# Implementation Plan — Issue #103

> 関連: `requirements.md` / `design.md`（本ディレクトリ）。Developer は着手前に必ず両方を読む。
>
> 各タスクは独立コミット可能な粒度。`_Requirements:_` は requirements.md の numeric ID を、`_Boundary:_` は design.md の Components 名を参照する。
>
> **重要**: T-02 (PoC) は **commit 不要だが Developer の作業ログに記録必須** のタスク。PoC 結果次第で T-03 の実装方針が分岐するため、T-03 着手前に必ず完了させること。

- [ ] **T-01. ドメインモデル: `PasskeyProviderStatus` enum 追加** (種別: 実装)
  - `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/model/PasskeyProviderStatus.kt` を新規作成
  - 3 値 enum: `Enabled`, `Disabled`, `Unsupported`
  - KDoc に「Issue #103 Req 3.2 / 3.3 / 3.4 / NFR 3.1 (PassKey 表記固定)」を明記
  - 受入条件 (DoD):
    - 単体で `./gradlew :app:compileDebugKotlin` が pass
    - import 元なし状態でも warning なし
  - 影響ファイル: `domain/model/PasskeyProviderStatus.kt` (新規 1 ファイル)
  - _Requirements: 3.2_
  - _Boundary: PasskeyProviderStatus_

- [ ] **T-02. PoC: `Settings.Secure.getString("credential_service")` の実測** (種別: 調査 / PoC)
  - **commit 不要**。作業ログ (`docs/specs/103-feat-passkey-passkey-os/poc-notes.md` を新規作成) にスクリーンショット / 実測値 / 結論を残す
  - 手順:
    1. Android 14 emulator (API 34) を起動
    2. KeyNest をインストールし、設定 → パスワードと PassKey → KeyNest を有効化
    3. `adb shell settings get secure credential_service` の出力を記録
    4. KeyNest 無効化状態で同コマンドを再実行、値の差分を記録
    5. 取得した値の format (`pkg/component:pkg/component` の `:` 区切り？) を確認
  - 結論パターン:
    - **(A) 想定通り**: 自パッケージ名が `:` 区切りで含まれる → design.md §4.2 のアルゴリズム通り進める (T-03)
    - **(B) format が異なる / 別 key**: design.md §9.1-1 二次案 (Disabled 固定 degraded mode) に切り替え。`docs/specs/103-feat-passkey-passkey-os/poc-notes.md` に切替判断と新アルゴリズムを記録。`needs-decisions` で人間にエスカレーション
  - 受入条件:
    - poc-notes.md に上記 5 項目が記録されている
    - 結論パターンが A / B のいずれかで確定している
  - 影響ファイル: `docs/specs/103-feat-passkey-passkey-os/poc-notes.md` (新規)
  - _Requirements: 3.4_
  - _Depends: T-01_

- [ ] **T-03. `CredentialProviderStatusChecker` interface + 実装追加** (種別: 実装) (P)
  - 3.1. `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/settings/passkey/CredentialProviderStatusChecker.kt` を新規作成
    - `interface CredentialProviderStatusChecker { fun check(): PasskeyProviderStatus }`
    - 同ファイル内に `internal class DefaultCredentialProviderStatusChecker(context: Context)` を実装
  - 3.2. `check()` の実装:
    - 冒頭で `if (Build.VERSION.SDK_INT < 34) return Unsupported` (Req 3.3)
    - それ以降は `@RequiresApi(34)` private helper `checkOnApi34Plus()` を呼ぶ
    - `checkOnApi34Plus()` は `Settings.Secure.getString(context.contentResolver, "credential_service")` の結果を `:` 区切りで split、各 entry の `/` より前 (= パッケージ名部分) が `context.packageName` と一致するか判定
    - 全体を `try { ... } catch (t: Throwable) { return Disabled }` で囲む (Req 3.5)
    - catch 内では `Log.d(TAG, "check failed", t)` までに限定 (Req 3.8 / NFR 2.1)
  - 3.3. T-02 結論 (B) の場合: `checkOnApi34Plus()` は常に `Disabled` を返す degraded mode に書き換え。class KDoc に「PoC 結果により degraded mode で固定」と明記
  - 受入条件:
    - interface + 実装が compile pass
    - lint: `@RequiresApi(34)` の付与漏れなし (`./gradlew :app:lintDebug` で `NewApi` warning ゼロ)
    - `Log.i` / `Log.w` / `Log.e` を `credential_service` 値や provider 一覧で呼ぶコード経路がない (grep 確認)
  - 影響ファイル: `ui/settings/passkey/CredentialProviderStatusChecker.kt` (新規)
  - _Requirements: 3.1, 3.3, 3.4, 3.5, 3.8_
  - _Boundary: CredentialProviderStatusChecker_
  - _Depends: T-01, T-02_

