package roro.stellar.server.util

import android.os.Handler
import roro.stellar.server.util.HandlerUtil.mainHandler
import java.util.Objects

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
    /**
     * 设置主线程Handler
     * Set main thread Handler
     *
     * @param mainHandler 主线程Handler
     */
    /** 主线程Handler Main thread Handler  */
    var mainHandler: Handler? = null
        /**
         * 获取主线程Handler
         * Get main thread Handler
         *
         * @return 主线程Handler
         * @throws NullPointerException 如果尚未调用setMainHandler
         */
        get() {
            Objects.requireNonNull<Handler?>(field, "Please call setMainHandler first")
            return field
        }
}