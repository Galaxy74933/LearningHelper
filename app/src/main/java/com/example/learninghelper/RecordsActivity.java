package com.example.learninghelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class RecordsActivity extends Activity {
    private boolean showDemo;
    private int version;
    private AppUi.Page page;
    private boolean deleting;
    private AlertDialog deleteDialog;
    protected boolean statisticsOnly() { return false; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        showDemo = state != null ? state.getBoolean("show_demo") : getIntent().getBooleanExtra("show_demo", false);
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putBoolean("show_demo", showDemo); }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); showDemo = intent.getBooleanExtra("show_demo", false); refresh();
    }
    @Override protected void onResume() { super.onResume(); FocusCoordinator.clearNotice(this); refresh(); }
    @Override protected void onDestroy() {
        if (deleteDialog != null) deleteDialog.dismiss();
        super.onDestroy();
    }
    private void refresh() {
        int request = ++version;
        page = AppUi.page(this, statisticsOnly() ? "学习统计" : "学习记录", statisticsOnly() ? "看见每一次完成的积累" : "记录你的任务、时间和专注结果");
        AppUi.navigation(this, page, statisticsOnly() ? 2 : 1);
        page.content.addView(AppUi.text(this, "正在读取…", 16, AppUi.GRAY, false));
        StudyRepository.run(this, () -> StudyRepository.history(this), list -> {
            if (request == version) render(list);
        }, e -> {
            if (request != version) return;
            page.content.removeAllViews(); Button retry = AppUi.button(this, "读取失败，重新加载", true);
            page.content.addView(retry); retry.setOnClickListener(v -> refresh()); AppUi.error(this, e);
        });
    }
    private void render(List<StudyRepository.Session> list) {
        page.content.removeAllViews();
        if (statisticsOnly()) {
            long todaySeconds = 0, totalSeconds = 0; int todayCount = 0, totalCount = 0;
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            for (StudyRepository.Session s : list) {
                if (s.demo || s.status != TaskProvider.COMPLETED) continue;
                totalSeconds += s.actual; totalCount++;
                if (today.equals(s.endedDay)) { todaySeconds += s.actual; todayCount++; }
            }
            metric("今日专注时长", todaySeconds / 60 + " 分钟"); metric("今日完成次数", todayCount + " 次");
            metric("累计专注时长", totalSeconds / 60 + " 分钟"); metric("累计完成次数", totalCount + " 次");
            AppUi.space(page.content, 16);
            page.content.addView(AppUi.text(this, "只统计正常完成的正式专注。提前结束、异常中断、演示记录不计入。跨午夜的专注归入记录保存的结束日期。", 15, AppUi.GRAY, false));
            return;
        }
        Switch mode = new Switch(this); mode.setText("查看演示记录（不计统计）"); mode.setChecked(showDemo); page.content.addView(mode);
        mode.setOnCheckedChangeListener((v, checked) -> { showDemo = checked; render(list); });
        page.content.addView(AppUi.text(this, showDemo ? "仅显示 30 秒演示记录" : "正式记录 · 最近结束的排在前面", 14, AppUi.GRAY, false));
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        int count = 0;
        for (StudyRepository.Session s : list) {
            if (s.demo != showDemo) continue;
            count++;
            LinearLayout card = AppUi.card(this);
            card.addView(AppUi.text(this, s.title, 20, AppUi.INK, true));
            card.addView(AppUi.text(this, state(s) + (s.demo ? " · 演示" : ""), 15, AppUi.GREEN, true));
            card.addView(AppUi.text(this, "计划 " + AppUi.duration(s.planned) + "\n实际 " + AppUi.duration(s.actual), 16, AppUi.INK, false));
            card.addView(AppUi.text(this, "开始：" + format.format(new Date(s.startedAt)) + "\n结束：" + format.format(new Date(s.endedAt)), 13, AppUi.GRAY, false));
            Button delete = AppUi.button(this, "删除这条记录", false);
            delete.setTextColor(android.graphics.Color.rgb(170, 55, 55));
            card.addView(delete);
            delete.setOnClickListener(v -> confirmDelete(s));
            page.content.addView(card);
        }
        if (count == 0) page.content.addView(AppUi.text(this, "还没有" + (showDemo ? "演示" : "正式") + "记录。完成第一次专注后，这里会留下学习记录。", 17, AppUi.GRAY, false));
    }
    private void confirmDelete(StudyRepository.Session session) {
        if (deleting || (deleteDialog != null && deleteDialog.isShowing())) return;
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(session.startedAt));
        String message = "任务：" + session.title + "\n开始：" + time
                + "\n实际：" + AppUi.duration(session.actual) + "\n\n删除后无法恢复，任务本身会保留。";
        if (!session.demo && session.status == TaskProvider.COMPLETED)
            message += "这条记录对应的专注时长和次数也会从统计中扣除。";
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("删除这条学习记录？")
                .setMessage(message).setNegativeButton("取消", null).setPositiveButton("删除", null).create();
        deleteDialog = dialog;
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (deleting) return;
            deleting = true; dialog.setCancelable(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            android.content.Context app = getApplicationContext();
            StudyRepository.run(this, () -> StudyRepository.deleteSession(app, session.id), removed -> {
                deleting = false; dialog.dismiss();
                AppUi.toast(this, removed ? "记录已删除" : "记录已经不存在，列表已刷新");
                refresh();
            }, e -> {
                deleting = false; dialog.setCancelable(true);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                AppUi.error(this, e);
            });
        }));
        dialog.show();
    }
    private void metric(String label, String value) {
        LinearLayout card = AppUi.card(this); card.addView(AppUi.text(this, label, 16, AppUi.GRAY, false));
        card.addView(AppUi.text(this, value, 32, AppUi.GREEN, true)); page.content.addView(card);
    }
    public static String state(StudyRepository.Session s) {
        if (s.status == TaskProvider.COMPLETED) return "专注已完成";
        if (s.status == TaskProvider.EARLY_END) return "提前结束";
        if (s.status == TaskProvider.INTERRUPTED) return "异常中断";
        return "进行中";
    }
}
