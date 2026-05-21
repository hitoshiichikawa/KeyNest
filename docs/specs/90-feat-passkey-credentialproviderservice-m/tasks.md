# Task Breakdown — Issue #90 / feat(passkey): CredentialProviderService の manifest 登録と最小骨組み

> 関連: `requirements.md`, `design.md`（本ディレクトリ）
>
> 各タスクは独立コミット可能な粒度で、依存順に並べている。Developer はこの順番で実装する。
>
> 略号:
> - **req**: `requirements.md` の Requirement 番号
> - **NFR**: `requirements.md` の Non-Functional Requirement 番号
> - 設計の詳細は `design.md` の対応セクション (`§N.M`) を参照

---

## T-01: `androidx.credentials` 依存追加（version catalog + app build.gradle）

### 概要

`gradle/libs.versions.toml` に `androidx.credentials:credentials` の **`1.5.0` (stable) を確定値**として追加し、`app/build.gradle.kts` の `dependencies` ブロックから `implementation` で参照する。`credentials-play-services-auth` は本 Issue では追加しない（req 4.3）。バージョンは design §4.5 / §9.1-1 で人間レビュアが確定済み。

### 変更ファイル

- 変更: `gradle/libs.versions.toml`
  - `[versions]` セクションに `credentials = "1.5.0"` を追加
  - `[libraries]` セクションに `androidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }` を追加
- 変更: `app/build.gradle.kts`
  - `dependencies { ... }` 内に `implementation(libs.androidx.credentials)` を 1 行追加（既存 `implementation(libs.androidx.biometric)` 直後あたりが配置として整合的）

### 公開 IF

- `libs.androidx.credentials` (Kotlin DSL accessor) が gradle スクリプト全体で参照可能になる

### 受入基準（要件対応）

- **req 4.1**: `libs.versions.toml` に `androidx.credentials:credentials` の安定版が追加されている
- **req 4.2**: `app/build.gradle.kts` で `implementation` 参照されている
- **req 4.3**: `credentials-play-services-auth` が **追加されていない**（明示的非追加）
- **req 4.4**: `./gradlew :app:assembleDebug` が成功する
- **NFR 2.2**: `minSdk` / `targetSdk` / `compileSdk` / `applicationId` / `namespace` を変更しない
- **NFR 2.3**: 既存 `androidx.*` 依存のバージョンを変更しない

### 完了条件

- `./gradlew :app:assembleDebug` が成功（Manifest merger 警告を出さない）
- `./gradlew :app:testDebugUnitTest` が既存全 pass のまま（依存追加だけでは既存テスト挙動は変わらない）
- `git diff` で変更が `libs.versions.toml` 2 行と `build.gradle.kts` 1 行のみであること

### 依存タスク

- なし（先頭）

### リスクと対応（design §9.3 / §9.2）

- `1.5.0` は **確定値であり、フォールバック手順を design レベルでは持たない**。
- 想定外に 1.5.0 で必要 API が欠落していると判明した場合（現状の認識では Phase 1 で困らない）、または compileSdk 35 等を強要する推移依存衝突が起きた場合は、Developer はバージョンを勝手に変更せず `needs-decisions` で人間にエスカレーションすること（design §9.2 参照）。

---

## T-02: `KeyNestCredentialProviderService` スケルトン実装 + 単体テスト

### 概要

`androidx.credentials.provider.CredentialProviderService` を継承した `KeyNestCredentialProviderService` を新規追加する。3 callback (`onBeginCreateCredentialRequest` / `onBeginGetCredentialRequest` / `onClearCredentialStateRequest`) はすべて **エントリ 0 件の成功応答** を返す空実装とする。class 全体に `@RequiresApi(34)` を付与し、API 34+ ゲーティングを行う（design §6）。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/KeyNestCredentialProviderService.kt`
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/KeyNestCredentialProviderServiceTest.kt`

### 公開 IF（design §4.1）

```kotlin
package io.github.hitoshiichikawa.keynest.credentialprovider

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // 34
class KeyNestCredentialProviderService : CredentialProviderService() {
    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    )
    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    )
    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    )
}
```

それぞれの本体:
- `onBeginCreateCredentialRequest`: `callback.onResult(BeginCreateCredentialResponse())`
- `onBeginGetCredentialRequest`: `callback.onResult(BeginGetCredentialResponse.Builder().build())`
- `onClearCredentialStateRequest`: `callback.onResult(null)`

### 受入基準（要件対応）

