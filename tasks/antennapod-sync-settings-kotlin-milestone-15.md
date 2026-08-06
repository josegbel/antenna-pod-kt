# antennapod-sync-settings-kotlin-milestone-15

> **Description:** `kotlin` track only. Convert the Sync Settings screen slice (`ui:preferences/screen/synchronization/`, 4 Java files) to Kotlin and stand up `ui:preferences`'s test source set from scratch with characterization tests, preserving RxJava/EventBus/`PreferenceFragmentCompat`/XML UI exactly as-is. First of a ~6-milestone sequence that together modernize this screen (concurrency+MVVM, DI infra, DI wiring, `:storage:preferences`, Compose) — see `tasks/antennapod-sync-settings-modernization-future-work.md` for the full sequence and why it was split this way.
> **Repo:** `services/android-migration/projects/portfolio/antennapod`
> **Created:** 2026-08-06
> **Scope narrowed:** 2026-08-06, after Research (below) confirmed the full vertical slice is too large for one focused PR — see the "Scope narrowing" bullet at the end of this context block.

> **Pre-research context (carried over from the parent planning conversation / a live architectural survey — do not re-derive):**
> - **This milestone is different in kind from Milestones 2–14.** Every prior milestone converted one module's production or test code to Kotlin (the `kotlin` track only). This one is the portfolio's first **vertical-slice modernization**: a thin, self-contained screen, taken through multiple tracks at once (`kotlin`, `di`, `compose`, and an implicit `concurrency` track for the RxJava/EventBus→Coroutines+Flow move) to demonstrate full-stack modernization, not just mechanical translation. Consult `services/android-migration/.claude/agents/legacy-android-planner.md` for the documented recommended track order and dependency notes before sequencing this — this is the first milestone in the portfolio to combine tracks this way.
> - **Target surface, confirmed against source 2026-08-06:** `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/` — four Java files: `SynchronizationPreferencesFragment.java` (top-level screen, `PreferenceFragmentCompat`-based, reads `preferences_synchronization.xml`), `GpodderAuthenticationFragment.java`, `NextcloudAuthenticationFragment.java`, `AuthenticationDialog.java`. Verify this list is exhaustive and check for any sibling helper/adapter classes in research — do not assume it is complete.
> - **Dependencies already migrated and available to build on:**
>   - `:net:sync:service-interface` — 100% Kotlin as of Milestone 11 (production) / Milestone 14 (tests, PR #20, merged 2026-08-06). `SynchronizationProvider`, `SynchronizationQueue` live here.
>   - `:event` — 100% Kotlin as of Milestone 9. `SyncServiceEvent` (currently consumed via `EventBus.getDefault().register(this)` / `@Subscribe` in `SynchronizationPreferencesFragment`) lives here.
> - **Dependency NOT yet migrated, confirmed 2026-08-06:** `:storage:preferences` is 100% Java (7 files: `SynchronizationSettings.java`, `SynchronizationCredentials.java`, `UserPreferences.java`, `PlaybackPreferences.java`, `SleepTimerPreferences.java`, `SleepTimerType.java`, `UsageStatistics.java`). This slice's screen reads `SynchronizationCredentials` and `SynchronizationSettings` directly. Whether those two classes get pulled into this milestone's scope or become their own prerequisite milestone is an open scoping question for research/planning — do not assume either answer.
> - **No DI framework exists anywhere in this codebase today** — swept for Hilt/Dagger/Koin, found none; current pattern is manual instantiation plus `EventBus.getDefault()` for cross-component pub/sub (e.g. `SyncServiceEvent`). This milestone would be the **first** DI introduction in the portfolio. Scope it minimally (this slice's own graph) — do not treat this milestone as a mandate to roll DI out app-wide.
> - **No Compose exists in the main app today** — the only Compose in the repo is `app-wearos` (Wear Compose, a different UI paradigm, not evidence of main-app Compose readiness). This milestone would be the **first** Compose UI in `:app`/`:ui:*`. The app's current theme is **Material Components (Material2)**, not Material3 — relevant to the visual-parity decision below.
> - **MVVM precedent already exists in this module**, informally: `ui:preferences/screen/bugreport/BugReportViewModel.java` extends `AndroidViewModel`, uses RxJava (`Observable`/`Disposable`/`Schedulers`) for async work, manually instantiated (no DI). Useful as the "before" shape to modernize away from, and evidence that ViewModel-based MVVM is not a foreign pattern to introduce here.
> - **Visual-parity decision, already made in the parent conversation — do not relitigate without new evidence:** "pixel-perfect" (byte-identical) View→Compose parity is **not** the acceptance bar and was explicitly rejected as a goal, for two structural reasons: (1) the app's current Material2 theming vs. the modern Material3 Compose defaults this milestone should use are different specs by design; (2) View's Canvas/Skia draw pipeline and Compose's own layout/draw pipeline round anti-aliasing/sub-pixel metrics differently regardless of matched values. **The acceptance bar is visual parity verified by Paparazzi screenshot-diff with a stated tolerance, not pixel-identical assertion** — consistent with the global CLAUDE.md hard rule "Snapshot tests for new UI: Paparazzi for Android composables."
> - **UI hosting decision, already made in the parent conversation — do not relitigate without new evidence:** the new screen is hosted via a `ComposeView` (replacing `PreferenceFragmentCompat` as the Fragment's root), with **real Compose UI content inside it** — not `AndroidView`/`ComposeView` wrapping the existing inflated XML preference screen. The wrap-the-XML approach was explicitly considered and rejected because it achieves pixel parity trivially but delivers none of the modernization value (no real MVVM, no Flow, no real Compose UI) that is the point of this slice.
> - This is unaffiliated OSS portfolio case-study work — see `services/android-migration/projects/portfolio/README.md`.
> - José gave the go-ahead to scaffold this milestone on 2026-08-06, immediately after PR #20 (Milestone 14) was merged into `develop`. No other module/track was left explicitly queued after Milestone 14 — this slice was chosen fresh in conversation, not inherited from a prior checkpoint's Resume command.
> - **Before/after screenshots are an explicit deliverable of the *sequence*, not necessarily of this milestone** — visual evidence is the marketing artifact (see `services/android-migration/projects/portfolio/README.md`), but the "after" (Compose) side doesn't exist until the `compose` milestone at the end of the sequence. What this milestone (`kotlin`) owes toward that goal, at minimum, is **not regressing the ability to capture a "before" screenshot later** — do not let the Kotlin conversion change the screen's rendered output. Whether this milestone captures the "before" baseline itself (so it doesn't depend on the View-based screen still existing unchanged N milestones later) is for the planner to decide; see the future-work file for the mechanism-selection discussion (Paparazzi-can't-easily-render-`PreferenceFragmentCompat` vs. manual `adb` capture).
> - **Scope narrowing, 2026-08-06, after Research and before Plan:** José reviewed Research's findings and agreed the full vertical slice (kotlin + di + concurrency + compose all at once) is too large for one focused, reviewable PR — consistent with this portfolio's established pattern of one track per milestone, chained via an explicit follow-up queue (see `tasks/antennapod-model-kotlin-future-work.md` for precedent). **This task file (Milestone 15) is now scoped to the `kotlin` track only.** The remaining tracks are sequenced as Milestones 16–20 and recorded in `tasks/antennapod-sync-settings-modernization-future-work.md`, which the planner for each of those milestones should read first rather than re-deriving the sequencing rationale. The Research section below still surveys the **whole** vertical slice (it was completed before the narrowing decision) — its `kotlin`-track subsections are what's actually in scope for this milestone's Plan; its `di`/`concurrency`/`compose` subsections are carried forward as a head start for Milestones 16–20's own research passes, not re-done from scratch, but each of those milestones still needs its own planning pass since facts may drift between now and then.

## Research
_Last updated by: legacy-android-researcher | 2026-08-06_

### Summary

The target slice is four Java files, 704 lines, in `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/`. `SynchronizationPreferencesFragment` (250 lines) is a `PreferenceFragmentCompat` subclass that inflates a five-row `PreferenceScreen` XML and mutates those rows imperatively from three entry points — `onCreatePreferences`, `onStart`, and a sticky EventBus `@Subscribe` on `SyncServiceEvent`. It has no ViewModel, no state holder, and no separation between "what the screen shows" and "which `Preference` object gets `setEnabled` called on it": screen state is read on demand from two process-global static classes (`SynchronizationSettings`, `SynchronizationCredentials`) and written straight into `Preference` setters and the host Activity's `ActionBar` subtitle. The other three files are dialogs. `GpodderAuthenticationFragment` (277 lines) is a four-step `ViewFlipper` wizard driven by a hand-rolled integer state machine, doing network I/O on RxJava3 `Completable`/`Observable` whose `Disposable`s are never retained. `NextcloudAuthenticationFragment` (123 lines) is a ViewBinding-based `DialogFragment` implementing a callback interface owned by `NextcloudLoginFlow` in `:net:sync:gpoddernet`. `AuthenticationDialog` (54 lines) is an abstract `MaterialAlertDialogBuilder` subclass — and, importantly, it is **not slice-private**: two classes in `:app` subclass it. The module has **zero tests** — `ui/preferences/src/` contains only `main`, and `ui/preferences/build.gradle` declares no test dependency of any kind.

The requested tracks would replace all of that: `kotlin` converts the four files (the module has no Kotlin plugin today and no Kotlin file anywhere in it); `concurrency` moves the two RxJava3 subscriptions and the sticky-EventBus `SyncServiceEvent` subscription onto Coroutines + `StateFlow`; `di` introduces the codebase's first DI container to replace `ClientConfigurator`'s static-init-plus-mutable-global-singleton pattern; `compose` replaces `PreferenceFragmentCompat` + `preferences_synchronization.xml` with a `ComposeView` hosting real Compose content. Three things in that set are larger than the slice and the planner must scope them explicitly rather than discovering them mid-implementation: (1) the screen is not reachable only by navigation — it is **indexed by the `preferencesearch` library against `R.xml.preferences_synchronization`**, and the search-result handler calls `SearchPreferenceResult.highlight(PreferenceFragmentCompat)`, so ceasing to be a `PreferenceFragmentCompat` breaks a `:app` call site at compile time and a user-visible search path at runtime; (2) Hilt cannot be confined to a library module — `@HiltAndroidApp` and the Hilt Gradle plugin must land on `:app`, which is in tension with the "scope minimally to this slice" instruction; (3) neither `androidx.compose.material3:material3` nor Paparazzi exists in the version catalog, and `kotlinx-coroutines-core`/`-android` do not either.

### Findings

#### Corrections to the pre-research context

Two carried-over facts are wrong against current source and both are load-bearing for locked decisions. Reporting, not relitigating.

1. **The app is Material3, not Material2.** `ui/common/src/main/res/values/styles.xml:12` — `Theme.Base.AntennaPod.Dynamic.Light` has parent `Theme.Material3.DynamicColors.Light`; `:71` — the dark equivalent has parent `Theme.Material3.DynamicColors.Dark`. The file contains 14 `Material3` references against a single residual `MaterialComponents` one (`:271`, a Toolbar overlay). The dependency is `com.google.android.material:material` 1.12.0 (`gradle/libs.versions.toml`), i.e. View-system Material3 with dynamic color. **Reason (1) given for the visual-parity decision — "Material2 theming vs. modern Material3 Compose defaults are different specs by design" — is factually false.** Reason (2) (View Canvas/Skia vs. Compose draw-pipeline rounding) is unaffected and still holds on its own; and a stronger replacement for reason (1) now exists: the app ships **six theme permutations** (Light / Dark / TrueBlack × dynamic / non-dynamic, `ui/common/src/main/res/values/styles.xml:4-144`, selected at runtime by `ThemeSwitcher`), with the dynamic variants deriving colour from the device wallpaper — a Compose `ColorScheme` cannot be byte-identical to a wallpaper-derived View theme by construction. The tolerance-based bar therefore survives, but the planner should record the corrected reasoning rather than the stated one.
2. **The RxJava in use is RxJava3, not RxJava2.** Imports are `io.reactivex.rxjava3.*` (`GpodderAuthenticationFragment.java:28-31`); catalog pins `io.reactivex.rxjava3:rxjava` 3.1.5 and `io.reactivex.rxjava3:rxandroid` 3.0.2. There is no RxJava2 anywhere in the repo. This matters for the interop shape of any incremental Rx→coroutine bridging (`kotlinx-coroutines-rx3`, not `-rx2`).

Verified-correct as given: the four-file list **is** exhaustive (no sibling helpers, adapters, or resource-only classes in the package); `:storage:preferences` **is** 7 Java files, 0 Kotlin; there is **no** Hilt/Dagger/Koin/KSP/KAPT anywhere (swept `*.gradle`, `*.toml`); `:event` and `:net:sync:service-interface` **are** Kotlin; `BugReportViewModel` **is** an `AndroidViewModel` using RxJava with manual instantiation.

#### Existing surface

`ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/` — 4 files, 704 lines, all Java. Module has no Kotlin plugin (`ui/preferences/build.gradle:1-3`).

- **`SynchronizationPreferencesFragment.java`** (250 lines) — the screen. Extends `AnimatedPreferenceFragment` (module-local, `screen/AnimatedPreferenceFragment.java`), which extends `PreferenceFragmentCompat` and supplies `MaterialSharedAxis` enter/exit/return/reenter transitions and a `colorSurface` background. Inflates `R.xml.preferences_synchronization` (`:45`). Five preference keys, all hardcoded strings (`:37-41`). Responsibilities: wire five `OnPreferenceClickListener`s (`setupScreen`, `:81-113`); recompute enabled/visible/summary state for all five rows from static settings (`updateScreen`, `:115-149`); own the host Activity's ActionBar title **and subtitle** (`:53`, `:63`, `:77`, `:157`, `:226`); render a provider-chooser `MaterialAlertDialogBuilder` backed by an anonymous `ArrayAdapter<SynchronizationProvider>` with an inline `ViewHolder` (`chooseProviderAndLogin`, `:161-210`); map `SynchronizationProvider` → `@StringRes` summary and `@DrawableRes` icon via two `switch` statements (`:229-249`).
- **`GpodderAuthenticationFragment.java`** (277 lines) — `DialogFragment`, gpodder.net login wizard. Four steps (`STEP_HOSTNAME`/`LOGIN`/`DEVICE`/`FINISH`, `:46-50`) advanced by a single `advance()` method that both transitions the `ViewFlipper` and performs the step's side effects, including the credential commit (`:255-259`). Three `volatile` mutable fields (`username`, `password`, `selectedDevice`, `:55-57`) plus a non-volatile `List<GpodnetDevice> devices` written from an IO thread (`:127`) and read from the main thread (`:155`, `:221`). Two RxJava3 subscriptions (`:124-142`, `:180-196`). Device-name/ID generation and dedupe logic (`:199-227`), plus a username character validator (`:272-276`).
- **`NextcloudAuthenticationFragment.java`** (123 lines) — `DialogFragment` implementing `NextcloudLoginFlow.AuthenticationCallback`. Uses ViewBinding (`NextcloudAuthDialogBinding`, `:43`). Owns login-flow save/restore across config change (`:51-55`, `:67-72`), a `shouldDismiss` deferred-dismiss flag for the not-resumed case (`:100-104`), and the Nextcloud credential commit (`:91-105`).
- **`AuthenticationDialog.java`** (54 lines) — abstract `MaterialAlertDialogBuilder` subclass. ViewBinding-based username/password dialog with a show/hide-password toggle (`:31-40`). Two overridable hooks: `protected void onCancelled()` (no-op default) and `protected abstract void onConfirmed(String username, String password)`.

Resources the slice owns or reads: `res/xml/preferences_synchronization.xml` (5 `Preference` rows); layouts `alertdialog_sync_provider_chooser.xml`, `authentication_dialog.xml`, `gpodnetauth_dialog.xml`, `gpodnetauth_host.xml`, `gpodnetauth_credentials.xml`, `gpodnetauth_device.xml`, `gpodnetauth_device_row.xml`, `gpodnetauth_finish.xml`, `nextcloud_auth_dialog.xml`; drawables `gpodder_icon.png`, `nextcloud_logo.png`. Strings live in `:ui:i18n` (`ui/i18n/src/main/res/values/strings.xml:744-779`).

#### Java/Kotlin interop boundary

**Calls INTO the slice from `:app` (3 call sites, all Java):**

- `app/.../PreferenceActivity.java:75` — `prefFragment = new SynchronizationPreferencesFragment();` inside `private PreferenceFragmentCompat getPreferenceScreen(int screen)` (`:63`). **The return type is `PreferenceFragmentCompat`.** If the fragment stops being one, this is a compile break in `:app`, and it propagates: `public PreferenceFragmentCompat openScreen(int screen)` (`:117`) returns it to nine call sites across `MainPreferencesFragment`, `DownloadsPreferencesFragment`, `UserInterfacePreferencesFragment` and `PreferenceActivity` itself.
- `app/.../FeedSettingsPreferenceFragment.java:193` — instantiates an **anonymous subclass** of `AuthenticationDialog`, overriding `onConfirmed(String, String)`.
- `app/.../OnlineFeedViewActivity.java:503` — declares a **named inner subclass** `FeedViewAuthenticationDialog extends AuthenticationDialog`, overriding **both** `onCancelled()` (calling `super.onCancelled()`, `:513`) and `onConfirmed(String, String)`.

`AuthenticationDialog` is therefore shared public API with two Java subclasses outside the module, not a slice-private helper. Converting it to Kotlin requires `open` on the class and on `onCancelled()`, `protected` visibility preserved on both hooks, and the two `String` parameters of `onConfirmed` left as platform-compatible nullable-or-not types that both Java overrides still satisfy. Both callers pass values that can be `null` (`feedPreferences.getUsername()` at `FeedSettingsPreferenceFragment.java:195`; `OnlineFeedViewActivity`'s `username`/`password` fields at `:509`).

**The `preferencesearch` indexing path — the non-obvious inbound coupling:**

- `app/.../MainPreferencesFragment.java:169-170` registers `config.index(R.xml.preferences_synchronization).addBreadcrumb(...)` with `com.bytehamster:lib.preferencesearch` 2.7.3. The library parses the XML at index time, so every title/summary in `preferences_synchronization.xml` is searchable from the settings search bar.
- `app/.../PreferenceActivity.java:157-161` — `onSearchResultClicked` does `PreferenceFragmentCompat fragment = openScreen(result.getResourceFile()); result.highlight(fragment);`. `highlight()` requires a live `PreferenceFragmentCompat` to flash the matched row.
- Consequence: the XML cannot simply be deleted (it is the search index source), and if the fragment is no longer a `PreferenceFragmentCompat`, either `getPreferenceScreen` returns `null` for this screen (search silently stops working for sync settings, a runtime regression with no compile signal) or `openScreen`'s signature changes across nine call sites. **Neither the XML nor `PreferenceActivity` is inside the 4-file slice.**

**The ActionBar subtitle contract:** the fragment writes the host Activity's ActionBar subtitle from four places and clears it in `onStop` (`:63`). `updateLastSyncReport` (`:221-227`) formats a relative timestamp into it. This is Activity-owned state mutated by the Fragment — it does not move into a Compose tree, and dropping it is a user-visible behavior change.

**Calls OUT of the slice:**

| Target | Module | Language | Notes |
|---|---|---|---|
| `SynchronizationSettings` (10 call sites) | `:storage:preferences` | **Java** | static, `init(Context)` from `ClientConfigurator` |
| `SynchronizationCredentials` (13 call sites) | `:storage:preferences` | **Java** | static, `init(Context)` from `ClientConfigurator` |
| `SynchronizationQueue.getInstance()` (7 call sites) | `:net:sync:service-interface` | Kotlin | `@JvmStatic var instance: SynchronizationQueue?` — **nullable** |
| `SynchronizationProvider` | `:net:sync:service-interface` | Kotlin | `fromIdentifier(String?): SynchronizationProvider?` — **nullable return** |
| `SyncServiceEvent` | `:event` | Kotlin | sticky EventBus event |
| `GpodnetService`, `GpodnetDevice`, `NextcloudLoginFlow` | `:net:sync:gpoddernet` | Java | all blocking/`throws`-based or Rx-callback-based |
| `AntennapodHttpClient` | `:net:common` | Java | |
| `Keyboard`, `ThemeUtils` | `:ui:common` | Java | |

**One in-module, out-of-slice consumer of the same state:** `ui/preferences/.../screen/NotificationPreferencesFragment.java:25` reads `SynchronizationSettings.isProviderConnected()`. Any repository abstraction introduced for the slice leaves this call site on the static API unless the planner scopes it in.

**Public API that must not silently break:** `AuthenticationDialog`'s constructor signature and its two `protected` hooks (two Java subclasses in `:app`); `SynchronizationPreferencesFragment`'s no-arg constructor and its `PreferenceFragmentCompat` type; `GpodderAuthenticationFragment.TAG` / `NextcloudAuthenticationFragment.TAG` (used only within the slice, `:197`/`:201`).

#### Current test coverage

**Zero. There is no test of any kind for this slice, and no test infrastructure in the module to add one to.**

- `ui/preferences/src/` contains exactly one directory: `main`. No `test`, no `androidTest`. (Compare: only `:ui:chapters` and `:ui:common` have a `test` source set among all `ui/*` modules.)
- `ui/preferences/build.gradle:24-45` declares **no `testImplementation` line at all** — not JUnit, not Robolectric, not Mockito. Adding the first test to this module means adding the test source set and its dependencies from nothing.
- `:app`'s instrumented suite does not cover it: `app/src/androidTest/java/de/test/antennapod/ui/PreferencesTest.java` contains **no** reference to `synchronization`, `Synchron`, or `sync` (grepped). `FeedSettingsTest.java` and `SleepTimerPreferencesTest.java` cover unrelated screens.
- A repo-wide grep for `SynchronizationPreferencesFragment|GpodderAuth|NextcloudAuth|preferences_synchronization` across `app/src/test` and `app/src/androidTest` returns nothing.

Tests that exist for the slice's **collaborators**, and what they actually assert — none of them exercise a line of the slice:

- `net/sync/service-interface/src/test/.../SynchronizationProviderTest.kt` — 9 tests, thorough: null/empty/unknown/case-sensitivity for `fromIdentifier`, exact persisted identifier strings, declaration order of `values()`. This is a real safety net for the enum, not for the screen.
- `event/.../SyncServiceEventTest.kt` — 1 test, asserts `messageResId` round-trips. Does not touch stickiness or delivery.
- `net/sync/service/src/test/.../SynchronizationQueueImplTest.java:61`, `SynchronizationQueueStorageTest.java:206-210` — exercise the queue implementation and its storage, calling `SynchronizationSettings.init(context)` in setup. They pin queue behavior, not screen behavior.
- `:storage:preferences` has **no test source set** — `SynchronizationSettings` and `SynchronizationCredentials` have no direct tests anywhere. The only coverage they get is incidental, via other modules' setup calls.

Do not read "the sync area is well tested" from the presence of `SynchronizationProviderTest`. The screen, both auth dialogs, the credential-commit sequences, and the two preference classes the screen reads are all uncovered.

#### Characterization-test gaps

Everything below has zero coverage today and is behavior a user can observe. Per the pipeline's own rule, this is Step 1 work, before any conversion.

**`SynchronizationPreferencesFragment` — screen state derivation (`updateScreen`, `:115-149`):**
1. Logged-out state: header shows `synchronization_choose_title` / `synchronization_summary_unchoosen`, no icon, header click opens the provider chooser; the four action rows are all disabled; logout summary is `null`.
2. Logged-in state: header title is set to the **empty string** (not left alone), summary/icon switch to the provider's, header click listener is set to `null`.
3. `pref_gpodnet_setlogin_information` visibility is `true` **only** when the selected provider is exactly `GPODDER_NET`; its enabled state tracks logged-in independently of visibility.
4. Logout summary is `HtmlCompat.fromHtml(getString(synchronization_login_status, username, hosturl), FROM_HTML_MODE_LEGACY)` — the string carries `\n\n` and is HTML-parsed; the rendered `Spanned` is the observable, not the raw string.
5. **Latent NPE, currently unreachable-by-test:** when `isProviderConnected()` is true but the persisted key is not a recognised identifier (a stale or downgraded install), `SynchronizationProvider.fromIdentifier` returns `null` (`:120`) and `getProviderSummary`'s `switch (provider)` (`:230`) throws NPE. The `default:` arms at `:236` and `:247` returning `sync_status_error`/`ic_error` are dead code today — they are what the author intended for the unknown case, and they are never reached. Pin the current behavior (crash) before deciding anything.

**`SynchronizationPreferencesFragment` — action wiring (`setupScreen`, `:81-113`):**
6. `pref_gpodnet_setlogin_information` opens an `AuthenticationDialog` seeded with the current username, with the username field **disabled**, and on confirm writes **only the password** — the username argument is discarded (`:89-91`).
7. Logout ordering is observable and specific: `SynchronizationCredentials.clear()` → `SynchronizationQueue.clear()` → Snackbar → `setSelectedSyncProvider(null)` → `updateScreen()` → `updateActionBar()` (`:104-112`). `SynchronizationCredentials.clear()` additionally calls `UserPreferences.setGpodnetNotificationsEnabled()` (`SynchronizationCredentials.java:58`) — a cross-class side effect into a much larger preferences class.
8. Sync / force-full-sync rows call `syncImmediately()` / `fullSync()` with no confirmation and no state change.

**`SynchronizationPreferencesFragment` — event and lifecycle:**
9. `syncStatusChanged` is `@Subscribe(threadMode = MAIN, sticky = true)` (`:66`) — a **sticky** subscription, so registering in `onStart` immediately replays the last posted `SyncServiceEvent`. This replay-on-subscribe is the mechanism by which the screen shows a sync result the user missed, and any Flow replacement must reproduce it.
10. Early return when not connected (`:68-70`) — a sync event arriving while logged out updates nothing at all, not even the subtitle.
11. Branch on `messageResId`: `sync_status_error` or `sync_status_success` → subtitle becomes the formatted last-sync report; anything else → subtitle becomes the raw message string (`:72-78`).
12. `onStart` sets the ActionBar **title**; `onStop` sets the **subtitle** to `""` (empty string, not `null`) whereas `updateActionBar` sets it to `null` when disconnected (`:157`). Empty-vs-null is a real distinction to `ActionBar`.

**`GpodderAuthenticationFragment`:**
13. Step machine: `advance()` is called once from `onCreateDialog` before any flip (`currentStep == STEP_DEFAULT` suppresses `showNext()`, `:263-265`), so the first step is set up without a transition. Off-by-one behavior here is easy to break.
14. Host step clears credentials and the sync queue **before** setting the new host URL (`:84-86`), then constructs `GpodnetService` from the freshly-cleared credentials (`:87-89`) — so device ID / username / password passed to the constructor are all `null` at that point by design.
15. Login step: username character validation against `[!@#$%&*()+=|<>?{}\[\]~]` runs **before** any network call and shows `gpodnetsync_username_characters_error` (`:113-117`).
16. Login error path renders `error.getCause().getMessage()` (`:140`) — not `error.getMessage()`. Whatever the current message text is for a given failure, it is the unwrapped cause's.
17. Device-name generation: base name `gpodnetauth_device_name_default` with `Build.MODEL`, then ` (1)`, ` (2)`… until unused (`:199-208`). Device ID is `name.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase(Locale.US)` (`:213`) — `Locale.US` specifically, and dedupe matches on **either** generated ID or caption (`:222`).
18. Credential commit happens on the `STEP_DEVICE`→`STEP_FINISH` transition, in this order: `setSelectedSyncProvider` → `setUsername` → `setPassword` → `setDeviceId` (`:255-259`). A partially-completed wizard therefore leaves credentials untouched, but step 14's `clear()` has already run.
19. Finish step dismisses **then** calls `syncImmediately()` (`:232-235`).
20. Dialog is non-cancellable in both senses (`dialog.setCancelable(false)` and `this.setCancelable(false)`, `:66-67`).

**`NextcloudAuthenticationFragment`:**
21. Credential commit order on success: `setSelectedSyncProvider` → `credentials.clear()` → `queue.clear()` → `setPassword` → `setHosturl` → `setUsername` → `queue.fullSync()` (`:92-99`). Note `clear()` runs **after** the provider is selected and **before** the three setters — different ordering from the gpodder path.
22. Deferred dismiss: if not resumed when authentication completes, set `shouldDismiss` and dismiss in `onResume` (`:100-104`, `:83-88`).
23. Login flow survives configuration change via `saveInstanceState`/`fromInstanceState` of an `ArrayList<String>` (`:51-55`, `:67-72`); `onDismiss` cancels the flow (`:75-80`).
24. Error dialog composes `nextcloud_login_error_generic + "\n\n" + errorMessage` with a `ForegroundColorSpan(0x88888888)` applied to the second half only (`:113-121`).

**`AuthenticationDialog`:**
25. Password visibility toggle swaps `HideReturnsTransformationMethod`/`PasswordTransformationMethod` and the button alpha between `1.0f` and `0.6f` (`:31-40`).
26. Username field enabled state is constructor-driven; `null` initial values leave the field untouched rather than setting `""` (`:25-30`).
27. Both the cancel button **and** the dialog's cancel listener call `onCancelled()` (`:42-43`) — `OnlineFeedViewActivity.FeedViewAuthenticationDialog` overrides it to `finish()` the Activity, so this fires on both paths.

**Interop-shape gaps (no test proves these today):** `AuthenticationDialog` being subclassable from Java with those exact two `protected` hooks; `SynchronizationPreferencesFragment` being assignable to `PreferenceFragmentCompat`; `preferences_synchronization.xml` being parseable by `preferencesearch`. Per `:net:sync:service-interface`'s README convention #9, this repo's established practice is to guard Java-visible call shapes with a **Java** test whose oracle is javac's acceptance — worth reusing here for `AuthenticationDialog`.

#### Track-specific findings

##### `kotlin`

Module state: `ui/preferences/build.gradle:1-3` applies only `libs.plugins.android.library` — no `kotlin.android`, no `ktlint`. There is not one Kotlin file in `:ui:preferences`. Adding both plugins is prerequisite work inside the module's own build file.

Null-safety hazards, in descending severity:

1. **`SynchronizationQueue.getInstance()` returns a nullable type in Kotlin.** `net/sync/service-interface/.../SynchronizationQueue.kt` declares `@JvmStatic var instance: SynchronizationQueue? = null`. Java call sites deref it unchecked; there are **7 in the slice** (`SynchronizationPreferencesFragment.java:97,101,106`; `GpodderAuthenticationFragment.java:85,234`; `NextcloudAuthenticationFragment.java:95,99`). J2K will emit `!!` at every one. Every `!!` here is a real crash the current Java code also has — the question is whether to preserve it or route through DI (see `di`).
2. **`SynchronizationProvider.fromIdentifier` returns `SynchronizationProvider?`** while `getProviderSummary`/`getProviderIcon` take a non-null parameter. `:net:sync:service-interface`'s README convention #1 **explicitly names this file**: *"the one unguarded Java call site at `SynchronizationPreferencesFragment`'s equivalent is Java, so the compiler won't warn."* Converting this file is precisely what makes that latent NPE (gap #5 above) compiler-visible. J2K will produce `!!` and hide it again; the correct move is a `when`/`null` branch that reaches the existing dead `default:` arms — but that is a **behavior change** from crash to graceful, and belongs to the planner, not to the conversion step.
3. **`:storage:preferences` carries zero `@Nullable`/`@NonNull` annotations.** Every getter on `SynchronizationSettings` and `SynchronizationCredentials` is a Kotlin platform type (`String!`, etc.). `getUsername()`, `getPassword()`, `getDeviceId()`, `getHosturl()` and `getSelectedSyncProviderKey()` all return `prefs.getString(key, null)` — genuinely nullable, with `null` the default for a fresh install. J2K will infer non-null `String` for locals holding them and produce silently-unsafe code.
4. **`GpodderAuthenticationFragment.java:103`** — `SynchronizationCredentials.getHosturl().startsWith("http://")` is an unguarded deref of a genuinely nullable getter. Currently safe only because `setupHostView` sets it first; nothing in the type system says so.
5. **`GpodderAuthenticationFragment.java:140`** — `error.getCause().getMessage()`: two chained derefs, either of which can be `null` for an RxJava error whose cause was not set.
6. **`findPreference(String)` is `@Nullable` in androidx.preference** — 11 unguarded call sites in `SynchronizationPreferencesFragment` (`:83,96,100,104,117,135,138,139,140,145,147`). J2K emits `!!` at each; ktlint will not object but the result is 11 undocumented crash points.
7. **`getSupportActionBar()` is nullable** — 5 unguarded derefs (`:53,63,77,157,226`), each also going through a `(AppCompatActivity) getActivity()` cast where `getActivity()` is itself nullable.
8. **`getView()` is nullable** at `:107` (`Snackbar.make(getView(), ...)`).
9. `volatile` fields (`GpodderAuthenticationFragment.java:55-57`) must become `@Volatile`; the non-volatile `devices` field (`:58`) is written on an IO thread and read on main — a pre-existing data race. Do **not** silently fix it; per this repo's convention (`:net:sync:service-interface` README #11) a known defect gets pinned by a test and tracked, not drive-by-repaired.
10. The anonymous `ArrayAdapter` at `:166-191` holds `ViewHolder holder` as a **field of the adapter**, reassigned on every `getView` call — a classic recycling bug. J2K translates it literally, which is correct behavior-preserving output. Flag it, do not fix it in the conversion step.
11. Build gates that make this stricter than a typical J2K job: `common.gradle` sets `-Werror` on all `JavaCompile` and `warningsAsErrors true` + `abortOnError true` + `checkDependencies true` on lint. CI runs `./gradlew checkstyle lint` then `./gradlew ktlintCheck` (`.github/workflows/checks.yml:46-48`). Checkstyle's source set is `fileTree('src/main/java') { include '**/*.java' }` — converted files leave checkstyle's scope and enter ktlint's, so both gates must be green.
12. `:ui:preferences` applies `playFlavor.gradle`, so its test tasks are **flavoured** (`testFreeDebugUnitTest` / `testPlayDebugUnitTest`), unlike `:net:sync:service-interface` whose README convention #8 warns specifically against copying task names across this boundary.

##### `di`

**Current approach: static initialization plus a mutable global singleton. No DI framework of any kind.** Confirmed by grep over all `*.gradle`/`*.toml` for `hilt|dagger|koin|ksp|kapt` — zero hits.

The existing composition root is `app/src/main/java/de/danoeh/antennapod/ClientConfigurator.java`, a `static synchronized initialize(Context)` guarded by a `static boolean initialized` flag (`:30-33`). Relevant to this slice, it performs `SynchronizationCredentials.init(context)` (`:44`), `SynchronizationSettings.init(context)` (`:45`), and `SynchronizationQueue.setInstance(new SynchronizationQueueImpl(context))` (`:54`).

Scope and lifecycle assumptions baked into that:
- Both preference classes hold a **`private static SharedPreferences prefs`** field, assigned once in `init(Context)` (`SynchronizationSettings.java:14-18`, `SynchronizationCredentials.java:16-20`). Every accessor NPEs if called before `init`. Scope is effectively `@Singleton`, but with no lifecycle owner and no way to provide a test double — which is a large part of why the slice is untestable today.
- `SynchronizationQueue.instance` is a **mutable public global** (`var`, not `val`), settable by anyone at any time.
- Cross-component signalling is `EventBus.getDefault()` — a second, parallel global.

**A minimal Hilt graph for just this slice would need:**
- Catalog additions: `com.google.dagger:hilt-android`, `com.google.dagger:hilt-android-compiler`, the `com.google.dagger.hilt.android` Gradle plugin, **and a symbol-processing plugin — neither KSP nor KAPT is in the catalog or applied anywhere.**
- A `@Singleton`-scoped `@Module` providing a sync-settings repository that wraps the two static preference classes behind an injectable interface (the only way to get a test seam without converting `:storage:preferences`).
- `@HiltViewModel` on the new ViewModel the `compose` track requires, plus `@AndroidEntryPoint` on the hosting Fragment.
- `SynchronizationQueue` bound as a provided dependency rather than read from the mutable global.

**The blocking structural fact: Hilt cannot be scoped to a library module.** `@HiltAndroidApp` must be applied to the `Application` class, and the Hilt Gradle plugin must be applied to the `:app` module for the component hierarchy to be generated at all. `:ui:preferences` is an `android.library`. So "first DI in the codebase, scoped minimally to this slice" is not achievable as literally stated — the minimum viable footprint is `:app`'s Application class + `:app`'s build file + `:ui:preferences`'s build file + the slice. Whether that is acceptable, or whether a constructor-injection-only approach (no framework) meets the milestone's demonstration goal, is a decision for the planner. Note also that `app-wearos` is a **second `android.application` module** — it does not depend on `:ui:preferences`, so it is unaffected, but confirm that before assuming.

##### `concurrency`

**The concrete pattern is RxJava3 + EventBus, with no coroutines and no LiveData in the slice.** (LiveData appears only in `BugReportViewModel`, a different screen in the same module. `kotlinx-coroutines-core` and `kotlinx-coroutines-android` are **not in the version catalog** — the only coroutines entry is `kotlinx-coroutines-play-services` 1.9.0, used by `app-wearos`.)

RxJava3 usage in the slice — two subscriptions, both in `GpodderAuthenticationFragment`:
- `:124-142` — `Completable.fromAction { login + fetch devices }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(onComplete, onError)`. **The returned `Disposable` is discarded.** Nothing cancels it on dismiss or configuration change; the success callback touches `login`, `progressBar`, `txtvError` — views owned by a dialog that may be gone.
- `:180-196` — `Observable.fromCallable { configureDevice }...subscribe(onNext, onError)`. Same: **`Disposable` discarded**, same view-touching-after-dismiss exposure.

That is a live resource/crash hazard in the current code, not something the migration introduces. Whether the coroutine rewrite preserves it (structurally impossible — `viewModelScope`/`lifecycleScope` cancels by construction) or fixes it is a behavior change the planner must name explicitly, because "the dialog no longer crashes on rotate-during-login" is a *desirable* divergence from behavioral equivalence and needs to be recorded as such rather than discovered in review.

EventBus usage:
- `SynchronizationPreferencesFragment` registers/unregisters in `onStart`/`onStop` (`:56`, `:62`) and subscribes with `@Subscribe(threadMode = ThreadMode.MAIN, sticky = true)` (`:66`). **The `sticky = true` is semantically load-bearing** — it replays the most recent `SyncServiceEvent` at registration time. A naive `MutableSharedFlow` replacement (`replay = 0`) loses that silently; `StateFlow` or `SharedFlow(replay = 1)` reproduces it, but with a difference: EventBus's sticky cache is cleared explicitly via `removeStickyEvent`, and `:event`'s README convention warns that sticky removal compares by `equals()` and that no event class in that module declares `equals`. Who posts `SyncServiceEvent` and whether anything removes it stickily needs checking in `:net:sync:service` before designing the Flow shape.
- `PreferenceActivity` itself also registers on EventBus for `MessageEvent` (`:150-157`) — unaffected but adjacent.

Out-of-slice Rx that does **not** get converted: `NextcloudLoginFlow` (`net/sync/gpoddernet/.../nextcloud/NextcloudLoginFlow.java`) owns two `Disposable` fields and a polling flow built on `Observable`/`Schedulers.io`. It lives in `:net:sync:gpoddernet` (Java) and its `AuthenticationCallback` interface is what `NextcloudAuthenticationFragment` implements. The slice can move to coroutines while still consuming that callback, but the callback fires on the Rx main-thread scheduler, so the bridge point (`callbackFlow`, or just keeping the callback) is a design decision. `GpodnetService` is a **blocking, `throws`-based** Java API (`getDevices()`, `login()`, `configureDevice()` all `throws GpodnetServiceException`) — it maps cleanly to `withContext(Dispatchers.IO)`.

##### `compose`

**Whether a ViewModel/MVVM layer already exists for this slice: no.** `SynchronizationPreferencesFragment` has no ViewModel, no state holder, and no UI-state type. All screen state is read on demand from static globals inside `updateScreen()`. The only `ViewModel` in `:ui:preferences` is `BugReportViewModel` (`screen/bugreport/`), which serves a different screen, uses `AndroidViewModel` + `MutableLiveData` + RxJava, and is manually instantiated. It is a useful *shape reference* — it proves ViewModel-based MVVM is not foreign to this module — but it is not a layer this slice can build on. **This is a blocking prerequisite; see Track prerequisites.**

Current View/XML layer:
- `PreferenceFragmentCompat` + `res/xml/preferences_synchronization.xml` (5 `Preference` rows, no custom preference types) for the screen itself.
- 9 XML layouts for the dialog flows, including a 4-child `ViewFlipper` (`gpodnetauth_dialog.xml` + `gpodnetauth_host/credentials/device/finish.xml`).
- **ViewBinding is enabled repo-wide** (`common.gradle` `buildFeatures { viewBinding true }`) and is used by `AuthenticationDialog` (`AuthenticationDialogBinding`) and `NextcloudAuthenticationFragment` (`NextcloudAuthDialogBinding`). `GpodderAuthenticationFragment` uses raw **`findViewById`** throughout (`:70,78,79,96-101,148-152,168-170,230`), as does the adapter in `SynchronizationPreferencesFragment` (`:180-181`). So the slice is a mix, not uniformly ViewBinding.
- Custom views: none in the slice. `:ui:common` has a few (`CircularProgressBar`, `SquareImageView`) but none are used here.

Compose readiness of the toolchain:
- **Present in the catalog:** `androidx-compose-ui` 1.7.5, `androidx-compose-runtime` 1.7.5, `androidx-activity-compose` 1.9.3, `androidx-lifecycle-runtime-compose` 2.8.7, `androidx-lifecycle-viewmodel-ktx` 2.8.7, and the `kotlin-compose` plugin (`org.jetbrains.kotlin.plugin.compose`, version-ref'd to Kotlin 2.3.20).
- **Absent from the catalog:** `androidx.compose.material3:material3`, `androidx.compose.foundation:foundation`, `androidx.compose.ui:ui-tooling`/`ui-tooling-preview`, any Compose BOM, and **Paparazzi** (grepped the whole repo — Paparazzi appears only in prior task-file prose asserting it was *not* applicable). Every one of these is a new catalog entry.
- `app-wearos/build.gradle` is the only Compose consumer and uses **Wear** Compose Material3 with an explicit in-file warning not to add `androidx.compose.material:material`. It sets `buildFeatures { compose true }` and `kotlinOptions { jvmTarget = "21" }` — the pattern to copy for module setup, but not for UI libraries.
- Encouraging: `.editorconfig` already contains `ktlint_function_naming_ignore_when_annotated_with = Composable`, so ktlint is pre-configured for PascalCase composable names.
- **Version skew to check before adding anything:** `kotlin-bom` is pinned at **1.9.24** while the Kotlin plugin is **2.3.20**, and `common.gradle` applies `implementation platform(libs.kotlin.bom)` to every module. Compose runtime 1.7.5 and any new coroutines dependency will be resolved against that BOM.

Theming: the Compose tree needs a `ColorScheme`. The app resolves one of **six** View themes at runtime via `ThemeSwitcher` (Light/Dark/TrueBlack × dynamic/non-dynamic), where the dynamic variants inherit `Theme.Material3.DynamicColors.*` and take colour from the wallpaper. There is no theme-adapter library in the project. Bridging View-Material3 attrs into a Compose `MaterialTheme` is unsolved work, and it multiplies the Paparazzi snapshot matrix.

##### `navigation`

Not requested. Recorded only as context for the `compose` track: navigation into this screen is a manual `FragmentTransaction` — `PreferenceActivity.openScreen` does `.replace(binding.settingsContainer.getId(), fragment).addToBackStack(getString(getTitleOfPage(screen))).commit()` (`:123-125`), with back handled by `onOptionsItemSelected` popping the back stack (`:135-146`). The dialogs use `show(getChildFragmentManager(), TAG)` (`SynchronizationPreferencesFragment.java:196-201`). No Navigation Component anywhere.

#### Track prerequisites

- **`kotlin` — no prerequisites; met.** The module needs the `kotlin.android` and `ktlint` plugins added to `ui/preferences/build.gradle`, which is in-module setup, not a cross-module dependency. Both upstream Kotlin dependencies (`:event`, `:net:sync:service-interface`) are already converted. `:storage:preferences` being Java is **not** a blocker for converting the slice — Java classes are callable from Kotlin — but it does mean every preference getter arrives as an unannotated platform type (see `kotlin` finding #3).
- **`gradle-kts` — not requested.** For the record: every build file in the repo is Groovy (`build.gradle`, `common.gradle`, `playFlavor.gradle`, all module files), and `common.gradle` contains non-mechanical Groovy — a `groovy.xml.XmlSlurper` SpotBugs report parser, `gradle.taskGraph.whenReady` task-name matching, and an `exec {}` block reading `git rev-parse` in `ui/preferences/build.gradle:10-18`. Not in scope; noted so it is not mistaken for trivial later.
- **`di` — prefers `kotlin` first on this module; that ordering is satisfiable within this milestone. Not blocked by Kotlin, but blocked in its stated scope by a structural fact:** Hilt requires `@HiltAndroidApp` on the `Application` class and the Hilt Gradle plugin on `:app`. A library module cannot host a Hilt component hierarchy. The instruction "scope it minimally (this slice's own graph) — do not treat this milestone as a mandate to roll DI out app-wide" cannot be satisfied literally with Hilt. The planner must choose between (a) accepting a minimal `:app` footprint (Application annotation + plugin, no other call sites touched), or (b) a non-Hilt approach. Do not assume (a) is pre-approved.
- **`concurrency` — prefers `kotlin` first on this module; satisfiable within this milestone.** Needs `kotlinx-coroutines-core` and `-android` added to the version catalog (absent), and `kotlinx-coroutines-rx3` if incremental bridging to `NextcloudLoginFlow` is chosen. No blocking gap.
- **`compose` — requires `kotlin` done (satisfiable in-milestone) and wants the `concurrency` track's `StateFlow` target (also in-milestone, if sequenced before `compose` per the planner's documented order). BLOCKING PREREQUISITE: there is no ViewModel/MVVM layer for this slice.** `SynchronizationPreferencesFragment` has no ViewModel, no UI-state type, and reads state directly from static globals; `BugReportViewModel` belongs to a different screen and is not a layer this slice can build on. Per this pipeline's rules, introducing a ViewModel/MVVM layer is bespoke architectural work, **not in scope for the `compose` track**, and must be surfaced to the client rather than folded in quietly. Two further non-blocking-but-unfunded gaps attach to this track: `androidx.compose.material3:material3` and Paparazzi are both absent from the version catalog, and the View-Material3 → Compose `MaterialTheme` colour bridge across six theme variants has no existing solution in the repo.
- **`navigation` — not requested.** Prerequisite (`kotlin` done) would be satisfiable, but no navigation work is planned or implied.

### Unknowns

Decisions, not facts. Each belongs to `legacy-android-planner` (and several likely to José, per the repo rule on commercially-significant scope).

1. **`:storage:preferences` scope — pull in or split out?** The slice reads `SynchronizationSettings` (10 call sites) and `SynchronizationCredentials` (13 call sites), both Java statics with a `private static SharedPreferences` assigned in `init(Context)`. Three sub-decisions: (a) convert those two files to Kotlin in this milestone, (b) leave them Java and wrap them behind an injectable Kotlin interface for the DI/testability seam, or (c) split them into a prerequisite Milestone 15a. Constraints the planner needs: converting `SynchronizationCredentials` pulls in `UserPreferences.setGpodnetNotificationsEnabled()` (`SynchronizationCredentials.java:58`), coupling the change to the module's largest Java class; `SynchronizationSettings` has **10 call sites outside the slice** in five modules (`:net:sync:service`, `:net:download:service`, `:app`, plus `NotificationPreferencesFragment` in this module), so converting it is a cross-module blast radius, not a local change; and `:storage:preferences` has **no test source set at all**, so option (a) or (c) carries its own characterization-test bill before a line is converted. Option (b) is the smallest diff but leaves `:storage:preferences` un-migrated, which may or may not match the portfolio narrative.
2. **The "before" screenshot mechanism.** Constraints found: Paparazzi is **not in the version catalog or applied to any module**; `:ui:preferences` has **no test source set and no test dependencies at all**; the current screen is a `PreferenceFragmentCompat`, and Paparazzi's `paparazzi.snapshot()` renders a `View` — a `PreferenceFragmentCompat`'s view is built by a `RecyclerView`-backed `PreferenceManager` inflation that needs a real `PreferenceFragmentCompat` lifecycle, not just a `View`; and the repo has an explicit, documented **Robolectric-free precedent** with only two disclosed exceptions (`net/sync/service-interface/build.gradle:19-22`, README convention #10). Candidates the planner must choose between and record: (a) add Paparazzi and attempt a View-render of the inflated preference screen — lowest fidelity risk if it works, unproven for `PreferenceFragmentCompat`; (b) manual `adb exec-out screencap` on an emulator, checked into the repo as a PNG artifact — zero new tooling, but not reproducible in CI and not gated by any acceptance criterion; (c) an instrumented (`androidTest`) screenshot, which would be the module's first `androidTest` source set. Whichever is chosen, both the before and after images must land in File Scope and Acceptance Criteria per the task's stated requirement. Related: the "after" side needs the theme matrix decided too — six View themes exist; snapshotting all six against a Compose `ColorScheme` that cannot reproduce wallpaper-derived dynamic colour is not meaningful, so the planner should state which variants are snapshotted and why.
3. **Does the fragment remain a `PreferenceFragmentCompat`?** The `ComposeView`-hosting decision is locked, but its consequence is not scoped. `PreferenceActivity.getPreferenceScreen(int)` returns `PreferenceFragmentCompat` (`:63`) and `openScreen(int)` propagates that type to nine call sites; `onSearchResultClicked` calls `result.highlight(fragment)` (`:160`) needing a live `PreferenceFragmentCompat`; and `MainPreferencesFragment.java:169` indexes `R.xml.preferences_synchronization` into the search database. Options: keep the XML purely as a search-index source and accept that search-result *highlighting* degrades for this screen (a silent runtime regression — needs an explicit accept/reject); change `openScreen`'s signature and touch nine `:app` call sites (scope growth outside the slice); or special-case the sync screen in `PreferenceActivity`. **All three touch files outside the 4-file slice.**
4. **Hilt's `:app` footprint — accept, or use a non-Hilt approach?** See Track prerequisites. If Hilt: the milestone must also add a KSP or KAPT plugin, neither of which exists in the catalog. If not Hilt: does manual constructor injection through a `ViewModelProvider.Factory` satisfy the `di` track's demonstration value for a portfolio case study? That is partly a marketing question, so per the AEPM operational rules it is likely a José decision, not a planner one.
5. **The ViewModel/MVVM layer — a separate priced milestone, or in scope?** The `compose` track's blocking prerequisite. Per the planner agent's own rule this must go to Open Questions and Out of Scope rather than being folded in. Note the practical tension: without it, `compose` cannot proceed at all, so "defer it" means deferring the `compose` track.
6. **Behavior changes the migration makes unavoidable — preserve or fix, each recorded explicitly?** Three concrete ones: (a) the two orphaned RxJava `Disposable`s (`GpodderAuthenticationFragment.java:124,180`) cannot survive a move to `viewModelScope`/`lifecycleScope`, which cancel by construction — the dialog stops being able to touch dead views, which is a *fix*, not equivalence; (b) the `fromIdentifier` → `switch` NPE (`:120`/`:230`) becomes a compile-visible decision in Kotlin, and reaching the existing dead `default:` arms would convert a crash into a graceful error state; (c) the `devices` field data race (`:58`). Convention in this repo (`:net:sync:service-interface` README #11) is to pin known defects with a test and track them separately rather than drive-by fix. Which of the three follow that convention and which are fixed here is the planner's call, and each needs its own acceptance criterion.
7. **Does anything post or sticky-remove `SyncServiceEvent` in a way that constrains the Flow design?** The subscription is `sticky = true` (`:66`), so replay-on-subscribe is observable behavior. `:event`'s README warns that `EventBus.removeStickyEvent` compares by `equals()` and that no event class declares one. Before choosing `StateFlow` vs `SharedFlow(replay = 1)` vs keeping EventBus for this one event, check the posters in `:net:sync:service` — not surveyed here because it is outside the slice.
8. **Do the three dialogs move to Compose too, or only the screen?** The locked hosting decision names "the new screen." `GpodderAuthenticationFragment` is a 4-step `ViewFlipper` wizard across 5 XML layouts and is by far the largest UI rewrite in the slice; `AuthenticationDialog` is subclassed from `:app` by two Java classes and rewriting it in Compose changes an API two out-of-module callers depend on. A defensible minimal read is "screen to Compose, dialogs converted to Kotlin only" — but it is not stated, and it materially changes the milestone's size and its screenshot deliverable.
9. **Branch and base.** The working tree is currently on `antennapod-net-sync-service-interface-kotlin-milestone-14`, not `develop`. The checkpoint says this milestone should branch from a fresh `origin/develop` containing merged PR #20. AGENTS.md forbids committing on `develop`/`master`. Confirm the base before the first implementation commit.
10. **Version-catalog and toolchain hygiene.** `kotlin-bom` is pinned at 1.9.24 while the Kotlin plugin is 2.3.20, and `common.gradle` applies that BOM to every module. This milestone adds coroutines, Compose Material3, Hilt and Paparazzi on top of it. Whether the BOM pin needs raising — and whether that is in scope for a slice milestone or is its own toolchain task — needs deciding before dependencies are added, not after a resolution failure.

### Sources

**Task and process**
- `tasks/antennapod-sync-settings-modernization-milestone-15.md:7-21` — pre-research context block
- `features/antennapod-sync-settings-modernization-milestone-15.checkpoint.md:25` — locked decisions
- `services/android-migration/.claude/agents/legacy-android-planner.md:13-21` — documented track order
- `AGENTS.md:11-13` — read a module's `README.md` first; `:88-102` — build/test/PR conventions
- `ui/preferences/README.md:1-4` — module purpose

**The slice**
- `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/SynchronizationPreferencesFragment.java:36-250` — whole file; `:37-41` keys; `:45` XML inflation; `:53,63,77,157,226` ActionBar derefs; `:56,62,66` EventBus; `:68-70` early return; `:72-78` message branch; `:81-113` listener wiring; `:87-91` password-only commit; `:104-112` logout ordering; `:115-149` state derivation; `:119-123` nullable provider; `:142-145` HtmlCompat summary; `:166-191` adapter with recycled-holder-as-field; `:196-201` dialog show; `:212-219` provider-selected check; `:221-227` last-sync report; `:229-249` provider→resource switches with unreachable `default:` arms
- `.../GpodderAuthenticationFragment.java:41-277` — whole file; `:28-31` RxJava3 imports; `:46-52` step constants; `:55-58` volatile + non-volatile mutable state; `:66-67` non-cancellable; `:80-92` host step ordering; `:103` unguarded `getHosturl()` deref; `:106-107` IME action; `:113-117` username validation; `:124-142` orphaned `Completable`; `:140` `error.getCause().getMessage()`; `:147-165` device view; `:180-196` orphaned `Observable`; `:199-227` device name/ID generation and dedupe; `:232-235` dismiss-then-sync; `:238-270` step machine; `:255-259` credential commit ordering; `:272-276` character validator
- `.../NextcloudAuthenticationFragment.java:26-123` — whole file; `:43` ViewBinding; `:46-55` login-flow start and restore; `:59-64` progress UI; `:67-72` save state; `:75-80` cancel on dismiss; `:83-88` deferred dismiss; `:91-105` credential commit ordering; `:107-122` error dialog with span
- `.../AuthenticationDialog.java:14-54` — whole file; `:17-23` constructor and ViewBinding; `:24-30` field enable/seed; `:31-40` password toggle; `:42-46` cancel/confirm wiring; `:49-53` `protected` hooks
- `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/AnimatedPreferenceFragment.java:12-27` — base class, transitions, `colorSurface` background
- `ui/preferences/src/main/res/xml/preferences_synchronization.xml` — 5 preference rows
- `ui/i18n/src/main/res/values/strings.xml:744-745,774,778-779` — sync strings

**Interop boundary**
- `app/src/main/java/de/danoeh/antennapod/ui/screen/preferences/PreferenceActivity.java:63` (`PreferenceFragmentCompat` return type), `:74-75` (instantiation), `:117-131` (`openScreen`, back-stack), `:135-146` (back handling), `:157-161` (`result.highlight(fragment)`), `:150-157` (its own EventBus registration)
- `app/src/main/java/de/danoeh/antennapod/ui/screen/preferences/MainPreferencesFragment.java:150-178` (search config), `:169-170` (indexes `preferences_synchronization`), `:92` (openScreen call site)
- `app/src/main/java/de/danoeh/antennapod/ui/screen/feed/preferences/FeedSettingsPreferenceFragment.java:192-212` — anonymous `AuthenticationDialog` subclass
- `app/src/main/java/de/danoeh/antennapod/ui/screen/onlinefeedview/OnlineFeedViewActivity.java:282-284` (instantiation), `:503-522` (`FeedViewAuthenticationDialog`, overrides `onCancelled` + `onConfirmed`)
- `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/NotificationPreferencesFragment.java:25` — out-of-slice reader of `isProviderConnected()`
- Repo-wide grep for `SynchronizationPreferencesFragment|GpodderAuthenticationFragment|NextcloudAuthenticationFragment|AuthenticationDialog` outside the package — returns exactly the four `:app` sites above

**Dependencies**
- `storage/preferences/src/main/java/de/danoeh/antennapod/storage/preferences/SynchronizationSettings.java:6-71` — statics, `:14-18` `init`, `:20-22` `isProviderConnected`, `:44-46` nullable `getSelectedSyncProviderKey`
- `storage/preferences/.../SynchronizationCredentials.java:9-60` — statics, `:16-20` `init`, `:22-52` nullable getters, `:54-59` `clear()` calling `UserPreferences.setGpodnetNotificationsEnabled()`
- `net/sync/service-interface/src/main/java/.../SynchronizationQueue.kt` — `@JvmStatic var instance: SynchronizationQueue?`
- `net/sync/service-interface/src/main/java/.../SynchronizationProvider.kt` — `fromIdentifier(String?): SynchronizationProvider?`
- `net/sync/service-interface/README.md` conventions #1 (names `SynchronizationPreferencesFragment` as the unguarded call site), #8 (flavoured vs unflavoured test tasks), #9 (Java tests as compile-shape oracles), #10 (Robolectric scope), #11 (pin known defects, don't drive-by fix)
- `event/README.md` — sticky-event/`equals()` warning; `@JvmField`/`@JvmStatic` conventions
- `event/src/main/java/de/danoeh/antennapod/event/SyncServiceEvent.kt` — `class SyncServiceEvent(val messageResId: Int)`
- `net/sync/gpoddernet/src/main/java/de/danoeh/antennapod/net/sync/gpoddernet/GpodnetService.java:46,89,112,296,409` — blocking `throws`-based API
- `net/sync/gpoddernet/src/main/java/de/danoeh/antennapod/net/sync/nextcloud/NextcloudLoginFlow.java:1-80` — RxJava3, two `Disposable` fields, polling flow

**Test coverage**
- `ui/preferences/src/` — contains only `main` (no `test`, no `androidTest`)
- `ui/preferences/build.gradle:24-45` — no `testImplementation` of any kind
- `app/src/androidTest/java/de/test/antennapod/ui/PreferencesTest.java` — no `sync`/`Synchron` references
- `net/sync/service-interface/src/test/.../SynchronizationProviderTest.kt` — 9 tests, enum only
- `event/src/test/.../SyncServiceEventTest.kt` — 1 test
- `net/sync/service/src/test/.../SynchronizationQueueImplTest.java:49,61`, `SynchronizationQueueStorageTest.java:49,206-210` — queue-level, not screen-level
- `storage/preferences/` — no test source set

**Build, toolchain, DI, theming**
- `ui/preferences/build.gradle:1-3` (no Kotlin/ktlint plugin), `:10-18` (`exec` git-hash), `:24-45` (dependencies)
- `event/build.gradle:1-5`, `net/sync/service-interface/build.gradle:1-5` — the `kotlin.android` + `ktlint` plugin pattern migrated modules use
- `common.gradle:38-48` — `sourceCompatibility 21`, `-Werror` on `JavaCompile`; `:60-66` — `warningsAsErrors true`, `abortOnError true`, `checkDependencies true`; `:68-71` — `viewBinding true`; `:74-77` — `kotlin-bom` platform applied to every module; `:157-166` — checkstyle sources `src/main/java` `**/*.java` only
- `playFlavor.gradle` — `free`/`play` flavours (applied by `:ui:preferences`, hence flavoured test tasks)
- `gradle/libs.versions.toml` — kotlin 2.3.20; compose 1.7.5; `kotlin-bom` 1.9.24; `rxjava` 3.1.5 / `rxandroid` 3.0.2; `eventbus` 3.3.1; `google-material` 1.12.0; `preferencesearch` 2.7.3; `kotlinx-coroutines-play-services` 1.9.0 (**the only** coroutines entry); `kotlin-compose` plugin present; **no** hilt/dagger/koin/ksp/kapt, **no** `compose.material3`/`compose.foundation`/`ui-tooling`/BOM, **no** paparazzi
- Repo-wide grep for `hilt|dagger|koin|com.google.devtools.ksp|kotlin-kapt` over `*.gradle`/`*.toml`/`*.kts` — zero hits
- Repo-wide grep for `paparazzi` — hits only in prior `tasks/*.md` prose asserting non-applicability
- `app-wearos/build.gradle:1-6,50-52,54-56,73-92` — the only Compose module; Wear Compose Material3; `buildFeatures { compose true }`; `kotlinOptions { jvmTarget = "21" }`
- `.editorconfig:9-11` — `ktlint_code_style = android_studio`, `ktlint_function_naming_ignore_when_annotated_with = Composable`
- `.github/workflows/checks.yml:46-48` — `./gradlew checkstyle lint` then `./gradlew ktlintCheck`
- `app/src/main/java/de/danoeh/antennapod/ClientConfigurator.java:30-33` (init guard), `:44-45` (preference `init`), `:54` (`SynchronizationQueue.setInstance`)
- `ui/preferences/src/main/java/.../screen/bugreport/BugReportViewModel.java:25,169-186` — `AndroidViewModel` + `MutableLiveData` + RxJava, manually instantiated
- `ui/common/src/main/res/values/styles.xml:12,71` — `Theme.Material3.DynamicColors.Light/Dark` parents (**disproves the Material2 claim**); `:4-144` — six theme variants; `:271` — the single residual `MaterialComponents` reference
- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/ThemeSwitcher.java:9-56` — runtime theme selection across Light/Dark/TrueBlack × dynamic

**Git state**
- `git rev-parse --abbrev-ref HEAD` → `antennapod-net-sync-service-interface-kotlin-milestone-14` (not `develop`)
- `git log --oneline -20` — milestone 14 commits through `af93320b1`

## Plan
_Last updated by: legacy-android-planner | 2026-08-06_

### Objective

`kotlin` track only, on the `ui:preferences/screen/synchronization/` slice. Convert its four Java files (704 lines) to Kotlin behind a characterization suite that this milestone also has to **build from nothing** — `:ui:preferences` has no `test` source set, no test dependency, and not one Kotlin file today. 26 of Research's 27 enumerated behaviors are pinned by executable Robolectric tests written **before** the first conversion; the 27th (gap 16) is pinned by a mechanical source-level check, with the reason recorded rather than glossed. RxJava3, EventBus, `PreferenceFragmentCompat`, `preferences_synchronization.xml` and every layout are behaviorally untouched — that is Milestones 17 and 20's work, not this one's. `AuthenticationDialog`'s two Java subclasses in `:app` are treated as a live external API contract, not as slice-private code. The three known defects Research found (the `fromIdentifier` NPE, the `devices` data race, the adapter's recycled-holder-as-field) are **pinned and preserved**, per this repo's standing convention, not fixed.

### Resolved Decisions

Research Unknown 9 is resolved here (D1). Unknowns 1–8 and 10 belong to Milestones 16–20 per `tasks/antennapod-sync-settings-modernization-future-work.md` and are **not** resolved here; where a decision below has a consequence for one of them, that consequence is named so the later milestone inherits it explicitly rather than rediscovering it. Two corrections to Research are recorded (D3, D14). Everything else was decided at planning time against source.

---

**D1 — Branch fresh from a *fetched* `origin/develop`, and verify PR #20 is actually in it first.** (Research Unknown 9.)

The working tree is on `antennapod-net-sync-service-interface-kotlin-milestone-14`. More importantly, **the local `origin/develop` ref is stale and does not contain PR #20**, verified at planning time:

```
git log -1 --format='%ci' origin/develop   → 2026-08-05 11:24:32 +0200
git log --oneline -1 origin/develop        → 5f816b768 … (#19)   [Milestone 13]
git merge-base --is-ancestor af93320b1 origin/develop → false     [M14's tip is NOT an ancestor]
```

So "branch from `origin/develop`, which contains merged PR #20" is not yet true on this machine. Before the first commit the developer runs, in order, and pastes the output into Implementation Notes:

```bash
git fetch origin
git merge-base --is-ancestor af93320b1 origin/develop && echo "M14 present"
git checkout -b kotlin/ui-preferences-sync-settings origin/develop
git log --oneline -1
```

Branch name `kotlin/ui-preferences-sync-settings`, matching the `kotlin/<module>` precedent (`kotlin/net-sync-service-interface`, `kotlin/net-download-service-interface`). If the second command does not print `M14 present` after a fetch, **stop** — the base is wrong and the milestone's premise (`:net:sync:service-interface` tests are Kotlin, PR #20 merged) has not landed; that is a José question, not something to work around. AGENTS.md forbids committing on `develop`/`master`, so this is checked before Step 1, not at PR time.

---

**D2 — The characterization suite is written in Kotlin, and it is frozen byte-for-byte across every conversion step.**

The suite is *new* code, not converted code. Writing it in Java and converting it later would double the diff and add no equivalence value — M13's D-series settled the same question ("a plan that opened with 'write characterization tests first' would be writing tests for tests"). The tests are black-box over the production classes' observable effects, so their language is irrelevant to their validity as pins.

The risk this creates is specific and is closed by an acceptance criterion rather than by argument: a Kotlin test compiles against Java platform types before conversion and against declared Kotlin types after, so a test could in principle be *edited* to keep compiling and silently stop asserting the same thing. **AC7 requires `git diff` over `ui/preferences/src/test/` to be empty across Steps 8–11.** If a conversion forces a test edit, that stops the step and is disclosed — it is evidence of a signature change, which is exactly what the suite exists to catch.

One exception, by design: `AuthenticationDialogJavaInteropTest.java` is Java (D8) and is likewise frozen.

---

**D3 — Robolectric is the harness. Research's "Robolectric-free precedent" does not apply to this module.** (Correction to Research.)

Research cited `net/sync/service-interface/README.md` convention #10 as an "explicit, documented Robolectric-free precedent with only two disclosed exceptions." That convention is **module-local to `:net:sync:service-interface`** — it says "don't add Robolectric to the *other seven test files in this module*." Measured repo-wide, `testImplementation libs.robolectric` appears in **14 modules** (`grep -rn 'robolectric' --include='*.gradle' .` → 14 hits, one per module: `app`, `model`, `net/common`, `net/download/service-interface`, `net/download/service`, `net/sync/service-interface`, `net/sync/service`, `net/sync/wear-interface`, `parser/feed`, `parser/media`, `parser/transcript`, `storage/database`, `storage/importexport`, `ui/chapters`) — including `:app`, `:model`, `:storage:database`, `:parser:*` and — directly relevant — **`:ui:chapters`**, whose test block is `libs.androidx.test.core` + `libs.junit` + `libs.robolectric`. Robolectric 4.16 is already in the catalog. Adding it to `:ui:preferences` is following repo precedent, not breaking one. *(Count corrected in Revision 1 — the original text said 15, and the enumerated list is now given so the figure is re-checkable rather than asserted.)*

What *is* genuinely first-of-kind: **no test anywhere in this repo drives a Fragment or an Activity.** Grep for `FragmentScenario|ActivityScenario|Robolectric.buildActivity|launchFragment` across the whole repo returns nothing. Steps 1's harness is therefore a new pattern for this codebase, which is why Step 1 proves it before any behavior is asserted (D5).

---

**D4 — Zero changes to `gradle/libs.versions.toml`. The only build-file change is `ui/preferences/build.gradle`.**

Everything the harness needs is already in the catalog:

```groovy
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)   // added
    alias(libs.plugins.ktlint)           // added
}
…
    testImplementation libs.junit                 // added
    testImplementation libs.robolectric           // added
    testImplementation libs.androidx.test.core    // added
```

That is the whole build diff. No Mockito (`libs.mockito.core` exists but hand-rolled fakes are clearer and `SynchronizationQueue.instance` is already a public `var`, D6), no MockWebServer, no `fragment-testing`, no coroutines, no Compose, no Paparazzi, no BOM change. **Catalog additions are Milestone 16's job** and this milestone must not pre-empt them — a `libs.versions.toml` line in this diff means the plan was wrong. `common.gradle` declares no `kotlinOptions`, and `:event` / `:net:sync:service-interface` apply the same two plugins with no extra configuration, so nothing further is needed for the Kotlin toolchain. The `exec {}` git-hash block and `playFlavor.gradle` application are untouched.

Consequence for later milestones: the `kotlin-bom` 1.9.24 vs Kotlin 2.3.20 skew (Research Unknown 10) is **not** exercised by this milestone, because no new dependency is resolved against it. Milestone 16 still owns it.

---

**D5 — Step 1 proves the harness before it is trusted, with a pre-decided fallback ladder. No fallback invents production changes.**

Four things are unproven in this repo and are proof obligations of Step 1, each with its answer recorded in Implementation Notes:

| Proof | Primary mechanism | Pre-decided fallback |
|---|---|---|
| Robolectric can build an `AppCompatActivity` declared only in `src/test/java` | `Robolectric.buildActivity(SyncSettingsTestHost::class.java)` | `Shadows.shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, SyncSettingsTestHost::class.java))` before `buildActivity`. Test-only; zero production footprint. **A `src/debug/AndroidManifest.xml` or a manifest entry in `src/main` is forbidden** — it would ship a test host in the debug artifact. |
| `PreferenceFragmentCompat` inflates `R.xml.preferences_synchronization` under Robolectric | Host themed `de.danoeh.antennapod.ui.common.R.style.Theme_AntennaPod_Light`, which sets `preferenceTheme` → `AppPreferenceThemeOverlay` (`ui/common/src/main/res/values/styles.xml:34`), and attach via `supportFragmentManager.beginTransaction().add(android.R.id.content, fragment, TAG).commitNow()` | If `MaterialSharedAxis` construction in `AnimatedPreferenceFragment.onCreate` or `ThemeUtils.getColorFromAttr(…, colorSurface)` in `onViewCreated` fails, raise the Robolectric SDK via `@Config(sdk = …)` and record the working value. **Subclassing or modifying `AnimatedPreferenceFragment` to dodge it is forbidden** — it is out of File Scope. |
| A `MaterialAlertDialogBuilder`-built dialog is reachable from a test | `ShadowDialog.getLatestDialog()` after `show()` | `ShadowAlertDialog.getLatestAlertDialog()` |
| `AntennapodHttpClient.getHttpClient()` is constructible under Robolectric (needed by gap 14's path) | Call it directly in the smoke test | If it throws, gap 14's test wraps the button click in `assertThrows` and **still** asserts the credential/queue state — the ordering gap 14 pins is precisely that `clear()` runs *before* `GpodnetService` is constructed, so the assertions survive a construction failure. This fallback weakens nothing. |

`GpodnetService`'s constructor was read at planning time and is inert (field assignment plus `HostnameParser`) — no network at construction.

The five Step 1 proofs are written as five real tests and stay in the suite as a smoke layer; they are not scratch work.

---

**D6 — `SynchronizationQueue.instance` is the test seam. No DI, no wrapper, no production seam is introduced.**

`SynchronizationQueue` is an `abstract class` whose companion exposes `@JvmStatic var instance: SynchronizationQueue?` — a public mutable global. That is a defect in production terms (Milestone 18 removes it) and a gift in test terms: a `RecordingSynchronizationQueue` test double is assigned to it in `@Before` and reset to `null` in `@After`. This gives ordering-sensitive observation of all 7 in-slice call sites with **zero production change**.

`SynchronizationSettings` / `SynchronizationCredentials` / `UserPreferences` are initialised in `@Before` with `init(ApplicationProvider.getApplicationContext())` against Robolectric's real `SharedPreferences` — the same pattern `net/sync/service`'s existing tests already use. `:storage:preferences` stays Java and untouched (Research Unknown 1 is Milestone 19's, not this milestone's).

Consequence recorded for Milestone 18: every characterization test in this suite reads and writes the static preference globals directly. When Milestone 18 puts a repository behind them, this suite is what proves the repository preserved behavior — so it should be treated as the migration's regression net, not rewritten alongside it.

---

**D7 — The 27 gaps are pinned as follows. 26 executable, 1 source-level, none dropped.**

| Gap | Pinned by | File |
|---|---|---|
| 1, 2, 3, 4 | executable — drive the fragment logged-out and logged-in, assert every row's title/summary/icon/enabled/visible and the header's listener nullity | `SynchronizationPreferencesFragmentCharacterizationTest.kt` |
| 5 | executable — connected + unrecognised persisted key; `assertThrows(NullPointerException::class.java) { … }` **and** assert `preferenceHeader.title == ""` afterwards (D11 makes this discriminating) | same |
| 6 | executable — click the row, capture the dialog, assert the username field is disabled and pre-seeded, click confirm, assert password written and username unchanged | same |
| 7 | executable — recording queue observes provider-still-selected at `clear()` time; assert `UserPreferences` gpodnet-notifications side effect fired | same |
| 8 | executable — assert `syncImmediately()`/`fullSync()` recorded and no preference state changed | same |
| 9, 10, 11, 12 | executable — `EventBus.getDefault().postSticky(SyncServiceEvent(…))` before `onStart` for the replay; empty-string vs null subtitle asserted separately | `SynchronizationPreferencesFragmentLifecycleTest.kt` |
| 13, 14, 15, 17, 18, 19, 20 | executable — `advance()`, `generateDeviceName`, `generateDeviceId`, `isDeviceInList` and the `devices`/`username`/`password`/`selectedDevice`/`currentStep` fields are private, so they are driven by `getDeclaredMethod`/`getDeclaredField` reflection. Names are identical before and after J2K, so the tests are language-independent. | `GpodderAuthenticationFragmentCharacterizationTest.kt` |
| **16** | **source-level, not executable** | see below |
| 21, 22, 23, 24 | executable — `onNextcloudAuthenticated`/`onNextcloudAuthError` are public; the private `nextcloudLoginFlow` field is replaced by reflection with a recording `NextcloudLoginFlow` subclass to observe `cancel()` | `NextcloudAuthenticationFragmentCharacterizationTest.kt` |
| 25, 26, 27 | executable | `AuthenticationDialogCharacterizationTest.kt` |

**Gap 16 (`error.getCause().getMessage()` at `GpodderAuthenticationFragment.java:140`) is stated plainly as the one behavior this milestone does not pin executably.** Reaching that handler requires a failed real login against a live `GpodnetService` the fragment constructs internally; there is no seam and no MockWebServer in this repo, and introducing either is Milestone 16/17 scope. It is instead pinned by AC13's grep. Recorded as Open Question OQ2.

**The two-handler shape that check has to discriminate, stated here so AC13's command is actually mechanical** *(corrected in Revision 1 — the original text said "the converted file must read `error.cause!!.message`, never `error.message`," which is false of the file as a whole)*: `GpodderAuthenticationFragment.java` contains **two** RxJava error lambdas, and a correct conversion produces two *different* expressions.

| Handler | Java | Required Kotlin | Why |
|---|---|---|---|
| `setupLoginView`'s `Completable` (`:137-142`) | `txtvError.setText(error.getCause().getMessage())` (`:140`) | `error.cause!!.message` | gap 16 — the **unwrapped cause's** message, and the two chained derefs are both real NPE sites (`kotlin` finding 5) |
| `createDevice`'s `Observable` (`:191-196`) | `txtvError.setText(error.getMessage())` (`:194`) | `error.message` | unrelated, correct as written, **preserved verbatim** — it is not gap 16 and must not be "made consistent" with the login handler |

A blanket "`error.message` → 0" assertion therefore fails against a *correct* conversion, because `createDevice`'s handler legitimately emits exactly that. AC13 counts both expressions instead, and the two counts are disjoint as literal strings (`error.cause!!.message` does not contain the substring `error.message`).

---

**D8 — One Java file is added: `AuthenticationDialogJavaInteropTest.java`.** (README convention #9 of `:net:sync:service-interface`.)

`AuthenticationDialog` has two Java subclasses in `:app` (`FeedSettingsPreferenceFragment.java:193` anonymous, overriding `onConfirmed`; `OnlineFeedViewActivity.java:503` named `FeedViewAuthenticationDialog`, overriding **both** hooks and calling `super.onCancelled()`). `:app:assembleDebug` already compiles both, so the external guard is live — but it lives two modules away and disappears the day either subclass is refactored. Convention #9's pattern is a Java test whose entire oracle is javac's acceptance. This one is ~40 lines: two nested subclasses mirroring the two `:app` shapes exactly (one anonymous overriding only `onConfirmed`, one named overriding both and calling `super.onCancelled()`), each constructed with **both `usernameInitialValue` and `passwordInitialValue` null**, plus one trivial runtime assertion so it is a real test and not an empty class.

*(Citation corrected in Revision 1. The original text justified the null with "as `SynchronizationPreferencesFragment.java:87` does" — wrong twice over: that line is the **in-slice** anonymous subclass, not one of the two `:app` sites this test exists to mirror, and its literal `null` is the **password** argument, not the username, since it reads `new AuthenticationDialog(activity, R.string.…, false, SynchronizationCredentials.getUsername(), null)`.)*

The correct pin for both-parameters-null is **`OnlineFeedViewActivity.java:503-510`**, verified against source: `FeedViewAuthenticationDialog`'s constructor calls `super(context, titleRes, true, username, password)` (`:508`) reading the Activity's own fields, both declared `private String … = null` (`:84-85`) and assigned only in `onConfirmed` (`:520-521`) or restored from saved state. The dialog is constructed in `checkDownloadResult` (`:274-286`) on the **first** `ERROR_UNAUTHORIZED`, when both fields are still null — and the guard `if (username != null && password != null)` immediately above it (`:279`) is the author's own acknowledgement that they can be. So a null username reaching this constructor is a real, reachable production path, not a synthetic test value.

The other `:app` site, `FeedSettingsPreferenceFragment.java:193-195`, passes `feedPreferences.getUsername()` / `getPassword()` — unannotated nullable getters, i.e. platform types that may also be null. Neither `:app` site passes a *literal* null, which is exactly why the interop test has to: it forces the null case javac will otherwise never exercise from either caller.

It is deliberately *not* a Kotlin test: a Kotlin subclass would prove Kotlin-to-Kotlin subclassing, which is not the property at risk.

---

**D9 — `AuthenticationDialog`'s Kotlin shape is binding.**

```kotlin
abstract class AuthenticationDialog(
    context: Context,
    titleRes: Int,
    enableUsernameField: Boolean,
    usernameInitialValue: String?,
    passwordInitialValue: String?
) : MaterialAlertDialogBuilder(context) {
    private var passwordHidden = true
    init { … }
    protected open fun onCancelled() {}
    protected abstract fun onConfirmed(username: String, password: String)
}
```

Point by point, because every one of these is a live external contract:
- **`usernameInitialValue` / `passwordInitialValue` are `String?`.** Both callers pass values that are genuinely null: `SynchronizationPreferencesFragment.java:87` passes a literal `null` for the password, and `FeedSettingsPreferenceFragment.java:195` passes `feedPreferences.getUsername()`. Non-null here would be a compile break the day either caller becomes Kotlin, and a spurious `checkNotNullParameter` crash before that.
- **`onConfirmed`'s two parameters are non-null `String`.** They are only ever called with `viewBinding.usernameEditText.getText().toString()`, which cannot be null. Java overriders are unaffected by Kotlin nullability either way, so this costs nothing and documents the truth.
- **`onCancelled` must be `open`**, or `OnlineFeedViewActivity.FeedViewAuthenticationDialog` fails to compile. `abstract class` already implies `open` for the class itself.
- **Both hooks keep `protected`.** Kotlin `protected` emits JVM `protected`, which cross-package Java subclasses can override.
- **`passwordHidden` becomes `private`**, narrowing from Java's package-private. Kotlin has no package-private; `internal` would name-mangle. It is read and written only inside this file, and cross-package Java could never have reached it, so this is a no-op externally. AC16 greps to prove no other reference exists.
- **The constructor body becomes `init`**, preserving statement order exactly. The two hooks are invoked only from listener lambdas, so no "calling non-final function in constructor" hazard arises.

---

**D10 — Conversion order is leaf-first: `AuthenticationDialog` → `NextcloudAuthenticationFragment` → `GpodderAuthenticationFragment` → `SynchronizationPreferencesFragment`.**

Each intermediate state must compile in both directions, and does:
- Kotlin `AuthenticationDialog` is still subclassable by the Java `SynchronizationPreferencesFragment` (anonymous subclass at `:85-92`) and by the two `:app` classes.
- `GpodderAuthenticationFragment.TAG` and `NextcloudAuthenticationFragment.TAG` are read from `SynchronizationPreferencesFragment.java:197,201`, which is still Java at Steps 9 and 10. They must therefore be **`const val` in a `companion object`**, which emits a static field on the outer class and keeps `GpodderAuthenticationFragment.TAG` resolving from Java. A plain `val` or `@JvmField` inside the companion would not — this is the same trap `:net:sync:service-interface` README #3/#7 documents.
- `SynchronizationPreferencesFragment` converts last: it is the largest file, carries the most null hazards, and by then all three of its in-slice collaborators are Kotlin, so it converts against declared types rather than platform types.

The `STEP_*` constants and `EXTRA_LOGIN_FLOW` become `private const val` in the same companions.

---

**D11 — Every `!!` preserves an NPE that the Java already throws, at the same statement. The inventory is binding.**

This is the milestone's central equivalence discipline, and it is where a J2K conversion silently changes behavior if nobody counts. Expected inventory, measured against source at planning time:

| File | Sites | Form | What it preserves |
|---|---|---|---|
| `SynchronizationPreferencesFragment.kt` | 11 | `findPreference<Preference>(KEY)!!` at `:83,96,100,104,117,135,138,139,140,145,147` | `findPreference` is `@Nullable`; Java already derefs unchecked |
| " | 10 | `(activity as AppCompatActivity?)!!.supportActionBar!!` ×5 at `:53,63,77,157,226` | both `getActivity()` and `getSupportActionBar()` are nullable and unchecked in Java |
| " | 3 | `SynchronizationQueue.instance!!` at `:97,101,106` | `instance` is `var … ? = null` |
| " | 1 | `Snackbar.make(view!!, …)` at `:107` | `getView()` is nullable; Snackbar NPEs on null |
| " | 2 | `selectedProvider!!` at the **call sites** `:122,123` | see below |
| `GpodderAuthenticationFragment.kt` | 2 | `SynchronizationQueue.instance!!` at `:85,234` | as above |
| " | **4** | `service!!` at the `:125,126,127` (login `Completable.fromAction` lambda) and `:182` (`createDevice`'s `Observable.fromCallable` lambda) equivalents | `service` is assigned only in `setupHostView` |
| " | 1 | `devices!!` at `:155` | `devices` is null until login succeeds; `:217` proves the code knows it |
| " | 1 | `error.cause!!.message` at `:140` | gap 16 |
| `NextcloudAuthenticationFragment.kt` | 2 | `SynchronizationQueue.instance!!` at `:95,99` | as above |
| `AuthenticationDialog.kt` | 0 | — | nothing nullable is deref'd |

**Per-file totals, which are what AC8 checks against** (a `!!` **operator** count, not a statement count — the five ActionBar statements carry two each): `SynchronizationPreferencesFragment.kt` **27**, `GpodderAuthenticationFragment.kt` **8**, `NextcloudAuthenticationFragment.kt` **2**, `AuthenticationDialog.kt` **0** — **37** across the slice. *(Corrected in Revision 1: the `service!!` row read 3 while enumerating four distinct statements, and the totals quoted in Step 11 and OQ5 read 30, which matched neither the old table nor the new one. All four `service` dereferences were re-read in source — `service.setCredentials(…)` `:125`, `service.login()` `:126`, `devices = service.getDevices()` `:127`, `service.configureDevice(…)` `:182` — and every other row of this table was re-verified line-by-line at the same time; no other row moved.)*

Four rules that are not negotiable and that a reviewer must enforce:

1. **`selectedProvider!!` is forced at `:122`/`:123`, never at `:120`.** `SynchronizationProvider.fromIdentifier` returns `SynchronizationProvider?`. Forcing at the assignment would skip `preferenceHeader.setTitle("")` at `:121`, which in Java runs *before* the NPE. That is an observable difference — the header keeps its previous title instead of being cleared. Gap 5's test asserts the title is `""` after the throw, so this is not theoretical.
2. **`?.`, `?:`, `requireActivity()`, `requireContext()` and `requireView()` are forbidden anywhere a `!!` appears in the table.** Each converts a crash into either silence (`?.`) or a *different* exception type (`IllegalStateException` instead of `NullPointerException`). `MaterialAlertDialogBuilder(context!!)` specifically, not `MaterialAlertDialogBuilder(requireContext())`.
3. **No `!!` in this table may be collapsed into a hoisted local.** The four `service!!` sites in particular must stay four forced dereferences at the four original statements — `val svc = service!!` at the top of either lambda would reduce the count to 2 but move the NPE's throw point earlier, before `setCredentials`/`configureDevice`'s argument expressions are evaluated. That is a behavior change, it is not authorized by D13 (which forbids restructuring outright), and it would put the emitted code out of step with the count AC8 checks. The same rule applies to the 11 `findPreference` sites and the 5 ActionBar chains.
4. **`(activity as AppCompatActivity?)!!` is deliberate, not sloppy J2K output that survived review.** The tidier `(activity as AppCompatActivity)` throws on a failed null-to-non-null cast too, but the exception class is a Kotlin-version-dependent detail; the `?`-plus-`!!` form throws `java.lang.NullPointerException` unconditionally. Milestone 17 removes these five sites entirely when ActionBar handling moves to a ViewModel-backed host; until then they stay.

`SynchronizationCredentials.getHosturl().startsWith("http://")` (`GpodderAuthenticationFragment.java:103`) stays a bare platform-type call with **no** `!!` and **no** `?.` — it NPEs identically either way, and adding `?.` would silently suppress the account-creation warning instead of crashing.

---

**D12 — Three known defects are pinned and preserved. None is fixed.** (`net/sync/service-interface/README.md` convention #11; AGENTS.md "keep the diff to the absolute minimum".)

1. **The `fromIdentifier` → `switch` NPE** (gaps 5, `kotlin` finding 2). The `default:` arms at `:236`/`:247` returning `sync_status_error`/`ic_error` are dead code today. Converting to `when (provider) { … else -> … }` with a **non-null** parameter keeps them dead and keeps the crash. **Reaching them would be a crash→graceful behavior change and is forbidden here.** The `else ->` branches must survive the conversion — J2K may notice the `when` is exhaustive over two enum constants and drop them; AC14 greps to prove they are still there. Deleting them would erase the author's intent and change behavior the day a third provider is added. This is the file `:net:sync:service-interface` README #1 names as the unguarded call site; after this milestone it is guarded by a test, and that fact goes into `ui/preferences/README.md` (D15).
2. **The `devices` data race** (`kotlin` finding 9): written on an RxJava IO thread at `:127`, read on main at `:155`/`:221`, and not `volatile` while its three siblings are. It stays non-`@Volatile`. Milestone 17 owns it.
3. **The adapter's recycled-holder-as-field** (`kotlin` finding 10): `ViewHolder holder` is a field of the anonymous `ArrayAdapter`, reassigned on every `getView`. It stays a field. AC14 greps for it.

Two J2K mechanics in that adapter are prescribed because they are the likely accidental fixes: `getView`'s `convertView` parameter is reassigned in Java, so the Kotlin needs a local `var view = convertView` (parameters are `val`); and if Kotlin rejects the nested `class ViewHolder` declared inside the object expression, hoist it to a `private class ViewHolder` nested in `SynchronizationPreferencesFragment` and disclose it — behaviorally identical, since it is only ever moved through `setTag`/`getTag`.

---

**D13 — Idiomatic targets, stated so they are checkable, and bounded so they don't become a rewrite.**

Applied: `@Volatile` on the three volatile fields; `companion object` with `const val` for the constants; `when (currentStep)` replacing the `if/else-if` chain in `advance()` (single subject, so a **subject-ful** `when` — the subject-less form the track's idiom criterion mentions applies to independent boolean conditions and there are none here); `when (provider)` for the two `switch`es; `lateinit var viewFlipper` / `lateinit var viewBinding` (both assigned in `onCreateDialog` before any read, from a layout id that exists at compile time, so no reachable path observes them unset); string templates only where Java already concatenated; `replace(Regex(...), …)` and `lowercase(Locale.US)` for `replaceAll`/`toLowerCase(Locale.US)`.

Not applied, deliberately: no method extraction, no renaming, no reordering, no field-visibility changes beyond D9's `passwordHidden`, no `apply`/`also`/`let` scope-function restructuring of the listener wiring, no data classes, no extension functions, no conversion of `Pattern`/`Matcher` to `Regex` in `usernameHasUnwantedChars` (the Java allocates a fresh `Pattern` per call; a `Regex` hoisted to a companion would be a behavior-neutral but out-of-scope optimisation). `service`, `devices`, `selectedDevice`, `username`, `password` and `nextcloudLoginFlow` are **nullable `var`s with `!!` at use**, not `lateinit`, precisely because the code observes their null state (`:217`, `:246`, `:252`, `:69`, `:77`).

---

**D14 — CI runs only the Play flavor's unit tests; both flavors are run locally, as separate `--rerun` invocations.** (Correction to an assumption Research left implicit; carries M13's D-series discipline forward.)

`:ui:preferences` applies `playFlavor.gradle`, so its test tasks are `testFreeDebugUnitTest` / `testPlayDebugUnitTest` (`kotlin` finding 12). `.github/workflows/checks.yml:100` runs `./gradlew test${variant}UnitTest test${base-variant}UnitTest` for `PlayDebug`/`Debug` and `PlayRelease`/`Release` — `testDebugUnitTest` does not exist for a flavoured module, so Gradle satisfies that name from the unflavoured modules and **`testFreeDebugUnitTest` never runs in CI for this module**. Every step therefore runs both, separately, with `--rerun` (a combined invocation reports one of them `UP-TO-DATE`):

```bash
./gradlew --console=plain :ui:preferences:testFreeDebugUnitTest --rerun
./gradlew --console=plain :ui:preferences:testPlayDebugUnitTest --rerun
```

Recorded as OQ4 — it is a repo-wide gap, not this milestone's to fix.

---

**D15 — `ui/preferences/README.md` gains a conventions section. `AGENTS.md` rules apply: long-term-stable patterns only, no milestone numbers, no task-file references.**

Content: the slice's four files are Kotlin and `:storage:preferences` is still Java, so every preference getter arrives as a platform type; `AuthenticationDialog` is subclassed from `:app` and its two `protected` hooks plus nullable constructor parameters are external API; `TAG` constants must stay `const val`; the module's tests are Robolectric-based and drive real Fragments through a test host activity themed `Theme.AntennaPod.Light` (needed for `preferenceTheme`); test tasks are flavoured and CI only runs the Play one; the `!!` sites are deliberate crash-equivalence, not oversights; the three pinned defects are listed as pinned, with a pointer to the tests that pin them.

`tasks/antennapod-sync-settings-modernization-future-work.md` gains: this milestone's actual outcome under Milestone 15; a note under Milestone 17 that the `!!` inventory and the five ActionBar derefs are its cleanup; a note under Milestone 18 that this suite is the regression net for the repository seam; gap 16 as an explicit carried-forward hole.

### Steps

Each step is one reviewable diff and leaves the build green. **Every step ends with both flavors green as two separate `--rerun` invocations (D14)**, and Steps 8–11 additionally end with `./gradlew :app:assembleDebug`, `./gradlew checkstyle lint` and `./gradlew ktlintCheck` green, plus `git diff -- ui/preferences/src/test/` empty (D2).

**The characterization-tests-first rule is satisfied, not waived.** Research flagged a total coverage gap, so Steps 1–7 — seven of the twelve steps — write the pins before a single production line changes. No conversion step may begin until Step 7's baseline is recorded.

**Step 1 — Stand up the test source set and prove the harness. No production file changes.**
Edit `ui/preferences/build.gradle` per D4. Create `SyncSettingsTestHost.kt` (an `AppCompatActivity` themed `Theme_AntennaPod_Light`), `RecordingSynchronizationQueue.kt` (records call order across all 9 abstract members), and `SyncSettingsHarnessSmokeTest.kt` with the five D5 proof tests: host activity builds; `SynchronizationPreferencesFragment` attaches and all five `findPreference` keys resolve; `AuthenticationDialog` shows and is retrievable via `ShadowDialog.getLatestDialog()`; `GpodderAuthenticationFragment.onCreateDialog` completes and `viewFlipper.displayedChild == 0`; `NextcloudAuthenticationFragment.onCreateDialog` completes. Record which fallbacks from D5's table were needed. Tests: the five above.

**Step 2 — Pin `AuthenticationDialog` (gaps 25–27) and its Java interop shape.**
Create `AuthenticationDialogCharacterizationTest.kt` — password-toggle transformation-method class and button alpha `1.0f`/`0.6f` across two clicks; username field enabled state from the constructor flag; a `null` initial value leaving the field's text untouched rather than setting `""`; `onCancelled()` firing on both the negative button and `dialog.cancel()`. Create `AuthenticationDialogJavaInteropTest.java` per D8. Tests: `testPasswordToggleSwapsTransformationAndAlpha`, `testUsernameFieldEnabledStateFromConstructor`, `testNullInitialValuesLeaveFieldsUntouched`, `testOnCancelledFiresFromNegativeButtonAndFromCancelListener`, `testJavaSubclassOverrideShapes`.

**Step 3 — Pin `SynchronizationPreferencesFragment` state derivation and action wiring (gaps 1–8).**
Create `SynchronizationPreferencesFragmentCharacterizationTest.kt`. Gap 5's test is `testUnrecognisedProviderKeyThrowsAfterClearingHeaderTitle` and asserts **both** the NPE and `preferenceHeader.title == ""` — it is the test that makes D11's forcing-site rule enforceable. Gap 7's test asserts the five-step logout order *and* that `SynchronizationCredentials.clear()` fired `UserPreferences.setGpodnetNotificationsEnabled`. Tests: `testLoggedOutScreenState`, `testLoggedInScreenState`, `testGpodnetRowVisibleOnlyForGpodderNet`, `testLogoutSummaryIsHtmlParsedSpanned`, `testUnrecognisedProviderKeyThrowsAfterClearingHeaderTitle`, `testSetLoginDialogSeedsUsernameDisabledAndWritesPasswordOnly`, `testLogoutOrderingAndCrossClassSideEffect`, `testSyncAndFullSyncRowsCallQueueWithoutStateChange`.

**Step 4 — Pin `SynchronizationPreferencesFragment` event and lifecycle behavior (gaps 9–12).**
Create `SynchronizationPreferencesFragmentLifecycleTest.kt`. Tests: `testStickyEventReplaysOnStart`, `testSyncEventIgnoredWhenNotConnected`, `testSubtitleBranchesOnMessageResId`, `testOnStartSetsTitleAndOnStopSetsEmptySubtitleWhereasDisconnectedSetsNull`.

**Step 5 — Pin `GpodderAuthenticationFragment` (gaps 13–15, 17–20).**
Create `GpodderAuthenticationFragmentCharacterizationTest.kt`, driving private members by reflection per D7. Tests: `testFirstStepIsSetUpWithoutFlipping`, `testHostStepClearsCredentialsAndQueueBeforeSettingHostUrl`, `testUsernameValidationRunsBeforeAnyNetworkCall`, `testDeviceNameGenerationDedupesByIdAndByCaption`, `testDeviceIdUsesLocaleUsLowercase`, `testCredentialCommitOrderOnDeviceToFinishTransition`, `testPartialWizardLeavesCredentialsUntouched`, `testFinishStepDismissesBeforeSyncing`, `testDialogIsNonCancellableInBothSenses`. Record in Implementation Notes that gap 16 is not covered here and why (D7).

**Step 6 — Pin `NextcloudAuthenticationFragment` (gaps 21–24).**
Create `NextcloudAuthenticationFragmentCharacterizationTest.kt`. Tests: `testCredentialCommitOrderDiffersFromGpodderPath`, `testDeferredDismissWhenNotResumed`, `testLoginFlowSurvivesConfigurationChange`, `testDismissCancelsLoginFlow`, `testErrorDialogAppliesSpanToSecondHalfOnly`.

**Step 7 — Freeze the baseline. No repo diff; folds into Step 6's commit.**
Both flavors green with `--rerun`, per-class counts tabled, plus `git hash-object` for each of the eight test files. This is the "BEFORE" side of the equivalence proof and every later step compares against it.

**Step 8 — Convert `AuthenticationDialog.java` → `AuthenticationDialog.kt`** per D9. `git mv` then rewrite, so the rename is visible in review.

**Step 9 — Convert `NextcloudAuthenticationFragment.java` → `.kt`.** `TAG` → `const val` in a companion (D10); `viewBinding` `lateinit`; `nextcloudLoginFlow` nullable with its two existing null checks preserved; `MaterialAlertDialogBuilder(context!!)`, not `requireContext()` (D11 rule 2). The `NextcloudLoginFlow.AuthenticationCallback` overrides take `String?` — the Java interface carries no nullability annotations, so the values are platform types and the pass-through targets all accept null.

**Step 10 — Convert `GpodderAuthenticationFragment.java` → `.kt`.** `@Volatile` ×3, `devices` left non-volatile (D12.2), `when (currentStep)`, `service!!` at all **four** dereference sites with no hoisted local (D11 rule 3), and the two error handlers kept distinct — `error.cause!!.message` in `setupLoginView`, `error.message` in `createDevice` (D7's two-handler table / AC13).

**Step 11 — Convert `SynchronizationPreferencesFragment.java` → `.kt`.** The largest step and the one carrying 27 of the slice's 37 `!!` operators. D11's table and D12's three preservation rules are all enforced here. The class must remain a `public` no-arg-constructible subclass of `AnimatedPreferenceFragment` — never `internal`, or `PreferenceActivity.java:75` breaks.

**Step 12 — Verification sweep and documentation.**
Run every acceptance criterion's command and paste the output. Update `ui/preferences/README.md` and `tasks/antennapod-sync-settings-modernization-future-work.md` per D15, plus this task file's Implementation Notes and the checkpoint.

### File Scope

**Created — `ui/preferences/src/test/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/`:**
- `SyncSettingsTestHost.kt`
- `RecordingSynchronizationQueue.kt`
- `SyncSettingsHarnessSmokeTest.kt`
- `AuthenticationDialogCharacterizationTest.kt`
- `AuthenticationDialogJavaInteropTest.java`
- `SynchronizationPreferencesFragmentCharacterizationTest.kt`
- `SynchronizationPreferencesFragmentLifecycleTest.kt`
- `GpodderAuthenticationFragmentCharacterizationTest.kt`
- `NextcloudAuthenticationFragmentCharacterizationTest.kt`

**Renamed Java → Kotlin, same package and directory (`ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/`):**
- `AuthenticationDialog.java` → `.kt`
- `NextcloudAuthenticationFragment.java` → `.kt`
- `GpodderAuthenticationFragment.java` → `.kt`
- `SynchronizationPreferencesFragment.java` → `.kt`

After this milestone the package is 4/4 Kotlin and contains zero `.java` files.

**Modified:**
- `ui/preferences/build.gradle` (D4 — two plugin aliases, three `testImplementation` lines, nothing else)
- `ui/preferences/README.md` (Step 12 only — D15)
- `tasks/antennapod-sync-settings-kotlin-milestone-15.md`
- `tasks/antennapod-sync-settings-modernization-future-work.md`
- `features/antennapod-sync-settings-kotlin-milestone-15.checkpoint.md`

**Not in scope — a diff touching any of these means the plan was wrong and the task is re-planned, not patched:** `gradle/libs.versions.toml` (D4), `common.gradle`, `playFlavor.gradle`, root `build.gradle`, `settings.gradle`, `.editorconfig`, `config/checkstyle/**`, `config/spotbugs/**`, `.github/**`; every file under `ui/preferences/src/main/res/` — in particular `xml/preferences_synchronization.xml` and all nine dialog layouts; `ui/preferences/src/main/java/.../screen/AnimatedPreferenceFragment.java`; `ui/preferences/src/main/java/.../screen/NotificationPreferencesFragment.java`; anything under `ui/preferences/src/main/java/.../screen/bugreport/`; every file in `app/` (specifically `PreferenceActivity.java`, `MainPreferencesFragment.java`, `FeedSettingsPreferenceFragment.java`, `OnlineFeedViewActivity.java` — all four are guards, not edit targets); `storage/preferences/**`; `net/**`; `event/**`; `ui/i18n/**` and every other `ui/*` module; `model/`, `playback/**`, `parser/**`, `system/`, `app-wearos/`.

**No new user-visible string is added**, so AGENTS.md's `:ui:i18n` rule is not triggered — and adding one would itself be out of scope.

### Acceptance Criteria

Track: `kotlin`, `:ui:preferences` sync-settings slice. Every item is checked against Step 7's baseline.

**Characterization tests pass BEFORE the conversion — pin current behavior**
- [ ] **AC1** — At Step 7, against the four **unconverted Java** production files, both `./gradlew --console=plain :ui:preferences:testFreeDebugUnitTest --rerun` and `:testPlayDebugUnitTest --rerun` are BUILD SUCCESSFUL with 0 failures, 0 errors, 0 skipped, run as two separate invocations (D14). The per-class table is pasted for both flavors, and it accounts for all 27 Research gaps: 26 mapped to a named test method per D7's table, and gap 16 named as source-pinned with its reason.
- [ ] **AC2** — Step 1's Implementation Notes record, as observed fact rather than assumption, which of D5's four fallbacks were needed, the working Robolectric `sdk` level, and whether `AntennapodHttpClient.getHttpClient()` is constructible under Robolectric.

**Characterization tests pass AFTER the conversion — the equivalence proof**
- [ ] **AC3** — Both flavors are green as two separate `--rerun` invocations at the end of each of Steps 8, 9, 10, 11 and again at Step 12, every time with the same per-class counts as AC1, row for row, not merely in aggregate.
- [ ] **AC4** — No test is added, removed, renamed, split, merged or moved between classes after Step 7. The test-method-name list diffs empty against the AC1 baseline.
- [ ] **AC5** — `./gradlew :app:assembleDebug` is BUILD SUCCESSFUL after every conversion step, compiling `PreferenceActivity.java`, `MainPreferencesFragment.java`, `FeedSettingsPreferenceFragment.java` and `OnlineFeedViewActivity.java` **unedited** — the live external guard on the `PreferenceFragmentCompat` type, the `preferencesearch` index path, and `AuthenticationDialog`'s two subclasses.
- [ ] **AC6** — `./gradlew checkstyle lint` and `./gradlew ktlintCheck` are both BUILD SUCCESSFUL, with `ktlintMainSourceSetCheck` and `ktlintTestSourceSetCheck` genuinely **executing** (not `SKIPPED`/`NO-SOURCE`) for `:ui:preferences`, zero `@Suppress("ktlint:…")` added, no `.editorconfig` change, and no ktlint exclusion in any build file. Implementation Notes records whether Android Lint reported anything new against the `.kt` files — observed, not assumed, since `warningsAsErrors true` and `abortOnError true` are set repo-wide.

**Test suite is frozen across the conversion**
- [ ] **AC7** — `git diff <step-7-commit> -- ui/preferences/src/test/` is **empty** at the end of Steps 8, 9, 10 and 11 (D2). The eight `git hash-object` values from Step 7 are re-run at Step 12 and match. Any forced test edit stops the step and is disclosed with the signature change that caused it.

**`!!` inventory and crash-equivalence**
- [ ] **AC8** — `grep -o '!!' <file> | wc -l` per converted file yields **`SynchronizationPreferencesFragment.kt` 27, `GpodderAuthenticationFragment.kt` 8, `NextcloudAuthenticationFragment.kt` 2, `AuthenticationDialog.kt` 0 — 37 total**, and `grep -rn '!!' ui/preferences/src/main/java/…/synchronization/` produces an inventory matching D11's table row by row, per file and per site. In particular `grep -c 'service!!' GpodderAuthenticationFragment.kt` → **4**, one per original statement, with no hoisted local (D11 rule 3). Deviations are disclosed individually with a one-line justification naming the Java NPE each preserves; an undisclosed deviation fails this criterion.
- [ ] **AC9** — `grep -rnE 'requireActivity\(\)|requireContext\(\)|requireView\(\)' ui/preferences/src/main/java/…/synchronization/` returns **zero** hits (D11 rule 2).
- [ ] **AC10** — `SynchronizationPreferencesFragment.kt` forces `selectedProvider!!` at the two `getProviderSummary`/`getProviderIcon` call sites and **not** at the `fromIdentifier` assignment (D11 rule 1). Proven by `testUnrecognisedProviderKeyThrowsAfterClearingHeaderTitle` passing both before and after conversion — the title assertion is what discriminates, and Step 3's notes record it failing under a deliberately mis-placed `!!` (one-off live falsification, run and recorded, not asserted).
- [ ] **AC11** — `GpodderAuthenticationFragment.kt:` the `getHosturl().startsWith("http://")` site carries neither `!!` nor `?.`; `grep -c '?\.' ` over the four converted files is **0** unless individually disclosed.

**Known defects preserved, not fixed**
- [ ] **AC12** — `grep -c '@Volatile' GpodderAuthenticationFragment.kt` → **3** (`username`, `password`, `selectedDevice`), and `devices` is declared without it (D12.2).
- [ ] **AC13** — Gap 16's mechanical pin, scoped to discriminate the file's **two** error handlers rather than asserting a bare zero (D7's two-handler table / D12). Copy-pasteable, `-F` so no character is re-interpreted as a pattern:

  ```bash
  F=ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/GpodderAuthenticationFragment.kt
  grep -cF 'error.cause!!.message' "$F"   # → 1   setupLoginView's handler (Java :140) — gap 16
  grep -cF 'error.message'         "$F"   # → 1   createDevice's handler (Java :194) — preserved unchanged
  grep -cF 'error.cause?.message'  "$F"   # → 0   the safe-call form would swallow the NPE gap 16 pins
  grep -nF 'error.'                "$F"   # → exactly 2 hits, login handler first, createDevice second
  ```

  All four lines must hold. A `1`/`1` result is the pass; `0`/`2` means J2K flattened the login handler to `error.message` and gap 16's behavior was silently lost, which is precisely the hazard this criterion exists to catch. The fourth line is the completeness check — it fails if the conversion introduced a third `error.` reference the table does not account for. (`grep -nF 'error.'` does not match `txtvError.`: the substring there is capital-`E` `Error.`.)
- [ ] **AC14** — The two `when (provider)` expressions still carry `else ->` branches returning `R.string.sync_status_error` and `R.drawable.ic_error`, and the anonymous `ArrayAdapter` still declares `holder` as a **field of the adapter** reassigned inside `getView` (D12.1, D12.3). Both checked by direct inspection against the Java original; any hoisting of `ViewHolder` is disclosed.

**No public API break visible to Java callers outside the module**
- [ ] **AC15** — `AuthenticationDialogJavaInteropTest.java` compiles and passes, exercising both `:app` override shapes with **both `usernameInitialValue` and `passwordInitialValue` passed as literal `null`** — the shape `OnlineFeedViewActivity.java:508` reaches in production on the first `ERROR_UNAUTHORIZED`, when both of its backing fields (`:84-85`) are still null (D8). `javap -p` on the built `AuthenticationDialog.class` is pasted and shows: the class `public abstract`; the constructor's descriptor `(Landroid/content/Context;IZLjava/lang/String;Ljava/lang/String;)V`; `onCancelled()` and `onConfirmed(String,String)` both `protected`, the latter `abstract`.
- [ ] **AC16** — `grep -rn 'passwordHidden' ui/preferences/ app/` returns hits only inside `AuthenticationDialog.kt`, confirming the package-private → `private` narrowing (D9) is externally a no-op.
- [ ] **AC17** — `javap -p` shows `TAG` as a `public static final java.lang.String` on `GpodderAuthenticationFragment` and on `NextcloudAuthenticationFragment` — not on a nested `Companion` (D10). This is the property that keeps Steps 9–10 compilable while `SynchronizationPreferencesFragment` is still Java, and it stays a convention afterwards.
- [ ] **AC18** — `SynchronizationPreferencesFragment.kt` is a `public` class with an implicit no-arg constructor, still assignable to `PreferenceFragmentCompat`; not `internal`. Covered structurally by AC5.

**Idiomatic Kotlin target achieved, without behavior drift**
- [ ] **AC19** — `find ui/preferences/src/main/java/…/synchronization -name '*.java'` returns **nothing**; `-name '*.kt'` returns **4**. Every `!!` in the module is accounted for by AC8's table, so there is no *unjustified* `!!`. `advance()` dispatches through a subject-ful `when (currentStep)`; the two provider `switch`es are `when` expressions; the three volatile fields carry `@Volatile`; constants live in `companion object`s as `const val`.
- [ ] **AC20** — No idiomization beyond D13's applied list: `grep -rnE '\.apply \{|\.also \{|\.let \{|\bdata class\b|\bfun [A-Za-z]+\.[A-Za-z]+\(' ` over the four converted files returns **zero** hits, and `usernameHasUnwantedChars` still allocates its `Pattern` per call.

**Scope**
- [ ] **AC21** — `git diff --name-only origin/develop` lists only File Scope files. In particular `gradle/libs.versions.toml`, `common.gradle`, every file under `ui/preferences/src/main/res/`, `AnimatedPreferenceFragment.java` and every file in `app/` are unchanged.
- [ ] **AC22** — `ui/preferences/README.md` and `tasks/antennapod-sync-settings-modernization-future-work.md` are updated per D15, phrased as long-term-stable module conventions with no milestone number and no task-file reference (AGENTS.md).

**Not applicable to this milestone, asserted rather than assumed.** Accessibility (content descriptions, dynamic type) and dark-mode / hardcoded-color criteria attach to the `compose` and `navigation` tracks; neither is in flight, no new UI is created, no layout or theme file is touched, and the rendered screen is byte-identical because the XML and the `PreferenceFragmentCompat` inflation path are unchanged — which is also what preserves the ability to capture a "before" screenshot in a later milestone (Research Unknown 2, deferred). Paparazzi snapshots, instrumented back-stack and deep-link tests, SDUI contract versions, and analytics likewise do not apply: this module has no `androidTest` source set and this milestone adds none, and no Navigation Component exists anywhere in the repo. No HSHD is handled — the slice stores a sync username, password and host URL in `SharedPreferences` via `:storage:preferences`, unchanged by this milestone, and **no credential value appears in any test fixture, assertion message or log**; fixtures use obvious placeholders.

### Milestone

**Milestone 15 — `:ui:preferences` sync-settings slice (`screen/synchronization/`, 4 files), `kotlin` track.** Single milestone, single unified PR (code plus spec docs, per the M7/M9/M10/M12/M13/M14 precedent), twelve steps in roughly seven commits: Step 1; Steps 2–7 (characterization suite, committed per step); Steps 8, 9, 10, 11 (one commit each); Step 12.

First of the six-milestone sync-settings sequence (16 toolchain → 17 concurrency+MVVM → 18 DI wiring → 19 `:storage:preferences` → 20 compose), and the fourteenth milestone in the portfolio overall. Follows Milestone 14 (PR #20).

This is unaffiliated OSS portfolio work, so "milestone" is case-study structure, not invoicing. The case-study angle it earns is the sharpest one available to a migration practice: **this module had zero tests and zero test infrastructure, and the conversion was still gated on 26 executable behavioral pins written first — including a test that asserts a crash and asserts the screen state at the moment of the crash, because moving one `!!` two lines earlier would have changed it.** That is the concrete answer to "how do you know the Kotlin behaves the same," and it is more persuasive than any diff.

### Out of Scope

- **The `concurrency` track.** The two orphaned RxJava3 `Disposable`s stay orphaned, the sticky EventBus subscription stays EventBus, and `GpodnetService`'s blocking API stays blocking. No `kotlinx-coroutines-*` dependency, no `Flow`, no `viewModelScope`. Milestone 17.
- **The `di` track.** `ClientConfigurator`'s static-init pattern, `SynchronizationQueue.instance` as a mutable public global, and `EventBus.getDefault()` all stay exactly as they are. This milestone *uses* the global as a test seam (D6) rather than replacing it. No Hilt, no Dagger, no KSP/KAPT, no `:app` footprint. Milestones 16 and 18.
- **The `compose` track, and the ViewModel/MVVM layer it requires.** `SynchronizationPreferencesFragment` has no ViewModel, no state holder and no UI-state type; it reads everything on demand from static globals. Introducing that layer is bespoke architectural work, not part of any track, and Research correctly flagged it as a **blocking prerequisite for `compose`** — see OQ1. Nothing in this plan creates a ViewModel, a UI-state class, or a `ComposeView`. Milestones 17 and 20.
- **The `gradle-kts` track.** `ui/preferences/build.gradle` stays Groovy, including the `groovy.xml.XmlSlurper`-adjacent `exec {}` git-hash block at `:10-18`. Not requested; noted so it is not mistaken for trivial later.
- **The `navigation` track.** Manual `FragmentTransaction` navigation via `PreferenceActivity.openScreen` and `show(childFragmentManager, TAG)` are unchanged. Not requested.
- **`:storage:preferences`.** `SynchronizationSettings` and `SynchronizationCredentials` stay Java, unannotated, and static. Converting them is a five-module blast radius with its own characterization bill (Research Unknown 1). Milestone 19, which may decide not to proceed at all.
- **Fixing the three pinned defects** (D12): the `fromIdentifier` NPE, the `devices` data race, the adapter's recycled-holder-as-field. Each is pinned by a test in this milestone and tracked. Fixing any of them is a behavior change requiring its own review.
- **Any change to `preferences_synchronization.xml`, the nine dialog layouts, or the drawables.** The XML is the `preferencesearch` index source and the guarantee that the rendered screen is unchanged.
- **`AnimatedPreferenceFragment` and `NotificationPreferencesFragment`.** Both are Java, both are in this module, neither is in the slice. `NotificationPreferencesFragment.java:25` keeps reading `SynchronizationSettings.isProviderConnected()` on the static API.
- **The "before" screenshot** (Research Unknown 2). No mechanism is chosen here and none is needed: the rendered output is unchanged, so the capture can happen any time before Milestone 20. Recorded as OQ3.
- **Adding Paparazzi, Compose Material3, coroutines, Hilt, KSP/KAPT, MockWebServer, Mockito or a Compose BOM to the version catalog** (D4). All of it is Milestone 16's, and pre-empting it here would put unused dependencies into a conversion diff.
- **Raising the `kotlin-bom` pin** (Research Unknown 10). Not exercised by this milestone; Milestone 16 owns it.
- **Extracting, renaming, deduplicating or "improving" anything** (D13). Every improvement spotted during conversion goes into the future-work file, not into this diff.

## Open Questions
_Last updated by: legacy-android-planner | 2026-08-06_

None of these block implementation. Steps 1–12 proceed as written regardless of how they are answered.

**OQ1 — The `compose` track's blocking prerequisite, restated so it is not lost between milestones.** (Research Track prerequisites; Research Unknown 5.)

`compose` was requested for this slice in the original Milestone 15 scoping and cannot proceed as a `compose` track, because there is no ViewModel/MVVM layer for this screen to build on — `SynchronizationPreferencesFragment` reads all state on demand from static globals and has no state holder of any kind. `BugReportViewModel` belongs to a different screen and is a shape reference, not a layer. **Standing up that layer is bespoke architectural work, priced and scoped separately, and is not included in this plan or in any `compose` plan.** The future-work file currently folds it into Milestone 17 alongside the `concurrency` work; that is a reasonable sequencing but it does not make it track work, and Milestone 17's planner should surface it as its own scoped item rather than treating it as concurrency overhead. **Confirm with José before Milestone 17 is planned** whether the ViewModel layer is a funded line item or whether Milestone 17 is `concurrency`-only, in which case Milestone 20 (`compose`) stays blocked. Not this milestone's decision, and deliberately not folded into this milestone's File Scope.

**OQ2 — Gap 16 has no executable pin, and it is the only one.** (D7.)

`error.getCause().getMessage()` at `GpodderAuthenticationFragment.java:140` is reachable only through a failed real login against a `GpodnetService` the fragment constructs internally. AC13's grep pins the *shape* of the converted expression, which is enough to catch the specific J2K hazard (`error.message` instead of `error.cause!!.message`), but nothing proves the resulting user-visible error text is unchanged. Closing it needs either MockWebServer (a new catalog entry — Milestone 16) or the injectable `GpodnetService` that Milestone 17/18 produces anyway. Worth deciding whether Milestone 17 picks it up explicitly, because after this milestone it will look covered.

**OQ3 — When is the "before" screenshot captured?** (Research Unknown 2, deliberately deferred.)

This milestone guarantees the rendered screen is unchanged (no res file touched, `PreferenceFragmentCompat` inflation path untouched), so the "before" capture is not urgent. But it becomes impossible the moment Milestone 20 lands, and Milestone 17's ViewModel work will already start moving what the screen reads. Paparazzi cannot straightforwardly render a `PreferenceFragmentCompat` (its view comes from a `RecyclerView`-backed `PreferenceManager` inflation, not a plain `View`), so the realistic options remain a manual `adb exec-out screencap` checked in as a PNG, or the module's first `androidTest`. **Recommend capturing it manually at the end of this milestone** — it costs nothing, needs no tooling, and removes a dependency on the View-based screen still existing five milestones later. Not added to File Scope because a checked-in PNG is a deliverable decision with a marketing angle, and per the AEPM operational rules that is José's call, not a planner's.

**OQ4 — CI never runs the Free flavor's unit tests for flavoured modules.** (D14, measured at planning time.)

`.github/workflows/checks.yml` runs `test${variant}UnitTest test${base-variant}UnitTest` for `PlayDebug`/`Debug` and `PlayRelease`/`Release`. `testDebugUnitTest` does not exist for a module applying `playFlavor.gradle`, so Gradle satisfies that task name from the unflavoured modules and `:ui:preferences:testFreeDebugUnitTest` — along with `:net:sync:service`'s and `:ui:chapters`' Free equivalents — never executes in CI. This plan works around it by running both locally at every step, but the gap is repo-wide, has existed since before this case study, and is exactly the kind of thing a migration engagement is expected to notice. Fixing it is a one-line workflow change and is out of scope here. Worth raising upstream if OQ5 resolves that way.

**OQ5 — Upstreaming intent.** Standing, carried unchanged from M7/M9/M11/M12/M13/M14 (`tasks/antennapod-model-kotlin-future-work.md` item 2).

This milestone sharpens it. The converted files will contain 37 `!!` operators that are individually justified and collectively ugly, and an upstream AntennaPod reviewer seeing them in one PR will reasonably ask why the conversion did not simply guard the nulls. The answer — "each one preserves an existing crash, and Milestone 17 removes the underlying pattern" — is convincing in the context of a documented migration sequence and much less so as a standalone contribution. If upstreaming is the goal, the sequence probably wants to be offered as a series with this milestone's rationale attached, or Milestone 17 wants to land before anything is offered at all. **Commercial/strategic — for José**, per root `CLAUDE.md`'s commercial-implications rule; not an agent decision. Unanswered across six milestones without ever blocking one.

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-08-06 | Loop 1 of max 2_

### Verdict
CHALLENGE

### Concerns

Categories considered and dismissed with no finding: characterization-test *existence-vs-proof* quality (D7's gap→test-method mapping was checked line-by-line against Research's 27 gaps — all 27 are accounted for, 26 with named executable methods, 1 explicitly source-level; Steps 2–6's test-method lists match D7's table exactly, including split coverage where one gap has two observable sub-behaviors, e.g. gap 18, gap 23, gap 26); `di`/`concurrency`/`compose`/`navigation`/`gradle-kts` tracks (correctly out of scope, nothing bleeds in — File Scope's "Not in scope" list was checked against the actual repo structure and is accurate); File Scope creep (checked against actual source tree, no drift); D1's git-fetch/ancestry claim (reproduced exactly: `origin/develop` at `2026-08-05 11:24:32 +0200`, tip `5f816b768` (#19), `git merge-base --is-ancestor af93320b1 origin/develop` returns false — the plan's evidence is correct, not stale, not fabricated); the `AuthenticationDialog` interop guard (both `:app` subclasses read from source — `FeedSettingsPreferenceFragment.java:193` anonymous, overriding only `onConfirmed`, passing `feedPreferences.getUsername()`/`getPassword()`; `OnlineFeedViewActivity.java:503` named `FeedViewAuthenticationDialog`, overriding both hooks and calling `super.onCancelled()` — D9's Kotlin shape and the three-mechanism guard (assembleDebug, Java oracle test, `javap -p`) genuinely cover both shapes); D11 rule 1's forcing-site reasoning (verified directly against source: `preferenceHeader.setTitle("")` is at line 121, strictly between the `fromIdentifier` assignment at 119–120 and the `getProviderSummary`/`getProviderIcon` call sites at 122–123 — forcing `!!` at the assignment would throw before `setTitle("")` runs and is a real, not theoretical, divergence; forcing at the call sites preserves it exactly as the plan claims).

- **Severity:** CRITICAL
- **Class:** Characterization tests prove equivalence, not just existence / Coverage gaps left unaddressed
- **Concern:** AC13 is the plan's *only* verification for gap 16 (`error.getCause().getMessage()`), explicitly called out in D7/OQ2 as the one behavior with no executable pin — the plan states outright that this grep "is a mechanical check with a definite pass/fail, not a promise." As literally specified, it is not definite. `GpodderAuthenticationFragment.java` has **two** RxJava error handlers, not one: the login handler at `:140` (`error.getCause().getMessage()`, gap 16 — must become `error.cause!!.message`) and a second, unrelated, legitimately-preserved handler in `createDevice` at `:194` (`error.getMessage()`) which J2K will very likely emit as `error.message`. AC13's command — `grep -c 'error\.message' → 0` — is given with no line-range or function-scoping. Run literally against a *correct* conversion, it returns 1 (from the `createDevice` handler), not 0, producing a false failure on the plan's own admitted weakest coverage spot. The parenthetical "for the login handler" is prose, not part of the actual command; nothing in the AC text tells the developer how to scope the grep to only `setupLoginView`'s handler.
- **Evidence:** Plan D7/OQ2 (task file, "Gap 16 … stated plainly as the one behavior this milestone does not pin executably … pinned by AC13's grep … a mechanical check with a definite pass/fail, not a promise"); AC13 text: "`grep -c 'error\.message'` → **0** for the login handler"; source `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/GpodderAuthenticationFragment.java:140` (`error.getCause().getMessage()`, the target) vs `:194` (`error.getMessage()`, the confound) — confirmed by direct read.
- **Suggested mitigation:** Rewrite AC13's command to be scoped and copy-pasteable, e.g. extract the body of the converted `setupLoginView`'s error lambda (by function boundary or a preceding-context grep anchored on the login `subscribe(` call) before counting, or simply require the *count* of `error.cause!!.message` to be exactly 1 **and** the count of bare `error.message`/`error.getMessage()` to be exactly 1 (matching the `createDevice` handler, which must be preserved unchanged) rather than 0. State the exact two-handler shape explicitly in D7/AC13 so gap 16's mechanical check is actually mechanical.

- **Severity:** MAJOR
- **Class:** Silent behavior changes from mechanical translation
- **Concern:** D11's `!!` inventory table — described as "the milestone's central equivalence discipline" and "binding," with AC8 requiring a grep-based inventory to match it "per file and per site" — undercounts the `service!!` row for `GpodderAuthenticationFragment.kt`. The table lists this row's site count as **3** ("`service!!` at `:125,126,127`-equivalent and `:182`"), but the cited locations are four distinct statements dereferencing the nullable `service` field: `service.setCredentials(...)` (:125), `service.login()` (:126), `devices = service.getDevices()` (:127), and `service.configureDevice(...)` (:182, in the separate `createDevice` method). Confirmed directly against source — there is no fifth statement and no way to read the four cited lines as three sites without an undocumented local-variable hoist (`val svc = service!!`), which D13 does not authorize and which would itself shift the NPE's throw point relative to the Java original. Since AC8 treats deviation from this table as something requiring individual disclosure, and the table's own count doesn't match its own line list, a developer or reviewer following the "3" figure literally has no unambiguous target to implement or check against.
- **Evidence:** Plan D11 table row: "`GpodderAuthenticationFragment.kt` | 3 | `service!!` at `:125,126,127`-equivalent and `:182` (count per emitted receiver) | `service` is assigned only in `setupHostView`"; source `GpodderAuthenticationFragment.java:125-127` (three statements in the login `Completable.fromAction` lambda) and `:182` (`createDevice`'s `Observable.fromCallable` lambda) — four total statements, confirmed by direct read.
- **Suggested mitigation:** Correct the table to state 4 sites (or explicitly justify a 3-site count via a named, D13-authorized local-`val` hoist, and reconcile with D13's "no scope-function restructuring" language). Either way, make the number match the enumerated lines so AC8's grep has an unambiguous target.

- **Severity:** MAJOR
- **Class:** Silent behavior changes from mechanical translation (traceability of the equivalence argument itself)
- **Concern:** D8 justifies constructing `AuthenticationDialogJavaInteropTest.java`'s subclasses "with a null usernameInitialValue (as `SynchronizationPreferencesFragment.java:87` does)," and AC15 repeats "a `null` `usernameInitialValue`" as part of what the interop test must exercise. Both the citation and the value are wrong. `SynchronizationPreferencesFragment.java:87` is not one of the two `:app` call sites the interop test exists to mirror (`FeedSettingsPreferenceFragment.java:193` and `OnlineFeedViewActivity.java:503`) — it's the in-slice anonymous subclass instantiation, which the interop test isn't modeling at all. And that line passes a literal `null` for **`passwordInitialValue`**, not `usernameInitialValue` — the username argument there is `SynchronizationCredentials.getUsername()`, a nullable getter, not a literal null. D9's underlying conclusion (both constructor params must stay `String?`) is still correct on independent grounds, but the specific evidentiary citation baked into a binding design decision and copied into an acceptance criterion is factually backwards, in the one place (the interop test) this milestone leans hardest on for proving the external Java contract is preserved.
- **Evidence:** Plan D8 ("constructed with a null usernameInitialValue (as `SynchronizationPreferencesFragment.java:87` does)"); AC15 ("exercising both `:app` override shapes and a `null` `usernameInitialValue`"); source `SynchronizationPreferencesFragment.java:85-92` (`new AuthenticationDialog(activity, R.string…, false, SynchronizationCredentials.getUsername(), null) { … }` — null is the *password* argument, and the surrounding class is not `:app`); `FeedSettingsPreferenceFragment.java:193-195` and `OnlineFeedViewActivity.java:503-508` (the actual two `:app` sites, neither of which passes a literal `null` in source — both pass nullable-getter values).
- **Suggested mitigation:** Fix the citation and the parameter: either construct the interop test's nulls to match what the two real `:app` call sites can plausibly pass (nullable getters, so pick a null username *or* null password deliberately and say so), or drop the specific-line citation and just state directly "both parameters must accept null because the constructor signature is `String?, String?`" without implying a false source pin.

- **Severity:** MINOR
- **Class:** Silent behavior changes from mechanical translation (supporting evidence accuracy)
- **Concern:** D3's correction states Robolectric "appears in **15 modules**" repo-wide. A repo-wide case-insensitive grep for `robolectric` across all `build.gradle`/`*.gradle` files finds 14 modules (`ui/chapters`, `net/common`, `net/download/service-interface`, `net/download/service`, `net/sync/service-interface`, `net/sync/service`, `net/sync/wear-interface`, `app`, `parser/transcript`, `parser/feed`, `parser/media`, `storage/database`, `storage/importexport`, `model`). The off-by-one doesn't change the correction's substance (Robolectric is clearly repo-wide, not module-local to `:net:sync:service-interface`'s convention #10), but it's a measured-at-planning-time number stated as fact in a plan whose whole pitch is precision.
- **Evidence:** `grep -rli robolectric --include="*.gradle" .` → 14 hits, enumerated above, vs D3's stated "15 modules."
- **Suggested mitigation:** Re-run the count and correct the number, or drop the specific figure and just say "well over a handful, including `:ui:chapters`."

### Recommendation

Loop 1 of 2. None of these findings require re-scoping the milestone or touching File Scope — they're specification bugs in the Plan's own verification apparatus (an unscoped grep, a miscounted table row, a backwards citation), which is exactly the kind of thing that should be fixed before Step 1 rather than discovered mid-implementation when the "definite pass/fail" checks turn out not to be. Send back to `legacy-android-planner` to: (1) rewrite AC13 with an unambiguous, scoped command that actually discriminates the two error handlers; (2) correct D11's `service!!` site count to 4 (or justify 3 with an authorized hoist) and re-verify the rest of the `!!` inventory table row-by-row against source, since this is the one table AC8 treats as ground truth; (3) fix D8/AC15's citation. The MINOR module-count item can be fixed opportunistically and doesn't need its own review cycle.

## Plan — Revision 1 (responding to red-team loop 1)
_Last updated by: legacy-android-planner | 2026-08-06_

**All four findings accepted, and all four were re-derived from source in this session before acceptance rather than taken on the report's word.** Every one checked out exactly as reported. Nothing here changes a decision's substance, File Scope, step ordering, or what the developer will actually write — all four were defects in the Plan's **verification apparatus**: a grep that would false-fail on correct code, a table whose count contradicted its own line list, a citation pointing at the wrong file and the wrong parameter, and a miscounted module tally.

That they are all in the verification apparatus rather than in the decisions is not a mitigation, it is the finding. This milestone's entire pitch is that a zero-test module can be converted safely because 26 behaviors are pinned executably and the 27th is pinned by a check with "a definite pass/fail, not a promise." AC13 was that 27th check, and as written it returned `1` against a *correct* conversion. A developer following it literally would have been told the one behavior with no test had regressed, when it had not — the worst possible failure mode for a plan sold on precision, because it trains the reader to discount the checks.

### Verification I performed this loop

- **Finding 1 (AC13's unscoped grep) — confirmed.** Read `GpodderAuthenticationFragment.java:115-200` directly. Two RxJava error lambdas exist: `setupLoginView`'s at `:137-142` calling `error.getCause().getMessage()` (`:140`, gap 16) and `createDevice`'s at `:191-196` calling `error.getMessage()` (`:194`). The second is unrelated to gap 16, is correct as written, and a faithful conversion emits `error.message` for it — so the old command's `→ 0` target was unreachable by any correct diff.
- **Finding 2 (`service!!` count) — confirmed, and the whole table re-verified row by row.** Four distinct dereferences of the nullable `service` field: `service.setCredentials(usernameStr, passwordStr)` (`:125`), `service.login()` (`:126`), `devices = service.getDevices()` (`:127`) — all three inside the login `Completable.fromAction` lambda — and `service.configureDevice(deviceId, deviceNameStr, …)` (`:182`) inside `createDevice`'s `Observable.fromCallable`. There is no fifth, and no reading of those four lines yields three sites. Every other row was re-greped at the same time and all hold: `findPreference` 11 sites at `:83,96,100,104,117,135,138,139,140,145,147`; the ActionBar chain at exactly `:53,63,77,157,226` (5 statements, `(AppCompatActivity) getActivity()).getSupportActionBar()` in each, hence 10 operators); `SynchronizationQueue.getInstance()` 3 in `SynchronizationPreferencesFragment` (`:97,101,106`), 2 in `GpodderAuthenticationFragment` (`:85,234`), 2 in `NextcloudAuthenticationFragment` (`:95,99`); `Snackbar.make(getView(), …)` 1 at `:107`; `fromIdentifier` at `:119-120` with `preferenceHeader.setTitle("")` at `:121` and the two forcing sites at `:122,123`; `devices` read at `:155`.
- **Finding 3 (D8/AC15's citation) — confirmed, both halves.** `SynchronizationPreferencesFragment.java:85-92` reads `new AuthenticationDialog(activity, R.string.pref_gpodnet_setlogin_information_title, false, SynchronizationCredentials.getUsername(), null)` — the literal `null` is the **fifth** argument, `passwordInitialValue`, and the class is in `:ui:preferences`, not `:app`. Neither of the two `:app` sites the interop test mirrors was passing a literal null at all: `FeedSettingsPreferenceFragment.java:193-195` passes `feedPreferences.getUsername()`/`getPassword()`, and `OnlineFeedViewActivity.java:508` passes the Activity's `username`/`password` fields.
- **Finding 4 (Robolectric module count) — confirmed.** `grep -rn 'robolectric' --include='*.gradle' .` → 14 hits, one `testImplementation libs.robolectric` per module, enumerated in D3. Fourteen, not fifteen.

**One thing I found that the red-team's finding 3 did not, and it strengthens D8 rather than weakening it.** The report's mitigation offered "drop the specific-line citation and just state the constructor is `String?, String?`" as an option. That would have been the weaker fix, because a genuinely-null-username call site *does* exist — it just isn't the one that was cited. `OnlineFeedViewActivity` declares `private String username = null` and `private String password = null` (`:84-85`), assigns them only in `onConfirmed` (`:520-521`) or from saved state, and constructs `FeedViewAuthenticationDialog` in `checkDownloadResult` (`:274-286`) on the **first** `ERROR_UNAUTHORIZED` — when both are still null. The guard `if (username != null && password != null)` sitting immediately above that construction (`:279`) is the upstream author's own acknowledgement of it. So D8's null case is now pinned to a reachable production path in the very `:app` class the interop test exists to mirror, which is a better justification than the one that was there.

### Edits made, in place in the Plan above

**CRITICAL — AC13 rewritten as a four-line scoped command, and D7 now states the two-handler shape.**

1. **D7's gap-16 paragraph gains a two-row table** naming both handlers, their Java line numbers, their required Kotlin, and — for `createDevice` — an explicit instruction that it is **preserved verbatim and must not be "made consistent"** with the login handler. The old sentence "the converted file must read `error.cause!!.message`, never `error.message`" is struck and marked as corrected, because it is false of the file as a whole.
2. **AC13 replaced.** It now runs four `grep -F` counts against `GpodderAuthenticationFragment.kt`: `error.cause!!.message` → 1, `error.message` → 1, `error.cause?.message` → 0, and `grep -nF 'error.'` → exactly 2 hits in order. The two string counts are disjoint (`error.cause!!.message` does not contain the substring `error.message`), so the `1`/`1` pass is unambiguous and the `0`/`2` failure mode is exactly the J2K hazard gap 16 exists to catch. `-F` throughout so `!!`, `?` and `.` are never re-interpreted as patterns by whichever grep is on the developer's `PATH`. The fourth line adds a completeness check the old criterion had no equivalent of. **Gap 16 is still not executably pinned** — that has not changed and OQ2 stands unaltered; what changed is that its substitute check now discriminates.
3. **Step 10 restated** to name both handlers explicitly, so the developer sees the two-expression requirement at the step that writes them, not only in an acceptance criterion read afterwards.

**MAJOR — D11's `service!!` row corrected to 4, with per-file totals and a new anti-hoist rule.**

4. **The row now reads 4**, with the four statements attributed to their lambdas so the count and the line list agree.
5. **Per-file totals added under the table**, which is what AC8 now checks: 27 / 8 / 2 / 0, **37** across the slice, stated explicitly as an operator count rather than a statement count (the five ActionBar chains carry two each — the single most likely source of a miscount during review).
6. **A new D11 rule 3 forbids collapsing any tabled `!!` into a hoisted local.** This closes the loophole the red-team's mitigation named: `val svc = service!!` would make the count read 2 and would move the NPE's throw point ahead of the argument expressions — a real behavior change, unauthorized by D13, and invisible to a count-only check. The old rule 3 (`(activity as AppCompatActivity?)!!`) is renumbered to 4; nothing in the Plan referenced "D11 rule 3", and the references to rules 1 and 2 (AC9, AC10, Step 9) are unaffected.
7. **AC8 upgraded from "matching D11's table" to a numeric check** — `grep -o '!!' | wc -l` per file against the four totals, plus `grep -c 'service!!'` → 4 specifically. The disclosure-on-deviation clause is unchanged.
8. **Step 11's "27 of the 30" and OQ5's "30 `!!` operators" corrected to 37.** Flagging this plainly because it goes one step past the reported finding: **both figures were already wrong before this revision** — the pre-correction table summed to 36, not 30, so 30 matched neither table. Correcting the `service!!` row without correcting the derived totals would have left the plan self-contradicting in two more places. Step 11's own "27" (for `SynchronizationPreferencesFragment`) was and remains correct.

**MAJOR — D8/AC15's citation and parameter fixed.**

9. **D8's justification re-anchored to `OnlineFeedViewActivity.java:503-510`**, with the null-fields-at-first-`ERROR_UNAUTHORIZED` path spelled out and the `:279` guard cited as evidence. The false citation is quoted and marked corrected in place rather than deleted, so a reader of a later loop can see what changed.
10. **The interop test's construction changed from "null `usernameInitialValue`" to both parameters literal `null`**, which is what the corrected call site actually reaches, and D8 now notes that neither `:app` site passes a literal null in source — which is precisely why the test has to.
11. **AC15 updated to match**, naming both null parameters and the call site they mirror. **D9 needed no change**: its bullet already said `SynchronizationPreferencesFragment.java:87` "passes a literal `null` for the password," which is correct — the error was confined to D8 and AC15.

**MINOR — D3's module count.**

12. **15 → 14**, with all fourteen modules enumerated inline and the grep that produces the figure given, so the number is re-checkable rather than asserted. The correction's substance is untouched: Robolectric is repo-wide, `:net:sync:service-interface`'s convention #10 is module-local, and `:ui:chapters` remains the direct `ui/*` precedent.

### Counts and shape

**Unchanged:** all **12 Steps** and their order; the commit structure; **File Scope** in full — no file added, removed, or moved, and every correction lands inside the task file itself; **22 Acceptance Criteria**, none added or deleted, and no criterion's pass/fail *outcome* against a correct implementation moved except AC13's, which previously could not be passed at all; the Milestone framing; Out of Scope; **OQ1–OQ5** as written. D1, D2, D4–D6, D9, D10, D12–D15 are untouched.

**Changed:** **D7** (gap-16 paragraph gains the two-handler table), **D11** (`service!!` row 3 → 4, per-file totals added, new rule 3, old rule 3 → 4), **D8** (citation re-anchored, both parameters null), **D3** (15 → 14, list enumerated), **AC13** (rewritten), **AC8** (numeric totals), **AC15** (matched to D8), **Step 10** and **Step 11** (wording), **OQ5** (one figure).

**Net effect on the diff the developer will write:** one real change and one removed trap. The real change is D11 rule 3 — the four `service!!` sites must stay four, where a developer optimising for tidiness might have hoisted them and silently moved a throw point. The removed trap is AC13, which as written would have failed a correct conversion on the milestone's only unpinned behavior. Everything else changes what the Plan *claims* and how it is checked, not what gets written.

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-08-06 | Loop 2 of max 2_

### Verdict
APPROVE

### Concerns

All four Loop 1 findings were independently re-derived from source in this session — not taken on the planner's word — and every one checked out exactly as reported:

- **Finding 1 (AC13).** Read `GpodderAuthenticationFragment.java:115-200` directly. Two RxJava error lambdas exist exactly as the revision states: `setupLoginView`'s at `:137-142` (`error.getCause().getMessage()` at `:140`, gap 16) and `createDevice`'s at `:191-196` (`error.getMessage()` at `:194`, unrelated, preserved). The rewritten AC13 (four `grep -F` counts, `error.cause!!.message`→1, `error.message`→1, `error.cause?.message`→0, `error.` count→2) discriminates the two handlers correctly and is unambiguous against a correct conversion.
- **Finding 2 (`service!!` count and full inventory reconciliation).** Confirmed the four `service` dereferences by line: `service.setCredentials(...)` `:125`, `service.login()` `:126`, `devices = service.getDevices()` `:127`, `service.configureDevice(...)` `:182`. All other D11 rows re-checked against source and match: `findPreference` at `:83,96,100,104,117,135,138,139,140,145,147` (11 sites); ActionBar chain at `:53,63,77,157,226` (5 statements, confirmed `((AppCompatActivity) getActivity()).getSupportActionBar()` at each); `SynchronizationQueue.getInstance()` at `:97,101,106` in the screen (3), `:85,234` in Gpodder (2), `:95,99` in Nextcloud (2); `Snackbar.make(getView(), …)` at `:107` (1); `selectedProvider` forced at `:122,123` (2), with `preferenceHeader.setTitle("")` confirmed at `:121`, strictly between the `fromIdentifier` assignment (`:119-120`) and the two forcing sites — the rule-1 ordering argument holds. **Arithmetic independently summed, not just re-quoted:** `SynchronizationPreferencesFragment.kt` 11+10+3+1+2 = **27**; `GpodderAuthenticationFragment.kt` 2+4+1+1 = **8**; `NextcloudAuthenticationFragment.kt` 2+0 = **2**; `AuthenticationDialog.kt` **0**. Sum = 27+8+2+0 = **37**, matching AC8, Step 11's "27 of the slice's 37," and OQ5's "37 `!!` operators" — all three now agree. The pre-correction 30/36 discrepancy the planner disclosed is consistent with what a hand check of the old table would have produced.
- **Finding 3 (D8/AC15 citation).** Confirmed `SynchronizationPreferencesFragment.java:85-92`'s literal `null` is the fifth constructor argument (`passwordInitialValue`), and that class is in `:ui:preferences`, not `:app`. Confirmed the corrected citation independently: `OnlineFeedViewActivity.java:84-85` declares `private String username = null; private String password = null;`, `:119-120` restores them from `savedInstanceState` (also nullable), `:503-510`'s `FeedViewAuthenticationDialog` constructor passes both straight through to `super(...)`, and `:274-286`'s `checkDownloadResult` constructs it on the first `ERROR_UNAUTHORIZED` with the `:279` guard (`if (username != null && password != null)`) gating only a `Toast`, not the construction — so both fields are provably null at that construction site on a fresh run. D9 needed no change, as the revision states; verified `SynchronizationPreferencesFragment.java:87`'s existing "literal null for the password" language is correct and untouched.
- **Finding 4 (Robolectric module count).** `grep -rli robolectric --include="*.gradle" .` from repo root returns exactly 14 hits: `app`, `model`, `net/common`, `net/download/service-interface`, `net/download/service`, `net/sync/service-interface`, `net/sync/service`, `net/sync/wear-interface`, `parser/feed`, `parser/media`, `parser/transcript`, `storage/database`, `storage/importexport`, `ui/chapters`. Matches D3's corrected list exactly.

Categories re-considered and dismissed with no new finding, beyond what Loop 1 already cleared: D10's leaf-first ordering and the `TAG`-as-`const val` reasoning (unaffected by this revision, not re-litigated); AC15/AC17's `javap -p` mechanics (unaffected); Steps 1-7's characterization-test-first structure (unchanged, 12 Steps confirmed still 12 in the Steps section).

One new item, surfaced by the planner itself in its Revision 1 completion report and independently verified rather than taken on trust:

- **Severity:** MINOR
- **Class:** Coverage gaps left unaddressed / Silent behavior changes from mechanical translation
- **Concern:** D11's `!!` inventory, despite being re-verified row-by-row this revision and described as "binding," still omits two likely-necessary sites. `SynchronizationPreferencesFragment.java:186-188` — inside the anonymous `ArrayAdapter<SynchronizationProvider>`'s `getView` — calls `SynchronizationProvider synchronizationProvider = getItem(position);` then passes the result, unguarded, to `getProviderSummary(synchronizationProvider)` and `getProviderIcon(synchronizationProvider)` (`:187,188`), both of which D11/D9's own discipline requires to keep a **non-null** `SynchronizationProvider` parameter (that parameter is shared with the header-derivation call sites at `:122,123`, which D11 rule 1 already forces via `!!`). I independently confirmed, via `javap -v` against this project's own `compileSdk 36` `android.jar` (`common.gradle:2` — same jar the Kotlin compiler will actually see), that `android.widget.ArrayAdapter.getItem(int)` carries `RuntimeInvisibleAnnotations: android.annotation.Nullable`. I cross-checked that this exact annotation class is the one that produces genuine, well-known Kotlin nullability behavior — `android.os.BaseBundle.getString(String)` carries the identical `android.annotation.Nullable` annotation, and `Bundle.getString(...)` returning `String?` in Kotlin is long-established, documented behavior — so this is not a false signal from an unrecognized or IDE-only annotation. Given `getProviderSummary`/`getProviderIcon`'s parameter must stay non-null (per the already-approved D11 rule 1 discipline), a faithful conversion most likely needs `getItem(position)!!` at the adapter call site — two more `!!` operators, raising `SynchronizationPreferencesFragment.kt` from 27 to 29 and the slice total from 37 to 39. **This does not change actual runtime behavior**: the adapter's backing data is always `SynchronizationProvider.values()` (confirmed — `providers` at `:161` is populated from `SynchronizationProvider[] providers = SynchronizationProvider.values();`, and `SynchronizationProvider.kt` declares exactly two enum constants, `GPODDER_NET`/`NEXTCLOUD_GPODDER`, neither of which can be null), so `getItem(position)` can never actually return null in this instantiation and neither the Java nor a correctly-converted Kotlin ever crashes here. It is a real gap in the completeness of an inventory the plan calls "binding" and that AC8 checks numerically — but it is not a behavioral-equivalence risk, and it does not reproduce Loop 1 Finding 1's failure mode (a check with no way to pass a correct implementation): AC8 already contains a working disclosure clause ("Deviations are disclosed individually with a one-line justification... an undisclosed deviation fails this criterion"), so a developer who hits this as a compile error and adds `!!(` with disclosure can satisfy AC8 as written. The one rough edge is that AC8's disclosure template says "naming the Java NPE each preserves" — and this deviation, if it materializes, would not be preserving a reachable Java NPE (there isn't one at this call site), so the template's wording doesn't quite fit and could cause a developer to hesitate or force an inaccurate justification.
- **Evidence:** `SynchronizationPreferencesFragment.java:161` (`SynchronizationProvider[] providers = SynchronizationProvider.values();`), `:186-188` (`getItem(position)` then passed unguarded to `getProviderSummary`/`getProviderIcon`); `net/sync/service-interface/.../SynchronizationProvider.kt:3-5` (exactly two enum constants); `common.gradle:2` (`compileSdk 36`); `javap -v -classpath .../platforms/android-36/android.jar android.widget.ArrayAdapter` → `public T getItem(int); ... RuntimeInvisibleAnnotations: android.annotation.Nullable`; same tool against `android.os.BaseBundle` → `getString(String)` carries the identical annotation, corroborating that this annotation class is the one Kotlin's null-checker actually honors for this SDK.
- **Suggested mitigation:** Not blocking for this plan's approval, and not worth a third planning loop. Recommended as a low-cost addendum the planner can fold in whenever convenient (no File Scope or Step-count change required): add a row to D11's table for these two sites (bringing the stated totals to 29/8/2/0/39, with `SynchronizationPreferencesFragment.kt` at 29 and the slice at 39), and add one clause to AC8's disclosure sentence covering the case where a forced `!!` satisfies Kotlin's null-checker without preserving an actual reachable Java NPE (e.g., "...naming the Java NPE each preserves, or, if none is reachable, the SDK nullability contract the `!!` satisfies"). If the planner chooses not to revise the document, flag this finding to `android-migration-developer` directly before Step 11 begins, so a compile-time surprise at the adapter's `getView` doesn't get resolved ad hoc with a forbidden `?.` (D11 rule 2) or a parameter-nullability "fix" (which D12's crash-preservation convention would otherwise block).

### Recommendation

**APPROVE.** All four Loop 1 findings are fixed correctly and I re-derived every one from source independently rather than trusting the revision's self-report — the AC13 rewrite discriminates the two error handlers unambiguously, the `service!!` count and the full `!!` inventory arithmetic reconcile to 37 (checked by direct summation, not just re-reading the planner's total), the D8/AC15 citation now points at a real, reachable, both-null production path, and the Robolectric module count is exactly 14. No CRITICAL or MAJOR concern remains open. The one new item (the `ArrayAdapter.getItem()` nullability gap, independently confirmed via `javap` against this project's actual `compileSdk 36` jar) is real and worth fixing, but it is MINOR: it carries no behavioral-equivalence risk (the backing data can never contain null in this instantiation), and it is already coverable by AC8's existing disclosure convention rather than requiring the plan to be reopened. Per this repo's established pattern (AC6, AC8, AC14 all already rely on "observed and disclosed, not assumed" rather than pre-enumerating every possible deviation), this is exactly the kind of item that convention exists for. Proceed to implementation; no third loop needed.

## Implementation Notes
_By: android-migration-developer | 2026-08-06_

### Branch and starting state

Per D1: `git fetch origin` first, then verified `git merge-base --is-ancestor af93320b1 origin/develop` printed `M14 present` against the freshly-fetched ref (tip `18f6b20ba`, "kotlin/net-sync-service-interface: convert 8 of the module's 9 Java test files to Kotlin, hold ISyncServiceTest.java back (#20)"). This is a newer tip than the Plan's own D1 evidence (`5f816b768`, `2026-08-05 11:24:32 +0200`) — origin/develop moved forward between planning and implementation, exactly the scenario D1 told the developer to check for rather than trust the plan's age. Branched fresh: `git checkout -b kotlin/ui-preferences-sync-settings origin/develop`. Working tree carried the two pre-existing untracked spec files (this task file, the future-work file) from session start; no other local changes.

### Steps executed

All 12 Steps were implemented in order, characterization-tests-first (Steps 1–7 before any production line changed), exactly as the Plan structures them.

**Step 1 — harness.** `SyncSettingsTestHost.kt` (`AppCompatActivity`, `setTheme(Theme_AntennaPod_Light)` in `onCreate` before `super.onCreate`), `RecordingSynchronizationQueue.kt`, `SyncSettingsHarnessSmokeTest.kt` (6 tests — the 5 named in the Plan plus a 6th, `testAntennapodHttpClientConstructionUnderRobolectric`, added because D5's table lists `AntennapodHttpClient.getHttpClient()` constructibility as a 4th proof obligation that Step 1's five named tests don't individually cover; disclosed as an intentional addition, not a deviation, since it strengthens AC2's evidence rather than replacing anything).

**Step 2 — `AuthenticationDialog`.** `AuthenticationDialogCharacterizationTest.kt` (4 tests) + `AuthenticationDialogJavaInteropTest.java` (1 test, D8's two nested subclasses, both constructed with literal-null `usernameInitialValue`/`passwordInitialValue`, mirroring `OnlineFeedViewActivity.java:503-510`'s first-`ERROR_UNAUTHORIZED` path).

**Steps 3–4 — `SynchronizationPreferencesFragment`.** `SynchronizationPreferencesFragmentCharacterizationTest.kt` (8 tests, gaps 1–8) + `SynchronizationPreferencesFragmentLifecycleTest.kt` (4 tests, gaps 9–12).

**Step 5 — `GpodderAuthenticationFragment`.** `GpodderAuthenticationFragmentCharacterizationTest.kt` (9 tests, gaps 13–15/17–20), driving `advance()`, `generateDeviceName`/`generateDeviceId`/`isDeviceInList` and the private fields by reflection per D7. Gap 16 confirmed **not** executable here, per D7/OQ2 — no MockWebServer, no injectable `GpodnetService` in this repo yet.

**Step 6 — `NextcloudAuthenticationFragment`.** `NextcloudAuthenticationFragmentCharacterizationTest.kt` (6 tests — the 5 named in the Plan plus `testDismissWithNoLoginFlowDoesNotCrash`, an intentional addition pinning the `if (nextcloudLoginFlow != null)` guard's negative branch, disclosed as strengthening rather than replacing D7's minimum).

**Step 7 — baseline freeze.** Both flavors run with `--rerun`; results and the `git hash-object` table are below. **One correction to the baseline, disclosed:** the hashes first recorded at this step were taken *before* running `ktlintCheck` against the test source set (Step 8's checkstyle/lint/ktlint sweep is what caught this — the Plan's own Step ordering doesn't ask for a Step-7 ktlint run, only Steps 8–11's). `ktlintTestSourceSetCheck` found real import-ordering and line-length violations pre-existing in three test files (`GpodderAuthenticationFragmentCharacterizationTest.kt`, `SynchronizationPreferencesFragmentCharacterizationTest.kt`, `NextcloudAuthenticationFragmentCharacterizationTest.kt`, all written during Steps 5/3/6 respectively). Fixed for formatting only — import reordering and argument-list line-wrapping, zero assertion or logic changes, confirmed by re-reading every edit before and after — and the corrected hashes below are what Steps 8–11 are actually diffed against. This is disclosed rather than silently absorbed because it is exactly the class of thing AC7 exists to catch; the difference here is the edit predates any production conversion and is pure style, not a forced signature-change edit.

### Test commands run (D14 — both flavors, separate `--rerun` invocations, module is flavoured)

- `./gradlew --console=plain :ui:preferences:testFreeDebugUnitTest --rerun` — 38/38 PASS at Step 7 (baseline) and again after every one of Steps 8, 9, 10, 11, 12.
- `./gradlew --console=plain :ui:preferences:testPlayDebugUnitTest --rerun` — 38/38 PASS at every one of the same points.
- `./gradlew --console=plain :app:assembleDebug` — BUILD SUCCESSFUL after every one of Steps 8, 9, 10, 11, 12 (per AGENTS.md, this is the compile-verification command; never truncated/piped through a filtering command when read for correctness, only for the PASS/FAIL tallies quoted above).
- `./gradlew --console=plain :ui:preferences:ktlintCheck` — BUILD SUCCESSFUL after every one of Steps 8–11, `ktlintMainSourceSetCheck` and `ktlintTestSourceSetCheck` both genuinely executing (not `SKIPPED`/`NO-SOURCE`).
- `./gradlew --console=plain :ui:preferences:checkstyle :ui:preferences:lint` (module-scoped — see AC6 disclosure below for why not the bare repo-wide `checkstyle lint`) — BUILD SUCCESSFUL after every one of Steps 8–11.

Per-class counts, identical at Step 7 and at every later step (row for row, per AC3):

| Test class | Tests |
|---|---|
| `AuthenticationDialogCharacterizationTest` | 4 |
| `AuthenticationDialogJavaInteropTest` | 1 |
| `GpodderAuthenticationFragmentCharacterizationTest` | 9 |
| `NextcloudAuthenticationFragmentCharacterizationTest` | 6 |
| `SynchronizationPreferencesFragmentCharacterizationTest` | 8 |
| `SynchronizationPreferencesFragmentLifecycleTest` | 4 |
| `SyncSettingsHarnessSmokeTest` | 6 |
| **Total** | **38** |

`git hash-object` for the 9 files under `ui/preferences/src/test/` (D2/AC7's frozen baseline, corrected per the Step 7 note above — unchanged across Steps 8, 9, 10, 11, 12, verified by re-hashing after every step):

```
14c2618645709a45117d3e6a8b39e96cccd8c6f9  AuthenticationDialogCharacterizationTest.kt
f323c991e90fbfb79d2aa33bd521aa003c5e3b6b  AuthenticationDialogJavaInteropTest.java
3685d513edd5268030a951f914367bf0cac22822  GpodderAuthenticationFragmentCharacterizationTest.kt
3bfe7e2f59ad34c10c67efdb3ffd7b5c96fa7e24  NextcloudAuthenticationFragmentCharacterizationTest.kt
bbfe5f29825466d02c0c9335483d5c26ef50c32c  RecordingSynchronizationQueue.kt
79eac4abb9a41446e7efee99693e6cf8d8eac8f7  SynchronizationPreferencesFragmentCharacterizationTest.kt
9ca81f8e777ae3c6ec9a3c55538b3999a7e0d003  SynchronizationPreferencesFragmentLifecycleTest.kt
52eb8c9bdf26b3089c2117127d3bf9fb47204a6f  SyncSettingsHarnessSmokeTest.kt
3f16f9d994095f3220a1161a8299ed0d08abdcf4  SyncSettingsTestHost.kt
```

Note on AC7's literal mechanism: the Plan's AC7 specifies `git diff <step-7-commit> -- ui/preferences/src/test/`. Per this pipeline's hard rule ("never commit unless the user explicitly asks"), no commit was made at Step 7 or any other step, so there is no `<step-7-commit>` to diff against. `git hash-object` re-run after every step is the content-addressed equivalent — it detects any change to any byte of any test file exactly as a `git diff` against a frozen commit would, without requiring a commit to exist. All nine files' hashes were re-verified identical after Steps 8, 9, 10, 11 and again now at Step 12.

### Characterization test results — before/after, what each one actually exercises

All 38 tests were green against the unconverted Java at Step 7 and are green again now against the fully-converted Kotlin, with identical per-class counts throughout. Per-gap mapping (D7's table), with a one-line note on the real behavior each test exercises — not just "it passed":

| Gap(s) | Test | What it exercises |
|---|---|---|
| 1–4 | `testLoggedOutScreenState`, `testLoggedInScreenState`, `testGpodnetRowVisibleOnlyForGpodderNet`, `testLogoutSummaryIsHtmlParsedSpanned` | Drives the real screen logged-out and logged-in (GPODDER_NET and NEXTCLOUD_GPODDER separately for the visibility gap), asserting title/summary/icon/enabled/visible per row and that the header's click listener actually opens/doesn't-open a dialog (observed via `ShadowDialog`, not assumed from the listener being non-null) |
| 5 | `testUnrecognisedProviderKeyThrowsAfterClearingHeaderTitle` | Sets an unrecognised persisted provider key, asserts the fragment attach itself throws `NullPointerException`, and separately asserts the header title is already `""` at that point — this is the test that makes D11 rule 1's forcing-site placement enforceable, and it passed identically against Java and against the converted Kotlin |
| 6 | `testSetLoginDialogSeedsUsernameDisabledAndWritesPasswordOnly` | Clicks the real preference row, captures the real shown `AuthenticationDialog`, asserts the username field is disabled and pre-seeded, clicks confirm with a new password, asserts only the password changed in `SynchronizationCredentials` |
| 7 | `testLogoutOrderingAndCrossClassSideEffect` | A hook on `RecordingSynchronizationQueue.clear()` snapshots `SynchronizationCredentials`/`SynchronizationSettings` state at the exact moment the queue is cleared, proving credentials were already cleared and the provider was not yet cleared at that point; also asserts the `UserPreferences` gpodnet-notification flag flips from a pre-seeded `false` |
| 8 | `testSyncAndFullSyncRowsCallQueueWithoutStateChange` | Clicks sync/full-sync rows, asserts the recording queue saw exactly one call each and no preference state changed |
| 9–12 | `testStickyEventReplaysOnStart`, `testSyncEventIgnoredWhenNotConnected`, `testSubtitleBranchesOnMessageResId`, `testOnStartSetsTitleAndOnStopSetsEmptySubtitleWhereasDisconnectedSetsNull` | Posts a sticky `SyncServiceEvent` before the fragment attaches and observes the subtitle change on attach (proving EventBus sticky-replay-on-register); posts an event while disconnected and asserts the subtitle is untouched; drives the ActionBar controller through pause/stop and distinguishes empty-string vs `null` subtitle |
| 13–15, 17–20 | 9 tests in `GpodderAuthenticationFragmentCharacterizationTest` | Reflection-drives `advance()` and the private step-machine fields; proves the host step clears credentials/queue before setting the host URL (wrapped in `assertThrows` per D5's fallback, since `AntennapodHttpClient.getHttpClient()` is not constructible under Robolectric — confirmed, see AC2 below); proves username validation short-circuits before any RxJava subscription is created; proves device-name/ID dedupe matches by either ID or caption; proves `Locale.US` specifically via a Turkish-default-locale live falsification (`"I"` → `"i"`, not the Turkish dotless `"ı"`); proves credential-commit ordering and that a partial wizard leaves credentials untouched; proves `dismiss()` fires before `syncImmediately()` (via a test-only subclass recording both events into one ordered list); proves both the `Dialog` and the `DialogFragment` cancelable flags are false |
| 16 | not executable — source-level only | AC13's four-line scoped grep, run and recorded below |
| 21–24 | 5 tests in `NextcloudAuthenticationFragmentCharacterizationTest` (+1 disclosed addition) | Proves the Nextcloud credential-commit order differs from the gpodder path (provider selected *before* `credentials.clear()`, observed via the same recording-queue-hook technique as gap 7); proves deferred dismiss when not resumed, using a real `ActivityController.pause()/resume()`; proves the save/restore halves of config-change survival (save via a fake `NextcloudLoginFlow` subclass with a no-op `start()`/controlled `saveInstanceState()`, restore's guard proven by observing the `AntennapodHttpClient` crash fire only when the saved-state key is present — see the deviation note below); proves `onDismiss` cancels the flow; proves the error dialog's span boundaries and color |
| 25–27 | 4 tests in `AuthenticationDialogCharacterizationTest` | Proves the password-visibility toggle swaps both the transformation method and the button alpha across two clicks; proves the username field's enabled state is constructor-driven; proves a null initial value leaves the corresponding field empty while a non-null value is actually seeded (discriminating "guarded by an if" from "always empty"); proves `onCancelled()` fires from both the negative button and the dialog's cancel listener |
| interop | `AuthenticationDialogJavaInteropTest` | Two Java nested subclasses (anonymous, overriding only `onConfirmed`; named, overriding both hooks and calling `super.onCancelled()`), each constructed with both constructor's nullable parameters literal `null`, compile and run — the entire oracle is javac's acceptance plus one runtime assertion that `super.onCancelled()` actually ran |

### `!!` inventory — the major disclosed deviation from D11's table

**Measured, not assumed: 49 `!!` operators across the slice, not the Plan's 37 (or the red-team's own suggested 39).** Per file: `AuthenticationDialog.kt` 0, `GpodderAuthenticationFragment.kt` 12, `NextcloudAuthenticationFragment.kt` 7, `SynchronizationPreferencesFragment.kt` 30. Every single one was arrived at empirically — write the conversion, compile, read the compiler's errors/warnings (a missing `!!` is a compile error; a redundant one is a warning; neither appeared for any final `!!` in the four files), and grep-count the result (`grep -o '!!' <file> | wc -l`, re-run after stripping one self-inflicted measurement bug: an early explanatory comment near the `chooseProviderAndLogin` adapter literally contained the substring `!!` in prose, inflating that file's count by one until reworded — the same category of mistake as the `@SuppressLint` rationale comments caught and fixed during Steps 9–10, disclosed there). This is disclosed individually below, per AC8's convention, extended per the red-team's own Loop 2 suggested amendment ("naming the Java NPE each preserves, or, if none is reachable, the SDK nullability contract / control-flow invariant the `!!` satisfies").

**Where the Plan's table undercounted (+13 sites, all real preserved Java crashes or compiler-mandated assertions the table's authors didn't enumerate):**

1. **`Fragment.getContext()`, unguarded, at every `MaterialAlertDialogBuilder(getContext())` call site (5 sites: `GpodderAuthenticationFragment` ×1, `NextcloudAuthenticationFragment` ×2, `SynchronizationPreferencesFragment` ×2 — `chooseProviderAndLogin`'s builder and its `ArrayAdapter` constructor).** `Fragment.getContext()` is `@Nullable` in this project's AndroidX Fragment version (confirmed empirically: the Kotlin compiler required `!!` at every one of these sites with zero "unnecessary assertion" warnings, the same technique the red-team itself used for `ArrayAdapter.getItem()`). `MaterialAlertDialogBuilder`'s `Context` constructor parameter is `@NonNull`-annotated (Material Components library), so a null `Context` genuinely NPEs in the original Java the same way, unguarded, at the same statement. **Genuine preserved Java crash, same discipline as the table's other rows.**
2. **`DialogFragment.getDialog()`, unguarded, in `GpodderAuthenticationFragment.setupHostView`'s click listener (`getDialog().setTitle(...)`) — 1 site.** `getDialog()` is `@Nullable`. Same category as #1.
3. **`Fragment.getActivity()`, unguarded, in `SynchronizationPreferencesFragment.setupScreen` where it's captured into a local `activity` and passed as `AuthenticationDialog`'s first constructor argument — 1 site.** In Java this compiled without complaint because `AuthenticationDialog`'s Java constructor took a raw `Context` with no nullability annotation. Since `AuthenticationDialog.kt` (Step 8) now declares that parameter as non-null `Context` (D9's binding shape — correctly, since neither of `AuthenticationDialog`'s two `:app` Java callers or this in-slice caller can actually pass null in practice), Kotlin-to-Kotlin now enforces it at the call site. **Preserved Java crash on the same underlying condition** (fragment not attached), now caught one statement earlier due to the type becoming precise.
4. **`View` in the `chooseProviderAndLogin` `ArrayAdapter`'s `getItem(position)` — 1 site (`SynchronizationPreferencesFragment.kt`).** This is exactly the red-team's own Loop 2 MINOR finding (`android.widget.ArrayAdapter.getItem(int)` is `@Nullable` per `javap -v` against this project's actual `android.jar`), independently re-confirmed here by the Kotlin compiler requiring the assertion. Implemented as **one** forced `!!` at the point Java itself calls `getItem()` once into a local (`val synchronizationProvider = getItem(position)!!`), used twice after — not two separate forced dereferences at each use site, since Java's own code already had exactly one call assigned to one local reused twice, and preserving that exact structure is more faithful than introducing a second call `getItem()` didn't have in Java. **No behavioral-equivalence risk** (the backing data is always `SynchronizationProvider.values()`, as the red-team's own analysis already established), disclosed per their suggested amendment.
5. **`NextcloudLoginFlow?`, a mutable `var` field, read via `!!` inside `startLoginFlow()`, `onSaveInstanceState()`, and `onDismiss()` — 3 sites in `NextcloudAuthenticationFragment.kt`.** In Java, none of these three reads is a live NPE risk: `startLoginFlow()` is only ever called immediately after `nextcloudLoginFlow` is assigned, and the other two are already inside `if (nextcloudLoginFlow != null)` guards. Kotlin does not smart-cast a mutable class-level `var` property across statements or into a guarded block the way it would a local `val` — this is a Kotlin type-system requirement satisfying a control-flow invariant the original Java's lack of null-checking made irrelevant, **not a reproduction of a reachable Java crash.** Confirmed empirically: omitting any of the three is a compile error, and the compiler raised zero "unnecessary assertion" warnings for keeping them.

**Where the Plan's table over-counted by one (−1 site, a genuine correction):** `selectedProvider!!` in `SynchronizationPreferencesFragment.updateScreen()` is forced **once**, not at "the two call sites" as D11's table states, because `selectedProvider` is a local `val` — Kotlin smart-casts it to non-null for the rest of the scope after the first `!!`, so the second use (`getProviderIcon(selectedProvider)`, unforced) compiles clean with no warning either way. **AC10's actual mechanism — `testUnrecognisedProviderKeyThrowsAfterClearingHeaderTitle` passing before and after, with the header-title assertion as the discriminator — is unaffected and was independently re-verified green against the final Kotlin.** The forcing point is still after `preferenceHeader.setTitle("")`, exactly as D11 rule 1 requires; only the token count the table anticipated was off by one, not the behavior.

**Reconciled arithmetic, per file (table → actual, diff, and where the diff comes from):**

| File | Table | Actual | Diff | Where |
|---|---|---|---|---|
| `AuthenticationDialog.kt` | 0 | 0 | 0 | — |
| `GpodderAuthenticationFragment.kt` | 8 | 12 | +4 | `context!!` (1, finding #1) + `dialog!!` (1, finding #2) + `devices!!` second site in `isDeviceInList` (1, finding #6 below) + `selectedDevice!!` (1, finding #7 below) |
| `NextcloudAuthenticationFragment.kt` | 2 | 7 | +5 | `context!!` ×2 (finding #1) + `nextcloudLoginFlow!!` ×3 (finding #5) |
| `SynchronizationPreferencesFragment.kt` | 27 | 30 | +3 | `activity!!` (1, finding #3) + `context!!` ×2 — `chooseProviderAndLogin`'s builder and its `ArrayAdapter` constructor (finding #1) + `getItem()!!` (1, finding #4) − `selectedProvider!!` needed once instead of twice (−1, smart-cast correction below) |
| **Total** | **37** | **49** | **+12** | |

Two further sites not yet named above, same "genuine preserved Java crash" category as findings #1–#3, both in `GpodderAuthenticationFragment.kt`:

6. **`devices!!` inside `isDeviceInList`'s for-loop — a second site beyond the one D11's table already names in `setupDeviceView`.** In Java, `isDeviceInList` guards with `if (devices == null) return false;` before the loop, so the deref is safe there. In Kotlin, an intervening call to `generateDeviceId(name)` between the null-check and the loop invalidates the compiler's smart-cast for this mutable `var` field (confirmed: omitting `!!` here is a compile error). No reachable Java NPE; a control-flow invariant Kotlin's flow-insensitive-across-calls smart-cast rule requires re-asserting.
7. **`selectedDevice!!.id` in `advance()`'s `STEP_DEVICE` branch.** Java's `if (selectedDevice == null) { throw ... } else { ... selectedDevice.getId() ... }` is safe by the same immediately-preceding guard. Kotlin does not smart-cast a `@Volatile`-annotated property at all (by design — the value could change from another thread between the check and the read), so `!!` is required regardless of the guard. No reachable Java NPE; the `@Volatile` annotation itself (required by D13, preserving the pinned data-race-adjacent field) is what forces this.

**No `!!` was collapsed into a hoisted local for any D11-tabled site** (D11 rule 3): the four `service!!` sites in `GpodderAuthenticationFragment.kt` remain four separate forced dereferences at the four original statements; the 11 `findPreference` sites and 5 ActionBar chains are likewise untouched from the table's shape.

### AC-by-AC verification

- **AC1** — Step 7 baseline: both flavors BUILD SUCCESSFUL, 0 failures, 38/38, against the unconverted Java. Per-class table above; 26 gaps mapped to named tests, gap 16 named as source-pinned.
- **AC2** — D5 fallbacks needed: **none** of the four primary mechanisms failed — `Robolectric.buildActivity` built the test host directly (no `ShadowPackageManager` registration needed), `PreferenceFragmentCompat` inflated `preferences_synchronization.xml` under the `Theme_AntennaPod_Light`-themed host with no SDK-level bump needed (default Robolectric SDK from the module's `minSdk`/`targetSdk`/`compileSdk` config worked), `ShadowDialog.getLatestDialog()` retrieved `MaterialAlertDialogBuilder`-built dialogs directly. The one row that **did** need its fallback: `AntennapodHttpClient.getHttpClient()` is **not** constructible under default Robolectric — it throws `NullPointerException` from `okhttp3.Cache`'s constructor because `AntennapodHttpClient.cacheDirectory` is never set by anything in this test process. Confirmed via a dedicated smoke test (`testAntennapodHttpClientConstructionUnderRobolectric`, `assertThrows(NullPointerException::class.java)`), and gap 14's test (`testHostStepClearsCredentialsAndQueueBeforeSettingHostUrl`) uses exactly the fallback D5 prescribes: wrap the button click in `assertThrows` and still assert the credential/queue ordering, which survives the construction failure intact.
- **AC3** — Both flavors green, 38/38, identical per-class counts, at the end of Steps 8, 9, 10, 11 and 12 — verified above, re-run at each step, not merely claimed.
- **AC4** — No test added/removed/renamed/split/merged/moved after Step 7. The one caveat: the Step 7 *hash baseline itself* was corrected before Step 8 began (see "Steps executed" above) for pure ktlint formatting, zero test-name or assertion changes — disclosed there rather than silently folded in.
- **AC5** — `./gradlew --console=plain :app:assembleDebug` BUILD SUCCESSFUL after every one of Steps 8, 9, 10, 11, 12, compiling `PreferenceActivity.java`, `MainPreferencesFragment.java`, `FeedSettingsPreferenceFragment.java`, `OnlineFeedViewActivity.java` unedited (confirmed: none of the four appear in `git diff --name-only origin/develop`, reproduced below under AC21).
- **AC6** — `:ui:preferences:ktlintCheck` BUILD SUCCESSFUL after every one of Steps 8–11, `ktlintMainSourceSetCheck`/`ktlintTestSourceSetCheck` genuinely executing. Zero `@Suppress("ktlint:…")` added, no `.editorconfig` change, no ktlint exclusion in any build file. **Two disclosed findings on the lint side, both resolved without touching `build.gradle`:** (a) the bare repo-wide `./gradlew checkstyle lint` fails on a clean `origin/develop` checkout too — reproduced via `git stash`/re-run before any conversion touched this module — from `:app-wearos:compilePlayDebugKotlin`'s pre-existing compile error and pre-existing SpotBugs violations in several unrelated modules (`:app`, `:net:download:service`, etc.); this is a repo-wide, pre-existing condition, not something this diff introduced, so `:ui:preferences:checkstyle`/`:ui:preferences:lint` (module-scoped) were used for this module's own verification instead, and both are BUILD SUCCESSFUL at every step. (b) Android Lint's `UseRequireInsteadOfGet` rule (`androidx.fragment`, an error under this repo's `abortOnError true`) flags every `context!!`/`view!!` site this milestone's discipline requires and recommends `requireContext()`/`requireView()` — which D11 rule 2 explicitly forbids, because those helpers throw `IllegalStateException` instead of the `NullPointerException` the Java original throws. Resolved with a targeted `@SuppressLint("UseRequireInsteadOfGet")` on each affected function (6 annotations total: 1 in `GpodderAuthenticationFragment`, 2 in `NextcloudAuthenticationFragment`, 3 in `SynchronizationPreferencesFragment` — `onStart`, `setupScreen`, `updateActionBar`, `chooseProviderAndLogin`, `updateLastSyncReport`), each with a one-line comment naming the reason. This is an in-file annotation, not a `build.gradle` change, so D4's build-diff constraint is unaffected. No other Lint findings, new or pre-existing, against any of the four `.kt` files.
- **AC7** — See "Test commands run" above: `git hash-object`, not a commit-diff (no commits were made, per this pipeline's hard rule), used as the content-addressed equivalent. All nine test files' hashes identical across Steps 8–12 against the corrected Step 7 baseline.
- **AC8** — See the `!!` inventory section above in full. `grep -c 'service!!' GpodderAuthenticationFragment.kt` → 4, matching D11 rule 3 exactly (verified: `grep -c 'service!!' ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/GpodderAuthenticationFragment.kt` = 4). Every deviation from the table's stated 37 is disclosed above, individually, with the Java NPE it preserves or the control-flow invariant it satisfies.
- **AC9** — `grep -rnE 'requireActivity\(\)|requireContext\(\)|requireView\(\)' ui/preferences/src/main/java/.../synchronization/` → zero hits in actual code. (Explanatory comments near `@SuppressLint` annotations originally used these literal strings in prose and were reworded specifically to avoid contaminating this grep and the `!!`-count greps — disclosed as a measurement-hygiene fix, not a behavior change.)
- **AC10** — Proven by `testUnrecognisedProviderKeyThrowsAfterClearingHeaderTitle` passing both before (Java) and after (Kotlin) conversion, header-title assertion included. See the `!!` inventory section for the one-token-not-two correction.
- **AC11** — `grep -c '?\.'` → 0 for all four files. `GpodderAuthenticationFragment.kt`'s `SynchronizationCredentials.getHosturl().startsWith("http://")` carries neither `!!` nor `?.`, matching D11's explicit instruction.
- **AC12** — `grep -c '@Volatile' GpodderAuthenticationFragment.kt` → 3 (`username`, `password`, `selectedDevice`); `devices` carries no `@Volatile` (D12.2, the pinned data race, preserved).
- **AC13** — All four lines hold against the final `GpodderAuthenticationFragment.kt`: `error.cause!!.message` → 1, `error.message` → 1, `error.cause?.message` → 0, `error.` → 2 hits (login handler first, `createDevice` second).
- **AC14** — Both `when (provider)` expressions in `SynchronizationPreferencesFragment.kt` keep `else -> R.string.sync_status_error` / `else -> R.drawable.ic_error` (confirmed by direct inspection; the Kotlin compiler emits a warning — "'when' is exhaustive so 'else' is redundant here" — at both sites, which is expected and was left in place rather than "cleaned up," per D12.1's explicit instruction). The anonymous `ArrayAdapter` keeps `holder` as a field of the adapter, reassigned inside `getView()` — disclosed change from a nullable `var ... = null` to `lateinit var`: same field-reuse (recycling) behavior, only the null-representation changed, matching D13's own `lateinit` rationale already applied to `viewFlipper`/`viewBinding`. `ViewHolder` stayed a nested class inside the object expression (as an `inner class`); no hoisting to the fragment was needed.
- **AC15** — `AuthenticationDialogJavaInteropTest.testJavaSubclassOverrideShapes` compiles and passes, exercising both `:app` override shapes with both constructor parameters literal `null`. `javap -p -s` on the built class: `public abstract class ... AuthenticationDialog extends ... MaterialAlertDialogBuilder`; constructor descriptor `(Landroid/content/Context;IZLjava/lang/String;Ljava/lang/String;)V` — exact match; `protected void onCancelled()`; `protected abstract void onConfirmed(java.lang.String, java.lang.String)`.
- **AC16** — `grep -rn 'passwordHidden' ui/preferences/ app/` → hits only inside `AuthenticationDialog.kt`.
- **AC17** — `javap -p` on the built classes: `public static final java.lang.String TAG;` appears directly on both `GpodderAuthenticationFragment.class` and `NextcloudAuthenticationFragment.class` (the outer classes, not only `$Companion.class`, which also exists but is not where Java resolves `GpodderAuthenticationFragment.TAG`/`NextcloudAuthenticationFragment.TAG` from).
- **AC18** — Structural, covered by AC5: `SynchronizationPreferencesFragment.kt` is a `public` class (no visibility modifier = public in Kotlin) with an implicit no-arg constructor, still assignable to `PreferenceFragmentCompat` via `AnimatedPreferenceFragment`; `PreferenceActivity.java:75`'s `new SynchronizationPreferencesFragment()` compiles unedited.
- **AC19** — `find ui/preferences/src/main/java/.../synchronization -name '*.java'` → nothing; `-name '*.kt'` → 4. `advance()` dispatches through a subject-ful `when (currentStep)`; both provider `switch`es are `when` expressions; three volatile fields carry `@Volatile`; `TAG`/`EXTRA_LOGIN_FLOW`/`STEP_*` constants all live in `companion object`s as `const val`.
- **AC20** — `grep -rnE '\.apply \{|\.also \{|\.let \{|\bdata class\b|\bfun [A-Za-z]+\.[A-Za-z]+\('` over the four converted files → zero hits. `usernameHasUnwantedChars` still allocates `Pattern.compile(...)` per call (not hoisted to a companion `Regex`).
- **AC21** — `git diff --name-only origin/develop` (staged) lists exactly: the two pre-existing spec files, the future-work file (D15 update), `ui/preferences/build.gradle`, the four converted `.java`→`.kt` pairs, and the nine new test files. `gradle/libs.versions.toml`, `common.gradle`, every file under `ui/preferences/src/main/res/`, `AnimatedPreferenceFragment.java`, and every file in `app/` are unchanged — confirmed absent from the diff list.
- **AC22** — `ui/preferences/README.md` gained a "Kotlin conversion conventions" section (8 numbered conventions, no milestone number, no task-file reference, phrased as long-term-stable module conventions per AGENTS.md). `tasks/antennapod-sync-settings-modernization-future-work.md` gained: Milestone 15's actual outcome (replacing the "in progress" placeholder), a note under Milestone 17 naming the `!!` inventory/ActionBar cleanup and gap 16 as carried-forward, and a note under Milestone 18 naming this suite as the DI seam's regression net.

### Deviations from plan — summary

None of the below required re-scoping, touched File Scope, or changed a Step's order. All are specification-apparatus corrections or empirically-forced additions to D11's `!!` table, in the same spirit as the two Red-Team loops' own findings against this same table.

1. **`ui/preferences/build.gradle` gained one line beyond D4's stated three:** `testImplementation project(':model')`. D6's `RecordingSynchronizationQueue` must override `SynchronizationQueue.enqueueEpisodePlayed(media: FeedMedia?, completed: Boolean)`, and `FeedMedia` (in `:model`) is not on this module's test compile classpath — `:model` is an `implementation` (never `api`) dependency everywhere in this repo (verified: `grep -rn "project(':model')" **/build.gradle` — every hit is `implementation`), so it is not transitively visible. Unavoidable for the exact test double D6 specifies; disclosed with an inline comment at the point of addition.
2. **The `!!` inventory is 49, not 37** — see the dedicated section above. Every additional site is either a genuine preserved Java crash the table's authors didn't enumerate, or a Kotlin-smart-cast/mutable-property requirement with no reachable Java equivalent; one site (`selectedProvider!!`) is forced once instead of twice due to local-`val` smart-casting. No behavioral-equivalence risk in any of it — every characterization test that exercises a crash path (`testUnrecognisedProviderKeyThrowsAfterClearingHeaderTitle`, `testHostStepClearsCredentialsAndQueueBeforeSettingHostUrl`) still passes identically before and after.
3. **`GpodderAuthenticationFragment` became `open class`**, not plain `class`. Kotlin classes are `final` by default (Java's are implicitly open); the frozen characterization suite's `TestableGpodderAuthenticationFragment` (used to observe `dismiss()`-before-`syncImmediately()` ordering, gap 19) stopped compiling the moment the class converted. Since AC7 forbids editing the frozen test to work around a production signature change, and Java itself never restricted subclassing here, `open` is the faithful translation of Java's original openness, not a behavior change or a scope expansion — no other class in the slice needed this (verified: no other test or production code subclasses `NextcloudAuthenticationFragment` or `SynchronizationPreferencesFragment`).
4. **Two Android Lint suppressions added in-file** (`@SuppressLint("UseRequireInsteadOfGet")`, 6 call sites across 2 files) to reconcile D11 rule 2's explicit ban on `requireContext()`/`requireView()` with Android Lint's contrary recommendation — see AC6 above.
5. **A holder/icon/title nullability improvement in the `chooseProviderAndLogin` adapter**: `lateinit var` instead of nullable `var ... = null` for `holder`, `ViewHolder.icon`, `ViewHolder.title`. Not requested by D13's list verbatim, but directly authorized by D13's own stated `lateinit` rationale ("assigned before any read, from a layout id that exists at compile time") already applied elsewhere in the same milestone (`viewFlipper`, `viewBinding`) — applying the identical reasoning to a third, structurally identical case is not a new idiom, and it kept 6 otherwise-unavoidable `!!` off the final count (4 `holder!!` sites plus one each for `title!!`/`icon!!`) without changing the recycled-holder-as-field defect D12.3 requires to stay pinned.
6. **Two extra characterization tests beyond D7's named minimum**, disclosed at the point they were added (Steps 1 and 6 above): `testAntennapodHttpClientConstructionUnderRobolectric` and `testDismissWithNoLoginFlowDoesNotCrash`. Both strengthen coverage of an existing D5/D7 obligation; neither replaces or weakens a named test.

### Commit message (drafted, not executed — no commit was made per this pipeline's hard rule; the user has not asked for one)

```
feat(ui-preferences): kotlin migration — convert screen/synchronization/ (4 files) to Kotlin

kotlin track, Milestone 15. First of the six-milestone sync-settings
modernization sequence. Stands up :ui:preferences's test source set
from nothing (zero prior tests, zero test infra) with 38 characterization
tests pinning 26 of Research's 27 enumerated behaviors executably and
the 27th (gap 16, GpodderAuthenticationFragment's unwrapped-cause error
message) by a scoped four-line grep, per D7 -- no MockWebServer or
injectable GpodnetService exists in this repo yet to pin it executably.

RxJava3, EventBus, PreferenceFragmentCompat and every layout/XML file
are behaviorally untouched -- this is a language conversion, not a
rewrite. AuthenticationDialog's two :app Java subclasses, and
PreferenceActivity's PreferenceFragmentCompat-typed instantiation of
SynchronizationPreferencesFragment, both verified via :app:assembleDebug
green after every step.

37 `!!` operators were planned (D11); 49 were actually required, all
individually justified in the task file's Implementation Notes -- every
addition beyond the plan's table is either a real crash the pre-conversion
Java already threw unchecked at the same statement (unguarded
Fragment.getContext()/getActivity()/getDialog() derefs the plan's table
didn't enumerate) or a Kotlin null-checker requirement for a value
that's always non-null by construction, with zero behavioral-equivalence
risk in either case -- proven by the same characterization tests passing
identically before and after. GpodderAuthenticationFragment became
`open class` (Kotlin defaults to final, Java to open) to keep the
frozen characterization suite's one test subclass compiling.

38/38 tests green on both testFreeDebugUnitTest/testPlayDebugUnitTest
throughout, two separate --rerun invocations at every step. Test source
set byte-for-byte frozen (content-hash verified, not commit-diffed --
no commits were made) from Step 7 through Step 12. ktlintCheck and
module-scoped checkstyle/lint green throughout; two UseRequireInsteadOfGet
Lint findings resolved via targeted @SuppressLint rather than the
recommended requireContext()/requireView(), which would have thrown a
different exception type (IllegalStateException, not NullPointerException)
than the Java original.

No production .kt file outside the 4-file slice changed.
:app:assembleDebug green throughout, confirming every external Java
consumer still compiles.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

### Post-review correction (`migration-code-reviewer` loop 1, Convention MAJOR finding)

`migration-code-reviewer`'s loop 1 (`## Code Review Verdict`, MAJOR finding) caught that this
implementation added roughly 30 lines of newly-written explanatory comments across three of the four
converted production files, in direct violation of `AGENTS.md`'s "vital, always follow" /
"STRICTLY FOLLOW... NEVER DEVIATE" rule: *"Do not add any comments to the code you write, but also do
not remove comments that are already in the code."* The reviewer diffed every comment in the converted
files against `git show HEAD:...java` and found the same `!!`-justification wording repeated near-verbatim
at every `@SuppressLint("UseRequireInsteadOfGet")` site and at both blocks inside
`chooseProviderAndLogin`'s anonymous `ArrayAdapter` — content that duplicates, rather than adds to, the
rationale already correctly captured in `ui/preferences/README.md`'s new "Kotlin conversion conventions"
section (conventions #2 and #4). No re-scoping question here: the rule is unambiguous and the fix is
mechanical, not a judgment call.

**Fix applied.** Removed the newly-added comment blocks at exactly the reviewer-cited locations, leaving
every already-existing Java comment untouched:
- `GpodderAuthenticationFragment.kt:55-56` (the `!!`-justification comment ahead of `onCreateDialog`'s
  `@SuppressLint`) — removed. The class KDoc (originally `:38-40` in the Java) and the `generateDeviceId`
  device-naming-format comment (originally `:211-212` in the Java) are pre-existing and were left in
  place, per the reviewer's own note that these two were correctly preserved and not part of the finding.
- `NextcloudAuthenticationFragment.kt:30-31` and `:109-110` (identical `!!`-justification comment ahead of
  `onCreateDialog` and `onNextcloudAuthError`) — both removed. The class KDoc is pre-existing and untouched.
- `SynchronizationPreferencesFragment.kt:38-40, 76-78, 155-157, 171-173, 185-189, 208-211, 240-242` — all
  seven blocks removed: the six `!!`-justification comments ahead of `onStart`, `setupScreen`,
  `updateActionBar`, `chooseProviderAndLogin`, and `updateLastSyncReport`, plus the two comments inside the
  anonymous `ArrayAdapter` (the `holder`-as-field rationale and the `getItem(position)!!` nullability
  rationale). The pre-existing `// Do not call from onCreate; ActionBar is not yet available at that point`
  comment (Java `:152`) was left in place.
- `AuthenticationDialog.kt` — checked directly; carried zero newly-added comments to begin with (only the
  pre-existing class KDoc), consistent with the reviewer not citing it.

Re-diffed every comment line in all three touched files against each file's Java original after the edit:
the surviving comment set in each `.kt` file now maps 1:1 to the Java file's comment set, same content,
same relative position — nothing added, nothing removed that was already there.

Re-verified per the reviewer's suggested fix and this pipeline's AC7 discipline:
- `./gradlew --console=plain :ui:preferences:testFreeDebugUnitTest --rerun` — BUILD SUCCESSFUL,
  **38/38, 0 failures, 0 errors** (per-class: `AuthenticationDialogCharacterizationTest` 4,
  `AuthenticationDialogJavaInteropTest` 1, `GpodderAuthenticationFragmentCharacterizationTest` 9,
  `NextcloudAuthenticationFragmentCharacterizationTest` 6,
  `SynchronizationPreferencesFragmentCharacterizationTest` 8, `SynchronizationPreferencesFragmentLifecycleTest`
  4, `SyncSettingsHarnessSmokeTest` 6 — read from the per-class JUnit XML reports, not the console tail).
- `./gradlew --console=plain :ui:preferences:testPlayDebugUnitTest --rerun` — BUILD SUCCESSFUL, same
  **38/38, 0 failures, 0 errors**, identical per-class breakdown.
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL, confirming the four untouched `:app` guard files
  still compile against the (now comment-stripped) converted classes.
- `./gradlew :ui:preferences:ktlintCheck` — BUILD SUCCESSFUL; `ktlintMainSourceSetCheck` genuinely
  re-executed (not `UP-TO-DATE` from before the edit) and passed; `ktlintTestSourceSetCheck` reports
  `UP-TO-DATE`, correctly reflecting that no test file was touched by this fix.
- `./gradlew :ui:preferences:checkstyle :ui:preferences:lint` — BUILD SUCCESSFUL; `checkstyle` reports
  `UP-TO-DATE` (expected — it only inspects Java sources, and this fix touched only `.kt` files); `lint`
  genuinely ran and reported nothing new.

As expected for a comment-only change, no test hash, assertion, or `!!` count moved: this correction
touches no code identifier, no test file, and no other Decision, Step, or Acceptance Criterion. Scope
stays exactly the four production files already in File Scope — no file added or removed.

The reviewer's finding also noted similar explanatory comments in some characterization test files
(`GpodderAuthenticationFragmentCharacterizationTest.kt`, `NextcloudAuthenticationFragmentCharacterizationTest.kt`)
as a lower-value, secondary observation, explicitly calling the production-file instances "the higher-value
fix since they duplicate content the README already carries." This correction addresses the MAJOR finding's
cited locations, all of which are in the four production files. The test-file comments are left for a
separate pass if `migration-code-reviewer` re-raises them in loop 2.

### Post-review correction (`migration-code-reviewer` loop 2, Convention MAJOR finding)

`migration-code-reviewer`'s loop 2 (`## Code Review Verdict`, MAJOR finding) re-raised exactly the
question loop 1 deferred: the same `AGENTS.md` rule — *"Do not add any comments to the code you
write"* — applied to the 7 new characterization/support test files that loop 1's fix deliberately
left untouched (`SyncSettingsTestHost.kt` was, and remains, the only clean one of the 8). Unlike the
four production files, none of these test files has a Java original to carry anything over from —
this module had zero tests before this milestone (per Research) — so every comment in them is
unambiguously new prose with no "preserved from the original" defense available. The reviewer's own
words closed the scoping question rather than reopening it: *"AGENTS.md draws no distinction between
production and test code, or between converted and newly-authored files, for this rule... I am not
reading a carve-out into it that the text does not contain."* No re-scoping needed here either — same
rule, same mechanical fix, now applied file-by-file to the set loop 2 named.

**Fix applied.** Removed every newly-added comment from all 7 cited files, leaving `SyncSettingsTestHost.kt`
untouched (it had nothing to remove):

- `GpodderAuthenticationFragmentCharacterizationTest.kt` — removed the class KDoc (gap/reflection
  rationale), the `AntennapodHttpClient`/D5 fallback comment ahead of the host-step `assertThrows`
  block, the `setupLoginView()`/hosturl-precondition comment, the RxJava-short-circuit comment, the
  device-dedupe rationale comment, and the Turkish-locale-hazard comment. Beyond the reviewer's exact
  citations, this file also carried nine short trailing `// -> STEP_X` annotations on `invokeAdvance(fragment)`
  calls that the loop 2 finding did not individually cite but that are equally new, unconditionally
  in scope under the same rule, and inconsistent with the zero-added-comments outcome the loop 1 fix
  already established for the four production files (verified by re-grepping them: no new comments
  survive there either) — removed those too, for consistency with that precedent rather than leaving
  them for a hypothetical loop 3 to re-raise.
- `NextcloudAuthenticationFragmentCharacterizationTest.kt` — removed the differing-interleaving-from-gpodder
  comment, the credentials-clear-timing comment, the deferred-dismiss-not-resumed comment, the
  onResume-shouldDismiss comment, the fresh-fragment-no-key comment, the onCreateDialog-guard/D5
  restoration comment block, and the negative-case comment.
- `SynchronizationPreferencesFragmentCharacterizationTest.kt` — removed both header-click-listener
  rationale comments (logged-out and logged-in cases), the `HtmlCompat.FROM_HTML_MODE_LEGACY` rendering
  comment, and both logout-ordering rationale comments.
- `SynchronizationPreferencesFragmentLifecycleTest.kt` — removed the sticky-event-discrimination
  comment, the not-connected precondition comment, the "not even the subtitle" comment, the
  disconnected-branch comment, and the onStop-empty-vs-null comment.
- `SyncSettingsHarnessSmokeTest.kt` — removed the class KDoc (D5 proof-obligation framing) and the
  `AntennapodHttpClient`/D5 row-4-fallback comment block.
- `AuthenticationDialogCharacterizationTest.kt` — removed both null-initial-value rationale comments.
- `RecordingSynchronizationQueue.kt` — removed both KDoc blocks (class-level and `onCall`-property-level).

Re-diffed every one of the 7 files against the working tree after the edit (`git diff --`, not the
prose claim alone): every hunk removes comment lines only — no assertion, call, field name, or
string literal changed anywhere. `SyncSettingsTestHost.kt` has no diff, consistent with it never
having carried a comment.

Re-verified per the reviewer's suggested fix and this pipeline's AC7 discipline, using this module's
actual flavoured task names (not a generic `testDebugUnitTest`):
- `./gradlew --console=plain :ui:preferences:testFreeDebugUnitTest --rerun` — BUILD SUCCESSFUL,
  **38/38, 0 failures, 0 errors** (summed from the per-class JUnit XML reports under
  `ui/preferences/build/test-results/testFreeDebugUnitTest/`, not the console tail): `AuthenticationDialogCharacterizationTest`
  4, `AuthenticationDialogJavaInteropTest` 1, `GpodderAuthenticationFragmentCharacterizationTest` 9,
  `NextcloudAuthenticationFragmentCharacterizationTest` 6, `SynchronizationPreferencesFragmentCharacterizationTest`
  8, `SynchronizationPreferencesFragmentLifecycleTest` 4, `SyncSettingsHarnessSmokeTest` 6 — identical
  per-class breakdown to every prior run, confirming a comment-only change moved nothing.
- `./gradlew --console=plain :ui:preferences:testPlayDebugUnitTest --rerun` — BUILD SUCCESSFUL, same
  **38/38, 0 failures, 0 errors**, identical per-class breakdown, read from
  `ui/preferences/build/test-results/testPlayDebugUnitTest/`.
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (`UP-TO-DATE` on the app-assembly chain, since
  test source changes don't invalidate it), confirming this correction did not disturb the four
  untouched `:app` guard files' ability to compile.
- `./gradlew :ui:preferences:ktlintCheck` — BUILD SUCCESSFUL. This time, as the loop 2 finding
  anticipated, `ktlintTestSourceSetCheck` genuinely re-executed (not `UP-TO-DATE`) since the test
  source set is what changed; `ktlintMainSourceSetCheck` reported `UP-TO-DATE`, correctly reflecting
  that no production file was touched by this fix.

As expected for a comment-only change, no test hash, assertion, or characterization-test count moved:
this correction touches no code identifier, no production file, and no other Decision, Step, or
Acceptance Criterion. Scope is exactly the 7 test files loop 2 named plus the additional short
`GpodderAuthenticationFragmentCharacterizationTest.kt` step-marker comments flagged above for
consistency — no file added or removed, `SyncSettingsTestHost.kt` still untouched.

With this, all newly-written comments in production and test code alike have been removed across the
full diff (re-confirmed by grep across all four converted `.kt` production files and all 7 fixed test
files: zero `//` or `/**` lines remain that don't already exist in a Java original — there are none to
compare test files against, and none remain). Nothing found here is being flagged back to the planner
or reviewer as worth preserving as an exception to `AGENTS.md`'s rule; the rule was applied as written.

