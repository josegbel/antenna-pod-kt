# antennapod-sync-settings-before-screenshot-milestone-15b

> **Description:** Capture an automated, checked-in "before" screenshot of the Sync Settings screen's current View-based UI, for the eventual before/after comparison in Milestone 20 (`compose`). No production code changes — test/tooling only.
> **Repo:** `services/android-migration/projects/portfolio/antennapod`
> **Created:** 2026-08-06

> **Pre-research context (carried over from the parent planning conversation — do not re-derive):**
> - **Why now, and why this isn't "the Java before-screenshot."** Milestone 15 (`kotlin` track, PR #21, not yet merged) converted the Sync Settings screen from Java to Kotlin behavior-and-visually unchanged — its own Plan/ACs required no rendered-output change. So this screenshot, captured after Milestone 15, is visually identical to what the original Java UI looked like; it's still the correct "before" baseline for Milestone 20's Compose comparison, just captured post-conversion rather than pre-conversion. State this explicitly wherever the screenshot is referenced later so nobody assumes it predates Milestone 15.
> - **Branching update, 2026-08-06: PR #21 has merged into `develop`** (merge commit `f5d4c5551`). The stacked-branch constraint below is now moot — branch fresh from `origin/develop` as normal (verify via `git fetch origin` + ancestry check first, per this portfolio's now-standard practice after Milestone 15's D1 caught a stale local `develop`). *Original text, kept for context on why this was ever a concern:* ~~this is a stacked branch, not a fresh one from `develop`. The mechanism this task relies on only exists on Milestone 15's unmerged branch `kotlin/ui-preferences-sync-settings` (PR #21) — `develop` doesn't have it yet.~~
> - **The capture mechanism, already found and decided in the parent conversation — do not re-litigate the "Paparazzi vs. manual `adb`" framing from Milestone 15's Research/Open Questions.** Milestone 15 built `ui/preferences/src/test/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/SyncSettingsTestHost.kt`, an `AppCompatActivity` test host, and proved under Robolectric (`SyncSettingsHarnessSmokeTest.kt`) that it can attach `SynchronizationPreferencesFragment` to a real activity and resolve its actual inflated `PreferenceScreen` — this is exactly the "real fragment lifecycle" piece Milestone 15's Research found missing for a Paparazzi-only approach. Robolectric 4.16 (`gradle/libs.versions.toml:77`, already in the catalog) supports native graphics rendering, meaning a real `Bitmap` can be drawn from the fragment's actual measured/laid-out root view inside a plain JVM unit test — no emulator, no `adb`, and Paparazzi (not in the catalog until Milestone 16) is not required for this. This reuses infrastructure Milestone 15 already built and paid for; it is not new toolchain investment.
> - **Real decisions still open for this task's own planning (do not assume answers):**
>   1. Where the checked-in PNG artifact lives in the repo (no existing convention found yet — check `services/android-migration/projects/portfolio/README.md` and the repo root for any existing screenshot/marketing-asset location before inventing one).
>   2. Whether the capture test should run as part of the module's normal `test` task (risk: re-generates/could silently diff the checked-in artifact on every CI run, which is the wrong behavior for a frozen historical baseline) or should be isolated/excluded from the default task graph and run manually/on-demand only. Recommend the latter, but this is a real decision, not a given.
>   3. Whether the capture code (the harness extension that draws-to-`Bitmap`-and-writes-PNG) stays in the tree as reusable infrastructure for Milestone 20's "after" capture (recommended, since it's the same rendering approach and having both captured through the identical path is more comparable), or is a one-off script removed after this task generates its artifact.
>   4. What screen size / density / theme variant to render at. Milestone 15's Research found the app resolves one of 6 theme permutations at runtime (Light/Dark/TrueBlack × dynamic/non-dynamic); a single "before" reference screenshot doesn't need all 6, but which one (and why) should be a stated decision, not a default.
> - This is unaffiliated OSS portfolio case-study work — see `services/android-migration/projects/portfolio/README.md`.
> - José agreed to this approach (reusing Milestone 15's harness instead of a manual `adb` capture) on 2026-08-06 and asked for it to be done now rather than deferred to Milestone 20, with the explicit condition that it land as a separate follow-up PR rather than added to the already-open, already-reviewed PR #21.

## Research
_Last updated by: legacy-android-researcher | 2026-08-06_

### Summary

`:ui:preferences`' `screen/synchronization/` slice is, as of Milestone 15, four Kotlin production files
(`AuthenticationDialog`, `GpodderAuthenticationFragment`, `NextcloudAuthenticationFragment`,
`SynchronizationPreferencesFragment`) plus the module's first-ever test source set: 38 Robolectric JVM
unit tests across 7 files, including the `SyncSettingsTestHost` activity harness this task depends on.
The screen itself is a `PreferenceFragmentCompat` (via `AnimatedPreferenceFragment`) inflating a
5-entry `PreferenceScreen` from `res/xml/preferences_synchronization.xml`, hosted in a `RecyclerView`.
There is no ViewModel, no data binding, and no Compose anywhere in the module.

This task adds **no production code and no new track**. It is test/tooling-only work in service of the
future `compose` track (Milestone 20): capture a deterministic, checked-in PNG of the current View-based
rendering so a later Compose rewrite has a visual baseline. The empirical question — does Robolectric
4.16 actually produce real pixels here — is answered below: **yes, but only under
`@GraphicsMode(GraphicsMode.Mode.NATIVE)`; the default LEGACY mode silently produces a solid blank
fill that still passes any naive "bitmap is non-null / non-zero" assertion.** That silent-blank failure
mode is the single most important finding in this document.

### Findings

#### Existing surface

Production (all Kotlin as of Milestone 15, `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/`):
- `SynchronizationPreferencesFragment.kt:30` — extends `AnimatedPreferenceFragment`; inflates
  `R.xml.preferences_synchronization` and drives all five rows through `updateScreen()`
  (`SynchronizationPreferencesFragment.kt:111-147`), branching on
  `SynchronizationSettings.isProviderConnected()`.
- `AnimatedPreferenceFragment.java:23-26` — sets the fragment root's background to
  `?attr/colorSurface` in `onViewCreated`, so the captured root view has an opaque background rather
  than transparency. It also installs four `MaterialSharedAxis` transitions
  (`AnimatedPreferenceFragment.java:16-19`) — relevant only in that nothing in a `commitNow()`
  attach triggers them, so they do not perturb a static capture.
- `AuthenticationDialog.kt`, `GpodderAuthenticationFragment.kt`, `NextcloudAuthenticationFragment.kt` —
  dialogs reachable *from* this screen, not part of its own rendered surface. Out of scope for a
  "before" screenshot of the settings screen itself.

Test-only infrastructure (`ui/preferences/src/test/java/.../screen/synchronization/`):
- `SyncSettingsTestHost.kt:7-12` — a bare `AppCompatActivity` that calls
  `setTheme(CommonR.style.Theme_AntennaPod_Light)` **before** `super.onCreate`. This is the entire
  harness; it has no layout and no content view of its own. Fragments are added directly to
  `android.R.id.content`.
- `RecordingSynchronizationQueue.kt` — a `SynchronizationQueue` test double; required because
  `setupScreen()` wires click listeners that dereference `SynchronizationQueue.instance!!`
  (`SynchronizationPreferencesFragment.kt:92-102`).
- `SyncSettingsHarnessSmokeTest.kt:29-36` — the canonical `setUp()` every test in the slice copies.

Module build config:
- `ui/preferences/build.gradle:6-7` applies both `../../common.gradle` and `../../playFlavor.gradle`.
- `ui/preferences/build.gradle:54-61` — test deps are `junit`, `robolectric`, `androidx.test.core`,
  and `project(':model')`. No screenshot/imaging library of any kind.

#### Java/Kotlin interop boundary

Because this task is test-only, the interop boundary is **unchanged** — but two existing constraints
bound what the capture code may touch:

- `SyncSettingsTestHost` exists only under `src/test/`. Per `ui/preferences/README.md:51-53`, it must
  never be declared in `src/main`'s manifest or a `src/debug` manifest, which would ship it in the app.
  Any capture code must live in the same test source set, not be promoted to `main` or `debug` to make
  it "reusable".
- `:storage:preferences` (`SynchronizationSettings`, `SynchronizationCredentials`) is still Java with
  no nullability annotations (`ui/preferences/README.md:8-12`). The capture path reads these only
  indirectly, through `updateScreen()`, so it inherits the existing behaviour rather than adding a new
  boundary crossing.
- No public API of `:ui:preferences` changes. Nothing outside the module can observe this task.

#### Current test coverage

Verified empirically by running `./gradlew :ui:preferences:testPlayDebugUnitTest --rerun-tasks`:
**38 tests, all PASSED, ~16s wall clock.** Breakdown by file:

| File | Tests | What it actually asserts |
|---|---|---|
| `SyncSettingsHarnessSmokeTest.kt` | 6 | Host activity builds; fragment attaches and all 5 preference keys resolve; dialogs are retrievable via `ShadowDialog`; `AntennapodHttpClient` throws NPE under Robolectric |
| `SynchronizationPreferencesFragmentCharacterizationTest.kt` | 8 | Logged-out and logged-in screen state; gpodnet row visibility; logout ordering/side-effects; HTML-spanned summary; unrecognised-provider crash |
| `GpodderAuthenticationFragmentCharacterizationTest.kt` | 9 | Wizard step transitions, credential commit ordering, device-name dedupe, locale handling |
| `NextcloudAuthenticationFragmentCharacterizationTest.kt` | 6 | Login-flow cancellation, config change survival, deferred dismiss |
| `SynchronizationPreferencesFragmentLifecycleTest.kt` | 4 | `onStart`/`onStop` action-bar title/subtitle; sticky event replay |
| `AuthenticationDialogCharacterizationTest.kt` | 4 | Field enable state, password toggle transformation/alpha, cancel callbacks |
| `AuthenticationDialogJavaInteropTest.java` | 1 | Java subclass override shapes |

**Every one of these asserts on object state — `Preference.title`, `isEnabled`, `isVisible`, adapter
contents, call ordering. Not one asserts anything about rendered pixels, view geometry, measured
size, or layout.** There is zero rendering coverage in this module today.

#### Characterization-test gaps

For this task specifically the gap is narrow, because the task adds no production behaviour. The
honest gaps are:

1. **No test anywhere in the repo exercises `View.draw(Canvas)` under Robolectric.** Grepping for
   `GraphicsMode`, `createBitmap`, and `CompressFormat` across all module test sources returns nothing.
   This task would be the first. There is no existing pattern to copy and no existing regression
   safety net if Robolectric's rendering behaviour changes on a version bump.
2. **No test pins the *rendered* logged-out state.** `testLoggedOutScreenState`
   (`SynchronizationPreferencesFragmentCharacterizationTest.kt:68-91`) pins the logical state
   thoroughly, but a screenshot can be blank, clipped, or missing rows while every one of those
   assertions still passes. If the capture is to be trusted as a baseline, the capture test needs its
   own assertions on the produced bitmap (non-trivial distinct-colour count at minimum) — writing the
   PNG and asserting nothing is the failure mode to avoid.
3. **Cross-machine determinism is unverified.** Two consecutive local runs produced a byte-identical
   PNG (md5 `170c151660a80bdd62949e844f41f1a1`), so the render is deterministic on one machine/JDK.
   Whether a Linux CI runner with a different JDK produces the same bytes was **not** tested and
   should not be assumed. This directly bears on Unknown 3 below.

#### Track-specific findings

This task does not itself run a migration track. The relevant track is `compose` (Milestone 20), which
this is preparatory work for.

`compose`:
- **No ViewModel/MVVM layer exists in this module.** `SynchronizationPreferencesFragment` reads and
  writes `SynchronizationSettings`/`SynchronizationCredentials` statics directly from the fragment
  (`SynchronizationPreferencesFragment.kt:112, 130-146`) and receives updates via a sticky EventBus
  subscription (`SynchronizationPreferencesFragment.kt:54-71`). Per this pipeline's own rules that is a
  **blocking prerequisite** for the `compose` track, not something to fold in. Recorded here so
  Milestone 20's planning starts from it; it does not block *this* task.
- **The View layer is `PreferenceFragmentCompat`, not custom views or ViewBinding.** The whole screen
  is generated by the AndroidX preference library from
  `ui/preferences/src/main/res/xml/preferences_synchronization.xml` into a `RecyclerView`. A Compose
  rewrite is a rewrite of the preference-rendering mechanism, not a view-by-view port — which is
  exactly why a pixel baseline is worth having.

#### Empirical finding 1 — Robolectric native graphics (the core question)

Method: wrote a throwaway `ZzRenderProbeTest.kt` into the module's test source set, ran it under
`:ui:preferences:testPlayDebugUnitTest`, inspected the produced PNGs visually, then removed the file
(`git clean -f` — working tree verified clean afterwards, no production or test file was left behind).

The probe attached `SynchronizationPreferencesFragment` to `SyncSettingsTestHost` exactly as the
existing harness does, then explicitly measured and laid out `activity.window.decorView` at the
display metrics and drew it into an `ARGB_8888` `Bitmap`.

| Config | Distinct colours | Verdict |
|---|---|---|
| `@GraphicsMode(LEGACY)` (the **default**), sdk 36 | **1** (`#FFF9FCFF`) | **BLANK** — solid `colorSurface` fill, no text, no rows |
| `@GraphicsMode(NATIVE)`, sdk 36 (default) | 352 | RENDERED, but see clipping below |
| `@GraphicsMode(NATIVE)`, `@Config(sdk=[34])` | 356 | RENDERED, correct |
| `@GraphicsMode(NATIVE)`, `@Config(sdk=[34], qualifiers="w411dp-h891dp-xhdpi")` | 363 | RENDERED, correct, 822×1782 |

Conclusions, all directly observed:

1. **`@GraphicsMode(GraphicsMode.Mode.NATIVE)` is required and is the whole answer.** Nothing else was
   missing — no extra Gradle test option, no `robolectric.properties`, no dependency. The existing
   `common.gradle:50-54` `testOptions { unitTests { includeAndroidResources = true } }` is already
   sufficient. Robolectric 4.16 is already in the catalog at `gradle/libs.versions.toml:77` and already
   on this module's test classpath (`ui/preferences/build.gradle:55`).
2. **The LEGACY failure is silent and dangerous.** The blank bitmap is fully opaque — 150,400 of
   150,400 pixels non-zero. A test asserting "bitmap is not null" or "has non-transparent pixels"
   passes on a completely blank image. Any assertion must be on **distinct colour count** (or an
   equivalent content check), not on pixel non-nullity.
3. **The default SDK (36, from `common.gradle:2,6`) clips the first row.** At sdk 36 the content view
   is the full 320×470 decor height and the first preference row ("Choose synchronization provider")
   renders *underneath* the action bar — its title is lost and only the tail of its summary is visible.
   At sdk 34 the content view is correctly inset to 320×406 and all four visible rows render intact.
   This is edge-to-edge enforcement at targetSdk 35+. **A capture test that omits `@Config(sdk=...)`
   silently produces a clipped screenshot.**
4. **A `qualifiers` string is needed for a usable resolution.** Robolectric's default display is
   320×470 at density 1.0 — a legible but tiny image. `qualifiers = "w411dp-h891dp-xhdpi"` produced
   822×1782 at density 2.0, which reads as a normal phone screenshot.
5. **Explicit `measure()` + `layout()` on the decor view is required.** The `RecyclerView` did populate
   (childCount 4, adapterCount 4) once forced through measure/layout. Drawing without that step is
   untested and should not be assumed to work.
6. **Two harmless `Invalid ID 0x00000000.` lines** are printed to stderr in NATIVE mode. They do not
   fail the test and do not appear in the output.

Rendered output at the qualified config shows: action-bar title "Synchronization", then "Choose
synchronization provider" with its full summary, "Synchronize now", "Force full synchronization", and
"Logout" — real, correctly-laid-out text.

#### Empirical finding 2 — where a screenshot artifact could live

There is **no usable existing convention in this repo**, and the one place that looks like a convention
is a trap:

- `app/src/main/play/screenshots/` **is a git submodule** — `.gitmodules:1-5` maps `app/src/main/play`
  to `https://github.com/AntennaPod/StoreMetadata.git`. Nothing committed there lands in this
  repository. It is not an option.
- `scripts/createScreenshots.sh` writes to a repo-root `screenshots/<language>/` directory, but that is
  scratch output of a manual emulator script; the results are pushed to the upstream StoreMetadata repo,
  not committed here.
- `.gitignore:22` ignores `captures` (Android Studio's screen-capture directory) — so that path is
  actively excluded.
- No `docs/`, `assets/`, `media/`, or `images/` directory exists anywhere in this repo.
- `services/android-migration/projects/portfolio/README.md` sets policy (no implied client
  relationship; respect upstream licence before publishing modified source) but **names no asset
  location**. Neither does the fork's own `README.md` preamble.
- The only marketing-asset directory in the AEPM workspace is
  `/Users/josegarcia/ClaudeEnvironment/aepm-labs/web-aepm-labs/public/images`, which is a **different
  repository** — a PR against this repo cannot write there.

Per instruction, no location is invented here. This is Unknown 1.

#### Empirical finding 3 — what happens if the capture runs in the normal test task

- `:ui:preferences` has four unit-test variants (`testFreeDebugUnitTest`, `testFreeReleaseUnitTest`,
  `testPlayDebugUnitTest`, `testPlayReleaseUnitTest`) because of `playFlavor.gradle:1-10`. Verified via
  `./gradlew :ui:preferences:tasks --all`. There is no unflavoured `testDebugUnitTest` for this module.
- CI (`.github/workflows/checks.yml:56-100`) runs a matrix of `PlayDebug`+`Debug` and
  `PlayRelease`+`Release`, both with `execute-tests: true`. For this module that means a test in
  `src/test/` **executes twice per CI run** (`testPlayDebugUnitTest` and `testPlayReleaseUnitTest`).
  `FreeRelease` has `execute-tests: false` (`checks.yml:72-74`).
- Consequence: an unguarded capture test would rewrite the checked-in PNG twice on every CI run and
  four times on a local `./gradlew :ui:preferences:test`. If CI's bytes differ at all from the
  committed ones (see Characterization gap 3), that surfaces as either a dirty working tree or a
  failing assertion, on every unrelated PR — for an artifact that is meant to be a frozen historical
  baseline.
- **Precedent for isolation is thin but real, and it is entirely outside Gradle.** Both existing
  screenshot mechanisms in this repo are manual scripts a human runs, never wired into a Gradle task or
  CI: `scripts/createScreenshots.sh` (creates an emulator, drives the app with `adb`, deletes the
  emulator) and `app/src/main/play/screenshots/01_takeScreenshots.sh` +
  `02_frameScreenshots.py`, documented as manual in that submodule's `README.md:18-26`.
- **There is no precedent for excluding a JVM unit test from CI.** The one exclusion mechanism that
  exists — `@IgnoreOnCi` (`app/src/androidTest/java/de/test/antennapod/IgnoreOnCi.java:14`) — is
  instrumentation-only, applied via
  `-Pandroid.testInstrumentationRunnerArguments.notAnnotation=...` in
  `.github/workflows/runEmulatorTests.sh:8`. It does nothing for `src/test/`. The only other exclusion
  in the repo is a plain JUnit `@Ignore("Not a test")` on
  `net/download/service/.../DbReaderTest.java:50`. No Gradle-level `filter`/`excludeTestsMatching` or
  `systemProperty` config exists anywhere (grepped across all `*.gradle`).
- Style gates apply to the test source set: `./gradlew ktlintCheck` (`checks.yml:48`) runs
  `ktlintTestSourceSetCheck`. Verified — the throwaway probe file failed it on line-wrapping rules
  before removal. Whatever the capture test looks like, it must be ktlint-clean.

#### Empirical finding 4 — the harness's actual default rendered state

Confirmed directly from probe output, on all four configs: the existing `setUp()` pattern
(`SyncSettingsHarnessSmokeTest.kt:29-36` — `SynchronizationSettings.init`,
`SynchronizationCredentials.init`, `UserPreferences.init`, install `RecordingSynchronizationQueue`)
produces the **logged-out / fresh-install state**, with no additional setup required:

- `SynchronizationSettings.isProviderConnected()` → `false`
- `SynchronizationSettings.getSelectedSyncProviderKey()` → `null`
- `SynchronizationCredentials.getUsername()` → `null`
- Header row: title "Choose synchronization provider", summary "You can choose from multiple providers
  to synchronize your subscriptions and episode play state with", icon `null`
- `pref_gpodnet_setlogin_information`: `isVisible=false`, `isEnabled=false` (so it does not render)
- `pref_synchronization_sync`, `pref_synchronization_force_full_sync`,
  `pref_synchronization_logout`: `isEnabled=false`; logout summary `null`
- Action bar: title "Synchronization" (set by `onStart`,
  `SynchronizationPreferencesFragment.kt:41`), subtitle `null`
- `RecyclerView`: 4 children — Choose provider / Synchronize now / Force full synchronization / Logout

This matches `testLoggedOutScreenState`
(`SynchronizationPreferencesFragmentCharacterizationTest.kt:68-91`) exactly. **The pre-research
assumption was correct: the harness's existing `setUp()` already produces the fresh-install
logged-out state, and no extra arrangement is needed to capture it.** One caveat worth a planner's
eye: the three disabled rows do not render visibly dimmed in the captured bitmap — their titles draw
at the same weight as the enabled header row. Whether that faithfully reflects on-device rendering of
disabled preferences was not verified against a real device and is a known fidelity limit of a
Robolectric capture.

#### Git / branch state (Plan-stage action item)

Flagged explicitly so the planner does not miss it:

- **PR #21 has merged.** `origin/develop` is now `f5d4c5551` ("kotlin/ui-preferences: convert
  screen/synchronization/ (4 files) to Kotlin, stand up module's first test suite (#21)"). The
  pre-research context's statement that #21 is unmerged is now stale, and with it the whole
  stacked-branch instruction — the PR for this task should target `develop` directly, and no
  retargeting note is needed in its description.
- **The working tree is still on Milestone 15's branch** `kotlin/ui-preferences-sync-settings` at
  `bab75d91e`. No branch was created for this task. `git merge-base --is-ancestor HEAD origin/develop`
  confirms `HEAD` is already fully contained in `origin/develop`, and
  `git diff --stat HEAD origin/develop` over all paths except `tasks/` and `features/` is **empty** —
  so all empirical results above were produced against content byte-identical to `origin/develop`
  and remain valid.
- **Local `develop` is 17 commits behind** `origin/develop`. Milestone 15's D1 discipline still
  applies: fetch `origin` fresh, verify ancestry, and branch this task's own work from a genuinely
  up-to-date `origin/develop` — not from the stale local `develop`, and not by continuing on
  `kotlin/ui-preferences-sync-settings`. This is a Plan Step, not researcher work.

#### Track prerequisites

- `kotlin`: **met — already complete** for this slice (Milestone 15, merged as `f5d4c5551`). All four
  production files in `screen/synchronization/` are Kotlin.
- `compose` (the track this task serves, Milestone 20): **NOT met.** Two gaps. (a) **No ViewModel/MVVM
  layer exists** — the fragment reads statics and EventBus directly. Per this pipeline's rules that is
  a blocking prerequisite for `compose`, and introducing one is out of scope for any track. (b) The
  `concurrency` track has not run on this module, so there is no StateFlow-shaped state to render
  from; the screen's update mechanism is a sticky EventBus subscription. Neither gap blocks *this*
  task, which is capture-only.
- No other track is requested or implicated.

### Unknowns

Deliberately left for the planner — these are real decisions, not research gaps:

1. **Exact file path for the checked-in PNG.** No convention exists in this repo (Empirical finding 2).
   `app/src/main/play/screenshots/` is a submodule and unusable; `captures` is gitignored; there is no
   `docs/`/`assets/` directory. The planner must choose and justify a location, and it is a fair
   question whether the artifact belongs in this repo at all versus the `web-aepm-labs` marketing repo.
2. **Whether the capture code stays as reusable infrastructure or is one-off.** If it stays, it must
   remain under `src/test/` (`ui/preferences/README.md:51-53` forbids promoting the harness to `main`
   or `debug`) and must be ktlint-clean. Milestone 20's "after" capture going through the identical
   path is the argument for keeping it.
3. **Task-isolation approach**, informed by: the test would run twice per CI run for this module
   (Empirical finding 3); cross-machine byte-determinism is unverified (Characterization gap 3); and
   there is **no existing precedent** for excluding a JVM unit test from CI in this repo — `@IgnoreOnCi`
   is instrumentation-only. Options the planner should weigh: a plain `@Ignore` with a documented
   manual run command (matches the repo's existing manual-script culture); a system-property guard; a
   Gradle `filter { excludeTestsMatching ... }` (no precedent, would be the repo's first); or a
   separate opt-in Gradle task. Related sub-decision: does the test *write* the PNG on every run, or
   *assert against* the committed one?
4. **Theme variant.** The harness hardcodes `Theme.AntennaPod.Light`
   (`SyncSettingsTestHost.kt:9`), and Milestone 15's Research found 6 runtime theme permutations
   (Light/Dark/TrueBlack × dynamic/non-dynamic). Light is the path of least resistance but should be a
   stated decision. Note that changing the harness's theme would perturb all 38 existing tests, so a
   non-Light capture likely needs a second host activity rather than an edit to the existing one.
5. **SDK level and qualifiers pin — decision required, not optional.** Empirically, omitting
   `@Config(sdk=...)` uses SDK 36 and clips the first preference row behind the action bar. `sdk=[34]`
   renders correctly. The planner must pick a value and record *why*, and should note that this pin
   makes the artifact a rendering of the screen at API 34, not at the module's `targetSdk`.
6. **What the capture test asserts.** Given the LEGACY silent-blank failure mode, "writes a PNG" is not
   a sufficient acceptance criterion. A distinct-colour-count floor (observed: 363 at the qualified
   config, 1 when broken) is the obvious guard, but the exact assertion is the planner's call.
7. **Whether the fidelity limit on disabled rows matters.** Disabled preferences do not render dimmed
   in the Robolectric capture. If the "before/after" comparison is meant to be pixel-faithful to a real
   device, this is a caveat to document alongside the artifact.

### Sources

Branch / git state:
- `git branch --show-current` → `kotlin/ui-preferences-sync-settings`; `git log --oneline -1` → `bab75d91e`
- `git log --oneline -1 origin/develop` → `f5d4c5551` (PR #21 merged)
- `git merge-base --is-ancestor HEAD origin/develop` → true
- `git diff --stat HEAD origin/develop -- . ':(exclude)tasks' ':(exclude)features'` → empty
- `git branch -v` → local `develop` at `5f816b768`, `[behind 17]`

Production code:
- `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/SynchronizationPreferencesFragment.kt:30, 41, 54-71, 92-102, 111-147`
- `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/AnimatedPreferenceFragment.java:16-19, 23-26`
- `ui/preferences/src/main/res/xml/preferences_synchronization.xml` (5 `Preference` entries)

Test infrastructure:
- `ui/preferences/src/test/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/SyncSettingsTestHost.kt:7-12`
- `ui/preferences/src/test/java/.../SyncSettingsHarnessSmokeTest.kt:29-36, 43-111`
- `ui/preferences/src/test/java/.../SynchronizationPreferencesFragmentCharacterizationTest.kt:37-65, 68-91`
- `ui/preferences/src/test/java/.../RecordingSynchronizationQueue.kt`

Build / CI / tooling:
- `gradle/libs.versions.toml:77` — `robolectric = "4.16"`
- `common.gradle:2, 6` — `compileSdk 36`, `targetSdk 36`
- `common.gradle:50-54` — `testOptions { unitTests { includeAndroidResources = true } }`
- `ui/preferences/build.gradle:6-7, 54-61`
- `playFlavor.gradle:1-10` — `free`/`play` flavours
- `.github/workflows/checks.yml:46, 48` — `checkstyle lint`, `ktlintCheck`
- `.github/workflows/checks.yml:56-100` — unit-test matrix (`PlayDebug`+`Debug`, `PlayRelease`+`Release` execute tests; `FreeRelease` does not)
- `.github/workflows/runEmulatorTests.sh:8` — `notAnnotation=de.test.antennapod.IgnoreOnCi`
- `app/src/androidTest/java/de/test/antennapod/IgnoreOnCi.java:14`
- `net/download/service/src/test/java/de/danoeh/antennapod/net/download/service/episode/autodownload/DbReaderTest.java:50` — `@Ignore("Not a test")`
- `.gitmodules:1-5` — `app/src/main/play` → `AntennaPod/StoreMetadata.git`
- `.gitignore:22` — `captures`
- `app/src/main/play/screenshots/README.md:18-26` — manual capture/framing scripts
- `scripts/createScreenshots.sh` — manual emulator + `adb` capture, writes `screenshots/<language>/`
- `services/android-migration/projects/portfolio/README.md:5-8` — portfolio policy, no asset location
- `ui/preferences/README.md:8-12, 45-49, 50-53, 54-56`
- `./gradlew :ui:preferences:tasks --all` → four `test<Flavour><BuildType>UnitTest` tasks
- `./gradlew :ui:preferences:ktlintCheck` → fails via `:ui:preferences:ktlintTestSourceSetCheck`

Empirical runs (throwaway `ZzRenderProbeTest.kt`, since removed; working tree verified clean):
- `./gradlew :ui:preferences:testPlayDebugUnitTest --tests "*ZzRenderProbeTest*"` — 4 probe configs, output table above
- `./gradlew :ui:preferences:testPlayDebugUnitTest --rerun-tasks` — 38 tests, all PASSED, 16s
- Two forced re-runs of the qualified config → identical md5 `170c151660a80bdd62949e844f41f1a1` (same machine)

## Plan
_Last updated by: legacy-android-planner | 2026-08-06_

### Objective

Test-only, no track. Add one Robolectric test class and one capture-only host activity to
`:ui:preferences`' existing `screen/synchronization/` test source set, rendering the **logged-out Sync
Settings screen** to a real `Bitmap` through a host that reproduces production's chrome chain (extends the
app's own `ToolbarActivity`, inflates the app's own `settings_activity.xml`), asserting the render is
genuinely non-blank and un-clipped, and writing a PNG. That PNG is copied once, by hand, into a new
`tasks/artifacts/` directory as the frozen "before" baseline for Milestone 20's Compose comparison.
**Zero production code changes, zero new dependencies, zero build-file changes.** Four files created,
three touched. *(Revision 1 replaced the original "via Milestone 15's `SyncSettingsTestHost` harness" —
that harness diverges structurally from production's title-bar chrome; see D5.)*

All seven of Research's Unknowns are decided below. None is deferred.

### Resolved Decisions

**D1 — Branch fresh from a *fetched* `origin/develop`, verified by ancestry, before any commit.**
(Research's "Git / branch state" action item.)

Re-verified at planning time, this session:

```
git branch --show-current              → kotlin/ui-preferences-sync-settings   [Milestone 15's branch]
git log --oneline -1                   → bab75d91e
git log --oneline -1 origin/develop    → f5d4c5551 … (#21)                      [M15 merged]
git merge-base --is-ancestor HEAD origin/develop → TRUE
git branch -v | grep '^  develop'      → 5f816b768 [behind 17]                  [local develop is stale]
```

So Research's finding holds exactly: `HEAD` is already contained in `origin/develop`, the empirical probe
results were produced against content byte-identical to `origin/develop`, and **local `develop` is 17
commits behind and must not be used as a base.** Milestone 15's D1 discipline applies unchanged. Before
Step 1, the developer runs, in order, and pastes the output into Implementation Notes:

```bash
git fetch origin
git merge-base --is-ancestor f5d4c5551 origin/develop && echo "M15 present"
git checkout -b test/ui-preferences-sync-settings-before-screenshot origin/develop
git log --oneline -1
```

If the second command does not print `M15 present` after a fetch, **stop** — the base is wrong and this
task's whole premise (the `SyncSettingsTestHost` harness exists on `develop`) has not landed. That is a
José question, not something to work around.

Branch name `test/ui-preferences-sync-settings-before-screenshot`. The repo's two existing prefixes are
`kotlin/<subject>` (production conversions) and `docs/<subject>` (spec-only branches); neither fits
test-tooling work, so this follows the same `<kind>/<subject>` shape with a new, honest `kind`.
AGENTS.md forbids committing on `develop`/`master`, so this is checked before Step 1, not at PR time.

Note the two uncommitted M15 files currently in the working tree
(`features/antennapod-sync-settings-kotlin-milestone-15.checkpoint.md`,
`tasks/antennapod-sync-settings-modernization-future-work.md`, both `M`). They are Milestone 15's, not
this task's. They must not ride along in this task's diff.

**One recommended path, not a choice** *(corrected in Revision 1 — the original text offered "commit them
onto M15's branch or stash them" as an equivalent either/or, which it is not: PR #21 has merged, so
`kotlin/ui-preferences-sync-settings` is stale, and a commit added to it post-merge lands nowhere without
its own follow-up PR — something the original text never said)*. Before branching, run:

```bash
git stash push -m "M15 spec leftovers (see 15b D1)" -- \
  features/antennapod-sync-settings-kotlin-milestone-15.checkpoint.md \
  tasks/antennapod-sync-settings-modernization-future-work.md
git stash list | head -1     # record this ref in Implementation Notes
git status --porcelain       # must now be empty before `git checkout -b`
```

Record the stash ref verbatim in Implementation Notes — an unrecorded stash is how these edits get
silently lost, which is the actual risk here. **They are recovered after this task's PR, on their own
`docs/<subject>` branch and their own small PR** (the repo's existing prefix for spec-only branches, per
this decision's branch-naming paragraph above) — not on `kotlin/ui-preferences-sync-settings`, which is
merged and stale, and not folded into this task's PR, which AC7/AC11 forbid. If the developer prefers to
commit rather than stash, that is acceptable **only** with the same follow-up: a fresh `docs/` branch off
`origin/develop`, never the merged M15 branch.

---

**D2 — `@GraphicsMode(GraphicsMode.Mode.NATIVE)` is mandatory, and the assertion is a distinct-colour
floor of 200, not a non-null check.** (Research Unknown 6; Research's single most important finding.)

The LEGACY default produces a fully-opaque solid `colorSurface` fill — 150,400 of 150,400 pixels
non-zero, **1** distinct colour. Every naive assertion (`assertNotNull(bitmap)`, "has non-transparent
pixels", "file size > 0", "width == 822") passes on that blank image. A test written that way would
declare success while checking in a blank rectangle, and the regression would never be caught.

So the assertion is on **distinct ARGB values across the whole bitmap**, with a floor of **200**:

- Broken (LEGACY, or the render silently degrading on a future Robolectric bump) → **1**. Fails.
- Observed at this plan's exact config → **363**. Passes with 163 to spare.

200 is chosen to sit far above any degenerate render and far below the observed value, so that
font-rasterisation differences between JDK builds or CI runners (real, and the reason D3 refuses
byte-comparison) cannot false-fail it. A floor set at 350 would be a flaky-CI landmine; a floor set at 2
would not catch a two-tone half-render.

`@GraphicsMode` is applied at class level so it cannot be forgotten on a later added method.

**Scope of what this floor can detect — AC2 and AC3 are an independent, both-required pair, and neither
is sufficient alone.** *(Added in Revision 1. The original D2 calibrated the floor against two data points
only — 1 when blank, 363 when correct — and never mentioned the third, which is the one that matters.)*
Research's own probe table contains a **structurally broken but non-blank** render: `@GraphicsMode(NATIVE)`
at the unpinned sdk 36 default, where the first preference row's title is clipped behind the action bar,
produced **352** distinct colours — above the 200 floor, and within 3% of the correct 363. Stated plainly:

| Failure mode | Distinct colours | Caught by AC2's floor? | Caught by AC3? |
|---|---|---|---|
| LEGACY / blank fill | 1 | **yes** | no (a blank screen has no rows to be un-clipped, but AC3's childCount check would also fire — it is not the designed guard) |
| First row clipped behind the title bar | **352** | **no** | **yes** |
| Correct render | 363 | passes | passes |

So the distinct-colour floor detects **only** total render collapse. It cannot and does not detect a
"rendered but wrong" screen — the very failure mode that motivated D5's config pin. The load-bearing check
for that mode is `testFirstPreferenceRowIsNotClippedByActionBar` (AC3), and the two are **non-substitutable**:
a future edit that reads AC2 as general "render correctness" coverage and weakens or drops AC3 removes the
only guard against 352-class failures. Both test methods carry a one-line code comment saying so, naming
the other method, so the pairing survives being read one method at a time.

---

**D3 — The test writes its PNG into the module's gitignored `build/` directory on every run, and the
checked-in artifact is a one-time manual copy. No test-isolation mechanism is introduced.**
(Research Unknown 3 — this decision **eliminates the tracked-tree half** of it and **accepts, with a gate,
the environment-sensitivity half**.) *(Framing corrected in Revision 1 — the original text claimed this
decision "dissolves [Unknown 3] rather than answering it," which overstated it. It dissolves the
dirty-working-tree risk. It does not dissolve the risk that an environment-sensitive assertion, kept
deliberately in the default task graph, goes red on a CI runner Research never tested against. See
"Residual risk" below, which is new.)*

Research framed the isolation question around a test that writes into the *tracked* tree. That premise is
what creates every downside it lists, and it is avoidable. If the test's only output path is
`build/` — which `.gitignore:8` (`build/`) excludes at any depth — then:

- Running 2× per CI run (`testPlayDebugUnitTest`, `testPlayReleaseUnitTest`; confirmed at
  `checks.yml:64-71, 98-100`) and 4× on a local `:ui:preferences:test` is **harmless**. Nothing tracked
  is touched, the working tree stays clean, and no unrelated PR ever sees a dirty diff.
- **No isolation mechanism is needed**, so none of Research's four options is taken: no `@Ignore` (which
  would leave the render permanently unexercised), no system-property guard, no Gradle
  `excludeTestsMatching` (which would be this repo's first, with no precedent — correctly identified by
  Research as a reason for caution), no separate opt-in task. **The repo gains no new convention.**
- Research's Characterization gap 1 — "no regression safety net if Robolectric's rendering behaviour
  changes on a version bump" — is *closed rather than accepted*, because the render is exercised on every
  CI run. That is a benefit only available if the test stays in the default task graph. Isolating it would
  have thrown this away to solve a problem that only exists under the rejected premise.

**The committed PNG is never re-verified against a fresh render, deliberately.** Research's
Characterization gap 3 is explicit that cross-machine byte-determinism is unverified (identical md5 on one
machine proves only same-machine determinism). Making the committed PNG a golden-image assertion would
convert an unverified assumption into a CI failure on every unrelated PR. It is a **frozen historical
baseline**, not a golden image — the code proves the render still works, the PNG records what it looked
like at `f5d4c5551`. Those are two different jobs and this plan keeps them separate.

The test prints the resolved absolute output path. Research did not record the unit-test working
directory, so this plan does not assume it: Step 2 **observes** where the file lands and confirms it is
under `ui/preferences/build/`. If it is not, that is a disclosed finding that stops the step, not
something to paper over with a hardcoded path.

**Residual risk, named and gated rather than assumed away.** *(New in Revision 1.)* Keeping the test in
the default task graph is a real benefit (it closes Research's Characterization gap 1) but it has a cost
this plan previously did not state: a **graphics-mode-sensitive, environment-sensitive** assertion now runs
on every unrelated PR that touches `:ui:preferences`, twice per CI run, on `ubuntu-latest`
(`.github/workflows/checks.yml:12,20,59,110,169`) — a JDK and OS **every empirical probe in Research was
run against a different one of**. Research's Characterization gap 3 says cross-machine determinism is
unverified; D3 originally used that only to refuse a byte-diff, and never asked the adjacent question:
*does the render pass its own assertions on Linux CI at all?* If it does not, the "harmless" 2×-per-run
repetition is replaced by recurring CI-red across the repo, which is worse than the dirty tree Research
worried about. Three things follow, and all three are decisions, not caveats:

1. **A real CI run is a merge gate — AC13.** Local `--rerun` output does not satisfy this plan. The branch
   is pushed, the PR opened, and the actual GitHub Actions run's result pasted into Implementation Notes
   before the task is complete. This is cheap (branch protection requires a green run before merge anyway)
   and it is the only observation that retires the untested assumption.
2. **The two assertions carry deliberately different tolerances, for a stated reason.** Dimensions stay
   **exact** (`822 × 1782`): they derive arithmetically from the `qualifiers` string, not from font
   rasterisation, so they are cross-environment deterministic and an exact check is what proves the
   qualifier took effect (D5). The distinct-colour check stays a **floor**, not a range or an equality —
   163 colours of headroom below the observed value is precisely the cross-environment tolerance, and it
   is why the floor is 200 rather than 350 (D2). No other assertion in this test is environment-sensitive.
3. **If CI is red, the response is decided in advance, not improvised.** A dimension mismatch means the
   qualifier resolved differently on the runner → **stop and disclose**; do not "fix" it by relaxing the
   assertion, because that is the assertion proving the config took effect. A colour-count shortfall that
   is still comfortably non-degenerate (say 250–350) means font rasterisation differs on the runner →
   lower the floor to the observed value minus 30%, record both numbers and the runner's JDK in
   Implementation Notes, and say so in `tasks/artifacts/README.md`. A count at or near **1** means D2's
   hazard fired on Linux → **stop**, that is a genuine finding about Robolectric native graphics on the
   runner and not something to tune around. Disabling, `@Ignore`-ing or excluding the test from CI is not
   among the permitted responses — that is the isolation route this decision rejected, and taking it
   silently would re-open Characterization gap 1 while claiming it closed.

---

**D4 — The capture code stays in the tree as reusable infrastructure, under `src/test/`.**
(Research Unknown 2.)

It stays, for a reason that survives the Milestone 20 wrinkle below: **an artifact whose generator is not
in the tree is an unreproducible orphan binary.** The case-study claim this whole task exists to support
is "we can show you the before, and you can regenerate it" — deleting the generator retracts the second
half. It also costs nothing to keep: the assertions in D2 require the capture code to exist regardless, so
"one-off" would mean writing it and then deleting a file that is already passing CI.

It stays under `ui/preferences/src/test/`, never promoted to `src/main` or `src/debug` — `ui/preferences/
README.md:50-53` forbids that, because the host activity would then ship in the app. It must be
ktlint-clean (`ktlintTestSourceSetCheck` runs on this source set; Research's throwaway probe failed it on
line-wrapping, so this is a live gate, not a formality).

**Correction to the pre-research context's stated rationale, which the planner must record rather than
inherit.** The pre-research note argued for keeping the code so that Milestone 20's "after" capture goes
through "the identical path." That argument does not hold: the milestone queue records M20's *locked*
decision to use **Paparazzi** with tolerance-based diffing, and M16 is the milestone that adds Paparazzi
to the catalog. So the "before" (Robolectric, View system) and the "after" (Paparazzi, Compose) will come
out of **two different rendering engines**. The consequences, stated here so Milestone 20 inherits them
explicitly instead of rediscovering them:

1. **A pixel-diff between this PNG and any Paparazzi output is meaningless.** M20's tolerance-based
   Paparazzi diffing is a Compose-vs-Compose regression check — a different job. This PNG is a
   side-by-side visual reference for the case study, and must be used as one.
2. That makes the fidelity caveats in D7 *less* load-bearing, not more: a systematic offset that would
   have cancelled in a same-engine comparison instead just needs documenting, which D7 does.
3. The reproducibility argument above is therefore the *sole* surviving reason to keep the code — and it
   is sufficient on its own.

---

**D5 — Capture through a new, production-faithful host activity (`SyncSettingsCaptureHost`), at
`@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")`.** (Research Unknowns 4 and 5.)

**This decision was rewritten in Revision 1. Its original factual premise was false and is retracted in
full below before the replacement is stated, because the retracted claim would otherwise have shipped as a
provenance sentence in `tasks/artifacts/README.md`.**

**Retraction.** The original D5 justified the sdk-34 pin by asserting that the sdk-36 clipping is "a
harness artifact, not app behaviour," on the evidence that a `grep` for
`enableEdgeToEdge|setDecorFitsSystemWindows|WindowInsets` found no window-insets handling in the real
`PreferenceActivity`. **That grep was incapable of finding the mechanism it was looking for**, because
production's insets handling is an XML style and layout attribute, not any of those three APIs. Re-read
from source this session, production's actual host chain for this fragment is:

| Layer | Production | `SyncSettingsTestHost` (the existing harness) |
|---|---|---|
| Activity | `PreferenceActivity extends ToolbarActivity` (`app/src/main/java/de/danoeh/antennapod/ui/screen/preferences/PreferenceActivity.java:31`) | bare `AppCompatActivity` (`SyncSettingsTestHost.kt:7`) |
| Theme | `ThemeSwitcher.getNoTitleTheme(this)` (`ToolbarActivity.java:16`) → `Theme.AntennaPod.Light.NoTitle` (`styles.xml:152-155`) | `Theme.AntennaPod.Light` (`SyncSettingsTestHost.kt:9`) — **the parent, without `.NoTitle`** |
| `windowActionBar` | **false**; title bar is a custom `MaterialToolbar` | **true** (Material3 default); title bar is the system decor ActionBar |
| `android:fitsSystemWindows` | **true**, on `toolbar_activity.xml`'s root `LinearLayout` (`ui/common/src/main/res/layout/toolbar_activity.xml:7`) | **false**, inherited from `Theme.Base.AntennaPod.Dynamic.Light` (`styles.xml:30`) |
| Content chain | decor → `toolbar_activity.xml` (`MaterialToolbar` + `@android:id/content` `FrameLayout`) → `settings_activity.xml`'s `settingsContainer` → fragment | decor content `FrameLayout` → fragment |

So the harness does not merely miss an SDK-version quirk. It uses the **opposite `fitsSystemWindows`
polarity** from production and draws its title bar through a **structurally different mechanism**. The
sdk-36 clipping is therefore *not* a pure harness artifact in the sense claimed, and — more importantly for
this task — **the title-bar chrome in a screenshot captured through the bare harness is not the chrome that
ships.** For an artifact whose entire purpose is to be the visual "before" in a public case study, that is
a defect in the artifact, not a footnote.

**Decision: add a second, capture-only host activity rather than pin around the problem.** Create
`ui/preferences/src/test/java/.../SyncSettingsCaptureHost.kt`:

```
class SyncSettingsCaptureHost : ToolbarActivity() {
    override fun onCreate(...) {
        super.onCreate(savedInstanceState)          // ToolbarActivity applies NoTitle theme + toolbar_activity.xml
        supportActionBar?.setDisplayHomeAsUpEnabled(true)   // as PreferenceActivity.java:41-44 does
        setContentView(SettingsActivityBinding.inflate(layoutInflater).root)  // as PreferenceActivity.java:46-47 does
    }
}
```

The capture test attaches the fragment to `R.id.settingsContainer` (production's own container id, from
`ui/preferences/src/main/res/layout/settings_activity.xml`), not to `android.R.id.content`. This
reproduces production's chrome chain layer for layer using **production's own activity base class and
production's own layouts** — nothing about the chrome is re-implemented or approximated by this plan.

Four supporting facts, each verified from source this session rather than assumed:

1. **`ToolbarActivity` is reachable from this module's test source set.** `ui/preferences/build.gradle:34`
   declares `implementation project(":ui:common")`, and a module's `implementation` dependencies are on its
   own unit-test compile classpath. `SettingsActivityBinding` exists because `common.gradle:65-66` enables
   `viewBinding` repo-wide — the same binding class `PreferenceActivity` already imports.
2. **The theme resolves to exactly the Light permutation this plan wanted anyway, without hardcoding it.**
   `ThemeSwitcher.getNoTitleTheme` branches on `UserPreferences.getIsThemeColorTinted()`, which is
   `SDK_INT >= 31 && prefs.getBoolean(PREF_TINTED_COLORS, false)` — **false on a fresh install**
   (`UserPreferences.java:214-215`) — and on `UserPreferences.getTheme()`, which defaults to `SYSTEM` and,
   under a non-`night` qualifier string, resolves `LIGHT`. Both branches therefore land on
   `Theme.AntennaPod.Light.NoTitle`: the non-dynamic Light permutation, which is what production shows a
   fresh-install user, and the same Light family the existing harness picked. **Consequence to respect:
   the `qualifiers` string must never gain `-night`, or the captured theme flips silently.** Also newly
   load-bearing: `UserPreferences.init(context)` in `setUp()` was incidental before and is now required —
   `ThemeSwitcher` reads it *before* `super.onCreate`, so omitting it NPEs at activity construction.
3. **The existing 38 tests are unaffected, and this was checked rather than hoped.** They are unaffected
   because `SyncSettingsTestHost.kt` is not edited at all — it stays byte-identical (AC7), and no existing
   test references the new host. The specific breakage worth having checked anyway is the action bar: all
   four tests in `SynchronizationPreferencesFragmentLifecycleTest.kt` (`:72, 79-84, 99-105, 114-120`)
   assert on `activity.supportActionBar!!.title`/`.subtitle`, and the fragment itself dereferences
   `supportActionBar!!` in `onStart`/`onStop` (`SynchronizationPreferencesFragment.kt:41, 51`). Under
   `Theme…Light.NoTitle`, `windowActionBar=false` means a bare `AppCompatActivity` would return **null**
   from `getSupportActionBar()` and all four would NPE — which is exactly why the fix is a *second* host
   and not a theme change on the shared one. On the new host the dereference is safe, because
   `ToolbarActivity.onCreate` calls `setSupportActionBar(viewBinding.toolbar)` (`ToolbarActivity.java:19`),
   so `supportActionBar` is the `MaterialToolbar`-backed one. The three new tests exercise that path.
4. **Two deliberate, disclosed departures from `PreferenceActivity`.** The capture host does **not**
   register on EventBus in `onStart` (production does, `PreferenceActivity.java:165-175`, solely to show a
   `MessageEvent` Snackbar — nothing in the logged-out capture path posts one, and registering would add a
   second EventBus subscriber to a suite that already manages sticky events carefully), and it does **not**
   route through `MainPreferencesFragment` + `openScreen()` (production's navigation path; the capture
   attaches the target fragment directly, as all seven existing test files already do). Neither affects
   rendered chrome. Both are recorded in `tasks/artifacts/README.md`.

**SDK 34, not the module's default 36 — kept, with a correct rationale this time.** The pin is **not**
evidence that production has no inset handling to miss; it demonstrably does. It is a deliberate choice to
render at an API level that **predates edge-to-edge enforcement**, so that the capture does not depend on
how faithfully Robolectric simulates system-bar insets — which is itself unverified, and which this task
has no budget to establish. At sdk 34 the geometry is the one Research actually observed and confirmed
correct. **The artifact is a rendering at API 34, not at the module's `targetSdk` of 36, and this is stated
wherever it is referenced** (D6's README). Note for Milestone 20 rather than for this task: with the
faithful host the toolbar is a real sibling view in a `LinearLayout` rather than decor overlaying the
content, so the 352-colour clipping failure may well not be reachable at sdk 36 any more — plausible, not
verified here, and not a reason to move the pin without measuring.

**Qualifiers `w411dp-h891dp-xhdpi`.** Unchanged. Robolectric's default display is 320×470 at density 1.0 —
legible but too small to read as a screenshot. This qualifier produced **822×1782 at density 2.0**, a
normal phone-sized image. Both values are asserted (AC2) so a silently-dropped qualifier string fails
loudly rather than producing a tiny image nobody notices. The window dimensions are a property of the
qualifier string, not of the host activity, so they are unchanged by this revision. **The distinct-colour
count is not** — 363 was observed through the bare harness, and the faithful host draws different chrome,
so the expected value will move. That is why D2's assertion is a floor of 200 rather than a comparison
against 363; the newly observed count is recorded in Implementation Notes (AC2) as the new reference.

**Theme: `Theme.AntennaPod.Light.NoTitle`, resolved by production's own `ThemeSwitcher`** (see fact 2).
Light remains the decision: it is the app's fresh-install default and the state a "before" reference should
show. Milestone 15's Research found six runtime theme permutations; a single baseline does not need all
six. A Dark or TrueBlack capture would need only a `-night` qualifier or a `UserPreferences` theme write on
this new host — cheaper than it was before this revision, but still out of scope and still noted for M20.

**State: fresh-install logged-out, no extra arrangement.** Unchanged. Research confirmed on all four probe
configs that the existing `setUp()` pattern (`SyncSettingsHarnessSmokeTest.kt:29-36`) already produces
exactly this — `isProviderConnected()` false, provider key null, gpodnet row `isVisible=false`, four rows
in the `RecyclerView`. The capture test copies that `setUp()` verbatim and adds nothing.

**Contingency — bounded, and it stops rather than degrades.** `ToolbarActivity` has never been driven under
Robolectric anywhere in this repo. If it cannot be stood up in Step 1 within roughly one debugging session
(view-binding inflation, `ThemeSwitcher`, or `Robolectric.buildActivity` on a manifest-absent activity all
being plausible failure points), the developer **stops and reports** — with the failure pasted — rather than
quietly reverting to `SyncSettingsTestHost`. Falling back is José's call, not the developer's, and if
taken it carries a **mandatory, top-of-file** fidelity warning in `tasks/artifacts/README.md` naming the
divergence precisely (harness `Theme.AntennaPod.Light`, `fitsSystemWindows=false`, system decor ActionBar;
production `Theme.AntennaPod.Light.NoTitle`, `fitsSystemWindows=true`, custom `MaterialToolbar`) — not the
retracted "no insets handling anywhere" claim, and not buried among the other caveats.

---

**D6 — The artifact lives at `tasks/artifacts/`, in *this* repo, with a `README.md` alongside it.**
(Research Unknown 1.)

Research established there is no existing convention and one apparent convention is a trap. Re-verified
this session: `app/src/main/play` is a **git submodule** pointing at `AntennaPod/StoreMetadata.git`
(`.gitmodules:1-5`) — nothing committed there lands in this repository; `.gitignore:22` excludes
`captures`; no `docs/`, `assets/`, `media/` or `images/` directory exists at any level. Additionally
found this session and worth recording: **repo-root `screenshots/` is unusable as a name** —
`scripts/createScreenshots.sh` already writes its emulator output to `screenshots/<language>/`, so a
checked-in directory of that name would collide with an existing script's scratch path.

**It belongs in this repo, not only in the marketing repo.** The marketing repo
(`/Users/josegarcia/ClaudeEnvironment/aepm-labs/web-aepm-labs/public/images`, confirmed to exist) is a
*different* repository — a PR against this one cannot write there, and more importantly, an artifact
stored only there is severed from the code that produces it. D4's reproducibility argument requires the
PNG and its generator to be in the same tree at the same commit. **Publishing to the marketing site is a
copy, not a move.** Recorded as OQ1 since the final publishing home is José's call.

**Path: `tasks/artifacts/`.** Nested under `tasks/`, which already exists as AEPM-owned territory in this
upstream fork (`tasks/` and `features/` are both tracked and both AEPM additions), so this adds **zero new
root-level directories** to a fork of someone else's project — consistent with AGENTS.md's "keep the diff
to the absolute minimum." A subdirectory rather than loose files directly in `tasks/`, because Milestone
20 will add an "after" PNG and this before/after pattern is likely to repeat per-module; binaries
interleaved with spec `.md` files would degrade quickly.

Two files:

- `tasks/artifacts/sync-settings-before-milestone-15b.png` — the artifact.
- `tasks/artifacts/README.md` — provenance for everything in the directory. It must carry, at minimum:
  1. **The provenance sentence the pre-research context requires**, verbatim in substance: this screenshot
     was captured *after* Milestone 15's Java→Kotlin conversion (merged at `f5d4c5551`), which was
     behaviour- and visually-unchanged by its own acceptance criteria, so it is visually identical to the
     original Java UI and is the correct "before" baseline for Milestone 20 — **it does not predate
     Milestone 15**, and nobody should assume it does.
  2. The exact commit it was rendered at, the generating test's fully-qualified name, and the one-line
     command to regenerate it.
  3. The API-34 pin and why (D5) — stated as "predates edge-to-edge enforcement, so the render does not
     depend on Robolectric's unverified inset simulation," **not** as "production has no inset handling."
     That claim is retracted (D5) and must not appear.
  4. The two fidelity caveats (D7).
  5. The cross-engine warning (D4's correction): do not pixel-diff this against Paparazzi output.
  6. That it is unaffiliated OSS portfolio work per `services/android-migration/projects/portfolio/
     README.md` — no client relationship implied.
  7. **The chrome-fidelity statement** *(added in Revision 1)*. Under D5's primary path this is a positive
     statement and belongs alongside item 3: the capture is hosted by `SyncSettingsCaptureHost`, which
     extends production's own `ToolbarActivity` and inflates production's own `settings_activity.xml`, so
     the title bar is the same `MaterialToolbar` under the same `Theme.AntennaPod.Light.NoTitle` that
     ships — with the two disclosed departures (no EventBus registration, no `MainPreferencesFragment`
     navigation) named. Under D5's contingency fallback it is instead the **mandatory top-of-file warning**
     described there, at least as prominent as D7's disabled-row caveat, since in that case the captured
     title-bar chrome does not match what ships. Whichever applies, it is stated in the artifact's own
     README — not left to the task file.

This is an **interim** location and the README says so. If the portfolio's marketing site becomes the
publishing home, the PNG is copied there and this copy stays as the reproducible provenance record.
Relocating later is a one-line move plus a README edit — cheap, and not a reason to stall now.

---

**D7 — The disabled-row fidelity gap is documented, not investigated, and does not block this task.**
(Research Unknown 7.)

Research observed that the three disabled rows (Synchronize now / Force full sync / Logout) do not render
visibly dimmed — their titles draw at the same weight as the enabled header row — and did not verify this
against a real device. Two reasons it does not matter here:

1. The baseline's purpose is **layout, structure, row inventory and typography** for a side-by-side
   case-study comparison against a Compose rewrite. Row-level alpha on three disabled rows does not change
   any of those.
2. Chasing it means either diagnosing AndroidX preference's `setEnabled` → drawable-state propagation
   under Robolectric NATIVE, or standing up a real-device capture — both are new scope for a task whose
   whole justification is that it reuses infrastructure already paid for.

So: **documented as a known fidelity limit in `tasks/artifacts/README.md`, with the plausible cause named
(disabled state not propagating to the child `TextView`s' drawable state under Robolectric's native
renderer) marked explicitly as an unverified hypothesis.** The second caveat documented alongside it is
that this is a Robolectric render, not a device render, and has not been compared against one. Milestone
20 can re-check both if it ever wants device-fidelity claims. No assertion in this plan depends on it.

---

**D8 — No build-file change, no new dependency, no version-catalog change.**

Verified against source this session: Robolectric 4.16 is already at `gradle/libs.versions.toml:77` and
already on this module's test classpath (`ui/preferences/build.gradle:55`);
`testOptions { unitTests { includeAndroidResources = true } }` is already set repo-wide
(`common.gradle:50-55`); `Bitmap`, `Canvas` and `Bitmap.compress` are platform APIs needing no dependency;
writing the file needs only `java.io.File`. Research's conclusion 1 — "`@GraphicsMode(NATIVE)` is required
and is the whole answer; nothing else was missing" — is confirmed. **A diff touching
`ui/preferences/build.gradle`, `gradle/libs.versions.toml`, `common.gradle` or `playFlavor.gradle` means
this decision was wrong and the task is re-planned, not patched.**

### Steps

Three steps, each one reviewable diff leaving the build green. Steps 1 and 2 each end with both flavours
green as two separate `--rerun` invocations (`ui/preferences/README.md:54-56` — CI runs Play only, run both
locally), plus `./gradlew ktlintCheck` green.

**No characterization-test step is required, and this is not a waiver.** The characterization-tests-first
rule exists to pin behaviour a conversion might change. This task changes no production behaviour and no
production file; Research's "Characterization-test gaps" section says so directly ("the gap is narrow,
because the task adds no production behaviour"). The two gaps it does name are gap 1 (no rendering
regression net — **closed by D3**, because the new test runs in CI) and gap 2 (no test pins the *rendered*
logged-out state — **closed by Step 1**, whose assertions are exactly that pin). The 38 existing tests
remain the behavioural baseline and must stay green, byte-unchanged, throughout (AC7).

**Step 1 — Add the production-faithful capture host and the capture test. No file leaves `build/`;
nothing is checked in yet.** *(Revised in Revision 1: the host file is new, and the container id and
AC3 assertion changed with it.)*

Create two files in
`ui/preferences/src/test/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/`:

**(a) `SyncSettingsCaptureHost.kt`** (D5) — `class SyncSettingsCaptureHost : ToolbarActivity()`, whose
`onCreate` calls `super.onCreate(savedInstanceState)`, then
`supportActionBar?.setDisplayHomeAsUpEnabled(true)`, then
`setContentView(SettingsActivityBinding.inflate(layoutInflater).root)` — mirroring
`PreferenceActivity.java:37-47`. No theme call of its own: `ToolbarActivity.onCreate` applies
`ThemeSwitcher.getNoTitleTheme(this)` before `super.onCreate`, which resolves to
`Theme.AntennaPod.Light.NoTitle` under this plan's setUp and qualifiers (D5 fact 2). **`SyncSettingsTestHost.kt`
is not edited** (AC7). If this host cannot be driven under Robolectric, D5's contingency applies: stop and
report, do not fall back silently.

**(b) `SyncSettingsScreenshotCaptureTest.kt`** — annotated `@RunWith(RobolectricTestRunner::class)`,
`@GraphicsMode(GraphicsMode.Mode.NATIVE)` (D2) and `@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")`
(D5). `setUp()` copies `SyncSettingsHarnessSmokeTest.kt:29-36` verbatim — including
`UserPreferences.init(context)`, which is now load-bearing rather than incidental (D5 fact 2);
`tearDown()` clears `SynchronizationQueue.instance`. A private `capture()` helper builds
`SyncSettingsCaptureHost`, attaches `SynchronizationPreferencesFragment` to **`R.id.settingsContainer`**
(production's container id — *not* `android.R.id.content`, which under the toolbar layout resolves to the
decor content and would overlay the toolbar) via `commitNow()`, explicitly `measure()`s and `layout()`s
`window.decorView` at the display metrics (Research conclusion 5 — drawing without this is untested), and
draws it into an `ARGB_8888` `Bitmap`. Three tests:
- `testCapturedBitmapIsNotBlankAndHasExpectedDimensions` — asserts width `822`, height `1782` (D5), and
  distinct ARGB count `>= 200` (D2). Carries a one-line comment stating this floor detects only a blank
  render and that `testFirstPreferenceRowIsNotClippedByActionBar` is the guard for clipping (D2).
- `testFirstPreferenceRowIsNotClippedByActionBar` — asserts the `RecyclerView` has `4` children, and that
  `recyclerView.getChildAt(0)`'s top in window coordinates is `>=` the **`MaterialToolbar`'s bottom** in
  window coordinates (`R.id.toolbar` from `toolbar_activity.xml`), with the toolbar's own height `> 0`.
  *Restated in Revision 1*: the previous form asserted `android.R.id.content`'s top `> 0`, which was a
  proxy for the decor ActionBar's inset — a mechanism the faithful host no longer uses. Toolbar-bottom vs.
  first-row-top is the direct expression of "the first row is not hidden behind the title bar," and it
  holds independent of SDK level. Carries the reciprocal comment naming AC2's test (D2).
- `testWritesPngUnderModuleBuildDirectory` — compresses to PNG under `build/reports/screenshots/`, prints
  the resolved **absolute** path, and asserts the file exists and is `> 10_000` bytes.

Tests: the three above. Ends with 41 tests green in the module (38 + 3 — the host is not a test class),
both flavours, and `git status --porcelain` clean (D3).

**Step 2 — Generate and check in the artifact.**
Run `./gradlew --console=plain :ui:preferences:testPlayDebugUnitTest --tests "*SyncSettingsScreenshotCaptureTest*" --rerun`.
Read the printed absolute path and **confirm it resolves under `ui/preferences/build/`** — if it does not,
stop and disclose (D3). Open the PNG and visually confirm it shows the toolbar title "Synchronization"
*with the up arrow*, then "Choose synchronization provider" *with its title visible and its full summary*,
"Synchronize now", "Force full synchronization" and "Logout" — the five elements Research observed, plus
the up arrow the faithful host adds (D5). Copy it to
`tasks/artifacts/sync-settings-before-milestone-15b.png`. Create `tasks/artifacts/README.md` with all
**seven** required contents from D6. Record the PNG's `git hash-object` and dimensions in Implementation
Notes. Tests: none added; the three from Step 1 must still be green.

**Step 3 — Verification sweep, real-CI confirmation, and documentation.**
Run every acceptance criterion's command and paste the output. **Then push the branch, open the PR, and
paste the actual GitHub Actions run's result — not local `--rerun` output — into Implementation Notes,
including the distinct-colour count and dimensions the CI runner observed (AC13, D3). If CI is red, apply
D3's pre-decided response rather than improvising.** Add a convention entry to
`ui/preferences/README.md` recording that this module's test source set contains a Robolectric
native-graphics capture, that `@GraphicsMode(NATIVE)` is mandatory for any rendering test in it (LEGACY
silently produces a blank fill), and that its output goes to `build/` and is never asserted
byte-for-byte — phrased as a long-term-stable module convention with **no milestone number and no
task-file reference**, per AGENTS.md. Update this task file's Implementation Notes and the checkpoint.

### File Scope

**Created:**
- `ui/preferences/src/test/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/SyncSettingsCaptureHost.kt`
  *(added in Revision 1, D5 — the production-faithful capture host)*
- `ui/preferences/src/test/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/SyncSettingsScreenshotCaptureTest.kt`
- `tasks/artifacts/sync-settings-before-milestone-15b.png`
- `tasks/artifacts/README.md`

**Modified:**
- `ui/preferences/README.md` (Step 3 only)
- `tasks/antennapod-sync-settings-before-screenshot-milestone-15b.md`
- `features/antennapod-sync-settings-before-screenshot-milestone-15b.checkpoint.md`

**Not in scope — a diff touching any of these means the plan was wrong and the task is re-planned, not
patched:** `ui/preferences/build.gradle`, `gradle/libs.versions.toml`, `common.gradle`,
`playFlavor.gradle`, root `build.gradle`, `settings.gradle`, `.editorconfig`, `.gitignore`, `.gitmodules`,
`config/**`, `.github/**`, `scripts/**`; **every file under `ui/preferences/src/main/`** — in particular
all four `screen/synchronization/*.kt` production files, `AnimatedPreferenceFragment.java`,
`res/xml/preferences_synchronization.xml` and `res/layout/settings_activity.xml` (the new capture host
*inflates* this layout and must not modify it); **every file under `ui/common/src/main/`** — in particular
`ToolbarActivity.java`, `res/layout/toolbar_activity.xml` and `res/values/styles.xml`, which the capture
host subclasses and inherits from and which this task must not alter to make the capture easier
*(added in Revision 1, D5)*; **the four existing test files this task reads but must not
edit** — `SyncSettingsTestHost.kt` (D5: editing its theme would null out `supportActionBar` and NPE all
four `SynchronizationPreferencesFragmentLifecycleTest` tests — this is why the fix is a second host),
`RecordingSynchronizationQueue.kt`, `SyncSettingsHarnessSmokeTest.kt`,
`SynchronizationPreferencesFragmentCharacterizationTest.kt` — and the three other existing test files;
`app/**`; `storage/**`; `net/**`; every other module. Also out of scope:
`tasks/antennapod-sync-settings-kotlin-milestone-15.md`,
`tasks/antennapod-sync-settings-modernization-future-work.md` and
`features/antennapod-sync-settings-kotlin-milestone-15.checkpoint.md` — the last two are currently dirty
in the working tree from Milestone 15 and must be resolved onto M15's branch before branching (D1), never
carried into this diff.

**No new user-visible string is added**, so AGENTS.md's `:ui:i18n` rule is not triggered — and adding one
would itself be out of scope.

### Acceptance Criteria

No track. Every item is a command or an inspection with a definite pass/fail.

**The render is real — the criterion that makes this task worth doing**
- [ ] **AC1** — `SyncSettingsScreenshotCaptureTest.kt` carries `@GraphicsMode(GraphicsMode.Mode.NATIVE)`
  **at class level**. `grep -c 'GraphicsMode' <file>` → `>= 2` (import + annotation); `grep -c 'LEGACY'`
  → `0`.
- [ ] **AC2** — `testCapturedBitmapIsNotBlankAndHasExpectedDimensions` passes, asserting distinct ARGB
  count `>= 200` **and** dimensions exactly `822 × 1782`. The actual observed distinct-colour count is
  pasted into Implementation Notes — **363 is no longer the expected value** (it was observed through the
  bare harness; the faithful host of D5 draws different chrome), so the newly observed count is recorded as
  the new reference; anything at or near `1` means D2's hazard fired. A bare `assertNotNull(bitmap)`, a
  non-transparent-pixel check, or a file-size-only check **fails this criterion** even if the test is
  green — those are precisely the assertions that pass on a blank image. **This criterion does not, on its
  own, establish that the render is correct** — a clipped-but-rendered screen scores 352 and would pass it
  (D2). AC3 is the other half of the pair and neither substitutes for the other.
- [ ] **AC3** — `testFirstPreferenceRowIsNotClippedByActionBar` passes: `RecyclerView` childCount `4`;
  `MaterialToolbar` (`R.id.toolbar`) height `> 0`; first row's top in window coordinates `>=` the
  toolbar's bottom in window coordinates. Implementation Notes records the two observed coordinates.
  *(Restated in Revision 1 — the previous form asserted `android.R.id.content` top `> 0`, a proxy for the
  decor ActionBar inset that the faithful host no longer uses.)* **This is the load-bearing guard against
  the 352-colour "rendered but clipped" failure mode, which AC2 provably cannot detect.** Weakening or
  removing it on the grounds that AC2 covers render correctness is a misreading and fails review.
- [ ] **AC4** — `SyncSettingsScreenshotCaptureTest.kt` carries `@Config(sdk = [34], qualifiers =
  "w411dp-h891dp-xhdpi")`. `grep -c 'sdk = \[34\]'` → `1`. AC2's dimension assertion is what proves the
  `qualifiers` string actually took effect rather than being silently ignored.
- [ ] **AC5** — Step 2's visual inspection is recorded as a stated observation, naming all rendered
  elements seen (toolbar title "Synchronization" **with the up arrow**; "Choose synchronization provider"
  **with title visible and full summary**; "Synchronize now"; "Force full synchronization"; "Logout").
  Automated assertions cannot tell "renders text" from "renders the *right* text"; this is the human check
  that can. *(Up arrow added in Revision 1 — it is chrome the faithful host reproduces from
  `PreferenceActivity.java:41-44`, and its absence would mean the host's `supportActionBar` is not the
  `MaterialToolbar`-backed one D5 assumes.)*

**The artifact and its provenance**
- [ ] **AC6** — `tasks/artifacts/sync-settings-before-milestone-15b.png` exists, is a valid PNG at
  822×1782 (`file` output pasted), and its `git hash-object` is recorded. `tasks/artifacts/README.md`
  exists and contains all **seven** items D6 requires — in particular the explicit statement that the
  capture **post-dates** Milestone 15 and does not predate it, the API-34 pin and its **corrected** reason
  (edge-to-edge; the retracted "production has no inset handling" claim appears nowhere), both D7 fidelity
  caveats, the do-not-pixel-diff-against-Paparazzi warning, and D6 item 7's chrome-fidelity statement.

**Nothing else moved**
- [ ] **AC7** — `git diff origin/develop -- ui/preferences/src/test/` shows **exactly two added files**
  (`SyncSettingsCaptureHost.kt`, `SyncSettingsScreenshotCaptureTest.kt`) and **zero modifications** to the
  seven pre-existing test files. `SyncSettingsTestHost.kt` in particular is byte-unchanged — this is what
  makes "the 38 existing tests are unaffected" a checkable fact rather than a claim (D5 fact 3).
  *(Count corrected in Revision 1 — was "exactly one added file".)*
- [ ] **AC8** — Both `./gradlew --console=plain :ui:preferences:testPlayDebugUnitTest --rerun` and
  `:testFreeDebugUnitTest --rerun` are BUILD SUCCESSFUL as **two separate invocations**, each **41 tests,
  0 failures, 0 errors, 0 skipped** (38 pre-existing + 3 new). The per-class table is pasted for both
  flavours; the 38 pre-existing counts match Milestone 15's row for row.
- [ ] **AC9** — After a full `./gradlew --console=plain :ui:preferences:test` run, `git status --porcelain`
  is **empty apart from this task's own File Scope files** (D3). Specifically, running the test four times
  leaves `tasks/artifacts/sync-settings-before-milestone-15b.png` **unmodified** — proving the artifact is
  a frozen baseline and not rewritten on every run.
- [ ] **AC10** — `./gradlew ktlintCheck` is BUILD SUCCESSFUL with `:ui:preferences:ktlintTestSourceSetCheck`
  genuinely **executing** (not `SKIPPED`/`NO-SOURCE`), zero `@Suppress("ktlint:…")` added, no
  `.editorconfig` change and no ktlint exclusion in any build file. `./gradlew checkstyle lint` also
  BUILD SUCCESSFUL.
- [ ] **AC11** — `git diff --name-only origin/develop` lists **only** File Scope files. In particular
  `ui/preferences/build.gradle`, `gradle/libs.versions.toml`, `common.gradle` and every file under
  `ui/preferences/src/main/` are unchanged (D8). Both
  `git diff --stat origin/develop -- ui/preferences/src/main/` and
  `git diff --stat origin/develop -- ui/common/src/main/` are empty — the latter added in Revision 1,
  because the capture host now subclasses `ToolbarActivity` and inherits `toolbar_activity.xml` and
  `styles.xml`, making `:ui:common` a plausible place for scope to leak (D5).
- [ ] **AC12** — `ui/preferences/README.md`'s new convention entry is phrased as a long-term-stable module
  convention with no milestone number and no task-file reference (AGENTS.md).
- [ ] **AC13** *(added in Revision 1, D3)* — **The test is confirmed green on the real CI runner, not only
  locally.** The branch is pushed, the PR opened, and the GitHub Actions run's outcome for both
  `testPlayDebugUnitTest` and `testPlayReleaseUnitTest` on `ubuntu-latest` is pasted into Implementation
  Notes, together with the distinct-colour count and bitmap dimensions the runner observed. Local
  `--rerun` output does **not** satisfy this criterion — every empirical probe behind this plan was run on
  one macOS machine and one JDK, and Research's Characterization gap 3 states cross-machine determinism is
  unverified. If the run is red, D3's pre-decided response table governs, and whichever branch was taken is
  recorded here; **silently disabling, `@Ignore`-ing or CI-excluding the test fails this criterion**
  regardless of what colour the pipeline ends up.

**Not applicable, asserted rather than assumed.** No public API changes and no Java caller can observe
anything — the only new symbol is a test class in `src/test/`, and AC11 proves `src/main/` is untouched.
Accessibility (content descriptions, dynamic type) and dark-mode / hardcoded-colour criteria attach to the
`compose` and `navigation` tracks; neither is in flight, no new UI is created, no layout, theme, drawable
or colour resource is touched, and no composable exists in this module — so no Paparazzi snapshot is
required or possible (Paparazzi is not in the catalog until Milestone 16). No instrumented tests: this
module has no `androidTest` source set and this task adds none. No HSHD is handled — the captured screen
is the **logged-out** state by construction (D5), so no username, password or host URL appears in the
render, in any fixture, or in the checked-in PNG; **AC5's visual inspection doubles as the confirmation
that no credential is visible in the committed image.**

### Milestone

**Milestone 15b — `:ui:preferences` sync-settings "before" screenshot capture. No track; test/tooling
only.** Single PR against `develop`, three steps in two or three commits (Step 1; Step 2; Step 3, which
may fold into Step 2's commit). Deliberately a separate PR from #21, per José's explicit instruction on
2026-08-06.

An addendum to the six-milestone sync-settings sequence (15 `kotlin` → 16 toolchain → 17
`concurrency`+MVVM → 18 `di` → 19 `:storage:preferences` → 20 `compose`), not a member of it — hence
"15b". It exists to serve Milestone 20 and is worthless if it is not there when 20 runs, which is why it
is being done now rather than deferred.

This is unaffiliated OSS portfolio work, so "milestone" is case-study structure, not invoicing. The
case-study angle it earns is small but sharp, and it is about *verification discipline* rather than
migration volume: **the obvious way to write this test — render, write PNG, assert the bitmap is
non-null — passes 100% of the time against a completely blank image, because Robolectric's default
graphics mode fills the bitmap with an opaque solid colour. We found that by measuring distinct colours
(1 when broken, 363 when working) before writing a line of the real test.** That is the same habit that
makes the Java→Kotlin equivalence claims credible, applied to a task most teams would not have tested at
all.

### Out of Scope

- **Any production code change.** All four `screen/synchronization/*.kt` files, `AnimatedPreferenceFragment`,
  and `preferences_synchronization.xml` are read-only here.
- **Editing `SyncSettingsTestHost.kt`** — including to add window-insets handling, and including to change
  its theme to `.NoTitle`. The latter would set `windowActionBar=false` on a bare `AppCompatActivity`,
  nulling `supportActionBar` and NPE-ing all four `SynchronizationPreferencesFragmentLifecycleTest` tests
  plus the fragment's own `onStart`/`onStop`. Production fidelity is obtained by **adding** a second host
  (D5), never by editing the shared one.
- **Editing anything in `:ui:common`** *(added in Revision 1)* — `ToolbarActivity.java`,
  `toolbar_activity.xml`, `styles.xml`. The capture host subclasses and inherits them precisely so that
  nothing about production's chrome is re-implemented here; changing them to make the capture easier would
  destroy the fidelity the change exists to buy, and would alter the shipping app.
- **Reproducing the rest of `PreferenceActivity`** — its EventBus/`MessageEvent` Snackbar registration and
  its `MainPreferencesFragment` + `openScreen()` navigation. Both are deliberate, disclosed departures
  (D5 fact 4); neither affects rendered chrome.
- **Moving the SDK pin off 34**, including to test whether the faithful host renders un-clipped at sdk 36.
  Plausible (D5) but unmeasured, and measuring it is Milestone 20's business, not this task's.
- **Dark and TrueBlack captures.** Light only (D5). Cheaper than it was before this revision — a `-night`
  qualifier or a `UserPreferences` theme write on the new host would do it — but still not requested;
  noted for Milestone 20 if it wants a dark pair.
- **Captures of the logged-in state, of the three auth dialogs, or of any other settings screen.** One
  screen, one state.
- **Any golden-image / screenshot-diff assertion against the committed PNG** (D3). Cross-machine
  byte-determinism is unverified and this plan does not assume it.
- **Paparazzi, and any version-catalog change** (D8). Milestone 16 owns the toolchain.
- **Diagnosing the disabled-row dimming gap, or any device-vs-Robolectric fidelity comparison** (D7).
  Documented as a caveat, not investigated.
- **Any Gradle task-graph change, test filter, `@Ignore`, or CI workflow edit** (D3). The repo gains no new
  convention from this task.
- **The `compose` track and the ViewModel/MVVM layer it requires.** Research restates that
  `SynchronizationPreferencesFragment` reads statics and a sticky EventBus subscription directly, with no
  ViewModel anywhere — a **blocking prerequisite** for `compose`, bespoke architectural work, not part of
  any track. Nothing in this plan creates a ViewModel, a UI-state type or a `ComposeView`. Already tracked
  as Milestone 15's OQ1 and in `tasks/antennapod-sync-settings-modernization-future-work.md`; not
  re-opened here because `compose` is not requested by *this* task.
- **The `concurrency`, `di`, `gradle-kts` and `navigation` tracks.** None requested. `ui/preferences/
  build.gradle` stays Groovy and is not touched at all.
- **Publishing the artifact anywhere** — the marketing repo, a blog post, or sales copy. Copying it out is
  a separate action gated on OQ1 and OQ2.

## Open Questions

Two items, both for José. Neither blocks Steps 1–3; both gate anything *after* the PR.

**OQ1 — Is `tasks/artifacts/` the final home for the PNG, or an interim one?** D6 commits to it and
justifies it: the artifact must live in the same tree as the code that generates it, or the "you can
regenerate this" claim decays, and a PR against this repo cannot write into `web-aepm-labs` anyway. But
the *publishing* home for case-study collateral is plausibly
`/Users/josegarcia/ClaudeEnvironment/aepm-labs/web-aepm-labs/public/images` (the workspace's only
marketing-asset directory, confirmed to exist, and a different repository). This plan's position is that
publishing there is a **copy, not a move** — this repo keeps the provenance record. Confirm, or say where
it should go instead; relocating later is a one-line move plus a README edit.

**OQ2 — Licensing and attribution before this screenshot is published anywhere public.**
`services/android-migration/projects/portfolio/README.md:5-8` requires respecting the upstream project's
licence before publishing modified source, and requires that any write-up state explicitly that no client
relationship exists. A rendered screenshot of AntennaPod's UI is not source, but it does contain the
upstream project's strings, iconography and trade dress. Whether it may appear in AEPM marketing
material, and with what attribution, is a **commercial and legal-adjacent call**, which the root
`CLAUDE.md` says to flag for José rather than decide. Checking the PNG into this repo under D6 does not
publish it and does not pre-empt this. Resolve before the artifact leaves this repository.

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-08-06 | Loop 1 of max 2_

### Verdict
CHALLENGE

### Concerns

- **Severity:** CRITICAL
  **Class:** Silent behavior/fidelity gap presented as a proven equivalence claim (D5)
  **Concern:** D5's justification for pinning `sdk=[34]` rests on the claim "no window-insets handling anywhere" exists in the app's real `PreferenceActivity`, concluding the sdk-36 clipping is a pure harness artifact with no bearing on what a real user sees, and that pinning 34 "renders the screen as laid out, which is what a 'before' baseline is *for*." This claim is verified false by reading source, not just under-evidenced. Production's real host chain for this fragment is `PreferenceActivity extends ToolbarActivity` (`app/src/main/java/de/danoeh/antennapod/ui/screen/preferences/PreferenceActivity.java:31`). `ToolbarActivity` is explicitly documented "ensuring that system insets are properly consumed" (`ui/common/src/main/java/de/danoeh/antennapod/ui/common/ToolbarActivity.java:9`) and its layout (`ui/common/src/main/res/layout/toolbar_activity.xml:7`) sets `android:fitsSystemWindows="true"` on the root `LinearLayout`, with a `MaterialToolbar` consuming the top inset above the `@android:id/content` FrameLayout. `ToolbarActivity.onCreate` also calls `setTheme(ThemeSwitcher.getNoTitleTheme(this))` (`ToolbarActivity.java:16`), which resolves to `Theme.AntennaPod.Light.NoTitle` (`ui/common/src/main/res/values/styles.xml:152-155`) — `windowActionBar=false`, `windowNoTitle=true` — meaning production's visible title bar is the custom `MaterialToolbar`, not a system decor ActionBar. The test harness (`SyncSettingsTestHost.kt:9`) instead calls `setTheme(CommonR.style.Theme_AntennaPod_Light)` — the parent theme, *without* `.NoTitle` — which inherits `android:fitsSystemWindows=false` from `Theme.Base.AntennaPod.Dynamic.Light` (`styles.xml:30`) and leaves `windowActionBar` at its Material3 default (`true`), and the harness attaches the fragment directly to `android.R.id.content` with no toolbar/layout wrapper at all (per Research). So the harness isn't merely missing an sdk-version quirk — it uses the *opposite* `fitsSystemWindows` polarity from production and renders the title bar through a structurally different mechanism (system decor ActionBar vs. custom `MaterialToolbar`). The plan's grep (`enableEdgeToEdge|setDecorFitsSystemWindows|WindowInsets`) could never have found this because the real mechanism is an XML style/layout attribute (`android:fitsSystemWindows`), not one of the three Kotlin/Java APIs searched.
  **Evidence:** `app/src/main/java/de/danoeh/antennapod/ui/screen/preferences/PreferenceActivity.java:31`; `ui/common/src/main/java/de/danoeh/antennapod/ui/common/ToolbarActivity.java:9,16-20`; `ui/common/src/main/res/layout/toolbar_activity.xml:2-7`; `ui/common/src/main/res/values/styles.xml:30,37,152-155`; `ui/preferences/src/test/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/SyncSettingsTestHost.kt:9`; Plan D5 ("no window-insets handling anywhere ... grep ... returns nothing either").
  **Suggested mitigation:** Correct D5's rationale before implementation: state plainly that the harness's theme/layout scaffold structurally diverges from production's real host activity (opposite `fitsSystemWindows` value, system decor ActionBar instead of the app's actual `MaterialToolbar`), and that the sdk-34 pin is a pragmatic workaround for a harness limitation the team chose not to fix (reproducing `ToolbarActivity`'s chrome would need a second, more faithful host activity, which is legitimately out of scope) — not evidence that production has no relevant inset handling to miss. Add this as an explicit, separately-called-out fidelity caveat in `tasks/artifacts/README.md` (D6), at least as prominent as D7's disabled-row caveat, since it means the captured title-bar chrome does not visually match what ships. This does not necessarily change the sdk=34 decision itself, only the justification and the disclosure — but both are currently wrong and would ship an inaccurate provenance claim in a task whose entire pitch is verification rigor.

- **Severity:** MAJOR
  **Class:** Characterization/assertion design not proven to discriminate the failure it claims to guard against (D2)
  **Concern:** D2's 200-floor rationale ("chosen to sit far above any degenerate render and far below the observed value") is calibrated against only two data points: fully blank (1 color) and fully correct-at-the-pinned-config (363 colors). Research's own probe table contains a third, unaddressed data point: `@GraphicsMode(NATIVE)` at the *unpinned* sdk 36 default — a genuinely **broken** render (the first row's title is clipped under the action bar, which is precisely the defect D5's sdk pin exists to avoid) — produced **352** distinct colors, well above the proposed floor of 200 and nearly indistinguishable from the fully-correct 363. This proves the distinct-ARGB floor by itself cannot and does not detect the specific class of "wrong but not blank" render that motivated the sdk pin in the first place; it only detects a total LEGACY-style blank fill. The plan does structurally cover the clipping case through a *separate* test (`testFirstPreferenceRowIsNotClippedByActionBar`, AC3), so the two tests together are probably sound — but D2's own text never acknowledges the 352 data point or states that AC2 and AC3 are a required, non-substitutable pair. Nothing prevents a future edit from treating AC2's color-count assertion as sufficient "render correctness" coverage on its own (it reads that way) and weakening or dropping AC3.
  **Evidence:** Research "Empirical finding 1" table, row 2 (`@GraphicsMode(NATIVE)`, sdk 36 default → 352, "RENDERED, but see clipping below") vs. Plan D2 ("Broken (LEGACY...) → 1. ... Observed at this plan's exact config → 363 ... 200 is chosen to sit far above any degenerate render and far below the observed value").
  **Suggested mitigation:** Revise D2's text (and ideally a code comment on the two test methods) to state explicitly that the distinct-colour floor only detects the LEGACY/blank-fill failure mode, that it does **not** and cannot detect a structurally-clipped-but-otherwise-rendered screen (352 is the proof), and that `testFirstPreferenceRowIsNotClippedByActionBar` is the load-bearing check for that failure mode — the two tests are a required pair, not redundant belt-and-suspenders.

- **Severity:** MAJOR
  **Class:** Risk relocated, not eliminated (D3)
  **Concern:** D3 correctly eliminates the specific risk Research framed ("the checked-in PNG gets rewritten/diffed 2x–4x per run") by never writing the automated test's output anywhere but gitignored `build/`. That part of the reasoning is sound and verified (`.gitignore:8` — `build/`, matches at any depth; no `*.png` ignore rule exists that would also swallow `tasks/artifacts/*.png`). However, the plan's own framing — "this decision dissolves [Research's Unknown 3] rather than answering it" — overstates the result. By deliberately keeping the render-and-assert test in the default task graph (explicitly rejecting isolation, per D3), a graphics-mode-sensitive, environment-sensitive assertion (exact 822×1782 dimensions, distinct-ARGB floor of 200) now runs on **every unrelated PR** that touches this module, on `ubuntu-latest` CI (`.github/workflows/checks.yml:12,20,59,110,169`) — an OS Research never tested against. Research's Characterization gap 3 explicitly says cross-machine determinism is unverified; D3 quotes this gap but only uses it to justify *not* asserting byte-identity against the committed PNG. It does not address the adjacent, more operationally relevant question: will the render even reliably *pass* its own assertions on Linux CI at all? If not, the "harmless" 2×/CI-run repetition Research worried about is replaced by a recurring CI-red nuisance on every unrelated PR — arguably a worse outcome than a dirty working tree, since it blocks merges repo-wide rather than just this task's. Nothing in Steps or Acceptance Criteria requires observing an actual green run on the real GitHub Actions runner (as opposed to local invocations) before the task is considered complete.
  **Evidence:** Plan D3 ("this decision dissolves it rather than answering it"); Research Characterization gap 3 ("Cross-machine determinism is unverified... should not be assumed"); `.github/workflows/checks.yml:12,20,59,110,169` (`runs-on: ubuntu-latest`); Plan Steps 1–3 and AC8 (local `--rerun` invocations only, no CI-run confirmation required).
  **Suggested mitigation:** Add an explicit step or AC requiring the implementer to push the branch, open the PR, and paste the actual CI run's result (not just local `--rerun` output) before considering Step 1/2 done — this is cheap (branch protection likely already requires it before merge) but currently isn't called out as a gate specific to this plan's known-untested risk. If CI is red due to the color-count floor or dimension assertion, that is exactly the scenario D3's own reasoning didn't rule out.

- **Severity:** MINOR
  **Class:** Milestone/process ambiguity (D1)
  **Concern:** D1 tells the implementer to resolve the two pre-existing dirty M15 files by "commit them onto M15's branch or stash them," presented as an equivalent either/or choice. This session's `git status --porcelain` confirms both files are still dirty exactly as the plan states (`M features/antennapod-sync-settings-kotlin-milestone-15.checkpoint.md`, `M tasks/antennapod-sync-settings-modernization-future-work.md`), so the plan's factual premise here is accurate. But "commit them onto M15's branch" is of limited value on its own now that PR #21 has merged — a commit added to `kotlin/ui-preferences-sync-settings` post-merge doesn't land anywhere unless a separate follow-up PR is opened from it, which D1 doesn't mention. This is low-materiality (AC7 and AC11 already guard against these files leaking into *this* task's diff regardless of which option is picked) but could cause the implementer to make an undocumented judgment call, or silently lose the edits via an unpopped stash.
  **Evidence:** D1 ("Resolve them before branching — commit them onto M15's branch or stash them"); `git log --oneline -1 origin/develop` → `f5d4c5551` (PR #21 already merged, confirmed this session); `git status --porcelain` (both files still `M`, confirmed this session).
  **Suggested mitigation:** State a single recommended path (stash is simplest and reversible; if committing, note it requires its own small follow-up PR to actually land) rather than presenting both as equally sufficient.

### Categories checked with no finding
- **Artifact location collision (parent's point 4):** Re-verified this session — `app/src/main/play/` is confirmed a git submodule (`.gitmodules`) pointing at a different repo; root-level `screenshots/` does not exist as a committed directory (only as `scripts/createScreenshots.sh`'s uncommitted scratch output); `.gitignore` has no rule that would swallow `tasks/artifacts/*.png` or `tasks/artifacts/README.md`; `tasks/artifacts/` does not yet exist. D6's chosen path is clean.
- **Public API breakage:** No production file is touched (confirmed via File Scope and Out of Scope sections); no concern.
- **Milestone/scope creep:** Diff stays within File Scope as described; no MVVM/architecture work is introduced; D4's correction of the pre-research context's stale "identical rendering path" rationale is itself a good sign of the plan checking its own inherited assumptions (just not far enough, per the CRITICAL finding above).
- **`concurrency`/`di`/`gradle-kts`/`compose`/`navigation` track-specific checklist items:** Not applicable — no track runs in this task, confirmed by Research and Plan Objective ("Test-only, no track").
- **HSHD:** Logged-out state only, no credential fields in view hierarchy; confirmed by Research's Empirical finding 4 and Plan's "Not applicable" AC block.


## Plan — Revision 1 (responding to red-team loop 1)
_Last updated by: legacy-android-planner | 2026-08-06_

**All four findings accepted. All four were re-derived from source this session before acceptance rather than taken on the report's word, and all four checked out.** Unlike Milestone 15's Revision 1 — where every finding turned out to be a defect in the plan's *verification apparatus* — the CRITICAL finding here is a defect in a **decision**, and correcting it changes what the developer will actually write: one new file, a different fragment container, a rewritten assertion, and a chrome-fidelity statement in the artifact's own README.

The through-line is worth naming, because it is the same failure this pipeline exists to prevent, committed by the plan itself. D5 asserted a negative ("no window-insets handling anywhere") on the strength of a grep for three Kotlin/Java APIs, when the mechanism in question is an XML attribute. The grep could not have found it. A plan whose entire pitch is that legacy behaviour gets *pinned* rather than *assumed* published an assumption dressed as a verified fact — and that fact was going to ship, verbatim, as a provenance sentence in a public case-study artifact.

### Verification I performed this loop

- **Finding 1 (D5's factual basis) — confirmed, in full, and it is worse than a wrong justification.** Read the whole chain: `PreferenceActivity.java:31` (`extends ToolbarActivity`), `ToolbarActivity.java:9` (javadoc: "ensuring that system insets are properly consumed"), `:16` (`setTheme(ThemeSwitcher.getNoTitleTheme(this))` before `super.onCreate`), `:19` (`setSupportActionBar(viewBinding.toolbar)`), `:20` (`super.setContentView(viewBinding.getRoot())`), `:23-27` (`setContentView(View)` routes into `viewBinding.content`); `toolbar_activity.xml:7` (`android:fitsSystemWindows="true"` on the root `LinearLayout`, `MaterialToolbar` above an `@android:id/content` `FrameLayout`); `styles.xml:152-155` (`Theme.AntennaPod.Light.NoTitle` → `windowActionBar=false`, `windowNoTitle=true`) vs. `styles.xml:30` (`android:fitsSystemWindows=false` on `Theme.Base.AntennaPod.Dynamic.Light`, which `Theme.AntennaPod.Light` inherits at `styles.xml:4,12`). The harness (`SyncSettingsTestHost.kt:9`) uses the parent theme without `.NoTitle`. Opposite `fitsSystemWindows` polarity, different title-bar mechanism — exactly as reported.
- **And one thing the report did not reach, which changed the fix.** Production does **not** put the fragment in `@android:id/content` at all. `PreferenceActivity.java:46-47` inflates `SettingsActivityBinding` and `:49-52` commits the fragment into `binding.settingsContainer` — a `FragmentContainerView` from `ui/preferences/src/main/res/layout/settings_activity.xml`, sitting inside `toolbar_activity.xml`'s content `FrameLayout`. This matters mechanically: `toolbar_activity.xml`'s inner `FrameLayout` *also* carries `@android:id/content`, so a fragment transaction targeting `android.R.id.content` on a `ToolbarActivity` resolves against the **decor's** content view first and would attach the fragment as a sibling of the toolbar layout, overlaying the toolbar. A "make the host faithful, keep the `android.R.id.content` attach" fix would have rendered a worse screenshot than the one being replaced, and would have looked correct in a diff.
- **The 38-test impact question the task set me — answered, and it decided the shape of the fix.** Four of the 38 assert on the action bar: `SynchronizationPreferencesFragmentLifecycleTest.kt:72, 79-84, 99-105, 114-120`, all reading `activity.supportActionBar!!.title`/`.subtitle`. The fragment itself dereferences `supportActionBar!!` at `SynchronizationPreferencesFragment.kt:41` (`onStart`) and `:51` (`onStop`). So switching the shared harness to a `.NoTitle` theme would set `windowActionBar=false` on a bare `AppCompatActivity`, return **null** from `getSupportActionBar()`, and NPE all four tests *and* every other test that drives the fragment through `onStart`. That rules out editing `SyncSettingsTestHost` and rules **in** a second host — which costs nothing, since a second host leaves the existing file byte-unchanged and no existing test references the new one. The other 34 tests assert only fragment-internal state (`Preference.title`, `isEnabled`, `isVisible`, adapter contents, `ShadowDialog`) and are indifferent either way.
- **Feasibility of the second host — checked before committing the plan to it.** `ui/preferences/build.gradle:34` declares `implementation project(":ui:common")` (on the module's own test compile classpath); `common.gradle:65-66` enables `viewBinding` repo-wide, so `SettingsActivityBinding` exists and is the same class `PreferenceActivity` imports; `UserPreferences.getIsThemeColorTinted()` is `SDK_INT >= 31 && prefs.getBoolean(PREF_TINTED_COLORS, false)` (`UserPreferences.java:214-215`) → **false** on a fresh install, and `getTheme()` defaults to `SYSTEM` → `LIGHT` under a non-`night` qualifier, so `ThemeSwitcher.getNoTitleTheme` lands on `Theme.AntennaPod.Light.NoTitle` — the non-dynamic Light permutation, which is both what production shows a fresh-install user and the same Light family the old harness picked. The theme decision therefore did not have to change to make the chrome faithful.
- **Finding 2 (D2's floor vs. the 352 render) — confirmed.** Research's Empirical finding 1 table, row 2: `@GraphicsMode(NATIVE)`, sdk 36 default → **352** distinct colours, annotated "RENDERED, but see clipping below" — i.e. a genuinely broken render scoring above the proposed 200 floor and within 3% of the correct 363. D2's calibration text cited only 1 and 363 and never mentioned it.
- **Finding 3 (D3's residual CI risk) — confirmed.** `.github/workflows/checks.yml:12,20,59,110,169` → `runs-on: ubuntu-latest`. Every empirical probe behind this plan ran on one macOS machine and one JDK (Research's "Empirical runs" block). Research's Characterization gap 3 says cross-machine determinism is unverified; D3 used that only to refuse a byte-diff and never asked whether the test *passes* on Linux at all. Also corrected while in there: D3 cited `.gitignore:5` for the `build/` rule; it is `.gitignore:8`.
- **Finding 4 (D1's either/or) — confirmed.** Both files still dirty (`M features/antennapod-sync-settings-kotlin-milestone-15.checkpoint.md`, `M tasks/antennapod-sync-settings-modernization-future-work.md`); `origin/develop` is `f5d4c5551`, so `kotlin/ui-preferences-sync-settings` is merged and stale and a commit added to it lands nowhere on its own.

### Edits made, in place in the Plan above

**CRITICAL — D5 rewritten around a second, production-faithful host activity.**

1. **D5 opens with an explicit retraction**, quoting the false claim and naming why the grep behind it could not have found the mechanism (XML attribute, not one of the three APIs searched). The retraction is kept in the document rather than deleted, so a later reader can see what changed and why — and because D6 item 3 now forbids the retracted sentence from reaching the artifact's README, which only makes sense if the reader can see what is being forbidden.
2. **A five-row comparison table** states production's chain against the harness's, layer by layer, with file:line for each cell.
3. **New file: `SyncSettingsCaptureHost.kt`**, `: ToolbarActivity()`, mirroring `PreferenceActivity.java:37-47` — `super.onCreate`, `setDisplayHomeAsUpEnabled(true)`, `setContentView(SettingsActivityBinding.inflate(layoutInflater).root)`. It re-implements nothing: production's own base class, production's own layouts, production's own theme resolution.
4. **The fragment container changed from `android.R.id.content` to `R.id.settingsContainer`**, with the reason stated — the duplicate-id trap above. This is the one edit a reviewer is most likely to read as gratuitous, so it is justified at the point of use in Step 1 as well as in D5.
5. **Four supporting facts recorded with their evidence** (classpath reachability, theme resolution, 38-test impact, the two disclosed departures from `PreferenceActivity`), plus two consequences that are easy to trip over later: the `qualifiers` string must never gain `-night`, and `UserPreferences.init(context)` in `setUp()` is now load-bearing rather than incidental because `ThemeSwitcher` reads it before `super.onCreate`.
6. **The sdk-34 pin is kept, with a rationale that is actually true**: it renders at an API level predating edge-to-edge enforcement, so the capture does not depend on Robolectric's unverified inset simulation. It is *not* evidence that production lacks inset handling. Noted for M20, explicitly as unmeasured: with a real toolbar sibling rather than decor overlay, the clipping mode may not be reachable at sdk 36 at all.
7. **A bounded contingency that stops rather than degrades.** `ToolbarActivity` has never been driven under Robolectric in this repo. If it cannot be stood up, the developer stops and reports; falling back to the bare harness is José's call and carries a mandatory top-of-file warning in the artifact README naming the divergence precisely — with the *correct* theme identified (`Theme.AntennaPod.Light` vs. `Theme.AntennaPod.Light.NoTitle`), never the retracted claim.

**MAJOR — D2 now states what its floor can and cannot detect, and pairs AC2 with AC3.**

8. **A three-row table added to D2** — blank/1, clipped/352, correct/363 — with a column for which criterion catches each. The 352 row is the point: the floor detects only total render collapse.
9. **AC2 and AC3 declared an independent, non-substitutable pair** in D2, in AC2 ("does not, on its own, establish that the render is correct"), and in AC3 ("the load-bearing guard against the 352-colour failure mode, which AC2 provably cannot detect… weakening it on the grounds that AC2 covers render correctness is a misreading and fails review"). Each of the two test methods carries a one-line comment naming the other, so the pairing survives being read one method at a time — which is how it would have been lost.
10. **AC2's expected value un-pinned from 363.** That number came from the bare harness; the faithful host draws different chrome, so the observed count will move and is recorded as the new reference. The floor of 200 is unchanged and is exactly why the change is absorbable without re-planning.

**MAJOR — D3's overclaim withdrawn, residual risk named, and a real-CI gate added.**

11. **"Dissolves it rather than answering it" struck**, replaced with "eliminates the tracked-tree half and accepts, with a gate, the environment-sensitivity half." The dirty-working-tree reasoning is untouched and still correct.
12. **New "Residual risk" block** stating the cost of keeping an environment-sensitive assertion in the default task graph: it runs twice per CI run on `ubuntu-latest`, an OS/JDK no probe was run against, on every unrelated PR touching this module.
13. **New AC13 — a real GitHub Actions run is a merge gate**, with the runner's observed colour count and dimensions pasted into Implementation Notes. Local `--rerun` output explicitly does not satisfy it. Step 3 renamed and extended to carry the push/PR/paste action.
14. **Tolerances decided per-assertion rather than uniformly**, with reasons: dimensions stay **exact** (they derive from the qualifier string, not font rasterisation, and the exact check is what proves the qualifier took effect), the colour check stays a **floor** (163 colours of headroom *is* the cross-environment tolerance).
15. **A pre-decided response table for a red CI run** — dimension mismatch → stop and disclose; colour shortfall but non-degenerate → lower the floor 30% below observed and document; count near 1 → stop, that is a real Linux/Robolectric finding. `@Ignore`/CI-exclusion is named as **not** a permitted response, since it would re-open Characterization gap 1 while the plan claims it closed. Also fixed the `.gitignore:5` → `:8` citation.

**MINOR — D1 now states one path and completes it.**

16. **`git stash push -- <two paths>` is the single recommended path**, with the exact command, a requirement to record the stash ref in Implementation Notes (an unrecorded stash is how these edits actually get lost), and a `git status --porcelain` must-be-empty check before `git checkout -b`.
17. **The follow-up is stated, which is what was missing**: the files are recovered on their own `docs/<subject>` branch and their own small PR — not on the merged, stale `kotlin/ui-preferences-sync-settings`, and not folded into this task's PR, which AC7/AC11 forbid. Committing instead of stashing is permitted only with the same follow-up.

### Counts and shape

**Unchanged:** **three Steps** and their order; the no-characterization-step reasoning; **D4, D6's location choice, D7, D8** in substance; the Milestone framing; **OQ1 and OQ2**; the "Not applicable, asserted rather than assumed" block; every existing test file and every production file staying untouched.

**Changed:** **D5** (rewritten), **D2** (detection-scope table, AC pairing), **D3** (framing, residual-risk block, tolerance rationale, response table, one citation), **D1** (single path plus follow-up), **D6** (item 3 constrained, item 7 added — six required README contents become seven), **Objective**, **Step 1** (new host file, container id, restated assertion, comments), **Step 2** (up arrow, seven README items), **Step 3** (CI confirmation), **File Scope** (one file added to Created; `ui/common/src/main/**` added to Not-in-scope), **AC2, AC3, AC5, AC6, AC7, AC11** (restated), **AC13** (new — twelve criteria become thirteen), **Out of Scope** (three bullets added, one rewritten).

**Net effect on the diff the developer will write:** one new file, and one screenshot that shows the chrome that actually ships. `SyncSettingsCaptureHost.kt` did not exist in the approved plan; the capture test now attaches to `R.id.settingsContainer` instead of `android.R.id.content`; `testFirstPreferenceRowIsNotClippedByActionBar` asserts against the `MaterialToolbar`'s bottom instead of the decor content's top; and the PR does not close until a real Linux CI run has been observed green. Everything else is the same work.

**What this revision does not do:** it does not verify that `ToolbarActivity` runs under Robolectric — nothing in this repo has ever done that, establishing it costs a probe run, and D5's contingency exists precisely because the plan will not assert it without measuring. That is the honest boundary of what a planning loop can settle here, and it is stated in the plan rather than left for the developer to discover.

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-08-06 | Loop 2 of max 2_

### Verdict
APPROVE

### Concerns

- **Severity:** MINOR
  **Class:** Environment-sensitivity not fully closed (adjacent to D3/D5)
  **Concern:** D5 states the `qualifiers` string "must never gain `-night`, or the captured theme flips silently," correctly identifying that theme resolution is now live (routed through `ThemeSwitcher`/`UserPreferences`) rather than hardcoded, unlike the old harness. The plan asserts the current qualifiers string (`w411dp-h891dp-xhdpi`, no night qualifier) resolves to non-night and therefore `LIGHT`, but this specific host+qualifier combination was never empirically probed the way the sdk/qualifiers-for-dimensions combination was in Research's Empirical finding 1 — the reasoning is correct by inspection of `ThemeSwitcher.readThemeValue` (`Configuration.UI_MODE_NIGHT_MASK` unset → `else` branch → `LIGHT`) and Robolectric's well-established default of non-night `Configuration.uiMode`, but it is inference, not measurement, in a plan that otherwise holds itself to "verified from source this session" standards.
  **Evidence:** Plan D5 fact 2 ("the `qualifiers` string must never gain `-night`, or the captured theme flips silently"); `ThemeSwitcher.java:44-51` (`readThemeValue`, `else` branch → `LIGHT` when `UI_MODE_NIGHT_MASK` is not `UI_MODE_NIGHT_YES`).
  **Suggested mitigation:** No plan change required before implementation — Step 1's own visual inspection (AC5, confirming the Light-family chrome renders as expected) will catch a theme misresolution if the inference is wrong, so this is self-correcting within the plan's existing steps. Worth a one-line note in Implementation Notes if the observed theme ever looks off from what's expected, but not worth blocking on.

### Verification performed this loop

Re-derived from source rather than trusting the revision's report, per the task's five specific asks:

1. **Content-id collision (the mechanical claim behind the new host).** Read `ui/common/src/main/res/layout/toolbar_activity.xml:16` directly: the inner `FrameLayout` carries `android:id="@android:id/content"` — a literal reuse of the system content-view id, not a same-named local id. Confirmed via `Activity.findViewById` semantics (preorder search, first match wins, ancestor found before descendant) that a `replace(android.R.id.content, …)` on `ToolbarActivity` would resolve against the true decor content view and attach the fragment as an overlay sibling of the whole toolbar layout — exactly the "looks plausible, structurally wrong" failure the revision describes. Real, not overstated.
2. **Attach-point match.** `PreferenceActivity.java:46-52` commits into `binding.settingsContainer.getId()`; `settings_activity.xml` declares `<FragmentContainerView android:id="@+id/settingsContainer">`, an ordinary app-local id with no collision risk. The plan's chosen attach point for `SyncSettingsCaptureHost` matches production's real one exactly.
3. **The four cited LifecycleTest assertions.** Read `SynchronizationPreferencesFragmentLifecycleTest.kt` end to end: lines 72, 79, 84, 99-100, 104-105, 114-115, 120 all dereference `activity.supportActionBar!!.title`/`.subtitle` against a `SyncSettingsTestHost`-hosted activity. Confirmed a `.NoTitle` theme change on the shared host would null `getSupportActionBar()` and NPE all four, plus the fragment's own `onStart`/`onStop` (`SynchronizationPreferencesFragment.kt:41,51`). `SyncSettingsTestHost.kt` itself confirmed still byte-identical/unedited. This is a genuine, checked reason for a second host rather than an edit.
4. **D1/D2/D3 corrections.** `.gitignore:8` and `.github/workflows/checks.yml` lines 12/20/59/110/169 (`runs-on: ubuntu-latest`) both confirmed to match the plan's citations exactly. AC2/AC3 now read as a declared non-substitutable pair with the 352-colour case named explicitly. AC13 requires a real pushed CI run pasted into Implementation Notes and explicitly disqualifies local-only `--rerun` output, with a pre-decided response table for a red run. D1 now gives a single recommended path (stash, with ref recorded) and the follow-up the loop-1 finding said was missing (recovery via a fresh `docs/` branch, not the stale merged M15 branch).
5. **ToolbarActivity/Robolectric precedent.** Repo-wide grep for `ToolbarActivity` across all `.kt`/`.java` outside `build/` shows it referenced only by production consumers (`PreferenceActivity`, `OpmlImportActivity`, `WidgetConfigActivity`) and never by any test file. The "never driven under Robolectric in this repo" claim holds, and the plan's response (bounded stop-and-report contingency, not a silent assumption) is the correct level of caution given the claim is true.

Also independently spot-checked and confirmed accurate: `ui/preferences/build.gradle:34` (`implementation project(":ui:common")`), `common.gradle:65-66` (`viewBinding true`), `UserPreferences.java:214-215` (`getIsThemeColorTinted`), `UserPreferences.java:178-187` (`getTheme()` default `SYSTEM`), `ThemeSwitcher.java:10-23,42-57`, and `styles.xml:30,152-156` (fitsSystemWindows polarity and `.NoTitle` attribute set).

### Categories checked with no new finding
- **Public API breakage:** Still none — no `src/main` file touched, `AC11` still guards `:ui:common/src/main/` and `:ui:preferences/src/main/` both being empty in the diff.
- **Milestone/scope creep:** The new host class stays under `src/test/`, subclasses production's own `ToolbarActivity` without modifying it, and File Scope's Not-in-scope list was correctly extended in Revision 1 to cover `:ui:common/src/main/**`. No architecture change, no MVVM, no track introduced.
- **Characterization-test coverage:** Unchanged from loop 1's assessment — this task adds no production behavior, so the characterization-tests-first rule doesn't apply; the capture test's own render-correctness assertions (now AC2+AC3 as a pair) are the relevant coverage and are sound.
- **HSHD:** Unchanged — logged-out state only, confirmed again this loop by re-reading the fixture setup; no credential field is in the view hierarchy.
- **`concurrency`/`di`/`gradle-kts`/`compose`/`navigation` track checklist items:** Not applicable, no track runs in this task.

### Recommendation
Two loops, two rounds of independently-verified, source-grounded corrections — all four loop-1 findings were re-derived from source (not taken on the planner's word) and confirmed both accurate and properly fixed, with one additional structural defect (the content-id collision) caught and fixed in the process that loop 1 itself didn't reach. This plan is ready to hand to `android-migration-developer`. The one MINOR item above (unmeasured night-qualifier inference) is self-correcting via the plan's own Step 1 visual-inspection gate (AC5) and does not need a third loop.

## Implementation Notes
_By: android-migration-developer | 2026-08-07_

### Commit message

```
test(ui:preferences): add production-faithful before-screenshot capture (Milestone 15b)

Add SyncSettingsCaptureHost (extends production's ToolbarActivity, inflates
production's settings_activity.xml into R.id.settingsContainer) and
SyncSettingsScreenshotCaptureTest, a Robolectric native-graphics test that
renders the logged-out Sync Settings screen to a real Bitmap and asserts it
is genuinely non-blank (distinct-ARGB floor of 200, D2) and un-clipped
(first row vs. MaterialToolbar bottom, D2/D5). Check in the rendered PNG at
tasks/artifacts/sync-settings-before-milestone-15b.png as a frozen "before"
baseline for Milestone 20's future Compose comparison, with a provenance
README stating the capture postdates Milestone 15's Kotlin conversion.
Record this module's Robolectric native-graphics testing convention in
ui/preferences/README.md.

Test/tooling only, no production code change. Milestone 15's 38 existing
tests and SyncSettingsTestHost are byte-unchanged.
```

### D1 (git) — executed exactly as revised

```
$ git stash push -m "M15 spec leftovers (see 15b D1)" -- \
    features/antennapod-sync-settings-kotlin-milestone-15.checkpoint.md \
    tasks/antennapod-sync-settings-modernization-future-work.md
Saved working directory and index state On kotlin/ui-preferences-sync-settings: M15 spec leftovers (see 15b D1)

$ git stash list | head -1
stash@{0}: On kotlin/ui-preferences-sync-settings: M15 spec leftovers (see 15b D1)
```

**Stash ref: `stash@{0}`, message `M15 spec leftovers (see 15b D1)`. Recorded here per D1's explicit requirement.** After the stash, `git status --porcelain` was not literally empty — it showed this task's own two untracked spec-scaffold files (`tasks/antennapod-sync-settings-before-screenshot-milestone-15b.md`, `features/antennapod-sync-settings-before-screenshot-milestone-15b.checkpoint.md`), which are this task's own File Scope files, not M15 leftovers. The actual intent — no M15 dirty files riding along — was satisfied and is what mattered.

```
$ git fetch origin
$ git merge-base --is-ancestor f5d4c5551 origin/develop && echo "M15 present"
M15 present
$ git checkout -b test/ui-preferences-sync-settings-before-screenshot origin/develop
Switched to a new branch 'test/ui-preferences-sync-settings-before-screenshot'
$ git log --oneline -1
f5d4c5551 kotlin/ui-preferences: convert screen/synchronization/ (4 files) to Kotlin, stand up module's first test suite (#21)
```

Branch `test/ui-preferences-sync-settings-before-screenshot`, based on a freshly fetched `origin/develop` at `f5d4c5551`. **Stash re-verified still intact at the time of writing this section** (`git stash list` → `stash@{0}` unchanged, containing exactly the two M15 files with their expected edits) — not popped, not needed for anything in this task's own diff, and not touched by this task's commits. It is recovered separately, per D1, on its own `docs/<subject>` branch and its own small PR — out of scope for this task.

### Step 1 — capture host and test

Created `SyncSettingsCaptureHost.kt` (`: ToolbarActivity()`, mirrors `PreferenceActivity.java:37-47`) and `SyncSettingsScreenshotCaptureTest.kt` exactly per the Plan. `ToolbarActivity` stood up under Robolectric on the first attempt — D5's contingency (stop-and-report if it doesn't work within a debugging session) was not needed.

First isolated run:
```
$ ./gradlew --console=plain :ui:preferences:testPlayDebugUnitTest --tests "*SyncSettingsScreenshotCaptureTest*" --rerun-tasks
SyncSettingsScreenshotCaptureTest > testFirstPreferenceRowIsNotClippedByActionBar PASSED
SyncSettingsScreenshotCaptureTest > testWritesPngUnderModuleBuildDirectory STANDARD_OUT
    Screenshot written to: .../ui/preferences/build/reports/screenshots/sync_settings_before_milestone_15b.png
SyncSettingsScreenshotCaptureTest > testWritesPngUnderModuleBuildDirectory PASSED
SyncSettingsScreenshotCaptureTest > testCapturedBitmapIsNotBlankAndHasExpectedDimensions PASSED
BUILD SUCCESSFUL in 11s
```
All 3 passed first try. The printed path resolved under `ui/preferences/build/`, confirming D3's expectation (no tracked file touched by the test itself).

ktlint found two style violations on first pass (import ordering, a trailing comma) — fixed, re-verified `ktlintCheck` green afterward, and re-ran all tests to confirm the style fix changed no behavior.

### Step 2 — artifact

Visually inspected the PNG (required by AC5): toolbar shows "Synchronization" **with the up arrow**, then "Choose synchronization provider" with full title and summary, "Synchronize now", "Force full synchronization", "Logout" — all five elements, nothing clipped. The three disabled rows render at the same weight as the enabled header (D7's disclosed fidelity gap, confirmed present).

Copied the rendered PNG to `tasks/artifacts/sync-settings-before-milestone-15b.png`:
```
$ file tasks/artifacts/sync-settings-before-milestone-15b.png
PNG image data, 822 x 1782, 8-bit/color RGBA, non-interlaced
$ git hash-object tasks/artifacts/sync-settings-before-milestone-15b.png
3e74162d304cde8d30f68caa3eb6fc72f8cd7582
```
Wrote `tasks/artifacts/README.md` with all seven D6 items (verified present by grep against the actual file: postdates-M15/does-not-predate statement, commit + fully-qualified test name + regen command, API-34 corrected rationale, both D7 caveats, the do-not-pixel-diff-against-Paparazzi warning, the portfolio/no-client statement, and the chrome-fidelity statement naming the two disclosed departures).

### Step 3 — verification sweep

Added convention item 9 to `ui/preferences/README.md` (no milestone number, no task-file reference — grepped the addition for both and found neither, confirming AC12).

### Test commands run

- `./gradlew --console=plain :ui:preferences:testPlayDebugUnitTest --rerun-tasks` — **before: N/A (new test), after: PASS — 41 tests, 0 failures** (38 pre-existing + 3 new; per-class counts match Research's table row for row: `AuthenticationDialogCharacterizationTest` 4, `GpodderAuthenticationFragmentCharacterizationTest` 9, `NextcloudAuthenticationFragmentCharacterizationTest` 6, `SyncSettingsHarnessSmokeTest` 6, `SynchronizationPreferencesFragmentCharacterizationTest` 8, `SynchronizationPreferencesFragmentLifecycleTest` 4, `AuthenticationDialogJavaInteropTest` 1, `SyncSettingsScreenshotCaptureTest` 3 — new).
- `./gradlew --console=plain :ui:preferences:testFreeDebugUnitTest --rerun-tasks` — **PASS — 41 tests, 0 failures**, same per-class breakdown.
- `./gradlew --console=plain :ui:preferences:test --rerun-tasks` (all four variants, AC9's sweep) — **PASS — 164 tests (41×4), 0 failures**, `BUILD SUCCESSFUL`. `git status --porcelain` after: empty apart from this task's own File Scope files. Artifact hash unchanged before/after (`3e74162d304cde8d30f68caa3eb6fc72f8cd7582`) — confirms the checked-in PNG is a frozen baseline, not rewritten by the test suite.
- `./gradlew --console=plain ktlintCheck` (repo-wide, per AC10) — **PASS, BUILD SUCCESSFUL**, `:ui:preferences:ktlintTestSourceSetCheck` genuinely executed (not `SKIPPED`/`NO-SOURCE`).
- `./gradlew checkstyle lint` (repo-wide, per AGENTS.md and AC10) — **FAILS**, but for reasons wholly unrelated to this diff: `:app-wearos:compilePlayDebugKotlin` (pre-existing Kotlin compile error), `:net:download:service:spotbugsPlayDebug` and `:app:spotbugsPlayDebug` (pre-existing SpotBugs violations). Confirmed via `git diff --name-only f5d4c5551..HEAD` that none of these modules or files are touched by this branch. Scoped to the actually-affected module — `./gradlew --console=plain :ui:preferences:checkstyle :ui:preferences:lint` — **PASS, BUILD SUCCESSFUL**. This is the same pre-existing gap that later surfaced in real CI (see AC13 below); documenting once here rather than treating it as this task's failure.

### Characterization test results

This task adds no production behavior (Plan's explicit, red-team-approved position), so the characterization-tests-first rule doesn't apply in its usual before/after-a-conversion sense — there is no conversion step here to pin behavior around. The three new tests are this task's actual verification surface, in place of characterization tests, and are reported the same way:

| Test | Result | What it actually exercises |
|---|---|---|
| `testCapturedBitmapIsNotBlankAndHasExpectedDimensions` (AC2) | PASS | Renders the real fragment through the real host and asserts the bitmap is exactly 822×1782 and has **364** distinct ARGB values (cross-checked independently via PIL against the same PNG — the in-test count and this figure agree to within the counting-method's own precision). 364, not the bare-harness's 363, because the faithful host's chrome differs, as the Plan anticipated. Detects total render collapse (would read 1 if `@GraphicsMode` regressed to `LEGACY`) — **does not** by itself prove the render is structurally correct (D2's own point). |
| `testFirstPreferenceRowIsNotClippedByActionBar` (AC3) | PASS | Asserts `RecyclerView` childCount is 4 and the first row's top (128px in window coordinates, measured) is `>=` the `MaterialToolbar`'s bottom (128px — they are exactly flush, no clipping and no unexplained gap). This is the load-bearing guard against the 352-colour "rendered but clipped" failure the bare-harness sdk-36 default would have produced; AC2 cannot detect this class of failure on its own. |
| `testWritesPngUnderModuleBuildDirectory` | PASS | Confirms the PNG lands under `ui/preferences/build/reports/screenshots/` (gitignored), never in a tracked path, and is a real, non-trivial file (`> 10_000` bytes; actual observed size matches an 822×1782 24-bit-plus-alpha PNG). |

The 38 pre-existing characterization tests (Milestone 15's) were run before and after every step of this task and stayed green throughout, byte-unchanged in source (`SyncSettingsTestHost.kt` and its six sibling test files are untouched — confirmed via `git diff origin/develop -- <path>` returning empty for each).

### Acceptance Criteria — verified independently, not just claimed

- **AC1** — `grep -c 'GraphicsMode'` → `2` (import + class-level annotation). `grep -c 'LEGACY'` → `0`. PASS.
- **AC2** — dimensions exactly 822×1782; distinct-ARGB count **364** (new reference, superseding the bare-harness's 363, as the Plan anticipated). PASS.
- **AC3** — childCount 4; toolbar height > 0; firstRowTop (128) >= toolbarBottom (128). PASS.
- **AC4** — `grep -c 'sdk = \[34\]'` → `1`. PASS.
- **AC5** — visual inspection recorded above (Step 2), all five elements plus up arrow. PASS.
- **AC6** — PNG exists, 822×1782, hash `3e74162d304cde8d30f68caa3eb6fc72f8cd7582`; README's all seven required items confirmed present by direct grep against the file (see Step 2). PASS.
- **AC7** — `git diff --name-status f5d4c5551 -- ui/preferences/src/test/` → exactly two `A` (added) lines, zero modifications; `SyncSettingsTestHost.kt` diff is empty. PASS.
- **AC8** — both flavours BUILD SUCCESSFUL, 41/41 each, per-class table matches Research row for row (above). PASS.
- **AC9** — full `:ui:preferences:test` (164 tests, 0 failures) leaves `git status --porcelain` clean apart from File Scope files, and the artifact PNG hash is unchanged before/after. PASS.
- **AC10** — `ktlintCheck` (repo-wide) BUILD SUCCESSFUL, `ktlintTestSourceSetCheck` genuinely executed. `checkstyle lint` (repo-wide) fails on pre-existing, out-of-scope issues (detailed above and under AC13); scoped `:ui:preferences:checkstyle`/`:ui:preferences:lint` both BUILD SUCCESSFUL. **Partial-PASS with disclosed, unrelated cause** — see Deviations.
- **AC11** — `git diff --stat f5d4c5551 -- ui/preferences/src/main/ ui/common/src/main/ ui/preferences/build.gradle gradle/libs.versions.toml common.gradle playFlavor.gradle` → empty. PASS.
- **AC12** — `ui/preferences/README.md` item 9 grepped for `Milestone`/`15b`/`tasks/antennapod` → no matches. PASS.
- **AC13 — CANNOT BE SATISFIED IN ITS LITERAL FORM, for a reason unrelated to this implementation. Documented rather than claimed or silently dropped.**

  The branch was pushed and PR #22 opened (https://github.com/josegbel/antenna-pod-kt/pull/22) as required. GitHub Actions did not trigger any run for over an hour despite Actions being enabled repo-wide and the identical workflow having fired correctly for PR #21 the same day — this stalled the session; a second empty retrigger commit (`8203a70d7`) eventually got the `Checks` workflow to run (run `31159097992`).

  That run **failed**, but not on anything this task touched. `checks.yml`'s `unit-test` and `emulator-test` jobs both declare `needs: static-analysis` (verified: `.github/workflows/checks.yml:58,109`). The `static-analysis` job's "Checkstyle, Lint, SpotBugs" step runs the exact repo-wide `./gradlew checkstyle lint` command that also fails locally (see Test commands run, above) — and CI's own annotations confirm the same root cause: 7 pre-existing SpotBugs findings, all in files this branch never touches:
  - `app/src/main/java/de/danoeh/antennapod/ui/screen/queue/QueueFragment.java` (×2)
  - `app/src/main/java/de/danoeh/antennapod/ui/screen/preferences/PreferenceActivity.java` (its `MessageEvent` Snackbar handler — not the capture host, which is a *new*, separate file)
  - `app/src/main/java/de/danoeh/antennapod/ui/screen/onlinefeedview/OnlineFeedViewActivity.java`
  - `app/src/main/java/de/danoeh/antennapod/ui/screen/feed/FeedInfoFragment.java`
  - `app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java` (×2)

  Independently confirmed via `git diff --name-only f5d4c5551..HEAD` (this task's full diff): none of these five files appear. This is pre-existing `develop` baseline debt, structurally unrelated to this PR, and fixing it is out of scope per AGENTS.md ("never fix any warnings outside the code you wrote"). Because of the `needs:` chain, `unit-test` (the job that would actually run `SyncSettingsScreenshotCaptureTest` on `ubuntu-latest`) and `emulator-test` both show "skipped" — the specific signal AC13 exists to obtain (does the screenshot test's environment-sensitive assertions pass on Linux CI) is **structurally unreachable through this workflow right now**, through no fault of this implementation. `PR Conventions` (the one workflow unaffected by this gate) ran and passed.

  **Disclosed per AC13's own rule rather than worked around:** the pre-decided response table in D3 covers a red *unit-test* job (dimension mismatch, colour shortfall, near-1 count); it does not cover the unit-test job never running at all because an unrelated upstream gate failed. `@Ignore`-ing, CI-excluding, or otherwise touching `checks.yml` to route around this gate is exactly the kind of workaround D3 rules out, and none was taken. **Best available signal in place of the literal AC13 outcome: local Robolectric verification** (above — both flavours green, 4 variants green, distinct-colour count and dimensions recorded) plus confirmation that the identical `checkstyle lint` command fails identically and for the identical unrelated reason both locally and in CI, which at least rules out an environment-specific difference in the one command CI and this session both ran. **This does not amount to the CI confirmation AC13 asks for and is not represented as such.** Resolving it (fixing the pre-existing SpotBugs findings, or restructuring `checks.yml`'s `needs:` graph) is out of this task's File Scope and is a José decision — either accept local-only verification for this PR with the caveat above, or authorize a separate, explicitly-scoped follow-up to clear the pre-existing static-analysis debt so future PRs aren't gated by it.

### Deviations from plan

1. **D2's required one-line code comments on the two paired test methods (AC2/AC3 non-substitutability) were not added.** AGENTS.md states "Do not add any comments to the code you write," and this exact portfolio's Milestone 15 already went through two code-review rounds for violating that rule. Rather than re-trigger the same finding, the AC2/AC3 pairing is documented here (Characterization test results, above) and is fully stated in the Plan's own D2/AC2/AC3 text, which a reviewer reads alongside the diff. The assertions themselves (the actual behavior D2 requires) are unaffected — only the inline explanatory comment is omitted. Flagging for planner/reviewer awareness since this is a real, if narrow, conflict between the Plan's explicit instruction and a hard repo convention; the repo convention was treated as controlling.
2. **Two empty diagnostic commits** (`c17903102`, `8203a70d7`) were pushed while investigating why GitHub Actions wasn't triggering a run on PR #22 (see AC13 above). Neither changes any file. Left in the branch history rather than rewritten, since force-pushing a shared/pushed branch to rewrite history wasn't requested and isn't necessary for any acceptance criterion (all file-scope checks operate on tree content, not commit count).
3. **AC13 could not be satisfied in its literal form** — see the AC13 entry above. This is the one open item blocking a clean sign-off and needs a José decision (accept local-only verification for this PR, or authorize a separate follow-up to clear the pre-existing `:app`/`:net:download:service` SpotBugs debt and the `:app-wearos` compile error so `checks.yml`'s `needs: static-analysis` gate stops blocking unrelated PRs).

No other deviations. Every other Step, File Scope boundary, and Acceptance Criterion was implemented and verified exactly as the twice-red-teamed Plan specified.
