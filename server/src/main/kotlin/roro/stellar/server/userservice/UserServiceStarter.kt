package roro.stellar.server.userservice

import android.content.IContentProvider
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.stellar.api.BinderContainer
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.PackageManagerApis
import roro.stellar.server.ServerConstants
import roro.stellar.server.api.IContentProviderUtils

object UserServiceStarter {
    private const val TAG = "StellarUserServiceStarter"
    private const val EXTRA_BINDER = "roro.stellar.manager.intent.extra.BINDER"
    private const val EXTRA_CLIENT_BINDER = "roro.stellar.manager.intent.extra.CLIENT_BINDER"

    @Suppress("FieldCanBeLocal")
    private var stellarBinder: IBinder? = null
    private var clientBinder: IBinder? = null

    @JvmStatic
    fun main(args: Array<String>) {
        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper()
        }

        var token: String? = null
        var packageName: String? = null
        var className: String? = null
        var uid: Int = -1
        var serviceMode: Int = UserServiceConstants.MODE_ONE_TIME
        var verificationToken: String? = null

        for (arg in args) {
            when {
                arg.startsWith("--token=") ->
                    token = arg.substringAfter("--token=").trim('\'')
                arg.startsWith("--package=") ->
                    packageName = arg.substringAfter("--package=").trim('\'')
                arg.startsWith("--class=") ->
                    className = arg.substringAfter("--class=").trim('\'')
                arg.startsWith("--uid=") ->
                    uid = arg.substringAfter("--uid=").toIntOrNull() ?: -1
                arg.startsWith("--mode=") ->
                    serviceMode = arg.substringAfter("--mode=").toIntOrNull()
                        ?: UserServiceConstants.MODE_ONE_TIME
                arg.startsWith("--verification-token=") ->
                    verificationToken = arg.substringAfter("--verification-token=").trim('\'')
            }
        }

        if (token == null || packageName == null || className == null || uid == -1) {
            Log.e(TAG, "缺少必需参数")
            System.exit(1)
            return
        }

        Log.i(TAG, "启动 UserService: package=$packageName, class=$className, uid=$uid")

        val userBinder = createUserService(packageName, className)
        if (userBinder == null) {
            Log.e(TAG, "创建 UserService 实例失败")
            System.exit(1)
            return
        }

        val serviceBinder = UserServiceBinder(userBinder)
        Log.i(TAG, "已创建 UserServiceBinder 包装")

        if (!sendBinderToServer(serviceBinder, token, packageName, className, uid, serviceMode, verificationToken ?: "")) {
            Log.e(TAG, "发送 Binder 到服务器失败")
            System.exit(1)
            return
        }

        Log.i(TAG, "UserService 启动成功")

        Looper.loop()

        Log.i(TAG, "UserService 退出")
        System.exit(0)
    }

    private fun createUserService(packageName: String, className: String): IBinder? {
        return try {
            val classLoader = createClassLoader(packageName)
            val serviceClass = classLoader.loadClass(className)

            val constructor = serviceClass.getDeclaredConstructor()
            constructor.isAccessible = true
            val instance = constructor.newInstance()

            when (instance) {
                is IBinder -> instance
                else -> {
                    Log.e(TAG, "服务类不是 Binder 的子类")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建 UserService 失败", e)
            null
        }
    }

    private fun createClassLoader(packageName: String): ClassLoader {
        val userId = 0
        val ai = PackageManagerApis.getApplicationInfoNoThrow(packageName, 0, userId)
            ?: throw IllegalStateException("包未找到: $packageName")

        val apkPath = ai.sourceDir
        val libraryPath = ai.nativeLibraryDir

        Log.i(TAG, "使用 APK 直接加载: $apkPath")
        return dalvik.system.PathClassLoader(
            apkPath,
            libraryPath,
            UserServiceStarter::class.java.classLoader
        )
    }

    private fun sendBinderToServer(
        serviceBinder: IBinder,
        token: String,
        packageName: String,
        className: String,
        uid: Int,
        serviceMode: Int,
        verificationToken: String
    ): Boolean {
        // 连接到管理器应用的 provider，而不是客户端应用的 provider
        // 这样可以同时支持 Stellar 客户端和 Shizuku 客户端
        val providerName = "${ServerConstants.MANAGER_APPLICATION_ID}.stellar"
        Log.i(TAG, "连接到 Provider: $providerName (客户端包名: $packageName)")

        val userId = 0
        var provider: IContentProvider? = null

        return try {
            provider = ActivityManagerApis.getContentProviderExternal(
                providerName, userId, null, providerName
            )

            if (provider == null) {
                Log.e(TAG, "Provider 为 null")
                return false
            }

            if (!provider.asBinder().pingBinder()) {
                Log.e(TAG, "Provider 已死亡")
                return false
            }

            val extra = Bundle().apply {
                putParcelable(EXTRA_BINDER, BinderContainer(serviceBinder))
                putString(UserServiceConstants.OPT_TOKEN, token)
                putString(UserServiceConstants.OPT_PACKAGE_NAME, packageName)
                putString(UserServiceConstants.OPT_CLASS_NAME, className)
                putInt(UserServiceConstants.OPT_UID, uid)
                putInt(UserServiceConstants.OPT_PID, android.os.Process.myPid())
                putString(UserServiceConstants.OPT_VERIFICATION_TOKEN, verificationToken)
            }

            val reply = IContentProviderUtils.callCompat(
                provider, null, providerName, "sendUserService", null, extra
            )

            if (reply != null) {
                reply.classLoader = BinderContainer::class.java.classLoader
                val container = reply.getParcelable<BinderContainer>(EXTRA_BINDER)

                if (container?.binder != null && container.binder!!.pingBinder()) {
                    stellarBinder = container.binder
                    stellarBinder!!.linkToDeath({
                        Log.i(TAG, "Stellar 服务器已死亡")
                        if (serviceMode == UserServiceConstants.MODE_ONE_TIME) {
                            Log.i(TAG, "一次性模式，Stellar 服务器死亡，退出...")
                            System.exit(0)
                        } else {
                            Log.i(TAG, "守护模式，继续运行...")
                        }
                    }, 0)

                    val clientContainer = reply.getParcelable<BinderContainer>(EXTRA_CLIENT_BINDER)
                    if (clientContainer?.binder != null && clientContainer.binder!!.pingBinder()) {
                        clientBinder = clientContainer.binder
                        if (serviceMode == UserServiceConstants.MODE_ONE_TIME) {
                            clientBinder!!.linkToDeath({
                                Log.i(TAG, "客户端 App 已退出，一次性模式，服务退出...")
                                System.exit(0)
                            }, 0)
                            Log.i(TAG, "已设置客户端死亡监听（一次性模式）")
                        }
                    }

                    return true
                }
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "发送 Binder 到服务器失败", e)
            false
        } finally {
            provider?.let {
                try {
                    ActivityManagerApis.removeContentProviderExternal(providerName, null)
                } catch (e: Exception) {
                    Log.w(TAG, "removeContentProviderExternal 失败", e)
                }
            }
        }
    }
}
