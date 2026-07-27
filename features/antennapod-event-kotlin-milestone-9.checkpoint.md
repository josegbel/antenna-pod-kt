# Checkpoint — antennapod-event-kotlin-milestone-9

> **Repo:** `antennapod`
> **Task file:** `tasks/antennapod-event-kotlin-milestone-9.md`

## Status
**IMPLEMENTED — 19/22 test files converted to Kotlin, 95 tests green, 4 tiered commits on branch. Ready for code review.**

## Last updated
2026-07-27

## Lifecycle progress
- [x] Research (legacy-android-researcher)
- [x] Plan (legacy-android-planner)
- [x] Red-team plan (legacy-android-red-team) — Loop 1: CHALLENGE (1 MAJOR + 2 MINOR), all addressed by planner revision. **Loop 2 (final): APPROVE.** All three loop-1 findings independently re-verified against live source (not just trusted from the revision note); one cosmetic wording nit noted, non-blocking, no third loop.
- [x] Implement (android-migration-developer) — 2026-07-27. 19/22 files converted across 4 risk-tiered commits (Tier A 13 files, Tier B 3 files, Tier C 2 files, Tier D 1 file) plus a docs commit for Step 7. 3 files kept Java, byte-for-byte unchanged (`git diff` against merge-base confirmed empty at every step). 95 tests green before and after every step. D10 assertion-content diff empty for 17/19 files; 2 files (`QueueEventTest`, `EventIdentityEqualityTest`) carry disclosed, individually-justified pure-syntax residuals. One disclosed deviation: `BufferUpdateEventTest` needed an explicit `import java.lang.Float` (kotlin.Float has no callable `isNaN(x)` static form; D9 forbade the idiomatic `.isNaN()` rewrite). `javap -p` re-proof of the `@JvmField`/`@JvmStatic` contract matches all required shapes. Full Implementation Notes in the task file.
- [ ] Code review (migration-code-reviewer)
- [ ] Red-team implementation (legacy-android-red-team)
- [ ] PR opened

## Decisions for next session
- Module: `:event`. Track: `kotlin`, test-file-only scope. Gated on Milestone 8 (production code) being 100% Kotlin and merged — confirmed true, PR #14 merged, local `develop` fast-forwarded to `origin/develop` at the start of this session.
- Mirrors the `:model` Milestone 7 precedent exactly (same trigger condition, same "test-only, keep behavior identical, only change language" shape).
- Per explicit instruction this session: run the full lifecycle autonomously (no human intervention between stages) and ship as a single unified PR (code + spec docs together).

## Plan summary (2026-07-26)
- **Scope: 19 of 22 test files convert to Kotlin. 3 stay Java permanently, by design (Plan D2).** Kept Java, byte-for-byte unchanged: `PublicFieldInteropTest.java` (8 tests, the `@JvmField` oracle), `MessageEventTest.java` (3 tests, `accept(null)` is a Kotlin compile error), `FeedItemEventTest.java` (7 tests, `new FeedItemEvent(null, false)` is inexpressible in Kotlin). Bright line: *a test whose oracle is "javac accepts this call shape" cannot be hosted in Kotlin at any price.*
- Planning measurement behind the residual-erosion question (Plan D3): **all 17 `@JvmStatic` members and all 12 `@JvmField` fields have live external Java call sites** outside `:event` (2–14 each), all compiled by `:app:assembleDebug` in CI. Post-loop-1 wording: this establishes the conversion is **not a coverage regression today** — the guard is *contingent* on Java callers the repo's own migration is removing, so it is not a claim that a local signal is worthless. What decides each file is instead *what it still asserts after conversion*. No compensating test is added; the contract is re-proven mechanically by re-running M8's `javap` check as AC12.
- 4 risk-tiered conversion commits: Tier A 13 mechanical files / Tier B 3 accessor-dense / Tier C 2 overload+mutability / Tier D `EventIdentityEqualityTest` alone.
- Named hard-stops for the developer: `Integer.toHexString` stays verbatim (never `toString(16)`); `.equals()` never becomes `==`; `FeedListUpdateEvent(0)` never gains an `L`; `Arrays.asList` never becomes `listOf` and its locals get no explicit type annotation.
- 18 acceptance criteria; the binding one is AC8 — an **empty** assertion-content diff for all 19 converted files, with no disclosed exceptions, re-run by the reviewer.
- 5 Open Questions logged, none blocking. OQ1 (promote the Java-canary rule into the shared service-line agents) and OQ4 (upstreaming intent) are flagged for José.

