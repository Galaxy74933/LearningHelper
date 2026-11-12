package com.example.learninghelper;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.SystemClock;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** 所有任务和学习记录都通过此 Provider 访问；数据库兼容上一版。 */
public class TaskProvider extends ContentProvider {
    public static final String AUTHORITY = "com.example.learninghelper.tasks";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/tasks");
    public static final Uri SESSIONS_URI = Uri.parse("content://" + AUTHORITY + "/sessions");
    public static final String PROCESS_TOKEN = UUID.randomUUID().toString();
    public static final int RUNNING = 0, COMPLETED = 1, EARLY_END = 2, INTERRUPTED = 3;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        MATCHER.addURI(AUTHORITY, "tasks", 1);
        MATCHER.addURI(AUTHORITY, "tasks/#", 2);
        MATCHER.addURI(AUTHORITY, "sessions", 3);
        MATCHER.addURI(AUTHORITY, "sessions/#", 4);
    }
    private DatabaseHelper helper;

    @Override public boolean onCreate() {
        helper = new DatabaseHelper(getContext());
        return true;
    }

    private boolean tasks(Uri uri) {
        int m = MATCHER.match(uri);
        if (m < 1 || m > 4) throw new IllegalArgumentException("数据地址不正确");
        return m <= 2;
    }

    private String where(Uri uri, String selection) {
        String result = tasks(uri) ? "deleted = 0" : "1 = 1";
        int m = MATCHER.match(uri);
        if (m == 2 || m == 4) result += " AND _id = " + ContentUris.parseId(uri);
        if (selection != null && !selection.trim().isEmpty()) result += " AND (" + selection + ")";
        return result;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] args, String order) {
        boolean task = tasks(uri);
        if (order == null || order.trim().isEmpty())
            order = task ? "created_at DESC, _id DESC" : "started_at DESC, _id DESC";
        Cursor c = helper.getReadableDatabase().query(task ? "tasks" : "sessions", projection,
                where(uri, selection), args, null, null, order);
        c.setNotificationUri(getContext().getContentResolver(), task ? CONTENT_URI : SESSIONS_URI);
        return c;
    }

    @Override public String getType(Uri uri) {
        boolean task = tasks(uri);
        int m = MATCHER.match(uri);
        return "vnd.android.cursor." + (m == 2 || m == 4 ? "item" : "dir")
                + "/vnd." + AUTHORITY + (task ? ".task" : ".session");
    }

    @Override public Uri insert(Uri uri, ContentValues input) {
        int m = MATCHER.match(uri);
        Uri result;
        if (m == 1) {
            ContentValues v = taskValues(input, true);
            v.put("created_at", System.currentTimeMillis());
            v.put("deleted", 0);
            long id = helper.getWritableDatabase().insertOrThrow("tasks", null, v);
            result = ContentUris.withAppendedId(CONTENT_URI, id);
        } else if (m == 3) {
            result = startSession(input);
        } else throw new IllegalArgumentException("请使用列表地址新增数据");
        changed();
        return result;
    }

    @Override public int update(Uri uri, ContentValues input, String selection, String[] args) {
        int m = MATCHER.match(uri), count;
        if (m == 4) count = updateSession(ContentUris.parseId(uri), input);
        else if (m == 2) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                checkIdle(db, ContentUris.parseId(uri));
                ContentValues v = taskValues(input, false);
                count = v.size() == 0 ? 0 : db.update("tasks", v, where(uri, selection), args);
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        } else throw new IllegalArgumentException("请指定一条数据");
        if (count > 0) changed();
        return count;
    }

    @Override public int delete(Uri uri, String selection, String[] args) {
        int match = MATCHER.match(uri);
        if (match == 4) {
            // 仅允许删除指定的已结束记录；进行中的专注不能被删除。
            int count = helper.getWritableDatabase().delete("sessions",
                    where(uri, selection) + " AND status != 0", args);
            if (count > 0) changed();
            return count;
        }
        if (match != 2) throw new IllegalArgumentException("请指定要删除的一条任务或学习记录");
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        int count;
        try {
            checkIdle(db, ContentUris.parseId(uri));
            ContentValues v = new ContentValues();
            v.put("deleted", 1);
            count = db.update("tasks", v, where(uri, selection), args);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        if (count > 0) changed();
        return count;
    }

    private ContentValues taskValues(ContentValues input, boolean insert) {
        if (input == null) throw new IllegalArgumentException("内容不能为空");
        ContentValues v = new ContentValues();
        if (insert || input.containsKey("title")) {
            String title = input.getAsString("title");
            title = title == null ? "" : title.trim();
            if (title.isEmpty() || title.length() > 30)
                throw new IllegalArgumentException("任务名称应为 1～30 个字符");
            v.put("title", title);
        }
        if (insert || input.containsKey("note")) {
            String note = input.getAsString("note");
            note = note == null ? "" : note.trim();
            if (note.length() > 200) throw new IllegalArgumentException("备注不能超过 200 个字符");
            v.put("note", note);
        }
        if (insert || input.containsKey("done")) {
            Integer done = input.getAsInteger("done");
            done = done == null ? 0 : done;
            if (done != 0 && done != 1) throw new IllegalArgumentException("任务状态错误");
            v.put("done", done);
        }
        return v;
    }

    private void checkIdle(SQLiteDatabase db, long id) {
        try (Cursor c = db.query("sessions", new String[]{"_id"}, "task_id=? AND status=0",
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (c.moveToFirst()) throw new IllegalStateException("请先结束该任务的专注，再修改任务");
        }
    }

    private Uri startSession(ContentValues input) {
        Long task = input == null ? null : input.getAsLong("task_id");
        Integer duration = input == null ? null : input.getAsInteger("planned_seconds");
        boolean free = input != null && input.getAsInteger("free_focus") != null && input.getAsInteger("free_focus") == 1;
        if ((!free && task == null) || duration == null || (!free && duration != 10 && duration != 30 && duration != 900
                && duration != 1500 && duration != 2700 && duration != 3600))
            throw new IllegalArgumentException("请选择任务和有效时长");
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor c = db.query("sessions", new String[]{"_id"}, "status=0",
                    null, null, null, null)) {
                if (c.moveToFirst()) {
                    long id = c.getLong(0);
                    db.setTransactionSuccessful();
                    return ContentUris.withAppendedId(SESSIONS_URI, id);
                }
            }
            String title = "自由专注";
            if (!free) try (Cursor c = db.query("tasks", new String[]{"title"},
                    "_id=? AND done=0 AND deleted=0", new String[]{String.valueOf(task)},
                    null, null, null)) {
                if (!c.moveToFirst()) throw new IllegalStateException("任务不存在或已经完成");
                title = c.getString(0);
            }
            ContentValues v = new ContentValues();
            v.put("task_id", free ? 0 : task); v.put("title_snapshot", title);
            v.put("planned_seconds", duration); v.put("actual_seconds", 0);
            v.put("started_at", System.currentTimeMillis());
            v.put("started_elapsed", SystemClock.elapsedRealtime());
            v.put("status", RUNNING); v.put("is_demo", 0);
            v.put("owner_token", PROCESS_TOKEN);
            String memo = input.getAsString("memo"); v.put("memo", memo == null ? "" : memo.trim());
            long id = db.insertOrThrow("sessions", null, v);
            db.setTransactionSuccessful();
            return ContentUris.withAppendedId(SESSIONS_URI, id);
        } finally { db.endTransaction(); }
    }

    private int updateSession(long id, ContentValues input) {
        if (input == null) throw new IllegalArgumentException("更新内容不能为空");
        Integer requested = input.getAsInteger("status");
        int state = requested == null ? RUNNING : requested;
        if (state < RUNNING || state > INTERRUPTED) throw new IllegalArgumentException("状态错误");
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try (Cursor c = db.query("sessions", null, "_id=? AND status=0",
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (!c.moveToFirst()) { db.setTransactionSuccessful(); return 0; }
            int planned = c.getInt(c.getColumnIndexOrThrow("planned_seconds"));
            long started = c.getLong(c.getColumnIndexOrThrow("started_elapsed"));
            long wall = c.getLong(c.getColumnIndexOrThrow("started_at"));
            String owner = c.getString(c.getColumnIndexOrThrow("owner_token"));
            if (!PROCESS_TOKEN.equals(owner) && state != INTERRUPTED)
                throw new IllegalStateException("上次专注已中断，请先确认恢复提示");
            int elapsed = TimerMath.elapsedSeconds(started, planned, SystemClock.elapsedRealtime());
            if (state == COMPLETED && elapsed < planned)
                throw new IllegalStateException("还没到专注结束时间");
            if (state == EARLY_END && elapsed >= planned) state = COMPLETED;
            if (state == INTERRUPTED) elapsed = c.getInt(c.getColumnIndexOrThrow("actual_seconds"));
            ContentValues v = new ContentValues();
            v.put("actual_seconds", elapsed);
            if (state != RUNNING) {
                long end = state == COMPLETED ? wall + planned * 1000L : System.currentTimeMillis();
                v.put("status", state); v.put("ended_at", end);
                v.put("ended_day", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(end)));
            }
            int count = db.update("sessions", v, "_id=? AND status=0", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
            return count;
        } finally { db.endTransaction(); }
    }

    private void changed() {
        getContext().getContentResolver().notifyChange(CONTENT_URI, null);
        getContext().getContentResolver().notifyChange(SESSIONS_URI, null);
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context c) { super(c, "learning_helper.db", null, 4); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE tasks (_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "title TEXT NOT NULL,note TEXT NOT NULL DEFAULT '',done INTEGER NOT NULL DEFAULT 0,"
                    + "deleted INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL)");
            sessions(db);
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) sessions(db);
            if (oldVersion == 2) db.execSQL("ALTER TABLE sessions ADD COLUMN memo TEXT NOT NULL DEFAULT ''");
            if (oldVersion < 4) db.execSQL("UPDATE sessions SET is_demo=0 WHERE planned_seconds IN (10,30)");
        }
        private void sessions(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS sessions (_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "task_id INTEGER NOT NULL,title_snapshot TEXT NOT NULL,planned_seconds INTEGER NOT NULL,"
                    + "actual_seconds INTEGER NOT NULL DEFAULT 0,started_at INTEGER NOT NULL,"
                    + "started_elapsed INTEGER NOT NULL,ended_at INTEGER NOT NULL DEFAULT 0,"
                    + "ended_day TEXT NOT NULL DEFAULT '',status INTEGER NOT NULL DEFAULT 0,"
                    + "is_demo INTEGER NOT NULL DEFAULT 0,owner_token TEXT NOT NULL,memo TEXT NOT NULL DEFAULT '')");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS one_running_session ON sessions(status) WHERE status=0");
        }
    }
}
