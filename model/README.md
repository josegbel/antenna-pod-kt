# :model

This module provides basic model classes like `Feed` and `Chapter`.
Classes are plain data objects with no Android dependencies and no business logic; most are still Java, with a growing subset converted to Kotlin (`FeedOrder`, `FeedCounter`, `TranscriptType`, `DownloadError`, `DownloadStatus`, `TimerValue`, `TranscriptSegment`, `ProxyConfig`, `FeedFilter`, `DownloadResult`, `FeedItemFilter`, `Transcript`, `MediaType`, `SortOrder`, `VolumeAdaptionSetting`, `MediaMetadataRetrieverCompat`, `Chapter`, `FeedFunding`, `Feed`, `FeedItem`).
When converting a class from Java to Kotlin in this module, preserve the existing public Java API using `@JvmField`/`@JvmStatic`/`const val`, and avoid `data class` where the original Java relied on reference equality (i.e. no hand-written `equals`/`hashCode`).
`MediaMetadataRetrieverCompat.close()` is a documented no-unit-test exception: it is framework-pure (subclasses `android.media.MediaMetadataRetriever`, swallows `IOException` from `release()`) and cannot be exercised on the bare JVM; equivalence is verified by inspection plus `:app:assembleDebug` compiling for its downstream callers.
