package roro.stellar.server.util

import android.util.Log
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.FileHandler
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

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

class Logger {
    private val tag: String?

    private val LOGGER: Logger?

    constructor(tag: String?) {
        this.tag = tag
        this.LOGGER = null
    }

    constructor(tag: String, file: String) {
        this.tag = tag
        this.LOGGER = Logger.getLogger(tag)
        try {
            val fh = FileHandler(file)
            fh.setFormatter(SimpleFormatter())
            LOGGER.addHandler(fh)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val MAX_LOG_ENTRIES = 500
        private val logBuffer = CopyOnWriteArrayList<LogEntry>()

        @JvmStatic
        fun getLogs(): List<LogEntry> = logBuffer.toList()

        @JvmStatic
        fun getLogsFormatted(): List<String> = logBuffer.map { it.format() }

        @JvmStatic
        fun clearLogs() {
            logBuffer.clear()
        }

        @JvmStatic
        internal fun addLog(level: Int, tag: String?, message: String) {
            val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
            logBuffer.add(entry)
            while (logBuffer.size > MAX_LOG_ENTRIES) {
                logBuffer.removeAt(0)
            }
        }
    }

    fun isLoggable(tag: String?, level: Int): Boolean {
        return true
    }

    fun v(msg: String) {
        if (isLoggable(tag, Log.VERBOSE)) {
            println(Log.VERBOSE, msg)
        }
    }

    fun v(fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.VERBOSE)) {
            println(Log.VERBOSE, String.format(Locale.ENGLISH, fmt, *args))
        }
    }

    fun v(msg: String?, tr: Throwable?) {
        if (isLoggable(tag, Log.VERBOSE)) {
            println(Log.VERBOSE, msg + '\n' + Log.getStackTraceString(tr))
        }
    }

    fun d(msg: String) {
        if (isLoggable(tag, Log.DEBUG)) {
            println(Log.DEBUG, msg)
        }
    }

    fun d(fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.DEBUG)) {
            println(Log.DEBUG, String.format(Locale.ENGLISH, fmt, *args))
        }
    }

    fun d(msg: String?, tr: Throwable?) {
        if (isLoggable(tag, Log.DEBUG)) {
            println(Log.DEBUG, msg + '\n' + Log.getStackTraceString(tr))
        }
    }

    fun i(msg: String) {
        if (isLoggable(tag, Log.INFO)) {
            println(Log.INFO, msg)
        }
    }

    fun i(fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.INFO)) {
            println(Log.INFO, String.format(Locale.ENGLISH, fmt, *args))
        }
    }

    fun i(msg: String?, tr: Throwable?) {
        if (isLoggable(tag, Log.INFO)) {
            println(Log.INFO, msg + '\n' + Log.getStackTraceString(tr))
        }
    }

    fun w(msg: String) {
        if (isLoggable(tag, Log.WARN)) {
            println(Log.WARN, msg)
        }
    }

    fun w(fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.WARN)) {
            println(Log.WARN, String.format(Locale.ENGLISH, fmt, *args))
        }
    }

    fun w(tr: Throwable?, fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.WARN)) {
            println(
                Log.WARN,
                String.format(Locale.ENGLISH, fmt, *args) + '\n' + Log.getStackTraceString(tr)
            )
        }
    }

    fun w(msg: String?, tr: Throwable?) {
        if (isLoggable(tag, Log.WARN)) {
            println(Log.WARN, msg + '\n' + Log.getStackTraceString(tr))
        }
    }

    fun e(msg: String) {
        if (isLoggable(tag, Log.ERROR)) {
            println(Log.ERROR, msg)
        }
    }

    fun e(fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.ERROR)) {
            println(Log.ERROR, String.format(Locale.ENGLISH, fmt, *args))
        }
    }

    fun e(msg: String?, tr: Throwable?) {
        if (isLoggable(tag, Log.ERROR)) {
            println(Log.ERROR, msg + '\n' + Log.getStackTraceString(tr))
        }
    }

    fun e(tr: Throwable?, fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.ERROR)) {
            println(
                Log.ERROR,
                String.format(Locale.ENGLISH, fmt, *args) + '\n' + Log.getStackTraceString(tr)
            )
        }
    }

    fun println(priority: Int, msg: String): Int {
        LOGGER?.info(msg)
        addLog(priority, tag, msg)
        return Log.println(priority, tag, msg)
    }
}