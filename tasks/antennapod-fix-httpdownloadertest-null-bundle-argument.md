# antennapod-fix-httpdownloadertest-null-bundle-argument

> **Description:** Fix a pre-existing crash in `app/src/androidTest/.../service/download/HttpDownloaderTest.java`, discovered when Milestone 15b's PR #22 got a real `emulator-test` CI run for the first time in 19+ days (after three sibling tasks cleared the `static-analysis` gate that had been blocking it). Two call sites construct `DownloadRequest` with a `null` `arguments` `Bundle`, which crashes via Kotlin's `Intrinsics.checkNotNullParameter` since `:model` Milestone 6 tightened that parameter to non-null.
> **Repo:** `services/android-migration/projects/portfolio/antennapod`
> **Created:** 2026-08-13

> **Pre-research context (carried over from the parent investigation conversation — do not re-derive):**
> - **Why this exists.** José rebased and force-pushed Milestone 15b's branch (`test/ui-preferences-sync-settings-before-screenshot`, PR #22) onto `origin/develop` after all three sibling CI-blocker tasks merged (`antennapod-fix-spotbugs-static-analysis-debt` PR #23, `antennapod-fix-net-download-service-spotbugs-debt` PR #25, `antennapod-fix-app-wearos-compile-error` PR #26). The resulting CI run (`31694690838`, 2026-08-13T11:15:24Z) is the first run in the visible CI history where `Static Code Analysis` and all four `Unit Test` jobs **passed** — confirming the three-task detour worked. But the `Emulator Test` matrix (API 23/30/36) failed for the first time these tests have actually executed since 2026-07-24.
> - **Root cause, already diagnosed (verify, don't re-derive from scratch).** `app/src/androidTest/java/de/test/antennapod/service/download/HttpDownloaderTest.java` has two `new DownloadRequest(...)` call sites (around lines 87 and 123) that pass `null` for the `arguments` parameter. `model/src/main/java/de/danoeh/antennapod/model/download/DownloadRequest.kt:8-18`'s 9-arg convenience constructor declares `arguments: Bundle` — non-null. Pre-migration, `DownloadRequest.java`'s `Bundle arguments` parameter was **unannotated** (`git show 936c16aec^:model/.../DownloadRequest.java:31-33` — no `@NonNull`/`@Nullable`), so passing `null` from Java compiled and ran fine. The Kotlin migration (`936c16aec`, "kotlin migration — convert DownloadRequest, Playable, RemoteMedia, FeedMedia to Kotlin", **2026-07-25** — the day *after* `develop`'s `Checks` workflow went red on an unrelated `:app-wearos` issue) tightened it to non-null, matching the **only production construction site** (`net/download/service-interface/.../DownloadRequestBuilder.kt:18,67` — `private val arguments = Bundle()`, always a real empty Bundle, never null). This is the same J2K-widening case-study pattern documented in all three sibling tasks, but inverted: here Kotlin correctly tightened the contract to match real production usage, and it's the *test* that's stale, still written against the old, looser Java contract.
> - **Why this was never caught.** `checks.yml`'s `emulator-test` job (which runs `:app:connectedPlayDebugAndroidTest`, the job that actually executes this test) declares `needs: static-analysis`. `static-analysis` has failed on `develop` for every run since 2026-07-24 (first the `:app-wearos` compile error, then — once that specific job order is considered — the SpotBugs piles), so `emulator-test` never got to run and expose this crash until PR #22's rebase finally produced a green `static-analysis`.
> - **Secondary failure, likely a cascade not a second bug.** The same CI run also shows `de.test.antennapod.ui.DownloadLogTest.testExistingNonSubscribedFeed` failing with `NullPointerException: Attempt to invoke virtual method 'long ...Feed.getId()' on a null object reference`. The raw log shows `"Test run failed to complete. Instrumentation run failed due to Process crashed."` immediately after `HttpDownloaderTest`'s uncaught crash, and the failure count/composition shifts slightly across Gradle's automatic retries (9 failures → 9 failures → 10 failures, with `DownloadLogTest` only appearing on the later retries) — consistent with the crashed instrumentation process leaving corrupted/uninitialized shared state (single app process/database for the whole `connectedPlayDebugAndroidTest` run) for whatever test ran next, rather than an independent defect in `DownloadLogTest` itself. **Verify this hypothesis in Research rather than assume it** — confirm `DownloadLogTest`'s own setup is otherwise sound and that fixing `HttpDownloaderTest` alone makes the full suite green, or determine there really are two independent bugs.
> - **The fix is small and well-understood, but verify before assuming it's literally two lines.** Replacing `null` with `new Bundle()` at both call sites (matching the one real production caller's own pattern) should resolve the crash. Confirm there isn't a reason the test specifically wanted to probe null-Bundle behavior (unlikely, given it predates the type ever being nullable/non-null in any meaningful sense — Java couldn't express "must be null" any more than "must be non-null" here — but verify by reading the test's intent, not just its signature).
> - **Scope discipline.** Per `AGENTS.md`'s minimal-diff rule, fix only the `HttpDownloaderTest.java` call site(s) that pass null — do not touch `DownloadRequest.kt`, `DownloadRequestBuilder.kt`, or any other file, unless Research finds the cascade hypothesis is wrong and a second, independent fix is genuinely needed.
> - **Not part of Milestone 15b's own diff.** Confirmed via `git diff --name-only` that none of PR #22's own commits touch `app/src/androidTest/`. This is pre-existing `develop` debt (introduced 2026-07-25, before this whole three-task detour started), surfaced as a side effect of finally getting a real emulator-test CI run — not caused by the rebase.
> - **Sequencing.** This task's fix should merge, then Milestone 15b's PR #22 should rebase once more (or simply re-run CI once this lands, if no further develop drift makes another rebase necessary) to get a genuinely green `emulator-test` run for AC13.
> - This is unaffiliated OSS portfolio case-study work — see `services/android-migration/projects/portfolio/README.md`. Standalone repo hygiene, not part of the Sync Settings vertical-slice sequence (Milestones 15-20).

## Research
_Last updated by: legacy-android-researcher | 2026-08-13_

### Summary

The target is a single legacy Java instrumented test class, `app/src/androidTest/java/de/test/antennapod/service/download/HttpDownloaderTest.java` (168 lines, 9 `@Test` methods, `@LargeTest`, no `@RunWith` — default JUnit4 runner, no Activity involvement). It exercises `HttpDownloader` against a local `HTTPBin` stub server by constructing `DownloadRequest` objects directly via the raw constructor rather than through production's builder. It has never been converted to Kotlin and is not in any module's `kotlin` track scope; it lives in `:app`'s androidTest source set. The only requested track here is effectively `kotlin` — this task is fallout from the `kotlin` track already applied to `:model` (Milestone 6, `936c16aec`), not a new track for a new module.

The pre-research diagnosis of the **primary** failure is **confirmed in full**: two call sites pass `null` for a parameter that the Kotlin migration correctly tightened to non-null, and all 9 tests in the class fail deterministically on every API level and every retry attempt. The pre-research diagnosis of the **secondary** failure is **refuted**: `DownloadLogTest.testExistingNonSubscribedFeed` is *not* a cascade from `HttpDownloaderTest`. It is an independent, pre-existing, ~50%-flaky production null-dereference in `:app`'s `FeedItemlistFragment.loadItems()`, proven independent by a controlled comparison across seven CI attempts in which `HttpDownloaderTest` failed identically every time while `DownloadLogTest` passed in four of them. This changes the task's shape materially: fixing `HttpDownloaderTest` is necessary but **not sufficient** to guarantee a green `emulator-test` run for Milestone 15b's AC13.

### Findings

#### Existing surface

`HttpDownloaderTest` (9 `@Test` methods, all currently failing):
- `setUp()` initialises `UserPreferences`, resolves an external-files download dir (with an internal-storage fallback for emulators without an SD card), and starts an `HTTPBin` local server.
- `tearDown()` deletes every file in the dest dir (asserting each delete succeeds) and stops the server.
- `setupFeedFile(...)` builds a `Feed(downloadUrl, "")` and sets its `localFileUrl`.
- Two `download(...)` overloads: a 3-arg convenience that delegates to the 6-arg one with `username`/`password` null, and the 6-arg one that constructs the `DownloadRequest` and drives `HttpDownloader`.
- Tests: `testPassingHttp`, `testRedirect`, `testGzip`, `test404`, `testCancel`, `testDeleteOnFailShouldDelete`, `testDeleteOnFailShouldNotDelete`, `testAuthenticationShouldSucceed`, `testAuthenticationShouldFail`.

Eight of the nine reach `DownloadRequest` through the shared `download(...)` helper; `testCancel` constructs its own inline. Both construction paths pass `null` for `arguments`, which is why the failure rate is 9/9 — 100% of the class.

#### Java/Kotlin interop boundary

**Confirmed: exactly two `DownloadRequest` construction sites in this test file, at the current line numbers 87 and 123** (the pre-research estimate was correct):

- `HttpDownloaderTest.java:87-88` — inside `download(url, title, expectedResult, deleteExisting, username, password)`:
  `new DownloadRequest(feedFile.getLocalFileUrl(), url, title, 0, Feed.FEEDFILETYPE_FEED, username, password, null, false)`
- `HttpDownloaderTest.java:123-124` — inside `testCancel()`:
  `new DownloadRequest(feedFile.getLocalFileUrl(), url, "delay", 0, Feed.FEEDFILETYPE_FEED, null, null, null, false)`

Both are 9-argument calls, so both bind to the **9-arg convenience constructor** at `DownloadRequest.kt:30-43`, not the 11-arg primary at `DownloadRequest.kt:8-20`. Positional mapping for that overload is `(destination, source, title, feedfileId, feedfileType, username, password, arguments, initiatedByUser)`. The 8th argument is therefore `arguments`, and it is declared `arguments: Bundle` — **genuinely non-null** (`DownloadRequest.kt:38`). The CI stack trace confirms the parameter by name, removing any doubt about which position the null lands on:
`java.lang.NullPointerException: Parameter specified as non-null is null: method de.danoeh.antennapod.model.download.DownloadRequest.<init>, parameter arguments`

At line 123 there are three `null`s in a row. The first two land on `username: String?` and `password: String?` (`DownloadRequest.kt:36-37`) — both legitimately nullable, no crash. Only the third, `arguments`, crashes. That is consistent with line 87 also crashing despite passing non-null `username`/`password` in the auth tests.

**No other parameter of the 9-arg overload is at risk after the fix.** `destination: String` receives `feedFile.getLocalFileUrl()`, whose Kotlin declaration is `var localFileUrl: String? = null` (`Feed.kt:26`) — a nullable Kotlin property read from Java, so no compile-time check, but `setupFeedFile` assigns it a real absolute path (`HttpDownloaderTest.java:71,76`) before every call, so it is non-null at runtime. `title: String` receives non-null literals in all 9 tests. No second `checkNotNullParameter` will fire once `arguments` is fixed.

**Pre-migration Java contract — confirmed unannotated.** `git show 936c16aec^:model/src/main/java/de/danoeh/antennapod/model/download/DownloadRequest.java` shows the 9-arg constructor as:
```java
public DownloadRequest(@NonNull String destination, @NonNull String source, @NonNull String title, long feedfileId,
                       int feedfileType, String username, String password,
                       Bundle arguments, boolean initiatedByUser) {
```
`destination`, `source`, and `title` carry `@NonNull`; `arguments` carries **neither** `@NonNull` nor `@Nullable`. So J2K had no annotation to read, and the migration's decision to tighten it to non-null was a deliberate contract choice, not a mechanical translation — matching the sole production caller.

**Important nuance the pre-research did not surface:** passing `null` was never actually *supported* even in the Java version. The pre-migration Java `hashCode()` dereferenced the field unguarded — `result = 31 * result + arguments.hashCode();` (`DownloadRequest.java:141` at `936c16aec^`) — so any `DownloadRequest` built with a null `arguments` would have NPE'd on `hashCode()` in Java too. `HttpDownloaderTest` simply never calls `hashCode()` or `equals()` on its requests, so it got away with an object that was already in an unsupported state. This is decisive for the fix-intent question below: the test was not probing null-Bundle behavior; it was exploiting an unenforced hole that Java could not express.

**Production construction sites — confirmed exactly one.** A repo-wide grep for `DownloadRequest(` across all `.java`/`.kt` (excluding `/build/` and the declaration itself) returns exactly one production construction site: `DownloadRequestBuilder.build()` at `DownloadRequestBuilder.kt:67-70`. It passes `arguments`, a field declared `private val arguments = Bundle()` (`DownloadRequestBuilder.kt:19`) — always a real, non-null, eagerly-constructed `Bundle`, never reassigned, never nullable. The `Feed` secondary constructor additionally populates it with `REQUEST_ARG_PAGE_NR` (`DownloadRequestBuilder.kt:37`). Every other grep hit is either a getter call (`getDownloadRequest()`) or a Kotlin unit test (`DownloadRequestTest.kt`, `DownloadRequestBuilderCharacterizationTest.kt`), which are Kotlin sources and therefore compile-checked against the non-null contract.

#### Current test coverage

The safety net for this change is unusual: **the failing tests themselves are the coverage**, and they are currently 100% red.

- `HttpDownloaderTest` — 9 instrumented tests, all failing at the constructor. Because they fail during setup of the request object, **zero downloader behavior is currently being exercised at all**. The assertions on success/failure status, file existence after failed downloads, redirect handling, gzip handling, cancellation, and HTTP basic-auth success/failure are all unreached. This is not degraded coverage; it is total coverage loss for `HttpDownloader` on the instrumented path.
- `DownloadRequest` itself is well covered by Kotlin unit tests: `model/src/test/java/de/danoeh/antennapod/model/download/DownloadRequestTest.kt` (Parcel round-trip, equals/hashCode, auth-null transforms — 8 construction sites across the file) and `net/download/service-interface/src/test/java/.../DownloadRequestBuilderCharacterizationTest.kt`. Neither can catch this bug, because both are Kotlin and cannot pass `null` to a non-null parameter.
- These unit tests run in the `unit-test` CI job, which **passed** in run `31694690838`. Only the instrumented job caught this.

#### Characterization-test gaps

- **No test anywhere asserts `DownloadRequest`'s null-`arguments` behavior**, in either direction. There is no test that would have caught the tightening, and none that pins the current non-null contract against a Java caller. This is expected and arguably correct — the contract is now enforced by the compiler for Kotlin callers — but it means Java-source callers in `androidTest` are entirely unguarded. `HttpDownloaderTest` is the only Java caller in the repo, so the exposure is bounded, but it was found by CI rather than by a test.
- **`FeedItemlistFragment.loadItems()` has no test coverage for the null-feed path.** `DBReader.getFeed(...)` is explicitly annotated `@Nullable` (`DBReader.java:329-330`) and returns `null` when the row is absent (`DBReader.java:334-346` — `feed` stays null and the else branch only logs). `FeedItemlistFragment.java:659` dereferences it unguarded on the very next line. There is no unit or instrumented test asserting what happens when the feed disappears mid-load. This gap is what makes the secondary failure both real and unprovable-by-test today.

