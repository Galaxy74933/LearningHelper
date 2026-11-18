package com.example.learninghelper;

import android.app.*;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import org.json.*;
import java.util.*;

/** Local draft: cancelling never mutates a saved task. */
final class TodoScheduleEditor {
    final Activity c;final JSONObject draft;final LinearLayout root;final Runnable changed;
    String mode,timeMode,completion,start,end,startTime,endTime;TreeSet<String> selected=new TreeSet<>();Calendar month;
    boolean awaitingEnd;final Map<String,Button> modeButtons=new HashMap<>();
    LinearLayout calendar;Button summary,completionButton,timeButton,startButton,endButton;
    TodoScheduleEditor(Activity c,JSONObject draft,LinearLayout root,Runnable changed){this.c=c;this.draft=draft;this.root=root;this.changed=changed;}
    String label(String zh,String en){return AppUi.label(c,zh,en);}
    Button button(LinearLayout parent,String text,Runnable action){Button b=AppUi.button(c,text,false);b.setAllCaps(false);b.setElevation(0);b.setStateListAnimator(null);b.setBackgroundTintList(null);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setPadding(AppUi.dp(c,12),0,AppUi.dp(c,12),0);b.setTextColor(AppUi.INK);GradientDrawable surface=new GradientDrawable();surface.setColor(AppUi.SURFACE);surface.setCornerRadius(AppUi.dp(c,14));b.setBackground(surface);parent.addView(b,new LinearLayout.LayoutParams(-1,AppUi.dp(c,52)));b.setOnClickListener(v->action.run());return b;}
    static void show(Activity c,JSONObject source,Runnable changed){
        try{JSONObject copy=new JSONObject(source.toString());LinearLayout box=new LinearLayout(c);box.setOrientation(1);box.setPadding(AppUi.dp(c,20),AppUi.dp(c,12),AppUi.dp(c,20),AppUi.dp(c,20));
            TodoScheduleEditor e=new TodoScheduleEditor(c,copy,box,changed);e.build();ScrollView scroll=new ScrollView(c);scroll.addView(box);
            AlertDialog dialog=AppUi.dialog(c).setTitle(AppUi.label(c,"日期与时间","Date & time")).setView(scroll).setNegativeButton(AppUi.label(c,"取消","Cancel"),null).setPositiveButton(AppUi.label(c,"确定","Done"),null).create();
            dialog.setOnShowListener(x->{dialog.getButton(-1).setTextColor(AppUi.GREEN);dialog.getButton(-2).setTextColor(AppUi.GREEN);dialog.getButton(-1).setOnClickListener(v->{try{e.write();if(TodoSchedule.dates(copy).isEmpty()&&e.completion.equals("daily")){AppUi.toast(c,e.label("每天分别完成需要选择日期","Select dates for daily completion"));return;}if(e.timeMode.equals("range")&&e.startTime.compareTo(e.endTime)>=0){AppUi.toast(c,e.label("结束时间须晚于开始时间","End time must be after start time"));return;}
                boolean wasDaily=TodoSchedule.daily(source),nowDaily=TodoSchedule.daily(copy);copy.put("completed_dates",source.optJSONObject("completed_dates"));
                if(wasDaily!=nowDaily){copy.put("done",false);copy.put("completed_at",0);copy.put("completed_dates",new JSONObject());}
                TodoSchedule.normalize(copy);
                Runnable apply=()->{try{Iterator<String> keys=copy.keys();while(keys.hasNext()){String key=keys.next();source.put(key,copy.get(key));}changed.run();dialog.dismiss();}catch(Exception ex){AppUi.error(c,ex);}};
                JSONObject previous=source.optJSONObject("completed_dates");boolean loses=wasDaily!=nowDaily&&(source.optBoolean("done")||(previous!=null&&previous.length()>0));if(previous!=null){Iterator<String> oldKeys=previous.keys();while(oldKeys.hasNext()){String old=oldKeys.next();if(previous.optLong(old)>0&&!TodoSchedule.on(copy,old))loses=true;}}
                if(loses)AppUi.dialog(c).setTitle(e.label("调整已完成的安排？","Change completed schedule?" )).setMessage(e.label("更改完成方式或移除已完成日期，会清除对应完成状态；专注记录保留。","Changing completion mode or removing completed dates clears the affected completion status. Focus records are kept.")).setNegativeButton(e.label("取消","Cancel"),null).setPositiveButton(e.label("继续","Continue"),(a,b)->apply.run()).show();else apply.run();
            }catch(Exception ex){AppUi.error(c,ex);}});Window w=dialog.getWindow();if(w!=null){w.setGravity(Gravity.BOTTOM);w.setLayout(-1,(int)(c.getResources().getDisplayMetrics().heightPixels*.85));GradientDrawable bg=new GradientDrawable();bg.setColor(AppUi.SURFACE);bg.setCornerRadius(AppUi.dp(c,24));w.setBackgroundDrawable(bg);}});dialog.show();
        }catch(Exception ex){AppUi.error(c,ex);}
    }
    void build(){
        mode=draft.optString("schedule_mode","single");timeMode=draft.optString("time_mode","all");completion=draft.optString("completion_mode","whole");start=draft.optString("date");end=draft.optString("end_date",start);selected.addAll(TodoSchedule.dates(draft));startTime=draft.optString("start_time","09:00");endTime=draft.optString("end_time","10:00");month=start.isEmpty()?CalendarDates.day(System.currentTimeMillis()):TodoSchedule.parse(start);month.set(Calendar.DATE,1);
        LinearLayout modes=new LinearLayout(c);root.addView(modes);String[] zh={"单日","连续多日","指定多日"},en={"Single","Range","Multiple"},values={"single","range","multiple"};
        for(int i=0;i<3;i++){final String value=values[i];Button b=AppUi.button(c,label(zh[i],en[i]),false);b.setBackgroundTintList(null);b.setStateListAnimator(null);b.setElevation(0);b.setAllCaps(false);b.setTextSize(13);b.setPadding(0,0,0,0);modes.addView(b,new LinearLayout.LayoutParams(0,-2,1));modeButtons.put(value,b);b.setOnClickListener(v->{if(mode.equals(value))return;awaitingEnd=false;mode=value;completion=value.equals("multiple")?"daily":"whole";if(start.isEmpty())start=CalendarDates.key(month);end=start;selected.clear();selected.add(start);refresh();});}
        summary=button(root,"",()->{});summary.setClickable(false);
        TextView hint=AppUi.text(c,label("连续多日：依次点开始和结束日期；指定多日：再次点击可取消选择。","Range: tap start and end. Multiple: tap again to deselect."),12,AppUi.GRAY,false);root.addView(hint);
        calendar=new LinearLayout(c);calendar.setOrientation(1);root.addView(calendar);button(root,label("清除日期","Clear dates"),()->{start="";end="";awaitingEnd=false;selected.clear();refresh();});
        completionButton=button(root,"",()->choices(label("完成方式","Completion mode"),new String[]{label("整项完成","Complete as one task"),label("每天分别完成","Complete each date separately")},completion.equals("daily")?1:0,w->{completion=w==0?"whole":"daily";refresh();}));
        timeButton=button(root,"",()->choices(label("时间","Time"),new String[]{label("全天","All day"),label("时间点","At a time"),label("时间段（每天）","Time window (each day)")},timeMode.equals("all")?0:timeMode.equals("point")?1:2,w->{timeMode=new String[]{"all","point","range"}[w];refresh();}));
        startButton=button(root,"",()->pickTime(false));endButton=button(root,"",()->pickTime(true));refresh();
    }
    private void choices(String title,String[] options,int selectedIndex,java.util.function.IntConsumer action){
        Dialog sheet=new Dialog(c);LinearLayout content=new LinearLayout(c);content.setOrientation(1);content.setPadding(AppUi.dp(c,20),AppUi.dp(c,16),AppUi.dp(c,20),AppUi.dp(c,24));
        TextView heading=AppUi.text(c,title,20,AppUi.INK,true);heading.setPadding(AppUi.dp(c,12),0,0,AppUi.dp(c,12));content.addView(heading);
        for(int i=0;i<options.length;i++){final int index=i;Button option=button(content,options[i]+(i==selectedIndex?"    ✓":""),()->{action.accept(index);sheet.dismiss();});if(i==selectedIndex){GradientDrawable bg=new GradientDrawable();bg.setColor(AppUi.SOFT);bg.setCornerRadius(AppUi.dp(c,14));option.setBackground(bg);option.setTextColor(AppUi.GREEN);}LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)option.getLayoutParams();lp.bottomMargin=AppUi.dp(c,6);}
        button(content,label("取消","Cancel"),sheet::dismiss);sheet.setContentView(content);Window w=sheet.getWindow();if(w!=null){w.setWindowAnimations(0);w.setBackgroundDrawable(roundSurface());w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setDimAmount(.25f);}sheet.show();if(w!=null){w.setGravity(Gravity.BOTTOM);w.setLayout(-1,-2);}
    }
    private GradientDrawable roundSurface(){GradientDrawable bg=new GradientDrawable();bg.setColor(AppUi.SURFACE);bg.setCornerRadius(AppUi.dp(c,24));return bg;}
    /** Adjacent cells share a continuous band; only the actual start/end have circles. */
    private class DateSelection extends android.graphics.drawable.Drawable {
        final boolean before,after,endpoint;final android.graphics.Paint paint=new android.graphics.Paint(3);
        DateSelection(boolean before,boolean after,boolean endpoint){this.before=before;this.after=after;this.endpoint=endpoint;}
        @Override public void draw(android.graphics.Canvas canvas){android.graphics.Rect b=getBounds();float x=b.exactCenterX(),y=b.exactCenterY(),radius=Math.min(b.width(),b.height())*.44f;paint.setColor(AppUi.SOFT);if(before)canvas.drawRect(b.left,y-radius,x,y+radius,paint);if(after)canvas.drawRect(x,y-radius,b.right,y+radius,paint);if(endpoint){paint.setColor(AppUi.GREEN);canvas.drawCircle(x,y,radius,paint);}}
        @Override public void setAlpha(int a){paint.setAlpha(a);}@Override public void setColorFilter(android.graphics.ColorFilter f){paint.setColorFilter(f);}@Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
    void pickTime(boolean last){String[] p=(last?endTime:startTime).split(":");new TimePickerDialog(c,(view,h,m)->{String time=String.format(Locale.US,"%02d:%02d",h,m);if(last)endTime=time;else startTime=time;refresh();},Integer.parseInt(p[0]),Integer.parseInt(p[1]),true).show();}
    void refresh(){
        for(Map.Entry<String,Button> entry:modeButtons.entrySet()){Button b=entry.getValue();boolean active=entry.getKey().equals(mode);b.setTextColor(active?android.graphics.Color.WHITE:AppUi.INK);GradientDrawable bg=new GradientDrawable();bg.setColor(active?AppUi.GREEN:AppUi.SURFACE);bg.setCornerRadius(AppUi.dp(c,16));b.setBackground(bg);b.setSelected(active);}

        summary.setText(mode.equals("multiple")?label("指定日期 · ","Selected dates · ")+selected.size():start.isEmpty()?label("未安排","Unscheduled"):start+(mode.equals("range")?" → "+end:""));
        completionButton.setText(label("完成方式：","Completion: ")+(completion.equals("daily")?label("每天分别完成","Each date"):label("整项完成","Whole task"))+" ▾");
        timeButton.setText(label("时间：","Time: ")+(timeMode.equals("all")?label("全天","All day"):timeMode.equals("point")?label("时间点","At a time"):label("时间段（每天）","Daily time window"))+" ▾");
        startButton.setVisibility(timeMode.equals("all")?View.GONE:View.VISIBLE);endButton.setVisibility(timeMode.equals("range")?View.VISIBLE:View.GONE);startButton.setText(label("开始：","Start: ")+startTime);endButton.setText(label("结束：","End: ")+endTime);
        calendar.removeAllViews();LinearLayout nav=new LinearLayout(c);calendar.addView(nav);TextView prev=AppUi.text(c,"‹",24,AppUi.GREEN,true),title=AppUi.text(c,L10n.month(c,month),17,AppUi.INK,true),next=AppUi.text(c,"›",24,AppUi.GREEN,true);for(TextView t:new TextView[]{prev,title,next}){t.setGravity(Gravity.CENTER);nav.addView(t,new LinearLayout.LayoutParams(0,AppUi.dp(c,44),1));}prev.setOnClickListener(v->{month.add(Calendar.MONTH,-1);refresh();});next.setOnClickListener(v->{month.add(Calendar.MONTH,1);refresh();});
        LinearLayout weekdays=new LinearLayout(c);calendar.addView(weekdays);String[] days=L10n.english(c)?new String[]{"M","T","W","T","F","S","S"}:new String[]{"一","二","三","四","五","六","日"};for(String day:days){TextView t=AppUi.text(c,day,12,AppUi.GRAY,false);t.setGravity(Gravity.CENTER);weekdays.addView(t,new LinearLayout.LayoutParams(0,AppUi.dp(c,28),1));}
        int offset=(month.get(Calendar.DAY_OF_WEEK)+5)%7,total=month.getActualMaximum(Calendar.DATE);for(int r=0;r<(offset+total+6)/7;r++){LinearLayout row=new LinearLayout(c);calendar.addView(row);for(int col=0;col<7;col++){int day=r*7+col-offset+1;TextView cell=AppUi.text(c,day>0&&day<=total?""+day:"",15,AppUi.INK,false);cell.setGravity(Gravity.CENTER);row.addView(cell,new LinearLayout.LayoutParams(0,AppUi.dp(c,40),1));if(day<1||day>total)continue;String key=CalendarDates.key(CalendarDates.date(month.get(Calendar.YEAR),month.get(Calendar.MONTH),day));boolean active=mode.equals("multiple")?selected.contains(key):!start.isEmpty()&&key.compareTo(start)>=0&&key.compareTo(mode.equals("range")?end:start)<=0;if(active){boolean edge=!mode.equals("range")||key.equals(start)||key.equals(end);cell.setBackground(new DateSelection(mode.equals("range")&&key.compareTo(start)>0,mode.equals("range")&&key.compareTo(end)<0,edge));cell.setTextColor(edge?android.graphics.Color.WHITE:AppUi.INK);}cell.setOnClickListener(v->{if(mode.equals("multiple")){if(!selected.remove(key))selected.add(key);start=selected.isEmpty()?"":selected.first();end=selected.isEmpty()?"":selected.last();}else if(mode.equals("single")){start=key;end=key;}else if(!awaitingEnd){start=key;end=key;awaitingEnd=true;}else {end=key;awaitingEnd=false;if(end.compareTo(start)<0){String swap=start;start=end;end=swap;}}refresh();});}}
    }
    void write() throws JSONException {draft.put("schedule_mode",mode);draft.put("date",start);draft.put("end_date",end);draft.put("schedule_dates",new JSONArray(selected));draft.put("completion_mode",completion);draft.put("time_mode",timeMode);draft.put("start_time",startTime);draft.put("end_time",endTime);}
}
