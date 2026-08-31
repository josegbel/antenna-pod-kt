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

### Milestone 16 — toolchain/infra (`di` + `compose` + `concurrency` prerequisites) — complete, shipped without Paparazzi
Catalogued and wired, without touching the slice's behavior: Hilt 2.58 (stepped down from the newer 2.59+ line, which requires AGP 9.0.0 — this repo is on 8.11.0) at `:app` with `@HiltAndroidApp` on `PodcastApp`; KSP 2.3.11 catalogued and root-declared but applied to no module (its Kotlin-2.3.20 pairing proven by a reverted probe); the Compose BOM `2026.06.01` (the newest train this repo's `compileSdk 36`/AGP 8.11.0 can accept — `2026.08.00`'s Compose UI 1.12.0 requires `compileSdk 37`/AGP 9.1.0) wired into `:ui:preferences`; `kotlinx-coroutines-core`/`-android` at 1.9.0, wired into `:ui:preferences` and re-pointing `kotlinx-coroutines-play-services` at the same ref. **Paparazzi did not ship** — see the dedicated finding under Milestone 20 below; this is the one open item Milestone 16's own scope named that a future attempt should revisit with different information than Milestone 16 had.

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

**Outcome (done, PR pending):** both RxJava call sites converted to `lifecycleScope.launch` +
`withContext(ioDispatcher)`; the sticky `SyncServiceEvent` subscription replaced by a
`callbackFlow`/`stateIn(WhileSubscribed(0,0))` bridge in a new `SynchronizationPreferencesViewModel`
scoped to `SynchronizationPreferencesFragment` only. Per-behavior preserve/fix: the two discarded
`Disposable`s → **FIXED** (structurally forced by `lifecycleScope`); the `devices` race → **PRESERVED**
(`@Volatile` count stays 3); sticky-replay → **PRESERVED** (`StateFlow<SyncServiceEvent?>(null)`); Gap 16 →
**PRESERVED**, now pinned executably (`testWrongPasswordErrorPathThrowsFromNullCause`) and handed to
Milestone 18 by name (below). Slice `!!` 49 → 41 (the five ActionBar statements collapsed to one
`actionBar()` helper). `kotlinx-coroutines-test` deliberately not catalogued (see Milestone 18). The
Gpodder wizard fields (`currentStep`/`devices`/`username`/`password`/`selectedDevice`) stayed on the
fragment — six characterization tests reach them by reflected name — so the MVVM layer after this
milestone covers **one of the slice's four files, and within it only the ActionBar and the sync event**,
not the screen body. What Milestone 20 still owns is spelled out in its section below.

### Milestone 18 — `di` wiring
Wire the ViewModel (from Milestone 17) and a sync-settings repository abstraction over `:storage:preferences`'s statics through Hilt (infra from Milestone 16), replacing `ClientConfigurator`'s static-init-plus-mutable-global-singleton pattern for this slice specifically. `SynchronizationQueue.instance` (a mutable public `var` global today) should be bound as a provided dependency rather than read from the global.

**Inherited from Milestone 17, by name:**
- **Gap 16 ships preserved and is Milestone 18's to fix.** `GpodderAuthenticationFragment.kt`'s login
  error handler still reads `error.cause!!.message`, which NPEs and crashes the app on the most common
  gpodder.net login failure (`GpodnetServiceAuthenticationException("Wrong username or password")` carries
  no cause). Milestone 17 pinned it executably (`testWrongPasswordErrorPathThrowsFromNullCause`) and
  deliberately did not fix it — a crash fix inside an equivalence-proving diff makes the proof unreadable.
  The one-line fix is `error.cause?.message ?: error.message`; with Milestone 18's injected `GpodnetService`
  it can be asserted through the real failure path rather than a reflected fake. When fixed: invert that
  test to assert the rendered message, and update Milestone 15's `AC13` grep pin and
  `ui/preferences/README.md` convention 4. José's OQ1 (ship it preserved for one more milestone vs fix now)
  is still open and non-blocking.
- **`kotlinx-coroutines-test` becomes genuinely necessary here.** Milestone 17 kept every coroutine
  single-threaded and testable without it. Once a `SynchronizationPreferencesViewModel` read becomes
  `suspend` behind an injected repository, `runTest`/`TestDispatcher` is the right tool — catalogue it as
  Milestone 18 toolchain work (same for Turbine if a flow-assertion library is wanted).
- **The five `SynchronizationQueue.instance!!` sites stay for Milestone 18.** Three in
  `SynchronizationPreferencesFragment.kt` (the sync / force-full-sync / logout preference-row listeners),
  two in `GpodderAuthenticationFragment.kt` (host step, finish step). Milestone 17's ViewModel takes no
  constructor dependencies on purpose — taking `SynchronizationQueue` there would define the DI seam this
  milestone owns, and would give `RecordingSynchronizationQueue` (installed via the global by every test in
  the suite) a second injection path.

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
Replace `PreferenceFragmentCompat` + `preferences_synchronization.xml` with a `ComposeView` hosting real Compose content (locked decision — not an `AndroidView` wrapper around the existing XML). Requires Milestones 16 (toolchain) and 17 (ViewModel) done first.

