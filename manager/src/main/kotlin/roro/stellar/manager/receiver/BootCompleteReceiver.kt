package roro.stellar.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.startup.worker.AdbStartWorker

class BootCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val mode = StellarSettings.getBootMode()
        when (mode) {
            StellarSettings.BootMode.BROADCAST -> StellarReceiverStarter.start(context)
            StellarSettings.BootMode.TCPIP_PREWARM -> AdbStartWorker.enqueuePrepareTcpip(context)
            StellarSettings.BootMode.NONE,
            StellarSettings.BootMode.SCRIPT -> return
        }
    }
}