- [ ] **T-04. `CredentialProviderStatusCheckerTest` (Robolectric Unit) 追加** (種別: テスト) (P)
  - `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/settings/passkey/CredentialProviderStatusCheckerTest.kt` を新規作成
  - design.md §10.1.1 の 6 テストケースを実装:
    - `check_returnsUnsupported_onApi33` (`@Config(sdk = [33])`)
    - `check_returnsEnabled_whenPackageInCredentialService` (`@Config(sdk = [34])`)
    - `check_returnsDisabled_whenPackageNotInCredentialService` (`@Config(sdk = [34])`)
    - `check_returnsDisabled_whenCredentialServiceIsNull` (`@Config(sdk = [34])`)
    - `check_returnsDisabled_whenSettingsSecureThrows` (`@Config(sdk = [34])`)
    - `check_doesNotLogProviderListAtInfoLevel` (`@Config(sdk = [34])` + `ShadowLog.getLogs()`)
  - T-02 結論 (B) の場合: Enabled ケースを `@Ignore("PoC 結果により degraded mode 固定")` に切替、Disabled ケースのみ生かす
  - 受入条件:
    - `./gradlew :app:testDebugUnitTest --tests "*CredentialProviderStatusCheckerTest"` が pass
    - SDK 33 / 34 両方のテストが実行される
  - 影響ファイル: `app/src/test/java/.../ui/settings/passkey/CredentialProviderStatusCheckerTest.kt` (新規)
  - _Requirements: 6.3, 6.4_
  - _Boundary: CredentialProviderStatusChecker_
  - _Depends: T-03_

- [ ] **T-05. `SettingsUiState` への `passkeyProviderStatus` フィールド追加** (種別: 実装) (P)
  - `app/src/main/java/.../ui/settings/SettingsUiState.kt` を編集
  - 6 番目フィールド `val passkeyProviderStatus: PasskeyProviderStatus` を加法的に追加 (Req 5.2)
  - `EMPTY` placeholder のデフォルトを `PasskeyProviderStatus.Unsupported` に設定 (design.md §3.2 根拠)
  - KDoc に Issue #103 Req 3.2 への参照を追記
  - 受入条件:
    - compile pass
    - `grep -r "SettingsUiState(" app/src/test app/src/main` で 6 番目フィールドが必要なる callsite がないことを確認 (現状の grep 結果では `vm.uiState.value` 経由のみ)。callsite が見つかった場合は同一 commit で修正
  - 影響ファイル: `ui/settings/SettingsUiState.kt`
  - _Requirements: 5.2_
  - _Boundary: SettingsUiState, PasskeyProviderStatus_
  - _Depends: T-01_

- [ ] **T-06. `SettingsViewModel` の `combine` を 5 ソースに拡張** (種別: 実装)
  - `app/src/main/java/.../ui/settings/SettingsViewModel.kt` を編集
  - 6.1. constructor / `Factory` に `private val credentialProviderStatusChecker: CredentialProviderStatusChecker` パラメータ追加
  - 6.2. `combine(...)` を 4 → 5 引数に拡張、5 番目 source として `refreshTick.map { withContext(Dispatchers.IO) { credentialProviderStatusChecker.check() } }` を追加 (design.md §4.2)
  - 6.3. `combine` の lambda を 5 引数に拡張、`SettingsUiState(..., passkeyProviderStatus = passkey)` を構築
  - 6.4. `refresh()` の挙動は既存 `refreshTick.value++` を維持 (Req 3.7 / 5.3)
  - 受入条件:
    - compile pass
    - `SettingsActivity` の `by viewModels { Factory(...) }` に新パラメータを ServiceLocator から渡す (T-08 と同 commit でも、本 task と同 commit でも可)
  - 影響ファイル: `ui/settings/SettingsViewModel.kt`
  - _Requirements: 3.1, 3.6, 3.7, 5.2, 5.3_
  - _Boundary: SettingsViewModel, CredentialProviderStatusChecker, SettingsUiState_
  - _Depends: T-03, T-05_

