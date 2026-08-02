package de.danoeh.antennapod.net.sync.serviceinterface;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.Test;

/**
 * Characterizes {@link SyncServiceException} against the live Java implementation. The nested
 * subclass below, calling both {@code super(...)} forms, is a compile-time guard: it fails to
 * compile unless the converted class stays {@code open}.
 */
public class SyncServiceExceptionTest {

    private static class TestSyncServiceException extends SyncServiceException {
        TestSyncServiceException(String message) {
            super(message);
        }

        TestSyncServiceException(Throwable cause) {
            super(cause);
        }
    }

    @Test
    public void messageConstructorRoundTripsMessage() {
        SyncServiceException exception = new SyncServiceException("boom");

        assertEquals("boom", exception.getMessage());
    }

    @Test
    public void causeConstructorRoundTripsCause() {
        Throwable cause = new IllegalStateException("root cause");
        SyncServiceException exception = new SyncServiceException(cause);

        assertSame(cause, exception.getCause());
    }

    @Test
    public void causeConstructorMakesMessageTheCausesToString() {
        Throwable cause = new IllegalStateException("root cause");
        SyncServiceException exception = new SyncServiceException(cause);

        assertEquals(cause.toString(), exception.getMessage());
    }

    @Test
    public void isCheckedNotRuntimeException() {
        assertTrue(Exception.class.isAssignableFrom(SyncServiceException.class));
        assertFalse(RuntimeException.class.isAssignableFrom(SyncServiceException.class));
    }

    @Test
    public void serialVersionUidIsDeclaredOnTheExceptionClassItself() throws NoSuchFieldException {
        Field field = SyncServiceException.class.getDeclaredField("serialVersionUID");
        int modifiers = field.getModifiers();

        assertTrue(Modifier.isPrivate(modifiers));
        assertTrue(Modifier.isStatic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));
    }

    @Test
    public void serialVersionUidValueIsOne() throws NoSuchFieldException, IllegalAccessException {
        Field field = SyncServiceException.class.getDeclaredField("serialVersionUID");
        field.setAccessible(true);

        assertEquals(1L, field.getLong(null));
    }

    @Test
    public void subclassCanCallBothSuperConstructors() {
        TestSyncServiceException fromMessage = new TestSyncServiceException("message-form");
        TestSyncServiceException fromCause = new TestSyncServiceException(new RuntimeException("cause-form"));

        assertEquals("message-form", fromMessage.getMessage());
        assertEquals("cause-form", fromCause.getCause().getMessage());
    }
}
