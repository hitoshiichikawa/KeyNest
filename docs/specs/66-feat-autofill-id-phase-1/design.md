# Design Document — Issue #66 feat(autofill): カスタムフィールド機能 Phase 1

> 関連: [requirements.md](./requirements.md) / Issue #66 / Phase 2・3 は別 Issue

## 0. 制約（冒頭明示）

1. 既存テスト（unit / instrumented / `Migration_1_2_Test` を含む）を一切 fail させないこと（requirements §12-1）。
2. `develop` および `main` ブランチへの直接 push は禁止。feature branch + PR レビュー経由（requirements §12-2 / 12-3）。
3. Credential Manager API 経路には一切手を加えない（requirements §12-5）。
4. 既存 username / password 補完挙動を変更しない（requirements §12-6 / Req 4.3）。
5. 本書は実装コードを含まず、データクラス定義・API シグネチャ案のみを擬似コードで示す。

---

## 1. 概要

### 1.1 目的（1 段落要約）

`Credential` ドメインモデルに任意の `fieldKey → value` ペア集合（`customFields`）を追加し、Autofill 時に AssistStructure の各 field から抽出した match キー集合と部分一致 + 正規化照合で照らし合わせて Dataset に値を埋め込む。既存の username / password 補完経路は不変のまま、独立した第三のフィルパスとして実装する。データレイヤは Room schema v2 → v3 マイグレーションで 1 列追加し、追加値は既存 AES-GCM 鍵で暗号化保存する。

### 1.2 スコープ / Out of Scope

requirements.md §2 を参照。本設計書は requirements.md §10 の未決事項（JSON シリアライザ選定 / TypeConverter 配置 / 正規化関数の配置 / Dataset presentation / 暗号化粒度 / `fieldKey` 重複ハンドリング / レイアウト方式）に対する architect 決定を含む。

---

## 2. アーキテクチャ方針

### 2.1 レイヤ責務

| レイヤ | 既存責務 | Issue #66 で追加する責務 |
|---|---|---|
| domain/model | `Credential` / `EncryptedCredentialRecord` / `PlaintextCredential` | `CustomField` data class、`Credential.customFields`、`EncryptedCredentialRecord.customFieldsCiphertext/Iv`、`PlaintextCredential.customFields` |
| domain/usecase | `SaveCredentialUseCase` / `UpdateCredentialUseCase` / `UnlockVaultUseCase` / `ResolveAutofillCandidatesUseCase` | 上記 use case の入出力に customFields を追加。`AutofillCandidate` には fieldKey の **平文ラベル list** のみを載せる（暗号化されたままの value 群は載せない、§6.4 参照） |
| data/entity | `CredentialEntity`（Room） | 新規列 `custom_fields_ciphertext: BLOB NOT NULL` / `custom_fields_iv: BLOB NOT NULL` を追加 |
| data/migration | `Migration_1_2` | `Migration_2_3` を新設し、上記 2 列に空配列を表す暗号化済み初期値を入れる（§4 参照） |
| security | `AesGcmCipher` / `EncryptedBlob` | 流用。新規 codec `EncryptedCustomFieldsCodec` を追加し、`List<CustomField>` ⇔ JSON ⇔ AES-GCM の往復をカプセル化 |
| autofill/parser | `AutofillFieldHeuristics.classify` | `extractMatchKeys(descriptor)` 関数を追加。既存 `classify` は変更しない |
| autofill/builder | `FillResponseBuilder.buildLockedResponse` / `buildUnlockedDataset` | locked 経路で customField 用 AutofillId 集合を含むよう拡張、unlocked 経路で `Map<AutofillId, String>` を Dataset に流し込む |
| autofill/unlock | `AutofillUnlockActivity` | 復号後、customFields を含む完全データを `buildUnlockedDataset` に渡す経路を拡張 |
| ui/edit | `CredentialEditActivity` / `CredentialEditViewModel` | Advanced セクション配下に「カスタムフィールド」サブセクションを追加 |
| util | `SafeLogger` | 流用。本機能で追加する customField の value / fieldKey は `Redacted` で扱う |

### 2.2 既存設計との整合性

KeyNest はすでに **Locked Dataset（プレースホルダ + auth IntentSender）/ Unlocked Dataset（復号済み AutofillValue）** の二段構えで username / password を扱っている。customField もこの二段構えに完全に乗せる：

- **Locked 段階**（`KeyNestAutofillService.onFillRequest` → `FillResponseBuilder.buildLockedResponse`）: customField の値は復号せず、Dataset には PLACEHOLDER しか入らない。
- **Unlocked 段階**（`AutofillUnlockActivity` → `FillResponseBuilder.buildUnlockedDataset`）: BiometricPrompt 後に `UnlockVaultUseCase` が customField を含めて復号し、Dataset に AutofillValue を埋める。

この二段構えにより requirements Req 5.2 / 5.3 を自動的に満たす。

---

## 3. データモデル設計

### 3.1 ドメイン型（擬似コード）

