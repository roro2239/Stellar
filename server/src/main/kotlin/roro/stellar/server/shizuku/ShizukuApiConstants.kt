package roro.stellar.server.shizuku

/**
 * Shizuku API 常量定义
 * 用于兼容 Shizuku API
 */
object ShizukuApiConstants {

    // Shizuku 服务版本
    const val SERVER_VERSION = 13
    const val SERVER_PATCH_VERSION = 5

    // Binder 描述符
    const val BINDER_DESCRIPTOR = "moe.shizuku.server.IShizukuService"

    // Binder 事务码
    const val BINDER_TRANSACTION_transact = 1599295570 // '_' << 24 | 'S' << 16 | 'H' << 8 | 'K'

    // Provider 名称后缀
    const val PROVIDER_SUFFIX = ".shizuku"

    // Shizuku 管理器包名
    const val SHIZUKU_MANAGER_APPLICATION_ID = "moe.shizuku.manager"

    // 权限名称
    const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"

    // Meta-data key
    const val META_DATA_KEY = "moe.shizuku.client.V3_SUPPORT"

    // Bundle keys for attachApplication (V13+)
    const val ATTACH_APPLICATION_API_VERSION = "shizuku:attach-api-version"
    const val ATTACH_APPLICATION_PACKAGE_NAME = "shizuku:attach-package-name"

    // Bundle keys for bindApplication (sent back to client)
    const val BIND_APPLICATION_SERVER_UID = "shizuku:uid"
    const val BIND_APPLICATION_SERVER_VERSION = "shizuku:version"
    const val BIND_APPLICATION_SERVER_SECONTEXT = "shizuku:secontext"
    const val BIND_APPLICATION_SERVER_PATCH_VERSION = "shizuku:patch-version"
    const val BIND_APPLICATION_PERMISSION_GRANTED = "shizuku:permission-granted"
    const val BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE = "shizuku:should-show-request-permission-rationale"

    // Bundle keys for permission result
    const val REQUEST_PERMISSION_REPLY_ALLOWED = "shizuku:allowed"
    const val REQUEST_PERMISSION_REPLY_IS_ONETIME = "shizuku:is-onetime"

    // Bundle keys for user service
    const val USER_SERVICE_ARG_COMPONENT = "shizuku:user-service-arg-component"
    const val USER_SERVICE_ARG_DEBUGGABLE = "shizuku:user-service-arg-debuggable"
    const val USER_SERVICE_ARG_VERSION_CODE = "shizuku:user-service-arg-version-code"
    const val USER_SERVICE_ARG_PROCESS_NAME = "shizuku:user-service-arg-process-name"
    const val USER_SERVICE_ARG_TAG = "shizuku:user-service-arg-tag"
    const val USER_SERVICE_ARG_TOKEN = "shizuku:user-service-arg-token"
    const val USER_SERVICE_ARG_DAEMON = "shizuku:user-service-arg-daemon"

    // Binder extra key
    const val EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER"

    // 权限标志
    const val FLAG_ALLOWED = 1 shl 1
    const val FLAG_DENIED = 1 shl 2
    const val MASK_PERMISSION = FLAG_ALLOWED or FLAG_DENIED
}