## Red-team loop 1 resolution (planner, 2026-07-26)
All three findings addressed in the Plan's revision note. **No Decision, Step, File Scope entry or Acceptance Criterion changed substance** — the 19/3 split, four-tier commit structure and AC1–AC18 stand, as red-team independently confirmed.

- **MAJOR (D2/D3 asymmetry) — fixed, and the objection was correct.** D3 now (a) concedes its claim was narrower than written: it shows the conversion is *not a coverage regression today*, and explicitly flags that `:app:assembleDebug`'s guard is **contingent** on Java callers the repo's own Kotlin migration is actively removing — not that a fast local signal has no value; and (b) replaces the redundancy argument with the real decisive variable, *what does the file still assert after conversion?*, which is D2's bright line applied consistently. New evidence found while fixing: `PublicFieldInteropTest`'s behavioral assertions are **assertion-for-assertion identical** to ones already in `FeedUpdateRunningEventTest`, `PlaybackServiceEventTest`, `FeedEventTest` and `QueueEventTest` (table in D3), so converting it retains *zero* unique value — the real choice is not "lose a redundant guard" but "gain 8 green tautologies that read as coverage." The 19 files, by contrast, lose only an unintended side effect and keep 100% of their intended content.
- **MINOR (canary-helper variant) — fixed.** D2 names and rejects it on AGENTS.md minimal-diff grounds plus a fidelity argument (a helper's `constructNull()` is one call site written to be called; the kept file reads fields the way a real consumer does).
- **MINOR (D4 rigor) — fixed by verifying rather than hedging.** `model/.../SortOrder.kt:56` already calls `Integer.toString(...)` unqualified in compiling production Kotlin in this repo — same class, same static-call shape, no `kotlinc` needed. Stronger than the suggested doc citation.
- **OQ3 reframed** as a consequence of the MAJOR fix: it previously asked only about the 15 uncovered `@JvmStatic` members; it now covers the `@JvmField` case and the contract-decay scenario D3's concession exposes, with a table of what is and isn't guarded after M9.

## Red-team loop 2 resolution (legacy-android-red-team, 2026-07-26, FINAL)
**APPROVE.** Independently re-verified all three loop-1 fixes against live source rather than trusting the revision note:
- Read all four claimed duplicate files (`FeedUpdateRunningEventTest`, `PlaybackServiceEventTest`, `FeedEventTest`, `QueueEventTest`) and diffed each of `PublicFieldInteropTest`'s 8 tests by hand — all 8 have a genuine behavioral duplicate; the "assertion-for-assertion identical" claim holds. One cosmetic nit: 3 of the 8 duplicates live in `FeedItemEventTest`/`MessageEventTest`, which are *also* kept Java (not "converting in Step 2" as the summary prose implies) — the plan's own table already states this correctly, so it's a wording-only issue, not a factual error, and if anything understates the redundancy argument's strength for those 3. Non-blocking.
- Verified `model/.../SortOrder.kt:56` and the five `System.currentTimeMillis()` citations directly — all exactly as cited, confirming D4's in-repo precedent is real and accurate.
- Confirmed the canary-helper rejection and the OQ3 reframing are both substantively present and correctly scoped.
- Confirmed Steps, File Scope, and Acceptance Criteria are unchanged in substance, as the revision note claimed.

No third loop needed — the one residual item is cosmetic and doesn't touch any Decision/Step/AC. Full verdict: `## Red-Team Verdict — Plan` (Loop 2, FINAL) in the task file.

## Resume command
**Cleared for implementation.** Invoke `android-migration-developer` on `tasks/antennapod-event-kotlin-milestone-9.md` — Steps 1–7, File Scope, and AC1–AC18 are final. Standing instruction: run autonomously and ship as a single unified PR (code + spec docs together), per the M7/M8 precedent. After implementation: `migration-code-reviewer` (max 3 loops), then `legacy-android-red-team` again for the Implementation-mode review (separate from the Plan-mode review just completed).
