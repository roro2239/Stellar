package com.stellar.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import roro.stellar.Stellar

class FollowStellarStartup: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("StellarDemo", "${intent?.action}")
        Stellar.newProcess(
            arrayOf("touch", "/sdcard/test.log"),
            null,
            null
        )
    }
}