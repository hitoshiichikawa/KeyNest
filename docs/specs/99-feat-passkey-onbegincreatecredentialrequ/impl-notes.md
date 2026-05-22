# Implementation Notes — Issue #99 / feat(passkey): 登録セレモニー

> 関連: `requirements.md` / `design.md` / `tasks.md`
> ブランチ: `claude/issue-99-impl-feat-passkey-onbegincreatecredentialrequ`

## 状態サマリ

- **完了タスク**: T-01 (KeynestAaguid), T-02 (CborWriter), T-03 (CoseKeyEncoder),
  T-04 (AuthenticatorDataBuilder), T-05 (AttestationObjectBuilder)
- **保留タスク**: T-06 (PasskeyCreator), T-07 (CreateEntryBuilder + PasskeyCreateActivity),
  T-08 (alias 統一), T-09 (Service 差し替え + ExcludeCredentialDetector), T-10 (統合確認)
- **保留理由**: **#107 (PasskeyRepository + impl + ServiceLocator wiring + tests) が未 merge**。
  T-06 以降は `SavePasskeyRequest` / `PasskeyRepository` / `PasskeyRepositoryImpl.aliasFor`
  に依存しており、これらの型が存在しないため実装できない。tasks.md「前提依存」にも
  「本 Issue 着手時に #107 がまだ未 merge の場合、Developer はまず #107 の merge 完了を
  確認すること」と明記されている。

## 完了タスク詳細

### T-01: KeynestAaguid + test

- `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/KeynestAaguid.kt` 追加
- `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/KeynestAaguidTest.kt` 追加
- 16 byte big-endian `2a 56 cf 86 83 32 48 29 9f 2a e9 a4 ad bc 7a be`
- `bytes()` は `copyOf()` で毎回 defensive copy を返す
- Req 2.1 / 6.2(a) カバー

### T-02: CborWriter + test

- 自前 minimal CBOR encoder。uint / nint / byte string / text string / array header / map header の 6 種
- length encoding は RFC 8949 §3 (`0..23` / `0x18` / `0x19` / `0x1A` / `0x1B`) を `writeHeader` private helper に集約
- `writeNegativeInt(value)` は **負の Long** を受け取り内部で `-1 - value` を unsigned に変換 (defensively `value >= 0` で `IllegalArgumentException`)
- `writeUnsignedInt(value)` も defensively `value < 0` で `IllegalArgumentException`
- 9 ケースのテスト: RFC 8949 Appendix A vector を bytewise 比較 + chained writes + 防御的 guard
- design §4.5 / §7.5

### T-03: CoseKeyEncoder + test

- ES256 P-256 ECPublicKey を COSE_Key (alg=-7, kty=2, crv=1, x/y 32B big-endian) で encode
- map ordering: WebAuthn 慣例の `(1, 3, -1, -2, -3)` 順 (canonical でなく)
- `BigInteger.toByteArray()` の sign byte を剥がし 32 byte に左 0 padding する `toUnsignedFixedLength` private extension function
- 防御的 P-256 curve guard: `publicKey.params.curve.field.fieldSize == 256` で判定
- テスト 3 ケース:
  - `encodeEs256_bytewise_matchesKnownVector`: RFC 8152 §C.7.1 (`bilbo.baggins@hobbiton.example`) の x/y を `KeyFactory.getInstance("EC")` + `ECPublicKeySpec` で復元し bytewise 一致
  - `encodeEs256_paddsX_andY_to_32Bytes_evenWhenMsbZero`: P-256 keypair をランダム生成し、MSB=0 (raw byte 列が 32 未満) のサンプルが出るまで再生成して左 padding 不変条件を検証
  - `encodeEs256_throwsForNonP256Curve`: `secp384r1` で `IllegalArgumentException`
- design §4.4 / §7.4

### T-04: AuthenticatorDataBuilder + test

