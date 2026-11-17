package com.example.learninghelper;

import java.util.Calendar;

/** Local calendar boundaries, end exclusive; weeks start on Monday. */
final class StatsPeriod {
    static final int DAY=0,WEEK=1,MONTH=2,YEAR=3,ALL=4;
    final int mode; final long anchor; final Calendar start,end;
    StatsPeriod(int mode,long anchor){
        this.anchor=anchor;this.mode=Math.max(0,Math.min(4,mode));Calendar c=CalendarDates.day(anchor);
        if(mode==WEEK)c=CalendarDates.week(c);
        if(mode==MONTH||mode==YEAR)c.set(Calendar.DAY_OF_MONTH,1);
        if(mode==YEAR)c.set(Calendar.MONTH,0);
        start=c;end=(Calendar)c.clone();
        end.add(mode==YEAR?Calendar.YEAR:mode==MONTH?Calendar.MONTH:Calendar.DAY_OF_MONTH,mode==WEEK?7:1);
    }
    boolean contains(long time){return mode==ALL||(time>0&&time>=start.getTimeInMillis()&&time<end.getTimeInMillis());}
    boolean containsDay(String day){return mode==ALL||(day!=null&&day.compareTo(CalendarDates.key(start))>=0&&day.compareTo(CalendarDates.key(end))<0);}
    long shifted(int direction){Calendar c=(Calendar)start.clone();c.add(mode==YEAR?Calendar.YEAR:mode==MONTH?Calendar.MONTH:Calendar.DAY_OF_MONTH,direction*(mode==WEEK?7:1));return c.getTimeInMillis();}
}
