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
