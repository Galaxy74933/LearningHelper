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
    private final ArrayList<JSONObject> items=new ArrayList<>();
    private Calendar selected=CalendarDates.day(System.currentTimeMillis());
    private int mode=1; // 0 年，1 连续月历，2 当天
    private LinearLayout body,toolbar;
    private boolean readable=true,animating;
    private int anchorYear;
    private ScrollView agendaScroll;
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e){
        if(mode==2 && agendaScroll!=null && e.getActionMasked()==MotionEvent.ACTION_SCROLL){float axis=e.getAxisValue(MotionEvent.AXIS_VSCROLL);if(axis==0)axis=e.getAxisValue(MotionEvent.AXIS_SCROLL);if(axis!=0){agendaScroll.scrollBy(0,Math.round(-axis*AppUi.dp(this,56)));return true;}}
        return super.dispatchGenericMotionEvent(e);
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null){mode=state.getInt("mode",1);selected=CalendarDates.day(state.getLong("day",System.currentTimeMillis()));}
        anchorYear=selected.get(Calendar.YEAR);
        try{JSONArray data=new JSONArray(getSharedPreferences("learning_todos",0).getString("items","[]"));for(int i=0;i<data.length();i++)items.add(data.getJSONObject(i));}
        catch(Exception e){readable=false;AppUi.error(this,e);}
        page=AppUi.page(this,"日历待办","");AppUi.navigation(this,page,1);
        toolbar=new LinearLayout(this);toolbar.setPadding(AppUi.dp(this,16),0,AppUi.dp(this,16),0);page.root.addView(toolbar,1);
        body=page.content;render(false);
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("mode",mode);out.putLong("day",selected.getTimeInMillis());}
    private void save(){JSONArray data=new JSONArray();for(JSONObject item:items)data.put(item);getSharedPreferences("learning_todos",0).edit().putString("items",data.toString()).apply();}
    private TextView control(String text,Runnable action){TextView v=AppUi.text(this,text,16,AppUi.GREEN,true);v.setGravity(Gravity.CENTER);v.setMinHeight(AppUi.dp(this,48));v.setFocusable(true);v.setOnClickListener(w->action.run());return v;}
    private void tools(){toolbar.removeAllViews();
        toolbar.addView(control(mode==0?"月历":"‹ 年",()->{mode=mode==0?1:0;render(false);}),new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(control("今天",()->{selected=CalendarDates.day(System.currentTimeMillis());anchorYear=selected.get(Calendar.YEAR);mode=2;render(false);}),new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(control("未安排",()->undated()),new LinearLayout.LayoutParams(0,-2,1));
        TextView add=control("＋",()->edit(null));add.setTextSize(28);add.setContentDescription("添加待办事项");add.setEnabled(readable);toolbar.addView(add,new LinearLayout.LayoutParams(AppUi.dp(this,48),-2));
    }
    private int count(String day){int n=0;for(JSONObject i:items)if(day.equals(i.optString("date")))n++;return n;}
    private void render(boolean animate){
        tools();body.removeAllViews();page.scroll.scrollTo(0,0);
        if(!readable){body.addView(AppUi.text(this,"待办读取失败，原数据已保留。请重新打开重试。",16,AppUi.GRAY,false));return;}
        if(mode==0)year();else if(mode==1)months();else agenda();
        if(animate){body.setAlpha(0f);body.animate().alpha(1f).setDuration(180).start();}
    }
    private void year(){
        for(int y=anchorYear-1;y<=anchorYear+1;y++){
            final int year=y;TextView title=AppUi.text(this,year+"年",32,AppUi.GREEN,true);body.addView(title);if(year==anchorYear)title.post(()->{if(mode==0)page.scroll.scrollTo(0,title.getTop());});
            for(int row=0;row<4;row++){LinearLayout line=new LinearLayout(this);for(int col=0;col<3;col++){final int month=row*3+col;MiniMonth v=new MiniMonth(year,month);v.setOnClickListener(w->{selected=CalendarDates.date(year,month,1);anchorYear=year;mode=1;render(true);});line.addView(v,new LinearLayout.LayoutParams(0,AppUi.dp(this,166),1));}body.addView(line);}AppUi.space(body,24);
        }
        body.addView(control("下一年 ↓",()->{anchorYear++;selected=CalendarDates.date(anchorYear,0,1);render(false);}));
        body.addView(control("↑ 上一年",()->{anchorYear--;selected=CalendarDates.date(anchorYear,0,1);render(false);}));
    }
    private void months(){
        Calendar first=CalendarDates.date(selected.get(Calendar.YEAR),selected.get(Calendar.MONTH),1);
        for(int delta=-6;delta<=12;delta++){
            Calendar month=CalendarDates.plus(first,Calendar.MONTH,delta);TextView title=AppUi.text(this,month.get(Calendar.YEAR)+"年 "+(month.get(Calendar.MONTH)+1)+"月",25,AppUi.GREEN,true);body.addView(title);
            if(delta==0)title.post(()->{if(mode==1)page.scroll.scrollTo(0,title.getTop());});
            body.addView(weekdays());Calendar start=CalendarDates.week(month);int days=month.getActualMaximum(Calendar.DATE),offset=(month.get(Calendar.DAY_OF_WEEK)+5)%7,rows=(days+offset+6)/7;
            for(int w=0;w<rows;w++)body.addView(weekRow(CalendarDates.plus(start,Calendar.DATE,w*7),month.get(Calendar.MONTH),false));
            AppUi.space(body,20);
        }
        body.addView(control("查看更多月份 ↓",()->{selected.add(Calendar.MONTH,12);render(false);}));
        body.addView(control("↑ 更早月份",()->{selected.add(Calendar.MONTH,-6);render(false);}));
    }
    private LinearLayout weekdays(){LinearLayout row=new LinearLayout(this);for(String name:new String[]{"一","二","三","四","五","六","日"}){TextView t=AppUi.text(this,name,13,AppUi.GRAY,false);t.setGravity(Gravity.CENTER);row.addView(t,new LinearLayout.LayoutParams(0,AppUi.dp(this,28),1));}return row;}
    private LinearLayout weekRow(Calendar start,int month,boolean compact){
        LinearLayout row=new LinearLayout(this);
        for(int i=0;i<7;i++){
            final Calendar date=CalendarDates.plus(start,Calendar.DATE,i);String key=CalendarDates.key(date);boolean active=mode==2&&key.equals(CalendarDates.key(selected));boolean today=CalendarDates.key(System.currentTimeMillis()).equals(key);
            LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);cell.setPadding(0,AppUi.dp(this,8),0,0);
            TextView number=AppUi.text(this,String.valueOf(date.get(Calendar.DATE)),19,today? (AppUi.dark?AppUi.BG:Color.WHITE):active?AppUi.GREEN:date.get(Calendar.MONTH)==month?AppUi.INK:AppUi.GRAY,today||active);
            number.setGravity(Gravity.CENTER);android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);shape.setColor(today?AppUi.GREEN:Color.TRANSPARENT);if(active&&!today)shape.setStroke(AppUi.dp(this,2),AppUi.GREEN);number.setBackground(shape);cell.addView(number,new LinearLayout.LayoutParams(AppUi.dp(this,34),AppUi.dp(this,34)));
            if(!compact&&count(key)>0){TextView dot=AppUi.text(this,count(key)+"项",11,AppUi.GREEN,false);dot.setGravity(Gravity.CENTER);cell.addView(dot);}
            cell.setContentDescription(key+"，"+count(key)+"项待办");cell.setFocusable(true);cell.setOnClickListener(v->{if(animating)return;if(mode==2&&key.equals(CalendarDates.key(selected))){expandMonth();}else if(mode==1){collapse(date,row);}else{selected=date;render(true);}});
            row.addView(cell,new LinearLayout.LayoutParams(0,AppUi.dp(this,compact?52:84),1));
        }return row;
    }
    private void collapse(Calendar date,LinearLayout row){
        selected=date;animating=true;
        android.animation.ValueAnimator animator=android.animation.ValueAnimator.ofInt(page.scroll.getScrollY(),row.getTop());animator.setDuration(240);animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(a->page.scroll.scrollTo(0,(Integer)a.getAnimatedValue()));animator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){animating=false;mode=2;render(false);}});animator.start();
    }
    private void expandMonth(){mode=1;render(true);}
    private void agenda(){
        body.addView(AppUi.text(this,selected.get(Calendar.YEAR)+"年 "+(selected.get(Calendar.MONTH)+1)+"月",24,AppUi.INK,true));body.addView(weekdays());Calendar start=CalendarDates.week(selected);body.addView(weekRow(start,selected.get(Calendar.MONTH),false));
        LinearLayout panel=AppUi.card(this);panel.addView(AppUi.text(this,CalendarDates.key(selected)+" · 待办事项",19,AppUi.INK,true));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);ScrollView inner=new ScrollView(this);agendaScroll=inner;inner.addView(list);panel.addView(inner,new LinearLayout.LayoutParams(-1,0,1));
        String day=CalendarDates.key(selected);int n=0;for(JSONObject item:items)if(day.equals(item.optString("date"))){n++;itemRow(list,item);}if(n==0)list.addView(AppUi.text(this,"这一天还没有事项，点击＋添加。",15,AppUi.GRAY,false));
        panel.addView(control("＋ 添加当天事项",()->edit(null)));
        int height=Math.max(AppUi.dp(this,200),page.scroll.getHeight()-AppUi.dp(this,300));body.addView(panel,new LinearLayout.LayoutParams(-1,height));
        panel.setTranslationY(AppUi.dp(this,20));panel.setAlpha(0);panel.animate().translationY(0).alpha(1).setDuration(240).start();
        body.addView(weekRow(CalendarDates.plus(start,Calendar.DATE,7),selected.get(Calendar.MONTH),true));
        LinearLayout arrows=new LinearLayout(this);arrows.addView(control("‹ 上一周",()->{selected.add(Calendar.DATE,-7);render(true);}),new LinearLayout.LayoutParams(0,-2,1));arrows.addView(control("下一周 ›",()->{selected.add(Calendar.DATE,7);render(true);}),new LinearLayout.LayoutParams(0,-2,1));body.addView(arrows);
        // 当天列表自己滚动，使顶部日期行和底部下一周始终可见。
        inner.setOnGenericMotionListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_SCROLL){inner.scrollBy(0,Math.round(-event.getAxisValue(MotionEvent.AXIS_VSCROLL)*AppUi.dp(this,56)));return true;}return false;});
    }
    private void itemRow(LinearLayout list,JSONObject item){
        LinearLayout row=AppUi.card(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox done=new CheckBox(this);AppUi.tintControl(done);done.setText(item.optString("title"));done.setTextColor(AppUi.INK);done.setChecked(item.optBoolean("done"));row.addView(done,new LinearLayout.LayoutParams(0,-2,1));
        done.setOnCheckedChangeListener((v,on)->{try{item.put("done",on);save();}catch(Exception e){AppUi.error(this,e);}});
        TextView more=control("···",()->new AlertDialog.Builder(this).setItems(new String[]{"编辑 / 调整日期","删除"},(d,w)->{if(w==0)edit(item);else new AlertDialog.Builder(this).setTitle("删除这条待办？").setMessage(item.optString("title")).setNegativeButton("取消",null).setPositiveButton("删除",(x,y)->{items.remove(item);save();render(false);}).show();}).show());row.addView(more,new LinearLayout.LayoutParams(AppUi.dp(this,48),-2));list.addView(row);
    }
    private void edit(JSONObject existing){
        if(!readable)return;LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(AppUi.dp(this,20),0,AppUi.dp(this,20),0);
        EditText title=new EditText(this);AppUi.tintControl(title);title.setHint("准备做什么？");title.setText(existing==null?"":existing.optString("title"));form.addView(title);
        final Calendar date=(Calendar)selected.clone();if(existing!=null&&!existing.optString("date").isEmpty()){try{String[] parts=existing.getString("date").split("-");date.setTimeInMillis(CalendarDates.date(Integer.parseInt(parts[0]),Integer.parseInt(parts[1])-1,Integer.parseInt(parts[2])).getTimeInMillis());}catch(Exception ignored){}}
        Button pick=AppUi.button(this,CalendarDates.key(date),false);form.addView(pick);pick.setOnClickListener(v->new DatePickerDialog(this,(view,y,m,d)->{date.setTimeInMillis(CalendarDates.date(y,m,d).getTimeInMillis());pick.setText(CalendarDates.key(date));},date.get(Calendar.YEAR),date.get(Calendar.MONTH),date.get(Calendar.DATE)).show());
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?"添加待办":"编辑待办").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{String value=title.getText().toString().trim();if(value.isEmpty()){title.setError("请填写事项");return;}try{JSONObject item=existing==null?new JSONObject():existing;if(existing==null){item.put("id",UUID.randomUUID().toString());item.put("done",false);}item.put("title",value);item.put("date",CalendarDates.key(date));if(existing==null)items.add(0,item);save();selected=(Calendar)date.clone();mode=2;dialog.dismiss();render(false);}catch(Exception e){AppUi.error(this,e);}}));dialog.show();
    }
    private void undated(){ArrayList<JSONObject> old=new ArrayList<>();for(JSONObject i:items)if(i.optString("date").isEmpty())old.add(i);if(old.isEmpty()){AppUi.toast(this,"没有未安排日期的待办");return;}String[] names=new String[old.size()];for(int i=0;i<old.size();i++)names[i]=old.get(i).optString("title");new AlertDialog.Builder(this).setTitle("未安排日期 · 点击分配日期").setItems(names,(d,w)->edit(old.get(w))).setNegativeButton("关闭",null).show();}
    private class MiniMonth extends View {
        final int year,month;final Paint p=new Paint(3);
        MiniMonth(int y,int m){super(TodoActivity.this);year=y;month=m;setFocusable(true);setContentDescription(y+"年"+(m+1)+"月");}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float col=getWidth()/7f;p.setTextAlign(Paint.Align.LEFT);p.setColor(AppUi.INK);p.setTextSize(AppUi.dp(getContext(),20));c.drawText((month+1)+"月",4,AppUi.dp(getContext(),27),p);Calendar first=CalendarDates.date(year,month,1);int offset=(first.get(Calendar.DAY_OF_WEEK)+5)%7;float step=AppUi.dp(getContext(),20);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(AppUi.dp(getContext(),10));for(int d=1;d<=first.getActualMaximum(Calendar.DATE);d++){int pos=offset+d-1;float x=(pos%7+.5f)*col,y=AppUi.dp(getContext(),50)+(pos/7)*step;String key=CalendarDates.key(CalendarDates.date(year,month,d));boolean today=key.equals(CalendarDates.key(System.currentTimeMillis()));if(today){p.setColor(AppUi.GREEN);c.drawCircle(x,y+(p.ascent()+p.descent())/2,Math.min(col*.47f,step*.46f),p);}p.setColor(today?(AppUi.dark?AppUi.BG:Color.WHITE):AppUi.INK);c.drawText(""+d,x,y,p);if(count(key)>0){p.setColor(AppUi.GREEN);c.drawCircle(x,y+AppUi.dp(getContext(),7),AppUi.dp(getContext(),1),p);}}}
    }
}