- WebAuthn §6.1 authenticatorData (`rpIdHash | flags | signCount | attestedCredentialData? | extensions?`)
- FLAG_UP=0x01 / FLAG_UV=0x04 / FLAG_BE=0x08 / FLAG_BS=0x10 / FLAG_AT=0x40 / FLAG_ED=-0x80 (0x80 unsigned)
- 登録時は呼び出し側で `0x45` (UP|UV|AT) を組み立てる契約。flag と attestedCredentialData の整合は呼び出し側責務 (auto-clear なし)
- `attestedCredentialData(aaguid, credentialId, publicKeyCose)` 補助関数 (size guard: aaguid==16, credentialId in 1..1023)
- `rpIdHash(rpId)` 補助関数 (`MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray(UTF_8))`)
- signCount は `ByteBuffer.BIG_ENDIAN.putInt(signCount)` で 4 byte big-endian
- テスト 10 ケース: rpIdHash / layout offsets / 0x45 flag / signCount 3 値 / attestedCredentialData layout / credLen BE 3 値 / aaguid 不正 size / credentialId 不正 size / null attested 経路 / null extensions / rpIdHash 不正 size
- design §4.3 / §7.1 / §7.2

### T-05: AttestationObjectBuilder + test

- WebAuthn §6.5.4 attestation object (CBOR map 3 entries) を `CborWriter` (T-02) で組み立て
- map ordering: `fmt -> attStmt -> authData` (WebAuthn 仕様順、alphabetical ではない)
- `fmt = "none"`, `attStmt = {}` (空 map), `authData` は `writeByteString` 経由で長さ依存の header (0x58/0x59) を自動選択
- テスト 4 ケース:
  - `buildFormatNone_bytewise_prefixMatchesWebAuthnLayout`: 28-byte prefix を bytewise pin + `0x58 0x25` (37 byte authData) + tail 一致
  - `buildFormatNone_authDataIsCborByteString_with2ByteHeaderWhenLongerThan255`: 256-byte authData で `0x59 0x01 0x00` header
  - `buildFormatNone_mapHeaderIs0xA3`
  - `buildFormatNone_keysAreInWebAuthnOrder_notAlphabetical`: "fmt" / "attStmt" / "authData" の出現順を index 比較で verify
- design §4.6 / §7.3

## 保留タスク (T-06..T-10) — #107 依存

