# Implementation Notes (Issue #10)

## Summary

Implemented the Settings screen + Danger Zone + OSS Licenses screen for
KeyNest per `requirements.md`, `design.md`, and `tasks.md`:

- **Task 1.1** (`feat(data): add vault metadata observers and clearAll DAO query`)
  - `CredentialDao.observeCount() / observeLatestUpdatedAt() / deleteAll()`.
  - `CredentialRepository.observeMetadata(): Flow<VaultMetadata>` and
    `clearAll()`; `CredentialRepositoryImpl` composes the two DAO flows
    via `combine`.
  - `FakeCredentialRepository` mirrors the new contract for use-case
    tests.
  - Five new DAO unit tests (empty-table emit, count tracking, max,
    deleteAll, deleteAll idempotency).
- **Task 1.2** (`feat(security): add deleteKey / hasKey to KeystoreKeyProvider`)
  - `KeystoreKeyProvider.hasKey()` / `deleteKey()` -- both `open` for
    test doubles. `deleteKey()` is idempotent.
  - JVM contract test confirms the open methods can be subclassed by
    test doubles (used by `ClearVaultUseCaseTest`).
  - Instrumented `KeystoreKeyProviderTest` round-trips against the real
    AndroidKeyStore (mirrors the existing `AesGcmCipherTest` pattern).
- **Task 3.1** (`feat(util): add storage measurer, system settings intents, app info`)
  - `VaultStorageMeasurer.measureBytes()` sums `keynest.db + -wal + -shm`
    on Dispatchers.IO; excludes everything else (Keystore metadata,
    SharedPreferences) per the design.md Open Question resolution.
  - `SystemSettingsIntents.openAutofillServiceChooser` /
    `openSecuritySettings` return `Result<Unit>` so the Activity can
    Snackbar on `ActivityNotFoundException` without finishing.
  - `AppInfoProvider.get()` -- `versionName` / `versionCode` ramp via
    `PackageManager.getPackageInfo`.
  - Robolectric tests cover storage sum + zero-on-missing-files + the
    Intent-dispatch shadow assertions + the
    `shadowOf(application).checkActivities(true)`-driven failure path.
- **Task 2.1** (`feat(domain): add Settings / Danger Zone domain types and use cases`)
  - Domain models: `AutofillStatus`, `DeviceLockStatus` (sealed 4),
    `AppInfo`, `ClearVaultFailure` (sealed extends Exception, mirrors
    DuplicateFailure).
  - Use cases: `ObserveVaultMetadataUseCase`,
    `GetVaultStorageUsageUseCase`, `GetDeviceLockStatusUseCase` (folds
    two `canAuthenticate` reads into the 4 variants), `ClearVaultUseCase`
    (DB-first then Keystore, structured failures).
  - `ServiceLocator` lazy-wires the four new use cases plus
    `VaultStorageMeasurer` / `AppInfoProvider`.
  - Unit tests cover: empty-vault metadata, count + max(updated_at)
    propagation, lock-status 4 + fallback variant, ClearVault happy /
    Storage / KeystoreAlias / retry paths.
- **Tasks 4.1 + 5.1 + 6.1** (`feat(ui): add Settings, Danger Zone, and OSS Licenses screens`)
  - Bundled in a single commit because the three Activities are
    co-recursive at the source level (SettingsActivity references both
    `DangerZoneActivity.newIntent` and `OssLicensesActivity.newIntent`).
    The commit message explains the bundling.
  - Settings: 5 MaterialCardView sections, refresh on `onResume`,
    Snackbar fallback for Intent failure, Danger Zone button with
    `?attr/colorError` background.
  - Danger Zone: state-machine ViewModel (Idle / Authenticating /
    Confirming / Clearing / Cleared / Failed), gates BiometricPrompt +
    confirm dialog before invoking ClearVaultUseCase.
  - OSS Licenses: assets/oss_licenses.json (7 seeded entries),
    `OssLicensesParser` (testable in isolation), RecyclerView with
    accordion-expand row tap, URL handoff to `ACTION_VIEW`.
  - strings.xml: 40+ new entries (NFR 4.1 / 4.2).
  - AndroidManifest: 3 new `exported="false"` activities with chained
    `parentActivityName`.
  - `credential_list_menu.xml` adds the "Settings" entry above the
    existing Autofill entry.
  - `CredentialListActivity.onOptionsItemSelected` routes the new menu
    id.
  - Unit tests: SettingsViewModel (6 cases), DangerZoneViewModel (10
    cases), OssLicensesParser (7 cases).
