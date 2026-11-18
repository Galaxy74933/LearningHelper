package com.example.learninghelper;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** 所有日统计使用本地日历日期；旧数据没有完成日期时不猜测日期。 */
final class StatisticsData {
    static class CompletedTask {
        String title, source, todoId="", occurrenceDay="", category="未分类";
        long taskId;
        long completedAt;
        CompletedTask(String title,String source,long time){this.title=title;this.source=source;this.completedAt=time;}
    }
    final Map<String,String> todoCategories=new HashMap<>();
    final List<CompletedTask> completed=new ArrayList<>();
    List<StudyRepository.Session> sessions;
    final String[] days=new String[7];
    final long[] taskTrend=new long[7], focusTrend=new long[7];
    int todayTasks, todayCount, totalCount, unknownDates;
    long free,timed,todayFree,todayTimed;
    String today;
    static boolean counted(StudyRepository.Session s){return !s.demo&&(s.status==TaskProvider.COMPLETED||s.status==TaskProvider.EARLY_END);}
    static String day(StudyRepository.Session s){return s.endedDay==null||s.endedDay.isEmpty()?CalendarDates.key(s.endedAt):s.endedDay;}
    String category(StudyRepository.Session s){
        if(s.todoId!=null&&!s.todoId.isEmpty())return todoCategories.getOrDefault(s.todoId,"未分类");
        return s.taskId>0?"未分类":"未关联任务";
    }
    StatisticsData filtered(StatsPeriod period){
        StatisticsData d=new StatisticsData();d.today=today;d.todoCategories.putAll(todoCategories);d.sessions=new ArrayList<>();
        for(CompletedTask t:completed)if(period.contains(t.completedAt)){d.completed.add(t);if(t.completedAt<=0)d.unknownDates++;}
        for(StudyRepository.Session s:sessions)if(period.containsDay(day(s))){
            d.sessions.add(s);if(counted(s)){d.totalCount++;if(s.free)d.free+=Math.max(0,s.actual);else d.timed+=Math.max(0,s.actual);}
        }
        return d;
    }
    static StatisticsData load(Context c) throws Exception {
        StatisticsData data=new StatisticsData();
        Calendar cal=Calendar.getInstance();data.today=CalendarDates.key(cal.getTimeInMillis());cal.add(Calendar.DAY_OF_MONTH,-6);
        for(int i=0;i<7;i++){data.days[i]=CalendarDates.key(cal.getTimeInMillis());cal.add(Calendar.DAY_OF_MONTH,1);}
        for(StudyRepository.Task t:StudyRepository.tasks(c))if(t.done){CompletedTask item=new CompletedTask(t.title,"任务",t.completedAt);item.taskId=t.id;data.completed.add(item);}
        JSONArray todos=new JSONArray(c.getSharedPreferences("learning_todos",0).getString("items","[]"));
        for(int i=0;i<todos.length();i++){
            JSONObject t=todos.getJSONObject(i);data.todoCategories.put(t.optString("id"),t.optString("category","未分类"));
            if(TodoSchedule.daily(t)){JSONObject dates=t.optJSONObject("completed_dates");for(String day:TodoSchedule.dates(t))if(dates!=null&&dates.optLong(day)>0){CompletedTask item=new CompletedTask(t.optString("title")+" · "+day,"日历待办",dates.optLong(day));item.todoId=t.optString("id");item.occurrenceDay=day;item.category=t.optString("category","未分类");data.completed.add(item);}}
            else if(t.optBoolean("done")){CompletedTask item=new CompletedTask(t.optString("title"),"日历待办",t.optLong("completed_at",0));item.todoId=t.optString("id");item.category=t.optString("category","未分类");data.completed.add(item);}
        }
        Collections.sort(data.completed,(a,b)->Long.compare(b.completedAt,a.completedAt));
        for(CompletedTask t:data.completed){
            if(t.completedAt<=0){data.unknownDates++;continue;}
            String day=CalendarDates.key(t.completedAt);if(day.equals(data.today))data.todayTasks++;
            for(int i=0;i<7;i++)if(day.equals(data.days[i]))data.taskTrend[i]++;
        }
        data.sessions=StudyRepository.history(c);
        for(StudyRepository.Session s:data.sessions)if(counted(s)){
            data.totalCount++;if(s.free)data.free+=s.actual;else data.timed+=s.actual;
            String day=day(s);if(day.equals(data.today)){data.todayCount++;if(s.free)data.todayFree+=s.actual;else data.todayTimed+=s.actual;}
            for(int i=0;i<7;i++)if(day.equals(data.days[i]))data.focusTrend[i]+=s.actual;
        }
        return data;
    }
}
