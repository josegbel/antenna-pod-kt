# antennapod-fix-feeditemlistfragmenttest-flaky-characterization-tests

> **Description:** Fix two deterministic failures in `app/src/androidTest/.../ui/FeedItemlistFragmentTest.java`, the characterization test file added by `antennapod-fix-feeditemlistfragment-null-feed-crash` (merged, PR #29). Test-file-only — no production code involved.
> **Repo:** `services/android-migration/projects/portfolio/antennapod`
> **Created:** 2026-08-18

> **Pre-research context (carried over from the parent investigation conversation — do not re-derive):**
> - **Why this exists.** José merged PR #29 (`fix/feeditemlistfragment-null-feed-crash`) before its own CI-verification step (Plan Step 3, D2's bar: 3 full `emulator-test` runs, ≥9 clean attempt-legs) finished. The one CI run that did complete on that PR (workflow run `32126007968`) failed all three `Emulator Test` jobs (API 23/30/36), each retried 3× by Gradle, always failing the same way. `develop` is now red on `emulator-test` as of merge commit `a6275e8f0`.
> - **The production fix itself is not implicated.** Across all 3 API levels × 3 in-job retries (9 completed attempt-legs), raw job logs were grepped for the crash signature `FeedItemlistFragment.lambda$loadItems` — **zero occurrences**. The disposed-subscriber undeliverable-NPE defect that PR #29 exists to close did not reproduce. This task is pure test-authoring cleanup, not a re-open of that fix.
> - **Failure 1 — `testMissingFeedShowsEmptyStateWithoutCrashing`.** `activityRule.launchActivity(intent)` itself throws: `java.lang.RuntimeException: Could not launch intent ... within 45000 milliseconds. Perhaps the main thread has not gone idle...` — Espresso's activity-launch synchronization never settles. A plausible mechanism was spotted but **not yet verified as the actual root cause**: neither the new `onComplete` handler (fires when `DBReader.getFeed` returns null) nor the pre-existing `error` consumer it mirrors ever calls `viewBinding.progressBar.setVisibility(View.GONE)` (confirmed absent in both `git show 8443366a4:.../FeedItemlistFragment.java` — pre-fix — and the current file) — only the success path at `:678` does. If the indeterminate spinner keeps animating, that could be what prevents Espresso's idle detection from completing. **This gap pre-dates PR #29 and is not a regression from it** — the `error` consumer had the identical omission before this diff existed; PR #29's `onComplete` branch faithfully mirrors it per its own Plan (D1, "onComplete handler mirroring the existing error consumer minus `Log.e`"). Confirm this theory (or find the real cause) before deciding how to fix the test — do not assume without checking.
> - **Failure 2 — `testExistingFeedLoadsItems`.** `androidx.test.espresso.AmbiguousViewMatcherException`: the matcher for `withText(feed.getTitle())` (`"@@Feed title@@"`) matches 2 views in the hierarchy. Straightforward over-broad matcher — likely the fragment's own `txtvTitle` plus something else (toolbar title, nav-drawer subscription entry) also rendering the same text. Needs a narrower matcher (e.g. `allOf(withId(R.id.txtvTitle), withText(...))`) or a scoped `onView` — verify which other view collides before picking the fix.
> - **Scope discipline.** Per `AGENTS.md`'s minimal-diff rule and this task's own File Scope: touch only `FeedItemlistFragmentTest.java`. Do **not** add `progressBar.setVisibility(View.GONE)` to `FeedItemlistFragment.java`'s `error`/`onComplete` handlers unless Research/Plan concludes the test genuinely cannot be made reliable without it — that would be a production-code change to a pre-existing, out-of-scope gap and needs its own justification, not a default. If that turns out to be necessary, flag it as an Open Question for José rather than silently expanding File Scope.
> - **Milestone linkage unchanged.** Still blocks Milestone 15b (`tasks/antennapod-sync-settings-before-screenshot-milestone-15b.md`, PR #22) — a reliably green `emulator-test` run needs this fixed on top of PR #29's already-merged production fix.
> - This is unaffiliated OSS portfolio case-study work — see `services/android-migration/projects/portfolio/README.md`.

## Research
_Last updated by: legacy-android-researcher | 2026-08-18_

### Summary

The target is `app/src/androidTest/java/de/test/antennapod/ui/FeedItemlistFragmentTest.java` — 107 lines, 2 `@Test` methods, 2 private Hamcrest matcher helpers, no `@Before`/`@After`, added in its entirety by `675d2a0fe` (PR #29) on 2026-08-18. It is the only test of any kind, unit or instrumented, that references `FeedItemlistFragment`. Both tests launch `MainActivity` with `MainActivityStarter.EXTRA_FEED_ID` via a manually-triggered `IntentsTestRule<>(MainActivity.class, false, false)` and then assert on views. No track (`kotlin`, `gradle-kts`, `di`, `concurrency`, `compose`, `navigation`) applies to this file directly — it is Java in `:app`'s androidTest source set, and this task is test-authoring repair on the characterization surface the `kotlin`-track pipeline depends on.

**The pre-research context is materially incomplete, and correcting it is this research's most important output.** It describes two failures. There are in fact **three distinct defects**, and the two it names are each **API-level-specific**, not universal. Reading all three `Emulator Test` job logs from run `32126007968` rather than one: on **API 30 and 36**, `testMissingFeedShowsEmptyStateWithoutCrashing` does **not** hit the 45-second launch timeout at all — it launches fine and fails at line 56 with a **previously unreported** `AmbiguousViewMatcherException` where `withId(R.id.txtvTitle)` matches **12 views**. The 45s launch timeout is **API 23 only**. And Failure 2's collision count is 2 on API 23 but **3** on API 30/36. All three defects reproduce on every retry attempt on every API level that exhibits them — nothing here is flaky in the "sometimes passes" sense; the task slug's word "flaky" is a misnomer inherited from the parent investigation. On the substance of the two questions asked: the progressBar theory for Failure 1 is **confirmed, including observationally** (the leaked spinner is visible in a CI-captured view hierarchy), with the API-23-only scoping explained by a platform change at API 25; and the colliding views for Failure 2 are **identified by name from the CI artifact's full hierarchy dump** — the nav-drawer subscription row and the episode row's cover-placeholder label, neither of which is the toolbar.

### Findings

#### Existing surface

`FeedItemlistFragmentTest` (`app/src/androidTest/java/de/test/antennapod/ui/FeedItemlistFragmentTest.java`):
- `@RunWith(AndroidJUnit4.class)`, single `@Rule` `IntentsTestRule<MainActivity>(MainActivity.class, false, false)` at `:40-41` — no auto-launch, each test launches explicitly.
- **No `@Before`, no `@After`.** Both tests inline `EspressoTestUtils.clearPreferences()` + `clearDatabase()` as their first two statements (`:45-46`, `:61-62`). There is no teardown of any kind. Every sibling UI test in this package has both an `@Before` and (except `QueueFragmentTest`/`DownloadLogTest`) an `@After` — see "Comparison against sibling tests" below.
- `testMissingFeedShowsEmptyStateWithoutCrashing()` (`:43-57`): launches with a hard-coded `EXTRA_FEED_ID` of `42L` against a just-wiped database, waits for `R.id.recyclerView` (`:52`), `Thread.sleep(1000)` (`:53`), then asserts the recyclerView is displayed with 0 children (`:55`) and that `onView(withId(R.id.txtvTitle))` has text `""` (`:56`).
- `testExistingFeedLoadsItems()` (`:59-78`): builds a `Feed` titled `"@@Feed title@@"` with one `FeedItem`, writes it via `FeedDatabaseWriter.updateFeed`, launches with that feed's real id, calls `waitForViewGlobally(withText(feed.getTitle()), 2000)` (`:76`), then asserts the recyclerView has ≥1 child (`:77`).
- `hasChildCount(int)` (`:80-92`) and `hasChildCountAtLeast(int)` (`:94-106`): local `TypeSafeMatcher<View>` helpers checking `RecyclerView.getChildCount()`. Neither is implicated in any failure.

Production code under test, `app/src/main/java/de/danoeh/antennapod/ui/screen/feed/FeedItemlistFragment.java` (790 lines):
- `onCreateView` sets `viewBinding.progressBar.setVisibility(View.VISIBLE)` unconditionally at `:161`, then calls `loadItems()` at `:197`.
- `loadItems()` at `:652-699` — `Maybe.fromCallable{...}.subscribeOn(Schedulers.computation()).observeOn(AndroidSchedulers.mainThread()).subscribe(onSuccess, onError, onComplete)`. Three terminal handlers: success `:668-684`, error `:685-691`, complete `:692-698`.
- `refreshHeaderView()` at `:496-550` early-returns with a log line when `feed == null` (`:497-500`), so `viewBinding.header.txtvTitle.setText(feed.getTitle())` at `:514` never runs on the missing-feed path — the header title keeps its layout default of empty.

#### Java/Kotlin interop boundary

Not a meaningful axis for this task, and I am recording that explicitly rather than padding it. `FeedItemlistFragmentTest.java` is Java calling Java (`FeedItemlistFragment`, `MainActivity`, `EspressoTestUtils`, `FeedDatabaseWriter`) and Kotlin (`de.danoeh.antennapod.model.feed.Feed`, `FeedItem` — both migrated in `:model` Milestone 6). Both Kotlin call sites are already correct at runtime:
- `new Feed(0, "last modified", "@@Feed title@@", ...)` at `:65-67` binds to a `Feed` constructor overload whose parameters the test satisfies with non-null literals; it has executed successfully on all three API levels in this run (the feed is written, read back, and rendered — proven by its title appearing three times in the API 36 hierarchy dump).
- `new FeedItem(0, "title", "identifier", "link", new Date(), FeedItem.UNPLAYED, feed)` at `:68` likewise executes; the item renders in the recyclerView.

No `Intrinsics.checkNotNullParameter` failure appears anywhere in the three job logs for this class. **This is not a repeat of the `HttpDownloaderTest` stale-Java-caller pattern** — the interop boundary is sound and no nullability contract is implicated in any of the three defects.

The one interop-adjacent fact worth carrying forward: `feed = FeedDatabaseWriter.updateFeed(context, feed, false)` at `:70` reassigns `feed` from the return value before `feed.getId()` is read at `:73`, which is the correct idiom (the pre-write object has id 0). The test does this right.

#### Current test coverage

The safety net here is thin and must not be overstated:

- **`FeedItemlistFragmentTest` is the only test in the repository that references `FeedItemlistFragment`** — verified by `grep -rln "FeedItemlistFragment" app/src/test/ app/src/androidTest/`, which returns that one file. There is no JVM unit test, no Robolectric test, and no other instrumented test for this fragment.
- Both of its tests are **currently red on all three API levels, on every retry attempt**. So the effective coverage of `FeedItemlistFragment.loadItems()` today is **zero**, in both the missing-feed and existing-feed directions.
- What the tests *would* assert if they ran: `testMissingFeedShowsEmptyStateWithoutCrashing` covers PR #29's actual defect (missing feed must not crash, empty state must render) — this is the characterization test the parent task exists to provide. `testExistingFeedLoadsItems` covers the unchanged happy path. Neither has ever reached a green state in CI.
- Partial credit is real and should be recorded: on API 30/36, `testMissingFeed...` **does** get past `:52` (recyclerView appears) and `:55` (recyclerView displayed with exactly 0 children) — both assertions pass — and dies only at `:56`. The stack trace in the API 36 report confirms the failure site is `FeedItemlistFragmentTest.java:56`. So the empty-state behavior PR #29 fixed is, on those two API levels, already demonstrated; it is the *title* assertion that is unwritable as currently phrased. On API 23 nothing is demonstrated, because the activity never launches.
- Adjacent coverage that touches the same screen and currently passes: `FeedSettingsTest.testClickFeedSettings` and `TextOnlyFeedsTest.testMarkAsPlayedList` both drive `FeedItemlistFragment` to render a real feed and both pass on all three API levels in this run. They cover the happy path incidentally; neither covers the missing-feed path.

#### Characterization-test gaps

- **The missing-feed path has no working characterization test on any API level.** This is the single most important gap: PR #29's production fix is currently unverified by any executing test. It was merged on the strength of a test that has never passed.
- **No test covers the `error` branch of `loadItems()` (`:685-691`).** `testMissingFeedShowsEmptyStateWithoutCrashing` exercises `onComplete` (`:692-698`); nothing exercises `onError`. Since both branches share the progressBar omission documented below, a test that pinned `onError` would pin the same gap.
- **No test asserts the progressBar's terminal state on any path.** The leaked spinner documented below was found by reading a CI-captured hierarchy dump, not by any assertion. If the planner's fix narrows the matchers without addressing the spinner, nothing in the suite will notice the spinner is still leaking on the missing-feed and error paths — it will simply stop being fatal on API 23.
- **No test covers `loadMoreItems()` (`:701-731`)** or the paging path. Out of scope, recorded for completeness.

#### Failure 1 — 45s launch timeout (API 23 only). Theory CONFIRMED, with an important correction to its stated scope.

**(a) The progressBar omission is accurate. Verified by reading the current file directly.**

`loadItems()` sets `viewBinding.progressBar.setVisibility(View.GONE)` at exactly one place — `:678`, inside the success handler. Neither the `error` consumer (`:685-691`) nor the `onComplete` handler (`:692-698`) touches it. Both do the same four things (`feed = null`, `refreshHeaderView()`, `adapter.setDummyViews(0)`, `adapter.updateItems(emptyList)`, `updateToolbar()`), and `onComplete` differs from `error` only by omitting `Log.e` — exactly as PR #29's own Plan (D1) said it would.

**The gap pre-dates PR #29. Confirmed against `git show 8443366a4:app/src/main/java/de/danoeh/antennapod/ui/screen/feed/FeedItemlistFragment.java`.** In that pre-PR-29 version, `loadItems()` used `Observable.fromCallable` with only two handlers; the success handler set `progressBar` GONE, and the `error` consumer had the identical omission. So the spinner already leaked on the error path before this diff existed. **This is not a regression from PR #29** — the confirmation the pre-research asked for holds.

**(b) The mechanism is real, and it is directly confirmed by the CI evidence rather than assumed.**

Three independent pieces of evidence, in increasing order of strength:

1. *The runtime's own diagnostic names it.* The API 23 message, thrown from `androidx.test.runner.MonitoringInstrumentation.startActivitySync(MonitoringInstrumentation.java:550)`, reads in full: `"...Perhaps the main thread has not gone idle within a reasonable amount of time? There could be an animation or something constantly repainting the screen. Or the activity is doing network calls on creation? ... the last time the event queue was idle before your activity launch request was 1787049292837 and now the last time the queue went idle was: 1787049292843."` Those two timestamps are `System.currentTimeMillis()` values **6 ms apart**, and the exception was logged 45 seconds later. The queue therefore went idle once at the very start of the launch window and then **never again for the remaining ~45 seconds**. This is a stronger reading than the message's own crude "if these numbers are the same" heuristic suggests, and it establishes that the main thread genuinely stopped idling — the failure is not a missed-`RESUMED` or `singleTask`-onNewIntent artifact.

2. *The mechanism is independently documented.* An indeterminate `ProgressBar` hanging Espresso is a well-known, written-up Espresso failure mode, not an inference of mine — see [Jason Fry, "Android Espresso Test Hangs With Indeterminate ProgressBar"](https://jasonfry.co.uk/blog/android-espresso-test-hangs-with-indeterminate-progressbar/) and [baukunst, "Test your UI on Android with Espresso – Damn you, ProgressBar!"](https://blog.baukunst.io/2015/05/test-your-ui-on-android-with-espresso-damn-you-progressbar/). Espresso's idle synchronization is main-`Looper`-based; a view that re-posts an invalidation every frame keeps the queue permanently non-empty.

3. *The leaked spinner is directly observable in CI output.* The `test-report` artifact (`9321017518`) contains full view-hierarchy dumps for both tests on API 36. In `testExistingFeedLoadsItems` the fragment's spinner is `ProgressBar{id=2131362668, res-name=progressBar, visibility=GONE, width=0, height=0, ...}`. In `testMissingFeedShowsEmptyStateWithoutCrashing` the *same* view is `ProgressBar{id=2131362668, res-name=progressBar, visibility=VISIBLE, width=48, height=48, ...}`. **This is not a theory about what the code would do — it is the leak, captured.** It is also the only element that differs in visibility between the two dumps in a way that could repaint: `more_content`, `floatingSelectMenu`, `txtvFailure`, and `butSubscribe` are all `GONE` in both.

The layout confirms the spinner is the indeterminate kind: `app/src/main/res/layout/feed_item_list_fragment.xml:65-71` declares a bare `<ProgressBar>` with `android:indeterminateOnly="true"` and `android:visibility="gone"`, no `style` attribute — so it resolves to the platform default circular indeterminate widget, whose indeterminate drawable is an `AnimatedVectorDrawable` on API 21+.

**Correction the pre-research did not have: this failure is API 23 only, and that scoping is itself explained by the mechanism.** On API 30 and 36 the spinner is equally VISIBLE and leaking (proven by the dump above), yet `launchActivity` returns normally. The difference is a platform change at **API 25**: from that level onward `AnimatedVectorDrawable` runs its animator on the **RenderThread** (`VectorDrawableAnimatorRT`) instead of the UI thread, so the animation no longer posts per-frame work to the main `Looper` and no longer defeats Espresso's idle detection ([Microsoft Learn, `AnimatedVectorDrawable`](https://learn.microsoft.com/en-us/dotnet/api/android.graphics.drawables.animatedvectordrawable?view=net-android-35.0)). API 23 is below that cutoff and still animates on the UI thread. This accounts for the observed matrix exactly: API 23 hangs, API 30/36 do not.

Note also that `checks.yml:155` sets `disable-animations: true` on the emulator, and it evidently does **not** neutralize this — the hang reproduces on API 23 on both completed attempts.

**(c) Alternatives considered and ruled out.** I checked three other explanations before settling:
- *`singleTask` launch mode.* `app/src/main/AndroidManifest.xml:98-101` declares `MainActivity` as `android:launchMode="singleTask"`, which in principle can make `startActivitySync` wait forever for a never-created Activity when a live instance already exists in the shared instrumentation process. Ruled out as the cause here by the idle timestamps in (1): a `singleTask`/`onNewIntent` miss would leave the queue idling normally, and it does not. Recorded anyway as a latent hazard the planner should be aware of, since this test class leaves an Activity resumed with no `@After`.
- *Glide image loading repainting the header.* Ruled out: `loadFeedImage()` is only reachable from `refreshHeaderView()`, which early-returns at `:497-500` when `feed == null`. No Glide work happens on the missing-feed path.
- *A crash preventing `RESUMED`.* Ruled out: no `AndroidRuntime` or `FeedItemlistFragment.lambda$loadItems` signature appears in the API 23 log, and the run completed 70 tests with exactly 2 failures — no process crash.

**Confidence:** high on the mechanism (main thread stops idling; the only leaking repainter is the spinner; the leak is observed, not inferred). Moderate-to-high on the API-25 RenderThread account of *why it is API-23-only* — that part is inference from documented platform behavior, not something I could execute here (no emulator or device is attached; `adb devices` is empty and no AVD is running).

#### Failure 2 — `AmbiguousViewMatcherException` on `withText(feed.getTitle())`. Both colliders identified by name.

Not the toolbar. The API 36 `test-report` artifact's hierarchy dump lists all three matches verbatim, and the layout sources confirm each:

| # | View | Layout | Set from | Displayed? |
|---|---|---|---|---|
| 1 | `R.id.txtvTitle`, `RelativeLayout$LayoutParams`, x=72 y=13, 144×22, `is-selected=true` | `nav_listitem.xml:30` | `NavListAdapter.java:239` — `holder.title.setText(item.getTitle())`, the drawer's **subscription row** for this feed | No — its ancestor `FragmentContainerView{res-name=navDrawerFragment}` is `INVISIBLE` |
| 2 | `R.id.txtvTitle`, `LinearLayout$LayoutParams`, x=0 y=0, 148×55 | `feeditemlist_header.xml:136` | `FeedItemlistFragment.java:514` — the fragment's own header title, the intended target | Yes |
| 3 | `R.id.txtvPlaceholder`, `RelativeLayout$LayoutParams`, 56×56, `visibility=VISIBLE` | `feeditemlist_item.xml:62`, inside the episode row's `coverHolder` | `EpisodeItemViewHolder.java:95` — `placeholder.setText(item.getFeed().getTitle())`, the text shown behind the cover art while Glide loads | **Yes** |

The drawer is reachable because `app/src/main/res/layout/main.xml:47-52` keeps `navDrawerFragment` in the hierarchy at all times; `DrawerLayout` renders it `INVISIBLE` when closed rather than removing it, and Espresso's `withText` does not filter on visibility.

**Why the count is 2 on API 23 and 3 on API 30/36:** on API 23 `testMissingFeedShowsEmptyStateWithoutCrashing` burns 45 seconds and never launches, leaving different residual app state for the next test; the episode row (and hence match #3) is not present in that hierarchy. The API 23 log's listed `[1]` is a `txtvTitle` with `LinearLayout$LayoutParams` at x=0 y=0 — i.e. the header, match #2 above.

**Two facts the planner will need about disambiguation, stated as hierarchy observations rather than as a prescribed fix:**
- `isDisplayed()` alone is **not sufficient** for this matcher. It would eliminate #1 (INVISIBLE ancestor) but **not** #3 — `txtvPlaceholder` is `visibility=VISIBLE` at 56×56 and on screen; Espresso's `isDisplayed()` does not care that a cover image is drawn over it.
- Scoping by ancestor **is** sufficient and is the idiom already proven in this repo: #2 is the only one of the three under `R.id.appBar`.

#### Failure 3 — `withId(R.id.txtvTitle)` matches 12 views (API 30 and 36). NOT in the pre-research context.

This is the actual failure of `testMissingFeedShowsEmptyStateWithoutCrashing` on API 30 and 36, and the pre-research does not mention it. `FeedItemlistFragmentTest.java:56` does `onView(withId(R.id.txtvTitle)).check(matches(withText("")))`, and `R.id.txtvTitle` is a heavily reused id across at least eleven layouts in this app (`nav_listitem.xml`, `feeditemlist_item.xml`, `feeditemlist_header.xml`, `feeditem_fragment.xml`, `downloadlog_item.xml`, `ellipsize_start_listitem.xml`, `simplechapter_item.xml`, `external_player_fragment.xml`, plus `:ui:discovery`, `:ui:statistics`, `:ui:widget`).

The exception message names the first five matches: `Home`, `Queue`, `Inbox`, `Subscriptions`, `Episodes` — the nav drawer's own navigation rows, which share `nav_listitem.xml` with the subscription rows (`NavListAdapter.java:138,142` inflate the same layout for both `NavHolder` and `FeedHolder`; both bind via `R.id.txtvTitle` at `:303` and `:317`). Ten of the twelve are inside the INVISIBLE `navDrawerFragment`.

The remaining two both have `text=[]`, which matters for whatever the planner chooses:
- the fragment header's `txtvTitle` under `appBar` → `header` — the intended target;
- a `txtvTitle` under `FragmentContainerView{res-name=playerFragment}`, itself nested in `FragmentContainerView{res-name=audioplayerFragment, visibility=GONE}` — the `ExternalPlayerFragment` mini-player title (`ExternalPlayerFragment.java:64`).

So `allOf(withId(R.id.txtvTitle), withText(""))` would still be ambiguous (2 matches), while adding `isDisplayed()` would resolve it (the mini-player's ancestor is GONE). Again: reported as a hierarchy fact for the planner, not a chosen fix.

This failure is invisible on API 23 only because the test dies earlier, at launch. **Fixing Failure 1 without also fixing Failure 3 would convert the API 23 leg from a timeout into this same ambiguity failure** — the planner must treat them as one test's two sequential defects, not as alternatives.

#### Comparison against sibling tests — what `FeedSettingsTest` does differently

`FeedSettingsTest` is the right comparison and it is instructive on all three counts. It uses the **same** `EXTRA_FEED_ID` launch pattern (`FeedSettingsTest.java:47-49`) with the **same** rule shape (`:35`), and it passes on all three API levels in this run.

1. **It never launches against a missing feed.** `setUp()` (`:37-50`) builds real feed data through `UITestUtils.addLocalFeedData(false)` and launches with `uiTestUtils.hostedFeeds.get(0).getId()`. `loadItems()` therefore always takes the success path at `:668-684`, which sets `progressBar` GONE at `:678` — so the API 23 spinner leak is never triggered. This is a genuine controlled comparison for Failure 1: same launch mechanism, same API level, only the feed's existence differs, and only the missing-feed case hangs.
2. **Its title matcher is ancestor-scoped.** `FeedSettingsTest.java:59-60` uses `waitForView(allOf(isDescendantOfA(withId(R.id.appBar)), withText(feed.getTitle()), isDisplayed()), 1000)` — three conjuncts where `FeedItemlistFragmentTest:76` uses `withText(...)` alone. The `isDescendantOfA(withId(R.id.appBar))` conjunct is precisely what excludes the drawer row and the episode-row placeholder. This idiom is already established in the repo and needed no invention.
3. **It has proper lifecycle hooks.** `@Before setUp()` and `@After tearDown()` calling `uiTestUtils.tearDown()`. `FeedItemlistFragmentTest` has neither.

`TextOnlyFeedsTest` (`:38-52`, `:54-64`) is a second useful precedent and takes a **different route to the same screen**: `EspressoTestUtils.setLaunchScreen("" + feed.getId())` followed by `activityRule.launchActivity(new Intent())`, rather than an `EXTRA_FEED_ID` extra. It also passes. Whether that route sidesteps anything relevant here is not something I can determine without a device, and I am not recommending it — recording it because it is the only other in-repo way to open this fragment from a test.

Also worth noting for the planner: `EspressoTestUtils.waitForViewGlobally` (`EspressoTestUtils.java:102-126`) catches only `NoMatchingViewException` and `AssertionFailedError`. `AmbiguousViewMatcherException` is neither, so it propagates on the **first** poll — the 2000 ms timeout at `:76` never gets a chance to elapse. That is why Failure 2 is instantaneous and perfectly deterministic rather than intermittent.

#### CI evidence base

Run `32126007968` on `fix/feeditemlistfragment-null-feed-crash` (PR #29), 2026-08-18. `Static Code Analysis`, `Gradle Wrapper Validation`, and all three `Unit Test` jobs **passed**; all three `Emulator Test` legs failed. Per-leg, per-attempt results for this test class, read from raw job logs:

| API | Job | Attempts | `testMissingFeed...` | `testExistingFeedLoadsItems` | Total failures / tests |
|---|---|---|---|---|---|
| 23 | 95678219042 | 2 (3rd canceled by matrix fail-fast) | `RuntimeException: Could not launch intent ... within 45000 ms` — both attempts | `AmbiguousViewMatcherException`, **2** views — both attempts | 2 / 70 |
| 30 | 95678219128 | 3 | `AmbiguousViewMatcherException`, `withId(txtvTitle)`, **12** views — all 3 | `AmbiguousViewMatcherException`, **3** views — all 3 | 2 / 67 |
| 36 | 95678219141 | 3 | `AmbiguousViewMatcherException`, `withId(txtvTitle)`, **12** views — all 3 | `AmbiguousViewMatcherException`, **3** views — all 3 | 2 / 71 |

Two observations the planner should carry:
- **These two tests are the only failures in the entire suite on every leg.** `HttpDownloaderTest` is green (its sibling task's fix landed), and `DownloadLogTest` — the ~50%-flaky NPE that motivated PR #29 — **passed on all 8 completed attempts**, consistent with PR #29's production fix working. The 0/9 crash-signature grep recorded in the pre-research is corroborated: fixing this test file is the only thing standing between `develop` and a green `emulator-test`.
- **Nothing here is intermittent.** 8 completed attempts, 8 identical outcomes per API level. Whatever the planner writes, "did the flake stop?" is not the acceptance question — "do these two tests pass on all three API levels?" is, and it is answerable in a single run.

#### Track-specific findings

No migration track is requested for this task and none applies to the file being changed. Recording the adjacent facts as fact, not as a recommendation:

- `concurrency`: `FeedItemlistFragment.loadItems()` uses **RxJava3** (`Maybe.fromCallable(...).subscribeOn(Schedulers.computation()).observeOn(AndroidSchedulers.mainThread())`, `:656-667`), which is `:app`'s existing pattern. PR #29 changed `Observable` → `Maybe` there specifically to get an `onComplete` channel for the null-feed case. No Coroutines migration is proposed, implied, or in scope.
- `compose`: the screen is View/XML with ViewBinding (`viewBinding.*` throughout) and **no ViewModel layer** — `loadItems()` subscribes directly from the fragment and writes to a fragment field. Recorded only because it is a standing blocking prerequisite for any future `compose` track on `:app`; nothing in this task should be read as scoping that work.
- `kotlin`, `gradle-kts`, `di`, `navigation`: not requested, not applicable, not assessed.

#### Track prerequisites

- `kotlin`: not requested. If it were, no prerequisites — but note that converting `FeedItemlistFragmentTest.java` or `FeedItemlistFragment.java` while the only characterization test for the fragment is red would violate the pipeline's own behavioral-equivalence premise. **The characterization surface must be green before any track touches this fragment.**
- `gradle-kts`, `di`, `concurrency`, `navigation`: not requested, no prerequisites assessed.
- `compose`: not requested. Flagging for completeness only: **no ViewModel/MVVM layer exists for this screen**, which is a blocking prerequisite for that track. Introducing one is not in scope here and must not be folded into this task.

### Unknowns

1. **Does the fix belong in the test file alone, or does the progressBar leak need a production fix?** This is the task's stated Scope-discipline question and it is now decidable on evidence, but the decision is the planner's and José's, not mine. What research establishes: the leak is real and observed; it is pre-existing and not a PR #29 regression; it is fatal to Espresso only below API 25; and a test-only change *can* sidestep it (the API 30/36 legs prove the test can reach and pass its assertions with the spinner still leaking, and `FeedSettingsTest` proves the launch pattern works when the success path hides it). So a test-only fix is **feasible**. Whether it is **right** — given that the production gap will keep any future missing-feed instrumented test hanging on API 23, and that AntennaPod still ships an API 23 `Emulator Test` leg — is a judgement call with a real trade-off on both sides. Per the task's own instruction, flagging rather than deciding.
2. **Exactly which matcher shapes disambiguate, on all three API levels?** Research establishes the hierarchy facts (ancestor-scoping to `R.id.appBar` isolates the header; `isDisplayed()` alone does not, because `txtvPlaceholder` is displayed; `withText("")` alone does not, because the mini-player title is also empty). But the API 23 hierarchy could not be captured — only one `test-report` artifact was retained for the run, and it is the API 36 one. A matcher verified against API 30/36 dumps should be re-verified against a real API 23 run rather than assumed.
3. **Does Failure 3 have an API 23 counterpart that only appears once Failure 1 is fixed?** Almost certainly yes — line 56 is the same code on all API levels — but it cannot be observed until the launch timeout is gone. The planner should expect the API 23 leg to reveal Failure 3 next, and should not treat a fix for Failure 1 alone as sufficient.
4. **Should this test class gain `@Before`/`@After` hooks?** Every sibling in the package has them; this one has none, and it leaves a resumed `singleTask` `MainActivity` behind. No evidence that this is currently causing cross-test pollution (the suite is otherwise green, and the two failing tests are the last two of the class), but it is a divergence from a consistent local convention and is exactly the kind of thing that produces genuine flakiness later. Scope decision, not a research finding.
5. **Is the 45s `IdlingPolicy` timeout worth 45s × 2 attempts of CI time on API 23 even after a fix?** Not investigated. Raised only because the API 23 leg currently burns ~90 seconds on this one test.
6. **Not reproduced locally.** Stated plainly: `adb devices` is empty, no AVD is running, and `connectedPlayDebugAndroidTest` requires a live device. Every conclusion above rests on (a) direct source reading of every file in the path, (b) three raw CI job logs across 8 completed attempts, and (c) the CI-captured API 36 view-hierarchy dumps from artifact `9321017518`. The hierarchy-derived claims (Failures 2 and 3) are as close to direct observation as is possible without a device. The API-23-only scoping of Failure 1 is the one link in the chain that is inference from documented platform behavior rather than observation.

### Sources

- `app/src/androidTest/java/de/test/antennapod/ui/FeedItemlistFragmentTest.java:38-41` — `@RunWith(AndroidJUnit4)`, `IntentsTestRule<>(MainActivity.class, false, false)`, no `@Before`/`@After`
- `app/src/androidTest/java/de/test/antennapod/ui/FeedItemlistFragmentTest.java:43-57` — `testMissingFeedShowsEmptyStateWithoutCrashing`; hard-coded `42L` at `:49`, launch at `:50`, failing assertion at `:56`
- `app/src/androidTest/java/de/test/antennapod/ui/FeedItemlistFragmentTest.java:59-78` — `testExistingFeedLoadsItems`; `"@@Feed title@@"` at `:65`, launch at `:73-74`, failing matcher at `:76`
- `app/src/androidTest/java/de/test/antennapod/ui/FeedItemlistFragmentTest.java:80-106` — `hasChildCount` / `hasChildCountAtLeast` helpers
- `git log --diff-filter=A -- app/src/androidTest/.../FeedItemlistFragmentTest.java` → `675d2a0fe` (2026-08-18), "test(app): add characterization test for FeedItemlistFragment.loadItems()" — file added whole by PR #29
- `app/src/main/java/de/danoeh/antennapod/ui/screen/feed/FeedItemlistFragment.java:161` — `progressBar.setVisibility(View.VISIBLE)` in `onCreateView`
- `app/src/main/java/de/danoeh/antennapod/ui/screen/feed/FeedItemlistFragment.java:652-699` — `loadItems()`; success `:668-684` with the only `progressBar.setVisibility(View.GONE)` at `:678`; `error` `:685-691`; `onComplete` `:692-698` — neither hides the spinner
- `app/src/main/java/de/danoeh/antennapod/ui/screen/feed/FeedItemlistFragment.java:496-500` — `refreshHeaderView()` early-returns when `feed == null`, so `:514`'s `txtvTitle.setText(feed.getTitle())` never runs on that path
- `git show 8443366a4:app/src/main/java/de/danoeh/antennapod/ui/screen/feed/FeedItemlistFragment.java` — pre-PR-29 `loadItems()`: `Observable.fromCallable`, two handlers, `progressBar` GONE only in the success handler; identical omission in `error`
- `app/src/main/res/layout/feed_item_list_fragment.xml:65-71` — bare `<ProgressBar>`, `android:indeterminateOnly="true"`, `android:visibility="gone"`, no `style`
- `app/src/main/res/layout/feeditemlist_header.xml:136` — header `txtvTitle` (intended target)
- `app/src/main/res/layout/nav_listitem.xml:30` — drawer row `txtvTitle`
- `app/src/main/res/layout/feeditemlist_item.xml:62` — `txtvPlaceholder`; `:174` — episode-row `txtvTitle`
- `app/src/main/java/de/danoeh/antennapod/ui/episodeslist/EpisodeItemViewHolder.java:95` — `placeholder.setText(item.getFeed().getTitle())`
- `app/src/main/java/de/danoeh/antennapod/ui/screen/drawer/NavListAdapter.java:138,142` — both `NavHolder` and `FeedHolder` inflate `R.layout.nav_listitem`; `:303,:317` — both bind `R.id.txtvTitle`; `:239` — `holder.title.setText(item.getTitle())`
- `app/src/main/java/de/danoeh/antennapod/ui/screen/playback/audio/ExternalPlayerFragment.java:64` — mini-player's `R.id.txtvTitle`
- `app/src/main/res/layout/main.xml:47-52` — `navDrawerFragment` `FragmentContainerView` permanently in the `DrawerLayout` hierarchy
- `app/src/main/AndroidManifest.xml:98-101` — `MainActivity` `android:launchMode="singleTask"`
- `app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java:745-746` — `EXTRA_FEED_ID` handling
- `app/src/androidTest/java/de/test/antennapod/EspressoTestUtils.java:102-126` — `waitForViewGlobally` catches only `NoMatchingViewException` / `AssertionFailedError`
- `app/src/androidTest/java/de/test/antennapod/EspressoTestUtils.java:56-93` — `waitForView`
- `app/src/androidTest/java/de/test/antennapod/ui/FeedSettingsTest.java:35`, `:37-50`, `:52-55`, `:59-60` — same rule/launch pattern, real feed data, `@Before`/`@After`, ancestor-scoped title matcher
- `app/src/androidTest/java/de/test/antennapod/ui/TextOnlyFeedsTest.java:38-52,54-64` — alternative `setLaunchScreen(feedId)` route to the same fragment
- `app/src/androidTest/java/de/test/antennapod/ui/{NavigationDrawerTest,BottomNavigationTest,QueueFragmentTest,DownloadLogTest}.java` — all have `@Before`; all but the last two have `@After`
- `grep -rln "FeedItemlistFragment" app/src/test/ app/src/androidTest/` → only `FeedItemlistFragmentTest.java`
- `.github/workflows/checks.yml:113-120` — API 23 (`default`) / 30 (`aosp_atd`) / 36 (`aosp_atd`) matrix, no `fail-fast: false`; `:147-155` — `reactivecircus/android-emulator-runner@v2` with `disable-animations: true`
- `gh run view 32126007968` — job list; `Static Code Analysis`, wrapper validation, and all 3 `Unit Test` jobs green; all 3 `Emulator Test` legs red
- `gh run view --job 95678219042 --log` (API 23) — `Could not launch intent ... within 45000 milliseconds`, `MonitoringInstrumentation.java:550`, idle timestamps `1787049292837` / `1787049292843`; `withText` ambiguity **2** views; both attempts identical; 2 failures / 70 tests
- `gh run view --job 95678219128 --log` (API 30) — `withId(txtvTitle)` **12** views, `withText` **3** views, all 3 attempts; 2 failures / 67 tests
- `gh run view --job 95678219141 --log` (API 36) — same as API 30; 2 failures / 71 tests
- Artifact `9321017518` (`test-report`, API 36 / `emulator-5554 - 16`), `connected/debug/flavors/play/de.test.antennapod.ui.FeedItemlistFragmentTest.html` — full hierarchy dumps: `progressBar` `visibility=VISIBLE, width=48, height=48` in the missing-feed dump vs `visibility=GONE, width=0, height=0` in the existing-feed dump; the 3 `@@Feed title@@` matches with layout-params/position; `navDrawerFragment` `visibility=INVISIBLE`; `audioplayerFragment` `visibility=GONE`; failure site `FeedItemlistFragmentTest.java:56`
- [Jason Fry — "Android Espresso Test Hangs With Indeterminate ProgressBar"](https://jasonfry.co.uk/blog/android-espresso-test-hangs-with-indeterminate-progressbar/) — documented Espresso/indeterminate-ProgressBar hang
- [baukunst — "Test your UI on Android with Espresso – Damn you, ProgressBar!"](https://blog.baukunst.io/2015/05/test-your-ui-on-android-with-espresso-damn-you-progressbar/) — same failure mode, independent write-up
- [Microsoft Learn — `AnimatedVectorDrawable`](https://learn.microsoft.com/en-us/dotnet/api/android.graphics.drawables.animatedvectordrawable?view=net-android-35.0) — from API 25, `AnimatedVectorDrawable` animates on the RenderThread rather than the UI thread
- `adb devices` — empty; no AVD running; instrumented tests not reproduced locally

## Plan
_Last updated by: legacy-android-planner | 2026-08-18_

> **Scope decision (José, 2026-08-18) — Research Unknown 1 RESOLVED.** Expand File Scope to include a production-code fix: add `viewBinding.progressBar.setVisibility(View.GONE);` to `FeedItemlistFragment.java`'s `error` and `onComplete` handlers (matching what the success path at `:678` already does), rather than working around the leaked spinner test-side only. Reasoning given: this isn't just a test artifact — API 23 (and any pre-API-25) users who hit a missing/deleted feed currently see a permanently spinning progress bar, which is a real UX gap, not merely something blocking Espresso's idle sync. The planner should size this as a small, low-risk, two-line addition (mirrors an existing, already-reviewed pattern in the same method) and fold it into Steps/File Scope/Acceptance Criteria alongside the two test-matcher fixes for defects 2 and 3.

### Objective

Make both tests in `FeedItemlistFragmentTest` pass on all three CI API levels (23, 30, 36) by fixing the three distinct, deterministic defects Research identified: two over-broad Espresso matchers (test-side) and one leaked indeterminate `ProgressBar` on `FeedItemlistFragment.loadItems()`'s non-success paths (production-side, per José's scope decision above). Outcome: `develop` returns to a green `emulator-test`, and PR #29's null-feed production fix gains the executing characterization test it was merged without.

No migration track (`kotlin`, `gradle-kts`, `di`, `concurrency`, `compose`, `navigation`) is requested or applies. This is characterization-surface repair that the `kotlin`-track pipeline depends on.

### Resolved Decisions

**D1 — Production fix is in scope (Research Unknown 1).** Resolved by José, recorded above; not re-litigated here. Sized as planned: two statements, mirroring the success path's existing `:678` line, in a method that already passed two red-team loops in the parent task. The rest of `loadItems()` is not reopened.

**D2 — Defect 3 matcher (`FeedItemlistFragmentTest.java:56`): scope by ancestor, and deliberately *without* `isDisplayed()`.**
Replace `onView(withId(R.id.txtvTitle))` with `onView(allOf(isDescendantOfA(withId(R.id.appBar)), withId(R.id.txtvTitle)))`.
Verified structurally rather than by hierarchy dump: `android:id="@+id/appBar"` is declared in exactly two layouts in the whole repo — `feed_item_list_fragment.xml:11` and `feedinfo.xml:11` — and `feedinfo.xml` belongs to `FeedInfoFragment`, a different screen never on-screen concurrently with these tests. Within `feed_item_list_fragment.xml`'s `appBar` there is exactly one `txtvTitle` (via the `header` include at `:32-34` → `feeditemlist_header.xml:136`, the only `txtvTitle` in that file, which contains no further includes). So the matcher resolves to exactly 1 view. All ten nav-drawer rows, the episode-row placeholder, and the mini-player title are outside any `appBar`.
`isDisplayed()` is deliberately omitted here. It is unnecessary — containment alone is already unique — and it is the one conjunct that could misbehave on this specific path, because the assertion under test is `withText("")` and an empty `TextView`'s measured bounds are the thing most likely to vary across API levels and font settings. Adding a conjunct that can only turn a correct assertion into a `NoMatchingViewException` buys nothing.

**D3 — Defect 2 matcher (`FeedItemlistFragmentTest.java:76`): scope by ancestor, keep `waitForViewGlobally`.**
Replace `waitForViewGlobally(withText(feed.getTitle()), 2000)` with `waitForViewGlobally(allOf(isDescendantOfA(withId(R.id.appBar)), withText(feed.getTitle()), isDisplayed()), 2000)` — the exact conjunct set `FeedSettingsTest.java:59-60` already uses and that passes on all three API levels.
Two sub-decisions worth stating, because both are non-obvious:
- *Keep `waitForViewGlobally`; do not switch to `onView(isRoot()).perform(waitForView(...))`.* The smaller diff is the tiebreaker (AGENTS.md minimal-diff), but the substantive reason is failure behavior. `EspressoTestUtils.waitForView` (`:74-80`) returns on the **first** matching child in a breadth-first traversal — if a scoped matcher ever matched more than one view, it would silently pick one and pass, potentially asserting against the wrong view. `waitForViewGlobally` routes through `onView`, which throws `AmbiguousViewMatcherException` loudly. For a characterization test whose entire purpose is verification integrity, loud ambiguity failure is strictly the better property than a possible silent false green. This also keeps the import set unchanged apart from one addition.
- *Here `isDisplayed()` is kept*, unlike D2: the text is non-empty, `FeedSettingsTest` proves the three-conjunct form works on all three API levels against this same header, and `waitForViewGlobally` already asserts `isDisplayed()` internally at `EspressoTestUtils.java:108` regardless.

**D4 — Research Unknown 2 (matcher shapes verified only against API 36 dumps): no extra pre-ship verification step is required, and here is why that is not hand-waving.**
The concern is legitimate in general but does not bite for the matchers chosen, because D2 and D3 do not depend on any runtime property that varies by API level. They depend on **static layout containment** — which view is nested inside which — and that is identical across API 23/30/36: there are no `layout-v*` resource qualifiers anywhere in this repo (the only layout qualifier that exists at all is `app/src/main/res/layout-sw720dp/`, a screen-size bucket, and it contains only `main.xml`, which declares no `appBar`). The API-level variation Research documented is entirely in *visibility and presence* state (drawer `INVISIBLE` vs. laid out, episode row present or not, mini-player `GONE`) — precisely the axis ancestor-scoping does not consult.
This is also why ancestor-scoping was chosen over the alternatives Research surfaced: `isDisplayed()`-only and `withText("")`-only both hinge on exactly that varying runtime state, and Research showed each of them insufficient. Note as corroboration that the scoped matchers would also survive the `layout-sw720dp` tablet case, where the nav drawer is permanently visible — a configuration that would break an `isDisplayed()`-based fix.
Residual risk, stated plainly rather than dismissed: `FeedItemlistFragmentTest.java:56` has **never once executed on API 23** (defect 1 kills the test before it gets there), so Step 3's CI run is the first-ever execution of that line on that API level. That is a reason the CI-verification step must cover API 23 specifically — it is not a reason to add a separate pre-ship verification step, because no such step could produce evidence that Step 3 will not produce more directly.

**D5 — Research Unknown 3 (does defect 3 have an API 23 counterpart?): assume yes, and it is already handled.** Line 56 is the same code on every API level; it is currently masked on API 23 only because defect 1 aborts the test first. The Step 1 matcher fix applies to all API levels at once, so fixing defect 1 in Step 2 cannot expose an unfixed defect 3. This is the specific trap the checkpoint flagged, and the step ordering below neutralizes it.

**D6 — The new `progressBar` assertion needs scoping too, and `withId` alone would reintroduce exactly the bug being fixed.** `android:id="@+id/progressBar"` appears in **14** layouts, including `nav_list.xml:77` (inside the always-present nav drawer) and `feeditemlist_item.xml:199` (every episode row). The correct anchor is `withParent`, not `isDescendantOfA`: the fragment's spinner is a *direct child* of `coordinatorLayout` (`feed_item_list_fragment.xml:5,65`), whereas the episode-row spinners are descendants of it via `swipeRefresh` → `recyclerView`. `R.id.coordinatorLayout` is declared in exactly one layout in the repo, and `nav_list.xml`'s spinner's parent is `nav_layout`, not a `coordinatorLayout`. So `allOf(withId(R.id.progressBar), withParent(withId(R.id.coordinatorLayout)))` is uniquely the fragment's spinner. `isDescendantOfA` would happen to work on the missing-feed path today (0 episode rows) but would silently become ambiguous the moment anyone reuses the assertion on a populated path — `withParent` is correct regardless.

**D7 — On the mandatory "characterization tests first" rule.** Research flagged characterization-test gaps, so the rule applies and is **not** waived. It is satisfied in an inverted form worth making explicit for the reviewer and red-team: the characterization tests for the affected paths already exist but **cannot execute**, so "write/extend characterization tests pinning current behavior" here means *repairing them until they execute and pin current behavior* — which is Step 1, and Step 1 precedes every production-code change. Step 1 is test-file-only by construction.
One honesty note the standard before/after phrasing cannot express: for the production change in Step 2 there is no "pin current behavior, prove equivalence" framing available, because current behavior on that path **is the defect** (a spinner that never stops). Step 2 is a deliberate, user-visible behavior *change*, and the new assertion in D6 is a regression test for the new behavior, not a characterization test of the old. Step 1's CI evidence (API 23 still failing at launch with both matchers already fixed) is what serves as the "red before" demonstration.

### Steps

Ordered so each step leaves the build green and is committable on its own. Step 1 is test-only and precedes all production code, per D7.

1. **Fix both over-broad matchers in `FeedItemlistFragmentTest.java` (test-file only, defects 2 and 3).**
   - `:56` → `onView(allOf(isDescendantOfA(withId(R.id.appBar)), withId(R.id.txtvTitle))).check(matches(withText("")));` (per D2)
   - `:76` → `waitForViewGlobally(allOf(isDescendantOfA(withId(R.id.appBar)), withText(feed.getTitle()), isDisplayed()), 2000);` (per D3)
   - Add the single import `androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA`. `allOf`, `isDisplayed`, `withId`, `withText`, `waitForViewGlobally` are all already imported; no import becomes unused, so no import removals.
   - Tests updated: `testMissingFeedShowsEmptyStateWithoutCrashing`, `testExistingFeedLoadsItems`. No new test methods, no changes to `hasChildCount`/`hasChildCountAtLeast`.
   - Expected state after this step: API 30 and 36 **fully green** for both tests. API 23 still fails `testMissingFeedShowsEmptyStateWithoutCrashing` at `launchActivity` (defect 1 untouched) and now passes `testExistingFeedLoadsItems`. That partial-green API 23 result is the intended, informative outcome — it is the evidence that defect 1 is a genuinely separate production defect and not a matcher problem.

2. **Add the missing `progressBar` hide to `FeedItemlistFragment.loadItems()`'s two non-success handlers, and pin it with an assertion (defect 1).**
   - `FeedItemlistFragment.java`: insert `viewBinding.progressBar.setVisibility(View.GONE);` into the `error` consumer (`:685-691`) and the `onComplete` handler (`:692-698`), in each case immediately after the existing `refreshHeaderView();` call and before `adapter.setDummyViews(0);` — the exact position the success handler already uses at `:677-679`. Two added lines total, no other edits to the method, no reordering of existing statements. `android.view.View` is already imported.
   - `FeedItemlistFragmentTest.java`: append to `testMissingFeedShowsEmptyStateWithoutCrashing`, as its final assertion, `onView(allOf(withId(R.id.progressBar), withParent(withId(R.id.coordinatorLayout)))).check(matches(not(isDisplayed())));` (per D6). Add imports `androidx.test.espresso.matcher.ViewMatchers.withParent` and `org.hamcrest.Matchers.not`.
   - Test updated: `testMissingFeedShowsEmptyStateWithoutCrashing`. This closes Research's "no test asserts the progressBar's terminal state on any path" gap for the missing-feed path.
   - The `error` branch gets the same fix but remains unasserted — no test drives `onError` today, and adding one is out of scope (see Out of Scope). It is reviewable by inspection as a two-line mirror of the success path.

3. **Verify in CI, on the branch, before any merge is proposed. This step is mandatory and gating.**
   - Push and run the `emulator-test` workflow to completion **three times**, per the parent task's D2 bar. All three API legs (23, 30, 36) must complete in each run — a leg cancelled by the matrix's `fail-fast` (`.github/workflows/checks.yml:113-120` does not set `fail-fast: false`) means that run does not count toward the three.
   - Record the run IDs and per-leg outcomes in the checkpoint file, the way Research recorded run `32126007968`.
   - **Do not open the merge, and do not recommend merging, until this step has completed and is recorded.** This is a process safeguard, not a formality: merging PR #29 before exactly this step finished is the sole reason this follow-up task exists.

### File Scope

The developer may modify only these two files. Any diff touching anything else is out of scope and the reviewer should reject it.

- `app/src/androidTest/java/de/test/antennapod/ui/FeedItemlistFragmentTest.java` — matcher fixes at `:56` and `:76`, one appended assertion in `testMissingFeedShowsEmptyStateWithoutCrashing`, three added imports (`isDescendantOfA`, `withParent`, `not`). No changes to the `@Rule`, to `testExistingFeedLoadsItems`'s setup/data, to the `Thread.sleep(1000)` at `:53`, or to either private matcher helper.
- `app/src/main/java/de/danoeh/antennapod/ui/screen/feed/FeedItemlistFragment.java` — exactly two added `viewBinding.progressBar.setVisibility(View.GONE);` statements, one in the `error` consumer and one in the `onComplete` handler of `loadItems()`. Nothing else in this file, and nothing else in `loadItems()`.

No new files. No changes to layouts, to `EspressoTestUtils`, to `checks.yml`, or to any other test class.

### Acceptance Criteria

Defect closure — the primary bar:
- [ ] `FeedItemlistFragmentTest.testMissingFeedShowsEmptyStateWithoutCrashing` passes on API 23, 30, and 36 (closes defects 1 and 3).
- [ ] `FeedItemlistFragmentTest.testExistingFeedLoadsItems` passes on API 23, 30, and 36 (closes defect 2).
- [ ] Both tests pass on their **first** attempt-leg in each run — Gradle retries each instrumented test up to 3×, and a test that only passes on retry is not green for this task's purposes.
- [ ] No `AmbiguousViewMatcherException` appears anywhere in the `FeedItemlistFragmentTest` output on any leg.
- [ ] No `Could not launch intent ... within 45000 milliseconds` appears on the API 23 leg.

Production change correctness:
- [ ] `testMissingFeedShowsEmptyStateWithoutCrashing` asserts `allOf(withId(R.id.progressBar), withParent(withId(R.id.coordinatorLayout)))` matches `not(isDisplayed())`, and that assertion passes on all three API levels — the executing proof that the `onComplete` handler now hides the spinner.
- [ ] The diff to `FeedItemlistFragment.java` is exactly two added lines, both `viewBinding.progressBar.setVisibility(View.GONE);`, one per non-success handler, with no other line in the file added, removed, reordered, or reformatted (verifiable by `git diff --stat` showing `2 insertions(+), 0 deletions(-)` for that file).
- [ ] The `error` consumer's added line is positioned identically to the `onComplete` one and to the success path's `:678` — after `refreshHeaderView()`, before `adapter.setDummyViews(0)`.
- [ ] No public API break: `loadItems()` is `private`, both handlers are lambdas internal to it, and no signature, field, or visibility changes. Verified by the diff containing no declaration-line changes.

Suite-level and scope:
- [ ] The full `emulator-test` suite is green on all three API legs — not just this test class. Research established these two tests are the only remaining failures, so any *new* failure elsewhere is a regression introduced by this change and must be investigated, not retried away.
- [ ] `./gradlew checkstyle lint` passes (relevant because the import changes are the kind of thing checkstyle's unused/ordering rules catch).
- [ ] The diff touches exactly the two files in File Scope and no others.
- [ ] No comments added to either file (AGENTS.md).

Process safeguard:
- [ ] Step 3 completed: three full `emulator-test` runs, all three API legs completing in each (none cancelled by matrix `fail-fast`), run IDs and per-leg outcomes recorded in the checkpoint file.
- [ ] **Merge is not recommended until the box above is ticked.** The reviewer and red-team should treat an untick here as blocking regardless of how clean the diff looks.

### Milestone

**Not a numbered migration milestone.** This is unbilled remediation on the characterization surface — repair of test code delivered under the parent task `antennapod-fix-feeditemlistfragment-null-feed-crash` (PR #29, merged), plus the two-line production fix José scoped in above.

It **gates Milestone 15b** (`tasks/antennapod-sync-settings-before-screenshot-milestone-15b.md`, PR #22), which needs a reliably green `emulator-test` on `develop` before it can be rebased and re-checked. It does not advance the Milestone 15–20 sync-settings sequence itself.

For the portfolio case study, the honest framing of this work is that the pipeline's CI-verification gate caught a real defect the pipeline's own earlier stages had already approved — and that skipping that gate is what created a second task. That is a case-study point in the methodology's favor only if the gate is actually honored this time.

### Out of Scope

- **`@Before`/`@After` lifecycle hooks for this test class (Research Unknown 4).** Every sibling test in the package has them and this class has neither, leaving a resumed `singleTask` `MainActivity` behind. Genuine convention divergence and a plausible source of *future* flakiness — but it is implicated in none of the three defects, and adding it would expand a two-file surgical diff into a restructuring of the class. Worth a separate follow-up task; not this one.
- **Lowering the 45 s `IdlingPolicy` timeout (Research Unknown 5).** Moot once defect 1 is fixed — the API 23 leg stops burning ~90 s on this test because it stops timing out at all.
- **A test that drives `loadItems()`'s `onError` branch.** Research flagged that nothing covers it. The branch receives the same two-line fix, but adding a test that forces a `DBReader` failure is new test design, not matcher repair.
- **Removing the `Thread.sleep(1000)` at `:53`** or otherwise modernizing the test's waiting strategy.
- **Anything else in `FeedItemlistFragment.java`**, including the rest of `loadItems()`, `loadMoreItems()` (`:701-731`), `refreshHeaderView()`, and the `Maybe`/RxJava3 pattern. Explicitly: **no `concurrency`-track work** — no Coroutines/Flow migration is proposed or implied by touching this method.
- **Any `compose`-track work.** Recording for completeness only, since Research raised it: this screen is View/XML with ViewBinding and has **no ViewModel/MVVM layer**, which is a standing blocking prerequisite for any future `compose` track on `:app`. That is bespoke prerequisite work, separately scoped and priced (and already tracked for the sync-settings slice as Milestone 17). Nothing in this task should be read as scoping it.
- **`kotlin`, `gradle-kts`, `di`, `navigation` tracks.** Not requested, not applicable to these two files, not planned. Note that converting either file while its only characterization test is red would violate the pipeline's behavioral-equivalence premise — which is the reason this task exists ahead of any such work.
- **Re-opening PR #29's production fix.** Research confirmed it is working: the crash signature appears 0 times across 9 attempt-legs, and `DownloadLogTest` — the ~50%-flaky NPE that motivated it — passed on all 8 completed attempts.

## Open Questions

1. **Should Step 1 get its own CI run before Step 2 lands?** (Planner's recommendation: optional, and only if CI budget is comfortable.) Running the matrix after Step 1 alone would empirically isolate the matcher fixes from the production fix — the expected signature is API 30/36 fully green and API 23 green on `testExistingFeedLoadsItems` but still timing out on `testMissingFeed...`, which would confirm defect 1 is genuinely production-side before any production line is touched. Against it: a full emulator matrix run costs real CI time, and because Steps 1 and 2 are separate commits, bisecting after the fact is cheap if Step 3's run surprises us. Recommendation is to skip it and rely on Step 3, unless José wants the cleaner isolated evidence for the case study. Either way this does not change Step 3's mandatory three-run bar.
2. **If the API 23 leg still times out at `launchActivity` after Step 2, stop and report — do not widen scope.** Flagging the contingency now so nobody improvises later. Research's API-25 `AnimatedVectorDrawable`/RenderThread account is the one link in its chain that is inference from documented platform behavior rather than observation (Research says so itself, and no device was available to reproduce). If it turns out to be incomplete, the correct response is a new task with fresh research — not adding more production changes to this diff under time pressure. The two-line fix is independently correct as a UX fix regardless of whether it fully resolves Espresso's idle detection on API 23.

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-08-18 | Loop 1 of max 2_

### Verdict
APPROVE

### Verification performed (independent, not taken on the planner's report)

- **D6's `withParent(withId(R.id.coordinatorLayout))` anchor.** Read `app/src/main/res/layout/feed_item_list_fragment.xml` directly. Confirmed: the `<ProgressBar android:id="@+id/progressBar">` (`:65-71`) is a direct child of the root `<androidx.coordinatorlayout.widget.CoordinatorLayout android:id="@+id/coordinatorLayout">` (`:2-87`) — siblings with `AppBarLayout`, `SwipeRefreshLayout`, `more_content` include, and `floatingSelectMenu`. Confirmed `R.id.progressBar` appears in exactly **14** layouts repo-wide (`grep -rl 'id="@+id/progressBar"' --include="*.xml" .`, excluding `build/`): the planner's count is exact. Read the two collision cases the planner named: `nav_list.xml:76-81`'s `ProgressBar`'s immediate parent is the root `RelativeLayout android:id="@+id/nav_layout"` (not `coordinatorLayout`) — `withParent` correctly excludes it. `feeditemlist_item.xml:198-205`'s `LinearProgressIndicator android:id="@+id/progressBar"` is nested several levels deep inside an episode row's `LinearLayout`s, nowhere near a `CoordinatorLayout` — also correctly excluded. Also confirmed `R.id.coordinatorLayout` is declared in exactly one layout repo-wide, and that it is distinct from `main.xml`'s Activity-level `overview_coordinator_layout` (`main.xml:22`) — no cross-level id collision risk. The anchor is correct.
- **The ancestor-scoping matcher (D2/D3) and Unknown-2/D4's "static containment, API-invariant" claim.** Confirmed `android:id="@+id/appBar"` is declared in exactly two layouts repo-wide (`feed_item_list_fragment.xml:11`, `feedinfo.xml:11`), and `feedinfo.xml` belongs to a different fragment. Confirmed the only layout resource-qualifier directory in the entire repo (`find . -type d -name "layout-*"`) is `app/src/main/res/layout-sw720dp`, containing only `main.xml`, which contains no `appBar`. Confirmed `feed_item_list_fragment.xml`'s `appBar` contains exactly one `txtvTitle`, via the `header` include (`:32-34` → `feeditemlist_header.xml:136`, which itself contains no further `<include>`). Also independently confirmed — beyond what the planner argued — that `main.xml`'s `audioplayerFragment`/`playerFragment` subtree (hosting the mini-player's colliding `txtvTitle`) is a structural sibling of `main_content_view` (which hosts this fragment), not a descendant of anything under `appBar` — so the ancestor-scoping is robust for a stronger reason than the planner stated (not just "the mini-player is `GONE`" but "the mini-player is on an entirely separate branch of the tree"). D4's "no `layout-v*` qualifiers, so containment is API-invariant" claim holds.
- **Sanity-read of `EspressoTestUtils.waitForView` vs. `waitForViewGlobally`** (`EspressoTestUtils.java:56-93`, `:102-126`), underpinning D3's "loud ambiguity beats silent false-green" reasoning. Confirmed `waitForView` returns on the first breadth-first match with no ambiguity check. Confirmed `waitForViewGlobally` routes through `onView(...).check(matches(isDisplayed()))` inside a catch that traps only `NoMatchingViewException` and `AssertionFailedError` — `AmbiguousViewMatcherException` is neither, so it propagates immediately, exactly as claimed. One correction to the plan's own wording, not a substantive problem: D3 says the chosen 3-conjunct set is "the exact conjunct set `FeedSettingsTest.java:59-60` already uses" — true for the `allOf(...)` conjunct list itself, but `FeedSettingsTest` reaches it via `onView(isRoot()).perform(waitForView(...))`, not `waitForViewGlobally`; the plan is aware of and explicit about not following that part of the idiom (first D3 bullet), so this is a documentation-precision nit, not a hidden inconsistency — filed as MINOR below.
- **The two-line production fix and File Scope.** Read `FeedItemlistFragment.java`'s current `loadItems()` (`:652-699` in the plan's line numbering) directly. Confirmed the `error` and `onComplete` consumers currently read `feed = null; refreshHeaderView(); adapter.setDummyViews(0); adapter.updateItems(Collections.emptyList()); updateToolbar();` (plus `Log.e` for `error`) — the planned insertion point ("immediately after `refreshHeaderView()`, before `adapter.setDummyViews(0)`") is exactly where the success handler's own `progressBar` GONE call already sits relative to its equivalent statements. `android.view.View` is already imported (`:12`). No other statement in the method is touched. This is a genuinely minimal, mirror-of-existing-pattern diff. `git status` on the branch confirms no code has been written yet (only the task/checkpoint docs are modified) — nothing to check for File Scope drift at this stage.
- **Interaction with the parent task's `localFeed`/`MaybeCallbackObserver` concurrency finding.** Read the parent task's red-team history in full. The finding there is scoped specifically to the **success** consumer: `MaybeCallbackObserver.onSuccess`'s try/catch wraps the whole consumer body and escalates a thrown exception straight to `RxJavaPlugins.onError`, bypassing the graceful `error` consumer — and the hazard is a cross-invocation clobber of the shared, unsynchronized `feed` field read inside that consumer. This task's two added lines are in the `error` and `onComplete` consumers, not the success consumer; they are unconditional `View.GONE` calls on `viewBinding.progressBar` (already dereferenced without incident in the same method's success path) and read no field this finding is about. No interaction.
- **Step ordering / D5 (Unknown 3).** Confirmed by direct code inspection that `:56`'s matcher fix in Step 1 is unconditional (applies before any API-level branching), so Step 2's production fix cannot expose a not-yet-fixed defect-3 on any API level — the trap the checkpoint flagged is correctly neutralized by ordering alone, not merely asserted.
- **D7's characterization-test framing.** Confirmed honest rather than mislabeled: Step 1 (test-only, repairs pinning of existing behavior) precedes all production code; Step 2 is disclosed in-plan as a deliberate behavior change with a regression test, not dressed up as "pinning current behavior." This satisfies the mandatory-characterization-tests-first rule in the inverted form the plan claims, and does not overclaim equivalence anywhere I could find — Acceptance Criteria's "Production change correctness" section is scoped to diff-shape and the new assertion, not to a false pre/post equivalence claim.

### Checklist categories considered

1. **Characterization tests prove equivalence, not existence** — addressed above; Step 1's fixed matchers pin real behavior (structurally verified unique matches), and Step 2's new assertion is honestly framed as a regression test for new behavior, not equivalence.
2. **Silent behavior changes from mechanical translation** — not applicable; no J2K conversion in this task, Java-only diff.
3. **Public API breakage** — dismissed: `loadItems()` is `private`, no signature/visibility change, verified by reading the current method signature and body directly.
4. **Coverage gaps left unaddressed** — the plan explicitly leaves the `error` branch's progressBar fix unasserted (Out of Scope, honestly disclosed) and explicitly leaves `@Before`/`@After` hygiene and `Thread.sleep` modernization out of scope with stated reasoning. Neither is a silent gap.
5. **`concurrency` track** — not requested; verified no new interaction with the parent task's `MaybeCallbackObserver` finding (above).
6. **`compose`/`navigation`, 7. `di`, 8. `gradle-kts`** — not applicable; no track requested, confirmed the diff touches only a Java View/XML fragment and a Java Espresso test.
9. **Milestone/scope creep** — dismissed. File Scope is capped at two files with byte-level precision (two production lines, three test additions), matches AGENTS.md's minimal-diff rule, and the plan explicitly refuses to fold in `@Before`/`@After` hygiene or `compose`-track MVVM prerequisites it flags as tempting-but-out-of-scope.

### Concerns

- **Severity:** MINOR
  **Class:** Characterization tests prove equivalence, not just existence (documentation precision)
  **Concern:** D3 states the chosen matcher is "the exact conjunct set `FeedSettingsTest.java:59-60` already uses," which is true of the `allOf(...)` conjuncts but not of the surrounding wait mechanism — `FeedSettingsTest` reaches those conjuncts via `onView(isRoot()).perform(waitForView(...))`, while this plan deliberately keeps `waitForViewGlobally` for the reasons stated earlier in the same D3 bullet. Not a functional risk (the plan's own reasoning for keeping `waitForViewGlobally` is sound and independently verified above), but a future reader skimming only the "exact conjunct set" phrase could mistakenly believe the whole idiom, including the wait mechanism, was copied verbatim.
  **Evidence:** Plan D3 vs. `app/src/androidTest/java/de/test/antennapod/ui/FeedSettingsTest.java:59-60` (uses `waitForView`, not `waitForViewGlobally`).
  **Suggested mitigation:** None required before implementation; optionally tighten D3's wording to "the same `allOf` conjunct set" rather than implying the full call idiom matches, when this doc is next touched.

Plan is well-verified against the actual layout XML and production code rather than asserted, the two-line production fix is minimal and does not reopen the parent task's concurrency-hardened method, the matcher fixes for all three defects are structurally sound (not merely plausible) on all three API levels, and Step 3's CI-verification gate is correctly made mandatory and blocking given why this task exists. Proceed to `android-migration-developer`.

## Implementation Notes
_By: android-migration-developer | 2026-08-18_

### Commit message

Two commits, matching this portfolio's established test-then-fix split (cf. `675d2a0fe` / `d91e6a484` on the parent task):

1. `734c0971a` — `test(app): fix ambiguous view matchers in FeedItemlistFragmentTest`
   Scopes both title matchers to `isDescendantOfA(withId(R.id.appBar))` so they no longer collide with the nav-drawer subscription row, the episode row's cover placeholder, or the mini-player title (defects 2 and 3). Test-file-only, per Step 1 / D7.
2. `ecd85e9ca` — `fix(app): hide progressBar on FeedItemlistFragment's error/onComplete paths`
   Adds `viewBinding.progressBar.setVisibility(View.GONE);` to `loadItems()`'s `error` and `onComplete` handlers, mirroring the success handler's existing `:678` line, plus a new assertion pinning it (defect 1, Step 2 / D1 / D6).

Full diff is exactly the two files in File Scope; no other file touched.

### Test commands run

Actual discovered variants for `:app` (flavor dimension `market` = `free`/`play`, no plain `debug`/`release`): `testFreeDebugUnitTest`, `testFreeReleaseUnitTest`, `testPlayDebugUnitTest`, `testPlayReleaseUnitTest` (unit); `connectedFreeDebugAndroidTest`, `connectedPlayDebugAndroidTest` (instrumented). CI (`checks.yml:140,156`) builds and runs only the `play` flavor's instrumented tests, so that is what was exercised locally.

- `./gradlew :app:compilePlayDebugAndroidTestSources :app:compileFreeDebugAndroidTestSources` — before Step 1 edits: n/a (baseline unedited file already compiled); after Step 1: PASS; after Step 2: PASS (both flavors, both times).
- `./gradlew :app:assembleDebug` (per AGENTS.md's mandated compile command) — PASS, both `freeDebug` and `playDebug` variants assembled, no compilation errors. Full untruncated output captured; only pre-existing deprecation notes (`Note: ... uses or overrides a deprecated API`), unrelated to this diff.
- `./gradlew :app:connectedPlayDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.test.antennapod.ui.FeedItemlistFragmentTest` — run against a real, locally-booted API 36 emulator (`Pixel_9a` AVD, arm64-v8a; animations disabled via `settings put global *_animation_scale 0`, matching CI's `disable-animations: true`): **before Step 2 (Step 1 only): PASS (2/2, 0 failures, 0 errors)**; **after Step 2: PASS (2/2, 0 failures, 0 errors)**. This is real device execution, not a compile-only check — a device was unavailable during Research but became available during implementation, so this instance's characterization evidence goes beyond the plan's compile-time verification and directly confirms one of the three CI API legs (36) before the mandatory Step 3 CI run.
- `./gradlew checkstyle lint` — PASS, no findings in either changed file.
- Emulator API level covered locally: 36 only (matches one of the three CI legs). API 23 and 30 were not locally reproduced — no AVD for those levels was available in this environment — and remain to be confirmed by Step 3's mandatory CI runs, per the Plan.

### Characterization test results

- `FeedItemlistFragmentTest.testMissingFeedShowsEmptyStateWithoutCrashing` — before Step 1 (baseline, unedited): known-red per Research (45 s launch timeout on API 23, `withId(R.id.txtvTitle)` 12-way ambiguity on API 30/36 — not re-run, taken from Research's CI evidence since reproducing the pre-fix state locally would require reverting the branch). After Step 1 (matcher fix only, progressBar assertion not yet added): PASS on API 36 (2/2 total including the sibling test) — exercises the missing-feed empty-state path end-to-end: activity launch, recyclerView 0-child assertion, and the now-disambiguated empty-title assertion. After Step 2 (progressBar fix + new assertion): PASS on API 36, and the new `allOf(withId(R.id.progressBar), withParent(withId(R.id.coordinatorLayout)))` / `not(isDisplayed())` assertion is now part of the test and passed — this is the executing proof that the `onComplete` handler's added `View.GONE` call actually takes effect at runtime, not just that it compiles.
- `FeedItemlistFragmentTest.testExistingFeedLoadsItems` — before Step 1: known-red per Research (`AmbiguousViewMatcherException`, 2-3 way collision on `withText(feed.getTitle())`). After Step 1: PASS on API 36 — exercises the happy path end-to-end: real feed written via `FeedDatabaseWriter.updateFeed`, activity launch, disambiguated title-under-`appBar` wait, and the ≥1-child recyclerView assertion. After Step 2: PASS on API 36, unaffected by the Step 2 diff (as expected — Step 2 only touches the `error`/`onComplete` handlers, never reached on this path).
- Both tests' first-attempt-leg pass (no retry needed) on the one API level verified locally, consistent with Research's finding that these failures are 100% deterministic rather than flaky, so a single clean local run is meaningful evidence rather than a lucky draw.

### Deviations from plan

None in the two files' diff — both commits are byte-for-byte the Plan's Step 1 and Step 2 as specified (verified via `git diff --stat`: `FeedItemlistFragmentTest.java` +3/-2 in commit 1, then +3/-0 in commit 2; `FeedItemlistFragment.java` +2/-0 in commit 2 only).

One addition beyond what the Plan required, noted for the record rather than as a deviation: local instrumented-test execution on a real API 36 device. Research explicitly could not do this (`adb devices` was empty, no AVD running); an AVD was available during this implementation session, so it was used to get real pass/fail evidence rather than relying on compile-only verification. This does not replace or shortcut Step 3 — the mandatory three-run, all-three-API-level CI-verification gate is unchanged and still not executed by this session, per the task's explicit instruction that Step 3 is CI verification happening after PR opens.

Step 3 (three full `emulator-test` CI runs, all three API legs, gating any merge recommendation) was **not** performed in this session, as instructed — it is the next lifecycle stage, after code review and implementation-stage red-team.
