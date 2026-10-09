package de.mm20.launcher2.ui.settings.buildinfo

import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.BuildConfig
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

@Serializable
data object BuildInfoSettingsRoute: NavKey

@Composable
fun BuildInfoSettingsScreen() {
    val viewModel: BuildInfoSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val buildFeatures by viewModel.buildFeatures.collectAsState(emptyMap())
    PreferenceScreen(title = stringResource(R.string.preference_screen_buildinfo)) {
        item {
            PreferenceCategory {
                Preference(title = stringResource(R.string.hf_buildinfo_type), summary = BuildConfig.BUILD_TYPE)
                var buildSignature by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    buildSignature = withContext(Dispatchers.IO) {
                    try {
                    val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val pi = context.packageManager.getPackageInfo(
                            context.packageName,
                            PackageManager.GET_SIGNING_CERTIFICATES
                        )
                        pi.signingInfo?.apkContentsSigners?.firstOrNull()
                    } else {
                        val pi = context.packageManager.getPackageInfo(
                            context.packageName,
                            PackageManager.GET_SIGNATURES
                        )
                        pi.signatures?.firstOrNull()
                    }
                    val signatureHash = if (signature != null) {
                        val digest = MessageDigest.getInstance("SHA")
                        digest.update(signature.toByteArray())
                        digest.digest().toHexString(
                            HexFormat {
                                upperCase = true
                                bytes {
                                    byteSeparator = ":"
                                }
                            }
                        )
                    } else null
                    signatureHash
                    } catch (e: Exception) {
                        null
                    }
                    }
                }
                Preference(title = stringResource(R.string.hf_buildinfo_signature), summary = buildSignature ?: stringResource(R.string.au3_sysb_unknown))
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.hf_buildinfo_features)) {
                for (feature in buildFeatures) {
                    Preference(
                        title = feature.key,
                        summary = stringResource(if (feature.value) R.string.au3_sysb_yes else R.string.au3_sysb_no)
                    )
                }
            }
        }
    }
}