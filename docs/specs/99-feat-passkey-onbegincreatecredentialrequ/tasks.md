# Task Breakdown — Issue #99 / feat(passkey): 登録セレモニー (onBeginCreateCredentialRequest) 実装

> 関連: `requirements.md`, `design.md` (本ディレクトリ)
>
> 各タスクは独立コミット可能な粒度で、依存順に並べている。Developer はこの順番で実装する。
>
> 略号:
> - **Req**: `requirements.md` の Requirement / Acceptance Criteria 番号
> - **NFR**: `requirements.md` の Non-Functional Requirement 番号
> - 設計の詳細は `design.md` の対応セクション (`§N.M`) を参照

## 概要 / 前提

### 着手前に Developer が読むべき資料

1. `docs/specs/99-feat-passkey-onbegincreatecredentialrequ/requirements.md` (本 Issue PM 成果物)
2. `docs/specs/99-feat-passkey-onbegincreatecredentialrequ/design.md` (本ファイルと同ディレクトリ)
3. 依存 #90 (merged): `docs/specs/90-feat-passkey-credentialproviderservice-m/design.md` §4 / §8 (本 Issue が差し替える Service の現状)
4. 依存 #91 + #107: `docs/specs/91-feat-passkey-room-migration-passkeyentit/design.md` §6 / §7 (本 Issue が呼び出す `PasskeyRepository` の公開 IF)
5. 既存コード:
   - `app/src/main/java/.../credentialprovider/KeyNestCredentialProviderService.kt` (#90)
   - `app/src/main/java/.../auth/BiometricAuthenticator.kt`
   - `app/src/main/java/.../security/{AesGcmCipher,KeystoreKeyProvider,EncryptedBlob}.kt`
   - `app/src/main/java/.../data/entity/PasskeyEntity.kt`
   - `app/src/main/AndroidManifest.xml` (#90 で `<service>` 追加済 / 本 Issue は `<activity>` 追加)
   - `app/src/main/java/.../di/ServiceLocator.kt`

### 前提依存

- #91 (T-01〜T-04) が merge 済 (PasskeyEntity / PasskeyDao / Migration_4_5 / KeyNestDatabase v5)
- #107 (T-05〜T-11) が merge 済 (PasskeyRepository interface / impl / ServiceLocator wiring / tests)
- 本 Issue 着手時に #107 がまだ未 merge の場合、Developer はまず #107 の merge 完了を確認すること

### コミット運用

- T-01〜T-09 は **1 タスク = 1 コミット**を基本とする (T-02 のように関連テストを同コミットに含めてよい)
- T-10 は確認のみ、ファイル変更なし (コミット無し)
- 各コミットの後で `./gradlew :app:compileDebugKotlin` 成功を確認してから次タスクへ進む

---

## T-01: KeynestAaguid 定数の追加 + テスト

### 目的

AAGUID `2a56cf86-8332-4829-9f2a-e9a4adbc7abe` を本 Issue で初出の定数として 1 箇所に集約する。bytewise の正しさ (16 byte / big-endian / UUID hex 順) を unit test で保証し、後続クラス (`AuthenticatorDataBuilder` / `PasskeyCreator`) が defensive copy 経由で安全に参照できるようにする。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/KeynestAaguid.kt`
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/KeynestAaguidTest.kt`

### 公開 IF (design §3.3)

```kotlin
internal object KeynestAaguid {
    const val UUID_STRING: String = "2a56cf86-8332-4829-9f2a-e9a4adbc7abe"
    fun bytes(): ByteArray   // 16 byte big-endian の defensive copy
}
```

### 受入基準

- **Req 2.1 / 6.2 (a)**: `bytes()` が `0x2A, 0x56, 0xCF, 0x86, 0x83, 0x32, 0x48, 0x29, 0x9F, 0x2A, 0xE9, 0xA4, 0xAD, 0xBC, 0x7A, 0xBE` の 16 byte と完全一致
- `bytes()` の 2 回呼び出しが **異なる instance** (`!== `) を返す (defensive copy)
- `bytes()` の戻り値を mutator が変更しても次回呼び出しに影響しない (`fill(0)` 検証)
- `UUID_STRING` が `KEYNEST_AAGUID` 確定値 (`requirements.md` §決定事項) と一致

### テスト

- `KeynestAaguidTest`:
  - `bytes_returnsExpectedBigEndian16Bytes` — 完全一致
  - `bytes_returnsDefensiveCopy_perCall` — instance 別 + mutate 後 invariant
  - `uuidString_matchesKeynestAaguidConstant`

### 完了条件

- `./gradlew :app:compileDebugKotlin` 成功
- `./gradlew :app:testDebugUnitTest --tests *KeynestAaguidTest*` 3 ケース pass

### 依存タスク

- なし (先頭)

---

## T-02: CborWriter (minimal CBOR encoder) + テスト

### 目的

本 Issue で必要な CBOR primitive (uint / nint / byte string / text string / map header) のみを encode する minimal writer を追加する。外部 CBOR ライブラリ依存は追加しない (design §4.5 / §7.5)。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/CborWriter.kt`
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/CborWriterTest.kt`

### 公開 IF (design §4.5)

```kotlin
internal class CborWriter {
    fun writeUnsignedInt(value: Long): CborWriter
    fun writeNegativeInt(value: Long): CborWriter
    fun writeByteString(bytes: ByteArray): CborWriter
    fun writeTextString(s: String): CborWriter
    fun writeArrayHeader(count: Int): CborWriter
    fun writeMapHeader(entryCount: Int): CborWriter
    fun toByteArray(): ByteArray
}
```

実装メモ:
- RFC 8949 §3 の length encoding に従う (0..23 = 1 byte, 24..255 = 2 byte (`0x18` prefix), 256..65535 = 3 byte (`0x19` prefix), 65536..2^32-1 = 5 byte (`0x1A` prefix), 8 byte (`0x1B` prefix))
- `writeNegativeInt(value)` の引数 `value` は **負の整数** を受け取り、内部で `-1L - value` を unsigned encoding に変換する仕様にする (RFC 8949 major type 1)
- text string の UTF-8 encoding は `s.toByteArray(Charsets.UTF_8)` を使い、length は byte 長 (文字数ではない)

### 受入基準

- **design §4.5 / §7.4 / §7.3 で要求される CBOR primitive を全て encode 可能**
- RFC 8949 のテストベクタ (Appendix A) のうち本 Issue で使う範囲 (uint 0..23 / 24..255 / 256..65535、nint -1..-24 / -25..-256、byte string / text string / map(0) / map(1..5)) を bytewise 一致

### テスト (RFC 8949 テストベクタ複写)

- `CborWriterTest`:
  - `writeUnsignedInt_smallValues` (0 → `0x00`, 23 → `0x17`, 24 → `0x18 0x18`, 255 → `0x18 0xFF`, 256 → `0x19 0x01 0x00`)
  - `writeNegativeInt_smallValues` (-1 → `0x20`, -7 → `0x26`, -24 → `0x37`, -25 → `0x38 0x18`)
  - `writeByteString_empty_and_small_and_257` (空 → `0x40`, 1 byte → `0x41 0x..`, 257 byte → `0x59 0x01 0x01 ...`)
  - `writeTextString_utf8_includingNonAscii` (例: "none" → `0x64 0x6E 0x6F 0x6E 0x65`)
  - `writeMapHeader_emptyAndFiveEntries` (`{}` → `0xA0`, 5 entries → `0xA5`)
  - `chainedWrites_producesContiguousByteArray`
  - `negativeInt_throwsForPositiveArgument` (defensive — `writeNegativeInt(0)` で例外)

### 完了条件

- `./gradlew :app:testDebugUnitTest --tests *CborWriterTest*` 全 pass

### 依存タスク

- なし (T-01 と並列可だが、本 tasks では依存順に並べる)

---

## T-03: CoseKeyEncoder + テスト

### 目的

ES256 (P-256) public key を WebAuthn / RFC 8152 §13.1 の COSE_Key (alg = -7) として CBOR encode するヘルパを追加する。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/CoseKeyEncoder.kt`
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/CoseKeyEncoderTest.kt`

### 公開 IF (design §4.4)

```kotlin
internal object CoseKeyEncoder {
    /**
     * RFC 8152 §13.1 / WebAuthn §6.5.1.1 に従う COSE_Key (kty=2, alg=-7, crv=1, x, y)。
     * map ordering は (1, 3, -1, -2, -3) の WebAuthn 慣例順。
     */
    fun encodeEs256(publicKey: ECPublicKey): ByteArray
}
```

実装メモ:
- `publicKey.w` から `x` / `y` の `BigInteger` を取得
- `BigInteger.toByteArray()` の符号 byte (MSB sign) を剥がしつつ 32 byte に左 0 padding する helper (internal `toUnsignedFixedLength(length: Int)`) を同ファイル内に持つ
- map ordering は alphabetical / canonical ではなく **(1, 3, -1, -2, -3)** の固定順 (§4.4 / §7.4)
- CborWriter (T-02) を使って組み立てる

### 受入基準

- **Req 6.1 (b)**: 既知の P-256 keypair (固定 x, y) で COSE_Key 出力が `alg = -7` / `kty = 2` / `crv = 1` / x 32 byte / y 32 byte を含む CBOR map になる
- `x` / `y` の左 0 padding が 32 byte ちょうど (`BigInteger` 由来の sign byte が混入しない)

### テスト

- `CoseKeyEncoderTest`:
  - `encodeEs256_bytewise_matchesRfc8152TestVector` — RFC 8152 §C.7.1 の P-256 test vector (固定 x, y) を `KeyFactory.getInstance("EC")` で復元して encode、結果が bytewise 一致
  - `encodeEs256_paddsX_andY_to_32Bytes_evenWhenMsbZero` — MSB が 0 になる x / y (BigInteger 短縮) で左 0 padding が効くこと
  - `encodeEs256_throwsForNonP256Curve` (defensive)

### 完了条件

- `./gradlew :app:testDebugUnitTest --tests *CoseKeyEncoderTest*` 全 pass

### 依存タスク

- T-02 (`CborWriter`)

---

## T-04: AuthenticatorDataBuilder + テスト

### 目的

WebAuthn Level 2 §6.1 の authenticatorData を組み立てる pure-Kotlin ヘルパを追加する。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/AuthenticatorDataBuilder.kt`
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/AuthenticatorDataBuilderTest.kt`

### 公開 IF (design §4.3)

```kotlin
internal object AuthenticatorDataBuilder {
    fun build(
        rpIdHash: ByteArray,
        flags: Byte,
        signCount: Int,
        attestedCredentialData: ByteArray?,
        extensions: ByteArray? = null,
    ): ByteArray
    fun rpIdHash(rpId: String): ByteArray
    fun attestedCredentialData(aaguid: ByteArray, credentialId: ByteArray, publicKeyCose: ByteArray): ByteArray

    const val FLAG_UP: Byte = 0x01
    const val FLAG_UV: Byte = 0x04
    const val FLAG_BE: Byte = 0x08
    const val FLAG_BS: Byte = 0x10
    const val FLAG_AT: Byte = 0x40
    const val FLAG_ED: Byte = -0x80
}
```

### 受入基準

- **Req 6.2 (a)**: AAGUID が 16 byte で `KeynestAaguid.bytes()` と完全一致
- **Req 6.2 (b)**: `signCount` 4 byte big-endian `0x00 0x00 0x00 0x00`
- **Req 6.2 (c)**: `flags` バイト = `0x45` (UP | UV | AT)
- **Req 6.2 (d)**: `rpIdHash(rpId)` が `MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray(UTF_8))` と一致
- **Req 6.2 (e)**: `credentialIdLength` 2 byte big-endian = credentialId 長
- `build(...)` の戻り値 byte レイアウトが design §7.1 表のとおり

### テスト

- `AuthenticatorDataBuilderTest`:
  - `rpIdHash_matchesSha256OfUtf8RpId` (例: rpId = "example.com")
  - `build_layout_offsetsMatchSpec` — 0..31 = rpIdHash / 32 = flags / 33..36 = signCount / 37.. = attestedCredentialData
  - `build_flags0x45_forRegistration` (UP | UV | AT)
  - `build_signCount_4BytesBigEndian_initiallyZero`
  - `attestedCredentialData_layout_aaguidThenLenThenIdThenCose` (固定 credentialId 32 byte / 固定 cose 77 byte 程度で bytewise)
  - `attestedCredentialData_credentialIdLength_isBigEndian2Bytes` (32 byte → `0x00 0x20`)
  - `build_withoutAttestedCredentialData_omitsItAndAtFlag` (認証セレモニー Issue の preflight)
  - `build_extensions_null_omitsTrailingBytes`

### 完了条件

- `./gradlew :app:testDebugUnitTest --tests *AuthenticatorDataBuilderTest*` 全 pass

### 依存タスク

- T-01 (`KeynestAaguid`) — bytewise 一致テストで使用

---

## T-05: AttestationObjectBuilder + テスト

### 目的

`fmt = "none"` の WebAuthn attestation object (CBOR map 3 entries) を組み立てる。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/AttestationObjectBuilder.kt`
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/AttestationObjectBuilderTest.kt`

### 公開 IF (design §4.6)

```kotlin
internal object AttestationObjectBuilder {
    fun buildFormatNone(authenticatorData: ByteArray): ByteArray
}
```

map ordering は **"fmt" → "attStmt" → "authData"** の WebAuthn 仕様順 (design §4.6 / §7.3)。

### 受入基準

- **Req 6.2 補強**: 出力 CBOR map のキーが `"fmt"` / `"attStmt"` / `"authData"` の 3 件
- `"fmt"` 値 = `"none"`
- `"attStmt"` 値 = 空 map (`{}`)
- `"authData"` 値 = 引数の byte string そのまま
- bytewise: `0xA3 0x63 0x66 0x6D 0x74 0x64 0x6E 0x6F 0x6E 0x65 0x67 ...` で始まる固定 prefix

### テスト

- `AttestationObjectBuilderTest`:
  - `buildFormatNone_bytewise_matchesWebAuthnExample` — design §10.5 で出典明示の WebAuthn Level 2 §6.5.5.1 sample bytewise 比較 (authData は固定 50 byte の dummy)
  - `buildFormatNone_authDataIsCborByteString` — `0x58` (bytes header 1 byte length) または `0x59` (2 byte length) で encode
  - `buildFormatNone_mapHeaderIs0xA3` (3 entries map)
  - `buildFormatNone_keysAreInWebAuthnOrder_notAlphabetical` (fmt が先頭にあること)

### 完了条件

- `./gradlew :app:testDebugUnitTest --tests *AttestationObjectBuilderTest*` 全 pass

### 依存タスク

- T-02 (`CborWriter`)

---

## T-06: PasskeyCreator (keypair 生成 + StrongBox フォールバック + 暗号化 + response 組み立て) + テスト

### 目的

登録セレモニーの中核。ES256 (P-256) keypair の生成 (JCE 標準 provider 経由) / wrapping key の AndroidKeyStore 生成 (StrongBox 二段フォールバック) / COSE encode / authenticatorData 組み立て / attestationObject CBOR encode / `SavePasskeyRequest` 組み立てまでを集約する。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/PasskeyCreator.kt`
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/PasskeyCreateInput.kt` (internal data class)
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/PasskeyCreateResult.kt` (internal data class)
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/PasskeyCreatorTest.kt`

### 公開 IF (design §4.2 / §6)

```kotlin
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyCreator(
    private val keyPairGeneratorFactory: () -> KeyPairGenerator =
        { KeyPairGenerator.getInstance("EC") },
    private val wrappingKeyProvisioner: (Context, String) -> Unit = ::provisionWrappingKey,
    private val secureRandom: SecureRandom = SecureRandom(),
    private val nowMillisProvider: () -> Long = { System.currentTimeMillis() },
) {
    fun create(context: Context, input: PasskeyCreateInput): PasskeyCreateResult
}

internal sealed class PasskeyCreationException(message: String, cause: Throwable?) : Exception(message, cause) {
    class KeyGen(cause: Throwable) : PasskeyCreationException("ES256 keypair generation failed", cause)
    class Encoding(cause: Throwable) : PasskeyCreationException("COSE/CBOR encoding failed", cause)
}
```

実装メモ (design §6):
- `credentialId` 生成: 32 byte `SecureRandom().nextBytes(ByteArray(32))` → `Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)`
- EC keypair: `keyPairGeneratorFactory().apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()`
- wrapping key alias: `"keynest_passkey_$credentialId"`
- `wrappingKeyProvisioner(context, alias)` で AndroidKeyStore に AES-256-GCM wrapping key を作る (StrongBox 二段フォールバック / design §6.4 の `provisionWrappingKey` ヘルパ実装)
- `PrivateKey.encoded` (PKCS#8) を取得し、`SavePasskeyRequest.privateKey` として `PasskeyCreateResult` に乗せる
- `registrationResponseJson` の組み立て: 最低限 `id`, `rawId`, `type = "public-key"`, `response.clientDataJSON` (本 Issue では `null` / 呼び出し側補完), `response.attestationObject` を含む JSON 文字列 (design §5.2 のとおり最終的に `CreatePublicKeyCredentialResponse(registrationResponseJson)` で OS に渡す)
- 例外: `StrongBoxUnavailableException` / `ProviderException` は内部で吸収 (フォールバック)。`KeyStoreException` / 他の `Throwable` は `PasskeyCreationException.KeyGen` でラップして throw

### 受入基準

- **Req 1.1 / 6.1 (a)**: ES256 (P-256) keypair が JCE provider で生成され、curve 名が `secp256r1` であること
- **Req 1.2**: wrapping key alias が `keynest_passkey_<credentialId>` で AndroidKeyStore に作成される
- **Req 1.4 / 1.5 / 6.1 (c)**: `setIsStrongBoxBacked(true)` 試行 → `StrongBoxUnavailableException` 注入で fallback / `ProviderException` 注入でも fallback / 両方失敗で `PasskeyCreationException.KeyGen` が throw
- **Req 1.6**: `PasskeyCreateResult.savePasskeyRequest.privateKey` が PKCS#8 平文 byte 配列 (`KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(it))` で復元可能)
- **Req 1.8 / 6.1 (b)**: `PasskeyCreateResult.publicKeyCose` が COSE_Key bytewise (T-03 と一致)
- **Req 2.x**: `PasskeyCreateResult.authenticatorData` / `attestationObject` が T-04 / T-05 と一致

### テスト

- `PasskeyCreatorTest` (Robolectric, `@Config(sdk = [34])`):
  - `create_generatesEs256KeypairOnP256Curve` (`getParams().curve` が P-256)
  - `create_assignsAliasFollowingKeynestPasskeyPrefix`
  - `create_strongBoxTrySucceeds_whenPlatformSupports` (test seam で `wrappingKeyProvisioner` を fake にしフラグ確認)
  - `create_strongBoxFallback_onStrongBoxUnavailableException` (provisioner が 1 回目 throw → 2 回目 succeed)
  - `create_strongBoxFallback_onProviderException`
  - `create_throwsKeyGen_whenBothAttemptsFail`
  - `create_returnsPasskeyCreateResult_withPlaintextPkcs8PrivateKey` (`KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privateKey))` で `ECPrivateKey` を復元できる)
  - `create_authenticatorData_signCountIsZero_andFlagsAre0x45`
  - `create_attestationObject_isFmtNoneCborMap`
  - `create_credentialId_is43CharBase64UrlWithoutPadding` (32 byte → 43 文字)

### 完了条件

- `./gradlew :app:testDebugUnitTest --tests *PasskeyCreatorTest*` 全 pass
- `./gradlew :app:compileDebugKotlin` 成功

### 依存タスク

- T-01 (`KeynestAaguid`)
- T-03 (`CoseKeyEncoder`)
- T-04 (`AuthenticatorDataBuilder`)
- T-05 (`AttestationObjectBuilder`)

---

## T-07: CreateEntryBuilder + PasskeyCreateActivity + Manifest 登録 + string resources

### 目的

OS Credential Manager UI 用の `CreateEntry` factory と、ユーザーが「KeyNest」を選択した先で起動する `PasskeyCreateActivity` を追加する。Activity は確認画面 + BiometricPrompt + `PasskeyCreator.create()` + `PasskeyRepository.save()` + `PendingIntentHandler.setCreateCredentialResponse()` の一連を担う。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/CreateEntryBuilder.kt`
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/PasskeyCreateActivity.kt`
- 新規: `app/src/main/res/layout/activity_passkey_create.xml` (最小限の確認画面: タイトル / RP / userDisplayName / 「保存する」「キャンセル」)
- 変更: `app/src/main/AndroidManifest.xml` (`<activity>` 追加 — `<service>` 不変)
- 変更: `app/src/main/res/values/strings.xml` (新規 string resources)
- 変更: `app/src/main/res/values-ja/strings.xml` (日本語 / 両 locale で「PassKey」表記)

### 公開 IF (design §4.7 / §4.8)

```kotlin
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class CreateEntryBuilder(private val context: Context) {
    fun build(request: BeginCreatePublicKeyCredentialRequest): CreateEntry
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyCreateActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?)
    companion object {
        internal const val INTENT_DATA_SCHEME = "keynest"
        internal const val INTENT_DATA_AUTHORITY = "passkey"
        internal const val INTENT_DATA_PATH_PREFIX = "/create/"
        @VisibleForTesting internal fun intent(context: Context, requestToken: Int): Intent
    }
}
```

新規 string resources (例 / Developer が文言を最終決定):
- `passkey_create_entry_account_name` — 例: "KeyNest に保存"
- `passkey_create_entry_description` — 例: "PassKey として保存します"
- `passkey_create_confirm_title` — 例: "PassKey を保存しますか？"
- `passkey_create_confirm_save_button` — 例: "保存"
- `passkey_create_confirm_cancel_button` — 例: "キャンセル"
- `passkey_biometric_prompt_title` — 例: "PassKey を保存"
- `passkey_biometric_prompt_subtitle` — 例: "本人確認をしてください"

AndroidManifest 追加 (design §2.1 / §4.8 / NFR 3.3):

```xml
<!--
    Issue #99 / Parent #89:
    PassKey 登録セレモニーで CreateEntry の pending intent から起動される Activity。
    OS Credential Manager から呼ばれる前提のため exported=false。recents から除外し、
    透過テーマで既存 AutofillUnlockActivity と同等の UX を取る。
-->
<activity
    android:name=".credentialprovider.registration.PasskeyCreateActivity"
    android:exported="false"
    android:excludeFromRecents="true"
    android:taskAffinity=""
    android:theme="@style/Theme.KeyNest.Translucent"
    tools:targetApi="34" />
```

実装メモ (design §5.2 / §9.3):
- `onCreate` で `ServiceLocator.initialize(applicationContext)` を defensively 呼ぶ (既存 `AutofillUnlockActivity` パターン踏襲)
- `PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)` で OS 要求を取得 / null なら `CreateCredentialUnknownException`
- 確認画面 layout (`activity_passkey_create.xml`) を `setContentView(...)`。RP 名 / userDisplayName / 保存ボタン / キャンセルボタンを表示
- 「保存」タップ後に `lifecycleScope.launch { ... }` でフロー実行 (design §9.3 の `runRegistrationFlow` パターン)
- 平文 wipe: `try { ... } finally { wipeQueue.forEach { it.fill(0) }; setResult(...); finish() }` で確実に実行
- `BiometricAuthenticator(this as FragmentActivity)` を `authenticate(title, subtitle)` で呼ぶ

### 受入基準

- **Req 4.1**: 確認画面が表示される (RP 名 / userDisplayName / 保存 / キャンセル ボタン)
- **Req 4.2 / 4.3**: 既存 `BiometricAuthenticator` がそのまま呼ばれる (Device Credential フォールバック含む)
- **Req 4.4**: `AuthResult.Cancelled` → `CreateCredentialCancellationException` / `AuthResult.Failed / Unavailable` → `CreateCredentialUnknownException`
- **NFR 1.3**: 平文 private key を `finally` で wipe
- **Manifest**: `<activity>` 1 件追加、`<service>` ブロック不変
- **NFR 6.1**: 新規 string resources のテキストに「PassKey」表記を含み、「passkey」「パスキー」表記を含まない (両 locale)
- **#90 既存 manifest test 非破壊**: `CredentialProviderServiceManifestTest` / `CredentialProviderXmlTest` が引き続き pass (Req 6.6)

### テスト

このタスクでは以下のみ追加:
- `CreateEntryBuilderTest` (Robolectric, sdk 34):
  - `build_returnsCreateEntryWithExpectedAccountAndDescription` (string resource からの値)
  - `build_pendingIntentTargetsPasskeyCreateActivity`
  - `build_pendingIntentIsImmutable_andFlagUpdateCurrent` (PendingIntent.FLAG_IMMUTABLE / FLAG_UPDATE_CURRENT)

`PasskeyCreateActivity` 自体の Robolectric test は T-10 でカバーするか `@Ignore` placeholder の Instrumentation test として T-09 にまとめる (本 task では UI layout + manifest 追加 + 配線のみで満足)。

### 完了条件

- `./gradlew :app:compileDebugKotlin` / `:app:assembleDebug` 成功
- `./gradlew :app:testDebugUnitTest --tests *CreateEntryBuilderTest*` 全 pass
- 既存 `CredentialProviderServiceManifestTest` / `CredentialProviderXmlTest` 引き続き pass

### 依存タスク

- T-06 (`PasskeyCreator`)

---

## T-08: PasskeyRepository alias 命名統一 (`keynest_passkey_<credentialId>`)

### 目的

#91/#107 design の `passkey_<credentialId>` alias を、本 Issue #99 で人間確定済の `keynest_passkey_<credentialId>` に揃える (本 Issue requirements §決定 2 / design §6.1 / §12.1-5)。

### 変更ファイル

- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/repository/PasskeyRepositoryImpl.kt`
  - `companion object { internal fun aliasFor(credentialId: String): String = "keynest_passkey_$credentialId" }` に変更
- 変更: `app/src/test/java/io/github/hitoshiichikawa/keynest/data/PasskeyRepositoryTest.kt`
  - `save_persistsKeyAlias_inEntity` のアサート文字列を `keynest_passkey_<credentialId>` に変更
  - `save_createsKeystoreAlias` で `aliveAliases.contains("keynest_passkey_X")` を assert
  - 他 alias 関連ケース全ての文字列リテラルを `keynest_passkey_` prefix に変更
- 変更: `docs/specs/91-feat-passkey-room-migration-passkeyentit/design.md` の参照箇所 (任意 — 本 Issue 設計と整合性を担保するため、Developer が短いコメントで diff を残してもよい)

### 公開 IF

`PasskeyRepositoryImpl.aliasFor` の戻り値文字列が変わる。public IF は不変 (`internal` 関数 + alias 文字列のみ差分)。

### 受入基準

- `aliasFor("ABC")` の戻り値が `"keynest_passkey_ABC"` (前 `"passkey_ABC"`)
- 既存 `PasskeyRepositoryTest` の全 9 ケースが本タスク変更後も pass する (alias 文字列差分のみ追従)
- `Migration_4_5_Test` / `PasskeyDaoTest` は alias 文字列に依存しないため影響なし (NFR 2.3)

### テスト

新規追加なし。既存 `PasskeyRepositoryTest` を更新するのみ。

### 完了条件

- `./gradlew :app:testDebugUnitTest --tests *PasskeyRepositoryTest*` 全 pass
- `./gradlew :app:testDebugUnitTest --tests *Migration_4_5_Test*` 引き続き pass
- `./gradlew :app:testDebugUnitTest --tests *PasskeyDaoTest*` 引き続き pass

### 依存タスク

- なし (T-01〜T-07 と独立に着手可能。ただし本 Issue 内で T-09 より前に完了させる)

---

## T-09: KeyNestCredentialProviderService.onBeginCreateCredentialRequest 差し替え + ExcludeCredentialDetector + ServiceLocator 配線

### 目的

#90 の空応答 (`BeginCreateCredentialResponse()`) を本実装に置き換える。`excludeCredentials` 照合 + `CreateEntry` 提示 + `outcome.onResult/onError` の分岐を完成させる。同時に `PasskeyCreator` / `CreateEntryBuilder` / `ExcludeCredentialDetector` を ServiceLocator に登録する。

### 変更ファイル

- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/KeyNestCredentialProviderService.kt`
  - `onBeginCreateCredentialRequest` 本体を差し替え (design §4.1)
  - `onBeginGetCredentialRequest` / `onClearCredentialStateRequest` は **touch しない**
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/ExcludeCredentialDetector.kt`
- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/di/ServiceLocator.kt`
  - `passkeyCreator` / `createEntryBuilder` / `excludeCredentialDetector` の lazy singleton 追加 (`passkeyRepository` (#107 既存) はそのまま参照)
- 変更: `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/KeyNestCredentialProviderServiceTest.kt` (#90 既存テスト拡張)

### 公開 IF (design §4.1 / §4.9)

```kotlin
class ExcludeCredentialDetector(private val repository: PasskeyRepository) {
    fun containsAny(credentialIds: List<String>): Boolean
}

// ServiceLocator 追記:
val passkeyCreator: PasskeyCreator by lazy { PasskeyCreator() }
val createEntryBuilder: CreateEntryBuilder by lazy { CreateEntryBuilder(requireAppContext()) }
val excludeCredentialDetector: ExcludeCredentialDetector by lazy {
    ExcludeCredentialDetector(passkeyRepository)
}
```

Service 本体の差し替え (design §4.1):
- `request as? BeginCreatePublicKeyCredentialRequest` で publicKey 以外を空応答に
- `extractExcludeCredentialIds()` 拡張関数 (request の `requestJson` を JSON parse して `excludeCredentials[].id` を取り出し) を同 service ファイル内 (private 拡張) に追加
- `excludeCredentialDetector.containsAny(...)` で hit → `CreateCredentialNoCreateOptionException`
- それ以外 → `BeginCreateCredentialResponse.Builder().addCreateEntry(createEntryBuilder.build(...)).build()`

実装メモ (design §4.1.1):
- ServiceLocator 経由で `excludeCredentialDetector` / `createEntryBuilder` を取得 (`lateinit` field は持たない)
- `containsAny` は `runBlocking(Dispatchers.IO)` で同期実行 (callback 内で許容 / NFR 5.3 トレードオフは design §12 で正当化)
- request の JSON 解析は kotlinx-serialization-json (既存依存) で実施。失敗時は「excludeCredentials 不在」として扱う (防御的)

### 受入基準

- **Req 3.1 / 3.2 / 6.3 (a)(b)**: residentKey = "required" / "preferred" / "discouraged" の各値で `CreateEntry` を含む `BeginCreateCredentialResponse` を `outcome.onResult` で返す (Repository は mock してアサート)
- **Req 5.2 / 5.3 / 6.3 (c)**: `excludeCredentials` 一致時に `outcome.onError(CreateCredentialNoCreateOptionException)` を 1 回 + `Repository.save` が呼ばれない
- **Req 3.3**: `residentKey` 省略時は preferred 相当として `isDiscoverable = true` (本 Service callback 段階では `CreateEntry` を返すだけなので、isDiscoverable 振り分けは `PasskeyCreator.create()` 経由で確認 — T-06 と組み合わせる)
- **Req 6.4**: 既存 #90 空応答テスト (`onBeginGetCredentialRequest` / `onClearCredentialStateRequest`) が引き続き pass
- **NFR 5.3**: Service callback の重い処理が `PasskeyCreateActivity` に委譲されている (callback 内 keypair 生成なし)
- **#90 既存 Manifest test 非破壊**: `<service>` ブロック不変

### テスト

- `KeyNestCredentialProviderServiceTest` (拡張):
  - 既存テスト (`onBeginCreateCredentialRequest_emptyResponse` 等) は **削除ではなく拡張** — 「PublicKey 以外の request で空応答」相当のケースに名前変更 + 維持
  - `onBeginCreateCredentialRequest_publicKeyResidentKeyRequired_returnsCreateEntry`
  - `onBeginCreateCredentialRequest_publicKeyResidentKeyPreferred_returnsCreateEntry`
  - `onBeginCreateCredentialRequest_publicKeyResidentKeyDiscouraged_returnsCreateEntry`
  - `onBeginCreateCredentialRequest_excludeCredentialsHit_returnsNoCreateOptionException`
  - `onBeginCreateCredentialRequest_excludeCredentialsNoHit_returnsCreateEntry`
  - `onBeginCreateCredentialRequest_passwordRequest_returnsEmptyResponse_andDoesNotQueryRepository`
  - `onBeginGetCredentialRequest_stillReturnsEmptyResponse` (#90 既存維持)
  - `onClearCredentialStateRequest_stillReturnsNull` (#90 既存維持)

`PasskeyRepository` は mockk で差し替え (relaxed = false, `coEvery { findByCredentialId(any()) } returns null` 等)。`PasskeyCreator` / `CreateEntryBuilder` も mock で差し替え (Service 本体のテストでは中身を実行しない)。

### 完了条件

- `./gradlew :app:testDebugUnitTest --tests *KeyNestCredentialProviderServiceTest*` 全 pass (旧 + 新ケース)
- `./gradlew :app:assembleDebug` 成功
- `./gradlew :app:lintDebug` warnings ベースライン内

### 依存タスク

- T-06 (`PasskeyCreator`)
- T-07 (`CreateEntryBuilder` / `PasskeyCreateActivity` / Manifest)
- T-08 (alias 統一 — #107 Repository が `keynest_passkey_<credentialId>` 出力で動く)

---

## T-10: 統合確認 (全テスト pass + 既存テスト非破壊 + Instrumentation placeholder + PR 確認事項転記)

### 目的

T-01 〜 T-09 完了後、全テスト pass / build 成功 / 既存テスト非破壊 / Instrumentation test placeholder 配置 / PR description への確認事項転記を行う。

### 変更ファイル

- 新規: `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/PasskeyCreateActivityInstrumentationTest.kt`
  - `@SdkSuppress(minSdkVersion = 34)` + `@Ignore("API 34 emulator が CI に揃うまで手動実行 — #94 完了後に @Ignore 解除")` の placeholder
  - 構成: `ActivityScenario.launch(PasskeyCreateActivity::class.java)` で起動、BiometricPrompt をモックで succeed させて `CreatePublicKeyCredentialResponse` が return されるところまでを assert する skeleton (将来の実装) を `@Ignore` 付きでコミット
- (確認のみ) ファイル変更なし: 全 unit test の pass、既存テスト非破壊

### 受入基準

- **Req 6.x 全件**: design.md → tasks.md → 実装テストの紐づけで Requirement 6.1 / 6.2 / 6.3 / 6.4 / 6.5 / 6.6 が全件カバー
- **既存テスト非破壊** (NFR 2):
  - `KeyNestAutofillService` 関連テスト全 pass
  - #90 `KeyNestCredentialProviderServiceTest` (空応答部分) / `CredentialProviderServiceManifestTest` / `CredentialProviderXmlTest` 全 pass
  - #91/#107 `Migration_4_5_Test` / `PasskeyDaoTest` / `PasskeyRepositoryTest` 全 pass (T-08 で alias 文字列だけ差分)
  - `InternetPermissionAbsenceTest` 全 pass (本 Issue で `<uses-permission android:name="android.permission.INTERNET">` を追加していないこと)
  - `OnBackInvokedCallbackEnabledTest` 全 pass
- **CI 緑保持**: `./gradlew :app:testDebugUnitTest` で全 unit test pass
- **Lint 緑**: `./gradlew :app:lintDebug` で本 Issue 追加コードに新規 error 無し

### 手動検証 (実機 / API 34 emulator, PR description に結果記録)

1. KeyNest をビルド → API 34+ 実機にインストール
2. OS の「設定 → パスワードとパスキー → 既定 → KeyNest」を有効化
3. 任意の 3rd-party Web ブラウザで PassKey 登録対応サイト (例: `https://webauthn.io/`) を開く
4. `navigator.credentials.create()` を呼ぶ操作を実行
5. OS シートに「KeyNest」候補が表示されることを確認
6. KeyNest を選択 → 確認画面 → BiometricPrompt → 指紋 or PIN で認証
7. 登録完了が RP 側で reflect されることを確認 (登録 → ログイン flow が成立)
8. (residentKey 三値分岐) `webauthn.io` の residentKey オプションを切り替えて 3 回試行 → KeyNest 内 `passkeys` テーブルに `isDiscoverable` true/true/false で row が積まれることを `adb shell run-as` 経由 (本 Issue では UI から確認不可) で確認、または `PasskeyDao.listAllByRpId(...)` を呼ぶ debug build 用 menu を一時的に追加して確認
9. (excludeCredentials) 同一 RP / userHandle で再登録を試み、`excludeCredentials` を指定したフローで `CreateCredentialNoCreateOptionException` が OS シートに表示されることを確認

### PR description に転記する確認事項 (design §12.5)

1. **alias 命名統一**: T-08 で `PasskeyRepositoryImpl.aliasFor` を `"keynest_passkey_$credentialId"` に書き換える方針が #91/#107 既存 row を破壊しないこと (本 Issue マージ前後で実 PassKey row が存在しない前提) を human reviewer に確認してもらう
2. **EC private key 生成 provider**: AndroidKeyStore ではなく JCE 標準 provider (AndroidOpenSSL) で生成する設計 (design §6.3 選択肢 B) が Req 1.1 の意図を AES wrapping ハイブリッド保管で達成すると解釈してよいか、human reviewer に確認してもらう
3. **`excludeCredentials` チェック位置**: Service callback 内 `runBlocking` 方針 (design §4.1.1) が NFR 5.3 callback 速やか応答方針と整合することを human reviewer に確認してもらう
4. **(rpId, userHandle) 重複時のトランザクション境界**: `delete` → `save` 逐次実行で、`delete` が `DeletePasskeyResult.KeystoreCleanupFailed` を返した場合に新規 INSERT に進む設計 (design §5.3.2) が、Req 5.4 「silent fail 禁止」と整合することを human reviewer に確認してもらう
5. **`androidx.credentials` バージョン**: 1.3.0 維持で十分か、1.5.0 への bump が必要か、実装中に判明した場合は本 PR 内で `libs.versions.toml` を更新してよいか

### 完了条件 (Definition of Done)

- 全 T-01〜T-09 の受入基準が満たされている
- `./gradlew :app:testDebugUnitTest` で **全 unit test pass** (新規 + 既存)
- `./gradlew :app:assembleDebug` 成功
- `./gradlew :app:lintDebug` warnings ベースライン内 (新規 error 無し)
- `app/src/androidTest/` に `PasskeyCreateActivityInstrumentationTest` を `@Ignore` 付きで配置
- 手動検証 1〜9 を PR description に結果記録
- PR description の「確認事項」セクションに上記 1〜5 を転記

### 依存タスク

- T-01 〜 T-09 全完了
