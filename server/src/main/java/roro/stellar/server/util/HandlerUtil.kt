package roro.stellar.server.util

import android.os.Handler

/**
 * Handler工具类
 * Handler Utility Class
 *
 *
 * 功能说明 Features：
 *
 *  * 管理主线程Handler - Manages main thread Handler
 *  * 提供全局Handler访问 - Provides global Handler access
 *
 */
object HandlerUtil {
    /** 主线程Handler Main thread Handler  */
    lateinit var mainHandler: Handler
}