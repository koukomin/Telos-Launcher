package de.mm20.launcher2.appmanagement.shizuku

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Only installed as the library's isolated test package; never replaces the user's launcher. */
class PairingTestActivity : Activity() {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            TelosShizukuHost.status.collect { status ->
                java.io.File(filesDir, "pairing-test-result.txt").writeText(
                    "phase=${status.pairing.phase} running=${status.running} uid=${status.uid} log=${status.lastLog}",
                )
            }
        }
        setContentView(Button(this).apply {
            text = "Start wireless pairing test"
            setOnClickListener {
                val missing = buildList {
                    if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) add("android.permission.POST_NOTIFICATIONS")
                    if (android.os.Build.VERSION.SDK_INT >= 37 && checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != android.content.pm.PackageManager.PERMISSION_GRANTED) add("android.permission.ACCESS_LOCAL_NETWORK")
                }
                if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 42)
                else TelosAdbPairingService.start(this@PairingTestActivity, intent.getBooleanExtra("automatic", true))
            }
        })
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 42 && results.isNotEmpty() && results.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            TelosAdbPairingService.start(this, intent.getBooleanExtra("automatic", true))
        }
    }
}

@RunWith(AndroidJUnit4::class)
class PairingDeviceTest {
    @Test fun nativePairingBindingsLoad() {
        System.loadLibrary("adb")
        val context = checkNotNull(PairingContext.create("test-password".toByteArray()))
        try { assertTrue(context.msg.isNotEmpty()) } finally { context.destroy() }
    }

    @Test fun tlsIdentitySurvivesReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertArrayEquals(TelosAdbKey(context).adbPublicKey, TelosAdbKey(context).adbPublicKey)
    }

    /** Opt-in on a physical device. User supplies the live code through the actual UI. */
    @Test fun interactivePairingStartsShellServer() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("interactivePairing") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.startActivitySync(Intent(context, PairingTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = android.os.SystemClock.elapsedRealtime() + 180_000L
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (TelosShizukuHost.status.value.pairing.phase == TelosShizukuHost.PairingPhase.Done) {
                assertTrue(TelosShizukuHost.status.value.running)
                assertEquals(2000, TelosShizukuHost.status.value.uid)
                return@runBlocking
            }
            if (TelosShizukuHost.status.value.pairing.phase == TelosShizukuHost.PairingPhase.Failed) {
                fail(TelosShizukuHost.status.value.lastLog)
            }
            kotlinx.coroutines.delay(250)
        }
        fail("Pairing did not reach a live shell-UID binder within the test window")
    }
}