- **req 3.1**: Service が `…/credentialprovider/KeyNestCredentialProviderService.kt` に新規作成され、`CredentialProviderService` を継承
- **req 3.2**: `onBeginCreateCredentialRequest` がエントリ 0 件の `BeginCreateCredentialResponse` を `callback.onResult(...)` で返す
- **req 3.3**: `onBeginGetCredentialRequest` がエントリ 0 件の `BeginGetCredentialResponse` を `callback.onResult(...)` で返す
- **req 3.4**: `onClearCredentialStateRequest` が `callback.onResult(null)` で正常終了する
- **req 3.5**: いずれの callback も例外をスローせず、`onError` を呼ばない
- **req 3.6 / NFR 4.2**: クラス全体に `@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)` を付与（design §6.2 の方針で runtime SDK_INT ガードは追加しない）
- **NFR 1.3**: request 内容を `Log.i` 以上のレベルで出力しない（本実装は何もログ出力しないため自動的に成立）
- **req 6.1 / 6.2 / 6.3**: 各 callback の挙動を unit test (`KeyNestCredentialProviderServiceTest`) で検証

### テストケース（design §7.1）

`KeyNestCredentialProviderServiceTest` は Robolectric + `@Config(sdk = [34])` で以下 4 ケースを書く:

1. `onBeginCreateCredentialRequest` 呼び出し時に `OutcomeReceiver.onResult` が **エントリ 0 件の `BeginCreateCredentialResponse`** で 1 回だけ呼ばれること、`onError` が呼ばれないこと
2. `onBeginGetCredentialRequest` 呼び出し時に `OutcomeReceiver.onResult` が **エントリ 0 件の `BeginGetCredentialResponse`** で 1 回だけ呼ばれること、`onError` が呼ばれないこと
3. `onClearCredentialStateRequest` 呼び出し時に `OutcomeReceiver.onResult(null)` で 1 回だけ呼ばれること、`onError` が呼ばれないこと
4. （任意）3 callback いずれも例外をスローしないこと（`mockk` の `verify { onError(any()) wasNot Called }`）

モックは `mockk` を使う。`OutcomeReceiver` は `mockk<OutcomeReceiver<...>>()` で relaxed = false、`BeginCreateCredentialRequest` / `BeginGetCredentialRequest` / `ProviderClearCredentialStateRequest` は `mockk(relaxed = true)` で空 stub、`CancellationSignal()` は実物を渡す。

### 完了条件

- `./gradlew :app:testDebugUnitTest --tests *KeyNestCredentialProviderServiceTest*` が 4 ケースすべて pass
- lint で `@RequiresApi` 関連の警告が出ない
- 新規ファイル 2 個のみの diff

### 依存タスク

- T-01（`androidx.credentials` 依存が解決していること）

---

## T-03: `res/xml/credential_provider.xml` 新規追加 + 検証テスト

### 概要

サポートする credential type を OS に伝える xml resource を新規追加する。本 Issue では `androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL` のみを宣言し、`TYPE_PASSWORD_CREDENTIAL` は宣言しない（req 2.3、design §4.3）。

### 変更ファイル

- 新規: `app/src/main/res/xml/credential_provider.xml`
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/CredentialProviderXmlTest.kt`

### 公開 IF（design §4.3）

```xml
<?xml version="1.0" encoding="utf-8"?>
<credential-provider xmlns:android="http://schemas.android.com/apk/res/android">
    <capabilities>
        <capability android:name="androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL" />
    </capabilities>