```kotlin
// domain/model/CustomField.kt
data class CustomField(
    val fieldKey: String,   // ユーザー入力ラベル（正規化前、表示用）
    val value: String,      // 平文値。Credential（プレーンドメイン）には載せない（§3.2 参照）
)
```

- `fieldKey` / `value` ともに `String` 非 null。空文字列の意味付けは §9.3。
- `CustomField` は **plaintext を表す型** であり、`Credential` には載せない（後述）。

### 3.2 `Credential`（プレーンドメイン）に何を追加するか

要件文（Req 1.1 / 1.2）は「`Credential.customFields: List<CustomField>` を追加」と書かれているが、`Credential` クラスの設計コメント（既存 Credential.kt の Doc）は次のように明言している：

> Notably absent: the plaintext password. The domain layer only sees the encrypted blob via EncryptedCredentialRecord or, after explicit unlock, via the short-lived PlaintextCredential. Decrypted bytes never live on this aggregate.

すなわち `Credential` には plaintext を載せない設計が確立している。本機能でもこの方針を維持する。具体的には：

- `Credential` には **「customField の `fieldKey` 一覧のみ」** を載せる。値は載せない。
  - 型: `val customFieldKeys: List<String>`（空リスト既定）
  - 目的: list 画面など plaintext を必要としない UI でフィールド種別を表示できるようにするための補助情報。
- 完全な `List<CustomField>`（plaintext value 込み）は `PlaintextCredential` に載せる。
- 暗号化済みの `List<CustomField>` は `EncryptedCredentialRecord` に **1 つの BLOB ペア（ciphertext + IV）** として載せる。

ただし、Phase 1 では list 画面に customField 表示要件が無いため `customFieldKeys` は **載せないことも選択肢** である。実装簡素化のため Phase 1 では `Credential` への追加を行わず、必要になった時点（Phase 2 のサジェスト機能等）で追加する方針を**推奨**する。

**決定**: `Credential` には何も追加しない。`EncryptedCredentialRecord` と `PlaintextCredential` のみに customFields 関連プロパティを追加する。requirements の Req 1.1 と一見矛盾するが、Req 1.5（Credential model は平文 value を保持しない）と整合させると、`Credential` に空の `List<CustomField>` を持たせること自体に実用意味がない。実装フェーズで PM と再確認すること（§13 申し送り）。

### 3.3 `EncryptedCredentialRecord` / `PlaintextCredential` への追加

```kotlin
data class EncryptedCredentialRecord(
    // ... 既存フィールド ...
    val customFieldsCiphertext: ByteArray,  // 空配列なら空 JSON "[]" を暗号化した結果
    val customFieldsIv: ByteArray,          // 12 byte AES-GCM IV
)

data class PlaintextCredential(
    // ... 既存フィールド ...
    val customFields: List<CustomField>,    // close() 時に CharArray と同様に best-effort で wipe する必要がある
)
```

### 3.4 暗号化粒度の選択肢比較

| 観点 | 案 A: 配列全体を 1 ciphertext | 案 B: value 単位で暗号化 |
|---|---|---|
| マッチ時オーバーヘッド | 一括復号 1 回（unlocked 段階のみ）。locked 段階では復号なし。 | unlocked 段階で件数分復号。10 件で 10 回 `Cipher.init` + `doFinal`。 |
| 鍵ローテーション容易性 | ローテーション時に 1 回 decrypt → re-encrypt するだけ | 件数分のループが必要 |
| JSON シリアライズ複雑度 | 上位で List ⇔ JSON、下位で JSON ⇔ AES-GCM の 2 段。明快 | 各要素を `EncryptedCustomField(fieldKey, ciphertext, iv)` として Room に持つには別テーブル + JOIN が必要、または `BLOB` の連結を JSON 文字列にする等の不自然な構造になる |
| Locked Dataset 構築時の効率 | locked 段階では `customFieldsCiphertext` を一切触らない。fieldKey 平文も使わない（match は unlocked 後にも実行可能なので、後述設計では match を unlocked 段階に寄せる） | 同上 |
| Room schema 影響 | 既存テーブルに 2 列追加で済む | 別テーブル新設 or JSON BLOB 配列で複雑化 |
| 機密性 | 同等（同じ鍵） | 同等（同じ鍵） |

**推奨**: **案 A（配列全体を 1 ciphertext）**。理由は (1) Room schema 変更が最小（列 2 つの追加で完結）、(2) 既存 `EncryptedBlob` / `AesGcmCipher` をそのまま流用でき責務追加が `EncryptedCustomFieldsCodec` 1 クラスに収まる、(3) 件数 ≤ 10 でも暗号化往復のループは無駄、(4) 鍵ローテーション容易性が高い、(5) requirements Req 1.3 の「Room 列 `custom_fields` を追加し、JSON シリアライズで保存」を「**JSON を暗号化した BLOB を 2 列で保存**」と解釈して整合する。

### 3.5 `CredentialEntity`（Room）への列追加

