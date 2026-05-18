# Review Notes — Issue #77 (Round 1)

## Summary

Developer は `renderCustomFields()` の full rebuild を、rowId キー付き
view tag による idempotent diff renderer (`CustomFieldsRowsRenderer`) に
置換した。差分の取得は OK:

- `git diff --stat develop..HEAD`: 6 files, +1097 / -37
  - `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditActivity.kt` (-37/+42)
  - `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/edit/CustomFieldsRowsRenderer.kt` (新規 +263)
  - `app/src/main/res/values/ids.xml` (+15)
  - `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/edit/CustomFieldsRowsRendererTest.kt` (新規 +264)
  - `docs/specs/77-bug-edit-customfield-1-focus-rendercusto/impl-notes.md` (新規)
  - `docs/specs/77-bug-edit-customfield-1-focus-rendercusto/requirements.md` (新規)
- 修正は描画層に限定されており、`CustomFieldsState` / `Row` / reducer の
  signature には変更がない（NFR 2.1〜2.2 / 制約 4 を遵守）。
- `view_custom_field_row` レイアウト・id は変更されていない（NFR 2.3 / 制約 5）。
- detected_fields / suggestion 経路は触られていない（NFR 6.3 / 制約 6）。
- Robolectric SDK 33 で 4 件の focus / view-identity テストが新規追加され、
  要件 5.1〜5.4 と直接対応している。
- 既存テストへの影響は impl-notes.md で `:app:testDebugUnitTest` 全 green と
  明記されている（reducer 未変更のため整合する）。

注: spec ディレクトリには `tasks.md` / `design.md` が存在しないため、
本レビューは requirements.md と差分実装の突き合わせのみで判定する。
`_Boundary:_` アノテーションは tasks.md 不在のため明示的判定対象なし。

## AC カバレッジ

requirements.md §5 の Requirement 1〜6 を AC とみなして個別判定する。

### Requirement 1: idempotent diff 描画

- 1.1 既存 rowId の保持: **covered**
  `CustomFieldsRowsRenderer.render` ステップ 1 (`existing` HashMap 構築) と
  ステップ 2 (`cached != null` ブランチ, `CustomFieldsRowsRenderer.kt:139-189`)。
- 1.2 新規 rowId の inflate + addView: **covered**
  `cached == null` ブランチ (`CustomFieldsRowsRenderer.kt:141-155`) で
  `inflate` → `attachWatchers` → `setTag(custom_field_row_id, rowId)` →
  `container.addView(fresh, desiredIndex)`。
- 1.3 削除 rowId の removeView: **covered**
  ステップ 1 の逆順 walk (`CustomFieldsRowsRenderer.kt:123-130`) で
  `desiredRowIds` に含まれない子だけ `removeViewAt`。
- 1.4 並び順 `state.rows` 順: **covered**
  新規行は `addView(fresh, desiredIndex)`、既存行は currentIndex と
  desiredIndex の不一致時に `removeViewAt` → `addView` で位置調整
  (`CustomFieldsRowsRenderer.kt:183-187`)。
- 1.5 `state.editable == false` 時に行 0 件 + 既存ボタン挙動維持: **covered**
  renderer 側 `if (!state.editable) { container.removeAllViews(); return }`
  (`CustomFieldsRowsRenderer.kt:95-102`) + Activity 側 `btnAddCustomField` /
  `tvCustomFieldsReadonlyNote` visibility は従来通り
  (`CredentialEditActivity.kt:442-445`)。

### Requirement 2: focus 保持

- 2.1 / 2.2 / 2.3: **covered**
  既存 rowId の view を再利用し setText を呼ばないため EditText が
  destroy されず focus / IME が保持される。テスト
  `typingIntoFieldKey_preservesEditTextIdentityAndFocus` /
  `typingIntoFieldValue_preservesEditTextIdentityAndFocus`
  (`CustomFieldsRowsRendererTest.kt:85-133`) が `isSameInstanceAs` +
  `isFocused` で検証。
- 2.4 既存 EditText に setText を呼ばない: **covered**
  `syncIfDiverged` (`CustomFieldsRowsRenderer.kt:242-253`) の早期 return
  (`if (current == desired) return`) で no-op fast path 化されている。
  通常 emit (TextWatcher 経由) では current == desired となるので
  setText は走らない。

### Requirement 3: TextWatcher re-entrancy 対策

- 3.1 detach → setText → re-attach: **covered**
  `syncIfDiverged` が `removeTextChangedListener` → `setText` →
  `addTextChangedListener` の 3 段操作を実装している
  (`CustomFieldsRowsRenderer.kt:249-252`)。
- 3.2 dispatch ループ防止: **covered**
  既存 simpleWatcher と同じ匿名 TextWatcher 形状 (`forwardingWatcher`,
  `CustomFieldsRowsRenderer.kt:255-262`) で onTextChanged のみ転送し、
  setText 時は watcher 自身が外されているので無限ループは起きない。
