# Implementation Notes — Issue #73 Phase 1.5

Phase 1.5 (#73) を実装した際の実装メモ。レビュー時の判断材料として残す。

## 実装概要

Phase 1.5 のスコープは以下 2 点:

1. **`Mode.Edit` で customField を編集可能にする** — Phase 1 の暫定挙動
   (`editable = false`) を解消し、`load()` 時に `EncryptedCustomFieldsCodec`
   で復号して `CustomFieldsState.rows` に展開する。
2. **`Mode.Edit` で既存 password を masking 状態で表示し、focus toggle で
   平文 / masking を切り替え可能にする。** dirty 判定で未編集なら既存
   ciphertext を温存し、変更されていれば新値で update する。

実装は MVVM + UseCase の既存パターンを維持し、新規 use case は導入しない。
ViewModel に `EncryptedCustomFieldsCodec` / `AesGcmCipher` を直接 inject
する Factory 拡張のみ。

## 採用した方針

### customField / password 復号レイヤ

- design.md §代替案検討で採用案 A を選択: ViewModel が codec / cipher を
  直接呼び出す。新規 use case (`LoadCredentialForEditUseCase` 等) は導入しない。
- 復号失敗時は `_state.value = State.Error(cause = "decrypt_credential")`
  を emit し、`CustomFieldsState(rows = emptyList(), editable = false)` +
  `EditState.initialPassword = null` に fall back。Activity は閉じない
  (Req 4.5 / 7.3)。

### State 設計

- 既存の `State` sealed class (Idle / Saving / Saved / FieldError / Error)
  は変更せず、新規 `EditState(initialPassword: String?)` を並列の
  `MutableStateFlow<EditState>` として導入。
- `EditState` は Mode.Edit でのみ load 時に値が入る。Mode.New では
  `EditState.Empty` (= `initialPassword = null`) のまま運用される。

### password dirty 判定

- `save()` 内で以下の三分岐を実装:
  1. `existingId != null` かつ `password.isEmpty()` → `State.FieldError(Password, Blank)`
     を emit して save 中断 (Req 6.3)。
  2. `existingId != null` かつ `password.contentEquals(initialPassword.toCharArray())`
     → `newPassword = null` (温存)。
  3. それ以外 (Mode.New または Mode.Edit で password 変更あり) → `newPassword = password`。
- Phase 1 の `if (password.isEmpty()) null else password` 分岐は撤去。
  Mode.Edit では「空欄 = 温存」が「`initialPassword` 一致 = 温存」に
  置き換わる (NFR 2.4 セマンティクス維持)。
- 比較完了後、`initialPassword.toCharArray()` で確保した一時 CharArray
  は best-effort で zero-fill (NFR 1.4)。

### Activity 側の password UI 配線

- `editingId != null` (Mode.Edit) のときのみ:
  - `binding.layoutPassword.endIconMode = TextInputLayout.END_ICON_NONE`
    で `password_toggle` endIcon を抑止し、focus toggle と競合させない
    (§9 Q3 採用案 A)。
  - `EditState.initialPassword` が初めて非 null になった瞬間に
    `setText(...)` + masking + `setOnFocusChangeListener` を 1 回だけ
    実行する。再 emit で setText が連打されないよう `passwordInitialized`
    boolean guard を導入。
  - 初期 focus 抑制は `binding.inputLabel.requestFocus()` で実現
    (`android:windowSoftInputMode` を変更しない最小手段)。
- Mode.New では layout 既定値 `password_toggle` の挙動を完全に温存
  (NFR 5.2)。

### `(optional)` hint 削除

- Issue 本文に従い、Activity 側で `binding.layoutPassword.hint = ... + " (optional)"`
  を append していたコードを削除。layout XML 側は元から `(optional)` を
  含んでおらず、変更不要。

## 変更ファイル一覧

### main ソース

- `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditViewModel.kt`
  - primary constructor に `customFieldsCodec` / `aesGcmCipher` を追加
  - `EditState(initialPassword: String?)` + `_editState` / `editState` を追加
  - `load()` 内に decrypt 処理を追加 (customField + password を同一 try で
    処理し、失敗時は両方 fall back)
  - `save()` に password dirty 判定 + blank validation を追加
  - `Factory` クラスに codec / cipher パラメータ追加
- `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditActivity.kt`
  - `ServiceLocator.encryptedCustomFieldsCodec` / `ServiceLocator.aesGcmCipher`
    を Factory に渡す
  - Mode.Edit 時の layout 設定 (`endIconMode = END_ICON_NONE` /
    `binding.inputLabel.requestFocus()`) を追加
  - `renderEditStatePassword(state)` を新設し、`viewModel.editState`
    を collect (Mode.Edit でのみ subscribe)
  - 旧 `(optional)` hint suffix を削除
  - `passwordInitialized` boolean guard を追加

### test ソース

- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditViewModelEditModeCustomFieldsTest.kt` **(新規)**
  - 6 cases: decrypt 展開 / 空 ciphertext / reducer 動作 / save / round-trip / decrypt 失敗
- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditViewModelEditModePasswordTest.kt` **(新規)**
  - 6 cases: decrypt / 未変更 → null / 変更 → CharArray / 空 → FieldError /
    round-trip / decrypt 失敗
- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditActivityPasswordFocusTest.kt` **(新規, Robolectric)**
  - 4 cases: 初期 masking + non-focused / focus gain → plain / focus loss → mask / カーソル保持
- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/CredentialEditViewModelTest.kt` (修正)
  - ViewModel コンストラクタ helper に codec / cipher を追加
  - `save_inEditMode_dispatchesToUpdateUseCase_andUpdatesSignature` で
    password 引数を空 → `"pw2"` に変更 (Phase 1.5 では空 = blank validation)
- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/CredentialEditViewModelCustomFieldsTest.kt` (修正)
  - ViewModel コンストラクタ helper に codec / cipher を追加
  - `load_existingCredential_marksCustomFieldsSectionReadOnly` →
    `load_existingCredential_marksCustomFieldsSectionEditable_andLoadsDecryptedRows`
    にリネームし、assertion を `editable=true` 系へ更新
- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditViewModelSuggestionTest.kt` (修正)
  - ViewModel コンストラクタ helper に codec / cipher を追加
  - `suggestions_hiddenWhenCustomFieldsNotEditable` →
    `suggestions_appearInEditMode_afterPhase1_5` にリネーム。
    Mode.Edit でも editable=true になることで chip strip が表示される
    挙動を pin
- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditAdvancedStateTest.kt` (修正)
  - ViewModel コンストラクタ helper に codec / cipher を追加

### docs

- `docs/specs/73-feat-edit-mode-edit-customfield-phase-1/tasks.md` —
  各タスクの完了マーカー (`- [x]`) を更新
- `docs/specs/73-feat-edit-mode-edit-customfield-phase-1/impl-notes.md` (本ファイル)

### Task 1 (DI) について

ServiceLocator は Phase 1 の時点で既に `aesGcmCipher` (line 76) と
`encryptedCustomFieldsCodec` (line 85) を `val` として expose していたため、
ServiceLocator.kt 自体への変更は不要だった。Task 1 は「現状の expose で十分」
を確認した上で `tasks.md` のチェックボックスのみ flip する形でコミットした。

## テスト結果

`./gradlew :app:test` 実行結果:

```
BUILD SUCCESSFUL in 1m 21s
66 actionable tasks: 37 executed, 29 up-to-date
```

`testDebugUnitTest` レポート:

- **Tests**: 618
- **Failed**: 0
- **Ignored**: 0
- **Duration**: 22.153s

`testReleaseUnitTest` も成功。

新規追加した 16 テストケース内訳:

- `CredentialEditViewModelEditModeCustomFieldsTest`: 6
- `CredentialEditViewModelEditModePasswordTest`: 6
- `CredentialEditActivityPasswordFocusTest`: 4

## 確認事項

レビュー時に意思決定の妥当性を確認していただきたい点:

### 1. Activity の `renderEditStatePassword` 内 listener を別関数に切り出さなかった件

`Activity.renderEditStatePassword` 内に focus listener のラムダを直接書いている。
test 側 (`CredentialEditActivityPasswordFocusTest`) は Activity の listener body
を verbatim でコピーしてミラーする方式を採った (Phase 2 `AutofillEnableActivityVisibilityTest`
と同じ pattern)。listener 本体を `companion object internal fun` に切り出す案も
あったが、cursor 取得 / `setSelection` / `transformationMethod` の 3 つの
binding 参照が絡むため切り出すとシグネチャが冗長になり、結局 Activity 内
インラインのほうが読みやすかった。Reviewer がそれでも切り出し希望なら別 PR で
対応する。

### 2. `EditState` を `State` sealed class に統合せず並列 StateFlow にした件

design.md §Components and Interfaces 部に「`EditState`（または現存する
`State.Loaded` 系プロパティ）」とあり選択を委ねられていた。現状 `State`
sealed class は Idle/Saving/Saved/FieldError/Error のいずれも payload を持た
ない (`FieldError` / `Error` は cause/field を持つが load 結果は持たない) ため、
`State` を data-bearing にリファクタすると影響範囲が大きくなる。最小変更原則
で並列の `MutableStateFlow<EditState>` を採用した。

### 3. `save()` で Mode.Edit 空 password を即 FieldError 化した件

design.md §Components and Interfaces の擬似コードでは validation 経路を
詳細化していない。Phase 1.5 では「Mode.Edit で空 = ユーザーが意図的に
field を空にした = blank 不可」と解釈し、`State.FieldError(Field.Password, ErrorKind.Blank)`
を emit して save 中断とした (Req 6.3)。

Phase 1 の挙動 (`if (password.isEmpty()) null else password`) は「空 = 温存」
を意味していたが、Phase 1.5 では既存 password が field に表示されるため
ユーザーが意図的に空にしたなら blank validation エラーが妥当という解釈。
これは `save_inEditMode_dispatchesToUpdateUseCase_andUpdatesSignature`
test の挙動変更を引き起こしたため、当該 test の入力を空 → `"pw2"` に
変更した。

### 4. `initialPassword: String?` の zero-fill 限界

NFR 1.4 で「password CharArray の zero-fill ベストエフォート」を要求。
`EditState.initialPassword: String` は Kotlin/JVM の immutable String 仕様
により、heap 上の char[] への参照を断つことしかできない (zero-fill 不能)。
`String` 化を強要する経路 (`EditText.text.toString()` / `String(bytes, UTF_8)`)
が現実的に残るのは要件側でも許容されている (Req 5.6 / NFR 1.4 "可能な
限り")。`save()` 内では `toCharArray()` で取った一時 CharArray を
`Arrays.fill(' ')` で wipe する best-effort 実装を採用した。`String` 自体は
ViewModel destroy 時の GC に依存する。

### 5. `nextRowId` カウンタを load 時にもインクリメントする件

design.md §Components and Interfaces 「`nextRowId` は ViewModel に持つ既存
の単調増加カウンタを流用し、ロード行に対しても新規発番する」に従い、load
直後の `decrypted.map { CustomFieldsState.Row(rowId = nextRowId++, ...) }`
で既存行にも新規 rowId を割り当てている。ロード行と新規追加行は ID 体系上
区別されない (未決事項 §10 Q5 への回答)。
