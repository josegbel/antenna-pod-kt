# Checkpoint — antennapod-net-sync-service-interface-kotlin

> **Repo:** `antennapod`
> **Task file:** `tasks/antennapod-net-sync-service-interface-kotlin.md`

## Status
DONE — PR opened, awaiting human review/merge

## Last updated
2026-07-28

## Lifecycle progress
- [x] Research (legacy-android-researcher)
- [x] Plan (legacy-android-planner)
- [x] Red-team plan (legacy-android-red-team) — loop 1 CHALLENGE → planner revision → loop 2 APPROVE, see task file
- [x] Implement (android-migration-developer) — all 13 Steps complete, see task file Implementation Notes
- [x] Code review (migration-code-reviewer) — loop 1 APPROVE, see task file Code Review Verdict
- [x] Red-team implementation (legacy-android-red-team) — loop 1 APPROVE, see task file Red-Team Verdict (Implementation)
- [x] PR opened

## Decisions for next session
- Module: `:net:sync:service-interface`. Track: `kotlin` only. Selected as Milestone 11, the fourth portfolio case-study module, chosen as the sibling to Milestone 10's `:net:download:service-interface` — same service-interface/service split pattern, different domain (sync vs download).
- This is unaffiliated OSS portfolio work — see `services/android-migration/projects/portfolio/README.md`.
- No auto-chaining exception is to be added to any repo file — permanently resolved during Milestone 10 (see memory `feedback_pipeline_automation_not_verifiable`). The user has instead given a live, in-conversation instruction to drive this milestone's pipeline through to an opened PR with minimal check-ins.
- Branch: **created** as `kotlin/net-sync-service-interface`, checked out off local `develop` after fast-forwarding local `develop` to `origin/develop` (Milestone 10's PR #16 had merged upstream in the meantime — `78734ff57`). The two untracked spec files carried over cleanly onto the new branch as expected.

### Plan decisions the red team and developer must not re-derive (2026-07-28)

The Plan section resolves all nine Unknowns as **D1–D17** (mapping: U1→D1, U2→D10, U3→D7, U4→D11+D12, U5→D16, U6→D4, U7→D13, U8→D14, U9→D7). Highlights that change what the next stages should look at:

- **D0 introduces two named nullability rules** so every call is checkable rather than interpretive. **Rule N:** a reference parameter is non-null *only* where the Java body dereferences it unconditionally as its first action (so `Intrinsics` throws the same NPE at the same public entry point) — true at exactly two sites, `readFromJsonObject(jsonObject: JSONObject)` and `Builder(item: FeedItem, …)`. **Rule A:** honour a pre-existing `androidx.annotation.NonNull` — true at exactly three sites, the DTO list params. **Everything else is nullable.**
- **D5 is a hazard research did not find, and it would have stalled the implementation.** Kotlin's `List<out E>` emits `List<? extends EpisodeAction>` for a *parameter* whenever the type argument is non-final — which `EpisodeAction` is, as a Java class, for five steps after `ISyncService` converts. The two Java implementors in `:net:sync:gpoddernet` then fail with "same erasure, yet neither overrides the other", pointing at a module that is out of File Scope. Fix: `@JvmSuppressWildcards` at **function level** on `uploadSubscriptionChanges` and `uploadEpisodeActions`.
- **D9 found a real behaviour fork at `EpisodeAction.java:195` that is fully test-detectable.** `formatter.format(this.timestamp)` with a null `Date`: Java binds `DateFormat.format(Date)` → `NullPointerException`; if Kotlin instead binds `Format.format(Any)` it throws **`IllegalArgumentException`** from the same public method. Guarded both behaviourally (`writeToJsonObjectWithNullTimestampThrowsNullPointerException` asserts the NPE specifically) and mechanically (`javap -c` must show `DateFormat.format:(Ljava/util/Date;)…`). Recorded fallback: `format(this.timestamp!!)`, which raises the `!!` count to 4 and must be recorded.
- **Exactly three `!!` are permitted in the whole module**, all in `EpisodeAction.kt`: `item.feed!!`, `item.media!!`, `action!!.name`. AC13's count is exact in both directions. The null-media fixture **must** carry a non-null `feed` with a non-null `downloadUrl`, or it proves row 1 twice and row 2 never — the Milestone 10 red-team lesson applied pre-emptively, with a mandated falsification check in AC4.
- **D10: `EpisodeAction.equals` is pinned, and the fix is not in this PR at all** — not even as a separate labelled commit, because that commit would have to edit the very test that pins it and would destroy the "same suite, unmodified, both versions" claim. Three tests make a quiet fix impossible. Honesty mitigation is AGENTS.md-compliant (test method names + a test-file comment per the `DownloadRequestTest.kt:13-15` precedent + README + future-work item 9 + OQ3), **not** a production-code comment.
- **D12: nine Java test files for nine production files, and four are compile-time guards** — `UploadChangesResponseTest` reads the inherited `timestamp` unqualified, `ISyncServiceTest` declares `throws` on six methods and catches through the interface type, `SyncServiceExceptionTest` calls both `super(...)` forms, `SynchronizationQueueTest` passes nulls to all nine stub methods. **Only the two `EpisodeAction` files use Robolectric** (AC5 pins `grep -c RobolectricTestRunner` == 2).
- **D6 adds a required `open`:** `SyncServiceException` has two Java subclasses, so Kotlin's default-final would break `:net:sync:gpoddernet`. This is preservation, not widening — it is the *only* `open` in the module (AC13 greps for exactly one hit). Five other classes become final, verified zero-subclass by grep, disclosed as a narrowing in AC15.
- **D3 found a third `UploadChangesResponse` subclass** research missed: `NextcloudSyncService.java:160` (`private static class NextcloudGpodderEpisodeActionPostResponse`). Adds no new constraint but makes it three Java subclasses, not two.
- **D7(a) deliberately diverges from Milestone 10's holder shape.** This module's accessors are bean-named, so `companion object { @JvmStatic var instance: SynchronizationQueue? = null }` emits the right statics; M10's explicit `@JvmStatic fun` form is the recorded fallback if `javap` disagrees. Do not read the divergence as an inconsistency.
- **D7(c) makes Unknown 9 a non-event.** All abstract reference params are nullable, so no other module's suite can regress; the guard is in-module (`stubAcceptsNullArgumentsWithoutThrowing`), not an empirical pre-run of `:net:download:service:test`.
- **Step 2 is a spike that commits no code** — throwaway-convert `SynchronizationQueue.kt` + Stub, run `:app:assembleDebug` and SpotBugs across the five relevant consumers to learn whether the nullable `getInstance()` produces new `NP_*` findings, record verbatim, then `git checkout --`. **If a new finding appears: stop and escalate.** Do not switch to a non-null holder to dodge a lint result and do not edit `config/spotbugs/exclude.xml` (out of File Scope).
- **Test tasks are unflavoured, verified three ways** (module `build.gradle` has no `apply from: playFlavor.gradle`; `common.gradle` declares no flavours; the repo-wide grep listing the 22 modules that do apply it excludes this one — note its sibling `:net:sync:service` *does*). Commands: `:net:sync:service-interface:testDebugUnitTest --rerun` and `:testReleaseUnitTest --rerun`. Copy-pasting Milestone 10's `testFreeDebugUnitTest`/`testPlayDebugUnitTest` is itself a review finding. Step 1 records `tasks --all` output. No free-flavour SpotBugs gate gap here, unlike M10.
- **Shape: one PR, 13 steps, 12 commits** (4 characterization + 7 conversion + 1 docs; Step 2 commits nothing). Conversion order is smallest-and-loudest first, riskiest last: `SyncServiceException` → `ISyncService` → `UploadChangesResponse` → the two DTOs → `SynchronizationProvider` → `SynchronizationQueue`+Stub → `EpisodeAction`.
- **Hard stops:** any edit outside `net/sync/service-interface/` during a conversion step, and any test-file edit during Steps 6–12. Both mean re-plan, not patch.
- **Pre-existing gate noise to expect, not chase** (recorded by Milestone 10 and verified against unmodified `develop`): `:app-wearos:compileFreeDebugKotlin`/`compilePlayDebugKotlin` at `EpisodeDetailActivity.kt:115:28`, and `:app:spotbugsPlayDebug`'s seven violations. AC14 requires re-running the identical failing task on `develop` and comparing byte-for-byte before dismissing anything as pre-existing.
- **Three Open Questions, none blocking:** OQ1 upstreaming intent (standing, for José — D1 and D10 both assume *fork*), OQ2 test-suite Kotlin conversion as a possible Milestone 12 (harder here: four files are Java precisely so a wrong JVM shape fails to compile), OQ3 whether the `equals` fix becomes its own task and goes upstream (downstream of OQ1).

### Red-team plan loop 1→2 (2026-07-28) — resolved, do not re-derive
Loop 1 CHALLENGE found: (1) CRITICAL — the PLAY-gate test pair discriminated `started`'s operator from `position`'s but never independently exercised `total`'s own boundary at `EpisodeAction.java:87`, since `position` and `total` share the same `> 0` operator; (2) MAJOR — `Builder(FeedItem, Action)`'s `action` parameter had no discriminating test, unlike the sibling `Builder(String, String, Action)` overload's `action` param; (3) MINOR — AC3's stated test count (24) didn't match Step 3's actual enumeration (27). The planner fixed all three **in place** (D0, D9, D11, Step 3, Step 4, AC3, AC4 — see "Plan — Revision 1" in the task file): added `readFromJsonObjectPlayFieldsAllOrNothingWhenTotalIsZero` and `builderFeedItemConstructorAcceptsNullAction`, corrected the count to 28, and — the part that matters — fixed the root cause in each case (D0's dangerous parameter-eliding ellipsis; D11's "two of three clauses share an operator" framing), not just the two named symptoms. Loop 2 independently re-derived both fixes against the actual source (not the planner's account) and found them correct. **Plan is APPROVED. Ready for `android-migration-developer`.**

### Research findings the planner must not re-derive (2026-07-28)

- **Module shape:** 9 production files / 514 LOC, one package. 57% of LOC is `EpisodeAction.java` (293 LOC), which holds ~90% of the risk. The other 8 files (221 LOC) are largely mechanical.
- **ZERO tests.** `net/sync/service-interface/src/` contains only `main` — no `test`, no `androidTest`, no `testFixtures`, and `build.gradle` declares **no `testImplementation` at all**. Creating the test source set + adding deps is unavoidably Step 1. Worst starting safety net of any milestone in this case study so far.
- **Robolectric is unavoidable** for `EpisodeAction` JSON tests: `org.json.JSONObject` is an Android-framework stub under plain JUnit and `common.gradle` does **not** set `returnDefaultValues`. Follow `model/build.gradle:24-28`'s disclosed-scoped-exception comment precedent. The `TextUtils`→stdlib swap does **not** avoid this.
- **Build file differs from Milestone 10's module — do not copy commands.** No `playFlavor.gradle`, so test tasks are the **unflavoured** `testDebugUnitTest` / `testReleaseUnitTest`, aggregate `:net:sync:service-interface:test` (same as `:model`/`:event`, NOT M10's `testFreeDebugUnitTest`/`testPlayDebugUnitTest`). Also **no `java-test-fixtures`** and no `lint {}` block.
- **Three dead deps** at `build.gradle:12,15,16` (`:storage:preferences`, rxandroid, rxjava) — zero usage in `src/main/`. Not hidden coupling. Removal optional.
- **Four load-bearing JVM-shape decisions J2K gets wrong:**
  1. `UploadChangesResponse.timestamp` is a `public final long` **field** read unqualified from a Java subclass (`GpodnetUploadChangesResponse.java:48`) and qualified from `SyncService.java:208,255` → needs **`@JvmField`**, not `val`.
  2. `EpisodeAction`'s 4 static `Action` aliases → **`@JvmField`** (type is an enum, so `const val` is NOT available — differs from M10's `WORK_*`).
  3. `ISyncService`'s 6 methods → **`@Throws(SyncServiceException::class)`**, else Java `catch (SyncServiceException)` at `SyncService.java:209` becomes an unreachable-catch compile error. First checked-exception interface in the case study.
  4. `SyncServiceException.serialVersionUID` → `private const val` in a `companion object`.
