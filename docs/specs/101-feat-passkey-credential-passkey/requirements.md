# Requirements Document

## 冒頭メタ

| 項目 | 値 |
|---|---|
| **Issue** | #101 feat(passkey): 既存 credential 一覧画面に PassKey 表示を統合 |
| **Parent (umbrella)** | #89 feat(passkey): Android Credential Manager 経由の passkey プロバイダ対応 |
| **Phase** | **Phase 4** (umbrella #89 サブ分割案 5「一覧 UI 統合」に対応) |
| **Depends on** | #91 (Room migration / `PasskeyEntity` / `PasskeyDao` / `PasskeyRepository`) — merged |
| **(参考) 並列 / 先行 Phase** | #90 (Service 骨組み、merged) / #99 (登録セレモニー、merged) / #100 (認証セレモニー、merged) |
| **作業ブランチ** | `claude/issue-101-design-feat-passkey-credential-passkey` |
| **PR base** | `develop` (リポジトリ既定。最終的に `main` に集約) |
| **表記ポリシー** | umbrella #89 確定事項 (確認事項 3) に従い、UI ラベル / KDoc / コメント / ログメッセージで PassKey 機能に言及する箇所は **「PassKey」** で統一 (「パスキー」「passkey」「Passkey」は禁止)。テーブル名 / カラム名は既存 (`passkeys` / `passkeyDao` 等) を踏襲 |

## 概要 / Goal / Non-Goal

### 概要

KeyNest はこれまで password ベースの credential のみを `CredentialListActivity` で
一覧表示してきた。umbrella Issue #89 で確定した「Android Credential Manager API
経由の PassKey プロバイダ対応」は、Phase 1 (#90 Service 骨組み / #91 Room 永続化) と
Phase 2 (#99 登録セレモニー) / Phase 3 (#100 認証セレモニー) が確定済みで、KeyNest
は OS から PassKey の保管 / 認証先として既に機能している。一方で、保管された
PassKey は現状 **KeyNest 内のどの画面からも参照できない** (`PasskeyDao` /
`PasskeyRepository` には get / find API が揃っているが、それを呼ぶ UI が存在しない)。

本 Issue (#101 = umbrella #89 分割案 5「一覧 UI 統合」 = **Phase 4**) は、既存
`CredentialListActivity` (password 一覧) に **PassKey を統合表示** する。具体的には、

- `CredentialListViewModel` を拡張し、password Flow (`ListCredentialsUseCase`) と
  PassKey Flow (新規 `ListPasskeysUseCase` 相当、または `PasskeyRepository`
  直叩き) を 1 つの **統合 list flow** に合成する。
- `CredentialListAdapter` の item type を password 行と PassKey 行で出し分け、
  異なるアイコン / ラベルで視覚的に区別する。
- 既存の検索 box (`inputSearch`) で password の `label` / `username` /
  `packageName` に加え、PassKey の `rpId` / `userDisplayName` / `userName` を
  **部分一致** でフィルタする。
- 並び順は password と PassKey 共通で **`lastUsedAt DESC, createdAt DESC`** (NULL
  末尾) に統一し、種別を問わず「最近使ったものが上」UX を実現する。
- 検索 / フィルタ / 表示更新が main thread を blocking しない (既存方針継承)。

までを 1 PR の到達点とする。

この Issue 単体では PassKey の **個別管理画面** (rename / delete UI = #89 分割案 6)、
**種別フィルタ chip** (PassKey のみ / password のみ表示)、**設定画面** (#89 分割案 7)、
**README / Privacy / Support docs 更新** (#89 分割案 8) には到達しない (Out of Scope)。

なお umbrella #89 のとおり、UI / KDoc / コメント / ログメッセージに登場する
日本語 / 英語表記は **「PassKey」** に統一する。

### Goal

- `CredentialListUiState.mainList` を password と PassKey の **両方を含む統合
  list** に変える (型は新規 sealed class `CredentialListItem` を導入、後述
  「データモデル」参照)。
- `CredentialListAdapter` を `ListAdapter<CredentialListItem, ...>` に変え、
  password 行と PassKey 行を **viewType** で出し分ける。共通カード (44dp アイコン
  タイル + メタ 3 行 + 右端 overflow) のレイアウトは踏襲しつつ、種別アイコン
  (`ic_passkey_*` / `ic_password_*`) と種別ラベルを差し替える。
- `CredentialListViewModel` を拡張し、`PasskeyRepository` / 新規
  `ListPasskeysUseCase` 経由で全 PassKey を Flow として取得する。password Flow
  (`ListCredentialsUseCase`) と `combine` し、後段の **検索 (`query`)** / **フィルタ
  (`filter`)** / 並び順マージを 1 つの pipeline に統合する。
- 検索のスコープを次のとおりに拡張する (`applySearch` を更新):
  - password 行: 既存の `label` / `username` / `packageName` (case-insensitive
    contains)
  - PassKey 行: `rpId` / `rpDisplayName` / `userName` / `userDisplayName` /
    KeyNest 側の `displayName` (case-insensitive contains)
- 並び順は password / PassKey 共通で **`lastUsedAt DESC, createdAt DESC`** とし、
  `lastUsedAt = NULL` は末尾 (`(lastUsedAt IS NULL) ASC` 相当を Kotlin 側で再現)。
- 既存の sort popup (`CredentialSortOrder.UpdatedAtDesc` / `LabelAsc` /
  `PackageAsc`) は password 行のメタを軸にした並び替えなので **password 列にのみ
  作用** し、PassKey 行は **常に `lastUsedAt DESC, createdAt DESC`** で並ぶ
  (Out of Scope: PassKey 用 sort オプション追加)。最終的な統合リストの並びは
  「password ブロック (選択 sort 順) + PassKey ブロック (固定 sort 順)」では
  なく、**両種別を mainList 上で混在させた単一の `lastUsedAt DESC, createdAt DESC`**
  とする (本 Issue Goal の Requirement 1.4)。sort popup と統合 list の整合は
  未決事項 (後段「未決事項」参照)。
- DiffUtil の `areItemsTheSame` で password.id と passkey.credentialId が同値に
  なる事故を防ぐため、`CredentialListItem` を sealed class とし、Diff 比較を
  **(variant, id) ペア** で行う (後段「データモデル」参照)。
- 検索 / フィルタ / 表示更新が main thread を blocking しないよう、PassKey 取得は
  IO dispatcher (`PasskeyRepository` の `suspend` API を `viewModelScope` /
  `flow { ... }.flowOn(Dispatchers.IO)` で吸収) で実行する (#91 / #99 / #100 と
  同方針)。
- 既存テスト (`CredentialListViewModelTest` / `CredentialListAdapterTest` 相当が
  ある場合 / `applySearch` / `applyFilter` 単体テスト) を破壊せず、password 単独
  振る舞いの回帰がないことを保証する。
- 上記をカバーする `CredentialListViewModelTest` 拡張 + 新規
  `CredentialListAdapterInstrumentationTest` (両 viewType の描画) +
  `applySearchPasskeyTest` (PassKey の `userName` 部分一致がヒットすることの検証)
  を追加する。
- 種別アイコンは Material Design の `key` 系 / `fingerprint` 系シンボルを
  vector drawable 化したもの、または既存 `ic_kn_mark.xml` 等の流用 / 微調整で
  賄う方針 (新規アセット発番か既存流用かは未決事項として後段に明示、推奨は
  「既存 `ic_fingerprint_24.xml` を PassKey 用、新規 `ic_password_key_24.xml`
  系 vector 1 つを password 用に発番」)。

### Non-Goal (Out of Scope)

- **PassKey の個別管理画面** (rename / delete UI) — umbrella #89 分割案 6 = 別 Issue。
  本 Issue では PassKey item をタップした際の遷移先は「v1 では何もしない (no-op
  + 簡易 Snackbar 表示)」とする (Requirement 5 / 未決事項参照)。
- **種別フィルタ UI** (PassKey のみ / password のみ / 全件 の 3 値切替 chip)。
  Issue #101 本文「Out of Scope: 種別フィルタは v1 では未対応」を踏襲。本 Issue
  では UI 上 chip を追加しないが、後段「未決事項」で「State 構造を 3 値 enum で
  保持し、将来追加しやすくする」推奨案を提示する。
- **PassKey 単位での sort オプション追加** (`CredentialSortOrder` への新メンバ
  追加)。本 Issue では PassKey は常に `lastUsedAt DESC, createdAt DESC` 固定。
- **DB schema 変更** / **migration 追加**。#91 で `passkeys` テーブル (v5) が
  既に確定済みで、本 Issue では schema を変更しない (明示: **DB 変更なし**)。
- **新規 PassKey 用テーブル / カラムの追加** (例: per-PassKey "favorite" フラグ等)。
- **`CredentialProviderService` 自体の変更**。本 Issue は UI 層 (`ui/list/`) と
  対応する domain layer (use case) / 既存 `PasskeyRepository` 公開 IF の **読み出し
  パス活用のみ** で完結する。Service / Activity / Authenticator は触らない。
- **password と PassKey の「同一 RP」紐付け表示** (例: 同一アプリの password と
  PassKey をグルーピングして表示)。本 Issue では各 entity を flat に並べる。
- **OS 設定への導線 / 「KeyNest を PassKey プロバイダとして登録」状態表示** —
  umbrella #89 分割案 7 = 別 Issue。
- **検索キーワードの分割** (`AND` / `OR` / 区切り token)。本 Issue では既存
  `applySearch` の **substring contains** を踏襲し、ユーザー入力全体を 1 つの
  needle として扱う。
- **検索のソート / ハイライト / 表示数制限**。本 Issue では UI 側装飾は追加しない。
- **アイコン素材の design シート確定 / ブランド整合確認** (design/spec.md の更新)。
  design 側のアセット確定は未決事項として残し、本 Issue 範囲では「最低限の
  暫定 vector drawable を 2 つ用意」する。

## 関連 Issue / PR

- **Parent (umbrella)**: #89 feat(passkey): Android Credential Manager 経由の
  passkey プロバイダ対応 (umbrella)。
- **Depends on**:
  - #91 feat(passkey): Room migration + `PasskeyEntity` / `PasskeyDao` /
    `PasskeyRepository`。本 Issue は #91 で確定した `PasskeyDao` /
    `PasskeyRepository` の **読み出し API** を直接 / 間接に呼び出す。
- **先行確立済み (Phase 2/3、本 Issue では非依存だが用語整合のため参照)**:
  - #99 feat(passkey): 登録セレモニー (`onBeginCreateCredentialRequest`) — 表記
    ポリシー「PassKey」/ Keystore alias 命名 `keynest_passkey_<credentialId>` /
    AAGUID `2a56cf86-8332-4829-9f2a-e9a4adbc7abe` を確立済み。本 Issue UI 文言は
    `passkey_*` string key 命名規約に揃える (#99 の `passkey_create_*` 系を踏襲)。
  - #100 feat(passkey): 認証セレモニー (`onBeginGetCredentialRequest`)。本 Issue で
    追加する PassKey 行をタップした際の挙動 (v1 では no-op) は #100 と無関係に
    動作する (Credential Manager 経由の認証フローは本 Issue UI とは独立)。
- **後続予定 (Phase 5 以降)**:
  - umbrella #89 分割案 6: PassKey 単位の rename / 削除 UI。本 Issue で
    `CredentialListItem.Passkey` を導入することで、後続 Issue は「item タップ
    → 個別管理画面」の遷移を本 Issue が用意した接合点に追加するだけで済む。
  - umbrella #89 分割案 7: 設定画面 / OS 設定導線。
  - umbrella #89 分割案 8: README / Privacy / Support docs 更新。
  - 種別フィルタ chip 追加 Issue (本 Issue で State 構造を確立)。

## 既存実装の現状把握 (read-only)

> 本 Issue は新規実装が主だが、既存 `ui/list/` パッケージは Issue #9 / #29 /
> #43 / #46 / #51 / #67 で繰り返し更新されており、変更時の互換性確保が重要。
> 本セクションは現状コードの **責務 / 公開 IF / Flow 構造** を要約し、本 Issue で
> どこを差し替えるかの判断材料とする。実装ファイルは触らずに読むのみ。

### `CredentialListActivity.kt` (現状)

- `AppCompatActivity`。`ViewBinding` で `CredentialListActivityBinding` を保持。
- `viewModels { CredentialListViewModel.Factory(...) }` で ViewModel を取得。
  4 つの UseCase (`listCredentialsUseCase` / `observeRecentlyUsedUseCase` /
  `duplicateCredentialUseCase` / `deleteCredentialUseCase`) を `ServiceLocator`
  から渡している。
- 構成要素 (`setUp*` 群):
  - `setUpMainList()`: `CredentialListAdapter` を生成し
    `LinearLayoutManager` をセット。click / longClick / overflow click /
    iconLoader を渡す。
  - `setUpRecentCarousel()`: `RecentlyUsedCarouselAdapter` を別途生成、横スクロール
    Recycler。
  - `setUpSearch()`: `binding.inputSearch.addTextChangedListener {
    viewModel.onQueryChanged(s?.toString().orEmpty()) }`。
  - `setUpFilters()`: chip group (`chip_signature_matched` /
    `chip_signature_missing`) の `OnCheckedStateChangeListener` で
    `viewModel.onFilterChanged(CredentialFilter)` を発火。
  - `setUpSort()`: `btn_sort` から `PopupMenu` を出し、3 sort order
    (`UpdatedAtDesc` / `LabelAsc` / `PackageAsc`) を選択。
  - `setUpFab()` / `setUpEmptyStateCta()`: 新規追加導線。
- `observeUiState()`: `repeatOnLifecycle(STARTED)` 内で
  `viewModel.uiState.collect { renderState(it) }`。
- `renderState(state: CredentialListUiState)`:
  - `adapter.submitList(state.mainList)` (現状は `List<Credential>`)
  - `recentAdapter.submitList(state.recentList)`
  - `renderRecentVisibility(state.recentList.isEmpty())`
  - `renderEmptyView(state)` (Empty state 表示分岐)
  - `syncChipsTo(state.filter)`
- click 経路:
  - row click: `startEdit(credential) → CredentialEditActivity` (password 編集)。
  - row long-click: `promptDelete(credential)` (delete dialog)。
  - overflow click: `showRowOverflowMenu(credential, anchor)` (Duplicate のみ)。

### `CredentialListAdapter.kt` (現状)

- `ListAdapter<Credential, ViewHolder>` (DIFF は `Credential.id` 一致 +
  `packageName / username / label / updatedAt / signatureSha256` の contents
  比較)。
- `ViewHolder.bind(...)` で次を行う:
  - `text_label` ← `item.label`
  - `text_subtitle` ← `item.username`
  - `text_package` ← `item.packageName`
  - `chip_signature` ← `item.signatureSha256` の有無で OK / missing 表示切替
  - `strength_bar` ← `null` (常に GONE)
  - `iconLoader.loadInto(binding.iconApp, item.packageName)` (PackageManager
    解決の app icon、または fallback の InitialLetterDrawable)
  - row click / longClick / overflow click を delegate
- `onViewRecycled` で `iconLoader.cancel(holder.iconAppView)` (#43 race
  対策)。
- 1 viewType のみ (`onCreateViewHolder` は固定 `CredentialListItemBinding` を
  inflate)。

### `CredentialListViewModel.kt` (現状)

- `ViewModel`、`Factory` 経由生成。
- 3 つの input `MutableStateFlow`: `query: String` / `filter:
  CredentialFilter` / `sort: CredentialSortOrder`。
- 主要 Flow パイプライン:
  ```
  sortedListFlow = sort.flatMapLatest { listUseCase(it) }
  mainListFlow = combine(query, filter, sortedListFlow) { q, f, list ->
      val filtered = applyFilter(list, f)
      if (q.isBlank()) filtered else applySearch(filtered, q)
  }
  uiState = combine(query, filter, sort, mainListFlow, recentUseCase()) {
      ... -> CredentialListUiState(...)
  }.stateIn(...)
  ```
- 公開メソッド: `onQueryChanged(String)` / `onFilterChanged(CredentialFilter)` /
  `onSortChanged(CredentialSortOrder)` / `onDuplicate(CredentialId)` /
  `delete(CredentialId)` / `duplicateResult: SharedFlow<DuplicateOutcome>`。
- internal 用テスト helper (companion):
  - `applyFilter(list, filter)` : `signatureSha256` の有無で fold
  - `applySearch(list, query)`:
    - `needle = query.trim().lowercase()`
    - `c.label.lowercase().contains(needle) || c.username... || c.packageName...`
    - **case-insensitive substring contains** (空白区切り分割なし、トークン化
      なし)。
  - `computeEmptyKind(mainList, query, filter)`:
    - `mainList.isNotEmpty()` → null
    - 無入力 → `EmptyKind.Initial` / それ以外 → `EmptyKind.NoMatch`

### `CredentialListUiState.kt` (現状)

- `data class CredentialListUiState(query, filter, sort, mainList:
  List<Credential>, recentList: List<Credential>, emptyKind: EmptyKind?)`
- `EMPTY` 定数: query="", filter=None, sort=UpdatedAtDesc, mainList=emptyList,
  recentList=emptyList, emptyKind=Initial。
- 不変条件: `emptyKind != null ⇔ mainList.isEmpty()`。

### `PasskeyEntity` (現状 / #91 で確定済 / 本 Issue では Read のみ)

- 14 カラム (`credentialId` PK, `rpId`, `rpDisplayName`, `userHandle` BLOB,
  `userName`, `userDisplayName`, `isDiscoverable`, `encryptedPrivateKey`,
  `privateKeyIv`, `keyAlias`, `signCount`, `displayName`, `createdAt`,
  `lastUsedAt`)。
- index: `Index(value=["rpId"])`, `Index(value=["rpId","userHandle"],
  unique=true)`。
- `toString()` で `userHandle` / `encryptedPrivateKey` / `privateKeyIv` は
  size 表記のみ (NFR 1.3 継承)。

### `PasskeyDao` (現状 / #91 で確定済 / 本 Issue では Read のみ)

- 全 `suspend` API:
  - `insert(entity)` / `update(entity)` / `delete(credentialId)`
  - `findByCredentialId(credentialId): PasskeyEntity?`
  - `findByRpIdAndUserHandle(rpId, userHandle): PasskeyEntity?`
  - `listDiscoverableByRpId(rpId): List<PasskeyEntity>`
  - `listAllByRpId(rpId): List<PasskeyEntity>`
  - `incrementSignCount(credentialId, timestamp)`
- すべての list クエリは `ORDER BY (lastUsedAt IS NULL) ASC, lastUsedAt DESC,
  createdAt DESC`。
- **本 Issue が必要とする「全 PassKey を rpId に依存せず取り出す」 API は
  現状未提供** (Flow 系も未提供)。

### `PasskeyRepository` / `PasskeyRepositoryImpl` (現状 / #99 で inline 追加)

- `interface PasskeyRepository` 公開 API:
  - `suspend fun save(request: SavePasskeyRequest)`
  - `suspend fun findByCredentialId(credentialId): PasskeyEntity?`
  - `suspend fun findByRpIdAndUserHandle(rpId, userHandle): PasskeyEntity?`
  - `suspend fun delete(credentialId): DeletePasskeyResult`
- **本 Issue が必要とする「KeyNest が保管する全 PassKey の list / Flow」
  API は現状未提供**。
- `PasskeyRepositoryImpl` は `PasskeyDao` を委譲。Keystore wrapping key alias
  は `keynest_passkey_<credentialId>` (#99 で確定、#91 の `passkey_<credentialId>`
  とは異なる)。本 Issue では encrypted blob を一切触らず、metadata のみ参照。

### 既存 layout / drawable / strings の状況

- `app/src/main/res/layout/credential_list_item.xml`: `MaterialCardView` 内に
  44dp icon tile + label / subtitle / package text 3 行 + signature chip +
  StrengthBar + overflow ImageButton。PassKey 行用の追加情報スロットなし。
- `app/src/main/res/drawable/`: PassKey / password 用に特化したアイコン
  (`ic_passkey_*` / `ic_password_*`) は **未追加**。汎用候補として
  `ic_key_24.xml` / `ic_fingerprint_24.xml` / `ic_kn_mark.xml` 等は既存。
- `app/src/main/res/values/strings.xml` / `values-ja/strings.xml`: PassKey 系の
  string key は `passkey_create_*` / `passkey_biometric_prompt_*` のみで、一覧
  画面用の `credential_list_passkey_label` 等は未定義。

## ユーザーストーリー

- As a エンドユーザー (既存 password 保管メイン利用), I want KeyNest を開いた
  ときに自分が登録した password と PassKey が **1 つのリストに混在して表示**
  され、種別 (password / PassKey) がアイコンで一目で区別できること, so that
  鍵管理画面が複数に分裂せず、保管している credential 群の全体像を 1 画面で
  把握できる。
- As a エンドユーザー (PassKey 利用拡大中), I want PassKey を登録した後 KeyNest を
  開けば、登録した PassKey が **同じ一覧画面に出現する** こと, so that 「PassKey
  を登録したつもりだが KeyNest に保存されているのか確認できない」という不安が
  起きない。
- As a エンドユーザー, I want 検索 box に RP のドメイン (`example.com`) や
  username を入力すると、password も PassKey も **横断で検索される** こと, so
  that 種別を意識せずに目的の credential を 1 度の検索で見つけられる。
- As a エンドユーザー, I want 並び順が **「最近使ったものが上」** で password と
  PassKey が混在しても破綻しないこと, so that 直前に使った credential が常に
  先頭付近に出る。
- As a 後続「個別管理 UI」(#89 分割案 6) の実装担当, I want 本 Issue で
  `CredentialListItem.Passkey` (sealed class variant) と tap 接合点 (現時点では
  no-op) が確立されていること, so that 後続 Issue で「item タップ → PassKey
  管理画面」を追加するだけで rename / delete UI に到達できる。
- As a メンテナ, I want password 一覧の既存挙動 (Issue #9 系の検索 / フィルタ /
  recently used carousel / Empty state / row click) が PassKey 統合後も完全に
  回帰なく動くこと, so that 既存ユーザーが PassKey 統合をきっかけに既存資産を
  操作できなくなる事故が起きない。

## 既決事項テーブル

> umbrella #89 / 先行 Phase で人間 / claude triage により確定済みの方針のうち、
> 本 Issue の Requirement に直接効くものを引用する。

| ID | 確定内容 | 出所 | 本 Issue での適用箇所 |
|---|---|---|---|
| D-1 | 表記は **「PassKey」** で統一 (UI / KDoc / コメント / ログ。「パスキー」「passkey」「Passkey」は禁止) | umbrella #89 確認事項 3 (人間確定) / #99 NFR 6.1 / #100 §「概要」 | 新規 string resource (`credential_list_passkey_label` 等) / `CredentialListAdapter` の KDoc / アイコン a11y contentDescription / 統合 list の variant 命名 (`CredentialListItem.Passkey`) |
| D-2 | KeyNest authenticator の AAGUID = `2a56cf86-8332-4829-9f2a-e9a4adbc7abe` | umbrella #89 確認事項 1 (人間確定) | 本 Issue では AAGUID を **UI に出さない**。一覧 item には PassKey の metadata (`rpId` / `displayName`) のみ表示する。AAGUID 表示は #89 分割案 6 (個別管理 UI) で検討 |
| D-3 | minSdk = 26 維持。Credential Manager Provider は API 34+ ゲーティング | umbrella #89 / #90 / #99 / #100 | 本 Issue は **UI 層** であり Credential Manager API を直接呼ばない。`PasskeyRepository` 経由のメタデータ参照は API 26 でも動く (Room の通常クエリ)。`@RequiresApi(34)` ゲーティングは不要 |
| D-4 | PassKey 用 Keystore alias 命名 = `keynest_passkey_<credentialId>` | #99 決定 2 (人間確定 「1,2,3 全て推奨案で OK」) | 本 Issue は復号 / 署名を行わない (UI のみ) ため alias を **参照しない**。`PasskeyEntity.keyAlias` 列を UI に出すこともしない |
| D-5 | エクスポート禁止ポリシー (PassKey / password 共通) | umbrella #89 確認事項 7 (人間確定) | 本 Issue は read 側 (一覧表示) のみ。export 動線は追加しない |
| D-6 | password / PassKey の **保管は同一ストレージに統合** (umbrella 設計前提) | umbrella #89 「設計の前提」 | 本 Issue で一覧 UI を「同一画面に統合」する流れは設計前提から自然に導かれる (DB は別テーブルだが UI は 1 画面) |
| D-7 | discoverable / non-discoverable 両対応 | umbrella #89 確認事項 4 (人間確定) | 本 Issue では `isDiscoverable` の値を問わず **全 PassKey** を一覧表示する (`listAllByRpId` 相当ではなく「KeyNest 全体の PassKey」を取り出す API が新規必要 — 後段「データモデル」参照) |
| D-8 | password 行の DiffUtil は `Credential.id` (`CredentialId` value class = Long) で識別、PassKey 行は `credentialId: String` で識別 | #91 / 既存 `CredentialListAdapter.DIFF` | 同値衝突回避のため `CredentialListItem` を sealed class とし、`areItemsTheSame` は **variant 種別 + 内部 id** ペアで判定する (Requirement 1.5) |
| D-9 | DB schema 変更なし (本 Issue) | #91 で `passkeys` v5 確定済 + 本 Issue は read 側のみ | `KeyNestDatabase.version` は **据置**。新規 migration / `app/schemas/.../*.json` の発番なし |

## スコープ

### 新規追加するファイル

| 対象 | 概要 |
|---|---|
| `app/src/main/java/.../ui/list/CredentialListItem.kt` | `sealed interface CredentialListItem` (本 Issue で導入)。`Password(credential: Credential)` / `Passkey(passkey: PasskeyDisplayModel)` の 2 variant。DiffUtil の `areItemsTheSame` 比較に使う `stableId` を持つ |
| `app/src/main/java/.../ui/list/PasskeyDisplayModel.kt` | UI 用 read-only model。`credentialId: String` / `rpId` / `rpDisplayName` / `userName` / `userDisplayName` / `displayName` / `isDiscoverable` / `createdAt` / `lastUsedAt` のみを保持 (ciphertext / userHandle / encryptedPrivateKey / privateKeyIv / keyAlias / signCount は **UI 層に持ち込まない**、NFR 2 と整合) |
| `app/src/main/java/.../domain/usecase/ListPasskeysUseCase.kt` | 新規 UseCase。KeyNest が保管する全 PassKey の Flow (`Flow<List<PasskeyDisplayModel>>`) を提供。実装は `PasskeyRepository` の新メソッド `listAll(): Flow<List<PasskeyEntity>>` (本 Issue で公開 IF に追加) を delegate し、Entity → DisplayModel 変換 (`map { it.map(PasskeyEntity::toDisplayModel) }`) を内包 |
| `app/src/main/java/.../ui/list/CredentialListPasskeyItemBinding` 用 layout `app/src/main/res/layout/credential_list_passkey_item.xml` | PassKey 行の専用 layout (44dp アイコンタイル + title 行 + subtitle + tertiary 行 + 種別ラベルチップ)。最終的に `credential_list_item.xml` と同等の共通レイアウトを使い回せるなら 1 file に統合する案も有 (最終形は design.md) |
| `app/src/main/res/drawable/ic_passkey_24.xml` | PassKey 行用アイコン (Material Symbols `passkey` / `fingerprint` outlined / `vpn_key_24` 系から選定。最終形は design.md / 未決事項 1) |
| `app/src/main/res/drawable/ic_password_24.xml` (任意) | password 行用アイコン (Material Symbols `password` / `key` 系)。既存 `ic_key_24.xml` を流用するなら不要 |
| `app/src/main/res/drawable/kn_kind_chip_bg_passkey.xml` (任意) | PassKey 種別ラベル chip の背景 (kn_accent_500 系)。`kn_signature_chip_bg_*` と同方式 |
| `app/src/test/.../ui/list/CredentialListViewModelTest.kt` (拡張 or 新規) | password Flow + PassKey Flow 統合のテスト。`applySearch` の PassKey 部分一致テスト |
| `app/src/test/.../ui/list/PasskeyDisplayModelTest.kt` | Entity → DisplayModel 変換のテスト (sensitive 列が漏れないこと) |
| `app/src/androidTest/.../ui/list/CredentialListAdapterInstrumentationTest.kt` | 両 viewType の layout inflate / icon 表示 / contentDescription を検証 |

### 変更する既存ファイル

| 対象 | 変更内容 |
|---|---|
| `app/src/main/java/.../ui/list/CredentialListAdapter.kt` | `ListAdapter<Credential, ViewHolder>` → `ListAdapter<CredentialListItem, ViewHolder>` に変更。viewType を 2 種 (`VIEW_TYPE_PASSWORD = 0` / `VIEW_TYPE_PASSKEY = 1`) に分割し、`getItemViewType` で variant 判定。`onCreateViewHolder` は viewType ごとに inflate を分岐 (`PasswordViewHolder` / `PasskeyViewHolder` を内部クラスとして定義)。既存 password 行の bind ロジックは `PasswordViewHolder.bind` にそのまま移植。PassKey 行は **専用 bind** (種別アイコン / rpId / userName / displayName) を実装。DIFF は `CredentialListItem.stableId` ベースで比較 |
| `app/src/main/java/.../ui/list/CredentialListUiState.kt` | `mainList: List<Credential>` → `mainList: List<CredentialListItem>` に型変更。`recentList` は当面 `List<Credential>` (password 由来のみ。Recently used carousel への PassKey 統合は Out of Scope) |
| `app/src/main/java/.../ui/list/CredentialListViewModel.kt` | コンストラクタに `ListPasskeysUseCase` を追加。`Factory` 側にも対応する引数を追加。`mainListFlow` の合成を「password Flow (`sortedListFlow`) + PassKey Flow (`listPasskeysUseCase()`)」の `combine` に拡張し、結果を `mergeAndSort(passwords, passkeys)` で 1 つの `List<CredentialListItem>` にマージ。`applyFilter` / `applySearch` は variant ごとに分岐 (sealed `when`) する形に拡張 |
| `app/src/main/java/.../ui/list/CredentialListActivity.kt` | adapter の型引数変更に伴い、`onItemClick` / `onItemLongClick` / `onOverflowClick` を **password 専用 callback** から **`CredentialListItem` を引数に取る関数** に変更。password variant では既存挙動を維持、PassKey variant では本 Issue v1 では `Snackbar` で「PassKey は KeyNest 内で管理されています (個別管理は後続 Issue)」相当の暫定 message を出す (Requirement 5)。`ServiceLocator` から `listPasskeysUseCase` 参照を追加 |
| `app/src/main/java/.../di/ServiceLocator.kt` | `listPasskeysUseCase: ListPasskeysUseCase` を提供 (`PasskeyRepository` の現有 instance を delegate) |
| `app/src/main/java/.../domain/repository/PasskeyRepository.kt` | **`listAll(): Flow<List<PasskeyEntity>>`** を公開 IF に追加 (戻り型は Flow。本 Issue では一覧 UI で Flow 観測する必要があるため `suspend fun listAll(): List<PasskeyEntity>` ではなく `fun listAll(): Flow<List<PasskeyEntity>>` を採用)。既存メソッド (`save` / `findByCredentialId` / `findByRpIdAndUserHandle` / `delete`) のシグネチャは変更しない (backward compatible) |
| `app/src/main/java/.../data/repository/PasskeyRepositoryImpl.kt` | `listAll(): Flow<List<PasskeyEntity>>` を実装。`PasskeyDao` に対応する `@Query("SELECT * FROM passkeys ORDER BY (lastUsedAt IS NULL) ASC, lastUsedAt DESC, createdAt DESC") fun listAll(): Flow<List<PasskeyEntity>>` を追加。Flow なので Room が自動で変更通知 |
| `app/src/main/java/.../data/dao/PasskeyDao.kt` | **`listAll(): Flow<List<PasskeyEntity>>`** を新規追加 (`@Query` + Flow)。既存 8 メソッドは変更しない。Flow 用の Room import (`kotlinx.coroutines.flow.Flow`) を追加 |
| `app/src/main/res/layout/credential_list_item.xml` (場合により分割) | password 専用化、PassKey 専用 layout を新規追加するか、共通レイアウトを残して bind 側で表示テキストを切替えるかは design.md で確定 (最低限 viewType 分岐に耐える状態に) |
| `app/src/main/res/values/strings.xml` / `values-ja/strings.xml` | 新規キー `credential_list_passkey_kind_label` (= 「PassKey」) / `credential_list_password_kind_label` (= 「Password」) / `credential_list_passkey_a11y` (TalkBack 用) / `credential_list_passkey_tap_v1_message` (v1 暫定 Snackbar 文言) / `credential_list_passkey_unknown_user` (`userDisplayName` が null/空のときの fallback) を追加 |

### Out of Scope (再掲)

- `Migration_*` / DB schema 変更 (D-9 と整合)。
- PassKey 個別管理 UI / 設定画面 / docs 更新。
- Recently used carousel への PassKey 統合 (本 Issue v1 では password のみ)。
- 種別フィルタ chip の UI 追加 (State 構造のみ未決事項として推奨案を提示)。

## データモデル / 公開 IF への影響

### `CredentialListItem` (新規 sealed interface)

```kotlin
// ui/list/CredentialListItem.kt (概念。最終形は design.md)
sealed interface CredentialListItem {
    /** Diff の同値判定に使う安定 ID。variant 種別を prefix することで衝突回避。 */
    val stableId: String
    val sortKey: SortKey

    data class Password(val credential: Credential) : CredentialListItem {
        override val stableId: String get() = "pw:${credential.id.value}"
        override val sortKey: SortKey get() =
            SortKey(lastUsedAt = credential.lastUsedAt, createdAt = credential.createdAt)
    }

    data class Passkey(val passkey: PasskeyDisplayModel) : CredentialListItem {
        override val stableId: String get() = "pk:${passkey.credentialId}"
        override val sortKey: SortKey get() =
            SortKey(lastUsedAt = passkey.lastUsedAt, createdAt = passkey.createdAt)
    }

    data class SortKey(val lastUsedAt: Long?, val createdAt: Long)
}
```

- **D-8 衝突回避**: `Credential.id.value` (Long) と `PasskeyEntity.credentialId`
  (String) は名前空間が異なるため、prefix `"pw:"` / `"pk:"` で確実に衝突を避ける。
- `sortKey` を共通化することで、`mergeAndSort` 関数の比較器が 1 本で済む。

### `PasskeyDisplayModel` (新規 UI 用 read-only model)

```kotlin
// ui/list/PasskeyDisplayModel.kt (概念。最終形は design.md)
data class PasskeyDisplayModel(
    val credentialId: String,
    val rpId: String,
    val rpDisplayName: String?,
    val userName: String?,
    val userDisplayName: String?,
    val displayName: String?,    // KeyNest 側のユーザー命名 (null = 未設定)
    val isDiscoverable: Boolean,
    val createdAt: Long,
    val lastUsedAt: Long?,
)
```

- **持たない**: `userHandle` (BLOB) / `encryptedPrivateKey` / `privateKeyIv` /
  `keyAlias` / `signCount`。これらは UI 層では一切必要なく、誤って画面に出すと
  NFR 2 (秘密情報の取り扱い) に違反する。
- 変換は `PasskeyEntity.toDisplayModel(): PasskeyDisplayModel` (内部関数) で行う。

### `PasskeyDao` 公開 IF への追加

| 追加メソッド | シグネチャ | 用途 |
|---|---|---|
| `listAll` | `@Query("SELECT * FROM passkeys ORDER BY (lastUsedAt IS NULL) ASC, lastUsedAt DESC, createdAt DESC") fun listAll(): Flow<List<PasskeyEntity>>` | KeyNest 全体の PassKey を Flow で観測。並びは既存 `listAllByRpId` / `listDiscoverableByRpId` と同じ |

- 既存 8 メソッドの **シグネチャは一切変更しない** (backward compatible)。
- `Flow<List<...>>` を採用 (`suspend fun ...(): List<...>` ではなく) する理由は、
  ViewModel 側で Room の自動変更通知に乗りたいから (#91 NFR 1 = main thread を
  blocking しない方針と整合)。一方で、本 Issue の段階で他コンシューマが Flow を
  必要としていないので、`@Query` メソッドを 1 つ増やすだけで impact は最小限。

### `PasskeyRepository` 公開 IF への追加

| 追加メソッド | シグネチャ | 用途 |
|---|---|---|
| `listAll` | `fun listAll(): Flow<List<PasskeyEntity>>` | DAO の `listAll` を delegate。`suspend` ではなく **同期 `fun`** で Flow を返す (`Flow` 自体は cold stream で副作用フリー) |

- 既存 4 メソッド (`save` / `findByCredentialId` / `findByRpIdAndUserHandle` /
  `delete`) は変更しない。本 Issue の変更は **加法のみ**。

### `ListPasskeysUseCase` (新規)

```kotlin
// domain/usecase/ListPasskeysUseCase.kt
class ListPasskeysUseCase(
    private val repo: PasskeyRepository,
) {
    operator fun invoke(): Flow<List<PasskeyDisplayModel>> =
        repo.listAll().map { entities -> entities.map { it.toDisplayModel() } }
}
```

- Entity → DisplayModel 変換を **UseCase 層** で行うことで、UI 層は `PasskeyEntity`
  (秘密情報を含む) に依存しない (NFR 2 と整合)。

### `CredentialListUiState` の型変更

```kotlin
// 変更前
data class CredentialListUiState(
    val mainList: List<Credential>, ...
)

// 変更後
data class CredentialListUiState(
    val mainList: List<CredentialListItem>, ...
)
```

- `recentList: List<Credential>` は **本 Issue では型変更しない** (PassKey の
  Recently used carousel 統合は Out of Scope)。
- `EMPTY` 定数の `mainList = emptyList<CredentialListItem>()` で初期化。

## Requirements

> EARS 形式 (The X shall …, When … the X shall …, If … the X shall …,
> Where … the X shall …) で記述。Issue #101 本文の Requirement 1〜4 を出発点に、
> 既決事項 (D-1〜D-9) と現状コードの構造を反映する。各 Acceptance Criteria 末尾の
> `(#101-R<x>.<y>)` は Issue 本文番号への traceability、`(D-X)` は本ドキュメント
> 既決事項テーブル対応を示す。

### Requirement 1: 統合表示 (#101 本文 Requirement 1)

**Objective:** As a エンドユーザー, I want password と PassKey が 1 つのリストに
混在表示され、種別がアイコンで区別されること, so that 鍵管理画面が分裂せず全体像を
1 画面で把握できる。

#### Acceptance Criteria

1.1. The `CredentialListActivity` shall password と PassKey を **同じ
`RecyclerView`** (`binding.recycler`) で 1 つのリストとして表示する (#101-R1.1)。

1.2. The `CredentialListAdapter` shall password 行と PassKey 行を **異なる
viewType** で出し分け、**種別アイコン** (password 用 = key 系 / PassKey 用 =
fingerprint or passkey 系) を切り替えて描画する (#101-R1.2 / D-1)。

1.3. When `CredentialListItem` が `Passkey` variant のとき, the adapter shall
PassKey 行のメタ表示として `rpDisplayName ?: rpId` を 1 行目、
`userDisplayName ?: userName ?: <fallback "(no user)">` を 2 行目、`rpId` を 3 行目
(`text_package` 相当の Mono フォント) に表示する (#101-R1.3)。displayName /
userDisplayName / userName / rpDisplayName が NULL or 空の場合の fallback 優先順位
は次のとおり:
- 1 行目: `displayName` (KeyNest 命名) → `rpDisplayName` → `rpId`
- 2 行目: `userDisplayName` → `userName` → `R.string.credential_list_passkey_unknown_user`
  (i18n key で「(ユーザー名なし)」相当)
- 3 行目: `rpId` 固定

1.4. The mainList sort order shall password と PassKey 共通で
**`lastUsedAt DESC, createdAt DESC`** とし、`lastUsedAt = NULL` は **末尾** に
並ぶ (#101-R1.4)。ViewModel の `mergeAndSort` 関数は次の比較器で安定 sort する
(NULL は最後):

```
compareBy<CredentialListItem>(
    { it.sortKey.lastUsedAt == null },  // false (= 値あり) → 上 / true → 下
    { -(it.sortKey.lastUsedAt ?: 0L) },  // 値あり同士は降順 (= -値で昇順 sort)
    { -it.sortKey.createdAt },           // tiebreaker: createdAt 降順
)
```

最終的な比較器の Kotlin 実装は design.md で確定する。

1.5. The `CredentialListAdapter.DIFF.areItemsTheSame(a, b)` shall **variant 種別
が一致しかつ内部 id が一致する場合のみ true** を返す (D-8)。すなわち
`CredentialListItem.stableId` の prefix (`"pw:"` / `"pk:"`) を含めた文字列比較で
判定する。これにより `Credential.id.value == 1L` と
`PasskeyEntity.credentialId == "1"` (極稀だが理論上可能) が `areItemsTheSame =
true` に誤判定する事故を防ぐ。

1.6. The `CredentialListAdapter.DIFF.areContentsTheSame(a, b)` shall variant ごとに
比較対象フィールドを切り替える:
- `Password` variant: 既存 (`packageName / username / label / updatedAt /
  signatureSha256`) と等価
- `Passkey` variant: `rpId / rpDisplayName / userName / userDisplayName /
  displayName / isDiscoverable / lastUsedAt`

1.7. When `mainList` が空 (= password も PassKey も 0 件) のとき, the
`computeEmptyKind(...)` shall 既存ロジック通り `EmptyKind.Initial` /
`EmptyKind.NoMatch` を返す。Empty state の文言は **password と PassKey で共通**
(Issue #29 で確定した「最初の鍵を巣に入れよう」文言を踏襲)。種別別の Empty state
文言は本 Issue Out of Scope。

1.8. The adapter shall PassKey 行のアイコンを `IconLoader.loadInto(...)` で
解決しない (PackageManager 解決は password の packageName 専用)。代わりに
`R.drawable.ic_passkey_24` 等の **vector drawable を直接 `setImageResource`** で
セットする (#101 確認事項 1 / 未決事項 1)。

1.9. The PassKey 行は password 行と **同一カードレイアウト基盤** (44dp アイコン
タイル + 3 行メタ + 右端 overflow) を踏襲する。signature chip / strength bar
領域は PassKey 行では `View.GONE` で隠す (PassKey に signature / strength の概念
なし)。最終的な layout 構造 (1 file 共有 / 2 file 分離) は design.md で確定。

### Requirement 2: 検索 (#101 本文 Requirement 2)

**Objective:** As a エンドユーザー, I want 検索 box への入力で password も PassKey
も横断検索できること, so that 種別を意識せずに目的の credential を見つけられる。

#### Acceptance Criteria

2.1. When ユーザーが `binding.inputSearch` に文字列を入力したとき, the ViewModel
shall password 行については既存通り `label` / `username` / `packageName` を、
PassKey 行については `rpId` / `rpDisplayName` / `userName` / `userDisplayName` /
KeyNest 側 `displayName` を **case-insensitive substring contains** でフィルタする
(#101-R2.1)。

2.2. The `applySearch(list, query)` 実装 shall variant ごとの `when` 分岐で
needle の検索対象 field を変える。例 (概念):

```kotlin
return list.filter { item ->
    when (item) {
        is CredentialListItem.Password -> with(item.credential) {
            label.lowercase().contains(needle) ||
            username.lowercase().contains(needle) ||
            packageName.lowercase().contains(needle)
        }
        is CredentialListItem.Passkey -> with(item.passkey) {
            rpId.lowercase().contains(needle) ||
            (rpDisplayName ?: "").lowercase().contains(needle) ||
            (userName ?: "").lowercase().contains(needle) ||
            (userDisplayName ?: "").lowercase().contains(needle) ||
            (displayName ?: "").lowercase().contains(needle)
        }
    }
}
```

2.3. The search shall main thread を blocking しない (#101-R2.2)。
`PasskeyRepository.listAll()` の Flow は Room の自動変更通知に乗り、
`viewModelScope` 内で `combine` される。`applyFilter` / `applySearch` は in-memory
変換 (pure function) なので main thread blocking のリスクは低いが、必要に応じて
ViewModel が `flow { ... }.flowOn(Dispatchers.Default)` を挟む (最終形は
design.md)。

2.4. The search shall **空白区切りの AND 検索 / OR 検索を行わない** (本 Issue
Out of Scope)。ユーザー入力の `query` 全体を 1 つの needle として扱い、
`trim().lowercase()` した文字列がいずれかの field に部分一致するかを判定する
(既存 `applySearch` の方針を継承)。

2.5. The search shall **大文字 / 小文字を区別しない**。`query` と各 field の
両方を `.lowercase()` してから `contains` を呼ぶ (既存実装と整合)。

2.6. When `query.isBlank()` のとき, the ViewModel shall 検索を **適用しない**
(全 mainList を返す)。これは既存 `mainListFlow` ロジックを踏襲する。

2.7. The existing chip filter (`CredentialFilter.SignatureMatched` /
`SignatureMissing`) shall **password 行のみに作用** する。PassKey 行は signature
chip フィルタの対象外で、フィルタが選択されていても **PassKey 行は常にリストに
残る** (PassKey は signatureSha256 の概念を持たないため、`SignatureMatched` で
フィルタすると PassKey が全消滅する UX が混乱要因となる。Requirement 2.7 で明示
固定)。`applyFilter` 実装は variant `when` で `Passkey` variant を素通しさせる。

### Requirement 3: 表記 (#101 本文 Requirement 3)

**Objective:** As a エンドユーザー, I want UI に表示される PassKey 種別の文言が
**「PassKey」** に統一されていること, so that 「passkey」「パスキー」「Passkey」
といったブレで違和感を覚えない。

#### Acceptance Criteria

3.1. The `R.string.credential_list_passkey_kind_label` shall 値が **`"PassKey"`**
で定義される (`values/strings.xml` / `values-ja/strings.xml` 共通の文字列。i18n
不要 = 商標的固定表記、#99 の `passkey_create_*` 系と整合) (#101-R3.1 / D-1)。

3.2. The `R.string.credential_list_password_kind_label` shall 値が **`"Password"`**
(en) / **`"パスワード"`** (ja) で定義される。

3.3. The PassKey 行に表示する「種別ラベル」 / contentDescription / Snackbar
メッセージ / KDoc の文言は **必ず `R.string.credential_list_passkey_kind_label`** を
参照する (ハードコードで `"passkey"` 等を埋め込まない)。

3.4. The `CredentialListAdapter` / `CredentialListViewModel` / `CredentialListItem`
の KDoc / コメント shall PassKey 機能に言及する箇所で **「PassKey」** 表記を
用いる (D-1)。

3.5. The 新規 vector drawable のファイル名 shall **`ic_passkey_24.xml`** の
ように **小文字 + アンダースコア** (Android リソース命名規約)。これは表記
ポリシーと別軸 (リソースファイル名は OS 制約により小文字必須)。

### Requirement 4: テスト (#101 本文 Requirement 4)

**Objective:** As a メンテナ, I want 統合表示 / 検索 / sort の主要経路が自動
テストで回帰検知できること, so that 後続 Issue (個別管理 UI 等) 実装中に一覧画面の
退行を早期に検知できる。

#### Acceptance Criteria

4.1. The `CredentialListViewModelTest` (拡張 or 新規) shall password Flow に 2
件、PassKey Flow に 2 件をそれぞれ供給したとき、`uiState.mainList` に **4 件**が
**`lastUsedAt DESC, createdAt DESC`** の順で並ぶことを検証する (#101-R4.1)。NULL
混在ケース (`lastUsedAt = null` の PassKey が `lastUsedAt = 100L` の password の
**後** に並ぶ) も assert する。

4.2. The `CredentialListViewModelTest` shall `applySearch(mainList, query)` の
変換結果について、PassKey の `userName` に部分一致する query が PassKey 行を
ヒットさせ、かつ無関係 password 行を除外することを検証する (#101-R4.3)。
case-insensitive (`"ALICE"` query が `userName = "alice"` を hit) も assert。

4.3. The `CredentialListViewModelTest` shall `CredentialFilter.SignatureMatched` /
`SignatureMissing` が選択されたとき、PassKey 行が **常に mainList に残る** こと
(Requirement 2.7) を検証する。

4.4. The `CredentialListAdapterInstrumentationTest` shall `CredentialListItem.Password`
と `CredentialListItem.Passkey` の 2 item を submitList した RecyclerView が、
それぞれ **正しい layout** (`R.layout.credential_list_item` 系 or
`credential_list_passkey_item` / 共通 layout 内で viewType 分岐) で描画されること
を ViewHolder のクラス名 + viewType で検証する (#101-R4.2)。

4.5. The `CredentialListAdapterInstrumentationTest` shall PassKey 行の icon 領域
(`binding.iconApp`) が `R.drawable.ic_passkey_24` (vector) で塗られていること、
password 行は既存 `IconLoader` フォールバック (InitialLetterDrawable) または
PackageManager 解決済み Drawable がセットされていることを assert する。

4.6. The `PasskeyDisplayModelTest` shall `PasskeyEntity` → `PasskeyDisplayModel`
変換後の DisplayModel が **`userHandle` / `encryptedPrivateKey` / `privateKeyIv`
/ `keyAlias` / `signCount`** いずれの値もフィールドに持たない (= reflection /
data class 自動 toString で出ない) ことを検証する (NFR 2)。

4.7. The `CredentialListViewModelTest` shall `mainList` が空 + `query=""` +
`filter=None` で `emptyKind = EmptyKind.Initial` を返し、`query="xxx"` /
`filter=SignatureMatched` のいずれかが active で mainList が空のとき
`EmptyKind.NoMatch` を返すこと (既存挙動の回帰なし) を検証する。

4.8. The 既存テスト `applyFilterTest` / `applySearchTest` (password 単独) shall
本 Issue 変更後も全件 pass する。`applyFilter` / `applySearch` の signature は
内部的に `List<Credential>` から `List<CredentialListItem>` に変わるが、既存
テストは新しい signature 用に **最低限の adapter テスト** に更新される (削除
ではなく型 update のみ)。具体的更新範囲は design.md で確定。

4.9. The 既存 `CredentialListActivity` 関連テスト (Empty state visibility /
chip sync / sort popup 等) shall 本 Issue 変更後も全件 pass する。

4.10. The `PasskeyDaoTest` (#91 で確立) shall `listAll(): Flow<List<PasskeyEntity>>`
の新規追加に対し、`insert` → `listAll` 観測 → 1 件返却 → `insert` 別 row →
2 件返却の Flow 自動更新を assert する。並びは `lastUsedAt DESC, createdAt DESC,
NULL 末尾` の既存 listAllByRpId と同等であることを assert。

4.11. The `PasskeyRepositoryTest` (#91 で確立) shall `listAll(): Flow<List<PasskeyEntity>>`
の Flow が `PasskeyDao.listAll()` を delegate していること、および空テーブル時に
**空 List** を emit すること (例外 / null emit ではない) を assert する。

### Requirement 5: PassKey 行のタップ挙動 (本 Issue v1 限定 / Issue 本文には
未明記、PM triage による補足)

**Objective:** As a エンドユーザー, I want PassKey item をタップしたときに
クラッシュせず、何が起こり得ないかが分かるよう **明示的なフィードバック** が
返ること, so that 「タップしたが何も起きない」状態に戸惑わない。

#### Acceptance Criteria

5.1. When ユーザーが mainList 上の `CredentialListItem.Passkey` をタップしたとき,
the `CredentialListActivity` shall (a) `CredentialEditActivity` を起動しない、
(b) `Snackbar` を `binding.root` に対して `LENGTH_SHORT` で表示し、文字列は
`R.string.credential_list_passkey_tap_v1_message` を使用する。

5.2. The `R.string.credential_list_passkey_tap_v1_message` shall ja で
**「PassKey の個別管理は今後のアップデートで提供予定です」** 相当の文言を持ち、
en では **`"PassKey management is coming in a later release"`** 相当の文言を
持つ (最終文言は design.md / コピーライティング側で確定)。

5.3. The PassKey 行の **long-click** shall **何もしない** (`setOnLongClickListener`
で `true` を返して consume するか、`null` を渡して propagation させるかは design.md
で確定。少なくとも `promptDelete(...)` を呼ばない = password 用 delete dialog を
誤って表示しない)。

5.4. The PassKey 行の **overflow ImageButton** (右端 3 点リーダ) shall **PassKey
行では visibility を `View.GONE` にする** (現状 overflow メニューは password 用の
「Duplicate」のみで、PassKey に該当する操作がない)。Requirement 5.1 と整合。

5.5. When 後続 Issue (#89 分割案 6) が PassKey 個別管理画面を実装するとき,
Requirement 5.1〜5.4 の暫定挙動は **置き換えられる**。本 Issue は接合点として
`onItemClick: (CredentialListItem) -> Unit` の callback シグネチャを sealed
variant 対応にすることで、後続 Issue の差分を最小化する。

### Requirement 6: アクセシビリティ / i18n (Issue #101 本文には未明記、UX
品質維持のため PM triage が追加)

**Objective:** As a TalkBack ユーザー, I want PassKey 行と password 行が音声で
区別できること, so that 視覚に依存せず credential 一覧を扱える。

#### Acceptance Criteria

6.1. The PassKey 行のアイコン (`binding.iconApp` 相当 or 専用 ImageView) shall
**`contentDescription`** に `R.string.credential_list_passkey_kind_label` (= 「PassKey」)
を設定する (空 contentDescription は禁止)。

6.2. The password 行のアイコン shall 既存挙動通り `contentDescription = "@null"`
を維持する (既存 layout で `android:contentDescription="@null"`、TalkBack は
親 row が読み上げを担う)。

6.3. The PassKey 行の row 全体 contentDescription / accessibility 用テキスト
shall「PassKey: <rpDisplayName ?: rpId>, <userDisplayName ?: userName>」相当を
読み上げ可能にする (具体的な実装手段 = `View.contentDescription` セット or
TextView の text 連結で十分かは design.md)。

6.4. The 新規 string keys (`credential_list_passkey_kind_label` /
`credential_list_password_kind_label` /
`credential_list_passkey_tap_v1_message` /
`credential_list_passkey_unknown_user` / `credential_list_passkey_a11y` 等) shall
`values/strings.xml` (en default) と `values-ja/strings.xml` の **両方** に
追加される。en 側のキー欠落 / ja 側のキー欠落で AAPT2 が fail しないこと
(`R.string.*` 参照が両 locale で解決すること) を CI で保証する。

## Non-Functional Requirements

### NFR 1: パフォーマンス / スレッディング

1.1. The `PasskeyRepository.listAll()` Flow shall Room の **suspend / Flow
基盤の自動 IO dispatcher** に乗り、main thread を blocking しない (#91 NFR 1
を継承)。

1.2. The `CredentialListViewModel.mainListFlow` shall `combine(query, filter,
sortedListFlow, listPasskeysFlow) { ... }` の合成を `viewModelScope` 内で実行し、
`flowOn(Dispatchers.Default)` (in-memory 変換が CPU bound) を経由しても
**main thread blocking しない**。最終形は design.md。

1.3. The 一覧 UI shall **password 100 件 + PassKey 100 件 = 200 件規模** で stutter
なく描画される (60fps、Recycler の onBindViewHolder が 1 行あたり 16ms 未満で
返る)。具体的な FPS 計測は本 Issue 範囲外 (instrumentation test での目視確認
+ DiffUtil の `payload` 最適化は将来別 Issue)。

1.4. The 一覧 UI shall password 1000 件 + PassKey 1000 件 = 2000 件規模でも
スクロールが詰まらない (RecyclerView の ViewHolder reuse + DiffUtil で
変更分のみ rebind)。

1.5. The merging / sorting 関数 (`mergeAndSort`) shall **O(N log N)** の
計算量 (Kotlin `sortedWith(comparator)`) で抑える。`mergeSort` の手書きは
不要。

### NFR 2: 秘密情報の取り扱い

2.1. The `PasskeyDisplayModel` shall `userHandle` / `encryptedPrivateKey` /
`privateKeyIv` / `keyAlias` / `signCount` を **フィールドに持たない**
(Requirement 4.6 と整合)。

2.2. The `PasskeyRepository.listAll()` の戻り値 `PasskeyEntity` は `userHandle`
等の sensitive 列を含むが、`ListPasskeysUseCase` 内で **即時 DisplayModel に
変換** し、UI 層 (ViewModel 以降) には Entity を渡さない。

2.3. The `CredentialListAdapter` / `CredentialListActivity` shall PassKey
関連の log 出力で `credentialId` / `rpId` / `userName` / `userDisplayName` /
`displayName` を **`info` 以上のレベルで logcat に出さない** (既存 `SafeLogger`
慣行 / #91 NFR 2 / #99 NFR 1.3 を継承)。`SafeLogger.info` 呼び出しでは
`size=...` / `emptyKind=...` 等の count / 種別ラベルのみとする (既存
`CredentialListActivity.renderState` の log policy を踏襲)。

2.4. The `CredentialListItem.Passkey` の `toString()` shall (data class 自動生成
で構わないが) DisplayModel の値が **既に sensitive 列を持たないため** 漏洩
リスクなし。`PasskeyEntity.toString()` の redaction (#91 NFR 2.2) には触らない。

### NFR 3: 既存テスト非破壊 / 後方互換性

3.1. The 本 Issue 変更 shall #91 の `Migration_4_5_Test` / `PasskeyDaoTest` /
`PasskeyRepositoryTest` を破壊しない (DAO に **メソッドを追加** するのみで
既存メソッドのシグネチャ / 振る舞いは変更しない)。

3.2. The 本 Issue 変更 shall #99 の `PasskeyCreatorTest` /
`AuthenticatorDataTest` / `KeyNestCredentialProviderServiceTest` (登録セレモニー
拡張部) を破壊しない (本 Issue は registration / authentication ceremony を
触らない)。

3.3. The 本 Issue 変更 shall #100 の `PasskeyAssertionTest` /
`KeyNestCredentialProviderServiceTest` (認証セレモニー拡張部) を破壊しない
(同上)。

3.4. The 本 Issue 変更 shall #90 の `CredentialProviderServiceManifestTest` /
`CredentialProviderXmlTest` を破壊しない (Manifest / xml に触らない)。

3.5. The 既存 `CredentialListViewModelTest` の password 専用テスト ケース shall
本 Issue 変更後も pass する。テスト signature の型変更 (`List<Credential>` →
`List<CredentialListItem>`) に追従する **テスト helper 更新** は許容するが、
**期待されるロジック** (filter / search / emptyKind 判定) の正しさを再確認する
形を維持する。

### NFR 4: ネット境界 / セキュリティポリシー

4.1. The 本 Issue 変更 shall `android.permission.INTERNET` を追加しない
(umbrella #89 のネット境界ポリシー継承)。

4.2. The 本 Issue 変更 shall PassKey の private key 平文 / `userHandle` raw value /
`encryptedPrivateKey` raw value のいずれも UI / log / Snackbar に **絶対に
露出させない** (NFR 2 と整合)。

### NFR 5: 命名 / 表記

5.1. The 新規追加クラス名 / KDoc / コメント / ログメッセージ shall PassKey
機能に言及する箇所で **「PassKey」** 表記を用いる (D-1)。

5.2. The string resource key shall `credential_list_passkey_*` の prefix で
統一する。既存 `passkey_create_*` (#99 確立) との prefix の使い分けは「画面別
prefix」(`credential_list_*` / `passkey_create_*` / `passkey_biometric_prompt_*`)
で揃える。

### NFR 6: アクセシビリティ (Requirement 6 とは別軸の構造的要件)

6.1. The 新規追加 ImageView (種別アイコン) shall TalkBack で読み上げ可能な
`contentDescription` を持つ (Requirement 6.1 と整合)。`@null` を設定する場合は
親 row が完全な読み上げを担うことを design.md で確認する。

6.2. The PassKey 行の Touch target shall password 行と同サイズ (44dp アイコン
タイル + Card 全体クリック領域) を維持する。

## 未決事項 / 確認事項テーブル

> Issue #101 本文「確認事項」2 件、および PM triage で抽出した補足論点を、
> design / 実装フェーズで決着が必要な単位に分解する。各項目に「決定が必要な人」
> 「現時点の暫定案」「決定が遅れた場合の影響」を併記する。

| ID | 項目 | 現時点の暫定案 | 決定者 | 決定が遅れた場合の影響 | 出所 |
|---|---|---|---|---|---|
| Q-1 | **アイコン素材**: PassKey 用 / password 用アイコンを新規追加するか既存流用か | **暫定**: PassKey 用 = `R.drawable.ic_fingerprint_24` (既存 vector) を流用 or 新規 `ic_passkey_24.xml` を Material Symbols `passkey` から発番。password 用 = 既存 `ic_key_24.xml` を流用 or `ic_password_24.xml` を新規発番。**推奨**: 後続 #89 分割案 6 (個別管理 UI) と整合させるため、本 Issue でも `ic_passkey_24.xml` を新規発番してデザイン専有にする。`ic_fingerprint_24.xml` は生体認証セレモニーで既に使われているため共有しない | デザイナ (human) / 実装時の claude (vector を作成可) | デザイナ確定が遅れる場合は claude が暫定 vector を発番し、後続 PR で差し替え可能な構造 (resource 名は `ic_passkey_24.xml` に確定) で先行 | Issue #101 確認事項 1 |
| Q-2 | **種別フィルタ UI** の State 構造 | **暫定** + **推奨**: `CredentialKindFilter` (3 値 enum `All` / `PasskeyOnly` / `PasswordOnly`) を ViewModel `private val kindFilter: MutableStateFlow<CredentialKindFilter>` で **持つだけ** 保持 (UI 上 chip は未追加)。default = `All`。`mainListFlow` の `applyFilter` は将来 `kindFilter` を読む拡張ポイントを残す。**v1 では `kindFilter` は常に `All` に固定**。後続 Issue で chip を追加するだけで活性化できる | claude (実装時に decide。本 Issue 範囲) | State 構造の決定が遅れた場合、後続「種別フィルタ chip 追加 Issue」が ViewModel を再設計する破壊変更を強いる。先行確定推奨 | Issue #101 確認事項 2 / Out of Scope |
| Q-3 | **タップ時の遷移先** (PassKey item v1) | **暫定**: Requirement 5.1 のとおり `Snackbar` で「個別管理は後続 Issue」表示。**推奨**: 暫定案そのまま採用 (no-op + Snackbar)。**代替案**: そもそも PassKey item を `isClickable = false` にして tap event を握り潰す案もあるが、TalkBack 上で「クリック可能」フィードバックが不一致になるため非推奨 | claude (Requirement 5 で確定済み、design 側追認のみ) | 確定済みなので影響なし | PM triage |
| Q-4 | **`PasskeyDao.listAll()` の signature** (`Flow<List<PasskeyEntity>>` vs `suspend fun listAll(): List<PasskeyEntity>`) | **暫定** + **推奨**: `Flow<List<PasskeyEntity>>` を採用。理由: Room の `Flow` は自動変更通知に乗るため、PassKey が他経路 (#99 / #100) で増減した瞬間に一覧が更新される | claude (実装時に decide) | Flow を使わない場合、PassKey 登録 / 認証イベント後に一覧が古いままになる UX 退行が発生 | データモデル設計 |
| Q-5 | **`sort` popup (`CredentialSortOrder.UpdatedAtDesc` / `LabelAsc` / `PackageAsc`) と統合 list の整合** | **暫定**: 既存 sort popup は **password 行のみに作用** し、PassKey 行は常に `lastUsedAt DESC, createdAt DESC`。**推奨**: 本 Issue では「sort popup を全リストに同じ comparator 適用するのは PassKey にとって意味のあるフィールドが揃わない (label / packageName が PassKey 概念に存在しない) ため、`UpdatedAtDesc` 選択時のみ sort popup を有効化し、他選択時はリスト全体に `lastUsedAt DESC, createdAt DESC` を強制する」 / **代替案**: sort popup 自体に disable 状態を追加する。最終形は design.md | claude / UX (PM 確認候補) | 確定が遅れると ViewModel の sort 反映ロジックが不確定で、テストの期待値が書けない | PM triage |
| Q-6 | **Recently used carousel への PassKey 統合の有無** (Out of Scope 明記済みだが、関連) | **暫定**: 本 Issue では **password のみ** (`ObserveRecentlyUsedUseCase` の現状の戻り値型 `List<Credential>` を据置)。後続 Issue で PassKey 統合 | claude (本 Issue 範囲で確定済み) | 確定済 | PM triage |
| Q-7 | **PassKey 行の overflow ImageButton 取扱** (`View.GONE` vs **隠さず disabled**) | **暫定**: Requirement 5.4 のとおり `View.GONE`。**推奨**: そのまま採用 (overflow メニュー = 「Duplicate」は PassKey に意味なし) | claude (Requirement 5.4 で確定) | 確定済 | PM triage |
| Q-8 | **i18n 文言** (ja / en) の最終コピー | **暫定**: 本 Requirement で提示した英訳・和訳をそのまま採用。最終コピーは PR レビューで修正可 | レビュアー (human) / claude (PR で提案) | 暫定で merge → 後続 PR で文言調整可。クリティカルではない | PM triage |
| Q-9 | **layout XML 構造** (1 file 共有 vs 2 file 分離) | **暫定**: `credential_list_item.xml` を共通 layout として残し、bind 側で `chip_signature` / `strength_bar` の visibility と icon resource を切り替える。**推奨**: 共通 layout 1 file (DRY + DiffUtil の payload 最適化が将来効きやすい) | claude (design.md で確定) | 確定が遅れても実装で吸収可。テストは ViewHolder のクラス分岐で書き分ける | PM triage |
| Q-10 | **Issue 本文と source code の不整合** (Issue #101 本文「`PasskeyRepository.listAllByRpId` (or 全件取得) を統合した Flow を提供」と現状 `PasskeyRepository` の API 不在) | **事実**: `PasskeyRepository` 現状 API は `save / findByCredentialId / findByRpIdAndUserHandle / delete` のみで、`listAllByRpId` も全件取得 Flow も存在しない (#91 段階で DAO 側のみ `listAllByRpId` が追加されていた)。**推奨**: Issue 本文の「listAllByRpId or 全件取得」のうち、本 Issue では **「全件取得」 = `listAll()`** を新規追加する (RP ごとに分割表示する UI 要件が本 Issue にないため、`listAllByRpId` の追加は不要) | claude (本 Issue で確定) | 確定が遅れると ViewModel の Flow 接合点が不確定。本 Requirement の D-9 / 「データモデル」セクションで先行確定済み | Issue #101 本文 |

## 受入基準サマリ (チェックリスト)

> 上記 EARS から **重要 8 件** を抽出した acceptance summary。PR レビュー時に
> ここを上から順に確認できる。

- [ ] 一覧 RecyclerView に password と PassKey が **混在表示** され、種別アイコン
  で視覚区別できる (Req 1.1 / 1.2)
- [ ] 並び順が password / PassKey 共通で **`lastUsedAt DESC, createdAt DESC`** (NULL
  末尾) になっている (Req 1.4 / NFR 1.5)
- [ ] DiffUtil の `areItemsTheSame` で password.id と passkey.credentialId が
  **誤一致しない** (Req 1.5 / D-8)
- [ ] 検索 box の入力で PassKey の `rpId` / `rpDisplayName` / `userName` /
  `userDisplayName` / KeyNest displayName を **case-insensitive 部分一致** で
  ヒットさせる (Req 2.1 / 2.2 / 2.5)
- [ ] signature chip フィルタ選択時に **PassKey 行が消えない** (Req 2.7)
- [ ] UI 表記が **「PassKey」** に統一されている (Req 3.1 / 3.3 / 3.4 / D-1)
- [ ] PassKey 行のタップで **Snackbar 表示 + クラッシュなし**、edit 画面が
  起動しない (Req 5.1 / 5.5)
- [ ] `CredentialListViewModelTest` 拡張 / `CredentialListAdapterInstrumentationTest`
  新規 / `PasskeyDisplayModelTest` 新規 / `PasskeyDaoTest.listAll` 拡張が
  CI で pass する (Req 4.1〜4.11)
- [ ] **DB schema 変更なし** (`KeyNestDatabase.version` は据置、`app/schemas/.../*.json`
  発番なし) (D-9)
- [ ] 既存 password 一覧の機能 (Issue #9 系の sort / Empty state / Recently
  used / Duplicate / Delete dialog / row click → edit) が **完全に回帰なし**
  (NFR 3.5 / Req 4.9)

## 用語集

- **PassKey**: WebAuthn / FIDO2 で定義される public-key credential。本リポジトリ
  では umbrella #89 確定により「PassKey」表記で統一する (D-1)。
- **password 行 / PassKey 行**: 一覧 UI における 2 種類の item。本 Issue で
  `CredentialListItem.Password` / `CredentialListItem.Passkey` の sealed variant
  として導入する。
- **discoverable / non-discoverable**: PassKey の residentKey 属性。本 Issue UI
  では区別せず、KeyNest が保管している全 PassKey を表示する (D-7)。
- **rpId / rpDisplayName**: WebAuthn の Relying Party 識別子 / 表示名。一覧
  の 1 行目に表示する。
- **userName / userDisplayName**: WebAuthn の user.name / user.displayName (RP
  が提示)。一覧の 2 行目に表示する。
- **displayName** (KeyNest 側): ユーザーが KeyNest UI で PassKey に付与する別名。
  Phase 4 時点ではユーザー命名 UI 自体が未提供のため常に NULL (個別管理 UI = #89
  分割案 6 で活性化)。
- **stableId**: DiffUtil の `areItemsTheSame` で衝突なく同値判定するための
  string key (`"pw:<id>"` / `"pk:<credentialId>"`) (Req 1.5)。
- **CredentialListItem**: 本 Issue で導入する sealed interface。`Password` /
  `Passkey` の 2 variant を持つ統合 list 用 model。
- **PasskeyDisplayModel**: 本 Issue で導入する UI 用 read-only model。
  `PasskeyEntity` から sensitive 列を除去した投影 (NFR 2)。

## 関連 Issue / PR (再掲)

- **Parent**: #89 (umbrella: feat(passkey): Android Credential Manager 経由の
  passkey プロバイダ対応)
- **Depends on**: #91 (Room migration + `PasskeyEntity` / `PasskeyDao` /
  `PasskeyRepository`) — merged
- **参考 (先行 Phase)**:
  - #90 Service 骨組み (merged)
  - #99 登録セレモニー (merged)
  - #100 認証セレモニー (merged)
- **後続予定** (#89 分割案):
  - #89 分割案 6: PassKey 単位の rename / 削除 UI (本 Issue で接合点を確立)
  - #89 分割案 7: 設定画面 / OS 設定導線
  - #89 分割案 8: README / Privacy / Support docs 更新
  - 種別フィルタ chip UI 追加 Issue (本 Issue で State 構造 = Q-2 推奨案を確立)
- **参考**:
  - Android Credential Provider:
    https://developer.android.com/training/sign-in/credential-provider
  - W3C WebAuthn Level 2: https://www.w3.org/TR/webauthn-2/
