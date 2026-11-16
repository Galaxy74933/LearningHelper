package com.example.learninghelper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** 最近七个本地自然日；任务用折线，专注用柱状图。 */
final class StatisticsChart extends View {
    private final long[] values;
    private final String[] dates;
    private final boolean focus;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    StatisticsChart(Context c,long[] values,String[] dates,boolean focus){
        super(c);this.values=values;this.dates=dates;this.focus=focus;
        StringBuilder description=new StringBuilder(focus?L10n.t(getContext(),"近七日专注时长："):L10n.t(getContext(),"近七日完成任务："));
        for(int i=0;i<7;i++)description.append(dates[i]).append(" ").append(values[i]).append(focus?L10n.t(getContext(),"秒；"):L10n.t(getContext(),"个；"));
        setContentDescription(description);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    private float dp(int n){return AppUi.dp(getContext(),n);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float left=dp(42),right=getWidth()-dp(16),top=dp(22),bottom=getHeight()-dp(30);
        float max=0;for(long value:values)max=Math.max(max,value);
        // 秒为最小单位，短时专注也有可见高度。
        float unit=focus?(max>3600?3600:max>180?60:1):1;
        float step=Math.max(1,(float)Math.ceil(max/unit/3));float ceiling=step*3*unit;
        paint.setTextSize(dp(10));paint.setStrokeWidth(dp(1));
        for(int i=0;i<=3;i++){
            float y=bottom-(bottom-top)*i/3;
            paint.setColor(AppUi.SOFT);canvas.drawLine(left,y,right,y,paint);
            paint.setColor(AppUi.GRAY);paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText((long)(step*i)+(focus?(unit==3600?L10n.t(getContext(),"时"):unit==60?L10n.t(getContext(),"分"):L10n.t(getContext(),"秒")):""),left-dp(8),y+dp(3),paint);
        }
        float spacing=(right-left)/6;Path line=new Path();paint.setTextAlign(Paint.Align.CENTER);
        for(int i=0;i<7;i++){
            float x=left+spacing*i,y=bottom-(bottom-top)*values[i]/ceiling;
            paint.setColor(AppUi.GRAY);String label=dates[i].substring(5).replace('-','/');canvas.drawText(label,x,bottom+dp(20),paint);
            paint.setColor(AppUi.GREEN);
            if(focus){if(values[i]>0){float half=Math.min(dp(9),spacing*.22f);canvas.drawRoundRect(x-half,y,x+half,bottom,half,half,paint);}}
            else {if(i==0)line.moveTo(x,y);else line.lineTo(x,y);canvas.drawCircle(x,y,dp(3),paint);}
        }
        if(!focus){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawPath(line,paint);paint.setStyle(Paint.Style.FILL);}
        if(max==0){paint.setColor(AppUi.GRAY);paint.setTextSize(dp(12));canvas.drawText(AppUi.label(getContext(),"近七日暂无"+(focus?"专注":"完成")+"记录",focus?"No focus records in the last 7 days":"No completed tasks in the last 7 days"),(left+right)/2,top+dp(28),paint);}
    }
}
