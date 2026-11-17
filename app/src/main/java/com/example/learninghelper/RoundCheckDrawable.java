package com.example.learninghelper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Shared circular indicator; CheckBox retains its native interaction and accessibility. */
final class RoundCheckDrawable extends Drawable {
    private final int size;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tick=new Path();
    private int alpha=255;
    RoundCheckDrawable(Context context){size=AppUi.dp(context,32);}
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
    @Override public void draw(Canvas canvas){
        boolean checked=false,enabled=false,active=false;
        for(int state:getState()){
            if(state==android.R.attr.state_checked)checked=true;
            if(state==android.R.attr.state_enabled)enabled=true;
            if(state==android.R.attr.state_pressed||state==android.R.attr.state_focused)active=true;
        }
        float unit=Math.min(getBounds().width(),getBounds().height())/32f;
        float x=getBounds().exactCenterX(),y=getBounds().exactCenterY();
        paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);
        paint.setColor(checked||active?AppUi.GREEN:AppUi.GRAY);
        paint.setAlpha(enabled?alpha:Math.round(alpha*.38f));
        if(!checked){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.6f*unit);}
        canvas.drawCircle(x,y,10*unit,paint);
        if(checked){
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*unit);
            paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(AppUi.dark?AppUi.BG:Color.WHITE);paint.setAlpha(enabled?alpha:Math.round(alpha*.38f));
            tick.reset();tick.moveTo(x-4.5f*unit,y);tick.lineTo(x-1.1f*unit,y+3.3f*unit);tick.lineTo(x+5*unit,y-3.6f*unit);
            canvas.drawPath(tick,paint);
        }
    }
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
