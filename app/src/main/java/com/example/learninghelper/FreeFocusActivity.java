package com.example.learninghelper;
/** 兼容旧入口，使用统一专注页。 */
public class FreeFocusActivity extends FocusActivity {
    @Override public void onCreate(android.os.Bundle state){getIntent().putExtra("free_mode",true);super.onCreate(state);}
}
