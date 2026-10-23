package de.mm20.launcher2.network.nextdns

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Account editor only. Saving a credential never changes the device's DNS or enables the VPN. */
class NextDnsRepository(context: Context) {
    private val vault = NextDnsVault(context.applicationContext)
    private val api = NextDnsApi(NextDnsHttpsTransport())
    private val mutex = Mutex()

    suspend fun credentialsInfo(): NextDnsCredentialsInfo = operation {
        val credentials = vault.read()
        NextDnsCredentialsInfo(credentials?.profileId, credentials != null)
    }
    suspend fun connect(profileId: String, key: String): NextDnsProfile = operation {
        val credentials = NextDnsCredentials(NextDnsValidation.profileId(profileId), NextDnsValidation.apiKey(key))
        val profile = api.profile(credentials.profileId, credentials.key)
        kotlin.coroutines.coroutineContext.ensureActive()
        vault.write(credentials)
        profile
    }
    suspend fun disconnect() = operation { vault.clear() }
    suspend fun loadLogs(search: String = "", status: String? = null, cursor: String? = null): NextDnsPage<NextDnsLog> = operation {
        val credentials = credentials()
        api.logs(credentials.profileId, credentials.key, search, status, cursor)
    }
    suspend fun loadSettings(page: NextDnsSettingsPage): NextDnsSettings = operation {
        val credentials = credentials()
        api.settings(credentials.profileId, credentials.key, page)
    }
    suspend fun loadAnalytics(
        metric: NextDnsAnalyticsMetric,
        period: NextDnsAnalyticsPeriod,
        deviceId: String?,
        cursor: String? = null,
    ): NextDnsAnalyticsSnapshot = operation {
        val credentials = credentials()
        api.analytics(credentials.profileId, credentials.key, metric, period, deviceId, cursor)
    }
    suspend fun loadAnalyticsDevices(): List<NextDnsAnalyticsDevice> = operation {
        val credentials = credentials()
        api.analyticsDevices(credentials.profileId, credentials.key)
    }
    suspend fun updateSettings(page: NextDnsSettingsPage, values: kotlinx.serialization.json.JsonObject) = operation {
        val credentials = credentials()
        api.patchSettings(credentials.profileId, credentials.key, page, values)
    }
    suspend fun loadRules(allow: Boolean): List<NextDnsRule> = operation {
        val credentials = credentials()
        api.rules(credentials.profileId, credentials.key, allow)
    }
    suspend fun loadRewrites(): List<NextDnsRewrite> = operation {
        val credentials = credentials()
        api.rewrites(credentials.profileId, credentials.key)
    }
    suspend fun createRewrite(name: String, content: String): NextDnsRewrite = operation {
        val credentials = credentials()
        api.createRewrite(credentials.profileId, credentials.key, name, content)
    }
    suspend fun deleteRewrite(rewriteId: String) = operation {
        val credentials = credentials()
        api.deleteRewrite(credentials.profileId, credentials.key, rewriteId)
    }
    suspend fun putRule(allow: Boolean, domain: String) = operation {
        val credentials = credentials()
        api.putRule(credentials.profileId, credentials.key, allow, domain)
    }
    suspend fun setRuleActive(allow: Boolean, domain: String, active: Boolean) = operation {
        val credentials = credentials()
        api.setRuleActive(credentials.profileId, credentials.key, allow, domain, active)
    }
    suspend fun deleteRule(allow: Boolean, domain: String) = operation {
        val credentials = credentials()
        api.deleteRule(credentials.profileId, credentials.key, allow, domain)
    }
    private fun credentials(): NextDnsCredentials = vault.read() ?: throw NextDnsException(NextDnsErrorKind.UNCONFIGURED)
    private suspend fun <T> operation(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: NextDnsException) { throw failure }
            catch (_: java.io.IOException) { throw NextDnsException(NextDnsErrorKind.NETWORK) }
            catch (_: Exception) { throw NextDnsException(NextDnsErrorKind.API) }
        }
    }
}
