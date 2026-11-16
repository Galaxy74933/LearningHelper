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
    public static int GREEN = Color.rgb(37,120,104), INK = Color.rgb(23,37,42),
            GRAY = Color.rgb(101,117,123), BG = Color.rgb(245,247,248), SOFT = Color.rgb(231,242,236);
    public static int SURFACE = Color.WHITE;
    public static boolean dark;
    public static void appearance(Activity a) {
        SharedPreferences p=prefs(a);
        int mode=p.getInt("appearance",0);
        dark=mode==2 || (mode==0 && (android.content.res.Resources.getSystem().getConfiguration().uiMode & 48)==32);
        int[] accents=dark?new int[]{0xff78d5bb,0xff8dbbff,0xffcfabff,0xffffbe87}:new int[]{0xff257868,0xff2868b2,0xff7950a3,0xffa85d22};
        GREEN=accents[Math.max(0,Math.min(3,p.getInt("accent",0)))];
        INK=dark?0xffedf2f4:0xff17252a; GRAY=dark?0xffb0bec5:0xff65757b;
        BG=dark?0xff12191d:0xfff5f7f8; SURFACE=dark?0xff202a30:Color.WHITE;
        SOFT=blend(SURFACE,GREEN,dark?0.20f:0.12f);
        android.content.res.Configuration config=new android.content.res.Configuration(a.getResources().getConfiguration());
        config.uiMode=(config.uiMode & ~48)|(dark?32:16);
        a.getResources().updateConfiguration(config,a.getResources().getDisplayMetrics());
        a.setTheme(dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        if((a instanceof FocusActivity || a instanceof FreeFocusActivity) && p.getBoolean("keep_awake",false))
            a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else a.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private static int blend(int base,int accent,float fraction){
        return Color.rgb(Math.round(Color.red(base)*(1-fraction)+Color.red(accent)*fraction),Math.round(Color.green(base)*(1-fraction)+Color.green(accent)*fraction),Math.round(Color.blue(base)*(1-fraction)+Color.blue(accent)*fraction));
    }
    private static ColorStateList controlColors(int active,int idle){
        return new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_checked},new int[]{android.R.attr.state_pressed},new int[]{android.R.attr.state_focused},new int[]{}},new int[]{blend(SURFACE,GRAY,.35f),active,active,active,idle});
    }
    /** 系统控件的强调色必须显式跟随应用主题，而非平台默认色。 */
    public static void tintControl(View view){
        if(view instanceof android.widget.Switch){
            android.widget.Switch control=(android.widget.Switch)view;
            control.setThumbTintList(controlColors(GREEN,dark?0xffb0bec5:0xffeeeeee));
            control.setTrackTintList(controlColors(blend(SURFACE,GREEN,.42f),blend(SURFACE,GRAY,.35f)));
        }else if(view instanceof android.widget.CompoundButton){
            ((android.widget.CompoundButton)view).setButtonTintList(controlColors(GREEN,GRAY));
        }else if(view instanceof android.widget.SeekBar){
            android.widget.SeekBar bar=(android.widget.SeekBar)view;
            bar.setThumbTintList(ColorStateList.valueOf(GREEN));bar.setProgressTintList(ColorStateList.valueOf(GREEN));
            bar.setSecondaryProgressTintList(ColorStateList.valueOf(SOFT));bar.setProgressBackgroundTintList(ColorStateList.valueOf(blend(SURFACE,GRAY,.3f)));
        }else if(view instanceof android.widget.EditText){
            android.widget.EditText input=(android.widget.EditText)view;
            input.setTextColor(INK);input.setHintTextColor(GRAY);input.setBackgroundTintList(controlColors(GREEN,GRAY));input.setHighlightColor(SOFT);
            if(Build.VERSION.SDK_INT>=29){
                android.graphics.drawable.Drawable cursor=input.getTextCursorDrawable();if(cursor!=null){cursor=cursor.mutate();cursor.setTint(GREEN);input.setTextCursorDrawable(cursor);}
                android.graphics.drawable.Drawable left=input.getTextSelectHandleLeft(),right=input.getTextSelectHandleRight(),handle=input.getTextSelectHandle();
                if(left!=null){left=left.mutate();left.setTint(GREEN);input.setTextSelectHandleLeft(left);}
                if(right!=null){right=right.mutate();right.setTint(GREEN);input.setTextSelectHandleRight(right);}
                if(handle!=null){handle=handle.mutate();handle.setTint(GREEN);input.setTextSelectHandle(handle);}
            }
        }
        if(view instanceof android.widget.CompoundButton)((android.widget.CompoundButton)view).setTextColor(INK);
    }
    public static String label(Context c,String zh,String en) {
        String language=prefs(c).getString("language","system");
        boolean english=language.equals("en") || (language.equals("system") && java.util.Locale.getDefault().getLanguage().equals("en"));
        return english?en:zh;
    }
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
        b.setTextColor(primary?(dark?BG:Color.WHITE):GREEN);b.setBackgroundTintList(ColorStateList.valueOf(primary?GREEN:SOFT));
        b.setMinWidth(0);b.setMinimumWidth(0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(c,52));lp.topMargin=dp(c,8);b.setLayoutParams(lp);return b;
    }
    public static LinearLayout card(Context c) {
        LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);int pad=dp(c,16);v.setPadding(pad,pad,pad,pad);
        GradientDrawable bg=new GradientDrawable();bg.setColor(SURFACE);bg.setCornerRadius(dp(c,14));bg.setStroke(dp(c,1),Color.rgb(221,229,232));v.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(c,12);v.setLayoutParams(lp);return v;
    }
    public static void space(LinearLayout parent,int height) {
        View v=new View(parent.getContext());parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),height)));
    }
    public static void weighted(LinearLayout row,View view) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(row.getContext(),52),1);lp.setMargins(dp(row.getContext(),2),0,dp(row.getContext(),2),0);row.addView(view,lp);
    }
    public static final class Page { public LinearLayout root, content; public ScrollView scroll; }
    public static boolean handleWheel(Page page,MotionEvent event){return page.scroll instanceof WheelScrollView && ((WheelScrollView)page.scroll).handleWheel(event);}
    public static Page page(Activity a,String title,String subtitle) {
        appearance(a);
        if(a.getActionBar()!=null)a.getActionBar().hide();
        if(Build.VERSION.SDK_INT>=30) a.getWindow().setDecorFitsSystemWindows(false);
        else a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        int flags=a.getWindow().getDecorView().getSystemUiVisibility();
        a.getWindow().getDecorView().setSystemUiVisibility(dark?(flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR):(flags|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        Page p=new Page();p.root=new WheelPageLayout(a);p.root.setOrientation(LinearLayout.VERTICAL);p.root.setBackgroundColor(BG);
        p.root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout heading=new LinearLayout(a);heading.setOrientation(LinearLayout.VERTICAL);heading.setPadding(dp(a,20),dp(a,16),dp(a,20),dp(a,8));
        heading.addView(text(a,title,25,INK,true));if(subtitle!=null&&!subtitle.isEmpty())heading.addView(text(a,subtitle,14,GRAY,false));p.root.addView(heading);
        WheelScrollView scroll=new WheelScrollView(a);scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);scroll.setScrollbarFadingEnabled(false);
        scroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        ((WheelPageLayout)p.root).scroll=scroll; p.scroll=scroll;
        if(a instanceof WheelActivity)((WheelActivity)a).scrollPage=p;
        p.content=new LinearLayout(a);p.content.setOrientation(LinearLayout.VERTICAL);p.content.setPadding(dp(a,20),dp(a,4),dp(a,20),dp(a,24));scroll.addView(p.content);
        p.root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));a.setContentView(p.root);p.root.requestApplyInsets();return p;
    }
    public static void navigation(Activity a,Page page,int selected) {
        LinearLayout row=new LinearLayout(a);row.setPadding(dp(a,8),dp(a,4),dp(a,8),dp(a,4));row.setBackgroundColor(SURFACE);
        String[] labels={label(a,"任务","Tasks"),label(a,"日历","Calendar"),label(a,"统计","Stats"),label(a,"设置","Settings")};Class<?>[] classes={MainActivity.class,TodoActivity.class,StatsActivity.class,SettingsActivity.class};
        for(int i=0;i<labels.length;i++){
            final int index=i;
            LinearLayout item=new LinearLayout(a);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(android.view.Gravity.CENTER);item.setPadding(0,dp(a,5),0,dp(a,5));
            int color=i==selected?GREEN:GRAY;
            item.addView(new NavIcon(a,i,color),new LinearLayout.LayoutParams(dp(a,22),dp(a,22)));
            TextView label=text(a,labels[i],12,color,i==selected);label.setGravity(android.view.Gravity.CENTER);item.addView(label);
            item.setBackgroundColor(i==selected?SOFT:SURFACE);item.setFocusable(true);item.setContentDescription(labels[i]);
            row.addView(item,new LinearLayout.LayoutParams(0,dp(a,58),1));
            item.setOnClickListener(v->{if(index!=selected){a.startActivity(new Intent(a,classes[index]).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));if(!(a instanceof MainActivity))a.finish();}});

        }
        page.root.addView(row);
    }
    private static class NavIcon extends View {
        private final int type,color; private final android.graphics.Paint p=new android.graphics.Paint(3);
        NavIcon(Context c,int type,int color){super(c);this.type=type;this.color=color;}
        @Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            if(type==0){c.drawRoundRect(4,3,21,22,3,3,p);c.drawLine(7,12,10,15,p);c.drawLine(10,15,17,8,p);}
            else if(type==1){c.drawRoundRect(3,5,21,22,2,2,p);c.drawLine(3,10,21,10,p);c.drawLine(8,2,8,7,p);c.drawLine(16,2,16,7,p);c.drawPoint(8,14,p);c.drawPoint(13,14,p);c.drawPoint(8,18,p);}
            else if(type==2){c.drawLine(3,22,22,22,p);c.drawRoundRect(5,13,8,20,1,1,p);c.drawRoundRect(11,5,14,20,1,1,p);c.drawRoundRect(17,9,20,20,1,1,p);}
            else {c.drawCircle(12,12,7,p);c.drawCircle(12,12,3,p);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);c.drawLine(12,2,12,5,p);c.restore();}}
            c.restore();
        }
    }
    public static String duration(int s){return (s/60)+" 分 "+(s%60)+" 秒";}
    /** 页面根节点先接收滚轮，不依赖鼠标恰好停在滚动容器的空白处。 */
    private static final class WheelPageLayout extends LinearLayout {
        WheelScrollView scroll;
        WheelPageLayout(Context c) { super(c); }
        @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
            if (scroll != null && scroll.handleWheel(event)) return true;
            return super.dispatchGenericMotionEvent(event);
        }
    }
    private static final class WheelScrollView extends ScrollView {
        private float remainder;
        WheelScrollView(Context c) { super(c); }
        boolean handleWheel(MotionEvent event) {
            if (event.getActionMasked() != MotionEvent.ACTION_SCROLL) return false;
            float axis = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            // 部分触控板、旋钮设备使用 AXIS_SCROLL，而非鼠标的 VSCROLL。
            if (axis == 0)
                axis = event.getAxisValue(MotionEvent.AXIS_SCROLL);
            if (axis == 0 || Float.isNaN(axis) || Float.isInfinite(axis)) return false;
            float distance = -axis * dp(getContext(), 56);
            if (remainder * distance < 0) remainder = 0;
            remainder += distance;
            int pixels = (int) remainder;
            remainder -= pixels;
            if (pixels != 0) {
                if (canScrollVertically(pixels > 0 ? 1 : -1)) scrollBy(0, pixels);
                else remainder = 0;
            }
            // 已到边界也消费事件，避免子控件重复处理、改变输入内容。
            return true;
        }
        @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
            return handleWheel(event) || super.dispatchGenericMotionEvent(event);
        }
    }
}
