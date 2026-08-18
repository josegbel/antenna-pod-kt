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
_Last updated by: legacy-android-researcher | (pending)_

## Plan
_Last updated by: legacy-android-planner | (pending)_

## Open Questions
