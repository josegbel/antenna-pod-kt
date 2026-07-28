package de.danoeh.antennapod.net.download.serviceinterface

import android.content.Context
import de.danoeh.antennapod.model.feed.Feed

abstract class FeedUpdateManager {

    abstract fun restartUpdateAlarm(context: Context?, replace: Boolean)

    abstract fun runOnce(context: Context?)

    abstract fun runOnce(context: Context?, feed: Feed?)

    abstract fun runOnce(context: Context?, feed: Feed?, nextPage: Boolean)

    abstract fun runOnceOrAsk(context: Context)

    abstract fun runOnceOrAsk(context: Context, feed: Feed?)

    companion object {
        private var instance: FeedUpdateManager? = null

        @JvmStatic
        fun getInstance(): FeedUpdateManager? {
            return instance
        }

        @JvmStatic
        fun setInstance(instance: FeedUpdateManager?) {
            this.instance = instance
        }
    }
}
