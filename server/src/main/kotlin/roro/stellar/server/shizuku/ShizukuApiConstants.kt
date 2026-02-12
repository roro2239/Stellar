package roro.stellar.server.shizuku

object ShizukuApiConstants {

    const val SERVER_VERSION = 13
    const val SERVER_PATCH_VERSION = 5

    const val SHIZUKU_APP_PACKAGE_NAME = "moe.shizuku.privileged.api"

    const val BINDER_DESCRIPTOR = "moe.shizuku.server.IShizukuService"

    const val BINDER_TRANSACTION_transact = 1599295570

    const val PROVIDER_SUFFIX = ".shizuku"

    const val SHIZUKU_MANAGER_APPLICATION_ID = "moe.shizuku.manager"

    const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"

    /** Shizuku 权限名称，用于 ConfigManager */
    const val PERMISSION_NAME = "shizuku"

    const val META_DATA_KEY = "moe.shizuku.client.V3_SUPPORT"

    // Shizuku 原始标志值 (用于 AIDL 接口兼容)
    const val FLAG_ASK = 0
    const val FLAG_ALLOWED = 1 shl 1  // 2
    const val FLAG_GRANTED = FLAG_ALLOWED
    const val FLAG_DENIED = 1 shl 2   // 4
    const val MASK_PERMISSION = FLAG_ALLOWED or FLAG_DENIED

    const val EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER"
    const val EXTRA_ALLOWED = "moe.shizuku.privileged.api.intent.extra.ALLOWED"

    /**
     * 将 Stellar 权限标志转换为 Shizuku 标志
     * Stellar: ASK=0, GRANTED=1, DENIED=2
     * Shizuku: ASK=0, GRANTED=2, DENIED=4
     */
    fun stellarToShizukuFlag(stellarFlag: Int): Int {
        return when (stellarFlag) {
            1 -> FLAG_GRANTED  // GRANTED
            2 -> FLAG_DENIED   // DENIED
            else -> FLAG_ASK   // ASK
        }
    }

    /**
     * 将 Shizuku 标志转换为 Stellar 权限标志
     */
    fun shizukuToStellarFlag(shizukuFlag: Int): Int {
        return when {
            (shizukuFlag and FLAG_GRANTED) != 0 -> 1  // GRANTED
            (shizukuFlag and FLAG_DENIED) != 0 -> 2   // DENIED
            else -> 0  // ASK
        }
    }
}
