package roro.stellar.server.ktx

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

/**
 * Handler扩展工具
 * Handler Extension Utilities
 * 
 * 功能说明 Features：
 * - 提供全局主线程Handler - Provides global main thread Handler
 * - 提供全局工作线程Handler - Provides global worker thread Handler
 * - 使用懒加载优化性能 - Uses lazy loading for performance
 * 
 * 使用场景 Use Cases：
 * - 在主线程执行UI更新
 * - 在后台线程执行耗时操作
 */

/**
 * 主线程Handler
 * Main thread Handler
 * 
 * 懒加载创建，用于在主线程执行任务
 * Lazily created, used to execute tasks on main thread
 */
val mainHandler by lazy {
    Handler(Looper.getMainLooper())
}

/**
 * 工作线程（内部使用）
 * Worker thread (internal use)
 * 
 * 懒加载创建，线程不安全模式（仅在单线程初始化）
 */
private val workerThread by lazy(LazyThreadSafetyMode.NONE) {
    HandlerThread("Worker").apply { start() }
}

/**
 * 工作线程Handler
 * Worker thread Handler
 * 
 * 懒加载创建，用于在后台线程执行任务
 * Lazily created, used to execute tasks on background thread
 */
val workerHandler by lazy {
    Handler(workerThread.looper)
}

