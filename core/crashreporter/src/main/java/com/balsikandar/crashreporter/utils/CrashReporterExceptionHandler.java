package com.balsikandar.crashreporter.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CrashReporterExceptionHandler implements Thread.UncaughtExceptionHandler {

    @Nullable
    private Thread.UncaughtExceptionHandler exceptionHandler;

    public CrashReporterExceptionHandler() {
        this.exceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {

        boolean enabled = true;
        try {
            enabled = com.balsikandar.crashreporter.CrashReporter.isEnabled();
        } catch (Throwable ignored) {
        }
        if (enabled) {
            CrashUtil.saveCrashReport(throwable);
        }

        exceptionHandler.uncaughtException(thread, throwable);
    }
}
