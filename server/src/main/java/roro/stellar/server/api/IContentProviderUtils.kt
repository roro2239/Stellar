package roro.stellar.server.api

import android.content.AttributionSource
import android.content.IContentProvider
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import roro.stellar.server.util.OsUtils.uid

/**
 * IContentProvider兼容工具类（服务端版本）
 * IContentProvider Compatibility Utility (Server Version)
 *
 *
 * 功能说明 Features：
 *
 *  * 兼容不同Android版本的IContentProvider.call()方法 - Compatible with different Android versions' IContentProvider.call() method
 *  * 处理Android 10-12的API变化 - Handles API changes in Android 10-12
 *  * 支持AttributionSource（Android 12+） - Supports AttributionSource (Android 12+)
 *
 *
 *
 * 版本差异 Version Differences：
 *
 *  * Android 12+ (API 31+): 使用AttributionSource
 *  * Android 11 (API 30): 使用attributeTag参数（传null）
 *  * Android 10 (API 29): 使用authority参数
 *  * Android 9- (API 28-): 基本参数
 *
 */
object IContentProviderUtils {
    /**
     * 调用ContentProvider方法（兼容所有Android版本）
     * Call ContentProvider method (compatible with all Android versions)
     *
     * @param provider ContentProvider实例
     * @param callingPkg 调用包名
     * @param authority Provider权限
     * @param method 方法名
     * @param arg 参数
     * @param extras 额外数据
     * @return 返回结果
     * @throws RemoteException 远程调用异常
     */
    @Throws(RemoteException::class)
    fun callCompat(
        provider: IContentProvider,
        callingPkg: String?,
        authority: String?,
        method: String?,
        arg: String?,
        extras: Bundle?
    ): Bundle? {
        val result: Bundle?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: 使用AttributionSource
            result = provider.call(
                (AttributionSource.Builder(uid)).setPackageName(callingPkg).build(),
                authority,
                method,
                arg,
                extras
            )
        } else if (Build.VERSION.SDK_INT >= 30) {
            // Android 11: 支持attributeTag（传null）
            result = provider.call(callingPkg, null as String?, authority, method, arg, extras)
        } else if (Build.VERSION.SDK_INT >= 29) {
            // Android 10: 支持authority
            result = provider.call(callingPkg, authority, method, arg, extras)
        } else {
            // Android 9及以下: 基本参数
            result = provider.call(callingPkg, method, arg, extras)
        }

        return result
    }
}