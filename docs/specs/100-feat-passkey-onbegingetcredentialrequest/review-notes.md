# Review Notes

<!-- idd-claude:review round=2 model=claude-opus-4-7 timestamp=2026-05-22T10:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-100-impl-feat-passkey-onbegingetcredentialrequest
- HEAD commit: d6f2accc1fd5d70ff831ed55f9372af542b29a87
- Compared to: develop..HEAD
- Feature Flag Protocol: リポジトリに `CLAUDE.md` が存在せず `## Feature Flag Protocol` 節も無いため、通常の 3 カテゴリ（AC 未カバー / missing test / boundary 逸脱）のみで判定。flag 観点は適用外。
- diff 規模: 20 ファイル / +2375 行・-24 行（`PasskeyAssertion.kt` / `PasskeyAssertionTypes.kt` / `AllowCredentialsParser.kt` / `GetEntryBuilder.kt` / `PasskeyAuthActivity.kt` / `KeyNestCredentialProviderService.kt` / `PasskeyRepository.kt` / `PasskeyRepositoryImpl.kt` / `ServiceLocator.kt` / `AndroidManifest.xml` / `strings.xml` x2 locale / 各テスト 5 ファイル / instrumentation placeholder / `impl-notes.md`）。tasks.md の T-01〜T-07 すべてが commit に反映されている。
- 前回 (round=1) reject の 4 件 finding（R1.x / R2.x / R3.x 残り / R4.x テスト系）は本 round で全て対応済み。impl-notes.md「Reviewer round=1 reject 是正」セクションで各 task の commit hash と是正内容を追跡。

## Verified Requirements

### Requirement 1: 候補抽出

- 1.1 — `GetEntryBuilder.build` が `allowCredentialIds.isEmpty()` 経路で `repository.listDiscoverableByRpId(rpId)` を呼ぶ（`GetEntryBuilder.kt:52-55`）。テスト: `GetEntryBuilderTest.build_allowCredentialsEmpty_callsListDiscoverableByRpId_andReturnsEntries`
- 1.2 — `allowCredentialIds.mapNotNull { id -> repository.findByCredentialId(id) }` で各 id について存在確認 + `it.rpId == rpId` フィルタ（`GetEntryBuilder.kt:57-62`）。テスト: `build_allowCredentialsSpecified_callsFindByCredentialIdForEachId` / `build_returnsOnlyExistingCredentials`
- 1.3 — 候補 0 件は `BeginGetCredentialResponse.Builder().build()`（entries 追加なし）で空応答（`KeyNestCredentialProviderService.kt:115-121`）。テスト: `onBeginGetCredentialRequest_publicKeyZeroCandidates_returnsEmptyResponse`
- 1.4 — Service callback 内では `runBlocking(Dispatchers.IO)` 内で DAO 点 lookup のみ実行し重い処理（生体 / 復号 / 署名）は Activity に委譲（`GetEntryBuilder.kt:52` / `PasskeyAuthActivity.runAuthenticationFlow`）
- 1.5 — `filterIsInstance<BeginGetPublicKeyCredentialOption>()` で publicKey 以外は除外、空なら空応答（`KeyNestCredentialProviderService.kt:104-110`）。テスト: `onBeginGetCredentialRequest_noPublicKeyOption_returnsEmptyResponse` / `onBeginGetCredentialRequest_passwordOptionOnly_returnsEmptyResponse_andDoesNotCallGetEntryBuilder`
- 1.6 — `accountName = userDisplayName ?: userName ?: rpId` / `displayName = rpDisplayName ?: rpId` の fallback 実装（`GetEntryBuilder.kt:75-80`）。テスト: `build_entryAccountName_fallsBackThroughDisplayNameThenUserNameThenRpId`

### Requirement 2: 生体認証

- 2.1 — `PasskeyAuthActivity.runAuthenticationFlow` で `biometricAuthenticatorFactory(this).authenticate(...)` を起動（`PasskeyAuthActivity.kt:153-157`）。`BiometricAuthenticator` は `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` 既存実装を再利用。テスト: `runAuthenticationFlow_biometricSucceeded_completesAssertion_andCommitsSignCount`
- 2.2 — Device Credential フォールバックは既存 `BiometricAuthenticator` の `Authenticators.DEVICE_CREDENTIAL` ビット指定で達成（#99 と同方針 / 本 Issue は再利用のみ）
- 2.3 — `Cancelled → GetCredentialCancellationException` / `Failed → GetCredentialUnknownException` / `Unavailable → GetCredentialUnknownException` のマッピング（`PasskeyAuthActivity.kt:158-168`）。テスト: `runAuthenticationFlow_biometricCancelled_returnsCancellationException_andLeavesSignCountUnchanged` / `_biometricFailed_returnsUnknownException` / `_biometricUnavailable_returnsUnknownException`
- 2.4 — `lifecycleScope.launch { try { ... } finally { wipeQueue.forEach { fill(0) }; finish() } }` で同一 ActivityScope 内に署名前 +1 → 復号 → 署名 → 応答を集約、ロールバックは `withTransaction` の coroutine cancel 経路でも保証（`PasskeyAuthActivity.kt:149-224`）。テスト: `runAuthenticationFlow_signFailure_rollsBackSignCount_andReturnsUnknownException` + `PasskeyRepositoryTest.signWithIncrement_rollsBackSignCount_whenSignerCoroutineCancelled`

