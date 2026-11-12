package com.example.learninghelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class RecordsActivity extends Activity {
    private int version;
    private AppUi.Page page;
    private boolean deleting;
    private AlertDialog deleteDialog;
    protected boolean statisticsOnly() { return false; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); refresh();
    }
    @Override protected void onResume() { super.onResume(); FocusCoordinator.clearNotice(this); refresh(); }
    @Override protected void onDestroy() {
        if (deleteDialog != null) deleteDialog.dismiss();
        super.onDestroy();
    }
    private void refresh() {
        int request = ++version;
        page = AppUi.page(this, statisticsOnly() ? "学习统计" : "学习记录", statisticsOnly() ? "看见每一次完成的积累" : "每一段认真投入，都有迹可循");
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
            long todaySeconds = 0, totalSeconds = 0, totalFree = 0, totalTimed = 0, todayFree = 0, todayTimed = 0; int todayCount = 0, totalCount = 0;
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            for (StudyRepository.Session s : list) {
                if (s.demo || !(s.status == TaskProvider.COMPLETED || (s.taskId == 0 && s.status == TaskProvider.EARLY_END))) continue;
                totalSeconds += s.actual; totalCount++;
                if (s.taskId == 0) totalFree += s.actual; else totalTimed += s.actual;
                if (today.equals(s.endedDay)) { todaySeconds += s.actual; todayCount++; if(s.taskId==0)todayFree+=s.actual;else todayTimed+=s.actual; }
            }
            LinearLayout hero=AppUi.card(this);android.graphics.drawable.GradientDrawable heroBg=new android.graphics.drawable.GradientDrawable();heroBg.setColor(AppUi.SOFT);heroBg.setCornerRadius(AppUi.dp(this,14));hero.setBackground(heroBg);hero.addView(AppUi.text(this,"今日专注时长",18,AppUi.GRAY,false));hero.addView(AppUi.text(this,(todaySeconds/60)+" 分钟",42,AppUi.GREEN,true));hero.addView(AppUi.text(this,"自由专注："+AppUi.duration((int)todayFree)+"\n定时专注："+AppUi.duration((int)todayTimed),14,AppUi.GRAY,false));page.content.addView(hero);
            LinearLayout pair=new LinearLayout(this);pair.setWeightSum(2);pair.addView(statBox("今日完成次数",todayCount+" 次"),new LinearLayout.LayoutParams(0,AppUi.dp(this,116),1));pair.addView(statBox("累计专注时长",totalSeconds/60+" 分钟"),new LinearLayout.LayoutParams(0,AppUi.dp(this,116),1));page.content.addView(pair);
            LinearLayout pair2=new LinearLayout(this);pair2.setWeightSum(2);pair2.addView(statBox("累计完成次数",totalCount+" 次"),new LinearLayout.LayoutParams(0,AppUi.dp(this,116),1));pair2.addView(statBox("累计分类时长","自由："+AppUi.duration((int)totalFree)+"\n定时："+AppUi.duration((int)totalTimed)),new LinearLayout.LayoutParams(0,AppUi.dp(this,116),1));page.content.addView(pair2);
            page.content.addView(AppUi.text(this,"怎样计算",20,AppUi.INK,true));
            page.content.addView(AppUi.text(this,"• 只统计正常完成的正式专注\n• 自由专注和定时专注分别记录并计入总时长\n• 自由专注手动保存计入统计；定时专注提前结束不计入\n• 10 秒、30 秒正常完成同样计入统计",15,AppUi.GRAY,false));
            AppUi.space(page.content,24);Button back=AppUi.button(this,"返回任务，开始下一次专注",true);page.content.addView(back);back.setOnClickListener(v->{startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();});
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        int count = 0; String lastDay = "";
        for (StudyRepository.Session s : list) {
            count++;
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(s.startedAt));
            if (!day.equals(lastDay)) { String todayLabel = day.equals(new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date())) ? "今天 · " + new SimpleDateFormat("MM月dd日", Locale.CHINA).format(new Date(s.startedAt)) : day; page.content.addView(AppUi.text(this, todayLabel, 20, AppUi.INK, true)); lastDay = day; }
            LinearLayout card = AppUi.card(this);
            card.addView(AppUi.text(this, s.title, 20, AppUi.INK, true));
            String datePrefix = new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date(s.startedAt)).equals(new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date(s.endedAt))) ? "" : new SimpleDateFormat("MM月dd日 ",Locale.CHINA).format(new Date(s.endedAt));
            String timeRange = new SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date(s.startedAt)) + "–" + datePrefix + new SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date(s.endedAt));
            AppUi.space(card,6);
            card.addView(AppUi.text(this,timeRange + " · " + AppUi.duration(s.actual),15,AppUi.GRAY,false));
            LinearLayout footer=new LinearLayout(this);footer.setGravity(android.view.Gravity.CENTER_VERTICAL);
            boolean counted=s.status==TaskProvider.COMPLETED || (s.taskId==0 && s.status==TaskProvider.EARLY_END);
            String label=counted?"已完成":s.status==TaskProvider.EARLY_END?"提前结束 · 不计入完成统计":"异常中断 · 不计入完成统计";
            footer.addView(AppUi.text(this,label,14,counted?AppUi.GREEN:android.graphics.Color.rgb(160,106,52),true),new LinearLayout.LayoutParams(0,-2,1));
            Button delete = AppUi.button(this, "删除", false);
            delete.setTextSize(13);delete.setTextColor(android.graphics.Color.rgb(170,55,55));delete.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));delete.setElevation(0);
            footer.addView(delete,new LinearLayout.LayoutParams(AppUi.dp(this,64),AppUi.dp(this,48)));card.addView(footer);
            delete.setOnClickListener(v -> confirmDelete(s));
            page.content.addView(card);
        }
        if (count == 0) page.content.addView(AppUi.text(this, "还没有学习记录，完成第一次专注后，这里会留下学习记录。", 17, AppUi.GRAY, false));
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
    private LinearLayout statBox(String label,String value){
        LinearLayout box=AppUi.card(this);
        int pad=AppUi.dp(this,14);box.setPadding(pad,pad,pad,pad);
        TextView title=AppUi.text(this,label,15,AppUi.GRAY,false);
        title.setSingleLine(true);title.setAutoSizeTextTypeUniformWithConfiguration(11,15,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        box.addView(title,new LinearLayout.LayoutParams(-1,AppUi.dp(this,24)));
        boolean breakdown=label.equals("累计分类时长");
        TextView amount=AppUi.text(this,value,breakdown?14:25,AppUi.INK,!breakdown);
        amount.setMaxLines(breakdown?2:1);amount.setLineSpacing(0,1f);
        amount.setGravity(android.view.Gravity.CENTER_VERTICAL);
        amount.setAutoSizeTextTypeUniformWithConfiguration(10,breakdown?14:25,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        box.addView(amount,new LinearLayout.LayoutParams(-1,0,1));
        return box;
    }
    public static String state(StudyRepository.Session s) {
        if (s.status == TaskProvider.COMPLETED) return "专注已完成";
        if (s.status == TaskProvider.EARLY_END) return s.taskId==0?"自由专注已保存":"提前结束";
        if (s.status == TaskProvider.INTERRUPTED) return "异常中断";
        return "进行中";
    }
}
