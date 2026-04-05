package roro.stellar.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import roro.stellar.manager.model.ServiceStatus

class StellarReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ServiceStatus().isRunning) {
            StellarReceiverStarter.start(context, forceStart = true)
        }
    }
}
