package com.example.learninghelper;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** 概览只展示指标与趋势；点击卡片进入任务或专注明细。 */
public class RecordsActivity extends WheelActivity {
    private AppUi.Page page;
    private LinearLayout fixedControls;
    private int version;
    private int periodMode=StatsPeriod.DAY;
    private long periodAnchor=System.currentTimeMillis();
    private StatisticsData cached;
    @Override protected void onCreate(android.os.Bundle state){super.onCreate(state);if(state!=null){periodMode=state.getInt("periodMode",0);periodAnchor=state.getLong("periodAnchor",System.currentTimeMillis());}}
    @Override protected void onSaveInstanceState(android.os.Bundle out){super.onSaveInstanceState(out);out.putInt("periodMode",periodMode);out.putLong("periodAnchor",periodAnchor);}

    private boolean deleting;
    private AlertDialog deleteDialog;
    private static final String DETAIL="stats_detail";
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);refresh();}
    @Override protected void onDestroy(){if(deleteDialog!=null)deleteDialog.dismiss();super.onDestroy();}
    private String title(String detail){
        if(getIntent().hasExtra("period_mode")&&detail!=null)return AppUi.label(this,detail.endsWith("tasks")?"完成任务":"专注记录",detail.endsWith("tasks")?"Completed tasks":"Focus records");
        if("today_tasks".equals(detail))return L10n.t(RecordsActivity.this,"今日完成任务");
        if("all_tasks".equals(detail))return L10n.t(RecordsActivity.this,"累计完成任务");
        if("today_focus".equals(detail))return L10n.t(RecordsActivity.this,"今日专注记录");
        if("all_focus".equals(detail))return L10n.t(RecordsActivity.this,"累计专注与记录");
        return L10n.t(RecordsActivity.this,"学习统计");
    }
    private void refresh(){
        int request=++version;String detail=getIntent().getStringExtra(DETAIL);
        page=AppUi.page(this,title(detail),"");
        fixedControls=new LinearLayout(this);fixedControls.setOrientation(LinearLayout.VERTICAL);
        fixedControls.setPadding(AppUi.dp(this,20),0,AppUi.dp(this,20),0);
        page.root.addView(fixedControls,1,new LinearLayout.LayoutParams(-1,-2));
        if(detail==null)AppUi.navigation(this,page,2);
        else {TextView back=AppUi.text(this,L10n.t(RecordsActivity.this,"‹ 返回统计"),16,AppUi.GREEN,true);back.setPadding(0,AppUi.dp(this,10),0,AppUi.dp(this,10));fixedControls.addView(back);back.setFocusable(true);back.setOnClickListener(v->finish());}
        StudyRepository.run(this,()->StatisticsData.load(this),data->{if(request==version){if(detail==null){cached=data;dashboard(data);}else {
            if(getIntent().hasExtra("period_mode")){
                StatsPeriod p=new StatsPeriod(getIntent().getIntExtra("period_mode",0),getIntent().getLongExtra("period_anchor",System.currentTimeMillis()));
                page.content.addView(AppUi.text(this,StatsDashboard.label(this,p),14,AppUi.GRAY,true));data=data.filtered(p);
            }
            details(data,detail);
        }}},e->{if(request==version)AppUi.error(this,e);});
    }
    private LinearLayout section(String name){
        LinearLayout card=AppUi.card(this);GradientDrawable bg=new GradientDrawable();bg.setColor(AppUi.SURFACE);bg.setCornerRadius(AppUi.dp(this,18));card.setBackground(bg);
        card.addView(AppUi.text(this,name,20,AppUi.INK,true));page.content.addView(card);return card;
    }
    private void dashboard(StatisticsData d){
        page.content.removeAllViews();
        fixedControls.removeAllViews();
        new StatsDashboard(this,page.content,d,new StatsPeriod(periodMode,periodAnchor),new StatsDashboard.Actions(){
            public void range(int mode,long anchor){periodMode=mode;periodAnchor=anchor;dashboard(cached);page.scroll.scrollTo(0,0);}
            public void details(String key){startActivity(new Intent(RecordsActivity.this,RecordsActivity.class).putExtra(DETAIL,key).putExtra("period_mode",periodMode).putExtra("period_anchor",periodAnchor));}
        }).render();
        // Keep the period tabs and date navigation outside the scrolling content.
        for(int i=0;i<2;i++){android.view.View control=page.content.getChildAt(0);page.content.removeView(control);fixedControls.addView(control);}
    }
    private String duration(long s){return AppUi.duration(this,s);}
    private void details(StatisticsData d,String key){
        boolean today=key.startsWith("today"),tasks=key.endsWith("tasks");
        if(tasks){
            LinearLayout summary=section(AppUi.label(this,today?"今日完成":getIntent().hasExtra("period_mode")?"本周期完成":"累计完成",today?"Completed today":getIntent().hasExtra("period_mode")?"Completed in period":"Total completed"));
            summary.addView(AppUi.text(this,String.valueOf(today?d.todayTasks:d.completed.size()),36,AppUi.GREEN,true));
            summary.addView(AppUi.text(this,AppUi.label(this,"已完成任务","Completed tasks"),14,AppUi.GRAY,false));
            AppUi.space(page.content,16);page.content.addView(AppUi.text(this,AppUi.label(this,"任务记录","Task records"),20,AppUi.INK,true));
            int shown=0;String last="";
            for(StatisticsData.CompletedTask task:d.completed){
                if(today&&(task.completedAt<=0||!d.today.equals(CalendarDates.key(task.completedAt))))continue;
                String day=task.completedAt>0?CalendarDates.key(task.completedAt):L10n.t(this,"完成日期未记录");
                if(!day.equals(last)){AppUi.space(page.content,14);page.content.addView(AppUi.text(this,(day.equals(d.today)?L10n.t(this,"今天 · "):"")+day,16,AppUi.GRAY,true));last=day;}
                LinearLayout card=AppUi.card(this);card.addView(AppUi.text(this,task.title,17,AppUi.INK,true));
                card.addView(AppUi.text(this,L10n.t(this,task.source)+L10n.t(this," · 已完成"),13,AppUi.GREEN,false));
                if(task.completedAt>0)card.addView(AppUi.text(this,new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(task.completedAt)),12,AppUi.GRAY,false));
                LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.END);Button del=AppUi.button(this,L10n.t(this,"删除"),false);
                footer.addView(del,new LinearLayout.LayoutParams(AppUi.dp(this,84),AppUi.dp(this,48)));card.addView(footer);del.setOnClickListener(v->confirmDeleteTask(task));page.content.addView(card);shown++;
            }
            if(shown==0){AppUi.space(page.content,24);page.content.addView(AppUi.text(this,L10n.t(RecordsActivity.this,"暂无完成任务，完成后会显示在这里。"),15,AppUi.GRAY,false));}
        }else{
            long free=today?d.todayFree:d.free,timed=today?d.todayTimed:d.timed;
            LinearLayout summary=section((today?L10n.t(RecordsActivity.this,"今日"):getIntent().hasExtra("period_mode")?AppUi.label(this,"本周期","Selected period"):L10n.t(RecordsActivity.this,"累计"))+L10n.t(RecordsActivity.this,"专注 · ")+(today?d.todayCount:d.totalCount)+L10n.t(RecordsActivity.this," 次"));
            summary.addView(AppUi.text(this,duration(free+timed),24,AppUi.GREEN,true));summary.addView(AppUi.text(this,L10n.t(RecordsActivity.this,"自由：")+duration(free)+L10n.t(RecordsActivity.this,"\n定时：")+duration(timed),14,AppUi.GRAY,false));
            AppUi.space(page.content,16);page.content.addView(AppUi.text(this,L10n.t(RecordsActivity.this,"学习记录"),20,AppUi.INK,true));
            String last="";int shown=0;
            for(StudyRepository.Session s:d.sessions){String day=StatisticsData.day(s);if(today&&!d.today.equals(day))continue;
                if(!day.equals(last)){AppUi.space(page.content,14);page.content.addView(AppUi.text(this,(day.equals(d.today)?L10n.t(RecordsActivity.this,"今天 · "):"")+day,16,AppUi.GRAY,true));last=day;}
                LinearLayout card=AppUi.card(this);card.addView(AppUi.text(this,L10n.sessionTitle(this,s),17,AppUi.INK,true));
                SimpleDateFormat f=new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA);card.addView(AppUi.text(this,f.format(new Date(s.startedAt))+" → "+f.format(new Date(s.endedAt)),12,AppUi.GRAY,false));
                if(!s.todoDate.isEmpty())card.addView(AppUi.text(this,AppUi.label(this,"关联日期：","Task date: ")+s.todoDate,12,AppUi.GRAY,false));
                card.addView(AppUi.text(this,(s.free?L10n.t(RecordsActivity.this,"自由专注"):L10n.t(RecordsActivity.this,"定时专注"))+" · "+duration(s.actual)+" · "+L10n.t(this,state(s))+(StatisticsData.counted(s)?"":L10n.t(RecordsActivity.this,"（不计入统计）")),13,AppUi.GRAY,false));
                LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.END);Button del=AppUi.button(this,L10n.t(RecordsActivity.this,"删除"),false);footer.addView(del,new LinearLayout.LayoutParams(AppUi.dp(this,84),AppUi.dp(this,48)));card.addView(footer);del.setOnClickListener(v->confirmDelete(s));page.content.addView(card);shown++;
            }
            if(shown==0){AppUi.space(page.content,24);page.content.addView(AppUi.text(this,L10n.t(RecordsActivity.this,"还没有专注记录。"),16,AppUi.GRAY,false));}
            AppUi.space(page.content,16);page.content.addView(AppUi.text(this,L10n.t(RecordsActivity.this,"自由专注保存、定时专注完成后计入统计。删除记录会同步更新统计。"),13,AppUi.GRAY,false));
        }
    }
    private void confirmDeleteTask(StatisticsData.CompletedTask task){
        if(deleting||(deleteDialog!=null&&deleteDialog.isShowing()))return;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(AppUi.label(this,"删除这条任务？","Delete this task?"))
            .setMessage(task.title+"\n\n"+(!task.occurrenceDay.isEmpty()?AppUi.label(this,"仅清除这一天的完成记录，任务和专注记录保留。","Only this date’s completion is cleared. The task and focus records are kept."):AppUi.label(this,"将同步从任务列表和完成统计中删除，已有专注记录保留。","This removes the task from your list and completion statistics. Focus records are kept.")))
            .setNegativeButton(L10n.t(this,"取消"),null).setPositiveButton(L10n.t(this,"删除"),null).create();deleteDialog=dialog;
        dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{
            if(deleting)return;deleting=true;dialog.setCancelable(false);dialog.getButton(-1).setEnabled(false);dialog.getButton(-2).setEnabled(false);
            StudyRepository.run(this,()->task.taskId>0?getContentResolver().delete(StudyRepository.taskUri(task.taskId),null,null)>0:!task.occurrenceDay.isEmpty()?StudyRepository.clearTodoOccurrence(this,task.todoId,task.occurrenceDay):StudyRepository.deleteTodo(this,task.todoId),removed->{
                deleting=false;dialog.dismiss();refresh();
            },e->{deleting=false;dialog.setCancelable(true);dialog.getButton(-1).setEnabled(true);dialog.getButton(-2).setEnabled(true);AppUi.error(this,e);});
        }));dialog.show();
    }
    private void confirmDelete(StudyRepository.Session session) {
        if (deleting || (deleteDialog != null && deleteDialog.isShowing())) return;
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(session.startedAt));
        String message = L10n.t(RecordsActivity.this,"任务：") + L10n.sessionTitle(this,session) + L10n.t(RecordsActivity.this,"\n开始：") + time
                + L10n.t(RecordsActivity.this,"\n实际：") + AppUi.duration(this,session.actual) + L10n.t(RecordsActivity.this,"\n\n删除后无法恢复，任务本身会保留。");
        if (StatisticsData.counted(session))
            message += L10n.t(RecordsActivity.this,"这条记录对应的专注时长和次数也会从统计中扣除。");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(L10n.t(RecordsActivity.this,"删除这条学习记录？"))
                .setMessage(message).setNegativeButton(L10n.t(RecordsActivity.this,"取消"), null).setPositiveButton(L10n.t(RecordsActivity.this,"删除"), null).create();
        deleteDialog = dialog;
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (deleting) return;
            deleting = true; dialog.setCancelable(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            android.content.Context app = getApplicationContext();
            StudyRepository.run(this, () -> StudyRepository.deleteSession(app, session.id), removed -> {
                deleting = false; dialog.dismiss();
                AppUi.toast(this, removed ? L10n.t(RecordsActivity.this,"记录已删除") : L10n.t(RecordsActivity.this,"记录已经不存在，列表已刷新"));
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
        if (s.status == TaskProvider.EARLY_END) return s.free?"自由专注已保存":"提前结束";
        if (s.status == TaskProvider.INTERRUPTED) return "异常中断";
        return "进行中";
    }
}
