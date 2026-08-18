# Checkpoint — antennapod-fix-feeditemlistfragmenttest-flaky-characterization-tests

> **Repo:** `antennapod`
> **Task file:** `tasks/antennapod-fix-feeditemlistfragmenttest-flaky-characterization-tests.md`
> **Sibling/parent task:** `antennapod-fix-feeditemlistfragment-null-feed-crash` (merged, PR #29) — this task fixes two deterministic failures in the characterization test file that fix added, discovered after José merged the PR ahead of its own CI-verification step completing.
> **Blocks:** Milestone 15b (`tasks/antennapod-sync-settings-before-screenshot-milestone-15b.md`, PR #22) — needs this merged for a reliably green `emulator-test` run.

## Status
**SCAFFOLDED (2026-08-18) — ready for `legacy-android-researcher`.** Branch `fix/feeditemlistfragmenttest-flaky-characterization-tests` created off `origin/develop @ a6275e8f0` (includes PR #29). First commit on this branch closes out the parent task's checkpoint bookkeeping (the implementation red-team's final Loop 2 APPROVE, written to the working tree but never committed before the merge).

Pre-research context in the task file already carries: confirmation that PR #29's production fix is not implicated (0/9 crash-signature occurrences in the one completed CI run on that PR), the two specific failures with their raw evidence, and a plausible-but-unverified root-cause theory for Failure 1 (pre-existing progressBar-not-hidden gap in the `error`/`onComplete` handlers, unchanged by PR #29). Research should confirm or refute that theory before Plan commits to a fix shape.

## Last updated
2026-08-18

## Lifecycle progress
- [x] Branch created off `origin/develop @ a6275e8f0`, parent-task checkpoint bookkeeping committed (`4efd6f528`)
- [ ] Research (legacy-android-researcher)
- [ ] Plan (legacy-android-planner)
- [ ] Red-team plan (legacy-android-red-team)
- [ ] Implement (android-migration-developer)
- [ ] Code review (migration-code-reviewer)
- [ ] Red-team implementation (legacy-android-red-team)
- [ ] PR opened
- [ ] CI verification (3 full `emulator-test` runs, per this task's own Plan — do not merge before this completes, given what happened to the parent task)
- [ ] PR merged
- [ ] Milestone 15b's branch rebased/re-checked once more for a genuinely, reliably green `emulator-test` run

## Decisions for next session
- Discovered 2026-08-18 during PR #29's post-merge CI verification (see parent task's checkpoint for full detail) — José merged that PR before its CI-verification step finished, so this follow-up exists to close the gap on `develop` rather than on a pre-merge branch.
- Test-file-only fix expected (`FeedItemlistFragmentTest.java`); do not expand File Scope to `FeedItemlistFragment.java` production code unless Research/Plan explicitly justifies it (see task file's Scope discipline note) — flag to José first if that turns out to be necessary.

## Resume command
Invoke `legacy-android-researcher` on this task file. It should: (1) independently verify the progressBar theory for Failure 1 (read `FeedItemlistFragment.java`'s current `onComplete`/`error` handlers and confirm whether an indeterminate `ProgressBar` left visible/animating is capable of blocking Espresso's `IdlingPolicy`-based activity-launch synchronization — this is a mechanism claim, not yet confirmed), (2) identify the second view that `withText(feed.getTitle())` collides with for Failure 2, (3) confirm current test coverage / whether any other `androidTest` file uses a similar pattern successfully (the Plan for the parent task cited `FeedSettingsTest.setUp()`'s `EXTRA_FEED_ID` launch pattern as a proven idiom — check whether it also asserts on view text the same way, for comparison).
