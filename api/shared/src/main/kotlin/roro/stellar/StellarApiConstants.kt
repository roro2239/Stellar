package roro.stellar

object StellarApiConstants {
    const val SERVER_VERSION = 2
    const val SERVER_PATCH_VERSION = 0

    const val PERMISSION_KEY = "roro.stellar.permissions"
    val PERMISSIONS = arrayOf(
        "stellar",
        "follow_stellar_startup",
        "follow_stellar_startup_on_boot"
    )

    fun isRuntimePermission(permission: String): Boolean {
        return permission == "stellar" || permission.endsWith(":runtime")
    }

    const val BINDER_DESCRIPTOR = "com.stellar.server.IStellarService"
    const val BINDER_TRANSACTION_transact = 1

    const val BIND_APPLICATION_SERVER_VERSION = "stellar:attach-reply-version"
    const val BIND_APPLICATION_SERVER_PATCH_VERSION = "stellar:attach-reply-patch-version"
    const val BIND_APPLICATION_SERVER_UID = "stellar:attach-reply-uid"
    const val BIND_APPLICATION_SERVER_SECONTEXT = "stellar:attach-reply-secontext"
    const val BIND_APPLICATION_PERMISSION_GRANTED = "stellar:attach-reply-permission-granted"
    const val BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE = "stellar:attach-reply-should-show-request-permission-rationale"

    const val REQUEST_PERMISSION_REPLY_ALLOWED = "stellar:request-permission-reply-allowed"
    const val REQUEST_PERMISSION_REPLY_IS_ONETIME = "stellar:request-permission-reply-is-onetime"
    const val REQUEST_PERMISSION_REPLY_PERMISSION = "stellar:request-permission-reply-permission"

    const val ATTACH_APPLICATION_PACKAGE_NAME = "stellar:attach-package-name"
    const val ATTACH_APPLICATION_API_VERSION = "stellar:attach-api-version"
}
