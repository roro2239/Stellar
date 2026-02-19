package roro.stellar.server

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.UserManagerApis
import roro.stellar.StellarApiConstants.PERMISSIONS
import roro.stellar.StellarApiConstants.PERMISSION_KEY
import roro.stellar.server.StellarConfig.PackageEntry
import roro.stellar.server.api.IContentProviderUtils
import roro.stellar.server.ktx.workerHandler
import roro.stellar.server.util.Logger
import roro.stellar.server.util.UserHandleCompat

class ConfigManager {

    private val mWriteRunner: Runnable = Runnable { saveToManager(config) }

    private val config: StellarConfig
    val packages: MutableMap<Int, PackageEntry>
        get() = LinkedHashMap(config.packages)

    init {
        this.config = loadFromManager()

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
                if (applicationInfo.metaData?.getBoolean("moe.shizuku.client.V3_SUPPORT", false) == true) {
                    permissions.add("shizuku")
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
        var entry = findLocked(uid)
        if (entry == null) {
            entry = PackageEntry()
            val packages = PackageManagerApis.getPackagesForUidNoThrow(uid)
            entry.packages.addAll(packages)
            config.packages[uid] = entry
            LOGGER.i("为 uid=%d 创建新配置以保存权限 %s", uid, permission)
        }
        entry.permissions[permission] = newFlag
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

    fun isShizukuCompatEnabled(): Boolean {
        synchronized(this) {
            return config.shizukuCompatEnabled
        }
    }

    fun setShizukuCompatEnabled(enabled: Boolean) {
        synchronized(this) {
            if (config.shizukuCompatEnabled != enabled) {
                config.shizukuCompatEnabled = enabled
                LOGGER.i("Shizuku 兼容层状态已更改: %s", if (enabled) "启用" else "禁用")
                scheduleWriteLocked()
            }
        }
    }

    companion object {
        private val LOGGER: Logger = Logger("ConfigManager")

        const val FLAG_ASK: Int = 0
        const val FLAG_GRANTED: Int = 1
        const val FLAG_DENIED: Int = 2

        private val GSON_IN: Gson = GsonBuilder().create()
        private val GSON_OUT: Gson = GsonBuilder()
            .setVersion(StellarConfig.LATEST_VERSION.toDouble())
            .create()

        private const val WRITE_DELAY = 1000L
        private const val MANAGER_PROVIDER = "${ServerConstants.MANAGER_APPLICATION_ID}.stellar"

        private fun callProvider(method: String, extras: Bundle?): Bundle? {
            val token: IBinder? = null
            var provider: android.content.IContentProvider? = null
            return try {
                provider = ActivityManagerApis.getContentProviderExternal(MANAGER_PROVIDER, 0, token, MANAGER_PROVIDER)
                    ?: return null
                IContentProviderUtils.callCompat(provider, null, MANAGER_PROVIDER, method, null, extras ?: Bundle())
            } catch (tr: Throwable) {
                LOGGER.e(tr, "ContentProvider 调用失败: %s", method)
                null
            } finally {
                if (provider != null) {
                    try { ActivityManagerApis.removeContentProviderExternal(MANAGER_PROVIDER, token) } catch (_: Throwable) {}
                }
            }
        }

        fun loadFromManager(): StellarConfig {
            return try {
                val reply = callProvider("loadConfig", null)
                val json = reply?.getString("configJson")
                if (json != null) {
                    GSON_IN.fromJson(json, StellarConfig::class.java) ?: StellarConfig()
                } else {
                    LOGGER.i("manager 无配置，使用默认值")
                    StellarConfig()
                }
            } catch (tr: Throwable) {
                LOGGER.w(tr, "从 manager 加载配置失败，使用默认值")
                StellarConfig()
            }
        }

        fun saveToManager(config: StellarConfig) {
            try {
                val json = GSON_OUT.toJson(config)
                val extras = Bundle().apply { putString("configJson", json) }
                val result = callProvider("saveConfig", extras)
                if (result != null) {
                    LOGGER.v("配置已保存到 manager")
                } else {
                    LOGGER.w("保存配置到 manager 失败: 返回 null")
                }
            } catch (tr: Throwable) {
                LOGGER.e(tr, "保存配置到 manager 失败")
            }
        }
    }
}
