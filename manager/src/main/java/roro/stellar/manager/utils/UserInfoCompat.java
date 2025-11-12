package roro.stellar.manager.utils;

/**
 * 用户信息兼容类
 * User Info Compatibility Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>封装Android用户信息 - Wraps Android user information</li>
 * <li>简化用户信息存储和传递 - Simplifies user info storage and transfer</li>
 * <li>兼容多用户系统 - Compatible with multi-user system</li>
 * </ul>
 * 
 * <p>包含信息 Information：</p>
 * <ul>
 * <li>用户ID - User ID</li>
 * <li>用户名称 - User name</li>
 * </ul>
 */
public class UserInfoCompat {

    /** 用户ID User ID */
    public final int id;
    /** 用户名称 User name */
    public final String name;

    /**
     * 构造用户信息
     * Construct user info
     * 
     * @param id 用户ID
     * @param name 用户名称
     */
    public UserInfoCompat(int id, String name) {
        this.id = id;
        this.name = name;
    }
}

