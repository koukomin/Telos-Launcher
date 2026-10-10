package com.balsikandar.crashreporter.utils;

public class CrashReporterExceptionHandler implements Thread.UncaughtExceptionHandler {

    private Thread.UncaughtExceptionHandler exceptionHandler;

    public CrashReporterExceptionHandler() {
        this.exceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {

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
