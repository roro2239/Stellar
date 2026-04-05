package roro.stellar.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import roro.stellar.manager.StellarSettings

class BootCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val mode = StellarSettings.getBootMode()
        if (mode == StellarSettings.BootMode.NONE || mode == StellarSettings.BootMode.SCRIPT) return

        StellarReceiverStarter.start(context)
    }
}
