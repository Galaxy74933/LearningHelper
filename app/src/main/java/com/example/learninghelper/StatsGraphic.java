package com.example.learninghelper;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

/** Lightweight charts with native text legends supplied by the dashboard. */
final class StatsGraphic extends View {
    static final int BARS=0,DONUT=1,HEAT=2,RATIO=3;
    final int type;final long[] values;final String[] labels;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    Calendar month;java.util.function.LongConsumer onDay;
    boolean compactHeat;
    StatsGraphic(Context c,int type,long[] values,String[] labels){super(c);this.type=type;this.values=values;this.labels=labels;setMinimumHeight(AppUi.dp(c,160));
        StringBuilder a=new StringBuilder();for(int i=0;i<values.length;i++)a.append(labels[i]).append(": ").append(AppUi.duration(c,values[i])).append("; ");setContentDescription(a);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    static int color(int index){
        // One hue, varied lightness: never rotate orange into green or purple into pink.
        float[] shades={1f,.70f,.43f,.85f,.56f,.32f};
        return mix(AppUi.SURFACE,AppUi.GREEN,shades[index%shades.length]);
    }
    private int highlighted=-1;
    private float[] emphasis;
    private android.animation.ValueAnimator hoverAnimator;
    private android.animation.ValueAnimator revealAnimator;
    private float reveal=0f;
    private boolean revealStarted;
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(type==DONUT)getViewTreeObserver().addOnPreDrawListener(revealListener);}
    private final ViewTreeObserver.OnPreDrawListener revealListener=()->{
        Rect visible=new Rect();
        if(!revealStarted&&getGlobalVisibleRect(visible)&&visible.height()>=getHeight()/2){
            revealStarted=true;revealAnimator=android.animation.ValueAnimator.ofFloat(0f,1f);revealAnimator.setDuration(1100);
            revealAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            revealAnimator.addUpdateListener(animation->{reveal=(float)animation.getAnimatedValue();invalidate();});revealAnimator.start();
        }return true;
    };
    private float ringRadius(){return Math.min(getWidth()*.245f,getHeight()*.30f);}
    private long total(){long n=0;for(long v:values)n+=v;return n;}
    private void highlight(int index){
        if(highlighted==index)return;highlighted=index;
        if(hoverAnimator!=null)hoverAnimator.cancel();
        if(emphasis==null)emphasis=new float[values.length];
        final float[] from=emphasis.clone();hoverAnimator=android.animation.ValueAnimator.ofFloat(0,1);
        hoverAnimator.setDuration(160);hoverAnimator.addUpdateListener(anim->{float f=(float)anim.getAnimatedValue();for(int i=0;i<emphasis.length;i++)emphasis[i]=from[i]+((i==highlighted?1:0)-from[i])*f;invalidate();});hoverAnimator.start();
    }
    private int segmentAt(float x,float y){
        float dx=x-getWidth()/2f,dy=y-getHeight()/2f,dist=(float)Math.hypot(dx,dy),r=ringRadius();
        if(dist<r-dp(15)||dist>r+dp(24)||total()==0)return -1;
        double angle=(Math.toDegrees(Math.atan2(dy,dx))+450)%360;float at=0;
        for(int i=0;i<values.length;i++){at+=360f*values[i]/total();if(angle<at&&values[i]>0)return i;}return -1;
    }
    @Override public boolean onHoverEvent(MotionEvent event){
        if(type!=DONUT)return super.onHoverEvent(event);
        highlight(event.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT?-1:segmentAt(event.getX(),event.getY()));return true;
    }
    @Override protected void onDetachedFromWindow(){if(hoverAnimator!=null)hoverAnimator.cancel();if(revealAnimator!=null)revealAnimator.cancel();if(getViewTreeObserver().isAlive())getViewTreeObserver().removeOnPreDrawListener(revealListener);super.onDetachedFromWindow();}
    private void fitted(Canvas c,String value,float x,float y,float width,int size,int color,Paint.Align align){
        p.setTextSize(dp(size));p.setColor(color);p.setTextAlign(align);
        String t=android.text.TextUtils.ellipsize(value,new android.text.TextPaint(p),Math.max(dp(10),width),android.text.TextUtils.TruncateAt.END).toString();c.drawText(t,x,y,p);
    }
    private void donut(Canvas c){
        float w=getWidth(),h=getHeight(),cx=w/2,cy=h/2,r=ringRadius();long sum=total();float start=-90;
        float[] angles=new float[values.length];
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(23));p.setColor(AppUi.SOFT);c.drawCircle(cx,cy,r,p);
        for(int i=0;i<values.length;i++){
            float sweep=sum==0?0:360f*values[i]/sum,mid=start+sweep/2;angles[i]=mid;
            float grow=emphasis==null?0:emphasis[i],shift=dp(5)*grow,rad=r+dp(3)*grow;
            float dx=(float)Math.cos(Math.toRadians(mid))*shift,dy=(float)Math.sin(Math.toRadians(mid))*shift;
            p.setStrokeWidth(dp(23)+dp(4)*grow);p.setColor(color(i));
            float shown=Math.max(0,Math.min(sweep,360f*reveal-(start+90)));
            c.drawArc(new RectF(cx-rad+dx,cy-rad+dy,cx+rad+dx,cy+rad+dy),start,shown,false,p);start+=sweep;
        }
        p.setStyle(Paint.Style.FILL);
        String name=highlighted<0?AppUi.label(getContext(),"总时长","Total time"):labels[highlighted];
        long seconds=highlighted<0?sum:values[highlighted];
        fitted(c,name,cx,cy-dp(7),2*r-dp(30),13,AppUi.GRAY,Paint.Align.CENTER);
        fitted(c,AppUi.duration(getContext(),seconds),cx,cy+dp(15),2*r-dp(30),13,AppUi.INK,Paint.Align.CENTER);
        for(int side=0;side<2;side++){
            final boolean right=side==1;ArrayList<Integer> ids=new ArrayList<>();
            for(int i=0;i<angles.length;i++)if(values[i]>0&&((Math.cos(Math.toRadians(angles[i]))>=0)==right))ids.add(i);
            ids.sort((i,j)->Double.compare(Math.sin(Math.toRadians(angles[i])),Math.sin(Math.toRadians(angles[j]))));
            float gap=dp(47),first=cy-(ids.size()-1)*gap/2;
            for(int n=0;n<ids.size();n++){
                int i=ids.get(n);double angle=Math.toRadians(angles[i]);float x=cx+(float)Math.cos(angle)*(r+dp(15)),y=cy+(float)Math.sin(angle)*(r+dp(15));
                float ty=first+n*gap,edge=right?w*.81f:w*.19f,tx=right?w-dp(2):dp(2);
                p.setColor(color(i));p.setStrokeWidth(dp(1));c.drawLine(x,y,edge,ty-dp(6),p);
                c.drawLine(edge,ty-dp(6),right?w-dp(6):dp(6),ty-dp(6),p);
                Paint.Align align=right?Paint.Align.RIGHT:Paint.Align.LEFT;float labelWidth=w*.21f;
                fitted(c,labels[i],tx,ty-dp(13),labelWidth,11,AppUi.INK,align);
                fitted(c,String.format(L10n.locale(getContext()),"%.1f%%",sum==0?0:100.0*values[i]/sum),tx,ty+dp(8),labelWidth,11,AppUi.GRAY,align);
                fitted(c,shortTime(values[i]),tx,ty+dp(21),labelWidth,10,AppUi.GRAY,align);
            }
        }
    }
    float dp(float n){return n*getResources().getDisplayMetrics().density;}
    void text(Canvas c,String t,float x,float y,int size,int color){p.setColor(color);p.setTextSize(dp(size));p.setTextAlign(Paint.Align.CENTER);c.drawText(t,x,y,p);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();long sum=0,max=0;for(long v:values){sum+=v;max=Math.max(max,v);}
        if(type==HEAT){if(compactHeat)miniHeat(c,w,h);else heat(c,w,h);return;}
        if(type==DONUT){donut(c);return;}
        if(type==RATIO){float y=dp(8);p.setColor(AppUi.SOFT);c.drawRoundRect(0,y,w,y+dp(16),dp(8),dp(8),p);if(sum>0){float edge=w*values[0]/sum;p.setColor(color(0));c.drawRect(0,y,edge,y+dp(16),p);p.setColor(color(1));c.drawRect(edge,y,w,y+dp(16),p);}return;}
        float top=dp(35),bottom=h-dp(30),step=w/Math.max(1,values.length);p.setColor(AppUi.SOFT);p.setStrokeWidth(dp(1));c.drawLine(0,bottom,w,bottom,p);
        for(int i=0;i<values.length;i++){float x=step*(i+.5f),bar=max==0?0:(bottom-top)*values[i]/max;p.setColor(AppUi.GREEN);if(bar>0)c.drawRoundRect(x-step*.28f,bottom-bar,x+step*.28f,bottom,dp(4),dp(4),p);text(c,labels[i],x,h-dp(10),11,AppUi.GRAY);if(values[i]>0)text(c,shortTime(values[i]),x,bottom-bar-dp(7),10,AppUi.GRAY);}
        if(max==0)text(c,AppUi.label(getContext(),"暂无专注记录","No focus records"),w/2,h/2,14,AppUi.GRAY);
    }
    String shortTime(long s){boolean en=L10n.english(getContext());return s<60?s+(en?"s":"秒"):s<3600?s/60+(en?"m":"分"):String.format(Locale.US,"%.1f",s/3600f)+(en?"h":"时");}
    void heat(Canvas c,float w,float h){String[] weekdays=AppUi.label(getContext(),"一 二 三 四 五 六 日","M T W T F S S").split(" ");float cell=w/7,top=dp(26),rh=(h-top)/6;for(int i=0;i<7;i++)text(c,weekdays[i],cell*(i+.5f),dp(16),11,AppUi.GRAY);
        int offset=(month.get(Calendar.DAY_OF_WEEK)+5)%7;for(int i=0;i<values.length;i++){int slot=i+offset;float x=slot%7*cell,y=top+slot/7*rh;long v=values[i];float f=v==0?0:v<1800?.18f:v<3600?.35f:v<7200?.55f:.85f;int fill=mix(AppUi.SURFACE,AppUi.GREEN,f);int ink=(Color.red(fill)*.299+Color.green(fill)*.587+Color.blue(fill)*.114)>145?0xff17252a:0xffedf2f4;p.setColor(fill);c.drawRoundRect(x+dp(2),y+dp(2),x+cell-dp(2),y+rh-dp(2),dp(6),dp(6),p);text(c,""+(i+1),x+cell/2,y+rh*.40f,12,ink);if(v>0)text(c,shortTime(v),x+cell/2,y+rh*.77f,10,ink);}
    }
    private void miniHeat(Canvas canvas,float w,float h){
        float cell=w/7f,top=dp(16),step=(h-top)/6f;
        String[] weekdays=AppUi.label(getContext(),"一 二 三 四 五 六 日","M T W T F S S").split(" ");
        for(int i=0;i<7;i++)text(canvas,weekdays[i],cell*(i+.5f),dp(10),7,AppUi.GRAY);
        int offset=(month.get(Calendar.DAY_OF_WEEK)+5)%7;
        for(int i=0;i<values.length;i++){
            int slot=i+offset;float x=(slot%7+.5f)*cell,y=top+(slot/7+.5f)*step;long value=values[i];
            float shade=value==0?0:value<1800?.18f:value<3600?.35f:value<7200?.55f:.85f;
            int fill=mix(AppUi.SURFACE,AppUi.GREEN,shade);
            p.setColor(fill);p.setStyle(Paint.Style.FILL);canvas.drawCircle(x,y,Math.min(cell,step)*.47f,p);
            int ink=value==0?AppUi.INK:(Color.red(fill)*.299+Color.green(fill)*.587+Color.blue(fill)*.114)>145?0xff17252a:0xffedf2f4;
            p.setTextSize(dp(8));p.setTextAlign(Paint.Align.CENTER);p.setColor(ink);canvas.drawText(""+(i+1),x,y-(p.ascent()+p.descent())/2,p);
        }
    }
    static int mix(int a,int b,float f){return Color.rgb((int)(Color.red(a)*(1-f)+Color.red(b)*f),(int)(Color.green(a)*(1-f)+Color.green(b)*f),(int)(Color.blue(a)*(1-f)+Color.blue(b)*f));}
    @Override public boolean onTouchEvent(MotionEvent e){if(type==DONUT){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){highlight(segmentAt(e.getX(),e.getY()));return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP){performClick();highlight(-1);return true;}if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){highlight(-1);return true;}return true;}if(type!=HEAT||onDay==null)return super.onTouchEvent(e);if(e.getAction()==MotionEvent.ACTION_DOWN)return true;if(e.getAction()==MotionEvent.ACTION_UP){float top=dp(26);if(e.getY()<top)return true;int slot=(int)((e.getY()-top)/((getHeight()-top)/6))*7+(int)(e.getX()/(getWidth()/7f));int day=slot-(month.get(Calendar.DAY_OF_WEEK)+5)%7+1;if(day>=1&&day<=values.length){performClick();Calendar d=(Calendar)month.clone();d.set(Calendar.DAY_OF_MONTH,day);onDay.accept(d.getTimeInMillis());}return true;}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
}
