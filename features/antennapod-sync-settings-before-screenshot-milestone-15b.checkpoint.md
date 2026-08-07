# Checkpoint — antennapod-sync-settings-before-screenshot-milestone-15b

> **Repo:** `antennapod`
> **Task file:** `tasks/antennapod-sync-settings-before-screenshot-milestone-15b.md`
> **Depends on:** Milestone 15 (PR #21, merged into `develop` at `f5d4c5551`, 2026-08-06).

## Status
**Implementation done, Implementation Notes written, PR #22 open. AC13 (CI-gate) cannot be satisfied in its literal form — disclosed, not worked around — because CI's `unit-test` job is gated behind a `static-analysis` job that fails on pre-existing, unrelated SpotBugs debt in `:app`. Ready for code review with that one open item flagged for José.**

## Last updated
2026-08-07

## Lifecycle progress
- [x] Research (legacy-android-researcher)
- [x] Plan (legacy-android-planner)
- [x] Red-team plan (legacy-android-red-team) — **Loop 1: CHALLENGE.** CRITICAL: `SyncSettingsTestHost` used the wrong theme (`Theme_AntennaPod_Light` vs. production's real `Theme.AntennaPod.Light.NoTitle` via `ToolbarActivity`), so the captured chrome wouldn't match what ships. Also 2 MAJOR (the 200-color floor doesn't alone catch the sdk-36 clipping failure; writing only to gitignored `build/` doesn't fully address CI-environment risk) and 1 MINOR (D1's dirty-file handling didn't account for #21 already merging). Revised: rather than editing the shared host (which would've broken 4 of Milestone 15's 38 tests that assert on `supportActionBar`, and — a deeper bug the planner found on its own — naively reusing `android.R.id.content` on a real `ToolbarActivity` would attach the fragment as an overlay sibling of the toolbar due to a content-id collision in `toolbar_activity.xml`, producing a screenshot that looks plausible but is structurally wrong), the plan now adds a dedicated `SyncSettingsCaptureHost` extending `ToolbarActivity` and inflating production's real `settings_activity.xml`. **Loop 2 (final): APPROVE** — every claim in Revision 1 independently re-verified against source, including the content-id collision and the four at-risk Milestone 15 tests. One MINOR non-blocking note (theme-qualifier resolution asserted by inspection, not empirically probed — self-correcting via Step 1's visual-inspection gate). Full verdicts at `## Red-Team Verdict — Plan` in the task file.
- [x] Implement (android-migration-developer) — branched `test/ui-preferences-sync-settings-before-screenshot` from fresh `develop` (after stashing the two M15 doc leftovers per revised D1 — stash ref `stash@{0}`, "M15 spec leftovers (see 15b D1)", verified still intact and unneeded for this task's own diff). 4 commits: Step 1 (new `SyncSettingsCaptureHost` + screenshot test), Steps 2-3 (checked-in artifact + README convention entry), plus two empty CI-retrigger diagnostic commits (disclosed in Implementation Notes, no file changes). Branch pushed, **PR #22 opened**: https://github.com/josegbel/antenna-pod-kt/pull/22. Implementation Notes section now written in full in the task file, with every Acceptance Criterion independently verified and recorded (not just claimed).
- **RESOLVED (was BLOCKED) — CI stall explained.** The retrigger commit (`8203a70d7`) did get a real `Checks` run (`31159097992`) after the earlier ~hour-long silence. That run failed, but the `unit-test`/`emulator-test` jobs (which would run the actual screenshot test) both declare `needs: static-analysis` (`.github/workflows/checks.yml:58,109`), and `static-analysis` failed on 7 pre-existing SpotBugs findings entirely outside this PR's diff (`QueueFragment.java` ×2, `PreferenceActivity.java`, `OnlineFeedViewActivity.java`, `FeedInfoFragment.java`, `MainActivity.java` ×2 — confirmed via `git diff --name-only f5d4c5551..HEAD` that none of these files are touched). AC13's literal CI-gate requirement is therefore structurally unreachable right now, through no fault of this implementation — documented in Implementation Notes rather than claimed as passing or silently dropped.
- [ ] Code review (migration-code-reviewer) — ready to start; AC13's disclosed gap is the one item worth its explicit attention.
- [ ] Red-team implementation (legacy-android-red-team)
- [x] PR opened — https://github.com/josegbel/antenna-pod-kt/pull/22 (not yet mergeable per this milestone's own AC13 bar until either the pre-existing static-analysis debt is cleared or José accepts local-only verification for this PR).

## Decisions for next session
- Small, test-only addition discovered mid-conversation: automate the "before" screenshot for the eventual Milestone 20 Compose comparison, using the Robolectric fragment-driving harness (`SyncSettingsTestHost`) that Milestone 15 already built, instead of a manual `adb` capture or waiting on Paparazzi (Milestone 16).
- **Update 2026-08-06: PR #21 merged into `develop` (`f5d4c5551`).** Branch fresh from `origin/develop` as normal — the stacked-branch plan is no longer needed. Verify via fresh fetch + ancestry check first (Milestone 15's D1 precedent: local `origin/develop` refs have been stale before).
- José authorized this 2026-08-06, explicitly asked for it to be a separate PR rather than added to #21.
- Auto-chain: not yet explicitly re-confirmed for this specific task by José in-session (see [[antennapod-pipeline-autonomy]] memory — authorization doesn't silently carry forward to a new milestone). The orchestrating session stated its intent to auto-chain this one too, consistent with the spirit of the standing instruction and the small/low-risk size of this task, giving José the chance to redirect.

## Resume command
Research and Plan are both complete in `tasks/antennapod-sync-settings-before-screenshot-milestone-15b.md`. All seven of Research's Unknowns are decided (D1–D8); nothing is left TBD. Three steps, six files in File Scope, twelve acceptance criteria, two Open Questions for José (OQ1 artifact's final home, OQ2 licensing before public publication) — neither blocks implementation.

Next step: invoke `legacy-android-red-team` on the Plan. Points most worth adversarial attention: D2's distinct-colour floor of 200 (is it the right guard, and is 200 the right number?), D3's claim that writing only to gitignored `build/` dissolves the CI-isolation problem entirely, and D5's sdk=34 pin being a *harness* workaround rather than app behaviour.

Before the first commit the developer must run D1's fetch-and-ancestry check, and must first resolve the two Milestone 15 files currently dirty in the working tree (`features/antennapod-sync-settings-kotlin-milestone-15.checkpoint.md`, `tasks/antennapod-sync-settings-modernization-future-work.md`) — they belong to M15's branch, not this task's diff.