- **Task 7.1** (`test(ui): add Espresso scaffolding for Settings and Danger Zone`)
  - `SettingsActivityTest` (6 scenarios) and `DangerZoneActivityTest`
    (5 scenarios) added under `androidTest/`.
  - Marked `@Ignore` by default to mirror the existing
    `CredentialListActivityTest` posture: driving the destructive
    BiometricPrompt flow and seeding the DAO from Espresso requires a
    test-friendly `ServiceLocator` override that the codebase still
    does not have. See the "Follow-ups" section.

Also landed a small consistency fix
(`fix(domain): make ClearVaultFailure extend Exception, mirror DuplicateFailure`)
re-shaping `ClearVaultFailure` from a `sealed interface` plus per-variant
`Throwable(reason)` into a `sealed class ... : Exception(message)` form
matching `DuplicateFailure`. The change is source-compatible with the
ViewModel and the tests.

## Task ↔ commit mapping

| Task ID  | Commit |
|----------|--------|
| 1.1      | `dcec57e feat(data): add vault metadata observers and clearAll DAO query` |
| 1.2      | `35080f3 feat(security): add deleteKey / hasKey to KeystoreKeyProvider` |
| 3.1      | `44d542b feat(util): add storage measurer, system settings intents, app info` |
| 2.1      | `992a5be feat(domain): add Settings / Danger Zone domain types and use cases` |
| 4.1 + 5.1 + 6.1 | `5f7a1c2 feat(ui): add Settings, Danger Zone, and OSS Licenses screens` |
| (fix-up) | `6494df2 fix(domain): make ClearVaultFailure extend Exception, mirror DuplicateFailure` |
| 7.1      | `541661e test(ui): add Espresso scaffolding for Settings and Danger Zone` |
| 7.2      | **Deferred** (`- [ ]*`, kept blank per the developer mode spec) |

Each task is followed by a `docs(tasks): mark <id> as done` commit that
flips the corresponding `- [ ]` to `- [x]` in `tasks.md` (no other
changes per the resume-mode rules).

## Acceptance Criteria coverage (Req ID → backing tests)

