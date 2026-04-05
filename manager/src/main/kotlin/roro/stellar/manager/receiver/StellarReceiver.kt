package roro.stellar.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import roro.stellar.manager.model.ServiceStatus

class StellarReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (ServiceStatus().isRunning) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                StellarReceiverStarter.start(context, forceStart = true)
            } finally {
                pending.finish()
            }
        }
    }
}
