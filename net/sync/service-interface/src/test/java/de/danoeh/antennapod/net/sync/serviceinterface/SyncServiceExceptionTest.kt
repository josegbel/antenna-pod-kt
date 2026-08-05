package de.danoeh.antennapod.net.sync.serviceinterface

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterizes [SyncServiceException] against the live Java implementation. The nested
 * subclass below, calling both `super(...)` forms, is a compile-time guard: it fails to
 * compile unless the converted class stays `open`.
 */
class SyncServiceExceptionTest {

    private class TestSyncServiceException : SyncServiceException {
        constructor(message: String?) : super(message)
        constructor(cause: Throwable?) : super(cause)
    }

    @Test
    fun messageConstructorRoundTripsMessage() {
        val exception = SyncServiceException("boom")

        assertEquals("boom", exception.message)
    }

    @Test
    fun causeConstructorRoundTripsCause() {
        val cause = IllegalStateException("root cause")
        val exception = SyncServiceException(cause)

        assertSame(cause, exception.cause)
    }

    @Test
    fun causeConstructorMakesMessageTheCausesToString() {
        val cause = IllegalStateException("root cause")
        val exception = SyncServiceException(cause)

        assertEquals(cause.toString(), exception.message)
    }

    @Test
    fun isCheckedNotRuntimeException() {
        assertTrue(Exception::class.java.isAssignableFrom(SyncServiceException::class.java))
        assertFalse(RuntimeException::class.java.isAssignableFrom(SyncServiceException::class.java))
    }

    @Test
    fun serialVersionUidIsDeclaredOnTheExceptionClassItself() {
        val field: Field = SyncServiceException::class.java.getDeclaredField("serialVersionUID")
        val modifiers = field.modifiers

        assertTrue(Modifier.isPrivate(modifiers))
        assertTrue(Modifier.isStatic(modifiers))
        assertTrue(Modifier.isFinal(modifiers))
    }

    @Test
    fun serialVersionUidValueIsOne() {
        val field: Field = SyncServiceException::class.java.getDeclaredField("serialVersionUID")
        field.isAccessible = true

        assertEquals(1L, field.getLong(null))
    }

    @Test
    fun subclassCanCallBothSuperConstructors() {
        val fromMessage = TestSyncServiceException("message-form")
        val fromCause = TestSyncServiceException(RuntimeException("cause-form"))

        assertEquals("message-form", fromMessage.message)
        assertEquals("cause-form", fromCause.cause!!.message)
    }
}
