package roro.stellar.server;

/**
 * 服务端常量定义
 * Server Constants Definition
 *
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>定义服务端使用的常量 - Defines server-side constants</li>
 * <li>权限和包名定义 - Permission and package name definitions</li>
 * <li>Binder事务代码 - Binder transaction codes</li>
 * </ul>
 */
public class ServerConstants {

    /** 错误代码：管理器应用未找到 Error code: Manager app not found */
    public static final int MANAGER_APP_NOT_FOUND = 50;

    /** Stellar API权限名称 Stellar API permission name */
    public static final String PERMISSION = "roro.stellar.manager.permission.API_V23";

    /** 管理器应用ID Manager application ID */
    public static final String MANAGER_APPLICATION_ID = "roro.stellar.manager";

    /** 权限请求Action Permission request action */
    public static final String REQUEST_PERMISSION_ACTION = MANAGER_APPLICATION_ID + ".intent.action.REQUEST_PERMISSION";

    /** Binder事务代码：获取应用列表 Binder transaction: get applications */
    public static final int BINDER_TRANSACTION_getApplications = 10001;
}

