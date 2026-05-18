# Implementation Plan

Issue #73 (Phase 1.5) を独立にコミット可能な粒度で実装するためのタスク分割。
順序: 1 → 2 → 3 → 4 → 5 / 6 / 7（テスト系は並列可）。

- [x] 1. DI: `EncryptedCustomFieldsCodec` および `AesGcmCipher` を ViewModel から取得可能にする
  - `ServiceLocator` に `encryptedCustomFieldsCodec: EncryptedCustomFieldsCodec` および
    `aesGcmCipher: AesGcmCipher` のシングルトン参照を expose
    （既に Phase 1 で生成済みなら参照可能化のみ）
  - codec / cipher のインスタンスは `UpdateCredentialUseCase` / `UnlockVaultUseCase` /
    `SaveCredentialUseCase` と **同一** にし、複数生成を防ぐ
  - 既存 callers（use case 群）の挙動・参照経路を変えない
  - _Requirements: 1.1, 4.1, 7.2_

- [x] 2. `CredentialEditViewModel` の Factory / コンストラクタに codec / cipher を inject
  - `CredentialEditViewModel` の primary constructor に
    `customFieldsCodec: EncryptedCustomFieldsCodec` および `aesGcmCipher: AesGcmCipher` を追加
  - `Factory` クラスにも同パラメータを追加し、`create()` で ViewModel に引き渡す
  - `CredentialEditActivity.viewModels { Factory(...) }` 呼び出しに
    `ServiceLocator.encryptedCustomFieldsCodec` および `ServiceLocator.aesGcmCipher` を渡す
  - 既存テスト（`CredentialEditViewModelTest` / `CredentialEditViewModelCustomFieldsTest` /
    `CredentialEditViewModelSuggestionTest`）の ViewModel 生成 helper にも同引数を追加し、
    既存挙動を壊さないこと
  - ViewModel の `EditState`（または現存する `State.Loaded` 系プロパティ）に
    **`initialPassword: String?`** フィールドを追加（既定値 `null`、Mode.New では常に `null` のまま）
  - _Requirements: 1.1, 4.1, 7.2_
  - _Depends: 1_

- [x] 3. `CredentialEditViewModel.load()` 内で customField と password を復号して State に展開
  - `record` が non-null な場合、`customFieldsCodec.decrypt(EncryptedBlob(iv = record.customFieldsIv, ciphertext = record.customFieldsCiphertext))`
    を呼び、結果を `CustomFieldsState.Row(rowId = nextRowId++, fieldKey = it.fieldKey, value = it.value)`
    に map して `CustomFieldsState(rows = ..., editable = true)` を emit
  - 同じ try ブロックで `aesGcmCipher.decrypt(EncryptedBlob(iv = record.passwordIv, ciphertext = record.passwordCiphertext))`
    を呼び、`String(decryptedBytes, Charsets.UTF_8)` を `_state.value.initialPassword` にセット
  - 空 BLOB ケースは codec が空 list を返すため、結果として `rows = [] / editable = true` で正常パスに乗る（Req 1.5）
  - decrypt が throw した場合 catch して `_state.value = State.Error(cause = "decrypt_credential")` を emit、
    `CustomFieldsState(rows = emptyList(), editable = false)` + `initialPassword = null` を維持し、
    Activity を閉じない
  - 既存 `bindPackageForSuggestions(record.packageName)` 呼び出しはそのまま残す（Req 9.3）
  - 既存 reducer (`addCustomFieldRow` / `removeCustomFieldRow` / `updateCustomFieldKey` /
    `updateCustomFieldValue`) のロジック本体は **変更しない**（NFR 5.1）
  - `save()` の customField 三項コメントを「Phase 1.5 で edit mode も editable=true になり、
    null パスは事実上到達しない」に更新
  - `save()` に password dirty 判定を追加:
    - `typedPassword.contentEquals(initialPassword.toCharArray())` ならば `newPassword = null`
    - 一致しないなら `newPassword = typedPassword.toCharArray()` で渡す
    - typedPassword が空文字なら既存の blank validation error を emit して save 中断
    - 比較完了後、可能な限り CharArray を zero-fill する（既存 `UpdateCredentialUseCase` の
      zero-fill ポリシーと整合）
  - `SafeLogger.info("edit mode loaded (customFields=N, password=loaded)")` / 失敗時
    `SafeLogger.error(throwable = ex)` のみ（平文ログ禁止、Req 8.1-8.3）
  - _Requirements: 1.1, 1.2, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.5, 4.6, 6.1, 6.2, 6.3, 6.4, 6.5, 7.2, 7.3, 7.4, 8.1, 8.2, 8.3, 9.3_
  - _Depends: 2_

