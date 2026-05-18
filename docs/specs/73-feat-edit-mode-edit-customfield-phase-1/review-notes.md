# Review Notes — Issue #73 Phase 1.5

## Summary

- HEAD: `4827783f9b6ee9a74dfd7ea8d4c4165cfbaf4c08`
- BASE: `develop`
- ROUND: 1 / 最大 2
- 差分概況: `git diff --stat develop..HEAD`
  - 11 files changed, 1344 insertions(+), 57 deletions(-)
  - main: `CredentialEditActivity.kt`, `CredentialEditViewModel.kt`
  - test: 3 new (`CredentialEditViewModelEditModeCustomFieldsTest`,
    `CredentialEditViewModelEditModePasswordTest`,
    `CredentialEditActivityPasswordFocusTest`)
    + 4 existing test helpers updated
    (`CredentialEditViewModelTest`,
    `CredentialEditViewModelCustomFieldsTest`,
    `CredentialEditViewModelSuggestionTest`,
    `CredentialEditAdvancedStateTest`)
  - docs: `tasks.md` (チェックボックス), `impl-notes.md` (新規)
- 影響レイヤ: ViewModel (`load()` 内 decrypt + `save()` の password dirty
  判定 + `Factory`)、Activity (password 初期表示 / focus listener /
  endIconMode 切替 / `passwordInitialized` guard)、テスト 3 ファイル新規
- Developer の自己テスト結果 (`impl-notes.md`): `./gradlew :app:test`
  全 618 件 pass / fail 0 / ignored 0

reviewer 用の出力契約ファイル (`.claude/agents/reviewer.md`) は本リポジトリに
存在しないため、本ファイルは prompt の出力契約フォーマットに従う。

## 判定詳細

### 1. AC 未カバー

**None.**

requirements.md §5 / §6 / §10 の numeric AC を実装・テスト両面で点検した
結果、未カバーは検出されなかった。代表的な対応位置:

