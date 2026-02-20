package roro.stellar.server.util

import android.os.Bundle
import android.os.IBinder
import android.util.Log
import rikka.hidden.compat.ActivityManagerApis
import roro.stellar.server.api.IContentProviderUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

data class LogEntry(
    val timestamp: Long,
    val level: Int,
    val tag: String?,
    val message: String
) {
    fun getLevelName(): String = when (level) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        else -> "?"
    }

    fun format(): String {
        val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return "${dateFormat.format(Date(timestamp))} ${getLevelName()}/$tag: $message"
    }
}

class Logger(private val tag: String?) {

    companion object {
        private const val MAX_LOG_ENTRIES = 500
        private const val PROVIDER = "roro.stellar.manager.stellar"
        private val logBuffer = CopyOnWriteArrayList<LogEntry>()
        private val executor = Executors.newSingleThreadExecutor()
        private var providerReady = false

        fun initProvider() {
            providerReady = true
            executor.execute {
                callProvider("clearLogs", null)
                logBuffer.forEach { entry -> callProvider("saveLog", entry.format()) }
            }
        }

        private fun callProvider(method: String, arg: String?): Bundle? {
            val token: IBinder? = null
            var provider: android.content.IContentProvider? = null
            return try {
                provider = ActivityManagerApis.getContentProviderExternal(PROVIDER, 0, token, PROVIDER)
                    ?: return null
                IContentProviderUtils.callCompat(provider, null, PROVIDER, method, arg, Bundle())
            } catch (_: Throwable) {
                null
            } finally {
                if (provider != null) {
                    try { ActivityManagerApis.removeContentProviderExternal(PROVIDER, token) } catch (_: Throwable) {}
                }
            }
        }

        @JvmStatic
        fun getLogs(): List<LogEntry> = logBuffer.toList()

        @JvmStatic
        fun getLogsFormatted(): List<String> = logBuffer.map { it.format() }

        @JvmStatic
        fun clearLogs() {
            logBuffer.clear()
            if (!providerReady) return
            executor.execute { callProvider("clearLogs", null) }
        }

        @JvmStatic
        internal fun addLog(level: Int, tag: String?, message: String) {
            val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
            logBuffer.add(entry)
            while (logBuffer.size > MAX_LOG_ENTRIES) logBuffer.removeAt(0)
            if (!providerReady) return
            executor.execute { callProvider("saveLog", entry.format()) }
        }
    }

    fun v(msg: String) = println(Log.VERBOSE, msg)
    fun v(fmt: String, vararg args: Any?) = println(Log.VERBOSE, String.format(Locale.ENGLISH, fmt, *args))
    fun v(msg: String?, tr: Throwable?) = println(Log.VERBOSE, msg + '\n' + Log.getStackTraceString(tr))

    fun d(msg: String) = println(Log.DEBUG, msg)
    fun d(fmt: String, vararg args: Any?) = println(Log.DEBUG, String.format(Locale.ENGLISH, fmt, *args))
    fun d(msg: String?, tr: Throwable?) = println(Log.DEBUG, msg + '\n' + Log.getStackTraceString(tr))

    fun i(msg: String) = println(Log.INFO, msg)
    fun i(fmt: String, vararg args: Any?) = println(Log.INFO, String.format(Locale.ENGLISH, fmt, *args))
    fun i(msg: String?, tr: Throwable?) = println(Log.INFO, msg + '\n' + Log.getStackTraceString(tr))

    fun w(msg: String) = println(Log.WARN, msg)
    fun w(fmt: String, vararg args: Any?) = println(Log.WARN, String.format(Locale.ENGLISH, fmt, *args))
    fun w(tr: Throwable?, fmt: String, vararg args: Any?) = println(Log.WARN, String.format(Locale.ENGLISH, fmt, *args) + '\n' + Log.getStackTraceString(tr))
    fun w(msg: String?, tr: Throwable?) = println(Log.WARN, msg + '\n' + Log.getStackTraceString(tr))

    fun e(msg: String) = println(Log.ERROR, msg)
    fun e(fmt: String, vararg args: Any?) = println(Log.ERROR, String.format(Locale.ENGLISH, fmt, *args))
    fun e(msg: String?, tr: Throwable?) = println(Log.ERROR, msg + '\n' + Log.getStackTraceString(tr))
    fun e(tr: Throwable?, fmt: String, vararg args: Any?) = println(Log.ERROR, String.format(Locale.ENGLISH, fmt, *args) + '\n' + Log.getStackTraceString(tr))

    fun println(priority: Int, msg: String): Int {
        addLog(priority, tag, msg)
        return Log.println(priority, tag, msg)
    }
}
