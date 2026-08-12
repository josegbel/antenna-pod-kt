# :event

This module contains EventBus events used for cross-component communication throughout the app.
All classes are plain Kotlin classes; subscribers register using GreenRobot EventBus `@Subscribe` annotations.

Conventions established by the kotlin-track migration, and expected to survive future edits:
- Public fields that Java callers read as fields (not via a getter) use `@JvmField`. Static factory
  methods and static helpers on a companion object use `@JvmStatic`, so they stay callable from Java
  exactly as before (`QueueEvent.added(...)`, not `QueueEvent.Companion.added(...)`).
- No class in this module is a `data class`, and none declares `equals`/`hashCode`. Several event
  types are posted as EventBus sticky events, and `EventBus.removeStickyEvent(Object)` compares by
  `equals()` — synthesizing value equality would change sticky-cache removal semantics at runtime.
- `QueueEvent.items` and the map backing `EpisodeDownloadEvent` are live, mutable references, not
  snapshots — `QueueFragment` aliases and mutates `QueueEvent.items` directly. Never introduce a
  defensive copy (`toList()`, `toMutableList()`, `Collections.unmodifiableList`, etc.) for either.
- Tests in this module are plain JUnit — no Robolectric, no Mockito. None of these classes touch the
  Android framework beyond `MessageEvent` holding (never invoking) a `Consumer<Context>`.
- The test source set is intentionally mixed Kotlin/Java, and that mix is not a migration remnant to
  be finished: `PublicFieldInteropTest.java`, `MessageEventTest.java`, and `FeedItemEventTest.java`
  stay Java on purpose. Each hosts an assertion whose oracle is "javac accepts this call shape," which
  only a Java compiler can prove — from Kotlin, `@JvmField` reads and `@JvmStatic` calls compile
  identically with or without those annotations, so a converted `PublicFieldInteropTest` would still
  pass with zero of its assertions capable of failing, and `MessageEventTest`/`FeedItemEventTest`
  each contain a call (a null actioned `Consumer<Context>`, a null items list) that only a Java caller
  can make and that is not expressible in Kotlin at all. Converting any of these three files does not
  fail loudly — it silently deletes the one module-local, compiler-enforced guard on the `@JvmField`/
  `@JvmStatic` contract described above. Leave them as the last Java files in this module.
- `MessageEvent.message` is non-null by contract, not merely by convention — the constructor throws
  `NullPointerException` if `null` is passed. Any producer forwarding a possibly-null value (e.g.
  `Throwable.getLocalizedMessage()`, which is nullable) must supply its own fallback string before
  constructing a `MessageEvent`.
