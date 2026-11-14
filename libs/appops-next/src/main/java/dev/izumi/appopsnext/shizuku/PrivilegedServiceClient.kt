package dev.izumi.appopsnext.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.system.Os
import android.util.Log
import dev.izumi.appopsnext.BuildConfig
import dev.izumi.appopsnext.appops.PrivilegedAppOpsGateway
import dev.izumi.appopsnext.appops.WatcherResponseCodec
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import dev.izumi.appopsnext.diagnostics.DiagnosticLogRepository
import dev.izumi.appopsnext.nativebackend.NativeDaemonBootstrapper
import dev.izumi.appopsnext.nativebackend.NativeDaemonGateway
import dev.izumi.appopsnext.shizuku.model.PrivilegedBackendType
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceInfo
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceFailureReason
import dev.izumi.appopsnext.shizuku.model.PrivilegedServiceState
import dev.izumi.appopsnext.appops.command.AppOpsCommands
import dev.izumi.appopsnext.history.AppOpsHistoryOutputExtractor
import dev.izumi.appopsnext.shizuku.service.AppOpsUserService
import de.mm20.launcher2.ktx.TelosShellEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class PrivilegedServiceClient(
    context: Context,
    private val diagnosticLog: DiagnosticLogRepository,
) : PrivilegedAppOpsGateway {
    private val appContext = context.applicationContext
    private val mutableState =
        MutableStateFlow<PrivilegedServiceState>(PrivilegedServiceState.Disconnected)
    val state: StateFlow<PrivilegedServiceState> = mutableState.asStateFlow()

    @Volatile
    private var service: IPrivilegedAppOpsService? = null
    @Volatile
    private var nativeGateway: NativeDaemonGateway? = null
    @Volatile
    private var shellBackend = false
    private var bound = false
    private var connectionTimeoutJob: Job? = null
    private var nativeConnectionJob: Job? = null
    private var connectionGeneration = 0
    private val connectionScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val nativeDaemonBootstrapper =
        NativeDaemonBootstrapper(context)

    private val userServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, AppOpsUserService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix(USER_SERVICE_PROCESS_SUFFIX)
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            connectionTimeoutJob?.cancel()
            diagnosticLog.info(
                source = LOG_SOURCE,
                message =
                    "onServiceConnected received; " +
                        "binderPresent=${binder != null}",
            )
            if (binder == null || !binder.pingBinder()) {
                service = null
                diagnosticLog.error(
                    source = LOG_SOURCE,
                    message = "UserService returned an empty or dead binder.",
                )
                mutableState.value = PrivilegedServiceState.Failure(
                    PrivilegedServiceFailureReason.EMPTY_BINDER,
                )
                return
            }

            runCatching {
                IPrivilegedAppOpsService.Stub.asInterface(binder).also { connectedService ->
                    val serviceInfo = PrivilegedServiceInfo(
                        uid = connectedService.uid,
                        pid = connectedService.pid,
                        apiLevel = connectedService.apiLevel,
                        backendType = PrivilegedBackendType.USER_SERVICE,
                    )
                    shellBackend = false
                    service = connectedService
                    mutableState.value = PrivilegedServiceState.Connected(
                        serviceInfo,
                    )
                    diagnosticLog.info(
                        source = LOG_SOURCE,
                        message =
                            "UserService connected. uid=${serviceInfo.uid}, " +
                                "pid=${serviceInfo.pid}, " +
                                "api=${serviceInfo.apiLevel}",
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "Unable to initialize UserService", error)
                diagnosticLog.error(
                    source = LOG_SOURCE,
                    message = "Unable to initialize UserService.",
                    error = error,
                )
                service = null
                mutableState.value = PrivilegedServiceState.Failure(
                    PrivilegedServiceFailureReason.INITIALIZATION_FAILED,
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            connectionTimeoutJob?.cancel()
            service = null
            bound = false
            mutableState.value = PrivilegedServiceState.Disconnected
            diagnosticLog.warning(
                source = LOG_SOURCE,
                message = "UserService disconnected.",
            )
        }
    }

    fun connect() {
        if (
            bound ||
            nativeGateway != null ||
            nativeConnectionJob?.isActive == true ||
            mutableState.value is PrivilegedServiceState.Connecting
        ) {
            diagnosticLog.info(
                source = LOG_SOURCE,
                message =
                    "Connection request skipped. bound=$bound, " +
                        "state=${mutableState.value::class.java.simpleName}",
            )
            return
        }

        val hasDaemonAsset = runCatching {
            appContext.assets.open("arm64-v8a/appopsnextd").close()
            true
        }.getOrDefault(false)
        if (!hasDaemonAsset) {
            diagnosticLog.info(
                source = NATIVE_LOG_SOURCE,
                message = "Native daemon asset missing; using UserService then shell.",
            )
            bindUserService()
            return
        }
        mutableState.value = PrivilegedServiceState.Connecting(
            PrivilegedBackendType.NATIVE_DAEMON,
        )
        val generation = ++connectionGeneration
        diagnosticLog.info(
            source = NATIVE_LOG_SOURCE,
            message =
                "Starting native daemon backend. api=${Build.VERSION.SDK_INT}, " +
                    "clientVersion=${BuildConfig.VERSION_CODE}",
        )
        nativeConnectionJob = connectionScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    NativeDaemonGateway(
                        nativeDaemonBootstrapper.launch(),
                    )
                }
            }
            if (generation != connectionGeneration) {
                result.getOrNull()?.close()
                return@launch
            }
            result.onSuccess { gateway ->
                nativeGateway = gateway
                val daemonInfo = gateway.info
                val serviceInfo = PrivilegedServiceInfo(
                    uid = daemonInfo.uid,
                    pid = daemonInfo.pid,
                    apiLevel = Build.VERSION.SDK_INT,
                    backendType = PrivilegedBackendType.NATIVE_DAEMON,
                )
                mutableState.value = PrivilegedServiceState.Connected(
                    serviceInfo,
                )
                Log.i(
                    NATIVE_LOG_SOURCE,
                    "Native daemon connected. " +
                        "protocol=${daemonInfo.protocolVersion}, " +
                        "uid=${daemonInfo.uid}, pid=${daemonInfo.pid}",
                )
                diagnosticLog.info(
                    source = NATIVE_LOG_SOURCE,
                    message =
                        "Native daemon connected. " +
                            "protocol=${daemonInfo.protocolVersion}, " +
                            "uid=${daemonInfo.uid}, pid=${daemonInfo.pid}",
                )
            }.onFailure { error ->
                Log.w(TAG, "Native daemon backend unavailable", error)
                diagnosticLog.warning(
                    source = NATIVE_LOG_SOURCE,
                    message =
                        "Native daemon startup failed; falling back to " +
                            "UserService. ${error::class.java.simpleName}: " +
                            error.message.orEmpty(),
                )
                bindUserService()
            }
        }
    }

    private fun bindUserService() {
        mutableState.value = PrivilegedServiceState.Connecting(
            PrivilegedBackendType.USER_SERVICE,
        )
        diagnosticLog.info(
            source = LOG_SOURCE,
            message =
                "Binding UserService fallback. api=${Build.VERSION.SDK_INT}, " +
                    "clientVersion=${BuildConfig.VERSION_CODE}",
        )
        runCatching {
            Handler(Looper.getMainLooper()).post {
                runCatching {
                    Shizuku.bindUserService(userServiceArgs, connection)
                    bound = true
                    diagnosticLog.info(
                        source = LOG_SOURCE,
                        message =
                            "bindUserService returned; waiting for connection callback.",
                    )
                    startConnectionTimeout()
                }.onFailure { error ->
                    Log.e(TAG, "Unable to bind UserService", error)
                    bound = false
                    activateShellBackend()
                }
            }
        }.onFailure { error ->
            Log.e(TAG, "Unable to bind UserService", error)
            diagnosticLog.error(
                source = LOG_SOURCE,
                message = "bindUserService threw an exception.",
                error = error,
            )
            bound = false
            activateShellBackend()
        }
    }

    fun retry() {
        diagnosticLog.info(
            source = NATIVE_LOG_SOURCE,
            message = "Manual privileged backend retry requested.",
        )
        disconnect()
        connect()
    }

    fun disconnect() {
        connectionGeneration++
        nativeConnectionJob?.cancel()
        nativeConnectionJob = null
        connectionTimeoutJob?.cancel()
        nativeGateway?.close()
        nativeGateway = null
        if (bound) {
            runCatching {
                Shizuku.unbindUserService(userServiceArgs, connection, false)
            }.onFailure { error ->
                Log.w(TAG, "Unable to unbind UserService", error)
                diagnosticLog.warning(
                    source = LOG_SOURCE,
                    message =
                        "Unable to unbind UserService: " +
                            "${error::class.java.simpleName}: " +
                            error.message.orEmpty(),
                )
            }
        }

        if (
            bound ||
            service != null ||
            mutableState.value !is PrivilegedServiceState.Disconnected
        ) {
            diagnosticLog.info(
                source = NATIVE_LOG_SOURCE,
                message = "Privileged backend connection state cleared.",
            )
        }
        service = null
        bound = false
        shellBackend = false
        mutableState.value = PrivilegedServiceState.Disconnected
    }

    private fun startConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = connectionScope.launch {
            delay(CONNECTION_TIMEOUT_MILLIS)
            if (
                (mutableState.value as? PrivilegedServiceState.Connecting)
                    ?.backendType == PrivilegedBackendType.USER_SERVICE
            ) {
                diagnosticLog.error(
                    source = LOG_SOURCE,
                    message =
                        "UserService connection callback timed out after " +
                            "${CONNECTION_TIMEOUT_MILLIS / 1_000}s.",
                )
                activateShellBackend()
            }
        }
    }

    private fun activateShellBackend() {
        connectionScope.launch(Dispatchers.IO) {
            val ready = TelosShellEngine.isPrivilegedAvailable()
            if (!ready) {
                mutableState.value = PrivilegedServiceState.Failure(
                    PrivilegedServiceFailureReason.BIND_TIMED_OUT,
                )
                return@launch
            }
            shellBackend = true
            mutableState.value = PrivilegedServiceState.Connected(
                PrivilegedServiceInfo(
                    uid = runCatching { Os.getuid() }.getOrDefault(2000),
                    pid = 0,
                    apiLevel = Build.VERSION.SDK_INT,
                    backendType = PrivilegedBackendType.USER_SERVICE,
                ),
            )
            diagnosticLog.info(
                source = LOG_SOURCE,
                message = "Using privileged shell backend for AppOps commands.",
            )
        }
    }

    override suspend fun getPackageOps(packageName: String): ShellCommandResult =
        withContext(Dispatchers.IO) {
            readWithShellFallback(AppOpsCommands.getPackageOps(packageName)) {
                executeNative { it.getPackageOps(packageName) } ?: service?.takeUnless { shellBackend }?.getPackageOps(packageName)
            }
        }

    override suspend fun getPackageOp(
        packageName: String,
        operationName: String,
    ): ShellCommandResult =
        withContext(Dispatchers.IO) {
            readWithShellFallback(AppOpsCommands.getPackageOp(packageName, operationName)) {
                executeNative { it.getPackageOp(packageName, operationName) }
                    ?: service?.takeUnless { shellBackend }?.getPackageOp(packageName, operationName)
            }
        }

    override suspend fun getUidOps(uid: Int): ShellCommandResult =
        withContext(Dispatchers.IO) {
            readWithShellFallback(AppOpsCommands.getUidOps(uid)) {
                executeNative { it.getUidOps(uid) } ?: service?.takeUnless { shellBackend }?.getUidOps(uid)
            }
        }

    override suspend fun getUidStates(packageName: String): ShellCommandResult =
        withContext(Dispatchers.IO) {
            readWithShellFallback(AppOpsCommands.getUidStates(packageName)) {
                executeNative { it.getUidStates(packageName) } ?: service?.takeUnless { shellBackend }?.getUidStates(packageName)
            }
        }

    override suspend fun getOpStates(operationName: String): ShellCommandResult =
        withContext(Dispatchers.IO) {
            readWithShellFallback(AppOpsCommands.getOpStates(operationName)) {
                executeNative { it.getOpStates(operationName) } ?: service?.takeUnless { shellBackend }?.getOpStates(operationName)
            }
        }

    override suspend fun getWatchers(): ShellCommandResult =
        withContext(Dispatchers.IO) {
            val result = executeNative { it.getWatchers() }
                ?: service?.takeUnless { shellBackend }?.getWatchers()
                ?: executeShell(AppOpsCommands.getWatchers())
            result.copy(stdout = WatcherResponseCodec.decode(result.stdout))
        }

    override suspend fun getHistory(
        operationName: String,
    ): ShellCommandResult =
        withContext(Dispatchers.IO) {
            executeNative { it.getHistory(operationName) }
                ?: service?.takeUnless { shellBackend }?.getHistory(operationName)
                ?: executeShell(AppOpsCommands.getHistory(operationName)).let { result ->
                    result.copy(
                        stdout = AppOpsHistoryOutputExtractor.extract(
                            commandOutput = result.stdout,
                            operationName = operationName,
                        ),
                    )
                }
        }

    override suspend fun getPackagePermissions(
        packageName: String,
    ): ShellCommandResult =
        withContext(Dispatchers.IO) {
            readWithShellFallback(AppOpsCommands.getPackagePermissions(packageName)) {
                executeNative { it.getPackagePermissions(packageName) } ?: service?.takeUnless { shellBackend }?.getPackagePermissions(packageName)
            }
        }

    override suspend fun setRevokedCompat(
        userId: Int,
        packageName: String,
        permissionName: String,
        revoked: Boolean,
    ): ShellCommandResult =
        withContext(Dispatchers.IO) {
            executeNative {
                it.setRevokedCompat(userId, packageName, permissionName, revoked)
            }
                ?: service?.takeUnless { shellBackend }?.setRevokedCompat(
                    userId,
                    packageName,
                    permissionName,
                    revoked,
                )
                ?: executeShell(
                    AppOpsCommands.setRevokedCompat(
                        userId = userId,
                        packageName = packageName,
                        permissionName = permissionName,
                        revoked = revoked,
                    ),
                )
        }

    override suspend fun setPackageOpMode(
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): ShellCommandResult =
        withContext(Dispatchers.IO) {
            executeNative { it.setPackageOpMode(packageName, operationName, mode) } ?: service?.takeUnless { shellBackend }?.setPackageOpMode(
                    packageName,
                    operationName,
                    mode.shellValue,
                )
                ?: executeShell(
                    AppOpsCommands.setPackageOpMode(packageName, operationName, mode),
                )
        }

    override suspend fun setUidOpMode(
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): ShellCommandResult =
        withContext(Dispatchers.IO) {
            executeNative { it.setUidOpMode(packageName, operationName, mode) } ?: service?.takeUnless { shellBackend }?.setUidOpMode(
                    packageName,
                    operationName,
                    mode.shellValue,
                )
                ?: executeShell(
                    AppOpsCommands.setUidOpMode(packageName, operationName, mode),
                )
        }

    // Read commands may safely retry through Shizuku's shell transport. Never retry writes:
    // a lost Binder response does not prove that a mode change was not applied.
    private suspend fun readWithShellFallback(
        arguments: List<String>,
        read: suspend () -> ShellCommandResult?,
    ): ShellCommandResult = dev.izumi.appopsnext.appops.command.readWithFallback(
        primary = read,
        fallback = {
            executeShell(arguments).also { result ->
                if (result.exitCode == 0 && !result.timedOut) {
                    // Future writes use this verified transport once; no write is replayed.
                    shellBackend = true
                }
            }
        },
        onFailure = { result, error ->
            diagnosticLog.warning(
                source = LOG_SOURCE,
                message = "AppOps read failed; retrying privileged shell. " +
                    "exit=${result?.exitCode}, timeout=${result?.timedOut}, " +
                    "stderr=${result?.stderr?.take(512)}, error=${error?.javaClass?.simpleName}",
            )
        },
    )

    private suspend fun executeShell(arguments: List<String>): ShellCommandResult {
        if (!shellBackend && service == null && nativeGateway == null) {
            activateShellBackend()
        }
        val res = TelosShellEngine.execArgsResult(arguments.toTypedArray())
        return ShellCommandResult(
            exitCode = res.exitCode,
            stdout = res.stdout,
            stderr = res.stderr,
            timedOut = res.timedOut,
        )
    }

    private suspend fun executeNative(
        action: suspend (NativeDaemonGateway) -> ShellCommandResult,
    ): ShellCommandResult? {
        if (shellBackend) return null
        val gateway = nativeGateway ?: return null
        try {
            return action(gateway)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            gateway.close()
            connectionScope.launch {
                // An old command must never disconnect a newly established backend.
                if (nativeGateway === gateway) {
                    nativeGateway = null
                    mutableState.value = PrivilegedServiceState.Failure(
                        PrivilegedServiceFailureReason.INITIALIZATION_FAILED,
                    )
                    diagnosticLog.warning(
                        source = NATIVE_LOG_SOURCE,
                        message = "Native command connection lost: ${error.message}",
                    )
                }
            }
            throw error
        }
    }

    private companion object {
        const val TAG = "PrivilegedService"
        const val LOG_SOURCE = "UserService"
        const val NATIVE_LOG_SOURCE = "NativeBackend"
        const val USER_SERVICE_PROCESS_SUFFIX = "appops"
        const val CONNECTION_TIMEOUT_MILLIS = 12_000L
    }
}
