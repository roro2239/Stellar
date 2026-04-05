package roro.stellar.manager.receiver

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.topjohnwu.superuser.Shell
import roro.stellar.Stellar
import roro.stellar.manager.AppConstants
import roro.stellar.manager.R
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.startup.command.Starter
import roro.stellar.manager.startup.notification.BootStartNotifications
import roro.stellar.manager.startup.worker.AdbStartWorker
import roro.stellar.manager.util.EnvironmentUtils
import roro.stellar.manager.util.UserHandleCompat

object StellarReceiverStarter {

    fun start(context: Context, forceStart: Boolean = false) {
        if ((UserHandleCompat.myUserId() > 0 || Stellar.pingBinder()) && !forceStart) return

        when (StellarSettings.getLastLaunchMethod()) {
            StellarSettings.LaunchMethod.ROOT -> rootStart() || adbStart(context)
            StellarSettings.LaunchMethod.ADB -> adbStart(context)
            StellarSettings.LaunchMethod.UNKNOWN -> {
                Log.i(AppConstants.TAG, "缺少上次启动方式，使用兼容回退路径")
                rootStart() || adbStart(context)
            }
        }
    }

    private fun adbStart(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            EnvironmentUtils.getAdbTcpPort() <= 0
        ) {
            Log.w(AppConstants.TAG, "后台启动不受支持：当前设备不支持无线调试自启")
            return false
        }

        if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            BootStartNotifications.showNotification(
                context,
                context.getString(R.string.boot_start_failed, "WRITE_SECURE_SETTINGS")
            )
            return false
        }

        AdbStartWorker.enqueue(context)
        return true
    }

    private fun rootStart(): Boolean {
        if (!Shell.getShell().isRoot) {
            Shell.getCachedShell()?.close()
            return false
        }

        try {
            return Shell.cmd(Starter.internalCommand).exec().code == 0
        } catch (e: Exception) {
            Log.e(AppConstants.TAG, "Root 后台启动失败", e)
            return false
        }
    }
}
