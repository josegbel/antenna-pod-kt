# :net:download:service

The download service.
Uses WorkManager workers (`EpisodeDownloadWorker`, `FeedUpdateWorker`) for background downloads and feed refreshes.

- This module's `DownloadServiceInterfaceImpl` and `FeedUpdateManagerImpl` inherit `@Nullable`-annotated
  `Context`/`FeedItem`/`FeedMedia` parameters from their Kotlin superclasses in
  `:net:download:service-interface`, but every flagged method dereferences its parameter
  unconditionally. This is a J2K artifact, not a defect introduced here: an unannotated Java
  abstract-method parameter has no Kotlin equivalent, so migrating the superclass to Kotlin (PR #21)
  widened every one of them to nullable. The resulting nine `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE`
  findings are suppressed, method-scoped, in `config/spotbugs/exclude.xml`. Three of the nine
  (`cancel`'s `media` parameter, `restartUpdateAlarm`, `runOnce(Context, Feed, boolean)`) are
  suppressed on the strength of null-check guards added at their call sites in `:app`
  (`CancelDownloadActionButton.java`, `DownloadsPreferencesFragment.java`, `FeedItemlistFragment.java`,
  `FeedSettingsPreferenceFragment.java`, `EditUrlSettingsDialog.java`). The other six
  (`downloadNow`, `download`, `cancelAll`, `getNumberOfActiveDownloads`, and `cancel`'s `context`
  parameter) are suppressed because no caller has ever passed null to them — no guard was added or
  needed.
- **The suppressions are unconditional and permanent.** SpotBugs performs no caller analysis on this
  detector, so it cannot observe whether a call-site guard exists. Removing one of the guards named
  above will not make its finding reappear and will not fail CI — the suppression keeps matching the
  unchanged implementation method regardless. This README note, the `exclude.xml` comments, and code
  review are the only things tying the suppressions to the guards; only the `CancelDownloadActionButton`
  guard has an automated test (`CancelDownloadActionButtonTest`) behind it.
- `AutoDownloadManagerImpl` carries the identical inherited-nullable widening on
  `AutoDownloadManager.kt` but has zero findings today, because both its methods
  (`autodownloadUndownloadedItems`, `performAutoCleanup`) hand `context` to unannotated Java sinks
  (`AutomaticDownloadAlgorithm.autoDownloadUndownloadedItems`,
  `EpisodeCleanupAlgorithmFactory.build().performCleanup`). Annotating or migrating either sink to
  Kotlin will produce two new findings of the same shape.