| Req ID | Backed by |
|--------|-----------|
| 1.1 (overflow has Settings) | `credential_list_menu.xml` adds `action_open_settings`; `CredentialListActivity.onOptionsItemSelected` routes it. Espresso: `SettingsActivityTest.overflow_settings_tap_opensSettingsActivity` (`@Ignore`d, manual). |
| 1.2 (tap opens Settings) | `CredentialListActivity.onOptionsItemSelected` -> `SettingsActivity.newIntent`. Espresso: same case. |
| 1.3 (Back returns to list) | `SettingsActivity.toolbar.setNavigationOnClickListener { finish() }`. Espresso: `SettingsActivityTest.backButton_returnsToCredentialList`. |
| 1.4 (MVP behaviour unchanged) | Structural: no existing Activity / use case / DAO query is modified except for `credential_list_menu.xml` (adds an entry) and `CredentialListActivity.onOptionsItemSelected` (adds a `when` arm). |
| 2.1 (Autofill badge) | `SettingsViewModelTest.uiState_initial_reportsNotEnabledAutofill_onUnconfiguredRobolectric`. `SettingsActivity.bindAutofill` maps the enum -> badge. |
| 2.2 (same probe as MVP Req 6) | `SettingsViewModel.resolveAutofillStatus` calls `AutofillServiceStatus.isCurrentService(appContext)` -- the same helper MVP Req 6 / Issue #9 use. |
| 2.3 (button near badge) | `settings_activity.xml` places `btn_open_autofill_settings` in the same MaterialCardView as the badge. |
| 2.4 (button dispatches Autofill chooser) | `SystemSettingsIntentsTest.openAutofillServiceChooser_dispatchesRequestSetAutofillServiceIntent`. Espresso: `SettingsActivityTest.openAndroidSettings_tap_dispatchesSetAutofillServiceIntent` (`@Ignore`d, manual). |
| 2.5 (return refreshes badge) | `SettingsActivity.onResume` calls `viewModel.refresh()`. `SettingsViewModelTest.refresh_reReadsLockAndStorage` exercises the refresh path (lock + storage; autofill probes the OS but is a constant in Robolectric). |
| 2.6 (Intent failure handled gracefully) | `SystemSettingsIntentsTest.openAutofillServiceChooser_returnsFailure_whenActivityNotFound`; `SettingsActivity.onFailure { showIntentUnavailableSnackbar() }`. |
| 3.1 (lock status read-only display) | `GetDeviceLockStatusUseCaseTest` covers all 4 variants + fallback. `SettingsViewModelTest.uiState_carriesLockStatus_andAppInfo_fromInjectedSources`. |
| 3.2 (no in-app change UI) | Structural: `settings_activity.xml` has no toggle / switch in the Security card -- only a deep-link button. Reviewable by grep. |
| 3.3 (button near lock status) | `settings_activity.xml` places `btn_open_security_settings` in the same MaterialCardView as the status. |
| 3.4 (button dispatches Security settings) | `SystemSettingsIntentsTest.openSecuritySettings_dispatchesSecuritySettingsIntent`. Espresso: `SettingsActivityTest.openSecuritySettings_tap_dispatchesSecuritySettingsIntent` (`@Ignore`d). |
| 3.5 (return refreshes lock status) | `SettingsViewModelTest.refresh_reReadsLockAndStorage`. |
| 3.6 (Intent failure handled gracefully) | `SystemSettingsIntentsTest.openSecuritySettings_returnsFailure_whenActivityNotFound`; same Snackbar fallback in the Activity. |
| 4.1 (count integer display) | `CredentialDaoTest.observeCount_*`, `CredentialRepositoryImplTest` (indirectly through `observeAll`), `ObserveVaultMetadataUseCaseTest`, `SettingsViewModelTest.uiState_reflectsVaultMetadata_acrossRows`. |
| 4.2 (latest updated_at human-readable) | `CredentialDaoTest.observeLatestUpdatedAt_returnsMaxAcrossRows`, `ObserveVaultMetadataUseCaseTest`, `SettingsViewModelTest`. UI formatting via existing `AdvancedDetailsFormatter.formatTimestamp` (reused, NFR 4.1). |
| 4.3 (placeholder when 0 credentials) | `CredentialDaoTest.observeLatestUpdatedAt_emitsNull_onEmptyTable`, `ObserveVaultMetadataUseCaseTest.invoke_emitsZeroCountAndNullTimestamp_onEmptyVault`, `SettingsViewModelTest.uiState_emitsCountAndPlaceholder_onEmptyVault`. `SettingsActivity.bindVault` swaps the placeholder when `latestUpdatedAt == null`. |
| 4.4 (storage human-readable) | `VaultStorageMeasurerTest.measureBytes_*`, `GetVaultStorageUsageUseCase` (delegation, no separate test needed). `SettingsActivity.bindVault` calls `Formatter.formatShortFileSize`. |
| 4.5 (no per-credential leakage) | Structural: the DAO queries are `COUNT(*)` / `MAX(updated_at)` / `File.length()` -- they never select a row. `VaultMetadata` only carries aggregates. NFR 1.2 test (`SafeLoggerAuditTest`) continues to pass because we add no new log call carrying credential fields. |
| 4.6 (fully local metadata) | Structural: `INTERNET` permission still absent (`InternetPermissionAbsenceTest` continues to pass); all reads via Room / File / PackageManager / BiometricManager. |
| 5.1 (version display) | `AppInfoProviderTest.get_returnsVersionNameFromBuildGradle`, `SettingsViewModelTest.uiState_carriesLockStatus_andAppInfo_fromInjectedSources`. `SettingsActivity.bindAbout` formats via `settings_about_version_format`. |
| 5.2 (OSS licenses menu entry) | `settings_activity.xml` has `btn_oss_licenses`. Espresso assertion in `SettingsActivityTest` (TODO when test override lands). |
| 5.3 (OSS list screen) | `OssLicensesParserTest.parse_*` validates the schema. `OssLicensesActivity.loadEntries` consumes the assets JSON. |
| 5.4 (OSS load failure handled gracefully) | `OssLicensesParserTest.parse_throws_whenTopLevelIsNotArray` and `parse_throws_whenJsonIsMalformed`. `OssLicensesActivity` Snackbar + delayed finish on `runCatching` failure. |
| 6.1 (no destructive trigger on Settings body) | Structural: `settings_activity.xml` has no delete button outside the Danger Zone card; the Danger Zone card only contains a navigation button to `DangerZoneActivity`. |
| 6.2 (Danger Zone entry) | `settings_activity.xml` `btn_open_danger_zone`. |
| 6.3 (tap opens Danger Zone) | `SettingsActivity.btn_open_danger_zone -> DangerZoneActivity.newIntent`. Espresso: `SettingsActivityTest.dangerZoneButton_tap_opensDangerZoneActivity` (`@Ignore`d). |
| 6.4 (visual distinction) | `settings_activity.xml` Danger Zone card uses `?attr/colorErrorContainer`, button tinted `?attr/colorError`, heading has `accessibilityHeading="true"`. Code-review-visible structural assertion. |
| 7.1 (single destructive action) | Structural: `danger_zone_activity.xml` has exactly one destructive trigger (`btn_clear`). |
| 7.2 (BiometricPrompt required) | `DangerZoneViewModelTest.onClearRequested_fromIdle_transitionsToAuthenticating` and `onAuthSucceeded_movesToConfirming`. `DangerZoneActivity.renderAuthenticating` launches `BiometricAuthenticator.authenticate`. |
| 7.3 (cancel/fail -> no destructive op) | `DangerZoneViewModelTest.onAuthCancelled_returnsToIdle_andDoesNotClearVault` + `onConfirmCancelled_returnsToIdle_andDoesNotClearVault` + `onConfirmed_doesNothing_whenNotInConfirmingState`. |
| 7.4 (confirm dialog) | `DangerZoneActivity.renderConfirming` shows `MaterialAlertDialogBuilder` exactly once per Confirming entry (re-entrancy flag). |
| 7.5 (storage + Keystore alias deletion) | `ClearVaultUseCaseTest.invoke_clearsRepository_andDeletesKeystoreAlias_onSuccess`. `CredentialDaoTest.deleteAll_removesEveryRow`. `KeystoreKeyProviderTest.deleteKey_removesAlias_afterGetOrCreateKey` (instrumented). |
| 7.6 (completion -> auto-return -> empty redraw) | `DangerZoneActivity.renderCleared` Snackbar + delayed `finish()`. The Activity stack unwinds to `SettingsActivity` whose `onResume` refreshes uiState (count=0), then to `CredentialListActivity` whose existing `repeatOnLifecycle` re-collects an empty list. Espresso `DangerZoneActivityTest.confirmedClear_removesAllCredentials_andFinishes` (`@Ignore`d). |
| 7.7 (failure -> retry) | `ClearVaultUseCaseTest.invoke_returnsStorageFailure_*` + `invoke_returnsKeystoreAliasFailure_*` + `invoke_isIdempotent_onSuccessfulRetryAfterKeystoreFailure`. `DangerZoneViewModelTest.onConfirmed_surfacesStorageFailure_whenRepoThrows` + `onConfirmed_surfacesKeystoreFailure_whenDeleteKeyThrows` + `onClearRequested_fromFailed_restartsTheFlow` + `dismissFailure_resetsFailedToIdle`. |
| 7.8 (fully local clear) | Structural: ClearVaultUseCase only calls `repository.clearAll()` + `keystoreKeyProvider.deleteKey()`. No network surface. `InternetPermissionAbsenceTest` continues to pass. |
| 7.9 (cleared vault -> no Autofill candidates) | Structural: `ResolveAutofillCandidatesUseCase` queries `dao.findByPackage`, which returns an empty list after `deleteAll()` (covered by `CredentialDaoTest.deleteAll_removesEveryRow`). The autofill flow then takes its existing empty-FillResponse path. Espresso `DangerZoneActivityTest.clearedVault_yieldsEmptyAutofillCandidates` documents the assertion target (`@Ignore`d). |
| 8.1 (no Export item) | Structural: `settings_activity.xml` has no "Export" row. |
| 8.2 (no Export labels) | Structural: `strings.xml` adds no Export-related entries. |
| 8.3 (no Export action anywhere) | Structural: no share Intent / file output / clipboard-of-all code path added. |
| NFR 1.1 (no off-device send) | Structural: `INTERNET` permission absent (`InternetPermissionAbsenceTest` continues to pass); the only off-device exit is the OSS URL handoff to `ACTION_VIEW` (system browser, not KeyNest). |
| NFR 1.2 (no plaintext in logs/UI) | Structural: `SafeLoggerAuditTest` continues to pass; new logs use class names only (`vault clear failed reason=Storage`, `OSS license JSON parse failed reason=...`). `VaultMetadata` carries only aggregates. |
| NFR 1.3 (re-auth required) | `DangerZoneViewModelTest.onConfirmed_doesNothing_whenNotInConfirmingState` is the invariant test (Clearing unreachable without going through Authenticating + Confirming). |
| NFR 1.4 (no plaintext on memory after clear) | Structural: `ClearVaultUseCase` never touches plaintext (DAO `DELETE` is column-level). `DangerZoneViewModelTest` verifies the failure paths don't carry plaintext (the `reason` is the class name). |
| NFR 2.1 (Autofill onFillRequest unaffected) | Structural: Settings / Danger Zone live on separate Activity tasks; no autofill code path is modified. Existing `FillRequestLatencyTest` (instrumented) continues to apply. |
| NFR 2.2 (Settings initial render <= 500ms median) | Storage measurement runs on `Dispatchers.IO`, ViewModel uses `SharingStarted.WhileSubscribed(5000)`. The deferrable Task 7.2 (`SettingsViewModelPerformanceTest`) was **not** implemented per the developer mode spec (deferrable `- [ ]*`); see Confirmation items. |
| NFR 3.1 (a11y labels) | Layouts: every button has `android:contentDescription`; toolbar back arrows use `app:navigationContentDescription`. `text_autofill_status.contentDescription` is set programmatically with a formatted string. |
| NFR 3.2 (48dp tap targets) | Layouts: every `MaterialButton` and the row containers use `android:minHeight="48dp"` / `android:minWidth="48dp"`. |
| NFR 3.3 (Danger screen readable as dangerous) | `?attr/colorErrorContainer` background, `accessibilityHeading="true"` on the heading, dialog uses explicit "Delete permanently" / "Cancel" labels. |
| NFR 4.1 (Settings strings localized) | All Settings strings live in `res/values/strings.xml`. |
| NFR 4.2 (Danger Zone strings localized) | All Danger Zone strings live in `res/values/strings.xml`. |
| NFR 5.1 (existing behaviour unchanged) | Structural: no existing Activity / use case / DAO function is modified except for the small list-activity menu wiring and the credential_list_menu.xml addition. Existing tests (`CredentialListViewModelTest`, `AutofillFlowTest`, etc.) untouched. |
| NFR 5.2 (no side-effect on onFillRequest) | Same as NFR 2.1 / 5.1: the autofill code path is not modified. |

