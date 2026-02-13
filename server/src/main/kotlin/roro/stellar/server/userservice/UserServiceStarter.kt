package roro.stellar.server.userservice

import android.app.ActivityThread
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.ContextHidden
import android.content.IContentProvider
import android.ddm.DdmHandleAppName
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.UserHandle
import android.os.UserHandleHidden
import android.util.Log
import com.stellar.api.BinderContainer
import dev.rikka.tools.refine.Refine
import rikka.hidden.compat.ActivityManagerApis
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
        var debugName: String? = null

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
                arg.startsWith("--debug-name=") ->
                    debugName = arg.substringAfter("--debug-name=").trim('\'')
            }
        }

        if (token == null || packageName == null || className == null || uid == -1) {
            Log.e(TAG, "缺少必需参数")
            System.exit(1)
            return
        }

        val userId = uid / 100000

        Log.i(TAG, "启动 UserService: package=$packageName, class=$className, uid=$uid, userId=$userId")

        val userBinder = createUserService(packageName, className, userId, debugName)
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

    private fun createUserService(packageName: String, className: String, userId: Int, debugName: String?): IBinder? {
        Log.i(TAG, "创建 UserService: $packageName/$className")

        return try {
            val activityThread = ActivityThread.systemMain()
            val systemContext = activityThread.systemContext
            DdmHandleAppName.setAppName(debugName ?: "$packageName:user_service", userId)
            val userHandle: UserHandle = Refine.unsafeCast(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    UserHandleHidden.of(userId)
                } else {
                    UserHandleHidden(userId)
                }
            )
            val context = Refine.unsafeCast<ContextHidden>(systemContext).createPackageContextAsUser(
                packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
                userHandle
            )
            val mPackageInfo = context::class.java.getDeclaredField("mPackageInfo")
            mPackageInfo.isAccessible = true
            val loadedApk = mPackageInfo.get(context)

            val makeApplication = loadedApk.javaClass.getDeclaredMethod(
                "makeApplication",
                Boolean::class.javaPrimitiveType,
                Instrumentation::class.java
            )
            val application = makeApplication.invoke(loadedApk, true, null) as Application

            val mInitialApplication = activityThread.javaClass.getDeclaredField("mInitialApplication")
            mInitialApplication.isAccessible = true
            mInitialApplication.set(activityThread, application)

            Log.i(TAG, "Application 创建成功: ${application.javaClass.name}")

            val classLoader = application.classLoader
            val serviceClass = classLoader.loadClass(className)

            val instance = try {
                val constructorWithContext = serviceClass.getConstructor(Context::class.java)
                constructorWithContext.newInstance(application)
            } catch (e: NoSuchMethodException) {
                val constructor = serviceClass.getDeclaredConstructor()
                constructor.isAccessible = true
                constructor.newInstance()
            }

            when (instance) {
                is IBinder -> {
                    Log.i(TAG, "UserService 实例创建成功: $className")
                    instance
                }
                else -> {
                    Log.e(TAG, "服务类不是 Binder 的子类: ${instance.javaClass.name}")
                    null
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "创建 UserService 失败", e)
            null
        }
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
