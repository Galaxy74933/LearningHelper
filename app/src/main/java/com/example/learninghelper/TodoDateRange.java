package com.example.learninghelper;

import java.util.Calendar;

/** 按本地截止日期筛选；区间包含起始日，不包含结束日。 */
public final class TodoDateRange {
    public static final String[] LABELS={"全部","今天","明天","未来三天","本周","下周","本月","下月","未安排日期","已过期"};
    private TodoDateRange() {}

    public static boolean matches(String date,int range,Calendar today) {
        if(range==0)return true;
        if(range==8)return date==null||date.isEmpty();
        if(date==null||date.isEmpty())return false;
        Calendar start=CalendarDates.day(today.getTimeInMillis()),end;
        if(range==9)return date.compareTo(CalendarDates.key(start))<0;
        switch(range){
            case 1:end=CalendarDates.plus(start,Calendar.DATE,1);break;
            case 2:start.add(Calendar.DATE,1);end=CalendarDates.plus(start,Calendar.DATE,1);break;
            case 3:end=CalendarDates.plus(start,Calendar.DATE,3);break;
            case 4:start=CalendarDates.week(start);end=CalendarDates.plus(start,Calendar.DATE,7);break;
            case 5:start=CalendarDates.plus(CalendarDates.week(start),Calendar.DATE,7);end=CalendarDates.plus(start,Calendar.DATE,7);break;
            case 6:start.set(Calendar.DATE,1);end=CalendarDates.plus(start,Calendar.MONTH,1);break;
            case 7:start.set(Calendar.DATE,1);start.add(Calendar.MONTH,1);end=CalendarDates.plus(start,Calendar.MONTH,1);break;
            default:return true;
        }
        return date.compareTo(CalendarDates.key(start))>=0&&date.compareTo(CalendarDates.key(end))<0;
    }
}
