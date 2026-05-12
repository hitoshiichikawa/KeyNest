# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-11T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-1-impl--easykeynest-mvp-packagename-autofill
- HEAD commit: ed3c6dfac17b622bda56c8b7636b6aef7263510c
- Compared to: develop..HEAD (21 commits)
- Feature Flag Protocol: CLAUDE.md は repo に存在せず、protocol は適用外（通常 3 カテゴリ判定のみ）

## Verified Requirements

- 1.1 — `SaveCredentialUseCase` (app/src/main/java/com/example/keynest/domain/usecase/SaveCredentialUseCase.kt) で 4 項目を `EncryptedCredentialRecord` として永続化。テスト `SaveCredentialUseCaseTest.invoke_savesRecord_encryptsPassword_andCapturesSignature` で確認。
- 1.2 — password は `AesGcmCipher.encrypt` 経由でのみ永続化。`CredentialEntity` に平文カラムなし。`SaveCredentialUseCaseTest` で `passwordCiphertext` に平文が含まれないことを assert。`AesGcmCipherTest` (androidTest) で実 Keystore round-trip を担保。
- 1.3 — `PackageNameValidator` (`^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$`)。`SaveCredentialUseCaseTest.invoke_failsValidation_when*` / `PackageNameValidatorTest` / `CredentialEditViewModelTest.save_mapsPackageNameBlank_toFieldError` で確認。
- 1.4 — `CredentialEntity` は `package_name` に index のみで UNIQUE 制約なし。`CredentialDaoTest.insert_allowsMultipleRowsForSamePackage` / `SaveCredentialUseCaseTest.invoke_allowsMultipleCredentialsForSamePackage` で確認。
- 1.5 — `UpdateCredentialUseCase` / `DeleteCredentialUseCase` + `ListCredentialsUseCase` の `Flow<List<Credential>>` 購読。`UpdateCredentialUseCaseTest` / `DeleteCredentialUseCaseTest` / `ListCredentialsUseCaseTest` / `CredentialRepositoryImplTest.update_persistsModifiedFields_andRefreshedSignature` で確認。
- 2.1 — `PackageSignatureResolver.resolveSha256`（API 28+ で `GET_SIGNING_CERTIFICATES`、26-27 で `GET_SIGNATURES`）+ `SaveCredentialUseCase` で `signatureSha256` 保存。`PackageSignatureResolverTest.resolveSha256_api28_singleSigner_*` / `*_api26_singleSigner_*` で両分岐を担保。
- 2.2 — `SigningHash` nullable + `SaveCredentialUseCase` で null 許容、`signatureCapturedAt` も同時 null。`SaveCredentialUseCaseTest.invoke_savesRecord_withNullSignature_whenAppNotInstalled` / `PackageSignatureResolverTest.*_uninstalledPackage_returnsNull` で確認。
- 2.3 — `UpdateCredentialUseCase` が毎回 `sigResolver.resolveSha256` を再呼び出し。`UpdateCredentialUseCaseTest.invoke_updatesSignatureHashOnEveryCall` / `PackageSignatureResolverTest.resolveSha256_isReRunnable_pickingUpUpdatedSignerEachTime` で確認。
- 3.1 — `KeyNestAutofillService.onFillRequest` が `AssistStructureParser` → `ResolveAutofillCandidatesUseCase` → `FillResponseBuilder` の順で動作。`ResolveAutofillCandidatesUseCaseTest.invoke_returnsOnlyMatchingSignatureCredentials` 他で確認。
- 3.2 — `AssistStructureParser.parse` で usernameId/passwordId のいずれかが null になりうる。`KeyNestAutofillService` は `hasUsernameAndPassword == false` で `onSuccess(null)`。`AssistStructureParserTest.parse_returnsEmpty_whenNoCandidatesFound` / `parse_findsPasswordOnly_whenStructureLacksUsername` で確認。
- 3.3 — `FillResponseBuilder.buildLockedResponse` が候補ごとに `Dataset.Builder` + `setAuthentication` で構築。`FillResponseBuilderTest.buildLockedResponse_returnsNonNull_*` で確認。`DatasetPresentationFactory.build` で `label` / `subtitle` を `RemoteViews` に設定。
- 3.4 — `FillResponseBuilder.buildUnlockedDataset` で `AutofillValue.forText(usernameValue / passwordValue)` を `setValue`。`AutofillUnlockActivity` の成功パスで呼び出し。
- 3.5 — `onFillRequest` 内に network I/O 一切なし、復号もしない（`ResolveAutofillCandidatesUseCase` は ciphertext を読まずに metadata projection のみ返す）。`AutofillCandidate` 型に `passwordCiphertext` / `passwordIv` フィールドが存在しないことを `candidates_doNotCarryCiphertextOrIv` test で構造的に固定。
- 4.1 — `ResolveAutofillCandidatesUseCase.invoke` で `sigResolver.resolveSha256(callerPackage)` を取得し、credential の `signatureSha256` と比較。
- 4.2 — `signatureSha256 == callerHash` で filter（`SigningHash.equals` は `MessageDigest.isEqual` でタイミング攻撃耐性）。`ResolveAutofillCandidatesUseCaseTest.invoke_returnsOnlyMatchingSignatureCredentials` / `SignatureMismatchTest.candidateFilter_dropsMismatched_andRetainsMatched` で確認。
- 4.3 — `signatureSha256 != null` で filter。`ResolveAutofillCandidatesUseCaseTest.invoke_excludesCredentials_withNullSignature` / `SignatureMismatchTest` で確認。
- 4.4 — `ResolveAutofillCandidatesUseCaseTest.invoke_returnsOnlyMatchingMembers_whenMixed`（一致 / 不一致 / NULL 混在で一致のみ返却）で確認。
- 5.1 — `FillResponseBuilder.buildLockedResponse` で各 Dataset に `PendingIntent.getActivity(AutofillUnlockActivity, FLAG_MUTABLE)` を `setAuthentication` 付与。placeholder は `"••••••"` ハードコード値で credential 由来でない。`LockedFillResponseSecurityTest.lockedFillResponse_bytes_doNotContainCandidateCredentialMaterial` でバイト列に password 平文が含まれないことを assert。
- 5.2 — `AutofillUnlockActivity.runUnlockFlow` で `BiometricAuthenticator.authenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)` を起動。
- 5.3 — `AuthResult.Succeeded` 時に `UnlockVaultUseCase.invoke` → `AesGcmCipher.decrypt` → `FillResponseBuilder.buildUnlockedDataset` → `EXTRA_AUTHENTICATION_RESULT` に set → `setResult(RESULT_OK)`。`UnlockVaultUseCaseTest.invoke_returnsPlaintextCredential_whenDecryptionSucceeds` + `AesGcmCipherTest` (androidTest) で復号 round-trip を担保。
- 5.4 — `AuthResult.Cancelled / Failed / Unavailable` で `setResult(RESULT_CANCELED)` (`finishWithCancel`)。`BiometricAuthenticator.authenticate` の `onAuthenticationError` で `ERROR_USER_CANCELED / NEGATIVE_BUTTON / CANCELED` を `Cancelled` に分類。
- 5.5 — `PlaintextCredential.close` で `Arrays.fill(password, ' ')`。`AutofillUnlockActivity` で `finally { plain.close() }`。`PlaintextCredentialTest.close_zeroFillsPasswordBuffer` / `close_isIdempotent` で確認。`SaveCredentialUseCase` / `UpdateCredentialUseCase` も入力 `CharArray` を `Arrays.fill(' ')` で wipe（`SaveCredentialUseCaseTest` で assert）。
- 6.1 — `CredentialListActivity.onResume` で `hasEnabledAutofillService()` を確認し、未有効化かつ `redirectShown == false` なら `AutofillEnableActivity` を起動。
- 6.2 — `AutofillEnableActivity.launchSettings` で `Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply { data = Uri.parse("package:$packageName") }` を `startActivity`。
- 6.3 — `AutofillEnableActivity.renderState` で `isAutofillServiceEnabled()` の結果に応じて `btnEnable` と `textAlreadyEnabled` の `visibility` を出し分け。
- 7.1 — `app/build.gradle.kts` `minSdk = 26`。
- 7.2 — `targetSdk = 34` + `AndroidManifest.xml` に `<service android:name=".autofill.KeyNestAutofillService" android:permission="android.permission.BIND_AUTOFILL_SERVICE">` + `<intent-filter>` + `<meta-data android:name="android.autofill" android:resource="@xml/autofill_service_config" />`。
- 7.3 — `AndroidManifest.xml` に Accessibility / DeviceOwner / root 関連 permission なし。`InternetPermissionAbsenceTest` で `BIND_ACCESSIBILITY_SERVICE` / `BIND_DEVICE_ADMIN` の非宣言を assert。
- NFR 1.1 — `AesGcmCipher` (AES-256-GCM, randomizedEncryption=true) + `KeystoreKeyProvider` (alias `keynest_aead_v1`, 256bit, BLOCK_MODE_GCM)。`AesGcmCipherTest.encryptingSamePlaintextTwice_producesDistinctCiphertexts` で randomized 性を担保。
- NFR 1.2 — `KeystoreKeyProvider` は `AndroidKeyStore` provider 経由のみ。`SecretKey` を export する API を提供しない。
- NFR 1.3 — `SafeLogger` で `throwable.message` を forward せず `javaClass.simpleName` のみ記録。`SaveFailure.Storage` / `UpdateFailure.Storage` / `UnlockFailure.Decrypt` も同様。`SafeLoggerAuditTest.saveUseCase_storageError_doesNotIncludePassword` / `unlockUseCase_decryptError_doesNotIncludePassword` で確認。`Credential.toString` / `EncryptedCredentialRecord.toString` / `PlaintextCredential.toString` / `SigningHash.toString` / `EncryptedBlob.toString` も redaction 済み。
- NFR 1.4 — locked FillResponse には復号 password が一切含まれない（`FillResponseBuilder.buildLockedResponse` は placeholder のみ）。`LockedFillResponseSecurityTest` でバイト列を `marshall()` し forbidden token 不在を確認。
- NFR 1.5 — `AndroidManifest.xml` に `INTERNET` permission 非宣言。`InternetPermissionAbsenceTest.manifest_doesNotDeclare_internetPermission` で確認。
- NFR 2.1 — `FillRequestLatencyTest.resolveCandidates_medianLatency_within300ms_for100Credentials` で中央値 ≤ 300ms、p95 ≤ 600ms を assert。
- NFR 2.2 — `ResolveAutofillCandidatesUseCase` は decrypt を呼ばない（型契約として `AutofillCandidate` に ciphertext/iv フィールドが存在しないことを `candidates_doNotCarryCiphertextOrIv` で固定）。
- NFR 3.1 — `AssistStructureParser.parse` で全例外を catch し `ParsedFields(null, null)` を返却。`AssistStructureParserTest.parse_swallowsExceptions_andReturnsEmpty` で確認。`KeyNestAutofillService.onFillRequest` も全例外を catch し `onSuccess(null)` 返却（NFR 3.1 / 3.2 を担保）。
- NFR 3.2 — `ResolveAutofillCandidatesUseCase` は repository 例外を `try` で catch し empty 返却。`ResolveAutofillCandidatesUseCaseTest.invoke_swallowsRepositoryExceptions_returningEmpty` で確認。
- NFR 4.1 — Accessibility 依存なし（manifest に `BIND_ACCESSIBILITY_SERVICE` 非宣言、`InternetPermissionAbsenceTest` で固定）。Gradle deps に accessibility 系ライブラリなし。
- NFR 4.2 — Google Sign-In / Google Identity 系の Gradle 依存なし（`app/build.gradle.kts` 確認済み）。
- NFR 4.3 — `BIND_DEVICE_ADMIN` / root API 非使用（`InternetPermissionAbsenceTest` で確認）。
- NFR 5.1 — `SafeLogger.previewHex` で 8 文字超のみ先頭 8 文字 + `"..."` 表示。`SigningHash.toString` は raw bytes を漏らさず `"SigningHash(<32B>)"` を返す。`SafeLoggerTest.previewHex_*` で確認。

## Findings

なし。requirements.md の全 numeric ID（Requirement 1.1〜7.3 + NFR 1.1〜5.1）について、最新 commit 差分中に実装と単体／計装テストの両方が確認できた。tasks.md の `_Boundary:_` 違反も検出されなかった。

補足: `T10.1 AutofillFlowTest` は `@Ignore`（実機の biometric enrollment + 別アプリ target 必須のため）として明示されているが、impl-notes.md の "T10.1 が即時通る保証なし" / "deferred" 節に経緯と代替担保 (`AesGcmCipherTest` / `SignatureMismatchTest` / `SafeLoggerAuditTest` 等) が記載されているため、missing test には該当しない。

## Summary

requirements.md の全 numeric ID（41 件 = AC 26 件 + NFR 15 件）について、対応する実装と単体／計装テストが develop..HEAD の差分内で確認できた。tasks.md の `_Boundary:_` 制約も全タスクで遵守されている。Feature Flag Protocol は CLAUDE.md 未配置のため対象外。3 カテゴリ（AC 未カバー / missing test / boundary 逸脱）いずれにも該当する問題は見つからなかった。

RESULT: approve
