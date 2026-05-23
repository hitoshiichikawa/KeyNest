# Review Notes

<!-- idd-claude:review round=2 model=claude-opus-4-7 timestamp=2026-05-23T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-103-impl-feat-passkey-passkey-os
- HEAD commit: 9e3630ef6ddb88e44c15f2ddd8cb6d55c1e430eb
- Compared to: develop..HEAD

`git log --oneline develop..HEAD` で 15 commit を確認: T-01 〜 T-15 のすべての
タスクに対応するコミットが順に積まれている (round 1 では HEAD == develop で
全タスク未実施だった状態が完全に解消)。

`git diff --stat develop..HEAD` の 16 ファイル / +1275 / -11 行の差分すべてが
tasks.md `_Boundary:_` で許可された Components
(`PasskeyProviderStatus` / `CredentialProviderStatusChecker` / `SettingsUiState` /
`SettingsViewModel` / `SettingsActivity` / `SystemSettingsIntents`) と DI 配線
(`ServiceLocator`) / layout / strings / tests の範囲に収まっている。

## Verified Requirements

### Requirement 1 (PassKey プロバイダ セクション)

- 1.1 — `app/src/main/res/layout/settings_activity.xml` L136-232 に
  `eyebrow_passkey_provider` + `group_passkey_provider` を Autofill hero
  (`group_autofill_hero`) と Security Eyebrow (`eyebrow_security`) の間に独立
  SettingGroup として加法挿入
- 1.2 — eyebrow に `@string/settings_passkey_provider_eyebrow` を適用 (layout L153)
- 1.3 — `kn_settings_group_bg` / `kn_section_gap` / `kn_settings_row_divider` を
  既存 SettingGroup と同じパターンで踏襲 (layout L163-167)
- 1.4 — `SettingsActivity.bindPasskeyProvider` の `when` で `Enabled` →
  `R.string.settings_passkey_provider_status_enabled` (SettingsActivity.kt L160-161)
- 1.5 — 同じく `Disabled` → `R.string.settings_passkey_provider_status_disabled`
  (SettingsActivity.kt L162-163)
- 1.6 — 同じく `Unsupported` → `R.string.settings_passkey_provider_status_unsupported`
  (SettingsActivity.kt L164-165)
- 1.7 — layout L201 で `text_passkey_provider_status` に
  `android:textColor="@color/kn_text_2"` を適用 (中立 tone)
- 1.8 — `SettingsActivity.onResume` → `viewModel.refresh()` の既存経路に
  `resolvePasskeyProviderStatus` が乗ったことで再判定が走る
  (`refresh_reReadsPasskeyProviderStatus` テストで担保)

### Requirement 2 (OS 設定導線ボタン)

- 2.1 — `bindPasskeyProvider` で `status != Unsupported &&
  Build.VERSION.SDK_INT >= UPSIDE_DOWN_CAKE` で VISIBLE
  (SettingsActivity.kt L168-171)
- 2.2 — 同じく Unsupported (API 33-) では `View.GONE`、instrumentation テスト
  `passkeyProviderButton_isGone_onApi33` (`@SdkSuppress(maxSdkVersion = 33)`) で
  担保
- 2.3 — `SystemSettingsIntents.openPasskeyProviderSettings` が `Intent("android.settings.CREDENTIAL_PROVIDER")`
  を一次経路で発火 (SystemSettingsIntents.kt L87-91)、
  `openPasskeyProviderSettings_dispatchesCredentialProviderIntent_onSuccess` で検証
- 2.4 — `Intent(Settings.ACTION_SETTINGS)` を fallback 経路で発火
  (SystemSettingsIntents.kt L93-94)、
  `openPasskeyProviderSettings_fallsBackToActionSettings_whenPrimaryFails` で検証
- 2.5 — `SettingsActivity.wireRows` の
  `onFailure { showIntentUnavailableSnackbar() }` 配線 (SettingsActivity.kt L106-108)
  + `openPasskeyProviderSettings_returnsFailure_whenBothFail` で
  `Result.failure(ActivityNotFoundException)` を返すことを検証
- 2.6 — layout L221 で `style="@style/Widget.Material3.Button.TonalButton"` を
  ボタンに適用
