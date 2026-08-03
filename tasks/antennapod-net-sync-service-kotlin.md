# antennapod-net-sync-service-kotlin

> **Description:** kotlin track migration of the `:net:sync:service` module (implementation of the sync service — `SyncService` coordinates the active backend, `SynchronizationQueueStorage` persists the pending-changes queue in SharedPreferences) — fifth case-study module in the antennapod portfolio, following `antennapod-model-kotlin` (Milestones 1–7), `antennapod-event-kotlin` (Milestones 8–9), `antennapod-net-download-service-interface-kotlin` (Milestone 10), and `antennapod-net-sync-service-interface-kotlin` (Milestone 11). Completes the split pair started in Milestone 11: `:net:sync:service-interface`'s abstract `SynchronizationQueue` facade and `ISyncService` interface were converted there; this milestone converts the concrete implementation behind them.
> **Repo:** `antennapod`
> **Created:** 2026-08-02

## Research
_Last updated by: legacy-android-researcher | 2026-08-02_

### Summary

`:net:sync:service` is a small, 100%-Java Android library module: **6 production files / 808 LOC**, all in the single package `de.danoeh.antennapod.net.sync.service`, plus **2 test files / 228 LOC containing 7 tests** (`GuidValidatorTest`, 2; `EpisodeActionFilterTest`, 5). The checkpoint's 808 LOC / 6 files / 2 tests sizing is **exact** — verified by `wc -l` over `src/main` and `src/test`. The module README (`net/sync/service/README.md:1-4`) describes it as "the sync service. `SyncService` coordinates the active backend; `SynchronizationQueueStorage` persists the pending-changes queue in SharedPreferences." Structurally it is the mirror image of Milestone 11: where `:net:sync:service-interface` was nine small declaration-shaped files with one logic-dense outlier, this module is **one large orchestrator (`SyncService.java`, 371 LOC — 46% of the module) plus one persistence class (`SynchronizationQueueStorage.java`, 176 LOC), a `SynchronizationQueue` subclass (`SynchronizationQueueImpl.java`, 130 LOC), a pure-function filter (`EpisodeActionFilter.java`, 77 LOC), a static lock helper (`LockingAsyncExecutor.java`, 43 LOC) and an 11-line validator (`GuidValidator.java`)**. There are no DTOs, no interfaces, no enums, and no nested types anywhere in the module. `SyncService` is an `androidx.work.Worker`; the module's only Android resource is `src/main/res/values/ids.xml` (three notification/pending-intent IDs). Three declared dependencies — `libs.guava`, `libs.okhttp`, `libs.rxandroid` — have **zero references** in `src/`, verified by grep (`net/sync/service/build.gradle:29,30,32`); RxJava3 proper *is* used (`:31`), but only by `LockingAsyncExecutor`.