### Requirement 3: assertion 署名 / authenticatorData / signCount

- 3.1 — `PasskeyRepository.loadPrivateKey(credentialId)` 実装が `EncryptedBlob(entity.privateKeyIv, entity.encryptedPrivateKey)` を `keynest_passkey_<credentialId>` alias の `AesGcmCipher.decrypt(blob)` で復号し PKCS#8 を返す（`PasskeyRepositoryImpl.kt:112-119`）。テスト: `PasskeyRepositoryTest.loadPrivateKey_returnsPlaintextPkcs8_thatRecoversEcPrivateKey`（PKCS#8 → ECPrivateKey 復元検証）
- 3.2 — `PasskeyAssertion.sign` が `AuthenticatorDataBuilder.build(rpIdHash, flags=0x05, signCount.toInt(), attestedCredentialData=null, extensions=null)` で 37 byte authenticatorData を生成（`PasskeyAssertion.kt:52-67`）。テスト: `PasskeyAssertionTest.sign_authenticatorData_rpIdHash_matchesSha256OfUtf8RpId` / `_flagsIs0x05_atIsZero_edIsZero` / `_signCountIs4BytesBigEndian` / `_lengthIs37Bytes`
- 3.3 — `signWithIncrement(credentialId) { newSignCount -> ... }` 内で signer 呼び出し前に `dao.incrementSignCount` を実行し新値を取得（`PasskeyRepositoryImpl.kt:121-129`）。Activity 側は新 signCount を `PasskeyAssertion.sign` の `signCount` 引数に渡す（`PasskeyAuthActivity.kt:180-198`）。テスト: `PasskeyRepositoryTest.signWithIncrement_signerReceivesNewSignCount` / `_calledTwice_incrementsSignCountByTwo` / `PasskeyAuthActivityTest.runAuthenticationFlow_calledTwice_incrementsSignCountByTwo`
- 3.4 — Option A の失敗時ロールバックは `database.withTransaction { ... }` 内で `signer(...)` が throw すると Room ktx 標準仕様で自動 rollback（`PasskeyRepositoryImpl.kt:124-129`）。テスト: `PasskeyRepositoryTest.signWithIncrement_rollsBackSignCount_whenSignerThrows` / `_whenSignerCoroutineCancelled` / `PasskeyAuthActivityTest.runAuthenticationFlow_signFailure_rollsBackSignCount_andReturnsUnknownException` / `_decryptFailure_rollsBackSignCount_andReturnsUnknownException`
- 3.5 — `Signature.getInstance("SHA256withECDSA").apply { initSign(...); update(authenticatorData); update(clientDataHash); sign() }` で ASN.1 DER 形式 ES256（`PasskeyAssertion.kt:69-79`）。テスト: `PasskeyAssertionTest.sign_signatureVerifiesWithGeneratedPublicKey_asn1Der` / `_signatureChangesWhenClientDataJsonChanges`
- 3.6 — Activity の `finally { wipeQueue.forEach { it.fill(0) }; wipeQueue.clear() }` で平文 PKCS#8 を wipe（`PasskeyAuthActivity.kt:219-223`）。テスト: `PasskeyAuthActivityTest.runAuthenticationFlow_wipesPlaintextPrivateKey_evenOnFailure`
- 3.7 — `buildAuthenticationResponseJson` で `id` / `rawId` / `type=public-key` / `response.{clientDataJSON, authenticatorData, signature, userHandle}` / `clientExtensionResults={}` を組み立て（`PasskeyAuthActivity.kt:241-268`）。base64url unpadded で encode（`Base64.URL_SAFE or NO_PADDING or NO_WRAP`）
- 3.8 — `PendingIntentHandler.setGetCredentialResponse(resultIntent, GetCredentialResponse(PublicKeyCredential(responseJson)))` + `setResult(RESULT_OK, resultIntent)` + `finish()`（`PasskeyAuthActivity.kt:200-206, 219-223`）

### Requirement 4: テスト

