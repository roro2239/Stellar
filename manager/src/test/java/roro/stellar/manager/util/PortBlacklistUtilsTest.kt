package roro.stellar.manager.util

import org.junit.Assert.*
import org.junit.Test

class PortBlacklistUtilsTest {

    @Test
    fun testBlacklistedPorts() {
        assertTrue(PortBlacklistUtils.isPortBlacklisted(5555))
        assertTrue(PortBlacklistUtils.isPortBlacklisted(5556))
        assertTrue(PortBlacklistUtils.isPortBlacklisted(5037))
        assertTrue(PortBlacklistUtils.isPortBlacklisted(8080))
        assertTrue(PortBlacklistUtils.isPortBlacklisted(4444))
    }

    @Test
    fun testNonBlacklistedPorts() {
        assertFalse(PortBlacklistUtils.isPortBlacklisted(8765))
        assertFalse(PortBlacklistUtils.isPortBlacklisted(12345))
        assertFalse(PortBlacklistUtils.isPortBlacklisted(30000))
    }

    @Test
    fun testGenerateSafeRandomPort() {
        repeat(100) {
            val port = PortBlacklistUtils.generateSafeRandomPort(1000, 9999, 100)
            assertTrue("Generated port $port should not be -1", port != -1)
            assertFalse("Generated port $port should not be blacklisted", 
                PortBlacklistUtils.isPortBlacklisted(port))
            assertTrue("Generated port $port should be in range", port in 1000..9999)
        }
    }

    @Test
    fun testPortValidation() {
        assertFalse(PortBlacklistUtils.isPortValidAndSafe(0, false))
        assertFalse(PortBlacklistUtils.isPortValidAndSafe(65536, false))
        assertFalse(PortBlacklistUtils.isPortValidAndSafe(5555, false))
        assertTrue(PortBlacklistUtils.isPortValidAndSafe(8765, false))
        
        assertTrue(PortBlacklistUtils.isPortValidAndSafe(5555, true))
        assertTrue(PortBlacklistUtils.isPortValidAndSafe(8080, true))
        assertFalse(PortBlacklistUtils.isPortValidAndSafe(0, true))
        assertFalse(PortBlacklistUtils.isPortValidAndSafe(65536, true))
    }

    @Test
    fun testGetBlacklistedPorts() {
        val blacklist = PortBlacklistUtils.getBlacklistedPorts()
        assertTrue(blacklist.isNotEmpty())
        assertTrue(blacklist.contains(5555))
        assertTrue(blacklist.contains(8080))
    }
}