```kotlin
@Entity(tableName = "credentials", indices = [Index("package_name")])
data class CredentialEntity(
    // ... 既存列 ...

    @ColumnInfo(name = "custom_fields_ciphertext", typeAffinity = ColumnInfo.BLOB)
    val customFieldsCiphertext: ByteArray,

    @ColumnInfo(name = "custom_fields_iv", typeAffinity = ColumnInfo.BLOB)
    val customFieldsIv: ByteArray,
)
```

- 列名は既存 `password_ciphertext` / `password_iv` のネーミング規約に揃える。
- 両列とも `NOT NULL`。空配列も「空 JSON 文字列 `[]` を暗号化した結果」として常に値が入る（NULL は使わない）。
- `equals` / `hashCode` / `toString` も既存パターン（`contentEquals` / `<NB>` プレビュー）に揃える。

---

## 4. Room schema v2 → v3 migration

### 4.1 ALTER TABLE 文

```sql
ALTER TABLE credentials ADD COLUMN custom_fields_ciphertext BLOB NOT NULL DEFAULT x'';
ALTER TABLE credentials ADD COLUMN custom_fields_iv BLOB NOT NULL DEFAULT x'';
```

SQLite の `ALTER TABLE ADD COLUMN` は `DEFAULT` 句にリテラルしか許さない（関数呼び出し不可）ため、空 BLOB（`x''`）を default として入れる。

**重要**: 「空配列の暗号化済み BLOB」を migration 時に生成することは **migration では行わない**。理由：

- migration は同期 SQL のみ。`AesGcmCipher` の `getOrCreateKey()` は Android Keystore API（バックグラウンドスレッド前提・I/O 例外あり）であり migration 経路で呼び出すべきでない。
- 既存行の `custom_fields_ciphertext` / `custom_fields_iv` が空 BLOB（length=0）の場合、Repository 層の `toEncryptedRecord()` または `UnlockVaultUseCase` で「空 BLOB == 未初期化 == 空 customFields」と解釈する**特例処理**を入れる。
- 初回 update 時に空 customFields でも空配列を暗号化した値が保存され、以降は通常パスになる。

この方針により migration は純粋に SQL のみで完結し、`Migration_2_3_Test` は v2 v3 間で credential 行が保持され、新 2 列が空 BLOB を持つことを SQL 直読で検証できる。

### 4.2 Room 設定変更

`KeyNestDatabase`:
```kotlin
@Database(entities = [CredentialEntity::class], version = 3, exportSchema = true)
abstract class KeyNestDatabase : RoomDatabase() { ... }
```

`companion object.create()` の `addMigrations(Migration_1_2)` を `addMigrations(Migration_1_2, Migration_2_3)` に変更。

### 4.3 v3.json schema 出力

- `ksp.arg("room.schemaLocation", ...)` は既に設定済み（`app/build.gradle.kts` line 12-14）。
- gradle ビルドで `app/schemas/io.github.hitoshiichikawa.keynest.data.KeyNestDatabase/3.json` が生成される。
- 該当ディレクトリは **現行 applicationId 配下**であり `.gitignore` の対象外（line 49-50 は旧 applicationId のみ ignore）。よって `3.json` を git tracking 対象に追加 commit する必要がある。Issue #63 で確立した運用に従う。

### 4.4 既存行への初期化方針（再掲）

空 BLOB を default で入れ、Repository 層で「空 BLOB == 空 customFields」と解釈する。これにより migration 時の Keystore 呼び出しを回避し、既存行は破壊されない。

---

## 5. TypeConverter / JSON シリアライザ選定

### 5.1 「TypeConverter は不要」という決定

§3.4 で案 A（配列全体を 1 ciphertext）を採用したため、Room の `@TypeConverter` は **不要**。`custom_fields_ciphertext` / `custom_fields_iv` は素の `BLOB` であり、`List<CustomField>` ⇔ `String`（JSON）⇔ `ByteArray`（UTF-8）⇔ `EncryptedBlob` の変換は Repository 層の mapping 関数（既存 `toEntity` / `toEncryptedRecord` の隣）で行う。これにより Room の `@TypeConverters` アノテーション追加も不要。

requirements §10-2 で挙がっていた `@TypeConverter` 配置論点は本設計で「TypeConverter を導入しない」決定により消滅する。

### 5.2 JSON シリアライザ候補比較

ただし「`List<CustomField>` ⇔ JSON 文字列」変換は必要。候補：

