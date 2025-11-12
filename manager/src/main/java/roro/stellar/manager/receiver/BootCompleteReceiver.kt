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
import roro.stellar.manager.AppConstants
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.adb.AdbWirelessHelper
import roro.stellar.manager.ui.features.starter.SelfStarterService
import roro.stellar.manager.ui.features.starter.Starter
import roro.stellar.manager.utils.UserHandleCompat
import roro.stellar.Stellar

/**
 * 开机启动接收器
 * Boot Complete Receiver
 * 
 * 功能说明 Features：
 * - 监听系统开机完成事件 - Listens to system boot complete event
 * - 根据设置自动启动Stellar服务 - Auto starts Stellar service based on settings
 * - 支持Root和无线ADB两种启动方式 - Supports Root and Wireless ADB startup methods
 * - 仅在主用户下执行 - Only executes in main user
 * 
 * 启动条件 Startup Conditions：
 * - Root启动：需要Root权限且开启相应设置
 * - 无线ADB启动：需要WRITE_SECURE_SETTINGS权限（Android 13+）
 */
class BootCompleteReceiver : BroadcastReceiver() {
    /** 无线ADB辅助工具 Wireless ADB helper */
    private val adbWirelessHelper = AdbWirelessHelper()

    /**
     * 接收开机广播
     * Receive boot broadcast
     */
    override fun onReceive(context: Context, intent: Intent) {
        // 只处理开机完成广播
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED != intent.action
            && Intent.ACTION_BOOT_COMPLETED != intent.action
        ) {
            return
        }

        // 仅在主用户(userId=0)下执行，且服务未运行
        if (UserHandleCompat.myUserId() > 0 || Stellar.pingBinder()) return

        // 读取开机启动设置
        val startOnBootRootIsEnabled = StellarSettings.getPreferences()
            .getBoolean(StellarSettings.KEEP_START_ON_BOOT, false)
        val startOnBootWirelessIsEnabled = StellarSettings.getPreferences()
            .getBoolean(StellarSettings.KEEP_START_ON_BOOT_WIRELESS, false)

        // 根据设置选择启动方式
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

    /**
     * Root模式启动
     * Root mode startup
     */
    private fun rootStart(context: Context) {
        if (!Shell.getShell().isRoot) {
            Shell.getCachedShell()?.close()
            return
        }
        Shell.cmd(Starter.internalCommand).exec()
    }

    /**
     * 无线ADB模式启动（Android 13+）
     * Wireless ADB mode startup (Android 13+)
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun adbStart(context: Context) {
        Log.i(
            AppConstants.TAG,
            "WRITE_SECURE_SETTINGS is enabled and user has Start on boot is enabled for wireless ADB"
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