- 2.7 — `bindPasskeyProvider` の visibility 計算で Enabled / Disabled いずれも
  `status != Unsupported` 経路で VISIBLE (SettingsActivity.kt L168-171)

### Requirement 3 (状態判定)

- 3.1 — `CredentialProviderStatusChecker` interface + `DefaultCredentialProviderStatusChecker`
  実装を `ui/settings/passkey/` 配下に新規追加
- 3.2 — `PasskeyProviderStatus` enum 3 値 (Enabled / Disabled / Unsupported)
  を `domain/model/` 配下に新規追加
- 3.3 — `DefaultCredentialProviderStatusChecker.check()` 冒頭で
  `if (SDK_INT < UPSIDE_DOWN_CAKE) return Unsupported`
  (CredentialProviderStatusChecker.kt L86-89)、
  `check_returnsUnsupported_onApi33` / `_evenWhenCredentialServiceContainsSelf`
  で API を呼ばないことを検証
- 3.4 — `checkOnApi34Plus()` private helper で `Settings.Secure.getString` →
  `:` split → `substringBefore("/") == packageName` で判定
  (CredentialProviderStatusChecker.kt L93-115)、
  `check_returnsEnabled_whenPackageInCredentialService` /
  `_whenPackageIsLastEntry` / `_returnsDisabled_whenPackageNotInCredentialService` で検証
- 3.5 — `checkOnApi34Plus()` 全体を `try { ... } catch (t: Throwable) { return Disabled }`
  で囲み (CredentialProviderStatusChecker.kt L107-124)、`SettingsViewModel.resolvePasskeyProviderStatus`
  にも belt-and-suspenders の try/catch を追加 (SettingsViewModel.kt L120-130)。
  `check_returnsDisabled_whenSettingsSecureThrows` /
  `_whenSettingsSecureThrowsRuntimeException` /
  `uiState_passkeyProvider_fallbackToDisabled_onCheckerException` で検証
- 3.6 — `SettingsViewModel.resolvePasskeyProviderStatus` を
  `withContext(passkeyProbeDispatcher = Dispatchers.IO)` 経由で `viewModelScope`
  上で非同期実行 (SettingsViewModel.kt L65-68)
- 3.7 — `refreshTick.map { resolvePasskeyProviderStatus() }` を combine 5 ソースの
  5 番目として配線 (SettingsViewModel.kt L77)、`refresh_reReadsPasskeyProviderStatus`
  で `refresh()` 経由で再判定が走ることを検証
- 3.8 — `checkOnApi34Plus()` の catch 内は `Log.d` のみで実値・例外メッセージを
  embed していない (CredentialProviderStatusChecker.kt L107-121)、
  `check_doesNotLogProviderListAtInfoLevel` / `_onException` で `ShadowLog.getLogs()`
  の `Log.INFO` 以上に provider package 名 / `credential_service` 値が現れない
  ことを検証

### Requirement 4 (表記ポリシー / 文言)

- 4.1 — 全ての文言で「PassKey」表記固定 (strings.xml diff 確認、KDoc / コメントも `PassKey`)
- 4.2 — Eyebrow / ステータス 3 種 / ボタンラベル / a11y label 全 6 key が
  `R.string.settings_passkey_*` 経由 (layout 内 `android:text` /
  `android:contentDescription` が全てリソース参照)
- 4.3 — `values/strings.xml` L293-298 (en) と `values-ja/strings.xml` L163-168 (ja)
  の両方に同名 6 key を追加
- 4.4 — design.md §4.3 / §9.1-2 で確定したラベル文言 (「PassKey 設定を開く」 /
  「Open PassKey settings」 / `PASSKEY PROVIDER` 大文字) を string resource に
  格納

### Requirement 5 (既存への非干渉)

- 5.1 — 既存 Autofill hero / Security / Vault / About / Danger zone の `android:id` /
  順序 / 子要素は layout diff で一切変更されていない (加法的 95 行挿入のみ)
- 5.2 — `SettingsUiState` の既存 5 フィールド (`autofillStatus` / `lockStatus` /
  `metadata` / `storageBytes` / `appInfo`) のシグネチャ不変、6 番目 `passkeyProviderStatus`
  のみ加法的追加 (SettingsUiState.kt diff 確認)
