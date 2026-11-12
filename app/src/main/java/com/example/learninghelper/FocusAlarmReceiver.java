package com.example.learninghelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class FocusAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!FocusCoordinator.ACTION_DUE.equals(intent.getAction()))return;
        long id=intent.getLongExtra("session_id",-1);if(id<0)return;
        PendingResult pending=goAsync();Context app=context.getApplicationContext();
        StudyRepository.IO.execute(()->{
            try{
                StudyRepository.Session s=StudyRepository.session(app,id);
                if(s==null||s.status!=TaskProvider.RUNNING)return;
                if(!s.owned()){
                    FocusCoordinator.notice(app,s,"上次专注需要确认","应用曾退出，请打开应用确认中断记录。",true);
                }else if(s.remaining()>0){FocusCoordinator.schedule(app,s);}
                else FocusCoordinator.finish(app,id,TaskProvider.COMPLETED,true);
            }catch(Exception e){Log.e("FocusAlarmReceiver","处理到期事件失败",e);}
            finally{pending.finish();}
        });
    }
}
