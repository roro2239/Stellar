package roro.stellar.server.service.log

import roro.stellar.server.ClientManager
import roro.stellar.server.util.Logger

class LogManager(private val clientManager: ClientManager) {
    fun getLogs(): List<String> = Logger.getLogsFormatted()

    fun getLogsForUid(uid: Int): List<String>? {
        val record = clientManager.findClients(uid).firstOrNull() ?: return null
        return Logger.getLogsSince(record.attachTime)
    }

    fun clearLogs() = Logger.clearLogs()
}
