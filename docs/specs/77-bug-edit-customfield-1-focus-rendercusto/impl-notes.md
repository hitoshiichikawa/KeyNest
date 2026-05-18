# Implementation Notes — Issue #77

`renderCustomFields` の full rebuild を rowId ベースの idempotent diff
描画へ切り替え、customField 入力欄の連続入力で focus が失われる bug
(#77) を解消した実装メモ。レビュー時の判断材料として残す。

## 実装概要

問題の核は `CredentialEditActivity.renderCustomFields()` が
`viewModel.customFields` StateFlow の emit ごとに
`container.removeAllViews()` で全行 view を破棄して再 inflate していた
こと。TextWatcher が user input を ViewModel に転送 → StateFlow が
emit → Activity が全行を再生成 → user input 中の EditText も破棄、
というループが 1 文字ごとに走り、focus / IME が中断していた。

修正は描画層の最小修正に留め、`CustomFieldsState` / reducer の
シグネチャや `view_custom_field_row` 内部 id は一切変更していない
(NFR 2.1〜2.3 / 制約 4・5)。

## 採用した方針

### 描画戦略: rowId キーの idempotent diff

`CustomFieldsRowsRenderer` という `object` (internal) を新規追加し、
Activity から
`CustomFieldsRowsRenderer.render(container, inflater, state, callbacks)`
で 1 回呼び出す形にした。renderer は以下 2 ステップで container を
desired state に揃える:

1. **不要 view の eviction**: container 子要素を逆順で walk し、
   tag (R.id.custom_field_row_id) に紐付いた rowId が
   `state.rows` に含まれなければ `removeViewAt(i)` で除去。逆順
   なので index が走査中にずれない。
2. **desired 順での配置**: `state.rows.withIndex()` を順に walk し、
   - cached view がなければ inflate して watcher 取り付け +
     desired index に `addView`
   - cached view があれば再利用。値が ViewModel と乖離していれば
     `syncIfDiverged` で watcher 一時 detach → setText → re-attach。
     desired index と現在 index が異なれば
     `removeViewAt(currentIndex)` → `addView(desiredIndex)` で reorder。

eviction を先に行うのは、reorder 経路に focus 中の行が巻き込まれて
detach + re-attach されると Robolectric / 実機 ともに focus が
silently 失われるため (テスト 5.4 が当初 fail した直接原因)。
`CredentialEditViewModel` の現行 reducer は既存行を入れ替えないので
reorder 分岐は実質 dead path だが、将来の reorder 追加に対しても
safe な順序を選んだ。

### rowId / TextWatcher の保持手段

要件 §8 残作業の Open Question (watcher インスタンス保持手段) は
**view tag** を採用。`res/values/ids.xml` に以下 3 件を新規追加し
Issue #43 の `icon_loader_request_tag` と同じ pattern (XML 宣言 +
コード側で `setTag(R.id.xxx, value)` / `getTag(R.id.xxx)`) で運用する:

| id | 用途 |
|---|---|
| `custom_field_row_id` | row 全体 view → rowId (Long) のマッピング |
| `custom_field_key_watcher` | fieldKey EditText に attach 中の TextWatcher 参照 |
| `custom_field_value_watcher` | value EditText に attach 中の TextWatcher 参照 |

`WeakHashMap` 案も検討したが、(a) renderer が `object` で
プロセス全体に 1 インスタンスしか存在せず、Activity 寿命を超えて
保持される懸念があること、(b) 既存 Issue #43 の patten に揃えるのが
NFR 4.2 の要請でもあること、から view tag を採用した。

### TextWatcher 再エントランシー対策

通常の emit 経路では「user が EditText に入力 → watcher → reducer →
StateFlow emit」の順なので、EditText の current text は既に
`state.row.{fieldKey,value}` と一致しており、`syncIfDiverged` は
no-op fast path に落ちる (Requirement 2.4)。

ViewModel reducer が将来値を正規化するなどして乖離が発生した場合
だけ、detach → setText → re-attach を行う (Requirement 3.1)。
detach に使う watcher 参照は view tag (`custom_field_key_watcher` /
`custom_field_value_watcher`) から取得する (Requirement 3.3)。

### Activity 側 wiring

`renderCustomFields` は以下にスリム化した:

- `CustomFieldsRowsRenderer.render(...)` を 1 回呼ぶ
- add ボタンの visibility / isEnabled、read-only note の visibility
  は renderer 外にあるので Activity が引き続き直接制御

renderer に渡す `Callbacks` インスタンスは Activity フィールドに
保持し emit 毎に再生成しない。watcher は rowId を lambda capture で
保持しており Callbacks の identity 比較は不要なので、Callbacks の
インスタンスを stable に保つこと自体に強制力はないが、Activity の
emit 毎に object literal を生成する微小 garbage を避ける目的で
property にした。

