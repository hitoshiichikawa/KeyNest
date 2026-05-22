# Implementation Notes — Issue #100 / feat(passkey): 認証セレモニー (onBeginGetCredentialRequest) 実装

> Developer サブエージェント用 implementation log. 設計や要件で確定済の判断 (requirements.md / design.md) は再記載しない。実装中に遭遇した実装メモ・差分・確認事項のみを記録する。

## タスク完了マッピング

| Task | Commit hash (作業順) | Note |
|------|---------------------|------|
| T-01 | 820c774 | `PasskeyAssertion` + types + `PasskeyAssertionTest` |
| T-02 | 29f3248 | `PasskeyRepository` 3 メソッド + Impl 拡張 + Test |
| T-03 | f10a9d2 | `AllowCredentialsParser` + `GetEntryBuilder` + tests |
| T-04 | dbb5cb5 | `PasskeyAuthActivity` + Manifest + strings (2 locale) |
| T-05 | f0e715e | Service `onBeginGetCredentialRequest` 差し替え + Service test 拡張 |
| T-06 | 9520dec | `PasskeyAuthActivityTest` |
| T-07 | (本コミット) | Instrumentation placeholder + 統合確認 + 本 impl-notes 更新 |

## 実装メモ

### T-01 PasskeyAssertion

