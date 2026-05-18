# Implementation Plan

Issue #73 (Phase 1.5) を独立にコミット可能な粒度で実装するためのタスク分割。
順序: 1 → 2 → 3 → 4 / 5（テスト系は並列可）。

- [ ] 1. DI: `EncryptedCustomFieldsCodec` を ViewModel から取得可能にする
  - `ServiceLocator` に `encryptedCustomFieldsCodec: EncryptedCustomFieldsCodec` シングルトンを
    expose（既に Phase 1 で生成済みなら参照可能化のみ）
  - codec のインスタンスは `UpdateCredentialUseCase` / `UnlockVaultUseCase` と **同一** にし、
    複数生成を防ぐ
  - 既存 callers（use case 群）の挙動・参照経路を変えない
  - _Requirements: 1.1, 4.2_

- [ ] 2. `CredentialEditViewModel` の Factory / コンストラクタに codec を inject
  - `CredentialEditViewModel` の primary constructor に
    `customFieldsCodec: EncryptedCustomFieldsCodec` を追加
  - `Factory` クラスにも同パラメータを追加し、`create()` で ViewModel に引き渡す
  - `CredentialEditActivity.viewModels { Factory(...) }` 呼び出しに
    `ServiceLocator.encryptedCustomFieldsCodec` を渡す
  - 既存テスト（`CredentialEditViewModelTest` / `CredentialEditViewModelCustomFieldsTest` /
    `CredentialEditViewModelSuggestionTest`）の ViewModel 生成 helper にも同引数を追加し、
    既存挙動を壊さないこと
  - _Requirements: 1.1, 4.2_
  - _Depends: 1_

- [ ] 3. `CredentialEditViewModel.load()` 内で customField を復号して `CustomFieldsState` に展開
  - `record` が non-null な場合、`customFieldsCodec.decrypt(EncryptedBlob(iv = record.customFieldsIv, ciphertext = record.customFieldsCiphertext))`
    を呼び、結果を `CustomFieldsState.Row(rowId = nextRowId++, fieldKey = it.fieldKey, value = it.value)`
    に map して `CustomFieldsState(rows = ..., editable = true)` を emit
  - 空 BLOB ケースは codec が空 list を返すため、結果として `rows = [] / editable = true` で正常パスに乗る（Req 1.5）
  - decrypt が throw した場合 catch して `_state.value = State.Error(cause = "decrypt_custom_fields")` を emit、
    `CustomFieldsState(rows = emptyList(), editable = false)` を維持し、Activity を閉じない
  - 既存 `bindPackageForSuggestions(record.packageName)` 呼び出しはそのまま残す（Req 6.3）
  - 既存 reducer (`addCustomFieldRow` / `removeCustomFieldRow` / `updateCustomFieldKey` /
    `updateCustomFieldValue`) と `save()` のロジック本体は **変更しない**（NFR 5）
  - `save()` 内コメントを「Phase 1.5 で edit mode も editable=true になり、null パスは事実上到達しない」に更新
  - `SafeLogger.info("edit mode customFields loaded (count=N)")` / 失敗時 `SafeLogger.error(throwable = ex)` のみ
    （平文ログ禁止、Req 5.1-5.3）
  - _Requirements: 1.1, 1.2, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4, 3.5, 4.2, 4.3, 4.4, 5.1, 5.2, 5.3, 6.3_
  - _Depends: 2_

- [ ] 4. `CredentialEditViewModelEditModeCustomFieldsTest` を新規追加（並列可） (P)
  - 新規ファイル `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditViewModelEditModeCustomFieldsTest.kt`
  - test cases:
    - `loadInEditMode_decryptsAndExposesRows` — 2 件の customField を含む record で load → rows.size == 2 / editable == true
    - `loadInEditMode_emptyCiphertext_yieldsEditableEmptyRows` — 空 BLOB → rows = [] / editable = true
    - `editModeReducers_addRemoveUpdate_workEndToEnd` — load 後 reducer 群の動作確認
    - `editModeSave_passesEditedListToUpdateUseCase` — fake update use case を spy し、`UpdateCredentialInput.customFields` が編集後リストであることを assert
    - `editModeRoundTrip_preservesValuesSemantically` — load → 無編集 save → 再 load で同集合
    - `editModeDecryptFailure_emitsErrorAndKeepsLocked` — codec.decrypt が throw する stub → State.Error + rows = [] / editable = false
  - `FakeCredentialRepository` / `StubAesGcmCipher` / `EncryptedCustomFieldsCodec` を Phase 1 / 2 既存テストと同じヘルパで構築
  - _Requirements: 1.1, 1.2, 1.5, 2.1, 2.2, 2.3, 2.4, 3.1, 3.5, 4.3, 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_
  - _Boundary: CredentialEditViewModel, EncryptedCustomFieldsCodec_
  - _Depends: 3_

- [ ] 5. 既存テスト群の非破壊性回帰確認（並列可） (P)
  - `./gradlew :app:test` で以下が **全件 pass** することを確認:
    - `CredentialEditViewModelTest` / `CredentialEditViewModelCustomFieldsTest` / `CredentialEditViewModelSuggestionTest`
    - `UpdateCredentialUseCaseTest` / `SaveCredentialUseCaseCustomFieldsTest` / `UnlockVaultUseCaseCustomFieldsTest`
    - `EncryptedCustomFieldsCodecTest`
    - `Migration_1_2_Test` / `Migration_2_3_Test`
  - 特に Phase 2 `CredentialEditViewModelSuggestionTest` の Mode.Edit を仮定したケースが
    本 Issue で `editable = true` に変化することで挙動が変わる場合、テスト側の前提を見直す
    （ロジックは変えず、テスト仕様を Phase 1.5 後の挙動に追従させるのみ）
  - 失敗があれば Task 3 / 4 のロジックを修正し、再実行
  - _Requirements: 6.1, 6.2, 7.7_
  - _Boundary: CredentialEditViewModelTest, CredentialEditViewModelCustomFieldsTest, CredentialEditViewModelSuggestionTest_
  - _Depends: 3_