- 3.3 watcher を view に紐付けて保持: **covered**
  EditText の `setTag(R.id.custom_field_key_watcher, watcher)` /
  `setTag(R.id.custom_field_value_watcher, watcher)` で保持
  (`CustomFieldsRowsRenderer.kt:222-223`)。tag id は `ids.xml` に宣言
  (`ids.xml` 差分参照)。

### Requirement 4: 行追加・削除の副作用なし

- 4.1 行追加: **covered**
  既存行は rowId 一致で reuse、新規行のみ addView。テスト
  `addingRowToThreeRowState_preservesExistingRowsFocusAndIdentity`
  (`CustomFieldsRowsRendererTest.kt:140-168`) が中央行 EditText の
  same-instance + text + isFocused を検証。
- 4.2 行削除: **covered**
  対象 rowId のみ `removeViewAt`、他行は保持。テスト
  `removingMiddleRow_preservesSurroundingRowsFocusAndIdentity`
  (`CustomFieldsRowsRendererTest.kt:176-205`) が 1 番目・3 番目の
  EditText について same-instance + text + isFocused を検証。
- 4.3 reducer 未変更: **covered**
  `git diff develop..HEAD -- CredentialEditViewModel.kt` は変更ファイル
  一覧に含まれていない。`addCustomFieldRow` /
  `removeCustomFieldRow` / `updateCustomFieldKey` /
  `updateCustomFieldValue` のシグネチャは保持されている。

### Requirement 5: テスト

- 5.1 fieldKey "abcdef" focus 保持: **covered**
  `typingIntoFieldKey_preservesEditTextIdentityAndFocus`
  (`CustomFieldsRowsRendererTest.kt:85-110`)。
- 5.2 value "12345" focus 保持: **covered**
  `typingIntoFieldValue_preservesEditTextIdentityAndFocus`
  (`CustomFieldsRowsRendererTest.kt:115-133`)。
- 5.3 3 行 → 4 行 追加で他行保持: **covered**
  `addingRowToThreeRowState_preservesExistingRowsFocusAndIdentity`
  (`CustomFieldsRowsRendererTest.kt:140-168`)。
- 5.4 3 行 middle 削除で他行保持: **covered**
  `removingMiddleRow_preservesSurroundingRowsFocusAndIdentity`
  (`CustomFieldsRowsRendererTest.kt:176-205`)。
- 5.5 既存テスト fail なし: **covered**（impl-notes に green 確認の記載あり、
  reducer 非変更なので reducer テスト群への影響なし）。
- 5.6 Robolectric / Espresso / Activity 単体テスト いずれかで実現: **covered**
  Robolectric SDK 33 (`@Config(sdk = [33])`) で実装、
  `CredentialEditActivityPasswordFocusTest` と同じ pattern。

### Requirement 6: Phase 1.5 整合性

- 6.1 Mode.New / Mode.Edit で同一挙動: **covered**
  `renderCustomFields` 単一エントリ + 単一 renderer 呼び出し
  (`CredentialEditActivity.kt:432-446`)。モード分岐なし。
- 6.2 `state.editable == false` 挙動不変: **covered**
  renderer 早期 return + Activity 側 visibility ロジック未変更
  (`CredentialEditActivity.kt:442-445`)。
- 6.3 Phase 2 (#67) detected_fields コード不変: **covered**
  差分に detected_fields / suggestion 関連ファイルは含まれない。

### NFR カバレッジ（参考、judgment scope 外だが整合性確認）

- NFR 1 性能: O(n) diff (`existing` HashMap 1 個 + 線形 walk 2 本)。問題なし。
- NFR 2 後方互換: data class / reducer signature / layout id 不変。OK。
- NFR 3 ログ規約: 新規ログ追記なし、fieldKey / value をログ化しない。OK。
- NFR 4.2 tag id pattern: `ids.xml` に `custom_field_row_id` /
  `custom_field_key_watcher` / `custom_field_value_watcher` を XML 宣言。
  Issue #43 `icon_loader_request_tag` と同じ pattern。OK。

## Findings

判定スコープ (AC 未カバー / missing test / boundary 逸脱) における問題は
**検出されなかった**。

- AC 未カバー: なし。Requirement 1〜6 / 5.1〜5.4 はいずれも実装または
  テストで明確にカバーされている。
- missing test: なし。requirement 5.1〜5.4 が
  `CustomFieldsRowsRendererTest` の 4 テストで 1:1 対応している。
- boundary 逸脱: なし。`tasks.md` 不在で `_Boundary:_` 明示的指定は
  ないが、requirements.md の §2 「Out of Scope」(RecyclerView 化、
  reducer signature 変更、layout 変更、Phase 2 経路変更等) に違反する
  変更は差分に含まれていない。

RESULT: approve