| 候補 | 依存追加コスト | 既存依存との重複 | APK サイズ影響 | KSP/kapt | テスト相性 |
|---|---|---|---|---|---|
| `kotlinx.serialization` | 中（`org.jetbrains.kotlin.plugin.serialization` plugin + runtime ~280KB） | なし | +約 280KB | KSP 自動生成（plugin 適用で完結） | Robolectric/JVM どちらも OK |
| Moshi | 中（runtime ~110KB + KSP codegen ~50KB） | なし | +約 160KB | KSP 必要（既存 KSP 設定流用可） | Robolectric/JVM どちらも OK |
| Gson | 低（runtime ~290KB、reflection ベース） | なし | +約 290KB | 不要 | Robolectric/JVM どちらも OK、ただし `@SerializedName` 必須でない自由度の高さが暗号化境界では曖昧さを生む |
| 自前 JSON エンコーダ | ゼロ依存 | 不要 | ゼロ | 不要 | 完全 control。ただしエスケープ実装ミスのリスクあり |
| `org.json`（android.jar 同梱） | ゼロ依存 | 不要 | ゼロ | 不要 | `JSONObject` / `JSONArray` ベース。Robolectric では動作するが unit test で android jar が必要、コード可読性は中 |

**推奨**: **`kotlinx.serialization`**。理由：

1. Kotlin 公式（JetBrains 製）で長期サポートが約束される。
2. `@Serializable` data class への変換 / 逆変換が型安全で、`CustomField` のような単純な 2 フィールド型では 1 行で済む。
3. KSP は既に有効化済み（`build.gradle.kts` line 4 / 12-14）でセットアップ容易、plugin 1 行追加のみ。
4. 既存 Room / KSP との衝突なし（Room の KSP プロセッサと別ファサード）。
5. 将来 Phase 2 の `detected_fields` ログ等で JSON シリアライズ需要が増える可能性が高く、ライブラリ選定の一貫性として有効。
6. Moshi も類似コスト・性能だが、Kotlin 公式統合の優位性で kotlinx.serialization を採る。

**APK サイズの妥当性**: KeyNest の主目的（パスワード保管 + Autofill）に対し +280KB は許容範囲。Phase 2 以降で複数の JSON 化処理が増える見込みであれば、ライブラリ追加は早期の方が累積便益が高い。

### 5.3 シリアライズ用型定義

```kotlin
// security/CustomFieldsJson.kt（または同 package 内 codec）
@Serializable
internal data class CustomFieldJson(
    val k: String,   // fieldKey（短縮キーで JSON サイズを微減）
    val v: String,   // value
)
```

キー名を `k` / `v` に短縮するかどうかは tasks.md の T2 で実装者判断とする。`fieldKey` / `value` のままでも実用上問題ない。

### 5.4 不正 JSON / 復号失敗時のフォールバック

- decrypt 失敗時: `UnlockVaultUseCase` 既存パターン（`UnlockFailure.Decrypt`）に揃え、credential 全体の unlock を失敗扱い。
- JSON parse 失敗時: customFields を空リストとして返す（fail-open）。username/password は使えるべき。SafeLogger.warn で警告。

---

## 6. 暗号化との統合

### 6.1 新規 codec の責務

```kotlin
// security/EncryptedCustomFieldsCodec.kt
class EncryptedCustomFieldsCodec(
    private val cipher: AesGcmCipher,
    private val json: kotlinx.serialization.json.Json = Json,
) {
    /** 空リストの場合も常に "[]" を暗号化した非空 BLOB を返す。 */
    fun encrypt(customFields: List<CustomField>): EncryptedBlob

    /** 空 BLOB（length=0）の場合は空リストを返す（migration 後の既存行向け fallback）。 */
    fun decrypt(blob: EncryptedBlob): List<CustomField>
}
```

- `encrypt`: `List<CustomFieldJson>` → `Json.encodeToString` → UTF-8 bytes → `cipher.encrypt`。中間 bytes を `Arrays.fill(0)` で wipe。
- `decrypt`: 空 BLOB なら空リスト、非空なら `cipher.decrypt` → UTF-8 string → `Json.decodeFromString`。
- 既存 `AesGcmCipher` をそのまま流用。鍵分離なし（requirements Req 1.4 で「既存 username/password と同じ AES-GCM 鍵」と明記）。

### 6.2 各 use case への組み込み

| use case | 変更点 |
|---|---|
| `SaveCredentialUseCase` | `NewCredentialInput.customFields: List<CustomField> = emptyList()` を追加。`encrypt` して `EncryptedCredentialRecord.customFieldsCiphertext/Iv` に詰める |
| `UpdateCredentialUseCase` | `UpdateCredentialInput.customFields: List<CustomField>?` を追加（null = 既存維持、空リスト = 全削除）。null/非 null 判定は newPassword の既存パターンに揃える |
| `UnlockVaultUseCase` | decrypt 後、`EncryptedCustomFieldsCodec.decrypt` で `customFields` を取り出し `PlaintextCredential.customFields` に詰める |
| `ResolveAutofillCandidatesUseCase` | locked 段階のため変更最小。`AutofillCandidate` には customFields の **fieldKey 一覧（平文ラベル）** だけ載せる（match を locked 段階で行うため） |

### 6.3 復号タイミング

locked → unlocked 遷移時のみ（既存 password と同じ）。Locked Dataset には value を含めない（requirements Req 5.2 / 5.3）。

### 6.4 「match を locked 段階に行うべきか unlocked 段階に行うべきか」

