package roro.stellar.server.communication

import android.os.Binder
import roro.stellar.server.util.UserHandleCompat.getUserId

data class CallerContext(
    val uid: Int,
    val pid: Int,
    val packageName: String? = null
) {
    val userId: Int = getUserId(uid)

    companion object {
        fun fromBinder(): CallerContext {
            return CallerContext(
                uid = Binder.getCallingUid(),
                pid = Binder.getCallingPid()
            )
        }
    }
}
