/**
 * APK文件变化监听器
 * APK File Change Observers
 * 
 * 功能说明 Features：
 * - 监听APK文件的变化（删除、更新） - Monitors APK file changes (deletion, update)
 * - 使用FileObserver实现文件系统监听 - Uses FileObserver for filesystem monitoring
 * - 支持多个监听器订阅同一路径 - Supports multiple listeners for same path
 * - 自动管理观察者生命周期 - Auto manages observer lifecycle
 * 
 * 工作原理 How It Works：
 * - 使用inotify监听文件系统事件
 * - 监听父目录而非文件本身（避免DELTE_SELF事件未触发）
 * - 当文件被删除时通知所有监听器
 * 
 * 使用场景 Use Cases：
 * - 监听Manager应用卸载
 * - 监听应用更新
 * - 及时响应APK变化
 * 
 * 注意事项 Notes：
 * - inotify监听的是inode，如果仍有进程持有文件，DELETE_SELF不会触发
 * - 因此监听父目录而非文件本身
 */
package roro.stellar.server

import android.os.FileObserver
import android.util.Log
import java.io.File
import java.util.*

/**
 * APK变化监听器接口
 * APK Change Listener Interface
 */
interface ApkChangedListener {
    /**
     * APK文件发生变化时调用
     * Called when APK file changes
     */
    fun onApkChanged()
}

private val observers = Collections.synchronizedMap(HashMap<String, ApkChangedObserver>())

object ApkChangedObservers {

    /**
     * 开始监听APK文件变化
     * Start watching APK file changes
     * 
     * <p>监听APK所在目录的文件变化，当base.apk被删除时触发监听器</p>
     * 
     * @param apkPath APK文件路径
     * @param listener 变化监听器
     */
    @JvmStatic
    fun start(apkPath: String, listener: ApkChangedListener) {
        // inotify watchs inode, if the there are still processes holds the file, DELTE_SELF will not be triggered
        // so we need to watch the parent folder

        val path = File(apkPath).parent!!
        val observer = observers.getOrPut(path) {
            ApkChangedObserver(path).apply {
                startWatching()
            }
        }
        observer.addListener(listener)
    }

    /**
     * 停止监听APK文件变化
     * Stop watching APK file changes
     * 
     * <p>移除指定监听器，如果某个路径没有监听器则停止观察</p>
     * 
     * @param listener 要移除的监听器
     */
    @JvmStatic
    fun stop(listener: ApkChangedListener) {
        val pathToRemove = mutableListOf<String>()

        for ((path, observer) in observers) {
            observer.removeListener(listener)

            if (!observer.hasListeners()) {
                pathToRemove.add(path)
            }
        }

        for (path in pathToRemove) {
            observers.remove(path)?.stopWatching()
        }
    }
}

@Suppress("DEPRECATION")
class ApkChangedObserver(private val path: String) : FileObserver(path, DELETE) {

    private val listeners = mutableSetOf<ApkChangedListener>()

    /**
     * 添加监听器
     * Add listener
     * 
     * @param listener 监听器
     * @return true表示添加成功（之前不存在）
     */
    fun addListener(listener: ApkChangedListener): Boolean {
        return listeners.add(listener)
    }

    /**
     * 移除监听器
     * Remove listener
     * 
     * @param listener 监听器
     * @return true表示移除成功（之前存在）
     */
    fun removeListener(listener: ApkChangedListener): Boolean {
        return listeners.remove(listener)
    }

    /**
     * 是否有监听器
     * Has listeners
     * 
     * @return true表示至少有一个监听器
     */
    fun hasListeners(): Boolean {
        return listeners.isNotEmpty()
    }

    /**
     * 文件系统事件回调
     * Filesystem event callback
     * 
     * <p>当base.apk被删除时，停止监听并通知所有监听器</p>
     * 
     * @param event 事件类型
     * @param path 文件路径
     */
    override fun onEvent(event: Int, path: String?) {
        Log.d("StellarServer", "onEvent: ${eventToString(event)} $path")

        if ((event and 0x00008000 /* IN_IGNORED */) != 0 || path == null) {
            return
        }

        if (path == "base.apk") {
            stopWatching()
            ArrayList(listeners).forEach { it.onApkChanged() }
        }
    }

    /**
     * 开始监听
     * Start watching
     */
    override fun startWatching() {
        super.startWatching()
        Log.d("StellarServer", "start watching $path")
    }

    /**
     * 停止监听
     * Stop watching
     */
    override fun stopWatching() {
        super.stopWatching()
        Log.d("StellarServer", "stop watching $path")
    }
}

/**
 * 将FileObserver事件转换为字符串
 * Convert FileObserver event to string
 * 
 * @param event 事件标志位
 * @return 事件名称字符串
 */
private fun eventToString(event: Int): String {
    val sb = StringBuilder()
    if (event and FileObserver.ACCESS == FileObserver.ACCESS) {
        sb.append("ACCESS").append(" | ")
    }
    if (event and FileObserver.MODIFY == FileObserver.MODIFY) {
        sb.append("MODIFY").append(" | ")
    }
    if (event and FileObserver.ATTRIB == FileObserver.ATTRIB) {
        sb.append("ATTRIB").append(" | ")
    }
    if (event and FileObserver.CLOSE_WRITE == FileObserver.CLOSE_WRITE) {
        sb.append("CLOSE_WRITE").append(" | ")
    }
    if (event and FileObserver.CLOSE_NOWRITE == FileObserver.CLOSE_NOWRITE) {
        sb.append("CLOSE_NOWRITE").append(" | ")
    }
    if (event and FileObserver.OPEN == FileObserver.OPEN) {
        sb.append("OPEN").append(" | ")
    }
    if (event and FileObserver.MOVED_FROM == FileObserver.MOVED_FROM) {
        sb.append("MOVED_FROM").append(" | ")
    }
    if (event and FileObserver.MOVED_TO == FileObserver.MOVED_TO) {
        sb.append("MOVED_TO").append(" | ")
    }
    if (event and FileObserver.CREATE == FileObserver.CREATE) {
        sb.append("CREATE").append(" | ")
    }
    if (event and FileObserver.DELETE == FileObserver.DELETE) {
        sb.append("DELETE").append(" | ")
    }
    if (event and FileObserver.DELETE_SELF == FileObserver.DELETE_SELF) {
        sb.append("DELETE_SELF").append(" | ")
    }
    if (event and FileObserver.MOVE_SELF == FileObserver.MOVE_SELF) {
        sb.append("MOVE_SELF").append(" | ")
    }

    if (event and 0x00008000 == 0x00008000) {
        sb.append("IN_IGNORED").append(" | ")
    }

    if (event and 0x40000000 == 0x40000000) {
        sb.append("IN_ISDIR").append(" | ")
    }

    return if (sb.isNotEmpty()) {
        sb.substring(0, sb.length - 3)
    } else {
        sb.toString()
    }
}

