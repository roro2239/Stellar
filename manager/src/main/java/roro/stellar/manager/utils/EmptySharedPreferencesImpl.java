package roro.stellar.manager.utils;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 空SharedPreferences实现
 * Empty SharedPreferences Implementation
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>提供SharedPreferences的空实现 - Provides empty implementation of SharedPreferences</li>
 * <li>所有读取操作返回默认值 - All read operations return default values</li>
 * <li>所有写入操作不执行任何动作 - All write operations do nothing</li>
 * <li>用于禁用某些功能时的占位符 - Used as placeholder when certain features are disabled</li>
 * </ul>
 * 
 * <p>使用场景 Use Cases：</p>
 * <ul>
 * <li>设备锁定时禁用配置存储 - Disable config storage when device is locked</li>
 * <li>测试环境中避免实际写入 - Avoid actual writes in test environment</li>
 * </ul>
 */
public class EmptySharedPreferencesImpl implements SharedPreferences {

    /**
     * 获取所有键值对
     * Get all key-value pairs
     * 
     * @return 空Map
     */
    @Override
    public Map<String, ?> getAll() {
        return new HashMap<>();
    }

    /**
     * 获取字符串值
     * Get string value
     * 
     * @return 始终返回默认值
     */
    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        return defValue;
    }

    /**
     * 获取字符串集合
     * Get string set
     * 
     * @return 始终返回默认值
     */
    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        return defValues;
    }

    /**
     * 获取整数值
     * Get int value
     * 
     * @return 始终返回默认值
     */
    @Override
    public int getInt(String key, int defValue) {
        return defValue;
    }

    /**
     * 获取长整数值
     * Get long value
     * 
     * @return 始终返回默认值
     */
    @Override
    public long getLong(String key, long defValue) {
        return defValue;
    }

    /**
     * 获取浮点数值
     * Get float value
     * 
     * @return 始终返回默认值
     */
    @Override
    public float getFloat(String key, float defValue) {
        return defValue;
    }

    /**
     * 获取布尔值
     * Get boolean value
     * 
     * @return 始终返回默认值
     */
    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return defValue;
    }

    /**
     * 检查是否包含指定键
     * Check if contains key
     * 
     * @return 始终返回false
     */
    @Override
    public boolean contains(String key) {
        return false;
    }

    /**
     * 创建编辑器
     * Create editor
     * 
     * @return 空实现的Editor
     */
    @Override
    public Editor edit() {
        return new EditorImpl();
    }

    /**
     * 注册监听器（空实现）
     * Register listener (empty implementation)
     */
    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {

    }

    /**
     * 注销监听器（空实现）
     * Unregister listener (empty implementation)
     */
    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {

    }

    /**
     * 空Editor实现
     * Empty Editor Implementation
     * 
     * 所有写入操作不执行任何动作
     * All write operations do nothing
     */
    private static class EditorImpl implements Editor {

        @Override
        public Editor putString(String key, @Nullable String value) {
            return this;
        }

        @Override
        public Editor putStringSet(String key, @Nullable Set<String> values) {
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            return this;
        }

        @Override
        public Editor remove(String key) {
            return this;
        }

        @Override
        public Editor clear() {
            return this;
        }

        @Override
        public boolean commit() {
            return true;
        }

        @Override
        public void apply() {

        }
    }
}

