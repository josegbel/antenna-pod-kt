package de.danoeh.antennapod.net.sync.serviceinterface

import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Characterizes [SynchronizationQueue]'s static holder and [SynchronizationQueueStub]
 * against the live Java implementation. Captures and restores the previous instance around every
 * test so this suite cannot pollute or be polluted by other tests sharing the same JVM fork.
 */
class SynchronizationQueueTest {

    private var previousInstance: SynchronizationQueue? = null

    @Before
    fun captureExistingInstance() {
        previousInstance = SynchronizationQueue.instance
    }

    @After
    fun restoreExistingInstance() {
        SynchronizationQueue.instance = previousInstance
    }

    @Test
    fun setInstanceThenGetInstanceReturnsSameInstanceNotACopy() {
        val stub = SynchronizationQueueStub()
        SynchronizationQueue.instance = stub

        assertSame(stub, SynchronizationQueue.instance)
    }

    @Test
    fun setInstanceNullYieldsNullGetInstance() {
        SynchronizationQueue.instance = null

        assertNull(SynchronizationQueue.instance)
    }

    @Test
    fun stubAcceptsNullArgumentsWithoutThrowing() {
        val stub = SynchronizationQueueStub()

        stub.sync()
        stub.syncImmediately()
        stub.fullSync()
        stub.syncIfNotSyncedRecently()
        stub.clear()
        stub.enqueueFeedAdded(null)
        stub.enqueueFeedRemoved(null)
        stub.enqueueEpisodeAction(null)
        stub.enqueueEpisodePlayed(null, true)
    }
}
