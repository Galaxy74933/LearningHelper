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
    private PopupWindow preferencePopup;
    @Override public void onCreate(android.os.Bundle state){
        super.onCreate(state);
        if(state!=null){savedScroll=state.getInt("settings_scroll",0);websiteDraft=state.getString("website_draft");}
    }
    @Override protected void onPause(){if(page!=null)savedScroll=page.scroll.getScrollY();if(preferencePopup!=null)preferencePopup.dismiss();super.onPause();}
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
        if(card.getChildCount()>2){android.view.View divider=new android.view.View(this);divider.setBackgroundColor(AppUi.dark?0xff39444b:0xffe6ecee);
        LinearLayout.LayoutParams separator=new LinearLayout.LayoutParams(-1,AppUi.dp(this,1));separator.topMargin=AppUi.dp(this,6);card.addView(divider,separator);}
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
    private String versionLabel(){String version="—";try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}return t("版本：v","Version: v")+version;}
    private void preferenceRow(LinearLayout card,String title,String key,String[] options,int fallback){
        int selected=Math.max(0,Math.min(options.length-1,AppUi.prefs(this).getInt(key,fallback)));
        LinearLayout line=row(card,title,options[selected],"⌃\n⌄",()->{
            LinearLayout clicked=card.findViewWithTag(key);
            showPreferencePopup(clicked.getChildAt(clicked.getChildCount()-1),key,options,selected);
        });
        line.setTag(key);
        for(int i=1;i<line.getChildCount();i++)if(line.getChildAt(i) instanceof TextView){TextView text=(TextView)line.getChildAt(i);text.setTextColor(AppUi.GREEN);if(i==line.getChildCount()-1){text.setTextSize(12);text.setLineSpacing(-AppUi.dp(this,4),1);}}
    }
    private void showPreferencePopup(android.view.View anchor,String key,String[] options,int selected){
        if(preferencePopup!=null)preferencePopup.dismiss();
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(list);
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
        background.setColor(AppUi.SURFACE);background.setCornerRadius(AppUi.dp(this,18));
        scroll.setBackground(background);scroll.setClipToOutline(true);
        int width=Math.min(AppUi.dp(this,244),getResources().getDisplayMetrics().widthPixels-AppUi.dp(this,32));
        PopupWindow popup=new PopupWindow(scroll,width,-2,true);preferencePopup=popup;
        popup.setBackgroundDrawable(background.getConstantState().newDrawable());popup.setElevation(AppUi.dp(this,6));popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        for(int i=0;i<options.length;i++){
            final int value=i;
            if(i>0){android.view.View separator=new android.view.View(this);separator.setBackgroundColor(AppUi.dark?0xff39444b:0xffe6ecee);list.addView(separator,new LinearLayout.LayoutParams(-1,AppUi.dp(this,1)));}
            LinearLayout option=new LinearLayout(this);option.setGravity(android.view.Gravity.CENTER_VERTICAL);option.setPadding(AppUi.dp(this,12),AppUi.dp(this,12),AppUi.dp(this,16),AppUi.dp(this,12));option.setMinimumHeight(AppUi.dp(this,52));
            TextView check=AppUi.text(this,i==selected?"✓":"",18,AppUi.GREEN,true);check.setGravity(android.view.Gravity.CENTER);option.addView(check,new LinearLayout.LayoutParams(AppUi.dp(this,28),-2));
            TextView label=AppUi.text(this,options[i],16,i==selected?AppUi.GREEN:AppUi.INK,i==selected);option.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            android.graphics.drawable.ColorDrawable fill=new android.graphics.drawable.ColorDrawable(i==selected?AppUi.SOFT:AppUi.SURFACE);
            option.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((AppUi.GREEN&0xffffff)|0x22000000),fill,null));
            option.setFocusable(true);option.setContentDescription(options[i]+(i==selected?t("，已选中",", selected"):""));option.setSelected(i==selected);
            option.setOnClickListener(v->{savedScroll=page.scroll.getScrollY();AppUi.prefs(this).edit().putInt(key,value).apply();popup.dismiss();render();});list.addView(option,new LinearLayout.LayoutParams(-1,-2));
        }
        scroll.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED));
        int offset=AppUi.dp(this,6);popup.setHeight(Math.min(scroll.getMeasuredHeight(),popup.getMaxAvailableHeight(anchor,offset)));
        popup.showAsDropDown(anchor,0,offset,android.view.Gravity.END);
    }
    private void render(){
        String detail=getIntent().getStringExtra("settings_detail");
        if(detail!=null){renderDetail(detail);return;}
        page=AppUi.page(this,t("设置","Settings"),t("按你的习惯，安排每一次专注 · 自动保存","Make focus your own · Changes saved automatically"));AppUi.navigation(this,page,3);
        profileHeader();
        LinearLayout appearance=section("外观","Appearance");
        LinearLayout display=new LinearLayout(this);display.setGravity(android.view.Gravity.CENTER_VERTICAL);display.addView(AppUi.text(this,t("显示","Display"),16,AppUi.INK,false),new LinearLayout.LayoutParams(AppUi.dp(this,58),-2));
        int mode=AppUi.prefs(this).getInt("appearance",0);int[] modes={1,2,0};
        FocusOptions switcher=new FocusOptions(this,new String[]{t("浅色","Light"),t("深色","Dark"),t("跟随系统","System")},mode==1?0:mode==2?1:2,index->{AppUi.prefs(this).edit().putInt("appearance",modes[index]).apply();savedScroll=page.scroll.getScrollY();page.root.postDelayed(()->render(),240);});
        display.addView(new android.view.View(this),new LinearLayout.LayoutParams(0,1,1));
        display.addView(switcher,new LinearLayout.LayoutParams(AppUi.dp(this,204),AppUi.dp(this,46)));appearance.addView(display);
        display.post(()->{int width=Math.min(AppUi.dp(this,204),Math.max(1,display.getWidth()-AppUi.dp(this,58)));if(switcher.getLayoutParams().width!=width){switcher.getLayoutParams().width=width;switcher.requestLayout();}});
        preferenceRow(appearance,t("分类和标签的字体色","Category & tag color"),"tag_color",new String[]{t("自适应颜色","Adaptive"),t("主题色","Accent"),t("正文色","Text")},0);
        preferenceRow(appearance,t("主题色","Accent color"),"accent",new String[]{t("森林绿","Forest"),t("海洋蓝","Ocean"),t("紫罗兰","Violet"),t("暖橙色","Amber")},0);
        preferenceRow(appearance,t("时长显示格式","Duration format"),"duration_format",new String[]{t("时·分·秒","Hours · minutes · seconds"),t("天·时·分·秒","Days · hours · minutes · seconds"),t("分·秒","Minutes · seconds"),"HH:mm:ss"},0);
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
        LinearLayout about=AppUi.card(this);page.content.addView(about);
        LinearLayout aboutRow=row(about,t("关于","About"),versionLabel(),()->openDetail("about"));
        TextView aboutTitle=(TextView)aboutRow.getChildAt(0);
        aboutTitle.setTextSize(19);
        aboutTitle.setTypeface(null,android.graphics.Typeface.BOLD);
        page.scroll.post(()->page.scroll.scrollTo(0,savedScroll));
    }
    private void renderDetail(String detail){
        String title=detail.equals("about")?t("关于","About"):detail.equals("agreement")?t("用户协议","User agreement"):detail.equals("privacy")?t("隐私政策","Privacy policy"):detail.equals("profile")?t("个人信息","Personal information"):detail.equals("language")?t("设置与导航语言","Settings & navigation language"):detail.equals("website")?t("项目网址","Project website"):detail.equals("reset")?t("恢复默认设置","Reset preferences"):t("使用说明","Usage notes");
        page=AppUi.page(this,title,"");
        // 使用同一 Activity 的独立实例，系统返回手势与顶部返回入口都回到设置首页。
        LinearLayout backBar=new LinearLayout(this);backBar.setPadding(AppUi.dp(this,20),0,AppUi.dp(this,20),0);
        TextView back=AppUi.text(this,t("‹  返回","‹  Back"),16,AppUi.GREEN,false);
        back.setGravity(android.view.Gravity.CENTER_VERTICAL);back.setMinHeight(AppUi.dp(this,48));back.setFocusable(true);back.setOnClickListener(v->finish());
        backBar.addView(back,new LinearLayout.LayoutParams(-1,-2));page.root.addView(backBar,0);
        if(detail.equals("about")){
            TextView version=AppUi.text(this,versionLabel(),18,AppUi.GRAY,false);version.setGravity(android.view.Gravity.CENTER);version.setPadding(0,AppUi.dp(this,28),0,AppUi.dp(this,28));page.content.addView(version);
            LinearLayout links=AppUi.card(this);page.content.addView(links);
            row(links,t("用户协议","User agreement"),null,()->openDetail("agreement"));
            row(links,t("操作说明","Instructions"),null,()->openDetail("help"));
            row(links,t("隐私政策","Privacy policy"),null,()->openDetail("privacy"));
            row(links,t("项目网址","Project website"),null,()->openDetail("website"));
            row(links,t("恢复默认设置","Reset preferences"),null,()->openDetail("reset"));
        }else if(detail.equals("agreement")){
            LinearLayout card=section("用户协议","User agreement");
            note(card,"本应用用于个人学习计划、专注计时与记录。请仅导入你有权使用的音乐和图片，并对自己填写的内容负责。\n\n计时与提醒受设备状态、系统权限和省电设置影响，请勿将其用于必须准时的关键提醒。你可以随时停止使用，并通过系统设置清除应用数据。卸载或清除数据前请确认不再需要本地记录。","This app supports personal study planning, timers and records. Only import audio and images you have permission to use. Timers and reminders depend on device and system settings. You can stop using the app and clear its data in system settings.");
        }else if(detail.equals("privacy")){
            LinearLayout card=section("隐私政策","Privacy policy");
            note(card,"任务、待办、专注记录、设置和个人资料保存在设备本地。手机号、邮箱和微信为自愿填写的本地资料，不用于登录验证。应用没有账号服务器，不主动上传这些内容。\n\n头像和音乐仅在你主动选择后访问；通知权限用于专注提醒和播放状态。系统备份或设备迁移可能按照你的系统设置备份应用数据。\n\n打开项目网址时，将由外部浏览器访问对应网站，适用该网站的隐私政策。你可以编辑或删除个人资料、待办和学习记录；清除应用数据会移除全部本地内容。","Study data, preferences and optional profile details are stored locally. The app has no account server and does not actively upload this data. Selected images and audio are accessed with your permission. Notifications show focus and playback status. OS backup or device transfer may copy app data according to your system settings. External websites have their own privacy policies. Clearing app data removes local content.");
        }else if(detail.equals("profile")){
            profileDetails();
        }else if(detail.equals("language")){
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
                    android.content.SharedPreferences.Editor editor=AppUi.prefs(this).edit();for(String key:new String[]{"accent","appearance","tag_color","duration_format","duration_index","sound_index","keep_awake","audio_volume","reminders","reminder_sound","reminder_vibrate","language"})editor.remove(key);editor.apply();finish();}).show());
        }else{
            LinearLayout guide=section("操作说明","Instructions");note(guide,"专注：切换倒计时或正计时，点击圆圈内数字调整倒计时时长，选择声音后开始。结束时可以保存记录，也可以不保存。\n\n日历：添加待办、设置日期和分类；点日期展开当天事项，再点同一天收回。左上角切换清单、四象限或分类视图，右上角按时间筛选。\n\n统计：查看完成情况和专注时长，点击概览卡片查看明细。外观设置中的时长格式用于记录和统计，计时圆圈保留易读的数字格式。","Focus: choose countdown or count-up, set duration and sound, then start. Calendar: add and organize to-dos, switch views and filter dates. Stats: tap overview cards for details. Duration format affects records and statistics; the timer keeps its numeric display.");
            LinearLayout card=section("声音与提醒","Sounds & reminders");note(card,"内置环境声为合成音效。锁屏或省电模式可能延迟提醒；强行停止或重启设备后，请重新打开应用检查记录。","Ambient sounds are synthesized. Power saving may delay reminders. Reopen the app after force-stop or restart to check records.");
            LinearLayout data=section("数据保存","Data storage");note(data,"数据保存在设备本地，无登录或云同步。卸载或清除应用数据会删除本地内容。恢复默认设置不会删除学习数据。","Data is stored locally, without sign-in or cloud sync. Uninstalling or clearing app data deletes local content. Resetting preferences keeps learning data.");
        }
    }
    private static final int PICK_AVATAR=302;
    private android.content.SharedPreferences profile(){return getSharedPreferences("personal_profile",MODE_PRIVATE);}
    private String nickname(){String name=profile().getString("nickname","");return name.isEmpty()?t("学习者","Learner"):name;}
    private java.io.File avatarFile(){return new java.io.File(getFilesDir(),"profile-avatar.png");}
    private android.view.View avatar(int size){
        FrameLayout frame=new FrameLayout(this);
        android.graphics.drawable.GradientDrawable circle=new android.graphics.drawable.GradientDrawable();circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);circle.setColor(AppUi.SOFT);
        frame.setBackground(circle);frame.setClipToOutline(true);
        TextView fallback=AppUi.text(this,nickname().substring(0,nickname().offsetByCodePoints(0,1)),size/3,AppUi.GREEN,true);fallback.setGravity(android.view.Gravity.CENTER);frame.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
        StudyRepository.run(this,()->android.graphics.BitmapFactory.decodeFile(avatarFile().getPath()),bitmap->{if(bitmap!=null)image.setImageBitmap(bitmap);},e->{});
        frame.setContentDescription(t("个人头像","Profile picture"));return frame;
    }
    private void profileHeader(){
        LinearLayout header=AppUi.card(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
        TextView name=AppUi.text(this,nickname(),23,AppUi.INK,true);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(name);
        AppUi.space(info,6);info.addView(AppUi.text(this,t("查看和编辑个人信息","View and edit profile"),13,AppUi.GRAY,false));header.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams photo=new LinearLayout.LayoutParams(AppUi.dp(this,60),AppUi.dp(this,60));photo.leftMargin=AppUi.dp(this,12);header.addView(avatar(60),photo);
        header.setFocusable(true);header.setContentDescription(t("编辑个人信息","Edit profile"));header.setOnClickListener(v->openDetail("profile"));page.content.addView(header);
    }
    private void profileDetails(){
        LinearLayout photo=new LinearLayout(this);photo.setOrientation(LinearLayout.VERTICAL);photo.setGravity(android.view.Gravity.CENTER);photo.setPadding(0,AppUi.dp(this,18),0,AppUi.dp(this,20));
        photo.addView(avatar(94),new LinearLayout.LayoutParams(AppUi.dp(this,94),AppUi.dp(this,94)));AppUi.space(photo,12);
        photo.addView(AppUi.text(this,t("修改头像","Change picture"),15,AppUi.GREEN,false));photo.setFocusable(true);photo.setContentDescription(t("修改头像","Change picture"));
        photo.setOnClickListener(v->{try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),PICK_AVATAR);}catch(Exception e){AppUi.error(this,e);}});page.content.addView(photo);
        LinearLayout basic=section("基本信息","Basic information");
        profileField(basic,"昵称","Nickname","nickname",android.text.InputType.TYPE_CLASS_TEXT,30);
        LinearLayout contact=section("联系方式","Contact information");
        profileField(contact,"手机号","Phone","phone",android.text.InputType.TYPE_CLASS_PHONE,30);
        profileField(contact,"邮箱","Email","email",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,254);
        profileField(contact,"微信","WeChat","wechat",android.text.InputType.TYPE_CLASS_TEXT,80);
        AppUi.space(page.content,16);note(page.content,"个人信息仅保存在本机；手机号、邮箱和微信为资料填写，不进行账号绑定或验证。","Your profile stays on this device. Contact details are not linked or verified.");
    }
    private void profileField(LinearLayout card,String zh,String en,String key,int inputType,int limit){
        String value=profile().getString(key,"");
        row(card,t(zh,en),value.isEmpty()?t("未填写","Not set"):value,()->{
            EditText input=new EditText(this);AppUi.tintControl(input);input.setInputType(inputType);input.setSingleLine();input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(limit)});input.setText(profile().getString(key,""));input.setSelection(input.length());
            LinearLayout form=new LinearLayout(this);form.setPadding(AppUi.dp(this,24),AppUi.dp(this,8),AppUi.dp(this,24),0);form.addView(input,new LinearLayout.LayoutParams(-1,-2));
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle(t("修改","Edit ")+t(zh,en)).setView(form).setNegativeButton(t("取消","Cancel"),null).setPositiveButton(t("保存","Save"),null).create();
            dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String text=input.getText().toString().trim();
                if(key.equals("nickname")&&text.isEmpty()){input.setError(t("请输入昵称","Enter a nickname"));return;}
                if(key.equals("email")&&!text.isEmpty()&&!android.util.Patterns.EMAIL_ADDRESS.matcher(text).matches()){input.setError(t("请输入有效邮箱","Enter a valid email"));return;}
                if(key.equals("phone")&&!text.isEmpty()&&!text.matches("[+0-9() -]{5,30}")){input.setError(t("请输入有效手机号","Enter a valid phone number"));return;}
                profile().edit().putString(key,text).apply();dialog.dismiss();render();
            }));dialog.show();
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_AVATAR||result!=RESULT_OK||data==null||data.getData()==null)return;
        android.net.Uri uri=data.getData();
        StudyRepository.run(this,()->{
            android.graphics.Bitmap bitmap;
            if(Build.VERSION.SDK_INT>=28){
                bitmap=android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(getContentResolver(),uri),(decoder,info,source)->{
                    int w=info.getSize().getWidth(),h=info.getSize().getHeight();float scale=Math.min(1f,512f/Math.max(w,h));decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            }else{
                android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;
                try(java.io.InputStream in=getContentResolver().openInputStream(uri)){android.graphics.BitmapFactory.decodeStream(in,null,options);}
                options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>512)options.inSampleSize*=2;options.inJustDecodeBounds=false;
                try(java.io.InputStream in=getContentResolver().openInputStream(uri)){bitmap=android.graphics.BitmapFactory.decodeStream(in,null,options);}
            }
            if(bitmap==null)throw new IllegalArgumentException("图片无法读取，请重新选择");
            android.util.AtomicFile file=new android.util.AtomicFile(avatarFile());java.io.FileOutputStream stream=null;
            try{stream=file.startWrite();if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream))throw new java.io.IOException("头像保存失败");file.finishWrite(stream);}
            catch(Exception e){if(stream!=null)file.failWrite(stream);throw e;}finally{bitmap.recycle();}
            return true;
        },ok->{render();AppUi.toast(this,t("头像已更新","Picture updated"));},e->AppUi.error(this,e));
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

