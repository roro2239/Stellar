package roro.stellar.server.util;

import android.os.Build;

/**
 * ABI工具类
 * ABI Utility Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>检测设备支持的ABI架构 - Detects device supported ABI architecture</li>
 * <li>判断是否支持32位应用 - Determines if 32-bit apps are supported</li>
 * </ul>
 */
public class AbiUtil {

    /** 缓存的32位支持状态 Cached 32-bit support status */
    private static Boolean has32Bit;

    /**
     * 检查设备是否支持32位应用
     * Check if device supports 32-bit apps
     * 
     * @return true表示支持32位应用
     */
    public static boolean has32Bit() {
        if (has32Bit == null) {
            has32Bit = Build.SUPPORTED_32_BIT_ABIS.length > 0;
        }
        return has32Bit;
    }
}

