package roro.stellar.manager.receiver

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import roro.stellar.Stellar
import roro.stellar.manager.AppConstants
import roro.stellar.manager.R
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.startup.command.Starter
import roro.stellar.manager.startup.worker.AdbStartWorker
import roro.stellar.manager.util.UserHandleCompat

class BootCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED != intent.action
            && Intent.ACTION_BOOT_COMPLETED != intent.action
        ) {
            return
        }

        if (UserHandleCompat.myUserId() > 0 || Stellar.pingBinder()) return

        val mode = StellarSettings.getBootMode()
        if (mode == StellarSettings.BootMode.NONE || mode == StellarSettings.BootMode.SCRIPT) return

        val lastLaunch = StellarSettings.getLastLaunchMethod()

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 优先尝试 Root 启动
                if (hasRootPermission() && rootStart(context)) {
                    return@launch
                }

                // Root 失败或不可用，且上次是通过 ADB 启动的，使用 WorkManager
                if (lastLaunch == StellarSettings.LaunchMethod.ADB && canStartViaAdb(context)) {
                    Log.i(AppConstants.TAG, "上次通过 ADB 启动，使用 WorkManager 进行开机 ADB 自启")
                    AdbStartWorker.enqueue(context)
                    return@launch
                }

                showToast(context, context.getString(R.string.boot_start_failed, "no available startup path"))
            } finally {
                pending.finish()
            }
        }
    }

    private fun hasRootPermission(): Boolean {
        return try {
            Shell.getShell().isRoot
        } catch (_: Exception) {
            false
        }
    }

    private fun canStartViaAdb(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    private fun rootStart(context: Context): Boolean {
        val result = try {
            Shell.cmd(Starter.internalCommand).exec()
        } catch (e: Exception) {
            Log.e(AppConstants.TAG, "Root boot start failed", e)
            return false
        }

        if (result.code != 0) {
            val err = result.err.joinToString("\n").ifEmpty { "exit code: ${result.code}" }
            Log.w(AppConstants.TAG, "Root boot start command failed: $err")
            return false
        }

        Thread.sleep(3000)
        if (Stellar.pingBinder()) {
            StellarSettings.setLastLaunchMethod(StellarSettings.LaunchMethod.ROOT)
            showToast(context, context.getString(R.string.boot_start_success))
            return true
        }

        Log.w(AppConstants.TAG, "Root boot start command succeeded but binder not available")
        return false
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
