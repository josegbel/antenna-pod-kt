package de.danoeh.antennapod.net.sync.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;

import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;

/**
 * Reflection-only contract test substituting for androidx.work:work-testing (D12, declined).
 * Pins WorkManager's persisted class name and reflective constructor call, and this module's one
 * inbound call site (ClientConfigurator.java:53) -- none of it is checked by any compiler in this
 * repo, and it loads classes and inspects members without invoking any Android stub, so it needs
 * no Robolectric.
 */
public class SyncServiceWorkerContractTest {

    @Test
    public void syncServiceClassResolvesByItsPersistedFullyQualifiedName() throws ClassNotFoundException {
        Class<?> clazz = Class.forName("de.danoeh.antennapod.net.sync.service.SyncService");

        assertTrue(Modifier.isPublic(clazz.getModifiers()));
        assertFalse(Modifier.isAbstract(clazz.getModifiers()));
        assertTrue(Worker.class.isAssignableFrom(clazz));
    }

    @Test
    public void syncServiceHasAPublicContextWorkerParametersConstructor() throws Exception {
        Class<?> clazz = Class.forName("de.danoeh.antennapod.net.sync.service.SyncService");
        Constructor<?> constructor = clazz.getDeclaredConstructor(Context.class, WorkerParameters.class);

        assertTrue(Modifier.isPublic(constructor.getModifiers()));
    }

    @Test
    public void syncServiceDoWorkIsPublic() throws Exception {
        Class<?> clazz = Class.forName("de.danoeh.antennapod.net.sync.service.SyncService");
        Method doWork = clazz.getMethod("doWork");

        assertTrue(Modifier.isPublic(doWork.getModifiers()));
    }

    @Test
    public void syncServiceTagIsPublicStaticFinalString() throws Exception {
        Field tag = SyncService.class.getField("TAG");

        assertEquals(String.class, tag.getType());
        int modifiers = tag.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isStatic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));
    }

    @Test
    public void episodeActionFilterTagIsPublicStaticFinalString() throws Exception {
        Field tag = EpisodeActionFilter.class.getField("TAG");

        assertEquals(String.class, tag.getType());
        int modifiers = tag.getModifiers();
        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isStatic(modifiers));
        assertTrue(Modifier.isFinal(modifiers));
    }

    @Test
    public void synchronizationQueueImplHasAPublicContextConstructorAndExtendsSynchronizationQueue()
            throws Exception {
        Constructor<SynchronizationQueueImpl> constructor =
                SynchronizationQueueImpl.class.getDeclaredConstructor(Context.class);

        assertTrue(Modifier.isPublic(constructor.getModifiers()));
        assertTrue(SynchronizationQueue.class.isAssignableFrom(SynchronizationQueueImpl.class));
    }

    @Test
    public void guidValidatorIsValidGuidIsStatic() throws Exception {
        Method method = GuidValidator.class.getMethod("isValidGuid", String.class);

        assertTrue(Modifier.isStatic(method.getModifiers()));
    }

    @Test
    public void episodeActionFilterGetRemoteActionsOverridingLocalActionsIsStatic() throws Exception {
        Method method = EpisodeActionFilter.class.getMethod(
                "getRemoteActionsOverridingLocalActions", List.class, List.class);

        assertTrue(Modifier.isStatic(method.getModifiers()));
    }

    @Test
    public void lockingAsyncExecutorsThreeMethodsAreStatic() throws Exception {
        Method executeLockedAsync = LockingAsyncExecutor.class.getMethod("executeLockedAsync", Runnable.class);
        Method lock = LockingAsyncExecutor.class.getMethod("lock");
        Method unlock = LockingAsyncExecutor.class.getMethod("unlock");

        assertTrue(Modifier.isStatic(executeLockedAsync.getModifiers()));
        assertTrue(Modifier.isStatic(lock.getModifiers()));
        assertTrue(Modifier.isStatic(unlock.getModifiers()));
    }

    @Test
    public void synchronizationQueueStorageCrossClassMethodsKeepTheirJavaNames() throws Exception {
        Class<?> clazz = SynchronizationQueueStorage.class;

        assertNotNull(clazz.getDeclaredMethod("clearQueue"));
        assertNotNull(clazz.getDeclaredMethod("enqueueFeedAdded", String.class));
        assertNotNull(clazz.getDeclaredMethod("enqueueFeedRemoved", String.class));
        assertNotNull(clazz.getDeclaredMethod("enqueueEpisodeAction", EpisodeAction.class));
        assertNotNull(clazz.getDeclaredMethod("removeLegacyConflictingFeedEntries", Collection.class));
    }
}
