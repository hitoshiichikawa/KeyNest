# Implementation Notes — Issue #100 / feat(passkey): 認証セレモニー (onBeginGetCredentialRequest) 実装

> Developer サブエージェント用 implementation log. 設計や要件で確定済の判断 (requirements.md / design.md) は再記載しない。実装中に遭遇した実装メモ・差分・確認事項のみを記録する。

## タスク完了マッピング

| Task | Commit hash (作業順) | Note |
|------|---------------------|------|
| T-01 | 820c774 | `PasskeyAssertion` + types + `PasskeyAssertionTest` |
| T-02 | (later) | `PasskeyRepository` 3 メソッド + Impl 拡張 + Test |
| T-03 | (later) | `AllowCredentialsParser` + `GetEntryBuilder` + tests |
| T-04 | (later) | `PasskeyAuthActivity` + Manifest + strings (2 locale) |
| T-05 | (later) | Service `onBeginGetCredentialRequest` 差し替え + Service test 拡張 |
| T-06 | (later) | `PasskeyAuthActivityTest` |
| T-07 | (later) | Instrumentation placeholder + 統合確認 |

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

## 確認事項

> 実装中に判明した不明点や、矛盾を解消するために必要な人間判断は本セクションに列挙する。

(現時点では未確定事項なし。design.md §12.5 の PR 確認事項候補 5 件は PR description でカバー予定。)