**What Milestone 17 delivered vs. what Milestone 20 still owns.** Milestone 17 handed over the class
(`SynchronizationPreferencesViewModel`), the `StateFlow`-of-immutable-`data class` pattern, the collection
seam and the `ui/preferences/README.md` conventions that fix that pattern in place — so `compose` does not
have to invent an architecture first. It did **not** hand over the screen's state model: the ViewModel
holds only the ActionBar title/subtitle (`SyncSettingsUiState`) and the sync event. `updateScreen()` and
everything it renders — `isProviderConnected()`, the provider summary and icon, username/host-url, per-row
enabled/visible state — still live in the fragment, read on demand from `:storage:preferences` statics.
**Hoisting that into `SyncSettingsUiState` is Milestone 20's work.** Also: the three auth dialogs
(`GpodderAuthenticationFragment`, `NextcloudAuthenticationFragment`, `AuthenticationDialog`) have **no**
ViewModel — the Gpodder wizard's `currentStep`/`devices`/`username`/`password`/`selectedDevice` stayed
fragment fields because six characterization tests reach them by reflected name. If Milestone 20 decides
the dialogs move to Compose, a Gpodder-wizard ViewModel is **that milestone's scope**, and its
characterization-suite rewrite is forced there by the UI rewrite rather than colliding with an equivalence
proof.

Open questions Research flagged, not yet answered:
- **Settings search stays working or is explicitly degraded.** `MainPreferencesFragment` indexes `preferences_synchronization.xml` for search, and `PreferenceActivity.onSearchResultClicked` needs a live `PreferenceFragmentCompat` to highlight the matched row. All three fix options (keep the XML as index-only and accept degraded highlighting; change `openScreen`'s signature across 9 `:app` call sites; special-case this screen in `PreferenceActivity`) touch files outside the slice.
- **Do the 3 dialogs move to Compose too, or only the top-level screen?** `GpodderAuthenticationFragment` (4-step `ViewFlipper` wizard, 5 XML layouts) is the largest UI rewrite in the slice if included; `AuthenticationDialog` is subclassed from `:app` by two Java classes, so rewriting it changes an external API. Not decided by the locked hosting decision, which only names "the new screen."
- **Theming.** The app resolves one of 6 View themes at runtime (Light/Dark/TrueBlack × dynamic/non-dynamic wallpaper-derived color) via `ThemeSwitcher`; there's no existing View-Material3 → Compose `MaterialTheme` bridge in the repo. Decide which theme variants get Paparazzi-snapshotted and why — snapshotting all 6 against a Compose `ColorScheme` that can't reproduce wallpaper-derived dynamic color isn't meaningful.
- **Before/after screenshot mechanism — Paparazzi was attempted in Milestone 16 and did not ship. Read the specific failure before re-attempting it, and try something different, not the same ladder again.** Milestone 16 catalogued `app.cash.paparazzi` `2.0.0-alpha05` (the only release that targets this stack's AGP 8.11.0/Kotlin 2.3.20 — no stable Paparazzi release does) and declared it `apply false` at root, per its own Step 10. That alone — before any module applied the plugin — broke dependency resolution repo-wide: `2.0.0-alpha05`'s Gradle plugin pulls `com.android.tools:sdk-common:31.13.2` onto the root buildscript classpath, which wins conflict resolution over AGP 8.11.0's own `sdk-common:31.11.0` and then requests `com.android.tools.build:aapt2:8.13.2-12782657` — a build id that was **never published** (confirmed via direct Maven lookup: only `com.android.tools.build:aapt2-proto:8.13.2-14304508` exists at that version; the native `aapt2` artifact under that exact qualifier does not). This broke `:event`'s `compileDebugLibraryResources` task — a module with zero relationship to `:ui:preferences` or Paparazzi — the moment the plugin marker was declared, reproduced with `--refresh-dependencies` to rule out a stale cache. **This is a different failure than Milestone 16's own remedy ladder (D6: `forkEvery`, `robolectric.properties sdk=34`, a separate Gradle module, Robolectric `4.16.1`) was built for** — that ladder addresses the Robolectric/layoutlib JVM-sharing conflict at test-runtime (cashapp/paparazzi#1979), which is a completely different mechanism from a buildscript-classpath artifact that was never published. None of the ladder's four rungs touch buildscript resolution, so climbing it again would not help. **What a future attempt should check instead:** (a) whether a later `2.0.0` release (still alpha-only as of Milestone 16, 2026-08) has fixed its own transitive `sdk-common` pin — re-read `paparazzi-gradle-plugin-<version>.pom`'s `sdk-common` dependency and confirm the `aapt2`/`aapt2-proto` build ids it requests actually exist on Maven Central *before* declaring the plugin, the same way Milestone 16's D2 verified Hilt's and KSP's pins; (b) whether Paparazzi ever reaches a stable release built against this stack's AGP line, which would likely resolve a coherent `sdk-common`; (c) only as a last resort, whether forcing `com.android.tools:sdk-common` and `com.android.tools.build:aapt2`/`aapt2-proto` to AGP 8.11.0's own versions via a `resolutionStrategy` in root `build.gradle` unblocks Paparazzi without breaking its own rendering — untried and unverified, flagged as a possible but unauthorized-in-Milestone-16 workaround, not a recommendation. Milestone 15b already demonstrated a working, zero-new-tooling screenshot mechanism in this exact module using Robolectric `@GraphicsMode(NATIVE)` (see `ui/preferences/README.md` convention 9) — that remains the fallback if Paparazzi is still unusable when this milestone starts. Whichever mechanism is chosen, both before and after images must land in File Scope and Acceptance Criteria, not be treated as incidental test output.

## Standing conventions carried into every milestone in this sequence

- Known pre-existing defects (the RxJava disposables, the `fromIdentifier`/`switch` NPE, the `devices` data race) are pinned by a test and tracked, not drive-by-fixed, per this repo's established convention (`net/sync/service-interface/README.md` convention #11).
- `:ui:preferences` applies `playFlavor.gradle`, so its test tasks are flavoured (`testFreeDebugUnitTest`/`testPlayDebugUnitTest`) — do not copy unflavoured task names from `:net:sync:service-interface`, whose own README warns against the reverse mistake.
- This is unaffiliated OSS portfolio case-study work — see `services/android-migration/projects/portfolio/README.md`.