**重要な設計判断**。fieldKey は plaintext で `AutofillCandidate` に載るため、match 自体は locked 段階で行える。しかし value は復号されないため、locked Dataset には customField 用の AutofillId list だけが含まれ、unlocked 段階で value を埋めることになる。

**決定**: **match は locked 段階で行い**、locked Dataset には「どの AutofillId にどの fieldKey インデックスを埋めるか」のマッピング情報を `Intent` の extras 経由で `AutofillUnlockActivity` に渡す。

具体的には、`buildLockedResponse` 経由でビルドされる `Intent` に以下を追加する：

```
EXTRA_CUSTOM_FIELD_AUTOFILL_IDS: ArrayList<AutofillId>  // 入力 AutofillId
EXTRA_CUSTOM_FIELD_KEY_INDICES:  IntArray              // 対応する customField index (Credential.customFields[i] の i)
```

`AutofillUnlockActivity` は復号後に `PlaintextCredential.customFields[i].value` をこれら AutofillId に対して setValue する。

**理由**: locked 段階で match することで、unlock 後の処理時間（NFR 3.1 の応答 SLA）に影響しない。また locked Dataset の placeholder 設定にもこれらの AutofillId が必要（既存 password の挙動と同じく、locked Dataset は影響する AutofillId 全てに placeholder + auth IntentSender を持つ必要がある）。

---

## 7. AutofillFieldHeuristics 拡張

### 7.1 新 API シグネチャ

既存 `classify(descriptor) -> Role` には**一切手を入れない**（Req 4.3）。並列に新関数を追加する：

```kotlin
internal object AutofillFieldHeuristics {

    // 既存（変更なし）
    fun classify(descriptor: FieldDescriptor): Role { ... }

    // 新規
    /**
     * descriptor から match 用キー候補集合を抽出して返す。
     * 抽出対象: autofillHints / hint / idEntry / contentDescription
     * （AssistStructure.ViewNode.text は editable 入力中の値を含むため
     *  含めない。requirements §3 / 用語定義「match キー」と差分があれば
     *  実装者が確認のこと。後述 §13 申し送り参照）
     *
     * 戻り値: 正規化（小文字化 + 全空白除去）後の文字列集合。
     * 重複は除去するが順序は問わない。
     */
    fun extractMatchKeys(descriptor: FieldDescriptor): Set<String>

    /**
     * fieldKey / match キーで共有する正規化関数。
     * 仕様: 小文字化 + Unicode 空白文字（半角・全角・タブを含む \p{Z} + \t / \r / \n）を除去。
     */
    fun normalizeKey(raw: String): String
}
```

### 7.2 抽出対象の確定

requirements §3 用語定義は match キー = `autofillHints / hint / resourceId / text / contentDescription`。一方 `text` は editable 入力中の値（ユーザーがすでに入力した文字）を含むため、安易に match キーに使うと「`fieldKey="社員"` の credential が `text="社員 田中太郎"` の入力済み氏名フィールドに誤マッチ」する事故が起きる。

**決定**: Phase 1 では `text` を **match キーから除外**する。requirements 用語定義と差分が生じるが、誤マッチ低減のため。tasks.md で実装者がこの判断をテストで担保する（§13 申し送り）。

### 7.3 正規化処理（NFR 5）

`AutofillFieldHeuristics.normalizeKey(raw)` を **単一の utility** として配置し、`FillResponseBuilder` 側もこの関数を呼ぶ。これにより requirements NFR 5（一貫性）を満たす。Phase 2 のサジェスト経路でも同一関数を共有可能。

正規化規則の擬似コード：
```kotlin
fun normalizeKey(raw: String): String =
    raw.lowercase()
       .filter { !it.isWhitespace() && it !in FULL_WIDTH_SPACES }
// FULL_WIDTH_SPACES = setOf('　', ' ', ' ', ...) 等
```

Kotlin の `Char.isWhitespace()` は `　` 全角スペースを含むため、シンプルに `.filter { !it.isWhitespace() }` で済むことを実装者は確認すること。

---

## 8. FillResponseBuilder 拡張

### 8.1 match algorithm

入力:
- `credential.customFields: List<CustomField>`（順序保持）
- `assistFields: List<FieldDescriptor + AutofillId>`（AssistStructureParser が抽出した全 editable view、既存 parser は username/password のみ抽出するため拡張が必要）

出力:
- `matches: List<Pair<AutofillId, customFieldIndex>>` — 同一 AutofillId は最初に match した customField のみ採用、同一 customField は match した全 AutofillId に適用

擬似アルゴリズム（O(F × C) = 500 × 10 = 5000 ops、NFR 3 範囲内）:
```
for each assistField in assistFields:
    matchKeys = extractMatchKeys(assistField.descriptor)
    for each (idx, customField) in credential.customFields.withIndex():
        normalizedKey = normalizeKey(customField.fieldKey)
        if normalizedKey is blank: continue
        if any matchKey in matchKeys contains normalizedKey:
            matches.add(assistField.autofillId, idx)
            break  // 同一 field が複数 customField と match した場合は最初を採用（Req 4.4）
```

