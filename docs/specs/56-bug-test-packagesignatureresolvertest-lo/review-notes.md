# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-16T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-56-impl-bug-test-packagesignatureresolvertest-lo
- HEAD commit: c5206088dd521b145cce8da10cfe8fb46de744c6
- Compared to: develop..HEAD

変更ファイル（`git diff --name-only develop..HEAD`）:

- `app/src/test/java/com/example/keynest/util/PackageSignatureResolverTest.kt`
- `app/src/test/java/com/example/keynest/autofill/LockedFillResponseSecurityTest.kt`
- `docs/specs/56-bug-test-packagesignatureresolvertest-lo/impl-notes.md`
- `docs/specs/56-bug-test-packagesignatureresolvertest-lo/requirements.md`

製品コード（`app/src/main/**`）および `app/build.gradle.kts` には変更なし。

## Verified Requirements

- 1.1 — `PackageSignatureResolverTest` 全 7 メソッド pass を `app/build/test-results/testDebugUnitTest/TEST-com.example.keynest.util.PackageSignatureResolverTest.xml`（`tests="7" failures="0" errors="0"`）で確認。`Signature(bytes)` → `signatureOf(bytes)` ヘルパ（`mockk<Signature>(relaxed=true) { every { toByteArray() } returns bytes }`）への置換で 4 件の NPE が解消
- 1.2 — `LockedFillResponseSecurityTest.lockedFillResponse_bytes_doNotContainCandidateCredentialMaterial` pass を `TEST-com.example.keynest.autofill.LockedFillResponseSecurityTest.xml`（`tests="1" failures="0" errors="0"`）で確認。`@Before` の `mockkStatic(PendingIntent::class)` で `IntentSender.writeToParcel` 経路の NPE を回避、`@After` で `unmockkStatic` し他テスト汚染を遮断
- 1.3 — impl-notes.md にて `./gradlew :app:testDebugUnitTest` 全 509 件 pass / `BUILD SUCCESSFUL` を確認（新規 failure 0 件）
- 2.1 — `PackageSignatureResolverTest` の `@Test` メソッド名 7 件すべて要件記載と一致（`resolveSha256_api28_singleSigner_returnsCanonicalHash` / `resolveSha256_api28_multipleSigners_isOrderIndependent` / `resolveSha256_api28_uninstalledPackage_returnsNull` / `resolveSha256_api26_singleSigner_returnsHash` / `resolveSha256_api26_uninstalledPackage_returnsNull` / `resolveSha256_api26_emptySignatures_returnsNull` / `resolveSha256_isReRunnable_pickingUpUpdatedSignerEachTime`）。Arrange/Act/Assert 構造と assertion（`isEqualTo` / `isNull` / `isNotNull` / `isNotEqualTo`）はすべて保持。SHA-256 計算は本物のテスト入力 bytes に対して `PackageSignatureResolver.canonicalSha256` を素通しで実行している
- 2.2 — `LockedFillResponseSecurityTest`: forbidden token（`top-secret-password-do-not-leak` / `super-sekrit` / `leaky-secret`）の `doesNotContain` assertion は無変更。placeholder の `contains` assertion は元の `FillResponseBuilder.PLACEHOLDER` 直接比較から `placeholderAsBytes`（`PLACEHOLDER` の UTF-8 byte 列を ISO-8859-1 で字面化）への置換となっており、これは parcel が UTF-8 で書き込む実態に整合する byte-plane 上の正しい比較に**強化**されている（緩めではない）。impl-notes.md にて元実装は NPE で隠蔽されていた既存バグだったと整理されており、観点（placeholder の存在検証）は保持
- 2.3 — assertion を緩めた箇所なし、`@Ignore` 追加なし、mock は `Signature.toByteArray()` と `PendingIntent.getActivity` の **必要最小限**にとどまり、検証対象である `PackageSignatureResolver.canonicalSha256` / `FillResponseBuilder` の経路は実コードで実行されている
- 2.4 — snapshot ファイルは存在せず、盲目的更新の経路はない
- 3.1 — `git diff develop..HEAD -- app/src/main/java/com/example/keynest/util/PackageSignatureResolver.kt` は空（変更なし）
- 3.2 — `git diff develop..HEAD -- app/src/main/java/com/example/keynest/autofill/builder/FillResponseBuilder.kt` は空（変更なし）
- 3.3 — `git diff --name-only` の出力に `app/src/main/` 配下のファイルが含まれない（製品コード非変更）
- 4.1 — `app/build.gradle.kts` 変更なし（`io.mockk:mockk` / `org.robolectric:robolectric` は既に testImplementation に存在）
- 4.2 / 4.3 — 依存追加なしのため適用外
- NFR 1.1 / 1.2 — テスト実行結果 XML 上、PackageSignatureResolverTest は秒未満、LockedFillResponseSecurityTest は数秒オーダーで規定範囲内
- NFR 2.1 / 2.2 — mock は決定論的（固定 byte 列 / 固定 PendingIntent mock）であり flaky 経路なし。Robolectric は `@Config(sdk = [33])` 固定、`mockkStatic` の lifecycle は `@Before`/`@After` で対称

## Findings

なし

## Summary

3 カテゴリ（AC 未カバー / missing test / boundary 逸脱）すべてクリア。製品コード非変更・依存追加なし・既存 `@Test` 名と検証観点を維持しつつ、`Signature.toByteArray()` の Plain JVM stub 問題と `PendingIntent` IntentSender の NPE 経路を test-only mock で回避し、5 件失敗を 0 件 / 全 509 件 pass に復旧している。placeholder assertion の UTF-8 byte 列化は緩めではなく、parcel の実エンコーディングに整合する観点強化として妥当。

RESULT: approve
