# Checkpoint — antennapod-fix-net-download-service-spotbugs-debt

> **Repo:** `antennapod`
> **Task file:** `tasks/antennapod-fix-net-download-service-spotbugs-debt.md`
> **Sibling task:** `antennapod-fix-spotbugs-static-analysis-debt` (unrelated cause, shared symptom — both gate `checks.yml`'s `static-analysis` job).
> **Blocks:** Milestone 15b (`tasks/antennapod-sync-settings-before-screenshot-milestone-15b.md`, PR #22) — its AC13 CI-gate needs this merged too, alongside the sibling task.

## Status
**RED-TEAM APPROVED (Implementation, Loop 2 of max 2, final) — ready for PR.** All 9 Steps complete on branch `fix/net-download-service-nullable-param-spotbugs` (off `origin/develop` at `5ae7d560f`). All 9 SpotBugs findings suppressed (0 `BugInstance` on both `:net:download:service:spotbugsPlayDebug` and `:app:spotbugsPlayDebug`, independently re-verified by the Loop 2 red-team run); 7 call sites guarded across 5 `:app` files; characterization tests green before and after the one testable guard; repo-wide `--continue checkstyle lint` sweep shows only the pre-existing, out-of-scope `:app-wearos` compile failures (D9). Three deviations recorded across the lifecycle: a corrected call-site line number in one `exclude.xml` comment (D7's prose cited `:145`, verified-accurate is `:163`); 6 pre-existing `LocalFeedUpdaterTest` failures confirmed identical on a clean `origin/develop` worktree (unrelated to this diff, out of File Scope, not fixed here); and the Loop 1 implementation red-team's MAJOR finding (the `runOnce` suppression comment claimed "six" unguarded call sites but listed five citations) fixed with a one-word change, independently re-verified in Loop 2.

## Last updated
2026-08-12

## Lifecycle progress
- [x] Research (legacy-android-researcher) — 2026-08-12
- [x] Plan (legacy-android-planner) — 2026-08-12
- [x] Red-team plan Loop 1 (legacy-android-red-team) — 2026-08-12 — **CHALLENGE** (1 CRITICAL, 1 MAJOR; both wording defects)
- [x] Plan revised in response to Loop 1 (legacy-android-planner) — 2026-08-12
- [x] Red-team plan Loop 2 (legacy-android-red-team) — 2026-08-12 — **APPROVE** (1 MINOR, cosmetic, non-blocking)
- [x] Implement (android-migration-developer) — 2026-08-12
- [x] Code review (migration-code-reviewer) — 2026-08-12 — **APPROVE** (2 MINOR, non-blocking)
- [x] Red-team implementation Loop 1 (legacy-android-red-team) — 2026-08-12 — **CHALLENGE** (1 MAJOR: `exclude.xml` count/citation mismatch)
- [x] Implementation fixed in response to Loop 1 (android-migration-developer) — 2026-08-12
- [x] Red-team implementation Loop 2 (legacy-android-red-team) — 2026-08-12 — **APPROVE** (final loop)
- [ ] PR opened
- [ ] PR merged
- [ ] Milestone 15b's branch rebased/merged with `develop` and CI re-checked (alongside the sibling SpotBugs task)

## Decisions for next session
- Discovered 2026-08-12 by `migration-code-reviewer` during Loop 1 review of the sibling task's implementation — not part of the Sync Settings vertical-slice sequence (Milestones 15-20), standalone repo hygiene.
- 9 SpotBugs findings in `:net:download:service:spotbugsPlayDebug`, traced to PR #21's `:net:download:service-interface` Kotlin migration (Milestone 10, `d5b7f94aa`, merged 2026-08-06). Pre-existing on `develop`, not caused by any in-flight task.
- José authorized this 2026-08-12 as a separate follow-up task, sequenced alongside (not blocking on, not blocked by) the sibling `antennapod-fix-spotbugs-static-analysis-debt` task — both must merge before Milestone 15b's PR #22 gets a real CI signal.
- Worth checking in Research whether this shares the sibling task's root-cause pattern (Kotlin migration interop making previously-invisible findings visible to SpotBugs) or is a genuinely different mechanism — flagged in the task file's pre-research context, **now verified: same J2K nullability-widening mechanism, different manifestation.** All 9 are one pattern, `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` (priority 2, rank 16, category STYLE), across 6 methods in 2 classes. Cause is J2K widening unannotated Java abstract-method params to Kotlin nullable in `d5b7f94aa`; bytecode-confirmed via `javap`.

### Plan outcome (2026-08-12, `legacy-android-planner`)
- **Approach: option (c), corrected.** Guard 7 nullable call sites in 5 `:app` files; suppress **all 9** findings via **7** method-scoped `exclude.xml` entries; leave the Kotlin declarations untouched per `net/download/service-interface/README.md:10`.
- **Two Research errors caught and corrected in the Plan — the developer must follow the Plan, not Research's prose here:**
  1. **Guarding call sites clears zero SpotBugs findings.** `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` does no call-site analysis, so "guard 3, suppress 6" would leave findings 5/8/9 live and CI red. All 9 must be suppressed; the guards' role is to *earn* the suppressions by removing reachability — but only for findings 5/8/9; the other six were already unreachable with no guard added, and the suppression comments must say so separately (Plan D1 correction (1), D7 templates (a)/(b)).
  2. **Seven distinct methods, not six.** Re-derived from the report XML: `downloadNow`, `download`, `cancel`, `cancelAll`, `getNumberOfActiveDownloads`, `restartUpdateAlarm`, `runOnce`. Writing 6 entries ships a still-red CI (Plan D2).
- Parameter-level suppression is impossible (`cancel` carries one reachable + one unreachable finding; SpotBugs filters have no parameter matcher) — Plan D3.
- **Only 1 of 7 guards is testable** (`CancelDownloadActionButton`, plain JVM test in `app/src/test` with a recording `DownloadServiceInterface` double). The six `Fragment.getContext()` / `WeakReference` guards are review-verified, not test-verified, with honest AC wording — no Robolectric added (Plan D5).
- `AutoDownloadManagerImpl`'s latent 10th/11th finding: **out of scope**, documented in `net/download/service/README.md` with its trigger condition (Plan D6).
- Branch: `fix/net-download-service-nullable-param-spotbugs`, fresh from `develop` at `5ae7d560f` (Plan D8).
- **PR must not claim it unblocks Milestone 15b's CI** — the `:app-wearos` fix is also required (Plan D9).

### Red-team Loop 1 response (2026-08-12, `legacy-android-planner`)
Verdict CHALLENGE, both findings accepted and fixed in place. Neither touched the Steps' code-change substance, File Scope, or any other Resolved Decision — D1-D7's technical content (the 7 guard sites, the 9 findings across 7 `exclude.xml` entries) was independently re-verified by the red-team as correct.

1. **CRITICAL — the "removing a guard re-arms the finding" claim was false and self-contradicted D1.** SpotBugs's `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` does zero caller analysis, so it cannot observe whether a guard exists; if a guard is later deleted, nothing re-detects and CI stays green while the crash path reopens. **D1 gained correction (2)**: the suppressions are unconditional and permanent from SpotBugs's point of view, the guards are what make the code safe, and the suppressions carry no enforcement mechanism — only `CancelDownloadActionButtonTest` (1 of 7 guards) is a mechanical safety net; the rest is the README note plus code review. Propagated to D7's per-class preamble (iii), Step 8's README instruction, and the Suppression-quality / Documentation ACs, with an explicit prohibition on the word "re-arm" anywhere in the shipped artifact and an AC requiring the reviewer to grep for it.
2. **MAJOR — D7's suppression-comment instructions were uniform where the findings are not.** Only findings 5, 8, 9 are made unreachable by a guard this commit adds; findings 1, 2, 3, 4, 6, 7 had no unsafe call site to begin with. **D7's comment section is now split into template (a)** (guard added — name the guarded files, per a new mapping table) **and template (b)** (no guard added or needed — enumerate the already-non-null callers, do not attribute a guard). `cancel`'s single entry now has an explicit mixed-justification rule: `context` under (b), `media` under (a), separated by parameter name; a blended sentence fails the AC. **D1 gained correction (1)** so its "made true by this diff" framing states 3-of-9 rather than 9-of-9.

### Open decisions blocking the planner (2026-08-12, from Research) — ALL RESOLVED in the Plan
1. **`:app-wearos` does not compile on `develop` — and CI hits it before SpotBugs.** `EpisodeDetailActivity.kt:115` fails on `FeedItem.title` being `String?` (portfolio-caused, `:model` Milestones 1-7). `:app-wearos:lint` depends on that compile, so `./gradlew checkstyle lint` cannot pass while it stands. `develop` has had **eight consecutive red `Checks` runs** back to 2026-07-26. Both this task's and the sibling's "clears the CI gate" premise are incomplete. Probably a third task — needs José's call on scope and ordering.
2. **Fix mechanism:** (a) tighten the Kotlin abstract declarations, (b) method-scoped `exclude.xml` suppressions, or (c) guard the 3 reachable call sites + suppress the 6 unreachable. Option (a) contradicts a standing warning in `net/download/service-interface/README.md:10` where a prior milestone made the opposite call for the same reason.
3. **The "13 latent NPEs" in `d5b7f94aa`'s commit message are unrelated to these 9 findings** — verified as the exact count of `!!` operators in the interface module's own Kotlin. No missing four; do not let the discrepancy drive scope.

### Verified baseline
- **9 findings, unchanged** on clean `develop` `f5d4c5551`, on current `develop` HEAD `5ae7d560f`, and on the sibling branch. Sibling PR #23 (now merged into `develop`) does **not** affect this pile; `:app:spotbugsPlayDebug` now passes.
- Reproduce with `./gradlew :net:download:service:spotbugsPlayDebug`; report at `net/download/service/build/reports/spotbugs/playDebug.xml`.
- **Zero test coverage** at all 9 sites — the existing suite deliberately installs `DownloadServiceInterfaceStub` instead of the flagged impls.

## Resume command
Fully red-teamed and approved — see `## Red-Team Verdict — Implementation` (both loops) in `tasks/antennapod-fix-net-download-service-spotbugs-debt.md` for the final verification record, and `## Implementation Notes` (including its Loop 1 fix addendum) for the commit message draft, test commands run, characterization-test results, and all three disclosed deviations. Branch `fix/net-download-service-nullable-param-spotbugs` is off `origin/develop` at `5ae7d560f`, working tree has no uncommitted changes beyond this task's own diff plus the two untouched `antennapod-fix-app-wearos-compile-error` spec files (still untracked, not part of this task's diff).

Next step: open the PR. Use the Plan's Objective/Resolved-Decisions section as the PR description per AGENTS.md's PR conventions, reference `Closes:` the originating issue if one exists, and do not claim in the PR that this unblocks Milestone 15b's CI on its own (D9 — the sibling `antennapod-fix-spotbugs-static-analysis-debt` task and the `:app-wearos` compile fix are also required).