- [x] 4. `CredentialEditActivity` に password 初期表示 / focus toggle / endIconMode 切替を配線
  - `viewModel.state` を collect し、`initialPassword != null` が **初めて成立した瞬間** に
    `binding.inputPassword.setText(initialPassword)` を 1 回だけ呼ぶ（`var passwordInitialized = false`
    等の guard でガード）
  - setText 直後に
    `binding.inputPassword.transformationMethod = PasswordTransformationMethod.getInstance()`
    で masking 初期化
  - Mode.Edit のみ `binding.passwordLayout.endIconMode = TextInputLayout.END_ICON_NONE` に切り替える
    （Q3 採用案 A）。Mode.New では layout 既定値 `password_toggle` を維持
  - Mode.Edit のみ `binding.inputPassword.setOnFocusChangeListener { _, hasFocus ->
        val s = binding.inputPassword.selectionStart
        val e = binding.inputPassword.selectionEnd
        binding.inputPassword.transformationMethod =
            if (hasFocus) null else PasswordTransformationMethod.getInstance()
        binding.inputPassword.setSelection(s, e)
    }` を配線
  - 画面遷移直後に `inputPassword` に自動 focus が当たらないよう、`label` 入力欄を初期 focus に
    振る（`binding.inputLabel.requestFocus()` 等）または Activity の `android:windowSoftInputMode`
    を適切に設定する
  - `renderCustomFields` / `binding.btnAddCustomField` 周りは既存実装で `editable` を尊重して
    いるため **本体ロジック変更なし**
  - `credential_edit_activity.xml` の password 欄 hint から `(optional)` 表記を削除
  - listener 内で `SafeLogger` を呼ばない（masking 状態をログに残さない、Req 8.4）
  - 既存 biometric / device credential unlock 経路（編集画面遷移までに通過する unlock パス）は
    **一切変更しない**（Req 7.1）
  - _Requirements: 1.3, 1.4, 2.6, 4.2, 4.3, 4.4, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 7.1, 8.4_
  - _Depends: 3_

- [x] 5. `CredentialEditViewModelEditModeCustomFieldsTest` を新規追加（並列可） (P)
  - 新規ファイル `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditViewModelEditModeCustomFieldsTest.kt`
  - test cases:
    - `loadInEditMode_decryptsAndExposesRows` — 2 件の customField を含む record で load → rows.size == 2 / editable == true
    - `loadInEditMode_emptyCiphertext_yieldsEditableEmptyRows` — 空 BLOB → rows = [] / editable = true
    - `editModeReducers_addRemoveUpdate_workEndToEnd` — load 後 reducer 群の動作確認
    - `editModeSave_passesEditedListToUpdateUseCase` — fake update use case を spy し、`UpdateCredentialInput.customFields` が編集後リストであることを assert
    - `editModeRoundTrip_preservesCustomFieldsValuesSemantically` — load → 無編集 save → 再 load で同集合
    - `editModeDecryptFailure_emitsErrorAndKeepsLocked` — codec.decrypt が throw する stub → State.Error + rows = [] / editable = false
  - `FakeCredentialRepository` / `StubAesGcmCipher` / `EncryptedCustomFieldsCodec` を Phase 1 / 2 既存テストと同じヘルパで構築
  - _Requirements: 1.1, 1.2, 1.5, 2.1, 2.2, 2.3, 2.4, 3.1, 3.5, 7.3, 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_
  - _Boundary: CredentialEditViewModel, EncryptedCustomFieldsCodec_
  - _Depends: 3_

