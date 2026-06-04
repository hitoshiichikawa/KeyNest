# Requirements Document

## 概要 / Goal / Non-Goal

### 概要

KeyNest は umbrella Issue #89 で「Android Credential Manager API 経由の PassKey
プロバイダ対応」を進めることが確定しており、Phase 1 として #90 (Service Manifest
登録 + 空応答スケルトン) と #91 (Room 永続化レイヤ + `PasskeyEntity` /
`PasskeyDao` / `PasskeyRepository`) を、Phase 2 として #99 (登録セレモニー =
`onBeginCreateCredentialRequest` 本実装 + `PasskeyCreator` + `PasskeyCreateActivity`)
を実装済みである。

本 Issue (#100) は umbrella #89 の **Phase 3 = 認証セレモニー (authentication
ceremony)** を実装する。具体的には、

- #90 / #99 のあとも **空応答のままになっている**
  `KeyNestCredentialProviderService.onBeginGetCredentialRequest` を本実装に置き換え、
- WebAuthn `navigator.credentials.get()` 由来の `BeginGetCredentialRequest`
  (PublicKey type) を受けて、`allowCredentials` が指定されていれば credentialId
  フィルタで、空であれば `listDiscoverableByRpId(rpId)` で resident credential を、
  KeyNest が保管している PassKey 候補として OS の Credential Manager UI に
  **`CredentialEntry` (= `PublicKeyCredentialEntry`)** として提示し、
- ユーザーが KeyNest 上の PassKey を選択した先で新規 `PasskeyAuthActivity`
  (仮称、最終命名は design.md) を起動して **BiometricPrompt** を経由したうえで、
- 新規 `PasskeyAssertion` (domain layer) が **`authenticatorData` を組み立て**
  (`rpIdHash` / `flags = UP|UV` / インクリメント済み `signCount` /
  extensions 空) し、`PasskeyRepository` から復号した private key で
  **ES256 (P-256) 署名** を行い、
- WebAuthn `AuthenticationResponseJSON` 形式 (`credentialId` / `authenticatorData` /
  `signature` / `userHandle` / `clientDataJSON`) を `GetCredentialResponse` として
  OS / RP に返却する

までを 1 PR の到達点とする。

