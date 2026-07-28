package de.danoeh.antennapod.net.sync.serviceinterface;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;

/**
 * Characterizes {@link UploadChangesResponse} against the live Java implementation. The nested
 * subclass below, reading the inherited {@code timestamp} field unqualified, is a compile-time
 * guard: it fails to compile unless the converted class exposes {@code timestamp} as a genuine
 * public field ({@code @JvmField}) rather than a private backing field plus accessor.
 */
public class UploadChangesResponseTest {

    private static class TestUploadChangesResponse extends UploadChangesResponse {
        TestUploadChangesResponse(long timestamp) {
            super(timestamp);
        }

        String describe() {
            return "timestamp=" + timestamp;
        }
    }

    @Test
    public void constructorRoundTripsTimestamp() {
        UploadChangesResponse response = new TestUploadChangesResponse(42L);

        assertEquals(42L, response.timestamp);
    }

    @Test
    public void subclassReadsInheritedTimestampFieldUnqualified() {
        TestUploadChangesResponse response = new TestUploadChangesResponse(99L);

        assertEquals("timestamp=99", response.describe());
    }

    @Test
    public void timestampIsAPublicFinalFieldNotAnAccessor() throws NoSuchFieldException {
        Field field = UploadChangesResponse.class.getField("timestamp");
        int modifiers = field.getModifiers();

        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));
        assertFalse(Modifier.isStatic(modifiers));

        boolean hasGetter = false;
        for (Method method : UploadChangesResponse.class.getMethods()) {
            if (method.getName().equals("getTimestamp")) {
                hasGetter = true;
            }
        }
        assertFalse("UploadChangesResponse must not expose a getTimestamp() accessor", hasGetter);
    }
}
