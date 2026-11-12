package com.example.learninghelper;

import android.app.*;import android.os.*;import android.widget.*;import java.text.*;import java.util.*;

public class MemoActivity extends Activity {
    private AppUi.Page page; private EditText input;
    @Override protected void onResume(){super.onResume();render();}
    private void render(){
        page=AppUi.page(this,"学习备忘","记录作业、计划和已经完成的事项");
        page.content.addView(AppUi.text(this,"写下今天要完成的学习内容",18,AppUi.INK,true));
        input=new EditText(this);input.setHint("例如：完成 Android 四大组件作业");input.setMinLines(3);page.content.addView(input);
        Button save=AppUi.button(this,"保存备忘",true);page.content.addView(save);save.setOnClickListener(v->{String text=input.getText().toString().trim();if(text.isEmpty()){input.setError("请输入备忘内容");return;}String old=getPreferences(0).getString("memos","");String line=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date())+"\t"+text;getPreferences(0).edit().putString("memos",line+"\n"+old).apply();input.setText("");render();});
        Button back=AppUi.button(this,"返回任务首页",false);page.content.addView(back);back.setOnClickListener(v->finish());
        page.content.addView(AppUi.text(this,"已保存的备忘",18,AppUi.INK,true));
        String all=getPreferences(0).getString("memos","");int count=0;for(String line:all.split("\\n")){if(line.trim().isEmpty())continue;count++;LinearLayout card=AppUi.card(this);String[] p=line.split("\\t",2);card.addView(AppUi.text(this,p.length>1?p[1]:line,17,AppUi.INK,true));if(p.length>1)card.addView(AppUi.text(this,p[0],13,AppUi.GRAY,false));Button del=AppUi.button(this,"删除",false);card.addView(del);final String target=line;del.setOnClickListener(v->{String next=getPreferences(0).getString("memos","").replace(target+"\n","").replace(target,"");getPreferences(0).edit().putString("memos",next).apply();render();});page.content.addView(card);}if(count==0)page.content.addView(AppUi.text(this,"还没有备忘，先写下一件要完成的事吧。",15,AppUi.GRAY,false));
    }
}
