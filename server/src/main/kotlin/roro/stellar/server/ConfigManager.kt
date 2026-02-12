package roro.stellar.server

import android.content.pm.PackageManager
import android.os.Build
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.UserManagerApis
import roro.stellar.StellarApiConstants.PERMISSIONS
import roro.stellar.StellarApiConstants.PERMISSION_KEY
import roro.stellar.server.StellarConfig.PackageEntry
import roro.stellar.server.ktx.workerHandler
import roro.stellar.server.util.Logger
import roro.stellar.server.util.UserHandleCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader

class ConfigManager {

    private val mWriteRunner: Runnable = Runnable { write(config) }

    private val config: StellarConfig
    val packages: MutableMap<Int, PackageEntry>
        get() = LinkedHashMap(config.packages)

    init {
        this.config = load()

        var changed = false

        for (entry in LinkedHashMap(config.packages)) {

            val packages = PackageManagerApis.getPackagesForUidNoThrow(entry.key)
            if (packages.isEmpty()) {
                LOGGER.i("remove config for uid %d since it has gone", entry.key)
                config.packages.remove(entry.key)
                changed = true
                continue
            }

            var needRemoving = true

            for (packageName in entry.value.packages) {
                if (packages.contains(packageName)) {
                    needRemoving = false
                    break
                }
            }

            val rawSize = entry.value.packages.size
            val s = LinkedHashSet(entry.value.packages)
            entry.value.packages.clear()
            entry.value.packages.addAll(s)
            val shrunkSize = entry.value.packages.size
            if (shrunkSize < rawSize) {
                LOGGER.w("entry.packages has duplicate! Shrunk. (%d -> %d)", rawSize, shrunkSize)
            }

            if (needRemoving) {
                LOGGER.i("remove config for uid %d since the packages for it changed", entry.key)
                config.packages.remove(entry.key)
                changed = true
            }
        }

        for (userId in UserManagerApis.getUserIdsNoThrow()) {
            for (pi in PackageManagerApis.getInstalledPackagesNoThrow(
                PackageManager.GET_META_DATA.toLong(),
                userId
            )) {
                if (
                    pi == null ||
                    pi.applicationInfo == null ||
                    pi.applicationInfo!!.metaData == null ||
                    !pi.applicationInfo!!.metaData.getString(PERMISSION_KEY, "").split(",")
                        .contains("stellar") ||
                    pi.packageName == ServerConstants.MANAGER_APPLICATION_ID
                ) {
                    continue
                }

                val uid = pi.applicationInfo!!.uid

                val packages = ArrayList<String>()
                packages.add(pi.packageName)

                updateLocked(uid, packages)
                changed = true
            }
        }

        for (entry in LinkedHashMap(config.packages)) {
            val permissions = LinkedHashSet<String>()
            val packages = PackageManagerApis.getPackagesForUidNoThrow(entry.key)
            for (packageName in packages) {
                val applicationInfo = PackageManagerApis.getApplicationInfoNoThrow(
                    packageName, PackageManager.GET_META_DATA.toLong(),
                    UserHandleCompat.getUserId(entry.key)
                ) ?: continue
                for (permission in (applicationInfo.metaData?.getString(PERMISSION_KEY, "")
                    ?: "").split(",")) {
                    if (PERMISSIONS.contains(permission)) {
                        permissions.add(permission)
                    }
                }
            }
            val packageEntry = findLocked(entry.key)!!
            val permissionsToRemove = mutableListOf<String>()
            for (permission in entry.value.permissions) {
                if (!permissions.contains(permission.key)) {
                    permissionsToRemove.add(permission.key)
                }
            }
            for (permissionKey in permissionsToRemove) {
                packageEntry.permissions.remove(permissionKey)
            }
            for (permission in permissions) {
                if (packageEntry.permissions[permission] == null) {
                    packageEntry.permissions[permission] = FLAG_ASK
                }
            }
            scheduleWriteLocked()
        }

        if (changed) {
            scheduleWriteLocked()
        }
    }

