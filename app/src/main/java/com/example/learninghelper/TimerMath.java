package com.example.learninghelper;

public final class TimerMath {
    private TimerMath() {}

    public static long remainingMillis(long started, int seconds, long now) {
        return Math.max(0L, seconds * 1000L - Math.max(0L, now - started));
    }

    public static int elapsedSeconds(long started, int seconds, long now) {
        return (int) Math.min(seconds, Math.max(0L, now - started) / 1000L);
    }
}
