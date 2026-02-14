package roro.stellar.server.shizuku

object ShizukuApiConstants {

    const val PERMISSION_NAME = "shizuku"

    const val META_DATA_KEY = "moe.shizuku.client.V3_SUPPORT"

    const val SERVER_VERSION = 13
    const val SERVER_PATCH_VERSION = 6

    const val BINDER_DESCRIPTOR = "moe.shizuku.server.IShizukuService"
    const val BINDER_TRANSACTION_transact = 1

    const val FLAG_ASK = 0
    const val FLAG_GRANTED = 1 shl 1
    const val FLAG_DENIED = 1 shl 2

    private const val STELLAR_FLAG_ASK = 0
    private const val STELLAR_FLAG_GRANTED = 1
    private const val STELLAR_FLAG_DENIED = 2

    const val EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER"

    // 权限回调使用的 key（与原版 Shizuku 一致）
    const val REQUEST_PERMISSION_REPLY_ALLOWED = "shizuku:request-permission-reply-allowed"

    object BindApplication {
        const val SERVER_VERSION = "shizuku:attach-reply-version"
        const val SERVER_PATCH_VERSION = "shizuku:attach-reply-patch-version"
        const val SERVER_UID = "shizuku:attach-reply-uid"
        const val SERVER_SECONTEXT = "shizuku:attach-reply-secontext"
        const val PERMISSION_GRANTED = "shizuku:attach-reply-permission-granted"
        const val SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE = "shizuku:attach-reply-should-show-request-permission-rationale"
    }

    object AttachApplication {
        const val PACKAGE_NAME = "shizuku:attach-package-name"
        const val API_VERSION = "shizuku:attach-api-version"
    }

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

    fun stellarToShizukuFlag(stellarFlag: Int): Int = when (stellarFlag) {
        STELLAR_FLAG_GRANTED -> FLAG_GRANTED
        STELLAR_FLAG_DENIED -> FLAG_DENIED
        else -> FLAG_ASK
    }

    fun shizukuToStellarFlag(shizukuFlag: Int): Int = when {
        (shizukuFlag and FLAG_GRANTED) != 0 -> STELLAR_FLAG_GRANTED
        (shizukuFlag and FLAG_DENIED) != 0 -> STELLAR_FLAG_DENIED
        else -> STELLAR_FLAG_ASK
    }
}
