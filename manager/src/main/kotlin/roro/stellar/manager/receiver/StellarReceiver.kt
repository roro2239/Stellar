package roro.stellar.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import roro.stellar.manager.model.ServiceStatus
import roro.stellar.manager.startup.worker.AdbStartWorker

class StellarReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ServiceStatus().isRunning) {
            AdbStartWorker.enqueue(context)
        }
    }
}
