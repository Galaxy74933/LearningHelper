package com.example.learninghelper;

import android.content.Context;
import org.json.*;
import java.util.*;

/** A schedule belongs to one stable todo ID; daily completions are keyed by local date. */
final class TodoSchedule {
    static Calendar parse(String key){String[] p=key.split("-");return CalendarDates.date(Integer.parseInt(p[0]),Integer.parseInt(p[1])-1,Integer.parseInt(p[2]));}
    static SortedSet<String> dates(JSONObject t){
        TreeSet<String> result=new TreeSet<>();String mode=t.optString("schedule_mode","single");
        if(mode.equals("multiple")){JSONArray a=t.optJSONArray("schedule_dates");if(a!=null)for(int i=0;i<a.length();i++)if(!a.optString(i).isEmpty())result.add(a.optString(i));}
        else if(mode.equals("range")){
            String start=t.optString("date"),end=t.optString("end_date",start);
            if(!start.isEmpty()&&!end.isEmpty())try{Calendar c=parse(start);while(CalendarDates.key(c).compareTo(end)<=0){result.add(CalendarDates.key(c));c.add(Calendar.DATE,1);}}catch(Exception ignored){}
        }else if(!t.optString("date").isEmpty())result.add(t.optString("date"));
        return result;
    }
    static boolean daily(JSONObject t){return t.optString("completion_mode").equals("daily");}
    static boolean on(JSONObject t,String day){String mode=t.optString("schedule_mode","single");if(mode.equals("range"))return !t.optString("date").isEmpty()&&day.compareTo(t.optString("date"))>=0&&day.compareTo(t.optString("end_date",t.optString("date")))<=0;return dates(t).contains(day);}
    static boolean done(JSONObject t,String day){if(!daily(t))return t.optBoolean("done");JSONObject a=t.optJSONObject("completed_dates");return a!=null&&a.optLong(day)>0;}
    static int completed(JSONObject t){int n=0;for(String d:dates(t))if(done(t,d))n++;return n;}
    static void normalize(JSONObject t) throws JSONException {
        if(!daily(t))return;
        JSONObject old=t.optJSONObject("completed_dates"),kept=new JSONObject();long last=0;SortedSet<String> dates=dates(t);
        for(String d:dates)if(old!=null&&old.optLong(d)>0){kept.put(d,old.optLong(d));last=Math.max(last,old.optLong(d));}
        t.put("completed_dates",kept);boolean all=!dates.isEmpty()&&kept.length()==dates.size();t.put("done",all);t.put("completed_at",all?last:0);
    }
    static void complete(JSONObject t,String day,boolean value) throws JSONException {
        if(daily(t)){JSONObject a=t.optJSONObject("completed_dates");if(a==null)a=new JSONObject();if(value)a.put(day,System.currentTimeMillis());else a.remove(day);t.put("completed_dates",a);normalize(t);}
        else {t.put("done",value);t.put("completed_at",value?System.currentTimeMillis():0L);}
    }
    static boolean matches(JSONObject t,int range,Calendar today){
        SortedSet<String> ds=dates(t);if(range==0)return true;if(range==8)return ds.isEmpty();
        if(range==9)return !t.optBoolean("done")&&!ds.isEmpty()&&ds.last().compareTo(CalendarDates.key(today))<0;
        for(String d:ds)if(TodoDateRange.matches(d,range,today))return true;return false;
    }
    static String description(Context c,JSONObject t){
        SortedSet<String> ds=dates(t);String text=ds.isEmpty()?AppUi.label(c,"未安排","Unscheduled"):t.optString("schedule_mode").equals("multiple")?AppUi.label(c,"指定日期 · ","Selected dates · ")+ds.size():ds.first()+(ds.size()>1?" → "+ds.last():"");
        String tm=t.optString("time_mode","all");text+=" · "+(tm.equals("all")?AppUi.label(c,"全天","All day"):t.optString("start_time")+(tm.equals("range")?"–"+t.optString("end_time"):""));
        if(daily(t))text+=" · "+completed(t)+"/"+ds.size();return text;
    }
}