| Task | ブロック理由 |
|------|-------------|
| T-06 PasskeyCreator | `PasskeyCreateResult.savePasskeyRequest: SavePasskeyRequest` が #107 で定義される型を要求。Repository 経由の暗号化方針 (Repo 内で `cipher.encrypt(...)` する設計 — design §6.3 選択肢 B + requirements.md §「データモデル/公開IF」) も `PasskeyRepositoryImpl` 不在では検証不能。 |
| T-07 CreateEntryBuilder + Activity | `PasskeyCreateActivity.runRegistrationFlow` が `PasskeyRepository.save(SavePasskeyRequest)` / `PasskeyRepository.findByRpIdAndUserHandle(...)` / `PasskeyRepository.delete(...)` を呼ぶ。`DeletePasskeyResult` 型 (#107 design 由来) も未定義。 |
| T-08 alias 統一 | 改名対象の `PasskeyRepositoryImpl.aliasFor` が未実装。 |
| T-09 Service 差し替え | `ExcludeCredentialDetector(repository: PasskeyRepository)` が `repository.findByCredentialId(...)` を呼ぶ。`ServiceLocator.passkeyRepository` も #107 で wiring される予定で本リポジトリには未配線。 |
| T-10 統合確認 | T-06..T-09 が前提。 |

## 確認事項 (人間レビュアへ)

### Q1. **#107 が未 merge の状態で本 Issue を進める方針**

tasks.md「前提依存」に従い T-01..T-05 (#107 に依存しないビルディングブロック) のみ
実装し、T-06..T-10 は #107 merge を待つ方針で進めた。次のいずれを採るべきか確認:

  - **(a)** 本 PR は T-01..T-05 のみで cut し、#107 merge 後に別 PR で T-06..T-10 を実装
  - **(b)** #107 を本 PR 内で先回り実装 (scope クリープ)
  - **(c)** #107 の merge を待ってから本 Issue 再開

本 Implementation Notes 著者の推奨は **(a)**。理由: T-01..T-05 は単体で
- レビュー可能 (それぞれ bytewise 受入基準が明確)
- 後続 Issue (認証セレモニー #89 分割案 4) からも `KeynestAaguid` / `CoseKeyEncoder` /
  `AuthenticatorDataBuilder` / `CborWriter` を再利用予定
- #107 とは独立にビルド / テストが pass する

### Q2. **alias 命名統一 (T-08)**

design §6.1 / §12.1-5 / requirements.md §決定 2 によれば、`PasskeyRepositoryImpl.aliasFor`
を `"keynest_passkey_$credentialId"` に変更する。本 PR では #107 未 merge のため実施
できないが、#107 側で初版から `keynest_passkey_<credentialId>` を採用してもらえれば
T-08 自体が不要になる。#107 の担当者と本 Issue 間で alias 文字列を事前に揃えるか確認。

### Q3. **EC private key 生成 provider (T-06 設計選択肢 B)**

design §6.3 で「AndroidKeyStore ではなく JCE 標準 provider (AndroidOpenSSL) で
生成し、AES wrapping ハイブリッド保管」を確定済。req 1.1 「AndroidKeyStore provider
上で生成」の意図を AES wrapping 経由で達成する解釈で問題ないか、T-06 着手前に
再確認したい (本 PR 範囲外だが備忘)。

### Q4. **`excludeCredentials` Service callback 内 `runBlocking` (T-09)**

design §4.1.1 で `ExcludeCredentialDetector.containsAny()` を `runBlocking(Dispatchers.IO)`
で同期実行する方針。NFR 5.3 callback 速やか応答方針との整合は design §12 で正当化
されているが、本 PR 範囲外なので備忘。

### Q5. **`androidx.credentials` バージョン**

design §12.1.9 で 1.3.0 維持を確定済。T-06..T-09 実装時に
`BeginCreatePublicKeyCredentialRequest` / `CreateEntry` / `PendingIntentHandler` /
`CreatePublicKeyCredentialResponse` / `CreateCredentialNoCreateOptionException` の
API 有無を最初に確認すること。

## 実装メモ — T-01..T-05 で気付いた点

### テスト実行環境 (確認)

T-01..T-05 のテストはすべて純 JVM (`MessageDigest` / `BigInteger` / `KeyFactory("EC")`
/ `KeyPairGenerator("EC")` はすべて JCE 標準)。Robolectric / AndroidJUnit4 ランナーは
**使っていない** (plain JUnit4 + Truth)。`@RunWith(AndroidJUnit4::class)` を付けると
Robolectric SDK ダウンロードが走り CI が遅くなる + sandbox 制約に当たるため、本実装は
plain JUnit4 で完結させた。`./gradlew :app:testDebugUnitTest --tests '*<Class>Test*'`
で 5 ケース × 各テストは数百ミリ秒で pass する。

`./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin` 成功 (warnings は
既存の autofill / AssistStructure 由来のみ、本 Issue 追加コード由来は無し)。

`./gradlew :app:testDebugUnitTest` 全件実行も走らせた。710 ケース中 1 件失敗だが、これは
**`AppInfoProviderTest.get_returnsVersionNameFromBuildGradle`** が build.gradle.kts の
versionName を `"0.1.0"` 期待しているのに対し実値が `"1.0.0"` という pre-existing な
差分で、本 Issue とは無関係 (build.gradle.kts は触っていない / `develop` でも同じ失敗)。

### CBOR `writeNegativeInt` / `writeUnsignedInt` の引数仕様

design §4.5 と tasks.md T-02 で「引数 `value` は **負の整数** を受け取り、内部で
`-1L - value` を unsigned encoding に変換」と確定。両方向で defensive guard を入れた:
- `writeNegativeInt(value >= 0)` → `IllegalArgumentException`
- `writeUnsignedInt(value < 0)` → `IllegalArgumentException`

各 `CborWriter` メソッドは `this` を返して chain 可能。length encoding は
`writeHeader(majorType, argument)` private helper に集約 (5 分岐: <=23 / <=0xFF /
<=0xFFFF / <=0xFFFFFFFF / それ以上)。

### COSE_Key map ordering (canonical CBOR との差異)

RFC 7049 / 8949 §3.9 canonical encoding は length-then-byte sort (uint 0..23 →
uint 24.. → nint 等の major type 順) を要求するが、WebAuthn テストベクタは
COSE_Key を `(1, 3, -1, -2, -3)` の COSE 慣例順で示す。本実装は WebAuthn 互換性を
優先し COSE 慣例順を採用 (design §4.4 / §7.4 / §10.5)。

### RFC 8152 §C.7.1 テストベクタの採用

`CoseKeyEncoderTest.encodeEs256_bytewise_matchesKnownVector` では
`bilbo.baggins@hobbiton.example` の P-256 公開鍵 (x=bac5b1...09eff, y=20138b...c117e)
を `KeyFactory.getInstance("EC")` + `ECPublicKeySpec(ECPoint(x, y), ECParameterSpec(secp256r1))`
で復元し、CBOR encode 結果が `A5 01 02 03 26 20 01 21 58 20 <x> 22 58 20 <y>` と
bytewise 一致することを確認した。`KeyFactory.generatePublic` は曲線上の点でない値で
`InvalidKeySpecException` を投げるため、test が pass しているということはこの vector
は P-256 上の有効な点で、追加の点検証は不要。

### 防御的 P-256 curve 判定

`CoseKeyEncoder.encodeEs256` で `publicKey.params.curve.field.fieldSize == 256` を確認。
curve OID 名 (`secp256r1` / `prime256v1`) は provider 依存だが、有限体のビット幅は
P-256 で常に 256 で安定なため。test では `secp384r1` で `IllegalArgumentException` を
verify。

### `BigInteger.toByteArray()` の sign byte 処理

`ECPublicKey.w.affineX.toByteArray()` は MSB が立つときに先頭 0x00 を prepend する
(2's complement sign)。33 byte になりうるため、`toUnsignedFixedLength(32)` で先頭の
0x00 を剥がし、必要なら左 0 padding する必要がある。`CoseKeyEncoderTest` の
`encodeEs256_paddsX_andY_to_32Bytes_evenWhenMsbZero` では MSB=0 の P-256 鍵が出る
まで最大 64 回 retry して padding 不変条件を検証する (deterministic な MSB=0 vector
の構築は P-256 曲線方程式制約のため非自明)。

### `AuthenticatorDataBuilder.build` の attestedCredentialData 省略時動作

design §4.3 では「`attestedCredentialData = null` のとき AT ビットを立てない / 末尾省略」
と明記されているが、本実装の `build` は呼び出し側が `flags` を組み立てる契約のため、
**flags の AT ビットの自動制御は行わない**。呼び出し側 (PasskeyCreator) が登録時は
`FLAG_UP or FLAG_UV or FLAG_AT` (0x45)、認証時は `FLAG_UP or FLAG_UV` (0x05) を渡す
責務を負う。T-04 テストの `build_withoutAttestedCredentialData_omitsItAndAtFlag` は
「呼び出し側が `attestedCredentialData = null` のとき flags から AT を抜く」前提で
書いてある (= AT ビットを含まない flags を渡したケースを verify)。

### `AttestationObjectBuilder.buildFormatNone` の authData encoding

design §7.3 のテストベクタでは authData の長さが 1 byte (header `0x58 <len>`) か
2 byte (header `0x59 <len high> <len low>`) かで分岐する。CBOR major type 2
(byte string) を `CborWriter.writeByteString` 経由で生成すれば長さに応じて正しい
header が選ばれる (T-02 でカバー済)。T-05 テストでは 37 byte (`0x58 0x25`) と
256 byte (`0x59 0x01 0x00`) の両方を verify。

### tasks.md の文言と本実装の整合差分

- tasks.md T-02 受入テストには `writeArrayHeader_small` が記載されていないが、
  T-02 公開 IF (`fun writeArrayHeader(count: Int)`) は記載されているため、テスト
  ケースとして `writeArrayHeader_small` (0 → `0x80`, 1 → `0x81`) を追加した
  (本 Issue で使用しないが、認証セレモニー #89 分割案 4 で再利用が想定される)。
- T-02 の `unsignedInt_throwsForNegativeArgument` は tasks.md 本文では受入基準に
  明示されていないが、design §4.5 の防御的方針と T-02 受入テスト一覧の
  `negativeInt_throwsForPositiveArgument` の対称性から追加した。
