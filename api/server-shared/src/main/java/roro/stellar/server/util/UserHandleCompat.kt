package roro.stellar.server.util

/**
 * 用户句柄兼容工具类
 * User Handle Compatibility Utility
 *
 *
 * 功能说明 Features：
 *
 *  * 处理多用户UID计算 - Handles multi-user UID calculation
 *  * 提取用户ID和应用ID - Extracts user ID and app ID
 *  * 兼容Android多用户系统 - Compatible with Android multi-user system
 *
 *
 *
 * UID结构 UID Structure：
 *
 *  * UID = userId * 100000 + appId
 *  * userId: 用户ID（0表示主用户，10+表示其他用户）
 *  * appId: 应用ID（相同应用在不同用户下appId相同）
 *
 */
object UserHandleCompat {
    /** 每个用户的UID范围 UID range per user  */
    const val PER_USER_RANGE: Int = 100000

    /**
     * 从UID获取用户ID
     * Get user ID from UID
     *
     * @param uid 完整的UID
     * @return 用户ID
     */
    fun getUserId(uid: Int): Int {
        return uid / PER_USER_RANGE
    }

    /**
     * 从UID获取应用ID
     * Get app ID from UID
     *
     * @param uid 完整的UID
     * @return 应用ID
     */
    fun getAppId(uid: Int): Int {
        return uid % PER_USER_RANGE
    }
}