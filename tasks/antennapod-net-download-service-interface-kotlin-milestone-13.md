# antennapod-net-download-service-interface-kotlin-milestone-13

> **Description:** Convert the `:net:download:service-interface` module's remaining Java test files to Kotlin, now that all production code in the module is 100% Kotlin (Milestone 10 complete).
> **Repo:** `services/android-migration/projects/portfolio/antennapod`
> **Created:** 2026-08-04

> **Pre-research context (carried over from Milestone 10 / standing decisions — do not re-derive):**
> - `:net:download:service-interface` production code is **100% Kotlin** as of Milestone 10 (PR #16, merged into `develop`). Per the standing test-migration-sequencing decision, characterization tests were deliberately kept in Java, unchanged, through that milestone — this was the trigger condition for that deferral, and it is now met.
> - Confirmed directly against source (2026-08-04, on a fresh branch off `origin/develop`): `net/download/service-interface/src/main` is 0 `.java` / 7 `.kt`. `net/download/service-interface/src/test` contains exactly **6 Java files, 0 Kotlin**: `DownloadRequestBuilderCharacterizationTest`, `DownloadRequestBuilderTest`, `DownloadRequestCreatorTest`, `DownloadServiceInterfaceTest`, `FileNameGeneratorCharacterizationTest`, `FilenameGeneratorTest`. All must move to Kotlin in this milestone (Kotlin/Java coexist in the same source set; no new source set needed) — verify actual helper/fixture classes and package layout in research, do not assume this list is exhaustive of every class in the directory.
> - The module's own `README.md` already states: "This module's characterization tests are Java by design, and are the equivalence oracle for any future conversion here — a Java test that compiles and passes unmodified against this module's Kotlin API is a mechanical proof of Java/Kotlin binary compatibility. Do not port them to Kotlin without a deliberate decision to do so." That deliberate decision is what this milestone makes.
> - This milestone is a **test-only** conversion — no production `.kt` file should need to change as a result (unless a genuine J2K-interop issue forces a minimal, disclosed fix, per the module's established deviation-disclosure pattern).
> - Test tasks are **flavored** (`free`/`play`, from `playFlavor.gradle`): use `testFreeDebugUnitTest` / `testPlayDebugUnitTest`, not a bare `testDebugUnitTest` (it doesn't exist). Always pass `--rerun`, since Gradle otherwise reports `UP-TO-DATE` without re-executing.
> - Direct precedent for this exact shape of milestone: `tasks/antennapod-model-kotlin-milestone-7.md` (`:model`'s test-only conversion after Milestone 6) and `tasks/antennapod-event-kotlin-milestone-9.md` (`:event`'s, after Milestone 8) — both moved a module's Java characterization suite to Kotlin once production hit 100%, and both are good references for J2K hazard categories (numeric overload resolution in assertions, Kotlin hard keywords used as identifiers) likely to recur here.
> - See `tasks/antennapod-net-download-service-interface-kotlin.md` (Milestone 10) for this module's full production-conversion history, and `tasks/antennapod-model-kotlin-future-work.md` for the cross-module deferred-items backlog (this milestone does not need to resolve any of it).
> - This is unaffiliated OSS portfolio case-study work — see `services/android-migration/projects/portfolio/README.md`.
> - **Live instruction this session (2026-08-04):** José told the orchestrating (top-level) session directly, in conversation, to drive this milestone's pipeline through to an opened PR without pausing for a go-ahead between stages — the same shape as the equivalent instruction recorded in `features/antennapod-net-sync-service-kotlin.checkpoint.md` (2026-08-02) for Milestone 12. This is an instruction about the top-level session's own conduct, not a file-based automation clause; it does not relax any individual agent's own review standard. No standing auto-chaining exception exists in any repo file — verify current state directly rather than trusting this note.

## Research
_Last updated by: legacy-android-researcher | 2026-08-04_

### Summary

`:net:download:service-interface` is a small interop-facade module whose production code is 100% Kotlin as of Milestone 10 — **7 `.kt`, 0 `.java`** under `net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/`. Its test source set is the exact mirror image: **6 `.java` files, 0 `.kt`, 823 LOC, 54 tests, 91 assertion calls**, all in the same single package, no `androidTest` source set (`net/download/service-interface/src/` contains only `main` and `test`). Every pre-research-callout claim verifies against live source. The suite is green on both flavors: `:net:download:service-interface:testFreeDebugUnitTest --rerun` and `:net:download:service-interface:testPlayDebugUnitTest --rerun` each report **BUILD SUCCESSFUL, 54 tests, 0 failures, 0 errors, 0 skipped** — identical class-by-class counts, since no test is flavor-gated. **One correction to the callout's test-running advice, which matters for every later verification step:** `--rerun` on a *two-task* command line re-executes only one of them (`181 actionable tasks: 1 executed`, and the `testFreeDebugUnitTest` results directory kept its 28 Jul mtime). The two flavors must be run as **separate `./gradlew` invocations** for `--rerun` to bite on both; run that way, both genuinely re-executed (both result dirs restamped, 54 PASSED lines each).

The conversion is mechanically small and, unusually for this portfolio, has **no ambiguity-shaped hazards at all** — every trap found is a compile error, not a silent behavior change. There is exactly **one Kotlin hard-keyword identifier** in the whole suite (`Object val` at `DownloadRequestBuilderTest.java:111`), **zero** int→long `assertEquals` widening traps (all seven integer-literal assertions compare against genuine `Int` actuals, so they merely shift from `assertEquals(long,long)` to `assertEquals(Object,Object)` with an identical verdict), **no Mockito, no PowerMock, no hamcrest** (`net/download/service-interface/build.gradle:26-27` declares only `libs.junit` 4.13 and `libs.robolectric` 4.16), and **no helper, fixture, or `Mother`-style class of any kind** — all six files are one top-level `public class ...Test` each, with helpers as private methods inside the single class that uses them. What replaces those hazards is the same structural problem `:event` Milestone 9 hit, and here it is **narrower and much better mitigated**: a slice of this suite derives its oracle value specifically from being Java. `DownloadServiceInterfaceTest.testWorkConstants` (`:123-129`) reads the five `WORK_*` `const val`s as bare Java static fields — the module README's convention #1, and its only in-module guard — and in Kotlin those five reads compile identically whether the constants are `const val`, plain `val`, or `@JvmField val`. The same erosion applies to `@JvmStatic` on the six singleton accessors and on `FileNameGenerator.generateFileName`. Critically, the residual coverage differs sharply between them: the `WORK_*` constants and the six singleton accessors are still proven by **87 Java call sites in five other modules** that `:app:assembleDebug` compiles, whereas `FileNameGenerator.generateFileName` / `MAX_FILENAME_LENGTH` have **zero Java callers anywhere in the repo outside this suite** — after conversion nothing at all proves they are JVM statics. That asymmetry is the planner's central scoping input, and the decision (convert all six, or hold a file back under Milestone 10's own "Java-oracle bright line" rule at `tasks/antennapod-net-download-service-interface-kotlin.md:OQ2`) is explicitly not mine.

### Findings

#### Existing surface

6 Java files, 823 LOC, all in `net/download/service-interface/src/test/java/de/danoeh/antennapod/net/download/serviceinterface/`, package matching directory:

| File | LOC | Tests | Asserts | Provenance |
|---|---|---|---|---|
| `DownloadRequestCreatorTest.java` | 197 | 13 | 22 | M10 (`d5b7f94aa`) |
| `DownloadServiceInterfaceTest.java` | 190 | 11 | 31 | M10 (`d5b7f94aa`) |
| `DownloadRequestBuilderCharacterizationTest.java` | 139 | 11 | 11 | M10 (`d5b7f94aa`) |
| `DownloadRequestBuilderTest.java` | 122 | 4 | 6 | upstream (`2d77b1f11`) |
| `FilenameGeneratorTest.java` | 98 | 9 | 13 | upstream (`edb440a5a`) |
| `FileNameGeneratorCharacterizationTest.java` | 77 | 6 | 8 | M10 (`d5b7f94aa`) |
| **Total** | **823** | **54** | **91** | |

Two of the six are **pre-existing upstream AntennaPod tests** that Milestone 10 deliberately did not touch (M10 D13, `tasks/antennapod-net-download-service-interface-kotlin.md:400`), precisely so that "the pre-existing suite compiled and passed unmodified against the Kotlin API" stayed a clean claim. Converting them is the act that retires that claim's artifact.

**No helper/fixture/`Mother`-style classes, and no cross-file coupling.** `grep -E '^\s*(public |private |static |final )*(class|interface|enum) '` over the six files returns exactly six hits — one top-level class each, no nested types, no shared base class, no shared constants file. Every helper is private and file-local: `DownloadRequestBuilderCharacterizationTest.createFeed`/`createMedia` (`:22-29`), `DownloadRequestCreatorTest.createFeed`/`createFeedItem`/`createFeedMedia` (`:40-50`, an independent near-duplicate), `DownloadRequestBuilderTest.createFeedItem` (`:118-121`) and `toString(Bundle)` (`:107-116`), `FileNameGeneratorCharacterizationTest.md5Suffix` (`:24-32`), `FilenameGeneratorTest.createFiles` (`:90-97`). **Conversion order is therefore unconstrained by the compiler** — no file's conversion can break another, in either language direction. (It may still need to land together for *review* reasons; that is Risk 1's territory, not the compiler's.)

**`static` members — 4 total, all private, all file-local:** `DownloadRequestBuilderCharacterizationTest.DEST` (`:20`, `private static final String`), `FileNameGeneratorCharacterizationTest.VALID_CHARS` (`:18-22`, same shape) and `md5Suffix` (`:24`, `private static` method), `DownloadRequestBuilderTest.toString` (`:107`, `private static` method). Nothing is `public static`, nothing is package-private, nothing is read from outside its own file.

**Java-only idioms — swept exhaustively:**

- `@RunWith(RobolectricTestRunner.class)`: **all 6 files**. `@Before`: 2 (`DownloadRequestCreatorTest:34`, `DownloadServiceInterfaceTest:27`). `@After`: 1 (`DownloadServiceInterfaceTest:28`, stacked on the same method as `@Before`). `@Test`: 54. No `@Rule`, `@ClassRule`, `@Ignore`, `@Parameters`, `@SuppressWarnings`, no parameterized runners.
- **Anonymous inner classes: 2** — `new FeedUpdateManager() { … }` (`DownloadServiceInterfaceTest:144-168`, 6 overrides) and `new AutoDownloadManager() { … }` (`:177-186`, 2 overrides). Both subclass **Kotlin abstract classes**; see Risk 2.
- **Lambdas / method references: 5 `assertThrows` sites** — `DownloadRequestBuilderCharacterizationTest:137` (a **method reference**, `builder::build`), `DownloadRequestCreatorTest:166,173,180,188,195` (lambdas), `FileNameGeneratorCharacterizationTest:75` (lambda).
- **One bare initializer block used for local-variable scoping** — `DownloadRequestBuilderTest.java:66-81`, a `{ … }` block inside a method that assigns the outer `toParcel`. Kotlin has no such construct; a bare `{ … }` there is a *lambda literal that never executes*. See Risk 5.
- Reflection: none. Try-with-resources: none. Serialization round-trips: none. Varargs: none. Checked-exception *contracts*: none (see below).
- Static imports: **only `org.junit.Assert.*`** — `assertEquals`, `assertFalse`, `assertNotEquals`, `assertNull`, `assertSame`, `assertThrows`, `assertTrue`. None is a Kotlin keyword.
- **Kotlin hard/soft keyword identifiers: exactly one.** `Object val = b.get(key);` at `DownloadRequestBuilderTest.java:111`, used again at `:112`. A full sweep for `is|in|as|when|object|fun|typealias|by|out|data|open|sealed|internal|constructor|init|companion` in declaration or call position returns nothing else.
- **Underscore-containing test names: 3**, all in `DownloadRequestBuilderTest` (`:21` `parcelInArrayListTest_WithAuth`, `:27` `_NoAuth`, `:33` `_MixAuth`). ktlint relevance in Risk 8.

#### Java/Kotlin interop boundary

**Inbound to the test source set: nothing.** No `*.gradle`, workflow, or test filter anywhere names a `:net:download:service-interface` test class. CI runs whole-module tasks only (`./gradlew test${variant}UnitTest test${base-variant}UnitTest`, `.github/workflows/checks.yml:100`). `config/checkstyle/suppressions.xml` contains **no** entry for this module at all — so unlike `:event` M9, this milestone creates no orphaned-suppression follow-up.

**Outbound from the test source set** — the tests call into (a) all 7 of this module's production Kotlin classes; (b) five `:model` Kotlin types (`DownloadRequest`, `DownloadStatus`, `Feed`, `FeedItem`, `FeedMedia`); (c) one **Java** class, `UserPreferences` (`storage/preferences/src/main/java/…/UserPreferences.java:43`, still an abstract Java class with statics) via `UserPreferences.init(context)` at `DownloadRequestCreatorTest:37`; (d) Robolectric/androidx-test, commons-lang3, commons-io, and Android framework (`Bundle`, `Parcel`, `URLUtil`, `TextUtils`, `File`). Every call in (a) and (b) is *already* a Java→Kotlin interop call today. That is what makes this suite an equivalence oracle, and what the conversion removes.

**Public API surface that must not silently break: none belonging to the test source set** — it is unpublished and unconsumed. The API surface at risk is the module's **production** interop contract, and its four documented conventions (`net/download/service-interface/README.md`) split cleanly into two that survive conversion and two that do not:

| README convention | In-module guard today | After conversion |
|---|---|---|
| #1 — five `WORK_*` are `const val`, not plain `val`, so a Java subclass inherits them as unqualified `public static final` | `DownloadServiceInterfaceTest.testWorkConstants:123-129` (bare Java static-field reads) | **Lost in-module.** Kotlin resolves `DownloadServiceInterface.WORK_TAG` identically for `const val` / `val` / `@JvmField val`. **Still externally proven**: `DownloadServiceInterfaceImpl.java:58,78,99` reads `WORK_DATA_MEDIA_ID`/`WORK_TAG_EPISODE_URL`/`WORK_TAG` **unqualified via inheritance** — the exact shape only `const val` produces — plus 8 qualified reads in `EpisodeDownloadWorker.java` and `MainActivity.java`. Guarded by `:app:assembleDebug`. |
| #2 — `@JvmStatic` on the 3 singleton accessor pairs and both `DownloadRequestCreator.create` overloads | every static call site in `DownloadServiceInterfaceTest`, `DownloadRequestCreatorTest` | **Lost in-module** (Kotlin resolves through companion/object regardless). **Still externally proven** by 87 Java call sites: `DownloadServiceInterface.get()` ×29, `setImpl` ×5, `FeedUpdateManager.getInstance` ×29, `setInstance` ×1, `AutoDownloadManager.getInstance` ×15, `setInstance` ×5, `DownloadRequestCreator.create` ×3. |
| #2b — `@JvmStatic` on `FileNameGenerator.generateFileName`, and `@VisibleForTesting const val MAX_FILENAME_LENGTH` | `FilenameGeneratorTest` (9 sites incl. `:72,74,81`), `FileNameGeneratorCharacterizationTest` (7 sites incl. `:46`), `DownloadRequestCreatorTest:70,78,124,139` | **Lost entirely — zero residual coverage anywhere.** A repo-wide grep finds **no Java caller of `FileNameGenerator` outside this test suite**; its only production callers are three Kotlin sites in `DownloadRequestCreator.kt:93,98,109`. Nothing needs it to be a JVM static either — so the honest statement is that both the guard and the requirement disappear together. |
| #3 — `isDownloadingEpisode`/`isEpisodeQueued`/`getProgress` take `url: String?`, not `String` | `DownloadServiceInterfaceTest.testNullUrlIsSafe:83-92` | **Retained.** From Kotlin, `dsi.isDownloadingEpisode(null)` is a **compile error** if the parameter is ever tightened to `String`. This is M10's single sharpest regression risk and it keeps its guard. |
| #4 — `setCurrentDownloads` installs by reference, never copies | `testSetCurrentDownloadsAliasesTheInstalledMap:111-120`, `testSetCurrentDownloadsReplacesRatherThanMerges:95-108` | **Retained** — purely behavioral, language-independent. |

**Production Kotlin shapes the tests bind to** (this drives the property-vs-function split, Risk 3). Everything below is verified against live source, not inferred:

| Production member | Kotlin shape | Kotlin test must write |
|---|---|---|
| `DownloadRequest.destination`/`source`/`title`/`feedfileId`/`feedfileType`/`arguments` (`DownloadRequest.kt:9-19`), `username`/`password` (`:15-16`), `lastModified` (`:22`) | `val` / `var` properties | `built.source` — **`built.getSource()` will not compile** |
| `Feed.downloadUrl` (`Feed.kt:27`), `title` (`:312`), `pageNr` (`:78`) | `var` properties | `feed.downloadUrl`, `feed.pageNr = 3` |
| `FeedMedia.downloadUrl` (`FeedMedia.kt:62`), `mimeType` (`:56`), `id` (`:31`) | `var` properties | `media.downloadUrl` |
| `DownloadServiceInterface.isDownloadingEpisode`/`isEpisodeQueued`/`getProgress` (`DownloadServiceInterface.kt:29,34,39`) | `fun` | `dsi.getProgress(url)` — **`dsi.progress` will not compile** |
| `DownloadServiceInterface.get`/`setImpl`, `FeedUpdateManager.getInstance`/`setInstance`, `AutoDownloadManager.getInstance`/`setInstance` | `@JvmStatic fun` in `companion object` | `FeedUpdateManager.getInstance()` (unchanged) |
| `DownloadRequestCreator.create`, `FileNameGenerator.generateFileName`, `MAX_FILENAME_LENGTH` | `object` members, `@JvmStatic` / `const val` | unchanged syntax |
| `DownloadStatus.getState()`/`getProgress()` (`DownloadStatus.kt:5,9`) | `fun` | not called from tests |

**~50 bean-getter call sites must convert to property syntax**, and they cluster conveniently: `DownloadRequestBuilderCharacterizationTest` 27, `DownloadRequestCreatorTest` 17, `DownloadRequestBuilderTest` 6, and **zero** in the other three. Conversely `DownloadServiceInterfaceTest`'s 24 accessor sites (`isDownloadingEpisode` ×10, `isEpisodeQueued` ×5, `getProgress` ×5, `getInstance` ×4) must all **stay** as function calls. Every misclassification is a compile error, so the risk is diff volume and review noise, not correctness. Note that Java-library getters (`File.getName()`, `Context.getCacheDir()`, `Bundle.getInt()`, …) are *optional* synthetic-property rewrites — both syntaxes compile — and should be treated as a minimal-diff policy question, not a requirement.

#### Current test coverage

Both flavors, run as separate `--rerun` invocations: **BUILD SUCCESSFUL, 54 tests, 0 failures, 0 errors, 0 skipped each.** Per-class counts read from the live JUnit XML under `net/download/service-interface/build/test-results/testFreeDebugUnitTest/` and `…/testPlayDebugUnitTest/`, identical in both:

| File | Tests | Asserts | What it actually pins |
|---|---|---|---|
| `DownloadRequestCreatorTest` | 13 | 22 | On-disk filename contracts: stale-feed-file deletion, title-over-URL and URL-fallback feed naming, partial-download reuse, the `-1`/`-2` collision ladder, media path from feed title, title-over-URL-guess, the 220-char truncation boundary, and **5 latent-NPE paths** (null download URL on media and on feed, null item with and without an existing partial file, null title + null URL) |
| `DownloadServiceInterfaceTest` | 11 | 31 | `isDownloadingEpisode`/`isEpisodeQueued`/`getProgress` across absent/COMPLETED/QUEUED/RUNNING, **null-URL safety** (README #3), replace-not-merge and **aliasing** semantics of `setCurrentDownloads` (README #4), the five `WORK_*` constants (README #1), and get/set round-trips for all three static singletons (README #2) |
| `DownloadRequestBuilderCharacterizationTest` | 11 | 11 | `isLocalFeed()` skipping `UrlChecker.prepareUrl`, `prepareUrl` applied for non-local feed and for media, `REQUEST_ARG_PAGE_NR`, `withInitiatedByUser`, `setSource` override, `setForce`'s asymmetry (true nulls `lastModified`, false is a no-op), hard-coded `mediaEnqueued == false`, and `buildWithNullSourceThrowsNpe` |
| `FilenameGeneratorTest` | 9 | 13 | Upstream: sanitisation of slashes/colons/angle brackets/apostrophes, accent stripping, dash and interior-space preservation, trailing-space trim, the `MAX_FILENAME_LENGTH` cap, distinct hashes for distinct long inputs, and real filesystem creatability via `getExternalCacheDir()` |
| `FileNameGeneratorCharacterizationTest` | 6 | 8 | The `>=` boundary on **both** sides (241 unhashed / 242 hashed), the **exact** MD5 suffix string, the random fallback's length-8 and `validChars` membership, leading-space collapsing, `isSpaceChar`-vs-`isWhitespace` discrimination via tab and non-breaking space, and `generateFileNameNullThrowsNpe` |
| `DownloadRequestBuilderTest` | 4 | 6 | Upstream: `DownloadRequest` Parcelable round-trip through `Bundle.putParcelableArrayList` in three auth configurations, plus builder `equals`/`not-equals` on differing credentials |
| **Total** | **54** | **91** | |

These 54 tests are the entire safety net for this milestone, and **there is no independent oracle: the suite verifies itself.** A green build after conversion proves the Kotlin suite passes; it does not prove the Kotlin suite still asserts what the Java suite asserted. That is the dominant risk here, and it is what Constraints & Risks is mostly about.

#### Characterization-test gaps

**There is no untested production behavior to write before conversion.** Milestone 10 wrote this suite specifically as the characterization layer for all 7 production files, and its AC set verified the coverage; a fresh read of the six files against the seven production files confirms every public entry point is exercised. So, as with `:event` M9, this milestone needs **zero new characterization tests as a Step 1** — the tests to be written already exist. Any plan that opens with "write characterization tests first" would be writing tests for tests.

The gap is of the other kind, and it is real: **behavior currently covered only *because the test is Java*, for which no Kotlin equivalent exists.** This is coverage the milestone would *destroy*, not coverage it inherits missing. Ranked by residual risk:

1. **`FileNameGenerator`'s JVM-static shape — total loss, zero residual.** `@JvmStatic generateFileName` and `@VisibleForTesting const val MAX_FILENAME_LENGTH` are read as Java statics at 20 sites across three files. No Java caller exists anywhere else in the repo. After conversion, nothing proves either is a JVM static. **Mitigating fact, and it is a strong one: nothing requires them to be, either** — the only production callers are Kotlin (`DownloadRequestCreator.kt:93,98,109`). The honest framing for the case study is "the guard and the requirement disappeared together," not "coverage was lost."
2. **`testWorkConstants` (`DownloadServiceInterfaceTest:123-129`) becomes a tautology.** Five assertions that today prove `const val`-shaped `public static final` fields; in Kotlin they prove only that five string constants hold their values. Zero compile error, zero test failure, and the README calls this convention out first precisely because getting it wrong breaks a Java subclass. **Residual: strong but external** — `DownloadServiceInterfaceImpl.java:58,78,99`'s unqualified inherited reads fail to compile if the constants regress, and that is caught by `:app:assembleDebug`, not by this module's 3-second test task.
3. **`@JvmStatic` on the six singleton accessors — lost in-module, strongly retained externally** (87 Java call sites, five modules). Lowest actual risk of the three.
4. **The upstream-suite-unmodified artifact.** M10 D13 kept `FilenameGeneratorTest.java` and `DownloadRequestBuilderTest.java` untouched so that "the pre-existing upstream Java suite compiled and passed unmodified against the converted Kotlin API" was a checkable claim (M10 AC6). Converting them does not invalidate the historical claim — it is recorded and reproducible at `d5b7f94aa` — but it does mean the repo no longer *carries* the artifact. Worth a deliberate sentence in the milestone's narrative rather than a silent deletion.

Nothing here is a reason not to proceed. It is a reason for the planner to choose explicitly between (a) converting all six and documenting the quantified loss, (b) holding one or two files in Java under Milestone 10's own "Java-oracle bright line" rule (`tasks/antennapod-net-download-service-interface-kotlin.md` OQ2) and `:event` M9's precedent of retaining 3 of 22 files, or (c) replacing the lost assertions with language-independent reflection/`javap` checks. That choice is not mine.

#### Track-specific findings (`kotlin`)

**Null-safety hazards.** The tests are *callers*, so hazards land where a Java call site passes or receives null across a Kotlin signature. All production signatures verified live:

| Test site | Production signature | Verdict |
|---|---|---|
| `DownloadServiceInterfaceTest:47,60,73,85,97,102,113` `Map<String, DownloadStatus>` → `setCurrentDownloads` | `DownloadServiceInterface.kt:11` `MutableMap<String?, DownloadStatus>` | **Compile error unless declared exactly.** `MutableMap` is invariant, so a Kotlin `HashMap<String, DownloadStatus>()` is *not* a subtype. Must be `HashMap<String?, DownloadStatus>()`. Loud — but the tempting "fix" is to widen the production signature, which is forbidden (Risk 6) |
| `DownloadServiceInterfaceTest:89,90,91` `isDownloadingEpisode(null)` etc. | `DownloadServiceInterface.kt:29,34,39` `url: String?` | Safe — and this is the *retained* guard for README #3 |
| `DownloadServiceInterfaceTest:179-181` anonymous `AutoDownloadManager` returning `null` | `AutoDownloadManager.kt:18` `autodownloadUndownloadedItems(context: Context?): Future<*>` — **non-null return** | **Compile error in Kotlin.** See Risk 2 — this is the milestone's one genuine Java-only-interop constraint |
| `DownloadServiceInterfaceTest:146-167` anonymous `FeedUpdateManager` overrides | `FeedUpdateManager.kt:8-18` — mixed: `runOnce(context: Context?)` nullable, `runOnceOrAsk(context: Context)` **non-null** | Loud; each of the 6 overrides must match its own declaration's nullability exactly |
| `DownloadRequestCreatorTest:171,193` `createFeed(10, null, …)`, `createFeed(13, null, null)`; `:178,186` `createFeedMedia(11, null, …)` | private helpers whose Java params are unannotated | J2K defaults unannotated Java params to **non-null**, which breaks these call sites loudly. The helpers' own params must be widened to `String?` / `FeedItem?` |
| `FileNameGeneratorCharacterizationTest:75` `generateFileName(null)` | `FileNameGenerator.kt:29` `string: String?` | Safe |
| `DownloadRequestBuilderCharacterizationTest:73-75` reconstructing a `DownloadRequest` from `built`'s getters | `DownloadRequest.kt:8-19` 11-arg primary — `title: String?`, `arguments: Bundle` non-null | Safe; 11 args unambiguously selects the primary over the 9-arg secondary (`:30-43`), whose `title` is non-null |
| `FilenameGeneratorTest:91` `getExternalCacheDir()` → `new File(cache, name)` | Android `@Nullable File`; `java.io.File(File, String)` is a platform-type constructor | Safe as-is. **A `!!` added here would change behavior** on a null cache dir — do not add one |

No platform-type surprises beyond these, and there is not a single `!= null` or `Objects.requireNonNull` idiom in the suite to mistranslate.

**Numeric widening — the trap exists, but not where M7/M9 found it.** All seven integer-literal assertions (`DownloadRequestBuilderCharacterizationTest:56`; `DownloadServiceInterfaceTest:40,53,66,79,91`; `FileNameGeneratorCharacterizationTest:55`), plus the `String`-prefixed `assertEquals(message + " - size", …)` at `DownloadRequestBuilderTest:100`, compare against **genuine `Int` actuals** (`getProgress(): Int`, `Bundle.getInt(): Int`, `String.length(): Int`, `List.size(): Int`). Kotlin performs no implicit widening, so each shifts from `Assert.assertEquals(long, long)` to `assertEquals(Object, Object)`; `Integer.equals(Integer)` yields the identical verdict, differing only in failure-message formatting. **Inert — but verify rather than assume, because it is the exact mechanism behind `:model` M7's three genuine failures.**

The suite's one *real* widening trap is not in an assertion at all: `DownloadRequestBuilderTest.java:118` declares `private FeedMedia createFeedItem(final int id)` and passes `id` into `FeedMedia(id: Long, …)` (`FeedMedia.kt:17`). Java widens `int`→`long`; **Kotlin does not widen an `Int` *variable***, so this is a compile error. Either the helper's parameter becomes `Long` or the argument becomes `id.toLong()`. Both are behavior-preserving — the neighbouring `"http://example.com/episode" + id` concatenation produces the identical string either way, since `1.toString() == 1L.toString()` — but that equivalence should be stated in the plan rather than assumed by the reviewer. (By contrast, every *literal* in a `Long` position — `createFeed(1, …)`, the trailing `0`s in the 14-arg `Feed` and 12-arg `FeedMedia` constructors — is fine: Kotlin types an unsuffixed integer literal from its expected type.)

**Static-helper translation choices — 4 members, no `Mother` classes.** There is no standalone helper class to convert (unlike `:model` M7's three `Mother` builders), so the only question is where four private statics land. `DEST` (`DownloadRequestBuilderCharacterizationTest:20`) and `VALID_CHARS` (`FileNameGeneratorCharacterizationTest:18`) are `private static final String` → `private const val` in a `companion object`, or a private top-level `const val`. `md5Suffix` (`FileNameGeneratorCharacterizationTest:24`) and `toString(Bundle)` (`DownloadRequestBuilderTest:107`) are private static methods with the same choice. Only `toString` deserves a second look: it is an *overload of `Any.toString()` by arity*, which Kotlin permits, and call sites at `:103-104` resolve on arity — but a companion function literally named `toString` taking a parameter is unusual enough to warrant an explicit decision (keep the identifier for minimal diff, per the M7/M9 policy, or rename it) rather than an accidental one.

**Framework usage — nothing that resists mechanical translation.** No Mockito, no PowerMock, no hamcrest, no `mockStatic` (contrast `:model` M7, which had a `mockStatic(TextUtils)` pair). Robolectric is used uniformly: `@RunWith(RobolectricTestRunner.class)` → `@RunWith(RobolectricTestRunner::class)` on all six, mechanical. `assertThrows`'s `ThrowingRunnable` is a Java SAM whose `run()` returns `void`; Kotlin's coercion-to-`Unit` handles the four value-returning lambda bodies cleanly. The one to check empirically is `DownloadRequestBuilderCharacterizationTest:137`'s **method reference** `builder::build`, which returns `DownloadRequest`: unit conversion for *callable references* (as opposed to lambdas) is a distinct language feature, supported since Kotlin 1.4 and this repo is on 2.3.20 (`gradle/libs.versions.toml:3`), so it should be fine — but if it is not, the failure is a compile error and the fix is `{ builder.build() }`. Good case either way; just do not let it be discovered in CI.

**Checked exceptions, package-private access, static field shapes — the M11 question, answered.** `:net:sync:service-interface` Milestone 11 found a hard constraint (`ISyncService`'s `throws SyncServiceException` crossing into two Java implementors, forcing `@Throws`). **No analogue exists here.** (a) The suite's `throws` clauses — `DownloadRequestCreatorTest:53,83,96,184`, `FileNameGeneratorCharacterizationTest:42`, `FilenameGeneratorTest:26,33,40,71,90` — are all on test methods and private helpers that nothing outside the file calls; JUnit invokes them reflectively, so no `@Throws` is required and none of them is a contract. (b) **No package-private access anywhere**: all four static members are `private`, all six classes are `public`, and every production member the tests touch is `public` (including `MAX_FILENAME_LENGTH`, which is `@VisibleForTesting` but genuinely public). (c) **Static field shapes are the one place this milestone does touch Java-only interop** — but as *lost coverage* (gaps #1 and #2 above), not as a compile-time constraint on the Plan. Net: the Plan has no `@Throws`/`serialVersionUID`/visibility work item.

**Toolchain gates — the boundaries this conversion crosses:**

- **ktlint: starts enforcing.** `net/download/service-interface/build.gradle:4` applies the plugin (12.3.0). `:net:download:service-interface:ktlintCheck` today reports `runKtlintCheckOverTestSourceSet NO-SOURCE` / `ktlintTestSourceSetCheck SKIPPED` (also `TestDebug`, `TestRelease`) — verified live, a genuine no-op. It begins enforcing on the first `.kt` in the test source set. CI runs `./gradlew ktlintCheck` repo-wide (`.github/workflows/checks.yml:48`), so this is a real gate. `.editorconfig` sets `ktlint_code_style = android_studio`, `max_line_length = 120`, and exempts only `@Composable` from function naming.
- **checkstyle: no change.** `common.gradle:147-158` sources the `checkstyle` task from `src/main/java` / `src/free/java` / `src/play/java` only, never `src/test`. These six files have never been checkstyle-checked and will not be after conversion. `config/checkstyle/suppressions.xml` names nothing in this module.
- **`-Xlint:all,…,-Werror` stops covering the tests.** `common.gradle:43-48` applies it to every `JavaCompile`, currently including `:net:download:service-interface:compileFreeDebugUnitTestJavaWithJavac`. Once all six files convert, that task goes `NO-SOURCE`. This is the *last* Java in the module (main went `NO-SOURCE` at M10), so after this milestone `-Werror` covers nothing here at all.
- **`compileFreeDebugUnitTestKotlin` already exists and reports `NO-SOURCE`** — direct evidence the Kotlin test-compile path is wired by the `kotlin.android` plugin, so **`net/download/service-interface/build.gradle` needs no change**, and no new test dependency is needed (the suite uses JUnit 4 only, not `kotlin.test`).
- **No `allWarningsAsErrors` / `kotlinOptions` / `freeCompilerArgs` anywhere** in `common.gradle`, `build.gradle`, `playFlavor.gradle`, or the module's own `build.gradle`. Kotlin test warnings will not be errors — this is the recurrence of `tasks/antennapod-model-kotlin-future-work.md` item #3, and it matters concretely if any workaround (e.g. an unchecked cast) is chosen.
- **SpotBugs: no change.** `common.gradle:86` applies it, but only `spotbugsDebug`/`spotbugsPlayDebug` variants exist — no unit-test variant, so test bytecode is never analyzed.
- **Android Lint: verify, low risk.** `common.gradle:57-63` sets `warningsAsErrors true`, `abortOnError true`, `checkDependencies true`. `lint.checkTestSources` is **not set** anywhere and defaults to false, so unit-test findings should not be reported — but CI runs `./gradlew checkstyle lint` (`.github/workflows/checks.yml:46`), so confirm empirically once the first `.kt` lands. Note `net/download/service-interface/build.gradle:12` already disables `ParcelClassLoader` for this module.
- **`id("java-test-fixtures")` is applied (`build.gradle:5`) but no `src/testFixtures` directory exists** in this module (or in `:net:download:service`, the only other user). It is inert today and this milestone does not need it — flagged only so nobody assumes a fixtures source set is available or, conversely, deletes the plugin as part of "cleanup." Out of scope either way.

### Constraints & Risks

**1. (CRITICAL, silent) The suite's oracle value is partly language-dependent, and converting it destroys a quantified portion of it with no failing signal.** Fully described under *Characterization-test gaps*. Unlike `:event` M9 — where 8 of 22 files existed *expressly* to prove `@JvmField` field syntax — the loss here is narrower (one test method, `testWorkConstants`, plus the diffuse `@JvmStatic` surface) and better mitigated (87 external Java call sites, plus `DownloadServiceInterfaceImpl.java`'s unqualified inherited constant reads, all caught by `:app:assembleDebug`). The **one place with genuinely zero residual coverage is `FileNameGenerator`**, and there the requirement disappears with the guard. A green post-conversion run is not evidence against this risk; it is the expected symptom of it. **Scoping decision for the planner, not a conversion detail for the developer.**

**2. (HIGH, loud) `DownloadServiceInterfaceTest:177-186`'s anonymous `AutoDownloadManager` returns `null` from a non-null Kotlin return type — the milestone's one true Java-only-interop constraint.** `AutoDownloadManager.kt:18` declares `abstract fun autodownloadUndownloadedItems(context: Context?): Future<*>`. A Java anonymous subclass may return `null` from it; a Kotlin `object : AutoDownloadManager() { … }` may not. The test never *calls* the method — it only asserts `assertSame(manager, AutoDownloadManager.getInstance())` — so any non-null `Future` is behaviorally equivalent for this test's purpose, but that is a substitution the planner should authorise explicitly (a `FutureTask`, a `CompletableFuture.completedFuture(null)`, or keeping the file in Java) rather than something the developer improvises. **Do not resolve this by changing `AutoDownloadManager.kt`'s return type to `Future<*>?`** — that is a production edit this milestone forbids, and it would alter the signature 15 Java `getInstance()` call sites compile against.

**3. (MEDIUM, loud, high volume) The `val`-vs-`fun` split across `:model` and this module touches ~50 getter call sites.** Table under *Java/Kotlin interop boundary*. From Java every accessor is `getX()`; from Kotlin, `DownloadRequest`'s nine are properties (`built.source`) while `DownloadServiceInterface`'s three and `DownloadStatus`'s two are functions (`dsi.getProgress(url)`). The split is per-declaration and invisible from Java. Every misclassification is a compile error. The risk is diff volume and review fatigue — 50 mechanical edits concentrated in three files, with a fourth file (`DownloadServiceInterfaceTest`) needing exactly none, which is a natural batching seam.

**4. (MEDIUM, loud) `setCurrentDownloads` requires `HashMap<String?, DownloadStatus>` — and the tempting fix is a forbidden production edit.** `DownloadServiceInterface.kt:11` takes `MutableMap<String?, DownloadStatus>`, which is invariant, so the Kotlin test must declare the nullable key type at all seven construction sites. J2K will emit `MutableMap<String, DownloadStatus>` and fail loudly. **The correct fix is in the test; widening the production parameter to `MutableMap<String, DownloadStatus>` would silently break README convention #3's sibling guarantee and the `containsKey(null)` behavior M10 D-decisions preserved deliberately.** Worth an explicit plan sentence because the compiler error points at the production signature, not at the test.

**5. (MEDIUM, loud-but-confusing) `DownloadRequestBuilderTest.java:66-81`'s bare initializer block has no Kotlin equivalent.** Java allows `{ … }` inside a method purely to scope locals; the block assigns the enclosing `toParcel`. In Kotlin a bare `{ … }` is a **lambda literal that is never invoked**, so a naive transcription leaves `toParcel` unassigned — which the compiler catches ("must be initialized"), making this loud rather than silent. J2K typically emits `run { … }`, which is correct. Flagged because the *reviewable* question is whether the block is preserved as `run { }` (minimal diff, preserves the author's scoping intent) or flattened (smaller, idiomatic, loses the comment's structure). Adjacent, same file: `bundleOut.getParcelableArrayList("r")` (`:95`) is generic with no inference source and will need an explicit type argument in Kotlin.

**6. (MEDIUM, silent if "fixed") `FilenameGeneratorTest`'s three reversed `assertEquals(actual, expected)` calls must be preserved verbatim.** `:28`, `:35`, `:42` all read `assertEquals(result, "abc abc")` — arguments in the wrong order. Both are `String`, so the *verdict* is identical and only the failure message would differ, but "fixing" the order during conversion is a change to an upstream test that this milestone has no mandate to make, and it would make the before/after assertion diff non-mechanical. Same class of decision as `:model` M7's "preserve `.equals()`, never simplify to `==`" policy. Related: `FilenameGeneratorTest:21-23`'s explicit no-arg constructor calling `super()` is redundant in Kotlin and should simply disappear; that one *is* a safe deletion.

**7. (MEDIUM, loud) One Kotlin hard-keyword identifier: `Object val` at `DownloadRequestBuilderTest.java:111-112`.** Must be renamed (per `services/android-migration/.claude/skills/kotlin-j2k-style/SKILL.md`'s rename-don't-backtick convention) or backticked. Trivial in itself; noted because it is the only one in 823 lines, so a plan that budgets for "keyword collisions" as a category should know the category has exactly one member.

**8. (LOW-MEDIUM) ktlint begins enforcing on this source set, and three test names contain underscores.** `DownloadRequestBuilderTest:21,27,33`. `:model` M7 established that ktlint's `FunctionNamingRule` selects its permissive test-function regex for any file importing `org.junit` — which all six files do — so the underscores should pass unchanged. That precedent is second-hand here and cheap to confirm (`./gradlew :net:download:service-interface:ktlintCheck` after the first `.kt` lands); confirm it rather than inherit it, because the alternative — renaming three upstream test methods — is a scope expansion that should be a decision, not a reaction to a red build.

**9. (LOW) `assertThrows` with a method reference, 1 site.** `DownloadRequestBuilderCharacterizationTest:137` `builder::build`. Unit conversion for callable references is supported on Kotlin 2.3.20; if it were not, the failure is a compile error and `{ builder.build() }` is the fix. The four lambda-based `assertThrows` sites are unambiguously fine.

**10. (LOW, positive) The seven `assertThrows` tests that pin M10's latent-NPE inventory survive conversion untouched.** Every `assertThrows(NullPointerException.class, …)` in the suite (5 in `DownloadRequestCreatorTest`, 1 in `DownloadRequestBuilderCharacterizationTest`, 1 in `FileNameGeneratorCharacterizationTest`) fires from *production Kotlin* — `DownloadRequestBuilder.kt:68`'s `source!!`, `DownloadRequestCreator.kt:55-56,98`'s `!!` chain, `FileNameGenerator.kt:32`'s iteration over a null `stripAccents` result — not from the test's own language. Converting the caller changes neither which exception is thrown nor from where. This is a large, language-independent block of the suite's value, and it is the part of M10's D8 `!!` inventory that this milestone cannot weaken even accidentally.

**11. (LOW, positive) Zero sequencing constraints, and a clean hazard clustering.** No shared helpers, no cross-file references (see *Existing surface*), so any file can convert in any order and every intermediate state compiles. The hazards also cluster almost disjointly by file — `DownloadServiceInterfaceTest` owns the map-invariance, anonymous-subclass and interop-erosion hazards and has zero getter rewrites; the three `DownloadRequest*` files own all ~50 getter rewrites; `DownloadRequestBuilderTest` alone owns the `val` keyword, the bare block, the `int`→`Long` helper and the underscore names; the two `FileNameGenerator*` files are almost entirely mechanical. Whatever batching the plan chooses can be driven by hazard clustering rather than by the compiler.

**12. (LOW, process) `--rerun` does not apply to every task on a multi-task command line.** Verified this session: `./gradlew … :testFreeDebugUnitTest :testPlayDebugUnitTest --rerun` reported `1 executed, 180 up-to-date` and left the free flavor's results directory at its previous mtime. Any acceptance criterion of the form "both flavors green, forced" must specify **two separate invocations**, or it will be satisfied by a build that only actually ran one of them. The module README's current wording ("Always pass `--rerun`") is true but insufficient and is a candidate for a one-line correction.

### Unknowns

1. **Do all six files convert, or does any file stay Java?** Milestone 10's own OQ2 sets the inherited rule — "any test that cannot be hosted in Kotlin without weakening the interop proof stays Java" — and `:event` M9 applied it, retaining 3 of 22 files. The strongest candidate here is `DownloadServiceInterfaceTest`, which contains both the `testWorkConstants` static-field proof (gap #2) and the `Future<*>` anonymous-subclass constraint (Risk 2). The strongest counter-argument is that its interop coverage is externally duplicated 87-fold, unlike `:event`'s. This is the milestone's central open question and needs a written decision, not a default.
2. **How is `AutoDownloadManager`'s non-null `Future<*>` return resolved?** (Risk 2.) Substitute a real `Future`, or keep the file in Java. Both are defensible; neither should be improvised by the developer, and changing the production return type is not on the list.
3. **Is the `FileNameGenerator` `@JvmStatic` / `MAX_FILENAME_LENGTH` coverage loss accepted, replaced, or is the production annotation itself now dead weight?** With zero Java callers repo-wide, a *third* option exists that `:event` M9 did not have: note in the README that `@JvmStatic` on `generateFileName` is now unguarded *and* unneeded. Removing it would be a production edit and is out of scope — but recording the finding is not, and it belongs in `tasks/antennapod-model-kotlin-future-work.md` if it is not acted on.
4. **Does `builder::build` SAM-convert to `ThrowingRunnable`?** (Risk 9.) High-confidence yes on Kotlin 2.3.20; unverified locally (no standalone `kotlinc` in this environment) and cheap to settle on the first converted file.
5. **Do ktlint's permissive test-function naming rules actually apply here?** (Risk 8.) Inherited from M7 rather than observed on this module. One command settles it.
6. **What is the assertion-equivalence verification mechanism?** M7's answer was a canonical assertion extractor plus a per-file 1:1 content diff against the merge base with an empty-residual AC; M9 reused it. This suite is smaller (91 assertions / 6 files vs 211 / 22), so the same approach is cheaper — but the canonicalization rules must handle the ~50 `getX()`→`.x` rewrites (Risk 3), which is a larger accessor surface per assertion than either predecessor. The merge base is clean: `HEAD` = `origin/develop` = `939659e57`, working tree contains only the two untracked spec files.
7. **Does `net/download/service-interface/README.md` get updated, and how?** Its final bullet ("This module's characterization tests are Java by design… Do not port them to Kotlin without a deliberate decision to do so") becomes false the moment this milestone lands and **must** change — it is the module's own instruction to future agents. The `--rerun` bullet also warrants the Risk 12 correction. Whether the README additionally records what now guards conventions #1 and #2 depends on Unknown 1's answer.
8. **Is `lint.checkTestSources` genuinely off for this module?** Not set anywhere; AGP default is false; but `common.gradle:57-63` sets `warningsAsErrors true` + `abortOnError true` and CI runs `./gradlew checkstyle lint`. Cheap to confirm once the first `.kt` lands, expensive to discover in CI.

### Track prerequisites

- **`kotlin`** — no prerequisites; **met**. The module's production code is 100% Kotlin (7/7 `.kt` under `src/main`, 0 `.java`), satisfying the standing test-migration-sequencing gate that a module's Java test suite stays Java until its production code is fully converted. That gate was the explicit trigger condition recorded in Milestone 10 D13 and it is now satisfied. The Kotlin test-compile path is already wired — `:net:download:service-interface:compileFreeDebugUnitTestKotlin` exists and reports `NO-SOURCE` — so **`net/download/service-interface/build.gradle` requires no change**, and no new test dependency is needed (JUnit 4.13 + Robolectric 4.16 only; no `kotlin.test`). The suite is verified green on both flavors today, 54/54 each. No blocking gap.
  - **Non-blocking flag, carried forward:** the prerequisite for *compiling* this milestone is met, but *whether all six files should convert* is unsettled (Unknowns 1–3). That is a scope question, not a prerequisite failure, and it does not gate planning.
- **`gradle-kts`, `di`, `concurrency`, `compose`, `navigation`** — not requested for this milestone; not assessed. No target (Coroutines, Compose, Hilt, or otherwise) is assumed for any of them.

### Sources

- File inventory: `find net/download/service-interface/src -type f` → `src/main` **7 `.kt` / 0 `.java`**, `src/test` **6 `.java` / 0 `.kt`**, no `androidTest`; all in package `de.danoeh.antennapod.net.download.serviceinterface`
- LOC: `wc -l` over the six test files → 823 total (per-file figures in the Existing surface table)
- Test results: `./gradlew --console=plain :net:download:service-interface:testFreeDebugUnitTest --rerun` and the same for `testPlayDebugUnitTest`, **as two separate invocations** → BUILD SUCCESSFUL each, `110 actionable tasks: 1 executed` each, 54 `PASSED` lines each; XML at `net/download/service-interface/build/test-results/testFreeDebugUnitTest/*.xml` and `…/testPlayDebugUnitTest/*.xml` (6 files each) → `tests=54, failures=0, errors=0, skipped=0` on both, identical per-class counts; cross-checked against 54 `@Test` annotations
- `--rerun` multi-task caveat: the combined two-task invocation reported `181 actionable tasks: 1 executed, 180 up-to-date` and left `build/test-results/testFreeDebugUnitTest` at its 28 Jul mtime; both directories restamped only after the separate runs
- Assertion counts: `grep -cE '\bassert[A-Z][A-Za-z]*\('` per file → **91** total
- Type declarations: `grep -E '^\s*(public |private |static |final )*(class|interface|enum) '` → exactly 6 hits, one per file; no nested types, **no helper/`Mother`/fixture class**
- `static` members: `DownloadRequestBuilderCharacterizationTest.java:20`; `FileNameGeneratorCharacterizationTest.java:18,24`; `DownloadRequestBuilderTest.java:107` — all `private`, all file-local
- Annotations: `@RunWith(RobolectricTestRunner.class)` on all 6 (`:17,29,17,24,15,18`); `@Before` at `DownloadRequestCreatorTest.java:34`, `DownloadServiceInterfaceTest.java:27`; `@After` at `DownloadServiceInterfaceTest.java:28`; `@Test` ×54; no `@Rule`/`@ClassRule`/`@Ignore`/`@Parameters`/`@SuppressWarnings`
- Static imports: only `org.junit.Assert.{assertEquals,assertFalse,assertNotEquals,assertNull,assertSame,assertThrows,assertTrue}`; **no Mockito, no PowerMock, no hamcrest** (`net/download/service-interface/build.gradle:26-27` = `libs.junit`, `libs.robolectric` only; `gradle/libs.versions.toml:76,77` = JUnit 4.13, Robolectric 4.16)
- Kotlin keyword identifiers: full sweep for `val|var|fun|object|is|in|as|when|typealias|by|out|data|open|sealed|internal|constructor|init|companion` → **one hit**, `DownloadRequestBuilderTest.java:111-112` (`Object val`)
- Underscore test names: `DownloadRequestBuilderTest.java:21,27,33`
- Anonymous inner classes: `DownloadServiceInterfaceTest.java:144-168` (`FeedUpdateManager`, 6 overrides), `:177-186` (`AutoDownloadManager`, 2 overrides, `return null` at `:180`)
- Bare initializer block: `DownloadRequestBuilderTest.java:66-81`; untyped `getParcelableArrayList` at `:95`; `int`→`Long` helper at `:118-120`
- Reversed-argument assertions: `FilenameGeneratorTest.java:28,35,42`; redundant explicit constructor at `:21-23`
- Integer-literal assertions (all against `Int` actuals): `DownloadRequestBuilderCharacterizationTest.java:56`; `DownloadServiceInterfaceTest.java:40,53,66,79,91`; `FileNameGeneratorCharacterizationTest.java:55`; plus `DownloadRequestBuilderTest.java:100`
- `assertThrows` sites: `DownloadRequestBuilderCharacterizationTest.java:137` (**method reference**); `DownloadRequestCreatorTest.java:166,173,180,188,195`; `FileNameGeneratorCharacterizationTest.java:75`
- Production signatures (this module): `AutoDownloadManager.kt:18,29,34,39`; `DownloadRequestBuilder.kt:9-15,22,30,40,45,49,55,60,66-71`; `DownloadRequestCreator.kt:14,15-17,19,37,62,83,87,93,96,98,102,109,119`; `DownloadServiceInterface.kt:9,11,29,34,39,46-50,54,59`; `DownloadServiceInterfaceStub.kt:7`; `FeedUpdateManager.kt:8-18,23,28`; `FileNameGenerator.kt:11,12-13,16-22,28-29,45-46`
- Production signatures (`:model`): `DownloadRequest.kt:8-19,22,30-43,129`; `DownloadStatus.kt:3,5,9,14-16`; `Feed.kt:27,78,123-146,173-190,251,312,362,393,399`; `FeedItem.kt:30,56,140-160,413`; `FeedMedia.kt:16-28,31,56,62,85,89,110,377`
- `UserPreferences` is still Java: `storage/preferences/src/main/java/de/danoeh/antennapod/storage/preferences/UserPreferences.java:43`
- README conventions: `net/download/service-interface/README.md` (five bullets — `const val`, `@JvmStatic`, `url: String?`, install-by-reference, flavored `--rerun`, plus the "tests are Java by design" closer)
- External Java guards for convention #1: `net/download/service/src/main/java/…/feed/DownloadServiceInterfaceImpl.java:52,53,56,58,78,86,99,106` (`:58,78,99` are **unqualified inherited** reads); `net/download/service/…/episode/EpisodeDownloadWorker.java:58,78`; `app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java:239,245,246,261`
- External Java guards for convention #2 (87 call sites): `DownloadServiceInterface.get()` ×29, `setImpl` ×5, `FeedUpdateManager.getInstance` ×29, `setInstance` ×1, `AutoDownloadManager.getInstance` ×15, `setInstance` ×5, `DownloadRequestCreator.create` ×3 — `grep -rn --include='*.java'` repo-wide, excluding this module's tests
- Java subclasses of the three abstract classes: `net/download/service/src/main/java/…/feed/DownloadServiceInterfaceImpl.java:26`, `…/feed/FeedUpdateManagerImpl.java:28`, `…/episode/autodownload/AutoDownloadManagerImpl.java:11`
- `FileNameGenerator` has **zero Java callers outside this test suite**: `grep -rn --include='*.java' --include='*.kt' FileNameGenerator` → 20 hits in the 2+1 test files, 3 in `DownloadRequestCreator.kt:93,98,109`, 1 declaration; `MAX_FILENAME_LENGTH` → 4 test hits, 2 production self-references
- `DownloadRequestBuilder` retains an external Java consumer: `net/download/service/src/main/java/…/feed/FeedUpdateWorker.java`
- The one Kotlin consumer of this module anywhere: `net/sync/service/src/main/java/de/danoeh/antennapod/net/sync/service/SyncService.kt`
- Build config: `net/download/service-interface/build.gradle:1-28` (plugins `android.library` + `kotlin.android` + `ktlint` + **`java-test-fixtures` with no `src/testFixtures` directory anywhere in the repo**; `lint { disable 'ParcelClassLoader' }`); `common.gradle:43-48` (`-Xlint:all,…,-Werror` on all `JavaCompile`), `:57-63` (lint `warningsAsErrors`/`abortOnError`/`checkDependencies`, `checkTestSources` unset), `:86-94` (SpotBugs, `ignoreFailures = true`), `:147-158` (checkstyle sources = `src/main/java` + free/play only, never `src/test`); `playFlavor.gradle` (the `free`/`play` `market` dimension); **no `allWarningsAsErrors`/`kotlinOptions`/`compilerOptions`/`freeCompilerArgs` in any of them**
- ktlint is a live no-op today: `./gradlew --console=plain :net:download:service-interface:ktlintCheck` → `runKtlintCheckOverTestSourceSet NO-SOURCE`, `ktlintTestSourceSetCheck SKIPPED` (same for `TestDebug`/`TestRelease`), `runKtlintCheckOverMainSourceSet UP-TO-DATE`, BUILD SUCCESSFUL; plugin `org.jlleitschuh.gradle.ktlint` 12.3.0 (`gradle/libs.versions.toml:86`); `.editorconfig` (`ktlint_code_style = android_studio`, `max_line_length = 120`, `@Composable`-only naming exemption)
- Kotlin test-compile path already wired: `:net:download:service-interface:compileFreeDebugUnitTestKotlin NO-SOURCE` in the free-flavor test run output; Kotlin 2.3.20 (`gradle/libs.versions.toml:3`)
- No external references to this module's test class names: `config/checkstyle/suppressions.xml` (no entry for this module); CI whole-module tasks at `.github/workflows/checks.yml:100`, `ktlintCheck` at `:48`, `checkstyle lint` at `:46`
- Milestone 10 provenance: `tasks/antennapod-net-download-service-interface-kotlin.md:14` (width-of-interop framing), `:107` (the null-URL regression risk that convention #3 guards), `:300-340` (D8, the 13-`!!` inventory and the `URLUtil.guessFileName` reasoning), `:388` (D12, one-PR shape), `:394-403` (D13 — Java oracle, upstream files untouched, test conversion deferred), `:460` (Step 2/3 test content), `:533` (AC15), `:556` + OQ2 (the "Java-oracle bright line" rule inherited by this milestone), `:703` (17/17 before and after)
- Precedent: `tasks/antennapod-event-kotlin-milestone-9.md:15-219` (Research structure, the `PublicFieldInteropTest` scoping decision, ktlint/checkstyle/`-Werror` gate analysis); `:event` retained 3 of 22 files in Java — `event/src/test/java/de/danoeh/antennapod/event/{FeedItemEventTest,MessageEventTest,PublicFieldInteropTest}.java`; `tasks/antennapod-model-kotlin-milestone-7.md:15-232` (M7 Research, the `Mother`-helper and reversed-assertion policies); `tasks/antennapod-net-sync-service-interface-kotlin.md:62,106,162` (M11's checked-exception constraint — **no analogue here**); `services/android-migration/.claude/skills/kotlin-j2k-style/SKILL.md` (rename-don't-backtick)
- Cross-cutting follow-ups: `tasks/antennapod-model-kotlin-future-work.md` item #3 (`allWarningsAsErrors` for Kotlin test-compile — recurs on this module at this milestone)
- Git state: branch `antennapod-net-download-service-interface-kotlin-milestone-13`, `HEAD` = `merge-base` = `origin/develop` = `939659e578d9fbac62b9a0010df9726303cb53f6` ("kotlin/net-sync-service: kotlin migration (#18)"); working tree clean apart from the two untracked spec files. Test-file provenance: 4 files introduced by `d5b7f94aa` (Milestone 10), `DownloadRequestBuilderTest.java` by `2d77b1f11`, `FilenameGeneratorTest.java` by `edb440a5a` (both upstream AntennaPod)
- `AGENTS.md` checked against the injection incident recorded in Milestone 10's Implementation Notes (`tasks/antennapod-net-download-service-interface-kotlin.md:720`): the file as read this session contains only ordinary AntennaPod project instructions, with no self-authorizing or pipeline-directed clause. No file in this repo constitutes user consent, and none was treated as such here.

---

## Plan
_Last updated by: legacy-android-planner | 2026-08-04_

### Objective

Convert `:net:download:service-interface`'s test source set from Java to Kotlin (`kotlin` track, test-only scope), completing the module's migration begun in Milestone 10. **All 6 files convert; none is held back** (D2). No production `.kt` file changes, no test behavior changes, no test is added, renamed, split, or removed. The 54-test suite must be green on **both** flavors before and after with an identical per-class breakdown, the assertion *content* of every file must diff clean against its Java original, and the two interop conventions that lose their module-local test guard are re-proven mechanically by `javap` (D4) rather than silently dropped.

### Resolved Decisions

Every Research Unknown (1–8) is resolved below. Nothing is left to the developer's judgement, and nothing is deferred to the reviewer.

---

**D1 — One milestone, one PR, four hazard-clustered conversion commits.** (Research Risk 11; matches M7 D1, M9 D1.)

Research established zero cross-file coupling — no shared helper, no shared base class, no nested types, no file-to-file reference in either language direction — so the compiler imposes no ordering and no atomicity requirement. Batching is chosen purely for review ergonomics, driven by Research's observation that the hazards cluster almost disjointly by file:

| Tier | Files | Tests | Asserts | Why grouped |
|---|---|---|---|---|
| A — mechanical + toolchain proof | `FilenameGeneratorTest`, `FileNameGeneratorCharacterizationTest` | 15 | 21 | Zero getter rewrites, zero anonymous subclasses. Also the first `.kt` in the source set, so it is the cheapest possible probe of ktlint/lint/`-Werror` before anything risky rides on the answer |
| B — accessor-dense | `DownloadRequestBuilderCharacterizationTest`, `DownloadRequestCreatorTest` | 24 | 33 | **44 of the 50** getter→property rewrites (27 + 17), all 6 `assertThrows` sites, and every helper-nullability widening |
| C — Java-idiom cluster | `DownloadRequestBuilderTest` | 4 | 6 | The remaining **6** getter rewrites, and — alone — the bare initializer block, the `val` hard keyword, the `toString(Bundle)` overload, the `int`→`Long` helper, the untyped `getParcelableArrayList`, and all three underscore test names |
| D — interop erosion + invariance | `DownloadServiceInterfaceTest` | 11 | 31 | Owns, alone: all 7 `MutableMap` invariance sites, both anonymous subclasses, the `Future<*>` constraint, and `testWorkConstants` |

Tier A is deliberately first even though Tier D is the decision-heavy one: a mixed Java+Kotlin test source set in a **flavored** module is an assumption everything else depends on, and it costs one small commit to prove rather than assume it.

---

**D2 — All six files convert to Kotlin. None is held back under the Java-oracle bright line.** (Research Unknown 1, Constraints & Risks #1, gaps #1–#4. This is the milestone's central decision.)

Milestone 10's OQ2 sets the inherited rule and `:event` Milestone 9's D2 states it precisely: *a test whose oracle is "javac accepts this call shape" stays Java, and the **file** is the unit of language choice.* Applied honestly here, that rule says **convert**, and the reasoning has to be stated at file granularity because that is where the rule operates.

**Applying the rule to the only serious candidate, `DownloadServiceInterfaceTest`.** M9 kept `PublicFieldInteropTest` because *all 8 of its tests* were javac-oracle tests, and because its behavioral content was assertion-for-assertion identical to four other files that were converting anyway — so after conversion it would have retained **zero** unique value, and the choice was "gain 8 green tautologies that read as coverage." That is not this file:

- **10 of its 11 tests are language-independent behavioral characterization** and survive conversion at full strength: `isDownloadingEpisode`/`isEpisodeQueued`/`getProgress` across absent/COMPLETED/QUEUED/RUNNING (4 tests), null-URL safety (README #3 — *strengthened* in Kotlin, since `dsi.isDownloadingEpisode(null)` becomes a compile error if the parameter is ever tightened), replace-not-merge and install-by-reference aliasing (README #4, 2 tests), and the three singleton get/set round-trips.
- **One test, `testWorkConstants`, loses one of its two proofs — not all of its value.** Today it proves both (a) the five `WORK_*` are `const val`-shaped `public static final` fields on the outer class, and (b) their five string values. In Kotlin it stops proving (a) and keeps proving (b). That second proof is not redundant with anything: **no other test anywhere in the repo asserts these five literal strings**, and a typo in `"episodeUrl:"` or `"was_queued"` is a *runtime* WorkManager tag/data mismatch, not a compile error. The file degrades from two proofs to one; it does not become a tautology.

That is the decisive difference from `:event`, and it is why the same rule produces the opposite answer.

**The residual guard for (a) is also structurally stronger here than `:event`'s was, and this matters because M9's red-team correctly rejected a bare "it's duplicated elsewhere" argument.** `:event`'s `@JvmField` guard rested on Java *call sites* that AntennaPod's own Kotlin migration is actively deleting — a contingent guard with an expiry date. The guard here is `DownloadServiceInterfaceImpl.java:58,78,99`, a **Java subclass of a Kotlin abstract class reading the constants unqualified via inheritance**. It lives in *production* Java rather than in a test, it is compiled by `:app:assembleDebug` on every CI run, and it cannot be deleted without deleting the download service implementation. It decays only when `:net:download:service` itself converts — a scheduled future milestone that will have to make its own explicit decision, recorded as **OQ2**.

**What that guard actually proves, stated precisely** (*corrected in Revision 1 — the original text claimed this is "the exact shape only `const val` produces… even `@JvmField` cannot be inherited unqualified from a companion." That is false.*). Kotlin's documented interop behavior is that a companion-object property annotated `@JvmField` gets its backing field emitted as a `public static final` field **on the enclosing class**, exactly as `const val` does; only a *plain* `val` keeps the field `private` on the enclosing class and routes Java through `Companion.getWORK_TAG()`. So an unqualified inherited read from a Java subclass discriminates **{`const val`, `@JvmField val`} from {plain `val`}** — "a real static field rather than a Companion-routed property" — and does **not** single out `const val`.

This does not weaken D2's conclusion, because the property the module actually depends on is the one that *is* proven: `DownloadServiceInterfaceImpl`'s unqualified reads break loudly on the regression that matters (demotion to a plain `val`, the one the README already calls out as forcing `Companion.getWORK_TAG()` on every Java caller). A hypothetical `const val` → `@JvmField val` change would be invisible to that guard — and is also behaviorally inert for every consumer in this codebase, since all five are read-only `String` tags consumed at runtime by WorkManager and by ordinary Java field reads, none of which needs a compile-time constant. The one real difference is that `const val` emits a `ConstantValue` attribute and therefore inlines into Java call sites at *their* compile time; AC19 is upgraded to check for that attribute directly, so the milestone proves `const val`-ness rather than merely asserting it.

**Honest note on how this was verified.** The `@JvmField`-in-a-companion behavior above is Kotlin's documented interop semantics and matches the stdlib annotation's own contract (`@Target(FIELD)`, "expose it as a field"), and red-team reports compiling a minimal reproduction confirming it. I could **not** independently compile that reproduction — this environment has no standalone `kotlinc` and the compiler jar is not reachable — so unlike D10's and D13's corrections (both of which I checked against this repo's actual stdlib sources) this one rests on documentation plus red-team's compilation, not on my own. Recorded as such rather than presented as verified by me.

**And the loss is not merely accepted — it is replaced.** D4 makes the `javap` shape check an acceptance criterion of this milestone, which is exactly what `:event` M9's own OQ2 asked for and did not get. That converts "silently unguarded" into "mechanically re-proven at the milestone boundary," and it is strictly stronger than the source-level proxy it replaces because it reads the emitted bytecode rather than the syntax that produced it.

**Rejected alternatives:**

- **Hold `DownloadServiceInterfaceTest` in Java.** Rejected on the analysis above: it would keep 1 of 11 tests' secondary proof at the cost of leaving the module's largest behavioral characterization file outside the conversion, and it would leave the milestone's headline claim ("the suite converts") false for the file that most needs the conversion to be true. It would also be inconsistent: the `@JvmStatic` erosion it would guard is duplicated 87-fold externally, which is the weakest of the three losses, not the strongest.
- **Split the file, keeping `testWorkConstants` in a small `WorkConstantsInteropTest.java`.** Rejected on M9's own rule — the file is the unit of language choice, and splitting a characterization test inside the one milestone whose premise is that characterization tests are not edited is precisely the erosion this pipeline exists to prevent. It also renames/relocates a test, which AC4 forbids.
- **Add a new Java canary class or a reflection-based replacement test.** Rejected for the same reasons M9 rejected them, plus one specific to this repo: `AGENTS.md` mandates the absolute minimum diff, and inventing a new file to launder a compile-shape proof is a larger and stranger diff than a `javap` acceptance criterion that costs zero files.
- **Convert and say nothing about the loss.** Rejected outright. The quantified loss is stated in D4/D5, in the README (D19), and in the milestone narrative.

**Disclosed residual, stated plainly rather than discovered later.** After this milestone, the module-local, per-member, three-second signal for README conventions #1 and #2 is gone. What remains is: `DownloadServiceInterfaceImpl.java`'s inherited unqualified reads plus 87 external Java call sites (both compiled by `:app:assembleDebug`), and this milestone's one-shot `javap` proof. For `FileNameGenerator` (README #2b) there is **no** residual at all — see D5, where the honest framing is that the guard and the requirement disappear together.

---

**D3 — The anonymous `AutoDownloadManager`'s non-null `Future<*>` return is satisfied by `FutureTask<Void?> { null }`. Changing `AutoDownloadManager.kt` is forbidden.** (Research Unknown 2, Risk 2.)

`AutoDownloadManager.kt:18` declares `abstract fun autodownloadUndownloadedItems(context: Context?): Future<*>` — non-null. The Java anonymous subclass at `DownloadServiceInterfaceTest:177-186` returns `null`; a Kotlin `object : AutoDownloadManager() { … }` cannot. The exact substitute is:

```kotlin
override fun autodownloadUndownloadedItems(context: Context?): Future<*> = FutureTask<Void?> { null }
```

with `import java.util.concurrent.FutureTask` added alongside the existing `java.util.concurrent.Future` import.

Why this and not the alternatives:
- **`CompletableFuture.completedFuture(null)` is rejected.** `java.util.concurrent.CompletableFuture` is API 24 and this repo's `common.gradle:5` sets `minSdk 23`. It would run fine under Robolectric on the JVM, but it plants an API-level violation in a source set that lint does not currently scan (Research Unknown 8) — a latent trap for the day `lint.checkTestSources` is ever turned on. `FutureTask` is API 1 and carries no such risk.
- **Widening the production return type to `Future<*>?` is forbidden**, per Research's explicit prohibition. It is a production edit this milestone excludes by File Scope, and it would change the signature that 15 external Java `getInstance()` consumers compile against.
- **Keeping the file in Java to dodge this** is rejected by D2, and would be a disproportionate response: the test never *calls* `autodownloadUndownloadedItems`. Its only assertion is `assertSame(manager, AutoDownloadManager.getInstance())`. Any non-null `Future` is behaviorally identical for this test's purpose, and the `FutureTask` is never `run()`, so nothing executes.

**Permitted fallback, one only:** if SAM inference on `FutureTask<Void?> { null }` fails to select the `Callable` constructor, use `FutureTask<Void?>(Runnable { }, null)` and record the substitution in Implementation Notes. No other shape is authorized. Per `AGENTS.md` no explanatory comment is added at the call site; the substitution is documented here and pinned by AC9.

The sibling `override fun performAutoCleanup(context: Context?)` takes the production signature's nullable parameter exactly. Likewise all six `FeedUpdateManager` overrides must match `FeedUpdateManager.kt:8-18` per-declaration: `restartUpdateAlarm(context: Context?, replace: Boolean)`, `runOnce(context: Context?)`, `runOnce(context: Context?, feed: Feed?)`, `runOnce(context: Context?, feed: Feed?, nextPage: Boolean)`, `runOnceOrAsk(context: Context)` — **non-null** — and `runOnceOrAsk(context: Context, feed: Feed?)` — **non-null first parameter**. Every mismatch is a compile error, so this is transcription discipline, not risk.

---

**D4 — The interop shape lost from `testWorkConstants` and the six `@JvmStatic` accessors is re-proven mechanically by `javap`, as an acceptance criterion.** (Research Unknown 1's consequence, gaps #2 and #3; delivers what `:event` M9's OQ2 asked for.)

At Step 6 the developer runs `javap` against the compiled `:net:download:service-interface` `freeDebug` classes and pastes the output into Implementation Notes. Required findings (AC19):

| Class | Required `javap` evidence | Which README convention it proves |
|---|---|---|
| `DownloadServiceInterface` | **`javap -v`**: `public static final java.lang.String WORK_TAG;` — on the outer class, not on `Companion` — **each carrying a `ConstantValue:` attribute** with its literal string; likewise `WORK_TAG_EPISODE_URL`, `WORK_DATA_PROGRESS`, `WORK_DATA_MEDIA_ID`, `WORK_DATA_WAS_QUEUED` | #1 (`const val`), the shape `testWorkConstants` stops proving |
| `DownloadServiceInterface` | `public static DownloadServiceInterface get();` and `public static void setImpl(DownloadServiceInterface);` | #2 |
| `FeedUpdateManager` | `public static FeedUpdateManager getInstance();` / `setInstance(FeedUpdateManager);` | #2 |
| `AutoDownloadManager` | `public static AutoDownloadManager getInstance();` / `setInstance(AutoDownloadManager);` | #2 |
| `DownloadRequestCreator` | `public static DownloadRequestBuilder create(FeedMedia);` and `create(Feed);` | #2 |
| `FileNameGenerator` | `public static java.lang.String generateFileName(java.lang.String);` and `public static final int MAX_FILENAME_LENGTH;` | #2b (see D5) |

Everything below the first row uses plain `javap -p`; only the constants row needs `-v`, because `-p` alone prints `public static final java.lang.String WORK_TAG;` identically for a `const val` and for a `@JvmField val` (D2's correction) and would therefore under-prove the convention it is cited for. The `ConstantValue:` attribute is what distinguishes `const val` specifically, and it is one flag away.

**If `ConstantValue:` unexpectedly does not appear, that is recorded, not failed.** This milestone does not touch production code, so a missing attribute would be a pre-existing property of `DownloadServiceInterface.kt` rather than anything the conversion caused; the developer records the observed output and proceeds, and AC19's other rows stand on their own.

This is a build-artifact check, language-independent by construction, and strictly stronger than the source-level proxy it replaces. It is a **milestone-boundary snapshot, not a regression gate** — nothing prevents a future PR from regressing a member and removing its Java caller in the same commit. Promoting it into an automated check is **OQ1**.

---

**D5 — `FileNameGenerator`'s `@JvmStatic` / `MAX_FILENAME_LENGTH` coverage loss is accepted and recorded as future-work item 14. The annotation is not removed.** (Research Unknown 3, gap #1.)

Research established the asymmetry precisely: `FileNameGenerator.generateFileName` and `MAX_FILENAME_LENGTH` have **zero Java callers anywhere in the repo** outside this test suite; the only production callers are three Kotlin sites in `DownloadRequestCreator.kt:93,98,109`. After conversion nothing proves they are JVM statics — and nothing *needs* them to be. The guard and the requirement disappear together, which is a materially different statement from "coverage was lost," and it is the honest one for the case study.

Three options existed; the decision is (a) with (c) recorded:
- **(a) Accept.** Taken. D4's `javap` row still snapshots the shape at this milestone's boundary, so the milestone does not close blind.
- **(b) Replace with a compensating test.** Rejected — adding tests during a language-conversion milestone is a scope expansion, and it would guard a requirement no caller has.
- **(c) Note that `@JvmStatic` on `generateFileName` is now unguarded *and* unneeded.** Recording this is in scope; **acting on it is not** — removing the annotation is a production edit this milestone forbids. It is filed as `tasks/antennapod-model-kotlin-future-work.md` **item 14**, with the explicit note that removal is a *behavior-visible* change if any future Java caller appears, so it should be decided rather than swept up in a cleanup pass.

`net/download/service-interface/build.gradle`'s vestigial `java-test-fixtures` plugin (Research's Sources note) is **not** re-filed — future-work item 8 already covers it.

---

**D6 — All 7 `setCurrentDownloads` construction sites declare `HashMap<String?, DownloadStatus>()`. Widening the production parameter is forbidden.** (Research Risk 4, null-safety table row 1.)

`DownloadServiceInterface.kt:11` takes `MutableMap<String?, DownloadStatus>`. Kotlin's `MutableMap` is invariant in its key type, so a `HashMap<String, DownloadStatus>()` is **not** a subtype and will not compile. J2K will emit the non-null key type and fail loudly. The seven sites are `DownloadServiceInterfaceTest:47,60,73,85,97,102,113`; each becomes:

```kotlin
val downloads = HashMap<String?, DownloadStatus>()
```

The compiler error points at the *production* signature, which makes "just widen `DownloadServiceInterface.kt` to `MutableMap<String, DownloadStatus>`" the tempting fix. It is forbidden: the nullable key type is the sibling of README convention #3, and it is what makes `currentDownloads.containsKey(null)` return `false` instead of throwing — the exact behavior `testNullUrlIsSafe` pins and that M10's D-decisions preserved deliberately. AC8 pins both halves: the seven declarations and the untouched production file.

No local gets an explicit `Map<String?, DownloadStatus>` supertype annotation — the declared type must be `HashMap<String?, DownloadStatus>` so `testSetCurrentDownloadsAliasesTheInstalledMap`'s later `downloads["x"] = …` mutation still compiles against a mutable receiver, preserving the aliasing proof exactly.

---

**D7 — Research's `val`-vs-`fun` table is binding for all 50 getter sites. Java-library synthetic-property rewrites are permitted but never required.** (Research Risk 3.)

Every misclassification is a compile error, so this is diff volume and review fatigue, not correctness risk — but the table is the authority and the developer does not re-derive it per site. Counted exactly at planning time: **50** must-convert sites, distributed 27 / 17 / 6 / 0 / 0 / 0 across `DownloadRequestBuilderCharacterizationTest`, `DownloadRequestCreatorTest`, `DownloadRequestBuilderTest`, and the other three respectively; plus 21 optional Java-library getters and 4 `getInstance()` calls that stay calls. AC12 pins all three groups.

**Become properties** (`built.getSource()` will not compile): `DownloadRequest.destination`, `source`, `title`, `feedfileId`, `feedfileType`, `arguments`, `username`, `password`, `lastModified`; `Feed.downloadUrl`, `title`, `pageNr` (write side: `feed.pageNr = 3`); `FeedMedia.downloadUrl`, `mimeType`, `id`.

**Stay function calls** (`dsi.progress` will not compile): `DownloadServiceInterface.isDownloadingEpisode(url)`, `isEpisodeQueued(url)`, `getProgress(url)`; `DownloadServiceInterface.get()` / `setImpl(…)`; `FeedUpdateManager.getInstance()` / `setInstance(…)`; `AutoDownloadManager.getInstance()` / `setInstance(…)`; `DownloadRequestCreator.create(…)`; `FileNameGenerator.generateFileName(…)`; every `DownloadRequestBuilder` method (`withInitiatedByUser`, `setSource`, `setForce`, `lastModified`, `withAuthentication`, `build` — all verified `fun` at `DownloadRequestBuilder.kt:40,45,49,55,60,66`). `DownloadServiceInterfaceTest` has **zero** property rewrites and 24 sites that all stay functions; that is the natural batching seam D1 uses.

**Java-library getters** (`File.getName()`, `getParentFile()`, `getAbsolutePath()`, `isDirectory()`, `Context.getCacheDir()`, `Bundle.getInt()`, `String.length()`, `List.size()`) compile in **both** syntaxes. Policy: whichever form J2K emits is accepted, because the D15 extractor canonicalizes both to the same text — so this choice cannot produce a residual and is not worth a review argument either way.

---

**D8 — `FilenameGeneratorTest`'s three reversed `assertEquals(actual, expected)` calls are preserved verbatim. "Fixing" the argument order is forbidden.** (Research Risk 6; carries M7's preserve-don't-improve policy forward.)

`FilenameGeneratorTest.java:28,35,42` all read `assertEquals(result, "abc abc")` — expected and actual transposed. Both arguments are `String`, so the *verdict* is identical and only a failure message would differ. Reordering them is a change to an upstream AntennaPod test this milestone has no mandate to make, and it would make the D15 assertion diff non-mechanical for the one file where a reviewer is least able to eyeball the difference. They transcribe character-for-character, transposition intact.

Two adjacent items in the same file, decided here so they are not improvised:
- `FilenameGeneratorTest.java:21-23`'s explicit no-arg constructor calling `super()` is redundant in Kotlin and **is deleted**. This is the one safe deletion in the milestone.
- `:67`'s `assertFalse(TextUtils.isEmpty(result))` is **kept as `TextUtils.isEmpty(...)`**, not swapped to `result.isEmpty()`. Future-work item 6 already records that swap as deliberately deferred for this module; taking it here would contradict a standing decision inside a conversion diff.

---

**D9 — The one hard-keyword identifier is renamed, not backticked: `Object val` → `value`.** (Research Risk 7.)

`DownloadRequestBuilderTest.java:111-112` declares `Object val = b.get(key);` and reads it at `:112`. Per `services/android-migration/.claude/skills/kotlin-j2k-style/SKILL.md`'s rename-don't-backtick convention, this becomes `val value = b.get(key)` with the read updated to match. It is a local inside a private helper, invisible outside the file. AC11 pins **zero** backticks anywhere in the converted source set — Research swept the whole 823 lines and found exactly this one keyword collision, so no backtick escape is legitimate in this module.

---

**D10 — `DownloadRequestBuilderTest`'s bare initializer block becomes a plain `run { … }` **statement**, assigning the `val` declared above it. No restructuring, no rename.** (Research Risk 5. *Corrected in Revision 1 — the original text of this decision claimed this form does not compile. It does; see below.*)

`DownloadRequestBuilderTest.java:65-81` declares `ArrayList<DownloadRequest> toParcel;` then assigns it from inside a bare `{ … }` scoping block. A bare `{ … }` in Kotlin is an uninvoked lambda literal, so it cannot be transcribed literally. Research's stated answer — J2K "typically emits `run { … }`, which is correct" — **is correct**, and it is what this decision mandates:

```kotlin
val toParcel: ArrayList<DownloadRequest>
run {
    // test DownloadRequests to parcel
    val destStr = "file://location/media.mp3"
    val item1 = createFeedItem(1)
    val request1 = DownloadRequestBuilder(destStr, item1).withAuthentication(username1, password1).build()
    val item2 = createFeedItem(2)
    val request2 = DownloadRequestBuilder(destStr, item2).withAuthentication(username2, password2).build()
    toParcel = ArrayList()
    toParcel.add(request1)
    toParcel.add(request2)
}
```

**Why this compiles, stated with its mechanism so it is not re-litigated:** `kotlin.run` is an `inline` function declared with `contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }` — verified directly in this repo's own stdlib version at `kotlin-stdlib-2.3.20-sources.jar!/commonMain/kotlin/util/Standard.kt:41-46`, not asserted from memory. That contract is exactly the mechanism (stable since Kotlin 1.3) that lets definite-assignment analysis see through the lambda body and treat a `val` assigned exactly once inside it as validly initialized. So the outer `val` keeps its `val`-ness, the assignment stays where the Java author put it, and the inner locals keep their names.

The `// test DownloadRequests to parcel` comment stays on the block's first line and is **preserved** (`AGENTS.md`: do not remove comments already in the code), as are the `// based on:` and `// spot-check contents` comments elsewhere in the file. **No local is renamed** — in particular `toParcel` is not shadowed by an inner `list`, because nothing is introduced to shadow it. Flattening the block is **not** authorized: it discards the scoping the upstream author wrote deliberately. Neither is restructuring into an initializer expression (`val toParcel = run { … ; list }`): it compiles and is behaviorally identical, but it forces a rename and reorders the assignment for no gain, which `AGENTS.md`'s minimal-diff rule forbids.

Same file, adjacent: `:95`'s `bundleOut.getParcelableArrayList("r")` has no inference source and needs an explicit type argument — `bundleOut.getParcelableArrayList<DownloadRequest>("r")`. The result stays a platform type; **no `!!` and no explicit nullable annotation** is added (D16), so the subsequent `fromParcel.size` / `fromParcel.get(1)` reads behave exactly as the Java did.

---

**D11 — `createFeedItem`'s parameter widens to `Long`; `id.toLong()` at the call sites is rejected.** (Research's non-assertion widening trap.)

`DownloadRequestBuilderTest.java:118` declares `private FeedMedia createFeedItem(final int id)` and passes `id` into `FeedMedia(id: Long, …)` (`FeedMedia.kt:17`). Java widens `int`→`long` silently; Kotlin does not widen an `Int` *variable*, so this is a compile error. Of the two behavior-preserving fixes, the parameter widens:

```kotlin
private fun createFeedItem(id: Long): FeedMedia
```

Reasons: it is one edit instead of two, and it leaves the two call sites (`createFeedItem(1)`, `createFeedItem(2)`) textually **unchanged**, since Kotlin types an unsuffixed integer literal from its expected type. The neighbouring `"http://example.com/episode" + id` concatenation produces an identical string either way — `1.toString() == 1L.toString()` — so nothing observable moves. The method keeps its (upstream, mildly misleading) name `createFeedItem` despite returning a `FeedMedia`; renaming it is not this milestone's business.

Every *literal* in a `Long` position elsewhere is fine unsuffixed and **must not gain an `L`**: `createFeed(1, …)` through `createFeed(13, …)`, `createFeedMedia(11, …)`, and the trailing `0`s in the 14-arg `Feed` and 12-arg `FeedMedia` constructors. AC13 pins that no `L` suffix and no `.toLong()` appears anywhere in the converted suite.

---

**D12 — `toString(Bundle)` keeps its identifier and becomes a private member function.** (Research's static-helper question.)

`DownloadRequestBuilderTest.java:107`'s `private static String toString(Bundle b)` is an arity overload of `Any.toString()`, which Kotlin permits. It becomes `private fun toString(b: Bundle): String` as a member of the test class — dropping `static`, which is unobservable because both call sites (`:103-104`) are inside an instance method of the same class, and Kotlin has no file-local statics without introducing a `companion object` for one private helper. Both call sites resolve by arity and stay textually as they are.

**Permitted fallback, one only:** if this fails to compile or ktlint objects to it, rename the declaration and both call sites to `bundleToString` and disclose it in Implementation Notes. Nothing else — in particular, do not move it to a companion object "to be safe," and do not change what it computes.

---

**D13 — `md5Suffix`'s byte arithmetic and `getBytes` call are pinned to exact expressions. This is the D15 extractor's most dangerous blind spot.** (Not flagged as a distinct risk in Research; found during planning.)

`FileNameGeneratorCharacterizationTest.md5Suffix` (`:24-32`) computes the **expected value** for `generateFileNameAt242CharsIsHashedWithExactMd5Suffix` — the suite's single strictest assertion. It is a private helper body, so the D15 assertion extractor does not read a line of it. Two lines inside it do not transcribe mechanically:

- `md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8))` — Kotlin's `String` does not expose `getBytes`. It becomes `md.digest(input.toByteArray(StandardCharsets.UTF_8))`, with `import java.nio.charset.StandardCharsets` added. This is the identical JDK call; `String.toByteArray(charset)` is `kotlin.text`'s alias for it. **`input.toByteArray()` without the explicit charset is forbidden** — it defaults to UTF-8 today and would still pass, but it deletes the explicit charset the expected value depends on.
- `Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3)` — `b` is a `Byte`. Kotlin has no `&`/`|` on `Byte` with an `Int` operand. It becomes:

  ```kotlin
  Integer.toHexString((b.toInt() and 0xFF) or 0x100).substring(1, 3)
  ```

  `b.toInt()` sign-extends exactly as Java's binary-numeric promotion does before the mask, so the result is bit-identical.

  **Rejected alternatives, each for its own reason** (*corrected in Revision 1 — the original text lumped all four together as "compiles and silently changes the hash," which is true of only one of them*):
  - `(b and 0xFF.toByte())` — **rejected because it does not compile**, not because it silently changes behavior. Kotlin's `Byte` has no bitwise operators in the default-imported scope: `Byte.and`/`or`/`inv` exist only as extension functions in `kotlin.experimental` (verified at `kotlin-stdlib-2.3.20-sources.jar!/commonMain/kotlin/experimental/bitwiseOperations.kt:11,16,26`), which is not a default-imported package. Without `import kotlin.experimental.and` it is an unresolved reference; *with* the import it still fails, since `Byte.and` returns a `Byte` and the following `or 0x100` has no `Byte`-plus-`Int` overload. A loud compile error at Step 2, not a hazard.
  - `b.toUByte().toInt()` — **rejected on style only; it is arithmetically equivalent.** `UByte.toInt()` is literally `data.toInt() and 0xFF` (`kotlin-stdlib-2.3.20-sources.jar!/commonMain/kotlin/UByte.kt:331`), so `(b.toUByte().toInt() and 0xFF)` reduces to `(b.toInt() and 0xFF and 0xFF)` — identical for every byte value, not merely for the ones anyone would test. It is forbidden because it drags an unrelated unsigned type into a characterization transcription for zero behavioral gain, which is a minimal-diff objection, **not** a correctness one. AC7's grep keeps forbidding it; the reason recorded here is now the true one.
  - `String.format("%02x", b)` — rejected as a reimplementation rather than a transcription. Genuinely different: `%02x` on a negative `Byte` formats the sign-extended `int`, so this one *would* silently change the expected hash.
  - Any `toString(16)` substitute for `Integer.toHexString` — rejected; carries `:event` M9's D4 negative-value defect (`Integer.toHexString(-1)` is `"ffffffff"`, `(-1).toString(16)` is `"-1"`).

`Integer.toHexString` resolves unqualified in Kotlin via the `java.lang.*` default import — verified in-repo precedent, not asserted from the spec: `model/src/main/java/de/danoeh/antennapod/model/feed/SortOrder.kt:56` already calls `Integer.toString(...)` unqualified in production Kotlin that compiles today.

The now-unused `import java.security.NoSuchAlgorithmException` must be removed (Kotlin has no checked exceptions, the `throws` clauses drop, and ktlint's unused-import rule will otherwise fail Step 2). AC7 pins the two expressions above by direct grep, since AC6 structurally cannot see them.

---

**D14 — Test method names are preserved byte-for-byte, underscores included. No backtick sentence names.** (Research Risk 8.)

All 54 names transcribe unchanged, including `DownloadRequestBuilderTest`'s three underscore names (`parcelInArrayListTest_WithAuth`, `_NoAuth`, `_MixAuth`). M7 established that ktlint's `FunctionNamingRule` selects its permissive test regex for any file importing `org.junit` — which all six do — so nothing forces a rename. That precedent is **second-hand for this module and is confirmed empirically, not inherited**: Step 4 runs `:net:download:service-interface:ktlintCheck` immediately after `DownloadRequestBuilderTest.kt` lands and records the result. If it unexpectedly fails, that is a **hard stop** — renaming three upstream test methods is a scope expansion requiring a decision, not a reaction to a red build, and it would break AC4 and the per-class reconciliation.

---

**D15 — Assertion content is verified by a mechanical per-file 1:1 diff against the Java original, for all 6 files. Empty residual required.** (Research Unknown 6; carries M7's D18 / M9's D10 mechanism forward, adapted.)

A green suite with an unchanged test count does not prove the assertions still say what they said — two canceling edits produce identical counts. This suite is the smaller case (91 assertions / 6 files vs M7's 211 / 22), but it has a **larger accessor surface per assertion** than either predecessor, so the canonicalization needs two rules the M7/M9 extractor did not have. The script is written to the session scratchpad and **is not committed**:

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
    $a =~ s/\.(is[A-Z][A-Za-z0-9_]*)\(\)/.$1/g;
    $a =~ s/\.(size|length)\(\)/.$1/g;
    $a =~ s/\[(\d+)\]/.get($1)/g;
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

Two rules are new for this module and are the D7 adaptation Research asked for:
- `.isX()` → `.isX` — normalizes `staleFile.getParentFile().isDirectory()` (Java) against `staleFile.parentFile.isDirectory` (Kotlin) in `DownloadRequestCreatorTest:57,103`.
- `[n]` → `.get(n)` — normalizes `toParcel.get(1)` (Java) against `toParcel[1]` (Kotlin) in `DownloadRequestBuilderTest:100-104`. Safe here because Research's sweep found no array-index expression inside any assertion in this suite.

The existing `.getX()` → `.x` rule is what makes the ~50 getter rewrites (D7) invisible to the diff, which is the whole point: the audit must be blind to the syntax change and sharp on everything else.

**Validation before use, at Step 1:** for each of the 6 files, the extractor's output line count must equal that file's `grep -cE '\bassert[A-Z][A-Za-z]*\('` count (22/31/11/6/13/8, total 91). Any mismatch means the extractor is mis-calibrated and must be fixed before Step 2. Per-file invocation from Step 2 onward:

```bash
diff <(git show 939659e57:<java-path> | perl "$SCRATCH/assertlines.pl") \
     <(perl "$SCRATCH/assertlines.pl" < <kotlin-path>)
```

**The required result is an empty residual for all 6 files, with no exceptions.** A non-empty residual **stops the step** — it is not deferred to Step 6. It is recorded verbatim in Implementation Notes with a one-line pure-syntax justification and accepted or rejected individually by the reviewer. A residual that alters an expected-value literal, swaps expected/actual order, or changes the assertion function itself is a rejection, not a justification opportunity: revert the file and redo it.

**Two known residual-risk sites, called out so they are not discovered as surprises:**
- `DownloadRequestBuilderCharacterizationTest:137`'s `assertThrows(NullPointerException.class, builder::build)`. Kept as a callable reference (D16), it canonicalizes clean. Converted to a trailing lambda it would canonicalize to `…, builder.build())` and residual against the Java's `builder::build` — which is a *disclosed* residual, not a silent one, and D16 forbids the conversion anyway.
- The five lambda `assertThrows` sites in `DownloadRequestCreatorTest:166,173,180,188,195` and one in `FileNameGeneratorCharacterizationTest:75` are handled by the trailing-lambda and `() ->` rules together and must canonicalize identically. Verified at Step 1's calibration.

**Known blind spots, stated so they are not mistaken for coverage.** The extractor reads assertion call lines only. It does not see: `md5Suffix`'s body (D13), the `run { … }` block (D10), the `HashMap<String?, …>` declarations (D6), the anonymous subclasses (D3), or the helper signatures (D11/D18). AC7, AC8, AC9 and compile failure cover those respectively. AC6 and AC7 are complementary; neither subsumes the other.

---

**D16 — `builder::build` stays a callable reference. No `!!` is added anywhere.** (Research Risk 9, Unknown 4; null-safety table rows 5–8.)

`assertThrows`'s `ThrowingRunnable.run()` returns `void`; Kotlin's coercion-to-`Unit` covers the value-returning lambda bodies. Unit conversion for *callable references* is supported since Kotlin 1.4 and this repo is on 2.3.20 (`gradle/libs.versions.toml:3`), so `assertThrows(NullPointerException::class.java, builder::build)` should compile as-is. If it does not, the failure is a loud compile error at Step 3 and the single permitted fix is `{ builder.build() }`, with the resulting D15 residual disclosed under that step. Settled empirically at Step 3 either way.

`!!` is forbidden throughout the converted suite (AC15). In particular, `FilenameGeneratorTest:91`'s `getExternalCacheDir()` returns an Android `@Nullable File`, and `java.io.File(File, String)` is a platform-type constructor that accepts it — **adding `!!` there would change behavior** on a null cache dir, turning a later `mkdir()` failure into an earlier NPE. `File(cache, name)` transcribes as-is with the platform type flowing.

---

**D17 — The two `private static final String` constants become `private const val` in a `private companion object`.**

`DownloadRequestBuilderCharacterizationTest.DEST` (`:20`) and `FileNameGeneratorCharacterizationTest.VALID_CHARS` (`:18-22`) each land as `private const val` inside a `private companion object` at the bottom of their own class, keeping their SCREAMING_SNAKE names and their exact string content — `VALID_CHARS`'s four-part concatenation stays four parts across four lines, since collapsing it would exceed the 120-char line limit and gratuitously widen the diff. Private top-level `const val`s were the alternative; the companion is chosen because it preserves the members' class association exactly as Java had it.

`FileNameGeneratorCharacterizationTest.md5Suffix` (a private static method) joins the same companion object as a `private fun`, since it is only called from test methods in that class. (`DownloadRequestBuilderTest.toString` is the exception — D12 makes it a member function.)

---

**D18 — The private helpers' parameters widen to nullable exactly where their call sites require it, and nowhere else.** (Research null-safety table row 5.)

J2K defaults unannotated Java parameters to non-null, which breaks four call sites loudly. The required signatures, verified against the `:model` constructors they forward to (all of which accept nullable — `Feed.kt:173-190`, `FeedItem.kt:140-157`, `FeedMedia.kt:16-28`):

| File | Kotlin signature |
|---|---|
| `DownloadRequestCreatorTest` | `private fun createFeed(id: Long, downloadUrl: String?, title: String?): Feed` |
| `DownloadRequestCreatorTest` | `private fun createFeedItem(id: Long, title: String?, feed: Feed?): FeedItem` |
| `DownloadRequestCreatorTest` | `private fun createFeedMedia(id: Long, item: FeedItem?, downloadUrl: String?, localFileUrl: String?): FeedMedia` |
| `DownloadRequestBuilderCharacterizationTest` | `private fun createFeed(id: Long, downloadUrl: String?, title: String?): Feed` |
| `DownloadRequestBuilderCharacterizationTest` | `private fun createMedia(id: Long, downloadUrl: String?): FeedMedia` |
| `DownloadRequestBuilderTest` | `private fun createFeedItem(id: Long): FeedMedia` (D11) |

The two near-duplicate `createFeed` helpers **stay duplicated** in their two files. Extracting a shared fixture is a scope expansion this milestone declines (Out of Scope), and it would create the first cross-file coupling in a source set whose zero-coupling property D1's batching depends on.

`DownloadRequestCreatorTest`'s `private Context context` field becomes `private lateinit var context: Context`, assigned in `@Before setUp()`. `UserPreferences.init(context)` calls into a still-Java class (`storage/preferences/…/UserPreferences.java:43`) and needs no change.

---

**D19 — `net/download/service-interface/README.md`'s last two bullets are rewritten. No milestone provenance.** (Research Unknown 7, Risk 12.)

Three bullets are factually wrong or insufficient, and the README is the module's own instruction to future agents:

1. **The "tests are Java by design" bullet (`:13`) is replaced.** Its instruction — "do not port them to Kotlin without a deliberate decision" — has now been discharged; leaving it would tell the next contributor to undo this milestone. The replacement states, as a long-term-stable convention, what actually guards conventions #1 and #2 now: that `DownloadServiceInterfaceImpl`'s unqualified inherited reads of the `WORK_*` constants and the ~87 Java call sites of the `@JvmStatic` accessors are the live compile-time guard (via `:app:assembleDebug`), that the module's own tests are Kotlin and therefore prove the constants' **values** but not their **shape**, and that `javap -p` on the built classes is the check to run when changing any of it. It must also say plainly that `FileNameGenerator.generateFileName`'s `@JvmStatic` has no Java caller left and is therefore unguarded — so that a future removal is a decision rather than an accident.
2. **The `--rerun` bullet (`:12`) gains Risk 12's correction:** `--rerun` applies to only one task on a multi-task command line, so the two flavors must be run as **two separate `./gradlew` invocations** or the second silently reports `UP-TO-DATE` without executing. This is a live footgun for every future agent working in this module and it is worth the one line.

3. **The `const val` bullet (`:8`) has its justification corrected** (*added in Revision 1*). It currently reads "The five `WORK_*` constants … are `const val`, **not plain `val` or `@JvmField val`**. **Only `const val`** emits a `public static final` field on the outer class that a Java subclass inherits unqualified." Per D2's correction that is wrong on both counts: `@JvmField val` emits the same externally-visible static field, so `DownloadServiceInterfaceImpl`'s unqualified reads do not discriminate it from `const val`. The bullet's **operative instruction is kept** — these stay `const val` — but its stated reason is corrected to the true one: a plain `val` is the regression the unqualified inherited reads actually catch, and what makes `const val` the right choice over `@JvmField val` is that it emits a `ConstantValue` attribute, so Java call sites inline the literal and need no class initialization. This is one sentence in a file this milestone is already editing, and leaving it would hand the next agent the same false premise that had to be corrected here.

Per `AGENTS.md`, all three bullets are phrased as long-term-stable module conventions with **no milestone numbers, no task-file references, and no task-specific detail**. The other three bullets are unchanged.

---

**D20 — `net/download/service-interface/build.gradle` is not modified and is deliberately excluded from File Scope.** Research verified `:net:download:service-interface:compileFreeDebugUnitTestKotlin` already exists and reports `NO-SOURCE`, so the Kotlin test-compile path is wired by the already-applied `kotlin.android` plugin. The suite is JUnit 4 + Robolectric only — **no `kotlin.test`, no `mockito-kotlin`, no new dependency of any kind**. Excluding the file from File Scope means a build-config change cannot slip in unnoticed. Same posture as M7's D16 and M9's D12.

---

**D21 — ktlint begins gating this source set and the gate is not to be softened.** `:net:download:service-interface:ktlintCheck` is a live no-op today (`runKtlintCheckOverTestSourceSet NO-SOURCE`, `ktlintTestSourceSetCheck SKIPPED`) and starts enforcing on the first `.kt` in `src/test`. CI runs `./gradlew ktlintCheck` repo-wide (`.github/workflows/checks.yml:48`). Zero `@Suppress("ktlint:…")` annotations, no `ktlint_disabled_rules`, no `.editorconfig` change, no ktlint filter or exclusion in any build file. Line wraps to satisfy the 120-char limit are made at **argument/comma boundaries only**, with `(` and `)` glued to their adjacent tokens — M7's red-team loop 2 established that any other wrap style produces false-positive residuals in the D15 audit.

---

**D22 — The seven integer-literal assertion sites' overload shift is verified green, not assumed, and `.toLong()` is forbidden.** (Research's numeric-widening finding.)

`DownloadRequestBuilderCharacterizationTest:56`, `DownloadServiceInterfaceTest:40,53,66,79,91`, `FileNameGeneratorCharacterizationTest:55`, plus the `String`-prefixed `DownloadRequestBuilderTest:100`, all shift from `Assert.assertEquals(long, long)` to `assertEquals(Object, Object)` because Kotlin performs no implicit widening and every actual is a genuine `Int`. `Integer.equals(Integer)` yields the identical verdict, differing only in failure-message formatting. Research is confident this is inert — and it is the exact mechanism behind `:model` M7's three genuine failures, so it is confirmed by AC2 being green rather than by argument. If any site unexpectedly fails to resolve, the fix is **not** `.toLong()` on either operand and **not** a relaxation to `assertTrue(a == b)`: stop the step and disclose.

---

### Steps

Each step is one reviewable diff and leaves the build green. **`--rerun` is mandatory and the two flavors are always two separate invocations** (Research Risk 12) — a combined `:testFreeDebugUnitTest :testPlayDebugUnitTest --rerun` re-executes only one of them and reports the other `UP-TO-DATE`:

```bash
./gradlew --console=plain :net:download:service-interface:testFreeDebugUnitTest --rerun
./gradlew --console=plain :net:download:service-interface:testPlayDebugUnitTest --rerun
```

**Standing obligation on every conversion step (Steps 2–5), per D15.** A step is not complete when the suites go green. For each file the step converts, the developer runs the assertion-content diff against that file's Java original at the merge base and pastes the result under that step's heading in Implementation Notes. The expected result is an **empty** diff for every file, with no exceptions. A non-empty residual stops the step; it is not deferred to Step 6.

**Note on the characterization-tests-first rule.** The pipeline's non-negotiable "Step 1 writes characterization tests" rule is **satisfied, not waived**. Research found no characterization-test gap because Milestone 10 wrote this suite from scratch as the characterization layer for all 7 production files and verified every public entry point is exercised; the tests that must exist before conversion already exist and are green on both flavors. A plan that opened with "write characterization tests first" would be writing tests for tests. Step 1 is therefore baseline *capture* rather than test *authoring* — the same obligation, discharged by the previous milestone. The separate gap Research identified (coverage that exists only *because* the test is Java) is not closable by writing more tests, and is handled by D2/D4/D5 instead.

**Step 1 — Capture the baseline and stand up the audit tool. No files change.**

Paste into Implementation Notes:
- a. **Merge-base SHA** — `git merge-base HEAD develop`, currently `939659e578d9fbac62b9a0010df9726303cb53f6`, with the working tree clean apart from the two untracked spec files. Every `.java` original is destroyed by its rename, so this SHA is the only route back to the pre-conversion text; without it the audit is unreproducible by the reviewer.
- b. **Per-class test counts, both flavors, two separate `--rerun` invocations.** For each run, record the command verbatim, the `N actionable tasks: M executed` line, and the `build/test-results/<task>/` directory mtime **before and after** — the three-part evidence that `--rerun` actually bit. Then a `classname → tests/failures/errors/skipped` table for all 6 classes from `net/download/service-interface/build/test-results/testFreeDebugUnitTest/TEST-*.xml` and `…/testPlayDebugUnitTest/TEST-*.xml`. Expected, identical on both flavors: **54 tests, 0 failures, 0 errors, 0 skipped** — `DownloadRequestCreatorTest` 13, `DownloadServiceInterfaceTest` 11, `DownloadRequestBuilderCharacterizationTest` 11, `FilenameGeneratorTest` 9, `FileNameGeneratorCharacterizationTest` 6, `DownloadRequestBuilderTest` 4.
- c. **Per-file assertion counts** — `grep -cE '\bassert[A-Z][A-Za-z]*\(' <file>` for each of the 6 files. Expected: 22 / 31 / 11 / 6 / 13 / 8, total **91**.

Then write `assertlines.pl` (D15) to the session scratchpad — **not** to the repo; it is an audit tool, not a deliverable, and is not in File Scope. Validate it before relying on it: its output line count must equal each file's grep count from (c) for all 6 files. If any file mismatches, the extractor is mis-calibrated for this codebase and must be fixed before Step 2. Record the per-file totals and confirm specifically that the six `assertThrows` sites canonicalize as D15 predicts.

**Step 2 — Convert Tier A: `FilenameGeneratorTest` and `FileNameGeneratorCharacterizationTest` (2 files, 15 tests). Also the toolchain proof.**

`git mv` both to `.kt` and rewrite. Apply D8 (three reversed `assertEquals` verbatim, redundant constructor deleted, `TextUtils.isEmpty` kept), D13 (`md5Suffix`'s two pinned expressions and the removed `NoSuchAlgorithmException` import), D16 (no `!!` at `getExternalCacheDir()`), D17 (`VALID_CHARS` as a `private const val` in a `private companion object`, four-part concatenation intact), D14 (names byte-for-byte). Run the `kotlin-j2k-style` skill on both files after J2K and before reporting them converted.

This tier is deliberately first even though it is nearly mechanical: it is the cheapest possible proof that a **mixed Java+Kotlin test source set in a flavored module** compiles and runs on both flavors before anything risky depends on that. Three checks run here and nowhere else:
- `./gradlew :net:download:service-interface:ktlintCheck` — confirm `ktlintTestSourceSetCheck` is now genuinely **executing**, not `SKIPPED`/`NO-SOURCE` (D21).
- `./gradlew checkstyle lint` — settles Research Unknown 8. `lint.checkTestSources` is unset and AGP defaults it to false, but `common.gradle:57-63` sets `warningsAsErrors true` + `abortOnError true` and CI runs this task. Cheap here, expensive to discover in CI. Record the answer in writing either way.
- Confirm `:net:download:service-interface:compileFreeDebugUnitTestJavaWithJavac` **still executes** (is not yet `NO-SOURCE`), i.e. `-Xlint:all -Werror` still covers the four remaining Java files at this point.

**Step 3 — Convert Tier B: `DownloadRequestBuilderCharacterizationTest` and `DownloadRequestCreatorTest` (2 files, 24 tests).**

The bulk of the mechanical volume: 44 of the 50 getter→property rewrites (27 + 17), per D7's binding table. Apply D18 (helper nullability widening — this is what makes `createFeed(10, null, …)`, `createFeed(13, null, null)` and `createFeedMedia(11, null, …)` compile), D17 (`DEST`), D16 (**settle `builder::build` empirically here** and record the outcome), D11's no-`L`-suffix rule for the `createFeed(1..13, …)` literals, D22 (`assertEquals(3, built.arguments.getInt(...))`). The 11-arg `DownloadRequest` reconstructions at `:73-75` and `:85-87` select the primary constructor unambiguously and their argument order is transcribed exactly. `context` becomes `private lateinit var context: Context`.

The seven `assertThrows(NullPointerException::class.java) { … }` tests across these two files pin M10's latent-NPE inventory and fire from *production* Kotlin `!!` sites — converting the caller changes neither which exception is thrown nor from where. They must remain seven, and AC6's residual must be empty for both files.

**Step 4 — Convert Tier C: `DownloadRequestBuilderTest` alone (1 file, 4 tests).**

The whole file is one decision surface. Apply D10 (the `run { … }` **statement**, assigning the `val` declared above it, exactly the authorized shape, no rename; comments preserved; explicit `getParcelableArrayList<DownloadRequest>` type argument; no `!!`), D9 (`val` → `value`), D12 (`toString(Bundle)` as a private member function, with the single named fallback), D11 (`createFeedItem(id: Long)`, call sites unchanged), D14 (the three underscore names byte-for-byte), D22 (`assertEquals(message + " - size", …)`).

**This is where D14 is settled empirically.** Run `:net:download:service-interface:ktlintCheck` immediately after this file lands and record whether the three underscore names pass. A failure is a **hard stop**, not a rename.

**Step 5 — Convert Tier D: `DownloadServiceInterfaceTest` alone (1 file, 11 tests).**

The milestone's decision-dense file, and the one D2 is about. Apply D6 (`HashMap<String?, DownloadStatus>()` at all seven sites, no supertype annotation, production file untouched), D3 (both anonymous subclasses as `object : … { }` with per-declaration nullability matched exactly, and the `FutureTask<Void?> { null }` substitute), D7 (**zero** property rewrites in this file — all 24 accessor sites stay function calls), D22 (the five `assertEquals(-1|0|42, dsi.getProgress(url))` sites), D14.

`testWorkConstants` converts unchanged and keeps its name; per D2 it now proves the five constants' **values** and no longer their shape, which D4 re-proves at Step 6 and D19 records in the README. The `@Before @After resetStatics()` method keeps both annotations stacked on the same function.

**Step 6 — Whole-suite reconciliation, the full gate set, and the `javap` interop re-proof.**

Run, in this order, and paste all output:
- `./gradlew --console=plain :net:download:service-interface:testFreeDebugUnitTest --rerun`
- `./gradlew --console=plain :net:download:service-interface:testPlayDebugUnitTest --rerun` (separate invocation)
- `./gradlew :net:download:service-interface:ktlintCheck`
- `./gradlew checkstyle lint`
- `./gradlew :app:assembleDebug`

Then reconcile against Step 1's baseline:
- a. Re-extract the 6-row per-class test table **for each flavor** and diff against baseline (b). Every row must be identical — not merely the total — and both flavors must show the `--rerun` evidence triple again.
- b. Re-extract the per-file assertion-count table and diff against baseline (c). Total **91**.
- c. **Re-run the D15 assertion-content diff across all 6 files in one sweep** and paste a consolidated `file → residual line count` table, every row `0`. This is a whole-suite re-derivation, not a restatement of the per-step results — it catches a file that a later step touched incidentally after its own audit passed.
- d. **Run the D4 `javap` interop re-proof** against the compiled `freeDebug` classes and paste the output for all six classes in D4's table — `javap -v` for `DownloadServiceInterface`'s five `WORK_*` constants (the `ConstantValue:` attribute is what proves `const val` rather than `@JvmField val`), `javap -p` for every other row.
- e. Record that `compileFreeDebugUnitTestJavaWithJavac` and `compilePlayDebugUnitTestJavaWithJavac` are now `NO-SOURCE` — the module's last Java is gone and `-Xlint:all -Werror` no longer covers anything here (future-work item 3's recurrence).

**Step 7 — Documentation.**

Update `net/download/service-interface/README.md` per D19 (both bullets, phrased as long-term-stable conventions with no milestone provenance). Append **item 14** to `tasks/antennapod-model-kotlin-future-work.md` per D5, and add this module's instance to existing **item 3**. Do **not** touch items 6, 7 or 8, and do **not** touch `config/checkstyle/suppressions.xml` (Research confirmed it names nothing in this module, so this milestone creates no orphaned-suppression follow-up). Fill in this task file's Implementation Notes and update `features/antennapod-net-download-service-interface-kotlin-milestone-13.checkpoint.md`.

### File Scope

The reviewer rejects any diff touching a file not on this list.

**Renamed `.java` → `.kt`** (`git mv` + rewrite; every file stays in its current directory, no package change, no new source set) — all under `net/download/service-interface/src/test/java/de/danoeh/antennapod/net/download/serviceinterface/`:

1. `FilenameGeneratorTest.java` → `.kt`
2. `FileNameGeneratorCharacterizationTest.java` → `.kt`
3. `DownloadRequestBuilderCharacterizationTest.java` → `.kt`
4. `DownloadRequestCreatorTest.java` → `.kt`
5. `DownloadRequestBuilderTest.java` → `.kt`
6. `DownloadServiceInterfaceTest.java` → `.kt`

**Total: 6 files renamed. No file is held back in Java** (D2) — after this milestone the module's test source set is 0 `.java` / 6 `.kt`.

**Modified:**
- `net/download/service-interface/README.md` (Step 7 only, two bullets — D19)
- `tasks/antennapod-model-kotlin-future-work.md` (Step 7 only: new item 14, and the `:net:download:service-interface` instance appended to item 3 — D5)
- `tasks/antennapod-net-download-service-interface-kotlin-milestone-13.md`
- `features/antennapod-net-download-service-interface-kotlin-milestone-13.checkpoint.md`

**Not in scope — a diff touching any of these means the plan was wrong and the task is re-planned, not patched:** `net/download/service-interface/build.gradle` (D20), anything under `net/download/service-interface/src/main/` (test-only milestone — in particular `AutoDownloadManager.kt` and `DownloadServiceInterface.kt`, whose signatures D3 and D6 explicitly forbid touching), `common.gradle`, root `build.gradle`, `settings.gradle`, `playFlavor.gradle`, `gradle/libs.versions.toml`, `.editorconfig`, `config/checkstyle/suppressions.xml`, `config/spotbugs/exclude.xml`, `.github/`, and any file in any other module (`app/`, `model/`, `storage/*/`, `net/*/` other than this one, `ui/*/`, `playback/*/`, `parser/*/`, `system/`). The `assertlines.pl` audit script lives in the session scratchpad and is **not** committed.

**Production-code escape valve (narrow).** If a genuine J2K-interop issue forces a minimal production `.kt` fix, it **stops the step** and is disclosed under the module's established deviation-disclosure pattern (M4/M6/M8/M10). It is never absorbed silently, and File Scope is not expanded to accommodate it without a re-plan. Research found no case that forces one, and D3/D6 pre-empt the two that would otherwise have been tempting.

### Acceptance Criteria

Track: `kotlin` (test source set), `:net:download:service-interface` module. Every item is checked against the Step 1 baseline in Implementation Notes.

**Characterization tests pass BEFORE the conversion step — pin current behavior**
- [ ] **AC1** — Step 1 records, against the unconverted Java sources, **two separate `./gradlew … --rerun` invocations** (`testFreeDebugUnitTest`, then `testPlayDebugUnitTest`), each BUILD SUCCESSFUL with **54 tests, 0 failures, 0 errors, 0 skipped**, and each accompanied by its `N actionable tasks: M executed` line and the before/after mtime of its `build/test-results/<task>/` directory. A single combined two-task invocation does **not** satisfy this criterion (Research Risk 12). The 6-row per-class table is pasted for both flavors.

**Characterization tests pass AFTER the conversion step — the equivalence proof**
- [ ] **AC2** — Both flavors are green, as **two separate `--rerun` invocations each time**, at the end of every one of Steps 2, 3, 4, 5 and again at Step 6: **54 tests, 0 failures, 0 errors, 0 skipped** on each flavor, every time. The `M executed` evidence is recorded at each step. No site required a `.toLong()`, an `L` suffix, or an `assertTrue(a == b)` relaxation to make an integer-literal assertion resolve (D22).
- [ ] **AC3** — The post-conversion per-class test count matches AC1 **row for row, on both flavors**, not merely in aggregate: `DownloadRequestCreatorTest` 13, `DownloadServiceInterfaceTest` 11, `DownloadRequestBuilderCharacterizationTest` 11, `FilenameGeneratorTest` 9, `FileNameGeneratorCharacterizationTest` 6, `DownloadRequestBuilderTest` 4.
- [ ] **AC4** — **No test is added, removed, renamed, split, merged, or moved between classes.** The Java-vs-Kotlin diff of the test-method-name list is empty across all 54 tests. The three underscore names `parcelInArrayListTest_WithAuth`/`_NoAuth`/`_MixAuth` survive byte-for-byte, and no backtick-quoted sentence-style name appears anywhere (D14).

**Assertion-content equivalence**
- [ ] **AC5** — The per-file assertion count (`grep -cE '\bassert[A-Z][A-Za-z]*\(' <file>`) is identical before and after for all 6 files: `DownloadServiceInterfaceTest` 31, `DownloadRequestCreatorTest` 22, `FilenameGeneratorTest` 13, `DownloadRequestBuilderCharacterizationTest` 11, `FileNameGeneratorCharacterizationTest` 8, `DownloadRequestBuilderTest` 6 — total **91**. Necessary but **not sufficient** on its own; see AC6.
- [ ] **AC6** — The D15 assertion-content diff residual is **empty for all 6 files**, with no disclosed exceptions. Evidence in Implementation Notes: Step 1's extractor validation (6/6 line counts matching AC5, and the six `assertThrows` sites confirmed to canonicalize), the per-step per-file diffs, and Step 6(c)'s consolidated 6-row `file → residual count` table reading `0` on every row. **The reviewer re-runs the exact Step 1 command and confirms it reproduces** — this criterion is not satisfied by the developer stating that they checked. Any residual is recorded verbatim with a one-line pure-syntax justification and accepted or rejected individually; a residual that alters an expected-value literal, swaps expected/actual order, or changes the assertion function fails this criterion outright.
- [ ] **AC7** — The four silent-idiomization hazards the AC6 extractor structurally cannot see are pinned by direct inspection of the converted files:
  - `FileNameGeneratorCharacterizationTest.kt` contains `Integer.toHexString((b.toInt() and 0xFF) or 0x100).substring(1, 3)` and `input.toByteArray(StandardCharsets.UTF_8)`; `grep -cE 'toString\(16\)|String\.format|toUByte|0xFF\.toByte|toByteArray\(\)'` over the test source set → **0** (D13).
  - `DownloadRequestBuilderTest.kt` contains `run {` used as a **statement** assigning the pre-declared `toParcel`, the `// test DownloadRequests to parcel` comment is still present inside it, `toParcel` is not renamed, and `getParcelableArrayList<DownloadRequest>("r")` carries its explicit type argument (D10).
  - `FilenameGeneratorTest.kt` still calls `TextUtils.isEmpty(result)` (D8) and the three reversed `assertEquals(result, "abc abc")` sites are byte-identical to the Java (D8) — checked by eye against `git show 939659e57:…/FilenameGeneratorTest.java`, since AC6 canonicalizes argument text but not argument *order*.
  - `grep -rn 'listOf(\|mapOf(\|mutableMapOf(' net/download/service-interface/src/test/` → **0**: no collection construction was idiomized (D6).

**Interop constraints the conversion must not resolve by editing production code**
- [ ] **AC8** — `grep -c 'HashMap<String?, DownloadStatus>()' …/DownloadServiceInterfaceTest.kt` → **7**, and `git diff 939659e57 -- net/download/service-interface/src/main/` is **empty**. In particular `DownloadServiceInterface.kt:11` still reads `MutableMap<String?, DownloadStatus>` and was not widened (D6).
- [ ] **AC9** — `DownloadServiceInterfaceTest.kt`'s anonymous `AutoDownloadManager` returns `FutureTask<Void?> { null }` (or the one permitted `FutureTask<Void?>(Runnable { }, null)` fallback, disclosed). `grep -c 'CompletableFuture' net/download/service-interface/src/test/` → **0**, and `AutoDownloadManager.kt:18` still declares `Future<*>` (non-null) — covered by AC8's empty `src/main/` diff (D3). `testAutoDownloadManagerGetSetInstanceRoundTrip` still asserts `assertSame(manager, AutoDownloadManager.getInstance())` and nothing calls `autodownloadUndownloadedItems`.
- [ ] **AC10** — All six `FeedUpdateManager` overrides and both `AutoDownloadManager` overrides match their production declarations' nullability exactly, including `runOnceOrAsk(context: Context)` and `runOnceOrAsk(context: Context, feed: Feed?)`'s **non-null** first parameter (D3). Verified by the file compiling with no `@Suppress` and no cast.

**Idiomatic Kotlin target achieved, without behavior drift**
- [ ] **AC11** — `grep -rn '`' net/download/service-interface/src/test/` returns **zero** hits. The one hard-keyword identifier was renamed (`val` → `value` in `DownloadRequestBuilderTest.kt`), not backticked (D9). Research swept all 823 lines and found exactly one collision, so no backtick escape is legitimate in this module.
- [ ] **AC12** — All 50 getter→property rewrites landed and none over-applied (D7). Verified in both directions:
  - `grep -rohE '\.get(Destination|Source|LastModified|Arguments|Password|Title|DownloadUrl|Username|MimeType|Id|FeedfileType|FeedfileId)\(\)' net/download/service-interface/src/test/ | wc -l` → **0**. This regex matches exactly **50** occurrences in the Java originals (`DownloadRequestBuilderCharacterizationTest` 27, `DownloadRequestCreatorTest` 17, `DownloadRequestBuilderTest` 6, and **0** in the other three) — measured at planning time, so a non-zero result names precisely which site was missed.
  - The function-call side is intact and was **not** over-idiomized: `DownloadServiceInterfaceTest.kt` still shows `isDownloadingEpisode(` ×10, `isEpisodeQueued(` ×5, `getProgress(` ×5 and `getInstance()` ×4 — **24 call sites, zero property rewrites in that file** — and `FileNameGenerator.generateFileName(`, `DownloadRequestCreator.create(`, `DownloadServiceInterface.get()` and every `DownloadRequestBuilder` method remain calls.
  - The 21 **Java-library** zero-arg getters (`File.getName()` ×6, `getParentFile()` ×4, `getAbsolutePath()` ×3, `Instrumentation.getTargetContext()` ×2, `InstrumentationRegistry.getInstrumentation()` ×2, `Context.getCacheDir()` ×2, `getExternalCacheDir()` ×1, `getClassLoader()` ×1) are **not** covered by this criterion in either direction — both syntaxes compile and the D15 extractor canonicalizes them identically, so whichever form J2K emitted is accepted.
- [ ] **AC13** — `grep -rnE '\.toLong\(\)|\b[0-9]+L\b' net/download/service-interface/src/test/` returns **zero** hits. `createFeedItem` in `DownloadRequestBuilderTest.kt` declares `id: Long` and both call sites remain the unsuffixed `createFeedItem(1)` / `createFeedItem(2)` (D11).
- [ ] **AC14** — `DownloadRequestBuilderCharacterizationTest.kt:` retains `assertThrows(NullPointerException::class.java, builder::build)` as a callable reference; if the Step 3 empirical check forced `{ builder.build() }`, that fact and its D15 residual are disclosed under Step 3 (D16).
- [ ] **AC15** — `grep -rn '!!' net/download/service-interface/src/test/` returns **zero** hits. In a pure test conversion there is no justified `!!`, and specifically none was added at `FilenameGeneratorTest.kt`'s `getExternalCacheDir()` (D16). No local is given an explicit type annotation that collapses a platform type.

**Toolchain gates**
- [ ] **AC16** — `./gradlew :net:download:service-interface:ktlintCheck` is BUILD SUCCESSFUL with `ktlintTestSourceSetCheck` genuinely **executing** (not `SKIPPED`/`NO-SOURCE`), with **zero** `@Suppress("ktlint:…")` annotations added, no `ktlint_disabled_rules`, no `.editorconfig` change, and no ktlint filter or exclusion in any build file (D21). Research Risk 8 is settled in writing: Implementation Notes records, from the Step 4 run, that the three underscore test names passed unchanged — observed on this module, not inherited from M7.
- [ ] **AC17** — `./gradlew checkstyle lint` is BUILD SUCCESSFUL, and Research Unknown 8 is settled in writing: Implementation Notes records whether Android Lint reported anything against the new `.kt` test files, observed at Step 2 rather than assumed from `lint.checkTestSources` being unset.
- [ ] **AC18** — `find net/download/service-interface/src/test -name '*.java'` returns **nothing** and `find … -name '*.kt' | wc -l` returns **6** (D2). `compileFreeDebugUnitTestJavaWithJavac` and `compilePlayDebugUnitTestJavaWithJavac` are now `NO-SOURCE`, and that consequence — `-Xlint:all -Werror` no longer covers anything in this module — is recorded in Implementation Notes and in future-work item 3.

**No public API break, and the interop contract re-proven**
- [ ] **AC19** — The README #1/#2/#2b interop shapes are re-proven mechanically (D4): Step 6(d)'s `javap` output is pasted and shows
  - from **`javap -v`**: the five `WORK_*` as `public static final java.lang.String` **on `DownloadServiceInterface` itself, not on `Companion`**, each with its `ConstantValue:` attribute — the part that proves `const val` specifically rather than merely "a real static field" (D2's correction). A missing `ConstantValue:` is **recorded, not failed**, per D4;
  - from `javap -p`: `get()`/`setImpl` on `DownloadServiceInterface`, `getInstance()`/`setInstance` on both `FeedUpdateManager` and `AutoDownloadManager`, and both `DownloadRequestCreator.create` overloads all `public static`; and `FileNameGenerator.generateFileName` `public static` with `MAX_FILENAME_LENGTH` a `public static final int`.

  This is the language-independent replacement for the source-level coverage `testWorkConstants` and the `@JvmStatic` call sites stop providing.
- [ ] **AC20** — No public API break visible to Java callers outside the module. `./gradlew :app:assembleDebug` is green, compiling both flavors and every consuming module — including `DownloadServiceInterfaceImpl.java`'s unqualified inherited `WORK_*` reads, which are the live external guard D2 relies on — with zero edits outside `net/download/service-interface/src/test/` and the four doc files.

**Scope and documentation**
- [ ] **AC21** — `git diff --name-only 939659e57` lists only files from the File Scope list. In particular `net/download/service-interface/build.gradle` is unchanged (D20), no file under `src/main/` appears, `config/checkstyle/suppressions.xml` is unchanged, no file outside this module and the four doc paths appears, and `assertlines.pl` was not committed.
- [ ] **AC22** — `net/download/service-interface/README.md`'s three affected bullets are updated (D19), all phrased as long-term-stable module conventions with **no milestone number, no task-file reference, and no task-specific detail** (`AGENTS.md`): the "tests are Java by design" bullet is replaced and states plainly that the module's tests now prove the `WORK_*` constants' values but not their shape, names `javap` as the check for the shape, and records that `FileNameGenerator.generateFileName`'s `@JvmStatic` has no remaining Java caller; the `--rerun` bullet carries the two-separate-invocations correction; and the `const val` bullet keeps its "these stay `const val`" instruction while dropping the false justification — `grep -cE 'not plain `val` or `@JvmField val`|Only `const val` emits' net/download/service-interface/README.md` → **0**, with the replacement text naming a plain `val` as the regression the unqualified inherited reads catch and `ConstantValue`/call-site inlining as the reason to prefer `const val` over `@JvmField val`. The other three bullets are byte-for-byte unchanged. `tasks/antennapod-model-kotlin-future-work.md` gains item 14 (D5) and item 3 gains this module's instance; items 6, 7 and 8 are unchanged.

**Not applicable to this module, asserted rather than assumed.** Accessibility (content descriptions, dynamic type), dark mode / hardcoded colors, RTL, Paparazzi snapshots, instrumented back-stack and deep-link tests, SDUI contract versions, analytics, and HSHD handling are all inapplicable: `:net:download:service-interface` has no UI, no resources, no layouts, no `AndroidManifest` UI surface, no navigation, and handles no personal or payment data; the module has no `androidTest` source set and this milestone adds none. Only the `kotlin` track is in flight — no `compose` or `navigation` work is requested or performed, so the accessibility and snapshot criteria those tracks carry do not attach here.

### Milestone

**Milestone 13 — `:net:download:service-interface` module, `kotlin` track (test source set).** Single milestone, single unified PR (code + spec docs together, per the standing instruction and the M7/M9/M10/M12 precedent), four hazard-clustered conversion commits mapping to Steps 2–5, plus a verification/docs commit for Steps 6–7 (Step 1 produces no repo diff, so it folds into Step 2's commit).

Follows Milestone 10 (`:net:download:service-interface` production code, PR #16, merged) and Milestones 1–12. On completion the `kotlin` track for this module is **closed**: production 7/7 Kotlin, tests 6/6 Kotlin, zero `.java` files remaining in the module. No further `:net:download:service-interface` track is in flight.

This is unaffiliated OSS portfolio work, so "milestone" is case-study narrative structure, not invoicing. The case-study angle it earns is the counterpart to Milestone 9's: **`:event` kept 3 of 22 test files in Java because converting them would have produced passing tautologies; this module converts all 6, and the difference is not appetite — it is that the same rule, applied to a file where 10 of 11 tests are language-independent and whose one Java-only proof is backed by a production Java subclass reading the constants unqualified, points the other way.** The `javap` re-proof (AC19) and the measured 87 external call sites are what make that defensible rather than merely asserted, and stating both outcomes from one rule is a stronger consulting artifact than either alone.

### Out of Scope

- **Holding any test file back in Java** (D2). Not deferred — decided against, with the reasoning recorded. Revisiting requires a new task and a new argument, not a follow-up milestone.
- **Splitting `DownloadServiceInterfaceTest`** to relocate `testWorkConstants` into a Java interop file, and **adding any compensating test** — a `WorkConstantsInteropTest.java`, a reflection-based shape test, or a new Java canary class (D2, D4). The `javap` acceptance criterion replaces them at zero file cost.
- **Removing `@JvmStatic` from `FileNameGenerator.generateFileName`, or `@VisibleForTesting` from `MAX_FILENAME_LENGTH`,** now that neither has a Java caller (D5). Recorded as future-work item 14; acting on it is a production edit.
- **Any production code change.** No file under `net/download/service-interface/src/main/` is modified. Specifically excluded and explicitly tempting: widening `AutoDownloadManager.autodownloadUndownloadedItems`'s return to `Future<*>?` (D3) and widening `DownloadServiceInterface.setCurrentDownloads`'s parameter to `MutableMap<String, DownloadStatus>` (D6). Both are resolved in the test.
- **`net/download/service-interface/build.gradle` changes of any kind** (D20), including adding `kotlin.test`, `mockito-kotlin`, or any new test dependency — and including removing the vestigial `java-test-fixtures` plugin, which stays filed as future-work item 8.
- **Repo-wide build-policy changes:** `allWarningsAsErrors` on Kotlin test-compile tasks (item 3), the flavor-aware SpotBugs gate (item 7), and cleaning orphaned checkstyle suppressions (item 5). All stay deferred; this milestone only annotates item 3 with this module's instance.
- **The `TextUtils.isEmpty` → `isEmpty()` swap** in `FilenameGeneratorTest` or anywhere else (D8). Future-work item 6 already records it as deliberately deferred for this module.
- **"Fixing" the three reversed `assertEquals` argument orders** in `FilenameGeneratorTest` (D8), or any other correctness-neutral improvement to an upstream test.
- **Changing assertion libraries or styles.** No `kotlin.test`, no AssertJ, no Truth, no `assertThrows` ↔ `@Test(expected=)` swaps, no hamcrest.
- **Deduplicating, restructuring, parameterizing, or "improving" any test.** The two near-duplicate `createFeed` helpers stay duplicated across their two files (D18). No shared base class, no extracted fixtures, no `src/testFixtures` source set, no new test cases. Every improvement spotted during conversion goes to `tasks/antennapod-model-kotlin-future-work.md`, not into this diff.
- **Tightening test-helper visibility to `internal`** now the suite is Kotlin. Cosmetic only, and mixing a name-mangling risk into this diff is exactly the wrong trade.
- **Every other track.** No `gradle-kts` (`net/download/service-interface/build.gradle` stays Groovy and is untouched), no `di`, no `concurrency`, no `compose`, no `navigation`. Nothing in this module is a ViewModel, View, navigation entry, or threading construct, and no target for any of those tracks is assumed.
- **Any architecture work** — no MVVM, no further modularization, no replacement of the singleton-registration pattern, no hardening of the deliberately-aliased `setCurrentDownloads` contract.
- **Converting test sources in any other module.** `:net:sync:service-interface`'s and `:net:sync:service`'s Java suites remain queued behind their own trigger conditions; `:storage:database`, `:parser:feed` and the rest are separately-scoped work.

## Open Questions
_Last updated by: legacy-android-planner | 2026-08-04_

None of these block implementation. Steps 1–7 proceed as written regardless of how they are answered.

**OQ1 — Should the `javap` interop proof become an automated CI check rather than a per-milestone manual acceptance criterion?** (Raised by D4; carried forward from `:event` Milestone 9's own OQ2, which asked the same question and did not get an answer.)

AC19 re-runs the shape proof by hand at this milestone's boundary. That is a snapshot, not a regression guard: nothing prevents a future PR from dropping a `@JvmStatic` or demoting a `const val` to `val` and having CI pass, provided the Java caller is removed in the same commit. This milestone is the second module where a source-level guard has been traded for a one-shot `javap` check, so the pattern is now recurring rather than incidental, and a small Gradle verification task or a `javap`-diffing test would close it permanently for every module at once. Out of scope here — new tooling, new File Scope, and arguably a shared-agent-definition convention rather than a repo one. Worth deciding before the next module's test suite converts.

**OQ2 — What guards README convention #1 once `:net:download:service` itself converts to Kotlin?** (Raised by D2.)

D2's residual guard for the `WORK_*` `const val` shape is `DownloadServiceInterfaceImpl.java:58,78,99` — a Java subclass reading the constants unqualified via inheritance, compiled by `:app:assembleDebug`. That guard is structurally strong today and cannot be deleted without deleting the download service implementation. It disappears entirely the day `:net:download:service` is migrated to Kotlin, which is a plausible future milestone in this same case study. At that point the five constants' shape has **no** guard anywhere: not in this module's tests (Kotlin, per D2), not in the consumer (Kotlin by then), and not in `:app` (whose `MainActivity.java`/`EpisodeDownloadWorker.java` reads are qualified, and so pass under `val` as well as `const val`).

The question is which way to close it: adopt OQ1's automated check before that milestone is scheduled, or make "does converting this module orphan another module's interop convention?" an explicit Research question in the pipeline's researcher definition. Not actionable today — the guard is intact — but it should be decided *before* the `:net:download:service` migration is planned, not discovered during it. **Flagged now specifically because that milestone would otherwise inherit the problem silently.**

**OQ3 — Is the `-Werror` coverage gap now large enough to act on?** (Future-work item 3, fifth recurrence.)

After AC18 this module has zero Java files, so `-Xlint:all -Werror` covers nothing here at all — production or test. The same is true of `:model` and `:net:sync:service-interface`; `:event` and `:net:sync:service` retain partial coverage only because they deliberately kept Java files. Item 3 has been logged and re-logged across five milestones without ever being taken, and each conversion widens it. Concretely relevant to *this* milestone only in that it removes the safety net from any unchecked-cast or platform-type workaround — and this plan authorizes none (AC15), so the exposure here is theoretical. Tracked repo-wide; not acted on. Worth asking whether "logged again" is still the right answer at the sixth recurrence.

**OQ4 — Upstreaming intent.** Standing, carried unchanged from M7/M9/M11/M12 (`tasks/antennapod-model-kotlin-future-work.md` item 2). Whether any of this work is offered upstream to AntennaPod affects how conservative the interop posture needs to stay long-term: a fully-Kotlin downstream fork could eventually drop `@JvmStatic` and `const val` entirely (which would make D5's finding actionable and OQ2 moot), whereas an upstream contribution cannot. It also shapes how D8's preserved reversed-`assertEquals` calls read to an upstream reviewer, who will see three transposed assertions carried faithfully into Kotlin and may well ask why. Unanswered across five milestones without ever blocking one. **Commercial/strategic — for José**, per root `CLAUDE.md`'s commercial-implications rule; not an agent decision.

---

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-08-04 | Loop 1 of max 2_

### Verdict
CHALLENGE

### Verification performed this loop

Read the Research and Plan sections in full, then verified — against live source and, where the claim was about Kotlin *language* behavior rather than this repo's content, against a real Kotlin compiler (`kotlinc-jvm 1.6.21`, bundled with an installed Android Studio; the repo itself declares Kotlin 2.3.20 but bitwise-operator and definite-assignment/contract semantics on primitives have been stable stdlib/language behavior since Kotlin 1.1/1.3 respectively, so a 1.6.21 compiler is a valid oracle for these specific claims) — rather than re-reading the Plan's own citations as proof of themselves:

- **D2's central scoping claim.** Read `DownloadServiceInterfaceImpl.java` directly: confirmed lines 58, 78, 99 read `WORK_DATA_MEDIA_ID`, `WORK_TAG_EPISODE_URL`, `WORK_TAG` unqualified, and the class declaration (`:26`) extends `DownloadServiceInterface`. Read `DownloadServiceInterface.kt`: confirmed the five `WORK_*` are `const val` inside a `companion object` (`:46-50`). Confirmed `minSdk 23` in `common.gradle:5`. Confirmed repo-wide that no other file (test or production) asserts any of the five literal string values (`grep` for all five string literals found only the declaration and the one test).
- **D3's `FutureTask` substitution.** Read `AutoDownloadManager.kt`: confirmed `autodownloadUndownloadedItems(context: Context?): Future<*>` is non-null. Read `DownloadServiceInterfaceTest.java:177-186`: confirmed the anonymous subclass never calls `autodownloadUndownloadedItems` (only overrides it) and the test's only assertion is `assertSame(manager, AutoDownloadManager.getInstance())`, matching the Plan's claim exactly.
- **D13's `md5Suffix` byte arithmetic.** Read the live method body and hand-derived, then compiled and ran, both the authorized expression and each "forbidden alternative" against a representative negative byte (`b = -1`, i.e. `0xFF`).
- **D10's `run { }` claim.** Read the live bare-initializer-block source (`DownloadRequestBuilderTest.java:63-81`) and compiled the structural equivalent of both the "naive" block-assignment form the Plan claims fails and the authorized initializer-expression form.
- **Measured counts.** Recomputed all of AC1/AC3/AC5/AC12's numbers directly from live source (`grep -c`, `@Test` counts) rather than trusting the Plan's table.
- **File Scope.** Re-read every Step for an implied production edit; found none — D3/D6/D20 correctly forbid the two tempting production touches.

Three of the verification targets surfaced findings; two of the five (D2's `DownloadServiceInterfaceImpl` shape and D3's `FutureTask` substitution) checked out exactly as claimed and are not repeated as concerns below.

### Concerns

- **Severity:** MAJOR
- **Class:** Silent behavior change from mechanical translation / unproven "does not compile" claim (checklist #2, #7); minimal-diff hard-rule risk
- **Concern:** D10 asserts as verified fact that J2K's typical emission for `DownloadRequestBuilderTest.java:66-81`'s bare initializer block — `run { … ; toParcel = ArrayList() }` used as a **statement**, assigning a `val` declared just above it — does not compile, in either the `val` or the `var` form, and prints two fabricated compiler errors ("captured values initialization is forbidden", "variable 'toParcel' must be initialized") to justify mandating a more invasive rewrite: `run` used as an **initializer expression** (`val toParcel: ArrayList<DownloadRequest> = run { … }`), which requires renaming the block's inner list local (`toParcel` → `list`) to avoid a shadowing warning and restructuring the block's control flow relative to the Java original. This is also a reversal of Research's own, correctly-stated finding one section earlier ("J2K typically emits `run { … }`, which is correct" — Research, Track-specific findings). I compiled the exact structural equivalent of the "naive" form the Plan claims fails — a `val` declared above a `run { }` block that assigns it once inside the block body, matching this file's shape precisely — with a real Kotlin compiler, for both `val` and `var`. Both compiled and ran cleanly with **zero errors**. This is expected Kotlin behavior, not a fluke: `kotlin.run` is an `inline` function carrying a `contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }` (stable since Kotlin 1.3), which is precisely the annotation that lets Kotlin's definite-assignment analysis see through the lambda body and treat a `val` assigned exactly once inside it as validly initialized — the opposite of what D10 claims ("Kotlin's definite-assignment analysis does not look inside a lambda body"). The mandated rewrite is not wrong Kotlin — it compiles and is behaviorally correct — but it is unforced additional diff surface (a variable rename, a restructured assignment) justified by a compiler error that does not occur, and `AGENTS.md` is explicit and repeatedly emphatic that diffs must be the "absolute minimum," with no reordering or reorganizing "unless asked for by the user." A plan that fabricates a compile failure to justify exceeding that rule is exactly the class of unverified technical claim this review exists to catch, even though — unusually for this checklist — the downstream code that ships is still correct.
- **Evidence:** `tasks/antennapod-net-download-service-interface-kotlin-milestone-13.md` D10 (lines ~387-398, the two `// error:` comments and "Kotlin's definite-assignment analysis does not look inside a lambda body, in either the `val` or the `var` form") versus `net/download/service-interface/src/test/java/de/danoeh/antennapod/net/download/serviceinterface/DownloadRequestBuilderTest.java:66-81` (the live shape) and Research's own contradicting statement (Research, Track-specific findings §"Static-helper translation choices"... actually §ordering — see the "bare initializer block" Risk 5 entry: "J2K typically emits `run { … }`, which is correct"). Verified independently by compiling `val toParcel: ArrayList<Int>; run { toParcel = ArrayList(); toParcel.add(1) }` and the `var` equivalent with `kotlinc-jvm 1.6.21` — both compiled and printed `[1]` with zero diagnostics.
- **Suggested mitigation:** Correct D10 to state that the plain `run { … }` block-assignment form (Research's original claim) does in fact compile, because `run` carries an `EXACTLY_ONCE` contract. Then make an honest choice between: (a) accepting the simpler, smaller-diff form Research originally described (preserves the Java structure almost verbatim, no rename needed), which is more consistent with `AGENTS.md`'s minimal-diff mandate; or (b) keeping the initializer-expression form if there is a real reason to prefer it (e.g. forcing `val` over `var`, or the reviewer finding the expression form clearer), but justify that choice honestly as a style preference, not as the only form that compiles. Either way, remove the two fabricated `// error:` lines from the plan text before this is presented as verified.

- **Severity:** MINOR
- **Class:** Unproven "compiles and silently changes" claim (checklist #7) — no delivered-code risk, since AC7 forbids all listed forms unconditionally regardless of the reasoning's accuracy
- **Concern:** D13's list of "forbidden alternatives" for `md5Suffix`'s byte-to-hex arithmetic contains two claims that do not hold up under direct compilation/computation, even though the *authorized* expression (`(b.toInt() and 0xFF) or 0x100`) is correct and bit-identical to the Java original (verified by hand and by compiling and running it for `b = -1`, giving `"ff"` on both sides, matching the Java `(b & 0xFF) | 0x100` computation exactly). (1) `(b and 0xFF.toByte())` is claimed to "compile and silently change the expected hash" — it does not compile at all. Kotlin's `Byte` type has no `and`/`or` infix operators defined anywhere in the standard library (only `Int` and `Long` do); compiling this expression with a real Kotlin compiler fails with `unresolved reference`, the only candidate the compiler offers being an unrelated `BigInteger.and`. This is a loud compile-time failure, not the silent hazard D13 describes. (2) `b.toUByte().toInt()` is claimed to "change the promotion" and, by implication, the resulting hash — but when substituted into the same formula (`(b.toUByte().toInt() and 0xFF) or 0x100`), it computes the **identical** result to the authorized form for every byte value, including `b = -1`: verified by compiling and running both side by side (`"ff"` == `"ff"`). This is mathematically forced, not a coincidence of the one input tested — masking with `and 0xFF` erases any difference sign-extension (`toInt()`) versus zero-extension (`toUByte().toInt()`) could have introduced, since only the low 8 bits survive the mask either way. Neither error changes what ships: AC7's `grep` (`toUByte|0xFF\.toByte|...`) forbids both forms unconditionally, independent of whether the stated reasoning for forbidding them is correct, so no wrong hash can reach the diff. The concern is narrower: D13 presents unverified compiler/arithmetic behavior as settled fact in a document whose sole value proposition is "verification is the value," and two of its four supporting claims do not survive a five-minute check.
- **Evidence:** `tasks/antennapod-net-download-service-interface-kotlin-milestone-13.md` D13 ("Forbidden alternatives, each of which compiles and silently changes the expected hash: `(b and 0xFF.toByte())` ... `b.toUByte().toInt()` (changes the promotion)") versus `net/download/service-interface/src/test/java/de/danoeh/antennapod/net/download/serviceinterface/FileNameGeneratorCharacterizationTest.java:24-32` (live `md5Suffix` body). Verified by compiling `(b and 0xFF.toByte())` standalone (fails: `error: unresolved reference`) and `(b.toUByte().toInt() and 0xFF) or 0x100` end-to-end against `Integer.toHexString(...).substring(1,3)` for `b: Byte = -1` (succeeds, produces `"ff"`, identical to the authorized form's output).
- **Suggested mitigation:** Correct D13's two mischaracterized alternatives: state that `(b and 0xFF.toByte())` is rejected because it is a compile error (Byte has no bitwise operators in Kotlin), not because it silently changes behavior, and either drop `b.toUByte().toInt()` from the forbidden list (it is behaviorally equivalent to the authorized form when combined with the subsequent `and 0xFF`) or, if it stays forbidden for a *style* reason (introducing an unrelated experimental type for no gain), say so rather than claiming it changes the hash. AC7's grep line does not need to change.

- **Severity:** MINOR
- **Class:** Unproven "only const val produces this shape" claim (checklist #2, #7) — no practical consequence, since `const val` and `@JvmField val` are functionally interchangeable for this module's purposes
- **Concern:** D2 argues the residual guard for README convention #1 is `DownloadServiceInterfaceImpl.java`'s unqualified inherited reads of the `WORK_*` constants, and states this is "the exact shape only `const val` produces (a plain `val` forces `Companion.getWORK_TAG()`; even `@JvmField` cannot be inherited unqualified from a companion)." The parenthetical's second clause is false. I compiled a minimal reproduction: a Kotlin `abstract class` with a `companion object` declaring one `const val`, one `@JvmField val`, and one plain `val`, then compiled a Java subclass reading each unqualified via `javac` against the pre-built Kotlin class. The `const val` and the `@JvmField val` reads **both** compiled cleanly; only the plain `val` read failed (`error: PLAIN_TAG has private access in Base`). `javap -p` on the compiled Kotlin class confirms why: `@JvmField val` in a companion object produces `public static final java.lang.String JVMFIELD_TAG;` on the *outer* class — byte-for-byte the same externally visible shape as `const val`'s `public static final java.lang.String CONST_TAG;` — while the plain `val` produces only a `private static final` field plus a `Companion` accessor. This means (a) `DownloadServiceInterfaceImpl`'s unqualified reads prove "this is a real static field, not a Companion-routed property" — i.e. distinguish `{const val, @JvmField val}` from `{plain val}` — but do **not** distinguish `const val` from `@JvmField val` specifically, contrary to the "only const val" framing; and (b) D4's `javap -p` re-proof (AC19), which checks only for the string `public static final java.lang.String WORK_TAG;`, inherits the same blind spot — it would not catch a hypothetical future regression from `const val` to `@JvmField val` either, even though D2/D19 describe the `javap` check as re-proving "the shape `testWorkConstants` stops proving." In practice this has no behavioral consequence: `const val` and `@JvmField val` are functionally interchangeable as read-only String tags for every consumer in this codebase (WorkManager tags, external Java reads, `:app:assembleDebug`), so the scoping decision itself (D2: convert all six files) is unaffected — this is a precision defect in the stated reasoning, not a gap in what actually gets protected.
- **Evidence:** `tasks/antennapod-net-download-service-interface-kotlin-milestone-13.md` D2 ("even `@JvmField` cannot be inherited unqualified from a companion"). Verified with a standalone reproduction: Kotlin `companion object { const val CONST_TAG = "const"; @JvmField val JVMFIELD_TAG = "jvmfield"; val PLAIN_TAG = "plain" }` compiled with `kotlinc-jvm`, then a Java subclass reading `CONST_TAG` and `JVMFIELD_TAG` unqualified compiled cleanly with `javac` against the built class; a second Java subclass reading `PLAIN_TAG` unqualified failed with `PLAIN_TAG has private access in Base`. `javap -p` on the compiled class shows `public static final java.lang.String CONST_TAG;` and `public static final java.lang.String JVMFIELD_TAG;` side by side, versus `private static final java.lang.String PLAIN_TAG;`.
- **Suggested mitigation:** Correct D2's parenthetical to "a plain `val` forces `Companion.getWORK_TAG()`; `@JvmField val` produces the identical externally-visible static-field shape as `const val`, so the distinction this guard actually proves is 'real static field vs. Companion-routed property,' not 'specifically `const val`.'" Optionally note in D4/AC19 that the `javap` re-proof carries the same scope (proves "real static field," not "specifically `const val`") — worth one honest sentence, since it does not change AC19's pass/fail outcome or require a different `javap` invocation.

### Checklist categories considered and dismissed

- **Characterization tests prove equivalence, not just existence** — considered; not a concern. The 54 tests were already the module's characterization layer before this milestone (Milestone 10's work), and D15's assertion-content diff (empty-residual requirement, per-file, re-run at Step 6 in one sweep) is a genuine mechanical equivalence check, not merely an existence check. Confirmed the diff mechanism's canonicalization rules against the two new-for-this-module cases it names (`.isX()` normalization, `[n]` normalization) by re-reading the actual call sites they're meant to normalize (`DownloadRequestCreatorTest:57,103`; `DownloadRequestBuilderTest:100-104`) and both are real.
- **Public API breakage** — considered in depth; none found. File Scope is genuinely test-only; D3 and D6 explicitly and correctly forbid the two production edits that would have been tempting (widening `AutoDownloadManager`'s return type, widening `DownloadServiceInterface.setCurrentDownloads`'s parameter), and no Step implies a production `.kt` edit. `git diff --name-only` after implementation is the AC21 check for this, correctly specified.
- **Coverage gaps left unaddressed** — considered; correctly disclosed rather than unaddressed. The `FileNameGenerator` JVM-static loss (D5) and the `testWorkConstants`/`@JvmStatic`-accessor shape loss (D2, D4) are both quantified, both have an explicit compensating mechanism (`javap -p` at Step 6) or an explicit accept-and-file-as-future-work decision, and neither is silently dropped.
- **`di`/`concurrency`/`compose`/`navigation` tracks** — correctly not assessed; module has no UI, no DI wiring, no threading construct in scope, and Out of Scope explicitly excludes all four.
- **`gradle-kts` track** — correctly not assessed; D20 explicitly excludes `build.gradle` from File Scope and the module's Groovy build file is untouched.
- **Milestone/scope creep** — considered; none found. File Scope is a fixed six-file rename list plus four doc files; Out of Scope explicitly forbids deduplicating the two near-duplicate `createFeed` helpers, "fixing" the reversed `assertEquals` calls, tightening visibility to `internal`, and every other tempting cleanup. Measured counts (91 assertions 31/22/13/11/8/6, 54 tests, 50 getter sites 27/17/6/0/0/0, 7 `HashMap` sites, 24 stay-function sites) were all independently recomputed against live source in this loop and match the Plan's table exactly — no arithmetic drift found.
- **Public-API-visible signature/behavior changes from J2K** — considered (D6's `MutableMap` invariance, D7's val/fun split, D11's `int`→`Long` widening, D18's nullable-parameter widening); all four independently verified against live production signatures and are accurate.

None of the three findings above touch File Scope, the Steps' ordering, or the Acceptance Criteria's pass/fail outcomes — all three are corrections to the *stated reasoning* inside already-correct Resolved Decisions (D2, D10, D13), fixable as in-place text edits. D10 is elevated to MAJOR rather than MINOR specifically because it currently directs the developer to exceed `AGENTS.md`'s minimal-diff hard rule on the strength of a compiler error that does not exist — that is a concrete, avoidable instruction to write a larger diff than necessary, not merely an inaccurate footnote. Awaiting a revision before loop 2.

## Plan — Revision 1 (responding to red-team loop 1)
_Last updated by: legacy-android-planner | 2026-08-04_

**All three findings accepted, and all three were independently re-verified before acceptance rather than taken on the report's word.** Two of them I could check against primary sources in this environment; one I could not, and that asymmetry is recorded in the Plan text itself rather than smoothed over.

All three are the same defect wearing three costumes, and it is worth naming plainly because it is the exact failure this pipeline sells against: **I asserted compiler behavior I had not executed, in a document whose entire value proposition is that claims get verified.** D10 invented two compiler error messages. D13 called a compile error a silent hazard and called an arithmetically-identical expression a behavior change. D2 asserted an exclusivity property of `const val` that `@JvmField val` also has. None of the three would have shipped wrong code — AC7, AC12 and AC19's greps constrain the diff regardless of why they were written — but D10 would have made the developer write a **larger diff than `AGENTS.md` permits**, which is a real cost, and the other two would have left three false technical claims in a case-study artifact.

### Verification I performed this loop

Not `kotlinc` — this environment has none, and the compiler jar in the Gradle cache is not reachable. Instead, this repo's **own** stdlib sources, which is a stronger oracle than red-team's `kotlinc-jvm 1.6.21` for version-sensitive questions since it is the exact artifact `:net:download:service-interface` compiles against (`kotlin-stdlib-2.3.20-sources.jar`, resolved from the Gradle cache):

- **Finding 1 (`run`) — confirmed.** `commonMain/kotlin/util/Standard.kt:41-46` declares `public inline fun <R> run(block: () -> R): R` with `contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }`. That contract is precisely what lets definite-assignment analysis see through the lambda, so a `val` declared above and assigned once inside compiles. My claim that "definite-assignment analysis does not look inside a lambda body" was wrong, and Research's original statement was right.
- **Finding 2 (`Byte` bitwise) — confirmed, both halves.** `Byte.and`/`or`/`inv` exist **only** at `commonMain/kotlin/experimental/bitwiseOperations.kt:11,16,26`, in package `kotlin.experimental` — not a default-imported package — while `Int.and` is a member (`commonMain/kotlin/Primitives.kt:1073`). So `(b and 0xFF.toByte())` is an unresolved reference, a compile error, not a silent hazard. And `UByte.toInt()` is literally `data.toInt() and 0xFF` (`commonMain/kotlin/UByte.kt:331`), which makes `b.toUByte().toInt() and 0xFF` reduce to `b.toInt() and 0xFF and 0xFF` — **identical for every byte value by construction**, not merely for the `b = -1` case red-team tested.
- **Finding 3 (`@JvmField` in a companion) — accepted, not independently confirmed.** I read the stdlib annotation (`@Target(FIELD)`, "expose it as a field") and Kotlin's documented interop rule that a companion property so annotated emits its backing field on the enclosing class; both agree with red-team's compiled reproduction. But I could not compile the reproduction myself, so **D2 now says so explicitly** rather than presenting it as verified by me. Given the finding I am correcting is "asserted unverified compiler behavior," recording provenance is part of the fix, not a hedge.

### Edits made, in place in the Plan above

**MAJOR — D10 now mandates the smaller diff Research originally described.**

1. **D10 rewritten.** The two fabricated `// error:` lines are gone. The authorized shape is now plain `run { … }` as a **statement**, assigning the `val` declared above it — the Java structure preserved almost verbatim: the assignment stays where the upstream author put it, the block comment stays on its first line, and **no local is renamed**. The `EXACTLY_ONCE` mechanism is stated with its file-and-line citation so it is not re-litigated at loop 2 or during review.
2. **The initializer-expression form is now explicitly rejected** on the honest ground red-team asked for — it compiles and is behaviorally identical, but it forces a rename and reorders the assignment for no gain, which minimal-diff forbids. That is a style judgement stated as one, not a compilation claim.
3. **AC7's D10 clause** already required `run {` plus the preserved comment and the explicit `getParcelableArrayList<DownloadRequest>` type argument, and is unchanged — it never referenced the rename, so it constrains the corrected form exactly as well as the old one.

**MINOR — D13's rejected alternatives now each carry their own true reason.**

4. **D13's flat "each of which compiles and silently changes the expected hash" list is replaced by a four-item list with per-item reasoning**: `(b and 0xFF.toByte())` rejected as a **compile error** (with the stdlib citation, and noting it still fails even *with* `import kotlin.experimental.and`, since `Byte.and` returns a `Byte` and the following `or 0x100` has no `Byte`/`Int` overload); `b.toUByte().toInt()` rejected on **style only**, explicitly recorded as arithmetically equivalent; `String.format("%02x", b)` kept as a genuine silent-change hazard (it formats the sign-extended `int`); `toString(16)` kept, carrying M9's D4 defect.
5. **AC7's grep is unchanged**, as red-team noted it should be — it forbids all four forms regardless of why, so no delivered-code risk existed and none is introduced.

**MINOR — D2's `const val` exclusivity claim corrected, and AC19 upgraded rather than merely caveated.**

6. **D2's parenthetical replaced** with a paragraph stating what the guard actually proves: unqualified inherited reads discriminate **{`const val`, `@JvmField val`} from {plain `val`}** — "a real static field, not a Companion-routed property" — and do not single out `const val`. D2's conclusion is unaffected and the reasoning now says why: the regression the guard catches (demotion to plain `val`) is the one the README already identifies as breaking every Java caller, and a `const val` → `@JvmField val` drift is inert for all five consumers, which are read-only `String` tags.
7. **AC19 and D4 upgraded, not just annotated.** Red-team's mitigation suggested one honest sentence disclosing the `javap -p` blind spot. Disclosing it is the weaker option when closing it costs one flag: the constants row now uses **`javap -v`** and requires the `ConstantValue:` attribute, which is exactly what distinguishes `const val` from `@JvmField val` (the former inlines into Java call sites; the latter does not). Every other row stays `javap -p`. **Step 6(d)** updated to match. A missing `ConstantValue:` is specified as **recorded, not failed** — this milestone touches no production code, so its absence would be a pre-existing property of `DownloadServiceInterface.kt`, and turning that into a failure would make AC19 a trap rather than a check.
8. **D19 and AC22 extended from two README bullets to three** — a consequence of finding 3 that the red-team did not trace. `net/download/service-interface/README.md:8` asserts the *same* false claim I did ("not plain `val` or `@JvmField val`… Only `const val` emits a `public static final` field on the outer class"). Correcting D2 while leaving the module's own durable instruction stating the falsehood would hand the next agent the premise this loop just removed. The bullet's operative instruction is kept — these stay `const val` — and only its justification changes. AC22's new clause is a `grep` for the two false phrases returning **0**, so it is checkable rather than a matter of reviewer taste. **File Scope is unaffected**: `README.md` was already the sole non-test production-tree file on the modified list.

### Counts and shape

**Unchanged:** all **7 Steps** and their order; the four-tier commit structure and its file assignments; **File Scope** in full — six renames plus four doc files, no file added or removed, and the corrections land inside files already scoped; **22 Acceptance Criteria**, none added or deleted, and none of AC1–AC18, AC20, AC21's pass/fail outcomes moved; the Milestone framing; Out of Scope; and **OQ1–OQ4** as written. D1, D3–D9, D11, D12, D14–D18, D20–D22 are untouched.

**Changed:** **D10** (rewritten — mandates a *smaller* diff than before), **D13** (four rejection reasons corrected), **D2** (one parenthetical replaced by a scoped paragraph plus a verification-provenance note), **D4** and **AC19** (`javap -v` + `ConstantValue` for the constants row; missing-attribute handling specified), **Step 6(d)** (matching `javap` invocations), **D19** and **AC22** (two README bullets → three).

**Net effect on the diff the developer will write:** strictly smaller. D10 was the only finding that reached delivered code, and its correction removes a variable rename and an assignment restructure from `DownloadRequestBuilderTest.kt`. Everything else changes what the Plan *claims*, not what gets written — which is the appropriate outcome for a loop whose findings were all about unverified reasoning rather than wrong decisions.

## Red-Team Verdict — Plan (Loop 2, Final)
_By: legacy-android-red-team | 2026-08-04 | Loop 2 of max 2 (final)_

### Verdict
CHALLENGE

### Verification performed this loop

This environment has no bare `kotlinc-jvm`, but it does have Android Studio's bundled `kotlin-compiler.jar` (`kotlinc-jvm 2.1.21`, JRE 21), which neither loop 1's red-team pass (which used a standalone `kotlinc-jvm 1.6.21`) nor Revision 1 (which said no compiler was reachable and fell back to reading this repo's own `kotlin-stdlib-2.3.20-sources.jar`) had used. I used it to independently re-derive all three of Revision 1's corrected claims from a live compiler and `javap`/`javac`, rather than accepting either the repo's stdlib-source reading or the prior loop's compiled reproduction as self-certifying:

- **Finding 1 (`run`'s `EXACTLY_ONCE` contract) — confirmed by compilation.** Compiled and ran `val toParcel: ArrayList<Int>; run { toParcel = ArrayList(); toParcel.add(1) }`, and the `var` equivalent — both compiled with zero diagnostics and printed the expected list. Matches Revision 1's stdlib-source citation (`Standard.kt:41-46`) and loop 1's finding exactly.
- **Finding 2 (`Byte` bitwise / `UByte.toInt()`) — confirmed by compilation, and Revision 1's more precise claim specifically verified.** `(b and 0xFF.toByte())` without `import kotlin.experimental.and` fails with `unresolved reference: and`. *With* the import it now resolves `and` but the plan's newly-added detail — that the subsequent `or 0x100` then fails because `Byte.and` returns a `Byte` with no `Byte`-plus-`Int` `or` overload — also reproduces exactly: `error: unresolved reference 'or'`. This is a claim loop 1 did not make and Revision 1 added; it independently checks out. Ran `(b.toInt() and 0xFF) or 0x100` versus `(b.toUByte().toInt() and 0xFF) or 0x100` over all 256 `Byte` values (`-128..127`): zero mismatches, and both produce `"ff"` for `b = -1`, matching `md5Suffix`'s actual usage.
- **Finding 3 (`@JvmField val` in a companion) — independently compiled, closing the gap Revision 1 flagged as unverified.** Revision 1 explicitly could not compile this reproduction itself and recorded that as a documented gap (D2, "Honest note on how this was verified"). I could: compiled a Kotlin `abstract class Base { companion object { const val CONST_TAG = "const"; @JvmField val JVMFIELD_TAG = "jvmfield"; val PLAIN_TAG = "plain" } }`, then compiled two Java subclasses against it with `javac` — one reading `CONST_TAG`/`JVMFIELD_TAG` unqualified (**compiles clean**), one reading `PLAIN_TAG` unqualified (**fails**: `PLAIN_TAG has private access in Base`). `javap -v` on the compiled class shows `public static final java.lang.String CONST_TAG;` and `public static final java.lang.String JVMFIELD_TAG;` as sibling fields on the outer class, `private static final java.lang.String PLAIN_TAG;` plus a synthetic `access$getPLAIN_TAG$cp()` for the third. This closes D2's disclosed provenance gap: the claim is now independently compiler-verified, not resting on documentation plus a different agent's report.
- **AC19's `javap -v` / `ConstantValue:` upgrade — verified to actually discriminate.** Same `javap -v` output: `CONST_TAG` carries `ConstantValue: String const`; `JVMFIELD_TAG` carries no `ConstantValue` attribute at all (only `RuntimeInvisibleAnnotations` for `@NotNull`). AC19's upgraded check is real — it is the one flag that tells `const val` and `@JvmField val` apart at the bytecode level, exactly as D2/D4 now claim.
- **D19/AC22's README claim — verified against the live file.** Read `net/download/service-interface/README.md:8` directly: it reads, verbatim, "are `const val`, not plain `val` or `@JvmField val`. Only `const val` emits a `public static final` field on the outer class that a Java subclass inherits unqualified…" — the same false exclusivity claim D2 corrected, present today, unmodified. D19/AC22's decision to extend from two README bullets to three is not invented scope; the third bullet's justification actually is wrong in the live file. Also ran AC22's grep (with the pattern's literal quote marks read as plain text rather than as nested Markdown backticks, which is the only way the acceptance criterion parses as an executable shell command): `grep -cE 'not plain val or @JvmField val|Only const val emits' net/download/service-interface/README.md` → **1** today, confirming the check currently fails as expected and will read **0** once D19's rewrite lands.
- **Shape check.** Recounted directly: `grep -c '^\*\*Step '` → **7**; unique `AC\d+` tokens → **22** (AC1–AC22, no gaps, no duplicates). File Scope's six renames plus four doc files is unchanged from the pre-revision Plan. D2's own resolved-decision text now states the corrected `{const val, @JvmField val}` vs. `{plain val}` distinction and no longer claims `const val` exclusivity.

All five of the above check out exactly as Revision 1 states. The next finding is new — surfaced by re-reading Step 4 and AC7's own literal text against D10's corrected body, not by re-checking Revision 1's citations.

### Concerns

- **Severity:** MAJOR
- **Class:** Plan-internal contradiction between a Resolved Decision and its own governing Step/Acceptance-Criterion — the corrected reasoning did not propagate to the two places that actually constrain what gets built (checklist #2, #9; directly touches the "milestone/scope creep" and "diff stays inside minimal-diff hard rule" categories)
- **Concern:** D10's revised text (lines 397–420) is unambiguous and correct: the authorized shape is a plain `run { … }` **statement** assigning a `val` declared above it, with **"no local is renamed"** stated explicitly, and the initializer-expression form (`val toParcel = run { … ; list }`) is explicitly **rejected** — "it compiles and is behaviorally identical, but it forces a rename and reorders the assignment for no gain, which `AGENTS.md`'s minimal-diff rule forbids." That is the correct fix for loop 1's MAJOR finding. But two places that actually govern the developer's and reviewer's behavior were never updated to match, and both still describe the rejected form:
  - **Step 4** (line 637): "Apply D10 (**the `run { … }` initializer expression** in exactly the authorized shape, comments preserved; explicit `getParcelableArrayList<DownloadRequest>` type argument; no `!!`)."
  - **AC7** (line 709): "`DownloadRequestBuilderTest.kt` contains `run {` **used as an initializer expression** for `toParcel`, the `// test DownloadRequests to parcel` comment is still present inside it, and `getParcelableArrayList<DownloadRequest>("r")` carries its explicit type argument (D10)."

  Both were true, verbatim descriptions of the *pre-revision* D10 (the form loop 1 flagged as unforced extra diff surface, requiring a rename of `toParcel` → `list`). Neither was edited when D10 was rewritten. Revision 1's own "Edits made" section asserts the opposite happened: item 3 (line 867) states "**AC7's D10 clause already required `run {` plus the preserved comment**… and is unchanged — it never referenced the rename, so it constrains the corrected form exactly as well as the old one." That claim is false on inspection of AC7's actual text, which does reference exactly the rejected shape by name ("used as an initializer expression for `toParcel`" is not generic `run {` phrasing — it is a specific technical description of the construct D10 now forbids, and the only way to satisfy it literally is `val toParcel = run { … ; <some final expression> }`, which is impossible without introducing a second name for the list, since `toParcel` cannot appear as both the initializer target and a value read inside its own initializer without shadowing — precisely the rename D10 forbids). The "Counts and shape" section (line 884) similarly does not list Step 4 or AC7 among what changed, and line 886's "Net effect on the diff the developer will write: strictly smaller… its correction removes a variable rename and an assignment restructure from `DownloadRequestBuilderTest.kt`" is not currently true of the Plan as written: as long as AC7 stands, a developer or reviewer who checks the literal acceptance criterion is checking for the rename-requiring form the correction was supposed to eliminate. This is the same failure mode Revision 1's own preamble names and disclaims three times over ("I asserted compiler behavior I had not executed... D10 invented two compiler error messages") — here it recurs as an assertion about the Plan's *own internal consistency* ("AC7... is unchanged... constrains the corrected form exactly as well as the old one") that was not checked against AC7's actual text before being written.
  - **Practical consequence, scoped honestly:** this is not a correctness or equivalence-proof risk — both forms are proven behaviorally identical (this loop's Finding 1 and loop 1's finding both confirm the statement form compiles and behaves correctly, and the initializer-expression form was never claimed to be wrong Kotlin, only a larger diff). No wrong code can ship as a result of this contradiction. But it is exactly the kind of ambiguity D10 says nothing should be — "Nothing is left to the developer's judgement, and nothing is deferred to the reviewer" (Plan preamble, line 249) is false for this one file as the document currently stands: an implementer following Step 4/AC7 literally writes the larger, `AGENTS.md`-noncompliant diff loop 1 flagged and the Plan claims to have eliminated; an implementer following D10 (the more authoritative, more recently-corrected text) writes the smaller diff but produces code that does not literally satisfy AC7's stated text, creating exactly the kind of reviewer friction ("is this AC7 residual disclosed or not?") the D15/AC6/AC7 apparatus elsewhere in this Plan is designed to prevent.
- **Evidence:** `tasks/antennapod-net-download-service-interface-kotlin-milestone-13.md:397-420` (D10, corrected — "No restructuring, no rename... Neither is restructuring into an initializer expression... which `AGENTS.md`'s minimal-diff rule forbids") versus `:637` (Step 4, unedited: "the `run { … }` initializer expression in exactly the authorized shape") and `:709` (AC7, unedited: "`run {` used as an initializer expression for `toParcel`") and `:867` ("AC7's D10 clause already required `run {`... and is unchanged — it never referenced the rename, so it constrains the corrected form exactly as well as the old one" — this claim does not hold against AC7's actual wording) and `:884,886` ("Counts and shape" not listing Step 4/AC7 as changed; "strictly smaller" claim).
- **Suggested mitigation:** Before Step 4 is executed, correct Step 4's and AC7's text to match D10's authoritative shape — e.g. Step 4: "Apply D10 (the `run { … }` **statement**, assigning the `val` declared above it, exactly the authorized shape, no rename; comments preserved; explicit `getParcelableArrayList<DownloadRequest>` type argument; no `!!`)"; AC7: "`DownloadRequestBuilderTest.kt` contains `run {` used as a **statement** assigning the pre-declared `toParcel`, the `// test DownloadRequests to parcel` comment is still present inside it, `toParcel` is not renamed, and `getParcelableArrayList<DownloadRequest>("r")` carries its explicit type argument (D10)." This is a two-line textual edit to the Plan document, not a File-Scope or production-code change, and it does not require re-opening D10 itself (which is correct as written) or touching any other Step/AC. Given the max-2-loop rule for the plan stage has now been used, I am not requesting a third plan-review loop for this alone: the fix is small, mechanical, and fully specified above. Whoever executes Step 4 — the `android-migration-developer` — must follow D10's code block (lines 401-414) as the source of truth over Step 4's current prose if the two are not reconciled first, and the `migration-code-reviewer` must specifically confirm at Step 4/implementation review that `DownloadRequestBuilderTest.kt` uses the statement form with no local renamed, since AC7's un-updated text cannot currently be trusted as the sole gate for this file.

### Checklist categories considered and dismissed

- **Characterization tests prove equivalence, not just existence** — re-confirmed unaffected; D15's mechanism is untouched by Revision 1 and this loop's finding is about Step 4/AC7's prose, not the D15 audit's mechanics.
- **Silent behavior changes from mechanical translation** — considered specifically for the three re-verified findings; none found. Both authorized forms of D10's fix are behaviorally identical (verified this loop and last), so the Step 4/AC7 contradiction is a diff-size and document-consistency defect, not a silent-behavior-change risk.
- **Public API breakage** — re-confirmed unaffected; nothing in Revision 1 or this loop's finding touches File Scope or any production file. AC20/AC21 are unchanged and still gate this correctly.
- **Coverage gaps left unaddressed** — re-confirmed unaffected; D4/D5's disclosed-and-compensated treatment is untouched by Revision 1, and AC19's upgrade to `javap -v` + `ConstantValue:` is now independently verified (this loop) to actually close the precision gap loop 1 flagged, not merely to gesture at it.
- **Milestone/scope creep** — considered specifically for D19/AC22's expansion from two README bullets to three. Not creep: independently confirmed the module's own README currently asserts the exact false claim D2 corrected (`net/download/service-interface/README.md:8`), so correcting it is discharging an obligation the revision itself created (leaving a corrected Plan next to an uncorrected README would hand the next agent the false premise), not scope expansion. File Scope is unaffected — `README.md` was already scoped.
- **`di`/`concurrency`/`compose`/`navigation`/`gradle-kts` tracks** — re-confirmed not applicable; nothing in Revision 1 touches any of these, and loop 1's dismissal stands.

### Escalation note

This is loop 2 of max 2 for the plan stage — the last plan-stage loop regardless of outcome, and no third plan-review loop follows. The verdict is CHALLENGE because a genuine, concrete defect survives: Step 4 and AC7 still describe and require the exact `run {}`-as-initializer-expression form that D10 was rewritten specifically to reject, and Revision 1's own claim that AC7 "already... constrains the corrected form exactly as well as the old one" does not hold up against AC7's actual text. This is **not** severe enough that the milestone should not proceed, and it does **not** warrant descoping, accepting a larger risk, or a bespoke prerequisite — the fix is a two-line textual correction to Step 4 and AC7 (given verbatim above), it does not touch D10 itself, File Scope, or any other Resolved Decision or Acceptance Criterion, and every other claim independently re-verified this loop (the `run`/`EXACTLY_ONCE` mechanism, the `Byte`/`UByte` arithmetic including Revision 1's new `or 0x100` detail, the `@JvmField val` companion-inheritance shape — now closed against a real compiler rather than left as a documented gap, the `javap -v`/`ConstantValue:` discriminator, and the README's actual false claim) checks out exactly as stated. Recommendation: apply the two-line Step 4/AC7 correction above (a text edit, not a re-plan) before Step 4 is executed, and have the `migration-code-reviewer` verify `DownloadRequestBuilderTest.kt`'s shape directly against D10 rather than against AC7's current wording if the correction is not applied first. This finding carries forward as a documented residual risk into implementation; it does not block starting Steps 1–3, 5–7, none of which touch this file or this decision.

### Revision 1 addendum (post-loop-2 textual sync, applied by the orchestrating session)

The `legacy-android-planner` agent that would normally apply this fix hit a session limit mid-task, after reading the loop 2 verdict but before editing the file (its last recorded state: "A real miss on my part — Revision 1 explicitly claimed AC7 needed no change. Let me read the loop 2 verdict and check what Step 4 and AC7 actually say."). Rather than re-spawn a planner session for a fix that was already fully specified verbatim in the Escalation note above, the orchestrating session applied the exact two-line correction directly:
- **Step 4** (was line 637): "the `run { … }` initializer expression in exactly the authorized shape" → "the `run { … }` **statement**, assigning the `val` declared above it, exactly the authorized shape, no rename".
- **AC7** (was line 709): "`run {` used as an initializer expression for `toParcel`" → "`run {` used as a **statement** assigning the pre-declared `toParcel`... `toParcel` is not renamed".

No other text changed. D10 itself, File Scope, and all 22 ACs' pass/fail outcomes are unaffected — this closes the one loop-2 finding and the Plan stage is now closed. No third red-team loop is needed: the correction is the verbatim text the red-team already specified as sufficient.

---

## Implementation Notes
_By: android-migration-developer | 2026-08-04_

### Pre-implementation textual-sync check

Read Step 4 (line ~637) and AC7 (line ~709) against D10 (lines 397–420) before starting, per this
session's explicit instruction. Both already read the corrected "`run { … }` **statement**,
assigning the `val` declared above it... no rename" / "used as a **statement** assigning the
pre-declared `toParcel`... `toParcel` is not renamed" text — the orchestrating session's addendum
fix had already landed cleanly. No mismatch found; nothing to flag here.

### Commit message(s)

Six commits on `antennapod-net-download-service-interface-kotlin-milestone-13`, following the
Plan's four-tier structure (Step 1 produced no repo diff, so it folds into Step 2's commit; one
extra commit fixes a staging mistake caught before Step 4 began):

```
kotlin/net-download-service-interface: Step 2 — convert Tier A (FilenameGeneratorTest, FileNameGeneratorCharacterizationTest) + toolchain proof
kotlin/net-download-service-interface: Step 3 — convert Tier B (DownloadRequestBuilderCharacterizationTest, DownloadRequestCreatorTest)
kotlin/net-download-service-interface: fix Step 3 commit — stage the actual Kotlin content
kotlin/net-download-service-interface: Step 4 — convert Tier C (DownloadRequestBuilderTest)
kotlin/net-download-service-interface: Step 5 — convert Tier D (DownloadServiceInterfaceTest)
```

(Step 6/7's verification-and-docs commit follows this note being written, per the Plan's own
sequencing — Step 6 produces no repo diff either, since it is entirely `--rerun`/`javap`/`ktlint`
verification against already-committed code.)

Summarized version for the PR description:

```
feat(net-download-service-interface): kotlin migration — convert the module's 6-file, 823-LOC, 54-test Java characterization suite to Kotlin

kotlin track (test source set), Milestone 13. Completes the module's
migration begun in Milestone 10 (100% Kotlin production code). All six
test files convert; none is held back under the Java-oracle bright
line (D2) — DownloadServiceInterfaceTest's testWorkConstants loses its
module-local proof that the five WORK_* constants are const-val-shaped
static fields (it still proves their values), and that shape is
re-proven mechanically by javap -v (ConstantValue attribute present on
all five) rather than left silently unguarded. The @JvmStatic erosion
on the six singleton accessors stays proven by 87 external Java call
sites, unaffected. FileNameGenerator's @JvmStatic loses its guard and
its requirement together (zero remaining Java callers repo-wide) —
filed as future-work item 14, annotation not removed.

54/54 tests green on both flavors throughout, two separate --rerun
invocations at every step. D15's per-file assertion-content diff is
empty for all 6 files, checked at every step and re-swept in one pass
at the end. Zero !!, zero backticks, all 50 getter-to-property
rewrites landed with none over-applied, builder::build stayed a
callable reference (settled empirically, no fallback needed).

One genuine deviation from the Plan's own stated assumption, disclosed
in Implementation Notes: D10 claimed
bundleOut.getParcelableArrayList<DownloadRequest>("r") "stays a
platform type" and would need no null handling. That's false in this
repo's actual Kotlin/AGP setup — a real compile error, not a
hypothetical — so DownloadRequestBuilderTest.kt uses
checkNotNull(...) instead of the forbidden !!, per the
kotlin-j2k-style skill's own guidance for exactly this shape.
```

### Branch and starting state

Branch `antennapod-net-download-service-interface-kotlin-milestone-13`, checked out off
`origin/develop` at merge-base `939659e578d9fbac62b9a0010df9726303cb53f6` ("kotlin/net-sync-service:
kotlin migration (#18)"), confirmed via `git merge-base HEAD origin/develop` at Step 1. Working
tree was clean apart from the two untracked spec files at session start.

### Test commands run

Module is flavored (`free`/`play`, from `playFlavor.gradle`); no plain `debug` variant exists.
Confirmed via `net/download/service-interface/build.gradle` (applies `playFlavor.gradle`, no
`buildTypes`/extra `productFlavors` of its own) — matches Research's stated task names exactly, so
no rediscovery surprise:
- `./gradlew --console=plain :net:download:service-interface:testFreeDebugUnitTest --rerun`
- `./gradlew --console=plain :net:download:service-interface:testPlayDebugUnitTest --rerun`

Always run as **two separate invocations**, never combined on one command line (Research Risk 12) —
verified again this session: a combined `:testFreeDebugUnitTest :testPlayDebugUnitTest --rerun` line
was not used anywhere in this implementation.

Also run:
- `./gradlew :net:download:service-interface:ktlintCheck` — after every conversion step from Step 2
  onward. `runKtlintCheckOverTestSourceSet`/`ktlintTestSourceSetCheck` confirmed genuinely
  **executing** (not `NO-SOURCE`/`SKIPPED`) starting at Step 2, the first `.kt` in the source set
  (D21/AC16). Three formatting-only findings surfaced and fixed at Step 4 (argument/parameter-list
  wrapping, `standard:function-signature`/`standard:parameter-list-wrapping`) — unrelated to the
  three underscore test names, which passed unchanged with **zero** findings against them, settling
  D14/Research Risk 8 empirically on this module rather than by inheritance from `:model` M7.
- `./gradlew :net:download:service-interface:checkstyle :net:download:service-interface:lint`
  (module-scoped) — green at Step 2 (first `.kt` landing, settling Research Unknown 8/AC17: Android
  Lint reported **nothing** against the new `.kt` test files) and again at Step 6.
- `./gradlew checkstyle lint` (repo-wide, per `AGENTS.md`'s own final-gate instruction) — run at
  Step 2 and Step 6. Both times failed with **two pre-existing findings unrelated to this module**:
  `:app-wearos:compileFreeDebugKotlin`/`compilePlayDebugKotlin` (a `String?`/`String` argument-type
  mismatch in `EpisodeDetailActivity.kt:115`, a file this milestone never touches) and
  `:app:spotbugsPlayDebug` (a pre-existing `NP_NULL_ON_SOME_PATH` finding in `QueueFragment.java`).
  Verified pre-existing, not introduced by this milestone: `git stash`-ed this milestone's changes
  back to the clean merge-base checkout and reran both failing tasks directly — both failed
  identically with the working tree unmodified. Restored the stash immediately after confirming.
  Module-scoped `checkstyle`/`lint` (above) is unaffected by either finding and is the criterion
  this milestone's ACs actually gate on.
- `./gradlew :net:download:service-interface:compileFreeDebugUnitTestJavaWithJavac` — confirmed
  still **executing** (not `NO-SOURCE`) at Step 2, covering the four still-Java files at that point;
  confirmed `NO-SOURCE` for both `compileFreeDebugUnitTestJavaWithJavac` and
  `compilePlayDebugUnitTestJavaWithJavac` at Step 6, after the last file converted.
- `./gradlew :app:assembleDebug` — green at Step 6, compiling both flavors and every consuming
  module, including `DownloadServiceInterfaceImpl.java`'s unqualified inherited `WORK_*` reads.
- `javap -v` / `javap -p` against `net/download/service-interface/build/tmp/kotlin-classes/freeDebug`
  at Step 6 — see AC19 below.

### Characterization test results

All 54 tests across the six files, green **before** (Step 1, against unconverted Java) and
**after** every conversion step (Steps 2–5 individually, then Step 6's whole-suite reconciliation),
with **zero test-file behavior edits** — every change is a language conversion, not a test-content
edit (`D15`'s per-file assertion-content diff is empty for all 6 files at every check).

| File | Tests | Before | After (per-step) | After (Step 6) | What it exercises |
|---|---|---|---|---|---|
| `FilenameGeneratorTest` | 9 | PASS | PASS (Step 2) | PASS | Upstream sanitisation contract: slashes/colons/brackets/apostrophes stripped, accents folded, dashes/interior spaces preserved, trailing space trimmed, `MAX_FILENAME_LENGTH` cap, distinct-hash-for-distinct-long-input, real filesystem creatability. Also caught and fixed a genuine transcription defect: the non-breaking-space literal in `generateFileNameTreatsTabAndNonBreakingSpaceDifferently`'s Java original (verified via `od -tx1`: `c2 a0`) was silently normalized to a regular space on first write; the D15 byte-level diff flagged it, and the fix used explicit Python `chr(0xA0)` construction to avoid the same normalization recurring |
| `FileNameGeneratorCharacterizationTest` | 6 | PASS | PASS (Step 2) | PASS | The `>=241/242` boundary on both sides, the exact MD5 suffix string (pinned via D13's two hand-verified expressions), the random-fallback length-8/`validChars` membership, leading-space collapse, `isSpaceChar`-vs-`isWhitespace` discrimination via tab and non-breaking space, `generateFileNameNullThrowsNpe` |
| `DownloadRequestBuilderCharacterizationTest` | 11 | PASS | PASS (Step 3) | PASS | `isLocalFeed()` skipping `prepareUrl`, `prepareUrl` applied for non-local feed and media, `REQUEST_ARG_PAGE_NR`, `withInitiatedByUser`, `setSource` override, `setForce`'s force-true-nulls/force-false-no-op asymmetry, `mediaEnqueued == false`, `buildWithNullSourceThrowsNpe` (fires from `DownloadRequestBuilder.kt:68`'s `source!!`, unchanged by this conversion) |
| `DownloadRequestCreatorTest` | 13 | PASS | PASS (Step 3) | PASS | On-disk filename contracts: stale-file deletion, title-over-URL and URL-fallback naming, partial-download reuse, the `-1`/`-2` collision ladder, media path from feed title, title-over-URL-guess, the 220-char truncation boundary, five latent-NPE paths (all fire from production `!!` chains, unchanged) |
| `DownloadRequestBuilderTest` | 4 | PASS | PASS (Step 4) | PASS | Upstream: `DownloadRequest` Parcelable round-trip through `Bundle.putParcelableArrayList` in three auth configurations (with-auth/no-auth/mixed-auth — the three underscore-named tests), builder `equals`/`not-equals` on differing credentials |
| `DownloadServiceInterfaceTest` | 11 | PASS | PASS (Step 5) | PASS | `isDownloadingEpisode`/`isEpisodeQueued`/`getProgress` across absent/COMPLETED/QUEUED/RUNNING, null-URL safety (README #3), replace-not-merge and aliasing semantics of `setCurrentDownloads` (README #4), the five `WORK_*` constants' **values** (README #1 — shape re-proven by `javap`, not by this test, per D2/D4), get/set round-trips for all three static singletons (README #2) |
| **Total** | **54** | **PASS** | **PASS** | **PASS** | |

Per-class counts re-verified identical on **both flavors** at Step 1 (baseline) and Step 6 (final):
`DownloadRequestCreatorTest` 13, `DownloadServiceInterfaceTest` 11, `DownloadRequestBuilderCharacterizationTest`
11, `FilenameGeneratorTest` 9, `FileNameGeneratorCharacterizationTest` 6, `DownloadRequestBuilderTest`
4 — row for row, not just in aggregate (AC3).

### `javap` interop re-proof (D4/AC19)

Run at Step 6 against `net/download/service-interface/build/tmp/kotlin-classes/freeDebug`:

```
$ javap -v -classpath <freeDebug classes> de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
  public static final java.lang.String WORK_TAG;
    flags: (0x0019) ACC_PUBLIC, ACC_STATIC, ACC_FINAL
    ConstantValue: String episodeDownload
  public static final java.lang.String WORK_TAG_EPISODE_URL;
    ConstantValue: String episodeUrl:
  public static final java.lang.String WORK_DATA_PROGRESS;
    ConstantValue: String progress
  public static final java.lang.String WORK_DATA_MEDIA_ID;
    ConstantValue: String media_id
  public static final java.lang.String WORK_DATA_WAS_QUEUED;
    ConstantValue: String was_queued
```

All five on `DownloadServiceInterface` itself (not `Companion`), each carrying a `ConstantValue:`
attribute — the `const val` shape, not merely "a real static field" (D2's `@JvmField val`-would-also-
qualify correction). `ConstantValue:` was present on all five; D4's "recorded, not failed" fallback
for a missing attribute was not needed.

```
$ javap -p -classpath <freeDebug classes> de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
  public static final DownloadServiceInterface get();
  public static final void setImpl(DownloadServiceInterface);

$ javap -p ... FeedUpdateManager
  public static final FeedUpdateManager getInstance();
  public static final void setInstance(FeedUpdateManager);

$ javap -p ... AutoDownloadManager
  public static final AutoDownloadManager getInstance();
  public static final void setInstance(AutoDownloadManager);

$ javap -p ... DownloadRequestCreator
  public static final DownloadRequestBuilder create(Feed);
  public static final DownloadRequestBuilder create(FeedMedia);

$ javap -p ... FileNameGenerator
  public static final int MAX_FILENAME_LENGTH;
  public static final String generateFileName(String);
```

Every row matches D4's table exactly. This is the language-independent replacement for the
source-level coverage `testWorkConstants` and the `@JvmStatic` call sites stop providing.

### Machine-checked ACs, verbatim

- **AC1** (baseline, two separate invocations): both flavors `BUILD SUCCESSFUL`, `110 actionable
  tasks: 1 executed, 109 up-to-date` each (single-task invocations, so `1 executed` is the test task
  itself), `build/test-results/<task>/` mtime moved from `09:25:32`/`09:25:38` to a fresh timestamp
  after each run — confirms `--rerun` genuinely re-executed both. 54/54, 0/0/0, identical per-class
  breakdown both flavors.
- **AC2**: both flavors green as two separate `--rerun` invocations at the end of every one of
  Steps 2–5 and again at Step 6. No `.toLong()`, `L` suffix, or `assertTrue(a == b)` relaxation
  anywhere (grep-confirmed, see AC13).
- **AC3**: per-class counts identical row-for-row, both flavors, Step 1 vs Step 6. Confirmed above.
- **AC4**: `git diff <merge-base> -- .../src/test/` test-method-name list is unchanged; all 54
  names transcribe unchanged, three underscore names byte-for-byte, zero backtick-quoted names.
- **AC5**: per-file assertion counts unchanged before/after: 31/22/13/11/8/6, total 91. Confirmed by
  direct `grep -cE` at Step 6 against the current `.kt` files, matching Step 1's baseline exactly.
- **AC6**: D15 diff empty for all 6 files, both per-step and in Step 6(c)'s consolidated one-sweep
  re-derivation (all six rows `0`). One residual **was** caught and fixed during Step 2 (the
  non-breaking-space defect, see the test-results table above) — the mechanism worked as designed.
- **AC7**: all four direct-inspection checks pass — see the grep output pasted above this section.
- **AC8**: `grep -c 'HashMap<String?, DownloadStatus>()' DownloadServiceInterfaceTest.kt` → 7.
  `git diff <merge-base> -- .../src/main/` → empty.
- **AC9**: `AutoDownloadManager`'s anonymous subclass returns `FutureTask<Void?> { null }` exactly
  as D3 specifies — no fallback needed. `grep -c CompletableFuture` → 0.
  `testAutoDownloadManagerGetSetInstanceRoundTrip` still asserts only `assertSame(...)`; nothing
  calls `autodownloadUndownloadedItems`.
- **AC10**: all six `FeedUpdateManager` overrides and both `AutoDownloadManager` overrides compile
  with no `@Suppress` and no cast, matching each declaration's nullability exactly (verified by the
  file compiling clean — a mismatch would be a loud error, not a silent pass).
- **AC11**: `grep -rn '`'` → 0. `Object val` → `val value`, renamed not backticked (D9).
- **AC12**: 0 leftover `.getX()` calls for the 13 required-property names; `DownloadServiceInterfaceTest.kt`
  still shows 10/5/5/4 function-call sites, zero property rewrites in that file. Both directions
  confirmed by direct grep, matching the Plan's exact pre-counted numbers.
- **AC13**: `grep -rnE '\.toLong\(\)|\b[0-9]+L\b'` → 0. `createFeedItem(id: Long)`, call sites
  unchanged (`createFeedItem(1)`, `createFeedItem(2)`).
- **AC14**: `builder::build` stayed a callable reference — settled empirically at Step 3, no
  fallback needed, no disclosed residual.
- **AC15**: `grep -rn '!!'` → 0. `FilenameGeneratorTest.kt`'s `getExternalCacheDir()` transcribes
  as-is, no `!!` added. `DownloadRequestBuilderTest.kt`'s one genuinely-required nullability
  handling uses `checkNotNull(...)`, not `!!` — see Deviations below.
- **AC16**: ktlint `BUILD SUCCESSFUL`, `ktlintTestSourceSetCheck` genuinely executing, zero
  `@Suppress("ktlint:…")`, no `.editorconfig`/filter changes. Three underscore names confirmed
  passing unchanged at Step 4, observed on this module directly.
- **AC17**: module-scoped `checkstyle lint` `BUILD SUCCESSFUL`; Android Lint reported nothing
  against the new `.kt` test files, observed at Step 2.
- **AC18**: `find .../src/test -name '*.java'` → nothing; `-name '*.kt' | wc -l` → 6.
  `compile{Free,Play}DebugUnitTestJavaWithJavac` both `NO-SOURCE` at Step 6.
- **AC19**: see the `javap` re-proof section above — every row matches.
- **AC20**: `:app:assembleDebug` green at Step 6, zero edits outside `src/test/` and the four doc
  files.
- **AC21**: `git diff --name-only <merge-base>` lists only File-Scope files (6 renames + README.md
  + future-work.md; task/checkpoint files untracked at merge-base so they don't appear in this diff
  but are the two remaining File-Scope entries). `net/download/service-interface/build.gradle`
  unchanged. `config/checkstyle/suppressions.xml` unchanged. `assertlines.pl` lives only in the
  session scratchpad, never committed.
- **AC22**: README's three affected bullets updated per D19, `grep -cE 'not plain `val` or
  `@JvmField val`|Only `const val` emits' net/download/service-interface/README.md` → 0. Other
  three bullets byte-for-byte unchanged (verified: only the three targeted lines changed in the
  README diff). `tasks/antennapod-model-kotlin-future-work.md` gained item 14 and an item 3 update;
  items 6, 7, 8 unchanged (verified: diff is 10 pure insertions, 0 deletions).

### Deviations from plan

One genuine deviation from a Resolved Decision's stated technical claim, disclosed here per the
task's instruction, plus one process-only staging mistake caught and fixed before it could affect
anything downstream:

1. **D10's "stays a platform type" claim is false for `bundleOut.getParcelableArrayList<DownloadRequest>("r")`
   in this repo's actual Kotlin/AGP setup (`DownloadRequestBuilderTest.kt`).** D10 states: "The
   result stays a platform type; no `!!` and no explicit nullable annotation is added (D16), so the
   subsequent `fromParcel.size` / `fromParcel.get(1)` reads behave exactly as the Java did." Once
   the explicit `<DownloadRequest>` type argument D10 itself mandates was added (needed because the
   call has no other inference source), the Kotlin compiler rejected direct `.size`/`[n]` access
   with: `Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type
   'ArrayList<DownloadRequest!>?'` — a real compile error, not a hypothetical, confirming
   `Bundle.getParcelableArrayList` is genuinely `@Nullable`-annotated in this SDK/AGP setup, not
   merely an unchecked platform type. `!!` is forbidden unconditionally by D16/AC15 (zero-tolerance
   grep, no disclosed-exception clause, unlike AC6's residual mechanism). Following the
   `kotlin-j2k-style` skill's own item 1 guidance for exactly this shape — "Only keep `!!` where the
   surrounding Java already proved non-null right before use... replace it with a
   `checkNotNull(x)`/smart-cast pattern if that reads more clearly" — I used
   `checkNotNull(bundleOut.getParcelableArrayList<DownloadRequest>("r"))`. The Java original never
   null-checked this value either (an unchecked dereference that NPEs on a null return), so
   `checkNotNull`'s `IllegalStateException` on that same never-actually-null-in-practice path (the
   Bundle/Parcel round-trip always succeeds in every one of the three test invocations) is the
   closest available equivalent without violating the no-`!!` rule. Does not touch any assertion
   line — D15's residual for this file stayed empty before and after the fix. Flagging prominently
   per this pipeline's own standard, since the `migration-code-reviewer` and red-team should verify
   this reasoning independently rather than take it on my word.
2. **Process-only: Step 3's first commit attempt staged the wrong content.** A `git add` invocation
   listing both the new `.kt` paths and the now-nonexistent `.java` paths (already renamed away by
   `git mv`) hit a fatal "pathspec did not match" error and staged nothing; the immediately-following
   `git commit` therefore captured only the earlier `git mv`'s rename with the original,
   pre-conversion Java content intact under the `.kt` extension (verified: that commit showed `0
   insertions/deletions`, and its committed file body still read `public class ...` with semicolons
   and Java getters — it would not even have compiled as Kotlin). Caught immediately by re-checking
   `git diff --stat HEAD` after the commit, before Step 4 began. Fixed with a new commit (never an
   amend, per the git safety protocol) that stages and commits the actual working-tree content — no
   behavior or scope change, purely a staging-mechanics correction. From Step 4 onward, every `git
   add` used only paths confirmed to exist, and every commit's diff was checked (`git diff --cached
   --stat`, then content spot-checked) before running `git commit`.

No Open Question (OQ1–OQ4) was resolved or closed by this implementation — all four stand exactly
as the Plan left them. Future-work item 14 (D5) and item 3's Milestone 13 update are filed per Step
7's instruction; items 6, 7, 8 are untouched.

---

## Code Review Verdict
_By: migration-code-reviewer | 2026-08-04 | Loop 1 of max 3_

### Verdict
APPROVE

### Verification performed

Read Research, Plan, Plan — Revision 1, both Red-Team Verdict (Plan) sections (including the
loop-2 Step 4/AC7 textual-sync addendum), and Implementation Notes in full, plus
`tasks/antennapod-net-sync-service-kotlin.md`'s Code Review Verdict as the calibration example for
depth. Nothing in this review is taken on the developer's word — every claim below was re-derived
directly against the actual committed source, not re-read from Implementation Notes' transcription
of it.

- **File Scope.** `git diff --stat 939659e57..HEAD -- .` shows exactly 16 changed files: the 6
  `.java`→`.kt` renames, `README.md`, `tasks/antennapod-model-kotlin-future-work.md`, the task file
  itself, the checkpoint file — nothing else. No production file, no `build.gradle`, no
  `config/checkstyle/suppressions.xml`, no file in any other module. Matches File Scope exactly. No
  finding.

- **Independent verification of Deviation 1 (the D10 platform-type claim).** The task explicitly
  asked me not to take this on trust. I edited `DownloadRequestBuilderTest.kt` to revert
  `checkNotNull(bundleOut.getParcelableArrayList<DownloadRequest>("r"))` back to the Plan's literal
  assumed shape (`bundleOut.getParcelableArrayList<DownloadRequest>("r")`, no null handling) and ran
  `./gradlew --console=plain :net:download:service-interface:compileFreeDebugUnitTestKotlin --rerun`.
  It failed with a real compile error at all four use sites (`toParcel.size`/`fromParcel.size`,
  `fromParcel[1].source`, `fromParcel[0].password`, `fromParcel[0].arguments`): "Only safe (?.) or
  non-null asserted (!!.) calls are allowed on a nullable receiver of type
  'ArrayList<DownloadRequest!>?'." This is a genuine compiler-forced deviation, not a shortcut —
  `Bundle.getParcelableArrayList` really is `@Nullable`-annotated in this repo's actual Android
  SDK/AGP setup, contradicting D10's stated assumption. Reverted the edit
  (`git checkout -- <file>`) and confirmed the committed content is back to `checkNotNull(...)`.
  Independently grepped the whole test source set for `!!`: **zero hits**, confirming `checkNotNull`
  — not `!!` — is what actually shipped, satisfying D16/AC15's unconditional prohibition. The
  developer's disclosure is accurate and the fix (`kotlin-j2k-style` skill's item 1: replace an
  unjustified `!!` with `checkNotNull`) is the correct one for a value the Java original also
  dereferenced without a null check.

- **Independent verification of Deviation 2 (the non-breaking-space defect).** Compared byte-exact
  content: `git show 939659e57:.../FileNameGeneratorCharacterizationTest.java | sed -n '70p' | od -An
  -tx1` and the equivalent line in the committed `.kt` file both show `61 c2 a0 62` — `a` + U+00A0
  (UTF-8 `c2 a0`) + `b` — byte-for-byte identical. The fix is genuinely present and correct in the
  committed file. Checked `git log --follow` for this file: only one commit exists
  (`1d49c6bc0`, Step 2), and its committed content already carries the correct non-breaking-space
  bytes — consistent with the developer's account that the defect was caught by the D15 diff and
  corrected before that file was ever committed, not caught-and-fixed across two commits. This was
  the right catch mechanism (D15's per-file assertion-content diff, not a human eyeball pass), and
  it is exactly the class of silent transcription defect this pipeline's D15 apparatus exists to
  catch.

- **Independent verification of the Step 3 staging-mistake claim.** Read the actual committed
  content of the flagged bad commit, `fc487674c`: `git show fc487674c:.../DownloadRequestCreatorTest.kt`
  shows genuine stale Java — semicolons, `import android.content.Context;`-style statements, blank
  lines matching Java import-grouping conventions rather than Kotlin's single block. This confirms
  the claim precisely: the file at that commit was renamed to `.kt` but still held its pre-conversion
  Java body. The very next commit, `d7f3cb65b`, shows a real diff (207 insertions / 210 deletions
  across the two Tier B files) and its message correctly identifies the cause (a `git add` invocation
  naming stale, now-nonexistent `.java` paths that failed fast and staged nothing). Confirmed it is a
  genuinely new commit, not an amend (`git log` shows both SHAs present in history, not one rewritten
  over the other). Confirmed the *current* HEAD content of both affected files is real, compiling
  Kotlin: zero semicolons in either file, and both compile and pass as part of the green test run
  below. The process mistake was real, was caught before Step 4 as claimed, and the current committed
  state is correct.

- **Characterization tests, BEFORE/AFTER, as two separate `--rerun` invocations (Risk 12/AC1–AC2) —
  re-run independently, not trusted from Implementation Notes.**
  - `./gradlew --console=plain :net:download:service-interface:testFreeDebugUnitTest --rerun` →
    `BUILD SUCCESSFUL`. Read the JUnit XML directly: 54/54 across all six classes, `0` failures/
    errors/skipped each, row-for-row matching the Plan's expected per-class counts
    (13/11/11/9/6/4).
  - `./gradlew --console=plain :net:download:service-interface:testPlayDebugUnitTest --rerun` (a
    genuinely separate invocation, run after the free-flavor one completed) → `BUILD SUCCESSFUL`.
    Same 54/54, identical per-class breakdown.
  - At no point did I run a combined two-task command line, and neither did the developer per their
    own disclosure — both individually verified.
- **AC5 (assertion counts) and AC6 (assertion-content equivalence) — re-derived, not trusted.**
  Direct `grep -cE '\bassert[A-Z][A-Za-z]*\('` per file: 11/22/6/31/13/8 = **91**, matching Plan and
  Implementation Notes exactly. Then reconstructed the Plan's own D15 Perl extractor from its exact
  source in the task file (matched byte-for-byte against a pre-existing copy already in the session
  scratchpad from a prior agent's work — same script, confirmed by diff) and ran the full 1:1
  canonicalized diff for **all six files** against the merge-base (`939659e578d9fbac62b9a0010df9726303cb53f6`)
  Java originals: **empty residual on all six**, reproducing AC6's claim exactly, as AC6 itself
  requires ("The reviewer re-runs the exact Step 1 command and confirms it reproduces"). Also
  validated the extractor's own calibration independently: its output line count matches each file's
  raw `grep -c` assertion count exactly (11/22/6/31/13/8), so the empty-residual result is not an
  artifact of a mis-calibrated extractor silently skipping lines.
- **AC4 (no test added/renamed/removed/moved).** Independently extracted the full `@Test`-annotated
  method name set from both the merge-base Java files and the committed Kotlin files (not reusing the
  developer's own extraction): 54 names each, identical sets, zero diff. The three underscore names
  (`parcelInArrayListTest_WithAuth`/`_NoAuth`/`_MixAuth`) are present unchanged.
- **AC7 (the four extractor-blind-spot hazards) — re-checked by direct inspection.** `md5Suffix` in
  `FileNameGeneratorCharacterizationTest.kt` contains exactly D13's two pinned expressions
  (`input.toByteArray(StandardCharsets.UTF_8)`, `(b.toInt() and 0xFF) or 0x100`); grep for the four
  forbidden forms (`toString(16)`, `String.format`, `toUByte`, `0xFF.toByte`, bare `toByteArray()`)
  returns zero across the whole test source set. `DownloadRequestBuilderTest.kt` uses `run {` as a
  **statement** (not an initializer expression) assigning the pre-declared `toParcel`, the
  `// test DownloadRequests to parcel` comment survives inside it, `toParcel` is not renamed, and
  `getParcelableArrayList<DownloadRequest>("r")` carries its explicit type argument — this is the
  corrected D10 shape (post loop-2/addendum), and Step 4/AC7's text in this same file already reads
  the corrected "statement" wording, confirming the orchestrating session's textual-sync fix actually
  landed in the document, not just in the developer's head. `FilenameGeneratorTest.kt` still calls
  `TextUtils.isEmpty(result)` and its three `assertEquals(result, "abc abc")` reversed-argument sites
  transcribe byte-identical to the Java original (checked by eye against
  `git show 939659e57:.../FilenameGeneratorTest.java`). No `listOf(`/`mapOf(`/`mutableMapOf(` anywhere
  in the test source set.
- **AC8/AC9/AC10 (interop constraints, no production edit).** `grep -c
  'HashMap<String?, DownloadStatus>()' DownloadServiceInterfaceTest.kt` → 7. `git diff 939659e57 --
  net/download/service-interface/src/main/` → **empty** (0 lines), confirmed directly, not merely
  cited. The `AutoDownloadManager` anonymous subclass returns `FutureTask<Void?> { null }` exactly as
  D3 specifies; `grep -c CompletableFuture` → 0. All six `FeedUpdateManager` overrides and both
  `AutoDownloadManager` overrides match their production declarations' nullability exactly, including
  both `runOnceOrAsk` overloads' non-null `Context` parameter — verified by reading the override
  signatures directly against `FeedUpdateManager.kt`/`AutoDownloadManager.kt`, not merely by the file
  compiling.
- **AC11/AC12/AC13/AC14/AC15 — re-derived by direct grep, not trusted.** Zero backticks, zero `!!`,
  zero `.toLong()`/`L`-suffix hits, zero leftover `.getX()` calls for the 13 required property names,
  `DownloadServiceInterfaceTest.kt`'s 24 accessor sites (10/5/5/4) all still function calls with zero
  property rewrites in that file, `builder::build` still a bare callable reference passed to
  `assertThrows` (no fallback needed, matching the developer's claim).
- **AC16 (ktlint).** `./gradlew :net:download:service-interface:ktlintCheck` → `BUILD SUCCESSFUL`,
  `ktlintTestSourceSetCheck` genuinely executing (not `NO-SOURCE`/`SKIPPED`). Zero
  `@Suppress("ktlint:…")` anywhere.
- **AC17 (module-scoped checkstyle/lint).** `./gradlew :net:download:service-interface:checkstyle
  :net:download:service-interface:lint` → `BUILD SUCCESSFUL`.
- **AC18.** `find .../src/test -name '*.java'` → nothing. `-name '*.kt' | wc -l` → 6.
- **AC19 (`javap -v`/`ConstantValue:` re-proof) — re-run independently against the actual compiled
  classes**, not copy-pasted from Implementation Notes: `javap -v -classpath
  .../build/tmp/kotlin-classes/freeDebug de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface`
  shows all five `WORK_*` fields as `public static final java.lang.String` directly on
  `DownloadServiceInterface` (not `Companion`), each carrying its own `ConstantValue:` attribute with
  the exact literal string. Matches Implementation Notes' pasted output exactly, byte for byte.
- **AC20.** `./gradlew --console=plain :app:assembleDebug` → `BUILD SUCCESSFUL` (both flavors,
  1231 tasks), re-run independently rather than trusted.
- **AC21/AC22.** `git diff --name-only` confirms File Scope compliance (see above). README diff shows
  exactly the three intended bullets changed (`const val` justification, `--rerun` two-invocations
  correction, "tests are Java by design" replaced with the Kotlin-tests/`javap` guidance); grep for
  the two forbidden false-exclusivity phrases → 0. `tasks/antennapod-model-kotlin-future-work.md`
  diff is 10 pure insertions (new item 14 plus item 3's Milestone 13 update); items 6/7/8 untouched,
  confirmed by reading the diff directly.

### Findings

None at CRITICAL or MAJOR severity. Every Acceptance Criterion (AC1–AC22) was independently
re-verified against live source, a live compiler/Gradle run, or a live `javap` dump — not accepted on
the developer's transcription — and all 22 check out exactly as reported. File Scope is respected
exactly. Both disclosed items (the D10 platform-type deviation and the non-breaking-space
transcription defect) are genuine, correctly diagnosed, correctly fixed, and consistent with this
pipeline's own deviation-disclosure and D15-audit mechanisms rather than being informal shortcuts.
The Step 3 staging mistake was real, caught before it could propagate, and fixed with a new commit
rather than an amend, matching the git safety protocol.

- **Severity:** MINOR
- **Class:** Quality
- **File:line:** `features/antennapod-net-download-service-interface-kotlin-milestone-13.checkpoint.md:22`
- **Finding:** The checkpoint's "Resume command" section still reads "Milestone 13 scaffolded,
  Research not yet started. Next: invoke `legacy-android-researcher`…" — stale boilerplate left over
  from the checkpoint's initial scaffolding, contradicted by the same file's own "Lifecycle progress"
  checklist three sections above, which correctly shows Research through Implement all checked off.
  Cosmetic only; nothing downstream reads this field programmatically, but it would mislead a human
  or agent resuming from this checkpoint file alone without reading the task file first.
- **Suggested fix:** Update the "Resume command" line to reflect actual state, e.g. "Implementation
  complete (all 7 Steps, 22/22 ACs). Next: invoke `migration-code-reviewer` for code review" (now
  superseded by this verdict — update again to point at `legacy-android-red-team` for the
  implementation-review stage). Not blocking; fix in the same commit as the next stage's update, or
  whenever this checkpoint file is next touched.

### Verification artifacts

- Independently re-ran both flavors' test suites as two separate `--rerun` invocations: 54/54 green
  each, identical per-class breakdown.
- Independently reconstructed and ran the D15 Perl extractor against all 6 files' merge-base Java
  originals vs. committed Kotlin: empty residual on all six, extractor calibration confirmed
  (line counts match raw assertion `grep -c` exactly).
- Independently reverted `DownloadRequestBuilderTest.kt`'s `checkNotNull(...)` to the Plan's literal
  assumed shape and reproduced the exact compile failure the developer reported, then restored the
  committed version.
- Independently confirmed the non-breaking-space fix at the byte level (`od -An -tx1`) against the
  merge-base Java original.
- Independently re-ran `javap -v` against the actual compiled `freeDebug` classes and confirmed the
  `ConstantValue:` attribute on all five `WORK_*` constants.
- Independently re-ran `:app:assembleDebug`, module-scoped `checkstyle`/`lint`, and `ktlintCheck`: all
  green.
- Independently re-extracted the 54-name `@Test` method-name set from both language versions:
  identical.

### Recommendation

APPROVE. Proceed to `legacy-android-red-team` for the implementation-review stage per the lifecycle.
The one MINOR finding (stale checkpoint "Resume command" text) does not block merge and can be fixed
opportunistically.
