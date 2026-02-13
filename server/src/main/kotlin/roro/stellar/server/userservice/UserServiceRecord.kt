package roro.stellar.server.userservice

import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.os.Parcel
import com.stellar.server.IUserServiceCallback
import roro.stellar.server.util.Logger

class UserServiceRecord(
    val token: String,
    val callingUid: Int,
    val callingPid: Int,
    val packageName: String,
    val className: String,
    val processNameSuffix: String,
    val versionCode: Long,
    val serviceMode: Int,
    val verificationToken: String,
    val callback: IUserServiceCallback?,
    private val onRemove: (UserServiceRecord) -> Unit
) {
    companion object {
        private val LOGGER = Logger("UserServiceRecord")
    }

    var serviceBinder: IBinder? = null
        private set

    var servicePid: Int = -1
        private set

    var started: Boolean = false

    private var removed: Boolean = false

    val isConnected: Boolean
        get() = serviceBinder?.pingBinder() == true

    private var deathRecipient: DeathRecipient? = null

    fun onServiceAttached(binder: IBinder, pid: Int) {
        serviceBinder = binder
        servicePid = pid

        deathRecipient = DeathRecipient {
            LOGGER.i("UserService 已死亡: token=%s, package=%s, class=%s",
                token, packageName, className)
            onServiceDied()
        }

        try {
            binder.linkToDeath(deathRecipient!!, 0)
        } catch (e: Exception) {
            LOGGER.w(e, "链接死亡监听失败")
        }

        try {
            callback?.onServiceConnected(binder, verificationToken)
        } catch (e: Exception) {
            LOGGER.w(e, "通知服务连接失败")
        }
    }

    private fun onServiceDied() {
        try {
            callback?.onServiceDisconnected()
        } catch (e: Exception) {
            LOGGER.w(e, "通知服务断开失败")
        }
        removeSelf()
    }

    fun removeSelf() {
        if (removed) {
            LOGGER.d("服务已被移除，跳过: token=%s", token)
            return
        }
        removed = true

        LOGGER.i("停止服务: %s", className)

        serviceBinder?.let { binder ->
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                binder.transact(UserServiceConstants.TRANSACTION_DESTROY, data, reply, 0)
                reply.readException()
                LOGGER.i("已调用服务内置 destroy()")
            } catch (e: Exception) {
                LOGGER.w(e, "调用服务 destroy() 失败")
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        deathRecipient?.let { recipient ->
            serviceBinder?.unlinkToDeath(recipient, 0)
        }

        try {
            callback?.onServiceDisconnected()
        } catch (e: Exception) {
            LOGGER.w(e, "通知服务断开失败")
        }

        onRemove(this)
    }

    fun getKey(): String {
        return "$packageName:$className:$processNameSuffix"
    }
}
