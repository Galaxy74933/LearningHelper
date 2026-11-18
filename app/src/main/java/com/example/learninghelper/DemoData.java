package com.example.learninghelper;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;
import java.io.*;

/** Explicit, repeatable classroom fixtures; never loaded on app startup. */
final class DemoData {
    static JSONObject create(Context c)throws Exception{
        JSONObject fixture;
        try(InputStream in=c.getAssets().open("demo-data.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            fixture=new JSONObject(out.toString("UTF-8"));
        }
        Calendar base=Calendar.getInstance(TimeZone.getTimeZone("UTC"));base.clear();base.set(2026,10,18);
        Calendar today=Calendar.getInstance();today.set(Calendar.HOUR_OF_DAY,0);today.set(Calendar.MINUTE,0);today.set(Calendar.SECOND,0);today.set(Calendar.MILLISECOND,0);
        long shift=today.getTimeInMillis()-base.getTimeInMillis(),now=System.currentTimeMillis();
        JSONArray tasks=fixture.getJSONArray("tasks"),sessions=fixture.getJSONArray("sessions");Set<String> categories=new HashSet<>();
        for(int i=0;i<tasks.length();i++){
            JSONObject t=tasks.getJSONObject(i);categories.add(t.optString("category"));
            for(String field:new String[]{"date","end_date"})t.put(field,date(t.optString(field),shift));
            JSONArray dates=t.getJSONArray("schedule_dates");for(int j=0;j<dates.length();j++)dates.put(j,date(dates.getString(j),shift));
            JSONObject old=t.getJSONObject("completed_dates"),done=new JSONObject();Iterator<String> keys=old.keys();
            while(keys.hasNext()){String k=keys.next();done.put(date(k,shift),Math.min(now,old.getLong(k)+shift));}t.put("completed_dates",done);
            if(t.optLong("completed_at")>0)t.put("completed_at",Math.min(now,t.getLong("completed_at")+shift));TodoSchedule.normalize(t);
        }
        JSONArray records=new JSONArray();
            for(int i=0;i<sessions.length();i++){
                JSONObject s=sessions.getJSONObject(i);long end=s.getLong("ended_at")+shift,start=s.getLong("started_at")+shift;
                // Morning imports keep all simulated sessions in the past.
                if(end>now){Calendar d=Calendar.getInstance();d.setTimeInMillis(end);d.add(Calendar.DATE,-1);long delta=end-d.getTimeInMillis();end-=delta;start-=delta;}
                ContentValues v=new ContentValues();
                for(String k:new String[]{"todo_id","title_snapshot","owner_token","memo"})v.put(k,s.optString(k));
                for(String k:new String[]{"task_id","planned_seconds","actual_seconds","status","is_demo","free_focus"})v.put(k,s.optLong(k));
                v.put("started_at",start);v.put("ended_at",end);v.put("started_elapsed",0);Calendar day=Calendar.getInstance();day.setTimeInMillis(end);v.put("ended_day",CalendarDates.key(day));
                JSONObject record=new JSONObject();for(String key:v.keySet())record.put(key,v.get(key));records.put(record);
            }
        return new JSONObject().put("todos",tasks).put("categories",new JSONArray(categories)).put("tasks",new JSONArray()).put("sessions",records);
    }
    private static String date(String text,long shift)throws Exception{
        if(text.isEmpty())return text;
        java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));long old=f.parse(text).getTime();
        Calendar local=Calendar.getInstance();local.setTimeInMillis(old+shift);return CalendarDates.key(local);
    }
}
