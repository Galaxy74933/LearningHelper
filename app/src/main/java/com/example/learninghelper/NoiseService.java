package com.example.learninghelper;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.*;
import android.util.Log;

/** 只有实际播放声音时才运行前台服务；计时记录独立保存。 */
public class NoiseService extends Service {
    private static final int NOTICE = 2001;
    private static volatile long activeSessionId = -1;
    private static volatile int activeSound;
    private static volatile String playbackStatus = "声音已关闭";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private MediaSession mediaSession;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private StudyRepository.Session session;
    private int generation;
    private boolean receiverRegistered;

    public static void play(Context c, long id, int sound) {
        if (sound <= 0) { stopMatching(c, id); return; }
        c.startForegroundService(new Intent(c, NoiseService.class)
                .putExtra("session_id", id).putExtra("sound", sound));
    }
    public static void play(Context c, long id, Uri audioUri) {
        if (audioUri == null) { stopMatching(c, id); return; }
        c.startForegroundService(new Intent(c, NoiseService.class).putExtra("session_id", id)
                .putExtra("sound", 1).putExtra("audio_uri", audioUri.toString()));
    }
    public static void stopMatching(Context c, long id) {
        // 所有服务启停在主线程串行执行，避免旧记录的停止事件影响新记录。
        new Handler(Looper.getMainLooper()).post(() -> {
            if (activeSessionId == id) c.stopService(new Intent(c, NoiseService.class));
        });
    }
    public static String status(long id) { return activeSessionId == id ? playbackStatus : "声音已关闭"; }
    public static int sound(long id) { return activeSessionId == id ? activeSound : 0; }

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { stopSelf(); }
    };
    @Override public void onCreate() {
        super.onCreate();
        FocusCoordinator.channels(this);
        audioManager = getSystemService(AudioManager.class);
        mediaSession = new MediaSession(this, "FocusStudyAudio");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onStop() { stopSelf(); }
            @Override public void onPause() { stopSelf(); }
        });
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisy, filter);
        receiverRegistered = true;
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        long id = intent.getLongExtra("session_id", -1);
        int sound = intent.getIntExtra("sound", 0);
        String audioUri = intent.getStringExtra("audio_uri");
        if (id < 0 || sound < 1 || sound > 3) { stopSelf(); return START_NOT_STICKY; }
        final int token = ++generation;
        handler.removeCallbacks(tick);
        releasePlayer(); session = null;
        activeSessionId = id; activeSound = sound; playbackStatus = "声音正在加载…";
        Notification notification = notification(id, "正在准备声音");
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NOTICE, notification);
        } catch (RuntimeException e) { failed("无法开启后台播放，计时仍会继续", e); return START_NOT_STICKY; }
        StudyRepository.IO.execute(() -> {
            try {
                StudyRepository.Session s = StudyRepository.session(getApplicationContext(), id);
                handler.post(() -> {
                    if (token != generation) return;
                    if (s == null || s.status != TaskProvider.RUNNING || !s.owned() || s.remaining() == 0) { stopSelf(); return; }
                    session = s; prepare(sound, token, audioUri);
                });
            } catch (Exception e) {
                handler.post(() -> { if (token == generation) failed("读取专注失败，声音已停止", e); });
            }
        });
        return START_NOT_STICKY;
    }
    private Notification notification(long id, String title) {
        Intent open = new Intent(this, FocusActivity.class).putExtra("session_id", id)
                .setData(Uri.parse("learninghelper://playing/" + id))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, NoiseActionReceiver.class).setAction(NoiseActionReceiver.STOP)
                .setData(Uri.parse("learninghelper://stop/" + id)).putExtra("session_id", id);
        PendingIntent action = PendingIntent.getBroadcast(this, 0, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, FocusCoordinator.PLAYBACK)
                .setSmallIcon(R.drawable.ic_focus_notification).setContentTitle(title)
                .setContentText(AppUi.SOUNDS[activeSound] + " · 停止声音不会结束专注")
                .setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "停止声音", action).build())
                .setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0))
                .build();
    }
    private void prepare(int sound, int token, String audioUri) {
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener(change -> {
                    if (change != AudioManager.AUDIOFOCUS_GAIN) stopSelf();
                }, handler).build();
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            failed("其他应用正在使用音频，请稍后重新打开声音", null); return;
        }
        int[] ids = {0, R.raw.rain, R.raw.waves, R.raw.forest};
        try (AssetFileDescriptor asset = getResources().openRawResourceFd(ids[sound])) {
            player = new MediaPlayer();
            player.setAudioAttributes(attributes);
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            if (audioUri != null) { player.setDataSource(this, Uri.parse(audioUri)); }
            else player.setDataSource(asset.getFileDescriptor(), asset.getStartOffset(), asset.getLength());
            player.setLooping(true); player.setVolume(0.55f, 0.55f);
            player.setOnPreparedListener(p -> {
                if (token != generation || player != p) return;
                if (session == null || session.remaining() == 0) { handler.post(tick); return; }
                p.start(); playbackStatus = "正在播放：" + AppUi.SOUNDS[sound];
                mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_STOP | PlaybackState.ACTION_PAUSE)
                        .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build());
                mediaSession.setActive(true);
                getSystemService(NotificationManager.class).notify(NOTICE, notification(session.id, session.title));
                handler.post(tick);
            });
            player.setOnErrorListener((p, what, extra) -> { failed("声音播放失败，计时仍会继续", null); return true; });
            player.prepareAsync();
        } catch (Exception e) { failed("声音文件无法播放，计时仍会继续", e); }
    }
    private final Runnable tick = new Runnable() {
        private int seconds;
        @Override public void run() {
            if (session == null) return;
            StudyRepository.Session current = session;
            if (current.remaining() == 0) {
                releasePlayer();
                StudyRepository.IO.execute(() -> {
                    try { FocusCoordinator.finish(getApplicationContext(), current.id, TaskProvider.COMPLETED, true); }
                    catch (Exception e) {
                        Log.e("NoiseService", "记录结算失败", e);
                        FocusCoordinator.notice(getApplicationContext(), current, "记录暂未保存", "请打开应用重试保存。", true);
                        stopMatching(getApplicationContext(), current.id);
                    }
                });
                return;
            }
            if (++seconds % 5 == 0) StudyRepository.IO.execute(() -> {
                try { StudyRepository.updateStatus(getApplicationContext(), current.id, TaskProvider.RUNNING); }
                catch (Exception e) { Log.w("NoiseService", "保存进度失败", e); }
            });
            handler.postDelayed(this, 1000);
        }
    };
    private void failed(String message, Exception error) {
        if (error != null) Log.e("NoiseService", message, error);
        AppUi.toast(this, message); stopSelf();
    }
    private void releasePlayer() {
        if (player != null) { player.setOnPreparedListener(null); player.setOnErrorListener(null); player.release(); player = null; }
        if (focusRequest != null) { audioManager.abandonAudioFocusRequest(focusRequest); focusRequest = null; }
        if (mediaSession != null) mediaSession.setActive(false);
    }
    @Override public void onDestroy() {
        ++generation; handler.removeCallbacksAndMessages(null); releasePlayer();
        if (receiverRegistered) unregisterReceiver(noisy);
        mediaSession.release(); stopForeground(STOP_FOREGROUND_REMOVE);
        activeSessionId = -1; activeSound = 0; playbackStatus = "声音已关闭";
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
