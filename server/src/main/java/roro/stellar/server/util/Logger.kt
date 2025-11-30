package roro.stellar.server.util

import android.util.Log
import java.io.IOException
import java.util.Locale
import java.util.logging.FileHandler
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

/**
 * 日志工具类
 * Logger Utility Class
 *
 *
 * 功能说明 Features：
 *
 *  * 封装Android Log API - Wraps Android Log API
 *  * 支持格式化日志输出 - Supports formatted log output
 *  * 支持文件日志记录 - Supports file logging
 *  * 提供所有日志级别 - Provides all log levels (v/d/i/w/e)
 *
 *
 *
 * 使用示例 Usage Example：
 * <pre>
 * private static final Logger LOGGER = new Logger("MyTag");
 * LOGGER.i("Info message");
 * LOGGER.w("Warning: %s", reason);
 * LOGGER.e(exception, "Error occurred");
</pre> *
 *
 *
 * 日志级别 Log Levels：
 *
 *  * v() - Verbose（详细）
 *  * d() - Debug（调试）
 *  * i() - Info（信息）
 *  * w() - Warning（警告）
 *  * e() - Error（错误）
 *
 */
class Logger {
    /** 日志标签 Log tag  */
    private val tag: String?

    /** 文件日志记录器（可选） File logger (optional)  */
    private val LOGGER: Logger?

    /**
     * 构造日志记录器（仅控制台输出）
     * Construct logger (console only)
     *
     * @param tag 日志标签
     */
    constructor(tag: String?) {
        this.tag = tag
        this.LOGGER = null
    }

    /**
     * 构造日志记录器（支持文件输出）
     * Construct logger (with file output)
     *
     * @param tag 日志标签
     * @param file 日志文件路径
     */
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

    /**
     * 检查是否可以记录指定级别的日志
     * Check if logging is enabled for specified level
     *
     * @param tag 日志标签
     * @param level 日志级别
     * @return 总是返回true（默认所有级别都记录）
     */
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
        return Log.println(priority, tag, msg)
    }
}