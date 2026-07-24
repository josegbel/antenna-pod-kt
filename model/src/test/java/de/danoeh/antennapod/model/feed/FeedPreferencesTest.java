package de.danoeh.antennapod.model.feed;

import android.text.TextUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

// android.text.TextUtils is an unmocked Android SDK stub in :model's bare-JVM unit tests
// (no Robolectric, per module policy). FeedPreferences.getTagsAsString() calls TextUtils.join(),
// which throws "not mocked" if invoked for real here. A MockedStatic gives it its real AOSP
// semantics so this test characterizes FeedPreferences' own logic against genuine device
// behavior rather than the stub's throw.
public class FeedPreferencesTest {

    private MockedStatic<TextUtils> textUtilsMock;

    @Before
    public void setUp() {
        textUtilsMock = mockStatic(TextUtils.class);
        textUtilsMock.when(() -> TextUtils.join(any(), (Iterable<?>) any())).thenAnswer(invocation -> {
            CharSequence delimiter = invocation.getArgument(0);
            Iterable<?> tokens = invocation.getArgument(1);
            StringBuilder sb = new StringBuilder();
            boolean firstTime = true;
            for (Object token : tokens) {
                if (firstTime) {
                    firstTime = false;
                } else {
                    sb.append(delimiter);
                }
                sb.append(token);
            }
            return sb.toString();
        });
    }

    @After
    public void tearDown() {
        textUtilsMock.close();
    }

    private FeedPreferences newPreferences(float feedPlaybackSpeed, FeedPreferences.SkipSilence feedSkipSilence,
                                            Set<String> tags) {
        return new FeedPreferences(1, FeedPreferences.AutoDownloadSetting.GLOBAL, true,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, "user", "pass",
                new FeedFilter(), feedPlaybackSpeed, 0, 0, feedSkipSilence, false,
                FeedPreferences.NewEpisodesAction.GLOBAL, tags);
    }

    @Test
    public void autoDeleteActionCodesAndFromCode() {
        assertEquals(0, FeedPreferences.AutoDeleteAction.GLOBAL.code);
        assertEquals(1, FeedPreferences.AutoDeleteAction.ALWAYS.code);
        assertEquals(2, FeedPreferences.AutoDeleteAction.NEVER.code);

        assertEquals(FeedPreferences.AutoDeleteAction.GLOBAL, FeedPreferences.AutoDeleteAction.fromCode(0));
        assertEquals(FeedPreferences.AutoDeleteAction.ALWAYS, FeedPreferences.AutoDeleteAction.fromCode(1));
        assertEquals(FeedPreferences.AutoDeleteAction.NEVER, FeedPreferences.AutoDeleteAction.fromCode(2));
        assertEquals(FeedPreferences.AutoDeleteAction.NEVER, FeedPreferences.AutoDeleteAction.fromCode(99));
    }

