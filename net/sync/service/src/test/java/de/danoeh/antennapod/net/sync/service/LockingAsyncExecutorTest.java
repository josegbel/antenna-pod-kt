package de.danoeh.antennapod.net.sync.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.plugins.RxJavaPlugins;

/**
 * Characterizes {@link LockingAsyncExecutor} against the live Java implementation: a single
 * process-wide ReentrantLock with zero prior test coverage. Every test in this file must leave
 * the process-wide lock free and the RxJavaPlugins error handler reset (null) on exit, since both
 * are process-wide state shared across the whole suite's single JVM fork.
 */
public class LockingAsyncExecutorTest {

    @After
    public void resetRxErrorHandler() {
        RxJavaPlugins.setErrorHandler(null);
    }

    @Test
    public void executeLockedAsyncRunsTheRunnableOnTheCallingThreadWhenUncontended() {
        Thread callingThread = Thread.currentThread();
        AtomicReference<Thread> ranOnThread = new AtomicReference<>();

        LockingAsyncExecutor.executeLockedAsync(() -> ranOnThread.set(Thread.currentThread()));

        assertSame(callingThread, ranOnThread.get());
    }

    @Test
    public void executeLockedAsyncReleasesTheLockAfterAnUncontendedRun() {
        LockingAsyncExecutor.executeLockedAsync(() -> { });

        Thread callingThread = Thread.currentThread();
        AtomicReference<Thread> ranOnThread = new AtomicReference<>();
        LockingAsyncExecutor.executeLockedAsync(() -> ranOnThread.set(Thread.currentThread()));

        assertSame(callingThread, ranOnThread.get());
    }

    @Test
    public void executeLockedAsyncDefersToAnotherThreadWhenContended() throws InterruptedException {
        CountDownLatch holderHasLock = new CountDownLatch(1);
        CountDownLatch releaseHolderLock = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            LockingAsyncExecutor.lock();
            holderHasLock.countDown();
            await(releaseHolderLock);
            LockingAsyncExecutor.unlock();
        });
        holder.start();
        assertTrue(holderHasLock.await(5, TimeUnit.SECONDS));

        Thread callingThread = Thread.currentThread();
        AtomicReference<Thread> ranOnThread = new AtomicReference<>();
        CountDownLatch ran = new CountDownLatch(1);
        LockingAsyncExecutor.executeLockedAsync(() -> {
            ranOnThread.set(Thread.currentThread());
            ran.countDown();
        });

        assertEquals(1, ran.getCount());

        releaseHolderLock.countDown();
        assertTrue(ran.await(5, TimeUnit.SECONDS));
        holder.join(5000);

        assertNotSame(callingThread, ranOnThread.get());
    }

    @Test
    public void lockIsReentrantSoALockedRunnableMayLockAgain() {
        LockingAsyncExecutor.lock();
        try {
            AtomicReference<Boolean> ran = new AtomicReference<>(false);
            LockingAsyncExecutor.executeLockedAsync(() -> ran.set(true));
            assertTrue(ran.get());
        } finally {
            LockingAsyncExecutor.unlock();
        }
    }

    @Test
    public void unlockWithoutHoldingTheLockThrowsIllegalMonitorStateException() {
        assertThrows(IllegalMonitorStateException.class, LockingAsyncExecutor::unlock);
    }

    @Test
    public void executeLockedAsyncWithNullRunnableThrowsNullPointerExceptionWhenUncontended() {
        assertThrows(NullPointerException.class, () -> LockingAsyncExecutor.executeLockedAsync(null));

        Thread callingThread = Thread.currentThread();
        AtomicReference<Thread> ranOnThread = new AtomicReference<>();
        LockingAsyncExecutor.executeLockedAsync(() -> ranOnThread.set(Thread.currentThread()));
        assertSame(callingThread, ranOnThread.get());
    }

    @Test
    public void executeLockedAsyncWithNullRunnableWhenContendedReturnsNormallyAndDeliversTheNpeToTheRxGlobalErrorHandler()
            throws InterruptedException {
        CountDownLatch holderHasLock = new CountDownLatch(1);
        CountDownLatch releaseHolderLock = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            LockingAsyncExecutor.lock();
            holderHasLock.countDown();
            await(releaseHolderLock);
            LockingAsyncExecutor.unlock();
        });
        holder.start();
        assertTrue(holderHasLock.await(5, TimeUnit.SECONDS));

        AtomicReference<Throwable> delivered = new AtomicReference<>();
        CountDownLatch errorDelivered = new CountDownLatch(1);
        RxJavaPlugins.setErrorHandler(throwable -> {
            delivered.set(throwable);
            errorDelivered.countDown();
        });

        LockingAsyncExecutor.executeLockedAsync(null);

        releaseHolderLock.countDown();
        assertTrue(errorDelivered.await(5, TimeUnit.SECONDS));
        holder.join(5000);

        Throwable observed = delivered.get();
        boolean foundNpeInCausalChain = false;
        while (observed != null) {
            if (observed instanceof NullPointerException) {
                foundNpeInCausalChain = true;
                break;
            }
            observed = observed.getCause();
        }
        assertTrue(foundNpeInCausalChain);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
