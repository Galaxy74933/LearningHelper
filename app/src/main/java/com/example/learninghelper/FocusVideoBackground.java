package com.example.learninghelper;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.*;
import android.widget.FrameLayout;

/** Muted, offline scenery. Audio remains exclusively owned by NoiseService. */
final class FocusVideoBackground implements TextureView.SurfaceTextureListener {
    private final Activity activity;
    private final TextureView texture;
    private MediaPlayer player;
    private final View veil;
    private Surface surface;
    private String scene="";
    private boolean active;
    FocusVideoBackground(Activity a,AppUi.Page page,FocusAtmosphere fallback){
        activity=a;
        FrameLayout layers=new FrameLayout(a);
        ((ViewGroup)page.root.getParent()).removeView(page.root);
        layers.setBackground(fallback);
        texture=new TextureView(a);texture.setOpaque(false);texture.setAlpha(0);texture.setSurfaceTextureListener(this);
        layers.addView(texture,new FrameLayout.LayoutParams(-1,-1));
        veil=new View(a);veil.setVisibility(View.GONE);veil.setBackgroundColor((AppUi.BG&0xffffff)|((AppUi.dark?145:125)<<24));
        layers.addView(veil,new FrameLayout.LayoutParams(-1,-1));
        page.root.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        layers.addView(page.root,new FrameLayout.LayoutParams(-1,-1));
        a.setContentView(layers);page.root.requestApplyInsets();
    }
    void setSound(int sound){
        String mode=AppUi.prefs(activity).getString("scene_mode","auto");
        String next=!mode.equals("auto")?"":sound==1?"rain":sound==2?"ocean":sound==3?"forest":"";
        if(scene.equals(next))return;
        scene=next;release();if(active)open();
    }
    void start(){active=true;if(player==null)open();}
    void stop(){active=false;release();}
    private void open(){
        if(!active||scene.isEmpty()||!texture.isAvailable()||player!=null)return;
        MediaPlayer next=new MediaPlayer();player=next;
        try(android.content.res.AssetFileDescriptor file=activity.getAssets().openFd("scenery/"+scene+".mp4")){
            surface=new Surface(texture.getSurfaceTexture());next.setSurface(surface);
            next.setDataSource(file.getFileDescriptor(),file.getStartOffset(),file.getLength());
            next.setVolume(0,0);next.setLooping(true);
            next.setOnVideoSizeChangedListener((p,w,h)->fit(w,h));
            next.setOnPreparedListener(p->{if(player==p&&active)p.start();});
            next.setOnInfoListener((p,what,extra)->{if(what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START&&player==p){veil.setVisibility(View.VISIBLE);texture.animate().alpha(1).setDuration(1000).start();}return false;});
            next.setOnErrorListener((p,what,extra)->{android.util.Log.w("FocusVideo", "Playback failed: "+scene+" ("+what+", "+extra+")");if(player==p)release();return true;});
            next.prepareAsync();
        }catch(Exception e){android.util.Log.w("FocusVideo", "Cannot open scenery: "+scene,e);release();}
    }
    private void fit(int w,int h){
        if(w<=0||h<=0||texture.getWidth()==0||texture.getHeight()==0)return;
        float scale=Math.max(texture.getWidth()/(float)w,texture.getHeight()/(float)h);
        Matrix m=new Matrix();m.setScale(w*scale/texture.getWidth(),h*scale/texture.getHeight(),texture.getWidth()/2f,texture.getHeight()/2f);texture.setTransform(m);
    }
    private void release(){
        texture.animate().cancel();texture.setAlpha(0);veil.setVisibility(View.GONE);
        if(player!=null){player.release();player=null;}
        if(surface!=null){surface.release();surface=null;}
    }
    public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){open();}
    public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){if(player!=null)fit(player.getVideoWidth(),player.getVideoHeight());}
    public boolean onSurfaceTextureDestroyed(SurfaceTexture s){release();return true;}
    public void onSurfaceTextureUpdated(SurfaceTexture s){}
}
