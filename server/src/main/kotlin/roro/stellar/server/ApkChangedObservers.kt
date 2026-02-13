package roro.stellar.server

import android.os.FileObserver
import android.util.Log
import java.io.File
import java.util.Collections

fun interface ApkChangedListener {
    fun onApkChanged()
}

private val observers = Collections.synchronizedMap(HashMap<String, ApkChangedObserver>())

object ApkChangedObservers {

    @JvmStatic
    fun start(apkPath: String, listener: ApkChangedListener) {
        val path = File(apkPath).parent!!
        val observer = observers.getOrPut(path) {
            ApkChangedObserver(path).apply {
                startWatching()
            }
        }
        observer.addListener(listener)
    }
}

@Suppress("DEPRECATION")
class ApkChangedObserver(private val path: String) : FileObserver(path, DELETE or DELETE_SELF or MOVED_FROM) {

    private val listeners = mutableSetOf<ApkChangedListener>()

    fun addListener(listener: ApkChangedListener): Boolean = listeners.add(listener)

    override fun onEvent(event: Int, path: String?) {
        Log.d("StellarServer", "事件: ${eventToString(event)} $path")

        if ((event and IN_IGNORED) != 0) return

        if (path == "base.apk" || path == null) {
            val isDeleteEvent = (event and DELETE != 0) || (event and DELETE_SELF != 0) || (event and MOVED_FROM != 0)
            if (isDeleteEvent) {
                stopWatching()
                ArrayList(listeners).forEach { it.onApkChanged() }
            }
        }
    }

    override fun startWatching() {
        super.startWatching()
        Log.d("StellarServer", "开始监听 $path")
    }

    override fun stopWatching() {
        super.stopWatching()
        Log.d("StellarServer", "停止监听 $path")
    }
}

private const val IN_IGNORED = 0x00008000
private const val IN_ISDIR = 0x40000000

private val EVENT_NAMES = mapOf(
    FileObserver.ACCESS to "ACCESS",
    FileObserver.MODIFY to "MODIFY",
    FileObserver.ATTRIB to "ATTRIB",
    FileObserver.CLOSE_WRITE to "CLOSE_WRITE",
    FileObserver.CLOSE_NOWRITE to "CLOSE_NOWRITE",
    FileObserver.OPEN to "OPEN",
    FileObserver.MOVED_FROM to "MOVED_FROM",
    FileObserver.MOVED_TO to "MOVED_TO",
    FileObserver.CREATE to "CREATE",
    FileObserver.DELETE to "DELETE",
    FileObserver.DELETE_SELF to "DELETE_SELF",
    FileObserver.MOVE_SELF to "MOVE_SELF",
    IN_IGNORED to "IN_IGNORED",
    IN_ISDIR to "IN_ISDIR"
)

private fun eventToString(event: Int): String = EVENT_NAMES.entries
    .filter { (flag, _) -> event and flag == flag }
    .joinToString(" | ") { it.value }

