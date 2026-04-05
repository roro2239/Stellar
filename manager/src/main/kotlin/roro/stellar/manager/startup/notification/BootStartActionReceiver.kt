package roro.stellar.manager.startup.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import roro.stellar.manager.receiver.StellarReceiverStarter
import roro.stellar.manager.startup.worker.AdbStartWorker

class BootStartActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_RETRY -> StellarReceiverStarter.start(context)
                    ACTION_CANCEL -> {
                        AdbStartWorker.cancel(context)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_RETRY = "roro.stellar.manager.action.BOOT_START_RETRY"
        const val ACTION_CANCEL = "roro.stellar.manager.action.BOOT_START_CANCEL"
    }
}
