package roro.stellar.manager.util

import android.system.Os

object UserHandleCompat {
    const val PER_USER_RANGE = 100000
    private val MY_USER_ID = getUserId(Os.getuid())

    fun getUserId(uid: Int): Int = uid / PER_USER_RANGE

    fun myUserId(): Int = MY_USER_ID
}
