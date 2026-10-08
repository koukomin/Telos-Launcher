package de.mm20.launcher2.comms.media.video.torrent

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.swig.settings_pack

/**
 * The one libtorrent session of Telos. Telos Downloads (torrent downloads, seeding) and Telos Video
 * (streaming while downloading) both use it, so there is never a second session that fights the first
 * one for the listening port, the DHT and the bandwidth.
 *
 * Users call [acquire] with themselves as owner and [release] when they are done; the session starts with the
 * first owner and stops (off the calling thread) when the last one is gone. The settings ([TorrentConfig])
 * are stored here and applied to the running session as soon as they change.
 */
object TorrentSession {
    private const val TAG = "TorrentSession"

    private val lock = Any()
    private val owners = HashSet<Any>()
    private var manager: SessionManager? = null

    /** The thread that stops the previous session; a new session waits for it (a fixed port is free again then) */
    @Volatile private var stopThread: Thread? = null
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    private val _config = MutableStateFlow(TorrentConfig())
    val config: StateFlow<TorrentConfig> = _config

    /** Reads the stored settings; safe to call many times */
    fun init(context: Context) {
        synchronized(lock) {
            if (prefs != null) return
            appContext = context.applicationContext
            prefs = appContext!!.getSharedPreferences("telos_torrent_session", Context.MODE_PRIVATE)
            _config.value = read(prefs!!)
        }
    }

    fun updateConfig(context: Context, transform: (TorrentConfig) -> TorrentConfig) {
        init(context)
        val next = synchronized(lock) {
            val c = transform(_config.value).sanitized()
            _config.value = c
            write(prefs!!, c)
            c
        }
        // the session is touched outside of the lock: applying settings can take a moment
        val sm = synchronized(lock) { manager }
        if (sm != null) apply(sm, next, startup = false)
    }

    val isRunning: Boolean get() = synchronized(lock) { manager != null }

    /** The running session or null; never starts one */
    fun current(): SessionManager? = synchronized(lock) { manager }

    /** Starts the session if needed and registers [owner]. Call from a background thread, starting takes a moment. */
    fun acquire(context: Context, owner: Any): SessionManager {
        init(context)
        synchronized(lock) {
            owners.add(owner)
            manager?.let { return it }
            runCatching { stopThread?.join(8000) }
            val sm = SessionManager(false)
            val cfg = _config.value
            val pack = SettingsPack.defaultSettings()
            fill(pack, cfg, startup = true)
            sm.start(SessionParams(pack))
            manager = sm
            de.mm20.launcher2.comms.blocklist.BlockLists.init(context)
            // a big list takes seconds to hand over to libtorrent: the session does not wait for it
            Thread {
                de.mm20.launcher2.comms.blocklist.BlockLists.applyToSession(sm)
            }.apply { name = "torrent-ip-filter"; isDaemon = true }.start()
            return sm
        }
    }

    /** [owner] does not need the session any more. The session stops when nobody does. */
    fun release(owner: Any) {
        // stopThread is set under the lock: a new owner that gets the lock next must see it and wait for the old session
        synchronized(lock) {
            owners.remove(owner)
            if (owners.isNotEmpty()) return
            val toStop = manager ?: return
            manager = null
            // stopping can block, and a new owner may already be waiting for the lock: stop on another thread
            val t = Thread {
                try {
                    toStop.stop()
                } catch (e: Throwable) {
                    Log.w(TAG, "Could not stop the session", e)
                }
            }.apply { name = "torrent-session-stop" }
            stopThread = t
            t.start()
        }
    }

    @Volatile private var fallbackDownloadKBps = 0

    /**
     * The download limit of Telos Downloads for all downloads together; torrents use it when no limit of their own
     * ([TorrentConfig.downloadLimitKBps]) is set.
     */
    fun setFallbackDownloadLimit(kbps: Int) {
        val v = kbps.coerceAtLeast(0)
        if (v == fallbackDownloadKBps) return
        fallbackDownloadKBps = v
        val sm = current() ?: return
        apply(sm, _config.value, startup = false)
    }

    /** Re-applies the enabled block lists, called after a list changed */
    fun reapplyBlockList() {
        val sm = current() ?: return
        de.mm20.launcher2.comms.blocklist.BlockLists.applyToSession(sm)
    }

    private fun apply(sm: SessionManager, cfg: TorrentConfig, startup: Boolean) {
        try {
            val pack = SettingsPack()
            fill(pack, cfg, startup)
            sm.applySettings(pack)
        } catch (e: Throwable) {
            Log.w(TAG, "Could not apply the torrent settings", e)
        }
    }

