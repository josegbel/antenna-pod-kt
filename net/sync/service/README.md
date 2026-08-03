# :net:sync:service

This module contains the sync service.
`SyncService` coordinates the active backend; `SynchronizationQueueStorage` persists the pending-changes queue in SharedPreferences.

## Kotlin conversion conventions (kotlin migration track, Milestone 12)

All six production files are Kotlin as of Milestone 12. This module has exactly one inbound
source call site (`app/src/main/java/de/danoeh/antennapod/ClientConfigurator.java:53`) and one
dead Gradle dependency (`ui/preferences`), so — unlike every other module in this case study — no
other module's compile or test suite contributes any equivalence evidence here. The five-file
Java characterization suite in `src/test/` is the entire proof. Conventions a future edit to this
module must preserve:

1. **`SyncService`'s fully-qualified class name, `public` modifier, non-abstract shape, and
   `(Context, WorkerParameters)` constructor must never change.** WorkManager persists the FQCN
   string in its own on-disk database and reflectively instantiates the class by that name and
   constructor signature. Renaming the class, moving it to another package, making it `internal`,
   or changing the constructor signature fails to instantiate any work already scheduled on an
   upgrading user's device. Nothing in this build checks this — it is proven only by
   `SyncServiceWorkerContractTest`'s reflection.
2. **The `"synchronization"` SharedPreferences file and its three keys
   (`sync_queued_episode_actions`, `sync_added`, `sync_removed`) are an on-disk contract with
   every existing installation.** Renaming any of them silently discards the user's un-uploaded
   listening history and pending subscription changes on upgrade. `SynchronizationSettings` (in
   `:storage:preferences`) shares the same `"synchronization"` file name for its own, unrelated
   keys — `clearQueue()`'s extra `SynchronizationSettings.resetTimestamps()` call is what makes
   that sharing observable.
3. **`SynchronizationQueueStorage`'s five cross-class methods (`clearQueue`, `enqueueFeedAdded`,
   `enqueueFeedRemoved`, `enqueueEpisodeAction`, `removeLegacyConflictingFeedEntries`) are
   `internal` with an explicit `@JvmName` matching their original Java name, not plain
   `internal`.** Plain `internal` mangles the emitted JVM method name
   (e.g. `clearQueue$net_sync_service`) — harmless once every caller in the module is Kotlin, but
   this module's own Java characterization suite calls two of them by exact name via reflection
   (`SyncServiceWorkerContractTest`) and would fail to compile/resolve against a mangled name. If
   a future change ever needs a sixth such method, give it the same treatment: `@JvmName` on an
   `internal` function, not plain `internal`.
4. **`GuidValidator`, `LockingAsyncExecutor`, and `EpisodeActionFilter` are Kotlin `object`s with
   `@JvmStatic` on every externally-called member, never top-level functions.** A top-level
   function would move the symbol to a synthetic `*Kt` facade class and break the two pre-existing
   Java test files (`GuidValidatorTest`, `EpisodeActionFilterTest`) at compile time.
5. **`LockingAsyncExecutor`'s private `ReentrantLock` property and its public `lock()` function
   share the name `lock`.** This is legal — Kotlin properties and functions occupy different
   namespaces — and preserves the exact same naming Java already had (a field and a method both
   named `lock`). Do not rename either to "fix" the apparent collision.
6. **`SynchronizationQueueStorage.getQueuedEpisodeActions()` returns `ArrayList<EpisodeAction?>`,
   not `ArrayList<EpisodeAction>`.** A stored queue entry missing `podcast`/`episode`/`action`, or
   carrying an unrecognised action string, parses to `null` and is added to the list unguarded —
   exactly as the pre-conversion Java did. This meets `ISyncService.uploadEpisodeActions`'s
   non-null-element parameter (declared in `:net:sync:service-interface`, Milestone 11) via a
   single `@Suppress("UNCHECKED_CAST")` cast in `SyncService.syncEpisodeActions` — a bare
   `CHECKCAST` that inspects no element. Do not replace it with `filterNotNull()` (silently drops
   a user's malformed queue entry instead of the `NullPointerException` production has always
   thrown from that path) or widen `ISyncService`'s parameter type (that interface is out of File
   Scope for this module and consumed by two Java backends).
7. **`GuidValidator.isValidGuid`'s parameter is `String?`, non-negotiably.** It is called with
   `action.guid`, which is nullable (`EpisodeAction.kt`, Milestone 11), and
   `GuidValidatorTest.java:16` passes a literal Java `null` — the one hazard in this module an
   existing test already caught for free.
8. **Test tasks are flavoured**: `testFreeDebugUnitTest` / `testFreeReleaseUnitTest` /
   `testPlayDebugUnitTest` / `testPlayReleaseUnitTest`, aggregated by `test`. This module applies
   `playFlavor.gradle` (unlike its sibling `:net:sync:service-interface`) — do not copy unflavoured
   task names from a module that doesn't apply it.
9. **The free-flavour SpotBugs gate does not fail the build.** `common.gradle`'s SpotBugs
   `doLast` only parses `debug.xml`/`playDebug.xml`; a flavoured module like this one emits
   `freeDebug.xml`/`playDebug.xml`, so `spotbugsFreeDebug`'s findings are generated but never
   gate `./gradlew lint`. Run `spotbugsFreeDebug` and `spotbugsPlayDebug` explicitly (as two
   separate Gradle invocations — running both together races on the shared XML-parsing `doLast`)
   before trusting a "clean" result for this module.
10. **The characterization suite (`src/test/`) is Java by design and must stay Java.** This
    module's only inbound caller is a constructor call, so the Java suite — not a cross-module
    compile — is the entire equivalence oracle. Two of the five new test files are compile-time
    guards, not just runtime ones: `LockingAsyncExecutorTest` fails to *compile* without
    `@JvmStatic` on all three `LockingAsyncExecutor` entry points, and `EpisodeActionFilterCharacterizationTest`
    fails to compile if `androidx.core.util.Pair` is ever replaced with `kotlin.Pair`. The two
    pre-existing files (`GuidValidatorTest`, `EpisodeActionFilterTest`) must never be modified —
    they are the milestone's proof that this conversion changed nothing observable.
11. **`SynchronizationQueueStorage.removeLegacyConflictingFeedEntries` has a known, live,
    deliberately-unfixed defect.** It writes `Collection.toString()` — e.g.
    `"[https://a/feed.xml, https://b/feed.xml]"` — into two SharedPreferences keys that every
    other read path parses with `org.json.JSONArray`, which throws on real feed URLs (their `://`
    breaks `JSONTokener`'s lenient bare-word parsing) and silently empties both queues. This is
    reached whenever a subscription upload throws `SyncServiceException`
    (`SyncService.syncSubscriptions`'s `catch` block). It is pinned by three tests in
    `SynchronizationQueueStorageTest` and tracked as its own future task in
    `tasks/antennapod-model-kotlin-future-work.md` (item 11, alongside the data migration existing
    corrupt installs would need) — do not "fix" it as a drive-by change.
