# Requirements Document

## 概要 / Goal / Non-Goal

### 概要

KeyNest は現在パスワードベースの credential のみを保管しているが、umbrella Issue
#89 で「Android Credential Manager API 経由の PassKey プロバイダ対応」を進めること
が決定している。本 Issue (#90) はその Phase 1 として、

- `CredentialProviderService` の **Manifest 登録**（intent-filter / permission /
  meta-data）
- `res/xml/credential_provider.xml`（サポート credential type 宣言）
- `KeyNestCredentialProviderService.kt`（callback はすべて空の成功応答を返す
  最小実装）
- `app/build.gradle.kts` への `androidx.credentials` 依存追加
- API 34+ 限定の機能ゲーティング

の 4 点を整え、Android 14 (API 34) 以降の端末で OS の「パスワードと PassKey」設定
画面に **KeyNest が PassKey プロバイダ候補として表示される** ところまでを到達点と
する。

この Issue 単体では PassKey の **生成・保管・登録セレモニー・認証セレモニー** は
実装しない。OS から Service が認識され、後続サブ Issue (#89 の分割案 3〜4) で登録 /
認証セレモニーの実装を差し込める「土台」を整えることが目的である。

なお umbrella #89 のとおり、本機能で扱う名称表記は **「PassKey」** に統一する
（UI / 設定 / ドキュメント / コメント）。

### Goal

- `AndroidManifest.xml` に `CredentialProviderService` のサブクラスを
  `BIND_CREDENTIAL_PROVIDER_SERVICE` permission 付き `<service>` として宣言し、
  `android.service.credentials.CredentialProviderService` の intent-filter と
  `androidx.credentials.provider.CREDENTIAL_PROVIDER` の meta-data
  (`res/xml/credential_provider.xml` 参照) を持たせる。
- `res/xml/credential_provider.xml` で
  `androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL` をサポート credential type
  として宣言する（discoverable / non-discoverable の両対応を見据えた宣言にする）。
- `KeyNestCredentialProviderService` を新規追加し、3 つの主要 callback
  (`onBeginCreateCredentialRequest` / `onBeginGetCredentialRequest` /
  `onClearCredentialStateRequest`) で **空の成功応答** を返す最小実装にする。
- `app/build.gradle.kts` に `androidx.credentials:credentials` 依存
  （必要に応じて `androidx.credentials:credentials-play-services-auth`）を追加する。
- 機能の有効化は **API 34+ のみ**。`minSdk = 26` は維持し、API 33 以下では Service
  が OS から起動されないことを利用するだけでなく、`KeyNestCredentialProviderService`
  自体にも `Build.VERSION.SDK_INT` ガードを入れて将来的な誤起動を防ぐ。
- 上記の Manifest 宣言・xml・空応答の挙動を検証する単体 / instrumentation テスト
  を追加する。

### Non-Goal (Out of Scope)

- PassKey の **生成・保管** (umbrella #89 の分割案 2「保管モデル」が担当)。
- **登録セレモニー** (`onBeginCreateCredentialRequest` の実体: ES256 keypair 生成
  + 暗号化保管 + `PublicKeyCredential` 返却) (分割案 3)。
- **認証セレモニー** (`onBeginGetCredentialRequest` の実体: エントリ提示 +
  `BiometricPrompt` + assertion 署名返却) (分割案 4)。
- 既存クレデンシャル **一覧 UI への PassKey 表示** (分割案 5)。
- PassKey 単位の **rename / 削除 UI** (分割案 6)。
- **設定画面**の「PassKey プロバイダとして登録」状態表示・OS 設定への導線
  (分割案 7)。
- **README / Privacy Policy / Support ページ**への PassKey 取り扱い追記
  (分割案 8)。
- AAGUID の attestation への実埋め込み (本 Issue では「将来使う AAGUID を
  予約する」までで、attestation は `none` 想定。後続セレモニー Issue で利用)。
- StrongBox / 通常 Keystore の使い分け設計 (保管モデル Issue が担当)。
- API 26〜33 ユーザー向けの「PassKey 機能は Android 14 以降で利用可能」表示
  (設定画面 Issue が担当)。

## 背景

- umbrella Issue #89 で、KeyNest は Android Credential Manager API (`CredentialProviderService`)
  に PassKey プロバイダとして登録される方針が確定済み。**独自 SDK / IPC は採用しない**
  ため、OS 側の credential-provider 仕様にそのまま乗せる必要がある。
- 現状の `app/src/main/AndroidManifest.xml` には `KeyNestAutofillService`
  (BIND_AUTOFILL_SERVICE) は宣言されているが、`CredentialProviderService` は未宣言。
  そのため Android 14+ の「パスワードと PassKey」設定画面に KeyNest は出現しない。
- 後続の登録 / 認証セレモニー Issue (#89 分割案 3 / 4) で実体実装を差し込むには、
  Service が **OS から bind される状態** が前提となる。空応答でも Service の lifecycle
  が回ることを Phase 1 で検証しておく必要がある。
- 現行 `app/build.gradle.kts` は `compileSdk = 34` / `minSdk = 26` / `targetSdk = 34`。
  `androidx.credentials` 依存は未追加。
- umbrella #89 で人間確認済みの決定:
  - **AAGUID**: `2a56cf86-8332-4829-9f2a-e9a4adbc7abe` (本 Issue では値を定数として
    予約するだけ。Manifest / xml には埋め込まない)。
  - **minSdk 維持 + API 34+ で機能ゲーティング**: OK。
  - **表記**: 「PassKey」で統一。
  - **Discoverable / non-discoverable**: 両対応。
  - **生体認証フォールバック**: Device Credential (PIN/Pattern) にフォールバック。
  - **エクスポート禁止**: 既存 credential と同一ポリシー。

## ユーザーストーリー

- As a Android 14 以降のエンドユーザー, I want 端末の **「設定」→「パスワードと
  PassKey」→「PassKey サービス」** で KeyNest を選択肢として見つけられること, so that
  後続バージョンで PassKey 登録 / 認証セレモニーが実装されたとき、追加のセットアップ
  なしですぐに KeyNest を PassKey プロバイダとして指定できる。
- As a Android 13 以前のエンドユーザー, I want 本 Issue による Manifest 変更で
  アプリの起動 / 既存機能 (autofill / 一覧表示 / 編集) に影響が出ないこと, so that
  PassKey 非対応 OS でも従来通り KeyNest を使い続けられる。
- As a 後続セレモニー Issue (#89 分割案 3 / 4) の実装担当, I want
  `KeyNestCredentialProviderService` の callback 拡張ポイントが空応答状態で
  最初から存在すること, so that 自分の Issue では空応答を実装で差し替えるだけで済む
  （Manifest / 依存 / Service 骨格の整備を待たずに着手できる）。

## Requirements

> EARS 形式 (The X shall …, When … the X shall …, While … the X shall …) で記述。

### Requirement 1: Service 登録 (Manifest)

**Objective:** As an OS (Android Credential Manager), I want KeyNest を
credential-provider として認識できること, so that 「パスワードと PassKey」設定画面
に KeyNest を候補表示できる。

#### Acceptance Criteria

1.1. The `AndroidManifest.xml` shall `CredentialProviderService` のサブクラスである
`KeyNestCredentialProviderService` を `<service>` として宣言する。

1.2. The `<service>` 宣言 shall `android:permission="android.permission.BIND_CREDENTIAL_PROVIDER_SERVICE"`
を持ち、OS framework 以外からの bind を拒否する。

1.3. The `<service>` 宣言 shall `android:exported="true"` を持つ
（OS から bind 可能にするため）。

1.4. The `<service>` 宣言 shall `<intent-filter>` で
`android.service.credentials.CredentialProviderService` action を宣言する。

1.5. The `<service>` 宣言 shall `<meta-data android:name="android.credentials.provider"
android:resource="@xml/credential_provider" />`（あるいは androidx 仕様に従う最新の
meta-data 名）を持ち、サポート credential type 定義 xml を参照する。

1.6. While 端末が Android 14 (API 34) 以上であるとき, the `<service>` 宣言 shall
`tools:targetApi="34"` を付与し、ビルド時 lint で「API 34 限定」が明示される。

### Requirement 2: サポート credential type の宣言 (res/xml)

**Objective:** As an OS Credential Manager, I want KeyNest がサポートする credential
type を構造化された形式で取得できること, so that ユーザーが PassKey フローを開始した
ときに KeyNest を候補表示できる。

#### Acceptance Criteria

2.1. The `res/xml/credential_provider.xml` shall 新規作成され、`<credential-provider>`
ルート要素を持つ。

2.2. The xml shall サポート credential type として
`androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL`
（`androidx.credentials.provider.types.public-key-credential` 等、`androidx.credentials`
の安定版仕様に従った type 識別子）を 1 件以上宣言する。

2.3. The xml shall パスワード型 (`androidx.credentials.TYPE_PASSWORD_CREDENTIAL`)
は本 Issue では宣言しない（既存パスワード保管は `KeyNestAutofillService` 経由で
継続するため、credential-provider 経路への二重露出を避ける）。

2.4. The xml shall discoverable / non-discoverable の両対応を阻害する宣言
（どちらか一方しか受け付けない属性）を含まない。

### Requirement 3: 最小実装 (空応答)

**Objective:** As a 後続セレモニー Issue 担当, I want `KeyNestCredentialProviderService`
の 3 callback が空の成功応答を返す状態で最初から存在すること, so that 後続 Issue で
空応答を実体に差し替えるだけで PassKey 登録 / 認証が成立する。

#### Acceptance Criteria

3.1. The `KeyNestCredentialProviderService` shall `androidx.credentials.provider.CredentialProviderService`
のサブクラスとして `app/src/main/java/.../passkey/KeyNestCredentialProviderService.kt`
（詳細な package 配置は design で決定）に新規作成される。

3.2. When `onBeginCreateCredentialRequest(request, cancellationSignal, callback)` が
呼ばれたとき, the service shall **エントリゼロ件**の `BeginCreateCredentialResponse`
を `callback.onResult(...)` で返す。

3.3. When `onBeginGetCredentialRequest(request, cancellationSignal, callback)` が
呼ばれたとき, the service shall **エントリゼロ件**の `BeginGetCredentialResponse`
を `callback.onResult(...)` で返す。

3.4. When `onClearCredentialStateRequest(request, cancellationSignal, callback)` が
呼ばれたとき, the service shall 何も状態変更せず `callback.onResult(null)` 相当の
正常終了で応答する。

3.5. The service shall callback 内で例外をスローしない。OS 側で `CreateCredentialException`
/ `GetCredentialException` 系の異常応答を返すコードパスは本 Issue では実装しない
（後続セレモニー Issue が担当）。

3.6. While `Build.VERSION.SDK_INT < 34` であるとき, the service shall callback 内で
`callback.onError(...)` または「空応答」を返し、現バージョンでは Service として
何も実行しない設計上の防御層を持つ（OS が bind してこない前提だが、テスト / 将来
バックポート時の安全網として配置する）。

### Requirement 4: 依存追加とビルド整合

**Objective:** As a ビルドパイプライン, I want `androidx.credentials` 依存が
`libs.versions.toml` および `app/build.gradle.kts` に追加された状態で `assembleDebug`
が成功すること, so that 本 Issue 以降のサブ Issue が同じ依存セットを共有できる。

#### Acceptance Criteria

4.1. The `gradle/libs.versions.toml`（または同等の version catalog ファイル）shall
`androidx.credentials:credentials` の安定版を `libs` に追加する。

4.2. The `app/build.gradle.kts` shall 4.1 で追加した依存を `implementation` で参照する。

4.3. The `androidx.credentials:credentials-play-services-auth` 依存は **本 Issue では
追加しない**（Google Play services 経由のフローは現状必要なし。必要が生じれば後続
Issue で別途追加する）。

4.4. When `./gradlew assembleDebug` を実行したとき, the build shall 成功し、Manifest
merger 警告（特に `<service>` 競合・`tools:targetApi` 未解決）を出さない。

### Requirement 5: OS 認識（手動検証 / 非自動）

**Objective:** As an エンドユーザー, I want Android 14 以降の端末で KeyNest を
PassKey プロバイダ候補として確認できること, so that 後続セレモニー Issue 完了時に
ユーザーが KeyNest を選択して PassKey を発行・利用できる。

#### Acceptance Criteria

5.1. While 端末が Android 14 (API 34) 以上であるとき, the OS shall 「設定 →
パスワードと PassKey → PassKey サービス」一覧に **KeyNest** を表示する
（手動検証項目。CI 自動化は確認事項 3 を参照）。

5.2. When ユーザーが 5.1 の一覧で KeyNest を選択したとき, the OS shall KeyNest を
PassKey プロバイダとして有効化し、選択直後にクラッシュ / 例外ダイアログを出さない。

5.3. While 端末が Android 13 (API 33) 以下であるとき, the app shall インストール後の
通常起動・既存機能 (autofill / 一覧 / 編集) を従前通り動作させ、Manifest 変更による
クラッシュを起こさない。

### Requirement 6: テスト

**Objective:** As a 開発者 / メンテナ, I want Manifest 宣言と空応答 callback の挙動が
自動テストで回帰検知できること, so that 後続セレモニー Issue 実装時に Phase 1 部分の
退行を早期に検知できる。

#### Acceptance Criteria

6.1. The `KeyNestCredentialProviderServiceTest`（unit / Robolectric もしくは
instrumentation。具体的な実行環境は確認事項 3 で決定）shall
`onBeginCreateCredentialRequest` 呼び出し時に `callback.onResult` がエントリゼロ件の
`BeginCreateCredentialResponse` で呼ばれることを検証する。

6.2. The test shall `onBeginGetCredentialRequest` 呼び出し時に `callback.onResult`
がエントリゼロ件の `BeginGetCredentialResponse` で呼ばれることを検証する。

6.3. The test shall `onClearCredentialStateRequest` 呼び出し時に `callback.onResult`
が正常終了応答で呼ばれることを検証する。

6.4. The Manifest 検証テスト shall `KeyNestCredentialProviderService` の `<service>`
宣言に対して以下が揃っていることを検証する: (a) `android:permission` が
`BIND_CREDENTIAL_PROVIDER_SERVICE`, (b) `<intent-filter>` に
`android.service.credentials.CredentialProviderService` action, (c) `<meta-data>` で
`@xml/credential_provider` を参照, (d) `android:exported="true"`。

6.5. The `credential_provider.xml` 検証テスト shall `<credential-provider>` ルート
要素内にサポート credential type として `TYPE_PUBLIC_KEY_CREDENTIAL` 系の宣言が
1 件以上存在することを検証する。

6.6. When 既存テスト（`KeyNestAutofillService` 関連 / `FillResponseBuilderTest` /
`LockedFillResponseSecurityTest` 等）を本 Issue の変更後に実行したとき, the test runner
shall それらをすべて成功させる（Manifest 変更による既存 Service 宣言の破壊がない
ことの検証）。

## Non-Functional Requirements

### NFR 1: セキュリティ境界の不変

1. The Manifest shall `android.permission.INTERNET` を追加しない（umbrella #89 の
   「ネット境界は呼び出し側 / KeyNest 自体はオフライン」方針を維持）。
2. The Manifest shall `BIND_ACCESSIBILITY_SERVICE` / DeviceOwner / root 系 permission
   を追加しない（既存 NFR 4.1 / 4.3 を維持）。
3. The `KeyNestCredentialProviderService` shall callback 引数 (`BeginCreateCredentialRequest`
   等) に含まれる呼び出し元 package / origin 情報を Logcat に `info` レベル以上で
   出力しない（PassKey の RP 情報リークを防ぐ）。

### NFR 2: 既存 Service / Activity への非干渉

1. The Manifest 変更 shall 既存の `KeyNestAutofillService` / `AutofillUnlockActivity` /
   `CredentialListActivity` / `SettingsActivity` 等の宣言を変更しない。
2. The `app/build.gradle.kts` 変更 shall `minSdk` / `targetSdk` / `compileSdk` /
   `applicationId` / `namespace` を変更しない。
3. The 依存追加 shall 既存 `androidx.credentials` 以外の依存バージョンを変更しない
   （`androidx.credentials` 追加に伴う推移的依存の上書き衝突が発生した場合は
   design 段階で別途記録）。

### NFR 3: 表記統一

1. The Service クラス名 / xml ファイル名 / コメント / KDoc / ログメッセージ shall
   PassKey 機能に言及する箇所で **「PassKey」** 表記を用いる（umbrella #89 確認事項 3
   で人間確定済み）。
2. The `string` resource 追加が発生する場合, the resource shall ja / en で「PassKey」
   表記を統一する（本 Issue では string 追加は最小限。設定画面 / 一覧 UI の文言追加は
   別 Issue）。

### NFR 4: OS バージョンゲーティング

1. The Manifest shall `<uses-sdk minSdkVersion="26" />`（gradle 経由）を変更しない。
2. The `<service>` 宣言 shall `tools:targetApi="34"` を付与する。lint が「API 34
   未満端末で no-op になる」旨を警告しないよう、`KeyNestCredentialProviderService`
   実装ファイルにも `@RequiresApi(34)` または同等の SDK ガードを付与する。
3. While 端末が API 33 以下であるとき, the OS shall `KeyNestCredentialProviderService`
   を bind しないため、Kotlin 側 SDK ガードはあくまで防御層として機能する（Requirement
   3.6 と整合）。

## Out of Scope

- PassKey の生成 / 保管モデル / Room migration（umbrella #89 分割案 2）。
- `onBeginCreateCredentialRequest` の実体（ES256 keypair 生成・暗号化保管・
  `PublicKeyCredential` 返却）(分割案 3)。
- `onBeginGetCredentialRequest` の実体（エントリ提示・`BiometricPrompt`・assertion
  署名）(分割案 4)。
- 一覧 UI への PassKey 表示 / アイコン整備（分割案 5）。
- PassKey 単位の rename / 削除 UI（分割案 6）。
- 設定画面での「PassKey プロバイダ有効化状態」表示と OS 設定への導線（分割案 7）。
- README / Privacy Policy / Support ページの更新（分割案 8）。
- AAGUID `2a56cf86-8332-4829-9f2a-e9a4adbc7abe` の attestation 応答への実埋め込み
  （本 Issue では値の予約のみ。実利用は登録セレモニー Issue）。
- StrongBox / 通常 Keystore の使い分け設計（保管モデル Issue）。
- Hybrid transport (caBLE) / CXP / クラウド sync（umbrella #89 で Out of Scope 確定）。
- API 26〜33 ユーザー向けの「PassKey 機能は Android 14 以降で利用可能」UI 表示
  （設定画面 Issue が担当）。
- 生体認証フォールバック（Device Credential PIN/Pattern）の Service レベル実装
  （umbrella #89 で方針確定済みだが、本 Issue は空応答のみのため実装は登録 / 認証
  セレモニー Issue）。

## 確認事項 / オープン課題

> 本セクションは Issue #90 本文「確認事項」3 項目 + 親 Issue #89 で既に決着済みの
> 項目の整理。**親 #89 で確定済みの項目は「決定済み」として記載**し、本 Issue
> 単体で依然未決の項目は質問として残す。

### 決定済み（親 Issue #89 で確定）

1. **AAGUID**: `2a56cf86-8332-4829-9f2a-e9a4adbc7abe` を採用。本 Issue では定数として
   予約するに留め、attestation への埋め込みは登録セレモニー Issue で行う。
2. **minSdk 維持 + API 34+ ゲーティング**: `minSdk = 26` を維持。Credential Manager
   Provider 機能は API 34+ のみ有効。本 Issue ではこの方針に従い Manifest と
   Kotlin 側双方で SDK ガードを入れる。
3. **表記**: 「PassKey」で統一（UI / 設定 / ドキュメント / コード KDoc / ログ）。
4. **Discoverable / non-discoverable**: 両対応。本 Issue の `credential_provider.xml`
   宣言で片方限定にする属性は付与しない。
5. **生体認証フォールバック**: Device Credential (PIN/Pattern) にフォールバック。
   本 Issue では空応答のため未実装。登録 / 認証セレモニー Issue で実装する。
6. **エクスポート禁止**: 既存 credential と同じポリシー。本 Issue では PassKey 保管が
   まだ存在しないため非該当だが、保管モデル Issue で同ポリシーを適用する前提で
   設計する。

### 本 Issue で未決（design / 実装フェーズで決着が必要）

1. **`androidx.credentials` の採用バージョン**: 安定版を採用するか、PassKey 関連 API
   の充実度を見て alpha / beta を採用するか。Requirement 4.1 では「安定版」を
   default として記載しているが、安定版で `BeginCreateCredentialResponse` /
   `BeginGetCredentialResponse` のゼロ件応答が問題なく構築できるかを design 段階で
   実 API を見て確定する。alpha / beta 採用が必要な場合は本 Issue で
   `libs.versions.toml` にバージョン固定で追加し、後続 Issue で安定版アップグレード
   時に再評価する。

2. **API 34 未満端末での Kotlin 側 SDK ガードの実装方式**: OS は API 33 以下で
   Service を bind しないため、`@RequiresApi(34)` を付けるだけで十分か、それとも
   class 全体に `Build.VERSION.SDK_INT >= 34` の runtime check を入れるかは design
   で決定する（Requirement 3.6 / NFR 4.2 と関連）。本 Issue 時点では両層の防御を
   要求しているが、片方で十分と判断された場合は design.md で削減して良い。

3. **テストの実行環境 (unit / Robolectric / instrumentation)**: 本 Issue の Service
   は API 34+ 限定であり、CI が現状 API 34 エミュレータを確保できるかは未確認。
   選択肢は以下:
   - (a) **Robolectric** を `@Config(sdk = [34])` で使い、`KeyNestCredentialProviderService`
     の callback 呼び出しを mock した `OutcomeReceiver` で検証する。CI 構成変更不要。
   - (b) **Instrumentation test** を API 34 エミュレータで実行する。CI の Android
     emulator 設定変更が必要。
   - (c) (a) と (b) の **併用**（Robolectric で callback ロジック、instrumentation
     で OS 認識まで）。
   本 Issue の Requirement 6 は (a) を default として記述しているが、最終決定は design
   で行う。CI 側の API 34 エミュレータ確保コスト次第。

4. **`<meta-data>` の `android:name` 正式値**: Issue #90 本文では `credential-provider`
   と記載されているが、`androidx.credentials` 安定版での正式 meta-data 名 (例:
   `androidx.credentials.provider.CREDENTIAL_PROVIDER` 等) を実 API で確認のうえ
   design で確定する（Requirement 1.5 はこの確定を見越して「あるいは androidx 仕様
   に従う最新の meta-data 名」と幅を持たせている）。

5. **`KeyNestCredentialProviderService` の package 配置**: `io.github.hitoshiichikawa.keynest.passkey`
   配下が直感的だが、既存の `autofill` package と並列に置くか、`credential` 等の
   より広い名前にするか（後続で password credential も credential-provider に乗せる
   可能性を見据えるか）。本 Issue の Requirement 3.1 では `.../passkey/...` を仮置き
   しているが、最終決定は design で行う。

## 関連 Issue / PR

- **Parent**: #89 (umbrella: feat(passkey): Android Credential Manager 経由の
  passkey プロバイダ対応)
- **後続予定** (#89 分割案):
  - #89-分割案 2: PassKey 保管モデル（Room migration / 暗号化スキーム / DAO）
  - #89-分割案 3: 登録セレモニー (`onBeginCreateCredentialRequest` 実体)
  - #89-分割案 4: 認証セレモニー (`onBeginGetCredentialRequest` 実体)
  - #89-分割案 5: 一覧 UI への PassKey 統合
  - #89-分割案 6: PassKey 個別管理 UI
  - #89-分割案 7: 設定画面 / OS 設定導線
  - #89-分割案 8: ドキュメント更新
- **参考**: Android Credential Provider 公式 (https://developer.android.com/training/sign-in/credential-provider)、
  WebAuthn Level 2 (https://www.w3.org/TR/webauthn-2/)

## 用語集

- **PassKey**: WebAuthn / FIDO2 で定義される public-key credential。本リポジトリでは
  umbrella #89 確定により「PassKey」表記で統一する。
- **CredentialProviderService**: Android Credential Manager API で OS から bind される
  Service 基底クラス (`androidx.credentials.provider.CredentialProviderService`)。
  PassKey / password credential の発行 / 取得要求を受け取る。
- **BIND_CREDENTIAL_PROVIDER_SERVICE**: OS framework だけが Service を bind できる
  ように制限する system permission。Service 宣言で必須。
- **credential-provider XML**: サポートする credential type（PassKey / password 等）
  を OS に伝える `res/xml/*.xml`。Manifest の `<meta-data>` から参照される。
- **discoverable credential (resident key)**: ユーザー名入力なしで RP に提示できる
  PassKey。本 Issue では宣言レベルで両対応を維持する。
- **non-discoverable credential**: `allowCredentials` が必須となる PassKey。同上。
- **AAGUID**: Authenticator Attestation GUID。KeyNest を authenticator として識別する
  128bit UUID。umbrella #89 で `2a56cf86-8332-4829-9f2a-e9a4adbc7abe` 確定。
- **空応答 (empty response)**: `BeginCreateCredentialResponse` / `BeginGetCredentialResponse`
  をエントリゼロ件で組み立てて `callback.onResult` に渡すこと。本 Issue ではすべての
  callback で空応答を返す。
