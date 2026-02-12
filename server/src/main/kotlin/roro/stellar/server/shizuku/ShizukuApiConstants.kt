package roro.stellar.server.shizuku

object ShizukuApiConstants {

    /** Shizuku 权限名称，用于 ConfigManager */
    const val PERMISSION_NAME = "shizuku"

    /** 用于检测应用是否支持 Shizuku */
    const val META_DATA_KEY = "moe.shizuku.client.V3_SUPPORT"

    // Shizuku 原始标志值 (用于 AIDL 接口兼容)
    const val FLAG_ASK = 0
    const val FLAG_GRANTED = 1 shl 1  // 2
    const val FLAG_DENIED = 1 shl 2   // 4

    const val EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER"
    const val EXTRA_ALLOWED = "moe.shizuku.privileged.api.intent.extra.ALLOWED"

    // Shizuku 用户服务参数常量
    object UserServiceArgs {
        const val COMPONENT = "shizuku:user-service-arg-component"
        const val DEBUGGABLE = "shizuku:user-service-arg-debuggable"
        const val VERSION_CODE = "shizuku:user-service-arg-version-code"
        const val DAEMON = "shizuku:user-service-arg-daemon"
        const val USE_32_BIT = "shizuku:user-service-arg-use-32-bit-app-process"
        const val PROCESS_NAME = "shizuku:user-service-arg-process-name"
        const val TAG = "shizuku:user-service-arg-tag"
        const val NO_CREATE = "shizuku:user-service-arg-no-create"
        const val REMOVE = "shizuku:user-service-remove"
        const val TOKEN = "shizuku:user-service-arg-token"
    }

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