`state.editable == false` のときは renderer 側で
`removeAllViews()` して early return する (要件 1.5 / 6.2 を遵守)。
Phase 1.5 (#73) マージ済みの現在、この分岐は decrypt 失敗の
fall-back でしか踏まれない。

## 変更ファイル一覧

### main ソース

- `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/edit/CustomFieldsRowsRenderer.kt`
  - **新規追加。** diff 描画の本体。
- `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditActivity.kt`
  - `renderCustomFields()` を renderer 呼び出しへ置換。
  - `customFieldCallbacks` プロパティを追加。
  - 不要になった `TextInputEditText` import を削除。
- `app/src/main/res/values/ids.xml`
  - `custom_field_row_id` / `custom_field_key_watcher` /
    `custom_field_value_watcher` を追加。

### test ソース

- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/edit/CustomFieldsRowsRendererTest.kt`
  - **新規追加。** Robolectric (SDK 33) 上で要件 5.1〜5.4 を検証する
    4 テスト:
    - `typingIntoFieldKey_preservesEditTextIdentityAndFocus` (5.1)
    - `typingIntoFieldValue_preservesEditTextIdentityAndFocus` (5.2)
    - `addingRowToThreeRowState_preservesExistingRowsFocusAndIdentity` (5.3)
    - `removingMiddleRow_preservesSurroundingRowsFocusAndIdentity` (5.4)
  - `FakeCustomFieldsHolder` で `CredentialEditViewModel` の
    customField slice (addRow / removeRow / updateKey / updateValue +
    monotonic rowId 採番) を mirror し、ViewModel + UseCase + DB の
    重い依存無しに renderer 単体を駆動する。
  - 既存 `CredentialEditActivityPasswordFocusTest` と同じ手法
    (`CredentialEditActivityBinding.inflate` で binding を取得し、
    Activity 起動はしない) を踏襲した。

## 既存テストとの相互作用

- 既存 `CredentialEditViewModelCustomFieldsTest` /
  `CredentialEditViewModelEditModeCustomFieldsTest` /
  `CredentialEditViewModelSuggestionTest` 等は ViewModel reducer の
  挙動のみ検証しており、本 PR では reducer を一切触っていないので
  すべて green のまま (`./gradlew :app:testDebugUnitTest` で確認済)。
- 既存 `CredentialEditActivityPasswordFocusTest` は password フィールド
  のみを対象としており、customField wiring とは独立。

## 動作確認方法 (手動確認手順)

実機 / エミュレータでの確認手順:

1. Mode.New の場合:
   1. 認証情報ハブ → 新規追加で `CredentialEditActivity` を開く。
   2. 「フィールド追加」をタップして customField 行を 1 件追加。
   3. fieldKey の入力欄をタップし、`abcdef` を連続入力する。
      → focus が外れず、ソフトキーボードも引っ込まないこと、
        入力欄に `abcdef` が表示されることを確認。
   4. value の入力欄にも `12345` を連続入力。同様に focus 保持を確認。
   5. もう 1 件「フィールド追加」をタップ。先に編集した行の値と
      focus 状態がそのまま残ること。
   6. 中間の行の「×」ボタンをタップ。残り行が破壊されないこと。
2. Mode.Edit (既存クレデンシャル編集) の場合:
   1. 既存クレデンシャルを開く。
   2. 既に保存されている customField があれば編集して連続入力を試す。
3. detected_fields chip 経由:
   1. chip をタップして fieldKey を転送した直後に、続けて文字を入力
      しても focus / IME が保持されること。

## 確認事項 (Open Questions / 未決)

特になし。要件 §8 の「未解決の Open Question」(watcher インスタンスの
保持手段) は本実装で view tag に確定した。

## 既知の制約

- `state.editable == false` 経路では renderer は container を空にする
  だけで、現状 (Phase 1) と完全に同じ挙動。Phase 1.5 (#73) で
  Mode.Edit でも `editable = true` が基本になったため、現在この分岐
  は decrypt 失敗 fall-back のみで踏まれる。
- renderer は `state.rows` 数が `MAX_CUSTOM_FIELDS = 10` で cap される
  前提で O(n) diff (HashMap 1 個 + 線形 walk 2 本) を採用 (NFR 1)。
- lint は本 PR とは無関係の既存エラー (PackageSignatureResolver.kt
  の NewApi など 100 件) で fail するが、`:app:assembleDebug` と
  `:app:testDebugUnitTest` は green。本 Issue は描画層の最小修正で
  あり既存 lint には触らない。
- androidTest (instrumentation) は追加していない。Robolectric 上の
  単体テストで focus / view identity / IME を間接検証する方針が
  既存の `CredentialEditActivityPasswordFocusTest` と一致しており、
  新規テスト基盤を追加しない方が制約に沿うため (要件 5.6 の幅広い
  選択肢の中から Robolectric を採用)。

## ビルド / テスト結果

ローカル (Linux + JDK 17 + Android SDK):

| コマンド | 結果 |
|---|---|
| `./gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew :app:testDebugUnitTest` | BUILD SUCCESSFUL (全テスト green) |
| `./gradlew :app:testDebugUnitTest --tests "*CustomFieldsRowsRendererTest"` | 4 tests passed |
| `./gradlew :app:lintDebug` | 既存エラー 100 件で失敗 (本 PR と無関係) |
