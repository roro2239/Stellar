package roro.stellar.server.binder

import android.content.IContentProvider
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import com.stellar.api.BinderContainer
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.DeviceIdleControllerApis
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.UserManagerApis
import roro.stellar.StellarApiConstants
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.api.IContentProviderUtils
import roro.stellar.server.shizuku.ShizukuApiConstants
import roro.stellar.server.shizuku.ShizukuServiceIntercept
import roro.stellar.server.util.Logger

object BinderDistributor {
    private val LOGGER = Logger("BinderDistributor")

    fun sendBinderToAllClients(binder: Binder?) {
        for (userId in UserManagerApis.getUserIdsNoThrow()) {
            sendBinderToClients(binder, userId)
        }
    }

    fun sendBinderToClients(binder: Binder?, userId: Int) {
        try {
            for (pi in PackageManagerApis.getInstalledPackagesNoThrow(
                PackageManager.GET_META_DATA.toLong(),
                userId
            )) {
                if (pi == null || pi.applicationInfo == null || pi.applicationInfo!!.metaData == null) continue

                if (pi.applicationInfo!!.metaData.getString(StellarApiConstants.PERMISSION_KEY, "").split(",")
                        .contains("stellar")
                ) {
                    sendBinderToUserApp(binder, pi.packageName, userId)
                }
            }
        } catch (tr: Throwable) {
            LOGGER.e("调用 getInstalledPackages 时发生异常", tr = tr)
        }
    }

    fun sendBinderToManager(binder: Binder?) {
        for (userId in UserManagerApis.getUserIdsNoThrow()) {
            sendBinderToManager(binder, userId)
        }
    }

    fun sendBinderToManager(binder: Binder?, userId: Int) {
        sendBinderToUserApp(binder, MANAGER_APPLICATION_ID, userId)
    }

    fun sendBinderToUserApp(
        binder: Binder?,
        packageName: String?,
        userId: Int,
        retry: Boolean = true
    ) {
        sendBinderInternal(
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
        userId: Int
    ) {
        if (shizukuIntercept == null || packageName == null) return

        sendBinderInternal(
            packageName = packageName,
            userId = userId,
            providerSuffix = ".shizuku",
            extraKey = ShizukuApiConstants.EXTRA_BINDER,
            binderContainer = moe.shizuku.api.BinderContainer(shizukuIntercept.asBinder()),
            logPrefix = "Shizuku ",
            retry = false,
            onRetry = null
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
    ) {
        if (packageName == null) return

        try {
            DeviceIdleControllerApis.addPowerSaveTempWhitelistApp(
                packageName, 30_000L, userId, 316, "shell"
            )
            LOGGER.v("将 %d:%s 添加到省电临时白名单 30 秒", userId, packageName)
        } catch (tr: Throwable) {
            LOGGER.e(tr, "添加 %d:%s 到省电临时白名单失败", userId, packageName)
        }

        val name = "$packageName$providerSuffix"
        var provider: IContentProvider? = null
        val token: IBinder? = null

        try {
            provider = ActivityManagerApis.getContentProviderExternal(name, userId, token, name)
            if (provider == null) {
                LOGGER.e("${logPrefix}provider 为 null %s %d", name, userId)
                return
            }
            if (!provider.asBinder().pingBinder()) {
                LOGGER.e("${logPrefix}provider 已失效 %s %d", name, userId)
                if (retry && onRetry != null) {
                    ActivityManagerApis.forceStopPackageNoThrow(packageName, userId)
                    LOGGER.e("终止用户 %d 中的 %s 并重试", userId, packageName)
                    Thread.sleep(1000)
                    onRetry()
                }
                return
            }

            val extra = Bundle()
            extra.putParcelable(extraKey, binderContainer)

            val reply = IContentProviderUtils.callCompat(provider, null, name, "sendBinder", null, extra)
            if (reply != null) {
                LOGGER.i("已向用户 %d 中的应用 %s 发送 ${logPrefix}binder", userId, packageName)
            } else {
                LOGGER.w("向用户 %d 中的应用 %s 发送 ${logPrefix}binder 失败", userId, packageName)
            }
        } catch (tr: Throwable) {
            LOGGER.e(tr, "向用户 %d 中的应用 %s 发送 ${logPrefix}binder 失败", userId, packageName)
        } finally {
            if (provider != null) {
                try {
                    ActivityManagerApis.removeContentProviderExternal(name, token)
                } catch (tr: Throwable) {
                    LOGGER.w(tr, "移除 ContentProvider 失败")
                }
            }
        }
    }
}
