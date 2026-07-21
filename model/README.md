# :model

This module provides basic model classes like `Feed` and `Chapter`.
Classes are plain data objects with no Android dependencies and no business logic; most are still Java, with a growing subset converted to Kotlin (`FeedOrder`, `FeedCounter`, `TranscriptType`, `DownloadError`, `DownloadStatus`, `TimerValue`, `TranscriptSegment`, `ProxyConfig`, `FeedFilter`, `DownloadResult`, `FeedItemFilter`, `Transcript`).
When converting a class from Java to Kotlin in this module, preserve the existing public Java API using `@JvmField`/`@JvmStatic`/`const val`, and avoid `data class` where the original Java relied on reference equality (i.e. no hand-written `equals`/`hashCode`).
