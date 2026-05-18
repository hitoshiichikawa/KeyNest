# Task Breakdown — Issue #67 feat(autofill): detected_fields ログによる「最近検出されたフィールド」サジェスト (Phase 2)

> 関連: [requirements.md](./requirements.md) / [design.md](./design.md) / Issue #67
> 各タスクは **独立コミット可能** な粒度。順序は推奨順だが T1 〜 T4 は並行可能。

## 前提条件

Phase 1 (Issue #66) の実装 PR は `develop` にマージ済み（commit `2e386e7`、2026-05-18 確認）。本 spec が前提とする Phase 1 の契約は **`develop` 上で確認済み**（design.md §0.1 / §12.1 表参照）:

- ✅ `KeyNestDatabase` version = 3、entities = `[CredentialEntity::class]`、`addMigrations(Migration_1_2, Migration_2_3)`
- ✅ `Migration_2_3` は `credentials` に `custom_fields_ciphertext` / `custom_fields_iv` BLOB を追加
- ✅ `AutofillFieldHeuristics` は **`object` singleton**。`FieldDescriptor` は `text` を持たない。`extractMatchKeys` / `normalizeKey` 共に public
- ✅ `AssistStructureParser.parse()` は `ParsedFields(usernameId, passwordId, customFieldCandidates)` を返し、全 editable view を `customFieldCandidates` に追加する
- ✅ `KeyNestAutofillService.onFillRequest` には 3 つの short-circuit 経路あり（structure null / hasUsernameAndPassword false / callerPackage null）
- ❌ `CredentialEditViewModel.CustomFieldsState.editable` は `Mode.Edit` で `false`（編集モード非対応、Phase 1 が Phase 2 へ申し送り）

着手前再確認: Phase 1 のいずれかが本 spec マージ後に変更されていれば、その差分箇所の task を update する。

## タスク一覧

### [x] T1. Room schema v4: `DetectedFieldEntity` 追加

**ファイル**:
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/entity/DetectedFieldEntity.kt`
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/entity/DetectedFieldSource.kt`

**内容**:
- `DetectedFieldEntity` を design.md §3.1 のとおり定義（`primaryKeys = ["package_name", "field_key", "source"]` の複合 PK、`Index(value = ["package_name", "last_detected_at"], orders = [ASC, DESC])`）。
- `DetectedFieldSource` enum を design.md §3.2 のとおり定義（4 値、`text` を含めない）。`fromStorageKey` ヘルパも追加。
- `KeyNestDatabase.@Database(entities = [...])` に `DetectedFieldEntity::class` を追加し、`version = 4` に bump。`abstract fun detectedFieldDao(): DetectedFieldDao` の宣言は T2 と合わせてコミットしてよい。

**Definition of Done**:
- `./gradlew :app:assembleDebug` がコンパイル成功する（DAO は T2 で実装、`abstract fun` 宣言は T2 commit に含める）。
- `equals` / `hashCode` / `toString` は data class auto-generated で OK。

**Estimated size**: 60〜100 LOC

---

### T2. `DetectedFieldDao` の追加

**ファイル**:
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/dao/DetectedFieldDao.kt`
- 既存: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/KeyNestDatabase.kt`（`detectedFieldDao()` abstract fun 追加 + `entities` に追加 + `version = 4`）

**内容**:
- design.md §5.1 のとおり以下のメソッドを定義:
  - `@Insert(onConflict = REPLACE) suspend fun insertOrReplaceInternal(entity)`
  - `@Query(...) suspend fun countByPackage(pkg): Int`
  - `@Query(DELETE ...) suspend fun deleteOldestByPackage(pkg, count)`
  - `@Query(SELECT ...) fun observeRecentByPackage(pkg, limit): Flow<List<DetectedFieldEntity>>`
  - `@Transaction suspend fun upsertWithLruCap(entity, capacity = LRU_CAPACITY)`
  - `@Query suspend fun deleteByPackage(pkg)`
  - `@Query suspend fun deleteAll()`
  - `companion object { const val LRU_CAPACITY = 50 }`

**Definition of Done**:
- `./gradlew :app:assembleDebug` 成功（KSP が DAO を生成）。
- `app/schemas/io.github.hitoshiichikawa.keynest.data.KeyNestDatabase/4.json` が生成され git tracking 対象に追加されている（次の T3 と合わせて commit してもよい）。

**Estimated size**: 70〜120 LOC

---

### T3. Migration_3_4 実装と schemas/4.json コミット

**ファイル**:
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/migration/Migration_3_4.kt`
- 既存: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/KeyNestDatabase.kt`（`addMigrations(...)` に追加）
- 新規: `app/schemas/io.github.hitoshiichikawa.keynest.data.KeyNestDatabase/4.json`（KSP 出力をそのまま add）

**内容**:
- `Migration_3_4` を design.md §4.2 のとおり実装。
- `KeyNestDatabase.companion.create()` の `.addMigrations(Migration_1_2, Migration_2_3, Migration_3_4)` に追加。
- gradle build で生成された `4.json` を **git add** する（Issue #63 の運用に従う）。
- `4.json` の差分が Phase 1 の `3.json` から `detected_fields` テーブル追加のみであることを確認。

**Definition of Done**:
- `./gradlew :app:assembleDebug` 成功。
- `git status` で `4.json` が staged になり、`credentials` 関連の既存 schema 差分が無いこと。

**Estimated size**: 30〜50 LOC + auto-generated JSON

---

### T4. ドメイン型 `DetectedField` と repository インターフェース

**ファイル**:
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/model/DetectedField.kt`
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/repository/DetectedFieldRepository.kt`
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/repository/DetectedFieldRepositoryImpl.kt`

**内容**:
- `DetectedField` data class を design.md §3.3 のとおり定義。
- `DetectedFieldRepository` interface を design.md §6.1 のとおり定義。
- `DetectedFieldRepositoryImpl` を design.md §6.2 のとおり実装（mapping helper はファイル private）。
- 未知の `source` 文字列は skip（null 安全）。

**Definition of Done**:
- `./gradlew :app:assembleDebug` 成功。
- T1 / T2 / T3 と相互に整合（DAO への呼び出しが正しいシグネチャで通る）。

**Estimated size**: 80〜120 LOC

---

### T5. `RecordDetectedFieldsUseCase` 実装

**ファイル**:
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/usecase/RecordDetectedFieldsUseCase.kt`

**内容**:
- design.md §7.1 のとおり実装。
- 引数: `(packageName: String, descriptors: List<AutofillFieldHeuristics.FieldDescriptor>)`。
- コンストラクタ: `(detectedFieldRepository, credentialRepository, clock = { System.currentTimeMillis() })`。**`AutofillFieldHeuristics` は `object` singleton なので DI 引数として受けない**（`AutofillFieldHeuristics.normalizeKey(...)` を直接呼ぶ）。
- `CredentialRepository.findByPackage(pkg)` が empty の場合は no-op で早期 return。
- 各 descriptor から 4 source（autofillHints / hint / idEntry / contentDescription）を抽出し、normalize 後に blank なら skip。
- 保存する `fieldKey` は **raw 値**（Phase 1 と同方針）。
- `text` は descriptor が持たないため自然に除外（型レベル、§0.1）。Phase 1 仕様が将来変わったら明示 skip コードを追加。

**Definition of Done**:
- 単体テスト（T9 で書く）が成立する。
- 既存 use case の thread / dispatcher 慣行に従う。

**Estimated size**: 80〜120 LOC

---

### T6. `ObserveRecentDetectedFieldsUseCase` 実装

**ファイル**:
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/usecase/ObserveRecentDetectedFieldsUseCase.kt`

**内容**:
- 引数: `(packageName: String, limit: Int = 10): Flow<List<DetectedField>>`
- 内部で `detectedFieldRepository.observeRecentByPackage(packageName, limit)` をそのまま返す。
- limit のデフォルト = 10（requirements Req 4.1）。

**Definition of Done**:
- ViewModel から呼び出される T8 のテストで間接的に検証。

**Estimated size**: 20〜30 LOC

---

### T7. `KeyNestAutofillService.onFillRequest` への組み込み

**ファイル**:
- 既存: `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/KeyNestAutofillService.kt`
- 既存: `app/src/main/java/io/github/hitoshiichikawa/keynest/di/ServiceLocator.kt`

**内容**:
- `ServiceLocator` に `detectedFieldRepository` / `recordDetectedFieldsUseCase` / `observeRecentDetectedFieldsUseCase` を追加（design.md §7.4）。
- `KeyNestAutofillService.onFillRequest` で detection の launch 位置は **`callerPackage` 確定直後・(b) `!parsed.hasUsernameAndPassword` short-circuit より前**（design.md §7.3）:
  - これにより Phase 2 の主要ユースケース（Phase 1 ヒューリスティクスでは username/password として認識されないが credential が登録済みのアプリ）でも detection が走る。
  - 登録済みゲートは use case 側で持つため、AutofillService 側で重複チェックしない。
- `descriptors = parsed.customFieldCandidates.map { it.descriptor }`。`descriptors.isEmpty()` のときは launch 自体を skip（余計な coroutine 起動コスト 0）。
- `scope.launch(Dispatchers.IO)` で起動（Room I/O、親 `scope` は `Dispatchers.Default`）。
- 例外は内側 try/catch で `SafeLogger.warn` して swallow（Req 3.8）。
- detection launch は **`handlerJob` に bind しない**（`scope` 直下の独立 child job、§7.3）。cancellation や callback タイミングと完全 decouple する。

**Definition of Done**:
- 既存テストが fail しない（既存 short-circuit 経路 (a)/(b)/(c) の挙動は変更しない）。
- `./gradlew :app:assembleDebug` 成功。
- 手動動作確認: 登録済み packageName のアプリで autofill を発火させた後、Room inspector で `detected_fields` テーブルに row が追加されることを確認（実機 or エミュ）。Phase 1 ヒューリスティクスが username/password を認識しないアプリ（custom field 主体のフォーム）でも row が追加されることを確認する。

**Estimated size**: 30〜50 LOC

---

### T8. 編集画面 ViewModel への suggestion 状態追加

**ファイル**:
- 既存: `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditViewModel.kt`
- 既存: `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditActivity.kt`

**内容**（design.md §8.1 / §8.2）:
- `SuggestionState` / `DetectedFieldSuggestion` 型を ViewModel 内 sealed/data class として追加。
- `_suggestion: MutableStateFlow<SuggestionState>` と `val suggestion: StateFlow<SuggestionState>` を追加。
- Phase 1 が定義する「customField 行追加」アクションの中で `refreshSuggestions()` を呼ぶ。Phase 1 のメソッド名は実装時に確認。
- `onSuggestionClicked(item)` で `focusedRowIndex` の customField row の `fieldKey` を更新し、サジェスト一覧を再 emit。
- 重複排除: 既存の customFields に存在する `fieldKey`（normalize 一致）はサジェストから除外。`distinctBy(normalize)` も適用。
- `viewModelScope.launch { observeRecentDetectedFieldsUseCase(pkg, 10).collect { ... } }` の subscription。`packageName` 変更時に再 subscribe（`flatMapLatest` または手動 cancel & relaunch）。
- Phase 1 が編集モードでの customField 編集をサポートしない場合は、`new` モード時のみ subscription を張る。

**Activity 側**:
- layout XML（T8b）の chip group / empty TextView をバインド。
- `viewModel.suggestion` を `repeatOnLifecycle(STARTED)` で collect し、chip を動的生成。chip クリックで `viewModel.onSuggestionClicked(item)` を呼ぶ。

**Definition of Done**:
- T11 の ViewModel test が pass。
- 手動動作確認: 登録済み credential の編集画面でカスタムフィールド追加 → chip 列が表示される。

**Estimated size**: 150〜250 LOC（ViewModel + Activity 両方）

---

### T8b. layout XML / strings.xml の追加

**ファイル**:
- 既存: `app/src/main/res/layout/credential_edit_activity.xml`
- 既存: `app/src/main/res/values/strings.xml`
- （存在すれば）既存: `app/src/main/res/values-en/strings.xml`

**内容**:
- design.md §8.2 のとおり HorizontalScrollView + ChipGroup + empty TextView を Phase 1 で追加される「カスタムフィールド」セクション直下に配置。
- `R.string.credential_edit_detected_fields_label` = "最近検出されたフィールド"
- `R.string.credential_edit_detected_fields_empty` = "履歴なし。アプリで一度フォームを開くと候補が表示されます。"
- en リソースが存在する場合は同 key で英訳を追加（"Recently detected fields" / "No detection history yet..."）。

**Definition of Done**:
- `./gradlew :app:assembleDebug` 成功（ID / リソース解決 OK）。
- インスペクターで layout preview が崩れていないこと。

**Estimated size**: 30〜50 LOC XML

---

### T9. RecordDetectedFieldsUseCaseTest

**ファイル**:
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/domain/usecase/RecordDetectedFieldsUseCaseTest.kt`

**内容**（design.md §9.3）:
- `findByPackage` empty → upsert 呼ばれない。
- credential 1 件 + descriptors に各 source あり → 各 source ごとに upsert 1 回。
- blank fieldKey は skip。
- `text` source は型レベルで含まれない（コメントで明示）。
- `clock` を injectable にして `lastDetectedAt` を assertion 可能にする。

**Definition of Done**:
- `./gradlew :app:testDebugUnitTest --tests "*RecordDetectedFieldsUseCaseTest*"` pass。

**Estimated size**: 120〜180 LOC

---

### T10. DetectedFieldDaoTest

**ファイル**:
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/data/dao/DetectedFieldDaoTest.kt`

**内容**（design.md §9.2）:
- Room.inMemoryDatabaseBuilder で v4 schema の test DB 構築。
- INSERT / 同 PK で REPLACE / `observeRecentByPackage` の order / limit / `upsertWithLruCap` の capacity 維持 / 他 packageName の row 非干渉。
- Robolectric ベース（既存 Migration_1_2_Test と同流儀）。

**Definition of Done**:
- `./gradlew :app:testDebugUnitTest --tests "*DetectedFieldDaoTest*"` pass。

**Estimated size**: 200〜300 LOC

---

### T11. CredentialEditViewModelTest（サジェスト関連）

**ファイル**:
- 既存または新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditViewModelTest.kt`

**内容**（design.md §9.5）:
- onSuggestionClicked で focusedRowIndex の customField row の fieldKey が更新される。
- 既存 fieldKey はサジェスト除外。
- normalize 一致での dedupe。
- 空履歴で emptyState 表示。

**Definition of Done**:
- `./gradlew :app:testDebugUnitTest --tests "*CredentialEditViewModelTest*"` pass。

**Estimated size**: 150〜220 LOC

---

### T12. Migration_3_4_Test

**ファイル**:
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/data/Migration_3_4_Test.kt`

**内容**（design.md §9.1）:
- v3 schema を SQL で直接構築し、credentials に row 1 件 insert。
- `Migration_3_4.migrate(db)` を直接呼ぶ。
- detected_fields テーブル存在 / 複合 PK / index / credentials 行保持 / credentials 列構成不変 を assert。
- idempotency test。

**Definition of Done**:
- `./gradlew :app:testDebugUnitTest --tests "*Migration_3_4_Test*"` pass。

**Estimated size**: 150〜200 LOC

---

### T13. AutofillService 統合テスト（任意・Phase 1 のテストフィクスチャに依存）

**ファイル**:
- 既存または新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/autofill/...Test.kt`

**内容**（design.md §9.4）:
- Phase 1 が `KeyNestAutofillService` のテストフィクスチャ（fake AssistStructure / fake repository 注入機構）を整備している場合、それを流用して以下を検証：
  - 登録済み packageName で `recordDetectedFieldsUseCase.invoke` が呼ばれる。
  - 未登録で呼ばれない。
  - detection 失敗が callback.onSuccess に影響しない。
- Phase 1 がテストフィクスチャを整備していない場合は T13 を skip し、tasks.md の確認事項に追記。

**Definition of Done**:
- 該当テストが pass、または skip 理由を tasks.md / PR description に記載。

**Estimated size**: 100〜200 LOC（フィクスチャ整備度合いに依存）

---

### T14. Vault clear-all 連動

**ファイル**:
- 既存: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/usecase/ClearVaultUseCase.kt`

**内容**（design.md §12.4）:
- `ClearVaultUseCase` 内で `credentialRepository.deleteAll()` の後に `detectedFieldRepository.deleteAll()` を呼ぶ。
- DI 注入を `ServiceLocator.clearVaultUseCase` に追加。
- 既存の `ClearVaultUseCase` のテストに「detected_fields も削除される」検証を追加。

**Definition of Done**:
- 既存 ClearVaultUseCase テストが pass し、新規 assertion も成立。
- Issue #10 の Danger Zone 動作が壊れていない。

**Estimated size**: 30〜60 LOC

---

### T15. リンタ / フォーマッタ通過 + 動作確認

**内容**:
- `./gradlew :app:lintDebug` / `./gradlew :app:testDebugUnitTest` / `./gradlew :app:assembleDebug` を全て pass。
- 実機 or エミュで E2E の流れ（autofill 発火 → detected_fields に row 追加 → 編集画面でサジェスト表示 → クリック転送）を最低 1 回確認。
- PR description に動作確認結果のスクリーンショットまたはログを添付。

**Definition of Done**:
- 上記 3 つの gradle コマンドが全て成功する。
- 動作確認の証跡が PR description にある。

**Estimated size**: 動作確認 30 分目安

---

## 並行・順序制約

- **T1 〜 T4 は相互に並行可能**（同一 commit にまとめてもよい）。
- T5 / T6 は T4 完了後に着手可能。
- T7 は T1 〜 T5 完了後（DAO / repository / use case が揃ってから）。
- T8 / T8b は T6 完了後に着手可能。
- T9 / T10 / T11 / T12 はそれぞれの実装 task 完了後に着手。
- T13 は T7 完了後、かつ Phase 1 のテストフィクスチャの状態に依存。
- T14 は T1 〜 T4 完了後（detected_field repository が揃ってから）。
- T15 は最後。

## 推奨コミット粒度

| commit | 含める task |
|---|---|
| commit 1 | T1 + T2 + T3 + T4（schema + DAO + migration + repository） |
| commit 2 | T5 + T6（use case 2 つ） |
| commit 3 | T7（AutofillService 組み込み） |
| commit 4 | T8 + T8b（UI / ViewModel / layout） |
| commit 5 | T9 + T10（use case / DAO テスト） |
| commit 6 | T11（ViewModel テスト） |
| commit 7 | T12（Migration テスト） |
| commit 8 | T13（任意、AutofillService 統合テスト） |
| commit 9 | T14（Vault clear 連動） |
| commit 10 | T15（lint / 動作確認反映） |

## 確認事項（実装着手前に Phase 1 マージ状態を確認）

1. Phase 1 (#66) 実装 PR が `develop` にマージされているか？
2. Phase 1 の `FieldDescriptor` に `text` が含まれていないか？（含まれていれば T5 で明示 skip 追加）
3. Phase 1 の `AssistStructureParser.parse()` が `customFieldCandidates` を全 editable view から抽出するか？
4. Phase 1 が編集モードでの customField 編集をサポートするか？（しない場合は T8 の subscription を新規モード限定に）
5. Phase 1 が `KeyNestAutofillService` のテストフィクスチャを整備したか？（T13 のスコープ判断材料）
6. Phase 1 の `Migration_2_3` のテストが既存 `Migration_1_2_Test` と同じ Robolectric 流儀か？（T12 で同流儀を踏襲）

これらは設計 PR review 時点では Phase 1 の状態が未確定なため、tasks.md の確認事項として残し、実装着手時点で再確認する。