- Req 4.4: `break` で実現。
- Req 4.5: 同一 customField が複数 field と match することは外側の for で許容される。

### 8.2 AssistStructureParser の拡張

既存 `parse()` は `ParsedFields(usernameId, passwordId)` のみ返す。拡張：

```kotlin
data class ParsedFields(
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val customFieldCandidates: List<CustomFieldCandidate>,  // 新規
)

data class CustomFieldCandidate(
    val autofillId: AutofillId,
    val descriptor: AutofillFieldHeuristics.FieldDescriptor,
)
```

- 既存 walk loop で username/password role でも Unknown でもない（または Unknown 含めて全 editable view）を `customFieldCandidates` に追加。
- 既存の `hasUsernameAndPassword` 早期 break は維持しない（customField match のため全 view を訪問する必要あり）。または `customFieldCandidates` 抽出専用の walk を別途行う。後者は performance trade-off で要検討（§13 申し送り）。

**推奨**: `customFieldCandidates` を含めて 1 回の walk で抽出。早期 break は customField 検出件数が 0 の場合のみ。`MAX_NODES = 500` の上限は維持。

### 8.3 buildLockedResponse 拡張

```kotlin
fun buildLockedResponse(
    candidates: List<AutofillCandidate>,
    usernameAutofillId: AutofillId?,
    passwordAutofillId: AutofillId?,
    customFieldCandidates: List<CustomFieldCandidate>,  // 新規
    inlineSpecs: List<InlinePresentationSpec> = emptyList(),
): FillResponse?
```

各 candidate ごとに：
1. customField match algorithm を実行し、`Map<AutofillId, customFieldIndex>` を得る。
2. 該当 AutofillId 全てに対して既存 `attachLockedValue` と同様に placeholder + presentation を setValue する。
3. `AutofillUnlockActivity.newIntent` に customField AutofillId list と customFieldIndex 配列を渡す（§6.4）。

### 8.4 buildUnlockedDataset 拡張

```kotlin
fun buildUnlockedDataset(
    usernameAutofillId: AutofillId?,
    usernameValue: String?,
    passwordAutofillId: AutofillId?,
    passwordValue: CharSequence?,
    customFieldValues: Map<AutofillId, String>,  // 新規
    label: String,
): Dataset
```

customFieldValues の各エントリに対して `builder.setValue(id, AutofillValue.forText(value))` を呼ぶ。既存パスワード設定と同じ単純なループ。

### 8.5 AutofillUnlockActivity 経由の復号フロー変更点

`newIntent` シグネチャ拡張:
```kotlin
fun newIntent(
    context: Context,
    credentialId: Long,
    usernameAutofillId: AutofillId?,
    passwordAutofillId: AutofillId?,
    customFieldAutofillIds: ArrayList<AutofillId>,  // 新規
    customFieldKeyIndices: IntArray,                // 新規（customField[i] の i）
): Intent
```

unlock 完了後の処理:
```
val plaintextFields = plain.customFields  // PlaintextCredential.customFields
val customFieldValues: Map<AutofillId, String> =
    customFieldAutofillIds.zip(customFieldKeyIndices.toList())
        .mapNotNull { (id, idx) -> 
            plaintextFields.getOrNull(idx)?.let { id to it.value } 
        }
        .toMap()
buildUnlockedDataset(..., customFieldValues = customFieldValues, ...)
```

エラー時（index 範囲外 / decrypt 失敗）は customField 部分を空マップ扱いとし username/password だけは fill する fail-open 戦略。

---

## 9. CredentialEditActivity / ViewModel 拡張

### 9.1 UI 構造（XML レイアウト）

Advanced セクション（`advanced_content`）配下、`row_credential_id` の直後に「カスタムフィールド」サブセクションを追加。

```
<LinearLayout id="row_custom_fields_section" orientation="vertical">
    <TextView>カスタムフィールド</TextView>
    <LinearLayout id="container_custom_fields" orientation="vertical">
        <!-- ViewModel state に従い動的に row を inflate / 削除 -->
    </LinearLayout>
    <Button id="btn_add_custom_field">+ フィールドを追加</Button>
</LinearLayout>
```

各 row は別 XML（`view_custom_field_row.xml`）で定義：

```
<LinearLayout orientation="horizontal">
    <TextInputLayout>
        <TextInputEditText id="input_field_key" inputType="text" hint="フィールド名" />
    </TextInputLayout>
    <TextInputLayout>
        <TextInputEditText id="input_field_value" inputType="text" hint="値" />
    </TextInputLayout>
    <ImageButton id="btn_remove_row" src="ic_close" contentDescription="削除" />
</LinearLayout>
```

- value 入力欄は `inputType="text"`（plain text、`textPassword` ではない）。Req 3.6 / Q3。

### 9.2 動的 row 追加方式の選定

