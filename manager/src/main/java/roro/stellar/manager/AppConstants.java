package roro.stellar.manager;

/**
 * 应用程序常量定义
 * Application Constants Definition
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>定义全局使用的常量 - Defines globally used constants</li>
 * <li>通知渠道和ID配置 - Notification channel and ID configuration</li>
 * <li>Intent Extra键名定义 - Intent extra key definitions</li>
 * </ul>
 */
public class AppConstants {

    /** 日志标签 Log tag */
    public static final String TAG = "StellarManager";

    /** 通知渠道：服务状态 Notification channel: service status */
    public static final String NOTIFICATION_CHANNEL_STATUS = "starter";
    
    /** 通知渠道：后台工作 Notification channel: background work */
    public static final String NOTIFICATION_CHANNEL_WORK = "work";
    
    /** 通知ID：服务状态 Notification ID: service status */
    public static final int NOTIFICATION_ID_STATUS = 1;
    
    /** 通知ID：后台工作 Notification ID: background work */
    public static final int NOTIFICATION_ID_WORK = 2;

    /** 包名 Package name */
    private static final String PACKAGE = "roro.stellar.manager";
    
    /** Intent Extra键前缀 Intent extra key prefix */
    public static final String EXTRA = PACKAGE + ".extra";
}

