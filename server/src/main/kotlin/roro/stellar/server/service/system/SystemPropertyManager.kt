package roro.stellar.server.service.system

import android.os.SystemProperties

class SystemPropertyManager {
    fun getSystemProperty(name: String, defaultValue: String): String {
        return try {
            SystemProperties.get(name, defaultValue)
        } catch (tr: Throwable) {
            throw IllegalStateException(tr.message)
        }
    }

    fun setSystemProperty(name: String, value: String) {
        try {
            SystemProperties.set(name, value)
        } catch (tr: Throwable) {
            throw IllegalStateException(tr.message)
        }
    }
}
