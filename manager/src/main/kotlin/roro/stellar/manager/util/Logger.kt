package roro.stellar.manager.util

import android.util.Log
import java.util.Locale

class Logger(
    private val tag: String?
) {
    fun d(msg: String) {
        Log.d(tag, msg)
    }

    fun w(msg: String) {
        Log.w(tag, msg)
    }

    fun w(msg: String?, tr: Throwable?) {
        Log.w(tag, msg, tr)
    }

    fun w(tr: Throwable?, fmt: String, vararg args: Any?) {
        val msg = String.format(Locale.ENGLISH, fmt, *args)
        Log.w(tag, msg, tr)
    }

    fun e(msg: String) {
        Log.e(tag, msg)
    }

    fun e(msg: String?, tr: Throwable?) {
        Log.e(tag, msg, tr)
    }

    companion object {
        val LOGGER: Logger = Logger("StellarManager")
    }
}