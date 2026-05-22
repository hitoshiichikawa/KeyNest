# Requirements Document

## 概要 / Goal / Non-Goal

### 概要

KeyNest は umbrella Issue #89 で「Android Credential Manager API 経由の PassKey
プロバイダ対応」を進めることが確定しており、その Phase 1 として並列に進む
#90 (Service Manifest 登録 + 空応答スケルトン) と #91 (Room 永続化レイヤ +
PasskeyEntity / PasskeyDao / PasskeyRepository) が先行している。

本 Issue (#99) は umbrella #89 の **Phase 2 = 登録セレモニー (registration
ceremony)** を実装する。具体的には、

- #90 で骨格だけ存在する `KeyNestCredentialProviderService.onBeginCreateCredentialRequest`
  の **空応答実装を本実装に置き換え**、
- WebAuthn `navigator.credentials.create()` 由来の `BeginCreateCredentialRequest`
  (PublicKey type) を受けて OS の Credential Manager UI に **`CreateEntry` を提示**し、
- ユーザーが KeyNest を選択した先で `PasskeyCreateActivity` (新規) を起動して
  **確認画面 + BiometricPrompt** を経由したうえで、
- `PasskeyCreator` (新規 domain layer) が **ES256 (P-256) keypair を AndroidKeyStore
  (可能なら StrongBox) で生成**し、private key を `AesGcmCipher` で暗号化、
- 確定 AAGUID (`2a56cf86-8332-4829-9f2a-e9a4adbc7abe`) を 16 byte big-endian で
  `authenticatorData` に埋め込み、`attestationObject` を `fmt = none` で組み立て、
