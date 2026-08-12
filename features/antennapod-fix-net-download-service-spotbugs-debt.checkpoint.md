# Checkpoint — antennapod-fix-net-download-service-spotbugs-debt

> **Repo:** `antennapod`
> **Task file:** `tasks/antennapod-fix-net-download-service-spotbugs-debt.md`
> **Sibling task:** `antennapod-fix-spotbugs-static-analysis-debt` (unrelated cause, shared symptom — both gate `checks.yml`'s `static-analysis` job).
> **Blocks:** Milestone 15b (`tasks/antennapod-sync-settings-before-screenshot-milestone-15b.md`, PR #22) — its AC13 CI-gate needs this merged too, alongside the sibling task.

## Status
**NOT STARTED — task scaffolded, research not yet begun.**

## Last updated
2026-08-12

## Lifecycle progress
- [ ] Research (legacy-android-researcher)
- [ ] Plan (legacy-android-planner)
- [ ] Red-team plan (legacy-android-red-team)
- [ ] Implement (android-migration-developer)
- [ ] Code review (migration-code-reviewer)
- [ ] Red-team implementation (legacy-android-red-team)
- [ ] PR opened
- [ ] PR merged
- [ ] Milestone 15b's branch rebased/merged with `develop` and CI re-checked (alongside the sibling SpotBugs task)

## Decisions for next session
- Discovered 2026-08-12 by `migration-code-reviewer` during Loop 1 review of the sibling task's implementation — not part of the Sync Settings vertical-slice sequence (Milestones 15-20), standalone repo hygiene.
- 9 SpotBugs findings in `:net:download:service:spotbugsPlayDebug`, traced to PR #21's `:net:download:service-interface` Kotlin migration (Milestone 10, `d5b7f94aa`, merged 2026-08-06). Pre-existing on `develop`, not caused by any in-flight task.
- José authorized this 2026-08-12 as a separate follow-up task, sequenced alongside (not blocking on, not blocked by) the sibling `antennapod-fix-spotbugs-static-analysis-debt` task — both must merge before Milestone 15b's PR #22 gets a real CI signal.
- Worth checking in Research whether this shares the sibling task's root-cause pattern (Kotlin migration interop making previously-invisible findings visible to SpotBugs) or is a genuinely different mechanism — flagged in the task file's pre-research context, not yet verified.

## Resume command
Task scaffolded, research not yet started. Next step: invoke `legacy-android-researcher` against `:net:download:service`'s current SpotBugs report (re-run `./gradlew :net:download:service:spotbugsPlayDebug` fresh from `develop` to get exact finding locations — the 9 violations' precise sites are not yet enumerated anywhere) and determine, for each, whether it's a real latent bug or a false positive, and whether it shares the sibling task's Kotlin-interop root-cause shape.