- [ ] **T-07. `SettingsViewModelTest` 拡張** (種別: テスト) (P)
  - `app/src/test/java/.../ui/settings/SettingsViewModelTest.kt` を編集
  - 7.1. helper `newViewModelWithCollector` に `passkeyChecker: CredentialProviderStatusChecker` パラメータ追加 (default: `FakeCredentialProviderStatusChecker(Unsupported)`)
  - 7.2. テストファイル末尾に `private class FakeCredentialProviderStatusChecker(var value: PasskeyProviderStatus) : CredentialProviderProviderStatusChecker { var callCount = 0; override fun check(): PasskeyProviderStatus = value.also { callCount++ } }` を追加
  - 7.3. design.md §10.1.2 の 5 ケースを追加:
    - `uiState_passkeyProvider_isEnabled_whenApi34AndKeyNestActive`
    - `uiState_passkeyProvider_isDisabled_whenApi34AndKeyNestInactive`
    - `uiState_passkeyProvider_isUnsupported_andCheckerCalledOnce_onApi33` (注: ViewModel は SDK 判定をしないため、Checker は `Unsupported` を返す fake で `callCount == 1` を assert)
    - `uiState_passkeyProvider_fallbackToDisabled_onCheckerException` (fake が throw する variant)
    - `refresh_reReadsPasskeyProviderStatus` (refresh 前後で値変更)
  - 7.4. 既存 7 テストが引き続き pass することを確認
  - 受入条件:
    - `./gradlew :app:testDebugUnitTest --tests "*SettingsViewModelTest"` が全 pass (新 5 + 既存 7 = 12 件)
  - 影響ファイル: `app/src/test/java/.../ui/settings/SettingsViewModelTest.kt`
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 3.7_
  - _Boundary: SettingsViewModel, CredentialProviderStatusChecker_
  - _Depends: T-06_

- [ ] **T-08. `ServiceLocator` に `credentialProviderStatusChecker` 追加 + Activity 配線** (種別: 実装) (P)
  - 8.1. `app/src/main/java/.../di/ServiceLocator.kt` を編集
    - `val credentialProviderStatusChecker: CredentialProviderStatusChecker by lazy { DefaultCredentialProviderStatusChecker(requireAppContext()) }` を追加
    - `@RequiresApi` は付与しない (内部で SDK_INT 分岐するため API 26+ で安全に解決可能)
  - 8.2. `SettingsActivity.kt` の `by viewModels { Factory(..., credentialProviderStatusChecker = ServiceLocator.credentialProviderStatusChecker) }` に追記
  - 受入条件:
    - compile pass
    - `./gradlew :app:assembleDebug` が pass
  - 影響ファイル: `di/ServiceLocator.kt`, `ui/settings/SettingsActivity.kt`
  - _Requirements: 3.1_
  - _Boundary: SettingsViewModel, CredentialProviderStatusChecker_
  - _Depends: T-06_

