package de.danoeh.antennapod.net.sync.serviceinterface;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Characterizes {@link SynchronizationQueue}'s static holder and {@link SynchronizationQueueStub}
 * against the live Java implementation. Captures and restores the previous instance around every
 * test so this suite cannot pollute or be polluted by other tests sharing the same JVM fork.
 */
public class SynchronizationQueueTest {

    private SynchronizationQueue previousInstance;

    @Before
    public void captureExistingInstance() {
        previousInstance = SynchronizationQueue.getInstance();
    }

    @After
    public void restoreExistingInstance() {
        SynchronizationQueue.setInstance(previousInstance);
    }

    @Test
    public void setInstanceThenGetInstanceReturnsSameInstanceNotACopy() {
        SynchronizationQueueStub stub = new SynchronizationQueueStub();
        SynchronizationQueue.setInstance(stub);

        assertSame(stub, SynchronizationQueue.getInstance());
    }

    @Test
    public void setInstanceNullYieldsNullGetInstance() {
        SynchronizationQueue.setInstance(null);

        assertNull(SynchronizationQueue.getInstance());
    }

    @Test
    public void stubAcceptsNullArgumentsWithoutThrowing() {
        SynchronizationQueueStub stub = new SynchronizationQueueStub();

        stub.sync();
        stub.syncImmediately();
        stub.fullSync();
        stub.syncIfNotSyncedRecently();
        stub.clear();
        stub.enqueueFeedAdded(null);
        stub.enqueueFeedRemoved(null);
        stub.enqueueEpisodeAction(null);
        stub.enqueueEpisodePlayed(null, true);
    }
}
