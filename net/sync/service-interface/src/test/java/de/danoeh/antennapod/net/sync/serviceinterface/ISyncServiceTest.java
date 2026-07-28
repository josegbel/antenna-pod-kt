package de.danoeh.antennapod.net.sync.serviceinterface;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * Characterizes {@link ISyncService} against the live Java implementation. The nested
 * implementation below, declaring {@code throws SyncServiceException} on all six overrides and
 * caught through the interface type, is a compile-time guard: it fails to compile unless the
 * converted interface carries {@code @Throws(SyncServiceException::class)} on every method.
 */
public class ISyncServiceTest {

    private static class TestSyncService implements ISyncService {
        boolean throwOnLogin;
        boolean logoutCalled;

        @Override
        public void login() throws SyncServiceException {
            if (throwOnLogin) {
                throw new SyncServiceException("login failed");
            }
        }

        @Override
        public SubscriptionChanges getSubscriptionChanges(long lastSync) throws SyncServiceException {
            return new SubscriptionChanges(Collections.emptyList(), Collections.emptyList(), lastSync);
        }

        @Override
        public UploadChangesResponse uploadSubscriptionChanges(List<String> addedFeeds, List<String> removedFeeds)
                throws SyncServiceException {
            return new UploadChangesResponse(addedFeeds.size() + removedFeeds.size()) { };
        }

        @Override
        public EpisodeActionChanges getEpisodeActionChanges(long lastSync) throws SyncServiceException {
            return new EpisodeActionChanges(Collections.emptyList(), lastSync);
        }

        @Override
        public UploadChangesResponse uploadEpisodeActions(List<EpisodeAction> queuedEpisodeActions)
                throws SyncServiceException {
            return new UploadChangesResponse(queuedEpisodeActions.size()) { };
        }

        @Override
        public void logout() throws SyncServiceException {
            logoutCalled = true;
        }
    }

    @Test
    public void implementorCanBeInvokedThroughTheInterfaceType() throws SyncServiceException {
        ISyncService service = new TestSyncService();

        service.login();
        SubscriptionChanges changes = service.getSubscriptionChanges(100L);
        service.logout();

        assertEquals(100L, changes.getTimestamp());
    }

    @Test
    public void checkedExceptionIsCatchableThroughTheInterfaceType() {
        TestSyncService impl = new TestSyncService();
        impl.throwOnLogin = true;
        ISyncService service = impl;

        try {
            service.login();
            fail("expected SyncServiceException");
        } catch (SyncServiceException expected) {
            assertEquals("login failed", expected.getMessage());
        }
    }

    @Test
    public void uploadSubscriptionChangesAcceptsListOfStrings() throws SyncServiceException {
        ISyncService service = new TestSyncService();

        UploadChangesResponse response = service.uploadSubscriptionChanges(
                Collections.singletonList("added"), Collections.singletonList("removed"));

        assertEquals(2L, response.timestamp);
    }

    @Test
    public void uploadEpisodeActionsAcceptsListOfEpisodeAction() throws SyncServiceException {
        ISyncService service = new TestSyncService();
        EpisodeAction action = new EpisodeAction.Builder("p", "e", EpisodeAction.Action.PLAY).build();

        UploadChangesResponse response = service.uploadEpisodeActions(Collections.singletonList(action));

        assertEquals(1L, response.timestamp);
    }
}
