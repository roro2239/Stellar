package roro.stellar;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import androidx.annotation.RestrictTo;

/**
 * Stellar API常量定义
 * Stellar API Constants Definition
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>定义Stellar API使用的所有常量 - Defines all constants used by Stellar API</li>
 * <li>包含版本号、Binder描述符和Bundle键名 - Includes version numbers, Binder descriptors and Bundle keys</li>
 * <li>客户端和服务端共享的协议常量 - Protocol constants shared by client and server</li>
 * </ul>
 */
public class StellarApiConstants {

    // ============================================
    // 版本信息 Version Information
    // ============================================
    
    /** 服务端API版本号 Server API version */
    public static final int SERVER_VERSION = 1;
    
    /** 服务端补丁版本号 Server patch version */
    public static final int SERVER_PATCH_VERSION = 0;

    // ============================================
    // Binder常量 Binder Constants
    // ============================================
    
    /** Binder接口描述符 Binder interface descriptor */
    public static final String BINDER_DESCRIPTOR = "com.stellar.server.IStellarService";
    
    /** Binder事务代码：远程事务 Binder transaction code: remote transaction */
    public static final int BINDER_TRANSACTION_transact = 1;

    // ============================================
    // 应用绑定相关常量 Application Binding Constants
    // ============================================
    
    /** Bundle键：服务端版本号 Bundle key: server version */
    public static final String BIND_APPLICATION_SERVER_VERSION = "Stellar:attach-reply-version";
    
    /** Bundle键：服务端补丁版本 Bundle key: server patch version */
    public static final String BIND_APPLICATION_SERVER_PATCH_VERSION = "Stellar:attach-reply-patch-version";
    
    /** Bundle键：服务端UID Bundle key: server UID */
    public static final String BIND_APPLICATION_SERVER_UID = "Stellar:attach-reply-uid";
    
    /** Bundle键：服务端SELinux上下文 Bundle key: server SELinux context */
    public static final String BIND_APPLICATION_SERVER_SECONTEXT = "Stellar:attach-reply-secontext";
    
    /** Bundle键：权限是否已授予 Bundle key: permission granted */
    public static final String BIND_APPLICATION_PERMISSION_GRANTED = "Stellar:attach-reply-permission-granted";
    
    /** Bundle键：是否应显示权限说明 Bundle key: should show permission rationale */
    public static final String BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE = "Stellar:attach-reply-should-show-request-permission-rationale";

    // ============================================
    // 权限请求相关常量 Permission Request Constants
    // ============================================
    
    /** Bundle键：权限请求是否被允许 Bundle key: permission request allowed */
    public static final String REQUEST_PERMISSION_REPLY_ALLOWED = "Stellar:request-permission-reply-allowed";
    
    /** Bundle键：权限是否为一次性 Bundle key: permission is one-time */
    public static final String REQUEST_PERMISSION_REPLY_IS_ONETIME = "Stellar:request-permission-reply-is-onetime";

    // ============================================
    // 附加应用相关常量 Attach Application Constants
    // ============================================
    
    /** Bundle键：应用包名 Bundle key: application package name */
    public static final String ATTACH_APPLICATION_PACKAGE_NAME = "Stellar:attach-package-name";
    
    /** Bundle键：API版本号 Bundle key: API version */
    public static final String ATTACH_APPLICATION_API_VERSION = "Stellar:attach-api-version";
}