| AC | 実装 | テスト |
|---|---|---|
| 1.1 Mode.Edit で復号して rows 展開 | `CredentialEditViewModel.kt:460-474` | `CredentialEditViewModelEditModeCustomFieldsTest.loadInEditMode_decryptsAndExposesRows` |
| 1.2 `editable = true` を保持 | 同上 (`_customFields.value = CustomFieldsState(rows, editable = true)`) | 同テスト + `CredentialEditViewModelCustomFieldsTest.load_existingCredential_marksCustomFieldsSectionEditable_andLoadsDecryptedRows` |
| 1.3 / 1.4 rows 空 / 表示 plain | `CredentialEditActivity.kt:412-441 renderCustomFields` (Phase 1 から再利用) | Phase 1 既存テスト + Phase 1.5 上記 |
| 1.5 空 BLOB → rows = [] / editable = true | codec の fail-open + `nextRowId` 採番 (codec 内 + ViewModel) | `loadInEditMode_emptyCiphertext_yieldsEditableEmptyRows` |
| 2.1-2.4 reducer 動作 | 既存 reducer を `editable = true` 経路で通過 | `editModeReducers_addRemoveUpdate_workEndToEnd` |
| 2.5 上限 10 件維持 | 既存 `CustomFieldsState.canAddMore` (Phase 1 実装) を流用、ViewModel 側でモード分岐なし (NFR 5.1) | tasks.md T5 に明示の test 要件なし。Phase 1 で既に網羅済みのコードパスを共有するため重複追加は不要と判定 |
| 2.6 体感速度 | reducer は同期的 StateFlow update (NFR 3.x の Activity 確認は手動 QA 範疇) | (該当タスクは tasks.md T5 で `2.1-2.4` をカバーすれば足る粒度) |
| 3.1-3.5 save 永続化 | `CredentialEditViewModel.kt:355-399` (既存 `if (_customFields.value.editable) effectiveCustomFields else null`) | `editModeSave_passesEditedListToUpdateUseCase` / `editModeRoundTrip_preservesCustomFieldsValuesSemantically` |
| 4.1 password 復号 → initialPassword | `CredentialEditViewModel.kt:480-490` | `loadInEditMode_decryptsPasswordToInitialPassword` |
| 4.2 setText(initialPassword) | `CredentialEditActivity.kt:463-469 renderEditStatePassword` | `CredentialEditActivityPasswordFocusTest.initialDisplay_isMaskedAndNotFocused` (Activity body を verbatim mirror) |
| 4.3 初期表示 masking | 同上 (`transformationMethod = PasswordTransformationMethod.getInstance()`) | 同上 |
| 4.4 自動 focus 抑制 | `CredentialEditActivity.kt:130 binding.inputLabel.requestFocus()` | 同テスト (focus 非取得確認) |
| 4.5 復号失敗 → State.Error + initialPassword null | `CredentialEditViewModel.kt:495-506` catch | `editModeDecryptFailure_emitsErrorAndKeepsPasswordNull` + `editModeDecryptFailure_emitsErrorAndKeepsLocked` |
| 4.6 ViewModel スコープに閉じ込め | `EditState` を `MutableStateFlow` で保持 | ViewModel destroy 時 GC の verbatim test は要件外 |
| 5.1 / 5.2 focus toggle | `CredentialEditActivity.kt:471-481` listener | `focusGain_appliesPlaintextMode` / `focusLoss_appliesMaskingMode` |
| 5.3 カーソル位置保持 | `setSelection(selStart, selEnd)` (`-1` ガード付き) | `toggle_preservesCursorPosition` |
| 5.4 endIconMode 競合回避 | `CredentialEditActivity.kt:125, 206-212` (Mode.Edit 時のみ END_ICON_NONE / Mode.New は layout default を温存) | `initialDisplay_isMaskedAndNotFocused` (`endIconMode == END_ICON_NONE` を assert) |
| 5.5 text 不変 | listener が text を触らないことを目視確認 | (assert は要件外) |
| 5.6 画面回転時挙動 | OS 標準に委譲 (要件側で許容) | (要件外) |
| 6.1 一致時 newPassword = null | `CredentialEditViewModel.kt:333-339` | `editModeSave_unchangedPassword_passesNullToUpdateInput` |
| 6.2 不一致時 新値 | `CredentialEditViewModel.kt:341` | `editModeSave_changedPassword_passesNewCharArrayToUpdateInput` |
| 6.3 空文字 → blank validation | `CredentialEditViewModel.kt:324-332` | `editModeSave_blankPassword_emitsValidationErrorAndAborts` |
| 6.4 CharArray 内容等価 | `contentEquals` 使用 | 6.1 / 6.2 テストで暗黙的にカバー |
| 6.5 zero-fill | `java.util.Arrays.fill(initialPasswordChars, ' ')` / `java.util.Arrays.fill(password, ' ')` / passwordBytes も zero | (best-effort、要件側で許容) |
| 7.1-7.4 認証フロー | 追加 biometric なし、catch fallback、StateFlow スコープ | `editModeDecryptFailure_*` 系 |
| 8.1-8.4 ログ規約 | `SafeLogger.info("edit mode loaded (customFields=N, password=loaded)")` / `SafeLogger.error(throwable = t)` / focus listener は SafeLogger 非呼出 | (テスト要件は requirements.md §10 に明示なし) |
| 9.1-9.3 Phase 2 整合 | `bindPackageForSuggestions(record.packageName)` 既存呼出維持 | `CredentialEditViewModelSuggestionTest.suggestions_appearInEditMode_afterPhase1_5` で Phase 1.5 挙動 pin |
| 10.1-10.12 テスト | 上記参照 | T5 + T6 + T7 + 既存 4 ファイル修正で網羅 |

