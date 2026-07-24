package roro.stellar.server.binder

import android.content.IContentProvider
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import com.stellar.api.BinderContainer
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.DeviceIdleControllerApis
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.api.IContentProviderUtils
import roro.stellar.server.shizuku.ShizukuApiConstants
import roro.stellar.server.shizuku.ShizukuServiceIntercept
import roro.stellar.server.util.Logger

object BinderDistributor {
    private val LOGGER = Logger("BinderDistributor")

    fun sendBinderToManager(binder: Binder?) {
        sendBinderToManager(binder, 0)
    }

    fun sendBinderToManager(binder: Binder?, userId: Int) {
        sendBinderInternal(
            packageName = MANAGER_APPLICATION_ID,
            userId = userId,
            providerSuffix = ".stellar",
            extraKey = "roro.stellar.manager.intent.extra.BINDER",
            binderContainer = BinderContainer(binder),
            logPrefix = "",
            retry = true,
            onRetry = { sendBinderToManager(binder, userId, retry = false) }
        )
    }

    private fun sendBinderToManager(binder: Binder?, userId: Int, retry: Boolean): Boolean {
        return sendBinderInternal(
            packageName = MANAGER_APPLICATION_ID,
            userId = userId,
            providerSuffix = ".stellar",
            extraKey = "roro.stellar.manager.intent.extra.BINDER",
            binderContainer = BinderContainer(binder),
            logPrefix = "",
            retry = retry,
            onRetry = { sendBinderToManager(binder, userId, retry = false) }
        )
    }

    fun sendBinderToUserApp(
        binder: Binder?,
        packageName: String?,
        userId: Int,
        retry: Boolean = true
    ): Boolean {
        return sendBinderInternal(
            packageName = packageName,
            userId = userId,
            providerSuffix = ".stellar",
            extraKey = "roro.stellar.manager.intent.extra.BINDER",
            binderContainer = BinderContainer(binder),
            logPrefix = "",
            retry = retry,
            onRetry = { sendBinderToUserApp(binder, packageName, userId, false) }
        )
    }

    fun sendShizukuBinderToUserApp(
        shizukuIntercept: ShizukuServiceIntercept?,
        packageName: String?,
        userId: Int,
        retry: Boolean = true
    ): Boolean {
        if (shizukuIntercept == null || packageName == null) return false

        return sendBinderInternal(
            packageName = packageName,
            userId = userId,
            providerSuffix = ".shizuku",
            extraKey = ShizukuApiConstants.EXTRA_BINDER,
            binderContainer = moe.shizuku.api.BinderContainer(shizukuIntercept.asBinder()),
            logPrefix = "Shizuku ",
            retry = retry,
            onRetry = { sendShizukuBinderToUserApp(shizukuIntercept, packageName, userId, false) }
        )
    }

    private fun sendBinderInternal(
        packageName: String?,
        userId: Int,
        providerSuffix: String,
        extraKey: String,
        binderContainer: Parcelable,
        logPrefix: String,
        retry: Boolean,
        onRetry: (() -> Unit)?
    ): Boolean {
        if (packageName == null) return false

        try {
            DeviceIdleControllerApis.addPowerSaveTempWhitelistApp(
                packageName, 30_000L, userId, 316, "shell"
            )
            LOGGER.v("已将用户 %d 的 %s 加入省电临时白名单 30 秒", userId, packageName)
        } catch (tr: Throwable) {
            LOGGER.e(tr, "将用户 %d 的 %s 加入省电临时白名单失败", userId, packageName)
        }

        val name = "$packageName$providerSuffix"
        var provider: IContentProvider? = null
        val token: IBinder? = null

        try {
            provider = ActivityManagerApis.getContentProviderExternal(name, userId, token, name)
            if (provider == null) {
                LOGGER.e("%sProvider 为 null：%s，userId=%d", logPrefix, name, userId)
                return false
            }
            if (!provider.asBinder().pingBinder()) {
                LOGGER.e("%sProvider 已失效：%s，userId=%d", logPrefix, name, userId)
                if (retry && onRetry != null) {
                    LOGGER.w("准备重试向用户 %d 的 %s 发送 %sBinder", userId, packageName, logPrefix)
                    Thread.sleep(300)
                    onRetry()
                }
                return false
            }

            val extra = Bundle()
            extra.putParcelable(extraKey, binderContainer)

            val reply = IContentProviderUtils.callCompat(provider, null, name, "sendBinder", null, extra)
            if (reply != null) {
                LOGGER.i("已向用户 %d 的应用 %s 发送 %sBinder", userId, packageName, logPrefix)
                return true
            }

            LOGGER.w("向用户 %d 的应用 %s 发送 %sBinder 失败：Provider 返回 null", userId, packageName, logPrefix)
            if (retry && onRetry != null) {
                LOGGER.w("准备重试向用户 %d 的应用 %s 发送 %sBinder", userId, packageName, logPrefix)
                Thread.sleep(300)
                onRetry()
            }
            return false
        } catch (tr: Throwable) {
            LOGGER.e(tr, "向用户 %d 的应用 %s 发送 %sBinder 失败", userId, packageName, logPrefix)
            return false
        } finally {
            if (provider != null) {
                try {
                    ActivityManagerApis.removeContentProviderExternal(name, token)
                } catch (tr: Throwable) {
                    LOGGER.w(tr, "移除 ContentProvider 失败：%s", name)
                }
            }
        }
    }
}
