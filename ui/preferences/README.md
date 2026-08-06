# :ui:preferences

This module provides the settings screen.
Built with `PreferenceFragmentCompat`; reads and writes settings via `:storage:preferences`.

## Kotlin conversion conventions — `screen/synchronization/`

All four files in `screen/synchronization/` (`AuthenticationDialog`, `GpodderAuthenticationFragment`,
`NextcloudAuthenticationFragment`, `SynchronizationPreferencesFragment`) are Kotlin. `:storage:preferences`
(`SynchronizationSettings`, `SynchronizationCredentials`) is still Java and carries no nullability
annotations, so every getter on it arrives here as an unannotated platform type — treat each one as
genuinely nullable unless the call site proves otherwise (most do return `null` on a fresh install).
Conventions a future edit to this slice must preserve:

1. **`AuthenticationDialog` is subclassed from `:app`, not slice-private.** Two Java classes outside
   this module extend it. Its constructor stays `(Context, Int, Boolean, String?, String?)`, `onCancelled()`
   stays `protected open`, and `onConfirmed(username: String, password: String)` stays `protected abstract`.
   `passwordHidden` is `private` — no external reference exists, Java or Kotlin.
2. **`GpodderAuthenticationFragment` stays `open`.** Nothing in `:app` subclasses it, but this module's
   own characterization suite does (to observe `dismiss()` ordering), and Kotlin classes are `final` by
   default unlike Java's implicit-open. Narrowing it back to `final` breaks that test.
3. **`GpodderAuthenticationFragment.TAG` and `NextcloudAuthenticationFragment.TAG` must stay `const val`
   in a `companion object`, never a plain `val` or `@JvmField`.** `const val` is what emits the field onto
   the *outer* class as `public static final String`, which is what Java callers (and `javap -p`) actually
   see — a plain `val` in the companion would only be reachable via `Companion.TAG`.
4. **Every `!!` in this slice is a deliberate preserved crash, not an oversight.** Each one either
   reproduces an NPE the pre-conversion Java already threw unchecked at the same statement, or satisfies
   Kotlin's null-checker for a value that is always non-null by construction (e.g. a `var` field read
   inside the same function it was just assigned in, where Kotlin does not smart-cast mutable properties
   the way Java's lack of null-checking silently allowed). Do not soften any of them to `?.` or replace
   them with `requireContext()`/`requireActivity()`/`requireView()` — the "require" helpers throw
   `IllegalStateException`, a different exception type than the `NullPointerException` the original code
   throws, which is exactly the kind of change this rule exists to prevent. Where Android Lint's
   `UseRequireInsteadOfGet` check disagrees, the correct fix is `@SuppressLint("UseRequireInsteadOfGet")`
   at the point of use, not the "require" helper.
5. **Three known defects are pinned, not fixed:** `SynchronizationPreferencesFragment.getProviderSummary`/
   `getProviderIcon` NPE on an unrecognised persisted provider key (their `when` branches keep an `else ->`
   that is currently unreachable dead code, intentionally, for exactly this case); the `devices` field in
   `GpodderAuthenticationFragment` is not `@Volatile` while its siblings (`username`, `password`,
   `selectedDevice`) are, a pre-existing data race; and the anonymous `ArrayAdapter` in
   `chooseProviderAndLogin` keeps its `ViewHolder` as a field of the adapter, reassigned on every
   `getView()` call, rather than the usual view-tag-only recycling pattern. All three are pinned by
   characterization tests in `src/test/java/.../screen/synchronization/`; do not "fix" any of them as a
   drive-by change alongside unrelated work.
6. **`AntennapodHttpClient.getHttpClient()` is not constructible under default Robolectric.** It builds an
   `okhttp3.Cache` against a `cacheDirectory` that is only ever set via
   `AntennapodHttpClient.setCacheDirectory(...)`, which nothing in this module's test setup calls. Any new
   test that reaches this path should expect a `NullPointerException` and assert around it rather than
   assume it succeeds.
7. **The characterization suite drives real `Fragment`s and `DialogFragment`s through a test-only host
   Activity, themed `Theme.AntennaPod.Light`.** The theme is required for `preferenceTheme` to resolve when
   a `PreferenceFragmentCompat` inflates. The host activity exists only under `src/test/`; it must never be
   declared in `src/main`'s manifest or a `src/debug` manifest, which would ship it in the app.
8. **Test tasks in this module are flavoured** (`testFreeDebugUnitTest` / `testPlayDebugUnitTest`), because
   this module applies `playFlavor.gradle`. CI only runs the Play-flavoured task for this module — run both
   locally.
9. **`screen/synchronization/` also contains a Robolectric native-graphics capture test.** Any test in this
   source set that draws a real `Bitmap` from a view hierarchy must carry
   `@GraphicsMode(GraphicsMode.Mode.NATIVE)` at class level — the default `LEGACY` mode silently fills the
   bitmap with an opaque solid colour instead of failing, so a bare `assertNotNull(bitmap)` or
   non-transparent-pixel check passes on a completely blank render. Assert on distinct-colour count (or an
   equivalent content check) instead. Such a test's PNG output is written under this module's `build/`
   directory on every run and is never asserted byte-for-byte against a checked-in image — cross-machine
   rendering determinism (font rasterisation, in particular) is not guaranteed.
