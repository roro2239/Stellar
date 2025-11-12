package roro.stellar.server.util;

/**
 * 用户句柄兼容工具类
 * User Handle Compatibility Utility
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>处理多用户UID计算 - Handles multi-user UID calculation</li>
 * <li>提取用户ID和应用ID - Extracts user ID and app ID</li>
 * <li>兼容Android多用户系统 - Compatible with Android multi-user system</li>
 * </ul>
 * 
 * <p>UID结构 UID Structure：</p>
 * <ul>
 * <li>UID = userId * 100000 + appId</li>
 * <li>userId: 用户ID（0表示主用户，10+表示其他用户）</li>
 * <li>appId: 应用ID（相同应用在不同用户下appId相同）</li>
 * </ul>
 */
public class UserHandleCompat {

    /** 每个用户的UID范围 UID range per user */
    public static final int PER_USER_RANGE = 100000;

    /**
     * 从UID获取用户ID
     * Get user ID from UID
     * 
     * @param uid 完整的UID
     * @return 用户ID
     */
    public static int getUserId(int uid) {
        return uid / PER_USER_RANGE;
    }

    /**
     * 从UID获取应用ID
     * Get app ID from UID
     * 
     * @param uid 完整的UID
     * @return 应用ID
     */
    public static int getAppId(int uid) {
        return uid % PER_USER_RANGE;
    }
}


