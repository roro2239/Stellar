package roro.stellar.manager;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;

import roro.stellar.manager.utils.EmptySharedPreferencesImpl;

/**
 * Stellar设置管理器
 * Stellar Settings Manager
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>管理Stellar Manager的全局设置 - Manages global settings of Stellar Manager</li>
 * <li>使用Device Protected Storage存储 - Uses Device Protected Storage</li>
 * <li>提供开机启动、端口等配置 - Provides boot startup, port and other configurations</li>
 * </ul>
 * 
 * <p>设置项 Settings：</p>
 * <ul>
 * <li>KEEP_START_ON_BOOT: 开机启动Root/ADB模式</li>
 * <li>KEEP_START_ON_BOOT_WIRELESS: 开机启动无线ADB</li>
 * <li>TCPIP_PORT: TCP/IP端口号</li>
 * </ul>
 */
public class StellarSettings {

    /** 设置文件名 Settings file name */
    public static final String NAME = "settings";
    
    /** 开机启动设置键 Boot startup setting key */
    public static final String KEEP_START_ON_BOOT = "start_on_boot";
    
    /** 无线ADB开机启动设置键 Wireless ADB boot startup setting key */
    public static final String KEEP_START_ON_BOOT_WIRELESS = "start_on_boot_wireless";
    
    /** TCP/IP端口设置键 TCP/IP port setting key */
    public static final String TCPIP_PORT = "tcpip_port";
    
    /** TCP/IP端口开关设置键 TCP/IP port switch setting key */
    public static final String TCPIP_PORT_ENABLED = "tcpip_port_enabled";
    
    /** 主题设置键 Theme setting key */
    public static final String THEME_MODE = "theme_mode";

    /** SharedPreferences实例 SharedPreferences instance */
    private static SharedPreferences sPreferences;

    /**
     * 获取SharedPreferences实例
     * Get SharedPreferences instance
     */
    public static SharedPreferences getPreferences() {
        return sPreferences;
    }

    /**
     * 获取设置存储上下文
     * Get settings storage context
     * 
     * @param context 上下文
     * @return Device Protected Storage上下文
     */
    @NonNull
    private static Context getSettingsStorageContext(@NonNull Context context) {
        // 使用Device Protected Storage，确保在锁屏前可访问
        Context storageContext = context.createDeviceProtectedStorageContext();
        storageContext = new ContextWrapper(storageContext) {
            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                try {
                    return super.getSharedPreferences(name, mode);
                } catch (IllegalStateException e) {
                    return new EmptySharedPreferencesImpl();
                }
            }
        };
        return storageContext;
    }

    /**
     * 初始化设置
     * Initialize settings
     * 
     * @param context 上下文
     */
    public static void initialize(Context context) {
        if (sPreferences == null) {
            sPreferences = getSettingsStorageContext(context).getSharedPreferences(NAME, Context.MODE_PRIVATE);
            
            // 首次安装时初始化TCP/IP端口配置
            if (!sPreferences.contains(TCPIP_PORT_ENABLED)) {
                // 默认开启
                sPreferences.edit().putBoolean(TCPIP_PORT_ENABLED, true).apply();
            }
            if (!sPreferences.contains(TCPIP_PORT)) {
                // 随机生成4位数端口 (1000-9999)
                int randomPort = 1000 + (int) (Math.random() * 9000);
                sPreferences.edit().putString(TCPIP_PORT, String.valueOf(randomPort)).apply();
            }
        }
    }

    /**
     * 启动方式定义
     * Launch Method Definition
     * 
     * <p>定义Stellar服务的启动方式类型</p>
     */
    @IntDef({LaunchMethod.UNKNOWN, LaunchMethod.ROOT, LaunchMethod.ADB,})
    @Retention(SOURCE)
    public @interface LaunchMethod {
        /** 未知启动方式 Unknown launch method */
        int UNKNOWN = -1;
        /** Root启动方式 Root launch method */
        int ROOT = 0;
        /** ADB启动方式 ADB launch method */
        int ADB = 1;
    }

    /**
     * 获取上次启动方式
     * Get last launch mode
     * 
     * @return 启动方式（ROOT/ADB/UNKNOWN）
     */
    @LaunchMethod
    public static int getLastLaunchMode() {
        return getPreferences().getInt("mode", LaunchMethod.UNKNOWN);
    }

    /**
     * 设置上次启动方式
     * Set last launch mode
     * 
     * @param method 启动方式（ROOT/ADB/UNKNOWN）
     */
    public static void setLastLaunchMode(@LaunchMethod int method) {
        getPreferences().edit().putInt("mode", method).apply();
    }
}

