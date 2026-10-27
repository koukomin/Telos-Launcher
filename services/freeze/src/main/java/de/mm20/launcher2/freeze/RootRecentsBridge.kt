package de.mm20.launcher2.freeze

import android.app.ActivityManager
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.os.Parcel
import org.json.JSONArray
import org.json.JSONObject
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Proxy

/** Narrow app_process bridge: exact system removal callbacks and complete task snapshots, no polling/diffs. */
object RootRecentsBridge {
    @JvmStatic fun main(args: Array<String>) {
        Looper.prepareMainLooper()
        HiddenApiBypass.addHiddenApiExemptions("Landroid/app/", "Landroid/os/ServiceManager", "Landroid/content/pm/")
        val manager = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "activity_task") as IBinder
        val stub = Class.forName("android.app.IActivityTaskManager\$Stub")
        val api = HiddenApiBypass.invoke(stub, null, "asInterface", manager) ?: return
        val type = Class.forName("android.app.ITaskStackListener")
        val listenerStub = Class.forName("android.app.ITaskStackListener\$Stub")
        fun transaction(name: String) = listenerStub.getDeclaredField("TRANSACTION_$name").also { it.isAccessible = true }.getInt(null)
        val removed = transaction("onTaskRemoved")
        val changed = transaction("onTaskStackChanged")
        val created = transaction("onTaskCreated")
        val removing = runCatching { transaction("onTaskRemovalStarted") }.getOrNull()
        val output = Any()
        fun emit(value: String) { synchronized(output) { println(value); System.out.flush() } }
        val callbackBinder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == INTERFACE_TRANSACTION) return super.onTransact(code, data, reply, flags)
                if (Binder.getCallingUid() != 1000) return false
                data.enforceInterface("android.app.ITaskStackListener")
                when (code) {
                    removed -> emit("R ${data.readInt()}")
                    created, changed -> emit("C")
                    removing -> {
                        val info = if (data.readInt() != 0) ActivityManager.RunningTaskInfo.CREATOR.createFromParcel(data) else null
                        val pkg = info?.baseIntent?.component?.packageName ?: info?.baseActivity?.packageName
                        if (info != null && pkg != null) emit("R ${info.taskId} $pkg ${info.javaClass.getField("userId").getInt(info)}")
                    }
                }
                return true
            }
        }
        val callback = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, values ->
            when (method.name) {
                "asBinder" -> callbackBinder
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === values?.firstOrNull()
                "toString" -> "TelosRootRecentsListener"
                else -> null
            }
        }
        callbackBinder.attachInterface(callback as IInterface, "android.app.ITaskStackListener")
        HiddenApiBypass.invoke(api.javaClass, api, "registerTaskStackListener", callback)
        emit("READY")
        try {
            System.`in`.bufferedReader().useLines { lines -> lines.forEach { command ->
                if (command == "snapshot") {
                    val result = runCatching {
                        val slice = HiddenApiBypass.invoke(api.javaClass, api, "getRecentTasks", 256, 0, args[0].toInt()) ?: error("No tasks")
                        val tasks = slice.javaClass.getMethod("getList").invoke(slice) as List<*>
                        check(tasks.size < 256)
                        val array = JSONArray()
                        tasks.forEach { value ->
                            val task = value as ActivityManager.RecentTaskInfo
                            val pkg = task.baseIntent.component?.packageName ?: task.baseActivity?.packageName
                                ?: task.baseIntent.`package` ?: error("No package")
                            array.put(JSONObject().put("id", task.taskId).put("package", pkg)
                                .put("user", task.javaClass.getField("userId").getInt(task)))
                        }
                        array.toString()
                    }.getOrNull()
                    emit("S ${result ?: "null"}")
                }
            } }
        } finally {
            runCatching { HiddenApiBypass.invoke(api.javaClass, api, "unregisterTaskStackListener", callback) }
        }
        kotlin.system.exitProcess(0)
    }
}