- 4.1 — `PasskeyAssertionTest`（pure JVM JUnit4 + Truth）が R3.2 (a-d) を bytewise で網羅（9 ケース、round=1 で既に確認済）
- 4.2 — 同テストの `_signatureVerifiesWithGeneratedPublicKey_asn1Der` で ASN.1 DER 形式 + verify 成功
- 4.3 — `KeyNestCredentialProviderServiceTest` に新規 5 ケース：(a) `_publicKeyAllowCredentialsEmpty_returnsDiscoverableEntries`、(b) `_publicKeyAllowCredentialsSpecified_returnsExistingMatches`、(c) `_publicKeyZeroCandidates_returnsEmptyResponse`、追加で `_passwordOptionOnly` / `_multiplePublicKeyOptions_aggregatesEntries`
- 4.4 — `PasskeyRepositoryTest.signWithIncrement_calledTwice_incrementsSignCountByTwo` + `PasskeyAuthActivityTest.runAuthenticationFlow_calledTwice_incrementsSignCountByTwo` の 2 段で end-to-end の +2 を bytewise 検証
- 4.5 — `PasskeyAuthActivityTest.runAuthenticationFlow_signFailure_rollsBackSignCount_andReturnsUnknownException` / `_decryptFailure_rollsBackSignCount_andReturnsUnknownException` で sign 失敗 / decrypt 失敗のそれぞれで rollback + `setGetCredentialException` を検証
- 4.6 — `KeyNestCredentialProviderServiceTest` の `onBeginCreateCredentialRequest_*` 既存 6 ケース + `onClearCredentialStateRequest_stillReturnsNull` が残置されている（`KeyNestCredentialProviderServiceTest.kt:92-205, 327-336`）。`onBeginGetCredentialRequest_stillReturnsEmptyResponse` は `_noPublicKeyOption_returnsEmptyResponse` にリネーム + 維持
- 4.7 — `PasskeyRepositoryTest` 既存 7 ケース（`save_*` / `findByCredentialId_*` / `findByRpIdAndUserHandle_*` / `delete_*`）が `database = mockk(relaxed = true)` を constructor に追加するだけで維持されている（`PasskeyRepositoryTest.kt:86-174`）。impl-notes.md 「既存テスト非破壊確認」で `Migration_4_5_Test` / `PasskeyDaoTest` の継続 pass を記録
- 4.8 — Manifest 変更は `<activity>` 1 件追加のみで `<service>` / `PasskeyCreateActivity` ブロック不変（`AndroidManifest.xml:120-134`）。impl-notes.md で `CredentialProviderServiceManifestTest` / `CredentialProviderXmlTest` の継続 pass を記録

### Non-Functional Requirements（要点）

- NFR 1.1 — 平文 wipe を `wipeQueue` + `finally fill(0)` で実装（テスト確認済）
- NFR 1.2 — Keystore alias `keynest_passkey_<credentialId>` を流用（#99 既存）
- NFR 2.1〜2.4 — autofill 関連 / #90 manifest / #91 dao/repository / #99 registration test の非破壊を impl-notes に記録
- NFR 3.1〜3.4 — minSdk 26 維持。新規クラスに `@RequiresApi(34)`、`<activity>` に `tools:targetApi="34"`、autofill 経路非破壊
- NFR 4.1〜4.2 — `INTERNET` 追加なし（Manifest コメントで明記）、`PasskeyAssertion` は pure local crypto
- NFR 5.1〜5.2 — Service callback で重い処理を呼ばず Activity に委譲、Activity 内処理は `lifecycleScope`（`Dispatchers.Main.immediate` 由来）で実行し署名は `signWithIncrement` 内で原子化
- NFR 6.1〜6.2 — string resources 両 locale で「PassKey」表記、新規クラスは `credentialprovider/authentication/` 配下に集約

## Findings

なし。

## Summary

Round=1 で reject 理由となった T-02〜T-07 が `29f3248` / `f10a9d2` / `dbb5cb5` / `f0e715e` / `9520dec` / `d6f2acc` の 6 commit で実装され、Requirements 1〜4 / NFR 1〜6 の全 AC を観測可能な実装 + テストでカバーする。tasks.md の境界（`credentialprovider/authentication/` 新設 + `KeyNestCredentialProviderService.onBeginGetCredentialRequest` のみ差し替え + `PasskeyRepository` への追加メソッド + Manifest `<activity>` 1 件追加 + strings 2 件追加）の逸脱は無く、`onBeginCreateCredentialRequest` / `onClearCredentialStateRequest` / #99 registration / #91 schema / #90 service 配線は touch されていない。impl-notes.md が記録する pre-existing 失敗（`AppInfoProviderTest`）と pre-existing lint error は本 Issue の追加コードに由来しないため判定対象外。

RESULT: approve
