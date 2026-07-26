# Checkpoint — antennapod-event-kotlin

> **Repo:** `antennapod`
> **Task file:** `tasks/antennapod-event-kotlin.md`

## Status
**DONE — ready for PR (single unified PR: code + spec docs together)**

## Last updated
2026-07-26

## Lifecycle progress
- [x] Research (legacy-android-researcher)
- [x] Plan (legacy-android-planner)
- [x] Red-team plan (legacy-android-red-team) — APPROVE, loop 1 of 2, 3 MINOR polish fixes applied to Plan text
- [x] Implement (android-migration-developer) — all 7 Steps done, 23/23 files converted, 94/94 characterization tests green before and after
- [x] Code review (migration-code-reviewer) — **APPROVE, loop 2 of max 3.** Loop 1 REQUEST CHANGES (one MAJOR: an added comment in `EpisodeDownloadEvent.kt` violated the repo's no-added-comments rule; two non-blocking MINORs). Fixed and re-verified 94/94 green → loop 2 APPROVE.
- [x] Red-team implementation (legacy-android-red-team) — **APPROVE, loop 2 of max 2 (final).** Loop 1 CHALLENGE (one MAJOR: D16 claimed the `SleepTimerUpdatedEvent` `Long.MIN_VALUE` overflow case was test-pinned; it wasn't). Fixed by adding one characterization test, re-verified 95/95 green → loop 2 APPROVE, no further findings.
- [ ] PR opened

## Decisions for next session
- Module: `:event` (`de.danoeh.antennapod.event`). Track: `kotlin` only, chosen as the next case-study module after `:model`'s kotlin track closed out (Milestones 1–7, PR #13 merged 2026-07-25). Selected for the same reasons `:model` was picked first: 23 plain Java POJOs per `event/README.md`, used app-wide via GreenRobot EventBus `@Subscribe`, minimal framework coupling.
- This is unaffiliated OSS portfolio work — see `services/android-migration/projects/portfolio/README.md`.
- Per this session's explicit instruction: run the full lifecycle autonomously (no human intervention between stages) and ship as a **single unified PR** (code + spec docs together), matching the Milestone 7 precedent rather than the earlier split-PR precedent.

- **Plan shape (2026-07-26):** one milestone (Milestone 8), one unified PR, 7 steps / 6 risk-tiered commits. Explicitly *not* the 3-way milestone split research floated — see Plan D1 and its hard-stop trigger.
- **Plan's load-bearing decision:** characterization tests are written in **Java** (D10). A Java test that compiles and passes unmodified against both the Java and the Kotlin classes is the equivalence proof for the 104 Java call sites. No test file may be edited during Steps 3–6 (one disclosed exception, D7).
- **Open for José:** OQ1 upstreaming intent (non-blocking, standing); OQ2 scheduling of the test-suite Kotlin conversion as Milestone 9.
- **AC10/SpotBugs deviation — resolved by both gates.** `:app:spotbugs{Free,Play}Debug` newly surfaces 6 real (pre-existing, not introduced) null-dereference risks in 4 consumer files (`MainActivity.java`, `OnlineFeedViewActivity.java`, `PreferenceActivity.java`, `QueueFragment.java`) now that `PlayerErrorEvent.message`/`MessageEvent.message`/`QueueEvent.item`'s Kotlin nullability is visible to SpotBugs. Both `migration-code-reviewer` and `legacy-android-red-team` independently confirmed this is pre-existing (verified via `git stash` + rerun against untouched `develop`) and accepted it as a documented, non-blocking deviation — matching the `:model` precedent (D9/D20) of not fixing pre-existing issues found incidentally. Logged as a candidate follow-up task (4 consumer files need null-guards) rather than folded into this milestone's File Scope. `:app-wearos`'s pre-existing `checkstyle lint` Kotlin-compile failure is also confirmed unrelated.
- Final test count: 95 (94 from implementation + 1 `Long.MIN_VALUE` edge case added during implementation red-team loop 1).

## Resume command
Milestone 8 is DONE — all lifecycle gates green. Next: create branch, commit, push, open the unified PR (code + spec docs together) using the Plan section as the PR description per `.github/pull_request_template.md`.
