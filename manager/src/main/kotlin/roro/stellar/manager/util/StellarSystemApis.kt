package roro.stellar.manager.util

import rikka.hidden.compat.UserManagerApis
import rikka.hidden.compat.util.SystemServiceBinder
import roro.stellar.Stellar
import roro.stellar.StellarBinderWrapper

object StellarSystemApis {

    init {
        SystemServiceBinder.setOnGetBinderListener {
            return@setOnGetBinderListener StellarBinderWrapper(it)
        }
    }

    private val users = arrayListOf<UserInfoCompat>()

    private fun getUsers(): List<UserInfoCompat> =
        if (!Stellar.pingBinder()) {
            listOf(UserInfoCompat(UserHandleCompat.myUserId(), "Owner"))
        } else try {
            UserManagerApis.getUsers(true, true, true).map { UserInfoCompat(it.id, it.name) }
        } catch (_: Throwable) {
            listOf(UserInfoCompat(UserHandleCompat.myUserId(), "Owner"))
        }

    fun getUsers(useCache: Boolean = true): List<UserInfoCompat> {
        synchronized(users) {
            if (!useCache || users.isEmpty()) {
                users.clear()
                users.addAll(getUsers())
            }
            return users
        }
    }

    fun getUserInfo(userId: Int): UserInfoCompat =
        getUsers(useCache = true).firstOrNull { it.id == userId }
            ?: UserInfoCompat(UserHandleCompat.myUserId(), "Unknown")
}

