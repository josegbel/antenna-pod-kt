package de.danoeh.antennapod.event;

import android.content.Context;

import androidx.core.util.Consumer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MessageEventTest {

    @Test
    public void oneArgConstructorStoresMessageAndLeavesActionAndActionTextNull() {
        MessageEvent event = new MessageEvent("hello");
        assertEquals("hello", event.message);
        assertNull(event.action);
        assertNull(event.actionText);
    }

    @Test
    public void threeArgConstructorStoresAllThreeFields() {
        Consumer<Context> action = ignored -> { };
        MessageEvent event = new MessageEvent("hello", action, "Undo");
        assertEquals("hello", event.message);
        assertSame(action, event.action);
        assertEquals("Undo", event.actionText);
    }

    @Test
    public void actionIsStoredByIdentityAndInvokedViaAccept() {
        boolean[] invoked = new boolean[1];
        Consumer<Context> action = context -> invoked[0] = true;
        MessageEvent event = new MessageEvent("hello", action, "Undo");
        assertSame(action, event.action);
        event.action.accept(null);
        assertTrue(invoked[0]);
    }
}
