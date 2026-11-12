package com.example.learninghelper;

import android.app.*;import android.content.*;import android.net.Uri;import android.os.*;import android.widget.*;import java.util.*;

public class FreeFocusActivity extends Activity {
    private AppUi.Page page; private StudyRepository.Session session; private TextView timer,status; private Handler h=new Handler(Looper.getMainLooper()); private long started; private boolean busy;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (session == null || timer == null) return;
            long sec = (SystemClock.elapsedRealtime() - session.startedElapsed) / 1000;
            timer.setText(String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60));
            h.postDelayed(this, 1000);
        }
    };
    @Override protected void onResume(){super.onResume(); render();}
    @Override protected void onPause(){h.removeCallbacks(tick);super.onPause();}
    private void render(){ page=AppUi.page(this,"自由专注","无需先创建任务，直接开始记录学习时间"); if(session==null) setup(); else running(); }
    private void setup(){
        Button audio=AppUi.button(this,"选择本地音频（可选）",false); page.content.addView(audio); audio.setOnClickListener(v->pickAudio());
        Button start=AppUi.button(this,"开始正计时",true); page.content.addView(start); start.setOnClickListener(v->{if(busy)return;busy=true;StudyRepository.run(this,()->StudyRepository.startFree(this,""),s->{busy=false;session=s;started=SystemClock.elapsedRealtime();render();String saved=getPreferences(0).getString("audio",null);if(saved!=null)NoiseService.play(this,s.id,Uri.parse(saved));},e->{busy=false;AppUi.error(this,e);});});
        Button back=AppUi.button(this,"返回任务首页",false);page.content.addView(back);back.setOnClickListener(v->finish());
    }
    private void running(){
        page.content.addView(AppUi.text(this,"自由专注进行中",22,AppUi.INK,true)); timer=AppUi.text(this,"00:00",42,AppUi.GREEN,true);page.content.addView(timer); status=AppUi.text(this,"正在记录实际专注时长",15,AppUi.GRAY,false);page.content.addView(status);
        Button end=AppUi.button(this,"结束并保存",true);page.content.addView(end);end.setOnClickListener(v->finishFocus());
        Button sound=AppUi.button(this,"环境声音",false);page.content.addView(sound);sound.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("选择声音").setSingleChoiceItems(AppUi.SOUNDS,NoiseService.sound(session.id),(d,w)->{NoiseService.play(this,session.id,w);d.dismiss();}).show()); h.post(tick);
    }
    private void finishFocus(){if(busy)return;busy=true;NoiseService.stopMatching(this,session.id);StudyRepository.run(this,()->StudyRepository.updateStatus(this,session.id,TaskProvider.EARLY_END),n->{busy=false;AppUi.toast(this,"自由专注已保存");finish();},e->{busy=false;AppUi.error(this,e);});}
    private void pickAudio(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,44);}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==44&&c==RESULT_OK&&d!=null){Uri u=d.getData();if(u!=null){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}getPreferences(0).edit().putString("audio",u.toString()).apply();AppUi.toast(this,"已加入音频：下次选择环境声音时可播放");}}}
}
