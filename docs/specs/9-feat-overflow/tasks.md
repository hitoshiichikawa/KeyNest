# Implementation Plan

- [x] 1. Room schema v2 拡張（`last_used_at` カラム + Migration）
- [x] 1.1 `CredentialEntity` / `Credential` / `EncryptedCredentialRecord` に `lastUsedAt: Long?` を追加
  - `data/entity/CredentialEntity.kt` に `@ColumnInfo(name = "last_used_at") val lastUsedAt: Long?` を追加（nullable, default なし）
  - `equals` / `hashCode` / `toString` に `lastUsedAt` を反映（既存 NFR 1.3 ポリシー継承 — `toString` で平文露出しないこと）
  - `domain/model/Credential.kt` の `Credential` および `EncryptedCredentialRecord` に同フィールドを追加
  - `data/repository/CredentialRepositoryImpl.kt` の mapping helpers 3 か所（`toEntity` / `toEncryptedRecord` / `toDomain`）に `lastUsedAt` を反映
  - 既存テスト（`FakeCredentialRepository`、`CredentialDaoTest`、`CredentialRepositoryImplTest`、`CredentialEditViewModelTest` 等）の Credential / EncryptedCredentialRecord コンストラクタ呼び出しに `lastUsedAt = null` を追加して compile を通す
  - _Requirements: 3.1, 3.3, 6.1_

- [x] 1.2 `KeyNestDatabase` を v2 化し `Migration_1_2` を提供
  - `data/migration/Migration_1_2.kt` を新規作成。`ALTER TABLE credentials ADD COLUMN last_used_at INTEGER` を `migrate()` で実行
  - `data/KeyNestDatabase.kt` の `version = 1` → `version = 2`、`databaseBuilder().addMigrations(Migration_1_2)`
  - `app/build.gradle.kts` に `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` を追加（Migration test の前提）
  - `app/schemas/com.example.keynest.data.KeyNestDatabase/1.json` および `2.json` を build 出力から取り込み（KSP が自動生成 → コミット）
  - _Requirements: 3.1, 3.2, 3.4, 6.1_

- [x] 1.3 Migration test を追加
  - `app/src/test/java/com/example/keynest/data/Migration_1_2_Test.kt` を新規作成。`MigrationTestHelper` で v1 schema を作成 → サンプル行を 1 件 INSERT → v2 へ migrate → 行が保持され `last_used_at IS NULL` を確認
  - `app/src/test/java/com/example/keynest/data/CredentialDaoTest.kt` の既存テスト群に対して `lastUsedAt` の追加が影響しないことを既存 assertion で担保（変更不要）
  - _Requirements: 3.1, 3.3, 3.4, 6.1_

- [x] 2. DAO / Repository に検索・並び替え・直近 5 件・last_used_at 更新を追加
- [x] 2.1 `CredentialDao` に新 query 5 本を追加
  - `observeByUpdatedAtDesc()` / `observeByLabelAsc()` / `observeByPackageAsc()` を `@Query` で実装（label / packageName は `COLLATE NOCASE`、tiebreaker は `updated_at DESC`）
  - `observeRecentlyUsed(limit: Int): Flow<List<CredentialEntity>>` を `WHERE last_used_at IS NOT NULL ORDER BY last_used_at DESC LIMIT :limit` で実装
  - `updateLastUsedAt(id: Long, timestamp: Long)` を `@Query("UPDATE ...")` で実装
  - 既存 `observeAll()` は削除せず保持（後方互換）
  - `CredentialDaoTest` に新 query 4 件分のテスト追加（`updateLastUsedAt` で対象行のみ更新 / sort 順 / `LIMIT 5` / NULL 除外）
  - _Requirements: 3.1, 3.7, 4.1, 4.2, 4.3, NFR 1.1_
  - _Boundary: CredentialDao_

- [x] 2.2 `CredentialRepository` / `CredentialRepositoryImpl` に新 IF を追加 (P)
  - IF: `observeBySort(order: CredentialSortOrder)`, `observeRecentlyUsed(limit: Int)`, `markUsed(id, timestamp)`, `duplicate(sourceId, timestamp): Result<CredentialId>`
  - `CredentialSortOrder` / `DuplicateFailure` は `ui/list/` ではなく `domain/model/` 配下に置いて domain 層で定義（UI 依存させないため）→ `domain/model/CredentialSortOrder.kt` / `domain/model/DuplicateFailure.kt` を新規作成
  - 実装側: `observeBySort` は 3 DAO Flow を `when(order)` で振り分け、`duplicate` は `findById` + 新 `EncryptedCredentialRecord.copy(id = CredentialId(0L), createdAt = ts, updatedAt = ts, lastUsedAt = null)` を `save` する（plaintext 経路を踏まない）
  - `FakeCredentialRepository` に新 IF を実装
  - `CredentialRepositoryImplTest` で `markUsed` / `duplicate` の動作確認
  - _Requirements: 3.1, 4.1, 4.3, 5.3, 5.4, NFR 1.4_
  - _Boundary: CredentialRepository, CredentialRepositoryImpl_
  - _Depends: 2.1_

