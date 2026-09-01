# antennapod-sync-settings-concurrency-mvvm-milestone-17

> **Description:** `concurrency` track + MVVM layer for the Sync Settings screen modernization sequence. Replace the two orphaned RxJava3 subscriptions in `GpodderAuthenticationFragment` and the sticky-EventBus `SyncServiceEvent` subscription in `SynchronizationPreferencesFragment` with Coroutines + Flow, and stand up the ViewModel / UI-state layer this slice has never had. Research (Milestone 15) found the ViewModel layer is a **blocking prerequisite for Milestone 20 (`compose`)**, not optional scope — `SynchronizationPreferencesFragment` reads all state on demand from static globals with no state holder of any kind. Sixth of a ~6-milestone sequence — see `tasks/antennapod-sync-settings-modernization-future-work.md` for the full sequence and the Milestone 17 row specifically.
> **Repo:** `services/android-migration/projects/portfolio/antennapod`
> **Created:** 2026-08-28

> **Pre-research context (carried over from Milestone 15's Research pass, Milestone 16's outcome, and the future-work file — do not re-derive):**
> - **Depends on:** Milestone 16 (`di`+`compose`+`concurrency` prerequisites, PR #31, **merged into `develop` at `8e4c292174897b75d7b9e1609c595717953a004e`, 2026-08-28**). Milestone 16 catalogued and wired `kotlinx-coroutines-core`/`-android` at **1.9.0** into `:ui:preferences` (and re-pointed `kotlinx-coroutines-play-services` at the same ref), added Hilt **2.58** at `:app` with `@HiltAndroidApp` on `PodcastApp`, catalogued KSP **2.3.11** (root-declared, applied to no module), and wired the Compose BOM **2026.06.01** into `:ui:preferences`. This milestone **consumes** that coroutines wiring — it does not add or move the coroutines pins. Branch fresh from `origin/develop` and confirm `8e4c29217` is an ancestor before Step 1.
> - **DI wiring is Milestone 18, not this one.** Hilt infra exists on `develop` after Milestone 16 but no DI graph, `@HiltViewModel`, or injected dependency exists yet. This milestone stands up the ViewModel as a **plain `ViewModel`/`AndroidViewModel` with a hand-written `ViewModelProvider.Factory`** (or `viewModels { }` lambda), the same shape `BugReportViewModel` uses elsewhere in `:ui:preferences` today — Milestone 18 is what later moves it to `@HiltViewModel`. Do not pull Hilt into this milestone's File Scope. `BugReportViewModel` is a **shape reference, not a layer to build on**.
> - **`BugReportViewModel` (`:ui:preferences`, different screen, same module) is the local MVVM precedent** — survey it during Research for the repo's established ViewModel/state-holder conventions (state exposure type, factory pattern, coroutine scope usage, test approach) before proposing this slice's layer.
> - **Three concurrency call sites are in scope**, all surfaced by Milestone 15's Research:
>   1. Two orphaned RxJava3 `Disposable` subscriptions in `GpodderAuthenticationFragment` — the returned `Disposable` is discarded, so they structurally **cannot** survive a move to `viewModelScope`/`lifecycleScope` (which cancel by construction). That is a **desirable behavior change, not equivalence** — must be recorded as such per-behavior, not silently "preserved".
>   2. The sticky-replay `SyncServiceEvent` EventBus subscription in `SynchronizationPreferencesFragment` — its sticky-replay semantics must be reproduced with `StateFlow` or `SharedFlow(replay = 1)`, **not** `replay = 0`. Before finalizing the Flow shape, check `:net:sync:service` for what posts/removes `SyncServiceEvent` stickily — that module is **out of the Milestone 15 slice and not yet surveyed**.
>   3. The `devices` field data race in `GpodderAuthenticationFragment` — a known pre-existing defect.
> - **This repo's convention is pin-and-track known defects, not drive-by-fix** (`net/sync/service-interface/README.md` convention #11). For each of the three known defects above (the two discarded `Disposable`s, the sticky-replay semantics, the `devices` data race) the plan must **explicitly decide and record, per-behavior, whether it is preserved or fixed** — with the RxJava disposables being the one case where a fix is structurally forced by the target API.
> - **Inherited `!!` cleanup from Milestone 15's `kotlin` conversion, this milestone's to address:** the 5 ActionBar `(activity as AppCompatActivity?)!!.supportActionBar!!` sites in `SynchronizationPreferencesFragment` disappear once ActionBar handling moves to a ViewModel-backed host (D11 rule 4 in Milestone 15's task file named this explicitly). More broadly, most of the slice's 49-site `!!` inventory exists because `:storage:preferences` statics and `SynchronizationQueue.instance` are read directly with no DI seam and no state holder — once this milestone's ViewModel owns that state, many forced assertions have a real path to disappearing. "Fewer `!!`" here is **evidence the architectural problem got fixed**, not an incidental style win — but wholesale removal that depends on the DI seam is Milestone 18's, not this one's.
> - **Gap 16, still open from Milestone 15:** `error.getCause().getMessage()`'s converted form (`error.cause!!.message` in `GpodderAuthenticationFragment.kt`) is pinned only by a grep on the emitted expression shape, not by an executable test — reaching it needs either MockWebServer or the injectable `GpodnetService` Milestone 18's DI work produces. Close it here **if convenient**, or explicitly hand it to Milestone 18.
> - **Milestone 15's characterization suite is this milestone's regression net.** `:ui:preferences` `screen/synchronization/` on `develop` after Milestones 15/15b/16 is **11 files / 8 test classes / 41 `@Test` methods** (verify the current count against the freshly-fetched `origin/develop` at Step 1 — Milestone 16 stated it changed no screen behavior and added no screen test, but re-measure, don't trust). Every test reads/writes the static preference globals and `SynchronizationQueue.instance` directly by design. This suite must stay green throughout — a suite rewritten at the same time it is meant to prove equivalence proves nothing.
> - **`:ui:preferences` applies `playFlavor.gradle`** — its test tasks are flavoured (`testFreeDebugUnitTest`/`testPlayDebugUnitTest`). Do not copy unflavoured task names from `:net:sync:service-interface`.
> - **CI bar** (from Milestone 16's D12): `assemblePlayDebug` + `assemblePlayRelease` + `assembleFreeRelease` + `assemblePlayDebugAndroidTest` + `checkstyle lint` + `ktlintCheck`, plus both flavoured `:ui:preferences` test tasks locally.
> - **Auto-chain authorized by José, 2026-08-28**, for this milestone specifically (Research → Plan → red-team plan, max 2 loops → Implement → Code review, max 3 loops → red-team implementation → PR opened, no pause between stages). Scoped to opening a PR, not merging. See `[[antennapod-pipeline-autonomy]]` memory precedent from Milestones 12–16.
> - This is unaffiliated OSS portfolio case-study work — see `services/android-migration/projects/portfolio/README.md`.

## Research
_Last updated by: legacy-android-researcher | 2026-08-28_

**Branch-state precondition verified.** `git fetch origin` run; `git merge-base --is-ancestor 8e4c292174897b75d7b9e1609c595717953a004e origin/develop` → **true**. `origin/develop` tip is `8e4c29217` ("toolchain/ui-preferences: catalog and wire Hilt, Compose, and coroutines prerequisites (Milestone 16) (#31)"). Working tree is on `toolchain/sync-settings-milestone-16` at `01e63561a` (the merge's second parent); `git diff HEAD origin/develop` is **empty**, so everything surveyed below is byte-identical to `origin/develop`. Milestone branch **not** cut — that remains the developer's Step 1.

### Summary

The Sync Settings slice is 4 Kotlin files (`AuthenticationDialog`, `GpodderAuthenticationFragment`, `NextcloudAuthenticationFragment`, `SynchronizationPreferencesFragment`) under `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/`, converted in Milestone 15 and untouched by Milestone 16. There is **no state holder of any kind**: `SynchronizationPreferencesFragment` is a `PreferenceFragmentCompat` subclass that reads every value on demand from Java statics (`SynchronizationSettings`, `SynchronizationCredentials`) and the mutable global `SynchronizationQueue.instance`, and writes all output straight onto the host Activity's `ActionBar`. Asynchrony is two orphaned RxJava3 subscriptions in `GpodderAuthenticationFragment` (`Schedulers.io()` → `AndroidSchedulers.mainThread()`, both `Disposable`s discarded, both capturing dialog `View`s directly in their lambdas) plus one sticky-replay EventBus subscription in `SynchronizationPreferencesFragment`. The module's coroutines wiring (`kotlinx-coroutines-core`/`-android` 1.9.0) landed in Milestone 16 and is currently **unused by any production code in the module** — no `suspend`, no `Flow`, no `viewModelScope` exists anywhere in `:ui:preferences` today.

This milestone introduces the first state holder for the slice and converts the three async call sites to Coroutines/Flow. Three findings materially change the shape of that work versus the pre-research assumptions. **First**, the `SyncServiceEvent` producer side is bigger and more one-directional than expected: 9 `postSticky` sites across `:net:sync:service` (8 in `SyncService.doWork()` on a WorkManager background thread, 1 in `SynchronizationQueueImpl`), and **zero `removeStickyEvent` calls in production code anywhere in the repo** — the sticky event is never cleared for the process lifetime. Any Flow seam therefore either bridges EventBus locally inside `:ui:preferences` (leaving all 9 producers untouched) or reaches across a module boundary into `:net:sync:service`, which is a materially larger blast radius and is not what this milestone's description scopes. **Second**, `BugReportViewModel` — the named shape reference — is **Java, LiveData-based, RxJava-driven, has no hand-written factory, and has zero tests**; it is a weaker precedent than the pre-research block assumes, and the only Coroutines/StateFlow ViewModel precedent in the repo is in `app-wearos`, a different app module. **Third**, Gap 16's unpinned `error.cause!!.message` is not a theoretical hazard: `GpodnetServiceAuthenticationException("Wrong username or password")` is constructed with a message and **no cause**, so `error.cause` is null on the single most common login failure and the expression throws NPE inside an RxJava `onError` consumer on the main thread. Entering a wrong gpodder.net password crashes the app today.

### Findings

#### 1. The two orphaned RxJava3 `Disposable` subscriptions (`GpodderAuthenticationFragment.kt`)

Both are in `GpodderAuthenticationFragment.kt`; neither result is assigned, and the class has **no** `CompositeDisposable`, no `onDestroyView`/`onDestroy`/`onDismiss` override, and no disposal path of any kind.

| | **Site A — login** | **Site B — create device** |
|---|---|---|
| Location | `setupLoginView`, `:121-139` (inside `login.setOnClickListener`) | `createDevice`, `:176-192` |
| Type | `Completable.fromAction { … }` | `Observable.fromCallable { … }` |
| Work thread | `.subscribeOn(Schedulers.io())` `:128` | `.subscribeOn(Schedulers.io())` `:181` |
| Callback thread | `.observeOn(AndroidSchedulers.mainThread())` `:129` | `.observeOn(AndroidSchedulers.mainThread())` `:182` |
| Background work | `service!!.setCredentials(usernameStr, passwordStr)` `:122`; `service!!.login()` `:123`; `devices = service!!.getDevices()` `:124`; then `username`/`password` field writes `:125-126` | `generateDeviceId(deviceNameStr)` `:177`; `service!!.configureDevice(…)` `:178`; returns a new `GpodnetDevice` `:179` |
| Success handler | `login.isEnabled = true`; `progressBar.visibility = GONE`; `advance()` `:130-133` | `progBarCreateDevice.visibility = GONE`; `selectedDevice = device`; `advance()` `:183-186` |
| Error handler | `login.isEnabled = true`; `progressBar.visibility = GONE`; **`txtvError.text = error.cause!!.message`** `:137`; `txtvError.visibility = VISIBLE` | `deviceName.isEnabled = true`; `progBarCreateDevice.visibility = GONE`; **`txtvError.text = error.message`** `:190`; `txtvError.visibility = VISIBLE` |
| Discarded | Yes — `.subscribe(…)` return value dropped `:130` | Yes — `.subscribe(…)` return value dropped `:183` |

**The two error handlers are deliberately asymmetric** and must stay that way: `error.cause!!.message` (Site A) vs `error.message` (Site B). This is Milestone 15's D7 two-handler table, enforced by its AC13 four-count `grep -F` check (`error.cause!!.message`→1, `error.message`→1, `error.cause?.message`→0, `error.`→2). Any Coroutines rewrite that "makes the two handlers consistent" breaks a recorded equivalence decision.

**View capture / leak.** Both lambdas close directly over dialog `View` instances (`login`, `progressBar`, `txtvError` in A; `deviceName`, `progBarCreateDevice`, `txtvError` in B) resolved from the `ViewFlipper` child at `setup*View` time. Because nothing disposes, an in-flight network call holds the dialog's whole view hierarchy alive, and its callback runs against detached views if the dialog was dismissed. The dialog is `setCancelable(false)` in both senses (`:60-61`, pinned by `testDialogIsNonCancellableInBothSenses`), which narrows but does not eliminate the window — configuration change and `dismiss()` from the finish step both still detach.

**Why the fix is structurally forced.** `viewModelScope`/`lifecycleScope` cancel by construction, so moving these subscriptions there *cannot* preserve "runs to completion after the host is gone." Per the pre-research block and the repo's pin-and-track convention, this is the one case where a fix is unavoidable and must be recorded as a **deliberate behavior change, not equivalence**.

#### 2. The sticky-replay `SyncServiceEvent` subscription (`SynchronizationPreferencesFragment.kt`)

```
@Subscribe(threadMode = ThreadMode.MAIN, sticky = true)     :54
fun syncStatusChanged(event: SyncServiceEvent) { … }        :56-71
```

Lifecycle, in exact source order — the ordering is behaviorally load-bearing:

- `onStart()` `:39-45`: `super.onStart()` → set ActionBar **title** `:41` → `updateScreen()` `:42` → `updateActionBar()` `:43` → **`EventBus.getDefault().register(this)` `:44`**. Registration is **last**, so the sticky replay fires *after* `updateActionBar()` has already written a subtitle, and can immediately overwrite it.
- `onStop()` `:48-52`: `super.onStop()` → **`unregister(this)` `:50`** → set ActionBar subtitle to `""` `:51`. Unregistration happens **before** the subtitle clear.

**Sticky semantics actually relied on.** `register()` immediately re-delivers the most recent `SyncServiceEvent` if one exists. This is the only mechanism by which the screen shows sync status it did not itself trigger — e.g. returning to the screen while a background sync is mid-flight. Directly pinned by `testStickyEventReplaysOnStart`, which posts a sticky `sync_status_started` *before* attaching the fragment and asserts the ActionBar subtitle equals that string.

**Guard and branch** inside the handler:
- `:57-59` — early `return` if `!SynchronizationSettings.isProviderConnected()`. The sticky replay is **dropped** when disconnected (pinned by `testSyncEventIgnoredWhenNotConnected`).
- `:60` — `updateScreen()` on every accepted event.
- `:61-70` — if `messageResId` is `sync_status_error` **or** `sync_status_success`, render `updateLastSyncReport(isLastSyncSuccessful(), getLastSyncAttempt())` (a formatted relative-time string); **else** set the subtitle to the raw `messageResId` (pinned by `testSubtitleBranchesOnMessageResId`).

**Distinguishing "no event yet" from "an event whose value is null" matters here**, because the disconnected-guard path and the never-synced path produce different ActionBar states (`null` subtitle vs `""` subtitle — `testOnStartSetsTitleAndOnStopSetsEmptySubtitleWhereasDisconnectedSetsNull` asserts both). `MutableStateFlow<SyncServiceEvent?>(null)` expresses this directly; `MutableSharedFlow(replay = 1)` expresses "nothing posted yet" only by absence of emission, which is harder to render deterministically. `replay = 0` is ruled out by `testStickyEventReplaysOnStart`.

#### 3. `:net:sync:service` — the `SyncServiceEvent` producer side (not previously surveyed)

**9 `postSticky` sites. 0 `removeStickyEvent` sites in production code.**

| File | Line | Event | Thread |
|---|---|---|---|
| `SynchronizationQueueImpl.kt` | `:136` | `sync_status_started` | caller's thread — main for the `pref_synchronization_sync` row, background for `fullSync()`/`clear()` via `LockingAsyncExecutor.executeLockedAsync` |
| `SyncService.kt` | `:61` | `sync_status_wait_for_downloads` | WorkManager `Worker.doWork()` background thread |
| `SyncService.kt` | `:70` | `sync_status_success` | same |
| `SyncService.kt` | `:74` | `sync_status_error` | same |
| `SyncService.kt` | `:94` | `sync_status_wait_for_downloads` | same |
| `SyncService.kt` | `:121` | `sync_status_subscriptions` | same |
| `SyncService.kt` | `:201` | `sync_status_episodes_download` | same |
| `SyncService.kt` | `:208` | `sync_status_episodes_upload` | same |
| `SyncService.kt` | `:211` | `sync_status_upload_played` | same |

Notes the Flow shape must account for:

- **The sticky event is never removed.** The only `removeStickyEvent(SyncServiceEvent::class.java)` calls in the repo are in `SynchronizationPreferencesFragmentLifecycleTest`'s `setUp`/`tearDown` (`:39`, `:45`) — test hygiene, not production behavior. So the last terminal status persists for the process lifetime and replays on every subsequent visit to the screen. A `MutableStateFlow` holding the last value reproduces this exactly and needs no "clear" path in production.
- **`SynchronizationQueueImpl.kt:136` posts conditionally** — only inside the `else` branch of `if (SyncService.isCurrentlyActive())`. When a sync is already running the debounce path is taken and **no** `sync_status_started` is posted. The status stream is not a clean state machine.
- **All 8 `SyncService` posts originate off the main thread**; the consumer declares `ThreadMode.MAIN`, so EventBus is performing the thread hop today. A Flow replacement must keep an explicit main-dispatcher collection point.
- **Module boundary.** `:ui:preferences` already has `implementation project(':net:sync:service')` and `project(':event')` (`ui/preferences/build.gradle:41,43`), so it *can* see both. But `:event` declares **no** EventBus and **no** coroutines dependency (`event/build.gradle` — only `:model` + `androidx.core`), so a shared Flow holder cannot live there without new wiring. `:net:sync:service-interface` has no coroutines either (only `:model`, `:storage:preferences`, rxandroid, rxjava).

#### 4. `BugReportViewModel` — the local ViewModel precedent (shape reference only)

`ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/bugreport/BugReportViewModel.java` — **it is Java, not Kotlin.** Conventions it actually establishes:

- **Base class:** `AndroidViewModel` (takes `Application`) `:26,172`.
- **State exposure:** a `private final MutableLiveData<UiState>` `:169` exposed as `LiveData<UiState>` via `getState()` `:186-188`. **LiveData, not StateFlow.**
- **State model:** one nested `public static class UiState` `:86-167` aggregating everything the screen needs, with nested `EnvironmentInfo`/`CrashLogInfo` and a `CrashLogState` enum. Notably **mutable** — `setCrashLogState` `:194-203` mutates the *existing* instance and re-posts the same reference, rather than emitting a copy.
- **Async:** **RxJava3, not Coroutines** — `Observable.fromCallable(…).subscribeOn(Schedulers.computation()).subscribe(this.uiState::postValue)` `:175-177`, with the `Disposable` retained `:170` and disposed in `onCleared()` `:180-184`. There is **no `viewModelScope` usage anywhere in `:ui:preferences`.**
- **Factory: there is none.** `BugReportFragment.java:46` does `new ViewModelProvider(this).get(BugReportViewModel.class)` — the default `AndroidViewModelFactory` handles the `Application` constructor. **This contradicts the pre-research block's "the same shape `BugReportViewModel` uses today" for a hand-written `ViewModelProvider.Factory`; no hand-written factory exists in this module.** A hand-written factory is still the right call for this milestone if the ViewModel takes any dependency beyond `Application` — but it is a *new* pattern for `:ui:preferences`, not a local precedent, and should be recorded as such.
- **Scope:** fragment-scoped (`ViewModelProvider(this)`), observed via `getViewLifecycleOwner()` `:68`.
- **Tests: none.** There is no test file for `BugReportViewModel` anywhere in the repo. It is a shape reference with **zero** proven testing approach to copy.

**The only Coroutines/StateFlow ViewModel precedent in the repo is `app-wearos`** (`MainViewModel.kt`, `EpisodeListViewModel.kt`, `FeedListViewModel.kt`, `EpisodeDetailViewModel.kt`) — a separate app module, not `:ui:preferences`. `MainViewModel.kt` shows the idiomatic shape: `data class MainUiState(...)` `:19`, `class MainViewModel(application: Application) : AndroidViewModel(application)` `:21`, `private val _uiState = MutableStateFlow(MainUiState())` `:22`, `val uiState: StateFlow<MainUiState> = _uiState` `:23`, `viewModelScope.launch(Dispatchers.IO)` `:26`. Also untested.

#### 5. The `devices` field data race (`GpodderAuthenticationFragment.kt`)

```
@Volatile private var username: String?              :45-46
@Volatile private var password: String?              :48-49
@Volatile private var selectedDevice: GpodnetDevice? :51-52
private var devices: List<GpodnetDevice>? = null     :53   ← no @Volatile
```

- **Written** on `Schedulers.io()` at `:124` (`devices = service!!.getDevices()`, inside Site A's `Completable`).
- **Read** on the main thread at `:151` (`for (device in devices!!)` in `setupDeviceView`), `:213` (`if (devices == null)`), `:217` (`for (device in devices!!)` in `isDeviceInList`), and transitively via `generateDeviceName()` `:199`.

No happens-before edge is established between the io-thread write and the main-thread reads other than the incidental one RxJava's `observeOn` provides for the *success* path. `isDeviceInList` is also reachable from `generateDeviceName()` during `setupDeviceView`, and its `devices == null` guard at `:213` followed by `devices!!` at `:217` is a check-then-act on a non-volatile mutable field.

**Deliberately preserved in Milestone 15**, not an oversight: D12.2, `AC12` (`grep -c '@Volatile'` → exactly 3, `devices` without it), and `ui/preferences/README.md` convention 5 all name it. It is one of the three "pinned, not fixed" defects. Note that if the ViewModel holds `devices` as `StateFlow<List<GpodnetDevice>>` with an `emptyList()` default, the race is fixed *and* two `!!` sites disappear as a side effect — so "preserve" is not the free default here; the planner must decide explicitly.

#### 6. Gap 16 is a reachable main-thread crash, not just an unpinned expression

`GpodderAuthenticationFragment.kt:137` reads `txtvError.text = error.cause!!.message`. Tracing the exception hierarchy:

- `SyncServiceException` (`net/sync/service-interface/.../SyncServiceException.kt`) has two constructors: `constructor(message: String?) : super(message)` — **no cause** — and `constructor(cause: Throwable?) : super(cause)`.
- `GpodnetServiceException.java:8` `super(message)` (no cause) and `:12` `super(e)` (cause).
- `GpodnetServiceAuthenticationException.java:6` → `super(message)`, **no cause**. Thrown at `GpodnetService.java:368` as `new GpodnetServiceAuthenticationException("Wrong username or password")`.
- `GpodnetServiceBadStatusCodeException.java:8` → `super(message)`, **no cause**. Thrown at `GpodnetService.java:378,381` for "currently unavailable" / "unable to connect".

So on **wrong username or password** — the most common login failure — `error.cause` is `null` and `error.cause!!` throws NPE *inside* the RxJava `onError` consumer, on the main thread. RxJava3's `LambdaObserver` routes a throw from the error consumer to `RxJavaPlugins.onError` as a `CompositeException`, which with no global handler installed reaches the thread's uncaught-exception handler — **an app crash**. The paths that *do* carry a cause are the `GpodnetServiceException(e)` wrappers at `GpodnetService.java:100,136,172,203,303,316,333,360` (IOException/JSON wrapping), which render fine.

This is a **pre-existing defect faithfully carried over from the Java** (`error.getCause().getMessage()`), not something Milestone 15 introduced — but it is now demonstrably reachable rather than theoretical, and this milestone is the one touching that exact statement. Preserve-or-fix must be decided explicitly. Note that "preserve" is achievable in Coroutines (a `catch` block doing `e.cause!!.message` still throws NPE, propagating through `viewModelScope` to the default handler) but the exception *type* reaching the handler differs — RxJava wraps in `CompositeException`, a coroutine does not.

#### 7. `!!` inventory — what this milestone can legitimately eliminate

Current totals, measured (`grep -o '!!' <file> | wc -l`), matching Milestone 15's recorded 49:

| File | `!!` |
|---|---|
| `SynchronizationPreferencesFragment.kt` | 30 |
| `GpodderAuthenticationFragment.kt` | 12 |
| `NextcloudAuthenticationFragment.kt` | 7 |
| `AuthenticationDialog.kt` | 0 |
| **Total** | **49** |

`SynchronizationPreferencesFragment.kt`'s 30, by category:

| Category | Count | Lines | This milestone's? |
|---|---|---|---|
| `(activity as AppCompatActivity?)!!.supportActionBar!!` | **10** (5 statements × 2) | 41, 51, 69, 158, 238 | **Yes** — D11 rule 4 named these explicitly |
| `findPreference<Preference>(…)!!` | 11 | 76, 92, 96, 100, 113, 130, 133, 134, 135, 143, 145 | **No** — `PreferenceFragmentCompat` API artifact; dies with Milestone 20 (`compose`), not with a ViewModel |
| `SynchronizationQueue.instance!!` | 3 | 93, 97, 102 | **Conditional** — see below |
| `context!!` | 2 | 164, 169 | No — Fragment API, preserved NPE (README convention 4) |
| `view!!` (Snackbar) | 1 | 103 | No — same |
| `activity!!` | 1 | 79 | No — same |
| `selectedProvider!!` | 1 | 117 | No — D11 rule 1's forcing-site decision, pinned by `testUnrecognisedProviderKeyThrowsAfterClearingHeaderTitle` |
| `getItem(position)!!` | 1 | 191 | No — adapter internals |

`GpodderAuthenticationFragment.kt`'s 12: `service!!` ×4 (122, 123, 124, 178), `devices!!` ×2 (151, 217), `SynchronizationQueue.instance!!` ×2 (79, 230), `context!!` (57), `dialog!!` (86), `error.cause!!` (137), `selectedDevice!!` (256).

**Honest assessment of the ActionBar 10.** They collapse only if the ViewModel owns title/subtitle as state and the fragment renders it from a single collection point. That still requires *one* `activity as AppCompatActivity` access somewhere, so the realistic outcome is 5 statements → 1 render site, i.e. roughly **8 of 10 eliminated, not 10**. The plan should state a number it can actually hit rather than inheriting "the 5 sites disappear entirely" from the future-work file.

**The `SynchronizationQueue.instance!!` question (3 here + 2 in Gpodder = 5).** The future-work file assigns these to Milestone 18's DI seam. But a hand-written `ViewModelProvider.Factory` can take `SynchronizationQueue` as a constructor parameter **without Hilt**, which would let this milestone eliminate the 3 in `SynchronizationPreferencesFragment` legitimately. That is a real scoping choice, not a foregone conclusion — taking it means this milestone defines the repository/dependency seam that Milestone 18 was scoped to define, and `RecordingSynchronizationQueue` (the existing test double, which works by assigning the global) would need to keep working either way. **Flagged for the planner, not decided here.**

**`devices!!` ×2** disappear if `devices` becomes non-null ViewModel state — but that is the same edit as fixing the pinned data race (finding 5), so the two decisions are coupled.

### Track-specific hazards

**`concurrency` track:**

1. **Coroutines are wired but entirely unused in this module.** `ui/preferences/build.gradle:47-48` declares `kotlinx-coroutines-core` and `-android`; no production file in `:ui:preferences` contains `suspend`, `Flow`, `launch`, or `viewModelScope`. This milestone writes the module's first coroutine.
2. **`kotlinx-coroutines-test` is not catalogued and not declared.** `gradle/libs.versions.toml` has `kotlinx-coroutines-core`/`-android`/`-play-services` at ref `coroutines = "1.9.0"` (`:14,69-71`) but **no `kotlinx-coroutines-test` entry**. There is no `runTest`, no `TestDispatcher`, no `StandardTestDispatcher`/`UnconfinedTestDispatcher` available. Testing a `viewModelScope` coroutine or a `StateFlow` deterministically needs one — and adding a catalog entry is arguably Milestone 16 toolchain work that Milestone 16 did not do. **No Turbine and no MockWebServer are catalogued either.**
3. **`lifecycle-viewmodel-ktx` / `lifecycle-runtime-ktx` are on the compile classpath only transitively.** Resolved `:ui:preferences:dependencies --configuration playDebugCompileClasspath` shows `androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4` and `lifecycle-runtime-ktx:2.9.4` present, pulled in transitively (and upgraded past the catalog's `lifecycle-runtime-compose = "2.8.7"` ref) — **but neither is declared in `ui/preferences/build.gradle`**. `androidx-lifecycle-viewmodel-ktx` *is* catalogued (`libs.versions.toml:59`) and currently wired only into `app-wearos` (`app-wearos/build.gradle:77`). `lifecycle-runtime-ktx` — which supplies `repeatOnLifecycle`/`flowWithLifecycle` — is **not catalogued at all**. So `viewModelScope` compiles today by accident of transitivity; relying on that undeclared is fragile.
4. **`repeatOnLifecycle` vs the current `onStart`/`onStop` pair is not a like-for-like swap.** The current code registers *after* `updateScreen()`/`updateActionBar()` and unregisters *before* the subtitle clear (finding 2). `repeatOnLifecycle(STARTED)` starts collection at a different point in the callback order; `testStickyEventReplaysOnStart` and `testOnStartSetsTitleAndOnStopSetsEmptySubtitleWhereasDisconnectedSetsNull` will both discriminate on this.
5. **Robolectric + coroutines main-dispatcher interaction.** The whole characterization suite is `RobolectricTestRunner` driving real fragments and calling `shadowOf(Looper.getMainLooper()).idle()` after each event post. A `Dispatchers.Main` backed by the Robolectric main looper needs `idle()` pumping to match; without `coroutines-test`'s `Dispatchers.setMain`, the existing `idle()` calls in the tests may or may not be sufficient. This is the most likely source of flaky/hanging tests in the milestone.
6. **RxJava stays in the module regardless.** `libs.rxandroid`/`libs.rxjava` (`build.gradle:57-58`) are still needed by `BugReportViewModel` and `NextcloudAuthenticationFragment` (7 `!!`, its own `nextcloudLoginFlow` async path — out of this milestone's named scope). Removing the Rx dependency is **not** available as an acceptance criterion.

**MVVM-layer hazards:**

7. **`SynchronizationPreferencesFragment` must stay `public` and no-arg-constructible.** `PreferenceActivity.java:75` instantiates it reflectively (Milestone 15 Step 11). It also extends `AnimatedPreferenceFragment` (Java, `ui/preferences/.../screen/AnimatedPreferenceFragment.java`), which sets `MaterialSharedAxis` transitions in `onCreate` and a `colorSurface` background in `onViewCreated` — the ViewModel host must not bypass either.
8. **The screen's "state" is currently written to an object it does not own.** Title and subtitle go onto the host Activity's `ActionBar`, which outlives the fragment. `onStop` sets subtitle to `""` while the disconnected path sets `null` — a distinction the suite asserts. A `UiState` data class must model "subtitle absent" vs "subtitle empty" as different values, or `testOnStartSetsTitleAndOnStopSetsEmptySubtitleWhereasDisconnectedSetsNull` fails.
9. **`GpodderAuthenticationFragment` must stay `open`** (`README` convention 2) — the characterization suite subclasses it as `TestableGpodderAuthenticationFragment` to observe `dismiss()` ordering.
10. **The wizard's state is a 4-step `ViewFlipper` driven by a private `currentStep` int** and private fields the tests manipulate **by reflection** (`field(name)`, `method(name)` helpers, `GpodderAuthenticationFragmentCharacterizationTest.kt:63-80`). Six of the nine tests set `devices`, `username`, `password`, or `selectedDevice` by reflected field name and read `currentStep` by reflected field name. **Moving any of those four fields into a ViewModel breaks those tests at the reflection call, not at an assertion** — and the regression net is supposed to stay green and unrewritten. This is the single largest structural tension in the milestone.

### Current test coverage

**Measured on `origin/develop`** (`ui/preferences/src/test/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/`): **11 files, 8 test classes, 41 `@Test` methods** — exactly matching the pre-research figure; Milestone 16 added none. Three files are helpers, not test classes: `RecordingSynchronizationQueue.kt`, `SyncSettingsCaptureHost.kt`, `SyncSettingsTestHost.kt`.

| File | `@Test` |
|---|---|
| `GpodderAuthenticationFragmentCharacterizationTest.kt` | 9 |
| `SynchronizationPreferencesFragmentCharacterizationTest.kt` | 8 |
| `NextcloudAuthenticationFragmentCharacterizationTest.kt` | 6 |
| `SyncSettingsHarnessSmokeTest.kt` | 6 |
| `AuthenticationDialogCharacterizationTest.kt` | 4 |
| `SynchronizationPreferencesFragmentLifecycleTest.kt` | 4 |
| `SyncSettingsScreenshotCaptureTest.kt` | 3 |
| `AuthenticationDialogJavaInteropTest.java` | 1 |

**What pins each in-scope behavior:**

| Behavior | Pinned by | Strength |
|---|---|---|
| Sticky replay on `onStart` | `SynchronizationPreferencesFragmentLifecycleTest.testStickyEventReplaysOnStart` `:60-73` | **Executable, strong.** Posts sticky *before* attach, asserts subtitle |
| Disconnected guard drops events | `…testSyncEventIgnoredWhenNotConnected` `:75-85` | **Executable, strong** |
| `error`/`success` vs other `messageResId` branch | `…testSubtitleBranchesOnMessageResId` `:87-106` | **Executable, strong.** Asserts both branches and that they differ |
| `onStart` title / `onStop` `""` / disconnected `null` | `…testOnStartSetsTitleAndOnStopSetsEmptySubtitleWhereasDisconnectedSetsNull` `:108-121` | **Executable, strong** |
| **Site A (login) RxJava subscription** | — | **NOTHING.** Zero coverage |
| **Site B (createDevice) RxJava subscription** | — | **NOTHING.** Zero coverage |
| **Threading / `subscribeOn`/`observeOn`** | — | **NOTHING** |
| **Disposal / cancellation behavior** | — | **NOTHING** (there is none to pin) |
| **`error.cause!!.message` (Gap 16)** | `AC13` `grep -F` only | **Source-level grep, not executable.** Still open |
| **`devices` data race** | `AC12` `grep -c '@Volatile'` → 3 | **Source-level grep, not executable.** The race itself is never exercised |
| `devices` *content* logic (dedup by id/caption, locale) | `testDeviceNameGenerationDedupesByIdAndByCaption` `:136-150`, `testDeviceIdUsesLocaleUsLowercase` `:152-163` | Executable, but sets `devices` **by reflection**, never via the Rx write |
| Wizard step transitions & credential commit order | `testCredentialCommitOrderOnDeviceToFinishTransition` `:165-185`, `testPartialWizardLeavesCredentialsUntouched` `:187-201` | Executable, but drives `advance()` **by reflection** |
| Username validation runs before network | `testUsernameValidationRunsBeforeAnyNetworkCall` `:114-134` | Executable — and it explicitly asserts the path **that returns before** `Completable.fromAction` is reached |

**Verified by grep:** no test file in `screen/synchronization/` references `Completable`, `Observable`, `Schedulers`, `Rx`, or `Trampoline`. **The entire asynchronous surface this milestone converts has zero executable test coverage today.** Everything currently green either avoids the async path (`testUsernameValidationRunsBeforeAnyNetworkCall`) or bypasses it by writing the post-async fields directly via reflection.

**This is the characterization gap that must be closed before conversion, not after.** The suite proves the *synchronous* behavior of the wizard and the *EventBus* behavior of the screen. It proves nothing about the two subscriptions being replaced. A green suite after this milestone therefore does **not** by itself demonstrate behavioral equivalence for findings 1, 5, and 6 — the planner must make new characterization tests for the async paths an early step, and must be explicit that they are new coverage, not a rewrite of the existing 41.

Blocking obstacle for those new tests: reaching either subscription requires a `GpodnetService`, which `GpodderAuthenticationFragment` constructs internally at `:81-85` from `AntennapodHttpClient.getHttpClient()` — and `README` convention 6 records that `getHttpClient()` is **not constructible under default Robolectric** (it builds an `okhttp3.Cache` against a never-set `cacheDirectory`, throwing NPE). `testHostStepClearsCredentialsAndQueueBeforeSettingHostUrl` `:102-104` asserts exactly that `NullPointerException`. So there is no seam today; MockWebServer is not catalogued; and injecting `GpodnetService` is the Milestone 18 DI work.

**Test-task names:** `:ui:preferences` applies `playFlavor.gradle`, so the tasks are `:ui:preferences:testFreeDebugUnitTest` and `:ui:preferences:testPlayDebugUnitTest` (README convention 8 — CI runs only the Play-flavoured one; run both locally).

### Open unknowns for the planner

1. **Where does the `SyncServiceEvent` Flow live?** Three options with very different blast radii: (a) a `callbackFlow`/`MutableStateFlow` bridge *inside* `:ui:preferences` (or the new ViewModel) that registers with EventBus and re-emits — leaves all 9 producers and `:net:sync:service` untouched, lowest risk, but keeps EventBus as the transport; (b) a shared Flow holder in `:net:sync:service` replacing `postSticky` at all 9 sites — larger, crosses a module with its own test surface, and other consumers of EventBus stickies would need auditing; (c) a holder in `:event` or `:net:sync:service-interface` — both currently lack coroutines *and* EventBus dependencies, so each needs new wiring. **Recommend (a) unless there is a stated reason to widen scope**, but this is the planner's call and it should be a Resolved Decision.
2. **`StateFlow<SyncServiceEvent?>` vs `SharedFlow(replay = 1)`.** Finding 2 argues `StateFlow` with a `null` initial value models "no sticky posted yet" more faithfully, and the never-removed sticky (finding 3) means no clear path is needed. Confirm against `testStickyEventReplaysOnStart` and `testOnStartSetsTitleAndOnStopSetsEmptySubtitleWhereasDisconnectedSetsNull`.
3. **How do the 9 reflection-driven Gpodder tests survive the ViewModel move?** (Hazard 10.) Six tests set `devices`/`username`/`password`/`selectedDevice` and read `currentStep` by reflected field name on `GpodderAuthenticationFragment`. If those fields move to a ViewModel, the tests fail at `getDeclaredField`. Options: keep the wizard fields on the fragment this milestone and scope the ViewModel to `SynchronizationPreferencesFragment` only; or move them and accept that the suite must be edited — which collides with "the regression net must not be rewritten while it is proving equivalence." **This needs an explicit decision before Step 1.** Scoping the ViewModel to the preferences screen only, and converting the Gpodder subscriptions to `lifecycleScope` without relocating state, is the option that keeps the net intact — but it changes what "MVVM layer" means for Milestone 20's prerequisite.
4. **Does `kotlinx-coroutines-test` get catalogued in this milestone?** It is absent (hazard 2) and there is no deterministic way to test a `viewModelScope` coroutine or a `StateFlow` without it. Adding a catalog entry is toolchain work of the kind Milestone 16 owned. Same question, lower stakes, for Turbine. If the answer is no, the plan must say how the new async characterization tests achieve determinism using only Robolectric `Looper` idling.
5. **Do `lifecycle-viewmodel-ktx` / `lifecycle-runtime-ktx` get declared explicitly?** (Hazard 3.) Both currently resolve transitively at 2.9.4; `lifecycle-runtime-ktx` is not in the catalog at all. Declaring them is correct practice but adds catalog churn; not declaring them leaves the milestone's core APIs on an undeclared transitive.
6. **Preserve or fix, per behavior — the four required decisions.** (a) The two orphaned `Disposable`s: fix is structurally forced by `viewModelScope`/`lifecycleScope`; record as deliberate behavior change. (b) The `devices` data race: fixing it is nearly free if `devices` becomes ViewModel state, but it is one of three defects `README` convention 5 says are pinned-not-fixed, and `AC12`'s grep would need retiring. (c) Gap 16's `error.cause!!.message`: now known to be a reachable wrong-password crash (finding 6) — "preserve a crash we can now prove users hit" deserves an explicit, argued decision rather than a default. (d) The sticky-replay semantics: must be reproduced, not dropped.
7. **Does Gap 16 close here or go to Milestone 18?** Closing it executably needs either MockWebServer (not catalogued) or an injectable `GpodnetService` (the Milestone 18 DI seam). If the hand-written factory in unknown 3 ends up taking dependencies anyway, a `GpodnetService` factory parameter could close it here — otherwise hand it to Milestone 18 **explicitly**, because after this milestone the code will look modernized and the gap will read as covered.
8. **How much of the ActionBar `!!` reduction is actually claimable?** Finding 7 argues ~8 of 10, not 10, because one `activity as AppCompatActivity` access must survive at the render site. The plan should commit to a specific number and a specific `grep` acceptance criterion rather than inheriting the future-work file's "disappear entirely."
9. **Does the hand-written factory take `SynchronizationQueue`?** (Finding 7.) Doing so eliminates 3 more `!!` without Hilt but starts defining the dependency seam Milestone 18 was scoped to own, and must not break `RecordingSynchronizationQueue`, which every test in the suite installs via the global.
10. **Is a new before/after screenshot expected?** `SyncSettingsScreenshotCaptureTest` (3 tests) writes `sync_settings_before_milestone_15b.png` under `build/reports/screenshots`. If this milestone changes no pixels, that should be stated as an acceptance criterion; if the ActionBar subtitle timing shifts, it may.

### Sources

- `tasks/antennapod-sync-settings-concurrency-mvvm-milestone-17.md:7-22` (pre-research context block)
- `tasks/antennapod-sync-settings-modernization-future-work.md:33-48` (Milestone 17 row), `:50-59` (Milestone 18), `:71-75` (standing conventions)
- `tasks/antennapod-sync-settings-kotlin-milestone-15.md:441-450` (Gap 16 / two-handler table), `:506` (D11), `:520`, `:605`, `:662-669` (AC8/AC12/AC13), `:714` (D12 pinned defects), `:731-733` (OQ2), `:846` (inventory reconciliation), `:1517`
- `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/GpodderAuthenticationFragment.kt:45-53` (`@Volatile` ×3, `devices` without), `:57`, `:79`, `:81-85`, `:86`, `:121-139` (Site A), `:124` (io-thread `devices` write), `:137` (Gap 16), `:151`, `:176-192` (Site B), `:190`, `:199`, `:212-223`, `:230`, `:256`
- `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/SynchronizationPreferencesFragment.kt:39-45` (`onStart` order), `:47-52` (`onStop` order), `:54-71` (sticky subscriber), `:57-59` (guard), `:61-70` (branch), `:41,51,69,158,238` (ActionBar `!!`), `:76,92,96,100,113,130,133,134,135,143,145` (`findPreference` `!!`), `:93,97,102` (`SynchronizationQueue.instance!!`), `:103,164,169,79,117,191`
- `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/bugreport/BugReportViewModel.java:26,86-167,169-192,194-203` ; `BugReportFragment.java:46,68`
- `app-wearos/src/main/java/de/danoeh/antennapod/wearos/MainViewModel.kt:19-38` ; `app-wearos/build.gradle:76-77`
- `net/sync/service/src/main/java/de/danoeh/antennapod/net/sync/service/SyncService.kt:61,70,74,94,121,201,208,211` (8 `postSticky`), `:42,46` (WorkManager `Worker`)
- `net/sync/service/src/main/java/de/danoeh/antennapod/net/sync/service/SynchronizationQueueImpl.kt:130-137` (conditional `postSticky`), `:40-51` (`LockingAsyncExecutor` paths)
- `net/sync/service-interface/src/main/java/.../SyncServiceException.kt` (both constructors) ; `net/sync/gpoddernet/.../GpodnetServiceException.java:8,12` ; `GpodnetServiceAuthenticationException.java:6` ; `GpodnetServiceBadStatusCodeException.java:8` ; `GpodnetService.java:368,378,381` (no-cause throws), `:100,136,172,203,303,316,333,360` (cause-carrying throws)
- `event/src/main/java/de/danoeh/antennapod/event/SyncServiceEvent.kt:3` ; `event/build.gradle` (no EventBus, no coroutines dependency)
- `ui/preferences/build.gradle:41,43,47-48,57-58,63-72` ; `ui/preferences/README.md` conventions 2, 4, 5, 6, 8, 9, 12
- `gradle/libs.versions.toml:7,14,58-59,69-71,90-92` (no `coroutines-test`, no `lifecycle-runtime-ktx`, no Turbine, no MockWebServer)
- `ui/preferences/src/test/java/.../SynchronizationPreferencesFragmentLifecycleTest.kt:39,45` (only `removeStickyEvent` calls in the repo), `:60-121`
- `ui/preferences/src/test/java/.../GpodderAuthenticationFragmentCharacterizationTest.kt:63-80` (reflection helpers), `:102-104`, `:114-134`, `:136-163`, `:165-201`, `:236-243`
- `ui/preferences/src/test/java/.../SyncSettingsTestHost.kt` ; `SyncSettingsScreenshotCaptureTest.kt:116-118`
- `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/AnimatedPreferenceFragment.java`
- `./gradlew -q :ui:preferences:dependencies --configuration playDebugCompileClasspath` (lifecycle 2.9.4 / coroutines 1.9.0 transitive resolution)
- `git merge-base --is-ancestor 8e4c292174897b75d7b9e1609c595717953a004e origin/develop` → true ; `git diff HEAD origin/develop` → empty

## Plan
_Last updated by: legacy-android-planner | 2026-08-31 — **Revision 1 complete**, closing the plan red-team's Loop 1 CHALLENGE (C1–C6); awaiting red-team Loop 2. See `## Plan — Revision 1` immediately before the verdict block at the end of this file for exactly what moved and why._

> **Revision 1 marker.** Two MAJOR findings changed real design: **C2** replaced D5's ViewModel-lifetime EventBus registration with a `callbackFlow` + `stateIn(SharingStarted.WhileSubscribed)` bridge whose registration is tied to collection, and **C1** moved the cancellation proof into its own test file with a deterministic, single-threaded harness (new **D15**). Both changes cascade: D2's "`viewModelScope` not used" row, D8's declaration count, D9's determinism story, D10's table, D14's freeze scope, **Steps 2 through 8**, File Scope (**+1 test file**, **+1 build-file line**), **nine revised acceptance criteria** (AC1, AC3, AC4, AC5, AC7, AC8, AC9, AC10, AC12) and **one new one** (**AC22**, the executable check C2 and C6 require) all move with them. The suite target moves **41 → 55**. Every edit is marked **[R1]** inline, and `## Plan — Revision 1` at the end of this file maps each of C1–C6 to what moved.

> **Measurement convention.** Every count, field name, line number and dependency claim below was **re-measured on this working tree at planning time** (`git diff HEAD origin/develop` empty, so the tree is byte-identical to `origin/develop` at `8e4c29217`), not carried from Research. Where planning measurement confirms Research, it is stated as confirmed; where it adds something Research did not have, it is marked as a **planning finding**. Step 1 re-derives the baseline from a real run regardless — these numbers are this plan's claim, not the developer's evidence.

### Objective

`concurrency` track plus the first MVVM state layer for `:ui:preferences`' Sync Settings slice. Three asynchronous call sites move off RxJava3/EventBus and onto Coroutines/Flow: the sticky `SyncServiceEvent` subscription in `SynchronizationPreferencesFragment` becomes a `StateFlow<SyncServiceEvent?>` owned by a new `SynchronizationPreferencesViewModel` and fed by a `callbackFlow` EventBus bridge whose registration is tied to collection **[R1]**, and the two orphaned RxJava3 `Disposable` subscriptions in `GpodderAuthenticationFragment` become `lifecycleScope` coroutines. The ViewModel additionally takes ownership of the screen's ActionBar title/subtitle state — the state holder Milestone 20 (`compose`) is blocked on — which collapses the five `(activity as AppCompatActivity?)!!.supportActionBar!!` statements to a single access site and takes the slice's `!!` inventory **49 → 41**. The milestone's verification premise is that Milestone 15's **41**-test suite stays green and **byte-identical** while proving equivalence; because Research measured that suite as having **zero** executable coverage of the two RxJava subscriptions, this plan writes that coverage **first, against the unmodified RxJava code** (Step 3), before any conversion touches it.

### Resolved Decisions

All 10 Research unknowns are resolved below: 1→**D5**, 2→**D6**, 3→**D3**, 4→**D9**, 5→**D8**, 6→**D10**, 7→**D11**, 8→**D12**, 9→**D4**, 10→**D13**. Five decisions have no corresponding unknown: **D1** (branch/baseline), **D2** (ViewModel shape), **D7** (subscription ordering), **D14** (test-double strategy and the two-phase characterization contract), and **D15** (the cancellation-proof harness — added in Revision 1 to close C1) **[R1]**. **Two corrections to Research** are recorded (D2 and D6) — reported, not relitigated. One decision (**D11**) carries a non-blocking Open Question for José.

---

**D1 — Branch fresh from a fetched `origin/develop`; re-measure the baseline before writing a line. No repo diff.**

Confirmed at planning time: `git log --oneline -1 origin/develop` → `8e4c29217` ("toolchain/ui-preferences: catalog and wire Hilt, Compose, and coroutines prerequisites (Milestone 16) (#31)"); `git merge-base --is-ancestor 8e4c292174897b75d7b9e1609c595717953a004e origin/develop` → true; `git status --short` shows only this task file, its checkpoint, and Milestone 16's two throwaway keystores as untracked. The milestone branch is **not** cut. `AGENTS.md` forbids committing on `develop`/`master`.

Step 1 re-derives three baselines and records them as the developer's frozen reference — **not copied from this plan**:

| Baseline | This plan's measured claim (planning time) |
|---|---|
| Sync-settings test suite | **11 files, 8 test classes, 41 `@Test`** — Gpodder 9, SyncPrefs 8, Nextcloud 6, HarnessSmoke 6, AuthDialog 4, Lifecycle 4, ScreenshotCapture 3, JavaInterop 1. Three support files: `RecordingSynchronizationQueue.kt`, `SyncSettingsTestHost.kt`, `SyncSettingsCaptureHost.kt` |
| `!!` inventory (`grep -o '!!' <file> \| wc -l`) | `SynchronizationPreferencesFragment.kt` **30**, `GpodderAuthenticationFragment.kt` **12**, `NextcloudAuthenticationFragment.kt` **7**, `AuthenticationDialog.kt` **0** = **49** |
| Resolved dependencies | `./gradlew :ui:preferences:dependencies --configuration playDebugCompileClasspath` and `--configuration playDebugUnitTestRuntimeClasspath`, saved outside the repo. Records `androidx.lifecycle:lifecycle-runtime-ktx` and `lifecycle-viewmodel-ktx` at their currently-transitive resolved versions (Research measured **2.9.4**) |

Any divergence is a **finding recorded before proceeding**, not a number to quietly adjust.

---

**D2 — Plain `ViewModel`, no factory, `StateFlow` exposure. This is a new pattern for `:ui:preferences`, and it is recorded as one rather than dressed up as following `BugReportViewModel`.** (Correction to the pre-research context block, confirming Research finding 4.)

The pre-research block instructed "a plain `ViewModel`/`AndroidViewModel` with a hand-written `ViewModelProvider.Factory` … the same shape `BugReportViewModel` uses". Research falsified the second half and planning re-verified it: `BugReportFragment.java:46` uses `new ViewModelProvider(this).get(BugReportViewModel.class)` with the **default** factory, and **no hand-written `ViewModelProvider.Factory` exists anywhere in `:ui:preferences`**. `BugReportViewModel` is also Java, `AndroidViewModel`, `MutableLiveData`, RxJava-driven, and has zero tests.

The shape this milestone builds, and why each choice diverges or does not:

| Aspect | Decision | Why |
|---|---|---|
| Base class | **`ViewModel`**, not `AndroidViewModel` | It needs no `Application`. All string formatting stays in the fragment (D12) precisely so `DateUtils.getRelativeDateTimeString` keeps using the **fragment's** `Context` — the Activity context carries configuration overrides the Application context does not, and `updateLastSyncReport`'s output is a locale-formatted relative time. Passing an `Application` here would be a silent behavior change on a formatted user-visible string. |
| Factory | **None — default `ViewModelProvider(this)`** | The ViewModel takes no constructor arguments (D4). A hand-written factory with nothing to inject is ceremony, and it would be a new pattern introduced with no consumer. Milestone 18 introduces the factory *when it introduces the dependency*, via `@HiltViewModel`. |
| Acquisition | **`ViewModelProvider(this)[SynchronizationPreferencesViewModel::class.java]`**, fragment-scoped | Matches `BugReportFragment.java:46` exactly. **Planning finding:** `by viewModels()` is **not available** — the catalog declares `androidx-fragment = androidx.fragment:fragment 1.8.9` (`libs.versions.toml:26`), the non-`-ktx` artifact. Adding `fragment-ktx` for one delegate is catalog churn this milestone does not need. |
| State exposure | **`StateFlow`**, not `LiveData` | Deliberate divergence from `BugReportViewModel`. The track is `concurrency`; Milestone 20 will want `collectAsStateWithLifecycle` over a `StateFlow`; and `app-wearos/MainViewModel.kt:22-23` (`private val _uiState = MutableStateFlow(...)` / `val uiState: StateFlow<...> = _uiState`) is the repo's only Coroutines/StateFlow ViewModel and the shape this copies. |
| State model | **Immutable `data class` + `sealed interface`**, emitted by `copy()` | Deliberate divergence from `BugReportViewModel.UiState`, which is **mutable** and re-posts the same reference (`setCrashLogState`, `:194-203`). A `StateFlow` re-posting an identical reference emits nothing — the mutable pattern is actively broken under `StateFlow` and must not be copied. |
| `viewModelScope` | **Used — as `stateIn`'s sharing scope, and for nothing else** **[R1]** | *Revision 1 reversal.* The original plan said "not used", because D5's bridge was a plain callback writing to a `MutableStateFlow`. C2 replaced that bridge with `callbackFlow { … }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), null)` (see D5), whose sharing coroutine runs in `viewModelScope`. No `viewModelScope.launch` is written by hand anywhere in this milestone — the only coroutine the ViewModel owns is the one `stateIn` starts, and it exists solely to keep EventBus registration alive exactly while a collector is subscribed. This is what makes `androidx-lifecycle-viewmodel-ktx` a required declaration (D8). Milestone 18 is still where hand-written `viewModelScope.launch` arrives, when the repository seam makes reads suspending. |

Both divergences (`StateFlow` over `LiveData`, immutable state over mutable) become `:ui:preferences` README conventions in Step 9, so the module ends with **one** stated convention rather than two contradictory precedents.

---

**D3 — The ViewModel is scoped to `SynchronizationPreferencesFragment` only. `GpodderAuthenticationFragment` keeps all five wizard fields and converts its two subscriptions to `lifecycleScope` in place. The 41-test regression net stays byte-identical.** (Unknown 3 — the milestone's central design decision.)

Re-verified at planning time, `GpodderAuthenticationFragmentCharacterizationTest.kt:63-80`: the suite reaches into `GpodderAuthenticationFragment` by reflected field and method name via `field(name)` / `method(name)` helpers. **Six** of its nine tests do so — `testDeviceNameGenerationDedupesByIdAndByCaption` (`devices`), `testCredentialCommitOrderOnDeviceToFinishTransition` (`username`, `password`, `devices`, `selectedDevice`, `currentStep`), `testPartialWizardLeavesCredentialsUntouched` (same), `testFinishStepDismissesBeforeSyncing` (same), plus `testFirstStepIsSetUpWithoutFlipping` and `testHostStepClearsCredentialsAndQueueBeforeSettingHostUrl` (`currentStep`). Moving `devices`/`username`/`password`/`selectedDevice`/`currentStep` into a ViewModel fails all six at `getDeclaredField`, **before any assertion runs**.

**Decision: option (a) — narrow the ViewModel to the preferences screen.** Three reasons, in order of weight:

1. **The regression net is the milestone's entire verification instrument.** Research measured that the async surface being converted has *zero* executable coverage. The only thing standing between "we converted three async call sites" and "we can prove we converted them without changing behavior" is a suite that is green because it was green before, on code it did not change. Editing six of its tests inside the same diff that rewrites the machinery they guard destroys that instrument. This is the same rule Milestone 18 inherits verbatim from the future-work file (`:53-59`) and the same rule Milestone 16 enforced with `--diff-filter=M`.
2. **What Milestone 20 was blocked on — the absence of *any* state holder — stops being true, but Milestone 20's own state-hoisting work is untouched. [R1]** *Revision 1 correction, closing C5: the original wording ("the blocking prerequisite this milestone owes Milestone 20 is satisfied") overstated the delivery and is withdrawn.* What this milestone actually hands Milestone 20 is **the class, the `StateFlow` + immutable-state pattern, the collection seam, and the module conventions that fix that pattern in place** — i.e. `compose` no longer has to invent an architecture before it can start. What it does **not** hand over is the screen's state model: the ViewModel holds **only** the ActionBar title/subtitle and the sync event. `updateScreen()` and everything it renders — `isProviderConnected()`, the provider summary and icon, username/host-url, per-row enabled/visible state — stay in the fragment, read on demand from `:storage:preferences` statics, and **hoisting them into `SyncSettingsUiState` remains Milestone 20's work** (Step 10 writes exactly that into the Milestone 20 row). The narrowing to `SynchronizationPreferencesFragment` is separately justified by the future-work file's Milestone 20 row (`:65`), which scopes `compose` to "Replace `PreferenceFragmentCompat` + `preferences_synchronization.xml` with a `ComposeView`" — i.e. this fragment. Whether the three dialogs move to Compose at all is listed there as an **open question, not a settled requirement** (`:67`), so a Gpodder-wizard ViewModel would be a prerequisite for a scope decision nobody has made yet.
3. **The wizard's state is a 4-step `ViewFlipper` cursor, not screen state.** `currentStep` drives `viewFlipper.showNext()` and the `when` in `advance()` (`:234-268`). Hoisting a View-system flipper index into a ViewModel produces a state holder shaped by the widget it is supposed to be freeing the screen from — a rewrite Milestone 20 would redo, not build on, if the dialogs go Compose.

**The tradeoff, stated plainly rather than buried.** After this milestone, "the Sync Settings slice has an MVVM layer" is true of **one of four files**, and within that one file it is true of **the ActionBar and the sync event only** — not of the screen body **[R1]**. `GpodderAuthenticationFragment` keeps `@Volatile private var username/password/selectedDevice` and `private var devices` as fragment fields, mutated from a coroutine instead of from an Rx callback. That is a smaller MVVM footprint than the milestone description's "stand up the ViewModel/UI-state layer this slice doesn't have today" reads as promising, and Step 10 writes that narrowing into the future-work file's Milestone 20 row so it is inherited rather than rediscovered. **If Milestone 20 later decides the dialogs go to Compose, a Gpodder wizard ViewModel is new scope for that milestone** — and at that point the reflection-driven tests are being rewritten *anyway*, because the Views they drive no longer exist. That is the right milestone to pay that bill in, because there the suite rewrite is forced by the UI rewrite rather than colliding with an equivalence proof.

---

**D4 — The ViewModel takes no constructor dependencies. The five `SynchronizationQueue.instance!!` sites stay. `RecordingSynchronizationQueue` keeps working untouched.** (Unknown 9.)

Research flagged that a hand-written factory *could* take `SynchronizationQueue` and eliminate the three `SynchronizationQueue.instance!!` sites in `SynchronizationPreferencesFragment` without Hilt. Rejected, on the same principle as D3: taking that parameter means this milestone defines the dependency seam the future-work file assigns to Milestone 18 (`:51`), inside the diff whose job is to prove three async conversions changed nothing. It also means every one of the eight test classes that installs `RecordingSynchronizationQueue` via the global (`SynchronizationQueue.instance = recordingQueue`) now has a *second*, differently-shaped injection path for the same object in the same module — which is how a seam becomes two seams.

`SynchronizationQueue.instance!!` therefore stays at all five sites (3 in `SynchronizationPreferencesFragment.kt:93,97,102`, 2 in `GpodderAuthenticationFragment.kt:79,230`), and all five preference-row click listeners stay in the fragment. Milestone 18 owns them.

---

**D5 — The `SyncServiceEvent` Flow is a local EventBus→`StateFlow` bridge inside the new ViewModel, in `:ui:preferences`. `:net:sync:service`'s nine producers are not touched.** (Unknown 1 — Research's own recommendation, adopted.)

Research measured 9 `postSticky` sites and **0** `removeStickyEvent` sites in production code, 8 of them inside `SyncService.doWork()` on a WorkManager thread. Options (b) and (c) both cross a module boundary: (b) rewrites all 9 producers and requires auditing every other consumer of the sticky bus; (c) needs new EventBus *and* coroutines wiring in `:event` or `:net:sync:service-interface`, neither of which declares either today. Both are a materially larger blast radius than a milestone whose File Scope is one screen and one dialog.

**Concrete shape — rewritten in Revision 1 to close C2. [R1]**

The original D5 registered the subscriber for the ViewModel's whole lifetime (`init` → `onCleared`) and argued equivalence from "all 9 producers post stickily". **That argument does not hold, and the red-team is right.** A `sticky = true` subscriber receives **non-sticky** posts too, and the frozen regression suite posts non-sticky `SyncServiceEvent`s at `SynchronizationPreferencesFragmentLifecycleTest.kt:81, :97, :102` (verified in Revision 1 by reading the file — `EventBus.getDefault().post(...)`, not `postSticky`). So the sticky/non-sticky distinction is live inside the verification instrument, not hypothetical, and lifetime-registration diverges from the old code for any event delivered while the fragment is stopped: the old fragment is unregistered and misses it forever, whereas a lifetime-registered bridge would keep mutating the state and replay that missed event to the next collector. Deferring that to a README convention was not good enough — a convention is not a guard.

**Adopted: the red-team's shape, registration tied to collection.**

```
class SynchronizationPreferencesViewModel : ViewModel() {

    private class SyncServiceEventSubscriber(private val onEvent: (SyncServiceEvent) -> Unit) {
        @Subscribe(threadMode = ThreadMode.POSTING, sticky = true)
        fun onSyncServiceEvent(event: SyncServiceEvent) = onEvent(event)
    }

    val syncStatus: StateFlow<SyncServiceEvent?> = callbackFlow {
        val subscriber = SyncServiceEventSubscriber { trySend(it) }
        EventBus.getDefault().register(subscriber)
        awaitClose { EventBus.getDefault().unregister(subscriber) }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
        null
    )
}
```

Five properties are load-bearing and must not be "tidied":

- **`WhileSubscribed`, and therefore `register()` at collector-subscribe / `unregister()` at collector-stop.** The fragment's collector starts in `onStart` at the exact source position `register()` occupies today and is cancelled in `onStop` at the exact position `unregister()` occupies today (D7). So EventBus registration now has **the same lifetime it has on `develop`** — per `onStart`/`onStop`, not per ViewModel. The stopped-state divergence C2 identified is gone by construction rather than by convention, and it is pinned by two new Step-4 tests (below).
- **`stopTimeoutMillis = 0`.** Any non-zero value would keep the subscriber registered past `onStop` and reintroduce exactly the divergence this decision exists to remove. `WhileSubscribed` skips the delay entirely at zero, so unregistration is not deferred through a timer.
- **`replayExpirationMillis = 0` — this one is easy to omit and it is the subtle half of the fix.** The default (`Long.MAX_VALUE`) keeps the cached value across a stop/start, so on the next `onStart` the collector would immediately replay the **last value from the previous session** before re-registration replays the sticky. If that last value was a non-sticky event, the divergence returns through the cache instead of through the registration. At zero, `stateIn`'s `StateFlow` **resets to its `initialValue` (`null`)** when the upstream stops, so a restarted screen shows nothing until EventBus itself replays the sticky — which is precisely what `develop` does.
- **A separate `SyncServiceEventSubscriber` object, not the ViewModel.** EventBus subscribes by reflecting `@Subscribe`-annotated methods on the registered instance; a `callbackFlow` lambda cannot carry the annotation. Making the subscriber a short-lived private object also means **nothing about EventBus outlives the collector** — there is no ViewModel-lifetime registration left to leak.
- **`ThreadMode.POSTING`, not `MAIN`.** Unchanged from the original D5 and still load-bearing. Today EventBus performs the background→main hop for all 8 `SyncService` posts; under this bridge the hop moves to the collection point, which is `Dispatchers.Main.immediate` via `lifecycleScope` (D7) — one hop, same as today, but explicit and located where a `compose` collector would also put it. `POSTING` is also what makes `register()`'s sticky replay reach `trySend` **synchronously on the registering thread**, which is what preserves the replay-before-collection ordering `testStickyEventReplaysOnStart` asserts.

**The ordering claim is a verification gate at Step 5, not an assumption. [R1]** For `testStickyEventReplaysOnStart` to stay green, `register()` and its synchronous sticky replay must both happen **before `onStart()` returns**, so that the replay overwrites the subtitle `updateActionBar()` just wrote. With everything on `Dispatchers.Main.immediate` and `launch` called from Robolectric's main thread, the coroutine machinery starts the collector inline and drains the thread-local unconfined event loop — which starts `stateIn`'s sharing coroutine, runs the `callbackFlow` block, and delivers the replayed event — before `launch` returns. **That is a reasoned expectation about coroutine internals, not a measured fact, and this plan does not treat it as one.** Step 5 runs the four frozen `SynchronizationPreferencesFragmentLifecycleTest` tests as its gate; if `testStickyEventReplaysOnStart` or `testOnStartSetsTitleAndOnStop…` goes red, the developer does **not** adjust the frozen test and does **not** improvise:

> **Named fallback, pre-authorised, same files and same acceptance criteria.** The ViewModel exposes `fun startObserving()` and `fun stopObserving()` which register/unregister the same `SyncServiceEventSubscriber` **synchronously** against a `MutableStateFlow<SyncServiceEvent?>` reset to `null` on stop; the fragment calls them at the exact `register()`/`unregister()` source positions, and the collector still renders. This is equally equivalence-preserving on the stopped-state question (registration is still tied to `onStart`/`onStop`) and removes all dependence on `stateIn` start-up timing; it costs the idiomatic Flow shape and one more public method. Choosing it is a **Step-5 finding recorded in Implementation Notes with the failing test output that forced it**, not a silent substitution, and every Step-4 test above applies unchanged (they exercise the same subscribe/unsubscribe semantics through whichever mechanism ships).

**C6 — cross-test EventBus accumulation, analysed rather than assumed. [R1]** The red-team is right that `onCleared()` never runs in the frozen Robolectric tests (the activity is built and stopped, never destroyed), so a ViewModel-lifetime registration would accumulate dead subscribers on the process-global `EventBus.getDefault()` for the whole suite run. **Under the adopted shape the question is largely moot**, because registration is no longer tied to `onCleared()` at all — it ends when the collector is cancelled, i.e. at `onStop`, which the frozen tests **do** reach (`controller.pause().stop()` in `testOnStartSetsTitleAndOnStop…`). For the tests that never stop the fragment, a bridge stays registered for the rest of the JVM run — **and this is exactly what `develop` already does**: the current fragment registers in `onStart` and, never being stopped or destroyed, never unregisters either. The accumulation is therefore **pre-existing, unchanged in kind and unchanged in count** (one subscriber per attached fragment either way), which is why the suite is green with it today. Its only observable effect is that a stale bridge writes to a stale `StateFlow` read by a stale collector rendering into a stale activity's ActionBar — never the ActionBar the running test asserts on. AC1's per-class counts are the check that this stays true, and Step 5 records the analysis rather than leaving it implicit.

---

**D6 — `MutableStateFlow<SyncServiceEvent?>(null)`, not `SharedFlow(replay = 1)`. Confirmed safe against conflation by a planning finding Research did not have.** (Unknown 2.)

Research argued `StateFlow` with a `null` initial value models "no sticky posted yet" more faithfully than a replay-1 `SharedFlow`, and that the never-removed sticky means no clear path is needed. Adopted. `replay = 0` is ruled out by `testStickyEventReplaysOnStart`.

**Planning finding — the conflation hazard Research flagged implicitly does not exist here.** `StateFlow` drops emissions that are `equals()` to the current value. Measured at planning time, `event/src/main/java/de/danoeh/antennapod/event/SyncServiceEvent.kt` reads in full:

```
class SyncServiceEvent(val messageResId: Int)
```

It is a **plain class, not a `data class`** — no `equals` override, so equality is reference identity. Every one of the 9 producers constructs a fresh instance, so two consecutive posts of the same `messageResId` are two distinct, unequal values and **both** emit, exactly as EventBus delivers both today. `testSubtitleBranchesOnMessageResId` depends on consecutive distinct posts being separately observed; it passes for this reason and not by luck. **This is the one property that would silently break if `SyncServiceEvent` were ever converted to a `data class`** — Step 10 records it as a convention in `:ui:preferences`' README, since the risk lands here and not in `:event`.

**How that property is pinned, since `.value` cannot see it. [R1]** *Revision 1, closing C3.* D9's original claim that every ViewModel-side test is "a plain `.value` assertion" was wrong for this one test, and the red-team caught it: a dropped emission is invisible to a `.value` read, because the final value is the same either way. `testConsecutiveEventsWithSameMessageResIdAreBothDelivered` (renamed from `…BothEmit` to say what it actually observes) therefore **collects the stream into a list**, and does so without `kotlinx-coroutines-test`:

```
val seen = mutableListOf<SyncServiceEvent?>()
val scope = CoroutineScope(Dispatchers.Main.immediate)
val job = scope.launch { viewModel.syncStatus.collect { seen += it } }
shadowOf(Looper.getMainLooper()).idle()
EventBus.getDefault().post(SyncServiceEvent(R.string.sync_status_started))
shadowOf(Looper.getMainLooper()).idle()
EventBus.getDefault().post(SyncServiceEvent(R.string.sync_status_started))
shadowOf(Looper.getMainLooper()).idle()
job.cancel()
// seen == [null, event1, event2] — three entries, the two events distinct instances
```

Determinism comes from the same two things the frozen suite already relies on: a single thread (`Dispatchers.Main.immediate` on Robolectric's main looper) and explicit `idle()` pumping between posts. The test is `@RunWith(RobolectricTestRunner::class)` — mandatory for the whole ViewModel test class, because `Dispatchers.Main` needs an Android main looper to initialise at all (D9).

**The honest limit of that pin, and the non-equivalence it exposes. [R1]** *Revision 1, closing C4.* What the test proves is that **posts delivered sequentially** — the main thread returning to idle between them — are each observed. It does **not** prove per-post delivery in general, and cannot, because `StateFlow` is conflated by contract: two posts arriving faster than the collector resumes coalesce into one rendered subtitle, where EventBus's `ThreadMode.MAIN` would have dispatched two. This is a real, recorded difference between the old mechanism and the new one — see **D10 row (e)**, where it sits alongside D11's `CompositeException`→NPE note rather than being left implicit.

---

**D7 — Subscription ordering is preserved exactly. `repeatOnLifecycle` is rejected; collection is an explicit `Job` started and cancelled at the precise source positions `register()`/`unregister()` occupy today.** (Research hazard 4.)

The order is behaviorally load-bearing in both directions, and both directions are pinned:

- `onStart()` (`:39-45`) — `super.onStart()` → set title → `updateScreen()` → `updateActionBar()` → **`register()` last**. `testStickyEventReplaysOnStart` sets up a *connected, successfully-synced* provider and posts a sticky `sync_status_started` **before** attach, then asserts the subtitle is the raw `sync_status_started` string. That assertion passes only because `updateActionBar()` writes the "successful (relative time)" report **first** and the sticky replay **overwrites** it. Registering earlier inverts that and fails the test.
- `onStop()` (`:47-52`) — `super.onStop()` → **`unregister()` first** → subtitle `= ""`.

`repeatOnLifecycle(STARTED)` starts and stops collection at lifecycle-observer dispatch points, not at these source positions, so it is not a like-for-like swap and is **not used anywhere in this milestone**. Instead:

```
private var syncStatusJob: Job? = null
// onStart, at the exact line register() occupies today:
syncStatusJob = viewLifecycleOwner.lifecycleScope.launch {
    viewModel.syncStatus.collect { event -> if (event != null) syncStatusChanged(event) }
}
// onStop, at the exact line unregister() occupies today:
syncStatusJob?.cancel(); syncStatusJob = null
```

`lifecycleScope` uses `Dispatchers.Main.immediate`, so when `launch` is called from the main thread the collector starts **synchronously** before `onStart` returns — matching `register()`'s synchronous sticky replay. The `if (event != null)` guard is what makes `StateFlow`'s mandatory initial emission mean "no sticky yet", i.e. what EventBus expresses by delivering nothing. The `syncStatusChanged` body is **not touched** in this step (D-note: it changes in Step 6 for the ActionBar render only).

**What Revision 1 changed here: nothing in the shape, everything in what it now carries. [R1]** The red-team cleared D7 as sound "subject to C2's registration-lifetime point", and C2's fix lands precisely inside it. Under the new D5 bridge, `syncStatusJob = viewLifecycleOwner.lifecycleScope.launch { … collect … }` is not only where **rendering** starts — it is now also where **EventBus registration** starts, because `stateIn(SharingStarted.WhileSubscribed(0, 0))` starts the upstream `callbackFlow` when its first collector subscribes. Symmetrically, `syncStatusJob?.cancel()` in `onStop` is what unregisters. So the two source positions this decision fought to preserve now carry the exact semantics they carried on `develop`: `register()` at the last line of `onStart`, `unregister()` before the subtitle clear in `onStop`. The one thing this makes load-bearing that was not before is `stateIn`'s start-up timing, which is why D5 turns that into a Step-5 verification gate with a named fallback instead of an argument.

---

**D8 — Exactly one new catalog entry — `androidx-lifecycle-runtime-ktx`, on the existing `lifecycle-runtime-compose` version ref — and exactly two `implementation` declarations on `:ui:preferences`, the second being the already-catalogued `androidx-lifecycle-viewmodel-ktx` **[R1]**. Nothing else is added to the catalog.** (Unknown 5.) *(Revision 1 changed the declaration count from one to two — see the reversal below — while leaving the catalog at one line.)*

`lifecycleScope` is used in both fragments and ships in `androidx.lifecycle:lifecycle-runtime-ktx`, which Research measured as reaching `:ui:preferences` **transitively only** and as **not catalogued at all**. Relying on an undeclared transitive for a core API of this milestone is the fragility Research named; it is fixed.

The entry follows the file's own existing precedent rather than inventing one — `libs.versions.toml:59` already declares `androidx-lifecycle-viewmodel-ktx` against `version.ref = "lifecycle-runtime-compose"`:

```
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle-runtime-compose" }
```

That ref pins **2.8.7** while the transitive graph already resolves lifecycle to **2.9.4**; Gradle resolves conflicts upward, so the declared entry is a **provable no-op on the resolved version** — AC6 requires the Step-1/Step-10 dependency diff to show `lifecycle-runtime-ktx` and `lifecycle-viewmodel-ktx` still at their Step-1 resolved version, and any movement is a finding that re-opens this decision. (The ref *name* is a pre-existing misnomer — it governs three non-Compose artifacts. Renaming it touches `app-wearos`; recorded as **OQ2**, non-blocking, not done here.)

**`androidx-lifecycle-viewmodel-ktx` *is* now declared — Revision 1 reversal. [R1]** The original D8 left it undeclared on the grounds that "`viewModelScope` is not used (D2)". C2's fix makes that false: `stateIn(viewModelScope, …)` is the sharing scope of the new bridge, and `viewModelScope` is an extension property that ships in `androidx.lifecycle:lifecycle-viewmodel-ktx`. Relying on it as an undeclared transitive would be the same fragility this decision exists to fix, one artifact over.

It needs **no new catalog entry** — `androidx-lifecycle-viewmodel-ktx` is already declared at `libs.versions.toml:59` on the same `lifecycle-runtime-compose` ref (verified in Revision 1) and is currently wired only into `app-wearos/build.gradle:77`. So the catalog still moves by exactly one line; `ui/preferences/build.gradle` moves by **two** `implementation` lines instead of one:

```
implementation libs.androidx.lifecycle.runtime.ktx
implementation libs.androidx.lifecycle.viewmodel.ktx
```

**Step 2 still runs the `dependencyInsight` probe, now as a no-movement check rather than a decision point**: `./gradlew :ui:preferences:dependencyInsight --configuration playDebugCompileClasspath --dependency androidx.lifecycle:lifecycle-viewmodel` is captured before and after, and must show the same resolved version (Research measured **2.9.4**) with the declaration added — the same provable-no-op property AC6 requires of `lifecycle-runtime-ktx`. Any version movement re-opens this decision.

---

**D9 — `kotlinx-coroutines-test` is **not** catalogued. Determinism comes from an injectable dispatcher field plus Robolectric's existing `Looper` idling. Turbine, MockWebServer and `mockito-core` are likewise not added.** (Unknown 4.)

Research is right that testing an arbitrary `viewModelScope` coroutine deterministically needs `runTest`/`TestDispatcher`. This milestone's design removes that need rather than working around its absence. **Revised in Revision 1 [R1]:** the original first bullet claimed "the ViewModel launches no coroutine at all" and "read as `.value` — a plain JUnit assertion". C2's bridge launches one (`stateIn`'s sharing coroutine) and C3 showed one test must observe the stream. Both claims are corrected below; the conclusion — **no `kotlinx-coroutines-test`** — survives, because determinism here comes from *single-threadedness*, which the design still guarantees, not from a test framework.

Every coroutine this milestone starts is confined to one thread, and every one of them has an explicit pump or an explicit release:

- **The ViewModel's only coroutine is `stateIn`'s sharing coroutine, on `viewModelScope` = `Dispatchers.Main.immediate`.** Under Robolectric that is `kotlinx-coroutines-android`'s `HandlerContext` over the main looper — the same looper the frozen suite already drives with `shadowOf(Looper.getMainLooper()).idle()`. No `Dispatchers.setMain` is required, and none is used. **`SynchronizationPreferencesViewModelTest` is therefore `@RunWith(RobolectricTestRunner::class)`**, not a plain JUnit class: `Dispatchers.Main` cannot initialise without an Android main looper. That is a hard requirement, not a preference.
- **ViewModel assertions are `.value` reads *except where the property under test is about the stream or the subscription*.** Of the seven Step-4/6 tests **[R1]**, **five** read `.value` after an `idle()`; `testConsecutiveEventsWithSameMessageResIdAreBothDelivered` collects into a list from a `Dispatchers.Main.immediate` scope and cancels the job before asserting (mechanism written out in full in D6); and `testCollectorCancellationUnregistersFromEventBus` asserts on `EventBus.getDefault().hasSubscriberForEvent(...)` rather than on state at all (AC22). **This is the C3 correction, stated where the wrong claim was made.** All seven start a collector before observing anything — under `stateIn(WhileSubscribed)` there is no upstream, and therefore no registration and no emission, until something subscribes.
- **The fragment-side collectors run on `Dispatchers.Main.immediate` via `lifecycleScope`** and are pumped by the same existing `idle()` calls.
- **The two Gpodder coroutines' `Dispatchers.IO` is the only genuinely non-deterministic piece**, and it is solved by a seam, not a framework: `GpodderAuthenticationFragment` gains `private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO`, which the new tests set **by reflection** — the same `field(name).set(fragment, value)` idiom the suite already uses for `devices`/`username`/`password`/`selectedDevice` (`:71-72`). `private var`, not `val`, so the backing field is non-final and reflective assignment is not relying on final-field write semantics. **No visibility is widened and no production API changes** — the field is private and the dispatchers come from `kotlinx-coroutines-core`, already declared at `ui/preferences/build.gradle:50`.
- **The seam is set to two different values for two different jobs. [R1]** `Dispatchers.Unconfined` for the six async characterization tests, where running the whole coroutine inline inside `performClick()` is exactly what makes the success/error assertions deterministic; and a hand-written **`ManualDispatcher`** for the cancellation proof, where the point is to *hold* the coroutine mid-flight. Both are single-threaded and neither needs a latch or a real background thread — which is how this plan avoids Research hazard 5's flakiness rather than accepting it. See **D15**.
- **`mockito-core` is catalogued** (`libs.versions.toml:92`) but not declared on this module, and is not added. Test doubles here are hand-written, matching `RecordingSynchronizationQueue`'s established convention in this exact source set (D14).

The cost is disclosed, not hidden: `kotlinx-coroutines-test` **will** be needed once Milestone 18 makes the ViewModel's reads suspending behind an injected repository. Step 10 records that against Milestone 18 in the future-work file, so it is inherited as a known bill rather than rediscovered as a surprise.

---

**D10 — Preserve-or-fix, decided per behavior, per `net/sync/service-interface/README.md` convention #11.** (Unknown 6.) **Five** decisions **[R1]** — four preserve-or-fix calls plus one recorded non-equivalence added in Revision 1 — each with the mechanism that makes it checkable:

| # | Behavior | Decision | Rationale and mechanism |
|---|---|---|---|
| **a** | The two discarded RxJava `Disposable`s — work runs to completion and writes to detached views after the host is gone | **FIXED — deliberate behavior change, not equivalence** | Structurally forced: `lifecycleScope` cancels at `Lifecycle.DESTROYED` by construction, so "survives the host" is not expressible in the target API. **Revised in Revision 1 [R1]:** proved by a *pair* of tests on the same scenario in their own file, `GpodderAuthenticationFragmentCancellationTest.kt` — `testInFlightLoginSurvivesDialogDismissal` green at Step 3 **against the unmodified RxJava code** (the "before" record), replaced at Step 7 by `testInFlightLoginIsCancelledWhenDialogIsDismissed` green against the coroutine code. The file's Step-7 diff is the artifact. Mechanism, determinism and the honest limit of the "fails on old code" claim are in **D15**. Also load-bearing and easy to get wrong: the converted `catch` **must rethrow `CancellationException`** or a cancelled login renders the error branch and this fix silently does not happen. |
| **b** | The `devices` field data race — non-`@Volatile`, written on an io thread, read on main | **PRESERVED, and preserved exactly** | `devices = service!!.getDevices()` moves inside `withContext(ioDispatcher) { … }`, so the write still happens on a background thread and the main-thread resumption still supplies the same incidental happens-before edge `observeOn` supplies today. **`AC12`'s `grep -c '@Volatile'` → exactly 3 is unchanged and does not need retiring** — the field stays a fragment field (D3), so the "make it ViewModel `StateFlow<List<GpodnetDevice>>` and the race evaporates" route Research flagged is not taken. `ui/preferences/README.md` convention 5 stands as written. |
| **c** | Sticky-replay semantics of `SyncServiceEvent` | **PRESERVED** | `MutableStateFlow<SyncServiceEvent?>(null)` (D6) + collection started at the exact `register()` position (D7). Pinned by `testStickyEventReplaysOnStart`, unmodified. |
| **d** | `error.cause!!.message` (Gap 16) — reachable wrong-password crash | **FIXED under OQ1, 2026-09-01** (was: preserved here, handed to Milestone 18) | José resolved OQ1 "fix-here". Site A's `error.cause!!.message` became `error.cause?.message ?: error.message` in a separate post-equivalence commit after Step 8; `testWrongPasswordErrorPathThrowsFromNullCause` was inverted to `testWrongPasswordErrorRendersServerMessageWithNoCause` (asserts the rendered `"Wrong username or password"` server message, VISIBLE error view, re-enabled button, hidden progress bar, `currentStep == 1`). The `CompositeException`→NPE disclosure is now moot. See **D11** (superseded) and the Implementation Notes "Gap 16 fix" subsection. |
| **e** **[R1]** | **Per-post delivery of `SyncServiceEvent`.** EventBus `ThreadMode.MAIN` dispatches every `post`; a `StateFlow` is conflated by contract and a collector that has not resumed sees only the latest value | **PRESERVED IN PRACTICE, NOT IDENTICAL — recorded, not claimed equivalent** | *Added in Revision 1, closing C4.* Sequentially delivered posts — the main thread returning to idle between them, which is every case the frozen suite exercises and the overwhelming majority of real ones, since the 8 `SyncService.doWork()` posts are seconds apart — are each observed, pinned by `testConsecutiveEventsWithSameMessageResIdAreBothDelivered` (D6). Posts arriving faster than the collector resumes — realistically only `SynchronizationQueueImpl.kt:136`'s `sync_status_started` immediately followed by `SyncService.kt:61`'s `sync_status_wait_for_downloads` — **can** coalesce, so the old screen would tick through two subtitles where the new one may render one. No test pins that window because none can without a scheduler this milestone has decided not to add (D9). This is the same class of disclosure as D11's `CompositeException`→bare-NPE note: a difference that is real, bounded, argued to be acceptable, and **written down** rather than discovered later by a reader of the diff. |

Every one of these lands in Implementation Notes as an explicit preserve/fix line, not inferred from the diff.

---

> **SUPERSEDED — OQ1 resolved 2026-09-01, fix-here.** The "preserved, handed to Milestone 18" narrative below is history. Site A now reads `error.cause?.message ?: error.message`, pinned by the inverted `testWrongPasswordErrorRendersServerMessageWithNoCause`; Milestone 18 no longer inherits Gap 16. Shipped in a standalone follow-up PR after #32 was merged.

**D11 — Gap 16 is preserved in this milestone, pinned by a new executable test, and handed to Milestone 18 by name. Raised for José as OQ1 because "we shipped a known crash" has a portfolio dimension a planner should not settle alone.** (Unknown 7.)

Research established that `GpodnetServiceAuthenticationException("Wrong username or password")` (`GpodnetService.java:368`) is constructed with a message and no cause, so `error.cause!!` NPEs on the most common login failure and crashes the app. Confirmed at planning time from `GpodnetService.java:46-77` that a `GpodnetService` is cheaply constructible without network (its constructor only stores the client and parses the hostname), which is what makes the fake of D14 possible — **so the executable pin Milestone 15 could not write is available now.**

**Decision: preserve, and pin it.** Three reasons:

1. **A crash fix inside an equivalence-proving diff destroys the proof.** This milestone's claim is "three async call sites moved to coroutines and nothing observable changed." If the same diff also changes what the user sees on a wrong password, a green suite can no longer distinguish "the conversion is correct" from "the conversion altered the failure path" — which is exactly the confounding convention #11 exists to prevent.
2. **Preserving is not free either, and the difference must be recorded.** Research noted it: RxJava3's `LambdaObserver` routes a throw from the error consumer to `RxJavaPlugins.onError` as a `CompositeException`; a coroutine's `try`/`catch` re-throwing propagates a bare `NullPointerException` to the uncaught handler. The *crash* is preserved; the *exception type reaching the handler* is not. Pinning it executably (`testWrongPasswordErrorPathThrowsFromNullCause`, Step 3, re-run unchanged after Step 7) is what makes that visible in the record instead of latent.
3. **Milestone 18 is where the fix is verifiable end-to-end.** The one-line fix is `error.cause?.message ?: error.message`, and with the injected `GpodnetService` from the DI seam it can be asserted through the real failure path rather than through a reflected fake.

Step 10 writes Gap 16 into the future-work file's Milestone 18 row **by name, with the one-line fix and the test that will prove it** — so that after this milestone, when the code reads as modernized, the gap does not read as covered.

---

**D12 — The ActionBar `!!` reduction is 10 → 2 in `SynchronizationPreferencesFragment.kt`, and the slice goes 49 → 41. Committed as a number, with the grep that checks it.** (Unknown 8.)

Research honestly estimated "roughly 8 of 10, not 10", because one `activity as AppCompatActivity` access must survive. Confirmed at planning time — five statements, each carrying two `!!`:

```
:41   (activity as AppCompatActivity?)!!.supportActionBar!!.setTitle(R.string.synchronization_pref)
:51   (activity as AppCompatActivity?)!!.supportActionBar!!.subtitle = ""
:69   (activity as AppCompatActivity?)!!.supportActionBar!!.setSubtitle(event.messageResId)
:158  (activity as AppCompatActivity?)!!.supportActionBar!!.subtitle = null
:238  (activity as AppCompatActivity?)!!.supportActionBar!!.subtitle = status
```

Step 6 moves the *decision* of what the ActionBar should show into the ViewModel and leaves the *access* at a single site:

```
data class SyncSettingsUiState(
    @StringRes val titleRes: Int = R.string.synchronization_pref,
    val subtitle: SyncSubtitle = SyncSubtitle.Absent
)
sealed interface SyncSubtitle {
    data object Absent : SyncSubtitle
    data object Cleared : SyncSubtitle
    data class Message(@StringRes val resId: Int) : SyncSubtitle
    data class LastSyncReport(val successful: Boolean, val attemptedAt: Long) : SyncSubtitle
}
```

`Absent` renders `null`, `Cleared` renders `""` — this is what makes hazard 8's distinction explicit in the type rather than implicit in call order, and it is exactly what `testOnStartSetsTitleAndOnStopSetsEmptySubtitleWhereasDisconnectedSetsNull` discriminates on. `Message` and `LastSyncReport` are rendered **in the fragment**, because both need the fragment `Context` (`getString`, `DateUtils.getRelativeDateTimeString`) — see D2.

The single access site is one private helper:

```
private fun actionBar() = (activity as AppCompatActivity?)!!.supportActionBar!!
```

`onStop`'s clear calls it directly rather than routing through state, so that D7's "cancel **then** clear" order is preserved literally. **Result: 10 `!!` at 5 statements → 2 `!!` at 1 statement.** `SynchronizationPreferencesFragment.kt` goes **30 → 22**; `GpodderAuthenticationFragment.kt` stays at **12** (its `service!!`×4, `devices!!`×2, `SynchronizationQueue.instance!!`×2, `error.cause!!`, `context!!`, `dialog!!`, `selectedDevice!!` are all either D4-deferred, D10-preserved, or Fragment-API preserved NPEs under README convention 4); `NextcloudAuthenticationFragment.kt` **7** and `AuthenticationDialog.kt` **0** are not touched at all. **Slice total 49 → 41, checked by AC13's per-file grep.** *(The `error.cause!!` here later became `error.cause?.message ?: error.message` in the OQ1 follow-up fix — see AC13 / AC14 — taking this file to 11 and the slice to 40.)* The 11 `findPreference!!` sites are a `PreferenceFragmentCompat` API artifact and die with Milestone 20, not with a ViewModel — untouched, as Research concluded.

---

**D13 — No new screenshot is captured, and no pixel changes. `SyncSettingsScreenshotCaptureTest` stays byte-identical and green.** (Unknown 10.)

Confirmed at planning time: its three tests (`testCapturedBitmapIsNotBlankAndHasExpectedDimensions`, `testFirstPreferenceRowIsNotClippedByActionBar`, `testWritesPngUnderModuleBuildDirectory`) render through `SyncSettingsCaptureHost` with **no** sticky event posted and **no** provider connected. On that path the subtitle is `null` before this milestone (`updateActionBar`'s disconnected branch, `:158`) and `SyncSubtitle.Absent` → `null` after it (D12) — the same ActionBar geometry, so `testFirstPreferenceRowIsNotClippedByActionBar` is unaffected. Milestone 15b's before-image (`sync_settings_before_milestone_15b.png`) remains the slice's before-shot; the after-shot belongs to Milestone 20, which is the milestone that changes pixels. AC15 states this as a criterion rather than leaving it as an assumption.

---

**D14 — Test doubles are hand-written, and the async characterization tests are written in two phases with an assertion contract that spans the conversion.**

**The double.** `GpodnetService` is a non-final public Java class (`GpodnetService.java:46`) whose constructor stores an `OkHttpClient` and parses a hostname — no I/O, no cache, so it does **not** hit README convention 6's `AntennapodHttpClient.getHttpClient()` NPE. A new test-only `FakeGpodnetService.kt` therefore extends it with `OkHttpClient()` (okhttp is already an `implementation` dependency, so it is on the test compile classpath) and overrides the four methods the two subscriptions call — `setCredentials`, `login`, `getDevices`, `configureDevice` — recording calls in order and optionally throwing a supplied `Throwable`. It is installed with `field("service").set(fragment, fake)`, the suite's existing reflection idiom. No mocking framework, matching `RecordingSynchronizationQueue`'s precedent in this source set.

**The two-phase contract — this is the part a reviewer must check line by line.** A characterization test that is written *after* the conversion characterizes the new code, not the old. So:

- **Step 3 writes `GpodderAuthenticationFragmentAsyncCharacterizationTest` against the unmodified RxJava production code**, made deterministic with `RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }` and `RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }` in `@Before`, reset in `@After`. It must be **green before one line of production code changes**. That run is the record of current behavior.
- **Steps 7 and 8 convert.** The test's `@Before`/`@After` swap the Rx plugin handlers for `field("ioDispatcher").set(fragment, Dispatchers.Unconfined)`. **Its assertions do not change.**
- **AC9 is the enforcement**: `git diff` of that file between its Step-3 commit and its Step-8 state must touch **only** import lines and the `@Before`/`@After` bodies. **Zero lines containing `assert` may change** in the **six Step-3 test bodies** **[R1]**. A changed assertion means the conversion changed behavior and the diff was papered over. *(Revision 1 narrowed AC9's freeze from "the file" to "the six Step-3 test bodies" — see D15 and AC9 itself.)*

---

**D15 — The cancellation proof lives in its own test file, `GpodderAuthenticationFragmentCancellationTest.kt`. Determinism comes from two hand-rolled single-threaded queues — RxJava's `TestScheduler` for the "before" record, a hand-written `ManualDispatcher` for the "after" one — and the artifact is that file's Step-7 diff, not one test's red→green.** *(Added in Revision 1 to close C1. **[R1]**)*

C1 was right on all three counts, and this decision closes it by the red-team's option **(ii) + (iii) + (iv)**: a separate File-Scope test file with a described harness, a reworded AC9, and a stated method for the fail-on-old-code claim. Option **(i)** — cataloguing `kotlinx-coroutines-test` — is **not** taken; **D9 is not reopened.**

**Why a separate file.** C1(a) is a real contradiction, not a wording slip: `GpodderAuthenticationFragmentAsyncCharacterizationTest.kt` exists to demonstrate that **not one assertion changed** across the conversion, and the cancellation proof exists to demonstrate that **exactly one behavior did**. Those are opposite contracts; putting them in one file makes AC9 either unenforceable or false. So the cancellation pair moves to `ui/preferences/src/test/java/.../synchronization/GpodderAuthenticationFragmentCancellationTest.kt` (File Scope, created at Step 3), and AC9's freeze narrows to the six Step-3 bodies in the *other* file. Nothing in the frozen eleven is touched either way (AC10).

**`ManualDispatcher` — the whole harness, written out.** It is a `CoroutineDispatcher` that queues and never runs anything on its own:

```
private class ManualDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()
    override fun dispatch(context: CoroutineContext, block: Runnable) { queue.addLast(block) }
    fun queued(): Int = queue.size
    fun runQueued() { while (queue.isNotEmpty()) queue.removeFirst().run() }
}
```

- **No thread, no `Executor`, no `CountDownLatch`, no timeout, no `runBlocking`.** `dispatch` enqueues; `runQueued()` is the only thing that ever runs a block. It is deliberately not thread-safe, because nothing but Robolectric's main thread ever touches it.
- **`isDispatchNeeded` is left at its default (`true`)**, which is the load-bearing part: `withContext(manual) { … }` therefore *always* dispatches, so the io block is guaranteed to sit in the queue while the coroutine is suspended. That queued-and-suspended state **is** the in-flight window, made explicit and observable (`queued() == 1`) instead of raced for.
- **Declared file-private inside `GpodderAuthenticationFragmentCancellationTest.kt`**, not as its own `ManualDispatcher.kt`. It has exactly one consumer, it is ~8 lines, and keeping it in-file keeps both halves of the paired proof readable in one place and holds File Scope's created-test-file count at **four**. It is added in **Step 7**, with the test that uses it — the Step-3 half of the pair has no `ioDispatcher` to hand it to.

**The pair.**

| | Step 3 — the "before" record | Step 7 — the "after" proof |
|---|---|---|
| Test | `testInFlightLoginSurvivesDialogDismissal` | `testInFlightLoginIsCancelledWhenDialogIsDismissed` |
| Runs against | **Unmodified RxJava production code** (`git diff origin/develop -- ui/preferences/src/main/` empty, AC4) | The Step-7 `lifecycleScope` + `withContext(ioDispatcher)` code |
| Queue seam | `RxJavaPlugins.setIoSchedulerHandler { TestScheduler() }` + `RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }` in `@Before`, both reset in `@After` | `field("ioDispatcher").set(fragment, ManualDispatcher())` — the suite's existing reflection idiom (D9) |
| In-flight window | After `butLogin.performClick()` + `idle()`: `fake.calls` is **empty** — `subscribeOn` queued the action on the `TestScheduler` and nothing has run | After the same click + `idle()`: `manual.queued() == 1` and `fake.calls` is **empty** — the coroutine is suspended in `withContext` |
| Then | `fragment.dismiss()`; `idle()`; `ioScheduler.triggerActions()`; `idle()` | `fragment.dismiss()`; `idle()`; `manual.runQueued()`; `idle()` |
| Asserts | `fake.calls == [setCredentials, login, getDevices]` and `field("devices").get(fragment)` is **non-null** — **the work ran to completion after the dialog was gone** | `fake.calls` is **still empty**, `field("devices").get(fragment)` is **still null**, and `credentialsError.visibility == GONE` — the block never ran **and** the error branch did not render |
| Fate | **Deleted in Step 7**, because it stops being true | Lives on. File's test count is **flat** across Step 7 (1 → 1) |

Two mechanism notes a reviewer should check rather than take on faith:

- **Why the Step-7 block never runs.** `DispatchedTask.run()` checks the job before invoking the continuation: for a cancellable dispatch mode with a cancelled job it resumes with the job's `CancellationException` instead of executing the block. `dismiss()` drives the `DialogFragment` to `DESTROYED`, which cancels `lifecycleScope`, so by the time `runQueued()` drains the queue the job is already cancelled. This is why the assertion is "the fake recorded nothing", not "the fake recorded something and it was ignored".
- **The `credentialsError.visibility == GONE` assertion is not decoration.** It is the executable half of D10a's "the converted `catch` **must rethrow `CancellationException`**". A `catch (error: Throwable)` that swallows it renders the error branch on a *deliberately* cancelled login, and this assertion is the only thing in the milestone that goes red when that happens.
- **`TestScheduler` costs no dependency.** `io.reactivex.rxjava3.schedulers.TestScheduler` ships in the core `io.reactivex.rxjava3:rxjava` artifact, already `implementation`-declared at `ui/preferences/build.gradle:57` and therefore on the test compile classpath. **Named fallback if it is not resolvable there:** a ~10-line `Scheduler` subclass in the same file that queues `Runnable`s and drains them on an explicit call — the Rx twin of `ManualDispatcher`, same determinism, still no new dependency. Taking the fallback is a **Step-3 finding recorded in Implementation Notes**, not a silent substitution (AC8).

**Determinism, stated as an argument rather than asserted.** Everything in this file runs on Robolectric's main thread: the fragment, `Dispatchers.Main.immediate` under `lifecycleScope`, the trampolined Rx callbacks, and both queues. There is no background thread to race, so there is nothing for a latch to synchronise and no timeout to tune. The only two sources of pending work are the two explicit queues (`triggerActions()` / `runQueued()`) and the main looper (`shadowOf(Looper.getMainLooper()).idle()`, the frozen suite's own idiom). Every observation happens after an explicit drain of both. This is precisely the shape Research hazard 5 warns about avoiding — its flakiness comes from a *real* background dispatcher, which this file does not have. Nothing here uses `runTest`, `TestDispatcher` or `Dispatchers.setMain` (AC8).

**The honest limit of the "fails on old code" claim — C1(c), answered rather than restated.** `testInFlightLoginIsCancelledWhenDialogIsDismissed` **cannot be run against pre-Step-7 code**, and this plan does not pretend otherwise: it reads `ioDispatcher`, a field that does not exist before Step 7, and more fundamentally an RxJava call site has no dispatcher seam to inject at all — the test would not compile, let alone go red. So **there is no single test whose red→green transition proves the behavior change**, and any acceptance criterion claiming one would be unfalsifiable. What replaces it is a **paired-file diff**, and it is a stronger artifact than a red→green because both halves are green runs on real code:

1. `testInFlightLoginSurvivesDialogDismissal` **green at Step 3 against production source that is byte-identical to `origin/develop`** (AC4 proves the byte-identity), with the run output pasted into Implementation Notes. That is a positive, executed record that today's code completes in-flight work after the dialog is gone — not an inference from the discarded `Disposable`.
2. The Step-7 diff of this one file, in which that test is **deleted** and its opposite is **added and green** on the converted code, with the scenario, the fake, the click, the `dismiss()` and the drain unchanged between them.

The deleted test is not a frozen test being edited: it was created by *this* milestone at Step 3, explicitly and only as a before-record, and its deletion is the assertion flip. AC12 row (a) states the artifact in exactly these terms.

---

### Steps

**Ten steps, eight reviewable commits — Steps 2, 3, 4, 5, 6, 7, 8 and 10 **[R1]**.** Steps 1 and 9 produce **no repo diff** (baseline measurement and the verification sweep) and fold into the following commit's Implementation Notes. Each commit leaves the build green and is reviewable on its own. *(Revision 1 corrected the old "ten commits (Steps 1 and 10 excepted)" line, which contradicted the Milestone section's — correct — count of eight and named the wrong two steps.)*

**Step 1 is not the conversion and Step 3 is not optional.** Research flagged a characterization gap — the entire async surface being converted has zero executable coverage — so **Step 3 writes that coverage against the unmodified RxJava code, before any conversion step.** This is non-negotiable. Steps 2 and 3 both precede every production-source change.

**Test-count ledger for the whole milestone [R1]** — every figure is re-derived from a real run at the step in question (AC3), never copied from here:

| After step | Total | What moved |
|---|---|---|
| 1, 2 | **41** | baseline, unchanged |
| 3 | **48** | +6 async characterization, +1 cancellation "before" record |
| 4 | **53** | +5 ViewModel tests |
| 5 | **53** | — |
| 6 | **55** | +2 ViewModel tests |
| 7 | **55** | −1 cancellation "before", +1 cancellation "after" — **flat** (D15) |
| 8, 9, 10 | **55** | — |

**15 test methods are written; 14 are in the final suite** (the Step-3 before-record is replaced by its Step-7 twin), across **three** new classes: `GpodderAuthenticationFragmentAsyncCharacterizationTest` **6**, `GpodderAuthenticationFragmentCancellationTest` **1**, `SynchronizationPreferencesViewModelTest` **7**.

**Step 1 — Branch fresh from `origin/develop`; re-measure and freeze the baseline. No repo diff.**
`git fetch origin`; confirm `8e4c292174897b75d7b9e1609c595717953a004e` is an ancestor of `origin/develop` and that `origin/develop`'s tip is `8e4c29217`; branch from `origin/develop` (D1). If either check fails, **stop** — the baseline this plan's numbers are written against is not the one in front of you. Run `./gradlew :ui:preferences:testFreeDebugUnitTest --rerun` and `:testPlayDebugUnitTest --rerun` as two separate invocations and table the per-class `@Test` counts across all 8 classes. Run the per-file `!!` greps. Capture both dependency configurations per D1. **Re-derive every number from the run — do not copy D1's table** — and record any divergence as a finding before proceeding. Tests: none added.

**Step 2 — Catalogue `androidx-lifecycle-runtime-ktx`; declare **both** lifecycle KTX artifacts on the module. [R1]** Touches `gradle/libs.versions.toml`, `ui/preferences/build.gradle`.
Per D8 **[R1]**. **One** new `[libraries]` entry — `androidx-lifecycle-runtime-ktx` on the existing `lifecycle-runtime-compose` ref, placed with the other `androidx-lifecycle-*` entries (`:58-59`). **Two** new `implementation` lines in `ui/preferences/build.gradle`:

```
implementation libs.androidx.lifecycle.runtime.ktx
implementation libs.androidx.lifecycle.viewmodel.ktx
```

`androidx-lifecycle-viewmodel-ktx` needs **no** catalog entry — it is already declared at `libs.versions.toml:59` on the same ref and is currently wired only into `app-wearos/build.gradle:77`. It is now a required declaration, not a conditional one: C2's bridge calls `stateIn(viewModelScope, …)`, and `viewModelScope` ships in that artifact (D2's `viewModelScope` row, D8's Revision 1 reversal). *(This replaces the pre-revision "one line, or two if the `dependencyInsight` probe says so" conditional — the probe is no longer a decision point.)*

Run D8's probe as a **no-movement check**, before and after the two declarations: `./gradlew :ui:preferences:dependencyInsight --configuration playDebugCompileClasspath --dependency androidx.lifecycle:lifecycle-viewmodel`, plus the same for `lifecycle-runtime`. Re-capture both dependency configurations and confirm the resolved lifecycle versions are **unmoved** from Step 1 — Research measured **2.9.4** against the catalog ref's 2.8.7, so both declarations must be provable no-ops (AC6). Any version movement re-opens D8 and is a finding recorded before proceeding. No production source changes. Tests: none added; all **41** stay green.

**Step 3 — Characterization tests for the two RxJava subscriptions, plus the cancellation "before" record, all written against unmodified production code. [R1]** Creates **three** files: `ui/preferences/src/test/java/.../synchronization/FakeGpodnetService.kt`, `GpodderAuthenticationFragmentAsyncCharacterizationTest.kt` and `GpodderAuthenticationFragmentCancellationTest.kt`.
Per D14 and D15. `git diff origin/develop -- ui/preferences/src/main/` must be **empty** at the end of this step — that emptiness is what makes both files records of *current* behavior (AC4). Adds **seven** tests in total, all green against the RxJava code: **six** in `GpodderAuthenticationFragmentAsyncCharacterizationTest.kt` (the six below, frozen from here by AC9) and **one** in `GpodderAuthenticationFragmentCancellationTest.kt` — `testInFlightLoginSurvivesDialogDismissal`, the D15 "before" record, which uses an Rx `TestScheduler` queue seam and **no** `ioDispatcher` (that field does not exist yet). Its green run output is pasted into Implementation Notes; it is deleted in Step 7. `ManualDispatcher` is **not** written in this step. Suite **41 → 48**.

The six frozen async characterization tests:
1. `testLoginSuccessCallsServiceInOrderAndAdvancesToDeviceStep` — asserts `setCredentials` → `login` → `getDevices` call order, that `devices`/`username`/`password` hold the fake's values, `butLogin.isEnabled == true`, `progBarLogin.visibility == GONE`, `currentStep == 2`.
2. `testLoginWritesDevicesBeforeCredentialFields` — the fake's `getDevices()` asserts the fragment's `username` field is still `null` at that moment, pinning the `:124-126` write order.
3. `testLoginErrorWithCauseRendersCauseMessage` — `login()` throws `GpodnetServiceException(IOException("boom"))`; asserts `credentialsError.text == "boom"`, `VISIBLE`, `butLogin.isEnabled == true`, `progBarLogin.visibility == GONE`, `currentStep` unchanged at 1.
4. `testWrongPasswordErrorPathThrowsFromNullCause` — `login()` throws `GpodnetServiceAuthenticationException("Wrong username or password")`; asserts the `NullPointerException` from `error.cause!!` escapes. **This is Gap 16's first executable pin** (D11).
5. `testCreateDeviceSuccessConfiguresLowercasedIdAndAdvances` — asserts `configureDevice` received the lowercased generated id and the entered caption, `selectedDevice` matches, `progbarCreateDevice.visibility == GONE`, `currentStep == 3`.
6. `testCreateDeviceErrorRendersErrorMessageNotCause` — `configureDevice` throws `GpodnetServiceException("nope")`; asserts `deviceSelectError.text == "nope"` and `deviceName.isEnabled == true`. **This is the first executable pin of the deliberate two-handler asymmetry** (`error.cause!!.message` at Site A vs `error.message` at Site B) that Milestone 15's `AC13` could only grep for.

**Step 4 — Create the ViewModel and its state types; nothing consumes them yet.** Creates `ui/preferences/src/main/java/.../synchronization/SynchronizationPreferencesViewModel.kt` (holding `SyncSettingsUiState` and `SyncSubtitle`) and `ui/preferences/src/test/java/.../synchronization/SynchronizationPreferencesViewModelTest.kt`.
D2, D5, D6 exactly — the `callbackFlow { register … awaitClose { unregister } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), null)` bridge of D5 **[R1]**, not the withdrawn `init`/`onCleared` registration. `SynchronizationPreferencesFragment.kt` is **not touched** in this step — the ViewModel is reviewable in isolation and the 41 stay green untouched. The class is `@RunWith(RobolectricTestRunner::class)`; `Dispatchers.Main` cannot initialise without an Android main looper (D9).

**Every test in this class starts a collector before reading state, and this is a consequence of `WhileSubscribed`, not a style choice [R1]:** with no subscriber the upstream `callbackFlow` never runs, nothing registers with EventBus, and `syncStatus.value` stays frozen at the `null` initial value forever. So each test does `val job = CoroutineScope(Dispatchers.Main.immediate).launch { viewModel.syncStatus.collect { … } }`, then `shadowOf(Looper.getMainLooper()).idle()`, then acts, idles again, asserts, and cancels the job. Four of the tests then assert on `.value`; test 3 asserts on the collected list (D6/D9).

Adds **five** tests **[R1]** — three carried over, one renamed and re-mechanised, two new for C2/C6, one withdrawn:
1. `testStickyEventPostedBeforeCollectionIsReplayedIntoSyncStatus` — sticky posted before the collector subscribes is replayed into `syncStatus.value`. *(Renamed **[R1]** from `…BeforeConstructionIsReplayed…`: under the new bridge the replay happens when `register()` runs at collector-subscribe, not at construction. The scenario is unchanged; the old name would have described the wrong mechanism.)*
2. `testSyncStatusStartsNullWhenNoStickyEventExists`
3. `testConsecutiveEventsWithSameMessageResIdAreBothDelivered` **[R1]** — *renamed from `…BothEmit` and changed from a `.value` read to a stream-collector test, per D6/C3.* Collects into a `mutableListOf<SyncServiceEvent?>` from a `Dispatchers.Main.immediate` scope, posts two distinct `SyncServiceEvent(R.string.sync_status_started)` instances with an `idle()` between them, cancels the job, and asserts the list is `[null, event1, event2]` — three entries, the two events distinct instances. A dropped emission is **invisible** to a `.value` read, which is why the mechanism had to change; the name now says what the test observes (delivery) rather than what it cannot see (emission). Determinism is single-threadedness plus explicit `idle()` pumping (D6, D9) — no `kotlinx-coroutines-test`. Pins D6's non-`data class` finding, so a future `SyncServiceEvent` change breaks a test rather than the screen.
4. `testCollectorCancellationUnregistersFromEventBus` **[R1]** — *replaces the withdrawn `testOnClearedUnregistersFromEventBus`, which pinned a mechanism C2 removed: `onCleared()` is no longer an unregister path, and under Robolectric it never runs anyway (C6).* Records `EventBus.getDefault().hasSubscriberForEvent(SyncServiceEvent::class.java)` **before** collection starts, asserts it is `true` while the collector is active, and asserts it returns to **exactly the recorded pre-collection value** after `job.cancel()` + `idle()`. Comparing against the recorded value rather than a hardcoded `false` makes the test immune to any pre-existing subscriber on the process-global bus — which is what C6 was about — while still failing if this bridge leaves a subscriber behind.
5. `testEventsPostedWhileNotCollectingAreSeenOnlyIfSticky` **[R1]** — *new, the executable form of C2's stopped-then-restarted requirement, and the reason lifetime-registration was safe to drop rather than merely argued away.* One test, two halves on the same ViewModel: **(a)** with no active collector, `EventBus.getDefault().post(SyncServiceEvent(R.string.sync_status_started))` — a **non-sticky** post, the shape the frozen suite uses at `SynchronizationPreferencesFragmentLifecycleTest.kt:81, :97, :102` — then start a fresh collector and assert `syncStatus.value` is **`null`**: the event was missed, exactly as the unregistered `develop` fragment misses it, and `replayExpirationMillis = 0` means no stale value survives the gap either. **(b)** cancel again, `postSticky(SyncServiceEvent(R.string.sync_status_success))`, start a third collector, assert `syncStatus.value` is that event: EventBus's own sticky replay reaches the re-registered subscriber. Together these are the direct check that registration tracks collection, that a `WhileSubscribed` stop really does reset to `initialValue`, and that the sticky path (D10c) still works across a stop/start cycle. Pinned by **AC22**.

**Step 5 — `SynchronizationPreferencesFragment` consumes `syncStatus`; EventBus subscription removed from the fragment.** Touches `ui/preferences/src/main/java/.../synchronization/SynchronizationPreferencesFragment.kt`.
Acquire the ViewModel with `ViewModelProvider(this)[SynchronizationPreferencesViewModel::class.java]` (D2). Delete `@Subscribe`, `EventBus.getDefault().register(this)` and `unregister(this)` and the `EventBus`/`Subscribe`/`ThreadMode` imports; start and cancel `syncStatusJob` at the exact source positions they occupied (D7). `syncStatusChanged`'s body is **unchanged** in this step — it stops being an EventBus callback and becomes a private function called from the collector. **The four `SynchronizationPreferencesFragmentLifecycleTest` tests are this step's equivalence proof and are not edited.** Tests: none added; suite stays at **53**.

**This step is also the `stateIn` start-up-timing verification gate (D5 [R1]).** Under the C2 bridge, `syncStatusJob = lifecycleScope.launch { … }` is now what *registers* with EventBus, so `testStickyEventReplaysOnStart` passes only if `stateIn`'s sharing coroutine starts, runs the `callbackFlow` block, and delivers `register()`'s synchronous sticky replay **before `onStart()` returns** — otherwise `updateActionBar()`'s subtitle is not overwritten and the assertion fails. D5 argues that happens (everything is `Dispatchers.Main.immediate`, inline on Robolectric's main thread, draining the unconfined event loop before `launch` returns) but calls it a reasoned expectation about coroutine internals, **not a measured fact**. The gate: run the four frozen `SynchronizationPreferencesFragmentLifecycleTest` tests and paste the output. If `testStickyEventReplaysOnStart` or `testOnStartSetsTitleAndOnStopSetsEmptySubtitleWhereasDisconnectedSetsNull` is red, the developer does **not** touch the frozen test and does **not** improvise:

> **Pre-authorised fallback, same File Scope, same acceptance criteria [R1].** Swap `callbackFlow`/`stateIn` for `fun startObserving()` / `fun stopObserving()` on the ViewModel, which register and unregister the same private `SyncServiceEventSubscriber` **synchronously** against a `MutableStateFlow<SyncServiceEvent?>` that is reset to `null` on stop. The fragment calls them at the exact `register()`/`unregister()` source positions and the collector still renders from the same `StateFlow`. Registration stays tied to `onStart`/`onStop`, so C2's stopped-state equivalence is preserved and **all five Step-4 tests apply unchanged** — they exercise subscribe/unsubscribe semantics through whichever mechanism ships, and AC22 is satisfied either way. What it costs: the idiomatic Flow shape, two public methods, and D8's `viewModelScope`/`lifecycle-viewmodel-ktx` justification (the declaration stays regardless — it is correct practice for a module holding a `ViewModel`, and AC7 permits exactly the two lines either way). Choosing it is a **Step-5 finding recorded in Implementation Notes with the failing test output that forced it**, not a silent substitution.

Record C6's cross-test analysis here too (D5's closing paragraph): registration now ends at collector cancellation, i.e. at `onStop`, which the frozen tests do reach — and for tests that never stop the fragment the residue is one subscriber per attached fragment, exactly what `develop` already leaves. AC1's per-class counts are the check that this stays true.

**Step 6 — Move ActionBar title/subtitle into `SyncSettingsUiState`; collapse the five access statements to one.** Touches `SynchronizationPreferencesFragment.kt`, `SynchronizationPreferencesViewModel.kt`, `SynchronizationPreferencesViewModelTest.kt`.
D12 exactly. The ViewModel gains `uiState: StateFlow<SyncSettingsUiState>`, `fun onStarted()` (the `updateActionBar()` decision: connected → `LastSyncReport(isLastSyncSuccessful(), getLastSyncAttempt())`, else `Absent`) and `fun onSyncEvent(event)` (the `:61-70` branch: `sync_status_error`/`sync_status_success` → `LastSyncReport`, else → `Message(event.messageResId)`). The fragment gains `actionBar()` and one `render(state)`; `onStop` calls `actionBar().subtitle = ""` directly, **after** cancelling the job (D7). `updateScreen()` stays in the fragment and keeps being called at its current three call sites — it is preference-row rendering, not ActionBar state, and moving it is Milestone 20's (D3 reason 2 **[R1]**: hoisting the screen-body state remains Milestone 20's work). Adds **two** tests to `SynchronizationPreferencesViewModelTest`, numbered from the five of Step 4 **[R1]**:
6. `testDisconnectedProviderYieldsAbsentSubtitleAndConnectedYieldsLastSyncReport`
7. `testErrorAndSuccessEventsYieldLastSyncReportWhileOtherEventsYieldMessage`
Both follow the class's collector convention (start a collector, `idle()`, act, `idle()`, assert `.value`, cancel) — `uiState` is a `StateFlow` derived in the same ViewModel and, where it is fed by the `WhileSubscribed` bridge, is subject to the same no-collector-no-upstream rule. Suite **53 → 55**. The four lifecycle tests remain unmodified and green — this is the step where they carry the most weight.

**Step 7 — Convert Site A (login) to `lifecycleScope` + `withContext(ioDispatcher)`; flip the cancellation pair. [R1]** Touches **three** files: `GpodderAuthenticationFragment.kt`, `GpodderAuthenticationFragmentAsyncCharacterizationTest.kt` (**setup only**, per D14/AC9) and `GpodderAuthenticationFragmentCancellationTest.kt` (the paired flip, per D15).
Replace `Completable.fromAction { … }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe({…},{…})` (`:121-139`) with `lifecycleScope.launch { try { withContext(ioDispatcher) { … } ; <success body> } catch (error: Throwable) { <error body> } }`. The io block keeps its four statements in order, including the non-`@Volatile` `devices` write (D10b). The error body keeps `txtvError.text = error.cause!!.message` verbatim (D10d/D11). `lifecycleScope`, not `viewLifecycleOwner.lifecycleScope` — this is a `DialogFragment` whose views come from `onCreateDialog`, so no view lifecycle owner exists. Add `private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO` (D9). The `catch` **must rethrow `CancellationException`** before rendering the error branch (D10a) — a swallowed cancellation renders an error on a deliberately cancelled login and silently un-does this milestone's one intentional behavior change.

**Two test files move, in opposite directions, and the difference between them is the point [R1]:**

- `GpodderAuthenticationFragmentAsyncCharacterizationTest.kt` — **setup only.** `@Before`/`@After` drop the Rx plugin handlers for Site A and gain `field("ioDispatcher").set(fragment, Dispatchers.Unconfined)` (D9); imports change. **Zero assertion lines change in the six Step-3 test bodies** — AC9, now narrowed to exactly that (D14 **[R1]**).
- `GpodderAuthenticationFragmentCancellationTest.kt` — **the paired flip, per D15.** Delete `testInFlightLoginSurvivesDialogDismissal` (it asserted non-cancellation, which is no longer true, and it was created at Step 3 as a before-record for precisely this moment); add the file-private `ManualDispatcher` class and `testInFlightLoginIsCancelledWhenDialogIsDismissed`, which installs the `ManualDispatcher` through the `ioDispatcher` seam, asserts `manual.queued() == 1` with `fake.calls` empty as the explicit in-flight window, `dismiss()`es, drains with `runQueued()` + `idle()`, and asserts the io block **never ran** (`fake.calls` still empty, `devices` still null) and the error branch did **not** render (`credentialsError.visibility == GONE`). **The file's test count is flat (1 → 1); the suite stays at 55.** This file is **not** covered by AC9 — its Step-7 diff is the deliberate-behavior-change artifact, and AC12 row (a) is what requires it to be produced and pasted.

**Step 8 — Convert Site B (createDevice) the same way.** Touches `GpodderAuthenticationFragment.kt`, `GpodderAuthenticationFragmentAsyncCharacterizationTest.kt` (setup only).
Replace `Observable.fromCallable { … }` (`:176-192`) with the same `lifecycleScope.launch`/`withContext(ioDispatcher)`/`try`/`catch` shape. The error body keeps `txtvError.text = error.message` verbatim — **do not "make the two handlers consistent"** (Milestone 15 D7; now pinned executably by Step 3's test 6). Remove the now-unused `Completable`, `Observable`, `Schedulers`, `AndroidSchedulers` imports. This is the step after which `git grep -n "rxjava3" ui/preferences/src/main/java/.../synchronization/GpodderAuthenticationFragment.kt` returns **zero** — while `libs.rxandroid`/`libs.rxjava` stay declared on the module for `BugReportViewModel` and `NextcloudAuthenticationFragment` (AC17) — and, incidentally, for the `TestScheduler` D15's Step-3 record used. Tests: none added; **Step 3's six run with unchanged assertions** (AC9), and `GpodderAuthenticationFragmentCancellationTest.kt` is **not touched in this step** — it exercises Site A only. Suite stays at **55**. **[R1]**

**Step 9 — CI-bar verification sweep.** No repo diff.
Run every acceptance criterion's command and paste the output: both flavoured `:ui:preferences` test tasks, `assemblePlayDebug` + `assemblePlayRelease` + `assembleFreeRelease` + `assemblePlayDebugAndroidTest`, `checkstyle lint`, `ktlintCheck`. Re-capture both dependency configurations and diff against Step 1. Re-run the per-file `!!` greps. Record every D10 preserve/fix line explicitly.

**Step 10 — Documentation.** Touches `ui/preferences/README.md`, `tasks/antennapod-sync-settings-concurrency-mvvm-milestone-17.md`, `tasks/antennapod-sync-settings-modernization-future-work.md`, `features/antennapod-sync-settings-concurrency-mvvm-milestone-17.checkpoint.md`.
Add the D-referenced conventions to the module README, phrased as long-term-stable module rules with no milestone number and no task-file reference (`AGENTS.md`), numbered **from 13**: `StateFlow` over `LiveData` and immutable `data class` state over the mutable re-posted instance (D2); `SyncServiceEvent` must stay a plain class, because conflation would start dropping consecutive same-`messageResId` posts if it became a `data class` (D6); EventBus registration in this module is tied to collection, never to a ViewModel's lifetime (D5); and **[R1]** deterministic coroutine tests here come from single-threadedness — Robolectric's `Looper` idling plus a hand-written queueing dispatcher — not from `kotlinx-coroutines-test` (D9, D15). Update the future-work file so Milestone 18 inherits Gap 16 by name with its one-line fix (D11), the `kotlinx-coroutines-test` bill (D9) and the five deferred `SynchronizationQueue.instance!!` sites (D4); and Milestone 20 inherits D3's narrowed MVVM footprint and the fact that a Gpodder-wizard ViewModel is *its* scope if the dialogs go Compose.

### File Scope

The reviewer rejects any diff touching a file not on this list.

**Modified — build surface:**
- `gradle/libs.versions.toml` (Step 2 — **exactly one** new `[libraries]` entry, `androidx-lifecycle-runtime-ktx`; **no new `[versions]` entry, no new `[plugins]` entry**) **[R1]**
- `ui/preferences/build.gradle` (Step 2 — **exactly two** `implementation` lines: `libs.androidx.lifecycle.runtime.ktx` and `libs.androidx.lifecycle.viewmodel.ktx`, the second needing no catalog entry — D8 **[R1]**)

**Modified — production source (exactly two files):**
- `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/SynchronizationPreferencesFragment.kt` (Steps 5, 6)
- `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/GpodderAuthenticationFragment.kt` (Steps 7, 8)

**Created — production source (exactly one file):**
- `ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/SynchronizationPreferencesViewModel.kt` (Step 4 — holds `SynchronizationPreferencesViewModel`, `SyncSettingsUiState`, `SyncSubtitle`)

**Created — test source (exactly four files): [R1]**
- `ui/preferences/src/test/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/FakeGpodnetService.kt` (Step 3, support)
- `ui/preferences/src/test/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/GpodderAuthenticationFragmentAsyncCharacterizationTest.kt` (Step 3; **setup-only** edits in Steps 7, 8 — AC9)
- `ui/preferences/src/test/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/GpodderAuthenticationFragmentCancellationTest.kt` **[R1]** (Step 3 creates it with the `testInFlightLoginSurvivesDialogDismissal` before-record; Step 7 deletes that test and adds the file-private `ManualDispatcher` class plus `testInFlightLoginIsCancelledWhenDialogIsDismissed` — D15. **`ManualDispatcher` is deliberately *not* its own `ManualDispatcher.kt`**: one consumer, ~8 lines, and keeping both halves of the paired proof in one file is the point. Added in Revision 1 to close C1 by the red-team's option (ii) — the cancellation proof cannot live in the file AC9 freezes.)
- `ui/preferences/src/test/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/SynchronizationPreferencesViewModelTest.kt` (Steps 4, 6)

**Modified — documentation (Step 10):**
- `ui/preferences/README.md`
- `tasks/antennapod-sync-settings-concurrency-mvvm-milestone-17.md`
- `tasks/antennapod-sync-settings-modernization-future-work.md`
- `features/antennapod-sync-settings-concurrency-mvvm-milestone-17.checkpoint.md`

**Not in scope — a diff touching any of these means the plan was wrong and the task is re-planned, not patched:** all **eleven existing files** under `ui/preferences/src/test/java/.../screen/synchronization/`, which stay **byte-identical** (D3 — this is the milestone's verification instrument, and AC10 enforces it with `--diff-filter=M`); `NextcloudAuthenticationFragment.kt` and `AuthenticationDialog.kt`; every other file under `ui/preferences/src/main/`, including `res/xml/preferences_synchronization.xml` and every layout; `ui/preferences/src/main/java/.../screen/bugreport/**` — `BugReportViewModel` is a shape reference, not an edit target, and is **not** migrated to StateFlow here; **all of `:net:sync:service`** — the 9 `postSticky` producers and `SynchronizationQueueImpl` are untouched (D5); `:net:sync:service-interface`, `:net:sync:gpoddernet` (including `GpodnetService.java`, which the fake **subclasses but must not modify**), `:event` (including `SyncServiceEvent.kt`, whose non-`data class` shape D6 depends on and which must stay as-is), `:storage:preferences`; `app/**` and `app-wearos/**`; root `build.gradle`, `common.gradle`, `playFlavor.gradle`, `settings.gradle`, `.editorconfig`, `.github/**`, `config/**`, `gradle/wrapper/**`, `gradle.properties`; every other milestone's task and checkpoint file.

**No new user-visible string is added**, so `AGENTS.md`'s `:ui:i18n` rule is not triggered — and adding one would itself be out of scope. **No code comments are added** to any file (`AGENTS.md`); every rationale in this plan lives in the README conventions and this task file.

### Acceptance Criteria

Track: `concurrency` + MVVM state layer on `:ui:preferences` `screen/synchronization/`. Every item is a command whose output the reviewer can check independently. Where a criterion states a number, that number is the pass condition. **22 criteria.** **[R1]**

**The regression net stays green and byte-identical**
- [ ] **AC1** — `./gradlew :ui:preferences:testFreeDebugUnitTest --rerun` and `./gradlew :ui:preferences:testPlayDebugUnitTest --rerun`, run as two separate invocations, are BUILD SUCCESSFUL with **0 failures, 0 errors, 0 skipped** at the end of **every** step from 2 onward, with the **same per-class counts as Step 1's baseline, row for row**, for all **eight** pre-existing classes (Gpodder 9, SyncPrefs 8, Nextcloud 6, HarnessSmoke 6, AuthDialog 4, Lifecycle 4, ScreenshotCapture 3, JavaInterop 1 = **41**). **No pre-existing per-class count may change in either direction.** The **three** new classes end the milestone at **[R1]**: `GpodderAuthenticationFragmentAsyncCharacterizationTest` **6**, `GpodderAuthenticationFragmentCancellationTest` **1**, `SynchronizationPreferencesViewModelTest` **7** — eleven classes, **55** tests. CI never runs the Free flavour for this module, so both are run locally.
- [ ] **AC2** — The four `SynchronizationPreferencesFragmentLifecycleTest` tests — `testStickyEventReplaysOnStart`, `testSyncEventIgnoredWhenNotConnected`, `testSubtitleBranchesOnMessageResId`, `testOnStartSetsTitleAndOnStopSetsEmptySubtitleWhereasDisconnectedSetsNull` — are green at the end of Steps 5 **and** 6, **with the file unmodified**. These four are the sticky-replay, disconnected-guard, branch and absent-vs-empty equivalence proofs (D6, D7, D12); a change to any of them invalidates the milestone's central claim. **[R1]** Step 5 runs them as an explicit gate on `stateIn`'s start-up timing (D5); if either ordering-sensitive test is red, the pre-authorised `startObserving()`/`stopObserving()` fallback is taken and the **failing output is pasted into Implementation Notes**. This criterion is unchanged either way — the fallback is equivalence-preserving on the same four tests, and taking it silently is itself a failure of this criterion.
- [ ] **AC3** — The total suite count is **41** at the end of Steps 1 and 2, **48** at the end of Step 3, **53** at the end of Steps 4 and 5, and **55** at the end of Steps 6 through 10 **[R1]** — Step 7 is **flat**, because the cancellation file's before-record is deleted in the same commit that adds its after-twin (D15). Each figure is re-derived from a real run; a divergence is a finding recorded before proceeding, not a number adjusted to match.

**Characterization coverage lands before conversion, and its assertions survive it**
- [ ] **AC4** — At the end of **Step 3**, `git diff origin/develop -- ui/preferences/src/main/` is **empty** and both flavoured test tasks are green at **48** tests **[R1]**. The six new async characterization tests **and** the seventh test, `testInFlightLoginSurvivesDialogDismissal` in `GpodderAuthenticationFragmentCancellationTest.kt`, are green **against the unmodified RxJava production code** (D14, D15). A Step-3 commit that also changes production source fails this criterion outright — and it is precisely that emptiness that makes the before-record a record of *current* behavior rather than of the plan's expectation of it.
- [ ] **AC5** — All six Step-3 async characterization tests are named in Implementation Notes with the behavior each pins, and each is confirmed green at Step 3, Step 7 and Step 8. Specifically: `testWrongPasswordErrorPathThrowsFromNullCause` is Gap 16's first executable pin (D11), and `testCreateDeviceErrorRendersErrorMessageNotCause` is the first executable pin of the deliberate `error.cause!!.message` / `error.message` asymmetry that Milestone 15's `AC13` could only grep for. **[R1]** Step 3's seventh test lives in a different file, is deliberately **not** carried past Step 7, and is covered by **AC12 row (a)** rather than by this criterion.
- [ ] **AC9** — **[R1]** `git diff <Step-3 commit> HEAD -- ui/preferences/src/test/java/.../GpodderAuthenticationFragmentAsyncCharacterizationTest.kt` at the end of Step 8 touches **only** import lines and the `@Before`/`@After` bodies. **No `assert`-containing line inside the six Step-3 test methods is added, removed or changed, and no existing test-method body changes.** This is D14's contract and the single strongest equivalence signal in the milestone — a changed assertion means behavior changed and the diff was papered over. *Revision 1 narrowed the scope of this criterion from "the file" to "the six Step-3 test bodies" (C1 mitigation (iii)). The reason is that the old wording made the milestone's one **intentional** behavior change unprovable: any test asserting that cancellation now happens is, by construction, a new asserting test, and it cannot live in the file whose contract is "nothing asserted here changed". It now lives in `GpodderAuthenticationFragmentCancellationTest.kt` (D15), which this criterion does **not** cover.* The narrowing does not weaken the freeze it was written for: the six bodies are still byte-identical, and **AC10** independently forbids touching any of the eleven pre-existing test files.

**Dependencies move by exactly one declaration and resolve to exactly what they resolved to before**
- [ ] **AC6** — The Step-1 vs Step-9 diff of `:ui:preferences:dependencies --configuration playDebugCompileClasspath` and `--configuration playDebugUnitTestRuntimeClasspath` is pasted into Implementation Notes and shows **no resolved-version movement for any artifact**. In particular `androidx.lifecycle:lifecycle-runtime-ktx` and `androidx.lifecycle:lifecycle-viewmodel-ktx` sit at their Step-1 resolved versions (Research measured **2.9.4**) despite the new catalog entry pinning ref **2.8.7** — Gradle resolves upward and the declaration is a provable no-op (D8). Any movement re-opens D8.
- [ ] **AC7** — **[R1]** `git diff origin/develop -- gradle/libs.versions.toml` adds **exactly one** `[libraries]` line — `androidx-lifecycle-runtime-ktx` on `version.ref = "lifecycle-runtime-compose"` — and **nothing else**: no new `[versions]` ref, no new `[plugins]` entry, no `kotlinx-coroutines-test`, no Turbine, no MockWebServer, no `mockito-core` declaration (D9). `git diff origin/develop -- ui/preferences/build.gradle` adds **exactly two** lines, both `implementation`: `libs.androidx.lifecycle.runtime.ktx` and `libs.androidx.lifecycle.viewmodel.ktx`, and removes none. *Revision 1 replaced the old "one line, or two if the `dependencyInsight` probe requires it" conditional: `viewmodel-ktx` is now unconditionally required, because C2's `stateIn(viewModelScope, …)` bridge uses `viewModelScope`. It needs no catalog entry — `libs.versions.toml:59` already declares it on the same ref — so the catalog still moves by exactly one line while the module's build file moves by two.* A third `implementation` line, or a second catalog line, fails this criterion.
- [ ] **AC8** — `git grep -n "coroutines-test\|turbine\|mockwebserver\|mockito" -- 'ui/preferences/**' '*.gradle' '*.toml'` returns no hit introduced by this milestone (D9), and no test file in the milestone references `runTest`, `TestDispatcher`, `StandardTestDispatcher`, `UnconfinedTestDispatcher` or `Dispatchers.setMain` **[R1]**. Determinism comes from four things, all of them either pre-existing or hand-written in-repo, and **none of them a dependency**: the frozen suite's existing `shadowOf(Looper.getMainLooper()).idle()`; the reflected `ioDispatcher` seam (D9); the **hand-written, file-private ~8-line `ManualDispatcher`** in `GpodderAuthenticationFragmentCancellationTest.kt`, which is an ordinary `CoroutineDispatcher` subclass queueing `Runnable`s with no thread, executor or latch (D15); and `io.reactivex.rxjava3.schedulers.TestScheduler`, which ships in the **core** `io.reactivex.rxjava3:rxjava` artifact already declared at `ui/preferences/build.gradle:57` and therefore adds nothing to the dependency graph. If `TestScheduler` proves unresolvable there, D15's hand-written `Scheduler` twin is used instead and the substitution is recorded as a Step-3 finding — either way this criterion is unchanged.

**Scope discipline**
- [ ] **AC10** — `git diff --diff-filter=M --name-only origin/develop -- ui/preferences/src/test/` is **empty** at every step: **not one of the eleven pre-existing test files is modified, removed, renamed or moved** (D3). `git diff --diff-filter=A --name-only origin/develop -- ui/preferences/src/test/` lists **exactly four** files and nothing else **[R1]**: `FakeGpodnetService.kt`, `GpodderAuthenticationFragmentAsyncCharacterizationTest.kt`, `GpodderAuthenticationFragmentCancellationTest.kt`, `SynchronizationPreferencesViewModelTest.kt`. *(Revision 1: the fourth is D15's cancellation file. `ManualDispatcher` is a file-private class inside it, not a fifth file — a `ManualDispatcher.kt` in this listing fails this criterion.)* `git diff --diff-filter=D --name-only origin/develop -- ui/preferences/src/test/` is **empty**: the only test method deleted in the milestone is `testInFlightLoginSurvivesDialogDismissal`, inside a file this milestone itself created, and no file is deleted.
- [ ] **AC11** — `git diff --name-only origin/develop` lists **only** File Scope files. `git diff origin/develop -- net/ event/ storage/ app/ app-wearos/ common.gradle build.gradle settings.gradle` is **empty** — in particular the 9 `postSticky` producers in `:net:sync:service` and `event/.../SyncServiceEvent.kt` are untouched (D5, D6). `git diff origin/develop -- ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/bugreport/` is **empty** (D2).
- [ ] **AC18** — `git grep -n "@HiltViewModel\|@AndroidEntryPoint\|@Inject\|@Module\|@InstallIn\|hilt" -- 'ui/preferences/**'` returns **zero** hits, and `git diff origin/develop -- ui/preferences/build.gradle` contains no Hilt or KSP line. DI is Milestone 18; this milestone's ViewModel is a plain `ViewModel` with the default factory (D2, D4).
- [ ] **AC19** — `git grep -n "@Composable\|ComposeView\|collectAsState" -- 'ui/preferences/src/main/java/**/synchronization/'` returns **zero** hits. No Compose is written here; Milestone 20 owns it.

**Behavioral equivalence, preserve-or-fix, recorded per behavior** (D10)
- [ ] **AC12** — Implementation Notes carries a **five-row** table **[R1]** stating, for each of D10's behaviors, **PRESERVED** or **FIXED**, the mechanism, and the test that checks it: (a) the two discarded `Disposable`s → **FIXED**, structurally forced by `lifecycleScope`, proved by the **paired-file artifact of D15**, which is pasted into Implementation Notes in two parts **[R1]**: **(i)** the Step-3 green run of `testInFlightLoginSurvivesDialogDismissal` against production source that AC4 proves byte-identical to `origin/develop` — an executed record that today's code completes in-flight work after the dialog is gone; and **(ii)** `git diff <Step-3 commit> <Step-7 commit> -- ui/preferences/src/test/java/.../GpodderAuthenticationFragmentCancellationTest.kt`, showing that test deleted and `testInFlightLoginIsCancelledWhenDialogIsDismissed` added and green, with the scenario, fake, click, `dismiss()` and queue-drain unchanged between them. *Revision 1 removed the claim that the Step-7 test is "confirmed to fail if run against the pre-Step-7 code" (C1(c)): it cannot be run there at all — it reads `ioDispatcher`, which does not exist before Step 7, and an RxJava call site has no dispatcher seam to inject — so the old wording was unfalsifiable. The paired diff replaces it, and both of its halves are green runs on real code.* Implementation Notes also states that the `catch` rethrows `CancellationException` and names `credentialsError.visibility == GONE` as the assertion that proves it; (b) the `devices` data race → **PRESERVED**, `grep -c '@Volatile' GpodderAuthenticationFragment.kt` → exactly **3** with `devices` still without it, `ui/preferences/README.md` convention 5 unchanged; (c) sticky replay → **PRESERVED**, `testStickyEventReplaysOnStart` green and unmodified; (d) Gap 16 → **FIXED under OQ1, 2026-09-01** (was PRESERVED); mechanism `error.cause?.message ?: error.message` at Site A only, pinned by the inverted test `testWrongPasswordErrorRendersServerMessageWithNoCause`; landed as a separate post-equivalence commit after Step 8, so the CompositeException→NPE disclosure is moot; (e) **[R1]** per-post delivery of `SyncServiceEvent` → **PRESERVED IN PRACTICE, NOT IDENTICAL** — sequential posts are each observed, pinned by `testConsecutiveEventsWithSameMessageResIdAreBothDelivered`; posts arriving faster than the collector resumes can coalesce under `StateFlow` conflation where EventBus dispatched both, no test pins that window, and the bounded real-world case (`SynchronizationQueueImpl.kt:136` immediately followed by `SyncService.kt:61`) is named (D10 row (e), closing C4).
- [ ] **AC22** — **[R1]** *New in Revision 1, the executable half of C2 and C6.* EventBus registration is tied to **collector lifetime**, not to ViewModel lifetime, and this is checked rather than argued. Two tests in `SynchronizationPreferencesViewModelTest`, both green at the end of Steps 4 through 10:
  - `testCollectorCancellationUnregistersFromEventBus` — `EventBus.getDefault().hasSubscriberForEvent(SyncServiceEvent::class.java)` is recorded **before** collection, asserted **`true`** while the collector is active, and asserted equal to the **recorded pre-collection value** after `job.cancel()` + `idle()`. Comparing to the recorded value rather than to a hardcoded `false` makes the assertion immune to any pre-existing subscriber on the process-global bus (C6) while still failing if this bridge leaks one.
  - `testEventsPostedWhileNotCollectingAreSeenOnlyIfSticky` — the stopped-then-restarted pair on one ViewModel: a **non-sticky** `post(SyncServiceEvent(sync_status_started))` delivered while no collector is active is **missed** (a fresh collector reads `syncStatus.value == null`, which also proves `replayExpirationMillis = 0` resets to `initialValue`), while a **`postSticky(SyncServiceEvent(sync_status_success))`** delivered while no collector is active **is** replayed to the next collector. Non-sticky is the shape the frozen suite itself posts at `SynchronizationPreferencesFragmentLifecycleTest.kt:81, :97, :102` (sticky only at `:67`; `removeStickyEvent` only at `:39`/`:45`, in `setUp`/`tearDown`), so this is a live distinction inside the verification instrument, not a hypothetical.

  C2 required this because lifetime-registration was **not** kept: D5's `callbackFlow` + `stateIn(SharingStarted.WhileSubscribed(0, 0))` shape is the fix, and these two tests are what verify the shape actually produces the equivalence it claims rather than merely being argued to. If Step 5's fallback (`startObserving()`/`stopObserving()`) is taken, both tests apply **unchanged** — they assert subscribe/unsubscribe semantics, not the mechanism that implements them.
- [ ] **AC14** — During the milestone proper (through Step 9): `grep -F -c 'error.cause!!.message' GpodderAuthenticationFragment.kt` → **1**, `grep -F -c 'error.message' …` → **1**, `grep -F -c 'error.cause?.message' …` → **0**. **After the OQ1 Gap 16 fix (2026-09-01):** `error.cause!!.message` → **0**, `error.cause?.message` → **1**, `error.message` → **2** (Site A's `… ?: error.message` fallback line plus Site B's unchanged line). Milestone 15's two-handler asymmetry is preserved throughout — Site A prefers the cause, Site B is message-only — first via `!!`/`.message`, then (post-OQ1) via `?.` + `?:` / `.message`.
- [ ] **AC15** — `SyncSettingsScreenshotCaptureTest`'s three tests are green and the file is unmodified at every step, and Implementation Notes states that this milestone changes **no pixels**: on the capture host's disconnected, no-sticky path the subtitle is `null` before and `SyncSubtitle.Absent → null` after (D13). No new before/after image is captured; Milestone 20 owns the after-shot.
- [ ] **AC17** — `git grep -n "rxjava3" ui/preferences/src/main/java/de/danoeh/antennapod/ui/preferences/screen/synchronization/GpodderAuthenticationFragment.kt` returns **zero** at the end of Step 8, while `libs.rxandroid` and `libs.rxjava` remain declared in `ui/preferences/build.gradle` — `BugReportViewModel` and `NextcloudAuthenticationFragment` still need them, so removing the module's Rx dependency is **not** a criterion of this milestone and must not be attempted.

**Idiomatic target achieved — `!!` reduction as evidence, with a committed number** (D12)
- [ ] **AC13** — At the end of Step 9, `grep -o '!!' <file> | wc -l` per file reads exactly: `SynchronizationPreferencesFragment.kt` **22** (down from 30), `GpodderAuthenticationFragment.kt` **12** (unchanged), `NextcloudAuthenticationFragment.kt` **7** (unchanged), `AuthenticationDialog.kt` **0** (unchanged) — slice total **41**, down from **49**. **After the OQ1 Gap 16 fix (2026-09-01, separate post-equivalence commit):** `GpodderAuthenticationFragment.kt` **12 → 11** (the `error.cause!!` removed), slice total **41 → 40**. Additionally `grep -c 'supportActionBar' SynchronizationPreferencesFragment.kt` → **1** (one access site, down from five statements). A count **lower** than 22 fails this criterion as surely as a higher one: it means `!!` was removed somewhere D12 did not authorise — the 11 `findPreference!!`, the 3 `SynchronizationQueue.instance!!` (D4) and the Fragment-API preserved NPEs under README convention 4 all **stay**.
- [ ] **AC20** — No subject-less `when` opportunity is introduced or missed: the ViewModel's event branch (`sync_status_error`/`sync_status_success` vs other) is dispatching on two values of one subject and stays an `if`/`else` or a subject-ful `when` on `event.messageResId` — it is **not** rewritten into a subject-less `when`, because that is not what the idiom is for. `./gradlew ktlintCheck` is BUILD SUCCESSFUL at the end of every step.

**The build is green at CI's real bar**
- [ ] **AC16** — At the end of Steps 5, 6, 8 and 9, all four CI build tasks are BUILD SUCCESSFUL and the output is pasted: `./gradlew assemblePlayDebug`, `./gradlew assemblePlayRelease`, `./gradlew assembleFreeRelease`, `./gradlew assemblePlayDebugAndroidTest`. `./gradlew checkstyle lint` and `./gradlew ktlintCheck` are BUILD SUCCESSFUL at the end of Steps 6, 8 and 9. `./gradlew :app:assembleDebug` is BUILD SUCCESSFUL at the end of **every** step.
- [ ] **AC21** — No public API break is visible to Java callers outside the module. `SynchronizationPreferencesFragment` stays `public` and no-arg-constructible (`PreferenceActivity.java:75` instantiates it reflectively) and still extends `AnimatedPreferenceFragment`, whose `onCreate` transitions and `onViewCreated` background are not bypassed (Research hazard 7); `GpodderAuthenticationFragment` stays `open` (README convention 2) and its `TAG` stays a `const val` (convention 3). Proved by `assemblePlayDebug` plus `SyncSettingsHarnessSmokeTest`'s six tests green and unmodified.

**Not applicable to this milestone, asserted rather than assumed.** **Accessibility** (content descriptions, dynamic type) and **dark mode** (no hardcoded colors) attach to the `compose` track's UI work: this milestone creates **no** composable, no layout and no `View` (AC19), and adds no user-visible string — the ActionBar strings it moves are the existing `:ui:i18n` resources rendered through the same `ActionBar` API, so there is nothing new to contrast-check or describe. Those bars bind Milestone 20 and are recorded there, not silently dropped. **Paparazzi snapshots**: no new composable exists, and Milestone 16's attempt did not ship (see the future-work file's Milestone 20 row) — Milestone 20 still owns both the mechanism choice and the snapshot suite. **Instrumented back-stack and deep-link tests** attach to `navigation`, which is not requested and for which no Navigation Component exists anywhere in the repo. **No SDUI contract** is involved. **HSHD**: `SynchronizationCredentials` handles a gpodder.net username and password, and this milestone must not widen their exposure — `AC5`'s error-path tests use the literal `"Wrong username or password"` server message and fabricated credentials only, no real credential value appears in any test fixture, and **no logging statement of any kind is added** to either converted call site.

### Milestone

**Milestone 17 — `:ui:preferences` `screen/synchronization/`, `concurrency` track + MVVM state layer.** Single milestone, single unified PR (code plus spec docs, per the M7/M9/M10/M12/M13/M14/M15/M16 precedent), **ten steps in eight commits**: Steps 2, 3, 4, 5, 6, 7, 8, 10. Steps 1 and 9 produce no repo diff and fold into the following commit's Implementation Notes. **[R1]** The suite goes **41 → 55**: **15** test methods written across **three** new classes, **14** of them in the final suite, because Step 7 deletes the cancellation before-record it replaces (D15). Four test files are created; not one of the eleven pre-existing ones is touched.

Sixth of the six-milestone sync-settings sequence (15 `kotlin` → 15b before-screenshot → 16 toolchain → **17 concurrency + MVVM** → 18 DI wiring → 19 `:storage:preferences` → 20 `compose`), and the sixteenth milestone in the portfolio overall. Branches from an `origin/develop` that already contains Milestones 15, 15b and 16.

This is unaffiliated OSS portfolio work, so "milestone" is case-study structure, not invoicing. The angle it earns is the one migration practices most often fake: **the milestone wrote the tests for the code it was about to replace, against the code it was about to replace, and then proved the conversion by showing that not one assertion had to change.** Three artifacts carry it:

1. **The coverage gap that was found before it could hide.** Research measured that the 41-test suite — genuinely strong on the wizard's synchronous behavior and the screen's EventBus behavior — proved **nothing** about the two RxJava subscriptions being converted. A green suite after the change would have looked like equivalence and demonstrated none. Step 3 closes that with **seven** tests written against the unmodified RxJava code **[R1]**: six that must survive the conversion with **zero** assertion changes (AC9, checkable in one `git diff`), and one — `testInFlightLoginSurvivesDialogDismissal` — written specifically to record the behavior the milestone intends to *break*, so that the one intentional change is evidenced by a green before-run and an assertion flip rather than asserted in prose (D15).
2. **The design decision that protected the instrument.** Six of nine Gpodder tests reach into private fields by reflected name. Hoisting those fields into a ViewModel breaks them at `getDeclaredField` — so the "obvious" full-slice MVVM move would have required rewriting the regression net inside the diff it was meant to police. D3 narrows the ViewModel to the screen that actually blocks Milestone 20, states what that costs, and writes the narrowing into the next milestone's brief rather than quietly redefining the deliverable.
3. **A crash that was found, proved, dated and scheduled — not swept up in a refactor.** Gap 16 was a grep pin in Milestone 15 because nothing could reach it. Planning confirmed `GpodnetService` is constructible without network, so this milestone can finally *prove* the wrong-password NPE with a test — and then deliberately does **not** fix it here, because a crash fix inside an equivalence-proving diff makes the proof unreadable. It ships pinned, named in Milestone 18's brief with its one-line fix, and raised to José as OQ1.

### Out of Scope

- **Any ViewModel for `GpodderAuthenticationFragment`, `NextcloudAuthenticationFragment` or `AuthenticationDialog`** (D3). The wizard's `currentStep`/`devices`/`username`/`password`/`selectedDevice` stay fragment fields. If Milestone 20 decides the dialogs go to Compose, that ViewModel is Milestone 20's scope, and its suite rewrite is forced there by the UI rewrite rather than colliding with an equivalence proof.
- **Any DI graph or Hilt usage** (D2, D4). No `@HiltViewModel`, no `@AndroidEntryPoint`, no `@Inject`, no hand-written `ViewModelProvider.Factory`, no binding of `SynchronizationQueue.instance`, no repository abstraction over `:storage:preferences`. The five `SynchronizationQueue.instance!!` sites stay. Milestone 18.
- **Touching `:net:sync:service`'s nine `postSticky` producers, or introducing a shared Flow holder in `:event` / `:net:sync:service-interface`** (D5). EventBus remains the transport; the bridge is local to `:ui:preferences`. Options (b) and (c) from Research unknown 1 are rejected here and stay available to a later milestone that has a reason to widen.
- **Fixing Gap 16** (D11, OQ1). The wrong-password NPE ships preserved and newly pinned. *(OQ1 was later resolved fix-here — the one-line fix shipped in a standalone follow-up PR after #32 was merged, not in Milestone 18. See the OQ1-resolved note.)*
- **Fixing the `devices` data race** (D10b). Preserved exactly; `@Volatile` count stays at 3; README convention 5 unchanged.
- **Removing RxJava from `:ui:preferences`** (AC17). `BugReportViewModel` and `NextcloudAuthenticationFragment` still use it. `libs.rxandroid`/`libs.rxjava` stay declared.
- **Migrating `BugReportViewModel` to Coroutines/StateFlow, or to Kotlin.** It is a shape reference this plan deliberately diverges from (D2), not an edit target. Its zero-test state is recorded, not remedied here.
- **Cataloguing `kotlinx-coroutines-test`, Turbine, MockWebServer, or declaring `mockito-core`** (D9, D15). **[R1]** The red-team's C1 offered cataloguing `kotlinx-coroutines-test` as one way to make the cancellation proof deterministic; **that option is declined and D9 is not reopened.** The determinism it would buy is obtained instead by the `ioDispatcher` seam, Robolectric's existing `idle()` pumping, the hand-written file-private `ManualDispatcher`, and RxJava's `TestScheduler` from the already-declared core artifact — all single-threaded, none of them a new dependency (AC8). `kotlinx-coroutines-test` becomes genuinely necessary at Milestone 18, when the ViewModel's reads go suspending behind an injected repository, and is written into its brief.
- **`repeatOnLifecycle` / `flowWithLifecycle`** (D7). Rejected as not like-for-like against the load-bearing `onStart`/`onStop` ordering. An explicit `Job` at the exact former `register()`/`unregister()` positions is used instead.
- **Any Compose, any `ComposeView`, any theme bridge, any snapshot test** (AC19). Milestone 20, including the still-open Paparazzi-vs-Robolectric-native-graphics choice Milestone 16 left it.
- **Converting `NextcloudAuthenticationFragment`'s `nextcloudLoginFlow` async path**, or reducing its 7 `!!`. Not named in this milestone's scope and not touched.
- **Reducing the 11 `findPreference<Preference>(…)!!` sites** (D12). A `PreferenceFragmentCompat` API artifact that dies with Milestone 20, not with a ViewModel.
- **Moving `updateScreen()` into the ViewModel.** It is preference-row rendering against `PreferenceFragmentCompat`'s own API; hoisting it produces state shaped by the widget Milestone 20 removes.
- **Renaming the `lifecycle-runtime-compose` version ref** to something that describes the three non-Compose artifacts it now governs (OQ2). Touches `app-wearos`; its own micro-task.
- **The `kotlin`, `gradle-kts`, `di`, `compose` and `navigation` tracks.** Every build file stays Groovy; `:storage:preferences` stays Java; no Navigation Component exists anywhere in the repo.

### Open Questions

| | Question | Status |
|---|---|---|
| **OQ1** | Gap 16: ship as a preserved, pinned crash, or fix here? | **Resolved 2026-09-01 — José chose fix-here.** #32 was merged first; the fix then landed as its own commit on a standalone follow-up branch `fix/gpodder-auth-causeless-exception-crash` off `origin/develop`: `error.cause?.message ?: error.message` at Site A, inverted test, doc recount 41→40 / `!!` 12→11. |
| **OQ2** | `lifecycle-runtime-compose` version ref is a misnomer | Open. Own micro-task. Does not block. |
| **OQ3** | Upstreaming intent (standing, carried from M7/M9/M11–M16) | Open, standing. Does not block. The earlier "except for OQ1" caveat is now removed — OQ1 is resolved fix-here. |

> **RESOLVED 2026-09-01 — José chose fix-here.** After PR #32 was merged, the one-line fix (`error.cause?.message ?: error.message` at Site A only; Site B unchanged) shipped in a standalone follow-up bugfix PR on branch `fix/gpodder-auth-causeless-exception-crash`, past the AC9 equivalence window, with the inverted test. Effects: D10 row (d) / AC12 row (d) → FIXED; AC13 `GpodderAuthenticationFragment.kt` 12→11, slice 41→40; AC14 counts become `error.cause!!.message`→0 / `error.cause?.message`→1 / `error.message`→2. **`tasks/antennapod-sync-settings-kotlin-milestone-15.md`'s AC13 four-count `grep -F` pin and its D7 two-handler table are now stale for Site A** (Site A's operator changed here; the A-cause-first / B-message-only asymmetry itself is preserved) — not rewritten, flagged here for a reader following that trail. The rest of this section is kept as the record of the decision.

**OQ1 — This milestone ships a known, now-provable app crash on the most common gpodder.net login failure, and it does so deliberately.** (D11.)

Entering a wrong gpodder.net password crashes AntennaPod today: `GpodnetServiceAuthenticationException("Wrong username or password")` carries no cause, so `error.cause!!.message` NPEs inside the error handler. This milestone rewrites that exact statement and **keeps it**, adding `testWrongPasswordErrorPathThrowsFromNullCause` to pin it.

**The technical case for preserving is strong** and is D11's: a crash fix inside the diff whose job is to prove three async conversions changed nothing makes the proof unreadable, and Milestone 18's injected `GpodnetService` is where the fix can be verified through the real failure path rather than a reflected fake. Repo convention (`net/sync/service-interface/README.md` #11) says pin-and-track, not drive-by-fix, and this plan follows it.

**The case for fixing now is not technical, which is why it is José's.** This is an OSS app with real users, the milestone sequence may be offered upstream (OQ3), and "we found a reachable crash on the most common failure path and shipped it unfixed for one more milestone" reads differently in a public case study than it does in an engineering log — even with a test pinning it and a scheduled fix. That is a positioning and public-claims judgement, which the root `CLAUDE.md` reserves for José.

**Recommendation: preserve, pin, fix in Milestone 18** — the plan as written. **If José prefers to fix it here**, the change is bounded and named in advance: the Site A `error.cause!!.message` line becomes `error.cause?.message ?: error.message`; `testWrongPasswordErrorPathThrowsFromNullCause` inverts to assert the rendered message instead of the NPE; **AC14 changes** (real post-fix counts: `error.cause!!.message` → 0, `error.cause?.message` → 1, `error.message` → 2 — the `… ?: error.message` fallback is a second literal occurrence this line originally undercounted); **AC13's count for that file drops 12 → 11** and the slice total **41 → 40**; **AC12 row (d) flips to FIXED**; and Milestone 15's `AC13` grep pin and `ui/preferences/README.md` convention 4 gain a dated note recording the supersession. Doing it as **its own commit, after Step 8**, keeps the equivalence proof intact and the fix independently reviewable — that is the only way this plan would accept it.

**OQ2 — The catalog's `lifecycle-runtime-compose = "2.8.7"` ref now governs three artifacts, only one of which is Compose.** (D8.)

`libs.versions.toml:59` already points `androidx-lifecycle-viewmodel-ktx` at it, and D8 adds `androidx-lifecycle-runtime-ktx` to the same ref for consistency. The name is now actively misleading, and all three resolve to **2.9.4** in practice via transitive upgrade, so the pinned number is decorative as well. The honest fix is a rename to `lifecycle` plus a bump to the resolved version — which touches `app-wearos/build.gradle:77` and is a real (if small) cross-module change with nothing to do with this milestone. **Worth its own micro-task**; not urgent, and this milestone makes it one line more visible rather than worse.

**OQ3 — Upstreaming intent.** Standing, carried unchanged from M7/M9/M11–M16 (`tasks/antennapod-model-kotlin-future-work.md` item 2). Answered for Milestone 16 on 2026-08-20 ("no worries as a standalone upstream PR"); noted here because this milestone's shape is the most upstream-palatable of the sequence so far — it converts real async code, adds **14** tests **[R1]**, removes 8 `!!`, and adds exactly one catalog line and two `implementation` lines — **except** for OQ1, which is the one thing an upstream maintainer would question. *(OQ1 was resolved fix-here on 2026-09-01, shipped as a standalone follow-up bugfix PR; that caveat no longer applies and this milestone reads as a straightforwardly attractive upstream contribution.)*

## Open Questions
_Running list — resolved items move into the Plan's Resolved Decisions._

- **OQ1 — RESOLVED 2026-09-01 (fix-here):** José chose to fix Gap 16 rather than defer it. After PR #32 merged, the one-line fix (`error.cause?.message ?: error.message` at Site A) shipped in a standalone follow-up bugfix PR on branch `fix/gpodder-auth-causeless-exception-crash`, with the inverted test. See the Plan's OQ1-resolved note, D10 row (d), AC12/AC13/AC14, and the Implementation Notes "Gap 16 fix" subsection.
- **OQ2:** rename the `lifecycle-runtime-compose` catalog version ref. Own micro-task.
- **OQ3:** standing upstreaming question, carried from Milestones 7–16.

## Plan — Revision 1
_By: legacy-android-planner | 2026-08-31 — what moved in response to the plan red-team's Loop 1 CHALLENGE, concern by concern. Every edit named here is marked **[R1]** at its site._

**C1 (MAJOR) — the cancellation proof was unbuildable as specified. ADOPTED, by options (ii) + (iii) + (iv); option (i) declined.**
- **New decision D15** specifies the whole harness: a separate test file `GpodderAuthenticationFragmentCancellationTest.kt`; a hand-written, file-private ~8-line `ManualDispatcher` (a `CoroutineDispatcher` that queues `Runnable`s and runs them only on an explicit `runQueued()` — no thread, no executor, no latch, `isDispatchNeeded` left at its default so `withContext` always enqueues); and, for the Step-3 "before" half, RxJava's `TestScheduler` from the already-declared core artifact.
- **(a) — the AC9 contradiction is gone.** The cancellation tests no longer live in `GpodderAuthenticationFragmentAsyncCharacterizationTest.kt`. **Step 7** was rewritten: that file gets **setup-only** edits, the cancellation file gets the flip. **AC9** (mitigation (iii)) now freezes only the **six Step-3 test-method bodies** — "no `assert`-containing line in the six Step-3 tests changes; no existing test-method body changes" — so a net-new asserting test in a different file is not a violation. The freeze it existed for is not weakened: the six bodies stay byte-identical, and **AC10** independently forbids modifying any of the eleven pre-existing test files.
- **(b) — the determinism objection is answered, not argued around.** The red-team was right that `Dispatchers.Unconfined` leaves no in-flight window. Under `ManualDispatcher` the window is *explicit and assertable* (`manual.queued() == 1`, `fake.calls` empty) rather than raced for, and everything still runs on Robolectric's single main thread — which is exactly why Research hazard 5's flakiness (a real background dispatcher) does not apply.
- **(c) — the unfalsifiable claim is withdrawn.** **AC12 row (a)** no longer says the Step-7 test is "confirmed to fail if run against the pre-Step-7 code"; it cannot be run there at all (`ioDispatcher` does not exist, and an Rx call site has no dispatcher seam). Mitigation (iv) is answered concretely: the artifact is a **paired-file diff** — the Step-3 green run of `testInFlightLoginSurvivesDialogDismissal` against production source AC4 proves byte-identical to `origin/develop`, plus the Step-7 diff deleting it and adding its green opposite with the scenario unchanged. Both halves are green runs on real code.
- **Option (i) — cataloguing `kotlinx-coroutines-test` — is declined and D9 is not reopened.** Recorded in Out of Scope with the reason: determinism here comes from single-threadedness, which the design guarantees, not from a test framework. The bill is still handed to Milestone 18, where suspending repository reads make it genuinely necessary.
- **Cascade:** Step 3 (+1 file, +1 test), Step 7 (rewritten), Step 8 (cancellation file explicitly untouched), File Scope (created test files **three → four**), AC3/AC4 counts, AC5 (scoped to the six), AC8 (`ManualDispatcher` and `TestScheduler` named as dependency-free), AC10 (**exactly four** created), AC1 (per-class rows for the three new classes).

**C2 (MAJOR) — ViewModel-lifetime EventBus registration was not equivalence-preserving. ADOPTED in full; the suggested shape taken as-is.**
- **D5 rewritten.** The `init` → `onCleared` registration is withdrawn as wrong, and the red-team's `callbackFlow { register … awaitClose { unregister } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), null)` is adopted. Five properties are called out as load-bearing: `WhileSubscribed`; `stopTimeoutMillis = 0`; `replayExpirationMillis = 0` (the subtle half — the default cache would re-introduce the divergence through a stale replay); a separate private `SyncServiceEventSubscriber` object (a `callbackFlow` lambda cannot carry `@Subscribe`); and `ThreadMode.POSTING` so the sticky replay reaches `trySend` synchronously on the registering thread.
- **Verified against the frozen suite, with the user-confirmed line numbers:** `SynchronizationPreferencesFragmentLifecycleTest.kt` posts **non-sticky** at `:81`, `:97`, `:102` and **sticky** at `:67`; `removeStickyEvent` appears only at `:39`/`:45` (setUp/tearDown hygiene). The sticky/non-sticky distinction is therefore live inside the verification instrument, which is what makes lifetime-registration indefensible rather than merely inelegant.
- **Because lifetime-registration was NOT kept, the executable test the red-team asked for is still added** — the equivalence claim is verified, not assumed: **new AC22**, backed by two Step-4 tests. `testCollectorCancellationUnregistersFromEventBus` asserts `hasSubscriberForEvent(SyncServiceEvent::class.java)` returns to its **recorded pre-collection value** after the collector job is cancelled (recorded rather than hardcoded `false`, so pre-existing global subscribers cannot make it flaky). `testEventsPostedWhileNotCollectingAreSeenOnlyIfSticky` is the stopped-then-restarted pair: a non-sticky post delivered with no collector is **missed** by the next collector (which also proves `replayExpirationMillis = 0` resets to `initialValue`), a sticky post **is** replayed.
- **Cascade:** D2's `viewModelScope` row reversed ("used — as `stateIn`'s sharing scope, and nothing else"); D7 gains the note that the two preserved source positions now carry registration itself; **D8 reversed** to declare `androidx-lifecycle-viewmodel-ktx` as well (`viewModelScope` ships there) — **two** `implementation` lines, still **one** catalog line, since `libs.versions.toml:59` already declares it; **Step 2** rewritten (the `dependencyInsight` probe demoted from decision point to no-movement check); **Step 4** re-specified (the withdrawn `testOnClearedUnregistersFromEventBus` replaced, plus the note that under `WhileSubscribed` every test must start a collector or `.value` stays frozen at `null` forever); **Step 5** gains the `stateIn` start-up-timing **verification gate** and the pre-authorised `startObserving()`/`stopObserving()` fallback; **AC2** and **AC7** updated.
- **One thing deliberately not claimed:** `stateIn`'s inline start-up before `onStart()` returns is a reasoned expectation about coroutine internals, not a measured fact. Rather than assert it, Step 5 makes the four frozen lifecycle tests the gate and pre-authorises a named, equally equivalence-preserving fallback whose selection must be recorded with the failing output.

**C3 (MINOR) — D9's ".value only" claim contradicted D6's pin. ADOPTED.**
- **D6** now specifies the mechanism in full: `testConsecutiveEventsWithSameMessageResIdAreBothDelivered` (**renamed** from `…BothEmit`, because delivery is what it can observe and a dropped emission is invisible to `.value`) collects into a list from a `Dispatchers.Main.immediate` scope with `idle()` between posts, and cancels the job before asserting `[null, event1, event2]`. **D9's** wrong bullet is corrected at the site where it was wrong. **Step 4** test 3 updated to match, with its determinism note. No new dependency.

**C4 (MINOR) — StateFlow conflation vs EventBus per-post delivery was unrecorded. ADOPTED.**
- **D10 gains row (e)**: per-post delivery is **PRESERVED IN PRACTICE, NOT IDENTICAL**, with the bounded real case named (`SynchronizationQueueImpl.kt:136` immediately followed by `SyncService.kt:61`), an explicit statement that no test pins that window and why one cannot without a scheduler D9 declines to add, and the parallel drawn to D11's `CompositeException`→bare-NPE disclosure. **D6** carries the same limit inline; **AC12** is now a **five-row** table.

**C5 (MINOR) — "M20 prerequisite satisfied" overstated the delivery. ADOPTED.**
- **D3 reason 2** rewritten: the old "satisfies the blocking prerequisite this milestone owes Milestone 20" is **withdrawn**. What ships is the class, the `StateFlow` + immutable-state pattern, the collection seam and the module conventions; what does **not** ship is the screen's state model — `updateScreen()`, `isProviderConnected()`, provider summary and icon, username/host-url and per-row state stay in the fragment, and hoisting them is Milestone 20's. D3's tradeoff paragraph and **Step 6** now say the same thing in the same words, and **Step 10** writes that scoping into the future-work file's Milestone 20 row.

**C6 (MINOR) — cross-test EventBus pollution, since `onCleared()` never runs under Robolectric. ADOPTED, and largely dissolved by C2's fix.**
- **D5's closing paragraph** analyses it instead of assuming: registration no longer depends on `onCleared()` at all, so it ends at collector cancellation — i.e. at `onStop`, which the frozen tests do reach. For tests that never stop the fragment the residue is one subscriber per attached fragment, **which is exactly what `develop` already leaves** (the current fragment registers in `onStart` and is never stopped or destroyed), so the accumulation is pre-existing and unchanged in kind and count. **Step 5** records the analysis; **AC1**'s per-class counts are the check; **AC22**'s first test is the direct executable guard, written to be immune to whatever the global bus already holds.

**Considered and deliberately not changed.** D7, D10b, D10d/D11/OQ1, D12, D13, D2's core (plain `ViewModel`, no factory, `StateFlow` + immutable state), D8's single catalog line, and File Scope's production-source fencing — all cleared by the red-team and left as written, apart from the cascade edits named above. D3's central narrowing (ViewModel scoped to `SynchronizationPreferencesFragment`, the 41-test net byte-identical) is unchanged; only its Milestone-20 framing moved. OQ1 (Gap 16 ships preserved and pinned) is unchanged and still open for José.

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-08-28 | Loop 1 of max 2_

### Verdict
CHALLENGE

### Concerns

**C1 — the D10a cancellation proof (`testInFlightLoginIsCancelledWhenDialogIsDismissed`) is unbuildable as specified**
- **Severity:** MAJOR
- **Class:** coverage gap / self-contradicting acceptance criterion (`concurrency` track)
- **Concern:** This test is the milestone's *only* evidence for its one intentional behavior change (D10a, AC12 row a), and three things about it do not hold together:
  - **(a) It violates AC9 by construction.** Step 7 says it "Touches ... `GpodderAuthenticationFragmentAsyncCharacterizationTest.kt` (setup only, per D14/AC9)" and in the same sentence "Adds one test — `testInFlightLoginIsCancelledWhenDialogIsDismissed`". AC9 requires the Step-3→Step-8 diff of that file to touch "**only** import lines and the `@Before`/`@After` bodies … Zero lines containing `assert` are added, removed or changed." A net-new `@Test fun` with assertions in that file breaks the criterion the plan calls "the single strongest equivalence signal in the milestone".
  - **(b) D9's determinism seam cannot produce the scenario.** With `ioDispatcher = Dispatchers.Unconfined` (D9) and `lifecycleScope` = `Dispatchers.Main.immediate` running inline on Robolectric's main thread, `lifecycleScope.launch { try { withContext(ioDispatcher) { … } ; <success> } catch { … } }` runs to completion **synchronously inside `login.performClick()`**. There is no in-flight window in which to call `dismiss()` and observe non-cancellation-vs-cancellation. A controllable in-flight state needs either `kotlinx-coroutines-test` (`StandardTestDispatcher` + `advanceUntilIdle`), which D9 explicitly refuses, or a real background dispatcher + latch — the exact path Research hazard 5 flags as "the most likely source of flaky/hanging tests."
  - **(c) "Confirmed to fail if run against the pre-Step-7 code" (AC12 row a) is not executable.** The test depends on the `ioDispatcher` field, which does not exist before Step 7, so the fail-on-old-code property — the thing that makes it a real behavior-change proof rather than a tautology — cannot be demonstrated as written.
- **Evidence:** task file Step 7 (~L558–559), AC9 (~L612), AC12 row a (~L626), D9 (~L424–433); Research "Track-specific hazards" item 5.
- **Suggested mitigation:** Pick one and make the plan self-consistent: (i) catalogue `kotlinx-coroutines-test` for this milestone (reopening D9) and pin the cancellation deterministically with a test dispatcher; or (ii) put the cancellation proof in a **separate new test file added to File Scope**, with an explicitly-described concurrent-dispatcher + synchronization-primitive harness and a written determinism argument. In either case: (iii) reword AC9 to freeze only the **six Step-3 test-method bodies** ("assertions in the six Step-3 tests are byte-identical; no existing test-method body changes"), so a net-new test is not a violation; and (iv) state concretely how the fail-on-old-code check is performed (e.g. a throwaway verification commit, or a reasoned argument from the discarded `Disposable`).

**C2 — D5's ViewModel-lifetime EventBus registration is not equivalence-preserving, and a strictly better shape is available**
- **Severity:** MAJOR
- **Class:** silent behavior change from mechanical translation (`concurrency` / lifecycle)
- **Concern:** D5 registers the EventBus subscriber for the ViewModel's whole lifetime (`init` → `onCleared`), replacing the fragment's per-`onStart`/`onStop` `register()`/`unregister()`. The plan's equivalence argument is "safe *because* all 9 producers post stickily and nothing removes the sticky", with the non-sticky case delegated to a Step-9 README note.
  - For any event delivered **while the fragment is stopped**, the designs diverge: old code (unregistered) misses it and only re-syncs to the latest *sticky* on the next `onStart`; new code keeps mutating `_syncStatus.value`, and the collector replays that value on the next `onStart` — so a non-sticky event the old screen would never have shown becomes visible. Production has zero non-sticky `SyncServiceEvent` posts **today** (grep across repo: 9 `postSticky`, 0 `post`), but the **frozen** regression suite already posts non-sticky events (`SynchronizationPreferencesFragmentLifecycleTest.kt:81,97,102`), so the sticky/non-sticky distinction is live in the verification instrument, not hypothetical.
  - A README convention is not an enforcing guard, and the plan asserts AC2 without walking through why the four frozen lifecycle tests still pass under lifetime-registration (they do — a `sticky = true` subscriber also receives non-sticky posts, and the started fragment's collector is live — but that is load-bearing and unstated).
  - `callbackFlow { register(this); awaitClose { unregister(this) } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(…), null)` reproduces the `onStart`-register / `onStop`-unregister lifecycle **and** D7's ordering (registration is driven by the collector subscribing, at the exact `lifecycleScope.launch` position) more faithfully than "register in `init`". It also matches the repo's only StateFlow-ViewModel precedent — `app-wearos/MainViewModel.kt` uses `viewModelScope` freely — which D2 claims to follow.
- **Evidence:** task file D5 (~L344–367), D2 (~L316), AC2 (~L606); `SynchronizationPreferencesFragmentLifecycleTest.kt:81,97,102`; `net/sync/service/**` grep (9 `postSticky`, 0 `post`); `app-wearos/src/main/java/de/danoeh/antennapod/wearos/MainViewModel.kt:24-42`.
- **Suggested mitigation:** Adopt the `callbackFlow` + `stateIn(SharingStarted.WhileSubscribed)` shape (registration tied to collection, i.e. to fragment start/stop). If lifetime-registration is kept, justify it explicitly against the stopped-state divergence and add an executable test in the **new** suite pinning stopped-then-restarted behavior for both a sticky and a synthetic non-sticky `SyncServiceEvent`.

**C3 — D9 claims all ViewModel tests are plain `.value` assertions, but D6's pin needs to observe the emission stream**
- **Severity:** MINOR
- **Class:** coverage gap / internal inconsistency
- **Concern:** D9 states every ViewModel-side test is "read as `.value` — a plain JUnit assertion, no `runTest`." Step 4's `testConsecutiveEventsWithSameMessageResIdBothEmit` (pinning D6's non-`data class` finding) is inherently about the emission *stream* — conflation is invisible to `.value` reads — so it must run a collector. Without `kotlinx-coroutines-test` this needs an explicitly-plumbed `Dispatchers.Unconfined` collector into a list.
- **Evidence:** task file D9 (~L428), Step 4 test 3 (~L546), D6 (~L371–381).
- **Suggested mitigation:** Specify the collector mechanism for that one test and confirm its determinism, or concede a narrow `kotlinx-coroutines-test` dependency.

**C4 — StateFlow conflation vs EventBus per-post delivery is an unrecorded non-equivalence**
- **Severity:** MINOR
- **Class:** silent behavior change from mechanical translation
- **Concern:** `StateFlow` conflates; EventBus `ThreadMode.MAIN` delivers every post. When producers post in quick succession (e.g. `SynchronizationQueueImpl:136` `sync_status_started` then `SyncService.kt:61` `sync_status_wait_for_downloads`), the old screen ticks through each subtitle; the new collector may render only the latest. Rare in practice (the 8 `SyncService` posts are seconds apart), but the milestone's thesis is *provable* equivalence, and D11 already sets the precedent of recording a preserved-but-not-identical difference (`CompositeException` vs bare NPE).
- **Evidence:** task file D6 (~L375–381) addresses only the `equals`/`data class` angle, not backpressure conflation; Research finding 3 (`SyncService.doWork()` post sequence).
- **Suggested mitigation:** Record the conflation difference in Implementation Notes alongside D11's exception-type note, rather than leaving it implicit.

**C5 — "M20 prerequisite satisfied" (D3) overstates what the narrow ViewModel delivers**
- **Severity:** MINOR
- **Class:** milestone/scope framing
- **Concern:** After this milestone the ViewModel holds only ActionBar title/subtitle + the sync event. `updateScreen()` and all screen-body state (`isProviderConnected`, provider summary/icon, username/hosturl, per-row enabled/visible) stay in the fragment reading statics directly, explicitly deferred. Defensible and disclosed, but D3 reason 2's "satisfies the blocking prerequisite this milestone owes Milestone 20" should be scoped: M20 still owns hoisting `updateScreen()` state; this milestone delivers the class, the `StateFlow`/immutable-state pattern and the module conventions, not the screen's state model.
- **Evidence:** task file D3 reason 2 (~L329) and tradeoff paragraph (~L332); Step 6 ("`updateScreen()` stays in the fragment … moving it is Milestone 20's", ~L553).
- **Suggested mitigation:** Reword D3 reason 2 and the Step 10 future-work update to state precisely what M20 still must hoist.

**C6 — cross-test EventBus pollution: `onCleared()` never runs in the frozen Robolectric tests**
- **Severity:** MINOR
- **Class:** test-suite integrity
- **Concern:** `EventBus.getDefault()` is a process-global singleton and `:ui:preferences` declares plain `libs.eventbus` with no subscriber index (reflection subscription). In the frozen tests the fragment is never destroyed (`commitNow()` + `pause().stop()`, never removed), so `ViewModel.onCleared()` — D5's only `unregister` path — never runs, and dead registered ViewModels accumulate on the global bus across the suite run. Almost certainly benign (dead collectors), but the plan asserts AC1 (all 41 green, identical per-class counts) without analysing it.
- **Evidence:** `SynchronizationPreferencesFragmentLifecycleTest.kt:51-58,110-121`; `ui/preferences/build.gradle` (`implementation libs.eventbus`, no APT); task file D5 (~L360), AC1 (~L605).
- **Suggested mitigation:** Have Step 4/5 verify no cross-test bus pollution explicitly (and note that C2's `stateIn(WhileSubscribed)` shape would also fix this, since it unregisters on collector stop).

### Categories considered and cleared
- **D7 (subscription ordering, `repeatOnLifecycle` rejected).** Verified against `SynchronizationPreferencesFragmentLifecycleTest`: with `Dispatchers.Main.immediate` inline on Robolectric's main thread, `lifecycleScope.launch { syncStatus.collect { … } }` starts synchronously and delivers the current `StateFlow` value before `onStart` returns, so `updateActionBar()` writes the subtitle first and the sticky replay overwrites it — `testStickyEventReplaysOnStart` and `testOnStartSetsTitleAndOnStop…` both still discriminate correctly; `onStop` "cancel then clear" preserves "unregister then clear". `repeatOnLifecycle` rejection is well reasoned. Sound, subject to C2's registration-lifetime point.
- **D10b (`devices` race PRESERVED).** `withContext(Dispatchers.IO)` in production keeps the write off-main and the non-`@Volatile` field a genuine race, with the same resumption happens-before edge `observeOn` provides today; `@Volatile` count stays 3. "Preserved" is accurate — "preserved *exactly*" slightly overstates (pool identity/timing differ) but observable behavior is equivalent.
- **D10d / D11 / OQ1 (Gap 16 crash PRESERVED).** Reasoning is correct (a crash fix inside an equivalence-proving diff makes the proof unreadable), the `CompositeException`→bare-NPE type change is disclosed, the one-line fix and its test are handed to M18 by name, and the positioning question is correctly escalated to José as non-blocking OQ1.
- **File Scope.** `:net:sync:service` (9 `postSticky`), `:event/SyncServiceEvent.kt`, `screen/bugreport/**` correctly fenced off; AC10/AC11/AC18/AC19 enforce with `git diff`/`grep`. Grep confirms nothing in `:net:sync:service` needs to open. Nothing forced open that the plan claims closed.
- **D2 (plain `ViewModel`, no factory, `StateFlow` + immutable state).** Correction to the pre-research block is correct and evidenced (`BugReportFragment.java:46` default factory; no hand-written factory in the module); immutable-state divergence from `BugReportViewModel`'s StateFlow-incompatible mutable-reference pattern is right.
- **D8 (one catalog line).** `androidx-lifecycle-runtime-ktx` on the existing `lifecycle-runtime-compose` ref matches the file's own precedent (`libs.versions.toml:59`); resolves upward to 2.9.4, provable no-op; AC6 guards against version movement.
- **D12 (`!!` 30→22, slice 49→41) / D13 (no pixel change).** The five `(activity as AppCompatActivity?)!!.supportActionBar!!` statements verified at lines 41/51/69/158/238; collapse to one `actionBar()` helper = 2; `Absent`/`Cleared` sealed cases make null-vs-empty explicit. Capture host runs disconnected + no-sticky → `null` before and `Absent → null` after; geometry unchanged. Numbers check out.

### Loop status
Loop 1 of max 2 complete. Planner addresses C1 and C2 (MAJOR) and folds in C3–C6, then re-invokes for Loop 2. If Loop 2 still CHALLENGEs, escalate to José with a descope / accept-risk / bespoke-prerequisite recommendation.

## Red-Team Verdict — Plan (Loop 2)
_By: legacy-android-red-team | 2026-08-31 | Loop 2 of max 2 (final)_

### Verdict
APPROVE

**The plan proceeds to `android-migration-developer`.** Both Loop 1 MAJOR concerns (C1, C2) are closed by real design changes, not wording. C3–C6 are genuinely folded in. Counts are internally consistent. The residual concerns below are all MINOR — implementation notes for the developer, not blockers — and every one is resolvable inside the existing File Scope and acceptance criteria.

### How the Loop 1 concerns were verified closed

**C1 — cancellation proof — CLOSED.**
- **AC9 contradiction gone.** The asserting cancellation test moves to its own File-Scope file `GpodderAuthenticationFragmentCancellationTest.kt` (AC10 lists exactly four created test files; `ManualDispatcher` is file-private, not a fifth file). AC9 now freezes only the six Step-3 bodies in the *other* file, and AC10 independently forbids touching any of the eleven pre-existing test files. The two opposite contracts ("nothing asserted here changed" vs "exactly one thing changed") now live in separate files. Consistent.
- **`ManualDispatcher` is genuinely deterministic and genuinely holds the coroutine mid-flight.** Verified against coroutine dispatch semantics: `withContext(manual)` with `isDispatchNeeded` left at its default `true` always enqueues the block Runnable, so after `performClick()` the coroutine is provably suspended with `manual.queued() == 1` and `fake.calls` empty — an explicit, assertable in-flight window rather than a raced one. `dismiss()` drives the `DialogFragment` to `DESTROYED`, cancelling `lifecycleScope`; the queued continuation was dispatched in `MODE_CANCELLABLE` (via `startCoroutineCancellable`), so when `runQueued()` finally runs it, `DispatchedTask.run()` sees the cancelled job and resumes with `CancellationException` *instead of* executing the io block — which is exactly why the assertion is "the fake recorded nothing." Everything runs on Robolectric's single main thread; there is no background dispatcher, so Research hazard 5's flakiness class does not apply. Sound.
- **Rx `TestScheduler` "before" record is sound.** `Completable.fromAction{…}.subscribeOn(Schedulers.io())` with a non-auto-running `TestScheduler` defers the action until `triggerActions()`, giving the same explicit in-flight window; with no disposal, `triggerActions()` after `dismiss()` runs the work to completion and `observeOn` (trampolined) delivers the callback against detached views — a positive executed record of current behavior. `TestScheduler` ships in the already-declared core `rxjava` artifact (adds no dependency); the named `Scheduler`-subclass fallback covers the resolution risk.
- **The unfalsifiable "fails on old code" claim is withdrawn** (AC12 row a) and replaced by a paired-file diff — Step-3 green run against source AC4 proves byte-identical to `origin/develop`, plus the Step-7 diff deleting it and adding its green opposite. Both halves are green runs on real code; this is honestly a stronger artifact than a single red→green, and the plan says so without overclaiming.

**C2 — EventBus registration lifetime — CLOSED.**
- **`callbackFlow{ register … awaitClose{ unregister } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), null)` correctly reproduces per-`onStart`/`onStop` registration.** The fragment's collector starts at the exact source position `register()` occupies and is cancelled at the exact position `unregister()` occupies (D7); `WhileSubscribed` ties the `callbackFlow` upstream — and therefore EventBus registration — to that collector's subscription. Registration lifetime is now per-`onStart`/`onStop`, same as `develop`, by construction rather than by README convention.
- **`stopTimeoutMillis = 0` and `replayExpirationMillis = 0` reasoning is correct.** Zero stop-timeout means unregistration is not deferred through a timer past `onStop`. `replayExpirationMillis = 0` is the subtle half and the plan gets it right: the kotlinx contract resets `stateIn`'s `StateFlow` to its `initialValue` (`null`) when the upstream stops, so a restarted screen shows nothing until EventBus itself replays the sticky — matching `develop`. The default (`Long.MAX_VALUE`) would re-introduce the stopped-state divergence through a stale cached value; the plan identified this unprompted.
- **AC22 is an adequate executable pin.** `testEventsPostedWhileNotCollectingAreSeenOnlyIfSticky` exercises exactly the sticky-vs-non-sticky stopped-then-restarted divergence flagged in Loop 1 (non-sticky post with no collector → missed by next collector, which also proves the `replayExpirationMillis` reset; `postSticky` with no collector → replayed). Non-sticky is the shape the frozen suite itself posts (`SynchronizationPreferencesFragmentLifecycleTest.kt:81, :97, :102`, verified — sticky only at `:67`), so the distinction is live in the verification instrument. `testCollectorCancellationUnregistersFromEventBus` compares `hasSubscriberForEvent` against a *recorded* pre-collection value, making it immune to any pre-existing global subscriber (C6) while still failing on a leak.
- **The D5 named fallback (`startObserving()`/`stopObserving()`) genuinely preserves the fix.** It registers/unregisters the same `SyncServiceEventSubscriber` synchronously at the same source positions against a `MutableStateFlow` reset to `null` on stop — equally equivalence-preserving on the stopped-state question, removes all dependence on `stateIn` start-up timing, and every Step-4 test (including both AC22 tests) applies unchanged because they assert subscribe/unsubscribe semantics, not the mechanism. Step 5 makes the four frozen lifecycle tests an explicit gate and requires the failing output to be pasted if the fallback is taken — not a silent substitution. Appropriately hedged; the `stateIn`-inline-start-up expectation is labelled a reasoned expectation, not a measured fact.
  - *Independent check:* all four frozen `SynchronizationPreferencesFragmentLifecycleTest` tests survive the new shape regardless of sync-vs-async start-up, because each either idles in `attach()` (line 56) or idles explicitly before asserting — none asserts an intermediate state between `onStart()` returning and the next `idle()`. The Step-5 gate is conservative, which is the right posture.

**C3 — CLOSED.** `testConsecutiveEventsWithSameMessageResIdAreBothDelivered` (renamed to say what it observes) collects the stream into a list from a `Dispatchers.Main.immediate` scope with `idle()` pumping between posts; determinism is single-threadedness plus explicit pump, no `kotlinx-coroutines-test`. D9's wrong ".value only" bullet is corrected at its site.

**C4 — CLOSED.** D10 row (e) records per-post delivery as PRESERVED IN PRACTICE, NOT IDENTICAL, names the bounded real case (`SynchronizationQueueImpl.kt:136` immediately followed by `SyncService.kt:61`), states no test can pin the sub-resume window without a scheduler D9 declines to add, and draws the parallel to D11's `CompositeException`→NPE disclosure. AC12 is now a five-row table.

**C5 — CLOSED.** D3 reason 2's "satisfies the blocking prerequisite this milestone owes Milestone 20" is withdrawn; what ships is the class, the `StateFlow`/immutable-state pattern, the collection seam and the conventions — not the screen's state model. Step 6 and Step 10 use the same words.

**C6 — CLOSED.** D5's closing paragraph analyses cross-test residue rather than assuming: registration now ends at collector cancellation (= `onStop`, which the frozen tests reach via `controller.pause().stop()`), and for tests that never stop the fragment the residue is one subscriber per attached fragment — exactly what `develop` already leaves, unchanged in kind and count. AC22 test 1's recorded-value comparison is the executable guard.

### Counts and consistency — checked, clean
- Suite ledger: 41 → 48 (Step 3: +6 async +1 cancellation-before) → 53 (Step 4: +5 VM) → 53 (Step 5) → 55 (Step 6: +2 VM) → 55 (Step 7: −1/+1 flat) → 55. 15 methods written, 14 in final suite (Step-3 before-record deleted at Step 7), 8 pre-existing + 3 new classes = 11 classes, 41 + 14 = 55. AC1/AC3/AC4/AC5/AC10/AC22 and the Steps ledger all agree.
- File Scope: four created test files, consistent everywhere (no stray "exactly three" left in the plan body); `ManualDispatcher` is file-private, AC10 forbids a `ManualDispatcher.kt`.
- Build surface: one new `[libraries]` catalog line + two `implementation` lines (`runtime-ktx` and the already-catalogued `viewmodel-ktx`, now required because `stateIn(viewModelScope, …)` uses `viewModelScope`). AC7, File Scope, Step 2 and D8 agree.
- Source facts spot-checked: `SyncServiceEvent` is `class SyncServiceEvent(val messageResId: Int)` — plain class, D6 holds. `private var service: GpodnetService? = null` — nullable, assigned only at the host wizard step, so `field("service").set(fragment, fake)` before the login step is a real seam (D14 is not contradicted by Research's "no seam today", which was about `getHttpClient()`, not about injecting a pre-built `GpodnetService`). The five `(activity as AppCompatActivity?)!!.supportActionBar!!` statements and both async call sites match D12/D7/D10.

### Concerns

**C7 — `testWrongPasswordErrorPathThrowsFromNullCause` is frozen by AC9 while AC12(d) discloses that its exception channel changes**
- **Severity:** MINOR
- **Class:** internal inconsistency / coverage-mechanism gap (`concurrency` track)
- **Concern:** This is one of the six Step-3 bodies AC9 freezes byte-identical through Step 8. Under RxJava, the NPE from `error.cause!!.message` inside the error consumer is caught by `LambdaObserver.onError` and routed to `RxJavaPlugins.onError` as a `CompositeException`; under the coroutine rewrite the rethrown bare `NullPointerException` propagates through `lifecycleScope.launch` to the thread's uncaught handler. The plan discloses exactly this difference (D11 reason 2, AC12 row d) yet also asserts the test is "re-run unchanged." If the test body observes the thrown type, the assertion must change and AC9 is violated for this test.
- **Evidence:** task file AC9 (~L760), AC12 row (d) (~L774), D11 reason 2 (~L513), Step 3 test 4 (~L669); `GpodderAuthenticationFragment.kt:130-138`.
- **Suggested mitigation:** State in Step 3 / D14 that this test's throwable capture and normalization live in `@Before`/`@After` (which AC9 permits) — e.g. Step-3 `@Before` unwraps the `CompositeException` to the contained NPE, Step-7 `@Before` stores the propagated NPE directly — so the body assertion (`the culprit is a NullPointerException`) is genuinely byte-identical. Or exempt this one test from AC9 the way the cancellation test was exempted, and cover it under AC12(d) explicitly.

**C8 — the new ViewModel test class needs sticky-event hygiene for AC22 determinism, and the plan doesn't state it**
- **Severity:** MINOR
- **Class:** test-suite integrity
- **Concern:** `EventBus.getDefault()` is process-global and `SyncServiceEvent` stickies are never removed in production. AC22's `testEventsPostedWhileNotCollectingAreSeenOnlyIfSticky` part (a) asserts `syncStatus.value == null` after a *non-sticky* post — which only holds if no sticky `SyncServiceEvent` is left on the bus by an earlier test in the class (part (b) of the same method calls `postSticky`, as does test 1's scenario). `SynchronizationPreferencesFragmentLifecycleTest` handles this with `removeStickyEvent(SyncServiceEvent::class.java)` in both `setUp` and `tearDown`; the plan cites that precedent but doesn't require the new class to copy it.
- **Evidence:** task file Step 4 (~L676), AC22 (~L775); `SynchronizationPreferencesFragmentLifecycleTest.kt:39,45`.
- **Suggested mitigation:** One line in Step 4: `SynchronizationPreferencesViewModelTest` clears `SyncServiceEvent` stickies in `@Before` and `@After`, matching the lifecycle test.

**C9 — D10 row (e) records conflation but not the `callbackFlow` buffered-channel overflow-drop path**
- **Severity:** MINOR
- **Class:** silent behavior change from mechanical translation
- **Concern:** `callbackFlow` defaults to a `BUFFERED` (64) channel; if EventBus ever delivered more than 64 `SyncServiceEvent`s before the sharing coroutine drained, `trySend` returns failure and events are dropped — a different drop mechanism from StateFlow conflation. Negligible in practice (the nine producers post seconds apart, sticky replay is one event), but the milestone's thesis is *provable* equivalence and D10(e) already exists to catalogue exactly this family of bounded difference.
- **Evidence:** task file D5 code sketch (~L364-372), D10 row (e) (~L500).
- **Suggested mitigation:** One clause in D10 row (e): `trySend` on a full buffer would also drop, same negligible real-world envelope; or set an explicit channel capacity/overflow policy in the `callbackFlow` and note it.

### Categories considered and cleared (Loop 2)
- **`ManualDispatcher` mechanism** — verified against `DispatchedTask.run()` / `MODE_CANCELLABLE` cancellation semantics; the io block provably never executes on a cancelled job. Sound.
- **`stateIn(WhileSubscribed(0,0), null)` semantics** — `stopTimeoutMillis`/`replayExpirationMillis` behaviour matches the kotlinx contract; the reset-to-`initialValue` on upstream stop is real. Sound.
- **Four frozen lifecycle tests under the new bridge** — independently traced; all four survive sync-or-async start-up because each idles before asserting. Step-5 gate + fallback is more than sufficient.
- **D7 ordering** — unchanged in shape; now also carries registration itself, which is the point of C2's fix landing inside it. The two source positions carry `develop`'s exact semantics.
- **D9 determinism without `kotlinx-coroutines-test`** — every coroutine is single-threaded with an explicit pump or release (`idle()`, `runQueued()`, `triggerActions()`, `job.cancel()`); the `ioDispatcher` seam is `private var` set by reflection, no API widening. The deferral of `kotlinx-coroutines-test` to Milestone 18 (suspending repository reads) is correct and written into the future-work brief.
- **D10a `CancellationException` rethrow** — `credentialsError.visibility == GONE` is the executable guard against a `catch (Throwable)` that swallows cancellation; correctly identified as the one assertion that goes red on that mistake.
- **D10d / D11 / OQ1 (Gap 16)** — unchanged from Loop 1; still correctly preserved, pinned, handed to Milestone 18 by name, and escalated to José as non-blocking OQ1. The CompositeException→NPE disclosure is honest (see C7 for the one place it collides with AC9).
- **D12 / D13** — `!!` 30→22 and slice 49→41 with the `supportActionBar` grep at 1; no pixel change on the capture host's disconnected/no-sticky path (`null` before, `Absent → null` after). Unchanged from Loop 1, still correct.
- **File Scope fencing** — `:net:sync:service` (9 `postSticky`), `event/SyncServiceEvent.kt`, `screen/bugreport/**` all fenced; AC10/AC11/AC18/AC19 enforce with `git diff`/`grep`. Nothing forced open that the plan claims closed.
- **Scope creep** — the ViewModel is narrowed to `SynchronizationPreferencesFragment` and to the ActionBar + sync event only; the wizard keeps its fields; `updateScreen()` stays in the fragment. D3 states the tradeoff plainly and writes the narrowing into Milestone 20's brief. No architecture change smuggled in under "equivalence."

### Loop status
Loop 2 of max 2 complete — **APPROVE**. Auto-chain proceeds to `android-migration-developer`. C7–C9 are MINOR and handed to the developer as implementation notes; they do not require another planner loop. No escalation to José required (OQ1 remains separately open for him as a non-blocking positioning question).

## Code Review Verdict — Loop 1
_By: migration-code-reviewer | 2026-08-31 | Loop 1 of max 3_

### Verdict
**APPROVE**

No CRITICAL or MAJOR findings are open. Scope is clean, behavioral equivalence is genuinely proven
(not merely "the suite is green"), every stated count re-derives independently, and the one intentional
behavior change is evidenced rather than asserted. The findings below are MINOR/NIT and may be addressed
in this milestone's PR polish or deferred to the `legacy-android-red-team` implementation pass — none
blocks merge.

### What was verified independently (not taken from Implementation Notes)

- **Scope.** `git diff --diff-filter=M --name-only origin/develop...HEAD -- ui/preferences/src/test/` is
  **empty** — none of the eleven pre-existing `screen/synchronization/` test files is touched.
  `--diff-filter=A` lists **exactly four** created test files (`FakeGpodnetService.kt`,
  `GpodderAuthenticationFragmentAsyncCharacterizationTest.kt`,
  `GpodderAuthenticationFragmentCancellationTest.kt`, `SynchronizationPreferencesViewModelTest.kt`);
  `--diff-filter=D` empty. `ManualDispatcher` is file-private inside the cancellation file, not a fifth
  file. The full branch diff is 9 code/build files + 4 docs, all inside File Scope. `git diff
  origin/develop...HEAD -- net/ event/ storage/ app/ app-wearos/ common.gradle build.gradle
  settings.gradle playFlavor.gradle` and `-- .../screen/bugreport/` are both **empty**. No
  `@HiltViewModel`/`@Inject`/`@Module`/KSP (AC18), no `@Composable`/`ComposeView`/`collectAsState`
  (AC19), no `rxjava3` in `GpodderAuthenticationFragment.kt` (AC17 — verified 0), `libs.rxandroid`/
  `libs.rxjava` still declared.
- **Suite 41 → 55.** Fresh `:ui:preferences:testPlayDebugUnitTest --rerun-tasks`: **55 tests, 0
  failures, 0 errors, 0 skipped.** Both flavours green. Per-class: the 8 pre-existing classes are
  row-for-row unchanged from baseline (AuthDialog 4, JavaInterop 1, GpodderChar 9, NextcloudChar 6,
  SyncPrefsChar 8, Lifecycle 4, HarnessSmoke 6, ScreenshotCapture 3 = 41); new classes AsyncChar 6,
  Cancellation 1, ViewModel 7 = 14. AC1/AC3 hold. `:app:assembleDebug`, `:app:assemblePlayRelease`
  (R8), `:ui:preferences:ktlintCheck`, `:ui:preferences:lintPlayDebug`, `:ui:preferences:checkstyle`
  all BUILD SUCCESSFUL.
- **AC9 — the six frozen async characterization `@Test` bodies are byte-identical.** `git diff
  5392ca143 HEAD -- .../GpodderAuthenticationFragmentAsyncCharacterizationTest.kt` reaches only as far
  as the `clickLogin` helper (`@@ -88,8 +80,12 @@`); the six `@Test` methods, which follow
  `showDeviceStep` in the file, are not in the diff at all. Zero assertion lines changed. The four
  frozen `SynchronizationPreferencesFragmentLifecycleTest` tests are green and unmodified, and they
  assert on the *rendered* ActionBar subtitle string (sticky-replay string, "successful" report
  substring, `null` vs `""`), so the fragment-side `render()` formatting moved out of
  `updateLastSyncReport`/`updateActionBar` is genuinely pinned — AC2 satisfied at Steps 5 and 6.
- **Counts:** `!!` per file 22/12/7/0 = **41** (AC13); `grep -c 'supportActionBar'
  SynchronizationPreferencesFragment.kt` → **1**; `grep -c '@Volatile' GpodderAuthenticationFragment.kt`
  → **3**; `error.cause!!.message` → 1, `error.message` → 1, `error.cause?.message` → 0 (AC14);
  `libs.versions.toml` +1 `[libraries]` line, `ui/preferences/build.gradle` +2 `implementation` lines,
  nothing removed (AC7); no `coroutines-test`/Turbine/MockWebServer/`mockito` introduced, no
  `runTest`/`TestDispatcher`/`setMain` in any new test (AC8).
- **Behavioral equivalence — the load-bearing check.**
  - The 6 async characterization tests assert on real behavior (call order, field write order, rendered
    error text, `currentStep`, button/progress-bar visibility) — no tautologies, no assert-free
    invocations. Test 4 (`testWrongPasswordErrorPathThrowsFromNullCause`) genuinely exercises the Gap-16
    NPE path; test 6 genuinely exercises the Site-A/Site-B `error.cause!!.message` vs `error.message`
    asymmetry (the fake throws a cause-less `GpodnetServiceException("nope")`, so Site B rendering
    "nope" proves it does not use Site A's handler).
  - The cancellation pair is a valid paired-file artifact: `testInFlightLoginSurvivesDialogDismissal`
    green at `5392ca143` against production source that `git diff origin/develop -- src/main/` proves
    byte-identical, then `git diff 5392ca143 30351e60d` shows it deleted and
    `testInFlightLoginIsCancelledWhenDialogIsDismissed` added, scenario unchanged. `ManualDispatcher`
    holds the coroutine at `queued() == 1`, `dismiss()` cancels `lifecycleScope`, the io block never
    runs. `credentialsError.visibility == GONE` is a real guard that the converted `catch` rethrows
    `CancellationException` — and the production code does (`catch (error: CancellationException) {
    throw error }` precedes `catch (error: Throwable)` at both sites).
  - The D10 five-row preserve/fix table is accurate against the actual diff: (a) both `Disposable`s
    FIXED / structurally forced — `lifecycleScope` + the paired diff; (b) `devices` write stays inside
    `withContext(ioDispatcher)`, no `@Volatile` added, count still 3 — PRESERVED; (c)
    `MutableStateFlow<SyncServiceEvent?>(null)` + collection at the former `register()` position —
    PRESERVED, `testStickyEventReplaysOnStart` green; (d) `error.cause!!.message` verbatim in the
    converted `catch`, exception-channel change (CompositeException → bare NPE) disclosed; (e) StateFlow
    conflation + `callbackFlow` buffer-drop recorded as PRESERVED-IN-PRACTICE-NOT-IDENTICAL.
  - `callbackFlow { register … awaitClose { unregister } }.stateIn(viewModelScope,
    WhileSubscribed(0, 0), null)` is implemented as specified: `stopTimeoutMillis`/`replayExpirationMillis`
    both 0, a separate `SyncServiceEventSubscriber` object carries `@Subscribe(ThreadMode.POSTING,
    sticky = true)`, registration is tied to the collector. D7 ordering preserved: collector `launch` at
    the former `register()` line in `onStart`, `syncStatusJob?.cancel()` at the former `unregister()`
    line in `onStop`, `actionBar().subtitle = ""` **after** cancel. The Step-5 `stateIn` start-up gate
    passed on the first run (fallback not taken) — confirmed by `testStickyEventReplaysOnStart` and
    `testOnStartSetsTitleAndOnStop…` green in my rerun.
  - AC22: `testCollectorCancellationUnregistersFromEventBus` compares `hasSubscriberForEvent` to a
    *recorded* pre-collection value (immune to a pre-existing global subscriber, C6);
    `testEventsPostedWhileNotCollectingAreSeenOnlyIfSticky` covers the stopped-then-restarted
    non-sticky-missed / sticky-replayed pair. Both green.
  - C7/C8/C9 folded in: C7 — the throwable capture moved to `@Before`/`@After` (Rx error-handler
    unwrap at Step 3 → `Thread.setDefaultUncaughtExceptionHandler` at Step 7), body assertion
    (`capturedCulprit is NullPointerException`) byte-identical. C8 —
    `SynchronizationPreferencesViewModelTest` clears `SyncServiceEvent` stickies in both `@Before` and
    `@After`. C9 — D10 row (e) in Implementation Notes carries the `callbackFlow` BUFFERED(64)
    overflow-drop addendum.

### Findings

**Finding 1 — AC9's literal "only imports and `@Before`/`@After`" is exceeded by helper-method edits**
- **Severity:** MINOR
- **Class:** Tests / Convention
- **File:line:** `ui/preferences/src/test/java/.../GpodderAuthenticationFragmentAsyncCharacterizationTest.kt:75` (`showLoginStep` gains `field("ioDispatcher").set(fragment, Dispatchers.Unconfined)`) and `:79-85` (`clickLogin` wraps `performClick()` + `idle()` in a `try/catch` storing `capturedCulprit`)
- **Finding:** AC9 as worded requires the `5392ca143 → HEAD` diff of this file to touch "only import
  lines and the `@Before`/`@After` bodies." It also touches two private helpers. The **substantive**
  guarantee AC9 exists for — the six `@Test` bodies and their assertions byte-identical — is fully met
  and independently verified, and the helper edits are exactly the seam-swap (Rx plugin handlers →
  `ioDispatcher` reflection) that D14 anticipated, just located in a factored-out helper rather than
  inline. The developer disclosed this in Implementation Notes (§ "AC9"). The `clickLogin` `try/catch`
  is inert for the five non-escaping paths (the production `catch (Throwable)` handles them and renders)
  and is only a capture path for test 4's escaping NPE, so it does not mask a behavior change.
- **Suggested fix:** No code change required. Either (a) note in Implementation Notes that AC9's intent
  is "the six `@Test` bodies are frozen" and the helper plumbing is in-scope seam-swap, so the
  `legacy-android-red-team` implementation pass is not surprised by the literal-vs-actual gap; or (b) a
  one-line AC9 wording tweak in a future revision. Flagging so it is a conscious acceptance, not an
  overlooked AC miss.

**Finding 2 — `SyncSubtitle.Cleared` is dead code**
- **Severity:** MINOR
- **Class:** Quality
- **File:line:** `ui/preferences/src/main/java/.../SynchronizationPreferencesViewModel.kt:74` (`data object Cleared`) and `SynchronizationPreferencesFragment.kt` `render()` `when` branch `SyncSubtitle.Cleared -> ""`
- **Finding:** No code path ever produces a `SyncSubtitle.Cleared` value — `onStop` sets
  `actionBar().subtitle = ""` directly (correctly, to keep D7's cancel-then-clear order literal), so
  the state is never routed through `Cleared` and the `render()` branch for it is unreachable.
  `AGENTS.md` says no dead code. The inconsistency traces to the plan's D12 sealed-interface sketch,
  which listed `Cleared` while also having `onStop` bypass state — faithfully implemented.
- **Suggested fix:** Remove `data object Cleared` and its `render()` branch; the `when` stays
  exhaustive over the three remaining subtypes. (Do not instead route `onStop` through state — that
  would reorder cancel-vs-clear.)

**Finding 3 — recorded deviation `kotlinx.coroutines.stacktrace.recovery=false`: sound, does not weaken equivalence**
- **Severity:** MINOR (assessment, no action required)
- **Class:** Behavioral Equivalence
- **File:line:** `GpodderAuthenticationFragmentAsyncCharacterizationTest.kt:43`, `GpodderAuthenticationFragmentCancellationTest.kt:47` (`@Before`)
- **Finding:** The property is set to disable kotlinx stack-trace recovery, which the Gradle `Test`
  task's `-ea` turns on. The justification (recovery is assertion-gated and is off on *every* Android
  build — debug and release — and on RxJava, so leaving it on would make the coroutine path's
  `error.cause` chain diverge from both the pre-conversion RxJava behavior and real-device behavior:
  `testLoginErrorWithCauseRendersCauseMessage` would see `"java.io.IOException: boom"` instead of
  `"boom"`, and `testWrongPasswordErrorPathThrowsFromNullCause` would see a non-null `error.cause` and
  no NPE) is technically correct. Disabling it makes the test characterize production behavior, which is
  the point of a characterization test. This **strengthens** fidelity to production, does not weaken the
  equivalence claim, and is recorded in Implementation Notes § "Deviations from plan". No planner
  re-approval needed — it is a test-harness detail, not a scope or design change. The one residual
  (bare NPE reaching the *process* uncaught handler on a real device) is asserted in-process only and
  is already listed under "Needs manual / device verification."
- **Suggested fix:** None. Accepted as-is.

**Finding 4 — `SyncServiceEventSubscriber` is `internal`, plan D5 specified `private`**
- **Severity:** NIT
- **Class:** Convention
- **File:line:** `SynchronizationPreferencesViewModel.kt:22`
- **Finding:** The nested subscriber class has no consumer outside the enclosing ViewModel (the tests
  assert via `EventBus.hasSubscriberForEvent`, not on the type). `internal` widens visibility past what
  the plan specified and past what is used. EventBus reflection works either way (verified — the AC22
  tests pass).
- **Suggested fix:** Narrow to `private class SyncServiceEventSubscriber`.

### Loop status
Loop 1 of max 3 — **APPROVE**. Proceeds to `legacy-android-red-team` implementation review. Findings
1–4 are MINOR/NIT and non-blocking; Finding 2 (dead `Cleared`) is the only one worth a code touch and
can ride in PR polish.

## Implementation Notes
_By: android-migration-developer | 2026-08-31_

Branch `concurrency/sync-settings-milestone-17`, cut fresh from `origin/develop` at
`8e4c292174897b75d7b9e1609c595717953a004e` (confirmed ancestor; `origin/develop` tip `8e4c29217`).
Steps 1–10 executed in order. Eight commits (Steps 2, 3, 4, 5, 6, 7, 8, 10); Steps 1 and 9 produced no
repo diff and fold in here. PR not opened.

### Commit message (unified PR)

```
feat(ui:preferences): concurrency + MVVM state layer for the Sync Settings screen (Milestone 17)

Convert the two orphaned RxJava3 Disposable subscriptions in GpodderAuthenticationFragment
and the sticky-EventBus SyncServiceEvent subscription in SynchronizationPreferencesFragment
to Coroutines + Flow, and stand up the first state holder the slice has ever had —
SynchronizationPreferencesViewModel, a plain ViewModel (no Hilt, default factory) scoped
to SynchronizationPreferencesFragment.

concurrency track:
- Site A (login) and Site B (create device) move to lifecycleScope.launch { try {
  withContext(ioDispatcher) { ... } ; <success> } catch (CancellationException) { throw it }
  catch (Throwable) { <error> } }. Both discarded Disposables are FIXED — a fix structurally
  forced by lifecycleScope, which cancels at DESTROYED; recorded as a deliberate behavior
  change, proved by a paired-file diff (D15). The devices data race, the two-handler error
  asymmetry (error.cause!!.message vs error.message), Gap 16's wrong-password NPE and the
  sticky-replay semantics are all PRESERVED — see the D10 table below.
- The sticky SyncServiceEvent bus becomes syncStatus: StateFlow<SyncServiceEvent?> via a
  callbackFlow { register ... awaitClose { unregister } }.stateIn(viewModelScope,
  SharingStarted.WhileSubscribed(0, 0), null) bridge whose EventBus registration is tied to
  collection — i.e. to the fragment's onStart/onStop, matching develop.

MVVM state layer:
- SynchronizationPreferencesViewModel additionally owns the ActionBar title/subtitle as
  SyncSettingsUiState (immutable data class + SyncSubtitle sealed interface). The five
  (activity as AppCompatActivity?)!!.supportActionBar!! statements collapse to one actionBar()
  helper: SynchronizationPreferencesFragment.kt !! 30 -> 22, slice 49 -> 41.
- updateScreen() and the screen-body state stay in the fragment — hoisting them is Milestone
  20's work. The Gpodder wizard keeps its fields (six characterization tests reach them by
  reflected name), so the MVVM layer covers one of the slice's four files.

Toolchain: one new catalog line (androidx-lifecycle-runtime-ktx on the existing
lifecycle-runtime-compose ref) + two implementation lines on :ui:preferences
(lifecycle-runtime-ktx, lifecycle-viewmodel-ktx). Both provable no-ops on the resolved
version (2.9.4). No kotlinx-coroutines-test / Turbine / MockWebServer / mockito-core added.

Milestone 15's 41-test characterization suite stays byte-identical and green throughout.
Six new async characterization tests were written against the unmodified RxJava code first
(Step 3) and survived the conversion with zero assertion changes. Suite 41 -> 55.
```

### Test commands run

`:ui:preferences` applies `playFlavor.gradle`; both flavours run locally (CI runs only Play).

- `./gradlew --console=plain :ui:preferences:testFreeDebugUnitTest` — Step 1: PASS (41). Step 9: PASS (55).
- `./gradlew --console=plain :ui:preferences:testPlayDebugUnitTest` — Step 1: PASS (41). Step 9: PASS (55).
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL at the end of every step.
- `./gradlew assemblePlayDebug assemblePlayRelease assembleFreeRelease assemblePlayDebugAndroidTest` — BUILD SUCCESSFUL at the end of Steps 5, 6, 8, 9.
- `./gradlew checkstyle lint` — BUILD SUCCESSFUL at the end of Steps 5, 6, 8, 9.
- `./gradlew ktlintCheck` — BUILD SUCCESSFUL at the end of every step from 3.
- No instrumented test task (`connectedAndroidTest`) — this milestone adds no navigation/instrumented path.
- No Paparazzi verify task — this milestone adds no composable (AC19); Milestone 20 owns it.

### Per-step suite counts (AC3), re-derived from a real run each step

| After step | Free | Play | Note |
|---|---|---|---|
| 1 | 41 | 41 | baseline, re-measured — matches D1's table exactly |
| 2 | 41 | 41 | catalog + build.gradle only |
| 3 | 48 | 48 | +6 async characterization, +1 cancellation "before" record |
| 4 | 53 | 53 | +5 ViewModel tests |
| 5 | 53 | 53 | fragment consumes syncStatus; EventBus subscription removed |
| 6 | 55 | 55 | +2 ViewModel tests (ActionBar state) |
| 7 | 55 | 55 | Site A converted; cancellation before-record deleted, after-test added — flat |
| 8 | 55 | 55 | Site B converted |
| 9, 10 | 55 | 55 | verification sweep + docs |

Final per-class (both flavours identical), pre-existing 8 classes byte-for-byte unchanged from Step 1:
AuthenticationDialogCharacterizationTest 4, AuthenticationDialogJavaInteropTest 1,
GpodderAuthenticationFragmentCharacterizationTest 9, NextcloudAuthenticationFragmentCharacterizationTest 6,
SynchronizationPreferencesFragmentCharacterizationTest 8, SynchronizationPreferencesFragmentLifecycleTest 4,
SyncSettingsHarnessSmokeTest 6, SyncSettingsScreenshotCaptureTest 3 (= 41); new:
GpodderAuthenticationFragmentAsyncCharacterizationTest 6, GpodderAuthenticationFragmentCancellationTest 1,
SynchronizationPreferencesViewModelTest 7 (= 14). Total 55. 0 failures, 0 errors, 0 skipped.

### AC9 — the six Step-3 async characterization bodies are byte-identical through Step 8

Step-3 commit: `5392ca143`. `git diff 5392ca143 HEAD -- ...GpodderAuthenticationFragmentAsyncCharacterizationTest.kt`
touches only: imports, one field (`previousUncaughtHandler`), the `@Before`/`@After` bodies, and the
`showLoginStep`/`clickLogin` helper plumbing. `diff` of everything from the first `@Test` to EOF between
the Step-3 blob and HEAD: **empty** — the six `@Test` method bodies are byte-identical. No
`assert`-containing line changed. `GpodderAuthenticationFragmentCancellationTest.kt` is a File-Scope file
this milestone created and is deliberately not covered by AC9 (it is the deliberate-behavior-change
artifact).

**AC9 scope, stated consciously (code-review Finding 1).** AC9's literal text says "only import lines and
the `@Before`/`@After` bodies." The diff also touches two test *helper* methods: `showLoginStep` gains
`field("ioDispatcher").set(fragment, Dispatchers.Unconfined)` and `clickLogin` wraps `performClick()` +
`idle()` in a `try/catch` that stores `capturedCulprit`. These are exactly the D14-anticipated seam-swap
("the test's `@Before`/`@After` swap the Rx plugin handlers for `field("ioDispatcher").set(...)`") relocated
into helpers the six `@Test` methods call, not into the `@Test` bodies themselves. The substantive freeze
— the six `@Test` bodies and every `assert` line in them are byte-identical `5392ca143`→HEAD (verified by
the empty `diff` above) — holds. `AC10`'s `--diff-filter=M` on `src/test/` is independently empty.

### Characterization test results — per test, before/after, and what real behavior each exercises

Six frozen async tests (`GpodderAuthenticationFragmentAsyncCharacterizationTest`), all green at Step 3
(vs unmodified RxJava code), Step 7 and Step 8 (vs the coroutine code):

1. `testLoginSuccessCallsServiceInOrderAndAdvancesToDeviceStep` — Step 3 PASS / Step 7 PASS / Step 8 PASS.
   Exercises: `setCredentials` → `login` → `getDevices` really run in that order on the background hop,
   `devices`/`username`/`password` hold the returned values, the button re-enables, the progress bar hides,
   and the wizard advances to `currentStep == 2`. Fails on a regression that reorders the calls, drops one,
   or fails to advance.
2. `testLoginWritesDevicesBeforeCredentialFields` — 3 PASS / 7 PASS / 8 PASS. The fake's `getDevices()`
   asserts `fragment.username` is still `null` at call time, pinning the `devices = ...` then `username = ...`
   write order inside the background block. Fails if the conversion reorders those assignments.
3. `testLoginErrorWithCauseRendersCauseMessage` — 3 PASS / 7 PASS / 8 PASS. `login()` throws
   `GpodnetServiceException(IOException("boom"))`; asserts `credentialsError` shows `"boom"` (the cause's
   message), is VISIBLE, the button re-enables, the bar hides, and `currentStep` is unchanged. This is the
   Site A "error with a cause" path.
4. `testWrongPasswordErrorPathThrowsFromNullCause` — 3 PASS / 7 PASS / 8 PASS. `login()` throws
   `GpodnetServiceAuthenticationException("Wrong username or password")` (no cause); asserts the
   `NullPointerException` from `error.cause!!` escapes the handler. **Gap 16's first executable pin** — the
   wrong-password crash is real and preserved. The exception channel differs (RxJava `CompositeException`
   → bare NPE); the test captures it via the default uncaught-exception handler, and the body assertion
   (`capturedCulprit is NullPointerException`) is genuinely byte-identical across the change (red-team C7).
   **[post-#32, 2026-09-01] This test was inverted to `testWrongPasswordErrorRendersServerMessageWithNoCause`
   when OQ1 was resolved fix-here — it now asserts the rendered `"Wrong username or password"` message and
   the NPE/`capturedCulprit` harness was removed. See the "Gap 16 fix" subsection.**
5. `testCreateDeviceSuccessConfiguresLowercasedIdAndAdvances` — 3 PASS / 7 PASS / 8 PASS. Site B success:
   `configureDevice` receives the lowercased generated id and the entered caption, `selectedDevice` is set,
   the progress bar hides, wizard advances to `currentStep == 3`.
6. `testCreateDeviceErrorRendersErrorMessageNotCause` — 3 PASS / 7 PASS / 8 PASS. `configureDevice` throws
   `GpodnetServiceException("nope")`; asserts `deviceSelectError` shows `"nope"` (`error.message`, not its
   cause) and the field re-enables. **First executable pin of the deliberate two-handler asymmetry**
   (Site A reads `error.cause!!.message`, Site B reads `error.message`) that Milestone 15's `AC13` could
   only grep for.

Cancellation pair (`GpodderAuthenticationFragmentCancellationTest`, D15 / AC12 row a):
- `testInFlightLoginSurvivesDialogDismissal` — **green at Step 3 against production source that
  `git diff origin/develop -- ui/preferences/src/main/` proves byte-identical to `origin/develop`.** A
  non-auto-running `TestScheduler` holds the subscription's action; after `fragment.dismiss()` the discarded
  `Disposable` still runs the work to completion — `fake.calls == [setCredentials, login, getDevices]` and
  `devices` is non-null. Positive executed record of today's behavior. Deleted at Step 7.
- `testInFlightLoginIsCancelledWhenDialogIsDismissed` — green at Step 7 and Step 8 against the coroutine
  code. A file-private `ManualDispatcher` holds the coroutine suspended in `withContext`
  (`manual.queued() == 1`, `fake.calls` empty); `dismiss()` cancels `lifecycleScope`; `manual.runQueued()`
  resumes the continuation with `CancellationException` instead of executing the io block, so `fake.calls`
  stays empty, `devices` stays null, and `credentialsError.visibility == GONE` — the block never ran and the
  error branch did not render (the last assertion is the executable guard that the `catch` rethrows
  `CancellationException`; a `catch (Throwable)` that swallowed it would render the error branch and go red).
- Paired-file artifact: `git diff 5392ca143 30351e60d -- ...GpodderAuthenticationFragmentCancellationTest.kt`
  shows exactly `testInFlightLoginSurvivesDialogDismissal` deleted, `ManualDispatcher` + the opposite test
  added, with the scenario / fake / click / `dismiss()` / drain unchanged between them. Both halves are
  green runs on real code.

Seven ViewModel tests (`SynchronizationPreferencesViewModelTest`), green at every step from their
introduction:
- `testStickyEventPostedBeforeCollectionIsReplayedIntoSyncStatus` — a sticky posted before a collector
  subscribes is replayed into `syncStatus.value` when `register()` runs at collector-subscribe (D10c).
- `testSyncStatusStartsNullWhenNoStickyEventExists` — `null` initial value = "no sticky yet".
- `testConsecutiveEventsWithSameMessageResIdAreBothDelivered` — collects the stream into a list; two
  distinct `SyncServiceEvent(sync_status_started)` instances are **both** observed (`[null, e1, e2]`),
  pinning D6's non-`data class` finding — a `data class` conversion would make `StateFlow` conflation drop
  the second and this test would go red.
- `testCollectorCancellationUnregistersFromEventBus` (AC22) — `hasSubscriberForEvent(SyncServiceEvent)`
  returns to its **recorded pre-collection value** after `job.cancel()`, immune to any pre-existing global
  subscriber (red-team C6) while still failing on a leak.
- `testEventsPostedWhileNotCollectingAreSeenOnlyIfSticky` (AC22) — the stopped-then-restarted pair: a
  **non-sticky** post with no collector is missed by the next collector (`value == null`, which also proves
  `replayExpirationMillis = 0` resets to the initial value); a **`postSticky`** with no collector **is**
  replayed. This is the executable form of the C2 stopped-state divergence — non-sticky is the shape the
  frozen suite itself posts at `SynchronizationPreferencesFragmentLifecycleTest.kt:81, :97, :102`.
- `testDisconnectedProviderYieldsAbsentSubtitleAndConnectedYieldsLastSyncReport` — `onStarted()` yields
  `SyncSubtitle.Absent` when disconnected, `LastSyncReport(successful = true, ...)` when connected+synced.
- `testErrorAndSuccessEventsYieldLastSyncReportWhileOtherEventsYieldMessage` — `onSyncEvent()` routes
  `sync_status_error`/`sync_status_success` to `LastSyncReport` and every other `messageResId` to
  `Message(resId)` — the `:61-70` branch, now in the ViewModel.

The four frozen `SynchronizationPreferencesFragmentLifecycleTest` tests (`testStickyEventReplaysOnStart`,
`testSyncEventIgnoredWhenNotConnected`, `testSubtitleBranchesOnMessageResId`,
`testOnStartSetsTitleAndOnStopSetsEmptySubtitleWhereasDisconnectedSetsNull`) are green and **unmodified**
at the end of Steps 5 and 6. **Step-5 `stateIn` start-up-timing gate (D5): PASSED — the pre-authorised
`startObserving()`/`stopObserving()` fallback was NOT taken.** Both ordering-sensitive tests
(`testStickyEventReplaysOnStart`, `testOnStartSetsTitleAndOnStop…`) went green on the first run with the
`callbackFlow` + `stateIn(WhileSubscribed(0, 0))` shape.

### D10 — preserve-or-fix, decided per behavior (five rows)

| # | Behavior | Decision | Mechanism / evidence |
|---|---|---|---|
| a | The two discarded RxJava `Disposable`s — work runs to completion and writes to detached views after the host is gone | **FIXED** — deliberate behavior change, not equivalence | Structurally forced: `lifecycleScope` cancels at `DESTROYED`. Proved by the D15 paired-file artifact above (Step-3 green "before" record vs the Step-7 diff deleting it and adding its green opposite). The converted `catch` rethrows `CancellationException`; `credentialsError.visibility == GONE` in `testInFlightLoginIsCancelledWhenDialogIsDismissed` is the assertion that proves it. |
| b | The `devices` field data race — non-`@Volatile`, written on an io thread, read on main | **PRESERVED, exactly** | `devices = service!!.getDevices()` stays inside `withContext(ioDispatcher) { … }`, so the write still happens off-main and the main-thread resumption supplies the same incidental happens-before edge. `grep -c '@Volatile' GpodderAuthenticationFragment.kt` → **3**, `devices` still without it. `ui/preferences/README.md` convention 5 unchanged. |
| c | Sticky-replay semantics of `SyncServiceEvent` | **PRESERVED** | `MutableStateFlow<SyncServiceEvent?>(null)` initial value + `callbackFlow`/`stateIn` collection started at the exact former `register()` position (D7). `testStickyEventReplaysOnStart` green and unmodified. |
| d | `error.cause!!.message` (Gap 16) — reachable wrong-password crash | **FIXED under OQ1, 2026-09-01, post-#32** (was: preserved here, fixed in Milestone 18) | During the milestone: verbatim in the converted `catch`, `testWrongPasswordErrorPathThrowsFromNullCause` green at Steps 3/7/8, `CompositeException`→bare-NPE channel change disclosed. **After #32 merged, José resolved OQ1 fix-here:** Site A became `error.cause?.message ?: error.message` (Site B unchanged), the test was inverted to `testWrongPasswordErrorRendersServerMessageWithNoCause`, and the channel-change disclosure is moot. Post-fix `grep -F -c`: `error.cause!!.message` → **0**, `error.cause?.message` → **1**, `error.message` → **2** (AC14). See the "Gap 16 fix" subsection. |
| e | Per-post delivery of `SyncServiceEvent` — EventBus `ThreadMode.MAIN` dispatches every post; a `StateFlow` is conflated by contract | **PRESERVED IN PRACTICE, NOT IDENTICAL — recorded, not claimed equivalent** | Sequentially delivered posts (the main thread returning to idle between them — every case the frozen suite exercises and the overwhelming majority of real ones, since the 8 `SyncService.doWork()` posts are seconds apart) are each observed, pinned by `testConsecutiveEventsWithSameMessageResIdAreBothDelivered`. Posts arriving faster than the collector resumes — realistically only `SynchronizationQueueImpl.kt:136`'s `sync_status_started` immediately followed by `SyncService.kt:61`'s `sync_status_wait_for_downloads` — can coalesce under `StateFlow` conflation where EventBus dispatched both; no test pins that sub-resume window because none can without a scheduler this milestone deliberately did not add. **C9 addendum:** `callbackFlow` here uses its default `BUFFERED` (64) channel; a `trySend` on a full buffer would also drop — the same negligible real-world envelope (the nine producers post seconds apart; sticky replay is one event). No explicit `onBufferOverflow` policy was set; the default buffer is far larger than any realistic burst. Same class of bounded, argued, written-down difference as row (d). |

### AC6 — dependency diff, Step 1 vs Step 9 (no resolved-version movement)

`:ui:preferences:dependencies --configuration playDebugCompileClasspath` and
`--configuration playDebugUnitTestRuntimeClasspath` captured at Step 1 and Step 9. The only difference in
the compile classpath is the two **requested** entries the new declarations add:

```
> androidx.lifecycle:lifecycle-runtime-ktx:2.8.7 -> 2.9.4
> androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7 -> 2.9.4
```

Both resolve **upward to 2.9.4** — the version they already resolved to transitively at Step 1. No
artifact's resolved version moved. `dependencyInsight` for `androidx.lifecycle:lifecycle-viewmodel` and
`…:lifecycle-runtime` at Step 2 both showed `-> 2.9.4` with the catalog ref pinning 2.8.7 — provable
no-ops, as D8 predicted. `kotlinx-coroutines` stays at 1.9.0.

### AC7 — build surface moved by exactly one catalog line + two implementation lines

`git diff origin/develop -- gradle/libs.versions.toml`: one added `[libraries]` line,
`androidx-lifecycle-runtime-ktx` on `version.ref = "lifecycle-runtime-compose"`. No new `[versions]`,
no `[plugins]`, no `kotlinx-coroutines-test`, no Turbine, no MockWebServer, no `mockito-core`.
`git diff origin/develop -- ui/preferences/build.gradle`: two added `implementation` lines
(`libs.androidx.lifecycle.runtime.ktx`, `libs.androidx.lifecycle.viewmodel.ktx`), none removed.

### AC8 — determinism without a new dependency

`git grep -n "coroutines-test\|turbine\|mockwebserver\|mockito" -- 'ui/preferences/**'` → nothing
introduced by this milestone (the `mockito-core` catalog line and its use in `:model` / `:net:download:service`
/ `:playback:service` are all pre-existing and untouched). No test file added by this milestone references
`runTest`, `TestDispatcher`, `StandardTestDispatcher`, `UnconfinedTestDispatcher` or `Dispatchers.setMain`.
Determinism sources: Robolectric `shadowOf(Looper.getMainLooper()).idle()`; the reflected
`private var ioDispatcher` seam set to `Dispatchers.Unconfined`; the hand-written file-private
`ManualDispatcher` (a `CoroutineDispatcher` queueing `Runnable`s, no thread/executor/latch). The
`TestScheduler` fallback named in D15 was **not** needed — `io.reactivex.rxjava3.schedulers.TestScheduler`
resolved fine from the already-declared core `rxjava` artifact for the Step-3 "before" record.

### Per-file `!!` greps (AC13), `grep -o '!!' <file> | wc -l`

| File | Step 1 | Step 9 | Post-#32 (OQ1 fix) |
|---|---|---|---|
| `SynchronizationPreferencesFragment.kt` | 30 | **22** | 22 |
| `GpodderAuthenticationFragment.kt` | 12 | **12** | **11** |
| `NextcloudAuthenticationFragment.kt` | 7 | **7** | 7 |
| `AuthenticationDialog.kt` | 0 | **0** | 0 |
| **slice total** | **49** | **41** | **40** |

Post-#32 column: the OQ1 Gap 16 fix (2026-09-01, standalone follow-up PR) replaced `error.cause!!.message`
with `error.cause?.message ?: error.message` at Site A. See the "Gap 16 fix" subsection.

`grep -c 'supportActionBar' SynchronizationPreferencesFragment.kt` → **1** (the single `actionBar()`
access site, down from five statements). The 11 `findPreference<Preference>(…)!!`, the 3
`SynchronizationQueue.instance!!` (Milestone 18), and the Fragment-API preserved NPEs all stay.

### Scope discipline

`git diff --name-only origin/develop` → 9 files, all in File Scope (2 build, 2 modified production, 1 new
production, 4 new test). `--diff-filter=M` on `ui/preferences/src/test/` → empty (none of the eleven
pre-existing test files modified). `--diff-filter=A` → exactly the four created test files. `--diff-filter=D`
→ empty. `git diff origin/develop -- net/ event/ storage/ app/ app-wearos/ common.gradle build.gradle settings.gradle`
→ empty. `git diff origin/develop -- ui/preferences/src/main/java/.../screen/bugreport/` → empty.
`git grep "@HiltViewModel\|@AndroidEntryPoint\|@Inject\|@Module\|@InstallIn\|hilt" -- 'ui/preferences/**'`
→ zero (AC18). `git grep "@Composable\|ComposeView\|collectAsState" -- '.../synchronization/'` → zero (AC19).
`git grep "rxjava3" GpodderAuthenticationFragment.kt` → zero at end of Step 8 (AC17), while
`libs.rxandroid`/`libs.rxjava` stay declared for `BugReportViewModel` and `NextcloudAuthenticationFragment`.

### AC15 — no pixel change

`SyncSettingsScreenshotCaptureTest`'s three tests are green and the file unmodified at every step. The
capture host runs disconnected + no-sticky: the ActionBar subtitle was `null` before (`updateActionBar`'s
disconnected branch) and is `SyncSubtitle.Absent → null` after — same geometry,
`testFirstPreferenceRowIsNotClippedByActionBar` unaffected. No new before/after image captured; Milestone
20 owns the after-shot.

### Deviations from plan

**Two deviations, both recorded here; neither changes behavior or needed planner re-approval.**

**(1) kotlinx stack-trace recovery disabled for this module's test tasks.** `ui/preferences/build.gradle`
gains, after the `dependencies` block:

```
tasks.withType(Test).configureEach {
    systemProperty "kotlinx.coroutines.stacktrace.recovery", "false"
}
```

Reason: Gradle `Test` tasks run with assertions enabled (`-ea`), which turns **on** kotlinx-coroutines
stack-trace recovery. Recovery replaces a thrown exception with a reconstructed copy of the same class
whose `cause` is the original exception — so under a plain Step-7 run,
`testLoginErrorWithCauseRendersCauseMessage` saw `error.cause` one level deeper
(`"java.io.IOException: boom"` instead of `"boom"`) and `testWrongPasswordErrorPathThrowsFromNullCause`
saw a non-null `error.cause` and threw no NPE. Both are **test-environment artifacts**: on a real Android
device kotlinx detects Android and disables recovery by default, and any release build
(`desiredAssertionStatus()` false) disables it too. Disabling it here makes the coroutine characterization
tests characterize real-device / release behavior, which matches the RxJava path exactly — the six frozen
`@Test` bodies pass unchanged (AC9 intact). **This is a module-level test-task system property applied to
every test class in the module** — deterministic regardless of test-discovery order. (An earlier revision
set `System.setProperty(...)` in two of the three coroutine test classes' `@Before`; both reviewers noted
that was order-fragile — only 2 of 3 classes set it, and the frozen `SynchronizationPreferencesFragmentLifecycleTest`
now runs fragment coroutines too — so it was moved to the build file in the post-review cleanup pass.)
AC7 constrains `implementation`/catalog lines, not a test JVM property; `git diff origin/develop --
gradle/libs.versions.toml` is still exactly +1 catalog line and the module still adds exactly two
`implementation` lines.

**(2) `SyncSubtitle.Cleared` removed (dead code).** Plan D12 sketched a four-case `SyncSubtitle`
(`Absent`/`Cleared`/`Message`/`LastSyncReport`). Nothing ever produces `Cleared`: `onStop` sets
`actionBar().subtitle = ""` directly (to keep D7's "cancel job **then** clear" order literal), so the
`render()` branch for it was unreachable. Both reviewers flagged it. It and its `render()` branch are
removed; no test referenced it (grep-confirmed). `SyncSubtitle` is now `Absent`/`Message`/`LastSyncReport`.
The null-vs-empty distinction the plan cared about (`testOnStartSetsTitleAndOnStopSetsEmptySubtitleWhereasDisconnectedSetsNull`)
still holds: `Absent → null` via `render()`, `""` via `onStop`'s direct call.

**Not a deviation, but recorded per plan D5:** `SyncServiceEventSubscriber` is `internal`, where D5's
sketch wrote `private`. Confirmed necessary **by test run**, not inspection: EventBus 3.3.1 invokes the
`@Subscribe` method by reflection without calling `setAccessible(true)`, and a `private` nested class is
not accessible to `org.greenrobot.eventbus` even though the method itself is public — a `private` subscriber
class fails every ViewModel test with `IllegalAccessException: class org.greenrobot.eventbus.EventBus
cannot access a member of class …SyncServiceEventSubscriber`. `internal` compiles to a bytecode-public
class (no name mangling for classes), which EventBus can reflect into, while keeping the type out of the
module's Kotlin-visible API. The `@Subscribe` method stays public as EventBus requires.

No other deviation. `kotlinx-coroutines-test` was not catalogued (D9/D15). No new dependency. No
`repeatOnLifecycle`. No Hilt. No Compose. The Step-5 fallback was not taken.

### Acceptance Criteria — local verification status

| AC | Status | Note |
|---|---|---|
| AC1 | PASS (local) | Both flavours BUILD SUCCESSFUL, 0/0/0, per-class rows unchanged for the 8 pre-existing classes; 11 classes / 55 tests. |
| AC2 | PASS (local) | Four `…LifecycleTest` tests green + unmodified at Steps 5 and 6; Step-5 gate passed, fallback not taken. |
| AC3 | PASS (local) | Ledger 41→41→48→53→53→55→55→55; re-derived each step. |
| AC4 | PASS (local) | End of Step 3: `git diff origin/develop -- ui/preferences/src/main/` empty; both flavours green at 48. |
| AC5 | PASS (local) | Six async tests named above with the behavior each pins; green at Steps 3, 7, 8. |
| AC6 | PASS (local) | Dependency diff shows no resolved-version movement (both new entries `2.8.7 -> 2.9.4`). |
| AC7 | PASS (local) | Exactly one catalog line, exactly two `implementation` lines. The `tasks.withType(Test)` `systemProperty` block in `ui/preferences/build.gradle` is a test JVM property, not an `implementation`/catalog line — AC7 unaffected. |
| AC8 | PASS (local) | No `coroutines-test`/Turbine/MockWebServer/`mockito` introduced; no `runTest`/`TestDispatcher`/`setMain`. The only `stacktrace.recovery` reference is the build-file `systemProperty` (a JVM flag, not a dependency). |
| AC9 | PASS (local) | Six Step-3 `@Test` bodies byte-identical `5392ca143`→HEAD (empty `diff` from first `@Test` to EOF); the diff touches imports, one field, `@Before`/`@After`, and the `showLoginStep`/`clickLogin` helper seam-swap — the D14-anticipated relocation, consciously noted above. |
| AC10 | PASS (local) | 0 modified / 4 added / 0 deleted under `ui/preferences/src/test/`; `ManualDispatcher` file-private, no fifth file. |
| AC11 | PASS (local) | Only File-Scope files touched; `net/ event/ storage/ app/ app-wearos/` + build files + `bugreport/` empty. |
| AC12 | PASS (local) | Five-row D10 table above; `@Volatile` → 3; `testStickyEventReplaysOnStart` green + unmodified; conflation + buffer-drop recorded. **Row (d): Gap 16 was PRESERVED in the milestone (exception-type difference stated), then FIXED post-#32 under OQ1 — see the "Gap 16 fix" subsection.** |
| AC13 | PASS (local) | Milestone: 22 / 12 / 7 / 0 = 41; `supportActionBar` grep → 1. **Post-#32 OQ1 fix: `GpodderAuthenticationFragment.kt` 12→11, slice 41→40.** |
| AC14 | PASS (local) | Milestone: `error.cause!!.message` → 1, `error.message` → 1, `error.cause?.message` → 0. **Post-#32 OQ1 fix: `error.cause!!.message` → 0, `error.cause?.message` → 1, `error.message` → 2.** |
| AC15 | PASS (local) | `SyncSettingsScreenshotCaptureTest` green + unmodified; no pixel change on the disconnected/no-sticky path. |
| AC16 | PASS (local) | `assemblePlayDebug` + `assemblePlayRelease` + `assembleFreeRelease` + `assemblePlayDebugAndroidTest` + `checkstyle lint` + `ktlintCheck` + `:app:assembleDebug` BUILD SUCCESSFUL at the required steps. |
| AC17 | PASS (local) | `git grep rxjava3 GpodderAuthenticationFragment.kt` → 0 at end of Step 8; `libs.rxandroid`/`libs.rxjava` still declared. |
| AC18 | PASS (local) | `git grep` Hilt/KSP annotations in `ui/preferences/**` → 0; no Hilt/KSP line in `build.gradle`. |
| AC19 | PASS (local) | `git grep` `@Composable`/`ComposeView`/`collectAsState` in `.../synchronization/` → 0. |
| AC20 | PASS (local) | ViewModel event branch stays a subject-ful `if`/`else` on `event.messageResId`; `ktlintCheck` green every step. |
| AC21 | PASS (local) | `SynchronizationPreferencesFragment` still `public`, no-arg, extends `AnimatedPreferenceFragment`; `GpodderAuthenticationFragment` still `open`, `TAG` still `const val`; `SyncSettingsHarnessSmokeTest`'s 6 tests green + unmodified; `assemblePlayDebug` green. |
| AC22 | PASS (local) | `testCollectorCancellationUnregistersFromEventBus` + `testEventsPostedWhileNotCollectingAreSeenOnlyIfSticky` green at Steps 4–10. |

**Needs manual / device verification** (not locally checkable): the actual on-device behaviour of the
wrong-password crash path reaching the process uncaught-exception handler as a bare `NullPointerException`
(AC12 row d — the test captures it in-process); the real-device timing of `StateFlow` conflation vs
EventBus per-post delivery under a genuine fast producer burst (AC12 row e — argued, not device-measured).

### Cleanup pass (post-review) — 2026-08-31

Both review passes APPROVED. A cleanup pass before the PR (coordinator instruction; every item raised by
both the code reviewer and the red-team, all non-blocking) landed as the **9th commit** on the branch:

1. **`SyncSubtitle.Cleared` removed** — dead code (see Deviations §2). No test referenced it.
2. **Stack-trace-recovery disable moved to `ui/preferences/build.gradle`** as a module-level
   `tasks.withType(Test).configureEach { systemProperty "kotlinx.coroutines.stacktrace.recovery", "false" }`,
   deleting the two `System.setProperty(...)` lines from the test `@Before`s (see Deviations §1). Covers
   every test class in the module deterministically. `git diff origin/develop -- gradle/libs.versions.toml`
   still +1 line; AC7/AC8 re-checked and still hold.
3. **`SyncServiceEventSubscriber` visibility decided by test run** — `private` fails every ViewModel test
   with `IllegalAccessException` from EventBus reflection; `internal` is required and stays (see Deviations,
   "Not a deviation" note).
4. **AC9 helper-scope acknowledgement** added to the AC9 section above.
5. **Device check (red-team Finding 1)** — `./gradlew :app:installPlayDebug` on `emulator-5554`
   (API-36 emulator running), then navigated More → Settings → Synchronization via `adb`/`uiautomator`.
   Result: **PASS.** ActionBar title renders `"Synchronization"` (from `render()` → `actionBar().setTitle(state.titleRes)`);
   no subtitle (disconnected → `SyncSubtitle.Absent` → `null`); screen body intact (provider chooser row,
   disabled sync/force-full-sync rows); survives a rotation + navigate-away-and-back cycle
   (`onStop` → `onStart`, i.e. collector cancel/restart) with the screen re-rendering correctly;
   `adb logcat -d | grep de.danoeh.antennapod` shows **0 FATAL / AndroidRuntime / Caused-by lines**. The
   collector-driven ActionBar rendering works on-device.

Post-cleanup re-verification:
- `./gradlew :ui:preferences:testFreeDebugUnitTest` — BUILD SUCCESSFUL, 55, 0/0/0.
- `./gradlew :ui:preferences:testPlayDebugUnitTest` — BUILD SUCCESSFUL, 55, 0/0/0.
- `./gradlew :ui:preferences:ktlintCheck` — BUILD SUCCESSFUL.
- `./gradlew checkstyle lint` — BUILD SUCCESSFUL.
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
- `git diff origin/develop -- gradle/libs.versions.toml` — +1 catalog line (unchanged); `ui/preferences/build.gradle`
  — two `implementation` lines + the test `systemProperty` block.
- Scope unchanged: the same 9 code/build files (2 build, 3 production, 4 test) + 4 doc files. The cleanup
  pass adds no new file — it edits `ui/preferences/build.gradle` (+7 lines), `SynchronizationPreferencesViewModel.kt`,
  `SynchronizationPreferencesFragment.kt`, and the two coroutine test files' `@Before` (helper/imports
  only). `git diff --diff-filter=M origin/develop -- ui/preferences/src/test/` still **empty**; the four
  created test files are still the only additions there; the six frozen `@Test` bodies stay byte-identical.

### Gap 16 fix — OQ1 resolved (post-#32), 2026-09-01
_By: android-migration-developer | 2026-09-01_

PR #32 (this milestone) was merged; `origin/develop` at `cfe560cb0` contains all of Milestone 17. José
then resolved **OQ1 fix-here**. The fix ships as a **standalone bugfix PR**, not on the (now merged, dead)
milestone branch — branch `fix/gpodder-auth-causeless-exception-crash` off `origin/develop`.

- **Production (one line, Site A only).** `GpodderAuthenticationFragment.kt` login `catch (error: Throwable)`:
  `txtvError.text = error.cause!!.message` → `txtvError.text = error.cause?.message ?: error.message`.
  **Site B (`createDevice`, `txtvError.text = error.message`) is unchanged** — Milestone 15's D7
  two-handler asymmetry (A cause-first, B message-only) stays deliberate, now via `?.`+`?:` at A vs
  `.message` at B. The `catch (CancellationException) { throw error }` rethrow is untouched.
- **Test (inverted, not weakened).** `testWrongPasswordErrorPathThrowsFromNullCause` →
  `testWrongPasswordErrorRendersServerMessageWithNoCause`. `GpodnetServiceAuthenticationException("Wrong
  username or password")` has no cause, so the fixed expression yields the bare message. Asserts
  `credentialsError.text == "Wrong username or password"`, `credentialsError.visibility == VISIBLE`,
  `butLogin.isEnabled == true`, `progBarLogin.visibility == GONE`, `currentStep == 1`. No throwable/NPE
  assertion. `testLoginErrorWithCauseRendersCauseMessage` (the `IOException("boom")` case) is unchanged
  and green — `error.cause?.message` still yields `"boom"`. Suite stays **55** (one test changed, none
  added or removed); the async characterization class stays at 6.
- **Harness disposition: REMOVED (nothing else used it).** The `capturedCulprit` /
  `previousUncaughtHandler` fields, the `Thread.setDefaultUncaughtExceptionHandler` install/restore in
  `@Before`/`@After`, and the `try/catch` around `performClick()` in the `clickLogin` helper were C7's
  normalization machinery for the one NPE-observing test only. After the inversion no other test in the
  file references `capturedCulprit`, so all of it was removed. `@Before` now only does the four `init`
  calls + `RecordingSynchronizationQueue` + `setHosturl`; `@After` only nulls `SynchronizationQueue.instance`;
  `clickLogin` calls `performClick()` + `idle()` directly.
- **AC9 was satisfied at Step 8** (six frozen `@Test` bodies byte-identical `5392ca143`→milestone HEAD).
  This fix is deliberately **past the AC9 window** — the Step-3-commit → Step-8-state freeze is closed —
  and is the separately-reviewable post-equivalence behavior change José approved. It changes exactly one
  intentional behavior (the wrong-password render) and is proved by the inverted test's positive
  assertion on the rendered message, not by a green suite alone.
- **Recount.** `GpodderAuthenticationFragment.kt` `!!` **12 → 11**; slice total **41 → 40**
  (`SynchronizationPreferencesFragment.kt` 22 + this 11 + `NextcloudAuthenticationFragment.kt` 7 +
  `AuthenticationDialog.kt` 0). Three `grep -F -c` on `GpodderAuthenticationFragment.kt`:
  `error.cause!!.message` **0**, `error.cause?.message` **1**, `error.message` **2**.
- **Verification (fix branch).**
  - `./gradlew --console=plain :ui:preferences:testFreeDebugUnitTest :ui:preferences:testPlayDebugUnitTest`
    — BUILD SUCCESSFUL, **55 / 55**, 0 failures / 0 errors / 0 skipped on both flavours.
  - `./gradlew checkstyle lint` — BUILD SUCCESSFUL. `./gradlew ktlintCheck` — BUILD SUCCESSFUL.
    `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
  - Per-file `!!`: `SynchronizationPreferencesFragment.kt` 22, `GpodderAuthenticationFragment.kt` **11**,
    `NextcloudAuthenticationFragment.kt` 7, `AuthenticationDialog.kt` 0 = **40**.
- **Docs touched by this PR:** this task file (OQ1-resolved note, D10 row (d), AC12/AC13/AC14, this
  subsection), `ui/preferences/README.md` convention 4, the modernization future-work file (M18 no longer
  inherits Gap 16), and this milestone's checkpoint file. Milestone 15's task file is **not** edited —
  the OQ1-resolved note above flags that its AC13 four-count grep pin is now stale for Site A.

## Red-Team Verdict — Implementation
_By: legacy-android-red-team | 2026-08-31 | Loop 1 of max 2_

### Verdict
APPROVE

**The implementation proceeds to PR.** The milestone's central claim holds up under independent
re-verification: the 41-test Milestone 15 net is byte-identical and green (I re-ran both flavours twice
from `--rerun-tasks` — 55/55/0/0/0, the eight pre-existing classes row-for-row unchanged), the six async
characterization tests were written against source `git diff origin/develop -- ui/preferences/src/main/`
proves byte-identical and survived the conversion with their `@Test` bodies **not present in the
`5392ca143..HEAD` diff at all**, and the one intentional behavior change (Disposable cancellation) is
carried by a genuine paired-file artifact rather than prose. Scope is clean (9 code/build + 4 doc files,
all in File Scope; `net/`, `event/`, `screen/bugreport/` untouched; no Hilt/KSP/Compose). Every
preserve-or-fix call in D10 is accurate against the actual diff. All findings below are MINOR/NIT and
none undermines the equivalence proof; two are worth a code touch in PR polish.

### Concerns

**Finding 1 — ActionBar title/subtitle rendering moves from synchronous-in-`onStart()` to
collector-driven, and no test covers the resulting on-device timing window**
- **Severity:** MINOR
- **Class:** silent behavior change from mechanical translation (`concurrency` / lifecycle)
- **Concern:** On `develop`, `onStart()` writes the ActionBar title and subtitle synchronously before it
  returns, and `register()`'s sticky replay runs synchronously inside `onStart()` too (EventBus
  `ThreadMode.MAIN` invoked from the main thread = direct call). In the implementation, title/subtitle
  are rendered only by `viewLifecycleOwner.lifecycleScope.launch { launch { viewModel.uiState.collect
  { render(it) } } … }`, and the sticky-replay path additionally routes through the `callbackFlow`
  buffered channel → `stateIn` sharing coroutine → a second collector → `viewModel.onSyncEvent()` →
  `_uiState` → the `uiState` collector. Under Robolectric with `shadowOf(Looper.getMainLooper()).idle()`
  this all settles inside the same test step, so the four frozen `SynchronizationPreferencesFragmentLifecycleTest`
  tests pass — but each asserts only *after* an `idle()`, so a one-frame flicker on a real device (a
  stale title/subtitle briefly visible when returning to the screen mid-sync, or across a configuration
  change) is not caught by anything. The developer discloses exactly this path in Implementation Notes
  under "Needs manual / device verification."
- **Evidence:** `SynchronizationPreferencesFragment.kt` `onStart()` / `render()` (post-diff); `SynchronizationPreferencesFragmentLifecycleTest.kt:56,66-71,108-121` (assertions all follow `attach()`/`idle()`); Implementation Notes final paragraph.
- **Suggested mitigation:** Before treating the PR as done, run the app and check the config-change-during-sync path on a device (`AGENTS.md` install/run command), or state plainly in the PR description that this path is Robolectric-pinned only and Milestone 20's `collectAsStateWithLifecycle` work will re-settle it. No code change required for equivalence — the final rendered state matches `develop`; only the transient differs.

**Finding 2 — the `kotlinx.coroutines.stacktrace.recovery=false` deviation depends on an unstated
test-class-ordering invariant, and the deviation note misdescribes it**
- **Severity:** MINOR
- **Class:** test-suite integrity / determinism (`concurrency` track)
- **Concern:** The property is set in `@Before` of only **two** of the three new coroutine test classes
  — `SynchronizationPreferencesViewModelTest` does **not** set it, contrary to the Implementation Notes
  claim that "the three coroutine-using test classes … each sets it." `kotlinx.coroutines.internal.StackTraceRecovery`'s
  `RECOVER_STACK_TRACES` is read once, on first use, and cached for the JVM lifetime. It works today
  only because Gradle 8.13 discovers test classes in sorted order, so `GpodderAuthenticationFragmentAsyncCharacterizationTest`
  (which sets the property) runs before every other class that can trigger kotlinx stack-trace recovery
  — including the frozen `Synchronization*` fragment tests, which now exercise fragment-coroutine
  cancellation and do not set it, and `SynchronizationPreferencesViewModelTest`, which cancels
  collectors in every test and does not set it. If discovery order ever changes, or a coroutine-using
  test class sorts ahead of the setters, or an `AuthenticationDialog*` test gains an async path, the
  val caches `true` and `testLoginErrorWithCauseRendersCauseMessage` (sees `"java.io.IOException: boom"`
  instead of `"boom"`) and `testWrongPasswordErrorPathThrowsFromNullCause` (sees a non-null
  `error.cause`, no NPE, `capturedCulprit` stays null) both fail. The *reasoning* for disabling
  recovery is sound and equivalence-strengthening — it is `-ea`-gated, off on every real Android and
  release build and on the RxJava path, so leaving it on makes the **test** diverge from production
  rather than the production code diverging (code review Finding 3 assessed this correctly).
- **Evidence:** `GpodderAuthenticationFragmentAsyncCharacterizationTest.kt:42`, `GpodderAuthenticationFragmentCancellationTest.kt:47` (setters); `SynchronizationPreferencesViewModelTest.kt` `@Before` (no setter); Implementation Notes "Deviations from plan" ("the three coroutine-using test classes … each sets it"); observed run order (test-result timestamps: `AuthenticationDialogCharacterizationTest` 10:01:55 → `GpodderAuthenticationFragmentAsyncCharacterizationTest` 10:02:00 → `Synchronization*` 10:02:06+).
- **Suggested mitigation:** Set it once at the module test-task level — `tasks.withType(Test).configureEach { systemProperty 'kotlinx.coroutines.stacktrace.recovery', 'false' }` in `ui/preferences/build.gradle` — which removes the ordering dependency entirely. AC7 constrains added `implementation` *lines*, not a test-JVM system property, so the developer's stated reason for avoiding this ("could not be done via a Gradle test JVM arg without violating AC7") does not hold; if the planner disagrees, at minimum set the property in all three new classes' `@Before` and correct the deviation note.

**Finding 3 — Site B (create-device) `CancellationException` rethrow is verified by inspection only**
- **Severity:** MINOR
- **Class:** coverage gap (`concurrency` track)
- **Concern:** `GpodderAuthenticationFragmentCancellationTest` exercises Site A (login) only — plan Step
  8 says so explicitly. Site B's converted `catch (error: CancellationException) { throw error }` ahead
  of `catch (error: Throwable)` is a line-for-line mirror of Site A (confirmed in the diff), but nothing
  executably pins that a create-device coroutine cancelled by `dismiss()` does not render the error
  branch. Low risk given the symmetry; noted for completeness so it is a conscious acceptance.
- **Evidence:** `GpodderAuthenticationFragment.kt` Site B `catch` block (post-diff); `GpodderAuthenticationFragmentCancellationTest.kt` (single test, Site A).
- **Suggested mitigation:** None required. Optionally add a second test to the cancellation file mirroring the Site A scenario for `createDeviceButton`.

**Finding 4 — `SyncSubtitle.Cleared` is dead / unreachable code**
- **Severity:** MINOR (code hygiene, not equivalence)
- **Class:** quality — also raised as code review Finding 2
- **Concern:** No path produces `SyncSubtitle.Cleared`: `onStop()` sets `actionBar().subtitle = ""`
  directly (correctly, to keep D7's cancel-then-clear ordering), so the `render()` branch
  `SyncSubtitle.Cleared -> ""` is unreachable. `AGENTS.md` forbids dead code.
- **Evidence:** `SynchronizationPreferencesViewModel.kt` `sealed interface SyncSubtitle` (`data object Cleared`); `SynchronizationPreferencesFragment.kt` `render()` `when` branch.
- **Suggested mitigation:** Remove `data object Cleared` and its `render()` branch (the `when` stays exhaustive over the three remaining subtypes). Do **not** route `onStop` through state instead — that reorders cancel-vs-clear.

**Finding 5 — `SyncServiceEventSubscriber` is `internal`; plan D5 specified `private`**
- **Severity:** NIT
- **Class:** convention — also raised as code review Finding 4
- **Concern:** The nested subscriber has no consumer outside the enclosing ViewModel (AC22 tests assert
  via `EventBus.hasSubscriberForEvent`, not on the type). `internal` widens visibility past what is
  used and past the plan.
- **Evidence:** `SynchronizationPreferencesViewModel.kt:22`.
- **Suggested mitigation:** `private class SyncServiceEventSubscriber`.

### Categories considered and cleared

- **Characterization proves equivalence, not existence.** The six async tests assert real behavior —
  `setCredentials`→`login`→`getDevices` call order, the `devices`-before-`username` write order (via
  `beforeGetDevices`), rendered error text, `currentStep`, button/progress-bar visibility. Test 4
  genuinely reaches the Gap-16 `error.cause!!` NPE; test 6 genuinely distinguishes Site A
  (`error.cause!!.message`) from Site B (`error.message`) — the fake throws a cause-less
  `GpodnetServiceException("nope")`, so Site B rendering "nope" is only possible if it does not use
  Site A's handler. No assert-free invocations, no tautologies. Re-ran green.
- **The one deliberate behavior change is genuinely proven.** `ManualDispatcher` (default
  `isDispatchNeeded`, so `withContext` always enqueues) holds the coroutine at `queued() == 1` with
  `fake.calls` empty; `dismiss()` drives the `DialogFragment` to `DESTROYED` and cancels
  `lifecycleScope`; `runQueued()` resumes the `MODE_CANCELLABLE` continuation with `CancellationException`
  instead of executing the io block, so the fake records nothing and `credentialsError.visibility ==
  GONE` — the block never ran *and* the error branch did not render. The `credentialsError == GONE`
  assertion is a real guard that `catch (CancellationException) { throw error }` precedes
  `catch (Throwable)` (verified present at both sites). The Step-3 "before" record uses a non-auto
  `TestScheduler` and is a positive executed record against byte-identical production source.
- **`devices` race genuinely PRESERVED.** `devices = service!!.getDevices()` stays inside
  `withContext(ioDispatcher)`, no `@Volatile` added (`grep -c` → 3, `devices` without it), same
  structured-resume happens-before that `observeOn(mainThread())` provided for the success path; the
  concurrent-read race for `isDeviceInList`/`setupDeviceView` is neither closed nor widened. `ui/preferences/README.md`
  convention 5 unchanged.
- **Per-post delivery — the divergences are bounded and recorded.** `StateFlow` conflation (D10 row e)
  and `callbackFlow` `BUFFERED(64)` `trySend` overflow-drop (C9 addendum) are the only per-post
  divergences; checked against the real `SyncService.doWork()` sequence (8 posts on a WorkManager
  thread seconds apart) and `SynchronizationQueueImpl.kt:136` — the one realistic fast pair
  (`sync_status_started` immediately followed by `sync_status_wait_for_downloads`) is named. Envelope
  is honest. `SyncServiceEvent` confirmed still `class SyncServiceEvent(val messageResId: Int)` — plain
  class, so consecutive same-`resId` posts are distinct instances and both delivered
  (`testConsecutiveEventsWithSameMessageResIdAreBothDelivered` collects the stream into a list and
  proves it — re-ran green).
- **EventBus registration lifetime.** `callbackFlow { register … awaitClose { unregister } }.stateIn(viewModelScope,
  WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), null)` implemented as specified;
  a separate `SyncServiceEventSubscriber` carries `@Subscribe(ThreadMode.POSTING, sticky = true)`;
  collector `launch` at the former `register()` line in `onStart`, `syncStatusJob?.cancel()` at the
  former `unregister()` line in `onStop`, `actionBar().subtitle = ""` after the cancel. AC22's two
  tests (`testCollectorCancellationUnregistersFromEventBus` comparing against a *recorded*
  pre-collection value; `testEventsPostedWhileNotCollectingAreSeenOnlyIfSticky` — non-sticky missed,
  sticky replayed) re-ran green. The Step-5 `stateIn` start-up gate passed; fallback not taken.
- **Gap 16 / OQ1.** Wrong-password path still crashes — `testWrongPasswordErrorPathThrowsFromNullCause`
  re-ran green, `capturedCulprit is NullPointerException`. `error.cause!!.message` verbatim in the
  converted `catch` (`grep -F` → 1 / 1 / 0). The `CompositeException` → bare-NPE channel change is
  disclosed in D10 row (d) and accurate. Preserved, pinned, handed to Milestone 18 by name, escalated
  to José as non-blocking OQ1 — unchanged and still correct.
- **Public API / reflective construction.** `SynchronizationPreferencesFragment` still `public`,
  no-arg, extends `AnimatedPreferenceFragment`; `GpodderAuthenticationFragment` still `open`, `TAG`
  still `const val`. `SyncSettingsHarnessSmokeTest` (6) and `SyncSettingsScreenshotCaptureTest` (3)
  green and unmodified. `:app:assembleDebug` and `:ui:preferences:ktlintCheck` green here;
  `assemblePlayRelease` (R8) green per code review.
- **Dependency surface.** Catalog +1 line (`androidx-lifecycle-runtime-ktx` on the existing
  `lifecycle-runtime-compose` ref), build.gradle +2 `implementation` lines
  (`lifecycle-runtime-ktx` + already-catalogued `lifecycle-viewmodel-ktx`, now required because
  `stateIn(viewModelScope, …)` uses `viewModelScope`), nothing removed. Both provable no-ops on the
  resolved 2.9.4 per AC6. No `kotlinx-coroutines-test` / Turbine / MockWebServer / `mockito-core`.
- **Scope creep.** ViewModel deliberately narrowed to `SynchronizationPreferencesFragment` and to the
  ActionBar + sync event only; the Gpodder wizard keeps `username`/`password`/`selectedDevice`/`devices`/`currentStep`
  as fragment fields; `updateScreen()` stays in the fragment. Narrowing written into the future-work
  file's Milestone 20 row. No MVVM architecture smuggled in under "equivalence."
- **`!!` reduction.** `SynchronizationPreferencesFragment.kt` 30 → 22, slice 49 → 41, `supportActionBar`
  grep → 1. Exactly the D12 number; nothing removed that D12 did not authorise.
- **AC9 literal-vs-intent gap** (code review Finding 1): the six frozen `@Test` bodies are byte-identical
  (`5392ca143..HEAD` diff reaches only imports, `@Before`/`@After`, one field, and the
  `showLoginStep`/`clickLogin` helper seam-swap); `AC10`'s `--diff-filter=M` on `src/test/` is empty.
  Substantive freeze intact.

### Loop status
Loop 1 of max 2 — **APPROVE**. Auto-chain proceeds to PR (José authorised auto-chain to PR, not merge).
Findings 1–5 are MINOR/NIT and non-blocking; Finding 2 (ordering-fragile deviation) and Finding 4 (dead
`Cleared`) are the two worth a code touch in PR polish, and Finding 1 warrants a device check before the
PR is considered complete. No escalation to José required; OQ1 remains separately open for him as a
non-blocking positioning question.
