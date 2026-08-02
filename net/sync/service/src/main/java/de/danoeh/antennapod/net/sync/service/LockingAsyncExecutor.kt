package de.danoeh.antennapod.net.sync.service

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.locks.ReentrantLock

object LockingAsyncExecutor {

    private val lock = ReentrantLock()

    /**
     * Take the lock and execute runnable (to prevent changes to preferences being lost when enqueueing while sync is
     * in progress). If the lock is free, the runnable is directly executed in the calling thread to prevent overhead.
     */
    @JvmStatic
    fun executeLockedAsync(runnable: Runnable?) {
        if (lock.tryLock()) {
            try {
                // Uncontended: a null runnable throws here, inside the try, so the finally below
                // still releases the lock -- same NPE, same thread, same site as Java's bare call.
                runnable!!.run()
            } finally {
                lock.unlock()
            }
        } else {
            Completable.fromRunnable {
                lock.lock()
                try {
                    // Contended: this runs on Schedulers.computation() after subscribe() returns,
                    // so a null runnable's NPE is delivered to RxJavaPlugins' global error handler,
                    // never to executeLockedAsync's caller -- preserved, not repaired (D0).
                    runnable!!.run()
                } finally {
                    lock.unlock()
                }
            }.subscribeOn(Schedulers.computation())
                .subscribe()
        }
    }

    @JvmStatic
    fun unlock() {
        lock.unlock()
    }

    @JvmStatic
    fun lock() {
        lock.lock()
    }
}
