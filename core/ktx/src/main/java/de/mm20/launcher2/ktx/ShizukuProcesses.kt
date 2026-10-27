package de.mm20.launcher2.ktx

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import android.os.Handler
import android.os.ParcelFileDescriptor
import de.mm20.launcher2.ktx.shizuku.ICommandService
import de.mm20.launcher2.ktx.shizuku.ShizukuCommandService
import moe.shizuku.server.IRemoteProcess
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Supported Shizuku UserService binding; no reflection into its private/deprecated process factory. */
object ShizukuProcesses {
    @Volatile private var appContext: Context? = null
    private val monitor = Any()
    private class Binding {
        val ready = CountDownLatch(1)
        @Volatile var service: ICommandService? = null
    }
    private var binding: Binding? = null
    fun init(context: Context) {
        if (appContext != null) return
        synchronized(monitor) {
            if (appContext != null) return
            appContext = context.applicationContext
            Shizuku.addBinderDeadListener {
                synchronized(monitor) { binding?.ready?.countDown(); binding = null }
            }
        }
    }
    fun available(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun start(args: Array<String>, environment: Array<String>? = null, directory: String? = null): Process {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Privileged processes must start off Main" }
        check(args.isNotEmpty() && available())
        val context = requireNotNull(appContext) { "ShizukuProcesses is not initialized" }
        val current = synchronized(monitor) {
            binding?.takeIf { it.ready.count > 0 || it.service?.asBinder()?.pingBinder() == true } ?: Binding().also { state ->
                binding = state
                val serviceArgs = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShizukuCommandService::class.java.name))
                    .daemon(false).processNameSuffix("telos-command").version(1)
                    .debuggable(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
                        state.service = runCatching { ICommandService.Stub.asInterface(binder)?.takeIf { it.uid == 0 || it.uid == 2000 } }.getOrNull()
                        state.ready.countDown()
                    }
                    override fun onServiceDisconnected(name: ComponentName) { state.service = null; state.ready.countDown() }
                }
                Handler(Looper.getMainLooper()).post {
                    runCatching { Shizuku.bindUserService(serviceArgs, connection) }.onFailure { state.ready.countDown() }
                }
            }
        }
        check(current.ready.await(8, TimeUnit.SECONDS)) { "Shizuku UserService connection timed out" }
        val service = checkNotNull(current.service) { "Shizuku UserService unavailable" }
        val remote = IRemoteProcess.Stub.asInterface(service.start(args, environment, directory))
        return fromRemoteProcess(requireNotNull(remote))
    }

    /** Descriptor ownership boundary, also exercised with a fake binder by Android regression tests. */
    fun fromRemoteProcess(remote: IRemoteProcess): Process {
        return object : Process() {
            private val stdout by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream) }
            private val stderr by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream) }
            private val stdin by lazy { ParcelFileDescriptor.AutoCloseOutputStream(remote.outputStream) }
            override fun getInputStream() = stdout
            override fun getErrorStream() = stderr
            override fun getOutputStream() = stdin
            override fun waitFor(): Int = remote.waitFor()
            override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = remote.waitForTimeout(timeout, unit.name)
            override fun exitValue(): Int = remote.exitValue()
            override fun isAlive(): Boolean = remote.alive()
            override fun destroy() { runCatching { remote.destroy() }; runCatching { stdin.close() }; runCatching { stdout.close() }; runCatching { stderr.close() } }
        }
    }
}