- **The silent, highest-consequence hazard:** `SynchronizationProvider.fromIdentifier` must be `(provider: String?): SynchronizationProvider?`. Non-null param crashes `SyncService.doWork()` for **every user who has never configured sync** (key is `prefs.getString(..., null)`). Compiler cannot catch it; caller is Java. Land the `fromIdentifier(null) == null` test first.
- **Pre-existing defect to pin, not fix mid-conversion:** `EpisodeAction.java:161` reads `action != that.action` (inverted). Field-identical instances compare unequal; `hashCode` includes `action`, so the contract is violated. Nothing in the repo calls it. Decide pin-vs-fix explicitly (see Unknown 2).
- **Cheap early de-risk:** throwaway-convert only `SynchronizationQueue.kt` and run `./gradlew lint` to learn whether emitting `@Nullable` on `getInstance()` triggers new SpotBugs `NP_*` findings in the 5 dependent modules, before sinking effort into tests. SpotBugs is a hard gate (`common.gradle:96-129` throws despite `ignoreFailures = true`), and `config/spotbugs/exclude.xml` has no `net.sync` entry.
- **Interop width:** 7 dependent Gradle modules (2 more than M10), 28 referencing files, **all Java, zero Kotlin callers**. 22 `getInstance()` sites + 5 `setInstance()` sites (4 of them tests in *other* modules that install `SynchronizationQueueStub`).

