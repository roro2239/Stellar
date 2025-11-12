package roro.stellar.manager.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.widget.ImageView
import androidx.collection.LruCache
import kotlinx.coroutines.*
import me.zhanghai.android.appiconloader.AppIconLoader
import roro.stellar.manager.R
import roro.stellar.manager.compat.BuildUtils
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * 应用图标缓存
 * App Icon Cache
 * 
 * 功能说明 Features：
 * - 使用LruCache缓存应用图标 - Uses LruCache to cache app icons
 * - 异步加载应用图标 - Asynchronously loads app icons
 * - 支持自适应图标 - Supports adaptive icons
 * - 自动管理内存和线程池 - Auto manages memory and thread pool
 * 
 * 缓存策略 Cache Strategy：
 * - 缓存大小为最大内存的1/4 - Cache size is 1/4 of max memory
 * - 使用协程异步加载 - Uses coroutines for async loading
 * - 线程池大小根据CPU核心数自动调整 - Thread pool size auto adjusts based on CPU cores
 * 
 * 使用方式 Usage：
 * ```kotlin
 * AppIconCache.loadIconBitmapAsync(context, appInfo, iconSize, imageView)
 * ```
 */
object AppIconCache : CoroutineScope {

    /**
     * 应用图标LRU缓存
     * App Icon LRU Cache
     * 
     * 键为(包名, 版本号, 图标尺寸)的三元组
     * Key is triple of (packageName, versionCode, iconSize)
     */
    private class AppIconLruCache constructor(maxSize: Int) : LruCache<Triple<String, Int, Int>, Bitmap>(maxSize) {

        /**
         * 计算缓存项大小（单位：KB）
         * Calculate cache entry size (in KB)
         */
        override fun sizeOf(key: Triple<String, Int, Int>, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    /** 协程上下文 Coroutine context */
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main

    /** LRU缓存实例 LRU cache instance */
    private val lruCache: LruCache<Triple<String, Int, Int>, Bitmap>

    /** 图标加载调度器 Icon loading dispatcher */
    private val dispatcher: CoroutineDispatcher

    /** 应用图标加载器映射 App icon loader map */
    private var appIconLoaders = mutableMapOf<Int, AppIconLoader>()

    init {
        // 初始化应用图标LRU缓存
        // Initialize app icon lru cache
        val maxMemory = Runtime.getRuntime().maxMemory() / 1024
        val availableCacheSize = (maxMemory / 4).toInt()
        lruCache = AppIconLruCache(availableCacheSize)

        // 初始化图标加载线程池
        // Initialize load icon scheduler
        val availableProcessorsCount = try {
            Runtime.getRuntime().availableProcessors()
        } catch (ignored: Exception) {
            1
        }
        val threadCount = 1.coerceAtLeast(availableProcessorsCount / 2)
        val loadIconExecutor: Executor = Executors.newFixedThreadPool(threadCount)
        dispatcher = loadIconExecutor.asCoroutineDispatcher()
    }

    /**
     * 获取协程调度器
     * Get coroutine dispatcher
     * 
     * @return 用于加载图标的协程调度器
     */
    fun dispatcher(): CoroutineDispatcher {
        return dispatcher
    }

    /**
     * 从缓存获取图标
     * Get icon from cache
     * 
     * @param packageName 包名
     * @param userId 用户ID
     * @param size 图标尺寸
     * @return 缓存的Bitmap，不存在则返回null
     */
    private fun get(packageName: String, userId: Int, size: Int): Bitmap? {
        return lruCache[Triple(packageName, userId, size)]
    }

    /**
     * 将图标放入缓存
     * Put icon into cache
     * 
     * @param packageName 包名
     * @param userId 用户ID
     * @param size 图标尺寸
     * @param bitmap 图标Bitmap
     */
    private fun put(packageName: String, userId: Int, size: Int, bitmap: Bitmap) {
        if (get(packageName, userId, size) == null) {
            lruCache.put(Triple(packageName, userId, size), bitmap)
        }
    }

    /**
     * 从缓存移除图标
     * Remove icon from cache
     * 
     * @param packageName 包名
     * @param userId 用户ID
     * @param size 图标尺寸
     */
    private fun remove(packageName: String, userId: Int, size: Int) {
        lruCache.remove(Triple(packageName, userId, size))
    }

    /**
     * 获取或加载应用图标
     * Get or load app icon
     * 
     * <p>从缓存获取图标，如果不存在则加载并缓存</p>
     * 
     * @param context 上下文
     * @param info 应用信息
     * @param userId 用户ID
     * @param size 图标尺寸
     * @return 应用图标Bitmap
     */
    @SuppressLint("NewApi")
    fun getOrLoadBitmap(context: Context, info: ApplicationInfo, userId: Int, size: Int): Bitmap? {
        val cachedBitmap = get(info.packageName, userId, size)
        if (cachedBitmap != null) {
            return cachedBitmap
        }
        var loader = appIconLoaders[size]
        if (loader == null) {
            val shrinkNonAdaptiveIcons = BuildUtils.atLeast30 && context.applicationInfo.loadIcon(context.packageManager) is AdaptiveIconDrawable
            loader = AppIconLoader(size, shrinkNonAdaptiveIcons, context)
            appIconLoaders[size] = loader
        }
        val bitmap = loader.loadIcon(info, false)
        put(info.packageName, userId, size, bitmap)
        return bitmap
    }

    @JvmStatic
    fun loadIconBitmapAsync(context: Context,
                            info: ApplicationInfo, userId: Int,
                            view: ImageView): Job {
        return launch {
            val size = view.measuredWidth.let { if (it > 0) it else (48 * context.resources.displayMetrics.density).toInt() }
            val cachedBitmap = get(info.packageName, userId, size)
            if (cachedBitmap != null) {
                view.setImageBitmap(cachedBitmap)
                return@launch
            }

            val bitmap = try {
                withContext(dispatcher) {
                    getOrLoadBitmap(context, info, userId, size)
                }
            } catch (e: CancellationException) {
                // do nothing if canceled
                return@launch
            } catch (e: Throwable) {
                null
            }

            if (bitmap != null) {
                view.setImageBitmap(bitmap)
            } else {
                if (Build.VERSION.SDK_INT >= 26) {
                    view.setImageResource(R.drawable.ic_default_app_icon)
                } else {
                    view.setImageDrawable(null)
                }
            }
        }
    }
}

