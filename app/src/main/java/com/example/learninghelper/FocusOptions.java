package com.example.learninghelper;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** 可横向滑动的分段选择条，选中底色平滑移动。 */
final class FocusOptions extends FrameLayout {
    interface Change { void select(int index); }
    private final View pill;
    private final LinearLayout labels;
    private final Change change;
    private int selected;
    private float downX,downY;
    private boolean swipe;
    FocusOptions(Context c,String[] titles,int initial,Change change){
        super(c);this.change=change;selected=initial;
        GradientDrawable track=new GradientDrawable();track.setColor(AppUi.SURFACE);track.setCornerRadius(AppUi.dp(c,25));setBackground(track);
        pill=new View(c);GradientDrawable fill=new GradientDrawable();fill.setColor(AppUi.SOFT);fill.setCornerRadius(AppUi.dp(c,23));pill.setBackground(fill);addView(pill);
        labels=new LinearLayout(c);addView(labels,new LayoutParams(-1,-1));
        for(int i=0;i<titles.length;i++){final int index=i;TextView text=AppUi.text(c,titles[i],14,AppUi.GRAY,true);text.setGravity(Gravity.CENTER);text.setFocusable(true);text.setOnClickListener(v->choose(index));labels.addView(text,new LinearLayout.LayoutParams(0,-1,1));}
    }
    void setSelection(int index){selected=index;update(false);}
    void choose(int index){if(!isEnabled())return;selected=index;update(true);change.select(index);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        super.onMeasure(widthSpec,heightSpec);
        int pad=AppUi.dp(getContext(),3);
        pill.measure(MeasureSpec.makeMeasureSpec(Math.max(1,getMeasuredWidth()/labels.getChildCount()-2*pad),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,getMeasuredHeight()-2*pad),MeasureSpec.EXACTLY));
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);update(false);}
    private void update(boolean animate){
        if(labels.getChildCount()==0)return;
        int pad=AppUi.dp(getContext(),3),width=getWidth()/labels.getChildCount();
        LayoutParams lp=new LayoutParams(Math.max(1,width-2*pad),Math.max(1,getHeight()-2*pad));lp.topMargin=pad;lp.leftMargin=pad;pill.setLayoutParams(lp);
        pill.animate().cancel();if(animate)pill.animate().translationX(selected*width).setDuration(230).start();else pill.setTranslationX(selected*width);
        for(int i=0;i<labels.getChildCount();i++){TextView t=(TextView)labels.getChildAt(i);t.setTextColor(i==selected?AppUi.GREEN:AppUi.GRAY);t.setSelected(i==selected);}
    }
    @Override public void setEnabled(boolean enabled){super.setEnabled(enabled);if(labels!=null)for(int i=0;i<labels.getChildCount();i++)labels.getChildAt(i).setEnabled(enabled);setAlpha(enabled?1f:.65f);}
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(!isEnabled())return true;
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();swipe=false;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.abs(e.getX()-downX)>AppUi.dp(getContext(),18)&&Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY)){
            if(!swipe){MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();}swipe=true;getParent().requestDisallowInterceptTouchEvent(true);return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP&&swipe){choose(Math.max(0,Math.min(labels.getChildCount()-1,selected+(e.getX()<downX?1:-1))));return true;}
        return super.dispatchTouchEvent(e);
    }
}
