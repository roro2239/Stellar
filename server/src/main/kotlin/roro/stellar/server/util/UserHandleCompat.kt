package roro.stellar.server.util

object UserHandleCompat {
    const val PER_USER_RANGE: Int = 100000

    fun getUserId(uid: Int): Int = uid / PER_USER_RANGE

    fun getAppId(uid: Int): Int = uid % PER_USER_RANGE
}