package de.danoeh.antennapod.ui.preferences.screen.synchronization;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Looper;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

/**
 * Java oracle for AuthenticationDialog's external subclass shape (D8, README convention #9 of
 * :net:sync:service-interface). The two nested classes mirror, respectively,
 * FeedSettingsPreferenceFragment.java:193 (anonymous, overrides only onConfirmed) and
 * OnlineFeedViewActivity.java:503 (named, overrides both hooks and calls super.onCancelled()).
 * Both are constructed with both usernameInitialValue and passwordInitialValue as literal null --
 * the shape OnlineFeedViewActivity.java:508 reaches in production on the first
 * ERROR_UNAUTHORIZED, when its backing username/password fields (:84-85) are still null.
 */
@RunWith(RobolectricTestRunner.class)
public class AuthenticationDialogJavaInteropTest {

    private static final class FeedViewAuthenticationDialog extends AuthenticationDialog {
        private boolean superOnCancelledCalled = false;

        FeedViewAuthenticationDialog(AppCompatActivity activity, String username, String password) {
            super(activity, android.R.string.ok, true, username, password);
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            superOnCancelledCalled = true;
        }

        @Override
        protected void onConfirmed(String username, String password) {
        }
    }

    @Test
    public void testJavaSubclassOverrideShapes() {
        AppCompatActivity activity = Robolectric.buildActivity(SyncSettingsTestHost.class).setup().get();

        AuthenticationDialog anonymousSubclass = new AuthenticationDialog(
                activity, android.R.string.ok, false, null, null) {
            @Override
            protected void onConfirmed(String username, String password) {
            }
        };
        AlertDialog anonymousDialog = anonymousSubclass.show();
        assertNotNull(anonymousDialog);

        FeedViewAuthenticationDialog namedSubclass = new FeedViewAuthenticationDialog(activity, null, null);
        AlertDialog namedDialog = namedSubclass.show();
        assertNotNull(namedDialog);
        namedDialog.cancel();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(namedSubclass.superOnCancelledCalled);
    }
}
