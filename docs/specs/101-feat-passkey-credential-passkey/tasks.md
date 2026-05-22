# Task Breakdown — Issue #101 / feat(passkey): 既存 credential 一覧画面に PassKey 表示を統合

> 関連: `requirements.md`, `design.md`（本ディレクトリ）
>
> 各タスクは独立コミット可能な粒度で、依存順に並べている。Developer はこの順番で実装する。
>
> 略号:
> - **R**: `requirements.md` の Requirement / Acceptance Criteria 番号 (例: R1.4)
> - **NFR**: `requirements.md` の Non-Functional Requirement 番号
> - **D**: `requirements.md` の 既決事項 (例: D-8)
> - **Q**: `requirements.md` の 未決事項 → `design.md` §17 で確定 (例: Q-1)
> - 設計詳細は `design.md` の対応セクション (`§N.M`) を参照
>
> Conventional Commits の scope は本 Issue 共通で **`feat(passkey-list)`** を採用する
> (`#99` = `passkey-register` / `#100` = `passkey-auth` と並列の命名)。
>
> 「PassKey」表記固定 (D-1) を全コミットメッセージ / KDoc / コメントで遵守する。

## 概要 / 前提

### 着手前に Developer が読むべき資料

1. `docs/specs/101-feat-passkey-credential-passkey/requirements.md` (本 Issue PM 成果物)
2. `docs/specs/101-feat-passkey-credential-passkey/design.md` (本ファイルと同ディレクトリ)
3. 依存 #91 (merged): `docs/specs/91-feat-passkey-room-migration-passkeyentit/design.md` §3 / §5 (PasskeyEntity / PasskeyDao の責務)
4. 参考 (用語整合) #99 / #100 (merged): 各々の `design.md` (PassKey 表記 / `keynest_passkey_<credentialId>` alias 規約)
5. 既存コード:
   - `app/src/main/java/.../ui/list/CredentialListActivity.kt` (現状の Activity)
   - `app/src/main/java/.../ui/list/CredentialListAdapter.kt` (現状の adapter)
   - `app/src/main/java/.../ui/list/CredentialListViewModel.kt` (現状の VM)
   - `app/src/main/java/.../ui/list/CredentialListUiState.kt` (現状の UiState)
   - `app/src/main/java/.../domain/repository/PasskeyRepository.kt` (#99 inline)
   - `app/src/main/java/.../data/repository/PasskeyRepositoryImpl.kt` (#99 inline / #100 拡張済)
   - `app/src/main/java/.../data/dao/PasskeyDao.kt` (#91)
   - `app/src/main/java/.../data/entity/PasskeyEntity.kt` (#91)
   - `app/src/main/res/layout/credential_list_item.xml` (#9 / #29 / #43 / #51)
   - `app/src/main/res/values/strings.xml` / `values-ja/strings.xml`
   - `app/src/main/java/.../di/ServiceLocator.kt`

### 前提依存

- **merged (本 Issue 着手時点で develop に含まれる前提)**:
  - #90 Service skeleton + Manifest `<service>` 配線
  - #91 `PasskeyEntity` / `PasskeyDao` (8 メソッド) / `Migration_4_5`
  - #99 `PasskeyRepository` interface (4 メソッド) + Impl (cipher/keyProvider/keyStore factory 注入)
  - #100 `PasskeyRepository` interface 3 メソッド追加 (`listDiscoverableByRpId` / `loadPrivateKey` / `signWithIncrement`) + Impl constructor 拡張 (`database` 引数追加)

### コミット運用

- T-01〜T-10 は **1 タスク = 1 コミット**を基本 (T-02 / T-07 のように関連テストを同コミットに含めてよい)
- T-11 (QA) は変更なしのチェック (commit 無し or chore commit)
- 各コミット後に `./gradlew :app:compileDebugKotlin` 成功を確認してから次タスクへ進む
- 全タスク完了後 `./gradlew :app:testDebugUnitTest` + `./gradlew :app:lintDebug` で最終確認

### タスク依存関係図

```
T-01 (DAO + Repo listAll)
   ↓
T-02 (UI model: CredentialListItem + PasskeyDisplayModel + KindFilter + Sorting + UseCase)
   ↓
T-03 (ViewModel combine 拡張)
   ↓
T-04 (Adapter multi-viewType)
   ↓
T-05 (drawable: ic_passkey_24 / ic_password_24)
   ↓
T-06 (strings: en + ja 5 件)
   ↓
T-07 (Activity wire-up + Snackbar + ServiceLocator)
   ↓
T-08 (Unit tests: ViewModel / Mapper / Sorting)
   ↓
T-09 (Instrumentation tests: Adapter viewType)
   ↓
T-10 (既存テスト追従修正)
   ↓
T-11 (QA checklist + PR description 転記)
```

---

## T-01: PasskeyDao.listAll + PasskeyRepository.listAll + テスト

### 目的

KeyNest 全体の PassKey を Flow で観測する DAO query と Repository delegate を新規追加する (R4.10 / R4.11)。本 Issue 残り全タスクが依存する **データ層 scaffold**。

### 変更ファイル

- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/dao/PasskeyDao.kt`
  - `import kotlinx.coroutines.flow.Flow` を追加
  - `@Query("SELECT * FROM passkeys ORDER BY (lastUsedAt IS NULL) ASC, lastUsedAt DESC, createdAt DESC") fun listAll(): Flow<List<PasskeyEntity>>` を追加 (`suspend` を付けない)
  - 既存 8 メソッドの signature は **一切変更しない**
- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/repository/PasskeyRepository.kt`
  - `import kotlinx.coroutines.flow.Flow` を追加
  - `fun listAll(): Flow<List<PasskeyEntity>>` を interface に **加法** で追加
  - 既存 4 メソッドの signature は **一切変更しない**
- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/repository/PasskeyRepositoryImpl.kt`
  - `override fun listAll(): Flow<List<PasskeyEntity>> = dao.listAll()` を追加
  - 既存 method / constructor / factory には触らない
- 変更: `app/src/test/java/io/github/hitoshiichikawa/keynest/data/dao/PasskeyDaoTest.kt`
  - `listAll()` の Flow 観測テスト 2 件追加 (詳細 §テスト)
- 変更: `app/src/test/java/io/github/hitoshiichikawa/keynest/data/repository/PasskeyRepositoryTest.kt` (もしくは `data/PasskeyRepositoryTest.kt` のパス)
  - `listAll()` delegate テスト 1 件 + 空テーブル時 emit テスト 1 件追加

### 公開 IF (design §4.1 / §4.2 / §4.3)

```kotlin
// PasskeyDao.kt
@Query(
    "SELECT * FROM passkeys " +
        "ORDER BY (lastUsedAt IS NULL) ASC, lastUsedAt DESC, createdAt DESC",
)
fun listAll(): Flow<List<PasskeyEntity>>
```

```kotlin
// PasskeyRepository.kt
fun listAll(): Flow<List<PasskeyEntity>>
```

```kotlin
// PasskeyRepositoryImpl.kt
override fun listAll(): Flow<List<PasskeyEntity>> = dao.listAll()
```

### 受入条件

- **R4.10 (a)**: 空テーブルで `listAll()` を collect 開始すると **空 list** を 1 回 emit する
- **R4.10 (b)**: `insert(entity1)` 後に Flow が 1 件入りの list を emit
- **R4.10 (c)**: さらに `insert(entity2)` で 2 件入りの list を emit
- **R4.10 (d)**: 並び順が `lastUsedAt DESC, createdAt DESC, NULL last` で既存 `listAllByRpId` と等価 (NULL 混在 fixture で順序 assert)
- **R4.11 (a)**: `PasskeyRepository.listAll()` が `dao.listAll()` を delegate (in-memory DAO で確認)
- **R4.11 (b)**: 空テーブルで Flow が **空 list** を emit (例外 / null emit ではない)
- **NFR 3.x**: 既存 `PasskeyDaoTest` 8 メソッド分のテスト全件 pass
- **D-9**: `KeyNestDatabase.version` 不変 / migration 追加なし

### テスト

`PasskeyDaoTest.kt` 追加ケース (Turbine + `runTest`):

```kotlin
@Test
fun listAll_empty_emitsEmptyListOnSubscribe() = runTest {
    dao.listAll().test {
        assertThat(awaitItem()).isEmpty()
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun listAll_emitsUpdatedListAfterInsert() = runTest {
    dao.listAll().test {
        assertThat(awaitItem()).isEmpty()
        dao.insert(fixture1)
        assertThat(awaitItem()).containsExactly(fixture1)
        dao.insert(fixture2)
        assertThat(awaitItem()).containsExactly(fixture1, fixture2).inOrder()  // 並び順 assert
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun listAll_ordersByLastUsedDescThenCreatedDescNullsLast() = runTest {
    // entity_lastUsed_300_created_100 / entity_lastUsed_200_created_50 /
    // entity_lastUsed_null_created_150 / entity_lastUsed_null_created_50
    dao.insertAll(entity_a, entity_b, entity_c, entity_d)
    dao.listAll().test {
        val list = awaitItem()
        assertThat(list).containsExactly(
            entity_a, entity_b, entity_c, entity_d,  // expected order
        ).inOrder()
    }
}
```

`PasskeyRepositoryTest.kt` 追加ケース:

```kotlin
@Test
fun listAll_delegatesToDao() = runTest {
    coEvery { dao.listAll() } returns flowOf(listOf(entity1, entity2))
    repository.listAll().test {
        assertThat(awaitItem()).containsExactly(entity1, entity2).inOrder()
        awaitComplete()
    }
}

@Test
fun listAll_emitsEmptyListWhenTableEmpty() = runTest {
    coEvery { dao.listAll() } returns flowOf(emptyList())
    repository.listAll().test {
        assertThat(awaitItem()).isEmpty()
        awaitComplete()
    }
}
```

Turbine が未導入なら本タスク内で `libs.versions.toml` に `turbine = "1.0.0"` (最新安定版) と `:app:build.gradle.kts` の `testImplementation(libs.turbine)` を追加。

### 実行コマンド

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "*PasskeyDaoTest*"
./gradlew :app:testDebugUnitTest --tests "*PasskeyRepositoryTest*"
```

### 想定コミットメッセージ

```
feat(passkey-list): add PasskeyDao.listAll + Repository delegate for #101

Add `fun listAll(): Flow<List<PasskeyEntity>>` to PasskeyDao with the
existing `(lastUsedAt IS NULL) ASC, lastUsedAt DESC, createdAt DESC`
ordering, and a Repository delegate that simply forwards the DAO Flow.
This is the data-layer scaffold for Issue #101 (Phase 4 of umbrella
#89): the upcoming CredentialListViewModel needs to combine the
passwords Flow with a passkeys Flow, and the existing per-RP DAO
methods (listAllByRpId / listDiscoverableByRpId) do not cover the
"all PassKeys regardless of RP" path.

- DB schema unchanged (D-9). No migration added.
- Existing 8 DAO methods and 4+3 Repository methods untouched.
- Two PasskeyDaoTest cases assert Room invalidation tracker emits new
  lists after insert, plus the NULL-last ordering matches listAllByRpId
  semantics (R4.10).
- Two PasskeyRepositoryTest cases assert delegate behavior and the
  empty-table emits empty list path (R4.11).

Refs #89 #91 #101
```

### 依存タスク

- なし (先頭)

### 対応 EARS / NFR

R4.10 / R4.11 / NFR 3.1 / D-9

---

## T-02: CredentialListItem + PasskeyDisplayModel + KindFilter + Sorting + ListPasskeysUseCase

### 目的

UI 統合の中核となる **sealed interface / projection / enum / Comparator / UseCase** を pure Kotlin で 1 コミットに集約して導入する。ViewModel / Adapter は次タスクで参照する。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListItem.kt`
  - `sealed interface CredentialListItem` + `Password(Credential)` / `Passkey(PasskeyDisplayModel)` / `SortKey` 内包 data class
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/list/PasskeyDisplayModel.kt`
  - 9 field data class + `Companion.fromEntity(PasskeyEntity): PasskeyDisplayModel`
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/list/KindFilter.kt`
  - enum 3 値 (`All` / `PasswordOnly` / `PasskeyOnly`)
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListSorting.kt`
  - `internal object CredentialListSorting` に `byLastUsedThenCreatedDesc: Comparator` + `mergeAndSort(passwords, passkeys)` 実装
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/usecase/ListPasskeysUseCase.kt`
  - `operator fun invoke(): Flow<List<PasskeyDisplayModel>>` で repo.listAll() を delegate + map 変換

### 公開 IF (design §3.1 / §3.2 / §3.3 / §3.5 / §4.4)

design §3.1 / §3.2 / §3.3 / §3.5 / §4.4 の Kotlin 確定形をそのまま実装。

### 受入条件

- **R1.4 (Comparator)**: `byLastUsedThenCreatedDesc` が NULL を末尾に集め、非 null を `lastUsedAt DESC` で並べ、tiebreaker で `createdAt DESC`
- **R1.5 (stableId)**: `Password(Credential(id=1)).stableId == "pw:1"` / `Passkey(PasskeyDisplayModel(credentialId="abc")).stableId == "pk:abc"`
- **NFR 2.1 (sensitive 列遮断)**: `PasskeyDisplayModel::class.java.declaredFields` から `userHandle` / `encryptedPrivateKey` / `privateKeyIv` / `keyAlias` / `signCount` の **どれも検出されない** (T-08 unit test で検証)
- **Q-1 (KindFilter)**: enum 3 値、`All` がデフォルト想定
- 全ファイル `internal` または `public` (KDoc 完備、PassKey 表記固定 / D-1)
- compileDebugKotlin 成功

### テスト

本タスクではテスト追加なし (T-08 で集約)。ただし `PasskeyDisplayModel.fromEntity` の単体テストだけは本タスクのコンパイル検証目的で `PasskeyDisplayModelTest` を **最小 1 ケース** (`fromEntity_copiesNonSensitiveFields`) で先行追加してもよい (Developer 判断)。

### 実行コマンド

```bash
./gradlew :app:compileDebugKotlin
```

### 想定コミットメッセージ

```
feat(passkey-list): introduce CredentialListItem sealed interface for #101

Add the UI-model layer that the upcoming ViewModel / Adapter
modifications need to merge password and PassKey rows into a single
RecyclerView (Issue #101 / Phase 4 of umbrella #89):

- `CredentialListItem` sealed interface with `Password(Credential)`
  and `Passkey(PasskeyDisplayModel)` variants. `stableId` carries a
  `"pw:"` / `"pk:"` prefix so DiffUtil's areItemsTheSame cannot
  accidentally treat a `Credential.id.value == 1L` and a
  `PasskeyEntity.credentialId == "1"` as the same row (R1.5 / D-8).
- `PasskeyDisplayModel` is a read-only 9-field projection of
  PasskeyEntity. It deliberately does NOT carry `userHandle`,
  `encryptedPrivateKey`, `privateKeyIv`, `keyAlias` or `signCount` so
  the UI cannot accidentally surface those fields (NFR 2.1).
- `KindFilter` enum (All / PasswordOnly / PasskeyOnly) — kept off the
  UI for v1 (per requirements §"未決事項" Q-2) but wired through state
  so a future chip-filter Issue can activate it without restructuring
  the ViewModel pipeline.
- `CredentialListSorting.byLastUsedThenCreatedDesc` Comparator:
  `lastUsedAt DESC, createdAt DESC, NULL last` (R1.4) using
  `nullsLast(reverseOrder())` + `thenByDescending`.
- `ListPasskeysUseCase` delegates `PasskeyRepository.listAll()`
  (introduced in the previous commit) and maps each PasskeyEntity to
  a PasskeyDisplayModel so the sensitive columns never reach the
  ViewModel layer (NFR 2.2).

DB schema unchanged. Existing files untouched.

Refs #89 #91 #101
```

### 依存タスク

- T-01 (`PasskeyRepository.listAll` 公開 IF が必要)

### 対応 EARS / NFR

R1.3 / R1.4 / R1.5 / NFR 2.1 / NFR 2.2 / D-1 / D-8 / Q-1 / Q-2 / Q-4

---

## T-03: CredentialListViewModel の combine 拡張 + UiState 型変更

### 目的

password と PassKey の Flow を `combine` で統合し、`mergeAndSort` + `applyFilter` + `applyKindFilter` + `applySearch` の pipeline を 1 つの `mainListFlow` にする。`CredentialListUiState.mainList` 型変更も同コミット。

### 変更ファイル

- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListViewModel.kt`
  - コンストラクタに `listPasskeysUseCase: ListPasskeysUseCase` を追加
  - `Factory` に同 useCase 引数を追加
  - `kindFilter: MutableStateFlow<KindFilter>` 追加 (default = `KindFilter.All`)
  - `sortedPasswordsFlow` (現 `sortedListFlow` を rename) / `passkeysFlow` を定義
  - `mainListFlow` を `combine(query, filter, kindFilter, sortedPasswordsFlow, passkeysFlow) { ... }` に書き換え、内部で `CredentialListSorting.mergeAndSort` → `applyFilter` → `applyKindFilter` → `applySearch`
  - `uiState` 構築 `combine` を 2 段 (`combine(query, filter, sort, kindFilter)` + `combine(quad, mainListFlow, recentUseCase())`) で組み立て
  - companion `applyFilter` の signature を `(list: List<CredentialListItem>, filter: CredentialFilter): List<CredentialListItem>` に変更し、sealed when で Passkey 変種は **素通し** (R2.7)
  - companion `applyKindFilter` を新規追加 (KindFilter.All で素通し / PasswordOnly で Passkey 除外 / PasskeyOnly で Password 除外)
  - companion `applySearch` の signature を `(list: List<CredentialListItem>, query: String): List<CredentialListItem>` に変更し、sealed when で variant 別の検索フィールド (design §7.1)
  - companion `computeEmptyKind` の signature に `kindFilter: KindFilter` 引数を追加 (Q-2 future-proofing)
  - `onPasskeyClicked(passkey: PasskeyDisplayModel)` を新規 public method として追加 (SafeLogger に「タップ事実」のみ記録、credentialId / rpId raw 出さない / NFR 2.3)
  - 既存 `credentials: StateFlow<List<Credential>>` の **型を `StateFlow<List<CredentialListItem>>`** に変更 (またはバージョン互換のため削除を検討、Developer 判断。本 Issue 設計では新しい collector は uiState を経由する想定なので削除を推奨)
- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListUiState.kt`
  - `mainList: List<Credential>` → `mainList: List<CredentialListItem>`
  - `kindFilter: KindFilter` 追加 (default `KindFilter.All`)
  - `passkeyCount: Int` 追加
  - `EMPTY` 定数も追従

### 公開 IF (design §4.5)

design §4.5.1 / §4.5.2 / §4.5.3 / §4.5.4 の Kotlin 確定形に従う。

### 受入条件

- **R1.4 (mergeAndSort 経由)**: password Flow に 2 件 + PassKey Flow に 2 件供給したとき、`uiState.mainList` の並びが `lastUsedAt DESC, createdAt DESC, NULL last` (design §15.1 fixture 期待値)
- **R2.1 / R2.2**: `applySearch("alice")` で password (username) と PassKey (userName) を横断 hit
- **R2.5**: `applySearch("ALICE")` でも同 hit (case-insensitive)
- **R2.7**: `applyFilter(CredentialFilter.SignatureMatched)` で Passkey variant が **素通し** で残る
- **R1.7**: mainList が空 + query 空 + filter None + kindFilter All で `EmptyKind.Initial`
- **NFR 1.2**: `combine` 内の変換が pure function であること (副作用なし)
- **NFR 2.3**: `onPasskeyClicked` の SafeLogger 出力に `credentialId` / `rpId` / `userName` raw が **含まれない** (logcat assert)
- `./gradlew :app:compileDebugKotlin` 成功
- 既存 `applyFilter` / `applySearch` の password 単独テストが signature 変更追従後も pass (T-08 / T-10 で検証)

### テスト

本タスクでは新規テスト追加なし (T-08 で集約)。compileDebug 成功 + 既存テストの **assertion 型追従が破壊しない** ことだけ確認。既存 password 単独テストは T-10 で追従修正。

### 実行コマンド

```bash
./gradlew :app:compileDebugKotlin
```

### 想定コミットメッセージ

```
feat(passkey-list): combine password and PassKey flows in ViewModel for #101

Extend CredentialListViewModel to merge the existing
ListCredentialsUseCase Flow with the new ListPasskeysUseCase Flow
into a single `mainListFlow: Flow<List<CredentialListItem>>`. The
pipeline is:

  combine(query, filter, kindFilter, passwordsFlow, passkeysFlow) ->
    mergeAndSort(passwords, passkeys)  // lastUsedAt DESC, NULL last
    .applyFilter(filter)               // signature chip — passkeys pass-through (R2.7)
    .applyKindFilter(kindFilter)       // future-facing, always All in v1
    .applySearch(query)                // case-insensitive contains, variant-aware

Public surface changes:

- Factory now takes a fifth argument `listPasskeysUseCase`.
- New `onPasskeyClicked(PasskeyDisplayModel)` records the tap fact via
  SafeLogger (`rpIdLength=...` only, never raw rpId / userName /
  credentialId — NFR 2.3). Activity-side Snackbar wiring lands in T-07.
- `CredentialListUiState.mainList` is now `List<CredentialListItem>`,
  with new fields `kindFilter: KindFilter` and `passkeyCount: Int`.
- Companion helpers (`applyFilter` / `applySearch` /
  `computeEmptyKind`) signatures change to take `List<CredentialListItem>`.

DB schema unchanged. PassKey-side filter / search uses
`PasskeyDisplayModel` fields only (rpId, rpDisplayName, userName,
userDisplayName, displayName) so sensitive entity columns are
out of reach (NFR 2.1).

Refs #89 #91 #101
```

### 依存タスク

- T-01 (Repository.listAll)
- T-02 (CredentialListItem / PasskeyDisplayModel / KindFilter / CredentialListSorting / ListPasskeysUseCase)

### 対応 EARS / NFR

R1.4 / R1.7 / R2.1 / R2.2 / R2.5 / R2.6 / R2.7 / NFR 1.2 / NFR 2.2 / NFR 2.3 / D-8 / Q-2 / Q-4

---

## T-04: CredentialListAdapter の multi-viewType 化

### 目的

`ListAdapter<Credential, ...>` を `ListAdapter<CredentialListItem, ...>` に置き換え、`PasswordViewHolder` / `PasskeyViewHolder` 2 内部クラスに分割。layout XML は **共通 1 file** (`credential_list_item.xml`) を再利用し、bind 側で visibility / icon resource / text 解決を切り替える (Q-9 確定 / design §6.3)。

### 変更ファイル

- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListAdapter.kt`
  - 親クラスを `ListAdapter<CredentialListItem, RecyclerView.ViewHolder>` に変更
  - callback 3 種 (`onItemClick` / `onItemLongClick` / `onOverflowClick`) の引数型を `(CredentialListItem) -> Unit` / `(CredentialListItem, View) -> Unit` に変更
  - `VIEW_TYPE_PASSWORD = 0` / `VIEW_TYPE_PASSKEY = 1` 定数 (`internal const val`)
  - `getItemViewType(position)` を sealed when で実装
  - `onCreateViewHolder(parent, viewType)` を viewType 別に分岐 (両 viewType とも `credential_list_item.xml` を inflate)
  - `BaseViewHolder` / `PasswordViewHolder` / `PasskeyViewHolder` の 3 内部クラスを実装 (design §4.6.4)
  - `DIFF` を `DiffUtil.ItemCallback<CredentialListItem>` に変更し、`areItemsTheSame` は `stableId` 比較、`areContentsTheSame` は variant 別 fields 比較
  - `onViewRecycled` は `if (holder is PasswordViewHolder)` でガード (PasskeyViewHolder は iconLoader 不使用)
- 変更なし: `app/src/main/res/layout/credential_list_item.xml` (Q-9 確定により共通 layout として再利用、XML 1 byte も変更しない)

### 公開 IF (design §4.6)

design §4.6.1 / §4.6.2 / §4.6.3 / §4.6.4 / §4.6.5 のとおり。

### 受入条件

- **R1.1 (同じ RecyclerView)**: `binding.recycler` で password + PassKey を 1 list として描画
- **R1.2 (種別アイコン切替)**: password 行は既存 `iconLoader.loadInto(iconApp, packageName)`、PassKey 行は `setImageResource(R.drawable.ic_passkey_24)` (T-05 で drawable 追加完了後に bind が機能)
- **R1.3 (PassKey 行のメタ表示)**: 1 行目 = `displayName ?: rpDisplayName ?: rpId` / 2 行目 = `userDisplayName ?: userName ?: R.string.credential_list_passkey_unknown_user` (T-06 で string 追加) / 3 行目 = `rpId`
- **R1.5 (DiffUtil stableId)**: `areItemsTheSame` は variant prefix を含む `stableId` 文字列比較
- **R1.6 (areContentsTheSame variant 別)**: Password 側は既存 5 fields 比較、Passkey 側は `PasskeyDisplayModel` data class equals
- **R1.8 (vector drawable 直接設定)**: `iconLoader.loadInto` を **PassKey 行で呼ばない**、`setImageResource` のみ
- **R1.9 (chip_signature / strength_bar / btn_overflow を GONE)**: PassKey 行のみ visibility を変える
- **R5.4 (overflow GONE)**: PassKey 行 `btn_overflow.visibility = View.GONE`
- **R5.3 (long-click 何もしない)**: PassKey 行 `setOnLongClickListener { true }` で consume
- **R6.1 (contentDescription)**: PassKey 行 `iconApp.contentDescription = R.string.credential_list_passkey_kind_label` (T-06 で string 追加)
- **R6.2 (password 行は @null 維持)**: PasswordViewHolder で `iconApp.contentDescription` を **明示的に null** にセット (XML が `@null` だが ViewHolder reuse 時に PassKey 側で書き換えられる可能性があるため defensive)
- `compileDebugKotlin` 成功

### テスト

本タスクではテストファイル変更なし (T-09 instrumentation で集約)。コンパイル成功と既存 `CredentialListAdapterTest` (もしあれば) の signature 追従は T-10 で実施。

### 実行コマンド

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug  # XML reference の R.drawable.ic_passkey_24 が未追加のため、T-05 完了までは fail することがある
```

> 補足: T-04 で `R.drawable.ic_passkey_24` / `R.string.credential_list_passkey_kind_label` / `R.string.credential_list_passkey_unknown_user` への参照を書くため、未追加の段階では `compileDebugKotlin` が unresolved reference を出す可能性が高い。Developer の判断で:
> - **案 A**: T-04 / T-05 / T-06 を **1 コミットにまとめる** (推奨。各 sub-task は内部で独立だが本タスクのコミット単位ではアトミック)
> - **案 B**: T-04 内で `R.drawable.ic_passkey_24` 等を **lazy 解決** にし、stub vector / stub string を先に追加して順次差し替える
>
> 推奨は **案 A**: T-04 + T-05 + T-06 を 1 コミットにまとめて build green を維持する。本 tasks.md はタスクの **論理単位** を 3 つに分けるが、コミット粒度は Developer が build green を保てる方を選ぶ。

### 想定コミットメッセージ (T-04 + T-05 + T-06 まとめのコミット例)

```
feat(passkey-list): render password and PassKey rows side by side for #101

Extend CredentialListAdapter to render `CredentialListItem` (the new
sealed interface introduced earlier in this PR) with two viewTypes:

- `VIEW_TYPE_PASSWORD` (0) → existing icon-loader + label / username /
  packageName + signature chip + StrengthBar + overflow flow (Issue #29
  / #43 / #51 behaviour preserved).
- `VIEW_TYPE_PASSKEY` (1) → ic_passkey_24 vector via setImageResource,
  three-line fallback (displayName -> rpDisplayName -> rpId / etc per
  R1.3), signature chip + StrengthBar + overflow ImageButton hidden,
  long-click consumed (R5.3 / R5.4).

Both viewTypes inflate the same `credential_list_item.xml` (Q-9
confirmed in design §6.3) — the layout file is not edited. DiffUtil
identifies rows by `stableId` (`"pw:<id>"` / `"pk:<credentialId>"`) so
a Long credential.id and a String credentialId can never collide
(R1.5 / D-8). `onViewRecycled` only cancels iconLoader for
PasswordViewHolder; PassKey rows paint synchronously and need no
race-prevention (preserves Issue #43 fix scope).

Bundled resource additions (kept in this commit to keep the build
green per the tasks.md note):

- `ic_passkey_24.xml` (Material Symbols passkey, outlined 24dp, tinted
  via ?attr/colorOnSurfaceVariant for dark-mode parity).
- `ic_password_24.xml` (reserved for the upcoming PassKey individual-
  management UI — Issue #89 sub-plan 6 — so the icon name space is
  pinned now).
- Five string keys in both values/strings.xml and values-ja/strings.xml:
  credential_list_passkey_kind_label (en/ja both "PassKey" per D-1),
  credential_list_password_kind_label, credential_list_passkey_unknown_user,
  credential_list_passkey_tap_v1_message, credential_list_passkey_a11y_icon.

No layout XML edits. No DB schema changes.

Refs #89 #101
```

### 依存タスク

- T-02 (CredentialListItem / PasskeyDisplayModel)
- T-03 (ViewModel が `List<CredentialListItem>` を emit するように変更済)
- T-05 (drawable 追加)
- T-06 (strings 追加)

### 対応 EARS / NFR

R1.1 / R1.2 / R1.3 / R1.5 / R1.6 / R1.8 / R1.9 / R5.3 / R5.4 / R6.1 / R6.2 / D-1 / D-8 / Q-9

---

## T-05: アイコン素材 (ic_passkey_24.xml / ic_password_24.xml)

### 目的

PassKey 行用の vector drawable と、後続 #89 分割案 6 で再利用する password 用 vector drawable を新規発番する (Q-1 確定 / design §9)。

### 変更ファイル

- 新規: `app/src/main/res/drawable/ic_passkey_24.xml`
  - Material Symbols `passkey` outlined 24dp / `viewportWidth = viewportHeight = 24`
  - `android:tint="?attr/colorOnSurfaceVariant"` (Material 3 theme attribute)
  - `pathData` は Material Symbols 配布 SVG を Android Studio Vector Asset Studio で取り込み
- 新規: `app/src/main/res/drawable/ic_password_24.xml`
  - Material Symbols `password` outlined 24dp (または `vpn_key` outlined 24dp)
  - 同様に `android:tint="?attr/colorOnSurfaceVariant"` で dark mode 対応
  - 本 Issue では `CredentialListAdapter` から参照されない (password 行は既存 `iconLoader` 経路を維持) が、resource 名予約 + 後続 #89 分割案 6 で利用想定

### 公開 IF (design §9)

- `R.drawable.ic_passkey_24`
- `R.drawable.ic_password_24`

### 受入条件

- 両 XML が `android:width="24dp"` / `android:height="24dp"` / `viewportWidth="24"` / `viewportHeight="24"` で完結
- `android:tint="?attr/colorOnSurfaceVariant"` または同等の theme attribute 参照
- light theme + dark theme で 24dp icon として可視 (実機 / preview で目視確認、QA は T-11 で集約)
- `aapt2` の vector parse error なし (`./gradlew :app:processDebugResources` で確認)

### テスト

XML 単体のテストは追加しない (instrumentation の T-09 で `iconApp.drawable.constantState` の resource id を assert することで間接検証)。

### 実行コマンド

```bash
./gradlew :app:processDebugResources
./gradlew :app:assembleDebug
```

### 想定コミットメッセージ

T-04 と統合する場合は §T-04 のコミットメッセージに含まれる。独立コミットにする場合:

```
feat(passkey-list): add ic_passkey_24 and ic_password_24 vectors for #101

Two new 24dp Material Symbols-derived vector drawables backing the
multi-viewType RecyclerView introduced earlier in this PR (Issue
#101). Both use `?attr/colorOnSurfaceVariant` for the tint so the
Material 3 theme produces automatically-correct values on dark
backgrounds (NFR 6.x / design §9).

- `ic_passkey_24.xml`: PassKey row icon, painted by
  PasskeyViewHolder.bind via setImageResource.
- `ic_password_24.xml`: reserved for the upcoming PassKey individual-
  management UI (Issue #89 sub-plan 6). Not referenced from this PR's
  ViewHolders because password rows continue to use IconLoader to
  resolve the actual app icon. Pinning the resource name now keeps the
  upcoming PR diff smaller.

`ic_fingerprint_24` (biometric prompt) and `ic_key_24` (autofill) are
intentionally not reused so the semantic boundary stays clean.

Refs #89 #101
```

### 依存タスク

- なし (独立)

### 対応 EARS / NFR

R1.2 / R1.8 / R3.5 / Q-1 / NFR 6.x

---

## T-06: 文字列リソース (en + ja で 5 件)

### 目的

PassKey 一覧 UI で使う 5 件の string key を `values/strings.xml` と `values-ja/strings.xml` の両方に追加する (R3.x / R6.4 / NFR 5.2)。

### 変更ファイル

- 変更: `app/src/main/res/values/strings.xml` (en default)
- 変更: `app/src/main/res/values-ja/strings.xml` (ja)

### 追加キー一覧 (design §10.1)

| key | en | ja |
|---|---|---|
| `credential_list_passkey_kind_label` | `PassKey` | `PassKey` |
| `credential_list_password_kind_label` | `Password` | `パスワード` |
| `credential_list_passkey_unknown_user` | `(no user)` | `(ユーザー名なし)` |
| `credential_list_passkey_tap_v1_message` | `PassKey management is coming in a later release.` | `PassKey の個別管理は今後のアップデートで提供予定です。` |
| `credential_list_passkey_a11y_icon` | `PassKey` | `PassKey` |

### 受入条件

- **R3.1**: `credential_list_passkey_kind_label` が en/ja 共に `"PassKey"`
- **R3.2**: `credential_list_password_kind_label` が en `"Password"` / ja `"パスワード"`
- **R3.3 / R3.4 / D-1**: ja で「passkey」「パスキー」「Passkey」表記が含まれない (`grep "パスキー\|passkey\|Passkey" values-ja/strings.xml | grep -v "PassKey"` の結果が空)
- **R6.4**: en / ja 両方に 5 件のキーが揃っている (AAPT2 で `./gradlew :app:assembleDebug` 成功)
- **NFR 5.2**: 全 key の prefix が `credential_list_passkey_*` または `credential_list_password_*`

### テスト

XML には test を書かない。`./gradlew :app:assembleDebug` で AAPT2 resolution が成功すれば OK。`./gradlew :app:lintDebug` で `MissingTranslation` warning が出ないことも確認。

### 実行コマンド

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

### 想定コミットメッセージ

T-04 と統合する場合は §T-04 に含まれる。独立コミットにする場合:

```
feat(passkey-list): add PassKey list i18n strings for #101

Add five string keys backing the multi-viewType RecyclerView and the
PassKey-tap Snackbar (Issue #101). All keys live in both
`values/strings.xml` and `values-ja/strings.xml` so AAPT2 resolves
both locales (R6.4).

- credential_list_passkey_kind_label (en/ja: "PassKey" — D-1
  trademark-style fixed spelling)
- credential_list_password_kind_label (en "Password" / ja "パスワード")
- credential_list_passkey_unknown_user (fallback when userDisplayName
  and userName are both null — R1.3)
- credential_list_passkey_tap_v1_message (Snackbar shown when the user
  taps a PassKey row in v1 — R5.2)
- credential_list_passkey_a11y_icon (contentDescription for the
  PassKey row icon — R6.1)

ja file contains no "パスキー" / "passkey" / "Passkey" — only the
fixed "PassKey" spelling, per D-1 / R3.4.

Refs #89 #101
```

### 依存タスク

- なし

### 対応 EARS / NFR

R3.1 / R3.2 / R3.3 / R3.4 / R3.5 / R5.2 / R6.1 / R6.4 / D-1 / NFR 5.1 / NFR 5.2 / Q-8

---

## T-07: Activity wire-up + Snackbar + ServiceLocator

### 目的

Adapter の callback 型変更に追従して `CredentialListActivity` の `setUpMainList` を sealed when 分岐に書き換え、PassKey 行タップ時の `Snackbar` 表示を実装する。`ServiceLocator` に `listPasskeysUseCase` を追加し、ViewModel Factory にも引数を追加する (R5.x / Q-3 確定 / design §4.7 / §4.8)。

### 変更ファイル

- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/di/ServiceLocator.kt`
  - `import io.github.hitoshiichikawa.keynest.domain.usecase.ListPasskeysUseCase` 追加
  - `val listPasskeysUseCase: ListPasskeysUseCase by lazy { ListPasskeysUseCase(passkeyRepository) }` を `listCredentialsUseCase` の隣に追記
- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListActivity.kt`
  - `viewModels { Factory(...) }` block に `ServiceLocator.listPasskeysUseCase` を追加
  - `setUpMainList()` 内 adapter コンストラクタ呼び出しの 3 callback を sealed when に書き換え (design §4.7.1)
  - `showPasskeyTapSnackbar()` private function 新規追加 (`Snackbar.make(binding.root, R.string.credential_list_passkey_tap_v1_message, Snackbar.LENGTH_SHORT).show()`)
  - `renderState` の SafeLogger 出力に `passkeyCount=${state.passkeyCount}` を追加 (NFR 2.3 / 既存の count-only policy 範囲内)
  - `applyEmptyStateVisibility` の signature が `EmptyKind?` 引数を取る既存形なら無変更。`renderEmptyView(state: CredentialListUiState)` 経由で呼ばれる経路の型変更追従のみ

### 公開 IF (design §4.7 / §4.8)

design §4.7.1 / §4.7.2 / §4.8 のとおり。

### 受入条件

- **R5.1 (a)**: PassKey 行タップで `CredentialEditActivity` が **起動しない** (sealed when の Passkey 分岐に `startEdit` の呼び出しなし)
- **R5.1 (b)**: PassKey 行タップで `Snackbar` が `binding.root` / `LENGTH_SHORT` で表示される
- **R5.1 (b)**: Snackbar 文言が `R.string.credential_list_passkey_tap_v1_message`
- **R5.3**: PassKey 行 long-click で `promptDelete(...)` が **呼ばれない** (sealed when の Passkey 分岐は `Unit`)
- **R5.4**: PassKey 行 overflow click で `showRowOverflowMenu(...)` が **呼ばれない** (Adapter 側で overflow 自体が GONE のため到達しないが、defensive で sealed when 分岐に `Unit`)
- ServiceLocator の `listPasskeysUseCase` lazy が成功 (`PasskeyRepository` 経由で `ListPasskeysUseCase` を build)
- `compileDebugKotlin` / `assembleDebug` 成功
- 既存 password 行の click / long-click / overflow の挙動が **完全に保持** されている (Issue #9 系の Empty state / sort / filter / Recently used carousel / Duplicate / Delete dialog / edit 起動が動作)

### テスト

T-09 instrumentation で adapter callback の挙動を間接検証する。Activity 単体テスト (Robolectric) があれば T-10 で追従修正。

### 実行コマンド

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

### 想定コミットメッセージ

```
feat(passkey-list): wire CredentialListActivity for PassKey rows (#101)

Plug the multi-viewType adapter into CredentialListActivity:

- ServiceLocator exposes a new `listPasskeysUseCase` (delegates
  `PasskeyRepository.listAll()` via the ListPasskeysUseCase introduced
  earlier in this PR).
- Activity-side `viewModels { Factory(...) }` block forwards the new
  use case as the fifth argument.
- `setUpMainList()` rewires the three adapter callbacks (onItemClick /
  onItemLongClick / onOverflowClick) to a `when (item)` sealed branch:
  Password rows keep the existing startEdit / promptDelete /
  showRowOverflowMenu paths, PassKey rows show a Snackbar with
  `R.string.credential_list_passkey_tap_v1_message` (R5.1 / R5.2) and
  consume long-click + overflow without side effects (R5.3 / R5.4).
- `renderState` SafeLogger now logs `passkeyCount=...` alongside the
  existing `size=...` / `recent=...` / `emptyKind=...` (NFR 2.3 — no
  raw credentialId / rpId / userName).

No change to res/layout XML. No DB / schema change. Existing password
flows are completely preserved (Issue #9 / #29 / #43 / #51 paths
unchanged — verified by passing the existing CredentialListActivity
Robolectric tests after type follow-up in the next commit).

Refs #89 #101
```

### 依存タスク

- T-02 / T-03 / T-04 / T-06

### 対応 EARS / NFR

R5.1 / R5.2 / R5.3 / R5.4 / R5.5 / NFR 2.3 / Q-3 / Q-7

---

## T-08: Unit tests (ViewModel / Mapper / Sorting)

### 目的

design §12.1 / §15 で確定したテスト戦略をコード化する。新規 3 ファイル + 既存 `CredentialListViewModelTest` 拡張。

### 変更ファイル

- 変更: `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListViewModelTest.kt`
  - 既存 password 単独テストの assertion 型を `List<CredentialListItem>` に追従
  - 新規ケース (R4.1 / R4.2 / R4.3 / R4.7 対応、design §15.2〜§15.5):
    - `combine_mergesPasswordsAndPasskeys_byLastUsedDescNullsLast`
    - `applySearch_hitsPasskeyUserName_caseInsensitive`
    - `applySearch_hitsPasskeyRpId`
    - `applySearch_hitsPasskeyRpDisplayName`
    - `applySearch_returnsEmpty_whenNoneMatch`
    - `applyFilter_signatureMatched_keepsPasskeyRows`
    - `applyFilter_signatureMissing_keepsPasskeyRows`
    - `applyKindFilter_passwordOnly_dropsPasskeyRows`
    - `applyKindFilter_passkeyOnly_dropsPasswordRows`
    - `computeEmptyKind_initial_whenAllInputsAreDefault`
    - `computeEmptyKind_noMatch_whenQueryActive`
    - `uiState_passkeyCount_reflectsCurrentMainList`
    - `onPasskeyClicked_doesNotMutateState_andDoesNotEmitCredentialId` (NFR 2.3 — SafeLogger spy で raw credentialId / rpId が log message に含まれないことを assert)
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/list/PasskeyDisplayModelTest.kt`
  - `fromEntity_copiesNonSensitiveFields` (9 field を全てチェック)
  - `dataClassDoesNotExposeSensitiveEntityFields` (reflection で `userHandle` / `encryptedPrivateKey` / `privateKeyIv` / `keyAlias` / `signCount` がないこと)
  - `dataClassEquals_handlesNullableFields` (`rpDisplayName` / `userName` / `userDisplayName` / `displayName` / `lastUsedAt` の null 対応)
  - `fromEntity_preservesIsDiscoverableFlag` (true / false)
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListSortingTest.kt`
  - `byLastUsedThenCreatedDesc_bothNonNull_ordersByLastUsedDesc`
  - `byLastUsedThenCreatedDesc_oneNull_putsNullLast`
  - `byLastUsedThenCreatedDesc_bothNull_ordersByCreatedDesc`
  - `byLastUsedThenCreatedDesc_sameLastUsed_usesCreatedDescTiebreaker`
  - `byLastUsedThenCreatedDesc_identicalKeys_isStable`
  - `byLastUsedThenCreatedDesc_longMaxValueBoundary`
  - `mergeAndSort_combinesAndSorts_inDescNullsLast` (password 2 + passkey 2 = 4 件 fixture / design §15.1)
  - `mergeAndSort_emptyInputs_returnsEmpty`
  - `mergeAndSort_onlyPasswords_returnsAllAsPasswordVariants`
  - `mergeAndSort_onlyPasskeys_returnsAllAsPasskeyVariants`

### 公開 IF

- 各テストは `internal` / `public`、JUnit4 + Truth + Turbine (Flow テスト) + `runTest` で書く

### 受入条件

- **R4.1**: `combine_mergesPasswordsAndPasskeys_byLastUsedDescNullsLast` が design §15.1 fixture で順序を assert
- **R4.2**: `applySearch_hitsPasskeyUserName_caseInsensitive` が `query="ALICE"` で PassKey の `userName="alice"` を hit
- **R4.3**: `applyFilter_signatureMatched_keepsPasskeyRows` / `applyFilter_signatureMissing_keepsPasskeyRows` が PassKey 行素通しを確認
- **R4.6**: `PasskeyDisplayModelTest.dataClassDoesNotExposeSensitiveEntityFields` が reflection で sensitive 列がないこと
- **R4.7**: `computeEmptyKind_*` ケースが 3 状態 (Initial / NoMatch / null) を網羅
- **NFR 2.3**: `onPasskeyClicked_doesNotMutateState_andDoesNotEmitCredentialId` で SafeLogger 出力に raw credentialId / rpId が含まれないこと
- 全テスト pass
- 既存 `CredentialListViewModelTest` の password 単独テストも追従後 pass

### テスト

T-08 自体がテスト追加なので "tests for tests" はなし。Developer は test 実行時の coverage report で R4.1〜R4.11 / NFR 2.x の対応行が touched されていることを確認すること。

### 実行コマンド

```bash
./gradlew :app:testDebugUnitTest --tests "*CredentialListViewModelTest*"
./gradlew :app:testDebugUnitTest --tests "*PasskeyDisplayModelTest*"
./gradlew :app:testDebugUnitTest --tests "*CredentialListSortingTest*"
./gradlew :app:testDebugUnitTest  # 全件 pass 確認
```

### 想定コミットメッセージ

```
test(passkey-list): unit tests for ViewModel / mapper / sort (#101)

Cover the R4.1 / R4.2 / R4.3 / R4.6 / R4.7 acceptance criteria for
Issue #101's new UI plumbing:

CredentialListViewModelTest (extends existing class):
- New cases for password+PassKey combine ordering (R4.1 / design §15.1
  fixtures), PassKey-aware applySearch (R4.2 — case-insensitive
  contains across rpId / rpDisplayName / userName / userDisplayName /
  displayName), signature-chip filter passthrough for PassKey rows
  (R4.3), KindFilter PasswordOnly / PasskeyOnly behaviour (future-
  facing Q-2), computeEmptyKind 3 states (R4.7), passkeyCount
  reflection in UiState, and a SafeLogger spy assertion that
  onPasskeyClicked never logs raw credentialId / rpId / userName
  (NFR 2.3).
- Existing password-only cases keep their logic; only the assertion
  side switches from `List<Credential>` to `List<CredentialListItem>`.

PasskeyDisplayModelTest (new):
- fromEntity copies all 9 non-sensitive fields correctly.
- reflection confirms the data class does NOT declare userHandle /
  encryptedPrivateKey / privateKeyIv / keyAlias / signCount (R4.6 /
  NFR 2.1).
- Null-safety for rpDisplayName / userName / userDisplayName /
  displayName / lastUsedAt.

CredentialListSortingTest (new):
- byLastUsedThenCreatedDesc tested across 7 edge cases (both non-null
  / one null / both null / tiebreaker / stable sort / Long.MAX_VALUE
  boundary).
- mergeAndSort tested with mixed / empty / password-only / passkey-
  only inputs.

Refs #89 #101
```

### 依存タスク

- T-02 / T-03 (テスト対象が存在している前提)

### 対応 EARS / NFR

R4.1 / R4.2 / R4.3 / R4.6 / R4.7 / NFR 2.1 / NFR 2.3 / Q-2

---

## T-09: Instrumentation test (Adapter viewType rendering)

### 目的

Robolectric (sdk 34) で `CredentialListAdapter` の両 viewType レンダリングを検証する (R4.4 / R4.5 / R6.1 / R6.2)。

### 変更ファイル

- 新規: `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListAdapterInstrumentationTest.kt`
  - もしくは Robolectric ベースに統一する場合は `app/src/test/.../CredentialListAdapterRobolectricTest.kt` に配置 (既存 KeyNest の慣習に従う)

### テストケース (design §12.2 / §15.6)

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CredentialListAdapterInstrumentationTest {

    @Test fun getItemViewType_returnsPasswordForPasswordVariant() { ... }
    @Test fun getItemViewType_returnsPasskeyForPasskeyVariant() { ... }

    @Test fun onCreateViewHolder_password_returnsPasswordViewHolder() { ... }
    @Test fun onCreateViewHolder_passkey_returnsPasskeyViewHolder() { ... }

    @Test fun bindPasswordRow_keepsExistingSignatureChipAndOverflow() { ... }
    @Test fun bindPasskeyRow_setsIcPasskey24AndHidesSignatureAndOverflow() { ... }

    @Test fun bindPasskeyRow_textLabel_fallsBackThroughDisplayNameRpDisplayNameRpId() { ... }
    @Test fun bindPasskeyRow_textSubtitle_fallsBackThroughUserDisplayNameUserNameUnknownString() { ... }
    @Test fun bindPasskeyRow_textPackage_isRpId() { ... }

    @Test fun bindPasskeyRow_iconAppContentDescription_isPassKey() { ... }
    @Test fun bindPasswordRow_iconAppContentDescription_isNull() { ... }

    @Test fun diff_areItemsTheSame_passwordVsPasskeyWithSameNumericId_returnsFalse() {
        val pw = CredentialListItem.Password(Credential(id = CredentialId(1), ...))
        val pk = CredentialListItem.Passkey(PasskeyDisplayModel(credentialId = "1", ...))
        // stableId: "pw:1" vs "pk:1" → not same
        assertThat(adapterDiff.areItemsTheSame(pw, pk)).isFalse()
    }
}
```

### 受入条件

- **R4.4**: 両 viewType の inflate / layout で **正しい ViewHolder クラス** (`PasswordViewHolder` / `PasskeyViewHolder`) が返る
- **R4.5**: PassKey 行の `iconApp.drawable` が `R.drawable.ic_passkey_24` の `constantState` を持つ (Robolectric `Shadows.shadowOf` で resource id を取得)
- **R6.1**: PassKey 行 `iconApp.contentDescription` が `"PassKey"` (en string、`InstrumentationRegistry.getInstrumentation().context.getString(R.string.credential_list_passkey_kind_label)`)
- **R6.2**: Password 行 `iconApp.contentDescription` が `null` (XML 既存挙動の維持)
- **R1.5 / D-8 (DIFF 衝突防止)**: Password(id=1) と Passkey(credentialId="1") の `areItemsTheSame` が **false**

### 実行コマンド

```bash
./gradlew :app:testDebugUnitTest --tests "*CredentialListAdapterInstrumentationTest*"
# もし androidTest 配置の場合:
./gradlew :app:connectedDebugAndroidTest --tests "*CredentialListAdapterInstrumentationTest*"
```

> 補足: KeyNest 既存の慣習として instrumentation は Robolectric で代用しているケースが多い (#90 / #99 / #100 同方針)。本タスクも **Robolectric ベース** を採用し `:app:testDebugUnitTest` で実行可能にする。実機 instrumentation test (`connectedDebugAndroidTest`) は CI 制約があり本 Issue では行わない。

### 想定コミットメッセージ

```
test(passkey-list): Adapter Robolectric tests for multi-viewType (#101)

Cover R4.4 / R4.5 / R6.1 / R6.2 / R1.5 with Robolectric (sdk 34):

- getItemViewType returns VIEW_TYPE_PASSWORD / VIEW_TYPE_PASSKEY per
  CredentialListItem variant.
- onCreateViewHolder picks the right internal ViewHolder class.
- PasswordViewHolder.bind preserves chip_signature / btn_overflow
  visibility, matching the Issue #29 baseline.
- PasskeyViewHolder.bind paints ic_passkey_24, hides chip_signature /
  strength_bar / btn_overflow (R1.9 / R5.4), and sets
  iconApp.contentDescription to the localized "PassKey" string (R6.1).
- DiffUtil.areItemsTheSame between a Password(id=1) and a
  Passkey(credentialId="1") returns false — i.e. the "pw:" / "pk:"
  stableId prefix actually prevents the cross-variant collision
  envisioned by D-8.

Robolectric replaces a real instrumentation test here, matching the
project pattern from #90 / #99 / #100.

Refs #89 #101
```

### 依存タスク

- T-04 / T-05 / T-06

### 対応 EARS / NFR

R1.5 / R1.9 / R4.4 / R4.5 / R5.4 / R6.1 / R6.2 / D-8

---

## T-10: 既存テストの追従修正

### 目的

`CredentialListViewModel` / `CredentialListAdapter` / `CredentialListUiState` の signature 変更に伴い、本 PR で touch しないと build red になる既存テストを **assertion / fixture 側だけで** 追従修正する (NFR 3.5 / R4.8 / R4.9)。

### 変更ファイル (候補 — 実際の影響範囲は Developer が検出)

- 変更 (候補): `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListViewModelTest.kt` (T-08 で既に拡張済)
- 変更 (候補): `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListAdapterTest.kt` (もし存在する場合 — `(Credential) -> Unit` → `(CredentialListItem) -> Unit` への callback 型追従、`CredentialListItem.Password(credential)` で wrap)
- 変更 (候補): `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListEmptyStateTest.kt` (もし存在する場合 — `EmptyKind` 判定は変えないが `mainList` 引数型を `List<CredentialListItem>` に追従)
- 変更 (候補): `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/list/RecentlyUsedCarouselAdapterTest.kt` (`recentList: List<Credential>` 型据置のため無変更想定)
- 変更 (候補): Activity 関連 Robolectric テスト (もし存在する場合 — `viewModels { Factory(...) }` の 5 引数追従、`adapter.submitList` 呼び出しの型追従)

### 検出手順

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tee build-errors.log
# 失敗テストを 1 件ずつ修正 (assertion / fixture / type-cast のみ、logic は変えない)
```

### 受入条件

- **R4.8**: 既存 `applyFilterTest` / `applySearchTest` (password 単独) が型変更後 pass
- **R4.9**: 既存 `CredentialListActivity` 関連テスト (Empty state visibility / chip sync / sort popup 等) が pass
- **NFR 3.5**: 期待されるロジック (filter / search / emptyKind 判定) は **変更しない** (test method 本体の logic 部分が diff に含まれないことをレビューでチェック)
- 全 unit test pass

### テスト

T-10 はテスト修正タスク。新規テスト追加なし。

### 実行コマンド

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

### 想定コミットメッセージ

```
test(passkey-list): follow-up existing tests for signature changes (#101)

Adjust existing CredentialListViewModelTest / CredentialListAdapterTest
/ CredentialListEmptyStateTest / Activity Robolectric tests (where
present) so they compile against the new types introduced earlier in
this PR:

- `applyFilter(list: List<CredentialListItem>, filter: CredentialFilter)`
  instead of `applyFilter(list: List<Credential>, ...)`.
- `applySearch(list: List<CredentialListItem>, query: String)`
  instead of `applySearch(list: List<Credential>, ...)`.
- Adapter callbacks now take `CredentialListItem`, so the test
  fixtures wrap each fake Credential in
  `CredentialListItem.Password(credential)`.
- `computeEmptyKind` gains a `kindFilter` parameter (always `All` in
  the existing cases).

Only assertion / fixture / cast sites are touched. No test method
body's *logic* changes — what each existing case asserts about the
password-only behaviour is preserved 1:1 (NFR 3.5 / R4.8 / R4.9).

Refs #89 #101
```

### 依存タスク

- T-02 / T-03 / T-04 / T-07 / T-08 (signature 変更が完了している前提)

### 対応 EARS / NFR

R4.8 / R4.9 / NFR 3.5

---

## T-11: アクセシビリティ + QA checklist + PR description 転記

### 目的

EARS R1〜R6 全件の手動 / 機械的 QA checklist を完了し、PR description に確認事項を転記する (R4.x 全件カバー + R6.x の TalkBack 手動確認)。本タスクは **commit 無し** (chore commit でも可) で、`gradle` 系の最終確認 + 手動 QA + PR description 追記が主目的。

### 確認項目 (PR description チェックボックスとして転記)

| カテゴリ | 確認項目 | 対応 EARS |
|---|---|---|
| 統合表示 | password と PassKey が同じ RecyclerView に混在表示される | R1.1 |
| 統合表示 | 種別アイコン (`ic_passkey_24` / 既存 `iconLoader`) が切り替わる | R1.2 |
| 統合表示 | PassKey 行 1 行目 / 2 行目 / 3 行目の fallback 優先順が正しい | R1.3 |
| 統合表示 | 並び順が `lastUsedAt DESC, createdAt DESC, NULL last` | R1.4 |
| 統合表示 | DiffUtil で password.id と passkey.credentialId が誤一致しない | R1.5 / D-8 |
| 統合表示 | DiffUtil の `areContentsTheSame` が variant 別に正しい fields を比較 | R1.6 |
| 統合表示 | Empty state 表示が password / PassKey 共通 | R1.7 |
| 統合表示 | PassKey 行が vector drawable を直接設定 | R1.8 |
| 統合表示 | PassKey 行で chip_signature / strength_bar / btn_overflow が GONE | R1.9 / R5.4 |
| 検索 | 検索 box で PassKey の rpId / rpDisplayName / userName / userDisplayName / displayName が部分一致 hit | R2.1 / R2.2 |
| 検索 | case-insensitive (lowercase 比較) | R2.5 |
| 検索 | query が blank の場合は全件 (検索適用しない) | R2.6 |
| 検索 | signature chip フィルタ選択時に PassKey 行が消えない | R2.7 |
| 表記 | `credential_list_passkey_kind_label` が en/ja 共に "PassKey" | R3.1 / D-1 |
| 表記 | `credential_list_password_kind_label` が en/ja で適切 | R3.2 |
| 表記 | ja で「passkey」「パスキー」「Passkey」が出現しない | R3.4 / D-1 |
| 表記 | drawable のファイル名が小文字 + アンダースコア | R3.5 |
| テスト | `CredentialListViewModelTest` 拡張 + 新規ケース pass | R4.1 / R4.2 / R4.3 / R4.7 |
| テスト | `CredentialListAdapterInstrumentationTest` (Robolectric) pass | R4.4 / R4.5 |
| テスト | `PasskeyDisplayModelTest` で sensitive 列除外 reflection 検証 pass | R4.6 / NFR 2.1 |
| テスト | `PasskeyDaoTest.listAll` 新規ケース pass | R4.10 |
| テスト | `PasskeyRepositoryTest.listAll` 新規ケース pass | R4.11 |
| テスト | 既存 `applyFilterTest` / `applySearchTest` (password 単独) が追従後 pass | R4.8 |
| テスト | 既存 `CredentialListActivity` 関連テスト pass | R4.9 |
| タップ | PassKey 行タップで Snackbar 表示 + edit 画面起動しない | R5.1 |
| タップ | Snackbar 文言が `credential_list_passkey_tap_v1_message` | R5.2 |
| タップ | PassKey 行 long-click で `promptDelete` が呼ばれない | R5.3 |
| アクセシビリティ | PassKey 行 iconApp の contentDescription が "PassKey" | R6.1 |
| アクセシビリティ | password 行 iconApp の contentDescription が `@null` 維持 | R6.2 |
| アクセシビリティ | TalkBack で PassKey 行と password 行が音声区別可能 (手動 QA) | R6.3 |
| アクセシビリティ | 5 件の新規 string keys が en / ja 両方に存在 | R6.4 |
| パフォーマンス | password 100 + PassKey 100 件規模で stutter なし (60fps、手動 QA) | NFR 1.3 |
| セキュリティ | `PasskeyDisplayModel` が sensitive 列を declare しない | NFR 2.1 |
| セキュリティ | SafeLogger 出力に `credentialId` / `userName` / `rpId` raw が含まれない | NFR 2.3 |
| セキュリティ | `INTERNET` permission を追加していない | NFR 4.1 |
| Schema | `KeyNestDatabase.version` 不変 / migration 追加なし | D-9 |
| 既存挙動 | password 一覧の既存挙動 (Issue #9 系) が完全に回帰なし | NFR 3.5 |

### 手動 QA 手順 (実機 / API 34 emulator)

1. 既存 password を 3〜5 件登録した状態の KeyNest をビルド + インストール
2. #99 経路で PassKey を 2〜3 件登録 (WebAuthn 対応 Web ブラウザ + `webauthn.io` 等)
3. KeyNest を開く → 一覧に password と PassKey が混在表示されることを確認
4. 並び順が `lastUsedAt DESC` (直近に使ったものが上) であることを確認
5. 検索 box に PassKey の rpId / userName を入力 → hit することを確認
6. 検索 box に大文字 (例: "ALICE") を入力 → 小文字 username の password / PassKey が hit
7. signature chip フィルタ (matched / missing) を選択 → PassKey 行が消えないことを確認
8. PassKey 行をタップ → Snackbar 表示 (CredentialEditActivity が起動しないこと)
9. PassKey 行を long-press → 何も起きない (password 用 Delete dialog が出ない)
10. PassKey 行右端に overflow アイコン (3 点リーダー) が表示されないことを確認
11. TalkBack を有効化 → PassKey 行アイコンが「PassKey」と読み上げられることを確認
12. dark mode に切替え → 両種別アイコンが正しく tint されることを確認
13. password を 1 件削除 → 一覧から消えることを確認 (既存挙動)
14. password を 1 件編集 → 一覧上の値が反映されることを確認 (既存挙動)
15. password の Duplicate メニュー → 動作することを確認 (既存挙動)

### PR description に転記する確認事項

design §21 の 6 項目をそのまま PR description に転記する:

1. **Q-1 アイコン素材**: `ic_passkey_24` / `ic_password_24` の vector path を Material Symbols から取り込み、dark mode 視認性を実機確認したか。
2. **Q-2 KindFilter v1 固定**: ViewModel 内部で `KindFilter.All` 固定を KDoc / test で明示し、dead-code 警告を回避したか。
3. **Q-5 sort popup の副次効果**: LabelAsc / PackageAsc 選択時に password 行の並びが変わらないように見える UX 退行を PR description で明示し、後続 Issue 候補としてフラグを立てたか。
4. **Q-9 layout XML 再利用**: PassKey 行で chip_signature / strength_bar / btn_overflow を GONE にする bind ロジックが Issue #29 系の既存テストを破壊しないか。
5. **既存テスト追従**: `CredentialListViewModelTest` の signature 変更追従が assertion のみで logic を変えていないか。
6. **Turbine 依存導入** (該当する場合): `libs.versions.toml` に追加した dependency が CI で resolve 成功したか。

### 完了条件 (Definition of Done)

- T-01〜T-10 の受入条件が満たされている
- 全 unit test pass: `./gradlew :app:testDebugUnitTest`
- assembleDebug 成功: `./gradlew :app:assembleDebug`
- lint 緑: `./gradlew :app:lintDebug` で新規 error なし
- 上記 QA checklist 全件 ✓
- PR description に確認事項 6 件転記済

### 想定コミットメッセージ (chore コミット例 / 任意)

```
chore(passkey-list): finalize QA checklist for #101

No code changes. Last-stage verification:

- All unit + Robolectric tests green (R4.1〜R4.11).
- assembleDebug / lintDebug green.
- Manual QA on API 34 emulator across the 15-step script in
  tasks.md §T-11 (混在表示 / 並び順 / 検索 / フィルタ / タップ /
  TalkBack / dark mode / 既存 password 挙動).
- PR description carries the 6 confirmation items (Q-1 / Q-2 / Q-5 /
  Q-9 / existing test follow-up / Turbine dependency).

Refs #89 #101
```

### 依存タスク

- T-01〜T-10 全完了

### 対応 EARS / NFR

R1.x〜R6.x 全件 / NFR 1.x〜6.x 全件 / D-1〜D-9 全件

---

## タスク → 要件 / 変更ファイル サマリ表

| Task | 主要対応 EARS / NFR / D / Q | 主要変更ファイル | 依存 |
|---|---|---|---|
| T-01 | R4.10 / R4.11 / NFR 3.1 / D-9 / Q-4 | `PasskeyDao.kt` / `PasskeyRepository.kt` / `PasskeyRepositoryImpl.kt` / `PasskeyDaoTest.kt` / `PasskeyRepositoryTest.kt` | なし |
| T-02 | R1.3 / R1.4 / R1.5 / NFR 2.1 / NFR 2.2 / D-1 / D-8 / Q-1 / Q-2 | `CredentialListItem.kt` / `PasskeyDisplayModel.kt` / `KindFilter.kt` / `CredentialListSorting.kt` / `ListPasskeysUseCase.kt` | T-01 |
| T-03 | R1.4 / R1.7 / R2.1 / R2.2 / R2.5 / R2.6 / R2.7 / NFR 1.2 / NFR 2.3 / Q-2 | `CredentialListViewModel.kt` / `CredentialListUiState.kt` | T-01 / T-02 |
| T-04 | R1.1 / R1.2 / R1.5 / R1.6 / R1.8 / R1.9 / R5.3 / R5.4 / R6.1 / R6.2 / D-1 / D-8 / Q-9 | `CredentialListAdapter.kt` | T-02 / T-03 / T-05 / T-06 |
| T-05 | R1.2 / R1.8 / R3.5 / Q-1 / NFR 6.x | `ic_passkey_24.xml` / `ic_password_24.xml` | なし |
| T-06 | R3.1 / R3.2 / R3.4 / R5.2 / R6.1 / R6.4 / D-1 / NFR 5.x / Q-8 | `values/strings.xml` / `values-ja/strings.xml` | なし |
| T-07 | R5.1 / R5.2 / R5.3 / R5.4 / R5.5 / NFR 2.3 / Q-3 / Q-7 | `ServiceLocator.kt` / `CredentialListActivity.kt` | T-02〜T-06 |
| T-08 | R4.1 / R4.2 / R4.3 / R4.6 / R4.7 / NFR 2.1 / NFR 2.3 / Q-2 | `CredentialListViewModelTest.kt` / `PasskeyDisplayModelTest.kt` / `CredentialListSortingTest.kt` | T-02 / T-03 |
| T-09 | R1.5 / R1.9 / R4.4 / R4.5 / R5.4 / R6.1 / R6.2 / D-8 | `CredentialListAdapterInstrumentationTest.kt` | T-04 / T-05 / T-06 |
| T-10 | R4.8 / R4.9 / NFR 3.5 | 既存 test ファイルの signature 追従修正 | T-02〜T-08 |
| T-11 | 全 EARS / NFR / D / Q カバー | (commit 無し or chore のみ) | T-01〜T-10 |

## トレーサビリティマトリクス (EARS → Task)

| EARS / NFR | T-01 | T-02 | T-03 | T-04 | T-05 | T-06 | T-07 | T-08 | T-09 | T-10 | T-11 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| R1.1 | | | | ✓ | | | ✓ | | | | ✓ |
| R1.2 | | | | ✓ | ✓ | | | | | | ✓ |
| R1.3 | | ✓ | | ✓ | | ✓ | | | | | ✓ |
| R1.4 | | ✓ | ✓ | | | | | ✓ | | | ✓ |
| R1.5 | | ✓ | | ✓ | | | | | ✓ | | ✓ |
| R1.6 | | | | ✓ | | | | | | | ✓ |
| R1.7 | | | ✓ | | | | | ✓ | | | ✓ |
| R1.8 | | | | ✓ | ✓ | | | | | | ✓ |
| R1.9 | | | | ✓ | | | | | ✓ | | ✓ |
| R2.1 | | | ✓ | | | | | ✓ | | | ✓ |
| R2.2 | | | ✓ | | | | | ✓ | | | ✓ |
| R2.5 | | | ✓ | | | | | ✓ | | | ✓ |
| R2.6 | | | ✓ | | | | | ✓ | | | ✓ |
| R2.7 | | | ✓ | | | | | ✓ | | | ✓ |
| R3.1 | | | | | | ✓ | | | | | ✓ |
| R3.2 | | | | | | ✓ | | | | | ✓ |
| R3.4 | | | | | | ✓ | | | | | ✓ |
| R3.5 | | | | | ✓ | | | | | | ✓ |
| R4.1 | | | | | | | | ✓ | | | ✓ |
| R4.2 | | | | | | | | ✓ | | | ✓ |
| R4.3 | | | | | | | | ✓ | | | ✓ |
| R4.4 | | | | | | | | | ✓ | | ✓ |
| R4.5 | | | | | | | | | ✓ | | ✓ |
| R4.6 | | ✓ | | | | | | ✓ | | | ✓ |
| R4.7 | | | ✓ | | | | | ✓ | | | ✓ |
| R4.8 | | | | | | | | | | ✓ | ✓ |
| R4.9 | | | | | | | | | | ✓ | ✓ |
| R4.10 | ✓ | | | | | | | | | | ✓ |
| R4.11 | ✓ | | | | | | | | | | ✓ |
| R5.1 | | | | | | | ✓ | | | | ✓ |
| R5.2 | | | | | | ✓ | ✓ | | | | ✓ |
| R5.3 | | | | ✓ | | | ✓ | | | | ✓ |
| R5.4 | | | | ✓ | | | ✓ | | ✓ | | ✓ |
| R5.5 | | | | | | | ✓ | | | | ✓ |
| R6.1 | | | | ✓ | | ✓ | | | ✓ | | ✓ |
| R6.2 | | | | ✓ | | | | | ✓ | | ✓ |
| R6.3 | | | | ✓ | | ✓ | | | | | ✓ |
| R6.4 | | | | | | ✓ | | | | | ✓ |
| NFR 1.2 | | | ✓ | | | | | | | | ✓ |
| NFR 1.3 | | | | ✓ | | | | | | | ✓ |
| NFR 2.1 | | ✓ | | | | | | ✓ | | | ✓ |
| NFR 2.2 | | ✓ | ✓ | | | | | | | | ✓ |
| NFR 2.3 | | | ✓ | | | | ✓ | ✓ | | | ✓ |
| NFR 3.1 | ✓ | | | | | | | | | | ✓ |
| NFR 3.5 | | | | | | | | | | ✓ | ✓ |
| NFR 4.1 | | | | | | | | | | | ✓ |
| NFR 5.2 | | | | | | ✓ | | | | | ✓ |
| D-1 | | ✓ | | ✓ | | ✓ | | | | | ✓ |
| D-8 | | ✓ | | ✓ | | | | | ✓ | | ✓ |
| D-9 | ✓ | | | | | | | | | | ✓ |
| Q-1 | | ✓ | | | ✓ | | | | | | ✓ |
| Q-2 | | ✓ | ✓ | | | | | ✓ | | | ✓ |
| Q-3 | | | | | | | ✓ | | | | ✓ |
| Q-4 | ✓ | ✓ | | | | | | | | | ✓ |
| Q-7 | | | | | | | ✓ | | | | ✓ |
| Q-8 | | | | | | ✓ | | | | | ✓ |
| Q-9 | | | | ✓ | | | | | | | ✓ |

## 既存テスト非破壊チェックリスト (PR レビュー時)

PR の最終確認時、レビュアーは以下が全件 pass していることを確認する:

- [ ] `Migration_4_5_Test` (#91) — DB schema 不変
- [ ] `PasskeyDaoTest` (#91) の既存 8 メソッドテスト
- [ ] `PasskeyRepositoryTest` (#99/#100) の既存 4+3 メソッドテスト
- [ ] `KeyNestCredentialProviderServiceTest` (#90/#99/#100) — Service / credentialprovider/ に触らず
- [ ] `PasskeyCreatorTest` / `PasskeyAssertionTest` / `AuthenticatorDataBuilderTest` / `AttestationObjectBuilderTest` / `CoseKeyEncoderTest` / `CborWriterTest` / `KeynestAaguidTest` / `CreateEntryBuilderTest` (#99/#100) — credentialprovider/ に触らず
- [ ] `InternetPermissionAbsenceTest` — INTERNET permission を追加していない
- [ ] `OnBackInvokedCallbackEnabledTest` — touch なし
- [ ] `KeyNestAutofillService` 関連テスト — autofill 経路に触らず
- [ ] `CredentialListViewModelTest` (#9 系既存) — signature 追従後の logic 変更なし
- [ ] `CredentialListActivity` Robolectric テスト (Empty state / chip sync / sort popup) — callback 型追従のみ
