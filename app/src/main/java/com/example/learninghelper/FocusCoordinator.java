package com.example.learninghelper;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

/** 所有结束入口共用这里，避免页面、服务和闹钟重复生成记录。 */
public final class FocusCoordinator {
    private FocusCoordinator() {}
    public static final String REMINDERS="focus_reminders", PLAYBACK="focus_playback";
    public static final String ACTION_DUE="com.example.learninghelper.FOCUS_DUE";
    public static final int RESULT_NOTICE=2002;
    public static void channels(Context c){
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(REMINDERS,"专注结束提醒",NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(PLAYBACK,"白噪音播放",NotificationManager.IMPORTANCE_LOW));
    }
    public static boolean notificationsAllowed(Context c){
        return (Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
                &&c.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }
    private static PendingIntent alarmIntent(Context c,long id){
        Intent i=new Intent(c,FocusAlarmReceiver.class).setAction(ACTION_DUE)
                .setData(Uri.parse("learninghelper://alarm/"+id)).putExtra("session_id",id);
        return PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static boolean schedule(Context c,StudyRepository.Session s){
        if(s==null||s.status!=TaskProvider.RUNNING||!s.owned())return false;
        try{
            c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    s.startedElapsed+s.planned*1000L,alarmIntent(c,s.id));return true;
        }catch(RuntimeException e){Log.e("FocusAlarm","安排提醒失败",e);return false;}
    }
    public static void cancel(Context c,long id){
        PendingIntent pi=alarmIntent(c,id);c.getSystemService(AlarmManager.class).cancel(pi);pi.cancel();
    }
    public static StudyRepository.Session finish(Context c,long id,int requested,boolean notify){
        int changed=StudyRepository.updateStatus(c,id,requested);
        StudyRepository.Session s=StudyRepository.session(c,id);
        if(s==null||s.status==TaskProvider.RUNNING)throw new IllegalStateException("记录未能结算，请重试");
        try { cancel(c,id); }
        catch (RuntimeException e) { Log.w("FocusAlarm", "记录已保存，但取消提醒失败", e); }
        NoiseService.stopMatching(c,id);
        if(changed>0&&notify){
            String title=s.status==TaskProvider.COMPLETED?"专注完成":"专注已结束";
            String body=s.title+" · "+AppUi.duration(s.actual);
            notice(c,s,title,body,false);
        }
        return s;
    }
    public static void notice(Context c,StudyRepository.Session s,String title,String body,boolean recover){
        channels(c);if(!notificationsAllowed(c))return;
        Intent i=new Intent(c,recover?FocusActivity.class:RecordsActivity.class)
                .setData(Uri.parse("learninghelper://result/"+s.id)).putExtra("session_id",s.id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,REMINDERS).setSmallIcon(R.drawable.ic_focus_notification)
                .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pi).setAutoCancel(true).build();
        try { c.getSystemService(NotificationManager.class).notify(RESULT_NOTICE,n); }
        catch (RuntimeException e) { Log.w("FocusAlarm", "通知未能显示", e); }
    }
    public static void clearNotice(Context c){c.getSystemService(NotificationManager.class).cancel(RESULT_NOTICE);}
}
