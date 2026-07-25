package de.danoeh.antennapod.model.download

import java.net.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class ProxyConfigTest {

    @Test
    fun testFieldAssignment() {
        val config = ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 1234, "user", "pass")

        assertEquals(Proxy.Type.HTTP, config.type)
        assertEquals("proxy.example.com", config.host)
        assertEquals(1234, config.port)
        assertEquals("user", config.username)
        assertEquals("pass", config.password)
    }

    @Test
    fun testNullableFieldsAcceptNull() {
        val config = ProxyConfig(Proxy.Type.DIRECT, null, 0, null, null)

        assertEquals(Proxy.Type.DIRECT, config.type)
        assertNull(config.host)
        assertEquals(0, config.port)
        assertNull(config.username)
        assertNull(config.password)
    }

    @Test
    fun testDefaultPortConstant() {
        assertEquals(8080, ProxyConfig.DEFAULT_PORT)
    }

    @Test
    fun testReferenceEqualityPin() {
        val config1 = ProxyConfig(Proxy.Type.HTTP, "host", 80, "u", "p")
        val config2 = ProxyConfig(Proxy.Type.HTTP, "host", 80, "u", "p")

        // No equals()/hashCode() defined: two same-content instances must NOT be equal.
        assertNotSame(config1, config2)
        assertFalse(config1.equals(config2))
    }
}
