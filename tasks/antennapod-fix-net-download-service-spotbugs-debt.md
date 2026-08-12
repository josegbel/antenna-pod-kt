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
_Not yet started._

## Plan
_Not yet started._

## Open Questions
_None yet — will surface during Research/Plan._