#### Track-specific findings

`kotlin` (the only applicable track — this is fallout from `:model`'s completed `kotlin` track, not a new module migration):

- **Null-safety hazard, tightening class.** Every other J2K hazard documented in the sibling tasks is the *widening* case (J2K turned unannotated Java params into `T?`, and still-Java implementors deref them — see `5c1b5ebdd`'s commit message and `net/download/service-interface/README.md:10`). This is the **inverse**: the migration correctly *tightened* an unannotated Java param to non-null, and a stale Java caller in a source set the compiler does not cross-check broke. Worth recording as a distinct hazard category for the case study.
- **Why the compiler did not catch it.** `HttpDownloaderTest.java` is Java in `:app`'s `androidTest` source set. Kotlin nullability annotations on `:model`'s classes are emitted as metadata plus `Intrinsics.checkNotNullParameter` calls; javac does not enforce Kotlin's `@NotNull` metadata, so the call compiles cleanly and fails only at runtime. `assemblePlayDebugAndroidTest` (`checks.yml:139-140`) succeeded — the failure is strictly runtime.
- **Detection latency.** 19 days (`936c16aec`, 2026-07-25 → CI run `31694690838`, 2026-08-13), entirely because the only job that executes this test was gated behind a red `static-analysis`.

Tracks `gradle-kts`, `di`, `concurrency`, `compose`, `navigation`: not requested and not applicable. Note that the *secondary* finding below sits in RxJava3 code (`FeedItemlistFragment.loadItems()` uses `Observable.fromCallable().subscribeOn(Schedulers.computation())`) — that is `:app`'s existing concurrency pattern, reported as fact, not as a recommendation to migrate it. No `concurrency` track work is proposed or in scope here.

#### Secondary failure — cascade hypothesis REFUTED

**Verdict: `DownloadLogTest.testExistingNonSubscribedFeed` is an independent, pre-existing, flaky defect. It is not caused by `HttpDownloaderTest`.**

I could not reproduce locally — see "Reproduction attempt" below — but the CI evidence is a genuine controlled experiment, not inference. `runEmulatorTests.sh:12` retries the whole suite up to three times, and the job matrix has three API levels, so run `31694690838` contains **seven independent executions of the same commit**. Across all seven, `HttpDownloaderTest` failed with all 9 tests, every time, identically. `DownloadLogTest` passed in four of them:

| API | Attempt | Tests completed | Failures | `DownloadLogTest` | Process crash |
|---|---|---|---|---|---|
| 23 | 1 | 35 | 10 | **FAILED** | yes |
| 23 | 2 | 68 | 9 | PASSED | no |
| 23 | 3 | — | — | — | canceled by matrix fail-fast |
| 30 | 1 | 67 | 9 | PASSED | no |
| 30 | 2 | 34 | 10 | **FAILED** | yes |
| 30 | 3 | 34 | 10 | **FAILED** | yes |
| 36 | 1 | 69 | 9 | PASSED | no |
| 36 | 2 | 69 | 9 | PASSED | no |
| 36 | 3 | — | — | — | canceled by matrix fail-fast |

API 36 is the cleanest disproof: two attempts, identical 9 `HttpDownloaderTest` failures both times, `DownloadLogTest` passed both times, 69/69 tests completed, no process crash. Same cause present, opposite effect. Causation is refuted.

The direction of the pre-research hypothesis is also inverted. `HttpDownloaderTest`'s NPEs are **caught by JUnit and reported as ordinary test failures** — they never crash the process, proven by API 30 attempt 1 and API 36 attempts 1-2, which ran the full 67-69 tests to completion with those 9 failures and no crash. It is `DownloadLogTest` that kills the instrumentation process, which is why the crashed runs stop at 34-35 tests.

**Structural mechanism, from what I read.** The failure is *not* `DownloadLogTest`'s own `feed` field being null (which was the pre-research's assumed mechanism, based on the abbreviated log line). The real stack is in production code, on a background thread:
```
java.lang.NullPointerException: Attempt to invoke virtual method 'long de.danoeh.antennapod.model.feed.Feed.getId()' on a null object reference
	at de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment.lambda$loadItems$25(FeedItemlistFragment.java:659)
	at io.reactivex.rxjava3.internal.operators.observable.ObservableFromCallable.subscribeActual(ObservableFromCallable.java:46)
	...
	at java.lang.Thread.run(Thread.java:923)
```
`FeedItemlistFragment.java:658-659` reads:
```java
feed = DBReader.getFeed(feedID, true, 0, page * EPISODES_PER_PAGE);
int count = DBReader.getFeedEpisodeCount(feed.getId(), feed.getItemFilter());
```
`DBReader.getFeed` is `@Nullable` and returns null for a missing feed. The chain runs on `Schedulers.computation()`; when the subscriber has already been disposed (`loadItems()` disposes on re-entry, `FeedItemlistFragment.java:653-655`, and the fragment disposes on teardown), RxJava3 cannot deliver the error to the `error ->` consumer at `:676` and routes it to `RxJavaPlugins.onError` — an uncaught background-thread exception, which kills `de.danoeh.antennapod.debug` (logcat confirms `Process: de.danoeh.antennapod.debug, PID: 2659`) and produces `Instrumentation run failed due to Process crashed.`

Why a `FeedItemlistFragment` is alive at all during `testExistingNonSubscribedFeed`: it should not be. `DownloadLogDetailsDialog.goToFeed()` (`DownloadLogDetailsDialog.java:157-168`) routes `STATE_SUBSCRIBED` feeds to `MainActivityStarter.withOpenFeed(...)` → `FeedItemlistFragment`, and everything else to `OnlineFeedviewActivityStarter`. `testExistingNonSubscribedFeed` sets `STATE_NOT_SUBSCRIBED` (`DownloadLogTest.java:80`), so its own intended path is the online-feed view, **not** `FeedItemlistFragment`. The fragment is therefore leftover from a sibling test in the same class that does route through it (`testExistingSubscribedFeed`, `testExistingMedia`, `testNonExistingFeed`), still resident in the shared app process. `DownloadLogTest.setUp()` calls `EspressoTestUtils.clearDatabase()` (`DownloadLogTest.java:53`), which does a full `PodDBAdapter.deleteDatabase()` (`EspressoTestUtils.java:204-210`). A surviving fragment that re-runs `loadItems()` after that wipe gets `null` back for a feed ID that no longer exists — exactly the observed NPE. That is a race between fragment teardown and the next test's DB wipe, which is precisely why it is ~50% flaky and API-level dependent.

`HttpDownloaderTest` cannot participate in this mechanism: it is a non-UI `@LargeTest` with no `@RunWith(AndroidJUnit4)`, no `ActivityTestRule`, no Activity, no fragment, and no database access. It cannot create or resurrect a `FeedItemlistFragment`.

**Provenance: pre-existing `develop` debt, unrelated to PR #22.** `git diff --name-only origin/develop...HEAD` returns only two task/checkpoint markdown files, `tasks/artifacts/*`, `ui/preferences/README.md`, and two files under `ui/preferences/src/test/` — no `androidTest`, and `git diff --stat origin/develop...HEAD -- app/ net/ model/ storage/` is **empty**. Milestone 15b touches no production code and no instrumented test. `git log` on `FeedItemlistFragment.java` shows its last touch as `5c1b5ebdd` (sibling SpotBugs task), and inspecting that diff confirms it added only `getContext()` null guards at lines ~175, ~315, and ~542 — **it did not touch `loadItems()`**. `DownloadLogTest.java` has not been modified since `f47134a7e` (#7867, upstream).

#### Fix correctness — `new Bundle()` vs `DownloadRequestBuilder`

Both options were evaluated. **My reading favours the minimal-diff `new Bundle()`, and I think the builder option is not merely heavier but actually behavior-changing** — but the call is the planner's.

Case for `new Bundle()` at both sites (`HttpDownloaderTest.java:88` and `:124`):
- It exactly matches the sole production caller's own value: `DownloadRequestBuilder.kt:19` holds `private val arguments = Bundle()`, and for the media path never adds a key, so an empty `Bundle` *is* production's real argument in the common case.
- It preserves every other property of the constructed request bit-for-bit, so the 9 restored assertions test the same thing they were written to test.
- It satisfies `AGENTS.md`'s minimal-diff and "change the absolutely minimal number of lines" rules.
- `android.os.Bundle` is already an available type; only an import is added.

Case against switching the test to `DownloadRequestBuilder`:
- **It cannot express what the test needs.** The builder has no general-purpose constructor — only `(destination, FeedMedia)` and `(destination, Feed)` (`DownloadRequestBuilder.kt:22-38`). The `Feed` overload derives `title` from `feed.getHumanReadableIdentifier()`, which falls back to `downloadUrl` when the title is empty (`Feed.kt:251-257`). `HttpDownloaderTest.setupFeedFile` builds `new Feed(downloadUrl, "")` with an empty title, so every request's title would silently become the HTTPBin URL instead of the intended `"test200"` / `"testGzip"` / `"delay"`.
- The `Feed` overload also rewrites `source` through `UrlChecker.prepareUrl(...)` and injects `REQUEST_ARG_PAGE_NR` into `arguments` (`DownloadRequestBuilder.kt:32,37`) — neither of which the current test does. It also defaults `initiatedByUser = true` (`:20`) where the test passes `false`.
- So "align the test with production's construction path" would change the requests under test, which is the one thing a characterization-style fix must not do. It is a legitimate refactor to want, but it belongs in its own task with its own justification, not folded into a red-CI unblock.

Neither call site shows any intent to probe null-`arguments` behavior: there is no comment, no assertion on `arguments`, no `@Test` named for it, and the pre-migration Java `hashCode()` would itself have NPE'd on such an object (`DownloadRequest.java:141` at `936c16aec^`). The `null`s are incidental.

#### Test coverage impact

Fixing the two call sites should restore the full original intent of all 9 tests, and I found no evidence they were independently broken or flaky:
- Their failure signature is uniform and constructor-local — every one of the 9 dies at `DownloadRequest.<init>` with the identical message, on every API level and every attempt (7/7). None reached its assertions, so none can be hiding a second failure. That also means **the fix cannot be assumed to make them pass** — it makes them *run* for the first time in 19 days, and whatever they then assert about `HttpDownloader`, HTTPBin, redirects, gzip, cancellation, and basic-auth is unverified until CI executes them. The planner should treat "the 9 tests pass" as a hypothesis to confirm in CI, not a foregone conclusion.
- `setUp()` already handles the emulator-without-SD-card case (`HttpDownloaderTest.java:57-60`), and `@LargeTest` with no `IgnoreOnCi` annotation means they are in scope for the CI filter (`runEmulatorTests.sh:8`).
- `tearDown()` asserts every file deletes (`:44-47`); with tests actually completing, this is a plausible new source of noise if a download leaves a locked or unexpected file, but there is no evidence of that historically.

#### Track prerequisites

- `kotlin`: **prerequisites met.** No blocking gap. `:model`'s `kotlin` track is complete (`936c16aec`, `:model` now 100% Kotlin) and the non-null contract on `DownloadRequest.arguments` is correct and should not be loosened. The work here is repairing a stale Java caller in `:app`'s androidTest source set, not converting anything.
- `gradle-kts`, `di`, `concurrency`, `compose`, `navigation`: not requested, not applicable, no prerequisites assessed.

#### CI mechanics

`checks.yml` confirmed as described:
- `emulator-test` (`checks.yml:107-161`) declares `needs: static-analysis` (`:109`), matrix `api-level: 23 (default) / 30 (aosp_atd) / 36 (aosp_atd)` (`:113-120`), `timeout-minutes: 45`, `runs-on: ubuntu-latest`. No `fail-fast: false`, so the first matrix leg to fail cancels the others — which is exactly what the run's annotations show (`"The strategy configuration was canceled because emulator-test._30_aosp_atd failed"`), and why API 23 and 36 never got a third attempt.
- `unit-test` (`:56-105`) also declares `needs: static-analysis` (`:58`), so both downstream jobs were gated by the same red job.
- The emulator step runs `bash .github/workflows/runEmulatorTests.sh` (`:156`), which invokes `./gradlew connectedPlayDebugAndroidTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=de.test.antennapod.IgnoreOnCi` (`runEmulatorTests.sh:7-9`) and retries it up to three times (`:12`). All observed failures are in the `:app:connectedPlayDebugAndroidTest` task.
- On failure it uploads `app/build/reports/androidTests/` as the `test-report` artifact (`checks.yml:157-161`) — available for the post-fix run if a per-test HTML report is wanted.
- `ci-summary` (`:163-171`) needs `wrapper-validation`, `emulator-test`, and `unit-test`, so `emulator-test` is a hard gate on the overall check.

#### Reproduction attempt

**Not reproduced locally — stated explicitly rather than implied.** `adb devices` returns an empty device list; an SDK and emulator binary exist at `/Users/josegarcia/Library/Android/sdk/emulator/emulator` but no AVD is running and no physical device is attached. `connectedPlayDebugAndroidTest` requires a live device, so neither the `HttpDownloaderTest` fix nor the `DownloadLogTest` flake could be exercised here. The conclusions above rest on (a) direct source reading of every file in the call path and (b) the seven-attempt CI matrix, which functions as a controlled comparison for the independence question. The `HttpDownloaderTest` root cause is certain (7/7 deterministic, parameter named in the stack trace, single unambiguous constructor overload). The `DownloadLogTest` mechanism is a structural argument from read code plus the observed flake pattern — high confidence on *independence*, moderate confidence on the precise leftover-fragment race.

### Unknowns

1. **Will fixing `HttpDownloaderTest` alone produce a green `emulator-test` run?** Not guaranteed. `DownloadLogTest` flaked in 3 of 7 observed attempts and kills the process when it flakes. The 3× retry in `runEmulatorTests.sh` gives each API level three chances, and each API level had at least one clean `DownloadLogTest` attempt, so a green run is likely — but API 30 flaked on 2 of its 3 attempts, and with no `fail-fast: false` a single red leg fails the whole matrix. The planner must decide whether AC13 tolerates a retry-dependent green, and how many re-runs are acceptable before escalating. Note also that removing the 9 `HttpDownloaderTest` failures changes suite timing, which may shift the race in either direction — unpredictable from here.
2. **Should the `FeedItemlistFragment.java:659` null-deref be fixed in this task, a follow-up task, or not at all?** It is a genuine unguarded dereference of a documented-`@Nullable` return in `:app` production code, it is pre-existing `develop` debt unrelated to any migration track, and fixing it is out of this task's stated scope (`AGENTS.md` minimal-diff; the task's own "Scope discipline" bullet). But leaving it means AC13's green run is luck-dependent. This is a scope decision with sequencing consequences — flagging for the planner and José, not deciding it here. If taken, it would be a fifth CI-blocker sibling task, and the minimal shape would be a null guard at `FeedItemlistFragment.java:658-659` that routes to the existing `error ->` branch.
3. **Does `DownloadLogTest` flake on upstream AntennaPod `develop` too, or only in this fork?** Not investigated. Relevant to whether it is worth an upstream issue and to the case study's framing (pre-existing upstream flake vs. fork-introduced). The code path involved (`FeedItemlistFragment.loadItems`, `DBReader.getFeed`) contains no migrated Kotlin — `Feed.kt` is migrated but `getId()` is a plain `var id: Long = 0` property (`Feed.kt:20`) and the NPE is on the receiver, not a Kotlin intrinsic — so there is no evidence any migration caused it.
4. **Does the fix need a regression guard?** No test can catch a Java-source caller passing null to a Kotlin non-null parameter at compile time, and adding one would mean asserting on a crash. The planner may decide that the 9 restored tests *are* the guard, or may want a note in `model/README.md` recording the tightened contract and its sole Java caller. Recommend deciding explicitly rather than by omission.
5. **Are all 9 `HttpDownloaderTest` tests actually green once they run?** Unknown and unknowable before CI — none has reached an assertion in 19 days. See "Test coverage impact".

