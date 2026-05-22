# Requirements Document

## 冒頭メタ

| 項目 | 値 |
|---|---|
| **Issue** | #103 feat(passkey): 設定画面に PassKey プロバイダ登録状態と OS 設定への導線を追加 |
| **Parent (umbrella)** | #89 feat(passkey): Android Credential Manager 経由の passkey プロバイダ対応 |
| **Phase** | **Phase 6** (umbrella #89 サブ分割案 7「設定画面 / OS 設定導線」に対応) |
| **Depends on** | #90 (`CredentialProviderService` の Manifest 登録 / `KeyNestCredentialProviderService` の最小実装、merged) |
| **(参考) 先行 Phase** | #91 (Room 永続化) / #99 (登録セレモニー) / #100 (認証セレモニー) / #101 (一覧 UI 統合) |
| **作業ブランチ** | `claude/issue-103-design-feat-passkey-passkey-os` |
| **PR base** | `develop` |
| **表記ポリシー** | umbrella #89 確認事項 3 に従い、UI ラベル / KDoc / コメント / ログメッセージで PassKey 機能に言及する箇所は **「PassKey」** で統一 (「passkey」「Passkey」「passKey」「パスキー」は禁止)。リソース ファイル名は OS 制約により小文字 (`ic_passkey_*` 等) で可 |

## Introduction

KeyNest は #90 で `CredentialProviderService` を Manifest 登録し、Android 14
(API 34) 以降の端末で OS の「パスワードと PassKey」設定画面に PassKey プロバイダ
候補として登録できる状態になっている。さらに #99 (登録) / #100 (認証) で実体
セレモニーも完成しており、KeyNest は PassKey の保管 / 認証先として動作する。

しかし KeyNest 内の設定画面 (`SettingsActivity`) は現状 Autofill / セキュリティ /
Vault / About / Danger zone の 5 セクションを持つだけで、「KeyNest が PassKey
プロバイダとして OS に有効化されているか」を確認する手段がない。ユーザーは
PassKey 登録セレモニーを開始するまで KeyNest が選択された状態か分からず、
未有効化のままだと OS 側で KeyNest が候補に出ないという混乱が発生する。

本 Issue (#103 = umbrella #89 分割案 7 = **Phase 6**) は `SettingsActivity` に
「PassKey プロバイダ」セクションを追加し、(1) KeyNest の Credential Manager 登録
状態の表示、(2) Android 14+ では OS 設定への導線、(3) Android 13 以下では
「Android 14 以降で利用可能です」旨のフォールバック表示までを 1 PR の到達点とする。
PassKey 機能そのものの有効化 / 無効化トグルは OS 側で行うため、KeyNest 設定画面
からは触らない。

## Requirements

### Requirement 1: 「PassKey プロバイダ」セクションの追加

**Objective:** As an エンドユーザー, I want 設定画面で KeyNest が PassKey プロバイダとして OS に登録されているか一目で確認できること, so that PassKey 登録セレモニーを開始する前に有効化漏れを把握できる

#### Acceptance Criteria

1. The Settings screen shall AppBar 直下〜既存 SettingGroup の縦並びの中で、独立した「PassKey プロバイダ」SettingGroup を 1 つ表示する
2. The PassKey provider SettingGroup shall Eyebrow ヘッダーに `R.string.settings_passkey_provider_eyebrow` (= 「PASSKEY プロバイダ」相当) を表示する
3. The PassKey provider SettingGroup shall 既存の SettingGroup 視覚仕様 (`@color/kn_surface` 背景 / `@dimen/kn_r_md` 角丸 / `@color/kn_border` 1dp 外枠 / 24dp グループ間余白) を踏襲する
4. Where `Build.VERSION.SDK_INT >= 34` であり、かつ KeyNest が Credential Manager に登録 / 有効化されている場合, the PassKey provider section shall ステータス文言として `R.string.settings_passkey_provider_status_enabled` (= 「有効: PassKey の登録 / 認証に利用できます」相当) を表示する
5. Where `Build.VERSION.SDK_INT >= 34` であり、かつ KeyNest が Credential Manager で未有効化の場合, the PassKey provider section shall ステータス文言として `R.string.settings_passkey_provider_status_disabled` (= 「未設定: 下のボタンから有効化してください」相当) を表示する
6. Where `Build.VERSION.SDK_INT < 34` の場合, the PassKey provider section shall ステータス文言として `R.string.settings_passkey_provider_status_unsupported` (= 「Android 14 以降で利用可能です」相当) を表示する
7. The PassKey provider section shall ステータス文言のテキストカラーに既存 SettingRow の `sub` 文言と同じ `@color/kn_text_2` を適用し、Empty state / Error state 用の警告色は使わない (有効 / 未設定 / API 33 以下のいずれの状態でも tone は中立)
8. When ユーザーが `SettingsActivity.onResume` 経路で画面を再表示したとき, the Settings screen shall 状態判定をやり直し、OS 設定での有効化 / 解除を反映する

### Requirement 2: OS 設定への導線ボタン

**Objective:** As an エンドユーザー (Android 14 以降), I want OS の Credential Manager 設定画面を 1 タップで開けること, so that KeyNest を PassKey プロバイダとして有効化する操作に最短で到達できる

#### Acceptance Criteria

1. Where `Build.VERSION.SDK_INT >= 34` の場合, the PassKey provider section shall 「OS 設定を開く」ボタン (具体的なラベル文言は Open Questions 2) を表示する
2. Where `Build.VERSION.SDK_INT < 34` の場合, the PassKey provider section shall 「OS 設定を開く」ボタンを表示しない (View.GONE)
3. When ユーザーが「OS 設定を開く」ボタンをタップしたとき, the Settings screen shall OS の Credential Manager 設定画面 (Android 14+ では `Settings.ACTION_CREDENTIAL_PROVIDER` 相当の intent) を発火する
4. If 当該 intent がデバイスで解決できない場合, the Settings screen shall フォールバックとして `Settings.ACTION_SETTINGS` を発火する
5. If フォールバックも解決できない場合, the Settings screen shall 既存 `showIntentUnavailableSnackbar` と同等のメッセージ (`R.string.settings_intent_unavailable`) を Snackbar で表示し、画面を維持する
6. The 「OS 設定を開く」ボタン shall 視覚仕様として既存 `btn_open_autofill_settings` 等のセカンダリ CTA と同じスタイル (`Widget.Material3.Button.TonalButton` / KeyNest トークン) を踏襲し、ボタン単体としては destructive ではない中立スタイルを適用する
7. The 「OS 設定を開く」ボタン shall 表示条件 (Requirement 2.1) を満たす場合、KeyNest が既に有効化されている / 未有効化のどちらの状態でも **押下可能** とする (有効化済みでも別 PassKey 設定確認のために OS 設定を開きたいニーズがあるため)

### Requirement 3: KeyNest 登録状態の判定

**Objective:** As a SettingsViewModel, I want 端末の OS バージョンと Credential Manager の登録状態を統合した 3 値 (`Enabled` / `Disabled` / `Unsupported`) を導出できること, so that UI 層は単一の State に基づいて文言とボタン可視性を切り替えられる

#### Acceptance Criteria

1. The Settings layer shall 「KeyNest が Credential Manager に PassKey プロバイダとして登録 / 有効化されているか」を判定する仕組み (具体的な API 経路は design.md で確定。Open Questions 1) を 1 つ追加する
2. The Settings layer shall 判定結果を 3 値 `PasskeyProviderStatus = { Enabled, Disabled, Unsupported }` に正規化する (`Unsupported` は API 33 以下)
3. While `Build.VERSION.SDK_INT < 34` のとき, the Settings layer shall 判定 API を呼ばずに `PasskeyProviderStatus.Unsupported` を即時返す
4. While `Build.VERSION.SDK_INT >= 34` のとき, the Settings layer shall 判定 API を呼び、KeyNest が登録 / 有効化されていれば `Enabled`、それ以外は `Disabled` を返す
5. If 判定 API が例外を投げた場合, the Settings layer shall ログのみ残し、`PasskeyProviderStatus.Disabled` にフォールバックする (誤って `Enabled` を表示しない)
6. The Settings layer shall 判定処理を `viewModelScope` 上で非同期に行い、main thread を blocking しない
7. The Settings layer shall `SettingsViewModel.refresh()` 経路 (既存 `onResume` 呼び出し) で再判定を実行する
8. The Settings layer shall 判定 API 呼び出し時に PackageManager / Credential Manager から取得した呼び出し元情報や登録済み provider 一覧を Logcat に `info` レベル以上で出力しない (秘匿性維持)

### Requirement 4: 表記ポリシーと文言

**Objective:** As an エンドユーザー, I want 設定画面の PassKey 関連表記が他画面と一貫していること, so that 「passkey」「Passkey」「パスキー」のブレで違和感を覚えない

#### Acceptance Criteria

1. The PassKey provider section shall PassKey 機能に言及する文言で **「PassKey」** 表記を用いる (umbrella #89 確認事項 3 確定済み)
2. The PassKey provider section shall Eyebrow ヘッダー / ステータス文言 / ボタンラベル / contentDescription / Snackbar メッセージのいずれも、ハードコード文字列ではなく `R.string.settings_passkey_*` 系の string resource を経由して表示する
3. The newly added string resources shall `values/strings.xml` (en) と `values-ja/strings.xml` (ja) の両方に追加し、「PassKey」表記を i18n 不要の固定表記として扱う (#99 / #101 の `passkey_*` 系既存 key と命名整合)
4. The PassKey provider section shall ボタン文言 / Eyebrow ヘッダー文言の最終決定は Architect (design.md) に委ねる (Open Questions 2)。本 requirements は文言キーと表示条件のみを規定する

### Requirement 5: 既存設定画面への非干渉

**Objective:** As a メンテナ, I want 「PassKey プロバイダ」セクション追加が既存 Autofill / セキュリティ / Vault / About / Danger zone の挙動を破壊しないこと, so that 既存ユーザーが PassKey 機能の追加をきっかけに既存設定操作を失わない

#### Acceptance Criteria

1. The Settings screen shall 既存 5 セクション (Autofill hero / セキュリティ / Vault / About / Danger zone) の表示順 / レイアウト / View ID / クリック挙動を変更しない
2. The `SettingsViewModel.uiState` shall 既存 5 フィールド (`autofillStatus` / `lockStatus` / `metadata` / `storageBytes` / `appInfo`) のシグネチャを破壊しない (= 加法的拡張のみ。新規 `passkeyProviderStatus` 等を追加することは可)
3. The `SettingsViewModel.refresh()` 経路 shall 既存 `autofill` / `lock` / `storage` の再判定タイミングと整合した形で PassKey 判定も再実行する (= `refreshTick` mechanism の踏襲または同等手段)
4. The new section shall 既存 `SettingsActivityBinding` の View ID と衝突する命名を避ける (例: `btn_open_passkey_settings` / `text_passkey_provider_status` 等の重複しない ID を新設)
5. The Settings screen shall 「PassKey プロバイダ」セクションが API 33 以下で「Android 14 以降で利用可能です」と表示される状態でも、画面全体のスクロール / 他セクションのタップに支障を出さない

### Requirement 6: テスト

**Objective:** As a メンテナ, I want 状態判定 / 文言切替 / intent 発火の主要経路が自動テストで回帰検知できること, so that 後続 Issue (umbrella #89 分割案 8 ドキュメント更新等) 実装中に設定画面の退行を早期に検知できる

#### Acceptance Criteria

1. The `SettingsViewModelTest` (拡張 or 新規) shall API 34+ かつ KeyNest 有効化済みの fixture で `uiState.passkeyProviderStatus == Enabled` を返すことを検証する
2. The `SettingsViewModelTest` shall API 34+ かつ KeyNest 未有効化の fixture で `uiState.passkeyProviderStatus == Disabled` を返すことを検証する
3. The `SettingsViewModelTest` shall API 33 以下の fixture で `uiState.passkeyProviderStatus == Unsupported` を返し、判定 API が一切呼ばれないことを検証する
4. The `SettingsViewModelTest` shall 判定 API が例外を投げた場合に `Disabled` にフォールバックし、`Enabled` に誤って遷移しないことを検証する
5. The instrumentation / Robolectric test shall 「OS 設定を開く」ボタンタップで `Settings.ACTION_CREDENTIAL_PROVIDER` 相当の intent が `startActivity` 経由で発火することを `ShadowActivity.getNextStartedActivity()` (Robolectric) または Espresso intent 検証で確認する
6. The instrumentation / Robolectric test shall 当該 intent が解決できない fixture で `Settings.ACTION_SETTINGS` がフォールバックとして発火することを検証する
7. The instrumentation / Robolectric test shall API 33 以下の fixture で 「OS 設定を開く」ボタンが `View.GONE` であり、ステータス文言として `settings_passkey_provider_status_unsupported` が表示されることを検証する
8. The 既存 `SettingsViewModelTest` / `SettingsActivityTest` (存在する場合) shall 本 Issue 変更後も全件 pass し、Autofill / lock / vault / about / danger zone セクションの回帰がないことを確認する

## Non-Functional Requirements

### NFR 1: パフォーマンス / 応答性

1. The PassKey provider status 判定 shall `SettingsActivity.onCreate` / `onResume` から画面初描画までを 1 秒以内に完了させ、Autofill hero 等の既存セクションの描画を遅延させない (= 同期 blocking で 1 秒を超える API 呼び出しは行わない。必要なら IO dispatcher で非同期実行)
2. The PassKey provider status 判定 shall `SettingsActivity` のスクロール / 他ボタン押下を main thread blocking で阻害しない

### NFR 2: セキュリティ / 秘匿性

1. The PassKey provider 判定 shall PackageManager / Credential Manager API から取得した「KeyNest 以外の登録済み provider 一覧」「呼び出し元 package 情報」を Logcat に `info` レベル以上で出力しない (= 他アプリの存在が KeyNest ログ経由でリークしない)
2. The PassKey provider section shall AAGUID (`2a56cf86-8332-4829-9f2a-e9a4adbc7abe`) や Keystore alias (`keynest_passkey_<credentialId>`) を UI に表示しない (内部識別子をエンドユーザー画面に出さない)
3. The new string resources shall PassKey の暗号鍵 / private key / userHandle 等の機微情報を文字列に埋め込まない

### NFR 3: 表記統一

1. The new section の UI ラベル / KDoc / コメント / ログメッセージ shall PassKey 機能に言及する箇所で **「PassKey」** 表記を用いる (umbrella #89 確認事項 3)
2. The new string resources shall ja / en で「PassKey」表記を統一する (i18n 不要の固定表記)

### NFR 4: OS バージョンゲーティング

1. The Settings layer shall `minSdk = 26` を維持し、API 26〜33 でアプリが起動・スクロール・既存セクション操作に支障を出さないことを保証する
2. While `Build.VERSION.SDK_INT < 34` のとき, the Settings layer shall Credential Manager 系 API を呼ばず、`@RequiresApi(34)` ガードまたは `Build.VERSION.SDK_INT` runtime check で防御層を持つ
3. The PassKey provider section の「Android 14 以降で利用可能です」表示 shall API 33 以下でアプリがクラッシュしないことを最優先とし、ボタン非表示 + 文言のみで完結する

### NFR 5: 既存挙動への非干渉

1. The new section 追加 shall 既存 `SettingsActivity.newIntent(context)` の公開 API シグネチャを変更しない
2. The new section 追加 shall 既存 View ID (`btn_open_autofill_settings` / `btn_open_security_settings` / `btn_open_danger_zone` / `btn_oss_licenses` / `text_autofill_status` / `text_lock_status` / `text_vault_count` / `text_vault_latest_updated` / `text_vault_storage` / `text_app_version` / `toolbar` / `chip_autofill_status` 等) を削除 / 改名しない
3. The new section 追加 shall `compileSdk` / `targetSdk` / `minSdk` / `applicationId` / `namespace` を変更しない

## Out of Scope

- **KeyNest 内の PassKey 操作** (PassKey 単位の rename / 削除 / 個別管理画面) — umbrella #89 分割案 6 = 別 Issue
- **PassKey 機能の有効化 / 無効化トグル** (KeyNest 設定画面側からの On/Off 切替) — OS 側の Credential Manager 設定で行う仕様のため、本 Issue では実装しない (Issue 本文 Out of Scope を踏襲)
- **既存セクション (Autofill / セキュリティ / Vault / About / Danger zone) の文言 / レイアウト変更** — 本 Issue は加法的拡張のみ
- **PassKey 個別の登録件数 / 最終利用日時の Vault SettingGroup への表示** — 本 Issue では「KeyNest が PassKey プロバイダとして OS に登録されているか」のみを扱う
- **OS 設定 deeplink の packageName 直接指定** — `Settings.ACTION_CREDENTIAL_PROVIDER` への `Uri.parse("package:...")` 付与で KeyNest 直接選択を試みるかは Architect (design.md) で確定。本 requirements では「intent 発火 + フォールバック」のみ規定
- **README / Privacy Policy / Support ページへの PassKey 取り扱い追記** — umbrella #89 分割案 8 = 別 Issue
- **Recently used carousel への PassKey 統合** — #101 / 後続 UI 整備 Issue
- **DB schema 変更 / migration 追加** — 本 Issue は UI 層と SettingsViewModel への加法的拡張のみで、PassKey 永続化層 (#91) には触らない
- **`CredentialProviderService` 自体の変更** — 本 Issue は OS 設定への遷移と状態表示のみで、Service / Authenticator / Activity (`PasskeyAuthActivity` 等) は触らない

## Open Questions

> 本 Issue 本文「確認事項」を Architect (design.md) に申し送る。本 requirements
> はインタフェース契約と UI 表示条件のみを規定し、以下の決定は design 段階で確定する。

1. **KeyNest の Credential Manager 登録状態を判定する API 経路** (Issue 本文 確認事項 1)
   - `androidx.credentials` 1.5.0 系のどのクラス経由が推奨か
   - 候補: `androidx.credentials.CredentialManager.isUserConfigured()` 相当 / Framework `CredentialManager#isEnabledCredentialProviderService(ComponentName)` / PackageManager 経由で `BIND_CREDENTIAL_PROVIDER_SERVICE` 宣言済み service の有効化状態問い合わせ / その他公式 API
   - Architect は実 API の availability と stability を確認のうえ design.md で確定する。判定が同期 / 非同期どちらになっても、Requirement 3.6 (main thread blocking 禁止) を満たす形で実装する

2. **「OS 設定を開く」ボタンのラベル文言と Eyebrow ヘッダー文言の最終確定** (Issue 本文 確認事項 2)
   - ボタン候補: 「OS 設定を開く」/「PassKey 設定を開く」/「Android 設定」
   - umbrella #89 確定の「PassKey」表記との一貫性を考慮しつつ、ユーザーにとって直感的な動詞句を Architect が選定する
   - Eyebrow ヘッダー候補: 「PASSKEY プロバイダ」/ 「PASSKEY」/ 「CREDENTIAL MANAGER」
   - 本 requirements は表示条件と string resource キーのみを規定し、具体的文言は design.md で確定する

## 関連 Issue / PR

- **Parent (umbrella)**: #89 feat(passkey): Android Credential Manager 経由の passkey プロバイダ対応
- **Depends on**:
  - #90 feat(passkey): `CredentialProviderService` 最小実装 / Manifest 登録 — 本 Issue の「KeyNest が Credential Manager に登録可能」前提を確立済み
- **先行確立済み (Phase 2/3/4、本 Issue では非依存だが用語整合のため参照)**:
  - #91 feat(passkey): Room migration + PassKey 永続化
  - #99 feat(passkey): 登録セレモニー (`onBeginCreateCredentialRequest`) — 表記ポリシー「PassKey」確立、string key 命名規約 (`passkey_create_*`) を踏襲
  - #100 feat(passkey): 認証セレモニー (`onBeginGetCredentialRequest`)
  - #101 feat(passkey): 既存 credential 一覧への PassKey 統合
- **後続予定**:
  - umbrella #89 分割案 8: README / Privacy Policy / Support ページの PassKey 関連更新
