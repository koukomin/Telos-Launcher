package de.mm20.launcher2.freeze

import android.content.Context
import android.content.pm.PackageManager
import android.app.ActivityManager
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.Process
import android.os.Build
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Proxy

private data class RecentsSignal(val id: Int, val task: RecentsTask? = null)

internal data class RecentsTask(val id: Int, val packageName: String, val userId: Int)
internal fun removedLastTask(removed: RecentsTask, remaining: List<RecentsTask>, userId: Int): Boolean =
    removed.userId == userId && remaining.none { it.packageName == removed.packageName && it.userId == userId }

/** Privileged removal callbacks, never inferred from app switches or a truncated task-list diff. */
class RecentsFreezeMonitor internal constructor(
    private val context: Context,
    private val settings: FreezeSettings,
    private val autoFreeze: AutoFreezeController,
    private val freezeManager: FreezeManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()
    private val events = Channel<RecentsSignal>(256)
    private val refreshQueued = java.util.concurrent.atomic.AtomicBoolean(false)
    private val overflowed = java.util.concurrent.atomic.AtomicBoolean(false)
    private fun offer(signal: RecentsSignal) {
        if (signal.id == REFRESH && signal.task == null && !refreshQueued.compareAndSet(false, true)) return
        if (events.trySend(signal).isFailure) overflowed.set(true)
    }
    private val known = linkedMapOf<Int, RecentsTask>()
    private var root: RootRecentsConnection? = null
    private var service: Any? = null
    private var listener: Any? = null
    private var binder: IBinder? = null
    private val userId = Process.myUid() / 100000

    init {
        scope.launch {
            settings.freezeOnRecentsRemoval.collectLatest { enabled ->
                if (!enabled || Build.VERSION.SDK_INT < 29) { disconnect(); return@collectLatest }
                try {
                    freezeManager.refreshBackendState()
                    while (isActive) {
                        val shizukuReady = runCatching { Shizuku.pingBinder() &&
                            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
                        if (_connected.value && root?.alive == false) disconnect()
                        if (_connected.value && root == null && !shizukuReady) disconnect()
                        if (!_connected.value) {
                            if (shizukuReady) connect()
                            if (!_connected.value && freezeManager.activeBackend.value == FreezeBackendType.Root) connectRoot()
                        }
                        if (overflowed.getAndSet(false)) disconnect()
                        if (_connected.value) {
                            val event = withTimeoutOrNull(5000) { events.receive() }
                            if (event != null) try { processEvent(event) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (error: Exception) {
                                android.util.Log.w("RecentsFreeze", "Removal handling failed; reconnecting", error)
                                disconnect()
                            }
                        } else delay(5000)
                    }
                } finally { disconnect() }
            }
        }
    }

    private fun connect() {
        try {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/app/ITaskStackListener", "Landroid/app/IActivityTaskManager", "Landroid/app/TaskInfo", "Landroid/content/pm/ParceledListSlice", "Landroid/content/pm/BaseParceledListSlice")
            val remote = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity_task"))
            val stub = Class.forName("android.app.IActivityTaskManager\$Stub")
            val api = HiddenApiBypass.invoke(stub, null, "asInterface", remote) ?: return
            val listenerType = Class.forName("android.app.ITaskStackListener")
            val listenerStub = Class.forName("android.app.ITaskStackListener\$Stub")
            fun transaction(name: String) = listenerStub.getDeclaredField("TRANSACTION_$name").also { it.isAccessible = true }.getInt(null)
            val removed = transaction("onTaskRemoved")
            val changed = transaction("onTaskStackChanged")
            val created = transaction("onTaskCreated")
            val removing = runCatching { transaction("onTaskRemovalStarted") }.getOrNull()
            val callbackBinder = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    if (code == INTERFACE_TRANSACTION) return super.onTransact(code, data, reply, flags)
                    // Accept callbacks only from Android system_server, never another application.
                    if (Binder.getCallingUid() != SYSTEM_UID) return false
                    data.enforceInterface("android.app.ITaskStackListener")
                    when (code) {
                        removed -> offer(RecentsSignal(data.readInt()))
                        created -> offer(RecentsSignal(REFRESH))
                        removing -> {
                            val info = data.readTypedObject(ActivityManager.RunningTaskInfo.CREATOR)
                            val pkg = info?.baseIntent?.component?.packageName ?: info?.baseActivity?.packageName
                            if (info != null && pkg != null) offer(RecentsSignal(info.taskId, RecentsTask(info.taskId, pkg,
                                runCatching { info.javaClass.getField("userId").getInt(info) }.getOrDefault(-1))))
                        }
                        changed -> offer(RecentsSignal(REFRESH))
                    }
                    return true
                }
            }
            val callback = Proxy.newProxyInstance(listenerType.classLoader ?: javaClass.classLoader, arrayOf(listenerType)) { proxy, method, args ->
                when (method.name) {
                    "asBinder" -> callbackBinder
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    "toString" -> "TelosRecentsFreezeListener"
                    else -> null
                }
            }
            callbackBinder.attachInterface(callback as IInterface, "android.app.ITaskStackListener")
            service = api; listener = callback; binder = remote
            // Seed before registering. Unknown/racing task IDs are ignored rather than guessed.
            val initial = snapshot() ?: error("Recent task read unavailable")
            known.clear(); initial.forEach { known[it.id] = it }
            HiddenApiBypass.invoke(api.javaClass, api, "registerTaskStackListener", callback)
            _connected.value = true
        } catch (error: Exception) {
            android.util.Log.w("RecentsFreeze", "Task removal detection unavailable", error)
            disconnect()
        }
    }

    private fun connectRoot() {
        try {
            val connection = RootRecentsConnection(context, userId) { id, task -> offer(RecentsSignal(id, task)) }
            root = connection
            check(connection.awaitReady()) { "Root task listener unavailable" }
            val initial = connection.snapshot() ?: error("Root task snapshot unavailable")
            known.clear(); initial.forEach { known[it.id] = it }
            _connected.value = true
        } catch (error: Exception) {
            android.util.Log.w("RecentsFreeze", "Root task removal detection unavailable", error)
            disconnect()
        }
    }

    private fun disconnect() {
        root?.close(); root = null
        val api = service
        val callback = listener
        if (api != null && callback != null) runCatching {
            HiddenApiBypass.invoke(api.javaClass, api, "unregisterTaskStackListener", callback)
        }
        _connected.value = false
        service = null; listener = null; binder = null
        known.clear()
        refreshQueued.set(false)
        overflowed.set(false)
        while (events.tryReceive().isSuccess) { /* discard old-session callbacks */ }
    }

    private fun snapshot(): List<RecentsTask>? = runCatching {
        if (Build.VERSION.SDK_INT < 29) return null
        root?.let { return it.snapshot() }
        val api = service ?: return null
        val slice = HiddenApiBypass.invoke(api.javaClass, api, "getRecentTasks", 256, 0, userId) ?: return null
        val tasks = slice.javaClass.getMethod("getList").invoke(slice) as? List<*> ?: return null
        if (tasks.size >= 256) return null // A full page cannot prove another task is absent.
        tasks.map { value ->
            val task = value as? ActivityManager.RecentTaskInfo ?: return null
            val pkg = task.baseIntent?.component?.packageName ?: task.baseActivity?.packageName
                ?: task.baseIntent?.`package` ?: return null
            val owner = task.javaClass.getField("userId").getInt(task)
            RecentsTask(task.taskId, pkg, owner)
        }
    }.getOrNull()

    private suspend fun processEvent(signal: RecentsSignal) {
        val id = signal.id
        signal.task?.let { if (it.userId == userId && it.id >= 0) known[it.id] = it }
        if (id == REFRESH) {
            refreshQueued.set(false)
            val tasks = snapshot() ?: run { disconnect(); return }
            tasks.forEach { known[it.id] = it }
            while (known.size > 1024) known.remove(known.keys.first())
            return
        }
        val removed = known[id] ?: return
        delay(750) // Wait for the Recents animation; a removal-start event is not absence yet.
        if (!settings.freezeOnRecentsRemoval.first()) return
        var remaining = snapshot() ?: run { disconnect(); return }
        repeat(2) {
            if (remaining.any { it.id == id && it.userId == removed.userId }) {
                delay(500)
                remaining = snapshot() ?: run { disconnect(); return }
            }
        }
        remaining.forEach { known[it.id] = it }
        if (remaining.none { it.id == id && it.userId == removed.userId }) known.remove(id)
        if (removedLastTask(removed, remaining, userId)) {
            autoFreeze.freezeAfterRecentsRemoval(removed.packageName) {
                // Exclusion checks may take time. A reopened app must not be frozen using an old snapshot.
                val latest = snapshot() ?: return@freezeAfterRecentsRemoval false
                latest.none { it.packageName == removed.packageName && it.userId == userId }
            }
        }
    }

    companion object {
        private const val REFRESH = -1
        private const val SYSTEM_UID = 1000
    }
}