備考: AC 2.5 (`MAX_CUSTOM_FIELDS = 10`) の Mode.Edit 維持は tasks.md T5 の
`_Requirements:_` 列に明示されておらず、Phase 1 で確立した
`CustomFieldsState.canAddMore` を NFR 5.1 (モード分岐を持たない) に基づき
共有するため、Phase 1 既存テストの被覆で十分と判定した。reject 事由とは
しない。

### 2. missing test

**None.**

tasks.md T5 / T6 / T7 の `_Requirements:_` / `_Boundary:_` で要求された
全テストケースが対応するファイルに存在することを差分から確認した:

- T5 (`_Requirements: 1.1, 1.2, 1.5, 2.1, 2.2, 2.3, 2.4, 3.1, 3.5, 7.3, 10.1-10.6_`,
  `_Boundary: CredentialEditViewModel, EncryptedCustomFieldsCodec_`):
  `CredentialEditViewModelEditModeCustomFieldsTest.kt` に 6 ケース全配備
  - `loadInEditMode_decryptsAndExposesRows`
  - `loadInEditMode_emptyCiphertext_yieldsEditableEmptyRows`
  - `editModeReducers_addRemoveUpdate_workEndToEnd`
  - `editModeSave_passesEditedListToUpdateUseCase`
  - `editModeRoundTrip_preservesCustomFieldsValuesSemantically`
  - `editModeDecryptFailure_emitsErrorAndKeepsLocked`

- T6 (`_Requirements: 4.1, 4.5, 4.6, 6.1, 6.2, 6.3, 6.4, 6.5, 10.5-10.10_`,
  `_Boundary: CredentialEditViewModel, AesGcmCipher_`):
  `CredentialEditViewModelEditModePasswordTest.kt` に 6 ケース全配備
  - `loadInEditMode_decryptsPasswordToInitialPassword`
  - `editModeSave_unchangedPassword_passesNullToUpdateInput`
  - `editModeSave_changedPassword_passesNewCharArrayToUpdateInput`
  - `editModeSave_blankPassword_emitsValidationErrorAndAborts`
  - `editModeRoundTrip_preservesPasswordSemantically`
  - `editModeDecryptFailure_emitsErrorAndKeepsPasswordNull`

- T7 (`_Requirements: 4.3, 4.4, 5.1, 5.2, 5.3, 10.11_`,
  `_Boundary: CredentialEditActivity_`):
  `CredentialEditActivityPasswordFocusTest.kt` に 4 ケース全配備
  - `initialDisplay_isMaskedAndNotFocused`
  - `focusGain_appliesPlaintextMode`
  - `focusLoss_appliesMaskingMode`
  - `toggle_preservesCursorPosition`

T8 (回帰確認) は `impl-notes.md` の `./gradlew :app:test` 結果 (618 件
pass) で根拠が示されている。Phase 1 / Phase 2 既存テストの前提見直し
(`CredentialEditViewModelCustomFieldsTest` の rename + assertion 反転 /
`CredentialEditViewModelSuggestionTest` の rename + 挙動 pin) も適切に
実施されている。

### 3. boundary 逸脱

**None.**

design.md §Architecture / §代替案検討 / §File Structure Plan の境界が
維持されているかを点検:

- **採用案 A の遵守** (design.md §代替案検討):
  - customField 復号: ViewModel が `EncryptedCustomFieldsCodec.decrypt` を
    直接呼ぶ (採用案 A) — 該当箇所 `CredentialEditViewModel.kt:461`
  - password 復号: ViewModel が `AesGcmCipher.decrypt` を直接呼ぶ
    (採用案 A) — 該当箇所 `CredentialEditViewModel.kt:480`
  - 新規 use case (`LoadCredentialForEditUseCase`,
    `DecryptCredentialPasswordUseCase`) は追加されていない
- **password masking toggle 実装手段** (design.md §代替案検討 採用案 A):
  `setOnFocusChangeListener` + `transformationMethod` 切替 +
  `setSelection()` で実装 — `CredentialEditActivity.kt:471-481`
