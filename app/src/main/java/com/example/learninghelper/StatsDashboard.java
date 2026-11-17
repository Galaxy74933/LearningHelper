package com.example.learninghelper;

import android.app.DatePickerDialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** One shared range controls every metric, chart and record link. */
final class StatsDashboard {
    final RecordsActivity a;final LinearLayout root;final StatisticsData all,d;final StatsPeriod range;
    final TreeMap<String,Long> daily=new TreeMap<>();final Map<String,Long> categories=new TreeMap<>(),tasks=new TreeMap<>();
    interface Actions {void range(int mode,long anchor);void details(String key);}
    final Actions actions;
    StatsDashboard(RecordsActivity a,LinearLayout root,StatisticsData all,StatsPeriod range,Actions actions){this.a=a;this.root=root;this.all=all;this.range=range;this.actions=actions;d=all.filtered(range);
        for(StudyRepository.Session s:d.sessions)if(StatisticsData.counted(s)){add(daily,StatisticsData.day(s),Math.max(0,s.actual));add(categories,all.category(s),Math.max(0,s.actual));}
        for(StatisticsData.CompletedTask t:d.completed)add(tasks,t.category,1);
    }
    static void add(Map<String,Long> map,String key,long n){map.put(key,map.getOrDefault(key,0L)+n);}
    String l(String zh,String en){return AppUi.label(a,zh,en);}
    TextView text(String value,int size,int color,boolean bold){return AppUi.text(a,value,size,color,bold);}
    String time(long s){return AppUi.duration(a,s);}
    GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(AppUi.dp(a,radius));return g;}
    LinearLayout section(String name){LinearLayout c=AppUi.card(a);c.setBackground(bg(AppUi.SURFACE,18));c.addView(text(name,19,AppUi.INK,true));AppUi.space(c,10);root.addView(c);return c;}
    TextView control(String name,Runnable click){TextView t=text(name,14,AppUi.GREEN,true);t.setGravity(Gravity.CENTER);t.setMinHeight(AppUi.dp(a,44));t.setFocusable(true);t.setOnClickListener(v->click.run());return t;}
    void render(){
        LinearLayout tabs=new LinearLayout(a);tabs.setPadding(4,4,4,4);tabs.setBackground(bg(AppUi.SOFT,24));String[] names=l("日,周,月,年,总","Day,Week,Month,Year,All").split(",");
        for(int i=0;i<5;i++){final int mode=i;TextView t=control(names[i],()->actions.range(mode,range.anchor));if(i==range.mode)t.setBackground(bg(AppUi.SURFACE,22));t.setSelected(i==range.mode);tabs.addView(t,new LinearLayout.LayoutParams(0,AppUi.dp(a,44),1));}root.addView(tabs);
        LinearLayout nav=new LinearLayout(a);nav.setGravity(Gravity.CENTER_VERTICAL);root.addView(nav);
        if(range.mode!=StatsPeriod.ALL){TextView previous=control("‹",()->actions.range(range.mode,range.shifted(-1)));previous.setContentDescription(l("上一个周期","Previous period"));nav.addView(previous,new LinearLayout.LayoutParams(AppUi.dp(a,44),AppUi.dp(a,48)));}
        TextView label=control(label(a,range),()->chooseDate());nav.addView(label,new LinearLayout.LayoutParams(0,AppUi.dp(a,48),1));
        if(range.mode!=StatsPeriod.ALL){TextView next=control("›",()->actions.range(range.mode,range.shifted(1)));next.setContentDescription(l("下一个周期","Next period"));nav.addView(next,new LinearLayout.LayoutParams(AppUi.dp(a,44),AppUi.dp(a,48)));}
        LinearLayout overview=section(l("概览","Overview"));LinearLayout r1=new LinearLayout(a),r2=new LinearLayout(a);overview.addView(r1);overview.addView(r2);
        tile(r1,l("专注总时长","Focus time"),time(d.free+d.timed),"all_focus");tile(r1,l("已完成任务","Completed tasks"),""+d.completed.size(),"all_tasks");
        tile(r2,l("专注次数","Focus sessions"),""+d.totalCount,"all_focus");tile(r2,l("专注天数","Active days"),""+daily.values().stream().filter(v->v>0).count(),"all_focus");
        AppUi.space(overview,10);overview.addView(text(l("自由专注：","Stopwatch: ")+time(d.free)+l("\n定时专注：","\nCountdown: ")+time(d.timed),13,AppUi.GRAY,false));
        if(range.mode==StatsPeriod.DAY)timeline();else if(range.mode==StatsPeriod.MONTH)month(range.start);else if(range.mode==StatsPeriod.YEAR)year();
        distribution();bars();
        recordModule(l("专注记录","Focus records"),"all_focus");
        LinearLayout links=recordModule(l("任务记录","Task records"),"all_tasks");
        if(d.unknownDates>0)links.addView(text(l("含未记录完成日期的历史任务：","Historical tasks without a completion date: ")+d.unknownDates,12,AppUi.GRAY,false));
    }
    LinearLayout recordModule(String name,String key){
        LinearLayout card=AppUi.card(a);card.setBackground(bg(AppUi.SURFACE,18));
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(AppUi.dp(a,44));
        row.addView(text(name,19,AppUi.INK,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text("›",24,AppUi.GREEN,false);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(AppUi.dp(a,32),-2));
        card.addView(row);card.setFocusable(true);card.setContentDescription(name);card.setOnClickListener(v->actions.details(key));root.addView(card);return card;
    }
    static String label(android.content.Context c,StatsPeriod r){if(r.mode==4)return AppUi.label(c,"全部历史","All time");String pattern=r.mode==0?"yyyy-MM-dd":r.mode==2?"yyyy-MM":"yyyy";if(r.mode==1)return CalendarDates.key(r.start)+" – "+CalendarDates.key(CalendarDates.plus(r.end,Calendar.DAY_OF_MONTH,-1));return new SimpleDateFormat(pattern,L10n.locale(c)).format(r.start.getTime());}
    void chooseDate(){if(range.mode==4)return;Calendar c=CalendarDates.day(range.anchor);new DatePickerDialog(a,(view,y,m,day)->actions.range(range.mode,CalendarDates.date(y,m,day).getTimeInMillis()),c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();}
    void tile(LinearLayout row,String name,String value,String key){LinearLayout box=new LinearLayout(a);box.setOrientation(1);int p=AppUi.dp(a,10);box.setPadding(p,p,p,p);box.setBackground(bg(AppUi.SOFT,12));box.addView(text(name,13,AppUi.INK,true));TextView n=text(value,23,AppUi.GREEN,true);n.setMaxLines(1);n.setAutoSizeTextTypeUniformWithConfiguration(12,23,1,android.util.TypedValue.COMPLEX_UNIT_SP);box.addView(n,new LinearLayout.LayoutParams(-1,AppUi.dp(a,40)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(4,4,4,4);row.addView(box,lp);box.setOnClickListener(v->actions.details(key));box.setFocusable(true);box.setContentDescription(name+": "+value);}
    void timeline(){
        LinearLayout card=section(l("当天专注时间线","Daily focus timeline"));
        List<StudyRepository.Session> records=new ArrayList<>();
        for(StudyRepository.Session session:d.sessions)if(StatisticsData.counted(session))records.add(session);
        records.sort(Comparator.comparingLong(session->session.startedAt));
        if(records.isEmpty()){card.addView(text(l("当天暂无专注记录","No focus records for this day"),14,AppUi.GRAY,false));return;}
        for(int i=0;i<records.size();i++){
            StudyRepository.Session session=records.get(i);
            LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.TOP);
            LinearLayout clock=new LinearLayout(a);clock.setOrientation(LinearLayout.VERTICAL);
            clock.setPadding(0,AppUi.dp(a,10),0,AppUi.dp(a,20));
            TextView start=text(timelineTime(session.startedAt),11,AppUi.INK,true);
            TextView end=text(timelineTime(session.endedAt),11,AppUi.GRAY,false);
            clock.addView(start);clock.addView(new android.view.View(a),new LinearLayout.LayoutParams(1,0,1));clock.addView(end);
            row.addView(clock,new LinearLayout.LayoutParams(AppUi.dp(a,46),-1));
            final boolean first=i==0,last=i==records.size()-1;
            android.view.View axis=new android.view.View(a){
                final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(android.graphics.Canvas canvas){
                    float x=getWidth()/2f,top=AppUi.dp(a,18),bottom=getHeight()-AppUi.dp(a,26);
                    paint.setStrokeWidth(AppUi.dp(a,1));paint.setColor(AppUi.SOFT);
                    canvas.drawLine(x,first?top:0,x,last?bottom:getHeight(),paint);
                    paint.setColor(AppUi.GREEN);paint.setStrokeWidth(AppUi.dp(a,3));paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                    canvas.drawLine(x,top,x,bottom,paint);canvas.drawCircle(x,top,AppUi.dp(a,4),paint);
                    paint.setColor(AppUi.SURFACE);canvas.drawCircle(x,top,AppUi.dp(a,2),paint);
                }
            };
            axis.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(axis,new LinearLayout.LayoutParams(AppUi.dp(a,20),-1));
            LinearLayout entry=new LinearLayout(a);entry.setOrientation(LinearLayout.VERTICAL);
            int padding=AppUi.dp(a,12);entry.setPadding(padding,padding,padding,padding);entry.setBackground(bg(AppUi.SOFT,14));
            TextView name=text(L10n.sessionTitle(a,session),15,AppUi.INK,true);entry.addView(name);
            TextView duration=text(time(session.actual),14,AppUi.INK,false);duration.setPadding(0,AppUi.dp(a,6),0,0);entry.addView(duration);
            TextView meta=text(category(all.category(session))+" · "+(session.free?l("正计时","Stopwatch"):l("倒计时","Countdown")),11,AppUi.GRAY,false);
            meta.setPadding(0,AppUi.dp(a,5),0,0);entry.addView(meta);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=AppUi.dp(a,4);lp.bottomMargin=AppUi.dp(a,12);row.addView(entry,lp);
            card.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
    }
    String timelineTime(long timestamp){
        String pattern=CalendarDates.key(timestamp).equals(CalendarDates.key(range.start))?"HH:mm":"MM-dd\nHH:mm";
        return new SimpleDateFormat(pattern,L10n.locale(a)).format(new Date(timestamp));
    }
    void year(){
        int year=range.start.get(Calendar.YEAR);
        LinearLayout card=section(year+l("年 · 专注热力图"," · Focus heatmap"));
        for(int row=0;row<4;row++){
            LinearLayout months=new LinearLayout(a);
            for(int column=0;column<3;column++){
                Calendar first=CalendarDates.date(year,row*3+column,1);
                LinearLayout cell=new LinearLayout(a);cell.setOrientation(LinearLayout.VERTICAL);
                String name=new SimpleDateFormat(l("M月","MMM"),L10n.locale(a)).format(first.getTime());
                TextView heading=text(name,14,AppUi.GREEN,true);heading.setPadding(AppUi.dp(a,2),0,0,AppUi.dp(a,4));cell.addView(heading);
                long[] values=new long[first.getActualMaximum(Calendar.DAY_OF_MONTH)];String[] labels=new String[values.length];
                for(int day=0;day<values.length;day++){Calendar date=CalendarDates.plus(first,Calendar.DAY_OF_MONTH,day);labels[day]=CalendarDates.key(date);values[day]=daily.getOrDefault(labels[day],0L);}
                StatsGraphic chart=new StatsGraphic(a,StatsGraphic.HEAT,values,labels);chart.month=(Calendar)first.clone();chart.compactHeat=true;chart.setMinimumHeight(0);
                chart.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                cell.addView(chart,new LinearLayout.LayoutParams(-1,AppUi.dp(a,112)));
                cell.setFocusable(true);cell.setContentDescription(year+" "+name);cell.setOnClickListener(v->actions.range(StatsPeriod.MONTH,first.getTimeInMillis()));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(column==0?0:AppUi.dp(a,8),0,0,AppUi.dp(a,16));months.addView(cell,lp);
            }
            card.addView(months);
        }
        card.addView(text(l("颜色越深，专注越久；点击月份查看详情。","Darker means more focus. Tap a month for details."),12,AppUi.GRAY,false));
    }
    void month(Calendar m){LinearLayout card=section(new SimpleDateFormat("yyyy-MM",L10n.locale(a)).format(m.getTime())+l(" · 专注热力图"," · Focus heatmap"));long[] values=new long[m.getActualMaximum(Calendar.DAY_OF_MONTH)];String[] labels=new String[values.length];for(int i=0;i<values.length;i++){Calendar day=(Calendar)m.clone();day.set(Calendar.DAY_OF_MONTH,i+1);labels[i]=CalendarDates.key(day);values[i]=daily.getOrDefault(labels[i],0L);}StatsGraphic chart=new StatsGraphic(a,StatsGraphic.HEAT,values,labels);chart.month=(Calendar)m.clone();chart.onDay=day->actions.range(StatsPeriod.DAY,day);card.addView(chart,new LinearLayout.LayoutParams(-1,AppUi.dp(a,300)));card.addView(text(l("无记录 · <30分 · 30–60分 · 1–2小时 · ≥2小时","None · <30m · 30–60m · 1–2h · ≥2h"),11,AppUi.GRAY,false));}
    void bars(){
        LinearLayout card=section(l("专注时长","Focus duration"));
        ArrayList<Long> vals=new ArrayList<>();ArrayList<String> labs=new ArrayList<>();
        if(range.mode==StatsPeriod.DAY){
            long[] periods=new long[6];
            for(StudyRepository.Session session:d.sessions)if(StatisticsData.counted(session)){
                Calendar ended=Calendar.getInstance();ended.setTimeInMillis(session.endedAt);
                periods[ended.get(Calendar.HOUR_OF_DAY)/4]+=Math.max(0,session.actual);
            }
            for(int i=0;i<6;i++){labs.add(String.format(Locale.US,"%d–%d",i*4,(i+1)*4)+l("时","h"));vals.add(periods[i]);}
            card.addView(text(l("每4小时汇总 · 00–24时","4-hour totals · 00–24h"),12,AppUi.GRAY,false));
        }else if(range.mode==StatsPeriod.WEEK||range.mode==StatsPeriod.MONTH){
            for(Calendar day=(Calendar)range.start.clone();day.before(range.end);day.add(Calendar.DAY_OF_MONTH,1)){
                labs.add(new SimpleDateFormat("MM/dd",L10n.locale(a)).format(day.getTime()));
                vals.add(daily.getOrDefault(CalendarDates.key(day),0L));
            }
        }else{
            TreeMap<String,Long> months=new TreeMap<>();
            for(Map.Entry<String,Long> entry:daily.entrySet())add(months,entry.getKey().substring(0,7),entry.getValue());
            Calendar first,last;
            if(range.mode==StatsPeriod.YEAR){first=(Calendar)range.start.clone();last=CalendarDates.plus(range.end,Calendar.MONTH,-1);}
            else if(!months.isEmpty()){
                String begin=months.firstKey(),end=months.lastKey();
                first=CalendarDates.date(Integer.parseInt(begin.substring(0,4)),Integer.parseInt(begin.substring(5))-1,1);
                last=CalendarDates.date(Integer.parseInt(end.substring(0,4)),Integer.parseInt(end.substring(5))-1,1);
            }else{card.addView(text(l("暂无专注记录","No focus records"),14,AppUi.GRAY,false));return;}
            for(Calendar month=(Calendar)first.clone();!month.after(last);month.add(Calendar.MONTH,1)){
                String key=new SimpleDateFormat("yyyy-MM",Locale.US).format(month.getTime());
                labs.add(range.mode==StatsPeriod.YEAR?new SimpleDateFormat("MM",L10n.locale(a)).format(month.getTime()):key);
                vals.add(months.getOrDefault(key,0L));
            }
        }
        long[] values=new long[vals.size()];for(int i=0;i<values.length;i++)values[i]=vals.get(i);
        StatsGraphic chart=new StatsGraphic(a,StatsGraphic.BARS,values,labs.toArray(new String[0]));
        if(values.length>7){
            chart.setMinimumWidth(AppUi.dp(a,values.length*64));
            HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setFillViewport(true);
            scroll.addView(chart,new android.view.ViewGroup.LayoutParams(AppUi.dp(a,values.length*64),AppUi.dp(a,210)));
            card.addView(scroll,new LinearLayout.LayoutParams(-1,AppUi.dp(a,210)));
            card.addView(text(l("左右滑动查看","Swipe horizontally to view"),12,AppUi.GRAY,false));
        }else card.addView(chart,new LinearLayout.LayoutParams(-1,AppUi.dp(a,210)));
    }
    String category(String key){return key.equals("未关联任务")?l("未关联任务","No linked task"):L10n.category(a,key);}
    List<Map.Entry<String,Long>> sorted(Map<String,Long> map){List<Map.Entry<String,Long>> entries=new ArrayList<>(map.entrySet());entries.removeIf(e->e.getValue()<=0);entries.sort((x,y)->Long.compare(y.getValue(),x.getValue()));return entries;}
    void distribution(){
        LinearLayout card=section(l("专注分类分布","Focus by category"));List<Map.Entry<String,Long>> entries=sorted(categories);
        long[] values=new long[entries.size()];String[] labels=new String[entries.size()];
        for(int i=0;i<values.length;i++){values[i]=entries.get(i).getValue();labels[i]=category(entries.get(i).getKey());}
        if(values.length==0)card.addView(text(l("暂无可统计的专注时长","No focus time to display"),14,AppUi.GRAY,false));
        else{
            card.addView(new StatsGraphic(a,1,values,labels),new LinearLayout.LayoutParams(-1,AppUi.dp(a,Math.max(280,values.length*48))));
            for(int i=0;i<values.length;i++){
                LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,AppUi.dp(a,12),0,AppUi.dp(a,12));
                TextView icon=text("●",21,StatsGraphic.color(i),false);row.addView(icon,new LinearLayout.LayoutParams(AppUi.dp(a,28),-2));
                LinearLayout info=new LinearLayout(a);info.setOrientation(LinearLayout.VERTICAL);
                LinearLayout heading=new LinearLayout(a);heading.setGravity(Gravity.CENTER_VERTICAL);
                TextView name=text(labels[i],15,AppUi.INK,true);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));
                TextView share=text(percent(values[i],d.free+d.timed),13,AppUi.GRAY,false);heading.addView(share);info.addView(heading);
                TextView duration=text(time(values[i]),13,AppUi.GRAY,false);info.addView(duration);
                ProgressBar bar=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress((int)(1000.0*values[i]/(d.free+d.timed)));
                bar.setProgressTintList(android.content.res.ColorStateList.valueOf(StatsGraphic.color(i)));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(AppUi.SOFT));
                info.addView(bar,new LinearLayout.LayoutParams(-1,AppUi.dp(a,12)));row.addView(info,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);
            }
        }

    }
    String percent(long part,long total){return String.format(L10n.locale(a),"%.1f%%",total==0?0:100.0*part/total);}

}
