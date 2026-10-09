package de.mm20.launcher2.ui.launcher.sheets

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.appmanagement.ShizukuManager
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppInfoSheetVM(
    private val app: Application,
) : ViewModel(), KoinComponent {

    private val shizukuManager: ShizukuManager by inject()
    private val freezeManager: FreezeManager by inject()

    private val _permissions = MutableStateFlow<List<PermissionInfo>>(emptyList())
    val permissions = _permissions.asStateFlow()

    private val _icon = MutableStateFlow<LauncherIcon?>(null)
    val icon = _icon.asStateFlow()

    private val _isShizukuAvailable = MutableStateFlow(false)
    val isShizukuAvailable = _isShizukuAvailable.asStateFlow()

    private val _isFrozen = MutableStateFlow(false)
    val isFrozen = _isFrozen.asStateFlow()

    private val _canFreeze = MutableStateFlow(false)
    val canFreeze = _canFreeze.asStateFlow()

    init {
        _isShizukuAvailable.value = shizukuManager.isAvailable()
        refreshFreezeState()
    }

    private fun refreshFreezeState() {
        viewModelScope.launch {
            _isFrozen.value = freezeManager.isFrozen(app.componentName.packageName)
            freezeManager.refreshBackendState()
            _canFreeze.value = freezeManager.hasPermission()
        }
    }

    fun init(context: Context, iconSize: Int) {
        loadPermissions(context)
        loadIcon(context, iconSize)
    }

    private fun loadIcon(context: Context, size: Int) {
        viewModelScope.launch {
            _icon.value = app.loadIcon(context, size, false)
        }
    }

    private fun loadPermissions(context: Context) {
        viewModelScope.launch {
            val pm = context.packageManager
            val packageInfo = try {
                pm.getPackageInfo(app.componentName.packageName, PackageManager.GET_PERMISSIONS)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

            val requestedPermissions = packageInfo?.requestedPermissions ?: emptyArray()
            val flags = packageInfo?.requestedPermissionsFlags ?: intArrayOf()

            val list = requestedPermissions.mapIndexed { index, perm ->
                val isGranted = (flags[index] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                PermissionInfo(
                    name = perm.substringAfterLast("."),
                    fullName = perm,
                    isGranted = isGranted
                )
            }.sortedBy { !it.isGranted }
            
            _permissions.value = list
        }
    }

    fun forceStop() {
        viewModelScope.launch {
            shizukuManager.forceStopPackage(app.componentName.packageName)
        }
    }

    fun freeze() {
        // Goes through FreezeManager (selected backend, protected packages, freeze stats) rather
        // than talking to Shizuku directly. Toggles, so a frozen app can be unfrozen again here.
        viewModelScope.launch {
            val packageName = app.componentName.packageName
            val userId = app.user.hashCode()
            if (freezeManager.isFrozen(packageName)) {
                freezeManager.unfreeze(packageName, userId)
            } else {
                freezeManager.freeze(packageName, userId)
            }
            _isFrozen.value = freezeManager.isFrozen(packageName)
        }
    }

    fun requestShizukuPermission() {
        viewModelScope.launch {
            shizukuManager.requestPermission()
            _isShizukuAvailable.value = shizukuManager.isAvailable()
            refreshFreezeState()
        }
    }

    data class PermissionInfo(
        val name: String,
        val fullName: String,
        val isGranted: Boolean
    )
}
