package com.example.learninghelper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.*;

public class SettingsActivity extends WheelActivity {
    private AppUi.Page page;
    private int savedScroll;
    private String websiteDraft;
    @Override public void onCreate(android.os.Bundle state){
        super.onCreate(state);
        if(state!=null){savedScroll=state.getInt("settings_scroll",0);websiteDraft=state.getString("website_draft");}
    }
    @Override protected void onPause(){if(page!=null)savedScroll=page.scroll.getScrollY();super.onPause();}
    @Override protected void onSaveInstanceState(android.os.Bundle out){super.onSaveInstanceState(out);out.putInt("settings_scroll",page==null?0:page.scroll.getScrollY());out.putString("website_draft",websiteDraft);}
    private void openDetail(String detail){startActivity(new Intent(this,SettingsActivity.class).putExtra("settings_detail",detail));}

    private String t(String zh,String en){return AppUi.label(this,zh,en);}
    @Override protected void onResume(){super.onResume();render();}
    private LinearLayout section(String zh,String en){
        LinearLayout card=AppUi.card(this);page.content.addView(card);
        card.addView(AppUi.text(this,t(zh,en),19,AppUi.INK,true));AppUi.space(card,8);return card;
    }
    private void note(LinearLayout card,String zh,String en){card.addView(AppUi.text(this,t(zh,en),13,AppUi.GRAY,false));}
    private void choice(LinearLayout card,String title,String key,String[] values,int defaultValue,boolean refresh){
        card.addView(AppUi.text(this,title,15,AppUi.INK,false));
        Spinner spinner=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);
        int current=Math.max(0,Math.min(values.length-1,AppUi.prefs(this).getInt(key,defaultValue)));
        spinner.setSelection(current);card.addView(spinner,new LinearLayout.LayoutParams(-1,AppUi.dp(this,48)));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int pos, long id){
                if(AppUi.prefs(SettingsActivity.this).getInt(key,defaultValue)==pos)return;
                AppUi.prefs(SettingsActivity.this).edit().putInt(key,pos).apply();if(refresh)render();
            }
        });
    }
    private void toggle(LinearLayout card,String title,String key,boolean fallback){
        Switch control=new Switch(this);AppUi.tintControl(control);control.setText(title);control.setTextColor(AppUi.INK);control.setTextSize(15);
        control.setPadding(0,AppUi.dp(this,12),0,AppUi.dp(this,12));control.setChecked(AppUi.prefs(this).getBoolean(key,fallback));card.addView(control,new LinearLayout.LayoutParams(-1,-2));
        control.setOnCheckedChangeListener((v,on)->{AppUi.prefs(this).edit().putBoolean(key,on).apply();
            if(key.equals("reminders")&&!on)FocusCoordinator.clearNotice(this);
        });
    }
    private LinearLayout row(LinearLayout card,String title,String value,Runnable run){return row(card,title,value,"›",run);}
    private LinearLayout row(LinearLayout card,String title,String value,String indicator,Runnable run){
        android.view.View divider=new android.view.View(this);divider.setBackgroundColor(AppUi.dark?0xff39444b:0xffe6ecee);
        LinearLayout.LayoutParams separator=new LinearLayout.LayoutParams(-1,AppUi.dp(this,1));separator.topMargin=AppUi.dp(this,6);card.addView(divider,separator);
        LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(AppUi.dp(this,58));row.setPadding(0,AppUi.dp(this,12),0,AppUi.dp(this,12));
        TextView label=AppUi.text(this,title,16,AppUi.INK,false);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        if(value!=null&&!value.isEmpty()){
            TextView current=AppUi.text(this,value,14,AppUi.GRAY,false);current.setMaxLines(1);current.setEllipsize(android.text.TextUtils.TruncateAt.END);
            current.setMaxWidth(AppUi.dp(this,128));current.setPadding(AppUi.dp(this,8),0,AppUi.dp(this,8),0);row.addView(current);
        }
        if(!indicator.isEmpty()){TextView arrow=AppUi.text(this,indicator,indicator.equals("›")?26:19,AppUi.GRAY,false);arrow.setGravity(android.view.Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(AppUi.dp(this,24),-2));}
        android.util.TypedValue selectable=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackground,selectable,true);row.setBackgroundResource(selectable.resourceId);
        row.setFocusable(true);row.setContentDescription(title+(value==null?"":"，"+value));row.setOnClickListener(v->run.run());card.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;
    }
    private LinearLayout action(LinearLayout card,String title,Runnable run){return row(card,title,null,"",run);}
    private LinearLayout external(LinearLayout card,String title,Runnable run){return row(card,title,null,"↗",run);}
    private String languageValue(){String code=AppUi.prefs(this).getString("language","system");return code.equals("zh")?"简体中文":code.equals("en")?"English":t("跟随系统","System default");}
    private void render(){
        String detail=getIntent().getStringExtra("settings_detail");
        if(detail!=null){renderDetail(detail);return;}
        page=AppUi.page(this,t("设置","Settings"),t("按你的习惯，安排每一次专注 · 自动保存","Make focus your own · Changes saved automatically"));AppUi.navigation(this,page,3);
        LinearLayout appearance=section("外观","Appearance");
        choice(appearance,t("主题颜色","Accent color"),"accent",new String[]{t("森林绿","Forest"),t("海洋蓝","Ocean"),t("紫罗兰","Violet"),t("暖橙色","Amber")},0,true);
        choice(appearance,t("显示模式","Display mode"),"appearance",new String[]{t("跟随系统","System"),t("浅色","Light"),t("深色","Dark")},0,true);
        LinearLayout general=section("通用","General");
        choice(general,t("默认专注时长","Default focus duration"),"duration_index",new String[]{t("15 分钟","15 minutes"),t("25 分钟","25 minutes"),t("45 分钟","45 minutes"),t("60 分钟","60 minutes"),t("10 秒","10 seconds"),t("30 秒","30 seconds")},1,false);
        toggle(general,t("专注页面保持屏幕常亮","Keep screen awake on focus pages"),"keep_awake",false);
        note(general,"默认时长用于下一次定时专注，不改变正在进行的计时。","The duration applies to the next timed session, not an active timer.");
        LinearLayout audio=section("声音与通知","Sound & notifications");
        choice(audio,t("默认背景声音","Default background sound"),"sound_index",new String[]{t("关闭声音","Off"),t("雨声（合成）","Rain (synthesized)"),t("海浪（合成）","Waves (synthesized)"),t("林间声（合成）","Forest (synthesized)")},0,false);
        TextView volume=AppUi.text(this,"",15,AppUi.INK,false);audio.addView(volume);
        SeekBar slider=new SeekBar(this);AppUi.tintControl(slider);slider.setMax(100);slider.setProgress(AppUi.prefs(this).getInt("audio_volume",55));volume.setText(t("背景音量：","Background volume: ")+slider.getProgress()+"%");audio.addView(slider,new LinearLayout.LayoutParams(-1,AppUi.dp(this,48)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
            public void onProgressChanged(SeekBar s,int value,boolean user){if(user){AppUi.prefs(SettingsActivity.this).edit().putInt("audio_volume",value).apply();volume.setText(t("背景音量：","Background volume: ")+value+"%");}}
        });
        note(audio,"音量在下次播放音频时生效。","Volume applies when audio is next started.");
        toggle(audio,t("允许专注结束提醒","Allow focus reminders"),"reminders",true);
        toggle(audio,t("提醒声音","Reminder sound"),"reminder_sound",true);
        toggle(audio,t("提醒振动","Reminder vibration"),"reminder_vibrate",false);
        note(audio,FocusCoordinator.notificationsAllowed(this)?"系统通知权限：已开启":"系统通知权限：未开启",FocusCoordinator.notificationsAllowed(this)?"System notifications: allowed":"System notifications: not allowed");
        note(audio,"提醒仍受系统权限、通知类别与勿扰模式控制。关闭提醒不影响计时；播放音频时仍需要服务通知。本应用不会管理其他软件的消息。","System permissions, channel settings and Do Not Disturb take precedence. Playback requires a service notification. Other apps' messages are not controlled here.");
        external(audio,t("管理系统通知权限","System notification settings"),()->enableNotifications(this));
        LinearLayout language=section("语言","Language");
        row(language,t("设置与导航语言","Settings & navigation language"),languageValue(),()->openDetail("language"));
        note(language,"当前支持设置页与底部导航的中英切换；其他页面及用户填写的内容保留原文。","Currently applies to Settings and bottom navigation. Other pages and user content keep their original language.");
        LinearLayout about=section("关于软件","About");
        String version="—";try{android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);version=info.versionName+" ("+info.versionCode+")";}catch(Exception ignored){}
        note(about,"专注学习助手 · 版本 "+version,"LearningHelper · Version "+version);
        note(about,"学习任务、待办、专注统计与学习备忘。数据保存在设备本地，无登录或云同步。","Tasks, to-dos, focus statistics and notes. Data is stored locally; no sign-in or cloud sync.");
        String url=AppUi.prefs(this).getString("project_url","");
        row(about,t("项目网址","Project website"),url.isEmpty()?t("未填写","Not set"):android.net.Uri.parse(url).getHost(),()->openDetail("website"));
        row(about,t("使用说明","Usage notes"),null,()->openDetail("help"));
        row(about,t("恢复默认设置","Reset preferences"),null,()->openDetail("reset"));
        page.scroll.post(()->page.scroll.scrollTo(0,savedScroll));
    }
    private void renderDetail(String detail){
        String title=detail.equals("language")?t("设置与导航语言","Settings & navigation language"):detail.equals("website")?t("项目网址","Project website"):detail.equals("reset")?t("恢复默认设置","Reset preferences"):t("使用说明","Usage notes");
        page=AppUi.page(this,title,"");
        // 使用同一 Activity 的独立实例，系统返回手势与顶部返回入口都回到设置首页。
        LinearLayout backBar=new LinearLayout(this);backBar.setPadding(AppUi.dp(this,20),0,AppUi.dp(this,20),0);
        TextView back=AppUi.text(this,t("‹  返回设置","‹  Back to settings"),16,AppUi.GREEN,false);
        back.setGravity(android.view.Gravity.CENTER_VERTICAL);back.setMinHeight(AppUi.dp(this,48));back.setFocusable(true);back.setOnClickListener(v->finish());
        backBar.addView(back,new LinearLayout.LayoutParams(-1,-2));page.root.addView(backBar,0);
        if(detail.equals("language")){
            LinearLayout card=section("选择语言","Choose language");String[] codes={"system","zh","en"};String[] labels={t("跟随系统","System default"),"简体中文","English"};
            RadioGroup group=new RadioGroup(this);card.addView(group);
            String current=AppUi.prefs(this).getString("language","system");
            for(int i=0;i<codes.length;i++){final String code=codes[i];RadioButton radio=new RadioButton(this);AppUi.tintControl(radio);radio.setText(labels[i]);radio.setTextColor(AppUi.INK);radio.setTextSize(16);radio.setId(android.view.View.generateViewId());radio.setMinHeight(AppUi.dp(this,56));group.addView(radio);radio.setChecked(current.equals(code));radio.setOnClickListener(v->{AppUi.prefs(this).edit().putString("language",code).apply();render();});}
            note(card,"当前支持设置页与底部导航的中英切换；其他页面及用户填写的内容保留原文。","Currently applies to Settings and bottom navigation. Other pages and user content keep their original language.");
        }else if(detail.equals("website")){
            LinearLayout card=section("项目网址","Project website");note(card,"填写你的 GitHub 仓库或项目主页，保存后可直接打开。","Enter your GitHub repository or project homepage, then save to open it.");
            EditText input=new EditText(this);AppUi.tintControl(input);input.setSingleLine();input.setTextColor(AppUi.INK);input.setHintTextColor(AppUi.GRAY);input.setHint("https://github.com/...");input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
            input.setText(websiteDraft==null?AppUi.prefs(this).getString("project_url",""):websiteDraft);card.addView(input,new LinearLayout.LayoutParams(-1,AppUi.dp(this,56)));
            input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){websiteDraft=s.toString();}public void afterTextChanged(android.text.Editable e){}});
            action(card,t("保存网址","Save website"),()->{String url=input.getText().toString().trim();android.net.Uri uri=android.net.Uri.parse(url);
                if(!url.isEmpty()&&(!("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))||uri.getHost()==null)){input.setError(t("请输入完整的 http 或 https 网址","Enter a full http or https URL"));return;}
                AppUi.prefs(this).edit().putString("project_url",url).apply();websiteDraft=url;AppUi.toast(this,t("网址已保存","Website saved"));render();});
            String url=AppUi.prefs(this).getString("project_url","");
            if(!url.isEmpty())external(card,t("打开已保存的网址","Open saved website"),()->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(Exception e){AppUi.error(this,e);}});
        }else if(detail.equals("reset")){
            LinearLayout card=section("恢复范围","What will be reset");note(card,"仅恢复外观、时长、声音、通知和语言选项，不删除任务、待办、备忘和学习记录。项目网址保留。","Reset appearance, duration, sound, notifications and language. Tasks, to-dos, notes, records and project website are kept.");
            action(card,t("恢复默认设置…","Reset preferences…"),()->new AlertDialog.Builder(this).setTitle(t("确认恢复默认设置？","Reset preferences?"))
                .setMessage(t("不会删除学习数据。","Your learning data will be kept.")).setNegativeButton(t("取消","Cancel"),null).setPositiveButton(t("恢复","Reset"),(d,w)->{
                    android.content.SharedPreferences.Editor editor=AppUi.prefs(this).edit();for(String key:new String[]{"accent","appearance","duration_index","sound_index","keep_awake","audio_volume","reminders","reminder_sound","reminder_vibrate","language"})editor.remove(key);editor.apply();finish();}).show());
        }else{
            LinearLayout card=section("声音与提醒","Sounds & reminders");note(card,"内置环境声为合成音效。锁屏或省电模式可能延迟提醒；强行停止或重启设备后，请重新打开应用检查记录。","Ambient sounds are synthesized. Power saving may delay reminders. Reopen the app after force-stop or restart to check records.");
            LinearLayout data=section("数据保存","Data storage");note(data,"数据保存在设备本地，无登录或云同步。卸载或清除应用数据会删除本地内容。恢复默认设置不会删除学习数据。","Data is stored locally, without sign-in or cloud sync. Uninstalling or clearing app data deletes local content. Resetting preferences keeps learning data.");
        }
    }
    public static void enableNotifications(Activity a) {
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !AppUi.prefs(a).getBoolean("notification_requested", false)) {
            AppUi.prefs(a).edit().putBoolean("notification_requested", true).apply();
            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        } else {
            a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
        }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results); if (code == 101) render();
    }
}

