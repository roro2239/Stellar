package roro.stellar.manager.receiver

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import roro.stellar.Stellar
import roro.stellar.manager.AppConstants
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.adb.AdbWirelessHelper
import roro.stellar.manager.ui.features.starter.SelfStarterService
import roro.stellar.manager.ui.features.starter.Starter
import roro.stellar.manager.util.UserHandleCompat

class BootCompleteReceiver : BroadcastReceiver() {
    private val adbWirelessHelper = AdbWirelessHelper()

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED != intent.action
            && Intent.ACTION_BOOT_COMPLETED != intent.action
        ) {
            return
        }

        if (UserHandleCompat.myUserId() > 0 || Stellar.pingBinder()) return

        val startOnBootRootIsEnabled = StellarSettings.getPreferences()
            .getBoolean(StellarSettings.KEEP_START_ON_BOOT, false)
        val startOnBootWirelessIsEnabled = StellarSettings.getPreferences()
            .getBoolean(StellarSettings.KEEP_START_ON_BOOT_WIRELESS, false)

        if (startOnBootRootIsEnabled) {
            rootStart(context)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
            && startOnBootWirelessIsEnabled
        ) {
            adbStart(context)
        } else {
            Log.w(AppConstants.TAG, "不支持开机启动")
        }
    }

    private fun rootStart(context: Context) {
        if (!Shell.getShell().isRoot) {
            Shell.getCachedShell()?.close()
            return
        }
        Shell.cmd(Starter.internalCommand).exec()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun adbStart(context: Context) {
        Log.i(
            AppConstants.TAG,
            "WRITE_SECURE_SETTINGS 已启用且用户已启用无线 ADB 开机启动"
        )

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val wirelessAdbStatus = adbWirelessHelper.validateThenEnableWirelessAdbAsync(
                    context.contentResolver, context, 15_000L
                )
                if (wirelessAdbStatus) {
                    val intentService = Intent(context, SelfStarterService::class.java)
                    context.startService(intentService)
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
                Log.e(AppConstants.TAG, "权限被拒绝", e)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(AppConstants.TAG, "启动无线ADB时出错", e)
            } finally {
                pending.finish()
            }
        }
    }
}

