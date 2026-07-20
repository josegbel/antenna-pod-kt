package de.danoeh.antennapod.model.download;

import org.junit.Test;

import java.net.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

public class ProxyConfigTest {

    @Test
    public void testFieldAssignment() {
        ProxyConfig config = new ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 1234, "user", "pass");

        assertEquals(Proxy.Type.HTTP, config.type);
        assertEquals("proxy.example.com", config.host);
        assertEquals(1234, config.port);
        assertEquals("user", config.username);
        assertEquals("pass", config.password);
    }

    @Test
    public void testNullableFieldsAcceptNull() {
        ProxyConfig config = new ProxyConfig(Proxy.Type.DIRECT, null, 0, null, null);

        assertEquals(Proxy.Type.DIRECT, config.type);
        assertNull(config.host);
        assertEquals(0, config.port);
        assertNull(config.username);
        assertNull(config.password);
    }

    @Test
    public void testDefaultPortConstant() {
        assertEquals(8080, ProxyConfig.DEFAULT_PORT);
    }

    @Test
    public void testReferenceEqualityPin() {
        ProxyConfig config1 = new ProxyConfig(Proxy.Type.HTTP, "host", 80, "u", "p");
        ProxyConfig config2 = new ProxyConfig(Proxy.Type.HTTP, "host", 80, "u", "p");

        // No equals()/hashCode() defined: two same-content instances must NOT be equal.
        assertNotSame(config1, config2);
        assertFalse(config1.equals(config2));
    }
}
