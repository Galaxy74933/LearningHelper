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
    private int version;
    private boolean deleting;
    private AlertDialog deleteDialog;
    private static final String DETAIL="stats_detail";
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);refresh();}
    @Override protected void onDestroy(){if(deleteDialog!=null)deleteDialog.dismiss();super.onDestroy();}
    private String title(String detail){
        if("today_tasks".equals(detail))return "今日完成任务";
        if("all_tasks".equals(detail))return "累计完成任务";
        if("today_focus".equals(detail))return "今日专注记录";
        if("all_focus".equals(detail))return "累计专注与记录";
        return "学习统计";
    }
    private void refresh(){
        int request=++version;String detail=getIntent().getStringExtra(DETAIL);
        page=AppUi.page(this,title(detail),detail==null?"看见每一天的进步":"查看具体学习情况");
        if(detail==null)AppUi.navigation(this,page,2);
        else {TextView back=AppUi.text(this,"‹ 返回统计",16,AppUi.GREEN,true);back.setPadding(0,AppUi.dp(this,10),0,AppUi.dp(this,10));page.content.addView(back);back.setOnClickListener(v->finish());}
        StudyRepository.run(this,()->StatisticsData.load(this),data->{if(request==version){if(detail==null)dashboard(data);else details(data,detail);}},e->{if(request==version)AppUi.error(this,e);});
    }
    private void open(String key){startActivity(new Intent(this,RecordsActivity.class).putExtra(DETAIL,key));}
    private LinearLayout section(String name){
        LinearLayout card=AppUi.card(this);GradientDrawable bg=new GradientDrawable();bg.setColor(AppUi.SURFACE);bg.setCornerRadius(AppUi.dp(this,18));card.setBackground(bg);
        card.addView(AppUi.text(this,name,20,AppUi.INK,true));page.content.addView(card);return card;
    }
    private void dashboard(StatisticsData d){
        LinearLayout overview=section("今日概览");AppUi.space(overview,4);overview.addView(AppUi.text(this,d.today+" · 点击数据块查看详情",12,AppUi.GRAY,false));
        LinearLayout first=new LinearLayout(this),second=new LinearLayout(this);overview.addView(first);overview.addView(second);
        tile(first,"今日完成数",String.valueOf(d.todayTasks),"个 · 按完成日期","today_tasks",0);
        tile(first,"累计完成数",String.valueOf(d.completed.size()),"个 · 当前已完成任务","all_tasks",1);
        tile(second,"今日专注次数",String.valueOf(d.todayCount),"次 · 已完成或保存","today_focus",0);
        tile(second,"累计专注",compact(d.free+d.timed),"自由 + 定时","all_focus",1);
        chart("任务统计","近七日 · 每日完成数",d.taskTrend,d.days,false,"all_tasks");
        chart("专注统计","近七日 · 每日专注时长",d.focusTrend,d.days,true,"all_focus");
    }
    private void tile(LinearLayout row,String name,String value,String hint,String key,int position){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);int pad=AppUi.dp(this,12);box.setPadding(pad,pad,pad,pad);
        GradientDrawable bg=new GradientDrawable();bg.setColor(AppUi.SOFT);bg.setCornerRadius(AppUi.dp(this,14));box.setBackground(bg);
        box.addView(AppUi.text(this,name,14,AppUi.INK,true));AppUi.space(box,12);
        TextView number=AppUi.text(this,value,26,AppUi.GREEN,true);number.setMaxLines(1);number.setAutoSizeTextTypeUniformWithConfiguration(14,26,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        box.addView(number,new LinearLayout.LayoutParams(-1,AppUi.dp(this,38)));AppUi.space(box,5);box.addView(AppUi.text(this,hint,11,AppUi.GRAY,false));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.topMargin=AppUi.dp(this,12);if(position==0)lp.rightMargin=AppUi.dp(this,6);else lp.leftMargin=AppUi.dp(this,6);row.addView(box,lp);
        box.setFocusable(true);box.setContentDescription(name+"："+value+"，"+hint+"，查看详情");box.setOnClickListener(v->open(key));
    }
    private void chart(String title,String subtitle,long[] values,String[] dates,boolean focus,String key){
        LinearLayout card=section(title+"  ›");card.setOnClickListener(v->open(key));card.setFocusable(true);
        AppUi.space(card,6);card.addView(AppUi.text(this,subtitle,12,AppUi.GRAY,false));
        card.addView(new StatisticsChart(this,values,dates,focus),new LinearLayout.LayoutParams(-1,AppUi.dp(this,210)));
    }
    private String compact(long seconds){return AppUi.duration(this,seconds);}
    private String duration(long s){return AppUi.duration(this,s);}
    private void details(StatisticsData d,String key){
        boolean today=key.startsWith("today"),tasks=key.endsWith("tasks");
        if(tasks){
            LinearLayout summary=section((today?d.todayTasks:d.completed.size())+" 个已完成");
            summary.addView(AppUi.text(this,"包含主界面任务和日历待办；已删除或取消完成的事项不计入。",13,AppUi.GRAY,false));
            if(d.unknownDates>0)summary.addView(AppUi.text(this,d.unknownDates+" 个旧任务未记录完成日期，仅计入累计数量。",13,AppUi.GRAY,false));
            int shown=0;
            for(StatisticsData.CompletedTask task:d.completed){
                if(today&&(task.completedAt<=0||!d.today.equals(CalendarDates.key(task.completedAt))))continue;
                LinearLayout card=section(task.title);card.addView(AppUi.text(this,task.source+" · 已完成",13,AppUi.GREEN,false));
                card.addView(AppUi.text(this,task.completedAt>0?"完成于 "+new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(task.completedAt)):"完成日期未记录",13,AppUi.GRAY,false));shown++;
            }
            if(shown==0){AppUi.space(page.content,24);page.content.addView(AppUi.text(this,"暂无完成任务，完成后会显示在这里。",15,AppUi.GRAY,false));}
        }else{
            long free=today?d.todayFree:d.free,timed=today?d.todayTimed:d.timed;
            LinearLayout summary=section((today?"今日":"累计")+"专注 · "+(today?d.todayCount:d.totalCount)+" 次");
            summary.addView(AppUi.text(this,duration(free+timed),24,AppUi.GREEN,true));summary.addView(AppUi.text(this,"自由："+duration(free)+"\n定时："+duration(timed),14,AppUi.GRAY,false));
            AppUi.space(page.content,16);page.content.addView(AppUi.text(this,"学习记录",20,AppUi.INK,true));
            String last="";int shown=0;
            for(StudyRepository.Session s:d.sessions){String day=StatisticsData.day(s);if(today&&!d.today.equals(day))continue;
                if(!day.equals(last)){AppUi.space(page.content,14);page.content.addView(AppUi.text(this,(day.equals(d.today)?"今天 · ":"")+day,16,AppUi.GRAY,true));last=day;}
                LinearLayout card=AppUi.card(this);card.addView(AppUi.text(this,s.title,17,AppUi.INK,true));
                SimpleDateFormat f=new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA);card.addView(AppUi.text(this,f.format(new Date(s.startedAt))+" → "+f.format(new Date(s.endedAt)),12,AppUi.GRAY,false));
                card.addView(AppUi.text(this,(s.free?"自由专注":"定时专注")+" · "+duration(s.actual)+" · "+state(s)+(StatisticsData.counted(s)?"":"（不计入统计）"),13,AppUi.GRAY,false));
                LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.END);Button del=AppUi.button(this,"删除",false);footer.addView(del,new LinearLayout.LayoutParams(AppUi.dp(this,68),AppUi.dp(this,48)));card.addView(footer);del.setOnClickListener(v->confirmDelete(s));page.content.addView(card);shown++;
            }
            if(shown==0){AppUi.space(page.content,24);page.content.addView(AppUi.text(this,"还没有专注记录。",16,AppUi.GRAY,false));}
            AppUi.space(page.content,16);page.content.addView(AppUi.text(this,"自由专注保存、定时专注完成后计入统计。删除记录会同步更新统计。",13,AppUi.GRAY,false));
        }
    }
    private void confirmDelete(StudyRepository.Session session) {
        if (deleting || (deleteDialog != null && deleteDialog.isShowing())) return;
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(session.startedAt));
        String message = "任务：" + session.title + "\n开始：" + time
                + "\n实际：" + AppUi.duration(session.actual) + "\n\n删除后无法恢复，任务本身会保留。";
        if (!session.demo && (session.status == TaskProvider.COMPLETED || (session.free && session.status == TaskProvider.EARLY_END)))
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
        if (s.status == TaskProvider.EARLY_END) return s.free?"自由专注已保存":"提前结束";
        if (s.status == TaskProvider.INTERRUPTED) return "异常中断";
        return "进行中";
    }
}
