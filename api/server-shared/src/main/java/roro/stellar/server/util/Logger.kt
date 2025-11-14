package roro.stellar.server.util;

import android.util.Log;

import java.io.IOException;
import java.util.Locale;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

/**
 * 日志工具类
 * Logger Utility Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>封装Android Log API - Wraps Android Log API</li>
 * <li>支持格式化日志输出 - Supports formatted log output</li>
 * <li>支持文件日志记录 - Supports file logging</li>
 * <li>提供所有日志级别 - Provides all log levels (v/d/i/w/e)</li>
 * </ul>
 * 
 * <p>使用示例 Usage Example：</p>
 * <pre>
 * private static final Logger LOGGER = new Logger("MyTag");
 * LOGGER.i("Info message");
 * LOGGER.w("Warning: %s", reason);
 * LOGGER.e(exception, "Error occurred");
 * </pre>
 * 
 * <p>日志级别 Log Levels：</p>
 * <ul>
 * <li>v() - Verbose（详细）</li>
 * <li>d() - Debug（调试）</li>
 * <li>i() - Info（信息）</li>
 * <li>w() - Warning（警告）</li>
 * <li>e() - Error（错误）</li>
 * </ul>
 */
public class Logger {

    /** 日志标签 Log tag */
    private final String tag;
    
    /** 文件日志记录器（可选） File logger (optional) */
    private final java.util.logging.Logger LOGGER;

    /**
     * 构造日志记录器（仅控制台输出）
     * Construct logger (console only)
     * 
     * @param tag 日志标签
     */
    public Logger(String tag) {
        this.tag = tag;
        this.LOGGER = null;
    }

    /**
     * 构造日志记录器（支持文件输出）
     * Construct logger (with file output)
     * 
     * @param tag 日志标签
     * @param file 日志文件路径
     */
    public Logger(String tag, String file) {
        this.tag = tag;
        this.LOGGER = java.util.logging.Logger.getLogger(tag);
        try {
            FileHandler fh = new FileHandler(file);
            fh.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fh);
        } catch (IOException e) {
            e.printStackTrace();
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
    public boolean isLoggable(String tag, int level) {
        return true;
    }

    public void v(String msg) {
        if (isLoggable(tag, Log.VERBOSE)) {
            println(Log.VERBOSE, msg);
        }
    }

    public void v(String fmt, Object... args) {
        if (isLoggable(tag, Log.VERBOSE)) {
            println(Log.VERBOSE, String.format(Locale.ENGLISH, fmt, args));
        }
    }

    public void v(String msg, Throwable tr) {
        if (isLoggable(tag, Log.VERBOSE)) {
            println(Log.VERBOSE, msg + '\n' + Log.getStackTraceString(tr));
        }
    }

    public void d(String msg) {
        if (isLoggable(tag, Log.DEBUG)) {
            println(Log.DEBUG, msg);
        }
    }

    public void d(String fmt, Object... args) {
        if (isLoggable(tag, Log.DEBUG)) {
            println(Log.DEBUG, String.format(Locale.ENGLISH, fmt, args));
        }
    }

    public void d(String msg, Throwable tr) {
        if (isLoggable(tag, Log.DEBUG)) {
            println(Log.DEBUG, msg + '\n' + Log.getStackTraceString(tr));
        }
    }

    public void i(String msg) {
        if (isLoggable(tag, Log.INFO)) {
            println(Log.INFO, msg);
        }
    }

    public void i(String fmt, Object... args) {
        if (isLoggable(tag, Log.INFO)) {
            println(Log.INFO, String.format(Locale.ENGLISH, fmt, args));
        }
    }

    public void i(String msg, Throwable tr) {
        if (isLoggable(tag, Log.INFO)) {
            println(Log.INFO, msg + '\n' + Log.getStackTraceString(tr));
        }
    }

    public void w(String msg) {
        if (isLoggable(tag, Log.WARN)) {
            println(Log.WARN, msg);
        }
    }

    public void w(String fmt, Object... args) {
        if (isLoggable(tag, Log.WARN)) {
            println(Log.WARN, String.format(Locale.ENGLISH, fmt, args));
        }
    }

    public void w(Throwable tr, String fmt, Object... args) {
        if (isLoggable(tag, Log.WARN)) {
            println(Log.WARN, String.format(Locale.ENGLISH, fmt, args) + '\n' + Log.getStackTraceString(tr));
        }
    }

    public void w(String msg, Throwable tr) {
        if (isLoggable(tag, Log.WARN)) {
            println(Log.WARN, msg + '\n' + Log.getStackTraceString(tr));
        }
    }

    public void e(String msg) {
        if (isLoggable(tag, Log.ERROR)) {
            println(Log.ERROR, msg);
        }
    }

    public void e(String fmt, Object... args) {
        if (isLoggable(tag, Log.ERROR)) {
            println(Log.ERROR, String.format(Locale.ENGLISH, fmt, args));
        }
    }

    public void e(String msg, Throwable tr) {
        if (isLoggable(tag, Log.ERROR)) {
            println(Log.ERROR, msg + '\n' + Log.getStackTraceString(tr));
        }
    }

    public void e(Throwable tr, String fmt, Object... args) {
        if (isLoggable(tag, Log.ERROR)) {
            println(Log.ERROR, String.format(Locale.ENGLISH, fmt, args) + '\n' + Log.getStackTraceString(tr));
        }
    }

    public int println(int priority, String msg) {
        if (LOGGER != null) {
            LOGGER.info(msg);
        }
        return Log.println(priority, tag, msg);
    }
}