- `PasskeyRepository.save(...)` (#91) を経由して `passkeys` テーブルに永続化、
- `PublicKeyCredential` レスポンスを OS / RP に返却する

までを 1 PR の到達点とする。

この Issue 単体では認証セレモニー (`onBeginGetCredentialRequest` 本体) / 一覧 UI /
個別管理 / 設定画面の更新 / attestation `direct` / `packed` 形式 / excludeCredentials
時のユーザー向けエラー UI 文言策定 には到達しない (Out of Scope, #89 後続分割案 4〜7
+ 後述 carve-out)。

なお umbrella #89 のとおり、UI / KDoc / コメント / ログメッセージに登場する
日本語 / 英語表記は **「PassKey」** に統一する。

### Goal

- `KeyNestCredentialProviderService.onBeginCreateCredentialRequest` を「空応答」から
  「`CreateEntry` 入りの `BeginCreateCredentialResponse`」に差し替える。
- 新規 `PasskeyCreateActivity` を `CreateEntry` の pending intent から起動可能にし、
  そこで「保存しますか？」確認画面と `BiometricAuthenticator` (既存) 起動を行う。
- 新規 `PasskeyCreator` (domain / pure Kotlin) を導入し、ES256 (P-256) keypair 生成、
  COSE_Key (alg = -7) public key encoding、AAGUID 16 byte 埋め込みの
  `authenticatorData` 組み立て、`attestationObject` の `fmt = none` CBOR 直列化を
  集約する。
- StrongBox 対応端末では `setIsStrongBoxBacked(true)` で keypair 生成を試み、
  非対応端末 (`StrongBoxUnavailableException` ないし `setIsStrongBoxBacked` 不可)
  では **通常 TEE Keystore にフォールバック** する (決定 1)。
- 生成した keypair の private key を `AesGcmCipher`
  (`KeystoreKeyProvider(keyAlias = "keynest_passkey_<credentialId>")` 注入) で
  AES-GCM 暗号化し、`PasskeyRepository.save(SavePasskeyRequest)` (#91) で
  `passkeys` テーブル + Keystore wrapping key alias `keynest_passkey_<credentialId>`
  に永続化する (決定 2)。
- RP の `residentKey: "required" | "preferred" | "discouraged"` 値ごとに
  `PasskeyEntity.isDiscoverable` を `true | true | false` で振り分ける。
  `userHandle` / `userName` / `userDisplayName` は両モード共通で保存する。
- 既存 `BiometricAuthenticator` (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`) を再利用し、
  BiometricPrompt 未設定端末では Device Credential (PIN/Pattern) にフォールバック
  させる (#89 確定事項)。
- 同一 `(rpId, userHandle)` の PassKey が既存の場合は新規 keypair で **上書き**
  する (RP 仕様)。`excludeCredentials` で指定された credentialId が KeyNest 内に
  既存のときは `CreateCredentialNoCreateOptionException` を呼び出し側に返す
  (決定 3)。
- 上記をカバーする `PasskeyCreatorTest` / `AuthenticatorDataTest` /
  `KeyNestCredentialProviderServiceTest` (登録セレモニー用 unit test) を追加し、
  既存テスト (#90 / #91 で導入された Robolectric / migration / DAO テスト) を
  破壊しない。

### Non-Goal (Out of Scope)

- 認証セレモニー (`onBeginGetCredentialRequest` の実体: エントリ提示 / BiometricPrompt /
  assertion 署名 / signCount インクリメント) — umbrella #89 分割案 4。
- 既存クレデンシャル **一覧 UI への PassKey 表示** — umbrella #89 分割案 5。
- PassKey 単位の **rename / 削除 UI** — umbrella #89 分割案 6。
- **設定画面**の「PassKey プロバイダとして登録」状態表示・OS 設定への導線 —
  umbrella #89 分割案 7。
- README / Privacy Policy / Support ページの更新 — umbrella #89 分割案 8。
- WebAuthn Attestation の **`direct` / `packed`** 形式 (本 Issue は `fmt = none`
  のみ。umbrella #89 Out of Scope を継承)。
- **excludeCredentials エラー UX のユーザー向けメッセージ UI / 文言策定**
  (決定 3 により別 Issue へ carve out。本 Issue は `CreateCredentialNoCreateOptionException`
  送出までで完了。後述「carve-out」セクション参照)。
- **PasskeyEntity / PasskeyDao / PasskeyRepository / Migration_4_5 の新規追加**
  (#91 が担当。本 Issue は #91 で確定した interface
  (`PasskeyRepository.save / findByRpIdAndUserHandle / findByCredentialId / delete`)
  をそのまま呼び出す側)。
- **`CredentialProviderService` 自体の Manifest 登録 / xml / 依存追加**
  (#90 が担当。本 Issue は #90 で配線済みの Service クラス本体だけを差し替える)。
- API 26〜33 ユーザー向けの「PassKey 機能は Android 14 以降で利用可能」表示
  (umbrella #89 分割案 7 / 設定画面 Issue)。
- 一覧 UI から PassKey を参照する Flow API (#91 で「需要発生時に別 Issue」と確定済み)。
- 旧 PassKey 上書き時の **古い Keystore alias の cleanup 責務** の最終確定
  (未解決事項として `delete` を呼ぶか `save` 側で `deleteKey` するかは design.md
  フェーズで詰める。後述「未解決事項」)。
- AAGUID の WebAuthn `direct` attestation 露出 (umbrella #89 で attestation `none`
  のみと確定済み。AAGUID は `authenticatorData` 内で必須なので埋め込みは行う)。

## 関連 Issue / PR

- **Parent (umbrella)**: #89 feat(passkey): Android Credential Manager 経由の
  PassKey プロバイダ対応 (umbrella)。
- **Depends on (Phase 1, 並列実施)**:
  - #90 feat(passkey): `CredentialProviderService` の Manifest 登録と最小骨組み
    (本 Issue が差し替える `KeyNestCredentialProviderService.onBeginCreateCredentialRequest`
    の **空応答スケルトン** を提供する)。
  - #91 feat(passkey): Room migration + `PasskeyEntity` / `PasskeyDao` /
    `PasskeyRepository` (本 Issue が `save(SavePasskeyRequest)` /
    `findByRpIdAndUserHandle` / `findByCredentialId` / `delete` を呼び出す)。
- **後続予定 (Phase 2 以降)**:
  - umbrella #89 分割案 4: 認証セレモニー (`onBeginGetCredentialRequest`)。
    本 Issue で確立する `PasskeyCreator` / `AuthenticatorData` の組み立てロジック
    の対 (assertion 署名) を実装する。
  - umbrella #89 分割案 5/6: 一覧 UI / 個別管理 UI。
  - umbrella #89 分割案 7: 設定画面。
  - excludeCredentials UX 文言策定 Issue (本 Issue から carve out)。

## スコープ

### 新規追加するファイル

| 対象 | 概要 |
|---|---|
| `app/src/main/java/.../credentialprovider/registration/PasskeyCreator.kt` | domain layer。ES256 (P-256) keypair 生成 (StrongBox 二段フォールバック) / COSE_Key (alg=-7) encode / `authenticatorData` 組み立て / `attestationObject` (fmt=none) CBOR encode |
| `app/src/main/java/.../credentialprovider/registration/AuthenticatorData.kt` | `authenticatorData` の byte 構造を組み立てる pure Kotlin ヘルパ。rpIdHash (SHA-256) / flags / signCount / attestedCredentialData (AAGUID + credentialIdLength + credentialId + COSE_Key) |
| `app/src/main/java/.../credentialprovider/registration/KeynestAaguid.kt` | 確定 AAGUID `2a56cf86-8332-4829-9f2a-e9a4adbc7abe` を 16 byte (`ByteArray`) として 1 箇所に集約する定数オブジェクト |
| `app/src/main/java/.../credentialprovider/registration/PasskeyCreateActivity.kt` | `FragmentActivity`。`CreateEntry` の pending intent から起動。確認画面 + `BiometricAuthenticator.authenticate(...)` → `PasskeyCreator` 呼び出し → `PasskeyRepository.save(...)` → 結果 (PublicKeyCredential / Exception) を `PendingIntentHandler.setCreateCredentialResponse(...)` 経由で OS に返却 |
| `app/src/main/java/.../credentialprovider/registration/CreateEntryFactory.kt` (仮称、最終粒度は design.md で確定) | `BeginCreateCredentialRequest` から `CreateEntry` (account display name / 説明文 / pending intent) を組み立てる factory |
| `app/src/test/.../credentialprovider/registration/PasskeyCreatorTest.kt` | ES256 keypair 生成 / COSE_Key encode (alg=-7 / kty=2 / crv=1 / x,y 32 byte) の bytewise 検証 |
| `app/src/test/.../credentialprovider/registration/AuthenticatorDataTest.kt` | AAGUID 16 byte / rpIdHash 32 byte / flags / signCount 初期 0 / credentialId / credentialIdLength の bytewise 検証 |
| `app/src/test/.../credentialprovider/KeyNestCredentialProviderServiceTest.kt` (#90 で追加されたテストを **拡張**) | `onBeginCreateCredentialRequest` が `residentKey = required / preferred / discouraged` の各値で `isDiscoverable` 設定済みの `CreateEntry` を返すこと / `excludeCredentials` 一致時に `CreateCredentialNoCreateOptionException` を `outcome.onError` で返すこと |

### 変更する既存ファイル

| 対象 | 変更内容 |
|---|---|
| `app/src/main/java/.../credentialprovider/KeyNestCredentialProviderService.kt` (#90 導入) | `onBeginCreateCredentialRequest` の空応答 (`BeginCreateCredentialResponse()`) を本実装に置換。`onBeginGetCredentialRequest` / `onClearCredentialStateRequest` は触らない (#89 分割案 4 / 7 担当) |
| `app/src/main/AndroidManifest.xml` | `<activity android:name=".credentialprovider.registration.PasskeyCreateActivity" ...>` を追加 (`exported=false`、`theme` は既存 dialog / translucent theme を流用)。既存 `KeyNestCredentialProviderService` の `<service>` 宣言は変更しない (#90 確定) |
| `app/src/main/java/.../auth/BiometricAuthenticator.kt` | **基本的に変更しない**。本 Issue では既存のまま再利用 (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL` の Authenticators 構成と `AuthResult` sealed class が #89 「Device Credential フォールバック」要件を満たしている)。`FragmentActivity` 注入で `PasskeyCreateActivity` から使う |
| `app/src/main/java/.../security/AesGcmCipher.kt` | 変更しない (alias 注入は呼び出し側 `KeystoreKeyProvider(keyAlias = ...)` で完結) |
| `app/src/main/java/.../security/KeystoreKeyProvider.kt` | 変更しない (`keyAlias` コンストラクタ引数を `keynest_passkey_<credentialId>` で呼び出すだけ) |
| `app/src/main/java/.../di/ServiceLocator.kt` | `PasskeyCreator` のシングルトン提供を追加検討 (最終粒度は design.md で確定)。`PasskeyRepository` (#91 で追加済み) はそのまま参照 |

### Out of Scope (再掲)

- #91 のデータ層 / #90 の Service 配線は本 Issue では触らない。
- 認証セレモニー / 一覧 UI / 個別管理 / 設定画面 / docs は本 Issue では触らない。
- excludeCredentials の **エラー UI 文言** は本 Issue では策定しない (carve-out、決定 3)。

## 決定事項 (Issue #99 コメントで人間確定)

> Issue #99 本文「確認事項 1〜3」に対し、人間レビュアが「**1, 2, 3 すべて推奨案で
> かまいません**」と確定済み。以下はその確定値を本 requirements で固定し、
> 関連 Requirement との対応を明示するためのリファレンス。

### 決定 1: StrongBox 二段フォールバックを採用 (Option A)

- **決定**: ES256 (P-256) keypair 生成時、StrongBox 対応端末では
  `KeyGenParameterSpec.Builder(...).setIsStrongBoxBacked(true)` を試行し、
  非対応端末 (`StrongBoxUnavailableException` あるいは
  `setIsStrongBoxBacked(true)` 自体が利用不能なケース) では `try / catch` で
  通常 TEE Keystore (StrongBox なし) にフォールバックする。
- **理由 (人間確定の推奨案より)**: StrongBox 対応端末でのハードウェア保護強化は
  FIDO2 設計意図に合致し、非対応端末へのフォールバックも業界標準の実装慣行。
- **影響範囲**:
  - `PasskeyCreator` 内の keypair 生成ロジック
  - Requirement 1.4 / 1.5 で明示
  - NFR 1.1 (private key の Keystore 外露出禁止) を満たすために、StrongBox / 通常
    Keystore のいずれであっても `KeyProperties.KEY_ALGORITHM_EC` + AndroidKeyStore
    provider を必須にする

### 決定 2: Keystore alias 命名 = `keynest_passkey_<credentialId>` (Option A)

- **決定**: PassKey の private key を AES-GCM 暗号化する wrapping key の
  AndroidKeyStore alias は **`keynest_passkey_<credentialId>`** で固定。
  `credentialId` は WebAuthn 仕様の base64url 文字列 (PassKey の PK) をそのまま使う。
- **#91 との整合確認**: #91 では Keystore alias 命名を **`passkey_<credentialId>`**
  と確定している (#91 requirements.md 「決定 3」)。Issue #99 本文の提案は
  **`keynest_passkey_<credentialId>`** (prefix に `keynest_` 追加) であり、人間
  確定により Issue #99 提案がそのまま採用された。
  - **本 requirements の方針**: 本 Issue #99 では人間確定の `keynest_passkey_<credentialId>`
    を採用する。#91 design.md (内部実装) の `passkey_<credentialId>` との **不整合は
    本 Issue 着手前に解消する必要がある**。具体的には次のいずれかを design.md
    フェーズで確定する:
    1. #91 design.md / impl-notes.md / コードを **`keynest_passkey_<credentialId>`** に
       揃える (`PasskeyRepositoryImpl.aliasFor(credentialId)` の戻り値を変更)。
    2. もしくは #91 で実装済みコードを尊重し、本 Issue では `passkey_<credentialId>`
       に揃える (この場合は人間レビュア再確認が必要)。
  - 本 requirements としては **(1) を推奨**する (Issue #99 本文の Option A 採用、
    Issue #99 コメントで人間が明示確定したのは Issue #99 本文の表現)。
  - **未解決事項**として後段「未解決事項 / 確認事項」に再掲する。
- **影響範囲**:
  - `PasskeyCreator` 内で keypair 生成時に AndroidKeyStore alias を組み立てる箇所
  - `PasskeyRepositoryImpl` (#91) の `cipherFactory` / `keyProviderFactory` に
    渡す alias 文字列
  - Requirement 1.2 / 1.6 / NFR 1.2 で明示

### 決定 3: excludeCredentials エラー UX は本 Issue 範囲外に carve out (Option B)

- **決定**: RP が `excludeCredentials` を指定し、その中に KeyNest が保管済みの
  credentialId が含まれている場合、本 Issue では
  **`CreateCredentialNoCreateOptionException` を `outcome.onError(...)` で返す**
  ところまでで実装を打ち切る。ユーザー向けのエラーメッセージ UI / 文言 / トースト
  表示 / ダイアログは本 Issue では実装しない。
- **理由 (人間確定の推奨案より)**: 本 Issue は keypair 生成・`authenticatorData`
  組み立て・生体認証のコアロジックに集中させ、UX 詳細を別 Issue に分離することで
  レビュー品質を保ち、スコープクリープを防ぐ。
- **carve-out 先**: excludeCredentials UX 文言策定 Issue (本 Issue 着手後、別途
  人間が起票)。
- **影響範囲**:
  - Requirement 5.2 で「`CreateCredentialNoCreateOptionException` を返す」までを
    Acceptance Criteria とする
  - Out of Scope セクションで明示
  - 本 Issue の `KeyNestCredentialProviderServiceTest` では「例外型と例外メッセージ
    が空でないこと」までを検証し、メッセージ文言の locale 別検証はしない

## 背景

- umbrella #89 でカバーする 8 つの分割案のうち、Phase 1 の #90 (Service 骨格) /
  #91 (Room 永続化) が確定し、本 Issue (#99 = 分割案 3「登録セレモニー」) が
  Phase 2 の最初の実装フェーズ。
- #90 design.md §8 で「登録セレモニー Issue は
  `KeyNestCredentialProviderService.onBeginCreateCredentialRequest` の空応答を
  `BeginCreateCredentialResponse.Builder().addCreateEntry(CreateEntry(...))` で
  差し替え、`CreateEntry` の pending intent は新設 `CreatePasskeyActivity` を
  起動」「AAGUID `2a56cf86-8332-4829-9f2a-e9a4adbc7abe` をここで定数化」と接合点
  が明示済み。
- #91 design.md §6.1 で `PasskeyRepository.save(SavePasskeyRequest)` および
  `findByRpIdAndUserHandle` / `findByCredentialId` / `delete` が公開 IF として
  確定済みで、本 Issue はそれをそのまま呼び出す。
- #91 で `userHandle` は BLOB (`ByteArray`) として保持 / `(rpId, userHandle)`
  UNIQUE 制約あり / Keystore wrapping key は passkey ごとに独立 alias で発番、
  までが既に確定済み。本 Issue はその上で「登録時にどのデータをどの値で
  `SavePasskeyRequest` に積むか」を確定する。
- umbrella #89 の確認事項 (人間確定済み) より本 Issue が継承する事実:
  - AAGUID = `2a56cf86-8332-4829-9f2a-e9a4adbc7abe` 固定。16 byte big-endian で
    `authenticatorData` の attestedCredentialData に埋め込む。
  - residentKey 三値 (`required` / `preferred` / `discouraged`) すべて受ける。
  - 生体認証は `BiometricPrompt` 未設定時に Device Credential (PIN/Pattern) へ
    フォールバック。
  - 表記は「PassKey」(日本語 UI 含む)。
  - minSdk = 26 維持。Credential Manager Provider は API 34+ ゲーティング
    (#90 で `@RequiresApi(34)` + `tools:targetApi="34"` 確立済み)。
  - attestation は `none` のみ (umbrella #89 Out of Scope)。
  - エクスポート禁止ポリシー (既存 credential と同一)。
- 既存資産:
  - `auth/BiometricAuthenticator.kt` は `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` で
    既に Device Credential フォールバックを実装済み。本 Issue ではこれを
    そのまま `FragmentActivity = PasskeyCreateActivity` で再利用する。
  - `security/AesGcmCipher.kt` + `security/KeystoreKeyProvider.kt` は alias 注入
    可能。`KeystoreKeyProvider(keyAlias = "keynest_passkey_<credentialId>")` を
    都度 new することで PassKey ごとの独立 wrapping key を実現できる。
  - `security/EncryptedBlob.kt` は `(iv, ciphertext)` ペアを保持しており、
    `SavePasskeyRequest` の平文 private key → AES-GCM 暗号化 → `(privateKeyIv,
    encryptedPrivateKey)` 分解の橋渡しに使える (#91 で確定)。

## ユーザーストーリー

- As a 3rd-party アプリ / ブラウザ (RP), I want
  `navigator.credentials.create({publicKey: {...}})` を呼んだとき OS Credential
  Manager の選択シートに **KeyNest が PassKey 保存先候補として表示**され、選択後に
  生体認証を経て `PublicKeyCredential` (clientDataJSON + attestationObject) が
  返ってくること, so that 自社で keypair を持たずに KeyNest を「passkey 保管庫」
  として利用できる。
- As a エンドユーザー, I want アプリ / ブラウザの「PassKey で登録」フローを進めた
  ときに、OS シートで KeyNest を選び、**生体認証 (または PIN / Pattern)** が
  通れば PassKey が KeyNest に保管されて以降は同じアプリでログインできること,
  so that パスワードを覚えなくて済む。
- As a 後続「認証セレモニー」Issue (#89 分割案 4) の実装担当, I want
  `PasskeyRepository.findByCredentialId(...)` / `findByRpIdAndUserHandle(...)`
  で取り出した PassKey の `encryptedPrivateKey` / `privateKeyIv` / `keyAlias` /
  `signCount` がすべて **本 Issue で正しく保管された形式** であること, so that
  assertion 署名側の実装で「保管時と認証時で COSE_Key / authenticatorData / 暗号化
  方式の前提が一致している」前提に乗れる。
- As a メンテナ, I want excludeCredentials 経路が必ず
  `CreateCredentialNoCreateOptionException` で OS に返り、KeyNest 側に空 entity
  が残らないこと, so that RP の重複登録防止意図を破壊しない。

## Requirements

> EARS 形式 (The X shall …, When … the X shall …, While … the X shall …, If … the
> X shall …) で記述。Issue #99 本文の Requirement 1〜6 をベースに、Issue 本文より
> 具体化したうえで、umbrella #89 確定事項 / 決定 1〜3 / #90 #91 design.md の確定
> 接合点を反映する。各 Acceptance Criteria 末尾の `(#99-R<x>.<y>)` は Issue 本文
> 番号へのトレース、`(決定 X)` は本ドキュメントの決定事項対応を示す。

### Requirement 1: keypair 生成 (#99 本文 Requirement 1)

**Objective:** As a 後続認証セレモニー Issue 担当, I want ES256 (P-256) keypair が
AndroidKeyStore (StrongBox 優先 / 通常 Keystore フォールバック) で生成され、
private key は AES-GCM 暗号化のみが DB に到達すること, so that 認証時に同じ
仕組みで private key を復号して assertion 署名に使える。

#### Acceptance Criteria

1.1. When `onBeginCreateCredentialRequest` が `BeginCreatePublicKeyCredentialRequest`
を受けて `PasskeyCreator` を呼び出したとき, the `PasskeyCreator` shall ES256 (P-256
= `KeyProperties.KEY_ALGORITHM_EC` + `ECGenParameterSpec("secp256r1")`) の keypair
を AndroidKeyStore provider で生成する。 (#99-R1.1)

1.2. The `PasskeyCreator` shall keypair 生成時に AndroidKeyStore alias を
**`keynest_passkey_<credentialId>`** 規則で指定する (決定 2)。`credentialId` は
WebAuthn の base64url 文字列であり、本 Issue 内で生成する (32 byte の乱数を
base64url-encode する想定。最終的な byte 長は design.md で確定)。

1.3. The `PasskeyCreator` shall keypair 生成 `KeyGenParameterSpec.Builder` に
`KeyProperties.PURPOSE_SIGN` を指定し、`setDigests(KeyProperties.DIGEST_SHA256)` /
`setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))` を必須で設定する。
`setUserAuthenticationRequired` は **false** とする (BiometricPrompt 制御は
`PasskeyCreateActivity` のアプリ層で行うため。既存 `KeystoreKeyProvider` の
方針と整合)。

1.4. When 端末が StrongBox に対応している (`PackageManager.FEATURE_STRONGBOX_KEYSTORE`
が true) とき, the `PasskeyCreator` shall `setIsStrongBoxBacked(true)` を有効に
した spec で keypair 生成を試みる (決定 1)。

1.5. If StrongBox 生成が `StrongBoxUnavailableException` 等で失敗したとき, the
`PasskeyCreator` shall 同一 alias / 同一 spec から `setIsStrongBoxBacked` のみ
を外した spec で keypair 生成を **再試行** する (決定 1)。再試行も失敗した場合は
`CreateCredentialException` (`CreateCredentialUnknownException` 想定、最終型は
design.md で確定) を呼び出し側に伝播する。

1.6. The `PasskeyCreator` shall 生成直後に private key を **byte 配列として取り出し**、
`AesGcmCipher(KeystoreKeyProvider(keyAlias = "keynest_passkey_<credentialId>"))`
で暗号化する。暗号化後の `(iv, ciphertext)` 以外を DB / disk / 永続ログに残さない
(NFR 1.1 / 1.2 と整合)。

1.7. The `PasskeyCreator` (もしくは呼び出し側 `PasskeyCreateActivity`) shall 暗号化
完了後、平文 private key を保持していたメモリを **明示的に上書き wipe** する
(`ByteArray.fill(0)` 相当)。生存時間は「生成 → 暗号化 → wipe」までに限定する
(NFR 1.3)。

1.8. The `PasskeyCreator` shall 生成した public key (P-256) を WebAuthn **COSE_Key
(alg = -7, kty = 2 (EC2), crv = 1 (P-256), x = 32 byte, y = 32 byte)** 形式で
CBOR encode した byte 列を返す。`attestationObject` の attestedCredentialData に
そのまま埋め込める形式とする。

### Requirement 2: AAGUID / authenticatorData / attestationObject (#99 本文 Requirement 2)

**Objective:** As a RP, I want KeyNest が返す `authenticatorData` および
`attestationObject` が WebAuthn Level 2 §6.5 仕様に準拠し、AAGUID =
`2a56cf86-8332-4829-9f2a-e9a4adbc7abe` を 16 byte big-endian で含むこと, so that
標準的な WebAuthn server / library で受理できる。

#### Acceptance Criteria

2.1. The `KeynestAaguid` shall AAGUID を `byteArrayOf(0x2a, 0x56, 0xcf, 0x86,
0x83, 0x32, 0x48, 0x29, 0x9f, 0x2a, 0xe9, 0xa4, 0xad, 0xbc, 0x7a, 0xbe)` 相当の
**16 byte big-endian (UUID 文字列の dash 区切りを除いた hex 順)** で保持する 1
箇所限定の定数とする (#89 確定事項)。 (#99-R2.1)

2.2. The `AuthenticatorData` shall 次の byte レイアウトで組み立てる
(WebAuthn Level 2 §6.1):
- `rpIdHash` = SHA-256(rpId) → 32 byte
- `flags` = `UP (0x01) | UV (0x04) | AT (0x40)` (`= 0x45`)。**residentKey に
  応じて `BE (0x08, backup eligibility)` / `BS (0x10, backup state)` フラグを
  立てるかは本 Issue では false で固定** (#89 Out of Scope: cloud sync / 端末間
  transfer 無し)
- `signCount` = `0x00 0x00 0x00 0x00` (4 byte big-endian, **初期値 0**)
- `attestedCredentialData` = `AAGUID (16 byte) || credentialIdLength (2 byte big-endian)
  || credentialId (length byte) || publicKey COSE_Key`
- `extensions` = なし
 (#99-R2.1, #99-R2.3)

2.3. The `AuthenticatorData.flags` shall `AT` (attestedCredentialData present) ビット
(`0x40`) を必ず立てる (登録セレモニーは attestedCredentialData 必須)。 (#99-R2.1)

2.4. The `AuthenticatorData.signCount` shall **初期値 0** で encode される
(`0x00000000`)。signCount 運用方針 (常時 0 固定 / RP ごとにインクリメント) の
最終確定は認証セレモニー Issue (#89 分割案 4) に委ねる (#91 Non-Goal でも明示)。
本 Issue では「初期値 0 で書き込む」までを確定する。 (#99-R2.3)

2.5. The `attestationObject` shall WebAuthn Level 2 §6.5.4 の CBOR map として
encode され、次のキーを持つ:
- `fmt` = `"none"` (テキスト文字列)
- `attStmt` = `{}` (空の CBOR map)
- `authData` = Requirement 2.2 で組み立てた byte 列
 (#99-R2.2)

2.6. The `PasskeyCreator` shall `attestationObject` を `PublicKeyCredential` の
response (`registrationResponseJson` 内 `attestationObject` フィールド) として
返却する形に整形して `PasskeyCreateActivity` に渡す。最終的に
`PendingIntentHandler.setCreateCredentialResponse(...)` 経由で OS / RP に返却される。

### Requirement 3: discoverable / non-discoverable 分岐 (#99 本文 Requirement 3)

**Objective:** As a RP, I want `residentKey` 指定に応じて KeyNest が
`PasskeyEntity.isDiscoverable` を正しく振り分け、後続の認証セレモニーで
usernameless login (discoverable) と `allowCredentials` 必須 (non-discoverable)
の両モードが正しく区別されること, so that 標準的な WebAuthn フローに従う。

#### Acceptance Criteria

3.1. When `BeginCreatePublicKeyCredentialRequest` の `residentKey = "required"` または
`"preferred"` であるとき, the registration flow shall `PasskeyEntity.isDiscoverable
= true` で `PasskeyRepository.save(...)` を呼び出す (#89 確定事項: 両対応)。
(#99-R3.1)

3.2. When `BeginCreatePublicKeyCredentialRequest` の `residentKey = "discouraged"`
であるとき, the registration flow shall `PasskeyEntity.isDiscoverable = false`
で `PasskeyRepository.save(...)` を呼び出す。 (#99-R3.2)

3.3. If `residentKey` が省略されているとき, the registration flow shall WebAuthn
Level 2 §5.4.6 の default に従い **`"preferred"`** 相当として扱い、結果として
`isDiscoverable = true` で保存する。 (#99-R3.1 補強)

3.4. The registration flow shall `userHandle` / `userName` / `userDisplayName` /
`rpId` / `rpDisplayName` を **両モード共通で `PasskeyEntity` に保存** する
(`userHandle` は RP から渡された byte 配列をそのまま、最大 64 byte。両モードで
保存する旨は #89 確定事項 + #91 schema で確定済み)。 (#99-R3.3)

3.5. The `credentialId` shall 本 Issue で生成され、`PasskeyEntity.credentialId`
(PK) として保存される。生成方式は「32 byte の `SecureRandom` 乱数を base64url-encode」
を想定する (最終 byte 長と encoding は design.md で確定)。

### Requirement 4: 生体認証 (#99 本文 Requirement 4)

**Objective:** As a エンドユーザー, I want PassKey 生成の直前に生体認証 (または
Device Credential) を要求され、認証を通らない限り private key が生成・保存
されないこと, so that 端末の所有者以外が無断で KeyNest に PassKey を追加できない。

#### Acceptance Criteria

4.1. When `PasskeyCreateActivity` が `CreateEntry` の pending intent 経由で
起動したとき, the activity shall 確認画面 (RP 名 / userDisplayName / 「PassKey を
保存しますか？」相当の表記) を表示する (UI 詳細は design.md で確定。本 Issue は
**最小限の確認画面** のみ。#89 分割案 6 の rename UI は別 Issue)。

4.2. When ユーザーが確認画面で「保存」を選択したとき, the activity shall 既存
`BiometricAuthenticator.authenticate(title, subtitle)` を `BIOMETRIC_STRONG or
DEVICE_CREDENTIAL` allowed authenticators 構成で起動する。 (#99-R4.1)

4.3. If 端末で BiometricPrompt が利用不能で Device Credential (PIN/Pattern/Password)
が設定されているとき, the `BiometricAuthenticator` shall Device Credential への
フォールバックを実行する (既存実装の `Authenticators.DEVICE_CREDENTIAL` ビットで
自動的に達成される。#89 確定事項)。 (#99-R4.2)

4.4. If `BiometricAuthenticator.authenticate(...)` が `AuthResult.Succeeded`
以外 (`Cancelled` / `Failed` / `Unavailable`) を返したとき, the registration
flow shall (a) `PasskeyCreator.generate(...)` を呼ばず、(b) `PasskeyRepository.save(...)`
を呼ばず、(c) OS に対しては `outcome.onError(CreateCredentialException)` を返す。
具体的な例外型は `Cancelled` → `CreateCredentialCancellationException` /
`Failed` および `Unavailable` → `CreateCredentialUnknownException` を **想定**
する (最終確定は design.md)。 (#99-R4.3)

4.5. The registration flow shall 生体認証成功後に **同一 ActivityScope 内で
keypair 生成 → 暗号化 → DB 保存** を完了させる。Activity が認証中に
`onDestroy` した場合は `outcome.onError(CreateCredentialCancellationException)`
相当で OS に返却し、空 entity / 半端な Keystore alias を残さない (未解決事項
として後段で再掲)。

### Requirement 5: 重複検知 / excludeCredentials (#99 本文 Requirement 5)

**Objective:** As a RP, I want 同一 `(rpId, userHandle)` の PassKey が既存の
場合は新規 keypair で上書きされ、`excludeCredentials` で指定された credentialId が
KeyNest 内に存在するときは登録自体が拒否されること, so that RP の重複防止意図を
KeyNest が破壊しない。

#### Acceptance Criteria

5.1. When `PasskeyRepository.findByRpIdAndUserHandle(rpId, userHandle)` (#91
公開 IF) が non-null を返した状態で `PasskeyRepository.save(...)` を呼んだとき,
the registration flow shall **既存 row を新規 keypair / 新規 credentialId で
上書き** する (RP 仕様準拠)。具体的には:
- (a) 旧 row を `PasskeyRepository.delete(oldCredentialId)` で削除し (#91 の
  `delete` は対応 Keystore alias も同時廃棄する)、
- (b) その後 `PasskeyRepository.save(SavePasskeyRequest)` で新 credentialId の
  row を新規 INSERT する。
- (c) この (a)→(b) の順序および「(a) が成功した場合のみ (b) に進む」整合性
  境界は design.md で確定する (未解決事項)。
 (#99-R5.1)

5.2. If `BeginCreatePublicKeyCredentialRequest` の **`excludeCredentials`** に
指定された credentialId のいずれかが `PasskeyRepository.findByCredentialId(...)`
で non-null を返すとき, the registration flow shall (a) keypair 生成 / 暗号化 /
DB 保存をすべてスキップし、(b) `outcome.onError(CreateCredentialNoCreateOptionException)`
を OS に返す (決定 3 = Option B)。ユーザー向けエラーメッセージ UI / 文言は
**本 Issue では実装しない** (carve-out)。 (#99-R5.2)

5.3. The `excludeCredentials` チェックは Requirement 4 の生体認証起動 **前** に
実行する。生体認証を要求してから「実は exclude されていた」と気づくのは UX 上
最悪 (ユーザーが指紋を読んでから蹴られる) なので、`onBeginCreateCredentialRequest`
の段階で OS に `CreateCredentialNoCreateOptionException` を返すか、または
`PasskeyCreateActivity` 起動直後の最初期で返す (最終確定は design.md)。

5.4. The registration flow shall Requirement 5.1 の上書き処理 (a) で旧 row の
削除に失敗した場合、(b) を実行せず `outcome.onError(CreateCredentialUnknownException)`
で OS に返す (silent fail 禁止 / NFR 1.5 と整合)。

### Requirement 6: テスト (#99 本文 Requirement 6)

**Objective:** As a メンテナ, I want keypair 生成 / `authenticatorData` 組み立て /
residentKey 分岐 / excludeCredentials 経路の振る舞いが自動テストで回帰検知できる
こと, so that 後続認証セレモニー Issue 実装中に登録セレモニーの退行を早期に
検知できる。

#### Acceptance Criteria

6.1. The `PasskeyCreatorTest` (Robolectric, `@Config(sdk = [34])`) shall:
- (a) ES256 (P-256) keypair が AndroidKeyStore provider で生成されること
  (Robolectric の shadow `AndroidKeyStore` でアサート可能な範囲。実 StrongBox
  挙動は Robolectric ではモック)。
- (b) 生成された public key を COSE_Key として encode した byte 列が、
  `alg = -7` / `kty = 2` / `crv = 1` / `x` 32 byte / `y` 32 byte を含む
  CBOR map になっていることを bytewise で検証。
- (c) `setIsStrongBoxBacked(true)` 試行 → 失敗時に通常 Keystore へ
  フォールバックすることを、`KeyGenParameterSpec` のキャプチャ or 例外注入で
  検証。
 (#99-R6.1)

6.2. The `AuthenticatorDataTest` (純 JVM unit test、Robolectric 不要) shall:
- (a) AAGUID が `2a56cf86-8332-4829-9f2a-e9a4adbc7abe` の 16 byte big-endian
  並びと完全一致すること (`KeynestAaguid` 経由)。
- (b) `signCount` の初期値が `0x00000000` (4 byte big-endian) であること。
- (c) `flags` バイトが `0x45` (UP | UV | AT) であること (Requirement 2.2 と整合)。
- (d) `rpIdHash` が `MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray(UTF_8))`
  と一致する 32 byte であること。
- (e) `credentialIdLength` が 2 byte big-endian で credentialId の byte 長と
  一致すること。
 (#99-R6.2)

6.3. The `KeyNestCredentialProviderServiceTest` (#90 で導入された Robolectric テスト
を **拡張**) shall:
- (a) `residentKey = "required"` / `"preferred"` の各値で
  `outcome.onResult(BeginCreateCredentialResponse)` が 1 件以上の `CreateEntry`
  を含むこと、かつ後段で `PasskeyEntity.isDiscoverable = true` で `save` される
  こと (これは Repository を mock してアサート)。
- (b) `residentKey = "discouraged"` で `PasskeyEntity.isDiscoverable = false`
  で `save` されること。
- (c) `excludeCredentials` に既存 credentialId を含む request では
  `outcome.onError(CreateCredentialNoCreateOptionException)` が 1 回だけ呼ばれ、
  `Repository.save` が **呼ばれないこと**。
 (#99-R6.3)

6.4. The 既存 `KeyNestCredentialProviderServiceTest` (空応答検証 / #90 で導入)
shall 本 Issue 変更後も `onBeginGetCredentialRequest` / `onClearCredentialStateRequest`
の空応答検証が引き続き pass する (本 Issue は `onBeginCreateCredentialRequest` のみ
差し替えるため)。

6.5. The 既存 `Migration_4_5_Test` / `PasskeyDaoTest` / `PasskeyRepositoryTest`
(#91 で導入) shall 本 Issue 変更後も全件 pass する。本 Issue は #91 のスキーマや
DAO に手を入れないため、原則影響なし (NFR 3 と整合)。

6.6. The 既存 `CredentialProviderServiceManifestTest` / `CredentialProviderXmlTest`
(#90 で導入) shall 本 Issue で `PasskeyCreateActivity` を AndroidManifest に追加
した後も pass する (本 Issue が触るのは `<activity>` 追加のみで、`<service>`
ブロックは変更しない)。

## Non-Functional Requirements

### NFR 1: セキュリティ — private key の Keystore 外露出禁止

1.1. The PassKey 用 EC private key shall **AndroidKeyStore provider 上で生成**
される。`KeyPairGenerator.getInstance("EC", "AndroidKeyStore")` 経由のみ。
ソフトウェア乱数 / `BouncyCastle` / 平文 PEM 入出力は禁止 (Requirement 1.1 と
整合)。

1.2. The PassKey 用 wrapping key (AES-256-GCM) shall AndroidKeyStore provider 上で
`keynest_passkey_<credentialId>` alias で生成 / 取得され、raw key bytes が TEE
の外に出ない (既存 `KeystoreKeyProvider` 既定値: `setRandomizedEncryptionRequired(true)`,
`setUserAuthenticationRequired(false)` を継承)。 (決定 2 / Requirement 1.6 と整合)

1.3. The PassKey 用 EC private key を `AesGcmCipher` に渡すための **平文 byte 配列**
は、暗号化完了直後に `ByteArray.fill(0)` 相当で wipe される。Activity / Service
の field / 静的フィールドに保持されないこと。 (Requirement 1.7 と整合)

1.4. The KeyNest shall PassKey private key 平文 / wrapping key raw / `credentialId`
raw value / `userHandle` raw value を **logcat に info 以上のレベルで出力しない**
(既存 `SafeLogger` 慣行を継承)。debug レベルでもサイズ表記のみで raw を出さない
方針を design.md で確定する。

1.5. If 暗号化 / 復号 / Keystore I/O が失敗したとき, the registration flow shall
silent fail せず例外を `outcome.onError(CreateCredentialException)` に伝播する
(Requirement 5.4 と整合)。

### NFR 2: 既存テスト非破壊

2.1. The 本 Issue 変更 shall 既存 `KeyNestAutofillService` 関連テスト
(`FillResponseBuilderTest` / `LockedFillResponseSecurityTest` /
`CustomFieldFillResponseTest` 等) に影響を与えない (本 Issue は autofill 経路を
触らない)。

2.2. The 本 Issue 変更 shall #90 の `CredentialProviderServiceManifestTest` /
`CredentialProviderXmlTest` / `KeyNestCredentialProviderServiceTest` (空応答部分)
を破壊しない (Requirement 6.4 / 6.6 と整合)。

2.3. The 本 Issue 変更 shall #91 の `Migration_4_5_Test` / `PasskeyDaoTest` /
`PasskeyRepositoryTest` を破壊しない (Requirement 6.5 と整合)。

### NFR 3: minSdk / API ゲーティング

3.1. The `minSdkVersion` shall **26 を維持** する (umbrella #89 / #90 と整合)。

3.2. The `PasskeyCreator` / `PasskeyCreateActivity` / `KeynestAaguid` 等の本 Issue
新規追加クラス shall `@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)` (= 34)
で API 34+ ゲーティングする (#90 の二段防御方針を継承)。

3.3. The `PasskeyCreateActivity` の `<activity>` 宣言 shall `tools:targetApi="34"`
を付与する (#90 の Manifest 流儀と整合)。

3.4. The `KeyNestAutofillService` および既存 password credential 経路の `minSdk
= 26` 動作 shall 本 Issue 変更後も影響を受けない (#90 NFR を継承)。

### NFR 4: ネット境界の不変

4.1. The 本 Issue 変更 shall `android.permission.INTERNET` を追加しない (umbrella
#89 のネット境界ポリシー)。RP との通信は呼び出し側のアプリ / ブラウザが担う。

4.2. The 本 Issue 変更 shall WebAuthn server (RP) との直接通信を行わない
(`PasskeyCreator` は pure local crypto / encoding のみ)。

### NFR 5: パフォーマンス / UI ブロッキング許容範囲

5.1. The keypair 生成 (StrongBox 試行含む) / AES-GCM 暗号化 / Room INSERT shall
**`Dispatchers.IO`** で実行され、`PasskeyCreateActivity` の UI thread を blocking
しない (#91 の `PasskeyRepositoryImpl` がすでに `withContext(Dispatchers.IO)`
を持つことを前提とする)。

5.2. The BiometricPrompt 表示から keypair 生成 → DB 保存 → OS 応答までの一連
処理の所要時間 shall **3 秒以内** を目標とする (StrongBox 端末で keypair 生成
自体が 1〜2 秒かかるケースあり)。3 秒を超える場合は spinner / progress indicator
を表示する (UI 詳細は design.md)。

5.3. The 本 Issue shall `onBeginCreateCredentialRequest` の callback 内では
**重い処理を行わず** (keypair 生成は `PasskeyCreateActivity` に委譲)、すぐに
`outcome.onResult(BeginCreateCredentialResponse(CreateEntry...))` を返す。
これは Credential Manager の API 34+ 仕様 (callback は速やかに `outcome` を
解決すべし) と整合する。

### NFR 6: 命名 / 表記

6.1. The 新規追加クラス名 / KDoc / コメント / ログメッセージ shall PassKey 機能に
言及する箇所で **「PassKey」** 表記を用いる (#89 確定事項を継承)。

6.2. The package 配置 shall `credentialprovider/registration/` 配下に集約される
(#90 design.md §8 の接合点)。authenticator (認証セレモニー) は別 Issue で
`credentialprovider/authentication/` 配下に集約される予定。

## データモデル / 公開 IF への影響

### #91 PasskeyEntity / PasskeyRepository への影響

- 本 Issue は **#91 で確定済みの `passkeys` テーブル schema (v5) / `PasskeyDao`
  公開 IF / `PasskeyRepository` 公開 IF に追加変更を加えない**。
  - `save(SavePasskeyRequest)`: 本 Issue が呼び出す主 API。`SavePasskeyRequest`
    には `credentialId` / `rpId` / `rpDisplayName` / `userHandle` / `userName` /
    `userDisplayName` / `isDiscoverable` / `privateKey` (平文 byte。Repository
    内部で AES-GCM 暗号化) / `keyAlias` / `signCount` / `displayName` /
    `createdAt` を渡す。
  - `findByCredentialId(credentialId)`: excludeCredentials 判定で使用 (Requirement
    5.2)。
  - `findByRpIdAndUserHandle(rpId, userHandle)`: 上書き判定で使用 (Requirement 5.1)。
  - `delete(credentialId)`: 上書き時の旧 row 削除で使用 (Requirement 5.1)。
- ただし Issue #99 本文 (Requirement 1) に
  「`AesGcmCipher` 暗号化は呼び出し側 (本 Issue 側) で行うか、Repository 側で
  行うか」の選択肢が **既に #91 design.md §6.2 で「Repository 側で行う」と確定
  済み** (`PasskeyRepositoryImpl.save` 内で `cipher.encrypt(request.privateKey)`)。
  したがって本 Issue では平文 `ByteArray` を `SavePasskeyRequest.privateKey` に
  詰めて渡せばよく、暗号化は Repository に委ねる方針で確定する。

### 新規定数 `KEYNEST_AAGUID` の配置先

- **配置先**: `app/src/main/java/.../credentialprovider/registration/KeynestAaguid.kt`
  に object 単体ファイルで配置する。
- **理由**: #90 design.md §9.1-6 で「AAGUID 定数は登録セレモニー Issue (#89 分割案 3
  = 本 Issue) で attestation 実装時に追加する」と明示。`credentialprovider/registration/`
  配下に集約することで、認証セレモニー Issue (`credentialprovider/authentication/`)
  からも参照される将来に備える。
- **公開 API**:
  ```kotlin
  internal object KeynestAaguid {
      val BYTES: ByteArray = byteArrayOf(...) // 16 byte big-endian
      const val UUID_STRING: String = "2a56cf86-8332-4829-9f2a-e9a4adbc7abe"
  }
  ```
  - `BYTES` は `defensiveCopy()` 経由で外部公開する (`ByteArray` の mutability
    対策。最終形は design.md)。

### 新規定数 / 命名規約のまとめ

| 名前 | 値 / 形式 | 配置 | 出所 |
|---|---|---|---|
| `KeynestAaguid.BYTES` | 16 byte (`2a56cf86-...`) | `credentialprovider/registration/KeynestAaguid.kt` | #89 確定事項 |
| Keystore alias 命名 | `keynest_passkey_<credentialId>` | `PasskeyCreator` / `PasskeyRepositoryImpl` (#91 と要整合) | 決定 2 |
| activity name | `.credentialprovider.registration.PasskeyCreateActivity` | AndroidManifest.xml | 本 Issue |
| COSE_Key alg | `-7` (ES256) | `PasskeyCreator` 内定数 | WebAuthn Level 2 §6.5.1 |

### CBOR encoding library

- WebAuthn `attestationObject` および `authenticatorData` 内 COSE_Key の CBOR
  encode に使うライブラリの選定は本 Issue の依存追加事項。
- 候補:
  1. `com.upokecenter:cbor` (純 Kotlin / Java で軽量)。
  2. `co.nstant.in:cbor` (Bouncy Castle 系で広く実績)。
  3. 自前 minimal CBOR encoder (本 Issue で必要な subset = uint / bstr / map /
     int は限定的なので、外部依存を増やさない選択もあり)。
- 本 Issue では **(3) 自前 minimal encoder** を **第一候補**として残す
  (umbrella #89 の「依存追加は最小限」方針 / Issue #90 の credentials 依存追加
  以外を増やさない流儀と整合)。最終確定は **未解決事項** として後段で再掲。

## 処理フロー要点

> 詳細フロー (mermaid / state machine / pending intent payload schema) は
> design.md で確定する。本セクションでは責務分担と「どのクラスが何を担当する
> か」だけを示す。

### Step 1: OS → Service callback

```
Browser/App
  └─> CredentialManager.createCredential(CreatePublicKeyCredentialRequest)
        └─> CredentialManager System Service
              └─> KeyNestCredentialProviderService.onBeginCreateCredentialRequest(req, signal, outcome)
                    ├─ excludeCredentials を PasskeyRepository.findByCredentialId(...) で照合
                    │  └─ 1 件でも hit → outcome.onError(CreateCredentialNoCreateOptionException) で終了
                    └─ そうでなければ BeginCreateCredentialResponse に CreateEntry を 1 件以上積んで
                       outcome.onResult(response)
```

責務:
- `KeyNestCredentialProviderService` 本体は **重い処理を行わない** (NFR 5.3)。
- `CreateEntry` の pending intent は `PasskeyCreateActivity` を起動する Intent を
  ラップする。

### Step 2: ユーザーが KeyNest を選択 → Activity 起動

```
OS Credential Manager UI
  └─> User taps "KeyNest"
        └─> CreateEntry.pendingIntent.send()
              └─> PasskeyCreateActivity.onCreate()
                    ├─ PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
                    ├─ 確認画面 (RP 名 / userDisplayName) を表示
                    ├─ ユーザーが「保存」を選択 → BiometricAuthenticator.authenticate(...)
                    ├─ AuthResult.Succeeded → PasskeyCreator.generate(request) を IO で実行
                    ├─ PasskeyRepository.save(SavePasskeyRequest) を呼ぶ
                    │  ├─ 既存 (rpId, userHandle) row があれば先に delete(oldCredentialId)
                    │  └─ 新 row を INSERT (AES-GCM 暗号化は Repository が行う)
                    └─ PendingIntentHandler.setCreateCredentialResponse(
                         resultIntent,
                         CreatePublicKeyCredentialResponse(registrationResponseJson)
                       ) → setResult(RESULT_OK, resultIntent) → finish()
```

責務:
- `PasskeyCreateActivity` = UI / lifecycle 管理 + BiometricPrompt 起動。
- `PasskeyCreator` = ES256 keypair 生成 + COSE_Key encode + authenticatorData 組み立て
  + attestationObject encode。
- `PasskeyRepository` (#91 既存) = AES-GCM 暗号化 + Room INSERT + Keystore alias
  管理。

### Step 3: OS / RP へ応答

```
PasskeyCreateActivity.finish() (RESULT_OK)
  └─> OS Credential Manager
        └─> Browser/App's CreateCredentialResponse callback fires
              └─> RP server に registration response (clientDataJSON + attestationObject) を送信
```

責務:
- KeyNest 側は `attestationObject` を組み立てて OS に返却するまで。RP server との
  通信は呼び出し側のアプリ / ブラウザ。

## 既存資産との接続

| 既存資産 | 本 Issue での使い方 |
|---|---|
| `KeyNestCredentialProviderService` (#90) | `onBeginCreateCredentialRequest` の空応答 (`BeginCreateCredentialResponse()`) を `CreateEntry` 入りの `BeginCreateCredentialResponse.Builder().addCreateEntry(...).build()` に差し替え。`onBeginGetCredentialRequest` / `onClearCredentialStateRequest` は **触らない** |
| `PasskeyRepository` (#91) | `save(SavePasskeyRequest)` を呼ぶ (主要 path)。`findByCredentialId(credentialId)` を excludeCredentials 判定で呼ぶ。`findByRpIdAndUserHandle(rpId, userHandle)` / `delete(credentialId)` を上書き判定で呼ぶ |
| `PasskeyEntity` / `PasskeyDao` (#91) | 直接は触らない (Repository 経由) |
| `auth/BiometricAuthenticator` | そのまま再利用。`FragmentActivity` (= `PasskeyCreateActivity`) を渡す。`AuthResult.Succeeded` 以外は CreateCredentialException 経路 |
| `security/AesGcmCipher` | #91 `PasskeyRepositoryImpl.save` が内部利用するので本 Issue から直接呼ぶ必要なし |
| `security/KeystoreKeyProvider` | 同上。alias 命名規則 `keynest_passkey_<credentialId>` は #91 design.md / impl と本 Issue の整合を取る必要あり (未解決事項) |
| `security/EncryptedBlob` | 同上 (#91 Repository 内部で `(iv, ciphertext)` 構築に利用) |
| 既存 `KeyNestAutofillService` / autofill 経路 | 本 Issue は触らない |
| 既存 `CredentialEntity` / `CredentialDao` / password credential 経路 | 本 Issue は触らない |
| Manifest `<service>` (#90) | 本 Issue は触らない。新規 `<activity>` のみ追加 |
| `res/xml/credential_provider.xml` (#90) | 本 Issue は触らない (TYPE_PUBLIC_KEY_CREDENTIAL のみで充足) |

## 既存テストへの影響 (再掲)

- `KeyNestCredentialProviderServiceTest` (#90 で導入): 空応答テスト部分は
  Requirement 6.4 のとおり pass 維持。`onBeginCreateCredentialRequest` の検証
  部分は本 Issue で **拡張** する。
- `CredentialProviderServiceManifestTest` / `CredentialProviderXmlTest` (#90):
  本 Issue で `<activity>` 追加のみ、`<service>` 不変なので影響なし
  (Requirement 6.6)。
- `Migration_4_5_Test` / `PasskeyDaoTest` / `PasskeyRepositoryTest` (#91):
  schema / DAO / Repository に触らないので影響なし (Requirement 6.5)。
- `InternetPermissionAbsenceTest`: `INTERNET` を追加しない方針継承 (NFR 4.1) で
  影響なし。
- 既存 `KeyNestAutofillService` 関連テスト: autofill 経路を触らないので影響なし
  (NFR 2.1)。

## テスト要件 (Requirement 6 の補足 / 具体化)

| Test class | 種別 | SDK | 検証ポイント | 要件対応 |
|---|---|---|---|---|
| `PasskeyCreatorTest` | Robolectric | 34 | ES256 keypair 生成 / COSE_Key (alg=-7 / kty=2 / crv=1 / x,y 32 byte) bytewise / StrongBox フォールバック | 6.1 / 1.x |
| `AuthenticatorDataTest` | 純 JVM unit | — | AAGUID 16 byte / rpIdHash 32 byte / flags 0x45 / signCount 0x00000000 / credentialIdLength | 6.2 / 2.x |
| `KeyNestCredentialProviderServiceTest` (拡張) | Robolectric | 34 | residentKey 三値での isDiscoverable 振り分け / excludeCredentials hit で `CreateCredentialNoCreateOptionException` / 空応答テスト残置 | 6.3 / 6.4 / 3.x / 5.2 |
| `PasskeyCreateActivityTest` (任意 / design で要否確定) | Robolectric | 34 | BiometricAuthenticator.authenticate succeed / cancel / unavailable の各経路で OS response が `setCreateCredentialResponse` / `onError` どちらに行くか | 4.x |

CI / API 34 emulator 整備状況に応じて Instrumentation test (`@SdkSuppress(minSdkVersion = 34)`)
を `@Ignore` placeholder で追加するか否かは design.md で確定する (#90 と同方針)。

## 未解決事項 / 確認事項

> 本 Issue の確認事項 1〜3 は人間確定済み (本ドキュメント「決定事項」セクション)。
> 以下は design.md / 実装フェーズで詰める細部のみ列挙する。

1. **`PasskeyCreateActivity` の lifecycle と CredentialProviderService の outcome
   receiver 連携**:
   - `onBeginCreateCredentialRequest` の `outcome` は callback メソッドが return
     した時点で完了する。`PasskeyCreateActivity` 起動 → ユーザー操作 → 結果返却
     は **`CreateEntry` の pending intent → `PendingIntentHandler.setCreateCredentialResponse(...)`
     → `setResult(RESULT_OK)` → `finish()`** 経路で別経由になる (`outcome` は
     Service callback では使わない)。この前提を design.md で正式に確定する。
   - Activity 異常終了 (`onDestroy` 中断 / プロセス kill) 時に `outcome` も
     `setResult` も発火しない場合、OS / RP 側は何秒でタイムアウトするか / KeyNest
     側はどうハンドルするか。

2. **Requirement 5.1 (上書き) の `delete` → `save` トランザクション境界**:
   - `PasskeyRepository.delete(oldCredentialId)` 成功 → `PasskeyRepository.save(...)`
     失敗 のケースで、旧 row が消えてしまい新 row も入らない状態
     (= ユーザーから見ると PassKey が一時的に消える) を許容するか、Repository に
     `saveOrReplaceByRpIdAndUserHandle(...)` のような **トランザクション API** を
     追加して #91 に back-port するか。
   - #91 design.md には現状 atomic な replace API はない。本 Issue の design.md
     で「呼び出し側で逐次実行 + 失敗時はリトライ可能例外」とするか、Repository
     拡張を出すかを決める。

3. **`keynest_passkey_<credentialId>` (本 Issue 決定 2) vs `passkey_<credentialId>`
   (#91 既存) の alias 命名整合**:
   - Issue #99 本文 Option A = `keynest_passkey_<credentialId>` を人間確定。
   - #91 design.md / impl-notes.md / コードは `passkey_<credentialId>` で確定済み。
   - 整合方法は design.md で確定 (推奨: #91 側を `keynest_passkey_<credentialId>`
     に揃える)。

4. **`attestationObject` の CBOR encoding library 選定**:
   - 自前 minimal encoder / `com.upokecenter:cbor` / `co.nstant.in:cbor` の 3 案。
   - 本 requirements 推奨は「自前 minimal encoder」(依存追加最小化)。最終確定は
     design.md。

5. **`credentialId` の生成方式**:
   - 32 byte の `SecureRandom` 乱数 → base64url encode を想定 (Requirement 3.5)。
   - 16 byte / 32 byte / 64 byte のどれにするか、padding の `=` を残すか除くか
     は design.md で確定。

6. **`excludeCredentials` チェックを Service 側で実行するか Activity 側で実行するか**:
   - Requirement 5.3 で「生体認証起動前」までは確定済み。
   - Service 側で実行する場合は `outcome.onError` で完結し Activity 起動なし
     (UX 最良)。
   - Activity 側で実行する場合は `setResult(RESULT_OK, resultIntent)` に
     `PendingIntentHandler.setCreateCredentialException(...)` で例外を入れる。
   - どちらを採用するか design.md で確定。

7. **CBOR / COSE_Key の uint vs negative int encoding 細部**:
   - COSE_Key の `alg = -7` (ES256) は CBOR の negative int (major type 1) で
     encode する必要があり、`-1 - n` の bit pattern 規則がある。テスト
     (`AuthenticatorDataTest`) は bytewise で検証するため、encoder 実装の正確性が
     critical。design.md でテストベクタを WebAuthn 仕様 §6.5.1.1 から複写する。

8. **`CreateEntry` の displayName / accountName / description**:
   - OS Credential Manager UI に表示される文言。RP の `rpDisplayName` /
     `userDisplayName` をどう組み合わせるか、locale 別に翻訳するか
     (`res/values/strings.xml` / `res/values-ja/strings.xml`) は design.md で確定。
   - 本 Issue では「PassKey 機能の文言は『PassKey』表記」(#89 確定) を maintain
     する範囲のみ requirement で確定済み。

9. **`androidx.credentials` バージョン**:
   - #90 で `1.5.0` 確定済み。本 Issue で `CreateEntry` / `PendingIntentHandler` /
     `BeginCreatePublicKeyCredentialRequest` / `CreatePublicKeyCredentialResponse`
     / `CreateCredentialNoCreateOptionException` の各 API が 1.5.0 で安定提供
     されているかを design.md で再確認する (現状の認識では `1.5.0` で揃っている)。

## carve-out (本 Issue から外した案件)

| 案件 | 行き先 | 本 Issue (#99) への影響 |
|---|---|---|
| excludeCredentials エラー時のユーザー向けメッセージ UI / 文言策定 | 別 Issue (本 Issue 着手後に人間が起票予定) | 本 Issue は `CreateCredentialNoCreateOptionException` を OS に返すまでで完了 (決定 3 / Requirement 5.2) |
| signCount 運用方針の最終確定 (常時 0 固定 / インクリメント) | 認証セレモニー Issue (#89 分割案 4) | 本 Issue では初期値 0 で書き込むまでを確定 (Requirement 2.4) |
| 一覧 UI への PassKey 表示 | #89 分割案 5 | 本 Issue では UI 側に何も追加しない |
| 個別管理 UI (rename / 削除) | #89 分割案 6 | 同上 |
| 設定画面 / OS 設定導線 | #89 分割案 7 | 同上 |
| README / Privacy / Support docs 更新 | #89 分割案 8 | 同上 |
| direct / packed attestation 形式 | 別 Issue (umbrella #89 Out of Scope) | 本 Issue は `fmt = "none"` のみ |
| CI に API 34 emulator を導入し instrumentation test を実行可能にする | #94 (#90 から carve-out 済み) | 本 Issue でも Robolectric 中心。Instrumentation test 追加要否は design.md |

## 用語集

- **登録セレモニー (registration ceremony)**: WebAuthn の credential 作成手順全体。
  RP から `PublicKeyCredentialCreationOptions` を受け、authenticator が keypair を
  生成し、`attestationObject` を返す。本 Issue が実装する範囲。
- **認証セレモニー (authentication ceremony)**: WebAuthn の credential 利用手順
  (assertion 署名)。本 Issue では実装しない (#89 分割案 4)。
- **AAGUID**: Authenticator Attestation GUID。KeyNest authenticator を識別する
  128 bit UUID。`2a56cf86-8332-4829-9f2a-e9a4adbc7abe` 固定。
- **`authenticatorData`**: WebAuthn Level 2 §6.1 で定義される binary 構造。
  `rpIdHash` (32) + `flags` (1) + `signCount` (4) + `attestedCredentialData` 可変長
  + `extensions` 可変長。
- **`attestedCredentialData`**: `authenticatorData` の一部。`AAGUID` (16) +
  `credentialIdLength` (2) + `credentialId` 可変長 + `publicKey` (COSE_Key) で
  構成される。
- **`attestationObject`**: WebAuthn Level 2 §6.5 で定義される CBOR map
  (`fmt` / `attStmt` / `authData`)。本 Issue では `fmt = "none"` / `attStmt = {}`。
- **COSE_Key**: CBOR Object Signing and Encryption の鍵表現 (RFC 9052)。本 Issue で
  使う ES256 は `kty = 2 (EC2)` / `alg = -7` / `crv = 1 (P-256)` / `x` 32 byte /
  `y` 32 byte の CBOR map。
- **discoverable credential (resident key)**: `allowCredentials` 無しで RP に
  提示できる PassKey。`isDiscoverable = true` の row。
- **non-discoverable credential**: `allowCredentials` 必須の PassKey。
  `isDiscoverable = false` の row。
- **StrongBox**: Android Keystore のハードウェアバックアップ機能。Pixel 3 以降など
  対応端末でのみ利用可能。`PackageManager.FEATURE_STRONGBOX_KEYSTORE` で判定。
- **`CreateEntry`**: `androidx.credentials.provider.CreateEntry`。OS Credential
  Manager UI に「KeyNest にこの PassKey を保存」候補として表示される行。
- **`CreateCredentialNoCreateOptionException`**: `androidx.credentials.exceptions.
  CreateCredentialNoCreateOptionException`。「この request では PassKey を作成でき
  ない (例: excludeCredentials で除外されている)」を OS に伝える例外。

## 変更履歴 / 出典

- **本 requirements の根拠** (作成日: 2026-05-22):
  - Issue #99 本文 (Requirement 1〜6 / 確認事項 1〜3) — `gh issue view 99` で
    取得した本文。
  - Issue #99 コメント (人間確定): 「1, 2, 3 すべて推奨案でかまいません」と明示
    回答済み。これにより:
    - 確認事項 1 (StrongBox) → Option A (二段フォールバック) 確定 = 決定 1。
    - 確認事項 2 (Keystore alias 命名) → Option A
      (`keynest_passkey_<credentialId>`) 確定 = 決定 2。
    - 確認事項 3 (excludeCredentials UX) → Option B (別 Issue へ carve out) 確定
      = 決定 3。
  - Parent Issue #89 本文 / 確認事項 1〜7 — AAGUID 値 / residentKey 両対応 /
    Device Credential フォールバック / 表記「PassKey」/ minSdk 26 維持 / attestation
    `none` / エクスポート禁止が確定済み。
  - 依存 Issue #90 design.md §2 / §4 / §8 / §9 — `KeyNestCredentialProviderService`
    package 配置 / `androidx.credentials` 1.5.0 / `@RequiresApi(34)` /
    `tools:targetApi="34"` / 後続 Issue 接合点 (本 Issue が
    `onBeginCreateCredentialRequest` を差し替える前提) を継承。
  - 依存 Issue #91 design.md §3 / §5 / §6 — `PasskeyEntity` schema / `PasskeyDao`
    公開 IF / `PasskeyRepository.save / findByCredentialId / findByRpIdAndUserHandle
    / delete / loadPrivateKey` API シグネチャ / Repository が AES-GCM 暗号化境界を
    持つこと / Keystore alias 命名 (`passkey_<credentialId>`) を継承。
    なお #91 の alias 命名は本 Issue の決定 2 (`keynest_passkey_<credentialId>`)
    と差分があり、未解決事項 3 で整合方法を継続検討する。
  - 既存コード:
    - `app/src/main/java/io/github/hitoshiichikawa/keynest/auth/BiometricAuthenticator.kt`
      = 既存 `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` 実装 + `AuthResult` sealed
      class を本 Issue から再利用する。
    - `app/src/main/java/io/github/hitoshiichikawa/keynest/security/{AesGcmCipher,
      KeystoreKeyProvider, EncryptedBlob}.kt` = `KeystoreKeyProvider(keyAlias = ...)`
      コンストラクタで PassKey ごとに alias 差し替えできる既存 API を再利用する。
- **本 requirements で新たに導入した推測**: なし。すべての要件は上記出典の
  事実 (Issue 本文 / 人間コメント / 依存 design.md / 既存コード) のいずれかに
  trace 可能。
- 既存 requirements.md のスタイル踏襲先:
  - `docs/specs/91-feat-passkey-room-migration-passkeyentit/requirements.md`
    (章立て / EARS 形式 / 「決定事項」「Out of Scope」「未解決事項」「関連 Issue」
    の節構成 / 表ベースのスコープ整理)。
  - `docs/specs/90-feat-passkey-credentialproviderservice-m/requirements.md`
    (umbrella 継承事項 / API ゲーティング表現 / NFR 構成)。