この Issue 単体では新規登録セレモニー (#99) / 一覧 UI (#89 分割案 5) / 個別管理
(rename / 削除) UI (#89 分割案 6) / 設定画面の更新 (#89 分割案 7) / README /
Privacy / Support docs 更新 (#89 分割案 8) / caBLE (Hybrid transport) / WebAuthn
extensions (`hmac-secret` / `prf` 等) / attestation の検証には到達しない (Out of
Scope)。

なお umbrella #89 のとおり、UI / KDoc / コメント / ログメッセージに登場する
日本語 / 英語表記は **「PassKey」** に統一する。

### Goal

- `KeyNestCredentialProviderService.onBeginGetCredentialRequest` を「空応答」から
  「`CredentialEntry` 入りの `BeginGetCredentialResponse`」に差し替える。
- `allowCredentials` 経路 (non-discoverable) と `listDiscoverableByRpId` 経路
  (discoverable = usernameless login) の両方をサポートする (#89 確定事項: 両対応)。
- 候補が 0 件の場合は **空の `BeginGetCredentialResponse`** を返し、OS Credential
  Manager シートに KeyNest を出さない (#100 本文 Requirement 1.3)。
- 新規 `PasskeyAuthActivity` (仮称、最終命名は design.md) を `CredentialEntry` の
  pending intent から起動可能にし、そこで既存
  `BiometricAuthenticator` (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`) で
  BiometricPrompt を起動する。BiometricPrompt 未設定端末では Device Credential
  (PIN/Pattern) にフォールバックする (#89 確定事項 / #99 と同方針)。
- 新規 `PasskeyAssertion` (domain / pure Kotlin) を導入し、`authenticatorData` の
  byte 組み立て (rpIdHash 32 byte + flags 1 byte + signCount 4 byte + extensions
  なし) と ES256 (P-256) 署名 (`authenticatorData || clientDataHash` を入力に
  `SHA256withECDSA` ASN.1 DER) を集約する。
- `PasskeyRepository` から PassKey を取り出し、`encryptedPrivateKey` を AES-GCM で
  復号して **PKCS#8 ES256 private key** を得る。復号 / 署名後の **平文 ByteArray
  は明示的に wipe** する (`ByteArray.fill(0)` 相当 / NFR 1.3、#99 と同方針)。
- signCount は **署名直前に `incrementSignCount(credentialId)` で +1** し、その
  値を `authenticatorData` に書き込む (**Option A = 署名前インクリメント +
  失敗時ロールバック**、#100 コメント「AでOK」で人間確定)。署名失敗 / 後続処理
  失敗時は DB / Keystore 上の signCount を元値に戻す。
- AuthenticationResponseJSON (WebAuthn Level 2 §5.1.4) 形式で
  `credentialId` (base64url) / `authenticatorData` (base64url) / `signature`
  (base64url) / `userHandle` (base64url) / `clientDataJSON` (base64url) を返却し、
  `PendingIntentHandler.setGetCredentialResponse(...)` 経由で OS / RP に返す。
- 上記をカバーする `PasskeyAssertionTest` (`authenticatorData` の bytewise 検証 +
  ES256 署名 verify) / `KeyNestCredentialProviderServiceTest` 拡張
  (`allowCredentials` 空 vs 指定の両ケース / signCount 二段階インクリメント) を
  追加し、既存テスト (#90 / #91 / #99 で導入されたもの) を破壊しない。

### Non-Goal (Out of Scope)

- 登録セレモニー (`onBeginCreateCredentialRequest` の実体 / `PasskeyCreator` /
  `PasskeyCreateActivity`) — #99 が担当済み。
- **caBLE / Hybrid transport** (QR / BLE による他端末からの PassKey 取得) —
  別 Issue (umbrella #89 Out of Scope を継承)。
- **attestation の検証** (登録時の `attestationObject` 検証 / metadata service
  との照合) — RP 側責務。KeyNest authenticator 側では実施しない。
- **WebAuthn extensions** (`hmac-secret` / `prf` / `largeBlob` / `appid` 等) —
  本 Issue では空配列 / 未対応で返却する (確認事項 2 を人間確定とみなす扱い、
  後述「確定済み判断事項」参照)。
- 既存クレデンシャル **一覧 UI への PassKey 表示** — #89 分割案 5。
- PassKey 単位の **rename / 削除 UI** — #89 分割案 6。
- **設定画面**の「PassKey プロバイダとして登録」状態表示 / OS 設定への導線 —
  #89 分割案 7。
- README / Privacy Policy / Support ページの更新 — #89 分割案 8。
- **`PasskeyEntity` / `PasskeyDao` / `PasskeyRepository` のスキーマ追加 /
  マイグレーション** — #91 が担当済み。本 Issue は #91 / #99 で確定した
  公開 API をそのまま呼び出す側。ただし
  `loadPrivateKey(credentialId): ByteArray` (復号後 PKCS#8 を返す) と
  `incrementSignCount(credentialId): Long` (新値を返す) など **本 Issue で
  追加が必要になる Repository メソッド** は本 Issue が `PasskeyRepository` に
  追加する。これは #91 で `PasskeyDao.incrementSignCount` / `findByCredentialId`
  / `listDiscoverableByRpId` までは確定済みだが、Repository 公開 IF が現状
  `save` / `findByCredentialId` / `findByRpIdAndUserHandle` / `delete` の
  4 本に限定されているため (後段「データモデル / 公開 IF への影響」参照)。
- **`CredentialProviderService` 自体の Manifest 登録 / xml / 依存追加** —
  #90 が担当済み。本 Issue は #90 で配線済みの Service クラス本体の
  `onBeginGetCredentialRequest` だけを差し替える。
- API 26〜33 ユーザー向けの「PassKey 機能は Android 14 以降で利用可能」表示 —
  #89 分割案 7 / 設定画面 Issue。
- 認証時に **Touch‑less な silent assertion** を返す経路 (UV bit 0、UP bit 0
  などのバリエーション) — 本 Issue では UP = UV = 1 固定。
- PassKey 認証回数の **オフライン counter sync** (端末復元 / 別端末との signCount
  整合) — caBLE 不対応と一体で別 Issue 扱い。

## 関連 Issue / PR

- **Parent (umbrella)**: #89 feat(passkey): Android Credential Manager 経由の
  PassKey プロバイダ対応 (umbrella)。
- **Depends on**:
  - #90 feat(passkey): `CredentialProviderService` の Manifest 登録と最小骨組み
    (本 Issue が差し替える `KeyNestCredentialProviderService.onBeginGetCredentialRequest`
    の **空応答スケルトン** を提供する)。
  - #91 feat(passkey): Room migration + `PasskeyEntity` / `PasskeyDao` /
    `PasskeyRepository` (本 Issue が `findByCredentialId` /
    `listDiscoverableByRpId` (`PasskeyDao` 経由) / `incrementSignCount`
    (`PasskeyDao` で確定済み、Repository 経由で本 Issue が公開する) を呼び出す)。
  - #99 feat(passkey): 登録セレモニー (`onBeginCreateCredentialRequest` 本実装)。
    本 Issue は #99 が確立した `credentialprovider/registration/` 配下の
    `PasskeyCreator` / `PasskeyCreateActivity` / `AuthenticatorData` の対
    (assertion 側) を `credentialprovider/authentication/` 配下に新設する。
    本 Issue 開始時点で #99 が確立した `KeynestAaguid` (16 byte big-endian) 定数 /
    `KEYNEST_AAGUID` / Keystore alias 命名 `keynest_passkey_<credentialId>` /
    Repository 公開 IF はそのまま流用する。
- **後続予定 (Phase 4 以降)**:
  - umbrella #89 分割案 5/6: 一覧 UI / 個別管理 UI。
  - umbrella #89 分割案 7: 設定画面。
  - umbrella #89 分割案 8: README / Privacy / Support docs 更新。
  - caBLE / Hybrid transport / extensions 対応 (別 Issue)。

## スコープ

### 新規追加するファイル

| 対象 | 概要 |
|---|---|
| `app/src/main/java/.../credentialprovider/authentication/PasskeyAssertion.kt` | domain layer。`authenticatorData` の byte 組み立て (`rpIdHash` 32 + `flags` 1 + `signCount` 4 + `extensions` なし) + ES256 (P-256) 署名 (`SHA256withECDSA`, ASN.1 DER) + AuthenticationResponseJSON 組み立て |
| `app/src/main/java/.../credentialprovider/authentication/PasskeyAuthActivity.kt` (仮称) | `FragmentActivity`。`CredentialEntry` の pending intent から起動。`BiometricAuthenticator.authenticate(...)` → `PasskeyRepository.loadPrivateKey(...)` → `PasskeyRepository.incrementSignCount(...)` (Option A: 署名前 +1) → `PasskeyAssertion.sign(...)` → 結果を `PendingIntentHandler.setGetCredentialResponse(...)` で OS に返却 |
| `app/src/main/java/.../credentialprovider/authentication/GetEntryBuilder.kt` (仮称) | `BeginGetCredentialRequest` から `PublicKeyCredentialEntry` (`accountName` / `displayName` / `pendingIntent`) を組み立てる factory。`allowCredentials` 経路 / discoverable 経路の両分岐を担う |
| `app/src/main/java/.../credentialprovider/authentication/AllowCredentialsParser.kt` (仮称、最終粒度は design.md) | `BeginGetPublicKeyCredentialOption.requestJson` から `allowCredentials[].id` を抽出するヘルパ (#99 の `extractExcludeCredentialIds` と対の責務) |
| `app/src/test/.../credentialprovider/authentication/PasskeyAssertionTest.kt` | `authenticatorData` の bytewise (rpIdHash / flags 0x05 (UP\|UV) / signCount big-endian / extensions なし) を検証。ES256 署名が public key で verify できることを検証 |
| `app/src/test/.../credentialprovider/KeyNestCredentialProviderServiceTest.kt` (#99 で拡張済みのテストを **更に拡張**) | `onBeginGetCredentialRequest` が `allowCredentials = 空` / `allowCredentials = 指定` の両ケースで正しい候補数を返すこと / 同じ PassKey で 2 回認証すると signCount が 2 段階 (+2) インクリメントされること / 候補 0 件で空応答を返すこと |

### 変更する既存ファイル

| 対象 | 変更内容 |
|---|---|
| `app/src/main/java/.../credentialprovider/KeyNestCredentialProviderService.kt` (#90 導入 / #99 で `onBeginCreateCredentialRequest` 差し替え済み) | `onBeginGetCredentialRequest` の **空応答 (`BeginGetCredentialResponse.Builder().build()`) を本実装に置換**。`onBeginCreateCredentialRequest` / `onClearCredentialStateRequest` は触らない |
| `app/src/main/AndroidManifest.xml` | `<activity android:name=".credentialprovider.authentication.PasskeyAuthActivity" ...>` を追加 (`exported=false`、`theme` は #99 `PasskeyCreateActivity` と同じ dialog / translucent theme を流用)。既存 `<service>` / `PasskeyCreateActivity` 宣言は変更しない |
| `app/src/main/java/.../auth/BiometricAuthenticator.kt` | **基本的に変更しない**。本 Issue では既存のまま再利用 (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL` 構成。`PasskeyAuthActivity` を `FragmentActivity` として渡す) |
| `app/src/main/java/.../domain/repository/PasskeyRepository.kt` (#99 で inline 追加) | **`loadPrivateKey(credentialId): ByteArray`** と **`incrementSignCount(credentialId): Long`** を追加 (戻り値: 新 signCount)。**`decrementSignCount(credentialId, oldValue: Long)`** ないし **`rollbackSignCount(credentialId)`** を追加して Option A の失敗時ロールバックを支える (最終 API シグネチャは design.md で確定。`DAO.incrementSignCount` は #91 で実装済み) |
| `app/src/main/java/.../data/repository/PasskeyRepositoryImpl.kt` (#99 で inline 追加) | 上記 3 メソッドの実装を追加。`loadPrivateKey` 内で `keynest_passkey_<credentialId>` alias の `KeystoreKeyProvider` + `AesGcmCipher` を組み立て、`(privateKeyIv, encryptedPrivateKey)` から復号する。`incrementSignCount` / ロールバックは Room の `runInTransaction` 内で実行する |
| `app/src/main/java/.../di/ServiceLocator.kt` | `PasskeyAssertion` のシングルトン提供 / `GetEntryBuilder` / `AllowCredentialsParser` の DI を追加検討 (最終粒度は design.md で確定)。既存 `PasskeyRepository` 参照はそのまま |

### Out of Scope (再掲)

- #90 / #91 / #99 で確立した骨組み・データ層・登録セレモニーは本 Issue では触らない。
- caBLE / attestation / WebAuthn extensions は本 Issue では実装しない。
- 一覧 UI / 個別管理 / 設定画面 / docs は本 Issue では更新しない。

## 確定済み判断事項

> Issue #100 本文「確認事項」3 件のうち、③signCount 更新タイミングのみ
> 人間判断必須として PM が triage で確認、**Issue #100 コメント「AでOK」で
> Option A (署名前インクリメント + 失敗時ロールバック) 確定**。①UP/UV フラグ /
> ②extensions 空返却は PM triage で「Claude 自律判断可能」と整理済み。本セク
> ションでは 3 件すべての確定値と理由を明示する。

### 決定 1: UP / UV フラグは両方 1 で固定 (Issue 本文 確認事項 1)

- **決定**: `authenticatorData.flags` バイトに **UP (0x01) ビットと UV (0x04)
  ビットを両方立てて固定** とする (`flags = 0x05`)。`AT` (attestedCredentialData
  present) / `ED` (extension data) は **常に 0** (認証セレモニーでは
  attestedCredentialData は不要、extensions も Out of Scope)。
- **理由**: BiometricPrompt が `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` 構成で
  通過した時点で WebAuthn Level 2 §6.1 の「User Verified」要件を満たすため、
  UV = 1 を立てる根拠が明確。UP = 1 はそもそも user presence を要求する
  プラットフォーム生体認証である以上常に満たされる。RP 側で UV を要求するか
  どうかは RP の `userVerification` プリファレンス次第だが、KeyNest 側は
  常時 UV = 1 を返すことで RP が `required` を要求しても弾かれない。
- **影響範囲**:
  - `PasskeyAssertion` 内の flags 定数 (`0x05`)
  - Requirement 3.2 / 6.1 で明示
  - `PasskeyAssertionTest` で flags バイトを `0x05` で固定 assert

### 決定 2: extensions は空配列 (出力時 ED フラグ = 0 / `authenticatorData` に
extension data を含めない) (Issue 本文 確認事項 2)

- **決定**: 本 Issue では WebAuthn extensions (`hmac-secret` / `prf` /
  `largeBlob` / `appid` 等) を **一切処理しない**。`authenticatorData.flags`
  の **ED (Extension data) ビットは 0** で固定し、`extensions` byte 列を
  `authenticatorData` に **append しない**。AuthenticationResponseJSON 側の
  `clientExtensionResults` も空オブジェクト `{}` で返す。
- **理由**: 本 Issue のスコープは Phase 3 認証セレモニーのコア (候補抽出 + 生体
  認証 + ES256 署名 + signCount 更新) に集中する。extensions は WebAuthn
  オプション機能で、RP 側も extensions を必須として要求するケースは少ない
  (`required` 指定の場合、RP は extension が処理されなくても assertion を
  破棄しない仕様 = WebAuthn Level 2 §9 の processing rules)。
- **影響範囲**:
  - `PasskeyAssertion` の `authenticatorData` 組み立てロジック (extensions
    bytes を含めない、flags に ED ビットを立てない)
  - Requirement 3.2 で明示
  - Out of Scope セクションで明示

### 決定 3: signCount は署名前インクリメント + 失敗時ロールバック (Option A)
(Issue 本文 確認事項 3、#100 コメント「AでOK」で人間確定)

- **決定**: `PasskeyAuthActivity` の認証フローは次の順序で進める:
  1. BiometricPrompt 成功直後、`PasskeyRepository.incrementSignCount(credentialId)`
     を呼び出して DB 上の `signCount` を +1 し、その **新値** を取得する。
  2. その新値を `authenticatorData.signCount` フィールド (4 byte big-endian)
     に書き込み、`PasskeyAssertion.sign(...)` で ES256 署名を実施する。
  3. 署名が成功すれば assertion をそのまま OS に返却し、DB の `signCount`
     はインクリメント済みの値で確定。
  4. 署名 / 後続処理 (encoding / `setGetCredentialResponse` / private key wipe)
     のどれかが例外で失敗した場合は、**Room の `runInTransaction` 内で
     `signCount` を元値に戻す** (= `decrementSignCount` or 等価な更新)。
- **理由 (Issue #100 コメント「AでOK」 + PM triage 推奨)**: WebAuthn Level 2
  §6.3.3 step 21 で RP は受け取った signCount と保存値を比較してクローン
  authenticator 検知を行う。`authenticatorData` に書き込んだ signCount と
  DB 永続値が常に一致していることが replay 検知精度に直結するため、署名前に
  DB を進めておく方式が最も忠実。Option B (署名後インクリメント) は実装が
  シンプルだが、署名済み assertion と DB 値の一時的乖離が生じる。
- **トランザクション境界**: `incrementSignCount` (read 旧値 + write 新値) と
  ロールバック (write 旧値) は Room の `withTransaction { ... }` 内で原子化
  する。最終的な API 形 (例: `incrementSignCount` の戻り値が新値を返す ABI、
  ロールバックは `setSignCount(credentialId, oldValue)` で実施するなど) は
  design.md で確定する。
- **影響範囲**:
  - `PasskeyRepository` 公開 API に `incrementSignCount(credentialId): Long`
    (新 signCount を戻り値で返す) と、ロールバック用の補助 API を追加
    (Requirement 3.3 / 3.4 / 5.x)
  - `PasskeyAuthActivity` の責務範囲 (順序保証 / `runInTransaction` 呼び出し
    境界)
  - `KeyNestCredentialProviderServiceTest` で「同じ PassKey で 2 回認証 →
    signCount が +2」「署名失敗注入で signCount が +0」の両ケースを assert
    する (#100 本文 Requirement 4.4 + 本決定)

## 背景

- umbrella #89 でカバーする 8 つの分割案のうち、Phase 1 の #90 (Service 骨格) /
  #91 (Room 永続化) と Phase 2 の #99 (登録セレモニー) が確定し、本 Issue
  (#100 = 分割案 4「認証セレモニー」) が Phase 3 にあたる。これで PassKey の
  **登録 → 認証** の両セレモニーが揃い、KeyNest が PassKey プロバイダとして
  自立する。
- #90 / #99 の現状コードでは `KeyNestCredentialProviderService.onBeginGetCredentialRequest`
  が以下のように空応答スタブのままになっている (`KeyNestCredentialProviderService.kt`
  の `onBeginGetCredentialRequest` 内コメント抜粋: 「`#90 stub retained —
  authentication ceremony is #89 分割案 4`」)。本 Issue でこれを差し替える。
- #91 で `passkeys` テーブル + `PasskeyDao` が確立済み。`PasskeyDao` は本 Issue
  で必要な
  - `findByCredentialId(credentialId)`: `allowCredentials` 経路で各 credentialId
    の存在確認 + 取得
  - `listDiscoverableByRpId(rpId)`: discoverable credential (= resident key)
    の usernameless login 候補抽出
  - `incrementSignCount(credentialId, timestamp)`: `signCount` の +1 と
    `lastUsedAt` 更新
  までを公開済み。一方 `PasskeyRepository` (#99 で inline 追加) は現状
  `save` / `findByCredentialId` / `findByRpIdAndUserHandle` / `delete` の
  4 本のみで、本 Issue は **`loadPrivateKey` + `incrementSignCount` +
  ロールバック補助** の 3 メソッドを Repository 公開 API に追加する必要がある。
- #99 で `credentialprovider/registration/` 配下に `PasskeyCreator` /
  `PasskeyCreateActivity` / `CreateEntryBuilder` / `ExcludeCredentialDetector` /
  `KeynestAaguid` を確立済み。本 Issue では対をなす形で
  `credentialprovider/authentication/` 配下に新設する。`KeynestAaguid` は
  本 Issue では参照しない (authenticatorData の attestedCredentialData は
  認証セレモニーでは不要 = AT ビット 0 で固定)。
- umbrella #89 の確認事項 (人間確定済み) より本 Issue が継承する事実:
  - residentKey 両対応 (discoverable + non-discoverable) → 本 Issue は
    `listDiscoverableByRpId` (usernameless) と `findByCredentialId`
    (`allowCredentials` 経路) の両方を実装。
  - 生体認証は `BiometricPrompt` 未設定時に Device Credential (PIN/Pattern)
    へフォールバック。
  - 表記は「PassKey」(日本語 UI 含む)。
  - minSdk = 26 維持。Credential Manager Provider は API 34+ ゲーティング
    (#90 で `@RequiresApi(34)` + `tools:targetApi="34"` 確立済み)。
  - エクスポート禁止ポリシー (既存 credential と同一)。
- 既存資産:
  - `auth/BiometricAuthenticator.kt` (#99 でも使用) は
    `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` で Device Credential フォールバック
    を実装済み。本 Issue では `FragmentActivity = PasskeyAuthActivity` で再利用。
  - `security/AesGcmCipher.kt` + `security/KeystoreKeyProvider.kt` は
    `keynest_passkey_<credentialId>` alias 注入により passkey ごとの独立
    wrapping key を扱える。本 Issue では **decrypt 経路** (`AesGcmCipher.decrypt(blob)`)
    を呼ぶ。
  - `security/EncryptedBlob.kt` は `(iv, ciphertext)` ペアを保持。
    `PasskeyEntity.privateKeyIv` / `encryptedPrivateKey` を `EncryptedBlob` に
    再構成して `AesGcmCipher.decrypt(blob)` に渡す。
  - WebAuthn assertion 関連クラス (`PasskeyAssertion` / `PasskeyAuthActivity` /
    `GetEntryBuilder` 相当) は **本 Issue 時点では未実装** (現リポジトリに対応
    ファイルが存在しないことを確認済み)。本 Issue ですべて新規追加する。

## ユーザーストーリー

- As a 3rd-party アプリ / ブラウザ (RP), I want
  `navigator.credentials.get({publicKey: {...}})` を呼んだとき OS Credential
  Manager の選択シートに **KeyNest が PassKey 提供候補として表示**され、
  ユーザーが該当 PassKey を選択 + 生体認証を通過すると
  `AuthenticationResponseJSON` (clientDataJSON + authenticatorData + signature +
  userHandle) が返ってくること, so that 自社で keypair を持たずに KeyNest を
  「passkey 提供元」として WebAuthn ログインを完結できる。
- As a エンドユーザー, I want RP 側ログイン画面で「PassKey でログイン」を選んだ
  ときに、OS シートで保管している KeyNest 上の PassKey を選び、**生体認証
  (または PIN / Pattern)** が通れば そのまま RP のログインが完了すること, so
  that パスワードを入力せずに済む。`allowCredentials` で RP が特定の
  credentialId を要求した場合も、保存している PassKey と一致すれば同様にログイン
  できる。
- As a エンドユーザー (usernameless login), I want RP 側が
  `allowCredentials` を空で要求した場合 (= 「保存されている PassKey から自由に
  選んでください」) でも、KeyNest が保管している discoverable な PassKey
  候補を OS シートで選べること, so that ユーザー名入力なしで RP にログインできる。
- As a 後続 Issue (一覧 UI / 個別管理) の実装担当, I want 認証セレモニーが
  使う `PasskeyRepository.loadPrivateKey` / `incrementSignCount` が公開 API
  として確立されていること, so that 後続の rename / 削除 / 強制無効化 UI も
  同じ Repository API を共通基盤として利用できる。
- As a メンテナ, I want 署名失敗 / 端末スリープ / Activity 強制終了 などの
  異常系で **DB の signCount が増えっぱなしにならない** こと (= Option A の
  ロールバック処理が確実に走ること), so that RP の replay 検知の信頼性を
  KeyNest 側で破壊しない。

## Requirements

> EARS 形式 (The X shall …, When … the X shall …, While … the X shall …,
> If … the X shall …) で記述。Issue #100 本文の Requirement 1〜4 をベースに、
> 確定済み判断事項 (決定 1〜3) と #90 / #91 / #99 の確定接合点を反映する。
> 各 Acceptance Criteria 末尾の `(#100-R<x>.<y>)` は Issue 本文番号への
> トレース、`(決定 X)` は本ドキュメントの決定事項対応を示す。

### Requirement 1: 候補抽出 (#100 本文 Requirement 1)

**Objective:** As a RP, I want KeyNest が `allowCredentials` の指定有無に
かかわらず正しく候補を抽出して OS Credential Manager UI に提示すること, so
that 標準的な WebAuthn 認証フローに従って KeyNest が PassKey 提供元として
機能できる。

#### Acceptance Criteria

1.1. When `onBeginGetCredentialRequest` が呼ばれ、`BeginGetPublicKeyCredentialOption`
の `requestJson` 内 **`allowCredentials` が空 (または省略)** のとき, the service
shall `PasskeyRepository.findByCredentialId(...)` ではなく、`PasskeyDao.listDiscoverableByRpId(rpId)`
相当の API を Repository 経由で呼び出し、得られた discoverable PassKey 群を
`PublicKeyCredentialEntry` として `BeginGetCredentialResponse` に積む。
(#100-R1.1)

1.2. When `requestJson` 内 **`allowCredentials` が指定されている** とき, the
service shall 各要素の `id` (base64url 文字列) について
`PasskeyRepository.findByCredentialId(id)` を呼び、戻り値が non-null のものに
ついてのみ `PublicKeyCredentialEntry` を生成して `BeginGetCredentialResponse`
に積む (#100-R1.2)。

1.3. If 候補が 0 件のとき, the service shall **空の `BeginGetCredentialResponse`**
(`BeginGetCredentialResponse.Builder().build()`) を返す。OS Credential Manager
シート上に KeyNest は表示されない (#100-R1.3)。

1.4. The service shall 1.1 / 1.2 の候補抽出を `onBeginGetCredentialRequest`
コールバック内で実行するが、**重い処理を伴わない** (Room の単点 lookup は
通常 ms オーダーで、ANR の閾値に対して十分に短い)。重い処理 (生体認証 /
復号 / 署名 / DB 更新) は `PasskeyAuthActivity` に委譲する (NFR 5.1 と整合)。

1.5. The service shall PublicKey 以外の `BeginGetCredentialOption`
(`BeginGetPasswordOption` 等) を受けたときは本 Issue では処理せず、
**空の `BeginGetCredentialResponse`** で応答する (password 系は既存
`KeyNestAutofillService` 経路で扱う方針継承)。最終的に password 系 option
への対応をどうするかは別 Issue 扱い。

1.6. The `PublicKeyCredentialEntry` の `accountName` / `displayName` には
`PasskeyEntity.userDisplayName` (なければ `userName`) を、説明文には
`rpDisplayName` ないし `rpId` を表示する (UI 詳細は design.md。本 Issue は
**最低限の表示文言** のみ確定し、locale 別翻訳は本 Issue 範囲外)。

### Requirement 2: 生体認証 (#100 本文 Requirement 2)

**Objective:** As a エンドユーザー, I want PassKey による署名の直前に生体認証
(または Device Credential) を要求され、認証を通らない限り signCount 更新も
署名も発生しないこと, so that 端末の所有者以外が KeyNest 上の PassKey で
無断ログインできない。

#### Acceptance Criteria

2.1. When ユーザーが OS Credential Manager UI 上で KeyNest の
`PublicKeyCredentialEntry` を選択した直後, the service / Activity 経路 shall
**`PasskeyAuthActivity` を pending intent 経由で起動**し、Activity の
`onCreate` で `BiometricAuthenticator.authenticate(title, subtitle)` を
`BIOMETRIC_STRONG or DEVICE_CREDENTIAL` 構成で起動する。 (#100-R2.1)

2.2. If 端末で BiometricPrompt が利用不能で Device Credential (PIN/Pattern/Password)
が設定されているとき, the `BiometricAuthenticator` shall Device Credential
へのフォールバックを実行する (既存実装の `Authenticators.DEVICE_CREDENTIAL` ビット
で自動的に達成、#89 確定事項 / #99 と同方針)。 (#100-R2.2)

2.3. If `BiometricAuthenticator.authenticate(...)` が `AuthResult.Succeeded`
以外 (`Cancelled` / `Failed` / `Unavailable`) を返したとき, the authentication
flow shall (a) `PasskeyRepository.incrementSignCount(...)` を呼ばず、
(b) `PasskeyAssertion.sign(...)` を呼ばず、(c) OS に対しては
`PendingIntentHandler.setGetCredentialException(resultIntent, GetCredentialException)`
を書き込み `setResult(RESULT_OK) + finish()` で返却する。具体的な例外型は
`Cancelled` → `GetCredentialCancellationException` /
`Failed` および `Unavailable` → `GetCredentialUnknownException` を **想定** する
(最終確定は design.md)。 (#100-R2.3)

2.4. The authentication flow shall 生体認証成功後に **同一 ActivityScope 内で
signCount インクリメント → private key 復号 → 署名 → encoding → OS 応答**
までを完了させる。Activity が認証中 / 復号中 / 署名中に `onDestroy` した場合は
**Option A のロールバック** が走り、signCount は元値に戻る (Requirement 3.4 と
整合)。

### Requirement 3: assertion 署名 / authenticatorData / signCount (#100 本文 Requirement 3)

**Objective:** As a RP, I want KeyNest が返す `authenticatorData` および
`signature` が WebAuthn Level 2 §6.1 / §6.3.3 仕様に準拠し、`signCount` が
DB 永続値と完全一致していること, so that 標準的な WebAuthn server / library で
受理でき、RP の replay 検知が正しく機能する。

#### Acceptance Criteria

3.1. The `PasskeyRepository.loadPrivateKey(credentialId)` shall 該当 PassKey
の `(privateKeyIv, encryptedPrivateKey)` を `keynest_passkey_<credentialId>`
alias の `KeystoreKeyProvider` + `AesGcmCipher` で **AES-GCM 復号** し、
PKCS#8 形式の ES256 (P-256) private key byte 配列を返す (#100-R3.1)。

3.2. The `PasskeyAssertion` shall 次の byte レイアウトで `authenticatorData`
を組み立てる (WebAuthn Level 2 §6.1):
- `rpIdHash` = `SHA-256(rpId.toByteArray(UTF_8))` → 32 byte
- `flags` = **`0x05`** (`UP (0x01) | UV (0x04)`、AT = 0 / ED = 0 / BS = 0 /
  BE = 0、決定 1 / 決定 2)
- `signCount` = `incrementSignCount` で得た **新 signCount** を 4 byte
  big-endian で encode
- `attestedCredentialData` = **なし** (認証セレモニーでは AT ビット 0)
- `extensions` = **なし** (決定 2、ED ビット 0)
 (#100-R3.2)

3.3. The authentication flow shall BiometricPrompt 成功直後、`PasskeyAssertion.sign(...)`
呼び出しに先立って `PasskeyRepository.incrementSignCount(credentialId)` で
DB 上の signCount を +1 し、その新値を `authenticatorData.signCount` に
書き込む (**決定 3 = Option A**) (#100-R3.3)。

3.4. If `PasskeyAssertion.sign(...)` ないしその後続処理 (encoding /
`setGetCredentialResponse` / private key wipe など Activity 範囲の処理) が
例外で失敗したとき, the authentication flow shall **DB 上の signCount を
インクリメント前の元値にロールバック** する (決定 3 / Option A)。
ロールバックは Room の `runInTransaction` ないし
`withTransaction { ... }` 境界内で実行され、`incrementSignCount` (write 新値) と
ロールバック (write 旧値) が片方だけ反映される状況を作らない。 (#100-R3.3 補強)

3.5. The `PasskeyAssertion.sign(...)` shall WebAuthn Level 2 §6.3.3 の入力
順序に従い、`authenticatorData || clientDataHash` (clientDataHash =
`SHA-256(clientDataJSON.toByteArray(UTF_8))`) を入力として ES256 (P-256) /
`SHA256withECDSA` の **ASN.1 DER** 形式の署名 byte 配列を返す (raw r||s でも
ASN.1 DER でもなく、WebAuthn Level 2 §6.3.3 step 23 は ASN.1 DER を要求)。
(#100-R3.4)

3.6. The authentication flow shall 復号後の **平文 PKCS#8 private key
ByteArray** を、署名完了直後に `ByteArray.fill(0)` 相当で wipe する。
`PasskeyAssertion` / Activity / Service の field に保持しないこと (NFR 1.3、
#99 と同方針)。

3.7. The `GetEntryBuilder` / `PasskeyAuthActivity` が組み立てる
**`PublicKeyCredentialResponseJson`** (= AuthenticationResponseJSON) shall
次のフィールドを含む:
- `id`: credentialId を **base64url (unpadded)** で encode した文字列
- `rawId`: 同じく base64url (unpadded)
- `response.clientDataJSON`: RP から渡された clientDataJSON を base64url
  (unpadded) で encode
- `response.authenticatorData`: 3.2 で組み立てた byte 列を base64url
  (unpadded) で encode
- `response.signature`: 3.5 で生成した署名 byte 列を base64url (unpadded) で
  encode
- `response.userHandle`: `PasskeyEntity.userHandle` (BLOB) を base64url
  (unpadded) で encode
- `type`: `"public-key"` (固定文字列)
- `clientExtensionResults`: `{}` (空オブジェクト、決定 2)
 (#100-R3.4)

3.8. The authentication flow shall 最終的に
`PendingIntentHandler.setGetCredentialResponse(resultIntent, GetCredentialResponse(PublicKeyCredential(authenticationResponseJson)))`
を呼び、`setResult(RESULT_OK, resultIntent)` + `finish()` で Activity を終了する。

### Requirement 4: テスト (#100 本文 Requirement 4)

**Objective:** As a メンテナ, I want 認証セレモニーの主要経路 (候補抽出 /
`authenticatorData` 組み立て / ES256 署名 / signCount Option A) が自動テストで
回帰検知できること, so that 後続 Issue 実装中に認証セレモニーの退行を早期に
検知できる。

#### Acceptance Criteria

4.1. The `PasskeyAssertionTest` (純 JVM unit test、Robolectric 不要) shall:
- (a) `authenticatorData` の rpIdHash が
  `MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray(UTF_8))` と
  完全一致する 32 byte であること。
- (b) `flags` バイトが `0x05` (UP | UV、AT = 0、ED = 0) であること (決定 1 /
  決定 2)。
- (c) `signCount` が引数で与えた値の 4 byte big-endian であること
  (`0x00000001` / `0x00000002` などの値で境界検証)。
- (d) `authenticatorData` の長さが **`32 + 1 + 4 = 37 byte`** であること
  (attestedCredentialData なし + extensions なし、決定 2)。
 (#100-R4.1)

4.2. The `PasskeyAssertionTest` shall ES256 (P-256) 署名が、**同じテストで
生成した public key** で `Signature.getInstance("SHA256withECDSA").verify(sig)`
が `true` を返すことを bytewise で検証する。署名対象は
`authenticatorData || clientDataHash` (clientDataHash = `SHA-256(clientDataJSON)`)
であり、ASN.1 DER 形式であること。 (#100-R4.2)

4.3. The `KeyNestCredentialProviderServiceTest` (#90 / #99 で導入された
Robolectric テストを **拡張**) shall:
- (a) `allowCredentials = 空` の `BeginGetPublicKeyCredentialOption` で
  `outcome.onResult(BeginGetCredentialResponse)` が **discoverable な PassKey
  数** に一致する `PublicKeyCredentialEntry` を返すこと (Repository を mock
  してアサート)。
- (b) `allowCredentials = [id1, id2, id3]` (うち id1 / id3 が KeyNest 内に
  存在) で `outcome.onResult(BeginGetCredentialResponse)` が **存在する 2 件**
  分の `PublicKeyCredentialEntry` を返すこと。
- (c) 候補が 0 件のとき `outcome.onResult` に **空の
  `BeginGetCredentialResponse`** が渡されること。
 (#100-R4.3)

4.4. The `KeyNestCredentialProviderServiceTest` (または別途
`PasskeyAuthActivityTest` を新設、要否は design.md) shall 同じ PassKey で 2 回
認証フローを完了させると `PasskeyDao.incrementSignCount` が **2 回呼ばれ**、
DB 上の `signCount` が `+2` インクリメントされることをアサートする。Repository
は mock せず in-memory Room を使うか、`DaoSpy` で呼び出し回数 + 引数を記録する
方式 (最終形は design.md)。 (#100-R4.4)

4.5. The `PasskeyAuthActivityTest` (または `KeyNestCredentialProviderServiceTest`
の Option A 検証分) shall 「`PasskeyAssertion.sign(...)` が例外を投げる」
シナリオを注入し、(a) DB 上の `signCount` がインクリメント前の値に戻る
(`ロールバック成立`)、(b) OS には `PendingIntentHandler.setGetCredentialException(...)`
が書き込まれることをアサートする (決定 3 / Requirement 3.4 と整合)。

4.6. The 既存 `KeyNestCredentialProviderServiceTest` (空応答検証 / #90 で
導入、#99 で `onBeginCreateCredentialRequest` 拡張) shall 本 Issue 変更後も
`onBeginCreateCredentialRequest` / `onClearCredentialStateRequest` のテストが
引き続き pass する (本 Issue は `onBeginGetCredentialRequest` のみ差し替える)。

4.7. The 既存 `Migration_4_5_Test` / `PasskeyDaoTest` / `PasskeyRepositoryTest`
(#91) / `PasskeyCreatorTest` / `AuthenticatorDataTest` (#99) shall 本 Issue
変更後も全件 pass する。Repository 公開 IF に `loadPrivateKey` /
`incrementSignCount` を **追加**する変更は backward compatible で、既存
メソッドのシグネチャは変更しないため、原則影響なし。

4.8. The 既存 `CredentialProviderServiceManifestTest` / `CredentialProviderXmlTest`
(#90) shall 本 Issue で `PasskeyAuthActivity` を AndroidManifest に追加した後も
pass する (本 Issue が触るのは `<activity>` 追加のみで、`<service>` /
`CreatePasskeyActivity` ブロックは変更しない)。

## Non-Functional Requirements

### NFR 1: セキュリティ — private key の Keystore 外露出禁止 / wipe

1.1. The PassKey 用 EC private key (PKCS#8) shall `PasskeyRepository.loadPrivateKey(...)`
の戻り値として **AES-GCM 復号直後のみメモリ上に存在し**、`PasskeyAssertion.sign(...)`
完了後ただちに `ByteArray.fill(0)` 相当で wipe する。Activity / Service /
PasskeyAssertion の field / 静的フィールドには保持しない (Requirement 3.6 と
整合、#99 NFR 1.3 を継承)。

1.2. The PassKey 用 wrapping key (AES-256-GCM, alias
`keynest_passkey_<credentialId>`) shall AndroidKeyStore provider 上で raw key
bytes を TEE の外に出さない (#99 / 既存 `KeystoreKeyProvider` の方針継承)。

1.3. The KeyNest shall PassKey private key 平文 / clientDataJSON 内 challenge /
`credentialId` raw value / `userHandle` raw value / 署名 bytes を **logcat に
info 以上のレベルで出力しない** (既存 `SafeLogger` 慣行 / #99 NFR 1.4 を継承)。
debug レベルでもサイズ表記のみで raw を出さない方針を design.md で確定する。

1.4. If 復号 / 署名 / DB 更新 (signCount) / Keystore I/O が失敗したとき, the
authentication flow shall silent fail せず例外を **OS に
`setGetCredentialException(...)`** で返し、かつ **決定 3 のロールバック** を
走らせる (Requirement 3.4 と整合)。

1.5. The authentication flow shall `clientDataJSON` を **そのまま base64url
encode** して返却するのみで、内容 (challenge / origin / type) の妥当性検証は
**RP 側責務** とする。authenticator (本 Issue) はクライアントから渡された
clientDataJSON をハッシュ化して署名するだけ。

### NFR 2: 既存テスト非破壊

2.1. The 本 Issue 変更 shall 既存 `KeyNestAutofillService` 関連テスト
(`FillResponseBuilderTest` / `LockedFillResponseSecurityTest` /
`CustomFieldFillResponseTest` 等) に影響を与えない (本 Issue は autofill 経路を
触らない)。

2.2. The 本 Issue 変更 shall #90 の `CredentialProviderServiceManifestTest` /
`CredentialProviderXmlTest` を破壊しない (Requirement 4.8 と整合)。

2.3. The 本 Issue 変更 shall #91 の `Migration_4_5_Test` / `PasskeyDaoTest` /
`PasskeyRepositoryTest` を破壊しない (`PasskeyRepository` に **追加メソッド**
を入れるため、既存テストの呼び出しは backward compatible / Requirement 4.7 と
整合)。

2.4. The 本 Issue 変更 shall #99 の `PasskeyCreatorTest` /
`AuthenticatorDataTest` / `KeyNestCredentialProviderServiceTest`
(`onBeginCreateCredentialRequest` 拡張部分) を破壊しない (Requirement 4.6 /
4.7 と整合)。

### NFR 3: minSdk / API ゲーティング

3.1. The `minSdkVersion` shall **26 を維持** する (umbrella #89 / #90 / #99 と
整合)。

3.2. The `PasskeyAssertion` / `PasskeyAuthActivity` / `GetEntryBuilder` 等の
本 Issue 新規追加クラス shall `@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)`
(= 34) で API 34+ ゲーティングする (#90 / #99 の二段防御方針を継承)。

3.3. The `PasskeyAuthActivity` の `<activity>` 宣言 shall
`tools:targetApi="34"` を付与する (#90 / #99 の Manifest 流儀と整合)。

3.4. The `KeyNestAutofillService` および既存 password credential 経路の
`minSdk = 26` 動作 shall 本 Issue 変更後も影響を受けない (#90 NFR を継承)。

### NFR 4: ネット境界の不変

4.1. The 本 Issue 変更 shall `android.permission.INTERNET` を追加しない
(umbrella #89 のネット境界ポリシー)。RP との通信は呼び出し側のアプリ /
ブラウザが担う。

4.2. The 本 Issue 変更 shall WebAuthn server (RP) との直接通信を行わない
(`PasskeyAssertion` は pure local crypto / encoding のみ)。

### NFR 5: パフォーマンス / UI ブロッキング許容範囲

5.1. The Service の `onBeginGetCredentialRequest` callback shall **重い処理を
行わず** (秒未満で `outcome.onResult(...)` を解決する)、候補抽出に必要な
DAO 呼び出し (`findByCredentialId` × N or `listDiscoverableByRpId`) は
`Dispatchers.IO` ないし `runBlocking(IO)` で実行する (Room の単点 lookup は
通常 ms オーダーで ANR 閾値内、#99 と同方針)。

5.2. The 復号 / signCount インクリメント / 署名 / OS 応答までの一連処理
shall `Dispatchers.IO` で実行され、`PasskeyAuthActivity` の UI thread を
blocking しない。BiometricPrompt 表示から OS 応答までの目標所要時間は **3 秒
以内** (#99 と同基準)。3 秒を超える場合は spinner / progress indicator を
表示する (UI 詳細は design.md)。

### NFR 6: 命名 / 表記

6.1. The 新規追加クラス名 / KDoc / コメント / ログメッセージ shall PassKey
機能に言及する箇所で **「PassKey」** 表記を用いる (#89 確定事項を継承)。

6.2. The package 配置 shall **`credentialprovider/authentication/`** 配下に
集約される (#99 の `credentialprovider/registration/` に対する対の配置)。

## データモデル / 公開 IF への影響

### #91 / #99 PasskeyEntity / PasskeyDao / PasskeyRepository への影響

- 本 Issue は **`passkeys` テーブル schema (v5) を変更しない**。
  - `PasskeyEntity.signCount: Long` / `lastUsedAt: Long?` / `encryptedPrivateKey` /
    `privateKeyIv` / `keyAlias` (= `keynest_passkey_<credentialId>` 規約は
    #99 で確定済み) をそのまま使う。
- 本 Issue は `PasskeyDao` の公開 IF を **変更しない**。
  - `findByCredentialId(credentialId)`: 1.2 で使用
  - `listDiscoverableByRpId(rpId)`: 1.1 で使用
  - `incrementSignCount(credentialId, timestamp)`: 3.3 で使用 (DAO は既に
    `signCount + 1` と `lastUsedAt = :timestamp` を 1 UPDATE で原子化済み)
- 本 Issue は `PasskeyRepository` 公開 IF に以下を **追加** する (backward
  compatible)。既存メソッド (`save` / `findByCredentialId` /
  `findByRpIdAndUserHandle` / `delete`) のシグネチャは変更しない。
  - `loadPrivateKey(credentialId: String): ByteArray`
    AES-GCM 復号後の PKCS#8 ES256 private key byte 配列を返す。呼び出し側は
    使用後ただちに `ByteArray.fill(0)` で wipe する責務を負う (NFR 1.1)。
  - `incrementSignCount(credentialId: String): Long`
    DAO の `incrementSignCount(credentialId, timestamp = nowMillis())` を Room
    transaction 境界で実行し、UPDATE 後の **新 signCount** を SELECT して返す。
    戻り値が `authenticatorData.signCount` フィールドに書き込まれる。
  - signCount ロールバック用補助メソッド (例: `setSignCount(credentialId: String,
    value: Long)` / `decrementSignCount(credentialId, expectedValue: Long)` 等)。
    Option A の失敗時ロールバックを Room transaction 境界で安全に実行できる
    API 形を design.md で確定する (未解決事項に再掲)。

### 新規定数 / 命名規約のまとめ

| 名前 | 値 / 形式 | 配置 | 出所 |
|---|---|---|---|
| `PasskeyAssertion.FLAGS_UP_UV` | `0x05` (`UP \| UV`) | `credentialprovider/authentication/PasskeyAssertion.kt` 内定数 | 決定 1 / WebAuthn §6.1 |
| `authenticatorData` 長 | `32 + 1 + 4 = 37 byte` (extensions なし) | `PasskeyAssertion` | 決定 2 / WebAuthn §6.1 |
| ES256 署名アルゴリズム | `"SHA256withECDSA"` (JCE) | `PasskeyAssertion` | WebAuthn §6.3.3 step 23 |
| activity name | `.credentialprovider.authentication.PasskeyAuthActivity` | AndroidManifest.xml | 本 Issue |
| Keystore alias (decrypt 側) | `keynest_passkey_<credentialId>` | `PasskeyRepositoryImpl.aliasFor(credentialId)` | #99 決定 2 を継承 |

### AuthenticationResponseJSON のフォーマット

- WebAuthn Level 2 §5.1.4 + `androidx.credentials.PublicKeyCredential` が要求
  する JSON 構造で組み立てる (Requirement 3.7 の項目一覧)。
- JSON ライブラリは既存資産 `kotlinx.serialization.json.Json` (`#99` で
  `KeyNestCredentialProviderService` 内で `requestJson` parse に既に使用) を
  再利用する。新規依存追加なし (NFR 4 / umbrella #89 「依存追加最小化」方針と
  整合)。

## 処理フロー要点

> 詳細フロー (mermaid / state machine / pending intent payload schema) は
> design.md で確定する。本セクションでは責務分担と「どのクラスが何を担当する
> か」だけを示す。

### Step 1: OS → Service callback

```
Browser/App
  └─> CredentialManager.getCredential(GetPublicKeyCredentialRequest)
        └─> CredentialManager System Service
              └─> KeyNestCredentialProviderService.onBeginGetCredentialRequest(req, signal, outcome)
                    ├─ BeginGetPublicKeyCredentialOption を抽出
                    ├─ requestJson.allowCredentials を parse
                    ├─ allowCredentials が空 → PasskeyRepository.listDiscoverableByRpId(rpId)
                    ├─ allowCredentials が指定 → 各 id について findByCredentialId(id) で存在確認
                    └─ 候補 0 件 → 空 BeginGetCredentialResponse
                       そうでなければ PublicKeyCredentialEntry を 1 件以上積んで
                       outcome.onResult(response)
```

責務:
- `KeyNestCredentialProviderService` 本体は **重い処理を行わない** (NFR 5.1)。
- `PublicKeyCredentialEntry` の pending intent は `PasskeyAuthActivity` を
  起動する Intent を credentialId / providerRequest 識別子と共にラップする。

### Step 2: ユーザーが KeyNest 上の PassKey を選択 → Activity 起動

```
OS Credential Manager UI
  └─> User taps KeyNest's PublicKeyCredentialEntry
        └─> entry.pendingIntent.send()
              └─> PasskeyAuthActivity.onCreate()
                    ├─ PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
                    ├─ BiometricAuthenticator.authenticate(...)
                    │  └─ AuthResult.Succeeded
                    │       ├─ PasskeyRepository.incrementSignCount(credentialId) → newSignCount   ← Option A
                    │       ├─ PasskeyRepository.loadPrivateKey(credentialId) → plaintextPkcs8
                    │       ├─ PasskeyAssertion.sign(rpId, clientDataJSON, newSignCount, plaintextPkcs8)
                    │       │  └─ authenticatorData + ES256 signature
                    │       ├─ plaintextPkcs8.fill(0)   ← NFR 1.1
                    │       ├─ AuthenticationResponseJSON を組み立て
                    │       └─ PendingIntentHandler.setGetCredentialResponse(resultIntent, GetCredentialResponse(...))
                    │  └─ AuthResult.Cancelled / Failed / Unavailable
                    │       └─ PendingIntentHandler.setGetCredentialException(resultIntent, GetCredentialException(...))
                    │       (signCount は触っていないのでロールバック不要)
                    └─ 署名/encoding 失敗 → ロールバック (PasskeyRepository.setSignCount(credentialId, oldValue))
                       → PendingIntentHandler.setGetCredentialException(...)
                    └─ setResult(RESULT_OK, resultIntent) → finish()
```

責務:
- `PasskeyAuthActivity` = UI / lifecycle 管理 + BiometricPrompt 起動 +
  `Repository` / `Assertion` の orchestration + 平文 wipe + ロールバック制御。
- `PasskeyAssertion` = `authenticatorData` 組み立て + ES256 署名 +
  AuthenticationResponseJSON encode (pure crypto / encoding、Repository を
  直接触らない)。
- `PasskeyRepository` (#91 / #99 既存 + 本 Issue 追加) = AES-GCM 復号 +
  signCount トランザクション管理 + Keystore alias 管理。

### Step 3: OS / RP へ応答

```
PasskeyAuthActivity.finish() (RESULT_OK)
  └─> OS Credential Manager
        └─> Browser/App's GetCredentialResponse callback fires
              └─> RP server に authentication response
                  (clientDataJSON + authenticatorData + signature + userHandle) を送信
                  → RP が signCount を保存値と比較 → ログイン完了
```

責務:
- KeyNest 側は AuthenticationResponseJSON を組み立てて OS に返却するまで。
  RP server との通信は呼び出し側のアプリ / ブラウザ。

## 既存資産との接続

| 既存資産 | 本 Issue での使い方 |
|---|---|
| `KeyNestCredentialProviderService` (#90 / #99) | `onBeginGetCredentialRequest` の空応答 (`BeginGetCredentialResponse.Builder().build()`) を `PublicKeyCredentialEntry` 入りの `BeginGetCredentialResponse` に差し替え。`onBeginCreateCredentialRequest` / `onClearCredentialStateRequest` は **触らない** |
| `PasskeyRepository` (#99 で inline 追加) | `findByCredentialId(credentialId)` を `allowCredentials` 経路で呼ぶ。本 Issue で追加する `loadPrivateKey(credentialId)` / `incrementSignCount(credentialId)` / ロールバック補助を `PasskeyAuthActivity` から呼ぶ |
| `PasskeyDao` (#91) | `incrementSignCount(credentialId, timestamp)` / `findByCredentialId` / `listDiscoverableByRpId` を Repository 経由で利用 (直接は触らない) |
| `auth/BiometricAuthenticator` | そのまま再利用。`FragmentActivity` (= `PasskeyAuthActivity`) を渡す。`AuthResult.Succeeded` 以外は `GetCredentialException` 経路 (#99 と同方針) |
| `security/AesGcmCipher` / `KeystoreKeyProvider` / `EncryptedBlob` | `PasskeyRepositoryImpl.loadPrivateKey(...)` 内で利用。`(privateKeyIv, encryptedPrivateKey)` を `EncryptedBlob` に再構成 → `AesGcmCipher.decrypt(...)` で平文 PKCS#8 復号 |
| `credentialprovider/registration/KeynestAaguid` (#99) | **本 Issue では参照しない** (認証セレモニーでは attestedCredentialData なし = AAGUID 埋め込みなし、決定 2 / Requirement 3.2) |
| 既存 `KeyNestAutofillService` / autofill 経路 | 本 Issue は触らない |
| 既存 `CredentialEntity` / `CredentialDao` / password credential 経路 | 本 Issue は触らない (PublicKey 以外の option は空応答、Requirement 1.5) |
| Manifest `<service>` (#90) / `PasskeyCreateActivity` (#99) | 本 Issue は触らない。新規 `<activity android:name="...PasskeyAuthActivity">` のみ追加 |
| `res/xml/credential_provider.xml` (#90) | 本 Issue は触らない (TYPE_PUBLIC_KEY_CREDENTIAL のみで充足) |

## 既存テストへの影響 (再掲)

- `KeyNestCredentialProviderServiceTest` (#90 で導入、#99 で `onBeginCreateCredentialRequest`
  拡張): `onBeginGetCredentialRequest` の検証部分を本 Issue で **拡張** する。
  `onBeginCreateCredentialRequest` / `onClearCredentialStateRequest` の既存
  テストは Requirement 4.6 のとおり pass 維持。
- `CredentialProviderServiceManifestTest` / `CredentialProviderXmlTest` (#90):
  本 Issue で `<activity>` 追加のみ、`<service>` / `PasskeyCreateActivity`
  不変なので影響なし (Requirement 4.8)。
- `Migration_4_5_Test` / `PasskeyDaoTest` / `PasskeyRepositoryTest` (#91):
  schema / DAO に触らない、Repository は **追加メソッド** のみで既存 API は
  破壊しないので影響なし (Requirement 4.7)。`PasskeyRepositoryTest` には
  本 Issue 追加メソッド (`loadPrivateKey` / `incrementSignCount` / ロールバック)
  の単体テストを **追加** する (拡張、テスト要件参照)。
- `PasskeyCreatorTest` / `AuthenticatorDataTest` (#99): 本 Issue は #99 の
  registration 配下を一切触らないので影響なし (Requirement 4.7)。
- `InternetPermissionAbsenceTest`: `INTERNET` を追加しない方針継承 (NFR 4.1) で
  影響なし。
- 既存 `KeyNestAutofillService` 関連テスト: autofill 経路を触らないので影響なし
  (NFR 2.1)。

## テスト要件 (Requirement 4 の補足 / 具体化)

| Test class | 種別 | SDK | 検証ポイント | 要件対応 |
|---|---|---|---|---|
| `PasskeyAssertionTest` | 純 JVM unit | — | `authenticatorData` の rpIdHash / flags 0x05 / signCount big-endian / 全長 37 byte / ES256 署名 verify (SHA256withECDSA, ASN.1 DER) | 4.1 / 4.2 / 3.x |
| `KeyNestCredentialProviderServiceTest` (拡張) | Robolectric | 34 | `allowCredentials = 空` で discoverable 全件 / `allowCredentials = 指定` で交差集合 / 候補 0 件で空応答 / `onBeginCreateCredentialRequest` 既存テスト残置 | 4.3 / 4.6 / 1.x / 1.5 |
| `PasskeyAuthActivityTest` (任意 / design で要否確定) | Robolectric | 34 | `BiometricAuthenticator.authenticate` succeed → signCount +1 → 署名 → OS 応答 / cancel → signCount 不変 / 署名失敗注入 → signCount ロールバック | 2.x / 3.3 / 3.4 / 4.4 / 4.5 |
| `PasskeyRepositoryTest` (拡張) | unit (in-memory Room) | — | `loadPrivateKey` で `(privateKeyIv, encryptedPrivateKey)` を AES-GCM 復号して平文 PKCS#8 を返すこと / `incrementSignCount` が DAO を 1 回呼んで新 signCount を返すこと / 同じ credentialId で 2 回呼ぶと +2 / ロールバック API で旧値復元 | 3.1 / 3.3 / 3.4 / 4.4 / 4.5 |

CI / API 34 emulator 整備状況に応じて Instrumentation test
(`@SdkSuppress(minSdkVersion = 34)`) を `@Ignore` placeholder で追加するか否かは
design.md で確定する (#90 / #99 と同方針)。

## 未解決事項 / 確認事項

> 本 Issue の確認事項 3 件は人間確定済み (本ドキュメント「確定済み判断事項」
> セクション)。以下は design.md / 実装フェーズで詰める細部のみ列挙する。

1. **`PasskeyRepository.incrementSignCount(credentialId)` の戻り値仕様**:
   - DAO の `incrementSignCount(credentialId, timestamp)` は戻り値を返さない
     (Room の `@Query("UPDATE ...")` の戻り値は通常 `Int` (affected rows) で、
     更新後の値ではない)。
   - Repository 側で `runInTransaction { incrementSignCount(...); SELECT signCount }`
     を実行して新値を SELECT する形になるか、`incrementSignCount` 自体を
     `@Transaction` annotated な複合クエリにするか、design.md で確定する。

2. **signCount ロールバック API の最終形**:
   - 案 A: `setSignCount(credentialId: String, value: Long)` (絶対値で書き戻す)
   - 案 B: `decrementSignCount(credentialId: String, expectedValue: Long)`
     (compare-and-swap、競合検知あり)
   - 案 C: `runInTransaction { val old = ...; increment(); try { sign() }
     catch { setSignCount(old) } }` を Repository 内に閉じ込めた
     `signWithIncrement(credentialId, signer: (newSignCount: Long) -> ByteArray)`
     のような高階関数 API
   - 本 requirements 推奨は **案 C** (失敗時ロールバックを呼び出し側で書き
     忘れるリスクをなくす)。最終確定は design.md。

3. **`AllowCredentialsParser` の `requestJson` 解析詳細**:
   - `BeginGetPublicKeyCredentialOption.requestJson` の構造は WebAuthn の
     `PublicKeyCredentialRequestOptionsJSON` (Level 2 §5.1.4)。
   - `allowCredentials[].id` は base64url 文字列 (RP 側 JSON での表現)。
     KeyNest の `PasskeyEntity.credentialId` も base64url 文字列で揃っている
     ことを #91 / #99 で確認済み。
   - JSON parse 失敗時は #99 の `extractExcludeCredentialIds` と同じく
     **空リスト扱い** (defensive) とする方針。
   - 最終的な parse ロジック / fallback / validation は design.md で確定。

4. **`PublicKeyCredentialEntry` の displayName / accountName**:
   - OS Credential Manager UI に表示される文言。`userDisplayName` /
     `userName` / `rpDisplayName` の優先順位、null fallback、locale 別翻訳
     (`res/values/strings.xml` / `res/values-ja/strings.xml`) は design.md で
     確定。
   - 本 Issue では「PassKey 機能の文言は『PassKey』表記」(#89 確定) を
     maintain する範囲のみ requirement で確定済み。

5. **`androidx.credentials` バージョン互換性**:
   - #90 で `1.5.0` 確定済み。本 Issue で `PublicKeyCredentialEntry` (もしくは
     `CredentialEntry`) / `PendingIntentHandler.retrieveProviderGetCredentialRequest` /
     `setGetCredentialResponse` / `setGetCredentialException` /
     `GetCredentialException` / `GetCredentialCancellationException` /
     `GetCredentialUnknownException` の各 API が 1.5.0 で安定提供されているかを
     design.md で再確認する。

6. **`PasskeyAuthActivity` の lifecycle と Service の outcome receiver 連携**:
   - `onBeginGetCredentialRequest` の `outcome` は callback メソッドが return
     した時点で完了する (#99 と同じ前提)。`PasskeyAuthActivity` 起動 → 結果
     返却は **`CredentialEntry` の pending intent →
     `PendingIntentHandler.setGetCredentialResponse(...)` →
     `setResult(RESULT_OK)` → `finish()`** 経路で別経由になる (`outcome` は
     Service callback では使わない)。この前提を design.md で正式に確定する。
   - Activity 異常終了 (`onDestroy` 中断 / プロセス kill) 時に `outcome` も
     `setResult` も発火しない場合、OS / RP 側は何秒でタイムアウトするか /
     KeyNest 側はどうハンドルするか / signCount ロールバックがどう保証されるか
     を design.md で詰める。

7. **AuthenticationResponseJSON encoding 細部**:
   - base64url は **unpadded** (RFC 4648 §5) と **padded** の 2 形式があり、
     WebAuthn Level 2 §5.1.4 / `androidx.credentials.PublicKeyCredential` が
     どちらを要求するかを design.md で確認する。
   - `kotlinx.serialization.json.Json` の field 順序 / 余分 whitespace の有無 /
     null フィールドの省略可否を WebAuthn 仕様と突き合わせる。
   - 本 requirements では「`response.userHandle` を **常に含める**」とした
     (Requirement 3.7) が、WebAuthn 上 userHandle は optional な場合がある。
     KeyNest は両モード (discoverable / non-discoverable) で `userHandle` を
     保管しているため、常に返して問題ないはず。最終確認は design.md で行う。

8. **ES256 署名フォーマット (raw r||s vs ASN.1 DER) の最終確認**:
   - WebAuthn Level 2 §6.3.3 step 23 / RFC 8152 §8.1 (COSE Algorithm ES256)
     は ASN.1 DER 形式を要求 (本 requirements は ASN.1 DER で確定、
     Requirement 3.5)。
   - JCE `Signature.getInstance("SHA256withECDSA")` は標準で ASN.1 DER を返す
     ため、変換不要であることを design.md で確認する。

## carve-out (本 Issue から外した案件)

| 案件 | 行き先 | 本 Issue (#100) への影響 |
|---|---|---|
| WebAuthn extensions (`hmac-secret` / `prf` / `largeBlob` / `appid` 等) | 別 Issue (umbrella #89 Out of Scope) | 本 Issue では `clientExtensionResults = {}` / `authenticatorData.flags` の ED ビット = 0 (決定 2) |
| caBLE / Hybrid transport | 別 Issue (umbrella #89 Out of Scope) | 本 Issue は端末ローカルの PassKey のみ提供 |
| attestation の検証 (RP 側 attestation 受領後の検証ロジック) | RP 側責務 | 本 Issue は signature のみ返却 |
| 一覧 UI への PassKey 表示 | #89 分割案 5 | 本 Issue では UI 側に何も追加しない |
| 個別管理 UI (rename / 削除) | #89 分割案 6 | 同上 |
| 設定画面 / OS 設定導線 | #89 分割案 7 | 同上 |
| README / Privacy / Support docs 更新 | #89 分割案 8 | 同上 |
| CI に API 34 emulator を導入し instrumentation test を実行可能にする | #94 (#90 から carve-out 済み) | 本 Issue でも Robolectric 中心。Instrumentation test 追加要否は design.md |

## 用語集

- **認証セレモニー (authentication ceremony)**: WebAuthn の credential 利用手順
  (`navigator.credentials.get()`)。RP の challenge / `allowCredentials` を受け、
  authenticator が `authenticatorData` を組み立てて ES256 署名し、
  AuthenticationResponseJSON を返す。本 Issue が実装する範囲。
- **登録セレモニー (registration ceremony)**: WebAuthn の credential 作成手順
  (`navigator.credentials.create()`)。#99 が実装済み。
- **`authenticatorData`**: WebAuthn Level 2 §6.1 で定義される binary 構造。
  認証セレモニーでは `rpIdHash` (32) + `flags` (1) + `signCount` (4) のみ
  (= 37 byte、attestedCredentialData / extensions なし)。
- **`clientDataJSON`**: RP から authenticator に渡される JSON。
  `{ type, challenge, origin, ... }`。本 Issue では authenticator (KeyNest) は
  parse せず、`SHA-256(clientDataJSON)` を `authenticatorData` に concat して
  署名するのみ。
- **`clientDataHash`**: `SHA-256(clientDataJSON.toByteArray(UTF_8))`。
  ES256 署名入力の後半 (`authenticatorData || clientDataHash`)。
- **ES256**: ECDSA using P-256 and SHA-256 (RFC 7518 §3.1)。JCE では
  `Signature.getInstance("SHA256withECDSA")`。WebAuthn の COSE `alg = -7` に
  対応。
- **AuthenticationResponseJSON**: WebAuthn Level 2 §5.1.4 で定義される JSON
  構造。`id` / `rawId` / `response.{clientDataJSON, authenticatorData, signature,
  userHandle}` / `type` / `clientExtensionResults` を持つ。OS は
  `PublicKeyCredential(authenticationResponseJson)` 経由で RP に渡す。
- **discoverable credential (resident key)**: `allowCredentials` 無しで RP に
  提示できる PassKey。`PasskeyEntity.isDiscoverable = true` の row。
  `listDiscoverableByRpId` で抽出される。
- **non-discoverable credential**: `allowCredentials` 必須の PassKey。
  `PasskeyEntity.isDiscoverable = false` の row。`allowCredentials[].id` で
  特定される。
- **signCount (signature counter)**: WebAuthn Level 2 §6.1 の 4 byte unsigned
  int。authenticator が assertion を返すたびに +1 する。RP は前回値と比較して
  クローン authenticator 検知に用いる。本 Issue では **Option A** (署名前
  インクリメント + 失敗時ロールバック) で運用。
- **UP / UV / AT / ED フラグ**: `authenticatorData.flags` のビット。
  UP = User Presence (0x01) / UV = User Verified (0x04) / AT = attestedCredentialData
  present (0x40) / ED = Extension data (0x80)。本 Issue は **UP | UV = 0x05**
  で固定 (決定 1 / 決定 2)。
- **`PublicKeyCredentialEntry`**: `androidx.credentials.provider.PublicKeyCredentialEntry`。
  OS Credential Manager UI に「KeyNest 内の PassKey で署名」候補として表示
  される行。
- **`GetCredentialException`**: `androidx.credentials.exceptions.GetCredentialException`。
  認証セレモニーで OS に異常を返すための例外。サブクラスとして
  `GetCredentialCancellationException` / `GetCredentialUnknownException` 等。
- **Option A (本 Issue)**: signCount を署名前にインクリメントし、署名失敗時に
  DB / Keystore 上の値をロールバックする方式。Issue #100 コメント「AでOK」で
  人間確定 (決定 3)。

## 変更履歴 / 出典

- **本 requirements の根拠** (作成日: 2026-05-22):
  - Issue #100 本文 (Requirement 1〜4 / 確認事項 1〜3) — `gh issue view 100` で
    取得した本文。
  - Issue #100 コメント (人間確定):
    - PM triage コメント (PM 発): 確認事項 1 (UP/UV) と 確認事項 2 (extensions)
      は Claude 自律判断可能、確認事項 3 (signCount) のみ人間判断必須と整理。
    - 人間コメント「AでOK」: 確認事項 3 → **Option A (署名前インクリメント +
      失敗時ロールバック) 確定** = 決定 3。
    - 確認事項 1 / 2 は PM triage の整理に従って本 requirements で確定値を
      固定 = 決定 1 / 決定 2 (UP/UV = 1 固定 / extensions 空)。
  - Parent Issue #89 本文 / 確認事項 1〜7 — residentKey 両対応 / Device
    Credential フォールバック / 表記「PassKey」/ minSdk 26 維持 / attestation
    `none` / エクスポート禁止が確定済み。
  - 依存 Issue #90 requirements.md / design.md — `KeyNestCredentialProviderService`
    package 配置 / `androidx.credentials` 1.5.0 / `@RequiresApi(34)` /
    `tools:targetApi="34"` / Service 配線済みである前提を継承。
  - 依存 Issue #91 requirements.md / design.md — `PasskeyEntity` schema (v5) /
    `PasskeyDao` 公開 IF (`findByCredentialId` / `findByRpIdAndUserHandle` /
    `listDiscoverableByRpId` / `incrementSignCount(credentialId, timestamp)`) /
    Keystore alias 命名 (`passkey_<credentialId>` を #99 で `keynest_passkey_<credentialId>`
    に揃える方針確定済み) を継承。
  - 依存 Issue #99 requirements.md / 実装コード — `PasskeyRepository` の inline
    追加 (`save` / `findByCredentialId` / `findByRpIdAndUserHandle` / `delete`
    の 4 本) / `PasskeyRepositoryImpl` の AES-GCM 暗号化境界 /
    `credentialprovider/registration/` 配下のパッケージ規約 /
    `BiometricAuthenticator` の `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` 構成 /
    `KeynestAaguid` 定数 (本 Issue では参照しない) / Keystore alias
    `keynest_passkey_<credentialId>` を継承。
  - 既存コード:
    - `app/src/main/java/.../credentialprovider/KeyNestCredentialProviderService.kt`
      = `onBeginGetCredentialRequest` が `#90 stub retained — authentication
      ceremony is #89 分割案 4.` コメント付きで空応答スタブ状態 (本 Issue で
      差し替える対象)。
    - `app/src/main/java/.../domain/repository/PasskeyRepository.kt` =
      現状 `save` / `findByCredentialId` / `findByRpIdAndUserHandle` / `delete`
      の 4 本のみ。本 Issue で `loadPrivateKey` / `incrementSignCount` /
      ロールバック補助の 3 本を追加する。
    - `app/src/main/java/.../data/dao/PasskeyDao.kt` = 認証セレモニーで必要な
      `findByCredentialId` / `listDiscoverableByRpId` / `incrementSignCount`
      が #91 で実装済み。本 Issue は DAO に変更を加えない。
    - `app/src/main/java/.../auth/BiometricAuthenticator.kt` =
      `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` + `AuthResult` sealed class を
      本 Issue から再利用 (#99 と同パターン)。
    - `app/src/main/java/.../security/{AesGcmCipher, KeystoreKeyProvider,
      EncryptedBlob}.kt` = `keynest_passkey_<credentialId>` alias の
      `KeystoreKeyProvider` + `AesGcmCipher.decrypt(blob)` を `loadPrivateKey`
      で利用する既存 API。
    - WebAuthn assertion 関連クラス (`PasskeyAssertion` / `PasskeyAuthActivity`)
      は **本 Issue 時点では未実装** (リポジトリ全体を `Glob` で確認済み)。
      本 Issue ですべて新規追加する。
- **本 requirements で新たに導入した推測**: なし。すべての要件は上記出典の
  事実 (Issue 本文 / 人間コメント / 依存 design.md / 既存コード) のいずれかに
  trace 可能。
- 既存 requirements.md のスタイル踏襲先:
  - `docs/specs/99-feat-passkey-onbegincreatecredentialrequ/requirements.md`
    (本 Issue と同位の対 = 登録セレモニー。章立て / EARS 形式 / 「決定事項」
    「Out of Scope」「未解決事項」「carve-out」「変更履歴 / 出典」の節構成 /
    表ベースのスコープ整理を踏襲)。
  - `docs/specs/91-feat-passkey-room-migration-passkeyentit/requirements.md`
    (確定済の設計判断 / Acceptance Criteria 末尾の `(#xx-Ry.z)` トレース
    フォーマットを踏襲)。
  - `docs/specs/90-feat-passkey-credentialproviderservice-m/requirements.md`
    (umbrella 継承事項 / API ゲーティング表現 / NFR 構成を踏襲)。
