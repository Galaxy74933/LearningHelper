package com.example.learninghelper;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

public class FocusActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppUi.Page page;
    private StudyRepository.Session session;
    private TextView countdown, soundState, hint;
    private Button endButton;
    private long requestedSession = -1, taskId = -1;
    private int loadVersion, durationIndex = 1, selectedSound;
    private boolean visible, busy, failedSave, resultShown;
    private long lastCheckpoint;
    private AlertDialog resultDialog;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        readIntent();
        durationIndex = AppUi.prefs(this).getInt("duration_index", 1);
        selectedSound = AppUi.prefs(this).getInt("sound_index", 0);
        if (state != null) {
            requestedSession = state.getLong("session_id", requestedSession);
            durationIndex = state.getInt("duration_index", durationIndex);
            selectedSound = state.getInt("sound_index", selectedSound);
        }
    }
    private void readIntent() {
        requestedSession = getIntent().getLongExtra("session_id", -1);
        taskId = getIntent().getLongExtra("task_id", -1);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); readIntent();
        session = null; resultShown = false; failedSave = false;
        if (resultDialog != null) resultDialog.dismiss();
        if (visible) load();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong("session_id", session == null ? requestedSession : session.id);
        out.putInt("duration_index", durationIndex); out.putInt("sound_index", selectedSound);
    }
    @Override protected void onResume() { super.onResume(); visible = true; load(); }
    @Override protected void onPause() { visible = false; handler.removeCallbacks(tick); super.onPause(); }
    @Override protected void onStop() { checkpoint(); super.onStop(); }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (resultDialog != null) resultDialog.dismiss();
        super.onDestroy();
    }
    private static class ScreenData {
        StudyRepository.Session session;
        StudyRepository.Task task;
    }
    private void load() {
        if (busy) return;
        int version = ++loadVersion;
        handler.removeCallbacks(tick);
        page = AppUi.page(this, "专注学习", "专注一件事，按自己的节奏完成");
        page.content.addView(AppUi.text(this, "正在读取专注状态…", 16, AppUi.GRAY, false));
        StudyRepository.run(this, () -> {
            ScreenData d = new ScreenData();
            // 无论从哪个任务进入，都优先返回唯一的进行中记录。
            d.session = StudyRepository.running(this);
            if (d.session == null && requestedSession >= 0) d.session = StudyRepository.session(this, requestedSession);
            if (d.session == null) for (StudyRepository.Task t : StudyRepository.tasks(this)) if (t.id == taskId && !t.done) d.task = t;
            return d;
        }, d -> {
            if (version != loadVersion) return;
            session = d.session;
            if (session != null) {
                requestedSession = session.id;
                if (session.status == TaskProvider.RUNNING && session.owned()) FocusCoordinator.schedule(this, session);
                renderSession();
            }
            else if (d.task != null) renderSetup(d.task);
            else {
                page.content.removeAllViews(); page.content.addView(AppUi.text(this, "请先到首页选择一个待完成任务。", 18, AppUi.INK, false)); homeButton();
            }
        }, e -> {
            if (version != loadVersion) return;
            page.content.removeAllViews(); Button retry = AppUi.button(this, "读取失败，点击重试", true);
            page.content.addView(retry); retry.setOnClickListener(v -> load()); homeButton(); AppUi.error(this, e);
        });
    }
    private Spinner spinner(String[] values, int selection) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(adapter);
        s.setSelection(Math.max(0, Math.min(selection, values.length - 1)));
        page.content.addView(s, new LinearLayout.LayoutParams(-1, AppUi.dp(this, 52))); return s;
    }
    private void renderSetup(StudyRepository.Task task) {
        page.content.removeAllViews();
        page.content.addView(AppUi.text(this, task.title, 26, AppUi.INK, true));
        if (!task.note.isEmpty()) page.content.addView(AppUi.text(this, task.note, 16, AppUi.GRAY, false));
        AppUi.space(page.content, 16);
        page.content.addView(AppUi.text(this, "本次时长", 16, AppUi.INK, true));
        boolean demo = AppUi.prefs(this).getBoolean("demo", false);
        if (!demo && durationIndex >= 4) durationIndex = 1;
        Spinner durations = spinner(demo ? AppUi.DURATION_LABELS : Arrays.copyOf(AppUi.DURATION_LABELS, 4), durationIndex);
        page.content.addView(AppUi.text(this, "本次声音", 16, AppUi.INK, true));
        Spinner sounds = spinner(AppUi.SOUNDS, selectedSound);
        durations.setOnItemSelectedListener(new Choice() { @Override public void chosen(int pos) { durationIndex = pos; } });
        sounds.setOnItemSelectedListener(new Choice() { @Override public void chosen(int pos) { selectedSound = pos; } });
        page.content.addView(AppUi.text(this, "想快速试用？到「设置」开启演示模式，就能选择 10 秒或 30 秒。", 14, AppUi.GRAY, false));
        permissionHint();
        Button start = AppUi.button(this, "开始专注", true); page.content.addView(start);
        start.setOnClickListener(v -> {
            if (busy) return;
            busy = true; start.setEnabled(false);
            int seconds = AppUi.DURATIONS[durationIndex], sound = selectedSound;
            android.content.Context app = getApplicationContext();
            StudyRepository.run(this, () -> {
                StudyRepository.Session s = StudyRepository.start(app, task.id, seconds);
                FocusCoordinator.schedule(app, s);
                return s;
            }, s -> {
                busy = false; session = s; requestedSession = s.id; failedSave = false; resultShown = false;
                boolean scheduled = FocusCoordinator.schedule(this, s);
                renderSession();
                if (!scheduled && s.owned()) AppUi.toast(this, "后台提醒未安排成功；请保持应用打开或稍后返回检查。");
                if (visible && s.owned() && s.status == TaskProvider.RUNNING && s.remaining() > 0) play(sound);
            }, e -> { busy = false; start.setEnabled(true); AppUi.error(this, e); });
        });
        homeButton();
    }
    private void renderSession() {
        handler.removeCallbacks(tick); page.content.removeAllViews();
        page.content.addView(AppUi.text(this, session.title, 26, AppUi.INK, true));
        page.content.addView(AppUi.text(this, session.demo ? "演示专注 · 不计入正式统计" : "计划 " + AppUi.duration(session.planned), 15, AppUi.GREEN, false));
        if (session.status != TaskProvider.RUNNING) { renderResult(); return; }
        if (!session.owned()) {
            page.content.addView(AppUi.text(this, "发现上次未结束的专注。应用进程曾退出，无法确认期间的学习时间。确认后保存为“异常中断”，保留最后保存的 " + AppUi.duration(session.actual) + "，不计入正式统计。", 17, AppUi.INK, false));
            endButton = AppUi.button(this, "确认中断并保存", true); page.content.addView(endButton);
            endButton.setOnClickListener(v -> finishSession(TaskProvider.INTERRUPTED)); homeButton(); return;
        }
        countdown = AppUi.text(this, "", 56, AppUi.GREEN, true); countdown.setGravity(Gravity.CENTER);
        AppUi.space(page.content, 24); page.content.addView(countdown); AppUi.space(page.content, 12);
        hint = AppUi.text(this, "锁屏或省电模式下提醒可能延迟，返回应用会校准时间。", 14, AppUi.GRAY, false); page.content.addView(hint);
        soundState = AppUi.text(this, NoiseService.status(session.id), 16, AppUi.INK, false); page.content.addView(soundState);
        Button change = AppUi.button(this, "选择 / 切换声音", false); page.content.addView(change);
        change.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("本次声音")
                .setSingleChoiceItems(AppUi.SOUNDS, NoiseService.sound(session.id), (dialog, which) -> { selectedSound = which; play(which); dialog.dismiss(); }).setNegativeButton("取消", null).show());
        permissionHint();
        endButton = AppUi.button(this, failedSave ? "重试保存记录" : "提前结束", false); page.content.addView(endButton);
        endButton.setOnClickListener(v -> {
            if (session.remaining() == 0) { finishSession(TaskProvider.COMPLETED); return; }
            new AlertDialog.Builder(this).setTitle("提前结束本次专注？")
                    .setMessage("会保存已专注的时间，但不计入完成统计。")
                    .setNegativeButton("继续专注", null).setPositiveButton("结束并保存", (dialog, which) -> finishSession(TaskProvider.EARLY_END)).show();
        });
        homeButton();
        if (visible) handler.post(tick);
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible || session == null || session.status != TaskProvider.RUNNING || !session.owned()) return;
            long seconds = (session.remaining() + 999) / 1000;
            if (countdown != null) countdown.setText(String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60));
            if (soundState != null) soundState.setText(NoiseService.status(session.id));
            if (seconds == 0) {
                if (!busy && !failedSave) finishSession(TaskProvider.COMPLETED);
                return;
            }
            if (SystemClock.elapsedRealtime() - lastCheckpoint >= 5000) checkpoint();
            handler.postDelayed(this, 500);
        }
    };
    private void checkpoint() {
        if (session == null || session.status != TaskProvider.RUNNING || !session.owned()) return;
        long id = session.id; android.content.Context app = getApplicationContext(); lastCheckpoint = SystemClock.elapsedRealtime();
        StudyRepository.IO.execute(() -> {
            try { StudyRepository.updateStatus(app, id, TaskProvider.RUNNING); }
            catch (Exception e) { android.util.Log.w("FocusActivity", "保存进度失败", e); }
        });
    }
    private void play(int sound) {
        if (session == null || session.status != TaskProvider.RUNNING || !session.owned()) return;
        try { NoiseService.play(this, session.id, sound); }
        catch (RuntimeException e) { AppUi.toast(this, "声音未能开启，计时仍会继续。请保持专注页面可见后重试。"); }
    }
    private void finishSession(int status) {
        if (busy || session == null) return;
        busy = true; failedSave = false; handler.removeCallbacks(tick);
        if (endButton != null) { endButton.setEnabled(false); endButton.setText("正在保存…"); }
        long id = session.id;
        NoiseService.stopMatching(this, id);
        StudyRepository.run(this, () -> FocusCoordinator.finish(this, id, status, true), s -> {
            busy = false; session = s; renderSession();
        }, e -> {
            busy = false; failedSave = true;
            if (endButton != null) { endButton.setEnabled(true); endButton.setText("重试保存记录"); }
            if (hint != null) hint.setText("本次记录暂未保存。请重试，不要重复开始。");
            AppUi.error(this, e);
            if (visible && session.owned() && session.remaining() > 0) handler.post(tick);
        });
    }
    private void renderResult() {
        String state = RecordsActivity.state(session);
        page.content.addView(AppUi.text(this, state, 30, AppUi.GREEN, true));
        page.content.addView(AppUi.text(this, "本次记录已保存：" + AppUi.duration(session.actual), 20, AppUi.INK, true));
        page.content.addView(AppUi.text(this, session.demo ? "这是演示记录，不计入正式统计。" : session.status == TaskProvider.COMPLETED ? "任务是否已经做完，由你自己决定。" : "本次记录不计入完成统计。", 15, AppUi.GRAY, false));
        if (session.status == TaskProvider.COMPLETED) {
            Button done = AppUi.button(this, "同时将任务标为完成", false); page.content.addView(done);
            done.setOnClickListener(v -> markTaskDone(done));
        }
        Button history = AppUi.button(this, "查看学习记录", true); page.content.addView(history);
        history.setOnClickListener(v -> { startActivity(new Intent(this, RecordsActivity.class).putExtra("show_demo", session.demo)); finish(); });
        homeButton();
        if (visible && !resultShown) {
            resultShown = true; FocusCoordinator.clearNotice(this);
            resultDialog = new AlertDialog.Builder(this).setTitle(state)
                    .setMessage("“" + session.title + "”\n" + AppUi.duration(session.actual) + "\n记录已保存" + (session.demo ? "（演示）" : ""))
                    .setPositiveButton("知道了", null).create(); resultDialog.show();
        }
    }
    private void markTaskDone(Button button) {
        if (busy) return; busy = true; button.setEnabled(false);
        long id = session.taskId;
        StudyRepository.run(this, () -> {
            for (StudyRepository.Task task : StudyRepository.tasks(this)) {
                if (task.id == id) { if (!task.done) StudyRepository.done(this, task); return true; }
            }
            return false;
        }, exists -> { busy = false; button.setText(exists ? "任务已完成" : "任务已经删除"); }, e -> { busy = false; button.setEnabled(true); AppUi.error(this, e); });
    }
    private void permissionHint() {
        if (!FocusCoordinator.notificationsAllowed(this)) {
            page.content.addView(AppUi.text(this, "结束提醒未开启；仍可正常计时、保存记录。", 14, AppUi.GRAY, false));
            Button allow = AppUi.button(this, "开启通知提醒", false); page.content.addView(allow);
            allow.setOnClickListener(v -> SettingsActivity.enableNotifications(this));
        }
    }
    private void homeButton() {
        Button back = AppUi.button(this, "返回任务首页", false); page.content.addView(back);
        back.setOnClickListener(v -> { startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish(); });
    }
    private abstract static class Choice implements AdapterView.OnItemSelectedListener {
        public abstract void chosen(int position);
        @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) { chosen(position); }
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }
}
