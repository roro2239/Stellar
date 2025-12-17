package roro.stellar.manager.util

object PortBlacklistUtils {
    
    private val BLACKLIST_PORTS = setOf(
        5555, 5556, 5557, 5558, 5559, 5037,
        8080, 8888, 9999, 6666, 7777, 1234,
        4444, 3389, 22, 23, 21, 80, 443,
        3306, 5432, 27017, 6379
    )
    
    fun isPortBlacklisted(port: Int): Boolean {
        return BLACKLIST_PORTS.contains(port)
    }
    
    fun generateSafeRandomPort(
        minPort: Int = 1000,
        maxPort: Int = 9999,
        maxAttempts: Int = 100
    ): Int {
        var attempts = 0
        while (attempts < maxAttempts) {
            val port = minPort + (0..(maxPort - minPort)).random()
            if (!isPortBlacklisted(port)) {
                return port
            }
            attempts++
        }
        return -1
    }
    
    fun isPortValidAndSafe(port: Int, isManualInput: Boolean = false): Boolean {
        if (port !in 1..65535) {
            return false
        }
        
        if (isManualInput) {
            return true
        }
        
        return !isPortBlacklisted(port)
    }
    
    fun getBlacklistedPorts(): Set<Int> {
        return BLACKLIST_PORTS.toSet()
    }
}
