package roro.stellar.manager.utils

import android.util.Log
import java.util.Locale

/**
 * 日志工具类
 * Logger Utility Class
 *
 *
 * 功能说明 Features：
 *
 *  * 封装Android Log API - Wraps Android Log API
 *  * 支持格式化日志输出 - Supports formatted log output
 *  * 提供所有日志级别 - Provides all log levels (v/d/i/w/e)
 *
 */
class Logger
/**
 * 构造日志记录器
 * Construct logger
 *
 * @param tag 日志标签
 */(
    /** 日志标签 Log tag  */
    private val tag: String?
) {
    /**
     * 检查是否可以记录指定级别的日志
     * Check if logging is enabled for specified level
     *
     * @param tag 日志标签
     * @param level 日志级别
     * @return 总是返回true
     */
    fun isLoggable(tag: String?, level: Int): Boolean {
        return true
    }

    /** 输出Verbose级别日志 Log verbose message  */
    fun v(msg: String) {
        if (isLoggable(tag, Log.VERBOSE)) {
            Log.v(tag, msg)
            LogFileManager.getInstance().writeLog("V", tag, msg, null)
        }
    }

    /** 输出格式化Verbose级别日志 Log formatted verbose message  */
    fun v(fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.VERBOSE)) {
            val msg = String.format(Locale.ENGLISH, fmt, *args)
            Log.v(tag, msg)
            LogFileManager.getInstance().writeLog("V", tag, msg, null)
        }
    }

    /** 输出Verbose级别日志（带异常） Log verbose message with throwable  */
    fun v(msg: String?, tr: Throwable?) {
        if (isLoggable(tag, Log.VERBOSE)) {
            Log.v(tag, msg, tr)
            LogFileManager.getInstance().writeLog("V", tag, msg, tr)
        }
    }

    /** 输出Debug级别日志 Log debug message  */
    fun d(msg: String) {
        if (isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, msg)
            LogFileManager.getInstance().writeLog("D", tag, msg, null)
        }
    }

    /** 输出格式化Debug级别日志 Log formatted debug message  */
    fun d(fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.DEBUG)) {
            val msg = String.format(Locale.ENGLISH, fmt, *args)
            Log.d(tag, msg)
            LogFileManager.getInstance().writeLog("D", tag, msg, null)
        }
    }

    /** 输出Debug级别日志（带异常） Log debug message with throwable  */
    fun d(msg: String?, tr: Throwable?) {
        if (isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, msg, tr)
            LogFileManager.getInstance().writeLog("D", tag, msg, tr)
        }
    }

    /** 输出Info级别日志 Log info message  */
    fun i(msg: String) {
        if (isLoggable(tag, Log.INFO)) {
            Log.i(tag, msg)
            LogFileManager.getInstance().writeLog("I", tag, msg, null)
        }
    }

    /** 输出格式化Info级别日志 Log formatted info message  */
    fun i(fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.INFO)) {
            val msg = String.format(Locale.ENGLISH, fmt, *args)
            Log.i(tag, msg)
            LogFileManager.getInstance().writeLog("I", tag, msg, null)
        }
    }

    /** 输出Info级别日志（带异常） Log info message with throwable  */
    fun i(msg: String?, tr: Throwable?) {
        if (isLoggable(tag, Log.INFO)) {
            Log.i(tag, msg, tr)
            LogFileManager.getInstance().writeLog("I", tag, msg, tr)
        }
    }

    /** 输出Warning级别日志 Log warning message  */
    fun w(msg: String) {
        if (isLoggable(tag, Log.WARN)) {
            Log.w(tag, msg)
            LogFileManager.getInstance().writeLog("W", tag, msg, null)
        }
    }

    /** 输出格式化Warning级别日志 Log formatted warning message  */
    fun w(fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.WARN)) {
            val msg = String.format(Locale.ENGLISH, fmt, *args)
            Log.w(tag, msg)
            LogFileManager.getInstance().writeLog("W", tag, msg, null)
        }
    }

    /** 输出Warning级别日志（带异常和格式化） Log warning message with throwable and format  */
    fun w(tr: Throwable?, fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.WARN)) {
            val msg = String.format(Locale.ENGLISH, fmt, *args)
            Log.w(tag, msg, tr)
            LogFileManager.getInstance().writeLog("W", tag, msg, tr)
        }
    }

    /** 输出Warning级别日志（带异常） Log warning message with throwable  */
    fun w(msg: String?, tr: Throwable?) {
        if (isLoggable(tag, Log.WARN)) {
            Log.w(tag, msg, tr)
            LogFileManager.getInstance().writeLog("W", tag, msg, tr)
        }
    }

    /** 输出Error级别日志 Log error message  */
    fun e(msg: String) {
        if (isLoggable(tag, Log.ERROR)) {
            Log.e(tag, msg)
            LogFileManager.getInstance().writeLog("E", tag, msg, null)
        }
    }

    /** 输出格式化Error级别日志 Log formatted error message  */
    fun e(fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.ERROR)) {
            val msg = String.format(Locale.ENGLISH, fmt, *args)
            Log.e(tag, msg)
            LogFileManager.getInstance().writeLog("E", tag, msg, null)
        }
    }

    /** 输出Error级别日志（带异常） Log error message with throwable  */
    fun e(msg: String?, tr: Throwable?) {
        if (isLoggable(tag, Log.ERROR)) {
            Log.e(tag, msg, tr)
            LogFileManager.getInstance().writeLog("E", tag, msg, tr)
        }
    }

    /** 输出Error级别日志（带异常和格式化） Log error message with throwable and format  */
    fun e(tr: Throwable?, fmt: String, vararg args: Any?) {
        if (isLoggable(tag, Log.ERROR)) {
            val msg = String.format(Locale.ENGLISH, fmt, *args)
            Log.e(tag, msg, tr)
            LogFileManager.getInstance().writeLog("E", tag, msg, tr)
        }
    }

    companion object {
        /** 全局日志实例 Global logger instance  */
        val LOGGER: Logger = Logger("StellarManager")
    }
}