    private fun fill(p: SettingsPack, c: TorrentConfig, @Suppress("UNUSED_PARAMETER") startup: Boolean) {
        p.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), c.dht)
        p.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), c.lsd)
        p.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), c.upnp)
        p.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), c.natPmp)
        p.setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(), c.utp)
        p.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), c.utp)
        // a slow torrent still counts against the limit, otherwise "max active" would not mean what it says
        p.setBoolean(settings_pack.bool_types.dont_count_slow_torrents.swigValue(), false)
        val enc = when (c.encryption) {
            TorrentEncryption.Prefer -> settings_pack.enc_policy.pe_enabled
            TorrentEncryption.Require -> settings_pack.enc_policy.pe_forced
            TorrentEncryption.Disable -> settings_pack.enc_policy.pe_disabled
        }
        p.setInteger(settings_pack.int_types.out_enc_policy.swigValue(), enc.swigValue())
        p.setInteger(settings_pack.int_types.in_enc_policy.swigValue(), enc.swigValue())
        p.setInteger(settings_pack.int_types.allowed_enc_level.swigValue(), settings_pack.enc_level.pe_both.swigValue())
        p.setInteger(settings_pack.int_types.download_rate_limit.swigValue(), (if (c.downloadLimitKBps > 0) c.downloadLimitKBps else fallbackDownloadKBps) * 1024)
        p.setInteger(settings_pack.int_types.upload_rate_limit.swigValue(), c.uploadLimitKBps * 1024)
        p.setInteger(settings_pack.int_types.active_downloads.swigValue(), c.maxActiveDownloads)
        p.setInteger(settings_pack.int_types.active_seeds.swigValue(), c.maxActiveSeeds)
        p.setInteger(settings_pack.int_types.active_limit.swigValue(), c.activeLimit())
        p.setInteger(settings_pack.int_types.connections_limit.swigValue(), c.maxConnections)
        p.listenInterfaces(c.listenInterfaces())
    }

    private fun read(sp: SharedPreferences): TorrentConfig {
        val d = TorrentConfig()
        return TorrentConfig(
            dht = sp.getBoolean("dht", d.dht),
            pex = sp.getBoolean("pex", d.pex),
            lsd = sp.getBoolean("lsd", d.lsd),
            utp = sp.getBoolean("utp", d.utp),
            encryption = runCatching { TorrentEncryption.valueOf(sp.getString("encryption", "")!!) }.getOrDefault(d.encryption),
            listenPort = sp.getInt("listenPort", d.listenPort),
            upnp = sp.getBoolean("upnp", d.upnp),
            natPmp = sp.getBoolean("natPmp", d.natPmp),
            downloadLimitKBps = sp.getInt("downloadLimitKBps", d.downloadLimitKBps),
            uploadLimitKBps = sp.getInt("uploadLimitKBps", d.uploadLimitKBps),
            maxActiveDownloads = sp.getInt("maxActiveDownloads", d.maxActiveDownloads),
            maxActiveSeeds = sp.getInt("maxActiveSeeds", d.maxActiveSeeds),
            maxConnections = sp.getInt("maxConnections", d.maxConnections),
            seedRatioX100 = sp.getInt("seedRatioX100", d.seedRatioX100),
            seedMinutes = sp.getInt("seedMinutes", d.seedMinutes),
            stopAtDone = sp.getBoolean("stopAtDone", d.stopAtDone),
            sequentialByDefault = sp.getBoolean("sequentialByDefault", d.sequentialByDefault),
        ).sanitized()
    }

    private fun write(sp: SharedPreferences, c: TorrentConfig) {
        sp.edit()
            .putBoolean("dht", c.dht).putBoolean("pex", c.pex).putBoolean("lsd", c.lsd).putBoolean("utp", c.utp)
            .putString("encryption", c.encryption.name).putInt("listenPort", c.listenPort)
            .putBoolean("upnp", c.upnp).putBoolean("natPmp", c.natPmp)
            .putInt("downloadLimitKBps", c.downloadLimitKBps).putInt("uploadLimitKBps", c.uploadLimitKBps)
            .putInt("maxActiveDownloads", c.maxActiveDownloads).putInt("maxActiveSeeds", c.maxActiveSeeds)
            .putInt("maxConnections", c.maxConnections)
            .putInt("seedRatioX100", c.seedRatioX100).putInt("seedMinutes", c.seedMinutes)
            .putBoolean("stopAtDone", c.stopAtDone).putBoolean("sequentialByDefault", c.sequentialByDefault)
            .apply()
    }
}
