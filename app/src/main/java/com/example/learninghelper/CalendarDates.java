package com.example.learninghelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** 日历按设备本地日期计算，以周一为一周的开始。 */
public final class CalendarDates {
    private CalendarDates() {}
    public static Calendar day(long time) {
        Calendar c=Calendar.getInstance();c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);
        c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c;
    }
    public static Calendar date(int year,int month,int day) {
        Calendar c=day(System.currentTimeMillis());c.set(Calendar.DAY_OF_MONTH,1);
        c.set(Calendar.YEAR,year);c.set(Calendar.MONTH,month);c.set(Calendar.DAY_OF_MONTH,day);return c;
    }
    public static Calendar week(Calendar date) {
        Calendar c=day(date.getTimeInMillis());c.add(Calendar.DAY_OF_MONTH,-((c.get(Calendar.DAY_OF_WEEK)+5)%7));return c;
    }
    public static String key(Calendar c) {return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(c.getTime());}
    public static String key(long millis) {return key(day(millis));}
    public static Calendar plus(Calendar c,int field,int amount) {Calendar result=(Calendar)c.clone();result.add(field,amount);return result;}
}
