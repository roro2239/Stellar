package roro.stellar.manager.utils;

import android.util.Log;

import java.util.Locale;

/**
 * 日志工具类
 * Logger Utility Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>封装Android Log API - Wraps Android Log API</li>
 * <li>支持格式化日志输出 - Supports formatted log output</li>
 * <li>提供所有日志级别 - Provides all log levels (v/d/i/w/e)</li>
 * </ul>
 */
public class Logger {

    /** 全局日志实例 Global logger instance */
    public static final Logger LOGGER = new Logger("StellarManager");

    /** 日志标签 Log tag */
    private String TAG;

    /**
     * 构造日志记录器
     * Construct logger
     * 
     * @param TAG 日志标签
     */
    public Logger(String TAG) {
        this.TAG = TAG;
    }

    /**
     * 检查是否可以记录指定级别的日志
     * Check if logging is enabled for specified level
     * 
     * @param tag 日志标签
     * @param level 日志级别
     * @return 总是返回true
     */
    public boolean isLoggable(String tag, int level) {
        return true;
    }

    /** 输出Verbose级别日志 Log verbose message */
    public void v(String msg) {
        if (isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, msg);
            LogFileManager.getInstance().writeLog("V", TAG, msg, null);
        }
    }

    /** 输出格式化Verbose级别日志 Log formatted verbose message */
    public void v(String fmt, Object... args) {
        if (isLoggable(TAG, Log.VERBOSE)) {
            String msg = String.format(Locale.ENGLISH, fmt, args);
            Log.v(TAG, msg);
            LogFileManager.getInstance().writeLog("V", TAG, msg, null);
        }
    }

    /** 输出Verbose级别日志（带异常） Log verbose message with throwable */
    public void v(String msg, Throwable tr) {
        if (isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, msg, tr);
            LogFileManager.getInstance().writeLog("V", TAG, msg, tr);
        }
    }

    /** 输出Debug级别日志 Log debug message */
    public void d(String msg) {
        if (isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, msg);
            LogFileManager.getInstance().writeLog("D", TAG, msg, null);
        }
    }

    /** 输出格式化Debug级别日志 Log formatted debug message */
    public void d(String fmt, Object... args) {
        if (isLoggable(TAG, Log.DEBUG)) {
            String msg = String.format(Locale.ENGLISH, fmt, args);
            Log.d(TAG, msg);
            LogFileManager.getInstance().writeLog("D", TAG, msg, null);
        }
    }

    /** 输出Debug级别日志（带异常） Log debug message with throwable */
    public void d(String msg, Throwable tr) {
        if (isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, msg, tr);
            LogFileManager.getInstance().writeLog("D", TAG, msg, tr);
        }
    }

    /** 输出Info级别日志 Log info message */
    public void i(String msg) {
        if (isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, msg);
            LogFileManager.getInstance().writeLog("I", TAG, msg, null);
        }
    }

    /** 输出格式化Info级别日志 Log formatted info message */
    public void i(String fmt, Object... args) {
        if (isLoggable(TAG, Log.INFO)) {
            String msg = String.format(Locale.ENGLISH, fmt, args);
            Log.i(TAG, msg);
            LogFileManager.getInstance().writeLog("I", TAG, msg, null);
        }
    }

    /** 输出Info级别日志（带异常） Log info message with throwable */
    public void i(String msg, Throwable tr) {
        if (isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, msg, tr);
            LogFileManager.getInstance().writeLog("I", TAG, msg, tr);
        }
    }

    /** 输出Warning级别日志 Log warning message */
    public void w(String msg) {
        if (isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, msg);
            LogFileManager.getInstance().writeLog("W", TAG, msg, null);
        }
    }

    /** 输出格式化Warning级别日志 Log formatted warning message */
    public void w(String fmt, Object... args) {
        if (isLoggable(TAG, Log.WARN)) {
            String msg = String.format(Locale.ENGLISH, fmt, args);
            Log.w(TAG, msg);
            LogFileManager.getInstance().writeLog("W", TAG, msg, null);
        }
    }

    /** 输出Warning级别日志（带异常和格式化） Log warning message with throwable and format */
    public void w(Throwable tr, String fmt, Object... args) {
        if (isLoggable(TAG, Log.WARN)) {
            String msg = String.format(Locale.ENGLISH, fmt, args);
            Log.w(TAG, msg, tr);
            LogFileManager.getInstance().writeLog("W", TAG, msg, tr);
        }
    }

    /** 输出Warning级别日志（带异常） Log warning message with throwable */
    public void w(String msg, Throwable tr) {
        if (isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, msg, tr);
            LogFileManager.getInstance().writeLog("W", TAG, msg, tr);
        }
    }

    /** 输出Error级别日志 Log error message */
    public void e(String msg) {
        if (isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, msg);
            LogFileManager.getInstance().writeLog("E", TAG, msg, null);
        }
    }

    /** 输出格式化Error级别日志 Log formatted error message */
    public void e(String fmt, Object... args) {
        if (isLoggable(TAG, Log.ERROR)) {
            String msg = String.format(Locale.ENGLISH, fmt, args);
            Log.e(TAG, msg);
            LogFileManager.getInstance().writeLog("E", TAG, msg, null);
        }
    }

    /** 输出Error级别日志（带异常） Log error message with throwable */
    public void e(String msg, Throwable tr) {
        if (isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, msg, tr);
            LogFileManager.getInstance().writeLog("E", TAG, msg, tr);
        }
    }

    /** 输出Error级别日志（带异常和格式化） Log error message with throwable and format */
    public void e(Throwable tr, String fmt, Object... args) {
        if (isLoggable(TAG, Log.ERROR)) {
            String msg = String.format(Locale.ENGLISH, fmt, args);
            Log.e(TAG, msg, tr);
            LogFileManager.getInstance().writeLog("E", TAG, msg, tr);
        }
    }
}

