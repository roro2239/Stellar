package roro.stellar.manager.model

import roro.stellar.Stellar

data class ServiceStatus(
        val uid: Int = -1,
        val apiVersion: Int = -1,
        val patchVersion: Int = -1,
        val seContext: String? = null
) {
    val isRunning: Boolean
        get() = uid != -1 && Stellar.pingBinder()
}

