package com.example.learninghelper;

import android.app.Activity;
import android.view.MotionEvent;

/** 在窗口分发给输入框和按钮前统一处理页面滚轮。弹窗仍使用自己的窗口。 */
public class WheelActivity extends Activity {
    @Override protected void attachBaseContext(android.content.Context base) {
        android.content.res.Configuration config=new android.content.res.Configuration(base.getResources().getConfiguration());
        config.setLocale(L10n.locale(base));
        super.attachBaseContext(base.createConfigurationContext(config));
    }
    AppUi.Page scrollPage;
    @Override public void startActivityForResult(android.content.Intent intent,int request,android.os.Bundle options){super.startActivityForResult(intent,request,options);overridePendingTransition(0,0);}
    @Override public void finish(){super.finish();overridePendingTransition(0,0);}

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if(scrollPage!=null && AppUi.handleWheel(scrollPage,event)) return true;
        return super.dispatchGenericMotionEvent(event);
    }
}