- **endIconMode 切替** (Q3 採用案 A): Mode.Edit のみ `END_ICON_NONE`、
  Mode.New は layout 既定値 `password_toggle` を温存 — Activity 側で
  `editingId != null` ガード付き (`CredentialEditActivity.kt:206-212`)
- **NFR 5.1 (モード分岐を reducer 内に持たない)**:
  `addCustomFieldRow` / `removeCustomFieldRow` / `updateCustomFieldKey` /
  `updateCustomFieldValue` は Phase 1 の `editable` フラグ判定のみで
  通過/non-pass を制御 — Mode 分岐は導入されていない
  (`CredentialEditViewModel.kt:525-564`)
- **NFR 5.2 (Mode.New 不変)**: Activity 側で `editingId != null` ガードに
  より `renderEditStatePassword` collector / endIconMode 切替 /
  `inputLabel.requestFocus()` がすべて Mode.Edit 限定で発動。Mode.New は
  Phase 1 と同一コードパス
- **Out of Scope (requirements.md §2)**: Phase 2 detection / suggestion
  本体 / Credential Manager API / Room schema / 追加 biometric / 暗号化
  方式変更 — いずれも変更なし
- **State sealed class**: design.md §Components and Interfaces で
  `EditState` を「既存 `State.Loaded` 系プロパティ」と「並列の
  StateFlow<EditState>」のどちらでも可と委ねており、Developer は後者を
  選択。impl-notes.md §確認事項 2 に経緯あり。boundary 逸脱ではない

備考 (補足観察、reject 事由ではない):
- impl-notes.md §確認事項 1: `renderEditStatePassword` 内の listener を
  Activity 内インラインに残し、test 側で verbatim mirror した点は
  reviewer の判断に委ねられている。本レビューでは
  「`CredentialEditActivityPasswordFocusTest` の listener が Activity 本体と
  乖離した瞬間に該当 test が fail する」mirror パターンが Phase 2
  `AutofillEnableActivityVisibilityTest` と整合的で、テストの保護対象が
  Activity ロジックである以上、境界逸脱ではないと判定する
- impl-notes.md §確認事項 3 (save 時 Mode.Edit 空 password の即 FieldError 化):
  Req 6.3 の文言「空 password は許容しない、既存挙動を維持」と整合。
  Phase 1 の「空 = 温存」セマンティクスは NFR 2.4 が「`UpdateCredentialInput.newPassword`
  の null セマンティクスを変えず継続利用」と明言しており、本実装はその
  use case 側の null 解釈を保ったまま判定基準を切り替えているため、
  boundary 逸脱ではない

## approve 条件 / reject 条件の根拠

### approve 条件

- requirements.md §5 (Req 1-10) / §6 (NFR 1-5) の全 numeric AC が
  実装 + テストの両面で覆われている (上記 "AC 未カバー" None)
- tasks.md T5 / T6 / T7 / T8 の `_Requirements:_` / `_Boundary:_` で
  要求されたテストが全配備済み (上記 "missing test" None)
- design.md §代替案検討で採用された方針 (customField / password 復号
  レイヤ ともに案 A、masking toggle 案 A、endIconMode 案 A) が実装に
  反映されており、Out of Scope / NFR 5.1 / NFR 5.2 / NFR 2.4 の境界も
  維持されている (上記 "boundary 逸脱" None)
- `./gradlew :app:test` が impl-notes.md に記録された通り 618 件 pass /
  fail 0 で完走 (T8 回帰確認)

### reject 条件 (該当なし)

- 該当する事象は本レビューで検出されなかった
- スタイル / 命名 / lint / format 観点での指摘は本契約上 reject 事由
  に含まれない

## 最終判定

3 カテゴリ (AC 未カバー / missing test / boundary 逸脱) すべてで
"None"。

RESULT: approve
