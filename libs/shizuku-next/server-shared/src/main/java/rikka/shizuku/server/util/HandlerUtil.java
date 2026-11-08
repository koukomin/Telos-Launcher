package rikka.shizuku.server.util;

import android.os.Handler;

import androidx.annotation.NonNull;

import java.util.Objects;

public class HandlerUtil {

    private static Handler mainHandler;

    public static void setMainHandler(Handler mainHandler) {
        HandlerUtil.mainHandler = mainHandler;
    }

    @NonNull
    public static Handler getMainHandler() {
        Objects.requireNonNull(mainHandler, "Please call setMainHandler first");
        return HandlerUtil.mainHandler;
    }
}
