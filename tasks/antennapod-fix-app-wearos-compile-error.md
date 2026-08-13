# antennapod-fix-app-wearos-compile-error

> **Description:** Fix a Kotlin compile error in `:app-wearos` (`EpisodeDetailActivity.kt:115`, `Argument type mismatch: actual type is 'String?', but 'String' was expected`) that has kept `develop`'s `Checks` CI workflow red for at least 8 consecutive runs (2026-07-26 through 2026-08-12), failing at `:app-wearos:compileFreeDebugKotlin` before the `static-analysis` job's SpotBugs step ever runs. Discovered as a side effect of researching a sibling SpotBugs task (`antennapod-fix-net-download-service-spotbugs-debt`).
> **Repo:** `services/android-migration/projects/portfolio/antennapod`
> **Created:** 2026-08-12

> **Pre-research context (carried over from the parent research conversation — do not re-derive):**
> - **Why this exists.** `legacy-android-researcher`, researching `tasks/antennapod-fix-net-download-service-spotbugs-debt.md`, ran a repo-wide `./gradlew --continue spotbugsPlayDebug spotbugsDebug` on current `develop` (`5ae7d560f`) to confirm that pile's baseline, and separately confirmed via `gh run view 31592660967` that the actual CI `Checks` workflow fails at `:app-wearos:compileFreeDebugKotlin` — never reaching any SpotBugs task at all. `gh run list --branch develop --workflow Checks --limit 8` shows **eight consecutive failures back to 2026-07-26**. This means `checks.yml`'s `static-analysis` job has not produced a real signal in the visible CI history, independent of either SpotBugs debt pile.
> - **Root cause, already diagnosed (verify, don't re-derive from scratch).** `app-wearos/src/main/java/de/danoeh/antennapod/wearos/EpisodeDetailActivity.kt:115:28` calls `Text(text = item.title)`, where `FeedItem.title` is `var title: String? = null` (`model/src/main/java/de/danoeh/antennapod/model/feed/FeedItem.kt:30`). Pre-migration, `FeedItem` had `private String title` with an unannotated getter (`git show b71cb7942^` — the commit that migrated `:model` to Kotlin, portfolio Milestones 1-7) — an unannotated Java platform type that `:app-wearos`'s then-Java or early-Kotlin code accepted silently. This is the **same J2K nullability-widening mechanism** documented in both sibling SpotBugs tasks, manifesting a third time, one module further out. `:app-wearos` is upstream/legacy code (last touched `5fe70196e`/`b8f8426c8`, 2026-05-14) that no portfolio milestone has revisited or recompiled since the `:model` migration landed.
> - **Sequencing impact.** This is the *third* independent blocker on `checks.yml`'s `static-analysis` job, and the most severe: unlike the two SpotBugs piles (which fail a job step but let the workflow run), this is a hard compile error that stops the `static-analysis` job before it reaches the `Checkstyle, Lint, SpotBugs` step at all (`.github/workflows/checks.yml:44-45` — `./gradlew checkstyle lint`, and `:app-wearos:lint` transitively depends on `:app-wearos:compilePlayDebugKotlin`, confirmed via `./gradlew :app-wearos:lint --dry-run`). All three fixes — this one, `antennapod-fix-spotbugs-static-analysis-debt` (merged, PR #23), and `antennapod-fix-net-download-service-spotbugs-debt` (research done, not yet planned) — must merge before `develop` gets a green `Checks` run, and therefore before Milestone 15b's PR #22's AC13 can be satisfied.
> - **José's decision (2026-08-12):** scope this as its own third, independent follow-up task, sequenced alongside (not blocking, not blocked by) the two SpotBugs tasks. All three are standalone repo-hygiene fixes triggered by different manifestations of the same underlying J2K-widening pattern, in three different modules, with three different failure shapes (Java-interop nullability findings, Java-interop nullability findings, Kotlin-to-Kotlin compile error).
> - **Scope caution.** `:app-wearos` is otherwise untouched by this portfolio's migration work — confirm during Research whether this is truly a single-line fix (e.g., `item.title ?: ""`, or reading whether `title` should be treated as always-non-null in production like the `FeedFunding.content` precedent in the sibling task) or whether it surfaces other latent issues in a module nobody has looked at recently. Per `AGENTS.md`'s minimal-diff rule, fix only what's needed to make `:app-wearos:compilePlayDebugKotlin` succeed — do not do a broader `:app-wearos` health pass unless something forces it.
> - This is unaffiliated OSS portfolio case-study work — see `services/android-migration/projects/portfolio/README.md`. Standalone repo hygiene, not part of the Sync Settings vertical-slice sequence (Milestones 15-20).

## Research
_Last updated by: legacy-android-researcher | 2026-08-12_

### Summary

The preliminary diagnosis is **confirmed, exactly and currently**. On `origin/develop` (`5ae7d560f`), `./gradlew :app-wearos:compileFreeDebugKotlin` fails with one error and only one error: `EpisodeDetailActivity.kt:115:28 Argument type mismatch: actual type is 'String?', but 'String' was expected`. `EpisodeDetailActivity.kt:115` passes `item.title` to Wear Compose `Text(text: String)`, and `FeedItem.title` is `var title: String? = null` (`FeedItem.kt:30`) since this portfolio converted it in `b71cb7942`. Pre-migration it was `private String title` with an unannotated `public String getTitle()` (`git show b71cb7942^:.../FeedItem.java:32,215-217`) — a platform type Kotlin accepted silently. `:app-wearos` is a standalone Wear OS companion APK, 14 Kotlin files / 1,017 lines, that no portfolio milestone has ever touched or recompiled; last upstream change was `cf30423ee` (2026-07-08), two weeks before the `:model` conversion landed.

Two pre-research claims need correcting, and both make the case study *stronger*, not weaker. **First, the CI history is 12 consecutive red runs back to 2026-07-24, not 8 back to 2026-07-26** — the "8" was an artifact of `gh run list --limit 8`. The last green `Checks` run on `develop` is `30081326668` (2026-07-24T09:06:11Z); the very next run, `30081950299` (2026-07-24T09:16:14Z), is the push of the `:model` PR titled *"feat(model): kotlin migration — convert Chapter, FeedFunding, Feed, F…"* and its log shows `:app-wearos:compileFreeDebugKotlin FAILED` with this exact error. Causation is therefore established by CI record, not only by inference. **Second, "last touched 2026-05-14" is wrong** — `:app-wearos` received three upstream commits in July (`43e4f64fa`, `b8f8426c8`, `cf30423ee`), the last on 2026-07-08. It is still true that no *portfolio* work has touched it.

The fix is genuinely one line, and this is verified rather than argued. In a throwaway git worktree at `origin/develop` (created, probed, and removed; the repo working tree was never modified), changing `:115` to `item.title ?: ""` makes `:app-wearos:compileFreeDebugKotlin`, `:app-wearos:compilePlayDebugKotlin`, `:app-wearos:ktlintCheck`, `:app-wearos:lint`, `:app-wearos:assemblePlayDebug` and `:app-wearos:assembleFreeDebug` **all pass**. No second latent error surfaces. `?: ""` is not an invented idiom: the same upstream author wrote the same fallback at the module's three *other* title-rendering sites in the *same commit* (`5fe70196e`), and an existing upstream test proves the null branch is unreachable in production on the watch.

### Findings

#### Existing surface

`:app-wearos` is a standalone Wear OS companion application module (`com.android.application`, `applicationId "de.danoeh.antennapod"`, `minSdk 25`, `uses-feature android.hardware.type.watch`), 100% Kotlin + Jetpack Compose for Wear (Material3), 14 files / 1,017 lines. It has four Activities, four ViewModels, two shared composables, and a three-class sync layer:

- `MainListActivity.kt` (153) — launcher; now-playing card plus navigation entries. Renders `uiState.nowPlaying.item.title ?: ""` at `:99`.
- `FeedListActivity.kt` (59) — subscription list. Renders `feed.title ?: ""` at `:54`.
- `EpisodeListActivity.kt` (69) — queue/downloads/episodes/per-feed list. Renders `episode.title ?: ""` at `:64`.
- `EpisodeDetailActivity.kt` (231) — **the failing file**. Reads a `FeedItem` from the Intent extra `EXTRA_EPISODE` (`:55`, with a `?: run { finish(); return }` guard), hosts `EpisodeDetailScreen`, a top-level `@Composable` (`:82-231`). Renders `item.title` at `:115` **with no fallback** — the sole outlier.
- `MainViewModel.kt` / `FeedListViewModel.kt` / `EpisodeListViewModel.kt` / `EpisodeDetailViewModel.kt` — `MutableStateFlow`-based UI-state holders. `EpisodeDetailUiState.item` is a **non-null** `FeedItem` (`EpisodeDetailViewModel.kt:22`), so the compile error is purely about the *field's* type, not the item's.
- `sync/WearDataRepository.kt` — singleton of `StateFlow`s; `sync/WearDataListenerService.kt` — `WearableListenerService` decoding inbound payloads; `sync/WearMessageSender.kt` — outbound messages.
- `composable/ListScaffold.kt`, `composable/ListItem.kt` — shared Wear list chrome.

The module already has a `README.md` (`app-wearos/README.md`), satisfying AGENTS.md's per-module convention.

#### Java/Kotlin interop boundary

**Nothing depends on `:app-wearos`.** The only reference anywhere in the build is `settings.gradle:20` (`include ':app-wearos'`). It is a leaf: its own APK, its own launcher, its own `taskAffinity=".wearos"`. There is no public API surface to preserve and no downstream caller to break. This is the single most scope-limiting fact in this task.

**What `:app-wearos` calls out to** (`app-wearos/build.gradle:64-67`): `:model`, `:ui:common`, `:ui:notifications`, `:net:sync:wear-interface`. Of these, only `:model` has been migrated by this portfolio (Milestones 1-7). `:ui:common` is 18 Java files / 0 Kotlin; `:ui:notifications` and `:net:sync:wear-interface` are Java. So `:app-wearos`'s Kotlin-to-Kotlin surface with migrated code is exactly `:model`, and `:model` is where the widening happened.

The Java boundaries it still touches are *safe by construction* because they are platform types: `DateFormatter.formatAbbrev(Context, Date)` (`ui/common/.../DateFormatter.java:18`) accepts the nullable `item.getPubDate()` at `EpisodeDetailActivity.kt:104`, and `Converter.getDurationStringLong(int)` (`ui/common/.../Converter.java:21`) takes primitives. `WearNowPlaying.item` is an unannotated public Java field, hence a platform type — which is why `MainListActivity.kt:99`'s `uiState.nowPlaying.item.title ?: ""` compiles.

Note that `FeedItem` also deliberately keeps an explicit `fun getPubDate(): Date?` / `fun setPubDate(...)` pair over a renamed backing field (`FeedItem.kt:40-45, 264-269`) — a decision made during the `:model` migration specifically to preserve the Java-style accessor. That is why `EpisodeDetailActivity.kt:104`'s `item.getPubDate()` call still compiles from Kotlin and did not become a second error.

**Neither `:app-wearos` nor `:net:sync:wear-interface` appears in `AGENTS.md`'s module list.** Both have their own `README.md`. This is a documentation gap worth flagging, not fixing here.

#### Current test coverage

**`:app-wearos` has no test source set of any kind.** `app-wearos/src/` contains exactly one directory, `main`. There is no `src/test`, no `src/androidTest`, no test file anywhere under the module. `app-wearos/build.gradle:83` declares `testImplementation libs.junit`, but the dependency is unused — there is nothing for it to compile.

Consequently **zero tests reach `EpisodeDetailActivity`, `EpisodeDetailScreen`, or any other `:app-wearos` class.** The module's entire behavior is unverified by automated test today. Its only automated gate has been compilation + Android Lint + ktlint, all three of which have been failing since 2026-07-24.

The real coverage that *does* exist sits one module out, in `:net:sync:wear-interface`, and it is directly relevant. `net/sync/wear-interface/src/test/java/.../WearSerializerTest.java` has 8 tests, and `testEpisodesRoundTripNullTitle()` (`:48-58`) is precisely the characterization test this task would otherwise need to write:

```java
item.setTitle(null);
byte[] bytes = WearSerializer.episodesToBytes(Collections.singletonList(item));
List<FeedItem> result = WearSerializer.episodesFromBytes(bytes);
assertEquals("", result.get(0).getTitle());
```

It pins the invariant that makes the fix behaviour-neutral: a null title on the phone becomes `""` on the watch, never null.

#### Characterization-test gaps

The honest answer is that **the gap that matters is already covered upstream, and the gap that remains is not worth closing for this fix.**

1. **`EpisodeDetailScreen` rendering — no coverage, and not economically closable here.** There is no Compose UI test infrastructure anywhere in this repo: `grep -rn "createComposeRule\|ComposeTestRule"` over all sources returns **zero hits**, `gradle/libs.versions.toml` has no `androidx.compose.ui:ui-test-*` entry and no Paparazzi entry (only `robolectric = 4.16` at `:77`, which this project's own convention discourages — see the repo memory note on preferring stdlib/KMP-portable approaches over Robolectric shims). Writing a characterization test for `:115` would mean standing up a Wear-Compose test harness in a module with zero tests, adding at least one new dependency and a new source set, for a one-token change. That is a larger and riskier diff than the fix.

2. **The behavioral question the test would answer is already answered, by an existing upstream test plus a closed provenance chain.** Every `FeedItem` that can reach `EpisodeDetailScreen` is produced by `WearSerializer.episodeFromJson`, which does `item.setTitle(obj.optString(KEY_TITLE, ""))` (`WearSerializer.java:44`) — `JSONObject.optString` returns the fallback for both a missing key and `JSONObject.NULL`, so the title is **never null on the watch**. The two entry points, `episodesFromBytes` (`:70-75`) and `nowPlayingFromBytes` (`:129-135`), both route through it, and `WearDataListenerService.kt` is the only writer of `WearDataRepository`. `EpisodeDetailActivity` receives its item as a serialized Intent extra put by `MainListActivity.kt:46` / `EpisodeListActivity.kt:35`, both of which take it from that repository. `WearSerializerTest.testEpisodesRoundTripNullTitle` (`:48-58`) asserts exactly this. **The `?: ""` branch is therefore unreachable in production**, and the fix is provably a no-op at runtime.

3. **What the fix *can* be verified by, conclusively:** compilation of both flavors, `ktlintCheck`, `lint`, and `assemble` — all confirmed green in the worktree probe (see Track prerequisites). This is the "verified by compilation + review only" path, and here it is not a compromise: there is no runtime behavior to characterize, because the changed branch cannot execute.

4. **One residual gap worth naming for the planner, small:** nothing pins the `WearSerializer`-never-emits-null-title invariant *as a documented contract*. `net/sync/wear-interface/README.md` is the natural home for it, and AGENTS.md explicitly asks for exactly this kind of long-term-stable fact to be recorded in module READMEs. That is a doc line, not a test.

#### Track-specific findings

No migration track is requested — this is standalone repo hygiene. The finding relevant to the pipeline is that this is **the third distinct manifestation of the same `kotlin`-track hazard**, and the first that is a hard compile error rather than a static-analysis finding:

| Manifestation | Module | Shape | Root migration |
|---|---|---|---|
| `MessageEvent.message`, `QueueEvent.item`, `FeedFunding.content` | `:app` (Java consumers) | SpotBugs `NP_*` findings | `:event` M8/M9, `:model` M1-7 |
| nullable-param call sites | `:net:download:service` | SpotBugs findings | `:net:download:service-interface` M10 |
| **`FeedItem.title`** | **`:app-wearos` (Kotlin consumer)** | **Kotlin compile error** | **`:model` M1-7 (`b71cb7942`)** |

The generalizable lesson for the `kotlin` track, worth carrying into future module research on this repo: **J2K widening an unannotated Java field to a nullable Kotlin type breaks Java consumers *silently* (a new static-analysis finding) but breaks Kotlin consumers *loudly* (a compile error) — and a migration milestone that only builds `:app` will not see the Kotlin consumers.** The `:model` milestones' verification commands were `:app:assembleDebug` and module-scoped test tasks (per AGENTS.md's stated build command), none of which reach `:app-wearos`. Every other `:model` consumer in the repo is Java, so `:app-wearos` was the only module that could have failed this way, and it was the one module the milestone never compiled. This is a **repo-wide-compile-after-widening** check that future milestones should adopt; flagging it, not prescribing it.

The three sibling `?: ""` sites are also a track-relevant data point: they were written by the same author, in the same commit (`5fe70196e`, 2026-05-14), *against the pre-migration platform type*, where the elvis was purely defensive and warning-free. The migration did not create the idiom — it made the one site that lacked it fail to compile.

#### Track prerequisites

Not applicable — no migration track is requested. The gate is CI. Confirmed mechanics:

- `.github/workflows/checks.yml:44-45` — the `static-analysis` job's `Checkstyle, Lint, SpotBugs` step runs `./gradlew checkstyle lint`.
- `:app-wearos:lint`'s task graph contains **both** `:app-wearos:compileFreeDebugKotlin` (via `lintAnalyzeFreeDebug`) **and** `:app-wearos:compilePlayDebugKotlin` — verified with `./gradlew :app-wearos:lint --dry-run`. So `checkstyle lint` cannot pass while either fails. CI hits the Free variant first (`compileFreeDebugKotlin`), which is why the pre-research saw that task name rather than `compilePlayDebugKotlin`; **both fail, and both are fixed by the same one-line change**, since the file is in `src/main`.
- `app-wearos/build.gradle:57-61` additionally wires `lint` to `dependsOn ktlintCheck`; `common.gradle:139-144` wires `lint` to `dependsOn spotbugsDebug`/`spotbugsPlayDebug`. `:app-wearos` has zero `.java` files, so its `checkstyle` task (source = `src/main/java/**/*.java`, `common.gradle:147-158`) and its SpotBugs tasks are effectively no-ops.
- `checks.yml:53-58` — `unit-test` declares `needs: static-analysis`, and its own `Build` step runs `./gradlew assemble${variant}` for `PlayDebug`/`PlayRelease`/`FreeRelease`, which would also hit `:app-wearos`. It never runs today.

**Exact local reproduction (current `origin/develop` = `5ae7d560f`):**

```
./gradlew --console=plain :app-wearos:compileFreeDebugKotlin
```
→ `e: .../EpisodeDetailActivity.kt:115:28 Argument type mismatch: actual type is 'String?', but 'String' was expected.` / `Task :app-wearos:compileFreeDebugKotlin FAILED`. Reproduced 2026-08-12; matches CI run `31592660967` verbatim.

**Exact verification command set for the fix** (all six confirmed green in the throwaway worktree with only `item.title ?: ""` applied at `:115`):

```
./gradlew --console=plain :app-wearos:compileFreeDebugKotlin :app-wearos:compilePlayDebugKotlin :app-wearos:ktlintCheck
./gradlew --console=plain :app-wearos:lint
./gradlew --console=plain :app-wearos:assemblePlayDebug :app-wearos:assembleFreeDebug
```

#### Module ownership / interop

`:app-wearos` is a **standalone Wear OS companion APK**, not a library. `app-wearos/src/main/AndroidManifest.xml` declares `<uses-feature android:name="android.hardware.type.watch" android:required="true"/>`, `com.google.android.wearable.standalone = false` (phone-dependent), one exported launcher `MainListActivity`, three non-exported activities, and an exported `WearDataListenerService`. It applies `playFlavor.gradle`, so it has the **same `free`/`play` market flavor split as `:app`** (`playFlavor.gradle:1-11`) — the pre-research's uncertainty here is resolved: the flavors exist, and both Debug variants' Kotlin compile tasks are in `lint`'s graph.

Nothing depends on it (`settings.gradle:20` is the only reference), so the blast radius of any change here is the watch app alone.

The "module nobody has looked at recently" risk that *is* worth flagging without expanding scope: **`:app-wearos` has had no working automated gate for 19 days and has zero tests**, so any regression introduced into it during that window would be invisible. Reassuringly, the module itself has not changed since `cf30423ee` (2026-07-08) — the breakage came entirely from `:model` underneath it. But the same is not automatically true of the *next* `:model`-adjacent milestone, and once this fix lands, `:app-wearos` compile becomes a live gate again for every subsequent PR.

### Unknowns

1. **Should the fallback be `""`, or should `FeedItem.title` be tightened to non-null at the declaration (the `MessageEvent.message` precedent from PR #23)?** My read: **`""`**, decisively. `FeedItem.title` is genuinely nullable — `FeedItem()` no-arg construction leaves it null (`FeedItem.kt:30`), `getIdentifyingValue()` (`FeedItem.kt:238-240`) explicitly branches on it being null/empty, `FeedMedia.kt:237` falls back with `currentItem.title ?: currentItem.getIdentifyingValue()`, and `WearSerializerTest:51` sets it null deliberately. Unlike `MessageEvent.message`, there is no evidence of a non-null author contract to restore, and tightening it would ripple across the whole app for a watch-only compile error. **Planner should confirm and record the reasoning**, since the sibling task set the opposite precedent for a superficially similar problem and a reviewer will ask.
2. **Should the fallback be `""` or `stringResource(...)` of some placeholder?** `""` matches all three sibling sites in this exact module (`MainListActivity.kt:99`, `FeedListActivity.kt:54`, `EpisodeListActivity.kt:64`) and introduces no user-visible string, so AGENTS.md's `:ui:i18n` rule is not triggered. A placeholder string would trigger it, expand scope into `:ui:i18n`, and diverge from the module's own convention. I see no case for it, but the planner owns the call.
3. **Is a characterization test required by this portfolio's test-first pattern, and if not, is that documented in the Plan?** Research's position is that it is not achievable proportionately (no Compose test infra in the repo at all) and not *needed* (the `?: ""` branch is unreachable in production, per `WearSerializer.java:44` + `WearSerializerTest:48-58`). But this portfolio's whole pitch is behavioral-equivalence proof, and "no test" needs to be an explicit, argued decision in the Plan with the equivalence argument written out — not an omission. **Planner + red-team should ratify.** The cheap alternative, if one is wanted: a plain-JUnit test added to `WearSerializerTest` asserting `nowPlayingFromBytes` also yields a non-null title (currently only the `episodesFromBytes` path is asserted null-null-safe), which closes the last uncovered branch of the invariant the fix leans on, in an existing test file, with no new dependencies.
4. **Branch point.** The working tree is on `fix/net-download-service-nullable-param-spotbugs` (`8339f9adb`) with the sibling task's commits, and **local `develop` is stale at `f5d4c5551` while `origin/develop` is `5ae7d560f`**. This fix must branch fresh from an up-to-date `origin/develop`, or it will carry the sibling's diff. AGENTS.md forbids committing to `develop`/`master` directly.
5. **This PR's own `Checks` run will still be red, at `:net:download:service:spotbugsPlayDebug`.** The sibling task's PR #25 is open, not merged. Whichever of the two lands first sees a red gate for the other's reason. The PR description must state that `antennapod-fix-net-download-service-spotbugs-debt` must also land before `static-analysis` goes green, and must not claim this PR restores CI on its own — the same discipline both sibling tasks adopted. Merge-policy call for José, not a technical one.
6. **After this lands, `unit-test`'s `assemble` steps will exercise `:app-wearos` release builds for the first time since 2026-07-24.** `FreeRelease` runs `minifyEnabled true` + `shrinkResources true` with `proguard.cfg` and the CI-generated keystore (`checks.yml:96-98`). I probed both *Debug* assembles green but could not probe a release build (no keystore locally). Risk is low — the module is unchanged and R8 config is untouched — but this is the first gate beyond `static-analysis` that will newly execute, and it is worth naming so nobody is surprised.
7. **Should `AGENTS.md`'s module list gain `:app-wearos` and `:net:sync:wear-interface`?** Both are missing; both have READMEs. Adding them is arguably the kind of durable documentation AGENTS.md itself asks for, but it is adjacent to this fix and AGENTS.md's minimal-diff rule cuts against it. **Flagging, not deciding.**

### Sources

**Reproduction and probe (2026-08-12)**
- `./gradlew --console=plain :app-wearos:compileFreeDebugKotlin` on `8339f9adb` → single error at `EpisodeDetailActivity.kt:115:28`, `BUILD FAILED in 4s`
- Throwaway detached worktree at `origin/develop` (`5ae7d560f`) with only `item.title ?: ""` applied at `:115`: `:app-wearos:compileFreeDebugKotlin` + `:app-wearos:compilePlayDebugKotlin` + `:app-wearos:ktlintCheck` → `BUILD SUCCESSFUL`, 124 tasks executed (cold, nothing UP-TO-DATE); `:app-wearos:lint` → `BUILD SUCCESSFUL`; `:app-wearos:assemblePlayDebug` + `:app-wearos:assembleFreeDebug` → `BUILD SUCCESSFUL`. Worktree removed; repo working tree never modified (`git status --short` shows only this task's two untracked spec files)
- `./gradlew :app-wearos:lint --dry-run` → graph contains `:app-wearos:compileFreeDebugKotlin`, `:app-wearos:lintAnalyzeFreeDebug`, `:app-wearos:compilePlayDebugKotlin`

**The failing site and its three siblings**
- `app-wearos/src/main/java/de/danoeh/antennapod/wearos/EpisodeDetailActivity.kt:115` — `text = item.title` (no fallback); `:55` Intent-extra guard; `:82-231` `EpisodeDetailScreen`; `:104` `item.getPubDate()`
- `app-wearos/src/main/java/de/danoeh/antennapod/wearos/MainListActivity.kt:99` — `text = uiState.nowPlaying.item.title ?: ""`; `:46` `putExtra(EXTRA_EPISODE, episode)`
- `app-wearos/src/main/java/de/danoeh/antennapod/wearos/FeedListActivity.kt:54` — `text = feed.title ?: ""`
- `app-wearos/src/main/java/de/danoeh/antennapod/wearos/EpisodeListActivity.kt:64` — `text = episode.title ?: ""`; `:35` `putExtra(EXTRA_EPISODE, episode)`
- `git blame -L 113,116 app-wearos/.../EpisodeDetailActivity.kt` and the same on the three siblings → **all four lines from `5fe70196eb`, Hans-Peter Lehmann, 2026-05-14**
- `app-wearos/src/main/java/de/danoeh/antennapod/wearos/EpisodeDetailViewModel.kt:22` — `val item: FeedItem` (non-null)

**The widened declaration**
- `model/src/main/java/de/danoeh/antennapod/model/feed/FeedItem.kt:30` — `var title: String? = null`
- `model/src/main/java/de/danoeh/antennapod/model/feed/FeedItem.kt:40-45, 264-269` — `pubDateField` + explicit `getPubDate()`/`setPubDate()`
- `model/src/main/java/de/danoeh/antennapod/model/feed/FeedItem.kt:238-240` — `getIdentifyingValue()` null/empty branch on `title`
- `model/src/main/java/de/danoeh/antennapod/model/feed/FeedMedia.kt:237` — `currentItem.title ?: currentItem.getIdentifyingValue()`
- `git show b71cb7942^:model/src/main/java/de/danoeh/antennapod/model/feed/FeedItem.java` → `:32` `private String title;`, `:215-217` unannotated `public String getTitle()`
- `git log --format="%h %ad" --date=short -- model/.../FeedItem.kt` → `b71cb7942` 2026-07-23, `e0f4af32d` 2026-07-24, `936c16aec` 2026-07-25

**CI record**
- `.github/workflows/checks.yml:44-45` — `./gradlew checkstyle lint`; `:53-58` — `unit-test` `needs: static-analysis`; `:96-98` — CI-generated `app-wearos/keystore`
- `common.gradle:139-144` — `lint dependsOn spotbugsDebug/spotbugsPlayDebug`; `:147-158` — `checkstyle` source is `src/main/java/**/*.java` only
- `app-wearos/build.gradle:57-61` — `lint dependsOn ktlintCheck`; `:83` — unused `testImplementation libs.junit`; `:7-8` — applies `common.gradle` and `playFlavor.gradle`
- `playFlavor.gradle:1-11` — `free`/`play` market flavor dimension
- `gh run list --branch develop --workflow Checks --limit 25` → **last green `30081326668` (`37f1c201e`, 2026-07-24T09:06:11Z); 12 consecutive failures since, starting `30081950299` (`810fe1d38`, 2026-07-24T09:16:14Z)**
- `gh run view 30081950299 --log-failed` → `:app-wearos:compileFreeDebugKotlin FAILED` and `:app-wearos:compilePlayDebugKotlin FAILED`, both with `EpisodeDetailActivity.kt:115:28`, plus `:app:spotbugsPlayDebug FAILED` (the sibling pile, since fixed by PR #23)
- `gh run view 31592660967` — 2026-08-12 push to `develop`, same failure

**Module identity and dependents**
- `settings.gradle:20` — `include ':app-wearos'`; the only reference to it in any build file
- `app-wearos/src/main/AndroidManifest.xml` — `uses-feature android.hardware.type.watch`, `wearable.standalone=false`, 4 activities, 1 exported service
- `app-wearos/README.md` — module purpose and DataLayer architecture
- `app-wearos/build.gradle:64-67` — depends on `:model`, `:ui:common`, `:ui:notifications`, `:net:sync:wear-interface`
- `ls app-wearos/src/` → `main` only; no `test`, no `androidTest`
- `wc -l $(find app-wearos/src -name "*.kt")` → 14 files, 1,017 lines
- `git log --format="%h %ad" --date=short -- app-wearos/` → `cf30423ee` 2026-07-08, `b8f8426c8` 2026-07-03, `43e4f64fa` 2026-07-02, `3a7f18a7f` 2026-05-17, `5fe70196e` 2026-05-14
- `AGENTS.md` — module list omits both `:app-wearos` and `:net:sync:wear-interface`

**The invariant that makes the fix behaviour-neutral**
- `net/sync/wear-interface/src/main/java/.../WearSerializer.java:44` — `item.setTitle(obj.optString(KEY_TITLE, ""))`; `:33` phone-side `getTitle() != null ? getTitle() : ""`; `:70-75` `episodesFromBytes`; `:129-135` `nowPlayingFromBytes`
- `net/sync/wear-interface/src/test/java/.../WearSerializerTest.java:48-58` — `testEpisodesRoundTripNullTitle()` asserts null title → `""` after round trip (8 tests total in this file)
- `net/sync/wear-interface/src/main/java/.../WearNowPlaying.java:6` — unannotated `public final FeedItem item` (platform type)
- `app-wearos/src/main/java/de/danoeh/antennapod/wearos/sync/WearDataListenerService.kt:10-31` — sole writer of `WearDataRepository`
- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/DateFormatter.java:18`, `ui/common/.../Converter.java:21` — Java signatures consumed at `EpisodeDetailActivity.kt:104,153,158`

**Test infrastructure availability**
- `grep -rn "createComposeRule\|ComposeTestRule"` over all sources → **0 hits repo-wide**
- `gradle/libs.versions.toml` → no Paparazzi, no `compose ui-test`; `:77` `robolectric = 4.16` only
- `grep -rn "\.title ?:" --include="*.kt"` → 4 hits total repo-wide: the three `:app-wearos` siblings + `FeedMedia.kt:237`; `grep -rn "\.title!!"` → 0 hits

**Conventions**
- `AGENTS.md` — minimal-diff rule, module-README-first, `:ui:i18n` new-strings rule, no commits on `develop`/`master`
- Repo memory: KMP-portability-over-Robolectric preference; test-first milestone pattern

## Plan
_Last updated by: legacy-android-planner | 2026-08-12 (rev. 2 — red-team Loop 1 correction to Unknown 3 / Step 2 / ACs)_

> **Revision 2 (2026-08-12), in response to red-team Loop 1 CHALLENGE.** Red-team's single MAJOR finding is **accepted in full**. The proposed `testNowPlayingRoundTripNullTitle()` is dropped rather than relabelled, and every claim that it closed a coverage gap is withdrawn. Changed: **Unknown 3** (rewritten), **Step 2** (now README + baseline run, no test code), **Acceptance Criteria → Behavioral equivalence** (rewritten), **File Scope** (one reduction: `WearSerializerTest.java` is now read-only), **Out of Scope** (two bullets added), **Open Questions** (new #4). Unchanged: Research, Objective, Situation change, Unknowns 1/2/4/5/6/7, the README decision, Step 1, **Step 3 (the `?: ""` fix)**, Step 4, all other ACs, Milestone.

### Objective

Restore `:app-wearos` compilation by adding the `?: ""` fallback that the module's three sibling title-render sites already use, at `EpisodeDetailActivity.kt:115`. No migration track is requested — this is standalone repo hygiene that unblocks `checks.yml`'s `static-analysis` job, red since 2026-07-24.

### Situation change since Research (verified 2026-08-12, post-research)

Research was written before sibling PR #25 merged. Two facts have moved and the Plan is built on the current ones:

- **`origin/develop` is now `d1e1bd127`**, not `5ae7d560f`. It contains **both** sibling fixes: PR #23 (merged 11:37Z) and PR #25 (merged 18:40Z). Research's Unknown 5 ("PR #25 is open, not merged") is stale.
- **The latest `Checks` run on `develop` — `31628870547`, on `d1e1bd127`, triggered by the #25 merge — still fails, and `--log-failed` now shows exactly one failing task: `:app-wearos:compileFreeDebugKotlin`.** No SpotBugs task appears. This fix is therefore the **last known** blocker, and the CI record now confirms both sibling piles are cleared.
- I additionally probed `./gradlew --continue checkstyle` repo-wide → `BUILD SUCCESSFUL`, 30 tasks, zero violations. So the `checkstyle` half of `checkstyle lint` is clean. The `lint` half beyond `:app-wearos` remains unprobed (see Step 4 and Open Question 3) — it has not executed on CI since 2026-07-24.

"Last known blocker" is the strongest claim the evidence supports. It is **not** the same as "this makes CI green," and Step 4 exists to find out which one is true.

### Resolved Decisions

**Unknown 1 — `?: ""` at the call site, NOT tightening `FeedItem.title` to non-null. Resolved: `?: ""`.**

This is deliberately the *opposite* call from sibling PR #23, which tightened `MessageEvent.message` to non-null at the declaration. That is not an inconsistency, because the two fields are different shapes, and the deciding test is *whether the pre-migration Java had a single unambiguous non-null contract that J2K widened by accident, or a genuinely nullable field that J2K widened correctly*:

- `MessageEvent.message` had one construction path, always non-null, and every consumer assumed non-null. The nullable Kotlin type was a J2K artifact with no basis in the original design. Tightening **restored** the author's contract.
- `FeedItem.title` is **legitimately nullable, by current design, in code this task does not own.** Research found three independent live proofs: `FeedItem.getIdentifyingValue()` (`FeedItem.kt:238-240`) explicitly branches on the title being null/empty; `FeedMedia.kt:237` falls back with `currentItem.title ?: currentItem.getIdentifyingValue()`; and `WearSerializerTest:51` sets it null deliberately as a supported scenario, not as an edge case. The no-arg `FeedItem()` constructor also leaves it null (`FeedItem.kt:30`). There is no non-null author contract here to restore — the nullable type is *correct*.

Tightening it would therefore not be a fix; it would be a semantic change to a core `:model` type, rippling through every consumer in the app, to resolve a compile error in a leaf watch module. That trade is backwards. The narrow fix belongs at the call site that cannot accept null, which is exactly what the module's own author already did at its three other title sites in the same commit (`5fe70196e`).

**Rule for the reviewer, and for future instances of this pattern:** tighten at the declaration when the nullability is a J2K artifact contradicted by the original Java contract; guard at the call site when the field is genuinely nullable and only one consumer needs a non-null view.

**Unknown 2 — `""` vs. a `stringResource(...)` placeholder. Resolved: `""`.**

Matches all three sibling sites in this exact module. Introduces no user-visible string, so AGENTS.md's `:ui:i18n` rule is not triggered and scope stays out of `:ui:i18n`. A placeholder would diverge from the module's own established convention for no user-visible benefit, since the branch cannot execute in production anyway (see Unknown 3).

**Unknown 3 — characterization test. Resolved: no Compose characterization test, and no new `WearSerializerTest` method either. Equivalence is pinned by the existing `testEpisodesRoundTripNullTitle`, run before and after the fix.**

> **Revised 2026-08-12 after red-team Loop 1 (MAJOR).** The previous version of this decision added `testNowPlayingRoundTripNullTitle()` and justified it as closing the "one uncovered leg" of the null-title invariant — claiming `nowPlayingFromBytes` was a second, untested entry point. **That claim was false and is withdrawn.** It was derived from the call graph (`EpisodeDetailActivity` really is fed from two provenance paths) without reading the serializer, and the serializer does not share that shape. See point 4 below for the corrected position and the evidence.

This portfolio's pitch is behavioral-equivalence proof, so "no test" is ratified here as an argued decision, not an omission. The argument, in full:

1. **A Compose characterization test is disproportionate and is not the cheapest available proof.** `:app-wearos` has no test source set at all (`src/` contains only `main`), and there is zero Compose UI test infrastructure repo-wide — `createComposeRule` has 0 hits, and `libs.versions.toml` has neither Paparazzi nor `compose ui-test`. Covering one token would mean a new source set, a new test harness, and at least one new dependency in a module with no tests: a larger, riskier diff than the fix, in a module whose blast radius is the watch APK alone.
2. **The changed branch is unreachable in production, so there is no runtime behavior to characterize.** Every `FeedItem` that can reach `EpisodeDetailScreen` originates in `WearSerializer.episodeFromJson`, which does `item.setTitle(obj.optString(KEY_TITLE, ""))` (`WearSerializer.java:44`); `optString` returns the fallback for both a missing key and `JSONObject.NULL`. Both watch-side entry points (`episodesFromBytes`, `nowPlayingFromBytes`) route through it, `WearDataListenerService.kt` is the sole writer of `WearDataRepository`, and `EpisodeDetailActivity` receives its item as an Intent extra sourced from that repository. The title is never null on the watch. `?: ""` is a provable runtime no-op.
3. **The equivalence proof is executable, not merely asserted.** Existing upstream test `WearSerializerTest.testEpisodesRoundTripNullTitle` (`:48-58`) already pins the `episodesFromBytes` leg of that invariant, and it runs on every CI build.
4. **There is no second leg to close. `nowPlayingFromBytes` shares the same title code, so a `testNowPlayingRoundTripNullTitle()` would be redundant, and it is not being added.** `EpisodeDetailActivity` does have two provenance paths (`MainListActivity.kt:46` from now-playing, `EpisodeListActivity.kt:35` from the episode list), and that is what made a second test look warranted. But provenance branches in the *caller*, not in the serializer. Reading `WearSerializer.java` end to end:
   - `nowPlayingToBytes` (`:118-126`) calls `episodeToJson(item)` — the identical private method `episodesToBytes` (`:57-67`) calls per item; it then adds only `KEY_IS_PLAYING`.
   - `nowPlayingFromBytes` (`:129-141`) calls `episodeFromJson(obj)` — the identical private method `episodesFromBytes` (`:70-81`) calls per element; it then reads only `KEY_IS_PLAYING` and wraps in `WearNowPlaying`.
   - All title handling lives in exactly two lines, both private and both shared: `episodeToJson` `:33` (`getTitle() != null ? getTitle() : ""`) and `episodeFromJson` `:44` (`setTitle(optString(KEY_TITLE, ""))`).

   So the proposed test would invoke the same private code through a different public wrapper. It could not fail in any scenario where `testEpisodesRoundTripNullTitle` does not also fail.

   **The "narrower wrapper-wiring insurance" reframe was also considered and rejected, because it does not survive being written out.** Since `episodeToJson` `:33` already normalizes null to `""` on the way *out*, a null-titled item and a `""`-titled item serialize to byte-identical payloads. The proposed test would therefore be a strict subset of two tests that already exist: `testNowPlayingRoundTrip` (`:100-118`) already proves the now-playing wrapper carries a non-null title through `WearNowPlaying` unmutated and returns non-null, and `testEpisodesRoundTripNullTitle` (`:48-58`) already proves the null→`""` normalization. No wrapper logic remains for it to insure. Adding it and calling it insurance would be relabelling noise as rigor — the precise failure mode this pipeline's red-team step exists to catch, and a poor look on a case study whose entire pitch is verification rigor.

   **A genuinely-new alternative exists and is deliberately declined as out of scope.** `WearSerializer` does have real uncovered branches: the `JSONException` catch in `episodesFromBytes`/`feedsFromBytes`, and `nowPlayingFromBytes` returning `null` for *malformed non-empty* bytes (`testNowPlayingFromBytesEmpty` covers only the `data.length == 0` early return). Closing those would be honest new coverage — but it is serializer error-path test debt in a module this task does not own, is unrelated to a nullable-title compile error, and would be fair grounds for the opposite red-team challenge. It belongs in its own task, not bundled into a one-line fix. Recorded in Out of Scope.

5. **The equivalence evidence this fix actually rests on therefore already exists and already runs in CI.** `testEpisodesRoundTripNullTitle` is the characterization test for this change — it was simply written by upstream rather than by us. Step 2 runs the existing 8-test suite as the documented **before** baseline and Step 3 re-runs it as the **after**, which is the standard existing-test-as-characterization-test pattern and is fully honest. AGENTS.md's minimal-diff rule (*"Only update existing code if necessary"*) independently forbids adding a test that cannot fail on its own, so `WearSerializerTest.java` is left unmodified.

`:net:sync:wear-interface` is not a migrated module; converting its tests to Kotlin is not in scope either way.

**Unknown 4 — branch point. Resolved: branch fresh from `origin/develop` at `d1e1bd127`.**

The working tree is currently on `fix/net-download-service-nullable-param-spotbugs`, which is PR #25's branch and is now **merged** — branching from it, or from the stale local `develop` (`f5d4c5551`, 2026-08-06), would produce a wrong or duplicated diff. Step 1 pins this explicitly. AGENTS.md forbids committing to `develop`/`master` directly. Branch name: `fix/app-wearos-nullable-title-compile-error`.

**Unknown 5 — PR/commit-message discipline. Resolved, with the claim updated to current evidence.**

The PR description must state the honest, verifiable position and nothing stronger:

> This removes the last known blocker on `checks.yml`'s `static-analysis` job. Sibling PRs #23 and #25 cleared both SpotBugs piles; run `31628870547` on `d1e1bd127` fails at `:app-wearos:compileFreeDebugKotlin` and nothing else. Note that `./gradlew checkstyle lint` has not run past `:app-wearos` since 2026-07-24, and the `unit-test` job (`needs: static-analysis`) has not run at all in that window — so tasks beyond this point are executing for the first time in 19 days and may surface unrelated pre-existing failures. This PR does not claim to make `Checks` green; it claims to remove the blocker that has prevented anyone from finding out.

Do not write "fixes CI" or "restores a green build."

**Unknown 6 — release builds newly exercised. Resolved: named as a risk, explicitly out of scope.**

Once `static-analysis` passes, `unit-test` runs `assemble` for `PlayDebug`/`PlayRelease`/`FreeRelease`, and `FreeRelease` applies `minifyEnabled true` + `shrinkResources true` with the CI-generated keystore (`checks.yml:96-98`). Research probed both *Debug* assembles green but could not probe a release build locally (no keystore). Risk is low — `:app-wearos` is unchanged since `cf30423ee` and the R8 config is untouched — but if `FreeRelease` fails, **that is a new task, not an expansion of this one.** Recorded so nobody is surprised and nobody reflexively widens this diff.

**Unknown 7 — `AGENTS.md` module list omits `:app-wearos` and `:net:sync:wear-interface`. Resolved: out of scope here.**

Real, worth doing, and unrelated to a compile fix. AGENTS.md's own minimal-diff rule cuts against bundling it. Raised as Open Question 1 for José as its own trivial follow-up.

**Additional decision (Research's characterization-gap item 4) — document the serializer invariant in `net/sync/wear-interface/README.md`.**

Included in scope, deliberately and narrowly. AGENTS.md instructs: *"When you discover something broadly useful about a module, such as ... a pattern all callers should follow, update that module's `README.md` proactively."* "`WearSerializer` never emits a null title to the watch" is exactly that shape: long-term stable, generic, and the invariant every `:app-wearos` caller silently relies on — including the line this task is fixing. The README currently documents paths and protocol but not this guarantee. **Hard cap: add the invariant to the existing document; do not restructure it.** This decision is unchanged by the Unknown 3 revision and stands on its own: the invariant is real, undocumented, and already pinned by an existing test (`testEpisodesRoundTripNullTitle`) — the README note records the contract, it does not depend on any *new* test. It remains Step 2.

### Steps

Each step leaves the build in a committable state.

1. **Branch fresh from an up-to-date `origin/develop`.** `git fetch origin`, then create `fix/app-wearos-nullable-title-compile-error` from `origin/develop`; confirm `HEAD` resolves to `d1e1bd127` and that `git log --oneline -1` shows the PR #25 merge. Reproduce the failure to confirm the starting state: `./gradlew --console=plain :app-wearos:compileFreeDebugKotlin` must FAIL with exactly one error at `EpisodeDetailActivity.kt:115:28`. No code change. Nothing to commit.

2. **Establish the characterization baseline and document the invariant.** Two parts, no test code:
   - **Baseline (no edit).** Run `./gradlew --console=plain :net:sync:wear-interface:test` and record the verbatim result in Implementation Notes: **8 tests, all green**, including `testEpisodesRoundTripNullTitle` (`WearSerializerTest.java:48-58`). This is the **BEFORE** half of the equivalence proof. That existing upstream test *is* this task's characterization test — it pins the null-title→`""` invariant the fix leans on, and it is re-run unchanged in Step 3 as the **AFTER** half. **Do not add, rename, or modify any test in this file** (see Unknown 3: a `nowPlaying`-flavoured null-title test would re-execute the same shared private `episodeToJson`/`episodeFromJson` code and could not fail independently).
   - **Document the contract.** In `net/sync/wear-interface/README.md`, add a short note recording that `WearSerializer` normalizes a null title to `""` in **both** directions — outbound at `episodeToJson` and inbound at `episodeFromJson`, and therefore on every public entry point (`episodesToBytes`/`episodesFromBytes`, `nowPlayingToBytes`/`nowPlayingFromBytes`, and the `feeds*` pair) — so watch-side consumers may treat `FeedItem.title` as non-null. Phrase it as a durable module contract, naming the shared private helpers as the reason it holds uniformly; do not mention this task or PR. Do not restructure the document.

   Commit (README only).

3. **Apply the one-line fix.** In `app-wearos/src/main/java/de/danoeh/antennapod/wearos/EpisodeDetailActivity.kt:115`, change `text = item.title` to `text = item.title ?: ""`. Change nothing else in the file. Verify with Research's exact confirmed command set:
   ```
   ./gradlew --console=plain :app-wearos:compileFreeDebugKotlin :app-wearos:compilePlayDebugKotlin :app-wearos:ktlintCheck
   ./gradlew --console=plain :app-wearos:lint
   ./gradlew --console=plain :app-wearos:assemblePlayDebug :app-wearos:assembleFreeDebug
   ```
   All must be `BUILD SUCCESSFUL`. Re-run `:net:sync:wear-interface:test` to confirm Step 2's tests still pass AFTER the change. Commit.

4. **Report-only: run the full CI static-analysis surface and record what it finds.** Run `./gradlew --console=plain checkstyle lint` from the repo root — the exact command at `checks.yml:44-45`, which has not run past `:app-wearos` since 2026-07-24. Record the verbatim outcome in the task file's Implementation Notes. **Do not fix anything this surfaces.** If it is green, say so, and the PR can state that the whole `static-analysis` command passes locally. If it surfaces failures in other modules, those are pre-existing, out of scope, and become a new task — report them, name the modules, and stop. No commit unless recording notes.

### File Scope

The developer may modify only these files. Anything else is out of scope and the reviewer should reject it.

- `app-wearos/src/main/java/de/danoeh/antennapod/wearos/EpisodeDetailActivity.kt` — **line 115 only**, one token added
- ~~`net/sync/wear-interface/src/test/java/.../WearSerializerTest.java`~~ — **removed from File Scope 2026-08-12 (red-team Loop 1).** Read-only reference now: it is *run* in Steps 2 and 3 as the characterization baseline, but **not modified**. This is the only File Scope change from the Loop 1 revision, and it is a reduction (4 writable files, was 5)
- `net/sync/wear-interface/README.md` — add the invariant note; do not restructure
- `tasks/antennapod-fix-app-wearos-compile-error.md` — Implementation Notes
- `features/antennapod-fix-app-wearos-compile-error.checkpoint.md` — lifecycle bookkeeping

**Explicitly NOT in scope**, called out because each is a plausible reflex here:

- `MainListActivity.kt`, `FeedListActivity.kt`, `EpisodeListActivity.kt` — already correct; Research confirmed no second latent error. Do not "harmonize" them.
- Any other `:app-wearos` file, and any new `:app-wearos` test source set or dependency
- `model/.../FeedItem.kt` — see Unknown 1; do not tighten `title`
- `AGENTS.md`, `app-wearos/README.md`, `gradle/libs.versions.toml`, `.github/workflows/checks.yml`
- Any module surfaced by Step 4

### Acceptance Criteria

Verification — the fix
- [ ] `./gradlew --console=plain :app-wearos:compileFreeDebugKotlin` fails at `EpisodeDetailActivity.kt:115:28` on the branch point before Step 3, and succeeds after (both states recorded in Implementation Notes)
- [ ] `:app-wearos:compileFreeDebugKotlin`, `:app-wearos:compilePlayDebugKotlin`, `:app-wearos:ktlintCheck` all `BUILD SUCCESSFUL`
- [ ] `:app-wearos:lint` `BUILD SUCCESSFUL`
- [ ] `:app-wearos:assemblePlayDebug` and `:app-wearos:assembleFreeDebug` both `BUILD SUCCESSFUL`

Behavioral equivalence
- [ ] `./gradlew :net:sync:wear-interface:test` green with **8** tests **before** Step 3 (Step 2 baseline) and **after** Step 3, with both runs recorded verbatim in Implementation Notes
- [ ] The existing `testEpisodesRoundTripNullTitle` (`WearSerializerTest.java:48-58`) — this task's characterization test — passes in both runs and is **unmodified**
- [ ] `net/sync/wear-interface/src/test/java/.../WearSerializerTest.java` is **byte-for-byte unchanged**: no test added, renamed, or edited (`git diff origin/develop -- net/sync/wear-interface/src/test/` is empty). Per Unknown 3, a `nowPlaying` null-title test would re-execute the shared private `episodeToJson`/`episodeFromJson` path and could not fail unless `testEpisodesRoundTripNullTitle` also failed
- [ ] Neither the PR description nor the README claims this task adds or closes any test coverage — the equivalence proof is an existing upstream test, and is described as such
- [ ] No production behavior change is claimed beyond the unreachable branch: the diff to `src/main` production code is exactly `item.title` → `item.title ?: ""` on one line, verifiable by `git diff --stat` showing 1 insertion / 1 deletion in `EpisodeDetailActivity.kt`

Scope and interop
- [ ] `git diff --name-only origin/develop` lists only files from File Scope
- [ ] No public API break: `FeedItem` is untouched, so no `:model` consumer — Java or Kotlin — is affected
- [ ] No new user-visible string, so no `:ui:i18n` change (`ui/i18n/src/main/res/values/strings.xml` unmodified)
- [ ] No new Gradle dependency, no new source set, no version-catalog change

Idiom and convention
- [ ] The fallback matches the module's three existing sites verbatim in form (`?: ""`), with no `!!` introduced anywhere
- [ ] `net/sync/wear-interface/README.md` states the null-title normalization invariant, phrased as a durable module contract rather than a reference to this task or this PR
- [ ] No comments added to the changed production line (AGENTS.md)

Reporting honesty
- [ ] Step 4's `./gradlew checkstyle lint` outcome is recorded verbatim in Implementation Notes, whether green or not
- [ ] The PR description states this removes the *last known* blocker and does **not** claim it makes `Checks` green; it names that `checkstyle lint` past `:app-wearos` and the entire `unit-test` job are executing for the first time since 2026-07-24

Not applicable: no `compose`, `navigation`, `di`, `concurrency`, or `gradle-kts` track is requested. No new composable is introduced, so no Paparazzi snapshot, accessibility, or dark-mode criteria apply — the changed line renders an existing `Text` with an existing style, unchanged in every reachable case.

### Milestone

**Not a billable migration milestone.** Standalone repo-hygiene / CI-unblock work on the portfolio project — the third and, per run `31628870547`, last known of the three independent blockers on `checks.yml`'s `static-analysis` job, after PR #23 and PR #25 (both merged 2026-08-12). It unblocks **Milestone 15b** (`tasks/antennapod-sync-settings-before-screenshot-milestone-15b.md`, PR #22) whose AC13 requires a real CI signal.

For the case study, its value is narrative rather than billable: it is the cleanest available example of the `kotlin` track's J2K-widening hazard — the same root cause as the two SpotBugs siblings, but manifesting as a hard compile error in the one consumer module the migration milestones never compiled, because every other `:model` consumer is still Java.

### Out of Scope

- **Tightening `FeedItem.title` to non-null**, or any other `:model` change — see Unknown 1
- **Fixing the module's three other title sites** — already correct
- **Any `:app-wearos` health pass**: no test source set, no Compose test infrastructure, no dependency updates, no warning cleanup. Research confirmed no second latent error, and AGENTS.md's minimal-diff rule governs
- **Compose UI / Paparazzi infrastructure** anywhere in the repo — see Unknown 3
- **Adding `:app-wearos` and `:net:sync:wear-interface` to `AGENTS.md`'s module list** — Open Question 1
- **Anything Step 4 surfaces outside `:app-wearos`** — report, do not fix; new task
- **Release-build (`FreeRelease` R8/minify) failures** if `unit-test` newly exposes any — Unknown 6; new task
- **Rebasing or re-running Milestone 15b's PR #22** — separate task, tracked in the checkpoint
- **Converting `:net:sync:wear-interface` tests to Kotlin** — that module is not migrated and is not on any milestone
- **Adding any test to `WearSerializerTest.java`** — including the `testNowPlayingRoundTripNullTitle()` an earlier draft of this Plan proposed. It would re-execute the shared private `episodeToJson`/`episodeFromJson` path and could not fail independently of `testEpisodesRoundTripNullTitle`. See Unknown 3
- **Closing `WearSerializer`'s real uncovered branches** — the `JSONException` catches in `episodesFromBytes`/`feedsFromBytes`, and `nowPlayingFromBytes` returning `null` for malformed *non-empty* bytes (only the empty-array case is covered today). This is genuine missing coverage, but it is serializer error-path test debt unrelated to a nullable-title compile error. Its own task if wanted — see Open Question 4

## Open Questions

_For José. None of these block implementation — the Plan is executable as written._

1. **`AGENTS.md`'s module list omits `:app-wearos` and `:net:sync:wear-interface`** (Research Unknown 7). Both exist, both have `README.md` files, and both are load-bearing for this task. Adding them is a two-line documentation fix, but it is unrelated to a compile error and AGENTS.md's own minimal-diff rule argues against bundling it. Want it as a separate trivial PR, or left alone as upstream's business?

2. **Merge order is no longer a constraint, but confirm the framing.** Research's Unknown 5 assumed PR #25 was still open; it merged at 2026-08-12T18:40Z, and CI run `31628870547` on the resulting `d1e1bd127` now fails at `:app-wearos:compileFreeDebugKotlin` alone. So this PR can merge whenever. Confirm you are comfortable with the deliberately hedged PR wording in Unknown 5 — it is more conservative than "this fixes CI," and that hedge is load-bearing for the case study's credibility if Step 4 or the `unit-test` job turns up something else.

3. **If Step 4 shows `./gradlew checkstyle lint` green repo-wide, do you want that stated in the PR?** It would be the strongest honest claim available ("the full `static-analysis` command passes locally"), but it is a local result on a developer machine, not a CI result, and CI has 19 days of unexecuted tasks behind it. My recommendation is to state it as a local observation and let the actual CI run speak for itself. Flagging because it is a public-facing claim on portfolio work.

4. **`WearSerializer` has three genuinely uncovered error branches — want a separate test-debt task?** (Surfaced while correcting Unknown 3 under red-team Loop 1.) The `JSONException` catches in `episodesFromBytes` and `feedsFromBytes` are untested, and `nowPlayingFromBytes`'s malformed-but-non-empty → `null` path is untested (`testNowPlayingFromBytesEmpty` only covers `data.length == 0`). This is real coverage worth having, in a module the watch app fully depends on. I deliberately kept it out of this one-line compile fix rather than bundling it. Say the word and it becomes its own small task.

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-08-12 | Loop 1 of max 2_

### Verdict
CHALLENGE

### Concerns

- **Severity:** MAJOR
- **Class:** Characterization tests prove equivalence, not just existence / Coverage gaps left unaddressed
- **Concern:** Step 2's new test, `testNowPlayingRoundTripNullTitle()`, does not close the gap the Plan claims it closes. I read `net/sync/wear-interface/src/main/java/.../WearSerializer.java` in full: `nowPlayingToBytes`/`nowPlayingFromBytes` and `episodesToBytes`/`episodesFromBytes` both delegate all title (de)serialization to the same two private methods, `episodeToJson` and `episodeFromJson`. The only logic that differs between the two public entry points is JSON-array-wrapping and the `is_playing` field — neither touches title handling. The existing `testEpisodesRoundTripNullTitle` (`WearSerializerTest.java:48-58`) already fully exercises `episodeFromJson`'s null-to-`""` normalization. The planned new test invokes the identical private code path through a different public wrapper; it is not a second "leg" of the invariant, it is the same leg re-executed under a different name, and it would not fail in any scenario where the existing test wouldn't also fail.
- **Evidence:** Plan, Unknown 3, point 4: *"nowPlayingFromBytes is the other entry point feeding EpisodeDetailActivity... nothing currently asserts it is null-title-safe... This converts the fix's central correctness claim from a code-reading argument into a test that fails if anyone ever weakens the serializer."* Contradicted by `WearSerializer.java`: `nowPlayingToBytes` calls `episodeToJson(item)` (same private method `episodesToBytes` calls per-item); `nowPlayingFromBytes` calls `episodeFromJson(obj)` (same private method `episodesFromBytes` calls per-item). There is no title-handling code unique to the "now playing" path.
- **Suggested mitigation:** Either (a) drop the redundancy framing and describe the new test honestly — e.g., "regression coverage for the `nowPlaying*` wrapper methods' wiring, not a second proof of the null-title invariant, which is already fully proven by `testEpisodesRoundTripNullTitle`" — and adjust the AC/PR language accordingly, or (b) keep the test (it's cheap and harmless as regression insurance for the wrapper) but rewrite Unknown 3's justification so it does not claim to close an "uncovered leg" that, per the actual shared-helper implementation, doesn't exist. For a portfolio task whose entire pitch is verification rigor, the AC and PR description should not carry a coverage claim that doesn't survive reading the code it's about.

### Independently verified, no issue found
- Reproduced the compile error firsthand in a fresh worktree at current `origin/develop` (`d1e1bd127`, confirmed via `git fetch`): exactly one error, `EpisodeDetailActivity.kt:115:28`, matching the Plan's "Situation change" claim precisely — no drift since Research ran.
- Pulled CI run `31628870547`'s failed-step log via `gh run view --log-failed`: exactly one `FAILED` task, `:app-wearos:compileFreeDebugKotlin`. The "last known blocker" framing and the hedged PR wording (Unknown 5) are accurate, not aspirational.
- Read `FeedItem.getIdentifyingValue()`, `FeedMedia.kt:237`, and `WearSerializerTest.java:51` directly: `title` is genuinely treated as nullable in code this task doesn't own, independent of the Plan's say-so. Unknown 1's guard-vs-tighten distinction from sibling PR #23 holds against the actual code.
- Read all three sibling `?: ""` sites (`MainListActivity.kt:99`, `FeedListActivity.kt:54`, `EpisodeListActivity.kt:64`) verbatim — confirmed precedent, confirmed File Scope correctly excludes them.
- Read `net/sync/wear-interface/README.md` in full: short, list-structured; the planned addition is a genuine one-paragraph append, not a restructuring.
- Branch point (`origin/develop` @ `d1e1bd127`, not the stale local `develop` or current working-tree branch), File Scope minimality (no `FeedItem.kt`, no sibling sites, no `AGENTS.md`), and the `?: ""` vs. `stringResource` call (Unknown 2) all check out against AGENTS.md and repo state.
- No `compose`/`navigation`/`di`/`concurrency`/`gradle-kts` track concerns apply — no new composable is introduced.

Loop 1 of max 2. Planner should correct Unknown 3's justification (or the test's purpose) and re-invoke red-team.

## Red-Team Verdict — Plan
_By: legacy-android-red-team | 2026-08-12 | Loop 2 of max 2 (final)_

### Verdict
APPROVE

### Concerns

- **Severity:** MINOR
- **Class:** Silent behavior changes from mechanical translation / unverifiable equivalence claim (documentation overclaim)
- **Concern:** Step 2's instructed README wording states the null-title normalization holds "on every public entry point (`episodesToBytes`/`episodesFromBytes`, `nowPlayingToBytes`/`nowPlayingFromBytes`, and the `feeds*` pair)" and directs the developer to name "the shared private helpers as the reason it holds uniformly." That attribution is accurate for `episodes*`/`nowPlaying*` — both literally call the same private `episodeToJson`/`episodeFromJson` — but is false for the `feeds*` pair. `feedsToBytes`/`feedsFromBytes` never call `episodeToJson`/`episodeFromJson`; they contain their own separate, textually-similar-but-independent null-check against `Feed.title` (a different field on a different class). The invariant holds there today by coincidence of duplicated code, not because of a shared implementation — so the "shared helper" framing would mislead a future reader into thinking a change to `episodeToJson`/`episodeFromJson` alone keeps both halves of the contract in sync. There is also no existing test exercising a null-titled `Feed` through `feedsToBytes`/`feedsFromBytes` (`testFeedsRoundTrip`/`testFeedsRoundTripMultiple` both use non-null titles) — the `feeds*` half of the claim rests on a code read only, which is fine for a README note but should not be attributed to code that doesn't run for that path.
- **Evidence:** Plan, Step 2, "Document the contract" bullet: *"...and therefore on every public entry point (episodesToBytes/episodesFromBytes, nowPlayingToBytes/nowPlayingFromBytes, and the feeds* pair)... Phrase it as a durable module contract, naming the shared private helpers as the reason it holds uniformly."* Contradicted by `net/sync/wear-interface/src/main/java/.../WearSerializer.java:84-115` (`feedsToBytes`, `feedsFromBytes`), which contain their own inline `feed.getTitle() != null ? feed.getTitle() : ""` (`:90`) and `feed.setTitle(obj.optString(KEY_TITLE, ""))` (`:108`) — neither calls `episodeToJson`/`episodeFromJson` (verified by reading the full file; those two methods are called only at `:61`, `:75`, `:120`, `:135`, none of which are inside `feedsToBytes`/`feedsFromBytes`).
- **Suggested mitigation:** When drafting the README note in Step 2, scope the "shared private helper" causal claim to the two entry points that actually share it (`episodes*`, `nowPlaying*` — the pair this fix's equivalence proof actually depends on). If `feeds*` is mentioned at all, phrase it separately, e.g. "the same normalization pattern is independently duplicated in `feedsToBytes`/`feedsFromBytes` for `Feed.title`" — not attributed to the shared helpers. This is a wording correction for the developer to apply while executing Step 2; it does not change the fix, File Scope, or any AC, and does not warrant another planning loop.

### Independently verified against source, not the planner's report

1. **Confirmed the load-bearing new evidence firsthand.** Read `WearSerializer.java` in full (144 lines). `episodeToJson` (`:30-38`) line 33: `obj.put(KEY_TITLE, item.getTitle() != null ? item.getTitle() : "");` — exactly as claimed: null title normalizes to `""` on the way out. `episodeFromJson` (`:41-54`) line 44: `item.setTitle(obj.optString(KEY_TITLE, ""));` — normalizes on the way in. Confirmed the delegation claim too: `nowPlayingToBytes` (`:118-126`) calls `episodeToJson(item)` at `:120`, the identical private method `episodesToBytes` (`:57-67`) calls per-item at `:61`; `nowPlayingFromBytes` (`:129-141`) calls `episodeFromJson(obj)` at `:135`, the identical private method `episodesFromBytes` (`:70-81`) calls per-element at `:75`. The "byte-identical payload for null vs. empty-string title" and "strict subset of two existing tests" claims both hold: a null-titled and `""`-titled `FeedItem` produce identical JSON via `:33`, so `testNowPlayingRoundTrip` (proves the wrapper carries a title through unmutated) plus `testEpisodesRoundTripNullTitle` (proves the null→`""` normalization) together cover everything the dropped test would have covered. Also confirmed via `git diff origin/develop -- .../WearSerializer.java .../WearSerializerTest.java` (empty) that the working tree's stale branch has not touched either file, so this read is valid regardless of current checkout.
2. **Confirmed `WearSerializerTest.java` is genuinely untouched.** Read the file in full (126 lines): all 8 original tests present, no ninth test, no edits to any existing test, no `testNowPlayingRoundTripNullTitle` anywhere. File Scope now lists exactly 4 writable files (`EpisodeDetailActivity.kt`, `net/sync/wear-interface/README.md`, the task file, the checkpoint file) plus the test file explicitly called out as struck-through/read-only — matches the claimed "5 → 4" reduction precisely. No Step (1-4) references adding, renaming, or modifying any test; Step 2 explicitly instructs "Do not add, rename, or modify any test in this file," and Step 3 only re-runs the suite.
3. **Confirmed the equivalence proof is sound as rewired.** `testEpisodesRoundTripNullTitle` (`:48-58`) sets `item.setTitle(null)` on a `FeedItem`, round-trips it through `episodesToBytes`/`episodesFromBytes`, and asserts the result title is `""`. Since `episodesFromBytes` and `nowPlayingFromBytes` both bottom out in the identical private `episodeFromJson`, this test genuinely exercises the exact code path that decides whether a null title can ever reach `EpisodeDetailActivity.kt:115` — and per Research's provenance chain (`WearDataListenerService.kt` is the sole writer of `WearDataRepository`, which is the sole source of the Intent extra both `MainListActivity.kt:46` and `EpisodeListActivity.kt:35` pass to `EpisodeDetailActivity`), there is no path into that screen that bypasses `episodeFromJson`. Running it before Step 3 (baseline) and after (regression check) is a legitimate before/after characterization pattern for a fix whose changed branch is unreachable in production.
4. **Confirmed no other Plan content drifted**, beyond the necessary File Scope reduction. Spot-checked Research, Objective, Situation change, and Unknowns 1, 2, 4, 5, 6, 7 — all read as internally consistent with the Loop 1-reviewed content and with each other; none reference or depend on the dropped test. Step 1, Step 3 (the actual `item.title ?: ""` fix), and Step 4 are unchanged in substance from Loop 1. The two new Out of Scope bullets ("Adding any test to WearSerializerTest.java," "Closing WearSerializer's real uncovered branches") are consistent documentation of the Loop 1 correction, not scope expansion.
5. **Confirmed Open Question 4 stays out of Plan scope.** The three uncovered `WearSerializer` branches (the `JSONException` catches in `episodesFromBytes`/`feedsFromBytes`, and `nowPlayingFromBytes`'s malformed-non-empty→null path) are not touched by any of Steps 1-4 or File Scope — the only `WearSerializer`-related work in scope is running the existing suite unchanged (Step 2/3) and documenting the title-normalization contract in the README (Step 2). Open Question 4 correctly routes this to a separate future task rather than folding it in.

### Recommendation

**APPROVE. Plan is ready for `android-migration-developer`.** This is the final loop (2 of max 2); the Loop 1 CHALLENGE is fully resolved and independently re-verified against source, not just the planner's account of it. Pass the one MINOR concern above (README wording for the `feeds*` pair) to the developer as a correction to apply while executing Step 2 — it is a documentation-precision fix, does not touch the load-bearing equivalence claim this task depends on, and does not require another red-team pass.

## Implementation Notes
_By: android-migration-developer | 2026-08-13_

### Commit message
fix(app-wearos): guard nullable FeedItem.title at EpisodeDetailActivity render site

Restores `:app-wearos` compilation by adding the `?: ""` fallback its three sibling title-render sites already carry, at `EpisodeDetailActivity.kt:115`. No migration track — standalone repo hygiene removing the last known blocker on `checks.yml`'s `static-analysis` job (Plan rev. 2, red-team APPROVE Loop 2/2 final). Also documents the null-title normalization invariant in `net/sync/wear-interface/README.md`, with the `feeds*` attribution corrected per red-team's Loop 2 MINOR finding (separately-implemented, coincidentally-identical normalization — not routed through the shared `episodeToJson`/`episodeFromJson` helpers).

### Branch hygiene (pre-Step-1)
- Working tree was on `fix/net-download-service-nullable-param-spotbugs` (PR #25's branch, merged). `git fetch origin` → `origin/develop` = `d1e1bd127`, confirmed to contain both merged sibling PRs (`5ae7d560f` #23, `d1e1bd127` #25) via `git log origin/develop --oneline -5`.
- Branched fresh: `git checkout -b fix/app-wearos-nullable-title-compile-error origin/develop`. `HEAD` = `d1e1bd127d70c67313be4abd8fefd88f54dc6ea6`, matching PR #25's merge commit exactly.
- Unset the branch's upstream tracking (`git branch --unset-upstream`) since `checkout -b <name> origin/develop` had set it to track `origin/develop` directly — left alone, a bare `git push` later would have targeted `develop`.
- Confirmed both this task's untracked spec files (`tasks/antennapod-fix-app-wearos-compile-error.md`, `features/antennapod-fix-app-wearos-compile-error.checkpoint.md`) survived the branch switch (`git status --short`).

### Test commands run
- Step 1 (no edit, reproduce): `./gradlew --console=plain :app-wearos:compileFreeDebugKotlin` → **FAILED**, exactly one error: `EpisodeDetailActivity.kt:115:28 Argument type mismatch: actual type is 'String?', but 'String' was expected.` Matches Research/Plan exactly, no drift.
- Step 3 verification set, all run after the one-line fix:
  - `./gradlew --console=plain :app-wearos:compileFreeDebugKotlin :app-wearos:compilePlayDebugKotlin :app-wearos:ktlintCheck` → `BUILD SUCCESSFUL in 5s`, 124 actionable tasks.
  - `./gradlew --console=plain :app-wearos:lint` → `BUILD SUCCESSFUL in 12s`, 290 actionable tasks.
  - `./gradlew --console=plain :app-wearos:assemblePlayDebug :app-wearos:assembleFreeDebug` → `BUILD SUCCESSFUL in 9s`, 207 actionable tasks.
- Step 4 (report-only, repo-wide, exact `checks.yml:44-45` command): `./gradlew --console=plain checkstyle lint` → **`BUILD SUCCESSFUL in 36s`, 1970 actionable tasks (110 executed, 1860 up-to-date). Zero `FAILED` tasks, zero checkstyle violations, repo-wide** (grep for `FAILED`/`error:`/`violation` across the full log returned nothing). The `java.rmi.Remote` "missing classes for analysis" lines under several modules' `spotbugsPlayDebug`/`spotbugsDebug` tasks are pre-existing SpotBugs classpath-analysis notices, not failures or violations — `spotbugsDebug`/`spotbugsPlayDebug` complete and `lint` for each of those modules reports `BUILD SUCCESSFUL`. **This is a local observation on one developer machine, not a CI result** — per Open Question 3, it is reported as such and not overstated in the PR.

### Characterization test results
- Test: `WearSerializerTest.testEpisodesRoundTripNullTitle` (`net/sync/wear-interface/src/test/java/.../WearSerializerTest.java:48-58`) — the existing upstream test this task uses as its characterization baseline per Plan Step 2/3 (no new test written; per Unknown 3 a `nowPlaying`-flavored variant would re-execute the same shared private `episodeFromJson` path and could not fail independently).
  - **BEFORE** (`./gradlew --console=plain :net:sync:wear-interface:test`, run on the branch immediately after Step 1's reproduction, before any production edit): `BUILD SUCCESSFUL in 11s`. All 8 `WearSerializerTest` tests PASSED, including `testEpisodesRoundTripNullTitle`, in both `testDebugUnitTest` and `testReleaseUnitTest` variants (16 individual PASSED lines, 8 distinct test names each run twice).
  - **AFTER** (`./gradlew --console=plain :net:sync:wear-interface:test --rerun-tasks`, run after the Step 3 fix landed; `--rerun-tasks` used because the module's inputs hadn't changed and Gradle would otherwise report the prior result UP-TO-DATE rather than genuinely re-executing): `BUILD SUCCESSFUL in 12s`. All 8 tests PASSED again, including `testEpisodesRoundTripNullTitle`, in both variants.
  - **What it actually exercises:** `testEpisodesRoundTripNullTitle` sets `FeedItem.title = null`, round-trips the item through `WearSerializer.episodesToBytes`/`episodesFromBytes`, and asserts the deserialized title is `""`, not null. `episodesFromBytes` bottoms out in the same private `episodeFromJson` (`WearSerializer.java:41-54`) that `nowPlayingFromBytes` also calls, and per the sole-writer provenance chain (`WearDataListenerService.kt` → `WearDataRepository` → the Intent extra both `MainListActivity.kt` and `EpisodeListActivity.kt` pass to `EpisodeDetailActivity`), there is no path into the fixed line that bypasses this method. A real regression that made the serializer emit a null title again would fail this test both before and after the fix — it is genuine equivalence evidence for the changed line's unreachable branch, not a test that merely calls code without asserting.
  - `git diff origin/develop -- net/sync/wear-interface/src/test/` is empty — `WearSerializerTest.java` is confirmed byte-for-byte unchanged.

### Deviations from plan

None. The one MINOR wording correction flagged by red-team's Loop 2 (final) — the README's "shared private helper" causal claim must not extend to the `feeds*` pair, since `feedsToBytes`/`feedsFromBytes` (`WearSerializer.java:84-115`) contain their own independent inline `Feed.title` normalization and never call `episodeToJson`/`episodeFromJson` — was applied during Step 2 as instructed (this was a developer-executed correction to the Plan's wording, pre-authorized by red-team's final loop, not a deviation from the Plan). Verified directly against `WearSerializer.java` before writing the note: `episodeToJson`/`episodeFromJson` are called only at lines `61`, `75`, `120`, `135`, none of which are inside `feedsToBytes`/`feedsFromBytes`. The README note attributes the `episodes*`/`nowPlaying*` invariant to the shared helpers and describes the `feeds*` invariant as an independently-duplicated, coincidentally-identical normalization.

### File Scope compliance
`git diff --stat origin/develop` (excluding the two spec files, which are new/untracked by design):
- `app-wearos/src/main/java/de/danoeh/antennapod/wearos/EpisodeDetailActivity.kt` — 1 insertion, 1 deletion (line 115 only)
- `net/sync/wear-interface/README.md` — 4 insertions

No other file touched. `WearSerializerTest.java` confirmed unchanged (see above). No new Gradle dependency, no new source set, no version-catalog change, no `:ui:i18n` change, no `FeedItem.kt` change, no sibling `?: ""` site touched, no `AGENTS.md` change.

### Acceptance Criteria verified locally
All ACs under "Verification — the fix," "Behavioral equivalence," "Scope and interop," "Idiom and convention," and "Reporting honesty" were verified locally exactly as specified above (compile before/after, ktlintCheck, lint, both assembles, 8-test suite before/after, byte-for-byte test-file diff, File Scope diff, no new string/dependency/source-set). Nothing in this task's ACs requires manual/device verification — the module has no runtime behavior change to observe on a physical watch, since the changed branch is unreachable in production (see Characterization test results above). The PR description (not yet drafted/opened per instruction) must carry forward the hedged framing from Plan Unknown 5 verbatim in substance: "removes the last known blocker," not "fixes CI" or "restores a green Checks run," and must name that `checkstyle lint` past `:app-wearos` and the `unit-test` job are executing for the first time since 2026-07-24 in CI specifically — this developer's local Step 4 result is supporting evidence, not a substitute for that CI run.

## Code Review Verdict
_By: migration-code-reviewer | 2026-08-13 | Loop 1 of max 3_

### Verdict
APPROVE

### Findings

None. All independent verification passed; no CRITICAL, MAJOR, or MINOR findings.

### Independent verification performed (not taken on the developer's report)

1. **Branch point and File Scope.** `git merge-base HEAD origin/develop` = `d1e1bd127`, matching the Plan's Unknown 4 exactly. `git diff --name-status origin/develop` shows exactly 4 files: `app-wearos/.../EpisodeDetailActivity.kt`, `net/sync/wear-interface/README.md`, and the task's two spec files — matching File Scope precisely, no expansion.

2. **The one-line fix.** Read `EpisodeDetailActivity.kt` directly; `git diff origin/develop -- .../EpisodeDetailActivity.kt` shows exactly 1 insertion/1 deletion at line 115 (`item.title` → `item.title ?: ""`), no other change to the file, no `!!`, no comment added. Ran `./gradlew --console=plain :app-wearos:compileFreeDebugKotlin :app-wearos:compilePlayDebugKotlin --rerun-tasks` myself on the branch → `BUILD SUCCESSFUL`. Independently reproduced the **baseline** failure in a throwaway `git worktree` at `origin/develop` (`d1e1bd127`, not touching this working tree): `compileFreeDebugKotlin` fails with exactly one error, `EpisodeDetailActivity.kt:115:28 Argument type mismatch: actual type is 'String?', but 'String' was expected` — verbatim match to Research/Plan/Implementation Notes. Worktree removed after.

3. **`WearSerializerTest.java` byte-for-byte unchanged.** `git diff origin/develop -- net/sync/wear-interface/src/test/` is empty. No test added, renamed, or modified — the Plan's explicit post-Loop-1 constraint holds.

4. **Characterization baseline is real.** Ran `./gradlew --console=plain :net:sync:wear-interface:test --rerun-tasks` myself: `BUILD SUCCESSFUL`, all 8 tests in `WearSerializerTest` PASSED in both `testDebugUnitTest` and `testReleaseUnitTest`, including `testEpisodesRoundTripNullTitle`. This matches the developer's reported BEFORE/AFTER results and independently confirms the suite genuinely re-executes (not cached) and genuinely passes post-fix.

5. **README wording fix (red-team Loop 2 MINOR).** Read `WearSerializer.java:84-115` directly: `feedsToBytes`/`feedsFromBytes` contain their own inline `feed.getTitle() != null ? feed.getTitle() : ""` / `optString(KEY_TITLE, "")` checks and never call `episodeToJson`/`episodeFromJson` (those two methods are called only at lines 61, 75, 120, 135 — all outside `feedsToBytes`/`feedsFromBytes`). The actual README diff correctly separates the claims: "the shared private helpers" is scoped to `episodes*`/`nowPlaying*`, and `feeds*` is described as applying "the same null-to-`""` normalization... via their own inline checks rather than the shared helpers." The attribution is accurate and the correction was genuinely applied, not just claimed.

6. **File Scope exactness / untouched siblings.** `grep`-confirmed the three sibling sites (`MainListActivity.kt:99`, `FeedListActivity.kt:54`, `EpisodeListActivity.kt:64`) are unchanged (still `?: ""`), and `git diff --stat origin/develop -- app-wearos/ net/sync/wear-interface/ model/` shows only the two files noted above — `model/` and every other `:app-wearos`/`:net:sync:wear-interface` file are untouched.

7. **Repo-wide report-only gate (Step 4).** Ran `./gradlew --console=plain checkstyle lint` from repo root myself: `BUILD SUCCESSFUL in 20s`, 1970 actionable tasks, zero FAILED tasks. The only non-routine log lines are the same pre-existing `java.rmi.Remote`/collection-class SpotBugs "missing classes for analysis" notices the developer's report named, on `:ui:preferences:spotbugsPlayDebug` and `:app:spotbugsPlayDebug` — not failures. This independently confirms the developer's Step 4 report is accurate, not overstated, and correctly labeled as a local (not CI) observation in the Implementation Notes.

8. **Also ran independently, all green:** `:app-wearos:ktlintCheck`, `:app-wearos:lint`, `:app-wearos:assemblePlayDebug`, `:app-wearos:assembleFreeDebug`.

9. **Commit message / reporting discipline.** Read all 3 commits (`856f81f3b`, `c9816ff54`, `1afccb35a`) verbatim. None claims "fixes CI" or "restores a green Checks run"; the fix commit states the technical rationale (unreachable branch, characterization baseline) without any CI-status claim, consistent with the Plan's Unknown 5 discipline. The PR description itself is correctly not yet drafted, per the operating instruction to stop for code review first; Implementation Notes correctly commit to carrying the hedged "last known blocker" framing forward when it is opened.

### Notes for the next loop (red-team implementation)
No handoff concerns beyond what the Plan already flagged (release-build risk under Unknown 6, the three genuinely-uncovered `WearSerializer` error branches under Open Question 4) — both correctly left out of this diff.

## Red-Team Verdict — Implementation
_By: legacy-android-red-team | 2026-08-13 | Loop 1 of max 2_

### Verdict
APPROVE

### Concerns

None. Every equivalence claim in the Plan and Implementation Notes was independently re-derived from source and from fresh command runs, not taken on the developer's or code reviewer's word — nothing survived scrutiny as an unproven claim.

### Independently verified (fresh reads and fresh command runs, not re-checking the prior two loops' work)

1. **The fallback's actual runtime effect, traced end-to-end, not just cited.** Read `EpisodeDetailActivity.kt` in full. `EXTRA_EPISODE` is populated at exactly two call sites — `MainListActivity.kt:46` and `EpisodeListActivity.kt:35` — both sourcing the `FeedItem` from `WearDataRepository` (`app-wearos/.../sync/WearDataRepository.kt`), whose only writer is `WearDataListenerService.onMessageReceived` (`app-wearos/.../sync/WearDataListenerService.kt:12-30`). Every branch there (`NOW_PLAYING`, `QUEUE`/`DOWNLOADS`/`EPISODES`, `FEED_EPISODES_PREFIX`) routes through `WearSerializer.episodesFromBytes` or `nowPlayingFromBytes`, and I read `WearSerializer.java` in full myself: both public methods bottom out in the identical private `episodeFromJson` (`:41-54`), whose line 44 is `item.setTitle(obj.optString(KEY_TITLE, ""))` — `optString` never returns null. I also confirmed `AndroidManifest.xml:43-45` declares `EpisodeDetailActivity` `android:exported="false"`, and grepped the file and its ViewModel for `onSaveInstanceState`/Bundle-restoration logic — none exists. So there is no deep link, no external Intent, and no saved/restored state that could hand this Activity a `FeedItem` bypassing the serializer. The provenance chain in Research/Plan holds under independent re-derivation, not just re-reading the prose that asserted it.
2. **The characterization-baseline substitution is sound, re-confirmed by running the suite myself from a clean state.** Ran `./gradlew --console=plain :net:sync:wear-interface:test --rerun-tasks` fresh: `BUILD SUCCESSFUL`, all 8 `WearSerializerTest` tests PASSED in both Debug and Release unit-test variants, including `testEpisodesRoundTripNullTitle`. Read `WearSerializerTest.java` in full (126 lines, unchanged from `origin/develop` per `git diff origin/develop -- net/sync/wear-interface/src/test/` returning empty) — confirmed there is no ninth test and no edit to any of the 8. Since `episodesFromBytes` and `nowPlayingFromBytes` share the identical private `episodeFromJson`, and the provenance chain above shows no other path into `EpisodeDetailActivity`, this existing test genuinely is dispositive for the changed line — not a borrowed test papering over a gap.
3. **The README note is accurate on independent re-read, including the `feeds*` scoping.** Read `WearSerializer.java:84-115` (`feedsToBytes`/`feedsFromBytes`) directly: they use their own inline `feed.getTitle() != null ? feed.getTitle() : ""` (`:90`) and `optString(KEY_TITLE, "")` (`:108`) and never call `episodeToJson`/`episodeFromJson` (those are called only at `:61,75,120,135`). The committed README (`net/sync/wear-interface/README.md:22-24`) correctly attributes the shared-helper causal claim only to `episodes*`/`nowPlaying*`, and describes `feeds*` as "independently apply[ing] the same null-to-`\"\"` normalization... via their own inline checks rather than the shared helpers" — this does not overclaim shared implementation for a pair that only coincidentally matches, and does not imply test coverage that doesn't exist (`testFeedsRoundTrip`/`testFeedsRoundTripMultiple` both use non-null titles only).
4. **File Scope and commit hygiene.** `git diff --name-status origin/develop` (excluding the two spec files) shows exactly two files: `EpisodeDetailActivity.kt` (1/1 line) and `net/sync/wear-interface/README.md` (+4 lines) — matches File Scope exactly. Read all three commits (`856f81f3b`, `c9816ff54`, `1afccb35a`) individually: the README commit touches only the README, the fix commit touches only `EpisodeDetailActivity.kt`, and neither commit's diff references or depends on the other's content — either could be reverted independently without breaking the other (the fix doesn't cite the README, and the README doesn't reference the fixed line by number in a way that would go stale). Confirmed `ui/i18n/src/main/res/values/strings.xml`, `AGENTS.md`, `app-wearos/README.md`, `gradle/libs.versions.toml`, `.github/workflows/checks.yml`, and `model/` are all untouched (`git diff origin/develop` against each returns empty).
5. **The "last known blocker" framing is still accurate, re-checked against current CI state, not just the Plan's snapshot of it.** `git fetch origin` + `git log origin/develop --oneline -3` shows `origin/develop` is still `d1e1bd127` — unchanged since the Plan was written, no new commits landed. `gh run list --branch develop --workflow Checks --limit 5` confirms the most recent run (`31628870547`, triggered by PR #25's merge) is still the latest and still `failure`. Nothing has changed on `develop` that would make the hedged framing stale or, conversely, that would justify strengthening it to "restores a green build" — `unit-test`'s `assemble` steps and `emulator-test` genuinely have not executed since 2026-07-24, so an unrelated failure further down the job graph remains a live, un-eliminated possibility. The Implementation Notes' commitment to carry forward "removes the last known blocker" (not "fixes CI") into the eventual PR description is the correct call as of this review, not merely as of the Plan's.
6. **Independently re-ran the core build/compile verification from scratch** (not trusting the developer's or code reviewer's transcripts): `./gradlew --console=plain :app-wearos:compileFreeDebugKotlin :app-wearos:compilePlayDebugKotlin --rerun-tasks` → `BUILD SUCCESSFUL in 6s`, 121 actionable tasks, all executed (not UP-TO-DATE). Matches both prior reports.
7. **Rendering behavior of the fallback, considered and correctly judged moot.** `EpisodeDetailScreen`'s `Text(text = item.title ?: "")` (`:114-127`) has no placeholder string for an empty title — an empty string would render as blank space in that slot, not e.g. "Untitled Episode." This would be a legitimate UX concern if the branch were reachable. It is not: point 1 above closes that question independently, so there is no user-facing scenario in which this matters. Noting it here as "considered and dismissed," per this review's operating rule against silent dismissal, rather than omitting it.

### Categories considered with no issue found
`compose`/`navigation`/`di`/`concurrency`/`gradle-kts` tracks: not applicable, no new composable, no DI/coroutine/build-script change. Public API breakage: none — `FeedItem` is untouched, no `:model` consumer (Java or Kotlin) is affected. Milestone/scope creep: none — diff is exactly the two File Scope production/doc files, no architecture change, no unrelated refactor.

### Recommendation
**APPROVE. Ready to open the PR** with the hedged framing from Plan Unknown 5 / Implementation Notes carried forward verbatim in substance ("removes the last known blocker," not "fixes CI" or "restores a green Checks run"), naming that `checkstyle lint` past `:app-wearos` and the entire `unit-test`/`emulator-test` job graph are executing for the first time in CI since 2026-07-24.
