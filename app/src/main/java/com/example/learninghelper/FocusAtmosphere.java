package com.example.learninghelper;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import java.io.*;

/** Offline photographs with a slow, seamless pan and zoom. */
final class FocusAtmosphere extends Drawable {
    private final Context context;
    private final Paint paint=new Paint(3);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Bitmap image,previous;
    private String key="";
    private int generation;
    private long changed;
    private boolean active;
    private final Runnable frame=new Runnable(){public void run(){if(active){invalidateSelf();handler.postDelayed(this,33);}}};
    FocusAtmosphere(Context c){context=c.getApplicationContext();}
    void setSound(int sound){
        String mode=AppUi.prefs(context).getString("scene_mode","auto");
        String next=mode.equals("photo")?AppUi.prefs(context).getString("scene_photo",""):mode.equals("off")?"":sound==1?"rain":sound==2?"ocean":sound==3?"forest":"";
        if(next.equals(key))return;key=next;int request=++generation;
        StudyRepository.IO.execute(()->{
            Bitmap loaded=null;
            try{if(!next.isEmpty())try(InputStream in=next.startsWith("/")?new FileInputStream(next):context.getAssets().open("scenery/"+next+".jpg")){loaded=BitmapFactory.decodeStream(in);}}catch(Exception ignored){}
            final Bitmap result=loaded;
            handler.post(()->{if(request!=generation)return;previous=image;image=result;changed=SystemClock.uptimeMillis();invalidateSelf();});
        });
    }
    void start(){active=true;handler.removeCallbacks(frame);handler.post(frame);}
    void stop(){active=false;handler.removeCallbacks(frame);}
    @Override public void draw(Canvas c){
        c.drawColor(AppUi.BG);float blend=Math.min(1,(SystemClock.uptimeMillis()-changed)/1000f);
        drawPhoto(c,previous,1-blend);drawPhoto(c,image,blend);
        if(blend>=1)previous=null;
        paint.setColor(AppUi.BG);paint.setAlpha(AppUi.dark?175:185);c.drawRect(getBounds(),paint);paint.setAlpha(255);
    }
    private void drawPhoto(Canvas c,Bitmap b,float alpha){
        if(b==null||alpha<=0)return;Rect r=getBounds();float t=SystemClock.uptimeMillis()/1000f;
        float scale=Math.max(r.width()/(float)b.getWidth(),r.height()/(float)b.getHeight())*(1.06f+.025f*(float)Math.sin(t/18));
        float w=b.getWidth()*scale,h=b.getHeight()*scale;
        float x=r.left+(r.width()-w)*(.5f+.18f*(float)Math.sin(t/25));
        float y=r.top+(r.height()-h)*(.5f+.18f*(float)Math.cos(t/28));
        paint.setAlpha((int)(255*alpha));c.drawBitmap(b,null,new RectF(x,y,x+w,y+h),paint);paint.setAlpha(255);
    }
    static String importPhoto(Context c,android.net.Uri uri)throws Exception{
        File f=File.createTempFile("background-",".jpg",c.getFilesDir());
        try{
            Bitmap b=Build.VERSION.SDK_INT>=28?ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.getContentResolver(),uri),(decoder,info,source)->{
                int w=info.getSize().getWidth(),h=info.getSize().getHeight();float ratio=Math.min(1f,1600f/Math.max(w,h));decoder.setTargetSize(Math.max(1,(int)(w*ratio)),Math.max(1,(int)(h*ratio)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            }):decodeLegacy(c,uri);
            try(OutputStream out=new FileOutputStream(f)){if(!b.compress(Bitmap.CompressFormat.JPEG,88,out))throw new IOException("Image save failed");}finally{b.recycle();}
            String old=AppUi.prefs(c).getString("scene_photo","");
            if(!AppUi.prefs(c).edit().putString("scene_photo",f.getAbsolutePath()).putString("scene_mode","photo").commit())throw new IOException("Preferences save failed");
            if(!old.isEmpty()){File prior=new File(old);if(prior.getParentFile().equals(c.getFilesDir())&&!prior.equals(f))prior.delete();}
            return f.getAbsolutePath();
        }catch(Exception e){f.delete();throw e;}
    }
    private static Bitmap decodeLegacy(Context c,android.net.Uri uri)throws Exception{
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
        options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1600)options.inSampleSize*=2;options.inJustDecodeBounds=false;
        Bitmap b;try(InputStream in=c.getContentResolver().openInputStream(uri)){b=BitmapFactory.decodeStream(in,null,options);}if(b==null)throw new IOException("Unsupported image");
        try(InputStream in=c.getContentResolver().openInputStream(uri)){
            android.media.ExifInterface exif=new android.media.ExifInterface(in);int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);Matrix matrix=new Matrix();
            if(orientation==3||orientation==4)matrix.postRotate(180);else if(orientation==5||orientation==6)matrix.postRotate(90);else if(orientation==7||orientation==8)matrix.postRotate(270);
            if(orientation==2||orientation==4||orientation==5||orientation==7)matrix.postScale(-1,1);
            if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),matrix,true);if(rotated!=b)b.recycle();b=rotated;}
        }catch(IOException ignored){}return b;
    }
    @Override public void setAlpha(int a){}
    @Override public void setColorFilter(ColorFilter f){}
    @Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
