package roro.stellar.manager.ui.features.home

import android.content.pm.PackageManager
import roro.stellar.manager.compat.BuildUtils.atLeast30
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import roro.stellar.Stellar
import roro.stellar.manager.BuildConfig
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.common.state.Resource
import roro.stellar.manager.model.FeatureAvailability
import roro.stellar.manager.model.RestrictedFeature
import roro.stellar.manager.model.ServiceStatus
import roro.stellar.manager.startup.command.Starter
import roro.stellar.manager.util.EnvironmentUtils
import roro.stellar.manager.util.UserHandleCompat
import java.util.concurrent.TimeUnit

class HomeViewModel : ViewModel() {

    private val _serviceStatus = MutableLiveData<Resource<ServiceStatus>>()
    val serviceStatus = _serviceStatus as LiveData<Resource<ServiceStatus>>

    private fun load(): ServiceStatus {
        if (!Stellar.pingBinder()) return ServiceStatus()
        val grantRuntimePermission = hasRemotePermission("android.permission.GRANT_RUNTIME_PERMISSIONS")
        val revokeRuntimePermission = hasRemotePermission("android.permission.REVOKE_RUNTIME_PERMISSIONS")
        val manageRuntimePermission = grantRuntimePermission && revokeRuntimePermission
        val manageAppOps = hasRemotePermission("android.permission.MANAGE_APP_OPS_MODES")
        val writeSecureSettings = hasRemotePermission("android.permission.WRITE_SECURE_SETTINGS")
        val shellIdCommand = canExecuteCommand("id")
        val propertyReadCommand = canExecuteCommand("getprop ro.build.version.sdk")
        val settingsReadCommand = canExecuteCommand("settings get secure enabled_accessibility_services")
        val packageListCommand = canExecuteCommand("cmd package list packages roro.stellar.manager")
        val serviceListCommand = canExecuteCommand("service list")
        val processListCommand = canExecuteCommand("ps -A -o PID 2>/dev/null | head -n 1")
        val filesystemReadCommand = canExecuteCommand("ls /system/bin")
        val selinuxStatusCommand = canExecuteCommand("getenforce")
        val commandStates = listOf(
            FeatureAvailability(RestrictedFeature.SHELL_ID_COMMAND, shellIdCommand),
            FeatureAvailability(RestrictedFeature.PROPERTY_READ_COMMAND, propertyReadCommand),
            FeatureAvailability(RestrictedFeature.SETTINGS_READ_COMMAND, settingsReadCommand),
            FeatureAvailability(RestrictedFeature.PACKAGE_LIST_COMMAND, packageListCommand),
            FeatureAvailability(RestrictedFeature.SERVICE_LIST_COMMAND, serviceListCommand),
            FeatureAvailability(RestrictedFeature.PROCESS_LIST_COMMAND, processListCommand),
            FeatureAvailability(RestrictedFeature.FILESYSTEM_READ_COMMAND, filesystemReadCommand),
            FeatureAvailability(RestrictedFeature.SELINUX_STATUS_COMMAND, selinuxStatusCommand),
            FeatureAvailability(RestrictedFeature.APPOPS_MANAGE, manageAppOps)
        )
        val terminalCommand = commandStates.all { it.available }
        val bootReceiverStart = UserHandleCompat.myUserId() == 0
        val bootAdbPortDiscovery = atLeast30 ||
            EnvironmentUtils.getAdbTcpPort() > 0
        val bootAdbConnectCommand = shellIdCommand && propertyReadCommand
        val bootAdbStart = writeSecureSettings &&
            bootReceiverStart &&
            bootAdbPortDiscovery &&
            bootAdbConnectCommand &&
            grantRuntimePermission
        val featureStates = listOf(
            FeatureAvailability(
                RestrictedFeature.TERMINAL_COMMAND,
                terminalCommand,
                commandStates.filterNot { it.available }
            ),
            FeatureAvailability(RestrictedFeature.RUNTIME_PERMISSION_MANAGE, manageRuntimePermission),
            FeatureAvailability(RestrictedFeature.SECURE_SETTINGS_WRITE, writeSecureSettings),
            FeatureAvailability(RestrictedFeature.BOOT_ADB_START, bootAdbStart)
        ).filterNot { it.available }
        return ServiceStatus(Stellar.uid, Stellar.version, grantRuntimePermission, featureStates)
    }

    private fun hasRemotePermission(permission: String): Boolean =
        Stellar.checkRemotePermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun canExecuteCommand(command: String): Boolean {
        val process = try {
            Stellar.newProcess(arrayOf("sh", "-c", command), null, null)
        } catch (_: Throwable) {
            return false
        }

        return try {
            if (!process.waitForTimeout(1500, TimeUnit.MILLISECONDS)) {
                process.destroy()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (_: Throwable) {
            runCatching { process.destroy() }
            false
        }
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = load()
                _serviceStatus.postValue(Resource.success(status))

                val prefs = StellarSettings.getPreferences()
                val lastVersion = prefs.getInt(StellarSettings.LAST_VERSION_CODE, BuildConfig.VERSION_CODE)
                if (status.isRunning && lastVersion < BuildConfig.VERSION_CODE) {
                    restartService()
                }
                prefs.edit().putInt(StellarSettings.LAST_VERSION_CODE, BuildConfig.VERSION_CODE).apply()
            } catch (_: CancellationException) {
            } catch (e: Throwable) {
                _serviceStatus.postValue(Resource.error(e, ServiceStatus()))
            }
        }
    }

    fun restartService() {
        if (!Stellar.pingBinder()) return
        try {
            Stellar.newProcess(arrayOf("sh", "-c", Starter.internalCommand), null, null)
        } catch (_: Throwable) {}
    }
}