- [x] 3. Domain use-case 3 種を追加
- [x] 3.1 `ObserveRecentlyUsedUseCase` / `MarkCredentialUsedUseCase` / `DuplicateCredentialUseCase` (P)
  - 3 ファイルを `domain/usecase/` に新規作成（design.md「Components and Interfaces」記載の signature）
  - 各 use-case の単体テスト（`FakeCredentialRepository` で fixture）を `domain/usecase/` 配下に追加
  - `ListCredentialsUseCase` の `invoke()` に `order: CredentialSortOrder = UpdatedAtDesc` を default 引数で追加し、`repo.observeBySort(order)` を呼ぶ
  - `ServiceLocator` に新 use-case 3 つを lazy で公開
  - _Requirements: 3.1, 3.2, 3.3, 3.6, 3.7, 4.1, 4.2, 4.3, 5.3, 5.4, NFR 1.4_
  - _Boundary: ObserveRecentlyUsedUseCase, MarkCredentialUsedUseCase, DuplicateCredentialUseCase, ListCredentialsUseCase, ServiceLocator_
  - _Depends: 2.2_

- [x] 4. `AutofillUnlockActivity` で auth 成功直後に `markUsed` を fire-and-forget
- [x] 4.1 auth success → unlock success → `markUsed(id, now)` を `launch(Dispatchers.IO)` で実行
  - 呼び出し位置は `plain.close()` の直前（design.md「Autofill Layer」参照）
  - 失敗時は `SafeLogger.warn` で残し、unlock 結果には影響させない（NFR 1.2: 失敗 log に平文を含めない → reason は class name のみ）
  - `MarkCredentialUsedUseCase` 単独で失敗しても `setResult(RESULT_OK, ...)` の挙動は変えない（既存 `onFillRequest` 経路の挙動を保つ Req 6.3）
  - 既存テスト `AutofillFlowTest` が変更後も pass することを確認（fire-and-forget なので race 検証は別途）
  - _Requirements: 3.2, 6.1, 6.3, NFR 1.2, NFR 2.2_

- [ ] 5. ViewModel state 拡張: query / filter / sort / recentList を combine
- [x] 5.1 `CredentialListUiState` / `CredentialFilter` / `EmptyKind` を新規作成
  - `ui/list/CredentialListUiState.kt`, `ui/list/CredentialFilter.kt`, `ui/list/EmptyKind.kt` を作成（design.md 記載の data class / sealed / enum 定義）
  - `CredentialSortOrder` は task 2.2 で domain 層に置いたものを import
  - _Requirements: 1.3, 1.4, 2.1, 2.5, 4.1, 4.2_

- [ ] 5.2 `CredentialListViewModel` を MVI 風入力 StateFlow に拡張
  - `MutableStateFlow` 3 本（query / filter / sort）と公開 `uiState: StateFlow<CredentialListUiState>` を追加
  - `combine` で `(query, filter, sort.flatMapLatest { listUseCase(it) })` → `mainList`、`recentUseCase()` と合流して `uiState` を構築
  - `onQueryChanged` / `onFilterChanged` / `onSortChanged` / `onDuplicate(id)` を公開、`delete(id)` は既存維持
  - `emptyKind` は `(mainList.isEmpty()) → Initial（query 空 & filter None）or NoMatch（それ以外）` で算出
  - `Factory` を新 use-case 3 つを取るよう更新、`CredentialListActivity` 側の `viewModels { Factory(...) }` も更新
  - 単体テスト: `CredentialListViewModelTest` を新規作成（query/filter/sort の combine、空状態種別、recent list が query/filter 非依存であること、検索照合がローカルのみで完結すること）
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.2, 2.3, 2.4, 2.5, 2.7, 3.1, 3.3, 3.6, 4.1, 4.2, 4.3, 4.4, 4.5, NFR 1.1_
  - _Boundary: CredentialListViewModel_
  - _Depends: 3.1, 5.1_

