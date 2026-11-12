package com.example.learninghelper;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.text.InputFilter;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private AppUi.Page page;
    private int filter, loadVersion;
    private boolean busy;
    private static class Data {
        List<StudyRepository.Task> tasks;
        StudyRepository.Session running;
        long todaySeconds; int todayCount;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) filter = state.getInt("filter", 0);
        FocusCoordinator.channels(this);
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putInt("filter", filter); }
    @Override protected void onResume() { super.onResume(); refresh(); }
    private void refresh() {
        int version = ++loadVersion;
        page = AppUi.page(this, "专注学习助手", "先明确一个小目标，再开始一段专注");
        AppUi.navigation(this, page, 0);
        page.content.addView(AppUi.text(this, "正在读取任务…", 16, AppUi.GRAY, false));
        StudyRepository.run(this, () -> {
            Data d = new Data(); d.tasks = StudyRepository.tasks(this); d.running = StudyRepository.running(this);
            if (d.running != null && d.running.owned() && d.running.remaining() == 0) {
                FocusCoordinator.finish(this, d.running.id, TaskProvider.COMPLETED, true); d.running = null;
            }
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            for (StudyRepository.Session s : StudyRepository.history(this)) {
                if (!s.demo && s.status == TaskProvider.COMPLETED && today.equals(s.endedDay)) {
                    d.todaySeconds += s.actual; d.todayCount++;
                }
            }
            return d;
        }, d -> { if (version == loadVersion) render(d); }, e -> {
            if (version != loadVersion) return;
            page.content.removeAllViews(); page.content.addView(AppUi.text(this, "读取失败，请重试", 16, AppUi.INK, false));
            Button retry = AppUi.button(this, "重新加载", true); page.content.addView(retry); retry.setOnClickListener(v -> refresh());
            AppUi.error(this, e);
        });
    }
    private void render(Data d) {
        page.content.removeAllViews();
        LinearLayout summary = AppUi.card(this);
        summary.addView(AppUi.text(this, "今日完成 " + d.todayCount + " 次 · " + d.todaySeconds / 60 + " 分钟", 20, AppUi.GREEN, true));
        summary.addView(AppUi.text(this, "定时专注提前结束、异常中断不计入统计", 13, AppUi.GRAY, false));
        page.content.addView(summary);
        if (d.running != null) {
            Button active = AppUi.button(this, d.running.owned() ? "返回当前专注：" + d.running.title : "上次专注已中断，点击处理", true);
            page.content.addView(active);
            active.setOnClickListener(v -> startActivity(new Intent(this, FocusActivity.class).putExtra("session_id", d.running.id)));
        }
        LinearLayout taskHeading=new LinearLayout(this);taskHeading.setGravity(Gravity.CENTER_VERTICAL);
        TextView taskTitle=AppUi.text(this,"待完成任务",21,AppUi.INK,true);taskHeading.addView(taskTitle,new LinearLayout.LayoutParams(0,-2,1));
        Button add=taskAction("+ 新建任务",false,false);taskHeading.addView(add,new LinearLayout.LayoutParams(AppUi.dp(this,104),AppUi.dp(this,36)));add.setOnClickListener(v->{if(!busy)edit(null);});
        Button free = AppUi.button(this, "自由专注（正计时）", false); page.content.addView(free);
        free.setOnClickListener(v -> startActivity(new Intent(this, FreeFocusActivity.class)));
        Button memo = AppUi.button(this, "学习备忘", false); page.content.addView(memo);
        memo.setOnClickListener(v -> startActivity(new Intent(this, MemoActivity.class)));
        RadioGroup group = new RadioGroup(this); group.setOrientation(RadioGroup.HORIZONTAL);
        String[] labels = {"待完成", "已完成", "全部"};
        for (int i = 0; i < labels.length; i++) {
            RadioButton b = new RadioButton(this); b.setId(100 + i); b.setText(labels[i]); group.addView(b);
        }
        group.check(100 + filter); page.content.addView(group);
        group.setOnCheckedChangeListener((g, id) -> { filter = id - 100; render(d); });
        taskTitle.setText(filter==0?"待完成任务":filter==1?"已完成任务":"全部任务");page.content.addView(taskHeading);
        int count = 0;
        for (StudyRepository.Task t : d.tasks) {
            if (filter == 0 && t.done || filter == 1 && !t.done) continue;
            count++;
            LinearLayout card = AppUi.card(this);
            LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView circle=AppUi.text(this,t.done?"✓":"○",30,AppUi.GREEN,false);circle.setContentDescription(t.done?"撤销完成":"标为完成");titleRow.addView(circle,new LinearLayout.LayoutParams(AppUi.dp(this,34),-2));
            titleRow.addView(AppUi.text(this, t.title, 20, AppUi.INK, true),new LinearLayout.LayoutParams(0,-2,1));card.addView(titleRow);
            card.addView(AppUi.text(this, t.done ? "已完成" : "待完成", 13, AppUi.GREEN, false));
            if (!t.note.isEmpty()) card.addView(AppUi.text(this, t.note, 15, AppUi.GRAY, false));
            LinearLayout row = new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);AppUi.space(card,8);
            Button toggle = AppUi.button(this, t.done ? "撤销完成" : "标为完成", false);
            Button edit = taskAction("编辑",false,true), delete = taskAction("删除",false,true);
            row.addView(edit,new LinearLayout.LayoutParams(AppUi.dp(this,32),AppUi.dp(this,36)));
            row.addView(AppUi.text(this,"·",12,AppUi.GRAY,false));
            row.addView(delete,new LinearLayout.LayoutParams(AppUi.dp(this,32),AppUi.dp(this,36)));
            row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
            if (!t.done) { Button focus=taskAction("开始专注",true,false);row.addView(focus,new LinearLayout.LayoutParams(AppUi.dp(this,100),AppUi.dp(this,34)));focus.setOnClickListener(v->{if(!busy)startActivity(new Intent(this,FocusActivity.class).putExtra("task_id",t.id));}); }
            card.addView(row);
            toggle.setTextSize(12); edit.setTextSize(12); delete.setTextSize(12); toggle.setVisibility(View.GONE);
            boolean locked = d.running != null && d.running.taskId == t.id;
            circle.setEnabled(!locked);circle.setOnClickListener(v->{if(!busy)mutate(()->StudyRepository.done(this,t));});
            toggle.setEnabled(!locked); edit.setEnabled(!locked); delete.setEnabled(!locked);
            toggle.setOnClickListener(v -> mutate(() -> StudyRepository.done(this, t)));
            edit.setOnClickListener(v -> { if (!busy) edit(t); });
            delete.setOnClickListener(v -> {
                if (!busy) new AlertDialog.Builder(this).setTitle("删除任务？")
                        .setMessage("将删除“" + t.title + "”，已经保存的学习记录会保留。")
                        .setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> mutate(() -> StudyRepository.delete(this, t.id))).show();
            });
            page.content.addView(card);
        }
        if (count == 0) page.content.addView(AppUi.text(this, filter == 0 ? "这里还没有待完成任务。点击上方按钮添加一个吧。" : "这个分类暂时没有任务。", 16, AppUi.GRAY, false));
    }
    // Task-card actions use explicit backgrounds so the system theme cannot restore square buttons or shadows.
    private Button taskAction(String label, boolean primary, boolean textOnly) {
        Button button = new Button(this);
        button.setText(label);button.setAllCaps(false);button.setTextSize(textOnly?12:14);
        button.setTextColor(textOnly?AppUi.GRAY:primary?android.graphics.Color.WHITE:AppUi.INK);
        button.setTypeface(android.graphics.Typeface.DEFAULT,textOnly?android.graphics.Typeface.NORMAL:android.graphics.Typeface.BOLD);
        button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(0);button.setMinimumHeight(0);
        button.setPadding(0,0,0,0);button.setIncludeFontPadding(false);button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);button.setStateListAnimator(null);button.setElevation(0);button.setTranslationZ(0);
        button.setBackgroundTintList(null);
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
        shape.setColor(textOnly?android.graphics.Color.TRANSPARENT:primary?AppUi.GREEN:android.graphics.Color.WHITE);
        shape.setCornerRadius(AppUi.dp(this,10));
        if(!primary&&!textOnly)shape.setStroke(AppUi.dp(this,1),android.graphics.Color.rgb(221,229,232));
        android.graphics.drawable.GradientDrawable mask=new android.graphics.drawable.GradientDrawable();
        mask.setColor(android.graphics.Color.WHITE);mask.setCornerRadius(AppUi.dp(this,10));
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22257868),shape,mask));
        return button;
    }
    private interface Change { void run(); }
    private void mutate(Change change) {
        if (busy) return; busy = true;
        StudyRepository.run(this, () -> { change.run(); return true; }, ok -> { busy = false; refresh(); }, e -> { busy = false; AppUi.error(this, e); });
    }
    private void edit(StudyRepository.Task task) {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        int pad = AppUi.dp(this, 20); form.setPadding(pad, pad, pad, pad);
        EditText title = new EditText(this); title.setHint("任务名称（必填，最多 30 字）"); title.setSingleLine(true);
        title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(30)});
        EditText note = new EditText(this); note.setHint("备注（选填，最多 200 字）"); note.setMinLines(2); note.setMaxLines(5);
        note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        form.addView(title); form.addView(note);
        if (task != null) { title.setText(task.title); note.setText(task.note); }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(task == null ? "新建任务" : "编辑任务")
                .setView(form).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = title.getText().toString().trim();
            if (name.isEmpty()) { title.setError("请输入任务名称"); return; }
            String memo = note.getText().toString();
            if (busy) return; busy = true;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            StudyRepository.run(this, () -> { StudyRepository.saveTask(this, task == null ? null : task.id, name, memo); return true; }, ok -> {
                busy = false; dialog.dismiss(); refresh();
            }, e -> { busy = false; dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); AppUi.error(this, e); });
        }));
        dialog.show();
    }
}
