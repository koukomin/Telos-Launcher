package rikka.rish;

import android.annotation.SuppressLint;
import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@SuppressLint("DiscouragedPrivateApi")
@SuppressWarnings("JavaReflectionMemberAccess")
class FileDescriptors {

    private static Method getInt;
    private static Method setInt;

    static {
        try {
            getInt = FileDescriptor.class.getDeclaredMethod("getInt$");
            getInt.setAccessible(true);
            setInt = FileDescriptor.class.getDeclaredMethod("setInt$", int.class);
            setInt.setAccessible(true);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @NonNull
    public static FileDescriptor fromFd(int fd) {
        FileDescriptor fileDescriptor = new FileDescriptor();
        try {
            setInt.invoke(fileDescriptor, fd);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
        }
        return fileDescriptor;
    }

    public static int getFd(FileDescriptor fileDescriptor) {
        try {
            //noinspection ConstantConditions
            return (int) getInt.invoke(fileDescriptor);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return -1;
        }
    }

    public static void closeSilently(@Nullable FileDescriptor fileDescriptor) {
        if (fileDescriptor == null) {
            return;
        }

        try {
            Os.close(fileDescriptor);
        } catch (ErrnoException ignored) {
        }
    }
}
