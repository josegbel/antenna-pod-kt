package de.danoeh.antennapod.model.feed;

import android.text.TextUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

// android.text.TextUtils is an unmocked Android SDK stub in :model's bare-JVM unit tests
// (no Robolectric, per module policy). SubscriptionsFilter's String constructor and
// serialize() call TextUtils.split()/TextUtils.join(), which throw "not mocked" if invoked
// for real here. A MockedStatic gives these calls their real AOSP semantics so this test
// characterizes SubscriptionsFilter's own logic against genuine device behavior rather than
// the stub's throw.
public class SubscriptionsFilterTest {

    private MockedStatic<TextUtils> textUtilsMock;

    @Before
    public void setUp() {
        textUtilsMock = mockStatic(TextUtils.class);
        textUtilsMock.when(() -> TextUtils.split(anyString(), anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            String expression = invocation.getArgument(1);
            if (text.isEmpty()) {
                return new String[0];
            }
            return text.split(expression, -1);
        });
        textUtilsMock.when(() -> TextUtils.join(any(), (Object[]) any())).thenAnswer(invocation -> {
            CharSequence delimiter = invocation.getArgument(0);
            Object[] tokens = invocation.getArgument(1);
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

    @Test
    public void emptyStringConstructorIsNotEnabled() {
        SubscriptionsFilter filter = new SubscriptionsFilter("");
        assertFalse(filter.isEnabled());
    }

    @Test
    public void emptyStringConstructorHideNonSubscribedFeedsTrue() {
        SubscriptionsFilter filter = new SubscriptionsFilter("");
        assertTrue(filter.hideNonSubscribedFeeds);
    }

    @Test
    public void emptyStringSerializeReturnsEmpty() {
        SubscriptionsFilter filter = new SubscriptionsFilter("");
        assertEquals("", filter.serialize());
    }

    @Test
    public void singlePropertyEnablesFlag() {
        SubscriptionsFilter filter = new SubscriptionsFilter(SubscriptionsFilter.COUNTER_GREATER_ZERO);
        assertTrue(filter.showIfCounterGreaterZero);
        assertTrue(filter.isEnabled());
    }

    @Test
    public void multiplePropertiesParsed() {
        String properties = SubscriptionsFilter.COUNTER_GREATER_ZERO + "," + SubscriptionsFilter.ENABLED_UPDATES;
        SubscriptionsFilter filter = new SubscriptionsFilter(properties);
        assertTrue(filter.showIfCounterGreaterZero);
        assertTrue(filter.showUpdatedEnabled);
        assertFalse(filter.showUpdatedDisabled);
    }

    @Test
    public void trailingSeparatorKeepsEmptyToken() {
        SubscriptionsFilter filter = new SubscriptionsFilter(SubscriptionsFilter.COUNTER_GREATER_ZERO + ",");
        assertTrue(filter.isEnabled());
        assertTrue(filter.showIfCounterGreaterZero);
    }

    @Test
    public void onlyDividerConstructorNoPropertiesMatch() {
        SubscriptionsFilter singleDivider = new SubscriptionsFilter(",");
        assertTrue(singleDivider.isEnabled());
        assertFalse(singleDivider.showIfCounterGreaterZero);
        assertFalse(singleDivider.showAutoDownloadEnabled);
        assertFalse(singleDivider.showAutoDownloadDisabled);
        assertFalse(singleDivider.showUpdatedEnabled);
        assertFalse(singleDivider.showUpdatedDisabled);
        assertFalse(singleDivider.showEpisodeNotificationEnabled);
        assertFalse(singleDivider.showEpisodeNotificationDisabled);
        assertTrue(singleDivider.hideNonSubscribedFeeds);

        SubscriptionsFilter runOfDividers = new SubscriptionsFilter(",,");
        assertTrue(runOfDividers.isEnabled());
        assertFalse(runOfDividers.showIfCounterGreaterZero);
        assertTrue(runOfDividers.hideNonSubscribedFeeds);
    }

    @Test
    public void stringArrayConstructorParsesFlags() {
        SubscriptionsFilter filter = new SubscriptionsFilter(
                new String[]{SubscriptionsFilter.SHOW_NON_SUBSCRIBED_FEEDS});
        assertFalse(filter.hideNonSubscribedFeeds);
        assertTrue(filter.isEnabled());
    }

    @Test
    public void getValuesReturnsDefensiveClone() {
        SubscriptionsFilter filter = new SubscriptionsFilter(SubscriptionsFilter.COUNTER_GREATER_ZERO);
        String[] values = filter.getValues();
        values[0] = "mutated";
        assertArrayEquals(new String[]{SubscriptionsFilter.COUNTER_GREATER_ZERO}, filter.getValues());
    }

    @Test
    public void serializeRoundTrip() {
        String properties = SubscriptionsFilter.COUNTER_GREATER_ZERO + "," + SubscriptionsFilter.ENABLED_UPDATES;
        SubscriptionsFilter filter = new SubscriptionsFilter(properties);
        String serialized = filter.serialize();
        SubscriptionsFilter roundTripped = new SubscriptionsFilter(serialized);
        assertEquals(filter.showIfCounterGreaterZero, roundTripped.showIfCounterGreaterZero);
        assertEquals(filter.showUpdatedEnabled, roundTripped.showUpdatedEnabled);
        assertEquals(filter.showUpdatedDisabled, roundTripped.showUpdatedDisabled);
        assertEquals(filter.hideNonSubscribedFeeds, roundTripped.hideNonSubscribedFeeds);
    }
}
