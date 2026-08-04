# Checkpoint — antennapod-net-download-service-interface-kotlin-milestone-13

> **Repo:** `antennapod`
> **Task file:** `tasks/antennapod-net-download-service-interface-kotlin-milestone-13.md`

## Status
**IN PROGRESS — code review passed, awaiting red-team implementation review**

## Last updated
2026-08-04

## Lifecycle progress
- [x] Research (legacy-android-researcher) — 6 Java files / 823 LOC / 54 tests, no ambiguity-shaped hazards; central scoping question was whether `DownloadServiceInterfaceTest` should stay Java (it owns the `testWorkConstants` static-field proof and an `AutoDownloadManager` non-null-return constraint), left explicitly for the planner.
- [x] Plan (legacy-android-planner) — 22 Decisions, 7 Steps, 22 ACs. Convert all six files (D2: the `WORK_*` constant guard degrades from two proofs to one, not to zero — `DownloadServiceInterfaceImpl.java`'s unqualified inherited reads still guard it externally). `AutoDownloadManager`'s non-null `Future<*>` return substituted with `FutureTask<Void?> { null }` (D3), not a production-signature change.
- [x] Red-team plan (legacy-android-red-team) — loop 1 of max 2: **CHALLENGE** (1 MAJOR, 2 MINOR — all corrections to stated reasoning, not to the underlying decisions: D10's claim that `run { }` doesn't compile for the initializer-block fix was empirically false; two smaller D13/D2 reasoning errors). Planner's Revision 1 accepted all three, independently re-verified two against real stdlib sources. **Loop 2 of max 2 (final): CHALLENGE** on one new MAJOR — Step 4/AC7's prose was never updated to match D10's corrected `run{}` form, a plan-internal-consistency defect, not a correctness risk (both forms behaviorally proven identical). Red-team gave the exact two-line fix verbatim and did not request a third loop (budget exhausted; fix judged too small to warrant one). The planner agent hit a session limit before applying it; the orchestrating session applied the exact verbatim two-line correction directly (see task file's "Revision 1 addendum" after the Loop 2 verdict). **Plan stage is now closed.**
- [x] Implement (android-migration-developer) — all 7 Steps done, all 22 ACs machine-checked and pasted in Implementation Notes. All six files converted, 0 `.java` / 6 `.kt` in the test source set. 54/54 tests green on both flavors throughout, D15's per-file assertion diff empty for all 6 files (one genuine transcription defect — a non-breaking-space literal — caught and fixed by the diff at Step 2). `javap -v`/`-p` interop re-proof (D4/AC19) confirms all five `WORK_*` constants carry `ConstantValue:` on the outer class and all six `@JvmStatic` members/overloads are `public static`. One disclosed deviation: `DownloadRequestBuilderTest.kt` uses `checkNotNull(...)` instead of the Plan's assumed no-null-handling-needed shape, because D10's "stays a platform type" claim for `Bundle.getParcelableArrayList` was empirically false (real compile error) in this repo's Kotlin/AGP setup — `!!` is forbidden unconditionally by D16/AC15, so `checkNotNull` (endorsed by the `kotlin-j2k-style` skill's own item 1 for this exact shape) was used instead. README and future-work.md updated per D19/D5. Full detail in the task file's "## Implementation Notes".
- [x] Code review (migration-code-reviewer) — **loop 1 of max 3: APPROVE.** Independently re-verified rather than trusted: reverted the `checkNotNull` deviation to the Plan's literal assumed shape and reproduced the exact compile error, confirming it's compiler-forced, not a shortcut; byte-level (`od -An -tx1`) comparison of the non-breaking-space fix against merge-base Java, confirmed identical; read the flagged bad Step-3 commit's actual content and confirmed the fix-up commit and HEAD are correct; re-ran both flavors as genuinely separate `--rerun` invocations (54/54 each); independently reconstructed and ran the D15 assertion extractor (empty residual, all 6 files); independently ran `javap -v` for AC19; ran `:app:assembleDebug` (green) and confirmed `git diff` on `src/main/` empty for AC8; independently diffed the 54 `@Test` method names for AC4. One non-blocking MINOR: stale checkpoint "Resume command" text (fixed directly below).
- [ ] Red-team implementation (legacy-android-red-team)
- [ ] PR opened

## Decisions for next session
- Module: `:net:download:service-interface`. Track: `kotlin` only, test-only conversion. Milestone 13. Chosen over the other queued suite (`:net:sync:service-interface`, from Milestone 11) per José's explicit choice this session — that suite stays queued for a future milestone.
- Branch: `antennapod-net-download-service-interface-kotlin-milestone-13`, created off `origin/develop` (which already includes Milestone 12's merged PR #18).
- This is unaffiliated OSS portfolio work — see `services/android-migration/projects/portfolio/README.md`.
- **2026-08-04, live instruction:** José told the orchestrating (top-level) session directly, in conversation, to drive this milestone's pipeline through to an opened PR without pausing for a go-ahead between stages, matching Milestone 12's precedent. Each stage still applies its own full independent verification standard.

## Resume command
Implementation done, code review passed (loop 1, APPROVE). Next: invoke `legacy-android-red-team` to review the implementation (running code against the plan), then open the PR.
