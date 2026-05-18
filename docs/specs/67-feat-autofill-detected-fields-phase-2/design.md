# Design Document — Issue #67 feat(autofill): detected_fields ログによる「最近検出されたフィールド」サジェスト (Phase 2)

> 関連: [requirements.md](./requirements.md) / Issue #67 / Phase 1 = Issue #66 / Phase 3 = 別 Issue
> 本書は実装コードを含まず、データクラス定義・API シグネチャ案を擬似コードで示す。

## 0. 制約（冒頭明示）

1. 既存テスト（unit / instrumented / `Migration_1_2_Test` / `Migration_2_3_Test` / Phase 1 が追加したテスト群）を一切 fail させないこと（requirements §5.5 / §12）。
2. `develop` / `main` ブランチへの直接 push は禁止。feature branch + PR レビュー経由（requirements §12）。
3. Phase 1 (Issue #66) の v3 スキーマと customField 入力 UI を変更しないこと。本 Phase 2 は **v4 への schema migration と新規 entity / DAO 追加** で完結する。
4. 既存 username / password 補完挙動・Phase 1 の customField 補完挙動を変更しない（新規 hook のみ追加）。
5. 本書は実装コードを含まず、データクラス定義・API シグネチャ案のみを擬似コードで示す。

### 0.1 Phase 1 マージ状態の確認（2026-05-18）

本設計は **Phase 1 (#66) が `develop` にマージ済み**（commit `2e386e7`）の状態を前提に確定している。`develop` 上で確認した Phase 1 の実装契約は以下のとおり（§12.1 risk R1 解消）。差分があれば本設計を update する：

| 項目 | Phase 1 で確定した実装 | 本書での扱い |
|---|---|---|
| `KeyNestDatabase.version` | `3`、`entities = [CredentialEntity::class]`、`addMigrations(Migration_1_2, Migration_2_3)` | §4.3 で v3 → v4 に拡張 |
| `Migration_2_3` | `credentials` に `custom_fields_ciphertext BLOB NOT NULL DEFAULT x''` / `custom_fields_iv BLOB NOT NULL DEFAULT x''` 追加 | §4 で `Migration_3_4` を直接 v3 → v4 として実装 |
| `AutofillFieldHeuristics` | **`object` singleton**。`FieldDescriptor` data class（`autofillHints` / `inputType` / `idEntry` / `hint` / `contentDescription`、**`text` を持たない**）。`extractMatchKeys` / `normalizeKey` 共に public | §7 で直接参照（DI せず）。`text` 不在は型レベルで担保 |
| `AssistStructureParser.parse()` | `ParsedFields(usernameId, passwordId, customFieldCandidates: List<CustomFieldCandidate>)`。`customFieldCandidates` は全 editable view から収集 | §7.3 で `customFieldCandidates.map { it.descriptor }` を流用 |
| `KeyNestAutofillService.onFillRequest` short-circuit | (a) `structure == null` (b) `!parsed.hasUsernameAndPassword` (c) `callerPackage == null` のいずれかで `callback.onSuccess(null)` し早期 return | §7.3 で **(b) 経路でも detection を fire-and-forget で起動** するよう設計 |
| `CredentialEditViewModel.CustomFieldsState` | `rows` / `editable` を持つ。**`Mode.Edit` では `editable = false`** で row 追加 / 編集を不可（Phase 1 §13 で Phase 2 へ申し送り）。`focusedRowIndex` 相当の状態は **無い** | §8.1 で `editable == true` を suggest UI 表示ゲートとし、転送先は **直近 `addCustomFieldRow()` で append された行**（list の末尾）に固定 |
| `ServiceLocator` | `object`、`credentialRepository` / `clearVaultUseCase` などを lazy で提供。`initialize(context)` が `KeyNestApp.onCreate` と `KeyNestAutofillService.onCreate` から呼ばれる | §7.4 に従い `detectedFieldRepository` / `recordDetectedFieldsUseCase` / `observeRecentDetectedFieldsUseCase` を追加 |

---

## 1. 概要

### 1.1 目的（1 段落要約）

新規 Room エンティティ `DetectedFieldEntity` と DAO を追加し、`KeyNestAutofillService.onFillRequest` 内で **登録済み packageName のみ** について AssistStructure の全 ViewNode から match キー（autofillHints / hint / resourceId / contentDescription、`text` は除外）を抽出して upsert する。`CredentialEditActivity` のカスタムフィールド「フィールド追加」アクションから対象 packageName の検出履歴（最新 10 件、`lastDetectedAt DESC`）をサジェスト UI で表示し、クリックで新規 customField 行の `fieldKey` 入力欄に転送する。データ量肥大化を防ぐため **per-package 50 件 LRU** を DAO 側で担保する。

### 1.2 スコープ / Out of Scope

requirements.md §2 を参照。本書は requirements §4 で確定済みの 3 件（Q1 = text 除外 / Q2 = 登録済みのみ / Q3 = LRU 50）を前提に実装方針を確定する。

---

## 2. アーキテクチャ方針

### 2.1 レイヤ責務

| レイヤ | 既存責務 | Issue #67 で追加する責務 |
|---|---|---|
| domain/model | `Credential` / `EncryptedCredentialRecord` / `PlaintextCredential` | 追加なし（detected_fields はドメイン公開 API 経由ではなく専用 repository で扱う） |
| domain/repository | `CredentialRepository` | 新規 `DetectedFieldRepository` interface（`upsert` / `getRecentByPackage` の 2 メソッド） |
| domain/usecase | autofill 系 use case | 新規 `RecordDetectedFieldsUseCase`（FillRequest 内から fire-and-forget で呼ぶ）と `ObserveRecentDetectedFieldsUseCase`（編集画面のサジェスト用） |
| data/entity | `CredentialEntity` | 新規 `DetectedFieldEntity` を追加 |
| data/dao | `CredentialDao` | 新規 `DetectedFieldDao`（`upsertWithLruCap` / `getByPackage` / `deleteByPackage`） |
| data/repository | `CredentialRepositoryImpl` | 新規 `DetectedFieldRepositoryImpl` |
| data/migration | `Migration_1_2` / Phase 1 で `Migration_2_3` | 新規 `Migration_3_4` を追加し `detected_fields` テーブル + index を新設 |
| autofill | `KeyNestAutofillService.onFillRequest` / `AssistStructureParser` | (a) Phase 1 の `ParsedFields.customFieldCandidates` をそのまま流用（追加 walk なし）。(b) `onFillRequest` 内で **`!parsed.hasUsernameAndPassword` の short-circuit 経路も含めて** credential ヒット時に `RecordDetectedFieldsUseCase` を **fire-and-forget** で呼ぶ（§7.3） |
| ui/edit | `CredentialEditActivity` / `CredentialEditViewModel` | Phase 1 の customField 入力 UI（`Mode.New` で `editable=true` の状態）の直下に「最近検出されたフィールド」サジェスト chip 列を追加。`ObserveRecentDetectedFieldsUseCase` を購読。**`Mode.Edit` では Phase 1 が customField を read-only にしているため suggest UI も表示しない**（§8.5） |
| util | `SafeLogger` | 流用。`AutofillFieldHeuristics`（Phase 1 で `object` 化された singleton）の `normalizeKey()` を直接呼ぶ（DI せず） |

### 2.2 既存設計との整合性

- detected_fields は **plaintext 専用テーブル**（暗号化なし）。`fieldKey` はリソース ID / autofillHints 等で個人情報を含まない想定（requirements §9）。
- AutofillService の応答遅延に detection を **加算しない** ため、`callback.onSuccess(...)` 呼出 **以降** の処理として fire-and-forget で実行する（既存の `scope.launch` を再利用）。
- Phase 1 で導入する `AutofillFieldHeuristics.extractMatchKeys()` / `normalizeKey()` を **そのまま流用** する。Phase 2 で独自正規化関数を作らない（requirements NFR 5）。

### 2.3 dependency graph (追加ノードのみ)

```
KeyNestAutofillService.onFillRequest
        │
        ├── (既存) FillResponseBuilder.buildLockedResponse → callback.onSuccess
        │
        └── (新規) RecordDetectedFieldsUseCase(packageName, descriptors)
                       │
                       └── DetectedFieldRepository.upsert(entity)
                                  │
                                  └── DetectedFieldDao.upsertWithLruCap(entity)

CredentialEditActivity / ViewModel
        │
        ├── (Phase 1) customField 行を動的に追加
        │
        └── (新規) ObserveRecentDetectedFieldsUseCase(packageName) → suggestion chip 列
                       │
                       └── DetectedFieldRepository.getRecentByPackage(packageName, limit=10)
                                  │
                                  └── DetectedFieldDao.getByPackage(packageName, limit=10)
```

---

## 3. データモデル設計

### 3.1 `DetectedFieldEntity`（Room）

```kotlin
// data/entity/DetectedFieldEntity.kt
@Entity(
    tableName = "detected_fields",
    primaryKeys = ["package_name", "field_key", "source"],
    indices = [
        // requirements Req 1.5: サジェスト UI の主要クエリ
        // (package_name, last_detected_at DESC) を高速に走査
        Index(value = ["package_name", "last_detected_at"], orders = [Index.Order.ASC, Index.Order.DESC])
    ],
)
data class DetectedFieldEntity(
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "field_key")
    val fieldKey: String,

    @ColumnInfo(name = "source")
    val source: String,   // "autofillHints" / "hint" / "resourceId" / "contentDescription"

    @ColumnInfo(name = "last_detected_at")
    val lastDetectedAt: Long,
)
```

- 複合 PK `(package_name, field_key, source)` により同一フィールドの重複 INSERT を防ぐ。Room が自動で UNIQUE を担保する。
- 暗号化なし（requirements §9）。
- `source` は `enum class` ではなく **`String` で持つ**。Room が enum を扱うには TypeConverter が必要で、Phase 1 で TypeConverter を導入しない方針 (Phase 1 design §5.1) に合わせる。代わりに enum を Kotlin 側で別途定義し、entity との変換は repository 層で行う。

### 3.2 `Source` ドメイン enum

```kotlin
// data/entity/DetectedFieldSource.kt（または domain/model 配下）
enum class DetectedFieldSource(val storageKey: String) {
    AutofillHints("autofillHints"),
    Hint("hint"),
    ResourceId("resourceId"),
    ContentDescription("contentDescription");

    companion object {
        fun fromStorageKey(key: String): DetectedFieldSource? =
            values().firstOrNull { it.storageKey == key }
    }
}
```

- `text` は **意図的に enum から除外**（requirements Q1）。
- `storageKey` は DB に保存される値。enum 名そのものではなく文字列リテラルとすることで、enum リネーム時の DB 互換性を保つ。

### 3.3 ドメイン型 `DetectedField`

```kotlin
// domain/model/DetectedField.kt
data class DetectedField(
    val packageName: String,
    val fieldKey: String,
    val source: DetectedFieldSource,
    val lastDetectedAt: Long,
)
```

- repository が `DetectedFieldEntity` ⇔ `DetectedField` を相互変換。
- 編集画面の ViewModel は `DetectedField` のみを扱い `DetectedFieldEntity` を直接参照しない（既存 `Credential` / `CredentialEntity` の二層構造に揃える）。

---

## 4. Room schema v3 → v4 migration

### 4.1 SQL 文

```sql
CREATE TABLE IF NOT EXISTS detected_fields (
    package_name TEXT NOT NULL,
    field_key TEXT NOT NULL,
    source TEXT NOT NULL,
    last_detected_at INTEGER NOT NULL,
    PRIMARY KEY(package_name, field_key, source)
);

CREATE INDEX IF NOT EXISTS index_detected_fields_package_name_last_detected_at
    ON detected_fields (package_name, last_detected_at DESC);
```

- Room の auto-generated SQL は `CREATE INDEX` に DESC を含めない場合があるため、Migration では Room が出力する `4.json` に書かれた SQL を **そのまま** コピーする運用とする。実装時に Room が schema diff から自動生成した SQL を Migration_3_4 に反映する。
- 既存 `credentials` テーブルには **一切触らない**（requirements Req 2.2）。

### 4.2 Migration_3_4

```kotlin
// data/migration/Migration_3_4.kt
object Migration_3_4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS detected_fields (
                package_name TEXT NOT NULL,
                field_key TEXT NOT NULL,
                source TEXT NOT NULL,
                last_detected_at INTEGER NOT NULL,
                PRIMARY KEY(package_name, field_key, source)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_detected_fields_package_name_last_detected_at
              ON detected_fields (package_name, last_detected_at DESC)
        """.trimIndent())
    }
}
```

- 純粋な SQL のみ。Keystore 等の I/O を含まない（Phase 1 設計と同じ方針）。
- Migration テスト用に `Migration_3_4_Test` を別ファイルで追加（§9）。

### 4.3 KeyNestDatabase の更新

```kotlin
@Database(
    entities = [CredentialEntity::class, DetectedFieldEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class KeyNestDatabase : RoomDatabase() {
    abstract fun credentialDao(): CredentialDao
    abstract fun detectedFieldDao(): DetectedFieldDao

    companion object {
        const val DB_NAME = "keynest.db"
        fun create(context: Context): KeyNestDatabase {
            return Room.databaseBuilder(...)
                .addMigrations(Migration_1_2, Migration_2_3, Migration_3_4)  // Phase 1 が Migration_2_3 を追加済み前提
                .build()
        }
    }
}
```

- Phase 1 が `Migration_2_3` を追加する前提でコンフリクトを最小化。
- `entities = [...]` に `DetectedFieldEntity` を追加。

### 4.4 schemas/4.json の git tracking

- `ksp.arg("room.schemaLocation", ...)` は既に設定済み（Phase 1 と同じ）。
- gradle ビルドで `app/schemas/io.github.hitoshiichikawa.keynest.data.KeyNestDatabase/4.json` が生成される。
- **`4.json` を git commit に含める**（Issue #63 で確立した運用、requirements Req 2.4）。

---

## 5. DAO 設計

### 5.1 `DetectedFieldDao` インターフェース

```kotlin
// data/dao/DetectedFieldDao.kt
@Dao
interface DetectedFieldDao {

    /**
     * 単純な REPLACE 戦略の upsert。複合 PK に一致する row があれば
     * lastDetectedAt のみ更新、無ければ INSERT。
     *
     * 注意: 本メソッド単体では LRU 上限を考慮しない。
     *       呼び出し側は必ず [upsertWithLruCap] 経由で呼ぶこと。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceInternal(entity: DetectedFieldEntity)

    /**
     * 指定 packageName 内の row 数を返す（LRU 判定用）。
     */
    @Query("SELECT COUNT(*) FROM detected_fields WHERE package_name = :pkg")
    suspend fun countByPackage(pkg: String): Int

    /**
     * 指定 packageName 内で最古の row（複数なら 1 件）を削除する。
     * LRU 上限超過時に [upsertWithLruCap] から呼ばれる。
     */
    @Query("""
        DELETE FROM detected_fields
        WHERE rowid IN (
            SELECT rowid FROM detected_fields
            WHERE package_name = :pkg
            ORDER BY last_detected_at ASC
            LIMIT :count
        )
    """)
    suspend fun deleteOldestByPackage(pkg: String, count: Int)

    /**
     * 単純な観測クエリ。サジェスト UI の購読は Flow で行う。
     */
    @Query("""
        SELECT * FROM detected_fields
        WHERE package_name = :pkg
        ORDER BY last_detected_at DESC
        LIMIT :limit
    """)
    fun observeRecentByPackage(pkg: String, limit: Int): Flow<List<DetectedFieldEntity>>

    /**
     * upsert + LRU 上限維持を atomic に実行する。
     *
     * 手順:
     *   1) [insertOrReplaceInternal] で当該 row を upsert（lastDetectedAt 更新）。
     *   2) [countByPackage] で件数取得。
     *   3) count > [capacity] なら超過数を [deleteOldestByPackage] で削除。
     *
     * Room の @Transaction で atomic 性を担保。SQLite の WAL モードに
     * よる並行 FillRequest にも安全。
     */
    @Transaction
    suspend fun upsertWithLruCap(entity: DetectedFieldEntity, capacity: Int = LRU_CAPACITY) {
        insertOrReplaceInternal(entity)
        val count = countByPackage(entity.packageName)
        val excess = count - capacity
        if (excess > 0) {
            deleteOldestByPackage(entity.packageName, excess)
        }
    }

    /**
     * ユーザーが credential を全削除 (clear-all) したときに対応する
     * packageName の detected_fields も削除するための補助。
     * 実装時に CredentialRepository.deleteAll と連動させるかは tasks.md
     * で確認事項として扱う。
     */
    @Query("DELETE FROM detected_fields WHERE package_name = :pkg")
    suspend fun deleteByPackage(pkg: String)

    @Query("DELETE FROM detected_fields")
    suspend fun deleteAll()

    companion object {
        const val LRU_CAPACITY = 50  // requirements Req 1.4
    }
}
```

### 5.2 upsert における race condition

- 並行 `FillRequest` が同 packageName を upsert する場合、`@Transaction` により SQLite レベルで sequentially serialized される（WAL モード）。
- ただし `INSERT OR REPLACE` の挙動として、複合 PK 重複時は `rowid` が変わる（行が一度 DELETE → INSERT される）。これは LRU の `rowid ORDER BY last_detected_at ASC` 削除に影響を与えない（rowid は LRU の判断材料ではない）。
- `last_detected_at` の同 ms 衝突は実用上問題ない（requirements §10 R3）。

### 5.3 サジェスト購読用 Flow

- `observeRecentByPackage(pkg, limit)` は `Flow<List<...>>` を返し、ViewModel が `collect` してサジェスト UI を更新。
- `LIMIT 10` を ViewModel 側から渡す（Phase 1 のカスタムフィールド max=10 件と同じ N）。

---

## 6. Repository 設計

### 6.1 `DetectedFieldRepository`

```kotlin
// domain/repository/DetectedFieldRepository.kt
interface DetectedFieldRepository {

    /** RecordDetectedFieldsUseCase から呼ばれる。fire-and-forget 前提。 */
    suspend fun upsert(field: DetectedField)

    /** ObserveRecentDetectedFieldsUseCase から呼ばれる。 */
    fun observeRecentByPackage(packageName: String, limit: Int): Flow<List<DetectedField>>

    /** 将来の CredentialRepository.deleteAll 連動用（実装時に判断、§7.3 参照） */
    suspend fun deleteByPackage(packageName: String)

    /** Vault clear-all 連動 */
    suspend fun deleteAll()
}
```

### 6.2 `DetectedFieldRepositoryImpl`

```kotlin
// data/repository/DetectedFieldRepositoryImpl.kt
class DetectedFieldRepositoryImpl(
    private val dao: DetectedFieldDao,
) : DetectedFieldRepository {

    override suspend fun upsert(field: DetectedField) {
        dao.upsertWithLruCap(field.toEntity())
    }

    override fun observeRecentByPackage(packageName: String, limit: Int): Flow<List<DetectedField>> =
        dao.observeRecentByPackage(packageName, limit)
            .map { list -> list.mapNotNull { it.toDomainOrNull() } }

    override suspend fun deleteByPackage(packageName: String) {
        dao.deleteByPackage(packageName)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}

// Mapping helpers (file private)
private fun DetectedField.toEntity(): DetectedFieldEntity =
    DetectedFieldEntity(
        packageName = packageName,
        fieldKey = fieldKey,
        source = source.storageKey,
        lastDetectedAt = lastDetectedAt,
    )

private fun DetectedFieldEntity.toDomainOrNull(): DetectedField? {
    val src = DetectedFieldSource.fromStorageKey(source) ?: return null
    return DetectedField(packageName, fieldKey, src, lastDetectedAt)
}
```

- 未知の `source` 文字列を読み込んだ場合は `null` で skip（フォワード互換性）。
- `LRU_CAPACITY = 50` は DAO 側のデフォルト引数。テスト用に override 可能。

---

## 7. AutofillService 統合

### 7.1 新規 use case `RecordDetectedFieldsUseCase`

```kotlin
// domain/usecase/RecordDetectedFieldsUseCase.kt
class RecordDetectedFieldsUseCase(
    private val detectedFieldRepository: DetectedFieldRepository,
    private val credentialRepository: CredentialRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /**
     * 登録済み packageName のときのみ実行する。未登録なら no-op。
     *
     * `AutofillFieldHeuristics` は Phase 1 で `object` singleton として
     * 配置されているため DI せず直接参照する（§0.1 整合性確認）。
     *
     * @param packageName  AssistStructure.activityComponent.packageName
     * @param descriptors  AssistStructureParser が抽出した全 editable ViewNode の descriptor
     */
    suspend operator fun invoke(
        packageName: String,
        descriptors: List<AutofillFieldHeuristics.FieldDescriptor>,
    ) {
        // Req 3.1 + Q2-A: 登録済み packageName のみ
        val hasCredential = credentialRepository.findByPackage(packageName).isNotEmpty()
        if (!hasCredential) return

        val now = clock()
        val records = buildRecords(packageName, descriptors, now)
        for (record in records) {
            // upsert 個別エラーは外側 try/catch で swallow される想定
            detectedFieldRepository.upsert(record)
        }
    }

    private fun buildRecords(
        packageName: String,
        descriptors: List<AutofillFieldHeuristics.FieldDescriptor>,
        now: Long,
    ): List<DetectedField> {
        val out = mutableListOf<DetectedField>()
        for (d in descriptors) {
            // Req 3.6: text source は除外
            // FieldDescriptor は text を持たないため型レベルで担保される
            // (§0.1 で確認)。
            //
            // - autofillHints: 各要素を 1 row として扱う
            d.autofillHints?.forEach { hint ->
                addIfNotBlank(out, packageName, hint, DetectedFieldSource.AutofillHints, now)
            }
            // - hint
            addIfNotBlank(out, packageName, d.hint, DetectedFieldSource.Hint, now)
            // - resourceId (idEntry)
            addIfNotBlank(out, packageName, d.idEntry, DetectedFieldSource.ResourceId, now)
            // - contentDescription
            addIfNotBlank(out, packageName, d.contentDescription, DetectedFieldSource.ContentDescription, now)
        }
        return out
    }

    private fun addIfNotBlank(
        sink: MutableList<DetectedField>,
        pkg: String,
        raw: String?,
        source: DetectedFieldSource,
        now: Long,
    ) {
        // Req 3.5: 空文字列は upsert しない（normalize 前後どちらも判定）
        if (raw.isNullOrBlank()) return
        val normalized = AutofillFieldHeuristics.normalizeKey(raw)
        if (normalized.isBlank()) return
        // `fieldKey` には 表示用の raw 値を保存（normalize は Phase 1 の比較経路で行う）。
        // requirements NFR 5 / Phase 1 §7.3 に揃え、表示値は raw を維持。
        sink += DetectedField(pkg, raw, source, now)
    }
}
```

- **保存値**: `fieldKey` は **raw 値**（resource id `loginEmail` 等）をそのまま保存し、normalize は比較・dedup 経路でのみ行う方針。Phase 1 の `AutofillFieldHeuristics.extractMatchKeys()` は normalize 後の値を返すため、サジェスト UI で表示する文字列は **本 use case が保存した raw 値**を直接使う（ユーザーが認識しやすい元表記を維持）。
- **重複排除**: 同 packageName / fieldKey / source の重複は **DB の複合 PK が自然に dedupe** する（INSERT OR REPLACE）。memo: 同一 FillRequest 内で重複 ViewNode があるとき `INSERT OR REPLACE` が複数回呼ばれるが、最終的に row 1 つに収束するため副作用なし。
- **`text` 除外**: Phase 1 `FieldDescriptor` が `text` を持たないため **型レベルで担保**（§0.1）。将来 Phase 1 仕様が変わって `text` が追加された場合は `buildRecords` 内に明示 skip を追加する。

### 7.2 `AssistStructureParser` / `FieldDescriptor` の流用

Phase 1 で導入済みの `AssistStructureParser.parse()` の戻り値 `ParsedFields.customFieldCandidates: List<CustomFieldCandidate>` をそのまま流用する。本 Phase 2 では `AssistStructureParser` / `AutofillFieldHeuristics` のシグネチャに **一切手を加えない**。

```kotlin
// Phase 1 で確定済み (develop / 2026-05-18 確認)
data class ParsedFields(
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val customFieldCandidates: List<CustomFieldCandidate> = emptyList(),
)

data class CustomFieldCandidate(
    val autofillId: AutofillId,
    val descriptor: AutofillFieldHeuristics.FieldDescriptor,
)

// Phase 1 の AutofillFieldHeuristics (object singleton)
object AutofillFieldHeuristics {
    data class FieldDescriptor(
        val autofillHints: List<String>?,
        val inputType: Int,
        val idEntry: String?,
        val hint: String?,
        val contentDescription: String?,
        // 注: text を持たない。Phase 1 §7.2 で意図的に除外済み。
    )

    fun extractMatchKeys(descriptor: FieldDescriptor): Set<String>
    fun normalizeKey(raw: String): String
}
```

**型レベル保証**: `FieldDescriptor` に `text` が存在しないため、`RecordDetectedFieldsUseCase.buildRecords` が `text` を誤って扱う可能性は **型で排除** される。requirements Q1-A はコンパイル時に担保される。

### 7.3 `KeyNestAutofillService.onFillRequest` への組み込み

Phase 1 実装の `onFillRequest` には **3 つの short-circuit 経路**がある（`develop` / 2026-05-18 確認）:

1. `structure == null` → `callback.onSuccess(null)` で return
2. `!parsed.hasUsernameAndPassword` → `callback.onSuccess(null)` で return
3. `callerPackage == null` → `callback.onSuccess(null)` で return

Phase 2 の detection を `callback.onSuccess(response)` 後だけに置くと、(2) 経路 **= 登録済みアプリだが Phase 1 ヒューリスティクスでは username/password として認識されないフォーム** で detection が走らない。しかしこれこそが Phase 2 の主要ユースケース（custom field 主体のアプリ）であり、ここで取りこぼすと「履歴なし」状態から抜け出せない。

したがって detection は **structure と callerPackage が確定した直後**（=(1) を抜けた後、(2)/(3) のチェック前）に fire-and-forget で launch する。

```kotlin
// autofill/KeyNestAutofillService.kt
override fun onFillRequest(request, signal, callback) {
    val handlerJob = Job(serviceJob)
    cancellationSignal.setOnCancelListener { handlerJob.cancel() }

    scope.launch(handlerJob) {
        try {
            val structure = request.fillContexts.lastOrNull()?.structure
            if (structure == null) {
                if (handlerJob.isActive) callback.onSuccess(null)
                return@launch
            }

            val parsed = parser.parse(structure)
            val callerPackage = extractCallerPackage(structure)

            // --- Phase 2 追加: callerPackage 確定後に fire-and-forget で detection ---
            // (2) /(3) の short-circuit より前に launch することで、
            //   - Phase 1 ヒューリスティクスで username/password が検出できないアプリ
            //   - locked response 構築が空 candidate で打ち切られるアプリ
            // でも detection は走る（Phase 2 の主要ユースケース）。
            //
            // Req 3.1 (登録済み packageName のみ) のゲートは use case 側で持つ。
            // 未登録のときは早期 return される (no DB 書き込み)。
            //
            // Req 3.3 / 3.4 (応答 latency に加算しない):
            //   - `scope.launch(Dispatchers.IO)` で別 coroutine に分離。
            //   - `handlerJob` に bind しないことで、cancellation や callback 完了の
            //     タイミングと完全に独立させる。
            if (callerPackage != null) {
                val descriptors = parsed.customFieldCandidates.map { it.descriptor }
                if (descriptors.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            ServiceLocator.recordDetectedFieldsUseCase(callerPackage, descriptors)
                        } catch (t: Throwable) {
                            SafeLogger.warn(message = "detected_fields upsert failed", throwable = t)  // Req 3.8
                        }
                    }
                }
            }

            // --- 既存 short-circuit (b) / (c) ---
            if (!parsed.hasUsernameAndPassword) {
                SafeLogger.info(message = "onFillRequest: no username+password fields detected")
                if (handlerJob.isActive) callback.onSuccess(null)
                return@launch
            }
            if (callerPackage == null) {
                if (handlerJob.isActive) callback.onSuccess(null)
                return@launch
            }

            // --- 既存 happy path ---
            val candidates = ServiceLocator.resolveAutofillCandidatesUseCase(callerPackage)
            val response = responseBuilder.buildLockedResponse(...)
            if (handlerJob.isActive) callback.onSuccess(response)
        } catch (t: Throwable) {
            SafeLogger.warn(message = "onFillRequest swallowed exception", throwable = t)
            if (handlerJob.isActive) callback.onSuccess(null)
        }
    }
}
```

設計上のポイント：

- **fire-and-forget の launch 位置**: `callerPackage` 確定直後 / short-circuit (b)(c) より前。検出取りこぼしを防ぐ。
- **dispatcher**: `Dispatchers.IO`（Room I/O のため）。`scope` の親は `Dispatchers.Default` なので IO Dispatcher への切替は必須。
- **`handlerJob` に bind しない**: `scope.launch(Dispatchers.IO)`（明示的に親を指定しない）で `scope` 直下の独立 child job として起動する。これにより:
  - `cancellationSignal` がトリガーされて `handlerJob.cancel()` されても upsert は中断されない（途中で失敗すると DB 状態が中途半端になるため避ける）
  - callback 完了タイミングと完全に decouple される
- **descriptors が空のときは launch 自体を skip**: parser が何も descriptor を返さなかった場合の no-op launch を避けて余計な coroutine 起動コストを 0 にする。
- **exception 隔離**: detection の例外は内側 try/catch で swallow（Req 3.8）。`scope` 全体には影響しない（`SupervisorJob` 配下のため、兄弟ジョブにも波及しない）。
- **登録済みゲート**: `RecordDetectedFieldsUseCase.invoke` の冒頭で `credentialRepository.findByPackage` を呼ぶ。未登録なら即 return で DB 書き込みは発生しない（Req 3.1 / Q2-A）。`onFillRequest` 側で重複チェックしない（責務分離）。

#### 7.3.1 latency への影響

| measurement | 影響 |
|---|---|
| `callerPackage` 抽出 (`extractCallerPackage`) | 既存呼出と同じタイミング。変化なし |
| `descriptors.isNotEmpty()` / `customFieldCandidates.map { it.descriptor }` | 既存 `parsed.customFieldCandidates` をなめるだけ。≤ 500 nodes × 数十バイトコピー、< 1 ms |
| `scope.launch(Dispatchers.IO)` のオーバーヘッド | < 1 ms (coroutine 起動のみ) |
| `callback.onSuccess(...)` 到達まで | 既存と同じパス。**理論上 0 ms 加算** |

### 7.4 `ServiceLocator` への追加

```kotlin
// di/ServiceLocator.kt
val detectedFieldRepository: DetectedFieldRepository by lazy {
    DetectedFieldRepositoryImpl(database.detectedFieldDao())
}

val recordDetectedFieldsUseCase: RecordDetectedFieldsUseCase by lazy {
    RecordDetectedFieldsUseCase(detectedFieldRepository, credentialRepository)
}

val observeRecentDetectedFieldsUseCase: ObserveRecentDetectedFieldsUseCase by lazy {
    ObserveRecentDetectedFieldsUseCase(detectedFieldRepository)
}
```

---

## 8. 編集画面 UI 拡張

### 8.1 ViewModel: `CredentialEditViewModel` に追加する状態

Phase 1 で確定済みの `CustomFieldsState` は以下の形（`develop` / 2026-05-18 確認）:

```kotlin
// Phase 1 既存定義（変更しない）
data class CustomFieldsState(
    val rows: List<Row> = emptyList(),
    val editable: Boolean = true,  // Mode.New で true、Mode.Edit で false
) {
    data class Row(val rowId: Long, val fieldKey: String, val value: String)
    val canAddMore: Boolean get() = editable && rows.size < MAX_CUSTOM_FIELDS
    companion object { const val MAX_CUSTOM_FIELDS = 10 }
}
// Phase 1 既存 reducer:
//   addCustomFieldRow()       -> rows に空 Row append（capacity 制限あり）
//   removeCustomFieldRow(id)
//   updateCustomFieldKey(id, key)
//   updateCustomFieldValue(id, value)
```

**転送ターゲットの決定方針**: Phase 1 の `CustomFieldsState` は `focusedRowIndex` 相当の状態を持たない。Phase 2 で UI 状態を追加するよりも、**直近に `addCustomFieldRow()` した行 (= `rows.last()` の rowId)** を転送ターゲットとして扱う方が Phase 1 の reducer 契約を壊さない（§13 開発フローでこの方針を採用）。理由:

- `addCustomFieldRow()` は capacity 内ならば常に末尾に append する単純な reducer。
- サジェスト UI は「追加ボタン押下 → chip 列表示 → クリック → 末尾行に転送 → chip 列を非表示」という直線的なフローで成立する（§8.4）。
- chip 列表示中に別の既存行を編集する操作は §8.4 で対象外とする（誤って既存行を上書きするのを避ける）。

```kotlin
// ui/edit/CredentialEditViewModel.kt に追加する Phase 2 状態
data class SuggestionState(
    val visible: Boolean,                     // chip 列を表示するか
    val items: List<DetectedFieldSuggestion>, // 表示する候補
    val emptyMessage: Boolean,                // 「履歴なし」プレースホルダを出すか
)

data class DetectedFieldSuggestion(
    val fieldKey: String,
    val source: DetectedFieldSource,
)

private val _suggestion = MutableStateFlow(SuggestionState(false, emptyList(), false))
val suggestion: StateFlow<SuggestionState> = _suggestion.asStateFlow()

// Phase 1 の addCustomFieldRow() を Phase 2 で wrap せず、Activity 側で
// 「追加ボタンタップ後」の hook として onAddCustomFieldClickedForSuggest() を
// 追加で呼ぶ。Phase 1 addCustomFieldRow() の reducer は変更しない。
fun onAddCustomFieldClickedForSuggest() {
    // 直前に Phase 1 が append した行が「現在の転送先」になる。
    refreshSuggestions()
}

fun onSuggestionClicked(item: DetectedFieldSuggestion) {
    val current = _customFields.value
    val targetRowId = current.rows.lastOrNull()?.rowId ?: return
    // Phase 1 既存 reducer をそのまま使う（追加 reducer なし）
    updateCustomFieldKey(targetRowId, item.fieldKey)
    // 候補リストから当該 row を除外し、再度 emit
    refreshSuggestions()
    // chip 列を非表示にする（§8.4）
    _suggestion.update { it.copy(visible = false) }
}

/**
 * packageName が確定したら 1 度だけ collect を起動する。
 * `currentPackageName` は Phase 1 load() の延長または save() 直前に
 * Activity から渡される packageName。
 */
private fun refreshSuggestions() {
    val pkg = currentPackageName ?: run {
        _suggestion.value = SuggestionState(false, emptyList(), false)
        return
    }
    // Phase 1 が edit mode で editable=false にしているため、suggest UI も非表示
    val state = _customFields.value
    if (!state.editable) {
        _suggestion.value = SuggestionState(false, emptyList(), false)
        return
    }
    suggestionJob?.cancel()
    suggestionJob = viewModelScope.launch {
        observeRecentDetectedFieldsUseCase(pkg, limit = 10).collect { list ->
            // Req 4.4: 現在の customFields に既に存在する fieldKey は除外
            val existing = _customFields.value.rows
                .map { AutofillFieldHeuristics.normalizeKey(it.fieldKey) }
                .filter { it.isNotEmpty() }
                .toSet()
            val filtered = list
                .filter { AutofillFieldHeuristics.normalizeKey(it.fieldKey) !in existing }
                .distinctBy { AutofillFieldHeuristics.normalizeKey(it.fieldKey) }
                .map { DetectedFieldSuggestion(it.fieldKey, it.source) }
            _suggestion.value = SuggestionState(
                visible = true,
                items = filtered,
                emptyMessage = list.isEmpty(),
            )
        }
    }
}
```

- `observeRecentDetectedFieldsUseCase(pkg, 10)` は Flow。`refreshSuggestions()` 内で 1 度 collect を起動し、`packageName` が変わるか edit mode に遷移したら job を cancel する。
- **重複排除**: `distinctBy(normalize)` で同一 normalized key が複数 source として重複表示されないようにする。
- **`AutofillFieldHeuristics`** は `object` singleton なので DI せず直接呼ぶ（§0.1）。

### 8.2 Activity: chip group の追加

`CredentialEditActivity` の layout に Phase 1 で追加される「カスタムフィールド」セクションのすぐ下、または「フィールド追加」ボタンの直下に **HorizontalScrollView + ChipGroup** を追加する。

```xml
<!-- credential_edit_activity.xml の Advanced セクション内 -->
<HorizontalScrollView
    android:id="@+id/scrollDetectedFieldSuggestions"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:visibility="gone">

    <com.google.android.material.chip.ChipGroup
        android:id="@+id/chipGroupDetectedFields"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:singleLine="true" />
</HorizontalScrollView>

<TextView
    android:id="@+id/tvDetectedFieldsEmpty"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="@string/credential_edit_detected_fields_empty"
    android:visibility="gone" />
```

Activity 側で `viewModel.suggestion` を collect し、chip を動的に生成。chip クリックリスナで `viewModel.onSuggestionClicked(item)` を呼ぶ。

**chip 表示形式**: 各 chip のテキストは `fieldKey` のみ（source は表示しない、Phase 2 ではシンプルに保つ）。長い fieldKey は `ellipsize="end"` でトランケート。

### 8.3 strings.xml

```xml
<string name="credential_edit_detected_fields_label">最近検出されたフィールド</string>
<string name="credential_edit_detected_fields_empty">履歴なし。アプリで一度フォームを開くと候補が表示されます。</string>
```

i18n は既存規約に従い、デフォルト ja。英訳は `values-en/strings.xml` を別 task で追加（既存の en リソース体制に合わせる）。

### 8.4 サジェスト UI の可視性切替

- ユーザーが「フィールド追加」ボタンを押した直後 → 新規行が追加された **時刻と紐づけて** chip 列を表示。
- 既存 customField 行を編集している間（フォーカスのみ移った場合）は chip 列を **表示しない**（誤って既存行に fieldKey を上書きすることを避ける）。
- 新規行の `fieldKey` フィールドへの入力が **完了**（フォーカスアウト or サジェストクリック）した時点で chip 列を非表示。

### 8.5 編集モードでのサジェスト UI 不表示（Phase 1 確定により決着）

Phase 1 (`develop` / 2026-05-18 確認) では `CredentialEditViewModel.load()` 内で `Mode.Edit` 時に `CustomFieldsState(rows = emptyList(), editable = false)` を設定し、customField セクションを **読み取り専用** にしている（Phase 1 ViewModel コメント "Phase 2 will revisit." を参照）。これは「既存 customField を decrypt するには追加の biometric unlock が必要で Phase 1 のスコープ外」という Phase 1 設計判断による。

Phase 2 は **このスコープ判断をそのまま継承する**:

- **新規作成モード (`Mode.New`)**: `editable = true` で suggest UI を有効化（本 design の主対象）。
- **編集モード (`Mode.Edit`)**: `editable = false` のため Phase 1 で既に「行追加ボタン」が無効化されており、suggest UI も自然に表示されない。`refreshSuggestions()` 冒頭で `state.editable == false` を見て早期 return する（§8.1 実装サンプル）。

将来「編集モードでの customField 編集」が別 Issue（Phase 1.5）で実装される場合、その変更で `editable = true` のパスを edit mode にも開ければ、Phase 2 の suggest UI ロジックは **そのまま流用** できる（packageName と `customFields.editable` だけを条件にしているため）。requirements Req 4.6 はこの方針で満たされる。

---

## 9. テスト戦略

### 9.1 Migration_3_4_Test

`Migration_1_2_Test` と同じ Robolectric ベースの instrumented unit test 流儀：

```kotlin
// app/src/test/java/.../data/Migration_3_4_Test.kt
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class Migration_3_4_Test {

    @Test
    fun migrate_createsDetectedFieldsTable_andPreservesCredentials() {
        // Arrange: v3 schema を作り credentials に row を 1 件 insert
        // Act: Migration_3_4.migrate(db) を呼ぶ
        // Assert:
        //   1) detected_fields テーブルが存在する
        //   2) 複合 PK が定義どおり
        //   3) index_detected_fields_package_name_last_detected_at が存在する
        //   4) credentials の row が保持されている
        //   5) credentials の列構成が変わっていない
    }

    @Test
    fun migrate_idempotent_doesNotFailOnSecondCall() {
        // CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS の挙動を確認
    }
}
```

### 9.2 DetectedFieldDaoTest

```kotlin
// app/src/test/java/.../data/dao/DetectedFieldDaoTest.kt
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class DetectedFieldDaoTest {

    // Room.inMemoryDatabaseBuilder で v4 schema のテスト DB を構築

    @Test fun upsert_insertsNewRow() { /* ... */ }

    @Test fun upsert_updatesExistingRow_byCompositeKey() {
        // 同一 (pkg, key, source) で 2 回 upsert → row 1 件、lastDetectedAt 更新
    }

    @Test fun observeRecentByPackage_ordersByLastDetectedAtDesc() { /* ... */ }

    @Test fun observeRecentByPackage_limitN() { /* ... */ }

    @Test fun upsertWithLruCap_keepsCapacity() {
        // capacity=3 で 4 件 upsert → 1 件削除されて 3 件残る
        // 削除されるのは lastDetectedAt 最小の row
    }

    @Test fun upsertWithLruCap_doesNotAffectOtherPackages() {
        // pkg=A で 50 件 / pkg=B で 50 件 → 100 件残る
    }
}
```

### 9.3 RecordDetectedFieldsUseCaseTest

```kotlin
// app/src/test/java/.../domain/usecase/RecordDetectedFieldsUseCaseTest.kt
class RecordDetectedFieldsUseCaseTest {

    @Test fun invoke_noOp_whenPackageHasNoCredential() {
        // CredentialRepository.findByPackage → empty list
        // → detected_field repository.upsert は呼ばれない
    }

    @Test fun invoke_upsertsAllSources_whenCredentialExists() {
        // descriptors に autofillHints + hint + idEntry + contentDescription を載せる
        // 各 source ごとに upsert が 1 回呼ばれることを Mockito で検証
    }

    @Test fun invoke_skipsBlankFieldKeys() { /* Req 3.5 */ }

    @Test fun invoke_doesNotIncludeTextSource() {
        // descriptor は text を持たない (型で保証) ので
        // この test は「型レベルの保証」を明示するため (compile-time check 相当)
        // - もし将来 FieldDescriptor に text が追加されたら、本 test を update
    }
}
```

### 9.4 KeyNestAutofillService 統合テスト

既存 `KeyNestAutofillService` のテスト方法に揃える（Phase 1 で何らかのテストフィクスチャが整備される想定）：

```kotlin
@Test fun onFillRequest_recordsDetectedFields_forRegisteredPackage() {
    // simulated FillRequest with structure
    // - credentialRepository が 1 件返す状態
    // → callback.onSuccess(response) 後に
    //   detectedFieldRepository.upsert が呼ばれる
}

@Test fun onFillRequest_doesNotRecord_forUnregisteredPackage() { /* Req 3.1 */ }

@Test fun onFillRequest_detectionFailure_doesNotAffectResponse() {
    // detectedFieldRepository.upsert が例外を投げても
    // callback.onSuccess(response) は正常に呼ばれている
}
```

ServiceLocator のテスト double 注入は Phase 1 が確立する想定。Phase 1 が同等のテストフィクスチャを整備しない場合、本 Phase 2 で簡易的な fake repository を `ServiceLocator` 経由で注入する仕組みを追加する（tasks.md T8 で検討）。

### 9.5 CredentialEditViewModelTest

```kotlin
class CredentialEditViewModelTest {

    @Test fun onSuggestionClicked_transfersFieldKeyToFocusedRow() {
        // viewModel.onAddCustomFieldClicked() → focusedRowIndex = 0
        // viewModel.onSuggestionClicked(item with fieldKey="loginEmail")
        // → customFieldRows[0].fieldKey == "loginEmail"
    }

    @Test fun suggestions_filterOutExistingKeys() {
        // 既に customFields に "loginEmail" がある状態で
        // suggestion が "loginEmail" を含まない
    }

    @Test fun suggestions_dedupe_byNormalizedKey() {
        // "loginEmail" と "LoginEmail" が両方 suggest される source にあっても
        // 1 件のみ表示
    }

    @Test fun suggestions_emptyState_whenNoDetectedFields() {
        // observeRecent → empty list
        // → SuggestionState.emptyMessage == true
    }
}
```

### 9.6 既存テストの非破壊性

- `Migration_1_2_Test`: 影響なし（v1 → v2 のみテストする）
- Phase 1 で追加される `Migration_2_3_Test` / DAO test / autofill test: 影響なし（v3 までで完結する test は v4 を見ない）
- 既存 unit test: 影響なし（DetectedFieldEntity の追加は他テストの fixture に影響を与えない）
- requirements Req 5.5 を担保。

---

## 10. パフォーマンス分析

### 10.1 onFillRequest の応答時間

- Phase 2 で追加する detection は **`callback.onSuccess(response)` 後** に launch される（§7.3 fire-and-forget）。
- ⇒ autofill 応答 latency への加算は **理論上 0 ms**。
- 実際の上昇は (a) `parsed.customFieldCandidates.map { it.descriptor }` の構築 (≤ 500 nodes × 数十バイト = 数十 KB のコピー、< 1 ms)、(b) coroutine launch のオーバーヘッド (< 1 ms)。

### 10.2 detection の所要時間

- 1 FillRequest あたり descriptor 件数 ≤ 500（既存 MAX_NODES）、各 descriptor から最大 4 source × autofillHints の長さ。
- upsert 数 ≤ 500 × 4 = 2000、実用は 5〜50 程度。
- 各 upsert は SQLite REPLACE + COUNT + 0〜1 件 DELETE。WAL モードで 1 件 < 1 ms。50 件で < 50 ms。
- IO Dispatcher 上で実行されるため UI thread への影響なし。

### 10.3 サジェスト UI の表示

- `observeRecentByPackage(pkg, 10)` の SQL は `(package_name, last_detected_at DESC)` index 経由で O(log n)。
- 100 packageName × 50 件 = 5000 row でも index lookup は数 ms。
- viewModelScope で `collect` し chip 生成は ≤ 10 件で UI thread 上で問題なし。

---

## 11. プロセス越境とサービス間の整合性

`KeyNestAutofillService` は app プロセスと **同じプロセス**で動作する（既存方針）。`ServiceLocator.detectedFieldRepository` は service / UI 両方から同一インスタンスを参照するため、サジェスト UI は最新の detection を反映する。Phase 2 で multi-process 構成を導入しない（Phase 1 と同じ）。

---

## 12. リスクと申し送り

### 12.1 Phase 1 整合性確認 (2026-05-18 解決済み、requirements §10 R1 / R2)

Phase 1 (#66) の実装 PR は `develop` にマージ済み（commit `2e386e7`）。requirements §10 R1 / R2 で挙げていた整合性懸念は、本 PR レビュー round で **`develop` 上のコードを直接確認** して以下のとおり決着した（詳細は §0.1）:

| 元 risk | 確認結果 |
|---|---|
| R1: Phase 1 スキーマ v3 確定 | ✅ `KeyNestDatabase.version = 3` / `Migration_2_3` 実装済み |
| R1: `AutofillFieldHeuristics` 公開 IF | ✅ `object` singleton。`extractMatchKeys` / `normalizeKey` 共に public |
| R1: `ParsedFields.customFieldCandidates` 全 view walk | ✅ `AssistStructureParser.parse()` が全 editable view を `customFieldCandidates` に追加 |
| R1: `FieldDescriptor` の `text` 不在 | ✅ Phase 1 で意図的に除外（`AutofillFieldHeuristics.kt` の `extractMatchKeys` コメント参照） |
| R2: 編集モードでの customField 編集サポート | ❌ Phase 1 では **見送り**（`CustomFieldsState.editable = false` in `Mode.Edit`）。Phase 2 suggest UI は新規モードのみ（§8.5） |

**残課題**: なし。Phase 2 実装着手時にこの整合性が壊れていないか念のため再確認するが、現時点で設計の前提は満たされている。

### 12.2 サジェスト UI が編集モード非対応であること

§8.5 のとおり Phase 1 が編集モードを read-only にしているため、Phase 2 suggest UI も新規モードのみで動作する。Phase 1.5 / Phase 3 等で編集モード対応が入ったときに、本 Phase 2 のロジックを **そのまま流用** できるよう、`refreshSuggestions()` のゲート条件は `customFields.editable == true` のみとし、Mode に直接依存させない（§8.1 サンプル参照）。

### 12.3 `text` 除外の型レベル担保（Phase 1 で確定）

Phase 1 `FieldDescriptor` は `text` を持たない（§0.1 / §7.2）。Phase 2 `RecordDetectedFieldsUseCase.buildRecords` も `text` を参照しない。将来 Phase 1 仕様が変わって `text` が `FieldDescriptor` に追加された場合のみ、明示 skip 防御コードを追加する（その時点での個別 PR で対応）。

### 12.4 Vault clear-all 連動 → 申し送り

Issue #10 の Danger Zone「Vault clear」は `CredentialDao.deleteAll()` を呼ぶ。Phase 2 で導入する `DetectedFieldDao.deleteAll()` も **同時に呼ぶべき**（ユーザーが credential を全削除した状況で detection 履歴だけ残るのは UX 上不自然）。`ClearVaultUseCase` に `detectedFieldRepository.deleteAll()` を追加する task を tasks.md に含める。Issue #10 で確立した既存挙動を **後方互換的に拡張する** 形になる。

### 12.5 credential 個別削除と detected_fields の関係

ユーザーが特定 credential を削除した場合、その packageName に他の credential が残っているか否かで detection の継続要否が変わる：

- 残っている → 引き続き detection 対象。`detected_fields` の row は維持。
- 残っていない → 次回 FillRequest で `findByPackage` が empty となるため新規 detection は走らない。既存 `detected_fields` の row は **そのまま残る**（Phase 2 では明示削除しない）。

申し送り: 「最後の credential が削除された packageName の detected_fields を自動 purge するか」は別 Issue（TTL 削除と同じ枠組み）に委ねる。Phase 2 では明示削除しない。

### 12.6 i18n（en リソース）

`strings.xml` の en 翻訳は追加 task として tasks.md に含めるが、Phase 2 のスコープ最小化のため **既存の en リソース有無に従う**。en リソースがプロジェクトに無い場合は ja のみで OK。

### 12.7 base ブランチ確認

PR 作成時は `gh pr create --base develop` を明示し、`gh pr view <PR> --json baseRefName` で `develop` を verify する（Issue #96 経由の運用、watcher prompt §3）。

---

## 13. 開発フロー（概要）

1. ~~Phase 1 (#66) の実装 PR が `develop` にマージされる。~~ → **完了**（commit `2e386e7`、2026-05-18 時点）。
2. 本 Phase 2 設計 PR がレビュー & マージされる（現フェーズ）。
3. Developer が本設計 PR の merge commit ベースで新 feature branch を切る (`claude/issue-67-impl-...`)。
4. tasks.md の T1 〜 T15 を **独立コミット可能な単位** で順次実装。
5. 各 task の完了ごとに `./gradlew assembleDebug / test / lint` を走らせる。
6. 実装 PR を `develop` に対して作成し、レビュー後マージ。

---

## 14. 設計サマリ（merge 時の参照用 1 段落）

`DetectedFieldEntity(packageName, fieldKey, source, lastDetectedAt)` を複合 PK で保存する `detected_fields` テーブルを Room v3 → v4 で新設し、`KeyNestAutofillService.onFillRequest` が登録済み packageName のみ AssistStructure descriptors を抽出して `RecordDetectedFieldsUseCase` に渡し、`DetectedFieldDao.upsertWithLruCap`（per-package 50 件 LRU）で書き込む。`text` source は型レベル＋実装レベルで除外。`CredentialEditActivity` は Phase 1 のカスタムフィールド入力 UI のすぐ下に chip group を持ち、`ObserveRecentDetectedFieldsUseCase` を購読して最新 10 件を表示、クリックで新規 customField 行の `fieldKey` に転送する。detection は `callback.onSuccess` 後の fire-and-forget で実行され autofill 応答 latency に加算されない。