- [ ] 6. `CredentialEditViewModelEditModePasswordTest` を新規追加（並列可） (P)
  - 新規ファイル `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditViewModelEditModePasswordTest.kt`
  - test cases:
    - `loadInEditMode_decryptsPasswordToInitialPassword` — `EncryptedCredentialRecord` で load →
      `state.value.initialPassword == decryptedPlain`
    - `editModeSave_unchangedPassword_passesNullToUpdateInput` — typedPassword を `initialPassword`
      と同値のまま save → `UpdateCredentialInput.newPassword == null`
    - `editModeSave_changedPassword_passesNewCharArrayToUpdateInput` — typedPassword を変更 →
      `UpdateCredentialInput.newPassword?.concatToString() == changed`
    - `editModeSave_blankPassword_emitsValidationErrorAndAborts` — typedPassword を空文字 →
      `State.FieldError(password)` emit + save 中断
    - `editModeRoundTrip_preservesPasswordSemantically` — load → 無編集 save → 再 load で同 plaintext
    - `editModeDecryptFailure_emitsErrorAndKeepsPasswordNull` — cipher.decrypt が throw →
      `State.Error` + `initialPassword == null`
  - `StubAesGcmCipher` を Phase 1 / 2 既存テストと同じヘルパで構築
  - _Requirements: 4.1, 4.5, 4.6, 6.1, 6.2, 6.3, 6.4, 6.5, 10.5, 10.6, 10.7, 10.8, 10.9, 10.10_
  - _Boundary: CredentialEditViewModel, AesGcmCipher_
  - _Depends: 3_

- [ ] 7. `CredentialEditActivityPasswordFocusTest` (UI test) を新規追加（並列可） (P)
  - 新規ファイル `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditActivityPasswordFocusTest.kt`
    （Robolectric を使う場合は `app/src/test/.../CredentialEditActivityPasswordFocusTest.kt` も可）
  - 既存プロジェクトの UI テスト規約（Espresso / Robolectric / Compose Test の採用状況）に従って
    フレームワークを選定する
  - test cases:
    - `focusGain_appliesPlaintextMode` — `inputPassword.requestFocus()` →
      `transformationMethod == null`
    - `focusLoss_appliesMaskingMode` — focus を別 field に移す →
      `transformationMethod is PasswordTransformationMethod`
    - `toggle_preservesCursorPosition` — selection を中央に設定 → focus 切替 → selection が保持される
    - `initialDisplay_isMaskedAndNotFocused` — Activity 起動直後 `inputPassword` の
      `transformationMethod is PasswordTransformationMethod` かつ `hasFocus == false`
  - _Requirements: 4.3, 4.4, 5.1, 5.2, 5.3, 10.11_
  - _Boundary: CredentialEditActivity_
  - _Depends: 4_

- [ ] 8. 既存テスト群の非破壊性回帰確認（並列可） (P)
  - `./gradlew :app:test` で以下が **全件 pass** することを確認:
    - `CredentialEditViewModelTest` / `CredentialEditViewModelCustomFieldsTest` / `CredentialEditViewModelSuggestionTest`
    - `UpdateCredentialUseCaseTest` / `SaveCredentialUseCaseCustomFieldsTest` / `UnlockVaultUseCaseCustomFieldsTest`
    - `EncryptedCustomFieldsCodecTest`
    - `Migration_1_2_Test` / `Migration_2_3_Test`
  - 特に Phase 2 `CredentialEditViewModelSuggestionTest` の Mode.Edit を仮定したケースが
    本 Issue で `editable = true` に変化することで挙動が変わる場合、テスト側の前提を見直す
    （ロジックは変えず、テスト仕様を Phase 1.5 後の挙動に追従させるのみ）
  - 失敗があれば Task 3 / 4 / 5 / 6 / 7 のロジックを修正し、再実行
  - _Requirements: 9.1, 9.2, 10.12_
  - _Boundary: CredentialEditViewModelTest, CredentialEditViewModelCustomFieldsTest, CredentialEditViewModelSuggestionTest_
  - _Depends: 3, 4_
