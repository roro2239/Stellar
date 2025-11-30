package roro.stellar.manager.utils

import android.util.Log
import java.util.Locale

class Logger(
    private val tag: String?
) {
    fun isLoggable(tag: String?, level: Int): Boolean {
        return true
    }

    fun v(msg: String) {
        if (isLoggable(tag, Log.VERBOSE)) {
            Log.v(tag, msg)
        }
    }

    fun v(fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.VERBOSE)) {
            val msg = String.format(Locale.ENGLISH, fmt, *args)
            Log.v(tag, msg)
        }
    }

    fun v(msg: String?, tr: Throwable?) {
        if (isLoggable(tag, Log.VERBOSE)) {
            Log.v(tag, msg, tr)
        }
    }

    fun d(msg: String) {
        if (isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, msg)
        }
    }

    fun d(fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.DEBUG)) {
            val msg = String.format(Locale.ENGLISH, fmt, *args)
            Log.d(tag, msg)
        }
    }

    fun d(msg: String?, tr: Throwable?) {
        if (isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, msg, tr)
        }
    }

    fun i(msg: String) {
        if (isLoggable(tag, Log.INFO)) {
            Log.i(tag, msg)
        }
    }

    fun i(fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.INFO)) {
            val msg = String.format(Locale.ENGLISH, fmt, *args)
            Log.i(tag, msg)
        }
    }

    fun i(msg: String?, tr: Throwable?) {
        if (isLoggable(tag, Log.INFO)) {
            Log.i(tag, msg, tr)
        }
    }

    fun w(msg: String) {
        if (isLoggable(tag, Log.WARN)) {
            Log.w(tag, msg)
        }
    }

    fun w(fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.WARN)) {
            val msg = String.format(Locale.ENGLISH, fmt, *args)
            Log.w(tag, msg)
        }
    }

    fun w(tr: Throwable?, fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.WARN)) {
            val msg = String.format(Locale.ENGLISH, fmt, *args)
            Log.w(tag, msg, tr)
        }
    }

    fun w(msg: String?, tr: Throwable?) {
        if (isLoggable(tag, Log.WARN)) {
            Log.w(tag, msg, tr)
        }
    }

    fun e(msg: String) {
        if (isLoggable(tag, Log.ERROR)) {
            Log.e(tag, msg)
        }
    }

    fun e(fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.ERROR)) {
            val msg = String.format(Locale.ENGLISH, fmt, *args)
            Log.e(tag, msg)
        }
    }

    fun e(msg: String?, tr: Throwable?) {
        if (isLoggable(tag, Log.ERROR)) {
            Log.e(tag, msg, tr)
        }
    }

    fun e(tr: Throwable?, fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.ERROR)) {
            val msg = String.format(Locale.ENGLISH, fmt, *args)
            Log.e(tag, msg, tr)
        }
    }

    companion object {
        val LOGGER: Logger = Logger("StellarManager")
    }
}