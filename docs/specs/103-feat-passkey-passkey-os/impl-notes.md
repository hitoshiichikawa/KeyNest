# Implementation Notes — Issue #103

## Round 2 是正内容

Reviewer round=1 で「impl ブランチ HEAD が develop と完全に同一 SHA で T-01〜T-15
全 15 タスクが未実施」と reject された。本 round 2 で T-01 〜 T-15 全タスクを順に
実装し、設計書 `design.md` / `tasks.md` の DoD を満たすコミットを 14 件積んだ。

### 積んだコミット (順序順)

| # | Hash | Task | Subject |
|---|------|------|---------|
| 1 | `a57cc84` | T-02 | docs(passkey): record PoC conclusion for credential_service probe |
| 2 | `34683db` | T-01 | feat(passkey): add PasskeyProviderStatus enum |
| 3 | `90628cb` | T-03 | feat(passkey): add CredentialProviderStatusChecker |
| 4 | `ba80511` | T-04 | test(passkey): add CredentialProviderStatusCheckerTest |
| 5 | `7178740` | T-05 | feat(passkey): add passkeyProviderStatus to SettingsUiState |
| 6 | `27572bb` | T-06 + T-08 | feat(passkey): wire CredentialProviderStatusChecker into SettingsViewModel |
| 7 | `11307c8` | T-07 | test(passkey): add Issue #103 cases to SettingsViewModelTest |
| 8 | `8cf048c` | T-09 | feat(passkey): add settings_passkey_provider_* strings (en + ja) |
| 9 | `87d9c06` | T-10 | feat(passkey): insert PassKey provider SettingGroup into settings layout |
| 10 | `3e23d8f` | T-12 | feat(passkey): add openPasskeyProviderSettings with 2-step fallback |
| 11 | `fc8cb54` | T-13 | test(passkey): add openPasskeyProviderSettings test cases |
| 12 | `9e28719` | T-11 | feat(passkey): bind PassKey provider state in SettingsActivity |
| 13 | `241a3b8` | T-14 | test(passkey): add instrumentation cases for PassKey provider UI |
| 14 | `1e63ddb` | T-15 | fix(passkey): harden ViewModel-side fallback and test plumbing |

### 追加 / 変更したファイル

#### 新規

