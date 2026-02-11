package roro.stellar.shizuku.server

object ShizukuApiConstants {

    const val SERVER_VERSION = 13
    const val SERVER_PATCH_VERSION = 5

    const val SHIZUKU_APP_PACKAGE_NAME = "moe.shizuku.privileged.api"

    const val BINDER_DESCRIPTOR = "moe.shizuku.server.IShizukuService"

    const val BINDER_TRANSACTION_transact = 1599295570 // '_' << 24 | 'S' << 16 | 'H' << 8 | 'K'

    const val PROVIDER_SUFFIX = ".shizuku"

    const val SHIZUKU_MANAGER_APPLICATION_ID = "moe.shizuku.manager"

    const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"

    const val META_DATA_KEY = "moe.shizuku.client.V3_SUPPORT"

    const val FLAG_ASK = 0
    const val FLAG_ALLOWED = 1 shl 1
    const val FLAG_GRANTED = FLAG_ALLOWED
    const val FLAG_DENIED = 1 shl 2
    const val MASK_PERMISSION = FLAG_ALLOWED or FLAG_DENIED

    const val EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER"
}