</credential-provider>
```

### 受入基準（要件対応）

- **req 2.1**: `res/xml/credential_provider.xml` が新規作成され、`<credential-provider>` ルートを持つ
- **req 2.2**: `<capabilities>` 内に `androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL` が 1 件以上宣言される
- **req 2.3**: `TYPE_PASSWORD_CREDENTIAL` を **宣言しない**（明示的非宣言）
- **req 2.4**: discoverable / non-discoverable 片方限定にする属性を含まない
- **req 6.5**: xml の内容を `CredentialProviderXmlTest` で検証

### テストケース（design §7.1）

`CredentialProviderXmlTest` は Robolectric + `@Config(sdk = [33])`（xml parse だけなので 33 で十分）で以下を検証:

1. `context.resources.getXml(R.xml.credential_provider)` で `XmlResourceParser` を取得し、ルート要素名が `credential-provider` であること
2. parse 中に `<capability>` 要素を全件収集し、`android:name` 属性に `androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL` が含まれることをアサート
3. `android:name` 属性に `androidx.credentials.TYPE_PASSWORD_CREDENTIAL` が **含まれない** ことをアサート

### 完了条件

- `./gradlew :app:testDebugUnitTest --tests *CredentialProviderXmlTest*` が 3 アサーション pass
- `./gradlew :app:assembleDebug` 成功（xml が AAPT2 で有効と認識される）

### 依存タスク

- なし（T-01 / T-02 とは独立。T-04 で参照されるので T-04 より先に完了する必要がある）

---

## T-04: `AndroidManifest.xml` に `<service>` 宣言追加 + manifest 検証テスト

### 概要

`AndroidManifest.xml` ルートに `xmlns:tools` 名前空間を追加し、`<application>` 配下に `KeyNestCredentialProviderService` の `<service>` 宣言を追加する（design §4.2）。`tools:targetApi="34"` で API 34+ 限定であることを lint に明示する。既存 `<service>` / `<activity>` 宣言は **一切変更しない**（NFR 2.1）。

### 変更ファイル

- 変更: `app/src/main/AndroidManifest.xml`
  - `<manifest>` ルートに `xmlns:tools="http://schemas.android.com/tools"` を追加
  - `<application>` 配下の末尾（既存 `OssLicensesActivity` の後）に `<service>` ブロックを追加
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/manifest/CredentialProviderServiceManifestTest.kt`

### 追加する `<service>` ブロック（design §4.2 確定形）

```xml
<service
    android:name=".credentialprovider.KeyNestCredentialProviderService"
    android:exported="true"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_CREDENTIAL_PROVIDER_SERVICE"
    tools:targetApi="34">
    <intent-filter>
        <action android:name="android.service.credentials.CredentialProviderService" />
    </intent-filter>
    <meta-data
        android:name="android.credentials.provider"
        android:resource="@xml/credential_provider" />
</service>
```

### 受入基準（要件対応）

- **req 1.1**: `<service>` で `KeyNestCredentialProviderService` を宣言
- **req 1.2**: `android:permission="android.permission.BIND_CREDENTIAL_PROVIDER_SERVICE"` を持つ
- **req 1.3**: `android:exported="true"` を持つ
- **req 1.4**: `<intent-filter>` で `android.service.credentials.CredentialProviderService` action を宣言
- **req 1.5**: `<meta-data android:name="android.credentials.provider" android:resource="@xml/credential_provider" />` を持つ
- **req 1.6 / NFR 4.2**: `tools:targetApi="34"` を付与
- **req 4.4**: Manifest merger 警告を出さずに `assembleDebug` が成功
- **NFR 1.1 / 1.2**: `INTERNET` / `BIND_ACCESSIBILITY_SERVICE` / DeviceOwner 系 permission を追加しない
- **NFR 2.1**: 既存 `KeyNestAutofillService` / `AutofillUnlockActivity` / `CredentialListActivity` 等の宣言を変更しない
- **req 6.4**: Manifest 内容を `CredentialProviderServiceManifestTest` で検証
- **req 6.6**: 既存 `InternetPermissionAbsenceTest` / `OnBackInvokedCallbackEnabledTest` が引き続き pass

### テストケース（design §7.1）

`CredentialProviderServiceManifestTest` は Robolectric + `@Config(sdk = [33])` で `context.packageManager.getPackageInfo(packageName, PackageManager.GET_SERVICES or PackageManager.GET_META_DATA)` を使い、merged manifest を読んで以下を検証:

1. `services` 配列に `name` が `io.github.hitoshiichikawa.keynest.credentialprovider.KeyNestCredentialProviderService` の `ServiceInfo` が 1 件存在する
2. その `ServiceInfo.permission` が `android.permission.BIND_CREDENTIAL_PROVIDER_SERVICE`
3. その `ServiceInfo.exported` が `true`
4. `<intent-filter>` の action 検出: `context.packageManager.queryIntentServices(Intent("android.service.credentials.CredentialProviderService"), 0)` で `KeyNestCredentialProviderService` が 1 件 hit する（Robolectric の `ShadowPackageManager` が intent-filter を解釈する）
5. `ServiceInfo.metaData` の `"android.credentials.provider"` key が `@xml/credential_provider` の resource id（`R.xml.credential_provider`）を指す

### 完了条件

- `./gradlew :app:testDebugUnitTest --tests *CredentialProviderServiceManifestTest*` が 5 アサーション pass
- `./gradlew :app:testDebugUnitTest --tests *InternetPermissionAbsenceTest*` および `*OnBackInvokedCallbackEnabledTest*` が引き続き pass（NFR 2.1 / req 6.6）
- `./gradlew :app:assembleDebug` 成功、Manifest merger 警告ゼロ
- `./gradlew :app:lintDebug` で `tools:targetApi` 関連の lint エラーが出ない

