package roro.stellar.server.util;

import android.os.Handler;
import android.os.Looper;

import java.util.Objects;

/**
 * Handler工具类
 * Handler Utility Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>管理主线程Handler - Manages main thread Handler</li>
 * <li>提供全局Handler访问 - Provides global Handler access</li>
 * </ul>
 */
public class HandlerUtil {

    /** 主线程Handler Main thread Handler */
    private static Handler mainHandler;

    /**
     * 设置主线程Handler
     * Set main thread Handler
     * 
     * @param mainHandler 主线程Handler
     */
    public static void setMainHandler(Handler mainHandler) {
        HandlerUtil.mainHandler = mainHandler;
    }

    /**
     * 获取主线程Handler
     * Get main thread Handler
     * 
     * @return 主线程Handler
     * @throws NullPointerException 如果尚未调用setMainHandler
     */
    public static Handler getMainHandler() {
        Objects.requireNonNull(mainHandler, "Please call setMainHandler first");
        return HandlerUtil.mainHandler;
    }
}