- `AuthenticatorDataBuilder.build(rpIdHash, flags = 0x05, signCount = ..., attestedCredentialData = null)` を再利用。`build_withoutAttestedCredentialData_omitsItAndAtFlag` テスト (#99) が既に AT=0 ケースを検証済み。
- `signCount` の範囲 (`0..0xFFFF_FFFFL`) 超過は `PasskeyAssertionException.Encoding` に集約。
- `ECPrivateKey` 復元は `KeyFactory.getInstance("EC")` (default provider) で実行。`InvalidKeySpecException` は `PasskeyAssertionException.SignFailed` でラップ。

### T-02 PasskeyRepository signWithIncrement (Option A)

- `signWithIncrement(credentialId, signer)` は `database.withTransaction { ... }` で囲む高階関数 API (design §6.1 案 C)。
- `incrementSignCount` (UPDATE) → `findByCredentialId` (SELECT) → `signer(newSignCount)` を 1 transaction 内で原子化。
- signer が throw した場合 `withTransaction` の標準仕様で rollback。
- `PasskeyRepositoryImpl` の constructor に `database: KeyNestDatabase` を追加 (既存 default arg は保持) + `nowMillisProvider` を追加。
- `PasskeyRepositoryTest` の既存 9 ケース (うち実体は 7 ケース) は `database` 引数を新規追加するだけで pass 復旧する。`loadPrivateKey` / `signWithIncrement` テストは in-memory `KeyNestDatabase` (Robolectric) ベースに切り替え。

### T-03 AllowCredentialsParser / GetEntryBuilder

- `kotlinx.serialization.json.Json` を `KeyNestCredentialProviderService.extractExcludeCredentialIds` と同じ慣行で使用 (`ignoreUnknownKeys = true; isLenient = true`)。
- `parseRpId` のみ `IllegalArgumentException`、`parseAllowCredentialIds` は defensive で空 list。
- `GetEntryBuilder.build(option)` 内で Repository 呼出は `runBlocking(Dispatchers.IO)` 同期化 (NFR 5.1 / Service callback 内呼出のため)。
- pendingIntent は credentialId を `keynest://passkey/auth/<credentialId>` data URI に乗せる。

### T-04 PasskeyAuthActivity

- `PasskeyCreateActivity` をパターン参考にする。確認画面なしで直接 BiometricPrompt を起動 (design §4.6)。
- `clientDataHash` は `ProviderGetCredentialRequest.credentialOption` (`GetPublicKeyCredentialOption`) の `clientDataHash` を優先し、null fallback で `SHA-256(requestJson)` を計算する設計 (design §7.4)。ただし `PasskeyAssertion.sign` は `clientDataJson` 文字列ベースで再計算する API なので、Activity 側は `clientDataJson` (string) と `clientDataHash` (bytes) の対応関係を意識せず `clientDataJson` をそのまま渡す方向で実装。
- string resources: `passkey_auth_prompt_title` / `passkey_auth_prompt_subtitle` を両 locale に追加。

### T-05 Service.onBeginGetCredentialRequest 差し替え

- `request.beginGetCredentialOptions.filterIsInstance<BeginGetPublicKeyCredentialOption>()` で publicKey のみ抽出。
- 既存 `onBeginGetCredentialRequest_stillReturnsEmptyResponse` テストは `_noPublicKeyOption_returnsEmptyResponse` にリネーム (実際の挙動相当)。

### T-06 PasskeyAuthActivityTest

- `BiometricAuthenticator` の差し替え seam: `PasskeyAuthActivity` 内に `@VisibleForTesting internal var biometricAuthenticatorFactory: (FragmentActivity) -> BiometricAuthenticator` を仕込む。
- `PasskeyAssertion` は object なので `mockkObject(PasskeyAssertion)` で差し替え。
- `Robolectric.buildActivity(PasskeyAuthActivity::class.java).withIntent(intent).create()` で起動 → `lifecycleScope` の coroutine が即時実行されるよう `runTest` + `Dispatchers.Main.setMain(...)` の組み合わせを使用検討。

## Reviewer round=1 reject 是正

T-01 単独で reviewer round=1 が出された結果、Requirements 1 (候補抽出) 全 6 AC / Requirement 2 (生体認証) 全 4 AC / Requirement 3 残り 6 AC / Requirement 4 残り 6 AC が未カバーで reject。本シリーズで T-02〜T-07 を実装契約どおりに追加して全 AC をカバーした。

### T-02 PasskeyRepository 拡張 (commit: 29f3248)

- `PasskeyRepository` interface に `listDiscoverableByRpId` / `loadPrivateKey` / `signWithIncrement` を **追加** (既存 4 メソッド署名は不変 / NFR 2.3 backward compatible)。
- `PasskeyRepositoryImpl` constructor に `database: KeyNestDatabase` と `nowMillisProvider: () -> Long` を追加。ServiceLocator は `PasskeyRepositoryImpl(database.passkeyDao(), database)` の 1 引数追加のみ。
- `signWithIncrement` 高階関数 API は `androidx.room:room-ktx` の `withTransaction { ... }` で wrap。signer が throw → Room ktx の標準仕様で自動 rollback (CancellationException 含む)。`room-ktx` は `libs.versions.toml` v2.6.1 で既存依存として宣言済みのため追加依存なし。
- `PasskeyRepositoryTest` はテストランナーを純 JVM JUnit4 から Robolectric (`@RunWith(AndroidJUnit4::class)` + `@Config(sdk = [34])`) に切り替え、in-memory `KeyNestDatabase` を新規 `signWithIncrement` ケース 5 件向けに併設。既存 7 ケース (mock-based) は `database = mockk(relaxed = true)` を 1 行追加するだけで pass 復旧した。
- `signWithIncrement` の rollback 検証で当初 `assertThat(ex).isSameInstanceAs(boom)` を使ったが、Room の `withTransaction` が coroutine boundary を跨ぐ際に同一クラス・同一 message の新インスタンスを再構築する挙動が確認された。`isInstanceOf(RuntimeException::class.java)` + `message` 一致に切り替えて pass。

### T-03 AllowCredentialsParser + GetEntryBuilder (commit: f10a9d2)

- `AllowCredentialsParser` は `kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }` で実装。`extractExcludeCredentialIds` (#99) と同じ defensive 方針。
- `GetEntryBuilder.build(option)` は `runBlocking(Dispatchers.IO)` で Repository 呼出を同期化。`rpId` 一致を防御層でも確認 (design §12.3 RP-spoofing 緩和)。
- `accountName` fallback は `entity.userDisplayName?.takeIf { it.isNotBlank() } ?: entity.userName?.takeIf { it.isNotBlank() } ?: entity.rpId` で空文字も skip するよう実装。
- T-04 で `PasskeyAuthActivity.pendingIntent(...)` を必要とするため、本コミットでは Activity 全体ではなく **companion object + 空の onCreate stub** を先行投入し、`GetEntryBuilderTest` の `pendingIntentTargetsPasskeyAuthActivity` が pass する状態を確保した。Activity の runAuthenticationFlow 本体は T-04 で投入。

### T-04 PasskeyAuthActivity + Manifest + strings (commit: dbb5cb5)

- `PasskeyCreateActivity` を pattern reference に AppCompatActivity ベース、確認画面なし。`runAuthenticationFlow(...)` を `@VisibleForTesting internal` で公開し T-06 から直接駆動できるようにした。
- BiometricAuthenticator 差し替え seam: `var biometricAuthenticatorFactory: (FragmentActivity) -> BiometricAuthenticator` を仕込み。
- Repository 差し替え seam: `var passkeyRepositoryProvider: () -> PasskeyRepository = { ServiceLocator.passkeyRepository }`。
- AuthenticationResponseJSON は `StringBuilder` 手書きで構築 (新規依存なし / #99 `PasskeyCreator.buildRegistrationResponseJson` 同パターン)。base64url は `Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP` で unpadded。
- AndroidManifest: `<activity android:name=".credentialprovider.authentication.PasskeyAuthActivity" ...>` を `PasskeyCreateActivity` の隣に追加。属性は `exported=false` / `excludeFromRecents=true` / `taskAffinity=""` / `theme="@style/Theme.KeyNest.Translucent"` / `tools:targetApi="34"` で `PasskeyCreateActivity` と統一。
- strings: `passkey_auth_prompt_title` / `passkey_auth_prompt_subtitle` を `values/strings.xml` と `values-ja/strings.xml` の両 locale に追加。EN は "Sign in with PassKey" / "Confirm your identity"、JA は "PassKey で認証" / "本人確認をしてください" で「PassKey」表記固定 (NFR 6.1)。

### T-05 Service.onBeginGetCredentialRequest 差し替え (commit: f0e715e)

- `KeyNestCredentialProviderService.onBeginGetCredentialRequest` を `BeginGetCredentialResponse.Builder().build()` 空応答スタブから `filterIsInstance<BeginGetPublicKeyCredentialOption>()` → `ServiceLocator.getEntryBuilder.build(option)` 経由で `PublicKeyCredentialEntry` を組み立てる本実装に差し替え。
- `onBeginCreateCredentialRequest` (#99) / `onClearCredentialStateRequest` (#90 stub) は touch しない (Req 4.6 / 4.8)。
- `ServiceLocator` に `getEntryBuilder: GetEntryBuilder by lazy { GetEntryBuilder(requireAppContext(), passkeyRepository) }` を `@get:RequiresApi(34)` + `@delegate:SuppressLint("NewApi")` 付きで追加。`PasskeyAssertion` / `AllowCredentialsParser` は `object` なので Service Locator 配線なし (design §4.8)。
- `KeyNestCredentialProviderServiceTest` の既存 `onBeginGetCredentialRequest_stillReturnsEmptyResponse` を `_noPublicKeyOption_returnsEmptyResponse` にリネーム + 新規 5 ケース (`publicKeyAllowCredentialsEmpty` / `publicKeyAllowCredentialsSpecified` / `publicKeyZeroCandidates` / `passwordOptionOnly` / `multiplePublicKeyOptions`) を追加。`GetEntryBuilder` を `mockk` で差し替え、戻り値の `PublicKeyCredentialEntry` 件数で R1.1 / R1.2 / R1.3 / R1.5 を検証。

### T-06 PasskeyAuthActivityTest (commit: 9520dec)

- Robolectric `@Config(sdk = [34])`。in-memory `KeyNestDatabase` で実 Room を駆動し signCount rollback を bytewise 検証。
- 当初 `Robolectric.buildActivity(...)` を `.create()` 呼ばずに使う形を試したが、`lifecycleScope` の coroutine が走らずに NPE。`PendingIntentHandler.retrieveProviderGetCredentialRequest(any())` を `returns null` で stub した上で `.create()` を実行し、production onCreate の早期 finish 経路を通したあと `clearMocks(PendingIntentHandler.Companion, recordedCalls = true, verificationMarks = true)` で記録された呼び出しをリセットしてからテスト本体の `runAuthenticationFlow(...)` を直接駆動する形に落ち着いた。
- `PendingIntentHandler.set*` は `mockkStatic(PendingIntentHandler::class)` ではなく `mockkObject(PendingIntentHandler.Companion)` + `just Runs` を使用 (`returns Unit` だと mockk の record phase が実 body を invoke して `GetCredentialResponse.credential.type` の null NPE を吐いた)。
- `tearDown()` で `controller.destroy()` を呼ぶと「FragmentManager has been destroyed」になるため省略。Activity GC は test method scope に任せる。
- 8 シナリオすべて pass: `biometricSucceeded` / `Cancelled` / `Failed` / `Unavailable` / `signFailure` / `decryptFailure (AEADBadTagException)` / `calledTwice → +2` / `wipesPlaintextPrivateKey_evenOnFailure`。

### T-07 統合確認 + Instrumentation placeholder + impl-notes 更新 (commit: 本コミット)

- `app/src/androidTest/java/.../credentialprovider/authentication/PasskeyAuthActivityInstrumentationTest.kt` を 3 ケース skeleton + `@Ignore("API 34 emulator が CI に揃うまで手動実行 — #94 完了後に @Ignore 解除")` で配置。
- 統合確認 (T-07 完了条件):
  - `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL (各 commit 後にも確認済み)。
  - `./gradlew :app:testDebugUnitTest` → 789 件中 788 件 pass。残 1 件は **pre-existing 失敗** (`AppInfoProviderTest.get_returnsVersionNameFromBuildGradle`: expected `"0.1.0"` vs `"1.0.0"`)。`app/build.gradle.kts:25` は v1.0.0 release commit (main: 53bc8b5) で `"1.0.0"` に更新済みだが、テスト側 assertion が `"0.1.0"` のまま残っているため。本 Issue が触らないコードで、依存 Issue 内に修正を含めると scope-creep になるため、impl-notes に確認事項として記録するに留める。
  - `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (3s, cache hit)。
  - `./gradlew :app:lintDebug` → 153 errors / 151 warnings。**本 Issue 新規追加コードに由来する error / warning は 0 件**。lintDebug が出す全 error は pre-existing で `DatasetPresentationFactory.kt` / `PackageSignatureResolver.kt` / `res/values/strings.xml` (MissingTranslation) / `res/values-en/strings.xml` / `res/values/type.xml` の 5 ファイルに集中している。当初 `GetEntryBuilder.build` 内の `PasskeyAuthActivity.pendingIntent(...)` 呼び出しに対して `VisibleForTests` 警告が出ていたが、`@VisibleForTesting` を `intent` / `pendingIntent` から外して `internal` 単独に格下げ (`PasskeyCreateActivity.intent` と同じ慣行) して解消。
- 既存テスト非破壊確認:
  - `KeyNestAutofillService` 関連テスト (`FillResponseBuilderTest` 等): pass 維持 (本 Issue は autofill 経路に触らない)。
  - #90 `CredentialProviderServiceManifestTest` / `CredentialProviderXmlTest`: pass 維持 (本 Issue の Manifest 変更は `<activity>` 追加のみで `<service>` / `<meta-data>` 不変)。
  - #91 `Migration_4_5_Test` / `PasskeyDaoTest`: pass 維持 (DAO / schema に触らない)。
  - `PasskeyRepositoryTest` 既存 7 ケース: `database = mockk()` を constructor に追加するだけで pass 復旧。
  - #99 `PasskeyCreatorTest` / `AuthenticatorDataBuilderTest` / `AttestationObjectBuilderTest` / `CoseKeyEncoderTest` / `CborWriterTest` / `KeynestAaguidTest` / `CreateEntryBuilderTest`: pass 維持 (#99 registration 配下は touch せず、`AuthenticatorDataBuilder` を読取り専用で再利用)。
  - `KeyNestCredentialProviderServiceTest` #99 既存ケース (`onBeginCreateCredentialRequest_*` / `onClearCredentialStateRequest_stillReturnsNull`): pass 維持。`onBeginGetCredentialRequest_stillReturnsEmptyResponse` は本 Issue で `_noPublicKeyOption_returnsEmptyResponse` にリネーム + 維持。
  - `InternetPermissionAbsenceTest` / `OnBackInvokedCallbackEnabledTest`: pass 維持 (`<uses-permission android:name="android.permission.INTERNET">` 追加なし / `enableOnBackInvokedCallback` 不変)。

## 確認事項

> 実装中に判明した不明点や、矛盾を解消するために必要な人間判断は本セクションに列挙する。

1. **`AppInfoProviderTest.get_returnsVersionNameFromBuildGradle` の pre-existing 失敗**: `app/src/test/java/.../util/AppInfoProviderTest.kt:30` が `versionName == "0.1.0"` を assert しているが、`app/build.gradle.kts:25` は v1.0.0 release commit (main: 53bc8b5) で `"1.0.0"` に更新済み。本 Issue が触らないコードで、修正は別 Issue で扱うのが scope-clean。本 PR で同梱して修正してよいかを reviewer 判断とする (Out of Scope と整理するなら本 impl-notes 記載のみで pass)。
2. **`lintDebug` の 153 errors も pre-existing**: 主に `PackageSignatureResolver.kt:52` (SigningInfo を min 26 で参照、API 28+) / `DatasetPresentationFactory.kt` の NewApi / `res/values{-en}/strings.xml` の MissingTranslation。本 Issue で追加した `authentication/` 配下に lint error / warning は 0 件。lint baseline 化 (`gradlew updateLintBaseline`) は別 Issue で行うべき判断 (#94 / Issue 設定整備 系)。
3. **design.md §12.5 PR 確認事項 5 件**: 本 Issue の PR description で reviewer に転記すること。
