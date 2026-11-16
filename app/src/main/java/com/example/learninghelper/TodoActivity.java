package com.example.learninghelper;

import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import org.json.*;
import java.util.*;

/** 第二个导航入口：日期驱动的待办日历，沿用原待办存储。 */
public class TodoActivity extends WheelActivity {
    private AppUi.Page page;
    private boolean renderedEnglish,resumed;
    private String categoryFilter;
    private String[] categoryLabels(List<String> values){String[] labels=new String[values.size()];for(int i = 0; i<labels.length; i++)labels[i]=L10n.category(this,values.get(i));return labels;}
    @Override protected void onResume(){super.onResume();if(page!=null && renderedEnglish!=L10n.english(this)){recreate();return;}if(resumed&&page!=null){try{
        List<JSONObject> fresh=StudyRepository.todos(this);items.clear();items.addAll(fresh);cachedMonthViews.clear();render(false);}catch(Exception e){AppUi.error(this,e);}}resumed=true;}
    private final ArrayList<JSONObject> items=new ArrayList<>();
    private Calendar selected=CalendarDates.day(System.currentTimeMillis());
    private int mode=1; // 0 年，1 连续月历，2 当天
    private LinearLayout body,toolbar;
    private boolean readable=true,animating;
    private int anchorYear;
    private LinearLayout agendaPanel;
    private View monthHeading, selectedWeek;
    private boolean returningToMonth;
    private android.animation.ValueAnimator calendarAnimator;
    private Bitmap doorImage;
    private DoorDrawable doors;
    private int doorSplit, monthScroll;
    private Calendar monthAnchor;
    private final ArrayList<View> cachedMonthViews=new ArrayList<>();
    private final HashMap<String,Integer> dayCounts=new HashMap<>();
    private String todayKey;
    private final String[] priorities={"重要 & 紧急","重要 & 不紧急","不重要 & 紧急","不重要 & 不紧急"};
    private final String[] viewNames={"日历视图","待办清单","四象限","分类视图"};
    private int viewType;
    private int timeRange;
    private PopupWindow rangePopup;
    private TextView rangeButton;
    private final ArrayList<JSONObject> visibleItems=new ArrayList<>();
    private TextView pageTitle;
    private Dialog navigationDialog;
    private int draftPriority=3;
    private String draftCategory="未分类";

    @Override protected void onDestroy(){if(rangePopup!=null)rangePopup.dismiss();if(navigationDialog!=null)navigationDialog.dismiss();if(calendarAnimator!=null){calendarAnimator.removeAllListeners();calendarAnimator.cancel();}if(doors!=null)page.scroll.getOverlay().remove(doors);if(doorImage!=null)doorImage.recycle();super.onDestroy();}

