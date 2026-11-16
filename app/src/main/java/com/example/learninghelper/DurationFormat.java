package com.example.learninghelper;

import java.util.Locale;

/** 只调整显示，不修改实际记录的秒数。 */
public final class DurationFormat {
    private DurationFormat() {}
    public static String format(long seconds,int mode){
        long s=Math.max(0,seconds),minutes=s/60, hours=s/3600;
        if(mode==1)return s/86400+"天 "+hours%24+"时 "+minutes%60+"分 "+s%60+"秒";
        if(mode==2)return minutes+"分 "+s%60+"秒";
        if(mode==3)return String.format(Locale.US,"%02d:%02d:%02d",hours,minutes%60,s%60);
        return hours+"时 "+minutes%60+"分 "+s%60+"秒";
    }
}