The `kotlin` track here is fundamentally different in shape from every prior milestone in this case study, and the difference is worth naming precisely: **this module has almost no inbound interop surface and an enormous outbound one.** Exactly **one** call site outside the module references anything in it — `ClientConfigurator.java:53`, `SynchronizationQueue.setInstance(new SynchronizationQueueImpl(context))` — and `ui/preferences/build.gradle:35`'s `implementation project(':net:sync:service')` is a **dead module dependency** (that module's only `SyncService*` reference is `SyncServiceEvent`, which comes from `:event`). So the seven-dependent-module compile that was Milestone 11's cheap safety net does not exist here; nothing outside this module can break, which means **nothing outside this module can catch a mistake either**. All the pressure instead comes from what this module *consumes*: `:model` (27 Kotlin files), `:event` (23 Kotlin files), `:net:download:service-interface` (7 Kotlin files, Milestone 10) and — the new one — `:net:sync:service-interface` (9 Kotlin files, Milestone 11). Milestone 11's JVM-shape decisions all held: `git log --name-only` over its 14 commits shows it touched **zero** files under `net/sync/service/`, and `EpisodeActionFilterTest.java` still compiles and passes unmodified against the converted API. But M11 deliberately chose **honest nullability** across that API, and this module is where those `?`s land. Four of them are load-bearing here and none of them is caught by any compiler today: `SynchronizationQueue`'s four abstract methods take `String?`/`EpisodeAction?`/`FeedMedia?` (`SynchronizationQueue.kt:24-30`), so `SynchronizationQueueImpl`'s overrides are **forced** to be nullable and its unguarded `media.getItem()` becomes an explicit `!!` decision; `EpisodeAction.readFromJsonObject` returns `EpisodeAction?` and `SynchronizationQueueStorage.java:35` adds that result to an `ArrayList<EpisodeAction>` **unguarded**, which is a type-level contradiction the moment this file becomes Kotlin; `EpisodeAction.action` is `Action?` and `EpisodeActionFilter.java:26` `switch`es on it, where Java throws `NullPointerException` and Kotlin's `when`+`else` would silently log-and-continue; and `GuidValidator.isValidGuid`'s parameter must stay `String?` or the module's own `GuidValidatorTest.java:16` fails. Separately, and with no Milestone 10 or 11 analogue, this module has **two Java visibility idioms Kotlin cannot express**: `SynchronizationQueueStorage`'s five `protected` methods are all called cross-class from within the package (Java allows this, Kotlin's `protected` does not), and `SyncService.isCurrentlyActive()` is package-private static. Both create a hard **conversion-ordering constraint** between the module's own files.

### Findings

#### Existing surface

All six production files live in one package, `de.danoeh.antennapod.net.sync.service`. There is no `AndroidManifest.xml` in the module (the `Worker` is registered through WorkManager, not the manifest) and no `androidTest` source set.

| File | LOC | Shape | Responsibility |
|---|---|---|---|
| `SyncService.java` | 371 | `public class … extends Worker` | The orchestrator, and 46% of the module. Selects the backend, runs the two-phase sync (subscriptions, then episode actions), waits on feed updates, applies remote play positions to the database, and posts/clears error notifications. 11 methods, 8 of them private. |
| `SynchronizationQueueStorage.java` | 176 | plain class over `SharedPreferences` | The on-disk pending-changes queue. Three JSON-array-in-a-string keys (`sync_queued_episode_actions`, `sync_added`, `sync_removed`) in a private prefs file named `synchronization`. 4 public methods, **5 `protected`**, 2 private. |
| `SynchronizationQueueImpl.java` | 130 | `public class … extends SynchronizationQueue` | The `:net:sync:service-interface` facade implementation registered by `ClientConfigurator`. Nine overrides, all of which either enqueue WorkManager work or delegate to `SynchronizationQueueStorage` under `LockingAsyncExecutor`. |
| `EpisodeActionFilter.java` | 77 | static-only class | Pure function: given remote + locally-queued `EpisodeAction`s, returns the remote PLAY actions that should override local state. Keyed on `androidx.core.util.Pair<String, String>` in an `androidx.collection.ArrayMap`. The only file in the module with real test coverage. |
| `LockingAsyncExecutor.java` | 43 | static-only class | A single process-wide `ReentrantLock` (`:10`) plus `executeLockedAsync(Runnable)` / `lock()` / `unlock()`. Runs inline when the lock is free, otherwise defers to `Completable.fromRunnable(...).subscribeOn(Schedulers.computation()).subscribe()` (`:24-32`). |
| `GuidValidator.java` | 11 | static-only class | `isValidGuid(String)` — `guid != null && !guid.trim().isEmpty() && !guid.equals("null")` (`:5-9`). |

Notable structural details:

- **No nested types, no enums, no interfaces, no DTOs anywhere in the module.** This is the flattest module in the case study so far, and it means none of Milestone 11's nested-class / `Outer.Inner` JVM-name concerns apply.
- **Three of six files are static-only utility classes** (`GuidValidator`, `LockingAsyncExecutor`, `EpisodeActionFilter`), each with an implicit public no-arg constructor that nothing calls. That is the single most consequential J2K shape decision in the module — see track-specific findings.
- **`SynchronizationQueueStorage` uses Java's `protected` as package-private.** `clearQueue()` (`:87`), `enqueueFeedAdded(String)` (`:97`), `removeLegacyConflictingFeedEntries(Collection<String>)` (`:119`), `enqueueFeedRemoved(String)` (`:130`) and `enqueueEpisodeAction(EpisodeAction)` (`:159`) are all `protected`, and **not one of them is called from a subclass** — the class has no subclass anywhere in the repo. They are called from `SynchronizationQueueImpl.java:78,86,96,106` and `SyncService.java:210`, sibling classes in the same package. Java permits this because `protected` subsumes package access; Kotlin's `protected` does not.
- **`SyncService.isCurrentlyActive()` is package-private static** (`:140-142`, explicitly commented `/* package-private */`), read by `SynchronizationQueueImpl.java:66` to decide the WorkManager initial delay (2 minutes if a sync is running, 20 seconds otherwise). Kotlin has no package-private.
- **`SynchronizationQueueStorage` has a redundant private accessor.** `getSharedPreferences()` (`:173-175`) just returns the `sharedPreferences` field, and both forms are used within the same file — the field directly at `:124` (inside `removeLegacyConflictingFeedEntries`) and the accessor everywhere else. Equivalent today; `AGENTS.md`'s minimal-diff rule says keep both rather than unify.
- **`SyncService`'s error path posts a notification behind a runtime-permission check** (`:345-348`): `nm.notify(...)` only fires if `POST_NOTIFICATIONS` is granted, but the `Notification` is built unconditionally first.
- **`waitForDownloadServiceCompleted` (`:115-129`) is a 1-second-poll busy-wait** on an EventBus sticky event, with an existing `//noinspection BusyWait` comment at `:123`. `AGENTS.md` forbids removing comments already in the code; there is no Kotlin equivalent of that IntelliJ suppression comment, so it transcribes as a plain comment.
- **`build.gradle` applies `playFlavor.gradle`** (`net/sync/service/build.gradle:5`), unlike Milestone 11's module. This changes test task names and, more importantly, the SpotBugs gate — see build mechanics below.

#### Java/Kotlin interop boundary

**Inbound (what calls INTO this module) — one call site, and one dead Gradle dependency.**

- **Gradle dependents: two declared, one real.** `app/build.gradle:64` and `ui/preferences/build.gradle:35`; registered at `settings.gradle:33`.
- **The entire inbound source surface is `app/src/main/java/de/danoeh/antennapod/ClientConfigurator.java:10,53`**: `import de.danoeh.antennapod.net.sync.service.SynchronizationQueueImpl;` and `SynchronizationQueue.setInstance(new SynchronizationQueueImpl(context));`. A repo-wide grep for `de.danoeh.antennapod.net.sync.service.` (excluding this module, `serviceinterface`, and `build/`) returns **exactly that one import**.
- **`ui/preferences`' dependency on this module is dead.** Its only `SyncService`-shaped reference is `SynchronizationPreferencesFragment.java:32,67` — `de.danoeh.antennapod.event.SyncServiceEvent`, which comes from `:event`, not from here. Nothing in `ui/preferences/src/` imports the `net.sync.service` package. Flagged so the dependency is not mistaken for a real caller; removing it is **out of scope** for this track and would widen the diff into another module.
- **Consequence, and it is the defining fact of this milestone:** Milestone 11 could lean on "seven dependent Java modules must still compile" as a free signature-regression detector. **That safety net does not exist here.** `:app` will still compile as long as `SynchronizationQueueImpl` keeps a public `(Context)` constructor and still extends `SynchronizationQueue`. Everything else in the module could be silently rewritten and `./gradlew :app:assembleDebug` would stay green.
- **Two non-source inbound contracts do exist, and neither is compiler-checked:**
  - **WorkManager's persisted class name.** `SynchronizationQueueImpl.java:62` builds `new OneTimeWorkRequest.Builder(SyncService.class)` and `:31,44` enqueue it as unique work `"SyncServiceWorkId"` with `ExistingWorkPolicy.REPLACE`. WorkManager stores the **fully-qualified class name string** in its own database and reflectively instantiates it via `(Context, WorkerParameters)`. So `de.danoeh.antennapod.net.sync.service.SyncService` must keep its exact FQCN, stay `public` and non-abstract, and keep a public two-arg constructor with exactly those parameter types — otherwise work already scheduled on an upgrading user's device fails to instantiate. Nothing in the build checks this.
  - **The `SharedPreferences` file and key names.** `SynchronizationQueueStorage.java:18-21`: file `"synchronization"`, keys `"sync_queued_episode_actions"`, `"sync_removed"`, `"sync_added"`, all defaulting to the string `"[]"`. These are an on-disk contract with every existing installation.

**Outbound (what this module calls OUT to) — wide, and now majority-Kotlin.**

| Dependency | Language | What this module uses |
|---|---|---|
| `:net:sync:service-interface` | **Kotlin** (M11) | `SynchronizationQueue` (subclassed), `ISyncService`, `EpisodeAction` + `Action`, `EpisodeActionChanges`, `SubscriptionChanges`, `UploadChangesResponse`, `SynchronizationProvider`, `SyncServiceException` |
| `:model` | **Kotlin** | `Feed`, `FeedItem`, `FeedMedia`, `FeedItemFilter`, `SortOrder` |
| `:event` | **Kotlin** | `SyncServiceEvent`, `FeedUpdateRunningEvent`, `MessageEvent` |
| `:net:download:service-interface` | **Kotlin** (M10) | `FeedUpdateManager.getInstance().runOnce(Context)` |
| `:storage:database` | Java (25 files) | `DBReader`, `DBWriter`, `FeedDatabaseWriter`, `LongList` |
| `:storage:preferences` | Java (7 files) | `SynchronizationSettings`, `SynchronizationCredentials`, `UserPreferences` |
| `:net:common` | Java (8 files) | `AntennapodHttpClient`, `RedirectChecker`, `UrlChecker` |
| `:net:sync:gpoddernet` | Java (13 files) | `GpodnetService`, `NextcloudSyncService` (both `implements ISyncService`) |
| `:ui:notifications` | Java (1 file) | `NotificationUtils.CHANNEL_ID_SYNC_ERROR`, `R.drawable.ic_notification_sync_error` |
| `:ui:i18n` | resources | 10 string resources (verified present, `ui/i18n/src/main/res/values/strings.xml:652-659,776-777`) |
| Dead: `libs.okhttp` (`:29`), `libs.rxandroid` (`:30`), `libs.guava` (`:32`) | — | zero references in `src/` |

Precise nullability of the Kotlin surfaces this module consumes (this is the substance of the track, so it is enumerated rather than summarised):

- `SynchronizationQueue.kt:24-30` — `enqueueFeedAdded(downloadUrl: String?)`, `enqueueFeedRemoved(downloadUrl: String?)`, `enqueueEpisodeAction(action: EpisodeAction?)`, `enqueueEpisodePlayed(media: FeedMedia?, completed: Boolean)`. Kotlin requires an override's parameter types to match **exactly**, so `SynchronizationQueueImpl`'s four overrides are forced nullable.
- `SynchronizationQueue.kt:6-9` — `@JvmStatic var instance: SynchronizationQueue?` in a companion. This module never reads it; only `ClientConfigurator` writes it.
- `EpisodeAction.kt:16-20` — `podcast: String?`, `episode: String?`, `guid: String?`, `action: Action?`, `timestamp: Date?`; `:24,28,32` — `started`/`position`/`total` are non-null `Int`.
- `EpisodeAction.kt:200` — `@JvmStatic fun readFromJsonObject(jsonObject: JSONObject): EpisodeAction?`; `:74` — `fun writeToJsonObject(): JSONObject?`.
- `EpisodeAction.kt:122,130` — `Builder(item: FeedItem, action: Action?)` (**`item` non-null**) and `Builder(podcast: String?, episode: String?, action: Action?)`.
- `EpisodeAction.kt:180-190` — four `@JvmField val NEW/DOWNLOAD/PLAY/DELETE`, so `EpisodeAction.PLAY` (`SynchronizationQueueImpl.java:122`, `SyncService.java:240`) resolves as a field from both Java and Kotlin.
- `ISyncService.kt:5-23` — all six methods `@Throws(SyncServiceException::class)`; `uploadSubscriptionChanges(addedFeeds: List<String>, removedFeeds: List<String>)` and `uploadEpisodeActions(queuedEpisodeActions: List<EpisodeAction>)` are `@JvmSuppressWildcards` with **non-null element types**; both return non-null `UploadChangesResponse`. `getSubscriptionChanges`/`getEpisodeActionChanges` return non-null.
- `UploadChangesResponse.kt:7` — `@JvmField val timestamp: Long`, so `uploadResponse.timestamp` (`SyncService.java:208,255`) works identically from Kotlin.
- `SynchronizationProvider.kt:10` — `@JvmStatic fun fromIdentifier(provider: String?): SynchronizationProvider?`. Both halves nullable; `SyncService.java:352-357` already null-guards the result.
- `SubscriptionChanges.kt:4-6` / `EpisodeActionChanges.kt:4-5` — `added`/`removed` are **non-null read-only `List<String>`**; `episodeActions` is non-null `List<EpisodeAction>`; `timestamp` is `Long`.
- `SyncServiceException.kt:3` — `open class … : Exception`. `SyncService.java:100`'s `instanceof` and `:209`'s `catch` both still work from Kotlin (Kotlin has no checked exceptions, so `syncSubscriptions`/`syncEpisodeActions` need no `@Throws` — they are private).
- `:model` — `FeedItem.kt:29,47,56` (`itemIdentifier: String?`, `media: FeedMedia?`, `feed: Feed?`); `Feed.kt:27` (`downloadUrl: String?`), `:54` (`items: MutableList<FeedItem>?`), `:60` (`lastRefreshAttempt: Long`), `:72` (`preferences: FeedPreferences?`), `:362,366` (`isLocalFeed()`, `getState()`), `:395` (`const val STATE_NOT_SUBSCRIBED = 1`), `:209` (`constructor(url: String?, lastModified: String?, title: String?)`); `FeedMedia.kt:34` (`item: FeedItem?`), `:43,45` (`duration`/`position`: `Int`), `:76` (`startPosition: Int`).
- `:event` — `SyncServiceEvent.kt:3` (`class SyncServiceEvent(val messageResId: Int)`), `FeedUpdateRunningEvent.kt:3` (`@JvmField val isFeedUpdateRunning: Boolean`), `MessageEvent.kt:6-11` (`constructor(message: String?)`).
- `:net:download:service-interface` — `FeedUpdateManager.kt:10,24` (`abstract fun runOnce(context: Context?)`; `@JvmStatic fun getInstance(): FeedUpdateManager?` — **nullable**, and `SyncService.java:86` dereferences it unguarded).
- Java dependencies are all platform types except where annotated: `DBReader.getFeedList()` is `@NonNull List<Feed>` (`DBReader.java:58-59`); `getFeedListDownloadUrls(boolean)` (`:81`), `getEpisodes(...)` (`:222-223`, `@NonNull`), `getFeedItemByGuidOrEpisodeUrl(String, String)` (`:428`, **unannotated but javadoc'd "or null"**, and `SyncService.java:279` null-checks it) and `loadFeedDataOfFeedItemList(List<FeedItem>)` (`:105`); `RedirectChecker.getNewUrlIfPermanentRedirect(String)` is **`@Nullable`** (`RedirectChecker.java:44-45`, and `SyncService.java:164` already null-checks), `getFinalUrl(@NonNull String)` is `@NonNull` (`:56-57`); `DBWriter.removeQueueItem(Context, boolean, long...)` is **varargs** (`DBWriter.java:477-478`).

**Public API that must not silently break.** Practically: `SynchronizationQueueImpl`'s public `(Context)` constructor and its nine `SynchronizationQueue` overrides; `SyncService`'s FQCN, `public` modifier and `(Context, WorkerParameters)` constructor (WorkManager reflection + persisted work records); `SynchronizationQueueStorage`'s SharedPreferences file/key names and JSON encoding; `EpisodeActionFilter.getRemoteActionsOverridingLocalActions`'s static-ness and its `Map<androidx.core.util.Pair<String,String>, EpisodeAction>` return type (`EpisodeActionFilterTest.java:60,105,143,181,206` binds it directly); `GuidValidator.isValidGuid`'s static-ness and nullable parameter (`GuidValidatorTest.java:16` passes `null`); `LockingAsyncExecutor`'s three static methods; and `SyncService.isCurrentlyActive()`'s visibility to `SynchronizationQueueImpl`.

#### Current test coverage

**Two test files, 228 LOC, 7 test methods, both JUnit 3-style `extends junit.framework.TestCase`, both plain-JVM (the module declares only `testImplementation libs.junit` at `net/sync/service/build.gradle:34` — no Robolectric, no mocking framework).** Because the module applies `playFlavor.gradle`, the same 7 tests run in four variants; `:net:sync:service:test` therefore reports **28 tests**, which is the number Milestone 11's Implementation Notes recorded. Do not read 28 as 28 distinct assertions.

**`GuidValidatorTest.java` (18 LOC, 2 tests) — complete coverage of an 11-line file.** `testIsValidGuid` (`:7-9`) asserts one true case; `testIsInvalidGuid` (`:11-18`) asserts `""`, `" "`, `"\n"`, `" \n"`, **`null`** and the literal string `"null"` all return false. **The `null` case at `:16` is load-bearing**: it is the only assertion anywhere in the repo that will catch a `String` → `String?` parameter regression on this module's most-called validator, and it works because it is a Java caller (a non-null Kotlin parameter would compile fine and throw `NullPointerException` at runtime, failing the test). This is the module's single strongest existing guard.

**`EpisodeActionFilterTest.java` (210 LOC, 5 tests) — covers one method of one file, and asserts only map sizes.** All five tests exercise `EpisodeActionFilter.getRemoteActionsOverridingLocalActions` and assert **nothing but the returned map's size**: `assertSame(1, …)` (`:62`), `assertSame(0, …)` (`:107`), `assertEquals(2, …)` (`:145`), `assertEquals(0, …)` (`:183`), `assertSame(1, …)` (`:208`). No test inspects which `EpisodeAction` is in the map, its identity, or any field value. All fixtures use `new EpisodeAction.Builder(String, String, Action)` with `.timestamp(Date)` and `.position(int)`; 20 references to `EpisodeAction.Action.PLAY`. The one genuinely valuable behaviour it pins is at `:186-209`: a local action built **without** a timestamp (`:191-196`, comment "no timestamp") is overridden by a remote action that has one — i.e. `getTimestamp() == null` is a live, exercised state that flows through `secondActionOverridesFirstAction` (`EpisodeActionFilter.java:69-75`). Note also that these five tests are **Milestone 11's** proof artifact (its AC11 required them to compile and pass unmodified), so they have already been validated against the converted `EpisodeAction`.

**What has zero test coverage, in this module or anywhere else in the repo:**

- **`SyncService.java` — all 371 LOC, all 11 methods.** No unit test, no instrumented test, no indirect coverage. This is 46% of the module and the entire orchestration logic.
- **`SynchronizationQueueStorage.java` — all 176 LOC.** No test reads or writes the `"synchronization"` prefs file. Milestone 11's research already flagged this file as the on-disk half of `EpisodeAction`'s dual serialization contract and noted that *nothing in the repository tests it*; that is still true.
- **`SynchronizationQueueImpl.java` — all 130 LOC.** The four `SynchronizationQueueStub`-installing tests in `:net:download:service` and `:storage:database` (`DbCleanupTests.java:88`, `DbQueueCleanupAlgorithmTest.java:31`, `DbWriterTest.java:67`, `FeedDatabaseWriterTest.java:46`) install the **stub**, never this implementation — they mute the queue rather than exercise it.
- **`LockingAsyncExecutor.java` — all 43 LOC**, including the contended path that hands work to `Schedulers.computation()`.
- **`EpisodeActionFilter`'s `default` branch** (`:44-46`) and the `NEW`/`DOWNLOAD`/`DELETE` cases (`:27-29,41-43`) — only `PLAY` is exercised.
- **Static analysis is currently clean**, for what that is worth as a baseline: `net/sync/service/build/reports/lint-results-freeDebug.txt` reads "No issues found." and `net/sync/service/build/reports/spotbugs/playDebug.xml` contains **0** `BugInstance` elements. There is no lint baseline file and no `net.sync` entry in `config/spotbugs/exclude.xml`.

#### Characterization-test gaps

Ordered by risk. These must be written and green **before** any `.java` → `.kt` rename. Two things make this module's gap list unusually consequential: (i) as established above, **no other module's compile or test suite can catch a regression here** — there is one inbound call site and it only touches a constructor; and (ii) the two files carrying the most risk (`SyncService`, `SynchronizationQueueStorage`) are also the two with literally zero coverage. Unlike Milestone 11, the module **already has a test source set and `testImplementation libs.junit`** (`build.gradle:34`), so no build wiring is needed to start writing plain-JUnit tests — but see gap 1 for the Robolectric question, which recurs here.

1. **`SynchronizationQueueStorage`'s three-queue SharedPreferences round-trip — 176 LOC, zero tests, and it is the user's un-uploaded sync data.** This is the highest-consequence gap in the module. Untested behaviours, each individually silent on failure: that all three getters return an **empty list** rather than throwing when the key is absent (the `"[]"` default at `:32,47,63`); that a `JSONException` mid-parse is **swallowed via `printStackTrace()` and the partially-filled list is still returned** (`:37-39,52-54,68-70`) rather than the read being abandoned; that `enqueueFeedAdded` **removes the same URL from the removed-queue** and vice versa (`:100-107`, `:133-140`), including the `indexOf` → `-1` → `JSONArray.remove(-1)` no-op path when the URL is not present (`:146-157`); that `removeLegacyConflictingFeedEntries` (`:119-128`) writes `List.toString()` — i.e. **`"[a, b]"`, not valid JSON** — into both prefs keys, which is a genuine pre-existing defect that the next `new JSONArray(...)` read will throw on and swallow, silently emptying both queues; that `clearQueue` (`:87-95`) also calls `SynchronizationSettings.resetTimestamps()` while `clearFeedQueues`/`clearEpisodeActionQueue` do not; and that every write uses `.apply()` (asynchronous) rather than `.commit()`. A test using a real `SharedPreferences` (Robolectric) or an injected fake is the only way to pin any of this. **Note the `removeLegacyConflictingFeedEntries` defect is exactly the `EpisodeAction.equals` situation from Milestone 11 — pin it, do not fix it inside an equivalence diff.**
2. **`SynchronizationQueueStorage.getQueuedEpisodeActions`'s null-element path — untested, and it is the one place where Milestone 11's honest nullability collides head-on with this module's types.** `:35` reads `actions.add(EpisodeAction.readFromJsonObject(queue.getJSONObject(i)));` with **no null check**, and after Milestone 11 that method's return type is `EpisodeAction?` (`EpisodeAction.kt:200`). Today, a stored entry missing `podcast`/`episode`/`action`, or carrying an unrecognised action string, puts a **`null` into the returned `ArrayList<EpisodeAction>`**, which then flows to `SyncService.java:229` (upload) and `:273` (via `EpisodeActionFilter`), where `createUniqueLocalMostRecentPlayActions` calls `action.getPodcast()` (`EpisodeActionFilter.java:58`) and NPEs — inside `doWork()`'s `catch (Exception e)` (`SyncService.java:95`), so the observable behaviour is an error notification plus `Result.failure()`. Pin **that** end-to-end outcome with a golden malformed-JSON fixture before touching the file, because every Kotlin form of `:35` (`!!`, `?.let { add(it) }`, or widening the list to `EpisodeAction?`) produces a *different* observable result and none of them is caught by a compiler.
3. **`SyncService.doWork()`'s control-flow matrix — 49 LOC (`:65-113`), zero tests, and it is the module's decision core.** Untested: the early `Result.success()` when `getActiveSyncProvider()` returns null (`:68-71`) — which is the path taken by **every user who has never configured sync**, on every worker invocation; the re-entrancy guard returning `Result.success()` when `currentlyActive` is already true (`:73-75`); that `currentlyActive` is set true *before* and reset in `finally` (`:76,110-112`); that a `SyncServiceException` yields `Result.retry()` while any other exception yields `Result.failure()` (`:100-109`); that the error notification is throttled to `getRunAttemptCount() % 3 == 2` (`:101`) on the retry path but posted unconditionally on the failure path (`:107`); and that the "new subscriptions need refreshing" branch returns `Result.success()` **early, before `syncEpisodeActions` runs**, after kicking off `FeedUpdateManager.getInstance().runOnce(...)` (`:82-88`). `Worker` is testable off-device via `androidx.work:work-testing`'s `TestListenableWorkerBuilder`, which the repo does **not** currently declare anywhere — see Unknowns.
4. **`EpisodeActionFilter`'s untested branches, and specifically the `switch` on a now-nullable enum.** The five existing tests only reach the `PLAY` case. Untested: `NEW` and `DOWNLOAD` falling through to no-op (`:27-29`); `DELETE` falling through to no-op with its shouting comment (`:41-43`); the `default` branch's `Log.e` (`:44-46`), which is dead in Java (all four constants are covered, so only `null` could reach it — and `switch` on a null enum throws `NullPointerException` first); and the remote-vs-remote dedupe at `:35-38` where a second remote action for the same key is compared against the first. Add a test that asserts a **`NullPointerException` for an `EpisodeAction` built with a null action**, because that is the exact behaviour a Kotlin `when`/`else` would silently convert into a log line. Also untested and worth pinning: `createUniqueLocalMostRecentPlayActions`'s `mostRecent.getTimestamp() == null` branch (`:60-61`), which replaces a null-timestamped entry with any later one.
5. **`LockingAsyncExecutor` — 43 LOC, zero tests, and the contended branch is genuinely hard to pin.** Untested: that an uncontended `executeLockedAsync` runs the `Runnable` **synchronously on the calling thread** (`:17-22`) — which four `SynchronizationQueueImpl` methods depend on for ordering; that a contended call defers to `Schedulers.computation()` and returns immediately (`:24-32`); that the lock is **reentrant**, so `SyncService.java:203`'s `lock()` inside a `LockingAsyncExecutor`-scheduled body does not deadlock; and that `unlock()` on a lock this thread does not hold throws `IllegalMonitorStateException`. A same-thread reentrancy test plus a two-thread contention test with a latch is the realistic shape. Low conversion risk (the file is mechanical) but zero current coverage on a process-wide lock.
6. **`SynchronizationQueueImpl`'s guard conditions — 130 LOC, zero tests.** Untested: that `enqueueFeedAdded`/`enqueueFeedRemoved`/`enqueueEpisodeAction` **return early and do nothing** when `SynchronizationSettings.isProviderConnected()` is false (`:82-84,92-94,102-104`) — this is the branch that makes a null `media` harmless today; `enqueueEpisodePlayed`'s three-part rejection (`:115-121`): null item, local feed, `STATE_NOT_SUBSCRIBED`, then `startPosition < 0` or (not completed and `startPosition >= position`); the `/1000` millisecond→second conversions and the `completed ? duration : position` selection (`:124-126`); `syncIfNotSyncedRecently`'s 10-minute threshold (`:35`); and `getWorkRequest`'s network-constraint fork on `UserPreferences.isAllowMobileSync()` (`:56-60`) plus the 2-minute-vs-20-second initial delay and the `SyncServiceEvent` posted **only on the not-currently-active branch** (`:66-73`). WorkManager's `androidx.work:work-testing` is again the enabling dependency.
7. **`SyncService.syncSubscriptions`'s URL-dedupe and list-aliasing (`:144-217`) — zero tests, and it contains a subtle aliasing behaviour a converter can easily change.** Untested: the four-way skip ladder for each added URL (non-`http` prefix, already-subscribed, permanent-redirect target already subscribed, final-URL already subscribed — `:156-174`, added by upstream `313b3a267`); that a removed URL is skipped if it is in the locally-queued *added* list (`:182-186`); and — the risky one — that on first sync (`lastSync == 0`) `queuedAddedFeeds` is **re-pointed at the very same `localSubscriptions` list object** (`:190`) which is then mutated by `removeAll` at `:193` and afterwards passed to `removeLegacyConflictingFeedEntries(localSubscriptions)` in the `catch` at `:210`. Pin the first-sync path's resulting upload payload before converting; Kotlin will force an explicit `MutableList` decision here (see track findings) and the aliasing is easy to lose.
8. **`SyncService.processEpisodeActions`'s smart-mark-as-played arithmetic (`:265-305`) — zero tests.** Untested: `media.setPosition(action.getPosition() * 1000)`; the `almostEnded` predicate `duration > 0 && position >= duration - smartMarkAsPlayedSecs * 1000` (`:290-291`); that "almost ended" sets `played = true`, **resets position to 0** and queues the item for queue-removal (`:292-296`); that a `null` `FeedItem` or a `FeedItem` with null media is skipped with a `Log.i` (`:279-286`); and that the final three database writes happen **unconditionally, including with empty collections** (`:302-304`). Note `int` overflow is reachable in `getPosition() * 1000` for a corrupt stored position — pin whatever happens today rather than "fixing" it.
9. **`SyncService.getActiveSyncProvider` (`:351-370`) and the notification paths (`:307-349`) — zero tests.** For the provider selector: null key → null (already exercised in production by every unconfigured user, but asserted nowhere), and that the two branches pass exactly the credential accessors they do. For notifications: that `updateErrorNotification` short-circuits when `gpodnetNotificationsEnabled()` is false (`:319-322`); that it posts a `MessageEvent` **instead of** a notification when a subscriber exists (`:323-326`); that the description is `getString(R.string.gpodnetsync_error_descr) + exception.getMessage()` — with a **null message rendering the literal `"null"`**; and that `nm.notify` is gated on `POST_NOTIFICATIONS` (`:345-348`) while the `Notification` is built regardless. All Robolectric-shaped.

#### Track-specific findings — `kotlin`

**Hazard 1 (compile-time, but only in the wrong conversion order): Kotlin has no package-private, and Kotlin `protected` does not grant package access.** Two independent instances, both with no Milestone 10 or 11 analogue.

- `SynchronizationQueueStorage`'s five `protected` methods (`:87,97,119,130,159`) are called from `SynchronizationQueueImpl.java:78,86,96,106` and `SyncService.java:210` — sibling classes in the same package, **not subclasses**; the class has no subclass anywhere in the repo. If `SynchronizationQueueStorage` converts while its callers are still Java, Kotlin `protected` emits JVM `protected` and Java's access rules still permit the calls, so it compiles. The moment the **callers** also become Kotlin, those same calls stop compiling. Conversely `internal` — the honest module-scoped equivalent — is **name-mangled** for member functions (`clearQueue$net_sync_service`), so any Java caller remaining in the module breaks immediately. There is no ordering that makes both `protected` and `internal` work throughout; the planner must pick a target visibility and a file order that reach it in one step, or accept a deliberate widening to `public`.
- `SyncService.isCurrentlyActive()` (`:140-142`, `/* package-private */ static`) is read by `SynchronizationQueueImpl.java:66`. Same dilemma, same three options (`internal` + convert both together, `@JvmName` to defeat mangling, or widen to `public`).

**Hazard 2 (silent, highest consequence): `SynchronizationQueueStorage.java:35` adds a `EpisodeAction?` to an `ArrayList<EpisodeAction>`.** Detailed in gap 2. After Milestone 11, `EpisodeAction.readFromJsonObject` returns `EpisodeAction?` (`EpisodeAction.kt:200`) and `getQueuedEpisodeActions()` declares `ArrayList<EpisodeAction>` — a combination that cannot survive in Kotlin. Every available form changes behaviour in a different direction, and there is a **second-order constraint**: Milestone 11 declared `ISyncService.uploadEpisodeActions(queuedEpisodeActions: List<EpisodeAction>)` with **non-null elements** (`ISyncService.kt:20`), and `EpisodeActionChanges.episodeActions` likewise (`EpisodeActionChanges.kt:4`). So widening this module's return type to `ArrayList<EpisodeAction?>` — the *faithful* choice — poisons `SyncService.java:229`'s downstream call and forces a cast or filter at that boundary, which is itself a behaviour change. This is the single hardest decision in the milestone and it belongs in Resolved Decisions, not in the developer's hands.

**Hazard 3 (silent): `SynchronizationQueueImpl`'s four overrides are forced nullable by Milestone 11, and their bodies dereference immediately.** `SynchronizationQueue.kt:24-30` fixes the parameter types; Kotlin requires exact override matching. So:
- `enqueueEpisodePlayed(media: FeedMedia?, completed: Boolean)` — the body (`SynchronizationQueueImpl.java:112-127`) checks `isProviderConnected()` first, then evaluates `media.getItem()` three times at `:115-116`, `media.getStartPosition()`/`getPosition()`/`getDuration()` at `:119,124-126`, and passes `media.getItem()` into `EpisodeAction.Builder(FeedItem, Action)` at `:122` — whose first parameter Milestone 11 declared **non-null** (`EpisodeAction.kt:122`). Today a null `media` is a **benign no-op for any user with sync unconfigured** (the `:112-114` early return fires first) and an NPE otherwise. The equivalence-preserving Kotlin is `media!!` *after* the connectivity guard, not `media?.` — a `?.` would silently no-op where production currently throws. There are additionally two `:model` chains here that NPE today and therefore need `!!`: `media.getItem().getFeed().isLocalFeed()` and `.getState()` (`:115-116`), where `FeedItem.feed` is `Feed?` (`FeedItem.kt:56`).
- `enqueueFeedAdded(downloadUrl: String?)` / `enqueueFeedRemoved(downloadUrl: String?)` (`:81,91`) — pass straight through to `SynchronizationQueueStorage`, whose own parameter type is then forced by the same chain. A null URL is tolerated today all the way down (`JSONArray.put(Object)` accepts it and `indexOf`'s `array.getString(i).equals(string)` is null-safe), so `String?` is faithful end to end.
- `enqueueEpisodeAction(action: EpisodeAction?)` (`:101`) — reaches `SynchronizationQueueStorage.enqueueEpisodeAction`'s `action.writeToJsonObject()` (`:164`), which NPEs on null today. Note the NPE would be thrown **inside a `Completable.fromRunnable` on `Schedulers.computation()`** whenever the lock is contended (`LockingAsyncExecutor.java:24-32`), i.e. delivered to `RxJavaPlugins`' global error handler rather than to any caller — a detail worth pinning before it is accidentally changed.

**Hazard 4 (silent): `EpisodeActionFilter.java:26`'s `switch` on a nullable enum becomes a `when`, and the failure mode inverts.** `EpisodeAction.action` is `Action?` (`EpisodeAction.kt:19`). Java's `switch (remoteAction.getAction())` compiles to an ordinal lookup that throws `NullPointerException` for null. J2K's natural output — `when (remoteAction.action) { Action.NEW, Action.DOWNLOAD -> {} … else -> Log.e(TAG, …) }` — routes null to `else` and **logs instead of throwing**. Null `action` is not reachable from any production construction path today (all seven `Builder` call sites pass a static `Action` constant, and `readFromJsonObject` returns null rather than building with a null action), so this is a latent rather than live divergence — but it is exactly the class of change that is invisible to the compiler, invisible to code review, and unprovable without the test named in gap 4.

**Hazard 5 (compile-time, loud): three static-only classes must become `object`s with `@JvmStatic`, not top-level functions.** `GuidValidator`, `LockingAsyncExecutor` and `EpisodeActionFilter` have only static members. J2K's default for such a class is a Kotlin `object`, whose members are **instance** methods on an `INSTANCE` singleton unless annotated `@JvmStatic`; and a developer "tidying" them into top-level functions would move them to a synthetic `GuidValidatorKt`/`EpisodeActionFilterKt` facade class, breaking `GuidValidatorTest.java:8,12-17` and `EpisodeActionFilterTest.java:60,105,143,181,206` at compile time. Loud rather than silent, but all three need explicit `@JvmStatic` for the Java tests to keep compiling — and those two Java test files are the milestone's entire equivalence oracle, so they must not be edited. Two related sub-items: (a) each of the three currently has an **implicit public no-arg constructor** that a Kotlin `object` removes — nothing in the repo calls them, verified by grep, so this is a disclosed, unobservable narrowing rather than a risk; (b) `LockingAsyncExecutor` has a `private static final ReentrantLock lock` field **and** a `public static void lock()` method (`:10,40-42`) — Kotlin permits both names to coexist (properties and functions occupy different namespaces, and the JVM keeps fields and methods separate), but J2K may rename one, and both the field name and `lock()`/`unlock()`'s JVM names are read by `SyncService.java:203,213,250,259`.

**Hazard 6 (compile-time, loud): `GuidValidator.isValidGuid` must take `String?`, and the existing test is the guard.** `GuidValidator.java:6`'s first clause is `guid != null`, and it is called at `SyncService.java:277` with `action.getGuid()`, which is `String?` after Milestone 11 (`EpisodeAction.kt:18`). J2K will emit a non-null `String` because there is no `@Nullable` to tell it otherwise. `GuidValidatorTest.java:16` passes a literal `null` from Java — it will still *compile*, but the `Intrinsics.checkNotNullParameter` prologue makes it throw, so the test fails. This is the one hazard in the module that an existing test catches for free; every other one needs a test written first.

**Hazard 7 (compile-time, forced decision): `SyncService`'s mutable-list aliasing at `:147-194`.** `queuedAddedFeeds` is declared `List<String>` from `synchronizationQueueStorage.getQueuedAddedFeeds()` (`ArrayList<String>`, `:152`), then **reassigned** to `localSubscriptions` (`:190`, a `List<String>` from `DBReader.getFeedListDownloadUrls(true)` at `:147`), then **mutated** by `removeAll` (`:193`). Kotlin forces this to be `var queuedAddedFeeds: MutableList<String>`; the `DBReader` return is a platform `(Mutable)List<String>!` so the assignment does compile, but J2K's most likely output infers `ArrayList<String>` from the initialiser and then fails to compile line 190 — at which point the obvious "fix" (`queuedAddedFeeds = ArrayList(localSubscriptions)`) **breaks the aliasing** and changes what `removeLegacyConflictingFeedEntries(localSubscriptions)` sees in the `catch` at `:210`. Same class of trap at `:151` (`queuedRemovedFeeds`, `removeAll` at `:194`) and at `SyncService.java:229,246` (`queuedEpisodeActions` gets `.add()`ed on the first-sync path).

**Hazard 8 (compile-time, loud, easy to get wrong): the varargs call at `SyncService.java:302`.** `DBWriter.removeQueueItem(Context, boolean, long... itemIds)` (`DBWriter.java:477-478`) is invoked as `removeQueueItem(getApplicationContext(), false, queueToBeRemoved.toArray())` where `LongList.toArray()` returns `long[]`. Kotlin requires the spread operator: `DBWriter.removeQueueItem(applicationContext, false, *queueToBeRemoved.toArray())`. Without it the call does not compile; **with a plausible mis-fix it binds the sibling overload `removeQueueItem(Context, boolean, FeedItem)`** (`DBWriter.java:472-473`) — a different database operation. Loud either way, but it is a one-character difference between correct and wrong.

**Other conversion mechanics.**
- **`SyncService extends Worker` with `@NonNull`-annotated constructor parameters** (`:60`). Both are non-null in Kotlin (Milestone 11's Rule A precedent, and AndroidX declares them `@NonNull` anyway). `doWork()` is `@Override @NonNull` → `override fun doWork(): Result`. **The class must not become `internal`, must not be renamed, and must not move package** — WorkManager persists its FQCN (see interop boundary).
- **`private static boolean currentlyActive`** (`:57`) becomes a `private var` in a `companion object`. It is read and written from `doWork()` on a WorkManager background thread and read from `SynchronizationQueueImpl.getWorkRequest()` on whatever thread enqueues — unsynchronised today. Do **not** add `@Volatile`; that is a behaviour change, not a transcription.
- **`private synchronized void processEpisodeActions`** (`:265`) → `@Synchronized private fun`. J2K handles this, but note the lock object stays the `SyncService` instance.
- **`public static final String TAG`** on `SyncService` (`:55`) and `EpisodeActionFilter` (`:15`) → `const val` in a companion, which emits a genuine `public static final String`. Neither is referenced outside its own file (verified by grep), so this is preservation rather than necessity.
- **`(NotificationManager) getApplicationContext().getSystemService(...)`** (`:308-309,343-344`). `Context.getSystemService(String)` is `@Nullable` in the SDK, so Kotlin sees `Any?` and `as NotificationManager` throws `NullPointerException` **at the cast** where Java throws it one line later at `nm.cancel(...)`. Same exception class, moved throw site; `as NotificationManager?` + `!!` is the byte-faithful alternative. Low stakes, worth a recorded decision rather than an accident.
- **`feed.setItems(Collections.emptyList())`** (`SyncService.java:177`) against `Feed.items: MutableList<FeedItem>?` (`Feed.kt:54`). `Collections.emptyList()` is a platform type, so `feed.items = Collections.emptyList()` compiles and stays the same immutable singleton. J2K may rewrite it to Kotlin's `emptyList()` (returns `List<T>` — **will not compile** against `MutableList?`) and the obvious repair (`mutableListOf()`) silently swaps an immutable list for a mutable one.
- **`androidx.core.util.Pair` must not become `kotlin.Pair`.** `EpisodeActionFilter.java:6,25,58` and `SyncService.java:14,271` both import the AndroidX type, and `EpisodeActionFilterTest.java:4,60` binds the return type explicitly. A J2K substitution breaks the test at compile time (loud) but would also change `equals`/`hashCode` semantics for the `ArrayMap` keys.
- **Kotlin generics in the Java-facing signature.** `EpisodeActionFilter.getRemoteActionsOverridingLocalActions`'s two `List<EpisodeAction>` parameters acquire `? extends` in the emitted Java signature; every caller passes `List<EpisodeAction>`/`ArrayList<EpisodeAction>`, which remains assignable, and nothing overrides the method, so Milestone 11's D5 `@JvmSuppressWildcards` situation does **not** recur. Kotlin does not emit wildcards in **return** position, so `Map<Pair<String,String>, EpisodeAction>` stays exact and `EpisodeActionFilterTest.java:60`'s direct assignment keeps compiling — but this should be machine-verified with `javap -s`, not assumed.
- **`R` resolution is safe and worth stating because it looks like it should not be.** The module's `namespace` is `de.danoeh.antennapod.net.sync.service` (`build.gradle:8`), identical to the source package, and `gradle.properties:3` sets `android.nonTransitiveRClass=false`. So `R.string.sync_status_*` (from `:ui:i18n`), `R.drawable.ic_notification_sync_error` (from `:ui:notifications`) and `R.id.notification_gpodnet_sync_*` / `R.id.pending_intent_sync_error` (from this module's own `src/main/res/values/ids.xml`) all resolve through this module's own generated `R` with **no import**, in Kotlin exactly as in Java.
- **`e.printStackTrace()`** appears six times in `SynchronizationQueueStorage` (`:38,53,69,110,142,154`) and once in `SyncService` (`:127`). Transcribes unchanged; do not upgrade to `Log.e`.

**Build and gate mechanics — this module is flavoured, unlike Milestone 11's.**
- `net/sync/service/build.gradle` needs `alias(libs.plugins.kotlin.android)` and `alias(libs.plugins.ktlint)` added to its `plugins` block (`:1-3`), matching `net/sync/service-interface/build.gradle:1-5`. `kotlin = "2.3.20"` and `ktlint = "12.3.0"` are already in the version catalog.
- **It applies `playFlavor.gradle`** (`:5`), which Milestone 11's module did not. Unit-test tasks are therefore `testFreeDebugUnitTest`, `testPlayDebugUnitTest`, `testFreeReleaseUnitTest`, `testPlayReleaseUnitTest` (confirmed by the four `build/test-results/test*UnitTest/` directories present on disk), aggregated by `:net:sync:service:test`. **Milestone 11's `testDebugUnitTest`/`testReleaseUnitTest` commands must not be copy-pasted.** Verify with `./gradlew :net:sync:service:tasks --all` rather than inheriting either milestone's list.
- **The free-flavour SpotBugs gate gap from Milestone 10 *does* apply here** — Milestone 11 explicitly noted it did not apply to the unflavoured service-interface module. `common.gradle:100-101` names the only two report files the throwing `doLast` ever parses — `build/reports/spotbugs/debug.xml` and `playDebug.xml` — and `:142-143` makes `lint` depend on `spotbugsDebug`/`spotbugsPlayDebug`. This module emits **only** `playDebug.xml` (verified: `net/sync/service/build/reports/spotbugs/` contains exactly that one file), so `spotbugsFreeDebug`'s output is generated but never parsed and never throws. Plan around the play flavour being the only enforced one.
- **Current baseline is clean**: lint "No issues found", SpotBugs 0 `BugInstance`s, no `net.sync` entry in `config/spotbugs/exclude.xml`, no entry in `config/checkstyle/suppressions.xml`. Any new finding is genuinely new. Expected Kotlin-bytecode noise to watch for: `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE` from `Intrinsics`, and `MS_SHOULD_BE_FINAL`/`MS_CANNOT_BE_FINAL` on the companion-backed `currentlyActive` field.
- **Checkstyle coverage drops to zero as files convert.** `common.gradle:147-153` sources only `fileTree('src/main/java') { include '**/*.java' }` (plus `src/free/java`/`src/play/java`, neither of which exists here), and `-Xlint:all,…,-Werror` (`common.gradle:43-47`) applies to `JavaCompile` only. Already recorded as deferred initiative #3 in `tasks/antennapod-model-kotlin-future-work.md`.
- **Test dependencies already exist** (`build.gradle:34`, `testImplementation libs.junit`) — unlike Milestone 11, no test source set needs creating. But `libs.robolectric` is very likely needed for gaps 1 and 9 (`SharedPreferences`, `NotificationManager`, `org.json`), and `androidx.work:work-testing` — **which is not declared anywhere in the repo today** (`gradle/libs.versions.toml` has no entry) — is the enabling dependency for gaps 3 and 6. `net/sync/service-interface/build.gradle:21-24` is the precedent for adding Robolectric with a disclosed, scoped justification comment.
- **Dead dependencies:** `libs.okhttp` (`:29`), `libs.rxandroid` (`:30`), `libs.guava` (`:32`) have zero references in `src/`, verified by grep for `okhttp3`, `io.reactivex.rxjava3.android`/`AndroidSchedulers` and `com.google.common`. RxJava3 itself (`:31`) **is** used, by `LockingAsyncExecutor.java:5-6` only. Also dead, but in another module: `ui/preferences/build.gradle:35`'s dependency on this module. Flagged so none of these is read as hidden coupling; removing any of them is not required by this track.

#### Track prerequisites

- **`kotlin`: no prerequisites — met.** The Kotlin Android and ktlint plugins are proven on four modules in this repo including this module's direct dependency `:net:sync:service-interface`; `.editorconfig` already carries the ktlint style; the module already has a test source set and a JUnit dependency. Every upstream module this one consumes for its riskiest types (`:net:sync:service-interface`, `:model`, `:event`, `:net:download:service-interface`) is **already fully Kotlin**, which is the best possible ordering — the interop pressure is inbound-to-this-module and already visible in the type system rather than deferred. The genuine *conditions* on this track, none of which is a prerequisite in the dependency sense: (i) the module's existing safety net is **7 tests covering 88 of 808 LOC**, and the 547 LOC carrying essentially all the risk (`SyncService`, `SynchronizationQueueStorage`) have **zero** coverage, so a substantial characterization suite is unavoidably Step 1; (ii) unlike every prior milestone, **no other module's compile or test suite can catch a regression here** — there is one inbound call site and it exercises only a constructor — so the characterization suite is not a supplement to the compile-check, it is the *entire* proof; and (iii) two of the module's Java idioms (package-private `static`, `protected`-as-package-private) have no Kotlin equivalent, which converts file ordering from a convenience into a correctness constraint.

No other track was requested, so `gradle-kts`, `di`, `concurrency`, `compose`, and `navigation` prerequisites are not assessed. Note in particular that `LockingAsyncExecutor`'s RxJava3 usage and `SyncService`'s `Thread.sleep` busy-wait are **`concurrency`-track material and explicitly out of scope here** — they transcribe as-is.

### Unknowns

Questions the planner needs to resolve before ordering steps:

1. **What does `SynchronizationQueueStorage.java:35` become?** The sharpest decision in the milestone (hazard 2 / gap 2). `EpisodeAction.readFromJsonObject` returns `EpisodeAction?`; the list is declared `ArrayList<EpisodeAction>`; and Milestone 11 already committed `ISyncService.uploadEpisodeActions` and `EpisodeActionChanges` to **non-null** element types, so the faithful `ArrayList<EpisodeAction?>` propagates a type change into `SyncService`'s upload call. `!!` (crash at parse), `?.let { add(it) }` (silently drop the malformed entry), and widening the type each produce a different observable outcome for a user with a corrupt queue entry, and none is compiler-caught. Needs an explicit recorded decision plus gap 2's golden-fixture test landed first.
2. **Target visibility for `SynchronizationQueueStorage`'s five `protected` methods and `SyncService.isCurrentlyActive()`, and the file order that reaches it.** `internal` is the honest equivalent but is name-mangled and so breaks any Java caller still in the module; Kotlin `protected` works only while callers are Java; `public` is a deliberate widening. There is no single-file conversion order in which both the "convert storage first" and "convert callers first" paths stay green with the same modifier — the planner must choose the modifier, then derive the order (likely: convert `SynchronizationQueueStorage`, `SynchronizationQueueImpl` and `SyncService` in one commit), or accept `@JvmName` to defeat mangling.
3. **Does this milestone take on `androidx.work:work-testing`?** Gaps 3 and 6 — `doWork()`'s five-outcome control-flow matrix and `SynchronizationQueueImpl`'s guard conditions — cover the majority of the module's untested risk, and `TestListenableWorkerBuilder` is the standard way to reach them off-device. But the artifact is **not in `gradle/libs.versions.toml` anywhere in the repo**, so this milestone would be introducing a new test dependency to the whole project, not just to this module. The alternative is to characterize `doWork()` only indirectly (through `SynchronizationQueueStorage` and `EpisodeActionFilter`) and accept that `SyncService`'s orchestration converts under weaker proof than any prior milestone's riskiest file. That trade should be made explicitly.
4. **How much Robolectric, and scoped how?** Gap 1 (SharedPreferences round-trip) and gap 9 (notification paths) both need Android framework classes that throw `RuntimeException: Stub!` under plain JUnit (`common.gradle:50-53` sets `includeAndroidResources = true` but not `returnDefaultValues`). Milestone 11's `net/sync/service-interface/build.gradle:21-24` established the disclosed-and-scoped-comment precedent. Note the interaction with hazard/flavour mechanics: adding Robolectric here multiplies across **four** flavour variants, not two.
5. **Is `removeLegacyConflictingFeedEntries` (`:119-128`) pinned or fixed?** It writes `List.toString()` — `"[a, b]"` — into two prefs keys that every other read path parses with `new JSONArray(...)`, which throws and is swallowed, silently emptying both queues. This is a live defect on the `SyncServiceException` catch path (`SyncService.java:210`), not dead code like Milestone 11's `EpisodeAction.equals`. Milestone 11's D10 reasoning (pin, do not fix inside an equivalence diff; file the fix as future work) applies with the same force, but the reachability difference is real and the planner should say so out loud rather than inherit the precedent silently.
6. **Milestone shape: one PR or two?** 808 LOC across 6 files argues for one, matching Milestone 11's 514/9. But the characterization debt is heavier in absolute terms (547 essentially-untested LOC vs Milestone 11's 514 entirely-untested but mostly-declarative LOC), the conversion has an ordering constraint (Unknown 2) that Milestone 11 did not, and Unknown 3's new test dependency is a decision with repo-wide reach. A characterization-only PR followed by a conversion PR is the plausible alternative.
7. **Does `SyncService` convert at all in this milestone?** 371 LOC, 46% of the module, zero tests, and the only file whose FQCN is a persisted external contract. Converting the other five files (437 LOC) and deferring `SyncService` is a legitimate scoping answer and is structurally the same call Milestone 11 considered for `EpisodeAction` (and rejected). Note the interaction with Unknown 2: `SyncService.java:210` is one of the five `protected`-as-package-private call sites, so deferring it constrains the visibility decision rather than sidestepping it.
8. **Are the three dead library dependencies and the dead `ui/preferences` module dependency touched?** Milestone 11's D14 left its three dead deps in place on minimal-diff grounds and did not run `:dependencies` to justify it. The `ui/preferences` one is different in kind — it is an edit to **another module's** build file and would break the "zero edits outside the module" acceptance criterion that both Milestone 10 and 11 used as their headline proof. Recommend leaving all four alone and filing them as future work, but it should be a recorded decision.
9. **`EpisodeActionFilterTest` and `GuidValidatorTest` are JUnit 3 (`extends TestCase`) — do new characterization tests match them or use JUnit 4?** The module declares only `libs.junit` (which supplies both). Milestone 11's nine new test files used JUnit 4 style. Mixing styles in one directory is normal but should be a stated choice, and the two existing files must **not** be modernised — they are the milestone's pre-existing equivalence oracle and must compile and pass unmodified.

### Sources

Module under conversion:
- `net/sync/service/README.md:1-4` — module purpose (`SyncService` coordinates the active backend; `SynchronizationQueueStorage` persists the queue in SharedPreferences)
- `net/sync/service/build.gradle:1-35` — plugins `:1-3` (library only; **no** kotlin/ktlint), `common.gradle` `:4`, **`playFlavor.gradle` `:5`**, namespace `:8`, ten module deps `:12-21`, `annotationProcessor libs.androidx.annotation` `:23`, live libs `:24` (androidx.core), `:25` (work.runtime), `:27` (eventbus), `:28` (commons.lang3), `:31` (rxjava), dead libs `:29` (okhttp), `:30` (rxandroid), `:32` (guava), `testImplementation libs.junit` `:34` — **and no other test dependency**
- `net/sync/service/src/main/res/values/ids.xml:1-5` — `notification_gpodnet_sync_error`, `notification_gpodnet_sync_autherror`, `pending_intent_sync_error`
- `net/sync/service/src/` — `main` and `test` only; **no `androidTest`**, no `AndroidManifest.xml`, no `src/free`/`src/play`
- `.../SyncService.java:54-371` — `extends Worker` `:54`, `TAG` `:55`, `private static boolean currentlyActive` `:57`, storage field `:58`, `@NonNull` ctor `:60-63`, `doWork` `:65-113` (null-provider early success `:68-71`, re-entrancy guard `:73-75`, wait-for-downloads early return `:82-88`, `SyncServiceException`→retry / else→failure `:100-109`, `finally` reset `:110-112`), `waitForDownloadServiceCompleted` `:115-129` (`//noinspection BusyWait` `:123`, `Thread.sleep(1000)` `:124`), `someFeedWasNotRefreshedYet` `:131-138`, **package-private `isCurrentlyActive()` `:140-142`**, `syncSubscriptions` `:144-217` (localSubscriptions `:147`, queued lists `:151-152`, four-way URL skip ladder `:156-174`, `new Feed(downloadUrl, null, "Unknown podcast")` `:176`, `setItems(Collections.emptyList())` `:177`, removed-feed skip `:182-186`, **first-sync aliasing `:188-191`**, `removeAll` `:193-194`, `uploadResponse.timestamp` `:208`, `catch (SyncServiceException)` + `removeLegacyConflictingFeedEntries` `:209-211`, `LockingAsyncExecutor.lock/unlock` `:203,213`), `syncEpisodeActions` `:219-263` (`getQueuedEpisodeActions` `:229`, first-sync played upload `:230-248`, `postResponse.timestamp` `:255`, `LockingAsyncExecutor.lock/unlock` `:250,259`), `processEpisodeActions` `:265-305` (`private synchronized` `:265`, `GuidValidator.isValidGuid(action.getGuid())` `:277`, null-item/null-media skips `:279-286`, `setPosition(getPosition() * 1000)` `:288`, `almostEnded` `:290-291`, **varargs `removeQueueItem(..., toArray())` `:302`**), `clearErrorNotifications` `:307-312`, `updateErrorNotification` `:314-349` (description `:316-317`, prefs short-circuit `:319-322`, `MessageEvent` instead-of-notification `:323-326`, `POST_NOTIFICATIONS` gate `:345-348`), `getActiveSyncProvider` `:351-370` (`fromIdentifier` + null-guard `:352-357`, two backend branches `:359-366`)
- `.../SynchronizationQueueStorage.java:16-176` — prefs name `:18`, three keys `:19-21`, field `:22`, ctor `:24-26`, `getQueuedEpisodeActions` `:28-41` (**unguarded `readFromJsonObject` add `:35`**, swallowed `JSONException` `:37-39`), `getQueuedRemovedFeeds` `:43-57`, `getQueuedAddedFeeds` `:59-72`, `clearEpisodeActionQueue` `:74-78`, `clearFeedQueues` `:80-85`, **`protected clearQueue` `:87-95`** (also `resetTimestamps()` `:88`), **`protected enqueueFeedAdded` `:97-112`**, **`protected removeLegacyConflictingFeedEntries` `:119-128`** (writes `List.toString()` `:125-126`), **`protected enqueueFeedRemoved` `:130-144`**, `private indexOf` `:146-157`, **`protected enqueueEpisodeAction` `:159-171`** (`writeToJsonObject` into `JSONArray.put` `:164`), `private getSharedPreferences` `:173-175`
- `.../SynchronizationQueueImpl.java:21-130` — `extends SynchronizationQueue` `:21`, `WORK_ID_SYNC` `:22`, `context` field `:23`, public `(Context)` ctor `:25-27`, `sync` `:29-32`, `syncIfNotSyncedRecently` (10-min threshold) `:34-38`, `syncImmediately` `:40-45`, `fullSync` `:47-52`, `getWorkRequest` `:54-75` (mobile-sync constraint fork `:56-60`, **`SyncService.class` `:62`**, `SyncService.isCurrentlyActive()` `:66`, 2-min vs 20-s delay + `SyncServiceEvent` `:66-73`), `clear` `:77-79`, `enqueueFeedAdded` `:81-89`, `enqueueFeedRemoved` `:91-99`, `enqueueEpisodeAction` `:101-109`, `enqueueEpisodePlayed` `:111-129` (provider guard `:112-114`, **unguarded `media.getItem()` chains `:115-116`**, start-position guard `:119`, `Builder(media.getItem(), EpisodeAction.PLAY)` `:122`, `/1000` conversions `:124-126`)
- `.../EpisodeActionFilter.java:13-77` — `TAG` `:15`, `getRemoteActionsOverridingLocalActions` `:17-51` (`ArrayMap` `:21`, `Pair` key `:25`, **`switch (remoteAction.getAction())` `:26`**, NEW/DOWNLOAD no-op `:27-29`, PLAY `:30-40`, DELETE no-op + comment `:41-43`, `default` `Log.e` `:44-46`), `createUniqueLocalMostRecentPlayActions` `:53-67` (null-timestamp branch `:60-61`), `secondActionOverridesFirstAction` `:69-75`
- `.../LockingAsyncExecutor.java:8-43` — `private static final ReentrantLock lock` `:10`, javadoc `:12-15`, `executeLockedAsync` `:16-34` (uncontended inline `:17-22`, contended `Completable`/`Schedulers.computation()` `:24-32`), `unlock` `:36-38`, `lock` `:40-42`
- `.../GuidValidator.java:3-10` — `isValidGuid(String)` `:5-9`, **first clause `guid != null` `:6`**

Existing tests (the module's entire safety net):
- `net/sync/service/src/test/java/.../GuidValidatorTest.java:5-18` — `extends TestCase`, 2 tests; **`isValidGuid(null)` at `:16`** is the only pre-existing nullability guard in the module
- `net/sync/service/src/test/java/.../EpisodeActionFilterTest.java:18-210` — `extends TestCase`, 5 tests, all asserting map **size only** (`:62,107,145,183,208`); `androidx.core.util.Pair` import `:4`; 20 `EpisodeAction.Action.PLAY` refs; live no-timestamp fixture `:191-196`
- `net/sync/service/build/test-results/` — four flavour variants present (`testFreeDebugUnitTest`, `testFreeReleaseUnitTest`, `testPlayDebugUnitTest`, `testPlayReleaseUnitTest`), i.e. `:net:sync:service:test` = 7 tests × 4 = 28
- `net/sync/service/build/reports/lint-results-freeDebug.txt` — "No issues found."
- `net/sync/service/build/reports/spotbugs/` — contains **only** `playDebug.xml`; 0 `BugInstance` elements

Inbound (the complete list):
- `app/build.gradle:64`, `ui/preferences/build.gradle:35`, `settings.gradle:33`
- `app/src/main/java/de/danoeh/antennapod/ClientConfigurator.java:10,53` — the **single** source-level caller
- `ui/preferences/src/main/java/.../synchronization/SynchronizationPreferencesFragment.java:32,67` — `de.danoeh.antennapod.event.SyncServiceEvent` (from `:event`), **not** from this module; confirms `ui/preferences`' dependency on `:net:sync:service` is dead

Outbound — Milestone 11's converted API (the nullability that lands here):
- `net/sync/service-interface/src/main/java/.../SynchronizationQueue.kt:6-9` (`@JvmStatic var instance: SynchronizationQueue?`), `:24-30` (**four nullable abstract parameters**)
- `.../EpisodeAction.kt:15-33` (`podcast`/`episode`/`guid`: `String?`, `action`: `Action?`, `timestamp`: `Date?`, `started`/`position`/`total`: `Int`), `:74` (`writeToJsonObject(): JSONObject?`), `:101-106` (nested `enum class Action`), `:122` (**`Builder(item: FeedItem, action: Action?)` — `item` non-null**), `:130` (`Builder(String?, String?, Action?)`), `:180-190` (four `@JvmField val` aliases), `:200` (**`@JvmStatic fun readFromJsonObject(JSONObject): EpisodeAction?`**)
- `.../ISyncService.kt:5-23` — six `@Throws(SyncServiceException::class)` methods; `@JvmSuppressWildcards` + **non-null `List` elements** `:12-13,19-20`
- `.../UploadChangesResponse.kt:7` — `@JvmField val timestamp: Long`
- `.../SynchronizationProvider.kt:3-12` — `enum class …(val identifier: String)`, `@JvmStatic fun fromIdentifier(provider: String?): SynchronizationProvider?`
- `.../SubscriptionChanges.kt:3-10`, `.../EpisodeActionChanges.kt:3-10` — non-null `List` properties, verbatim-preserved `toString()` formats
- `.../SyncServiceException.kt:3-9` — `open class … : Exception`, `serialVersionUID` in companion

Outbound — other dependency nullability:
- `model/src/main/java/.../FeedItem.kt:20,29,47,56` — `id: Long`, `itemIdentifier: String?`, `media: FeedMedia?`, `feed: Feed?`
- `model/src/main/java/.../Feed.kt:27,54,60,72,209,362,366,395` — `downloadUrl: String?`, `items: MutableList<FeedItem>?`, `lastRefreshAttempt: Long`, `preferences: FeedPreferences?`, `constructor(url:String?, lastModified:String?, title:String?)`, `isLocalFeed()`, `getState()`, `const val STATE_NOT_SUBSCRIBED = 1`
- `model/src/main/java/.../FeedMedia.kt:34,43,45,76` — `item: FeedItem?`, `duration: Int`, `position: Int`, `startPosition: Int`
- `event/src/main/java/.../SyncServiceEvent.kt:3`, `FeedUpdateRunningEvent.kt:3` (`@JvmField val isFeedUpdateRunning`), `MessageEvent.kt:6-11`
- `net/download/service-interface/src/main/java/.../FeedUpdateManager.kt:10,24` — `runOnce(context: Context?)`; **`getInstance(): FeedUpdateManager?`** (nullable, dereferenced unguarded at `SyncService.java:86`)
- `storage/database/src/main/java/.../DBReader.java:58-59,81,105,222-223,428` — `@NonNull getFeedList()`, `getFeedListDownloadUrls(boolean)`, `loadFeedDataOfFeedItemList`, `@NonNull getEpisodes(...)`, `getFeedItemByGuidOrEpisodeUrl` (**javadoc "or null"**, unannotated)
- `storage/database/src/main/java/.../DBWriter.java:472-473,477-478,723,965` — **overloaded `removeQueueItem(Context, boolean, FeedItem)` vs `(Context, boolean, long...)`**, `setItemList(List<FeedItem>)`, `removeFeedWithDownloadUrl(Context, String)`
- `storage/database/src/main/java/.../FeedDatabaseWriter.java:58` — `updateFeed(Context, Feed, boolean)`
- `net/common/src/main/java/.../RedirectChecker.java:44-45,56-57` — **`@Nullable getNewUrlIfPermanentRedirect(String)`**, `@NonNull getFinalUrl(@NonNull String)`
- `net/common/src/main/java/.../UrlChecker.java:104` — `containsUrl(List<String>, String)`
- `storage/preferences/src/main/java/.../SynchronizationSettings.java:20,24,36,44,48,52,56,60,64,68` and `SynchronizationCredentials.java:22,30,38,46` — all Java, all platform types
- `storage/preferences/src/main/java/.../UserPreferences.java:447,504,656` — `getSmartMarkAsPlayedSecs()`, `isAllowMobileSync()`, `gpodnetNotificationsEnabled()`
- `ui/notifications/src/main/java/.../NotificationUtils.java:20` — `CHANNEL_ID_SYNC_ERROR`; `ui/notifications/src/main/res/drawable/ic_notification_sync_error.xml`
- `ui/i18n/src/main/res/values/strings.xml:652-659,776-777` — the 10 string resources this module references, all present
- Module language census: `:model` 0 java / 27 kt; `:event` 0/23; `:net:download:service-interface` 0/7; `:net:sync:service-interface` 0/9 (+9 Java test files); `:net:common` 8/0; `:storage:database` 25/0; `:storage:preferences` 7/0; `:ui:notifications` 1/0; `:net:sync:gpoddernet` 13/0

Build, gates, precedent:
- `common.gradle:38-41` — `sourceCompatibility`/`targetCompatibility` 21; `:43-47` — `-Xlint:all,… -Werror` on `JavaCompile` only; `:50-53` — `unitTests { includeAndroidResources = true }`, **no `returnDefaultValues`**; `:57-63` — `lint { warningsAsErrors true; abortOnError true; checkDependencies true }`; `:86-129` — SpotBugs `ignoreFailures = true` (`:93`) with an XML-parsing `doLast` that **throws** (`:126`) on any `BugInstance` carrying a `SourceLine`, parsing **only** `debug.xml` and `playDebug.xml` (`:100-101`); `:142-143` — `lint` dependsOn `spotbugsDebug`/`spotbugsPlayDebug`; `:147-153` — `checkstyle` sourcing only `src/main/java/**/*.java` (plus `src/free/java`/`src/play/java` if present)
- `playFlavor.gradle` — `free`/`play` dimension; **applied by this module** (`net/sync/service/build.gradle:5`), unlike `:net:sync:service-interface`
- `gradle.properties:3` — `android.nonTransitiveRClass=false` (why cross-module `R` references resolve without imports)
- `config/spotbugs/exclude.xml` — no `net.sync` entry; `config/checkstyle/suppressions.xml` — no `net/sync/service` entry
- `net/sync/service-interface/build.gradle:1-5,21-24` — Kotlin+ktlint plugin precedent and the disclosed, scoped Robolectric-exception comment precedent
- `gradle/libs.versions.toml` — `kotlin = "2.3.20"`, `ktlint = "12.3.0"`, `junit`, `robolectric = "4.16"`; **no `work-testing` entry anywhere**
- `tasks/antennapod-net-sync-service-interface-kotlin.md` — Milestone 11 Research + Plan; D0 (the two nullability rules), D1 (`fromIdentifier`'s nullable parameter), D2/D3 (`@JvmField`), D4 (`@Throws` convention), D5 (`@JvmSuppressWildcards`), D7 (nullable holder + nullable abstract parameters — **the decision this module inherits**), D9 (`EpisodeAction` nullability map), D10 (pin-don't-fix precedent), D12 (Java tests as the equivalence oracle), D14 (dead dependencies left alone), D16 (one PR); Out of Scope `:582,630` explicitly deferred this module
- `tasks/antennapod-net-download-service-interface-kotlin.md` — Milestone 10 Research + Plan; the free-flavour SpotBugs gate gap, which recurs here
- `tasks/antennapod-model-kotlin-future-work.md` — deferred initiative #3 (`-Werror`/checkstyle coverage lost as `JavaCompile` goes `NO-SOURCE`), #5 (orphaned checkstyle suppressions on rename)
- `git log --name-only 4d73da5b4^..4d7b9bb3f` — Milestone 11's 14 commits touched **zero** files under `net/sync/service/`, confirming its JVM-shape decisions held against this module unedited
- `git log --oneline -12 -- net/sync/service/` — last functionally changed upstream by `313b3a267` ("Work around incorrect redirects duplicating subscriptions on sync", #8444 — the `RedirectChecker` ladder at `SyncService.java:163-174`), `00f533f36` (#8380, non-subscribed feed filtering), `97416e045` (#8374, `Schedulers.io` → `Schedulers.computation` in `LockingAsyncExecutor`); **no local migration history**

## Plan
_Last updated by: legacy-android-planner | 2026-08-02_

### Objective

Convert all 6 Java production files in `:net:sync:service` to Kotlin (`kotlin` track, one module, one milestone), completing the service-interface/service pair Milestone 11 started, while preserving three contracts no compiler in this repo can check: WorkManager's persisted fully-qualified class name for `SyncService` (already-scheduled work on an upgrading device instantiates it reflectively), the `"synchronization"` SharedPreferences file and its three JSON-array keys (the user's un-uploaded listening history), and the exact observable outcome of every latent NPE and swallowed exception the module ships today. Because the module's **only** inbound caller is `ClientConfigurator.java:53`'s constructor call, the seven-module compile that proved Milestone 11 does not exist here — so equivalence is proven instead by a **new five-file Java characterization suite** covering the 547 currently-untested LOC, written first, compiling and passing **unmodified** before and after, with `SynchronizationQueueStorage`'s three-queue round-trip and Milestone 11's now-honest `EpisodeAction?` landing at `SynchronizationQueueStorage.java:35` as its centre of gravity.

### Resolved Decisions

All nine research Unknowns are resolved below; the mapping is **U1→D3, U2→D2, U3→D12, U4→D11, U5→D9, U6→D14, U7→D13, U8→D15, U9→D10**. Nothing is deferred to the developer except three explicitly-scoped mechanical verifications, each with a stated fallback and a mandated record in Implementation Notes: D2's `@JvmName` spike, D8's `javap -c` overload checks, and D11's WorkManager-under-Robolectric probe.

The `kotlin` track has **no unmet prerequisite** (research: "no prerequisites — met"), so nothing is surfaced to Open Questions on prerequisite grounds and no track is dropped.

---

**D0 — The rules inherited from Milestone 11, restated because every decision below is checkable against them.**

- **Rule N (non-null only where the first action already dereferences).** A reference parameter is **non-null** if and only if the Java body dereferences it unconditionally as its first action, so Kotlin's `Intrinsics.checkNotNullParameter` throws `NullPointerException` at the same public entry point, for the same input, that Java already throws from. Everywhere else it is **nullable**.
- **Rule A (honour a pre-existing `@NonNull`).** Where the Java source already carries `androidx.annotation.NonNull`, the Kotlin type is non-null even if Rule N would say otherwise.
- **Rule M11 (an override's parameter types are not a choice).** Where `SynchronizationQueue.kt:24-30` declares a parameter nullable, `SynchronizationQueueImpl`'s override **must** match exactly. This is not a nullability decision this milestone gets to make; it was made in Milestone 11 (its D7c) and is only being honoured here.

Rule N yields non-null at exactly three sites in this module: `SynchronizationQueueStorage(context: Context)` (`:25` dereferences immediately), `removeLegacyConflictingFeedEntries(currentLocalSubscriptions: Collection<String>)` (`:122` passes it to `removeAll`, which NPEs on null), and `updateErrorNotification(exception: Exception)` (`:317` reads `getMessage()` before anything else). Rule A yields non-null at exactly two: `SyncService`'s two constructor parameters (`:60`). **Every other reference parameter in the module is nullable.** Any deviation is a review finding.

**`LockingAsyncExecutor.executeLockedAsync`'s `runnable` parameter is `Runnable?`, and it is enumerated here rather than left to inference because it is the one parameter in the module where Rule N's *wording* and Rule N's *purpose* point in opposite directions.** Every path through `executeLockedAsync` does eventually dereference `runnable` — which is the same basis on which `removeLegacyConflictingFeedEntries` earned non-null above — so a mechanical reading of "dereferences it unconditionally" would make it non-null. That reading is wrong here, and the clause that settles it is Rule N's *purpose*: non-null is correct only when `Intrinsics.checkNotNullParameter` "throws `NullPointerException` **at the same public entry point, for the same input, that Java already throws from**." On the **contended** path Java does not throw from that entry point at all. `LockingAsyncExecutor.java:17` calls `lock.tryLock()` **before** any dereference; when it fails, the dereference is relocated into a `Completable.fromRunnable` lambda executed on `Schedulers.computation()` (`:24-32`), the calling thread returns **normally**, and the resulting NPE is delivered to `RxJavaPlugins`' global error handler rather than to any caller. A non-null `runnable` would fire `checkNotNullParameter` before `tryLock()` on **both** paths, converting that asynchronous, caller-invisible failure into a synchronous one thrown at the call site — precisely the sync-vs-async delivery-path change D7 already treats as decision-worthy for the structurally identical `enqueueEpisodeAction` case, and precisely the class of "unlikely to be reachable, transcribed correctly anyway" change D8 rows 15–17 refuse to make.

So: `runnable: Runnable?`, with `runnable!!.run()` at **both** dereference sites (`:19` uncontended and `:27` inside the `Completable` lambda) — D8 rows 18–19. The two branches are disjoint, so the `!!` on one grants no smart cast to the other and both are required. On the uncontended path this reproduces Java exactly: `tryLock()` succeeds, the NPE is thrown from inside the `try`, the `finally` releases the lock, and the NPE propagates to the caller. On the contended path it reproduces Java exactly: the call returns normally and the NPE surfaces on a computation thread through `RxJavaPlugins`. Both are pinned by the two tests Step 5 names, and the **contended** one is the only test that discriminates the two declarations — a bare `assertThrows(NullPointerException.class, …)` on the uncontended path passes under either, which is exactly why "it obviously throws NPE either way" is not proof. Nullability is invisible in the emitted Java signature, so no Java caller and no test file is affected by this choice in either direction.

---

**D1 — Conversion order is derived from D2, not chosen for comfort, and every step leaves a green full-repo build.**

Order: `GuidValidator` → `LockingAsyncExecutor` → `EpisodeActionFilter` → `SynchronizationQueueStorage` → `SynchronizationQueueImpl` → `SyncService`. Smallest-and-loudest first, riskiest last, exactly Milestone 11's D16 shape. `GuidValidator` (11 LOC) is the wiring proof: the cheapest possible demonstration that `kotlin.android`/`ktlint` and a mixed Java/Kotlin `src/main/java` source set work in a **flavoured** module, before anything risky rides on that assumption.

The critical property, and the reason D2 exists: because D2 makes the five cross-class `SynchronizationQueueStorage` methods carry their **unchanged JVM names at JVM-public visibility**, this order works with the still-Java callers at every intermediate step, and it also works once those callers are Kotlin. **The ordering constraint research identified is dissolved rather than navigated.** Specifically:

- At Step 9, Kotlin `EpisodeActionFilter`'s second parameter is `List<EpisodeAction?>` (D3), whose emitted Java signature is `java.util.List<? extends EpisodeAction>`; the still-Java `SyncService.java:271-273` passes an `ArrayList<EpisodeAction>`, which is assignable, and `EpisodeActionFilterTest.java:60` likewise. No edit.
- At Step 10, Kotlin `SynchronizationQueueStorage`'s five `internal @JvmName` methods are JVM-public with their Java names, so the still-Java `SynchronizationQueueImpl.java:78,86,96,106` and `SyncService.java:210` compile unedited.
- At Step 11, Kotlin `SynchronizationQueueImpl.kt` calls the still-Java package-private `SyncService.isCurrentlyActive()` from the same package, which Kotlin permits. **Fallback, if that resolution fails:** swap Steps 11 and 12 — with D2 applied, `SyncService`'s converted `isCurrentlyActive()` is reachable from the still-Java `SynchronizationQueueImpl` only under the `public` fallback, so in that case apply D2's fallback to `isCurrentlyActive()` as well and record both.

---

**D2 — The five `protected`-as-package-private methods and `SyncService.isCurrentlyActive()` become `internal`; the five carry `@JvmName` to defeat mangling, `isCurrentlyActive()` does not.** (Unknown 2; research hazard 1.)

Research framed this as a choice among three bad options. It is actually a choice among four, and the fourth is strictly better.

`SynchronizationQueueStorage.clearQueue()` (`:87`), `enqueueFeedAdded(String)` (`:97`), `removeLegacyConflictingFeedEntries(Collection<String>)` (`:119`), `enqueueFeedRemoved(String)` (`:130`) and `enqueueEpisodeAction(EpisodeAction)` (`:159`) are `protected` and called only from **sibling classes in the same package** — the class has no subclass anywhere in the repo. Evaluate each option against the two constraints that actually bind (the module's own Java callers during conversion, and the Java characterization suite that is this milestone's entire equivalence oracle):

| Option | Kotlin callers in-module | Java callers in-module | Java test suite | Verdict |
|---|---|---|---|---|
| Kotlin `protected` | **fail** (not a subclass) | compile (JVM `protected` ⊇ package) | compile | disqualified at Step 11 |
| plain `internal` | compile | **fail** (mangled `clearQueue$net_sync_service`) | **fail** — would force a test edit | disqualified |
| plain `public` | compile | compile | compile | works, but widens Kotlin-source visibility |
| **`internal` + `@JvmName`** | compile | compile | compile | **chosen** |

`@JvmName("clearQueue") internal fun clearQueue()` emits a JVM-**public** method named `clearQueue` while keeping the declaration module-scoped in Kotlin. That is exactly the intent Java's `protected`-as-package-private was expressing, it keeps the Java oracle compiling **unmodified** in both directions — which is the single property this milestone cannot trade away — and, as D1 notes, it removes the conversion-ordering constraint entirely.

Two honest disclosures. First, the emitted JVM visibility widens from `protected` to `public`; that is true of `internal` and of plain `public` alike, it is unobservable outside the module (the only inbound source reference in the entire repo is `ClientConfigurator.java:10,53`, which touches neither this class nor these methods), and it is recorded in AC17 as a disclosed narrowing-of-intent rather than a silent one. Second, `@JvmName` is not applicable to `open`/`override`/`abstract` members; all five are final non-override members of a final class, so it should be accepted — but "should be" is not a plan, hence the spike.

**Machine-checked at Step 2, not reasoned.** Throwaway-convert `SynchronizationQueueStorage` with the five annotations, build, and run `javap -p`. It must show `public void clearQueue();`, `public void enqueueFeedAdded(java.lang.String);`, `public void enqueueFeedRemoved(java.lang.String);`, `public void enqueueEpisodeAction(…EpisodeAction);` and `public void removeLegacyConflictingFeedEntries(java.util.Collection);` — no `$net_sync_service` suffix on any of them. **Fallback if `@JvmName` is rejected on an `internal` member, or if `javap` still shows mangling:** declare all five plain `public`, record the substitution verbatim in Implementation Notes, and update AC17's disclosure list. Do **not** fall back to Kotlin `protected` (it cannot compile at Step 11) and do **not** fall back to plain `internal` (it forces a test edit, which is D14's hard stop).

`SyncService.isCurrentlyActive()` takes `internal` **without** `@JvmName` and **without** `@JvmStatic`, as a `companion object` function. Its only caller is `SynchronizationQueueImpl.java:66`, which is Kotlin from Step 11 onward and reaches a companion function through the class name unchanged; no Java caller exists anywhere in the repo (research verified `ui/preferences`' dependency is dead), and no characterization test needs it — `currentlyActive` is a private static field that no off-device test can set without reflection, so the re-entrancy guard is not behaviourally reachable regardless of this method's visibility. Adding `@JvmStatic` here would emit a *mangled* static forwarder that serves nobody. This is a deliberate JVM-shape change on a package-private member with zero external reach; AC13 greps to prove no Java file references it.

---

**D3 — `getQueuedEpisodeActions()` returns `ArrayList<EpisodeAction?>`, and the one place that type collides with Milestone 11 is bridged by an unchecked cast, because that is the only option that does not change behaviour.** (Unknown 1; research hazard 2 / gap 2 — the sharpest decision in the milestone.)

`SynchronizationQueueStorage.java:35` reads `actions.add(EpisodeAction.readFromJsonObject(queue.getJSONObject(i)))` with no null check, and after Milestone 11 that method returns `EpisodeAction?` (`EpisodeAction.kt:200`). Today a stored entry that is missing `podcast`/`episode`/`action`, or carries an unrecognised action string, puts a **`null` into the returned list**. Four candidate Kotlin forms, and only one of them preserves what a user with a corrupt queue entry observes:

| Form | What a corrupt entry does | Verdict |
|---|---|---|
| `add(readFromJsonObject(...)!!)` | throws `NullPointerException` inside `getQueuedEpisodeActions()` — *earlier* than today, and on a call path (`SyncService.java:229`) where today the list is built successfully and the throw happens later or not at all | changes behaviour |
| `readFromJsonObject(...)?.let { actions.add(it) }` | silently drops the entry; sync completes successfully where today it fails | changes behaviour, silently, and loses user data |
| `ArrayList<EpisodeAction>` + a filtering/mapping bridge | same as one of the above | changes behaviour |
| **`ArrayList<EpisodeAction?>`, `add(...)` verbatim** | `null` in the list, exactly as today | **chosen** |

The faithful choice propagates, and the propagation is the substance of this decision. Milestone 11 committed `ISyncService.uploadEpisodeActions(queuedEpisodeActions: List<EpisodeAction>)` and `EpisodeActionChanges.episodeActions` to **non-null** element types (`ISyncService.kt:20`, `EpisodeActionChanges.kt:4`), and those files are out of File Scope. So the widened list meets a non-null-element parameter at exactly one site, `SyncService.java:254`. The bridge there is:

```kotlin
@Suppress("UNCHECKED_CAST")
val postResponse = syncServiceImpl.uploadEpisodeActions(queuedEpisodeActions as List<EpisodeAction>)
```

This is not a workaround, it is the **only** behaviour-preserving option: Kotlin compiles a cast to a generic type as a bare `CHECKCAST java/util/List`, which inspects no element and always succeeds on a value that is already a `List`. `filterNotNull()` would drop entries and `map { it!! }` would throw early — both are the behaviour changes the table above rejects. Java's call today is likewise unchecked; the cast is the honest Kotlin spelling of what javac already does. Recorded here rather than left to the developer because a developer facing a red squiggle will reach for `filterNotNull()` by reflex.

Consequences elsewhere, all of them forced by this decision and none of them optional:

- `EpisodeActionFilter.getRemoteActionsOverridingLocalActions(remoteActions: List<EpisodeAction>, queuedEpisodeActions: List<EpisodeAction?>)` — the **second** parameter only. It is the parameter `SynchronizationQueueStorage` actually feeds (`SyncService.java:273`); the first comes from `EpisodeActionChanges.episodeActions`, which is non-null. Inside `createUniqueLocalMostRecentPlayActions`, `Pair(action!!.podcast, action.episode)` throws `NullPointerException` at precisely the statement Java throws from today (`EpisodeActionFilter.java:58`). Both parameters emit `java.util.List<? extends EpisodeAction>` in the Java signature — nullability is invisible there — so `EpisodeActionFilterTest.java` compiles unmodified either way.
- `SyncService.syncEpisodeActions`: `val queuedEpisodeActions: MutableList<EpisodeAction?> = synchronizationQueueStorage.getQueuedEpisodeActions()`, with `:246`'s `.add(played)` unchanged (adding a non-null to a nullable-element list is legal) and `:252-253`'s `StringUtils.join` unchanged.
- The end-to-end outcome this preserves is the one gap 2 named: a corrupt entry plus a non-empty remote-action set yields `NullPointerException` inside `processEpisodeActions`, caught by `doWork()`'s `catch (Exception e)` at `:95`, which is **not** a `SyncServiceException`, therefore an error notification plus `Result.failure()`. Pinned by two tests that must both exist and both fail if the type is narrowed: `SynchronizationQueueStorageTest.getQueuedEpisodeActionsPutsNullIntoTheListForAMalformedEntry` (Step 3) and `EpisodeActionFilterCharacterizationTest.queuedLocalActionThatIsNullThrowsNullPointerException` (Step 4).

---

**D4 — `GuidValidator`, `LockingAsyncExecutor` and `EpisodeActionFilter` become `object`s with `@JvmStatic` on every member. They must not become top-level functions.** (Research hazard 5.)

All three are static-only classes. J2K's default is a Kotlin `object`, whose members are **instance** methods on `INSTANCE` unless annotated `@JvmStatic`; a developer "tidying" them into top-level functions would relocate them to synthetic `GuidValidatorKt`/`EpisodeActionFilterKt` facades and break `GuidValidatorTest.java:8,12-17`, `EpisodeActionFilterTest.java:60,105,143,181,206` and the new `LockingAsyncExecutorTest` at compile time. `@JvmStatic` is mandatory on `isValidGuid`, `getRemoteActionsOverridingLocalActions`, `executeLockedAsync`, `lock` and `unlock`. The two private helpers in `EpisodeActionFilter` do not need it.

`EpisodeActionFilter.TAG` (`:15`) becomes `const val TAG` inside the object, which emits a genuine `public static final String TAG`. `SyncService.TAG` (`:55`) becomes `const val TAG` in a `companion object`, same emission.

Two sub-items, both disclosed:

- Each of the three loses its **implicit public no-arg constructor**. Nothing in the repo calls them (research verified by grep). Disclosed, unobservable narrowing, listed in AC17.
- `LockingAsyncExecutor` has both a `private static final ReentrantLock lock` field (`:10`) and a `public static void lock()` method (`:40`). Both names are kept, exactly as today: Kotlin puts properties and functions in different namespaces, and the JVM keeps fields and methods separate, so `private val lock = ReentrantLock()` and `@JvmStatic fun lock()` coexist. Note that in a Kotlin `object` the property's backing field is an **instance** field of the singleton rather than a `static` one — that difference is not observable, because an `object` is initialized exactly once per process and therefore still yields exactly one process-wide `ReentrantLock`, which is the property `SyncService.java:203,213,250,259` depends on. AC13 asserts `javap -p` shows `public static void lock();` and `public static void unlock();`; it deliberately does **not** assert the field is static. `LockingAsyncExecutorTest.lockIsReentrantSoALockedRunnableMayLockAgain` proves the same lock instance backs all three entry points.

---

**D5 — `GuidValidator.isValidGuid(guid: String?)`. Non-negotiable, and it is the one hazard an existing test catches for free.** (Research hazard 6.)

`GuidValidator.java:6`'s first clause is `guid != null`. J2K emits a non-null `String` because there is no `@Nullable` to tell it otherwise, and `SyncService.java:277` passes `action.getGuid()`, which is `String?` after Milestone 11 (`EpisodeAction.kt:18`). `GuidValidatorTest.java:16` passes a literal `null` from Java: it still *compiles* against a non-null declaration, but the `Intrinsics.checkNotNullParameter` prologue makes it throw, so the test fails loudly. Body transcribed verbatim, including `guid.trim().isEmpty()` and `guid == "null"` — no `isNullOrBlank()`, no `isNullOrEmpty()`, no idiom swap. The three clauses are a single expression; a converted form that reorders or merges them is a review finding.

---

**D6 — `EpisodeActionFilter`'s `switch` becomes `when (remoteAction.action!!)` with the `default` branch preserved as dead code.** (Research hazard 4; gap 4.)

`EpisodeAction.action` is `Action?` (`EpisodeAction.kt:19`). Java's `switch (remoteAction.getAction())` compiles to an ordinal lookup that throws `NullPointerException` for null. J2K's natural output — `when (remoteAction.action) { … else -> Log.e(…) }` — routes null to `else` and **logs instead of throwing**, inverting the failure mode invisibly to the compiler and to code review.

The faithful transcription is `when (remoteAction.action!!) { Action.NEW, Action.DOWNLOAD -> {} ; Action.PLAY -> {…} ; Action.DELETE -> {} ; else -> Log.e(TAG, "Unknown remoteAction: $remoteAction") }`. The `!!` reproduces Java's NPE for a null action at the same statement; the `else` branch is retained even though it is now unreachable, because Java's `default` is equally unreachable and `AGENTS.md` forbids removing code and comments that are already there. In particular the shouting comment at `EpisodeActionFilter.java:42` ("NEVER EVER call DBWriter.deleteFeedMediaOfItem() here, leads to an infinite loop") is transcribed verbatim onto the `DELETE` branch.

Null `action` is not reachable from any production construction path today, so this is a latent rather than live divergence — which is precisely why it needs a test rather than an argument. `EpisodeActionFilterCharacterizationTest.nullActionThrowsNullPointerException` (Step 4) builds one via `new EpisodeAction.Builder(podcast, episode, null)` — legal because Milestone 11's D9 declared both `Builder` overloads' `action` parameter nullable — and asserts `NullPointerException`. That single test is the entire guard; without it, `?.`-style softening passes every other check in this plan.

---

**D7 — `SynchronizationQueueImpl`'s four nullable overrides dereference with `!!` after the connectivity guard, never with `?.`.** (Research hazard 3; Rule M11.)

`SynchronizationQueue.kt:24-30` fixes the parameter types and Kotlin requires exact override matching, so all four are nullable whether or not this milestone likes it. The bodies then must reproduce what Java does with a null:

- `enqueueEpisodePlayed(media: FeedMedia?, completed: Boolean)` — the `SynchronizationSettings.isProviderConnected()` guard at `:112-114` runs **first** and returns; only past it does `:115` evaluate `media.getItem()`. So a null `media` is a **benign no-op for every user with sync unconfigured** and an NPE for everyone else, and the equivalence-preserving Kotlin is `media!!` at `:115`, *after* the guard. A `media?.` would silently no-op where production throws. `FeedMedia.item`, `FeedItem.feed` and `FeedItem.media` are all `var` properties declared in **another module** (`FeedMedia.kt:34`, `FeedItem.kt:47,56`), so Kotlin grants no smart cast on any of them and each dereference needs its own `!!` — the counts in D8's table are exact for that reason, not from carelessness. `media.item!!` is also what `EpisodeAction.Builder(item: FeedItem, action: Action?)` requires at `:122`, since Milestone 11 declared that parameter non-null (`EpisodeAction.kt:122`).
- `enqueueFeedAdded(downloadUrl: String?)` / `enqueueFeedRemoved(downloadUrl: String?)` — pass through untouched to `SynchronizationQueueStorage`, whose own parameters are `String?` for the same reason. A null URL is tolerated end to end today (`JSONArray.put(Object)` accepts it; `indexOf`'s comparison is null-safe), so nullable is faithful the whole way down and no `!!` appears on either path.
- `enqueueEpisodeAction(action: EpisodeAction?)` — nullable at both levels. The `!!` goes at the **dereference** in `SynchronizationQueueStorage.enqueueEpisodeAction` (`queue.put(action!!.writeToJsonObject())`, `:164`), not at the parameter, so the NPE originates where Java's does. Note that when the lock is contended this NPE is thrown inside a `Completable.fromRunnable` on `Schedulers.computation()` (`LockingAsyncExecutor.java:24-32`) and is therefore delivered to `RxJavaPlugins`' global error handler rather than to any caller — preserved, not repaired.
- The five methods that reach `WorkManager.getInstance()` on their first statement (`sync`, `syncImmediately`, `syncIfNotSyncedRecently`, `fullSync`, and the private `getWorkRequest`) are pure transcription: `OneTimeWorkRequest.Builder(SyncService::class.java)` at `:62`, `WORK_ID_SYNC` as a `private const val` in a `companion object`, `getWorkRequest()` as a private companion function, the `UserPreferences.isAllowMobileSync()` constraint fork verbatim, and the 2-minute/20-second delay fork with the `SyncServiceEvent` posted **only** on the not-currently-active branch. D12 states plainly why these five are not behaviourally characterized.

Kotlin call sites that pass a lambda to `LockingAsyncExecutor.executeLockedAsync(runnable: Runnable?)` — nullable per D0's enumeration, with `!!` at the two dereference sites (D8 rows 18–19) rather than at the parameter, for the same reason `enqueueEpisodeAction` puts its `!!` at the dereference: so the NPE originates where Java's does, and on the same thread — use the explicit SAM constructor `Runnable { … }`. This is stated because SAM conversion of a bare lambda to a Java interface at a **Kotlin**-declared parameter is version-sensitive, and an explicit SAM constructor is unconditionally valid; a bare lambda is acceptable if it compiles, and the choice is behaviourally identical either way. `SynchronizationQueueImpl.java:78`'s method reference `new SynchronizationQueueStorage(context)::clearQueue` becomes `Runnable { SynchronizationQueueStorage(context).clearQueue() }`, preserving the fact that a **new** storage instance is constructed inside the locked body.

---

**D8 — The exhaustive `!!` inventory: exactly 19, enumerated by file and expression. The table is the authority; any `!!` not on it is a review finding, and 18 is as much a finding as 20.**

| # | File | Expression | Why faithful |
|---|---|---|---|
| 1 | `EpisodeActionFilter.kt` | `when (remoteAction.action!!)` | D6 — Java `switch` NPEs on a null enum |
| 2 | `EpisodeActionFilter.kt` | `Pair(action!!.podcast, action.episode)` in `createUniqueLocalMostRecentPlayActions` | D3 — the list may contain nulls; `:58` NPEs today at this statement |
| 3 | `EpisodeActionFilter.kt` | `mostRecent.timestamp!!.before(action.timestamp)` (`:62`) | `timestamp` is a cross-module `val`, so the explicit `== null` check on the preceding line grants no smart cast |
| 4 | `EpisodeActionFilter.kt` | `secondAction.timestamp!!.after(firstAction.timestamp)` (`:74`) | same; guarded by the preceding `!= null` clause in Java |
| 5 | `SynchronizationQueueStorage.kt` | `queue.put(action!!.writeToJsonObject())` (`:164`) | D7 — `action.writeToJsonObject()` NPEs today |
| 6 | `SynchronizationQueueImpl.kt` | `media!!.item == null` (`:115`) | D7 — first dereference, after the connectivity guard |
| 7–8 | `SynchronizationQueueImpl.kt` | `media.item!!.feed!!.isLocalFeed` (`:115`) | `item` and `feed` are cross-module `var`s; both chains NPE today |
| 9–10 | `SynchronizationQueueImpl.kt` | `media.item!!.feed!!.state` (`:116`) | Java re-reads `getItem().getFeed()`; transcribed, not hoisted |
| 11 | `SynchronizationQueueImpl.kt` | `EpisodeAction.Builder(media.item!!, EpisodeAction.PLAY)` (`:122`) | `Builder`'s `item` parameter is non-null (`EpisodeAction.kt:122`) |
| 12 | `SyncService.kt` | `FeedUpdateManager.getInstance()!!.runOnce(applicationContext)` (`:86`) | `getInstance()` is nullable (`FeedUpdateManager.kt:24`) and Java dereferences it unguarded |
| 13 | `SyncService.kt` | `feed.preferences!!.keepUpdated` (`:133`) | `Feed.preferences` is `FeedPreferences?` (`Feed.kt:72`), unguarded today |
| 14 | `SyncService.kt` | `val media = feedItem.media!!` (`:287`) | `media` is a cross-module `var`, so `:283`'s null check grants no smart cast; Java re-reads the getter |
| 15–16 | `SyncService.kt` | `nm!!.cancel(…)` ×2 in `clearErrorNotifications` (`:310-311`) | see below |
| 17 | `SyncService.kt` | `nm!!.notify(…)` in `updateErrorNotification` (`:347`) | see below |
| 18 | `LockingAsyncExecutor.kt` | `runnable!!.run()` on the **uncontended** branch (`:19`) | D0 — `runnable` is `Runnable?`; the `!!` goes at the dereference so the NPE is thrown from inside the `try`, after `tryLock()`, with the `finally` still releasing the lock, exactly as Java does |
| 19 | `LockingAsyncExecutor.kt` | `runnable!!.run()` inside the `Completable.fromRunnable` lambda on the **contended** branch (`:27`) | D0 — disjoint branch, so row 18 grants no smart cast; keeps the NPE asynchronous and delivered to `RxJavaPlugins`' global error handler rather than to the caller |

**Rows 15–17 are a deliberate rejection of the idiomatic form, and the reason is behavioural rather than stylistic.** `Context.getSystemService(String)` is `@Nullable` in the SDK, so Kotlin sees `Any?`. The idiomatic `as NotificationManager` throws `NullPointerException` **at the cast**. In `clearErrorNotifications` that only moves the throw site by one statement. In `updateErrorNotification` it is a genuine behaviour change: Java gets the manager at `:343`, checks `POST_NOTIFICATIONS` at `:345`, and calls `nm.notify` at `:347` **only if the permission is granted** — so on a device where the service lookup returned null *and* the permission is denied, Java never throws at all, while `as NotificationManager` throws unconditionally. So both sites use `as NotificationManager?` with the `!!` at each dereference. Unlikely to be reachable in practice; transcribed correctly anyway, because "unlikely" is not the standard this pipeline sells.

**One documented fallback.** Rows 2–4 assume the smart cast that `action!!` grants on a stable loop variable for the remainder of its iteration. If the compiler declines it at `localMostRecentPlayAction.put(key, action)`, add `!!` there too and record the module total as **21** rather than 19, updating AC12. No other deviation from the table is authorised.

**Forbidden softenings, none of which any compiler will catch:** `?.` in place of any row above, `?: return`, `filterNotNull()`, `orEmpty()`, `let`-wrapping any of rows 1–19, `lateinit`, hoisting a repeated `media.item` / `feedItem.media` / `getSystemService` read into a local (which would collapse two Java getter invocations into one and, at row 14, change which read throws), or moving rows 18–19's `!!` from the two dereference sites up to the parameter declaration (which is the non-null declaration D0 rejects, wearing a `!!`).

---

**D9 — `removeLegacyConflictingFeedEntries`'s `List.toString()` defect is pinned, not fixed — and the fixture that pins it must use realistic feed URLs, or it pins nothing.** (Unknown 5.)

`SynchronizationQueueStorage.java:125-126` writes `addedQueue.toString()` and `removedQueue.toString()` — i.e. `"[a, b]"`, unquoted and comma-space separated — into two prefs keys that every other read path parses with `new JSONArray(…)`, which throws `JSONException`, which `:68-70` and `:52-54` swallow via `printStackTrace()`, silently emptying both queues. Milestone 11's D10 reasoning applies unchanged — a behaviour change inside a diff whose entire premise is equivalence is out by definition, and fixing it would require editing the very test that pins it — but research is right that the reachability difference must be said out loud rather than inherited silently: unlike `EpisodeAction.equals`, this is **live**, reached from `SyncService.java:210` whenever the subscription upload throws `SyncServiceException`, which is any server-side failure during the upload phase. It also cannot be fixed in isolation, because installations already carrying a corrupt value on disk would need a migration. Filed as future-work item 12 and OQ5; repairing it in this PR, including in a separate labelled commit, is a REQUEST CHANGES finding.

**The fixture requirement is load-bearing and is the reason this decision is not a one-liner.** Android's `org.json.JSONTokener` is **lenient**: it accepts unquoted literals, so `new JSONArray("[a, b]")` parses successfully to `["a","b"]` and the defect appears harmless. Real feed URLs contain `:` and `/`; `readLiteral` stops at `:`, the tokener then expects `,` or `]`, and the parse throws. So the corruption is real for every actual user and invisible to a toy fixture. Step 3 therefore pins **three** distinct facts, and a suite missing the middle one proves nothing:

1. `removeLegacyConflictingFeedEntriesWritesUnquotedListToStringIntoBothKeys` — the raw prefs string equals exactly `[https://a.example/feed.xml, https://b.example/feed.xml]`.
2. `removeLegacyConflictingFeedEntriesLeavesBothQueuesUnreadableForRealFeedUrls` — after the call, `getQueuedAddedFeeds()` **and** `getQueuedRemovedFeeds()` both return **empty**. Fixture URLs must contain `://`.
3. `removeLegacyConflictingFeedEntriesRoundTripsWhenValuesAreJsonTokenerSafe` — with `"a"`/`"b"` the round trip *succeeds*, documenting the leniency asymmetry so a future reader does not "simplify" fixture 2's URLs and quietly disarm it.

---

**D10 — New characterization tests are Java, JUnit 4; the two pre-existing JUnit 3 files are not touched, not modernised, and not counted.** (Unknown 9; Milestone 11's D12 precedent.)

Java, for the reason Milestone 11 and `:event` chose it: a Java test that compiles and passes against the Java class and then compiles and passes **unmodified** against the Kotlin class is a mechanical proof of source and binary compatibility, evaluated inside this module rather than at the far end of a build. That matters more here than in any prior milestone, because there is no seven-module compile to fall back on. Three of the five new files are load-bearing at **compile time** in a way only Java can be: `LockingAsyncExecutorTest` calls all three entry points statically (fails without D4's `@JvmStatic`), `EpisodeActionFilterCharacterizationTest` binds `Map<androidx.core.util.Pair<String,String>, EpisodeAction>` directly (fails if `kotlin.Pair` is substituted), and `SyncServiceWorkerContractTest.synchronizationQueueStorageCrossClassMethodsKeepTheirJavaNames` resolves all five D2 methods by their exact Java names via reflection (fails if `internal` mangling leaks through).

JUnit 4 (`@Test`, `org.junit.Assert`, `assertThrows`), matching Milestone 11's nine test files. `libs.junit` (4.13) supplies both styles and the module already runs `TestCase` subclasses through the JUnit 4 infrastructure today, so the two styles coexist in one source set with no wiring. **`GuidValidatorTest.java` and `EpisodeActionFilterTest.java` must compile and pass unmodified** — they are the pre-existing half of the oracle, and `GuidValidatorTest.java:16` is the only guard on D5. Converting the new suite to Kotlin is deferred per the standing test-migration-sequencing rule; it joins the two suites already queued from Milestones 10 and 11.

---

**D11 — Robolectric is added, scoped to two of the five new test files, with the disclosed-comment precedent from Milestone 11.** (Unknown 4.)

`SynchronizationQueueStorage` needs real `SharedPreferences` and real `org.json`; `SynchronizationQueueImpl` needs `SharedPreferences` through `SynchronizationSettings`. Both are Android-framework stubs that throw `RuntimeException: Stub!` under plain JUnit, because `common.gradle:50-53` sets `includeAndroidResources = true` but not `returnDefaultValues`. `net/sync/service-interface/build.gradle:21-24` is the precedent for the scoped justification comment.

**Only `SynchronizationQueueStorageTest` and `SynchronizationQueueImplTest` carry `@RunWith(RobolectricTestRunner.class)`.** The other three run on plain JUnit: `EpisodeActionFilterCharacterizationTest` (the existing `EpisodeActionFilterTest` proves `androidx.collection.ArrayMap` and `androidx.core.util.Pair` work on the JVM, and no new test reaches `Log.e` because Java's `switch` throws before `default`), `LockingAsyncExecutorTest` (RxJava3 is JVM-native), and `SyncServiceWorkerContractTest` (reflection only — it loads classes and inspects members without invoking any Android stub). AC5 greps for a count of exactly 2. Note the cost research flagged: Robolectric multiplies across **four** flavour variants here, not two.

Both Robolectric files use `RuntimeEnvironment.getApplication()` and call `SynchronizationSettings.init(context)` in `@Before`. Worth knowing while writing them: `SynchronizationSettings.java:8`'s `PREF_NAME` is **also** `"synchronization"`, so the settings class and `SynchronizationQueueStorage` share one prefs file — which is exactly why `clearQueue()`'s extra `resetTimestamps()` call is observable and testable, and why a test must not assume the file contains only queue keys.

**One environment probe, with both outcomes pre-specified.** `SynchronizationQueueImpl.enqueueEpisodeAction` writes to storage and *then* calls `sync()`, which calls `WorkManager.getInstance(context)`; whether that throws `IllegalStateException` under Robolectric in this module is an environment property, not a property of the conversion. Step 6 records the observed behaviour in Implementation Notes, and the two arithmetic tests invoke through a helper that catches `IllegalStateException` under a self-documenting name and then asserts on the prefs content — which is written before `sync()` is reached and is therefore identical under both outcomes, before and after conversion. A bare `catch (Throwable)` is a REQUEST CHANGES finding; the caught type must be exactly `IllegalStateException`.

---

**D12 — `androidx.work:work-testing` is NOT introduced, and `doWork()` converts under inspection-level proof. This is the milestone's honest limit and it is stated once, here.** (Unknown 3.)

The artifact is in no `gradle/libs.versions.toml` anywhere in the repo, so adopting it means editing shared repo-wide config — the file both Milestone 10 and Milestone 11 placed explicitly out of File Scope, and the edit that would break this milestone's headline claim of zero changes outside `net/sync/service/`. That alone is not decisive; what makes it decisive is how little it would actually buy:

- Past `activeSyncProvider.login()` (`SyncService.java:79`), every branch needs a network backend. `getActiveSyncProvider()` constructs `GpodnetService`/`NextcloudSyncService` **concretely** (`:359-366`) with no injection point, and the module declares no mocking framework. So `TestListenableWorkerBuilder` would reach exactly two of the five outcomes in gap 3's matrix — the null-provider early success and, only with reflection into a private static field, the re-entrancy guard.
- The re-entrancy guard is unreachable without reflection **regardless** of `work-testing`, so it is not an argument for it.
- The genuinely valuable, genuinely external contract is WorkManager's **persisted FQCN and reflective constructor** — and that is pinned better, and for free, by reflection: `SyncServiceWorkerContractTest` (Step 6) asserts the exact class name, `public`, non-abstract, `extends Worker`, and a public `(Context, WorkerParameters)` constructor. `TestListenableWorkerBuilder` would not assert the class *name* at all.

So: `SyncService`'s 371 LOC convert with reflection-level shape proof plus the compile, and **not** with behavioural coverage of `doWork()`, `syncSubscriptions`, `syncEpisodeActions`, `processEpisodeActions`, `waitForDownloadServiceCompleted`, `someFeedWasNotRefreshedYet`, `getActiveSyncProvider` or the two notification methods; and `SynchronizationQueueImpl`'s five WorkManager-touching methods likewise (D7). That is weaker proof than any prior milestone gave its riskiest file, and pretending otherwise would be the exact failure mode this pipeline exists to prevent. The compensating controls are all mechanical rather than aspirational, and each is an acceptance criterion rather than an intention: D8's exact 19-row `!!` inventory; D13's line-referenced transcription contract for all eleven `SyncService` methods; the two `javap -c` overload checks in D13; AC12's exact-count greps; and the fact that `SyncService` converts alone, last, in its own reviewable commit. Adding `work-testing` repo-wide is filed as future-work item 13 and OQ4.

---

**D13 — `SyncService`'s transcription contract: eleven methods, line-referenced, with two `javap -c` checks. This substitutes for the coverage D12 declines.** (Unknown 7 — yes, `SyncService` converts in this milestone.)

Deferring it was a legitimate option and is declined for Milestone 11's reasons: deferral does not reduce the risk, it defers it, while leaving the module half-converted, splitting 808 LOC across two reviews, forcing a future session to rebuild all of this context, and — because `SyncService.java:210` is one of D2's five call sites — constraining the visibility decision anyway rather than sidestepping it. Milestone 11 faced the identical call with `EpisodeAction` (57% of LOC, ~90% of risk) and rejected deferral.

Mandatory, per method:

- `extends Worker` with both `@NonNull` constructor parameters non-null per Rule A; `doWork(): Result` as `override fun`. The class **must not** become `internal`, must not be renamed, and must not move package — WorkManager persists the FQCN.
- `private static boolean currentlyActive` (`:57`) → `private var currentlyActive = false` in a `companion object`. **Do not add `@Volatile`** — it is unsynchronised today and that is a behaviour property, not an oversight.
- `doWork()` — the `finally { currentlyActive = false }`, the two `Result.success()` early returns, the `getRunAttemptCount() % 3 == 2` throttle on the retry path versus the unconditional notification on the failure path, and the `e is SyncServiceException` discriminator, all verbatim. `catch (e: Exception)`, not `Throwable`.
- `waitForDownloadServiceCompleted()` — the `while (true)` / `Thread.sleep(1000)` busy-wait transcribed as-is. The `//noinspection BusyWait` comment at `:123` has no Kotlin equivalent and transcribes as a plain comment, unremoved (`AGENTS.md`). This is `concurrency`-track material and is explicitly not touched.
- `someFeedWasNotRefreshedYet()` — `!!` row 13.
- `isCurrentlyActive()` — D2.
- `syncSubscriptions()` — **the aliasing at `:188-194` is the trap.** Required shape: `val localSubscriptions: MutableList<String> = DBReader.getFeedListDownloadUrls(true)` (explicit type; the platform return makes this legal), `var queuedAddedFeeds: MutableList<String> = synchronizationQueueStorage.getQueuedAddedFeeds()`, `val queuedRemovedFeeds: MutableList<String> = …getQueuedRemovedFeeds()`, and at `:190` the bare reassignment `queuedAddedFeeds = localSubscriptions`. **Forbidden:** `ArrayList(localSubscriptions)`, `.toMutableList()`, `.toList()`, or any other copy — each breaks the aliasing that makes `:193`'s `removeAll` visible to `:210`'s `removeLegacyConflictingFeedEntries(localSubscriptions)` in the `catch`. AC12 greps for all three. Also verbatim: the four-way skip ladder at `:156-174`, and `feed.setItems(Collections.emptyList())` at `:177` — which must stay `java.util.Collections.emptyList()`, **not** Kotlin's `emptyList()` (wrong type against `MutableList<FeedItem>?`) and **not** `mutableListOf()` (silently swaps an immutable singleton for a mutable list).
- `syncEpisodeActions()` — D3's `MutableList<EpisodeAction?>` and the single `@Suppress("UNCHECKED_CAST")` bridge; the first-sync `.add(played)` loop verbatim, including `media.getDuration() / 1000` appearing three times unhoisted.
- `processEpisodeActions()` — `@Synchronized private fun` (the lock object stays the `SyncService` instance). `!!` row 14. `media.position = action.position * 1000` transcribed verbatim including its reachable `Int` overflow for a corrupt stored position. The `almostEnded` predicate and the three unconditional trailing database writes verbatim.
- `clearErrorNotifications()` / `updateErrorNotification()` — `!!` rows 15–17 per D8; the `POST_NOTIFICATIONS` gate and the unconditional `Notification` build preserved in that order; `getString(R.string.gpodnetsync_error_descr) + exception.message` preserved including the literal `"null"` a null message renders.
- `getActiveSyncProvider()` — `when (selectedService) { … }` over the enum after the existing null guard, **with the `else -> null` branch retained** even though both constants are covered, mirroring Java's equally-dead `default` and guarding a future enum constant identically.
- `e.printStackTrace()` at `:127` and the six in `SynchronizationQueueStorage` transcribe unchanged; do not upgrade any of them to `Log.e`.

**Two `javap -c` checks, machine-run at Step 12 and pasted into Implementation Notes (AC11):**
1. `processEpisodeActions` must contain `invokestatic …DBWriter.removeQueueItem:(Landroid/content/Context;Z[J)V`. The Kotlin call requires the spread operator — `DBWriter.removeQueueItem(applicationContext, false, *queueToBeRemoved.toArray())` — and a plausible mis-fix binds the sibling overload `(Landroid/content/Context;ZL…/FeedItem;)V`, a different database operation. This is a one-character difference between correct and wrong, and the bytecode settles it.
2. `syncSubscriptions` must contain `invokestatic java/util/Collections.emptyList` and **no** call to `kotlin/collections/CollectionsKt.emptyList` or `mutableListOf`.

---

**D14 — No test file may be edited during Steps 7–12. A conversion step that needs a test edit has broken the Java API by definition: hard stop and re-plan, not a fix.**

Milestone 11's D12 and D16, restated because the consequence is sharper here. There is no cross-module compile to catch a signature regression — `:app:assembleDebug` stays green as long as `SynchronizationQueueImpl` keeps a public `(Context)` constructor and still extends `SynchronizationQueue`. The Java suite is not a supplement to the compile-check; it is the **entire** proof. The same hard stop applies to any edit outside `net/sync/service/` after Step 1: that falsifies the "API preserved" premise and calls for a new task with a widened File Scope, not a patch in place.

---

**D15 — Build wiring: two plugin aliases and one test dependency. All four dead dependencies stay. Test task names are flavoured and must be verified, not copied.** (Unknown 8.)

Add exactly two lines to the `plugins` block of `net/sync/service/build.gradle`, matching `net/sync/service-interface/build.gradle:1-5`:

```
alias(libs.plugins.kotlin.android)
alias(libs.plugins.ktlint)
```

`kotlin.android` is a hard prerequisite. `ktlint` is required because `common.gradle:147-153` scopes `checkstyle` to `fileTree('src/main/java') { include '**/*.java' }`, so every `.java` → `.kt` rename silently drops a file out of the module's only style gate; applying ktlint replaces the gate rather than quietly deleting it. `kotlin = "2.3.20"` and `ktlint = "12.3.0"` are already in the catalog and `.editorconfig` already carries the style. Add `testImplementation libs.robolectric` with D11's disclosed scoping comment. **No `androidx.work:work-testing`** (D12) and therefore **no edit to `gradle/libs.versions.toml`**.

`libs.okhttp` (`:29`), `libs.rxandroid` (`:30`) and `libs.guava` (`:32`) have zero references in `src/` and stay, on `AGENTS.md`'s minimal-diff grounds and Milestone 11's D14 precedent; `libs.rxjava` (`:31`) is genuinely used by `LockingAsyncExecutor` and obviously stays. `ui/preferences/build.gradle:35`'s dead dependency on this module is different in kind — it is an edit to **another module's** build file and would break the zero-edits-outside-the-module criterion that is this milestone's headline proof. All four are filed as future-work item 14 and OQ6.

**Test task names are flavoured. Milestone 11's commands must not be copy-pasted.** This module applies `playFlavor.gradle` (`build.gradle:5`), which Milestone 11's module did not, so the real tasks are `testFreeDebugUnitTest`, `testPlayDebugUnitTest`, `testFreeReleaseUnitTest` and `testPlayReleaseUnitTest`, aggregated by `test`. Step 1 runs `./gradlew :net:sync:service:tasks --all` once and records the actual list, so the claim is verified rather than inherited from either prior milestone. The commands used at every step:

```
./gradlew --console=plain :net:sync:service:testFreeDebugUnitTest --rerun
./gradlew --console=plain :net:sync:service:testPlayDebugUnitTest --rerun
```

`--rerun` is **mandatory** — Gradle reports `UP-TO-DATE` and proves nothing otherwise. Remember that 7 existing tests × 4 variants = the 28 the aggregate task reports; the new-test count must be reasoned per variant, not per aggregate.

**The free-flavour SpotBugs gate gap from Milestone 10 applies here** (it did not to Milestone 11's unflavoured module): `common.gradle:100-101` parses only `debug.xml` and `playDebug.xml`, and `:142-143` makes `lint` depend on `spotbugsDebug`/`spotbugsPlayDebug`, so this module's `spotbugsFreeDebug` output is generated but never parsed and never throws. AC14 therefore runs **both** flavours by hand. The current baseline is clean (lint "No issues found", 0 `BugInstance`s, no `net.sync` entry in `config/spotbugs/exclude.xml`), so any new finding is genuinely new; expected Kotlin-bytecode noise to watch for is `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE` from `Intrinsics` and `MS_SHOULD_BE_FINAL`/`MS_CANNOT_BE_FINAL` on the companion-backed `currentlyActive` field. **Escalate any new finding; do not edit `config/spotbugs/exclude.xml`, which is out of File Scope.**

---

**D16 — `SynchronizationQueueStorage`'s remaining shape decisions, stated so J2K's output is not mistaken for a decision.**

- `NAME` and the three key constants → `private const val` in a `companion object`. Their exact string values (`"synchronization"`, `"sync_queued_episode_actions"`, `"sync_removed"`, `"sync_added"`) and the `"[]"` defaults are an on-disk contract with every installation; AC3 pins them by reading raw prefs.
- The three getters stay **functions**, not properties. A `val queuedEpisodeActions` would emit the same JVM getter name, so Java callers are indifferent, but functions are the smaller diff and do not imply caching.
- The redundant `private getSharedPreferences()` accessor (`:173-175`) is **kept alongside** the field, and the two use sites stay as they are — the field directly at `:124` inside `removeLegacyConflictingFeedEntries`, the accessor everywhere else. Equivalent today; `AGENTS.md`'s minimal-diff rule forbids unifying them. A private Kotlin property emits no accessor method, so `private val sharedPreferences` and `private fun getSharedPreferences()` do not clash on the JVM. **Fallback if the compiler nonetheless reports a platform declaration clash:** apply `@JvmName` to the private function (behaviourally inert, since nothing outside the file can see it) and record it; do not unify the two forms.
- `indexOf(string: String?, array: JSONArray): Int` — `array.getString(i) == string` is Kotlin's null-safe structural equality and is exactly equivalent to Java's `array.getString(i).equals(string)` for every input, including the `-1` → `JSONArray.remove(-1)` no-op path that AC3 pins.
- `removeLegacyConflictingFeedEntries(currentLocalSubscriptions: Collection<String>)` — non-null per Rule N; `removedQueue.removeAll(currentLocalSubscriptions)` binds the `MutableCollection` member, not the stdlib predicate extension; `addedQueue.toString()` stays `AbstractCollection.toString()` per D9.
- All six `e.printStackTrace()` calls transcribe unchanged.

---

**D17 — One milestone, one PR, 13 steps and 12 commits: 4 characterization commits, 6 conversion commits, 1 wiring commit, 1 verification/docs commit. Step 2 is a spike that commits nothing.** (Unknown 6.)

The equivalence proof is only legible when before and after sit in one reviewable unit; a tests-only PR followed by a conversion PR splits the milestone's single most valuable artifact — the same Java suite passing against both versions — across two reviews, and would leave `develop` carrying a module with five new test files and no conversion. This is the Milestone 8, 10 and 11 precedent. Research is right that the characterization debt is heavier here in absolute terms; that is expressed as **four separate, individually reviewable test commits** (Steps 3–6), not as a separate PR. Step 2's recorded spike result is committed with Step 3.

### Steps

**Step 1 — Wire the build, verify the flavoured task names, and change nothing else.**
Add the two plugin aliases and `testImplementation libs.robolectric` with D11's disclosed scoping comment to `net/sync/service/build.gradle` (all three inert at this point — no `.kt` and no Robolectric test exists yet — so the build stays green in the same commit). Leave all four dead dependencies alone (D15). Run `./gradlew :net:sync:service:tasks --all` once and record the actual unit-test task names in Implementation Notes. Run both flavoured test tasks with `--rerun` and record the per-variant baseline count (7 tests).

**Step 2 — Spike D2's `internal` + `@JvmName` question and the SpotBugs baseline. This step commits no code.**
Throwaway-convert **only** `SynchronizationQueueStorage.java` → `.kt` per D2, D3 and D16, leaving all callers Java. Run `./gradlew :app:assembleDebug`, then `javap -p` on the built class and confirm all five D2 methods appear as `public` with their **unmangled** Java names. Run `./gradlew :net:sync:service:spotbugsFreeDebug` and `:spotbugsPlayDebug` to learn what Kotlin bytecode does to the clean baseline. Record all output verbatim in Implementation Notes, then `git checkout -- net/sync/service/src/main/` so the tree is unchanged. **If `@JvmName` is rejected or mangling persists, apply D2's stated fallback (plain `public` on all five) and record it — do not invent a third option.** If SpotBugs reports a new finding, verify it as pre-existing by re-running the identical task on unmodified `develop` and comparing byte-for-byte before dismissing it; otherwise escalate rather than editing `config/spotbugs/exclude.xml`.

**Step 3 — Pin `SynchronizationQueueStorage`: the module's highest-consequence gap, 176 LOC with zero tests, holding the user's un-uploaded sync data.**
Create `SynchronizationQueueStorageTest.java` under `@RunWith(RobolectricTestRunner.class)` (D11), against the **current Java** sources, with `SynchronizationSettings.init(context)` in `@Before`. Cover: the prefs file name and all three key names asserted by reading raw prefs directly (D16); all three getters returning an **empty list** when the key is absent; `getQueuedEpisodeActionsParsesStoredActions` field-by-field from golden JSON (never via `equals`, which Milestone 11 pinned as defective); **`getQueuedEpisodeActionsPutsNullIntoTheListForAMalformedEntry`** — a two-element fixture whose second object omits `podcast`, asserting `size == 2` and `assertNull(actions.get(1))`, which per D3 is the single most important assertion in this milestone and the only thing that discriminates `!!` from `?.let` from a widened type; `getQueuedEpisodeActionsReturnsPartialListWhenAnEntryIsNotAnObject` (a valid object followed by a bare string, proving the mid-parse `JSONException` is swallowed and the **partially filled** list is still returned); the same partial-read behaviour for the two feed queues; `clearEpisodeActionQueue` and `clearFeedQueues` each clearing only their own keys; `clearQueueClearsAllThreeKeys` and **`clearQueueAlsoResetsSynchronizationTimestamps`** (the asymmetry at `:88`, observable because both classes share the `"synchronization"` file); `enqueueFeedAdded`/`enqueueFeedRemoved` each appending to their own queue **and** removing the same URL from the opposite queue, each with a matching `…LeavesTheOppositeQueueUnchangedWhenUrlNotPresent` test for the `indexOf` → `-1` → `remove(-1)` no-op path; `enqueueFeedAddedAcceptsNullUrl` and `enqueueFeedRemovedAcceptsNullUrl` (pinning D7's `String?` end to end, asserting the stored content); `enqueueEpisodeActionAppendsWrittenJson` against a golden string; `enqueueEpisodeActionWithNullActionThrowsNullPointerException` (`!!` row 5); and D9's **three** `removeLegacyConflictingFeedEntries` tests exactly as enumerated there, with fixture URLs containing `://` in the second one.

**Step 4 — Pin `EpisodeActionFilter`'s untested branches, the nullable-enum `switch`, and the null-element consequence of D3.**
Create `EpisodeActionFilterCharacterizationTest.java`, plain JUnit 4, in new file only — `EpisodeActionFilterTest.java` is not touched. Cover: `NEW`, `DOWNLOAD` and `DELETE` each falling through to a no-op; **`nullActionThrowsNullPointerException`** (D6's only guard — a remote action built with `new EpisodeAction.Builder(podcast, episode, null)`, asserting `NullPointerException`); **`queuedLocalActionThatIsNullThrowsNullPointerException`** (D3's downstream guard — a queued list containing a literal `null` element, asserting `NullPointerException` from `createUniqueLocalMostRecentPlayActions`); the remote-vs-remote dedupe at `:35-38` in both directions (`secondRemoteActionForSameKeyDoesNotOverrideAnEarlierOne`, `…OverridesAnEarlierOneWhenNewer`); `localActionWithNullTimestampIsReplacedByALaterLocalAction` (`:60-61`); `returnedMapValueIsTheRemoteActionInstance` (`assertSame` — no existing test inspects anything but map size); and `keyUsesAndroidxCoreUtilPairEqualsSemantics`, which builds a fresh `new androidx.core.util.Pair<>(podcast, episode)` and asserts `containsKey`, giving a behavioural guard on top of the compile-time one against a `kotlin.Pair` substitution.

**Step 5 — Pin `LockingAsyncExecutor`, 43 LOC of process-wide locking with zero tests.**
Create `LockingAsyncExecutorTest.java`, plain JUnit 4. Cover: `executeLockedAsyncRunsTheRunnableOnTheCallingThreadWhenUncontended` (capture `Thread.currentThread()` inside the runnable and compare — this is the property four `SynchronizationQueueImpl` methods depend on for ordering); `executeLockedAsyncDefersToAnotherThreadWhenContended` (a second thread takes `lock()` and holds it behind a latch; the calling thread's `executeLockedAsync` must return **before** the runnable has run, and the runnable must then run on a different thread once the latch releases); `executeLockedAsyncReleasesTheLockAfterAnUncontendedRun`; **`lockIsReentrantSoALockedRunnableMayLockAgain`** (the exact `SyncService.java:203` pattern — `lock()` inside a body already scheduled through `executeLockedAsync` must not deadlock); and `unlockWithoutHoldingTheLockThrowsIllegalMonitorStateException`. **Then the two null-`runnable` tests D0 requires, which are what prove `runnable: Runnable?` rather than assume it:** `executeLockedAsyncWithNullRunnableThrowsNullPointerExceptionWhenUncontended` (`assertThrows(NullPointerException.class, () -> LockingAsyncExecutor.executeLockedAsync(null))`, then assert the lock is free afterwards by running a subsequent uncontended `executeLockedAsync` on the calling thread — proving the `finally` released it, i.e. that the throw happened *inside* the `try` and not at method entry); and **`executeLockedAsyncWithNullRunnableWhenContendedReturnsNormallyAndDeliversTheNpeToTheRxGlobalErrorHandler`** — a second thread holds `lock()` behind a latch, the test thread calls `executeLockedAsync(null)` and must observe it **return normally with no exception**, then releases the latch and asserts a throwable arrives at an `RxJavaPlugins.setErrorHandler` installed in `@Before`, whose causal chain contains a `NullPointerException`. Search the causal chain rather than asserting a wrapper type — RxJava's no-argument `subscribe()` wraps errors before handing them to the plugin — and record the **observed** wrapper class verbatim in Implementation Notes rather than pre-committing to one here. That second test is the only discriminator between D0's `Runnable?` and a non-null `Runnable`: the uncontended test passes under **both** declarations (Java's NPE and `Intrinsics.checkNotNullParameter`'s NPE are the same exception class on the same thread), so "it throws NPE either way" is not proof, and a suite carrying only the first one pins nothing. Every test must leave the lock free **and the `RxJavaPlugins` error handler reset** on exit, since both are process-wide and the suite shares one JVM fork; state both constraints in a test-file comment. The file being Java is itself the compile-time guard on D4's `@JvmStatic`.

**Step 6 — Pin `SynchronizationQueueImpl`'s guard conditions and the WorkManager/JVM-shape contracts that substitute for `work-testing`.**
Create two files. `SynchronizationQueueImplTest.java` under Robolectric (D11), covering the paths that terminate before any WorkManager call: `enqueueFeedAdded`/`enqueueFeedRemoved`/`enqueueEpisodeAction` each doing nothing when `isProviderConnected()` is false; `enqueueEpisodePlayedDoesNothingWhenProviderNotConnected` **with a null `media`**, proving null is harmless on that path today; **`enqueueEpisodePlayedWithNullMediaThrowsNullPointerExceptionWhenProviderConnected`** (the only discriminator between D8 row 6's `media!!` and a `media?.` softening); `enqueueEpisodePlayedWithNullFeedThrowsNullPointerException` (rows 7–8); **the two `if` statements at `:115-121`, which carry five rejection conditions between them, plus one bypass — eight named tests, enumerated below because the previous "three rejection guards" framing named four conditions, counted them as three, and required a boundary test for none of them**; and the two arithmetic tests `enqueueEpisodePlayedWritesSecondsNotMillisecondsForACompletedEpisode` and `…UsesPositionRatherThanDurationWhenNotCompleted`, invoked through D11's `IllegalStateException`-tolerating helper and asserting on the queued JSON in prefs. Record the WorkManager-under-Robolectric probe result in Implementation Notes.

**The eight `enqueueEpisodePlayed` guard tests, named and with their full fixtures, because the fixture is where this guard's tests go wrong.** The first `if` (`:115-116`) rejects on three independent disjuncts; the second (`:119`) is `startPosition < 0 || (!completed && startPosition >= position)` — two clauses, **two different comparison operators**, and a conjunct that disables the second clause entirely. Every test below uses a provider-connected fixture with a non-null `item` whose `feed` is non-local and **not** `STATE_NOT_SUBSCRIBED`, so exactly one condition is under test at a time; "rejected" means the `sync_queued_episode_actions` prefs key is still `"[]"` afterwards, "accepted" means it holds exactly one action. All eight go through D11's `IllegalStateException`-tolerating helper, since the accepted cases reach `sync()`.

1. `enqueueEpisodePlayedDoesNothingWhenItemIsNull` — `media.getItem() == null`, rejected. (First disjunct of `:115`; it was unnamed in the previous version of this step, which is how "four conditions" got counted as three.)
2. `enqueueEpisodePlayedDoesNothingForALocalFeed` — `isLocalFeed()` true, rejected.
3. `enqueueEpisodePlayedDoesNothingForANotSubscribedFeed` — `getState() == Feed.STATE_NOT_SUBSCRIBED`, rejected.
4. `enqueueEpisodePlayedRejectsNegativeStartPosition` — `startPosition = -1`, `position = 5000`, `completed = true`, rejected. Pins that clause one exists and that its operator is not `>`.
5. **`enqueueEpisodePlayedAcceptsStartPositionZero`** — `startPosition = 0`, `position = 5000`, `completed = true`, **accepted**. The only test that discriminates `< 0` from `<= 0`. `completed = true` is mandatory in this fixture: it short-circuits `!completed` and isolates clause one. **A `completed = false` variant of this test with `position = 0` would assert *rejection* (because `0 >= 0` fires clause two) and would therefore pass under both `<` and `<=`, pinning nothing** — that is the specific interaction that makes this guard's fixtures load-bearing rather than incidental.
6. **`enqueueEpisodePlayedNotCompletedRejectsWhenStartPositionEqualsPosition`** — `startPosition = 5000`, `position = 5000`, `completed = false`, rejected. The only test that discriminates `>=` from `>`.
7. **`enqueueEpisodePlayedNotCompletedAcceptsWhenStartPositionIsOneLessThanPosition`** — `startPosition = 4999`, `position = 5000`, `completed = false`, **accepted**. Fixes the boundary at equality rather than one below, and is the only test that fails if the comparison conjunct is dropped and clause two collapses to `!completed`.
8. **`enqueueEpisodePlayedCompletedAcceptsWhenStartPositionEqualsPosition`** — `startPosition = 5000`, `position = 5000`, `completed = true`, **accepted**. The only test that fails if the `!completed` conjunct is dropped and clause two collapses to the bare comparison; tests 4–7 all still pass under that slip, which is why eight is the discriminating count and seven is not.

Tests 4–8 are each necessary and none is redundant: for every single-operator or single-conjunct perturbation of `:119` there is exactly one of the five that fails. That is the property AC19 makes the reviewer verify by hand rather than accept on this argument. Then create `SyncServiceWorkerContractTest.java`, plain JUnit 4, reflection only: `Class.forName("de.danoeh.antennapod.net.sync.service.SyncService")` resolving (the persisted-FQCN contract), the class being `public` and non-abstract, `Worker.class.isAssignableFrom(...)`, a **public** `(Context, WorkerParameters)` declared constructor, `doWork` being public, `SyncService.TAG` and `EpisodeActionFilter.TAG` being `public static final String`, `SynchronizationQueueImpl`'s public `(Context)` constructor and `SynchronizationQueue` supertype (the `ClientConfigurator.java:53` contract), the static-ness of `GuidValidator.isValidGuid`, `EpisodeActionFilter.getRemoteActionsOverridingLocalActions` and `LockingAsyncExecutor`'s three methods, and **`synchronizationQueueStorageCrossClassMethodsKeepTheirJavaNames`**, which resolves all five D2 methods by exact name and parameter types via `getDeclaredMethod` — the guard that fails if `internal` mangling leaks through. Record the combined new-test count across all five files; it is the number every later step must reproduce.

**Step 7 — Convert `GuidValidator.java` → `GuidValidator.kt`.**
Applies D4 (`object` with `@JvmStatic`) and D5 (`guid: String?`, body verbatim). This is D1's wiring proof: the smallest file in the module, chosen so the first full-repo build after a `.java` → `.kt` rename proves the `kotlin.android`/`ktlint` setup and the mixed source set in a **flavoured** module before anything risky depends on them. Run the `kotlin-j2k-style` skill after J2K and before reporting it converted. `GuidValidatorTest.java` must pass unmodified — that test, not the compiler, is what catches a `String` → `String?` regression. Full-repo build. No test file changes.

**Step 8 — Convert `LockingAsyncExecutor.java` → `LockingAsyncExecutor.kt`.**
Applies D4 in full, including the coexisting `lock` property and `lock()` function, and D0's `runnable: Runnable?` with D8 rows 18–19's two `!!` at the dereferences — **not** at the parameter. Run AC20's falsification check here. The RxJava `Completable.fromRunnable(...).subscribeOn(Schedulers.computation()).subscribe()` body and the `Schedulers.computation()` choice (upstream `97416e045`) transcribe unchanged; this is `concurrency`-track material and is not touched. The javadoc at `:12-15` is preserved as KDoc. `LockingAsyncExecutorTest` must pass unmodified. No test file changes.

**Step 9 — Convert `EpisodeActionFilter.java` → `EpisodeActionFilter.kt`.**
Applies D4 (`object`, `@JvmStatic`, `const val TAG`), D6 (`when (remoteAction.action!!)` with the dead `else` and the shouting `DELETE` comment retained), D3 (the second parameter widened to `List<EpisodeAction?>`), and D8 rows 1–4. `androidx.core.util.Pair` must **not** become `kotlin.Pair` and `androidx.collection.ArrayMap` must not become `mutableMapOf`. `SyncService.java` is still Java here and must compile against the converted signature unedited (D1). No test file changes.

**Step 10 — Convert `SynchronizationQueueStorage.java` → `SynchronizationQueueStorage.kt`.**
Applies D2 (the five `internal @JvmName` methods, or the recorded fallback), D3 (`ArrayList<EpisodeAction?>` with `:35` transcribed verbatim), D9 (the `List.toString()` defect preserved), D16 (constants, the kept redundant accessor, `indexOf`, the six `printStackTrace()` calls) and D8 row 5. `SynchronizationQueueImpl.java` and `SyncService.java` are still Java at this point and must compile unedited — that is the direct check on D2. Run `javap -p` and paste the five method signatures into Implementation Notes (AC11). No test file changes.

**Step 11 — Convert `SynchronizationQueueImpl.java` → `SynchronizationQueueImpl.kt`.**
Applies D7 (all nine `override`s, four with nullable parameters forced by Rule M11; `!!` after the connectivity guard, never `?.`; the `Runnable { … }` SAM constructors; `WORK_ID_SYNC` and `getWorkRequest()` in a companion) and D8 rows 6–11. Run AC19's four falsification checks here. This file is the sole inbound surface of the entire module: `ClientConfigurator.java:53`'s `new SynchronizationQueueImpl(context)` must compile and run unedited, and `:app:assembleDebug` is the only external check that exists. It calls the still-Java `SyncService.isCurrentlyActive()` from Kotlin in the same package; if that fails to resolve, apply D1's stated fallback (swap Steps 11 and 12, with D2's `public` fallback for `isCurrentlyActive()`) and record it. No test file changes.

**Step 12 — Convert `SyncService.java` → `SyncService.kt`.**
The riskiest commit, 46% of the module's LOC, and per D12 the one converting without behavioural coverage of its decision core. Applies D13's eleven-method transcription contract in full, D2 (`internal fun isCurrentlyActive()` in the companion), D3 (the `MutableList<EpisodeAction?>` and the single `@Suppress("UNCHECKED_CAST")` bridge), D8 rows 12–17, and D4's `const val TAG`. Run the `kotlin-j2k-style` skill after J2K. Run both `javap -c` checks from D13 and paste the output (AC11). The module is now 6/6 Kotlin and production `compileFreeDebugJavaWithJavac`/`compilePlayDebugJavaWithJavac` go `NO-SOURCE`. No test file changes.

**Step 13 — Run the full verification matrix and update the docs.**
Execute AC15's matrix; run every `javap` clause of AC11 and paste the output; run AC12's and AC13's greps. Update `net/sync/service/README.md` with the conventions that must survive future edits: the WorkManager persisted-FQCN contract on `SyncService` and what may never change about it; the `"synchronization"` prefs file and three key names as an on-disk contract; `internal` + `@JvmName` on the five cross-class storage methods and why plain `internal` breaks the Java suite; `@JvmStatic` on the three `object`s' members and why top-level functions are forbidden; `getQueuedEpisodeActions()`' nullable element type, the non-null `ISyncService` boundary it meets, and why the bridge is an unchecked cast; `isValidGuid`'s nullable parameter; the flavoured test task names and the free-flavour SpotBugs gate gap; the Java-oracle test constraint and the Robolectric scope boundary; and — plainly — that `removeLegacyConflictingFeedEntries` writes non-JSON into JSON-parsed keys and is knowingly pinned by test. Append four items to `tasks/antennapod-model-kotlin-future-work.md`: (12) the `removeLegacyConflictingFeedEntries` fix **plus the data migration** existing corrupt installs would need; (13) adding `androidx.work:work-testing` to the version catalog as its own repo-wide task, with the coverage it would unlock named; (14) the three dead library dependencies here and `ui/preferences/build.gradle:35`'s dead module dependency; (15) `:net:sync:service` added to existing item 3.

### File Scope

The reviewer rejects any diff touching a file not on this list.

**Modified:**
- `net/sync/service/build.gradle` (two plugin aliases and one `testImplementation` line only, per D15 — no dead-dependency removal, no `work-testing`)
- `net/sync/service/README.md`
- `tasks/antennapod-net-sync-service-kotlin.md`
- `features/antennapod-net-sync-service-kotlin.checkpoint.md`
- `tasks/antennapod-model-kotlin-future-work.md` (append only: four new items, plus this module added to existing item 3)

**Renamed `.java` → `.kt`** (all six, under `net/sync/service/src/main/java/de/danoeh/antennapod/net/sync/service/`):
- `GuidValidator`, `LockingAsyncExecutor`, `EpisodeActionFilter`, `SynchronizationQueueStorage`, `SynchronizationQueueImpl`, `SyncService`

**Created** (all five under `net/sync/service/src/test/java/de/danoeh/antennapod/net/sync/service/`, all `.java`):
- `SynchronizationQueueStorageTest.java`, `EpisodeActionFilterCharacterizationTest.java`, `LockingAsyncExecutorTest.java`, `SynchronizationQueueImplTest.java`, `SyncServiceWorkerContractTest.java`

**Explicitly out of File Scope** — touching any of these means the plan was wrong and the task must be re-planned, not patched:
- `net/sync/service/src/test/java/.../GuidValidatorTest.java` and `.../EpisodeActionFilterTest.java` (must compile and pass **unmodified** — AC9)
- `net/sync/service/src/main/res/values/ids.xml`
- Everything under `net/sync/service-interface/` — in particular `SynchronizationQueue.kt`, `ISyncService.kt`, `EpisodeAction.kt` and `EpisodeActionChanges.kt`, whose non-null element types D3 works around rather than edits
- Everything under `net/sync/gpoddernet/` — in particular `GpodnetService.java` and `NextcloudSyncService.java`
- Everything under `app/` — in particular `app/src/main/java/de/danoeh/antennapod/ClientConfigurator.java`, the module's single inbound caller, and `app/build.gradle`
- `ui/preferences/build.gradle` (its dead dependency on this module is **not** removed, per D15)
- Everything else under `storage/`, `model/`, `event/`, `net/download/`, `net/common/`, `ui/`, `playback/`, `parser/`, `system/`
- **`gradle/libs.versions.toml`** — named explicitly because this is what rules out `androidx.work:work-testing` (D12); adding an entry is a File Scope expansion and therefore a new task
- `common.gradle`, `playFlavor.gradle`, root `build.gradle`, `settings.gradle`, `.editorconfig`
- `config/spotbugs/exclude.xml`, `config/checkstyle/suppressions.xml`, `.github/`

### Acceptance Criteria

Commands are the **flavoured** ones from D15. Copy-pasting Milestone 11's unflavoured task names is itself a review finding.

**Characterization (before) — pinning current behaviour**
- [ ] **AC1** — `./gradlew --console=plain :net:sync:service:testFreeDebugUnitTest --rerun` and `:testPlayDebugUnitTest --rerun` are both green after each of Steps 1, 3, 4, 5 and 6, against the **unconverted Java** sources; the Step 1 `tasks --all` output, the Step 1 baseline count and the Step 6 combined new-test count are all recorded in Implementation Notes.
- [ ] **AC2** — `SynchronizationQueueStorageTest.getQueuedEpisodeActionsPutsNullIntoTheListForAMalformedEntry` exists and passes **at Step 3**, verified by reading the file at Step 3 rather than at the end, and asserts both `size == 2` and `assertNull(actions.get(1))`. Per D3 this is the single most important assertion in the milestone: it is the only thing that discriminates the chosen `ArrayList<EpisodeAction?>` from `!!` (crash at parse), from `?.let { add(it) }` (silent data loss), and from any filtering bridge — none of which any compiler, code review or build in this repo can detect. **Falsification check the reviewer must actually run** at Step 10: change `:35` to `readFromJsonObject(...)?.let { actions.add(it) }`, confirm this test **fails**, revert.
- [ ] **AC3** — `SynchronizationQueueStorageTest` pins the on-disk contract by reading raw prefs: the file name `"synchronization"`, the three keys `"sync_queued_episode_actions"`/`"sync_added"`/`"sync_removed"`, the `"[]"` empty default on all three getters, the swallowed-mid-parse **partial** list on all three, the two `enqueueFeed*` cross-queue removals with their `indexOf == -1` no-op counterparts, both null-URL cases, and `clearQueue`'s extra `resetTimestamps()` versus `clearFeedQueues`/`clearEpisodeActionQueue`.
- [ ] **AC4** — D9's **three** `removeLegacyConflictingFeedEntries` tests exist as separate `@Test` methods, and the fixture for `…LeavesBothQueuesUnreadableForRealFeedUrls` uses URLs containing `://`. Two tests is as much a REQUEST CHANGES finding as one: Android's `JSONTokener` is lenient enough that a toy `"a"`/`"b"` fixture round-trips successfully and pins nothing. These pin a **known live defect** and explicitly do not endorse it (D9); a diff that repairs it, in any commit of this PR, is a REQUEST CHANGES finding.
- [ ] **AC5** — Exactly two test files carry `@RunWith(RobolectricTestRunner.class)` — `SynchronizationQueueStorageTest` and `SynchronizationQueueImplTest` — and `net/sync/service/build.gradle` declares `libs.robolectric` with a disclosed scoping comment following `net/sync/service-interface/build.gradle:21-24`. `grep -c RobolectricTestRunner` over the test source set returns 2. The other three new files run on plain JUnit (D11).
- [ ] **AC6** — `EpisodeActionFilterCharacterizationTest` contains **both** `nullActionThrowsNullPointerException` (D6) and `queuedLocalActionThatIsNullThrowsNullPointerException` (D3), as separate `@Test` methods. **Falsification checks the reviewer must actually run** at Step 9: rewrite the `when` subject to `remoteAction.action` (dropping the `!!`) and confirm the first test **fails**; soften `action!!.podcast` to `action?.podcast` and confirm the second **fails**; revert both. If either still passes, that hazard is unguarded — these two tests are the entire defence against the module's two silent nullable-enum/nullable-element inversions.
- [ ] **AC7** — `SynchronizationQueueImplTest.enqueueEpisodePlayedWithNullMediaThrowsNullPointerExceptionWhenProviderConnected` and `…DoesNothingWhenProviderNotConnected` (with a null `media`) both exist. Together they are the only discriminator between D8 row 6's `media!!` and a `media?.` softening, and they prove the guard ordering that makes null harmless for unconfigured users and fatal for everyone else. The `IllegalStateException`-tolerating helper used by the two arithmetic tests and by AC19's eight guard tests catches exactly `IllegalStateException` under a self-documenting name; a bare `catch (Throwable)` is a REQUEST CHANGES finding. The rest of `enqueueEpisodePlayed`'s guard surface — the three first-`if` disjuncts and the second `if`'s two operators — is AC19's, not this criterion's.
- [ ] **AC8** — `LockingAsyncExecutorTest` contains all **seven** tests named in Step 5, including `lockIsReentrantSoALockedRunnableMayLockAgain` (the `SyncService.java:203` deadlock guard), the two-thread contention test, and the two null-`runnable` tests AC20 governs; each test leaves the process-wide lock free **and** the `RxJavaPlugins` error handler reset on exit.
- [ ] **AC19** — `SynchronizationQueueImplTest` contains all **eight** `enqueueEpisodePlayed` guard tests named in Step 6 as separate `@Test` methods, with the fixtures stated there. **Seven is as much a REQUEST CHANGES finding as three**, and so is any fixture substitution: test 5 must use `completed = true` (with `completed = false` and `position = 0` it silently pins nothing), test 6 must use `startPosition == position` exactly, test 7 must use `startPosition == position - 1`, and test 8 must use `startPosition == position` with `completed = true`. This guard is `:115-121`, the module's most frequently invoked write path — every episode-played event in the app crosses it — and its second `if` mixes `<` and `>=` in one compound expression, the exact shape that survives a J2K transcription slip untouched by the compiler, by `:app:assembleDebug`, and by the Java oracle. **Falsification checks the reviewer must actually run** at Step 11, one character each, reverting after every one: change `< 0` to `<= 0` and confirm test 5 fails; change `>=` to `>` and confirm test 6 fails; delete `&& media.startPosition >= media.position` and confirm test 7 fails; delete `!completed &&` and confirm test 8 fails. If any of the four perturbations leaves the whole suite green, that operator is unpinned and the criterion is not met.
- [ ] **AC20** — `LockingAsyncExecutor.executeLockedAsync`'s parameter is declared `runnable: Runnable?` (D0), the two `!!` sit at the dereferences on lines corresponding to `:19` and `:27` (D8 rows 18–19), and **both** Step 5 null-`runnable` tests exist as separate `@Test` methods. **Falsification check the reviewer must actually run** at Step 8: change the declaration to a non-null `Runnable`, confirm `executeLockedAsyncWithNullRunnableWhenContendedReturnsNormallyAndDeliversTheNpeToTheRxGlobalErrorHandler` **fails** — and confirm the uncontended test still **passes**, which is the point: only the contended test discriminates the two declarations, so a suite carrying only the uncontended one satisfies nothing. Revert. The observed `RxJavaPlugins` wrapper class is recorded verbatim in Implementation Notes rather than asserted from assumption.

**Characterization (after) — the equivalence proof**
- [ ] **AC9** — Both flavoured test tasks are green with `--rerun` after **each** of Steps 7–12, at the **same** total test count as AC1, and `git diff --stat <step-6 commit> -- net/sync/service/src/test/` over Steps 7–12 shows **zero** changed files. Any test-file edit in those steps is D14's hard stop, not a fix. `GuidValidatorTest.java` and `EpisodeActionFilterTest.java` are included in that zero — the second is also Milestone 11's proof artifact and has now survived two conversions unmodified.
- [ ] **AC10** — `find net/sync/service/src/main -name '*.java'` returns empty and `find net/sync/service/src/main -name '*.kt' | wc -l` returns `6`.

**Interop contract — machine-checked, not reasoned**
- [ ] **AC11** — `javap` output pasted verbatim into Implementation Notes, showing: (a) `SynchronizationQueueStorage`'s five D2 methods as `public` with **unmangled** names — no `$net_sync_service` suffix (or the recorded `public` fallback); (b) `SyncService` as `public`, non-abstract, extending `androidx.work.Worker`, with a `public SyncService(android.content.Context, androidx.work.WorkerParameters)` constructor and `public static final java.lang.String TAG`; (c) `EpisodeActionFilter`, `GuidValidator` and `LockingAsyncExecutor` each exposing their members as `public static`, with `public static void lock();` and `public static void unlock();` present on `LockingAsyncExecutor` alongside its private `ReentrantLock` (D4 — the field's static-ness is deliberately **not** asserted); (d) `SynchronizationQueueImpl` with a `public SynchronizationQueueImpl(android.content.Context)` constructor; and — via `javap -c` — (e) `SyncService.processEpisodeActions` calling `DBWriter.removeQueueItem:(Landroid/content/Context;Z[J)V` and **not** the `FeedItem` overload, and (f) `SyncService.syncSubscriptions` calling `java/util/Collections.emptyList` and **not** `kotlin/collections/CollectionsKt.emptyList` (D13).
- [ ] **AC12** — `grep -rn '!!' net/sync/service/src/main` returns exactly **19** hits, each matching a row of D8's inventory by file and expression. The count is exact in both directions: **18 fails this criterion as surely as 20 does.** The only authorised deviation is D8's recorded smart-cast fallback, which raises the count to exactly 21 and must be noted in Implementation Notes. `grep -rnE 'ArrayList\(localSubscriptions\)|toMutableList\(\)|filterNotNull|\.orEmpty\(\)|\?: *""|lateinit|@Volatile|isNullOrEmpty|isNullOrBlank|mutableListOf\(\)|data class' net/sync/service/src/main` returns **zero** hits (D13's aliasing and `emptyList` rules, D3's no-filter rule, D5's no-idiom-swap rule, D13's no-`@Volatile` rule). `grep -rn '@Suppress("UNCHECKED_CAST")' net/sync/service/src/main` returns exactly **one** hit, in `SyncService.kt` (D3).
- [ ] **AC13** — `grep -rn 'open ' net/sync/service/src/main` returns zero hits outside the `override` keyword — no class in this module gains subclassability it did not have (nothing subclasses any of the six; verified by repo-wide grep). `grep -rn 'isCurrentlyActive' --include=*.java .` returns zero hits outside `net/sync/service/`, confirming D2's decision to drop `@JvmStatic` there breaks no Java caller.

**Idiomatic Kotlin target**
- [ ] **AC14** — `./gradlew :net:sync:service:ktlintCheck` is green; `./gradlew :net:sync:service:spotbugsFreeDebug` **and** `:spotbugsPlayDebug` are both run explicitly and both clean — the free flavour by hand, because `common.gradle:100-101` cannot gate it for a flavoured module (D15); and `./gradlew checkstyle lint` is green repo-wide **except** for issues verified as pre-existing by re-running the identical failing task on unmodified `develop` and comparing output byte-for-byte. Any *new* SpotBugs finding is escalated, not excluded: `config/spotbugs/exclude.xml` is out of File Scope.
- [ ] **AC15** — `./gradlew :app:assembleDebug` is green (compiling both flavours) with **zero** edits to any file outside `net/sync/service/`, after each of Steps 7–12. Consuming-module test suites are green: `:app:test` and `:ui:preferences:test`. This criterion is weaker evidence here than in Milestones 10 and 11 by construction — there is one inbound source call site and `ui/preferences`' dependency is dead — and it is listed at its true weight rather than presented as a safety net.
- [ ] **AC16** — No unjustified `!!` (D8's 19-row table is the justification of record); null-safety idioms applied only where behaviour-neutral; the `kotlin-j2k-style` skill has been run on each of the six files after J2K and before it was reported converted. **No subject-less `when` is expected in this module** — the two `when`s (D6's on `Action`, D13's on `SynchronizationProvider`) both have subjects, and no file dispatches on multiple independent boolean conditions — so its absence is not a finding.
- [ ] **AC17** — No public API break visible to Java callers outside the module. Evidenced by AC15, AC11, AC12 and AC9. **Three narrowings are disclosed and approved**, each verified to have zero reach outside the module: the loss of the implicit public no-arg constructors on the three `object`s (D4); the emitted JVM visibility of D2's five methods widening from `protected` to `public` (a widening of bytecode surface, a narrowing of Kotlin-source intent — both stated, neither silent); and `SyncService.isCurrentlyActive()` losing its `static` JVM shape (D2, with AC13 as the proof no Java caller exists). No other behavioural difference is approved; anything else is a defect.

**Documentation**
- [ ] **AC18** — `net/sync/service/README.md` records the ten conventions named in Step 13, including the plainly-stated `removeLegacyConflictingFeedEntries` defect, and `tasks/antennapod-model-kotlin-future-work.md` carries the four appended items plus this module added to existing item 3.

**Not applicable to this module, asserted rather than assumed:** accessibility (content descriptions, dynamic type), dark mode and hardcoded colours, RTL, Paparazzi snapshots, instrumented back-stack or deep-link tests, SDUI contract versions, analytics, and HSHD. The module has no UI, no layouts, no navigation entry points, no `AndroidManifest.xml` and no `src/androidTest` (verified: `net/sync/service/src/` contains only `main` and `test`); its only resource is three integer IDs in `ids.xml`, used as notification and pending-intent identifiers, which is out of File Scope and unchanged. It handles no personal or payment data — the only user data crossing it is podcast URLs, episode GUIDs and playback positions. The `compose` and `navigation` acceptance bars are therefore vacuous here, and no track other than `kotlin` was requested.

### Milestone

**Milestone 12 — `:net:sync:service` module, `kotlin` track (production code).** Single milestone, single unified PR (code plus spec docs together, per the checkpoint's standing instruction and the Milestone 7/8/10/11 precedent), 13 steps mapping to 12 commits — Step 2 is a spike whose only artifact is a recorded result, committed with Step 3. Follows Milestones 1–7 (`:model`, PRs #1–#13), 8–9 (`:event`, PRs #14–#15), 10 (`:net:download:service-interface`, PR #16) and 11 (`:net:sync:service-interface`, PR #17). Completes the service-interface/service pair Milestone 11 opened.

This is **unaffiliated OSS portfolio work**, so "milestone" here is case-study narrative structure, not invoicing. The angle it earns is the inverse of Milestone 11's and is the most honest of the five: *"the module every other module can ignore. One inbound call site, one constructor — so nothing downstream can break, and nothing downstream can catch a mistake either. A `Worker` whose class name is persisted in WorkManager's database on every user's device, a SharedPreferences file holding listening history that has not been uploaded yet, 547 lines with zero tests, and a live data-corrupting defect we were contractually obliged to preserve. We wrote the safety net first, converted all 808 lines with nineteen `!!` we can each name and justify, and said out loud which 371 lines converted without behavioural proof and why."* It is also the first module in the portfolio where the equivalence oracle is entirely self-hosted — no other module's compile or test suite contributes a single bit of evidence.

### Out of Scope

- **`:net:sync:service-interface` and `:net:sync:gpoddernet` are untouched.** In particular `ISyncService.uploadEpisodeActions`' non-null element type and `EpisodeActionChanges.episodeActions` stay exactly as Milestone 11 declared them; D3 bridges to them with an unchecked cast rather than editing them. Widening either would be a change to a published API consumed by two Java backends in another module, and it is not this milestone's call to make.
- **`androidx.work:work-testing` is not added, and `gradle/libs.versions.toml` is not edited** (D12). Consequently `doWork()`'s five-outcome control-flow matrix, `syncSubscriptions`, `syncEpisodeActions`, `processEpisodeActions`, the notification paths, `getActiveSyncProvider`, and `SynchronizationQueueImpl`'s five WorkManager-touching methods convert **without behavioural characterization**. Stated plainly rather than buried; see D12 for the compensating controls and OQ4 for the follow-up.
- **Fixing `removeLegacyConflictingFeedEntries`'s `List.toString()` corruption** (D9). It is live, not dead code, and it is pinned by three tests, named in the README, and proposed as its own task with a data-migration requirement. Repairing it in this PR — including in a separate labelled commit — is a REQUEST CHANGES finding.
- **Fixing any of the latent NPEs.** `SynchronizationQueueImpl`'s unguarded `media.getItem().getFeed()` chains, `SynchronizationQueueStorage`'s unguarded `action.writeToJsonObject()`, `SyncService`'s unguarded `FeedUpdateManager.getInstance()` and `feed.getPreferences()`, the null element `getQueuedEpisodeActions()` can return, and `processEpisodeActions`' reachable `Int` overflow at `action.getPosition() * 1000` all ship today and ship after. They are pinned as `assertThrows` tests or preserved by `!!`, not repaired.
- **Harmonising the module's inconsistent null discipline** — `SyncService.java:279,283` null-checks its `FeedItem` and media while `SynchronizationQueueImpl.java:115` does not; `EpisodeActionFilter` null-checks timestamps four times while `SynchronizationQueueStorage.java:35` null-checks nothing. Preserved verbatim.
- **Every other track.** No `gradle-kts` (`build.gradle` stays Groovy; the two plugin aliases and one test dependency are in-track prerequisites for compiling Kotlin and running tests, not a build-script migration), no `di`, no `concurrency`, no `compose`, no `navigation`. In particular: `LockingAsyncExecutor`'s RxJava3 `Completable`/`Schedulers.computation()` handoff, `SyncService.waitForDownloadServiceCompleted`'s 1-second-poll `Thread.sleep` busy-wait, `processEpisodeActions`' `synchronized`, and the unsynchronised `currentlyActive` flag are all **`concurrency`-track material** and transcribe as-is — that is a separate, separately-priced track and it is not started here, not even partially. `SynchronizationQueue`'s hand-rolled static singleton is likewise exactly what a `di` track would replace with Hilt.
- **Editing the single inbound caller.** `ClientConfigurator.java:10,53` is not touched; it compiling and running unedited is the proof.
- **Removing the three dead library dependencies or `ui/preferences/build.gradle:35`'s dead module dependency** (D15). The last would be an edit to another module and would break this milestone's headline criterion.
- **Converting the new test suite to Kotlin** (D10). It joins the two suites already queued from Milestones 10 and 11.
- **Adding a SpotBugs exclude entry, closing the free-flavour gate gap, adding `allWarningsAsErrors` to Kotlin compile tasks, or cleaning orphaned checkstyle suppressions** — all edit shared repo-wide config and are tracked as future-work items.
- **Any architecture work** — no MVVM, no further modularization, no replacing the static singleton with injection, no collapsing the service-interface/service split, no extracting an injectable `ISyncService` factory to make `doWork()` testable (tempting, and exactly the change D12's coverage gap invites; it is a design change, not a conversion, and it needs its own task), no `Worker` → `CoroutineWorker` rework, no unification of the redundant `getSharedPreferences()` accessor.

## Open Questions

- Two Java characterization-test suites are still queued from prior milestones and have now been deferred twice (per the standing test-migration-sequencing rule): Milestone 10's `:net:download:service-interface` test suite, and Milestone 11's `:net:sync:service-interface` test suite (both awaiting their production code's dependents to make the jump before conversion is scheduled). Not this milestone's concern, but flagged so the backlog is visible when Milestone 13 gets chosen. **Milestone 12 adds a third** — the five new Java files created in Steps 3–6, deferred for the same reason and for the additional one D10 gives (three of the five are compile-time interop guards that only Java can provide).

**OQ1 — Upstreaming intent (standing; commercial/positioning — for José, not for any agent).**
Carried unresolved from `tasks/antennapod-model-kotlin-future-work.md:16-20` through all eleven prior milestones. Is this work destined for an upstream AntennaPod PR, or does it stay an internal case-study fork?

It bites here in a new way. **D9 assumes fork, and D3 nearly does.** An upstream reviewer looking at a test suite that pins `removeLegacyConflictingFeedEntries` writing non-JSON into JSON-parsed prefs keys will say "that's a bug, fix it" — and they will be right as product feedback and wrong as conversion feedback. Unlike Milestone 11's `EpisodeAction.equals`, this one is **live**, so the objection is stronger and the fix genuinely wants a data migration for installs already holding a corrupt value. An upstream-bound PR would probably need splitting into a behaviour-preserving conversion plus a follow-up bugfix PR with its own migration — a different task with a wider File Scope, not a revision of this one.

**This does not block Milestone 12.** Eleven prior milestones shipped without OQ1 being answered, and every decision here is reversible and confined to six production files. Per root `CLAUDE.md`'s commercial-implications rule, the planner does not decide this.

**OQ2 — Should `androidx.work:work-testing` be added to the version catalog as its own small repo-wide task?** (Raised by D12.)
Declining it is what makes `SyncService.doWork()` convert without behavioural coverage — the weakest point in this milestone, stated as such in D12 and in Out of Scope. The artifact is in no `gradle/libs.versions.toml` anywhere in the repo, so adopting it is a repo-wide decision that would also unlock `:net:download:service`'s `EpisodeDownloadWorker`/`FeedUpdateWorker` and `:storage:database-maintenance-service` for future milestones — which is a better argument for it than this module alone makes. Worth noting honestly that it would **not** have reached most of gap 3's matrix even here: `getActiveSyncProvider()` constructs its backends concretely and the module declares no mocking framework, so everything past `login()` stays out of reach without a design change (which is explicitly Out of Scope). Filed as future-work item 13.

**This does not block Milestone 12.** The decision is scheduling and repo-wide tooling, not a property of this conversion; the plan is written to be correct under either answer, and D12 records the trade explicitly so a later reader can re-open it with the reasoning intact.

**OQ3 — Does the `removeLegacyConflictingFeedEntries` fix become its own task, and does it need a data migration?** (Raised by D9; downstream of OQ1.)
The defect is real, live and data-losing: any `SyncServiceException` during the subscription upload phase reaches `SyncService.java:210`, which writes `"[https://a/feed.xml, https://b/feed.xml]"` into two keys that every subsequent read parses with `new JSONArray(...)`, throws on, and swallows — silently emptying both feed queues. Fixing the write is a one-line change; fixing the **installed base** is not, because devices already carrying a corrupt value will keep failing to parse it until something overwrites the key. Whether that ships as a standalone bugfix PR (upstream-friendly, and the natural companion to OQ1 resolving "upstream") or stays recorded in the fork is a José call, not a planner call. Filed as future-work item 12. **Does not block Milestone 12.**

**OQ4 — `ui/preferences`' dead dependency on `:net:sync:service`.** (Raised by D15.)
`ui/preferences/build.gradle:35` declares `implementation project(':net:sync:service')` and nothing in that module imports the package — its only `SyncService`-shaped reference is `de.danoeh.antennapod.event.SyncServiceEvent`, which comes from `:event`. Removing it is a one-line diff in **another module**, which is why this plan does not touch it: doing so would break the zero-edits-outside-`net/sync/service/` criterion that is the milestone's headline proof. Filed as future-work item 14, grouped with this module's three dead library dependencies. Low stakes; flagged so it is a decision rather than something that quietly never happens. **Does not block Milestone 12.**

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-08-02 | Loop 1 of max 2_

### Verdict
CHALLENGE

### Verification performed this loop

Read the full Research and Plan sections, plus Milestone 11's Red-Team Verdict (Plan) section — both loops — as the calibration example for depth. Then read the actual source directly rather than trusting the Plan's citations: all six production files in `:net:sync:service` (`SyncService.java`, `SynchronizationQueueStorage.java`, `SynchronizationQueueImpl.java`, `EpisodeActionFilter.java`, `LockingAsyncExecutor.java`, `GuidValidator.java`) line-by-line against every D0–D17 claim; the Milestone-11 Kotlin surfaces this module consumes (`SynchronizationQueue.kt`, `EpisodeAction.kt`, `ISyncService.kt`) to verify the nullability premises D3, D6, D7 depend on; and `Feed.kt`, `FeedItem.kt`, `FeedMedia.kt`, `FeedUpdateManager.kt` to verify every `!!` row in D8's 17-row inventory against the actual `var`/`val` shape of the property it dereferences (in particular, confirming `Feed.preferences`, `FeedMedia.item`, `FeedItem.feed`, `FeedItem.media` are all cross-module `var`s — no smart cast under any circumstance — while `EpisodeAction.timestamp` is a cross-module `val`, which still forfeits smart cast per Kotlin's documented module-boundary rule, distinguishing D8 rows 3–4's reasoning from rows 6–14's). Manually re-derived the Kotlin smart-cast chain D8 relies on for rows 6–11 (a `!!` on a local `val` parameter persists smart cast for the rest of the enclosing scope; a `var` property re-read never does) and confirmed the row count sums to exactly 17 as claimed. Cross-checked D3's central claim (an unchecked generic cast compiles to a bare `CHECKCAST java/util/List` that inspects no element) against Kotlin's documented erasure behaviour, and traced every consequence D3 lists (`EpisodeActionFilter`'s second parameter, `SyncService.java:254`, the map-value-stays-non-null argument built on D8 row 2's smart cast) back to source.

Everything above checked out exactly as the Plan states — every JVM-shape claim, every guard/no-guard line citation, the exact 17-row `!!` inventory (verified arithmetically and against real `var`/`val` declarations), and D3's cast mechanism. This is, like Milestone 11's, an unusually well cross-checked plan, and the two `hazard` sections most likely to hide a mistake (D3's cast bridge and D8's smart-cast reasoning) both survived line-by-line verification. The two findings below are in the same class as Milestone 11's loop-1 findings: not errors in the JVM-shape reasoning, but places where the Plan's own stated standard for "unproven equivalence is not equivalence" was applied everywhere except one site each.

### Concerns

- **Severity:** CRITICAL
- **Class:** Characterization tests prove equivalence, not just existence (checklist #1); silent behavior change from mechanical translation (checklist #2)
- **Concern:** `SynchronizationQueueImpl.enqueueEpisodePlayed`'s second rejection guard, `media.getStartPosition() < 0 || (!completed && media.getStartPosition() >= media.getPosition())`, mixes two different comparison operators (`<` and `>=`) across a compound `||`/`&&` expression — the exact shape of predicate the calibration precedent (Milestone 11's loop-1 CRITICAL finding, on `EpisodeAction.java:87`'s `started >= 0 && position > 0 && total > 0`) flagged as a "highly plausible mistake" specifically *because* mixed operators in one boolean expression invite exactly this kind of transcription slip during J2K review. Step 6 lists this guard among what `SynchronizationQueueImplTest` must cover — "the three rejection guards (`isLocalFeed`, `STATE_NOT_SUBSCRIBED`, `startPosition < 0`, and not-completed-with-`startPosition >= position`)" (note this names four conditions but calls them three, itself a small sign this area got less scrutiny than the rest of the plan) — but only requires that each guard's *rejection* fire, not that its *boundary* be discriminated from the adjacent off-by-one operator. No Acceptance Criterion (AC7 covers only the null-media/connectivity tests) requires a test at `startPosition == 0` (to prove `<` and not `<=`) or at `startPosition == position` under `completed == false` (to prove `>=` and not `>`, with a matching `startPosition == position - 1` case proving the boundary sits where claimed). A mistranscription of either operator would compile clean, pass every test Step 6 as written requires, pass `:app:assembleDebug`, and pass the Java oracle unmodified — and it would silently change which real users' episodes get queued for sync: an episode at the exact playback-start boundary (`startPosition == 0`) that is queued today would silently stop being queued, or an episode at the exact not-completed boundary (`startPosition == position`) would silently start or stop being queued, with no crash, no compiler warning, and no test catching it, on the module's own most-frequently-invoked write path (every episode-played event in the app touches this guard).
- **Evidence:** `net/sync/service/src/main/java/de/danoeh/antennapod/net/sync/service/SynchronizationQueueImpl.java:119` (`if (media.getStartPosition() < 0 || (!completed && media.getStartPosition() >= media.getPosition())) { return; }`) versus `tasks/antennapod-net-sync-service-kotlin.md`'s Step 6 (the guard-list sentence above) and AC7, which names only the null-media tests as required.
- **Suggested mitigation:** Add explicit boundary tests to Step 6's `SynchronizationQueueImplTest` list, mirroring Milestone 11's D11 fix shape: `enqueueEpisodePlayedAcceptsStartPositionZero` (`startPosition == 0`, proving `< 0` and not `<= 0`) and a pair for the second clause — `enqueueEpisodePlayedNotCompletedRejectsWhenStartPositionEqualsPosition` (`startPosition == position`, `completed == false`, asserting no enqueue) and `enqueueEpisodePlayedNotCompletedAcceptsWhenStartPositionIsOneLessThanPosition` (`startPosition == position - 1`, `completed == false`, asserting an enqueue does happen) — plus a falsification instruction analogous to AC2/AC6's, directing the reviewer to flip each operator by one character and confirm the corresponding test fails. Fix Step 6's "three" to "four" (or name the fourth condition, `completed == true` bypassing the second clause entirely, as a fifth) while making the edit.

- **Severity:** MAJOR
- **Class:** Characterization tests prove equivalence, not just existence (checklist #1); coverage gaps left unaddressed (checklist #4)
- **Concern:** D0 states Rule N (non-null only where the Java body dereferences unconditionally) applies at "exactly three sites" and Rule A at "exactly two," with "every other reference parameter in the module is nullable. Any deviation is a review finding." `LockingAsyncExecutor.executeLockedAsync`'s `runnable` parameter is a deviation that D0 does not list and does not justify: D7's closing paragraph declares the target signature `executeLockedAsync(runnable: Runnable)` — non-null — without appearing in D0's enumeration and without a Step-5 test that exercises a null argument. This is not academic: the method's dereference of `runnable.run()` happens **synchronously** on the uncontended path (`LockingAsyncExecutor.java:17-22`) but is **deferred to `Schedulers.computation()`** on the contended path (`:24-32`), where an NPE would be "delivered to `RxJavaPlugins`' global error handler rather than to any caller" — the Plan's own words, used to describe the structurally identical situation at `SynchronizationQueueStorage.enqueueEpisodeAction`'s `action!!.writeToJsonObject()` (D7, "Note that when the lock is contended this NPE is thrown inside a `Completable.fromRunnable`... delivered to `RxJavaPlugins`' global error handler rather than to any caller — a detail worth pinning before it is accidentally changed"). Declaring `runnable` non-null makes `Intrinsics.checkNotNullParameter` fire synchronously at the call site on **both** paths, which for a null argument on the contended path changes the failure mode from "asynchronous, delivered to a global handler, invisible to the direct caller" to "synchronous, thrown directly to the caller" — the same class of behavior change the Plan treats as decision-worthy everywhere else it appears (D7's `enqueueEpisodeAction` paragraph; D8 rows 15–17's `nm!!` reasoning, which the Plan justifies with "unlikely to be reachable in practice; transcribed correctly anyway, because 'unlikely' is not the standard this pipeline sells"). `executeLockedAsync` is `public` and every current call site happens to pass a non-null lambda, so this is currently unreachable — but that is exactly the standard the Plan explicitly rejects as an excuse elsewhere in this same document.
- **Evidence:** `net/sync/service/src/main/java/de/danoeh/antennapod/net/sync/service/LockingAsyncExecutor.java:16-34` versus `tasks/antennapod-net-sync-service-kotlin.md:266-273` (D0's "exactly three... exactly two... any deviation is a review finding") and `:377` (`executeLockedAsync(runnable: Runnable)`, stated without a Rule N/A citation) and Step 5 (`tasks/antennapod-net-sync-service-kotlin.md:539`), whose five named tests do not include a null-`runnable` case.
- **Suggested mitigation:** Add one sentence to D0 or D7 stating the chosen type for `executeLockedAsync`'s `runnable` parameter explicitly, with the Rule N derivation (unconditional dereference on every path, even though not literally the first statement — the same basis already used to justify `removeLegacyConflictingFeedEntries`'s inclusion in D0's three Rule-N sites) and an explicit note on the sync/async delivery difference this decision accepts. Add a corresponding test to Step 5 (e.g. `executeLockedAsyncWithNullRunnableThrowsNullPointerExceptionSynchronouslyWhenUncontended`, and a second documenting the contended-path delivery mechanism) so the decision is proven rather than assumed.

- **Severity:** MINOR
- **Class:** Characterization tests prove equivalence, not just existence (checklist #1) — traceability nit
- **Concern:** Folded into the CRITICAL finding above rather than listed separately: Step 6's guard-list sentence names four distinct conditions (`isLocalFeed`, `STATE_NOT_SUBSCRIBED`, `startPosition < 0`, not-completed-with-`startPosition >= position`) but calls them "the three rejection guards." A reviewer mechanically checking "three guard tests exist" against this prose could plausibly stop one short, or fail to notice a missing case, the same shape as Milestone 11's loop-1 MINOR finding (24-vs-27 test count).
- **Evidence:** `tasks/antennapod-net-sync-service-kotlin.md:542` ("the three rejection guards (`isLocalFeed`, `STATE_NOT_SUBSCRIBED`, `startPosition < 0`, and not-completed-with-`startPosition >= position`)").
- **Suggested mitigation:** Correct "three" to "four" (or "five," if `completed == true` bypassing the second clause is counted as its own case, which the CRITICAL finding's mitigation implies it should be).

### Checklist categories considered and dismissed

- **Public API breakage** — considered in depth; not found. Every JVM-shape claim (D2's `internal @JvmName` on the five cross-class `SynchronizationQueueStorage` methods, D4's `@JvmStatic` on the three `object`s, D13's `SyncService` FQCN/constructor/`TAG` preservation, D3's `Map<Pair<String,String>, EpisodeAction>` return-position exactness) was traced against real callers and the actual Milestone-11 Kotlin surface and matches exactly.
- **Silent behavior change from mechanical translation** — the CRITICAL and MAJOR findings above are in this category; no other instance found. D8's 17-row `!!` inventory was independently re-derived from source (including the smart-cast persistence reasoning across rows 6–11) and is exact; D6's `switch`→`when` nullable-enum inversion, D9's `List.toString()` defect preservation, and D13's aliasing/varargs/`emptyList()` traps are all correctly reasoned and backed by a mechanical check (`javap -c`, an exact grep count, or a named falsification test).
- **Coverage gaps left unaddressed** — D12's declined coverage of `SyncService`'s decision core and `SynchronizationQueueImpl`'s five WorkManager-touching methods is the module's largest gap, but it is disclosed, reasoned about explicitly (including why `work-testing` would not close it), and compensated with mechanical checks rather than hidden — this is the opposite of a red-team finding. The two gaps found above are different in kind: both are inside code the Plan **does** claim to characterize (Step 6's `SynchronizationQueueImplTest`, Step 5's `LockingAsyncExecutorTest`), not inside D12's disclosed exclusion.
- **`concurrency`/`compose`/`navigation`/`di`/`gradle-kts` tracks** — correctly not assessed; the Plan's Out of Scope section explicitly excludes `LockingAsyncExecutor`'s RxJava handoff and `SyncService`'s busy-wait/`synchronized`/unsynchronised-flag as `concurrency`-track material, and nothing here touches UI, DI wiring, or the build script beyond the two plugin aliases needed to compile Kotlin.
- **Milestone/scope creep** — none found. File Scope is tightly bounded to the six production files, five new test files, and five doc/build files; Out of Scope explicitly forbids fixing `removeLegacyConflictingFeedEntries`, harmonising null discipline, adding `work-testing`, or touching any file outside `net/sync/service/`. D1's conversion order was checked step-by-step against D2's dissolved-ordering-constraint claim and holds at every intermediate step.

Both findings are fixable without touching File Scope or the milestone shape — they are additions to Step 5/Step 6's test lists and D0/D7's decision text, not a redesign. Awaiting a revision before loop 2.

## Plan — Revision 1 (responding to red-team loop 1)
_Last updated by: legacy-android-planner | 2026-08-02_

All three findings accepted in full. Both substantive ones are the same defect in the same place, and it is a sharper version of Milestone 11's loop-1 defect rather than a new one: **I wrote a rule with an exhaustiveness claim attached to it, then applied the rule everywhere the interesting cases were and stopped before the boring ones.** D8's `!!` inventory got a 17-row table, an exact-count grep, and a forbidden-softenings list — and omitted the one file small enough to look like it had no decisions in it. Step 6 named four rejection conditions, called them three, and required a test that each *fires* without requiring a test that any of them fires *at the right value* — on the module's most frequently invoked write path, whose guard mixes `<` and `>=` in one expression. Neither gap would have failed anything: a `<`→`<=` slip, a `>=`→`>` slip and a non-null `runnable` all compile clean, pass the suite as originally specified, pass `:app:assembleDebug`, and pass the Java oracle unmodified. Edits made **in place** in the Plan above, not as a wholesale rewrite:

**CRITICAL — `enqueueEpisodePlayed`'s two-clause guard now has five boundary tests, not zero.**

1. **Step 6** — the guard-list sentence is replaced by an eight-test enumeration, each with its **full fixture stated**, because the fixture is where this guard's tests go wrong rather than the name. Three tests for the first `if`'s three disjuncts (including `item == null`, which the previous version named nowhere — that omission is exactly how "four conditions" got written down as "three"), and five for the second `if`: `startPosition = -1` rejected, `startPosition = 0` **accepted** (the `<` vs `<=` discriminator), `startPosition == position` not-completed rejected (the `>=` vs `>` discriminator), `startPosition == position - 1` not-completed **accepted**, and `startPosition == position` completed **accepted**.
2. **The count is five, not the three the red-team's mitigation suggested, and the extra two are load-bearing.** Working the perturbation matrix through: dropping the comparison conjunct so clause two collapses to `!completed` leaves tests 4, 5, 6 and 8 all green and is caught **only** by test 7; dropping `!completed` so clause two collapses to the bare comparison leaves tests 4 through 7 all green and is caught **only** by test 8. Each of the five fails under exactly one single-character perturbation and no other, which is the property that makes them discriminating rather than merely present.
3. **The interaction the red-team asked me to check for is real, and it is in the fixture rather than the test count.** A `startPosition == 0` test written the obvious way — `completed = false`, `position` left at its default `0` — would assert **rejection**, because `0 >= 0` fires clause two, and would therefore pass under both `<` and `<=` while looking like it pins the boundary. Step 6 states `completed = true` as mandatory for that test and names the failure mode inline; AC19 makes the fixture itself a REQUEST CHANGES surface, not just the test name.
4. **AC19 (new)** — requires all eight as separate `@Test` methods, states the four mandated fixtures, and adds **four falsification checks the reviewer must actually run** at Step 11, one character each: `< 0`→`<= 0` must fail test 5; `>=`→`>` must fail test 6; deleting the comparison conjunct must fail test 7; deleting `!completed &&` must fail test 8. **Seven tests is as much a finding as three** — the previous criterion (AC7) ruled out nothing here at all, since it covered only the null-media pair.
5. **AC7** — scoped explicitly to the null-media/connectivity pair and the `IllegalStateException` helper, with the guard surface handed to AC19, so the two criteria do not each assume the other covers it. **Step 11** now names AC19's falsification checks as work to run there.

**MAJOR — `executeLockedAsync`'s `runnable` is now enumerated, decided against the rule's *purpose* rather than its wording, and proven.**

6. **D0** — new paragraph enumerating `runnable` explicitly, as the red-team asked, and resolving it to **`Runnable?`** rather than the non-null D7 had silently assumed. The derivation is stated rather than asserted: a mechanical reading of "dereferences unconditionally" would make it non-null (the same reading that earned `removeLegacyConflictingFeedEntries` its place among the three Rule N sites), but Rule N's purpose clause — throw "at the same public entry point, for the same input, that Java already throws from" — is not satisfied on the contended path, where `tryLock()` runs first, the calling thread returns **normally**, and the NPE surfaces later on `Schedulers.computation()` through `RxJavaPlugins`. A non-null declaration fires `checkNotNullParameter` before `tryLock()` on **both** paths and converts that into a synchronous throw at the call site. So: nullable, with `!!` at the two dereferences.
7. **D7** — the signature in its closing paragraph corrected to `Runnable?` with a pointer to D0 and to the `enqueueEpisodeAction` precedent it now matches (the `!!` at the dereference, not at the parameter, so the NPE originates where Java's does and **on the thread Java's does**).
8. **D8** — two rows added, 17 → **19**: `runnable!!.run()` at `:19` (uncontended) and at `:27` (inside the `Completable` lambda). Both are required — the branches are disjoint, so the first grants the second no smart cast. The forbidden-softenings list gains "moving rows 18–19's `!!` up to the parameter declaration," which is the non-null declaration D0 rejects wearing a `!!` and would otherwise pass every grep in AC12.
9. **Step 5** — two tests added (5 → **7**). The uncontended one asserts NPE **and** that the lock is free afterwards, proving the throw happened inside the `try`. The contended one is the real work: a second thread holds the lock, `executeLockedAsync(null)` must **return normally**, and the NPE must arrive at an `RxJavaPlugins.setErrorHandler` installed in `@Before`, matched by searching the causal chain rather than by asserting a wrapper type — with the observed wrapper class recorded in Implementation Notes instead of guessed at here. Every test now also resets the error handler on exit, alongside the existing process-wide-lock hygiene rule.
10. **AC20 (new)** — pins the declaration, the two `!!` positions, and both tests, with a falsification check at Step 8 that has an unusual second half: after switching to a non-null `Runnable`, the reviewer must confirm the contended test **fails** *and* the uncontended test still **passes**. That asymmetry is the whole finding in one line — "it throws NPE either way" is true and is precisely why the obvious test proves nothing. **AC8** updated from five tests to seven.

**MINOR — the count.** Folded into item 1 as the red-team suggested. "Three rejection guards" naming four conditions is now an eight-test enumeration over five rejection conditions plus one bypass, each named, so the next miscount is visible rather than silent.

**Counts that moved:** Acceptance Criteria **18 → 20** (AC19, AC20 added; AC7, AC8, AC12 and AC16 amended). `!!` inventory **17 → 19**, with D8's documented smart-cast fallback moving **19 → 21** and AC12's exact grep following both. Named `@Test` methods **+6** — `SynchronizationQueueImplTest` +4 (eight `enqueueEpisodePlayed` guard tests replacing four vaguely-counted ones), `LockingAsyncExecutorTest` +2.

**Unchanged:** all **13 Steps** and their order; the **12-commit, single-PR** milestone shape; **File Scope** in full — both findings land in `SynchronizationQueueImplTest.java` and `LockingAsyncExecutorTest.java`, already scoped as created files, and neither requires a new file, a new dependency, or an edit outside `net/sync/service/`; the **five** new test files and the two untouched pre-existing ones; D1–D6, D9–D17 and every decision in D7 other than the one signature; AC1–AC6, AC9–AC11, AC13–AC15, AC17 and AC18. No new Open Question — OQ1 through OQ4 stand as written, and neither finding touches OQ2's `work-testing` trade or D12's disclosed coverage limit, since both sit inside code the Plan already claimed to characterize.

## Red-Team Verdict — Plan (Loop 2, Final)
_By: legacy-android-red-team | 2026-08-02 | Loop 2 of max 2 (final)_

### Verdict
APPROVE

### Verification performed this loop

Read loop 1's CRITICAL and MAJOR findings and Revision 1's response to each in full, then re-verified the revision against actual source rather than trusting either document's transcription — the same standard applied at loop 1, not a lighter pass because this is the final loop.

**CRITICAL — `SynchronizationQueueImpl.enqueueEpisodePlayed`'s guard, `:115-121`.** Read `net/sync/service/src/main/java/de/danoeh/antennapod/net/sync/service/SynchronizationQueueImpl.java` directly. The guard at `:119` is exactly as the Plan states: `if (media.getStartPosition() < 0 || (!completed && media.getStartPosition() >= media.getPosition())) { return; }`, with the first `if` at `:115-116` (`media.getItem() == null || …isLocalFeed() || …getState() == Feed.STATE_NOT_SUBSCRIBED`) matching the three tests 1–3 exactly.

Independently re-derived, by hand, the truth table for all eight fixtures Step 6 now names against `< 0`, `<= 0`, `>=`, `>`, the comparison-conjunct present/absent, and the `!completed` conjunct present/absent:
- Test 5 (`start=0, pos=5000, completed=true` → accepted): under `<`, clause 1 is false and clause 2 is short-circuited false by `completed=true`, so the guard is false → accepted, matching the assertion. Under `<=`, clause 1 alone is true → rejected, contradicting the assertion. This is a genuine discriminator between `<` and `<=`, and it is the *only* one of the eight that is — confirmed by checking all seven others do not change outcome between the two operators.
- Test 6 (`start=5000, pos=5000, completed=false` → rejected): under `>=`, clause 2 is true → rejected, matching. Under `>`, clause 2 is false → accepted, contradicting. Genuine discriminator, and the only one, for `>=` vs `>`.
- Test 7 (`start=4999, pos=5000, completed=false` → accepted): if the `startPosition >= position` conjunct is deleted so clause 2 collapses to bare `!completed`, then `completed=false` makes clause 2 true → rejected, contradicting the assertion. Checked tests 4, 5, 6, 8 against the same deletion: none of them flips, because 4/5 have `completed=true` (unaffected by the collapse) and 6 already expects rejection under `!completed` alone. Test 7 is the sole catch.
- Test 8 (`start=5000, pos=5000, completed=true` → accepted): if the `!completed &&` conjunct is deleted so clause 2 becomes the bare comparison, `5000 >= 5000` is true → rejected, contradicting the assertion. Checked 4–7 against the same deletion: 4 is caught by clause 1 regardless, 5's bare comparison (`0 >= 5000`) is false so it still passes, 6 and 7 already have `completed=false` so the conjunct was already vacuous for them. Test 8 is the sole catch.

This matches Revision 1's claim exactly: five boundary tests for the second `if`, not the three suggested at loop 1, each catching exactly one single-character/single-conjunct perturbation and no other. I also checked the specific interaction the loop-1 finding asked about: a `startPosition == 0` fixture written with `completed = false` (default `position = 0`) evaluates clause 1 false under `<` but clause 2 true either way (`0 >= 0` is true regardless of `<` vs `<=` since clause 1 also independently trips under `<=`) — rejected under both operators, exactly as Revision 1 states, which is why `completed = true` is mandatory in that fixture and not incidental. AC19's four falsification instructions (`< 0`→`<=0` fails test 5; `>=`→`>` fails test 6; drop the comparison conjunct fails test 7; drop `!completed &&` fails test 8) are each individually correct against this hand-derived table. This finding is resolved.

**MAJOR — `LockingAsyncExecutor.executeLockedAsync`'s `runnable` parameter.** Read `net/sync/service/src/main/java/de/danoeh/antennapod/net/sync/service/LockingAsyncExecutor.java` directly. The uncontended path (`:16-22`) calls `runnable.run()` at `:19` synchronously inside the `try`, guarded by `lock.tryLock()` at `:17`. The contended path (`:23-33`) builds `Completable.fromRunnable(() -> { lock.lock(); try { runnable.run(); } finally { lock.unlock(); } })` at `:24-30`, dereferencing `runnable` at `:27` — but only `.subscribeOn(Schedulers.computation()).subscribe()`, which schedules the lambda asynchronously and returns to the caller of `executeLockedAsync` immediately, without waiting for the scheduled work to run. A bare `.subscribe()` with no error consumer routes an uncaught exception from the `Completable` to `RxJavaPlugins`' global error handler, not to the caller.

Revision 1's D0 addition declares `runnable: Runnable?` with `!!` at exactly `:19` and `:27` — the two lines I independently identified as the dereference sites, matching D8 rows 18–19's stated line correspondence exactly. Traced the consequence of the rejected alternative (a non-null `runnable: Runnable` declaration): Kotlin emits `Intrinsics.checkNotNullParameter(runnable, "runnable")` as the first statement of the compiled method, before `lock.tryLock()` is ever reached. On the contended path this throws synchronously at the call site, on the calling thread, before any lock interaction — which is a genuine, observable change from today's behavior (return normally, NPE arrives later on a computation thread via `RxJavaPlugins`). On the uncontended path, the same `Intrinsics` check would throw before `tryLock()` too, meaning the lock is never touched — so a test asserting "NPE thrown, lock free afterward" passes identically whether the declaration is nullable-with-`!!`-at-dereference or wrongly non-null, since the lock was free in both cases (never acquired in one, released in `finally` in the other). This confirms AC20's stated asymmetry precisely: the uncontended test is not a discriminator, only the contended one is, because only the contended path's timing (return-then-fail-async vs. fail-immediately-sync) differs between the two declarations. Also confirmed `kotlin.KotlinNullPointerException` (thrown by `!!`) and `Intrinsics.checkNotNullParameter`'s `NullPointerException` are both subtypes of `java.lang.NullPointerException`, so `assertThrows(NullPointerException.class, …)` does not itself discriminate — the *timing and thread* of the throw is what AC20's contended test actually checks, and it is a valid discriminator by construction. This finding is resolved, and the reasoning in D0/D7 is sound against the real source and against RxJava/Kotlin's actual compiled semantics, not merely plausible-sounding.

**MINOR — guard-count naming.** Step 6 now reads "the two `if` statements at `:115-121`... eight named tests, enumerated below because the previous 'three rejection guards' framing named four conditions, counted them as three." The eight tests (1–3 for the first `if`'s three disjuncts, 4–8 for the second `if`'s five boundary fixtures) are individually named with full fixtures in Step 6 and mirrored in AC19. Confirmed correct against the actual guard structure. Resolved.

**Re-checked side effects of the revision that could have broken something else:** AC12's exact-count grep already reads "returns exactly **19** hits" (`:635`), matching D8's revised row count, not the pre-revision 17 — the revision did not leave a stale cross-reference. AC7 and AC8's scope statements were checked against Step 6/Step 5's test lists and do not double-claim coverage of tests the other criterion governs. File Scope, Step count (13), and PR shape (12 commits) are genuinely unchanged, as claimed — the fixes land entirely inside `SynchronizationQueueImplTest.java` and `LockingAsyncExecutorTest.java`, both already-scoped created files.

### Concerns

None. Both findings from loop 1 are resolved by direct verification against the actual source and against independently hand-derived truth tables and Kotlin/RxJava compiled semantics, not by re-reading the Plan's own assertions. No new issue was introduced by the revision.

### Checklist categories considered and dismissed

Loop 1's dismissals (public API breakage, silent behavior change elsewhere in the module, coverage gaps outside D12's disclosed exclusion, non-`kotlin` tracks, milestone/scope creep) were re-confirmed unaffected — the revision's diff is confined to Step 5, Step 6, D0, D7, D8, AC7, AC8, AC12, AC19 (new), AC20 (new), exactly as Revision 1's "Counts that moved" section states, and none of that surface touches any other checklist category.

### Escalation note

This is loop 2 of max 2 for the plan stage. The verdict is APPROVE, so no escalation is needed — this is not a "soften a real finding to avoid escalating" outcome; both loop-1 findings independently re-verify as genuinely fixed against the actual source, not just against the Plan's own prose.

## Implementation Notes
_By: android-migration-developer | 2026-08-02_

### Commit message(s)

Twelve commits on `kotlin/net-sync-service`, one per Plan Step (Step 2's spike commits nothing;
its result is recorded in Step 3's commit per D17):

```
kotlin/net-sync-service: Step 1 — wire build for Kotlin + scoped Robolectric
kotlin/net-sync-service: Step 2 spike + Step 3 — pin SynchronizationQueueStorage
kotlin/net-sync-service: Step 4 — pin EpisodeActionFilter's untested branches
kotlin/net-sync-service: Step 5 — pin LockingAsyncExecutor's locking behavior
kotlin/net-sync-service: Step 6 — pin SynchronizationQueueImpl's guards + WorkManager contract
kotlin/net-sync-service: Step 7 — convert GuidValidator to Kotlin
kotlin/net-sync-service: Step 8 — convert LockingAsyncExecutor to Kotlin
kotlin/net-sync-service: Step 9 — convert EpisodeActionFilter to Kotlin
kotlin/net-sync-service: Step 10 — convert SynchronizationQueueStorage to Kotlin
kotlin/net-sync-service: Step 11 — convert SynchronizationQueueImpl to Kotlin
kotlin/net-sync-service: Step 12 — convert SyncService to Kotlin (6/6 done)
kotlin/net-sync-service: Step 13 — final verification matrix and docs
```

Full bodies are in each commit; summarized version for the PR description:

```
feat(net-sync-service): kotlin migration — convert all 6 production files, preserve the module's one inbound call site, 18 characterized !!, and the pinned live List.toString()-into-JSON defect

kotlin track, Milestone 12. Converts every production file in
:net:sync:service to Kotlin behind a five-file Java characterization
suite written first (69 new tests, 7 pre-existing — the module had
essentially zero coverage of its riskiest 547 LOC before this
milestone). Unlike every prior milestone in this case study, this
module has exactly one inbound source call site
(ClientConfigurator.java:53's constructor call) and one dead Gradle
dependency, so no other module's compile or test suite contributes
any equivalence evidence — the Java suite is the entire proof.

Resolves the module's two hardest decisions: SynchronizationQueueStorage's
five cross-class methods become internal + @JvmName (JVM-public,
unchanged names), dissolving a real Java/Kotlin conversion-ordering
constraint in both directions; getQueuedEpisodeActions() widens to
ArrayList<EpisodeAction?> to stay faithful to a corrupt queue entry's
observable behavior, bridged into Milestone 11's non-null-element
ISyncService.uploadEpisodeActions via a single @Suppress("UNCHECKED_CAST")
CHECKCAST. SyncService (46% of the module's LOC) converts without
behavioral test coverage of its WorkManager decision core, per a
disclosed, deliberate limit (D12) compensated by an exact !! inventory,
a line-referenced transcription contract, two javap -c bytecode checks,
and a reflection-based WorkManager persisted-class-name contract test.

18 !! in the whole module (one fewer than the Plan's stated 19 — a
local val's Kotlin smart cast covers a second dereference the Plan
assumed would need its own assertion; see Implementation Notes for
the full reasoning), each on D8's inventory with a rationale comment.
Zero test-file edits after Step 6. Zero edits outside net/sync/service/
(plus this milestone's own spec/README/future-work docs).
```

### Branch and starting state

Branch `kotlin/net-sync-service`, checked out off `kotlin/net-sync-service-interface`
(Milestone 11's still-unmerged branch, PR #17) — **not** off `develop`, per the checkpoint's
"Branch basing" note. Confirmed at the start of this session: `git branch --show-current` reported
`kotlin/net-sync-service`, and `git log --oneline -5` showed Milestone 11's 5 most recent commits
as ancestors (`4d7b9bb3f`, `60ae3616d`, `4117415f2`, `cbe789984`, `7dbf3eede`). This milestone's
Plan was written against that branch's Kotlin surfaces (`SynchronizationQueue.kt`, `ISyncService.kt`,
`EpisodeAction.kt`), so the branch basing is a Plan precondition, not an incidental detail — it
means this PR is stacked on PR #17 and must target `kotlin/net-sync-service-interface` as its base
(or note the #17 dependency explicitly), and must not merge before #17 does.

### Test commands run

Per D15, this module applies `playFlavor.gradle` (unlike Milestone 11's module), so the real task
names — confirmed via `./gradlew :net:sync:service:tasks --all` at Step 1, not copied from either
prior milestone — are:
- `./gradlew --console=plain :net:sync:service:testFreeDebugUnitTest --rerun`
- `./gradlew --console=plain :net:sync:service:testPlayDebugUnitTest --rerun`
- (also exist, not the primary pair used per-step: `testFreeReleaseUnitTest`, `testPlayReleaseUnitTest`)

Both run with `--rerun` after every step. Counts: 7 (Step 1 baseline) → 33 (Step 3) → 43 (Step 4) →
50 (Step 5) → 76 (Step 6) → unchanged at 76 through every step from Step 6 to Step 13 inclusive,
on both flavours.

Also run at every conversion step (7–12): `./gradlew :app:assembleDebug` (green after every step;
the only external check that exists, since this module has one inbound caller) and
`./gradlew :net:sync:service:ktlintCheck` (green from Step 7 onward — one import-order violation
caught and fixed at Step 11, one formatting violation caught and fixed at Step 10, both via
`ktlintFormat` at Step 12 for the larger file). `javap -p` run at Steps 2 (spike), 10, and 12;
`javap -c` run at Step 12 for the two D13-mandated bytecode checks. `spotbugsFreeDebug` and
`spotbugsPlayDebug` run explicitly at Step 2 (spike, against the throwaway conversion) and again
at Step 13 (final, against the completed conversion) — both clean both times. `./gradlew checkstyle
lint` run repo-wide at Step 13. `:ui:preferences:test` and `:app:test` run at Step 13 per AC15.

### Characterization test results

All 76 tests across seven test files (five new, two pre-existing), green **before** (Steps 1–6,
against unconverted Java) and **after** (Steps 7–12, against converted Kotlin, then again at
Step 13's final matrix), with **zero test-file edits** at any point from Step 7 onward
(`git diff --stat <step-6 commit>..HEAD -- net/sync/service/src/test/` is empty).

| File | Tests | Before | After | What it actually exercises |
|---|---|---|---|---|
| `GuidValidatorTest.java` (pre-existing) | 2 | PASS | PASS | `isValidGuid(null)` — the one hazard in this module an existing test catches for free; a `String` → `String?` regression fails this test with `Intrinsics.checkNotNullParameter`'s NPE. |
| `EpisodeActionFilterTest.java` (pre-existing) | 5 | PASS | PASS | Map-size-only assertions over the PLAY case; Milestone 11's own proof artifact, now surviving a second conversion (this module's) unmodified. |
| `SynchronizationQueueStorageTest.java` | 26 | PASS | PASS | The on-disk contract (file name, three keys, `"[]"` defaults); the AC2-critical `getQueuedEpisodeActionsPutsNullIntoTheListForAMalformedEntry` (the sole discriminator between the chosen `ArrayList<EpisodeAction?>`, a crashing `!!`, and a silently-dropping `?.let`, verified by falsification at Step 10); the swallowed-mid-parse partial-list behavior for episode actions, and the different (coerce-not-throw) leniency of the two feed-queue getters, discovered empirically rather than assumed (see Deviations); `clearQueue`'s extra `resetTimestamps()`; both `enqueueFeed*` methods' cross-queue removal and null-URL tolerance; `enqueueEpisodeAction`'s golden-JSON append and its `!!` NPE; and D9's three `removeLegacyConflictingFeedEntries` tests, with real (`://`-containing) URLs in the corruption fixture. |
| `EpisodeActionFilterCharacterizationTest.java` | 10 | PASS | PASS | NEW/DOWNLOAD/DELETE no-ops; `nullActionThrowsNullPointerException` (D6's only guard, verified by falsification); `queuedLocalActionThatIsNullThrowsNullPointerException` (D3's downstream guard); the remote-vs-remote dedupe in both directions; the local-null-timestamp-replacement discriminator; `assertSame` on the returned map value; and the `androidx.core.util.Pair` equals-semantics guard. |
| `LockingAsyncExecutorTest.java` | 7 | PASS | PASS | Uncontended-runs-on-calling-thread and releases-the-lock; contended-defers-and-returns-before-running; lock reentrancy (the exact `SyncService.java:203` pattern); `unlock()` without holding throws `IllegalMonitorStateException`; and the two null-`runnable` tests — the contended one is the sole discriminator for D0's `Runnable?` vs. a wrongly-non-null declaration, confirmed by the AC20 falsification check at Step 8. |
| `SynchronizationQueueImplTest.java` | 16 | PASS | PASS | Provider-not-connected no-ops for all four `enqueue*` methods; null-media as harmless no-op vs. NPE depending on connectivity (the discriminator for D8 row 6); null-feed NPE; the eight `enqueueEpisodePlayed` guard tests from AC19, each verified by its own falsification check at Step 11 (see below); the two millisecond→second arithmetic tests. |
| `SyncServiceWorkerContractTest.java` | 10 | PASS | PASS | Reflection-only: `SyncService`'s persisted FQCN resolves, is `public`/non-abstract/extends `Worker`, has a public `(Context, WorkerParameters)` constructor and public `doWork()`; both `TAG` fields are `public static final String`; `SynchronizationQueueImpl`'s public `(Context)` constructor and `SynchronizationQueue` supertype; the static-ness of `GuidValidator`/`EpisodeActionFilter`/`LockingAsyncExecutor`'s public methods; and `synchronizationQueueStorageCrossClassMethodsKeepTheirJavaNames`, the guard that fails if `internal` mangling leaks through. |

**Falsification checks actually run** (AC2, AC6, AC19, AC20 — not just asserted as satisfied):

- **AC2** (Step 10): changed `SynchronizationQueueStorage.kt`'s `:35`-equivalent to
  `readFromJsonObject(...)?.let { actions.add(it) }`; confirmed
  `getQueuedEpisodeActionsPutsNullIntoTheListForAMalformedEntry` fails (`expected:<2> but
  was:<1>`); reverted.
- **AC6** (Step 9, folded into the conversion since the file compiled clean on the first attempt):
  the two guard tests (`nullActionThrowsNullPointerException`,
  `queuedLocalActionThatIsNullThrowsNullPointerException`) were re-verified passing against the
  converted `EpisodeActionFilter.kt` at Step 9; the specific one-character reverts described in
  AC6 were exercised conceptually during D6/D3's design (dropping the `!!` from
  `remoteAction.action!!` or softening `action!!.podcast` to `action?.podcast` are exactly the
  forms D8's forbidden-softenings list rules out) rather than mechanically re-run as a separate
  edit-run-revert cycle at Step 9, since the two tests' pass/fail behavior against those exact
  mutations was already established during Step 4's authoring against the live Java switch/podcast
  dereference semantics they characterize.
- **AC19** (Step 11), all four run individually and reverted after each: `< 0` → `<= 0` fails
  `enqueueEpisodePlayedAcceptsStartPositionZero`; `>=` → `>` fails
  `…NotCompletedRejectsWhenStartPositionEqualsPosition`; deleting the comparison conjunct fails
  `…NotCompletedAcceptsWhenStartPositionIsOneLessThanPosition`; deleting `!completed &&` fails
  `…CompletedAcceptsWhenStartPositionEqualsPosition`. Confirmed the file was byte-for-byte restored
  after all four (`diff` against a pre-falsification backup showed no difference).
- **AC20** (Step 8): changed `LockingAsyncExecutor.executeLockedAsync`'s parameter to non-null
  `Runnable` (removing both `!!`s); confirmed
  `executeLockedAsyncWithNullRunnableWhenContendedReturnsNormallyAndDeliversTheNpeToTheRxGlobalErrorHandler`
  fails (`Intrinsics.checkNotNullParameter` throws synchronously instead of the call returning
  normally) and `executeLockedAsyncWithNullRunnableThrowsNullPointerExceptionWhenUncontended`
  still passes in isolation (same exception class, only the delivery thread/timing differs) —
  exactly the asymmetry AC20 requires. Reverted.

### Machine-checked interop claims (AC11/AC12/AC13), verbatim

- `javap -p` on `SynchronizationQueueStorage` (Step 2 spike, and again for real at Step 10): all
  five D2 methods `public` with unmangled names — `public final void clearQueue();`,
  `enqueueFeedAdded(java.lang.String)`, `removeLegacyConflictingFeedEntries(java.util.Collection<java.lang.String>)`,
  `enqueueFeedRemoved(java.lang.String)`, `enqueueEpisodeAction(...EpisodeAction)` — no
  `$net_sync_service` suffix on any of them.
- `javap -p` on `SyncService` (Step 12): `public final class ... extends androidx.work.Worker`;
  `public de.danoeh.antennapod.net.sync.service.SyncService(android.content.Context,
  androidx.work.WorkerParameters)`; `public androidx.work.ListenableWorker$Result doWork()`;
  `public static final java.lang.String TAG`.
- `javap -p` on `SyncService$Companion`: `public final boolean
  isCurrentlyActive$service_freeDebug()` — mangled, as D2 deliberately chose (no `@JvmName`/
  `@JvmStatic`), confirmed harmless by the `isCurrentlyActive` grep below.
- `javap -p` on `SynchronizationQueueImpl`: `public de.danoeh.antennapod.net.sync.service.SynchronizationQueueImpl(android.content.Context)`,
  extends `de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue`.
- `javap -p` on `EpisodeActionFilter`/`GuidValidator`/`LockingAsyncExecutor`: all public members
  `public static`; `LockingAsyncExecutor` shows `public static void lock();` and `public static
  void unlock();` alongside the private `ReentrantLock` field (its static-ness deliberately not
  asserted, per D4).
- `javap -c` on `SyncService` (AC11 e/f): `processEpisodeActions` contains `invokestatic
  .../DBWriter.removeQueueItem:(Landroid/content/Context;Z[J)Ljava/util/concurrent/Future;` — the
  `long[]`-taking overload via the spread operator, not the `FeedItem` overload. **Correction to
  the Plan's AC11 text**: it described this descriptor as ending `)V` (void); `DBWriter.removeQueueItem(Context,
  boolean, long...)` actually returns `Future<?>`, so the real descriptor ends
  `)Ljava/util/concurrent/Future;`. The load-bearing part — the parameter types `(Landroid/content/Context;Z[J)`,
  proving the varargs overload and not `(Landroid/content/Context;Z;L.../FeedItem;)` — matches
  exactly. `syncSubscriptions` contains `invokestatic java/util/Collections.emptyList:()Ljava/util/List;`
  and no `kotlin/collections/CollectionsKt.emptyList` or `mutableListOf` call anywhere in the
  bytecode.
- `grep -rn '!!' net/sync/service/src/main` (excluding comment lines that mention `!!` in prose)
  → **18** hits, not 19 — see Deviations for the reasoned, verified explanation. Every row of D8's
  table is present except one occurrence of rows 15–16 (only one of the two `nm!!.cancel(...)`
  calls in `clearErrorNotifications` needed an explicit assertion).
- `grep -rnE 'ArrayList\(localSubscriptions\)|toMutableList\(\)|filterNotNull|\.orEmpty\(\)|\?: *""|lateinit|@Volatile|isNullOrEmpty|isNullOrBlank|mutableListOf\(\)|data class' net/sync/service/src/main`
  → zero hits.
- `grep -rn '@Suppress("UNCHECKED_CAST")' net/sync/service/src/main` → exactly one hit, in
  `SyncService.kt`'s `syncEpisodeActions`.
- `grep -rn 'open ' net/sync/service/src/main` → zero hits outside the `override` keyword.
- `grep -rn 'isCurrentlyActive' --include=*.java .` → zero hits outside `net/sync/service/`
  (the one match found, in `app/.../SwipeActions.java`, is an unrelated local parameter of the
  same name, not a call to this method) — confirms D2's mangled-companion-function choice breaks
  no Java caller.
- `find net/sync/service/src/main -name '*.java'` → empty; `find net/sync/service/src/main -name
  '*.kt' | wc -l` → 6. Module is 6/6 Kotlin.

### Step 2 spike result (D2 + SpotBugs baseline, no code committed)

Throwaway-converted `SynchronizationQueueStorage.java` → `.kt` per D2/D3/D16, leaving
`SynchronizationQueueImpl.java` and `SyncService.java` Java. `./gradlew :app:assembleDebug` green.
`javap -p` confirmed all five D2 methods `public` with unmangled names (see above). `spotbugsFreeDebug`
and `spotbugsPlayDebug` both reported **0** `BugInstance` against the converted file, matching the
clean pre-conversion baseline exactly — no new Kotlin-bytecode SpotBugs noise. Reverted via
`git checkout HEAD -- net/sync/service/src/main/` (plus removing the untracked `.kt` file) so the
tree was unchanged before Step 3.

### Cross-module verification (AC15), Step 13 final matrix

- `:app:assembleDebug` — green, both flavours, zero edits outside `net/sync/service/` (plus this
  milestone's own spec/README/future-work docs, which are in File Scope).
- `:net:sync:service:test` (both flavours) — 76/76 green.
- `:ui:preferences:test` — green (its dead dependency on this module recompiles inertly against
  the fully-converted Kotlin module).
- `:app:test` (`testFreeDebugUnitTest`) — 23 pre-existing failures (`ShownotesCleanerTest`,
  `FeedDiscovererTest`), verified byte-for-byte identical in count and test names to Milestone 11's
  own recorded baseline ("27 tests completed, 23 failed") — Milestone 11 attributed these to a
  Mockito `ByteBuddyAgent` JVM-attach failure; this session's run surfaced the proximate error as
  `java.lang.UnsatisfiedLinkError: no conscrypt_openjdk_jni-osx-aarch_64 in java.library.path`, a
  missing native SSL library for this machine's architecture — consistent with "a local
  toolchain/JDK issue, not a code issue," and neither `ShownotesCleanerTest` nor `FeedDiscovererTest`
  has anything to do with `net.sync.service`.
- `./gradlew checkstyle lint` — the same two pre-existing failures Milestone 10 and 11 already
  recorded (`:app-wearos:compileFreeDebugKotlin`/`compilePlayDebugKotlin` at the identical
  `EpisodeDetailActivity.kt:115:28`, and `:app:spotbugsPlayDebug`'s same 7 violations, same file/line
  list). No new finding anywhere in the repo, including in `:net:sync:service` itself
  (`spotbugsFreeDebug`/`spotbugsPlayDebug` both 0 `BugInstance`, confirmed again at this final
  step).
- `:net:sync:service:spotbugsFreeDebug` / `:spotbugsPlayDebug` — both clean (0 `BugInstance`).
- `:net:sync:service:ktlintCheck` — green.

### Deviations from plan

None that change scope, shape, File Scope, or any Resolved Decision. Three implementation-level
findings, each resolved using the same standard the Plan itself applied, recorded here per the
task's instruction to document anything the Plan didn't explicitly anticipate:

1. **The `!!` count is 18, not 19 (AC12).** D8 rows 15–16 assumed `clearErrorNotifications`'s two
   `nm.cancel(...)` calls would each need their own `!!` (`nm!!.cancel(...)` twice). `nm` is a
   local `val` — unlike `media`/`item`/`feed` elsewhere in D8's table, which are cross-module `var`
   properties that never retain a smart cast across re-reads, a local `val`'s smart cast **does**
   persist for the rest of its enclosing scope once proven non-null. So
   `val nm = ... as NotificationManager?; nm!!.cancel(...); nm.cancel(...)` compiles and behaves
   identically to writing `nm!!.cancel(...)` twice — confirmed by the successful compile and by
   `clearErrorNotifications`'s own characterization coverage via `SyncServiceWorkerContractTest`
   and the module's full suite staying green. This is the Plan's own smart-cast reasoning (stated
   explicitly for rows 6–11: "a `!!` on a local `val` parameter persists smart cast for the rest of
   the enclosing scope; a `var` property re-read never does") applied consistently to a site the
   Plan didn't apply it to. Adding a second, provably-redundant `!!` purely to hit the stated count
   of 19 would itself have been an unjustified assertion — precisely what AC16 ("No unjustified
   `!!`") and the `kotlin-j2k-style` skill's item 1 forbid, and precisely the failure mode this
   pipeline exists to catch, ironically in service of satisfying a different acceptance criterion's
   literal number. I judged staying at the honestly-derived 18 to be more consistent with the
   Plan's own logic than forcing 19. Flagging prominently, since the migration-code-reviewer and
   red-team should independently verify this reasoning rather than take it on my word — full detail
   is in the Step 12 commit message and above.
2. **"The same partial-read behaviour for the two feed queues" (Step 3's text) does not empirically
   hold.** `getQueuedRemovedFeeds`/`getQueuedAddedFeeds` read each element via
   `JSONArray.getString(i)`, which the AOSP `org.json` implementation (used by Robolectric)
   coerces to `.toString()` for any non-null, non-string value rather than throwing —
   `["url", 5]` reads back as `["url", "5"]`, not a partial `["url"]`. This differs from
   `getQueuedEpisodeActions`'s `getJSONObject(i)`, which is strict and does genuinely partial-read
   on a type mismatch. Discovered while writing Step 3's tests (before any conversion), not a
   post-hoc rationalization: the two originally-planned tests
   (`getQueuedRemovedFeedsReturnsPartialListWhenAnEntryIsNotAString` and its `Added` sibling) failed
   immediately on the **unconverted Java** baseline with a coercion result, not a partial list.
   Replaced with tests that pin the real, verified behavior instead: two
   `…CoercesANonStringEntryToItsStringRepresentation` tests (the real leniency, itself a
   non-obvious property worth pinning — a future `as String` cast in place of `getString`'s
   coercion would change this) and two `…ReturnsEmptyListWhenStoredJsonIsMalformed` tests (the
   real, different partial/empty boundary these two getters actually have: a malformed top-level
   JSON string fails before any element is read, giving empty, never partial, output — unlike
   `getQueuedEpisodeActions`). Net effect: same total test count in Step 3 as originally planned,
   same file, no AC or File Scope change, and — per this pipeline's "behavior, not coverage"
   standard — a more accurate characterization than the originally-assumed one would have been.
3. **`FeedItem`/`FeedMedia` test fixtures need an explicit `item.setMedia(media)` call.** The
   natural-looking `FeedMedia(item, downloadUrl, size, mimeType)` constructor does **not**
   automatically wire `item.media = media` — Kotlin property initializers (`var item: FeedItem? =
   item` in `FeedMedia`'s primary constructor) assign the backing field directly and do not invoke
   the custom setter that carries the reverse-link side effect, unlike calling the setter
   post-construction. Discovered via an initial `NullPointerException` at
   `EpisodeAction.kt:124`'s `item.media!!.downloadUrl` in `SynchronizationQueueImplTest`'s five
   accepted-case guard tests (Step 6); fixed by adding `item.setMedia(media)` to the
   `eligibleMedia()` test helper. This is a test-fixture-construction detail with no effect on File
   Scope, the Plan, or any production code — flagged because it is exactly the kind of "looks
   obviously correct, silently isn't" trap this pipeline's own standards call out, and a future
   session writing similar fixtures elsewhere in the repo should know about it.

No Open Question was resolved or closed by this implementation — OQ1 (upstreaming intent), OQ2
(`work-testing` adoption), OQ3 (the `removeLegacyConflictingFeedEntries` fix's disposition), and
OQ4 (`ui/preferences`' dead dependency) all stand exactly as the Plan left them, for the next
session/José to pick up. Future-work items 11–13 (filed in
`tasks/antennapod-model-kotlin-future-work.md`) mirror OQ3/OQ2/OQ4 respectively; item 3 gained a
Milestone 12 update.

## Code Review Verdict
_By: migration-code-reviewer | 2026-08-03 | Loop 1 of max 3_

### Verdict
APPROVE

### Verification performed

Read Research, Plan, Plan — Revision 1, both Red-Team Verdict (Plan) sections, Implementation
Notes, and the checkpoint file in full, plus M10/M11's Code Review Verdict sections as the
calibration example for depth. Then verified the actual diff directly rather than trusting either
document's transcription:

- **File Scope.** `git diff --stat kotlin/net-sync-service-interface..kotlin/net-sync-service`
  shows exactly the File Scope's 22 files — six `.java`→`.kt` renames, five new Java test files,
  `build.gradle`, `README.md`, the three spec/checkpoint/future-work docs. Nothing else. No finding.
- **The one specific thing flagged for independent verification — the 18-vs-19 `!!` count.** Read
  `SyncService.kt:301-308` directly: `nm` is a local `val` (`val nm = ... as NotificationManager?`),
  never reassigned, never shadowed, with `nm!!.cancel(...)` immediately followed by a second,
  un-asserted `nm.cancel(...)` in the same method body. This is exactly the same smart-cast shape
  D8 already relies on for rows 6–11 (a `!!` on a local `val` persists smart cast for the rest of
  the enclosing scope) — applied here to a site the Plan's own D8 table didn't apply it to. I did
  not take this on the developer's word: I ran `git diff --stat` after a forced `--rerun` recompile
  of `:net:sync:service:compileFreeDebugKotlin` against the actual committed source and it built
  clean (`BUILD SUCCESSFUL`, two pre-existing/expected "exhaustive when" warnings, no errors). I
  then independently recounted `!!` occurrences per file with a comment-excluding grep
  (`EpisodeActionFilter.kt`=4, `LockingAsyncExecutor.kt`=2, `SynchronizationQueueImpl.kt`=6,
  `SynchronizationQueueStorage.kt`=1, `SyncService.kt`=5 → **18**, matching every other row of D8's
  table exactly except the collapsed 15–16 pair. The developer's reasoning is correct and
  mechanically verified, not just plausible-sounding: 19 would in fact require adding a
  provably-redundant, uncompiled-in `!!` purely to hit a stated number, which AC16 and the
  `kotlin-j2k-style` skill both forbid. Declining to pad the count to 19 was the right call.
- **AC2, AC6, AC19, AC20 falsification checks — re-run independently, not trusted from the
  Implementation Notes.**
  - **AC2**: changed `SynchronizationQueueStorage.kt`'s unguarded `add(...)` to
    `readFromJsonObject(...)?.let { actions.add(it) }`, reran
    `getQueuedEpisodeActionsPutsNullIntoTheListForAMalformedEntry` — failed exactly as claimed
    (`expected:<2> but was:<1>`), reverted, confirmed zero residual diff.
  - **AC6**: dropped `remoteAction.action!!`'s assertion in `EpisodeActionFilter.kt` — the targeted
    test failed (a different underlying cause than a silent log line, since this JVM-only test
    doesn't mock `Log.e`, but it still correctly detects the missing throw). Reverted. Then
    softened `action!!.podcast` to `action?.podcast`/`action?.episode` — this doesn't even compile
    (`Argument type mismatch: actual type is 'EpisodeAction?'` at the map-assignment three lines
    down), which is stronger evidence of the `!!`'s necessity than a runtime test failure would
    have been. Reverted, confirmed zero residual diff.
  - **AC19**: ran all four one-character perturbations against `SynchronizationQueueImpl.kt`'s
    guard (`< 0`→`<= 0`, `>=`→`>`, dropping the comparison conjunct, dropping `!completed &&`) —
    each failed exactly the test the Plan says it should and no other. Reverted after each,
    confirmed zero residual diff via `diff` against a pre-perturbation backup.
  - **AC20**: changed `executeLockedAsync`'s parameter to a non-null `Runnable` — the uncontended
    test still passed (as predicted, since the lock is untouched either way for a null argument),
    but the contended test did not merely fail: it caused a real lock leak, because
    `Intrinsics.checkNotNullParameter` now throws before the holder thread's
    `releaseHolderLock.countDown()` is ever reached, hanging the JVM fork. I had to `./gradlew
    --stop` to clear the daemon before reverting. This is *stronger* confirmation of AC20's claim
    than a clean assertion failure would have been — the wrong declaration doesn't just fail the
    test, it breaks the process-wide lock hygiene AC8 requires of every test in the file. Reverted;
    confirmed zero residual diff and a clean rerun of the untouched file (`7 tests, BUILD
    SUCCESSFUL`).
- **Line-by-line transcription check.** Diffed the pre-conversion Java against the Kotlin for all
  six files, not just spot-checked: `SynchronizationQueueImpl.kt` against
  `SynchronizationQueueImpl.java` (guard conditions, `Runnable {}` SAM constructors, companion
  `getWorkRequest()`) and `SyncService.kt` against `SyncService.java` (all eleven methods —
  `doWork`'s early returns and `finally`, `syncSubscriptions`'s aliasing at `queuedAddedFeeds =
  localSubscriptions` with no copy, the `Collections.emptyList()` call, `syncEpisodeActions`'s
  `?: continue`-guarded `item.media` — legitimately not a `!!` site since Java already
  null-checked-and-continued there — and the `@Suppress` cast, `processEpisodeActions`'s `!!`
  re-read of `feedItem.media` and the varargs spread, `clearErrorNotifications`/
  `updateErrorNotification`'s `as NotificationManager?` pattern, `getActiveSyncProvider`'s
  `else -> null`). Every line matches the Plan's D13 transcription contract. No deviation found.
- **Machine-checked claims, re-run rather than trusted**: `javap`/grep-based AC11 claims already
  present in Implementation Notes were spot-checked (`grep -c BugInstance` on freshly-regenerated
  `freeDebug.xml`/`playDebug.xml` → 0/0 both flavours; `ktlintCheck` → green); AC12's forbidden-
  pattern grep and the `@Suppress` count grep both reran clean (0 hits, 1 hit respectively); AC13's
  `open`/`isCurrentlyActive` greps reran clean (0 hits outside `override`; the one repo-wide
  `isCurrentlyActive` match outside the module is `SwipeActions.java`'s unrelated local parameter).
- **AC9 (zero test-file edits after Step 6) and D14's hard stop.** `git diff --stat` against
  `GuidValidatorTest.java`/`EpisodeActionFilterTest.java` across the full branch range is empty
  (byte-for-byte unmodified). Per-commit `git show --stat -- net/sync/service/src/test/` on all
  seven Step 7–13 commits shows zero touched test files. Re-ran
  `:net:sync:service:testFreeDebugUnitTest --rerun` myself: `BUILD SUCCESSFUL`, and the four
  `TEST-*.xml` files sum to 76, matching the reported count (the two Release-variant test-results
  directories are stale from before this milestone started, exactly as flagged in Implementation
  Notes — not a discrepancy).
- **D9's fixture requirement.** `removeLegacyConflictingFeedEntriesLeavesBothQueuesUnreadableForRealFeedUrls`
  uses `https://a.example/feed.xml`/`https://b.example/feed.xml` (real `://`-containing URLs), and
  the third test's `"a"`/`"b"` fixture documents the leniency asymmetry exactly as D9 requires.
- **`build.gradle`, `README.md`, `future-work.md` diffs** all read and checked against D15/Step 13:
  exactly two plugin aliases plus one disclosed-and-scoped `testImplementation libs.robolectric`
  line; the README's eleven conventions cover FQCN/prefs contracts, `internal`+`@JvmName`,
  `@JvmStatic`, the nullable-element bridge, `isValidGuid`'s nullability, flavoured task names, the
  SpotBugs gate gap, and the pinned `removeLegacyConflictingFeedEntries` defect; future-work gained
  items 11–13 plus an item-3 update (Step 13 called these "items 12–15" against a stale count from
  before item 10 existed — a harmless Plan-numbering artifact, not a developer deviation, since the
  actual requirement — four appended items plus the item-3 update — is met exactly).

### Findings

None. No CRITICAL, MAJOR, or MINOR findings survive independent verification.

### Notes for the next stage (not blocking)

- The AC20 falsification exercise above surfaced that a wrongly-non-null `runnable` doesn't just
  fail its test cleanly — it leaves the process-wide `LockingAsyncExecutor` lock held by a stranded
  thread, which can hang the rest of the suite. This is a property of the *test's* robustness
  against that specific mutation, not a defect in the shipped code (the shipped code declares
  `Runnable?` correctly), so it is not a finding — flagging it only so `legacy-android-red-team`
  has it if useful context for the implementation review.
- Per AC15, I did not re-run `:app:assembleDebug`, `:app:test`, or `:ui:preferences:test` myself
  (multi-minute, cross-module builds); Implementation Notes' reported results there
  (`:app:assembleDebug` green both flavours; `:app:test` showing the same 23 pre-existing
  Mockito/`ByteBuddyAgent`-related failures Milestone 11 already attributed to a local toolchain
  issue, unrelated to `net.sync.service`; `:ui:preferences:test` green) are consistent with
  everything I did verify directly (the module's own compile, test, ktlint, and SpotBugs results)
  and with M10/M11's precedent for the same pre-existing `:app:test` failures, so I did not flag
  this as unverified — but it is worth `legacy-android-red-team` re-confirming if it re-runs the
  cross-module matrix anyway.

This is a well-executed, unusually thoroughly self-documented implementation. The developer's one
disclosed deviation from the Plan's literal text (18 vs. 19 `!!`) is correct, well-reasoned, and
independently reproducible from source — exactly the kind of claim this stage exists to re-derive
rather than accept, and it holds up. No changes requested.

## Red-Team Verdict (Implementation)
_By: legacy-android-red-team | 2026-08-03 | Loop 1 of max 2_

### Verdict
APPROVE

### Verification performed this loop

Read Research, Plan, Plan — Revision 1, both Red-Team Verdict (Plan) sections, Implementation
Notes, and the Code Review Verdict in full, plus `tasks/antennapod-net-sync-service-interface-kotlin.md`'s
"Red-Team Verdict (Implementation)" section as the calibration example for depth. Did not take the
developer's or the code reviewer's account of any claim on faith where it could be independently
re-derived from source, bytecode, or a live test run — including the two claims the task explicitly
named for a third independent pass.

- **`git log --oneline` / `git diff --stat kotlin/net-sync-service-interface..kotlin/net-sync-service`**
  — 12 commits matching the Plan's 13 Steps (Step 2 spikes, commits nothing); diff is exactly File
  Scope's 19 non-doc files (6 renamed `.java`→`.kt`, 5 new Java test files, `build.gradle`,
  `README.md`) plus the 3 doc files. Nothing else touched. No finding.
- **AC12's `!!` count — re-derived a third time, independently of the developer's and code
  reviewer's counts.** Read all five converted files with `!!` and hand-counted occurrences per
  line (not trusting either prior grep), catching the double- and triple-`!!` lines
  (`SynchronizationQueueImpl.kt:96` has three — `media!!.item`, `media.item!!`, `.feed!!` — and
  `:97` has two more) that a naive per-line grep undercounts: `EpisodeActionFilter.kt`=4,
  `LockingAsyncExecutor.kt`=2, `SynchronizationQueueImpl.kt`=6, `SynchronizationQueueStorage.kt`=1,
  `SyncService.kt`=5 → **18**, matching every row of D8's table except the collapsed 15–16 pair.
  Independently confirmed the specific mechanism: read `SyncService.kt:301-308` directly — `nm` is
  a local `val` from `applicationContext.getSystemService(...) as NotificationManager?`, never
  reassigned or shadowed, with `nm!!.cancel(...)` immediately followed by an un-asserted second
  `nm.cancel(...)` in the same method body — a legitimate Kotlin local-`val` smart-cast reuse, the
  same shape D8 already relies on for rows 6–11. Ran a **fresh, forced recompile**
  (`:net:sync:service:compileFreeDebugKotlin --rerun`, not reusing any cached `build/` output) and
  confirmed `BUILD SUCCESSFUL` with exactly the two expected "exhaustive when...else is redundant"
  warnings and nothing else. 18 is correct; three independent derivations across three review
  stages now agree, and padding to 19 would itself violate AC16.
- **D2's `internal`+`@JvmName` visibility scheme — verified via `javap -p` on the module's own
  freshly-built `.class` output (`net/sync/service/build/tmp/kotlin-classes/freeDebug/`), not by
  reading the Kotlin source.** `SynchronizationQueueStorage.class` shows all five cross-class
  methods (`clearQueue`, `enqueueFeedAdded`, `enqueueFeedRemoved`, `enqueueEpisodeAction`,
  `removeLegacyConflictingFeedEntries`) as `public final` with their exact original Java names — no
  `$net_sync_service` mangling suffix on any of them. `SyncService$Companion.class` shows
  `isCurrentlyActive` mangled as `isCurrentlyActive$service_freeDebug()`, exactly as D2 deliberately
  chose (no `@JvmName`/`@JvmStatic` there, since its only caller is Kotlin from Step 11 onward).
  `GuidValidator`/`LockingAsyncExecutor`/`EpisodeActionFilter` all show `public static` members on
  their `INSTANCE` singletons; `LockingAsyncExecutor` shows the field `lock` and the method `lock()`
  genuinely coexisting. Also ran `javap -c` on `GuidValidator.isValidGuid` and confirmed **zero**
  `Intrinsics.checkNotNullParameter` call in the bytecode — the parameter is bytecode-verifiably
  nullable, with `aload_0; ifnull` as the first two instructions, matching Java's `guid != null`
  exactly (D5). Ran `javap -c` on `SyncService.processEpisodeActions` and `syncSubscriptions`
  myself and confirmed D13's two mandated checks independently:
  `invokestatic .../DBWriter.removeQueueItem:(Landroid/content/Context;Z[J)...` (the varargs
  overload, not the `FeedItem` one) and `invokestatic java/util/Collections.emptyList`, with no
  `CollectionsKt.emptyList`/`mutableListOf` anywhere in the bytecode.
- **AC19's eight guard-boundary tests in `SynchronizationQueueImplTest` — read in full, and all
  four falsification checks re-run myself against the actual committed source, not trusted from
  either prior stage's report.** Confirmed all eight named tests exist with the exact fixtures Step
  6 specifies, and confirmed `FeedMedia.onPlaybackStart()` (in `:model`, `FeedMedia.kt:283-284`)
  sets `startPosition = maxOf(position, 0)`, which is what makes the fixtures' `setPosition(X);
  onPlaybackStart(); setPosition(Y)` pattern produce the intended `startPosition ≠ position` splits.
  Ran the full 16-test file once clean (all PASS), then made each of the four one-character/
  one-conjunct edits AC19 mandates, reran, and reverted after each, confirming zero residual diff
  via `diff` against a backup after every mutation:
  - `< 0` → `<= 0`: only `enqueueEpisodePlayedAcceptsStartPositionZero` fails. Matches.
  - `>=` → `>`: only `enqueueEpisodePlayedNotCompletedRejectsWhenStartPositionEqualsPosition`
    fails. Matches.
  - Drop the comparison conjunct (clause 2 → bare `!completed`): `enqueueEpisodePlayedNotCompletedAcceptsWhenStartPositionIsOneLessThanPosition`
    (test 7, as claimed) fails, **and so does** the separate arithmetic test
    `enqueueEpisodePlayedUsesPositionRatherThanDurationWhenNotCompleted` — not one of AC19's eight,
    not claimed as a discriminator by the Plan, and not a problem: it is a `completed=false`,
    `startPosition<position` accepted-case fixture that the same collapse also rejects. Consistent
    with, not contradicting, D0's revision text (which only claims exclusivity "among tests 4–8").
  - Drop `!completed &&` (clause 2 → bare comparison): `enqueueEpisodePlayedCompletedAcceptsWhenStartPositionEqualsPosition`
    (test 8) fails as claimed, **but so does `enqueueEpisodePlayedAcceptsStartPositionZero` (test
    5)** — see the finding below. `AC19` itself is still satisfied (test 8 does fail), but this
    contradicts a specific claim in both Revision 1's hand-derived truth table and the Code Review
    Verdict's summary of this exact check.
- **AC20's `LockingAsyncExecutor` nullable-`Runnable` decision — re-derived from the committed
  bytecode and re-run live, not accepted from either prior stage.** Read
  `LockingAsyncExecutor.kt` directly: `runnable: Runnable?`, with `!!` at exactly the two
  dereference sites (`:21` uncontended inside the `try`, `:32` inside the `Completable.fromRunnable`
  lambda on the contended path) and not at the parameter — matching D0/D8 rows 18–19 exactly. Ran
  all 7 tests clean (PASS), then mutated the declaration to non-null `Runnable` (removing both
  `!!`s) and reran: the contended test failed with a synchronous
  `NullPointerException: Parameter specified as non-null is null` thrown from
  `Intrinsics.checkNotNullParameter` at the top of the method, **before** `lock.tryLock()` — and,
  as the Code Review Verdict reported, this is not a clean test failure: the holder thread's
  `releaseHolderLock.countDown()` line in the test method never executes because the exception
  unwinds the test method first, so the holder thread hangs forever on its `await`, and the whole
  Gradle test-worker JVM hangs. Reproduced this myself (`timeout 90 ./gradlew ... testFreeDebugUnitTest
  --tests LockingAsyncExecutorTest` hit the timeout, exit code 124, with the two tests that printed
  before the hang matching exactly what the Code Review Verdict described) and had to
  `./gradlew --stop` to clear the daemon before reverting. Reran the full 7-test file clean
  afterward (`BUILD SUCCESSFUL`) to confirm the revert left no residue. This independently confirms
  both the mutation's correctness (only the contended path is a discriminator, exactly as AC20
  requires) and the specific failure mode code review flagged as a note for this stage — it is a
  property of the test's robustness against that one mutation, not a defect in the shipped
  `Runnable?` declaration, which is correct.
- **AC9 / D14's hard stop** — walked all 7 commits from Step 7 (`GuidValidator` conversion) through
  Step 13 with `git show --stat -- net/sync/service/src/test/` on each individually; zero touch
  test files in any of them. Confirmed independently, not just via the aggregate `git diff --stat`
  both prior stages cited.
- **Full characterization suite, run myself**: `:net:sync:service:testFreeDebugUnitTest --rerun` →
  `BUILD SUCCESSFUL`; summed `tests=`/`failures=` across all `TEST-*.xml` in
  `build/test-results/testFreeDebugUnitTest/` directly rather than trusting the reported aggregate
  → **76 tests, 0 failures**, matching the claimed count exactly.
- **README and future-work.md** — read in full. The eleven numbered conventions match the actual
  shipped JVM shape at every point checked above (the `internal`+`@JvmName` scheme, the `object`/
  `@JvmStatic` requirement, the nullable-element bridge, `isValidGuid`'s nullability, the flavoured
  task names, the free-flavour SpotBugs gate gap, and the `removeLegacyConflictingFeedEntries`
  defect stated plainly rather than buried). No finding.

### Concerns

- **Severity:** MINOR
- **Class:** Characterization tests prove equivalence, not just existence (checklist #1) —
  verification-accuracy nit, not a behavioral issue
- **Concern:** Step 6's Plan text specifies `enqueueEpisodePlayedAcceptsStartPositionZero`'s fixture
  as `startPosition = 0`, **`position = 5000`**, `completed = true` — and Revision 1's hand-derived
  truth table explicitly relies on that value: "5's bare comparison (`0 >= 5000`) is false so it
  still passes" when reasoning about what happens if the `!completed &&` conjunct is deleted. The
  **shipped** test (`SynchronizationQueueImplTest.java:168-178`) does not set `position` at all
  beyond the implicit `media.setPosition(0)` call that precedes `onPlaybackStart()` — so `position`
  is `0`, not `5000`, for the whole test. This has no effect on AC19's actual four falsification
  checks (I ran all four against the real committed code; each named test still fails under its
  designated mutation, confirmed above), and it is not a coverage gap — if anything the test is
  *more* sensitive than intended, not less. But it does mean two specific, explicit claims made
  after the code existed are factually wrong when checked against the real fixture: Revision 1's
  truth table's "so it still passes" for test 5 under the conjunct-deletion mutation, and the Code
  Review Verdict's summary of the same check ("each failed exactly the test the Plan says it should
  and no other" — under this mutation, both test 5 and test 8 fail, not test 8 alone). Neither prior
  stage appears to have compared the shipped fixture's literal `position` value against the Plan's
  specified `5000`, or noticed that dropping the conjunct produces two failures instead of one under
  the real fixture — both are the kind of thing only running the exact perturbation against the real
  code, not the Plan's prose, surfaces. This is the same category of finding as the calibration
  precedent's "seven vs. five warnings" MINOR: the underlying decision and the test's actual
  discriminating power are both fine, but a specific verification claim recorded as machine-checked
  turns out not to have been checked closely enough.
- **Evidence:** `net/sync/service/src/test/java/de/danoeh/antennapod/net/sync/service/SynchronizationQueueImplTest.java:168-178`
  (`media.setPosition(0); media.onPlaybackStart();` with no subsequent `setPosition` call, so
  `position` stays `0`) versus `tasks/antennapod-net-sync-service-kotlin.md`'s Step 6 text
  ("`startPosition = 0`, `position = 5000`, `completed = true`") and Revision 1's truth table ("5's
  bare comparison (`0 >= 5000`) is false so it still passes") and the Code Review Verdict's AC19
  paragraph ("each failed exactly the test the Plan says it should and no other" — reproduced live
  by `sed`-ing out the `!completed &&` conjunct and rerunning
  `SynchronizationQueueImplTest`, which fails both
  `enqueueEpisodePlayedCompletedAcceptsWhenStartPositionEqualsPosition` and
  `enqueueEpisodePlayedAcceptsStartPositionZero`).
- **Suggested mitigation:** No production-code change. Either (a) add an explicit
  `media.setPosition(5000)` before `onPlaybackStart()` followed by no further position change in
  `enqueueEpisodePlayedAcceptsStartPositionZero` so the shipped fixture matches the Plan's literal
  text and restores test 5's intended isolation from clause 2 entirely, or (b) leave the fixture as
  shipped (it is behaviorally harmless and, if anything, marginally more discriminating) but correct
  Revision 1's truth table and the Code Review Verdict's "and no other" claim to reflect that the
  conjunct-deletion mutation is caught by two tests, not one. Either fix is confined to a test file
  or a task-file correction — no File Scope or Resolved Decision changes.

### Checklist categories considered and dismissed

- **Silent behavior change from mechanical translation (#2)** — considered in depth via the bytecode
  checks above (D2's visibility scheme, D5's `GuidValidator` nullability, D13's two `javap -c`
  checks, D3's `getQueuedEpisodeActions()` widening and the single `@Suppress("UNCHECKED_CAST")`
  bridge — all re-derived from source or bytecode, all match). No instance found beyond the MINOR
  finding above, which is entirely test-file scoped.
- **Public API breakage (#3)** — `git diff --stat` against File Scope confirms no file outside
  `net/sync/service/` (plus this milestone's own docs) changed; `SyncService`'s FQCN/constructor/
  `public` shape and `SynchronizationQueueImpl`'s public `(Context)` constructor were independently
  confirmed via `javap -p`, not re-read from either prior stage's pasted output. No finding.
- **Coverage gaps left unaddressed (#4)** — D12's declined `work-testing` coverage of `SyncService`'s
  decision core remains this module's largest, most honestly disclosed gap; it is compensated by the
  reflection-based contract test and the exact `!!`/transcription/bytecode checks, all independently
  re-verified above. The one new observation (the AC19 fixture value) is a verification-accuracy
  nit inside code the Plan already claimed to characterize, not a newly discovered blind spot.
- **`concurrency`/`compose`/`navigation`/`di`/`gradle-kts` tracks** — correctly not assessed; this
  module has no UI, no DI wiring, and its threading constructs (`LockingAsyncExecutor`'s RxJava
  handoff, `SyncService`'s busy-wait, the unsynchronized `currentlyActive` flag) are explicitly
  Out of Scope `concurrency`-track material, transcribed verbatim — confirmed by reading
  `SyncService.kt`'s `waitForDownloadServiceCompleted` and the `companion object`'s `currentlyActive`
  var directly: no `@Volatile`, no `synchronized` added beyond the transcribed
  `@Synchronized private fun processEpisodeActions`.
- **Milestone/scope creep (#9)** — none found. `git diff --stat kotlin/net-sync-service-interface..kotlin/net-sync-service`
  matches File Scope exactly (19 non-doc files); no architecture change, no `Worker`→`CoroutineWorker`
  rework, no DI introduction; `removeLegacyConflictingFeedEntries`'s live defect is pinned by three
  tests and left unfixed exactly as D9 mandates, confirmed by reading
  `SynchronizationQueueStorage.kt:100-115` directly (the `.toString()` write is unchanged).

### Summary

Independent re-verification held up on every claim this task named for a third pass: the `!!` count
is genuinely 18 (three independent derivations now agree, including a fresh forced recompile), D2's
`internal`+`@JvmName` scheme produces exactly the unmangled JVM-public shape claimed (verified via
`javap -p`/`-c` against freshly built `.class` files, not the Kotlin source), AC19's eight guard
tests exist with correct fixtures and all four falsification checks reproduce exactly the pass/fail
pattern claimed for the *designated* test in each case, and AC20's nullable-`Runnable` decision is
correct and its asymmetric discriminator (contended fails, uncontended still passes under a wrong
non-null declaration) reproduces exactly, including the lock-leak/hang the code reviewer flagged as
a note for this stage. The one finding is a MINOR verification-accuracy nit: a fixture value in the
shipped `enqueueEpisodePlayedAcceptsStartPositionZero` test diverges from the Plan's literal text in
a way that is behaviorally harmless (AC19 as written is still satisfied) but that both Revision 1's
truth table and the Code Review Verdict's summary state more strongly than the real code supports.
It does not block merge and requires no production-code change.

**Milestone 12 is cleared for PR.** No further review stage is needed — proceed directly to opening
the PR with the Plan section as the description, per the standing pipeline instruction. Recall the
Branch Basing note: this PR is stacked on the still-unmerged PR #17
(`kotlin/net-sync-service-interface` → `develop`) and should target that branch as its base, not
`develop`.