| 案 | メリット | デメリット |
|---|---|---|
| LinearLayout + addView/removeView | 件数 ≤ 10 で十分。実装が単純、既存 `binding.advancedContent` と同じパターン | view recycle なし（10 件なら影響無視） |
| RecyclerView + Adapter | 件数増加に強い、項目間 drag-reorder 対応も容易 | 編集 state（EditText の text）を ViewModel と双方向同期させる boilerplate が増大、Advanced セクション内に nested RecyclerView を入れると ScrollView との衝突管理が必要 |
| ViewPager / 動的 Fragment | 過剰 | 過剰 |

**推奨**: **LinearLayout + addView/removeView**。件数上限 10、scroll 親が既に存在、KeyNest の Activity-scoped 状態管理パターンに合致。CredentialEditActivity に小さい helper（`renderCustomFieldRows(state)`）を追加し、`ViewModel.customFieldsState` の変化を購読して row を完全に再構築する素朴な実装で十分。

### 9.3 ViewModel state / reducer 拡張

`CredentialEditViewModel` に新規 state を追加：

```kotlin
data class CustomFieldsState(
    val rows: List<Row> = emptyList(),
) {
    data class Row(
        val rowId: Long,        // UI 内で row を identify するキー（addCounter で発番）
        val fieldKey: String,
        val value: String,
    )

    val canAddMore: Boolean get() = rows.size < MAX_CUSTOM_FIELDS

    companion object {
        const val MAX_CUSTOM_FIELDS = 10
    }
}

private val _customFields = MutableStateFlow(CustomFieldsState())
val customFields: StateFlow<CustomFieldsState> = _customFields.asStateFlow()

fun addCustomFieldRow()
fun removeCustomFieldRow(rowId: Long)
fun updateCustomFieldKey(rowId: Long, fieldKey: String)
fun updateCustomFieldValue(rowId: Long, value: String)
```

`save()` メソッドのシグネチャに `customFields: List<CustomField>` を渡せるよう拡張し、内部で：
- empty fieldKey を持つ row を silent drop（Req 3.4）。
- `NewCredentialInput.customFields` / `UpdateCredentialInput.customFields` に詰める。

`load()` メソッド: 編集モード時、`UnlockVaultUseCase` を **使わず** `EncryptedCredentialRecord` から customField を取り出す方法は存在しない（暗号化済みのため）。よって **編集モード時にも biometric unlock が必要** になる。これは現状の編集 UI と挙動が変わるため重大判断ポイント（§13 申し送り）。

**暫定設計**: 編集モードでは customField 部分を「ロック中（鍵アイコン）」として表示し、Tap で BiometricPrompt → unlock → 表示・編集可能にする。または「編集画面に入った時点で 1 度 unlock を要求する」UX に変える。詳細は実装フェーズで PM 決定。Phase 1 完成のためには **新規作成モードのみ customField を編集可、編集モードでは customField セクションを非表示 or read-only にする** 簡易対応も選択肢。

### 9.4 保存時 silent drop の実装場所

ViewModel `save()` 内で：
```kotlin
val effective = customFields.value.rows
    .filter { it.fieldKey.isNotBlank() }
    .map { CustomField(it.fieldKey, it.value) }
```

これにより空 fieldKey 行は use case 層に到達しない。

---

## 10. 並行・スレッディング

- DB 書き込みは既存 UseCase 経由（`SaveCredentialUseCase` / `UpdateCredentialUseCase`）。これらは `suspend` なので `viewModelScope.launch` 配下で自動的にバックグラウンドスレッドへ。
- 暗号化処理（`EncryptedCustomFieldsCodec.encrypt`）も use case 内の同期 call。Android Keystore へのアクセスを含むため呼び出しは Dispatchers.Default または IO スレッドで行う必要があるが、既存 password 暗号化と同じ context で実行されるため特別な dispatcher 切替は不要。
- AutofillService 側は既存 `scope.launch(handlerJob)`（Dispatchers.Default）で実行される。customField match は CPU-bound のためそのまま流用。

---

## 11. ログ・SafeLogger 適用範囲

| データ | ログ可否 | 扱い |
|---|---|---|
| customField の `value` | 不可（Req 5.1 / NFR 2.1） | `SafeLogger.Redacted` で wrap |
| customField の `fieldKey` | 不可（NFR 2.1 で「機微に取り得る fieldKey」と明示） | 同上。バルク件数のみ INFO 可 |
| customField 件数 | 可（NFR 2.2「無害な集計情報」） | `SafeLogger.info("customField count=$n")` |
| AutofillFieldHeuristics の抽出した match キー | 不可（third-party app の field 構造を漏洩しうる） | DEBUG レベルでも出さない |
| 暗号化前後の BLOB size | 可（既存 `EncryptedBlob.toString` パターン） | `<NB>` プレビュー |

`Credential` / `CredentialEntity` の `toString()` には customField 情報を含めない（既存 BLOB 隠蔽パターンに揃える）。

---

## 12. リスク・トレードオフ

### 12.1 v2 → v3 migration 失敗時の rollback 戦略