### Sources

- `app/src/androidTest/java/de/test/antennapod/service/download/HttpDownloaderTest.java:80-97` — both `download(...)` helpers; construction site at `:87-88`
- `app/src/androidTest/java/de/test/antennapod/service/download/HttpDownloaderTest.java:119-140` — `testCancel`, construction site at `:123-124`
- `app/src/androidTest/java/de/test/antennapod/service/download/HttpDownloaderTest.java:28-29` — `@LargeTest`, no `@RunWith`, no Activity rule
- `app/src/androidTest/java/de/test/antennapod/service/download/HttpDownloaderTest.java:42-67` — `tearDown`/`setUp`, SD-card fallback at `:57-60`
- `model/src/main/java/de/danoeh/antennapod/model/download/DownloadRequest.kt:8-20` — 11-arg primary constructor, `arguments: Bundle` at `:18`
- `model/src/main/java/de/danoeh/antennapod/model/download/DownloadRequest.kt:30-43` — 9-arg convenience constructor; `arguments: Bundle` at `:38`, `username`/`password` nullable at `:36-37`
- `model/src/main/java/de/danoeh/antennapod/model/feed/Feed.kt:26` — `var localFileUrl: String? = null`
- `model/src/main/java/de/danoeh/antennapod/model/feed/Feed.kt:251-257` — `getHumanReadableIdentifier()` falls back to `downloadUrl`
- `git show 936c16aec^:model/src/main/java/de/danoeh/antennapod/model/download/DownloadRequest.java:31-33` — Java 9-arg ctor, `Bundle arguments` unannotated while `destination`/`source`/`title` are `@NonNull`
- `git show 936c16aec^:model/src/main/java/de/danoeh/antennapod/model/download/DownloadRequest.java:141` — `arguments.hashCode()` unguarded pre-migration
- `git log --oneline -1 936c16aec` — "feat(model): kotlin migration — convert DownloadRequest, Playable, RemoteMedia, FeedMedia to Kotlin", 2026-07-25
- `net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/DownloadRequestBuilder.kt:19` — `private val arguments = Bundle()`
- `net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/DownloadRequestBuilder.kt:22-38` — only two public constructors, both feed/media-bound; `:37` injects `REQUEST_ARG_PAGE_NR`
- `net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/DownloadRequestBuilder.kt:67-70` — sole production construction site
- Repo-wide `grep -rn "DownloadRequest(" --include=*.java --include=*.kt` (excluding `/build/`) — 2 test construction sites in `HttpDownloaderTest`, 1 production site in `DownloadRequestBuilder.kt`, remainder are getters or Kotlin unit tests
- `app/src/androidTest/java/de/test/antennapod/ui/DownloadLogTest.java:50-66` — `setUp()`, `clearDatabase()` at `:53`, `feed` assigned from `FeedDatabaseWriter.updateFeed` at `:65`
- `app/src/androidTest/java/de/test/antennapod/ui/DownloadLogTest.java:78-88` — `testExistingNonSubscribedFeed`, `STATE_NOT_SUBSCRIBED` at `:80`
- `app/src/androidTest/java/de/test/antennapod/EspressoTestUtils.java:204-210` — `clearDatabase()` → `PodDBAdapter.deleteDatabase()`
- `app/src/main/java/de/danoeh/antennapod/ui/screen/feed/FeedItemlistFragment.java:652-684` — `loadItems()`; NPE at `:659`; dispose-on-reentry at `:653-655`; error consumer at `:676-683`
- `app/src/main/java/de/danoeh/antennapod/storage/../DBReader.java:329-346` — `@Nullable public static synchronized Feed getFeed(...)`, returns null on missing row (full path: `storage/database/src/main/java/de/danoeh/antennapod/storage/database/DBReader.java`)
- `app/src/main/java/de/danoeh/antennapod/ui/screen/download/DownloadLogDetailsDialog.java:157-168` — `goToFeed()`; subscribed → `MainActivityStarter.withOpenFeed`, otherwise → `OnlineFeedviewActivityStarter`
- `.github/workflows/checks.yml:107-161` — `emulator-test` job, `needs: static-analysis` at `:109`, matrix at `:113-120`, no `fail-fast: false`, artifact upload at `:157-161`
- `.github/workflows/checks.yml:56-58` — `unit-test` also `needs: static-analysis`
- `.github/workflows/checks.yml:163-171` — `ci-summary` gates on `emulator-test`
- `.github/workflows/runEmulatorTests.sh:6-18` — test command, `IgnoreOnCi` filter, 3× retry
- `gh run view 31694690838` — job list and matrix-cancellation annotations
- `gh run view --job 94430805758 --log` (API 30) — attempt 1 at log lines 799588-799661 (67 tests, 9 failures, no crash); attempt 2 at 800675-800770 (34 tests, 10 failures, crash, full `FeedItemlistFragment` stack at 800719-800745); attempt 3 at 801778-801873
- `gh run view --job 94430805614 --log` (API 23) — attempt 1: 35 tests / 10 failures / `DownloadLogTest` FAILED; attempt 2: 68 tests / 9 failures / `DownloadLogTest` passed; attempt 3 canceled
- `gh run view --job 94430805695 --log` (API 36) — attempts 1 and 2 both 69 tests / 9 failures / `DownloadLogTest` passed; attempt 3 canceled
- `git diff --name-only origin/develop...HEAD` and `git diff --stat origin/develop...HEAD -- app/ net/ model/ storage/` (empty) — PR #22 touches no production or instrumented-test code
- `git show 5c1b5ebdd -- app/src/main/java/de/danoeh/antennapod/ui/screen/feed/FeedItemlistFragment.java` — only `getContext()` null guards at ~`:175`, ~`:315`, ~`:542`; `loadItems()` untouched
- `git log --oneline -5 -- app/src/androidTest/java/de/test/antennapod/ui/DownloadLogTest.java` — last touched by `f47134a7e` (#7867)
- `adb devices` — empty device list; `/Users/josegarcia/Library/Android/sdk/emulator/emulator` present but no AVD running

## Plan
_Last updated by: legacy-android-planner | 2026-08-13 (amended after red-team plan Loop 1 CHALLENGE: added D7 lifecycle re-sequencing + Steps 4-7 rework for Finding 1 CRITICAL; tightened the `DownloadLogTest` attribution ACs to require stack-trace-signature matching for Finding 2 MAJOR. Research, the core fix in Step 2, File Scope, and D2 are unchanged.)_

### Objective

Repair the two stale Java call sites in `app/src/androidTest/java/de/test/antennapod/service/download/HttpDownloaderTest.java` that pass `null` for `DownloadRequest`'s non-null `arguments: Bundle` parameter, restoring the 9 instrumented `HttpDownloader` tests to a state where they actually execute for the first time since 2026-07-25. This is `kotlin`-track fallout from `:model`'s completed Milestone 6 migration (`936c16aec`), not a new module migration — no production code changes.

### Resolved Decisions

**D1 — Fix mechanism: `new Bundle()` at both sites. Confirmed, not defaulted to.**

Research evaluated both options and recommended this; I am adopting it, and recording explicitly why the alternative is rejected rather than merely heavier.

Adopted: replace the 8th positional argument `null` with `new Bundle()` at `HttpDownloaderTest.java:88` and `:124`, adding the `android.os.Bundle` import (the file does not currently import it — confirmed against lines 1-26). Rationale:
- It reproduces the sole production caller's actual value bit-for-bit: `DownloadRequestBuilder.kt:19` holds `private val arguments = Bundle()`, and on the media path never adds a key, so an empty `Bundle` *is* production's real argument in the common case (Research, "Fix correctness").
- It preserves every other property of the constructed request, so the 9 restored assertions test exactly what they were written to test. That is the defining requirement of a characterization-preserving fix.
- It satisfies `AGENTS.md`'s minimal-diff rule: 2 changed lines plus 1 import.

Rejected — switching the test to `DownloadRequestBuilder`. Research established this is **not** a like-for-like syntax swap; it would change the requests under test, which is the one thing this fix must not do (Research, "Fix correctness — `new Bundle()` vs `DownloadRequestBuilder`"):
- The builder has no general-purpose constructor — only `(destination, FeedMedia)` and `(destination, Feed)` (`DownloadRequestBuilder.kt:22-38`).
- The `Feed` overload derives `title` from `feed.getHumanReadableIdentifier()`, which falls back to `downloadUrl` for empty titles (`Feed.kt:251-257`). `setupFeedFile` builds `new Feed(downloadUrl, "")`, so every request's title would silently become the HTTPBin URL instead of `"test200"` / `"testGzip"` / `"delay"`.
- It also rewrites `source` through `UrlChecker.prepareUrl(...)`, injects `REQUEST_ARG_PAGE_NR` into `arguments` (`DownloadRequestBuilder.kt:32,37`), and defaults `initiatedByUser = true` (`:20`) where the test needs `false`.

Aligning the test with production's construction path remains a legitimate refactor to want. It belongs in its own task with its own justification, not folded into a red-CI unblock. Recorded in Out of Scope.

There is no counter-reading in which the `null`s were intentional: no comment, no assertion on `arguments`, no `@Test` named for it, and the pre-migration Java `hashCode()` dereferenced the field unguarded (`DownloadRequest.java:141` at `936c16aec^`), so such an object was already in an unsupported state under Java. The test only got away with it by never calling `hashCode()`/`equals()`.

**D2 — Characterization: no new JVM-level characterization test. The existing 9 instrumented tests are the characterization surface, and their pre-fix state is already pinned by CI evidence.**

Research flagged characterization-test gaps, so this decision is made explicitly and argued, not skipped. I considered and reject a narrow JVM unit test against `DownloadRequest`'s constructor, for four independent reasons — any one of which is sufficient:

1. **It would pin the Kotlin compiler, not AntennaPod code.** The only oracle for "`null` throws / `new Bundle()` does not" is `Intrinsics.checkNotNullParameter`, emitted by kotlinc. Asserting it is a tautology about the toolchain, and it cannot fail in a way that indicates a regression in this repo.
2. **It would have to be a Java source file, in a test source set deliberately made 100% Kotlin.** Kotlin cannot pass `null` to a non-null parameter, so only a Java caller can express the failing shape — the "Java-oracle bright line" established in `:event` Milestone 9. But `:model`'s test source set was converted to Kotlin in its own gated milestone (Milestone 7); adding a Java test file back reverses a completed, deliberate migration decision to buy a tautology.
3. **It would require broadening a fenced Robolectric exception.** `android.os.Bundle` is not instantiable under plain JVM JUnit. `model/build.gradle` carries a checked-in comment scoping Robolectric to "Parcel characterization tests for this milestone's four files" and saying "Do not broaden usage beyond" it. This would breach that fence for no coverage gain.
4. **It would expand File Scope into `:model`**, contradicting this task's scope discipline and D3.

The correct call is therefore the `:app-wearos` precedent: verification rests on reading the diff, the local compile boundary in D6, and a real CI `emulator-test` run. This is *not* a waiver of characterization discipline — the characterization tests exist (all 9 `@Test` methods in `HttpDownloaderTest`), their pre-fix state is known with unusually high confidence (identical constructor-local failure in 7/7 independent CI executions, with the failing parameter named by the stack trace), and Step 1 records that baseline before any edit. What is unavailable is the ability to *execute* them locally, not the tests themselves.

Corollary — Research Unknown 4 (does the fix need a regression guard?): **no guard is added in this task.** No test can catch a Java-source caller passing `null` to a Kotlin non-null parameter at compile time, and the only runtime guard would be an assertion on a crash. The 9 restored tests are the practical guard, and the exposure is bounded — `HttpDownloaderTest` is the repo's only Java caller of this constructor. A durable one-line note in `model/README.md` recording the tightened contract would be cheap and is arguably exactly what `AGENTS.md` asks for, but it expands File Scope beyond the three files fixed in D3, so it is raised as **OQ1** rather than folded in silently.

**D3 — File Scope: exactly three files.** Confirmed as directed. The fix touches only the stale Java test caller. `DownloadRequest.kt`'s non-null contract is **correct** and must not be loosened — it matches the sole production construction site, and Research showed `null` was never actually supported even under the old Java. `DownloadRequestBuilder.kt` is untouched per D1.

**D4 — Branch point: fresh from `origin/develop` @ `835b9cecf`.** Verified after `git fetch`. That commit is `fix(app-wearos): guard nullable FeedItem.title at EpisodeDetailActivity render site (#26)`, and `git log origin/develop` confirms all three merged sibling PRs are ancestors: `#23` (`5ae7d560f`), `#25` (`d1e1bd127`), `#26` (`835b9cecf`). Branch name `fix/httpdownloadertest-null-bundle-argument`, matching the sibling convention (`fix/app-wearos-nullable-title-compile-error`, `fix/net-download-service-nullable-param-spotbugs`, `fix/spotbugs-nullability-findings`). **Do not branch from the currently checked-out `test/ui-preferences-sync-settings-before-screenshot`** — that is Milestone 15b's PR #22 branch, and this fix must land independently on `develop`.

**D5 — Commit/PR message discipline: do not claim this restores a green CI run.** Research refuted the cascade hypothesis with a controlled 7-attempt comparison: `DownloadLogTest.testExistingNonSubscribedFeed` is an independent ~50%-flaky defect (`FeedItemlistFragment.java:659`), tracked separately as `tasks/antennapod-fix-feeditemlistfragment-null-feed-crash.md` and sequenced in parallel per José. This plan neither depends on that fix landing first nor bundles it. Because `checks.yml`'s emulator matrix has no `fail-fast: false`, that sibling flake alone can redden the whole matrix after this fix lands. Same discipline as the SpotBugs and `:app-wearos` tasks: state what is removed, not what is restored.

**D6 — Verification boundary: what is achievable here vs. what waits for CI.** No local emulator (`adb devices` empty, no AVD), so `connectedPlayDebugAndroidTest` cannot run. Three local commands are available, and each has a precisely bounded meaning the developer must not overstate:

| Command | What it proves | What it does **not** prove |
|---|---|---|
| `./gradlew assemblePlayDebugAndroidTest` (exact CI command, `checks.yml:140`) | The `android.os.Bundle` import resolves and the edited calls still bind to the 9-arg convenience constructor at `DownloadRequest.kt:30-43` | **Nothing about the bug.** javac never enforced the Kotlin non-null metadata — this task compiled clean *before* the fix too. Green here is necessary, not sufficient. |
| `./gradlew :app:assembleDebug` (the `AGENTS.md`-listed compile command) | The `:app` main variant still builds | **Zero signal on the changed file** — `assembleDebug` does not compile the `androidTest` source set. |
| `./gradlew checkstyle lint` | Repo-wide style/lint hygiene unchanged | **Zero signal on the changed file** — `common.gradle:147-155` scopes the `checkstyle` task to `src/main/java`, `src/free/java`, `src/play/java` only. `src/androidTest/java` is not checked, so `UnusedImports` will *not* catch a stray import. The added import must be verified used by inspection. |

Only a real CI `emulator-test` run can establish that the 9 tests pass. Per Research, "the 9 tests pass" is a hypothesis, not a foregone conclusion — none has reached an assertion in 19 days, so whatever they assert about redirects, gzip, cancellation and basic-auth is unverified until CI executes them. The ACs below are written to that boundary.

Note on command provenance: `assemblePlayDebugAndroidTest` is not in `AGENTS.md`'s command list, but it is the verbatim command `.github/workflows/checks.yml:140` runs, so it is repo-blessed rather than invented — and `AGENTS.md`'s listed `:app:assembleDebug` structurally cannot compile this file, so following the list literally would produce false assurance.

**D7 — Lifecycle sequencing: this task's PR is opened *before* the implementation-stage red-team pass, and a real CI run is fetched and checked as an explicit step. Added Loop 1 in response to red-team Finding 1 (CRITICAL).**

The three merged sibling tasks (`#23`, `#25`, `#26`) each put `Red-team implementation` before `PR opened`, and that ordering was correct for them: their fixes were fully verifiable locally (`checkstyle`, `spotbugs`, `:app:assembleDebug` are the *same* checks CI runs), so the reviewer and red-team had real signal in hand at review time. **This task cannot inherit that ordering**, and D6 is the proof: all three locally-runnable commands give zero signal on whether the fix works, because the bug is a pure runtime NPE that javac never checked and that `assemblePlayDebugAndroidTest` compiled clean *before* the fix. Copying the sibling lifecycle here would have `migration-code-reviewer` and the implementation-stage `legacy-android-red-team` both sign off with no evidence that the 9 tests pass — closing the task on Research's explicitly-stated *hypothesis*.

Compounding it, verified directly: `checks.yml:3-7` triggers only on `pull_request: [opened, synchronize, reopened]` and `push` to `master`/`develop`. A feature-branch push alone triggers nothing. **Opening the PR is therefore not the last act of this task — it is the instrument that produces this task's only real evidence**, and it must happen before the gates that depend on that evidence.

Adopted sequence for this task, deliberately different from the three siblings:

`Implement` → `Code review` (static diff review; runs on the 3-file diff and needs no CI) → **`PR opened`** (triggers `checks.yml`'s `pull_request` event — the only way to get `emulator-test`) → **`CI verification`** (Step 6: fetch the actual run, record verbatim per-test results) → `Red-team implementation` (explicitly conditioned on the CI-verification AC block being satisfied with **real run data**, not pending) → `PR merged`.

Two consequences, stated plainly so no one later reads this as sloppiness:
- `migration-code-reviewer` reviews before CI exists. That is fine and intended — its job is scope, diff correctness, and Plan conformance, all of which are locally checkable. It must **not** mark the "CI verification" AC block satisfied; only Step 6 can.
- The implementation-stage red-team pass is the gate that owns the CI evidence. If Step 6's run has not completed, that pass does not render APPROVE on a pending run — it waits or returns CHALLENGE. **A hoped-for CI result is not a CI result.**

Precedent for using an open PR to obtain mid-review signal already exists in this very investigation: Milestone 15b's own branch carries `c17903102` "ci: trigger workflow run for AC13 verification" and `8203a70d7`, commits whose entire purpose was to make CI produce evidence during review.

### Steps

1. **Record the pre-fix characterization baseline** in this task file's Implementation Notes, before any edit: the two construction sites verbatim with their current line numbers (`HttpDownloaderTest.java:87-88` and `:123-124`), the CI failure signature (`NullPointerException: Parameter specified as non-null is null: … parameter arguments`), and the 7/7 deterministic-failure table from Research. Confirm by `grep -n "new DownloadRequest(" app/src/androidTest/java/de/test/antennapod/service/download/HttpDownloaderTest.java` that exactly two sites exist and no third has appeared. Also record verbatim the D2 statement that no new characterization test is added and why. No code changes; build stays green. Tests: none added — the characterization tests are the 9 existing `@Test` methods in `HttpDownloaderTest`, named individually in the ACs.

2. **Replace `null` with `new Bundle()` at both `DownloadRequest` construction sites** in `app/src/androidTest/java/de/test/antennapod/service/download/HttpDownloaderTest.java` (the 8th positional argument at `:88` inside `download(url, title, expectedResult, deleteExisting, username, password)`, and at `:124` inside `testCancel()`), and add the `android.os.Bundle` import. Change nothing else — no reordering, no comments, no reformatting, no touching the adjacent `username`/`password` nulls at `:124`, which correctly bind to nullable parameters (`DownloadRequest.kt:36-37`). Tests: no new test file; this restores execution of the 9 existing tests `testPassingHttp`, `testRedirect`, `testGzip`, `test404`, `testCancel`, `testDeleteOnFailShouldDelete`, `testDeleteOnFailShouldNotDelete`, `testAuthenticationShouldSucceed`, `testAuthenticationShouldFail`.

3. **Run the D6 verification matrix and record every result verbatim** in Implementation Notes — including the explicit statement of what each command does and does not prove, and the plain statement that the 9 tests were not executed locally because no emulator or device is available. Do not summarize or characterize a command's output as "verified" beyond its row in the D6 table.

4. **Request `migration-code-reviewer` on the local diff, before the PR exists.** Per D7, code review here is a static review of the 3-file diff against this Plan (scope, the two substitutions, the import, `AGENTS.md` minimal-diff) and does not require CI. The reviewer must **not** tick any item in the "CI verification" AC block — that block is Step 6's alone and is still unsatisfiable at this point. Tests: none added; the 9 named `HttpDownloaderTest` methods remain the characterization surface.

5. **Open the PR against `develop`** from `fix/httpdownloadertest-null-bundle-argument`, following `.github/pull_request_template.md`, with a description that observes D5 — it must state that this removes one of two known `emulator-test` blockers and must **not** claim it restores a green `emulator-test` or `Checks` run, naming `tasks/antennapod-fix-feeditemlistfragment-null-feed-crash.md` as the other one needed for a *reliable* (not retry-lucky) green. Per D7 this step is **not** the end of the task: opening the PR is what fires `checks.yml`'s `pull_request` trigger (`checks.yml:3-7` — a feature-branch push alone triggers nothing), and it is the instrument that produces Step 6's evidence. Do not commit or push until José explicitly asks.

6. **Fetch the real `emulator-test` run and check its actual result against the "CI verification" AC block — this step, not Step 5, is what closes the task.** Wait for the run triggered by Step 5 to complete (`gh run list --branch fix/httpdownloadertest-null-bundle-argument --workflow=Checks`), then pull its outcome (`gh run view <run-id>`, and `gh run view --job <job-id> --log` for each `emulator-test` API-level leg, as Research did for run `31694690838`). Record verbatim in Implementation Notes, per API level and per retry attempt: the run ID and URL, whether `emulator-test` reached `HttpDownloaderTest` at all, the pass/fail status of each of the 9 named tests, the total tests-completed and failure count per attempt, and the full stack trace of every remaining failure. If `emulator-test` did not run (e.g. gated behind a red `static-analysis` again), say so explicitly and do not substitute local command results for it. If the run is still in progress, this step is **incomplete** — record it as pending and do not proceed to the implementation-stage red-team pass. Tests: no new tests; this executes the 9 existing ones for the first time since 2026-07-25. Note that this step touches only the task file, so the build stays green by construction.

7. **Request the implementation-stage `legacy-android-red-team` pass, conditioned on Step 6's real run data.** Per D7 this is the gate that owns the CI evidence: it verifies the "CI verification" AC block against the recorded run, including the stack-trace-signature check on any residual `DownloadLogTest` failure. It must not render APPROVE against a pending, queued, or hoped-for run.

### File Scope

- `app/src/androidTest/java/de/test/antennapod/service/download/HttpDownloaderTest.java` — both construction sites plus the `android.os.Bundle` import
- `tasks/antennapod-fix-httpdownloadertest-null-bundle-argument.md` — Implementation Notes, review verdicts
- `features/antennapod-fix-httpdownloadertest-null-bundle-argument.checkpoint.md` — status/lifecycle bookkeeping

Explicitly **not** in scope: `model/src/main/java/de/danoeh/antennapod/model/download/DownloadRequest.kt`, `net/download/service-interface/src/main/java/.../DownloadRequestBuilder.kt`, `model/README.md` (see OQ1), `app/src/main/java/de/danoeh/antennapod/ui/screen/feed/FeedItemlistFragment.java`, and any file under `model/src/test/`. The reviewer rejects diffs touching anything outside the three files above.

### Acceptance Criteria

Only the `kotlin` track applies. No `gradle-kts`, `di`, `concurrency`, `compose`, or `navigation` work is requested, and no composable, screen, or navigation graph is introduced — so no Paparazzi snapshot, accessibility, or dark-mode criteria apply.

Characterization baseline (Step 1, before any edit)
- [x] Implementation Notes record both pre-fix call sites verbatim with line numbers, the exact CI NPE message naming `parameter arguments`, and the 7-attempt failure table
- [x] `grep -n "new DownloadRequest(" app/src/androidTest/.../HttpDownloaderTest.java` returns exactly 2 matches, recorded verbatim
- [x] Implementation Notes state, in the developer's own recording, that no new JVM characterization test is added and reproduce D2's four reasons — no AC anywhere claims a new test was written

The fix
- [x] Exactly two `null` → `new Bundle()` substitutions, at the 8th positional argument of each 9-arg `DownloadRequest` call
- [x] `import android.os.Bundle;` added and actually used (verified by inspection, since `checkstyle`'s `UnusedImports` does not cover `src/androidTest/java` — D6)
- [x] `git diff --stat origin/develop -- app/` shows **3 insertions / 2 deletions** in exactly one file, and no other production or test file changed
- [x] The `username`/`password` `null` arguments at `:124` are unchanged — they bind to `String?` and are correct as-is
- [x] No comment added to the changed lines, no reformatting, no reordering (`AGENTS.md`)

Local verification, bounded per D6
- [x] `./gradlew assemblePlayDebugAndroidTest` is `BUILD SUCCESSFUL`, and Implementation Notes state alongside it that this compiled clean before the fix too and is therefore necessary-not-sufficient evidence
- [x] `./gradlew :app:assembleDebug` is `BUILD SUCCESSFUL`, recorded with the note that it does not compile the `androidTest` source set
- [x] `./gradlew checkstyle lint` outcome recorded verbatim whether green or not, with the note that `common.gradle:147-155` excludes `src/androidTest/java` so this gives zero signal on the changed file
- [x] Implementation Notes state plainly that the 9 tests were **not** run locally, and why (`adb devices` empty, no AVD; `connectedPlayDebugAndroidTest` requires a live device)

CI verification — the only evidence that actually closes this task (Step 6, after the PR is open; see D7)

**These items are closed by the actual result of a completed CI run, recorded verbatim — never by a predicted, expected, or in-progress one.** A pending run leaves every box below unticked. No local command from the D6 table may be substituted for any item here. `migration-code-reviewer` (Step 4) may not tick these; only Step 6 can.

- [x] Implementation Notes name a specific, **completed** `Checks` run — run ID and URL — triggered by opening the PR in Step 5, in which the `emulator-test` job actually executed (not skipped, not cancelled, not gated behind a red `static-analysis`)
- [x] That run's recorded output shows `HttpDownloaderTest` **executed** and **0 failures in that class** across all 9 named tests (`testPassingHttp`, `testRedirect`, `testGzip`, `test404`, `testCancel`, `testDeleteOnFailShouldDelete`, `testDeleteOnFailShouldNotDelete`, `testAuthenticationShouldSucceed`, `testAuthenticationShouldFail`), on at least one API level, quoted from `gh run view --job <job-id> --log` rather than summarized
- [x] The per-attempt failure count in that run drops from 9-10 to at most 1, and no remaining failure is in `HttpDownloaderTest`
- [x] If any of the 9 fails on a *different*, post-constructor assertion (a real `HttpDownloader`/HTTPBin defect that has been invisible for 19 days), it is recorded as a new finding and escalated — **not** patched inside this task, whose File Scope does not permit it
- [x] Any residual `DownloadLogTest` failure is attributed to the sibling task **only after its stack trace is confirmed to match the diagnosed signature** — `NullPointerException: Attempt to invoke virtual method 'long de.danoeh.antennapod.model.feed.Feed.getId()' on a null object reference`, thrown from the `loadItems` lambda frame in `FeedItemlistFragment.java` (line `659` as diagnosed; a shifted line number in the same lambda still matches, a different method does not), on an `ObservableFromCallable.subscribeActual` background-thread frame. The trace must be pasted verbatim into Implementation Notes next to the attribution, not paraphrased
- [x] If a `DownloadLogTest` failure's trace does **not** match that signature — different exception, different frame, a plain assertion failure, or no process crash where one is expected — it is escalated as a **new finding** and this task does not close on it. Test-name matching alone is explicitly insufficient: Research Unknown 1 flags that removing the 9 `HttpDownloaderTest` failures changes suite timing and may shift the underlying race in either direction, so a new defect could surface on the same test method
- [x] A matched (signature-confirmed) `DownloadLogTest` failure does not by itself block this PR

Scope and interop
- [x] `git diff --name-only origin/develop` lists only the three File Scope files
- [x] No public API break: `DownloadRequest`'s signature, nullability, and behavior are untouched, so no `:model` consumer — Java or Kotlin — is affected. The contract is deliberately **not** loosened back to nullable
- [x] No new user-visible string, so no `ui/i18n/src/main/res/values/strings.xml` change
- [x] No new Gradle dependency, no new source set, no version-catalog change, no Robolectric broadening in `model/build.gradle`

Idiom
- [x] `new Bundle()` matches the sole production caller's value (`DownloadRequestBuilder.kt:19`); no `!!`-equivalent workaround, no `@SuppressWarnings`, no reflection, no cast
- [x] The fix is applied at the call sites, not by weakening the callee's contract

Reporting honesty (D5)
- [x] The PR description does **not** claim this restores a green `emulator-test` or `Checks` run
- [x] It names `tasks/antennapod-fix-feeditemlistfragment-null-feed-crash.md` as independently required for a *reliable* green, and does not assert either task depends on the other
- [x] The commit message describes the fix as repairing a stale Java caller of a correctly-tightened Kotlin contract — not as fixing `DownloadRequest`, and not as fixing CI
- [x] Neither the PR nor the commit claims new test coverage was added; both state the 9 restored tests are pre-existing

### Milestone

Not a billable migration-track milestone in its own right. This is **CI-blocker sibling task 4 of 4** in the 2026-08-13 investigation (after `#23`, `#25`, `#26`), and it unblocks **Milestone 15b — `:ui:preferences` sync-settings before-screenshot (PR #22), AC13's real-CI-signal requirement**. Its case-study value sits with the `:model` `kotlin` track (Milestone 6): it is the first documented instance of the **contract-tightening** J2K hazard — the inverse of the widening hazard behind all three siblings — where the migration correctly narrowed an unannotated Java parameter to non-null and a stale Java caller in a source set the compiler does not cross-check broke silently for 19 days.

### Out of Scope

- **Fixing `FeedItemlistFragment.java:659`'s null-feed dereference.** Independent, pre-existing `develop` defect, tracked as `tasks/antennapod-fix-feeditemlistfragment-null-feed-crash.md`, not yet researched, sequenced in parallel per José. This plan neither depends on it nor includes it.
- **Rewriting `HttpDownloaderTest` to construct requests via `DownloadRequestBuilder`.** Rejected in D1 as behavior-changing, not stylistic. A legitimate follow-up refactor, needing its own task and its own justification.
- **Converting `HttpDownloaderTest.java` to Kotlin.** Not requested; `:app`'s androidTest source set is not in any module's `kotlin`-track scope.
- **Loosening `DownloadRequest.arguments` back to nullable**, or adding a `@Nullable` overload. The contract is correct.
- **Adding a JVM characterization test for the non-null contract** — see D2.
- **A `model/README.md` note recording the tightened contract** — see OQ1; excluded from File Scope pending José's answer.
- **Any RxJava/`concurrency`-track work.** Research reported `FeedItemlistFragment`'s `Observable.fromCallable().subscribeOn(Schedulers.computation())` as fact, not as a migration recommendation.
- **Investigating whether `DownloadLogTest` flakes on upstream AntennaPod `develop`** (Research Unknown 3). Belongs to the sibling task.
- **Re-running or rebasing Milestone 15b's PR #22.** A separate follow-up action tracked in this task's checkpoint, taken after this merges.

## Open Questions

1. **Should a one-line contract note be added to `model/README.md`?** Research Unknown 4 asked whether the fix needs a regression guard. D2 resolves the test question (no test is possible or proportionate), but leaves one cheap durable option: a `model/README.md` line stating that `DownloadRequest.arguments` is non-null, that callers should pass a real `Bundle` (empty is fine) as the sole production path does, and that Java callers get no compile-time check. This is exactly the "long-term stable and generic" module knowledge `AGENTS.md` asks agents to record proactively, and the `:app-wearos` sibling set a direct precedent by documenting its invariant in `net/sync/wear-interface/README.md`. It is excluded from File Scope only because José fixed this task's scope at three files, and folding it in silently would breach that. **For José: add `model/README.md` to File Scope, or leave it out and let the note ride along with a future `:model` task?** Default if unanswered: leave it out and ship the three-file diff.

2. **How many `emulator-test` re-runs are acceptable before escalating?** (Research Unknown 1.) With the sibling `DownloadLogTest` flake unfixed and no `fail-fast: false` in `checks.yml:112-120`, a single flaky leg reddens the whole matrix. Every API level had at least one clean attempt in the observed run, so the 3× retry in `runEmulatorTests.sh:12` makes a green run likely — but API 30 flaked on 2 of its 3 attempts, and removing the 9 `HttpDownloaderTest` failures changes suite timing, which may shift the race either way. This plan's ACs deliberately gate on "0 failures **in `HttpDownloaderTest`**" rather than "green matrix," so this task can complete honestly on a red-but-explained run. **For José: does Milestone 15b's AC13 accept a retry-dependent green, or does it wait for the sibling fix?** This does not block starting implementation here.

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-08-13 | Loop 1 of max 2_

### Verdict
CHALLENGE

### Concerns

- **Severity:** CRITICAL
- **Class:** Characterization tests prove equivalence, not just existence / Coverage gaps left unaddressed
- **Concern:** The Plan repeatedly and correctly insists that a real CI `emulator-test` run is "the only evidence that actually closes this task" (D6; AC section "CI verification"), because no local emulator exists. But the Plan's own Steps (1-4) end at "open the PR" with no step that waits for, retrieves, and records the actual CI outcome — and I independently confirmed `checks.yml:3-7` triggers `emulator-test` only on `pull_request: [opened, synchronize, reopened]` or `push` to `master`/`develop` (not feature branches). So the *only* way to obtain the CI evidence the Plan calls decisive is to open the PR — which Step 4 places last, and which this task's own checkpoint's Lifecycle progress list (mirroring the two already-completed sibling tasks, `antennapod-fix-spotbugs-static-analysis-debt` and `antennapod-fix-app-wearos-compile-error`) places **after** both `Code review` and `Red-team implementation`. In both sibling precedents that ordering was harmless because their fixes were fully verifiable locally (`checkstyle`/`spotbugs`/`assembleDebug` — real signal, confirmed green before PR). This task is different in kind: D6 itself proves `assemblePlayDebugAndroidTest`, `:app:assembleDebug`, and `checkstyle lint` all give **zero signal** on whether the fix actually works, because the bug is a pure runtime NPE javac never checked. Following the established lifecycle order here means `Code review` and `Red-team implementation` (this very gate) would render their verdicts before any CI run exists to satisfy the "CI verification" AC block — leaving no defined point in the Plan where "the 9 tests pass" (Research's own stated hypothesis-not-foregone-conclusion) is ever actually confirmed before the task is treated as done. This is exactly the unproven-equivalence-claim risk this review exists to catch, and it is a planning gap, not a hypothetical: the Plan supplies detailed rules for what each local command does and doesn't prove, but no rule for when/whether the one command that does prove it (a real CI run) gets executed and checked before sign-off.
- **Evidence:** Plan §D6 ("Only a real CI `emulator-test` run can establish that the 9 tests pass... `emulator-test` will pass") and Acceptance Criteria "CI verification — the only evidence that actually closes this task" (lines ~318-322) vs. Plan §Steps 1-4 (Step 4, "Open the PR against `develop`... Do not commit or push until José explicitly asks," with no Step 5); `.github/workflows/checks.yml:3-7` (`on: pull_request: types: [opened, synchronize, reopened]` / `push: branches: [master, develop]` — confirmed by direct read, not cited anywhere in Research's or the Plan's Sources); `features/antennapod-fix-spotbugs-static-analysis-debt.checkpoint.md` and `features/antennapod-fix-app-wearos-compile-error.checkpoint.md` Lifecycle progress lists, both showing `Red-team implementation` completing before `PR opened`.
- **Suggested mitigation:** Add an explicit step (or amend Step 4) instructing the developer to push the branch and open the PR specifically to trigger `checks.yml`'s `pull_request` event *before* requesting `migration-code-reviewer` or the implementation-stage `legacy-android-red-team` pass, and to record the actual CI run's verbatim per-test results in Implementation Notes as a precondition for those two gates — mirroring how PR #22's own "ci: trigger workflow run for AC13 verification" commits already use push-to-open-PR to get real signal mid-review. Alternatively, explicitly amend this task's checkpoint lifecycle to insert a "CI verification" stage between `Code review` and `Red-team implementation`, and state plainly that this task's implementation-stage red-team review is conditioned on the "CI verification" AC block being satisfied with real run data, not merely pending. Either fix is a Plan/Steps wording change only — it does not touch File Scope or the 3-file diff.

- **Severity:** MAJOR
- **Class:** Coverage gaps left unaddressed
- **Concern:** The AC "Any residual `DownloadLogTest` failure is attributed to the sibling task, not to this fix, and does not by itself block this PR" attributes by test name alone, with no requirement to confirm the failure's actual mechanism matches the one Research diagnosed (NPE at `FeedItemlistFragment.java:659` on a null `Feed.getId()` receiver, surfacing as an RxJava-undeliverable-exception process crash). Research's own Unknown 1 explicitly flags that "removing the 9 `HttpDownloaderTest` failures changes suite timing, which may shift the race in either direction — unpredictable from here." As written, the AC would let a developer wave off *any* `DownloadLogTest` failure in the post-fix CI run as "the known flake" without checking that it's the same flake — including a failure this fix's own timing shift newly introduced or exposed, or a genuinely different defect that happens to land on the same test method.
- **Evidence:** Plan, Acceptance Criteria, "CI verification" section, final bullet, vs. Research, Unknowns, item 1 (quoted above) and the "Secondary failure — cascade hypothesis REFUTED" section's own stack-trace-based methodology (the refutation itself rested on matching the specific `FeedItemlistFragment.java:659` trace across 7 attempts, not on test-name pattern-matching).
- **Suggested mitigation:** Tighten the AC to require confirming the residual `DownloadLogTest` failure's stack trace names `FeedItemlistFragment.java:659` / `Feed.getId()` on a null receiver before attributing it to the sibling task; if the trace differs, treat it as a new finding per the adjacent AC ("escalated — not patched inside this task"), not as the pre-diagnosed flake.

### Independently verified, no issue found

- Read `HttpDownloaderTest.java` in full: exactly two `new DownloadRequest(...)` sites, at the current lines **87-88** and **123-124**, both matching the Plan's quoted text verbatim; `grep -n "new DownloadRequest("` returns exactly 2 matches. No `android.os.Bundle` import present in lines 1-26 today.
- Read `DownloadRequest.kt` in full and re-derived overload resolution myself, not from the citation: both 9-argument calls bind to the 9-arg convenience constructor (`:30-43`), whose 8th positional parameter is `arguments: Bundle` (`:38`, genuinely non-null, no `?`). Confirmed the 3-null run at `:123-124` lands its 8th argument (third `null`) on `arguments`, not `username`/`password` (both nullable at `:36-37`). No other parameter of either call site is at risk after the fix (`destination`, `source`, `title` are all non-null-typed but receive real non-null runtime values).
- Repo-wide `grep -rn "DownloadRequest(" --include="*.java" --include="*.kt" .` (excluding `/build/`): confirms exactly 2 test construction sites (`HttpDownloaderTest.java`), exactly 1 production site (`DownloadRequestBuilder.kt:67`, using `Bundle()` per `:19`), and all remaining hits are Kotlin unit tests (`DownloadRequestTest.kt`, `DownloadRequestBuilderCharacterizationTest.kt`, which cannot pass `null` to a non-null Kotlin parameter) or unrelated getter calls. Matches Research's claim exactly.
- Verified D2's four reasons against source directly, not the planner's paraphrase: `model/src/test/` contains **zero** `.java` files (29 `.kt` files enumerated), confirming claim (b); `model/build.gradle`'s Robolectric dependency carries the exact checked-in fence comment claim (c) describes ("Do not broaden usage beyond Parcel characterization tests for this milestone's four files"); claim (a)'s reasoning (a Java-source test could only observe `Intrinsics.checkNotNullParameter`, since Kotlin cannot even compile a null literal into a non-null parameter) is internally consistent with (b); claim (d) is trivially true given D3's File Scope.
- Verified D6's verification-boundary claims against source directly: `common.gradle:147-158`'s `checkstyle` task sources only `src/main/java`, `src/free/java`, `src/play/java` — `src/androidTest/java` is genuinely excluded, confirming zero signal on `UnusedImports` for this file. `checks.yml:140` runs `./gradlew assemblePlayDebugAndroidTest` verbatim inside the `emulator-test` job (`:107-140`), confirming command provenance. `:app:assembleDebug` is a distinct task from `assemblePlayDebugAndroidTest` and does not touch the `androidTest` source set.
- Checked the AC's specific `git diff --stat` claim ("3 insertions / 2 deletions") by hand: two single-line substitutions (`null` → `new Bundle()` on the existing lines `:88` and `:124`, each counted as 1 deletion + 1 insertion by line-based diff) plus one new `import android.os.Bundle;` line (1 insertion) totals exactly 3 insertions / 2 deletions. Arithmetic holds.
- Confirmed `tasks/antennapod-fix-feeditemlistfragment-null-feed-crash.md` exists (untracked, Research not yet started) with pre-research context matching D5's characterization of it verbatim — the cross-reference is not a dangling citation.
- No `gradle-kts`/`di`/`concurrency`/`compose`/`navigation` track concerns apply — no composable, DI binding, coroutine scope, navigation graph, or build-script behavior is touched by this diff; correctly excluded by the Plan.
- Public API: confirmed via the repo-wide grep above that `DownloadRequest`'s constructor signature and nullability are unmodified and no consumer besides the two fixed test call sites is affected. No scope creep: File Scope is exactly the 3 named files, and D1's rejection of the `DownloadRequestBuilder` refactor is independently justified by reading `DownloadRequestBuilder.kt`'s two feed/media-bound constructors, which indeed have no general-purpose overload and would rewrite `title`/`source`/`arguments`/`initiatedByUser` as described.

Loop 1 of max 2. Planner should add the CI-verification sequencing step (or amend the checkpoint lifecycle) and tighten the `DownloadLogTest` attribution AC, then re-invoke red-team.

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-08-13 | Loop 2 of max 2 (final)_

### Verdict
APPROVE

### Concerns

- **Severity:** MINOR
- **Class:** Coverage gaps left unaddressed
- **Concern:** Step 6/the CI-verification AC block say the recorded run must be "the run triggered by opening the PR in Step 5," but neither Step 6 nor the AC explicitly says what to do if a `synchronize` event produces a *later* run (e.g. a follow-up push after the initial PR-open run) — nothing pins the recorded run to the PR's current HEAD commit. Given File Scope is a 2-line-plus-import diff with no expected follow-up commits, this is a low-probability gap, not a live risk, and it does not undermine the loop-1 fix.
- **Evidence:** Plan Step 6 ("Fetch the real `emulator-test` run... Wait for the run triggered by Step 5 to complete") and the CI-verification AC block's opening rule.
- **Suggested mitigation:** If a future push to the PR branch occurs before merge, Step 6/7 should re-fetch and re-verify against the run matching the branch's then-current HEAD SHA, not an earlier one. Worth a one-line clarification if this task's PR ever needs a follow-up commit, but not worth blocking Loop 2 over.

### Independently re-verified (not taken on the planner's report)

1. **`checks.yml`'s trigger configuration, read directly (`.github/workflows/checks.yml:1-7`):** `on: pull_request: types: [opened, synchronize, reopened]` and `push: branches: [master, develop]`. No `push` trigger for arbitrary feature branches. This confirms both Loop 1's finding and the Plan's D7/D6 claims verbatim — a feature-branch push alone fires nothing; only opening (or updating, or reopening) the PR, or pushing straight to `develop`/`master`, triggers the workflow that contains `emulator-test`. Also re-read `emulator-test`'s own definition (`checks.yml:107-161`): `needs: static-analysis`, 3-way API matrix (23/30/36), no `fail-fast: false` — matches every claim the Plan makes about it.

2. **D7 and Steps 4-7, read directly.** The sequencing is `Implement → Code review (Step 4, static/local diff) → PR opened (Step 5) → CI verification (Step 6) → Red-team implementation (Step 7) → PR merged`. Step 4's boundary is explicit, not implied: *"The reviewer must **not** tick any item in the 'CI verification' AC block — that block is Step 6's alone and is still unsatisfiable at this point."* Step 6 is explicit that an in-progress run blocks progression: *"If the run is still in progress, this step is **incomplete**... and do not proceed to the implementation-stage red-team pass."* Step 7 is explicit that it "must not render APPROVE against a pending, queued, or hoped-for run." I traced every path through the new sequence and found no point where sign-off (code review or red-team) can occur without a completed CI run's real data standing behind the CI-verification ACs — Step 4 is walled off from that AC block by name, and Steps 6-7 are chained by an explicit incompleteness gate. The checkpoint's Lifecycle progress list mirrors this ordering exactly, including the note explaining the deliberate divergence from the three merged siblings. This closes Loop 1 Finding 1 in full.

3. **CI-verification AC wording, read for gaming resistance.** Confirmed each loophole Loop 1 could have left is closed by name:
   - *Still-in-progress run claimed as compliant* — closed by the AC block's own opening rule ("never by a predicted, expected, or in-progress one. A pending run leaves every box below unticked") and independently enforced by Step 6's incompleteness gate.
   - *`emulator-test` skipped/cancelled/gated behind a red `static-analysis`* — closed by the first CI-verification AC bullet naming all three failure modes explicitly ("not skipped, not cancelled, not gated behind a red `static-analysis`") as disqualifying.
   - *Local command results substituted for CI evidence* — closed twice: the AC block's opening rule ("No local command from the D6 table may be substituted for any item here") and Step 4's explicit bar on the reviewer ticking these items.
   - *`migration-code-reviewer` closing these ACs early* — closed explicitly ("`migration-code-reviewer` (Step 4) may not tick these; only Step 6 can").
   I could not construct a sequence of plausible developer actions that satisfies the CI-verification AC block without a genuinely completed run in hand.

4. **`DownloadLogTest` attribution AC, read for precision.** The required signature is: exception type + message (`NullPointerException: Attempt to invoke virtual method 'long de.danoeh.antennapod.model.feed.Feed.getId()' on a null object reference`), the specific calling frame (thrown from the `loadItems` lambda in `FeedItemlistFragment.java`, line 659 as diagnosed, with an explicit allowance that a shifted line number *in the same lambda* still matches but *a different method does not*), and the background-thread frame (`ObservableFromCallable.subscribeActual`). This is exactly the three-part bar this checklist calls for (file:line, exception type/message, calling frame) — not test-name matching. The companion bullet explicitly forecloses the exact failure mode Loop 1 flagged: *"Test-name matching alone is explicitly insufficient... a new defect could surface on the same test method,"* citing Research's own Unknown 1 about the timing shift. A verbatim-paste requirement ("not paraphrased") prevents a developer from asserting a match without showing the evidence for a reviewer to check. This closes Loop 1 Finding 2 in full.

5. **Drift check across Research, Step 2, File Scope, D2, D6.** Read all five sections in full against Loop 1's "Independently verified, no issue found" record (same file, lines documenting what I checked in Loop 1): the two `DownloadRequest` construction sites (`HttpDownloaderTest.java:87-88`, `:123-124`), the 9-arg-constructor overload resolution against `DownloadRequest.kt:30-43`, the repo-wide construction-site grep result, D2's four no-new-test reasons (Robolectric fence in `model/build.gradle`, `model/src/test/`'s 100%-Kotlin composition, the `Intrinsics.checkNotNullParameter` tautology argument, File Scope discipline), D6's verification-boundary table (`checkstyle`'s `src/main/java`/`src/free/java`/`src/play/java` scoping in `common.gradle:147-155`, `assemblePlayDebugAndroidTest`'s exact provenance from `checks.yml:140`), and File Scope's three-file list — all word-for-word unchanged from what I reviewed in Loop 1. As a bonus check not required by this loop, I also verified D4's branch-point ancestry claim directly (`git merge-base --is-ancestor 5ae7d560f 835b9cecf` and same for `d1e1bd127` both confirm ancestry) — consistent, no drift found anywhere outside the two targeted fixes.

### Basis for APPROVE

Both Loop 1 findings are resolved with mechanisms, not assertions: Finding 1's gap (no defined point where real CI evidence is obtained and checked before sign-off) is closed by a re-sequenced lifecycle with two independent, explicit gates (Step 4's bar on ticking CI ACs; Step 6/7's bar on proceeding without a completed run) rather than a single soft instruction. Finding 2's gap (test-name-only attribution) is closed by a three-part stack-trace signature requirement with an explicit escalation path for non-matches and a verbatim-paste evidentiary requirement. Both fixes were verified against source and AC text directly, not summarized from the planner's changelog. The one MINOR item above (run-freshness pinning across a hypothetical follow-up push) does not undermine either fix and does not warrant a third loop.

This is Loop 2 of max 2 — the maximum has been reached. The plan is APPROVED and ready for `android-migration-developer`. Flagging for the record, not as a blocker: unlike the three merged sibling tasks in this chain, this task's implementation will genuinely pause mid-flow — after Step 5 opens the PR, Step 6 must wait for a real CI run to complete before Step 7 (red-team implementation) can proceed. That pause is intentional and load-bearing, not a stall to work around.

## Implementation Notes
_By: android-migration-developer | 2026-08-14_

### Step 1 — Pre-fix characterization baseline (recorded before any edit)

**Confirmation grep, run before any edit:**
```
$ grep -n "new DownloadRequest(" app/src/androidTest/java/de/test/antennapod/service/download/HttpDownloaderTest.java
87:        DownloadRequest request = new DownloadRequest(feedFile.getLocalFileUrl(), url, title, 0, Feed.FEEDFILETYPE_FEED,
123:        final Downloader downloader = new HttpDownloader(new DownloadRequest(feedFile.getLocalFileUrl(),
```
Exactly 2 matches, as Research and the Plan state. No third site has appeared. Proceeding.

**Call site 1 — `HttpDownloaderTest.java:87-88`, inside `download(url, title, expectedResult, deleteExisting, username, password)`, verbatim pre-fix:**
```java
87:        DownloadRequest request = new DownloadRequest(feedFile.getLocalFileUrl(), url, title, 0, Feed.FEEDFILETYPE_FEED,
88:                username, password, null, false);
```
The 8th positional argument (`null` on line 88, third argument on that continuation line) binds to `arguments: Bundle` in the 9-arg convenience constructor (`DownloadRequest.kt:30-43`, `arguments` declared at `:38`, non-null).

**Call site 2 — `HttpDownloaderTest.java:123-124`, inside `testCancel()`, verbatim pre-fix:**
```java
123:        final Downloader downloader = new HttpDownloader(new DownloadRequest(feedFile.getLocalFileUrl(),
124:                url, "delay", 0, Feed.FEEDFILETYPE_FEED, null, null, null, false));
```
Three `null`s in a row on line 124. First two (`username`, `password`) bind to nullable `String?` params (`DownloadRequest.kt:36-37`) and are correct as-is. The third `null` is the 8th positional argument, `arguments: Bundle`, and is the one that crashes.

**CI failure signature (exact NPE message from run `31694690838`, per Research):**
```
java.lang.NullPointerException: Parameter specified as non-null is null: method de.danoeh.antennapod.model.download.DownloadRequest.<init>, parameter arguments
```
This names the failing parameter directly, removing ambiguity about which of the three trailing `null`s at line 124 is responsible.

**7-attempt deterministic-failure table (from Research, CI run `31694690838`):**

| API | Attempt | Tests completed | Failures | `DownloadLogTest` | Process crash |
|---|---|---|---|---|---|
| 23 | 1 | 35 | 10 | **FAILED** | yes |
| 23 | 2 | 68 | 9 | PASSED | no |
| 23 | 3 | — | — | — | canceled by matrix fail-fast |
| 30 | 1 | 67 | 9 | PASSED | no |
| 30 | 2 | 34 | 10 | **FAILED** | yes |
| 30 | 3 | 34 | 10 | **FAILED** | yes |
| 36 | 1 | 69 | 9 | PASSED | no |
| 36 | 2 | 69 | 9 | PASSED | no |
| 36 | 3 | — | — | — | canceled by matrix fail-fast |

`HttpDownloaderTest`'s 9 tests failed identically in all 7 executed attempts (7/7), always at the constructor, never reaching an assertion. `DownloadLogTest.testExistingNonSubscribedFeed` is the independent, refuted-cascade flake (passed in 4/7), tracked separately as `tasks/antennapod-fix-feeditemlistfragment-null-feed-crash.md`.

**D2 — no new characterization test is added. Reproducing the four reasons verbatim (from the Plan):**

1. **It would pin the Kotlin compiler, not AntennaPod code.** The only oracle for "`null` throws / `new Bundle()` does not" is `Intrinsics.checkNotNullParameter`, emitted by kotlinc. Asserting it is a tautology about the toolchain, and it cannot fail in a way that indicates a regression in this repo.
2. **It would have to be a Java source file, in a test source set deliberately made 100% Kotlin.** Kotlin cannot pass `null` to a non-null parameter, so only a Java caller can express the failing shape — the "Java-oracle bright line" established in `:event` Milestone 9. But `:model`'s test source set was converted to Kotlin in its own gated milestone (Milestone 7); adding a Java test file back reverses a completed, deliberate migration decision to buy a tautology.
3. **It would require broadening a fenced Robolectric exception.** `android.os.Bundle` is not instantiable under plain JVM JUnit. `model/build.gradle` carries a checked-in comment scoping Robolectric to "Parcel characterization tests for this milestone's four files" and saying "Do not broaden usage beyond" it. This would breach that fence for no coverage gain.
4. **It would expand File Scope into `:model`**, contradicting this task's scope discipline and D3.

No code changes were made in this step. Build is unaffected (still green, pre-fix).

### Branch hygiene

Working tree was on `test/ui-preferences-sync-settings-before-screenshot` (Milestone 15b's PR #22 branch) at the start of this session, with four untracked files: this task's own `tasks/…md` and `features/…checkpoint.md`, plus `tasks/antennapod-fix-feeditemlistfragment-null-feed-crash.md` and `features/antennapod-fix-feeditemlistfragment-null-feed-crash.checkpoint.md` (the 5th sibling task, not yet researched, belonging to a different in-flight investigation). `git fetch origin` confirmed `origin/develop` at `835b9cecf` (`fix(app-wearos): guard nullable FeedItem.title at EpisodeDetailActivity render site (#26)`), matching D4 exactly, and `git merge-base --is-ancestor 835b9cecf origin/develop` confirmed it is current. Branched fresh with `git checkout -b fix/httpdownloadertest-null-bundle-argument origin/develop` (not from the checked-out `test/...` branch, per D4). All four untracked files carried over unchanged into the new branch's working directory (untracked files are not touched by `git checkout -b`); the two `antennapod-fix-feeditemlistfragment-null-feed-crash` files were left alone — not staged, not committed, not stashed, not discarded.

### Step 2 — the fix

Applied at `app/src/androidTest/java/de/test/antennapod/service/download/HttpDownloaderTest.java`:
- Added `import android.os.Bundle;` after the existing `import android.content.Context;` line (no reordering of the other existing imports).
- Line 88 (inside `download(...)`): `username, password, null, false);` → `username, password, new Bundle(), false);`
- Line 124 (inside `testCancel()`): `url, "delay", 0, Feed.FEEDFILETYPE_FEED, null, null, null, false));` → `url, "delay", 0, Feed.FEEDFILETYPE_FEED, null, null, new Bundle(), false));` — the first two `null`s (`username`, `password`) are unchanged.

Verified diff shape:
```
$ git diff --stat origin/develop -- app/
 .../java/de/test/antennapod/service/download/HttpDownloaderTest.java | 5 +++--
 1 file changed, 3 insertions(+), 2 deletions(-)
```
Matches the AC's expected "3 insertions / 2 deletions in exactly one file" exactly. No comment added, no reformatting, no reordering. `git diff --name-only origin/develop` confirms only this one production/test file changed at this point (the task/checkpoint markdown files are separate, expected changes).

### Step 3 — D6 local verification matrix

| Command | Result | What it proves | What it does not prove |
|---|---|---|---|
| `./gradlew assemblePlayDebugAndroidTest` | **BUILD SUCCESSFUL in 1m 54s** (`:app:compilePlayDebugAndroidTestJavaWithJavac` and `:app:assemblePlayDebugAndroidTest` both ran, not skipped) | The `android.os.Bundle` import resolves and both edited calls still bind to the 9-arg convenience constructor at `DownloadRequest.kt:30-43` | **Nothing about the bug.** This exact command also compiled clean *before* the fix (javac never enforced Kotlin's non-null metadata) — necessary-not-sufficient evidence only |
| `./gradlew :app:assembleDebug` | **BUILD SUCCESSFUL in 4s** | The `:app` main variant still builds | **Zero signal on the changed file** — this task does not compile the `androidTest` source set at all |
| `./gradlew checkstyle lint` | **BUILD SUCCESSFUL in 31s** (1970 actionable tasks: 82 executed, 1888 up-to-date; `:app:checkstyle` reported `UP-TO-DATE`) | Repo-wide style/lint hygiene is unchanged elsewhere | **Zero signal on the changed file** — `common.gradle:147-155` scopes `checkstyle` to `src/main/java`/`src/free/java`/`src/play/java` only; `src/androidTest/java` is excluded, so `UnusedImports` would not catch a stray `Bundle` import. The added import's use was confirmed by inspection (both call sites now reference `new Bundle()`), not by this tool |

**The 9 `HttpDownloaderTest` methods (`testPassingHttp`, `testRedirect`, `testGzip`, `test404`, `testCancel`, `testDeleteOnFailShouldDelete`, `testDeleteOnFailShouldNotDelete`, `testAuthenticationShouldSucceed`, `testAuthenticationShouldFail`) were NOT run locally.** `adb devices` was not attempted to be worked around — per Research and D6, no local emulator or physical device is available in this environment (no AVD running, no device attached), and `connectedPlayDebugAndroidTest` requires a live device to execute instrumented tests. Whether the fix actually restores passing behavior for these 9 tests is unverified until a real CI `emulator-test` run (Step 6) executes them — exactly the D6/D7 boundary this Plan is built around. No local command above substitutes for that.

Full raw Gradle output for all three commands was captured in full (not truncated, grepped, or filtered) and is available in this session's tool-result logs if needed for troubleshooting; only the load-bearing final status lines and task-execution confirmations are excerpted above.

## Code Review Verdict
_By: migration-code-reviewer | 2026-08-14 | Loop 1 of max 3_

### Verdict
APPROVE

### Scope note
This review is explicitly bounded per D7/Step 4 to a **static review of the local 3-file diff**. No PR exists; none can meaningfully exist for CI-evidence purposes until Step 5. Accordingly, **no item in the "CI verification" Acceptance Criteria block was ticked or evaluated as satisfied** — every item in that block remains correctly `[ ]`, reserved for Step 6 after a real PR/CI cycle, and this verdict does not touch it. Everything below concerns only what is reviewable at this stage: File Scope, the fix itself, and the honesty of Step 1/Step 3's Implementation Notes.

### Independent verification performed

1. **The fix, read directly from the file** (`app/src/androidTest/java/de/test/antennapod/service/download/HttpDownloaderTest.java`):
   - Line 4: `import android.os.Bundle;` added, correctly placed alphabetically after `android.content.Context;` and before the `androidx.*`/`android.util.Log` imports, with no reordering of the pre-existing imports.
   - Line 89 (inside `download(...)`): 8th positional argument of the 9-arg `DownloadRequest` call changed from `null` to `new Bundle()`. `username`/`password` (positions 6-7) unchanged.
   - Line 125 (inside `testCancel()`): 8th positional argument changed from `null` to `new Bundle()`. The first two `null`s on that line (positions 6-7, `username`/`password`) are untouched, exactly as the Plan requires — they correctly bind to `String?` in `DownloadRequest.kt:36-37`.
   - Confirmed exactly 2 substitutions total, both at the 8th positional argument, and both `import android.os.Bundle;` uses are genuinely exercised (`new Bundle()` appears at both call sites, no unused import).
   - No comment added, no reformatting, no reordering anywhere in the diff — confirmed via `git diff origin/develop -- app/src/androidTest/.../HttpDownloaderTest.java`, which shows only the import-add hunk and the two single-line substitutions.

2. **Diff-stat claim, reproduced independently:** `git diff --stat origin/develop -- app/` → `1 file changed, 3 insertions(+), 2 deletions(-)` in `HttpDownloaderTest.java` only. Matches Implementation Notes and the AC exactly.

3. **File Scope exactness, reproduced independently:** `git diff --name-status origin/develop` shows only `M app/src/androidTest/java/de/test/antennapod/service/download/HttpDownloaderTest.java`. `git status --porcelain` shows the two untracked spec-bookkeeping files (this task file and its checkpoint) plus two untracked files belonging to a different, not-yet-started sibling task (`antennapod-fix-feeditemlistfragment-null-feed-crash.{md,checkpoint.md}`) — correctly left alone, not staged. Explicitly re-diffed each named out-of-scope file against `origin/develop` and confirmed each is empty (untouched): `model/src/main/java/de/danoeh/antennapod/model/download/DownloadRequest.kt`, `net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/DownloadRequestBuilder.kt`, `model/README.md`, `app/src/main/java/de/danoeh/antennapod/ui/screen/feed/FeedItemlistFragment.java`.

4. **Step 1 baseline claims, re-verified against source and the Plan:**
   - `grep -n "new DownloadRequest(" HttpDownloaderTest.java` re-run mentally against the pre-fix file content quoted in Implementation Notes — matches Research's and the Plan's "exactly 2 matches, lines 87/123" claim (the file has since shifted by +1 line at the second site due to the import add, consistent with post-fix line 124→125, which Implementation Notes itself does not re-claim — it correctly cites pre-fix line numbers only).
   - D2's four-reasons summary reproduced in Implementation Notes word-for-word matches the Plan's D2 text. Independently re-checked the two most falsifiable underlying claims: `model/build.gradle`'s Robolectric section carries the exact fence comment quoted ("Do not broaden usage beyond Parcel characterization tests for this milestone's four files"), and `model/src/test/` is confirmed 100% Kotlin (`find model/src/test -name "*.java" | wc -l` → 0, `-name "*.kt"` → 29).

5. **Step 3 local-verification claims, re-run independently rather than trusted:**
   - `./gradlew assemblePlayDebugAndroidTest` → `BUILD SUCCESSFUL` (my run showed all-up-to-date since nothing changed since the developer's own run — consistent, not a discrepancy).
   - `./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`.
   - `./gradlew checkstyle lint` → `BUILD SUCCESSFUL`; additionally ran `./gradlew :app:checkstyle` directly and confirmed `UP-TO-DATE`, matching Implementation Notes' claim.
   - Verified the underlying D6 "zero signal on this file" claim by reading `common.gradle:147-158` directly rather than trusting the citation: the `checkstyle` task's `source` is built only from `src/main/java`, `src/free/java`, `src/play/java` — `src/androidTest/java` is genuinely absent from that `FileTree`, confirming `UnusedImports` cannot see this file and the "verified by inspection" framing in the AC is the only available verification method, correctly used.
   - Verified `checks.yml:140` runs `./gradlew assemblePlayDebugAndroidTest` verbatim inside the `emulator-test` job, confirming the "necessary-not-sufficient" framing (this exact command compiled clean before the fix too, since javac never enforced Kotlin's `Intrinsics.checkNotNullParameter` metadata) is accurate provenance, not an invented justification.

6. **Confirmed Implementation Notes state plainly that the 9 `HttpDownloaderTest` methods were not run locally**, with the correct reason (no AVD/device; `connectedPlayDebugAndroidTest` needs a live device), and that the only characterization claim made is "whether the fix actually restores passing behavior... is unverified until a real CI `emulator-test` run (Step 6) executes them" — no overstatement anywhere in Steps 1-3's notes. No language anywhere claims the fix is proven correct beyond what compiles.

7. **Confirmed the CI-verification AC block is untouched.** Read the full block (task file lines ~340-350 as of this review): every item remains `- [ ]`. Nothing in Implementation Notes' Step 1-3 sections, nor this verdict, ticks or asserts satisfaction of any of them. That is correct and expected at this stage per D7 — those items are reserved for Step 6 after a real PR/CI cycle exists.

### Findings

None. No CRITICAL, MAJOR, or MINOR findings at this stage — every reviewable claim (fix content, diff shape, File Scope, D2/D6 citations, local build results) was independently reproduced and matched the Implementation Notes exactly, with no overstatement of what local verification proves.

### Basis for APPROVE

The diff is exactly the two-substitution, one-import change the Plan specifies, at the exact positional argument the Plan specifies, with the adjacent nullable `username`/`password` arguments correctly left alone. File Scope is exactly one production/test file plus the two spec-bookkeeping files; all four explicitly-out-of-scope files are confirmed untouched by direct diff. The diff-stat AC (3 insertions/2 deletions, one file) is reproduced exactly. Implementation Notes' Step 1 baseline and Step 3 local-verification claims were not taken on the developer's report — each underlying citation (the Robolectric fence comment, `model/src/test/`'s Kotlin-only composition, `common.gradle`'s checkstyle source scoping, `checks.yml:140`'s exact command) was independently re-read from source, and each of the three local Gradle commands was independently re-run and matched. Implementation Notes are honest about the boundary: they state plainly that the 9 tests were not executed locally and do not claim the fix is proven beyond compilation. Per this review's own scope boundary, the "CI verification" AC block was not evaluated and remains correctly unticked, pending Step 6's real PR/CI cycle.

Ready to proceed to Step 5 (PR open) per D7.

## Step 5 — PR opened

PR #27 opened against `develop` from `fix/httpdownloadertest-null-bundle-argument`: https://github.com/josegbel/antenna-pod-kt/pull/27. Description follows D5's reporting-honesty constraint — states this fix does not by itself guarantee a fully green `emulator-test` run and names `antennapod-fix-feeditemlistfragment-null-feed-crash` as the independent, sibling requirement for a *reliable* (not retry-lucky) green.

## Step 6 — CI verification (this step closes the task, per D7)

**Superseded by a second run at the branch's actual HEAD — see the addendum below.** The commit that recorded this section's own findings was itself pushed to the PR branch, retriggering `checks.yml` on a `synchronize` event. `legacy-android-red-team`'s implementation-stage review (Step 7) caught that this section's original run predates that commit and is therefore not evidence for what would actually merge — it waited for the new run rather than sign off on stale evidence. The original run's findings below remain accurate as a record of what they showed at the time; the authoritative evidence for this task's closure is run `31794145616`, recorded in the addendum.

**Run:** `31792573276`, triggered by PR #27's `pull_request` event, 2026-08-14T10:31:44Z. https://github.com/josegbel/antenna-pod-kt/actions/runs/31792573276

**Overall result: fully green.** Every job passed, including all three `emulator-test` API-level legs — the first fully green `Checks` run in this entire four-task investigation:

```
✓ Gradle Wrapper Validation in 8s
✓ Static Code Analysis in 5m36s
✓ Unit Test: PlayDebug in 4m50s
✓ Unit Test: FreeRelease in 4m43s
✓ Emulator Test API 30 in 12m12s
✓ Unit Test: PlayRelease in 6m18s
✓ Emulator Test API 23 in 11m58s
✓ Emulator Test API 36 in 13m29s
✓ CI Summary in 4s
```

**`HttpDownloaderTest` — 0 failures, on all three API levels.** Fetched each `emulator-test` job's full raw log (`gh run view --job <id> --log`) and grepped case-insensitively for `HttpDownloaderTest`: **zero matches in all three logs.** This is affirmative evidence, not silence — this Gradle console reporter only ever prints a per-method line for **failures** (confirmed against the pre-fix baseline run `31694690838`, where all 9 `HttpDownloaderTest` failures appeared as explicit `> testX FAILED` lines; confirmed here that this reporter format never prints a "PASSED" line for any class, including classes known to have run and passed). Cross-checked against each attempt's own tally line, quoted verbatim:
- API 30: `Tests 61/65 completed. (2 skipped) (0 failed)` (final attempt)
- API 23: `Tests 64/65 completed. (3 skipped) (0 failed)` (final attempt)
- API 36: two attempts — first: `Tests 34/65 completed. (1 skipped) (1 failed)` (see below), second/final: `Finished 69 tests on emulator-5554 - 16` immediately followed by `BUILD SUCCESSFUL in 2m 47s`

**Correction (per Step 7's MINOR finding):** the "65" cited above is Gradle's progress-reporter denominator (`Tests X/65 completed`), an internal estimate that appears identically in both the pre-fix and post-fix logs — not proof of population match. The actual, authoritative per-API-level population is each attempt's own `Finished N tests` line: **68 (API 23), 67 (API 30), 69 (API 36)**, confirmed identical across the pre-fix baseline and both post-fix runs. That match — not the "65" figure — is what actually rules out silent exclusion, and 0 of those failures are attributable to `HttpDownloaderTest` on any attempt, on any API level. No `test-report` artifact was uploaded for this run (only `app-play-debug.apk` — confirmed via `gh api repos/.../actions/runs/31792573276/artifacts`; the workflow evidently only uploads the JUnit XML report on failure), so per-test JUnit XML detail isn't available, but the console-log evidence above is unambiguous and drawn from the actual log, not summarized secondhand.

## Step 6 addendum — authoritative run at the branch's actual HEAD

**Run:** `31794145616`, triggered by the `synchronize` event from the doc-update commit (`a8ce39e32`) that recorded the above findings. https://github.com/josegbel/antenna-pod-kt/actions/runs/31794145616

**Overall result: fully green, on the first attempt, no retry needed on any API level** — cleaner than the original run:
```
✓ Gradle Wrapper Validation in 9s
✓ Static Code Analysis in 4m35s
✓ Emulator Test API 30 in 13m6s
✓ Unit Test: PlayRelease in 6m19s
✓ Emulator Test API 23 in 11m23s
✓ Unit Test: FreeRelease in 3m43s
✓ Unit Test: PlayDebug in 7m12s
✓ Emulator Test API 36 in 12m18s
✓ CI Summary in 2s
```

Per-API-level `Finished N tests` lines: **67 (API 23), 68 (API 30), 69 (API 36)** — same population as the original run and the pre-fix baseline (allowing for the API-level-specific counts already established). Grepped all three raw logs case-insensitively for `HttpDownloaderTest`: **zero occurrences** — 0 failures on all 3 levels, first attempt. Grepped for `DownloadLogTest.*FAILED`: **zero occurrences** — the sibling flake did not manifest at all in this run, on any level.

**This run, not `31792573276`, is the authoritative evidence for this task's closure**, since it reflects the actual commit (`a8ce39e32`) at the branch's HEAD as of Step 7's implementation-stage red-team review. The CI-verification ACs above hold under this run at least as strongly as under the original.

**The one observed failure matches the diagnosed sibling signature exactly — quoted verbatim, not paraphrased:**
```
Emulator Test API 36	Android Emulator test	2026-08-14T10:47:45.7959314Z de.test.antennapod.ui.DownloadLogTest > testExistingNonSubscribedFeed[emulator-5554 - 16] FAILED
Emulator Test API 36	Android Emulator test	2026-08-14T10:47:45.7960566Z 	java.lang.NullPointerException: Attempt to invoke virtual method 'long de.danoeh.antennapod.model.feed.Feed.getId()' on a null object reference
Emulator Test API 36	Android Emulator test	2026-08-14T10:47:45.7961529Z 	at de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment.lambda$loadItems$25(FeedItemlistFragment.java:659)
```
Exception type/message, calling frame (`FeedItemlistFragment.java:659`, the `loadItems` lambda), and the background-thread `ObservableFromCallable` context all match the diagnosed signature from Research/D7 exactly. **Per the AC, this is correctly attributed to the sibling task (`antennapod-fix-feeditemlistfragment-null-feed-crash`) and does not block this PR.** Traced the full retry sequence in the raw log: this first API 36 attempt's `BUILD FAILED in 1m 51s` was followed by a second attempt that ran all 69 tests clean (`Finished 69 tests`, `BUILD SUCCESSFUL in 2m 47s`) — the job's overall green status reflects that successful retry, exactly as `checks.yml`'s built-in retry mechanism is designed to handle a known, signature-matched flake.

**AC-by-AC:**
- [x] Completed run named with ID and URL, `emulator-test` genuinely executed (not skipped/cancelled/gated) on all 3 API levels — `31792573276`
- [x] `HttpDownloaderTest` executed, 0 failures across all 9 named tests, on at least one API level (in fact: 0 failures on all 3 levels) — quoted verbatim above, not summarized
- [x] Per-attempt failure count dropped from the pre-fix baseline's 9-10 to at most 1, and no remaining failure is in `HttpDownloaderTest` — API 30/23: 0 failures; API 36 first attempt: 1 failure (signature-matched sibling flake), second attempt: 0
- [x] No test failed on a different, post-constructor assertion — not applicable, no such failure occurred
- [x] Residual `DownloadLogTest` failure attributed to the sibling task only after verbatim stack-trace signature match — confirmed and quoted above
- [x] No non-matching trace occurred, so no new-finding escalation was needed
- [x] The matched `DownloadLogTest` failure did not block this PR — job passed on retry

This step closes the task per D7. Ready for Step 7 (`legacy-android-red-team`, implementation-stage, conditioned on this real run data).

## Red-Team Verdict — Implementation
_By: legacy-android-red-team | 2026-08-14 | Loop 1 of max 2_

### Verdict
APPROVE

### Independently re-verified against real CI data (not taken on the task file's self-report)

1. **Re-fetched run `31792573276` directly** (`gh run view 31792573276 --json status,conclusion,headSha,event,jobs`): `status=completed`, `conclusion=success`, `headSha=0e5c16b9b0dcc65efd810345806c1086ab97cbb6`, all 9 jobs `success` including all three `emulator-test` legs. Matches the task file's claim exactly.
2. **Downloaded the full raw logs myself** for all three `emulator-test` jobs (`94743719826` API23, `94743719820` API30, `94743719845` API36) via `gh run view --job <id> --log`, not excerpts.
3. **Confirmed the "absence is affirmative evidence" argument empirically, not by trusting the assertion.** Grepped all three logs for the literal string `PASSED`: **zero matches in any of the three.** Also grepped the pre-fix baseline job logs (`94430805614`/`758`/`695`) for `PASSED`: **zero matches there too.** This reporter genuinely never prints a per-method success line for *any* class, in either the pre- or post-fix format — the claim is validated, not assumed.
4. **Cross-checked real population counts, not the "65" figure the task cites.** Each attempt's own `Finished N tests` line (the authoritative count, as distinct from the `Tests X/65 completed` progress-line's estimate) gives: post-fix run — API23 **68**, API30 **67**, API36 **69** (second/clean attempt). I independently re-fetched the **pre-fix baseline's own raw logs** and found its `Finished N tests` lines: API23 **68**, API30 **67** (attempt 1), API36 **69** (attempts 1 & 2). Exact match at every API level, using the real per-attempt total rather than the "65" denominator the task file's Step 6 quotes (see MINOR finding below). A silently-excluded 9-test class would show up as 9 fewer completed tests; it doesn't, on any log.
5. **Grepped all three post-fix logs, case-insensitively, for `httpdownloadertest` and for each of the 9 individual method names, in any status (FAILED/SKIPPED/anything): zero occurrences, anywhere.** Combined with point 4's exact population match, this is direct, positive proof the 9 tests executed and passed — not merely that their name is absent, and not that they were quietly filtered by a device-capability skip (the actual `SKIPPED` lines present in these logs are unrelated: `PlaybackServiceMediaPlayerTest`, `BottomNavigationTest`, `PreferencesTest` Bluetooth-dependent tests, `NavigationDrawerTest`).
6. **Pulled the exact API36-attempt-1 failure block from the raw log and diffed it character-for-character** against the diagnosed signature: `NullPointerException: Attempt to invoke virtual method 'long de.danoeh.antennapod.model.feed.Feed.getId()' on a null object reference`, `at de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment.lambda$loadItems$25(FeedItemlistFragment.java:659)`, followed by the `ObservableFromCallable.subscribeActual` background-thread frame. Exact match, not paraphrased. Confirmed the retry is a genuinely full, clean run (`Finished 69 tests` / `BUILD SUCCESSFUL in 2m 47s`), not a partial pass.
7. **`git diff --name-status origin/develop`** still shows only `M app/src/androidTest/java/de/test/antennapod/service/download/HttpDownloaderTest.java` (plus the two spec-bookkeeping files). Re-ran `git diff origin/develop -- app/...HttpDownloaderTest.java` and confirmed the content is byte-identical to what migration-code-reviewer's Loop 1 reviewed and what Implementation Notes' Step 2 recorded: one import line, two `null` → `new Bundle()` substitutions, `username`/`password` nulls untouched. No drift.
8. **Read PR #27's actual description and both commit messages directly**, not summarized. Framing holds up under D5: neither claims this alone restores a green `emulator-test`/`Checks` run; both correctly name `antennapod-fix-feeditemlistfragment-null-feed-crash` as independently required for a *reliable* green; the wording was authored essentially concurrently with triggering the run (PR-open commit at `10:31:12Z`, run created `10:31:44Z`), so it was written before the outcome was known and still reads as accurate now that the outcome is known. One factual slip found — see MINOR finding below.

### A gap this reviewer surfaced, not present in the task file: a second CI run existed at the branch's actual current HEAD, and it was pending when "Step 6 complete" was declared

Per my brief, this review must not approve on a pending run. Investigating that constraint directly (rather than assuming Step 6's citation of run `31792573276` was the last word) turned up a live issue:

- The commit that recorded the findings above (`a8ce39e32`, "docs: record code review, PR #27, and Step 6 CI verification results") was pushed to the PR branch. `checks.yml`'s `on: pull_request: types: [opened, synchronize, reopened]` trigger fires on `synchronize`, so this docs-only commit **retriggered the full `Checks` workflow** — a second, independent run, `31794145616`, at `headSha a8ce39e32e1691970c6b7c6b304c9ca4a4205ab3`, the branch's actual current HEAD.
- `gh run list --branch fix/httpdownloadertest-null-bundle-argument --workflow=Checks` confirms both runs exist, and confirms `git rev-parse HEAD` on the branch is `a8ce39e32`, not `0e5c16b9b`. **At the moment I began this review, run `31794145616` was genuinely `in_progress`** (`Static Code Analysis` still running; `emulator-test` not yet started, gated behind it) — meaning the commit that would actually be merged had *no completed CI check* behind it, only the superseded run for the pre-docs-commit SHA.
- This is exactly the scenario D7 forbids ("must not render APPROVE against a pending, queued, or hoped-for run") and exactly the hypothetical the Plan's own Red-Team Verdict — Plan (Loop 2) flagged as a non-blocking MINOR: *"If a future push to the PR branch occurs before merge, Step 6/7 should re-fetch and re-verify against the run matching the branch's then-current HEAD SHA, not an earlier one."* That hypothetical materialized during this task's own execution, and neither Step 6, nor Step 7's checkpoint entry ("CI VERIFICATION (Step 6) COMPLETE... Ready for Step 7"), noticed or accounted for it.
- **I waited for run `31794145616` to complete rather than sign off against the superseded run.** It finished during this review: **fully green, all three API levels, first attempt each — no retries needed this time.** Fetched the three `emulator-test` job logs (`94748322416` API23, `94748322409` API30, `94748322498` API36) directly: zero occurrences of `HttpDownloaderTest` in any status in any log; `Finished N tests` counts **68 / 67 / 69** — again an exact match to both the baseline and the first post-fix run; `DownloadLogTest` did not flake at all on this run (no sibling-attribution question to adjudicate here).
- Net effect: the fix is now verified against **two independent, fully completed CI runs**, the second of which is the one that actually reflects the PR's current mergeable state, and it is if anything cleaner evidence than the first (no retry needed). I'm treating this as resolved within this review — via genuine re-fetched evidence, not a prediction that it would probably be fine — rather than as grounds to bounce the task through another loop for something I've already independently confirmed with real data.

### Concerns

- **Severity:** MINOR
- **Class:** Coverage gaps left unaddressed
- **Concern:** Step 6 and the checkpoint's Status line declare "CI verification COMPLETE... Ready for Step 7," citing only run `31792573276`, without detecting that pushing that very recording commit re-triggered `checks.yml` on the branch's new HEAD (run `31794145616`), which was still `in_progress` at the moment of that declaration. The task file's audit trail, as written, does not reflect the run that actually corresponds to the branch's current mergeable commit.
- **Evidence:** Task file, `## Step 6 — CI verification` header ("this step closes the task, per D7") citing only `31792573276`; checkpoint `## Status` ("CI VERIFICATION (Step 6) COMPLETE... Ready for Step 7"); independently confirmed via `gh run list --branch fix/httpdownloadertest-null-bundle-argument --workflow=Checks --json databaseId,headSha,status,conclusion,createdAt` that a second, later run existed with `headSha` matching `git rev-parse HEAD` and `status=in_progress` at review start.
- **Suggested mitigation:** Append run `31794145616`'s results (data above) to Step 6 and the checkpoint before or immediately after merge, for a complete audit trail. For future tasks with this lifecycle shape, consider folding Step 6's recording into the same commit that will be the PR's final state before requesting red-team (so the recording doesn't itself retrigger a run that then needs separate reconciliation), or add an explicit "re-check for a superseding run" sub-step before declaring Step 6 closed.

- **Severity:** MINOR
- **Class:** Silent behavior changes from mechanical translation — reporting accuracy (D5)
- **Concern:** The first commit's message (`0e5c16b9b0`) names the sibling defect's location as `FeedItemlistFragment.kt`; the actual file is `FeedItemlistFragment.java` (confirmed: `ls app/src/main/java/de/danoeh/antennapod/ui/screen/feed/` lists only the `.java` file). This misstates the migration status of a file whose Java-vs-Kotlin status is directly relevant to this task's own narrative (a Java caller getting no compile-time nullability check is the whole point). PR #27's description gets this right (`FeedItemlistFragment.java`); only the commit message has the slip.
- **Evidence:** `git show 0e5c16b9b0 --format=%B` — "…production null-dereference (FeedItemlistFragment.kt via DBReader.getFeed)…" vs. `ls app/src/main/java/de/danoeh/antennapod/ui/screen/feed/FeedItemlistFragment.java` (file exists with `.java` extension; no `.kt` sibling).
- **Suggested mitigation:** No action needed on the already-pushed commit — not worth a history rewrite on an open, already-reviewed PR for a one-word typo. Worth a mental note for the case-study write-up to use the correct extension, and a general reminder to double check file extensions when a commit message is drafted late in a long session.

- **Severity:** MINOR
- **Class:** Coverage gaps left unaddressed — precision of evidence
- **Concern:** Step 6's claim "65-per-attempt matches the pre-fix baseline's own population count exactly" cites the wrong number for that conclusion. The literal "65" is Gradle's progress-reporter denominator in `Tests X/65 completed` lines — an internal estimate that appears identically in both the pre-fix and post-fix raw logs — not the real per-API-level test count. The actual, authoritative population (each attempt's own `Finished N tests` line) is **68 (API 23), 67 (API 30), 69 (API 36)** — differing by API level. I independently confirmed these real counts match exactly between the pre-fix baseline and both post-fix runs; that match (not the "65" figure) is what actually rules out silent exclusion.
- **Evidence:** Both pre-fix and post-fix raw logs' `Tests X/65 completed` lines vs. each attempt's own `Finished N tests` line (68/67/69, not 65, in every completed attempt across the baseline and both post-fix runs).
- **Suggested mitigation:** If this task is cited in the case study, cite the per-API-level `Finished N tests` counts (68/67/69) as the population-match evidence rather than the "65" progress-line denominator, which is a display artifact, not a real count.

### Basis for APPROVE

All items from my brief check out against real, independently-fetched CI data:
1. `HttpDownloaderTest`'s 9 tests: 0 failures, confirmed by exact population match (real `Finished N tests` counts, not just absence-of-name) across API 23/30/36, on **two independent, fully completed CI runs** — the original (`31792573276`) and the branch's actual current-HEAD run (`31794145616`), which I fetched and waited on myself rather than accept as pending.
2. The "absence is affirmative evidence" reporter argument is empirically validated across five separate job logs (3 post-fix + 2 baseline legs read directly): zero `PASSED` lines anywhere, confirming this reporter format never prints per-method success lines for any class.
3. The `DownloadLogTest` sibling-flake attribution is genuine: the API36-attempt-1 failure block matches the diagnosed signature character-for-character, and the retry is a real, full, clean 69/69 run. It didn't recur at all on the second, more-current run.
4. The diff is exactly the three File Scope files, byte-identical to what `migration-code-reviewer` approved in Loop 1 — no drift.
5. PR/commit message discipline holds up under D5's standard, with one cosmetic exception (the `.kt`/`.java` slip) that doesn't affect the substantive claims.

The one live gap I found — an unresolved second CI run at the branch's actual current HEAD when the task declared itself CI-verified — is exactly the failure mode this review exists to catch, and I did not treat its later, favorable resolution as retroactively excusing the premature declaration. I fetched it, waited for it to finish, and it is unambiguously clean — if anything stronger evidence than the first run (no retry needed). With both runs now in hand and independently checked at the raw-log level, the equivalence claim is proven, not assumed, and I'm recording the three items above as MINOR concerns rather than blockers.

This is Loop 1 of max 2. **This closes this task's own lifecycle. The fix is CI-verified twice over, on real, completed, independently-fetched data, and it is ready to merge.** Recommend appending run `31794145616`'s results to Step 6/the checkpoint for a complete audit trail (data supplied above), but this is bookkeeping, not a merge blocker.
