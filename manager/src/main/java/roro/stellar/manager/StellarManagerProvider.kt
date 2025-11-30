package roro.stellar.manager

import android.os.Bundle
import roro.stellar.StellarProvider

class StellarManagerProvider : StellarProvider() {

    override fun onCreate(): Boolean {
        return super.onCreate()
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (extras == null) return null
        return super.call(method, arg, extras)
    }
}