## Pre-PR gate verification (2026-07-28)
`./gradlew checkstyle lint` run in full (untruncated) against the branch: **3 failures**, byte-for-byte identical to Milestone 10's already-disclosed pre-existing baseline — `:app-wearos:compilePlayDebugKotlin`/`compileFreeDebugKotlin` (`EpisodeDetailActivity.kt:115:28`, `String?` vs `String` mismatch) and `:app:spotbugsPlayDebug` (7 violations: 2× `MainActivity` Snackbar NP_NULL_PARAM_DEREF, 1× `FeedInfoFragment` NP_NULL_ON_SOME_PATH, 1× `OnlineFeedViewActivity` Snackbar, 1× `PreferenceActivity` Snackbar, 2× `QueueFragment` NP_NULL_ON_SOME_PATH — same lines as Milestone 10's disclosure). None of these touch `net/sync/service-interface/` or any file in this milestone's diff. Not fixed, per this repo's precedent of disclosing rather than fixing pre-existing, unrelated static-analysis findings surfaced incidentally by a migration (`:model` D9/D20, `:event` AC10, `:net:download:service-interface` Milestone 10).

## PR
https://github.com/josegbel/antenna-pod-kt/pull/17 (branch `kotlin/net-sync-service-interface` → `develop`).

## Resume command
Milestone 11 is DONE. PR #17 opened, awaiting José's review/merge. Nothing further to do for this milestone.

