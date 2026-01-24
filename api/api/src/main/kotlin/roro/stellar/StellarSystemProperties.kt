package roro.stellar

import android.os.RemoteException

object StellarSystemProperties {
    @Throws(RemoteException::class)
    fun get(key: String?): String {
        return Stellar.requireService().getSystemProperty(key, null)
    }

    @Throws(RemoteException::class)
    fun get(key: String?, def: String?): String {
        return Stellar.requireService().getSystemProperty(key, def)
    }

    @Throws(RemoteException::class)
    fun getInt(key: String?, def: Int): Int {
        return Stellar.requireService().getSystemProperty(key, def.toString()).toInt()
    }

    @Throws(RemoteException::class)
    fun getLong(key: String?, def: Long): Long {
        return Stellar.requireService().getSystemProperty(key, def.toString()).toLong()
    }

    @Throws(RemoteException::class)
    fun getBoolean(key: String?, def: Boolean): Boolean {
        return Stellar.requireService().getSystemProperty(key, def.toString()).toBoolean()
    }

    @Throws(RemoteException::class)
    fun set(key: String?, value: String?) {
        Stellar.requireService().setSystemProperty(key, value)
    }
}