- 5.3 — `combine` を 4 → 5 ソースに拡張、`refreshTick.map { ... }` パターン
  踏襲で既存 3 source の再判定タイミングと整合
- 5.4 — 新規 View ID (`btn_open_passkey_settings` / `text_passkey_provider_status` /
  `eyebrow_passkey_provider` / `group_passkey_provider`) はいずれも既存 ID と衝突
  しない命名 (layout grep 確認)
- 5.5 — `SettingsViewModelTest` 既存 7 件 + 新規 5 件いずれも pass (impl-notes
  記載)、API 33 fixture でも Unsupported 経路で UI 破綻なし

### Requirement 6 (テスト)

- 6.1 — `uiState_passkeyProvider_isEnabled_whenApi34AndKeyNestActive`
  (SettingsViewModelTest.kt L207-217)
- 6.2 — `uiState_passkeyProvider_isDisabled_whenApi34AndKeyNestInactive`
  (SettingsViewModelTest.kt L220-230)
- 6.3 — `uiState_passkeyProvider_isUnsupported_andCheckerCalledOnce_onApi33`
  (SettingsViewModelTest.kt L233-247) + `check_returnsUnsupported_onApi33`
  (CredentialProviderStatusCheckerTest.kt)
- 6.4 — `uiState_passkeyProvider_fallbackToDisabled_onCheckerException`
  (SettingsViewModelTest.kt L250-272)
- 6.5 — `openPasskeyProviderSettings_dispatchesCredentialProviderIntent_onSuccess`
  (SystemSettingsIntentsTest.kt L104-117) + instrumentation
  `passkeyProviderButton_tap_dispatchesCredentialProviderIntent`
- 6.6 — `openPasskeyProviderSettings_fallsBackToActionSettings_whenPrimaryFails`
  (SystemSettingsIntentsTest.kt L120-152)
- 6.7 — `passkeyProviderButton_isGone_onApi33` (SettingsActivityTest.kt L133-145,
  `@SdkSuppress(maxSdkVersion = 33)`)
- 6.8 — impl-notes 記載: `./gradlew :app:testDebugUnitTest` で 808 件中
  1 失敗 (pre-existing `AppInfoProviderTest`、develop でも fail で本 Issue 影響外)。
  本 Issue 関連の `CredentialProviderStatusCheckerTest` 11 件 /
  `SettingsViewModelTest` Issue #103 追加 5 件 + 既存 7 件 /
  `SystemSettingsIntentsTest` Issue #103 追加 3 件 + 既存 4 件は全 pass

### NFR

- NFR 1.1 / 1.2 — `resolvePasskeyProviderStatus` は `withContext(Dispatchers.IO)`
  で main thread blocking を回避
- NFR 2.1 — `check_doesNotLogProviderListAtInfoLevel` / `_onException` で実値リーク
  なしを担保
- NFR 2.2 / 2.3 — AAGUID / Keystore alias / private key を UI / log に登場
  させていない (diff 内に該当箇所なし)
- NFR 3.1 / 3.2 — strings / KDoc / コメント / log message いずれも `PassKey` 固定
- NFR 4.1〜4.3 — `@RequiresApi(UPSIDE_DOWN_CAKE)` を private helper にのみ付与、
  public `check()` は API 26+ から安全に呼び出し可能で SDK_INT 分岐で防御
- NFR 5.1〜5.3 — `SettingsActivity.newIntent` / 既存 View ID / `minSdk` /
  `targetSdk` / `compileSdk` / `applicationId` / `namespace` 不変

## Findings

なし

## Summary

Round 1 で指摘した 6 findings (AC 1.x / 2.x / 3.x / 4.x すべての未カバー +
全テスト missing + impl-notes 不在) はすべて解消された。T-01 〜 T-15 の 15
タスクに対応する 15 commit (T-06 + T-08 統合で実質 14 commit) が積まれ、
requirements.md の全 numeric ID (Req 1〜6 / NFR 1〜5) について実装またはテストが
diff から確認できた。`tasks.md` の `_Boundary:_` 違反も検出されなかった。
impl-notes.md にテスト実行結果 (808 件 / 関連分は全 pass、pre-existing 1 件
失敗は develop でも再現する範囲外) と PoC 結論 (A: Settings.Secure 経路採用) が
記録されている。

RESULT: approve
