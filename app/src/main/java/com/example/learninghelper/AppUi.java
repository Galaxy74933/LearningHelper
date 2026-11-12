package com.example.learninghelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class AppUi {
    private AppUi() {}
    public static final int GREEN = Color.rgb(37,120,104), INK = Color.rgb(23,37,42),
            GRAY = Color.rgb(101,117,123), BG = Color.rgb(245,247,248), SOFT = Color.rgb(231,242,236);
    public static final int[] DURATIONS = {900,1500,2700,3600,10,30};
    public static final String[] DURATION_LABELS = {"15 分钟","25 分钟","45 分钟","60 分钟","10 秒","30 秒"};
    public static final String[] SOUNDS = {"关闭声音","雨声（合成）","海浪（合成）","林间声（合成）"};
    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("focus_preferences", Context.MODE_PRIVATE); }
    public static int dp(Context c, int v) { return Math.round(v*c.getResources().getDisplayMetrics().density); }
    public static void toast(Context c, String message) { Toast.makeText(c,message,Toast.LENGTH_LONG).show(); }
    public static void error(Activity a, Exception e) {
        new AlertDialog.Builder(a).setTitle("操作未完成").setMessage(e.getMessage()==null?"请重试，并查看 Logcat":e.getMessage())
                .setPositiveButton("知道了",null).show();
    }
    public static TextView text(Context c,String value,int size,int color,boolean bold) {
        TextView v=new TextView(c); v.setText(value);v.setTextSize(size);v.setTextColor(color);
        v.setLineSpacing(dp(c,3),1f);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    public static Button button(Context c,String label,boolean primary) {
        Button b=new Button(c);b.setText(label);b.setTextSize(15);b.setAllCaps(false);
        b.setTextColor(primary?Color.WHITE:GREEN);b.setBackgroundTintList(ColorStateList.valueOf(primary?GREEN:SOFT));
        b.setMinWidth(0);b.setMinimumWidth(0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(c,52));lp.topMargin=dp(c,8);b.setLayoutParams(lp);return b;
    }
    public static LinearLayout card(Context c) {
        LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);int pad=dp(c,16);v.setPadding(pad,pad,pad,pad);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(c,14));bg.setStroke(dp(c,1),Color.rgb(221,229,232));v.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(c,12);v.setLayoutParams(lp);return v;
    }
    public static void space(LinearLayout parent,int height) {
        View v=new View(parent.getContext());parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),height)));
    }
    public static void weighted(LinearLayout row,View view) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(row.getContext(),52),1);lp.setMargins(dp(row.getContext(),2),0,dp(row.getContext(),2),0);row.addView(view,lp);
    }
    public static final class Page { public LinearLayout root, content; }
    public static Page page(Activity a,String title,String subtitle) {
        if(a.getActionBar()!=null)a.getActionBar().hide();
        if(Build.VERSION.SDK_INT>=30) a.getWindow().setDecorFitsSystemWindows(false);
        else a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        int flags=a.getWindow().getDecorView().getSystemUiVisibility();
        a.getWindow().getDecorView().setSystemUiVisibility(flags|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        Page p=new Page();p.root=new LinearLayout(a);p.root.setOrientation(LinearLayout.VERTICAL);p.root.setBackgroundColor(BG);
        p.root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout heading=new LinearLayout(a);heading.setOrientation(LinearLayout.VERTICAL);heading.setPadding(dp(a,20),dp(a,16),dp(a,20),dp(a,8));
        heading.addView(text(a,title,25,INK,true));if(subtitle!=null&&!subtitle.isEmpty())heading.addView(text(a,subtitle,14,GRAY,false));p.root.addView(heading);
        ScrollView scroll=new WheelScrollView(a);scroll.setFillViewport(true);scroll.setFocusable(true);scroll.setFocusableInTouchMode(true);
        p.content=new LinearLayout(a);p.content.setOrientation(LinearLayout.VERTICAL);p.content.setPadding(dp(a,20),dp(a,4),dp(a,20),dp(a,24));scroll.addView(p.content);
        p.root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));a.setContentView(p.root);p.root.requestApplyInsets();return p;
    }
    public static void navigation(Activity a,Page page,int selected) {
        LinearLayout row=new LinearLayout(a);row.setPadding(dp(a,8),dp(a,4),dp(a,8),dp(a,4));row.setBackgroundColor(Color.WHITE);
        String[] labels={"任务","记录","统计","设置"};Class<?>[] classes={MainActivity.class,RecordsActivity.class,StatsActivity.class,SettingsActivity.class};
        for(int i=0;i<labels.length;i++){
            final int index=i;Button b=button(a,labels[i],i==selected);b.setTextSize(14);weighted(row,b);
            b.setOnClickListener(v->{if(index!=selected){a.startActivity(new Intent(a,classes[index]).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));if(!(a instanceof MainActivity))a.finish();}});
        }
        page.root.addView(row);
    }
    public static String duration(int s){return (s/60)+" 分 "+(s%60)+" 秒";}
    private static final class WheelScrollView extends ScrollView {
        WheelScrollView(Context c){super(c);}
        @Override public boolean dispatchGenericMotionEvent(MotionEvent event){
            if(event.getAction()==MotionEvent.ACTION_SCROLL && (event.getSource() & InputDevice.SOURCE_CLASS_POINTER)!=0){
                float dy=event.getAxisValue(MotionEvent.AXIS_VSCROLL); if(dy!=0){scrollBy(0,Math.round(-dy*getResources().getDisplayMetrics().density*56)); return true;}
            }
            return super.dispatchGenericMotionEvent(event);
        }
    }
}

