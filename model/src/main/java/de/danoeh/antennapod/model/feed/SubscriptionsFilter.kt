package de.danoeh.antennapod.model.feed

import java.util.regex.Pattern

class SubscriptionsFilter(private val properties: Array<String>) {

    @JvmField val showIfCounterGreaterZero: Boolean = hasProperty(COUNTER_GREATER_ZERO)

    @JvmField val hideNonSubscribedFeeds: Boolean = !hasProperty(SHOW_NON_SUBSCRIBED_FEEDS)

    @JvmField val showAutoDownloadEnabled: Boolean = hasProperty(ENABLED_AUTO_DOWNLOAD)

    @JvmField val showAutoDownloadDisabled: Boolean = hasProperty(DISABLED_AUTO_DOWNLOAD)

    @JvmField val showUpdatedEnabled: Boolean = hasProperty(ENABLED_UPDATES)

    @JvmField val showUpdatedDisabled: Boolean = hasProperty(DISABLED_UPDATES)

    @JvmField val showEpisodeNotificationEnabled: Boolean = hasProperty(EPISODE_NOTIFICATION_ENABLED)

    @JvmField val showEpisodeNotificationDisabled: Boolean = hasProperty(EPISODE_NOTIFICATION_DISABLED)

    constructor(properties: String) : this(textUtilsSplit(properties))

    private fun hasProperty(property: String): Boolean {
        return properties.contains(property)
    }

    fun isEnabled(): Boolean {
        return properties.isNotEmpty()
    }

    fun getValues(): Array<String> {
        return properties.clone()
    }

    fun serialize(): String {
        return getValues().joinToString(DIVIDER)
    }

    companion object {
        private const val DIVIDER = ","

        const val COUNTER_GREATER_ZERO = "counter_greater_zero"
        const val ENABLED_AUTO_DOWNLOAD = "enabled_auto_download"
        const val DISABLED_AUTO_DOWNLOAD = "disabled_auto_download"
        const val ENABLED_UPDATES = "enabled_updates"
        const val DISABLED_UPDATES = "disabled_updates"
        const val EPISODE_NOTIFICATION_ENABLED = "episode_notification_enabled"
        const val EPISODE_NOTIFICATION_DISABLED = "episode_notification_disabled"
        const val SHOW_NON_SUBSCRIBED_FEEDS = "show_non_subscribed"

        // Reproduces AOSP TextUtils.split(text, expression) bit-for-bit:
        //   empty input -> zero-length array; otherwise Pattern.compile(expression).split(text, -1),
        //   where limit -1 KEEPS trailing empty tokens.
        // Do NOT replace with Kotlin's String.split(","): it returns [""] (length 1) for "",
        // which flips SubscriptionsFilter("").isEnabled() from false to true and corrupts default
        // filter state (new SubscriptionsFilter("") is live at DBReader:660 / SubscriptionFragment:369).
        // No test after this line can catch such a revert for the non-empty path (Kotlin split
        // matches there) — this comment + emptyStringConstructorIsNotEnabled are the regression guard.
        // MUST live in the companion object: it is called from the String constructor's
        // this(...) delegation, where an instance member cannot be referenced ('this' not yet initialized).
        private fun textUtilsSplit(text: String): Array<String> =
            if (text.isEmpty()) emptyArray() else Pattern.compile(DIVIDER).split(text, -1)
    }
}
