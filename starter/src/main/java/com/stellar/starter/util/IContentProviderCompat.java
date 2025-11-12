package com.stellar.starter.util;

import android.content.AttributionSource;
import android.content.IContentProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * IContentProvider兼容工具类
 * IContentProvider Compatibility Utility
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>兼容不同Android版本的IContentProvider.call()方法 - Compatible with different Android versions' IContentProvider.call() method</li>
 * <li>处理Android 11-12的参数变化 - Handles parameter changes in Android 11-12</li>
 * <li>支持AttributionSource（Android 12+） - Supports AttributionSource (Android 12+)</li>
 * </ul>
 * 
 * <p>版本差异 Version Differences：</p>
 * <ul>
 * <li>Android 12+ (API 31+): 使用AttributionSource</li>
 * <li>Android 11 (API 30): 使用attributeTag参数</li>
 * <li>Android 10 (API 29): 使用callingPkg参数</li>
 * <li>Android 9- (API 28-): 基本参数</li>
 * </ul>
 */
public class IContentProviderCompat {

    /**
     * 调用ContentProvider方法（兼容所有Android版本）
     * Call ContentProvider method (compatible with all Android versions)
     * 
     * @param provider ContentProvider实例
     * @param attributeTag 归因标签（API 30+）
     * @param callingPkg 调用包名
     * @param authority Provider权限
     * @param method 方法名
     * @param arg 参数
     * @param extras 额外数据
     * @return 返回结果
     * @throws RemoteException 远程调用异常
     */
    @Nullable
    public static Bundle call(@NonNull IContentProvider provider, @Nullable String attributeTag, @Nullable String callingPkg, @Nullable String authority, @Nullable String method, @Nullable String arg, @Nullable Bundle extras) throws RemoteException {
        Bundle reply;
        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12+: 使用AttributionSource
            try {
                reply = provider.call((new AttributionSource.Builder(Os.getuid())).setAttributionTag(attributeTag).setPackageName(callingPkg).build(), authority, method, arg, extras);
            } catch (LinkageError e) {
                // 回退到API 30的方法
                reply = provider.call(callingPkg, attributeTag, authority, method, arg, extras);
            }
        } else if (Build.VERSION.SDK_INT >= 30) {
            // Android 11: 支持attributeTag
            reply = provider.call(callingPkg, attributeTag, authority, method, arg, extras);
        } else if (Build.VERSION.SDK_INT >= 29) {
            // Android 10: 支持authority
            reply = provider.call(callingPkg, authority, method, arg, extras);
        } else {
            // Android 9及以下: 基本参数
            reply = provider.call(callingPkg, method, arg, extras);
        }

        return reply;
    }
}

