/**
 * 日志扩展函数
 * Log Extension Functions
 * 
 * 功能说明 Features：
 * - 为任意类型提供便捷的日志输出方法 - Provides convenient log output methods for any type
 * - 自动生成日志标签 - Auto generates log tags
 * - 支持所有日志级别 - Supports all log levels (v/d/i/w/e)
 * - 限制标签长度符合Android规范 - Limits tag length to Android spec
 * 
 * 使用示例 Usage Example：
 * ```kotlin
 * class MyClass {
 *     fun test() {
 *         logi("This is an info log")
 *         loge("Error occurred", exception)
 *     }
 * }
 * ```
 */
@file:Suppress("NOTHING_TO_INLINE")

package roro.stellar.manager.ktx

import android.util.Log

/**
 * 获取当前类的日志标签
 * Get log tag for current class
 * 
 * 自动从类名生成，最长23字符（Android限制）
 * Auto generated from class name, max 23 chars (Android limit)
 */
inline val <reified T> T.TAG: String
    get() = T::class.java.simpleName.let {
        if (it.isBlank()) throw IllegalStateException("标签为空")
        if (it.length > 23) it.substring(0, 23) else it
    }

/** Verbose级别日志（使用自动标签） Verbose level log (with auto tag) */
inline fun <reified T> T.logv(message: String, throwable: Throwable? = null) = logv(TAG, message, throwable)
/** Info级别日志（使用自动标签） Info level log (with auto tag) */
inline fun <reified T> T.logi(message: String, throwable: Throwable? = null) = logi(TAG, message, throwable)
/** Warning级别日志（使用自动标签） Warning level log (with auto tag) */
inline fun <reified T> T.logw(message: String, throwable: Throwable? = null) = logw(TAG, message, throwable)
/** Debug级别日志（使用自动标签） Debug level log (with auto tag) */
inline fun <reified T> T.logd(message: String, throwable: Throwable? = null) = logd(TAG, message, throwable)
/** Error级别日志（使用自动标签） Error level log (with auto tag) */
inline fun <reified T> T.loge(message: String, throwable: Throwable? = null) = loge(TAG, message, throwable)

/** Verbose级别日志（指定标签） Verbose level log (with custom tag) */
inline fun <reified T> T.logv(tag: String, message: String, throwable: Throwable? = null) {
    Log.v(tag, message, throwable)
    roro.stellar.manager.utils.LogFileManager.getInstance().writeLog("V", tag, message, throwable)
}
/** Info级别日志（指定标签） Info level log (with custom tag) */
inline fun <reified T> T.logi(tag: String, message: String, throwable: Throwable? = null) {
    Log.i(tag, message, throwable)
    roro.stellar.manager.utils.LogFileManager.getInstance().writeLog("I", tag, message, throwable)
}
/** Warning级别日志（指定标签） Warning level log (with custom tag) */
inline fun <reified T> T.logw(tag: String, message: String, throwable: Throwable? = null) {
    Log.w(tag, message, throwable)
    roro.stellar.manager.utils.LogFileManager.getInstance().writeLog("W", tag, message, throwable)
}
/** Debug级别日志（指定标签） Debug level log (with custom tag) */
inline fun <reified T> T.logd(tag: String, message: String, throwable: Throwable? = null) {
    Log.d(tag, message, throwable)
    roro.stellar.manager.utils.LogFileManager.getInstance().writeLog("D", tag, message, throwable)
}
/** Error级别日志（指定标签） Error level log (with custom tag) */
inline fun <reified T> T.loge(tag: String, message: String, throwable: Throwable? = null) {
    Log.e(tag, message, throwable)
    roro.stellar.manager.utils.LogFileManager.getInstance().writeLog("E", tag, message, throwable)
}