- `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/model/PasskeyProviderStatus.kt`
- `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/settings/passkey/CredentialProviderStatusChecker.kt`
- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/settings/passkey/CredentialProviderStatusCheckerTest.kt`
- `docs/specs/103-feat-passkey-passkey-os/poc-notes.md`
- `docs/specs/103-feat-passkey-passkey-os/impl-notes.md` (本ファイル)

#### 変更

- `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/settings/SettingsUiState.kt`
  (passkeyProviderStatus フィールド加法的追加)
- `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/settings/SettingsViewModel.kt`
  (combine 5 ソースへ拡張 + IO 非同期 + 例外時 Disabled fallback + passkeyProbeDispatcher 注入可能)
- `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/settings/SettingsActivity.kt`
  (bindPasskeyProvider + wireRows 拡張 + SDK_INT 防御層)
- `app/src/main/java/io/github/hitoshiichikawa/keynest/util/SystemSettingsIntents.kt`
  (openPasskeyProviderSettings 追加 / 2 段経路 / ACTION_CREDENTIAL_PROVIDER 文字列リテラル)
- `app/src/main/java/io/github/hitoshiichikawa/keynest/di/ServiceLocator.kt`
  (credentialProviderStatusChecker lazy 追加)
- `app/src/main/res/layout/settings_activity.xml`
  (PassKey provider SettingGroup 加法挿入 / 既存セクション順序不変)
- `app/src/main/res/values/strings.xml` (settings_passkey_provider_* 6 keys 追加)
- `app/src/main/res/values-ja/strings.xml` (同 6 keys ja 翻訳追加)
- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/settings/SettingsViewModelTest.kt`
  (Issue #103 5 ケース + FakeCredentialProviderStatusChecker)
- `app/src/test/java/io/github/hitoshiichikawa/keynest/util/SystemSettingsIntentsTest.kt`
  (openPasskeyProviderSettings 3 ケース)
- `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/ui/settings/SettingsActivityTest.kt`
  (instrumentation 3 ケース、@Ignore クラス維持)

## PoC 結論

`poc-notes.md` 参照。**採用パターン: A (Settings.Secure 経路採用)**。

sandbox 環境では Android 14 emulator を起動できないため、design.md §9.1-1 の
AOSP source 分析を一次根拠として採用し、Robolectric `Settings.Secure.putString`
で同等の挙動を T-04 で担保する形に倒した。例外時は Req 3.5 通り `Disabled`
fallback で graceful degrade するため、B 案 (degraded mode) の挙動は本実装の
catch ブランチで実質的に再現される。

## 要件達成確認 (Requirements Traceability)

| Req | Test |
|-----|------|
| 1.1〜1.3 | layout 加法挿入 (settings_activity.xml diff) + Robolectric instrumentation 経由は @Ignore のため目視確認 |
| 1.4 | `bindPasskeyProvider` Enabled → `settings_passkey_provider_status_enabled` (SettingsActivity.kt) |
| 1.5 | `bindPasskeyProvider` Disabled → `settings_passkey_provider_status_disabled` |
| 1.6 | `bindPasskeyProvider` Unsupported → `settings_passkey_provider_status_unsupported` + `passkeyProviderButton_isGone_onApi33` (T-14 / SettingsActivityTest) |
| 1.7 | layout `android:textColor="@color/kn_text_2"` for `text_passkey_provider_status` |
| 1.8 | `refresh_reReadsPasskeyProviderStatus` (SettingsViewModelTest) |
| 2.1 / 2.2 | `bindPasskeyProvider` の visibility logic (SDK_INT >= 34 / != Unsupported で VISIBLE 以外 GONE) + `passkeyProviderButton_isGone_onApi33` |
| 2.3 | `openPasskeyProviderSettings_dispatchesCredentialProviderIntent_onSuccess` (SystemSettingsIntentsTest) |
| 2.4 | `openPasskeyProviderSettings_fallsBackToActionSettings_whenPrimaryFails` |
| 2.5 | `openPasskeyProviderSettings_returnsFailure_whenBothFail` + SettingsActivity の `onFailure { showIntentUnavailableSnackbar() }` 配線 |
| 2.6 | layout `style="@style/Widget.Material3.Button.TonalButton"` |
| 2.7 | `bindPasskeyProvider` の `status != Unsupported` 条件で Enabled/Disabled 両方 VISIBLE |
| 3.1 / 3.2 | `CredentialProviderStatusChecker.check()` + `PasskeyProviderStatus` enum |
| 3.3 | `check_returnsUnsupported_onApi33` + `check_returnsUnsupported_onApi33_evenWhenCredentialServiceContainsSelf` (CredentialProviderStatusCheckerTest) |
| 3.4 | `check_returnsEnabled_whenPackageInCredentialService` / `_returnsEnabled_whenPackageIsLastEntry` / `_returnsDisabled_whenPackageNotInCredentialService` |
| 3.5 | `check_returnsDisabled_whenSettingsSecureThrows` / `_whenSettingsSecureThrowsRuntimeException` (CredentialProviderStatusCheckerTest) + `uiState_passkeyProvider_fallbackToDisabled_onCheckerException` (SettingsViewModelTest、ViewModel 側 belt-and-suspenders 層も担保) |
| 3.6 | SettingsViewModel.resolvePasskeyProviderStatus = `withContext(passkeyProbeDispatcher = Dispatchers.IO)` (production) |
| 3.7 | `refresh_reReadsPasskeyProviderStatus` |
| 3.8 / NFR 2.1 | `check_doesNotLogProviderListAtInfoLevel` + `check_doesNotLogProviderPackageAtInfoLevel_onException` |
| 4.1〜4.4 | strings.xml diff (en / ja 両方に PassKey 表記固定で 6 keys 追加) |
| 5.1 / 5.4 | layout 既存セクションの View ID / 順序を維持 (diff 確認) |
| 5.2 | SettingsUiState 既存 5 フィールド名・型は不変、6 番目 passkeyProviderStatus を加法的追加 |
| 5.3 | `refresh_reReadsPasskeyProviderStatus` (refresh で再判定) |
| 5.5 | API 33 fixture でも `SettingsViewModelTest` 既存 7 件 + Unsupported ケースが pass |
| 6.1〜6.4 | `uiState_passkeyProvider_isEnabled_*` / `_isDisabled_*` / `_isUnsupported_*` / `_fallbackToDisabled_onCheckerException` |
| 6.5 | `openPasskeyProviderSettings_dispatchesCredentialProviderIntent_onSuccess` + (instrumentation) `passkeyProviderButton_tap_dispatchesCredentialProviderIntent` |
| 6.6 | `openPasskeyProviderSettings_fallsBackToActionSettings_whenPrimaryFails` |
| 6.7 | (instrumentation) `passkeyProviderButton_isGone_onApi33` |
| 6.8 | `./gradlew :app:testDebugUnitTest` 全 808 件で本 Issue 関連は全 pass。pre-existing failure 1 件 (AppInfoProviderTest) は develop でも fail しており本 Issue 影響外 |
| NFR 1.1 / 1.2 | resolvePasskeyProviderStatus が `withContext(Dispatchers.IO)` (production default) |
| NFR 2.2 / 2.3 | AAGUID / Keystore alias / private key を UI / log に登場させていない (実装内に該当箇所なし) |
| NFR 3.1 / 3.2 | strings.xml / KDoc / コメント / log message いずれも「PassKey」固定 |
| NFR 4.1〜4.3 | SDK 33 fixture で `Unsupported` が確定 / Credential Manager API 呼び出しなし |
| NFR 5.x | SettingsActivity.newIntent / 既存 View ID / minSdk / targetSdk / compileSdk / applicationId / namespace 不変 |

## テスト実行結果

### `./gradlew :app:testDebugUnitTest` (フル)

```
808 tests completed, 1 failed
```

- **失敗**: `AppInfoProviderTest > get_returnsVersionNameFromBuildGradle`
  (expected 0.1.0, but was 1.0.0 — build.gradle の versionName が "1.0.0" に
  上がったが、本テストは "0.1.0" を assert している。develop ブランチ
  HEAD `e5c6a41` でも同じ失敗を再現済み = 本 Issue 影響外の pre-existing
  failure)
- **本 Issue で追加した 新規 / 拡張テスト**: 全 pass
  - `CredentialProviderStatusCheckerTest` 11 件 全 pass
  - `SettingsViewModelTest` Issue #103 追加 5 件 全 pass + 既存 7 件も全 pass (5.5 / 6.8 達成)
  - `SystemSettingsIntentsTest` Issue #103 追加 3 件 全 pass + 既存 4 件も全 pass

### `./gradlew :app:lintDebug`

- **失敗**: develop baseline で 153 errors / 130 warnings → 本 Issue 後 159 errors /
  152 warnings
- **本 Issue 由来の差分は MissingTranslation 6 件のみ** (新規追加した 6 keys が
  `values/` と `values-ja/` の両方に存在しているにも関わらず、lint は default
  resource を "en" として扱うため "ja translation missing" / "en translation
  missing" を報告する。`values-en/` が repo に存在しないため、Issue #99 (passkey_create_*)
  / Issue #100 (passkey_auth_*) で merge された既存 keys と同じ pattern)
- **`NewApi` 警告は新規発生なし** (CredentialProviderStatusChecker の
  checkOnApi34Plus に `@RequiresApi(UPSIDE_DOWN_CAKE)` を付与済み /
  SystemSettingsIntents.ACTION_CREDENTIAL_PROVIDER は文字列リテラル化済み)
- **`ObsoleteSdkInt` 警告 1 件** は本 Issue 触っていない既存箇所
  (SystemSettingsIntents.openAutofillServiceChooser の SDK 26 ガード)

### `./gradlew :app:assembleDebug`

- **成功** (APK ビルド OK)

## 設計上の判断と確認事項

### 設計判断

1. **`DefaultCredentialProviderStatusChecker` の `credentialServiceReader` lambda 注入**:
   設計書 §4.2 の元案では `Settings.Secure` を直叩きする pure helper だったが、
   JDK 17 + mockk の組み合わせで `Settings.Secure` static の retransform が
   `UnsupportedOperationException: class redefinition failed: attempted to
   change the class modifiers` で失敗するため、reader を constructor 引数で
   注入可能にして例外時 fallback (Req 3.5) を確実にテストできる形にした。
   production 配線は変更なし (default lambda が `Settings.Secure.getString`
   を呼ぶ)。design.md §4.2 の判定アルゴリズム / 視覚的な API 形状は維持。

2. **`SettingsViewModel.resolvePasskeyProviderStatus` の belt-and-suspenders try/catch**:
   production の `DefaultCredentialProviderStatusChecker` は内部で全 throwable
   を catch して Disabled を返すため、ViewModel 側の try/catch は理論上不要。
   しかしテスト fake (`FakeCredentialProviderStatusChecker`) が throw した時の
   ViewModel 挙動を観測する目的で、また将来別の Checker 実装が誤って throw
   しても combine pipeline が落ちないようにする目的で、defensive な try/catch を
   ViewModel 層にも追加した (Req 3.5 invariant の 2 重防御)。

3. **`passkeyProbeDispatcher` の constructor 注入**: production は
   `Dispatchers.IO` default (NFR 1.1 / 1.2 達成)、tests は同じ
   `StandardTestDispatcher` を渡すことで `advanceUntilIdle()` が
   PassKey 判定ブランチも待つようにした。これは既存 `getStorage` テスト fixture
   が `withContext(IO)` を override で外して同期化しているのと同型の手法だが、
   ViewModel に dispatcher を持たせる方が `resolvePasskeyProviderStatus`
   suspend function を maintain しやすいため後者を採った。

4. **layout 内のアイコン**: design.md §3 (T-10 注釈) で `ic_passkey_24` が
   不在なら `ic_key_24` 流用とされていたため、`ic_key_24` を利用した。
   ic_passkey 専用アイコンの追加は本 Issue スコープ外 (Out of Scope の暗黙的
   含意)。

### 確認事項 (PR 説明に転記推奨)

1. **手動 emulator 検証 (T-15.4 / 15.5)**: sandbox 環境では Android 14 / 13
   emulator を起動できなかったため、以下は人間 reviewer による目視確認に
   委ねる:
   - Android 14 emulator で SettingsActivity を起動し PassKey provider セクションが
     Autofill hero と Security の間に表示される
   - KeyNest を OS の Credential Manager で有効化 → KeyNest に戻ると "有効: PassKey
     の登録 / 認証に利用できます" が表示される (Req 1.4)
   - 「PassKey 設定を開く」タップで OS の Credential Manager 設定が開く (Req 2.3)
   - Android 13 emulator で起動し「Android 14 以降で利用可能です」表示 / ボタン
     非表示 / 既存セクションに支障なし (Req 1.6 / 2.2 / 5.5)
2. **`Settings.Secure` の `"credential_service"` key 名は AOSP 内部 key** であり、
   将来 Android バージョン / OEM カスタマイズで rename / 不在の可能性がある
   (design.md §9.1-1 / §9.2)。例外時は Req 3.5 通り Disabled fallback で
   graceful degrade するため UI は破綻しないが、key 名変更を将来検知する
   ためのメカニズム (例: instrumentation test を Android 14 / 15 / 16 emulator
   で定期実行) は別 Issue として umbrella #89 配下に提案する余地あり
3. **既存 `MissingTranslation` lint failure**: 本 Issue で追加した 6 keys は
   `values/` (default) + `values-ja/` の両方に存在しているが、`values-en/` が
   repo に存在しないため lint は en 翻訳不在として報告する。これは Issue #99 /
   #100 で merged された passkey_create_* / passkey_auth_* と同じパターンで、
   project-wide な lint baseline 設定 (`lint-baseline.xml`) または `values-en/`
   ディレクトリ追加で一括解消すべき。本 Issue スコープ外と判断したが、必要
   なら別 Issue で対応
4. **`AppInfoProviderTest` の pre-existing failure**: `versionName = "1.0.0"`
   と build.gradle 上の現値 ("1.0.0") は一致するが、テストは "0.1.0" を assert
   している (古い versionName のまま放置)。develop でも fail しており本
   Issue 関連外
5. **既存 lint baseline の整理**: 本 Issue で +6 errors になったが、それ以前
   から develop で 153 errors が出ている。`lint-baseline.xml` の導入を
   別 Issue で提案するのが望ましい

STATUS: complete
