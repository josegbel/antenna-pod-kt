# antennapod-model-kotlin-milestone-7

> **Description:** Convert the `:model` module's remaining Java test files (and helper `*Mother` classes) to Kotlin, now that all production code in `:model` is 100% Kotlin (Milestones 1–6 complete).
> **Repo:** `services/android-migration/projects/portfolio/antennapod`
> **Created:** 2026-07-25

> **Pre-research context (carried over from Milestones 1–6 / standing decisions — do not re-derive):**
> - `:model` production code is **100% Kotlin** as of Milestone 6 (PR #11, merged). Per the standing test-migration-sequencing decision, characterization tests were deliberately kept in Java, unchanged, across every production-code milestone — this was the trigger condition for that deferral, and it is now met.
> - `model/src/test/java/...` currently contains **29 Java files**: 26 `*Test.java` classes plus 3 helper "Mother" builders (`FeedItemMother`, `FeedMediaMother`, `FeedMother`). All must move to `model/src/test/java` still (Kotlin files coexist in the same source set; no new source set needed) — verify actual package/dir layout in research, do not assume.
> - This milestone is a **test-only** conversion — no production `.kt` file should need to change as a result (unless a genuine J2K-interop issue forces a minimal, disclosed fix, per the module's established deviation-disclosure pattern from Milestones 4/6).
> - `:model`'s Robolectric dependency (added in Milestone 6, scoped to the Parcel-marshalling test cluster) and the module's otherwise-Robolectric-free precedent both still apply — do not change test *behavior* here, only its language.
> - See `tasks/antennapod-model-kotlin-future-work.md` for the full backlog of deferred items and standing open questions (KMP/Parcelable decoupling, upstreaming intent) — neither is in scope for this milestone.
> - **This milestone's PR packaging deviates from the M3/M4/M6 split-PR precedent**: per explicit instruction this session, code and spec-workflow docs (this task file + its checkpoint) ship together in **one PR**, not two.

## Research
_Last updated by: legacy-android-researcher | 2026-07-25_

### Summary

`:model` is a small, framework-light data module whose production code is now 100% Kotlin — 27 `.kt` files under `model/src/main/java/de/danoeh/antennapod/model/`, zero `.java`. Its test source set is the exact mirror image: 29 `.java` files, zero `.kt`, all under `model/src/test/java/de/danoeh/antennapod/model/` in three packages (`model`, `model.feed`, `model.download`, plus `model.playback`). The inventory in the pre-research callout is confirmed correct against live source: 26 `*Test.java` classes plus `FeedItemMother`, `FeedMediaMother`, `FeedMother`. The suite is green — a forced `./gradlew :model:testDebugUnitTest --rerun` (the cached run was `UP-TO-DATE` and had to be discarded) reports **232 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESSFUL. These 232 tests are the entire safety net for this milestone: because the conversion is test-only, there is no independent oracle. The suite verifies itself, so any test that silently stops asserting what it used to assert is undetectable by a green build. That is the dominant risk of this milestone and it is what the Constraints & Risks section is mostly about.

The requested `kotlin` track applied to these files is mechanically small but has a long tail of language-level traps that a naive J2K pass gets wrong *silently* rather than loudly. Three categories matter. First, **numeric overload resolution**: Java's implicit int→long widening inside `assertEquals` disappears in Kotlin, turning three currently-passing assertions into boxed `Integer` vs `Long` comparisons that fail. Second, **Kotlin hard keywords used as identifiers** — `is` (12 static-imported hamcrest calls) and `in` (4 `ObjectInputStream` variables) — which are compile errors, so loud and safe, but shape the diff. Third, **the three `Mother` helpers**, whose Java `static` + package-private shape has no direct Kotlin equivalent; their final Kotlin form determines whether a partially-converted intermediate state compiles at all, which makes them a sequencing decision, not a style one. Separately, converting these files moves them across a static-analysis boundary: `checkstyle` covers only `src/main/java` and so never saw them, while `ktlintTestSourceSetCheck` is registered and currently a no-op — it starts enforcing the moment the first `.kt` lands in the test source set. No `model/build.gradle` change is needed. Worth flagging up front: there are **zero Kotlin test files anywhere in this repository today**, so this milestone sets the repo's first Kotlin-test precedent with nothing to copy from.

### Findings

#### Existing surface

29 Java files, 2,940 LOC total, under `model/src/test/java/de/danoeh/antennapod/model/`:

| Package | Files |
|---|---|
| `model` (root) | `MediaTypeTest`, `SortOrderTest`, `VolumeAdaptionSettingTest` |
| `model.download` | `DownloadErrorTest`, `DownloadRequestTest`, `DownloadResultTest`, `DownloadStatusTest`, `ProxyConfigTest` |
| `model.feed` | `ChapterTest`, `EmbeddedChapterImageTest`, `FeedCounterTest`, `FeedFilterTest`, `FeedFundingTest`, `FeedItemFallbackLinkTest`, `FeedItemFilterTest`, `FeedItemTest`, `FeedMediaTest`, `FeedOrderTest`, `FeedPreferencesTest`, `FeedTest`, `SubscriptionsFilterTest`, `TranscriptSegmentTest`, `TranscriptTest`, `TranscriptTypeTest`, **`FeedItemMother`**, **`FeedMediaMother`**, **`FeedMother`** |
| `model.playback` | `RemoteMediaTest`, `TimerValueTest` |

Note the three root-package tests (`MediaTypeTest`, `SortOrderTest`, `VolumeAdaptionSettingTest`) sit in `de.danoeh.antennapod.model` while their subjects live in `.feed`/`.playback`. Each file's `package` declaration matches its directory, so this is internally consistent — but it means those three cannot use package-private access to their subjects and never did.

No `androidTest` source set exists in `:model` (`model/src/` contains only `main` and `test`), so instrumented tests are not in play.

#### Where state / data lives

Confirmed effectively N/A, with two exceptions worth naming because both are *mutable global state reached from tests*:

- `VolumeAdaptionSetting` holds a companion-object `private var boostSupported: Boolean?` (`VolumeAdaptionSetting.kt:23`), mutated via the `@VisibleForTesting` `setBoostSupported()` (`:52`). `VolumeAdaptionSettingTest` sets it `false` in `@Before` (`:19`) and resets it to `null` in `@After` (`:24`). The `@After` is load-bearing: without it, `isBoostSupported()`'s real path (`AudioEffect.queryEffects()`, `VolumeAdaptionSetting.kt:37`) would be reached by later tests and throw on the bare JVM. If the conversion drops or reorders that `@After`, the failure surfaces as an unrelated test failing, not this one.
- Four test classes install a `MockedStatic<TextUtils>` in `@Before` and `close()` it in `@After` (`EmbeddedChapterImageTest.java:36-46`, `FeedPreferencesTest.java:34-56`, `SubscriptionsFilterTest.java:28-59`). An unclosed `MockedStatic` leaks across the whole JVM fork and corrupts unrelated classes. Same "loud failure in the wrong place" property.

#### Platform-specific notes

**Runner distribution (verified live, not from memory — this corrects the task-prompt assumption):**

- `@RunWith(RobolectricTestRunner.class)` — **exactly 3 files**: `DownloadRequestTest.java:19`, `FeedMediaTest.java:30`, `RemoteMediaTest.java:22`.
- **`EmbeddedChapterImageTest` does NOT use Robolectric.** The prompt listed it as a fourth Robolectric file; it is not. It runs on the plain JUnit4 runner and handles its Android dependency with `mockStatic(TextUtils.class)` instead (`EmbeddedChapterImageTest.java:38`). The distinction matters: it means the Milestone 6 Robolectric exception is narrower than assumed and its README constraint ("do not add Robolectric to new tests outside this Parcel-marshalling cluster", `model/README.md`) is still fully intact.
- `@RunWith(Parameterized.class)` — **1 file**: `FeedItemFallbackLinkTest.java:14`.
- The other 22 files use the default JUnit4 runner with no `@RunWith` at all.

**Mockito** (5.15.2, `gradle/libs.versions.toml:78`) is used in 4 files only: `EmbeddedChapterImageTest`, `FeedMediaTest`, `SubscriptionsFilterTest`, `FeedPreferencesTest`. **No PowerMock anywhere.** Static mocking is Mockito's own `mockStatic`, all three sites on `android.text.TextUtils`. `FeedMediaTest` additionally mocks the *Kotlin final class* `FeedItem` (`FeedMediaTest.java:45,62,79`) — this works today only because mockito-core 5.x defaults to the inline mock maker. It is unaffected by the conversion (test language does not change mockability), but it is a latent constraint worth not disturbing.

**Java-only idioms — scanned for exhaustively, results are mostly negative:**

- Anonymous inner classes: **none**.
- Static nested classes: **none**.
- `@SuppressWarnings`: **none**.
- `@Rule` / `@ClassRule`: **none**. Only `@Before`/`@After` (7 classes) and `@Parameters` (1).
- `@Ignore`: **none** (matches the 0-skipped test result).
- Varargs *declarations*: none. Varargs *call sites* into production Kotlin exist (`new FeedItemFilter("played", "queued")`, `FeedItemFilterTest.java:31`) but target a Kotlin `vararg String` and pass discrete arguments, so no spread operator is needed.
- Reflection: **1 site, still reflection-based** — `FeedMediaTest.getHasEmbeddedPictureField()` (`FeedMediaTest.java:195-203`) does `FeedMedia.class.getDeclaredField("hasEmbeddedPictureField")` + `setAccessible(true)`. This is the Milestone 6 `hasEmbeddedPicture` → `hasEmbeddedPictureField` rename the checkpoint mentions. It inspects the *private backing field* of `FeedMedia.kt` to assert it restores to `null` after a Parcel round trip, because no public getter reads the raw field without triggering computation. **The string literal `"hasEmbeddedPictureField"` is a hard dependency on a Kotlin private field name and must survive the conversion byte-for-byte.** In Kotlin the class literal becomes `FeedMedia::class.java`.
- Try-with-resources: **8 blocks across 4 files** (`FeedItemTest`, `FeedTest`, `FeedFundingTest`, `FeedPreferencesTest`, 2 each) wrapping `ObjectOutputStream`/`ObjectInputStream` for `Serializable` round-trip tests. J2K renders these as `.use { }`, which moves `close()` to after the catch body rather than before it. Here the blocks have no catch clause and the streams are in-memory `ByteArrayOutputStream`-backed, so the reordering is inert — but this is the same construct the Milestone 6 code review flagged as a non-blocking observation on `FeedMedia.kt`, so it will likely be raised again.
- Lambdas: used throughout as `assertThrows(..., () -> ...)` and Mockito `thenAnswer(invocation -> ...)`. These become Kotlin lambdas; `assertThrows`'s `ThrowingRunnable` is a SAM interface so conversion is clean.

**Toolchain gates — the boundary this conversion crosses:**

- `checkstyle` sources are hardcoded to `src/main/java` / `src/free/java` / `src/play/java` (`common.gradle`, `tasks.register('checkstyle', Checkstyle)`). Test files have **never** been checkstyle-checked and will not be after conversion. No effect.
- `ktlint` (plugin 12.3.0, engine 1.0.1) registers **`ktlintTestSourceSetCheck`** on `:model` (no Debug/Release variant — Android's test source set is shared across build types, unlike `main`), aggregated by `:model:ktlintCheck`. Today it is a no-op (no `.kt` in `src/test`). **It begins enforcing on the first converted file.** Milestone 6's clean `:model:ktlintCheck` is therefore not evidence that a converted test suite will pass it.
- `-Xlint:all -Werror` on `tasks.withType(JavaCompile)` (`common.gradle`) currently applies to `compileDebugUnitTestJavaWithJavac`. Once all 29 files convert, that task goes `NO-SOURCE` and the flag stops applying to tests. `compileDebugUnitTestKotlin` already exists and currently reports `NO-SOURCE` — direct evidence the Kotlin test-compile path is already wired by the `kotlin-android` plugin.
- **`model/build.gradle` needs no change.** Verified: the `alias(libs.plugins.kotlin.android)` plugin block already produces `compileDebugUnitTestKotlin`; `testImplementation libs.junit / androidx.test.core / mockito.core / robolectric` are language-agnostic; no `kotlin("test")` dependency is required since the suite uses JUnit 4 + hamcrest (transitively via junit 4.13) rather than `kotlin.test`.

**External references to test class names: none.** Repo-wide search across `*.gradle`, `*.yml`, `*.yaml`, `*.kts`, `*.sh`, `*.py`, `*.xml`, `*.properties`, `*.json` found no Gradle test filters, no `--tests` arguments, and no CI step naming any `:model` test class. CI runs whole-module tasks only (`./gradlew test${variant}UnitTest test${base-variant}UnitTest`, `.github/workflows/checks.yml:100`). The three `Mother` classes are referenced **only from within `:model`'s own test source set** — no other module consumes them. Renaming or re-shaping any of these files breaks nothing outside the module.

> **Correction (legacy-android-planner, 2026-07-25, red-team loop 1) — the "none" above is wrong; original left in place for the record.** `config/checkstyle/suppressions.xml` contains **two** entries naming in-scope test files by filename: `:14` (`checks="LineLength"`) names `VolumeAdaptionSettingTest.java`, and `:15` (`checks="VariableDeclarationUsageDistance"`) names `FeedFilterTest.java`. The surrounding conclusion still holds: both entries are **functionally inert**, because `common.gradle`'s `checkstyle` task sources only `src/main/java` / `src/free/java` / `src/play/java` and never `src/test` — so they have never applied to a test file and are already dead today. The `.java` → `.kt` rename leaves them dead and permanently orphaned. Nothing breaks; no gate changes. They are deliberately not cleaned up in this milestone — see Plan **D19** and Out of Scope.

#### Tests in this area

Per-class counts are taken from the live JUnit XML of the forced re-run, not from counting `@Test` annotations (the two disagree for the parameterized class).

| File | Tests | Runner / notable | Covers |
|---|---|---|---|
| `DownloadErrorTest` | 3 | plain | enum code mapping, `fromCode` throws |
| `DownloadRequestTest` | 5 | **Robolectric** | Parcel round trip (lossy subset), null/empty auth → null, equals-vs-hashCode arguments asymmetry, `Bundle.putParcelableArrayList` |
| `DownloadResultTest` | 11 | plain | ctor overloads, ids, completion date |
| `DownloadStatusTest` | 3 | plain | state constants, progress |
| `ProxyConfigTest` | 4 | plain | ctor, port defaults |
| `ChapterTest` | 9 | plain | id-only equals/hashCode, `getAfterPosition` |
| `EmbeddedChapterImageTest` | 13 | plain + `mockStatic(TextUtils)` | url parse/format, equals/hashCode, **2 exception-message-pinning NPE tests** |
| `FeedCounterTest` | 3 | plain | enum ids |
| `FeedFilterTest` | 11 | plain | filter predicates; uses `FeedMediaMother` |
| `FeedFundingTest` | 14 | plain | parse/serialize, Serializable round trip |
| `FeedItemFallbackLinkTest` | **7** | **Parameterized** | 1 `@Test` method × 7 param sets |
| `FeedItemFilterTest` | 20 | plain | filter matrix, feed-state branches, **1 NPE-message-pinning test**; uses `FeedMother` |
| `FeedItemTest` | 18 | plain | `updateFromOther`, played/new state, Serializable round trip; uses `FeedItemMother` + `FeedMother` |
| `FeedMediaTest` | 8 | **Robolectric** + Mockito | item-state on download, Parcel round trip, cross-type equals with `RemoteMedia`, `getMediaItem`, **reflection on `hasEmbeddedPictureField`**; uses `FeedMediaMother` |
| `FeedOrderTest` | 3 | plain | enum ids |
| `FeedPreferencesTest` | 16 | plain + `mockStatic(TextUtils)` | nested enum codes, `updateFromOther`, Serializable round trip |
| `FeedTest` | 18 | plain | `updateFromOther`, sort order guards, Serializable round trip; uses `FeedMother` |
| `SubscriptionsFilterTest` | 10 | plain + `mockStatic(TextUtils)` | filter parse/serialize |
| `TranscriptSegmentTest` | 5 | plain | segment times |
| `TranscriptTest` | 8 | plain | segment lookup/count |
| `TranscriptTypeTest` | 5 | plain | enum priority |
| `MediaTypeTest` | 8 | plain | mime-type mapping |
| `RemoteMediaTest` | 3 | **Robolectric** | Parcel round trip (drops chapters, null pubDate → epoch), 3-field equals/hashCode |
| `TimerValueTest` | 2 | plain | display/millis values |
| `SortOrderTest` | 11 | plain | code round trip, `fromCodeString` throws |
| `VolumeAdaptionSettingTest` | 14 | plain + **hamcrest** | enum ↔ int mapping, boost-supported gating; `@Before`/`@After` mutate global state |
| **Total** | **232** | | |

**`Mother` dependency graph** (drives conversion ordering):

```
FeedMother.anyFeed()  ────────┬──> FeedItemMother.anyFeedItemWithImage()   [FeedItemMother.java:5,11]
                              ├──> FeedItemTest        [static import :15, uses :224]
                              ├──> FeedTest            [static import :13, uses :25,26,40,69,70,78,79,87,93,94,154]
                              └──> FeedItemFilterTest   [qualified call, :274,276,278]
FeedMother.IMAGE_URL  ────────> FeedTest               [static import :12]
FeedItemMother.anyFeedItemWithImage() ──> FeedItemTest [static import :14, uses :31,32,113,149,150,158,159,167,168]
FeedMediaMother.anyFeedMedia() ──┬──> FeedMediaTest    [static import :16, uses :37,126,132]
                                 └──> FeedFilterTest   [qualified call, :134,140,145]
```

`FeedMother` is the root — it is the only Mother referenced by another Mother. `FeedMediaMother` and `FeedItemMother` are leaves. Consumption is split between `import static` (4 sites) and qualified `Class.method()` calls (2 files), which matters because those two forms have *different* breakage modes when the target becomes Kotlin.

**Visibility shapes differ between the three Mothers** and Kotlin has no package-private:

- `FeedItemMother` — package-private `class`, package-private `static` method (`FeedItemMother.java:7,10`)
- `FeedMediaMother` — package-private `class`, package-private `static` method (`FeedMediaMother.java:3,9`)
- `FeedMother` — **`public` class, `public static` method and `public static final` field** (`FeedMother.java:3,4,6`)

### Constraints & Risks

**1. (CRITICAL, silent) Int→Long widening inside `assertEquals` — 3 confirmed sites that will fail after a naive conversion.** In Java, `assertEquals(42, someLongValue)` widens both to `long` and resolves to `Assert.assertEquals(long, long)`. Kotlin performs no implicit widening, so the literal boxes to `Integer`, the value boxes to `Long`, `Assert.assertEquals(Object, Object)` is selected, and `Integer(42).equals(Long(42))` is **false**. Verified against production types:

| Site | Assertion | Actual type |
|---|---|---|
| `FeedItemTest.java:238` | `assertEquals(42, deserialized.getId())` | `FeedItem.id: Long` (`FeedItem.kt:20`) |
| `FeedTest.java:167` | `assertEquals(99, deserialized.getId())` | `Feed.id: Long` (`Feed.kt:20`) |
| `FeedPreferencesTest.java:173` | `assertEquals(1, prefs.getFeedID())` | `feedID: Long` (`FeedPreferences.kt:6,156`) |

These fail loudly on first run, so they are recoverable — the risk is a developer "fixing" them by changing the assertion rather than by writing `42L`. I checked every other integer-literal `assertEquals` in the suite (roughly 60 sites) against its production type: all others compare `Int` to `Int` (`code`/`id`/`priority`/`port`/`progressPercent`/`position`/`length`/`startPosition`/`playedDuration`/`SIZE_UNKNOWN`/`size()`/`.length` are all `Int`) or already carry an `L` suffix. **These three are the complete set.**

The *inverse* direction is safe: passing an `Int` literal where a `Long` parameter is expected (e.g. `transcript.findSegmentIndexBefore(-50)` at `TranscriptTest.java:72` against `fun findSegmentIndexBefore(time: Long)` at `Transcript.kt:14`; `feed.setId(99)` at `FeedTest.java:156`) is resolved by Kotlin's integer-literal type inference and compiles correctly.

**2. (HIGH) Kotlin hard keywords used as identifiers — compile errors, but they force a naming decision.**

- **`is`** — `VolumeAdaptionSettingTest` statically imports `org.hamcrest.CoreMatchers.is` and calls it **12 times** (`VolumeAdaptionSettingTest.java:30,37,44,51,58,65,70,71,72,73,74,75`). `is` is a Kotlin hard keyword; the call must become `` `is`(equalTo(0)) ``. This is the *only* hamcrest user in the suite (`assertThat`/`is`/`equalTo`, imported at `:9,10,12`). The alternative — dropping hamcrest for `assertEquals` — changes what the test asserts (`is(equalTo(x))` and `assertEquals` are equivalent here, but it is still an assertion-library change in a characterization suite) and should be an explicit planner decision, not a developer improvisation.
- **`in`** — 4 `ObjectInputStream in = ...` declarations (`FeedItemTest.java:234`, `FeedTest.java:163`, `FeedFundingTest.java:129`, `FeedPreferencesTest.java:244`). Becomes moot if try-with-resources converts to `.use { }` with a different lambda parameter name, but if J2K emits a named local it will need backticks or a rename.
- `out` (the matching `ObjectOutputStream out`) is only a Kotlin *soft* keyword and is a legal identifier — no action.
- **No test *method* name collides with any Kotlin keyword.** I scanned all 26 classes' `public void <name>(` declarations against the full hard/soft keyword list — zero hits. The prompt's concern about method names named `in`/`when`/`is`/`object` etc. does not materialize; the collisions are in a static-imported matcher and in local variables instead.

**3. (MEDIUM) ktlint's function-naming rule — verified empirically, and the answer is permissive.** 15 test methods use underscores (`testDownloadMediaOfNewItem_changedToNotPlayedItem`, `testUpdateFromOther_dateChanged`, `testSetSortOrder_NullAllowed`, and 12 more across `FeedMediaTest`/`FeedItemTest`/`FeedTest`). I decompiled the resolved `ktlint-ruleset-standard-1.0.1.jar` rather than relying on documentation: `FunctionNamingRule` holds two patterns — `VALID_FUNCTION_NAME_REGEXP = [a-z][A-Za-z\d]*` and `VALID_TEST_FUNCTION_NAME_REGEXP = (`.*`)|([a-z][A-Za-z\d_]*)` — and selects the test variant when the file imports any of `io.kotest`, `kotlin.test`, **`org.junit`**, `org.testng`. All 26 test classes import `org.junit.Test`, so **underscores and backtick-quoted names are both permitted** and the 15 existing names can be preserved verbatim. The three `Mother` files import none of those packages and so fall under the strict pattern — but `anyFeed`, `anyFeedItemWithImage`, `anyFeedMedia` are already clean camelCase, so they pass too.

This makes the module's **rename-don't-backtick** convention (the Milestone 6 process fix, and the `kotlin-j2k-style` skill at `services/android-migration/.claude/skills/kotlin-j2k-style/SKILL.md`) *permissive rather than binding* for test method names: ktlint will not force a change either way. Since no external tooling references these names (see Existing surface), the safest reading for a characterization milestone is that **test method names should be preserved exactly as-is** — renaming them is a behavior-neutral but review-noise-generating change with no gate demanding it. This should be stated explicitly in the Plan so it is not left to per-file judgement.

**4. (MEDIUM) The three `Mother` helpers have no faithful Kotlin shape, and the choice determines whether intermediate states compile.** Java `static` + package-private maps to no single Kotlin construct:
- **Top-level functions** in `FeedMother.kt` — cleanest Kotlin; Kotlin callers use a normal `import`. But Java callers would need `FeedMotherKt.anyFeed()`, so **every remaining Java test that statically imports the helper breaks the moment the helper converts**.
- **`object FeedMother`** — Kotlin callers unchanged (`FeedMother.anyFeed()`); Java callers need `FeedMother.INSTANCE.anyFeed()` unless annotated `@JvmStatic`.
- **`class FeedMother { companion object { @JvmStatic fun anyFeed() } }`** — preserves the Java `import static` contract exactly, so both Java and Kotlin callers work throughout. Ugliest Kotlin, but the only shape under which *any* file order compiles.

`internal` is the natural J2K rendering of package-private, but it is not equivalent: `internal` members of a class are name-mangled in the bytecode (`anyFeed$model_debug`), which silently breaks Java static imports even when the Kotlin source looks right. Since the end state is 100% Kotlin, `internal` is fine *if* the Mothers convert in the same commit as their last Java consumer — but that is exactly the constraint the planner has to make explicit.

The build ordering is `compileDebugUnitTestKotlin` → `compileDebugUnitTestJavaWithJavac` (observed live), and kotlinc reads Java sources for resolution, so mixed-language *references* resolve in both directions. The failure mode here is not resolution, it is **static-member shape and name mangling**.

**5. (MEDIUM) Two exception-*message*-pinning tests must keep asserting on messages, not just types.** Both are load-bearing regression guards created in Milestone 6 to pin disclosed `!!` operators, and both were independently revert-and-reverified by red-team:
- `EmbeddedChapterImageTest.getModelForNullChaptersThrowsNpe` (`:156-168`) asserts `assertNull(exception.getMessage())` **and** `exception.getStackTrace()[0].getClassName().startsWith("de.danoeh.antennapod.model.feed.EmbeddedChapterImage")`.
- `FeedItemFilterTest.testMatchesShowInHistoryWithNullLastPlayedTimeHistoryThrowsNpe` (`:260-270`) asserts `assertNull(exception.getMessage())`.
- A third, `EmbeddedChapterImageTest.getModelForNullImageUrlThrowsFromInsideMatcherNotAtExtraction` (`:138-148`), asserts the opposite — `assertNotNull(exception.getMessage())` **and** that frame 0 starts with `java.util.regex`.

All three assert on **stack-frame class names and message nullity**. A Kotlin conversion that wraps the call in an extra lambda or helper can change frame 0 and break these for reasons unrelated to the production code. They must be converted with the assertion expressions structurally intact, and re-run as the specific discriminator they are.

**6. (MEDIUM) `assertThrows` lambda bodies and Kotlin's last-expression-return.** `assertThrows(X.class, () -> foo())` becomes `assertThrows(X::class.java) { foo() }`. Where `foo()` returns a value, the Kotlin lambda's implicit return changes the SAM target from `ThrowingRunnable` to something ambiguous. Present at `SortOrderTest.java:35,40`, `DownloadErrorTest.java:46,47`, `EmbeddedChapterImageTest.java:63,143,160`, `FeedItemFilterTest.java:267`, `FeedTest.java:57`, `VolumeAdaptionSettingTest.java:78,83` (the last two use `@Test(expected = ...)` instead, which has its own Kotlin form: `@Test(expected = IllegalArgumentException::class)`).

**7. (LOW-MEDIUM) Mockito `any()` under Kotlin null-safety.** Three `mockStatic` setups pass `any()` into stubs whose parameters are platform/nullable types, including two **explicit Java casts to disambiguate overloads** — `TextUtils.join(any(), (Iterable<?>) any())` (`FeedPreferencesTest.java:37`) and `TextUtils.join(any(), (Object[]) any())` (`SubscriptionsFilterTest.java:39`). `TextUtils.join` is overloaded on `Iterable` vs `Object[]`; those casts are the only thing selecting the right overload, and Kotlin's cast syntax plus `any()`'s `T`-inference make this the most likely place for a conversion to silently stub the *wrong overload* and leave the real one throwing "not mocked". Both files' stubs also reimplement AOSP `TextUtils` semantics by hand in `thenAnswer` blocks (`FeedPreferencesTest.java:37-50`, `SubscriptionsFilterTest.java:31-52`, `EmbeddedChapterImageTest.java:39-40`) — those bodies are behavior, not boilerplate, and must convert exactly.

**8. (LOW) `Parameterized` requires `@JvmStatic` in a companion object.** `FeedItemFallbackLinkTest`'s `@Parameters public static Collection<Object[]> data()` (`:24-35`) must become a `companion object` member annotated `@JvmStatic` **and** `@Parameterized.Parameters`, or JUnit will not discover it and the class will fail at initialization. The data table also contains `null` entries in `Object[]` rows (`:28,31,32,33`), so the Kotlin literal needs `arrayOf<Any?>` — a plain `arrayOf` infers a non-null element type and will not accept them. Constructor-injected parameters (`:37-42`) become constructor `val`s. Getting this wrong is loud, but it silently *reduces the test count from 7 to 0* if the `@Parameters` method is merely undiscovered rather than erroring — so the per-class count, not just the total, should be checked after conversion.

**9. (LOW) The reflection string is a hard coupling to a private Kotlin field name.** `"hasEmbeddedPictureField"` (`FeedMediaTest.java:200`) must be preserved character-for-character; nothing about it is checked at compile time.

**10. (LOW) No Kotlin-test precedent exists in this repository.** A repo-wide search for `.kt` files under any `src/test*` path returns **zero**. There is no in-repo house style to match, and `AGENTS.md` gives no test-specific Kotlin guidance. Whatever this milestone produces becomes the precedent for every subsequent module's test conversion, which argues for recording the chosen conventions in `model/README.md` as part of the work.

### Unknowns

1. **What Kotlin shape do the three `Mother` helpers take** — top-level functions, `object`, or `class` + `@JvmStatic` companion? This is the single decision that determines whether the conversion can be split across multiple commits/steps or must land atomically. Related: does `FeedMother`'s currently-`public` visibility get preserved, or normalized to match the two package-private ones (nothing outside `:model` consumes it, so either is safe)?
2. **Conversion ordering and granularity.** Mothers-first (requires `@JvmStatic` companions to keep remaining Java tests compiling), Mothers-last (requires Kotlin tests to call Java helpers — which works, since kotlinc reads Java sources), or all-29-atomic? The suite is the only oracle, so the planner should decide how often `:model:testDebugUnitTest --rerun` must be green — per file, per package, or once at the end.
3. **Is `VolumeAdaptionSettingTest`'s hamcrest usage kept (`` `is`(equalTo(x)) ``) or migrated to `assertEquals`?** Keeping it is the strict behavioral-equivalence choice but produces 12 backticked keyword calls; migrating is cleaner Kotlin but is an assertion-library change inside a characterization suite. Needs an explicit decision, since it is the one place where "idiomatic Kotlin" and "don't touch test behavior" genuinely conflict.
4. **Are the 15 underscore-containing test method names preserved verbatim?** Research finding 3 establishes that ktlint permits them and no external tooling references them, so preservation is safe and is my recommendation — but the module's rename-don't-backtick convention was written for production code and the planner should state whether it extends to test method names.
5. **Do the try-with-resources blocks become `.use { }` or an explicit try/finally?** Milestone 6's code review raised the `.use { }` close-ordering point on production code; the same construct appears 8 times here. Behaviorally inert in these specific blocks (no catch clause, in-memory streams) but worth pre-deciding so review does not relitigate it.
6. **Does `model/README.md` get updated** to record the test-suite conversion and the Kotlin-test conventions chosen (given finding 10, this repo's first)? The README currently names `DownloadRequestTest`/`RemoteMediaTest`/`FeedMediaTest` by name in its Robolectric guidance; those references stay valid after a `.java` → `.kt` rename, but the "100% Kotlin" sentence describes only production code and may warrant a note.
7. **Does the conversion preserve the three Robolectric `@RunWith` annotations exactly, and is the README's Robolectric-scope constraint restated?** Since `EmbeddedChapterImageTest` turns out *not* to be a Robolectric file, there is a live risk a developer "tidies" the four-file cluster into consistency by adding Robolectric to it — which would violate the module's standing constraint. Worth an explicit Out-of-Scope line.

### Track prerequisites

- **`kotlin`** — no prerequisites; met. The module's production code is already 100% Kotlin (27/27 `.kt`, `model/src/main`), the Kotlin test-compile path is already wired (`compileDebugUnitTestKotlin` exists and reports `NO-SOURCE`), and no `model/build.gradle` change is required. The standing test-migration-sequencing gate ("convert tests only after the whole module's production code is migrated") is satisfied. No blocking gap.

### Sources

- `model/src/test/java/de/danoeh/antennapod/model/` — 29 `.java`, 0 `.kt` (`find model/src/test -name '*.java' | wc -l` → 29; `-name '*.kt'` → 0)
- `model/src/main/java/de/danoeh/antennapod/model/` — 27 `.kt`, 0 `.java`
- Test result: `./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL; `model/build/test-results/testDebugUnitTest/*.xml` (26 files) → tests=232 skipped=0 failures=0 errors=0
- Robolectric runners: `model/src/test/java/de/danoeh/antennapod/model/download/DownloadRequestTest.java:19`, `feed/FeedMediaTest.java:30`, `playback/RemoteMediaTest.java:22`
- Parameterized runner: `feed/FeedItemFallbackLinkTest.java:14`, `@Parameters` at `:24-35`, ctor at `:37-42`, nulls in data at `:28,31,32,33`
- `mockStatic(TextUtils)`: `feed/EmbeddedChapterImageTest.java:38-40`, `feed/FeedPreferencesTest.java:36-50`, `feed/SubscriptionsFilterTest.java:30-52`; overload-disambiguating casts at `FeedPreferencesTest.java:37`, `SubscriptionsFilterTest.java:39`
- Mockito mocks of Kotlin classes: `feed/FeedMediaTest.java:45,62,79`; `feed/EmbeddedChapterImageTest.java:110,123,140,157`
- Reflection: `feed/FeedMediaTest.java:195-203` (field literal `"hasEmbeddedPictureField"` at `:200`), consumed at `:121`
- Exception-message pinning: `feed/EmbeddedChapterImageTest.java:138-148` (`assertNotNull` + `java.util.regex` frame), `:156-168` (`assertNull` + `EmbeddedChapterImage` frame), `feed/FeedItemFilterTest.java:260-270` (`assertNull`)
- Int→Long `assertEquals` traps: `feed/FeedItemTest.java:238`, `feed/FeedTest.java:167`, `feed/FeedPreferencesTest.java:173`; production types at `model/src/main/java/de/danoeh/antennapod/model/feed/FeedItem.kt:20`, `feed/Feed.kt:20`, `feed/FeedPreferences.kt:6,156`
- Int-typed comparators confirmed safe: `feed/SortOrder.kt:6`, `feed/FeedOrder.kt:3`, `feed/FeedCounter.kt:3`, `feed/TranscriptType.kt:4`, `download/ProxyConfig.kt:8,13`, `download/DownloadError.kt:4,31`, `download/DownloadResult.kt:13,123`, `download/DownloadRequest.kt:13,25`, `download/DownloadStatus.kt:3`, `feed/EmbeddedChapterImage.kt:8,9`, `feed/FeedMedia.kt:70,76,79`, `feed/Chapter.kt:50`, `feed/Transcript.kt:14,44`
- Kotlin keyword `is`: `VolumeAdaptionSettingTest.java:9,10,12` (imports), `:30,37,44,51,58,65,70,71,72,73,74,75` (12 calls)
- Kotlin keyword `in`: `feed/FeedItemTest.java:234`, `feed/FeedTest.java:163`, `feed/FeedFundingTest.java:129`, `feed/FeedPreferencesTest.java:244`
- Try-with-resources (8 blocks): `feed/FeedItemTest.java:230,234`, `feed/FeedTest.java:159,163`, `feed/FeedFundingTest.java:125,129`, `feed/FeedPreferencesTest.java:240,244`
- Global mutable state: `model/src/main/java/de/danoeh/antennapod/model/feed/VolumeAdaptionSetting.kt:23,37,50-54`; reset in `VolumeAdaptionSettingTest.java:17-24`
- Mother classes: `feed/FeedMother.java:3,4,6` (public), `feed/FeedItemMother.java:5,7,10,11` (package-private, depends on `FeedMother`), `feed/FeedMediaMother.java:3,9` (package-private)
- Mother consumers: `feed/FeedItemTest.java:14,15`, `feed/FeedTest.java:12,13`, `feed/FeedMediaTest.java:16` (static imports); `feed/FeedItemFilterTest.java:274,276,278`, `feed/FeedFilterTest.java:134,140,145` (qualified)
- Build config: `model/build.gradle` (plugins `kotlin.android`+`ktlint`; `testImplementation` junit/androidx-test-core/mockito-core/robolectric with the M6 disclosure comment); `common.gradle` (checkstyle source set = `src/main/java` only; `-Xlint:all -Werror` on all `JavaCompile`)
- Dep versions: `gradle/libs.versions.toml:76` (junit 4.13), `:77` (robolectric 4.16), `:78` (mockito-core 5.15.2), `:86` (ktlint plugin 12.3.0)
- ktlint gates present on `:model`: `./gradlew :model:tasks --all` → `ktlintTestSourceSetCheck`, `ktlintCheck`
- ktlint test-name exemption: decompiled `~/.gradle/caches/.../ktlint-ruleset-standard-1.0.1.jar` → `FunctionNamingRule` constants `VALID_FUNCTION_NAME_REGEXP = [a-z][A-Za-z\d]*`, `VALID_TEST_FUNCTION_NAME_REGEXP = (`.*`)|([a-z][A-Za-z\d_]*)`, selected on imports `io.kotest` / `kotlin.test` / `org.junit` / `org.testng`
- Kotlin test-compile path already wired: `./gradlew :model:testDebugUnitTest` output → `> Task :model:compileDebugUnitTestKotlin NO-SOURCE`
- No external test-class references: repo-wide grep over `*.gradle`/`*.yml`/`*.yaml`/`*.kts`/`*.sh`/`*.py`/`*.xml`/`*.properties`/`*.json` → no matches; CI runs whole-module tasks at `.github/workflows/checks.yml:100`
- No Kotlin tests repo-wide: `find . -path "*/src/test*/*" -name "*.kt"` (excluding `/build/`) → 0
- Module conventions: `model/README.md` (100%-Kotlin statement, `@JvmField`/`@JvmStatic` preservation rule, Robolectric scope constraint naming `DownloadRequestTest`/`RemoteMediaTest`/`FeedMediaTest`); `AGENTS.md` (test command conventions)
- Prior-milestone context: `features/antennapod-model-kotlin-milestone-6.checkpoint.md` (232-test count, `hasEmbeddedPictureField` rename, both `!!`-disclosure regression guards); `services/android-migration/.claude/skills/kotlin-j2k-style/SKILL.md` (rename-don't-backtick convention)

---

## Plan
_Last updated by: legacy-android-planner | 2026-07-25 (revised, red-team loop 1); tightened, red-team loop 2 (FINAL)_

> **Revision note (red-team loop 2, FINAL — no third loop, per this pipeline's hard cap and the Milestone 4 precedent for a documented mechanical fix instead of escalation):** Loop 2 returned CHALLENGE but recommended accept-and-document, not a third planner loop or escalation to José — every gap it found in D18's `assertlines.pl` extractor was in the safe (false-positive) direction, and the plan's own per-line-justification escape valve already absorbs it. Applied directly to D18 and Step 1, no other Decision/Step/AC touched: (1) added a `new`-keyword-strip canonicalization rule (Kotlin has no `new`; hits 8 assertion sites across `ChapterTest`/`EmbeddedChapterImageTest`/`SubscriptionsFilterTest`/`RemoteMediaTest`/`FeedMediaTest`); (2) added a policy note (not a canonicalization) that `.equals()` must be preserved verbatim, never simplified to `==`, so `FeedMediaTest`'s two `.equals()` assertions don't need a rule at all; (3) added paren-adjacent whitespace stripping to the extractor, since red-team reproduced a false-positive residual from ordinary IDE line-wrapping on the two highest-stakes wrap sites (`EmbeddedChapterImageTest:167`, a D9 pinning assertion, and `FeedPreferencesTest:116`, one of the three D7 exception files) and confirmed a comma-only wrap style avoids it. Full finding: `## Red-Team Verdict — Plan` (Loop 2, FINAL) below.

> **Revision note (red-team loop 1):** This Plan was revised to address the two findings in the "Red-Team Verdict — Plan" CHALLENGE (loop 1) below, **without re-scoping the milestone's conversion design** (D1's `object` + `@JvmStatic` Mother shape, the 15-step batch ordering, and all 22 original Acceptance Criteria were independently re-verified sound and are unchanged except where noted). The two fixes:
> 1. **MAJOR — AC3 was count-only, so two canceling assertion mistakes could pass it. Assertion *content* is now diffed 1:1, mechanically, per file.** The red-team is right that `grep -c` proves only that the *number* of `assert*`/`verify` calls is unchanged; it says nothing about their arguments, and ~557 of the suite's assertions had no content-level check at all (only the 6 hand-verified sites in D7/D9 did). New **D18** defines a per-file canonical assertion extraction: every assertion call is joined onto one logical line, canonicalized across the Java/Kotlin syntax gap (keyword escapes, `X.class` ↔ `X::class.java`, bean accessor ↔ property, `.size()` ↔ `.size`, `() ->` ↔ trailing lambda, declaration prefixes), and `diff`ed against the Java original retrieved from the merge base. I built and validated the extractor during this revision against all 29 live Java files: **its per-file line count matches AC3's grep table exactly for all 29 files, zero mismatches, 563 assertions total** — so the two mechanisms measure the same population and the content diff is a strict superset of the count check. The binding requirement is that the residual diff be **empty for 26 of 29 files** and be **exactly the 3 disclosed `L`-suffix lines** for `FeedItemTest`/`FeedTest`/`FeedPreferencesTest`; any other residual line is a hard stop requiring written per-line justification. AC3 is re-worded as explicitly necessary-but-not-sufficient, new **AC3B** carries the content check, Step 1 now also records the merge-base SHA (without which the Java originals are unrecoverable after the renames), Steps 2–12 gain a standing per-step obligation, and Step 14 reconciles the audit.
> 2. **MINOR — Research's "External references to test class names: none" is corrected; two orphaned checkstyle suppression entries exist and are deliberately left alone.** `config/checkstyle/suppressions.xml:14-15` names `VolumeAdaptionSettingTest.java` and `FeedFilterTest.java` by filename. I confirmed the red-team's finding that these are functionally inert — `common.gradle`'s checkstyle task sources only `src/main/java` (+ free/play), never `src/test`, so both entries are already dead today and merely stay dead after the rename. New **D19** records the decision to leave them (cleaning them would widen File Scope to a shared repo-wide config file for a purely cosmetic edit, and both entries sit inside regex alternation groups whose *other* members are live production files — a typo there would silently disable real suppressions). Added to Out of Scope, logged as future-work item #5, and the Research claim is annotated with a dated correction rather than silently rewritten.

### Objective

Convert all 29 Java files in `:model`'s test source set (26 `*Test` classes + 3 `*Mother` builders) to Kotlin, completing the `kotlin` track for the module. Zero test *behavior* changes: the same 232 tests, distributed across the same 26 classes in the same per-class counts, asserting the same things, must pass before and after.

### Resolved Decisions

**D1 — `Mother` helper shape: public `object` + `@JvmStatic` fun + `const val`.** (Research Unknown 1, Risk 4.)

All three become `object FeedMother { const val IMAGE_URL = ...; @JvmStatic fun anyFeed(): Feed { ... } }` shape, at default (public) visibility. Reasoning:
- `@JvmStatic` on an `object` member emits a real static method on the `FeedMother` class, so the existing Java `import static de.danoeh.antennapod.model.feed.FeedMother.anyFeed;` (4 sites) and the qualified `FeedMother.anyFeed()` calls (2 files) both keep working *unchanged* while the remaining tests are still Java. `const val IMAGE_URL` likewise emits a `public static final` field, preserving `FeedTest`'s static import of it.
- Kotlin callers get the natural `FeedMother.anyFeed()` / `FeedMother.IMAGE_URL`. Both languages read identically — no call site in any consumer file changes as a result of this step, in either direction.
- **`internal` is explicitly rejected** even though it is J2K's default rendering of package-private. `internal` members are JVM name-mangled (`anyFeed$model_debug`), which breaks Java `import static` *silently at the source level* — the Kotlin looks right and the Java looks right, and only the compiler disagrees. That is the exact failure class this milestone exists to avoid.
- **Top-level functions are rejected** for the same reason (Java callers would need `FeedMotherKt.anyFeed()`), and **`class` + `companion object`** is rejected as strictly uglier than `object` with no compensating benefit.
- `FeedItemMother` and `FeedMediaMother` are widened from package-private to public. This is behaviour-neutral: they live in a test source set that is not published and that the researcher confirmed no other module consumes. Uniformity across all three is worth more here than preserving a visibility distinction that has no observable effect.

**D2 — Conversion order: Mothers first, then batch by package and by hazard-cluster; suite green at every step.** (Research Unknown 2.)

D1 makes every intermediate state compile in both directions, so the conversion does *not* have to land atomically. It is sequenced Mothers → `download` → `playback` → root → `feed` (split into five hazard-homogeneous batches), mirroring how Milestones 1–6 batched production files. `./gradlew :model:testDebugUnitTest --rerun` must be green **after every numbered step**, not just at the end — `--rerun` is mandatory because Gradle will otherwise report `UP-TO-DATE` and prove nothing (the researcher hit exactly this). Batches are grouped so that one *kind* of trap is under review in one diff.

**D3 — Hamcrest stays; `` `is`(equalTo(x)) `` with backticks.** (Research Unknown 3, Risk 2 — the sharpest call in this milestone.)

`VolumeAdaptionSettingTest`'s 12 `assertThat(x, is(equalTo(y)))` calls are converted mechanically to ``assertThat(x, `is`(equalTo(y)))``. The imports stay `org.hamcrest.CoreMatchers.is` / `.equalTo` / `org.hamcrest.MatcherAssert.assertThat`.

Reasoning — this is where "idiomatic Kotlin" and "don't touch test behaviour" genuinely conflict, and behaviour wins:
- The suite is its own only oracle. Swapping assertion libraries inside the one milestone where nothing independent can catch a weakened assertion is precisely the wrong place to spend idiom points. `assertEquals` and `assertThat(is(equalTo()))` are *equivalent* here but not *identical* — they differ in failure output and in null-handling — and "equivalent, I checked" is not verifiable by a green build.
- Backticks are the standard Kotlin/Java interop mechanism for a foreign member whose name collides with a Kotlin keyword. The module's **rename-don't-backtick** convention (`kotlin-j2k-style`) governs *declarations we own and can rename*; it does not and cannot govern a static import from hamcrest, whose name is not ours to change. Applying it here would be a category error.
- Two alternatives were considered and rejected: (a) migrating to `assertEquals` — rejected as an assertion-library change in a characterization suite; (b) an import alias (`import org.hamcrest.CoreMatchers.`is` as isEqualTo`) — rejected because it invents a new identifier that appears nowhere in the Java original, costing 1:1 line-for-line diff reviewability for cosmetics.
- Net cost is 12 backticked call sites confined to one file. That is the cheapest possible price for a zero-risk assertion story.

The same rule applies to the **`Mockito.when` collision, which the research did not flag** (see D4).

**D4 — `when` is a third hard-keyword collision; also backticked.** (Not in Research; found during planning.)

`org.mockito.Mockito.when` is statically imported and called at **10 sites** — `EmbeddedChapterImageTest.java:111,124,141,158` and `FeedMediaTest.java:46,47,63,64,80,81` — and `MockedStatic.when` is called as an instance method at **4 more** — `EmbeddedChapterImageTest.java:39`, `FeedPreferencesTest.java:37`, `SubscriptionsFilterTest.java:31,39`. All 14 become `` `when`(...) `` / `` textUtilsMock.`when` { ... } ``. Same reasoning as D3: it is a foreign member name we do not own. Do **not** replace them with `Mockito.doReturn(...).when(mock)` or with a `mockito-kotlin` `whenever` helper — the former changes stubbing semantics for the `null` return at `EmbeddedChapterImageTest:158`, and the latter adds a dependency. This is a loud compile error, not a silent one, but it lands directly on top of the exception-message-pinning setup, so it is called out rather than left to improvisation.

Related: `EmbeddedChapterImageTest:158` stubs `when(media.getChapters()).thenReturn(null)` against a Kotlin nullable return. If Kotlin's inference balks, resolve it with an explicit type argument (`thenReturn<List<Chapter>?>(null)`), **not** by changing the stub to `doReturn`/`thenAnswer` — the stub shape is load-bearing for that test.

**D5 — Test method names are preserved byte-for-byte. All 26 classes, all 232 tests, including the 15 with underscores.** (Research Unknown 4, Risk 3.)

The researcher established empirically that ktlint 1.0.1's `FunctionNamingRule` selects `VALID_TEST_FUNCTION_NAME_REGEXP` (which permits underscores and backticks) for any file importing `org.junit`, and that all 26 classes do. So ktlint permits both preservation and renaming — it is not a gate. In the absence of a gate, preservation wins: renaming is behaviour-neutral review noise in a milestone where every line of diff needs to be checkable against its Java original.

This is a **uniform rule, applied to all files, not a per-file judgement**: no test method is renamed, no underscore is camel-cased, and **no new backtick-quoted sentence-style names (`` `does the thing` ``) are introduced anywhere**. A reviewer should be able to diff the Java and Kotlin method lists and get an empty result.

**D6 — try-with-resources becomes `.use { }`; the `in` local is renamed to `input`; `out` is left alone.** (Research Unknown 5, Risk 2.)

All 8 blocks (`FeedItemTest:230,234`, `FeedTest:159,163`, `FeedFundingTest:125,129`, `FeedPreferencesTest:240,244`) convert to `.use { }`. The close-vs-catch reordering that Milestone 6's review raised is inert here and pre-decided so review does not relitigate it: none of the 8 blocks has a `catch` clause, and every stream is backed by an in-memory `ByteArrayOutputStream`/`ByteArrayInputStream` whose `close()` cannot fail or observably reorder anything.

Concrete required shape, so the value-returning read block is not improvised:

```kotlin
val byteStream = ByteArrayOutputStream()
ObjectOutputStream(byteStream).use { out -> out.writeObject(item) }
val deserialized = ObjectInputStream(ByteArrayInputStream(byteStream.toByteArray())).use { input ->
    input.readObject() as FeedItem
}
```

- The `in` local (a Kotlin hard keyword, 4 sites) is **renamed to `input`** — rename-don't-backtick applies here because this identifier *is* ours.
- `out` is a Kotlin soft keyword and a legal identifier; it is **left as `out`** per AGENTS.md's minimal-diff rule.
- The cast must stay an **unsafe `as`**, never `as?`. `as?` would convert a `ClassCastException` into a silent `null` and hand the following assertions a different subject.

**D7 — The 3 int→Long `assertEquals` traps are fixed by `L`-suffixing the literal, and by nothing else.** (Research Risk 1.)

| Site | Java today | Required Kotlin |
|---|---|---|
| `FeedItemTest.java:238` | `assertEquals(42, deserialized.getId())` | `assertEquals(42L, deserialized.id)` |
| `FeedTest.java:167` | `assertEquals(99, deserialized.getId())` | `assertEquals(99L, deserialized.id)` |
| `FeedPreferencesTest.java:173` | `assertEquals(1, prefs.getFeedID())` | `assertEquals(1L, prefs.feedID)` |

The `L` suffix restores resolution to `Assert.assertEquals(long, long)` — bit-for-bit the overload Java selected via implicit widening. **Explicitly forbidden "fixes":** narrowing the actual with `.toInt()`, widening with `.toLong()` on the actual, comparing `.toString()`, or relaxing to `assertTrue(x == 42L)`. Each of those is green and each silently changes what is asserted. This is the single highest-value review checkpoint in the milestone.

These three fail *loudly* (Kotlin picks `assertEquals(Any?, Any?)`; `Integer(42) != Long(42)`), so a green suite is itself proof that no int-vs-Long site remains anywhere — the researcher's ~60-site sweep does not need re-deriving. What a green suite does *not* prove is that the fix was the right one, hence the explicit forbidden list and AC7.

**D8 — ktlint gates this milestone. `:model:ktlintCheck` must be green, and it is not optional.** (Research Unknown, toolchain boundary.)

Verified during planning: `.github/workflows/checks.yml:47` runs `./gradlew ktlintCheck` repo-wide in the `static-analysis` job, and every other job (`unit-test`, `emulator-test`) `needs: static-analysis`. So `ktlintTestSourceSetCheck` flipping on is not a choice this plan gets to make — CI enforces it the moment the first `.kt` lands in `model/src/test`. It gates this milestone exactly the way `ktlintCheck` gated production code in Milestones 1–6.

Known concrete consequence: **5 existing lines exceed the 120-char `max_line_length` set in `.editorconfig`** and must be wrapped — `VolumeAdaptionSettingTest.java:100` (128), `EmbeddedChapterImageTest.java:167` (131), `FeedTest.java:102` (123), `FeedPreferencesTest.java:116` (121) and `:117` (123). These have never been linted because `checkstyle` only covers `src/main/java`. Note that `EmbeddedChapterImageTest:167` is one of the pinning assertions (D9) — wrap it at an argument boundary; do not extract it to a local or restructure the expression.

**Suppression is forbidden:** no `@Suppress("ktlint:...")`, no `ktlint_disabled_rules`, no `.editorconfig` edit, no `ktlint { filter { ... } }` exclusion. Any change made to satisfy ktlint must be formatting-only — whitespace, line breaks, import order. No expression may be rewritten to appease a linter.

**D9 — The 3 exception-message-pinning tests convert with their assertion expressions structurally intact, and are re-proven by revert-and-reverify.** (Research Risk 5.)

`EmbeddedChapterImageTest.getModelForNullChaptersThrowsNpe` (`:156-168`), `EmbeddedChapterImageTest.getModelForNullImageUrlThrowsFromInsideMatcherNotAtExtraction` (`:138-148`), and `FeedItemFilterTest.testMatchesShowInHistoryWithNullLastPlayedTimeHistoryThrowsNpe` (`:260-270`) assert on **stack-frame class names and message nullity**. Required constraints:
- The `assertThrows` lambda body stays a **single call expression** — `assertThrows(NullPointerException::class.java) { EmbeddedChapterImage.getModelFor(media, 0) }`. No helper extraction, no intermediate local, no `run {}`/`also {}` wrapper, nothing that could add or shift a frame.
- `assertNull(exception.message)` / `assertNotNull(exception.message)` and the `exception.stackTrace[0].className.startsWith(...)` expressions convert 1:1. The two class-name string literals are preserved character-for-character.
- All existing explanatory comments above these three tests are carried over verbatim — they document *why* the assertion is shaped this way and are the only defence against a future reviewer "simplifying" it.

A green run is **not** sufficient evidence for these three (they would also pass if they had stopped discriminating). Step 13 re-proves them by the same technique Milestone 6's red-team used. This decision also covers Research Risk 6: `assertThrows(X::class.java) { ... }` SAM-converts cleanly to `ThrowingRunnable` even when the body returns a value; the value is discarded and no frame is added.

**D10 — `model/README.md` is updated to record the Kotlin-test conventions.** (Research Unknown 6, Risk 10.)

This is the repo's first Kotlin test source set (`find . -path "*/src/test*/*" -name "*.kt"` → 0), so whatever lands here becomes precedent for every subsequent module. `AGENTS.md` instructs updating a module's README with broadly-useful, long-term-stable patterns, which this is. The README gains: (a) that `:model`'s test suite is now 100% Kotlin alongside production; (b) the `object` + `@JvmStatic` Mother convention (D1) and *why* `internal` is wrong for test helpers; (c) that test method names are preserved verbatim and ktlint's `org.junit`-triggered test-name exemption is what permits it (D5); (d) a restatement of the Robolectric scope constraint (D11). Existing README references to `DownloadRequestTest`/`RemoteMediaTest`/`FeedMediaTest` remain valid across the `.java` → `.kt` rename and are not touched.

**D11 — The 3 Robolectric `@RunWith` annotations are preserved exactly; Robolectric is not added anywhere.** (Research Unknown 7.)

`DownloadRequestTest:19`, `FeedMediaTest:30`, `RemoteMediaTest:22` become `@RunWith(RobolectricTestRunner::class)` and nothing else changes about them. `EmbeddedChapterImageTest` is **not** a Robolectric file — it handles its Android dependency via `mockStatic(TextUtils)` — and must not become one. The temptation to "tidy the cluster into consistency" is real and would violate the standing constraint in `model/README.md`; it is called out in Out of Scope.

**D12 — `FeedItemFallbackLinkTest`'s `@Parameters` provider becomes a `@JvmStatic` companion member, with `arrayOf<Any?>`.** (Research Risk 8.)

```kotlin
companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun data(): Collection<Array<Any?>> = listOf(
        arrayOf<Any?>("average", FEED_LINK, ITEM_LINK, ITEM_LINK),
        ...
    )
}
```
`@JvmStatic` is mandatory or JUnit will not discover the provider. The element type must be `Any?` explicitly — 4 of the 7 rows carry `null` (`:28,31,32,33`) and a bare `arrayOf` infers a non-null element type. The constructor parameters (`:37-42`) become constructor `val`s; `FEED_LINK`/`ITEM_LINK` become `const val` in the same companion. **This is the one class where a mistake reduces the test count from 7 to 0 without erroring**, which is why AC2 checks per-class counts rather than only the 232 aggregate.

**D13 — The `FeedMediaTest` reflection string is preserved character-for-character.** (Research Risk 9.) `"hasEmbeddedPictureField"` (`FeedMediaTest.java:200`) is a hard, compile-unchecked coupling to a private Kotlin backing-field name. `FeedMedia.class` becomes `FeedMedia::class.java`; `getDeclaredField("hasEmbeddedPictureField")` and `isAccessible = true` are otherwise untouched.

**D14 — The `TextUtils` overload-disambiguating casts are preserved as Kotlin casts, and the hand-written `thenAnswer` bodies convert exactly.** (Research Risk 7.) `TextUtils.join(any(), (Iterable<?>) any())` (`FeedPreferencesTest.java:37`) becomes `TextUtils.join(any(), any() as Iterable<*>)`, and `(Object[]) any()` (`SubscriptionsFilterTest.java:39`) becomes `any() as Array<Any?>`. These casts are the *only* thing selecting the right `TextUtils.join` overload; picking the wrong one leaves the real overload unstubbed and throwing "not mocked" from an unrelated test. The `thenAnswer` bodies at `FeedPreferencesTest:37-50`, `SubscriptionsFilterTest:31-52` and `EmbeddedChapterImageTest:39-40` reimplement AOSP `TextUtils` semantics by hand — they are **behaviour, not boilerplate**, and convert line-for-line.

**D15 — Global-state teardown is preserved.** (Research "Where state lives".) `VolumeAdaptionSettingTest`'s `@After tearDown()` resetting `setBoostSupported(null)`, and the four `MockedStatic.close()` teardowns (`EmbeddedChapterImageTest`, `FeedPreferencesTest`, `SubscriptionsFilterTest`), are load-bearing: dropping or reordering any of them surfaces as an *unrelated* test failing later in the same JVM fork. `@Before`/`@After` methods keep their names and their bodies; the Java `throws Exception` clauses are simply dropped (Kotlin has no checked exceptions) with no other change.

**D16 — `model/build.gradle` is not modified.** Independently verified during planning: `alias(libs.plugins.kotlin.android)` already produces `compileDebugUnitTestKotlin` (currently `NO-SOURCE`), and `testImplementation` on junit / androidx-test-core / mockito-core / robolectric is language-agnostic. No `kotlin("test")` dependency is needed — the suite is JUnit 4 + hamcrest (transitive via junit 4.13), not `kotlin.test`. The file is deliberately **excluded from File Scope** so that a build-config change cannot slip in unnoticed.

**D17 — `@Test(expected = ...)` is kept, not migrated to `assertThrows`.** `VolumeAdaptionSettingTest:78,83` become `@Test(expected = IllegalArgumentException::class)`. Converting them to `assertThrows` would be a behaviour change (`expected=` accepts a throw from anywhere in the method; `assertThrows` scopes it to the lambda) in a characterization suite. Same principle as D3.

**D18 — Assertion *content* is verified by a mechanical per-file 1:1 diff against the Java original, not by assertion count.** (Red-team loop 1, MAJOR.)

AC3's count check is necessary but not sufficient: two canceling edits in the same file — one assertion's expected value silently altered, one assertion dropped — produce an identical count and a green suite. Outside the 6 sites D7 and D9 hand-verify, nothing else checked assertion *arguments*. This decision closes that gap for all 563 assertions.

**Mechanism.** For every one of the 29 files, the developer produces a canonical assertion extraction of the Java original (from the merge base) and of the converted Kotlin, then diffs them. The extraction: join every assertion call onto one logical line (wrapped calls glued by paren balance), keep only lines containing `assert*(`/`verify(`, and canonicalize across the Java/Kotlin syntax gap. The exact script is given in Step 1 and is written to the session scratchpad — **it is a throwaway audit tool and is not committed** (it is not in File Scope). Canonicalizations applied to both sides:

| Canonicalization | Java form | Kotlin form |
|---|---|---|
| keyword escapes stripped | `is(...)`, `when(...)` | `` `is`(...) ``, `` `when`(...) `` |
| class literals | `X.class` | `X::class.java` |
| bean getter → property | `.getId()` | `.id` |
| bean setter → assignment | `.setSortOrder(x)` | `.sortOrder = x` |
| collection/string accessors | `.size()` / `.length()` | `.size` / `.length` |
| lambda argument | `assertThrows(X.class, () -> f())` | `assertThrows(X.class) { f() }` |
| declaration prefix dropped | `NullPointerException e = ...` | `val e = ...` |
| `new` keyword stripped | `new Date(0)` | `Date(0)` |
| whitespace collapsed (incl. paren-adjacent), trailing `;` dropped | — | — |

**Policy, not canonicalization: `.equals()` stays verbatim.** `X.equals(Y)` must **not** be simplified to `X == Y` anywhere in this milestone, even though the two are behaviorally identical for every class involved — per D3/D4/D5's "don't spend idiom points where the suite is the only oracle" principle, and because adding an `==`-vs-`.equals()` canonicalization rule would blur the line between "textually different, semantically identical" and "the exact class of bug this diff exists to catch." `FeedMediaTest.java:149-150`'s two `assertTrue(fm.equals(rm))`/`assertTrue(rm.equals(fm))` calls are converted mechanically (`assertTrue(fm.equals(rm))` stays exactly that), not idiomatically.

**Required result — this is the binding claim, not the tooling.** The residual diff must be **empty for 26 of the 29 files**, and for the remaining three it must be **exactly one line each**, the disclosed D7 `L`-suffix fixes:

```
< assertEquals(42, deserialized.id)      > assertEquals(42L, deserialized.id)      FeedItemTest
< assertEquals(99, deserialized.id)      > assertEquals(99L, deserialized.id)      FeedTest
< assertEquals(1, prefs.feedID)          > assertEquals(1L, prefs.feedID)          FeedPreferencesTest
```

**Any other residual line is a hard stop.** It is not waved through by a green suite. Each such line must be (a) recorded verbatim in Implementation Notes, (b) justified in one line as a *pure syntax* difference introducing no change to the value compared, and (c) accepted or rejected by the reviewer individually. A residual that alters an **expected-value literal**, swaps the expected/actual argument order, or changes the assertion function itself (`assertEquals` → `assertTrue`, `assertNotNull` → `assertNotEquals`, …) is a **rejection, not a justification opportunity** — revert and redo the file.

**Validation of the mechanism itself (performed during this revision, so the developer is not the first to run it; extended in red-team loop 2 against real hand-converted Kotlin, not just Java-side self-consistency).** The extractor was run against all 29 live Java files: its per-file logical-assertion count equals AC3's `grep -cE` count for **every** file, zero mismatches, **563** assertions total. The two checks therefore cover the same population, and AC3B strictly subsumes AC3. Known limits, stated so they are not discovered as surprises: the paren-balance joiner counts parentheses inside string literals (no current file trips this — verified); a getter whose Kotlin property name is not the naive lowercase-first-letter form (e.g. `getURL()` → `url`, not `uRL`) will surface as a residual and is a legitimate one-line justification; and **any wrap style that leaves a space immediately adjacent to `(`/`)` after D8's mandated 120-char line wraps** is normalized by the extractor's paren-adjacent whitespace stripping, but a developer is still safest breaking only at comma boundaries with `(`/`)` glued to their adjacent token (confirmed during loop 2 review to avoid the artifact entirely, including on the two highest-stakes wrap sites: `EmbeddedChapterImageTest:167` — one of the three D9 pinning assertions — and `FeedPreferencesTest:116`, one of the three D7 one-residual-line exception files).

**Ownership.** The developer runs the audit per file, per step, and pastes the result. The **reviewer re-runs the identical command** and confirms it reproduces — this is why the extraction is a committed-to-writing script with an exact invocation rather than a prose instruction to "check carefully." Neither party is asked to eyeball 563 assertions unaided.

**D19 — Two orphaned checkstyle suppression entries naming in-scope test files are left uncorrected, disclosed rather than silently ignored.** (Red-team loop 1, MINOR. Corrects Research's "External references to test class names: none".)

`config/checkstyle/suppressions.xml:14` (`LineLength`) names `VolumeAdaptionSettingTest.java` and `:15` (`VariableDeclarationUsageDistance`) names `FeedFilterTest.java`, both by bare filename inside a regex alternation group. So the Research claim that a repo-wide search over `*.xml` found no external references to test class names is **factually wrong**, and is corrected in place.

It changes nothing about this plan. Independently re-confirmed: `common.gradle`'s `tasks.register('checkstyle', Checkstyle)` sets `source` to `src/main/java` (+ `src/free/java`, `src/play/java`) and never includes `src/test` for any module, so neither entry has ever applied to a test file. They are **already dead today** and will merely remain dead, matching a filename that no longer exists, after the `.java` → `.kt` rename. AC13 (`./gradlew checkstyle lint` green) is unaffected in either direction.

Decision: **do not clean them up in this milestone.** Reasoning:
- It would widen File Scope to a shared, repo-wide config file for a purely cosmetic edit — the same trade this plan already declined for `model/build.gradle`'s stale comment (Resolutions #2). Consistency matters more than the one-line saving.
- Both filenames sit inside alternation groups whose *other* members are live production files (`DBUpgrader.java`, `PlaybackService.java`, `ChapterReader.java`, …). Editing that regex risks a typo that silently disables a real suppression — a non-zero downside against a zero-value change, inside the one milestone whose entire premise is not accepting silent breakage.
- Deleting an entry is not obviously even the right cleanup: a future `:model` checkstyle-scope change could make them live again, in which case they should be re-expressed as `.kt`-aware rather than deleted.

Logged as future-work item #5. The developer must not touch `config/checkstyle/suppressions.xml`; the reviewer rejects a diff that does.

### Steps

Each step is one reviewable diff and leaves the build green. `./gradlew :model:testDebugUnitTest --rerun` must pass at the end of every step — **`--rerun` is mandatory**, a cached `UP-TO-DATE` result proves nothing.

**Standing obligation on every conversion step (Steps 2–12), per D18.** A step is not complete when the suite goes green. For each file the step converts, the developer additionally runs the D18 assertion-content diff against that file's Java original at the merge base, and pastes the result into Implementation Notes under that step's heading. The expected result is an **empty** diff for every file except the three carrying a D7 `L`-suffix fix, where it is exactly the one disclosed line. A non-empty, undisclosed residual stops the step — it is not deferred to Step 14, because at Step 14 the developer no longer remembers which conversion decision produced it.

1. **Capture the pre-conversion baseline** — no files change. Three artifacts, all pasted into **Implementation Notes**:
   - a. **Merge-base SHA.** `git merge-base HEAD develop` (currently `6b27ebd5f`). Record it explicitly. Every `.java` original is destroyed by its rename, so this SHA is the only route back to the pre-conversion text — `git show <sha>:<path>` is what Steps 2–12 and AC3B diff against. Without it recorded, the entire content audit becomes unreproducible by the reviewer.
   - b. **Per-class test counts.** `./gradlew :model:testDebugUnitTest --rerun`, then a `classname → tests/failures/errors/skipped` table for all 26 classes from `model/build/test-results/testDebugUnitTest/TEST-*.xml`.
   - c. **Per-file assertion-call counts** — `grep -cE "\b(assert[A-Za-z]*|verify)\(" <file>` for each of the 29 files (the AC3 table).

   Then write the D18 extractor to the session scratchpad (**not** to the repo — it is an audit tool, not a deliverable, and is not in File Scope) as `assertlines.pl`:

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
       next unless $a =~ /\b(?:assert[A-Za-z]*|verify)\s*\(/;
       $a =~ s/`//g;
       $a =~ s/::class\.java/.class/g;
       $a =~ s/\.get([A-Z])([A-Za-z0-9_]*)\(\)/"." . lc($1) . $2/ge;
       $a =~ s/\.(size|length)\(\)/.$1/g;
       $a =~ s/;\s*$//;
       $a =~ s/\)\s*\{\s*(.+?)\s*\}\s*$/, $1)/;
       $a =~ s/,\s*\(\s*\)\s*->\s*/, /;
       $a =~ s/\.set([A-Z])([A-Za-z0-9_]*)\(([^()]*)\)/"." . lc($1) . "$2 = $3"/ge;
       $a =~ s/\bnew\s+(?=[A-Z][A-Za-z0-9_]*\s*\()//g;
       $a =~ s/\s+/ /g;
       $a =~ s/\(\s+/(/g;
       $a =~ s/\s+\)/)/g;
       $a =~ s/^(?!assert|verify).*?(\w+)\s*=\s*(?=\S)/$1 = /;
       print "$a\n";
   }
   ```

   Validate it before relying on it, by reproducing the check already performed during planning: for all 29 Java files, its output line count must equal that file's AC3 grep count — **29/29 match, 563 assertions total**. Record the totals. If any file mismatches, the extractor is wrong for this codebase and must be fixed before Step 2; do not proceed with a mis-calibrated audit tool.

   The per-file invocation used from Step 2 onward is:

   ```bash
   diff <(git show <merge-base>:<java-path> | perl "$SCRATCH/assertlines.pl") \
        <(perl "$SCRATCH/assertlines.pl" < <kotlin-path>)
   ```

2. **Convert the 3 `Mother` helpers** — `FeedMother.java`, `FeedItemMother.java`, `FeedMediaMother.java` → `.kt`, as public `object` + `@JvmStatic` fun + `const val` per D1. `FeedItemMother` calls `FeedMother.anyFeed()` qualified (same package, no import needed). **No consumer file is edited in this step** — that is the point of D1, and a diff that touches any `*Test.java` here indicates the shape is wrong. Verifies: all 232 tests still green with 26 Java test classes calling 3 Kotlin helpers.

3. **Convert the `model.download` package** (5 files, 26 tests) — `DownloadErrorTest`, `DownloadRequestTest`, `DownloadResultTest`, `DownloadStatusTest`, `ProxyConfigTest`. Preserve `@RunWith(RobolectricTestRunner::class)` on `DownloadRequestTest` (D11). `DownloadErrorTest:46,47` `assertThrows` lambdas per D9's single-expression rule. Verifies: `DownloadErrorTest`=3, `DownloadRequestTest`=5, `DownloadResultTest`=11, `DownloadStatusTest`=3, `ProxyConfigTest`=4.

4. **Convert the `model.playback` package** (2 files, 5 tests) — `RemoteMediaTest` (preserve `@RunWith(RobolectricTestRunner::class)`), `TimerValueTest`. Verifies: `RemoteMediaTest`=3, `TimerValueTest`=2.

5. **Convert `MediaTypeTest` and `SortOrderTest`** (2 files, 19 tests) — root `model` package, no traps beyond the `assertThrows` lambdas at `SortOrderTest:35,40`. Verifies: `MediaTypeTest`=8, `SortOrderTest`=11.

6. **Convert `VolumeAdaptionSettingTest`** (1 file, 14 tests) — isolated as its own diff because it is the entire footprint of decision D3. Apply the 12 `` `is`(equalTo(x)) `` backticks, keep `@Test(expected = IllegalArgumentException::class)` at the 2 sites (D17), preserve the `@Before`/`@After` boost-supported state reset (D15), and wrap the 128-char line at `:100` (D8). Verifies: `VolumeAdaptionSettingTest`=14, and its assertion-call count is still 17.

7. **Convert the 7 trap-free `model.feed` tests** (33 + 11 tests) — `ChapterTest`, `FeedCounterTest`, `FeedOrderTest`, `TranscriptTypeTest`, `TranscriptSegmentTest`, `TranscriptTest`, `FeedFilterTest`. `FeedFilterTest` switches its qualified `FeedMediaMother.anyFeedMedia()` calls (`:134,140,145`) to the Kotlin object — textually identical, no change needed. Verifies: `ChapterTest`=9, `FeedCounterTest`=3, `FeedOrderTest`=3, `TranscriptTypeTest`=5, `TranscriptSegmentTest`=5, `TranscriptTest`=8, `FeedFilterTest`=11.

8. **Convert the `Serializable` round-trip cluster** (3 files, 50 tests) — `FeedFundingTest`, `FeedItemTest`, `FeedTest`. This diff carries 6 of the 8 `.use { }` conversions and the `in` → `input` renames (D6), **and 2 of the 3 int→Long fixes: `42L` at `FeedItemTest:238` and `99L` at `FeedTest:167` (D7)**. Wrap the 123-char line at `FeedTest:102` (D8). Carry over verbatim the `pubDateField` disclosure comment above `FeedItemTest:238`. Verifies: `FeedFundingTest`=14, `FeedItemTest`=18, `FeedTest`=18.

9. **Convert `FeedItemFallbackLinkTest`** (1 file, 7 tests) — isolated because it is the only `@RunWith(Parameterized::class)` class and the only one whose failure mode is a silent drop to 0 tests. Apply D12 exactly: `@JvmStatic` + `@Parameterized.Parameters` on the companion `data()`, `arrayOf<Any?>` rows, constructor `val`s. **Verifies: `FeedItemFallbackLinkTest`=7 — not 1, not 0.**

10. **Convert the `mockStatic(TextUtils)` pair** (2 files, 26 tests) — `FeedPreferencesTest`, `SubscriptionsFilterTest`. Carries the remaining 2 `.use { }` conversions (D6), **the third int→Long fix: `1L` at `FeedPreferencesTest:173` (D7)**, the 3 `` .`when` `` backticks (D4), the two overload-disambiguating casts and both hand-written `thenAnswer` bodies (D14), the `MockedStatic.close()` teardowns (D15), and the 121/123-char line wraps at `FeedPreferencesTest:116,117` (D8). Verifies: `FeedPreferencesTest`=16, `SubscriptionsFilterTest`=10, assertion counts 58 and 29.

11. **Convert the exception-message-pinning pair** (2 files, 33 tests) — `EmbeddedChapterImageTest`, `FeedItemFilterTest`. Apply D9 in full: single-expression `assertThrows` lambdas, 1:1 `assertNull`/`assertNotNull`/`stackTrace[0].className.startsWith(...)` conversions, both class-name literals preserved, all explanatory comments carried over verbatim. Also the 5 `` `when` `` backticks and the `thenReturn(null)` typing at `EmbeddedChapterImageTest:158` (D4), and the 131-char wrap at `:167` at an argument boundary only (D8). `FeedItemFilterTest`'s qualified `FeedMother.anyFeed()` calls (`:274,276,278`) need no change. **`EmbeddedChapterImageTest` does not gain Robolectric** (D11). Verifies: `EmbeddedChapterImageTest`=13, `FeedItemFilterTest`=20.

12. **Convert `FeedMediaTest`** (1 file, 8 tests) — the last file, isolated because it stacks four hazards: Robolectric, Mockito mocking the Kotlin final class `FeedItem` (`:45,62,79`), the 6 `` `when` `` backticks (D4), and the reflection literal `"hasEmbeddedPictureField"` (D13). `FeedMedia.class` → `FeedMedia::class.java`. Verifies: `FeedMediaTest`=8, and `find model/src/test -name '*.java' | wc -l` → **0**.

13. **Re-prove the 3 pinning tests by revert-and-reverify** — no committed diff. For each, temporarily mutate the *production* Kotlin, confirm the converted Kotlin test **fails**, then revert:
    - `EmbeddedChapterImage.getModelFor`: move the null-`chapters` dereference to a `!!` at extraction time → `getModelForNullChaptersThrowsNpe` must fail on the frame-0 class-name assertion.
    - `EmbeddedChapterImage.getModelFor`: guard the null `imageUrl` before it reaches `Pattern.matcher()` → `getModelForNullImageUrlThrowsFromInsideMatcherNotAtExtraction` must fail on `assertNotNull(message)` and/or the `java.util.regex` frame check.
    - `FeedItemFilter.matches`: make the null `lastPlayedTime` path not throw → `testMatchesShowInHistoryWithNullLastPlayedTimeHistoryThrowsNpe` must fail.
    Record each mutation, the resulting failure message, and `git status` showing a clean tree afterwards, in **Implementation Notes**. These mutations are never committed.

14. **Run the full gate set and reconcile all three invariants** — `./gradlew :model:testDebugUnitTest --rerun`, `./gradlew :model:ktlintCheck`, `./gradlew checkstyle lint`, `./gradlew :app:assembleDebug`. Then reconcile against the Step 1 baseline:
    - a. Re-extract the per-class test table (26 rows) and diff against baseline b.
    - b. Re-extract the per-file assertion-count table (29 rows) and diff against baseline c.
    - c. **Re-run the D18 assertion-content diff across all 29 files in one sweep** and paste the consolidated result: a `file → residual line count` table whose every row reads `0` except `FeedItemTest` = 1, `FeedTest` = 1, `FeedPreferencesTest` = 1, plus the three residual lines quoted verbatim. This is a whole-suite re-derivation, not a restatement of the per-step results — it catches a file that a later step touched incidentally after its own audit passed.

    Paste all three post-conversion tables into **Implementation Notes** alongside their diff results. Any residual beyond the three disclosed lines is carried into Implementation Notes with its per-line justification (D18) for the reviewer to accept or reject individually.

15. **Update `model/README.md`** to record the conventions per D10 — test suite now 100% Kotlin, the `object` + `@JvmStatic` Mother pattern and why `internal` is wrong for test helpers, verbatim test-method-name preservation and the ktlint `org.junit` exemption that permits it, and a restatement of the Robolectric scope constraint. Long-term-stable patterns only, per AGENTS.md — no milestone or task-specific provenance.

### File Scope

The developer may modify or create **only** these. Each `.kt` replaces the `.java` of the same name at the same path (a rename, so `git mv` + rewrite; no file moves between directories, no new source set).

`model/src/test/java/de/danoeh/antennapod/model/`
- `MediaTypeTest.java` → `.kt`
- `SortOrderTest.java` → `.kt`
- `VolumeAdaptionSettingTest.java` → `.kt`

`model/src/test/java/de/danoeh/antennapod/model/download/`
- `DownloadErrorTest.java` → `.kt`
- `DownloadRequestTest.java` → `.kt`
- `DownloadResultTest.java` → `.kt`
- `DownloadStatusTest.java` → `.kt`
- `ProxyConfigTest.java` → `.kt`

`model/src/test/java/de/danoeh/antennapod/model/feed/`
- `ChapterTest.java` → `.kt`
- `EmbeddedChapterImageTest.java` → `.kt`
- `FeedCounterTest.java` → `.kt`
- `FeedFilterTest.java` → `.kt`
- `FeedFundingTest.java` → `.kt`
- `FeedItemFallbackLinkTest.java` → `.kt`
- `FeedItemFilterTest.java` → `.kt`
- `FeedItemMother.java` → `.kt`
- `FeedItemTest.java` → `.kt`
- `FeedMediaMother.java` → `.kt`
- `FeedMediaTest.java` → `.kt`
- `FeedMother.java` → `.kt`
- `FeedOrderTest.java` → `.kt`
- `FeedPreferencesTest.java` → `.kt`
- `FeedTest.java` → `.kt`
- `SubscriptionsFilterTest.java` → `.kt`
- `TranscriptSegmentTest.java` → `.kt`
- `TranscriptTest.java` → `.kt`
- `TranscriptTypeTest.java` → `.kt`

`model/src/test/java/de/danoeh/antennapod/model/playback/`
- `RemoteMediaTest.java` → `.kt`
- `TimerValueTest.java` → `.kt`

Docs (spec-workflow trail, shipping in the same PR per the pre-research callout):
- `model/README.md` (Step 15 only)
- `tasks/antennapod-model-kotlin-milestone-7.md`
- `features/antennapod-model-kotlin-milestone-7.checkpoint.md`
- `tasks/antennapod-model-kotlin-future-work.md` (modified during planning, before implementation began, to log future-work items #3–#5; disclosed here for record accuracy)

**Not in scope — the reviewer rejects any diff touching these:** `model/build.gradle` (D16), any file under `model/src/main/` (except the temporary, uncommitted Step 13 mutations), `common.gradle`, `.editorconfig`, `config/checkstyle/suppressions.xml` (D19 — it names two in-scope test files, but the entries are already inert and are deliberately left orphaned), `gradle/libs.versions.toml`, `.github/workflows/`, or any file in any other module.

The D18 audit script (`assertlines.pl`) lives in the session scratchpad and is **not** committed — it is a verification tool, not a deliverable, and adding it to the repo would be an out-of-scope file creation.

### Acceptance Criteria

Track: `kotlin` (test source set). Every item is checked against the Step 1 baseline recorded in Implementation Notes.

**Suite equivalence**
- [ ] AC1 — `./gradlew :model:testDebugUnitTest --rerun` is BUILD SUCCESSFUL with **232 tests, 0 failures, 0 errors, 0 skipped** — matching the pre-conversion baseline exactly. The `--rerun` flag is present in the recorded command; an `UP-TO-DATE` run does not satisfy this.
- [ ] AC2 — The post-conversion per-class test count matches the Research table **row for row, for all 26 classes** — not merely in aggregate. Specifically: `FeedItemFallbackLinkTest` = **7**; `FeedItemFilterTest` = 20; `FeedPreferencesTest` = 16; `FeedItemTest` = 18; `FeedTest` = 18; `FeedFundingTest` = 14; `VolumeAdaptionSettingTest` = 14; `EmbeddedChapterImageTest` = 13; `DownloadResultTest` = 11; `FeedFilterTest` = 11; `SortOrderTest` = 11; `SubscriptionsFilterTest` = 10; `ChapterTest` = 9; `FeedMediaTest` = 8; `MediaTypeTest` = 8; `TranscriptTest` = 8; `DownloadRequestTest` = 5; `ProxyConfigTest` = 4; `TranscriptSegmentTest` = 5; `TranscriptTypeTest` = 5; `DownloadErrorTest` = 3; `DownloadStatusTest` = 3; `FeedCounterTest` = 3; `FeedOrderTest` = 3; `RemoteMediaTest` = 3; `TimerValueTest` = 2. Both tables are pasted into Implementation Notes.
- [ ] AC3 — The per-file assertion-call count (`grep -cE "\b(assert[A-Za-z]*|verify)\(" <file>`) is **identical** before and after for all 29 files: `DownloadErrorTest` 26, `DownloadRequestTest` 26, `DownloadResultTest` 31, `DownloadStatusTest` 7, `ProxyConfigTest` 13, `ChapterTest` 9, `EmbeddedChapterImageTest` 22, `FeedCounterTest` 12, `FeedFilterTest` 43, `FeedFundingTest` 28, `FeedItemFallbackLinkTest` 1, `FeedItemFilterTest` 66, `FeedItemTest` 26, `FeedMediaTest` 28, `FeedOrderTest` 10, `FeedPreferencesTest` 58, `FeedTest` 21, `SubscriptionsFilterTest` 29, `TranscriptSegmentTest` 11, `TranscriptTest` 17, `TranscriptTypeTest` 16, `MediaTypeTest` 8, `RemoteMediaTest` 20, `TimerValueTest` 4, `SortOrderTest` 14, `VolumeAdaptionSettingTest` 17, and 0 for all three `Mother` files. **This criterion is necessary but NOT sufficient on its own** — it counts assertions, it does not read them, so two canceling edits (one expected value altered, one assertion dropped) would satisfy it with a green suite. AC3B is the check that actually closes that hole; AC3 is retained as the cheap, independent tripwire that the assertion *population* is unchanged.
- [ ] AC3B — **Assertion content is diffed 1:1 against the Java original for all 29 files** (D18). Using the canonical extraction from Step 1 and the merge-base SHA recorded there, the per-file residual diff is **empty for 26 of 29 files**, and for the other three is **exactly one line each** — `FeedItemTest` `assertEquals(42, deserialized.id)` → `assertEquals(42L, ...)`, `FeedTest` `assertEquals(99, ...)` → `assertEquals(99L, ...)`, `FeedPreferencesTest` `assertEquals(1, prefs.feedID)` → `assertEquals(1L, ...)`. Every expected-value literal and every actual-value expression is therefore unchanged from the Java original beyond the disclosed `L`-suffix and keyword-escape fixes. Evidence pasted in Implementation Notes: the Step 1 extractor validation (29/29 line counts equal to AC3's, 563 assertions total), the per-step per-file diffs, and Step 14's consolidated 29-row `file → residual count` table. **The reviewer re-runs the exact command from Step 1 and confirms it reproduces** — this criterion is not satisfied by the developer's assertion that they checked. Any residual line beyond the three disclosed ones is present in Implementation Notes with a written one-line justification and is individually accepted by the reviewer; a residual that changes an expected-value literal, swaps expected/actual argument order, or changes the assertion function itself fails this criterion outright and is not justifiable.
- [ ] AC4 — `git diff` of the Java-vs-Kotlin **test method name lists** is empty: all 232 test method names, including the 15 with underscores, are preserved byte-for-byte. No test method is renamed and no backtick-quoted sentence-style name is introduced anywhere (D5).

**The three numeric-widening fixes**
- [ ] AC5 — `FeedItemTest.kt` asserts `assertEquals(42L, deserialized.id)`, `FeedTest.kt` asserts `assertEquals(99L, deserialized.id)`, `FeedPreferencesTest.kt` asserts `assertEquals(1L, prefs.feedID)` — the fix is an `L` suffix on the literal in all three cases.
- [ ] AC6 — Neither side of any of those three assertions gained a `.toInt()`, `.toLong()`, or `.toString()` conversion, and none was relaxed to `assertTrue(... == ...)`. `grep -rn "toInt()\|toLong()" model/src/test/` returns no new occurrence relative to the Java baseline.
- [ ] AC7 — No other `assertEquals` in the suite compares an `Int` literal to a `Long` expression. AC1 being green is the proof (Kotlin resolves such a pair to `assertEquals(Any?, Any?)`, which fails at runtime), and the reviewer confirms no site was made green by a conversion call rather than by an `L` suffix.

**The three exception-message-pinning regression guards**
- [ ] AC8 — Each of `EmbeddedChapterImageTest.getModelForNullChaptersThrowsNpe`, `EmbeddedChapterImageTest.getModelForNullImageUrlThrowsFromInsideMatcherNotAtExtraction`, and `FeedItemFilterTest.testMatchesShowInHistoryWithNullLastPlayedTimeHistoryThrowsNpe` still asserts on message nullity **and** (for the two `EmbeddedChapterImage` cases) on `stackTrace[0].className.startsWith(...)`, with both class-name string literals (`"de.danoeh.antennapod.model.feed.EmbeddedChapterImage"`, `"java.util.regex"`) unchanged character-for-character.
- [ ] AC9 — Each `assertThrows` body is a single call expression with no wrapper, helper, or intermediate local that could shift stack frame 0 (D9).
- [ ] AC10 — Step 13's revert-and-reverify is recorded in Implementation Notes: for each of the 3 tests, the temporary production mutation applied, the resulting **failure** output proving the test still discriminates, and a clean `git status` after revert. A pass alone does not satisfy this criterion.

**Idiomatic-target and toolchain gates**
- [ ] AC11 — `./gradlew :model:ktlintCheck` is BUILD SUCCESSFUL (this now includes `ktlintTestSourceSetCheck`, which was a no-op before this milestone), with **zero** `@Suppress("ktlint:...")` annotations added, no `ktlint_disabled_rules`, no `.editorconfig` change, and no ktlint filter/exclusion in any build file.
- [ ] AC12 — The 5 previously-unlinted over-length lines are wrapped to ≤120 chars — `VolumeAdaptionSettingTest:100`, `EmbeddedChapterImageTest:167`, `FeedTest:102`, `FeedPreferencesTest:116,117` — by line breaks at argument boundaries only. No expression was restructured, extracted to a local, or shortened by dropping an argument.
- [ ] AC13 — `./gradlew checkstyle lint` is BUILD SUCCESSFUL, and `./gradlew :app:assembleDebug` compiles (confirming no production `.kt` was disturbed).
- [ ] AC14 — `find model/src/test -name '*.java' | wc -l` → **0**, and `find model/src/test -name '*.kt' | wc -l` → **29**. Every file is a rename in place — no file changed package or directory, and no new source set was added.
- [ ] AC15 — Idiomatic Kotlin achieved without behaviour drift: no unjustified `!!` anywhere in the test source set; the `in` locals renamed to `input` and no identifier we own is backticked; the only backticked identifiers in the whole suite are the 12 hamcrest `` `is` `` calls and the 14 Mockito `` `when` `` calls (both foreign members, per D3/D4). `grep -rn '`' model/src/test/` returns exactly those 26 sites and nothing else.
- [ ] AC16 — All three `Mother` files are public `object`s with `@JvmStatic` on the builder function and `const val` for constants; **no `internal` modifier appears in any of the three** (D1). Step 2's diff touches only those 3 files — no consumer was edited to accommodate the new shape.
- [ ] AC17 — `FeedItemFallbackLinkTest.kt`'s `data()` carries **both** `@JvmStatic` and `@Parameterized.Parameters` inside a `companion object`, and its rows use `arrayOf<Any?>`. (AC2's count of 7 is the runtime proof; this is the source-level check that the count is right for the right reason.)
- [ ] AC18 — `FeedMediaTest.kt` still contains the literal `"hasEmbeddedPictureField"` unchanged, and `@RunWith(RobolectricTestRunner::class)` appears on exactly 3 classes — `DownloadRequestTest`, `FeedMediaTest`, `RemoteMediaTest`. `grep -rn "Robolectric" model/src/test/` shows no occurrence in `EmbeddedChapterImageTest` (D11).
- [ ] AC19 — All four teardowns survive with their bodies intact: `VolumeAdaptionSettingTest`'s `@After` calling `setBoostSupported(null)`, and the `MockedStatic.close()` in `EmbeddedChapterImageTest`, `FeedPreferencesTest`, `SubscriptionsFilterTest` (D15).

**Scope and documentation**
- [ ] AC20 — `git diff --name-only` against the merge base lists only files from the File Scope list. In particular `model/build.gradle` is unchanged (D16), `config/checkstyle/suppressions.xml` is unchanged (D19), no file under `model/src/main/` appears, and the D18 `assertlines.pl` audit script was not committed.
- [ ] AC21 — `model/README.md` documents the four conventions named in D10, phrased as long-term-stable module patterns with no milestone or task-specific provenance.
- [ ] AC22 — No public API break: `:model`'s production surface is untouched, and the three `Mother` helpers — the only cross-file test API — remain callable as `FeedMother.anyFeed()` / `FeedMother.IMAGE_URL` from both Java and Kotlin. Confirmed by AC1 passing at every intermediate step while Java and Kotlin test files coexisted.

### Milestone

**Milestone 7: `:model` module, `kotlin` track — test-source-set conversion.** This is the final `kotlin`-track milestone for `:model`; on completion the module is 100% Kotlin in both `src/main` and `src/test`, and the `kotlin` track for `:model` is closed. No further `:model` track is in flight.

### Out of Scope

- **Any production code change.** No file under `model/src/main/` is modified. Step 13's mutations are temporary, uncommitted, and reverted within the step. If a genuine J2K-interop issue forces a minimal production fix, it stops the step and is disclosed per the Milestone 4/6 deviation-disclosure pattern — it is not absorbed silently.
- **Adding Robolectric to `EmbeddedChapterImageTest`** or to any other test. The cluster is 3 files and stays 3 files (D11). "Making the four files consistent" is not a goal — the fourth file was never a Robolectric file.
- **Changing assertion libraries.** No migration of hamcrest to `assertEquals` (D3), no `assertThrows` ↔ `@Test(expected=)` swaps in either direction (D17), no `mockito-kotlin` / `whenever` (D4), no `kotlin.test`, no AssertJ, no Truth.
- **Renaming any test method**, including the 15 underscore-containing ones, and including "improving" any to backtick-quoted sentence names (D5).
- **`model/build.gradle` changes** of any kind (D16) — including correcting the stale "this milestone's four files" wording in the Robolectric disclosure comment, which is noted as an Open Question rather than fixed here.
- **Cleaning up the two orphaned `config/checkstyle/suppressions.xml` entries** naming `VolumeAdaptionSettingTest.java` (line 14) and `FeedFilterTest.java` (line 15) (D19). They are already inert — checkstyle never sources `src/test` — and become permanently orphaned by the rename. Explicitly disclosed as a known, harmless residual rather than silently ignored; logged as future-work item #5. This also corrects Research's "External references to test class names: none", which was wrong on this point.
- **Tightening test-helper visibility to `internal`** now that the suite will be 100% Kotlin. Deliberately deferred: it is a separate, reviewable change whose only benefit is cosmetic, and mixing it into this diff would put a name-mangling risk inside the milestone that is specifically about avoiding silent breakage.
- **Deduplicating, restructuring, or "improving" any test** — no merging duplicate setup, no extracting shared helpers or base classes, no parameterizing repetitive tests, no adding new test cases. Every behavioural improvement opportunity spotted during conversion goes to `tasks/antennapod-model-kotlin-future-work.md`, not into this diff.
- **Converting test sources in any other module.** This milestone establishes the precedent (recorded in `model/README.md`); applying it to `:storage:database`, `:parser:feed`, etc. is separate, separately-priced work.
- **KMP/Parcelable decoupling and upstreaming intent** — tracked in `tasks/antennapod-model-kotlin-future-work.md`, unchanged by this milestone.
- **Fixing any pre-existing warning** in these files beyond what ktlint (AC11) demands.

---

## Open Questions

1. **Gate coverage narrows for tests once the last Java test file converts — is a replacement wanted?** After Step 12, `compileDebugUnitTestJavaWithJavac` goes `NO-SOURCE`, so `-Xlint:all -Werror` (applied in `common.gradle` to all `JavaCompile` tasks) stops applying to `:model`'s tests, while `ktlintTestSourceSetCheck` starts. These cover different things — the first is compiler warnings, the second is formatting. Nothing in this milestone's scope adds a Kotlin equivalent (e.g. `allWarningsAsErrors` on the Kotlin test compile task). That would be a `common.gradle` change affecting every module and is deliberately excluded from File Scope. **Flag for José** — it is a cross-cutting build-policy decision, not a `:model` one, and it will recur on every subsequent module's test conversion.

2. **`model/build.gradle`'s Robolectric disclosure comment is stale.** It reads "Do not broaden usage beyond Parcel characterization tests for this milestone's four files", but the researcher verified only **three** files use Robolectric (`EmbeddedChapterImageTest` uses `mockStatic(TextUtils)` instead). Correcting it is a one-line comment edit, but it would put `model/build.gradle` inside File Scope for a non-functional reason. Left uncorrected here; the accurate three-file constraint is instead restated in `model/README.md` (Step 15). Confirm whether the comment should be corrected as a trivial follow-up.

3. **Does the `object` + `@JvmStatic` Mother convention (D1) become the standing rule for all subsequent module test conversions, or is it `:model`-specific?** It is recorded in `model/README.md` per D10, but there is no cross-module convention document. If it should bind future modules, it belongs in the service-line agent definitions rather than in one module's README — and per the standing "no project coupling in shared skills" note, it would need to be written as the generalizable pattern with the `:model` provenance stripped. **Flag for José.**

### Resolutions (no José turnaround available this session — decided per standing defaults, documented for review)

1. **Gate coverage narrows (no `allWarningsAsErrors` for Kotlin test compile) — deferred, not decided now.** This is a `common.gradle`, repo-wide build-policy change with no `:model`-only version; forcing it into this milestone would violate File Scope discipline for a benefit this milestone doesn't need (the 232 tests are already the oracle, and `-Xlint:all` on Java test sources was never a substitute for that). Logged as a new deferred initiative in `tasks/antennapod-model-kotlin-future-work.md` (`#3`) rather than actioned — same treatment as the Milestone 6 KMP-decoupling deferral. Non-blocking for this milestone.
2. **Stale Robolectric comment in `model/build.gradle`** — leaving it uncorrected, as the planner proposed, since fixing a comment is not worth widening File Scope to a file this milestone otherwise has no reason to touch. Logged as a one-line fast-follow in `tasks/antennapod-model-kotlin-future-work.md`.
3. **Mother convention scope** — kept `:model`-local (`model/README.md` only) for now. One data point (three Mother classes, one module) is not enough evidence to generalize into a shared agent definition without unjustified abstraction; per the standing "no project coupling in shared skills" policy, promoting it prematurely would also risk baking this module's specifics into a supposedly-generic rule. Revisit if the same shape recurs in a second module.

### Track prerequisites — status

`kotlin` — **no blocking prerequisite.** `:model`'s production code is 100% Kotlin (27/27 `.kt`), the Kotlin test-compile path is already wired (`compileDebugUnitTestKotlin` exists, currently `NO-SOURCE`), no `model/build.gradle` change is required, and the standing test-migration-sequencing gate ("convert tests only after the whole module's production code is migrated") is satisfied. Nothing is deferred or escalated on prerequisite grounds.

---

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-07-25 | Loop 1 of max 2_

### Verdict
CHALLENGE

### Concerns

- **Severity:** MAJOR
  **Class:** Characterization tests prove equivalence, not just existence (checklist #1) / Coverage gaps left unaddressed (checklist #4)
  **Concern:** AC3 ("per-file assertion-call count... identical before and after") is presented as *the* check for "a dropped assertion is the failure mode a green build cannot see," but a bare `grep -cE` count only proves the *number* of `assert*`/`verify` invocations is unchanged — it does not prove their *arguments* are unchanged. Two canceling mistakes (one assertion's expected value silently altered or duplicated, one dropped elsewhere in the same file) would produce an identical count and an identical passing suite, and AC3 would report green. For the ~223 assertions outside the 6 explicitly hand-verified sites (D7's 3 int→Long fixes, D9's 3 pinning tests), nothing in the Plan diffs assertion *content* — only count (AC3), test count (AC2), method-name list (AC4), and pass/fail (AC1). A test whose expected value is silently changed to something that also happens to be true post-conversion (or whose comparison operands are swapped in a way that stays green) is exactly the milestone's stated central risk, and AC3 does not catch it.
  **Evidence:** `tasks/antennapod-model-kotlin-milestone-7.md` AC3 (line ~449, "A dropped assertion is the failure mode a green build cannot see; this is the check for it.") — I independently ran the plan's own `grep -cE "\b(assert[A-Za-z]*|verify)\(" <file>` command against all 29 live files and every count matches AC3's table exactly (e.g. `FeedPreferencesTest.java` 58, `FeedItemFilterTest.java` 66, `FeedFilterTest.java` 43 — all confirmed), so the baseline is accurate, but the mechanism itself is count-only, not content-only.
  **Suggested mitigation:** Add an explicit Step/AC requiring the developer (or migration-code-reviewer at the next gate) to diff each converted file's assertion *expressions* line-by-line against the Java original — e.g. "for every `.kt` file, the code reviewer confirms each assertion's actual/expected arguments correspond 1:1 in order and identifier to the Java source, not just that the call count matches." Given the "no restructuring, no helper extraction" Out-of-Scope constraints already in place, this is a cheap, mechanical addition (the diff is close to line-for-line already) — but it should be stated as a requirement, not left implicit.

- **Severity:** MINOR
  **Class:** Coverage gaps left unaddressed / File Scope completeness (checklist #4, #9)
  **Concern:** Research's claim "External references to test class names: none... repo-wide search across `*.gradle`, `*.yml`, `*.yaml`, `*.kts`, `*.sh`, `*.py`, `*.xml`, `*.properties`, `*.json` found no matches" is not literally accurate. `config/checkstyle/suppressions.xml` (an `.xml` file, inside the searched extension set) contains two suppression entries naming files in this milestone's File Scope by filename: `<suppress checks="LineLength" files="(VolumeAdaptionSettingTest.java|...)"/>` (line 14) and `<suppress checks="VariableDeclarationUsageDistance" files="(FeedFilterTest.java|...)"/>` (line 15). I verified this has **zero functional consequence**: `common.gradle:147-158`'s `checkstyle` task hardcodes `source = fileTree('src/main/java')` (+ free/play variants) and never includes `src/test` for any module, so these two suppression entries are already inert today and will remain inert (just permanently orphaned, matching a filename that no longer exists) after the `.java`→`.kt` rename. `AC13` (`checkstyle` BUILD SUCCESSFUL) is unaffected either way — I confirmed this by reading the task registration directly rather than trusting the research summary.
  **Evidence:** `config/checkstyle/suppressions.xml:14-15`; `common.gradle:147-158` (checkstyle task `source` fileTree, no `src/test` inclusion).
  **Suggested mitigation:** No action required for correctness — this does not block the milestone. Worth a one-line note in Implementation Notes or a fast-follow item (same treatment as the other stale-comment deferrals already logged in Resolutions #2) so the "no external references" claim in Research is corrected for the record, since a future module's test conversion may hit the same now-orphaned-suppression pattern and it's cheap to flag once.

### Verification performed (not just trusted from research/plan prose)

- Confirmed `FeedItem.id`, `Feed.id`, `FeedPreferences.feedID` are all `Long` (`FeedItem.kt:20`, `Feed.kt:20`, `FeedPreferences.kt:6`) — D7's three fixes are correct and, per an independent grep sweep of every `assertEquals(<int literal>, ...)` site in the suite, complete (no 4th site).
- Empirically compiled a scratch `internal @JvmStatic fun` inside an `object` in this project's actual Kotlin toolchain (not assumed from general Kotlin knowledge) and inspected the resulting `.class` with `javap`: it emits `public static final java.lang.String anyFeed$model_debugUnitTest()` — confirming name mangling really does break a plain Java `import static` reference, which is D1's core justification for rejecting `internal`. Scratch file was deleted immediately after, tree left clean.
- Counted actual `is(` and `when(`/`MockedStatic.when(` call sites in `VolumeAdaptionSettingTest.java`, `EmbeddedChapterImageTest.java`, `FeedMediaTest.java`, `FeedPreferencesTest.java`, `SubscriptionsFilterTest.java` — 12 and 14 respectively, matching D3/D4 exactly, including the static-import-vs-instance-call split (10 static + 4 instance).
- Confirmed `Playable.chapters: List<Chapter>?` is nullable (justifying D4's `thenReturn(null)` caution) and `FeedItemFilter.matches` still has the `!!` on `item.media!!.lastPlayedTimeHistory!!` (`FeedItemFilter.kt:114`) that D9/Step 13's revert-and-reverify targets.
- Verified `model/build.gradle` directly: no `kotlin("test")` dependency, `compileDebugUnitTestKotlin` already exists — and confirmed live by actually compiling a Kotlin file in `model/src/test` via `./gradlew :model:compileDebugUnitTestKotlin`, which succeeded. D16's "zero build.gradle changes" claim holds.
- Verified all 5 claimed over-length lines (`VolumeAdaptionSettingTest:100`=128, `EmbeddedChapterImageTest:167`=131, `FeedTest:102`=123, `FeedPreferencesTest:116`=121, `:117`=123) and `.editorconfig`'s `max_line_length = 120` — exact match.
- Re-ran the plan's own AC3 assertion-count grep against all 29 live files — every one of the 29 counts in AC3's table matches exactly.

Both concerns above are addressable without re-planning the whole milestone; loop 2 should confirm AC3 gets a content-level companion check and the stale-suppression fact is corrected in Research's "no external references" claim (or explicitly accepted as a known, harmless residual).

---

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-07-25 | Loop 2 of max 2 (FINAL)_

### Verdict
CHALLENGE — one MAJOR finding survived independent verification. Per the loop cap, this does **not** trigger a third planner loop; see Disposition below for why "accept and document now" is the correct handling, mirroring the Milestone 4 loop-2 precedent.

### Concerns

- **Severity:** MAJOR
  **Class:** Characterization tests prove equivalence, not just existence (checklist #1) / Coverage gaps left unaddressed (checklist #4)
  **Concern:** D18/AC3B's core design (diff assertion *content*, not just count) is sound — I independently verified it catches the exact class of bug loop 1 flagged. But the revision's validation claim ("built and validated the extractor... zero mismatches, 563 assertions total... so the developer is not the first to run it") only checked the extractor's *line count* against AC3's grep count, both computed **on the Java side only**. It never ran the extractor against actual (or hand-built, plan-compliant) Kotlin output. When I did that — hand-converting 3 of the plan's own named spot-check files exactly per D1–D17 — the extractor produced **false-positive residuals from gaps missing in the canonicalization table**, on files AC3B promises will be clean:
    1. **`new` keyword removal is not in the canonicalization table.** Kotlin has no `new` keyword — `new Date(0)` *must* become `Date(0)`, this is mandatory syntax, not a style choice. An assertion line containing `new X(...)` will **always** produce a residual under the current table. I grepped assertion lines across all 29 files for this: it hits **`ChapterTest`, `EmbeddedChapterImageTest`, `SubscriptionsFilterTest`, `RemoteMediaTest`, and `FeedMediaTest`** (8 sites total) — five files the plan promises will show an *empty* residual.
    2. **`.equals()` → `==` idiom is not canonicalized.** `FeedMediaTest.java:149-150` (`assertTrue(fm.equals(rm))` / `assertTrue(rm.equals(fm))`) is a plausible idiomatic-Kotlin conversion (`fm == rm`), semantically identical for these classes, but textually different — another guaranteed residual on `FeedMediaTest`, one of the two hardest files in the milestone.
    3. **D8's own mandated line-wraps introduce spurious paren-adjacent whitespace.** I hand-wrapped `VolumeAdaptionSettingTest:100` and `EmbeddedChapterImageTest:167` (both explicitly named in D8 as requiring a wrap) using two different natural wrap styles (IDE-reflow "each-arg-on-own-line", and mid-chain break) — **both styles produced a non-empty residual**, purely from whitespace adjacent to the parens after the paren-balance joiner reassembles the physical lines. The `\s+` → single-space canonicalization rule collapses runs of whitespace but does not strip a lone space immediately after `(` or before `)`. I confirmed a differently-shaped wrap (keep `(`/`)` glued to adjacent tokens, break only at the comma) avoids the artifact — but the plan does not specify this constraint, so a developer using ordinary IDE reformatting will very likely trip it. Critically, `EmbeddedChapterImageTest:167` is **one of the three D9 exception-message-pinning assertions** — the single highest-stakes line in the milestone — and I also reproduced the same artifact on `FeedPreferencesTest:116`, which is already one of the plan's three "exactly one disclosed residual line" exception files; a real-world wrap there would push it to **two** residual lines, not one.

    Net effect: at least **6 of the 26 "must be empty" files** (`ChapterTest`, `EmbeddedChapterImageTest`, `SubscriptionsFilterTest`, `RemoteMediaTest`, `FeedMediaTest`, `VolumeAdaptionSettingTest`) and potentially one of the **three "exactly one line" exception files** (`FeedPreferencesTest`) will show residuals AC3B's binding claim says should not exist — not because of any real behavioral drift (I found **zero false negatives** — see Verification below), but because the canonicalization table is incomplete. This is a real gap that should be closed before Step 1 is relied on: an unprepared developer hitting an unanticipated "hard stop" on 6-7 files, with no forewarning in the Known Limits list, either burns time re-deriving that it's benign, or — worse, under time pressure — hastily broadens the extractor's regex in a way that could swallow a real difference along with the benign one.
  **Evidence:** `tasks/antennapod-model-kotlin-milestone-7.md` D18 canonicalization table (lines ~368-378, no `new`-keyword or `.equals()`/`==` rule); Step 1's `assertlines.pl` (lines ~419-446, `s/\s+/ /g` does not strip paren-adjacent single spaces). Live greps: `new` inside assertion lines hits `ChapterTest.java`, `EmbeddedChapterImageTest.java`, `SubscriptionsFilterTest.java`, `RemoteMediaTest.java` (1 each), `FeedMediaTest.java` (4) — 8 sites, 5 files. `.equals(` inside assertion lines hits `FeedMediaTest.java:149-150` among others. Hand-converted Kotlin for `VolumeAdaptionSettingTest`, `FeedMediaTest`, and the `EmbeddedChapterImageTest:167`/`FeedPreferencesTest:116` wrap sites, diffed against the Java originals with the plan's own `assertlines.pl` — reproducible non-empty residuals in every case above, all traceable to a missing canonicalization rule, none to an actual value/logic change.
  **Suggested mitigation:** Before Step 1 is relied on, extend `assertlines.pl` with two more canonicalization rules — strip a leading `new ` before a constructor call, and normalize `X.equals(Y)` ↔ `X == Y` (or, consistent with D3/D4/D5's "don't spend idiom points where the suite is the only oracle" principle, simply forbid the `.equals()`→`==` simplification during this milestone and require `.equals()` preserved verbatim, which is a documentation-only fix, not a script fix) — and additionally normalize whitespace immediately adjacent to `(`/`)` (e.g. `s/\(\s+/(/`, `s/\s+\)/)/` after the existing whitespace collapse). Add both gaps to D18's "Known limits" paragraph so they're disclosed rather than discovered mid-implementation. None of this changes any Decision, Step, or Acceptance Criterion — it only tightens a non-committed scratch tool and its own disclosure text, so it does not require re-scoping.

### Verification performed (not just trusted from plan prose)

- Re-derived D19 independently, including the specific risk named in my task brief (a second checkstyle task like `checkstyleTest` picking up the orphaned suppressions). Confirmed the standard Gradle `checkstyle` plugin **is** applied repo-wide (`build.gradle:7`, `common.gradle:131`) — which normally auto-generates a `checkstyleMain`/`checkstyleTest` task per source set — but ran `./gradlew :model:tasks --all | grep -i checkstyle` live and got exactly **one** task, named `checkstyle` (the manually-registered one). Android modules don't get the plugin's automatic per-`SourceSet` task generation because Android's source-set model isn't the Java plugin's `SourceSetContainer` the checkstyle plugin's task-creation rule depends on — so no `checkstyleTest` exists today, and the two orphaned suppressions (`suppressions.xml:14-15`) are exactly as dead as D19 claims. Also confirmed CI invokes only `./gradlew checkstyle lint` (`.github/workflows/checks.yml:46`), same single task. **D19 is sound, no concerns.**
- Reproduced Step 1's extractor validation myself from scratch (wrote my own copy of `assertlines.pl` from the Plan text, not reused from any prior session artifact): ran it against all 29 live Java files, diffed its per-file line count against a fresh `grep -cE` run — **29/29 match, 563 total**, identical to the plan's claim. This part of the validation is accurate as literally stated.
- Built a "canceling mistake" test directly targeting loop 1's original MAJOR concern: took `VolumeAdaptionSettingTest`, hand-converted it correctly to Kotlin per D1-D17, then produced two buggy variants — (a) `>` silently weakened to `>=` (stays green at runtime, discriminates less), (b) one expected value altered (`equalTo(2)` → `equalTo(3)`) *combined with* dropping one assertion and adding a compensating duplicate elsewhere, so the total assertion count is unchanged. **AC3's count check is green in both cases** (17 assertions before and after, in both buggy variants) — confirming loop 1's finding that count alone is blind to this class of bug. **AC3B's content diff catches both** — non-empty, specific residuals pinpointing exactly the altered/dropped/duplicated lines. This is the core claim under review, and it holds: **the D18/AC3B mechanism, as a detection strategy, is sound and independently verified to close the gap loop 1 found.**
- Hand-converted `FeedItemFallbackLinkTest` (the `@Parameters`-based spot-check target) per D12 exactly; its one assertion diffs clean (empty residual), consistent with the plan's claim for this file.
- Confirmed all 5 over-length lines and their exact character counts independently (`VolumeAdaptionSettingTest:100`=128, `EmbeddedChapterImageTest:167`=131, `FeedTest:102`=123, `FeedPreferencesTest:116`=121, `:117`=123) — and additionally confirmed `FeedTest:102` is **not** an assertion line (it's the `Feed feed = createFeed(...)` setup call), so its wrap carries no extractor risk, unlike the other four.
- All hand-conversion and mistake-injection work was done in the session scratchpad only; the repo working tree was not touched (`git status` confirms clean, matching pre-review state).

### Disposition (loop cap reached — this is loop 2 of max 2)

The finding above is real and MAJOR by the severity guide (a gap that should be fixed before merge), so the honest verdict is CHALLENGE, not APPROVE-with-MINOR-footnote. But per the instruction for this session, I am deciding disposition rather than reflexively bouncing to a third loop or a full stop-and-ask-José escalation:

**Recommendation: ACCEPT AND DOCUMENT, proceed to implementation with two concrete additions to Step 1, not a re-plan.** Reasoning:
- The underlying fix — diff assertion content instead of trusting a count — is independently verified sound. I could not produce a false negative: every real content change I injected (weakened comparison, altered expected value, dropped+compensated assertion) was caught. The gap I found only produces **false positives** (extra, spurious "hard stops" requiring a one-line justification), which is the safe failure direction for a verification tool, not the dangerous one.
- The plan's own process already has an escape valve built for exactly this shape of event: D18 explicitly defines "any other residual line is a hard stop... justified in one line as a pure syntax difference... accepted or rejected by the reviewer individually." A developer hitting a `new`-keyword or wrap-whitespace residual on, say, `FeedMediaTest` would correctly recognize it as a pure-syntax difference and write the one-line justification; the reviewer would correctly accept it. The milestone does not stall — it just does more justification bookkeeping than the plan currently advertises.
- Nothing about File Scope, Steps, Decisions, or Acceptance Criteria needs to change to fix this. `assertlines.pl` is explicitly a non-committed scratch tool (Step 1, File Scope note) — extending its canonicalization table by two rules, and adding two lines to D18's "Known limits" disclosure, is exactly the kind of refinement Step 1 already invites ("Validate it before relying on it... if any file mismatches, the extractor is wrong for this codebase and must be fixed before Step 2"). This is that validation step doing its job — I'm just supplying the mismatch it should have caught.
- Escalating to José would be the wrong shape of ask here — this isn't a pricing, scope, or business-judgment question. It's a mechanical completeness gap in a throwaway verification script, with an unambiguous, cheap fix.

**Required before the developer relies on the tool in Step 1:** add the `new`-keyword-strip and `.equals()`/`==` rules (or the equivalent policy decision to forbid the `==` idiom simplification) to `assertlines.pl`, and add both gaps plus the paren-adjacent-whitespace gap to D18's "Known limits" paragraph so Steps 6, 7, 10, and 12 aren't ambushed by unexplained residuals on `VolumeAdaptionSettingTest`, `ChapterTest`, `SubscriptionsFilterTest`, `RemoteMediaTest`, `FeedMediaTest`, and potentially `FeedPreferencesTest`. This is implementation-time tightening, not a plan defect requiring re-planning — the milestone is cleared to proceed to `android-migration-developer` on that basis.

---

## Implementation Notes
_By: android-migration-developer | 2026-07-25_

### Step 1 — Baseline capture

**a. Merge-base SHA:** `git merge-base HEAD develop` → `6b27ebd5f82a1c0583d726002a2ba439137e066d` (matches the SHA recorded in the Plan). All Step 2–14 `git show <sha>:<path>` diffs use this SHA.

**b. Per-class test counts** (`./gradlew :model:testDebugUnitTest --rerun`, BUILD SUCCESSFUL, then counted from `model/build/test-results/testDebugUnitTest/TEST-*.xml`):

| Class | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| DownloadErrorTest | 3 | 0 | 0 | 0 |
| DownloadRequestTest | 5 | 0 | 0 | 0 |
| DownloadResultTest | 11 | 0 | 0 | 0 |
| DownloadStatusTest | 3 | 0 | 0 | 0 |
| ProxyConfigTest | 4 | 0 | 0 | 0 |
| ChapterTest | 9 | 0 | 0 | 0 |
| EmbeddedChapterImageTest | 13 | 0 | 0 | 0 |
| FeedCounterTest | 3 | 0 | 0 | 0 |
| FeedFilterTest | 11 | 0 | 0 | 0 |
| FeedFundingTest | 14 | 0 | 0 | 0 |
| FeedItemFallbackLinkTest | 7 | 0 | 0 | 0 |
| FeedItemFilterTest | 20 | 0 | 0 | 0 |
| FeedItemTest | 18 | 0 | 0 | 0 |
| FeedMediaTest | 8 | 0 | 0 | 0 |
| FeedOrderTest | 3 | 0 | 0 | 0 |
| FeedPreferencesTest | 16 | 0 | 0 | 0 |
| FeedTest | 18 | 0 | 0 | 0 |
| SubscriptionsFilterTest | 10 | 0 | 0 | 0 |
| TranscriptSegmentTest | 5 | 0 | 0 | 0 |
| TranscriptTest | 8 | 0 | 0 | 0 |
| TranscriptTypeTest | 5 | 0 | 0 | 0 |
| MediaTypeTest | 8 | 0 | 0 | 0 |
| RemoteMediaTest | 3 | 0 | 0 | 0 |
| TimerValueTest | 2 | 0 | 0 | 0 |
| SortOrderTest | 11 | 0 | 0 | 0 |
| VolumeAdaptionSettingTest | 14 | 0 | 0 | 0 |
| **Total (26 classes)** | **232** | **0** | **0** | **0** |

Matches Research/AC2 table exactly, row for row.

**c. Per-file assertion-call counts** (`grep -cE "\b(assert[A-Za-z]*|verify)\(" <file>`, all 29 files):

`DownloadErrorTest`=26, `DownloadRequestTest`=26, `DownloadResultTest`=31, `DownloadStatusTest`=7, `ProxyConfigTest`=13, `ChapterTest`=9, `EmbeddedChapterImageTest`=22, `FeedCounterTest`=12, `FeedFilterTest`=43, `FeedFundingTest`=28, `FeedItemFallbackLinkTest`=1, `FeedItemFilterTest`=66, `FeedItemMother`=0, `FeedItemTest`=26, `FeedMediaMother`=0, `FeedMediaTest`=28, `FeedMother`=0, `FeedOrderTest`=10, `FeedPreferencesTest`=58, `FeedTest`=21, `SubscriptionsFilterTest`=29, `TranscriptSegmentTest`=11, `TranscriptTest`=17, `TranscriptTypeTest`=16, `MediaTypeTest`=8, `RemoteMediaTest`=20, `TimerValueTest`=4, `SortOrderTest`=14, `VolumeAdaptionSettingTest`=17.

Matches Plan's AC3 table exactly, all 29 files.

**D18 extractor (`assertlines.pl`) — written to session scratchpad (not committed), including both red-team-loop-2 fixes (`new`-keyword strip, paren-adjacent whitespace strip; `.equals()` deliberately left un-canonicalized per D18 policy).** Validated against all 29 live Java files: per-file extractor line count equals the AC3 grep count for **29/29 files, 563 total assertions**, zero mismatches — reproducing the Plan's own validation claim independently in this session. Table (file: grep-count == extractor-count): all 29 pairs equal (see raw output; identical to the counts in (c) above, since none of the 29 Java files contain a wrapped assertion call whose grep-line-count differs from its logical-assertion count). Extractor path: `$SCRATCH/assertlines.pl`; invocation per Step 1's `diff <(git show <sha>:<path> | perl assertlines.pl) <(perl assertlines.pl < <kotlin-path>)`.

### Step 2 — Convert the 3 `Mother` helpers

`FeedMother.java`, `FeedItemMother.java`, `FeedMediaMother.java` → `.kt` (via `git mv` + rewrite), as public `object` + `@JvmStatic` fun + `const val`, per D1. `FeedItemMother` calls `FeedMother.anyFeed()` qualified. No consumer file (`*Test.java`) touched — confirmed via `git status --short`, diff is exactly the 3 Mother files.

`./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 232 tests / 0 failures / 0 errors / 0 skipped, 26 classes — all 26 Java test classes still compile and pass calling the 3 new Kotlin `object`s via their existing `import static` / qualified-call sites, unchanged.

D18 assertion-content diff (all three files have 0 assertions, per Step 1c): `FeedMother` empty, `FeedItemMother` empty, `FeedMediaMother` empty.

### Step 3 — Convert the `model.download` package (5 files, 26 tests)

`DownloadErrorTest`, `DownloadRequestTest`, `DownloadResultTest`, `DownloadStatusTest`, `ProxyConfigTest` → `.kt`. `@RunWith(RobolectricTestRunner::class)` preserved on `DownloadRequestTest` (D11). All bean getters kept as explicit `.getX()`/`.isX()` function calls (not property syntax) because the corresponding Kotlin production types (`DownloadError`, `DownloadResult`, `DownloadStatus`) declare these as real functions, not Kotlin properties — only `DownloadRequest` (a genuine constructor-property class) and `ProxyConfig` (`@JvmField val`s) got real property-syntax conversions (`.destination`, `.progressPercent = 50`, `.type`, etc.).

`./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 232/0/0/0. Per-class: `DownloadErrorTest`=3, `DownloadRequestTest`=5, `DownloadResultTest`=11, `DownloadStatusTest`=3, `ProxyConfigTest`=4 — all match baseline.

D18 assertion-content diffs: `DownloadErrorTest` empty, `DownloadResultTest` empty, `DownloadStatusTest` empty, `ProxyConfigTest` empty. `DownloadRequestTest` initially showed a 5-line undisclosed residual from `fromParcel.get(i)` → `fromParcel[i]` (Kotlin index-operator idiom, not in the canonicalization table) — resolved per AGENTS.md's minimal-diff/no-optimization rule by keeping `.get(i)` verbatim instead of the idiomatic `[i]`; re-diffed and now empty.

**Deviation (disclosed):** `Bundle.getParcelableArrayList("requests")` resolves to a nullable `ArrayList<DownloadRequest>?` return type from Kotlin (unlike Java's raw/unchecked usage), and Kotlin also requires an explicit `<DownloadRequest>` type argument the Java call site didn't need. Fixed with `bundleOut.getParcelableArrayList<DownloadRequest>("requests")!!` plus a one-line comment justifying the `!!` (the bundle was populated by `putParcelableArrayList` immediately above in the same test, so the key is always present). This is a J2K-interop necessity, not a behavior change — Java's implicit trust in the same non-null result is now made explicit.

### Step 4 — Convert the `model.playback` package (2 files, 5 tests)

`RemoteMediaTest` (preserved `@RunWith(RobolectricTestRunner::class)`), `TimerValueTest` → `.kt`. `RemoteMedia`'s real Kotlin properties (`downloadUrl`, `feedUrl`, `feedTitle`, `episodeTitle`, `episodeLink`, `feedAuthor`, `imageUrl`, `feedLink`, `mimeType`, `pubDate`, `notes`, `chapters`, `duration`, `position`, `lastPlayedTimeStatistics`) converted to property syntax; `getEpisodeIdentifier()` kept as an explicit function call (backed by a private constructor val with a manually-declared getter, not an auto property). `TimerValue`'s `getDisplayValue()`/`getMillisValue()` kept as explicit function calls (real Kotlin functions, not properties).

`./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 232/0/0/0. Per-class: `RemoteMediaTest`=3, `TimerValueTest`=2 — both match baseline.

D18 assertion-content diffs: `RemoteMediaTest` empty (including the `new Date(0)` → `Date(0)` site, confirming the loop-2 `new`-keyword-strip fix works as intended), `TimerValueTest` empty.

### Step 5 — Convert `MediaTypeTest` and `SortOrderTest` (2 files, 19 tests)

Root `model` package, no traps beyond `assertThrows` lambdas. `SortOrder.code` (`@JvmField val`) kept as direct field access, unchanged.

`./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 232/0/0/0. Per-class: `MediaTypeTest`=8, `SortOrderTest`=11 — both match baseline.

D18 assertion-content diffs: `MediaTypeTest` empty, `SortOrderTest` empty.

### Step 6 — Convert `VolumeAdaptionSettingTest` (1 file, 14 tests)

Applied D3 (all 12 `` `is`(equalTo(x)) `` backticks, hamcrest imports unchanged including `` import org.hamcrest.CoreMatchers.`is` ``), D17 (`@Test(expected = IllegalArgumentException::class)` kept at both sites), D15 (`@Before`/`@After` boost-supported reset preserved verbatim, `throws Exception` dropped). Wrapped the 128-char line at `:100` at the comma boundary only (`assertTrue("...",` / `    lightReductionFactor > heavyReductionFactor)`), avoiding the paren-adjacent-whitespace artifact red-team loop 2 flagged. `getAdaptionFactor()`/`toInteger()` kept as explicit function calls (real Kotlin functions on the enum, not properties).

`./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 232/0/0/0. `VolumeAdaptionSettingTest`=14, matches baseline.

D18 assertion-content diff: empty. Assertion count re-verified at 17 (matches AC3/Step1c baseline exactly).

### Step 7 — Convert the 7 trap-free `model.feed` tests (44 tests)

`ChapterTest`, `FeedCounterTest`, `FeedOrderTest`, `TranscriptTypeTest`, `TranscriptSegmentTest`, `TranscriptTest`, `FeedFilterTest` → `.kt`. `FeedFilterTest` switches its qualified `FeedMediaMother.anyFeedMedia()` calls to the Kotlin object — textually identical, no change needed. All bean getters on `Chapter`/`TranscriptSegment`/`Transcript`/`FeedFilter` kept as explicit `.getX()` calls (real Kotlin functions, not properties); `FeedItem.title`/`.media`, `FeedMedia.duration`, `Chapter.id`, `FeedCounter.id`, `FeedOrder.id`, `TranscriptType.priority`/`.canonicalMime` used as real property syntax.

`./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 232/0/0/0. Per-class: `ChapterTest`=9, `FeedCounterTest`=3, `FeedOrderTest`=3, `TranscriptTypeTest`=5, `TranscriptSegmentTest`=5, `TranscriptTest`=8, `FeedFilterTest`=11 — all match baseline.

D18 assertion-content diffs: `FeedCounterTest`, `FeedOrderTest`, `TranscriptTypeTest`, `TranscriptSegmentTest`, `TranscriptTest`, `FeedFilterTest` all empty. `ChapterTest` showed one undisclosed residual:

```
< assertEquals(-1, Chapter.getAfterPosition(new ArrayList<>(), 5000))
> assertEquals(-1, Chapter.getAfterPosition(ArrayList<Chapter>(), 5000))
```

**Justification (accepted, not a value change):** pure syntax difference between Java's diamond-operator empty-collection literal (`new ArrayList<>()`) and Kotlin's mandatory explicit-type-argument constructor call (`ArrayList<Chapter>()`) — the extractor's `new`-strip rule only matches `new X(` with no generic-argument segment in between, so a diamond-operator `new X<>()` site isn't normalized by it. No expected-value literal, argument order, or assertion function changed; both sides construct an empty list passed as the same second positional argument (`5000`). Not folded into the shared scratch extractor (kept the tool's behavior stable rather than risk re-auditing already-passed files); disclosed here per D18's per-line-justification path instead.

### Step 8 — Convert the `Serializable` round-trip cluster (3 files, 50 tests)

`FeedFundingTest`, `FeedItemTest`, `FeedTest` → `.kt`. All 8 `.use { }` conversions (D6) applied; the `in` locals renamed to `input`; `out` left as `out`. Carried over the `pubDateField` disclosure comment verbatim above `FeedItemTest`'s int→Long assertion. Preserved Java's static-import call style for the Mother helpers via Kotlin's object-member import (`import ...FeedItemMother.anyFeedItemWithImage`, `import ...FeedMother.anyFeed`, `import ...FeedMother.IMAGE_URL`) rather than switching to qualified calls, matching the original Java call-site text exactly and avoiding an avoidable residual. `FeedItem`/`Feed`'s `original`/`changedFeedItem`/`changedFeed` locals converted to `lateinit var` (cleaner than nullable + `!!`, matches the `@Before`-initialization contract exactly). 2 of the 3 D7 int→Long fixes applied: `42L` (`FeedItemTest`), `99L` (`FeedTest`). Wrapped `FeedTest`'s 123-char line at `:102` — confirmed by red-team loop 2 to be a non-assertion setup line, so it carries no extractor risk regardless of wrap style; wrapped at a comma boundary anyway for consistency with the other D8 wraps.

`./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 232/0/0/0. Per-class: `FeedFundingTest`=14, `FeedItemTest`=18, `FeedTest`=18 — all match baseline.

**D18 extractor tightened (disclosed, per red-team loop 2's own precedent for this exact class of gap):** added one more canonicalization rule — `.isXxx()` → `.isXxx` for Kotlin's `is`-prefixed boolean-property JVM-getter convention (a genuine Kotlin property like `var isPlayed: Boolean` compiles to a JVM getter literally named `isPlayed()`, callable from Java as `.isPlayed()` but only as bare property syntax `.isPlayed` from Kotlin — the getter-canonicalization rule only matched `get`-prefixed names, not `is`-prefixed ones). Re-validated the extended extractor against all 29 live Java files at the merge-base SHA: still **29/29 line-count match against AC3's grep table, 563 total assertions**, zero change to the self-consistency baseline reported in Step 1. Re-ran the D18 diff against **all previously-converted files (Steps 2–7)**: no regressions, no new residuals — `ChapterTest`'s already-disclosed diamond-operator residual is unchanged.

D18 assertion-content diffs:
- `FeedFundingTest`: empty.
- `FeedTest`: one line, the disclosed D7 fix (`assertEquals(99, deserialized.id)` → `assertEquals(99L, deserialized.id)`) — matches the Plan's prediction exactly.
- `FeedItemTest`: **six residual lines**, more than the Plan's "exactly one" prediction for this file. All individually justified, none altering an expected-value literal, argument order, or assertion function:
  1. Disclosed D7 fix: `assertEquals(42, deserialized.id)` → `assertEquals(42L, deserialized.id)`.
  2. `assertEquals(changedDate.time, original.pubDate.time)` → `...original.pubDate!!.time)` (×1) and `assertEquals(123456789L, item.pubDate.time)` → `...item.pubDate!!.time)` (×2) — `FeedItem.getPubDate()` returns nullable `Date?`; Java's chained `.getPubDate().getTime()` implicitly trusted non-null (pubDate is always set earlier in the same test). Kotlin requires an explicit `!!` to perform the same dereference; each site carries a code comment naming the invariant (pubDate was set on an earlier line in the same test). Pure null-safety syntax, no value change.
  3. `private void assertFeedItemImageWasUpdated() {` / `private void assertFeedItemImageWasNotUpdated() {` → `private fun ...` (×2) — an extractor false-positive, not a real assertion: these are the file's own private helper methods, whose names happen to start with "assert" (`assertFeedItemImageWasUpdated`, `assertFeedItemImageWasNotUpdated`), so the extractor's `assert[A-Za-z]*\(` regex matches the method **declaration** line, not a JUnit call. Confirmed via repo-wide search that no other of the 29 files has a private helper method named this way (this pattern is unique to `FeedItemTest`), so this is not extended into the shared extractor as a general rule (would require distinguishing declarations from calls, a bigger and riskier change for a one-file, symmetric artifact) — disclosed here instead. The only textual difference is the expected `void`→`fun` return-type keyword, which is a mandatory, inherent Java→Kotlin syntax difference on every converted method in every file, carrying no assertion content whatsoever.

### Step 9 — Convert `FeedItemFallbackLinkTest` (1 file, 7 tests)

Applied D12 exactly: `@JvmStatic` + `@Parameterized.Parameters` on the companion `data()`, `arrayOf<Any?>` rows (required since 4 of the 7 rows carry `null`), constructor `val`s, `FEED_LINK`/`ITEM_LINK` as `const val` in the same companion.

`./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 232/0/0/0. `FeedItemFallbackLinkTest`=**7** (not 0, not 1 — confirms `@JvmStatic` made the parameter provider discoverable).

D18 assertion-content diff: empty.

### Step 10 — Convert the `mockStatic(TextUtils)` pair (2 files, 26 tests)

`FeedPreferencesTest`, `SubscriptionsFilterTest` → `.kt`. Both remaining `.use { }` conversions applied. The third D7 int→Long fix applied: `1L` at `FeedPreferencesTest`. The `` .`when` `` backticks applied at all 3 sites (D4). `MockedStatic.close()` teardowns preserved (D15). The 121/123-char line wraps at `FeedPreferencesTest:116,117` wrapped at the comma boundary only, `(`/`)` glued to adjacent tokens per the D8/red-team-loop-2 guidance — no paren-adjacent-whitespace artifact reproduced. `FeedPreferences.getFeedID()`/`getTagsAsString()`/etc. and `SubscriptionsFilter.isEnabled()`/`getValues()`/`serialize()` kept as explicit function calls (real Kotlin functions, not properties); `SubscriptionsFilter`'s `@JvmField val` boolean flags (`showIfCounterGreaterZero`, `hideNonSubscribedFeeds`, etc.) kept as direct field access.

**Deviation (disclosed) — D14's literal cast form does not work at runtime; fixed with an explicit generic type argument instead.** `TextUtils.join(any(), (Iterable<?>) any())` per D14 was to become `TextUtils.join(any(), any() as Iterable<*>)`. Implemented literally first — all 16 `FeedPreferencesTest` tests failed in `@Before` with `org.mockito.exceptions.misusing.InvalidUseOfMatchersException: Misplaced or misused argument matcher`, reproduced both in the full suite and in isolation (`--tests FeedPreferencesTest`). Root cause: casting the untyped `any()` call via a trailing `as Iterable<*>` does not reliably preserve Mockito's argument-matcher stack ordering for this overload combination the way the same shape does for `SubscriptionsFilterTest`'s `any() as Array<Any?>` (which passed unmodified). Fixed by giving `any()` an explicit generic type argument instead of a post-hoc cast — `any<Iterable<*>>()` — which achieves the identical goal D14 named (disambiguating `TextUtils.join`'s `Iterable` vs `Object[]` overloads) without the runtime failure. `SubscriptionsFilterTest`'s `(Object[]) any()` → `any() as Array<Any?>` cast form, called for by D14, worked as prescribed and needed no change. Verified: all 16 `FeedPreferencesTest` tests pass with this fix, both in isolation and in the full 232-test run.

`./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 232/0/0/0. Per-class: `FeedPreferencesTest`=16, `SubscriptionsFilterTest`=10 — both match baseline. Assertion counts re-verified: `FeedPreferencesTest`=58, `SubscriptionsFilterTest`=29 (both match Step 1c baseline).

D18 assertion-content diffs:
- `FeedPreferencesTest`: **one line**, the disclosed D7 fix — `assertEquals(1, prefs.feedID)` → `assertEquals(1L, prefs.feedID)`, matching the Plan's "exactly one line" prediction exactly. (Note: the extractor's canonical form shows `prefs.feedID` even though the real Kotlin source calls the explicit function `prefs.getFeedID()` — `getFeedID()` is a manually-declared function in `FeedPreferences.kt`, not an auto-property, so `prefs.feedID` is not itself valid Kotlin here; this is purely the extractor's getter→property canonicalization applied to both sides for comparison purposes and does not reflect the actual call syntax. Minor correction to the Plan's D7 illustrative table, which showed `prefs.feedID` as the "required Kotlin" — the required *behavior* is the `1L` fix; the actual required syntax is `prefs.getFeedID()`, which is what was implemented.)
- `SubscriptionsFilterTest`: **one undisclosed-then-disclosed residual**:
  ```
  < assertArrayEquals(new String[]{SubscriptionsFilter.COUNTER_GREATER_ZERO}, filter.values)
  > assertArrayEquals(arrayOf(SubscriptionsFilter.COUNTER_GREATER_ZERO), filter.values)
  ```
  **Justification (accepted, not a value change):** pure syntax difference between Java's array-literal initializer (`new String[]{...}`) and Kotlin's `arrayOf(...)` factory function — Kotlin has no array-literal syntax. The extractor's `new`-strip rule only matches `new X(` constructor calls, not `new X[]{...}` array literals, so this is a distinct, narrower gap from the `ChapterTest`/diamond-operator one (Step 7). Confirmed via repo-wide search this is the only site across all 29 files with this shape, so — consistent with the Step 7 precedent — not folded into the shared extractor; disclosed here instead. No expected-value literal, argument order, or assertion function changed.

### Step 11 — Convert the exception-message-pinning pair (2 files, 33 tests)

`EmbeddedChapterImageTest`, `FeedItemFilterTest` → `.kt`. D9 applied in full to the 3 pinning tests: each `assertThrows` lambda body is a single call expression (`{ EmbeddedChapterImage.getModelFor(media, 0) }`, `{ filter.matches(item) }`) with no wrapper/helper/intermediate local; `assertNull`/`assertNotNull`(`.message`)/`stackTrace[0].className.startsWith(...)` converted 1:1; both class-name string literals (`"de.danoeh.antennapod.model.feed.EmbeddedChapterImage"`, `"java.util.regex"`) preserved character-for-character; all explanatory comments carried over verbatim. The 5 `` `when` `` backticks applied (`EmbeddedChapterImageTest` — 4 `MockedStatic.when` instance-call sites plus needed a `` import org.mockito.Mockito.`when` `` for the 3 plain `Mockito.when(media.chapters)` stub sites). The 131-char wrap at `EmbeddedChapterImageTest:167` wrapped inside the `assertTrue(...)` call (single boolean argument, no comma to break at) rather than at a comma — safe because the extractor's paren-adjacent whitespace stripping (already in the Step 1 script) neutralizes the artifact regardless of wrap shape; confirmed empty-diff-compatible below. `EmbeddedChapterImageTest` does **not** gain Robolectric (D11) — confirmed no `Robolectric` string appears in the file. `FeedItemFilterTest`'s qualified `FeedMother.anyFeed()` calls needed no import-style change (already qualified in the Java original, unlike `FeedItemTest`/`FeedTest`'s static imports).

`./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 232/0/0/0. Per-class: `EmbeddedChapterImageTest`=13, `FeedItemFilterTest`=20 — both match baseline.

**Deviation (disclosed) — Mockito stubbing shape adjusted for `FeedPreferencesTest`-class reasons doesn't recur here, but two new one-off extractor gaps surfaced, both confined to a single file each (verified by repo-wide search, including the not-yet-converted `FeedMediaTest`):**

D18 assertion-content diffs:
- `EmbeddedChapterImageTest`: **3 residual lines**, confined to `getModelForReturnsInstanceForEmbeddedUrl` (not one of the 3 D9 pinning tests):
  ```
  < assertTrue(model instanceof EmbeddedChapterImage)
  < assertEquals(3, ((EmbeddedChapterImage) model).position)
  < assertEquals(100, ((EmbeddedChapterImage) model).length)
  ---
  > assertTrue(model is EmbeddedChapterImage)
  > assertEquals(3, (model as EmbeddedChapterImage).position)
  > assertEquals(100, (model as EmbeddedChapterImage).length)
  ```
  **Justification (accepted, not a value change):** Java's `instanceof`/cast syntax has no extractor canonicalization rule (neither `instanceof` → `is` nor `((X) y)` → `(y as X)` is in the D18 table). Confirmed via repo-wide search this is the *only* site across all 29 files using `instanceof`/cast inside an assertion — not extended into the shared extractor for a single-file, symmetric artifact (same reasoning as the Step 7/10 precedents). No expected value, argument order, or assertion function changed — `model instanceof X` and `model is X` test the identical runtime condition; `((X) y).field` and `(y as X).field` perform the identical runtime cast and field read.
- `FeedItemFilterTest`: **3 residual lines**, all in the same shape:
  ```
  < assertEquals(1, copy.values.length)
  ---
  > assertEquals(1, copy.values.size)
  ```
  (×3, at `testConstructorFromFilterWithZeroAdditionalPropertiesYieldsNoTrailingEmptyEntry` and `testUnfiltered`/`testEmptyStringYieldsZeroLengthProperties`). **Justification (accepted, not a value change):** `FeedItemFilter.getValues()` returns a Kotlin `Array<String>`, whose size accessor is `.size` — Kotlin arrays have no `.length`. Java's bare `array.length` field access (no parentheses) is untouched by the extractor's existing `.length()`/`.size()` *method-call* canonicalization rule (which only matches parenthesized calls), so it survives to the diff unchanged, while the Kotlin side correctly uses `.size`. Confirmed via repo-wide search this exact shape (bare `.length` on an array-typed expression inside an assertion) occurs only in this file — not extended into the shared extractor (would risk touching Kotlin's *own* `.length` on `String`/`CharSequence`, which is correct on both sides and must not be conflated with the array case). No expected value or argument order changed.

Neither gap recurs in `FeedMediaTest` (Step 12, confirmed by the same repo-wide search before finalizing this step), so neither needed a shared-extractor decision under time pressure.

### Step 12 — Convert `FeedMediaTest` (1 file, 8 tests) — last file

Applied all 4 stacked hazards: preserved `@RunWith(RobolectricTestRunner::class)`; Mockito mocking the Kotlin final class `FeedItem` unaffected by the conversion (mockito-core 5.x inline mock maker, unchanged); the 6 `` `when` `` backticks plus `` import org.mockito.Mockito.`when` ``; the reflection literal `"hasEmbeddedPictureField"` preserved character-for-character, `FeedMedia.class` → `FeedMedia::class.java`. `FeedItem.isNew`/`.isPlayed` stubbed and verified via Kotlin property syntax (`` `when`(item.isNew).thenReturn(...) ``, `verify(item, never()).isPlayed = true`), since Mockito's mock-and-verify pattern on a Kotlin property setter is `verify(mock).property = value` (property assignment, not a `.setProperty(value)` function call). `FeedItem.setNew()` — a real explicit function, not a property — kept as `.setNew()` verbatim on both sides. Per AGENTS.md's "never reference a full package name, use imports" rule, `android.net.Uri.parse(...)` converted to an `import android.net.Uri` + `Uri.parse(...)`.

`./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 232/0/0/0. `FeedMediaTest`=8, matches baseline. `find model/src/test -name '*.java' | wc -l` → **0** — the entire test source set is now Kotlin.

D18 assertion-content diff: **8 residual lines**, two distinct categories, both confined to this file (no more files remain to check for recurrence — this is the last of the 29):

1. **6 lines — the setter-side counterpart of the Step 8 `is`-prefixed boolean gap.** The extractor's bean-setter canonicalization (`.setX(v)` → `.x = v`) assumes the Kotlin property name is the lowercased tail of the Java setter name, which holds for ordinary setters but not for `is`-prefixed booleans: `FeedItem.isPlayed` is the real property name (Kotlin keeps the full `isPlayed`, it does not strip "is" as it would for a `get`/`set` pair), so `.setPlayed(v)` canonicalizes to `.played = v` on the Java side but the actual, correct Kotlin call is `.isPlayed = v` (`.played` is not a real property and would not compile). Same root cause as `is`-prefixed getters (Step 8), just on the setter/verify side:
   ```
   < verify(item, never()).played = true       > verify(item, never()).isPlayed = true
   < verify(item, never()).played = false       > verify(item, never()).isPlayed = false
   < verify(item).played = false                > verify(item).isPlayed = false
   ```
   (×2 duplicated across the first three test methods, 6 lines total.) **Justification (accepted, not a value change):** `.isPlayed = v` and the extractor's canonical `.played = v` both denote the identical `FeedItem.isPlayed` property assignment; not folded into the shared extractor since this is the last of the 29 files — there is no future file left where extending the tool would pay for itself, and re-validating it against all 12 already-completed steps for a fix with no remaining beneficiary is not worth the risk of an incidental regression this late.
2. **2 lines — fully-qualified-name-to-import conversion**, mandated by `AGENTS.md` ("Never reference the full package name of classes directly in the code, use imports"), not a Plan decision:
   ```
   < assertEquals(android.net.Uri.parse(...), ...)      > assertEquals(Uri.parse(...), ...)
   ```
   (×2, both `getMediaItemMapsFieldsAndIconUri` icon-URI assertions.) **Justification (accepted, not a value change):** identical class (`android.net.Uri`) and identical static method call; only the reference form changed, per the repo's own mandatory style rule. Not added to the extractor as a general FQN-to-simple-name rule — too broad a transformation to canonicalize safely (risks masking an actual wrong-class substitution elsewhere).

### Step 13 — Revert-and-reverify the 3 pinning tests (no committed diff)

Confirmed `git status --short model/src/main/` was clean before starting and after each revert.

**1. `EmbeddedChapterImageTest.getModelForNullChaptersThrowsNpe`.** Mutated `EmbeddedChapterImage.getModelFor` (`model/src/main/java/de/danoeh/antennapod/model/feed/EmbeddedChapterImage.kt`) from:
```kotlin
val imageUrl = media.chapters!![chapter].imageUrl
```
to:
```kotlin
val chapters = java.util.Objects.requireNonNull(media.chapters) as List<Chapter>
val imageUrl = chapters[chapter].imageUrl
```
This moves the null-check to `java.util.Objects.requireNonNull`, changing the NPE's throw site from inside `EmbeddedChapterImage` to `java.util.Objects`. Ran `./gradlew :model:testDebugUnitTest --tests "...getModelForNullChaptersThrowsNpe" --rerun`: **FAILED**, `java.lang.AssertionError` at `EmbeddedChapterImageTest.kt:161` (the `assertTrue(exception.stackTrace[0].className.startsWith("de.danoeh.antennapod.model.feed.EmbeddedChapterImage"))` line) — exactly the frame-0 class-name assertion the Plan named. Reverted; `git status --short model/src/main/` clean afterward.

**2. `EmbeddedChapterImageTest.getModelForNullImageUrlThrowsFromInsideMatcherNotAtExtraction`.** Mutated the same function from:
```kotlin
val imageUrl = media.chapters!![chapter].imageUrl
```
to:
```kotlin
val imageUrl = media.chapters!![chapter].imageUrl!!
```
This guards the null `imageUrl` with `!!` at extraction time, before it ever reaches `isEmbeddedChapterImage()`'s `Pattern.matcher()` call. Ran the single test: **FAILED**, `java.lang.AssertionError` at `EmbeddedChapterImageTest.kt:141` (the `assertNotNull(exception.message)` line) — Kotlin's `!!` NPE carries a null message in this toolchain/target configuration, so the test fails on the message-nullity half of the Plan's "and/or" requirement (message-not-null or java.util.regex-frame). Either failure mode satisfies the Plan; this one did. Reverted; `git status --short model/src/main/` clean afterward.

**3. `FeedItemFilterTest.testMatchesShowInHistoryWithNullLastPlayedTimeHistoryThrowsNpe`.** Mutated `FeedItemFilter.matches` (`model/src/main/java/de/danoeh/antennapod/model/feed/FeedItemFilter.kt`) from:
```kotlin
showInHistory && item.media != null &&
    item.media!!.lastPlayedTimeHistory!!.time == 0L -> return false
```
to:
```kotlin
showInHistory && item.media != null &&
    item.media?.lastPlayedTimeHistory?.time == 0L -> return false
```
This is exactly the "future `?.` rewrite" the disclosure comment above the line warns against. Ran the single test: **FAILED**, `java.lang.AssertionError: expected java.lang.NullPointerException to be thrown, but nothing was thrown` at `FeedItemFilterTest.kt:267` — the item silently passed the filter instead of throwing, precisely the regression the test exists to catch. Reverted; `git status --short model/src/main/` clean afterward.

**Final state:** `git status --short model/src/main/` clean; `./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 232/0/0/0 (re-confirmed after all three reverts).

### Step 14 — Full gate set and final reconciliation

**a. Per-class test table (26 rows)** re-extracted from `model/build/test-results/testDebugUnitTest/TEST-*.xml` after a forced `--rerun`: identical to the Step 1 baseline table row for row — 232 tests, 0 failures, 0 errors, 0 skipped, all 26 classes matching their Research/AC2 counts exactly (`FeedItemFallbackLinkTest`=7, `FeedItemFilterTest`=20, `FeedPreferencesTest`=16, `FeedItemTest`=18, `FeedTest`=18, `FeedFundingTest`=14, `VolumeAdaptionSettingTest`=14, `EmbeddedChapterImageTest`=13, `DownloadResultTest`=11, `FeedFilterTest`=11, `SortOrderTest`=11, `SubscriptionsFilterTest`=10, `ChapterTest`=9, `FeedMediaTest`=8, `MediaTypeTest`=8, `TranscriptTest`=8, `DownloadRequestTest`=5, `ProxyConfigTest`=4, `TranscriptSegmentTest`=5, `TranscriptTypeTest`=5, `DownloadErrorTest`=3, `DownloadStatusTest`=3, `FeedCounterTest`=3, `FeedOrderTest`=3, `RemoteMediaTest`=3, `TimerValueTest`=2). Diff against baseline: **none**.

**b. Per-file assertion-count table (29 rows)** re-extracted via `grep -cE "\b(assert[A-Za-z]*|verify)\(" <file>` against all 29 `.kt` files: every count identical to the Step 1c baseline (`DownloadErrorTest`=26, `DownloadRequestTest`=26, `DownloadResultTest`=31, `DownloadStatusTest`=7, `ProxyConfigTest`=13, `ChapterTest`=9, `EmbeddedChapterImageTest`=22, `FeedCounterTest`=12, `FeedFilterTest`=43, `FeedFundingTest`=28, `FeedItemFallbackLinkTest`=1, `FeedItemFilterTest`=66, `FeedItemTest`=26, `FeedMediaTest`=28, `FeedOrderTest`=10, `FeedPreferencesTest`=58, `FeedTest`=21, `SubscriptionsFilterTest`=29, `TranscriptSegmentTest`=11, `TranscriptTest`=17, `TranscriptTypeTest`=16, `MediaTypeTest`=8, `RemoteMediaTest`=20, `TimerValueTest`=4, `SortOrderTest`=14, `VolumeAdaptionSettingTest`=17, all three `Mother`s=0; total 563). Diff against baseline: **none**.

**c. Consolidated D18 sweep, re-run across all 29 files in one pass** (whole-suite re-derivation, not a restatement of per-step results):

| File | Residual lines |
|---|---|
| DownloadErrorTest | 0 |
| DownloadRequestTest | 0 |
| DownloadResultTest | 0 |
| DownloadStatusTest | 0 |
| ProxyConfigTest | 0 |
| ChapterTest | 1 |
| EmbeddedChapterImageTest | 3 |
| FeedCounterTest | 0 |
| FeedFilterTest | 0 |
| FeedFundingTest | 0 |
| FeedItemFallbackLinkTest | 0 |
| FeedItemFilterTest | 3 |
| FeedItemMother | 0 |
| FeedItemTest | 6 |
| FeedMediaMother | 0 |
| FeedMediaTest | 8 |
| FeedMother | 0 |
| FeedOrderTest | 0 |
| FeedPreferencesTest | 1 |
| FeedTest | 1 |
| SubscriptionsFilterTest | 1 |
| TranscriptSegmentTest | 0 |
| TranscriptTest | 0 |
| TranscriptTypeTest | 0 |
| MediaTypeTest | 0 |
| RemoteMediaTest | 0 |
| TimerValueTest | 0 |
| SortOrderTest | 0 |
| VolumeAdaptionSettingTest | 0 |

**Reconciliation against AC3B's binding claim ("empty for 26 of 29, exactly one line each for the other 3"):** 21 of 29 files are empty (not 26); 3 files (`FeedPreferencesTest`, `FeedTest`, plus `FeedItemTest`'s disclosed D7 line among its 6) carry exactly the disclosed D7 `L`-suffix line; **5 additional files** (`ChapterTest`, `EmbeddedChapterImageTest`, `FeedItemFilterTest`, `FeedItemTest`, `FeedMediaTest`, `SubscriptionsFilterTest` — 6 files, since `FeedItemTest` appears in both the D7 group and this group) carry residuals beyond the D7 set, all individually justified in their step's Implementation Notes as pure-syntax differences with no expected-value, argument-order, or assertion-function change:
- `ChapterTest` (1): Java diamond-operator `new ArrayList<>()` vs Kotlin's explicit `ArrayList<Chapter>()` (Step 7).
- `EmbeddedChapterImageTest` (3): `instanceof`/cast syntax (`instanceof`→`is`, `((X) y)`→`(y as X)`) has no extractor rule (Step 11).
- `FeedItemFilterTest` (3): bare Java array `.length` field access vs Kotlin `Array.size` (Step 11).
- `FeedItemTest` (6): 1 disclosed D7 line + 3 `pubDate!!` null-safety additions + 2 extractor false-positives on private helper methods named `assertFeedItemImageWas(Not)Updated` (Step 8).
- `FeedMediaTest` (8): 6 `is`-prefixed-boolean-setter canonicalization gaps (the setter-side counterpart of the Step 8 `is`-prefixed getter gap) + 2 FQN-to-import conversions mandated by `AGENTS.md` (Step 12).
- `FeedPreferencesTest` (1) and `FeedTest` (1): exactly the disclosed D7 line, as predicted.
- `SubscriptionsFilterTest` (1): Java array-literal `new String[]{...}` vs Kotlin's `arrayOf(...)` (Step 10).

This is exactly the shape red-team loop 2 anticipated in its FINAL disposition ("more justification bookkeeping than the plan currently advertises... not a plan defect... I could not produce a false negative"): every gap found across all 8 steps was a **false positive** (an extra, disclosed, pure-syntax residual), never a false negative — no residual anywhere altered an expected-value literal, swapped argument order, or changed an assertion function. The D18 extractor itself was extended once, mid-milestone (Step 8, the `is`-prefixed boolean-getter rule), re-validated against the Step 1 baseline (still 29/29, 563 total) and against all previously-completed steps (no regressions) before use.

**Toolchain gates:**
- `./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 232 tests, 0 failures, 0 errors, 0 skipped.
- `./gradlew :model:ktlintCheck` → BUILD SUCCESSFUL after running `:model:ktlintTestSourceSetFormat` once to auto-fix mechanical wrapping/import-order violations ktlint's own rules require (argument-list-wrapping once a call wraps, lexicographic import order) — re-verified afterward that (1) all 232 tests still pass, (2) the consolidated D18 sweep above is unchanged from before the format pass (re-ran it twice, before and after), and (3) zero `@Suppress("ktlint:...")` annotations, no `ktlint_disabled_rules`, no `.editorconfig` edit, no ktlint filter/exclusion were introduced — the format pass only reformatted whitespace/import order/wrapping.
- `./gradlew checkstyle lint` → `checkstyle` alone is BUILD SUCCESSFUL (confirms D19: the two orphaned suppression entries remain inert). The combined `lint` (repo-wide) task reproduces exactly the **two pre-existing disclosed failures** carried over from Milestones 4–6, nothing new: `:app-wearos:compileFreeDebugKotlin`/`compilePlayDebugKotlin` (`EpisodeDetailActivity.kt:115` nullable-String argument mismatch) and `:app:spotbugsPlayDebug` (`FeedInfoFragment.java:214` possible-null-dereference of `FeedFunding.content`). Both are outside File Scope and unrelated to this milestone's changes.
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (confirms no production `.kt` was disturbed).
- `find model/src/test -name '*.java' | wc -l` → **0**; `find model/src/test -name '*.kt' | wc -l` → **29**; `find model/src -name '*.java' | wc -l` → **0** (whole module, main+test, is now 100% Kotlin).

**File Scope verification:** `git diff --name-only <merge-base-SHA>` lists exactly the 29 renamed test files plus `tasks/antennapod-model-kotlin-future-work.md` (pre-existing, modified during the planning session before implementation began — logs future-work items #3–#5 referenced by the approved Plan's Resolutions and D19, not touched further by this developer). `model/build.gradle`, `config/checkstyle/suppressions.xml`, `.editorconfig`, and everything under `model/src/main/` all show an empty diff against the merge base. The D18 `assertlines.pl` audit script lives only in the session scratchpad and was never committed.

### Step 15 — Update `model/README.md`

Added a new paragraph documenting: (1) the test suite is now 100% Kotlin; (2) the `object` + `@JvmStatic` Mother convention and why `internal` is wrong for test helpers (JVM name-mangling silently breaks Java `import static`); (3) verbatim test-method-name preservation and the ktlint `org.junit`-import test-name exemption that permits it; (4) a restatement of the Robolectric-scope constraint (still exactly `DownloadRequestTest`/`FeedMediaTest`/`RemoteMediaTest`, regardless of a file's language). Phrased as long-term-stable module patterns with no milestone or task-specific provenance, per `AGENTS.md`. Existing README content (100%-Kotlin production-code sentence, Parcelable/Robolectric disclosure, `serialVersionUID` note) left untouched.

## Commit message

```
feat(model): kotlin migration — convert :model's 29 test files to Kotlin

Completes the kotlin track for :model: 26 *Test.java classes and 3 Mother
test-helper builders converted to Kotlin, in place. Test-only change — no
production .kt file was modified. The 232 pre-existing tests are unchanged
in count, per-class distribution, and assertion content (verified via a
per-file mechanical content diff against the Java originals at the
pre-conversion merge-base SHA), so this is a language conversion with zero
intended behavior change.

Track: kotlin. Milestone 7 of the :model kotlin-migration sequence — the
final one; :model is now 100% Kotlin in both src/main and src/test.
```

### Test commands run

- `./gradlew :model:testDebugUnitTest --rerun` — before: PASS (232/0/0/0, Step 1 baseline), after: PASS (232/0/0/0, re-confirmed at Step 14 and after Step 13's reverts)
- `./gradlew :model:ktlintCheck` — PASS (after one `ktlintTestSourceSetFormat` mechanical auto-format pass; re-verified test suite and D18 sweep unchanged afterward)
- `./gradlew checkstyle lint` — `checkstyle` alone PASS; repo-wide `lint` reproduces the 2 pre-existing disclosed failures from Milestones 4–6 (`app-wearos` compile break, `FeedInfoFragment.java` SpotBugs), nothing new
- `./gradlew :app:assembleDebug` — PASS
- `find model/src/test -name '*.java' | wc -l` → 0; `find model/src/test -name '*.kt' | wc -l` → 29; `find model/src -name '*.java' | wc -l` → 0

### Characterization test results

Per-class results are the same 26 rows reported in Steps 1–14 (all match baseline exactly; see the per-step sections above for each class's before/after pass status and the D18 content-diff evidence). The three named exception-message-pinning tests, specifically:

| Test | Before | After | What it exercises |
|---|---|---|---|
| `EmbeddedChapterImageTest.getModelForNullChaptersThrowsNpe` | PASS | PASS | `EmbeddedChapterImage.getModelFor` still throws `NullPointerException` with a null message, from a stack frame whose class name starts with `EmbeddedChapterImage`, when `Playable.chapters` is null — re-proven by Step 13's revert-and-reverify (mutation broke the frame-0 check specifically) |
| `EmbeddedChapterImageTest.getModelForNullImageUrlThrowsFromInsideMatcherNotAtExtraction` | PASS | PASS | The same function still throws from inside `java.util.regex` (not at Kotlin `!!` extraction time) with a non-null JEP-358 message when `imageUrl` is null — re-proven by Step 13 (mutation broke the message-nullity check) |
| `FeedItemFilterTest.testMatchesShowInHistoryWithNullLastPlayedTimeHistoryThrowsNpe` | PASS | PASS | `FeedItemFilter.matches` still throws `NullPointerException` (not silently `false`) when a history-filtered item's `lastPlayedTimeHistory` is null — re-proven by Step 13 (mutation made the test fail with "nothing was thrown") |

A green run alone was not treated as sufficient for these three; Step 13's temporary production mutations (each reverted, `git status` confirmed clean) are the actual evidence these tests still discriminate the exact regressions they were written to catch.

### Deviations from plan

1. **D14's literal cast form (`any() as Iterable<*>`) does not work at runtime for `FeedPreferencesTest`'s `TextUtils.join` stub — fixed with an explicit generic type argument instead (`any<Iterable<*>>()`).** All 16 tests failed in `@Before` with `InvalidUseOfMatchersException` when implemented literally per D14; root-caused and fixed as described in Step 10. `SubscriptionsFilterTest`'s analogous `any() as Array<Any?>` cast, also called for by D14, worked as prescribed and needed no change.
2. **`Bundle.getParcelableArrayList`/`SimpleDateFormat.parse` J2K-interop nullability fixes** (Steps 3, 8) — Kotlin's stricter nullability on Android/JDK platform-type returns required `!!` at a small number of sites where Java's code implicitly trusted non-null; each is disclosed with a code comment naming the invariant that makes it safe, per house style.
3. **D18 extractor tightened once, mid-milestone** (Step 8) — added an `is`-prefixed-boolean-property canonicalization rule, re-validated against the Step 1 baseline and all prior steps before relying on it further; documented in Step 8 and Step 14c.
4. **21 additional D18 residuals beyond the 3 disclosed D7 lines**, spread across 6 files (`ChapterTest`, `EmbeddedChapterImageTest`, `FeedItemFilterTest`, `FeedItemTest`, `FeedMediaTest`, `SubscriptionsFilterTest` — Steps 7, 8, 10, 11, 12) — each individually justified as a pure-syntax gap in the (intentionally non-committed) audit tool's canonicalization table, never a value/argument/assertion-function change. Full accounting in Step 14c above. This is the outcome red-team loop 2 explicitly anticipated and pre-approved handling for via disclosure rather than a plan revision.
5. **Minor correction to D7's illustrative Kotlin form for `FeedPreferencesTest`** (Step 10) — the Plan's table showed `prefs.feedID` (property syntax) as the "required Kotlin," but `FeedPreferences.getFeedID()` is a manually-declared function in the actual production code, not an auto-property; the implemented (and only compilable) form is `prefs.getFeedID()`, preserving D7's substantive requirement (the `1L` fix) exactly.
6. **`tasks/antennapod-model-kotlin-future-work.md`** was already modified (future-work items #3–#5 added) before this developer's session began, during the planning/red-team phase that produced the approved Plan. Not further modified here; flagged in Step 14's File Scope verification for transparency since it technically sits outside the Plan's literal File Scope list but was pre-existing, not introduced by this implementation.
7. **Two `String.split` → `Pattern.compile(...).split(...)` rewrites inside hand-written stub/setup bodies, undisclosed until now (flagged by red-team loop 1, MAJOR):**
   - `SubscriptionsFilterTest.kt:36`, inside the D14-governed `TextUtils.split` `thenAnswer` body — Java's `return text.split(expression, -1);` was rewritten to `Pattern.compile(expression).split(text, -1)`.
   - `FeedPreferencesTest.kt:275`, inside the `getTagsAsStringJoinsWithSeparator` test method body (not a D14 `thenAnswer`, but the same hazard class) — Java's `result.split(FeedPreferences.TAG_SEPARATOR)` was rewritten to `Pattern.compile(FeedPreferences.TAG_SEPARATOR).split(result)`.

   **Why this form and not Kotlin's `.split()`:** both rewrites deliberately follow the Milestone-4 `FeedFunding.kt:54-60` precedent, which established that Kotlin's `CharSequence.split(String)`/`.split(Regex)` do **not** strip trailing empty tokens the way Java's `String.split(String)` (single-arg, or `limit <= 0`) does — silently changing output for any input ending in the separator. `Pattern.compile(...).split(...)` reproduces Java's trailing-empty-token-stripping behavior exactly, avoiding that trap.

   **Why this is behaviorally identical to the Java original, not just a same-output workaround:** Java's `String.split(String regex, int limit)` is documented, and implemented in the JDK, to delegate directly to `Pattern.compile(regex).split(this, limit)` (`java.lang.String#split` javadoc; the only exception is an internal fast-path for a single-character, non-regex-metacharacter separator, which is documented to produce output identical to the general `Pattern`-based path — it is a performance optimization, not a semantic difference). `Pattern.compile(expression).split(text, -1)` and `text.split(expression, -1)` are therefore the same method call one layer apart, not two different algorithms. Likewise `result.split(FeedPreferences.TAG_SEPARATOR)` (single-arg Java overload, implicit `limit = 0`) is defined as `split(regex, 0)`, which `Pattern.compile(TAG_SEPARATOR).split(result)` (Pattern's own zero-limit default) reproduces exactly.

   **Disclosure gap this closes:** neither rewrite changes an assertion, so D18/AC3B's assert-line-only audit never saw either one — this entry is what establishes the equivalence claim as checked by this milestone's own record, not left to an outside auditor's after-the-fact verification (per red-team loop 1's MAJOR finding and disposition).

No production `.kt` file needed to change (confirmed: `git diff --stat <merge-base> -- model/src/main` is empty), and no genuine J2K-interop issue forced a production-code fix — matching the Plan's expectation for a test-only milestone.

### Fix pass (3 MINOR findings)
_By: android-migration-developer | 2026-07-25_

Addressed the 3 MINOR findings from Code Review loop 1 (see `## Code Review Verdict` below). All three are documentation/comment corrections; no test file's behavior or assertions changed.

1. **File Scope omission for `tasks/antennapod-model-kotlin-future-work.md`.** Added a one-line entry for this file to the `## Plan`'s `### File Scope` Docs list (it was already a disclosed, legitimate pre-implementation edit per Deviation 6 above, just missing from the literal scope list). No content in `future-work.md` itself was touched.
2. **D18 residual miscount in Deviations item 4.** The summary previously read "8 additional D18 residuals... spread across 5 files." Recomputed directly from the Step 14c table: total residual lines across all 29 files = 24; the 3 D7-disclosed lines (`FeedPreferencesTest`=1, `FeedTest`=1, one of `FeedItemTest`'s 6) subtract out to leave **21 residuals beyond D7, across 6 files** (`ChapterTest`=1, `EmbeddedChapterImageTest`=3, `FeedItemFilterTest`=3, `FeedItemTest`=5 non-D7 of its 6, `FeedMediaTest`=8, `SubscriptionsFilterTest`=1 — sums to 21). Corrected Deviations item 4 to match the Step 14c table exactly.
3. **Missing justification comment on `FeedTest.kt`'s `feed.getPaymentLinks()!!`.** `getPaymentLinks()` (`Feed.kt:335`) returns exactly what `FeedFunding.extractPaymentLinks(paymentLinks)` (`Feed.kt:155`) produced at construction time. `FeedFundingTest.kt`'s sibling `!!` sites (e.g. `extractPaymentLinksOldFormatSingleLink`, line 25) each carry a two-line comment: "extractPaymentLinks only returns null for a blank input (checked above); this literal is non-blank, so the result is always non-null here." Added the matching comment above the `feed.getPaymentLinks()!!` call (originally `FeedTest.kt:157`, now at line 160 after the two-line comment insertion), naming the same invariant applied to the test's own non-blank literal (`"http://example.com/pay"`).

Re-ran `./gradlew :model:testDebugUnitTest --rerun` after all three fixes: BUILD SUCCESSFUL, 232 tests, 0 failures, 0 errors, 0 skipped (re-verified via the per-class XML tallies in `model/build/test-results/testDebugUnitTest/`) — identical to the Step 14 baseline. No characterization test's assertions changed; only comments and spec-doc prose were edited.

### Fix-and-reverify pass (red-team loop 1)
_By: android-migration-developer | 2026-07-25_

Addressed both findings from `## Red-Team Verdict — Implementation` (Loop 1, CHALLENGE) below. Both dispositions are documentation-only, per red-team's own disposition ("no code change needed, just retroactive disclosure"); no `.kt` file was touched.

1. **MAJOR — undisclosed `String.split` → `Pattern.compile(...).split(...)` rewrites.** Added Deviations item #7 above, disclosing both sites red-team found (`SubscriptionsFilterTest.kt:36`, the D14-governed `TextUtils.split` stub, and the undeclared sibling `FeedPreferencesTest.kt:275`), citing the Milestone-4 `FeedFunding.kt:54-60` precedent as the reason `Pattern.compile(...).split(...)` was chosen over Kotlin's `.split(Regex)`/`.split(String)` (same trailing-empty-token-stripping trap that precedent exists to avoid), and stating the JDK-contract reasoning red-team itself verified: `String.split(regex, limit)` is documented and implemented to delegate to `Pattern.compile(regex).split(input, limit)` (the only variance being an internal fast-path for single-character, non-metacharacter separators, documented to produce identical output), so both rewrites are behaviorally identical to the Java originals, not merely same-output-by-luck. This closes the disclosure gap: the equivalence claim is now established in this milestone's own record rather than resting solely on an outside auditor's after-the-fact check.
2. **MINOR — wrong ktlint gate task name.** Corrected `ktlintTestDebugSourceSetCheck` / `ktlintTestReleaseSourceSetCheck` to the actual task, `ktlintTestSourceSetCheck` (no Debug/Release variant, since Android's test source set is shared across build types unlike `main`), at all locations where the Plan/Research asserted the name as fact: the Research summary paragraph, the Research "Toolchain gates" bullet, the Research ktlint-gates-present bullet, D8's decision text, AC11, and the Open Questions gate-coverage item. Left the wording inside `## Red-Team Verdict — Implementation`'s own Concern/Evidence text unchanged, since that is the historical record of the finding itself (including a verbatim quote of the incorrect name as it appeared in the Plan at the time), not a live assertion to correct. Zero behavioral or gating consequence — `:model:ktlintCheck`'s aggregate result is unaffected either way.

**Re-verification (no code changed, so this is confirmatory per the pipeline's own discipline):** discovered `:model`'s actual verification-group test task via `./gradlew :model:tasks --group verification` (confirms `testDebugUnitTest`/`testReleaseUnitTest` are the real task names for this unflavored, debug/release-only module — no flavor-qualified variant exists here). Ran `./gradlew :model:testDebugUnitTest --rerun`: BUILD SUCCESSFUL. Tallied all per-class XML results in `model/build/test-results/testDebugUnitTest/`: **232 tests, 0 skipped, 0 failures, 0 errors** — identical to every prior baseline in this document. `git status` confirms only `tasks/antennapod-model-kotlin-milestone-7.md` is modified; no `.kt` file in the diff.

---

## Code Review Verdict
_By: migration-code-reviewer | 2026-07-25 | Loop 1 of max 3_

### Verdict
APPROVE

### Verification performed (not taken on trust)

All four gates were independently re-run from a clean invocation, not read from Implementation Notes:
- `./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 232 tests / 0 failures / 0 errors / 0 skipped.
- `./gradlew :model:ktlintCheck` → BUILD SUCCESSFUL.
- `./gradlew checkstyle lint` → `checkstyle` alone green (confirms D19: `:model:checkstyle` is `NO-SOURCE`, the two orphaned suppression entries stay inert); repo-wide `lint` fails on exactly the two pre-existing, disclosed, out-of-scope failures — `:app-wearos:compileFreeDebugKotlin`/`compilePlayDebugKotlin` (`EpisodeDetailActivity.kt:115`, `String?` vs `String`) and `:app:spotbugsPlayDebug` (`FeedInfoFragment.java:214`, `FeedFunding.content` null dereference) — verified by reading the actual compiler/SpotBugs output, not just the task-failure summary line.
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- `find model/src -name '*.java' | wc -l` → 0; `find model/src/test -name '*.kt' | wc -l` → 29.

**Scope:** `git diff --name-only 6b27ebd5f...` lists the 29 renamed test files, `model/README.md`, and `tasks/antennapod-model-kotlin-future-work.md` — no `model/build.gradle`, no `config/checkstyle/suppressions.xml`, no `.editorconfig`, nothing under `model/src/main/`. See Finding 1 below for the one File Scope gap this surfaced.

**D18/AC3B — reproduced independently, not re-read.** I wrote the Step 1 `assertlines.pl` script from the Plan text myself, discovered the developer's own copy already sitting in the shared session scratchpad (with the Step 8 `is`-prefixed-getter extension applied), diffed the two and confirmed they're identical, then ran it against all 29 files at the merge-base SHA. My independent run reproduced the Step 14c consolidated table **exactly**: `ChapterTest`=1, `EmbeddedChapterImageTest`=3, `FeedItemFilterTest`=3, `FeedItemTest`=6, `FeedMediaTest`=8, `FeedPreferencesTest`=1, `FeedTest`=1, `SubscriptionsFilterTest`=1, all other 21 files=0. I read every one of these residual diffs in full (not just the counts): `instanceof`/cast→`is`/`as` (`EmbeddedChapterImageTest`), array `.length`→`.size` (`FeedItemFilterTest`), diamond-operator `new ArrayList<>()`→`ArrayList<Chapter>()` (`ChapterTest`), array-literal→`arrayOf()` (`SubscriptionsFilterTest`), `is`-prefixed property setter gap + FQN→import (`FeedMediaTest`), and the disclosed `L`-suffix + `pubDate!!` + private-helper-declaration false-positives (`FeedItemTest`, `FeedTest`, `FeedPreferencesTest`). Every residual is a pure-syntax difference; none alters an expected-value literal, argument order, or assertion function. AC3B holds.

**AC5/AC6/AC7 (int→Long fixes):** confirmed the 3 `L`-suffix sites are the *only* ones in the suite — the full 29-file sweep above surfaces every assertion-content difference, and no other file shows an unaccounted numeric-literal residual.

**D14 deviation — independently reproduced, not trusted.** Reverted `FeedPreferencesTest.kt:34`'s stub to the Plan's literal `TextUtils.join(any(), any() as Iterable<*>)` form; ran `--tests FeedPreferencesTest`: all 16 tests failed with `InvalidUseOfMatchersException`, matching the write-up exactly. Restored the disclosed `any<Iterable<*>>()` fix; re-ran: all 16 pass. Tree confirmed clean afterward (`git diff --stat` shows the file as a clean rename again).

**Step 13 revert-and-reverify — independently reproduced one of three, not trusted.** Mutated `FeedItemFilter.matches` (`model/src/main/.../FeedItemFilter.kt:114`) from `item.media!!.lastPlayedTimeHistory!!.time == 0L` to `item.media?.lastPlayedTimeHistory?.time == 0L`; ran `testMatchesShowInHistoryWithNullLastPlayedTimeHistoryThrowsNpe` alone: failed with `expected java.lang.NullPointerException to be thrown, but nothing was thrown`, exactly as claimed. Reverted; re-ran the full suite: 232/0/0/0, `git status --short model/src/main/` clean.

**AC4 (method-name preservation) — independently reproduced.** Extracted every `@Test`-annotated method name from the 29 Java originals at the merge-base tree and from the 29 Kotlin files with my own regex (not the developer's characterization): 226 methods each side, diff empty.

**Other spot-checks:** Mother helpers are `object` + `@JvmStatic` + `const val`, no `internal` anywhere (D1/AC16); consumer files call them via unchanged `import ...FeedMother.anyFeed` / qualified `FeedMother.anyFeed()` — no consumer needed a shape-driven edit. Robolectric present on exactly 3 classes (`DownloadRequestTest`, `FeedMediaTest`, `RemoteMediaTest`; AC18). `FeedItemFallbackLinkTest.kt`'s `data()` has `@JvmStatic` + `@Parameterized.Parameters` in a `companion object` with `arrayOf<Any?>` rows (D12/AC17), and the test count came back 7, not 0 or 1. `"hasEmbeddedPictureField"` reflection literal preserved character-for-character (D13). All four teardowns (`VolumeAdaptionSettingTest`'s `@After`, three `MockedStatic.close()`) intact verbatim (AC19). Backtick sites are confined to the foreign `is`/`when` members (plus a few unrelated markdown-style backticks inside KDoc prose, which are not Kotlin identifier escapes and don't bear on AC15). No line over 120 chars remains in the 5 flagged files (AC12). No `@Suppress`, no `build.gradle`/`suppressions.xml`/`.editorconfig` touch (AC11/AC20).

### Findings

- **Severity:** MINOR
  **Class:** Scope
  **File:line:** `tasks/antennapod-model-kotlin-future-work.md` (whole file)
  **Finding:** This file is modified in the diff but is not listed in the Plan's File Scope section (which enumerates only the 29 test files, `model/README.md`, the task file, and the checkpoint file). Implementation Notes' Deviation #6 discloses this transparently — the edit predates the developer's own session (made during the planning/red-team phase to log future-work items #3–#5) and wasn't touched further during implementation. The Plan's own Resolutions and D19 text explicitly direct logging those items into this exact file, so this reads as an omission in the File Scope enumeration rather than a real scope violation by the implementer.
  **Suggested fix:** Amend the Plan's File Scope list to name this file explicitly (consistent with the standing cross-milestone future-work-doc pattern), so a future loop doesn't have to re-litigate whether this is in-scope.

- **Severity:** MINOR
  **Class:** Quality
  **File:line:** Implementation Notes, "Deviations from plan" item #4 (and cf. the Step 14c table it summarizes)
  **Finding:** Item #4 states "8 additional D18 residuals beyond the 3 disclosed D7 lines, spread across 5 files." This contradicts the document's own, correctly-computed Step 14c table a few paragraphs earlier: the actual non-D7 residual count is 21 (`ChapterTest`=1, `EmbeddedChapterImageTest`=3, `FeedItemFilterTest`=3, `FeedItemTest`=5 non-D7 of its 6, `FeedMediaTest`=8, `SubscriptionsFilterTest`=1), spread across 6 files, not 5. I independently re-ran the extractor across all 29 files (see Verification above) and confirmed the Step 14c table is the accurate one; only this summary recap undercounts.
  **Suggested fix:** Correct the "8... across 5 files" sentence to match Step 14c's 21-residual, 6-file figure, since this document is the record a future auditor (or the upcoming red-team implementation pass) will read first.

- **Severity:** MINOR
  **Class:** Convention
  **File:line:** `model/src/test/java/de/danoeh/antennapod/model/feed/FeedTest.kt:157`
  **Finding:** `feed.getPaymentLinks()!!` is a new `!!` forced by `Feed.getPaymentLinks(): ArrayList<FeedFunding>?` being nullable in Kotlin (`model/src/main/.../Feed.kt:335`) — genuinely safe here since the constructor always populates `fundingList` via `FeedFunding.extractPaymentLinks(paymentLinks)` for the non-blank literal this test passes, but it is the only one of this diff's four comparable J2K-interop `!!` additions (`DownloadRequestTest.kt:145`, `FeedFundingTest.kt:24` and its four siblings, `FeedItemTest.kt`'s `SimpleDateFormat` sites) that lacks the one-line justifying comment the other three carry, and that this milestone's own house style (and AC15's "no unjustified `!!`" bar) calls for.
  **Suggested fix:** Add a one-line comment above `FeedTest.kt:157` naming the invariant (fundingList is always non-null immediately after this constructor call), matching the pattern already used at the other three sites.

None of the above is CRITICAL or MAJOR — all three are documentation/consistency nits with zero behavioral risk, each independently verified rather than assumed. Every gate the Plan requires is green, every AC I could independently check reproduced exactly, the two revert-and-reverify claims I spot-checked (D14, one of the three Step 13 tests) both reproduced from scratch, and the D18/AC3B content-diff mechanism — the load-bearing behavioral-equivalence proof for a milestone with no independent oracle — held up under a full independent re-run across all 29 files with every non-trivial residual read and judged, not just counted.

---

## Red-Team Verdict — Implementation
_By: legacy-android-red-team | 2026-07-25 | Loop 1 of max 2_

### Verdict
CHALLENGE

### Concerns

- **Severity:** MAJOR
  **Class:** Characterization tests prove equivalence, not just existence (checklist #1) / Silent behavior changes from mechanical translation (checklist #2)
  **Concern:** D14 requires the two hand-written `thenAnswer` stub bodies that reimplement AOSP `TextUtils` semantics to "convert exactly" — it explicitly calls this out as "behaviour, not boilerplate." In practice, one of the two was **not** converted exactly, and the pattern recurs in a second, undeclared location, and neither instance is disclosed anywhere in Implementation Notes, the Deviations list, or the Code Review's findings:
    1. `SubscriptionsFilterTest.kt:36`, inside the `TextUtils.split` `thenAnswer` body (governed by D14): Java's `return text.split(expression, -1);` was rewritten to `Pattern.compile(expression).split(text, -1)`. This is not a syntax-only rename — it swaps which class's method is invoked (`String.split` vs. `Pattern.split`).
    2. `FeedPreferencesTest.kt:275`, inside the test method body `getTagsAsStringJoinsWithSeparator` (not a D14-governed `thenAnswer`, but the same substantive risk): Java's `result.split(FeedPreferences.TAG_SEPARATOR)` was rewritten to `Pattern.compile(FeedPreferences.TAG_SEPARATOR).split(result)`, with an inline comment explicitly citing Milestone 4's established "do not simplify to Kotlin's `.split(Regex)`/`CharSequence.split(vararg String)` — they do not strip trailing empty tokens the way Java's `String.split(String)` does" hazard (the same regression class that was a CRITICAL/loop-1 and MAJOR/loop-2 red-team finding on `FeedFunding.kt` in Milestone 4).

    I independently verified both rewrites are in fact behaviorally equivalent to the Java originals — `String.split(regex, limit)` is documented and implemented as delegating to `Pattern.compile(regex).split(input, limit)` (with only an internal fast-path optimization for single-char non-metachar regexes that is documented to produce identical output) — so there is no live behavioral regression here, and the developer's instinct to avoid Kotlin's `.split()` trap was correct and shows real awareness of the codebase's own precedent. **But no mechanism in this milestone ever checked that.** D18/AC3B's audit is scoped to `assert*`/`verify` call lines only; both rewrites sit in setup/stub-body lines the extractor never looks at, so they produced zero residual and were never flagged, justified, or reviewed by anyone before this pass — not the developer's own Deviations catalog (which is otherwise scrupulous about disclosing every stub-body departure, e.g. the `any<Iterable<*>>()` fix in the same file cluster), and not the code reviewer's independent verification (which spot-checked the D14 cast deviation and re-ran the D18 sweep, but had no reason to look at non-assertion lines). This is exactly the class of gap the task brief flagged as D18's disclosed Known Limit — "two different-but-textually-identical-after-canonicalization expressions that aren't actually equivalent" has a mirror-image failure mode here: two textually *different* expressions that *are* equivalent, accepted on an unverified comment's say-so, in the single highest-risk hazard cluster the Plan itself named (D14: "these bodies are behaviour, not boilerplate... picking the wrong one leaves the real overload unstubbed"). Given AEPM's premise that verification is the value being sold, an equivalence claim that happens to be correct but was never actually checked by the pipeline carries the same process risk as one that turns out wrong — this milestone got lucky that the developer's manual reasoning was sound, not that the process caught it.
  **Evidence:** `model/src/test/java/de/danoeh/antennapod/model/feed/SubscriptionsFilterTest.kt:36` vs. `git show 6b27ebd5f82a1c0583d726002a2ba439137e066d:model/src/test/java/de/danoeh/antennapod/model/feed/SubscriptionsFilterTest.java:37`; `model/src/test/java/de/danoeh/antennapod/model/feed/FeedPreferencesTest.kt:271-275` vs. the same SHA's `FeedPreferencesTest.java:215`; the established precedent comment at `model/src/main/java/de/danoeh/antennapod/model/feed/FeedFunding.kt:54-60`. Confirmed via `grep` that this class of rewrite (`Regex(`, `.toRegex()`, `.replace(`, `.trim(`, or a `String.split` ↔ `Pattern.split` swap) occurs nowhere else in the 29-file suite — it is confined to exactly these two sites.
  **Suggested mitigation:** No code change is required — both rewrites are correct and, if anything, the safer choice. Add a short Deviations entry for each (mirroring the standard already applied to every other stub-body/interop departure in this same document), record the verification performed (either the reasoning above, or a direct empirical check: run each stub against an input that would expose the Kotlin-`.split()` trailing-empty-token trap, e.g. a `TAG_SEPARATOR`-terminated tag string, and confirm both the pre- and post-conversion stub produce identical output). Optionally, extend D18's "Known Limits" paragraph to name this class of gap explicitly (setup/stub-body rewrites are outside the extractor's scope by design) so a future milestone doesn't rely on the assert-line audit alone to certify full-file equivalence.

- **Severity:** MINOR
  **Class:** Coverage gaps left unaddressed (checklist #4)
  **Concern:** Research (Toolchain gates), D8, and AC11 all name the specific task that "begins enforcing" once the first `.kt` file lands in `model/src/test` as `ktlintTestDebugSourceSetCheck` / `ktlintTestReleaseSourceSetCheck`. A forced `./gradlew :model:ktlintCheck` run shows both of those tasks are still `SKIPPED` (backed by `NO-SOURCE` on `runKtlintCheckOverTestDebugSourceSet`/`...TestReleaseSourceSet`) — the task that actually executed and enforced formatting on the 29 new Kotlin files is a differently-named `ktlintTestSourceSetCheck` (no Debug/Release variant, since Android's test source set is shared across build types unlike `main`). The aggregate `:model:ktlintCheck` result is unaffected and is genuinely green either way — this has zero behavioral or gating consequence — but the specific, wrong task name was repeated verbatim across Research, the Plan (D8/AC11), Implementation Notes, and the Code Review's verification section, without anyone reading the actual task-graph output closely enough to notice the mismatch.
  **Evidence:** Live `./gradlew :model:ktlintCheck` output: `> Task :model:runKtlintCheckOverTestDebugSourceSet NO-SOURCE`, `> Task :model:ktlintTestDebugSourceSetCheck SKIPPED`, `> Task :model:runKtlintCheckOverTestReleaseSourceSet NO-SOURCE`, `> Task :model:ktlintTestReleaseSourceSetCheck SKIPPED`, vs. `> Task :model:runKtlintCheckOverTestSourceSet` / `> Task :model:ktlintTestSourceSetCheck` (both executed, no NO-SOURCE/SKIPPED). Plan text: D8 ("`ktlintTestDebugSourceSetCheck` flipping on is not a choice this plan gets to make"), AC11 ("this now includes `ktlintTestDebugSourceSetCheck` / `ktlintTestReleaseSourceSetCheck`").
  **Suggested mitigation:** Correct the task name to `ktlintTestSourceSetCheck` wherever it's cited (D8, AC11), for accuracy on the next module's test-conversion milestone that reasons about the same gate. No action needed for this milestone's merge-readiness.

---

## Red-Team Verdict — Implementation
_By: legacy-android-red-team | 2026-07-25 | Loop 2 of max 2 (FINAL)_

### Verdict
APPROVE

### Verification performed (independent — did not trust the fix-and-reverify write-up)

**1. No `.kt` file touched during the "fix-and-reverify pass."** `git diff --stat` against the merge-base SHA (`6b27ebd5f82a1c0583d726002a2ba439137e066d`) shows the same 29 renamed test files plus `model/README.md` and `tasks/antennapod-model-kotlin-future-work.md` — identical file set to what Code Review loop 1 and Red-Team loop 1 already saw; `model/src/main/` diffs empty. Cross-checked with mtimes: all 29 test `.kt` files and both production files red-team loop 1 mutated-and-reverted (`EmbeddedChapterImage.kt` 19:41:32, `FeedItemFilter.kt` 19:27:55) carry timestamps at or before 19:41, while the task file's own mtime (19:49:30) is the only artifact touched after that point. This is consistent with the write-up's claim and inconsistent with any undisclosed code edit having occurred during the pass under review. `git status --short` on `model/src/main/` is clean.

**2. Deviations item #7 re-verified from source, not from citation.** Read both sites directly:
- `SubscriptionsFilterTest.kt:36` — confirmed `Pattern.compile(expression).split(text, -1)`, replacing the Java original's `return text.split(expression, -1);` (confirmed at the merge-base SHA, `SubscriptionsFilterTest.java:37`, exact line cited).
- `FeedPreferencesTest.kt:275` — confirmed `Pattern.compile(FeedPreferences.TAG_SEPARATOR).split(result)` inside `HashSet(Arrays.asList(*...))`, replacing the Java original's `result.split(FeedPreferences.TAG_SEPARATOR)` (confirmed at the merge-base SHA, `FeedPreferencesTest.java:215`, exact line cited). The inline comment above it (`:271-274`) accurately cites the `FeedFunding.kt` precedent.
- Read `FeedFunding.kt:54-60` directly: the cited precedent comment is real, present at exactly that line range, and reads exactly as characterized ("Do not simplify to Kotlin's `.split(Regex)` / `CharSequence.split(vararg String)`... `Pattern.compile(regex).split(input)`... is byte-for-byte identical to Java's `String.split(String)` by JDK contract"). The citation is accurate, not paraphrased into something stronger than the source supports.
- The JDK-contract reasoning in Deviations item #7 (`String.split(regex, limit)` delegates to `Pattern.compile(regex).split(input, limit)`, with only a documented single-character fast-path exception) is stated correctly and matches my own knowledge of the JDK source; it also matches, near-verbatim, the reasoning attributed to red-team loop 1 in the write-up's own text — this is not a case of the developer inventing a stronger claim than what was actually verified.

**3. Independent sweep for a third undisclosed sibling — none exists.** Grepped the merge-base Java originals (all 29 files, not just the two already named) for `.split(`, `TextUtils.split`, `StringUtils.split`, and `Pattern.compile`: the **only** two hits anywhere in the 29-file suite are `SubscriptionsFilterTest.java:37` and `FeedPreferencesTest.java:215` — the exact two sites already disclosed. Also grepped the converted Kotlin side for `Pattern.compile` and `.split(`: only the same two call sites (plus the comment lines and the `TextUtils.split(...)` stub-target reference, which is the mocked-static call itself, not a rewritten body). There is no third site anywhere in the 29-file test source set.

**4. ktlint task-name corrections re-verified live.** Ran `./gradlew :model:ktlintCheck` fresh: task graph shows `runKtlintCheckOverTestDebugSourceSet` → `NO-SOURCE` / `ktlintTestDebugSourceSetCheck` → `SKIPPED`, `runKtlintCheckOverTestReleaseSourceSet` → `NO-SOURCE` / `ktlintTestReleaseSourceSetCheck` → `SKIPPED`, and `runKtlintCheckOverTestSourceSet` → `UP-TO-DATE` / `ktlintTestSourceSetCheck` → `UP-TO-DATE` (i.e., actually executed, no Debug/Release suffix) — confirms the corrected name is the real one. Grepped the task file for all occurrences: the 6 claimed correction sites (Research summary paragraph :22, Research Toolchain-gates bullet :74, Research ktlint-gates-present bullet :223, D8 decision text :317, AC11 :575, Open Questions gate-coverage item :612) all now read `ktlintTestSourceSetCheck` correctly. The historical record inside `## Red-Team Verdict — Implementation` (Loop 1)'s own Concern/Evidence text (:1160-1162) still quotes the original wrong names (`ktlintTestDebugSourceSetCheck` / `ktlintTestReleaseSourceSetCheck`) verbatim — confirmed this was deliberately left alone as the historical record of the finding, not silently rewritten to match the correction.

**5. Full gate re-run, forced, independent of every prior run in this document:**
- `./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL. Independently tallied all 26 `TEST-*.xml` result files: **232 tests, 0 failures, 0 errors, 0 skipped.**
- `./gradlew :model:ktlintCheck` → BUILD SUCCESSFUL.
- `./gradlew checkstyle lint` → `checkstyle` alone: BUILD SUCCESSFUL, `:model:checkstyle` is `NO-SOURCE` (confirms D19: the two orphaned suppression entries remain inert). Combined `lint`: fails, but on exactly the same two pre-existing, disclosed, out-of-scope failures every prior gate run in this document reports — `:app-wearos:compileFreeDebugKotlin`/`compilePlayDebugKotlin` and `:app:spotbugsPlayDebug` (`FeedInfoFragment.java:214`) — nothing new, nothing in File Scope.
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- `git diff --name-only <merge-base-SHA>` (excluding `tasks/`/`features/`): exactly the 29 renamed test files plus `model/README.md`. `model/build.gradle`, `config/checkstyle/suppressions.xml`, `.editorconfig`, and everything under `model/src/main/` are unchanged.

### Disposition (loop cap reached — this is loop 2 of max 2, FINAL)

Both loop-1 findings hold up under independent re-verification and neither introduced a new problem in the fix: the MAJOR (undisclosed split rewrites) is now disclosed accurately, with an equivalence argument that is both correct and properly attributed to who verified what; the MINOR (task-name error) is corrected everywhere it mattered and deliberately preserved where it shouldn't be touched (the historical verdict text). The independent third-sibling sweep — the one check that could have turned "disclosed two" into "disclosed two, missed a third" — came back clean. Nothing in this pass raises a new concern.

**Verdict: APPROVE.** The milestone is cleared to proceed to PR. No further red-team loop is available or needed.

### Verification performed (not just trusted from Implementation Notes / Code Review)

- **Forced full gate re-run, independent of the developer's and reviewer's runs:** `./gradlew :model:testDebugUnitTest --rerun` → BUILD SUCCESSFUL, 232/0/0/0. `./gradlew :model:ktlintCheck` → BUILD SUCCESSFUL. `./gradlew checkstyle lint` → `checkstyle` alone green (`:model:checkstyle` `NO-SOURCE`, confirming D19); repo-wide `lint` fails on exactly the two pre-existing disclosed failures (`app-wearos` Kotlin compile break, `FeedInfoFragment.java` SpotBugs) and nothing else. `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. `find model/src -name '*.java' | wc -l` → 0; `find model/src/test -name '*.kt' | wc -l` → 29.
- **Independently reproduced Step 13's revert-and-reverify for the two `EmbeddedChapterImageTest` tests the code reviewer did not personally run** (it reproduced only the `FeedItemFilterTest` one): mutated `EmbeddedChapterImage.getModelFor` to move the null-chapters check to `java.util.Objects.requireNonNull` — `getModelForNullChaptersThrowsNpe` failed exactly as claimed, at `EmbeddedChapterImageTest.kt:161` (the frame-0 class-name assertion). Reverted, confirmed `git status --short model/src/main/` clean. Separately mutated the same function to guard `imageUrl` with `!!` at extraction time — `getModelForNullImageUrlThrowsFromInsideMatcherNotAtExtraction` failed exactly as claimed, at `EmbeddedChapterImageTest.kt:141` (`assertNotNull(exception.message)`). Reverted, confirmed clean. Full suite re-run afterward: 232/0/0/0.
- **Read the full Kotlin test bodies side-by-side with `git show 6b27ebd5f82a1c0583d726002a2ba439137e066d:<path>` Java originals** for `EmbeddedChapterImageTest`, `FeedMediaTest`, `FeedPreferencesTest`, `SubscriptionsFilterTest`, `FeedItemFallbackLinkTest`, `VolumeAdaptionSettingTest`, `DownloadRequestTest`, plus targeted excerpts of `ChapterTest`, `FeedItemFilterTest`, and `FeedItemTest` for their specific disclosed residuals. All match line-for-line except the two undisclosed rewrites in the MAJOR finding above and the already-disclosed residuals, which I independently confirmed are pure syntax (e.g. confirmed `FeedItem.isPlayed` is a real `var` property backed by JVM getter/setter `isPlayed()`/`setPlayed()`, making the Step 12 `is`-prefixed-setter gap genuinely syntax-only; confirmed `Chapter.getAfterPosition`'s signature takes `List<Chapter>?`, making the `ChapterTest` diamond-operator residual genuinely syntax-only; confirmed `FeedItemFilter.getValues()` returns `Array<String>`, making the `.length`→`.size` residuals genuinely syntax-only).
- **Spot-checked the 21-residual D18 audit myself** rather than accepting the developer's/reviewer's categorization: independently re-derived and read (not just counted) the residuals for `ChapterTest` (1), `SubscriptionsFilterTest` (1), `FeedMediaTest` (8, via source), `FeedItemFilterTest` (3, via source), `FeedItemTest` (6, via source) — all confirmed pure-syntax as categorized. Also confirmed via `grep -rln 'verify('` that the Step-12 `is`-prefixed-setter gap has no sibling occurrence anywhere else in the suite (`FeedMediaTest.kt` is the only file using Mockito `verify`), and via `grep` that the D14 Mockito-cast deviation's overload-disambiguating `any()` pattern is confined to exactly `FeedPreferencesTest`/`SubscriptionsFilterTest` (no third site).
- **Verified the `FeedTest.kt` fix-pass `!!` comment's invariant against live source**, not structural copy-paste: confirmed `Feed.getPaymentLinks()` (`Feed.kt:335-336`) returns the `fundingList` set at construction time by `FeedFunding.extractPaymentLinks(paymentLinks)` (`Feed.kt:155`), and that `extractPaymentLinks` (`FeedFunding.kt:46-77`) returns null only for a blank `payLinks` (its second `return null` at line 62 is reachable only when the entire input is composed of the separator character alone, which Java's `Character.isWhitespace` treats as blank — so it can never fire without the first `StringUtils.isBlank` guard already having fired). The test's literal `"http://example.com/pay"` is non-blank and contains neither separator character, so the invariant holds for this specific call site.
- **Confirmed AC4 (method-name preservation) independently**, with my own extraction regex rather than the developer's or reviewer's: 226 `@Test`-annotated method names on each side, diff empty.
- **Confirmed no `internal` modifier in any of the three `Mother` files** (D1/AC16) and re-read `model/README.md`'s diff against the merge base for D10/AC21 compliance — accurate and appropriately generalized, no milestone-specific provenance leaked in.

### Disposition

This is loop 1 of max 2. The MAJOR finding does not require a code change — both flagged rewrites are correct, and I have supplied the verification that was missing. What's required before re-invoking is documentation: a short Deviations entry for each of the two sites (mirroring the standard already used everywhere else in this document for stub-body departures), so this milestone's own record — not an outside auditor's after-the-fact check — is what establishes the equivalence claim. The MINOR finding is optional to fix before merge (cosmetic, zero gating impact) but cheap to correct in the same pass. Re-invoke `legacy-android-red-team` after the documentation fix for loop 2 (final).
