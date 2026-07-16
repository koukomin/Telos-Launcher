package de.mm20.launcher2.ui.settings.filesearch

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.accounts.Account
import de.mm20.launcher2.accounts.AccountType
import de.mm20.launcher2.accounts.AccountsRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugins.PluginService
import de.mm20.launcher2.preferences.search.FileSearchSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FileSearchSettingsScreenVM : ViewModel(), KoinComponent {
    private val fileSearchSettings: FileSearchSettings by inject()
    private val accountsRepository: AccountsRepository by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val pluginService: PluginService by inject()

    val hasFilePermission = permissionsManager.hasPermission(PermissionGroup.ExternalStorage)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val loading = mutableStateOf(true)
    val nextcloudAccount = mutableStateOf<Account?>(null)
    val owncloudAccount = mutableStateOf<Account?>(null)

    val availablePlugins = pluginService.getPluginsWithState(
        type = PluginType.FileSearch,
        enabled = true,
    )

    val enabledPlugins = fileSearchSettings.enabledPlugins


    fun onResume() {
        viewModelScope.launch {
            nextcloudAccount.value =
                accountsRepository.getCurrentlySignedInAccount(AccountType.Nextcloud)
            owncloudAccount.value =
                accountsRepository.getCurrentlySignedInAccount(AccountType.Owncloud)
            loading.value = false
        }
    }

    val localFiles = fileSearchSettings.localFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setLocalFiles(localFiles: Boolean) {
        fileSearchSettings.setLocalFiles(localFiles)
    }

    val nextcloud = fileSearchSettings.nextcloudFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setNextcloud(nextcloud: Boolean) {
        fileSearchSettings.setNextcloudFiles(nextcloud)
    }

    val gdrive = fileSearchSettings.gdriveFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setGdrive(gdrive: Boolean) {
        fileSearchSettings.setGdriveFiles(gdrive)
    }

    val owncloud = fileSearchSettings.owncloudFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setOwncloud(owncloud: Boolean) {
        fileSearchSettings.setOwncloudFiles(owncloud)
    }

    fun requestFilePermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.ExternalStorage)
    }

    fun login(context: AppCompatActivity, accountType: AccountType) {
        accountsRepository.signin(context, accountType)
    }

    fun setPluginEnabled(authority: String, enabled: Boolean) {
        fileSearchSettings.setPluginEnabled(authority, enabled)
    }

    val typeFilters = fileSearchSettings.typeFilters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setDocuments(enabled: Boolean) = fileSearchSettings.setDocuments(enabled)
    fun setImages(enabled: Boolean) = fileSearchSettings.setImages(enabled)
    fun setVideos(enabled: Boolean) = fileSearchSettings.setVideos(enabled)
    fun setMusic(enabled: Boolean) = fileSearchSettings.setMusic(enabled)
    fun setOther(enabled: Boolean) = fileSearchSettings.setOther(enabled)

    val excludedFolders = fileSearchSettings.excludedFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptySet())

    /**
     * Resolves an ACTION_OPEN_DOCUMENT_TREE uri to a filesystem path that can be matched
     * against MediaStore's DATA column. Returns null for tree ids we can't map (e.g. cloud
     * document providers).
     */
    fun addExcludedFolder(treeUri: Uri): Boolean {
        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val (volume, relativePath) = docId.split(":", limit = 2)
            .takeIf { it.size == 2 }
            ?.let { it[0] to it[1] }
            ?: return false
        val root = if (volume == "primary") {
            @Suppress("DEPRECATION")
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }
        val path = if (relativePath.isEmpty()) root else "$root/$relativePath"
        fileSearchSettings.addExcludedFolder(path)
        return true
    }

    fun removeExcludedFolder(path: String) {
        fileSearchSettings.removeExcludedFolder(path)
    }
}