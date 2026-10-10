package com.balsikandar.crashreporter;

import android.content.Context;
import android.content.Intent;

import com.balsikandar.crashreporter.utils.CrashReporterNotInitializedException;
import com.balsikandar.crashreporter.utils.CrashReporterExceptionHandler;
import com.balsikandar.crashreporter.utils.CrashUtil;

public class CrashReporter {

    private static Context applicationContext;

    private static String crashReportPath;

    private static boolean isNotificationEnabled = true;

    private CrashReporter() {
        // This class in not publicly instantiable
    }

    public static void initialize(Context context) {
        applicationContext = context;
        setUpExceptionHandler();
    }

    public static void initialize(Context context, String crashReportSavePath) {
        applicationContext = context;
        crashReportPath = crashReportSavePath;
        setUpExceptionHandler();
    }

    private static void setUpExceptionHandler() {
        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof CrashReporterExceptionHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new CrashReporterExceptionHandler());
        }
    }

    public static Context getContext() {
        if (applicationContext == null) {
            try {
                throw new CrashReporterNotInitializedException("Initialize CrashReporter : call CrashReporter.initialize(context, crashReportPath)");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return applicationContext;
    }

    public static String getCrashReportPath() {
        return crashReportPath;
    }

    public static boolean isNotificationEnabled() {
        return isNotificationEnabled;
    }

    //LOG Exception APIs
    public static void logException(Exception exception) {
        CrashUtil.logException(exception);
    }

    private static final String PREFS = "telos_crash_reporter";
    private static final String KEY_ENABLED = "enabled";
    private static volatile Boolean enabledCache = null;

    /**
     * Local on/off switch (default on). Mirrored in plain SharedPreferences so that the
     * uncaught exception handler can read it synchronously without DataStore.
     */
    public static boolean isEnabled() {
        Boolean cached = enabledCache;
        if (cached != null) return cached;
        boolean value = true;
        try {
            if (applicationContext != null) {
                value = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getBoolean(KEY_ENABLED, true);
            }
        } catch (Throwable ignored) {
        }
        enabledCache = value;
        return value;
    }

    public static void setEnabled(Context context, boolean enabled) {
        enabledCache = enabled;
        try {
            context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_ENABLED, enabled).apply();
        } catch (Throwable ignored) {
        }
    }

    public static void disableNotification() {
        isNotificationEnabled = false;
    }

}
