package com.example.learninghelper;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 界面通过本类异步调用 ContentResolver，避免在主线程访问数据库。 */
public final class StudyRepository {
    private StudyRepository() {}
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler UI = new Handler(Looper.getMainLooper());
    public interface Success<T> { void accept(T value); }
    public interface Failure { void accept(Exception error); }
    public static <T> void run(Activity a, Callable<T> job, Success<T> ok, Failure fail) {
        IO.execute(() -> {
            try {
                T result = job.call();
                UI.post(() -> { if (!a.isFinishing() && !a.isDestroyed()) ok.accept(result); });
            } catch (Exception e) {
                Log.e("LearningHelper", "操作失败", e);
                UI.post(() -> { if (!a.isFinishing() && !a.isDestroyed()) fail.accept(e); });
            }
        });
    }
    public static class Task { public long id, completedAt; public String title, note; public boolean done; }
    public static class Session {
        public long id, taskId, startedAt, startedElapsed, endedAt;
        public String title, owner, endedDay, memo;
        public String todoId="";
        public int planned, actual, status;
        public boolean demo, free;
        public boolean owned() { return TaskProvider.PROCESS_TOKEN.equals(owner); }
        public long remaining() { return TimerMath.remainingMillis(startedElapsed, planned, SystemClock.elapsedRealtime()); }
    }
    public static Uri taskUri(long id) { return ContentUris.withAppendedId(TaskProvider.CONTENT_URI, id); }
    public static Uri sessionUri(long id) { return ContentUris.withAppendedId(TaskProvider.SESSIONS_URI, id); }
    public static List<Task> tasks(Context c) {
        List<Task> list = new ArrayList<>();
        try (Cursor r = c.getContentResolver().query(TaskProvider.CONTENT_URI, null, null, null, null)) {
            if (r == null) throw new IllegalStateException("任务读取失败");
            while (r.moveToNext()) {
                Task t = new Task(); t.id = number(r, "_id"); t.title = string(r, "title");
                t.note = string(r, "note"); t.done = number(r, "done") == 1; t.completedAt = number(r, "completed_at"); list.add(t);
            }
        }
        return list;
    }
    public static void saveTask(Context c, Long id, String title, String note) {
        ContentValues v = new ContentValues(); v.put("title", title); v.put("note", note);
        if (id == null) {
            if (c.getContentResolver().insert(TaskProvider.CONTENT_URI, v) == null) throw new IllegalStateException("任务未保存");
        } else require(c.getContentResolver().update(taskUri(id), v, null, null));
    }
    public static void done(Context c, Task t) {
        ContentValues v = new ContentValues(); v.put("done", t.done ? 0 : 1);
        require(c.getContentResolver().update(taskUri(t.id), v, null, null));
    }
    public static void delete(Context c, long id) { require(c.getContentResolver().delete(taskUri(id), null, null)); }
    public static boolean deleteSession(Context c, long id) {
        int count = c.getContentResolver().delete(sessionUri(id), null, null);
        if (count > 0) return true;
        Session current = session(c, id);
        if (current != null) throw new IllegalStateException("进行中的专注不能删除，请先结束专注");
        return false; // 记录已被删除时只刷新，不误报成功删除其他数据。
    }
    public static void discardSession(Context c,long id) {
        int count=c.getContentResolver().delete(sessionUri(id).buildUpon().appendQueryParameter("discard","true").build(),null,null);
        if(count==0&&session(c,id)!=null)throw new IllegalStateException("未能放弃本次专注，请重试");
    }
    private static void require(int n) { if (n != 1) throw new IllegalStateException("任务不存在，请刷新页面"); }
    public static Session running(Context c) {
        try (Cursor r = c.getContentResolver().query(TaskProvider.SESSIONS_URI, null, "status=0", null, null)) {
            if (r == null) throw new IllegalStateException("专注状态读取失败");
            return r.moveToFirst() ? read(r) : null;
        }
    }
    public static Session session(Context c, long id) {
        try (Cursor r = c.getContentResolver().query(sessionUri(id), null, null, null, null)) {
            if (r == null) throw new IllegalStateException("记录读取失败");
            return r.moveToFirst() ? read(r) : null;
        }
    }
    public static Session start(Context c, long taskId, int seconds) {
        ContentValues v = new ContentValues(); v.put("task_id", taskId); v.put("planned_seconds", seconds);
        Uri uri = c.getContentResolver().insert(TaskProvider.SESSIONS_URI, v);
        if (uri == null) throw new IllegalStateException("专注未启动");
        Session s = session(c, ContentUris.parseId(uri));
        if (s == null) throw new IllegalStateException("无法读取新建专注");
        return s;
    }
    public static Session startFocus(Context c,long taskId,int seconds,boolean free) {
        ContentValues v=new ContentValues();v.put("task_id",Math.max(0,taskId));v.put("planned_seconds",seconds);v.put("free_focus",free?1:0);
        Uri uri=c.getContentResolver().insert(TaskProvider.SESSIONS_URI,v);
        if(uri==null)throw new IllegalStateException("专注未启动");
        return session(c,ContentUris.parseId(uri));
    }
    public static List<org.json.JSONObject> todos(Context c) {
        try {org.json.JSONArray array=new org.json.JSONArray(c.getSharedPreferences("learning_todos",0).getString("items","[]"));
            List<org.json.JSONObject> result=new ArrayList<>();for(int i=0;i<array.length();i++)result.add(array.getJSONObject(i));return result;
        }catch(org.json.JSONException e){throw new IllegalStateException(AppUi.label(c,"待办读取失败，原数据已保留。请重新打开重试。","Could not load to-dos. Your data is safe. Please retry."),e);}
    }
    public static org.json.JSONObject todo(Context c,String id) {for(org.json.JSONObject item:todos(c))if(id.equals(item.optString("id")))return item;return null;}
    public static Session startTodoFocus(Context c,String id,int seconds,boolean free) {
        ContentValues v=new ContentValues();v.put("task_id",0);v.put("todo_id",id);v.put("planned_seconds",seconds);v.put("free_focus",free?1:0);
        Uri uri=c.getContentResolver().insert(TaskProvider.SESSIONS_URI,v);if(uri==null)throw new IllegalStateException(L10n.t(c,"专注未启动"));return session(c,ContentUris.parseId(uri));
    }
    public static synchronized boolean completeTodo(Context c,String id) {
        List<org.json.JSONObject> list=todos(c);boolean found=false;
        try{for(org.json.JSONObject item:list)if(id.equals(item.optString("id"))){found=true;if(!item.optBoolean("done")){item.put("done",true);item.put("completed_at",System.currentTimeMillis());}break;}}
        catch(org.json.JSONException e){throw new IllegalStateException(e);}
        if(found){org.json.JSONArray array=new org.json.JSONArray();for(org.json.JSONObject item:list)array.put(item);c.getSharedPreferences("learning_todos",0).edit().putString("items",array.toString()).apply();}return found;
    }
    /** Only mutate the requested ID against fresh storage, never write a stale screen snapshot. */
    public static synchronized void saveTodo(Context c,org.json.JSONObject value,boolean create) {
        List<org.json.JSONObject> list=todos(c);String id=value.optString("id");
        if(id.isEmpty())throw new IllegalArgumentException("Missing to-do ID");
        boolean found=false;org.json.JSONArray array=new org.json.JSONArray();
        for(org.json.JSONObject item:list){if(id.equals(item.optString("id"))){array.put(value);found=true;}else array.put(item);}
        if(!found){if(!create)throw new IllegalStateException(AppUi.label(c,"任务不存在，请刷新页面","Task no longer exists. Please refresh."));array.put(value);}
        c.getSharedPreferences("learning_todos",0).edit().putString("items",array.toString()).apply();
    }
    public static synchronized boolean deleteTodo(Context c,String id) {
        org.json.JSONArray array=new org.json.JSONArray();boolean found=false;
        for(org.json.JSONObject item:todos(c)){if(id.equals(item.optString("id")))found=true;else array.put(item);}
        if(found)c.getSharedPreferences("learning_todos",0).edit().putString("items",array.toString()).apply();
        return found;
    }
    public static Session startFree(Context c, String memo) {
        ContentValues v = new ContentValues(); v.put("free_focus", 1); v.put("planned_seconds", 3600 * 24);
        v.put("memo", memo == null ? "" : memo.trim());
        Uri uri = c.getContentResolver().insert(TaskProvider.SESSIONS_URI, v);
        if (uri == null) throw new IllegalStateException("自由专注未启动");
        Session s = session(c, ContentUris.parseId(uri));
        if (s == null) throw new IllegalStateException("无法读取自由专注"); return s;
    }
    public static int updateStatus(Context c, long id, int state) {
        ContentValues v = new ContentValues(); v.put("status", state);
        return c.getContentResolver().update(sessionUri(id), v, null, null);
    }
    public static List<Session> history(Context c) {
        List<Session> list = new ArrayList<>();
        try (Cursor r = c.getContentResolver().query(TaskProvider.SESSIONS_URI, null, "status != 0", null, "ended_at DESC, _id DESC")) {
            if (r == null) throw new IllegalStateException("学习记录读取失败");
            while (r.moveToNext()) list.add(read(r));
        }
        return list;
    }
    private static Session read(Cursor r) {
        Session s = new Session(); s.id = number(r, "_id"); s.taskId = number(r, "task_id");
        s.todoId=string(r,"todo_id");
        s.title = string(r, "title_snapshot"); s.owner = string(r, "owner_token");
        s.memo = string(r, "memo");
        s.endedDay = string(r, "ended_day"); s.startedAt = number(r, "started_at");
        s.startedElapsed = number(r, "started_elapsed"); s.endedAt = number(r, "ended_at");
        s.planned = (int) number(r, "planned_seconds"); s.actual = (int) number(r, "actual_seconds");
        s.free = number(r, "free_focus") == 1; s.status = (int) number(r, "status"); s.demo = number(r, "is_demo") == 1;
        return s;
    }
    private static long number(Cursor c, String key) { return c.getLong(c.getColumnIndexOrThrow(key)); }
    private static String string(Cursor c, String key) { return c.getString(c.getColumnIndexOrThrow(key)); }
}