- [ ] **T-09. String resources 追加 (en + ja)** (種別: 実装) (P)
  - 9.1. `app/src/main/res/values/strings.xml` に design.md §4.3 の 6 key を追加 (en):
    - `settings_passkey_provider_eyebrow`
    - `settings_passkey_provider_status_enabled`
    - `settings_passkey_provider_status_disabled`
    - `settings_passkey_provider_status_unsupported`
    - `settings_passkey_provider_open_settings_action`
    - `settings_passkey_provider_open_settings_a11y_label`
  - 9.2. `app/src/main/res/values-ja/strings.xml` に同 6 key の ja 翻訳を追加
  - 9.3. 「PassKey」表記が大小ともに正しいことを目視 + grep 確認 (`grep -E "(passkey|Passkey|パスキー|passKey)" app/src/main/res/values*/strings.xml` で**新規追加分**にヒットしないこと、`PassKey` のみが許容)
  - 受入条件:
    - en / ja 両方に同名 key が存在
    - `./gradlew :app:processDebugResources` が pass
    - lint `MissingTranslation` が出ない
  - 影響ファイル: `res/values/strings.xml`, `res/values-ja/strings.xml`
  - _Requirements: 4.1, 4.2, 4.3, 4.4_
  - _Depends: T-01_

- [ ] **T-10. `settings_activity.xml` への SettingGroup 追加** (種別: 実装)
  - `app/src/main/res/layout/settings_activity.xml` を編集
  - 10.1. Autofill hero (`group_autofill_hero` L82-136) と Security Eyebrow (`eyebrow_security` L141-150) の間に、Security と同パターンで以下を加法的に挿入:
    - `<TextView android:id="@+id/eyebrow_passkey_provider" ...>` (Eyebrow, `settings_passkey_provider_eyebrow`)
    - `<LinearLayout android:id="@+id/group_passkey_provider" ...>` (`kn_settings_group_bg` 流用)
      - 内部に horizontal row 1 つ:
        - 左: 簡易 icon tile (`@drawable/ic_passkey_24` 不在なら `@drawable/ic_key_24` 流用、Vault と区別したい場合は新規 icon は本 Issue 範囲外として既存流用)
        - 中央: `TextView android:id="@+id/text_passkey_provider_status"` (`@color/kn_text_2`, Req 1.7)
      - その下に MaterialButton row:
        - `<com.google.android.material.button.MaterialButton android:id="@+id/btn_open_passkey_settings" style="@style/Widget.Material3.Button.TonalButton" android:text="@string/settings_passkey_provider_open_settings_action" android:contentDescription="@string/settings_passkey_provider_open_settings_a11y_label" />` (Req 2.6)
  - 10.2. 既存 View ID と衝突しない命名を確認: `btn_open_passkey_settings` / `text_passkey_provider_status` / `eyebrow_passkey_provider` / `group_passkey_provider` (Req 5.4)
  - 10.3. 既存 Autofill hero / Security / Vault / About / Danger zone の `android:id` / 子要素を一切変更しない (Req 5.1)
  - 受入条件:
    - `./gradlew :app:processDebugResources` pass
    - layout preview で新セクションが Autofill hero と Security の間に表示される (Android Studio で目視)
    - 既存 SettingsActivityBinding の View ID が全部生成される (binding compile error なし)
  - 影響ファイル: `res/layout/settings_activity.xml`
  - _Requirements: 1.1, 1.2, 1.3, 1.7, 2.6, 5.1, 5.4_
  - _Depends: T-09_

- [ ] **T-11. `SettingsActivity.bindPasskeyProvider` 実装 + wireRows 拡張** (種別: 実装)
  - `app/src/main/java/.../ui/settings/SettingsActivity.kt` を編集
  - 11.1. `wireRows()` に `binding.btnOpenPasskeyProviderSettings.setOnClickListener { SystemSettingsIntents.openPasskeyProviderSettings(this).onFailure { showIntentUnavailableSnackbar() } }` を追加 (Req 2.3 / 2.5)
  - 11.2. `bind(state)` に `bindPasskeyProvider(state.passkeyProviderStatus)` を追加 (Autofill bind の直後を推奨、layout 順と一致)
  - 11.3. `private fun bindPasskeyProvider(status: PasskeyProviderStatus)` を新設:
    - `when (status)` で 3 ケースの status text resource を切替 (Req 1.4 / 1.5 / 1.6)
    - `binding.btnOpenPasskeyProviderSettings.visibility = if (status == Unsupported) View.GONE else View.VISIBLE` (Req 2.1 / 2.2 / 2.7)
  - 受入条件:
    - compile pass
    - 既存 `bindAutofill` / `bindLockStatus` 等の signature 不変
    - `./gradlew :app:lintDebug` が new warning ゼロ
  - 影響ファイル: `ui/settings/SettingsActivity.kt`
  - _Requirements: 1.4, 1.5, 1.6, 1.8, 2.1, 2.2, 2.3, 2.5, 2.7_
  - _Boundary: SettingsActivity_
  - _Depends: T-08, T-10_