    @Test
    public void newEpisodesActionCodesAndFromCode() {
        assertEquals(0, FeedPreferences.NewEpisodesAction.GLOBAL.code);
        assertEquals(1, FeedPreferences.NewEpisodesAction.ADD_TO_INBOX.code);
        assertEquals(3, FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE.code);
        assertEquals(2, FeedPreferences.NewEpisodesAction.NOTHING.code);

        assertEquals(FeedPreferences.NewEpisodesAction.GLOBAL, FeedPreferences.NewEpisodesAction.fromCode(0));
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX, FeedPreferences.NewEpisodesAction.fromCode(1));
        assertEquals(FeedPreferences.NewEpisodesAction.NOTHING, FeedPreferences.NewEpisodesAction.fromCode(2));
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE, FeedPreferences.NewEpisodesAction.fromCode(3));
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX, FeedPreferences.NewEpisodesAction.fromCode(99));
    }

    @Test
    public void skipSilenceCodesAndFromCode() {
        assertEquals(0, FeedPreferences.SkipSilence.OFF.code);
        assertEquals(1, FeedPreferences.SkipSilence.GLOBAL.code);
        assertEquals(2, FeedPreferences.SkipSilence.AGGRESSIVE.code);

        assertEquals(FeedPreferences.SkipSilence.OFF, FeedPreferences.SkipSilence.fromCode(0));
        assertEquals(FeedPreferences.SkipSilence.GLOBAL, FeedPreferences.SkipSilence.fromCode(1));
        assertEquals(FeedPreferences.SkipSilence.AGGRESSIVE, FeedPreferences.SkipSilence.fromCode(2));
        assertEquals(FeedPreferences.SkipSilence.GLOBAL, FeedPreferences.SkipSilence.fromCode(99));
    }

    @Test
    public void autoDownloadSettingCodesAndFactories() {
        assertEquals(0, FeedPreferences.AutoDownloadSetting.DISABLED.code);
        assertEquals(2, FeedPreferences.AutoDownloadSetting.ENABLED.code);
        assertEquals(1, FeedPreferences.AutoDownloadSetting.GLOBAL.code);

        assertEquals(FeedPreferences.AutoDownloadSetting.DISABLED, FeedPreferences.AutoDownloadSetting.fromInteger(0));
        assertEquals(FeedPreferences.AutoDownloadSetting.ENABLED, FeedPreferences.AutoDownloadSetting.fromInteger(2));
        assertEquals(FeedPreferences.AutoDownloadSetting.GLOBAL, FeedPreferences.AutoDownloadSetting.fromInteger(1));
        assertEquals(FeedPreferences.AutoDownloadSetting.GLOBAL, FeedPreferences.AutoDownloadSetting.fromInteger(99));

        assertEquals(FeedPreferences.AutoDownloadSetting.ENABLED, FeedPreferences.AutoDownloadSetting.fromBoolean(true));
        assertEquals(FeedPreferences.AutoDownloadSetting.DISABLED, FeedPreferences.AutoDownloadSetting.fromBoolean(false));
    }

    @Test
    public void getFeedSkipSilenceReturnsGlobalWhenSpeedIsUseGlobal() {
        FeedPreferences prefs = newPreferences(FeedPreferences.SPEED_USE_GLOBAL,
                FeedPreferences.SkipSilence.OFF, new HashSet<>());
        assertEquals(FeedPreferences.SkipSilence.GLOBAL, prefs.getFeedSkipSilence());
    }

    @Test
    public void getFeedSkipSilenceReturnsStoredWhenSpeedSet() {
        FeedPreferences prefs = newPreferences(1.5f, FeedPreferences.SkipSilence.OFF, new HashSet<>());
        assertEquals(FeedPreferences.SkipSilence.OFF, prefs.getFeedSkipSilence());
    }

    @Test
    public void isAutoDownloadEnabledTrue() {
        FeedPreferences prefs = new FeedPreferences(1, FeedPreferences.AutoDownloadSetting.ENABLED,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, "user", "pass");
        assertTrue(prefs.isAutoDownload(false));
        assertTrue(prefs.isAutoDownload(true));
    }

    @Test
    public void isAutoDownloadDisabledFalse() {
        FeedPreferences prefs = new FeedPreferences(1, FeedPreferences.AutoDownloadSetting.DISABLED,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, "user", "pass");
        assertFalse(prefs.isAutoDownload(false));
        assertFalse(prefs.isAutoDownload(true));
    }

    @Test
    public void isAutoDownloadGlobalUsesDefault() {
        FeedPreferences prefs = new FeedPreferences(1, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, "user", "pass");
        assertTrue(prefs.isAutoDownload(true));
        assertFalse(prefs.isAutoDownload(false));
    }

    @Test
    public void updateFromOtherCopiesOnlyUsernamePassword() {
        FeedPreferences prefs = new FeedPreferences(1, FeedPreferences.AutoDownloadSetting.DISABLED,
                FeedPreferences.AutoDeleteAction.ALWAYS, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, "oldUser", "oldPass");
        FeedPreferences other = new FeedPreferences(2, FeedPreferences.AutoDownloadSetting.ENABLED,
                FeedPreferences.AutoDeleteAction.NEVER, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE, "newUser", "newPass");

        prefs.updateFromOther(other);

        assertEquals("newUser", prefs.getUsername());
        assertEquals("newPass", prefs.getPassword());
        assertEquals(1, prefs.getFeedID());
        assertEquals(FeedPreferences.AutoDownloadSetting.DISABLED, prefs.getAutoDownload());
        assertEquals(FeedPreferences.AutoDeleteAction.ALWAYS, prefs.getAutoDeleteAction());
    }

    @Test
    public void updateFromOtherNullIsNoOp() {
        FeedPreferences prefs = new FeedPreferences(1, FeedPreferences.AutoDownloadSetting.DISABLED,
                FeedPreferences.AutoDeleteAction.ALWAYS, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, "user", "pass");

        prefs.updateFromOther(null);

        assertEquals("user", prefs.getUsername());
        assertEquals("pass", prefs.getPassword());
    }

    @Test
    public void getTagsAsStringEmptyReturnsEmpty() {
        FeedPreferences prefs = newPreferences(FeedPreferences.SPEED_USE_GLOBAL,
                FeedPreferences.SkipSilence.GLOBAL, new HashSet<>());
        assertEquals("", prefs.getTagsAsString());
    }

    @Test
    public void getTagsAsStringSingleTagNoSeparator() {
        Set<String> tags = new HashSet<>();
        tags.add("news");
        FeedPreferences prefs = newPreferences(FeedPreferences.SPEED_USE_GLOBAL,
                FeedPreferences.SkipSilence.GLOBAL, tags);
        assertEquals("news", prefs.getTagsAsString());
    }

    @Test
    public void getTagsAsStringJoinsWithSeparator() {
        Set<String> tags = new HashSet<>();
        tags.add("news");
        tags.add("tech");
        FeedPreferences prefs = newPreferences(FeedPreferences.SPEED_USE_GLOBAL,
                FeedPreferences.SkipSilence.GLOBAL, tags);

        String result = prefs.getTagsAsString();
        Set<String> parts = new HashSet<>(Arrays.asList(result.split(FeedPreferences.TAG_SEPARATOR)));
        assertEquals(tags, parts);
    }

    @Test
    public void sevenArgConstructorAppliesDefaults() {
        FeedPreferences prefs = new FeedPreferences(1, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, "user", "pass");

        assertTrue(prefs.getKeepUpdated());
        assertNotNull(prefs.getFilter());
        assertEquals(FeedPreferences.SPEED_USE_GLOBAL, prefs.getFeedPlaybackSpeed(), 0.0001f);
    }

    @Test
    public void serializationRoundTripPreservesFields() throws Exception {
        Set<String> tags = new HashSet<>();
        tags.add("news");
        FeedPreferences prefs = new FeedPreferences(42, FeedPreferences.AutoDownloadSetting.ENABLED,
                true, FeedPreferences.AutoDeleteAction.ALWAYS, VolumeAdaptionSetting.OFF,
                "user", "pass", new FeedFilter(), 1.5f, 0, 0, FeedPreferences.SkipSilence.AGGRESSIVE,
                false, FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE, tags);

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(byteStream)) {
            out.writeObject(prefs);
        }
        FeedPreferences restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(byteStream.toByteArray()))) {
            restored = (FeedPreferences) in.readObject();
        }

        assertEquals(prefs.getFeedID(), restored.getFeedID());
        assertEquals(prefs.getUsername(), restored.getUsername());
        assertEquals(prefs.getAutoDownload(), restored.getAutoDownload());
        assertEquals(prefs.getAutoDeleteAction(), restored.getAutoDeleteAction());
        assertEquals(prefs.getTags(), restored.getTags());
    }
}
