# antennapod-sync-settings-modernization — Future Work

> **Repo:** `services/android-migration/projects/portfolio/antennapod`
> **Purpose:** Records the milestone sequence for the Sync Settings screen (`ui:preferences/screen/synchronization/`) vertical-slice modernization, split into focused, single-track, independently-reviewable PRs — mirroring `tasks/antennapod-model-kotlin-future-work.md`'s role for the `:model` module. Nothing here is in scope for any milestone until it is scaffolded as its own `tasks/<slug>.md` and explicitly greenlit.

## Why split

The slice was originally scoped as one milestone (Milestone 15) covering `kotlin` + `di` + `concurrency` + `compose` at once — a full-stack modernization case study. Milestone 15's Research pass (`tasks/antennapod-sync-settings-kotlin-milestone-15.md`) surveyed the whole slice and, in doing so, surfaced enough independent complexity in each track (Hilt can't be confined to a library module; no ViewModel/MVVM layer exists yet, which blocks `compose`; `:storage:preferences` is a 5-module blast radius, not a local dependency; zero test coverage exists anywhere in the module) that one PR covering all of it would not be focal or reviewable. José agreed on 2026-08-06 to split it, matching this portfolio's established one-track-per-milestone pattern. Milestone 15 itself was narrowed to `kotlin` only as a result.

## Milestone sequence

Each row is a planned future milestone, not a commitment to its exact scope — the milestone that actually picks each one up should run its own Research pass and treat the notes below as a head start, not a substitute.