- [ ] 6. UI: 検索バー / フィルタチップ / 並び替え / カルーセル / 行 overflow を XML 拡張
- [ ] 6.1 レイアウト / メニュー / 文字列リソースを追加
  - `res/layout/credential_list_activity.xml` を改修: AppBar 直下に検索 `TextInputLayout` + `ChipGroup`（`app:singleSelection="true"`） + 並び替えボタン、本体上部に「最近使った」header + 横スクロール `RecyclerView`（`recent_recycler`）、その下に従来 `recycler`、`empty_view` は維持
  - `res/layout/credential_list_item.xml` に右端 `ImageButton`（`btn_overflow`、minWidth/minHeight=48dp、`contentDescription="@string/credential_list_row_overflow_a11y"`）を追加 — adapter で password を bind しないこと（NFR 1.3）
  - `res/layout/credential_list_recent_item.xml` を新規作成（card 1 枚分。`minHeight=48dp`、label / username を表示。password は表示しない NFR 1.3）
  - `res/menu/credential_list_row_overflow.xml` を新規作成（`action_duplicate` 1 項目のみ — Req 5.5 に従い export/share は含めない）
  - `res/menu/credential_list_sort.xml` を新規作成（3 項目: 更新日時降順 / ラベル昇順 / packageName 昇順）
  - `res/values/strings.xml` に新規文字列を追加（design.md「Modified Files」参照、計 13 個）
  - chip は Material `Chip`（`android:checkable="true"`）+ ChipGroup `singleSelection` を使用し、selected/not selected 状態が a11y に公開されることを確認（NFR 3.3）
  - _Requirements: 2.1, 3.1, 4.1, 5.1, 5.2, 5.5, NFR 1.3, NFR 3.1, NFR 3.2, NFR 3.3, NFR 4.1_

- [ ] 6.2 `RecentlyUsedCarouselAdapter` を新規作成
  - `ui/list/RecentlyUsedCarouselAdapter.kt`: `ListAdapter<Credential, ...>` + `DiffUtil`（`id` + `lastUsedAt` で content 比較）、card の `contentDescription` に label / 相対時刻 を埋め込み
  - password / ciphertext を bind しない（NFR 1.3）
  - _Requirements: 3.1, 3.5, NFR 1.3, NFR 3.1_
  - _Boundary: RecentlyUsedCarouselAdapter_
  - _Depends: 5.1_

- [ ] 6.3 `CredentialListAdapter` / `CredentialListActivity` を UiState 駆動に書き換え
  - `CredentialListAdapter`: constructor に `onOverflowClick: (Credential, View) -> Unit` を追加し、`ViewHolder.bind` で `binding.btnOverflow.setOnClickListener { onOverflowClick(item, it) }`
  - `CredentialListActivity`:
    - 検索 `EditText.addTextChangedListener` → `viewModel.onQueryChanged`
    - `ChipGroup.setOnCheckedStateChangeListener` で選択 chip → `viewModel.onFilterChanged`（同 chip 再操作 → `None`）
    - 並び替えボタン → `PopupMenu(R.menu.credential_list_sort)` → `viewModel.onSortChanged`
    - `binding.recentRecycler` を `LinearLayoutManager(HORIZONTAL)` + `RecentlyUsedCarouselAdapter` で構築、card click → `startEdit`（行タップと同等 Req 3.5 / 6.2）
    - `viewModel.uiState.collect { state -> ... }` で全 view を更新: mainList を adapter に submit、recentList が空なら header + recycler を `GONE`、`emptyKind` で `empty_view` テキスト切替
    - adapter の `onOverflowClick` で `PopupMenu(R.menu.credential_list_row_overflow)` を表示し、`action_duplicate` → `viewModel.onDuplicate(id)`、Snackbar で結果通知
    - 既存「行長押し削除」「FAB 新規作成」「アクションバー Autofill 設定」「`AutofillEnableActivity` リダイレクト」は変更しない（Req 5.6 / 6.1）
    - `SafeLogger` への引数に query 文字列 / username / label / packageName の平文を含めない（NFR 1.2 — 既存 `emit size=${list.size}` パターン継承）
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.5, 2.6, 2.7, 3.1, 3.4, 3.5, 4.1, 4.3, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 6.1, 6.2, NFR 1.2_
  - _Boundary: CredentialListActivity, CredentialListAdapter_
  - _Depends: 5.2, 6.1, 6.2_

- [ ] 7. UI 結合テストを追加
- [ ] 7.1 Espresso UI テストを `app/src/androidTest/` に追加
  - `ui/list/CredentialListActivityTest.kt` を新規作成。design.md「Testing Strategy / E2E」5 ケースを実装
    - 検索 incremental（1 文字入力 → 部分一致絞り込み、クリアで全件復帰）
    - ChipGroup 排他選択（片方→他方で前者解除）
    - 並び替え PopupMenu（ラベル昇順選択で 1 番目が変わる）
    - 行 ︙ → 「複製」のみ表示 → 選択で行数 +1
    - recent カルーセル: `lastUsedAt` 全 NULL では header / recycler が `not displayed`、1 件 mark すると表示
  - fixture は in-memory Room（既存 `CredentialDaoTest` パターン流用）を Activity に注入できるよう `ServiceLocator` の test override を検討（必要なら別 helper を追加）
  - _Requirements: 1.1, 1.2, 1.4, 2.1, 2.6, 3.1, 3.4, 4.1, 4.3, 5.1, 5.2, 5.5_

- [ ]* 7.2 必要に応じて性能テスト fixture を追加
  - 500 件規模の Credential を Fake repo で生成し、`CredentialListViewModelTest` で query 1 文字あたりの combine 実行時間が JVM 上 < 50ms（実機 200ms 中央値の代替指標）を満たすことを確認
  - _Requirements: NFR 2.1_
