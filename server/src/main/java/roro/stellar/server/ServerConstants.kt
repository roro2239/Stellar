package roro.stellar.server

/**
 * 服务端常量定义
 * Server Constants Definition
 *
 *
 * 功能说明 Features：
 *
 *  * 定义服务端使用的常量 - Defines server-side constants
 *  * 权限和包名定义 - Permission and package name definitions
 *  * Binder事务代码 - Binder transaction codes
 *
 */
object ServerConstants {
    /** 错误代码：管理器应用未找到 Error code: Manager app not found  */
    const val MANAGER_APP_NOT_FOUND: Int = 50

    /** Stellar API权限名称 Stellar API permission name  */
    const val PERMISSION: String = "roro.stellar.manager.permission.API_V1"

    /** 管理器应用ID Manager application ID  */
    const val MANAGER_APPLICATION_ID: String = "roro.stellar.manager"

    /** 权限请求Action Permission request action  */
    const val REQUEST_PERMISSION_ACTION: String =
        "$MANAGER_APPLICATION_ID.intent.action.REQUEST_PERMISSION"

    /** Binder事务代码：获取应用列表 Binder transaction: get applications  */
    const val BINDER_TRANSACTION_getApplications: Int = 10001
}