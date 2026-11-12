package com.example.learninghelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 通知栏“停止声音”按钮的广播入口；只停声音，不结束专注。 */
public class NoiseActionReceiver extends BroadcastReceiver {
    public static final String STOP="com.example.learninghelper.STOP_SOUND";
    @Override public void onReceive(Context context,Intent intent){
        if(STOP.equals(intent.getAction()))NoiseService.stopMatching(context,intent.getLongExtra("session_id",-1));
    }
}