- [ ] **T-12. `SystemSettingsIntents.openPasskeyProviderSettings` 追加** (種別: 実装) (P)
  - `app/src/main/java/.../util/SystemSettingsIntents.kt` を編集
  - 12.1. `private const val ACTION_CREDENTIAL_PROVIDER = "android.settings.CREDENTIAL_PROVIDER"` を companion / file-private 定数として追加 (API 34+ 定数の文字列リテラル経由参照、NewApi lint 回避)
  - 12.2. `fun openPasskeyProviderSettings(activity: Activity): Result<Unit>` を追加:
    - primary: `Intent(ACTION_CREDENTIAL_PROVIDER)` を `startSafely` で発火、成功なら `Result.success(Unit)`
    - fallback: 失敗なら `Intent(Settings.ACTION_SETTINGS)` を `startSafely` で発火し、その結果を返す
  - 12.3. 既存 `startSafely` private helper を再利用 (新規 helper を作らない)
  - 受入条件:
    - compile pass
    - 既存 `openAutofillServiceChooser` / `openSecuritySettings` の signature / 挙動が不変
  - 影響ファイル: `util/SystemSettingsIntents.kt`
  - _Requirements: 2.3, 2.4_
  - _Boundary: SystemSettingsIntents_
  - _Depends: -_

- [ ] **T-13. `SystemSettingsIntentsTest` 拡張** (種別: テスト) (P)
  - `app/src/test/java/.../util/SystemSettingsIntentsTest.kt` を編集
  - design.md §10.1.3 の 3 ケースを追加:
    - `openPasskeyProviderSettings_dispatchesCredentialProviderIntent_onSuccess` (action 名 == "android.settings.CREDENTIAL_PROVIDER" を assert)
    - `openPasskeyProviderSettings_fallsBackToActionSettings_whenPrimaryFails` (`shadowOf(app).checkActivities(true)` で primary を resolution 不可にし fallback が dispatched されるか)
    - `openPasskeyProviderSettings_returnsFailure_whenBothFail` (両方失敗時の Result.failure)
  - 既存 4 テストが pass することを確認
  - 受入条件:
    - `./gradlew :app:testDebugUnitTest --tests "*SystemSettingsIntentsTest"` が全 pass (新 3 + 既存 4 = 7 件)
  - 影響ファイル: `app/src/test/java/.../util/SystemSettingsIntentsTest.kt`
  - _Requirements: 6.5, 6.6, 2.5_
  - _Boundary: SystemSettingsIntents_
  - _Depends: T-12_

- [ ] **T-14. `SettingsActivityTest` (instrumentation) 拡張 (@Ignore 維持)** (種別: テスト) (P)
  - `app/src/androidTest/java/.../ui/settings/SettingsActivityTest.kt` を編集
  - design.md §10.2 の 3 ケースを追加 (クラス全体は既存 `@Ignore` を維持):
    - `passkeyProviderButton_tap_dispatchesCredentialProviderIntent` (`onView(withId(R.id.btn_open_passkey_settings)).perform(click()); intended(hasAction("android.settings.CREDENTIAL_PROVIDER"))`)
    - `passkeyProviderButton_isGone_onApi33` (`@SdkSuppress(maxSdkVersion = 33)` 付与、View.GONE assert)
    - `passkeyProviderStatus_isDisplayed_withCorrectText_onResume` (`text_passkey_provider_status` の表示確認)
  - 受入条件:
    - compile pass
    - 既存 `@Ignore` がクラス単位で維持され、本 task 追加分が誤って enable されていない
  - 影響ファイル: `app/src/androidTest/java/.../ui/settings/SettingsActivityTest.kt`
  - _Requirements: 6.5, 6.7_
  - _Boundary: SettingsActivity_
  - _Depends: T-11_

