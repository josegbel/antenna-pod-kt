package de.danoeh.antennapod.net.sync.serviceinterface

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterizes [UploadChangesResponse] against the live Java implementation. The nested
 * subclass below, reading the inherited `timestamp` field unqualified, is a compile-time
 * guard: it fails to compile unless the converted class exposes `timestamp` as a genuine
 * public field (`@JvmField`) rather than a private backing field plus accessor.
 */
class UploadChangesResponseTest {

    private class TestUploadChangesResponse(timestamp: Long) : UploadChangesResponse(timestamp) {
        fun describe(): String {
            return "timestamp=$timestamp"
        }
    }

    @Test
    fun constructorRoundTripsTimestamp() {
        val response: UploadChangesResponse = TestUploadChangesResponse(42L)

        assertEquals(42L, response.timestamp)
    }

    @Test
    fun subclassReadsInheritedTimestampFieldUnqualified() {
        val response = TestUploadChangesResponse(99L)

        assertEquals("timestamp=99", response.describe())
    }

    @Test
    fun timestampIsAPublicFinalFieldNotAnAccessor() {
        val field: Field = UploadChangesResponse::class.java.getField("timestamp")
        val modifiers = field.modifiers

        assertTrue(Modifier.isPublic(modifiers))
        assertTrue(Modifier.isFinal(modifiers))
        assertFalse(Modifier.isStatic(modifiers))

        var hasGetter = false
        for (method in UploadChangesResponse::class.java.methods) {
            if (method.name == "getTimestamp") {
                hasGetter = true
            }
        }
        assertFalse("UploadChangesResponse must not expose a getTimestamp() accessor", hasGetter)
    }
}
