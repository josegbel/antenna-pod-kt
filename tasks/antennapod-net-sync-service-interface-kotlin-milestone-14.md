# antennapod-net-sync-service-interface-kotlin-milestone-14

> **Description:** Convert the `:net:sync:service-interface` module's remaining Java test files to Kotlin, now that all production code in the module is 100% Kotlin (Milestone 11 complete).
> **Repo:** `services/android-migration/projects/portfolio/antennapod`
> **Created:** 2026-08-05

> **Pre-research context (carried over from Milestone 11 / standing decisions — do not re-derive):**
> - `:net:sync:service-interface` production code is **100% Kotlin** as of Milestone 11 (PR #17, merged into `develop`). Per the same test-migration-sequencing pattern used for `:net:download:service-interface` (Milestone 10 → Milestone 13), the module's test suite was deliberately left in Java through that milestone.
> - The Milestone 13 checkpoint (`features/antennapod-net-download-service-interface-kotlin-milestone-13.checkpoint.md`) explicitly names this suite as the next queued item, pending its own go-ahead — that go-ahead was given 2026-08-05.
> - Confirmed directly against source (2026-08-05, on `develop` after PR #19 merged): `net/sync/service-interface/src/test/java/de/danoeh/antennapod/net/sync/serviceinterface/` contains exactly **9 Java files, 0 Kotlin**: `SyncServiceExceptionTest`, `SynchronizationQueueTest`, `EpisodeActionCharacterizationTest`, `SynchronizationProviderTest`, `UploadChangesResponseTest`, `ISyncServiceTest`, `EpisodeActionChangesTest`, `EpisodeActionJsonTest`, `SubscriptionChangesTest`. Verify actual helper/fixture classes and package layout in research, do not assume this list is exhaustive of every class in the directory.
> - This milestone is a **test-only** conversion — no production `.kt` file should need to change as a result (unless a genuine J2K-interop issue forces a minimal, disclosed fix, per the established deviation-disclosure pattern in this portfolio).
> - Test tasks are **flavored** (`free`/`play`, from `playFlavor.gradle`): use `testFreeDebugUnitTest` / `testPlayDebugUnitTest`, not a bare `testDebugUnitTest` (it doesn't exist). Always pass `--rerun`, since Gradle otherwise reports `UP-TO-DATE` without re-executing — and when running both flavors, invoke `./gradlew` **separately per flavor**; a single two-task command line only re-executes one of them (learned the hard way in Milestone 13, see `tasks/antennapod-net-download-service-interface-kotlin-milestone-13.md:23`).
> - Direct precedent for this exact shape of milestone: `tasks/antennapod-net-download-service-interface-kotlin-milestone-13.md` (this module's sibling interface module, same test-only-after-100%-Kotlin-production shape, same portfolio), `tasks/antennapod-model-kotlin-milestone-7.md`, and `tasks/antennapod-event-kotlin-milestone-9.md` — all good references for J2K hazard categories (numeric overload resolution in assertions, Kotlin hard keywords used as identifiers, Java-oracle erosion on constants/`@JvmStatic`) likely to recur here.
> - See `tasks/antennapod-net-sync-service-interface-kotlin.md` (Milestone 11) for this module's full production-conversion history, including its documented `!!`-inventory and `EpisodeAction.equals` defect-pinning decisions (D9, D10) — the test suite being converted here is what exercises those.
> - This is unaffiliated OSS portfolio case-study work — see `services/android-migration/projects/portfolio/README.md`.

## Research
_Last updated by: legacy-android-researcher | 2026-08-05_

### Summary

`:net:sync:service-interface` is a small interop-facade module whose production code is 100% Kotlin as of Milestone 11 — **9 `.kt`, 0 `.java`, 381 LOC** under `net/sync/service-interface/src/main/java/de/danoeh/antennapod/net/sync/serviceinterface/`. Its test source set is the exact mirror image: **9 `.java`, 0 `.kt`, 1,179 LOC, 83 tests, 162 assertion calls**, all in the same single package, no `androidTest` source set (`net/sync/service-interface/src/` contains only `main` and `test`). The pre-research file list verifies exactly against live source — all nine named files exist, no additional helper, fixture, or `Mother`-style class exists, and every file is one top-level `public class …Test` with its helpers as private static methods inside the single class that uses them. Provenance is unusual and worth stating up front: **all nine files were authored by Milestone 11 itself** (four commits, all 2026-07-28: `4d73da5b4`, `c368a4118`, `9972f46f1`, `39623f6f4`) — this module had **zero** tests before that milestone. Nothing here is upstream AntennaPod code; the suite is entirely a migration artifact written by this pipeline as the characterization net for the production conversion, which is why it maps one-file-per-production-file and why its interop assertions are so deliberate.

The `kotlin` track here is mechanically small and, like Milestone 13, has **no ambiguity-shaped hazards**: **zero** Kotlin hard-keyword identifiers, **zero** `int`→`long` `assertEquals` widening traps (every integer literal is matched to an actual of its own width — `started`/`position`/`total` are `Int` at `EpisodeAction.kt:24,28,32`, every `timestamp` is `Long`, so the seventeen affected assertions merely shift from `assertEquals(long,long)` to `assertEquals(Object,Object)` with an identical verdict), **zero** static/initializer blocks, **no Mockito, no PowerMock, no hamcrest** (`net/sync/service-interface/build.gradle:19,23` declares only `libs.junit` and `libs.robolectric`). What replaces them is two things. First, a **nullability tax that is entirely a consequence of Milestone 11's D9**: `readFromJsonObject` returns `EpisodeAction?` and `writeToJsonObject` returns `JSONObject?` by deliberate decision, so the converted suite needs **≈28 `!!` operators**, all in `EpisodeActionJsonTest` — and at **three specific sites** a developer writing `?.` instead of `!!` silently converts a real assertion into a vacuous one that still passes. Second, the same structural problem Milestone 13 hit, here **broader in surface but with strictly better residual coverage**: the module README (`net/sync/service-interface/README.md:52-59`, convention #9) states outright that this suite "is Java by design and must stay Java" and names four files as compile-time guards. That convention is the direct analogue of `DownloadServiceInterfaceTest.testWorkConstants` and it is the planner's central scoping input. My finding materially revises it: of the four files the README names, **one has its guard destroyed but externally re-proven, one keeps a strictly stronger reflection-based guard, one keeps its guard outright, and one is actively *strengthened* by conversion** (Java→Kotlin turns a runtime intrinsic check into a compile error). **Nothing in this module ends up with zero residual coverage** — a materially better position than Milestone 13's `FileNameGenerator`. The suite is green on both variants at HEAD.

### Findings

#### Existing surface

Production, 9 files / 381 LOC, all Kotlin, one package (`net/sync/service-interface/src/main/java/de/danoeh/antennapod/net/sync/serviceinterface/`):

| File | LOC | Shape | Relevance to this milestone |
|---|---|---|---|
| `EpisodeAction.kt` | 242 | `class` + nested `enum Action` + nested `Builder` + `companion object` | Carries all six of the module's nullable members, all three `!!`, the pinned `equals` defect, and both JSON methods. Source of ~95% of this milestone's hazard surface. |
| `SynchronizationQueue.kt` | 31 | `abstract class` + `companion object { @JvmStatic var instance: SynchronizationQueue? }` | 9 abstract methods, 4 with nullable parameters |
| `SynchronizationQueueStub.kt` | 32 | `class : SynchronizationQueue()` | No-op implementation used as a test double repo-wide |
| `ISyncService.kt` | 24 | `interface`, 6 methods, all `@Throws`, two `@JvmSuppressWildcards` | The `@Throws`/wildcard guard target |
| `SynchronizationProvider.kt` | 13 | `enum class` + `@JvmStatic fun fromIdentifier(String?)` | Nullable-parameter guard target |
| `SyncServiceException.kt` | 10 | `open class : Exception`, 2 ctors, `private const val serialVersionUID` | `open`-ness + `serialVersionUID` shape guard target |
| `SubscriptionChanges.kt` | 11 | `class`, 3 `val`, golden `toString()` | Trivial |
| `EpisodeActionChanges.kt` | 10 | `class`, 2 `val`, golden `toString()` | Trivial |
| `UploadChangesResponse.kt` | 8 | `abstract class(@JvmField val timestamp: Long)` | `@JvmField` guard target |

Build: `net/sync/service-interface/build.gradle` applies `android.library`, `kotlin.android`, `ktlint`, plus `../../../common.gradle`. Dependencies are `:model`, `:storage:preferences`, rx*, and — for tests only — `libs.junit` and `libs.robolectric` (`:23`, with an in-file comment scoping Robolectric to the two `EpisodeAction` JSON/characterization files).

**Correction to the task header, verified empirically and load-bearing for every later verification step.** The header instructs using `testFreeDebugUnitTest` / `testPlayDebugUnitTest`. **Those tasks do not exist in this module.** `net/sync/service-interface/build.gradle` does **not** apply `playFlavor.gradle`; `./gradlew :net:sync:service-interface:tasks --all` lists only **`testDebugUnitTest`** and **`testReleaseUnitTest`**. The header's advice was correctly carried from Milestone 13's sibling — `net/download/service-interface/build.gradle` *does* apply `playFlavor.gradle`, as do `net/common`, `net/ssl`, `net/discovery`, `net/download/service`, and `net/sync/service` — but it does not transfer here, and the module README already warns about exactly this (`README.md:48-51`, convention #8: "do not copy flavoured task names from a module that does"). The separate-invocation `--rerun` discipline from Milestone 13 still applies and I followed it.

#### Java/Kotlin interop boundary

**Calls INTO this module from outside it: 41 files across 9 modules — 28 Java, 13 Kotlin.** The 28 Java consumers are what makes convention #9's premise still live: they are compiled by `:app:assembleDebug` and they exercise every JVM-shape decision Milestone 11 made.

| Consuming module | Java | Kotlin |
|---|---|---|
| `:app` | 3 (`MainActivity`, `ClientConfigurator`, `EpisodeMultiSelectActionHandler`) | 0 |
| `:net:sync:gpoddernet` | 7 | 0 |
| `:net:sync:service` | 5 (all test) | 4 (all main) |
| `:storage:database` | 3 | 0 |
| `:net:download:service` | 5 | 0 |
| `:playback:service` | 2 | 0 |
| `:ui:preferences` | 3 | 0 |

**Calls OUT:** only `:model` (`Feed`, `FeedItem`, `FeedMedia`), `android.text.TextUtils`, `android.util.Log`, `org.json`, and `java.text`/`java.time`. The three `:model` types used by the test fixtures are already Kotlin with the shapes the fixtures need — `FeedItem.feed: Feed?` (`FeedItem.kt:56`), `FeedItem.media: FeedMedia?` (`:47`), `FeedItem.itemIdentifier: String?` (`:29`) are all nullable `var`s, so `item.feed = null` transcribes cleanly; `Feed(url: String?, lastModified: String?)` (`Feed.kt:198`) is the **only** two-argument `Feed` constructor, so `Feed(PODCAST_URL, null)` has no overload ambiguity from Kotlin; `FeedMedia(item: FeedItem?, downloadUrl: String?, size: Long, mimeType: String?)` (`FeedMedia.kt:85`) matches the fixture exactly.

**Public API surface that must not silently break:** none of it is touched by this milestone — this is a test-only conversion and no `.kt` under `src/main/` should change. The interop risk is not to the API but to the *evidence* that the API's JVM shape is correct, which is analysed in the next two subsections.

#### Current test coverage

**Green baseline confirmed at HEAD (`5f816b768`, `develop` == `origin/develop`, PR #19 merged), working tree clean, no feature branch created.** Both variants run as separate `./gradlew` invocations with `--rerun`; both genuinely re-executed (result XMLs restamped 11:38 and 11:39, PASSED lines emitted for every test).

- `./gradlew :net:sync:service-interface:testDebugUnitTest --rerun` → **BUILD SUCCESSFUL, 83 tests, 0 failures, 0 errors, 0 skipped**
- `./gradlew :net:sync:service-interface:testReleaseUnitTest --rerun` → **BUILD SUCCESSFUL, 83 tests, 0 failures, 0 errors, 0 skipped**

Class-by-class counts are byte-identical between variants; no test is variant-gated.

| File | LOC | `@Test` | Asserts | Robolectric | What it actually asserts |
|---|---|---|---|---|---|
| `EpisodeActionJsonTest.java` | 416 | 28 | 72 | yes | `readFromJsonObject` (18) / `writeToJsonObject` (7) / round-trip (3). The gpodder.net + Nextcloud wire format **and** the on-disk SharedPreferences pending-sync format. Includes the golden UTC timestamp format and the sub-second-truncation round-trip. |
| `EpisodeActionCharacterizationTest.java` | 320 | 25 | 50 | yes | `Builder` (14, incl. one test per overload for the `Action?` nullability decision), static `Action` aliases (2, one by `assertSame`, one by **reflection** on `public static final`), `equals`/`hashCode` (7, pinning the D10 defect), `toString` (2 golden strings). |
| `SynchronizationProviderTest.java` | 60 | 9 | 9 | no | `fromIdentifier` null/empty/unknown/case-sensitivity/exact-match, `getIdentifier` persisted strings, `values()` declaration order. |
| `SyncServiceExceptionTest.java` | 85 | 7 | 11 | no | Both constructors, cause→message behaviour, checked-not-runtime, `serialVersionUID` shape **and** value by reflection, and a nested subclass calling both `super(…)` forms. |
| `ISyncServiceTest.java` | 102 | 4 | 5 | no | A nested `TestSyncService implements ISyncService` with `throws` on all six overrides, invoked and caught through the interface type; two anonymous `UploadChangesResponse` subclasses. |
| `UploadChangesResponseTest.java` | 62 | 3 | 6 | no | Constructor round-trip; a nested subclass reading inherited `timestamp` **unqualified**; a reflection test asserting `public final` non-static **and that no `getTimestamp()` accessor exists**. |
| `SynchronizationQueueTest.java` | 58 | 3 | 2 | no | Static holder get/set incl. `null`; all nine `SynchronizationQueueStub` methods called, four with `null` arguments. Captures/restores the previous instance in `@Before`/`@After` so it cannot pollute a shared JVM fork. |
| `SubscriptionChangesTest.java` | 39 | 2 | 4 | no | Constructor round-trip + the misleadingly-named `"SubscriptionChange [added=…]"` golden `toString()`. |
| `EpisodeActionChangesTest.java` | 37 | 2 | 3 | no | Constructor round-trip + the misleadingly-named `"EpisodeActionGetResponse{…}"` golden `toString()`. |
| **Total** | **1,179** | **83** | **162** | 2 of 9 | |

This is genuinely dense coverage — 83 tests against 381 production LOC — because it was purpose-built as Milestone 11's equivalence oracle. It is a strong safety net for a test-only conversion, with the specific caveats below.

#### Characterization-test gaps

Behaviour with zero coverage today is **not** the risk on this milestone — the suite already covers the module comprehensively, and no new characterization test needs writing before conversion. The risk is the inverse: **existing coverage that silently stops proving what it proves today.** Three gaps, in descending severity.

**Gap 1 (CRITICAL, silent, and specific to this module) — three `assertNull` sites become vacuous tautologies if `?.` is used instead of `!!`.** `readFromJsonObject` returns `EpisodeAction?` (`EpisodeAction.kt:200`), so in Kotlin every local it produces needs forcing. At `EpisodeActionJsonTest:147-149`, `:157-159` and `:177-179` the local is then passed to `assertNull(…)`:

```
EpisodeAction action = EpisodeAction.readFromJsonObject(json);
assertNull(action.getTimestamp());     // :149  — also :159 (timestamp), :179 (guid)
```

Written as `assertNull(action!!.timestamp)` this keeps proving "parsing succeeded **and** the timestamp is null". Written as `assertNull(action?.timestamp)` it passes identically if `action` is null, i.e. if `readFromJsonObject` regressed to returning null for a valid payload — the exact failure these three tests exist to catch. Zero compile error, zero test failure, and `?.` is the idiom a J2K-cleanup pass or a ktlint-driven "avoid `!!`" instinct will reach for first. This is the one place on this milestone where a green run is not evidence of correctness.

**Gap 2 (HIGH, silent) — the suite's oracle value is partly language-dependent, and README convention #9 says so explicitly.** `net/sync/service-interface/README.md:52-59` states the suite "is Java by design and must stay Java" and names four files as compile-time guards. Milestone 11's own OQ2 (`tasks/antennapod-net-sync-service-interface-kotlin.md:655`) restates it and inherits `:event` M9's bright-line rule — *"any test that cannot be hosted in Kotlin without weakening the interop proof stays Java, which here is probably at least those four."* **That "probably" is testable, and I tested it. The picture is materially better than the README's framing, and materially more varied:**

| README convention | In-module guard today | Effect of converting it to Kotlin | External residual |
|---|---|---|---|
| **#1** `fromIdentifier(provider: String?)` must stay nullable | `SynchronizationProviderTest:12-14` passes a `null` literal | **STRENGTHENED.** From Java, tightening to non-null still compiles and fails only at *runtime* via `Intrinsics`. From Kotlin, `fromIdentifier(null)` is a **compile error**. | `SynchronizationPreferencesFragment.java:120` (Java, proves nothing); `SyncService.kt:352` |
| **#2** `UploadChangesResponse.timestamp` must stay `@JvmField` | Two guards: `UploadChangesResponseTest:39-43` (unqualified inherited read) and `:46-61` (reflection) | Compile guard **LOST** (a Kotlin subclass reads the property unqualified whether or not `@JvmField` is present). **Reflection guard SURVIVES and is strictly stronger** — it also asserts *no* `getTimestamp()` accessor exists, which the unqualified read never proved. | `GpodnetUploadChangesResponse.java:48` reads `timestamp` unqualified in `toString()`; `GpodnetEpisodeActionPostResponse.java:22` and `NextcloudSyncService.java:160-164` subclass it |
| **#3** four `Action` aliases must stay `@JvmField val` | `EpisodeActionCharacterizationTest:207-212` (`assertSame`) and `:215-223` (**reflection** on `public static final`) | **SURVIVES.** Reflection is language-independent. | 5 external Java production reads: `MediaDownloadedHandler.java:114`, `EpisodeMultiSelectActionHandler.java:111,131`, `FeedDatabaseWriter.java:132`, `DBWriter.java:164` |
| **#4** `@Throws` on all six `ISyncService` methods | `ISyncServiceTest:19-56` (Java `throws` on six overrides) + `:75-80` (`catch` through the interface type) | **LOST.** Kotlin has no checked exceptions; the overrides and the `catch` compile regardless. | **Strong**: two Java implementors, `GpodnetService.java:46` and `NextcloudSyncService.java:30`, both compiled by `:app:assembleDebug` |
| **#5** `@JvmSuppressWildcards` on the two `List` methods | `ISyncServiceTest:36,47` (Java overrides with `List<String>` / `List<EpisodeAction>`) | **LOST.** A Kotlin override never sees a wildcarded signature. | Same two Java implementors |
| **#6a** `@JvmStatic` on the queue accessors | `SynchronizationQueueTest:21,26,32,41` call `getInstance()`/`setInstance()` by their Java names | **LOST** — in Kotlin the call is `SynchronizationQueue.instance`, which compiles regardless of `@JvmStatic`. | **Very strong**: **27** external Java call sites across `:ui:preferences` (7), `:storage:database` (6), `:app` (4), `:playback:service` (4), `:net:download:service` (5), incl. `ClientConfigurator.java:53` |
| **#6b** `@JvmStatic` on `readFromJsonObject` | 21 call sites in `EpisodeActionJsonTest` | **LOST** (Kotlin reaches companion members via the class name regardless). | **Thin**: exactly **one** external Java caller, `ResponseMapper.java:52`. The only other caller is Kotlin (`SynchronizationQueueStorage.kt:21`). |
| **#7** `serialVersionUID` as `private const val` in a companion | `SyncServiceExceptionTest:60-67` and `:70-75` (**reflection**: private/static/final + value) | **SURVIVES.** | **None** — the two Java subclasses (`GpodnetServiceException.java:6`, `NextcloudSynchronizationServiceException.java`) declare their *own* `serialVersionUID` and prove nothing about the parent's. The in-module reflection test is the sole guard, and it survives. |
| — `SyncServiceException` must stay `open` | `SyncServiceExceptionTest:20-28` (nested subclass, both `super(…)` forms) | **SURVIVES.** A Kotlin subclass still requires `open`, and still requires both constructor signatures. | Two external Java subclasses |
| — stub methods' nullable parameters | `SynchronizationQueueTest:53-56` passes `null` ×4 | **STRENGTHENED**, same mechanism as #1: runtime `Intrinsics` failure becomes a compile error. | None |

**Net:** of the four files README #9 names as must-stay-Java, `SyncServiceExceptionTest` keeps its guard outright, `SynchronizationQueueTest` is strengthened, `UploadChangesResponseTest` trades a weak compile guard for a stronger surviving reflection guard, and only `ISyncServiceTest` genuinely loses both of its (conventions #4 and #5) — and those are the two with the strongest external duplication in the whole table. **No convention ends with zero residual coverage.** What is actually paid is **guard latency and locality, not guard existence**: conventions #4, #5, #6a and #6b move from a 5-second module test task to `:app:assembleDebug`, a slower and coarser gate that names a different module in its failure. That is the honest cost, and it is the planner's to price — I am not deciding it.

**Gap 3 (LOW) — `-Werror` coverage disappears for 1,179 LOC.** `common.gradle:47-52` applies `-Xlint:all … -Werror` via `tasks.withType(JavaCompile)`, which today includes `compileDebugUnitTestJavaWithJavac`. After conversion that task goes `NO-SOURCE`, and there is **no** `allWarningsAsErrors` anywhere in the build (verified across all `*.gradle`), so Kotlin warnings are not errors. This is a new instance of an already-recorded item — `tasks/antennapod-model-kotlin-future-work.md` item 11, filed by Milestone 11 for the same reason on the production side. `checkstyle` is unaffected: `common.gradle:172-176` sources only `src/main/java/**/*.java`, and this module has had zero of those since Milestone 11.

#### Track-specific findings — `kotlin`

**Null-safety hazards.** All of them descend from Milestone 11's D9 (`tasks/antennapod-net-sync-service-interface-kotlin.md:398-431`), which chose these nullable returns deliberately and documented them as forced, not preferred. The converted suite therefore needs roughly **28 `!!`**, concentrated entirely in `EpisodeActionJsonTest`:

| Source of nullability | Declaration | `!!` sites in the converted suite |
|---|---|---|
| `readFromJsonObject(): EpisodeAction?` | `EpisodeAction.kt:200` | **15** — 21 call sites, of which 6 are already inside `assertNull(…)` and need no forcing |
| `writeToJsonObject(): JSONObject?` | `EpisodeAction.kt:74` | **8** assignments (the 2 further uses at `EpisodeActionJsonTest:340,349` are method references inside `assertThrows` and dereference nothing) |
| `timestamp: Date?` | `EpisodeAction.kt:20` | **5** — the `.getTimestamp().getTime()` chains at `EpisodeActionJsonTest:76,169,413,414`(×2) |

Every one of these is behaviour-preserving: the Java code NPEs at exactly the same point, so `!!` is a faithful transcription, not a hardening. `?.` is not — see Gap 1. Note also that `EpisodeActionCharacterizationTest` needs **no** `!!` at all, because every `EpisodeAction` it handles comes from `Builder.build()`, which is non-null; the nullable-getter assertions there (`assertNull(action.guid)` at `:93`, `:100-101`, `:108`, `:117`, `:171`) transcribe directly and stay non-vacuous.

**Numeric overload resolution in assertions — zero traps, verified exhaustively.** Every integer literal in the suite is matched to an actual of its own width. `started`/`position`/`total` are `Int` (`EpisodeAction.kt:24,28,32`) and are only ever compared against bare integer literals; `EpisodeActionChanges.timestamp`, `SubscriptionChanges.timestamp` and `UploadChangesResponse.timestamp` are all `Long` and are only ever compared against `L`-suffixed literals (`EpisodeActionChangesTest:25`, `SubscriptionChangesTest:27`, `ISyncServiceTest:66,90,100`, `UploadChangesResponseTest:35`, `SyncServiceExceptionTest:74`). The seventeen `Int`/`Int` comparisons shift from `assertEquals(long,long)` in Java to `assertEquals(Object,Object)` in Kotlin — Kotlin performs no implicit `Int`→`Long` widening in overload resolution — with an identical verdict in every case, since `Integer.equals(Integer)` is exact. `assertEquals(-993, action.hashCode())` (`EpisodeActionCharacterizationTest:295`) and the `assertEquals`/`assertNotEquals` hash-code pairs at `:262,288` are the same shift.

**Anonymous-class-over-Kotlin-abstract-class sites — two, both benign.** `ISyncServiceTest:38` and `:49` write `new UploadChangesResponse(n) { }` over the `abstract class` at `UploadChangesResponse.kt:3`. These become `object : UploadChangesResponse(n) {}` and compile cleanly. No `Future<*>`-style generic constraint of the kind Milestone 13 hit; `UploadChangesResponse` has no type parameters.

**Lambdas and method references — four sites, one worth a decision.** Two lambdas (`EpisodeActionCharacterizationTest:73,83`) convert trivially. Two are bound method references, `assertThrows(NullPointerException.class, action::writeToJsonObject)` (`EpisodeActionJsonTest:340,349`), targeting JUnit's void-returning `ThrowingRunnable` from a function returning `JSONObject?`. Kotlin's callable-reference-to-SAM conversion with a discarded non-`Unit` return is the fragile form here; the lambda form `assertThrows(NullPointerException::class.java) { action.writeToJsonObject() }` is unambiguous. Behaviourally identical either way. **These two tests are load-bearing beyond their own file:** D9 designates `writeToJsonObjectWithNullTimestampThrowsNullPointerException` as one of the two checks (alongside `javap -c`) that `formatter.format(this.timestamp)` still binds `DateFormat.format(Date)` and not `Format.format(Any)` — the latter would throw `IllegalArgumentException` from the same public method. That check is a runtime assertion against production bytecode and is fully language-independent, so it survives conversion intact; it must not be softened to `assertThrows(Exception::class.java)`. The sibling test at `:344-350` reaches `!!` row 3 of D9's inventory (`action!!.name`), which throws an `NullPointerException` subclass — so `assertThrows(NullPointerException::class.java)` remains correct and must not be narrowed to an exact-type check.

**Kotlin hard-keyword identifiers — zero.** Swept all nine files for `object`, `val`, `var`, `fun`, `in`, `is`, `when`, `as`, `typealias`, `typeof`, `sealed` used as declared identifiers. None. (Milestone 13 found exactly one, `Object val`; this suite has none.)

**Static/`const` members whose Java-ness is acting as an interop proof** — fully enumerated in Gap 2's table.

**Static imports and structural odds and ends, all mechanical.** Eleven distinct `import static org.junit.Assert.*` members across the suite become plain Kotlin `import org.junit.Assert.assertEquals` etc. `@RunWith(RobolectricTestRunner.class)` → `::class`. `throws NoSuchFieldException` / `throws JSONException` on test methods simply vanish. `field.setAccessible(true)` → `field.isAccessible = true`. `EpisodeAction.class.getField(…)` → `EpisodeAction::class.java.getField(…)`. `for (String s : new String[]{…})` (`EpisodeActionCharacterizationTest:216`) → `for (s in arrayOf(…))`. The three `private static` helpers in `EpisodeActionCharacterizationTest:40-50` and four in `EpisodeActionJsonTest:37-57` land in a `companion object` under raw J2K — a style call for the `kotlin-j2k-style` skill, not a correctness one. **Zero** static/instance initializer blocks.

**New gate the developer must clear.** `ktlint` is applied at `net/sync/service-interface/build.gradle:4` and `ktlintTestDebugSourceSetCheck` / `ktlintTestReleaseSourceSetCheck` exist but are vacuous today (no Kotlin test sources). After conversion they become live over 1,179 LOC. `./gradlew :net:sync:service-interface:ktlintCheck` should be an explicit acceptance criterion, not an afterthought.

**Precedent is directly available.** Milestone 13 converted all six of `:net:download:service-interface`'s test files to Kotlin, including three that use `@RunWith(RobolectricTestRunner::class)` — so Kotlin + Robolectric under plain JUnit 4 is already proven in this portfolio and this repo, and needs no spike here.

#### Track prerequisites

- **`kotlin`: no prerequisites, and all of them are met anyway.** Production code in this module is already 100% Kotlin (Milestone 11 / PR #17, merged), the Kotlin toolchain and ktlint are already wired into `net/sync/service-interface/build.gradle`, the test suite is green on both variants at HEAD, and the Kotlin+Robolectric pattern this suite needs is already precedented in the sibling module. The only thing standing between this milestone and a mechanical conversion is the **scoping decision** in Unknown 1 — which is a decision, not a blocker, and belongs to the planner and to José, not to me.

No other track was requested; none is assessed.

### Unknowns

1. **Do all nine files convert, or does any file stay Java?** This is the milestone's central question and it needs a written decision, not a default. The inherited rule is `:event` M9's bright line, restated for this module at `tasks/antennapod-net-sync-service-interface-kotlin.md:655` and hardened into `README.md:52-59`: *any test that cannot be hosted in Kotlin without weakening the interop proof stays Java.* Applied literally that rule retains four files. Applied to what I actually measured (Gap 2's table) it retains at most **one** — `ISyncServiceTest`, the only file that loses both of its guards with nothing surviving in-module — and even that one has the strongest external duplication in the table (two Java implementors, `:app:assembleDebug`). Milestone 13 faced the identical question and chose to convert everything while re-proving the lost shapes mechanically via `javap` acceptance criteria; that option is available here and would cover conventions #4, #5, #6a and #6b at zero file cost. The planner should pick between: convert all nine (+ `javap` re-proof), or hold `ISyncServiceTest` back. I am deliberately not choosing.
2. **Does README convention #9 get rewritten, and by whom, in the same PR?** It currently reads as a standing prohibition ("must stay Java"). Whatever Unknown 1 decides, #9 becomes false or badly misleading the moment any file converts, and `AGENTS.md` requires proactively updating a module's README when a long-term-stable convention changes. Convention #8 (unflavoured test tasks) stays true and should be left alone — it is the thing that caught this task file's own header error.
3. **Is the `?.`-vs-`!!` hazard at `EpisodeActionJsonTest:149,159,179` (Gap 1) worth its own acceptance criterion?** It is the only silent-failure mode on this milestone. Options: an explicit AC forbidding `?.` on any `readFromJsonObject`/`writeToJsonObject` result; a post-conversion grep; or a stated `!!` count for the converted suite mirroring Milestone 11's D9 inventory discipline (`!!` count ≈ 28, with any deviation a review finding). The last is closest to this portfolio's established practice.
4. **Are the two `assertThrows(…, action::writeToJsonObject)` sites converted as method references or as lambdas?** Behaviourally identical; the lambda form avoids a SAM-conversion edge case. Trivial, but it should be decided once rather than per-site, since Milestone 11 designated one of these two tests as a named check on a real overload-resolution fork.
5. **Does the milestone re-verify the D9 `javap -c` binding after conversion?** The check is on production bytecode, which this milestone does not touch, so re-running it is arguably redundant. Cheap enough that the planner may want it in the final verification matrix anyway, given that the test asserting the same thing is being rewritten in the same PR.

### Sources

**Module under conversion**
- Nine test files, all `net/sync/service-interface/src/test/java/de/danoeh/antennapod/net/sync/serviceinterface/`: `EpisodeActionJsonTest.java` (416 LOC, 28 tests; `!!` sites `:70-252,366,387,409-410`; vacuity risk `:147-149,157-159,177-179`; method refs `:340,349`; `.getTime()` chains `:76,169,413,414`), `EpisodeActionCharacterizationTest.java` (320, 25; reflection guard `:215-223`; equals defect pins `:231-296`; helpers `:40-50`; array loop `:216`), `ISyncServiceTest.java` (102, 4; `throws` guard `:19-56`; anonymous subclasses `:38,49`; interface-typed catch `:75-80`), `SyncServiceExceptionTest.java` (85, 7; subclass guard `:20-28`; reflection `:60-75`), `UploadChangesResponseTest.java` (62, 3; unqualified read `:21-29,39-43`; reflection `:46-61`), `SynchronizationProviderTest.java` (60, 9; null literal `:12-14`), `SynchronizationQueueTest.java` (58, 3; accessors `:21,26,32,41`; null args `:53-56`), `SubscriptionChangesTest.java` (39, 2), `EpisodeActionChangesTest.java` (37, 2)
- Production: `EpisodeAction.kt:15-33` (nullable members), `:24,28,32` (`Int` widths), `:39-55` (pinned `equals` defect), `:74` (`JSONObject?`), `:83` (the `format` binding), `:180-190` (`@JvmField` aliases), `:199-200` (`@JvmStatic`, `EpisodeAction?`); `ISyncService.kt:5-23`; `SynchronizationQueue.kt:6-9,24-30`; `SynchronizationQueueStub.kt:21-31`; `UploadChangesResponse.kt:3-8`; `SyncServiceException.kt:3-9`; `SynchronizationProvider.kt:9-11`; `SubscriptionChanges.kt:3-10`; `EpisodeActionChanges.kt:3-9`
- `net/sync/service-interface/README.md:1-6` (purpose), `:20-27` (#1), `:28-31` (#2), `:32-35` (#3), `:36-41` (#4), `:42-46` (#5), `:47-49` (#6), `:44-47` (#7), `:48-51` (#8, unflavoured task names), `:52-59` (#9, the must-stay-Java convention), `:60-63` (#10, Robolectric scope), `:64-72` (#11, the pinned `equals` defect)
- `net/sync/service-interface/build.gradle:1-24` (no `playFlavor.gradle`; ktlint at `:4`; junit/robolectric at `:19,23`)

**Build configuration**
- `common.gradle:47-52` (`-Xlint:all … -Werror` on all `JavaCompile`), `:172-176` (checkstyle sources `src/main/java` Java only)
- `playFlavor.gradle:1-11`; applied by `net/common`, `net/ssl`, `net/discovery`, `net/download/service-interface`, `net/download/service`, `net/sync/service` — **not** by `net/sync/service-interface`
- `./gradlew :net:sync:service-interface:tasks --all` → `testDebugUnitTest`, `testReleaseUnitTest` only; `ktlintTestDebugSourceSetCheck` / `ktlintTestReleaseSourceSetCheck` present
- No `allWarningsAsErrors` in any `*.gradle` in the repo

**External Java consumers (residual interop proofs)**
- `ISyncService` implementors: `net/sync/gpoddernet/src/main/java/de/danoeh/antennapod/net/sync/gpoddernet/GpodnetService.java:46`, `net/sync/gpoddernet/src/main/java/de/danoeh/antennapod/net/sync/nextcloud/NextcloudSyncService.java:30`
- `UploadChangesResponse` subclasses: `…/gpoddernet/model/GpodnetUploadChangesResponse.java:15,23,48` (unqualified inherited read at `:48`), `…/gpoddernet/model/GpodnetEpisodeActionPostResponse.java:14,22`, `…/nextcloud/NextcloudSyncService.java:160-164`
- `SyncServiceException` subclasses: `…/gpoddernet/GpodnetServiceException.java:5-6`, `…/nextcloud/NextcloudSynchronizationServiceException.java:5`
- `EpisodeAction` alias reads: `net/download/service/…/episode/MediaDownloadedHandler.java:114`, `app/…/ui/episodeslist/EpisodeMultiSelectActionHandler.java:111,131`, `storage/database/…/FeedDatabaseWriter.java:132`, `storage/database/…/DBWriter.java:164`
- `readFromJsonObject` callers: `net/sync/gpoddernet/…/mapper/ResponseMapper.java:52` (only Java caller), `net/sync/service/…/SynchronizationQueueStorage.kt:21`
- `SynchronizationQueue` accessor call sites (27, Java): `ui/preferences/…/synchronization/{NextcloudAuthenticationFragment.java:95,99, GpodderAuthenticationFragment.java:85,234, SynchronizationPreferencesFragment.java:97,101,106}`, `storage/database/…/DBWriter.java:163,193,705,873,878`, `storage/database/…/FeedDatabaseWriter.java:138`, `storage/database/src/test/…/FeedDatabaseWriterTest.java:46`, `app/…/ClientConfigurator.java:53`, `app/…/EpisodeMultiSelectActionHandler.java:117,130`, `app/…/activity/MainActivity.java:221`, `playback/service/…/Media3PlaybackService.java:286,515`, `playback/service/…/PlaybackService.java:983,1206`, `net/download/service/…/MediaDownloadedHandler.java:113`, `net/download/service/…/feed/FeedUpdateWorker.java:118`, `net/download/service/src/test/…/{DbCleanupTests.java:88, DbQueueCleanupAlgorithmTest.java:31, DbWriterTest.java:67}`
- `fromIdentifier` callers: `ui/preferences/…/SynchronizationPreferencesFragment.java:120`, `net/sync/service/…/SyncService.kt:352`

**`:model` fixture dependencies**
- `model/src/main/java/de/danoeh/antennapod/model/feed/FeedItem.kt:29,47,56`; `Feed.kt:198` (only 2-arg constructor); `FeedMedia.kt:85`

**Prior-milestone decisions**
- `tasks/antennapod-net-sync-service-interface-kotlin.md:398-431` (D9 — nullability map, three-`!!` inventory, the `DateFormat.format` overload fork), `:433-456` (D10 — `equals` pinned not fixed), `:457` (D12 — tests are Java, Java is the oracle), `:562` (README contents mandated), `:639` (test conversion named as follow-up), `:655` (OQ2 — the bright-line rule and the "probably at least those four" guess this research revises)
- `tasks/antennapod-net-download-service-interface-kotlin-milestone-13.md:23` (separate-invocation `--rerun` discipline), `:25,70,113,159,185,262,275,292,321,327` (the `testWorkConstants` Java-oracle precedent and its `javap` resolution)
- `tasks/antennapod-model-kotlin-future-work.md` item 9 (`EpisodeAction.equals` fix), item 11 (`-Werror` coverage lost as `JavaCompile` goes `NO-SOURCE`)

**Verification run (2026-08-05, `develop` @ `5f816b768`, clean tree, no branch created)**
- `./gradlew --console=plain :net:sync:service-interface:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 83/0/0/0
- `./gradlew --console=plain :net:sync:service-interface:testReleaseUnitTest --rerun` → BUILD SUCCESSFUL, 83/0/0/0
- Per-class XML counts (identical on both variants): `net/sync/service-interface/build/test-results/test{Debug,Release}UnitTest/*.xml` — EpisodeActionJson 28, EpisodeActionCharacterization 25, SynchronizationProvider 9, SyncServiceException 7, ISyncService 4, SynchronizationQueue 3, UploadChangesResponse 3, EpisodeActionChanges 2, SubscriptionChanges 2
- Provenance: `git log` — all nine files first appear 2026-07-28 in `4d73da5b4`, `c368a4118`, `9972f46f1`, `39623f6f4` (Milestone 11 Steps 1-5); no upstream commits touch `net/sync/service-interface/src/test/`

## Plan
_Last updated by: legacy-android-planner | 2026-08-05_

### Objective

Convert `:net:sync:service-interface`'s test source set from Java to Kotlin (`kotlin` track, test-only scope), completing the module's migration begun in Milestone 11. **Eight of the nine files convert; `ISyncServiceTest.java` is held back in Java** (D2) under the inherited Java-oracle bright line, because all four of its tests are javac-oracle tests over a stub declared inside the file itself and it would convert into four green tautologies. No production `.kt` file changes, no test behaviour changes, no test is added, renamed, split, or removed. The 83-test suite must be green on **both** variants before and after with an identical per-class breakdown, the assertion *content* of every converted file must diff clean against its Java original, the two `@JvmStatic` conventions that lose their module-local guard are re-proven mechanically by `javap` (D6), and the three `assertNull` sites that a `?.` softening would silently turn vacuous are proven load-bearing by live falsification (D5) rather than by argument.

### Resolved Decisions

Every Research Unknown (1–5) is resolved below. Three further hazards were found at planning time and are decided here too (D4's extra `!!` site, D11's `ISyncServiceTest` `Int`→`Long` trap, D13's string-concatenation residual). Nothing is left to the developer's judgement, and nothing is deferred to the reviewer.

---

**D1 — One milestone, one PR, four hazard-clustered conversion commits.** (Matches M7 D1, M9 D1, M13 D1.)

Research established that every file is one top-level `public class …Test` with its helpers as private static methods inside the single class that uses them — no shared helper, no shared base class, no file-to-file reference in either direction. The compiler therefore imposes no ordering and no atomicity requirement. Batching is chosen purely for review ergonomics, and the hazards cluster almost disjointly by file:

| Tier | Files | Tests | Asserts | Why grouped |
|---|---|---|---|---|
| A — mechanical + toolchain proof | `SubscriptionChangesTest`, `EpisodeActionChangesTest`, `SynchronizationProviderTest` | 13 | 16 | Zero reflection, zero `!!`, no Robolectric, and no interop guard is traded away — `SynchronizationProviderTest` is the case conversion *strengthens* (README #1). Also the first `.kt` in this source set, so the cheapest possible probe of `compileDebugUnitTestKotlin` / ktlint / lint before anything risky rides on the answer |
| B — interop-guard cluster | `SyncServiceExceptionTest`, `UploadChangesResponseTest`, `SynchronizationQueueTest` | 13 | 19 | All three surviving reflection guards (README #7, #2, and the `open` subclass guard), plus the one place a convention's in-module guard actually dies (#6a, the `@JvmStatic` queue accessors), plus the suite's only `!!` outside `EpisodeActionJsonTest` (D4) |
| C — Robolectric + `:model` fixtures | `EpisodeActionCharacterizationTest` | 25 | 50 | Alone: the Robolectric probe, the `@JvmField` alias reflection guard (README #3), the seven D10 `equals`/`hashCode` defect pins, the two `assertThrows` lambdas, the three-site string-concatenation residual (D13), and every `:model` fixture call. **Zero `!!`** |
| D — the nullability tax | `EpisodeActionJsonTest` | 28 | 72 | Alone: all 28 `!!` (D4), the three vacuity sites and their falsification (D5), both `assertThrows` callable references (D7), and the golden UTC wire-format strings |

Tier A is deliberately first even though Tier D is the decision-heavy one: a **mixed Java+Kotlin test source set** is an assumption every later step depends on — and unlike Milestone 13, this milestone does not merely pass through that state, it *ends* in it (D2). It costs one small commit to prove rather than assume.

`ISyncServiceTest.java` is in no tier. It is not edited (D2, AC18).

---

**D2 — Eight files convert to Kotlin. `ISyncServiceTest` stays Java under the Java-oracle bright line.** (Research Unknown 1, Gap 2. This is the milestone's central decision.)

Milestone 11's OQ2 (`tasks/antennapod-net-sync-service-interface-kotlin.md:655`) sets the inherited rule and `:event` Milestone 9's D2 states it precisely: *a test whose oracle is "javac accepts this call shape" stays Java, and the **file** is the unit of language choice.* Research measured README convention #9's four named files rather than accepting them, and the measurement is what decides this — three of the four convert safely, one does not.

**The three README #9 files that convert.** Each keeps a guard that is language-independent, and in two cases a stronger one than the README claims:

- `SyncServiceExceptionTest` — the `open` guard survives (a Kotlin nested subclass still requires `open` and still requires both `super(…)` constructor forms), and the `serialVersionUID` guard is *reflection*, which is language-independent. Convention #7's only guard anywhere in the repo is that reflection test, and it survives intact.
- `UploadChangesResponseTest` — the unqualified-inherited-read compile guard is genuinely lost (a Kotlin subclass reads the inherited property unqualified whether or not `@JvmField` is present), but it is traded for a guard the README never credited: `:46-61` asserts by reflection that `timestamp` is `public`, `final`, non-`static` **and that no `getTimestamp()` accessor exists** — which is the complete `@JvmField` proof, strictly stronger than the compile guard it replaces.
- `SynchronizationQueueTest` — the null-argument stub calls are *strengthened*: from Java, tightening `enqueueFeedAdded(String?)` to non-null still compiles and fails only at runtime via `Intrinsics`; from Kotlin, `stub.enqueueFeedAdded(null)` is a compile error. Same mechanism as convention #1 in `SynchronizationProviderTest`. What this file *does* lose is convention #6a's guard — `getInstance()`/`setInstance()` become `SynchronizationQueue.instance` in Kotlin, which compiles with or without `@JvmStatic`. That is a real loss and D6 re-proves it.

**The one file that does not convert, `ISyncServiceTest`.** It loses both of its guards (conventions #4 `@Throws` and #5 `@JvmSuppressWildcards`) with nothing surviving in-module — and, decisively, it has **no behavioural remainder to survive with**. `ISyncService` is a bare interface with no implementation in this module, so all four of its tests exercise `TestSyncService`, a stub declared inside the test file itself:

- `implementorCanBeInvokedThroughTheInterfaceType` asserts `changes.timestamp == 100L` — a value its own nested stub just constructed.
- `checkedExceptionIsCatchableThroughTheInterfaceType` asserts a message its own stub just threw; the non-stub half of that assertion (`SyncServiceException` round-trips its message) is already covered assertion-for-assertion by `SyncServiceExceptionTest.messageConstructorRoundTripsMessage`.
- `uploadSubscriptionChangesAcceptsListOfStrings` and `uploadEpisodeActionsAcceptsListOfEpisodeAction` assert `response.timestamp == 2L` / `== 1L` — the sums its own stub computed from the list sizes it was handed.

Strip the compile-time oracle and **all four tests reduce to the stub asserting itself.** That is the same *rule* `:event` M9 applied when it kept `PublicFieldInteropTest` — "does this file, in Kotlin, still prove anything unique?" — but it is not the same *mechanism*, and the difference is worth stating precisely rather than borrowing the precedent's authority wholesale (*corrected in Revision 1; the original text claimed the two were "exactly the shape"*). `PublicFieldInteropTest`'s eight tests construct **real production objects** and read their genuine field values; after losing the field-syntax compile guard its assertions still check real behaviour, and M9 kept it because that behaviour turned out to be assertion-for-assertion **duplicated** by four other `:event` files that were converting anyway — redundant-but-real. `ISyncServiceTest` is a starker case: there is no surviving behavioural content to be redundant *with*, because the object under test is a stub the test file itself authored. Both files fail M9's question; they fail it for different reasons, and this one fails it harder.

What M13's `DownloadServiceInterfaceTest` shows is the same rule pointing the other way: 10 of its 11 tests were language-independent behavioural characterization against real production classes, which is why the identical rule converted it. Converting `ISyncServiceTest` would buy four green tautologies that read as coverage — the specific failure mode the rule exists to prevent.

**The counter-argument, stated and answered.** Conventions #4 and #5 have the strongest external duplication in Research's whole table: two Java implementors, `GpodnetService.java:46` and `NextcloudSyncService.java:30`, both class-compiled by `:app:assembleDebug`. True — and it is why converting this file would not leave the *conventions* unguarded. But that answers a different question. The rule does not ask "is the convention guarded elsewhere?"; it asks "does this file, in Kotlin, still prove anything?" For `ISyncServiceTest` the answer is no, and a strong external residual is a reason not to *worry* about the loss, not a reason to manufacture four tautologies. Milestone 13's D2 turned on the converted file retaining 10 live tests; that premise is absent here.

**Two corroborating consequences, neither of them decisive on its own:**
1. `compileDebugUnitTestJavaWithJavac` keeps executing, so `common.gradle:47-52`'s `-Xlint:all … -Werror` keeps covering one file in this module rather than nothing at all. This is the same narrower-gap outcome `:event` M9 recorded in future-work item 3, and D17 records it there again.
2. It sidesteps a trap Research did not flag and this plan found by reading the file: `ISyncServiceTest:38` and `:49` write `new UploadChangesResponse(addedFeeds.size() + removedFeeds.size()) { }` and `new UploadChangesResponse(queuedEpisodeActions.size()) { }` against `UploadChangesResponse(@JvmField val timestamp: Long)`. Java widens `int`→`long` silently; Kotlin does not widen an `Int` *expression*, so both would be compile errors requiring a `.toLong()` inserted into a characterization fixture. Loud, not silent — so this is a footnote, not a reason. It is recorded because a future session revisiting D2 will meet it.

**Rejected alternatives:**

- **Convert all nine and re-prove #4/#5 by `javap`, as M13 did for its lost shapes.** Rejected, and the reason is specific: `javap` can show `ISyncService`'s methods carry a `Exceptions:` attribute and non-wildcarded `List` signatures, so the *convention* would be re-proven — but it cannot restore the file's reason to exist. M13's `javap` criterion replaced a lost proof in a file that kept 10 live tests; here it would replace the file's *only* proof, leaving four tautologies behind as the visible artifact. Paying a file to keep a proof is cheaper than paying four fake tests to lose one.
- **Split `ISyncServiceTest`, converting the "behavioural" tests and keeping the guard in Java.** Rejected on M9's rule — the file is the unit of language choice — and because there are no behavioural tests to split off; see the four-test analysis above.
- **Hold all four README #9 files back.** Rejected: it is the README's guess, not a measurement, and Research disproved it for three of the four. `SyncServiceExceptionTest` and `SynchronizationQueueTest` would be held back for guards that survive or strengthen, and `UploadChangesResponseTest` for a weak compile guard it replaces with a stronger reflection guard. That is cargo-culting a convention past the evidence that revised it.
- **Delete `ISyncServiceTest` as low-value.** Not seriously considered, but stated so it is not proposed later: its compile-time value is real *today*, and deleting tests is not something a conversion milestone does.

**Disclosed residual, stated plainly rather than discovered later.** After this milestone, conventions **#6a** and **#6b** have no module-local guard. #6a (`SynchronizationQueue.getInstance()`/`setInstance()`) is backed by **27** external Java call sites across five modules, all compiled by `:app:assembleDebug` — very strong. #6b (`EpisodeAction.readFromJsonObject`) is backed by exactly **one** Java caller, `ResponseMapper.java:52` in `:net:sync:gpoddernet` — thin, and it expires the day that module converts. Both are re-proven at this milestone's boundary by D6 and recorded in the README by D3; #6b's expiry is raised as **OQ2**. What is paid on #6a is guard *latency and locality*, not guard existence: the failure moves from a five-second module test task to `:app:assembleDebug`, which is slower and names a different module in its output.

---

**D3 — `net/sync/service-interface/README.md` convention #9 is rewritten. Conventions #1–#8, #10 and #11 are untouched.** (Research Unknown 2.)

Convention #9 currently reads as a standing prohibition — *"is Java by design and must stay Java"* — and names four files as compile-time guards. It becomes false the moment three of those four convert, and `AGENTS.md` requires proactively updating a module's README when a long-term-stable convention changes. Leaving it would instruct the next contributor to undo this milestone.

The replacement is phrased as a long-term-stable convention with **no milestone number, no task-file reference, and no task-specific detail**, and must state four things:

1. The test source set is Kotlin **except `ISyncServiceTest.java`**, which stays Java because its four tests' entire oracle is javac's acceptance of `throws SyncServiceException` on six overrides and of non-wildcarded `List<String>` / `List<EpisodeAction>` parameters — a property only a Java suite can have. It is the live compile-time guard for conventions #4 and #5, and a Kotlin rewrite of it would assert only what its own nested stub does.
2. Conventions #1, #2, #3, #7 and the `open`-ness of `SyncServiceException` are guarded by tests that survive the conversion — by reflection (#2, #3, #7), by Kotlin's own null-checking (#1), or by the language requiring `open` for a subclass. Naming which mechanism guards which is the point: it tells a future editor what will actually break.
3. Conventions **#6a and #6b are no longer guarded by any test in this module.** #6a is guarded by 27 external Java call sites and #6b by exactly one (`ResponseMapper`); when changing either, run `javap -p` on the built classes, and treat #6b as unguarded once `:net:sync:gpoddernet` is Kotlin.
4. Test tasks are unflavoured and each variant needs its own `./gradlew … --rerun` invocation — a **cross-reference to the existing convention #8, not a rewrite of it.** #8 stays byte-for-byte as it is; it is the convention that caught this task file's own header error and it is still true.

Convention #10 (Robolectric scope) stays true and untouched — this milestone changes neither which two files use Robolectric nor why. Convention #11 (the pinned `equals` defect) stays untouched; D10's pins are transcribed, not altered.

---

**D4 — The `!!` inventory is exactly 29 occurrences. Every `readFromJsonObject`/`writeToJsonObject` return — the 23 sites that carry Gap 1's vacuity risk — is forced at the *assignment* site, never at the assertion. The other 6 force a non-null receiver's own nullable member inline, which carries no vacuity risk.** (Research Unknown 3, Gap 1, and the `kotlin`-track null-safety table. Carries Milestone 11's D9 inventory discipline forward. *Scope of the forcing rule narrowed in Revision 1 — the original wording claimed all 29 sites force at an assignment, which this Decision's own table contradicts for 6 of them.*)

Milestone 11's D9 chose `readFromJsonObject(): EpisodeAction?` and `writeToJsonObject(): JSONObject?` deliberately, so the converted suite must force. The counted, binding inventory — any `!!` not on this table is a review finding, and a count of 28 or 30 is equally a finding:

| File | Site | Count | Form |
|---|---|---|---|
| `EpisodeActionJsonTest` | `readFromJsonObject(…)` assigned to a local (21 call sites, minus the 6 already inside `assertNull(…)`) | **15** | `val action = EpisodeAction.readFromJsonObject(json)!!` |
| `EpisodeActionJsonTest` | `writeToJsonObject()` assigned to a local (`:272,293,306,318,329,365,386,409`) | **8** | `val json = action.writeToJsonObject()!!` |
| `EpisodeActionJsonTest` | `timestamp!!.time` (`:76`, `:169`, `:413`, `:414` ×2) | **5** | `roundTripped.timestamp!!.time` |
| `SyncServiceExceptionTest` | `fromCause.cause!!.message` (`:83`) | **1** | `assertEquals("cause-form", fromCause.cause!!.message)` |
| **Total** | | **29** | |

The last row was **not** in Research's inventory, which stated all `!!` were in `EpisodeActionJsonTest`. It was found at planning time: `Throwable.cause` is `Throwable?` in Kotlin, so `fromCause.getCause().getMessage()` cannot transcribe without forcing. It is a faithful transcription — Java NPEs at exactly the same point — and it is included in the count so AC12 is not tripped by it.

**The forcing rule applies to rows 1 and 2 — the 23 sites where a nullable *return* flows into a local — and that is what makes Gap 1 structurally hard rather than merely forbidden.** The alternative — leaving the local nullable and forcing at each dereference — produces roughly fifty `!!` instead of twenty-three and, far worse, puts a `!!`/`?.` choice at *every assertion site*, including the three vacuity sites. Forcing once at the assignment means:

- `val action = EpisodeAction.readFromJsonObject(json)!!` then `assertNull(action.timestamp)` — the assertion still proves "parsing succeeded **and** the timestamp is null", exactly as the Java did, and the NPE-on-regression moves from the first dereference to the assignment line. Same verdict, one line earlier.
- The receiver at the assertion is a **non-null** local, so `action?.timestamp` is no longer a silent alternative — it is a Kotlin `UNNECESSARY_SAFE_CALL` warning. That is not a hard gate (Gap 3: no `allWarningsAsErrors` anywhere in this build), which is why D5 exists on top of it; but it converts "invisible" into "visible in the compiler output", and it removes the idiom a J2K-cleanup pass would reach for.

**Rows 3 and 4 — the remaining 6 sites — force inline inside an assertion argument, and that is correct rather than an exception grudgingly allowed.** `action.timestamp!!.time` and `fromCause.cause!!.message` dereference a nullable *member* (`Date?`, `Throwable?`) of a receiver that is already non-null; there is no intermediate local to force at, and manufacturing one (`val t = action.timestamp!!`) would add a statement the Java never had, against `AGENTS.md`'s minimal-diff rule. **They carry no vacuity risk, and the reason is structural, not incidental:** all six sit inside `assertEquals`/`assertNotEquals` against a specific non-null expected value, never inside `assertNull`. Softening one to `?.` yields `expected:<1641092645000> but was:<null>` — a visibly failing assertion, not a silently passing one. Gap 1's mechanism *requires* the assertion to be an `assertNull`, which is exactly why D5's falsification targets the three `assertNull` sites and nothing else. `?.` remains forbidden at all six anyway (AC12) because it would be an unfaithful transcription of code that NPEs today — but the failure mode there is a noisy one, and the plan should not claim otherwise.

**Forbidden throughout the suite, none of which any compiler will catch:** `?.` on any `readFromJsonObject` or `writeToJsonObject` result; `?:` fallbacks; `?.let`; `orEmpty()`; and `checkNotNull(…)`/`requireNotNull(…)` as an M13-style substitute for `!!` — here they would change the thrown type on a real characterization path from `NullPointerException` to `IllegalStateException`, and `EpisodeActionJsonTest:337-350` asserts `NullPointerException` specifically. AC11 pins the count and AC12 pins the absence.

---

**D5 — The three vacuity sites are proven load-bearing by live falsification, run once and recorded, not by assertion.** (Research Unknown 3 / Gap 1 — the milestone's only silent-failure mode. Analogous to Milestone 11's D9 "fully discriminating" column, made executable.)

Milestone 11's D9 required each `!!` to be pinned by a *discriminating* test — one that fails if the `!!` is softened. This milestone inherits that obligation on the read side, where three tests at `EpisodeActionJsonTest:144-150`, `:152-160` and `:172-180` assign `readFromJsonObject`'s result and then pass a property of it to `assertNull`. D4's assignment-site forcing is the structural defence; this is the proof that it works.

**Procedure, run once at Step 5, on the working tree, with all six outcomes pasted into Implementation Notes and every mutation reverted before the step closes.** For each of the three tests — `readFromJsonObjectAbsentTimestampLeavesTimestampNull`, `readFromJsonObjectUnparseableTimestampIsSwallowedAndLeavesTimestampNull`, `readFromJsonObjectEmptyGuidLeavesGuidNull`:

- **Mutation M (the null-producing fixture).** Replace that test's `baseJson("play")` with a `JSONObject` carrying `"episode"` and `"action"` but **no** `"podcast"`, which `EpisodeAction.kt:204-206` returns `null` for. Nothing else in the test changes.
- **Run 1 — shipped form + M.** With `val action = EpisodeAction.readFromJsonObject(json)!!` and `assertNull(action.timestamp)`, the test **must FAIL** with a `NullPointerException` from the `!!`. This is the proof that the assertion is load-bearing on parse success.
- **Run 2 — softened form + M.** With `val action = EpisodeAction.readFromJsonObject(json)` and `assertNull(action?.timestamp)`, the test **must PASS**. This is the demonstration that the softening is vacuous *on this codebase*, not in theory.

Six outcomes, three FAIL and three PASS, each recorded with its mutation diff and its JUnit result line. **If any Run 1 passes or any Run 2 fails, stop the step** — the mutation is not producing a null and the exercise proved nothing; fix the fixture and redo. Then revert all mutations and re-run `EpisodeActionJsonTest` clean: 28/28 green.

This costs six short test runs and it is the only evidence on this milestone that distinguishes "the suite is green" from "the suite still checks what it checked". Rejected alternatives: a post-conversion `grep` for `?.` (catches the idiom, not the vacuity, and would have to allow legitimate `?.` elsewhere), and an acceptance criterion that merely forbids `?.` at those lines (unfalsifiable — it asserts the developer's intent rather than testing the code). The `!!`-count criterion (AC11) is kept **in addition**, not instead: it is cheap and it catches drift the falsification cannot see.

---

**D6 — Conventions #6a and #6b are re-proven mechanically by `javap`, as an acceptance criterion, and Milestone 11's D9 `javap -c` binding check is re-run in the same sweep.** (Research Unknown 1's consequence, Gap 2 rows #6a/#6b, and Research Unknown 5; carries M13's D4 forward.)

At Step 6 the developer runs `javap` against the compiled `:net:sync:service-interface` `debug` classes and pastes the output into Implementation Notes. Required findings (AC19 for the six `javap -p` rows, AC20 for the `javap -c` row):

| Class | Required `javap` evidence | What it proves |
|---|---|---|
| `SynchronizationQueue` | `javap -p`: `public static SynchronizationQueue getInstance();` and `public static void setInstance(SynchronizationQueue);` **on the outer class**, not only on `SynchronizationQueue$Companion` | README #6a — the shape `SynchronizationQueueTest` stops proving; 27 external Java call sites depend on it |
| `EpisodeAction` | `javap -p`: `public static EpisodeAction readFromJsonObject(org.json.JSONObject);` on the outer class | README #6b — the shape `EpisodeActionJsonTest` stops proving; exactly one external Java caller left |
| `SynchronizationProvider` | `javap -p`: `public static SynchronizationProvider fromIdentifier(java.lang.String);` on the outer class | README #6, the enum half; the in-module guard for #1 *strengthens*, but the `@JvmStatic`-ness is guarded the same way #6a is |
| `EpisodeAction` | `javap -p`: `public static final EpisodeAction$Action NEW;` and likewise `DOWNLOAD`, `PLAY`, `DELETE` | README #3 — belt-and-braces; `EpisodeActionCharacterizationTest:215-223`'s reflection test already survives and is the primary guard |
| `UploadChangesResponse` | `javap -p`: `public final long timestamp;` and **no** `getTimestamp()` in the method list | README #2 — belt-and-braces; `UploadChangesResponseTest:46-61` survives and is the primary guard |
| `SyncServiceException` | `javap -p`: `private static final long serialVersionUID;` | README #7 — belt-and-braces; `SyncServiceExceptionTest:60-75` survives and is the sole guard anywhere in the repo |
| `EpisodeAction.writeToJsonObject` | **`javap -c`**: `invokevirtual … java/text/DateFormat.format:(Ljava/util/Date;)Ljava/lang/String;` — **not** `java/text/Format.format:(Ljava/lang/Object;)…` | Milestone 11 D9's overload fork |

**The last row is Research Unknown 5, and the answer is yes, re-run it.** The check reads production bytecode, which this milestone does not touch, so it is expected to be redundant — but the *test* that asserts the same fork behaviourally (`writeToJsonObjectWithNullTimestampThrowsNullPointerException`) is being rewritten in this PR, and D7 decides the exact syntax of the call site that triggers it. One `javap -c` costs seconds and turns "the rewritten test still discriminates the same fork" from an assumption into a recorded observation. If it unexpectedly shows `Format.format(Object)`, that is a **pre-existing** property of `EpisodeAction.kt` rather than anything this conversion caused: record it, do not fix it, and stop the step for a decision.

**`ISyncService` is deliberately absent from this table.** Conventions #4 and #5 need no `javap` re-proof because `ISyncServiceTest.java` still guards them at compile time (D2). That is the concrete return on holding the file back.

This is a **milestone-boundary snapshot, not a regression gate** — nothing prevents a future PR from regressing a member and deleting its last Java caller in the same commit. Promoting it into an automated check is **OQ1**, third recurrence.

---

**D7 — The two `assertThrows` callable references stay callable references. The two `assertThrows` lambdas stay lambdas.** (Research Unknown 4.)

`EpisodeActionJsonTest:340,349` are `assertThrows(NullPointerException.class, action::writeToJsonObject)`; `EpisodeActionCharacterizationTest:72-73,82-83` are `assertThrows(NullPointerException.class, () -> new EpisodeAction.Builder(…))`. Both forms transcribe in place:

```kotlin
assertThrows(NullPointerException::class.java, action::writeToJsonObject)
assertThrows(NullPointerException::class.java) { EpisodeAction.Builder(item, EpisodeAction.Action.PLAY) }
```

Decided once rather than per-site, per Research's request. Reasons: JUnit 4's `ThrowingRunnable.run()` returns `void`, and Kotlin has supported coercing a value-returning callable reference into a `Unit`-expecting SAM position since 1.4 — this repo is on 2.3.20 (`gradle/libs.versions.toml:3`) — so the fragile form is supported; and keeping it canonicalizes clean against the Java in D10's audit, whereas a lambda rewrite would produce a disclosed residual at the two sites the milestone can least afford noise on.

**Permitted fallback, one only:** if the callable reference fails to compile, use `assertThrows(NullPointerException::class.java) { action.writeToJsonObject() }`, disclose it under Step 5, and record the resulting D10 residual. Settled empirically at Step 5 either way.

**Both `EpisodeActionJsonTest` sites are load-bearing beyond their own file and must not be weakened.** `writeToJsonObjectWithNullTimestampThrowsNullPointerException` is one of the two checks Milestone 11's D9 designated for the `DateFormat.format` fork (the other is D6's `javap -c`), and `IllegalArgumentException` escaping the same public method is the failure it exists to catch — so it must **not** be relaxed to `assertThrows(Exception::class.java)`. Its sibling at `:344-350` fires from `EpisodeAction.kt:36`'s `action!!.name`, which throws an `NullPointerException` subclass, so `NullPointerException::class.java` remains correct and must **not** be narrowed to an exact-type check.

---

**D8 — Every zero-argument Java getter on a Kotlin declaration becomes a property; every other call stays a call. The table is binding.**

Misclassification is a compile error in one direction and a silent no-op in the other, so this is diff volume rather than correctness risk — but the developer does not re-derive it per site.

**Become properties** (`action.getPodcast()` will not compile): on `EpisodeAction` — `podcast`, `episode`, `guid`, `action`, `timestamp`, `started`, `position`, `total`; on `SubscriptionChanges` — `added`, `removed`, `timestamp`; on `EpisodeActionChanges` — `episodeActions`, `timestamp`; on `SynchronizationProvider` — `identifier`; on `UploadChangesResponse` — `timestamp` (already a field, unchanged syntax); on `:model` — `FeedItem.feed`, `FeedItem.media`, `FeedItem.itemIdentifier` (all **write** side: `item.feed = …`).

**`SynchronizationQueue`'s accessors become property syntax, and this is where convention #6a's in-module guard dies** (D2, D6): `SynchronizationQueue.getInstance()` → `SynchronizationQueue.instance`, and `SynchronizationQueue.setInstance(x)` → `SynchronizationQueue.instance = x`, at `SynchronizationQueueTest:21,26,32,34,39,41`. The backing declaration is `@JvmStatic var instance: SynchronizationQueue?` (`SynchronizationQueue.kt:7-8`); the Kotlin call sites compile identically with or without `@JvmStatic`.

**Stay function calls:** `EpisodeAction.readFromJsonObject(json)`, `action.writeToJsonObject()`, `action.toString()`, `action.hashCode()`, `SynchronizationProvider.fromIdentifier(…)`, `SynchronizationProvider.values()` (D9), every `EpisodeAction.Builder` method, and every `java.lang.reflect` call (`getField(name)`, `getDeclaredField(name)`, `getLong(null)`, `isAccessible`, `Modifier.isPublic(…)`).

**Java-library zero-argument getters** (`Field.getModifiers()`, `Class.getMethods()`, `Method.getName()`, `Throwable.getMessage()`, `Throwable.getCause()`) compile in **both** syntaxes. Policy: whichever form J2K emits is accepted, because D10's extractor canonicalizes both to the same text — so this choice cannot produce a residual and is not worth a review argument. The one exception is `UploadChangesResponseTest:56`'s `method.getName().equals("getTimestamp")`, which J2K will render as `method.name == "getTimestamp"`; that is behaviourally identical for `String` and is **accepted**, but it sits inside a `for` loop where the extractor is blind, so AC13 pins it by eye.

---

**D9 — `SynchronizationProvider.values()` is kept; `entries` is forbidden. The array literal becomes `arrayOf(…)`.**

`SynchronizationProviderTest:53-59` asserts `values()` returns both constants in declaration order. `values()` still resolves in Kotlin and is what the Java asserted; swapping to `entries` would change the asserted API from an array to an immutable `List` and change what the test proves — a characterization edit, not a transcription. The `new SynchronizationProvider[] { GPODDER_NET, NEXTCLOUD_GPODDER }` argument becomes `arrayOf(SynchronizationProvider.GPODDER_NET, SynchronizationProvider.NEXTCLOUD_GPODDER)` (there is no other Kotlin spelling), and D10's extractor carries a rule that canonicalizes the two forms together so this does not surface as a residual.

Same file, adjacent and decided here so it is not improvised: `SynchronizationProvider.fromIdentifier(null)` at `:13` transcribes as-is. This is Research's *strengthened* case — from Kotlin it is a compile error if the parameter is ever tightened — and it needs no `!!`, no cast, and no `as String?`.

---

**D10 — Assertion content is verified by a mechanical per-file 1:1 diff against the Java original, for all 8 converted files. Empty residual required.** (Carries M7 D18 / M9 D10 / M13 D15 forward, adapted to this suite.)

A green suite with an unchanged test count does not prove the assertions still say what they said — two cancelling edits produce identical counts, and this milestone edits every assertion in `EpisodeActionJsonTest` by adding a `!!` somewhere upstream of it. The script is written to the session scratchpad and **is not committed**:

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
    $a =~ s/!!//g;
    $a =~ s/::class\.java/.class/g;
    $a =~ s/\bnew\s+[A-Za-z_][A-Za-z0-9_.]*\s*\[\s*\]\s*\{\s*(.*?)\s*\}/arrayOf($1)/g;
    $a =~ s/\.get([A-Z])([A-Za-z0-9_]*)\(\)/"." . lc($1) . $2/ge;
    $a =~ s/\.(is[A-Z][A-Za-z0-9_]*)\(\)/.$1/g;
    $a =~ s/;\s*$//;
    $a =~ s/\)\s*\{\s*(.+?)\s*\}\s*$/, $1)/;
    $a =~ s/,\s*\(\s*\)\s*->\s*/, /;
    $a =~ s/\bnew\s+(?=[A-Z][A-Za-z0-9_.]*\s*\()//g;
    $a =~ s/\s+/ /g;
    $a =~ s/\(\s+/(/g;
    $a =~ s/\s+\)/)/g;
    print "$a\n";
}
```

Three rules are new or adapted for this module and are the reason it is not M13's script verbatim:
- **`!!` is stripped.** Non-negotiable: without it, 28 of the 72 lines in `EpisodeActionJsonTest` would residual on the very thing the milestone is *supposed* to change, drowning any real defect. Stripping it is what makes the audit blind to the nullability tax and sharp on everything else — which is exactly why D5 and AC11 exist to cover the blind spot from the other side.
- **`new X[] {…}` → `arrayOf(…)`** — normalizes `SynchronizationProviderTest:54-58` (D9).
- **The `new` stripper accepts dotted type names** (`[A-Za-z0-9_.]*`), so `new EpisodeAction.Builder(…)` canonicalizes against Kotlin's `EpisodeAction.Builder(…)` at the four `assertThrows` sites. M13's version anchored on an undotted name and would silently fail to strip these.

**Validation before use, at Step 1:** for each of the 8 converting files, the extractor's output line count must equal that file's `grep -cE '\bassert[A-Z][A-Za-z]*\('` count — measured at planning time as `EpisodeActionJsonTest` 72, `EpisodeActionCharacterizationTest` 50, `SyncServiceExceptionTest` 11, `SynchronizationProviderTest` 9, `UploadChangesResponseTest` 6, `SubscriptionChangesTest` 4, `EpisodeActionChangesTest` 3, `SynchronizationQueueTest` 2, total **157**. Any mismatch means the extractor is mis-calibrated and must be fixed before Step 2. Confirm specifically at calibration that the four `assertThrows` sites and the one `assertArrayEquals` site canonicalize as this decision predicts. `ISyncServiceTest` is excluded from the audit entirely — it is not converted (D2).

Per-file invocation from Step 2 onward:

```bash
diff <(git show 5f816b768:<java-path> | perl "$SCRATCH/assertlines.pl") \
     <(perl "$SCRATCH/assertlines.pl" < <kotlin-path>)
```

**The required result is an empty residual for all 8 files, with no exceptions.** A non-empty residual **stops the step** — it is not deferred to Step 6. It is recorded verbatim with a one-line pure-syntax justification and accepted or rejected individually by the reviewer. A residual that alters an expected-value literal, swaps expected/actual order, or changes the assertion function itself is a rejection, not a justification opportunity: revert the file and redo it.

**Known blind spots, stated so they are not mistaken for coverage.** The extractor reads assertion call lines only. It does not see: the `!!` it strips (AC11, AC12, D5), the `expected` string locals built above the assertions (D11), the `for` loop bodies in `UploadChangesResponseTest:55-59` and `EpisodeActionCharacterizationTest:216-222` (AC13), the private helper bodies (D12), or the `@Before`/`@After` assignments in `SynchronizationQueueTest` (AC13). AC10 and AC13 are complementary to AC9; neither subsumes the other.

---

**D11 — Golden-string `expected` locals are self-guarding, so no concatenation-vs-template policy is imposed on them. String concatenation *inside an assertion argument* is preserved verbatim.** (Found at planning time.)

Five sites build an `expected` string above the assertion — `SubscriptionChangesTest:36`, `EpisodeActionChangesTest:34`, `EpisodeActionCharacterizationTest:306-307,315-316`, `EpisodeActionJsonTest:278-281`. J2K will render some as templates (`"…added=$added…"`). This is **accepted in either form and needs no criterion**, because each is compared against a value produced by *untouched production code* (`SubscriptionChanges.toString()`, `EpisodeAction.toString()`, the `SimpleDateFormat` output): a mistranscribed expected string fails its own test immediately. The tests guard themselves.

The three sites where it *does* matter are inside an assertion argument: `EpisodeActionCharacterizationTest:219,220,221`'s `assertTrue(fieldName + " must be public", …)` and its `must be static` / `must be final` siblings. These are **preserved as concatenation**, not converted to `"$fieldName must be public"`. Concatenation is valid Kotlin, ktlint's `string-template` rule does not flag it, it is the minimal diff under `AGENTS.md`, and it keeps three lines out of D10's residual for a failure-message string that has no behavioural content whatsoever. Three disclosed residuals for nothing gained is a bad trade.

---

**D12 — Private static helpers and constants become a `private companion object` at the bottom of their own class.** (Carries M13's D17 forward.)

`EpisodeActionJsonTest`'s `PODCAST`, `EPISODE` and its four helpers (`baseJson`, `epochMillisUtc`, `dateAtUtc`, `dateAtUtcWithNanos`, `:34-57`) and `EpisodeActionCharacterizationTest`'s `PODCAST_URL`, `EPISODE_URL`, `validItem` and `dateAtUtc` (`:37-50`) each land as `private const val` / `private fun` inside a `private companion object` in their own class. This preserves the members' class association exactly as Java had it, keeps them callable unqualified from every test method, and matches the precedent set for the sibling module. Private top-level declarations were the alternative and are rejected on that association point alone.

`throws JSONException` on `baseJson` and `throws NoSuchFieldException` / `throws IllegalAccessException` / `throws NoSuchAlgorithmException`-style clauses on test methods simply vanish; the corresponding `import`s must go with them or ktlint's unused-import rule fails the step. `789_000_000` (`EpisodeActionJsonTest:401`) is valid Kotlin unchanged.

---

**D13 — Java collection factories are transcribed verbatim. No `listOf` / `emptyList()` / `arrayListOf` idiomization.**

`Collections.emptyList()`, `Collections.singletonList(x)` and `Arrays.asList(a, b)` appear at `SubscriptionChangesTest:20-21,32-33`, `EpisodeActionChangesTest:20,31` and (in the held-back file) `ISyncServiceTest`. All three resolve in Kotlin with inference from the target parameter type and are kept as they are. Swapping to `listOf(…)` would be a correctness-neutral improvement inside a conversion diff, which `AGENTS.md`'s minimal-diff rule forbids and which this pipeline's whole premise argues against; it would also change the *implementation* class flowing into `SubscriptionChanges.added`, which two golden `toString()` assertions render. AC14 pins zero occurrences of the idiomized forms.

---

**D14 — Test method names are preserved byte-for-byte. No backtick sentence names, and no backticks anywhere.** (Carries M7 / M13 D14 forward.)

All 79 converted names transcribe unchanged. Research swept all nine files for Kotlin hard keywords used as identifiers and found **zero**, so no backtick escape is legitimate anywhere in this module — unlike M13, which had exactly one (`Object val`). M7 established that ktlint's `FunctionNamingRule` selects its permissive test regex for any file importing `org.junit`; every file here does. That precedent is **second-hand for this module and is confirmed empirically, not inherited**: Step 2 runs `ktlintCheck` immediately after the first three `.kt` files land and records the result. If it unexpectedly fails, that is a **hard stop** — renaming characterization test methods is a scope expansion requiring a decision, not a reaction to a red build, and it would break AC4 and the per-class reconciliation.

---

**D15 — ktlint begins gating this source set and the gate is not to be softened.**

`ktlintTestDebugSourceSetCheck` / `ktlintTestReleaseSourceSetCheck` exist today but are vacuous (no Kotlin test sources). They start enforcing on the first `.kt` in `src/test` and will cover ~1,100 LOC by Step 5. `.editorconfig` sets `max_line_length = 120` and `ktlint_code_style = android_studio`; the longest line in the converting set is 114 characters today (`EpisodeActionJsonTest:56`), so several conversions will land close to the limit. Line wraps are made at **argument/comma boundaries only**, with `(` and `)` glued to their adjacent tokens — M7's red-team loop 2 established that any other wrap style produces false-positive residuals in D10's audit. Zero `@Suppress("ktlint:…")` annotations, no `ktlint_disabled_rules`, no `.editorconfig` change, no ktlint filter or exclusion in any build file. CI runs `./gradlew ktlintCheck` repo-wide.

The `kotlin-j2k-style` skill (`services/android-migration/.claude/skills/kotlin-j2k-style/SKILL.md`) is run on each file after J2K and before the file is reported converted — but it does **not** override any decision above. Where the skill's general advice conflicts with D4 (the `!!` inventory and its forcing rule), D8 (`values()`), D11 (concatenation), D13 (Java collection factories) or D14 (names), this Plan wins and the conflict is noted in Implementation Notes.

---

**D16 — `net/sync/service-interface/build.gradle` is not modified and is deliberately excluded from File Scope.** The `kotlin.android` plugin is already applied (`:3`), so the Kotlin test-compile path is wired; Step 1 records `compileDebugUnitTestKotlin`'s presence and current `NO-SOURCE` status rather than assuming it. The suite is JUnit 4 + Robolectric only — **no `kotlin.test`, no `mockito-kotlin`, no new dependency of any kind.** Robolectric's scoping comment at `:21-23` stays byte-for-byte: it says "those two test files", which remains exactly true after conversion. The dead `:storage:preferences` / rx dependencies stay too — future-work item 10 already records them as deliberately deferred, and removing them here would contradict a standing decision inside a conversion diff. Excluding the file from File Scope means a build-config change cannot slip in unnoticed. Same posture as M7 D16, M9 D12, M13 D20.

---

**D17 — The `-Werror` coverage change is recorded as an update to future-work item 3, not as a new item, and it is narrower here than in any prior module.** (Research Gap 3. Research cited "item 11"; the correct item is **3**, `allWarningsAsErrors` for Kotlin test-compile tasks — item 11 is the `SynchronizationQueueStorage` JSON defect.)

Because `ISyncServiceTest.java` stays (D2), `compileDebugUnitTestJavaWithJavac` keeps executing and `-Xlint:all … -Werror` keeps covering one file rather than nothing. That is the same outcome `:event` M9 recorded — a narrower gap, not an equal one — and it is worth stating precisely, because item 3's running narrative now has three distinct shapes across six modules: full coverage retained (`:net:sync:service`), partial (`:event`, and now this module), and none at all (`:model`, `:net:download:service-interface`, and this module's production side). One paragraph appended to item 3. **No new future-work item is filed by this milestone**; nothing new became actionable, and item 10 already carries this module's deferred cleanups.

---

### Steps

Each step is one reviewable diff and leaves the build green. **`--rerun` is mandatory and the two variants are always two separate invocations** — a combined two-task command line re-executes only one of them and reports the other `UP-TO-DATE` (Milestone 13's hard-won lesson). The task names are **unflavoured**; `testFreeDebugUnitTest` / `testPlayDebugUnitTest` do not exist in this module (README convention #8, and the correction Research made to this task file's own header):

```bash
./gradlew --console=plain :net:sync:service-interface:testDebugUnitTest --rerun
./gradlew --console=plain :net:sync:service-interface:testReleaseUnitTest --rerun
```

**Standing obligation on every conversion step (Steps 2–5), per D10.** A step is not complete when the suites go green. For each file the step converts, the developer runs the assertion-content diff against that file's Java original at the merge base and pastes the result under that step's heading in Implementation Notes. The expected result is an **empty** diff for every file, with no exceptions. A non-empty residual stops the step; it is not deferred to Step 6.

**Note on the characterization-tests-first rule.** The pipeline's non-negotiable "Step 1 writes characterization tests" rule is **satisfied, not waived**. Research found no behavioural coverage gap: Milestone 11 authored all nine of these files from scratch as the characterization layer for all nine production files, and they are green on both variants at HEAD. The tests that must exist before conversion already exist. A plan that opened with "write characterization tests first" would be writing tests for tests. Step 1 is therefore baseline *capture* rather than test *authoring* — the same obligation, discharged by the previous milestone. The gap Research actually identified (coverage that exists only *because* the test is Java) is not closable by writing more tests, and is handled by D2, D5 and D6 instead.

**Step 1 — Capture the baseline and stand up the audit tool. No files change.**

Paste into Implementation Notes:

- a. **Merge-base SHA** — `git merge-base HEAD develop`, currently `5f816b768`, working tree clean apart from the two untracked spec files. Every converted `.java` original is destroyed by its rename, so this SHA is the only route back to the pre-conversion text; without it the audit is unreproducible by the reviewer.
- b. **Per-class test counts, both variants, two separate `--rerun` invocations.** For each run record the command verbatim, the `N actionable tasks: M executed` line, and the `build/test-results/<task>/` directory mtime **before and after** — the three-part evidence that `--rerun` actually bit. Then a `classname → tests/failures/errors/skipped` table for all 9 classes from `net/sync/service-interface/build/test-results/testDebugUnitTest/TEST-*.xml` and `…/testReleaseUnitTest/TEST-*.xml`. Expected, identical on both variants: **83 tests, 0 failures, 0 errors, 0 skipped** — `EpisodeActionJsonTest` 28, `EpisodeActionCharacterizationTest` 25, `SynchronizationProviderTest` 9, `SyncServiceExceptionTest` 7, `ISyncServiceTest` 4, `SynchronizationQueueTest` 3, `UploadChangesResponseTest` 3, `EpisodeActionChangesTest` 2, `SubscriptionChangesTest` 2.
- c. **Per-file assertion counts** — `grep -cE '\bassert[A-Z][A-Za-z]*\(' <file>` for each of the 9 files. Expected: 72 / 50 / 11 / 9 / 6 / 4 / 4 / 3 / 2, total **161**; of which **157** are in the 8 converting files and 4 are in `ISyncServiceTest`.
- d. **Toolchain preconditions, observed not assumed** — record that `:net:sync:service-interface:compileDebugUnitTestKotlin` exists and currently reports `NO-SOURCE`, that `ktlintTestDebugSourceSetCheck` is currently `SKIPPED`/`NO-SOURCE`, and that `compileDebugUnitTestJavaWithJavac` currently executes over 9 files.

Then write `assertlines.pl` (D10) to the session scratchpad — **not** to the repo; it is an audit tool, not a deliverable, and is not in File Scope. Validate it before relying on it: its output line count must equal each converting file's grep count from (c), all 8 of them. If any file mismatches, the extractor is mis-calibrated for this codebase and must be fixed before Step 2. Confirm specifically that the four `assertThrows` sites and the one `assertArrayEquals` array-literal site canonicalize as D10 predicts.

**Step 2 — Convert Tier A: `SubscriptionChangesTest`, `EpisodeActionChangesTest`, `SynchronizationProviderTest` (3 files, 13 tests). Also the toolchain proof.**

`git mv` all three to `.kt` and rewrite. Apply D8 (`changes.added` / `changes.removed` / `changes.timestamp` / `GPODDER_NET.identifier` become properties; `fromIdentifier(…)` and `values()` stay calls), D9 (`values()` kept, `arrayOf(…)` for the array literal, `fromIdentifier(null)` transcribed as-is), D13 (`Arrays.asList` / `Collections.singletonList` verbatim), D11 (the two `expected` golden-string locals are accepted in either form), D14 (names byte-for-byte). Run the `kotlin-j2k-style` skill on all three files after J2K and before reporting them converted.

This tier is deliberately first even though it is nearly mechanical: it is the cheapest possible proof that a **mixed Java+Kotlin test source set** compiles and runs on both variants — and unlike Milestone 13, this module *ends* in that state (D2), so it is a property of the delivered result and not a transient. Three checks run here and are recorded in writing:

- `./gradlew :net:sync:service-interface:ktlintCheck` — confirm `ktlintTestDebugSourceSetCheck` is now genuinely **executing**, not `SKIPPED`/`NO-SOURCE` (D15), and that D14's test method names pass unchanged. A name failure is a **hard stop**, not a rename.
- `./gradlew checkstyle lint` — `common.gradle:172-176` sources checkstyle from `src/main/java/**/*.java` only, so checkstyle is unaffected; Android Lint's `checkTestSources` is unset (AGP defaults it false) but `common.gradle` sets `warningsAsErrors true` + `abortOnError true` and CI runs this task. Cheap here, expensive to discover in CI. Record the answer either way.
- Confirm `compileDebugUnitTestJavaWithJavac` **still executes** over the remaining 6 Java files, i.e. `-Xlint:all -Werror` still covers them at this point.

**Step 3 — Convert Tier B: `SyncServiceExceptionTest`, `UploadChangesResponseTest`, `SynchronizationQueueTest` (3 files, 13 tests).**

The interop-guard cluster. Apply D4's row 4 (`fromCause.cause!!.message` — the one `!!` outside `EpisodeActionJsonTest`), D8's `SynchronizationQueue.instance` property rewrites at all six sites plus `previousInstance` as `private var previousInstance: SynchronizationQueue? = null`, and the reflection transcriptions: `SyncServiceException::class.java.getDeclaredField("serialVersionUID")`, `field.isAccessible = true`, `UploadChangesResponse::class.java.getField("timestamp")`, `Exception::class.java.isAssignableFrom(SyncServiceException::class.java)`.

Three specific shapes are pinned because they are what the surviving guards rest on:
- `SyncServiceExceptionTest`'s nested `TestSyncServiceException` keeps **both** secondary constructors calling `super(…)` — this is what still requires `SyncServiceException` to be `open` and to keep both constructor signatures.
- `UploadChangesResponseTest`'s nested `TestUploadChangesResponse(timestamp: Long) : UploadChangesResponse(timestamp)` keeps `describe()` reading the **inherited** `timestamp` unqualified. It no longer discriminates `@JvmField` (Research's finding) and that is accepted — the reflection test at `:46-61` is the real guard and is transcribed intact, including the `getTimestamp()`-must-not-exist half.
- `SynchronizationQueueTest`'s `@Before`/`@After` capture-and-restore is preserved exactly; nothing about the shared-JVM-fork hygiene changes. The nine stub calls with four `null` arguments transcribe as-is and are now compile-checked.

**Step 4 — Convert Tier C: `EpisodeActionCharacterizationTest` alone (1 file, 25 tests, Robolectric).**

`@RunWith(RobolectricTestRunner::class)`. Apply D12 (`PODCAST_URL`, `EPISODE_URL`, `validItem()`, `dateAtUtc()` into a `private companion object`), D8's `:model` write-side property rewrites (`item.feed = Feed(PODCAST_URL, null)`, `item.media = FeedMedia(item, EPISODE_URL, 0L, "audio/mp3")`, `item.itemIdentifier = "guid-item-1"`), D7's two `assertThrows` lambdas, D11's three preserved concatenations at `:219-221`, and `for (fieldName in arrayOf("NEW", "DOWNLOAD", "PLAY", "DELETE"))` with `EpisodeAction::class.java.getField(fieldName)`.

**Zero `!!` land in this file** (D4, AC11): every `EpisodeAction` here comes from `Builder.build()`, which is non-null, and the five nullable-getter assertions (`:93`, `:100-101`, `:108`, `:117`, `:171`) pass the nullable value straight into `assertNull`/`assertNotNull` without dereferencing it. If a `!!` appears here, something was transcribed wrong.

The seven `equals`/`hashCode` tests pin Milestone 11's D10 defect and are transcribed with **no** change to their expected values — including `assertEquals(-993, action.hashCode())` and the `assertEquals(a, b)` / `assertNotEquals(a, b)` pairs that look wrong and are correct. `assertNotEquals(a, null)` and `assertNotEquals(a, "not an episode action")` stay as they are. Per D2 and README #11 this suite pins a known defect; nothing here is "fixed".

**Step 5 — Convert Tier D: `EpisodeActionJsonTest` alone (1 file, 28 tests), and run the D5 falsification.**

The milestone's decision-dense file. Apply D4 (exactly 28 `!!` occurrences: 15 on `readFromJsonObject` assignments, 8 on `writeToJsonObject` assignments — **none of these 23 at an assertion** — plus 5 inline `timestamp!!`, two of which share line 414), D12 (the four helpers and two constants into a `private companion object`), D7 (**settle the callable-reference form empirically here** and record the outcome), D11 (the `expectedTimestamp` local accepted in either form), D8 (`json.getString(…)` / `json.getInt(…)` / `json.has(…)` stay calls; `action.podcast` etc. become properties).

Then run **D5's falsification in full**: three tests × two forms, six recorded outcomes (3 FAIL with `!!`, 3 PASS with `?.`), each with its mutation diff and JUnit result line, then every mutation reverted and `EpisodeActionJsonTest` re-run clean at 28/28. **A Run 1 that passes or a Run 2 that fails stops the step.** This is the only evidence on this milestone that separates "green" from "still checking what it checked", and it is not skippable.

**Step 6 — Whole-suite reconciliation, the full gate set, and the `javap` interop re-proof.**

Run, in this order, and paste all output:
- `./gradlew --console=plain :net:sync:service-interface:testDebugUnitTest --rerun`
- `./gradlew --console=plain :net:sync:service-interface:testReleaseUnitTest --rerun` (separate invocation)
- `./gradlew :net:sync:service-interface:ktlintCheck`
- `./gradlew checkstyle lint`
- `./gradlew :app:assembleDebug`

Then reconcile against Step 1's baseline:
- a. Re-extract the 9-row per-class test table **for each variant** and diff against baseline (b). Every row must be identical — not merely the total — and both variants must show the `--rerun` evidence triple again. `ISyncServiceTest` must still read 4/0/0/0.
- b. Re-extract the per-file assertion-count table and diff against baseline (c). Total **161**, with `ISyncServiceTest` still at 4.
- c. **Re-run the D10 assertion-content diff across all 8 converted files in one sweep** and paste a consolidated `file → residual line count` table, every row `0`. This is a whole-suite re-derivation, not a restatement of the per-step results — it catches a file that a later step touched incidentally after its own audit passed.
- d. **Run the D6 `javap` re-proof** against the compiled `debug` classes and paste the output for all seven rows in D6's table — `javap -p` for six, `javap -c` for the `DateFormat.format` binding.
- e. Record that `compileDebugUnitTestJavaWithJavac` **still executes**, now over exactly one file (`ISyncServiceTest.java`), and that `ktlintTestDebugSourceSetCheck` / `ktlintTestReleaseSourceSetCheck` now cover 8 files.
- f. Confirm `git diff 5f816b768 -- net/sync/service-interface/src/main/` is **empty** and `git status` shows `ISyncServiceTest.java` unmodified.

**Step 7 — Documentation.**

Rewrite `net/sync/service-interface/README.md` convention #9 per D3 (all four required statements, phrased as a long-term-stable convention with no milestone provenance; conventions #1–#8, #10, #11 byte-for-byte unchanged). Append this module's paragraph to **item 3** of `tasks/antennapod-model-kotlin-future-work.md` per D17 — and to **no other item**; items 9 and 10 are untouched and no new item is filed. Fill in this task file's Implementation Notes and update `features/antennapod-net-sync-service-interface-kotlin-milestone-14.checkpoint.md`.

### File Scope

The reviewer rejects any diff touching a file not on this list.

**Renamed `.java` → `.kt`** (`git mv` + rewrite; every file stays in its current directory, no package change, no new source set) — all under `net/sync/service-interface/src/test/java/de/danoeh/antennapod/net/sync/serviceinterface/`:

1. `SubscriptionChangesTest.java` → `.kt`
2. `EpisodeActionChangesTest.java` → `.kt`
3. `SynchronizationProviderTest.java` → `.kt`
4. `SyncServiceExceptionTest.java` → `.kt`
5. `UploadChangesResponseTest.java` → `.kt`
6. `SynchronizationQueueTest.java` → `.kt`
7. `EpisodeActionCharacterizationTest.java` → `.kt`
8. `EpisodeActionJsonTest.java` → `.kt`

**Total: 8 files renamed.** After this milestone the module's test source set is **1 `.java` / 8 `.kt`**.

**Held back in Java and NOT modified — a diff touching it is a scope violation, not a judgement call:**
- `net/sync/service-interface/src/test/java/de/danoeh/antennapod/net/sync/serviceinterface/ISyncServiceTest.java` (D2). It compiles and passes today against 100% Kotlin production code and nothing in this milestone gives it a reason to change. Pinned by AC18.

**Modified:**
- `net/sync/service-interface/README.md` (Step 7 only, convention #9 — D3)
- `tasks/antennapod-model-kotlin-future-work.md` (Step 7 only, item 3's new paragraph — D17)
- `tasks/antennapod-net-sync-service-interface-kotlin-milestone-14.md`
- `features/antennapod-net-sync-service-interface-kotlin-milestone-14.checkpoint.md`

**Not in scope — a diff touching any of these means the plan was wrong and the task is re-planned, not patched:** `net/sync/service-interface/build.gradle` (D16), anything under `net/sync/service-interface/src/main/` (test-only milestone — in particular `EpisodeAction.kt`, whose nullable returns D4 resolves in the tests, and `ISyncService.kt`, whose `@Throws`/`@JvmSuppressWildcards` D2 keeps guarded in Java), `ISyncServiceTest.java`, `common.gradle`, root `build.gradle`, `settings.gradle`, `playFlavor.gradle`, `gradle/libs.versions.toml`, `.editorconfig`, `config/checkstyle/suppressions.xml`, `config/spotbugs/exclude.xml`, `.github/`, and any file in any other module (`app/`, `model/`, `storage/*/`, `net/*/` other than this one, `ui/*/`, `playback/*/`, `parser/*/`, `system/`). The `assertlines.pl` audit script lives in the session scratchpad and is **not** committed.

**Production-code escape valve (narrow).** If a genuine J2K-interop issue forces a minimal production `.kt` fix, it **stops the step** and is disclosed under the module's established deviation-disclosure pattern (M4/M6/M8/M10/M13). It is never absorbed silently, and File Scope is not expanded to accommodate it without a re-plan. Research found no case that forces one, and D2/D4 pre-empt the two that would otherwise have been tempting (widening `readFromJsonObject`'s return to non-null; relaxing `ISyncService`'s annotations).

### Acceptance Criteria

Track: `kotlin` (test source set), `:net:sync:service-interface` module. Every item is checked against the Step 1 baseline in Implementation Notes.

**Characterization tests pass BEFORE the conversion step — pin current behaviour**
- [ ] **AC1** — Step 1 records, against the unconverted Java sources, **two separate `./gradlew … --rerun` invocations** (`testDebugUnitTest`, then `testReleaseUnitTest`), each BUILD SUCCESSFUL with **83 tests, 0 failures, 0 errors, 0 skipped**, and each accompanied by its `N actionable tasks: M executed` line and the before/after mtime of its `build/test-results/<task>/` directory. A single combined two-task invocation does **not** satisfy this criterion. No run uses `testFreeDebugUnitTest` or `testPlayDebugUnitTest` — those tasks do not exist in this module (README #8). The 9-row per-class table is pasted for both variants.

**Characterization tests pass AFTER the conversion step — the equivalence proof**
- [ ] **AC2** — Both variants are green, as **two separate `--rerun` invocations each time**, at the end of every one of Steps 2, 3, 4, 5 and again at Step 6: **83 tests, 0 failures, 0 errors, 0 skipped** on each variant, every time. The `M executed` evidence is recorded at each step. No site required a `.toLong()`, an added `L` suffix, or an `assertTrue(a == b)` relaxation to make an integer-literal assertion resolve.
- [ ] **AC3** — The post-conversion per-class test count matches AC1 **row for row, on both variants**, not merely in aggregate: `EpisodeActionJsonTest` 28, `EpisodeActionCharacterizationTest` 25, `SynchronizationProviderTest` 9, `SyncServiceExceptionTest` 7, `ISyncServiceTest` 4, `SynchronizationQueueTest` 3, `UploadChangesResponseTest` 3, `EpisodeActionChangesTest` 2, `SubscriptionChangesTest` 2.
- [ ] **AC4** — **No test is added, removed, renamed, split, merged, or moved between classes.** The Java-vs-Kotlin diff of the test-method-name list is empty across all 83 tests, and no backtick-quoted sentence-style name appears anywhere (D14).

**Scoping decision executed as planned**
- [ ] **AC5** — `find net/sync/service-interface/src/test -name '*.kt' | wc -l` → **8** and `find net/sync/service-interface/src/test -name '*.java'` → exactly `ISyncServiceTest.java`, nothing else (D2).
- [ ] **AC6** — `git diff 5f816b768 -- net/sync/service-interface/src/test/java/de/danoeh/antennapod/net/sync/serviceinterface/ISyncServiceTest.java` is **empty**. The held-back file was not edited, reformatted, re-annotated, or moved (D2).
- [ ] **AC7** — `:net:sync:service-interface:compileDebugUnitTestJavaWithJavac` **still executes** (is not `NO-SOURCE`) over exactly one file, and `ktlintTestDebugSourceSetCheck` / `ktlintTestReleaseSourceSetCheck` genuinely execute over the 8 converted files. Both observations pasted from Step 6(e).

**Assertion-content equivalence**
- [ ] **AC8** — The per-file assertion count (`grep -cE '\bassert[A-Z][A-Za-z]*\(' <file>`) is identical before and after for all 9 files: `EpisodeActionJsonTest` 72, `EpisodeActionCharacterizationTest` 50, `SyncServiceExceptionTest` 11, `SynchronizationProviderTest` 9, `UploadChangesResponseTest` 6, `SubscriptionChangesTest` 4, `ISyncServiceTest` 4, `EpisodeActionChangesTest` 3, `SynchronizationQueueTest` 2 — total **161**. Necessary but **not sufficient** on its own; see AC9.
- [ ] **AC9** — The D10 assertion-content diff residual is **empty for all 8 converted files**, with no disclosed exceptions. Evidence in Implementation Notes: Step 1's extractor validation (8/8 line counts matching AC8's converting-file rows, total 157, and the four `assertThrows` sites plus the one `assertArrayEquals` site confirmed to canonicalize), the per-step per-file diffs, and Step 6(c)'s consolidated 8-row `file → residual count` table reading `0` on every row. **The reviewer re-runs the exact Step 1 command and confirms it reproduces** — this criterion is not satisfied by the developer stating that they checked. Any residual is recorded verbatim with a one-line pure-syntax justification and accepted or rejected individually; a residual that alters an expected-value literal, swaps expected/actual order, or changes the assertion function fails this criterion outright.

**The nullability tax, and the one silent failure mode**
- [ ] **AC10** — **D5's falsification is executed and its six outcomes are pasted**: for each of `readFromJsonObjectAbsentTimestampLeavesTimestampNull`, `readFromJsonObjectUnparseableTimestampIsSwallowedAndLeavesTimestampNull` and `readFromJsonObjectEmptyGuidLeavesGuidNull`, the shipped `!!` form under mutation M **FAILS** with a `NullPointerException` and the softened `?.` form under mutation M **PASSES**. Each outcome carries its mutation diff and its JUnit result line. All mutations are reverted and `EpisodeActionJsonTest` re-runs clean at 28/28 before the step closes. `git diff` shows no mutation residue. **This is the milestone's central verification criterion**; a green suite without it does not establish that the three assertions still check anything.
- [ ] **AC11** — The `!!` count is verified by **occurrence**, not by matching line (*corrected in Revision 1 — the original criterion specified `grep -rc`, which counts lines and would report a fully correct conversion as short by one*):

  ```bash
  for f in net/sync/service-interface/src/test/java/de/danoeh/antennapod/net/sync/serviceinterface/*.kt; do
      printf '%s %s\n' "$(basename "$f")" "$(grep -o '!!' "$f" | wc -l)"
  done
  ```

  Required: **28** in `EpisodeActionJsonTest.kt`, **1** in `SyncServiceExceptionTest.kt`, **0** in the other six converted files — total **29**, matching D4's table row for row. A total of 28 or 30 is a review finding, not a rounding difference.

  **`grep -c` must not be substituted for `grep -o | wc -l` here, and the reason is recorded so nobody "simplifies" it back.** `EpisodeActionJsonTest.java:414` is `assertNotEquals(original.getTimestamp().getTime(), roundTripped.getTimestamp().getTime())` — two independent dereferences on one physical line, converting to `assertNotEquals(original.timestamp!!.time, roundTripped.timestamp!!.time)` at roughly 78 characters, well inside the 120-character limit, so D15 gives no licence to wrap it. It is the only such line in the suite (verified at planning time by an occurrence-count sweep of every `!!`-bearing site). A correct conversion therefore yields **28 occurrences across 27 matching lines** in that file, and **29 occurrences across 28 lines** suite-wide. If a line count is ever recorded instead, those are the numbers to expect. **Restructuring line 414 to satisfy a line-counting tool is forbidden** — it is an unforced diff change with no correctness justification, and File Scope authorizes no such edit.

  The 15/8/5 split within `EpisodeActionJsonTest.kt` is confirmed by inspection: 15 on `readFromJsonObject(…)` assignments, 8 on `writeToJsonObject()` assignments, 5 on `timestamp!!` (of which two share line 414).
- [ ] **AC12** — No nullable *return* is forced at an assertion site (the 23 `readFromJsonObject`/`writeToJsonObject` sites all force at their assignment — D4 rows 1–2; the 6 inline forcings in rows 3–4 are expected and authorized), and no softening was substituted for a `!!` anywhere. `grep -rnE '\?\.(timestamp|guid|podcast|episode|action|started|position|total)|readFromJsonObject\([^)]*\)\?\.|writeToJsonObject\(\)\?\.' net/sync/service-interface/src/test/` → **0**; `grep -rnE 'checkNotNull|requireNotNull|\?: |orEmpty\(\)|\?\.let' net/sync/service-interface/src/test/` → **0** (D4).

**Idiomatic Kotlin target achieved, without behaviour drift**
- [ ] **AC13** — The four hazards AC9's extractor structurally cannot see are pinned by direct inspection of the converted files:
  - `EpisodeActionCharacterizationTest.kt:` the reflection loop reads `for (fieldName in arrayOf("NEW", "DOWNLOAD", "PLAY", "DELETE"))` over `EpisodeAction::class.java.getField(fieldName)` and still asserts `isPublic`/`isStatic`/`isFinal` for each — the surviving guard for README #3 (D2).
  - `UploadChangesResponseTest.kt:` the reflection test still asserts `isPublic` **and** `isFinal` **and** `!isStatic` **and** that no method named `getTimestamp` exists; the nested subclass still reads the inherited `timestamp` unqualified in `describe()` (D2, D8).
  - `SyncServiceExceptionTest.kt:` the nested subclass still declares **both** `super(…)` constructor forms (the surviving `open` guard), and the `serialVersionUID` reflection test still asserts private + static + final + value `1L` (D2).
  - `SynchronizationQueueTest.kt:` `@Before` still captures and `@After` still restores the previous instance, and all nine stub methods are still called with the same four `null` arguments (D8).
- [ ] **AC14** — No collection or enum idiomization: `grep -rnE '\blistOf\(|\bemptyList\(\)|\bmutableListOf\(|\barrayListOf\(|SynchronizationProvider\.entries' net/sync/service-interface/src/test/` → **0**. `Collections.emptyList()`, `Collections.singletonList(…)`, `Arrays.asList(…)` and `values()` survive verbatim (D9, D13). `arrayOf(…)` appears only at the two authorized sites (`SynchronizationProviderTest.kt`'s array literal, `EpisodeActionCharacterizationTest.kt`'s field-name loop).
- [ ] **AC15** — grepping the converted source set for a literal backtick character (`` grep -rnF '`' net/sync/service-interface/src/test/ ``) returns **zero** hits. Research swept all nine files for Kotlin hard-keyword identifiers and found **zero**, so no backtick escape is legitimate in this module (D14).
- [ ] **AC16** — The three `assertTrue(fieldName + " must be …", …)` message arguments at `EpisodeActionCharacterizationTest.kt` are still string **concatenations**, not templates (D11) — checked by eye and corroborated by AC9's empty residual for that file.
- [ ] **AC17** — `EpisodeActionJsonTest.kt` retains `assertThrows(NullPointerException::class.java, action::writeToJsonObject)` as a callable reference at both sites; if the Step 5 empirical check forced the lambda form, that fact and its D10 residual are disclosed under Step 5 (D7). Neither site was widened to `Exception::class.java` nor narrowed to an exact-type check.

**Interop constraints the conversion must not resolve by editing production code**
- [ ] **AC18** — `git diff 5f816b768 -- net/sync/service-interface/src/main/` is **empty**. In particular `EpisodeAction.kt:74,200` still return `JSONObject?` / `EpisodeAction?` and were not widened to non-null, `ISyncService.kt` still carries `@Throws(SyncServiceException::class)` on all six methods and `@JvmSuppressWildcards` on both `List` methods, and `SynchronizationQueue.kt:7` still carries `@JvmStatic` (D2, D4).
- [ ] **AC19** — README #6a and #6b are re-proven mechanically (D6): Step 6(d)'s `javap` output is pasted and shows `getInstance()`/`setInstance(SynchronizationQueue)` `public static` on `SynchronizationQueue` itself (not only on `SynchronizationQueue$Companion`), `readFromJsonObject(org.json.JSONObject)` `public static` on `EpisodeAction` itself, `fromIdentifier(java.lang.String)` `public static` on `SynchronizationProvider`, the four `Action` aliases as `public static final`, `timestamp` as `public final long` with no `getTimestamp()`, and `serialVersionUID` as `private static final long`. This is the language-independent replacement for the source-level coverage `SynchronizationQueueTest` and `EpisodeActionJsonTest` stop providing.
- [ ] **AC20** — Milestone 11's D9 overload fork is re-confirmed on untouched production bytecode: `javap -c` on `EpisodeAction.writeToJsonObject` shows `invokevirtual … java/text/DateFormat.format:(Ljava/util/Date;)Ljava/lang/String;`, **not** `java/text/Format.format:(Ljava/lang/Object;)…` (D6, Research Unknown 5). A contrary result is recorded and stops the step for a decision; it is not fixed here.
- [ ] **AC21** — No public API break visible to Java callers outside the module. `./gradlew :app:assembleDebug` is green, compiling every consuming module — including `GpodnetService.java` and `NextcloudSyncService.java` (the two `ISyncService` implementors), the three `UploadChangesResponse` subclasses, `ResponseMapper.java:52`, and all 27 `SynchronizationQueue` accessor call sites — with zero edits outside `net/sync/service-interface/src/test/` and the four doc files.

**Toolchain gates**
- [ ] **AC22** — `./gradlew :net:sync:service-interface:ktlintCheck` is BUILD SUCCESSFUL with the test source-set checks genuinely **executing**, with **zero** `@Suppress("ktlint:…")` annotations added, no `ktlint_disabled_rules`, no `.editorconfig` change, and no ktlint filter or exclusion in any build file (D15). Implementation Notes records, from the Step 2 run, that the characterization test method names passed unchanged — observed on this module, not inherited from M7.
- [ ] **AC23** — `./gradlew checkstyle lint` is BUILD SUCCESSFUL, and Implementation Notes records whether Android Lint reported anything against the new `.kt` test files, observed at Step 2 rather than assumed from `lint.checkTestSources` being unset. Any pre-existing unrelated failure is verified as pre-existing against unmodified `origin/develop` before being set aside, per the Milestone 13 precedent.

**Scope and documentation**
- [ ] **AC24** — `git diff --name-only 5f816b768` lists only files from the File Scope list. In particular `net/sync/service-interface/build.gradle` is unchanged (D16), no file under `src/main/` appears, `ISyncServiceTest.java` does not appear, `config/checkstyle/suppressions.xml` is unchanged, no file outside this module and the four doc paths appears, and `assertlines.pl` was not committed.
- [ ] **AC25** — `net/sync/service-interface/README.md` convention #9 is rewritten per D3 and states all four required things (the `ISyncServiceTest`-stays-Java rule and its reason; which surviving mechanism guards #1/#2/#3/#7/`open`; that #6a and #6b have no module-local guard, with `javap -p` named as the check and #6b flagged as expiring when `:net:sync:gpoddernet` converts; and a cross-reference to #8 rather than a rewrite of it). `grep -c 'is Java by design and must stay Java' net/sync/service-interface/README.md` → **0**. It carries **no milestone number, no task-file reference, and no task-specific detail** (`AGENTS.md`). Conventions #1–#8, #10 and #11 are byte-for-byte unchanged. `tasks/antennapod-model-kotlin-future-work.md` gains one paragraph on item 3 (D17) and **no new item**; items 9 and 10 are unchanged.

**Not applicable to this module, asserted rather than assumed.** Accessibility (content descriptions, dynamic type), dark mode / hardcoded colours, RTL, Paparazzi snapshots, instrumented back-stack and deep-link tests, SDUI contract versions, analytics, and HSHD handling are all inapplicable: `:net:sync:service-interface` has no UI, no resources, no layouts, no `AndroidManifest` UI surface, no navigation, and handles no personal or payment data; the module has no `androidTest` source set (`src/` contains only `main` and `test`) and this milestone adds none. Only the `kotlin` track is in flight — no `compose` or `navigation` work is requested or performed, so the accessibility and snapshot criteria those tracks carry do not attach here.

### Milestone

**Milestone 14 — `:net:sync:service-interface` module, `kotlin` track (test source set).** Single milestone, single unified PR (code + spec docs together, per the standing instruction and the M7/M9/M10/M12/M13 precedent), four hazard-clustered conversion commits mapping to Steps 2–5, plus a verification/docs commit for Steps 6–7 (Step 1 produces no repo diff, so it folds into Step 2's commit).

Follows Milestone 11 (`:net:sync:service-interface` production code, PR #17, merged) and Milestones 1–13. On completion the `kotlin` track for this module is **closed for now, with one file deliberately open**: production 9/9 Kotlin, tests 8/9 Kotlin, one `.java` retained by decision rather than by omission. Its exit condition is recorded as **OQ3** so a future session does not re-litigate it from scratch. No further `:net:sync:service-interface` track is in flight.

This is unaffiliated OSS portfolio work, so "milestone" is case-study narrative structure, not invoicing. The case-study angle it earns is the completion of a three-part set: **one rule, three modules, three different answers.** `:event` kept 3 of 22 test files in Java; `:net:download:service-interface` converted 6 of 6; this module keeps 1 of 9. The difference is never appetite — it is that the rule asks "does this file, in Kotlin, still prove something?", and Research measured the answer file-by-file rather than accepting the module README's own blanket claim, which turned out to overstate the constraint by a factor of four. Being able to show a written convention being *revised by measurement* — and the one file where the measurement upheld it — is a stronger consulting artifact than any of the three outcomes alone.

### Out of Scope

- **Converting `ISyncServiceTest`** (D2). Not deferred pending effort — decided against on the evidence, with the reasoning recorded and an explicit exit condition in OQ3. Revisiting requires a new task and a new argument, not a follow-up milestone.
- **Splitting `ISyncServiceTest`** to convert part of it, **deleting it**, or **adding a compensating Kotlin test** that re-asserts its stub behaviour (D2). The file is the unit of language choice.
- **Any production code change.** No file under `net/sync/service-interface/src/main/` is modified. Specifically excluded and explicitly tempting: widening `EpisodeAction.readFromJsonObject`'s or `writeToJsonObject`'s return to non-null to avoid the 23 `!!` (D4), and relaxing `ISyncService`'s `@Throws` / `@JvmSuppressWildcards` (D2). Both are resolved in the tests or by holding a file back.
- **Fixing `EpisodeAction.equals`'s inverted `action != o.action`** (`EpisodeAction.kt:50`). Pinned by seven tests transcribed unchanged in Step 4; tracked as future-work item 9 and README #11. Fixing it inside a diff whose entire premise is equivalence is the one thing this milestone must not do.
- **`net/sync/service-interface/build.gradle` changes of any kind** (D16), including adding `kotlin.test` or `mockito-kotlin`, editing the Robolectric scoping comment, or removing the dead `:storage:preferences` / rx dependencies (future-work item 10 already records those as deliberately deferred).
- **The `TextUtils.isEmpty` → `isNullOrEmpty()` swap** in `EpisodeAction.kt` — future-work item 10, and a production edit besides.
- **Repo-wide build-policy changes:** `allWarningsAsErrors` on Kotlin test-compile tasks (item 3), the flavour-aware SpotBugs gate (item 7), cleaning orphaned checkstyle suppressions (item 5). All stay deferred; this milestone only annotates item 3 with this module's instance.
- **Promoting D6's `javap` proof into an automated check** (OQ1). New tooling, new File Scope, and arguably a shared-agent-definition convention rather than a repo one.
- **Changing assertion libraries or styles.** No `kotlin.test`, no AssertJ, no Truth, no `assertThrows` ↔ `@Test(expected=)` swaps, no hamcrest. Robolectric's scope stays exactly the two files README #10 names.
- **Deduplicating, restructuring, parameterizing, or "improving" any test.** `dateAtUtc` stays duplicated across `EpisodeActionJsonTest` and `EpisodeActionCharacterizationTest` — extracting a shared fixture would create the first cross-file coupling in a source set whose zero-coupling property D1's batching depends on. No shared base class, no `src/testFixtures`, no new test cases, no parameterized rewrite of the reflection loops. Every improvement spotted during conversion goes to `tasks/antennapod-model-kotlin-future-work.md`, not into this diff.
- **Tightening test-helper visibility to `internal`** now that eight files are Kotlin. Cosmetic only, and mixing a name-mangling risk into this diff is exactly the wrong trade.
- **Every other track.** No `gradle-kts` (`net/sync/service-interface/build.gradle` stays Groovy and is untouched), no `di`, no `concurrency`, no `compose`, no `navigation`. Nothing in this module is a ViewModel, View, navigation entry, or threading construct, and no target for any of those tracks is assumed.
- **Any architecture work** — no MVVM, no further modularization, no replacement of the `SynchronizationQueue` static-holder pattern, no repair of the deliberately-nullable `instance` contract.
- **Converting test or production sources in any other module.** `:net:sync:gpoddernet`, `:storage:database`, `:parser:feed` and the rest are separately-scoped work, each needing its own go-ahead.

## Open Questions
_Last updated by: legacy-android-planner | 2026-08-05_

None of these block implementation. Steps 1–7 proceed as written regardless of how they are answered.

**OQ1 — Should the `javap` interop proof become an automated CI check rather than a per-milestone manual acceptance criterion?** (Raised by D6; third recurrence, carried from `:event` M9's OQ2 and M13's OQ1, neither of which got an answer.)

AC19 and AC20 re-run the shape proof by hand at this milestone's boundary. That is a snapshot, not a regression guard: nothing prevents a future PR from dropping a `@JvmStatic` and deleting its last Java caller in the same commit, with CI green throughout. Three consecutive modules have now traded a source-level guard for a one-shot `javap` check, so the pattern is established rather than incidental, and a small Gradle verification task or a `javap`-diffing test would close it permanently for every module at once. Out of scope here — new tooling, new File Scope. It has now been asked three times; worth deciding whether asking a fourth is still the right answer.

**OQ2 — What guards README convention #6b once `:net:sync:gpoddernet` converts to Kotlin?** (Raised by D2's disclosed residual.)

After this milestone, `EpisodeAction.readFromJsonObject`'s `@JvmStatic` is guarded by exactly **one** Java call site anywhere in the repo — `ResponseMapper.java:52` in `:net:sync:gpoddernet`. Its only other caller is already Kotlin (`SynchronizationQueueStorage.kt:21`). The day `:net:sync:gpoddernet` migrates, that convention has **no** guard at all: not in this module's tests (Kotlin, per D2), not in the consumer, and nowhere else. Convention #6a is in a far stronger position (27 external Java call sites across five modules, several of which are not scheduled for conversion), so this is specifically a #6b problem, not a general one.

The question is which way to close it: adopt OQ1's automated check before that migration is scheduled, or make "does converting this module orphan another module's interop convention?" an explicit Research question in the pipeline's researcher definition. Not actionable today — the guard is intact and D3 records its fragility in the README. **Flagged now specifically because the `:net:sync:gpoddernet` milestone would otherwise inherit the problem silently**, exactly as M13's OQ2 flagged the same shape for `:net:download:service`.

**OQ3 — What is the exit condition for `ISyncServiceTest.java`, the module's last Java file?** (Raised by D2, recorded so a future session does not re-derive the decision from scratch.)

D2 holds the file because its four tests' entire oracle is javac's acceptance of `throws SyncServiceException` on six overrides and of non-wildcarded `List` parameters. That oracle stops being load-bearing when **both** `GpodnetService.java` and `NextcloudSyncService.java` are Kotlin — at which point no Java implementor of `ISyncService` exists anywhere, conventions #4 and #5 have no consumer left to break, and the file can be converted or deleted with nothing lost. Until then, converting it buys four tautologies (D2's analysis).

Stated as a trigger rather than a schedule: **when `:net:sync:gpoddernet` converts, revisit this file in that milestone's plan, not before.** Whether the right answer at that point is "convert" or "delete" is a genuine open question — a Kotlin `ISyncServiceTest` whose implementors are all Kotlin would prove nothing at all, which argues for deletion, but deleting tests is a decision that needs its own justification.

**OQ4 — Is the `-Werror` coverage gap now large enough to act on?** (Future-work item 3, sixth recurrence.)

D17 records the narrowest instance yet: one Java file survives here, so `-Xlint:all -Werror` still covers something in this module — unlike `:model` and `:net:download:service-interface`, where it now covers nothing at all. Concretely relevant to *this* milestone only in that it removes the safety net from any unchecked-cast or platform-type workaround in the eight converted files, and this plan authorizes none (AC12), so the exposure here is theoretical. Tracked repo-wide; not acted on. Item 3 has now been logged across six milestones without ever being taken.

**OQ5 — Upstreaming intent.** Standing, carried unchanged from M7/M9/M11/M12/M13 (`tasks/antennapod-model-kotlin-future-work.md` item 2). Whether any of this work is offered upstream to AntennaPod affects how conservative the interop posture needs to stay long-term: a fully-Kotlin downstream fork could eventually drop `@JvmStatic`, `@JvmField` and `@Throws` entirely, which would make D2's held-back file pointless and OQ2 and OQ3 both moot; an upstream contribution cannot. It also shapes how an upstream reviewer would read a deliberately-retained single `.java` file in an otherwise-Kotlin test source set — D3's README rewrite is the artifact that has to answer that question for them. Unanswered across six milestones without ever blocking one. **Commercial/strategic — for José**, per root `CLAUDE.md`'s commercial-implications rule; not an agent decision.

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-08-05 | Loop 1 of max 2_

### Verdict
CHALLENGE

### Verification performed this loop

Read the Research and Plan sections in full (not in isolation — Research's Gap 2 table, Unknown 1, and the D9 nullability decisions from the Milestone 11 task file were load-bearing context for D2/D4/D5), then verified against live source rather than trusting the Plan's own citations as proof of themselves:

- **D2's central claim** (`ISyncServiceTest`'s four tests reduce to a stub asserting itself once the compile-time oracle is stripped) — read `ISyncServiceTest.java` in full and traced each of the four `@Test` methods' assertion back to its inputs. Confirmed independently, not taken on the Plan's word.
- **The `:event` M9 `PublicFieldInteropTest` analogy** — read `PublicFieldInteropTest.java` in full and cross-referenced `tasks/antennapod-event-kotlin-milestone-9.md`'s D2/D3 reconciliation (the red-team-corrected version, not the original) for the actual reason that file was kept in Java.
- **D4's `!!` inventory (29)** — recounted every `readFromJsonObject(` and `writeToJsonObject(` call site and every `.getTime()` chain in `EpisodeActionJsonTest.java` by direct grep and line-by-line reading, and the one `SyncServiceExceptionTest.java` site, rather than accepting the Plan's table.
- **D4's "forced at the assignment site, never at the assertion" claim** — checked this against the same inventory: does every one of the 29 sites actually land on a prior assignment statement, or does the Plan's own table contain exceptions?
- **D5's live-falsification procedure** — read `EpisodeAction.kt:200-206` directly and hand-traced the mutation (drop `"podcast"`) through `optString`/`TextUtils.isEmpty` to confirm it genuinely produces a `null` return, and traced both Run 1 (`!!`) and Run 2 (`?.`) forms against that mutated input.
- **D6's `javap` re-proof** — checked each of the six `@JvmStatic`/`@JvmField`/`const val` declarations in the actual `.kt` production files against standard Kotlin/JVM interop semantics for what `javap -p`/`-c` would show.
- **AC11's exact verification command** — ran `grep -c` behavior against a synthetic two-match-one-line fixture to confirm what the command in the Plan actually reports, rather than assuming line-count and occurrence-count are interchangeable.
- **The corrected task names** (`testDebugUnitTest`/`testReleaseUnitTest`, unflavoured) — read `net/sync/service-interface/build.gradle` (no `playFlavor.gradle` applied, unlike `net/download/service-interface/build.gradle`) and independently ran `./gradlew :net:sync:service-interface:tasks --all`, which lists only `testDebugUnitTest`/`testReleaseUnitTest` — confirms the Plan's AC1 correction empirically, not just by reading the build file.

Two of the seven verification targets checked out exactly as claimed (D2's stub-asserting-itself analysis and D5's falsification procedure) and are not repeated as concerns below. The task-name correction also checked out exactly and is confirmed, not a concern.

### Concerns

- **Severity:** MAJOR
- **Class:** Unproven equivalence-verification mechanism (checklist #1/#4 — the acceptance criterion does not actually prove what it claims to prove)
- **Concern:** AC11 specifies `grep -rc '!!' net/sync/service-interface/src/test/` and asserts it "reports exactly 28" for `EpisodeActionJsonTest.kt`. `grep -c` counts **matching lines**, not occurrences of the pattern — a line containing the pattern twice is still counted once. D4's own inventory table places the 5 `timestamp!!.time` sites at `EpisodeActionJsonTest:76,169,413,414(×2)` — i.e. line 414 (`assertNotEquals(original.getTimestamp().getTime(), roundTripped.getTimestamp().getTime());`) contains **two** independent `.getTime()` dereferences on **one physical line**, each requiring its own `!!` after conversion (`assertNotEquals(original.timestamp!!.time, roundTripped.timestamp!!.time)`). That converted line is ~85 characters — well under the module's 120-character ktlint limit (`.editorconfig`, cited by D15), so it will not be wrapped, and D15 explicitly limits wraps to argument/comma boundaries only when a line exceeds 120 chars, ruling out an incidental save. The result: a fully correct conversion with all 29 `!!` present and none missing will make `grep -c` report **27** matching lines in `EpisodeActionJsonTest.kt` (27 lines contain a `!!`, one of which contains two), not the 28 the AC states. Since AC11's own text treats a deviation from the stated number as diagnostic ("a count of 28 or 30 is a review finding, not a rounding difference"), a developer or reviewer following the AC literally will see a real, correctly-converted file reported as failing this criterion — either burning a review cycle chasing a phantom discrepancy, or worse, "fixing" it by artificially restructuring line 414 (splitting the two dereferences onto separate lines) purely to satisfy a miscounting tool, which is an unforced diff change with no correctness justification and no license anywhere in File Scope or D15's line-wrap rule for "because the audit script needs it."
- **Evidence:** Plan D4/AC11 (`grep -rc '!!' net/sync/service-interface/src/test/` → "exactly 28... in EpisodeActionJsonTest.kt"); `EpisodeActionJsonTest.java:414` (live source, confirmed via `grep -n "getTime()" EpisodeActionJsonTest.java` → four matching lines, one of which — 414 — contains two `.getTime()` calls); confirmed `grep -c` line-counting behavior directly: `printf 'a!!b!!c\n' | grep -c '!!'` → `1`, while `grep -o '!!' | wc -l` → `2` on the same input.
- **Suggested mitigation:** Change AC11's verification command from `grep -c` (line count) to an occurrence count, e.g. `grep -o '!!' <file> | wc -l`, or state the expected `grep -c` result correctly as **27 matching lines / 28 total occurrences** for `EpisodeActionJsonTest.kt` and explain the one two-`!!`-per-line exception at the `assertNotEquals` site so a future reviewer doesn't mistake 27 for a shortfall. Either fix is a one-line edit to AC11's text; no Step, File Scope entry, or other AC needs to change.

- **Severity:** MINOR
- **Class:** Imprecise equivalence-preservation claim (checklist #2 — the claim as stated is broader than what the Plan's own evidence supports, though the narrower true claim still holds)
- **Concern:** D4's summary sentence states every one of the 29 `!!` is "forced at the *assignment* site, never at the assertion." D4's own inventory table contradicts this for 6 of the 29: the 5 `timestamp!!.time` sites (`EpisodeActionJsonTest:76,169,413,414×2`) and the 1 `SyncServiceExceptionTest:83` site (`fromCause.cause!!.message`) are all forced **inline inside the assertion's argument expression** (e.g. `assertEquals(epochMillisUtc(...), action.timestamp!!.time)`), not at a preceding `val x = …!!` assignment — there is no intermediate local for the `Date?`/`Throwable?` chain these force. I independently checked whether this reintroduces Gap 1's silent-vacuity hazard (the reason "never at the assertion" exists as a rule) and it does not: all 6 of these sites sit inside assertions that check equality against a specific non-null expected value (`assertEquals`), not `assertNull`, so softening any of them to `?.` would produce a visibly failing assertion on regression ("expected: <X> but was: <null>"), not a silently-passing one — the vacuity mechanism Gap 1/D5 exist to prevent requires the assertion itself to be an `assertNull`, which none of these 6 are. So the *safety property* D4 is protecting holds; only the *stated scope* of the "assignment site, never the assertion" rule is inaccurate against the Plan's own table.
- **Evidence:** Plan D4 ("every one of them is forced at the *assignment* site, never at the assertion") versus D4's own table rows 3–4 (`timestamp!!.time`, `fromCause.cause!!.message`); live confirmation these sit inside `assertEquals(...)` calls at `EpisodeActionJsonTest.java:76,169,413,414` and `SyncServiceExceptionTest.java:83` (all `assertEquals`/`assertNotEquals`, none `assertNull`).
- **Suggested mitigation:** Narrow D4's summary sentence to state the actual scope, e.g.: "every `EpisodeAction?`/`JSONObject?` return (23 of the 29 sites — the vacuity-risk-bearing nullability chain Gap 1 identifies) is forced at the assignment site, never at the assertion; the remaining 6 sites force an already-non-null receiver's own nullable member (`Date?`/`Throwable?`) inline inside a non-null-expected-value assertion, which carries no vacuity risk because a regression fails the assertion visibly rather than passing it." No AC, Step, or File Scope change needed — this is a one-paragraph correction to D4's prose.

- **Severity:** MINOR
- **Class:** Imprecise precedent citation (checklist #1/#9 adjacent — the cited analogy supports the conclusion at the level of the rule being applied, but overstates how closely the two cases match mechanically)
- **Concern:** D2 states holding `ISyncServiceTest` back "is exactly the shape `:event` M9 kept `PublicFieldInteropTest` for." Reading `PublicFieldInteropTest.java` and M9's actual (red-team-corrected) D2/D3 reconciliation shows the mechanism is not the same. `PublicFieldInteropTest`'s 8 tests construct **real production objects** (`FeedUpdateRunningEvent`, `QueueEvent`, `MessageEvent`, etc.) and read their genuine field values — after losing the compile-time field-syntax guard, the assertions still check real, meaningful production behavior; M9's D2/D3 reconciliation kept the file specifically because that behavioral content, while real, turned out to be **assertion-for-assertion duplicated** in other files that do convert to Kotlin (`FeedUpdateRunningEventTest`, `PlaybackServiceEventTest`, `FeedEventTest`, `QueueEventTest`), leaving it with zero *unique* value. `ISyncServiceTest`'s four tests, by contrast, exercise `TestSyncService` — a stub declared inside the test file itself, with no real production implementor in this module — so the values asserted (e.g. `response.timestamp == 2L` from `addedFeeds.size() + removedFeeds.size()`) are arithmetic the test itself set up being read back, not a real production object's field. This is a stronger and structurally different degenerate case than `PublicFieldInteropTest`'s (redundant-but-real vs. self-referential-to-a-test-fixture), even though both correctly land on "the file, in Kotlin, proves nothing unique" under M9's own bright-line question. The D2 conclusion for `ISyncServiceTest` does not depend on this analogy — it holds independently on direct inspection of the file, which I verified separately — so this does not change the Decision, but "exactly the shape" overstates the precedent's precision in a document whose value proposition is exact verification.
- **Evidence:** `event/src/test/java/de/danoeh/antennapod/event/PublicFieldInteropTest.java` (all 8 tests construct real production classes); `tasks/antennapod-event-kotlin-milestone-9.md` lines ~289-310 (the loop-1-corrected D2/D3 reconciliation: "its behavioral content is... assertion-for-assertion identical to tests that already exist elsewhere... the real choice is not 'lose a redundant guard' but 'convert and gain 8 green tests that assert nothing anyone needs'"); `net/sync/service-interface/src/test/java/de/danoeh/antennapod/net/sync/serviceinterface/ISyncServiceTest.java:36-39,47-50,87-101` (the stub computing and echoing back test-supplied values, no external duplicate Kotlin file exists for this behavior since the module has no other test of `TestSyncService`-equivalent stub arithmetic).
- **Suggested mitigation:** Either drop the "exactly the shape" phrasing in favor of "the same rule (`does this file, in Kotlin, still prove anything?`) applied to a different, arguably starker, degenerate case — M9 kept `PublicFieldInteropTest` because its surviving behavioral content was redundant with other tests; here there is no surviving behavioral content at all, since the object under test is a stub the test itself authored" — or leave the citation but add one clause distinguishing the two mechanisms. One-sentence fix, does not touch the Decision, any Step, or any AC.

### Checklist categories considered and dismissed

- **Silent behavior changes from mechanical translation (numeric widening, exception types)** — considered in depth; none found beyond what's already disclosed. Independently reconfirmed the "zero `int`→`Long` widening trap" claim by checking every integer-literal `assertEquals` site against its production type (`EpisodeAction.kt:24,28,32` are `Int`; the `Long` timestamp fields are all compared against `L`-suffixed literals) — matches Research exactly.
- **Public API breakage** — considered; none found. File Scope is genuinely test-only, `git diff -- src/main/` is required empty by AC18, and D2/D4 explicitly reject the two production edits (widening `readFromJsonObject`'s return, relaxing `ISyncService`'s annotations) that would have been tempting shortcuts around the nullability tax and the held-back file.
- **Coverage gaps left unaddressed** — considered; correctly disclosed, not silently dropped. Gap 1's vacuity risk gets a genuine falsification procedure (D5, verified above), and conventions #6a/#6b's lost in-module guards get a `javap` re-proof (D6) whose mechanics I independently confirmed match standard `@JvmStatic`/`@JvmField`/`const val` JVM interop semantics — `javap -p` on the outer class is the correct check for each of the six rows in D6's table.
- **`concurrency`/`compose`/`navigation`/`di` tracks** — correctly not assessed; module has no threading construct, no UI, no DI wiring, and Out of Scope explicitly excludes all four tracks.
- **`gradle-kts` track** — correctly not assessed; D16 explicitly excludes `build.gradle` from File Scope, and I independently confirmed the module's Groovy build file has no `playFlavor.gradle` application (unlike its M13 sibling), validating the corrected task names.
- **Milestone/scope creep** — considered; none found. File Scope is a fixed 8-file rename list plus four doc files; Out of Scope explicitly forbids fixing the pinned `equals` defect, deduplicating `dateAtUtc`, tightening visibility to `internal`, and every other tempting cleanup spotted during research. D4's 15/8/5 split and the SyncServiceException 1 site were independently recomputed against live source in this loop (grep + manual line reading) and match the Plan's table exactly — no arithmetic drift found beyond the AC11 verification-command bug above.

None of the three findings above touch File Scope, Step ordering, or the substance of any Decision — D2's held-back-file conclusion, D4's `!!` count and forcing strategy, and D5's falsification procedure are all independently confirmed correct. All three are corrections to the Plan's *stated reasoning or verification tooling*, fixable as in-place text edits: AC11's command (MAJOR, because it will misfire on a correct implementation and invites an unforced diff change to chase a phantom failure), and two prose-precision corrections (MINOR) that don't change what ships. Awaiting a revision before loop 2.


---

## Plan — Revision 1 (responding to red-team loop 1)
_By: legacy-android-planner | 2026-08-05_

All three findings are **accepted in full**. No Decision, Step, File Scope entry, or acceptance criterion other than AC11 changes; the edits are in place in the Plan above and are itemised below. AC11's *command* changes; its required numbers are unchanged once counted correctly.

### Verification I performed this loop

I did not take any of the three findings on the red-team's word, including the two it graded MINOR.

- **MAJOR (AC11's counting bug) — confirmed, and I checked it was the only instance.** `grep -c` line-counting behaviour reproduced directly (`printf 'a!!b!!c\n' | grep -c '!!'` → `1`; `grep -o '!!' | wc -l` → `2`). Then, rather than accepting that line 414 is the only doubled site, I swept the whole file for *any* line carrying two or more of the constructs that become `!!`: `awk` occurrence-counts of `getTime()` per line returned exactly one line with two (`:414`), and the same sweep for `readFromJsonObject(` / `writeToJsonObject()` returned **none** — the 15 read and 8 write forcings are each on their own statement line. So the discrepancy is exactly 1, in exactly one file, and the correct figures are **28 occurrences / 27 lines** for `EpisodeActionJsonTest.kt` and **29 / 28** suite-wide. The red-team's arithmetic is right and its "~85 characters" estimate for the converted line is close enough (I measure ~78); either way it is far inside the 120-character limit, so D15 offers no wrap and no incidental rescue.
- **MINOR (D4's overstated scope) — confirmed against my own table.** Rows 3 and 4 (5 × `timestamp!!.time`, 1 × `cause!!.message`) do force inline in an assertion argument. I re-derived the safety argument independently rather than restating the red-team's: the vacuity mechanism in Gap 1 requires the *assertion itself* to be `assertNull`, because that is the only assertion form whose verdict is unchanged when the receiver is null. All six of these sites are `assertEquals`/`assertNotEquals` against a specific non-null expected value, so a `?.` softening surfaces as `expected:<…> but was:<null>`. The safety property holds; only my summary sentence was wrong. This is also why D5's falsification correctly targets the three `assertNull` sites and no others — a fact the corrected D4 now states explicitly, so the scoping of D5 reads as deliberate rather than arbitrary.
- **MINOR (the `PublicFieldInteropTest` analogy) — confirmed, and it is the finding I am most glad to have received.** I had cited M9's precedent for its *conclusion* without checking its *mechanism*. The red-team is right that they differ: M9's file asserted real production objects' real field values and was kept because that content was assertion-for-assertion duplicated elsewhere; `ISyncServiceTest` has no real production content to be duplicated. That makes this the starker case, not the matching one — so the correction strengthens D2's conclusion while weakening my stated support for it. Rewriting it as "same rule, different and starker degenerate case" is both more accurate and more useful to a reader deciding the next module.

### Edits made, in place in the Plan above

1. **AC11** — verification command replaced with a per-file `grep -o '!!' | wc -l` loop (occurrence count). Required occurrence figures are unchanged: 28 / 1 / 0 × 6, total 29. Added the reason `grep -c` must not be substituted back, the expected line-count figures (27 in-file, 28 suite-wide) for anyone who records lines anyway, and an **explicit prohibition on restructuring line 414 to satisfy a counting tool** — the unforced diff change the red-team correctly identified as the worse of the two failure modes.
2. **D4** — heading and body narrowed. The forcing-at-the-assignment rule is now scoped to the 23 nullable-*return* sites that carry Gap 1's risk. A new paragraph covers rows 3–4 affirmatively: they force a non-null receiver's nullable *member* inline, there is no local to force at, manufacturing one would violate the minimal-diff rule, and they carry no vacuity risk because none is an `assertNull`. `?.` stays forbidden at all six (AC12) on transcription-faithfulness grounds, with the noise/silence distinction stated so the plan does not overclaim.
3. **D2** — the "exactly the shape" claim replaced with the same-rule/different-mechanism distinction, including what `PublicFieldInteropTest` actually asserted and why M9 kept it (redundant-but-real), against `ISyncServiceTest`'s self-referential stub. The M13 `DownloadServiceInterfaceTest` contrast is kept and re-anchored on "real production classes", which is the axis that actually separates the three cases.
4. **Three downstream references** carrying the same overstatement, found by grep and by re-reading AC11's neighbours rather than left to drift: **Step 5**'s "every one at an assignment or on `timestamp`, none at an assertion" now states the 15/8/5 split with the "none of these 23 at an assertion" qualifier and notes the shared line 414; **AC12**'s opening clause ("No `!!` sits at an assertion site") — which would have contradicted the corrected D4 and could have failed a correct implementation on its prose alone — now scopes itself to nullable *returns* and names rows 3–4's six inline forcings as expected and authorized; **D15**'s parenthetical "(`!!` at assignment sites)" is now "(the `!!` inventory and its forcing rule)". AC12's two grep commands are unchanged — they were always correctly scoped to the specific nullable chains and to the forbidden softenings, and only the sentence above them overstated.

### Counts and shape

Unchanged: 17 Decisions, 7 Steps, 25 ACs, 8 files renamed, 1 held back, 4 doc files modified. `!!` inventory unchanged at 29 occurrences (15 + 8 + 5 + 1). The only numeric change anywhere is AC11's counting *method*, which now reports 29 where the previous command would have reported 28.

## Red-Team Verdict — Plan (Loop 2, Final)
_By: legacy-android-red-team | 2026-08-05 | Loop 2 of max 2 (final)_

### Verdict
APPROVE

### Verification performed this loop

Did not take Revision 1's account of its own fixes on trust — re-derived each of the three loop-1 findings independently against live source and against the Plan's current text, and separately swept for stale references the planner's "three downstream references" list might not have caught.

- **AC11's grep-count math.** Independently recounted every `!!`-producing construct in `EpisodeActionJsonTest.java` from my own loop-1 read of the full file (not re-using the planner's sweep): 15 `readFromJsonObject(` assignments (each its own statement line), 8 `writeToJsonObject()` assignments (each its own statement line, including `:365,386,409` which feed into a subsequent `readFromJsonObject(json)` call on the *next* line, not the same one), and the 4 `.getTime()`-chain lines at `:76,169,413,414` — of which only `:414` (`assertNotEquals(original.getTimestamp().getTime(), roundTripped.getTimestamp().getTime())`) carries two. No other line among the 27 combines two constructs. That gives **28 occurrences / 27 lines** in-file and, adding `SyncServiceExceptionTest.java:83`'s single site on its own line, **29 occurrences / 28 lines** suite-wide — exactly Revision 1's corrected figures. Re-read the new AC11 text (`tasks/…:618-630`): the script now loops per-file with `grep -o '!!' "$f" | wc -l`, which I confirmed directly counts occurrences, not lines (`printf 'a!!b!!c\n' | grep -o '!!' | wc -l` → `2`). The script is non-recursive (globs `*.kt` in the exact test directory), so it can't pick up stray matches from `build/` or elsewhere, and it naturally excludes `ISyncServiceTest.java` (never renamed to `.kt`). The explicit prohibition on restructuring line 414 (`:628`) closes the exact unforced-diff failure mode loop 1 flagged. Confirmed fixed, no residual issue.
- **D4's affirmative justification for the 6 inline sites.** Re-checked independently rather than re-reading the planner's restatement of my own loop-1 argument: all 6 sites (`timestamp!!.time` ×5, `cause!!.message` ×1) sit inside `assertEquals`/`assertNotEquals` calls against a specific non-null expected value — confirmed again directly against `EpisodeActionJsonTest.java:76,169,413,414` and `SyncServiceExceptionTest.java:83`, none of which is `assertNull`. Gap 1/D5's vacuity mechanism requires the assertion itself to be `assertNull` for a `?.` softening to be indistinguishable from a genuine null; an `assertEquals` against a non-null literal fails loudly (`expected:<X> but was:<null>`) on the same regression. The new D4 text (`:283,302`) states this exact reasoning, correctly scopes the assignment-site rule to "the 23 sites that carry Gap 1's vacuity risk," and explicitly ties D5's three-site (not six-site) falsification scope to the same mechanism — which resolves a question I hadn't asked in loop 1 (why does D5 only falsify 3 of the 29?) with a real structural answer rather than leaving it implicit. Confirmed fixed.
- **AC12/Step 5/D15 consistency with the corrected D4.** Read all three current texts against the corrected D4 side by side. AC12 (`:631`) now opens "No nullable *return* is forced at an assertion site... the 6 inline forcings in rows 3–4 are expected and authorized" — matches D4's scope exactly, and its two `grep` commands (unchanged, checking for `?.` softenings and `checkNotNull`/`requireNotNull`/`?:`/`orEmpty()`/`?.let`) remain correctly aimed regardless of the forcing-site scope, since they forbid specific unfaithful transcriptions rather than counting assignment sites. Step 5 (`:540`) states "15... 8... — none of these 23 at an assertion — plus 5 inline `timestamp!!`, two of which share line 414," arithmetically consistent with D4's table (15+8+5=28, the file's share of 29) and with AC11's line-count note. D15 (`:473`) now reads "the `!!` inventory and its forcing rule" — generic, no longer asserting a blanket "assignment sites" claim. I also swept the full file for the retired phrases (`grep -n "never at the assertion"`, `"exactly the shape"`, `"grep -rc '!!'"`) to check for anything the planner's four-item list might have missed: all remaining hits are inside the loop-1 verdict block and the Revision-1 narrative themselves (historical record, correctly left as-is — they document what was wrong, not a live claim), none in live Plan prose (Objective, Decisions, Steps, File Scope, ACs, Milestone, Out of Scope, Open Questions). Confirmed no fourth stale reference exists.
- **D2's analogy rewrite, spot-checked against my own loop-1 finding.** `:247` now states the shared rule and the differing mechanism explicitly — "redundant-but-real" for `PublicFieldInteropTest` vs. "no surviving behavioural content... a stub the test file itself authored" for `ISyncServiceTest` — matching what I verified directly against both files in loop 1. Not re-verified against source a third time since nothing about the underlying files changed between loops; the fix is a prose correction to an already-confirmed-accurate distinction.
- **Scope integrity.** Recounted Decisions (17), Steps (7), and ACs (25) directly against the live file — unchanged from loop 1, matching Revision 1's claim. File Scope's renamed-file list is still exactly 8 (`SubscriptionChangesTest` through `EpisodeActionJsonTest`), `ISyncServiceTest.java` still explicitly held back and unmodified. No Step, File Scope entry, or AC pass/fail condition changed other than AC11's verification command — confirmed by re-reading AC1–AC25 in full, not just the four the planner listed as touched.

### Concerns

None. All three loop-1 findings are fixed at the root — not patched locally while leaving the same overstatement live elsewhere, which was the specific risk this loop was asked to rule out. The AC11 fix is mechanically correct and independently reproducible from a stated command. The D4/AC12/Step 5/D15 fix is consistent across every location that previously repeated the overstated claim, and I found no fifth location the sweep missed. The D2 analogy correction is accurate against both source files and strengthens rather than weakens the underlying (independently-verified-in-loop-1) conclusion that `ISyncServiceTest` should stay Java.

### Checklist categories considered and dismissed (loop 2)

- **Characterization tests prove equivalence, not just existence** — unaffected by this revision; D5's falsification procedure and D10's assertion-content diff are untouched and were already confirmed in loop 1.
- **Silent behavior changes from mechanical translation** — unaffected; D4's forcing strategy (the substance of it, not its prose scope) was already confirmed safe in loop 1 and is unchanged here.
- **Public API breakage / coverage gaps / track-specific hazards** — unaffected by this revision; already assessed in loop 1 with no findings, and nothing in Revision 1 touches File Scope, production code, or the `javap` re-proof.
- **Milestone/scope creep** — specifically re-checked this loop given the risk that a "fix the prose" revision quietly drops or adds an AC/Step/Decision. Confirmed none did.

### Verdict rationale

Two loops, three findings, all MAJOR/MINOR-tagged corrections to stated reasoning or verification tooling — never to a Decision's conclusion, a Step's content, or File Scope. Loop 2 confirms the fixes are complete (no stale fourth reference), correct (the AC11 script genuinely counts occurrences; the D4 affirmative justification genuinely holds), and consistent with each other (AC12/Step 5/D15 all agree with the corrected D4). This milestone is approved to proceed to implementation.

## Implementation Notes
_By: android-migration-developer | 2026-08-05_

### Branch and starting state

Branch `antennapod-net-sync-service-interface-kotlin-milestone-14`, checked out off `origin/develop` at merge-base `5f816b768b1e82e98e1ca1a76c49deecc2d6f51c` ("kotlin/net-download-service-interface: convert :net:download:service-interface's remaining test files to Kotlin (#19)") — matches the merge-base Research and Plan already recorded. Working tree was clean apart from the two untracked spec files at session start.

### Commit message(s)

Six commits on this branch, following the Plan's four-tier structure (Step 1 produced no repo diff, so it folds into Step 2's commit; Steps 6 and 7 combine into one verification-and-docs commit, per the Plan's own Milestone section):

```
kotlin/net-sync-service-interface: Step 2 — convert Tier A (SubscriptionChangesTest, EpisodeActionChangesTest, SynchronizationProviderTest) + toolchain proof
kotlin/net-sync-service-interface: Step 3 — convert Tier B (SyncServiceExceptionTest, UploadChangesResponseTest, SynchronizationQueueTest)
kotlin/net-sync-service-interface: Step 4 — convert Tier C (EpisodeActionCharacterizationTest, Robolectric)
kotlin/net-sync-service-interface: Step 5 — convert Tier D (EpisodeActionJsonTest), run D5's live falsification
kotlin/net-sync-service-interface: Step 6/7 — final verification matrix and docs
```

Summarized version for the PR description:

```
feat(net-sync-service-interface): kotlin migration — convert 8 of the module's 9 Java test files to Kotlin, hold ISyncServiceTest.java back

kotlin track (test source set), Milestone 14. Completes the module's
migration begun in Milestone 11 (100% Kotlin production code). Eight of
nine test files convert; ISyncServiceTest.java stays Java (D2) — its
four tests exercise a stub declared inside the file itself, with no
production implementor in this module, so their entire value is as a
javac compile-time oracle for @Throws/@JvmSuppressWildcards, which a
Kotlin rewrite would destroy without replacing (four green tautologies,
not four tests). The other three README-#9-named files convert safely:
SyncServiceExceptionTest and SynchronizationQueueTest keep or strengthen
their guards, UploadChangesResponseTest trades a weak compile guard for
a stronger surviving reflection guard.

83/83 tests green on both testDebugUnitTest/testReleaseUnitTest
throughout, two separate --rerun invocations at every step. D10's
per-file assertion-content diff is empty for all 8 converted files.
Exactly 29 !! (28 in EpisodeActionJsonTest, 1 in
SyncServiceExceptionTest), verified by occurrence count not line count
(AC11). D5's live falsification actually run: the three assertNull
sites that a ?. softening would make vacuous were proven load-bearing
by mutating a null-producing fixture and confirming the shipped !! form
fails with NullPointerException while the softened ?. form passes
vacuously — six recorded outcomes, then reverted. javap re-proof
confirms README #6a/#6b's lost in-module guards and re-confirms
Milestone 11's DateFormat.format overload-resolution fork on untouched
production bytecode. README convention #9 rewritten to state which
mechanism now guards each convention; future-work item 3 gains one
paragraph, no new item filed.

No production .kt file changed. :app:assembleDebug green, confirming
every external Java consumer — including ISyncService's two Java
implementors — still compiles.
```

### Test commands run

Module is **unflavoured** — confirmed via `net/sync/service-interface/build.gradle` (no `playFlavor.gradle` applied) and `./gradlew :net:sync:service-interface:tasks --group verification`, which lists only `testDebugUnitTest`/`testReleaseUnitTest`. The task header's originally-stated `testFreeDebugUnitTest`/`testPlayDebugUnitTest` do not exist in this module (README #8) — Research had already caught this and the Plan carried the correction forward:

- `./gradlew --console=plain :net:sync:service-interface:testDebugUnitTest --rerun` — before (Step 1): PASS (83/0/0/0), after (Steps 2–6, every time): PASS (83/0/0/0)
- `./gradlew --console=plain :net:sync:service-interface:testReleaseUnitTest --rerun` — before (Step 1): PASS (83/0/0/0), after (Steps 2–6, every time): PASS (83/0/0/0)

Always run as **two separate invocations** per D1's warning (Milestone 13's hard-won lesson) — no combined two-task command line was used anywhere in this implementation. Each invocation's `build/test-results/<task>/` directory mtime was confirmed to advance on every run (e.g. Step 1: `testDebugUnitTest` 11:38:52 → 12:41:17 after the Step-1 baseline `--rerun`), the three-part evidence D1's Step 1 requires.

Also run:
- `./gradlew :net:sync:service-interface:ktlintCheck` — BUILD SUCCESSFUL at every step from Step 2 onward, zero `@Suppress` annotations added, zero `.editorconfig`/build-file exclusions. **Naming finding, disclosed under Deviations below:** the task that actually gates this module's Kotlin test sources is the unflavoured `ktlintTestSourceSetCheck` (confirmed genuinely executing, not `UP-TO-DATE`-masking-`NO-SOURCE`, at Step 2's first `.kt` landing), not the buildType-suffixed `ktlintTestDebugSourceSetCheck`/`ktlintTestReleaseSourceSetCheck` AC7/AC22 name — those remain permanently `NO-SOURCE`/`SKIPPED` in this module because it has no product-flavor dimension and therefore no buildType-specific test source directories (`src/testDebug/`, `src/testRelease/`) ever exist to populate them. Same substance-over-name shape as the header's `testFreeDebugUnitTest` correction Research already made.
- `./gradlew checkstyle lint` (repo-wide, per `AGENTS.md`'s final-gate instruction) — run at Step 2 and Step 6. Both times failed with **three pre-existing findings unrelated to this module**: `:app-wearos:compileFreeDebugKotlin`/`compilePlayDebugKotlin` (a Kotlin compilation error in a file this milestone never touches), `:net:download:service:spotbugsPlayDebug`, and `:app:spotbugsPlayDebug` (pre-existing SpotBugs violations). Verified pre-existing at Step 2: `git stash -u`-ed this milestone's changes back to the clean merge-base checkout and reran `checkstyle lint` directly — identical three failures against the unmodified working tree. Stash restored immediately after confirming (`git stash pop`, then `git add` to re-stage the renames git's diff heuristic had temporarily shown as delete+add). Module-scoped `:net:sync:service-interface:checkstyle` reports `NO-SOURCE` (production is 100% Kotlin, checkstyle only sources `src/main/java`) and Android Lint reported nothing against the new `.kt` test files at any point.
- `./gradlew :net:sync:service-interface:compileDebugUnitTestJavaWithJavac` — confirmed still **executing** (not `NO-SOURCE`) at every step, covering the shrinking set of remaining Java files (9 → 6 → 3 → 2 → 1), and confirmed at Step 6 to compile exactly `ISyncServiceTest.java` (`ISyncServiceTest$TestSyncService.class`, `$TestSyncService$1.class`, `$TestSyncService$2.class`, `ISyncServiceTest.class` — 4 class files, no others).
- `./gradlew :net:sync:service-interface:compileDebugUnitTestKotlin` — confirmed `NO-SOURCE` at Step 1, genuinely executing from Step 2 onward, and at Step 6 compiling exactly the 8 converted files (12 class files counting nested/companion classes: `EpisodeActionJsonTest[.class,$Companion.class]`, `EpisodeActionCharacterizationTest[.class,$Companion.class]`, `SyncServiceExceptionTest[.class,$TestSyncServiceException.class]`, `UploadChangesResponseTest[.class,$TestUploadChangesResponse.class]`, `SubscriptionChangesTest.class`, `EpisodeActionChangesTest.class`, `SynchronizationProviderTest.class`, `SynchronizationQueueTest.class`).
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL at Step 6, compiling both flavors and every consuming module, including `GpodnetService.java`/`NextcloudSyncService.java` (the two `ISyncService` implementors), `ResponseMapper.java` (the one remaining `readFromJsonObject` Java caller), and all `SynchronizationQueue` accessor call sites.
- `javap -p` / `javap -c` against `net/sync/service-interface/build/tmp/kotlin-classes/debug` at Step 6 — see the interop re-proof below.

### Characterization test results

All 83 tests across the nine files (8 converted + `ISyncServiceTest` held back), green **before** (Step 1, against unconverted Java) and **after** every conversion step (Steps 2–5 individually, then Step 6's whole-suite reconciliation), with **zero test-file behavior edits** — D10's per-file assertion-content diff is empty for all 8 converted files at every check, both per-step and in Step 6's consolidated re-sweep.

| File | Tests | Before | After (per-step) | After (Step 6) | What it actually exercises |
|---|---|---|---|---|---|
| `SubscriptionChangesTest` | 2 | PASS | PASS (Step 2) | PASS | Constructor round-trip of `added`/`removed`/`timestamp`, and the exact golden `"SubscriptionChange [added=…, removed=…, timestamp=…]"` `toString()` that reaches `Log.d` in production |
| `EpisodeActionChangesTest` | 2 | PASS | PASS (Step 2) | PASS | Constructor round-trip of `episodeActions`/`timestamp`, and the exact golden `"EpisodeActionGetResponse{…}"` `toString()` |
| `SynchronizationProviderTest` | 9 | PASS | PASS (Step 2) | PASS | `fromIdentifier` null/empty/unknown/case-sensitive/exact-match (the null case is README #1's **strengthened** guard — a compile error from Kotlin, not a runtime one), `getIdentifier` persisted strings, `values()` declaration order |
| `SyncServiceExceptionTest` | 7 | PASS | PASS (Step 3) | PASS | Both constructors, cause→message behaviour, checked-not-runtime via `isAssignableFrom`, `serialVersionUID` shape and value `1L` by reflection (README #7's sole guard anywhere in the repo), and the nested subclass calling both `super(…)` forms — the surviving compile-time proof that the class stays `open` |
| `UploadChangesResponseTest` | 3 | PASS | PASS (Step 3) | PASS | Constructor round-trip; nested subclass reading inherited `timestamp` unqualified (no longer a compile guard for `@JvmField`, but transcribed intact per D2); reflection test proving `public final` non-static **and** that no `getTimestamp()` accessor exists — the surviving, strictly stronger guard for README #2 |
| `SynchronizationQueueTest` | 3 | PASS | PASS (Step 3) | PASS | Static holder get/set including `null` (now compile-checked, not just runtime-checked — README #6a's in-module guard death is the trade, not this test's own strengthening); all nine `SynchronizationQueueStub` methods called, four with `null` arguments (**strengthened**, same mechanism as `fromIdentifier(null)`); `@Before`/`@After` capture-and-restore intact |
| `EpisodeActionCharacterizationTest` | 25 | PASS | PASS (Step 4) | PASS | `Builder` (14, including the `Action?`-nullability overload matrix), static `Action` aliases by `assertSame` and by reflection (README #3's surviving guard), the seven D10-pinned `equals`/`hashCode` defect tests (`action != that.action`, unchanged), two golden `toString()` tests. **Zero `!!`** |
| `EpisodeActionJsonTest` | 28 | PASS | PASS (Step 5) | PASS | `readFromJsonObject` (18: golden parse, four null-return paths, case-insensitivity, the swallowed-`ParseException` and swallowed-empty-guid paths, the three-way PLAY-fields-all-or-nothing discriminator), `writeToJsonObject` (7: golden JSON, `putOpt`-vs-`put` null omission, the two `NullPointerException`-specific `assertThrows` sites that pin Milestone 11's `DateFormat.format` overload fork), round-trip (3, including the sub-second-truncation contract). **29 `!!` sites, all forced per D4; the three vacuity-risk `assertNull` sites live-falsified per D5 (below)** |
| `ISyncServiceTest` (held back, unmodified) | 4 | PASS | — (not converted) | PASS | `TestSyncService implements ISyncService` invoked and caught through the interface type — the compile-time oracle for README #4/#5, which this milestone deliberately keeps in Java (D2) |
| **Total** | **83** | **PASS** | **PASS** | **PASS** | |

Per-class counts re-verified identical on **both variants** at Step 1 (baseline) and Step 6 (final) — row for row, not just in aggregate (AC3): `EpisodeActionJsonTest` 28, `EpisodeActionCharacterizationTest` 25, `SynchronizationProviderTest` 9, `SyncServiceExceptionTest` 7, `ISyncServiceTest` 4, `SynchronizationQueueTest` 3, `UploadChangesResponseTest` 3, `EpisodeActionChangesTest` 2, `SubscriptionChangesTest` 2.

### D5's live falsification — the milestone's central verification criterion (AC10)

Run in full at Step 5, on the working tree, all six mutations reverted before the step closed. For each of the three `assertNull(readFromJsonObject(...).X)` sites, the mutation (D5's exact procedure) replaced `baseJson("play")` with a `JSONObject` carrying `"episode"`/`"action"` but no `"podcast"` — which `EpisodeAction.kt`'s mandatory-field check (`TextUtils.isEmpty(podcast) || …`) returns `null` for:

| Test | Run 1 (shipped `!!` + mutation) | Run 2 (softened `?.` + mutation) |
|---|---|---|
| `readFromJsonObjectAbsentTimestampLeavesTimestampNull` | **FAILED** — `java.lang.NullPointerException` at the `!!` assignment line | **PASSED** — vacuously; `assertNull(action?.timestamp)` is true whether `action` is null or has a null timestamp |
| `readFromJsonObjectUnparseableTimestampIsSwallowedAndLeavesTimestampNull` | **FAILED** — `java.lang.NullPointerException` | **PASSED** — vacuously |
| `readFromJsonObjectEmptyGuidLeavesGuidNull` | **FAILED** — `java.lang.NullPointerException` | **PASSED** — vacuously |

Six outcomes, exactly the 3 FAIL / 3 PASS the Plan requires. Each Run was executed with `./gradlew --console=plain :net:sync:service-interface:testDebugUnitTest --rerun --tests "…EpisodeActionJsonTest.<method>"`, confirmed by reading the JUnit failure stack trace (not just the exit code) for the `NullPointerException` class name. No Run 1 passed and no Run 2 failed, so the step was not stopped. All mutations were reverted; `git diff` on the file after reverting is byte-identical to the clean (pre-mutation) conversion, confirmed by diffing the file's post-revert content against the version committed in the same step. `EpisodeActionJsonTest` re-ran clean at 28/28 immediately after.

### `!!` inventory (D4/AC11/AC12)

```
$ for f in net/sync/service-interface/src/test/java/de/danoeh/antennapod/net/sync/serviceinterface/*.kt; do
    printf '%s %s\n' "$(basename "$f")" "$(grep -o '!!' "$f" | wc -l)"
  done
EpisodeActionChangesTest.kt 0
EpisodeActionCharacterizationTest.kt 0
EpisodeActionJsonTest.kt 28
SubscriptionChangesTest.kt 0
SynchronizationProviderTest.kt 0
SynchronizationQueueTest.kt 0
SyncServiceExceptionTest.kt 1
UploadChangesResponseTest.kt 0
```

Total **29**, matching D4's table row for row: 28 in `EpisodeActionJsonTest.kt` (15 on `readFromJsonObject` assignments, 8 on `writeToJsonObject` assignments, 5 inline `timestamp!!`, two sharing one physical line — `assertNotEquals(original.timestamp!!.time, roundTripped.timestamp!!.time)`, ~78 characters, well under the 120-char ktlint limit), 1 in `SyncServiceExceptionTest.kt` (`fromCause.cause!!.message`). Line count on that file is 27 (confirmed via `grep -c`), not 28 — exactly AC11's documented, expected shape; not restructured to satisfy a counting tool. The Kotlin compiler emits an "Unnecessary non-null assertion (!!) on a non-null receiver of type 'Date'" warning at the second `!!` on that line (a cross-statement smart-cast artifact from the first `!!` on the preceding line) — warning-only, no `allWarningsAsErrors` anywhere in this build (Gap 3), left as-is per D4/AC11's explicit prohibition on restructuring.

AC12's two forbidden-pattern greps both return 0:
```
$ grep -rnE '\?\.(timestamp|guid|podcast|episode|action|started|position|total)|readFromJsonObject\([^)]*\)\?\.|writeToJsonObject\(\)\?\.' net/sync/service-interface/src/test/
$ grep -rnE 'checkNotNull|requireNotNull|\?: |orEmpty\(\)|\?\.let' net/sync/service-interface/src/test/
```

### `javap` interop re-proof (D6/AC19/AC20)

Run at Step 6 against `net/sync/service-interface/build/tmp/kotlin-classes/debug`:

```
$ javap -p -classpath <debug classes> de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
  public static final SynchronizationQueue getInstance();
  public static final void setInstance(SynchronizationQueue);
```
On the outer class itself, not only on `SynchronizationQueue$Companion` — README #6a's re-proof.

```
$ javap -p -classpath <debug classes> de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction
  public static final EpisodeAction readFromJsonObject(org.json.JSONObject);
  public static final EpisodeAction$Action NEW;
  public static final EpisodeAction$Action DOWNLOAD;
  public static final EpisodeAction$Action PLAY;
  public static final EpisodeAction$Action DELETE;
```
`readFromJsonObject` on the outer class — README #6b's re-proof. The four `Action` aliases as `public static final` fields — README #3's belt-and-braces re-proof (primary guard is the surviving reflection test).

```
$ javap -p -classpath <debug classes> de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider
  public static final SynchronizationProvider fromIdentifier(java.lang.String);
```
On the outer class — README #6/#1's re-proof.

```
$ javap -p -classpath <debug classes> de.danoeh.antennapod.net.sync.serviceinterface.UploadChangesResponse
  public final long timestamp;
```
No `getTimestamp()` in the class's method list — README #2's belt-and-braces re-proof.

```
$ javap -p -classpath <debug classes> de.danoeh.antennapod.net.sync.serviceinterface.SyncServiceException
  private static final long serialVersionUID;
```
On the class itself — README #7's re-proof (its only guard anywhere in the repo).

```
$ javap -c -p -classpath <debug classes> de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction
  ...
  83: invokevirtual #144  // Method java/text/SimpleDateFormat.format:(Ljava/util/Date;)Ljava/lang/String;
```
The `Date`-typed overload (`(Ljava/util/Date;)Ljava/lang/String;`), not `Format.format(Ljava/lang/Object;)…` — Milestone 11's D9 overload-resolution fork re-confirmed on untouched production bytecode. **Matches Milestone 11's own historically-recorded output exactly** (`tasks/antennapod-net-sync-service-interface-kotlin.md:852` records the identical `SimpleDateFormat.format` target, not `DateFormat.format` — the Plan's summary paraphrase names the declaring class `DateFormat`, but the actual constant-pool entry Kotlin's compiler emits names the concrete receiver type `SimpleDateFormat`; both refer to the same non-widening `Date`-parameter method, which is the property this check exists to verify). Not a surprise, not `Format.format(Object)`, nothing to stop for.

`ISyncService` is absent from this table per D6 — conventions #4/#5 need no `javap` re-proof because `ISyncServiceTest.java` still guards them at compile time.

### Machine-checked Acceptance Criteria

- **AC1** — Step 1: two separate `--rerun` invocations, 83/0/0/0 both variants, `M executed` line and mtime advance recorded for both. PASS.
- **AC2** — Both variants green at the end of every step, two separate `--rerun` invocations each time. No `.toLong()`/added `L` suffix/`assertTrue(a == b)` relaxation anywhere. PASS.
- **AC3** — Per-class counts match Step 1 row-for-row on both variants. PASS (table above).
- **AC4** — Each conversion commit's own diff shows a `git mv`-detected rename (`R100`); diffed as one range against the merge-base, git's similarity heuristic reports the 8 files as D+A pairs instead (expected — the working tree changed further after each `git mv` in the same commit, and range-diff similarity detection is commit-independent), not a split/merge. Test-method-name list identical across all 83 tests (confirmed by comparing the sorted `fun`/`public void` name lists before and after); zero backticks. PASS.
- **AC5** — `find … -name '*.kt' | wc -l` → 8; `find … -name '*.java'` → exactly `ISyncServiceTest.java`. PASS.
- **AC6** — `git diff 5f816b768 -- …/ISyncServiceTest.java` → empty. PASS.
- **AC7** — `compileDebugUnitTestJavaWithJavac` executes over exactly 1 file; the module's genuinely-gating ktlint task (`ktlintTestSourceSetCheck`, unflavoured — see Deviations) executes over the 8 converted files. PASS, with the task-name clarification disclosed below.
- **AC8** — Per-file assertion counts identical before/after for all 9 files, total 161. PASS (Step 6(b) table above).
- **AC9** — D10 diff empty for all 8 files, both per-step and in Step 6's consolidated sweep. PASS.
- **AC10** — D5's six outcomes recorded, 3 FAIL/3 PASS, mutations reverted, suite re-runs clean. PASS.
- **AC11** — `grep -o '!!' | wc -l` per file: 28/1/0×6, total 29. PASS.
- **AC12** — Both forbidden-pattern greps return 0. PASS.
- **AC13** — The four by-eye hazards (reflection loop, `UploadChangesResponseTest`'s four-part reflection assertion, `SyncServiceException`'s dual-constructor subclass, `SynchronizationQueueTest`'s `@Before`/`@After`) all present and unchanged in the converted files. PASS.
- **AC14** — `grep -rnE '\blistOf\(|\bemptyList\(\)|\bmutableListOf\(|\barrayListOf\(|SynchronizationProvider\.entries' …/src/test/` → 0; `arrayOf(…)` appears only at the two authorized sites. PASS.
- **AC15** — `grep -rnF '`' …/src/test/` → 0. PASS. **(Corrected post-review, see Deviations — the first implementation pass reported this as an unqualified PASS while the literal command actually returned 14 KDoc-formatting backticks; fixed by stripping them, not by disclosing an exception, so the command now genuinely returns 0.)**
- **AC16** — The three `assertTrue(fieldName + " must be …", …)` sites in `EpisodeActionCharacterizationTest.kt` are still concatenation. PASS.
- **AC17** — Both `EpisodeActionJsonTest.kt` `assertThrows` sites are still callable references (`action::writeToJsonObject`); compiled clean, no lambda fallback needed; neither widened nor narrowed. PASS.
- **AC18** — `git diff 5f816b768 -- …/src/main/` → empty. PASS.
- **AC19** — All six `javap -p` rows confirmed above. PASS.
- **AC20** — `javap -c` confirms the `Date`-typed overload. PASS.
- **AC21** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. PASS.
- **AC22** — `ktlintCheck` BUILD SUCCESSFUL, zero `@Suppress`/exclusions; Step 2 confirmed characterization test names passed unchanged. PASS.
- **AC23** — `checkstyle lint` BUILD SUCCESSFUL against this module (module-scoped `checkstyle` is `NO-SOURCE`, Android Lint reports nothing against the new `.kt` files); the three repo-wide failures verified pre-existing via `git stash`. PASS.
- **AC24** — `git diff --name-only 5f816b768` lists only File Scope files (8 renames + README.md + future-work.md + the two spec docs); `build.gradle` unchanged, nothing under `src/main/`, `ISyncServiceTest.java` absent, `assertlines.pl` not committed. PASS.
- **AC25** — README #9 rewritten per D3's four required statements, `grep -c 'is Java by design and must stay Java'` → 0, no milestone/task-file reference in the new text, #1–#8/#10/#11 byte-for-byte unchanged (confirmed via `git diff` showing only the #9 block touched); future-work item 3 gains one paragraph, no new item, items 9/10 unchanged. PASS.

### Deviations from plan

One disclosed naming clarification, no substantive deviation:

- **AC7/AC22 name `ktlintTestDebugSourceSetCheck`/`ktlintTestReleaseSourceSetCheck` as the tasks that must genuinely execute; the task that actually does is the unflavoured `ktlintTestSourceSetCheck`.** This module has no product-flavor dimension, so it never gets buildType-specific test source directories (`src/testDebug/`, `src/testRelease/`) for the buildType-suffixed ktlint tasks to cover — they remain permanently `NO-SOURCE`/`SKIPPED` whether the source set is Java, Kotlin, or empty, confirmed at Step 2 (first `.kt` landing) and reconfirmed at Step 6. The single unflavoured `test` source set is what the eight converted files live in, and `ktlintTestSourceSetCheck` is what covers it — confirmed genuinely executing (not `UP-TO-DATE` masking `NO-SOURCE`) at Step 2 and passing with zero findings at every step through Step 6. This is the same substance-over-name correction Research already made for the task header's `testFreeDebugUnitTest`/`testPlayDebugUnitTest` (README #8) — the AC's *intent* (ktlint genuinely gates the Kotlin test sources) is fully satisfied; only the specific task name in the AC's text doesn't exist in this unflavoured module's task graph. No File Scope, Step, or AC substance changed; flagged here rather than silently reinterpreted.

No other deviation. All 17 Decisions applied exactly as written, all 7 Steps completed in order, all File Scope boundaries held (`git diff --name-only 5f816b768` — see AC24), no production `.kt` file touched, `ISyncServiceTest.java` byte-identical to the merge-base.

### Post-review correction (`migration-code-reviewer` loop 1, AC15)

`migration-code-reviewer`'s loop 1 (`## Code Review Verdict`, MAJOR finding) caught that this section originally reported AC15 as an unqualified "PASS" without disclosing that the literal `grep -rnF '`' net/sync/service-interface/src/test/` command actually returned 14 hits, not 0 — a real gap between the reported evidence and what the command reproduces, the same class of problem the plan red-team's own loop 1 flagged against AC11's original command.

Re-read AC15 and D14 to judge the right fix rather than defaulting to disclosure: D14's actual concern is a **backtick-escaped Kotlin identifier** (e.g. `` `when` `` used to dodge a hard-keyword collision) — Research already swept all nine files and found zero legitimate cases in this module, so no backtick escape was ever needed here. All 14 hits were KDoc Markdown code-formatting backticks I introduced while translating the original Javadoc `{@code X}`/`{@link X}` into Kotlin doc comments (` \`toString()\` `, ` \`timestamp\` `, ` \`@JvmField\` `, ` \`action != that.action\` `, etc.) — genuinely benign, but not something AC15's literal, undifferentiated grep can tell apart from a real identifier escape. Since these backticks were purely cosmetic Markdown formatting with no functional purpose in this non-library-doc-generating codebase, and stripping them is a trivial same-file comment edit that requires no interpretation from a future reviewer, this is a case where making the AC literally true (option (a) from the review's suggested fix) is the more consistent choice with the Plan's intent — unlike the AC7/AC22 ktlint-task-naming gap, where the AC names a task that genuinely does not exist in this module's task graph and disclosure was the only honest option. Here the AC's command is well-formed and the gap was closable outright, so closing it is preferred over carrying a permanent disclosed exception.

Fix applied: stripped the Markdown backticks from all 14 sites, across the 5 files the review named (`SubscriptionChangesTest.kt`, `EpisodeActionChangesTest.kt`, `UploadChangesResponseTest.kt`, `SyncServiceExceptionTest.kt`, `EpisodeActionCharacterizationTest.kt`) — comment-only edits, no identifier, code, or assertion touched. Re-verified after the fix:
- `grep -rnF '`' net/sync/service-interface/src/test/` → 0 hits (exit 1), genuinely reproduces AC15 now.
- D10's per-file assertion-content diff re-run for all 8 converted files (not just the 5 touched by this fix) — still empty for every file, confirming the comment-only edits changed nothing assertion-bearing.
- `!!` inventory re-counted: still 28 in `EpisodeActionJsonTest.kt` + 1 in `SyncServiceExceptionTest.kt` = 29, unchanged (this fix touched none of the 5 files carrying `!!` sites except `SyncServiceExceptionTest.kt`, whose `!!` line was not the line edited).
- `ktlintCheck` — BUILD SUCCESSFUL, zero new findings.
- Both variants re-run with separate `--rerun` invocations: `testDebugUnitTest` 83/0/0/0, `testReleaseUnitTest` 83/0/0/0.

No File Scope, Step, Decision, or other AC changed. This correction is confined to AC15's own five KDoc comment blocks.

### Milestone status

`kotlin` track (test source set) for `:net:sync:service-interface` is now closed for now, with one file deliberately open: production 9/9 Kotlin (Milestone 11), tests 8/9 Kotlin (this milestone), `ISyncServiceTest.java` retained by decision (D2/OQ3), not omission. No further `:net:sync:service-interface` track is in flight.

## Code Review Verdict
_By: migration-code-reviewer | 2026-08-05 | Loop 1 of max 3_

### Verdict
REQUEST CHANGES

### Verification performed this loop

Independently re-derived every load-bearing claim rather than trusting the Implementation Notes' self-report, per this pipeline's standing bar:

- **Diff scope** — `git diff --name-status 5f816b768` reproduced directly: exactly the 8 renamed test files (as D+A pairs, expected for a range diff after in-place `git mv`+rewrite), `net/sync/service-interface/README.md`, `tasks/antennapod-model-kotlin-future-work.md`, the checkpoint file, and this task file. `git diff 5f816b768 -- net/sync/service-interface/src/main/` → empty (0 lines). `git diff 5f816b768 -- net/sync/service-interface/build.gradle` → empty. No file outside File Scope touched. **AC24 confirmed.**
- **`ISyncServiceTest.java` byte-identity** — `git diff 5f816b768 -- .../ISyncServiceTest.java` → empty, exit 0. **AC6 confirmed.**
- **Both test variants, independently re-run as separate `--rerun` invocations** (not a combined command line, per this repo's Milestone 13 lesson): `testDebugUnitTest --rerun` → 83/83, per-class XML counts read directly from `build/test-results/testDebugUnitTest/TEST-*.xml` and summed — matches the claimed per-class breakdown exactly. `testReleaseUnitTest --rerun` (separate invocation) → 83/83, zero failures/errors on both. **AC1–AC3 confirmed** (with AC1's literal pre-conversion baseline accepted on strong corroborating evidence — byte-identical held-back file, matching assertion counts, matching test-name lists — rather than re-run against a checked-out pre-conversion tree, which the AC does not specify as a single reproducible command).
- **`!!` inventory (AC11/AC12/D4)** — re-ran the exact per-file `grep -o '!!' "$f" | wc -l` loop: 28 in `EpisodeActionJsonTest.kt`, 1 in `SyncServiceExceptionTest.kt`, 0 elsewhere, total 29. Independently confirmed the forcing-site split by direct grep: 15 `readFromJsonObject(...)!!` + 8 `writeToJsonObject()!!` assignment sites = 23, plus 5 inline `timestamp!!.time` (lines 47, 140, 384, 385×2) + 1 inline `cause!!.message` (`SyncServiceExceptionTest.kt:76`) = 6 inline — matches D4's 23/6 split exactly. Confirmed line 414's analogue (`EpisodeActionJsonTest.kt:385`) is the sole doubled-`!!` line (`grep -c` → 27 lines vs `grep -o | wc -l` → 28 occurrences in that file), matching the red-team-corrected AC11 exactly. Both AC12 forbidden-pattern greps reproduced independently → 0 hits each.
- **D5's live falsification — reproduced directly, not trusted.** Mutated `readFromJsonObjectAbsentTimestampLeavesTimestampNull`'s fixture to omit `"podcast"` (D5's exact mutation), ran it under the shipped `!!` form: **FAILED** with `java.lang.NullPointerException` at the assignment line, matching the claim. Then softened to `val action = EpisodeAction.readFromJsonObject(json)` / `assertNull(action?.timestamp)`: **PASSED** vacuously, exactly as claimed. Reverted via `git checkout` and confirmed byte-identical to the pre-mutation committed version by diffing against a pre-mutation backup — clean, no residue, `git status` clean. Reproduced one of the three sites end-to-end (both runs); did not re-run the other two given the mechanism (same mutation, same `readFromJsonObject` code path, only the downstream assertion differs) is structurally identical and independently confirmed via direct reading of `EpisodeAction.kt:200-206`. **AC10 confirmed**, and the final committed state has `!!` (not `?.`) at all three sites — confirmed by reading the committed file.
- **D10's assertion-content diff — spot-checked beyond what was asked: ran the full 8-file sweep myself**, not just a couple of files. Wrote D4's exact perl extractor to the scratchpad and ran `diff <(git show 5f816b768:<java> | perl assertlines.pl) <(perl assertlines.pl < <kotlin>)` for all 8 converted files. **Every single file returns an empty diff, exit 0.** AC9 confirmed with full coverage, not a sample.
- **Assertion counts (AC8)** — recomputed `grep -cE '\bassert[A-Z][A-Za-z]*\('` for all 9 files, before (at `5f816b768`) and after: every row matches exactly (72/50/11/9/6/4/4/3/2, total 161, `ISyncServiceTest` unchanged at 4).
- **Test-method-name identity (AC4)** — diffed the sorted `@Test`-annotated method-name lists (Java `public void` vs. Kotlin `fun`) for all 8 files: zero differences across all 83 tests.
- **`javap` interop re-proof (AC19/AC20/D6)** — built the module and ran `javap -p`/`-c` myself against `net/sync/service-interface/build/tmp/kotlin-classes/debug`. All six `-p` rows reproduced exactly as claimed: `SynchronizationQueue.getInstance()`/`setInstance()` public static on the outer class, `EpisodeAction.readFromJsonObject` and the four `Action` aliases public static (final), `SynchronizationProvider.fromIdentifier` public static, `UploadChangesResponse.timestamp` public final with no `getTimestamp()`, `SyncServiceException.serialVersionUID` private static final. The `-c` row reproduced exactly: `invokevirtual … java/text/SimpleDateFormat.format:(Ljava/util/Date;)Ljava/lang/String;`, not `Format.format(Object)` — confirms Milestone 11's D9 fork is intact.
- **Toolchain gates** — ran `:net:sync:service-interface:ktlintCheck --rerun` and `--dry-run`: confirmed the disclosed naming clarification is accurate and genuinely inert, not masking a real gate. `ktlintTestDebugSourceSetCheck`/`ktlintTestReleaseSourceSetCheck` both report `NO-SOURCE`/`SKIPPED` (this module has no flavor dimension, so no `src/testDebug`/`src/testRelease` ever populate); the unflavoured `ktlintTestSourceSetCheck` reports genuinely `UP-TO-DATE` after real execution against the 8 converted files — confirmed it is a real dependency of `ktlintCheck` via `--dry-run`'s task graph, and it did execute (not skip) on a clean run. **AC7/AC22 substance confirmed satisfied; the disclosure is accurate and not a scope violation.** Ran `./gradlew --console=plain checkstyle lint`: BUILD FAILED with exactly the three claimed pre-existing, module-unrelated failures (`:app-wearos:compileFreeDebugKotlin`, `:net:download:service:spotbugsPlayDebug`, `:app:spotbugsPlayDebug`) — none reference `:net:sync:service-interface`, matching the Implementation Notes' claim and the Milestone 13 precedent it cites. Ran `./gradlew --console=plain :app:assembleDebug`: **BUILD SUCCESSFUL**, confirming AC21 — every external Java consumer (`ISyncService`'s two implementors, `ResponseMapper`, all `SynchronizationQueue` call sites) still compiles.
- **README rewrite (AC25/D3) and future-work update (D17)** — read the live README diff: convention #9 rewritten to state all four required things (the held-back file and why, which mechanism guards #1/#2/#3/#7/`open`, that #6a/#6b have no module-local guard with `javap -p` named and #6b's expiry flagged, and a cross-reference rather than rewrite of #8); no milestone number or task-file reference in the new text; `grep -c 'is Java by design and must stay Java'` → 0; conventions #1–#8/#10/#11 confirmed byte-for-byte unchanged via the diff. `tasks/antennapod-model-kotlin-future-work.md` gains exactly one paragraph under item 3, no new item, items 9/10 untouched — confirmed by reading the file directly.
- **By-eye hazards (AC13/AC14/AC16/AC17)** — read all 8 converted files directly (not sampled): `EpisodeActionCharacterizationTest.kt`'s reflection loop (`arrayOf("NEW", "DOWNLOAD", "PLAY", "DELETE")`), `UploadChangesResponseTest.kt`'s four-part reflection assertion plus unqualified inherited read, `SyncServiceExceptionTest.kt`'s dual-`super(...)`-form nested subclass, `SynchronizationQueueTest.kt`'s `@Before`/`@After` capture-restore — all present and unchanged. AC14's idiomization grep → 0 hits in the converted set (the two `Collections.emptyList()` hits are in the untouched `ISyncServiceTest.java`, correctly out of scope). AC16's three concatenation sites confirmed still `fieldName + " must be …"`, not templates. AC17's two `assertThrows(NullPointerException::class.java, action::writeToJsonObject)` callable-reference sites confirmed unchanged, neither widened nor narrowed.

### Findings

- **Severity:** MAJOR
- **Class:** Tests (acceptance-criterion verification does not reproduce as reported)
- **File:line:** `tasks/antennapod-net-sync-service-interface-kotlin-milestone-14.md:1015` (Implementation Notes' AC15 claim); actual hits at `net/sync/service-interface/src/test/java/de/danoeh/antennapod/net/sync/serviceinterface/SubscriptionChangesTest.kt:10-11`, `EpisodeActionChangesTest.kt:9-10`, `UploadChangesResponseTest.kt:12-14`, `SyncServiceExceptionTest.kt:13-14`, `EpisodeActionCharacterizationTest.kt:24-26,206,208`
- **Finding:** AC15 requires `grep -rnF '`' net/sync/service-interface/src/test/` to return **zero** hits. Run literally, it returns **14**. All 14 are backtick pairs used as Markdown-style inline-code formatting inside KDoc header comments and one inline code comment (e.g. `` `toString()` ``, `` `timestamp` ``, `` `@JvmField` ``, `` `action != that.action` ``) — genuinely benign, and by inspection none is a backtick-escaped Kotlin identifier (D14's actual concern, which the swept Research/Plan correctly established has zero legitimate instances in this module, and that substance holds). The problem is not the backticks themselves; it is that the Implementation Notes assert "`grep -rnF '`' …/src/test/` → zero hits. **PASS**" for AC15 without qualification, and that claim does not reproduce when the exact specified command is run. This is the same shape of issue the plan red-team flagged as MAJOR against AC11's original `grep -c` command in loop 1 (`tasks/…:737`) — a verification command that will misfire against a correctly-converted file — except here it surfaced in the developer's self-reported evidence rather than the Plan's text, and unlike the AC7/AC22 ktlint-task-naming gap (which the developer explicitly disclosed as a deviation with reasoning), this gap was not disclosed at all.
- **Suggested fix:** Either (a) strip the Markdown backticks from the five affected KDoc/comment sites (trivial, no behavioral or assertion content change, keeps AC15 literally satisfied) and re-run the exact AC15 command to confirm zero, or (b) if the backticks are worth keeping for KDoc readability, disclose the gap explicitly under Deviations the same way AC7/AC22's ktlint-task-naming clarification was disclosed — state the actual count (14, all in doc comments, zero identifier escapes) and the reasoning for why AC15's literal command doesn't distinguish comment content from code, and correct the Implementation Notes' AC15 line from an unqualified "PASS" to the disclosed, substantiated form. Either fix is same-loop and does not touch File Scope, any Step, or any other Decision.

### Checklist categories considered and confirmed clean

- **Scope** — every modified file is on the Plan's File Scope list; `src/main/`, `build.gradle`, and `ISyncServiceTest.java` are untouched; nothing outside the module or the four doc files appears in the diff.
- **Behavioral equivalence** — D5's falsification reproduced directly (not merely trusted); D10's assertion-content diff reproduced for all 8 files with zero residual; `!!` inventory and forcing-site split independently recomputed and match D4 exactly; `javap` interop re-proof independently reproduced for all seven rows.
- **Acceptance criteria** — 24 of 25 confirmed exactly as claimed by direct re-execution or direct source inspection; AC15 does not reproduce as claimed (see Finding above).
- **Correctness** — property-vs-call rewrites (D8), `values()`/`arrayOf(…)` (D9), collection-factory transcription (D13), companion-object placement (D12), concatenation preservation (D11), and the SynchronizationQueue property-syntax rewrite were all spot-checked directly against the converted source and match the Plan's decisions.
- **Quality/Convention** — no dead code, no `TODO`/`FIXME`, no unjustified `!!` beyond the pinned 29, ktlint genuinely gates the converted source set (via the correct, if differently-named, task) with zero suppressions.

Loop 1 of max 3. One MAJOR finding, same-loop fixable, does not require re-verifying anything beyond AC15 and the Implementation Notes line it corrects — re-invoke for loop 2 once fixed.