### Milestone 15 — `kotlin` (complete)
Converted the 4-file slice to Kotlin, stood up `ui:preferences`'s test source set from nothing with
38 characterization tests (26 executable behaviors from Research's 27-gap inventory, one source-level
grep pin for the unreachable gap, plus D5's harness-proof tests and the Java interop oracle), and
confirmed `AuthenticationDialog`'s two `:app` subclasses still compile unedited. `:storage:preferences`
stayed Java throughout (calling Java from Kotlin is not a blocker). See
`tasks/antennapod-sync-settings-kotlin-milestone-15.md` for the full record, including a materially
larger `!!` inventory than planned (49 across the slice, not the Plan's 37) discovered empirically
during conversion — every additional site is either a real preserved Java `NullPointerException`
(`Fragment.getContext()`/`getActivity()`/`getDialog()`/`getView()`, all unguarded in the original Java
too) or a Kotlin null-checker requirement for a value that is always non-null by construction (reading
a mutable `var` field in the same function it was just assigned in, which Java's lack of null-checking
let pass silently but Kotlin's smart-cast rules for mutable properties do not). `GpodderAuthenticationFragment`
had to become `open` to keep the frozen characterization suite's one test subclass compiling — the only
production-code consequence of any of this.

### Milestone 16 — toolchain/infra (`di` + `compose` + `concurrency` prerequisites)
Add to the version catalog and wire up, without touching the slice's behavior: Hilt (`hilt-android`, `hilt-android-compiler`, the Hilt Gradle plugin) plus a symbol-processing plugin (neither KSP nor KAPT exists in this repo today — pick one); `androidx.compose.material3:material3`, `androidx.compose.foundation:foundation`, `androidx.compose.ui:ui-tooling`/`ui-tooling-preview`, a Compose BOM; Paparazzi; `kotlinx-coroutines-core`/`-android` (and `-rx3` if Milestone 17 chooses incremental Rx bridging over a full replacement). Research also flagged a `kotlin-bom` version skew (pinned 1.9.24 against a 2.3.20 Kotlin plugin, applied repo-wide by `common.gradle`) that should be resolved before anything here is added, not after a resolution failure. **Open question carried from Research:** Hilt requires `@HiltAndroidApp` on the `Application` class and its plugin on `:app` — a library module cannot host it. "Scope minimally" cannot be satisfied literally; decide here whether a small `:app`-footprint Hilt setup is acceptable or whether a non-Hilt (manual constructor injection) approach better fits a portfolio case study's demonstration goal. Flagged in Milestone 15's Research as likely a José call, not a planner one, given the commercial/marketing angle.

### Milestone 17 — `concurrency` + MVVM
Replace the two orphaned RxJava3 subscriptions in `GpodderAuthenticationFragment` and the sticky-EventBus `SyncServiceEvent` subscription in `SynchronizationPreferencesFragment` with Coroutines + Flow. Stand up the ViewModel/UI-state layer this slice doesn't have today — Research found this is a **blocking prerequisite** for Milestone 20 (`compose`), not optional scope, since `SynchronizationPreferencesFragment` reads all state on demand from static globals with no state holder of any kind. `BugReportViewModel` (a different screen in the same module) is a useful shape reference, not a layer to build on. Must explicitly decide and record, per-behavior, whether each of the following is preserved or fixed (this repo's convention is pin-and-track known defects, not drive-by-fix): the two discarded RxJava `Disposable`s (structurally can't survive a move to `viewModelScope`/`lifecycleScope`, which cancel by construction — a desirable behavior change, not equivalence); the sticky-replay semantics of `SyncServiceEvent` (`StateFlow` or `SharedFlow(replay = 1)`, not `replay = 0`); the `devices` field data race in `GpodderAuthenticationFragment`. Also needs to check `:net:sync:service` for what posts/removes `SyncServiceEvent` stickily before finalizing the Flow shape — out of the Milestone 15 slice, not yet surveyed.

**Inherited from Milestone 15's `kotlin` conversion, this milestone's to clean up:** the 5 ActionBar
`(activity as AppCompatActivity?)!!.supportActionBar!!` sites in `SynchronizationPreferencesFragment`
disappear entirely once ActionBar handling moves to a ViewModel-backed host (D11 rule 4 in Milestone 15's
task file named this explicitly). More broadly, most of the slice's `!!` inventory (49 sites, see
Milestone 15's outcome note above) exists specifically because `:storage:preferences` statics and
`SynchronizationQueue.instance` are read directly with no DI seam and no state holder — once this
milestone's ViewModel owns that state, the majority of these forced assertions have a real path to
disappearing rather than merely being preserved. Do not treat "fewer `!!`" as an incidental style win
when it happens; it is evidence the underlying architectural problem (not just the syntax) got fixed.
**Gap 16, still open:** `error.getCause().getMessage()`'s converted form (`error.cause!!.message` in
`GpodderAuthenticationFragment.kt`) is pinned only by a grep on the emitted expression shape, not by an
executable test — reaching it needs either MockWebServer or the injectable `GpodnetService` this
milestone's DI work produces anyway. Close it here if convenient, or explicitly hand it to Milestone 18.

### Milestone 18 — `di` wiring
Wire the ViewModel (from Milestone 17) and a sync-settings repository abstraction over `:storage:preferences`'s statics through Hilt (infra from Milestone 16), replacing `ClientConfigurator`'s static-init-plus-mutable-global-singleton pattern for this slice specifically. `SynchronizationQueue.instance` (a mutable public `var` global today) should be bound as a provided dependency rather than read from the global.

**Milestone 15's characterization suite is this milestone's regression net.** Every test in
`ui/preferences/src/test/java/.../screen/synchronization/` reads and writes the static preference
globals and the `SynchronizationQueue.instance` global directly (by design — see `RecordingSynchronizationQueue`
and `D6` in Milestone 15's task file). When this milestone puts a repository/DI seam behind those globals,
this suite is what proves the seam preserves behavior. Treat it as the thing this milestone must keep
green throughout, not something to rewrite alongside the DI change — a suite that gets rewritten at the
same time it's supposed to be proving equivalence proves nothing.

### Milestone 19 — `:storage:preferences` (scope TBD, may not proceed)
Decide whether `SynchronizationSettings`/`SynchronizationCredentials` convert to Kotlin at all. Research found this is genuinely cross-module, not local: `SynchronizationSettings` has 10 call sites outside the slice across `:net:sync:service`, `:net:download:service`, `:app`, and this module's own `NotificationPreferencesFragment`; `SynchronizationCredentials.clear()` pulls in `UserPreferences.setGpodnetNotificationsEnabled()`, coupling into the module's largest Java class; and `:storage:preferences` has no test source set at all, so this milestone carries its own characterization-test bill before any conversion. A smaller alternative that satisfies Milestone 18's DI seam without a full conversion: wrap the existing Java statics behind an injectable Kotlin interface and leave `:storage:preferences` itself in Java, same interop pattern used throughout this portfolio (Kotlin calling Java is not a hazard). Recommend deciding this on its own merits rather than as a default "finish the migration" move.

### Milestone 20 — `compose`
Replace `PreferenceFragmentCompat` + `preferences_synchronization.xml` with a `ComposeView` hosting real Compose content (locked decision — not an `AndroidView` wrapper around the existing XML). Requires Milestones 16 (toolchain) and 17 (ViewModel) done first. Open questions Research flagged, not yet answered:
- **Settings search stays working or is explicitly degraded.** `MainPreferencesFragment` indexes `preferences_synchronization.xml` for search, and `PreferenceActivity.onSearchResultClicked` needs a live `PreferenceFragmentCompat` to highlight the matched row. All three fix options (keep the XML as index-only and accept degraded highlighting; change `openScreen`'s signature across 9 `:app` call sites; special-case this screen in `PreferenceActivity`) touch files outside the slice.
- **Do the 3 dialogs move to Compose too, or only the top-level screen?** `GpodderAuthenticationFragment` (4-step `ViewFlipper` wizard, 5 XML layouts) is the largest UI rewrite in the slice if included; `AuthenticationDialog` is subclassed from `:app` by two Java classes, so rewriting it changes an external API. Not decided by the locked hosting decision, which only names "the new screen."
- **Theming.** The app resolves one of 6 View themes at runtime (Light/Dark/TrueBlack × dynamic/non-dynamic wallpaper-derived color) via `ThemeSwitcher`; there's no existing View-Material3 → Compose `MaterialTheme` bridge in the repo. Decide which theme variants get Paparazzi-snapshotted and why — snapshotting all 6 against a Compose `ColorScheme` that can't reproduce wallpaper-derived dynamic color isn't meaningful.
- **Before/after screenshot mechanism.** Paparazzi is not in the catalog yet (added in Milestone 16) and `paparazzi.snapshot()` renders a `View` — whether it can render a `PreferenceFragmentCompat`'s `RecyclerView`-backed inflation (the "before" side, if not already captured in an earlier milestone) is unproven in this repo. Alternatives: manual `adb exec-out screencap` checked in as a PNG (zero new tooling, not CI-reproducible, not gated by an AC), or the module's first `androidTest` source set. Whichever is chosen, both before and after images must land in File Scope and Acceptance Criteria, not be treated as incidental test output.

## Standing conventions carried into every milestone in this sequence

- Known pre-existing defects (the RxJava disposables, the `fromIdentifier`/`switch` NPE, the `devices` data race) are pinned by a test and tracked, not drive-by-fixed, per this repo's established convention (`net/sync/service-interface/README.md` convention #11).
- `:ui:preferences` applies `playFlavor.gradle`, so its test tasks are flavoured (`testFreeDebugUnitTest`/`testPlayDebugUnitTest`) — do not copy unflavoured task names from `:net:sync:service-interface`, whose own README warns against the reverse mistake.
- This is unaffiliated OSS portfolio case-study work — see `services/android-migration/projects/portfolio/README.md`.
