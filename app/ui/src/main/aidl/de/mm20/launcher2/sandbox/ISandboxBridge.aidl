package de.mm20.launcher2.sandbox;

import android.os.ParcelFileDescriptor;

interface ISandboxBridge {
    void installApp(in ParcelFileDescriptor pfd, String packageName);
    void ping();
}
