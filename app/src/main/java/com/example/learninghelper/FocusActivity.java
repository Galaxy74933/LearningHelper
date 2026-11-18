package com.example.learninghelper;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.Gravity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.*;
import java.util.*;

public class FocusActivity extends WheelActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppUi.Page page;
    private boolean freeMode;
    private int chosenSeconds=1500;
    private String selectedTodoId="", selectedTodoDate="";
    private String taskTitle="不关联任务";
    private List<StudyRepository.Task> tasks=new ArrayList<>();
    private FocusOptions soundOptions;
    private FocusAtmosphere atmosphere;
    private static final int PICK_AUDIO=44, PICK_BACKGROUND=45;

    private StudyRepository.Session session;
    private TextView countdown, soundState, hint;
    private Button endButton;
    private long requestedSession = -1, taskId = -1;
    private int loadVersion, durationIndex = 1, selectedSound;
    private boolean visible, busy, failedSave, resultShown;
    private long lastCheckpoint;
    private AlertDialog resultDialog;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        readIntent();
        FocusCoordinator.channels(this);
        freeMode=getIntent().getBooleanExtra("free_mode",false);
        durationIndex = AppUi.prefs(this).getInt("duration_index", 1);
        selectedSound = AppUi.prefs(this).getInt("sound_index", 0);
        chosenSeconds=AppUi.DURATIONS[Math.max(0,Math.min(AppUi.DURATIONS.length-1,durationIndex))];
        if (state != null) {
            selectedTodoDate=state.getString("selected_todo_date","");
            selectedTodoId=state.getString("selected_todo","");
            requestedSession = state.getLong("session_id", requestedSession);
            durationIndex = state.getInt("duration_index", durationIndex);
            selectedSound = state.getInt("sound_index", selectedSound);
            freeMode=state.getBoolean("free_mode",freeMode);chosenSeconds=state.getInt("seconds",chosenSeconds);taskId=state.getLong("selected_task",taskId);
        }
    }
    private void readIntent() {
        requestedSession = getIntent().getLongExtra("session_id", -1);
        taskId = getIntent().getLongExtra("task_id", -1);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); readIntent();
        session = null; resultShown = false; failedSave = false;
        if (resultDialog != null) resultDialog.dismiss();
        if (visible) load();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong("session_id", session == null ? requestedSession : session.id);
        out.putString("selected_todo_date",selectedTodoDate);out.putString("selected_todo",selectedTodoId);out.putBoolean("free_mode",freeMode);out.putInt("seconds",chosenSeconds);out.putLong("selected_task",taskId);
        out.putInt("duration_index", durationIndex); out.putInt("sound_index", selectedSound);
    }
    @Override protected void onResume() { super.onResume(); visible = true; load(); }
    @Override protected void onPause() { visible = false; if(atmosphere!=null)atmosphere.stop(); handler.removeCallbacks(tick); super.onPause(); }
    @Override protected void onStop() { checkpoint(); super.onStop(); }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (resultDialog != null) resultDialog.dismiss();
        super.onDestroy();
    }
    private static class ScreenData {
        StudyRepository.Session session;
        StudyRepository.Task task;
        List<StudyRepository.Task> tasks;
        org.json.JSONObject todo;
        List<org.json.JSONObject> todos;
    }
    private void load() {
        if (busy) return;
        int version = ++loadVersion;
        handler.removeCallbacks(tick);
        if(atmosphere!=null)atmosphere.stop();
        page = AppUi.page(this, L10n.t(FocusActivity.this,"专注学习"), L10n.t(FocusActivity.this,"专注一件事，按自己的节奏完成"));
        atmosphere=new FocusAtmosphere(this);page.root.setBackground(atmosphere);
        AppUi.navigation(this,page,0);
        page.content.addView(AppUi.text(this, L10n.t(FocusActivity.this,"正在读取专注状态…"), 16, AppUi.GRAY, false));
        StudyRepository.run(this, () -> {
            ScreenData d = new ScreenData();
            // 无论从哪个任务进入，都优先返回唯一的进行中记录。
            d.session = StudyRepository.running(this);
            if (d.session == null && requestedSession >= 0) d.session = StudyRepository.session(this, requestedSession);
            d.tasks=StudyRepository.tasks(this);
            if(!selectedTodoId.isEmpty())d.todo=StudyRepository.todo(this,selectedTodoId);
            if (d.session == null) for (StudyRepository.Task t : d.tasks) if (t.id == taskId && !t.done) d.task = t;
            return d;
        }, d -> {
            if (version != loadVersion) return;
            session = d.session; tasks=d.tasks;
            if (session != null) {
                requestedSession = session.id;
                if (session.status == TaskProvider.RUNNING && session.owned()) FocusCoordinator.schedule(this, session);
                renderSession();
            }
            else {if(d.todo!=null&&!d.todo.optBoolean("done")){taskId=0;taskTitle=d.todo.optString("title");}else {selectedTodoId="";selectedTodoDate="";taskId=0;taskTitle=L10n.t(FocusActivity.this,"不关联任务");}renderSetup(d.task);}
        }, e -> {
            if (version != loadVersion) return;
            page.content.removeAllViews(); Button retry = AppUi.button(this, L10n.t(FocusActivity.this,"读取失败，点击重试"), true);
            page.content.addView(retry); retry.setOnClickListener(v -> load()); homeButton(); AppUi.error(this, e);
        });
    }
    private String audioDisplayName(){String value=AppUi.prefs(this).getString("focus_audio_name","");return value.isEmpty()?L10n.t(this,"自定义音乐"):value.equals("设备音频")?L10n.t(this,value):value;}
    private Spinner spinner(String[] values, int selection) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(adapter);
        s.setSelection(Math.max(0, Math.min(selection, values.length - 1)));
        page.content.addView(s, new LinearLayout.LayoutParams(-1, AppUi.dp(this, 52))); return s;
    }
    private void renderSetup(StudyRepository.Task task) { renderFocus(false); }
    private void renderFocus(boolean running){
        handler.removeCallbacks(tick);
        page.content.removeAllViews();
        updateAtmosphere();
        FocusOptions modes=new FocusOptions(this,new String[]{L10n.t(FocusActivity.this,"倒计时"),L10n.t(FocusActivity.this,"正计时")},freeMode?1:0,index->{freeMode=index==1;countdown.setText(time(freeMode?0:chosenSeconds));countdown.setContentDescription(freeMode?L10n.t(FocusActivity.this,"已专注时长"):L10n.t(FocusActivity.this,"选择专注时长"));View ring=page.content.findViewWithTag("focus_ring");if(ring instanceof FrameLayout)((FrameLayout)ring).getChildAt(0).invalidate();TextView label=page.content.findViewWithTag("timer_label");if(label!=null)label.setText(freeMode?L10n.t(FocusActivity.this,"记录每一刻专注"):L10n.t(FocusActivity.this,"点击数字调整时长"));hint.setText(freeMode?"":L10n.t(FocusActivity.this,"准备好后，开始这一次专注。"));});
        modes.setEnabled(!running&&!busy);page.content.addView(modes,new LinearLayout.LayoutParams(-1,AppUi.dp(this,48)));
        AppUi.space(page.content,12);
        TextView task=AppUi.text(this,(running?L10n.sessionTitle(this,session):(taskId>0||!selectedTodoId.isEmpty()?taskTitle:L10n.t(this,"不关联任务")))+"  ›",20,AppUi.INK,true);task.setGravity(Gravity.CENTER);task.setMinHeight(AppUi.dp(this,48));task.setMaxLines(2);task.setFocusable(true);task.setContentDescription(L10n.t(FocusActivity.this,"选择任务"));task.setOnClickListener(v->{if(!running&&!busy)chooseTask();else AppUi.toast(this,L10n.t(FocusActivity.this,"本次任务已锁定，结束后可重新选择"));});page.content.addView(task,new LinearLayout.LayoutParams(-1,-2));
        String linkedDate=running?session.todoDate:selectedTodoDate;
        if(!(running?session.todoId:selectedTodoId).isEmpty()&&!linkedDate.isEmpty()){
            TextView date=AppUi.text(this,AppUi.label(this,"关联日期：","Task date: ")+linkedDate+(running?"":"  ›"),13,AppUi.GRAY,false);date.setGravity(Gravity.CENTER);date.setMinHeight(AppUi.dp(this,36));
            date.setOnClickListener(v->{if(!running&&!busy){org.json.JSONObject item=StudyRepository.todo(this,selectedTodoId);if(item!=null)chooseTodoDate(item);}});page.content.addView(date);
        }
        AppUi.space(page.content,8);
        soundOptions=new FocusOptions(this,new String[]{L10n.t(FocusActivity.this,"雨声"),L10n.t(FocusActivity.this,"海浪"),L10n.t(FocusActivity.this,"林间"),L10n.t(FocusActivity.this,"自定义"),L10n.t(FocusActivity.this,"关闭")},soundPosition(),index->{
            if(index==3){chooseMusic();return;}selectedSound=index==4?0:index+1;updateAtmosphere();if(running)play(selectedSound);
        });page.content.addView(soundOptions,new LinearLayout.LayoutParams(-1,AppUi.dp(this,46)));
        soundState=AppUi.text(this,running?NoiseService.status(this,session.id):selectedSound==4?L10n.t(FocusActivity.this,"已选择：")+audioDisplayName():"",12,AppUi.GRAY,false);soundState.setGravity(Gravity.CENTER);page.content.addView(soundState,new LinearLayout.LayoutParams(-1,AppUi.dp(this,20)));soundState.setVisibility(soundState.getText().length()==0?View.GONE:View.VISIBLE);
        TextView background=AppUi.text(this,AppUi.label(this,"背景 · 更换","Background · Change"),13,AppUi.GREEN,false);background.setGravity(Gravity.CENTER);background.setMinHeight(AppUi.dp(this,40));background.setOnClickListener(v->chooseBackground());page.content.addView(background);
        FrameLayout ring=new FrameLayout(this);ring.setTag("focus_ring");ring.addView(new FocusRingView(this),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout center=new LinearLayout(this);center.setGravity(Gravity.CENTER);center.setOrientation(LinearLayout.VERTICAL);
        countdown=AppUi.text(this,time(running?(freeMode?session.elapsedMillis()/1000:(session.remaining()+999)/1000):(freeMode?0:chosenSeconds)),46,AppUi.INK,true);countdown.setGravity(Gravity.CENTER);countdown.setMaxLines(1);countdown.setAutoSizeTextTypeUniformWithConfiguration(24,46,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        countdown.setContentDescription(freeMode?L10n.t(FocusActivity.this,"已专注时长"):L10n.t(FocusActivity.this,"选择专注时长"));countdown.setFocusable(!freeMode&&!running);countdown.setOnClickListener(v->{if(!freeMode&&!running&&!busy)chooseDuration();});
        center.addView(countdown,new LinearLayout.LayoutParams(-1,AppUi.dp(this,66)));TextView label=AppUi.text(this,freeMode?L10n.t(FocusActivity.this,"记录每一刻专注"):running?L10n.t(FocusActivity.this,"正在专注"):L10n.t(FocusActivity.this,"点击数字调整时长"),14,AppUi.GRAY,false);label.setTag("timer_label");label.setGravity(Gravity.CENTER);center.addView(label);ring.addView(center,new FrameLayout.LayoutParams(-1,-1));
        int width=getResources().getDisplayMetrics().widthPixels-AppUi.dp(this,48);int size=Math.min(AppUi.dp(this,304),width);
        LinearLayout.LayoutParams circle=new LinearLayout.LayoutParams(size,size);circle.gravity=Gravity.CENTER_HORIZONTAL;circle.topMargin=AppUi.dp(this,2);circle.bottomMargin=AppUi.dp(this,12);page.content.addView(ring,circle);
        hint=AppUi.text(this,running?L10n.t(FocusActivity.this,"计时中可切换音乐；返回后继续计时。"):freeMode?"":L10n.t(FocusActivity.this,"准备好后，开始这一次专注。"),13,AppUi.GRAY,false);hint.setGravity(Gravity.CENTER);page.content.addView(hint);
        endButton=AppUi.button(this,running?(failedSave?L10n.t(FocusActivity.this,"重试保存记录"):freeMode?L10n.t(FocusActivity.this,"结束并保存"):L10n.t(FocusActivity.this,"提前结束")):L10n.t(FocusActivity.this,"开始专注"),true);
        android.graphics.drawable.GradientDrawable buttonBg=new android.graphics.drawable.GradientDrawable();buttonBg.setColor(AppUi.SOFT);buttonBg.setCornerRadius(AppUi.dp(this,28));endButton.setBackgroundTintList(null);endButton.setBackground(buttonBg);endButton.setTextColor(AppUi.GREEN);
        LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(AppUi.dp(this,190),AppUi.dp(this,54));action.gravity=Gravity.CENTER_HORIZONTAL;action.topMargin=AppUi.dp(this,10);page.content.addView(endButton,action);endButton.setEnabled(!busy);
        if(running){
            Button pause=roundedAction(AppUi.label(this,session.paused()?"▶ 继续":"Ⅱ 暂停",session.paused()?"▶ Resume":"Ⅱ Pause"),false);
            LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);
            page.content.removeView(endButton);
            LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,AppUi.dp(this,52),1);left.rightMargin=AppUi.dp(this,8);
            actions.addView(pause,left);actions.addView(endButton,new LinearLayout.LayoutParams(0,AppUi.dp(this,52),1));
            LinearLayout.LayoutParams group=new LinearLayout.LayoutParams(-1,-2);group.topMargin=AppUi.dp(this,18);page.content.addView(actions,group);
            pause.setEnabled(!busy);pause.setOnClickListener(v->togglePause());
            if(session.paused())label.setText(AppUi.label(this,"已暂停","Paused"));
        }
        endButton.setOnClickListener(v->{if(busy)return;if(!running){startFocus();return;}if(!freeMode&&session.remaining()==0){finishSession(TaskProvider.COMPLETED);return;}confirmEnd();});
        AppUi.space(page.content,12);
        if(running&&visible)handler.post(tick);
    }
    private void chooseBackground(){
        AppUi.dialog(this).setTitle(AppUi.label(this,"专注背景","Focus background"))
            .setItems(new String[]{AppUi.label(this,"跟随声音 · 自然场景","Match sound · Nature"),AppUi.label(this,"从相册选择照片","Choose a photo"),AppUi.label(this,"关闭背景","No background")},(d,index)->{
                if(index==1){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),PICK_BACKGROUND);}catch(Exception e){AppUi.error(this,e);}return;}
                AppUi.prefs(this).edit().putString("scene_mode",index==0?"auto":"off").apply();updateAtmosphere();
            }).setNegativeButton(AppUi.label(this,"取消","Cancel"),null).show();
    }
    private void updateAtmosphere(){if(atmosphere!=null){atmosphere.setSound(selectedSound);if(visible)atmosphere.start();}}
    private String time(long seconds){return seconds>=3600?String.format(Locale.US,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60);}
    private int soundPosition(){return selectedSound==0?4:selectedSound==4?3:selectedSound-1;}
    private Dialog optionSheet(String title,LinearLayout content){
        Dialog dialog=new Dialog(this);LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(AppUi.dp(this,20),AppUi.dp(this,16),AppUi.dp(this,20),AppUi.dp(this,16));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(AppUi.SURFACE);bg.setCornerRadius(AppUi.dp(this,24));panel.setBackground(bg);
        TextView heading=AppUi.text(this,title,22,AppUi.INK,true);heading.setGravity(Gravity.CENTER);panel.addView(heading);AppUi.space(panel,16);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(content);panel.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        Button close=roundedAction(L10n.t(FocusActivity.this,"取消"),false);close.setOnClickListener(v->dialog.dismiss());panel.addView(close);
        dialog.setContentView(panel);dialog.show();android.view.Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);}
        panel.post(()->{int max=getResources().getDisplayMetrics().heightPixels/2;if(scroll.getHeight()>max){scroll.getLayoutParams().height=max;scroll.requestLayout();}});
        return dialog;
    }
    private void sheetOption(LinearLayout list,String title,String subtitle,boolean selected,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(AppUi.dp(this,16),AppUi.dp(this,14),AppUi.dp(this,16),AppUi.dp(this,14));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(selected?AppUi.SOFT:AppUi.BG);bg.setCornerRadius(AppUi.dp(this,14));
        row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((AppUi.GREEN&0xffffff)|0x22000000),bg,null));
        row.addView(AppUi.text(this,(selected?"✓  ":"")+title,17,selected?AppUi.GREEN:AppUi.INK,true));if(!subtitle.isEmpty())row.addView(AppUi.text(this,subtitle,13,AppUi.GRAY,false));
        row.setFocusable(true);row.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=AppUi.dp(this,8);list.addView(row,lp);
    }
    private void chooseTask(){
        StudyRepository.run(this,()->{ScreenData d=new ScreenData();d.todos=StudyRepository.todos(this);return d;},data->{
            LinearLayout options=new LinearLayout(this);options.setOrientation(LinearLayout.VERTICAL);final Dialog[] sheet=new Dialog[1];
            sheetOption(options,L10n.t(FocusActivity.this,"不关联任务"),L10n.t(FocusActivity.this,"按自己的节奏直接开始"),taskId<=0&&selectedTodoId.isEmpty(),()->{selectedTodoId="";selectedTodoDate="";taskId=0;taskTitle=L10n.t(FocusActivity.this,"不关联任务");sheet[0].dismiss();renderFocus(false);});
            String today=CalendarDates.key(System.currentTimeMillis());
            data.todos.sort((a,b)->Boolean.compare(TodoSchedule.on(b,today)&&!TodoSchedule.done(b,today),TodoSchedule.on(a,today)&&!TodoSchedule.done(a,today)));
            int count=0;
            for(org.json.JSONObject item:data.todos)if(!item.optBoolean("done")){
                count++;String id=item.optString("id");sheetOption(options,item.optString("title"),AppUi.label(this,"待办事项","To-do")+" · "+L10n.category(this,item.optString("category","未分类"))+" · "+TodoSchedule.description(this,item),id.equals(selectedTodoId),()->{selectedTodoId=id;taskId=0;taskTitle=item.optString("title");selectedTodoDate=defaultTodoDate(item);sheet[0].dismiss();renderFocus(false);if(TodoSchedule.dates(item).size()>1)chooseTodoDate(item);});
            }
            if(count==0)options.addView(AppUi.text(this,L10n.t(FocusActivity.this,"暂无可关联的任务，也可以直接专注。"),14,AppUi.GRAY,false));
            sheet[0]=optionSheet(L10n.t(FocusActivity.this,"关联任务"),options);
        },e->AppUi.error(this,e));
    }
    private String defaultTodoDate(org.json.JSONObject item){
        String today=CalendarDates.key(System.currentTimeMillis());
        if(TodoSchedule.on(item,today)&&!TodoSchedule.done(item,today))return today;
        for(String date:TodoSchedule.dates(item))if(!TodoSchedule.done(item,date))return date;
        return "";
    }
    private void chooseTodoDate(org.json.JSONObject item){
        LinearLayout options=new LinearLayout(this);options.setOrientation(LinearLayout.VERTICAL);final Dialog[] sheet=new Dialog[1];
        for(String date:TodoSchedule.dates(item))if(!TodoSchedule.done(item,date))sheetOption(options,date,AppUi.label(this,"关联此日期的任务，实际计时独立记录","Link this task date; actual focus time is recorded separately"),date.equals(selectedTodoDate),()->{selectedTodoDate=date;sheet[0].dismiss();renderFocus(false);});
        sheet[0]=optionSheet(AppUi.label(this,"关联任务日期","Task date"),options);
    }
    private void chooseMusic(){
        LinearLayout options=new LinearLayout(this);options.setOrientation(LinearLayout.VERTICAL);final Dialog[] sheet=new Dialog[1];
        String current=AppUi.prefs(this).getString("focus_audio","");int count=0;
        java.lang.reflect.Field[] fields=R.raw.class.getFields();Arrays.sort(fields,(a,b)->a.getName().compareTo(b.getName()));
        for(java.lang.reflect.Field field:fields){
            String name=field.getName();if(name.equals("rain")||name.equals("waves")||name.equals("forest"))continue;
            try{int id=field.getInt(null);android.util.TypedValue value=new android.util.TypedValue();getResources().getValue(id,value,true);
                if(value.string==null||!value.string.toString().toLowerCase(Locale.ROOT).endsWith(".mp3"))continue;
                String uri="android.resource://"+getPackageName()+"/raw/"+name;String label=name.replace('_',' ');count++;
                sheetOption(options,label,L10n.t(FocusActivity.this,"项目音乐"),uri.equals(current),()->{AppUi.prefs(this).edit().putString("focus_audio",uri).putString("focus_audio_name",label).apply();selectedSound=4;sheet[0].dismiss();if(session!=null&&session.status==TaskProvider.RUNNING)play(4);renderFocus(session!=null&&session.status==TaskProvider.RUNNING);});
            }catch(Exception e){android.util.Log.w("FocusMusic","无法读取音乐资源",e);}
        }
        if(count==0){options.addView(AppUi.text(this,L10n.t(FocusActivity.this,"暂无项目音乐"),17,AppUi.INK,true));options.addView(AppUi.text(this,L10n.t(FocusActivity.this,"添加音乐后，会在这里显示供你选择。"),14,AppUi.GRAY,false));AppUi.space(options,16);}
        sheetOption(options,L10n.t(FocusActivity.this,"从设备选择音频"),L10n.t(FocusActivity.this,"也可以使用手机里的音乐"),false,()->{sheet[0].dismiss();pickAudio();});
        sheet[0]=optionSheet(L10n.t(FocusActivity.this,"选择背景音乐"),options);
        sheet[0].setOnDismissListener(d->{if(soundOptions!=null)soundOptions.setSelection(soundPosition());});
    }
    private void chooseDuration(){
        final Dialog dialog=new Dialog(this);LinearLayout sheet=new LinearLayout(this);sheet.setOrientation(LinearLayout.VERTICAL);sheet.setPadding(AppUi.dp(this,24),AppUi.dp(this,20),AppUi.dp(this,24),AppUi.dp(this,24));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(AppUi.SURFACE);bg.setCornerRadius(AppUi.dp(this,24));sheet.setBackground(bg);
        TextView title=AppUi.text(this,L10n.t(FocusActivity.this,"专注时长"),21,AppUi.INK,true);title.setGravity(Gravity.CENTER);sheet.addView(title);
        LinearLayout wheels=new LinearLayout(this);wheels.setGravity(Gravity.CENTER);
        NumberPicker minutes=new NumberPicker(this),seconds=new NumberPicker(this);minutes.setMinValue(0);minutes.setMaxValue(180);seconds.setMinValue(0);seconds.setMaxValue(59);minutes.setValue(chosenSeconds/60);seconds.setValue(chosenSeconds%60);
        for(NumberPicker picker:new NumberPicker[]{minutes,seconds}){picker.setWrapSelectorWheel(false);picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);if(Build.VERSION.SDK_INT>=29)picker.setTextColor(AppUi.INK);wheels.addView(picker,new LinearLayout.LayoutParams(AppUi.dp(this,90),AppUi.dp(this,170)));wheels.addView(AppUi.text(this,picker==minutes?L10n.t(FocusActivity.this,"分"):L10n.t(FocusActivity.this,"秒"),16,AppUi.GRAY,false));}
        sheet.addView(wheels);TextView helper=AppUi.text(this,L10n.t(FocusActivity.this,"上下滑动选择 · 1 秒至 180 分钟"),12,AppUi.GRAY,false);helper.setGravity(Gravity.CENTER);sheet.addView(helper);
        Button save=AppUi.button(this,L10n.t(FocusActivity.this,"保存"),false);sheet.addView(save);save.setOnClickListener(v->{int value=minutes.getValue()*60+seconds.getValue();if(value<1||value>10800){AppUi.toast(this,L10n.t(FocusActivity.this,"请选择 1 秒至 180 分钟"));return;}chosenSeconds=value;dialog.dismiss();renderFocus(false);});
        dialog.setContentView(sheet);android.view.Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);}dialog.show();if(window!=null)window.setLayout(-1,-2);
    }
    private void startFocus(){
        busy=true;endButton.setEnabled(false);final long selectedTask=taskId;final boolean free=freeMode;final int seconds=chosenSeconds;
        StudyRepository.run(this,()->{StudyRepository.Session s=(selectedTodoId.isEmpty()?StudyRepository.startFocus(getApplicationContext(),selectedTask,seconds,free):StudyRepository.startTodoFocus(getApplicationContext(),selectedTodoId,selectedTodoDate,seconds,free));FocusCoordinator.schedule(getApplicationContext(),s);return s;},s->{busy=false;session=s;requestedSession=s.id;failedSave=false;resultShown=false;renderSession();if(visible&&s.owned()&&s.status==TaskProvider.RUNNING)play(selectedSound);},e->{busy=false;endButton.setEnabled(true);AppUi.error(this,e);});
    }
    private Button roundedAction(String title,boolean primary){
        Button button=AppUi.button(this,title,primary);button.setAllCaps(false);button.setTextSize(15);
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(primary?AppUi.GREEN:AppUi.SOFT);shape.setCornerRadius(AppUi.dp(this,26));
        button.setBackgroundTintList(null);button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((AppUi.GREEN&0xffffff)|0x33000000),shape,null));
        button.setTextColor(primary?(AppUi.dark?AppUi.BG:android.graphics.Color.WHITE):AppUi.GREEN);button.setElevation(0);return button;
    }
    private void togglePause(){
        if(busy||session==null||session.status!=TaskProvider.RUNNING)return;
        busy=true;handler.removeCallbacks(tick);
        StudyRepository.run(this,()->StudyRepository.pause(getApplicationContext(),session.id,!session.paused()),updated->{
            busy=false;session=updated;renderSession();if(!session.paused()&&visible)play(selectedSound);
        },error->{busy=false;AppUi.error(this,error);renderSession();});
    }
    private void confirmEnd(){
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);int padding=AppUi.dp(this,22);content.setPadding(padding,padding,padding,padding);
        TextView title=AppUi.text(this,AppUi.label(this,"结束本次专注？","End this session?"),21,AppUi.INK,true);title.setGravity(Gravity.CENTER);content.addView(title);
        TextView message=AppUi.text(this,AppUi.label(this,freeMode?"保存本次实际专注时长，或放弃本次记录。":"保存后，实际专注时长计入统计，并标记为提前结束。",freeMode?"Save your focused time, or discard this session.":"Saved focused time counts toward statistics and is marked as ended early."),14,AppUi.GRAY,false);
        message.setGravity(Gravity.CENTER);message.setPadding(0,AppUi.dp(this,12),0,AppUi.dp(this,14));content.addView(message);
        AlertDialog dialog=AppUi.dialog(this).setView(content).create();
        String[] labels={AppUi.label(this,"放弃本次计时（不保存）","Discard session"),AppUi.label(this,"结束并保存已专注时长","End and save focused time"),AppUi.label(this,"取消","Cancel")};
        for(int i=0;i<3;i++){final int choice=i;Button option=roundedAction(labels[i],i==1);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,AppUi.dp(this,54));lp.topMargin=AppUi.dp(this,10);content.addView(option,lp);option.setOnClickListener(v->{dialog.dismiss();if(choice==0)discardSession();else if(choice==1)finishSession(TaskProvider.EARLY_END);});}
        dialog.show();if(dialog.getWindow()!=null){android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(AppUi.SURFACE);background.setCornerRadius(AppUi.dp(this,26));dialog.getWindow().setBackgroundDrawable(background);dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-AppUi.dp(this,40),AppUi.dp(this,420)),-2);}
    }

    private void discardSession(){
        if(busy||session==null)return;
        busy=true;handler.removeCallbacks(tick);long id=session.id;
        if(endButton!=null){endButton.setEnabled(false);endButton.setText(L10n.t(FocusActivity.this,"正在结束…"));}
        StudyRepository.run(this,()->{
            StudyRepository.discardSession(this,id);
            try{FocusCoordinator.cancel(this,id);}catch(RuntimeException e){android.util.Log.w("FocusActivity","取消提醒失败",e);}
            NoiseService.stopMatching(this,id);
            FocusCoordinator.clearNotice(this);
            return true;
        },ignored->{
            busy=false;session=null;requestedSession=-1;
            AppUi.toast(this,L10n.t(FocusActivity.this,"本次专注已结束，未保存记录"));
            load();
        },e->{
            busy=false;AppUi.error(this,e);
            if(endButton!=null){endButton.setEnabled(true);endButton.setText(L10n.t(FocusActivity.this,"重试结束但不保存"));endButton.setOnClickListener(v->discardSession());}
        });
    }
    private void renderSession(){
        handler.removeCallbacks(tick);page.content.removeAllViews();freeMode=session.free;
        if(session.status!=TaskProvider.RUNNING){renderResult();return;}
        if(!session.owned()){
            page.content.addView(AppUi.text(this,L10n.t(FocusActivity.this,"上次专注因进程退出而中断，确认后保留最后保存的时长，不计入正式统计。"),17,AppUi.INK,false));endButton=AppUi.button(this,L10n.t(FocusActivity.this,"确认中断并保存"),true);page.content.addView(endButton);endButton.setOnClickListener(v->finishSession(TaskProvider.INTERRUPTED));homeButton();return;
        }
        renderFocus(true);
    }
    private void pickAudio(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),PICK_AUDIO);}catch(Exception e){AppUi.error(this,e);}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_BACKGROUND){if(result==RESULT_OK&&data!=null&&data.getData()!=null){android.net.Uri uri=data.getData();StudyRepository.run(this,()->FocusAtmosphere.importPhoto(getApplicationContext(),uri),path->updateAtmosphere(),e->AppUi.error(this,e));}return;}
        if(request!=PICK_AUDIO)return;
        if(result==RESULT_OK&&data!=null&&data.getData()!=null){android.net.Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);AppUi.prefs(this).edit().putString("focus_audio",uri.toString()).putString("focus_audio_name","设备音频").apply();selectedSound=4;if(session!=null)play(4);}catch(Exception e){AppUi.error(this,e);}}
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible || session == null || session.status != TaskProvider.RUNNING || !session.owned()) return;
            long seconds = session.free?session.elapsedMillis()/1000:(session.remaining() + 999) / 1000;
            if (countdown != null) countdown.setText(time(seconds));
            View ring=page.content.findViewWithTag("focus_ring");if(ring instanceof FrameLayout)((FrameLayout)ring).getChildAt(0).invalidate();
            if (soundState != null) soundState.setText(NoiseService.status(FocusActivity.this,session.id));
            if (!session.free && seconds == 0) {
                if (!busy && !failedSave) finishSession(TaskProvider.COMPLETED);
                return;
            }
            if (SystemClock.elapsedRealtime() - lastCheckpoint >= 5000) checkpoint();
            handler.postDelayed(this, 500);
        }
    };
    private void checkpoint() {
        if (session == null || session.status != TaskProvider.RUNNING || !session.owned() || session.paused()) return;
        long id = session.id; android.content.Context app = getApplicationContext(); lastCheckpoint = SystemClock.elapsedRealtime();
        StudyRepository.IO.execute(() -> {
            try { StudyRepository.updateStatus(app, id, TaskProvider.RUNNING); }
            catch (Exception e) { android.util.Log.w("FocusActivity", "保存进度失败", e); }
        });
    }
    private void play(int sound) {
        if (session == null || session.status != TaskProvider.RUNNING || !session.owned() || session.paused()) return;
        try { if(sound==4){String uri=AppUi.prefs(this).getString("focus_audio",null);if(uri==null){chooseMusic();return;}NoiseService.play(this,session.id,android.net.Uri.parse(uri));}else NoiseService.play(this, session.id, sound); }
        catch (RuntimeException e) { AppUi.toast(this, L10n.t(FocusActivity.this,"声音未能开启，计时仍会继续。请保持专注页面可见后重试。")); }
    }
    private void updateSoundButtons(LinearLayout row){for(int i=0;i<row.getChildCount();i++){Button b=(Button)row.getChildAt(i);int sound=i==3?0:i+1;b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selectedSound==sound?AppUi.SOFT:AppUi.SURFACE));}}
    private void finishSession(int status) {
        if (busy || session == null) return;
        busy = true; failedSave = false; handler.removeCallbacks(tick);
        if (endButton != null) { endButton.setEnabled(false); endButton.setText(L10n.t(FocusActivity.this,"正在保存…")); }
        long id = session.id;
        NoiseService.stopMatching(this, id);
        StudyRepository.run(this, () -> FocusCoordinator.finish(this, id, status, true), s -> {
            busy = false; session = s; renderSession();
        }, e -> {
            busy = false; failedSave = true;
            if (endButton != null) { endButton.setEnabled(true); endButton.setText(L10n.t(FocusActivity.this,"重试保存记录")); }
            if (hint != null) hint.setText(L10n.t(FocusActivity.this,"本次记录暂未保存。请重试，不要重复开始。"));
            AppUi.error(this, e);
            if (visible && session.owned() && session.remaining() > 0) handler.post(tick);
        });
    }
    private void renderResult() {
        String state = L10n.t(this,RecordsActivity.state(session));
        page.content.addView(AppUi.text(this, state, 30, AppUi.GREEN, true));
        page.content.addView(AppUi.text(this, L10n.t(FocusActivity.this,"本次记录已保存：") + AppUi.duration(this,session.actual), 20, AppUi.INK, true));
        page.content.addView(AppUi.text(this, (session.status == TaskProvider.COMPLETED || session.status == TaskProvider.EARLY_END) ? L10n.t(FocusActivity.this,"任务是否已经做完，由你自己决定。") : L10n.t(FocusActivity.this,"本次记录不计入完成统计。"), 15, AppUi.GRAY, false));
        if ((session.taskId>0||!session.todoId.isEmpty()) && (session.status == TaskProvider.COMPLETED || session.status == TaskProvider.EARLY_END)) {
            Button done = AppUi.button(this, L10n.t(FocusActivity.this,"同时将任务标为完成"), false); page.content.addView(done);
            done.setOnClickListener(v -> markTaskDone(done));
        }
        Button history = AppUi.button(this, L10n.t(FocusActivity.this,"查看学习记录"), true); page.content.addView(history);
        history.setOnClickListener(v -> { startActivity(new Intent(this, RecordsActivity.class).putExtra("stats_detail","all_focus")); finish(); });
        homeButton();
        if (visible && !resultShown) {
            resultShown = true; FocusCoordinator.clearNotice(this);
            resultDialog = AppUi.dialog(this).setTitle(state)
                    .setMessage("“" + L10n.sessionTitle(this,session) + "”\n" + AppUi.duration(this,session.actual) + L10n.t(FocusActivity.this,"\n记录已保存"))
                    .setPositiveButton(L10n.t(FocusActivity.this,"知道了"), null).create(); resultDialog.show();
        }
    }
    private void markTaskDone(Button button) {
        if(busy)return;
        if(!session.todoId.isEmpty()){
            org.json.JSONObject todo=StudyRepository.todo(this,session.todoId);
            if(todo!=null&&TodoSchedule.daily(todo)){
                if(!session.todoDate.isEmpty()){
                    if(!TodoSchedule.on(todo,session.todoDate)){AppUi.toast(this,AppUi.label(this,"关联日期已从任务安排中移除，请在待办中确认完成状态","The linked date was removed. Update completion in To-do."));return;}
                    try{TodoSchedule.complete(todo,session.todoDate,true);StudyRepository.saveTodo(this,todo,false);button.setText(AppUi.label(this,"已完成日期：","Completed date: ")+session.todoDate);button.setEnabled(false);}catch(Exception e){AppUi.error(this,e);}return;
                }
                ArrayList<String> pending=new ArrayList<>();for(String date:TodoSchedule.dates(todo))if(!TodoSchedule.done(todo,date))pending.add(date);
                if(pending.isEmpty()){button.setText(AppUi.label(this,"所有日期已完成","All dates completed"));return;}
                AppUi.dialog(this).setTitle(AppUi.label(this,"选择完成的日期","Choose completed date")).setItems(pending.toArray(new String[0]),(d,w)->{
                    try{org.json.JSONObject fresh=StudyRepository.todo(this,session.todoId);if(fresh==null)return;TodoSchedule.complete(fresh,pending.get(w),true);StudyRepository.saveTodo(this,fresh,false);button.setText(AppUi.label(this,"已完成日期：","Completed date: ")+pending.get(w));}catch(Exception e){AppUi.error(this,e);}
                }).setNegativeButton(L10n.t(this,"取消"),null).show();return;
            }
        }
        busy = true; button.setEnabled(false);
        long id = session.taskId;
        StudyRepository.run(this, () -> {
            if(!session.todoId.isEmpty())return StudyRepository.completeTodo(this,session.todoId);
            for (StudyRepository.Task task : StudyRepository.tasks(this)) {
                if (task.id == id) { if (!task.done) StudyRepository.done(this, task); return true; }
            }
            return false;
        }, exists -> { busy = false; button.setText(exists ? L10n.t(FocusActivity.this,"任务已完成") : L10n.t(FocusActivity.this,"任务已经删除")); }, e -> { busy = false; button.setEnabled(true); AppUi.error(this, e); });
    }
    private void permissionHint() {
        if (!FocusCoordinator.notificationsAllowed(this)) {
            page.content.addView(AppUi.text(this, L10n.t(FocusActivity.this,"结束提醒未开启；仍可正常计时、保存记录。"), 14, AppUi.GRAY, false));
            Button allow = AppUi.button(this, L10n.t(FocusActivity.this,"开启通知提醒"), false); page.content.addView(allow);
            allow.setOnClickListener(v -> SettingsActivity.enableNotifications(this));
        }
    }
    private void homeButton() {
        Button back = AppUi.button(this, L10n.t(FocusActivity.this,"返回专注"), false); page.content.addView(back);
        back.setOnClickListener(v -> { requestedSession=-1;session=null;resultShown=false;failedSave=false;getIntent().removeExtra("session_id");load(); });
    }
    private abstract static class Choice implements AdapterView.OnItemSelectedListener {
        public abstract void chosen(int position);
        @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { chosen(position); }
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }
    private class FocusRingView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); private final RectF rect=new RectF();
        FocusRingView(android.content.Context c){super(c);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(AppUi.dp(c,12));setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,radius=Math.min(cx,cy)-AppUi.dp(getContext(),12);
            paint.setColor(AppUi.SOFT);paint.setStrokeWidth(AppUi.dp(getContext(),freeMode?2:9));
            if(freeMode){for(int i=0;i<120;i++){double angle=Math.PI*2*i/120;float inner=radius-AppUi.dp(getContext(),22);c.drawLine(cx+(float)Math.cos(angle)*inner,cy+(float)Math.sin(angle)*inner,cx+(float)Math.cos(angle)*radius,cy+(float)Math.sin(angle)*radius,paint);}}
            else {c.drawCircle(cx,cy,radius,paint);if(session!=null&&session.status==TaskProvider.RUNNING){paint.setColor((AppUi.GREEN&0x00ffffff)|0x66000000);rect.set(cx-radius,cy-radius,cx+radius,cy+radius);c.drawArc(rect,-90,360*Math.max(0,Math.min(1,session.remaining()/(session.planned*1000f))),false,paint);}}
        }
    }
}
