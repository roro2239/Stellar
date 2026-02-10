package roro.stellar.server.service.log

import roro.stellar.server.util.Logger

class LogManager {
    fun getLogs(): List<String> {
        return Logger.getLogsFormatted()
    }

    fun clearLogs() {
        Logger.clearLogs()
    }
}
