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
    public static class Task { public long id; public String title, note; public boolean done; }
    public static class Session {
        public long id, taskId, startedAt, startedElapsed, endedAt;
        public String title, owner, endedDay, memo;
        public int planned, actual, status;
        public boolean demo;
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
                t.note = string(r, "note"); t.done = number(r, "done") == 1; list.add(t);
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
        s.title = string(r, "title_snapshot"); s.owner = string(r, "owner_token");
        s.memo = string(r, "memo");
        s.endedDay = string(r, "ended_day"); s.startedAt = number(r, "started_at");
        s.startedElapsed = number(r, "started_elapsed"); s.endedAt = number(r, "ended_at");
        s.planned = (int) number(r, "planned_seconds"); s.actual = (int) number(r, "actual_seconds");
        s.status = (int) number(r, "status"); s.demo = number(r, "is_demo") == 1;
        return s;
    }
    private static long number(Cursor c, String key) { return c.getLong(c.getColumnIndexOrThrow(key)); }
    private static String string(Cursor c, String key) { return c.getString(c.getColumnIndexOrThrow(key)); }
}
