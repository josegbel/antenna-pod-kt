package de.danoeh.antennapod.model.feed

import android.text.TextUtils
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic

// android.text.TextUtils is an unmocked Android SDK stub in :model's bare-JVM unit tests
// (no Robolectric, per module policy). SubscriptionsFilter's String constructor and
// serialize() call TextUtils.split()/TextUtils.join(), which throw "not mocked" if invoked
// for real here. A MockedStatic gives these calls their real AOSP semantics so this test
// characterizes SubscriptionsFilter's own logic against genuine device behavior rather than
// the stub's throw.
class SubscriptionsFilterTest {

    private lateinit var textUtilsMock: MockedStatic<TextUtils>

    @Before
    fun setUp() {
        textUtilsMock = mockStatic(TextUtils::class.java)
        textUtilsMock.`when`<Array<String>> { TextUtils.split(anyString(), anyString()) }.thenAnswer { invocation ->
            val text: String = invocation.getArgument(0)
            val expression: String = invocation.getArgument(1)
            if (text.isEmpty()) {
                return@thenAnswer arrayOf<String>()
            }
            Pattern.compile(expression).split(text, -1)
        }
        textUtilsMock.`when`<String> { TextUtils.join(any(), any() as Array<Any?>) }.thenAnswer { invocation ->
            val delimiter: CharSequence = invocation.getArgument(0)
            val tokens: Array<Any?> = invocation.getArgument(1)
            val sb = StringBuilder()
            var firstTime = true
            for (token in tokens) {
                if (firstTime) {
                    firstTime = false
                } else {
                    sb.append(delimiter)
                }
                sb.append(token)
            }
            sb.toString()
        }
    }

    @After
    fun tearDown() {
        textUtilsMock.close()
    }

    @Test
    fun emptyStringConstructorIsNotEnabled() {
        val filter = SubscriptionsFilter("")
        assertFalse(filter.isEnabled())
    }

    @Test
    fun emptyStringConstructorHideNonSubscribedFeedsTrue() {
        val filter = SubscriptionsFilter("")
        assertTrue(filter.hideNonSubscribedFeeds)
    }

    @Test
    fun emptyStringSerializeReturnsEmpty() {
        val filter = SubscriptionsFilter("")
        assertEquals("", filter.serialize())
    }

    @Test
    fun singlePropertyEnablesFlag() {
        val filter = SubscriptionsFilter(SubscriptionsFilter.COUNTER_GREATER_ZERO)
        assertTrue(filter.showIfCounterGreaterZero)
        assertTrue(filter.isEnabled())
    }

    @Test
    fun multiplePropertiesParsed() {
        val properties = SubscriptionsFilter.COUNTER_GREATER_ZERO + "," + SubscriptionsFilter.ENABLED_UPDATES
        val filter = SubscriptionsFilter(properties)
        assertTrue(filter.showIfCounterGreaterZero)
        assertTrue(filter.showUpdatedEnabled)
        assertFalse(filter.showUpdatedDisabled)
    }

    @Test
    fun trailingSeparatorKeepsEmptyToken() {
        val filter = SubscriptionsFilter(SubscriptionsFilter.COUNTER_GREATER_ZERO + ",")
        assertTrue(filter.isEnabled())
        assertTrue(filter.showIfCounterGreaterZero)
    }

    @Test
    fun onlyDividerConstructorNoPropertiesMatch() {
        val singleDivider = SubscriptionsFilter(",")
        assertTrue(singleDivider.isEnabled())
        assertFalse(singleDivider.showIfCounterGreaterZero)
        assertFalse(singleDivider.showAutoDownloadEnabled)
        assertFalse(singleDivider.showAutoDownloadDisabled)
        assertFalse(singleDivider.showUpdatedEnabled)
        assertFalse(singleDivider.showUpdatedDisabled)
        assertFalse(singleDivider.showEpisodeNotificationEnabled)
        assertFalse(singleDivider.showEpisodeNotificationDisabled)
        assertTrue(singleDivider.hideNonSubscribedFeeds)

        val runOfDividers = SubscriptionsFilter(",,")
        assertTrue(runOfDividers.isEnabled())
        assertFalse(runOfDividers.showIfCounterGreaterZero)
        assertTrue(runOfDividers.hideNonSubscribedFeeds)
    }

    @Test
    fun stringArrayConstructorParsesFlags() {
        val filter = SubscriptionsFilter(
            arrayOf(SubscriptionsFilter.SHOW_NON_SUBSCRIBED_FEEDS)
        )
        assertFalse(filter.hideNonSubscribedFeeds)
        assertTrue(filter.isEnabled())
    }

    @Test
    fun getValuesReturnsDefensiveClone() {
        val filter = SubscriptionsFilter(SubscriptionsFilter.COUNTER_GREATER_ZERO)
        val values = filter.getValues()
        values[0] = "mutated"
        assertArrayEquals(arrayOf(SubscriptionsFilter.COUNTER_GREATER_ZERO), filter.getValues())
    }

    @Test
    fun serializeRoundTrip() {
        val properties = SubscriptionsFilter.COUNTER_GREATER_ZERO + "," + SubscriptionsFilter.ENABLED_UPDATES
        val filter = SubscriptionsFilter(properties)
        val serialized = filter.serialize()
        val roundTripped = SubscriptionsFilter(serialized)
        assertEquals(filter.showIfCounterGreaterZero, roundTripped.showIfCounterGreaterZero)
        assertEquals(filter.showUpdatedEnabled, roundTripped.showUpdatedEnabled)
        assertEquals(filter.showUpdatedDisabled, roundTripped.showUpdatedDisabled)
        assertEquals(filter.hideNonSubscribedFeeds, roundTripped.hideNonSubscribedFeeds)
    }
}
