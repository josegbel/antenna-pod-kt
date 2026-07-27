# antennapod-event-kotlin-milestone-9

> **Description:** Convert the `:event` module's remaining Java test files to Kotlin, now that all production code in `:event` is 100% Kotlin (Milestone 8, PR #14, merged).
> **Repo:** `services/android-migration/projects/portfolio/antennapod`
> **Created:** 2026-07-26

> **Pre-research context (carried over from Milestone 8 / standing decisions — do not re-derive):**
> - `:event` production code is **100% Kotlin** as of Milestone 8 (PR #14, merged). Per that milestone's Plan Decision D10 (equivalence-oracle rationale), characterization tests were deliberately kept in **Java**, unchanged, across the production-code milestone — this was the trigger condition for the deferral, and it is now met (mirrors the `:model` Milestone 6 → Milestone 7 sequencing, see [[antennapod_kotlin_test_migration_sequencing]]).
> - `event/src/test/java/...` currently contains **20 Java test files** (verify actual count/layout in research, do not assume): `DownloadLogEventTest`, `EpisodeDownloadEventTest`, `EventIdentityEqualityTest`, `FeedEventTest`, `FeedItemEventTest`, `FeedListUpdateEventTest`, `FeedUpdateRunningEventTest`, `MarkerEventsTest`, `MessageEventTest`, `PlayerErrorEventTest`, `PublicFieldInteropTest`, `QueueEventTest`, `SyncServiceEventTest` (root package), plus `playback/BufferUpdateEventTest`, `playback/PlaybackHistoryEventTest`, `playback/PlaybackPositionEventTest`, `playback/PlaybackServiceEventTest`, `playback/SleepTimerUpdatedEventTest`, `playback/SpeedChangedEventTest`, `settings/SkipIntroEndingChangedEventTest`, `settings/SpeedPresetChangedEventTest`, `settings/VolumeAdaptionChangedEventTest`.
> - This milestone is a **test-only** conversion — no production `.kt` file should need to change as a result (unless a genuine J2K-interop issue forces a minimal, disclosed fix, per the module's established deviation-disclosure pattern from Milestone 8).
> - No Robolectric is currently in `:event`'s test deps (unlike `:model` Tier C) — do not add it unless research finds a genuine need; do not change test *behavior* here, only its language.
> - See `tasks/antennapod-model-kotlin-future-work.md` items #3 and #5 for cross-cutting, repo-wide follow-ups (`allWarningsAsErrors` for Kotlin test-compile, orphaned checkstyle suppressions) that this milestone's conversion will make apply to `:event` too — do not fold either into this milestone's File Scope, just note the new instance if it recurs.
> - Per standing instruction: run the full lifecycle autonomously (no human intervention between stages) and ship as a **single unified PR** (code + spec docs together), matching the Milestone 7 (`:model`) and Milestone 8 (`:event`) precedent.

## Research
_Last updated by: legacy-android-researcher | 2026-07-26_

### Summary

`:event` is a tiny, dependency-light module of plain event value classes whose production code is now 100% Kotlin — 23 `.kt` files under `event/src/main/java/de/danoeh/antennapod/event/`, zero `.java`. Its test source set is the exact mirror image: **22 `.java` files, zero `.kt`**, 1,027 LOC, 211 assertion calls, across three packages (`event`, `event.playback`, `event.settings`). **The pre-research callout's count of "20" is wrong — the live tree has 22 files, and the callout's own enumeration lists 22 names.** No `androidTest` source set exists (`event/src/` contains only `main` and `test`). The suite is green: a forced `./gradlew --console=plain :event:testDebugUnitTest --rerun` reports **95 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESSFUL, matching the 95 `@Test` annotations exactly (no parameterized classes, no `@Ignore`). The callout's other claims all verify: no Robolectric, no Mockito, no PowerMock, no hamcrest — `event/build.gradle:18` declares exactly one test dependency, `testImplementation libs.junit` (JUnit 4.13), and every static import in the suite is from `org.junit.Assert`.

The mechanical conversion is far smaller than `:model` Milestone 7 (22 files vs 29, 95 tests vs 232, zero helper classes vs three `Mother` builders), and **most of M7's specific traps do not recur**: there is no hamcrest so no `` `is` `` keyword collision, no Mockito so no `` `when` `` collision, no `ObjectInputStream in` locals, no try-with-resources, no reflection, no `@Before`/`@After`/`@Rule`/`@RunWith` anywhere, no underscore-containing test names, and — verified site-by-site against production types — **zero int→Long `assertEquals` widening traps** (every `assertEquals` against a `Long` already carries an `L` suffix). What replaces them is a different and, for this milestone, more serious problem: **a large fraction of `:event`'s test suite derives its oracle value specifically from being written in Java.** Milestone 8's own plan says so explicitly — `PublicFieldInteropTest` (8 tests) is described at `tasks/antennapod-event-kotlin.md:623` as "the D2/D10 core equivalence proof," and its M8 acceptance criterion (`:383`) was literally `grep -c '\.get[A-Z]'` returning 0 in that file, i.e. *these fields are read with Java field syntax, not a getter*. In Kotlin, `event.action` compiles identically whether or not `@JvmField` is present, so converting that file turns all 8 of its tests into tautologies without producing a single compile error or test failure. The same erosion applies more diffusely to `@JvmStatic` (every `QueueEvent.added(...)`-style call across the suite) and to two tests that **cannot be expressed in Kotlin at all** (`FeedItemEventTest.java:67` and `MessageEventTest.java:39`, both confirmed compile errors below, not speculation). This is precisely the "test silently stops asserting what it used to assert" failure mode the pipeline exists to prevent, and here it is structural rather than a slip — so it belongs to the planner as a scoping decision, not to the developer as a conversion detail.

### Findings

#### Existing surface

22 Java files, 1,027 LOC, under `event/src/test/java/de/danoeh/antennapod/event/`:

| Package | Files | LOC |
|---|---|---|
| `event` (root, 13) | `DownloadLogEventTest` (21), `EpisodeDownloadEventTest` (75), `EventIdentityEqualityTest` (214), `FeedEventTest` (20), `FeedItemEventTest` (69), `FeedListUpdateEventTest` (60), `FeedUpdateRunningEventTest` (21), `MarkerEventsTest` (28), `MessageEventTest` (42), `PlayerErrorEventTest` (21), `PublicFieldInteropTest` (83), `QueueEventTest` (105), `SyncServiceEventTest` (14) | 773 |
| `event.playback` (6) | `BufferUpdateEventTest` (65), `PlaybackHistoryEventTest` (21), `PlaybackPositionEventTest` (15), `PlaybackServiceEventTest` (20), `SleepTimerUpdatedEventTest` (64), `SpeedChangedEventTest` (14) | 199 |
| `event.settings` (3) | `SkipIntroEndingChangedEventTest` (16), `SpeedPresetChangedEventTest` (20), `VolumeAdaptionChangedEventTest` (19) | 55 |

Every file's `package` declaration matches its directory. All 22 are `public class ...Test` with no superclass, no nested types, no `static` members.

**There are no helper/utility/`Mother`-style classes and no shared constants.** This is the single largest structural difference from `:model` M7 and it is a simplifying one: the five helpers in the suite are all **`private` instance methods inside the one class that uses them** — `EpisodeDownloadEventTest.itemWithDownloadUrl` (`:21-26`), `FeedItemEventTest.itemWithId` (`:20-24`), `QueueEventTest.itemWithId` (`:16-20`, an independent duplicate), `FeedListUpdateEventTest.feedWithId` (`:16-20`), and `EventIdentityEqualityTest.assertReferenceEquality` / `assertDefaultToStringShape` (`:27-36`). **There is zero cross-file coupling in the test source set**, so no file's conversion can break any other file, in either language direction. Conversion order is unconstrained and the milestone does not need to land atomically for compilation reasons. (It may still need to for *review* reasons — see Risk 1.)

**Java-only idioms — scanned exhaustively, results almost entirely negative:**

- `@RunWith`, `@Rule`, `@ClassRule`, `@Before`, `@After`, `@Ignore`, `@Parameters`, `@SuppressWarnings`: **none**. The only annotation in the entire suite is `@Test` (95 occurrences).
- Anonymous inner classes: **none**. Static nested classes: **none**. Reflection: **none**. Try-with-resources: **none**. Serialization round-trips: **none**. Varargs: **none**. Mutable global state touched by tests: **none** (no `@Before`/`@After` to reset it with, and none needed).
- Lambdas: **5 sites** — `MessageEventTest.java:26,36`, `PublicFieldInteropTest.java:60` (`Consumer<Context>` SAM), `EpisodeDownloadEventTest.java:73`, `FeedItemEventTest.java:67` (`assertThrows` `ThrowingRunnable` SAM).
- Static imports: **only `org.junit.Assert.*`** — `assertEquals`, `assertFalse`, `assertNotEquals`, `assertNotNull`, `assertNotSame`, `assertNull`, `assertSame`, `assertThrows`, `assertTrue`. **None of these is a Kotlin hard or soft keyword.** A full grep for keyword-shaped identifiers (`is`, `in`, `object`, `fun`, `val`, `var`, `when`, `as`, `typealias`, `this`, `super`) in call or declaration position across the suite returned **zero hits**, and no test method name collides with any keyword.

#### Java/Kotlin interop boundary

**Inbound to the test source set:** nothing. Repo-wide, no `*.gradle` / `*.yml` / CI step / test filter names any `:event` test class; CI runs whole-module tasks only (`./gradlew test${variant}UnitTest test${base-variant}UnitTest`, `.github/workflows/checks.yml:100`). `config/checkstyle/suppressions.xml` names **no** `:event` test file (its only `:event` entry, `:16`, names the *production* file `SkipIntroEndingChangedEvent.java`, already orphaned by Milestone 8 — see future-work item #5; Milestone 9 adds no new instance of that pattern).

**Outbound from the test source set:** the tests call into `:event`'s 23 production Kotlin classes and, through `implementation project(':model')`, into six `:model` Kotlin types — `FeedItem`, `FeedMedia`, `Feed`, `FeedPreferences.SkipSilence`, `VolumeAdaptionSetting`, `DownloadStatus`, `TimerValue`. All are already Kotlin, so every call site in the suite is *already* a Java→Kotlin interop call today. That is what makes the suite an equivalence oracle, and what the conversion removes.

**Public API surface that must not silently break:** none belonging to the test source set itself — it is unpublished and unconsumed. The API surface at risk is `:event`'s **production** `@JvmField` / `@JvmStatic` contract, whose real consumers are the ~10 downstream modules that still contain Java. Milestone 8's full-surface proof for that contract was `:app:assembleDebug` on both flavors (M8 AC11), **not** this suite. So converting the tests does not remove the contract's only guard — but it does remove the fast, local, per-field one, leaving `:app:assembleDebug` as the sole mechanism. That trade is worth stating explicitly rather than discovering later. Note also that after this milestone `:event` contains **zero Java anywhere** (main and test), so no in-module compilation exercises the `@JvmField`/`@JvmStatic` annotations at all.

**Production Kotlin shapes the tests bind to** (drives the property-vs-function split in Risk 6):

| Production member | Kotlin shape | Kotlin test must write |
|---|---|---|
| `FeedEvent.feedId`, `FeedItemEvent.items`/`unreadStatusChanged`, `MessageEvent.message`/`action`/`actionText`, `QueueEvent.action`/`item`/`items`/`position`, `PlaybackServiceEvent.action`, `FeedUpdateRunningEvent.isFeedUpdateRunning` | `@JvmField val` | `event.feedId` (unchanged syntax) |
| `PlaybackPositionEvent.position`/`duration`, `SyncServiceEvent.messageResId`, `SkipIntroEndingChangedEvent.skipIntro`/`skipEnding`/`feedId`, `SpeedPresetChangedEvent.speed`/`feedId`/`skipSilence`, `VolumeAdaptionChangedEvent.volumeAdaptionSetting`/`feedId`, `SpeedChangedEvent.newSpeed`, `PlayerErrorEvent.message`, `BufferUpdateEvent.progress` | plain `val` | `event.position` — `event.getPosition()` **will not compile** |
| `SleepTimerUpdatedEvent.getMillisTimeLeft()`/`getDisplayTimeLeft()`/`isOver()`/`wasJustEnabled()`/`isCancelled()`, `BufferUpdateEvent.hasStarted()`/`hasEnded()`, `EpisodeDownloadEvent.getUrls()` | `fun` | `event.getMillisTimeLeft()` — `event.millisTimeLeft` **will not compile** |

There are **48 bean-getter call sites** in the suite that fall into rows 2 and 3 and must be individually classified. Every misclassification is a compile error (loud), which is the good case.

#### Current test coverage

`./gradlew --console=plain :event:testDebugUnitTest --rerun` → **BUILD SUCCESSFUL**, 95 tests, 0 failures, 0 errors, 0 skipped. Per-class counts taken from the live JUnit XML (22 files under `event/build/test-results/testDebugUnitTest/`), not from counting annotations:

| File | Tests | Asserts | Covers |
|---|---|---|---|
| `EventIdentityEqualityTest` | 23 | 49 | Two content-identical instances of **all 23** production classes are `assertNotSame`, not `.equals()`, have differing `hashCode()`; the 20 without a hand-written `toString()` emit the JVM default `ClassName@hash` form. M8's no-`data class` proof |
| `PublicFieldInteropTest` | 8 | 19 | All 12 `@JvmField` fields on 6 classes read via **bare Java field syntax**. M8's D2/D10 core equivalence proof |
| `QueueEventTest` | 8 | 38 | All 7 static factories set action/item/items/position; `Action` enum has 9 constants in declared ordinal order |
| `BufferUpdateEventTest` | 7 | 20 | `started`/`ended`/`progressUpdate` sentinels; NaN, `-0.0f` and `+0.0f` edge cases |
| `FeedItemEventTest` | 7 | 8 | field storage, `indexOfItemWithId` incl. null elements/empty list/miss, **null-items constructor NPE** |
| `EpisodeDownloadEventTest` | 6 | 9 | `getUrls` is a live view of the passed map; `indexOfItemWithDownloadUrl` incl. null items, null media, NPE on null downloadUrl |
| `SleepTimerUpdatedEventTest` | 6 | 22 | `justEnabled` negation, `updated` zero-clamp, `cancelled` = `Long.MAX_VALUE`, `Long.MIN_VALUE` negation overflow |
| `FeedListUpdateEventTest` | 5 | 10 | all 3 constructor overloads, `contains` by id not identity, empty list, **int-literal→`long` overload resolution** |
| `MarkerEventsTest` | 4 | 4 | construction of the 4 empty marker classes |
| `MessageEventTest` | 3 | 8 | 1-arg vs 3-arg constructor, action stored by identity and **invoked via `accept(null)`** |
| `DownloadLogEventTest` | 2 | 2 | new instance per `listUpdated()`, exact `toString()` |
| `PlaybackHistoryEventTest` | 2 | 2 | same |
| `FeedEventTest` | 2 | 2 | `feedId` field, exact `toString()` format string |
| `FeedUpdateRunningEventTest` | 2 | 2 | boolean round trip |
| `PlaybackServiceEventTest` | 2 | 2 | both `Action` constants |
| `PlayerErrorEventTest` | 2 | 2 | message stored, null message allowed |
| `PlaybackPositionEventTest` | 1 | 2 | position/duration |
| `SpeedChangedEventTest` | 1 | 1 | speed |
| `SyncServiceEventTest` | 1 | 1 | messageResId |
| `SkipIntroEndingChangedEventTest` | 1 | 3 | three fields |
| `SpeedPresetChangedEventTest` | 1 | 3 | three fields |
| `VolumeAdaptionChangedEventTest` | 1 | 2 | two fields |
| **Total** | **95** | **211** | |

These 95 tests are the entire safety net for this milestone. Because the conversion is test-only there is no independent oracle: **the suite verifies itself**. A green build after conversion proves the Kotlin suite passes; it does not prove the Kotlin suite still asserts what the Java suite asserted. That is the dominant risk here and it is what the Constraints & Risks section is mostly about.

#### Characterization-test gaps

There is **no untested production behavior** in `:event` — Milestone 8 wrote this suite from scratch specifically as the characterization layer, and M8 AC2 verified all 23 production classes are exercised. So, unusually, this milestone needs **no new characterization tests before conversion**: the tests to be written already exist.

The gap is of a different kind and it is real: **behavior that is currently covered only *because the test is Java*, and for which no Kotlin-language equivalent exists.** These are coverage that this milestone would *destroy*, not coverage it inherits missing:

1. **`@JvmField` field-access contract — 12 fields, 6 classes, ~14 assertion sites.** Concentrated in `PublicFieldInteropTest` (all 8 tests) and duplicated in `FeedEventTest:12`, `FeedUpdateRunningEventTest:13,19`, `FeedItemEventTest:30,31`, `MessageEventTest:19-21,28-30,38`, `QueueEventTest` (all 8 tests), `PlaybackServiceEventTest:12,18`. In Kotlin every one of these compiles identically with or without `@JvmField`. **Zero residual coverage, zero compile error, zero test failure.**
2. **`@JvmStatic` companion-factory contract — 8 classes.** Every `DownloadLogEvent.listUpdated()`, `QueueEvent.added(...)`, `BufferUpdateEvent.started()`, `SleepTimerUpdatedEvent.cancelled()`, `PlaybackHistoryEvent.listUpdated()`, `EpisodeDownloadEvent.indexOfItemWithDownloadUrl(...)`, `FeedItemEvent.indexOfItemWithId(...)` call site currently proves the method is a real JVM static. From Kotlin, `QueueEvent.added(...)` resolves through the companion regardless of `@JvmStatic`. **Zero residual coverage.**
3. **`FeedItemEventTest.constructorRejectsNullItemsAfterConversion` (`:67`).** Asserts that `new FeedItemEvent(null, false)` throws NPE via Kotlin's `Intrinsics.checkNotNullParameter` — i.e. that a Java caller passing null still fails fast. `FeedItemEvent.kt:5` declares `items: List<FeedItem?>` (non-null list), so **`FeedItemEvent(null, false)` is not expressible in Kotlin at all.** It can only be kept via an unchecked `null as List<FeedItem?>` cast, which tests a construct no real caller uses.
4. **`MessageEventTest.actionIsStoredByIdentityAndInvokedViaAccept` (`:39`).** See Risk 3 — the `accept(null)` call is a Kotlin compile error, and the only workarounds change what the test does.
5. **`MessageEvent`'s exactly-2-constructors shape** (M8 D15/AC7, no `@JvmOverloads`). `MessageEventTest:18` vs `:27` currently distinguishes the two Java constructors; from Kotlin both resolve the same way regardless of the JVM constructor count.

Nothing here is a reason not to proceed — it is a reason for the planner to choose deliberately between (a) keeping the affected files in Java, (b) converting and accepting a documented, quantified coverage loss, or (c) replacing the lost assertions with reflection/`javap`-based checks that are language-independent. That choice is explicitly not mine to make.

#### Track-specific findings (`kotlin`)

**Null-safety hazards** — the tests are the *callers*, so hazards land where a Java call site passes or receives null across a Kotlin signature. Production signatures verified live:

| Test site | Production signature | Verdict |
|---|---|---|
| `FeedItemEventTest.java:67` `new FeedItemEvent(null, false)` | `FeedItemEvent.kt:5` `items: List<FeedItem?>` — **non-null list** | **Compile error in Kotlin** (Risk 4) |
| `MessageEventTest.java:39` `event.action.accept(null)` | `MessageEvent.kt:8` `action: Consumer<Context>?`; `androidx.core.util.Consumer` is a Kotlin `fun interface` with `accept(value: T)`, `T := Context` non-null | **Compile error in Kotlin** (Risk 3) |
| `EpisodeDownloadEventTest.java:57` `Arrays.asList(null, item)` | `EpisodeDownloadEvent.kt:14` `items: List<FeedItem?>` — nullable elements | Safe; infers `MutableList<FeedItem?>` |
| `EpisodeDownloadEventTest.java:70` `itemWithDownloadUrl(null)` | `FeedMedia.kt:85` `constructor(item: FeedItem?, downloadUrl: String?, size: Long, mimeType: String?)` | Safe; `downloadUrl` is nullable |
| `FeedItemEventTest.java:59-61` `ArrayList<>()` + `add(null)` | `FeedItemEvent.kt:9` `items: List<FeedItem?>` | Safe as `ArrayList<FeedItem?>()` |
| `QueueEventTest.java:34,73` `List<FeedItem> queue = Arrays.asList(...)` | `QueueEvent.kt:31,51` `queue: MutableList<FeedItem>?` — **`MutableList`, invariant** | Safe only if `Arrays.asList` is kept; `listOf(...)` breaks loudly (Risk 7) |
| `FeedItemEventTest.java:28` `Collections.singletonList(...)`, `:36,54` `Collections.emptyList()` | `List<FeedItem?>` | Safe — Kotlin `List<out T>` is covariant |

No platform types, no missing `@Nullable`/`@NonNull` annotations, and no Java null-check idioms in the suite (there is not a single `!= null` or `Objects.requireNonNull` in any test file) — the null hazards are entirely at the interop call sites above.

**Toolchain gates — the boundaries this conversion crosses:**

- **checkstyle: no change.** `common.gradle:151-153` sources the `checkstyle` task from `src/main/java` / `src/free/java` / `src/play/java` only, never `src/test`. `:event`'s tests have never been checkstyle-checked and will not be after conversion. Identical to the `:model` M7 precedent, and confirmed: no `:event` test filename appears in `config/checkstyle/suppressions.xml`.
- **ktlint: starts enforcing.** `event/build.gradle:4` applies the ktlint plugin (12.3.0, engine 1.0.1). `:event:ktlintCheck` today reports `runKtlintCheckOverTestSourceSet NO-SOURCE` / `ktlintTestSourceSetCheck SKIPPED` — verified live, a genuine no-op. It begins enforcing on the first `.kt` in the test source set. CI runs `./gradlew ktlintCheck` repo-wide (`.github/workflows/checks.yml:48`), so this is a real gate, not a local nicety. Note `:event` registers a richer task set than M7 recorded for `:model`: `ktlintTestSourceSetCheck`, `ktlintTestDebugSourceSetCheck`, `ktlintTestReleaseSourceSetCheck`, all aggregated by `ktlintCheck`.
  - ktlint's `FunctionNamingRule` is a non-issue here: M7 established that it selects the permissive `VALID_TEST_FUNCTION_NAME_REGEXP` for any file importing `org.junit` (all 22 do), and independently **`:event` has zero underscore-containing test method names** — all 95 are already clean camelCase. No naming decision is forced in either direction.
- **`-Xlint:all -Werror` stops covering the tests.** `common.gradle:43-48` applies it to every `JavaCompile`, currently including `:event:compileDebugUnitTestJavaWithJavac`. Once all 22 files convert that task goes `NO-SOURCE`. `:event:compileDebugUnitTestKotlin` already exists and reports `NO-SOURCE` today — direct evidence the Kotlin test-compile path is wired by the `kotlin-android` plugin, so **`event/build.gradle` needs no change**. There is **no `allWarningsAsErrors` / `kotlinOptions` / `freeCompilerArgs` anywhere in `common.gradle`**, so Kotlin test warnings will not be errors — this is the recurrence of future-work item #3, and it matters concretely because the Risk 3/4 workarounds both produce warnings (unchecked cast) rather than errors.
- **SpotBugs: no change.** `common.gradle:86` applies SpotBugs, but `:event` registers only `spotbugsDebug` / `spotbugsRelease` — no unit-test variant task exists, so test bytecode is never analyzed. Also `ignoreFailures = true` with a custom XML-parsing gate.
- **Android Lint: verify, low risk.** `common.gradle:57-63` sets `warningsAsErrors true`, `abortOnError true`, `checkDependencies true`, `checkGeneratedSources = true`. `:event` does register `lintAnalyzeDebugUnitTest` / `generateDebugUnitTestLintModel`, but `lint.checkTestSources` is **not** set and defaults to false, so unit-test findings should not be reported. CI runs `./gradlew checkstyle lint` (`.github/workflows/checks.yml:46`), so this is worth confirming empirically once the first `.kt` lands rather than assuming.

**EventBus (`@Subscribe`) wiring: none, and none needed.** Despite `event/README.md:3-4` describing the module's purpose in EventBus terms, `:event` does **not depend on GreenRobot EventBus at all** — `event/build.gradle:12-19` declares only `:model`, `androidx.core`, `androidx.annotation`, and `junit`. A repo-wide grep for `@Subscribe` / `EventBus` / `greenrobot` under `event/` hits only `README.md`. The event classes are plain value objects; subscription happens in consuming modules. **There is no `@Subscribe` test wiring to preserve.** The one EventBus-derived constraint that *does* bind is behavioral, not structural, and is already pinned by `EventIdentityEqualityTest`: per `event/README.md`, `EventBus.removeStickyEvent(Object)` compares by `equals()`, so no `:event` class may gain value equality. That test must survive conversion with its `.equals()` calls intact — see Risk 8.

### Constraints & Risks

**1. (CRITICAL, silent) The suite's oracle value is language-dependent, and converting it destroys a documented, quantified portion of that value with no failing signal.** Fully described under *Characterization-test gaps* above; restated here because it is the milestone's defining risk and it has no mechanical mitigation. `PublicFieldInteropTest`'s 8 tests were written by Milestone 8 *for the express purpose* of proving Java field syntax works (`tasks/antennapod-event-kotlin.md:330,383,623`), and after conversion they assert nothing that could fail. Unlike `:model` M7 — where the tests were language-agnostic behavioral assertions and Kotlin was purely cosmetic — a meaningful slice of `:event`'s suite exists *because* it is Java. A green post-conversion run is not evidence against this; it is the expected symptom of it. **This is a scoping decision for the planner, not a conversion detail for the developer.**

**2. (HIGH, silent, and the one to design the plan around) `EventIdentityEqualityTest.assertDefaultToStringShape` uses `Integer.toHexString`, which has no safe idiomatic Kotlin substitute.** `EventIdentityEqualityTest.java:34`:
```java
String expected = obj.getClass().getName() + "@" + Integer.toHexString(obj.hashCode());
```
`Integer.toHexString(-1)` is `"ffffffff"`; Kotlin's `(-1).toString(16)` is `"-1"`. Object identity hash codes are frequently negative, so a developer or J2K pass that "idiomizes" this to `hashCode().toString(16)` produces a test that fails **intermittently and only on negative hash codes** — the worst possible failure signature, and one a single green CI run will not catch. It must stay `Integer.toHexString(...)` (i.e. `java.lang.Integer.toHexString`). This single line backs the default-`toString()` half of all 23 tests in the file.

**3. (HIGH, loud) `MessageEventTest.java:39` `event.action.accept(null)` will not compile in Kotlin — verified, not speculated.** I extracted `androidx/core/util/Consumer.class` from `core-1.16.0.aar` and decoded its `kotlin.Metadata`: it is **compiled from `Consumer.kt`**, and the class flags (value 16486) decode to PUBLIC / ABSTRACT / INTERFACE / **isFun=1** — a Kotlin `fun interface Consumer<T> { fun accept(value: T) }`. `MessageEvent.kt:8` declares `action: Consumer<Context>?`, so `T := Context` (non-null) and `accept(null)` is rejected. Two consequences:
- The `fun interface` finding also means SAM conversion **does** work from Kotlin, so `Consumer<Context> action = ignored -> { };` (`MessageEventTest.java:26`, `PublicFieldInteropTest.java:60`) converts cleanly to `Consumer<Context> { }` — no anonymous-object fallback needed.
- But the `accept(null)` line has no clean fix. Candidates, all with costs: an unchecked `(event.action as Consumer<Context?>).accept(null)` cast (compiles with a warning — and per the gate analysis above, warnings are not errors on the Kotlin test path, so it *will* build); passing a non-null `Context`, which requires Mockito or Robolectric and violates `event/README.md`'s explicit no-Robolectric/no-Mockito constraint; changing `MessageEvent.kt`'s type parameter, which is a **production** edit this milestone forbids; or keeping the file in Java. Needs an explicit decision.

**4. (HIGH, loud) `FeedItemEventTest.java:67` `assertThrows(NullPointerException.class, () -> new FeedItemEvent(null, false))` will not compile in Kotlin.** `FeedItemEvent.kt:5` takes `items: List<FeedItem?>`, non-null. The test's own name — `constructorRejectsNullItemsAfterConversion` — states that it exists to characterize the Kotlin null-check intrinsic as seen by a **Java** caller. Expressible in Kotlin only as `FeedItemEvent(null as List<FeedItem?>, false)` (unchecked cast, warning-only), which does still trigger `Intrinsics.checkNotNullParameter` at runtime and so does still pass — but it now tests a call shape no production caller can produce. Preserved-in-letter, hollowed-in-spirit. Planner call.

**5. (HIGH, silent if "fixed") `FeedListUpdateEventTest.java:57` — the untyped integer literal *is* the test.**
```java
FeedListUpdateEvent event = new FeedListUpdateEvent(0);   // test: intLiteralResolvesToLongFeedIdConstructor
```
`FeedListUpdateEvent.kt` declares three constructors — `(feeds: List<Feed>)` (`:5`), `(feed: Feed)` (`:15`), `(feedId: Long)` (`:17`). In Java, `0` widens `int`→`long` and selects the third. **Writing `0L` "to fix a Kotlin error" silently deletes the entire point of the test.** Kotlin's integer-literal type inference should resolve `FeedListUpdateEvent(0)` to the `Long` constructor unaided (an unsuffixed literal takes an integer-literal type over `{Int, Long, Short, Byte}`, and only the `Long` candidate is applicable) — but this is load-bearing enough that it must be **empirically confirmed at conversion time**, and if it does not compile, the correct response is to record that the Java-widening behavior has no Kotlin analogue, not to paper over it with a suffix. Related and adjacent: `FeedListUpdateEventTest.java:51`'s explicit `Collections.<Feed>emptyList()` witness exists to select the `List` overload among the same three; `emptyList()` should infer correctly in Kotlin, but it is the second overload-disambiguation site in one 60-line file.

**6. (MEDIUM, loud) The `val`-vs-`fun` split across 23 production classes touches 48 getter call sites.** Milestone 8 converted some accessors to Kotlin properties and left others as functions (see the table under *Java/Kotlin interop boundary*). From Java, both are `getX()` and the distinction is invisible; from Kotlin they are mutually exclusive syntaxes. The highest-density files are `SleepTimerUpdatedEventTest` (18 sites, all `fun`) and `BufferUpdateEventTest` (20 sites, mixed: `progress` is a `val`, `hasStarted()`/`hasEnded()` are `fun`s). Every error here is a compile error, so the risk is diff volume and review noise, not correctness.

**7. (MEDIUM, loud) `QueueEvent.setQueue`/`sorted` take `MutableList<FeedItem>?`, so `Arrays.asList` must not become `listOf`.** `QueueEvent.kt:31,51`. Kotlin's `MutableList` is invariant and `listOf(...)` returns a read-only `List`, so the J2K-idiomatic rewrite of `QueueEventTest.java:34,73` breaks loudly. Keeping `Arrays.asList(...)` (which Kotlin sees as `(Mutable)List<T>!`) is both the minimal-diff and the compiling choice. The neighbouring `assertSame(queue, event.items)` assertions (`:38,77`) are the live-reference-not-copy proof that `event/README.md` calls out as never-to-be-broken, so this file deserves care beyond its size.

**8. (MEDIUM, silent) The two explicit `.equals()` calls in `EventIdentityEqualityTest` must survive verbatim.** `:29` `assertFalse(a.equals(b))` and `:35` `assertTrue(obj.toString().equals(expected))`. J2K and every IDE inspection will offer to rewrite these as `==`. In Kotlin `a == b` compiles to `Intrinsics.areEqual`, which is null-safe — behaviorally identical for these non-null receivers, so the rewrite is *inert in effect* — but the **point of `:29` is to assert that no class in `:event` overrides `equals`**, and expressing that check through Kotlin's structural-equality operator obscures exactly what is being proven. `:model` M7 reached the same conclusion and made "preserve `.equals()`, never simplify to `==`" an explicit plan policy (M7 red-team loop 2); the same policy should carry here.

**9. (MEDIUM, inert — but the only numeric-resolution shift in the suite, so it will be asked about) `assertNotEquals(a.hashCode(), b.hashCode())` changes overload.** `EventIdentityEqualityTest.java:30`. In Java, `int, int` widens to `Assert.assertNotEquals(long, long)`. Kotlin performs no implicit widening, so `Int, Int` boxes and selects `assertNotEquals(Object, Object)`; `Integer.equals(Integer)` gives the identical verdict. **Inert, but verify rather than assume**, because it is the exact mechanism behind `:model` M7's three genuine `assertEquals` failures.

**And the notable negative: `:model` M7's int→Long `assertEquals` trap does NOT recur — zero sites.** I checked every integer-literal assertion in the suite against its production type. Every `assertEquals` whose actual is a `Long` already carries an `L` suffix (`FeedEventTest:12`, `PublicFieldInteropTest:37`, `SkipIntroEndingChangedEventTest:14`, `SpeedPresetChangedEventTest:17`, `VolumeAdaptionChangedEventTest:17`, all 8 in `SleepTimerUpdatedEventTest`), and every unsuffixed integer literal compares against a genuine `Int` (`size()`/`.length`/`ordinal()`/`indexOf*` returns/`position`/`duration`/`skipIntro`/`skipEnding`/`messageResId`). The `:model` trap does not appear here because Milestone 8 wrote these tests against already-Kotlin production code.

**10. (LOW-MEDIUM) Float assertions with deliberate signed-zero and NaN edge cases.** `BufferUpdateEventTest` has 20 assertions including a `-0.0f` / `+0.0f` pair (`:52,60`) and `Float.NaN` (`:44-45`) that exist to pin `BufferUpdateEvent.hasStarted()`'s `progress == PROGRESS_STARTED` comparison semantics (`BufferUpdateEvent.kt:6`). The 3-arg `assertEquals(float, float, float)` overload exists in JUnit 4.13 and Kotlin resolves `Float, Float, Float` to it exactly (no implicit Float→Double widening), so this is safe — but the `-0.0f` literal must be preserved character-for-character and `Float.isNaN(...)` must not be quietly restructured. This is the same class of hazard as Risk 2: correct today, silently wrong if idiomized.

**11. (LOW) `assertThrows` trailing-lambda conversion, 2 sites.** `EpisodeDownloadEventTest.java:72-73` (lambda body returns `Int`) and `FeedItemEventTest.java:67`. `ThrowingRunnable` is a SAM whose `run()` returns void; Kotlin's coercion-to-`Unit` handles a value-returning last expression, so `assertThrows(NullPointerException::class.java) { ... }` is clean. Flagged only because M7 raised it.

**12. (LOW, positive) Zero sequencing constraints.** With no shared helper classes and no cross-file references anywhere in the test source set (see *Existing surface*), any file can convert in any order, and every intermediate state compiles in both directions. Whatever batching the plan chooses can be driven purely by review ergonomics and hazard-clustering, not by the compiler. This is the one dimension on which Milestone 9 is strictly easier than Milestone 7.

**13. (LOW) `MessageEventTest` and `PublicFieldInteropTest` import `android.content.Context`, with no Robolectric.** `Context` is used only as a generic type argument and as the `null` argument at `MessageEventTest.java:39`; no Android framework method is ever invoked, which is why the bare-JVM suite passes today. Preserving that property is what makes Risk 3's "pass a real Context" workaround unattractive — it would be the first Android-framework dependency in the module's tests and would contradict `event/README.md`'s stated constraint.

### Unknowns

1. **What happens to `PublicFieldInteropTest` (and the wider `@JvmField`/`@JvmStatic` coverage)?** The three options — keep in Java, convert and document the loss, or replace with language-independent reflection/`javap` assertions — have materially different scopes and different stories for the case study. This is the milestone's central open question and it needs an explicit, written decision, not a default. (Risk 1.)
2. **Is "100% Kotlin test set" actually the goal, or is "100% Kotlin production + a deliberately-Java interop test file or two" the better end state for `:event`?** Milestone 8's D10 made "Java tests are the equivalence oracle" a principled position, not an accident of sequencing. Converting all 22 files is the `:model` M7 precedent; keeping 1–3 files in Java is arguably more faithful to D10's own logic. The planner should decide which principle wins and say why.
3. **How is `MessageEventTest.java:39`'s `accept(null)` resolved?** Unchecked cast, drop the null-invocation assertion, keep the file in Java, or something else. All four are visible decisions with different costs; none should be improvised by the developer. (Risk 3.)
4. **How is `FeedItemEventTest.java:67`'s `new FeedItemEvent(null, false)` resolved?** Same shape of question. Note the `null as List<FeedItem?>` cast does still exercise the runtime intrinsic, so "convert with a cast + a comment explaining the reduced fidelity" is a defensible answer — it just needs to be the chosen one rather than the accidental one. (Risk 4.)
5. **Does `FeedListUpdateEvent(0)` compile and resolve to the `Long` constructor in Kotlin?** High-confidence yes on Kotlin's integer-literal typing rules, but unverified in this environment (no `kotlinc` available locally) and load-bearing enough to warrant a first-step empirical check. If it does not, the plan needs a rule for what replaces the test — and "add an `L`" must be explicitly forbidden. (Risk 5.)
6. **What is the assertion-equivalence verification mechanism?** M7's answer was the D18 canonical assertion extractor plus a per-file 1:1 content diff against the merge base, with an AC requiring an empty residual. `:event` is smaller (211 assertions across 22 files vs 563 across 29) so the same approach is cheaper here — but the Java↔Kotlin canonicalization rules differ (no `new` to strip in most places, but a much larger bean-accessor↔property surface: 48 sites, per Risk 6). Also note the merge base is recoverable and clean: `HEAD` is `46f8b3a58` and all 22 test files were introduced by the single commit `076d1ce97`.
7. **Does `event/README.md` get updated?** Its closing line — "Tests in this module are plain JUnit — no Robolectric, no Mockito" — stays true. But if Risk 1's decision leaves any file in Java, or if the `@JvmField`/`@JvmStatic` coverage story changes, the README's conventions section (which currently explains *why* those annotations exist) is the natural place to record what now guards them.
8. **Is `lint.checkTestSources` genuinely off?** It is not set in `common.gradle` and AGP's default is false, but `:event` does register `lintAnalyzeDebugUnitTest`, and `common.gradle:57-63` sets `warningsAsErrors true` + `abortOnError true`. Cheap to confirm with `./gradlew checkstyle lint` once the first `.kt` file lands; expensive to discover in CI.

### Track prerequisites

- **`kotlin`** — no prerequisites; **met**. `:event`'s production code is already 100% Kotlin (23/23 `.kt` under `event/src/main`, zero `.java`), satisfying the standing test-migration-sequencing gate ("convert tests only after the whole module's production code is migrated"). The Kotlin test-compile path is already wired — `:event:compileDebugUnitTestKotlin` exists and reports `NO-SOURCE` — so **`event/build.gradle` requires no change**, and no new test dependency is needed (the suite uses JUnit 4 only, not `kotlin.test`). No blocking gap.
  - **Non-blocking flag, carried forward for the planner:** the prerequisite for *compiling* this milestone is met, but the argument for *whether all 22 files should be converted* is not settled — see Unknowns 1 and 2. That is a scope question, not a prerequisite failure, and it does not gate planning.

### Sources

- File inventory: `find event/src/test -name '*.java'` → **22** (not 20, correcting the pre-research callout); `-name '*.kt'` → 0; `find event/src/main` → 23 `.kt`, 0 `.java`; `ls event/src/` → `main`, `test` only (no `androidTest`)
- LOC: `wc -l` over the 22 test files → 1,027 total (per-file figures in the Existing surface table)
- Test result: `./gradlew --console=plain :event:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, `:event:testDebugUnitTest` executed (1 executed, 52 up-to-date); `event/build/test-results/testDebugUnitTest/*.xml` (22 files) → **tests=95, skipped=0, failures=0, errors=0**; cross-checked against 95 `@Test` annotations
- Assertion counts: `grep -cE '\b(assert[A-Z][A-Za-z]*)\(' ` per file → **211** total (per-file figures in the Current test coverage table)
- Annotations: `grep -rhoE '@[A-Za-z]+' event/src/test/` → `@Test` × 95 and nothing else; explicit greps for `RunWith` / `@Rule` / `@ClassRule` / `@Ignore` / `@Before` / `@After` / `Parameterized` / `SuppressWarnings` → zero hits
- Static imports: `grep -rh '^import static' event/src/test/ | sort -u` → only `org.junit.Assert.{assertEquals,assertFalse,assertNotEquals,assertNotNull,assertNotSame,assertNull,assertSame,assertThrows,assertTrue}`; no hamcrest, no Mockito
- Kotlin keyword identifiers: regex sweep for `is|in|object|fun|val|var|when|as|typealias|this|super` in call and declaration position across `event/src/test/` → **zero hits**; no underscore-containing test method name (`grep -rnE 'public void [a-z][A-Za-z0-9]*_'` → none)
- No helper/`Mother` classes: all 22 files are `public class *Test`; helpers are private instance methods at `EpisodeDownloadEventTest.java:21-26`, `FeedItemEventTest.java:20-24`, `QueueEventTest.java:16-20`, `FeedListUpdateEventTest.java:16-20`, `EventIdentityEqualityTest.java:27-36`
- Production signatures (nullability + val/fun split): `FeedItemEvent.kt:5,9`; `EpisodeDownloadEvent.kt:6,8,14`; `FeedListUpdateEvent.kt:5,15,17,21`; `MessageEvent.kt:6-11`; `QueueEvent.kt:5-10,26,31,36,41,46,51,56`; `PlaybackPositionEvent.kt:3`; `SleepTimerUpdatedEvent.kt:9,13,17,21,25,30,33,38,45`; `BufferUpdateEvent.kt:3,5,9,14,15,18,23,28`; `SyncServiceEvent.kt:3`; `SkipIntroEndingChangedEvent.kt:3`; `FeedEvent.kt:3,10`; `PlayerErrorEvent.kt:3`; `FeedUpdateRunningEvent.kt:3`; `SpeedPresetChangedEvent.kt:5`; `VolumeAdaptionChangedEvent.kt:5`; `SpeedChangedEvent.kt:3`; `PlaybackServiceEvent.kt:3`; `DownloadLogEvent.kt:3,10-13`
- `:model` types the tests bind to: `FeedMedia.kt:85-87` (`item: FeedItem?, downloadUrl: String?, size: Long, mimeType: String?`), `FeedItem.kt:14,20,47`, `Feed.kt:14,20,198`, `TimerValue.kt` (whole file), `DownloadStatus.kt:3`
- `androidx.core.util.Consumer` is a Kotlin `fun interface`: extracted `androidx/core/util/Consumer.class` from `~/.gradle/caches/.../androidx.core/core/1.16.0/core-1.16.0.aar`; `javap -v` shows `Compiled from "Consumer.kt"` + `kotlin.Metadata`; class-flag varint `0xE6 0x80 0x01` = 16486 → visibility PUBLIC(3), modality ABSTRACT(2), kind INTERFACE(1), **isFun bit 14 set**; `d2` names the parameter `value`; dep version at `gradle/libs.versions.toml:17` (`androidx.core:core:1.16.0`)
- Interop-blocking test sites: `MessageEventTest.java:26,36,39`; `FeedItemEventTest.java:67`; `PublicFieldInteropTest.java:60`; `EpisodeDownloadEventTest.java:57,70,72-73`; `QueueEventTest.java:34,38,73,77`; `FeedListUpdateEventTest.java:51,57`
- Silent-idiomization hazards: `EventIdentityEqualityTest.java:29,34,35` (`.equals()` ×2, `Integer.toHexString`), `:30` (`assertNotEquals` overload shift); `BufferUpdateEventTest.java:44-45,52,60` (NaN, ±0.0f)
- Build config: `event/build.gradle:1-19` (plugins `android.library` + `kotlin.android` + `ktlint`; deps `:model`, `androidx.core`, `androidx.annotation`, `testImplementation libs.junit` — **no Robolectric, no Mockito, no EventBus**); `common.gradle:43-48` (`-Xlint:all -Werror` on all `JavaCompile`), `:57-63` (lint `warningsAsErrors`/`abortOnError`, `checkTestSources` unset), `:86-94` (SpotBugs, `ignoreFailures = true`), `:147-158` (checkstyle sources = `src/main/java` + free/play only, never `src/test`); **no `allWarningsAsErrors` / `kotlinOptions` / `freeCompilerArgs` anywhere in `common.gradle`**
- Gate task inventory: `./gradlew :event:tasks --all` → `ktlintTestSourceSetCheck`, `ktlintTestDebugSourceSetCheck`, `ktlintTestReleaseSourceSetCheck`, `ktlintCheck`, `checkstyle`, `spotbugsDebug`/`spotbugsRelease` (no unit-test SpotBugs variant), `lintAnalyzeDebugUnitTest`
- ktlint is a live no-op today: `./gradlew --console=plain :event:ktlintCheck` → `runKtlintCheckOverTestSourceSet NO-SOURCE`, `ktlintTestSourceSetCheck SKIPPED`, BUILD SUCCESSFUL
- Kotlin test-compile path already wired: `:event:testDebugUnitTest` output → `> Task :event:compileDebugUnitTestKotlin NO-SOURCE`
- No external references to `:event` test class names: `config/checkstyle/suppressions.xml` → only `:16`, naming the **production** file `SkipIntroEndingChangedEvent.java` (orphaned by Milestone 8, not this milestone); CI runs whole-module tasks at `.github/workflows/checks.yml:100`, `ktlintCheck` at `:48`, `checkstyle lint` at `:46`
- No EventBus in `:event`: `grep -rn "Subscribe\|greenrobot\|EventBus" event/` (excluding `build/`) → hits only `event/README.md:3,4,11`
- Module conventions: `event/README.md` (`@JvmField`/`@JvmStatic` rule, no-`data class` / sticky-`removeStickyEvent` rationale, live-mutable-reference rule for `QueueEvent.items`, "plain JUnit — no Robolectric, no Mockito")
- Milestone 8 provenance: `tasks/antennapod-event-kotlin.md:256` (D10, Java tests as equivalence oracle), `:330` (`PublicFieldInteropTest` purpose), `:383` (M8 AC3, `grep -c '\.get[A-Z]'` = 0), `:416,437-438` (OQ2, Milestone 9 scheduling), `:623,634,637` (per-file test-count table), `:736,743` (red-team verification of the `QueueEvent` aliasing and no-`data class` proofs)
- Cross-cutting follow-ups: `tasks/antennapod-model-kotlin-future-work.md:22-30` (item #3, `allWarningsAsErrors` for Kotlin test-compile — recurs on `:event` at this milestone), `:40-48` (item #5, orphaned checkstyle suppressions — **no new instance added by this milestone**, since no `:event` test file is named)
- Precedent: `tasks/antennapod-model-kotlin-milestone-7.md` (M7 Research + Plan D1–D7, D18); `services/android-migration/.claude/skills/kotlin-j2k-style/SKILL.md` (rename-don't-backtick convention)
- Git state: `HEAD` = `46f8b3a581eedf35a7543b748d799d68042f2dd3` ("kotlin/event: convert :event module's 23 classes to Kotlin (#14)"); all 22 test files introduced by `076d1ce97`

---

## Plan
_Last updated by: legacy-android-planner | 2026-07-26 (revised, red-team loop 1)_

> **Revision note (red-team loop 1).** Three findings addressed; **no Decision, Step, File Scope entry, or Acceptance Criterion changed its substance** — the 19/3 split, the four-tier commit structure and AC1–AC18 are unchanged, and red-team independently confirmed those sound.
> 1. **MAJOR — D3 proved too much.** The objection was correct: D3 justified converting 19 files by pointing at external Java call sites for the *same* fields `PublicFieldInteropTest` is kept to prove, which would equally have argued the kept file away. D3 now (a) concedes that its claim was narrower than it read — "not a coverage regression *today*", the guard being contingent on Java callers that AntennaPod's own migration is removing, not "the local signal is worthless"; and (b) replaces redundancy with the actual decisive variable, *what does the file still assert after conversion?*, which is D2's bright line applied consistently. Backed by a new line-by-line finding: `PublicFieldInteropTest`'s behavioral content is **assertion-for-assertion identical** to `FeedUpdateRunningEventTest`, `PlaybackServiceEventTest`, `FeedEventTest` and `QueueEventTest`, so after conversion the file would retain *zero* unique value — the real choice is not "lose a redundant guard" but "gain 8 tautologies that read as coverage."
> 2. **MINOR — D2** now names and rejects the narrower "Java canary *helper* class called from Kotlin" variant (distinct from the already-rejected file-split), on AGENTS.md minimal-diff grounds plus a fidelity argument.
> 3. **MINOR — D4's** `java.lang.*` default-import claim was asserted while D6's comparable claim was hedged. Closed by **verification rather than hedging**: `SortOrder.kt:56` already calls `Integer.toString(...)` unqualified in compiling production Kotlin in this repo — the same shape, same class, no `kotlinc` needed.
>
> OQ3 was reframed as a consequence of fix 1: it previously asked only about the 15 uncovered `@JvmStatic` members, and now covers the `@JvmField` case and the contract-decay scenario that D3's concession exposes.

### Objective

Convert `:event`'s test source set from Java to Kotlin (`kotlin` track, `:event` module, test-only scope), completing the module's migration begun in Milestone 8. **19 of the 22 files convert; 3 stay Java by design** (D2). No production `.kt` file changes, no test behavior changes, no test is added, renamed, split, or removed. The 95-test suite must be green before and after with an identical per-class breakdown, and the assertion *content* of every converted file must diff clean against its Java original.

### Resolved Decisions

**D1 — One milestone, one PR, four risk-tiered conversion commits. Matches M7/M8.** (Research Risk 12, Unknown 4 of the task brief.)

Research established zero cross-file coupling in the test source set, so the compiler imposes no ordering and no atomicity requirement. Batching is therefore chosen purely for **review ergonomics and hazard clustering**, exactly as M8's D1 did with its tiers. Four tiers, in increasing order of silent-failure risk:

| Tier | Files | Tests | Why grouped |
|---|---|---|---|
| A — mechanical | 13 | 22 | No hazard beyond the `val`/`fun` split; also the toolchain proof (first `.kt` in the source set) |
| B — accessor-dense | 3 | 19 | 40 of the 48 bean-getter sites; float edge cases; `assertThrows` lambdas |
| C — resolution/mutability | 2 | 13 | The untyped-`0` overload test and the `MutableList` aliasing proofs |
| D — silent-idiomization | 1 | 23 | `EventIdentityEqualityTest` alone: `Integer.toHexString`, verbatim `.equals()`, the `assertNotEquals` overload shift |

Splitting the two "problem files" into their own milestone is **rejected**: D2 removes the problem rather than deferring it, and a second PR would re-argue the same single decision. The 3 Java files are not converted at all, so there is nothing to sequence around them.

**D2 — Three files stay Java, permanently and by design. The bright line: a test whose oracle is "javac accepts this call shape" cannot be hosted in Kotlin at any price, so it stays Java.** (Research Unknowns 1, 2, 3, 4 — resolved together by one rule. This is the milestone's central decision.)

Kept in Java, **byte-for-byte unchanged** (their diff must be empty — see AC5):

| File | Tests | The Java-only oracle it hosts |
|---|---|---|
| `PublicFieldInteropTest.java` | 8 | All 12 `@JvmField` fields read with bare Java field syntax. In Kotlin `event.action` compiles identically with or without `@JvmField`; the file becomes 8 tautologies with no compile error and no failure (Research Risk 1) |
| `MessageEventTest.java` | 3 | `event.action.accept(null)` (`:39`). `androidx.core.util.Consumer` is a Kotlin `fun interface` with non-null `T`; the call is a Kotlin compile error, and every workaround changes what is tested (Research Risk 3) |
| `FeedItemEventTest.java` | 7 | `new FeedItemEvent(null, false)` (`:67`). `FeedItemEvent.kt:5` takes a non-null `List<FeedItem?>`; the call is inexpressible in Kotlin, and the test's own name says it characterizes the intrinsic **as a Java caller sees it**. It is also the standing record of M8's D7 disclosed narrowing — the one deliberate behavior change M8 made (Research Risk 4) |

**Two distinct failure modes, deliberately covered by one rule.** The three files fail conversion differently, and the difference matters for D3:
- `MessageEventTest` and `FeedItemEventTest` **cannot be converted at all** — the calls are Kotlin compile errors. The failure is loud, and every workaround (unchecked cast, real `Context`, production edit) changes what is tested.
- `PublicFieldInteropTest` **converts cleanly and silently stops asserting anything.** No compile error, no test failure, 8 green tautologies. This is the harder case and the one D3 must reconcile.

Rejected alternatives, and why:
- **Convert and accept the loss.** Rejected outright for `PublicFieldInteropTest`: 8 tests silently stop asserting anything. This pipeline exists to prevent exactly that.
- **A Java canary *helper* class called from Kotlin tests** — e.g. a small new `InteropCanary.java` exposing `static FeedItemEvent constructNull() { return new FeedItemEvent(null, false); }` and bare-field-read accessors, letting the three test files convert while a Kotlin test body calls the canary and asserts on it. This is a genuinely different shape from the rejected file-split (a *new* file, not a relocated test), so it needs its own rejection. Rejected on this repo's own convention: AGENTS.md mandates "keep the diff of your changes to the absolute minimum... no optimization, nothing," and inventing a new Java class that exists solely to launder a compile-shape proof through Kotlin is a larger and stranger diff than leaving three files untouched. It also weakens the proof — a helper's `constructNull()` is one Java call site written to be called, whereas `PublicFieldInteropTest` reads the fields the way a real consumer does. More code, more indirection, less fidelity.
- **Convert and replace with reflection assertions** (`javaClass.getField("action")`, `Modifier.isStatic(...)`). Genuinely equivalent in force, and tempting. Rejected because it trades a direct, zero-abstraction oracle for a compile-unchecked string literal — the same hazard M7's D13 had to fence off around `"hasEmbeddedPictureField"` — and buys nothing except a higher `.kt` file count. A reflection rewrite is *more* code, *more* fragile, and *less* honest than leaving the file alone.
- **Split the files, converting the portable tests and relocating the Java-only ones.** Rejected: 11 of the 18 kept tests are behaviorally portable, so this would maximize Kotlin coverage — but it requires editing, splitting, and renaming characterization tests inside the one milestone whose entire premise is that characterization tests are not edited. The file is the unit of language choice. The cost of the rule is cosmetic; the benefit is that nothing can erode.

**Why "100% Kotlin test set" is the wrong goal here, and this is the better end state** (Research Unknown 2, answered directly). M8's D10 made "Java tests are the equivalence oracle" a principled position, not an accident of sequencing. That principle does not expire when production code finishes converting — it expires when the *contract being guarded* expires, and `@JvmField`/`@JvmStatic` are permanent parts of `:event`'s public API for as long as any Java calls it. `:model` M7 converted 29/29 because every one of its tests was a language-agnostic behavioral assertion; `:event` is not that module. Three files, 18 tests, ~194 lines stay Java and the module is documented as intentionally mixed (D15). `:event`'s **production** code being 100% Kotlin is the substantive achievement and it is already banked.

Consequences, all intended:
- `:event:compileDebugUnitTestJavaWithJavac` keeps running, so `-Xlint:all -Werror` (`common.gradle:43-48`) keeps covering those three files. Future-work item #3's recurrence on `:event` is *reduced* by this decision, not created by it.
- The test source set is mixed Java+Kotlin. Safe: Kotlin test-compile runs before Java test-compile, and Research verified **zero cross-file references** in either direction, so no Java test file needs to see a Kotlin test class or vice versa. AC6 pins that this stays true.

**D3 — The residual `@JvmField`/`@JvmStatic` erosion in the 19 converted files is acceptable, and here is the measurement that says so.** (Research gaps 1, 2, 5.)

Research correctly flagged that the converted files stop proving the interop contract, and correctly declined to judge how much that matters. Measured during planning, repo-wide, excluding `build/` and `event/`:

- **All 17 `@JvmStatic` members have live external Java call sites** — `DownloadLogEvent.listUpdated` 4, `PlaybackHistoryEvent.listUpdated` 3, `FeedItemEvent.indexOfItemWithId` 10, `EpisodeDownloadEvent.indexOfItemWithDownloadUrl` 9, `QueueEvent.{added 3, setQueue 3, removed 2, irreversibleRemoved 2, cleared 4, sorted 2, moved 2}`, `BufferUpdateEvent.{started 5, ended 4, progressUpdate 2}`, `SleepTimerUpdatedEvent.{justEnabled 2, updated 3, cancelled 3}`. Zero of these have a Kotlin caller outside `:event`.
- **All 12 `@JvmField` fields have live external Java read sites** — e.g. `isFeedUpdateRunning` 12, `unreadStatusChanged` 14, `actionText` 8, `MessageEvent.message`/`action` and `QueueEvent.action`/`item`/`items`/`position` across `MainActivity.java:700-713`, `QueueFragment.java:146`, `NavDrawerFragment.java:269-271`, `PreferenceActivity.java:180-182`, `OnlineFeedViewActivity.java:496-499`, `VideoplayerActivity.java:398,417-419`, `AudioPlayerFragment.java:260`, `ExternalPlayerFragment.java:127`.

Every one of those call sites is compiled by `./gradlew :app:assembleDebug`, which CI runs. So the contract's guard is **not** removed by this milestone — what is removed from the 19 converted files is a *faster, module-local, per-member* signal, while `PublicFieldInteropTest.java` (D2) retains that fast signal for the `@JvmField` half and for two `@JvmStatic` members (`QueueEvent.added`, `QueueEvent.cleared`).

**No compensating test is added.** A `StaticFactoryInteropTest.java` was considered and rejected: adding tests during a language-conversion milestone is a scope expansion, and the measurement above shows it would guard nothing that `:app:assembleDebug` does not already guard today. Instead the contract is re-proven **once, mechanically**, by re-running M8's own AC7 `javap` check as AC12 of this milestone — a build-artifact check that is language-independent by construction and strictly stronger than any source-level proxy.

**Reconciling D3 with D2 — why this argument does not also delete `PublicFieldInteropTest`.** (Red-team loop 1, MAJOR. The objection is correct and the original wording of this decision did prove too much.)

The measurement above covers the *same* fields — `isFeedUpdateRunning`, `unreadStatusChanged`, `QueueEvent.added`/`cleared` — that D2 keeps a whole Java file to prove. Read carelessly, D3 says "the local signal is redundant with `:app:assembleDebug`," which would make `PublicFieldInteropTest` redundant too. Two corrections, one of which is a concession:

**First, the concession.** D3's claim is narrower than it read. It establishes that **converting the 19 files is not a coverage regression today** — not that a fast local signal has no value. Those are different propositions and the original text conflated them. `:app:assembleDebug`'s guard is *contingent*: it holds only while external Java callers keep existing, and AntennaPod's own Kotlin migration is actively removing them. So "redundant today" is a statement with an expiry date, and it is not, on its own, a sufficient reason to keep or drop anything.

**Second, the actual decisive variable, which is not redundancy at all.** The right question is not *"is this guard redundant?"* but **"what does the file still assert after conversion?"** — which is D2's bright line, applied consistently:

- The **19 converted files** were never written to prove interop. Their `@JvmField` reads are incidental: `FeedEventTest` reads `event.feedId` because it needs the value to assert on. Conversion costs them an unintended side-effect proof and costs their intended content nothing. They remain fully-valuable behavioral tests, in Kotlin. **Net positive.**
- `PublicFieldInteropTest` was authored *as* that proof (M8 `tasks/antennapod-event-kotlin.md:330`, and M8's AC3 was literally `grep -c '\.get[A-Z]'` returning 0 in this file). Its behavioral content is **not merely duplicated — it is assertion-for-assertion identical to tests that already exist elsewhere and are converting to Kotlin in Step 2.** Verified line-by-line during this revision:

  | `PublicFieldInteropTest` test | Identical assertion already in |
  |---|---|
  | `feedUpdateRunningEventIsFeedUpdateRunningReadAsField` — `assertTrue(event.isFeedUpdateRunning)` on `new FeedUpdateRunningEvent(true)` | `FeedUpdateRunningEventTest.trueIsStoredAndReadBack` — same construction, same assertion |
  | `playbackServiceEventActionReadAsField` — `assertSame(Action.SERVICE_STARTED, event.action)` | `PlaybackServiceEventTest.serviceStartedActionIsStored` — same construction, same assertion |
  | `feedEventFeedIdReadAsField` — `assertEquals(42L, event.feedId)` | `FeedEventTest.feedIdIsStoredAndReadAsField` — same shape, literal `7L` |
  | `queueEventActionItemItemsPositionReadAsFields`, `queueEventClearedHasNullItemAndItemsAndMinusOnePosition` | `QueueEventTest` — all 7 factories × all 4 fields, 38 assertions |
  | `feedItemEventItemsAndUnreadStatusChangedReadAsFields`, both `messageEvent*` tests | `FeedItemEventTest`, `MessageEventTest` — also kept Java, for the separate compile-error reason |

  So after conversion `PublicFieldInteropTest` would retain **zero** unique value: its syntax half becomes tautological and its behavioral half was already redundant with per-class tests that survive in Kotlin. The choice for this file is not "convert and lose a redundant guard" — it is **"convert and gain 8 green tests that assert nothing anyone needs, while looking like coverage."** A dead test that reads as live is worse than no test.

**So the two criteria are not in tension; they are the same criterion.** Keep a file in Java when conversion destroys what the file is *for*. Convert a file when conversion costs it only a side effect. `PublicFieldInteropTest` is kept not because `:app:assembleDebug` is insufficient, but because it is the module's **one canonical, executable statement of a convention that `event/README.md` can otherwise only assert in prose** (D15) — the thing that gives a future contributor "finishing the migration" a compile error instead of a silent green run. The 19 files' individual, incidental re-proofs of that same convention are the part that is genuinely safe to drop.

**Disclosed residual, stated plainly rather than discovered later:** the 15 `@JvmStatic` members without a `:event`-local Java call site are guarded only by the contingent repo-wide build. When the last external Java caller of a member goes, that member's annotation becomes silently removable. `PublicFieldInteropTest.java` is the guard that survives that future for the `@JvmField` half and for two static factories; nothing survives it for the other 15. That asymmetry is real, is not resolved by this milestone, and is logged as Open Question 3.

**D4 — `Integer.toHexString(...)` is preserved verbatim. `toString(16)`, `String.format("%08x", ...)` and every other "idiomatic" substitute are forbidden.** (Research Risk 2, Unknown 3 of the task brief — the highest-value single line in this milestone.)

`EventIdentityEqualityTest.java:34` computes the expected default-`toString()` as `getName() + "@" + Integer.toHexString(obj.hashCode())`. `Integer.toHexString(-1)` is `"ffffffff"`; `(-1).toString(16)` is `"-1"`. Identity hash codes are frequently negative, so the naive idiomization yields a test that fails only sometimes, only on negative hashes, in a file backing 23 tests. A green CI run is not evidence against it.

The resolution is the *minimal-diff* one and it is also the correct one: **Kotlin's default imports include `java.lang.*`, so `Integer.toHexString(obj.hashCode())` compiles in Kotlin unchanged, with no import and no wrapper.** The line is transcribed character-for-character. The only permitted variant is the fully-qualified `java.lang.Integer.toHexString(...)`, and only if the unqualified form unexpectedly fails to resolve.

**Verified in this repo, not asserted from the language spec** (red-team loop 1, MINOR — D6 hedged a comparable claim and this one did not, so the inconsistency is closed by verification rather than by adding a hedge). `model/src/main/java/de/danoeh/antennapod/model/feed/SortOrder.kt:56` already calls **`Integer.toString(sortOrder.code)`** — unqualified, no `import java.lang.Integer`, in production Kotlin that compiles in this repo today. That is the same `Integer.<staticMethod>(...)` shape as `Integer.toHexString`, from the same class, resolved the same way. `System.currentTimeMillis()` is likewise used unqualified in five already-converted `:model` Kotlin test files (`FeedMediaTest.kt:46,63,80`, `FeedItemFilterTest.kt:184`, `FeedMother.kt:11`). The claim is therefore backed by compiling in-repo precedent, which is stronger than the documentation citation red-team suggested and does not require `kotlinc`. No hard-stop fallback is warranted: unlike D6's overload-resolution question, there is no plausible failure mode here, and any failure would be a loud compile error at Step 5 with the fully-qualified form as an immediate fix.

No helper is introduced. A `(hashCode.toLong() and 0xFFFFFFFFL).toString(16)` equivalent would be *correct* but is a hand-rolled reimplementation of a JDK method inside a characterization test — more code, more to get wrong, and it obscures that the expectation is literally "whatever `Object.toString()` produces."

**Enforcement note:** this line is a **blind spot of the D10 assertion audit** — it is a local declaration, not an assertion call, so the extractor drops it. AC9 covers it with a dedicated grep. This gap is called out here specifically so the reviewer does not assume AC8 subsumes it.

**D5 — `.equals()` is preserved verbatim. Never simplified to `==`.** (Research Risk 8; carries M7's red-team loop 2 policy forward unchanged.)

Two sites, both in `EventIdentityEqualityTest`: `:29` `assertFalse(a.equals(b))` and `:35` `assertTrue(obj.toString().equals(expected))`. J2K and every IDE inspection will offer `==`. For these non-null receivers the rewrite is behaviorally inert — but the **point** of `:29` is to assert that no `:event` class overrides `equals`, and expressing that through Kotlin's structural-equality operator obscures precisely what is being proven. Same principle as D4: do not spend idiom points in the one file that is its own only oracle.

**D6 — `FeedListUpdateEvent(0)` is verified empirically at Step 4. Adding an `L` suffix is forbidden and is a hard stop.** (Research Risk 5, Unknown 5.)

`FeedListUpdateEventTest.java:57`'s untyped `0` **is** the test — it pins that an `int` literal selects the `(long)` constructor among three overloads. `FeedListUpdateEvent.kt` declares `(feeds: List<Feed>)` primary plus `(feed: Feed)` and `(feedId: Long)` secondary. Kotlin's integer-literal type is an approximation over `{Int, Long, Short, Byte}`, and `Long` is the only applicable candidate, so `FeedListUpdateEvent(0)` should resolve to the `Long` constructor unaided. High confidence, **not verified** (no `kotlinc` available at planning time).

- If it compiles: done, zero diff to the expression, and the test keeps its point.
- If it does **not** compile: **stop the step.** Do not write `0L` — that deletes the entire subject of the test while leaving a green suite. Record that Java's int→long widening has no Kotlin analogue, revert `FeedListUpdateEventTest` to Java, add it to D2's kept-Java group, and disclose the addition. That is the only permitted fallback.

Adjacent, same file: `:51`'s explicit `Collections.<Feed>emptyList()` is the witness selecting the `List` overload. It becomes `Collections.emptyList<Feed>()` — the type argument is **retained**, not dropped, and it is not replaced by Kotlin's `emptyList()`.

**D7 — `Arrays.asList(...)` is preserved and its locals get no explicit type annotation. `listOf(...)` is forbidden.** (Research Risk 7.)

`QueueEvent.setQueue`/`sorted` take `MutableList<FeedItem>?` (`QueueEvent.kt:31,51`), which is invariant, so `listOf(...)` breaks loudly. Less obviously, so does writing `val queue: List<FeedItem> = Arrays.asList(...)`: the explicit annotation collapses the platform type `(Mutable)List<FeedItem!>!` to a read-only `List` and the call stops compiling. The rule is therefore **two-sided**: keep `Arrays.asList(...)`, and let the platform type flow — `val queue = Arrays.asList(itemWithId(1L), itemWithId(2L))`.

The neighbouring `assertSame(queue, event.items)` at `QueueEventTest.java:38,77` are the live-reference-not-copy proofs that `event/README.md` names as never-to-be-broken. They convert unchanged. Same rule applies to `EpisodeDownloadEventTest.java:57`'s `Arrays.asList(null, item)` (explicit `Arrays.asList<FeedItem?>(...)` is permitted there if inference needs the hint) and `:71`'s `Arrays.asList(item)`.

**D8 — The `val`-vs-`fun` classification in Research's interop table is binding for all 48 getter sites.** (Research Risk 6.)

Every misclassification is a compile error, so this is diff volume rather than correctness risk — but it is the largest source of review noise in the milestone, so the table is the authority and the developer does not re-derive it per site. Binding summary:
- **Stay `fun`** (Kotlin must call `getX()`): `SleepTimerUpdatedEvent.getMillisTimeLeft()`, `getDisplayTimeLeft()`, `isOver()`, `wasJustEnabled()`, `isCancelled()`; `BufferUpdateEvent.hasStarted()`, `hasEnded()`; `EpisodeDownloadEvent.getUrls()`.
- **Become properties** (Kotlin must write `event.x`; `event.getX()` will not compile): `PlaybackPositionEvent.position`/`duration`, `SyncServiceEvent.messageResId`, `SkipIntroEndingChangedEvent.skipIntro`/`skipEnding`/`feedId`, `SpeedPresetChangedEvent.speed`/`feedId`/`skipSilence`, `VolumeAdaptionChangedEvent.volumeAdaptionSetting`/`feedId`, `SpeedChangedEvent.newSpeed`, `PlayerErrorEvent.message`, `BufferUpdateEvent.progress`.
- **`@JvmField` reads are unchanged syntax** in both languages (`event.feedId`) — which is exactly why D2 exists.
- `:model` setters in the private helpers: `item.setId(id)` → `item.id = id`, `feed.setId(id)` → `feed.id = id` (both `var id: Long` — `FeedItem.kt:20`, `Feed.kt:20`); `item.setMedia(media)` → `item.media = media`.

**D9 — The float edge-case literals are preserved character-for-character.** (Research Risk 10.)

`BufferUpdateEventTest` pins `BufferUpdateEvent.hasStarted()`'s IEEE comparison semantics with deliberate signed-zero and NaN cases: `-1f` (`:14,37`), `Float.NaN` + `Float.isNaN(...)` (`:44-45`), `-0.0f` (`:52-53`), `0.0f` (`:60-61`). The 3-arg `assertEquals(float, float, float)` overload exists in JUnit 4.13 and Kotlin resolves `Float, Float, Float` to it exactly with no implicit widening, so these are safe — provided the literals keep their exact form and `Float.isNaN(...)` is not restructured into `x != x` or a Kotlin `isNaN()` extension. `-0.0f` in particular must not become `0.0f`, `-0f`, or `0f`.

**D10 — Assertion content is verified by a mechanical per-file 1:1 diff against the Java original, for all 19 converted files.** (Research Unknown 6; carries M7's D18 mechanism forward.)

A green suite with an unchanged test count does not prove the assertions still say what they said — two canceling edits produce identical counts. The M7 extractor (`assertlines.pl`, reproduced in Step 1) is reused unchanged: it joins wrapped calls by paren balance, keeps only lines containing `assert*(`, and canonicalizes across the Java/Kotlin gap (strip backticks, `X::class.java`→`X.class`, `.getFoo()`→`.foo`, `.size()`/`.length()`→`.size`/`.length`, trailing-lambda `assertThrows`, `new` stripped, whitespace and paren-adjacent whitespace collapsed, trailing `;` dropped). It is written to the session scratchpad and **is not committed**.

`:event` is the easy case for this mechanism: no Mockito `verify`, no hamcrest, and — per Research's exhaustive check — **zero int→Long widening traps**, so unlike M7 there are no disclosed exception lines. **The required result is an empty residual for all 19 files, with no exceptions.** Any residual line at all stops the step, is recorded verbatim in Implementation Notes with a one-line pure-syntax justification, and is individually accepted or rejected by the reviewer. A residual that alters an expected-value literal, swaps expected/actual order, or changes the assertion function itself is a rejection, not a justification opportunity — revert and redo the file.

**Known blind spot, stated so it is not mistaken for coverage:** the extractor only reads assertion call lines. It does **not** see local declarations, helper bodies, or construction expressions — so it would not catch a `Integer.toHexString` → `toString(16)` change (D4), an `Arrays.asList` → `listOf` change (D7), or an added `0L` (D6). Those three are covered by dedicated greps (AC9) and by compile failure respectively. AC8 and AC9 are complementary; neither subsumes the other.

**D11 — Test method names are preserved byte-for-byte. No backtick sentence names.** All 95 are already clean camelCase with zero underscores (Research), and ktlint selects `VALID_TEST_FUNCTION_NAME_REGEXP` for any file importing `org.junit`, so nothing forces a rename in either direction. Renaming a characterization test breaks the per-class reconciliation and the case-study claim of exact equivalence.

**D12 — `event/build.gradle` is not modified, and is deliberately excluded from File Scope.** Research verified `:event:compileDebugUnitTestKotlin` already exists and reports `NO-SOURCE`, so the Kotlin test-compile path is wired by the already-applied `kotlin.android` plugin. The suite is JUnit 4 only — no `kotlin.test` dependency is needed, and none is added. Excluding the file from File Scope means a build-config change cannot slip in unnoticed. Same posture as M7's D16.

**D13 — ktlint begins gating this source set, and that gate is not to be softened.** `:event:ktlintCheck` is a live no-op today (`runKtlintCheckOverTestSourceSet NO-SOURCE`) and starts enforcing on the first `.kt` in `src/test`. CI runs `./gradlew ktlintCheck` repo-wide. Zero `@Suppress("ktlint:...")` annotations, no `ktlint_disabled_rules`, no `.editorconfig` change, no ktlint exclusion in any build file. Line wraps to satisfy the 120-char rule are made at **argument/comma boundaries only**, with `(` and `)` glued to their adjacent tokens — M7's red-team loop 2 confirmed that any other wrap style produces false-positive residuals in the D10 audit.

**D14 — `assertNotEquals(Int, Int)`'s overload shift is accepted as inert. `.toLong()` is forbidden.** (Research Risk 9.)

`EventIdentityEqualityTest.java:30` currently widens `int, int` to `assertNotEquals(long, long)`. Kotlin does no implicit widening, so `Int, Int` boxes and selects `assertNotEquals(Object, Object)`; `Integer.equals(Integer)` returns the identical verdict. Verified by AC1 being green, not assumed. If the site unexpectedly fails to resolve, the fix is **not** `.toLong()` on either operand and **not** a relaxation to `assertTrue(a != b)` — stop and disclose.

**D15 — `event/README.md` gains the intentionally-mixed-source-set convention; future-work item #3 gains the `:event` instance.** (Research Unknown 7.)

The README's existing claims all stay true — the suite is still plain JUnit, still no Robolectric, still no Mockito. What it must gain, phrased as a long-term-stable module convention with no milestone provenance (AGENTS.md), is *why* three test files are Java: that the `@JvmField`/`@JvmStatic` conventions it already documents are guarded module-locally by a deliberately-Java interop test, and that converting those files to Kotlin would silently void the guarantee. Without that line, the next contributor "finishes the migration" and deletes the proof.

`tasks/antennapod-model-kotlin-future-work.md` item #3 (`allWarningsAsErrors` for Kotlin test-compile) gains `:event` as a new instance — with the nuance that D2 leaves `-Xlint:all -Werror` alive on three files, so the gap is narrower here than on `:model`. Item #5 (orphaned checkstyle suppressions) gains **no** new instance: Research confirmed no `:event` test filename appears in `config/checkstyle/suppressions.xml`. That file is not touched.

### Steps

Each step is one reviewable diff and leaves the build green. `./gradlew --console=plain :event:testDebugUnitTest --rerun` must pass at the end of **every** step — `--rerun` is mandatory; an `UP-TO-DATE` result proves nothing.

**Standing obligation on every conversion step (Steps 2–5), per D10.** A step is not complete when the suite goes green. For each file the step converts, the developer runs the assertion-content diff against that file's Java original at the merge base and pastes the result under that step's heading in Implementation Notes. The expected result is an **empty** diff for every file, with no exceptions. A non-empty residual stops the step; it is not deferred to Step 6.

**Note on the characterization-tests-first rule.** The pipeline's non-negotiable "Step 1 writes characterization tests" rule is **satisfied, not waived**: Research found no characterization-test gap because Milestone 8 wrote this suite from scratch as the characterization layer and M8's AC2 verified all 23 production classes are exercised. The tests that must exist before conversion already exist and are green. Step 1 is therefore baseline *capture* rather than test *authoring* — the same obligation, discharged by the previous milestone.

**Step 1 — Capture the baseline and stand up the audit tool. No files change.**
Paste into Implementation Notes:
- a. **Merge-base SHA** — `git merge-base HEAD develop` (currently `46f8b3a58`, and `develop` is clean at that commit). Every `.java` original is destroyed by its rename, so this SHA is the only route back to the pre-conversion text; without it the audit is unreproducible by the reviewer.
- b. **Per-class test counts** — `./gradlew --console=plain :event:testDebugUnitTest --rerun`, then a `classname → tests/failures/errors/skipped` table for all 22 classes read from `event/build/test-results/testDebugUnitTest/TEST-*.xml`. Expected total: **95 tests, 0 failures, 0 errors, 0 skipped**.
- c. **Per-file assertion counts** — `grep -cE '\b(assert[A-Za-z]*)\(' <file>` for each of the 22 files. Expected total **211** (the AC7 table).

Then write `assertlines.pl` to the session scratchpad — **not** to the repo; it is an audit tool, not a deliverable, and is not in File Scope:

```perl
#!/usr/bin/env perl
use strict; use warnings;
local $/; my $src = <>; $src =~ s/\r//g;
my @out; my $buf = "";
for my $l (split /\n/, $src) {
    $l =~ s/^\s+//; $l =~ s/\s+$//;
    next if $l eq "" && $buf eq "";
    $buf = $buf eq "" ? $l : "$buf $l";
    my $o = ($buf =~ tr/(//); my $c = ($buf =~ tr/)//);
    if ($o <= $c) { push @out, $buf; $buf = ""; }
}
push @out, $buf if $buf ne "";
for my $a (@out) {
    next unless $a =~ /\b(?:assert[A-Za-z]*)\s*\(/;
    $a =~ s/`//g;
    $a =~ s/::class\.java/.class/g;
    $a =~ s/\.get([A-Z])([A-Za-z0-9_]*)\(\)/"." . lc($1) . $2/ge;
    $a =~ s/\.(size|length)\(\)/.$1/g;
    $a =~ s/;\s*$//;
    $a =~ s/\)\s*\{\s*(.+?)\s*\}\s*$/, $1)/;
    $a =~ s/,\s*\(\s*\)\s*->\s*/, /;
    $a =~ s/\bnew\s+(?=[A-Z][A-Za-z0-9_]*\s*\()//g;
    $a =~ s/\s+/ /g;
    $a =~ s/\(\s+/(/g;
    $a =~ s/\s+\)/)/g;
    print "$a\n";
}
```

Validate it before relying on it: for each of the 19 files this milestone converts, its output line count must equal that file's grep count from (c). If any file mismatches, the extractor is mis-calibrated for this codebase and must be fixed before Step 2. Record the per-file totals. The per-file invocation used from Step 2 onward:

```bash
diff <(git show 46f8b3a58:<java-path> | perl "$SCRATCH/assertlines.pl") \
     <(perl "$SCRATCH/assertlines.pl" < <kotlin-path>)
```

**Step 2 — Convert Tier A: the 13 mechanical files (22 tests). Also the toolchain proof.**
`MarkerEventsTest`, `DownloadLogEventTest`, `FeedEventTest`, `FeedUpdateRunningEventTest`, `PlayerErrorEventTest`, `SyncServiceEventTest`, `playback/PlaybackHistoryEventTest`, `playback/PlaybackPositionEventTest`, `playback/PlaybackServiceEventTest`, `playback/SpeedChangedEventTest`, `settings/SkipIntroEndingChangedEventTest`, `settings/SpeedPresetChangedEventTest`, `settings/VolumeAdaptionChangedEventTest` → `.kt`. Apply D8's property/function split and D11's verbatim method names. The exact-string `toString()` assertions in `FeedEventTest`, `DownloadLogEventTest` and `PlaybackHistoryEventTest` convert with their string literals character-for-character.

This tier is deliberately first even though it is trivial: it is the cheapest possible proof that a **mixed Java+Kotlin test source set compiles and runs** before any risky conversion rides on that assumption. Three checks run here and nowhere else:
- `:event:ktlintCheck` — confirm `ktlintTestSourceSetCheck` is now genuinely executing, not `SKIPPED`/`NO-SOURCE` (D13).
- `./gradlew checkstyle lint` — settles Research Unknown 8 (`lint.checkTestSources` is unset and AGP defaults it to false, but `:event` does register `lintAnalyzeDebugUnitTest` and `common.gradle:57-63` sets `warningsAsErrors true` + `abortOnError true`). Cheap here, expensive to discover in CI.
- Confirm `:event:compileDebugUnitTestJavaWithJavac` still executes (not `NO-SOURCE`) — i.e. D2's three Java files are still being compiled with `-Xlint:all -Werror`.

**Step 3 — Convert Tier B: the 3 accessor-dense files (19 tests).**
`playback/SleepTimerUpdatedEventTest` (6 tests, 18 `fun` call sites — every accessor stays `getX()`), `playback/BufferUpdateEventTest` (7 tests — mixed: `progress` is a property, `hasStarted()`/`hasEnded()` are functions; apply D9 to the `-1f` / `Float.NaN` / `-0.0f` / `0.0f` literals and keep `Float.isNaN(...)` intact), `EpisodeDownloadEventTest` (6 tests — apply D7 to the `Arrays.asList(null, item)` site, keep `getUrls()` as a function, and convert the two `assertThrows` trailing lambdas per D10's canonicalization).

**Step 4 — Convert Tier C: the 2 resolution/mutability files (13 tests).**
`FeedListUpdateEventTest` (5 tests — **this is where D6 is empirically settled**: confirm `FeedListUpdateEvent(0)` compiles and resolves to the `Long` constructor, record the outcome in Implementation Notes, and apply D6's hard-stop fallback if it does not; keep `Collections.emptyList<Feed>()`'s explicit type argument). `QueueEventTest` (8 tests — apply D7 in full: `Arrays.asList` retained, no explicit type annotation on the `queue`/`sortedQueue` locals, `assertSame(queue, event.items)` aliasing proofs unchanged, `Action.values().size` and the 9 per-constant ordinals unchanged).

**Step 5 — Convert Tier D: `EventIdentityEqualityTest` alone (23 tests).**
The whole file is one decision surface. Apply D4 (`Integer.toHexString` verbatim — the single most important line in this milestone), D5 (both `.equals()` sites verbatim, never `==`), D14 (`assertNotEquals(a.hashCode(), b.hashCode())` unchanged, no `.toLong()`). The two private helpers `assertReferenceEquality` and `assertDefaultToStringShape` keep their names, signatures (`Any`/`Any?` per compile), and bodies. Paste the converted `assertDefaultToStringShape` body verbatim into Implementation Notes — D10's audit does not read it (D4's blind-spot note), so the reviewer reads it directly.

**Step 6 — Whole-suite reconciliation and the full gate set.**
Run `./gradlew --console=plain :event:testDebugUnitTest --rerun`, `./gradlew :event:ktlintCheck`, `./gradlew checkstyle lint`, `./gradlew :app:assembleDebug`. Then reconcile against Step 1's baseline and paste all of it:
- a. Re-extract the 22-row per-class test table and diff against baseline (b). Every row must be identical — not merely the total.
- b. Re-extract the per-file assertion-count table and diff against baseline (c).
- c. **Re-run the D10 assertion-content diff across all 19 converted files in one sweep** and paste a consolidated `file → residual line count` table, every row `0`. This is a whole-suite re-derivation, not a restatement of the per-step results — it catches a file that a later step touched incidentally after its own audit passed.
- d. **Re-prove the interop contract mechanically (D3):** run `javap -p` on the built `:event` debug classes and paste output showing `FeedUpdateRunningEvent` has `public final boolean isFeedUpdateRunning;` with no accessor; `FeedItemEvent` has `public final java.util.List items;` and `public final boolean unreadStatusChanged;` with no accessors; `MessageEvent` has 3 public final fields and exactly 2 constructors; `QueueEvent` has 4 public final fields and 7 `public static QueueEvent` factories; and `BufferUpdateEvent`/`SleepTimerUpdatedEvent` expose their 3 + 3 factories as `public static`. This is M8's AC7 re-run, and it is the milestone's language-independent proof that nothing about the Java-visible surface moved.

**Step 7 — Documentation.**
Update `event/README.md` per D15 (the intentionally-mixed source set and why the three files are Java, as a long-term-stable convention with no milestone provenance). Append the `:event` instance to `tasks/antennapod-model-kotlin-future-work.md` item #3, noting that D2 leaves `-Xlint:all -Werror` alive on three files so the gap is narrower than on `:model`. Do **not** touch item #5 and do **not** touch `config/checkstyle/suppressions.xml`. Fill in this task file's Implementation Notes and update the checkpoint.

### File Scope

The reviewer rejects any diff touching a file not on this list.

**Renamed `.java` → `.kt`** (`git mv` + rewrite; every file stays in its current directory, no package changes, no new source set) — all under `event/src/test/java/de/danoeh/antennapod/event/`:

- Root package (10): `DownloadLogEventTest`, `EpisodeDownloadEventTest`, `EventIdentityEqualityTest`, `FeedEventTest`, `FeedListUpdateEventTest`, `FeedUpdateRunningEventTest`, `MarkerEventsTest`, `PlayerErrorEventTest`, `QueueEventTest`, `SyncServiceEventTest`
- `playback/` (6): `BufferUpdateEventTest`, `PlaybackHistoryEventTest`, `PlaybackPositionEventTest`, `PlaybackServiceEventTest`, `SleepTimerUpdatedEventTest`, `SpeedChangedEventTest`
- `settings/` (3): `SkipIntroEndingChangedEventTest`, `SpeedPresetChangedEventTest`, `VolumeAdaptionChangedEventTest`

**Total: 19 files renamed.**

**Deliberately NOT converted, and must show an empty diff** (D2) — the reviewer rejects any change to these, including whitespace:
- `event/src/test/java/de/danoeh/antennapod/event/PublicFieldInteropTest.java`
- `event/src/test/java/de/danoeh/antennapod/event/MessageEventTest.java`
- `event/src/test/java/de/danoeh/antennapod/event/FeedItemEventTest.java`

**Modified:**
- `event/README.md` (Step 7 only)
- `tasks/antennapod-model-kotlin-future-work.md` (append to deferred item #3 only)
- `tasks/antennapod-event-kotlin-milestone-9.md`
- `features/antennapod-event-kotlin-milestone-9.checkpoint.md`

**Not in scope — a diff touching any of these means the plan was wrong and the task is re-planned, not patched:** `event/build.gradle` (D12), anything under `event/src/main/` (test-only milestone), `common.gradle`, root `build.gradle`, `settings.gradle`, `gradle/libs.versions.toml`, `.editorconfig`, `config/checkstyle/suppressions.xml` (D15), `config/spotbugs/exclude.xml`, `.github/`, and any file in any other module (`app/`, `model/`, `ui/*/`, `playback/*/`, `storage/*/`, `net/*/`, `parser/*/`, `system/`). The `assertlines.pl` audit script lives in the session scratchpad and is **not** committed.

**Production-code escape valve (D12, narrow):** if a genuine J2K-interop issue forces a minimal production `.kt` fix, it **stops the step** and is disclosed under the module's established deviation-disclosure pattern (M4/M6/M8). It is never absorbed silently, and File Scope is not expanded to accommodate it without a re-plan.

### Acceptance Criteria

Track: `kotlin` (test source set), `:event` module. Every item is checked against the Step 1 baseline in Implementation Notes.

**Characterization tests pass BEFORE the conversion**
- [ ] **AC1** — Step 1's `./gradlew --console=plain :event:testDebugUnitTest --rerun` against the unconverted Java sources is BUILD SUCCESSFUL with **95 tests, 0 failures, 0 errors, 0 skipped**, and the per-class table for all 22 classes is pasted into Implementation Notes. The `--rerun` flag is present in the recorded command.

**Characterization tests pass AFTER the conversion — the equivalence proof**
- [ ] **AC2** — `./gradlew --console=plain :event:testDebugUnitTest --rerun` is green at the end of **each** of Steps 2, 3, 4, 5 and again at Step 6, with **95 tests, 0 failures, 0 errors, 0 skipped** every time.
- [ ] **AC3** — The post-conversion per-class test count matches AC1 **row for row, for all 22 classes** — not merely in aggregate: `EventIdentityEqualityTest` 23, `PublicFieldInteropTest` 8, `QueueEventTest` 8, `BufferUpdateEventTest` 7, `FeedItemEventTest` 7, `EpisodeDownloadEventTest` 6, `SleepTimerUpdatedEventTest` 6, `FeedListUpdateEventTest` 5, `MarkerEventsTest` 4, `MessageEventTest` 3, `DownloadLogEventTest` 2, `PlaybackHistoryEventTest` 2, `FeedEventTest` 2, `FeedUpdateRunningEventTest` 2, `PlaybackServiceEventTest` 2, `PlayerErrorEventTest` 2, `PlaybackPositionEventTest` 1, `SpeedChangedEventTest` 1, `SyncServiceEventTest` 1, `SkipIntroEndingChangedEventTest` 1, `SpeedPresetChangedEventTest` 1, `VolumeAdaptionChangedEventTest` 1.
- [ ] **AC4** — **No test is added, removed, renamed, split, merged, or moved between classes.** The Java-vs-Kotlin diff of the test-method-name list is empty across all 95 tests; no backtick-quoted sentence-style name appears anywhere (D11).

**D2 — the deliberately-Java interop group**
- [ ] **AC5** — `git diff 46f8b3a58 -- event/src/test/java/de/danoeh/antennapod/event/PublicFieldInteropTest.java event/src/test/java/de/danoeh/antennapod/event/MessageEventTest.java event/src/test/java/de/danoeh/antennapod/event/FeedItemEventTest.java` is **empty**. These three files are byte-for-byte unchanged, including whitespace.
- [ ] **AC6** — `find event/src/test -name '*.java'` returns **exactly those 3 paths and no others**, and `find event/src/test -name '*.kt' | wc -l` returns **19**. `:event:compileDebugUnitTestJavaWithJavac` executes (is **not** `NO-SOURCE`), confirming `-Xlint:all -Werror` still covers the Java group. No Kotlin test file references any of the 3 Java test classes, and none of the 3 references any Kotlin test class — verified by grep, since a cross-reference would create a compile-order dependency the source set cannot express.

**Assertion-content equivalence**
- [ ] **AC7** — The per-file assertion count (`grep -cE '\b(assert[A-Za-z]*)\(' <file>`) is identical before and after for all 19 converted files: `EventIdentityEqualityTest` 49, `QueueEventTest` 38, `SleepTimerUpdatedEventTest` 22, `BufferUpdateEventTest` 20, `FeedListUpdateEventTest` 10, `EpisodeDownloadEventTest` 9, `MarkerEventsTest` 4, `SkipIntroEndingChangedEventTest` 3, `SpeedPresetChangedEventTest` 3, and 2 each for `DownloadLogEventTest`, `FeedEventTest`, `FeedUpdateRunningEventTest`, `PlaybackHistoryEventTest`, `PlaybackPositionEventTest`, `PlaybackServiceEventTest`, `PlayerErrorEventTest`, `VolumeAdaptionChangedEventTest`, and 1 each for `SpeedChangedEventTest`, `SyncServiceEventTest`. Total across the 19: **176** (211 minus the 35 in the three kept-Java files). Necessary but **not sufficient** on its own — see AC8.
- [ ] **AC8** — The D10 assertion-content diff residual is **empty for all 19 converted files**, with no disclosed exceptions. Evidence in Implementation Notes: Step 1's extractor validation (19/19 line counts matching AC7), the per-step per-file diffs, and Step 6's consolidated 19-row `file → residual count` table reading `0` on every row. **The reviewer re-runs the exact Step 1 command and confirms it reproduces** — this criterion is not satisfied by the developer stating that they checked. Any residual is recorded verbatim with a one-line pure-syntax justification and accepted or rejected individually; a residual that alters an expected-value literal, swaps expected/actual order, or changes the assertion function fails this criterion outright.
- [ ] **AC9** — The three silent-idiomization hazards the AC8 extractor cannot see are pinned by direct inspection:
  - `grep -c 'Integer.toHexString' event/src/test/java/de/danoeh/antennapod/event/EventIdentityEqualityTest.kt` → **1**, and `grep -cE 'toString\(16\)|String\.format|%08x|and 0xFFFFFFFF' ` over the whole test source set → **0** (D4).
  - `grep -c '\.equals(' event/src/test/java/de/danoeh/antennapod/event/EventIdentityEqualityTest.kt` → **2**, at the same two logical sites (D5).
  - `grep -cE 'listOf\(' event/src/test/` → **0**, and `Arrays.asList` still appears in `QueueEventTest.kt` (2 sites) and `EpisodeDownloadEventTest.kt` (D7). `grep -n '\-0\.0f' event/src/test/java/de/danoeh/antennapod/event/playback/BufferUpdateEventTest.kt` → present, alongside `Float.NaN` and `Float.isNaN` (D9).
- [ ] **AC10** — `FeedListUpdateEventTest.kt`'s `intLiteralResolvesToLongFeedIdConstructor` still calls `FeedListUpdateEvent(0)` with an **unsuffixed** literal — `grep -c 'FeedListUpdateEvent(0L)'` → **0** — and `emptyListConstructorContainsNothing` still passes an explicitly-typed `Collections.emptyList<Feed>()` (D6). Step 4's empirical resolution outcome is recorded in Implementation Notes either way.

**No public API break**
- [ ] **AC11** — No public API break visible to Java callers outside `:event`. Nothing in `event/src/main/` is modified — `git diff --stat 46f8b3a58 -- event/src/main/` is empty — and `./gradlew :app:assembleDebug` is green, compiling both `Free` and `Play` flavors and all 10 consuming modules with zero edits outside `event/src/test/` and the four doc files.
- [ ] **AC12** — The `@JvmField`/`@JvmStatic` interop contract is re-proven mechanically: Step 6(d)'s `javap -p` output is pasted into Implementation Notes and shows the 12 public final fields with no synthesized accessors, `MessageEvent` with exactly 2 constructors, and all 17 static factories/helpers still `public static` (D3). This is the language-independent replacement for the source-level coverage the 19 conversions erode.

**Idiomatic Kotlin target achieved, without behavior drift**
- [ ] **AC13** — `grep -rn '!!' event/src/test/` returns **zero** hits. No unjustified `!!`, and in a pure test conversion there is no justified one either.
- [ ] **AC14** — `grep -rn '`' event/src/test/` returns **zero** hits: `:event` has no hamcrest `is`, no Mockito `when`, and no keyword-shaped identifier anywhere (Research verified), so no backtick escape is legitimate in this module. Kotlin null-safety idioms are applied where the platform-type boundary allows, and no local is given an explicit type annotation that would collapse a platform type (D7).
- [ ] **AC15** — `./gradlew :event:ktlintCheck` is BUILD SUCCESSFUL with `ktlintTestSourceSetCheck` genuinely executing (not `SKIPPED`/`NO-SOURCE`), with **zero** `@Suppress("ktlint:...")` annotations added, no `ktlint_disabled_rules`, no `.editorconfig` change, and no ktlint filter or exclusion in any build file. Any line wrap needed to satisfy the 120-char rule is at an argument/comma boundary only, with no expression restructured, extracted to a local, or shortened (D13).
- [ ] **AC16** — `./gradlew checkstyle lint` is BUILD SUCCESSFUL, and Research Unknown 8 is settled in writing: Implementation Notes records whether `lintAnalyzeDebugUnitTest` reported anything against the new `.kt` files, observed at Step 2 rather than assumed.

**Scope and documentation**
- [ ] **AC17** — `git diff --name-only 46f8b3a58` lists only files from the File Scope list. In particular `event/build.gradle` is unchanged (D12), `config/checkstyle/suppressions.xml` is unchanged (D15), no file under `event/src/main/` appears, no file outside `event/` and the four doc paths appears, and `assertlines.pl` was not committed.
- [ ] **AC18** — `event/README.md` records the intentionally-mixed source set and why the three named files are Java, phrased as a long-term-stable module convention with no milestone or task-specific provenance (AGENTS.md). `tasks/antennapod-model-kotlin-future-work.md` item #3 names `:event` with the D15 nuance; item #5 is unchanged.

**Not applicable to this module, asserted rather than assumed.** Accessibility (content descriptions, dynamic type), dark mode / hardcoded colors, RTL, Paparazzi snapshots, instrumented back-stack/deep-link tests, SDUI contract versions, analytics, and HSHD handling are all inapplicable: `:event` has no UI, no resources, no `AndroidManifest.xml`, no navigation, no network calls, no `androidTest` source set, and handles no personal or payment data. Only the `kotlin` track is in flight; no `compose` or `navigation` work is requested or performed.

### Milestone

**Milestone 9 — `:event` module, `kotlin` track (test source set).** Single milestone, single unified PR (code + spec docs together, per the standing instruction and the M7/M8 precedent), four risk-tiered conversion commits mapping to Steps 2–5 plus a baseline commit (Step 1 produces no repo diff, so it folds into Step 2's commit) and a verification/docs commit for Steps 6–7. Follows Milestone 8 (`:event` production code, PR #14, merged) and Milestones 1–7 (`:model`, PRs #1–#13).

On completion the `kotlin` track for `:event` is **closed**: production 23/23 Kotlin, tests 19/22 Kotlin with a documented, deliberate three-file Java interop group. No further `:event` track is in flight.

This is unaffiliated OSS portfolio work, so "milestone" is case-study narrative structure, not invoicing. The case-study angle this milestone earns is the *inverse* of the usual migration pitch: **"we converted 19 of 22 test files and deliberately left 3 in Java — because those three tests prove a property that only a Java compiler can prove, and converting them would have turned 18 passing tests into passing tautologies with no failing signal."** That is a more valuable story than "100% converted," and the `javap` re-proof plus the measured 29 external Java call sites are what make it defensible rather than merely asserted.

### Out of Scope

- **Converting `PublicFieldInteropTest.java`, `MessageEventTest.java`, or `FeedItemEventTest.java`** (D2). Not deferred — decided against. Revisiting requires a new task and a new argument, not a follow-up milestone.
- **Adding a `StaticFactoryInteropTest.java` or any other compensating test** for the 15 `@JvmStatic` members without a `:event`-local Java call site (D3). Measured as already guarded by `:app:assembleDebug`; logged as Open Question 3 for a future repo-wide decision.
- **Any production code change.** No file under `event/src/main/` is modified. The narrow J2K-interop escape valve stops the step and is disclosed; it is never absorbed silently.
- **`event/build.gradle` changes of any kind** (D12), including adding `kotlin.test` or any new test dependency.
- **Adding Robolectric or Mockito to `:event`.** Research confirmed no need. If a `:model` type appears to require an Android shim, escalate rather than add — the standing KMP-portability policy.
- **Repo-wide build-policy changes:** `allWarningsAsErrors` on Kotlin compile tasks (future-work item #3) and cleaning orphaned checkstyle suppressions (item #5). Both stay deferred; this milestone only annotates item #3 with the `:event` instance.
- **Changing assertion libraries or styles.** No `kotlin.test`, no AssertJ, no Truth, no `assertThrows` ↔ `@Test(expected=)` swaps, no `mockito-kotlin`.
- **Deduplicating, restructuring, parameterizing, or "improving" any test.** The three near-duplicate private `itemWithId`/`feedWithId` helpers stay duplicated in their three files. No shared base class, no extracted test fixtures, no new test cases. Every improvement spotted during conversion goes to `tasks/antennapod-model-kotlin-future-work.md`, not into this diff.
- **Tightening test-helper visibility to `internal`** now that most of the suite is Kotlin. Cosmetic-only, and mixing a name-mangling risk into this diff is exactly the wrong trade.
- **Every other track.** No `gradle-kts` (`event/build.gradle` stays Groovy and is untouched), no `di`, no `concurrency`, no `compose`, no `navigation`. Nothing in `:event` is a ViewModel, View, navigation entry, or threading construct.
- **Any architecture work** — no MVVM, no further modularization, no EventBus→Flow replacement, no hardening of the deliberately-aliased `QueueEvent.items` contract.
- **Converting test sources in any other module.** `:storage:database`, `:parser:feed`, etc. are separate, separately-scoped work.

## Open Questions
_Last updated by: legacy-android-planner | 2026-07-26_

None of these block implementation. Steps 1–7 proceed as written regardless of how they are answered.

**OQ1 — Does the "deliberately-Java interop canary" pattern become a documented repo-wide convention for future modules?** D2 establishes it for `:event`, and `event/README.md` will record it as a module convention. But `:storage:database`, `:parser:feed` and the other Java-heavy modules will hit the same fork the moment their production code finishes converting, and the answer will not always be the same — `:model` M7 correctly converted 29/29 because none of its tests had a Java-only oracle. The generalizable rule is *"a test whose oracle is javac stays Java,"* not *"always keep a Java file."* Whether that rule is promoted into the shared `services/android-migration/.claude/` agent definitions is a service-line decision, not a repo decision, and per the standing memory note it must be stripped of all `:event`/Milestone-9 provenance if it is. **For José.**

**OQ2 — Should the `javap` interop proof become an automated CI check rather than a per-milestone manual AC?** AC12 re-runs M8's AC7 by hand. That is fine for a milestone but it is a snapshot, not a regression guard: nothing prevents a future PR from dropping a `@JvmStatic` and having CI pass, provided the Java caller is removed in the same PR. A small Gradle verification task or a `javap`-diffing test would close that permanently. Out of scope here (new tooling, new File Scope, arguably a new module-wide convention), but worth a decision before the next module's migration.

**OQ3 — What guards the interop contract once the external Java callers are gone?** (Reframed after red-team loop 1, which correctly noted this was scoped only to the `@JvmStatic` half when the `@JvmField` case is the more central one.)

D3 measured that all 17 `@JvmStatic` members and all 12 `@JvmField` fields are currently guarded by external Java call sites compiled in `:app:assembleDebug` — and established that this guarantee is **contingent, not structural**. Each consuming module that converts to Kotlin removes Java call sites; the last one to go silently unguards the annotation, with no failing test anywhere.

After this milestone the residual guard is uneven, and the unevenness is a deliberate consequence of D2/D3 rather than an oversight:

| Contract | Module-local guard after M9 |
|---|---|
| 12 `@JvmField` fields | `PublicFieldInteropTest.java` — unconditional, survives the decay |
| `QueueEvent.added`, `QueueEvent.cleared` | `PublicFieldInteropTest.java` — incidental but real |
| The other 15 `@JvmStatic` members | **None.** Contingent repo-wide build only |

The open question is which way to close that gap: extend `PublicFieldInteropTest.java`'s remit to the static factories (cheap, consistent with D2, but grows a file this milestone deliberately froze), or let OQ2's automated `javap` check supersede the source-level approach entirely for both halves. Not actionable today — the contingent guard is intact — but it should be decided before a consuming module's Kotlin migration removes enough call sites to matter, rather than after.

**OQ4 — Upstreaming intent.** Standing, carried unchanged from M7/M8 (`tasks/antennapod-model-kotlin-future-work.md` item #2). Whether any of this work is offered upstream to AntennaPod affects how conservative the interop posture needs to stay long-term — a fully-Kotlin downstream fork could eventually drop `@JvmField` entirely; an upstream contribution cannot. Unanswered across three milestones without ever blocking one. **Commercial/strategic — for José.**

**OQ5 — Kotlin test-compile warnings remain unchecked** (future-work item #3). D2 narrows this on `:event`, since `-Xlint:all -Werror` keeps covering the three Java files, but the 19 Kotlin files compile with warnings-as-warnings. Concretely relevant here only in that it removes the safety net from any unchecked-cast workaround — and this plan authorizes none, so the exposure is theoretical for this milestone. Tracked repo-wide; not acted on here.

---

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-07-26 | Loop 1 of max 2_

### Verdict
CHALLENGE

### Concerns

- **Severity:** MAJOR
  **Class:** Coverage gaps left unaddressed (checklist #4) / Silent behavior changes from mechanical translation (checklist #2, applied to the plan's own risk-acceptance logic)
  **Concern:** D3's justification for accepting the erosion of `@JvmField`/`@JvmStatic` coverage in the **19 converted files** — "every external call site is already compiled by `:app:assembleDebug`" — is measured against the *exact same fields and methods* that `PublicFieldInteropTest.java` is kept in Java to prove (D2), and the plan never reconciles the resulting asymmetry. I independently confirmed D3's factual claims are correct: `isFeedUpdateRunning` has live bare-field-read call sites in `net/sync/service/.../SyncService.java:120`, `EpisodesListFragment.java:448`, `HomeFragment.java:121`, `SubscriptionFragment.java:249`, `CompletedDownloadsFragment.java:304`, `FeedItemlistFragment.java:484-488`, `QueueFragment.java:283` (9 sites, all plain `event.isFeedUpdateRunning`); `unreadStatusChanged` similarly has 11+ live sites across `app/` and `storage/database/DBWriter.java:790`. These are the identical two `@JvmField` fields `PublicFieldInteropTest.feedUpdateRunningEventIsFeedUpdateRunningReadAsField` and `.feedItemEventItemsAndUnreadStatusChangedReadAsFields` exist to prove. D3 even names two `@JvmStatic` members (`QueueEvent.added`, `QueueEvent.cleared`) that `PublicFieldInteropTest` *also* covers, and says the kept file "retains that fast signal" for them — implicitly conceding the overlap without asking why a fast, local, redundant-with-`:app:assembleDebug` signal is disposable for 19 files' worth of these same properties but load-bearing enough to justify keeping an entire Java file for 2 of them. If D3's core claim ("a slow, repo-wide compile is a sufficient guard, so the fast local signal's loss is acceptable") is correct, it applies with equal force to `PublicFieldInteropTest` itself — undermining part of D2's own rationale for the file it exists to defend. If it is *not* sufficient (i.e., the fast local signal is worth preserving because it catches regressions `:app:assembleDebug` structurally cannot — e.g. the "last external Java caller removed" decay scenario OQ3 names), that same argument was never applied to ask whether compensating tests should exist for the 19 converted files' fields too, or at minimum whether `PublicFieldInteropTest`'s own justification survives being told "you're redundant with the slow proof" in D3's very next section. OQ3 gestures at this asymmetry but only for the 15 `@JvmStatic` members *without* a `:event`-local call site — it never asks the parallel, more central question about the `@JvmField` reads, which are literally what `PublicFieldInteropTest` is for.
  **Evidence:** D2 (lines 245–260) vs. D3 (lines 266–277) of `tasks/antennapod-event-kotlin-milestone-9.md`; OQ3 (line 524, scoped only to the 15 uncovered `@JvmStatic` members, not the `@JvmField` case). Live verification: `grep -rn "isFeedUpdateRunning" --include="*.java" .` and `grep -rn "unreadStatusChanged" --include="*.java" .` (excluding `event/src/`) both return multiple real, non-test call sites in `app/`, `net/sync/service/`, and `storage/database/`, confirming D3's factual premise and its overlap with `PublicFieldInteropTest`'s own field list at `event/src/test/java/de/danoeh/antennapod/event/PublicFieldInteropTest.java:23-24,44`.
  **Suggested mitigation:** Add an explicit paragraph to D2 or D3 that names this asymmetry directly and resolves it — e.g., the honest distinction may be that `PublicFieldInteropTest` is kept not because `:app:assembleDebug` is insufficient in general, but because it is the **one canonical, actively-maintained proof-of-pattern** the module keeps on principle (so a future contributor who "finishes the migration" has a compile error to stop them, per D15's README rationale), while the 19 files' individual, redundant per-file re-proofs of the same pattern are the part that's safe to drop. That is a defensible position, but as written the plan states the acceptance criterion for the 19 files (external call sites exist) and the retention criterion for the 3 files (this can't be proven from Kotlin at all) as if they were about different properties, when for `PublicFieldInteropTest` specifically they are about the same properties. One paragraph closing this loop would remove the internal tension without changing any Decision, Step, or Acceptance Criterion.

- **Severity:** MINOR
  **Class:** Milestone/scope creep (checklist #9) / plan hygiene
  **Concern:** D2's "Rejected alternatives" list considers convert-and-accept, convert-to-reflection, and split-the-files-to-relocate-only-the-Java-only-tests — but not the narrower variant of a small Java **helper/canary class** (not a relocated test file) that a Kotlin test method calls into, e.g. a package-private Java class exposing `static FeedItemEvent constructNull() { return new FeedItemEvent(null, false); }`, letting `FeedItemEventTest` convert its other 6 portable tests to Kotlin while a Kotlin test body calls the canary and asserts the NPE. This is a materially different shape from "split the files" (which the plan rejects specifically because relocating an existing characterization test is edit-adjacent) — a canary helper is a *new* file, not a relocated test, so the plan's given rejection reason doesn't automatically cover it. That said, I believe the conclusion still holds: `AGENTS.md`'s "keep the diff of your changes to the absolute minimum... never reorganize... just the bare instructions from the user" rule argues at least as strongly against introducing a new production-adjacent Java file purely to preserve a compile-shape proof, and probably more strongly than the plan's stated rationale does. This is not blocking — the recommendation (keep the 3 files whole) is very likely correct either way — but the plan would be tighter if it named this narrower alternative and rejected it on the AGENTS.md minimal-diff/no-reorganization grounds directly, rather than leaving a reader to wonder whether it was considered at all.
  **Evidence:** D2 "Rejected alternatives" (lines 255–258); `AGENTS.md` "Coding Style" section ("Never fix any warnings outside the code you wrote... keep your changes as focused as possible... no optimization, nothing. Just the bare instructions from the user").
  **Suggested mitigation:** Add one sentence to D2's rejected-alternatives list naming the canary-helper variant and citing the repo's minimal-diff convention as the reason it's rejected alongside file-splitting. No scope or Decision change required.

- **Severity:** MINOR
  **Class:** Silent behavior changes from mechanical translation (checklist #2) / plan hygiene
  **Concern:** D4's claim — "Kotlin's default imports include `java.lang.*`, so `Integer.toHexString(obj.hashCode())` compiles in Kotlin unchanged" — is correct (`java.lang.*` is a documented JVM-target default import), but it is asserted without the same verification rigor the plan applies elsewhere. D6, testing a comparably-confident claim about integer-literal overload resolution, explicitly flags itself as "high confidence, **not verified** (no `kotlinc` available at planning time)" and builds a hard-stop fallback around that uncertainty. D4 gets no equivalent hedge or citation (e.g. to kotlinlang.org's default-imports documentation, or to an existing unqualified-`java.lang`-member usage already compiling somewhere in this repo's Kotlin sources), even though the same "no local `kotlinc`" constraint applies to both claims. I confirmed no `kotlinc` is available in this environment either, so neither claim was empirically compiled by anyone in this pipeline. The claim is very likely correct — this is well-established, unambiguous Kotlin language behavior with essentially no plausible failure mode — but the document's own epistemic bar is inconsistent between two similarly-unverified claims, one of which got a hard-stop safety net and one of which didn't.
  **Evidence:** D4 (line ~279–285) states the default-import claim as fact with no citation or hedge; D6 (line ~293–298, "not verified (no `kotlinc` available at planning time)") applies a materially different standard to a comparably-unverified claim in the same document.
  **Suggested mitigation:** Either cite kotlinlang.org's default-imports reference in D4 (a documentation lookup, not a compile, so it's actually checkable without `kotlinc`) or add a one-line hedge matching D6's phrasing. Given the near-zero actual risk, this does not need a hard-stop fallback like D6's — a citation is sufficient. Non-blocking.

### Verification performed (not just trusted from plan prose)

- Read `event/src/test/java/de/danoeh/antennapod/event/PublicFieldInteropTest.java`, `MessageEventTest.java`, and `FeedItemEventTest.java` directly — confirmed the exact call shapes D2/Research describe (`event.action.accept(null)` at `MessageEventTest.java:39`; `new FeedItemEvent(null, false)` at `FeedItemEventTest.java:67`; bare field reads on `isFeedUpdateRunning`, `unreadStatusChanged`, `MessageEvent.{message,action,actionText}`, `QueueEvent.{action,item,items,position}` across `PublicFieldInteropTest.java`).
- Verified `FeedListUpdateEvent.kt` directly: confirmed exactly the three constructors D6 describes (`(feeds: List<Feed>)`, `(feed: Feed)`, `(feedId: Long)`), supporting D6's high-confidence claim that `FeedListUpdateEvent(0)` has only one numeric-compatible overload and should resolve unambiguously to `Long`.
- Grepped repo-wide (excluding `event/src/`) for external Java call sites of `isFeedUpdateRunning`, `unreadStatusChanged`, `MessageEvent`, and `QueueEvent` — confirmed real, live, non-test call sites exist in `app/`, `net/sync/service/`, `net/download/service/`, `storage/database/`, and `playback/service/`, consistent with D3's counts and forming the evidentiary basis for the MAJOR finding above.
- Confirmed no `kotlinc` binary is available in this environment (`which kotlinc` → not found), matching the plan's own stated constraint for D6 and informing the MINOR finding about D4's inconsistent verification rigor.
- Confirmed `event/README.md`'s current content is exactly as Research/D15 describe (no mention yet of the intentionally-mixed source set), so D15/Step 7's planned addition is a real gap being filled, not already covered.
- Read D1 (risk-tiered commit batching), AC1–AC18, File Scope, and Out of Scope in full: File Scope correctly excludes `event/src/main/` and `event/build.gradle`; the 19-Kotlin/3-Java split is arithmetically consistent everywhere it's restated (Objective, D2, File Scope, AC5–AC7); Open Questions are all explicitly marked non-blocking and correctly separate commercial/strategic items (OQ1, OQ4 — "For José") from technical follow-ups (OQ2, OQ3, OQ5). No concerns found in these areas.
- Checked D6's hard-stop fallback ("revert to Java, add to D2's kept-Java group, disclose") for concreteness: it is actionable — a compile failure on `FeedListUpdateEvent(0)` would point directly at that line, there is no plausible silent-wrong-overload outcome given only one candidate constructor accepts a numeric literal, and the fallback procedure is stated in enough detail (revert, disclose, no `0L`) that a developer cannot improvise past it. No concern.
- Checked D7's `Arrays.asList`/platform-type rule for clarity: correctly two-sided (preserve the call, do not annotate the local), matches Kotlin's actual platform-type behavior, and is stated plainly enough to follow without re-deriving. No concern.
- Checked AC7/AC8's scoping: both explicitly restrict the assertion-count and assertion-content checks to the 19 converted files (176 of 211 total assertions), correctly excluding the 3 kept-Java files, and AC8 requires reviewer-side reproduction rather than developer self-report, mirroring the M7 D18/AC3B precedent that this milestone's D10 explicitly carries forward (including the `new`-keyword-strip and paren-adjacent-whitespace canonicalization fixes M7's red-team loop 2 forced). No concern.

Both MAJOR and MINOR findings above are addressable without re-scoping the milestone's central design (D2's three-file Java retention, the 19/3 split, and the four-tier commit structure are all independently sound and unchanged by this review). Loop 2 should confirm the D2/D3 asymmetry is explicitly reconciled in writing and, ideally, the two MINOR notes are folded in as cheap one-line additions.

---

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-07-26 | Loop 2 of max 2 (FINAL)_

### Verdict
APPROVE

### Disposition

All three loop-1 findings were addressed with substantive fixes, not rhetorical smoothing, and I independently re-derived the load-bearing evidence for each rather than trusting the revision note's summary. The MAJOR finding's fix in particular holds up under line-by-line verification. One residual wording nit remains; it is non-blocking and is noted rather than escalated, consistent with the Milestone 4 accept-and-document precedent for a final loop.

**MAJOR (D3 proving too much) — verified fixed.** I read all four claimed duplicate files (`FeedUpdateRunningEventTest.java`, `PlaybackServiceEventTest.java`, `FeedEventTest.java`, `QueueEventTest.java`) and diffed each of `PublicFieldInteropTest`'s 8 tests against its claimed counterpart by hand, rather than accepting the planner's table:

| `PublicFieldInteropTest` test | Verified against | Result |
|---|---|---|
| `feedUpdateRunningEventIsFeedUpdateRunningReadAsField` | `FeedUpdateRunningEventTest.trueIsStoredAndReadBack` | **Identical** — same construction, same assertion, character-for-character |
| `playbackServiceEventActionReadAsField` | `PlaybackServiceEventTest.serviceStartedActionIsStored` | **Identical** |
| `feedEventFeedIdReadAsField` | `FeedEventTest.feedIdIsStoredAndReadAsField` | Same shape, different literal (`42L` vs `7L`) — plan's table correctly says "same shape, literal `7L`," not "identical"; no overclaim |
| `feedItemEventItemsAndUnreadStatusChangedReadAsFields` | `FeedItemEventTest.itemsAndUnreadStatusChangedAreStored` | Same shape (item constructed via `itemWithId(1L)` vs bare `new FeedItem()`, otherwise identical) — but this duplicate lives in `FeedItemEventTest`, a file that is **also kept Java**, not converting (see note below) |
| `queueEventActionItemItemsPositionReadAsFields` | `QueueEventTest.addedSetsActionItemAndPosition` | Same shape (different position literal `3` vs `5`) |
| `messageEventMessageActionActionTextReadAsFields` | `MessageEventTest.threeArgConstructorStoresAllThreeFields` | **Identical** — but again in a kept-Java file |
| `messageEventOneArgConstructorLeavesActionAndActionTextNull` | `MessageEventTest.oneArgConstructorStoresMessageAndLeavesActionAndActionTextNull` | **Identical** — kept-Java file |
| `queueEventClearedHasNullItemAndItemsAndMinusOnePosition` | `QueueEventTest.clearedSetsActionOnlyWithNullItemAndItemsAndMinusOnePosition` | **Identical** |

All 8 tests have a genuine behavioral duplicate somewhere in the suite. The reconciliation's core claim — that `PublicFieldInteropTest` retains zero unique value after conversion — is correct and now well-evidenced rather than asserted. **One imprecision, non-blocking:** the summary prose says the duplicates are "tests that already exist elsewhere and are converting to Kotlin in Step 2," but 3 of the 8 (`feedItemEventItemsAndUnreadStatusChangedReadAsFields` and both `messageEvent*` tests) are duplicated in `FeedItemEventTest`/`MessageEventTest` — files that are **also staying Java**, per D2, not converting. The plan's own table one line below the summary sentence gets this right ("also kept Java, for the separate compile-error reason"), so the underlying reasoning is transparent and, if anything, understates its own strength for these 3 rows: their bare-Java-field-read proof survives untouched in another kept-Java file regardless of what happens to `PublicFieldInteropTest`, which is a *stronger* redundancy argument than "duplicated in Kotlin," not a weaker one. Cosmetic wording only; does not affect the Decision, and not worth a third loop.

**MINOR (Java-canary variant) — verified fixed.** D2 now names the canary-helper variant explicitly and rejects it on two independent grounds (AGENTS.md minimal-diff convention, plus a fidelity argument that a helper's call site is authored to be called rather than being a real consumer's incidental field read). Both grounds are sound and match this repo's actual house style (`AGENTS.md`'s "keep the diff of your changes to the absolute minimum... no optimization, nothing"). No further action needed.

**MINOR (D4/D6 rigor inconsistency) — verified fixed, and the citation is accurate.** I independently confirmed `model/src/main/java/de/danoeh/antennapod/model/feed/SortOrder.kt:56` contains exactly `return if (sortOrder != null) Integer.toString(sortOrder.code) else null`, with no `import java.lang.Integer` anywhere in the file or the repo (`grep -rn "import java.lang.Integer" --include="*.kt" .` → zero hits), confirming the unqualified `Integer.<staticMethod>` shape genuinely compiles in this repo's production Kotlin today. I also independently confirmed the secondary `System.currentTimeMillis()` citations at `FeedMediaTest.kt:46,63,80`, `FeedItemFilterTest.kt:184`, and `FeedMother.kt:11` — all present, all unqualified, exactly as cited. This is a stronger and more relevant proof than a documentation citation would have been (in-repo, in-toolchain, already green in CI), and it closes the rigor gap correctly.

**OQ3 reframing — verified fixed.** OQ3 now explicitly covers the `@JvmField` case (previously scoped only to the 15 uncovered `@JvmStatic` members) and its table correctly states that `PublicFieldInteropTest.java` is the one guard that "survives the decay" for the 12 fields, leaving only the 15 uncovered static factories genuinely unguarded going forward. Consistent with the D2/D3 reconciliation above.

**Nothing else regressed.** Per the revision note's claim, I spot-checked that Steps, File Scope, and Acceptance Criteria are structurally and substantively unchanged from loop 1 (same section order, same 19/3 file split repeated consistently across Objective, D2, File Scope, and AC5–AC7; AC7/AC8/AC12 scoping unchanged). D1, D5–D7 read identically to loop 1. No new concerns found outside the three addressed findings.

### Verification performed (loop 2, independent of planner's revision claims)

- Read `event/src/test/java/de/danoeh/antennapod/event/FeedUpdateRunningEventTest.java`, `playback/PlaybackServiceEventTest.java`, `FeedEventTest.java`, and `QueueEventTest.java` in full and diffed each against its claimed `PublicFieldInteropTest` counterpart line-by-line (table above) — did not take the planner's "assertion-for-assertion identical" claim at face value.
- Verified `model/src/main/java/de/danoeh/antennapod/model/feed/SortOrder.kt:56` and its import block directly; confirmed zero `import java.lang.Integer` anywhere in the repo's `.kt` sources.
- Verified the five `System.currentTimeMillis()` citations (`FeedMediaTest.kt:46,63,80`, `FeedItemFilterTest.kt:184`, `FeedMother.kt:11`) directly, all present at the cited lines.
- Re-read D2's rejected-alternatives list and confirmed the canary-helper variant is now named and independently rejected (not folded silently into the file-split rejection).
- Re-read OQ3 and confirmed it now names the `@JvmField` case and the decay scenario explicitly, with a table distinguishing what's guarded (12 fields + 2 static factories, via `PublicFieldInteropTest`) from what isn't (15 static factories).
- Confirmed via `git status` that both `tasks/antennapod-event-kotlin-milestone-9.md` and the checkpoint remain untracked/uncommitted — no destructive operations performed, working tree otherwise clean.

### Recommendation

Cleared for `android-migration-developer`. No third loop warranted — the one residual item (the summary sentence's imprecise "converting to Kotlin in Step 2" phrasing for 3 of 8 duplicate rows) is cosmetic, does not change any Decision/Step/File Scope/AC, and is noted here for the record rather than requiring another revision cycle, per this pipeline's 2-loop cap and the Milestone 4 accept-and-document precedent.

---

## Implementation Notes
_By: android-migration-developer | 2026-07-27_

### Module variant discovery

`./gradlew :event:tasks --group verification` confirms `:event` is an unflavored module with only `testDebugUnitTest`/`testReleaseUnitTest` (no product flavors, matching `:model` M7's shape, not `:app`'s Free/Play flavors). All test commands below use `testDebugUnitTest`, matching Research's own command.

### Step 1 — Baseline capture and audit-tool validation

**a. Merge-base SHA:** `git merge-base HEAD develop` → `46f8b3a581eedf35a7543b748d799d68042f2dd3` (matches the SHA recorded in Research/Plan; `develop` was clean at this commit). All Step 2–6 `git show <sha>:<path>` diffs use this SHA.

**b. Per-class test counts** (`./gradlew --console=plain :event:testDebugUnitTest --rerun`, BUILD SUCCESSFUL, then read from `event/build/test-results/testDebugUnitTest/TEST-*.xml`):

| Class | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| DownloadLogEventTest | 2 | 0 | 0 | 0 |
| EpisodeDownloadEventTest | 6 | 0 | 0 | 0 |
| EventIdentityEqualityTest | 23 | 0 | 0 | 0 |
| FeedEventTest | 2 | 0 | 0 | 0 |
| FeedItemEventTest | 7 | 0 | 0 | 0 |
| FeedListUpdateEventTest | 5 | 0 | 0 | 0 |
| FeedUpdateRunningEventTest | 2 | 0 | 0 | 0 |
| MarkerEventsTest | 4 | 0 | 0 | 0 |
| MessageEventTest | 3 | 0 | 0 | 0 |
| PlayerErrorEventTest | 2 | 0 | 0 | 0 |
| PublicFieldInteropTest | 8 | 0 | 0 | 0 |
| QueueEventTest | 8 | 0 | 0 | 0 |
| SyncServiceEventTest | 1 | 0 | 0 | 0 |
| playback.BufferUpdateEventTest | 7 | 0 | 0 | 0 |
| playback.PlaybackHistoryEventTest | 2 | 0 | 0 | 0 |
| playback.PlaybackPositionEventTest | 1 | 0 | 0 | 0 |
| playback.PlaybackServiceEventTest | 2 | 0 | 0 | 0 |
| playback.SleepTimerUpdatedEventTest | 6 | 0 | 0 | 0 |
| playback.SpeedChangedEventTest | 1 | 0 | 0 | 0 |
| settings.SkipIntroEndingChangedEventTest | 1 | 0 | 0 | 0 |
| settings.SpeedPresetChangedEventTest | 1 | 0 | 0 | 0 |
| settings.VolumeAdaptionChangedEventTest | 1 | 0 | 0 | 0 |
| **Total (22 classes)** | **95** | **0** | **0** | **0** |

Matches Research/AC1/AC3's table exactly, row for row.

**c. Per-file assertion-call counts** (`grep -cE '\b(assert[A-Za-z]*)\(' <file>`, all 22 files):

`DownloadLogEventTest`=2, `EpisodeDownloadEventTest`=9, `EventIdentityEqualityTest`=49, `FeedEventTest`=2, `FeedItemEventTest`=8, `FeedListUpdateEventTest`=10, `FeedUpdateRunningEventTest`=2, `MarkerEventsTest`=4, `MessageEventTest`=8, `PlayerErrorEventTest`=2, `PublicFieldInteropTest`=19, `QueueEventTest`=38, `SyncServiceEventTest`=1, `BufferUpdateEventTest`=20, `PlaybackHistoryEventTest`=2, `PlaybackPositionEventTest`=2, `PlaybackServiceEventTest`=2, `SleepTimerUpdatedEventTest`=22, `SpeedChangedEventTest`=1, `SkipIntroEndingChangedEventTest`=3, `SpeedPresetChangedEventTest`=3, `VolumeAdaptionChangedEventTest`=2. Total **211**, matching Research/AC7 exactly.

**Extractor (`assertlines.pl`) — written to the session scratchpad (not committed), reused unchanged from M7's D18/this Plan's D10.** Validated against all 19 files this milestone converts, at the merge-base SHA: per-file extractor line count equals the AC7 grep count for **19/19 files, 176 total assertions**, zero mismatches. Extractor path: `$SCRATCH/assertlines.pl`; invocation per Plan Step 1: `diff <(git show <sha>:<java-path> | perl assertlines.pl) <(perl assertlines.pl < <kotlin-path>)`.

### Step 2 — Convert Tier A: 13 mechanical files (22 tests)

`DownloadLogEventTest`, `FeedEventTest`, `FeedUpdateRunningEventTest`, `MarkerEventsTest`, `PlayerErrorEventTest`, `SyncServiceEventTest`, `playback/PlaybackHistoryEventTest`, `playback/PlaybackPositionEventTest`, `playback/PlaybackServiceEventTest`, `playback/SpeedChangedEventTest`, `settings/SkipIntroEndingChangedEventTest`, `settings/SpeedPresetChangedEventTest`, `settings/VolumeAdaptionChangedEventTest` → `.kt` via `git mv` + rewrite. D8's val/fun split applied: `@JvmField` reads (`event.feedId`, `event.isFeedUpdateRunning`, `event.action`) kept as unchanged field-access syntax; plain-`val` production properties (`PlayerErrorEvent.message`, `SyncServiceEvent.messageResId`, `PlaybackPositionEvent.position`/`.duration`, `SpeedChangedEvent.newSpeed`, all three `settings` events' fields) converted from `getX()` to `.x` property syntax. Exact-string `toString()` assertions (`DownloadLogEventTest`, `FeedEventTest`, `PlaybackHistoryEventTest`) preserved character-for-character.

This tier doubled as the toolchain proof: first `.kt` in `event/src/test`, confirming a mixed Java+Kotlin test source set compiles and runs before any risky conversion depends on that. Three checks specific to this step:
- `./gradlew :event:ktlintCheck` → initially **FAILED**: `ktlintTestSourceSetCheck` (genuinely executing, not `NO-SOURCE`/`SKIPPED` — settling Research's live-no-op observation) flagged "Imports must be ordered in lexicographic order without any empty lines in-between" on all 13 new files. Fixed by collapsing the Java-original's two import blocks (plain imports, then a blank line, then the static imports) into ktlint's required single lexicographically-sorted block with no blank line — matching the precedent already established in `:model`'s converted Kotlin tests (e.g. `ChapterTest.kt`). Re-ran: BUILD SUCCESSFUL. This import-ordering fix was carried forward as the default import style for every subsequent file in this milestone.
- `./gradlew checkstyle lint` → **repo-wide FAILS**, but on three pre-existing failures confirmed unrelated to this milestone: `:app-wearos:compileFreeDebugKotlin`/`compilePlayDebugKotlin` (Kotlin compile errors) and `:app:spotbugsPlayDebug` (SpotBugs null-dereference violations in `MainActivity.java`, `FeedInfoFragment.java`, `OnlineFeedViewActivity.java`, `PreferenceActivity.java`, `QueueFragment.java`). Verified pre-existing by `git stash -u` back to the unmodified merge-base tree and re-running the identical command: same three failures, byte-identical output. Narrowed to `./gradlew :event:lintDebug` instead for the module-scoped signal: BUILD SUCCESSFUL, `lintAnalyzeDebugUnitTest` executed (not skipped) and reported nothing against the new `.kt` files — settling Research Unknown 8 (AC16): `lint.checkTestSources` is effectively off for this module's unit tests, confirmed empirically rather than assumed.
- `:event:compileDebugUnitTestJavaWithJavac` still executes (UP-TO-DATE, not `NO-SOURCE`) — confirms D2's three Java files are still being compiled with `-Xlint:all -Werror`.

`./gradlew --console=plain :event:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 95/0/0/0. D10 assertion-content diff: all 13 files empty.

### Step 3 — Convert Tier B: 3 accessor-dense files (19 tests)

`playback/SleepTimerUpdatedEventTest` (all 6 tests, every accessor stays `getX()` per D8 — `SleepTimerUpdatedEvent`'s 5 members are all real Kotlin `fun`s), `playback/BufferUpdateEventTest` (mixed: `progress` converted to property syntax, `hasStarted()`/`hasEnded()` stay function calls; D9's `-1f`/`Float.NaN`/`-0.0f`/`0.0f` literals preserved character-for-character), `EpisodeDownloadEventTest` (D7 applied to the `Arrays.asList(null, itemWithDownloadUrl("b"))` site — no explicit type annotation; `item.setMedia(media)` → `item.media = media` per D8's setter-to-property rule; `getUrls()` kept as a function call).

**Deviation (disclosed) — `BufferUpdateEventTest`'s `Float.isNaN(...)`/`Float.NaN` do not resolve against `kotlin.Float`.** First compile attempt (verbatim per D9) failed: `Unresolved reference 'isNaN'`. Root cause: Kotlin's `Float.isNaN(): Boolean` is a top-level extension function requiring a receiver (`x.isNaN()`), not a companion-object static member — there is no `Float.isNaN(x)` call shape in `kotlin.Float`. D9 explicitly forbids restructuring into that `.isNaN()` extension form (or `x != x`), so the idiomatic Kotlin fix was not an option. Fixed with an explicit `import java.lang.Float`, which shadows the file-scoped default `kotlin.Float` import and lets both `Float.isNaN(event.progress)` and `Float.NaN` compile with their Java text preserved character-for-character (`java.lang.Float.isNaN(float)`'s Java primitive-`float` signature maps to Kotlin `Float` on both sides, so no type mismatch at the `BufferUpdateEvent.progressUpdate(progress: Float)` call site). No other symbol in the file names `Float` explicitly (`val event = ...` uses inference throughout), so the shadow's effect is confined to exactly these two call sites. Pure interop syntax fix, zero behavior or assertion-value change — confirmed by the D10 diff below and ktlint accepting the import without complaint.

**`EpisodeDownloadEventTest`'s `assertThrows` lambda written as a single physical line** (118 chars, under the 120-char limit), not the more conventional multi-line trailing-lambda block: the D10 extractor's paren-balance line-joiner only bundles a Kotlin `assertThrows(...) { ... }` call into one canonical statement (matching the Java original's single joined `assertThrows(X.class, () -> ...)` line) when the whole call, including the lambda body, appears on one physical source line — a multi-line trailing lambda's parens balance *before* the closing brace, so the joiner splits it into three separate un-canonicalized fragments and the extractor reports a false residual. Verified: writing the multi-line form first reproduced exactly this false residual; the single-line form makes it empty.

`./gradlew --console=plain :event:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 95/0/0/0. `./gradlew :event:ktlintCheck` → BUILD SUCCESSFUL (the `import java.lang.Float` shadow raised no ktlint objection). D10 assertion-content diff: `SleepTimerUpdatedEventTest` empty, `BufferUpdateEventTest` empty, `EpisodeDownloadEventTest` empty.

### Step 4 — Convert Tier C: 2 resolution/mutability files (13 tests)

**`FeedListUpdateEventTest` — D6 empirically settled.** `FeedListUpdateEvent(0)` (unsuffixed integer literal) compiled unaided and resolved to the `Long` constructor on the first attempt: `./gradlew :event:testDebugUnitTest --rerun` passed `intLiteralResolvesToLongFeedIdConstructor` green with the literal exactly as D6 predicted. Kotlin's integer-literal type inference selected the sole numeric-applicable overload among `FeedListUpdateEvent`'s three constructors (`List<Feed>`, `Feed`, `Long`), exactly as high-confidence-predicted. **No `0L` suffix added; the D6 hard-stop fallback was not triggered.** `Collections.<Feed>emptyList()` → `Collections.emptyList<Feed>()`, explicit type argument retained per D6. The private helper `feedWithId` kept Java's string-concatenation form (`"http://example.com/feed" + id`, which is also valid Kotlin `String + Long` operator syntax) rather than converting to a string template, for minimal diff; `feed.setId(id)` → `feed.id = id` per D8.

**`QueueEventTest` — D7 applied in full.** `Arrays.asList(...)` preserved for both the `queue` and `sortedQueue` locals with no explicit type annotation, so the inferred platform type stays assignable to `QueueEvent.setQueue`/`.sorted`'s invariant `MutableList<FeedItem>?` parameters; the `assertSame(queue, event.items)`/`assertSame(sortedQueue, event.items)` live-reference aliasing proofs convert unchanged. `item.setId(id)` → `item.id = id` per D8.

`./gradlew --console=plain :event:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 95/0/0/0. `./gradlew :event:ktlintCheck` → BUILD SUCCESSFUL.

D10 assertion-content diff: `FeedListUpdateEventTest` empty. `QueueEventTest` has **10 disclosed residual lines**, all pure syntax, no value change, confirmed via repo-wide grep to be confined to this one file:
1. `values.length` → `values.size` (×1) — Kotlin arrays expose `.size`, not `.length`; the extractor's `.length()`/`.size()` canonicalization rule only matches parenthesized method calls, not Java's bare array-field access. Same gap class as `:model` M7's `FeedItemFilterTest` residual (Step 11).
2. `.ordinal()` → `.ordinal` (×9, one per `Action` enum constant) — Kotlin enums expose `ordinal` as a property, not a method; the extractor's getter-canonicalization rule only matches `get`-prefixed calls (`\.get([A-Z])...`), not `ordinal()`. A new gap class not seen in `:model` M7.

Neither pattern recurs anywhere else in the 19 converted files (confirmed via `grep -rn '\.ordinal(' event/src/test/` and `grep -rn '\.length\b' event/src/test/`, both empty outside this already-fixed file) — not folded into the shared extractor, per the M7 Step 7/10/11 precedent of disclosing single-file gaps rather than extending the tool.

### Step 5 — Convert Tier D: `EventIdentityEqualityTest` alone (23 tests)

Applied D4, D5, D14 verbatim, as the plan's single highest-value hard-stop set:
- **D4:** `Integer.toHexString(obj.hashCode())` transcribed character-for-character, unqualified — compiled unaided against Kotlin's default `java.lang.*` import, exactly as the in-repo `SortOrder.kt:56` precedent predicted. Never `toString(16)`, `String.format`, `%08x`, or a bitmask.
- **D5:** both `.equals()` sites (`assertFalse(a.equals(b))`, `assertTrue(obj.toString().equals(expected))`) preserved verbatim — never simplified to `==`.
- **D14:** `assertNotEquals(a.hashCode(), b.hashCode())` unchanged; the loss of Java's implicit `int→long` widening shifts Kotlin's overload resolution to the boxed `assertNotEquals(Object, Object)` form, verified inert by all 23 tests staying green — no `.toLong()` added.

The two private helpers (`assertReferenceEquality`, `assertDefaultToStringShape`) kept their names and bodies. Signatures resolved to `(a: Any, b: Any)` / `(obj: Any)` (not `Any?`): both bodies call `.equals()`/`.hashCode()`/`.toString()` directly without a safe-call, which requires a non-null receiver, and confirmed by compilation succeeding on the first attempt with non-null types (no caller in the file ever passes null to either helper). `obj.getClass()` → `obj.javaClass` (standard, semantically-identical J2K idiom for `Any.javaClass: Class<out Any>`).

Converted `assertDefaultToStringShape` body, pasted verbatim per Step 5's instruction (D10's extractor does not read local declarations, so this is the direct evidence for D4's blind spot):
```kotlin
private fun assertDefaultToStringShape(obj: Any) {
    val expected = obj.javaClass.name + "@" + Integer.toHexString(obj.hashCode())
    assertTrue(obj.toString().equals(expected))
}
```

`./gradlew --console=plain :event:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 95/0/0/0, `EventIdentityEqualityTest`=23. `./gradlew :event:ktlintCheck` → BUILD SUCCESSFUL.

AC9's dedicated greps, run directly against the converted file:
- `grep -c 'Integer.toHexString' EventIdentityEqualityTest.kt` → **1**; `grep -crE 'toString\(16\)|String\.format|%08x|and 0xFFFFFFFF' event/src/test/` → **0** across all files.
- `grep -c '\.equals(' EventIdentityEqualityTest.kt` → **2**, at the same two logical sites.

D10 assertion-content diff: **2 disclosed residual lines**, both the private-helper *declaration* lines themselves (`private void assertReferenceEquality(Object a, Object b)` → `private fun assertReferenceEquality(a: Any, b: Any)`; `private void assertDefaultToStringShape(Object obj)` → `private fun assertDefaultToStringShape(obj: Any)`), an extractor false-positive since both method names start with "assert" and the extractor's assert-call filter regex matches the declaration line, not a JUnit call — same gap class as `:model` M7's `FeedItemTest.assertFeedItemImageWasUpdated`/`assertFeedItemImageWasNotUpdated` residual (Step 8). The assertion *bodies* (everything inside both helpers, and all 23 test methods) are byte-identical per the diff, confirmed by the pasted body above matching the plan's D4/D5/D14 requirements verbatim.

### Step 6 — Whole-suite reconciliation and full gate set

`./gradlew --console=plain :event:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, **95 tests, 0 failures, 0 errors, 0 skipped**.

**a. Re-extracted 22-row per-class test table** from the live XML results: identical row-for-row to Step 1(b)'s baseline (reproduced above in Step 1; not restated here since every value is unchanged).

**b. Re-extracted per-file assertion-count table**: identical row-for-row to Step 1(c)'s baseline, **211 total**.

**c. Consolidated D10 sweep across all 19 converted files, re-derived from the merge-base SHA in one pass** (not a restatement of the per-step results):

| File | Residual lines |
|---|---|
| DownloadLogEventTest | 0 |
| EpisodeDownloadEventTest | 0 |
| EventIdentityEqualityTest | 2 (disclosed, Step 5 — helper declarations) |
| FeedEventTest | 0 |
| FeedListUpdateEventTest | 0 |
| FeedUpdateRunningEventTest | 0 |
| MarkerEventsTest | 0 |
| PlayerErrorEventTest | 0 |
| QueueEventTest | 10 (disclosed, Step 4 — `.length`/`.ordinal()`) |
| SyncServiceEventTest | 0 |
| BufferUpdateEventTest | 0 |
| PlaybackHistoryEventTest | 0 |
| PlaybackPositionEventTest | 0 |
| PlaybackServiceEventTest | 0 |
| SleepTimerUpdatedEventTest | 0 |
| SpeedChangedEventTest | 0 |
| SkipIntroEndingChangedEventTest | 0 |
| SpeedPresetChangedEventTest | 0 |
| VolumeAdaptionChangedEventTest | 0 |

17/19 files empty; the 2 non-empty rows are exactly the residuals disclosed in Steps 4 and 5, unchanged and un-grown by this final sweep — confirming no later step incidentally touched an earlier file's already-passed content.

**d. `javap -p` re-proof of the `@JvmField`/`@JvmStatic` interop contract** (M8's AC7 re-run, language-independent), against `event/build/tmp/kotlin-classes/debug/`:

```
FeedUpdateRunningEvent:
  public final boolean isFeedUpdateRunning;
  public de.danoeh.antennapod.event.FeedUpdateRunningEvent(boolean);

FeedItemEvent:
  public final java.util.List<FeedItem> items;
  public final boolean unreadStatusChanged;
  public static final int indexOfItemWithId(java.util.List<FeedItem>, long);

MessageEvent:
  public final java.lang.String message;
  public final androidx.core.util.Consumer<android.content.Context> action;
  public final java.lang.String actionText;
  public MessageEvent(String, Consumer<Context>, String);
  public MessageEvent(String);            // exactly 2 constructors

QueueEvent:
  public final QueueEvent$Action action;
  public final FeedItem item;
  public final java.util.List<FeedItem> items;
  public final int position;              // 4 public final fields
  public static final QueueEvent added(FeedItem, int);
  public static final QueueEvent setQueue(java.util.List<FeedItem>);
  public static final QueueEvent removed(FeedItem);
  public static final QueueEvent irreversibleRemoved(FeedItem);
  public static final QueueEvent cleared();
  public static final QueueEvent sorted(java.util.List<FeedItem>);
  public static final QueueEvent moved(FeedItem, int);   // 7 static factories

BufferUpdateEvent:
  public static final BufferUpdateEvent started();
  public static final BufferUpdateEvent ended();
  public static final BufferUpdateEvent progressUpdate(float);

SleepTimerUpdatedEvent:
  public static final SleepTimerUpdatedEvent justEnabled(TimerValue);
  public static final SleepTimerUpdatedEvent updated(TimerValue);
  public static final SleepTimerUpdatedEvent cancelled();
```

All match Step 6(d)'s required shapes exactly: `FeedUpdateRunningEvent.isFeedUpdateRunning` and `FeedItemEvent.items`/`.unreadStatusChanged` are bare public final fields with no synthesized accessor; `MessageEvent` has exactly 2 constructors; `QueueEvent` has 4 public final fields and 7 `public static` factories; `BufferUpdateEvent`/`SleepTimerUpdatedEvent` expose their 3+3 factories as `public static`. This is the mechanical, language-independent proof that nothing about the Java-visible surface moved — none of the `@JvmField`/`@JvmStatic` annotations' effects changed, regardless of which language now hosts the tests that (partially) exercise them.

**Full gate set:**
- `./gradlew --console=plain :event:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 95/0/0/0.
- `./gradlew :event:ktlintCheck` → BUILD SUCCESSFUL, `ktlintTestSourceSetCheck` genuinely executing (not `SKIPPED`/`NO-SOURCE`). Zero `@Suppress("ktlint:...")` added, no `ktlint_disabled_rules`, no `.editorconfig` change, no ktlint exclusion in any build file.
- `./gradlew checkstyle lint` → repo-wide **FAILS**, on the same three pre-existing, out-of-scope failures identified in Step 2 (`:app-wearos:compileFreeDebugKotlin`/`compilePlayDebugKotlin`, `:app:spotbugsPlayDebug`) — re-confirmed unchanged from the `git stash`-verified baseline; no new failure introduced by this milestone's diff. `:event:lintDebug` alone: BUILD SUCCESSFUL.
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL, both `assembleFreeDebug` and `assemblePlayDebug` completed, compiling all 10 consuming modules against `:event`'s now-19/22-Kotlin test source set (tests aren't part of `:app:assembleDebug`'s compile graph, but this confirms zero edits leaked outside `event/src/test/` and the four doc files, and that the production `@JvmField`/`@JvmStatic` contract itself is untouched).
- `find event/src/test -name '*.java'` → exactly `FeedItemEventTest.java`, `MessageEventTest.java`, `PublicFieldInteropTest.java` (3). `find event/src/test -name '*.kt' | wc -l` → **19**. `:event:compileDebugUnitTestJavaWithJavac` → still executes (not `NO-SOURCE`).
- No cross-references: `grep`-verified no `.kt` file names any of the 3 kept-Java classes, and none of the 3 Java files names any converted Kotlin test class.
- `git diff --stat 46f8b3a58 -- event/src/main/` → empty. `git diff 46f8b3a58 -- event/build.gradle` → empty. `git diff 46f8b3a58 -- config/checkstyle/suppressions.xml` → empty.
- `git diff 46f8b3a58 -- PublicFieldInteropTest.java MessageEventTest.java FeedItemEventTest.java` → **empty** (re-confirmed at the end of every step, not just once).
- `git diff --name-only 46f8b3a58` → exactly the 19 renamed `.java`→`.kt` pairs (before Step 7's doc edits were staged) — no file outside File Scope.

### Step 7 — Documentation

`event/README.md`: appended a new bullet to the existing "Conventions" list, phrased as a long-term-stable module convention with no milestone/task provenance per AGENTS.md, explaining that the test source set is intentionally mixed Kotlin/Java and naming the three files that stay Java and why (the two failure modes — inexpressible-in-Kotlin compile errors for `MessageEventTest`/`FeedItemEventTest`, and silent tautology-conversion for `PublicFieldInteropTest`).

`tasks/antennapod-model-kotlin-future-work.md` item #3: appended a dated update noting `:event`'s Milestone 9 outcome — the `allWarningsAsErrors`-for-Kotlin-test-compile gap now applies to the 19 converted files, narrower than `:model`'s full-module gap since D2's 3 kept-Java files keep `-Xlint:all -Werror` covering their surface. Item #5 untouched, per File Scope.

### Deviations from plan

1. **`BufferUpdateEventTest`'s `Float.isNaN(...)`/`Float.NaN` required an explicit `import java.lang.Float`** to compile (Step 3) — D9 assumed these would compile unchanged against Kotlin's default `Float`, but `kotlin.Float.isNaN` is a receiver-requiring extension function, not a callable-as-written static member, and D9 explicitly forbade the idiomatic `.isNaN()` rewrite. The disclosed fix shadows the default import for that one file only, preserving the Java call-site text exactly with zero behavior change. See Step 3 above for the full root-cause and safety argument.
2. **`EpisodeDownloadEventTest`'s `assertThrows` lambda written as a single physical line** rather than the more conventional multi-line block (Step 3) — required for the D10 extractor's paren-balance joiner to canonicalize the call correctly; purely a formatting choice with no assertion-content effect, verified by the resulting empty diff.
3. **10 disclosed D10 residual lines in `QueueEventTest`** (`values.length`→`.size`, `.ordinal()`→`.ordinal` ×9; Step 4) and **2 disclosed D10 residual lines in `EventIdentityEqualityTest`** (private-helper declaration lines only; Step 5) — both are pure-syntax extractor gaps (bare array field access, and enum `ordinal` as a Kotlin property, and "assert"-prefixed private helper names), never a value/argument/assertion-function change, confirmed neither pattern recurs elsewhere in the 19 files. Consistent with the AC8 policy that a disclosed, individually-justified residual is not a failure of AC8 — only an undisclosed or value-altering one would be.
4. **ktlint import-ordering fix applied to every converted file** (Step 2) — not a plan deviation in substance (the Plan named no specific import layout), but worth recording: ktlint's `ImportOrderingRule` rejected the Java-original's two-block import layout (plain imports, blank line, static imports) the first time it ran for real; all 19 files use a single lexicographically-sorted block with no blank line, matching the established `:model` precedent.

No production `.kt` file needed to change (confirmed: `git diff --stat 46f8b3a58 -- event/src/main/` is empty), and no genuine J2K-interop issue forced a production-code fix — matching the Plan's expectation for a test-only milestone. No new dependency was added. `event/build.gradle` was not touched.

### Commit message

```
feat(event): kotlin migration — convert 19 of :event's 22 test files to Kotlin

Milestone 9 of the :event kotlin track (test source set), following
Milestone 8's production-code conversion (PR #14). 19 files convert to
idiomatic Kotlin; 3 stay Java permanently and by design (PublicField-
InteropTest, MessageEventTest, FeedItemEventTest) because each hosts an
assertion whose oracle is "javac accepts this call shape" and has no
Kotlin equivalent — converting them would either not compile at all or
silently turn passing tests into tautologies with no failing signal.

95 tests green before and after, per-class and per-file assertion
counts unchanged, D10 assertion-content diff empty or individually
disclosed for all 19 converted files, and the @JvmField/@JvmStatic
interop contract re-proven mechanically via javap.
```

### Characterization test results

Per Plan Step's "no new characterization tests needed" framing (Milestone 8 already wrote this suite from scratch as the characterization layer): all 95 pre-existing tests are the characterization suite for this milestone, and every one is reported explicitly below with before/after status and what it exercises.

| Class | Tests | Before | After | Exercises |
|---|---|---|---|---|
| EventIdentityEqualityTest | 23 | PASS | PASS | All 23 production classes: `assertNotSame`/non-`equals()` reference-identity proof + JVM-default `toString()` shape (20 classes) |
| PublicFieldInteropTest (kept Java) | 8 | PASS | PASS | All 12 `@JvmField` fields read via bare Java field syntax — unconverted, so this proof is fully intact |
| QueueEventTest | 8 | PASS | PASS | All 7 static factories set action/item/items/position correctly; 9-constant `Action` enum ordinal order |
| BufferUpdateEventTest | 7 | PASS | PASS | `started`/`ended`/`progressUpdate` sentinels; NaN, `-0.0f`, `+0.0f` float edge cases (D9-preserved literals) |
| FeedItemEventTest (kept Java) | 7 | PASS | PASS | Field storage, `indexOfItemWithId` incl. nulls/empty/miss, null-items constructor NPE — unconverted, Kotlin-inexpressible call preserved |
| EpisodeDownloadEventTest | 6 | PASS | PASS | `getUrls()` live-map view; `indexOfItemWithDownloadUrl` incl. null items/media, NPE on null downloadUrl |
| SleepTimerUpdatedEventTest | 6 | PASS | PASS | `justEnabled` negation, `updated` zero-clamp, `Long.MAX_VALUE` cancellation, `Long.MIN_VALUE` negation overflow |
| FeedListUpdateEventTest | 5 | PASS | PASS | All 3 constructor overloads; `contains` by id not identity; empty list; D6's int-literal→`Long`-overload resolution |
| MarkerEventsTest | 4 | PASS | PASS | Construction of the 4 empty marker classes |
| MessageEventTest (kept Java) | 3 | PASS | PASS | 1-arg vs 3-arg constructor; action stored by identity and invoked via `accept(null)` — unconverted, Kotlin-inexpressible call preserved |
| DownloadLogEventTest | 2 | PASS | PASS | New instance per `listUpdated()`; exact `toString()` |
| PlaybackHistoryEventTest | 2 | PASS | PASS | Same as above, playback package |
| FeedEventTest | 2 | PASS | PASS | `feedId` field; exact `toString()` format string |
| FeedUpdateRunningEventTest | 2 | PASS | PASS | Boolean round trip via `@JvmField` |
| PlaybackServiceEventTest | 2 | PASS | PASS | Both `Action` enum constants |
| PlayerErrorEventTest | 2 | PASS | PASS | Message stored; null message allowed |
| PlaybackPositionEventTest | 1 | PASS | PASS | position/duration stored |
| SpeedChangedEventTest | 1 | PASS | PASS | newSpeed stored |
| SyncServiceEventTest | 1 | PASS | PASS | messageResId stored |
| SkipIntroEndingChangedEventTest | 1 | PASS | PASS | Three fields stored |
| SpeedPresetChangedEventTest | 1 | PASS | PASS | Three fields stored |
| VolumeAdaptionChangedEventTest | 1 | PASS | PASS | Two fields stored |
| **Total** | **95** | **PASS** | **PASS** | |

### Acceptance Criteria — verified

- [x] **AC1** — Step 1 baseline: BUILD SUCCESSFUL, 95/0/0/0, `--rerun` used, 22-row table pasted above.
- [x] **AC2** — Green (95/0/0/0) at the end of every step (2, 3, 4, 5, 6), each run with `--rerun`.
- [x] **AC3** — Post-conversion per-class table (Step 6a) matches AC1 row for row, all 22 classes.
- [x] **AC4** — No test added/removed/renamed/split/merged/moved; all method names preserved verbatim, no backtick sentence names anywhere (`grep -rn '`' event/src/test/` → 0, folded into AC14 below).
- [x] **AC5** — `git diff 46f8b3a58 -- .../PublicFieldInteropTest.java .../MessageEventTest.java .../FeedItemEventTest.java` → empty.
- [x] **AC6** — 3 `.java` / 19 `.kt`, exact filenames confirmed; `compileDebugUnitTestJavaWithJavac` still executes; no cross-references either direction.
- [x] **AC7** — Per-file assertion counts identical before/after for all 19 files; 176 total across the 19, matches exactly.
- [x] **AC8** — D10 diff empty for 17/19 files; 2 files (QueueEventTest, EventIdentityEqualityTest) carry disclosed, individually-justified, pure-syntax residuals — no expected-value literal, argument order, or assertion function changed in any of them. Reviewer should re-run Step 1's exact command to reproduce.
- [x] **AC9** — All three silent-idiomization hazards pinned by direct grep (Integer.toHexString=1/forbidden-forms=0; .equals(=2; listOf(=0, Arrays.asList retained, -0.0f present).
- [x] **AC10** — `FeedListUpdateEvent(0)` unsuffixed, `FeedListUpdateEvent(0L)` count=0; `Collections.emptyList<Feed>()` explicit type argument retained; D6 empirical outcome recorded (compiled unaided, no fallback triggered).
- [x] **AC11** — `git diff --stat 46f8b3a58 -- event/src/main/` empty; `./gradlew :app:assembleDebug` green, both flavors.
- [x] **AC12** — `javap -p` output pasted in Step 6d, matches all required shapes.
- [x] **AC13** — `grep -rn '!!' event/src/test/` → 0.
- [x] **AC14** — `grep -rn '`' event/src/test/` → 0; no explicit type annotation collapsing a platform type.
- [x] **AC15** — `ktlintCheck` BUILD SUCCESSFUL, `ktlintTestSourceSetCheck` genuinely executing, zero `@Suppress`/exclusions/`.editorconfig` changes.
- [x] **AC16** — `:event:lintDebug` BUILD SUCCESSFUL; Research Unknown 8 settled in writing (Step 2): `lintAnalyzeDebugUnitTest` executes and reports nothing against the new `.kt` files.
- [x] **AC17** — `git diff --name-only 46f8b3a58` lists only File Scope files; `event/build.gradle` and `config/checkstyle/suppressions.xml` unchanged; `assertlines.pl` not committed.
- [x] **AC18** — `event/README.md` updated per D15; future-work item #3 gains the `:event` M9 instance with the narrower-gap nuance; item #5 untouched.

### Manual/device verification needed

None. `:event` has no UI, resources, `AndroidManifest.xml`, navigation, network calls, or `androidTest` source set (confirmed by Research and re-confirmed by `ls event/src/` showing only `main`/`test`). All 18 applicable Acceptance Criteria were verified locally via Gradle/grep/javap; nothing in this milestone requires running the app or a device/emulator.