## Prior resume note (superseded)
Research, Plan, and plan red-team (both loops) are complete in `tasks/antennapod-net-sync-service-interface-kotlin.md` (Plan = D0–D17, 13 Steps, File Scope of 5 modified + 9 renamed + 9 created files, AC1–AC18, plus OQ1–OQ3; Plan Revision 1 closed loop 1's findings; loop 2 = APPROVE). Implementation is **done** — see below. Code review is **done** (loop 1 of max 3, APPROVE, zero findings — see task file Code Review Verdict). Implementation red-team is **done** (loop 1 of max 2, APPROVE, two non-blocking MINOR findings — see task file Red-Team Verdict (Implementation)).

### Implementation summary (2026-07-28) — what the code reviewer / implementation red-team should look at

All 13 Steps complete, 12 commits on `kotlin/net-sync-service-interface`. Full detail (per-file test
results, machine-checked `javap`/grep claims, falsification-check outcomes, the Step 2 spike
result, the AC16 cross-module matrix, and three recorded implementation-level deviations) is in
the task file's **Implementation Notes** section — read that first rather than re-deriving from
the diff.

Headline points worth a second pair of eyes, since they're the highest-consequence claims:
- **`fromIdentifier(null) == null` was proven, not assumed** — `SynchronizationProviderTest` was
  green and unmodified for nine steps before `SynchronizationProvider.kt` landed at Step 10, and
  stayed green immediately after.
- **All three `!!` in `EpisodeAction.kt` were falsification-tested live**, not just reasoned about:
  each was individually softened to `?.`, confirmed the *matching* test failed and no other test
  did, then reverted. Same live-falsification treatment for all three PLAY-gate boundary tests
  against the exact mistranscriptions the red-team's loop-1 CRITICAL finding was about.
- **The `EpisodeAction.equals` defect is pinned exactly as D10 specified** — verify the diff did
  not "fix" `action != that.action` anywhere; it should still read `!=`, not `==`.
- **One deviation worth checking:** an unauthorized rename (`equals(other: Any?)`) was caught and
  reverted to Java's exact `o` *before* being committed — confirm the committed diff shows `o`,
  not `other`, since this is exactly the kind of thing a fresh read should re-verify rather than
  trust the developer's own account of having caught it.
- **Pre-existing test/gate noise, not this milestone's regressions** — `:net:download:service:test`
  (6 `LocalFeedUpdaterTest` failures), `:app:test` (23 failures, a local Mockito/JDK toolchain
  issue), `:app-wearos` compile failure, and `:app:spotbugsPlayDebug`'s 7 violations were all
  verified byte-for-byte identical against unmodified `develop` — a reviewer re-running these
  should see the same counts, not zero failures.

Items the plan review already checked in depth, so the code reviewer / implementation red-team pass doesn't need to re-litigate them unless the diff itself looks wrong:
1. **D9's `!!` inventory and its "Pinned by" column** — verified rows 1/2 (`item.feed!!`/`item.media!!`, same expression sequence at `EpisodeAction.java:242`) have fixtures that genuinely discriminate `!!` from `?.` given Java's left-to-right argument evaluation, and row 3 is not shadowed by rows 1/2. Still worth a live falsification run (soften each `!!`, confirm the right test fails, revert) once code exists — AC4 mandates this.
2. **D9's `format(Date)` vs `format(Any)` claim** — plausible and well-hedged (behavioral NPE-specific test + `javap -c` mechanical check + recorded `!!` fallback); worth confirming which overload Kotlin actually binds once the file is converted (Step 12), not before.
3. **D5's wildcard analysis** — confirmed against Kotlin's own documentation: wildcards are elided only for final type arguments, and `EpisodeAction` is non-final Java until Step 12, so `@JvmSuppressWildcards` at Step 7 is real, not precautionary.
4. **D6's `serialVersionUID` placement claim** (companion `private const val` → static final on the *outer* class). AC12 escalates if `javap` disagrees; check the escalation path is adequate rather than a dead end.
5. **AC3's PLAY-gate pair** — do the two tests together really discriminate `started >= 0 && position > 0 && total > 0` from every plausible mistranscription of that asymmetric predicate?
6. **D7(d)'s disclosed unverifiable item** — the pristine `getInstance()` null default is not observable in a shared test JVM. Confirm the stand-in assertion plus `@Before`/`@After` restore is the honest maximum, per the [[unverifiable-equivalence-policy]].
