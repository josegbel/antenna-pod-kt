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

## Resolved (kept for history)

- **Tier B Robolectric-free precedent (José, 2026-07-21):** `:model` unit tests must stay bare-JVM, no Robolectric — motivated `EmbeddedChapterImage`/`SubscriptionsFilter`/`FeedPreferences` (Milestone 5) using stdlib swaps instead of framework shims. See [[kmp-portability-over-robolectric-shims]]. Applies as the default for `:model`, but **not** to Tier C (see below).
- **Tier C Parcel characterization strategy → Robolectric (José, 2026-07-24).** Milestone 6 (`DownloadRequest`/`FeedMedia`/`RemoteMedia`/`Playable`) adds Robolectric to `:model`'s test deps specifically to exercise real `Parcel`/`Bundle` round-trips under plain JUnit — an explicit, disclosed exception to the Robolectric-free precedent above, scoped to Parcelable characterization tests only. Chosen over instrumented `androidTest` (slower, needs a device) and verified-by-inspection (not machine-checked). This reopens the KMP-portability tension the precedent was meant to avoid — see deferred initiative #1 above; if that decoupling work is ever picked up, Tier C's Robolectric dependency would need to be revisited/removed alongside it.