- `fallbackToDestructiveMigration` は導入しない（既存ポリシー継続、requirements §7「失敗時のフォールバック」）。
- migration 例外時は Room の標準動作（クラッシュ）に従い、ユーザーには再インストール経路を案内する（既存挙動を継承）。
- リスク: 既存 v2 ユーザーは migration crash で credential が一時的に使えなくなる可能性。新 column が `DEFAULT x''`（空 BLOB）を持つため migration 自体は冪等で単純、crash 可能性は低い。

### 12.2 match algorithm の偽 match リスク

短い fieldKey（例: `id` 1 文字、`no`、`番号`）は match キーに対する `contains` で多数の field と false positive する。例:
- fieldKey=`id` → match キー=`spinner_id`、`user_id`、`device_id` 全部に false match
- fieldKey=`番号` → match キー=`phone_number_area`、`zip_code_number` 等に false match

**緩和策の選択肢**:
- (a) 最低 fieldKey 長さ制約（例: 3 文字以上、または正規化後 2 文字以上）を保存時 validation で追加
- (b) match 規則を「完全一致」または「単語境界 + contains」に強化
- (c) 緩和策なし、ユーザーの責任とする

**推奨**: Phase 1 では **(c) 緩和策なし**。requirements で「部分一致 + 正規化」と明示されており、勝手に強化すると false negative の苦情を生む可能性がある。代わりに edit UI で `fieldKey` が短いとき warning 表示する案を §13 申し送りに残す。

### 12.3 計算量

`MAX_NODES=500` × `MAX_CUSTOM_FIELDS=10` = 5,000 ops/credential、credential 候補数 N で `N × 5000`。typical N ≤ 3 なら 15,000 ops、NFR 3 の応答 SLA（数百 ms）内に余裕で収まる。正規化は 1 field あたり O(文字数)。

### 12.4 JSON ライブラリ追加による APK サイズ影響

`kotlinx.serialization` runtime + plugin 出力 ≈ 280KB。KeyNest 現状 APK サイズに対して 5-10% 程度の増加見込み。Phase 2 以降の使用も視野に入れれば許容範囲。

### 12.5 編集モードでの再 unlock 要否

§9.3 で述べた通り、編集モードで customField を編集可能にするには biometric unlock が必要。これは既存編集 UX（unlock 不要で username/label 編集可、password のみ空欄表示）と非対称になる。実装フェーズで UX チームと再協議要。

---

## 13. 未決事項（実装者向け申し送り）

1. **`Credential` ドメイン型に何を載せるか**: §3.2 で「何も載せない」と推奨したが requirements Req 1.1 と表面上矛盾。実装着手前に PM と確認すること（list 画面で fieldKey 一覧表示が将来要件として確実なら `customFieldKeys: List<String>` を載せる選択肢もある）。

2. **編集モードでの biometric unlock 要否**: §9.3 / §12.5 で論じた通り、編集モード時の customField 部分の扱い（unlock 要求 / read-only 表示 / 編集モードで customField セクション非表示）の最終 UX 決定が未済。

3. **`text`（編集中の値）を match キーに含めるか**: §7.2 で Phase 1 では除外と決定したが、requirements §3 用語定義と差分あり。テストで `text` 除外が明示的に担保されているか確認。

4. **最低 fieldKey 長さ制約の有無**: §12.2 で「Phase 1 では設けない」と決定。実装時、UI に warning（「短い fieldKey は誤マッチを生む可能性があります」）を出すかは tasks T11 の実装者裁量。

5. **Dataset presentation の文言**: customField 用 Dataset の RemoteViews 表示で fieldKey をそのまま見せるか「カスタム: 会員番号」と prefix を付けるか未定。既存 `DatasetPresentationFactory.build(label, subtitle)` の subtitle 領域でどう表現するか実装者判断（requirements §10-4 でも未決）。

6. **`fieldKey` 重複ハンドリング**: 同一 credential 内に同一 fieldKey を 2 件登録した場合の保存時 dedupe 要否。requirements §10-6 で「match 側は決定的なので保存側 dedupe は必須ではない」と整理済み。本設計では何もしない（UI で警告のみ）方針。

7. **入力 EditText の文字種制限**: fieldKey と value の入力欄に文字種制限を加えるか（例: fieldKey は ASCII + 日本語のみ）。requirements に規定なし。実装フェーズで制限なし（自由入力）が無難。

8. **AssistStructure walk 方式**: §8.2 で「1 回の walk で全 view 抽出」を推奨したが、`MAX_NODES=500` を超えるアプリ（複雑な業務 webview など）で username/password 検出が customField 抽出に押し出されて失敗するリスクあり。実装者は `MAX_NODES` 引き上げの是非も含めて測定すること。

---

## 14. 末尾制約（再掲）

- 既存テスト（unit / instrumented）を一切破壊しないこと。
- `develop` / `main` への直接 push は禁止。feature branch + PR レビュー経由。
- Credential Manager API 経路への変更禁止。
- 既存 username / password 補完挙動への変更禁止。
