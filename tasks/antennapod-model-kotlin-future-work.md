# antennapod-model-kotlin — Future Work

> **Repo:** `services/android-migration/projects/portfolio/antennapod`
> **Purpose:** Deferred initiatives and standing open questions surfaced during the `:model` kotlin-track milestones (1–5+), kept in one place instead of re-appearing piecemeal in each milestone's `## Open Questions` section. Nothing here is in scope for any milestone until it's explicitly pulled in by José.

## Deferred initiatives

### 1. Decouple `:model` from `android.os.Parcelable` (KMP portability)
**Raised:** 2026-07-24, during Milestone 6 scoping.
**Status:** Deferred — explicitly not folded into Milestone 6.

`DownloadRequest`, `FeedMedia`, `RemoteMedia`, and `Playable` (the last four `.java`/Tier C files in `:model`) implement `Parcelable` for real IPC use (Intents/Bundles into the download and playback services, `MediaBrowserCompat`). Removing that dependency from `:model` is not a language conversion — it's an architecture change: split each type into a plain Kotlin class in `:model` plus a `Parcelable` adapter/wrapper living in a module allowed to depend on the Android platform, mirroring the existing `service-interface`/`service` split pattern used elsewhere in this repo (see root `AGENTS.md`). It would require touching every call site across `:playback:service`, `:net:download:service`, `:app`, etc. that currently marshals these types via `Parcel`/`Intent`/`Bundle` directly — a much wider blast radius than the "convert N files, preserve API, characterize with tests" shape used in Milestones 1–5.

**Recommendation when picked up:** treat as its own initiative/track (closer to `di`/architecture than `kotlin`), not a milestone of the existing kotlin-conversion sequence. Needs its own research → plan → red-team pass, since it changes module boundaries.

### 2. Upstreaming intent (commercial/strategic)
**Raised:** 2026-07-19, Milestone 1 Open Questions (`tasks/antennapod-model-kotlin.md`).
**Status:** Standing, unresolved. Non-blocking for all milestones to date.

Is the plan to contribute these Kotlin conversions back to AntennaPod upstream, or keep them as an internal case-study fork? This shapes how nullability should be introduced (upstream-bound would favor landing `@Nullable`/`@NonNull` in Java first, as a smaller reviewable PR, before converting to Kotlin) and has licensing/attribution/public-positioning implications per the portfolio README and root `CLAUDE.md`'s commercial-implications rule. Has not blocked any milestone so far because nullability has been resolved per-file each time regardless of the answer.

### 3. `allWarningsAsErrors` for Kotlin test-compile tasks (cross-cutting build policy)
**Raised:** 2026-07-25, during Milestone 7 planning.
**Status:** Deferred — explicitly not folded into Milestone 7.

Once `:model`'s last Java test file converts to Kotlin, `compileDebugUnitTestJavaWithJavac` goes `NO-SOURCE` and `common.gradle`'s repo-wide `-Xlint:all -Werror` (applied to all `JavaCompile` tasks) stops covering `:model`'s tests, while `ktlintTestDebugSourceSetCheck` starts covering them instead — different concerns (compiler warnings vs. formatting), not a like-for-like replacement. Adding a Kotlin-compile equivalent (`allWarningsAsErrors` on the Kotlin test-compile task) would be a `common.gradle` change affecting every module's test compilation, not a `:model`-scoped one.

**Recommendation when picked up:** treat as a repo-wide build-policy decision, not a milestone of the kotlin-conversion sequence — needs its own scoping pass across all modules with Kotlin test sources, not just `:model`.

**Update, 2026-07-26 (`:event` kotlin milestone, Milestone 8):** the same effect now also applies to `:event`. Its production `compileDebugJavaWithJavac` goes `NO-SOURCE` as of Step 6 (23/23 files converted to Kotlin); its test source set is deliberately kept Java (D10, equivalence-oracle rationale — see `tasks/antennapod-event-kotlin.md`), so `-Xlint:all -Werror` still covers `:event`'s tests today. The gap only appears once `:event`'s tests are themselves converted to Kotlin (tracked as OQ2 / a prospective Milestone 9), at which point this same repo-wide question applies to `:event` too.

