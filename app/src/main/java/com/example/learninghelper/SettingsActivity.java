package com.example.learninghelper;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.*;
import java.util.Arrays;

public class SettingsActivity extends Activity {
    @Override protected void onResume() { super.onResume(); render(); }
    private void render() {
        AppUi.Page page = AppUi.page(this, "设置", "简单设置，专注当下"); AppUi.navigation(this, page, 3);
        page.content.addView(AppUi.text(this, "默认专注时长", 18, AppUi.INK, true));
        Spinner duration = spinner(Arrays.copyOf(AppUi.DURATION_LABELS, 4), Math.min(3, AppUi.prefs(this).getInt("duration_index", 1)));
        page.content.addView(duration);
        page.content.addView(AppUi.text(this, "默认声音", 18, AppUi.INK, true));
        Spinner sound = spinner(AppUi.SOUNDS, AppUi.prefs(this).getInt("sound_index", 0)); page.content.addView(sound);
        Switch demo = new Switch(this); demo.setText("开启 10 秒 / 30 秒演示选项"); demo.setChecked(AppUi.prefs(this).getBoolean("demo", false)); page.content.addView(demo);
        page.content.addView(AppUi.text(this, "开启后，在专注页面的时长列表中选择 10 秒或 30 秒。演示记录单独查看，不进入正式统计。", 14, AppUi.GRAY, false));
        Button save = AppUi.button(this, "保存设置", true); page.content.addView(save);
        save.setOnClickListener(v -> {
            AppUi.prefs(this).edit().putInt("duration_index", duration.getSelectedItemPosition())
                    .putInt("sound_index", sound.getSelectedItemPosition()).putBoolean("demo", demo.isChecked()).apply();
            AppUi.toast(this, "设置已保存，下次专注时生效");
        });
        LinearLayout card = AppUi.card(this); page.content.addView(card);
        card.addView(AppUi.text(this, FocusCoordinator.notificationsAllowed(this) ? "系统通知权限已开启" : "系统通知权限未开启", 18, AppUi.INK, true));
        card.addView(AppUi.text(this, "计时与记录不依赖通知权限。结束提醒还需要在系统中保持“专注结束提醒”通知类别开启。", 14, AppUi.GRAY, false));
        Button permission = AppUi.button(this, "管理通知提醒", false); card.addView(permission);
        permission.setOnClickListener(v -> enableNotifications(this));
        LinearLayout about = AppUi.card(this); page.content.addView(about);
        about.addView(AppUi.text(this, "关于声音与后台运行", 18, AppUi.INK, true));
        about.addView(AppUi.text(this, "内置雨声、海浪、林间声均为程序合成的环境声，不是真实环境录音，可离线循环播放。\n\n播放时会显示前台服务通知。停止声音不会结束计时。来电、其他音频抢占或耳机拔出后，声音可能停止；需要你重新开启。\n\n锁屏、省电模式下的到时提醒可能延迟。应用被强行停止或设备重启后，请重新打开应用处理异常中断记录。", 15, AppUi.GRAY, false));
    }
    private Spinner spinner(String[] values, int position) {
        Spinner s = new Spinner(this); ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(adapter);
        s.setSelection(Math.max(0, Math.min(position, values.length - 1)));
        s.setLayoutParams(new LinearLayout.LayoutParams(-1, AppUi.dp(this, 52))); return s;
    }
    public static void enableNotifications(Activity a) {
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !AppUi.prefs(a).getBoolean("notification_requested", false)) {
            AppUi.prefs(a).edit().putBoolean("notification_requested", true).apply();
            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        } else {
            a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
        }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results); if (code == 101) render();
    }
}