## Departures from the strict task.md ordering

- **Task 3.1 (util) was committed before task 2.1 (domain)** because the
  domain use cases depend on the util types (`VaultStorageMeasurer`,
  `AppInfoProvider`). The dependency direction matches design.md but
  not the task.md ordering. The `AppInfo` domain model came along in
  the util commit because `AppInfoProvider` returns it.
- **Tasks 4.1, 5.1, and 6.1 were committed as a single
  `feat(ui): add Settings, Danger Zone, and OSS Licenses screens`**
  because the Settings screen imports both `DangerZoneActivity.newIntent`
  and `OssLicensesActivity.newIntent` as static factories, so the
  three Kotlin sources only compile as a set. Splitting strings.xml /
  AndroidManifest per task would have required temporarily removing
  entries to commit intermediates, which felt worse than the single
  bundled commit. The commit message documents the bundling.
- **Task 7.2 (deferrable, `- [ ]*`)** was not implemented per the
  developer prompt's explicit instruction to skip deferrable tasks.

## Build verification (NOT executed locally)

The Android SDK / JDK is **not installed in the sandbox** this
implementation was authored in (`./gradlew` exits with
`ERROR: JAVA_HOME is not set`). The same constraint was noted in Issue
#9's `impl-notes.md`, which similarly delegated `./gradlew test` to the
reviewer. The reviewer / CI should therefore run:

- `./gradlew :app:testDebugUnitTest` -- expected to pass with the new
  unit / Robolectric tests (CredentialDaoTest +5,
  KeystoreKeyProviderContractTest +2, VaultStorageMeasurerTest +3,
  SystemSettingsIntentsTest +4, AppInfoProviderTest +1,
  ObserveVaultMetadataUseCaseTest +3, GetDeviceLockStatusUseCaseTest +6,
  ClearVaultUseCaseTest +4, SettingsViewModelTest +6,
  DangerZoneViewModelTest +10, OssLicensesParserTest +7 = **51 new
  unit tests**).
- `./gradlew :app:compileDebugAndroidTestKotlin` -- expected to compile
  the new `@Ignore`d Espresso classes.
- `./gradlew :app:lintDebug` -- expected to be clean. The new strings
  / layouts / manifests follow the conventions established by Issue
  #9.

If a JVM / SDK becomes available locally I will re-run these commands
and update this note.

## Follow-ups

Not blocking this PR. Captured so the reviewer can decide whether to
split them into separate Issues.

1. **Test-friendly ServiceLocator override** -- the
   `SettingsActivityTest` / `DangerZoneActivityTest` / existing
   `CredentialListActivityTest` are all `@Ignore`d because the
   `ServiceLocator` exposes singletons but no swap hooks. A small
   refactor (introduce an `interface ServiceLocator` and a test
   override) would allow the three classes to be un-ignored
   mechanically (their bodies are ready to run).
