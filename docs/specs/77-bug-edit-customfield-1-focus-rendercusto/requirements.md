# Requirements Document

Issue: #77 bug(edit): customField の入力欄が 1 文字ごとに focus を失い連続入力できない
(`renderCustomFields` の full rebuild 副作用)

Branch: `claude/issue-77-impl-bug-edit-customfield-1-focus-rendercusto`

## 1. 背景 / 問題

`CredentialEditActivity.renderCustomFields()`
(`app/src/main/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditActivity.kt:412-441`)
は、`viewModel.customFields` StateFlow の emit を受信するたびに
`container.removeAllViews()` でコンテナ内の **すべての行 view を破棄**し、
`state.rows` を全件再 inflate している。実装コメントには
"deliberately replaces the entire container subtree on every state emission"
と書かれており、Phase 1 (Issue #66 / PR #69) で意図的に採用された構成だが、
TextWatcher との組み合わせによる focus loss の副作用が見落とされていた。

### 再現シーケンス

1. ユーザーが `fieldKey` 入力欄に "a" を 1 文字入力
2. `TextWatcher` 発火 → `viewModel.updateCustomFieldKey(rowId, "a")`
3. `_customFields` StateFlow が emit
4. Activity の `customFields.collect(::renderCustomFields)` が受信
5. `removeAllViews()` で入力中の EditText ごと破棄
6. 全行を新たに inflate（focus を持たない fresh view）
7. 入力欄から focus が外れ、ソフトキーボードも引っ込む

ユーザーは 1 文字打つたびに再度 EditText をタップしないと続きを入力できず、
事実上 customField の編集ができない状態である。

### 影響範囲

- Phase 1 (Issue #66) で導入された `Mode.New` の customField 編集 UX を完全に破壊している
- Phase 1.5 (Issue #73 / PR #74 already merged) で `Mode.Edit` にも customField 編集が
  解放されたため、本 bug が放置されると影響範囲が広がる（新規・既存両方で連続入力不可）
- Phase 2 (Issue #67 / PR #72 merged) の detected_fields サジェスト UI は
  「chip タップで fieldKey 入力欄に転送」する設計のため、転送後にユーザーが値を
  追記しようとすると本 bug の影響を受け、サジェスト機能の実用性が大きく低下する

### 関連実装の現状

- `CredentialEditViewModel.CustomFieldsState.Row` は `rowId: Long` を持ち、
  `nextRowId++` で単調増加に採番される（`CredentialEditViewModel.kt:200-205, 276-`）
- `removeCustomFieldRow` / `updateCustomFieldKey` / `updateCustomFieldValue` は
  rowId をキーに行を mutate する（`CredentialEditViewModel.kt:538-562`）
- `simpleWatcher` は匿名 `TextWatcher` を返す inline ヘルパー
  (`CredentialEditActivity.kt:545-552`)
- `res/values/ids.xml` には現状 `icon_loader_request_tag` のみが定義されている
  (Issue #43 で導入された pattern を踏襲する想定)

## 2. スコープ

### 対象

| 対象 | 変更内容 |
|---|---|
| `CredentialEditActivity.renderCustomFields()` | full rebuild から **rowId ベースの idempotent diff 描画** へ変更する |
| 同上 | container 内の既存 row view に `rowId` を tag (`view.setTag(R.id.custom_field_row_id, rowId)`) で保持し、再 emit 時に既存 view を再利用する |
| 同上 | `state.rows` と container child を rowId で diff し、新規行のみ inflate + addView、削除行のみ removeView、既存行は値を re-set せず触らない |
| 同上 | 行並び順を `state.rows` 順序に揃える |
| `res/values/ids.xml` | `custom_field_row_id` を新規定義（既存の `icon_loader_request_tag` と同じ pattern） |
| 既存 EditText の値同期 | 通常 emit では既存 EditText に `setText()` を呼ばず TextWatcher 経路で完結。reducer 側で値が正規化されて ViewModel と EditText が不一致になる稀ケースのみ、TextWatcher 一時 detach → setText → re-attach する |

### Out of Scope

- RecyclerView 化（DiffUtil ベースの本格的なリファクタは別 Issue とする。
  本 Issue は最小修正で focus loss を解消することを目的とする）
- customField value の masking（必要なら別 Issue 起票）
- customField の検索 / sort 機能
- Mode.Edit での customField 編集可能化（Phase 1.5 = Issue #73 / PR #74 のスコープ。
  本 Issue マージ時点で既にマージ済み前提）
- TextWatcher / `simpleWatcher` の API 形状変更（必要最小限の改修にとどめる）
- detected_fields サジェスト UI 側のロジック変更（Phase 2 / Issue #67 のスコープ）

## 3. 用語定義

| 用語 | 定義 |
|---|---|
| 行 view (row view) | `R.layout.view_custom_field_row` を inflate した 1 customField 行のサブツリー（fieldKey EditText + value EditText + 削除ボタンを含む） |
| rowId | `CustomFieldsState.Row.rowId`。ViewModel が `nextRowId++` で割り当てる UI スコープの単調増加 Long。永続化されない |
| full rebuild | `renderCustomFields` が emit のたびに `removeAllViews()` で全行を破棄して再 inflate する現行挙動 |
| idempotent diff 描画 | rowId をキーに既存 view を再利用し、差分のみ add/remove する描画戦略 |
| TextWatcher re-entrancy | `setText()` 呼び出しが `TextWatcher.onTextChanged` を発火し、それが ViewModel reducer を呼び戻して `_customFields` を emit し、再び `setText()` が呼ばれる状態 |
| detach / re-attach pattern | `editText.removeTextChangedListener(watcher)` → `setText` → `editText.addTextChangedListener(watcher)` の 3 段操作 |

## 4. ユーザーストーリー

1. **customField を編集したいユーザー**: `fieldKey` 入力欄に複数文字を連続入力しても
   focus がキープされ、ソフトキーボードが引っ込まずに一息で入力できる
2. **customField の value を編集したいユーザー**: 既存行の value 欄でも同様に連続入力可能
3. **detected_fields サジェスト経由で fieldKey を補完したいユーザー**: chip タップで
   fieldKey が転送された後、続けて入力欄を編集できる
4. **行を追加・削除したいユーザー**: 「フィールド追加」「削除」ボタンを押しても、
   操作対象外の行で編集中だった内容や focus が失われない

## 5. 機能要件 (EARS 形式)

Issue 本文の Requirement 1〜6 を一次ソースとし、表現を整えて再掲する。
Issue コメントに人間の追加決定はなく、本書ではそのまま採用する。

### Requirement 1: 既存 view の保持（idempotent diff 描画）

1.1 When `state.rows` の subset が前回 render と同じ `rowId` を持つとき,
the `renderCustomFields` shall それらの既存 row view を `removeView` せず保持する

1.2 When `state.rows` に新規 `rowId` が追加されたとき, the `renderCustomFields`
shall 新規 row view を inflate し container 末尾に `addView` する

1.3 When `state.rows` から `rowId` が削除されたとき, the `renderCustomFields`
shall 対応する row view のみを `removeView` する

1.4 The `renderCustomFields` shall row view の並び順を `state.rows` の順序に
揃える（rowId で識別し、必要なら `addView(view, position)` で位置調整する）

1.5 The `renderCustomFields` shall `state.editable == false` のとき、現行挙動
（行を 0 件にし、add ボタン非表示、read-only note 表示）を変更しない

### Requirement 2: focus 保持

2.1 When ユーザーが既存 EditText に 1 文字入力したとき, the focus shall その
EditText に保持される

2.2 When ユーザーが連続入力（例: "abcdef"）したとき, the EditText shall
中断なく全文字を受け取る

2.3 While ソフトキーボードが表示されているとき, the keyboard shall 1 文字入力
ごとに引っ込まない

2.4 When state emit で `state.rows` サイズが変わらず既存 rowId のみ更新された
とき, the `renderCustomFields` shall 既存 EditText に `setText()` を呼ばない
（値は ViewModel と EditText で既に一致しているため）

### Requirement 3: TextWatcher re-entrancy 対策

3.1 If state emit によって既存 EditText の値が ViewModel 側 reducer で正規化
された結果として EditText 表示値と不一致になった場合（rare ケース）,
the `renderCustomFields` shall TextWatcher を一時 detach → `setText` →
re-attach することで EditText 表示を ViewModel と同期する

3.2 The TextWatcher shall ViewModel への dispatch ループ（同値再 emit が
無限再帰する）を引き起こさない（既存 `simpleWatcher` の挙動を維持する）

3.3 The implementation shall watcher インスタンスを行 view に紐付けて保持する
（タグまたは同等手段で view から watcher を参照できるようにする）。具体的な
保持手段は design.md / 実装フェーズで確定する

### Requirement 4: 行追加・削除の副作用なし

4.1 When ユーザーが「フィールド追加」ボタンを押したとき, the
`renderCustomFields` shall 新規行を末尾に追加し、既存行の focus と編集中の
値を破壊しない

4.2 When ユーザーが既存行の削除ボタンを押したとき, the `renderCustomFields`
shall 対象行のみを `removeView` し、他の行の focus と値を破壊しない

4.3 The reducer (`addCustomFieldRow` / `removeCustomFieldRow` /
`updateCustomFieldKey` / `updateCustomFieldValue`) shall 既存仕様を変更しない
（本 Issue は描画層のみを修正する）

### Requirement 5: テスト

5.1 The CredentialEditActivityTest shall `fieldKey` 入力欄に "abcdef" を
連続入力したとき focus が同一 EditText に保持されることを検証する

5.2 The CredentialEditActivityTest shall `value` 入力欄に "12345" を連続入力
したとき focus が同一 EditText に保持されることを検証する

5.3 The CredentialEditActivityTest shall 既存行が 3 件ある状態で 1 件追加した
とき、追加前 row の focus / 値が保持されることを検証する

5.4 The CredentialEditActivityTest shall 既存行が 3 件ある状態で middle row を
削除したとき、残る row の focus / 値が保持されることを検証する

5.5 The test suite shall 既存テスト（Phase 1 / Phase 1.5 / Phase 2 を含む
`CredentialEditViewModelTest` / `CredentialEditViewModelCustomFieldsTest` /
`CredentialEditViewModelSuggestionTest` 等）を一切 fail させない

5.6 The focus 保持テスト shall Robolectric / Espresso / 単純な Activity 単体
テストのいずれかで実現する。具体的手段は design.md / 実装フェーズで確定する

### Requirement 6: Phase 1.5 (#73) との整合性

6.1 The fix shall `Mode.New` / `Mode.Edit` 両方で同一挙動になる（両モードで
共通の `renderCustomFields` を通る現行構造を維持する）

6.2 The fix shall `state.editable == false` のときの挙動（行を空のまま、
add ボタン非表示、read-only note 表示）を変更しない

6.3 The fix shall Phase 2 (#67) detected_fields サジェスト UI 側のコードを
変更しない。chip タップによる fieldKey 転送経路は既存実装をそのまま使う

## 6. 非機能要件

### NFR 1: 性能

1. The `renderCustomFields` shall `state.rows` 数 ≤ 10（`MAX_CUSTOM_FIELDS`）
   の前提で O(n) diff を許容する。早期最適化（HashMap 等）を必須としない
2. The diff 描画 shall 1 emit あたりの処理を 16ms 以内（1 フレーム以内）に
   完了させ、ユーザー入力に体感的な遅延を発生させない

### NFR 2: 後方互換性

1. The fix shall `CustomFieldsState` / `CustomFieldsState.Row` の data class
   形状を変更しない
2. The fix shall `addCustomFieldRow` / `removeCustomFieldRow` /
   `updateCustomFieldKey` / `updateCustomFieldValue` の signature と挙動を
   変更しない
3. The fix shall `R.layout.view_custom_field_row` の view 階層・id を変更
   しない（行 view 内の id は既存 `R.id.input_field_key` /
   `R.id.input_field_value` / `R.id.btn_remove_row` をそのまま参照する）
4. The fix shall `viewModel.customFields` StateFlow の emit セマンティクスを
   変更しない（Activity 側の受信処理を変えるのみ）

### NFR 3: ログ規約

1. The implementation shall customField の `fieldKey` / `value` をログに
   出力しない（Phase 1 NFR 2 / `SafeLogger` 規約を踏襲）
2. The implementation shall diff 結果（追加・削除された rowId）を平文ログに
   残さない。デバッグ用途であれば件数のような無害な集計情報のみ許容する

### NFR 4: 一貫性

1. The fix shall `Mode.New` / `Mode.Edit` で同一の描画コードパスを通す
   （描画戦略をモードで分岐させない）
2. The fix shall `view.setTag(int, Object)` の id 採番方針を既存 Issue #43 の
   `icon_loader_request_tag` と同じ pattern（`res/values/ids.xml` に明示宣言）
   に従う

## 7. テスト要件

§5 Requirement 5 を主とする。加えて以下を満たすこと。

- Phase 1 / Phase 1.5 / Phase 2 で導入された既存 ViewModel テストを再実行し、
  すべて green であることを確認する
- focus 保持テストは「emit のたびに full rebuild が走らないこと」を間接的に
  検証する（例: 入力中の `EditText` 参照が再 emit 後も同一インスタンスを指す
  ことのアサーション）。具体的なアサーション手段は design.md / 実装フェーズで
  確定する
- 行追加・削除テストでは「対象外行の `EditText` インスタンスが再 emit 前後で
  同一であること」「対象外行の編集中テキストが保持されること」を検証する

## 8. 確認事項（Open Questions）

Issue 本文の「確認事項」セクション 4 件はいずれもコメントで明確な追加決定が
されていないため、本要件書では Issue 本文の方針をそのまま採用する。

### Q1. `rowId` の View tag マッピング（要件本体に反映済み）

`View.setTag(R.id.custom_field_row_id, rowId)` で行 view に rowId を保持する。
`res/values/ids.xml` に `custom_field_row_id` を新規追加し、既存 Issue #43 で
導入された `icon_loader_request_tag` と同じ pattern とする。

→ Requirement 1 / NFR 4.2 / §2 スコープに反映済み。

### Q2. 再 render の最小化 — diff の計算量

`state.rows` は最大 10 件に capped されているため、emit のたびに container
内の全 view を走査して O(n) diff を取れば十分。HashMap 化等の早期最適化は
不要。

→ NFR 1 に反映済み。

### Q3. TextWatcher 一時 detach pattern

`setText()` の前に `editText.removeTextChangedListener(watcher)` →
`setText` → `editText.addTextChangedListener(watcher)` で re-entrancy を
防ぐ。watcher インスタンスを view に保持する必要がある（タグまたは
プロパティ）。

→ Requirement 3.1 / 3.3 に反映済み。具体的な watcher 保持手段は design.md /
実装フェーズで確定する（残作業）。

### Q4. PR #74 (Issue #73 Phase 1.5) との merge 順序

本 Issue は PR #74 マージ後に着手する。Phase 1.5 でも同じ
`renderCustomFields()` を通るため、どちらの順でも整合する想定だが、現時点で
PR #74 はマージ済みのため、本 Issue 着手時点でブランチには Phase 1.5 が
含まれている。

→ §1 影響範囲 / §2 Out of Scope に反映済み。残作業なし。

### 未解決の Open Question

- 行 view と TextWatcher インスタンスの紐付け手段の具体（タグ id 追加 vs
  view の `getTag()` Map vs `WeakHashMap`）。Requirement 3.3 で抽象的に
  明文化済みで、設計フェーズで確定する

## 9. 関連 Issue / PR

- **Phase 1 (Issue #66 / PR #69 merged)**: customField 初期実装。本 bug
  を混入した PR。`renderCustomFields` の full rebuild を意図的に採用したが、
  TextWatcher との組み合わせで focus loss する副作用が見落とされた
- **Phase 1.5 (Issue #73 / PR #74 merged)**: `Mode.Edit` で customField 編集
  を解放。本 Issue が解消する focus loss の影響範囲が拡大したため、本 Issue
  を急ぐ動機となった
- **Phase 2 (Issue #67 / PR #72 merged)**: detected_fields サジェスト UI。
  chip タップで fieldKey 入力欄に転送する設計のため、本 bug の影響を直接
  受ける。本 Issue 修正後に転送後の連続入力が正常に行えるようになる
- **Issue #43 (merged)**: `res/values/ids.xml` で `icon_loader_request_tag`
  を導入した先例。本 Issue で追加する `custom_field_row_id` も同じ pattern
  を採用する

## 10. 制約

1. The Issue Implementation shall 既存テスト（unit / instrumented を含む）
   を一切 fail させない
2. The Issue Implementation shall `develop` / `main` ブランチに直接 push
   しない（feature branch + PR レビュー経由）
3. The requirements.md shall 実装コードを含まない。クラス名・メソッド名への
   言及は既存実装の同定に必要な最小限のみとする
4. The Issue Implementation shall `CustomFieldsState` / `Row` の data class
   形状および reducer 群の signature を変更しない（NFR 2.1 / 2.2）
5. The Issue Implementation shall `R.layout.view_custom_field_row` の view
   階層・id を変更しない（NFR 2.3）
6. The Issue Implementation shall Phase 2 (#67) detection / suggestion 経路の
   コードを変更しない（前提条件の保持のみ）
7. The Issue Implementation shall RecyclerView 化を含む大規模リファクタを
   行わない（最小修正で focus loss を解消することを目的とする）