### 依存タスク

- T-02（Service クラスが存在しないと `android:name` の参照が解決しない）
- T-03（`@xml/credential_provider` resource が存在しないと AAPT2 がエラー）

---

## T-05: Instrumentation test スケルトン配置（`@Ignore` 付き）

### 概要

CI に API 34 emulator を導入する作業は **#94 として別 Issue に carve out 済み**（design §9.4）。本 Issue では instrumentation test を **`@Ignore` 付きの placeholder** として配置し、CI 緑を保つ（design §7.2 / requirements 確認事項 3）。`@Ignore` は **#94 が完了して CI に API 34 emulator が組み込まれるまで維持**する。

### 変更ファイル

- 新規: `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/credentialprovider/KeyNestCredentialProviderServiceInstrumentationTest.kt`

### 配置内容

- クラスに `@RunWith(AndroidJUnit4::class)` および `@SdkSuppress(minSdkVersion = 34)` を付与
- 各 `@Test` メソッドに `@Ignore("CI に API 34 emulator が組み込まれるまで手動実行。#94 で gating 解除")` を付与
- テストメソッド本体は **空 or TODO コメントのみ**（後続 Issue で実装する Service binding 経由の検証 placeholder）:
  - `serviceBinding_returnsEmptyCreateResponse` (req 6.1 の instrumentation 版)
  - `serviceBinding_returnsEmptyGetResponse` (req 6.2 の instrumentation 版)
  - `serviceBinding_clearCredentialState_succeeds` (req 6.3 の instrumentation 版)

### 受入基準（要件対応）

- **req 6.1 / 6.2 / 6.3**（補強）: instrumentation 経由の検証経路が将来動かせる形で配置されている。本 Issue では Robolectric (T-02) が主検証手段。
- **CI 非破壊**: `@Ignore` 付きのため CI の `connectedAndroidTest` が（実行されたとしても）skip 扱いで通る

### 完了条件

- ファイルが新規追加され、`./gradlew :app:compileDebugAndroidTestSources` で compile エラーなし
- CI の既存ジョブが影響を受けないこと（実行されない `@Ignore` 付きのため自動的に成立）

### 依存タスク

- T-02（参照する Service クラスが存在する必要がある）

### 注意

- 本タスクで instrumentation test を「実装」するのではなく「placeholder 配置」のみ。実装は #94 で CI に API 34 emulator が組み込まれた後、後続セレモニー Issue (#89 分割案 3 / 4) の Developer が `@Ignore` を外して中身を埋める想定。

---

## T-06: 統合確認（テスト全 pass + 手動検証 + PR 確認事項転記）

### 概要

T-01 〜 T-05 が完了した状態で、全テスト pass / build 成功 / 既存テスト非破壊 / 手動検証 / PR 用の確認事項転記を行う。コミット対象は無し（確認のみ）。

### 変更ファイル

- なし

### 受入基準（要件対応）

- **req 4.4**: `./gradlew :app:assembleDebug` が成功し、Manifest merger 警告ゼロ
- **req 5.1**: 手動検証 — Android 14+ 実機 or emulator にインストール後、「設定 → パスワードと PassKey → PassKey サービス」一覧に **KeyNest** が表示されることを確認（スクリーンショット撮影）
- **req 5.2**: 手動検証 — 上記一覧で KeyNest を選択しても OS がクラッシュ / 例外ダイアログを出さないことを確認
- **req 5.3**: 手動検証 — Android 13 (API 33) 以下の emulator にインストールし、アプリ起動 / 既存機能 (autofill / 一覧 / 編集) が従前通り動作することを確認
- **req 6.6**: 既存テストが全 pass（`FillResponseBuilderTest` / `LockedFillResponseSecurityTest` / `CustomFieldFillResponseTest` / `InternetPermissionAbsenceTest` / `OnBackInvokedCallbackEnabledTest` / `*LayoutTokensTest` 等）

### 完了条件

- `./gradlew :app:testDebugUnitTest` が **全 test pass**（新規 3 ファイル + 既存全件）
- `./gradlew :app:lintDebug` が警告ゼロ or 既存ベースライン内
- `./gradlew :app:assembleDebug` が成功
- 手動検証（API 34 端末 + API 33 端末）のスクリーンショットを PR description に貼付
- `design.md §9.1` の決定済み事項（確認事項 A〜E が resolved である旨と、carve out 案件 #94）を PR 本文「確認事項」セクションに転記

### 依存タスク

- T-01 〜 T-05 全完了