    private fun scheduleWriteLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (workerHandler.hasCallbacks(mWriteRunner)) {
                return
            }
        } else {
            workerHandler.removeCallbacks(mWriteRunner)
        }
        workerHandler.postDelayed(mWriteRunner, WRITE_DELAY)
    }

    private fun findLocked(uid: Int): PackageEntry? {
        return config.packages[uid]
    }

    fun find(uid: Int): PackageEntry? {
        synchronized(this) {
            return findLocked(uid)
        }
    }

    /**
     * 获取指定 UID 的权限标志
     * @return FLAG_ASK, FLAG_GRANTED 或 FLAG_DENIED
     */
    fun getPermissionFlag(uid: Int, permission: String): Int {
        synchronized(this) {
            return findLocked(uid)?.permissions?.get(permission) ?: FLAG_ASK
        }
    }

    fun findOldConfigByPackageName(currentUid: Int, packageName: String): Pair<Int, PackageEntry>? {
        synchronized(this) {
            for ((uid, entry) in config.packages) {
                if (uid == currentUid) {
                    continue
                }
                if (entry.packages.contains(packageName)) {
                    return Pair(uid, entry)
                }
            }
            return null
        }
    }

    fun createConfigWithAllPermissions(uid: Int, packageName: String) {
        synchronized(this) {
            val userId = UserHandleCompat.getUserId(uid)
            val applicationInfo = PackageManagerApis.getApplicationInfoNoThrow(
                packageName,
                PackageManager.GET_META_DATA.toLong(),
                userId
            )

            if (applicationInfo == null) {
                LOGGER.w("无法获取应用信息: %s, 使用默认权限", packageName)
                val packages = mutableListOf(packageName)
                updateLocked(uid, packages)
                return
            }

            val declaredPermissions = LinkedHashSet<String>()
            val metaDataPermissions = applicationInfo.metaData?.getString(PERMISSION_KEY, "") ?: ""
            for (permission in metaDataPermissions.split(",")) {
                if (PERMISSIONS.contains(permission)) {
                    declaredPermissions.add(permission)
                }
            }

            LOGGER.i("应用 %s 声明的权限: %s", packageName, declaredPermissions.toString())

            val entry = PackageEntry()
            entry.packages.add(packageName)

            for (permission in declaredPermissions) {
                entry.permissions[permission] = FLAG_ASK
            }

            if (entry.permissions.isEmpty()) {
                entry.permissions["stellar"] = FLAG_ASK
            }

            config.packages[uid] = entry
            scheduleWriteLocked()

            LOGGER.i("已创建配置: uid=%d, package=%s, permissions=%s",
                uid, packageName, entry.permissions.toString())
        }
    }

    private fun updateLocked(
        uid: Int,
        packages: MutableList<String>?
    ) {
        var entry = findLocked(uid)
        if (entry == null) {
            entry = PackageEntry()
            entry.permissions["stellar"] = FLAG_ASK
            config.packages[uid] = entry
        }
        if (packages != null) {
            for (packageName in packages) {
                if (entry.packages.contains(packageName)) {
                    continue
                }
                entry.packages.add(packageName)
            }
        }
        scheduleWriteLocked()
    }

    fun update(
        uid: Int,
        packages: MutableList<String>?
    ) {
        synchronized(this) {
            updateLocked(uid, packages)
        }
    }

    private fun updatePermissionLocked(uid: Int, permission: String, newFlag: Int) {
        findLocked(uid)?.let { it.permissions[permission] = newFlag }
        scheduleWriteLocked()
    }

    fun updatePermission(uid: Int, permission: String, newFlag: Int) {
        synchronized(this) {
            updatePermissionLocked(uid, permission, newFlag)
        }
    }

    private fun removeLocked(uid: Int) {
        config.packages.remove(uid)
        scheduleWriteLocked()
    }

    fun remove(uid: Int) {
        synchronized(this) {
            removeLocked(uid)
        }
    }

    companion object {
        private val LOGGER: Logger = Logger("ConfigManager")

        const val FLAG_ASK: Int = 0
        const val FLAG_GRANTED: Int = 1
        const val FLAG_DENIED: Int = 2

        private val GSON_IN: Gson = GsonBuilder()
            .create()

        private val GSON_OUT: Gson = GsonBuilder()
            .setVersion(StellarConfig.LATEST_VERSION.toDouble())
            .create()

        private const val WRITE_DELAY = (1 * 1000).toLong()

        private val FILE = File("/data/user_de/0/com.android.shell/stellar.json")
        private val ATOMIC_FILE = AtomicFile(FILE)

        fun load(): StellarConfig {
            val stream: FileInputStream
            try {
                stream = ATOMIC_FILE.openRead()
            } catch (_: FileNotFoundException) {
                LOGGER.i("no existing config file " + ATOMIC_FILE.baseFile + "; starting empty")
                return StellarConfig()
            }

            var config: StellarConfig? = null
            try {
                config = GSON_IN.fromJson(
                    InputStreamReader(stream),
                    StellarConfig::class.java
                )
            } catch (tr: Throwable) {
                LOGGER.w(tr, "加载配置失败")
            } finally {
                try {
                    stream.close()
                } catch (e: IOException) {
                    LOGGER.w("关闭配置文件失败: $e")
                }
            }
            if (config != null) return config
            return StellarConfig()
        }

        fun write(config: StellarConfig) {
            synchronized(ATOMIC_FILE) {
                val stream: FileOutputStream
                try {
                    stream = ATOMIC_FILE.startWrite()
                } catch (e: IOException) {
                    LOGGER.w("写入状态失败: $e")
                    return
                }
                try {
                    val json = GSON_OUT.toJson(config)
                    stream.write(json.toByteArray())

                    ATOMIC_FILE.finishWrite(stream)
                    LOGGER.v("配置已保存")
                } catch (tr: Throwable) {
                    LOGGER.w("无法保存配置，正在恢复备份: %s", ATOMIC_FILE.baseFile)
                    ATOMIC_FILE.failWrite(stream)
                }
            }
        }
    }
}