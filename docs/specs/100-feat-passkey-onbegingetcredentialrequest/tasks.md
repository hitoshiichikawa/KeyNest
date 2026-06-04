# Task Breakdown — Issue #100 / feat(passkey): 認証セレモニー (onBeginGetCredentialRequest) 実装

> 関連: `requirements.md`, `design.md` (本ディレクトリ)
>
> 各タスクは独立コミット可能な粒度で、依存順に並べている。Developer はこの順番で実装する。
>
> 略号:
> - **Req**: `requirements.md` の Requirement / Acceptance Criteria 番号 (例: R3.2)
> - **NFR**: `requirements.md` の Non-Functional Requirement 番号
> - 設計の詳細は `design.md` の対応セクション (§N.M) を参照

## 概要 / 前提

### 着手前に Developer が読むべき資料

1. `docs/specs/100-feat-passkey-onbegingetcredentialrequest/requirements.md` (本 Issue PM 成果物)
2. `docs/specs/100-feat-passkey-onbegingetcredentialrequest/design.md` (本ファイルと同ディレクトリ)
3. 依存 #90 (merged): `docs/specs/90-feat-passkey-credentialproviderservice-m/design.md` §4 / §8 (本 Issue が差し替える Service の現状)
4. 依存 #91 (merged): `docs/specs/91-feat-passkey-room-migration-passkeyentit/design.md` §6 / §7 (DAO 公開 IF)
5. 依存 #99 (merged): `docs/specs/99-feat-passkey-onbegincreatecredentialrequ/design.md` §4 / §6 / §9 (登録セレモニーの対称構造 + `AuthenticatorDataBuilder` の再利用契約)
6. 既存コード:
   - `app/src/main/java/.../credentialprovider/KeyNestCredentialProviderService.kt` (#99 で `onBeginCreateCredentialRequest` 差し替え済 / 本 Issue は `onBeginGetCredentialRequest` のみ差し替え)
   - `app/src/main/java/.../credentialprovider/registration/PasskeyCreateActivity.kt` (実装 pattern 参考)
   - `app/src/main/java/.../credentialprovider/registration/AuthenticatorDataBuilder.kt` (#99 / 本 Issue で **再利用**)
   - `app/src/main/java/.../auth/BiometricAuthenticator.kt`
   - `app/src/main/java/.../security/{AesGcmCipher,KeystoreKeyProvider,EncryptedBlob}.kt`
   - `app/src/main/java/.../data/dao/PasskeyDao.kt` (`incrementSignCount(credentialId, timestamp)` / `listDiscoverableByRpId(rpId)` 既存)
   - `app/src/main/java/.../data/entity/PasskeyEntity.kt`
   - `app/src/main/java/.../domain/repository/PasskeyRepository.kt` (#99 inline)
   - `app/src/main/java/.../data/repository/PasskeyRepositoryImpl.kt` (#99 inline / 本 Issue で 3 メソッド追加 + constructor 拡張)
   - `app/src/main/AndroidManifest.xml` (#90 `<service>` + #99 `<activity>` 追加済 / 本 Issue は `<activity>` 1 件追加)
   - `app/src/main/java/.../di/ServiceLocator.kt`

### 前提依存

- #90 (merged): Service skeleton + Manifest `<service>` 配線
- #91 (merged): `PasskeyEntity` / `PasskeyDao` / `Migration_4_5`
- #99 (merged): `PasskeyCreator` / `PasskeyCreateActivity` / `CreateEntryBuilder` / `KeynestAaguid` / `AuthenticatorDataBuilder` / `CborWriter` / `CoseKeyEncoder` / `AttestationObjectBuilder` / `PasskeyRepository` inline interface / `PasskeyRepositoryImpl` inline impl

### コミット運用

- T-01〜T-06 は **1 タスク = 1 コミット**を基本とする (T-02 / T-05 のように関連テストを同コミットに含めてよい)
- T-07 は確認のみ、ファイル変更なし (コミット無し) を基本とするが、`@Ignore` placeholder のみコミット
- 各コミットの後で `./gradlew :app:compileDebugKotlin` 成功を確認してから次タスクへ進む

---

## T-01: PasskeyAssertion + PasskeyAssertionTypes + テスト

### 目的

WebAuthn Level 2 §6.1 / §6.3.3 に従う `authenticatorData` 組み立てと ES256 (P-256) 署名を pure-Kotlin で集約する。`AuthenticatorDataBuilder` (#99) を **再利用** し、新規 helper は作らない。Repository / Activity と独立に unit-test 可能。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/authentication/PasskeyAssertion.kt`
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/authentication/PasskeyAssertionTypes.kt` (`PasskeyAssertionInput` / `PasskeyAssertionResult` / `PasskeyAssertionException` 集約)
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/authentication/PasskeyAssertionTest.kt`

### 公開 IF (design §4.2 / §3.2 / §3.3)

```kotlin
internal object PasskeyAssertion {
    /** UP(0x01) | UV(0x04). 決定 1 / 決定 2. */
    internal const val FLAGS_UP_UV: Byte = 0x05
    /** rpIdHash(32) + flags(1) + signCount(4). 全長 37 byte 固定. */
    internal const val AUTHENTICATOR_DATA_LENGTH: Int = 37
    internal const val SIGNATURE_ALGORITHM: String = "SHA256withECDSA"
    internal const val EC_KEY_ALGORITHM: String = "EC"

    fun sign(input: PasskeyAssertionInput): PasskeyAssertionResult
}

internal data class PasskeyAssertionInput(
    val rpId: String,
    val clientDataJson: String,
    val signCount: Long,
    val privateKeyPkcs8: ByteArray,
)

internal data class PasskeyAssertionResult(
    val authenticatorData: ByteArray,
    val signature: ByteArray,
)

internal sealed class PasskeyAssertionException(message: String, cause: Throwable?) : Exception(message, cause) {
    class Encoding(cause: Throwable) : PasskeyAssertionException("authenticatorData encoding failed", cause)
    class SignFailed(cause: Throwable) : PasskeyAssertionException("ES256 signature failed", cause)
}
```

実装メモ:
- `authenticatorData` 組み立ては #99 の `AuthenticatorDataBuilder.build(rpIdHash, flags = 0x05, signCount = input.signCount.toInt(), attestedCredentialData = null, extensions = null)` を呼ぶ。`rpIdHash` は `AuthenticatorDataBuilder.rpIdHash(input.rpId)` で SHA-256 を取得 (#99 既存 helper)。
- `signCount` の範囲チェック: `require(input.signCount in 0..0xFFFF_FFFFL) { "signCount overflow" }` を `Encoding` 例外に変換。
- `clientDataHash = MessageDigest.getInstance("SHA-256").digest(input.clientDataJson.toByteArray(UTF_8))`.
- `ECPrivateKey` 復元: `KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(input.privateKeyPkcs8))`。`InvalidKeySpecException` は `SignFailed` でラップ。
- 署名: `Signature.getInstance("SHA256withECDSA").apply { initSign(priv); update(authenticatorData); update(clientDataHash) }.sign()`。例外は `SignFailed` でラップ。
- `PasskeyAssertion` は `object` なので test から直接呼ぶ (DI 不要)。
- raw byte / private key / signature を **logcat に出さない** (NFR 1.3 / SafeLogger 慣行)。

### 受入基準

- **Req 3.2 (a)**: `authenticatorData` の先頭 32 byte が `MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray(UTF_8))` と一致
- **Req 3.2 (b)**: `authenticatorData[32] == 0x05` (UP|UV、AT=0、ED=0)
- **Req 3.2 (c)**: `authenticatorData[33..36]` が `signCount` の 4 byte big-endian (0x00000001 / 0x00000002 / 任意の境界値)
- **Req 3.2 (d) / R4.1 (d)**: `authenticatorData.size == 37` 固定 (extensions なし)
- **Req 3.5 / R4.2**: ES256 署名が同テスト内で生成した public key で `Signature.getInstance("SHA256withECDSA").verify(sig)` を `true` で通る (ASN.1 DER 形式 / JCE 標準出力)
- 署名対象が `authenticatorData || clientDataHash` であること: `clientDataJson` を変えると signature が変わる回帰
- `signCount > 0xFFFF_FFFFL` で `PasskeyAssertionException.Encoding`

### テスト

- `PasskeyAssertionTest` (純 JVM / plain JUnit4 + Truth、Robolectric 不要 / #99 T-04 と同パターン):
  - `sign_authenticatorData_rpIdHash_matchesSha256OfUtf8RpId`
  - `sign_authenticatorData_flagsIs0x05_atIsZero_edIsZero`
  - `sign_authenticatorData_signCountIs4BytesBigEndian` (3 値: 1 / 2 / 0xFFFF_FFFFL = `Int.MAX_VALUE`+ 相当の境界)
  - `sign_authenticatorData_lengthIs37Bytes`
  - `sign_signatureVerifiesWithGeneratedPublicKey_asn1Der`
  - `sign_signatureChangesWhenClientDataJsonChanges`
  - `sign_throwsEncoding_whenSignCountOverflowsUnsigned32`
  - `sign_throwsSignFailed_whenPrivateKeyPkcs8IsMalformed` (任意 byte 列を inject)

### 完了条件

- `./gradlew :app:compileDebugKotlin` 成功
- `./gradlew :app:testDebugUnitTest --tests *PasskeyAssertionTest*` 全 pass

### 依存タスク

- なし (先頭。`AuthenticatorDataBuilder` は #99 で merged 済の前提)

---

## T-02: PasskeyRepository に 3 メソッド追加 + Impl 拡張 + テスト

### 目的

認証セレモニーが必要とする 3 公開 API (`listDiscoverableByRpId` / `loadPrivateKey` / `signWithIncrement`) を `PasskeyRepository` interface に追加し、`PasskeyRepositoryImpl` で実装する。Option A (req 決定 3) は `signWithIncrement(credentialId, signer)` 高階関数で原子化する (design §4.5 / §6.1)。

### 変更ファイル

- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/repository/PasskeyRepository.kt`
  - 3 メソッド (`listDiscoverableByRpId` / `loadPrivateKey` / `signWithIncrement`) を interface に追加
- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/repository/PasskeyRepositoryImpl.kt`
  - 3 メソッドを実装
  - constructor に `database: KeyNestDatabase` と `nowMillisProvider: () -> Long` を追加 (default arg / backward compatible)
- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/di/ServiceLocator.kt`
  - `passkeyRepository` lazy で `PasskeyRepositoryImpl(database.passkeyDao(), database)` を渡す (1 引数追加)
- 変更: `app/src/test/java/io/github/hitoshiichikawa/keynest/data/PasskeyRepositoryTest.kt`
  - 既存 9 ケースのセットアップに in-memory `KeyNestDatabase` を 1 行追加 (constructor 拡張に追従 / NFR 2.3 backward compatible)
  - 新規ケース 8 件 (下記テスト節) を追加

### 公開 IF (design §4.5)

```kotlin
// domain/repository/PasskeyRepository.kt 追記
interface PasskeyRepository {
    // 既存: save / findByCredentialId / findByRpIdAndUserHandle / delete ...

    suspend fun listDiscoverableByRpId(rpId: String): List<PasskeyEntity>

    suspend fun loadPrivateKey(credentialId: String): ByteArray

    suspend fun <T> signWithIncrement(
        credentialId: String,
        signer: suspend (newSignCount: Long) -> T,
    ): T
}
```

```kotlin
// data/repository/PasskeyRepositoryImpl.kt 拡張
class PasskeyRepositoryImpl(
    private val dao: PasskeyDao,
    private val database: KeyNestDatabase,                                          // 本 Issue で追加
    private val keyProviderFactory: (String) -> KeystoreKeyProvider = { alias -> KeystoreKeyProvider(keyAlias = alias) },
    private val cipherFactory: (KeystoreKeyProvider) -> AesGcmCipher = { provider -> AesGcmCipher(provider) },
    private val keyStoreLoader: () -> KeyStore = { ... },
    private val nowMillisProvider: () -> Long = { System.currentTimeMillis() },     // 本 Issue で追加
) : PasskeyRepository { ... }
```

実装メモ (design §4.5.2 / §6):
- `listDiscoverableByRpId(rpId)` = `dao.listDiscoverableByRpId(rpId)` 直接委譲
- `loadPrivateKey(credentialId)`:
  - `dao.findByCredentialId(credentialId)` で entity 取得、null → `IllegalStateException`
  - `EncryptedBlob(entity.privateKeyIv, entity.encryptedPrivateKey)` 構成
  - `cipherFactory(keyProviderFactory(aliasFor(credentialId))).decrypt(blob)` で平文 PKCS#8 を返す
- `signWithIncrement(credentialId, signer)`:
  - `androidx.room.withTransaction { ... }` で wrap
  - `dao.incrementSignCount(credentialId, nowMillisProvider())` 呼出
  - `dao.findByCredentialId(credentialId)?.signCount` で新値を取得 (null は `IllegalStateException`)
  - `signer(newSignCount)` を呼んで戻り値を返す
  - signer が throw → withTransaction が自動 rollback (Room ktx 仕様)
  - import: `androidx.room.withTransaction`

### 受入基準

- **Req 3.1**: `loadPrivateKey(credentialId)` の戻り値が PKCS#8 形式の平文 byte 配列で、`KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(it))` で `ECPrivateKey` に復元できる
- **Req 3.3 / R4.4**: `signWithIncrement(credentialId) { newSignCount -> ... }` を 2 回呼ぶと `incrementSignCount` が 2 回 (DAO mock or in-memory Room で count) 呼ばれ、DB の `signCount` が **+2** になる
- **Req 3.4 / R4.5**: `signer` が throw すると DB の `signCount` が元値に戻る (rollback 成立)
- **Req 1.1 (Repository 経由)**: `listDiscoverableByRpId(rpId)` が DAO の同名メソッドに委譲され、戻り値がそのまま返る
- **NFR 2.3**: 既存 `PasskeyRepositoryTest` 9 ケースが constructor 1 引数追加に追従して全 pass

### テスト

`PasskeyRepositoryTest` 既存 9 ケース (既存 `save` / `delete` / `findByCredentialId` / `findByRpIdAndUserHandle` 等) を **削除せず** セットアップ更新で維持 + 新規ケース:

- `listDiscoverableByRpId_delegatesToDao`
- `loadPrivateKey_returnsPlaintextPkcs8_thatRecoversEcPrivateKey` (in-memory `KeyNestDatabase` + 既存 `save` 経路で 1 件 INSERT → `loadPrivateKey` 呼出 → `KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(...))` 成功)
- `loadPrivateKey_throwsIllegalStateException_whenEntityMissing`
- `signWithIncrement_invokesSigner_andCommitsOnSuccess` (signer が return → 戻り値が伝搬 + signCount +1)
- `signWithIncrement_rollsBackSignCount_whenSignerThrows` (signer が `RuntimeException` を throw → 例外伝搬 + signCount 元値)
- `signWithIncrement_calledTwice_incrementsSignCountByTwo` (R4.4 直接対応)
- `signWithIncrement_signerReceivesNewSignCount` (signer が受け取った値が現在 DB 値と一致)
- `signWithIncrement_rollsBackSignCount_whenSignerCoroutineCancelled` (signer 内で `CancellationException` を throw → rollback 成立。Activity onDestroy ロールバック保証の回帰)

### 完了条件

- `./gradlew :app:compileDebugKotlin` 成功
- `./gradlew :app:testDebugUnitTest --tests *PasskeyRepositoryTest*` 全 pass (既存 9 + 新規 8)
- `./gradlew :app:testDebugUnitTest --tests *Migration_4_5_Test*` / `*PasskeyDaoTest*` 引き続き pass (NFR 2.3)

### 依存タスク

- なし (T-01 と並列可だが、本 tasks では依存順に並べる)

---

## T-03: AllowCredentialsParser + GetEntryBuilder + テスト

### 目的

`BeginGetPublicKeyCredentialOption.requestJson` から `allowCredentials[].id` / `rpId` を抽出する parser と、`PasskeyRepository` 経由で候補を抽出して `PublicKeyCredentialEntry` に変換する builder を追加する。Service callback から呼ばれる前提で `runBlocking(Dispatchers.IO)` で同期実行。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/authentication/AllowCredentialsParser.kt`
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/authentication/GetEntryBuilder.kt`
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/authentication/AllowCredentialsParserTest.kt`
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/authentication/GetEntryBuilderTest.kt`

### 公開 IF (design §4.3 / §4.4)

```kotlin
internal object AllowCredentialsParser {
    fun parseAllowCredentialIds(requestJson: String): List<String>
    fun parseRpId(requestJson: String): String
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class GetEntryBuilder(
    private val context: Context,
    private val repository: PasskeyRepository,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun build(option: BeginGetPublicKeyCredentialOption): List<PublicKeyCredentialEntry>
}
```

実装メモ:
- `AllowCredentialsParser`:
  - JSON parse は `kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }` (#99 既存依存)
  - `parseAllowCredentialIds`: `allowCredentials` 配列不在 / 空 / malformed / id 文字列でない要素は **空 list で defensive 扱い**
  - `parseRpId`: `rpId` 欠落は `IllegalArgumentException` (呼び出し側で skip)
- `GetEntryBuilder.build(option)`:
  - `runBlocking(Dispatchers.IO) { ... }` で Repository 呼出を同期化 (Service callback 内で呼ばれる前提)
  - `parseRpId` 失敗 → 空 list を返す (option を skip)
  - `parseAllowCredentialIds(requestJson)`:
    - 空 → `repository.listDiscoverableByRpId(rpId)`
    - 非空 → `ids.mapNotNull { repository.findByCredentialId(it) }.filter { it.rpId == rpId }` (rpId 一致防御)
  - 各 `PasskeyEntity` → `PublicKeyCredentialEntry`:
    - `accountName = entity.userDisplayName ?: entity.userName ?: entity.rpId`
    - `displayName = entity.rpDisplayName ?: entity.rpId`
    - `pendingIntent = PasskeyAuthActivity.pendingIntent(context, entity.credentialId)` (T-04 で追加する static factory)
    - `beginGetPublicKeyCredentialOption = option` (OS framework のために原 option を保持)
- `pendingIntent` の `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT` (#99 `CreateEntryBuilder` と同パターン)

### 受入基準

- **Req 1.1**: `allowCredentials = 空` で `listDiscoverableByRpId(rpId)` を 1 回呼ぶ + 返ってきた件数の `PublicKeyCredentialEntry` を返す
- **Req 1.2**: `allowCredentials = [id1, id2, id3]` で `findByCredentialId` を 3 回呼ぶ + 存在する件数だけ `PublicKeyCredentialEntry` を返す
- **Req 1.3**: 候補 0 件で空 list を返す
- **Req 1.6**: entity の `userDisplayName` / `userName` / `rpDisplayName` から accountName / displayName が組み立てられる (null fallback 正しく)
- **未解決事項 3**: malformed JSON / `allowCredentials` 不在 / 配列型不一致で `parseAllowCredentialIds` が空 list (defensive)
- **未解決事項 4**: accountName / displayName fallback 順序 (`userDisplayName ?> userName ?> rpId`, `rpDisplayName ?> rpId`)
- entity の rpId が parseRpId(option) と一致しない場合 entry に含めない (防御層)

### テスト

- `AllowCredentialsParserTest` (純 JVM):
  - `parseAllowCredentialIds_returnsIdsInOrder`
  - `parseAllowCredentialIds_emptyArray_returnsEmpty`
  - `parseAllowCredentialIds_missingField_returnsEmpty`
  - `parseAllowCredentialIds_malformedJson_returnsEmpty` (defensive)
  - `parseAllowCredentialIds_typeMismatch_returnsEmpty` (allowCredentials が文字列 / number の defensive)
  - `parseRpId_returnsRpIdString`
  - `parseRpId_throwsIllegalArgumentException_whenMissing`
- `GetEntryBuilderTest` (Robolectric, sdk 34):
  - `build_allowCredentialsEmpty_callsListDiscoverableByRpId_andReturnsEntries`
  - `build_allowCredentialsSpecified_callsFindByCredentialIdForEachId`
  - `build_returnsOnlyExistingCredentials` (id1, id3 が存在 / id2 が不在 → 2 件)
  - `build_filtersOutEntriesWithMismatchedRpId` (rpId 不一致 entity は除外)
  - `build_zeroCandidates_returnsEmptyList`
  - `build_entryAccountName_fallsBackThroughDisplayNameThenUserNameThenRpId`
  - `build_pendingIntentTargetsPasskeyAuthActivity_andCarriesCredentialIdInDataUri`
  - `build_parseRpIdFails_returnsEmptyList` (rpId 欠落 option を skip)

repository は mockk で差し替え。

### 完了条件

- `./gradlew :app:compileDebugKotlin` 成功
- `./gradlew :app:testDebugUnitTest --tests *AllowCredentialsParserTest*` 全 pass
- `./gradlew :app:testDebugUnitTest --tests *GetEntryBuilderTest*` 全 pass

### 依存タスク

- T-02 (`PasskeyRepository.listDiscoverableByRpId` 公開 API)

---

## T-04: PasskeyAuthActivity + Manifest 登録 + string resources

### 目的

`PublicKeyCredentialEntry.pendingIntent` から起動される Activity を追加する。BiometricPrompt + `PasskeyRepository.signWithIncrement` ブロック内で `loadPrivateKey` + `PasskeyAssertion.sign` + 平文 wipe + AuthenticationResponseJSON 組み立て + `PendingIntentHandler.setGetCredentialResponse` までを担う。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/authentication/PasskeyAuthActivity.kt`
- 変更: `app/src/main/AndroidManifest.xml` (`<activity>` 1 件追加。`<service>` / `PasskeyCreateActivity` 不変)
- 変更: `app/src/main/res/values/strings.xml` (新規 string resources)
- 変更: `app/src/main/res/values-ja/strings.xml` (日本語 / 両 locale で「PassKey」表記)

### 公開 IF (design §4.6 / §4.7 / §9.3)

```kotlin
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyAuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?)

    companion object {
        internal const val INTENT_DATA_SCHEME = "keynest"
        internal const val INTENT_DATA_AUTHORITY = "passkey"
        internal const val INTENT_DATA_PATH_PREFIX = "/auth/"

        @VisibleForTesting internal fun intent(context: Context, credentialId: String): Intent
        @VisibleForTesting internal fun pendingIntent(context: Context, credentialId: String): PendingIntent
    }
}
```

新規 string resources (具体文言は Developer が実装時に確定 / 「PassKey」表記必須):
- `passkey_auth_prompt_title` — 例: "PassKey で認証"
- `passkey_auth_prompt_subtitle` — 例: "本人確認をしてください"

AndroidManifest 追加 (design §2.1 / §4.7 / NFR 3.3、#99 `PasskeyCreateActivity` と同設定):

```xml
<!--
    Issue #100 / Parent #89:
    PassKey 認証セレモニーで PublicKeyCredentialEntry の pending intent から
    起動される Activity。OS Credential Manager から呼ばれる前提のため
    exported=false。recents から除外し、透過テーマで既存 PasskeyCreateActivity と
    同等の UX を取る。@RequiresApi(34) なクラスのため tools:targetApi も付与。
-->
<activity
    android:name=".credentialprovider.authentication.PasskeyAuthActivity"
    android:exported="false"
    android:excludeFromRecents="true"
    android:taskAffinity=""
    android:theme="@style/Theme.KeyNest.Translucent"
    tools:targetApi="34" />
```

実装メモ (design §5.2 / §9.3 / 本 Issue では確認画面なし):
- `onCreate` で `ServiceLocator.initialize(applicationContext)` defensively
- `PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)` で OS 要求取得 / null なら `setGetCredentialException(GetCredentialUnknownException) + setResult(RESULT_OK) + finish()`
- Intent data Uri (`keynest://passkey/auth/<credentialId>`) から credentialId を抽出
- `ProviderGetCredentialRequest.credentialOptions[0]` を `GetPublicKeyCredentialOption` にキャスト (公開クラス `androidx.credentials.GetPublicKeyCredentialOption`)
- `clientDataJson` は `option.requestJson` から抽出 (RP に返却するため文字列で保持) / 署名用 `clientDataHash` は `option.clientDataHash` が non-null ならそれを使い、null なら `SHA-256(option.requestJson)` で fallback (本 Issue では既存 `PasskeyAssertion` が clientDataJson を SHA-256 する経路に集約 — clientDataHash 引数は将来拡張で対応)
- BiometricPrompt: `BiometricAuthenticator(this as FragmentActivity).authenticate(getString(R.string.passkey_auth_prompt_title), getString(R.string.passkey_auth_prompt_subtitle))`
- `runAuthenticationFlow` skeleton (design §9.3):
  - `lifecycleScope.launch { try { ... } catch (e: GetCredentialException) { ... } catch (t: Throwable) { ... } finally { wipeQueue.forEach { fill(0) }; finish() } }`
  - 成功時: `ServiceLocator.passkeyRepository.signWithIncrement(credentialId) { newSignCount -> loadPrivateKey → PasskeyAssertion.sign → wipeQueue.add(plaintext) → AuthenticationResponseJSON 組み立て → return responseJson }`
  - `PendingIntentHandler.setGetCredentialResponse(resultIntent, GetCredentialResponse(PublicKeyCredential(responseJson)))`
- AuthenticationResponseJSON 組み立ては `PasskeyAuthActivity` 内 private function `buildAuthenticationResponseJson(...)` で手書き `StringBuilder` (依存追加なし / #99 `PasskeyCreator.buildRegistrationResponseJson` と同パターン / design §7.3)
- base64url encode: `Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)`

### 受入基準

- **Req 2.1**: pending intent から起動された Activity が `BiometricAuthenticator.authenticate` を `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` 構成で呼ぶ
- **Req 2.2**: BiometricPrompt 未設定端末で Device Credential フォールバック (既存 `BiometricAuthenticator` で達成)
- **Req 2.3**: `Cancelled` → `GetCredentialCancellationException` / `Failed / Unavailable` → `GetCredentialUnknownException`
- **Req 3.7**: AuthenticationResponseJSON が `id` / `rawId` / `type=public-key` / `response.{clientDataJSON, authenticatorData, signature, userHandle}` / `clientExtensionResults={}` を含む
- **NFR 1.1**: 平文 PKCS#8 を `finally` で `fill(0)` wipe (Activity onDestroy / 例外経路でも実行)
- **Manifest**: `<activity>` 1 件追加、`<service>` / `PasskeyCreateActivity` ブロック不変
- **NFR 6.1**: 新規 string resources の両 locale テキストに「PassKey」表記を含み、「passkey」「パスキー」表記を含まない
- **#90 既存 manifest test 非破壊**: `CredentialProviderServiceManifestTest` / `CredentialProviderXmlTest` 引き続き pass (Req 4.8)

### テスト

本タスクの主要ロジックは T-05 (`PasskeyAuthActivityTest`) で集約検証する。T-04 で追加するのは:
- `<activity>` 追加後も `CredentialProviderServiceManifestTest` (#90) が pass であることを既存 test 実行で確認 (新規テスト追加なし)
- `PasskeyAuthActivity.intent(context, credentialId)` factory の data Uri が `keynest://passkey/auth/<credentialId>` であることは、T-03 `GetEntryBuilderTest.build_pendingIntentTargetsPasskeyAuthActivity_andCarriesCredentialIdInDataUri` で間接的に検証されるので追加不要

### 完了条件

- `./gradlew :app:compileDebugKotlin` / `:app:assembleDebug` 成功
- 既存 `CredentialProviderServiceManifestTest` / `CredentialProviderXmlTest` 引き続き pass (本 Issue 追加コード由来の lint error なし)

### 依存タスク

- T-01 (`PasskeyAssertion`)
- T-02 (`PasskeyRepository.loadPrivateKey` / `signWithIncrement`)

---

## T-05: KeyNestCredentialProviderService.onBeginGetCredentialRequest 差し替え + ServiceLocator 配線 + Service test 拡張

### 目的

#90 / #99 の空応答スタブ (`onBeginGetCredentialRequest`) を本実装に置き換える。`allowCredentials` の有無で `findByCredentialId` / `listDiscoverableByRpId` を分岐し、`PublicKeyCredentialEntry` を OS Credential Manager UI に提示する経路を完成させる。

### 変更ファイル

- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/KeyNestCredentialProviderService.kt`
  - `onBeginGetCredentialRequest` 本体を差し替え (design §4.1)
  - `onBeginCreateCredentialRequest` (#99) / `onClearCredentialStateRequest` (#90 stub) は **touch しない**
- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/di/ServiceLocator.kt`
  - `getEntryBuilder` lazy singleton 追加
- 変更: `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/KeyNestCredentialProviderServiceTest.kt` (#99 既存テスト拡張)

### 公開 IF (design §4.1 / §4.8)

Service 本体の差し替え (design §4.1):
- `request.beginGetCredentialOptions.filterIsInstance<BeginGetPublicKeyCredentialOption>()` で publicKey option を抽出
- publicKey option が 0 件 → `callback.onResult(BeginGetCredentialResponse.Builder().build())` (空応答 / req 1.5)
- 各 publicKey option について `ServiceLocator.getEntryBuilder.build(option)` を呼んで `List<PublicKeyCredentialEntry>` を取得 (実体は T-03 で実装済)
- 全 entries を `BeginGetCredentialResponse.Builder` に積み (空でも `Builder().build()` で req 1.3 を満たす)
- `callback.onResult(response)` で返却

ServiceLocator 追記:
```kotlin
@get:RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@delegate:SuppressLint("NewApi")
internal val getEntryBuilder: GetEntryBuilder by lazy {
    GetEntryBuilder(requireAppContext(), passkeyRepository)
}
```

### 受入基準

- **Req 1.1 / R4.3 (a)**: `allowCredentials = 空` の publicKey option で `outcome.onResult(BeginGetCredentialResponse)` が **discoverable な PassKey 数** に一致する `PublicKeyCredentialEntry` を返す (Repository / GetEntryBuilder を mock してアサート)
- **Req 1.2 / R4.3 (b)**: `allowCredentials = [id1, id2, id3]` (うち id1 / id3 が存在) で `outcome.onResult` が **2 件** の entry を返す
- **Req 1.3 / R4.3 (c)**: 候補 0 件で `outcome.onResult` に **空の `BeginGetCredentialResponse`** が渡される (`response.credentialEntries.isEmpty()`)
- **Req 1.5**: PublicKey 以外の option (password など) のみ → 空応答 / `GetEntryBuilder.build` を呼ばない
- **Req 4.6**: #99 既存 `onBeginCreateCredentialRequest` テスト (residentKey 3 値 / excludeCredentials hit/miss / password 空応答) が引き続き pass
- **Req 4.8**: `CredentialProviderServiceManifestTest` / `CredentialProviderXmlTest` が引き続き pass (`<service>` 不変)
- **NFR 5.1**: Service callback 内で重い処理 (生体認証 / 復号 / 署名) を呼ばない (これらは Activity に委譲済)

### テスト

`KeyNestCredentialProviderServiceTest` (拡張):
- **既存ケース全件維持** (削除禁止):
  - `onBeginCreateCredentialRequest_publicKeyResidentKeyRequired_returnsCreateEntry` 他 #99 ケース
  - 既存 `onBeginGetCredentialRequest_stillReturnsEmptyResponse` (空応答ケース) は **削除せず** 本 Issue では「PublicKey option 不在で空応答」相当のケースに rename + 維持 (`onBeginGetCredentialRequest_noPublicKeyOption_returnsEmptyResponse`)
  - `onClearCredentialStateRequest_stillReturnsNull` (#90 既存維持)
- **新規ケース**:
  - `onBeginGetCredentialRequest_publicKeyAllowCredentialsEmpty_returnsDiscoverableEntries` (GetEntryBuilder mock が 3 件返す → response に 3 件)
  - `onBeginGetCredentialRequest_publicKeyAllowCredentialsSpecified_returnsExistingMatches` (GetEntryBuilder mock が 2 件返す → response に 2 件)
  - `onBeginGetCredentialRequest_publicKeyZeroCandidates_returnsEmptyResponse` (GetEntryBuilder mock が空 list → response.credentialEntries 空)
  - `onBeginGetCredentialRequest_passwordOptionOnly_returnsEmptyResponse_andDoesNotCallGetEntryBuilder`
  - `onBeginGetCredentialRequest_multiplePublicKeyOptions_aggregatesEntries` (option × 2 → GetEntryBuilder が 2 回呼ばれ entries が合算される / 任意ケース)

`GetEntryBuilder` / `ServiceLocator` は mockk で差し替え (#99 既存パターンと同様 `mockkObject(ServiceLocator)` で stub)。

### 完了条件

- `./gradlew :app:compileDebugKotlin` / `:app:assembleDebug` 成功
- `./gradlew :app:testDebugUnitTest --tests *KeyNestCredentialProviderServiceTest*` 全 pass (旧 + 新ケース)
- `./gradlew :app:lintDebug` warnings ベースライン内

### 依存タスク

- T-03 (`GetEntryBuilder`)
- T-04 (`PasskeyAuthActivity` — pending intent target が存在しないと `GetEntryBuilder` が compile しない可能性)

---

## T-06: PasskeyAuthActivityTest (signCount Option A end-to-end)

### 目的

`PasskeyAuthActivity.runAuthenticationFlow` の主要分岐 (BiometricPrompt 成功 / cancel / failed / 署名失敗 / 復号失敗) と Option A の rollback / commit を Robolectric で end-to-end カバーする。

### 変更ファイル

- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/authentication/PasskeyAuthActivityTest.kt`

### 受入基準

- **Req 2.x / R4.4 / R4.5**: 以下 6 シナリオが全て期待動作:
  - (a) `AuthResult.Succeeded` → `signWithIncrement` 内で `loadPrivateKey` + `PasskeyAssertion.sign` + AuthenticationResponseJSON 組み立て + `PendingIntentHandler.setGetCredentialResponse` 呼出 + DB signCount +1
  - (b) `AuthResult.Cancelled` → `signWithIncrement` 未呼出 / DB 不変 / `setGetCredentialException(GetCredentialCancellationException)`
  - (c) `AuthResult.Failed` → 同上で `GetCredentialUnknownException`
  - (d) `AuthResult.Unavailable` → 同上で `GetCredentialUnknownException`
  - (e) `PasskeyAssertion.sign` 注入失敗 (mockk で `PasskeyAssertionException.SignFailed` を throw) → DB の signCount が **元値に戻る** (rollback) + `setGetCredentialException(GetCredentialUnknownException)`
  - (f) `loadPrivateKey` 注入失敗 (mockk で `javax.crypto.AEADBadTagException` を throw) → 同上で rollback + `GetCredentialUnknownException`
- **R4.4**: 同じ credentialId で `Succeeded` を 2 回完了させると `dao.incrementSignCount` が 2 回呼ばれ DB signCount が **+2**
- **NFR 1.1**: 復号後の平文 PKCS#8 が `finally` で `fill(0)` される (mock で渡す平文 byte 配列を inspect / `0x00` 埋め確認)

### テスト

`PasskeyAuthActivityTest` (Robolectric, sdk 34, `@RunWith(AndroidJUnit4::class)` + `@Config(sdk = [34])`):
- `runAuthenticationFlow_biometricSucceeded_completesAssertion_andCommitsSignCount`
- `runAuthenticationFlow_biometricCancelled_returnsCancellationException_andLeavesSignCountUnchanged`
- `runAuthenticationFlow_biometricFailed_returnsUnknownException`
- `runAuthenticationFlow_biometricUnavailable_returnsUnknownException`
- `runAuthenticationFlow_signFailure_rollsBackSignCount_andReturnsUnknownException`
- `runAuthenticationFlow_decryptFailure_rollsBackSignCount_andReturnsUnknownException`
- `runAuthenticationFlow_calledTwice_incrementsSignCountByTwo`
- `runAuthenticationFlow_wipesPlaintextPrivateKey_evenOnFailure`

Robolectric セットアップ:
- `ActivityScenario.launch(PasskeyAuthActivity::class.java)` でなく、`Robolectric.buildActivity(...)` + Intent (`PasskeyAuthActivity.intent(context, credentialId)` で構成) を使う
- `ServiceLocator` は `mockkObject` で stub。`ServiceLocator.passkeyRepository` を mockk で差し替え
- `BiometricAuthenticator` は mock 不可能なため、テスト用 seam として `PasskeyAuthActivity` に `@VisibleForTesting internal var biometricAuthenticatorFactory: (FragmentActivity) -> BiometricAuthenticator = { BiometricAuthenticator(it) }` を仕込むか、`runAuthenticationFlow` を `@VisibleForTesting internal` 公開して `AuthResult` を直接 inject する方式を採用する (design §10.1 で「複雑な BiometricPrompt のフルセットアップを避けるため、内部 helper 経由で AuthResult を inject」を推奨)
- `PasskeyAssertion` は object なので `mockkObject(PasskeyAssertion)` で差し替え (`every { PasskeyAssertion.sign(any()) } returns ...`)

### 完了条件

- `./gradlew :app:testDebugUnitTest --tests *PasskeyAuthActivityTest*` 全 pass
- `./gradlew :app:compileDebugKotlin` / `:app:assembleDebug` 成功

### 依存タスク

- T-01 (`PasskeyAssertion`)
- T-02 (`PasskeyRepository.signWithIncrement`)
- T-04 (`PasskeyAuthActivity`)

---

## T-07: 統合確認 (全テスト pass + 既存テスト非破壊 + Instrumentation placeholder + PR 確認事項転記)

### 目的

T-01 〜 T-06 完了後、全テスト pass / build 成功 / 既存テスト非破壊 / Instrumentation test placeholder 配置 / PR description への確認事項転記を行う。

### 変更ファイル

- 新規: `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/credentialprovider/authentication/PasskeyAuthActivityInstrumentationTest.kt`
  - `@SdkSuppress(minSdkVersion = 34)` + `@Ignore("API 34 emulator が CI に揃うまで手動実行 — #94 完了後に @Ignore 解除")` の placeholder
  - 構成: `ActivityScenario.launch(PasskeyAuthActivity::class.java)` で起動、BiometricPrompt を実機で succeed させて `GetCredentialResponse` が return されるところまでを assert する skeleton (将来の実装) を `@Ignore` 付きでコミット
- (確認のみ) ファイル変更なし: 全 unit test の pass、既存テスト非破壊

### 受入基準

- **Req 4.x 全件**: requirements R4.1 / R4.2 / R4.3 / R4.4 / R4.5 / R4.6 / R4.7 / R4.8 が全件カバー
- **既存テスト非破壊** (NFR 2):
  - `KeyNestAutofillService` 関連テスト全 pass
  - #90 `CredentialProviderServiceManifestTest` / `CredentialProviderXmlTest` 全 pass
  - #91 `Migration_4_5_Test` / `PasskeyDaoTest` 全 pass
  - #99 `PasskeyCreatorTest` / `AuthenticatorDataBuilderTest` / `AttestationObjectBuilderTest` / `CoseKeyEncoderTest` / `CborWriterTest` / `KeynestAaguidTest` / `CreateEntryBuilderTest` / `KeyNestCredentialProviderServiceTest` (`onBeginCreateCredentialRequest` 既存ケース) 全 pass
  - `PasskeyRepositoryTest` 既存 9 ケース全 pass (T-02 で constructor 拡張に追従)
  - `InternetPermissionAbsenceTest` 全 pass (本 Issue で `<uses-permission android:name="android.permission.INTERNET">` を追加していないこと)
  - `OnBackInvokedCallbackEnabledTest` 全 pass
- **CI 緑保持**: `./gradlew :app:testDebugUnitTest` で全 unit test pass
- **Lint 緑**: `./gradlew :app:lintDebug` で本 Issue 追加コードに新規 error 無し

### 手動検証 (実機 / API 34 emulator, PR description に結果記録)

1. KeyNest をビルド → API 34+ 実機にインストール
2. OS の「設定 → パスワードとパスキー → 既定 → KeyNest」を有効化
3. 任意の 3rd-party Web ブラウザで PassKey 認証対応サイト (例: `https://webauthn.io/`) を開き、事前に #99 経由で PassKey を登録しておく
4. 「PassKey でログイン」を実行 (`navigator.credentials.get()`)
5. OS シートに「KeyNest」候補が表示されることを確認
6. KeyNest を選択 → BiometricPrompt → 指紋 or PIN で認証
7. ログイン完了が RP 側で reflect されることを確認
8. (`allowCredentials` 経路) `webauthn.io` で `allowCredentials` を指定した認証フローを試す → 該当 credentialId が KeyNest にあれば候補表示
9. (`signCount` Option A) 同じ PassKey で 2 回ログイン → SQLite `passkeys` table の `signCount` が **+2** されていることを `adb shell run-as` で確認 (debug build に確認用 menu を一時追加してもよい)
10. (rollback 確認) 任意の方法で署名失敗を inject (`PasskeyAssertion.sign` に例外を inject する debug build menu 等) → `signCount` が元値で維持されること確認

### PR description に転記する確認事項 (design §12.5)

1. **`signWithIncrement` 高階関数 API の採用**: requirements 未解決事項 2 の案 C を本 design で確定したが、`PasskeyRepository` 公開 IF に suspend 高階関数を追加する形が後続 Issue (一覧 / 個別管理 UI) から見て扱いやすいか、human reviewer に確認してもらう。
2. **`PasskeyRepositoryImpl` constructor 拡張**: `database: KeyNestDatabase` を constructor 引数に追加する変更が、#99 既存 `PasskeyRepositoryTest` のセットアップに 1 行追加するだけで pass 復旧する想定の正当性を確認してもらう。
3. **clientDataJSON / clientDataHash 取得経路**: `GetPublicKeyCredentialOption.clientDataHash` が常に non-null で渡るか、`requestJson` 内の clientDataJSON 文字列を fallback で SHA-256 する必要があるかを、実装フェーズ最初の動作確認で確定する (design §7.4 / requirements 未解決事項 7)。
4. **`AuthenticatorDataBuilder` の再利用**: assertion 側で AT ビットを抜くために `flags = 0x05` / `attestedCredentialData = null` を呼び出し側が組み立てる契約が、#99 既存テスト `build_withoutAttestedCredentialData_omitsItAndAtFlag` で十分カバーされていることを確認 (本 Issue では同 helper の追加テストは不要)。
5. **`androidx.credentials` 1.3.0 維持**: 1.3.0 で十分か、1.5.0 への bump が必要か、実装中に判明した場合は本 PR 内で `libs.versions.toml` を更新してよいか。

### 完了条件 (Definition of Done)

- 全 T-01〜T-06 の受入基準が満たされている
- `./gradlew :app:testDebugUnitTest` で **全 unit test pass** (新規 + 既存)
- `./gradlew :app:assembleDebug` 成功
- `./gradlew :app:lintDebug` warnings ベースライン内 (新規 error 無し)
- `app/src/androidTest/` に `PasskeyAuthActivityInstrumentationTest` を `@Ignore` 付きで配置
- 手動検証 1〜10 を PR description に結果記録
- PR description の「確認事項」セクションに上記 1〜5 を転記

### 依存タスク

- T-01 〜 T-06 全完了

---

## タスク → 要件 / 変更ファイル サマリ表

| Task | 主要対応要件 | 主要変更ファイル | 依存 |
|------|------------|----------------|------|
| T-01 | R3.2 / R3.5 / R3.6 / R4.1 / R4.2 / 決定 1〜2 | `PasskeyAssertion.kt` / `PasskeyAssertionTypes.kt` / `PasskeyAssertionTest.kt` | なし |
| T-02 | R3.1 / R3.3 / R3.4 / R4.4 / R4.5 / NFR 1.1 / 決定 3 / 未解決事項 1〜2 | `PasskeyRepository.kt` / `PasskeyRepositoryImpl.kt` / `ServiceLocator.kt` / `PasskeyRepositoryTest.kt` | なし |
| T-03 | R1.1 / R1.2 / R1.3 / R1.6 / 未解決事項 3〜4 | `AllowCredentialsParser.kt` / `GetEntryBuilder.kt` / 各 Test | T-02 |
| T-04 | R2.1 / R2.2 / R2.3 / R3.7 / NFR 1.1 / NFR 3.x / NFR 6.x | `PasskeyAuthActivity.kt` / `AndroidManifest.xml` / `strings.xml` / `strings.xml (ja)` | T-01, T-02 |
| T-05 | R1.x / R1.5 / R4.3 / R4.6 / R4.8 / NFR 5.1 | `KeyNestCredentialProviderService.kt` / `ServiceLocator.kt` / `KeyNestCredentialProviderServiceTest.kt` | T-03, T-04 |
| T-06 | R2.x / R3.3 / R3.4 / R4.4 / R4.5 / NFR 1.1 | `PasskeyAuthActivityTest.kt` | T-01, T-02, T-04 |
| T-07 | R4.7 / R4.8 / NFR 2.x 全件 | `PasskeyAuthActivityInstrumentationTest.kt` (@Ignore) | T-01〜T-06 |
