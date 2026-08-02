# :net:sync:service-interface

This module contains the interface for starting the sync service.
Defines `ISyncService` for backend implementations and `SynchronizationQueue` for queuing local changes before upload.

## Kotlin conversion conventions (kotlin migration track, Milestone 11)

All nine production files are Kotlin as of Milestone 11. All 28 Java call sites across seven
consuming modules compile unedited against the converted API. Conventions a future edit to this
module must preserve:

1. **`SynchronizationProvider.fromIdentifier(provider: String?): SynchronizationProvider?` — the
   parameter must stay nullable.** `SyncService.doWork()` calls this with
   `SynchronizationSettings.getSelectedSyncProviderKey()`, which is genuinely `null` for every
   user who has never configured sync. A non-null parameter compiles clean (the one unguarded
   Java call site at `SynchronizationPreferencesFragment`'s equivalent is Java, so the compiler
   won't warn) and crashes the sync worker on every invocation for that entire population.
   Tightening this is only safe once every caller is itself Kotlin and the compiler can enforce
   non-null at the call site — not before.
2. **`UploadChangesResponse.timestamp` must stay `@JvmField`, never a plain `val`.** Three Java
   subclasses in `:net:sync:gpoddernet` depend on it being a genuine `public final long` field:
   two read it qualified, one reads it unqualified via Java field inheritance. A plain `val`
   emits a private backing field plus `getTimestamp()` and breaks all three at compile time.
3. **`EpisodeAction`'s four static `Action` aliases (`NEW`/`DOWNLOAD`/`PLAY`/`DELETE`) must stay
   `@JvmField val` in the companion, never `const val`.** Their type is the nested `Action` enum,
   not a primitive or `String`, so `const val` is not available for them — unlike a primitive/
   `String` constant, which should use `const val` instead.
4. **`ISyncService`'s six methods must all carry `@Throws(SyncServiceException::class)`,
   mechanically, with no reachability analysis.** This is a standing convention for this case
   study, not specific to this interface: any converted Java interface whose methods declare
   `throws` and which is implemented by a Java class must carry `@Throws` on every such method.
   Omitting it breaks the Java implementor's override (illegal exception widening) and makes any
   `catch` around a call through the interface type unreachable — and the day an implementor
   becomes Kotlin too, the missing `@Throws` stops failing loudly at all.
5. **`uploadSubscriptionChanges`/`uploadEpisodeActions` need `@JvmSuppressWildcards` at function
   level.** Kotlin's `List<out E>` emits a wildcarded generic signature
   (`List<? extends EpisodeAction>`) for a `List<T>` parameter whenever `T` is a non-final class.
   For as long as any type parameter here is a non-final Java class, its absence breaks the two
   Java implementors with "same erasure, yet neither overrides the other." Harmless to leave on
   permanently even now that `EpisodeAction` is final Kotlin.
6. **`SynchronizationQueue.getInstance()`/`setInstance()` and `EpisodeAction.readFromJsonObject`
   need `@JvmStatic`.** Without it, Java callers would need to go through a `Companion` object
   instead of calling the static method directly on the outer class.
7. **`SyncServiceException.serialVersionUID` must be `private const val` inside a `companion
   object`, not a plain `val`.** This is what makes it emit as `private static final long` on the
   exception class itself rather than as an instance field-plus-getter or a field on a nested
   `Companion` class. Two Java subclasses carry their own `serialVersionUID`, so serialization
   compatibility across the hierarchy is observable.
8. **Test tasks are unflavoured**: `testDebugUnitTest` / `testReleaseUnitTest`, aggregated by
   `test`. This module does not apply `playFlavor.gradle` (unlike its sibling `:net:sync:service`)
   — do not copy flavoured task names from a module that does.
9. **The characterization suite (`src/test/`) is Java by design and must stay Java.** All 28
   external consumers of this module are Java, so a Java test suite is the equivalence oracle for
   Java-calling-Kotlin binary compatibility. Four of the nine test files are compile-time guards —
   they fail to *compile*, not just fail at runtime, if a JVM-shape decision above regresses
   (`UploadChangesResponseTest`'s unqualified field read, `ISyncServiceTest`'s `throws`
   declarations, `SyncServiceExceptionTest`'s `super(...)` calls, `SynchronizationQueueTest`'s
   null-argument stub calls). Converting this suite to Kotlin is a legitimate future milestone but
   must preserve those four files' Java-oracle property.
10. **Only `EpisodeActionJsonTest` and `EpisodeActionCharacterizationTest` use Robolectric.** It
    is required because `org.json.JSONObject` is an Android-framework stub under plain JUnit — not
    because of `android.text.TextUtils`/`android.util.Log`, which this module also uses but which
    don't force it. The other seven test files run on plain JUnit; don't add Robolectric to them.
11. **`EpisodeAction.equals()` has a known, deliberately-unfixed defect.** `action != that.action`
    should read `action == that.action` (every sibling clause in the method is an equality test).
    Two field-identical instances compare unequal; two instances differing only in `action`
    compare equal despite having different hash codes. Nothing in the repository calls
    `EpisodeAction.equals` today. It is pinned by three tests in `EpisodeActionCharacterizationTest`
    and tracked as its own future task in `tasks/antennapod-model-kotlin-future-work.md` (item 9)
    — do not "fix" it as a drive-by change; that is a behavior change requiring its own review.
