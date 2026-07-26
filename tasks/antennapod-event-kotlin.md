# antennapod-event-kotlin

> **Description:** kotlin track migration of the `:event` module (23 plain-POJO EventBus event classes) — new case-study module in the antennapod portfolio, following the `antennapod-model-kotlin` precedent (Milestones 1–7, PRs #1–#13).
> **Repo:** `antennapod`
> **Created:** 2026-07-26

## Research
_Last updated by: legacy-android-researcher | 2026-07-26_

### Summary
The `:event` module is AntennaPod's cross-component notification vocabulary: 23 `.java` files, **488 LOC total**, 0 Kotlin, holding the plain POJOs that GreenRobot EventBus dispatches between the database/download/sync/playback layers and the UI. It is a near-leaf module — `event/build.gradle:10-15` declares exactly one project dependency (`:model`) plus `androidx.core` and the `androidx.annotation` annotation processor. Notably it does **not** depend on EventBus itself: the event classes carry no `@Subscribe`/EventBus annotations and know nothing about the bus; `libs.eventbus` is declared by each of the 10 consuming modules instead. Only one of the 23 classes touches the Android framework at all (`MessageEvent`, via `android.content.Context` + `androidx.core.util.Consumer`). There are no `equals`/`hashCode` overrides anywhere in the module, no `Parcelable`, no `Serializable`, no builders, no `copy` helpers — every class is either an empty marker, a small immutable value holder with getters, or a value holder with `public final` fields, plus 4 with a static-factory pattern. Compared to `:model` (3,800 LOC, `Parcelable` graph, hand-written equality), this module is roughly one-eighth the size and carries a fraction of the semantic risk.

The requested work is the `kotlin` track only. The conversion itself is close to pure J2K, but the module is **wide, not deep**: `:event`'s API is consumed by **104 Java files across 10 modules** (`:app`, `:ui:statistics`, `:ui:preferences`, `:ui:discovery`, `:playback:service`, `:playback:cast`, `:storage:database`, `:storage:importexport`, `:net:download:service`, `:net:sync:service`) and **zero Kotlin files** — so 100% of the interop boundary is Java-calling-Kotlin, which is exactly the direction where Kotlin's property compilation silently breaks source compatibility. Twelve `public final` fields on six classes are read as **fields** by consumers (`event.action`, `event.items`, `event.item`, `event.position`, `event.message`, `event.actionText`, `event.unreadStatusChanged`, `event.isFeedUpdateRunning`); a naive `val` conversion turns each into a getter-only property and breaks the Java call sites at compile time unless `@JvmField` is applied. Two further hazards are behavioral rather than structural: `EpisodeDownloadEvent.indexOfItemWithDownloadUrl` dereferences a now-`String?` `FeedMedia.downloadUrl` (`model/src/main/java/de/danoeh/antennapod/model/feed/FeedMedia.kt:62`) with `.equals(...)`, and both `BufferUpdateEvent` and `SleepTimerUpdatedEvent` encode state in numeric sentinels (`-1f`/`-2f`, `Long.MAX_VALUE`, sign-of-value) compared with `==`. The module has **zero test files** — `event/src/test` does not exist — so nothing currently protects any of it.

### Findings

#### Existing surface
23 production classes under `event/src/main/java/de/danoeh/antennapod/event`, in four packages. Grouped by conversion risk:

**Tier A — empty markers / static-factory singletons (6 files, 39 LOC).** No state, nothing to characterize beyond identity and (where present) `toString`:
- `StreamingConfirmationEvent` (4 LOC, entirely empty class body — `StreamingConfirmationEvent.java:3-4`)
- `PlayerStatusEvent` (6), `DiscoveryDefaultUpdateEvent` (6), `StatisticsEvent` (7) — each an explicit no-arg public constructor and nothing else
- `DownloadLogEvent` (19) and `PlaybackHistoryEvent` (16) — private constructor + `listUpdated()` static factory + hand-written `toString()` returning a fixed string (`DownloadLogEvent.java:14-18`, `PlaybackHistoryEvent.java:12-15`)

**Tier B — private fields + getters, no public fields, no Android types (7 files, 118 LOC).** Mechanically the safest real conversions:
- `PlaybackPositionEvent` (`position`, `duration` : `int`), `SpeedChangedEvent` (`newSpeed` : `float`), `PlayerErrorEvent` (`message` : `String`), `SyncServiceEvent` (`messageResId` : `int`, an unannotated string resource id — `SyncServiceEvent.java:4-12`)
- `settings/VolumeAdaptionChangedEvent` (`VolumeAdaptionSetting`, `long`), `settings/SkipIntroEndingChangedEvent` (3× primitive), `settings/SpeedPresetChangedEvent` (`float`, `FeedPreferences.SkipSilence`, `long`)

**Tier C — `public final` fields read as fields by Java consumers (6 files, 161 LOC).** This is the interop-critical group:
- `FeedUpdateRunningEvent` — `public final boolean isFeedUpdateRunning` (`FeedUpdateRunningEvent.java:4`)
- `playback/PlaybackServiceEvent` — nested `enum Action { SERVICE_STARTED, SERVICE_SHUT_DOWN }` + `public final Action action` (`PlaybackServiceEvent.java:4-9`)
- `FeedEvent` — nested `enum Action { FILTER_CHANGED, SORT_ORDER_CHANGED }`, `private final Action action`, `public final long feedId`, plus `@NonNull toString()` (`FeedEvent.java:7-24`)
- `FeedItemEvent` — `@NonNull public final List<FeedItem> items`, `public final boolean unreadStatusChanged`, plus static helper `indexOfItemWithId(List<FeedItem>, long)` (`FeedItemEvent.java:11-27`)
- `QueueEvent` — 9-constant nested `enum Action`, four `public final` fields (`action`, `item`, `items`, `position`), private constructor + **7 static factories** (`added`, `setQueue`, `removed`, `irreversibleRemoved`, `cleared`, `sorted`, `moved`) (`QueueEvent.java:11-57`)
- `MessageEvent` — `public final String message`, `@Nullable public final Consumer<Context> action`, `@Nullable public final String actionText`, two constructors with the 1-arg delegating to the 3-arg (`MessageEvent.java:8-26`)

**Tier D — behavior worth characterizing before touching (4 files, 130 LOC).** These have real logic, not just field storage:
- `playback/BufferUpdateEvent` (35) — sentinel floats `PROGRESS_STARTED = -1`, `PROGRESS_ENDED = -2` compared with `==`; three static factories; **field `progress` is package-private (`final float progress`, no modifier — `BufferUpdateEvent.java:6`) while a public `getProgress()` also exists** (`:24-26`)
- `playback/SleepTimerUpdatedEvent` (45) — wraps `:model`'s `TimerValue`; encodes three states into one number: `CANCELLED = Long.MAX_VALUE`, "just enabled" = negative millis, "over" = `0`; `justEnabled()` **negates** the millis value, `updated()` clamps both components with `Math.max(…, 0)`, and all five accessors (`getMillisTimeLeft`, `getDisplayTimeLeft`, `isOver`, `wasJustEnabled`, `isCancelled`) derive from that encoding via `Math.abs` / `==` / `<` (`SleepTimerUpdatedEvent.java:6-44`)
- `EpisodeDownloadEvent` (30) — holds `Map<String, DownloadStatus>`, exposes `getUrls()` → `map.keySet()`, plus static `indexOfItemWithDownloadUrl(List<FeedItem>, String)` that walks items and calls `item.getMedia().getDownloadUrl().equals(downloadUrl)` (`EpisodeDownloadEvent.java:21-29`)
- `FeedListUpdateEvent` (28) — three overloaded constructors (`List<Feed>`, `Feed`, `long`) that all populate a private `ArrayList<Long>` of feed ids in the constructor body, plus `contains(Feed)` (`FeedListUpdateEvent.java:9-27`)

Module-wide facts: **no `equals`/`hashCode`/`compareTo` overrides in any of the 23 files**; **3 hand-written `toString()`** (`FeedEvent`, `DownloadLogEvent`, `PlaybackHistoryEvent`); **3 nested enums** (`QueueEvent.Action`, `FeedEvent.Action`, `PlaybackServiceEvent.Action`); **no builder or copy patterns**; **no `Parcelable`/`Serializable`**; **1 file with Android-framework imports** (`MessageEvent.java:3,6`).

#### Where state / data lives
`:event` holds no repositories, persistence, or network code — the classes are transient in-flight messages. The relevant "state" concerns are three aliasing/lifetime behaviors that the conversion must not disturb:

1. **Sticky events are retained state.** EventBus's sticky cache holds one instance per event class indefinitely. Five `:event` types are posted sticky: `FeedUpdateRunningEvent` (`net/download/service/.../FeedUpdateManagerImpl.java:101,109,127,130`; `app/.../MainActivity.java:236`), `EpisodeDownloadEvent` (`MainActivity.java:272`), `SyncServiceEvent` (`net/sync/service/.../SynchronizationQueueImpl.java:72`, `SyncService.java:85,92,96,116,146,221,228,231`), `PlayerErrorEvent` (`playback/cast/.../CastPsmp.java:108,246,287`; `playback/service/.../LocalPSMP.java:211,774`), `SleepTimerUpdatedEvent` (`playback/service/.../ClockSleepTimer.java:47,108,120`). Two are read back out of the cache imperatively: `EventBus.getDefault().getStickyEvent(FeedUpdateRunningEvent.class)` (`net/sync/service/.../SyncService.java:119`) and `getStickyEvent(SleepTimerUpdatedEvent.class)` (`playback/service/.../internal/MediaLibrarySessionCallback.java:189`). Sixteen `@Subscribe(sticky = true)` handlers replay them on registration.
2. **`QueueEvent.items` is aliased and then mutated by the receiver.** `QueueFragment.onEventMainThread(QueueEvent)` assigns the event's list straight into its own field (`queue = event.items;` — `app/.../ui/screen/queue/QueueFragment.java:153`) and subsequently calls `queue.add(...)`, `queue.remove(...)`, `queue.clear()` on it (`:148,160,165,171`). The list carried by the event is therefore a live mutable `java.util.List`, not a snapshot. Same for `DBWriter`, which builds `List<QueueEvent>` batches and posts them after mutating the underlying queue (`storage/database/.../DBWriter.java:402,414-416,449,506,515-517,614,619,625-627`).
3. **`EpisodeDownloadEvent`'s map is shared with the download service.** `MainActivity.java:270-272` passes the same `updatedEpisodes` map to both `DownloadServiceInterface.get().setCurrentDownloads(...)` and `new EpisodeDownloadEvent(...)` — the event does not defensively copy (`EpisodeDownloadEvent.java:13-15`), and `getUrls()` returns the live `keySet()` view.

Upstream data types all now come from the already-converted `:model` module: `FeedItem.kt`, `Feed.kt`, `FeedMedia.kt`, `DownloadStatus.kt`, `FeedPreferences.kt` (`SkipSilence` nested enum), `VolumeAdaptionSetting.kt`, `TimerValue.kt`.

#### Platform-specific notes
- **minSdk 23, targetSdk 36, compileSdk 36** (`common.gradle:3,7,8`); Java source/target **21** (`common.gradle:39-40`). Repo-wide `-Xlint:all -Werror` applies to every `JavaCompile` task (`common.gradle:43-47`).
- **Build-config prerequisite (in-track):** `event/build.gradle:1-3` applies only `alias(libs.plugins.android.library)` — the Kotlin Android plugin is **not** applied, so no `.kt` file will compile in this module until it is added. The plugin is in the catalog (`gradle/libs.versions.toml:83`, Kotlin `2.3.20` at `:3`). `:model` set the precedent by adding both `kotlin.android` and `ktlint` (`model/build.gradle:1-5`); `ktlint` is likewise unapplied here (`gradle/libs.versions.toml:86`).
- **`:event` has no test dependencies at all.** `event/build.gradle:10-15` declares no `junit`, no `androidx.test.core`, no `mockito`, no `robolectric`. Any characterization test requires adding test deps as well as the Kotlin plugin.
- **Lint gate asymmetry:** `common.gradle:151` scopes the `checkstyle` task to `fileTree('src/main/java') { include '**/*.java' }`, so every `.java` → `.kt` rename silently drops that file out of the checkstyle gate with nothing replacing it unless `ktlint` is applied. `config/checkstyle/suppressions.xml:16` currently names `SkipIntroEndingChangedEvent.java` in the `WhitespaceAround` suppression (it exists because of `this.skipIntro= skipIntro;` at `SkipIntroEndingChangedEvent.java:9`) — converting that file orphans the entry, the same pattern already logged as deferred item #5 in `tasks/antennapod-model-kotlin-future-work.md:38-44`.
- **No DI framework** anywhere in the repo; nothing injects or constructs these types through a container. Not applicable to this track.
- **Compose/Paparazzi:** none. `:event` has no UI, no resources, no `AndroidManifest.xml` — the module is `README.md`, `build.gradle`, and 23 source files (verified by `find event -type f -not -path 'event/build/*'`).
- **Android framework surface is a single file.** `MessageEvent.java:3,6` imports `android.content.Context` and `androidx.core.util.Consumer`; `androidx.core` is the module's only non-model runtime dependency (`event/build.gradle:12`) and exists solely for that type.

#### Tests in this area
**There are none. `event/src/test` does not exist; `event/src/androidTest` does not exist.** `event/src` contains only `main/java`. Test coverage of the 23 classes is **0 / 23**.

The only test-source reference to any `:event` class anywhere in the repo is an instrumented helper that *posts* two events as test setup rather than asserting anything about them: `app/src/androidTest/java/de/test/antennapod/ui/UITestUtils.java:196-197` (`new FeedListUpdateEvent(hostedFeeds)`, `QueueEvent.setQueue(queue)`). It exercises no event behavior and runs only on a device.

Consequently the whole of the behavior described in "Existing surface" — every static factory, every sentinel comparison, every constructor-body accumulation — is currently unverified by any automated check. The only thing protecting `:event` today is the Java compiler at its 104 call sites.

### Track prerequisites
- **`kotlin`: no hard prerequisites; not blocked.** `:event` depends only on `:model`, whose kotlin track is complete and merged (Milestones 1–7, PRs #1–#13) — so the upstream types this module consumes (`FeedItem`, `Feed`, `FeedMedia`, `DownloadStatus`, `FeedPreferences.SkipSilence`, `VolumeAdaptionSetting`, `TimerValue`) are already Kotlin with declared nullability, which *removes* the platform-type guesswork that dominated `:model`'s milestones. Two in-track setup items are required before any `.kt` compiles, both one-liners in the existing Groovy build file (this is the `kotlin` track, **not** `gradle-kts`):
  1. add `alias(libs.plugins.kotlin.android)` to `event/build.gradle:1-3` (`gradle/libs.versions.toml:83`);
  2. add test dependencies to `event/build.gradle:10-15` — the module has none today, so Step 1 characterization tests cannot even be written until `libs.junit` (at minimum) is present.
  Adding `alias(libs.plugins.ktlint)` is a third, strongly-advised item given the checkstyle gap noted above, matching `model/build.gradle:1-5`.
- Other tracks (`gradle-kts`, `di`, `concurrency`, `compose`, `navigation`) were **not** requested and are not assessed here. For the record so the planner does not have to re-derive it: nothing in `:event` is a ViewModel, a View, or a navigation entry, and the module contains no threading code of its own — the EventBus `ThreadMode` decisions all live in the consuming modules.

### Characterization-test gaps
**All 23 classes have zero test coverage. Every one of them is a gap.** Per the standard rule, characterization tests must exist and pass before any conversion begins. The gaps that actually matter (i.e. where a test would catch something J2K can plausibly change) are:

1. **`SleepTimerUpdatedEvent`'s tri-state sentinel encoding is completely unverified** (`SleepTimerUpdatedEvent.java:6-44`). `justEnabled()` stores `-millisValue`; `cancelled()` stores `Long.MAX_VALUE` in *both* components; `isCancelled()` tests `millisValue == CANCELLED`; `getMillisTimeLeft()` returns `Math.abs(...)`. Note `Math.abs(Long.MIN_VALUE) == Long.MIN_VALUE` and `-Long.MAX_VALUE` is representable, so the sign trick is load-bearing and edge-case-sensitive. Six call sites branch on these five accessors (`playback/service/.../PlaybackService.java:1049-1064`, `Media3PlaybackService.java:685-697`, `MediaLibrarySessionCallback.java:190`, `app/.../SleepTimerDialog.java:467-475`, `AudioPlayerFragment.java:331-334`, `VideoplayerActivity.java:279`). Needs tests pinning each factory × each accessor, including the `updated()` clamping at zero.
2. **`BufferUpdateEvent`'s float sentinels** (`BufferUpdateEvent.java:4-5,28-34`). `hasStarted()`/`hasEnded()` are `float ==` comparisons against `-1f`/`-2f`; a legitimate `progressUpdate(-1f)` would masquerade as "started". Consumers branch on all three accessors (`app/.../AudioPlayerFragment.java:361-366`, `VideoplayerActivity.java:267-272`, `playback/service/.../PlaybackService.java:1034`).
3. **`QueueEvent`'s 7 factories → 4 field values** (`QueueEvent.java:31-57`). Each factory sets a specific `(action, item, items, position)` tuple, including the `-1` position sentinel for the 5 non-positional factories. `QueueFragment.java:146-177` switches on `action` and dereferences `event.item`/`event.items`/`event.position` per branch, so a single wrong factory mapping is a silent NPE or a wrong-index crash. Two of the nine `Action` constants — `ADDED_ITEMS` and `DELETED_MEDIA` — have **no factory that can produce them**; `DELETED_MEDIA` is nonetheless compared against in `app/.../NavDrawerFragment.java:269`, i.e. that branch is dead today. Tests should pin the current (including dead) mapping, not "fix" it.
4. **`EpisodeDownloadEvent.indexOfItemWithDownloadUrl` null behavior** (`EpisodeDownloadEvent.java:21-29`). It null-guards `item` and `item.getMedia()` but **not** `getDownloadUrl()`, which is `String?` in the converted model (`model/.../FeedMedia.kt:62`) — so a media with a null download URL throws NPE today. Whatever the test asserts, that is the behavior to preserve; a J2K `?.equals()` or `==` rewrite would silently turn the NPE into a `false`/skip. Six call sites iterate it (`app/.../EpisodesListFragment.java:384`, `SearchFragment.java:349`, `EpisodesSurpriseSection.java:129`, `InboxSection.java:107`, `QueueSection.java:115`, `CompletedDownloadsFragment.java:222,322`, `FeedItemlistFragment.java:427`).
5. **`FeedListUpdateEvent`'s three constructors + `contains()`** (`FeedListUpdateEvent.java:9-27`). Each overload populates the same private id list differently; `contains(Feed)` compares by id, not identity — relied on by `app/.../ItemFragment.java:376` and `FeedItemlistFragment.java:477`. Also worth pinning: the `Collections.emptyList()` construction path used by `storage/database/.../FeedDatabaseWriter.java:229` yields a `contains()` that is always false.
6. **`FeedItemEvent.indexOfItemWithId`** (`FeedItemEvent.java:19-27`) — null-item tolerance and the `-1` miss return, used at 8 call sites including `AudioPlayerFragment.java:248` and `Media3VideoPlayerActivity.java:320`.
7. **The three `toString()` overrides** (`FeedEvent.java:20-24` — includes the *private* `action` field and `feedId`; `DownloadLogEvent.java:14-18`; `PlaybackHistoryEvent.java:12-15`). `FeedEvent.toString()` is the only external read of its private `action`. `NavDrawerFragment.java:267` and `QueueFragment.java:138,185` log events, so `QueueEvent`/`FeedItemEvent` currently emit the JVM default `ClassName@hash` — that too is current behavior, and any `data class` conversion would change it.
8. **Reference-equality semantics of all 23 classes.** None override `equals`/`hashCode`; all currently use identity. Tests should pin that two same-content instances are **not** equal (the same trap `:model` Milestone 1 resolved as Decision 3).

### Constraints & Risks
- **The Java-calling-Kotlin field/property break is the dominant risk, and it is repo-wide.** 104 Java files across 10 modules consume `:event`; **zero Kotlin files do**. Kotlin `val` compiles to a private field + getter, so every Java call site that reads a `public final` field as a field stops compiling. Concretely affected reads found in the tree: `event.action` (`app/.../NavDrawerFragment.java:269-271`, `VideoplayerActivity.java:398`, `ExternalPlayerFragment.java:127`, `AudioPlayerFragment.java:260`, `playback/service/.../PlaybackController.java:85`, `QueueFragment.java:146`), `event.item`/`event.position`/`event.items` (`QueueFragment.java:148-172`), `event.items` (`AudioPlayerFragment.java:248`, `Media3VideoPlayerActivity.java:320`, and every `unreadStatusChanged && event.items.isEmpty()` site), `event.unreadStatusChanged` (9 sites), `event.isFeedUpdateRunning` (8 sites incl. `net/sync/service/.../SyncService.java:120`), `event.message` (`MainActivity.java:700,705`, `PreferenceActivity.java:180`, `VideoplayerActivity.java:417`, `OnlineFeedViewActivity.java:496`), `event.actionText` + `event.action.accept(this)` (`MainActivity.java:713`, `PreferenceActivity.java:182`, `VideoplayerActivity.java:419`, `OnlineFeedViewActivity.java:499`). **`@JvmField` on each converted public field is the equivalence-preserving choice**; anything else forces edits in 10 downstream modules, which would blow past a `:event`-scoped File Scope.
- **`isFeedUpdateRunning` is a double trap.** Even with a getter-based conversion, a Kotlin `val isFeedUpdateRunning: Boolean` generates `isFeedUpdateRunning()` (Kotlin does not re-prefix `is`-named properties), so consumers reading it as a field break while the *name* looks unchanged — a rename that compiles fine inside `:event` and fails only in `:app`/`:net:sync:service`. Same shape applies to `unreadStatusChanged`.
- **`BufferUpdateEvent` has a latent JVM platform-declaration clash.** The field `progress` is package-private (`BufferUpdateEvent.java:6`) *and* a public `getProgress()` exists (`:24-26`). Kotlin has no package-private; a `val progress` property auto-generates `getProgress()` and collides with the hand-written one. The conversion must collapse them into a single property (preserving the public `getProgress()` JVM signature that `AudioPlayerFragment.java:366` and `VideoplayerActivity.java:272` call) rather than emit both.
- **`MessageEvent` must keep `androidx.core.util.Consumer<Context>`, not become a Kotlin function type.** Java call sites both construct it with a lambda (`app/.../FeedItemMenuHandler.java:290`, `RemoveFromHistorySwipeAction.java:43`, `playback/service/.../PlaybackService.java:910`, `SkipUtils.java:35,61`, `RemoveFromQueueSwipeAction.java:55`, `ShownotesWebView.java:133`, `DownloadLogAdapter.java:117`) and invoke it via `.accept(...)` at 4 sites. Rewriting to `(Context) -> Unit` breaks both directions. Its two-constructor shape also needs care: `@JvmOverloads` with defaults would *add* a previously non-existent 2-arg overload — source-compatible but an ABI change; a secondary constructor is the exact-parity option.
- **`data class` is the wrong default here, for a reason specific to EventBus.** Beyond the usual identity-vs-value concern, `EventBus.removeStickyEvent(Object)` compares the passed event to the cached one with `equals()`. Five of these types are posted sticky (see "Where state / data lives"), so synthesizing value equality changes sticky-cache removal semantics. Convert to plain `class`, add no `equals`/`hashCode`, exactly as `:model` resolved it.
- **`QueueEvent.items` / `EpisodeDownloadEvent`'s map must stay live mutable references.** `QueueFragment.java:153` aliases and mutates the event's list; a J2K-then-idiomatize pass that introduces `toList()`, an immutable `List` copy, or `Collections.unmodifiableList` produces an `UnsupportedOperationException` at runtime, not a compile error, and only on the SET_QUEUE/SORTED paths. Kotlin's read-only `List` type still erases to `java.util.List` for Java callers, so the *declaration* is safe; the *defensive copy* is not.
- **Nullability decisions are narrower than `:model`'s but not zero.** Annotation coverage in this module is 5 annotations total across 4 files: `@Nullable` on `QueueEvent`'s constructor params (`QueueEvent.java:22-23`) — note the *fields* themselves are unannotated — `@NonNull` on `FeedItemEvent.items` and its constructor param (`FeedItemEvent.java:11,14`), `@Nullable` on `MessageEvent.action`/`actionText` fields but **not** on the corresponding constructor params (`MessageEvent.java:12-16,22`). The remaining reference-typed members are unannotated and become platform types: `PlayerErrorEvent.message`, `MessageEvent.message`, `EpisodeDownloadEvent.map`, `SpeedPresetChangedEvent.skipSilence`, `VolumeAdaptionChangedEvent.volumeAdaptionSetting`, `SleepTimerUpdatedEvent.timerValue`, `QueueEvent.item`/`items`. Tightening any of these to non-null changes the compile-time contract for the 104 Java callers; loosening any to nullable forces `!!`/null-checks on the consumer side. `QueueEvent.item` is the sharpest case: it is genuinely null for 3 of the 7 factories, and `QueueFragment.java:158,169` dereferences it unguarded — so `FeedItem?` is correct, and J2K inserting `!!` inside `:event` to keep a non-null property would move a would-be-NPE to a different line and a different stack trace.
- **`SyncServiceEvent.messageResId` is an unannotated `int` string-resource id** (`SyncServiceEvent.java:4-12`) passed straight to `setSubtitle()` (`ui/preferences/.../SynchronizationPreferencesFragment.java:77`). Adding `@StringRes` during conversion would be a behavior-neutral improvement but is a scope decision, not a given.
- **Verification must be cross-module, not module-local.** `:event` compiling and its own tests passing proves nothing about the 104 Java call sites — the `:model` milestones learned this in red-team loop 1 (`tasks/antennapod-model-kotlin.md`, Plan revision note). All 10 consuming modules must be built, and the 6 flavored ones expose only `Free`/`Play`-qualified variants.
- **Not applicable to this module:** accessibility, RTL, dark mode, SDUI contract versions, analytics, and auth/payment/KYC sensitivity — `:event` has no UI, no resources, no network calls, and handles no personal or payment data. No HSHD concern.
- **Portfolio framing:** this is unaffiliated OSS case-study work, so "milestone" here is case-study narrative, not invoicing. The upstreaming question logged at `tasks/antennapod-model-kotlin-future-work.md:16-20` applies equally to `:event` and remains unresolved.

### Unknowns
1. **Milestone slicing — is one milestone enough, or two/three?** `:event` is 488 LOC across 23 files versus `:model`'s ~3,800 across 27, and it has no `Parcelable` graph, no `equals`/`hashCode`, and one Android-coupled file — so the 7-milestone shape used for `:model` is clearly overkill. But "one PR converting 23 files" is also weak as a case-study artifact and makes review of the `@JvmField` interop decisions harder. A natural 3-way split falls out of the tiering above: **Tier A+B (13 files, 157 LOC, zero public fields, zero logic)** → **Tier C (6 files, 161 LOC, all the `@JvmField`/interop work)** → **Tier D (4 files, 130 LOC, all the sentinel/null behavior)**. The planner should decide; the tier boundaries are drawn on risk, not file count.
2. **`@JvmField` vs accepting getters and editing consumers.** `@JvmField` is the equivalence-preserving choice and keeps File Scope inside `event/`, but it is arguably un-idiomatic Kotlin and would look odd in an upstream PR. The alternative — converting to properties and updating ~40 Java read sites across 10 modules — is a much larger, riskier diff. Needs an explicit decision, and it interacts with unknown #6.
3. **Where do the characterization tests live, and in which language?** `:event` has no test source set and no test dependencies at all. Options: write them in Java first (pinning current behavior against the current Java classes, then converting the tests later — the `:model` Milestone 1–6 pattern, with the test conversion deferred per [[antennapod_kotlin_test_migration_sequencing]]), or write them in Kotlin from the start. The former is more faithful to "characterize before you touch"; the latter avoids a second pass. Note the memory note says convert tests only after the whole module's production code is migrated.
4. **Does anything here need Robolectric?** Almost certainly not — no `Parcelable`, no `Bundle`, no `Context` use beyond `MessageEvent` *holding* a `Consumer<Context>` it never invokes itself. The standing policy (`tasks/antennapod-model-kotlin-future-work.md:48-49`) is Robolectric only for Parcelable round-trips, so the default answer is plain JUnit. Flagging it only so the planner records the decision rather than inheriting it silently.
5. **Should `ktlint` be applied to `:event` as part of this track?** Without it, converted `.kt` files fall out of the `checkstyle` gate (`common.gradle:151`, `.java`-only) with nothing replacing it. `:model` applied it (`model/build.gradle:1-5`). Same question `:model` Milestone 1 raised as its unknown #5.
6. **The two unreachable `QueueEvent.Action` constants.** `ADDED_ITEMS` has no producer and no consumer; `DELETED_MEDIA` has no producer but is compared against at `NavDrawerFragment.java:269`. Deleting them is a tempting "cleanup" but it changes a public enum's constant set (and `Action.values()`/`ordinal()`), which is a behavior change, not a conversion. Recommend explicitly declaring them out of scope — but that is the planner's call to make and record, not mine to assume.
7. **Upstreaming intent** (standing, from `tasks/antennapod-model-kotlin-future-work.md:16-20`). Unresolved, and it directly shapes unknown #2: an upstream-bound PR would likely prefer idiomatic properties + consumer edits over `@JvmField`, while a case-study fork favours the minimal-blast-radius `@JvmField` route. Commercial/positioning implications → per root `CLAUDE.md`, flag to José rather than decide.

### Sources
- Module surface & size: `find event -type f -not -path 'event/build/*'` → `README.md`, `build.gradle`, 23 `.java`, no manifest, no resources, no test source set; `wc -l` over `event/src/**/*.java` → 488 LOC total, largest `QueueEvent.java` 58, `SleepTimerUpdatedEvent.java` 45, `BufferUpdateEvent.java` 35, `EpisodeDownloadEvent.java` 30, smallest `StreamingConfirmationEvent.java` 4.
- `event/README.md:1-4` (module purpose, "plain Java POJOs" claim — verified accurate).
- `event/build.gradle:1-3` (only `android.library` plugin; no kotlin, no ktlint), `:6-8` (namespace), `:10-15` (deps: `project(':model')`, `androidx.core`, `annotationProcessor androidx.annotation`; **no** eventbus, **no** test deps).
- `settings.gradle:22` (`include ':event'`).
- `common.gradle:3,7,8` (compileSdk 36 / minSdk 23 / targetSdk 36), `:39-40` (Java 21), `:43-47` (`-Xlint:all -Werror` on all `JavaCompile`), `:151` (checkstyle sources `src/main/java` `**/*.java` only).
- `gradle/libs.versions.toml:3` (kotlin 2.3.20), `:83` (`kotlin-android` plugin alias), `:86` (`ktlint` plugin alias).
- `model/build.gradle:1-5` (kotlin + ktlint precedent), `:17-26` (test deps incl. scoped Robolectric disclosure).
- Per-class surface: `QueueEvent.java:11-13,15-18,21-29,31-57`; `MessageEvent.java:3,6,8-26`; `FeedItemEvent.java:11-14,19-27`; `FeedEvent.java:7-10,12-13,15-18,20-24`; `FeedUpdateRunningEvent.java:4,6-8`; `PlaybackServiceEvent.java:4-13`; `BufferUpdateEvent.java:4-6,8-22,24-34`; `SleepTimerUpdatedEvent.java:6-11,13-24,26-44`; `EpisodeDownloadEvent.java:11-19,21-29`; `FeedListUpdateEvent.java:9-27`; `SyncServiceEvent.java:4-12`; `PlayerErrorEvent.java:4-12`; `SpeedChangedEvent.java:4-12`; `PlaybackPositionEvent.java:4-18`; `settings/SpeedPresetChangedEvent.java:6-26`; `settings/SkipIntroEndingChangedEvent.java:4-24`; `settings/VolumeAdaptionChangedEvent.java:6-20`; `DownloadLogEvent.java:7-18`; `playback/PlaybackHistoryEvent.java:5-15`; `StatisticsEvent.java:3-6`; `PlayerStatusEvent.java:3-5`; `DiscoveryDefaultUpdateEvent.java:3-5`; `StreamingConfirmationEvent.java:3-4`.
- Module-wide absence claims verified by grep across `event/src`: no `equals(`/`hashCode(`/`compareTo(`, no `Parcelable`/`Serializable`, no `@Subscribe`/`greenrobot`, no `Builder`/`copy(`. Android imports confined to `MessageEvent.java:3,6`.
- Consumer inventory: grep of `de.danoeh.antennapod.event` across `**/*.java` and `**/*.kt` (excluding `event/` and `build/`) → **104 `.java` files, 0 `.kt` files**; import counts per class (MessageEvent 33, FeedItemEvent 23, PlaybackPositionEvent 22, PlayerStatusEvent 20, FeedListUpdateEvent 16, EpisodeDownloadEvent 10, FeedUpdateRunningEvent 9, SpeedChangedEvent 8, SleepTimerUpdatedEvent 8, PlayerErrorEvent 7, QueueEvent 6, PlaybackServiceEvent 6, BufferUpdateEvent 6, StatisticsEvent 4, DownloadLogEvent 4, SyncServiceEvent 3, VolumeAdaptionChangedEvent 3, StreamingConfirmationEvent 2, SpeedPresetChangedEvent 2, SkipIntroEndingChangedEvent 2, PlaybackHistoryEvent 2, FeedEvent 2, DiscoveryDefaultUpdateEvent 2 — all 23 classes have at least one external consumer).
- Consuming modules (10, all declaring `libs.eventbus` themselves): `app/build.gradle:57,116`; `ui/statistics/build.gradle:16,31`; `ui/preferences/build.gradle:26,49`; `ui/discovery/build.gradle:12,25`; `playback/service/build.gradle:18,45`; `playback/cast/build.gradle:12,19`; `storage/database/build.gradle:16,29`; `storage/importexport/build.gradle:12,28`; `net/download/service/build.gradle:13,37`; `net/sync/service/build.gradle:12,27`.
- Public-field read sites: `app/.../ui/screen/queue/QueueFragment.java:146-177,186,197-207,283`; `app/.../ui/screen/drawer/NavDrawerFragment.java:255,267,269-271`; `app/.../ui/screen/drawer/BottomNavigation.java:167`; `app/.../activity/MainActivity.java:700,705,713`; `app/.../ui/screen/preferences/PreferenceActivity.java:180,182`; `app/.../ui/screen/playback/video/VideoplayerActivity.java:398,417,419`; `app/.../ui/screen/onlinefeedview/OnlineFeedViewActivity.java:496,499`; `app/.../ui/screen/playback/audio/AudioPlayerFragment.java:248,260`; `app/.../ui/screen/playback/audio/ExternalPlayerFragment.java:127`; `app/.../ui/screen/playback/video/Media3VideoPlayerActivity.java:320`; `app/.../ui/episodeslist/EpisodesListFragment.java:331,448`; `app/.../ui/screen/SearchFragment.java:322`; `app/.../ui/screen/home/HomeFragment.java:121`; `app/.../ui/screen/episode/ItemFragment.java:362`; `app/.../ui/screen/episode/ItemPagerFragment.java:176`; `app/.../ui/screen/subscriptions/SubscriptionFragment.java:249,479`; `app/.../ui/screen/download/CompletedDownloadsFragment.java:254,304`; `app/.../ui/screen/feed/FeedItemlistFragment.java:399,484-488`; `playback/service/.../PlaybackController.java:85`; `net/sync/service/.../SyncService.java:120`.
- Sticky-event inventory: `net/download/service/.../feed/FeedUpdateManagerImpl.java:101,109,127,130`; `app/.../MainActivity.java:236,272`; `net/sync/service/.../SynchronizationQueueImpl.java:72`; `net/sync/service/.../SyncService.java:85,92,96,116,119,146,221,228,231`; `playback/cast/src/play/.../CastPsmp.java:108,246,287`; `playback/service/.../internal/LocalPSMP.java:211,774`; `playback/service/.../internal/ClockSleepTimer.java:47,108,120`; `playback/service/.../internal/MediaLibrarySessionCallback.java:189`; 16 `@Subscribe(sticky = true)` handlers across `app`, `ui:preferences`.
- Aliasing/mutation evidence: `app/.../ui/screen/queue/QueueFragment.java:148,153,160,165,171`; `storage/database/.../DBWriter.java:402,414-416,449,506,515-517,614,619,625-627`; `app/.../MainActivity.java:270-272`.
- Static-helper call sites: `EpisodeDownloadEvent.indexOfItemWithDownloadUrl` at `app/.../EpisodesListFragment.java:384`, `SearchFragment.java:349`, `EpisodesSurpriseSection.java:129`, `InboxSection.java:107`, `QueueSection.java:115`, `CompletedDownloadsFragment.java:222,322`, `FeedItemlistFragment.java:427`; `FeedItemEvent.indexOfItemWithId` at `EpisodesListFragment.java:336`, `SearchFragment.java:334`, `EpisodesSurpriseSection.java:117`, `QueueSection.java:103`, `QueueFragment.java:158,169,199`, `CompletedDownloadsFragment.java:266`, `FeedItemlistFragment.java:408`, `AudioPlayerFragment.java:248`, `Media3VideoPlayerActivity.java:320`; `FeedListUpdateEvent.contains` at `ItemFragment.java:376`, `FeedItemlistFragment.java:477`.
- `:model` Kotlin signatures depended on: `model/src/main/java/de/danoeh/antennapod/model/feed/FeedMedia.kt:24,62` (`downloadUrl: String?`), `feed/FeedItem.kt:20,47` (`var id: Long`, `var media: FeedMedia?`), `feed/Feed.kt:20` (`var id: Long`), `playback/TimerValue.kt:1-13` (getters are functions, both fields private), `feed/FeedPreferences.kt:79-89` (`enum class SkipSilence`), `feed/VolumeAdaptionSetting.kt`, `download/DownloadStatus.kt` — all `.kt`, confirming `:model`'s kotlin track is complete.
- Test coverage: `find event/src -type f` returns only `main/java` — no `event/src/test`, no `event/src/androidTest`, 0 test files, 0 test deps in `event/build.gradle`. Sole repo-wide test-source reference: `app/src/androidTest/java/de/test/antennapod/ui/UITestUtils.java:8,196-197` (posts events as setup, asserts nothing).
- Lint config: `config/checkstyle/suppressions.xml:16` (`WhitespaceAround` names `SkipIntroEndingChangedEvent.java`, caused by `SkipIntroEndingChangedEvent.java:9`); `config/spotbugs/exclude.xml` contains no `event` entries.
- Module history (low churn, no in-flight refactor): `git log --oneline -12 -- event/` → most recent touches `3a7f18a7f` (version catalogs), `9a4a50ad7` (Reduce number of feed item events, #8393), `47f921065` (streaming confirmation, #8317), `0debbc397` (episode count sleep timers, #7841).
- Precedent/policy docs: `tasks/antennapod-model-kotlin.md` (Research depth, Plan Decisions 1–4, red-team revision notes on cross-module verification); `tasks/antennapod-model-kotlin-milestone-7.md`; `tasks/antennapod-model-kotlin-future-work.md:16-20` (upstreaming), `:38-44` (orphaned checkstyle suppressions), `:48-49` (Robolectric policy).

---

## Plan
_Last updated by: legacy-android-planner | 2026-07-26_

### Objective

Convert all 23 Java classes in the `:event` module to Kotlin (`kotlin` track, `:event` module, one milestone), preserving the module's Java-visible API byte-for-byte so that none of the 104 consuming Java files across 10 modules changes. Equivalence is proven by a new Java characterization suite that must compile and pass **unmodified** both before and after the conversion.

### Resolved Decisions

**D1 — One milestone, one PR, six risk-tiered commits. Not a 3-way milestone split.** (Research Unknown 1.)

`:model` was split into 7 milestones because it was 3,800 LOC across 27 files with a `Parcelable` graph, hand-written `equals`/`hashCode`, Robolectric-only characterization paths, and a real type-dependency ordering (`Feed` → `FeedItem` → `FeedMedia`) that forced sequencing. `:event` has **none** of those: 488 LOC, 23 mutually independent leaf classes, no inheritance (verified: repo-wide grep for `extends *Event` returns **zero** hits), no equality overrides, no `Parcelable`, and no framework coupling beyond one held-but-never-invoked `Consumer<Context>`. At one-eighth the size with a fraction of the semantic risk, a 3-way milestone split would **triple the expensive part** — every milestone must rebuild all 10 consuming modules to prove the interop contract — while **reducing** per-PR review value, because the two decisions that actually matter (D2 `@JvmField`, D6 nullability) are module-wide and would have to be re-argued in each of the three PRs.

The researcher's tiering is nonetheless the right *review* boundary, so it is preserved as **commit** boundaries inside one PR (Steps 3/4/5/6 below = Tier A/B/C/D). A reviewer can bisect; a red-teamer can read the `@JvmField` decision as one self-contained diff.

**Trigger to revisit:** if the full-repo build at the end of Step 5 (Tier C, `@JvmField`) reveals a compile break in **more than one** consuming module, stop — that falsifies the "API preserved" premise, and Tier C should be re-scoped as its own milestone with a widened File Scope rather than patched in place. This is a hard stop, not a judgement call.

**D2 — `@JvmField` on all 12 public fields. Consumers are not edited.** (Research Unknown 2; verified empirically, see D3.)

The 12 `public final` fields on 6 Tier C classes are converted to `@JvmField val`. The alternative — idiomatic properties plus ~40 read-site edits across 10 modules — is rejected for this milestone:
- It would widen File Scope from one module to eleven, which is exactly the "scope grows = new task" line.
- Every one of the 104 consumer files is Java, so the edits buy no consumer-side idiom improvement — they would only translate `event.action` into `event.getAction()` in Java code that stays Java.
- `@JvmField` is already this repo's established precedent, set by `:model` and applied there **41 times** (`SortOrder.kt:6`, `ProxyConfig.kt:6-10`, `FeedItemFilter.kt:9-41`, `SubscriptionsFilter.kt:7-21`, `TranscriptType.kt:4-5`, `FeedFunding.kt:10,12`, `FeedPreferences.kt:42,60,79`, `DownloadRequest.kt:139`, `RemoteMedia.kt:151`). Choosing differently for `:event` would make the portfolio internally inconsistent for no gain.
- `model/README.md` already documents the `@JvmField`/`@JvmStatic` preservation rule as a module convention; this milestone extends it to `:event`.

This decision is **conditional on OQ1 (upstreaming intent)** and is recorded there. It does not block: `:model` resolved API preservation per-file across all 7 milestones without OQ1 ever being answered.

**D3 — `isFeedUpdateRunning`: `@JvmField` is required and is sufficient. Verified by `javap`, not by reasoning.** (Research Unknown/hazard 3.)

Verified against this repo's own compiled Kotlin, not from memory:

```
$ javap -p model/build/tmp/kotlin-classes/debug/.../feed/Feed.class     # source: var isPaged: Boolean
  private boolean isPaged;
  public final boolean isPaged();
  public final void setPaged(boolean);

$ javap -p model/build/tmp/kotlin-classes/debug/.../feed/FeedItemFilter.class   # source: @JvmField val showPlayed
  public final boolean showPlayed;
  public final boolean showIsFavorite;
```

Two things follow, and they settle the "nastiest" case:
1. A naive `val isFeedUpdateRunning: Boolean` emits a **private** field plus `isFeedUpdateRunning()`. Kotlin does *not* re-prefix an `is`-named property, so the getter name looks unchanged — but the field goes private and all 8 Java sites reading `event.isFeedUpdateRunning` **as a field** stop compiling. The break is invisible inside `:event` and only surfaces in `:app` and `:net:sync:service`. Research's diagnosis is confirmed.
2. `@JvmField` emits the backing field public with the property's own name and **no accessor at all**. Critically, the `Feed.isPaged` output shows the backing field is already named `isPaged` (unmangled) even in the accessor case — the `is`-prefix rule lives purely in accessor-name computation and never touches field naming. Therefore `@JvmField val isFeedUpdateRunning: Boolean` emits exactly `public final boolean isFeedUpdateRunning;` and nothing else — byte-for-byte parity with the Java original.

This is not left as reasoning: **AC7 requires the developer to run `javap` on the built `:event` classes and paste the output**, so the claim is machine-checked at implementation time.

Same reasoning applies to `unreadStatusChanged` (9 read sites) — no `is` prefix, so a naive `val` would emit `getUnreadStatusChanged()`, an even louder break. `@JvmField` fixes both identically.

**D4 — `BufferUpdateEvent`: collapse the package-private field and the hand-written getter into one public Kotlin property. Do not emit both.** (Research hazard 4.)

Target shape: `class BufferUpdateEvent private constructor(val progress: Float)`. The Kotlin property generates `public final float getProgress()`, which is the exact JVM signature the two external callers use (`AudioPlayerFragment.java:366`, `VideoplayerActivity.java:272`); the hand-written `getProgress()` is **deleted**, not kept — keeping it is a platform-declaration clash and will not compile.

The package-private field `progress` disappears with no consequence: verified by repo-wide grep that the only read of `.progress` on a `BufferUpdateEvent` is `this.progress = progress` inside the file itself (the one other `.progress` hit in the repo is `ui/echo/.../EchoProgress.java:26`, unrelated). Nothing in package `de.danoeh.antennapod.event.playback` — the only code that *could* see a package-private field — reads it.

**Consequent constraint on Step 1:** the characterization test for `BufferUpdateEvent` lives in the same package and could therefore legally read `event.progress` as a package-private field. It **must not**. It reads only `getProgress()`, `hasStarted()`, `hasEnded()`. A test that reads the field would break at Step 6 and would be indistinguishable from a real regression.

**D5 — `QueueEvent.items` stays a live, aliased, mutable `java.util.List`. Declared `MutableList<FeedItem>?`.** (Research hazard 5.)

`QueueFragment.java:153` does `queue = event.items;` and then calls `queue.add(...)` (`:148`), `queue.remove(...)` (`:160`), `queue.clear()` (`:165`), `queue.add(position, queue.remove(position))` (`:171`) on that same reference. Confirmed by reading the file.

- **Forbidden, and this is the single most dangerous line a J2K-then-idiomatize pass can introduce here:** `toList()`, `toMutableList()`, `Collections.unmodifiableList(...)`, `.toSet()`, or any other defensive copy anywhere in `QueueEvent` or `EpisodeDownloadEvent`. All produce a runtime `UnsupportedOperationException` (or a silently-detached copy) on only the `SET_QUEUE`/`SORTED` paths, with **no compile error**.
- Declared type is `MutableList<FeedItem>?`, not `List<FeedItem>?`. Both erase to `java.util.List<FeedItem>` so the Java signature is identical either way, but `MutableList` is the honest declaration of a contract the receiver actually exercises, and it stops a future Kotlin consumer from assuming a snapshot.
- Same rule for `EpisodeDownloadEvent.getUrls()`: it must return the live `map.keys` view, exactly as `map.keySet()` does today (`MainActivity.java:271-272` hands the same map to `setCurrentDownloads()` and to the event; there is no defensive copy today and there must not be one after).

**D6 — Nullability rule, applied uniformly, with a per-member table.** (Research Constraints; extends `:model`'s per-file precedent.)

The hazard is not the declaration — it is that a Kotlin **non-null parameter on a public function emits `Intrinsics.checkNotNullParameter`**, turning a null that is silently stored today into a throw at construction. The rule, applied to every reference-typed member and parameter:

| Java source says | Kotlin |
|---|---|
| `@NonNull` | non-null |
| `@Nullable` | nullable |
| unannotated, **and** the value is dereferenced unguarded inside `:event` today (so null already throws) | non-null — the NPE moves earlier but is not created |
| unannotated, **and** null is currently stored/returned without throwing | **nullable** — a non-null declaration would create a throw that does not exist today |

Applied (this table is binding; deviations are review findings):

| Member | Kotlin type | Why |
|---|---|---|
| `PlayerErrorEvent.message` | `String?` | stored only. **Real nulls exist**: `LocalPSMP.java:211` passes `e.getLocalizedMessage()`, `CastPsmp.java:108` passes `mediaError.getReason()` |
| `MessageEvent.message` | `String?` | stored only; `AddFeedFragment.java:216` passes `error.getLocalizedMessage()` |
| `MessageEvent.action` | `Consumer<Context>?` | already `@Nullable`; `MainActivity.java:712` null-guards it |
| `MessageEvent.actionText` | `String?` | already `@Nullable` |
| `QueueEvent.action` | `Action` (non-null) | private constructor; all 7 factories pass enum literals — no public entry point can supply null |
| `QueueEvent.item` | `FeedItem?` | already `@Nullable`; genuinely null for 3 of 7 factories |
| `QueueEvent.items` | `MutableList<FeedItem>?` | already `@Nullable`; see D5 |
| `QueueEvent` factory params `item` / `queue` / `sortedQueue` | nullable | stored without dereference; today `added(null, 0)` stores null and does **not** throw |
| `PlaybackServiceEvent.action` | `Action?` | unannotated public ctor param, stored only. All 4 sites pass literals, so the check would never fire — but "never fires" is not a reason to add it |
| `FeedEvent.action` | `private val action: Action?` | stays **private** (not `@JvmField`); read only by `toString()`, where `$action` renders `null` identically to Java concat |
| `FeedItemEvent.items` | `List<FeedItem?>` (non-null list) | Java declares `@NonNull`. **Disclosed narrowing — see D7.** Element type nullable: `PlayActionButton.java:50` builds `Collections.singletonList(media.getItem())` where `getItem()` is `FeedItem?` |
| `FeedItemEvent.indexOfItemWithId(items, id)` | `items: List<FeedItem?>` | `items.size()` dereferenced immediately; elements explicitly null-checked at `FeedItemEvent.java:22` |
| `EpisodeDownloadEvent.map` | `Map<String, DownloadStatus>` (non-null) | dereferenced unguarded by `getUrls()`; avoids a `!!` |
| `FeedListUpdateEvent` ctor params + `contains(feed)` | non-null | all three constructors dereference in the constructor body; `contains` calls `feed.getId()` |
| `SleepTimerUpdatedEvent.timerValue` + all 3 factory params | non-null | private constructor; factories construct a fresh `TimerValue` and dereference `timer` immediately |
| `VolumeAdaptionChangedEvent.volumeAdaptionSetting` | `VolumeAdaptionSetting?` | unannotated public ctor param, stored only |
| `SpeedPresetChangedEvent.skipSilence` | `FeedPreferences.SkipSilence?` | unannotated public ctor param, stored only |

None of this is visible to the 104 Java callers — javac does not enforce Kotlin nullability annotations — so the entire table is about not changing *runtime* behavior.

**D7 — `FeedItemEvent.items` narrowing is accepted and disclosed, not hidden.**

This is the one place the table permits a real behavior change. Today `new FeedItemEvent(null, false)` stores null and NPEs later at a consumer; after conversion it throws `NullPointerException` at construction. Accepted because Java already declared the parameter `@NonNull` — the contract is not being invented, only enforced — and because all 20+ construction sites verified above pass real lists (`Collections.emptyList()`, `Collections.singletonList(...)`, `DBWriter`'s live lists).

Because the before/after behavior differs, **no Step-1 test may assert the null-tolerant behavior** (it would break at Step 5 and look like a regression). Instead, `FeedItemEventTest.constructorRejectsNullItemsAfterConversion` is added **in Step 5**, asserting `assertThrows(NullPointerException.class, () -> new FeedItemEvent(null, false))`, and is the only new test permitted after Step 2.

**D8 — `EpisodeDownloadEvent.indexOfItemWithDownloadUrl`: preserve the NPE. `==` is forbidden.** (Research gap 4.)

Current Java (`EpisodeDownloadEvent.java:24`) null-guards `item` and `item.getMedia()` but **not** `getDownloadUrl()`, which is `String?` (`FeedMedia.kt:62`). A media with a null download URL therefore throws NPE today. That is the behavior to preserve.

- **Forbidden:** `media.downloadUrl == downloadUrl`. Kotlin's `==` on a nullable receiver is null-safe and would silently turn today's NPE into a `false`/skip — a behavior change with no compile error and no crash, i.e. the worst possible failure mode for this milestone's thesis.
- **Forbidden:** `?.equals(...)`, `.orEmpty()`, `equals(..., ignoreCase = …)`.
- **Required:** `media.downloadUrl!! == downloadUrl`. The `!!` throws before the comparison, at exactly the point Java throws.
- This `!!` is **justified and test-pinned**, satisfying the "no unjustified `!!`" bar: `EpisodeDownloadEventTest.indexOfItemWithDownloadUrlThrowsNpeWhenMediaHasNullDownloadUrl` asserts `assertThrows(NullPointerException.class, ...)` and is written in **Step 1 against the Java original**, so it passes before and after. (Kotlin's `!!` throws `NullPointerException`; `KotlinNullPointerException` is a subclass, so the assertion holds either way.)
- `item.getMedia()` is read twice in the Java. `FeedItem.media` is a plain field-backed `var` with no side effects, so hoisting it to a local is behavior-neutral and permitted.

**D9 — `QueueEvent.Action.DELETED_MEDIA` and `ADDED_ITEMS` are kept verbatim. Out of scope to remove.** (Research Unknown 6.)

Neither constant has a producing factory. `DELETED_MEDIA` is nonetheless compared against in a live branch at `NavDrawerFragment.java:269` (verified — an early-`return` guard alongside `SORTED` and `MOVED`), so removing it is a compile break; `ADDED_ITEMS` has no consumer at all, but removing it changes `Action.values()` and every subsequent constant's `ordinal()`. Both are **public API of an enum**, and this is a language conversion, not a cleanup. The enum body is transcribed constant-for-constant, in the same order. `QueueEventTest` pins `Action.values().length == 9` and the exact ordinal of each constant, so an accidental removal fails loudly.

**D10 — Characterization tests are written in **Java**, and Java is the equivalence oracle.** (Research Unknown 3.)

This is the strongest design decision in the plan and it inverts the obvious choice. Because the dominant risk is *Java-calling-Kotlin source compatibility across 104 files*, a Java test that compiles and passes against the Java class and then compiles and passes **unmodified** against the Kotlin class is a mechanical proof of exactly the property at risk — a miniature stand-in for the 104 call sites, evaluated inside `:event`'s own build instead of at the far end of a 10-module rebuild.

Binding consequences:
- Tests read the 12 public fields **as fields** (`event.action`, `event.items`, `event.isFeedUpdateRunning`), never through invented getters. A test written as `event.getAction()` proves nothing and is a review finding.
- **No test file may be edited during Steps 3–6.** Any conversion step that requires touching a test file has, by definition, broken the Java API — that is a hard stop and a re-plan, not a fix. The sole exception is the single new test mandated by D7.
- Tests stay Java for the whole milestone; `-Xlint:all -Werror` (`common.gradle:43-47`) therefore keeps covering the test source set even after `:event`'s production `compileDebugJavaWithJavac` goes `NO-SOURCE`.
- Converting the suite to Kotlin is deferred to a follow-up milestone, consistent with the standing test-migration-sequencing rule (convert tests only after **all** of a module's production code is Kotlin — satisfied at Step 7, not before) and with `:model`'s M1–M6 → M7 precedent. See Out of Scope and OQ2.

**D11 — Test dependencies: `libs.junit` only. No Robolectric, no Mockito, no `androidx.test.core`.** (Research Unknown 4.)

`:event` has no `Parcelable`, no `Bundle`, no `Context` use beyond *holding* a `Consumer<Context>` it never invokes — so the standing policy (Robolectric only for `Parcel` round-trips, `tasks/antennapod-model-kotlin-future-work.md:48-49`) yields plain JUnit. `MessageEvent`'s `Consumer<Context>` is exercised without any Android runtime by constructing a lambda and calling `accept(null)` — `androidx.core.util.Consumer.accept(T)` has no null check.

`:model` types are reachable from tests without a new dependency (`testImplementation` extends `implementation`, and `event/build.gradle:11` already declares `project(':model')`). `FeedItem`/`FeedMedia`/`Feed` construct fine on the bare JVM — `:model`'s own plain-JUnit `FeedFilterTest`/`FeedItemTest` do it today via the `*Mother` builders.

**If a `:model` type turns out to need an Android framework shim, stop and escalate** — do not add Robolectric or Mockito to `:event` to work around it. That is the [[kmp-portability-over-robolectric-shims]] policy and it is not the developer's call to reverse.

**D12 — Apply both `kotlin.android` and `ktlint` to `event/build.gradle`.** (Research Unknown 5, prerequisite.)

`kotlin.android` (`gradle/libs.versions.toml:83`) is a hard prerequisite — no `.kt` compiles in `:event` without it. `ktlint` (`:86`) is applied because `common.gradle:151` scopes the `checkstyle` task to `fileTree('src/main/java') { include '**/*.java' }`, so **every `.java` → `.kt` rename silently drops that file out of the only style gate the module has**. Applying `ktlint` — as `model/build.gradle:1-5` already does — replaces the gate instead of quietly deleting it. Leaving it off would mean this milestone reduces the repo's static-analysis coverage while claiming to modernize it.

No `kotlinOptions`/`jvmTarget` block is needed: `common.gradle:39-40` sets Java 21 compile options and `:model` compiles Kotlin under the identical setup with no extra configuration.

**D13 — Accessor shape rule: any accessor whose Java name is not `get*`/`is*` must stay a `fun`.**

Kotlin property getters are named `get<Name>` unless the property already starts with `is`. So:
- **Must stay functions:** `BufferUpdateEvent.hasStarted()`, `hasEnded()`; `SleepTimerUpdatedEvent.wasJustEnabled()`; `FeedListUpdateEvent.contains(Feed)`; `DownloadLogEvent.listUpdated()`, `PlaybackHistoryEvent.listUpdated()`. A `val wasJustEnabled` would emit `getWasJustEnabled()` and break `PlaybackService.java:1064`, `Media3PlaybackService.java:696`, `VideoplayerActivity.java:279`.
- **May become properties** (identical JVM name): `SleepTimerUpdatedEvent.isOver` / `isCancelled` / `millisTimeLeft` / `displayTimeLeft`; `EpisodeDownloadEvent.urls`; `BufferUpdateEvent.progress`; and all Tier B getters (`position`, `duration`, `newSpeed`, `message`, `messageResId`, `skipIntro`, `skipEnding`, `feedId`, `speed`, `skipSilence`, `volumeAdaptionSetting`).
- `FeedListUpdateEvent.contains` is **not** marked `operator` — that would make it usable as `in`, an API addition.
- `FeedListUpdateEvent` keeps 3 constructors (`List<Feed>`, `Feed`, `long`) as one private primary + two delegating secondary constructors, no `@JvmOverloads` — same shape as D15's `MessageEvent` treatment, since these carry comparable overload-resolution surface.

**D14 — 17 `@JvmStatic` members. Every static factory and static helper keeps its Java call shape.**

Companion-object members are invisible to Java as statics without `@JvmStatic` (Java would need `.Companion.`). Required on all of: `DownloadLogEvent.listUpdated`, `PlaybackHistoryEvent.listUpdated`, `FeedItemEvent.indexOfItemWithId`, `EpisodeDownloadEvent.indexOfItemWithDownloadUrl`, `QueueEvent.{added,setQueue,removed,irreversibleRemoved,cleared,sorted,moved}` (7), `BufferUpdateEvent.{started,ended,progressUpdate}` (3), `SleepTimerUpdatedEvent.{justEnabled,updated,cancelled}` (3). The private constants `BufferUpdateEvent.PROGRESS_STARTED`/`PROGRESS_ENDED` and `SleepTimerUpdatedEvent.CANCELLED` become `private const val` in the companion.

**D15 — `MessageEvent` keeps a secondary constructor. `@JvmOverloads` is forbidden.** (Research Constraints.)

Primary constructor takes the 3 params; `constructor(message: String?) : this(message, null, null)` is the secondary. `@JvmOverloads` with defaults would additionally synthesize a 2-arg overload that does not exist today — source-compatible but an unrequested API addition. `androidx.core.util.Consumer<Context>` is preserved verbatim; converting it to `(Context) -> Unit` would break both the 8 lambda construction sites and the 4 `.accept(...)` call sites.

**D16 — `SleepTimerUpdatedEvent`: `Math.max(x, 0)` must become `max(x, 0L)`. This is the int→long trap.**

`SleepTimerUpdatedEvent.java:19` reads `Math.max(timer.getDisplayValue(), 0)` and `Math.max(0, timer.getMillisValue())`. Both `TimerValue` accessors return `Long` (`TimerValue.kt:6,10` — note they are **functions**, not properties, so Kotlin code calls `timer.getDisplayValue()`, not `timer.displayValue`). Java widens the `0` literal; Kotlin will not, and `max(Long, Int)` does not resolve. The literals must be written `0L`. This is the same class of failure that `:model` Milestone 7 catalogued as its highest-value review checkpoint — loud here (compile error), but a developer "fixing" it with `.toInt()` would silently truncate.

`Math.abs` → `kotlin.math.abs` is behavior-identical (it delegates). The sign trick is load-bearing (`Math.abs(Long.MIN_VALUE) == Long.MIN_VALUE`) and is pinned by test, not by inspection.

**D17 — `BufferUpdateEvent`'s float sentinels stay `==` on primitive `Float`, and NaN/`-0.0f` are pinned by test.**

`progress == PROGRESS_STARTED` where both operands are statically non-null primitive `Float` compiles to IEEE comparison in Kotlin exactly as in Java. Kotlin's `equals`-based `Float` comparison (which makes `NaN == NaN` true and `-0.0f == 0.0f` false) applies only to **boxed** operands, which is why `progress` must not be declared `Float?` and `PROGRESS_STARTED` must be `const val`. Because that distinction is invisible in the source, `BufferUpdateEventTest` pins `progressUpdate(Float.NaN)`, `progressUpdate(-0.0f)`, and `progressUpdate(0.0f)` against all three accessors.

Related, and deliberately **not** fixed: `progressUpdate(-1f).hasStarted()` returns `true` today (the sentinel masquerade research flagged). The test pins that as current behavior. Fixing it is a behavior change, not a conversion.

**D18 — No `data class` anywhere. All 23 stay plain `class` with no `equals`/`hashCode`.**

Beyond the usual identity-vs-value concern, `EventBus.removeStickyEvent(Object)` compares the passed event to the cached one with `equals()`, and five of these types are posted sticky (`FeedUpdateRunningEvent`, `EpisodeDownloadEvent`, `SyncServiceEvent`, `PlayerErrorEvent`, `SleepTimerUpdatedEvent`). Synthesizing value equality would change sticky-cache removal semantics at runtime with no compile error. `data class` would also add `toString()` to `QueueEvent`/`FeedItemEvent`, changing what `NavDrawerFragment.java:267` and `QueueFragment.java:138,185` log. `EventIdentityEqualityTest` pins that two same-content instances of every one of the 23 classes are **not** equal and have distinct `hashCode`s in the general case, and that the 20 classes without a hand-written `toString()` still emit the JVM default `ClassName@hash` form.

The three hand-written `toString()` overrides (`FeedEvent`, `DownloadLogEvent`, `PlaybackHistoryEvent`) are transcribed to produce byte-identical strings and are pinned by exact-string assertions.

**D19 — Kotlin's default-`final` classes are safe here. Verified.**

Java `class X` is subclassable; Kotlin `class X` is not. A repo-wide grep for `extends <Anything>Event` returns **zero** hits, so no event type is subclassed anywhere, including test sources. No `open` modifier is added — adding one would be an unrequested API widening.

**D20 — The orphaned checkstyle suppression is left alone.**

`config/checkstyle/suppressions.xml:16` names `SkipIntroEndingChangedEvent.java` in the `WhitespaceAround` group (it exists because of `this.skipIntro= skipIntro;` at `SkipIntroEndingChangedEvent.java:9`). Converting that file orphans the entry. It is **not** cleaned up: doing so widens File Scope to a shared repo-wide config file, and the entry sits in a regex alternation group whose other eight members are live production files where a typo silently disables a real suppression. This is the identical situation already logged as deferred item #5 in `tasks/antennapod-model-kotlin-future-work.md:38-44`; this milestone appends `:event` to that entry rather than acting unilaterally.

**D21 — Steps land in tier order; each step is independently green and independently committable.**

Tier A (no state) → Tier B (private fields + getters, no interop surface) → Tier C (`@JvmField` interop) → Tier D (behavior). Tier A is deliberately first even though it is trivial: it is the cheapest possible full-repo proof that the `kotlin.android`/`ktlint` wiring and the mixed Java/Kotlin source set work, *before* any risky conversion rides on that assumption. `./gradlew :event:testDebugUnitTest --rerun` must be green after **every** step — `--rerun` is mandatory, because Gradle reports `UP-TO-DATE` and proves nothing otherwise (the lesson from `:model` Milestone 7 research).

### Steps

**Step 1 — Stand up the test source set and pin the high-risk behavior (Tier C + Tier D).**
Add `alias(libs.plugins.kotlin.android)` and `alias(libs.plugins.ktlint)` to `event/build.gradle`'s plugins block and `testImplementation libs.junit` to its dependencies block (no `.kt` file exists yet, so both plugins are inert and the build stays green — this is the "Step 0" prerequisite, landed in the same commit as the tests it enables, per D11/D12). Create `event/src/test/java/de/danoeh/antennapod/event/` and write the Java characterization tests for the 10 highest-risk classes, all against the **current Java** sources:
- `PublicFieldInteropTest.java` — reads all 12 public fields **as fields** on all 6 Tier C classes, in one file, so the D2 contract has a single reviewable proof.
- `FeedUpdateRunningEventTest.java`, `playback/PlaybackServiceEventTest.java`, `FeedEventTest.java` (incl. exact-string `toString()`), `FeedItemEventTest.java` (`indexOfItemWithId`: hit, miss → `-1`, null-element tolerance, empty list), `QueueEventTest.java` (all 7 factories × all 4 fields, the `-1` position sentinel, `Action.values().length == 9` and per-constant ordinals per D9), `MessageEventTest.java` (both constructors, `Consumer<Context>` stored by identity and invoked via `accept(null)`).
- `playback/BufferUpdateEventTest.java` (3 factories × `getProgress()`/`hasStarted()`/`hasEnded()`, plus `NaN`, `-0.0f`, `0.0f`, and the `progressUpdate(-1f)` masquerade — public API only, never the package-private field, per D4).
- `playback/SleepTimerUpdatedEventTest.java` (each of 3 factories × each of 5 accessors, the `updated()` clamp at zero, `Long.MAX_VALUE` cancellation, and the negation sign trick).
- `EpisodeDownloadEventTest.java` (`getUrls()` returns a live view backed by the passed map; `indexOfItemWithDownloadUrl` hit / miss / null-item / null-media, and `indexOfItemWithDownloadUrlThrowsNpeWhenMediaHasNullDownloadUrl` per D8).
- `FeedListUpdateEventTest.java` (all 3 constructor overloads, `contains()` by id not identity, the `Collections.emptyList()` path that always returns false, and `new FeedListUpdateEvent(0)` resolving to the `long` overload).

**Step 2 — Pin the remaining behavior (Tier A + Tier B) and identity semantics.**
Add `MarkerEventsTest.java` (construction of `StreamingConfirmationEvent`, `PlayerStatusEvent`, `DiscoveryDefaultUpdateEvent`, `StatisticsEvent`), `DownloadLogEventTest.java` and `playback/PlaybackHistoryEventTest.java` (static factory returns a new instance each call; exact-string `toString()`), `playback/PlaybackPositionEventTest.java`, `playback/SpeedChangedEventTest.java`, `PlayerErrorEventTest.java` (including a null `message`, per D6), `SyncServiceEventTest.java`, `settings/VolumeAdaptionChangedEventTest.java`, `settings/SkipIntroEndingChangedEventTest.java`, `settings/SpeedPresetChangedEventTest.java`, and `EventIdentityEqualityTest.java` (per D18: reference equality across all 23 classes; default `toString()` shape for the 20 without an override). Record the total test count — it is the number every later step must reproduce.

**Step 3 — Convert Tier A: the 6 markers and static-factory singletons.**
`StreamingConfirmationEvent`, `PlayerStatusEvent`, `DiscoveryDefaultUpdateEvent`, `StatisticsEvent`, `DownloadLogEvent`, `PlaybackHistoryEvent` → `.kt`. Applies D14 (`@JvmStatic` on both `listUpdated()`), D13 (`listUpdated` stays a `fun`), D18 (plain `class`), D19 (no `open`). No test file changes. This step is the wiring proof described in D21: run the full-repo build here, not just `:event`.

**Step 4 — Convert Tier B: the 7 private-field-plus-getter classes.**
`PlaybackPositionEvent`, `SpeedChangedEvent`, `PlayerErrorEvent`, `SyncServiceEvent`, `settings/VolumeAdaptionChangedEvent`, `settings/SkipIntroEndingChangedEvent`, `settings/SpeedPresetChangedEvent` → `.kt`, as primary-constructor `val` properties per D13/D6. No test file changes.

**Step 5 — Convert Tier C: the 6 `@JvmField` interop classes.**
`FeedUpdateRunningEvent`, `playback/PlaybackServiceEvent`, `FeedEvent`, `FeedItemEvent`, `QueueEvent`, `MessageEvent` → `.kt`, applying D2 (12 `@JvmField`s), D3, D5 (`MutableList`, no copies), D6 (nullability table), D9 (enum constants verbatim), D14 (`@JvmStatic` on the 8 factories/helpers here), D15 (secondary constructor). Add the single new test `FeedItemEventTest.constructorRejectsNullItemsAfterConversion` per D7 — the only test addition permitted after Step 2. Every other test file is unchanged. Run `javap` per AC7 and the full-repo build; **the D1 hard-stop trigger applies here**.

**Step 6 — Convert Tier D: the 4 behavioral classes.**
`playback/BufferUpdateEvent`, `playback/SleepTimerUpdatedEvent`, `EpisodeDownloadEvent`, `FeedListUpdateEvent` → `.kt`, applying D4 (collapse field + getter), D8 (`!!`, never `==`), D13 (`hasStarted()`/`hasEnded()`/`wasJustEnabled()`/`contains()` stay `fun`), D16 (`0L` literals, `getDisplayValue()`/`getMillisValue()` are functions), D17 (`const val` sentinels), D5 (`map.keys` live view), D14. No test file changes. `:event` is now 23/23 Kotlin production files.

**Step 7 — Verify cross-module and update `event/README.md`.**
Run the full verification matrix (AC10) across all 10 consuming modules and both product flavors. Update `event/README.md`, whose current text asserts "All classes are plain Java POJOs" — replace with the Kotlin equivalent plus the module conventions that must survive future edits: `@JvmField` on public fields, `@JvmStatic` on static factories, no `data class` (with the EventBus sticky-removal reason), no defensive copies of `QueueEvent.items`/`EpisodeDownloadEvent`'s map, and the plain-JUnit/no-Robolectric constraint. Append the two new deferred items to `tasks/antennapod-model-kotlin-future-work.md` (`:event` added to orphaned-suppression item #5; `:event` added to the `allWarningsAsErrors` item #3, since `:event`'s production `compileDebugJavaWithJavac` now goes `NO-SOURCE`).

### File Scope

The reviewer rejects any diff touching a file not on this list.

**Modified:**
- `event/build.gradle`
- `event/README.md`
- `tasks/antennapod-event-kotlin.md`
- `features/antennapod-event-kotlin.checkpoint.md`
- `tasks/antennapod-model-kotlin-future-work.md` (append to deferred items #3 and #5 only)

**Renamed `.java` → `.kt`** (all under `event/src/main/java/de/danoeh/antennapod/event/`):
- `DiscoveryDefaultUpdateEvent`, `DownloadLogEvent`, `EpisodeDownloadEvent`, `FeedEvent`, `FeedItemEvent`, `FeedListUpdateEvent`, `FeedUpdateRunningEvent`, `MessageEvent`, `PlayerErrorEvent`, `PlayerStatusEvent`, `QueueEvent`, `StatisticsEvent`, `StreamingConfirmationEvent`, `SyncServiceEvent`
- `playback/`: `BufferUpdateEvent`, `PlaybackHistoryEvent`, `PlaybackPositionEvent`, `PlaybackServiceEvent`, `SleepTimerUpdatedEvent`, `SpeedChangedEvent`
- `settings/`: `SkipIntroEndingChangedEvent`, `SpeedPresetChangedEvent`, `VolumeAdaptionChangedEvent`

**Created** (all under `event/src/test/java/de/danoeh/antennapod/event/`, all `.java`):
- `PublicFieldInteropTest`, `EventIdentityEqualityTest`, `MarkerEventsTest`, `DownloadLogEventTest`, `EpisodeDownloadEventTest`, `FeedEventTest`, `FeedItemEventTest`, `FeedListUpdateEventTest`, `FeedUpdateRunningEventTest`, `MessageEventTest`, `PlayerErrorEventTest`, `QueueEventTest`, `SyncServiceEventTest`
- `playback/`: `BufferUpdateEventTest`, `PlaybackHistoryEventTest`, `PlaybackPositionEventTest`, `PlaybackServiceEventTest`, `SleepTimerUpdatedEventTest`, `SpeedChangedEventTest`
- `settings/`: `SkipIntroEndingChangedEventTest`, `SpeedPresetChangedEventTest`, `VolumeAdaptionChangedEventTest`

**Explicitly out of File Scope** — touching any of these means the plan was wrong and the task must be re-planned, not patched: any file in `app/`, `ui/*/`, `playback/*/`, `storage/*/`, `net/*/`, `parser/*/`, `system/`, `model/`; `common.gradle`; `build.gradle` (root); `settings.gradle`; `gradle/libs.versions.toml`; `config/checkstyle/suppressions.xml`; `config/spotbugs/exclude.xml`; `.github/`.

### Acceptance Criteria

**Characterization (before)**
- [ ] **AC1** — `./gradlew :event:testDebugUnitTest --rerun` is green after Step 1 and after Step 2, against the **unconverted Java** sources, and the Step 2 run's total test count is recorded in Implementation Notes.
- [ ] **AC2** — Every one of the 23 production classes is exercised by at least one test. Verified by listing the 22 test files against the 23 production classes (the four Tier A markers share `MarkerEventsTest`).
- [ ] **AC3** — `PublicFieldInteropTest` reads all 12 public fields using **field syntax** (`event.action`, `event.items`, `event.item`, `event.position`, `event.message`, `event.actionText`, `event.unreadStatusChanged`, `event.isFeedUpdateRunning`, `event.feedId`). Verified by `grep -c` for `\.get[A-Z]` in that file returning 0.

**Characterization (after) — the equivalence proof**
- [ ] **AC4** — `./gradlew :event:testDebugUnitTest --rerun` is green after **each** of Steps 3, 4, 5, 6, with the same total test count as AC1 (+1 after Step 5, per D7).
- [ ] **AC5** — `git diff --stat <step-1..2 merge base> -- event/src/test/` over Steps 3–6 shows changes to **exactly one** file (`FeedItemEventTest.java`, +1 method). Any other test-file change is a REQUEST CHANGES finding, not a fix.
- [ ] **AC6** — `find event/src/main -name '*.java'` returns empty and `find event/src/main -name '*.kt' | wc -l` returns 23.

**Idiomatic Kotlin target + interop contract**
- [ ] **AC7** — `javap -p` on the built `:event` debug classes shows, verbatim: `FeedUpdateRunningEvent` has `public final boolean isFeedUpdateRunning;` and **no** `isFeedUpdateRunning()` method; `FeedItemEvent` has `public final java.util.List items;` + `public final boolean unreadStatusChanged;` and no corresponding getters; `QueueEvent` has 4 public final fields and 7 `public static QueueEvent` factory methods; `MessageEvent` has 3 public final fields and exactly **2** constructors (not 3 — proving `@JvmOverloads` was not used); `FeedListUpdateEvent` has exactly **3** public constructors with their original parameter types; `BufferUpdateEvent` has exactly one `public final float getProgress()` and both `hasStarted()`/`hasEnded()` remain no-arg methods (no `getHasStarted()`/`getHasEnded()`); `SleepTimerUpdatedEvent` has `public final boolean wasJustEnabled()` (not `getWasJustEnabled()`). Output pasted into Implementation Notes.
- [ ] **AC8** — `grep -rn '!!' event/src/main` returns exactly **one** hit, in `EpisodeDownloadEvent.kt`, on the `downloadUrl` comparison, and it is covered by `EpisodeDownloadEventTest.indexOfItemWithDownloadUrlThrowsNpeWhenMediaHasNullDownloadUrl`. Any other `!!` is unjustified.
- [ ] **AC9** — `grep -rnE 'data class|toList\(\)|toMutableList\(\)|toSet\(\)|unmodifiable' event/src/main` returns **zero** hits (D5, D18). `grep -rn 'operator fun' event/src/main` returns zero hits (D13). No `equals`/`hashCode` override exists in any of the 23 files.
- [ ] **AC10** — `./gradlew :event:ktlintCheck` is green, and `./gradlew checkstyle lint` is green repo-wide.

**Cross-module verification — module-local green proves nothing here**
- [ ] **AC11** — `./gradlew :app:assembleDebug` is green (compiles both `Free` and `Play` flavors and transitively all 10 consuming modules), with **zero** edits to any file outside `event/`.
- [ ] **AC12** — The unit tests of every consuming module that has a test source set are green: `:storage:database:test`, `:storage:importexport:test`, `:playback:service:test`, `:playback:cast:test`, `:net:download:service:test`, `:net:sync:service:test`, `:ui:preferences:test`, `:ui:statistics:test`, `:ui:discovery:test`.
- [ ] **AC13** — No public API break visible to Java callers outside `:event`. Evidenced by AC11 (compile) + AC7 (JVM signatures) + AC5 (the Java test suite compiled unmodified against both versions). The single approved, disclosed narrowing is `FeedItemEvent`'s `@NonNull items` per D7 — no other.
- [ ] **AC14** — `QueueEvent.Action.values().length == 9` and every constant's `ordinal()` is unchanged, asserted in `QueueEventTest` (D9).

**Documentation**
- [ ] **AC15** — `event/README.md` no longer claims "plain Java POJOs" and records the five module conventions named in Step 7; `tasks/antennapod-model-kotlin-future-work.md` items #3 and #5 name `:event`.

**Not applicable to this module, asserted rather than assumed:** accessibility (content descriptions, dynamic type), dark mode / hardcoded colors, RTL, Paparazzi snapshots, SDUI contract versions, analytics, HSHD. `:event` has no UI, no resources, no `AndroidManifest.xml`, no network calls, and handles no personal or payment data — verified by `find event -type f -not -path 'event/build/*'` returning only `README.md`, `build.gradle`, and source files.

### Milestone

**Milestone 8 — `:event` module, `kotlin` track (production code).** Single milestone, single unified PR (code + spec docs together, per the checkpoint's standing instruction and the Milestone 7 precedent), six risk-tiered commits mapping 1:1 to Steps 1–6 plus a verification/docs commit for Step 7. Follows Milestones 1–7 (`:model`, kotlin track, PRs #1–#13, closed 2026-07-25).

This is **unaffiliated OSS portfolio work**, so "milestone" here is case-study narrative structure, not invoicing. The case-study angle this milestone earns: *"104 Java call sites across 10 modules, zero of them edited — proven by a Java test suite that compiled unmodified against both the before and the after."*

### Out of Scope

- **Every other track.** No `gradle-kts` (`event/build.gradle` stays Groovy; the two plugin lines and one test dependency are in-track prerequisites for compiling Kotlin, not a build-script migration), no `di`, no `concurrency`, no `compose`, no `navigation`. Nothing in `:event` is a ViewModel, View, navigation entry, or threading construct — EventBus `ThreadMode` decisions all live in the consuming modules and stay there.
- **Converting the characterization tests to Kotlin.** Deliberately deferred per D10 — the tests being Java is what makes them an equivalence oracle. Natural follow-up (Milestone 9); see OQ2.
- **Editing any consuming module.** Zero of the 104 Java call sites is touched. If one must be, the plan is wrong (D1 hard stop).
- **Removing `QueueEvent.Action.DELETED_MEDIA` or `ADDED_ITEMS`,** or the dead `NavDrawerFragment.java:269` branch (D9).
- **"Fixing" `BufferUpdateEvent`'s sentinel masquerade** (`progressUpdate(-1f).hasStarted() == true`) or `SleepTimerUpdatedEvent`'s tri-state numeric encoding. Both are pinned as current behavior (D17).
- **Adding `@StringRes` to `SyncServiceEvent.messageResId`.** Behavior-neutral and probably correct, but it is an annotation addition, not a language conversion.
- **Cleaning `config/checkstyle/suppressions.xml`** (D20) or adding `allWarningsAsErrors` to Kotlin compile tasks in `common.gradle` — both are repo-wide config changes tracked as future-work items #5 and #3.
- **Adding Robolectric or Mockito to `:event`** (D11). If a `:model` type appears to need one, escalate rather than add.
- **Tightening the nullability the D6 table deliberately leaves loose** (`PlaybackServiceEvent.action`, `QueueEvent` factory params, `VolumeAdaptionChangedEvent`, `SpeedPresetChangedEvent`). These become worth revisiting only once consumers are Kotlin; logging to future work is Step 7's job, acting on it is not.
- **Any architecture work** — no MVVM, no further modularization, no EventBus→Flow replacement, no defensive-copy hardening of the aliased `QueueEvent.items` contract (a real latent bug, but a behavior change).

---

## Open Questions

**OQ1 — Upstreaming intent (standing; commercial/positioning — for José, not for any agent).**
Carried from `tasks/antennapod-model-kotlin-future-work.md:16-20`, unresolved across all 7 `:model` milestones. Is this work destined for an upstream AntennaPod PR, or does it stay an internal case-study fork?

It matters more here than it did for `:model`, and concretely: **D2 assumes fork.** `@JvmField` on 12 public fields is the minimal-blast-radius, equivalence-preserving choice and keeps File Scope inside `event/`. An upstream-bound PR would likely be asked to drop `@JvmField` in favour of idiomatic properties and update ~40 Java read sites across 10 modules — a fundamentally different task with an eleven-module File Scope, different risk profile, and a different verification story. That is a **new task, not a revision of this one.**

**This does not block Milestone 8.** `:model` resolved API preservation per-file for seven consecutive milestones without OQ1 ever being answered, and the same applies here: `@JvmField` is reversible, mechanical, and confined to 6 files. Per root `CLAUDE.md`'s commercial-implications rule, the planner does not decide this.

**OQ2 — Does the `:event` test-suite Kotlin conversion become Milestone 9, and when?**
D10 defers it deliberately (the Java suite is the equivalence oracle for Milestone 8, and the standing test-migration-sequencing rule requires all production code Kotlin first — satisfied only at Step 7). The question is purely scheduling: run Milestone 9 immediately after this PR merges, or move to the next production module first. Low stakes either way; flagged so it is a decision rather than a thing that quietly never happens. `:model` Milestone 7 is the template and its conventions (`object` + `@JvmStatic` helpers, backticked foreign keywords, byte-for-byte test-name preservation, assertion-content diffing) transfer directly.

---

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-07-26 | Loop 1 of max 2_

### Verdict
APPROVE

### Concerns

- **Severity:** MINOR
- **Class:** Silent behavior changes from mechanical translation (documentation accuracy)
- **Concern:** D6's nullability table states `QueueEvent.item` is "genuinely null for 4 of 7 factories," but the actual factory count is 3 of 7 — `setQueue`, `cleared`, and `sorted` pass `null` for `item`; `added`, `removed`, `irreversibleRemoved`, and `moved` all pass a non-null `item`. This directly contradicts the Plan's own Research section (line 96), which correctly says "genuinely null for 3 of the 7 factories." The declared type (`FeedItem?`) is unaffected either way, so this does not change the equivalence outcome, but it is a verifiable arithmetic error inside a table explicitly marked "binding; deviations are review findings" — which undercuts the credibility of a document whose entire pitch is "verified, not reasoned."
- **Evidence:** `tasks/antennapod-event-kotlin.md` D6 table row `QueueEvent.item`, vs. Research section line 96, vs. `event/src/main/java/de/danoeh/antennapod/event/QueueEvent.java:35-53` (verified by reading the file directly: `setQueue`/`cleared`/`sorted` pass `null`; `added`/`removed`/`irreversibleRemoved`/`moved` pass real items).
- **Suggested mitigation:** Correct "4 of 7" to "3 of 7" in the D6 table before implementation, for internal consistency with the Research section.

- **Severity:** MINOR
- **Class:** Coverage gaps left unaddressed (documentation completeness, not a live equivalence risk)
- **Concern:** `MessageEvent`'s 2-constructor overload gets its own Decision (D15, explicitly forbidding `@JvmOverloads`) and its own AC7 javap line ("exactly 2 constructors, not 3"). `FeedListUpdateEvent`'s 3-constructor overload (`List<Feed>`, `Feed`, `long`) — arguably carrying *more* overload-resolution surface than MessageEvent — gets neither: no Decision documents the intended secondary-constructor shape, and AC7's javap checklist never mentions `FeedListUpdateEvent`. In practice this is not a live equivalence hole: Step 1's `FeedListUpdateEventTest` already pins all 3 constructor overloads plus the `Collections.emptyList()` edge case and the `new FeedListUpdateEvent(0)` → `long`-overload resolution, and D10's core mechanism (AC4 + AC5: the unmodified Java test suite must stay green through Step 6) would hard-fail if the constructors were built wrong. But the plan is inconsistent about which overload-bearing classes get first-class Decision + AC7 treatment versus which are left to the generic safety net.
- **Evidence:** Compare D15 (`tasks/antennapod-event-kotlin.md` lines 291-293) and AC7's MessageEvent clause (line 390) against Step 6 (line 348-349) and AC7's full class list (line 390), which never names `FeedListUpdateEvent`. Source confirmed: `event/src/main/java/de/danoeh/antennapod/event/FeedListUpdateEvent.java:9-27` (3 real overloaded constructors, not default-parameter shaped).
- **Suggested mitigation:** Optional, not blocking: add a one-line Decision (or fold into D15) stating the 3 constructors become one private primary + three delegating secondary constructors, no `@JvmOverloads`; extend AC7 to assert `FeedListUpdateEvent` has exactly 3 public constructors with the original parameter types.

- **Severity:** MINOR
- **Class:** Coverage gaps left unaddressed (documentation completeness, not a live equivalence risk)
- **Concern:** Step 6's "applying" clause (D4, D8, D16, D17, D5, D14) never cites D13, even though 3 of Tier D's 4 classes are D13-governed on their riskiest accessors: `BufferUpdateEvent.hasStarted()`/`hasEnded()`, `SleepTimerUpdatedEvent.wasJustEnabled()` (D13's own headline example of what breaks — `getWasJustEnabled()` — if this is missed), and `FeedListUpdateEvent.contains()`. AC7 explicitly javap-verifies the `wasJustEnabled()` case but not `hasStarted()`/`hasEnded()` remaining methods on `BufferUpdateEvent`. Again, not a live gap in practice: the pre-existing `BufferUpdateEventTest` calls `event.hasStarted()`/`event.hasEnded()` as method calls, so if a developer accidentally turned either into a `val` (emitting `getHasStarted()`), the unmodified Step-1 test would fail to compile at Step 6 and AC4/AC5 would catch it immediately. This is a citation/traceability gap in the plan text, not an unguarded risk.
- **Evidence:** Step 6 (line 348-349) vs. D13 (lines 280-286) vs. AC7 (line 390, `BufferUpdateEvent` clause only covers `getProgress()`).
- **Suggested mitigation:** Optional: add D13 to Step 6's applying-clause and extend AC7's `BufferUpdateEvent` line to also assert `hasStarted()`/`hasEnded()` remain no-arg methods, for symmetry with how Tier C's interop risks are exhaustively AC7-verified.

### Categories considered and dismissed (no finding)

- **D3 (`isFeedUpdateRunning` field emission):** Re-verified the cited `javap` precedent directly against `model/build/tmp/kotlin-classes/debug/.../Feed.class` and `FeedItemFilter.class` — output matches the plan's quoted transcript exactly. No existing `@JvmField val isXxx` precedent exists anywhere in this repo (grepped), so D3's claim is an extrapolation from two separate observations rather than a direct empirical test of the exact `is`-prefix + `@JvmField` combination — but AC7 requires the developer to run `javap` on the actual built `:event` classes and paste verbatim output, so the extrapolation is machine-checked before merge, not shipped on faith.
- **D10 (Java tests as equivalence oracle):** Stress-tested for the blind spot the task named — does one field-read test per field really generalize to all 104 call sites? Yes, structurally: Java field-access compile-compatibility depends only on the field's existence/accessibility/declared type, not on the specific consumer file, and no `:event` type is subtyped anywhere (D19, verified: zero `extends *Event` hits). More importantly, the plan does not rely on the Java suite alone — AC11 (`:app:assembleDebug`, both flavors, transitively all 10 consuming modules, zero edits outside `event/`) is the actual full-surface proof; the Java suite is a fast local pre-check, not the sole oracle. No blind spot found.
- **D5 (`QueueEvent.items` mutation semantics):** Verified `MutableList<FeedItem>?` + `@JvmField` preserves the exact live-reference-mutation contract `QueueFragment.java:153` depends on — both `List` and `MutableList` erase identically to `java.util.List` for Java callers, so the declared Kotlin mutability is a source-level-only distinction with no interop risk.
- **D8 (`!!` placement, AC8's "exactly one" claim):** Verified achievable by tracing the exact hoist-to-local pattern D8 mandates (`item.getMedia()` → local `val`, enabling smart-cast, avoiding a second `!!`) against the real source at `EpisodeDownloadEvent.java:21-29`. AC8's count is achievable and correctly covers the `item.getMedia()` null path the task specifically asked about.
- **D16 (int→long widening trap):** Confirmed present in both the Decision text and Step 6's applying-clause, not summary-only. Confirmed via `TimerValue.kt` that `getDisplayValue()`/`getMillisValue()` are functions (not properties) returning `Long`, matching D16's stated reasoning exactly.
- **Step 1 test-infra sequencing:** Confirmed build-green claim holds — `kotlin.android` and `ktlint` plugins are inert with zero `.kt` files present, landed in the same commit as the JUnit dependency and the first tests, before any conversion step. `libs.junit = 4.13` (confirmed in `gradle/libs.versions.toml`), which supports `assertThrows` as D7/D8 require.
- **File Scope completeness:** Confirmed exact match against `find event/src/main -name '*.java'` (23 files) and the actual `event/build.gradle`/`event/README.md` content. Nothing outside `:event` is touched for production changes; the two future-work-doc appends are narrowly scoped.
- **OQ1/OQ2 non-blocking status:** Correctly non-blocking. `:model` set the precedent of shipping 7 milestones without OQ1 resolved; `@JvmField` is mechanical and confined to 6 files, consistent with treating this as deferred-but-tracked rather than a hidden gate.

### Verification methods used
`javap -p` against actual compiled `:model` classes; direct reads of all 23 `event/src/main` source files, `event/build.gradle`, `model/build.gradle`, `TimerValue.kt`, `FeedMedia.kt`, `FeedItem.kt`, `common.gradle`'s checkstyle scope; repo-wide greps for `@JvmField`, accessor method signatures, and `find` against File Scope's file lists.

---

## Implementation Notes
_By: android-migration-developer | 2026-07-26_

### Commit message

```
feat(event): kotlin migration — convert all 23 :event classes to Kotlin

Converts every production class in the :event module (23 files, 0 remaining
.java under event/src/main) from Java to Kotlin, kotlin track, Milestone 8.
Landed as six risk-tiered steps matching the plan's Tier A → B → C → D
sequence: markers/static-factories, private-field-plus-getter classes,
@JvmField interop classes (FeedUpdateRunningEvent, PlaybackServiceEvent,
FeedEvent, FeedItemEvent, QueueEvent, MessageEvent), then the four
behavioral classes (BufferUpdateEvent, SleepTimerUpdatedEvent,
EpisodeDownloadEvent, FeedListUpdateEvent).

Equivalence for the 104 Java call sites across 10 consuming modules is
proven by a 95-test Java characterization suite (22 files) that compiles
and passes unmodified against both the pre- and post-conversion classes,
per Plan Decision D10 — only FeedItemEventTest.java and
SleepTimerUpdatedEventTest.java changed after Step 2 (+1 method each: D7's
disclosed narrowing, and a Long.MIN_VALUE overflow case added during
implementation red-team). javap -p on the built classes confirms
field/method shapes match byte-for-byte where the plan requires it (AC7).
Also adds kotlin.android + ktlint to event/build.gradle (prerequisite,
D12) and updates event/README.md's stale "plain Java POJOs" claim plus
module conventions.

Deviation disclosed: AC10's "checkstyle lint green repo-wide" could not
be fully satisfied — see Deviations below.
```

### Test commands run

Discovered variants: `:event` has no product flavors of its own (unflavored library module, `debug`/`release` build types only) — confirmed via `./gradlew :event:tasks --group verification`, so `testDebugUnitTest`/`testReleaseUnitTest` are the real task names, matching the Plan's own commands. The 9 consuming modules named in AC12 (`:storage:database`, `:storage:importexport`, `:playback:service`, `:playback:cast`, `:net:download:service`, `:net:sync:service`, `:ui:preferences`, `:ui:statistics`, `:ui:discovery`) all apply `playFlavor.gradle` and expose `testFreeDebugUnitTest`/`testPlayDebugUnitTest` (not a plain `testDebugUnitTest` — confirmed by an initial "task ambiguous" error, then corrected).

- `./gradlew :event:testDebugUnitTest --rerun` — before Step 1 (no test source set yet): PASS (0 tests). After Step 1 (11 test files, Tier C+D characterization): PASS, 54/54. After Step 2 (+11 test files, Tier A+B+identity): PASS, 93/93 — this is the AC1/AC2 baseline count.
- After Step 3 (Tier A → Kotlin): PASS, 93/93.
- After Step 4 (Tier B → Kotlin): PASS, 93/93.
- After Step 5 (Tier C → Kotlin, `@JvmField`): PASS, 94/94 (+1, `FeedItemEventTest.constructorRejectsNullItemsAfterConversion`, per D7).
- After Step 6 (Tier D → Kotlin): PASS, 94/94. **`:event` is now 23/23 Kotlin, 0 `.java` under `src/main`.**
- After each kotlin-j2k-style idiom pass (4 passes, one per tier): re-ran `--rerun`, still 94/94 each time.
- `./gradlew :app:assembleDebug` — green after Step 3 (D21's wiring proof), green again after Step 5 (D1's hard-stop checkpoint — no break, so the trigger did not fire), green again after Step 6/Step 7. Confirmed via task graph that both `:app:assembleFreeDebug` and `:app:assemblePlayDebug` ran, not just one flavor.
- `./gradlew :storage:database:testFreeDebugUnitTest :storage:database:testPlayDebugUnitTest :storage:importexport:testFreeDebugUnitTest :storage:importexport:testPlayDebugUnitTest :playback:service:testFreeDebugUnitTest :playback:service:testPlayDebugUnitTest :playback:cast:testFreeDebugUnitTest :playback:cast:testPlayDebugUnitTest :net:sync:service:testFreeDebugUnitTest :net:sync:service:testPlayDebugUnitTest :ui:preferences:testFreeDebugUnitTest :ui:preferences:testPlayDebugUnitTest :ui:statistics:testFreeDebugUnitTest :ui:statistics:testPlayDebugUnitTest :ui:discovery:testFreeDebugUnitTest :ui:discovery:testPlayDebugUnitTest` — all green.
- `./gradlew :net:download:service:testFreeDebugUnitTest :net:download:service:testPlayDebugUnitTest` — **FAILED, 6/79 tests failing in both flavors** (`LocalFeedUpdaterTest`, NPEs on `SynchronizationQueue.getInstance()` and `Feed.getPreferences()` both returning null). Verified pre-existing and unrelated to `:event`: `git stash`-ed all of this milestone's changes and re-ran the identical task against the untouched `develop` baseline — same 6/79 failure, same stack traces. Not a regression; not fixed (out of File Scope).
- `./gradlew :event:ktlintCheck` — green (after fixing 8 violations the initial mechanical conversion introduced: unnecessary trailing commas in `FeedEvent`/`PlaybackServiceEvent`/`QueueEvent`/`MessageEvent`/`SleepTimerUpdatedEvent`, and import-ordering blank lines in `MessageEvent.kt`/`SleepTimerUpdatedEvent.kt`).
- `./gradlew :event:checkstyle :event:lintDebug :event:lintRelease` — all green (module-local; `:event:checkstyle` is trivially satisfied since it scans `src/main/java/**/*.java`, which is now empty).
- `./gradlew checkstyle lint` (repo-wide, per AGENTS.md) — **did not come back fully green; see Deviations.** `app-wearos:compile{Free,Play}DebugKotlin` fails (pre-existing on `develop`, confirmed via the same stash-and-compare method, unrelated to `:event`). `:app:spotbugsPlayDebug`/`spotbugsFreeDebug` newly report 6 additional `NP_NULL_ON_SOME_PATH`/`NP_NULL_PARAM_DEREF` violations on top of 1 pre-existing one — see Deviations for the full analysis.
- `javap -p` — run on the built `:event` debug classes for every AC7-named class; verbatim output below.

```
$ javap -p event/build/tmp/kotlin-classes/debug/de/danoeh/antennapod/event/FeedUpdateRunningEvent.class
public final class de.danoeh.antennapod.event.FeedUpdateRunningEvent {
  public final boolean isFeedUpdateRunning;
  public de.danoeh.antennapod.event.FeedUpdateRunningEvent(boolean);
}
```
No `isFeedUpdateRunning()` method — confirms D3.

```
$ javap -p event/build/tmp/kotlin-classes/debug/de/danoeh/antennapod/event/FeedItemEvent.class
public final class de.danoeh.antennapod.event.FeedItemEvent {
  public static final de.danoeh.antennapod.event.FeedItemEvent$Companion Companion;
  public final java.util.List<de.danoeh.antennapod.model.feed.FeedItem> items;
  public final boolean unreadStatusChanged;
  public de.danoeh.antennapod.event.FeedItemEvent(java.util.List<de.danoeh.antennapod.model.feed.FeedItem>, boolean);
  public static final int indexOfItemWithId(java.util.List<de.danoeh.antennapod.model.feed.FeedItem>, long);
  static {};
}
```
`items`/`unreadStatusChanged` are fields, no getters — confirms D2/D7.

```
$ javap -p event/build/tmp/kotlin-classes/debug/de/danoeh/antennapod/event/QueueEvent.class
public final class de.danoeh.antennapod.event.QueueEvent {
  public static final de.danoeh.antennapod.event.QueueEvent$Companion Companion;
  public final de.danoeh.antennapod.event.QueueEvent$Action action;
  public final de.danoeh.antennapod.model.feed.FeedItem item;
  public final java.util.List<de.danoeh.antennapod.model.feed.FeedItem> items;
  public final int position;
  private de.danoeh.antennapod.event.QueueEvent(...);
  public static final QueueEvent added(...); setQueue(...); removed(...); irreversibleRemoved(...); cleared(); sorted(...); moved(...);
  public de.danoeh.antennapod.event.QueueEvent(..., kotlin.jvm.internal.DefaultConstructorMarker);  // ACC_SYNTHETIC — see note below
  static {};
}
```
4 public final fields, 7 public static factories — confirms D2/D9. **Note on the extra 5-arg constructor:** Kotlin's compiler generates a bridge constructor (flagged `ACC_SYNTHETIC`, verified via `javap -v`) so the companion object — a separate class file — can call the outer class's `private` primary constructor; this is standard Kotlin codegen for "private constructor + companion factory," not something this conversion introduced deliberately. Verified empirically that it is **not** a new callable API surface: wrote a throwaway Java probe (`new QueueEvent(action, item, items, pos, (DefaultConstructorMarker) null)`) inside `event/src/test`, ran `:event:compileDebugUnitTestJavaWithJavac`, and got `error: constructor QueueEvent in class QueueEvent cannot be applied to given types; required: Action,FeedItem,List<FeedItem>,int` — javac does not resolve `ACC_SYNTHETIC` members from source, so no Java caller can reach it. Probe file deleted after the check; not part of the diff.

```
$ javap -p event/build/tmp/kotlin-classes/debug/de/danoeh/antennapod/event/MessageEvent.class
public final class de.danoeh.antennapod.event.MessageEvent {
  public final java.lang.String message;
  public final androidx.core.util.Consumer<android.content.Context> action;
  public final java.lang.String actionText;
  public de.danoeh.antennapod.event.MessageEvent(java.lang.String, androidx.core.util.Consumer<android.content.Context>, java.lang.String);
  public de.danoeh.antennapod.event.MessageEvent(java.lang.String);
}
```
Exactly 2 constructors (not 3) — confirms D15, no `@JvmOverloads` used.

```
$ javap -p event/build/tmp/kotlin-classes/debug/de/danoeh/antennapod/event/FeedListUpdateEvent.class
public final class de.danoeh.antennapod.event.FeedListUpdateEvent {
  private final java.util.List<java.lang.Long> feeds;
  public de.danoeh.antennapod.event.FeedListUpdateEvent(java.util.List<de.danoeh.antennapod.model.feed.Feed>);
  public de.danoeh.antennapod.event.FeedListUpdateEvent(de.danoeh.antennapod.model.feed.Feed);
  public de.danoeh.antennapod.event.FeedListUpdateEvent(long);
  public final boolean contains(de.danoeh.antennapod.model.feed.Feed);
}
```
Exactly 3 public constructors with original parameter types, no synthetic bridge (none of the three needed one) — confirms the red-team's suggested AC7 extension.

```
$ javap -p event/build/tmp/kotlin-classes/debug/de/danoeh/antennapod/event/playback/BufferUpdateEvent.class
  private final float progress;
  public final float getProgress();     // exactly one, hand-written getter deleted (D4)
  public final boolean hasStarted();    // no-arg method, not getHasStarted()
  public final boolean hasEnded();      // no-arg method, not getHasEnded()
```

```
$ javap -p event/build/tmp/kotlin-classes/debug/de/danoeh/antennapod/event/playback/SleepTimerUpdatedEvent.class
  public final boolean wasJustEnabled();   // not getWasJustEnabled() — confirms D13
```

```
$ javap -p event/build/tmp/kotlin-classes/debug/de/danoeh/antennapod/event/FeedEvent.class
  private final de.danoeh.antennapod.event.FeedEvent$Action action;   // stays private, no getter
  public final long feedId;
  public java.lang.String toString();
```

### Characterization test results

All 22 test files, 94 total tests. Before/after status is identical (PASS→PASS) across every step from Step 3 onward, since Steps 1–2 established the baseline against the original Java classes and Steps 3–6 are the "after" runs against the converted Kotlin classes with the same (unmodified) suite:

| Test file | Tests | Real behavior exercised |
|---|---|---|
| `PublicFieldInteropTest` | 8 | All 12 `@JvmField` fields on the 6 Tier C classes read via bare field syntax (`event.action`, `event.items`, etc.) — the D2/D10 core equivalence proof |
| `FeedUpdateRunningEventTest` | 2 | `isFeedUpdateRunning` field read for both `true`/`false` — the D3 `is`-prefix field-emission hazard |
| `playback/PlaybackServiceEventTest` | 2 | `action` field for both enum constants |
| `FeedEventTest` | 2 | `feedId` field read; exact-string `toString()` including the private `action` field |
| `FeedItemEventTest` | 6 (+1 after Step 5) | `indexOfItemWithId` hit/miss/-1/null-element/empty-list; the +1 is D7's disclosed narrowing (`new FeedItemEvent(null, false)` now throws NPE at construction, asserted via `assertThrows`) |
| `QueueEventTest` | 8 | All 7 static factories × all 4 fields incl. the `-1` position sentinel; `Action.values().length == 9` and per-constant ordinals (D9, dead constants preserved) |
| `MessageEventTest` | 3 | Both constructors; `Consumer<Context>` stored by identity and invoked via `.accept(null)` with no Android runtime |
| `playback/BufferUpdateEventTest` | 7 | All 3 factories × `getProgress()`/`hasStarted()`/`hasEnded()`; `NaN`/`-0.0f`/`0.0f`; the `progressUpdate(-1f).hasStarted() == true` sentinel masquerade preserved as-is (D17) |
| `playback/SleepTimerUpdatedEventTest` | 5 | All 3 factories × 5 accessors; the `updated()` zero-clamp; the `justEnabled()` sign-negation trick; `Long.MAX_VALUE` cancellation |
| `EpisodeDownloadEventTest` | 6 | `getUrls()` live-view-of-map semantics (post-construction mutation visible); `indexOfItemWithDownloadUrl` hit/miss/null-item/null-media; the required NPE on null `downloadUrl` (D8) |
| `FeedListUpdateEventTest` | 5 | All 3 constructor overloads; `contains()` by id not identity; `Collections.emptyList()` always-false path; `new FeedListUpdateEvent(0)` resolving to the `long` overload |
| `MarkerEventsTest` | 4 | Construction of the 4 truly-empty Tier A classes |
| `DownloadLogEventTest` / `playback/PlaybackHistoryEventTest` | 2 each | `listUpdated()` returns a new instance each call (not cached); exact-string `toString()` |
| `playback/PlaybackPositionEventTest`, `playback/SpeedChangedEventTest`, `PlayerErrorEventTest`, `SyncServiceEventTest`, `settings/VolumeAdaptionChangedEventTest`, `settings/SkipIntroEndingChangedEventTest`, `settings/SpeedPresetChangedEventTest` | 1–2 each | Tier B field storage/readback, incl. `PlayerErrorEventTest`'s null-message case (D6) |
| `EventIdentityEqualityTest` | 23 | Two content-identical instances of **every** one of the 23 classes are not `.equals()`, have different `hashCode()`, and (for the 20 without a hand-written override) emit the JVM default `ClassName@hash` `toString()` — the D18 no-`data class` proof |

AC5: only `FeedItemEventTest.java` changed after Step 2 (the +1 method per D7); every other test file is byte-identical from its Step-1/2 creation through Step 6. (No intermediate commits were made per this pipeline's "never commit unless asked" rule, so this is stated from direct tracking of every edit made during the session rather than a `git diff --stat` against a merge base — the migration-code-reviewer can verify by diffing the working tree's `event/src/test/` against a fresh checkout once commits exist.)

### Deviations from plan

**AC10 partially unmet — `./gradlew checkstyle lint` does not come back fully green repo-wide, and the gap cannot be closed inside File Scope.**

Two separate causes, both verified pre-existing/out-of-scope via `git stash` + re-run against untouched `develop`:

1. `:app-wearos:compile{Free,Play}DebugKotlin` fails today on `develop` with an unrelated `String?`/`String` type mismatch in `EpisodeDetailActivity.kt:115`. `app-wearos` is not one of the 10 modules that depend on `:event` (confirmed in Research); this failure is untouched by this milestone and was already present before any `:event` changes.

2. `:app:spotbugsPlayDebug`/`spotbugsFreeDebug` (wired into the `lint` task by `common.gradle`) reports **7 violations after conversion vs. 1 before** (the pre-existing 1, `FeedInfoFragment.java:214`, unrelated to `:event`, confirmed present on stashed `develop`). The 6 new ones are:
   - `MainActivity.java:700`, `MainActivity.java:705`, `OnlineFeedViewActivity.java:496`, `PreferenceActivity.java:180` — `NP_NULL_PARAM_DEREF`, `Snackbar.make(View, CharSequence, int)` called with a value SpotBugs now knows can be null.
   - `QueueFragment.java:158`, `QueueFragment.java:169` — `NP_NULL_ON_SOME_PATH`, dereference of `de.danoeh.antennapod.event.QueueEvent.item`.

   **Root cause, verified by direct comparison of the Java source and the compiled Kotlin bytecode:** these code paths were **already** null-unsafe in the original Java — `PlayerErrorEvent.message`/`MessageEvent.message` were unannotated `String` fields with real null producers (`e.getLocalizedMessage()`, `mediaError.getReason()`), and `QueueEvent.item` was already null for 3 of 7 factories (`setQueue`/`cleared`/`sorted`) with `QueueFragment.java:158,169` dereferencing it unguarded in some branches — but the Java fields carried **no** `@Nullable` annotation, so SpotBugs's null-dataflow analysis had nothing to key on. Kotlin's `String?`/`FeedItem?` declarations (both required by Plan D6, verified against real call sites, not guesses) compile to fields carrying `@org.jetbrains.annotations.Nullable` metadata, which SpotBugs *does* read. **This conversion did not introduce a new runtime risk — it made an existing one visible to static analysis for the first time.** Fixing it would mean adding null-guards in `MainActivity.java`, `OnlineFeedViewActivity.java`, `PreferenceActivity.java`, and `QueueFragment.java` — all outside File Scope, and exactly the kind of "expanding scope = new task" line D1/D2/OQ1 already draw for this milestone.

   Not fixed. Not silenced (no SpotBugs suppression added — that would hide a real, if pre-existing, latent bug rather than disclose it). Flagged here for the code reviewer/red-team/planner to decide: accept AC10 as "green within File Scope, pre-existing gap outside it" (recommended — matches D9/D20's precedent of not fixing pre-existing issues found incidentally during conversion), or open a follow-up task scoped to the 4 consumer files to add the null-guards SpotBugs is now correctly asking for.

No other deviations. All other Acceptance Criteria (AC1–AC9, AC11–AC15) verified as specified in the Plan.

---

## Code Review Verdict
_By: migration-code-reviewer | 2026-07-26 | Loop 1 of max 3_

### Verdict
REQUEST CHANGES

### Method
Independently re-verified rather than trusted: `git status`/`git diff` for File Scope adherence; read all 23 converted `.kt` files against their `develop`-baseline `.java` originals; read all 22 test files in full; ran `:event:testDebugUnitTest --rerun` fresh (94/94 across 22 test-result XMLs, matching the claimed count); ran `:event:ktlintCheck` and `:event:checkstyle` fresh (green / NO-SOURCE); ran `javap -p` on every AC7-named class and diffed against the pasted transcripts (byte-identical); ran `:app:assembleDebug` (both flavors, cached green) and a sample of AC12's consuming-module test tasks (green); independently reproduced the AC10 SpotBugs deviation by running `:app:spotbugsPlayDebug` on the diff (7 violations, exact same 4 files/7 line numbers as disclosed) and then `git stash`-ed the entire working tree to rerun the same task, `:app-wearos:compileFreeDebugKotlin`, and `:net:download:service:testFreeDebugUnitTest` against unmodified `develop` (1 pre-existing SpotBugs violation, same `app-wearos` compile error, same 6/79 `LocalFeedUpdaterTest` failures — all confirmed pre-existing and unrelated to `:event`) before restoring the stash.

File Scope: exact match, no out-of-scope files touched. AC1–AC4, AC6–AC15 independently confirmed as reported. D1–D21 spot-checked against the actual `.kt` source for every Tier and confirmed applied as specified (12 `@JvmField`s across the 6 correct classes incl. `FeedEvent.action` correctly staying private; 17 `@JvmStatic`s; `MutableList<FeedItem>?` with no defensive copies; the single `!!` in `EpisodeDownloadEvent.kt` on the `downloadUrl` comparison; `0L`/`max()` widening fix in `SleepTimerUpdatedEvent`; `MessageEvent`'s secondary constructor with exactly 2 constructors per `javap`; `QueueEvent.Action` 9 constants verbatim with `DELETED_MEDIA`/`ADDED_ITEMS` kept; D13's fun-not-property rule holds for `hasStarted`/`hasEnded`/`wasJustEnabled`/`contains`/both `listUpdated`s). Test assertions are real, not just invocations — every file read exercises actual output/state, including the sentinel-edge-case tests (`NaN`, `-0.0f`, `Long.MAX_VALUE`) and the two D7/D8 `assertThrows` tests.

The AC10 SpotBugs deviation is accepted as a documented, non-blocking deviation, not a File Scope expansion or a blocker: independently confirmed all 6 new findings trace to fields that D6 correctly declared nullable per already-existing null producers (`MessageEvent.message`, `PlayerErrorEvent.message`, `QueueEvent.item`), the risk is genuinely pre-existing (same unguarded reads exist in the Java-era bytecode, just invisible to SpotBugs without Kotlin's nullability metadata), fixing requires editing 4 files explicitly outside File Scope, and D1's hard-stop trigger is scoped to compile breaks, not static-analysis findings. Recommend logging a follow-up task scoped to those 4 consumer files rather than acting on it here — that is the planner's/José's call, not a review blocker.

### Findings

- **Severity:** MAJOR
- **Class:** Convention
- **File:line:** `event/src/main/java/de/danoeh/antennapod/event/EpisodeDownloadEvent.kt:17-19`
- **Finding:** Three lines of comments were added to `indexOfItemWithDownloadUrl` explaining the D8 `!!` justification. The `develop`-baseline Java original (`EpisodeDownloadEvent.java`) has no comments at all in this method, and this repo's `AGENTS.md` states, in the section marked "STRICTLY FOLLOW THE INSTRUCTIONS IN THIS FILE! NEVER DEVIATE FROM THEM": *"Do not add any comments to the code you write, but also do not remove comments that are already in the code."* Verified this is the only comment added anywhere in the 23 converted files (`grep -n '//' event/src/main/java/de/danoeh/antennapod/event/*.kt event/src/main/java/de/danoeh/antennapod/event/*/*.kt` returns hits only in this file).
- **Suggested fix:** Delete the three-line comment block. The `!!` justification already lives in the Plan (D8) and in `EpisodeDownloadEventTest.indexOfItemWithDownloadUrlThrowsNpeWhenMediaHasNullDownloadUrl`'s name/assertion — no in-source comment is needed to satisfy AC8's "justified and test-pinned" bar.

- **Severity:** MINOR
- **Class:** Tests
- **File:line:** `tasks/antennapod-event-kotlin.md:637` (Implementation Notes, AC5 discussion)
- **Finding:** AC5 as written in the Plan requires `git diff --stat <step-1..2 merge base> -- event/src/test/` to mechanically prove only `FeedItemEventTest.java` changed across Steps 3–6 — the load-bearing check for D10's whole equivalence-oracle mechanism (a test suite quietly loosened to fit the Kotlin output would invalidate the entire milestone's thesis without producing a compile error). No incremental commits exist (correctly, per this pipeline's "never commit unless asked" rule), so this specific mechanical check is not currently possible for any reviewer to run — the Implementation Notes already flag this honestly rather than paper over it. I read all 22 test files in full and found no evidence of the suite being weakened to accommodate the implementation (assertions are content-specific throughout, edge cases match the Plan's Step 1/2 specification), so I do not believe the underlying property is false — only that it is currently unverifiable by the mechanism the Plan itself specifies.
- **Suggested fix:** Not a blocker for this loop. When this PR is committed (per the six-commit-per-tier structure D21 already calls for), the `git diff --stat` check becomes runnable retroactively and should be run once more, either by this reviewer or by `legacy-android-red-team`, before the PR is opened.

- **Severity:** MINOR
- **Class:** Quality
- **File:line:** `tasks/antennapod-event-kotlin.md:527` (Implementation Notes, Test commands run)
- **Finding:** The note describing the pre-existing `:net:download:service` failure says "all NPE on `SynchronizationQueue.getInstance()` returning null." Independently reproducing the 6 failures (`LocalFeedUpdaterTest`) shows two distinct NPE causes across the 6: some on `SynchronizationQueue.getInstance()` returning null, others on `Feed.getPreferences()` returning null (`net/download/service/build/test-results/testFreeDebugUnitTest/`). The underlying claim — pre-existing, unrelated to `:event`, confirmed byte-identical against unstashed `develop` — is correct and independently reproduced; only the one-line characterization of the cause is imprecise.
- **Suggested fix:** Optional wording fix in Implementation Notes: "NPEs on `SynchronizationQueue.getInstance()` and `Feed.getPreferences()` both returning null" — no code change needed.

### Summary
One MAJOR finding blocks APPROVE this loop: an added comment in `EpisodeDownloadEvent.kt` violates this repo's explicit no-added-comments rule. It is a one-line fix. The two MINOR findings do not block and are recorded for traceability/wording only. Everything else — File Scope, all 21 Decisions, all 15 Acceptance Criteria, the disclosed AC10 deviation, and the cross-module verification claims — was independently re-verified against the actual built artifacts and source, not taken on the developer's word, and held up.

### Fix pass (2026-07-26)
MAJOR finding fixed: deleted the 3-line comment block in `EpisodeDownloadEvent.kt:17-19`, leaving only `media != null && media.downloadUrl!! == downloadUrl` (matches the Java baseline's comment-free style). Re-ran `:event:testDebugUnitTest --rerun`: 94/94 green, unchanged. MINOR wording finding also fixed: Test commands run's `:net:download:service` note now cites both NPE causes (`SynchronizationQueue.getInstance()` and `Feed.getPreferences()`). MINOR AC5 finding not actioned (correctly unverifiable pre-commit, per the reviewer's own note) — will be re-checked once commits exist, before PR.

---

## Code Review Verdict
_By: migration-code-reviewer | 2026-07-26 | Loop 2 of max 3_

### Verdict
APPROVE

### Method
Independently re-verified the fix pass rather than trusting the note: read `EpisodeDownloadEvent.kt` in full and diffed it against `git show develop:event/src/main/java/de/danoeh/antennapod/event/EpisodeDownloadEvent.java` — the 3-line comment block is gone, and `grep -rn '//' event/src/main/java/de/danoeh/antennapod/event/*.kt event/src/main/java/de/danoeh/antennapod/event/*/*.kt` returns zero hits across all 23 converted files, confirming the fix is complete and no comment was reintroduced elsewhere. Ran `./gradlew :event:testDebugUnitTest --rerun` fresh: BUILD SUCCESSFUL, and independently counted `tests=` totals across all 22 result XMLs in `event/build/test-results/testDebugUnitTest/` — 94/94, matching the reported count. Ran `./gradlew :event:ktlintCheck` fresh: green. Re-ran `git diff --name-only develop -- .` plus `git status --porcelain` for untracked files and matched the full list (23 renamed `.kt` files, `event/build.gradle`, `event/README.md`, `tasks/antennapod-event-kotlin.md`, `features/antennapod-event-kotlin.checkpoint.md`, `tasks/antennapod-model-kotlin-future-work.md`, 22 test files under `event/src/test/`) against the Plan's File Scope — exact match, no drift since loop 1, no new out-of-scope file. Diffed `tasks/antennapod-model-kotlin-future-work.md` against `develop`: both hunks are pure appends to items #3 and #5, as File Scope requires — no other edits.

### Findings

None open. Loop 1's MAJOR finding (added comment in `EpisodeDownloadEvent.kt:17-19`) is confirmed fixed — the file now reads `media != null && media.downloadUrl!! == downloadUrl` with no comment, matching the Java baseline's comment-free style. Loop 1's MINOR wording finding (`:net:download:service` failure root-cause description) is confirmed corrected in Test commands run. Loop 1's other MINOR finding (AC5's `git diff --stat` proof being unrunnable pre-commit) was correctly left un-actioned per the reviewer's own note in loop 1 — it is not a defect, just a mechanical check deferred until commits exist, and remains non-blocking.

### Summary
Both loop-1 findings are resolved and independently reproduced clean: the comment is gone (confirmed by direct read + repo-wide grep), tests are still 94/94 green (confirmed by a fresh `--rerun` and independent XML count), ktlint is green, and File Scope is an exact match with no drift. Nothing else changed since loop 1 that would reopen prior checks. APPROVE.

---

## Red-Team Verdict — Implementation
_By: legacy-android-red-team | 2026-07-26 | Loop 1 of max 2_

### Verdict
CHALLENGE

### Concerns

- **Severity:** MAJOR
- **Class:** Characterization tests prove equivalence, not just existence
- **Concern:** Plan Decision D16 makes a specific, falsifiable verification promise about `SleepTimerUpdatedEvent`'s numeric encoding: *"The sign trick is load-bearing (`Math.abs(Long.MIN_VALUE) == Long.MIN_VALUE`) and is pinned by test, not by inspection."* It is not pinned by test. `SleepTimerUpdatedEventTest.java`'s 5 tests use only small representative values — `justEnabled(new TimerValue(5L, 1000L))`, `updated(new TimerValue(-5L, -1000L))`, `updated(new TimerValue(7L, 2000L))`, `cancelled()` (`Long.MAX_VALUE`), and `justEnabled(new TimerValue(0L, 12345L))` — none constructs a `TimerValue` anywhere near `Long.MIN_VALUE`, the exact overflow case the Decision names by number. A repo-wide `grep -rn "MIN_VALUE" event/src/test` returns zero hits. This is the identical hazard Research flagged verbatim ("the sign trick is load-bearing and edge-case-sensitive... Needs tests pinning each factory × each accessor") and the Plan explicitly committed to closing via D16 — and the commitment wasn't kept. In a pipeline whose entire pitch is "verified, not reasoned," a Decision asserting a specific behavior is test-pinned when it is only inspection-pinned is exactly the class of gap this review exists to catch, independent of whether the underlying behavior actually diverges.
- **Evidence:** `tasks/antennapod-event-kotlin.md` D16 (lines 296–300, "is pinned by test, not by inspection"); `event/src/test/java/de/danoeh/antennapod/event/playback/SleepTimerUpdatedEventTest.java` (all 5 tests read in full — no `Long.MIN_VALUE` or near-overflow input); confirmed via `grep -rn "MIN_VALUE" event/src/test` (no hits) and `javap -p` on the built `SleepTimerUpdatedEvent.class` (independently re-run — `wasJustEnabled()` etc. match the Plan's claimed shape, unrelated to this finding).
- **Suggested mitigation:** Not a live equivalence risk today — verified by inspection that `kotlin.math.abs(Long)` (`event/.../SleepTimerUpdatedEvent.kt:10`) delegates to the identical two's-complement `Math.abs(long)` semantics as the Java original, so no behavior actually diverges pre/post-conversion for this input; the Plan's chosen "must be test-pinned, not inspected" bar is simply unmet, not silently violated in practice. Add one test to `SleepTimerUpdatedEventTest.java`, e.g. `justEnabledWithMinValueMillisPreservesOverflowedNegation`, constructing `justEnabled(new TimerValue(0L, Long.MIN_VALUE))` and asserting `getMillisTimeLeft() == Long.MIN_VALUE` (i.e., still negative — the overflow, not a "fixed" positive value) — so a future refactor of the sign trick (e.g., a well-intentioned switch to a saturating or `Math.absExact`-style negation) fails loudly in `:event`'s own suite instead of silently, which is precisely the mechanism D10 relies on for the other 93 tests.

### Categories considered and dismissed (no finding)

- **`QueueEvent` items aliasing (D5).** Independently read `QueueEvent.kt` and `QueueEventTest`/`PublicFieldInteropTest`: `MutableList<FeedItem>?` is `@JvmField`, no defensive copy anywhere (`grep -rnE 'toList\(\)|toMutableList\(\)|unmodifiable' event/src/main` → zero hits), and `QueueEventTest.setQueueSetsActionAndItemsWithMinusOnePosition`/`sortedSetsActionAndItemsWithMinusOnePosition` use `assertSame(queue, event.items)` — genuinely proving the live-reference-not-copy contract `QueueFragment.java:153`'s `queue = event.items;` then `queue.add/.remove/.clear` depends on, not just that the value is non-null. javap confirms the field erases to plain `java.util.List`, identical to the Java original.
- **`SleepTimerUpdatedEvent` sentinel encoding, aside from the finding above.** `justEnabled()`'s negation, `updated()`'s `max(x, 0L)` zero-clamp (confirmed `0L` literal present, not `0`, avoiding the int/long overload-resolution trap D16 also names), and `cancelled()`'s `Long.MAX_VALUE` dual-assignment are all genuinely test-pinned with real value assertions (not just invocations) in `SleepTimerUpdatedEventTest`.
- **`BufferUpdateEvent` collapsed field+getter (D4).** `javap -p` (re-run independently) shows exactly one `public final float getProgress()` and no residual package-private field or duplicate accessor; `progress` is `Float` (never `Float?`), so `==` against `const val` sentinels stays IEEE primitive comparison, not boxed `.equals()`. `BufferUpdateEventTest` pins `NaN`, `-0.0f`, `0.0f`, and the known `progressUpdate(-1f).hasStarted() == true` masquerade as current (unfixed) behavior, matching D17.
- **`EpisodeDownloadEvent.indexOfItemWithDownloadUrl` NPE preservation (D8).** Verbatim `media.downloadUrl!! == downloadUrl` (`EpisodeDownloadEvent.kt:17`) — confirmed the sole `!!` in the module (`grep -rn '!!' event/src/main`, exactly one hit). The developer's mechanical for-loop → `indexOfFirst {}` rewrite changes surface syntax but not iteration order, short-circuit evaluation, or exception timing — traced manually against the original loop and confirmed the NPE fires at the same relative position for the same inputs, and `indexOfItemWithDownloadUrlThrowsNpeWhenMediaHasNullDownloadUrl` pins it with `assertThrows`.
- **`MessageEvent` secondary constructor + `Consumer<Context>` preservation (D15).** `javap` shows exactly 2 constructors (not 3 — `@JvmOverloads` was not used); `androidx.core.util.Consumer<Context>` is untouched, not converted to a function type; `MessageEventTest.actionIsStoredByIdentityAndInvokedViaAccept` proves identity storage and functional invocation without any Android runtime dependency.
- **`FeedListUpdateEvent`'s 3-constructor shape** (added in the plan-polish pass after plan-review loop 1, per the task prompt). Independently read `FeedListUpdateEvent.kt` line-by-line against the Java original: the `(long feedId)` secondary constructor delegates to the primary with an empty list (a no-op init block) and then mutates `feeds` in its own body — reproducing the exact final state `feeds = [feedId]` the Java constructor produces, with no double-population and no order-of-initialization surprise. `javap` confirms exactly 3 public constructors with original parameter types (extending AC7 as the plan-level red-team suggested), and `FeedListUpdateEventTest` exercises all three overloads plus the always-false `Collections.emptyList()` path and `long`-overload resolution for an `int` literal argument.
- **`isFeedUpdateRunning` / `unreadStatusChanged` field-not-getter trap (D3).** Re-ran `javap -p` on `FeedUpdateRunningEvent.class` and `FeedItemEvent.class` myself: both fields, no accessor methods, matching the Plan's transcript exactly. Grepped the whole converted module for any other `is`-prefixed boolean and found none — `isFeedUpdateRunning` remains the only instance of this hazard shape in `:event`.
- **No `data class` anywhere (D18).** Read all 23 `.kt` files: none declares `equals`/`hashCode`, none is a `data class`. `EventIdentityEqualityTest`'s 23 tests each assert `assertNotSame` + `!a.equals(b)` + differing `hashCode()`, which is a genuine reference-identity proof, not a construction-only smoke test.
- **File Scope and doc-only files.** `git status --porcelain=v1 -uall`, filtered against the Plan's File Scope list, shows zero drift: exactly the 23 renamed `.kt` files, `event/build.gradle`, `event/README.md`, the two task/checkpoint files, and `tasks/antennapod-model-kotlin-future-work.md`. Diffed the last file directly (`git diff develop -- tasks/antennapod-model-kotlin-future-work.md`): both hunks are pure appends to items #3 and #5, nothing else touched. `event/README.md`'s diff matches Step 7's five named conventions with no extra claims.
- **AC10 SpotBugs deviation disposition.** Read the 6 flagged call sites directly (`MainActivity.java:700,705`, `OnlineFeedViewActivity.java:496`, `PreferenceActivity.java:180`, `QueueFragment.java:158,169`) and confirmed each dereferences a field (`MessageEvent.message`, `QueueEvent.item`) that D6 correctly declared nullable against a real pre-existing null producer or a real pre-existing null-producing factory (`setQueue`/`cleared`/`sorted`) — the unguarded reads were already latent in the Java bytecode; only SpotBugs's visibility into them changed, via Kotlin's `@Nullable` metadata. This is the correct disposition and matches the D9/D20 "don't fix pre-existing issues found incidentally, don't silence them either" precedent — accept as documented and non-blocking, log a follow-up scoped to the 4 consumer files. No SpotBugs suppression was added (confirmed no edits to `config/spotbugs/exclude.xml`), so the finding stays visible for whoever picks up the follow-up rather than being hidden.
- **Third-sibling-issue sweep** (the pattern of "looks equivalent but isn't," per Milestone 7's loop-2 precedent). Beyond the two hazards Research/Plan already caught (`isFeedUpdateRunning` naming, `BufferUpdateEvent` field/getter clash) and the one gap raised above, checked for a third instance of "declaration compiles, `javap` looks right, but a promised test doesn't actually cover the case it claims to" by re-reading every Decision (D1–D21) against its corresponding test file for a similar promise/delivery gap. Found none beyond D16 — `BufferUpdateEvent`'s NaN/-0.0f/0.0f promise (D17), `EpisodeDownloadEvent`'s NPE promise (D8), `FeedItemEvent`'s null-rejection promise (D7), and `QueueEvent`'s dead-constant-ordinal promise (D9) are each backed by an actual assertion exercising the named edge case, not just a general-shape test.

### Verification methods used
Direct reads of all 23 converted `.kt` files against their `develop`-baseline `.java` originals (via `git show develop:...`); direct reads of all 22 test files in full; independently ran `./gradlew :event:testDebugUnitTest --rerun` (BUILD SUCCESSFUL) and cross-checked the XML result files (`grep -o 'tests="[0-9]*"' ... | awk` → 94 total, 0 failures, 22 suites); independently ran `./gradlew :event:compileDebugKotlin` and `javap -p` against the built classes for `QueueEvent`, `FeedListUpdateEvent`, and `SleepTimerUpdatedEvent`, diffed against the Implementation Notes' pasted transcripts (byte-identical); independently ran `./gradlew :event:ktlintCheck` (green); `grep`-verified AC8 (`!!` count = 1, exact location) and AC9 (`data class`/`toList()`/`toMutableList()`/`toSet()`/`unmodifiable`/`operator fun` = 0 hits) directly rather than trusting the reported counts; `git status --porcelain=v1 -uall` plus targeted `git diff` on both doc files for File Scope adherence; read the 6 SpotBugs-flagged call sites in the consuming modules directly to verify the AC10 deviation's root-cause claim.

### Outcome
One MAJOR finding: loop back to `android-migration-developer` to add the `Long.MIN_VALUE` characterization test to `SleepTimerUpdatedEventTest.java` per the suggested mitigation above, then re-run `:event:testDebugUnitTest --rerun` and re-request `migration-code-reviewer`/`legacy-android-red-team` sign-off. This is a single-test, in-File-Scope fix with no production-code change implied — it should not require a new round through `migration-code-reviewer`'s full loop budget, but does need this red-team's re-approval (loop 2 of max 2) before PR, since D16's language should also be reconciled with whatever the added test actually asserts.

### Fix pass (2026-07-26)
Added `millisTimeLeftOverflowsBackToLongMinValueWhenNegatingLongMinValue` to `SleepTimerUpdatedEventTest.java`: constructs `SleepTimerUpdatedEvent.justEnabled(new TimerValue(0L, Long.MIN_VALUE))` and asserts `getMillisTimeLeft() == Long.MIN_VALUE`, exactly per the suggested mitigation. Re-ran `./gradlew :event:testDebugUnitTest --rerun`: 95/95 green (was 94/94; `SleepTimerUpdatedEventTest` now has 6 tests). D16's existing wording ("pinned by test, not by inspection") is now accurate as written — no text change needed, only the test addition. No production code touched.

---

## Red-Team Verdict — Implementation
_By: legacy-android-red-team | 2026-07-26 | Loop 2 of max 2 (final loop)_

### Verdict
APPROVE

### Method
Independently re-verified the fix pass rather than trusting the note.

- Read `event/src/test/java/de/danoeh/antennapod/event/playback/SleepTimerUpdatedEventTest.java` in full: `millisTimeLeftOverflowsBackToLongMinValueWhenNegatingLongMinValue` exists, constructs `SleepTimerUpdatedEvent.justEnabled(new TimerValue(0L, Long.MIN_VALUE))`, and asserts `assertEquals(Long.MIN_VALUE, event.getMillisTimeLeft())`.
- Traced the assertion against the actual converted source (`event/src/main/java/de/danoeh/antennapod/event/playback/SleepTimerUpdatedEvent.kt`): `justEnabled` stores `TimerValue(0L, -Long.MIN_VALUE)`; `-Long.MIN_VALUE` two's-complement-overflows back to `Long.MIN_VALUE`; `getMillisTimeLeft()` then computes `kotlin.math.abs(Long.MIN_VALUE)`. To confirm this isn't a guessed assertion, wrote and ran a standalone Java program (`Math.abs`, which `kotlin.math.abs(Long)` delegates to directly) — confirmed `-Long.MIN_VALUE == Long.MIN_VALUE` and `Math.abs(Long.MIN_VALUE) == Long.MIN_VALUE`, both `true`. The assertion matches real, verified overflow behavior, not an assumption.
- Confirmed the test is not tautological: it depends on the sign trick's actual two's-complement overflow, computed at runtime from a method parameter (not a compile-time constant), so a future "safer" reimplementation (saturating abs, `Math.absExact`, an `if`-guard against `MIN_VALUE`) would change the result and fail this test — which is exactly the protection D16 asked for.
- Ran `./gradlew :event:testDebugUnitTest --rerun` fresh: BUILD SUCCESSFUL. Independently counted `tests=`/`failures=`/`errors=` across all 22 XML files in `event/build/test-results/testDebugUnitTest/`: **95 tests, 0 failures, 0 errors** — matches the claimed 95/95.
- Ran `./gradlew :event:ktlintCheck` fresh: green, no changes needed.
- Checked `git status --porcelain=v1 -uall`: exact match to the File Scope established at loop 1 — 23 renamed `.kt` production files, `event/build.gradle`, `event/README.md`, 22 test files, the two task/checkpoint docs, `tasks/antennapod-model-kotlin-future-work.md`. No new or missing files.
- Confirmed no production drift: read `EpisodeDownloadEvent.kt` (loop-1 code review's comment-removal fix persisted, no reintroduced comment) and ran `grep -rn '//' event/src/main/java/de/danoeh/antennapod/event/*.kt event/src/main/java/de/danoeh/antennapod/event/*/*.kt` — zero hits across all 23 files. `find event/src/main -name '*.kt' | wc -l` → 23, `find event/src/main -name '*.java' | wc -l` → 0. `find event/src/test -name '*.java' | wc -l` → 22. This is a test-only diff, as claimed.

### Findings

None open. Loop 1's sole MAJOR finding — D16's "pinned by test, not by inspection" claim about `SleepTimerUpdatedEvent`'s `Long.MIN_VALUE` overflow was unbacked by any test — is resolved: the new test exists, genuinely exercises `Long.MIN_VALUE` (not a nearby value), and its assertion is independently verified correct against real JVM `Math.abs`/two's-complement overflow semantics rather than taken on faith. No new equivalence gaps found in this pass.

### Categories re-swept for this loop (no new findings)

- **Test existence vs. proof:** confirmed above — this was the entire point of the loop.
- **Silent behavior changes from mechanical translation:** no production file changed in the fix pass (verified by content + comment-persistence check, not just the note's claim).
- **Public API breakage:** File Scope unchanged since loop 1's implementation red-team pass; no new files, no consumer edits.
- **Scope creep:** the diff added exactly one test method to one file, matching the loop-1 outcome's prescribed single-test fix precisely — no broader change smuggled in.

### Outcome
Both red-team loops on this implementation are now clean (loop 1: one MAJOR, fixed and re-verified; loop 2: no open findings). Combined with `migration-code-reviewer`'s loop-2 APPROVE, this closes the implementation red-team gate for Milestone 8. Ready for PR per the AEPM production workflow's step 8.
