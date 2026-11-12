package com.example.learninghelper;

/** 与记录页共用读取逻辑，统计口径保持一致。 */
public class StatsActivity extends RecordsActivity {
    @Override protected boolean statisticsOnly() { return true; }
}
