package de.mm20.launcher2.appmanagement.art

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.compose.runtime.Immutable
import de.mm20.launcher2.ktx.TelosShellEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

@Immutable
data class SqliteOptimizationState(
    val isRunning: Boolean = false,
    val currentTarget: String = "",
    val databasesOptimized: Int = 0,
    val bytesReclaimed: Long = 0L,
    val usedPrivilegedCli: Boolean = false,
    val completed: Boolean = false,
)

object SqliteVacuumEngine {
    private const val TAG = "SqliteVacuumEngine"
    private val SQLITE_MAGIC = "SQLite format 3".toByteArray(Charsets.US_ASCII)
    private val VALID_PKG_REGEX = Regex("""^[a-zA-Z0-9._]+$""")
    private val CRITICAL_SYSTEM_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.providers.telephony",
        "com.android.providers.contacts",
        "com.android.providers.media",
        "com.google.android.gms",
        "com.android.vending",
        "moe.shizuku.privileged.api",
    )

    private val _state = MutableStateFlow(SqliteOptimizationState())
    val state: StateFlow<SqliteOptimizationState> = _state.asStateFlow()

    suspend fun optimizeDatabases(context: Context): SqliteOptimizationState = withContext(Dispatchers.IO) {
        if (_state.value.isRunning) return@withContext _state.value

        val appContext = context.applicationContext
        _state.value = SqliteOptimizationState(
            isRunning = true,
            currentTarget = appContext.packageName,
        )

        var optimizedCount = 0
        var reclaimedBytes = 0L

        // 1. Optimize Telos's own internal SQLite databases via Android's built-in SQLite engine
        val localResult = vacuumLocalAppDatabases(appContext)
        optimizedCount += localResult.first
        reclaimedBytes += localResult.second

        // 2. If privileged shell + sqlite3 binary are available, optimize eligible app databases
        var usedPrivilegedCli = false
        if (TelosShellEngine.isPrivilegedAvailable(appContext)) {
            val privInfo = probePrivilegedSqliteSupport()
            if (privInfo != null) {
                usedPrivilegedCli = true
                val pm = appContext.packageManager
                val apps = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
                    .filter { app ->
                        app.packageName != appContext.packageName &&
                            app.packageName !in CRITICAL_SYSTEM_PACKAGES &&
                            VALID_PKG_REGEX.matches(app.packageName) &&
                            (privInfo.isRoot || (app.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) &&
                            (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                    }

                for (app in apps) {
                    if (!_state.value.isRunning) break
                    val pkg = app.packageName
                    _state.value = _state.value.copy(
                        currentTarget = pkg,
                        databasesOptimized = optimizedCount,
                        bytesReclaimed = reclaimedBytes,
                        usedPrivilegedCli = true,
                    )
                    val (cnt, saved) = vacuumPackageViaShell(
                        packageName = pkg,
                        sqliteBin = privInfo.sqliteBinaryPath,
                        isRoot = privInfo.isRoot,
                    )
                    optimizedCount += cnt
                    reclaimedBytes += saved
                }
            }
        }

        val finalState = SqliteOptimizationState(
            isRunning = false,
            currentTarget = "",
            databasesOptimized = optimizedCount,
            bytesReclaimed = reclaimedBytes.coerceAtLeast(0L),
            usedPrivilegedCli = usedPrivilegedCli,
            completed = true,
        )
        _state.value = finalState
        finalState
    }

    fun cancel() {
        _state.value = _state.value.copy(isRunning = false)
    }

    private fun vacuumLocalAppDatabases(context: Context): Pair<Int, Long> {
        var count = 0
        var saved = 0L
        val dbNames = runCatching { context.databaseList().toList() }.getOrDefault(emptyList())
        for (name in dbNames) {
            if (name.endsWith("-wal") || name.endsWith("-shm") || name.endsWith("-journal")) continue
            val dbFile = context.getDatabasePath(name)
            if (!dbFile.exists() || !dbFile.isFile || !hasSqliteHeader(dbFile)) continue

            val beforeSize = totalDatabaseFootprint(dbFile)
            val ok = runCatching {
                SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                ).use { db ->
                    runCatching { db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).close() }
                    db.execSQL("VACUUM")
                    db.execSQL("REINDEX")
                }
                true
            }.onFailure {
                Log.w(TAG, "Skipping locked or busy local DB ${dbFile.name}: ${it.message}")
            }.getOrDefault(false)

            if (ok) {
                count++
                val afterSize = totalDatabaseFootprint(dbFile)
                if (beforeSize > afterSize) {
                    saved += (beforeSize - afterSize)
                }
            }
        }
        return count to saved
    }

    private fun totalDatabaseFootprint(dbFile: File): Long {
        val base = dbFile.length()
        val wal = File(dbFile.absolutePath + "-wal").takeIf { it.exists() }?.length() ?: 0L
        val shm = File(dbFile.absolutePath + "-shm").takeIf { it.exists() }?.length() ?: 0L
        return base + wal + shm
    }

    private fun hasSqliteHeader(file: File): Boolean {
        if (file.length() < 16L) return false
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(15)
                val read = raf.read(header)
                read == 15 && header.contentEquals(SQLITE_MAGIC)
            }
        }.getOrDefault(false)
    }

    private data class PrivilegedSqliteInfo(
        val sqliteBinaryPath: String,
        val isRoot: Boolean,
    )

    private suspend fun probePrivilegedSqliteSupport(): PrivilegedSqliteInfo? {
        val script = """
            UID_VAL=${'$'}(id -u 2>/dev/null)
            for BIN in /system/bin/sqlite3 /system/xbin/sqlite3 /product/bin/sqlite3 /vendor/bin/sqlite3 ${'$'}(command -v sqlite3 2>/dev/null); do
                if [ -x "${'$'}BIN" ]; then
                    echo "${'$'}UID_VAL|${'$'}BIN"
                    exit 0
                fi
            done
        """.trimIndent()
        val res = TelosShellEngine.execResult(script, timeoutMs = 8_000L)
        if (!res.isSuccess) return null
        val line = res.stdout.lineSequence().firstOrNull { it.contains('|') }?.trim() ?: return null
        val sep = line.indexOf('|')
        val uid = line.substring(0, sep).toIntOrNull() ?: return null
        val bin = line.substring(sep + 1).trim()
        if (bin.isEmpty()) return null
        return PrivilegedSqliteInfo(sqliteBinaryPath = bin, isRoot = uid == 0)
    }

    private suspend fun vacuumPackageViaShell(
        packageName: String,
        sqliteBin: String,
        isRoot: Boolean,
    ): Pair<Int, Long> {
        if (!VALID_PKG_REGEX.matches(packageName)) return 0 to 0L
        val safeBin = "'" + sqliteBin.replace("'", "'\\''") + "'"
        val script = if (isRoot) {
            """
            DB_DIR="/data/data/$packageName/databases"
            [ -d "${'$'}DB_DIR" ] || exit 0
            am force-stop $packageName >/dev/null 2>&1
            COUNT=0
            SAVED=0
            for F in "${'$'}DB_DIR"/*; do
                [ -f "${'$'}F" ] || continue
                case "${'$'}F" in
                    *-wal|*-shm|*-journal|*.bak|*.tmp) continue ;;
                esac
                MAGIC=${'$'}(dd if="${'$'}F" bs=1 count=15 2>/dev/null)
                [ "${'$'}MAGIC" = "SQLite format 3" ] || continue
                BEFORE=${'$'}(stat -c '%s' "${'$'}F" 2>/dev/null || echo 0)
                if $safeBin "${'$'}F" "PRAGMA busy_timeout=2500; VACUUM; REINDEX;" >/dev/null 2>&1; then
                    AFTER=${'$'}(stat -c '%s' "${'$'}F" 2>/dev/null || echo "${'$'}BEFORE")
                    COUNT=${'$'}((COUNT + 1))
                    if [ "${'$'}BEFORE" -gt "${'$'}AFTER" ]; then
                        SAVED=${'$'}((SAVED + BEFORE - AFTER))
                    fi
                fi
            done
            echo "RESULT|${'$'}COUNT|${'$'}SAVED"
            """.trimIndent()
        } else {
            """
            am force-stop $packageName >/dev/null 2>&1
            run-as $packageName sh -c '
                [ -d databases ] || exit 0
                COUNT=0
                SAVED=0
                for F in databases/*; do
                    [ -f "${'$'}F" ] || continue
                    case "${'$'}F" in
                        *-wal|*-shm|*-journal|*.bak|*.tmp) continue ;;
                    esac
                    MAGIC=${'$'}(dd if="${'$'}F" bs=1 count=15 2>/dev/null)
                    [ "${'$'}MAGIC" = "SQLite format 3" ] || continue
                    BEFORE=${'$'}(stat -c "%s" "${'$'}F" 2>/dev/null || echo 0)
                    if '"$safeBin"' "${'$'}F" "PRAGMA busy_timeout=2500; VACUUM; REINDEX;" >/dev/null 2>&1; then
                        AFTER=${'$'}(stat -c "%s" "${'$'}F" 2>/dev/null || echo "${'$'}BEFORE")
                        COUNT=${'$'}((COUNT + 1))
                        if [ "${'$'}BEFORE" -gt "${'$'}AFTER" ]; then
                            SAVED=${'$'}((SAVED + BEFORE - AFTER))
                        fi
                    fi
                done
                echo "RESULT|${'$'}COUNT|${'$'}SAVED"
            ' 2>/dev/null
            """.trimIndent()
        }

        var count = 0
        var saved = 0L
        TelosShellEngine.execLineSequence(script, timeoutMs = 30_000L) { lines ->
            for (line in lines) {
                if (line.startsWith("RESULT|")) {
                    val parts = line.split('|')
                    if (parts.size >= 3) {
                        count = parts[1].trim().toIntOrNull() ?: 0
                        saved = parts[2].trim().toLongOrNull() ?: 0L
                    }
                }
            }
        }
        return count to saved
    }
}
