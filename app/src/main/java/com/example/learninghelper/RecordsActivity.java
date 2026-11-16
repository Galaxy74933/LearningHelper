package com.example.learninghelper;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** 第三个入口只显示学习统计和历史记录。 */
public class RecordsActivity extends WheelActivity {
    private AppUi.Page page;private int version;private boolean deleting;private AlertDialog deleteDialog;
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);refresh();}
    @Override protected void onDestroy(){if(deleteDialog!=null)deleteDialog.dismiss();super.onDestroy();}
    private void refresh(){int request=++version;page=AppUi.page(this,"学习统计与记录","回顾每一次专注");AppUi.navigation(this,page,2);StudyRepository.run(this,()->StudyRepository.history(this),list->{if(request==version)render(list);},e->{if(request==version)AppUi.error(this,e);});}
    private boolean counted(StudyRepository.Session s){return !s.demo&&(s.status==TaskProvider.COMPLETED||(s.taskId==0&&s.status==TaskProvider.EARLY_END));}
    private void render(List<StudyRepository.Session> list){
        page.content.removeAllViews();long free=0,timed=0,todayFree=0,todayTimed=0;int count=0,todayCount=0;String today=CalendarDates.key(System.currentTimeMillis());
        for(StudyRepository.Session s:list)if(counted(s)){count++;if(s.taskId==0)free+=s.actual;else timed+=s.actual;if(today.equals(s.endedDay)){todayCount++;if(s.taskId==0)todayFree+=s.actual;else todayTimed+=s.actual;}}
        LinearLayout hero=AppUi.card(this);hero.addView(AppUi.text(this,"今日专注 · "+todayCount+" 次",17,AppUi.GRAY,false));hero.addView(AppUi.text(this,duration(todayFree+todayTimed),28,AppUi.GREEN,true));hero.addView(AppUi.text(this,"自由："+duration(todayFree)+"\n定时："+duration(todayTimed),14,AppUi.GRAY,false));page.content.addView(hero);
        LinearLayout total=AppUi.card(this);total.addView(AppUi.text(this,"累计专注 · "+count+" 次",17,AppUi.INK,true));total.addView(AppUi.text(this,duration(free+timed),26,AppUi.GREEN,true));total.addView(AppUi.text(this,"自由："+duration(free)+"\n定时："+duration(timed),14,AppUi.GRAY,false));page.content.addView(total);
        AppUi.space(page.content,20);page.content.addView(AppUi.text(this,"学习记录",22,AppUi.INK,true));String last="";
        for(StudyRepository.Session s:list){String day=s.endedDay==null?CalendarDates.key(s.endedAt):s.endedDay;if(!day.equals(last)){AppUi.space(page.content,12);page.content.addView(AppUi.text(this,(day.equals(today)?"今天 · ":"")+day,17,AppUi.GRAY,true));last=day;}
            LinearLayout card=AppUi.card(this);card.addView(AppUi.text(this,s.title,18,AppUi.INK,true));SimpleDateFormat f=new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA);card.addView(AppUi.text(this,f.format(new Date(s.startedAt))+" → "+f.format(new Date(s.endedAt)),13,AppUi.GRAY,false));card.addView(AppUi.text(this,(s.taskId==0?"自由专注":"定时专注")+" · "+duration(s.actual)+" · "+state(s),14,AppUi.GRAY,false));Button del=AppUi.button(this,"删除",false);LinearLayout footer=new LinearLayout(this);footer.setGravity(android.view.Gravity.END);footer.addView(del,new LinearLayout.LayoutParams(AppUi.dp(this,68),AppUi.dp(this,48)));card.addView(footer);del.setOnClickListener(v->confirmDelete(s));page.content.addView(card);}
        if(list.isEmpty())page.content.addView(AppUi.text(this,"还没有专注记录。",16,AppUi.GRAY,false));
        AppUi.space(page.content,16);page.content.addView(AppUi.text(this,"自由专注保存、定时专注完成后计入统计。删除记录会同步更新统计。",13,AppUi.GRAY,false));
    }
    private String duration(long s){return s/3600+"时 "+s%3600/60+"分 "+s%60+"秒";}
    private void confirmDelete(StudyRepository.Session session) {
        if (deleting || (deleteDialog != null && deleteDialog.isShowing())) return;
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(session.startedAt));
        String message = "任务：" + session.title + "\n开始：" + time
                + "\n实际：" + AppUi.duration(session.actual) + "\n\n删除后无法恢复，任务本身会保留。";
        if (!session.demo && (session.status == TaskProvider.COMPLETED || (session.taskId == 0 && session.status == TaskProvider.EARLY_END)))
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
    public static String state(StudyRepository.Session s) {
        if (s.status == TaskProvider.COMPLETED) return "专注已完成";
        if (s.status == TaskProvider.EARLY_END) return s.taskId==0?"自由专注已保存":"提前结束";
        if (s.status == TaskProvider.INTERRUPTED) return "异常中断";
        return "进行中";
    }
}
