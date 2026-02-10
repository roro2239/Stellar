package roro.stellar.server.service.userservice

import android.os.Bundle
import android.os.IBinder
import com.stellar.server.IUserServiceCallback
import roro.stellar.server.userservice.UserServiceManager

class UserServiceCoordinator(
    private val userServiceManager: UserServiceManager
) {
    fun startUserService(
        uid: Int,
        pid: Int,
        args: Bundle?,
        callback: IUserServiceCallback?
    ): String? {
        if (args == null) return null

        return userServiceManager.startUserService(uid, pid, args, callback)
    }

    fun stopUserService(token: String?) {
        if (token == null) return

        userServiceManager.stopUserService(token)
    }

    fun attachUserService(binder: IBinder?, options: Bundle?) {
        if (binder == null || options == null) return

        userServiceManager.attachUserService(binder, options)
    }

    fun getUserServiceCount(uid: Int): Int {
        return userServiceManager.getUserServiceCount(uid)
    }
}
