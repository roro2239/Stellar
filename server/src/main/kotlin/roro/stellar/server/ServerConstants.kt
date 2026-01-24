package roro.stellar.server

object ServerConstants {
    const val MANAGER_APP_NOT_FOUND: Int = 50

    const val MANAGER_APPLICATION_ID: String = "roro.stellar.manager"

    const val REQUEST_PERMISSION_ACTION: String =
        "$MANAGER_APPLICATION_ID.intent.action.REQUEST_PERMISSION"

    const val BINDER_TRANSACTION_getApplications: Int = 10001
}