package roro.stellar.manager.startup.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import roro.stellar.manager.startup.worker.AdbStartWorker

class BootStartActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RETRY -> AdbStartWorker.enqueue(context)
            ACTION_CANCEL -> {
                AdbStartWorker.cancel(context)
                BootStartNotifications.dismiss(context)
            }
        }
    }

    companion object {
        const val ACTION_RETRY = "roro.stellar.manager.action.BOOT_START_RETRY"
        const val ACTION_CANCEL = "roro.stellar.manager.action.BOOT_START_CANCEL"
    }
}
