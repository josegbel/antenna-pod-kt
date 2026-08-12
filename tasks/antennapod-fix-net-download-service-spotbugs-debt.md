# antennapod-fix-net-download-service-spotbugs-debt

> **Description:** Fix 9 pre-existing SpotBugs violations in `:net:download:service`, discovered during code review of `antennapod-fix-spotbugs-static-analysis-debt` (the `:app`/`:event`/`:model` nullability debt task). These findings pre-date this task and are a second, independent debt pile blocking `checks.yml`'s `static-analysis` job — clearing the original 7 findings alone will **not** restore CI signal while these 9 remain.
> **Repo:** `services/android-migration/projects/portfolio/antennapod`
> **Created:** 2026-08-12

> **Pre-research context (carried over from the parent review conversation — do not re-derive):**
> - **Why this exists.** `migration-code-reviewer`, reviewing the implementation of `tasks/antennapod-fix-spotbugs-static-analysis-debt.md` (branch `fix/spotbugs-nullability-findings`), independently reproduced the disclosed `./gradlew checkstyle lint` failure with `--continue` (rather than stopping at the first failure, as the developer's own run apparently did) and found a **third, undisclosed** failure: `:net:download:service:spotbugsPlayDebug` fails with 9 violations, using the identical `common.gradle` report-parser fail mechanism as the original 7 findings.
> - **Provenance.** Traced to PR #21's Kotlin migration of `:net:download:service-interface` (commit `d5b7f94aa`, merged into `develop` 2026-08-06). This code entered the SpotBugs task's branch only during that task's own D7 fast-forward from `origin/develop` on 2026-08-12 — **after** that task's Research baseline (`app/build/reports/spotbugs/playDebug.xml`, captured 2026-08-06/07, exactly 7 `BugInstance` entries) was already locked in. Not a regression from this portfolio's SpotBugs-debt work; a separate pre-existing pile that simply wasn't visible yet when that task's scope was drawn.
> - **Sequencing impact.** `tasks/antennapod-fix-spotbugs-static-analysis-debt.md`'s own stated premise — "once this merges into `develop`, Milestone 15b's branch... gets a real CI signal" — does not hold on its own. Both debt piles gate the same `static-analysis` job via `checks.yml`'s `needs:` chain; both must clear before `unit-test`/`emulator-test` can run for any PR, including Milestone 15b's PR #22.
> - **José's decision (2026-08-12):** scope this as its own follow-up task rather than folding it into the in-flight SpotBugs task's File Scope, and rather than accepting a partial fix. Keep the two debt piles independently researched, planned, and reviewed — they are unrelated in cause (this one is `:net:download:service`, likely Milestone 10's own Kotlin-migration interop fallout, not the `:event`/`:model` nullability-widening pattern the sibling task documents) even though they share a symptom (blocking the same CI gate).
> - **Likely relevant precedent:** the sibling task `antennapod-fix-spotbugs-static-analysis-debt.md` (Research + Plan + both Red-Team Verdicts already complete there) found that this portfolio's own prior Kotlin migrations are the recurring root cause of "new" SpotBugs findings on otherwise-unmodified Java call sites — J2K's nullability translation (or, potentially here, a different interop shape from the Milestone 10 service-interface split) making something newly visible to static analysis that wasn't flagged before. Worth checking whether the same pattern applies here before assuming it's unrelated in mechanism, even though it's correctly scoped as a separate task.
> - **Sequencing — this task's PR must also merge before Milestone 15b's can get a real CI signal**, alongside (not instead of) `antennapod-fix-spotbugs-static-analysis-debt`. Order between the two sibling tasks is not fixed by anything discovered so far; either can go first.
> - This is unaffiliated OSS portfolio case-study work — see `services/android-migration/projects/portfolio/README.md`. Standalone repo hygiene, not part of the Sync Settings vertical-slice sequence (Milestones 15-20).

## Research
_Last updated by: legacy-android-researcher | 2026-08-12_

### Summary

The 9 findings are **one root cause with one shape**, and it is a different shape from the sibling task's. All 9 are `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` — priority 2 (Medium), rank 16, category **STYLE** — not the `NP_NULL_PARAM_DEREF` / `NP_NULL_ON_SOME_PATH` (CORRECTNESS) patterns the sibling task fixed. They sit in two still-Java classes in `:net:download:service` (`DownloadServiceInterfaceImpl`, 7; `FeedUpdateManagerImpl`, 2), and every one of them says the same thing: *this method's body dereferences a parameter on every path, but the parameter it inherits from its Kotlin superclass is annotated `@Nullable`.* Verified at bytecode level — `javap -v` on the compiled `DownloadServiceInterface` shows `org.jetbrains.annotations.Nullable` on parameter 0 of `downloadNow`, `download`, `cancel`, `cancelAll`, `getNumberOfActiveDownloads`. The pre-migration Java (`git show d5b7f94aa^`) declared those same parameters **completely unannotated** (`public abstract void downloadNow(Context context, FeedItem item, boolean ignoreConstraints)`). So the mechanism is confirmed to be **the same J2K nullability-widening the sibling task documented** — J2K rendering an unannotated Java declaration as a Kotlin nullable — but landing on **abstract-method parameters** rather than on `@JvmField` properties, which is why it produces a different bug pattern in a different module. The pre-research context's guess that this might be "a different interop shape from the Milestone 10 service-interface split" is half right: the split is what puts a Kotlin interface and a Java implementation in different modules, but the widening itself is the same recurring J2K hazard.

Two things materially change the shape of this task versus the sibling's, and both should be read before planning. **First, the commit message's "13 latent NPEs" has nothing to do with these 9 findings.** The 13 is an exact count of `!!` operators in `:net:download:service-interface`'s own Kotlin sources (verified: 3 + 7 + 3 across `DownloadRequestBuilder.kt`, `DownloadRequestCreator.kt`, `DownloadServiceInterface.kt`); the 9 are SpotBugs findings in the sibling `:net:download:service` Java module about parameter annotations. Zero overlap, no missing four, nothing suppressed — the discrepancy is a false correlation and should not drive scope. **Second, and much more important: clearing these 9 will still not make CI green.** On current `develop` (`5ae7d560f`, which already contains the sibling task's merged PR #23), a repo-wide `spotbugsPlayDebug spotbugsDebug --continue` shows `:app` now passing and `:net:download:service` as the only remaining SpotBugs failure — but `:app-wearos:compilePlayDebugKotlin` **fails outright** with `EpisodeDetailActivity.kt:115:28 Argument type mismatch: actual type is 'String?', but 'String' was expected`. That is a hard Kotlin compile error, it is caused by this portfolio's own `:model` migration widening `FeedItem.title` to `String?`, `:app-wearos:lint` depends on it, and CI hits it **before** SpotBugs ever runs (the 2026-08-12 `develop` run failed at `:app-wearos:compileFreeDebugKotlin` at 11:42, never reaching a SpotBugs task). Both task files' stated sequencing premise — "both piles must clear before Milestone 15b gets a real CI signal" — is therefore incomplete. See Unknowns 1.

### Findings

#### Existing surface

`:net:download:service` is the implementation half of a service-interface/service split (`net/download/README.md:3-4`). It is **100% Java** — 23 production files, none migrated. Its three entry-point classes each subclass an abstract class that lives in the Kotlin-migrated `:net:download:service-interface` and are registered at startup by `:app`:

- `DownloadServiceInterfaceImpl` — `net/download/service/.../feed/DownloadServiceInterfaceImpl.java:26-120`. Extends `DownloadServiceInterface`. Wraps WorkManager: enqueues `EpisodeDownloadWorker` (`:28-60`), cancels by tag (`:72-95`), counts active downloads (`:102-119`). **7 of the 9 findings.**
- `FeedUpdateManagerImpl` — `net/download/service/.../feed/FeedUpdateManagerImpl.java:28-139`. Extends `FeedUpdateManager`. Periodic + one-shot feed refresh via `FeedUpdateWorker`, plus a mobile-data confirmation dialog. **2 of the 9 findings.**
- `AutoDownloadManagerImpl` — `net/download/service/.../episode/autodownload/AutoDownloadManagerImpl.java:11-54`. Extends `AutoDownloadManager`. **Zero findings** despite its superclass carrying the identical widening — see "Why exactly these nine" below; this is the control case that proves the mechanism.

The three Kotlin superclasses that actually carry the `@Nullable` annotations:

- `DownloadServiceInterface.kt:18-43` — `downloadNow(context: Context?, item: FeedItem?, ignoreConstraints: Boolean)`, `download(context: Context?, item: FeedItem?)`, `cancel(context: Context?, media: FeedMedia?)`, `cancelAll(context: Context?)`, `getNumberOfActiveDownloads(context: Context?)`.
- `FeedUpdateManager.kt:8-18` — `restartUpdateAlarm(context: Context?, ...)`, three `runOnce(context: Context?, ...)` overloads, and **two `runOnceOrAsk(context: Context, ...)` overloads that are non-null**.
- `AutoDownloadManager.kt:20-30` — `autodownloadUndownloadedItems(context: Context?)`, `performAutoCleanup(context: Context?)`.

#### The nine findings, exact and authoritative

From `net/download/service/build/reports/spotbugs/playDebug.xml` on a clean detached checkout of `develop` HEAD `5ae7d560f`, parsed from XML (not from console output). All nine: `type="NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE"`, `priority="2"`, `rank="16"`, `category="STYLE"`. SpotBugs reports a **method line range**, not a single dereference line — unlike the sibling task's findings, there is no per-finding "Dereferenced at" line, so the byproduct notes' line ranges were method extents, not fix sites.

| # | File | Method (JVM signature) | Line range | Flagged parameter |
|---|---|---|---|---|
| 1 | `DownloadServiceInterfaceImpl.java` | `downloadNow(Landroid/content/Context;Lde/danoeh/antennapod/model/feed/FeedItem;Z)V` | 28–37 | `context` |
| 2 | `DownloadServiceInterfaceImpl.java` | `downloadNow(...)` — same method | 28–37 | `item` |
| 3 | `DownloadServiceInterfaceImpl.java` | `download(Landroid/content/Context;Lde/danoeh/antennapod/model/feed/FeedItem;)V` | 40–47 | `item` |
| 4 | `DownloadServiceInterfaceImpl.java` | `cancel(Landroid/content/Context;Lde/danoeh/antennapod/model/feed/FeedMedia;)V` | 75–95 | `context` |
| 5 | `DownloadServiceInterfaceImpl.java` | `cancel(...)` — same method | 75–95 | `media` |
| 6 | `DownloadServiceInterfaceImpl.java` | `cancelAll(Landroid/content/Context;)V` | 99–100 | `context` |
| 7 | `DownloadServiceInterfaceImpl.java` | `getNumberOfActiveDownloads(Landroid/content/Context;)I` | 105–117 | `context` |
| 8 | `FeedUpdateManagerImpl.java` | `restartUpdateAlarm(Landroid/content/Context;Z)V` | 46–59 | `context` |
| 9 | `FeedUpdateManagerImpl.java` | `runOnce(Landroid/content/Context;Lde/danoeh/antennapod/model/feed/Feed;Z)V` | 70–90 | `context` |

Six distinct methods, nine parameter-level findings. The byproduct notes in the sibling task's Implementation Notes named the right two files and the right six line ranges, but attributed them to no parameter and no method — this table supersedes them.

#### Root cause

One cause, mechanically identical across all nine.

**Pre-migration Java (`git show d5b7f94aa^:.../DownloadServiceInterface.java`):**
```java
public abstract void downloadNow(Context context, FeedItem item, boolean ignoreConstraints);
public abstract void download(Context context, FeedItem item);
public abstract void cancel(Context context, FeedMedia media);
public abstract void cancelAll(Context context);
public abstract int getNumberOfActiveDownloads(Context context);
```
Every parameter unannotated. Java's unannotated reference type carries **no nullness fact at all**, so SpotBugs had nothing to check the Java overrides against and emitted nothing.

**Post-migration Kotlin (`DownloadServiceInterface.kt:18-43`)** renders all of them `Context?` / `FeedItem?` / `FeedMedia?`. Confirmed in bytecode:
```
public abstract void downloadNow(android.content.Context, ...FeedItem, boolean);
  RuntimeInvisibleParameterAnnotations:
    parameter 0: org.jetbrains.annotations.Nullable
```
SpotBugs 4.8.6 reads that annotation, propagates it to the inherited signature of the Java override in `:net:download:service`, observes the override's body dereferencing the parameter unconditionally, and reports the inconsistency.

**The decisive control is inside `FeedUpdateManager` itself.** Pre-migration `FeedUpdateManager.java` was the one file whose author *had* annotated something:
```java
public abstract void restartUpdateAlarm(Context context, boolean replace);   // unannotated
public abstract void runOnce(Context context, Feed feed, boolean nextPage);  // unannotated
public abstract void runOnceOrAsk(@NonNull Context context);                 // @NonNull
public abstract void runOnceOrAsk(@NonNull Context context, @Nullable Feed feed);
```
J2K preserved `@NonNull Context` as `context: Context` (`FeedUpdateManager.kt:16-18`) and widened the two unannotated ones to `Context?`. The result: `runOnceOrAsk` dereferences `context` at `FeedUpdateManagerImpl.java:100` and `:108` (`context.getString(...)`) and is **not flagged**, while `restartUpdateAlarm` and `runOnce` are. Same file, same class, same kind of dereference — the only variable is whether the pre-migration Java carried an annotation. This is the same finding the sibling task made about `MessageEvent.message` (annotated siblings preserved, unannotated field widened), reproduced independently in a second module.

**A constraint the planner must know: Kotlin cannot express what the Java said.** Java's unannotated `Context context` is a *platform type* — "nullness unspecified". Kotlin has no syntax for declaring a platform type; every Kotlin parameter is either `Context` or `Context?`. So J2K had no faithful option here. This is not a J2K bug that "should have" been avoided; it is a structural consequence of converting an unannotated Java API to Kotlin, and it will recur on every future `kotlin`-track module that exposes unannotated Java signatures to still-Java callers. The fix decision is therefore necessarily a *choice of contract*, not a restoration of one.

#### Why exactly these nine, and not the others

The discriminator is: **is the parameter dereferenced (or handed to a `@NonNull`-annotated API) on every path through the method body?** This is worth recording because it tells the planner which non-findings would become findings if the code were touched.

- `download(Context, FeedItem)` — `item` flagged, `context` **not**. Line `:40-42` early-returns when `item.isDownloaded()`, so there is a path on which `context` is never used. `item` is dereferenced at `:40` before any branch.
- `runOnce(Context, Feed, boolean)` — `context` flagged, `feed` **not**. `feed` is explicitly null-checked at `:71`, `:76`, and `:83`.
- `runOnce(Context)` (`:61-63`) and `runOnce(Context, Feed)` (`:65-67`) — **not flagged**. Pure delegation; they pass `context` on without touching it.
- `runOnceOrAsk` ×2 (`:92-115`) — **not flagged**, because their Kotlin parameter is non-null (see above).
- `AutoDownloadManagerImpl` (both methods) — **not flagged** despite `AutoDownloadManager.kt` widening both to `Context?`. `autodownloadUndownloadedItems` (`:39-41`) passes `context` into `AutomaticDownloadAlgorithm.autoDownloadUndownloadedItems(Context)`, an unannotated Java method; `performAutoCleanup` (`:52-54`) passes it to `EpisodeCleanupAlgorithmFactory.build().performCleanup(context)`, likewise unannotated. No dereference, no `@NonNull` sink, no finding.
- `:net:download:service-interface` itself passes `spotbugsPlayDebug` cleanly (verified: `BUILD SUCCESSFUL`). The 13 `!!` operators there are invisible to SpotBugs.

The common `@NonNull` sink in every flagged method is `WorkManager.getInstance(context)` (`:35`, `:45`, `:79`, `:90`, `:92`, `:99`, `:105`; `FeedUpdateManagerImpl:47`, `:55`, `:88`), plus `media.fileExists()` at `:75` and `item.isDownloaded()` at `:40`.

#### Reachability — real latent bug or false positive?

This pattern is **not** an assertion that a null reaches the dereference. Unlike the sibling task's `NP_NULL_PARAM_DEREF`, SpotBugs here is making a *declaration-versus-usage consistency* complaint about a single method in isolation; it performs no call-site analysis at all. That is also why it is `category=STYLE`, `rank=16`. So "is it a real bug?" has to be answered by reading the call sites, which SpotBugs did not do. I enumerated all of them.

**Genuinely reachable (2 of the 6 methods, covering findings 5, 8, 9):**

- **Finding 8/9 — `context` into `restartUpdateAlarm` / `runOnce`.** Six call sites pass a contractually-nullable value with no guard:
  - `app/.../DownloadsPreferencesFragment.java:83` — `restartUpdateAlarm(getContext(), true)`; `Fragment.getContext()` is `@Nullable`.
  - `app/.../FeedItemlistFragment.java:178`, `:316`, `:540` — `runOnce(getContext(), feed, ...)`.
  - `app/.../feed/preferences/FeedSettingsPreferenceFragment.java:208` — `runOnce(getContext(), feed)`.
  - `app/.../feed/preferences/EditUrlSettingsDialog.java:51` — `runOnce(activityRef.get(), feed)`, a `WeakReference` dereference with **no null check**, inside `onConfirmed` after a blocking `DBWriter.updateFeedDownloadURL(...).get()`. This is the strongest one: the activity can be collected during that blocking call.
  The remaining callers are safe (`MainActivity.java:220`/`:373` pass `this`; `PreferenceUpgrader.java:185`, `FeedUpdateReceiver.java:20`, `OpmlImportActivity.java:127`, `OpmlBackupAgent.java:148`, `DBWriter.java:160`, `SyncService.kt:64` pass non-null contexts).
- **Finding 5 — `media` into `cancel`.** `FeedItem.media` is `FeedMedia?` (`model/.../FeedItem.kt:47`). Of the three call sites, two guard it (`EpisodeMultiSelectActionHandler.java:155-163` behind `hasMedia()`; `DBWriter.java:220-228` behind `item.getMedia() != null`) — but `app/.../actionbutton/CancelDownloadActionButton.java:33-34` does not:
  ```java
  FeedMedia media = item.getMedia();
  DownloadServiceInterface.get().cancel(context, media);
  ```
  Unguarded. In practice the button is only rendered for an in-progress download, which implies media exists — but nothing enforces that, and `cancel` dereferences `media` at `:75` before anything else.

**Not reachable from any current call site (findings 1, 2, 3, 4, 6, 7):** `context` into `downloadNow`/`cancel`/`cancelAll`/`getNumberOfActiveDownloads` and `item` into `downloadNow`/`download`. All callers pass an Activity `this`, a `@NonNull` `BroadcastReceiver.onReceive` context, or a non-null loop element — `DownloadActionButton.java:64,72,78`; `AutomaticDownloadAlgorithm.java:88,107`; `EpisodeMultiSelectActionHandler.java:145`; `PowerConnectionReceiver.java:38`; `ConnectivityActionReceiver.java:29`. These six are false positives *at today's call sites*, but note the honest framing: they are false positives about **reachability**, not about the **declaration**, which is genuinely wrong in all nine cases.

Net: this pile is **not** cleanly separable into "real bugs" and "false positives" the way the sibling's was. All nine share one true statement — the inherited nullable declaration contradicts the implementation — and three of them additionally have a live null path.

#### Current test coverage

Thin in a specific and important way: **the tests deliberately route around both affected classes.**

- **`:net:download:service` unit tests: 9 files, 63 `@Test` methods total** — `APCleanupAlgorithmTest.java` (1), `DbCleanupTests.java` (5), `DbNullCleanupAlgorithmTest.java` (1), `DbQueueCleanupAlgorithmTest.java` (1), `DbReaderTest.java` (20), `DbWriterTest.java` (19), `ExceptFavoriteCleanupAlgorithmTest.java` (3), `LocalFeedUpdaterTest.java` (13), plus `DbTestUtils.java` (0, helper).
- **Not one of them constructs or invokes `DownloadServiceInterfaceImpl` or `FeedUpdateManagerImpl`.** A repo-wide grep over all `src/test` and `src/androidTest` trees returns zero references to either class name. The only non-production references anywhere are `ClientConfigurator.java:50-51` (registration) and `MainActivity.java:226` / `FeedUpdateWorker.java:68,71,107,205` (reads of `FeedUpdateManagerImpl`'s public static constants, not its methods).
- **The tests that need a `DownloadServiceInterface` install the Kotlin `DownloadServiceInterfaceStub` instead** — `DbWriterTest.java:66`, `LocalFeedUpdaterTest.java:75`, and (outside this module) `NonSubscribedFeedsCleanerTest.java:128`, `ItemEnqueuePositionCalculatorTest.java:74`. `DownloadServiceInterfaceStub.kt:9-23` has empty method bodies, so it exercises none of the flagged code.
- **`AutoDownloadManagerImpl`** — the one impl with no findings — *is* the one installed for real in tests (`DbCleanupTests.java:87`, `DbNullCleanupAlgorithmTest.java:66`, `DbQueueCleanupAlgorithmTest.java:30`, `ExceptFavoriteCleanupAlgorithmTest.java:29`).
- **`:net:download:service` has no `src/androidTest`**; `app/src/androidTest` contains zero references to `FeedUpdateManager` or `DownloadServiceInterface`.
- **Upstream `:net:download:service-interface` tests (6 Kotlin files)** cover the interface half only. `DownloadServiceInterfaceTest.kt:139-161` constructs an anonymous `object : FeedUpdateManager()` and `:167-172` an anonymous `object : AutoDownloadManager()`, both with empty bodies, purely to test singleton get/set round-trips.

Net: **zero tests exercise any of the 9 flagged methods.** Behavior at all six methods is currently unverified, and the two classes are unreachable from any JVM test as currently structured (they need WorkManager, hence an instrumented or Robolectric host that does not exist here).

#### Characterization-test gaps

Everything below is uncovered today. Ranked by how badly an over-broad fix would go undetected.

1. **Null-`context` entry into `restartUpdateAlarm` / `runOnce(Context, Feed, boolean)`** — the genuinely reachable path (six unguarded call sites, `EditUrlSettingsDialog:51` worst). Nothing pins what happens today. This is the gap that decides whether tightening the Kotlin declaration is a *fail-fast improvement* or a *new crash*, because tightening moves the throw from inside `WorkManager.getInstance(null)` to `Intrinsics.checkNotNullParameter` at method entry — a different exception type, a different stack, and for `EditUrlSettingsDialog` a different thread context. **Untested, and it is the crux of the whole task.**
2. **Null-`media` entry into `cancel(Context, FeedMedia)`** from `CancelDownloadActionButton:33-34`. Same question, same absence of coverage.
3. **`DownloadServiceInterfaceImpl.download`'s early-return contract** (`:40-42`) — the `item.isDownloaded()` short-circuit is the only reason `context` escapes finding #3. Any edit to `:39-47` can silently turn one finding into two.
4. **`FeedUpdateManagerImpl.runOnce`'s `feed == null` branches** (`:71`, `:76`, `:83`) — three separate null-tolerant behaviors (refresh-all vs. single feed; constraint set vs. not; extras written vs. not). Untested; these are exactly what a careless "just make everything non-null" sweep would collapse.
5. **The `@NonNull`-preserving `runOnceOrAsk` pair** (`:92-115`) — currently the only correctly-typed methods in the file and the reason two findings exist rather than four. Nothing pins that they stay non-null.

Gaps 1 and 2 are where a fix would change **crash behavior** rather than produce a compile error — the failure mode least likely to be caught by review or by the existing suite, since the existing suite cannot reach these classes at all.

#### Java/Kotlin interop boundary

This is a service-interface/service split, and the two halves have very different exposure.

**`:net:download:service-interface` (Kotlin) — genuinely public API.** `README.md:6` records ~95 Java call sites across `:app`, `:net:download:service`, `:net:sync:service`, `:storage:database`, `:storage:importexport`, including three Java subclasses. `README.md:8-13` documents four compiler-enforced interop contracts (`const val` on the five `WORK_*` constants, `@JvmStatic` on the singleton accessors and both `DownloadRequestCreator.create` overloads, `String?` on the null-URL query methods, by-reference `setCurrentDownloads`). **`README.md:10` is directly on point for this task** and should be read before any fix is designed:

> `DownloadServiceInterface.isDownloadingEpisode`/`isEpisodeQueued`/`getProgress` take `url: String?`, not `String`. Tightening this to non-null looks like an obvious cleanup but is not: several Java callers pass a possibly-null `getDownloadUrl()` with no guard, relying on `HashMap.containsKey(null)` returning `false`. A non-null parameter would turn that into a crash via `Intrinsics.checkNotNullParameter`.

That warning is about a different set of methods, but it is exactly the hazard the abstract-method parameters now present, and a prior milestone already made the "don't tighten" call once in this module for the same reason.

**`:net:download:service` (Java) — mostly internal, with two exceptions.** Both `implementation project(...)` declarations mean nothing is exposed transitively. The two flagged classes are referenced from outside the module in only three places:
- `app/.../ClientConfigurator.java:50-51` — `new DownloadServiceInterfaceImpl()` / `new FeedUpdateManagerImpl()`, the startup registration (`PodcastApp.java:32`).
- `app/.../MainActivity.java:47,226` — reads `FeedUpdateManagerImpl.WORK_TAG_FEED_UPDATE` (a public static `String`, `FeedUpdateManagerImpl.java:29`).
- `net/.../feed/FeedUpdateWorker.java:68,71,107,205` — reads `FeedUpdateManagerImpl.EXTRA_*` constants (`:32-35`).

**None of the nine flagged methods is invoked by name on the concrete impl type anywhere.** Every caller goes through the abstract base (`DownloadServiceInterface.get()`, `FeedUpdateManager.getInstance()`). So the impl **method signatures are free to change** — the constraint is entirely on the Kotlin abstract declarations, plus these two implementors that must stay signature-compatible:
- `DownloadServiceInterfaceStub.kt:9-23` (production, in the interface module; installed by 4 test classes)
- the anonymous `object : FeedUpdateManager()` and `object : AutoDownloadManager()` in `DownloadServiceInterfaceTest.kt:140-161,167-172` — Kotlin `override` must match the supertype's nullability exactly, so any tightening of the abstract declarations **forces edits to this test file**, which is a File Scope item the planner should not miss.

Java callers, by contrast, would **not** break at compile time: Kotlin non-null parameters are not compile-enforced from Java. That asymmetry is what makes tightening feel safe and be risky — the breakage would be a runtime `NullPointerException` from `Intrinsics.checkNotNullParameter` at the six call sites named under Reachability, not a build failure.

#### Track prerequisites

No migration track is requested — this is standalone repo hygiene, same as the sibling task. The relevant gate is CI.

- **The mechanism is confirmed identical to the sibling task's.** `common.gradle:88-93` — SpotBugs `toolVersion 4.8.6`, `effort = 'max'`, `reportLevel = 'medium'` (so priority-2 findings fail), `excludeFilter = config/spotbugs/exclude.xml`, `ignoreFailures = true`. `common.gradle:96-111` wires a `doLast` on every task whose name contains `spotbugs`; `common.gradle:113-130` (`parseSpotBugsXml`) walks `BugInstance` elements in `build/reports/spotbugs/debug.xml` and `playDebug.xml` and re-raises a `TaskExecutionException`. `:net:download:service/build.gradle:5` applies `common.gradle` and `:6` applies `playFlavor.gradle`, so it gets `spotbugsPlayDebug` exactly like `:app`. Verified for this module specifically, as asked.
- **`lint` pulls SpotBugs in.** `common.gradle:140-145` — `tasks.matching { it.name == 'lint' }` `dependsOn` `spotbugsDebug` and `spotbugsPlayDebug`. `.github/workflows/checks.yml:43-45` runs `./gradlew checkstyle lint`; `unit-test` (`:53-58`) and `emulator-test` declare `needs: static-analysis`.
- **Exact local reproduction command:** `./gradlew :net:download:service:spotbugsPlayDebug`. Report at `net/download/service/build/reports/spotbugs/playDebug.xml`.
- **Baseline finding count: exactly 9 `BugInstance` entries, all `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE`, zero others.** Confirmed identical on three checkouts: clean `develop` at `f5d4c5551`, clean `develop` at current HEAD `5ae7d560f`, and the sibling task's branch `fix/spotbugs-nullability-findings`. The sibling's merged changes (`MessageEvent.message` → non-null, 71 lines of `exclude.xml`) **do not affect this pile** — verified, and consistent with this module's only two `MessageEvent` producers (`FeedUpdateManagerImpl.java:100,108`) and `EpisodeDownloadWorker.java:238` all passing non-null `getString(...)` values.
- **No existing suppressions apply.** `config/spotbugs/exclude.xml` contains zero entries matching `net.download` and zero matching `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE`.
- **Repo-wide state on current `develop` (`5ae7d560f`), from `./gradlew --continue spotbugsPlayDebug spotbugsDebug`:** `:app:spotbugsPlayDebug` now **passes** (sibling PR #23 merged 2026-08-12). `:net:download:service:spotbugsPlayDebug` is the **only remaining SpotBugs failure repo-wide**. But `:app-wearos:compilePlayDebugKotlin` **fails** — see Unknowns 1.

### Unknowns

1. **`:app-wearos` does not compile on `develop`, and it fails before SpotBugs runs. This invalidates both task files' sequencing premise and is the single most important thing for the planner to resolve.**
   `app-wearos/src/main/java/de/danoeh/antennapod/wearos/EpisodeDetailActivity.kt:115:28` — `Argument type mismatch: actual type is 'String?', but 'String' was expected` — a `Text(text = item.title)` call where `FeedItem.title` is `var title: String? = null` (`model/.../FeedItem.kt:30`). Pre-migration it was `private String title` with an unannotated getter (`git show b71cb7942^`), i.e. a platform type Kotlin accepted silently. **This is the same J2K widening as the other two piles, one module further out, and it is portfolio-caused** (`b71cb7942`, `:model` Milestones 1-7; `:app-wearos` is upstream code from `5fe70196e`/`b8f8426c8` that no milestone ever recompiled). `:app-wearos:lint` `dependsOn` `:app-wearos:compilePlayDebugKotlin` (verified via `--dry-run`), so `./gradlew checkstyle lint` cannot pass while it stands. Real CI confirms: run `31592660967` (push to `develop`, 2026-08-12 11:37) failed `Static Code Analysis` at `:app-wearos:compileFreeDebugKotlin` and **never reached a SpotBugs task**; `gh run list --branch develop --workflow Checks` shows **eight consecutive failures back to 2026-07-26**, i.e. `develop` has not had a green `static-analysis` in the visible history. **Decision needed:** is this a third task, or in scope here? It is a different module, a different failure class (compile, not static-analysis finding), and a different root milestone — my read is it should be its own task, but it must be scheduled *before or with* this one or neither this task's PR nor Milestone 15b's will see a green gate. **Flagging for the planner and José; not deciding.**
2. **Tighten the Kotlin declarations, or suppress?** The two coherent options:
   **(a) Tighten** `DownloadServiceInterface.kt` / `FeedUpdateManager.kt` parameters to non-null. Clears all 9 at the declaration, matches the sibling task's D1 precedent, and restores what the original Java author's usage implied. **But:** it adds `Intrinsics.checkNotNullParameter` to methods reachable from six unguarded nullable call sites (Reachability, above) plus `CancelDownloadActionButton:33`, converting today's deep NPE into an immediate crash at a different site; it contradicts the standing warning in `service-interface/README.md:10`, where a prior milestone made the opposite call for the same reason on sibling methods; and it forces edits to `DownloadServiceInterfaceStub.kt` and `DownloadServiceInterfaceTest.kt`. Unlike the sibling's `MessageEvent` case there are **two** producers to patch there but **seven** call sites to guard here, and they live in `:app`, not in the module under change.
   **(b) Suppress** via `exclude.xml`, method-scoped with `<Method params=... returns=...>` per the sibling's D3 precedent (6 entries covering 9 findings). Honest about the fact that these are STYLE/rank-16 findings SpotBugs raises without call-site analysis. **But** it leaves three genuinely reachable null paths documented-and-unfixed, and it suppresses a *correct* observation rather than a false one — a weaker claim than the sibling's two suppressions, which were provable false positives.
   A third option worth costing: **(c) guard the three reachable call sites** (`EditUrlSettingsDialog:51`, the five `getContext()` callers, `CancelDownloadActionButton:33`) and suppress the six unreachable findings — fixes the actual latent bugs without changing the shared interface contract, but touches `:app` files outside this module. **Planner + José call.**
3. **Would tightening actually clear all nine?** I did not empirically verify this — it requires editing production Kotlin, which is outside a researcher's remit. The mechanism (bytecode-confirmed `@Nullable` → SpotBugs) makes it near-certain, but the planner should have Step 1 prove it before the plan commits to option (a). Note the module's `README.md:12` warning that flavored test tasks silently report `UP-TO-DATE` without `--rerun`; the same caution applies to re-running `spotbugsPlayDebug` when checking a fix.
4. **What is the actual runtime behavior today when `context` is null at `FeedUpdateManagerImpl:88`?** `WorkManager.getInstance(null)` — I could not read the WorkManager sources in this environment. Whether it NPEs, throws `IllegalStateException`, or is somehow tolerant changes how the PR should describe the severity of findings 8/9. As with the sibling task's D5, the PR must not claim a crash it has not observed.
5. **`AutoDownloadManager` is a latent tenth-and-eleventh finding.** `AutoDownloadManager.kt:20,30` carries the identical widening; `AutoDownloadManagerImpl` escapes only because both its methods pass `context` to unannotated Java sinks. If either sink is ever annotated or migrated, two new findings appear. Whether to fix it now (consistency, and it is the same declaration file) or leave it (AGENTS.md minimal-diff) is a scoping call. **Flagging, not deciding.**
6. **Does this recur in `:net:sync:service`?** No, and the reason is instructive: `:net:sync:service-interface`'s `SynchronizationQueue.kt:24-30` has the same widening (`downloadUrl: String?`, `media: FeedMedia?`), but its implementor `SynchronizationQueueImpl.kt` is **Kotlin**, so this Java-focused detector never fires. The pattern appears precisely where **a Kotlin-migrated interface module is implemented by a still-Java module** — which is exactly `:net:download:service`'s situation and worth carrying into future `kotlin`-track research on this repo.
7. **Branch point.** The working tree is currently on `fix/spotbugs-nullability-findings` (the sibling's branch, now merged as PR #23). This task should branch fresh from the updated `develop` (`5ae7d560f`), per AGENTS.md's rule against committing to `develop`/`master` directly and the sibling's D7 precedent.

### Sources

**Authoritative baseline**
- `net/download/service/build/reports/spotbugs/playDebug.xml` — 9 `BugInstance` entries, parsed from a clean detached worktree at `develop` HEAD `5ae7d560f`; zero findings of any other type
- `./gradlew :net:download:service:spotbugsPlayDebug` — local reproduction, `BUILD FAILED`, `common.gradle` line 127
- `./gradlew --continue spotbugsPlayDebug spotbugsDebug` (repo-wide, `5ae7d560f`) — `:app` passes, `:net:download:service` fails with the 9, `:app-wearos:compilePlayDebugKotlin` fails
- `./gradlew :net:download:service-interface:spotbugsPlayDebug` — `BUILD SUCCESSFUL`, 0 findings

**The two flagged files**
- `net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/feed/DownloadServiceInterfaceImpl.java:26-120` — `:28-37` `downloadNow`, `:39-47` `download` (early return `:40-42`), `:49-60` `getRequest`, `:72-95` `cancel`, `:97-100` `cancelAll`, `:102-119` `getNumberOfActiveDownloads`
- `net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/feed/FeedUpdateManagerImpl.java:28-139` — `:29-35` public constants, `:45-59` `restartUpdateAlarm`, `:61-67` unflagged delegating overloads, `:69-90` `runOnce`, `:92-115` unflagged `runOnceOrAsk` pair, `:100`/`:108` `MessageEvent` producers
- `net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/episode/autodownload/AutoDownloadManagerImpl.java:11-54` — the unflagged control case, `:39-41`, `:52-54`

**Kotlin declarations (the root cause)**
- `net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/DownloadServiceInterface.kt:18-43`, `:45-63`
- `net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/FeedUpdateManager.kt:8-18` — `:16-18` the non-null `runOnceOrAsk` pair
- `net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/AutoDownloadManager.kt:20,30`
- `net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/DownloadServiceInterfaceStub.kt:9-23`
- `javap -v -p .../net/download/service-interface/build/tmp/kotlin-classes/playDebug/.../DownloadServiceInterface.class` — `RuntimeInvisibleParameterAnnotations: parameter 0: org.jetbrains.annotations.Nullable` on `downloadNow`, `cancelAll` et al.
- `grep -o '!!'` over `net/download/service-interface/src/main/java/` → **13** (DownloadRequestBuilder.kt 3, DownloadRequestCreator.kt 7, DownloadServiceInterface.kt 3) — resolves the "13 latent NPEs" discrepancy

**Pre-migration Java (intent comparison)**
- `git show d5b7f94aa^:net/download/service-interface/.../DownloadServiceInterface.java` — all abstract params unannotated
- `git show d5b7f94aa^:net/download/service-interface/.../FeedUpdateManager.java` — `runOnceOrAsk` `@NonNull`, `restartUpdateAlarm`/`runOnce` unannotated
- `git show b71cb7942^:model/.../FeedItem.java:32` — `private String title` (unannotated), vs. `model/.../FeedItem.kt:30` `var title: String? = null`
- `git log -1 d5b7f94aa` — commit message claiming "preserve 95-call-site Java API and 13 latent NPEs"; `git show --stat d5b7f94aa` — 7 production files converted

**Call sites (reachability)**
- Nullable-`context`: `app/.../ui/screen/preferences/DownloadsPreferencesFragment.java:83`; `app/.../ui/screen/feed/FeedItemlistFragment.java:178,316,540`; `app/.../ui/screen/feed/preferences/FeedSettingsPreferenceFragment.java:208`; `app/.../ui/screen/feed/preferences/EditUrlSettingsDialog.java:47-53`
- Non-null `context`: `app/.../activity/MainActivity.java:220,373`; `app/.../PreferenceUpgrader.java:185`; `app/.../activity/OpmlImportActivity.java:127`; `net/.../feed/FeedUpdateReceiver.java:20`; `net/.../PowerConnectionReceiver.java:38`; `net/.../ConnectivityActionReceiver.java:29`; `storage/database/.../DBWriter.java:160`; `storage/importexport/.../OpmlBackupAgent.java:148`; `net/sync/service/.../SyncService.kt:64`
- `item`/`media`: `app/.../actionbutton/DownloadActionButton.java:64,72,78`; `app/.../actionbutton/CancelDownloadActionButton.java:33-34` (**unguarded** `item.getMedia()`); `app/.../ui/episodeslist/EpisodeMultiSelectActionHandler.java:145,155-163` (guarded by `hasMedia()`); `storage/database/.../DBWriter.java:220-228` (guarded by `!= null`); `net/.../autodownload/AutomaticDownloadAlgorithm.java:88,107`
- `model/src/main/java/de/danoeh/antennapod/model/feed/FeedItem.kt:47` — `var media: FeedMedia? = null`

**Interop / API surface**
- `app/src/main/java/de/danoeh/antennapod/ClientConfigurator.java:7,21,50-51`; `app/src/main/java/de/danoeh/antennapod/PodcastApp.java:32`
- `app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java:47,226` — reads `FeedUpdateManagerImpl.WORK_TAG_FEED_UPDATE`
- `net/download/service/.../feed/FeedUpdateWorker.java:68,71,107,205` — reads `FeedUpdateManagerImpl.EXTRA_*`
- `net/download/service/build.gradle:5-6,14-16` — `common.gradle` + `playFlavor.gradle`, `implementation project(':net:download:service-interface')`
- `app/build.gradle:61-62` — `:app` depends on both halves
- `net/download/service-interface/README.md:6,8-13` — ~95 call sites; **`:10` is the standing "do not tighten to non-null" warning**
- `net/download/README.md:3-4`, `net/download/service/README.md` — the interface/service split

**Tests**
- `net/download/service/src/test/...` — 9 files, 63 `@Test`; zero references to either flagged class
- `net/download/service/.../DbWriterTest.java:66` and `.../local/LocalFeedUpdaterTest.java:75` — install `DownloadServiceInterfaceStub`, not the Impl
- `net/download/service/.../DbCleanupTests.java:87`, `DbNullCleanupAlgorithmTest.java:66`, `DbQueueCleanupAlgorithmTest.java:30`, `ExceptFavoriteCleanupAlgorithmTest.java:29` — install the real `AutoDownloadManagerImpl` (the unflagged one)
- `net/download/service-interface/src/test/.../DownloadServiceInterfaceTest.kt:140-161,167-172` — anonymous subclasses that would need signature edits under option (a)
- `storage/database/src/test/.../NonSubscribedFeedsCleanerTest.java:128`, `ItemEnqueuePositionCalculatorTest.java:74` — further Stub installs

**CI mechanics**
- `common.gradle:88-93` — SpotBugs config; `:96-111` — `doLast` wiring; `:113-130` — `parseSpotBugsXml` re-raise; `:140-145` — `lint dependsOn spotbugs*`
- `.github/workflows/checks.yml:18-52` — `static-analysis`; `:43-45` runs `./gradlew checkstyle lint`; `:53-58` — `unit-test needs: static-analysis`
- `config/spotbugs/exclude.xml` — zero `net.download` entries, zero `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` entries
- `gh run view 31592660967` — push to `develop` 2026-08-12, `Static Code Analysis` failed at `:app-wearos:compileFreeDebugKotlin`, SpotBugs never reached
- `gh run list --branch develop --workflow Checks --limit 8` — eight consecutive failures, 2026-07-26 → 2026-08-12
- `./gradlew :app-wearos:lint --dry-run` — confirms `:app-wearos:compilePlayDebugKotlin` is in `lint`'s task graph
- `git log origin/develop` — `5ae7d560f` = sibling task's PR #23, merged 2026-08-12

**Conventions**
- `AGENTS.md` — minimal-diff rule, module-README-first, branch rules, compile/test command rules
- `tasks/antennapod-fix-spotbugs-static-analysis-debt.md` — sibling task's Research and Plan (D1 tighten-at-source, D3 method-scoped suppression, D5 don't-claim-a-crash, D7 branch-from-develop precedents)

## Plan
_Last updated by: legacy-android-planner | 2026-08-12_
_Revised 2026-08-12 after `legacy-android-red-team` Loop 1 (CHALLENGE): D1 gained corrections (1) and (2); D7's comment-content section was split into templates (a)/(b) with a mixed-justification rule for `cancel`; Step 8's README instruction and the Suppression-quality / Documentation Acceptance Criteria were rewritten to match. **Both were wording defects in the plan's required artifacts — no Step's code-change substance, no File Scope entry, and no other Resolved Decision changed.**_

### Objective

Clear all 9 `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` findings in `:net:download:service` by (a) guarding the 7 genuinely-reachable nullable call sites in `:app` so the nullable inherited declarations become unreachable in fact, and (b) recording all 9 findings as method-scoped, comment-justified `exclude.xml` suppressions earned by those guards. The Kotlin abstract declarations in `DownloadServiceInterface.kt` / `FeedUpdateManager.kt` are **not** touched, per José's decision (option (c)) and the standing warning at `net/download/service-interface/README.md:10`. No migration track is requested — standalone repo hygiene.

### Resolved Decisions

#### D1 — What option (c) actually buys, and the structural fact that reshapes it. **Guarding call sites clears ZERO SpotBugs findings. All 9 must be suppressed, not 6.**

This is the single most important correction in this plan, and the developer must internalize it before writing a line.

Research established (Reachability section) that `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` "performs no call-site analysis at all" — it is a declaration-versus-body consistency complaint about one method in isolation. It follows directly, and I have re-verified it against the report XML, that **adding a null guard in `:app` changes nothing SpotBugs can observe about `DownloadServiceInterfaceImpl.cancel` or `FeedUpdateManagerImpl.runOnce`.** The parameter is still inherited-`@Nullable`; the body still dereferences it on every path; the finding still fires.

So the brief's phrasing — "guard the 3 reachable call sites, suppress the 6 unreachable findings" — would leave findings 5, 8 and 9 (the reachable ones) unsuppressed and **CI still red**. That is not a viable end state for a task whose entire purpose is to clear a CI gate.

The coherent version of option (c), and what this plan implements:

> Guard the 7 call sites so that **no** caller can pass null; then suppress **all 9** findings, with each suppression's comment naming the guards that make it true.

The honest framing for the PR: *the declaration is still wrong in all nine cases; we chose not to fix the declaration because tightening it relocates crashes (README:10), so we removed the reachability instead and recorded why.*

**Two corrections to that framing, both required reading before D7 (added 2026-08-12 after red-team Loop 1).**

**(1) The suppressions are not uniformly "made true by this diff" — only three of nine are.** Findings 5, 8 and 9 (`cancel`'s `media`, `restartUpdateAlarm`'s `context`, `runOnce`'s `context`) had live unguarded null paths before this diff, and the 7 guards in D4 are what remove them. Findings 1, 2, 3, 4, 6 and 7 (`downloadNow`'s `context`/`item`, `download`'s `item`, `cancel`'s `context`, `cancelAll`'s `context`, `getNumberOfActiveDownloads`'s `context`) were **already** unreachable at every call site before this diff — no guard was added for them, and none was needed (Research, Reachability). So this diff is a stronger justification than the sibling task's two suppressions **for three findings**, and for the other six it is the same kind of claim the sibling made: no caller passes null today. D7 splits the comment templates accordingly; a blanket "guarded in this commit" comment on the other six would attribute a guard that does not exist.

**(2) There is no enforcement mechanism tying these suppressions to the guards. Nothing re-arms if a guard is later removed.** This follows from the same fact that opens D1: `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` does zero caller analysis. SpotBugs can observe only whether a method's inherited declared nullability is consistent with its body's dereference pattern — it cannot observe whether a guard exists at any call site, either now or later. The `<Match>` entries key on `<Class>` + `<Method>` + `<Bug>` on the **implementation** method, all three of which are unchanged by anything a caller does. Consequences, stated plainly because the whole suppression rests on them:

- These seven suppressions are **unconditional and permanent** from SpotBugs's point of view. They will keep matching for as long as the implementation methods exist in their current shape, regardless of what happens in `:app`.
- If someone later deletes one of the 7 guards, **SpotBugs re-detects nothing, CI stays green, and the crash path silently reopens.** There is no automated signal.
- The guards are what make the underlying code actually safe. The suppressions record *why* the findings are tolerated; they carry no protection of their own and must never be described as if they did.
- The only mechanical safety net anywhere in this task is `CancelDownloadActionButtonTest` (D5, Step 1-2), which covers exactly one of the seven guards (site 1, finding 5). Sites 2-7 are defended by the `exclude.xml` comments, the `net/download/service/README.md` note, and human code review — nothing else. That raises the stakes on keeping that one test green and on the README note being accurate.

Every place this plan requires wording — the `exclude.xml` comments (D7), the README note (Step 8), and the Acceptance Criteria — states this rather than implying a safety net that does not exist.

**Rejected: option (a), tightening the declarations.** Not re-litigated — José decided. Recorded here only so the reviewer knows it was decided, not overlooked: it contradicts `service-interface/README.md:10`, converts today's deep NPE into `Intrinsics.checkNotNullParameter` at 7 sites in `:app`, and forces signature edits to `DownloadServiceInterfaceStub.kt` and `DownloadServiceInterfaceTest.kt`.

#### D2 — **Seven `exclude.xml` entries, not six.** Research's "six distinct methods" is off by one.

Research's Findings table and Unknown 2 both say "six distinct methods, nine parameter-level findings" and "6 entries covering 9 findings". Re-derived from `net/download/service/build/reports/spotbugs/playDebug.xml` by parsing every `BugInstance`'s `<Method name=... signature=...>`, the distinct methods are **seven**:

| Class | Method | Findings covered |
|---|---|---|
| `DownloadServiceInterfaceImpl` | `downloadNow(Context, FeedItem, boolean)` | `context`, `item` (2) |
| `DownloadServiceInterfaceImpl` | `download(Context, FeedItem)` | `item` (1) |
| `DownloadServiceInterfaceImpl` | `cancel(Context, FeedMedia)` | `context`, `media` (2) |
| `DownloadServiceInterfaceImpl` | `cancelAll(Context)` | `context` (1) |
| `DownloadServiceInterfaceImpl` | `getNumberOfActiveDownloads(Context)` | `context` (1) |
| `FeedUpdateManagerImpl` | `restartUpdateAlarm(Context, boolean)` | `context` (1) |
| `FeedUpdateManagerImpl` | `runOnce(Context, Feed, boolean)` | `context` (1) |

5 + 2 = **7 methods**, 9 findings. A developer who writes 6 entries trusting Research's prose ships a still-red CI. Everything else in Research's table (signatures, line ranges, flagged parameter names) is correct and confirmed.

#### D3 — Parameter-level suppression is not expressible; this is why "suppress only the unreachable ones" was never available.

Two of the flagged methods carry findings on *both* a reachable and an unreachable parameter — `cancel(Context, FeedMedia)` has `context` (unreachable, finding 4) and `media` (reachable, finding 5); `downloadNow` has `context` and `item` (both unreachable). A SpotBugs `<Match>` scopes to `<Class>` + `<Method>` + `<Bug>`; it has **no parameter-index matcher**. Suppressing finding 4 necessarily suppresses finding 5. The "6 unreachable / 3 reachable" split is therefore not implementable as a suppression boundary even in principle, independently of D1.

**Considered and rejected: `<Local name="context"/>`.** Each `BugInstance` does carry a `<LocalVariable name="context|item|media" role="LOCAL_VARIABLE_PARAMETER_NAMED"/>` annotation, and the SpotBugs filter spec does list a `<Local>` matcher — so a parameter-scoped filter is *conceivably* expressible. Rejected on three grounds: `<Local>`'s matching semantics against the `LOCAL_VARIABLE_PARAMETER_NAMED` role are undocumented for this detector and would need empirical proof; there is zero precedent for `<Local>` anywhere in `config/spotbugs/exclude.xml`; and under D1 there is no longer any reason to want the split, since the guards make every parameter unreachable. Recording the rejection so the red-team does not have to rediscover the option.

#### D4 — Concrete guard shape at each of the 7 call sites.

**Uniform shape: null-check-and-skip, with an early return where the call is the last meaningful statement.** No fallback Context, no exception, no user-visible message.

Rejected alternatives, once, for all seven sites:
- **`requireContext()` / `requireActivity()`** — throws `IllegalStateException` when detached. That *relocates* the crash rather than removing it, which is precisely the failure mode `net/download/service-interface/README.md:10` warns about and precisely the reason José chose (c) over (a). Using it here would reintroduce option (a)'s defect at the call site instead of the declaration.
- **Falling back to `getActivity()`, `getApplicationContext()`, or a cached Context** — changes which `Context` instance WorkManager is initialized/looked-up against. Research Unknown 4 is explicit that `WorkManager`'s behavior here is unverified in this environment; substituting a different Context is an unverifiable behavior change. Not acceptable on a task that suppresses findings on the strength of "we removed the reachability".
- **Log-and-skip** — `DownloadsPreferencesFragment`, `EditUrlSettingsDialog` and `CancelDownloadActionButton` have no logger; adding one is diff for no verification value (nothing asserts on logs). `FeedItemlistFragment` has `Log` imported but adding a call there only would be inconsistent. Skipped for AGENTS.md's minimal-diff rule.

| # | Site (`origin/develop` lines) | Guard | Notes |
|---|---|---|---|
| 1 | `CancelDownloadActionButton.java:32-37` | `FeedMedia media = item.getMedia(); if (media == null) { return; }` before the `cancel` call | Early return, **not** a guard around only the `cancel` line. Equivalence argument below. |
| 2 | `DownloadsPreferencesFragment.java:83` | `Context context = getContext(); if (context != null) { …restartUpdateAlarm(context, true); }` | Needs `import android.content.Context;` — the file imports only `android.content.SharedPreferences` today. |
| 3 | `FeedItemlistFragment.java:176-180` | Hoist `Context context = getContext();` into the click listener; extend the existing `if (feed != null)` to `if (feed != null && context != null)` | Needs `import android.content.Context;`. |
| 4 | `FeedItemlistFragment.java:311-320` | Inside the background thread, after `DBWriter.resetPagedFeedPage(feed).get()`: `Context context = getContext(); if (context != null) { …runOnce(context, feed); }` | Second-strongest live path — a `Thread` outliving the fragment. The `getContext()` read stays on the background thread (pre-existing; not introduced here) and stays inside the existing `try`, to keep the diff minimal. |
| 5 | `FeedItemlistFragment.java:539-541` | `Context context = getContext(); if (context != null) { …runOnce(context, feed, true); }` inside the existing `if` | — |
| 6 | `FeedSettingsPreferenceFragment.java:202-209` | Inside the `"RefreshAfterCredentialChange"` thread: `Context context = getContext(); if (context != null) { …runOnce(context, feed); }` | `android.content.Context` already imported (`:5`). Background thread outliving the fragment — same class of path as #4. |
| 7 | `EditUrlSettingsDialog.java:47-55` | `Activity activity = activityRef.get(); if (activity != null) { …runOnce(activity, feed); }`, placed after `feed.setDownloadUrl(updated)` | `android.app.Activity` already imported (`:3`). **This guard is the file's own existing idiom** — `show()` at `:29-32` already does `Activity activity = activityRef.get(); if (activity == null) { return; }`. Research's "strongest" case: the activity can be collected during the blocking `DBWriter.updateFeedDownloadURL(...).get()` at `:49`. |

**Why site 1 is an early return rather than a narrow guard.** Today, a null `media` makes `cancel(context, null)` throw inside `DownloadServiceInterfaceImpl.cancel` at `media.fileExists()` (`:75`), which aborts `onClick` — so `item.disableAutoDownload()` (`:35`) and `DBWriter.setFeedItem(item, false)` (`:36`) **do not run either**. An early return reproduces that exact outcome ("nothing happens") minus the crash. Guarding only the `cancel` line, and letting `:35-36` proceed, would be a genuine behavior *change*: it would persist a `disableAutoDownload` that today never gets persisted.

**User-visible behavior delta, stated plainly (all 7 sites):** on the happy path — none. On the null path, today's outcome is an exception (deep inside `WorkManager.getInstance(null)`, or `media.fileExists()` at site 1); after this change it is a silently-skipped feed refresh / download-cancel. Nothing is shown to the user in either case, because in both cases the operation does not complete. **No new user-visible string is introduced, so `ui/i18n/src/main/res/values/strings.xml` is not touched and AGENTS.md's new-strings rule is not engaged.**

#### D5 — Characterization tests: exactly one of the seven guards is testable, and the plan says so rather than pretending otherwise.

Research established zero tests reach any flagged method or call site. Assessed per site for what is testable **without adding Robolectric** (MEMORY: "KMP portability over Robolectric shims" — this repo's standing preference is to avoid adding a Robolectric host rather than shim around one):

- **Site 1, `CancelDownloadActionButton` — JVM-testable, and the test is genuinely load-bearing.** Verified the preconditions myself: `ItemActionButton`'s constructor (`:19-21`) only assigns a field — no framework touch; `FeedItem.disableAutoDownload()` (`FeedItem.kt:329-331`) is a pure field write; `DBWriter.setFeedItem` (`DBWriter.java:784-792`) submits to `runOnDbThread` and returns a `Future` without touching the DB on the calling thread; and `DownloadServiceInterface.setImpl(...)` lets the test install a recording double. A plain JUnit test in `app/src/test` can therefore drive `onClick` and assert on what reached `cancel`. This is the before/after equivalence proof for the one guard that changes an actually-observable control path.
- **Sites 2-6 (`Fragment.getContext()` returning null) — not testable at any tier available here.** Forcing a detached-fragment state deterministically needs an instrumented host; `:net:download:service` has no `src/androidTest` and `app/src/androidTest` has zero references to `FeedUpdateManager`. Building that host to assert "a null check is present" is disproportionate.
- **Site 7 (`EditUrlSettingsDialog`) — not testable.** `onConfirmed` is `private`, `activityRef` is `private`, the class is `abstract`, and reaching `onConfirmed` requires an `Activity` for `MaterialAlertDialogBuilder`.

**Disposition, per the unverifiable-equivalence policy:** sites 2-7 get honest Acceptance Criteria wording — the criterion is *"the guard is present, in the exact shape D4 specifies, verified by reading the diff"*, explicitly **not** *"a test proves the guard works"*. They are not descoped and no code is extracted to make them artificially testable. The `exclude.xml` comments and the `net/download/service/README.md` note (Step 7) are the durable guard against regression, since a future edit that removes a guard has a written statement to contradict.

#### D6 — `AutoDownloadManagerImpl`'s latent tenth/eleventh finding (Research Unknown 5). **Decision: OUT OF SCOPE. Documented, not fixed.**

`AutoDownloadManager.kt:20,30` carries the identical widening, and `AutoDownloadManagerImpl` escapes only because both methods hand `context` to unannotated Java sinks (`AutomaticDownloadAlgorithm.autoDownloadUndownloadedItems`, `EpisodeCleanupAlgorithmFactory.build().performCleanup`). Excluded because:

- **There are zero findings today.** Adding `exclude.xml` entries for findings that do not exist creates exactly the dead-suppression problem the sibling task had to clean up in its D4 — class-wide dead entries that masked real findings for months and misled this task's own pre-research context.
- **There is nothing to guard.** No caller passes a nullable context to either method (Research verified `AutoDownloadManagerImpl` is the impl actually installed in 4 test classes and is unflagged). A guard would be dead code.
- AGENTS.md's minimal-diff rule.

The hazard is real and will fire the moment either sink is annotated or migrated, so it is captured in the `net/download/service/README.md` note (Step 7) and named in Out of Scope with its trigger condition, rather than left as an undocumented trap.

#### D7 — Exact `exclude.xml` entries, re-verified against source rather than carried from Research.

Signatures re-read from `origin/develop` source (`DownloadServiceInterfaceImpl.java:27,39,73,98,103`; `FeedUpdateManagerImpl.java:45,69`) and cross-checked against each `BugInstance`'s JVM `signature=` attribute in the report XML. Syntax follows the sibling task's precedent, which survived two plan red-team loops and is now live in this file at `config/spotbugs/exclude.xml:51-96,108-123`: `<Class>` + `<Method params= returns=>` + `<Bug>`, fully-qualified parameter types, comma-separated with no spaces, primitives spelled as Java keywords.

**Placement:** the class-scoped section of `exclude.xml` is ordered by bug-pattern name. `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` sorts after `NP_NULL_ON_SOME_PATH`, so the block goes **after** the `FeedInfoFragment` entry (currently ending `:123`) and **before** the `RV_RETURN_VALUE_IGNORED_BAD_PRACTICE` entry (currently `:124-127`). Preserve that ordering.

`DownloadServiceInterfaceImpl` — `<Class name="de.danoeh.antennapod.net.download.service.feed.DownloadServiceInterfaceImpl"/>`:

| `<Method>` element | Findings |
|---|---|
| `<Method name="downloadNow" params="android.content.Context,de.danoeh.antennapod.model.feed.FeedItem,boolean" returns="void"/>` | context, item |
| `<Method name="download" params="android.content.Context,de.danoeh.antennapod.model.feed.FeedItem" returns="void"/>` | item |
| `<Method name="cancel" params="android.content.Context,de.danoeh.antennapod.model.feed.FeedMedia" returns="void"/>` | context, media |
| `<Method name="cancelAll" params="android.content.Context" returns="void"/>` | context |
| `<Method name="getNumberOfActiveDownloads" params="android.content.Context" returns="int"/>` | context |

`FeedUpdateManagerImpl` — `<Class name="de.danoeh.antennapod.net.download.service.feed.FeedUpdateManagerImpl"/>`:

| `<Method>` element | Findings |
|---|---|
| `<Method name="restartUpdateAlarm" params="android.content.Context,boolean" returns="void"/>` | context |
| `<Method name="runOnce" params="android.content.Context,de.danoeh.antennapod.model.feed.Feed,boolean" returns="void"/>` | context |

Note `returns="int"` on `getNumberOfActiveDownloads` — the only non-`void` entry, and the one most likely to be copy-pasted wrong.

**`params`/`returns` are mandatory as a pair** (SpotBugs filter spec: "If one of the latter attributes is specified the other is required for creating a method signature"). They are load-bearing here for two of the seven: `runOnce` has **three** overloads in `FeedUpdateManagerImpl` (`:61`, `:65`, `:69`) and only `(Context, Feed, boolean)` is flagged; `cancel`/`cancelAll` are distinct names but `runOnceOrAsk` (`:92`, `:96`) is the adjacent non-null pair that must **stay** analyzable. A name-only `<Method name="runOnce"/>` would suppress all three overloads.

**Comment content (required).** Two levels: one preamble comment per class block, then one comment per `<Match>` entry. The per-entry comment is **not** uniform across the seven — six of the nine findings had no guard added and must not claim one (D1 correction 1).

**Per-class preamble comment (identical content in both class blocks), stating:**
- (i) the finding is a **true** statement about the inherited Kotlin declaration, not a false positive — do **not** describe these as false positives anywhere;
- (ii) the declaration is deliberately left nullable per `net/download/service-interface/README.md:10`, because tightening it relocates crashes into `Intrinsics.checkNotNullParameter` at Java call sites that do not compile-break;
- (iii) **the suppression is unconditional and permanent from SpotBugs's point of view.** This detector performs no caller analysis, so it cannot observe whether any call-site guard exists. Removing a guard will **not** make the finding reappear and will **not** fail CI — the suppression keeps matching the unchanged implementation method while the crash path silently reopens. The guards below are what make the code safe; this comment and `net/download/service/README.md` are the only record tying them to these suppressions, and code review is the only thing enforcing them.

Do **not** write, in any comment, README line, or commit message, that removing a guard "re-arms" or "re-triggers" the finding. It does not.

**Per-entry comment — template (a): findings made unreachable by a guard added in this commit.** Applies to `restartUpdateAlarm` (finding 8), `runOnce(Context,Feed,boolean)` (finding 9), and the `media` half of `cancel` (finding 5). Name the guarded call sites by file, and say the guard is what removes the reachability:

| Entry | Guarded call sites to name |
|---|---|
| `restartUpdateAlarm` | `DownloadsPreferencesFragment.java:83` (D4 site 2) |
| `runOnce(Context,Feed,boolean)` | `FeedItemlistFragment.java:178,316,540` (sites 3-5), `FeedSettingsPreferenceFragment.java:208` (site 6), `EditUrlSettingsDialog.java:51` (site 7). Note in the comment that sites 4, 6 and 7 call the **two-arg** `runOnce(Context, Feed)` overload, which delegates to this three-arg one — the guard still removes the null before it reaches the flagged method, and the two-arg overload is itself unflagged because it only forwards. |
| `cancel` (`media` only) | `CancelDownloadActionButton.java:33` (site 1) |

**Per-entry comment — template (b): findings where no call site was ever unsafe.** Applies to `downloadNow` (findings 1, 2), `download` (finding 3), `cancelAll` (finding 6), `getNumberOfActiveDownloads` (finding 7), and the `context` half of `cancel` (finding 4). State plainly that **no guard was added or needed** — every current caller already passes a non-null value — and enumerate those callers: `DownloadActionButton.java:64,72,78`; `AutomaticDownloadAlgorithm.java:88,107`; `EpisodeMultiSelectActionHandler.java:145`; `PowerConnectionReceiver.java:38`; `ConnectivityActionReceiver.java:29` (an Activity `this`, a `@NonNull` `onReceive` context, or a non-null loop element). The declaration remains inconsistent with the implementation, which is why this is a suppression and not a correction. Do **not** attribute a guard to these entries.

**`cancel(Context, FeedMedia)` carries both templates in one comment, and must say so explicitly.** D3 established that a single `<Match>` cannot be scoped to one parameter, so this entry's one comment suppresses two findings with two different justifications, and must distinguish them by parameter name:
- `context` (finding 4) — template (b): already safe before this diff, no guard added; callers `EpisodeMultiSelectActionHandler.java:145` and `DBWriter.java:220-228` pass non-null contexts.
- `media` (finding 5) — template (a): reachable before this diff via `CancelDownloadActionButton.java:33-34`'s unguarded `item.getMedia()`; made unreachable by the early return added there in this commit (D4 site 1).

A single blended sentence covering both parameters is not acceptable — it would either over-claim a guard for `context` or under-record the one that matters for `media`.

#### D8 — Branch point (Research Unknown 7). **Decision: fresh from `origin/develop` at `5ae7d560f`.**

`git checkout develop && git pull && git checkout -b fix/net-download-service-nullable-param-spotbugs`. The working tree is currently on `test/ui-preferences-sync-settings-before-screenshot` with uncommitted Milestone 15b changes plus two untracked sibling-task files; none of it may be carried in. `5ae7d560f` already contains the sibling task's merged PR #23 (verified: `config/spotbugs/exclude.xml` on this branch is byte-identical to `origin/develop`, so the sibling's suppressions are the baseline this plan appends to). AGENTS.md forbids committing to `develop`/`master` directly.

#### D9 — PR and commit wording constraint. **This PR must not claim it unblocks Milestone 15b's CI.**

Both this task file and the sibling's stated "both piles must clear before Milestone 15b gets a real CI signal", and Research proved that incomplete: `:app-wearos:compilePlayDebugKotlin` fails on `develop` and `:app-wearos:lint` depends on it, so `./gradlew checkstyle lint` cannot pass repo-wide regardless of SpotBugs. The commit message and PR description must state plainly that **`tasks/antennapod-fix-app-wearos-compile-error.md` must also land** before `static-analysis` can go green, and must not describe this PR as restoring CI signal on its own. Same discipline the sibling task learned by shipping an AC it could not actually verify.

**Direct consequence for verification (see Open Question 1):** this PR's own `Checks` run will be red at `:app-wearos`, whichever of the three tasks merges first. The Acceptance Criteria below are therefore written against **module-scoped local commands plus a `--continue` repo sweep**, not against a green `./gradlew checkstyle lint`. A criterion this task cannot satisfy is not a criterion.

#### D10 — Runtime behavior of `WorkManager.getInstance(null)` (Research Unknown 4). **Deliberately unresolved; must not be asserted.**

Research could not read WorkManager's sources in this environment and this plan does not add an instrumented probe to find out. Under D4 the null can no longer reach it from any call site, so the exact exception type does not affect the fix's correctness. **Binding constraint:** the PR describes findings 5/8/9 as "a nullable value reaching a parameter the implementation dereferences unconditionally" and must **not** assert a specific exception type, stack, or severity it has not observed. Carried forward from the sibling task's D5.

#### D11 — Research enumeration completions (no action required, recorded so the reviewer does not re-flag them).

- `DownloadLogAdapter.java:106` calls `runOnce(context, feed)` and appears in **neither** of Research's reachable/safe lists. Checked: `context` is a `private final Activity` field assigned in the constructor (`:29-35`) — non-null, belongs in the safe list. No guard needed.
- `CancelDownloadActionButton` has **two** construction sites, not one: `ItemActionButton.forItem:50` (past the `media == null` early return at `:38`) and `ItemFragment.java:322` (inside the `media != null` branch opened at `:302`). Both construct only when media is non-null at that moment — which is why site 1 is a narrow residual risk (the button instance is retained by adapters and re-reads `item.getMedia()` at click time, so a media deletion between render and tap re-opens the window), not an obviously-live crash. The guard is still correct; the PR should describe it at this severity and no higher.

### Steps

Test-first. Every step compiles and leaves `:app:assembleDebug` and all unit tests green, and is independently committable.

**Standing caveat for every step:** `./gradlew checkstyle lint` is **already red on `develop`** at `:app-wearos:compilePlayDebugKotlin` (D9) and stays red until that separate task lands. "Leaves the build green" in this plan means `:app:assembleDebug` compiles and the named tests pass — never `checkstyle lint` exiting 0. Per `net/download/service-interface/README.md`, flavored test tasks silently report `UP-TO-DATE`: always pass `--rerun`, and run flavors as separate invocations.

1. **Characterize today's null-`media` behavior at `CancelDownloadActionButton` (the one testable guard).** Create `app/src/test/java/de/danoeh/antennapod/actionbutton/CancelDownloadActionButtonTest.java` (Java — the class under test and the call being characterized are Java; `:app`'s three existing unit tests are Java). Add a `RecordingDownloadServiceInterface` test double extending `DownloadServiceInterface`, installed via `DownloadServiceInterface.setImpl(...)`, that records each `cancel(context, media)` invocation. Two tests:
   - `nullMediaIsCurrentlyForwardedToCancel()` — a `FeedItem` with `media == null`; invoke `onClick(null)`; assert the double recorded **exactly one** `cancel` call whose `media` argument was `null`. The `onClick` invocation is wrapped in `try { … } catch (Throwable ignored) { }` with a comment stating why: execution continues past the recorded call into `DBWriter.setFeedItem`, which is out of this test's interest and not this test's assertion.
   - `nonNullMediaStillReachesCancel()` — a `FeedItem` with a non-null `FeedMedia`; same tolerance wrapper; assert the double recorded one `cancel` call with that media. This is the equivalence anchor for Step 2 — it must pass **identically** before and after the guard.

   Both green on the unmodified tree. Verify: `./gradlew --console=plain :app:testPlayDebugUnitTest --rerun`.

   **Defined fallback:** if the JVM host cannot run this test (e.g. a framework class loads eagerly and `common.gradle:50-53` sets no `returnDefaultValues`), **stop and report**. Do not add Robolectric, and do not extract logic out of `CancelDownloadActionButton` to make it testable.

2. **Guard site 1 and flip its characterization test.** In `app/src/main/java/de/danoeh/antennapod/actionbutton/CancelDownloadActionButton.java`, insert the `if (media == null) { return; }` early return per D4. In the same step, **replace** `nullMediaIsCurrentlyForwardedToCancel()` with `nullMediaIsSkippedAndCancelIsNotCalled()`: assert the double recorded **zero** calls and that `onClick` returned normally — this test carries **no** `try`/`catch`, and its absence is load-bearing (a normal return can only happen if the early return fired before `DBWriter`). `nonNullMediaStillReachesCancel()` is left untouched and must still pass. The replacement is deliberate — an intentional contract change, not a test deleted to make a build pass — and must be called out in the commit message. Verify: `./gradlew --console=plain :app:testPlayDebugUnitTest --rerun` and `./gradlew :app:assembleDebug`.

3. **Guard the three `FeedItemlistFragment` sites** (`:178`, `:316`, `:540`) per D4 rows 3-5, adding `import android.content.Context;`. No test — D5. Verify: `./gradlew :app:assembleDebug`.

4. **Guard `DownloadsPreferencesFragment:83` and `FeedSettingsPreferenceFragment:208`** per D4 rows 2 and 6, adding `import android.content.Context;` to `DownloadsPreferencesFragment` only (`FeedSettingsPreferenceFragment` already imports it at `:5`). Verify: `./gradlew :app:assembleDebug`.

5. **Guard `EditUrlSettingsDialog:51`** per D4 row 7, matching the file's own `show()` idiom at `:29-32`. Verify: `./gradlew :app:assembleDebug`.

6. **Add the seven suppressions to `config/spotbugs/exclude.xml`**, exactly as specified in D7 — two commented class blocks, placed between the existing `FeedInfoFragment` entry and the `RV_RETURN_VALUE_IGNORED_BAD_PRACTICE` entry, comments covering all four required points. Then verify with **`./gradlew :net:download:service:spotbugsPlayDebug --rerun`** and confirm `net/download/service/build/reports/spotbugs/playDebug.xml` contains **zero** `BugInstance` entries (baseline: exactly 9). Also run `./gradlew :app:spotbugsPlayDebug --rerun` and confirm it is still **zero** — four `:app` files were edited in Steps 2-5 and must not have introduced a new finding.

7. **Run the negative control on `params` matching** (sibling precedent — a green build alone does not prove scope). Temporarily change the `runOnce` entry's `params` to `android.content.Context,de.danoeh.antennapod.model.feed.Feed` (the unflagged two-arg overload at `FeedUpdateManagerImpl.java:65`), re-run `./gradlew :net:download:service:spotbugsPlayDebug --rerun`, and confirm the `runOnce` finding **reappears** — proving the attribute is signature-matched, not ignored. Revert immediately; the committed XML must carry the three-arg `…,de.danoeh.antennapod.model.feed.Feed,boolean` form and the final run must be zero findings.

8. **Record the invariants in the module READMEs**, per AGENTS.md's proactive-README rule (both facts are long-term-stable and generic, not task-specific). In `net/download/service/README.md` — currently four lines — add: that this module's three `*Impl` classes inherit `@Nullable`-annotated parameters from Kotlin superclasses in `:net:download:service-interface` and dereference them unconditionally; that the resulting `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` findings are suppressed in `config/spotbugs/exclude.xml`, distinguishing the three suppressed **on the strength of call-site guards added alongside them** (`cancel`'s `media`, `restartUpdateAlarm`, `runOnce` — naming the guarded files per D7 template (a)) from the six suppressed because **no caller has ever passed null** (`downloadNow`, `download`, `cancelAll`, `getNumberOfActiveDownloads`, `cancel`'s `context` — D7 template (b), no guard added or needed); that **the suppressions are unconditional and permanent — SpotBugs does no caller analysis, so removing a guard does not make the finding reappear and does not fail CI; this README note and code review are the only things tying the suppressions to the guards, and only site 1 (`CancelDownloadActionButton`) has a test** (D1); and that `AutoDownloadManagerImpl` is unflagged **only** because both its methods pass `context` to unannotated Java sinks, so annotating or migrating `AutomaticDownloadAlgorithm.autoDownloadUndownloadedItems` or `EpisodeCleanupAlgorithm.performCleanup` will produce two new findings (D6). In `net/download/service-interface/README.md`, extend the existing `:10` bullet to note that the same do-not-tighten reasoning now also covers the abstract-method `Context?`/`FeedItem?`/`FeedMedia?` parameters on `DownloadServiceInterface`, `FeedUpdateManager` and `AutoDownloadManager`, not just the three `url: String?` methods. Doc-only; build unaffected.

9. **Full verification and bookkeeping.** In order: `./gradlew :app:assembleDebug`; `./gradlew --console=plain :app:testPlayDebugUnitTest --rerun`; `./gradlew --console=plain :net:download:service:testPlayDebugUnitTest --rerun` (the 63 pre-existing tests must be untouched and green); `./gradlew :net:download:service:spotbugsPlayDebug --rerun` and `./gradlew :app:spotbugsPlayDebug --rerun` (both zero); then the repo sweep `./gradlew --continue checkstyle lint` and confirm that **the only** failing tasks are `:app-wearos` compile tasks (D9) — no SpotBugs task, no checkstyle task, anywhere. Update `features/antennapod-fix-net-download-service-spotbugs-debt.checkpoint.md`.

### File Scope

The reviewer rejects any diff touching a file not on this list.

**Production code (5 `:app` files — all guard-only edits):**
- `app/src/main/java/de/danoeh/antennapod/actionbutton/CancelDownloadActionButton.java`
- `app/src/main/java/de/danoeh/antennapod/ui/screen/feed/FeedItemlistFragment.java`
- `app/src/main/java/de/danoeh/antennapod/ui/screen/preferences/DownloadsPreferencesFragment.java`
- `app/src/main/java/de/danoeh/antennapod/ui/screen/feed/preferences/FeedSettingsPreferenceFragment.java`
- `app/src/main/java/de/danoeh/antennapod/ui/screen/feed/preferences/EditUrlSettingsDialog.java`

**Configuration (1 file):**
- `config/spotbugs/exclude.xml`

**Tests (1 new file):**
- `app/src/test/java/de/danoeh/antennapod/actionbutton/CancelDownloadActionButtonTest.java`

**Documentation (2 files):**
- `net/download/service/README.md`
- `net/download/service-interface/README.md`

**Spec bookkeeping (2 files):**
- `tasks/antennapod-fix-net-download-service-spotbugs-debt.md`
- `features/antennapod-fix-net-download-service-spotbugs-debt.checkpoint.md`

**Explicitly NOT in scope, and the reviewer should reject them on sight:**
- `net/download/service-interface/.../DownloadServiceInterface.kt`, `FeedUpdateManager.kt`, `AutoDownloadManager.kt`, `DownloadServiceInterfaceStub.kt` — the whole point of option (c) is that these are untouched.
- `net/download/service/.../DownloadServiceInterfaceImpl.java`, `FeedUpdateManagerImpl.java`, `AutoDownloadManagerImpl.java` — the flagged files themselves are **not** modified. The fix is entirely call-site + config.
- `net/download/service-interface/src/test/.../DownloadServiceInterfaceTest.kt` — only option (a) would have forced edits here.
- `app-wearos/**` — separate task.
- `ui/i18n/src/main/res/values/strings.xml` — D4 introduces no user-visible string.
- `app/.../ItemActionButton.java`, `ItemFragment.java`, `DownloadLogAdapter.java`, `EpisodeMultiSelectActionHandler.java`, `DownloadActionButton.java`, `MainActivity.java`, `PreferenceUpgrader.java`, `OpmlImportActivity.java`, `AddFeedFragment.java`, and the `:net`/`:storage` callers — all verified non-null or already guarded.
- Any pre-existing entry in `config/spotbugs/exclude.xml`, including the sibling task's four `MainActivity` entries and the `QueueFragment`/`FeedInfoFragment` entries. This task **appends only**.

### Acceptance Criteria

**Static analysis — the reason this task exists**
- [ ] `./gradlew :net:download:service:spotbugsPlayDebug --rerun` exits 0 and `net/download/service/build/reports/spotbugs/playDebug.xml` contains **zero** `BugInstance` entries (baseline: exactly 9, all `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE`).
- [ ] `./gradlew :app:spotbugsPlayDebug --rerun` exits 0 with **zero** `BugInstance` entries — the five `:app` edits introduced no new finding.
- [ ] `./gradlew --continue checkstyle lint` shows **no** failing `spotbugs*` task and **no** failing `checkstyle*` task in any module; the only failures are `:app-wearos` Kotlin compile tasks (D9). The reviewer names the failing task list in the review.
- [ ] All **nine** findings are individually accounted for against D2's seven-method table — the reviewer states the mapping, having recounted from the report XML rather than from Research's prose (Research says six methods; it is seven).

**Characterization — before the guard**
- [ ] `CancelDownloadActionButtonTest.nullMediaIsCurrentlyForwardedToCancel()` exists and passes on the unmodified tree (Step 1, before Step 2), proving a null `FeedMedia` reaches `cancel` today.
- [ ] `CancelDownloadActionButtonTest.nonNullMediaStillReachesCancel()` exists and passes on the unmodified tree.

**Characterization — after the guard**
- [ ] `CancelDownloadActionButtonTest.nullMediaIsSkippedAndCancelIsNotCalled()` passes: zero recorded `cancel` calls, and `onClick` returns normally. The test contains **no** `try`/`catch` around `onClick` — the reviewer confirms this by reading the test, since the normal return is what proves the early return preceded `DBWriter.setFeedItem`.
- [ ] `nonNullMediaStillReachesCancel()` passes **unchanged** after the guard — the non-null path is untouched.
- [ ] `./gradlew --console=plain :net:download:service:testPlayDebugUnitTest --rerun` is green with all 63 pre-existing `@Test` methods unmodified; `git diff --name-only develop` lists no file under `net/download/service/src/test/`.

**Guards — the six that no test can reach (D5, honest wording)**
- [ ] Each of sites 2-7 matches D4's specified shape exactly, **verified by reading the diff, not by a test**: `Context`/`Activity` hoisted to a local, null-checked, call skipped when null. The reviewer states site-by-site which shape each uses.
- [ ] **No site uses `requireContext()`, `requireActivity()`, `getActivity()`, `getApplicationContext()`, or any substitute Context.** This is the criterion that keeps option (c) from collapsing into option (a) (D4).
- [ ] `EditUrlSettingsDialog`'s guard sits **after** `feed.setDownloadUrl(updated)` — the URL edit still persists when the activity has been collected; only the refresh is skipped.
- [ ] `CancelDownloadActionButton` uses an early `return`, so `item.disableAutoDownload()` and `DBWriter.setFeedItem(...)` remain unreached on the null-media path, as they are today.
- [ ] `git diff develop -- ui/i18n/` is empty — no new user-visible string (D4).

**Suppression quality**
- [ ] `config/spotbugs/exclude.xml` gains exactly **seven** new `<Match>` entries, each `<Class>` + `<Method>` + `<Bug>` scoped. None is class-wide. None is name-only.
- [ ] Every `<Method>` element carries **both** `params` and `returns`, with fully-qualified parameter types; `getNumberOfActiveDownloads` carries `returns="int"` and the other six carry `returns="void"`.
- [ ] The `runOnce` entry's `params` is `android.content.Context,de.danoeh.antennapod.model.feed.Feed,boolean` — the reviewer confirms it matches only the three-arg overload at `FeedUpdateManagerImpl.java:69` and not the overloads at `:61`/`:65`, nor the `runOnceOrAsk` pair at `:92`/`:96`.
- [ ] **Negative control performed and reported** (Step 7): with the `runOnce` entry's `params` temporarily set to the two-arg overload, the finding reappears; reverted before commit. `git diff config/spotbugs/exclude.xml` in the final commit shows the three-arg form.
- [ ] Both class-block preamble comments state that the finding is a **true** statement about the inherited Kotlin declaration (not a false positive) and name `net/download/service-interface/README.md:10` as the reason the declaration is left nullable (D7 preamble (i), (ii)).
- [ ] Both class-block preamble comments state that the suppression is **unconditional and permanent** — SpotBugs performs no caller analysis, so removing a guard does **not** make the finding reappear and does **not** fail CI; the guards are what make the code safe and this comment plus `net/download/service/README.md` plus code review are the only things recording that (D7 preamble (iii), D1). The reviewer greps the diff for "re-arm"/"re-trigger" and confirms **zero** occurrences in `exclude.xml`, both READMEs, the commit message, and the PR description — the claim is false and must appear nowhere.
- [ ] The three template-(a) entries (`restartUpdateAlarm`, `runOnce`, and `cancel`'s `media` half) name the specific call sites guarded in this commit, by file, per D7's table. The reviewer confirms each named file actually carries a guard in this diff.
- [ ] The four template-(b) entries (`downloadNow`, `download`, `cancelAll`, `getNumberOfActiveDownloads`) state that **no guard was added or needed** because every current caller passes non-null, and enumerate those callers. The reviewer confirms **none** of them attributes a guard to this commit.
- [ ] `cancel`'s single entry carries **both** justifications, explicitly separated by parameter name: `context` — already safe pre-diff, no guard added; `media` — reachable pre-diff via `CancelDownloadActionButton.java:33-34`, made unreachable by the early return added here. A single blended claim covering both parameters fails this criterion (D3, D7).
- [ ] No pre-existing `exclude.xml` entry is modified, reordered, or removed; the new block sits between the `FeedInfoFragment` entry and the `RV_RETURN_VALUE_IGNORED_BAD_PRACTICE` entry.

**Interop and API surface**
- [ ] No public API break visible to Java callers outside the module: the three Kotlin declaration files and `DownloadServiceInterfaceStub.kt` are unmodified, so every one of the ~95 Java call sites and all three Java subclasses are unaffected by construction. `git diff --name-only develop` lists **no** file under `net/download/service-interface/` except its `README.md`.
- [ ] `git diff --name-only develop` lists **no** file under `net/download/service/src/` — the flagged implementation files are not edited.
- [ ] `./gradlew :app:assembleDebug` compiles clean.

**Documentation**
- [ ] `net/download/service/README.md` records the inherited-nullable-parameter hazard; the suppression policy split into the three findings suppressed on the strength of guards added here (guarded files named) and the six suppressed because no caller ever passed null (no guard added); the plain statement that the suppressions are unconditional and permanent, that removing a guard triggers **no** SpotBugs re-detection and **no** CI failure, and that only site 1 has a test (D1); and `AutoDownloadManagerImpl`'s latent-finding trigger condition (D6).
- [ ] `net/download/service-interface/README.md`'s existing `:10` do-not-tighten bullet is extended to cover the abstract-method parameters.

**Process**
- [ ] Branch is `fix/net-download-service-nullable-param-spotbugs`, taken fresh from `develop` at or after `5ae7d560f`; `git log develop..HEAD` contains no Milestone 15b commits and the diff contains no Milestone 15b, sibling-task, or `:app-wearos` files.
- [ ] The commit message and PR description state plainly that `tasks/antennapod-fix-app-wearos-compile-error.md` must also land before `static-analysis` can go green, and do **not** claim this PR restores CI signal on its own (D9).
- [ ] The PR does not assert a specific exception type or severity for `WorkManager.getInstance(null)` (D10), and describes the `CancelDownloadActionButton` window at the severity D11 establishes — a retained button re-reading `item.getMedia()` after a media deletion — not as an obviously-live crash.
- [ ] The two sibling task files (`antennapod-fix-spotbugs-static-analysis-debt.*`, `antennapod-fix-app-wearos-compile-error.*`) are not modified.

### Milestone

**Repo hygiene — SpotBugs nullable-parameter debt (`:net:download:service` findings, guarded at five `:app` call-site files, suppressed in `config/spotbugs`).** Standalone; not part of the Sync Settings vertical-slice sequence (Milestones 15-20). Non-billable — unaffiliated OSS portfolio case-study work.

Sequencing: one of **three** independent prerequisites for Milestone 15b's PR #22 getting a real CI signal, alongside `antennapod-fix-spotbugs-static-analysis-debt` (merged as PR #23) and `antennapod-fix-app-wearos-compile-error` (scaffolded, not started). Order among them is unconstrained; none blocks another. **All three must land** — see D9.

Case-study value, and the reason this one is worth writing up more than the sibling: it is the counter-example to the sibling's lesson. There, restoring the declaration's intent upstream was right. Here, the same J2K widening landed on an abstract method whose Java callers are not compile-checked against Kotlin nullability, so tightening the declaration would have converted a latent NPE into a guaranteed `Intrinsics.checkNotNullParameter` crash at seven sites — and the module's own README already recorded a prior milestone making exactly that call. The generalizable rule for the `kotlin` track: **when J2K widens an unannotated Java parameter, fix at the declaration only if every caller is compile-checked; where callers are Java, fix at the call sites and record why the declaration stays wide.** The pattern fires precisely where a Kotlin-migrated interface module is implemented by a still-Java module (Research Unknown 6) — a shape this portfolio will hit again on every service-interface/service split.

### Out of Scope

- **The `:app-wearos` compile failure.** `tasks/antennapod-fix-app-wearos-compile-error.md` / `features/antennapod-fix-app-wearos-compile-error.checkpoint.md` — scaffolded, not started, José's decision 2026-08-12. Referenced in this PR's description (D9) but no file under `app-wearos/` is touched.
- **The sibling SpotBugs task.** `antennapod-fix-spotbugs-static-analysis-debt` merged as PR #23; its files are not modified.
- **Tightening any Kotlin declaration** in `DownloadServiceInterface.kt`, `FeedUpdateManager.kt`, or `AutoDownloadManager.kt` — option (a), decided against (D1). Includes not tightening `runOnce`'s `feed: Feed?`, which is legitimately nullable (`FeedUpdateManagerImpl.java:71,76,83` has three distinct null-tolerant behaviors).
- **`AutoDownloadManagerImpl`'s latent tenth/eleventh finding** (D6) — zero findings today, nothing to guard, no suppression added. Documented in `net/download/service/README.md` with its trigger condition. Becomes a real task only if `AutomaticDownloadAlgorithm.autoDownloadUndownloadedItems` or `EpisodeCleanupAlgorithm.performCleanup` is annotated or migrated.
- **`FeedItemlistFragment.java:308`'s `runOnceOrAsk(getContext(), feed)`.** Not a SpotBugs finding — `runOnceOrAsk` takes a non-null `Context` (`FeedUpdateManager.kt:16-18`), so it is correctly typed and unflagged. But it is a live crash path *today*: a null `getContext()` there throws at `Intrinsics.checkNotNullParameter`, and it is the **only one of the twelve** `runOnceOrAsk` call sites in the repo that uses `getContext()` rather than `requireContext()` or `this` — including `:193` in its own file, eleven lines away. Genuinely adjacent, genuinely a bug, and deliberately excluded: it is not one of the nine findings, fixing it does not affect CI, and AGENTS.md forbids drive-by cleanup. See Open Question 2.
- **Instrumented coverage for the six untestable guards** (D5). Would require an `androidTest` host that neither `:net:download:service` nor the relevant `:app` paths have. Follow-up candidate: `antennapod-app-fragment-detach-instrumented-coverage`.
- **Auditing other modules for the same Kotlin-interface/Java-implementor pattern.** Research Unknown 6 establishes `:net:sync:service` is clean (its implementor is Kotlin) and identifies the shape to look for. A survey task, not this fix.
- **Any migration track** (`kotlin`, `gradle-kts`, `di`, `concurrency`, `compose`, `navigation`). None requested, none implied. In particular, migrating `:net:download:service` to Kotlin — which would dissolve this entire finding class — is explicitly not this task.
- **Rebasing Milestone 15b's PR #22.** Belongs to Milestone 15b's checkpoint, after all three prerequisites land.

## Open Questions

1. **This PR cannot get a green `Checks` run, and the plan is written to accept that — confirm.** Whichever of the three prerequisite tasks merges first, `:app-wearos:compile*Kotlin` fails before SpotBugs runs, so `static-analysis` will be red on this PR regardless of its own correctness (Research Unknown 1; `develop` has eight consecutive red runs back to 2026-07-26). The Acceptance Criteria are therefore written against module-scoped local commands plus a `--continue` sweep whose only permitted failures are `:app-wearos` compile tasks. **Two things need José's confirmation:** (a) that merging this PR on a red CI check is acceptable given the failure is provably pre-existing and unrelated, or alternatively that this PR should be held until `antennapod-fix-app-wearos-compile-error` lands; and (b) whether the reviewer is authorized to APPROVE on the module-scoped evidence rather than a green gate. Not deciding — this is a merge-policy call, not a technical one.
2. **`FeedItemlistFragment.java:308` — fix now or file it?** It is a live `Intrinsics.checkNotNullParameter` crash path on `runOnceOrAsk(getContext(), feed)`, the only `getContext()` among twelve `runOnceOrAsk` call sites repo-wide, eleven lines from a `requireContext()` sibling at `:193` — and the file is already open in this diff (sites 3, 4, 5). One line, and the reviewer will almost certainly notice it. Excluded above on AGENTS.md minimal-diff grounds and because it clears no finding, but the cost of folding it in is near zero and the cost of a separate task is not. **José's call:** add it to File Scope now, or file `antennapod-app-runonceorask-getcontext-crash` as a follow-up? Defaulting to *out of scope* until told otherwise.
3. **Should the `net/download/service/README.md` note be stronger — a written policy rather than an observation?** D6 leaves `AutoDownloadManagerImpl`'s latent findings undocumented-in-code and documented-in-README only. A stronger version would state a module-level rule ("any new Java override of a Kotlin abstract in `:net:download:service-interface` must guard its callers, not tighten the declaration"), which would apply to future migrations beyond this one. That edges from recording a fact toward setting repo policy on an OSS project this portfolio does not own. **Flagging as a judgement call with commercial/positioning implications** (per the AEPM Labs rule on public-facing claims in portfolio work) rather than deciding. Defaulting to the observational wording in Step 8.

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-08-12 | Loop 1 of max 2_

### Verdict
CHALLENGE

### Concerns

- **Severity:** CRITICAL
  **Class:** Characterization tests / suppressions prove equivalence, not just existence — unproven (and self-contradicted) equivalence claim baked into the shipped artifact
  **Concern:** The Plan requires every `exclude.xml` comment, the `net/download/service/README.md` note, and an Acceptance Criterion to state that **"removing a guard re-arms the finding."** This is false, and it is false for a reason the Plan itself already proved. D1's own load-bearing insight is: *"adding a null guard in `:app` changes nothing SpotBugs can observe about `DownloadServiceInterfaceImpl.cancel` or `FeedUpdateManagerImpl.runOnce`. The parameter is still inherited-`@Nullable`; the body still dereferences it on every path; the finding still fires."* SpotBugs's `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` match is scoped to `<Class>`+`<Method>`+`<Bug>` on the **implementation** method (e.g. `DownloadServiceInterfaceImpl.cancel`) and is computed purely from that method's declaration/body, independent of any caller. I verified this against the actual bytecode (`javap` on `DownloadServiceInterface.class` confirms the `Nullable` annotations are on the abstract declaration, unrelated to any call site) and against the live SpotBugs XML report (9 `BugInstance` entries, none carrying caller information). If a future developer removes the guard added at `CancelDownloadActionButton.java` (or any of the other 6 sites), **SpotBugs will not re-detect anything** — the suppression continues to match the unchanged implementation method silently, and CI stays green while a real crash path re-opens with zero automated signal. The Plan is telling its own reviewer, and the future maintainer reading the comment, that a safety net exists where D1 already proved none does. This is exactly the "verification is the value" failure mode this review exists to catch: a written claim of protection that the Plan's own analysis disproves.
  **Evidence:** Plan D1, line ~276 ("adding a null guard in `:app` changes nothing SpotBugs can observe... the finding still fires") directly contradicts D7 point (iv), line ~378 ("that removing a guard re-arms the finding"), Step 8 (line ~425, same claim for the README note), and the "Suppression quality" Acceptance Criteria bullet (line ~492, "warns that removing a guard re-arms the finding").
  **Suggested mitigation:** Rewrite all three instances to state the true property: the suppression is **unconditional and permanent** with respect to caller-side changes — SpotBugs cannot observe whether a guard is present or absent at any call site, so removing a guard does **not** cause the finding to reappear and does **not** fail CI. The only regression defense is the README note and code review; say so plainly rather than implying an automated one exists. This also means D5/AC's characterization tests (where they exist, e.g. `CancelDownloadActionButtonTest`) are the *only* mechanical safety net for the reachable findings (5, 8, 9) — worth stating explicitly, since it raises the stakes on keeping those tests green.

- **Severity:** MAJOR
  **Class:** Silent behavior changes from mechanical translation — unproven equivalence, weaker form (misattributed justification rather than false claim)
  **Concern:** D7's required comment content, point (iii), instructs: *"the specific call sites guarded in this commit that make null unreachable, named by file"* — worded as if this applies uniformly across all 7 `exclude.xml` `<Match>` entries / 9 findings. It doesn't. Research's own Reachability section (which I independently re-verified against the flagged source: `downloadNow`, `download`, `cancelAll`, `getNumberOfActiveDownloads` all have exclusively non-null callers today — `DownloadActionButton.java`, `AutomaticDownloadAlgorithm.java`, `EpisodeMultiSelectActionHandler.java`, `PowerConnectionReceiver.java`, `ConnectivityActionReceiver.java`) established findings 1, 2, 3, 4, 6, 7 were **already unreachable before this diff, with no guard added anywhere for them** — there was never an unguarded call site to fix. Only findings 5, 8, 9 are made unreachable *by a guard this commit adds*. Worse, the `cancel(Context, FeedMedia)` method's single `<Match>` entry (D3 already established parameter-level suppression is impossible) covers **both** finding 4 (`context` — already safe, no guard added) and finding 5 (`media` — reachable, now guarded by `CancelDownloadActionButton.java`) simultaneously, so the *same* comment must carry two different justifications at once, and D7 never flags that this entry needs mixed wording. A developer following D7 literally for the `downloadNow`/`download`/`cancelAll`/`getNumberOfActiveDownloads` entries either invents a guard-by-file attribution that doesn't exist (dishonest) or silently deviates from the instruction (technically fails the letter of the AC bullet requiring "names the guarded call sites by file"). D1's own framing compounds this: *"Here the suppressions become true because this diff makes them true"* is accurate for 3 of 9 findings and inaccurate for 6 of 9, which were already true independent of this diff.
  **Evidence:** Plan D7 comment-content requirement, line ~378, point (iii); D1's "become true because this diff makes them true" framing, line ~282; Research Reachability section ("Not reachable from any current call site (findings 1, 2, 3, 4, 6, 7)"), and the cross-cutting note that `cancel`'s single Match entry carries both an unreachable (context) and now-reachable-guarded (media) finding (D3).
  **Suggested mitigation:** Split D7's comment-content instructions into two explicit templates: (a) for findings 5/8/9 (`cancel`'s media parameter, `restartUpdateAlarm`, `runOnce`) — "reachable at N call sites before this diff; made unreachable by the guards added in this commit at [files]"; (b) for findings 1/2/3/4/6/7 (`downloadNow`, `download`, `cancelAll`, `getNumberOfActiveDownloads`, and `cancel`'s context parameter) — "no caller passes null today; verified call sites are [enumerate]; the declaration is still inconsistent with the implementation, which is why this is a suppression and not a correction, but no guard was needed or added for this parameter." Apply the same split to the `net/download/service/README.md` note (Step 8) and to the "Suppression quality" AC bullet.

### Notes on categories considered and dismissed
- **Public API breakage:** none — all three Kotlin declaration files and `DownloadServiceInterfaceStub.kt` are explicitly out of File Scope and untouched; verified no other file under `net/download/service-interface/` besides its README appears in scope.
- **Coverage gaps:** D5's honest-wording disposition (test only where JVM-testable, diff-reading verification elsewhere) is sound and matches the repo's standing unverifiable-equivalence policy; no gap left silently unaddressed.
- **Milestone/scope creep:** File Scope stays tightly within guard-only edits, one config file, one new test file, two READMEs, and spec bookkeeping — no architecture change, no drive-by fix of the adjacent `FeedItemlistFragment.java:308` crash bug (confirmed absent from every guarded line: 178, 316, 540 only).
- **`concurrency`/`compose`/`navigation`/`di`/`gradle-kts` tracks:** not applicable — no track requested, standalone repo hygiene, confirmed by both task files.
- **7-method/9-finding count, exclude.xml signatures, guard-site shapes, JVM-testability chain:** independently re-derived against the live SpotBugs XML report, `javap` bytecode output, and the actual source of all 7 call-site files — all confirmed accurate, including the `getNumberOfActiveDownloads` `returns="int"` trap and the absence of `requireContext()`/`requireActivity()` anywhere in D4's guard shapes.

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-08-12 | Loop 2 of max 2 (final)_

### Verdict
APPROVE

### Concerns

- **Severity:** MINOR
  **Class:** Cross-reference clarity (not an equivalence-claim defect)
  **Concern:** Step 8 says the `exclude.xml` comments must cover "all **four** required points," but D7's preamble explicitly numbers only (i), (ii), (iii) and states the "do not write re-arm/re-trigger" instruction as a separate, unnumbered sentence rather than as an explicit "(iv)". The count is defensible (three affirmative points plus the ban), but nothing in D7 labels the ban as the fourth point, so a developer or reviewer skimming D7's numbered list alone could undercount and miss that the ban is a required, checked item rather than incidental prose.
  **Evidence:** Step 8, line 462 ("comments covering all four required points") vs. D7, lines 394–399 (three numbered preamble points (i)–(iii), then an unnumbered "Do **not** write..." sentence).
  **Suggested mitigation:** Cosmetic only — label the ban sentence "(iv)" in D7's preamble list, or reword Step 8 to "three preamble points plus the re-arm/re-trigger ban." Does not block implementation: the AC (line 530) and D7 body both independently spell out the ban and its grep-check regardless of the numbering.

### Verification performed (Loop 2, independent of the planner's report)
- Grepped the full task file for `re-arm`/`re-trigger`/`rearm`/`retrigger`: every remaining occurrence is either the explicit ban (D7, line 399), the AC's grep-and-confirm-zero instruction (line 530), or a verbatim quote inside the Loop 1 verdict (historical record). The normative Plan text (D1, D7, Step 8, AC) now uniformly states the true "unconditional and permanent — SpotBugs performs no caller analysis" framing. The false claim is gone everywhere it is binding.
- Re-derived the reachable-vs-unreachable split independently from Research's Reachability section and D2's method table: findings 5 (`cancel`/`media`), 8 (`restartUpdateAlarm`), 9 (`runOnce`) are the only ones with a guard added this commit — matching D7 template (a) exactly. Findings 1, 2, 3, 4, 6, 7 have no guard added anywhere in Steps 2–5 — matching template (b) exactly. Cross-checked against every guard site in Steps 2–5 and confirmed no guard touches a template-(b) method.
- Confirmed `cancel`'s dual-justification instruction (D7, lines 411–415; AC, line 533) gives two separately labeled bullets by parameter name plus an explicit "a single blended sentence... is not acceptable" ban — unambiguous enough that blending would visibly violate a stated instruction.
- Checked Research, D2–D6, D8–D11, D4's guard-site table, Steps 1–7, and File Scope against Loop 1's independently-verified findings (7-method/9-finding count, guard shapes, no `requireContext()`/`requireActivity()`, File Scope confined to guard-only edits) — no drift found beyond the two sections the planner reported changing (D1, D7/Step 8/AC wording).

This is the final loop. Both Loop 1 findings (CRITICAL false "re-arms" claim; MAJOR uniform-template misattribution) are substantively fixed, not just reworded around the edges. The plan is ready for `android-migration-developer`.

## Implementation Notes
_By: android-migration-developer | 2026-08-12_

### Branch hygiene (before Step 1)
The working tree was on `fix/spotbugs-nullability-findings` (the sibling task's now-merged, now-stale branch) with two uncommitted modifications (this task's own `tasks/*.md`/`features/*.checkpoint.md`) and two untracked files belonging to the unstarted `antennapod-fix-app-wearos-compile-error` task. Per D8: `git fetch origin` confirmed `origin/develop` HEAD is `5ae7d560f` (PR #23, the sibling task, merged). Ran `git checkout -b fix/net-download-service-nullable-param-spotbugs origin/develop`; all four uncommitted/untracked files survived the switch untouched (verified via `git status` and `git diff --name-status origin/develop` immediately after). The two wearos-task files were never read, edited, or staged during this task.

### Commit message
```
fix(net-download-service): guard nullable-param call sites, suppress 9 SpotBugs findings

Nine NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE findings in
:net:download:service (DownloadServiceInterfaceImpl, FeedUpdateManagerImpl) trace to
PR #21's Kotlin migration of :net:download:service-interface: J2K widened every
unannotated Java abstract-method parameter to Context?/FeedItem?/FeedMedia?, and these
still-Java implementations dereference their parameters unconditionally.

Per net/download/service-interface/README.md:10's standing precedent, the Kotlin
declarations are not tightened — doing so would convert today's latent
null-dereference into a guaranteed Intrinsics.checkNotNullParameter crash at seven
Java call sites that don't compile-check against Kotlin nullability. Instead:
guarded the 7 call sites across 5 :app files that could pass null (adding a
null-check-and-skip or early return, no requireContext()/requireActivity()
substitution), then suppressed all 9 findings with 7 method-scoped exclude.xml
entries. 3 of 9 findings (cancel's media, restartUpdateAlarm, runOnce) are made
unreachable by the guards added here; the other 6 were already unreachable at every
call site before this diff and needed no guard. Both are documented separately in
the exclude.xml comments and net/download/service/README.md — neither is described
as a false positive, and the suppressions are stated plainly as unconditional and
permanent (SpotBugs performs no caller analysis, so it cannot detect if a guard is
later removed; only the CancelDownloadActionButton guard has a test behind it).

This PR does not by itself get static-analysis fully green.
tasks/antennapod-fix-app-wearos-compile-error.md (a separate, unrelated
:app-wearos:compile*Kotlin failure caused by :model's own prior Kotlin migration)
must also land before that CI job passes end to end.
```

### Test commands run
Discovered variants via `./gradlew :app:tasks --group verification` and
`./gradlew :net:download:service:tasks --group verification`: both modules use
`play`/`free` flavors × `Debug`/`Release` build types (no plain `debug` variant
exists), so `testPlayDebugUnitTest` and `spotbugsPlayDebug` are the correct task
names — matching what the Plan already specified.

- `./gradlew --console=plain :app:testPlayDebugUnitTest --tests "de.danoeh.antennapod.actionbutton.CancelDownloadActionButtonTest" --rerun` — before guard (Step 1): PASS (2/2). After guard (Step 2): PASS (2/2, one test replaced per Step 2).
- `./gradlew --console=plain :app:testPlayDebugUnitTest --rerun` (full suite, after all steps): PASS, all tests green.
- `./gradlew :app:assembleDebug` — PASS, run after every step (2–9).
- `./gradlew :net:download:service:spotbugsPlayDebug --rerun` — before Step 6: FAILS with 9 `BugInstance` (baseline, confirmed). After Step 6: PASS, 0 `BugInstance`.
- `./gradlew :app:spotbugsPlayDebug --rerun` — PASS, 0 `BugInstance`, confirming the 5 edited `:app` files introduced no new finding.
- Step 7 negative control: temporarily set the `runOnce` entry's `params` to the unflagged two-arg overload → `./gradlew :net:download:service:spotbugsPlayDebug --rerun` → BUILD FAILED, finding reappeared (1 `BugInstance`, confirmed `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE`) → reverted to the three-arg form → re-ran → PASS, 0 `BugInstance`. Proves the `params`/`returns` pair is signature-matched, not ignored.
- `./gradlew --console=plain :net:download:service:testPlayDebugUnitTest --rerun` — see "Deviations from plan" below: 73/79 pass, 6 pre-existing `LocalFeedUpdaterTest` failures reproduced identically on a clean `origin/develop` worktree.
- `./gradlew --continue checkstyle lint` (repo-wide sweep, Step 9) — the only 2 failing tasks anywhere in the build are `:app-wearos:compilePlayDebugKotlin` and `:app-wearos:compileFreeDebugKotlin` (D9's predicted, pre-existing, out-of-scope failure). Grepped the full `--continue` output: zero failing `spotbugs*` tasks, zero failing `checkstyle*` tasks, in any module.

### Characterization test results
- `nullMediaIsCurrentlyForwardedToCancel()` (Step 1, before guard) — PASS on the unmodified tree. Exercises: `CancelDownloadActionButton.onClick(null-context)` on a `FeedItem` with `media == null`; asserts the installed `RecordingDownloadServiceInterface` double recorded exactly one `cancel(context, media)` call with `media == null` — proving a null `FeedMedia` is forwarded to `cancel` today, before any guard exists.
- `nonNullMediaStillReachesCancel()` (Step 1, before guard, and unchanged through Step 2) — PASS both before and after. Exercises: the same click path with a real `FeedMedia` attached; asserts the double recorded exactly one `cancel` call carrying that exact `FeedMedia` instance (`assertSame`) — the equivalence anchor proving the guard does not disturb the non-null path.
- `nullMediaIsSkippedAndCancelIsNotCalled()` (Step 2, after guard, replaces the "before" test per the Plan's stated intentional contract change) — PASS. Exercises: the same null-media click path after the early-return guard; asserts **zero** recorded `cancel` calls and no `try`/`catch` wraps the call — a normal return can only happen if the guard fired before `DBWriter.setFeedItem` is reached, which is what actually proves the early return precedes the dereference rather than merely swallowing an exception.
- The other 6 guard sites (D5): no test — Research/Plan established none is JVM-testable without adding Robolectric (forbidden by Plan D5 and this repo's standing Robolectric-avoidance preference), and none was made testable by extracting logic. Verified instead by reading the diff against D4's exact shape: `Context`/`Activity` hoisted to a local, null-checked, call skipped when null, **no** `requireContext()`/`requireActivity()`/`getActivity()`/`getApplicationContext()`/substitute Context anywhere. `EditUrlSettingsDialog`'s guard sits after `feed.setDownloadUrl(updated)` (URL edit persists even if the activity was collected; only the refresh is skipped).

### Deviations from plan
1. **`cancel`'s `context` justification cites `EpisodeMultiSelectActionHandler.java:163`, not `:145` as D7's prose states.** D7's text (and the Plan's `## Open Questions`/Verdict sections carrying it forward) says "callers `EpisodeMultiSelectActionHandler.java:145` and `DBWriter.java:220-228`" for `cancel`'s non-null `context`. Independently re-read the source: `.cancel(activity, feedItem.getMedia())` is at line 163 (inside `deleteChecked`, guarded by the `hasMedia()` check opened at line 138); line 145 is a different call in the same file (`.download(activity, episode)`, inside `downloadChecked`). Research's own combined citation list for "item/media" callers lists both `:145,155-163` together for that file, which is almost certainly the source of the copy-through into D7's `cancel`-specific paragraph. Wrote the `exclude.xml` comment with the verified-accurate line (`:163`) rather than reproducing the slip, since the whole point of D1/D7 is that these comments must be factually true. No other content of D7's mixed-justification instruction was changed — both parameters are still separated by name, `context` is still template (b), `media` is still template (a) tied to `CancelDownloadActionButton.java:33-34`.
2. **`:net:download:service:testPlayDebugUnitTest` has 6 pre-existing failures unrelated to this diff.** Running the module's full unit-test suite (Step 9) surfaced 6 failing tests, all in `LocalFeedUpdaterTest` (`testUpdateFeed_FeedMetadata`, `testUpdateFeed_FeedIconFromFolder`, `testUpdateFeed_AddMoreItems`, `testUpdateFeed_AddNewFeed`, `testUpdateFeed_RemoveItems`, `testUpdateFeed_FeedIconDefault`), each a `NullPointerException` originating in `LocalFeedUpdater.java` / `DBWriter.java` production code that this task's File Scope forbids touching and that this diff does not modify. Verified via an isolated `git worktree` checked out at clean `origin/develop` (`5ae7d560f`, no changes from this task applied): identical 6 failures, identical stack traces, identical 13/79-vs-6 count. This is a pre-existing baseline gap — the Plan's Step 1 caveat ("the module's existing test suite runs and passes before you start") did not hold for this module going in, and Research's "63 `@Test` methods" figure predates whatever regressed this. Not fixed here: out of File Scope, unrelated to the 9 SpotBugs findings, and fixing production nullability in `LocalFeedUpdater`/`DBWriter` would itself be exactly the kind of drive-by change AGENTS.md forbids. Flagging as a follow-up candidate rather than deciding scope unilaterally.
3. No other deviation. All 9 Steps implemented as specified; all D1–D11 decisions followed as written (except the one line-number correction in #1 above); File Scope held exactly (verified via `git diff --name-status origin/develop` — only the 9 specified production/config/doc/spec files plus the 1 new test file appear).

### Acceptance Criteria — self-check against the Plan's list
- Static analysis: both `spotbugsPlayDebug` tasks (`:net:download:service`, `:app`) verified 0 `BugInstance`; all 9 findings accounted for against D2's 7-method table (5 methods in `DownloadServiceInterfaceImpl`, 2 in `FeedUpdateManagerImpl`); repo-wide `--continue checkstyle lint` sweep shows no failing `spotbugs*`/`checkstyle*` task anywhere, only the two `:app-wearos` compile tasks.
- Characterization before/after: satisfied, see above.
- Guards: all 7 sites match D4's shape exactly (self-verified by re-reading each diff hunk above); no `requireContext()`/`requireActivity()`/`getActivity()`/`getApplicationContext()` introduced anywhere; `EditUrlSettingsDialog`'s guard placement and `CancelDownloadActionButton`'s early-return ordering both verified; `git diff origin/develop -- ui/i18n/` is empty.
- Suppression quality: exactly 7 new `<Match>` entries, each `<Class>`+`<Method>`+`<Bug>` scoped with both `params` and `returns` on fully-qualified types; `getNumberOfActiveDownloads` carries `returns="int"`, the other six `returns="void"`; negative control performed and reversed (see Test commands); both preamble comments state true-not-false-positive, cite `README.md:10`, and state the unconditional/permanent framing; grepped `exclude.xml`, both READMEs, this Implementation Notes section, and all touched source files for "re-arm"/"rearm"/"re-trigger"/"retrigger" — zero occurrences; template (a)/(b) split applied per-entry, `cancel`'s entry carries both justifications separated by parameter name; no pre-existing entry modified, reordered, or removed — new block inserted between `FeedInfoFragment` and `RV_RETURN_VALUE_IGNORED_BAD_PRACTICE` exactly as specified.
- Interop/API surface: `git diff --name-only origin/develop -- net/download/service-interface/` shows only `README.md`; `git diff --name-only origin/develop -- net/download/service/src/` is empty; `:app:assembleDebug` compiles clean.
- Documentation: both READMEs updated per Step 8's exact content requirements.
- Process: branch is `fix/net-download-service-nullable-param-spotbugs` off `origin/develop` at `5ae7d560f`; no Milestone 15b, sibling-task, or `:app-wearos` files in the diff; the two sibling task files (`antennapod-fix-spotbugs-static-analysis-debt.*`, `antennapod-fix-app-wearos-compile-error.*`) untouched.

**Not independently verifiable by this developer:** the guards at sites 2–7 (D5) — no test exists or was added for them, per the Plan's own honest-wording disposition. **Needs manual/reviewer verification**, not a local command: nothing further — all Plan-specified local commands were run and their output is recorded above.

### Addendum — Loop 1 red-team fix (2026-08-12)
Per the Implementation red-team's MAJOR finding: the `runOnce` `<Match>` entry's comment in `config/spotbugs/exclude.xml` said "six" unguarded call sites but listed only five citations (that entry is scoped to `runOnce` alone; "six" was leftover from Research's aggregate count across `runOnce` + `restartUpdateAlarm` combined). Changed "six" to "five" so the count matches its own citation list; no other line in the file touched. Re-ran `./gradlew :net:download:service:spotbugsPlayDebug --rerun` — `BUILD SUCCESSFUL`, 0 `BugInstance` (unaffected, as expected for a comment-only change).

## Code Review Verdict
_By: migration-code-reviewer | 2026-08-12 | Loop 1 of max 3_

### Verdict
APPROVE

### Findings

No CRITICAL or MAJOR findings. Two MINOR notes recorded below; neither blocks merge.

- **Severity:** MINOR
  **Class:** Tests
  **Finding:** `CancelDownloadActionButtonTest.nullMediaIsSkippedAndCancelIsNotCalled()` (`app/src/test/java/de/danoeh/antennapod/actionbutton/CancelDownloadActionButtonTest.java:47`) asserts only `assertEquals(0, recordingImpl.cancelCalls.size())`. AC's wording ("assert the double recorded zero recorded `cancel` calls and that `onClick` returned normally") is satisfied only implicitly — reaching the assertion line without an uncaught exception is what proves the normal return, since the test carries no `try`/`catch` (correctly, per the Plan). This is sufficient and matches the Plan's intent, but there is no explicit comment in the test connecting "no try/catch + assertion reached" to "proves a normal return," which is the exact inferential step the AC and D5 rely on. A future reader might not realize the absence of a catch block is load-bearing.
  **Suggested fix:** Optional for this loop — a one-line comment on the test method (e.g. `// no try/catch: reaching this line without throwing is itself proof the early return preceded DBWriter.setFeedItem`) would make the proof self-documenting. Not required to APPROVE; the AC as written asks the reviewer to confirm this by reading the diff, which I did.

- **Severity:** MINOR
  **Class:** Documentation
  **Finding:** `net/download/service/README.md`'s new bullet list names all five guarded `:app` files in one sentence for the three template-(a) suppressions collectively, rather than tying each named file to the specific finding(s) it clears (that granularity lives only in `exclude.xml`'s per-entry comments, which is where the AC actually requires it). Not a defect — the AC for this bullet (Documentation section) only requires "the guarded files named," which is satisfied — but a reader of the README alone, without cross-referencing `exclude.xml`, could not tell which of the five files corresponds to which of the three template-(a) findings.
  **Suggested fix:** Optional. If touched again, consider a short per-finding breakdown mirroring `exclude.xml`'s structure. Leaving as-is is acceptable since `exclude.xml` is the authoritative, per-entry record and the README's job (per Step 8) is the higher-level policy statement.

### Independent verification performed (not taken from the developer's self-report)

**Static analysis**
- `./gradlew :net:download:service:spotbugsPlayDebug --rerun` → `BUILD SUCCESSFUL`; `grep -c "<BugInstance" net/download/service/build/reports/spotbugs/playDebug.xml` → **0** (baseline was 9). Confirmed.
- `./gradlew :app:spotbugsPlayDebug --rerun` → `BUILD SUCCESSFUL`; `grep -c "<BugInstance" app/build/reports/spotbugs/playDebug.xml` → **0**. Confirmed the 5 edited `:app` files introduced no new finding.
- `./gradlew --continue checkstyle lint` (full repo sweep, captured to a log and grepped for every `FAILED` occurrence): exactly two failing tasks anywhere in the build — `:app-wearos:compileFreeDebugKotlin` and `:app-wearos:compilePlayDebugKotlin`, both the pre-existing `EpisodeDetailActivity.kt:115` failure D9 predicts. Zero failing `spotbugs*` or `checkstyle*` tasks in any module. Matches the developer's report and the module-scoped AC exactly (Open Question 1's premise holds).
- All 9 findings accounted for against D2's 7-method table: verified the 7 `<Match>` entries in the diff carry exactly the method names/params/returns D2 and D7 specify (`downloadNow`, `download`, `cancel`, `cancelAll`, `getNumberOfActiveDownloads`, `restartUpdateAlarm`, `runOnce`), with `getNumberOfActiveDownloads` the sole `returns="int"` entry and the rest `returns="void"`.

**Wording discipline (D1/D7)**
- Grepped `config/spotbugs/exclude.xml`, both READMEs, and the Implementation Notes' commit-message draft for `re-arm|rearm|re-trigger|retrigger` (case-insensitive): zero occurrences anywhere the claim would be binding — the only hits are the meta-references describing the grep check itself. The false "re-arms" framing Loop-1 red-team caught is genuinely absent from the shipped artifact.
- Both class-block preambles in `exclude.xml` state the suppression is "unconditional and permanent," name the no-caller-analysis mechanism, and cite `net/download/service-interface/README.md:10` — matches D7 preamble (i)–(iii) verbatim in substance.

**D7 split-template application — read all 7 `<Match>` entries and their comments directly**
- Template (a) (guard added this commit, call sites named): `restartUpdateAlarm` → `DownloadsPreferencesFragment.java:83`; `runOnce` → `FeedItemlistFragment.java:178,316,540`, `FeedSettingsPreferenceFragment.java:208`, `EditUrlSettingsDialog.java:51`, with the two-arg-delegates-to-three-arg note (verified factually true by reading `FeedUpdateManagerImpl.java:64-66`: `runOnce(Context, Feed)` calls `runOnce(context, feed, false)`); `cancel`'s `media` half → `CancelDownloadActionButton.java:33`. All three guarded files/lines independently confirmed to actually carry a guard in this diff (see Guards section below).
- Template (b) (no guard added or needed, callers enumerated): `downloadNow` → `DownloadActionButton.java:64,72,78`; `download` → `AutomaticDownloadAlgorithm.java:107`, `EpisodeMultiSelectActionHandler.java:145`; `cancel`'s `context` half → `EpisodeMultiSelectActionHandler.java:163`, `DBWriter.java:220-228`; `cancelAll` → `PowerConnectionReceiver.java:38`, `ConnectivityActionReceiver.java:29`; `getNumberOfActiveDownloads` → `AutomaticDownloadAlgorithm.java:88`. Independently read every cited source line — all six template-(b) attributions are factually accurate (see line-by-line spot checks below); none attributes a guard that doesn't exist.
- `cancel`'s single `<Match>` entry's comment explicitly separates `context` (template (b)) and `media` (template (a)) into two distinct bullet points by parameter name, exactly as D7/D3 require — not a blended sentence.
- **Line-citation spot checks, done by reading source directly (not trusting the comment text):** `DownloadActionButton.java:64,72,78` — all three are `downloadNow(...)` calls, confirmed. `EpisodeMultiSelectActionHandler.java:145` — `.download(activity, episode)`, confirmed (not `.cancel`). `EpisodeMultiSelectActionHandler.java:163` — `.cancel(activity, feedItem.getMedia())`, confirmed — this is the corrected citation from Deviation #1 (D7's prose said `:145`; `:163` is the line that actually calls `cancel`). `PowerConnectionReceiver.java:38` and `ConnectivityActionReceiver.java:29` — both `cancelAll(context)` inside `onReceive`, confirmed. `AutomaticDownloadAlgorithm.java:88` — `getNumberOfActiveDownloads(context)`, confirmed; `:107` — `download(context, episode)`, confirmed. `FeedUpdateManagerImpl.java:64-66` — two-arg `runOnce` delegates to the three-arg overload, confirmed.

**No `requireContext()`/`requireActivity()` anywhere** — read all 5 modified `:app` files' diffs directly: `CancelDownloadActionButton.java` (early return on `media == null`), `DownloadsPreferencesFragment.java`, `FeedItemlistFragment.java` (3 sites), `FeedSettingsPreferenceFragment.java`, `EditUrlSettingsDialog.java` (uses `activityRef.get()` + null check, matching the file's own `show()` idiom) — every guard is a hoisted local + null check + skip-on-null. No `requireContext`, `requireActivity`, `getActivity`, or `getApplicationContext` substitution anywhere in the diff.

**Untouched-file checks**
- `FeedItemlistFragment.java:310` (`runOnceOrAsk(getContext(), feed)`) — read directly, unchanged, outside every diff hunk. Correctly left as GitHub issue #24 territory, not folded in.
- `AutoDownloadManagerImpl.java` — `git diff origin/develop` for this file is empty. Untouched.
- Grepped the full diff for every explicitly-forbidden path (`DownloadServiceInterface.kt`, `FeedUpdateManager.kt`, `AutoDownloadManager.kt`, `DownloadServiceInterfaceStub.kt`, `DownloadServiceInterfaceImpl.java`, `FeedUpdateManagerImpl.java`, `AutoDownloadManagerImpl.java`, `DownloadServiceInterfaceTest.kt`, `app-wearos/`, `strings.xml`) — zero matches.

**Characterization test**
- Read `CancelDownloadActionButtonTest.java` in full. `nullMediaIsSkippedAndCancelIsNotCalled()` and `nonNullMediaStillReachesCancel()` are the two tests present (the after-guard state; `nullMediaIsCurrentlyForwardedToCancel()` was replaced per Step 2, as specified). Both genuinely assert on recorded behavior (`assertEquals`/`assertSame` against a `RecordingDownloadServiceInterface` double's `cancelCalls` list), not merely invoking the method under test. Ran both: `./gradlew --console=plain :app:testPlayDebugUnitTest --tests "...CancelDownloadActionButtonTest" --rerun` → both PASS. Ran the full `:app:testPlayDebugUnitTest` suite → all green.
- Reasoned through the pre-guard equivalence claim directly against the code rather than trusting the report: on unguarded `origin/develop` code, `onClick` with `media == null` calls `DownloadServiceInterface.get().cancel(context, null)` — since the test double overrides `cancel` to just record the call (no dereference), a `nullMediaIsCurrentlyForwardedToCancel`-shaped test would pass on the unmodified tree exactly as claimed; the double is what makes this observable without WorkManager. Confirms the Plan D5 characterization design is sound and the developer's "PASS before and after" claim is credible, not just self-reported.

**Deviations — reproduced independently, not taken on trust**
1. Line-citation correction (`EpisodeMultiSelectActionHandler.java:163` vs. D7's `:145`) — verified above by reading the file directly. The developer's correction is accurate; `:145` is a different call (`.download`).
2. Six pre-existing `LocalFeedUpdaterTest` failures — ran `:net:download:service:testPlayDebugUnitTest --rerun` on this branch: 79 tests, 6 failed, all in `LocalFeedUpdaterTest`, all `NullPointerException` at `LocalFeedUpdater.java:131` (`Feed.getPreferences()` returning null). Then created an isolated `git worktree` at clean `origin/develop` (`5ae7d560f`) and reran the same task: same 6 test names failed. **Note for the record:** the worktree's first run surfaced a different, transient failure (`IllegalStateException: Could not initialize plugin: MockMaker`, a Mockito/Byte-Buddy cold-JVM initialization flake unrelated to this diff); a second run in the same worktree reproduced the identical `NullPointerException` at `LocalFeedUpdater.java:131` seen on the feature branch, confirming the developer's "identical stack traces" claim once the environment flake is accounted for. This is a pre-existing, out-of-File-Scope failure, correctly not fixed here.

**File Scope**
- `git diff --name-status origin/develop`: 5 `:app` files, `config/spotbugs/exclude.xml`, `net/download/service/README.md`, `net/download/service-interface/README.md`, `tasks/*.md`, `features/*.checkpoint.md` — 9 tracked files, matching File Scope exactly. Plus the 1 new untracked test file (`CancelDownloadActionButtonTest.java`). Total 10, matching File Scope's list precisely. No forbidden file appears (checked above).
- Branch: `fix/net-download-service-nullable-param-spotbugs`; `git merge-base HEAD origin/develop` = `git rev-parse origin/develop` = `5ae7d560f`, confirming the branch has zero commits beyond `origin/develop` (all work is uncommitted, per D8/AC Process). No Milestone 15b or sibling-task files in the diff.

### Acceptance Criteria walkthrough

All checkboxes verified satisfied by independent reproduction above: static-analysis (0 findings both modules, module-scoped `--continue` sweep clean except the disclosed `:app-wearos` failures, all 9 findings mapped to D2's 7 methods), characterization before/after (both old- and new-shape tests reasoned/verified), the 6 untestable guards (all verified by reading the diff, no `requireContext`/`requireActivity`/substitute Context anywhere, `EditUrlSettingsDialog`'s guard after `feed.setDownloadUrl`, `CancelDownloadActionButton`'s early return preserves the today-nothing-runs contract, `ui/i18n/` diff empty), suppression quality (exactly 7 entries, both `params`/`returns` present and fully-qualified, `runOnce` three-arg form, negative control reported and reversed, zero "re-arm" occurrences, template (a)/(b) correctly split and cross-checked against source, `cancel`'s dual justification correctly separated, no pre-existing entry touched), interop/API surface (no file under `net/download/service-interface/` besides its README, no file under `net/download/service/src/`, `:app:assembleDebug` compiles — confirmed via the green `:app:testPlayDebugUnitTest` run which depends on it), documentation (both READMEs updated per Step 8's content), and process (branch/commit hygiene, PR-wording constraints recorded in the drafted commit message, D10's no-exception-type-asserted discipline followed, sibling task files untouched).

This is a well-executed implementation of a plan that already survived two red-team loops. The developer caught and correctly resolved one factual error in the Plan's own prose (the line-citation slip) rather than propagating it, and disclosed the pre-existing test-suite gap rather than silently working around it or expanding scope to fix it. Ready for `legacy-android-red-team`'s implementation review.

## Red-Team Verdict — Implementation
_By: legacy-android-red-team | 2026-08-12 | Loop 1 of max 2_

### Verdict
CHALLENGE

### Concerns

- **Severity:** MAJOR
  **Class:** Suppression comments prove equivalence, not just existence — miscounted call-site citation in the artifact that is this task's sole non-test regression record
  **Concern:** The `runOnce(Context, Feed, boolean)` `<Match>` entry's comment in `config/spotbugs/exclude.xml` states: *"context: reachable before this diff via **six** unguarded `getContext()`/`WeakReference` call sites; made unreachable by the null checks added in this commit at `FeedItemlistFragment.java:178,316,540`, `FeedSettingsPreferenceFragment.java:208` and `EditUrlSettingsDialog.java:51`."* That is five sites (178, 316, 540, 208, 51), not six. The "six" is a leftover from Research's Reachability section, which counted six unguarded call sites **combined** across both `restartUpdateAlarm` (1 site: `DownloadsPreferencesFragment.java:83`) and `runOnce` (5 sites, listed above) — a total the Plan's D4 table also uses when describing the whole reachable-`context` problem in aggregate. But this specific `<Match>` entry's comment is scoped to `runOnce` alone (it has its own separate entry, and separate comment, for `restartUpdateAlarm` three entries above it, correctly citing only `DownloadsPreferencesFragment.java:83`). Carrying the aggregate "six" into the `runOnce`-only comment, while listing only the five sites that actually belong to `runOnce`, makes the sentence internally inconsistent — count a reader can verify wrong by literally counting the file:line citations in the same sentence.
  This matters more than a typical typo here specifically because of what this task's own D1/D7 process establishes: these comments are described, in the class-block preamble two entries above, as **"the only record tying [the guards] to these suppressions"** once a guard is later touched — code review and this comment are the sole regression defense (no automated re-detection exists, confirmed empirically below). A future engineer auditing this suppression against "did all the guards this comment claims still exist" is being handed a count that doesn't match its own enumeration. It is exactly the class of self-contradicting precision defect Loop 1 of the *plan* red-team caught twice (the false "re-arms" claim, the "six vs. seven methods" undercount) — this is a third instance of the same pattern, now in the shipped artifact, and it slipped past both plan red-team loops (which reviewed prose, not the final XML) and the code-review loop (which verified each cited call site was individually accurate but did not check the stated aggregate count against its own list).
  **Evidence:** `config/spotbugs/exclude.xml` (diff against `origin/develop`), the `runOnce` `<Match>` entry's preceding comment: "reachable before this diff via six unguarded `getContext()`/`WeakReference` call sites; made unreachable by the null checks added in this commit at `FeedItemlistFragment.java:178,316,540`, `FeedSettingsPreferenceFragment.java:208` and `EditUrlSettingsDialog.java:51`" — five citations following a claim of six. Cross-checked against the `restartUpdateAlarm` entry's own comment three entries above, which correctly and separately cites only `DownloadsPreferencesFragment.java:83` (the sixth site Research's aggregate count included).
  **Suggested mitigation:** Change "six" to "five" in the `runOnce` entry's comment. One-word fix; no code, test, or File Scope change required. Re-verify no other comment in this block (or the two READMEs) carries the same aggregate-vs.-per-entry count mismatch — I checked the `restartUpdateAlarm`, `downloadNow`, `download`, `cancelAll`, `getNumberOfActiveDownloads`, and `cancel` entries' comments and found none of the other six has this defect; the aggregate "six" only leaks into the one entry whose own citation count contradicts it.

### Verification performed (independent of the developer's and code reviewer's self-reports)

- **Read all 5 modified `:app` files' diffs directly against `origin/develop`.** Every one of the 7 guards genuinely closes the null path Research identified as reachable, not merely relocates the read:
  - `CancelDownloadActionButton.java` — confirmed `DownloadServiceInterfaceImpl.cancel` dereferences `media.fileExists()` on its first line, before `context` is touched (read the impl source directly, `net/download/service/.../DownloadServiceInterfaceImpl.java:73-76`) — the early return is structurally equivalent to today's crash-then-abort outcome for `item.disableAutoDownload()`/`DBWriter.setFeedItem`, exactly as D4 argues.
  - `EditUrlSettingsDialog.java` (Research's "strongest" case) — confirmed the guard captures `activityRef.get()` into a local (`Activity activity = ...`) **before** the null check and the subsequent use. A strong local reference pins the referent against GC for the rest of the guarded block, so there is no reintroduced TOCTOU window between the check and the `runOnce(activity, feed)` call — this is the same idiom the file's own `show()` method already uses at `:29-32`. Structurally closes the race, does not just relocate the read.
  - `FeedItemlistFragment.java`'s three sites, including the background-thread one (`:311-320`, inside a `Thread` after `DBWriter.resetPagedFeedPage(feed).get()`) — confirmed the `getContext()` read happens exactly once (hoisted into a local), on the same thread as before, at the same point in the method as before. The pre-existing background-thread `getContext()` read itself is unchanged by this diff (correctly, per the Plan's own framing); what changed is that the previously-unconditional pass-through of a possibly-null value is now gated. This closes the specific null-parameter reachability SpotBugs flags without claiming to fix (or worsening) the separate, out-of-scope thread-safety question of calling `Fragment.getContext()` off the main thread.
  - `DownloadsPreferencesFragment.java` and `FeedSettingsPreferenceFragment.java` — same hoist-check-skip shape, confirmed.
  - No guard anywhere uses `requireContext()`, `requireActivity()`, `getActivity()`, or a substitute Context — grepped all 5 diffs directly.
- **`cancel(Context, FeedMedia)`'s single `<Match>` entry — re-derived SpotBugs filter semantics myself rather than trusting three prior passes.** Read `DownloadServiceInterfaceImpl.java` in full: there is exactly one method named `cancel`, with exactly one signature (`Context, FeedMedia`) — no overload exists that this `<Class>`+`<Method params=... returns="void">`+`<Bug>` triple could ambiguously also match. The entry is scoped to precisely this one method in this one class for precisely this one bug pattern, and (confirmed via `DownloadServiceInterfaceImpl`'s cancel body dereferencing both `media` unconditionally and `context` unconditionally via `WorkManager.getInstance(context)`) legitimately covers both flagged parameters at once, which is the only outcome possible given SpotBugs has no parameter-index matcher (D3, independently re-confirmed against the SpotBugs filter grammar's `<Method>` element, which carries no parameter-position attribute). Not broader than intended: no other class, no other method, no other bug pattern is touched by this entry.
- **Empirically tested the "unconditional and permanent" claim, not just reasoned about it.** Temporarily removed the `if (media == null) { return; }` guard from `CancelDownloadActionButton.java` (restored immediately after, confirmed via `git diff origin/develop` byte-identical to the intended diff post-restore), then ran `./gradlew :net:download:service:spotbugsPlayDebug --rerun`: **`BUILD SUCCESSFUL`, 0 `BugInstance` entries** — unchanged. This is not a coincidence of caching: `net/download/service/build.gradle` has zero dependency on `:app` (dependency direction is inverted — `:app` depends on `:net:download:service`), so a change to `:app` source cannot, even in principle, alter what `:net:download:service:spotbugsPlayDebug` observes. The claim in D1/D7/both READMEs — "removing a guard will not make the finding reappear and will not fail CI" — is therefore true at the build-topology level, not merely because SpotBugs's detector happens not to look; there is no dependency edge through which a guard's removal could ever reach this task. Confirmed both empirically and structurally.
- **Re-ran the two `:app`/`:net:download:service` static-analysis gates independently:** `./gradlew :net:download:service:spotbugsPlayDebug --rerun` → 0 `BugInstance` (baseline 9). `./gradlew :app:spotbugsPlayDebug --rerun` → 0 `BugInstance`. Matches code review's reported numbers.
- **Ran `CancelDownloadActionButtonTest` independently:** both `nullMediaIsSkippedAndCancelIsNotCalled` and `nonNullMediaStillReachesCancel` PASS. Read the test file in full — both genuinely assert on a recording double's captured state (`assertEquals`/`assertSame`), not merely invoking the method under test; the after-guard test has no `try`/`catch`, so reaching its assertion is itself proof of a normal return preceding `DBWriter.setFeedItem`, as D5/AC require.
- **`strings.xml` claim (D4's "no new user-visible string"):** `git diff origin/develop -- ui/i18n/src/main/res/values/strings.xml` is empty, confirmed directly — not merely trusted from the AC checkbox.
- **File Scope:** `git diff --name-status origin/develop` lists exactly the 9 tracked files File Scope specifies (5 `:app` production files, `config/spotbugs/exclude.xml`, 2 READMEs, 2 spec-bookkeeping files) plus the 1 new untracked test file — no more, no less. No forbidden path (`DownloadServiceInterface.kt`, `*.Impl.java`, `app-wearos/**`, etc.) appears.
- **`EpisodeMultiSelectActionHandler.java` line-citation deviation (developer's disclosed correction of `:145`→`:163`):** read the file directly. Line 145 is `.download(activity, episode)` inside `downloadChecked`; line 163 is `.cancel(activity, feedItem.getMedia())` inside `deleteChecked`, reached only when `feedItem.hasMedia()` is true (confirmed `hasMedia()` returns `media != null` in `FeedItem.kt:308-310`) — so `getMedia()` is non-null at the call, and `activity` is a `private final Activity` field, non-null by construction. The developer's correction is accurate; the exclude.xml comment's `:163` citation is correct.
- **Did not re-verify** the two disclosed deviations (line-citation correction; 6 pre-existing `LocalFeedUpdaterTest` failures) beyond the spot-check above and trusting code review's independent worktree reproduction — both are low-value re-verification relative to the structural/empirical checks above, per the task's own guidance.

### Notes on categories considered and dismissed
- **Public API breakage:** none. `git diff --name-only origin/develop -- net/download/service-interface/` shows only its `README.md`; `git diff --name-only origin/develop -- net/download/service/src/` is empty. Confirmed directly, not from the AC checkbox alone.
- **Silent behavior changes from mechanical translation:** the guard-and-skip behavior at all 7 sites is a genuine, disclosed behavior change (exception → silent skip) on the null path, but it is the change the Plan explicitly designed and the AC explicitly requires be non-user-visible — confirmed no string added, no toast/snackbar/dialog introduced at any of the 7 sites by reading each diff hunk.
- **Milestone/scope creep:** File Scope held exactly (verified above); no drive-by fix of the adjacent `FeedItemlistFragment.java:308` `runOnceOrAsk` crash bug (confirmed line 308 sits outside every diff hunk, per D11/Open Question 2's deliberate exclusion).
- **`concurrency`/`compose`/`navigation`/`di`/`gradle-kts` tracks:** not applicable — standalone repo hygiene, no track requested.
- **Coverage gaps:** D5's honest-wording disposition holds — the one JVM-testable guard has a real, assertion-bearing test; the six untestable guards are verified by direct source reading in this review, matching D4's exact shape with no substitute-Context escape hatch.

This is a single MAJOR, cheaply fixable, factual-accuracy defect in the artifact this task itself has repeatedly (and correctly) insisted must be precise — everything else, including the two claims this review was specifically asked to scrutinize hardest (the `EditUrlSettingsDialog` race closure and the "unconditional and permanent" suppression claim), held up under independent structural and empirical re-verification.

## Red-Team Verdict — Implementation
_By: legacy-android-red-team | 2026-08-12 | Loop 2 of max 2 (final)_

### Verdict
APPROVE

### Concerns
None. (The Loop 1 MAJOR finding is fixed; no new issue found.)

### Verification performed (Loop 2, independent of the developer's report)

1. **Read `config/spotbugs/exclude.xml` directly, in full, not from the diff or the developer's addendum.** The `runOnce(Context, Feed, boolean)` `<Match>` entry's preceding comment now reads: *"context: reachable before this diff via **five** unguarded `getContext()`/`WeakReference` call sites; made unreachable by the null checks added in this commit at `FeedItemlistFragment.java:178,316,540`, `FeedSettingsPreferenceFragment.java:208` and `EditUrlSettingsDialog.java:51`."* Counted the citations myself: `178`, `316`, `540`, `208`, `51` — five, matching "five" exactly. Compared word-for-word against Loop 1's verbatim quote of the pre-fix text: the only difference anywhere in the sentence is `six` → `five`; every citation, every file name, the trailing two-arg/three-arg delegation note, and the rest of the class-block preamble are byte-identical to what Loop 1 already read and approved of independently (the `restartUpdateAlarm`, `downloadNow`, `download`, `cancelAll`, `getNumberOfActiveDownloads`, and `cancel` entries' comments are unchanged from Loop 1's review, confirmed by re-reading all seven `<Match>` entries in the file, not just the one that changed).
2. **`git diff origin/develop -- config/spotbugs/exclude.xml`** shows the full task diff (7 new `<Match>` blocks + 2 preamble comments added, nothing else touched relative to `develop`) with the `runOnce` comment already containing "five" — consistent with a single-word fix on top of the state Loop 1 reviewed. **`git diff --name-status origin/develop`** lists the identical 9 tracked files (5 `:app` production files, `config/spotbugs/exclude.xml`, 2 READMEs, `tasks/*.md`, `features/*.checkpoint.md`) that Loop 1's own File Scope check enumerated, plus the same 1 untracked test file and the 2 untracked, unrelated `antennapod-fix-app-wearos-compile-error` spec files Loop 1 already noted as out-of-scope and untouched. No file appears in this diff that Loop 1 did not already see. The developer's claim — comment-only change, no other file touched — holds under independent re-derivation, not just by trusting the addendum's prose.
3. **Ran `./gradlew :net:download:service:spotbugsPlayDebug --rerun` myself:** `BUILD SUCCESSFUL`; `grep -c "<BugInstance" net/download/service/build/reports/spotbugs/playDebug.xml` → **0** (baseline 9, matching every prior report in this task's history). A comment-only edit inside an XML `<!-- -->` block cannot alter SpotBugs's bytecode analysis, and the empirical result confirms it didn't.
4. **Confirmed the prior deep-verification findings are unaffected by this edit, explicitly, for the record:** the guard-removal test (temporarily deleting `CancelDownloadActionButton.java`'s `media == null` guard and re-running `spotbugsPlayDebug` to prove the suppression doesn't reappear), the line-by-line guard verification against Research's reachable-path table, and the `cancel` entry's dual-justification XML-scoping analysis all concern either (a) production code in `:app`/`net/download/service/src`, none of which changed between Loop 1 and Loop 2 (confirmed by the identical file list in point 2), or (b) other `<Match>` entries' comments, which are textually unchanged (confirmed by point 1's full re-read). A one-word edit inside the `runOnce` comment has no path by which it could invalidate a claim about `CancelDownloadActionButton.java`'s guard, the `cancel` entry's parameter-level scoping, or any other entry's citation accuracy — those are independent artifacts, verified independently, and remain independently true. Stating this explicitly rather than silently relying on it.

### Notes on categories considered and dismissed
Unchanged from Loop 1 — this loop's only change is a one-word documentation fix in an already-reviewed suppression comment, so Loop 1's full checklist pass (public API breakage: none; silent behavior changes: the disclosed guard-and-skip change only, no user-visible string; milestone/scope creep: File Scope held; track-specific categories: not applicable, standalone repo hygiene; coverage gaps: D5's honest-wording disposition holds) is re-affirmed by the identical-file-list check in point 2 above rather than re-derived from scratch, per this loop's scope.

This is the final loop. Both the Plan (Loop 2 APPROVE) and the Implementation (this Loop 2 APPROVE) have now cleared red-team review. The task is ready for a PR: the diff stays inside File Scope, all 9 SpotBugs findings are suppressed with comments that are now internally consistent, the empirically-verified "unconditional and permanent" framing holds at the build-topology level, and the one JVM-testable guard has a real assertion-bearing regression test. No further automated loop is available or needed.
