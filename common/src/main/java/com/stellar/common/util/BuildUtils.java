package com.stellar.common.util;

import android.os.Build;

/**
 * Android版本检测工具类
 * Android Version Detection Utility
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>提供便捷的Android版本判断方法 - Provides convenient Android version check methods</li>
 * <li>支持预览版SDK检测 - Supports preview SDK detection</li>
 * <li>简化版本比较逻辑 - Simplifies version comparison logic</li>
 * </ul>
 * 
 * <p>版本对照 Version Mapping：</p>
 * <ul>
 * <li>API 31 - Android 12</li>
 * <li>API 30 - Android 11</li>
 * <li>API 29 - Android 10</li>
 * <li>API 28 - Android 9 (Pie)</li>
 * <li>API 26 - Android 8.0 (Oreo)</li>
 * <li>API 24 - Android 7.0 (Nougat)</li>
 * <li>API 23 - Android 6.0 (Marshmallow)</li>
 * </ul>
 *
 */
public class BuildUtils {

    /** 当前SDK版本 Current SDK version */
    private static final int SDK = Build.VERSION.SDK_INT;

    /** 预览SDK版本 Preview SDK version */
    private static final int PREVIEW_SDK = SDK >= 23 ? Build.VERSION.PREVIEW_SDK_INT : 0;

    /** 是否为Android 12及以上 Whether Android 12+ */
    public static boolean atLeast31() {
        return SDK >= 31 || SDK == 30 && PREVIEW_SDK > 0;
    }

    /** 是否为Android 11及以上 Whether Android 11+ */
    public static boolean atLeast30() {
        return SDK >= 30;
    }

    /** 是否为Android 10及以上 Whether Android 10+ */
    public static boolean atLeast29() {
        return SDK >= 29;
    }

    /** 是否为Android 9及以上 Whether Android 9+ */
    public static boolean atLeast28() {
        return SDK >= 28;
    }

    /** 是否为Android 8.0及以上 Whether Android 8.0+ */
    public static boolean atLeast26() {
        return SDK >= 26;
    }

    /** 是否为Android 7.0及以上 Whether Android 7.0+ */
    public static boolean atLeast24() {
        return SDK >= 24;
    }

    /** 是否为Android 6.0及以上 Whether Android 6.0+ */
    public static boolean atLeast23() {
        return SDK >= 23;
    }
}

