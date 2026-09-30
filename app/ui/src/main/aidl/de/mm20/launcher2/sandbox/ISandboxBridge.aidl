// === TELOS_PENDING_REVIEW_START: sandbox_cloning_and_bridge ===
package de.mm20.launcher2.sandbox;

import android.os.ParcelFileDescriptor;

interface ISandboxBridge {
    void installApp(in ParcelFileDescriptor pfd, String packageName);
    void ping();
}
// === TELOS_PENDING_REVIEW_END: sandbox_cloning_and_bridge ===