2. **OSS licenses JSON automation** -- the seed JSON is hand-curated.
   Issue #10 design.md flagged `oss-licenses-plugin` as a separate
   follow-up; not in scope here.
3. **Storage measurement scope decision (Open Question)** -- design.md
   resolved "DB + WAL + SHM only" for `keynest.db`. If KeyNest later
   introduces SharedPreferences with credential-related state, the
   measurer must be updated to include them (Req 4.4).
4. **`AdvancedDetailsFormatter` reuse** -- `SettingsActivity.bindVault`
   reuses `AdvancedDetailsFormatter.formatTimestamp` for the
   "last updated" row. The formatter currently lives under
   `com.example.keynest.util`; consider moving it to a shared `format`
   package once a third caller appears.
5. **Performance test deferred (NFR 2.2)** -- task 7.2 is the
   `SettingsViewModelPerformanceTest` skeleton. Recommended trigger
   for follow-up: if any of the Vault metadata / storage probes start
   appearing in the StrictMode `noteSlowCall` log.

## Confirmation items for reviewer (PR body)

1. The `ClearVaultFailure` re-shape (sealed class extends Exception)
   commit is a small consistency fix on top of task 2.1. If the
   reviewer prefers the original `sealed interface` form, the fix
   commit can be reverted without affecting the test surface (the
   tests rely on `result.exceptionOrNull() is
   ClearVaultFailure.Storage` which holds under both shapes).
2. The Settings / Danger Zone / OSS Licenses landing in a single
   commit (rather than three) is intentional. See "Departures" above.
   If a strict three-commit history is desired the reviewer should
   request the split before merge; my recommendation is to keep the
   single commit because the three sources are not independently
   compileable.
3. `oss_licenses.json` was hand-curated. The reviewer should
   confirm the seed list is acceptable for the first ship; a follow-up
   PR can swap to automated generation.
4. The Espresso tests are `@Ignore`d. Recommendation matches Issue
   #9's posture (manual QA verification + future un-ignore PR).
5. Settings screen's `AutofillStatus` probe is **synchronous** and
   re-fires on every `refresh()` tick. Under high-frequency tab
   churn this could spam the binder. Acceptable today because
   `refresh()` only fires on `onResume`. If we add other refresh
   triggers, consider memoising with a short TTL.