**Update, 2026-07-27 (`:event` kotlin milestone, Milestone 9):** 19 of `:event`'s 22 test files converted to Kotlin; `compileDebugUnitTestKotlin` now covers those. The gap is narrower here than on `:model`, though, not equal to it: 3 files (`PublicFieldInteropTest.java`, `MessageEventTest.java`, `FeedItemEventTest.java`) stay Java by design (see `event/README.md`'s intentionally-mixed-source-set convention), so `compileDebugUnitTestJavaWithJavac` keeps running and `-Xlint:all -Werror` keeps covering those three files' Kotlin-test-compile-uncovered surface. Only the 19 converted files fall into the same warnings-are-warnings-not-errors gap already described above for `:model` and for `:event`'s production code.

### 4. Stale Robolectric comment in `model/build.gradle`
**Raised:** 2026-07-25, during Milestone 7 planning.
**Status:** Deferred — one-line fix, not worth widening File Scope for.

The disclosure comment added in Milestone 6 says Robolectric is scoped to "this milestone's four files"; Milestone 7 research confirmed only **three** files (`DownloadRequestTest`, `FeedMediaTest`, `RemoteMediaTest`) actually use it — `EmbeddedChapterImageTest` uses `mockStatic(TextUtils)` instead. `model/README.md` already states the correct count; only the `build.gradle` comment is stale.

**Recommendation when picked up:** trivial one-line fix, bundle into whichever future `:model` milestone next touches `model/build.gradle` for an unrelated reason.

### 5. Orphaned checkstyle suppression entries naming test files
**Raised:** 2026-07-25, Milestone 7 red-team loop 1.
**Status:** Deferred — inert today, inert after; not worth widening File Scope to a repo-wide config file.

`config/checkstyle/suppressions.xml:14` (`LineLength`) names `VolumeAdaptionSettingTest.java` and `:15` (`VariableDeclarationUsageDistance`) names `FeedFilterTest.java`. Neither has ever applied: `common.gradle`'s `checkstyle` task sources only `src/main/java` (+ `src/free/java`, `src/play/java`) and never `src/test`, for any module. Milestone 7's `.java` → `.kt` rename leaves both entries permanently orphaned, matching filenames that no longer exist. No gate changes in either direction.

**Recommendation when picked up:** the same pattern will recur on **every** module test conversion, so handle it once, repo-wide, rather than per-milestone — and decide deliberately between deleting the dead entries and making them `.kt`-aware (a future checkstyle-scope change could make test suppressions live again). Editing these regex alternation groups is not risk-free: their other members are live production files, so a typo silently disables a real suppression.

**Update, 2026-07-26 (`:event` kotlin milestone, Milestone 8):** `config/checkstyle/suppressions.xml:16` (`WhitespaceAround`) names `SkipIntroEndingChangedEvent.java`, orphaned by that file's Step 4 conversion to `.kt` (`this.skipIntro= skipIntro;`, the line the suppression existed for, no longer exists). Same shape as the two `:model` entries above, same reasoning for leaving it alone (Plan Decision D20 in `tasks/antennapod-event-kotlin.md`) — one more data point for "handle once, repo-wide."

## Resolved (kept for history)

- **Tier B Robolectric-free precedent (José, 2026-07-21):** `:model` unit tests must stay bare-JVM, no Robolectric — motivated `EmbeddedChapterImage`/`SubscriptionsFilter`/`FeedPreferences` (Milestone 5) using stdlib swaps instead of framework shims. See [[kmp-portability-over-robolectric-shims]]. Applies as the default for `:model`, but **not** to Tier C (see below).
- **Tier C Parcel characterization strategy → Robolectric (José, 2026-07-24).** Milestone 6 (`DownloadRequest`/`FeedMedia`/`RemoteMedia`/`Playable`) adds Robolectric to `:model`'s test deps specifically to exercise real `Parcel`/`Bundle` round-trips under plain JUnit — an explicit, disclosed exception to the Robolectric-free precedent above, scoped to Parcelable characterization tests only. Chosen over instrumented `androidTest` (slower, needs a device) and verified-by-inspection (not machine-checked). This reopens the KMP-portability tension the precedent was meant to avoid — see deferred initiative #1 above; if that decoupling work is ever picked up, Tier C's Robolectric dependency would need to be revisited/removed alongside it.
