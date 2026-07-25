package de.danoeh.antennapod.model.feed

object FeedMother {
    const val IMAGE_URL = "http://example.com/image"

    @JvmStatic
    fun anyFeed(): Feed {
        return Feed(
            0, null, "title", "http://example.com", "This is the description",
            "http://example.com/payment", "Daniel", "en", null, "http://example.com/feed", IMAGE_URL,
            null, "http://example.com/feed", System.currentTimeMillis()
        )
    }
}