- [ ] **T-15. 既存テスト回帰確認 + Lint クリーン** (種別: テスト / 検証)
  - 15.1. `./gradlew :app:testDebugUnitTest` を実行し全件 pass を確認 (Req 6.8 / 5.1)
  - 15.2. `./gradlew :app:lintDebug` を実行し、本 Issue 変更ファイルで新規 lint warning がゼロであることを確認 (特に `NewApi` / `MissingTranslation`)
  - 15.3. `./gradlew :app:assembleDebug` で APK ビルドが成功することを確認
  - 15.4. (手動) API 34 emulator で SettingsActivity を起動し、(a) PassKey provider セクションが表示される、(b) ボタンタップで Credential Manager 設定が開く、(c) `onResume` 後に状態が更新される、ことを目視確認。スクリーンショットを PR 説明に添付
  - 15.5. (手動 / 任意) API 33 emulator で起動し、(a) 「Android 14 以降で利用可能です」表示、(b) ボタン非表示、(c) 既存セクション操作に支障なし、を確認
  - 受入条件:
    - `testDebugUnitTest` 全件 pass (Req 6.8)
    - `lintDebug` で本 Issue 変更分 0 warning
    - `assembleDebug` pass
    - 手動検証スクリーンショットが PR に添付
  - 影響ファイル: なし (検証のみ)
  - _Requirements: 5.1, 5.5, 6.8_
  - _Depends: T-04, T-07, T-11, T-13, T-14_

---

## Task Dependency Graph

```
T-01 (enum) ─┬─→ T-03 (Checker 実装) ─→ T-04 (Checker test)
             │                       └─→ T-06 (ViewModel 拡張) ─→ T-07 (ViewModel test)
             │                                                  └─→ T-08 (DI + Activity 配線)
             ├─→ T-05 (UiState フィールド追加) ─→ T-06
             └─→ T-09 (strings) ─→ T-10 (layout) ─→ T-11 (Activity bind) ─→ T-14 (instr test)
T-02 (PoC) ─→ T-03 ↑
T-12 (Intent helper) ─→ T-13 (Intent test)
T-08 → T-11 ↑
T-04 / T-07 / T-11 / T-13 / T-14 ─→ T-15 (回帰確認)
```

並列実行可能なグループ (`(P)` マーク):

- **Group A (independent kickoff after T-01)**: T-05, T-09, T-12 はそれぞれ独立 boundary なので並列可
- **Group B (after T-03)**: T-04 (Checker テスト) と T-06 (ViewModel 拡張) の前半は並列可（ただし T-06 は T-05 にも依存）
- **Group C (test layer)**: T-07, T-13, T-14 は別 boundary の test なので並列可

## Implementation Notes for Developer

1. **PoC 結果による分岐**: T-02 の結論 (A / B) によって T-03 のアルゴリズムが変わる。**T-02 完了前に T-03 を着手しない**こと
2. **「PassKey」表記**: T-09 / T-10 / T-11 で文字列 / KDoc / log message を書くとき、必ず `PassKey` (P 大文字 / K 大文字) を使う。grep で `passkey` (全小文字) / `Passkey` (P のみ) / `passKey` (camel) / `パスキー` を新規追加分から検出したら修正
3. **`@RequiresApi(34)` の付与位置**: `CredentialProviderStatusChecker.check()` 自体には付けない。private helper `checkOnApi34Plus()` にのみ付ける (design.md §7.1)
4. **`Settings.ACTION_CREDENTIAL_PROVIDER` 定数**: API 34+ 定数なので、文字列リテラル `"android.settings.CREDENTIAL_PROVIDER"` を `private const val ACTION_CREDENTIAL_PROVIDER` として helper 内に置く (NewApi lint 回避、design.md §4.4)
5. **既存 `SettingsViewModelTest.newViewModelWithCollector` helper の signature 変更**: 既存 7 テストが pass し続けるよう、`passkeyChecker` パラメータには default 値を与える