    private ScrollView agendaScroll;
    private final ArrayList<ScrollView> quadrantScrolls=new ArrayList<>();
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e){
        if(animating)return true;
        if(viewType==2 && e.getActionMasked()==MotionEvent.ACTION_SCROLL){
            float axis=e.getAxisValue(MotionEvent.AXIS_VSCROLL);if(axis==0)axis=e.getAxisValue(MotionEvent.AXIS_SCROLL);
            if(axis!=0){
                Rect bounds=new Rect();for(ScrollView scroll:quadrantScrolls)if(scroll.getGlobalVisibleRect(bounds)&&bounds.contains((int)e.getRawX(),(int)e.getRawY())){scroll.scrollBy(0,Math.round(-axis*AppUi.dp(this,56)));return true;}}
        }
        if(viewType==0 && mode==2 && agendaScroll!=null && e.getActionMasked()==MotionEvent.ACTION_SCROLL){float axis=e.getAxisValue(MotionEvent.AXIS_VSCROLL);if(axis==0)axis=e.getAxisValue(MotionEvent.AXIS_SCROLL);if(axis!=0){agendaScroll.scrollBy(0,Math.round(-axis*AppUi.dp(this,56)));return true;}}
        return super.dispatchGenericMotionEvent(e);
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        renderedEnglish=L10n.english(this);
        if(state!=null){mode=state.getInt("mode",1);selected=CalendarDates.day(state.getLong("day",System.currentTimeMillis()));}
        viewType=Math.max(0,Math.min(3,state==null?getSharedPreferences("learning_todos",0).getInt("view_type",0):state.getInt("view_type",0)));
        timeRange=Math.max(0,Math.min(TodoDateRange.LABELS.length-1,getSharedPreferences("learning_todos",0).getInt("time_range",0)));
        anchorYear=selected.get(Calendar.YEAR);
        try{JSONArray data=new JSONArray(getSharedPreferences("learning_todos",0).getString("items","[]"));for(int i=0;i<data.length();i++)items.add(data.getJSONObject(i));}
        catch(Exception e){readable=false;AppUi.error(this,e);}
        page=AppUi.page(this,AppUi.label(this,"待办","To-do"),"");AppUi.navigation(this,page,1);
        LinearLayout heading=(LinearLayout)page.root.getChildAt(0);pageTitle=(TextView)heading.getChildAt(0);pageTitle.setTextSize(L10n.english(this)?20:25);
        page.root.removeView(heading);FrameLayout titleBar=new FrameLayout(this);titleBar.addView(heading,new FrameLayout.LayoutParams(-1,-2));
        heading.setPadding(AppUi.dp(this,58),AppUi.dp(this,16),AppUi.dp(this,58),AppUi.dp(this,8));
        TextView menu=control("☰",()->showNavigation());menu.setTextSize(24);menu.setContentDescription(L10n.t(TodoActivity.this,"切换待办视图"));FrameLayout.LayoutParams menuParams=new FrameLayout.LayoutParams(AppUi.dp(this,48),AppUi.dp(this,48),Gravity.START|Gravity.CENTER_VERTICAL);menuParams.leftMargin=AppUi.dp(this,8);titleBar.addView(menu,menuParams);rangeButton=control("",()->showTimeRanges());rangeButton.setTextSize(L10n.english(this)?12:14);
        FrameLayout.LayoutParams rangeParams=new FrameLayout.LayoutParams(AppUi.dp(this,96),AppUi.dp(this,48),Gravity.END|Gravity.CENTER_VERTICAL);rangeParams.rightMargin=AppUi.dp(this,8);titleBar.addView(rangeButton,rangeParams);
        heading.setPadding(AppUi.dp(this,104),AppUi.dp(this,16),AppUi.dp(this,104),AppUi.dp(this,8));page.root.addView(titleBar,0);
        toolbar=new LinearLayout(this);toolbar.setPadding(AppUi.dp(this,16),0,AppUi.dp(this,16),0);page.root.addView(toolbar,1);
        body=page.content;render(false);
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("view_type",viewType);out.putInt("mode",mode);out.putLong("day",selected.getTimeInMillis());}
    private void reloadItems(){items.clear();items.addAll(StudyRepository.todos(this));cachedMonthViews.clear();}
    private void saveItem(JSONObject item,boolean create){StudyRepository.saveTodo(this,item,create);reloadItems();}
    private TextView control(String text,Runnable action){TextView v=AppUi.text(this,text,16,AppUi.GREEN,true);v.setGravity(Gravity.CENTER);v.setMinHeight(AppUi.dp(this,48));v.setFocusable(true);v.setOnClickListener(w->{if(!animating)action.run();});return v;}
    private void tools(){toolbar.removeAllViews();toolbar.setVisibility(viewType==2||viewType==3?View.GONE:View.VISIBLE);if(viewType==2||viewType==3)return;
        if(viewType!=0){TextView label=AppUi.text(this,"",14,AppUi.GRAY,false);toolbar.addView(label,new LinearLayout.LayoutParams(0,-2,1));TextView add=control("＋",()->newItem(3,"未分类"));add.setTextSize(26);add.setContentDescription(L10n.t(TodoActivity.this,"添加待办事项"));toolbar.addView(add,new LinearLayout.LayoutParams(AppUi.dp(this,48),-2));return;}

        toolbar.addView(control(mode==0?L10n.t(TodoActivity.this,"月历"):L10n.t(TodoActivity.this,"‹ 年"),()->{mode=mode==0?1:0;render(false);}),new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(control(L10n.t(TodoActivity.this,"今天"),()->{selected=CalendarDates.day(System.currentTimeMillis());anchorYear=selected.get(Calendar.YEAR);mode=2;render(false);}),new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(control(L10n.t(TodoActivity.this,"未安排"),()->undated()),new LinearLayout.LayoutParams(0,-2,1));
        TextView add=control("＋",()->edit(null));add.setTextSize(28);add.setContentDescription(L10n.t(TodoActivity.this,"添加待办事项"));add.setEnabled(readable);toolbar.addView(add,new LinearLayout.LayoutParams(AppUi.dp(this,48),-2));
    }
    private int count(String day){Integer count=dayCounts.get(day);return count==null?0:count;}
    private void render(boolean animate){
        quadrantScrolls.clear();
        todayKey=CalendarDates.key(System.currentTimeMillis());dayCounts.clear();for(JSONObject item:items){String date=item.optString("date");dayCounts.put(date,count(date)+1);}
        rangeButton.setVisibility(viewType==0?View.GONE:View.VISIBLE);rangeButton.setText(L10n.t(this,TodoDateRange.LABELS[timeRange])+" ▾");rangeButton.setContentDescription(L10n.t(TodoActivity.this,"时间范围：")+L10n.t(this,TodoDateRange.LABELS[timeRange]));
        visibleItems.clear();Calendar today=CalendarDates.day(System.currentTimeMillis());for(JSONObject item:items)if(TodoDateRange.matches(item.optString("date"),timeRange,today))visibleItems.add(item);
        pageTitle.setText(viewType==0?AppUi.label(this,"待办","To-do"):L10n.t(this,viewNames[viewType]));tools();body.removeAllViews();page.scroll.scrollTo(0,0);
        if(!readable){body.addView(AppUi.text(this,L10n.t(TodoActivity.this,"待办读取失败，原数据已保留。请重新打开重试。"),16,AppUi.GRAY,false));return;}
        if(viewType==1&&visibleItems.isEmpty())body.addView(AppUi.text(this,L10n.t(TodoActivity.this,"这个时间范围内暂无待办"),15,AppUi.GRAY,false));
        if(viewType==1)taskList();else if(viewType==2)quadrants();else if(viewType==3)categoryView();else if(mode==0)year();else if(mode==1)months();else agenda();
        if(animate){body.setAlpha(0f);body.animate().alpha(1f).setDuration(180).start();}
    }
    private void year(){
        for(int y=anchorYear-1;y<=anchorYear+1;y++){
            final int year=y;TextView title=AppUi.text(this,AppUi.label(this,year+"年",String.valueOf(year)),32,AppUi.GREEN,true);body.addView(title);if(year==anchorYear)title.post(()->{if(mode==0)page.scroll.scrollTo(0,title.getTop());});
            for(int row=0;row<4;row++){LinearLayout line=new LinearLayout(this);for(int col=0;col<3;col++){final int month=row*3+col;MiniMonth v=new MiniMonth(year,month);v.setOnClickListener(w->{selected=CalendarDates.date(year,month,1);anchorYear=year;mode=1;render(true);});LinearLayout.LayoutParams monthParams=new LinearLayout.LayoutParams(0,AppUi.dp(this,166),1);if(col<2)monthParams.rightMargin=AppUi.dp(this,12);line.addView(v,monthParams);}body.addView(line);}AppUi.space(body,24);
        }
        body.addView(control(L10n.t(TodoActivity.this,"下一年 ↓"),()->{anchorYear++;selected=CalendarDates.date(anchorYear,0,1);render(false);}));
        body.addView(control(L10n.t(TodoActivity.this,"↑ 上一年"),()->{anchorYear--;selected=CalendarDates.date(anchorYear,0,1);render(false);}));
    }
    private void months(){
        if(returningToMonth&&!cachedMonthViews.isEmpty()){for(View child:cachedMonthViews){if(child.getParent()!=null)((ViewGroup)child.getParent()).removeView(child);body.addView(child);}return;}
        cachedMonthViews.clear();
        Calendar base=returningToMonth&&monthAnchor!=null?monthAnchor:selected;Calendar first=CalendarDates.date(base.get(Calendar.YEAR),base.get(Calendar.MONTH),1);
        for(int delta=-2;delta<=4;delta++){
            Calendar month=CalendarDates.plus(first,Calendar.MONTH,delta);TextView title=AppUi.text(this,L10n.month(this,month),25,AppUi.GREEN,true);body.addView(title);
            if(delta==0){monthHeading=title;if(!returningToMonth)title.post(()->{if(mode==1&&!returningToMonth)page.scroll.scrollTo(0,title.getTop());});}
            body.addView(weekdays());Calendar start=CalendarDates.week(month);int days=month.getActualMaximum(Calendar.DATE),offset=(month.get(Calendar.DAY_OF_WEEK)+5)%7,rows=(days+offset+6)/7;
            for(int w=0;w<rows;w++){
                Calendar weekStart=CalendarDates.plus(start,Calendar.DATE,w*7);
                LinearLayout week=weekRow(weekStart,month.get(Calendar.MONTH),false);body.addView(week);
                if(delta==0 && CalendarDates.key(weekStart).equals(CalendarDates.key(CalendarDates.week(selected))))selectedWeek=week;
            }
            AppUi.space(body,20);
        }
        body.addView(control(L10n.t(TodoActivity.this,"查看更多月份 ↓"),()->{selected.add(Calendar.MONTH,4);render(false);}));
        body.addView(control(L10n.t(TodoActivity.this,"↑ 更早月份"),()->{selected.add(Calendar.MONTH,-2);render(false);}));
    }
    private LinearLayout weekdays(){LinearLayout row=new LinearLayout(this);for(String name:new String[]{L10n.t(TodoActivity.this,"一"),L10n.t(TodoActivity.this,"二"),L10n.t(TodoActivity.this,"三"),L10n.t(TodoActivity.this,"四"),L10n.t(TodoActivity.this,"五"),L10n.t(TodoActivity.this,"六"),L10n.t(TodoActivity.this,"日")}){TextView t=AppUi.text(this,name,13,AppUi.GRAY,false);t.setGravity(Gravity.CENTER);row.addView(t,new LinearLayout.LayoutParams(0,AppUi.dp(this,28),1));}return row;}
    private LinearLayout weekRow(Calendar start,int month,boolean compact){
        LinearLayout row=new LinearLayout(this);
        for(int i=0;i<7;i++){
            final Calendar date=CalendarDates.plus(start,Calendar.DATE,i);String key=CalendarDates.key(date);boolean active=mode==2&&key.equals(CalendarDates.key(selected));boolean today=todayKey.equals(key);
            LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);cell.setPadding(0,AppUi.dp(this,8),0,0);
            TextView number=AppUi.text(this,String.valueOf(date.get(Calendar.DATE)),19,today? (AppUi.dark?AppUi.BG:Color.WHITE):active?AppUi.GREEN:date.get(Calendar.MONTH)==month?AppUi.INK:AppUi.GRAY,today||active);
            number.setGravity(Gravity.CENTER);android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);shape.setColor(today?AppUi.GREEN:Color.TRANSPARENT);if(active&&!today)shape.setStroke(AppUi.dp(this,2),AppUi.GREEN);number.setBackground(shape);cell.addView(number,new LinearLayout.LayoutParams(AppUi.dp(this,34),AppUi.dp(this,34)));
            if(!compact&&count(key)>0){TextView dot=AppUi.text(this,count(key)+L10n.t(TodoActivity.this,"项"),11,AppUi.GREEN,false);dot.setGravity(Gravity.CENTER);cell.addView(dot);}
            cell.setContentDescription(key+"，"+count(key)+L10n.t(TodoActivity.this,"项待办"));cell.setFocusable(true);cell.setOnClickListener(v->{if(animating)return;if(mode==2&&key.equals(CalendarDates.key(selected))){expandMonth();}else if(mode==1){collapse(date,row);}else{selected=date;render(true);}});
            row.addView(cell,new LinearLayout.LayoutParams(0,AppUi.dp(this,compact?52:84),1));
        }return row;
    }
    private void collapse(Calendar date,LinearLayout row){
        if(animating)return;
        if(page.scroll.getWidth()==0||page.scroll.getHeight()==0)return;
        cachedMonthViews.clear();for(int i=0;i<body.getChildCount();i++)cachedMonthViews.add(body.getChildAt(i));
        monthAnchor=(Calendar)selected.clone();monthScroll=page.scroll.getScrollY();
        if(doorImage!=null)doorImage.recycle();
        doorImage=Bitmap.createBitmap(page.scroll.getWidth(),page.scroll.getHeight(),Bitmap.Config.ARGB_8888);
        page.scroll.draw(new Canvas(doorImage));doorImage.prepareToDraw();
        doorSplit=Math.max(1,Math.min(doorImage.getHeight()-1,row.getBottom()-monthScroll));
        selected=date;animating=true;mode=2;render(false);
        // 待办先放在门后，覆盖层一直不透明，直到上下月历移动后露出内容。
        doors=new DoorDrawable();page.scroll.getOverlay().add(doors);
        page.scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
            @Override public boolean onPreDraw(){page.scroll.getViewTreeObserver().removeOnPreDrawListener(this);animateDoors(true);return true;}
        });
    }
    private void expandMonth(){
        if(animating)return;
        if(doorImage==null){mode=1;render(false);return;}
        animating=true;doors=new DoorDrawable();doors.progress=1;page.scroll.getOverlay().add(doors);animateDoors(false);
    }
    private void animateDoors(boolean opening){
        calendarAnimator=android.animation.ValueAnimator.ofFloat(opening?0:1,opening?1:0);
        calendarAnimator.setDuration(800);calendarAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        calendarAnimator.addUpdateListener(a->{if(doors!=null){doors.progress=(Float)a.getAnimatedValue();doors.invalidateSelf();}});
        calendarAnimator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){
            if(opening){page.scroll.getOverlay().remove(doors);doors=null;animating=false;}
            else{
                returningToMonth=true;mode=1;render(false);
                page.scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
                    @Override public boolean onPreDraw(){page.scroll.getViewTreeObserver().removeOnPreDrawListener(this);page.scroll.scrollTo(0,monthScroll);page.scroll.getOverlay().remove(doors);doors=null;returningToMonth=false;animating=false;return true;}
                });
            }
        }});calendarAnimator.start();
    }
    private class DoorDrawable extends android.graphics.drawable.Drawable {
        float progress;
        DoorDrawable(){setBounds(0,0,page.scroll.getWidth(),page.scroll.getHeight());}
        @Override public void draw(Canvas c){
            if(doorImage==null)return;
            int top=agendaPanel.getTop()-page.scroll.getScrollY(),bottom=agendaPanel.getBottom()-page.scroll.getScrollY();
            float up=Math.max(0,doorSplit-top)*progress,down=Math.max(0,bottom-doorSplit)*progress;
            c.save();c.clipRect(0,0,getBounds().width(),doorSplit-up);c.drawBitmap(doorImage,0,-up,null);c.restore();
            c.save();c.clipRect(0,doorSplit+down,getBounds().width(),getBounds().height());c.drawBitmap(doorImage,0,down,null);c.restore();
        }
        @Override public void setAlpha(int alpha){} @Override public void setColorFilter(ColorFilter filter){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e){if(animating)return true;return super.dispatchTouchEvent(e);}
    private void agenda(){
        body.addView(AppUi.text(this,L10n.month(this,selected),24,AppUi.INK,true));body.addView(weekdays());Calendar start=CalendarDates.week(selected);body.addView(weekRow(start,selected.get(Calendar.MONTH),false));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(0,AppUi.dp(this,12),0,AppUi.dp(this,12));panel.setBackgroundColor(AppUi.BG);agendaPanel=panel;panel.addView(AppUi.text(this,CalendarDates.key(selected)+L10n.t(TodoActivity.this," · 待办事项"),19,AppUi.INK,true));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);ScrollView inner=new ScrollView(this);agendaScroll=inner;inner.addView(list);panel.addView(inner,new LinearLayout.LayoutParams(-1,0,1));
        String day=CalendarDates.key(selected);int n=0;for(JSONObject item:items)if(day.equals(item.optString("date"))){n++;itemRow(list,item);}if(n==0)list.addView(AppUi.text(this,L10n.t(TodoActivity.this,"这一天还没有事项，点击＋添加。"),15,AppUi.GRAY,false));
        LinearLayout penRow=new LinearLayout(this);penRow.setGravity(Gravity.CENTER);
        View pen=new PencilButton();
        pen.setContentDescription(L10n.t(TodoActivity.this,"添加当天事项"));pen.setFocusable(true);pen.setOnClickListener(v->edit(null));penRow.addView(pen,new LinearLayout.LayoutParams(AppUi.dp(this,52),AppUi.dp(this,52)));panel.addView(penRow);
        int height=Math.max(AppUi.dp(this,200),page.scroll.getHeight()-AppUi.dp(this,300));body.addView(panel,new LinearLayout.LayoutParams(-1,height));

        body.addView(weekRow(CalendarDates.plus(start,Calendar.DATE,7),selected.get(Calendar.MONTH),true));
        LinearLayout arrows=new LinearLayout(this);arrows.addView(control(L10n.t(TodoActivity.this,"‹ 上一周"),()->{selected.add(Calendar.DATE,-7);render(true);}),new LinearLayout.LayoutParams(0,-2,1));arrows.addView(control(L10n.t(TodoActivity.this,"下一周 ›"),()->{selected.add(Calendar.DATE,7);render(true);}),new LinearLayout.LayoutParams(0,-2,1));body.addView(arrows);
        // 当天列表自己滚动，使顶部日期行和底部下一周始终可见。
        inner.setOnGenericMotionListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_SCROLL){inner.scrollBy(0,Math.round(-event.getAxisValue(MotionEvent.AXIS_VSCROLL)*AppUi.dp(this,56)));return true;}return false;});
    }
    /** 鼠标悬停预览与按压反馈共用原生按钮状态，取消点击时也能恢复。 */
    private class PencilButton extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        PencilButton(){
            super(TodoActivity.this);
            setClickable(true);setFocusable(true);
            setPointerIcon(PointerIcon.getSystemIcon(getContext(),PointerIcon.TYPE_HAND));
            android.graphics.drawable.GradientDrawable mask=new android.graphics.drawable.GradientDrawable();
            mask.setShape(android.graphics.drawable.GradientDrawable.OVAL);mask.setColor(Color.WHITE);
            int ripple=(AppUi.GREEN & 0x00ffffff) | 0x33000000;
            setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(ripple),null,mask));
        }
        @Override protected void drawableStateChanged(){
            super.drawableStateChanged();
            boolean preview=isEnabled()&&(isHovered()||isFocused());
            boolean down=isEnabled()&&isPressed();
            animate().cancel();
            animate().scaleX(down?.90f:preview?1.06f:1f).scaleY(down?.90f:preview?1.06f:1f)
                    .translationY(down?AppUi.dp(getContext(),2):0)
                    .setDuration(down?90:160).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            invalidate();
        }
        @Override protected void onDetachedFromWindow(){animate().cancel();super.onDetachedFromWindow();}
        @Override public CharSequence getAccessibilityClassName(){return Button.class.getName();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            c.save();c.translate(getWidth()/2f,getHeight()/2f);
            float density=getResources().getDisplayMetrics().density;c.scale(density,density);
            if(isHovered()||isFocused()||isPressed()){
                paint.setStyle(Paint.Style.FILL);paint.setColor(AppUi.SOFT);c.drawCircle(0,0,23,paint);
            }
            paint.setColor(AppUi.GREEN);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);
            c.rotate(40);c.drawRect(-5,-13,5,8,paint);
            Path tip=new Path();tip.moveTo(-5,8);tip.lineTo(0,15);tip.lineTo(5,8);c.drawPath(tip,paint);
            c.drawLine(-5,-8,5,-8,paint);c.restore();
        }
    }
    private void itemRow(LinearLayout list,JSONObject item){
        LinearLayout row=new LinearLayout(this);row.setPadding(0,AppUi.dp(this,8),0,AppUi.dp(this,8));row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox done=new CheckBox(this);AppUi.tintControl(done);done.setText(item.optString("title"));done.setTextColor(AppUi.INK);done.setChecked(item.optBoolean("done"));LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);titleRow.addView(done,new LinearLayout.LayoutParams(0,-2,1));info.addView(titleRow);
        String category=item.optString("category","未分类");int importance=Math.max(0,Math.min(3,item.optInt("priority",3)));
        TextView categoryTag=AppUi.text(this,L10n.category(this,category),12,AppUi.tagColor(this,category),false);categoryTag.setMaxLines(1);categoryTag.setEllipsize(android.text.TextUtils.TruncateAt.END);categoryTag.setMaxWidth(AppUi.dp(this,90));titleRow.addView(categoryTag);TextView level=priorityLabel(importance,12);info.addView(level);info.addView(AppUi.text(this,L10n.t(TodoActivity.this,"截止：")+(item.optString("date").isEmpty()?L10n.t(TodoActivity.this,"未安排"):item.optString("date")),12,AppUi.GRAY,false));row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        done.setOnCheckedChangeListener((v,on)->{try{item.put("done",on);item.put("completed_at",on?System.currentTimeMillis():0L);saveItem(item,false);}catch(Exception e){AppUi.error(this,e);}});
        TextView more=control("···",()->new AlertDialog.Builder(this).setItems(new String[]{L10n.t(TodoActivity.this,"编辑 / 调整日期"),L10n.t(TodoActivity.this,"删除")},(d,w)->{if(w==0)edit(item);else new AlertDialog.Builder(this).setTitle(L10n.t(TodoActivity.this,"删除这条待办？")).setMessage(item.optString("title")).setNegativeButton(L10n.t(TodoActivity.this,"取消"),null).setPositiveButton(L10n.t(TodoActivity.this,"删除"),(x,y)->{StudyRepository.deleteTodo(this,item.optString("id"));reloadItems();render(false);}).show();}).show());row.addView(more,new LinearLayout.LayoutParams(AppUi.dp(this,48),-2));list.addView(row);
    }
    private void edit(JSONObject existing){
        if(!readable)return;LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(AppUi.dp(this,20),0,AppUi.dp(this,20),0);
        EditText title=new EditText(this);AppUi.tintControl(title);title.setHint(L10n.t(TodoActivity.this,"准备做什么？"));title.setText(existing==null?"":existing.optString("title"));form.addView(title);
        final Calendar date=(Calendar)selected.clone();if(existing!=null&&!existing.optString("date").isEmpty()){try{String[] parts=existing.getString("date").split("-");date.setTimeInMillis(CalendarDates.date(Integer.parseInt(parts[0]),Integer.parseInt(parts[1])-1,Integer.parseInt(parts[2])).getTimeInMillis());}catch(Exception ignored){}}
        form.addView(AppUi.text(this,L10n.t(TodoActivity.this,"分类"),15,AppUi.INK,true));
        final String[] category={existing==null?draftCategory:existing.optString("category","未分类")};
        Button categoryButton=AppUi.button(this,L10n.category(this,category[0])+" ▾",false);form.addView(categoryButton);
        final LinkedHashSet<String> categories=new LinkedHashSet<>();categories.add("未分类");categories.add("学习");categories.add("作业");categories.add("生活");
        categories.addAll(getSharedPreferences("learning_todos",0).getStringSet("categories",new HashSet<>()));for(JSONObject item:items)if(!item.optString("category").isEmpty())categories.add(item.optString("category"));
        categoryButton.setOnClickListener(v->{ArrayList<String> options=new ArrayList<>(categories);options.add(L10n.t(TodoActivity.this,"＋ 新建分类"));new AlertDialog.Builder(this).setTitle(L10n.t(TodoActivity.this,"选择分类")).setItems(categoryLabels(options),(d,which)->{
            if(which<options.size()-1){category[0]=options.get(which);categoryButton.setText(L10n.category(this,category[0])+" ▾");}
            else{EditText name=new EditText(this);AppUi.tintControl(name);name.setHint(L10n.t(TodoActivity.this,"分类名称"));name.setSingleLine();name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(20)});
                AlertDialog create=new AlertDialog.Builder(this).setTitle(L10n.t(TodoActivity.this,"新建分类")).setView(name).setNegativeButton(L10n.t(TodoActivity.this,"取消"),null).setPositiveButton(L10n.t(TodoActivity.this,"添加"),null).create();create.setOnShowListener(x->create.getButton(-1).setOnClickListener(w->{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError(L10n.t(TodoActivity.this,"请填写分类名称"));return;}categories.add(value);category[0]=value;categoryButton.setText(value+" ▾");create.dismiss();}));create.show();}
        }).show();});
        form.addView(AppUi.text(this,L10n.t(TodoActivity.this,"截止日期"),15,AppUi.INK,true));
        final boolean[] noDate={existing!=null?existing.optString("date").isEmpty():viewType!=0&&timeRange==8};
        Button pick=AppUi.button(this,(noDate[0]?L10n.t(TodoActivity.this,"未安排"):CalendarDates.key(date))+" ▾",false);form.addView(pick);
        pick.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(L10n.t(TodoActivity.this,"截止日期")).setItems(new String[]{L10n.t(TodoActivity.this,"今天"),L10n.t(TodoActivity.this,"明天"),L10n.t(TodoActivity.this,"下周（下周一）"),L10n.t(TodoActivity.this,"选择日期"),L10n.t(TodoActivity.this,"未安排日期")},(d,which)->{
            Calendar today=CalendarDates.day(System.currentTimeMillis());
            if(which==4){noDate[0]=true;pick.setText(L10n.t(TodoActivity.this,"未安排 ▾"));return;}
            if(which==3){new DatePickerDialog(this,(view,y,m,day)->{noDate[0]=false;date.setTimeInMillis(CalendarDates.date(y,m,day).getTimeInMillis());pick.setText(CalendarDates.key(date)+" ▾");},date.get(Calendar.YEAR),date.get(Calendar.MONTH),date.get(Calendar.DATE)).show();return;}
            Calendar due=which==0?today:which==1?CalendarDates.plus(today,Calendar.DATE,1):CalendarDates.plus(CalendarDates.week(today),Calendar.DATE,7);
            noDate[0]=false;date.setTimeInMillis(due.getTimeInMillis());pick.setText(CalendarDates.key(date)+" ▾");
        }).show());
        form.addView(AppUi.text(this,L10n.t(TodoActivity.this,"紧急重要程度"),15,AppUi.INK,true));
        Spinner priority=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,priorities){
            @Override public View getView(int position,View recycled,ViewGroup parent){return option(position);}
            @Override public View getDropDownView(int position,View recycled,ViewGroup parent){return option(position);}
            private View option(int position){TextView text=priorityLabel(position,15);text.setGravity(Gravity.CENTER_VERTICAL);text.setPadding(AppUi.dp(TodoActivity.this,10),AppUi.dp(TodoActivity.this,12),AppUi.dp(TodoActivity.this,8),AppUi.dp(TodoActivity.this,12));text.setMinHeight(AppUi.dp(TodoActivity.this,52));return text;}
        };priority.setAdapter(adapter);priority.setSelection(existing==null?draftPriority:Math.max(0,Math.min(3,existing.optInt("priority",3))));form.addView(priority,new LinearLayout.LayoutParams(-1,-2));draftPriority=3;draftCategory="未分类";
        ScrollView formScroll=new ScrollView(this);formScroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?L10n.t(TodoActivity.this,"添加待办"):L10n.t(TodoActivity.this,"编辑待办")).setView(formScroll).setNegativeButton(L10n.t(TodoActivity.this,"取消"),null).setPositiveButton(L10n.t(TodoActivity.this,"保存"),null).create();
        dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{
            String value=title.getText().toString().trim();if(value.isEmpty()){title.setError(L10n.t(TodoActivity.this,"请填写事项"));return;}
            try{JSONObject item=existing==null?new JSONObject():existing;
                if(existing==null){item.put("id",UUID.randomUUID().toString());item.put("done",false);}
                item.put("title",value);item.put("date",noDate[0]?"":CalendarDates.key(date));item.put("category",category[0]);item.put("priority",priority.getSelectedItemPosition());
                saveItem(item,existing==null);getSharedPreferences("learning_todos",0).edit().putStringSet("categories",new HashSet<>(categories)).apply();
                selected=(Calendar)date.clone();mode=2;dialog.dismiss();render(false);
            }catch(Exception e){AppUi.error(this,e);}
        }));dialog.show();
    }
    private void showTimeRanges(){
        if(rangePopup!=null)rangePopup.dismiss();
        LinearLayout options=new LinearLayout(this);options.setOrientation(LinearLayout.VERTICAL);options.setPadding(AppUi.dp(this,6),AppUi.dp(this,6),AppUi.dp(this,6),AppUi.dp(this,6));
        ScrollView scroll=new ScrollView(this);scroll.addView(options);
        rangePopup=new PopupWindow(scroll,AppUi.dp(this,180),-2,true);
        android.graphics.drawable.GradientDrawable surface=new android.graphics.drawable.GradientDrawable();surface.setColor(AppUi.SURFACE);surface.setCornerRadius(AppUi.dp(this,16));rangePopup.setBackgroundDrawable(surface);rangePopup.setElevation(AppUi.dp(this,8));rangePopup.setOutsideTouchable(true);
        for(int i=0;i<TodoDateRange.LABELS.length;i++){
            final int target=i;TextView option=control(L10n.t(this,TodoDateRange.LABELS[i])+(i==timeRange?"  ✓":""),()->{timeRange=target;getSharedPreferences("learning_todos",0).edit().putInt("time_range",target).apply();rangePopup.dismiss();render(false);});
            option.setTextColor(i==timeRange?AppUi.GREEN:AppUi.INK);option.setGravity(Gravity.CENTER_VERTICAL);option.setPadding(AppUi.dp(this,16),0,AppUi.dp(this,12),0);
            if(i==timeRange){android.graphics.drawable.GradientDrawable active=new android.graphics.drawable.GradientDrawable();active.setColor(AppUi.SOFT);active.setCornerRadius(AppUi.dp(this,10));option.setBackground(active);}options.addView(option,new LinearLayout.LayoutParams(-1,AppUi.dp(this,48)));
        }
        rangePopup.setHeight(Math.min(AppUi.dp(this,492),getResources().getDisplayMetrics().heightPixels/2));rangePopup.showAsDropDown(rangeButton,0,0,Gravity.END);
    }
    private void showNavigation(){
        if(animating)return;
        navigationDialog=new Dialog(this);LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(AppUi.SURFACE);panel.setPadding(AppUi.dp(this,20),AppUi.dp(this,32),AppUi.dp(this,20),AppUi.dp(this,24));
        panel.addView(AppUi.text(this,L10n.t(TodoActivity.this,"待办视图"),24,AppUi.INK,true));AppUi.space(panel,24);
        String[] icons={"▦","☷","⊞","▤"};
        for(int i=0;i<viewNames.length;i++){final int target=i;TextView option=AppUi.text(this,icons[i]+"   "+L10n.t(this,viewNames[i]),18,i==viewType?AppUi.GREEN:AppUi.INK,true);option.setGravity(Gravity.CENTER_VERTICAL);option.setPadding(AppUi.dp(this,14),0,AppUi.dp(this,10),0);option.setMinHeight(AppUi.dp(this,62));option.setContentDescription(L10n.t(this,viewNames[i]));option.setFocusable(true);if(i==viewType){android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(AppUi.SOFT);bg.setCornerRadius(AppUi.dp(this,12));option.setBackground(bg);}panel.addView(option,new LinearLayout.LayoutParams(-1,-2));option.setOnClickListener(v->{viewType=target;getSharedPreferences("learning_todos",0).edit().putInt("view_type",target).apply();navigationDialog.dismiss();render(false);});AppUi.space(panel,8);}
        navigationDialog.setContentView(panel);
        Window w=navigationDialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.START|Gravity.TOP);}navigationDialog.show();if(w!=null)w.setLayout(Math.min(AppUi.dp(this,300),getResources().getDisplayMetrics().widthPixels*4/5),-1);panel.setTranslationX(-AppUi.dp(this,300));panel.animate().translationX(0).setDuration(250).start();
    }
    private void newItem(int priority,String category){draftPriority=priority;draftCategory=category;edit(null);}
    private int priority(JSONObject item){return Math.max(0,Math.min(3,item.optInt("priority",3)));}
    private int priorityColor(int priority){int[] colors=AppUi.dark?new int[]{0xffff8787,0xffffca70,0xff89baff,0xffadb7bf}:new int[]{0xffc74646,0xffa36c13,0xff3479bd,0xff6c7b86};return colors[priority];}
    private TextView priorityLabel(int priority,int size){TextView text=AppUi.text(this,L10n.t(this,priorities[priority]),size,AppUi.INK,false);text.setCompoundDrawables(new PriorityFlag(priority),null,null,null);text.setCompoundDrawablePadding(AppUi.dp(this,6));return text;}
    private class PriorityFlag extends android.graphics.drawable.Drawable {
        final int level;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        PriorityFlag(int level){this.level=level;setBounds(0,0,AppUi.dp(TodoActivity.this,19),AppUi.dp(TodoActivity.this,19));}
        @Override public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);paint.setColor(priorityColor(level));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);c.drawLine(4,3,4,22,paint);paint.setStyle(Paint.Style.FILL);Path flag=new Path();flag.moveTo(5,3);flag.lineTo(22,5);flag.lineTo(22,16);flag.lineTo(5,14);flag.close();c.drawPath(flag,paint);paint.setColor(AppUi.dark?AppUi.BG:Color.WHITE);paint.setTextSize(9);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);c.drawText(level==0?"!!!":level<3?"!!":"!",13,12,paint);c.restore();}
        @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private void taskList(){
        for(boolean completed:new boolean[]{false,true}){ArrayList<JSONObject> group=new ArrayList<>();for(JSONObject item:visibleItems)if(item.optBoolean("done")==completed)group.add(item);
            if(group.isEmpty())continue;LinearLayout card=AppUi.card(this);card.addView(AppUi.text(this,(completed?L10n.t(TodoActivity.this,"已完成"):L10n.t(TodoActivity.this,"待完成"))+" · "+group.size(),18,AppUi.INK,true));for(JSONObject item:group)itemRow(card,item);body.addView(card);}
    }
    private void quadrants(){
        LinearLayout grid=new LinearLayout(this){
            @Override protected void onMeasure(int width,int height){
                int available=page.scroll.getHeight()-body.getPaddingTop()-body.getPaddingBottom();
                super.onMeasure(width, MeasureSpec.makeMeasureSpec(Math.max(AppUi.dp(TodoActivity.this,320),available), MeasureSpec.EXACTLY));
            }
        };grid.setOrientation(LinearLayout.VERTICAL);grid.setTag("quadrant_grid");
        for(int row=0;row<2;row++){LinearLayout line=new LinearLayout(this);
            for(int column=0;column<2;column++){final int level=row*2+column;LinearLayout card=AppUi.card(this);card.setPadding(AppUi.dp(this,8),AppUi.dp(this,6),AppUi.dp(this,8),AppUi.dp(this,8));
                LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
                TextView heading=priorityLabel(level,12);heading.setTextColor(priorityColor(level));header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
                TextView add=control("＋",()->newItem(level,"未分类"));add.setTextColor(priorityColor(level));add.setTextSize(22);add.setContentDescription(AppUi.label(this,"添加","Add ")+L10n.t(this,priorities[level]));header.addView(add,new LinearLayout.LayoutParams(AppUi.dp(this,36),AppUi.dp(this,48)));card.addView(header);
                ScrollView scroll=new ScrollView(this);quadrantScrolls.add(scroll);scroll.setOnGenericMotionListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_SCROLL){scroll.scrollBy(0,Math.round(-e.getAxisValue(MotionEvent.AXIS_VSCROLL)*AppUi.dp(this,56)));return true;}return false;});LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);card.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
                for(JSONObject item:visibleItems)if(priority(item)==level){LinearLayout taskRow=new LinearLayout(this);taskRow.setGravity(Gravity.CENTER_VERTICAL);
                    CheckBox check=new CheckBox(this);AppUi.tintControl(check);check.setText(item.optString("title"));check.setTextSize(14);check.setChecked(item.optBoolean("done"));taskRow.addView(check,new LinearLayout.LayoutParams(0,-2,1));check.setOnCheckedChangeListener((v,on)->{try{item.put("done",on);item.put("completed_at",on?System.currentTimeMillis():0L);saveItem(item,false);}catch(Exception e){AppUi.error(this,e);}});
                    TextView edit=control(L10n.t(this,"编辑"),()->edit(item));edit.setTextSize(11);taskRow.addView(edit,new LinearLayout.LayoutParams(AppUi.dp(this,38),AppUi.dp(this,48)));list.addView(taskRow);}
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(column==0?0:AppUi.dp(this,4),AppUi.dp(this,4),column==0?AppUi.dp(this,4):0,AppUi.dp(this,4));line.addView(card,lp);
            }grid.addView(line,new LinearLayout.LayoutParams(-1,0,1));}body.addView(grid,new LinearLayout.LayoutParams(-1,-2));
        grid.post(()->{if(grid.getParent()!=null)grid.requestLayout();});
    }
    private LinkedHashSet<String> categoryNames(){
        LinkedHashSet<String> names=new LinkedHashSet<>();names.add("未分类");names.add("学习");names.add("作业");names.add("生活");names.addAll(getSharedPreferences("learning_todos",0).getStringSet("categories",new HashSet<>()));for(JSONObject item:items)names.add(item.optString("category","未分类"));return names;
    }
    private void categoryView(){
        LinkedHashSet<String> categories=categoryNames();if(categoryFilter!=null&&!categories.contains(categoryFilter))categoryFilter=null;
        HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);tabs.addView(row);
        categoryTab(row,null);for(String category:categories)categoryTab(row,category);
        TextView create=control("＋",()->createCategory());create.setContentDescription(AppUi.label(this,"新建分类","New category"));row.addView(create,new LinearLayout.LayoutParams(AppUi.dp(this,48),AppUi.dp(this,48)));body.addView(tabs);
        if(categoryFilter!=null)tabs.post(()->{View active=row.findViewWithTag("active_category");if(active!=null)tabs.smoothScrollTo(Math.max(0,active.getLeft()-AppUi.dp(this,30)),0);});
        for(String category:categories){if(categoryFilter!=null&&!category.equals(categoryFilter))continue;
            ArrayList<JSONObject> group=new ArrayList<>();for(JSONObject item:visibleItems)if(category.equals(item.optString("category","未分类")))group.add(item);
            if(categoryFilter==null&&group.isEmpty())continue;
            LinearLayout card=AppUi.card(this),heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);TextView name=AppUi.text(this,L10n.category(this,category)+" · "+group.size(),18,AppUi.tagColor(this,category),true);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView add=control("＋",()->newItem(3,category));add.setContentDescription(AppUi.label(this,"添加到","Add to ")+L10n.category(this,category));heading.addView(add,new LinearLayout.LayoutParams(AppUi.dp(this,44),AppUi.dp(this,48)));card.addView(heading);for(JSONObject item:group)itemRow(card,item);body.addView(card);
        }
        if(categoryFilter==null){TextView add=control(AppUi.label(this,"＋ 添加待办","＋ Add to-do"),()->newItem(3,"未分类"));body.addView(add);}
    }
    private void categoryTab(LinearLayout row,String category){
        boolean selected= Objects.equals(categoryFilter,category);TextView tab=control(category==null?L10n.t(this,"全部"):L10n.category(this,category),()->{categoryFilter=category;render(false);});tab.setPadding(AppUi.dp(this,16),0,AppUi.dp(this,16),0);tab.setTextColor(selected?AppUi.GREEN:AppUi.GRAY);if(selected){tab.setBackgroundColor(AppUi.SOFT);tab.setTag("active_category");}row.addView(tab,new LinearLayout.LayoutParams(-2,AppUi.dp(this,48)));
    }
    private void createCategory(){
        EditText input=new EditText(this);AppUi.tintControl(input);input.setSingleLine();input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(20)});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(L10n.t(this,"新建分类")).setView(input).setNegativeButton(L10n.t(this,"取消"),null).setPositiveButton(L10n.t(this,"添加"),null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String value=input.getText().toString().trim();if(value.isEmpty()){input.setError(L10n.t(this,"请填写分类名称"));return;}
            Set<String> names=new HashSet<>(categoryNames());names.add(value);getSharedPreferences("learning_todos",0).edit().putStringSet("categories",names).apply();categoryFilter=value;dialog.dismiss();render(false);}));dialog.show();
    }
    private void undated(){ArrayList<JSONObject> old=new ArrayList<>();for(JSONObject i:items)if(i.optString("date").isEmpty())old.add(i);if(old.isEmpty()){AppUi.toast(this,L10n.t(TodoActivity.this,"没有未安排日期的待办"));return;}String[] names=new String[old.size()];for(int i=0;i<old.size();i++)names[i]=old.get(i).optString("title");new AlertDialog.Builder(this).setTitle(L10n.t(TodoActivity.this,"未安排日期 · 点击分配日期")).setItems(names,(d,w)->edit(old.get(w))).setNegativeButton(AppUi.label(this,"关闭","Close"),null).show();}
    private class MiniMonth extends View {
        final String monthLabel;final int year,month;final Paint p=new Paint(3);final String[] keys;final int offset,days;
        MiniMonth(int y,int m){super(TodoActivity.this);year=y;month=m;Calendar first=CalendarDates.date(y,m,1);monthLabel=new java.text.SimpleDateFormat(L10n.english(getContext())?"MMM":"M月",L10n.locale(getContext())).format(CalendarDates.date(year,month,1).getTime());offset=(first.get(Calendar.DAY_OF_WEEK)+5)%7;days=first.getActualMaximum(Calendar.DATE);keys=new String[days+1];for(int day=1;day<=days;day++)keys[day]=CalendarDates.key(CalendarDates.date(y,m,day));setFocusable(true);setContentDescription(L10n.month(getContext(),first));}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float col=getWidth()/7f;p.setTextAlign(Paint.Align.LEFT);p.setColor(AppUi.INK);p.setTextSize(AppUi.dp(getContext(),20));c.drawText(monthLabel,4,AppUi.dp(getContext(),27),p);float step=AppUi.dp(getContext(),20);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(AppUi.dp(getContext(),10));for(int d=1;d<=days;d++){int pos=offset+d-1;float x=(pos%7+.5f)*col,y=AppUi.dp(getContext(),50)+(pos/7)*step;String key=keys[d];boolean today=key.equals(todayKey);if(today){p.setColor(AppUi.GREEN);c.drawCircle(x,y+(p.ascent()+p.descent())/2,Math.min(col*.47f,step*.46f),p);}p.setColor(today?(AppUi.dark?AppUi.BG:Color.WHITE):AppUi.INK);c.drawText(""+d,x,y,p);if(count(key)>0){p.setColor(AppUi.GREEN);c.drawCircle(x,y+AppUi.dp(getContext(),7),AppUi.dp(getContext(),1),p);}}}
    }
}

