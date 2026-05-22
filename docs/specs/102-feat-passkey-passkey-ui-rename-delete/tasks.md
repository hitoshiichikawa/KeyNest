# Implementation Plan — Issue #102 / feat(passkey): PassKey 単位の個別管理 UI (rename / delete)

> 関連: `requirements.md`, `design.md`（本ディレクトリ）
>
> 各タスクは独立コミット可能な粒度で、依存順に並べている。Developer はこの順番で実装する。
>
> 略号:
> - **R**: `requirements.md` の Requirement / Acceptance Criteria 番号 (例: R1.4)
> - **NFR**: `requirements.md` の Non-Functional Requirement 番号
> - 設計詳細は `design.md` の対応セクション (`§N.M`) を参照
>
> Conventional Commits の scope は **`feat(passkey-detail)`** を採用する
> (`#99` = `passkey-register` / `#100` = `passkey-auth` / `#101` = `passkey-list` と並列の命名)。
>
> 「PassKey」表記固定 (NFR 3) を全コミットメッセージ / KDoc / コメント / string resource で遵守する。

## 概要 / 前提

### 着手前に Developer が読むべき資料

1. `docs/specs/102-feat-passkey-passkey-ui-rename-delete/requirements.md` (本 Issue PM 成果物)
2. `docs/specs/102-feat-passkey-passkey-ui-rename-delete/design.md` (本ファイルと同ディレクトリ)
3. 依存 #91 (merged): `PasskeyEntity` / `PasskeyDao` の責務（特に `update` / `delete` メソッドの DAO 仕様）
4. 依存 #99 (merged): Keystore alias 命名規約 `keynest_passkey_<credentialId>`、`PasskeyRepositoryImpl` 構成
5. 依存 #100 (merged): `PasskeyRepositoryImpl` への `database: KeyNestDatabase` 注入 / `withTransaction` 利用パターン
6. 依存 #101 (merged 前提): `CredentialListItem.Passkey` variant / `PasskeyDisplayModel` / `CredentialListActivity` の `onItemClick(CredentialListItem) -> Unit` callback 形式 / `R.string.credential_list_passkey_tap_v1_message` の撤去対象
7. 既存コード:
   - `app/src/main/java/.../domain/repository/PasskeyRepository.kt`
   - `app/src/main/java/.../data/repository/PasskeyRepositoryImpl.kt`
   - `app/src/main/java/.../data/dao/PasskeyDao.kt`
   - `app/src/main/java/.../data/entity/PasskeyEntity.kt`
   - `app/src/main/java/.../ui/list/CredentialListActivity.kt`
   - `app/src/main/java/.../di/ServiceLocator.kt`
   - `app/src/main/AndroidManifest.xml`
   - 参考: `app/src/main/java/.../ui/edit/CredentialEditActivity.kt` (View + ViewModel パターン)
   - 参考: `app/src/main/res/layout/credential_edit_activity.xml` (Toolbar + TextInputLayout 構成)

### 前提依存

- **merged (本 Issue 着手時点で develop に含まれる前提)**:
  - #90 / #91 / #99 / #100 / #101
- 特に **#101 の `CredentialListItem.Passkey` variant / `onItemClick: (CredentialListItem) -> Unit` callback** が既に存在することが本 Issue の前提
- `PasskeyRepositoryImpl` のコンストラクタには `database: KeyNestDatabase` が既に注入されている（#100 で導入済）

### コミット運用

- T-01 〜 T-08 は **1 タスク = 1 コミット**を基本（T-04 / T-05 のような UI 関連 + テストペアは同コミット可）
- T-09 は QA / 動作確認 (commit なし or chore commit)
- 各コミット後に `./gradlew :app:compileDebugKotlin` 成功を確認してから次タスクへ進む
- 全タスク完了後 `./gradlew :app:testDebugUnitTest` + `./gradlew :app:lintDebug` で最終確認

### タスク依存関係図

```
T-01 (Repository: update API + delete を withTransaction 化) + テスト
   ↓
T-02 (UI 骨組み: PasskeyDetailUiState / PasskeyDetailUiEvent / PasskeyDetailViewModel)
   ↓
T-03 (strings.xml / values-ja: 21 keys 追加)
   ↓
T-04 (layout: passkey_detail_activity.xml)
   ↓
T-05 (PasskeyDetailActivity + AndroidManifest + 削除確認ダイアログ)
   ↓
T-06 (CredentialListActivity の Passkey 行タップ差し替え)
   ↓
T-07 (PasskeyDetailViewModelTest 新規)
   ↓
T-08 (Robolectric テスト: 削除確認ダイアログ文言 / 一覧→詳細 navigation)
   ↓
T-09 (QA checklist + 動作確認 + PR description 転記)
```

---

- [ ] 1. データ層 (Repository) を rename / delete 用に整備する
- [ ] 1.1 `PasskeyRepository.update(entity)` を interface に加法的追加 / `PasskeyRepositoryImpl` に実装
  - `domain/repository/PasskeyRepository.kt` に `suspend fun update(entity: PasskeyEntity)` を追加（既存 7 メソッドのシグネチャ不変）
  - `data/repository/PasskeyRepositoryImpl.kt` に `override suspend fun update(entity: PasskeyEntity) = dao.update(entity)` を実装
  - KDoc に「rename UI から呼ばれる / 呼び出し側が `copy(displayName = trimmed)` で他列を保持する責務」を明記（design §4.4）
  - _Requirements: 2.5, 2.10, 6.4_
  - _Boundary: PasskeyRepository, PasskeyRepositoryImpl_

- [ ] 1.2 `PasskeyRepositoryImpl.delete(credentialId)` を `withTransaction` で囲い直す
  - 現状の `dao.delete(...)` → `keyStore.deleteEntry(alias)` の 2 段呼び出しを `database.withTransaction { ... }` 内に内包
  - KeyStore.deleteEntry が `KeyStoreException` を投げると transaction 内で rethrow され Room が DB を rollback
  - 外側 try-catch で `KeyStoreException` を `DeletePasskeyResult.KeystoreCleanupFailed(cause)` に変換（戻り値型は変更しない / Req 6.4）
  - `containsAlias` ガード（alias が無いケースでも `Success` を返す）は維持
  - KDoc を更新: 「DB 行と Keystore alias の状態は両方削除 or 両方未削除に収束する (best-effort ではなく strong consistency)」（design §4.4 / §5.3）
  - _Requirements: 3.6, 3.7, 6.4_
  - _Boundary: PasskeyRepositoryImpl_

- [ ] 1.3 `PasskeyRepositoryTest` に update / delete rollback の検証ケースを追加
  - 既存 test file `app/src/test/java/.../data/PasskeyRepositoryTest.kt` を拡張
  - 新規ケース (a): `update_persistsEntity_andDoesNotChangeOtherFields` — `PasskeyEntity` の全 14 列を pre-update entity と比較し `displayName` のみ差分があることを assert
  - 新規ケース (b): `delete_returnsKeystoreCleanupFailed_andDbRowIsRestored_whenKeyStoreThrows` — fake `KeyStore` で `deleteEntry` が `KeyStoreException` を投げるとき、戻り値が `KeystoreCleanupFailed` かつ `dao.findByCredentialId(credentialId)` が non-null（= DB rollback された）ことを検証
  - 新規ケース (c): `delete_callOrder_isDbFirstThenKeystore` — DAO / KeyStore mock の call order を `inOrder` 系で verify（DB delete が KeyStore deleteEntry より先に呼ばれる）
  - 既存ケース `delete_returnsSuccess_*` の assertion を `withTransaction` 経由でも PASS することを再確認（必要なら assertion 強化）
  - _Requirements: 3.6, 3.7, 7.5, 7.6, 7.7_
  - _Boundary: PasskeyRepositoryImpl_
  - _Depends: 1.1, 1.2_

- [ ] 2. UI 状態モデル / ViewModel を整備する
- [ ] 2.1 `PasskeyDetailUiState` data class + `PasskeyDetailUiEvent` sealed interface を新規追加
  - `app/src/main/java/.../ui/passkey/PasskeyDetailUiState.kt` を新規作成 — design §4.1 「PasskeyDetailUiState」セクション通り (loadKind / entity / displayNameInput / displayNameError / inFlight + `LOADING` companion)
  - `app/src/main/java/.../ui/passkey/PasskeyDetailUiEvent.kt` を新規作成 — design §4.1 「PasskeyDetailUiEvent」セクション通り (Saved / Deleted / SaveFailed / DeleteFailed / NotFound)
  - KDoc で「entity 全体を保持する設計理由 (Req 2.10 / NFR 2.1 bind 側で守る)」を明記
  - _Requirements: 1.2, 1.10, 2.4, 2.9, 3.11_
  - _Boundary: PasskeyDetailUiState, PasskeyDetailUiEvent_

- [ ] 2.2 `PasskeyDetailViewModel` を新規追加
  - `app/src/main/java/.../ui/passkey/PasskeyDetailViewModel.kt` を新規作成
  - `class PasskeyDetailViewModel(private val repo: PasskeyRepository, private val credentialId: String) : ViewModel()`
  - private `_uiState: MutableStateFlow<PasskeyDetailUiState>` + public `uiState: StateFlow<...>` (`asStateFlow()`)
  - private `_uiEvents: Channel<PasskeyDetailUiEvent>` (`Channel.BUFFERED`) + public `uiEvents: Flow<...>` (`receiveAsFlow()`)
  - `init { load() }` で `viewModelScope.launch { repo.findByCredentialId(credentialId)?.let { ... } ?: send(NotFound) }`
  - `onSaveClicked(rawInput: String)` — trim → 空チェック → `inFlight` 設定 → `repo.update(entity.copy(displayName = trimmed))` → `Saved` / `SaveFailed` イベント
  - `onDeleteConfirmed()` — `inFlight` 設定 → `repo.delete(credentialId)` → `when (result) { Success -> Deleted ; KeystoreCleanupFailed -> DeleteFailed }` + catch `Throwable -> DeleteFailed`
  - `inFlight = true` 時の `onSaveClicked` / `onDeleteConfirmed` は no-op
  - `Factory(repo, credentialId): ViewModelProvider.Factory` を inner class として提供
  - `SafeLogger` を使う場合は `tag = "KeyNest.PasskeyDetail"` で credentialId raw を出さない (NFR 2.2)
  - _Requirements: 1.2, 1.10, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 3.5, 3.7, 3.8, 3.9, 3.10, 3.11, NFR 1.2, NFR 1.3, NFR 2.1, NFR 2.2_
  - _Boundary: PasskeyDetailViewModel_
  - _Depends: 1.1, 1.2, 2.1_

- [ ] 3. 文言 / レイアウトを整備する
- [ ] 3.1 `strings.xml` (en) / `strings.xml` (ja) に `passkey_detail_*` キー 21 件を追加
  - `app/src/main/res/values/strings.xml` と `app/src/main/res/values-ja/strings.xml` の両方に design §4.3 の string keys 表に従って追加
  - en / ja 両方で「PassKey」を固定表記とする (NFR 3.2)
  - `passkey_detail_delete_confirm_message` は en / ja 両方で「RP 側の登録は残ります」相当の文言を含むこと（Req 3.3 / 7.8）
  - _Requirements: 1.7, 1.8, 1.10, 2.1, 2.4, 2.6, 2.7, 3.1, 3.2, 3.3, 3.4, 3.8, 3.9, 5.1, 5.2, 5.3, NFR 3.1, NFR 3.2_
  - _Boundary: strings.xml_

- [ ] 3.2 `passkey_detail_activity.xml` を新規作成
  - `app/src/main/res/layout/passkey_detail_activity.xml` を新規追加
  - design §4.1 「View 構成」表の View IDs を実装 (`toolbar` / `text_rp_display_name` / `text_user_display_name` / `text_rp_id` / `text_created_at` / `text_last_used_at` / `input_display_name_layout` (TextInputLayout) / `input_display_name` (TextInputEditText) / `btn_save` (MaterialButton) / `btn_delete` (MaterialButton, `colorError` トーン))
  - 既存 `credential_edit_activity.xml` の Toolbar / TextInputLayout の使い方を踏襲（Material Components）
  - `userHandle` / `encryptedPrivateKey` / `privateKeyIv` / `keyAlias` / `signCount` / AAGUID を表示するための View を **作らない**（NFR 2.1）
  - all labels / hints は string resource 経由 (Req 5.2)
  - _Requirements: 1.3, 1.4, 1.5, 2.1, 2.2, 3.1, 3.4, 5.2_
  - _Boundary: passkey_detail_activity.xml_
  - _Depends: 3.1_

- [ ] 4. Activity を実装し manifest に登録する
- [ ] 4.1 `PasskeyDetailActivity` を新規追加 + `AndroidManifest.xml` に `<activity>` を登録
  - `app/src/main/java/.../ui/passkey/PasskeyDetailActivity.kt` を新規作成
  - `companion object { const val EXTRA_CREDENTIAL_ID; fun newIntent(context, credentialId): Intent }`
  - `onCreate` で `PasskeyDetailActivityBinding.inflate` / `setSupportActionBar(toolbar)` / `setDisplayHomeAsUpEnabled(true)` / Intent extra `credentialId` を取得
  - extra が null / 空なら `Snackbar(R.string.passkey_detail_not_found)` + `finish()` で早期終了
  - `viewModels { PasskeyDetailViewModel.Factory(ServiceLocator.passkeyRepository, credentialId) }` で VM 解決
  - `lifecycleScope.launch { repeatOnLifecycle(STARTED) { viewModel.uiState.collect { renderState(it) } } }` で State 観測
  - 別 launch で `viewModel.uiEvents.collect { handleEvent(it) }` で一過性イベント受信
  - `renderState(state)`: loadKind 別に view bind — `Loaded` のとき entity の 7 列のみを TextView / EditText に流す。`inFlight` が true なら save / delete ボタンを `isEnabled = false`
  - `btnSave.setOnClickListener { viewModel.onSaveClicked(inputDisplayName.text.toString()) }`
  - `btnDelete.setOnClickListener { showDeleteConfirmDialog() }`
  - `showDeleteConfirmDialog()` で `MaterialAlertDialogBuilder` を構築し、本文に `R.string.passkey_detail_delete_confirm_message`、positive button に `passkey_detail_delete_confirm_positive`、negative に `passkey_detail_delete_confirm_negative`
  - positive button 押下で `viewModel.onDeleteConfirmed()`
  - `handleEvent`: `Saved` / `Deleted` / `NotFound` で Snackbar 表示後 `finish()`、`SaveFailed` / `DeleteFailed` で Snackbar 表示のみ (画面維持)
  - `AndroidManifest.xml` に `<activity android:name=".ui.passkey.PasskeyDetailActivity" android:exported="false" android:parentActivityName=".ui.list.CredentialListActivity"/>` を追加
  - _Requirements: 1.1, 1.2, 1.3, 1.6, 1.7, 1.8, 1.9, 1.10, 2.1, 2.2, 2.3, 2.4, 2.6, 2.7, 2.9, 3.1, 3.2, 3.3, 3.4, 3.8, 3.9, 3.11, 4.5, 5.1, 5.2, NFR 1.1, NFR 2.1, NFR 3.1_
  - _Boundary: PasskeyDetailActivity, AndroidManifest.xml_
  - _Depends: 2.2, 3.2_

- [ ] 5. 一覧画面からの遷移経路を差し替える
- [ ] 5.1 `CredentialListActivity` の PassKey 行タップを `PasskeyDetailActivity` 起動に差し替え
  - `app/src/main/java/.../ui/list/CredentialListActivity.kt` の `setUpMainList` 内 `adapter = CredentialListAdapter(...)` の `onItemClick` callback で、`CredentialListItem.Passkey` 分岐を `startActivity(PasskeyDetailActivity.newIntent(this, item.passkey.credentialId))` に差し替え
  - `viewModel.onPasskeyClicked(...)` 呼び出しを撤去
  - `showPasskeyTapSnackbar()` メソッドを削除（呼び出し元が無くなる）
  - password 行 (`is CredentialListItem.Password`) 分岐は無変更で `startEdit(item.credential)` を維持 (Req 4.3)
  - `onItemLongClick` / `onOverflowClick` の Passkey 分岐は #101 の `Unit` のまま無変更 (Req 4.4)
  - `R.string.credential_list_passkey_tap_v1_message` は他に参照が無いため string resource からも削除（grep で参照箇所を確認）
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 6.2_
  - _Boundary: CredentialListActivity_
  - _Depends: 4.1_

- [ ] 6. テストを追加する
- [ ] 6.1 `PasskeyDetailViewModelTest` を新規追加 (P)
  - `app/src/test/java/.../ui/passkey/PasskeyDetailViewModelTest.kt` を新規作成
  - Robolectric `@RunWith(AndroidJUnit4::class)` + `@Config(sdk = [33])` (既存 ViewModel テストの慣習)
  - 各テストで `repo: PasskeyRepository = mockk()`、`coEvery { repo.findByCredentialId(...) } returns sampleEntity` で初期 load を制御
  - 検証ケース (design §7.1 表に従って 9 ケース実装):
    1. `load_emitsLoaded_whenRepoReturnsEntity`
    2. `load_emitsNotFoundEvent_whenRepoReturnsNull`
    3. `onSaveClicked_withBlankInput_setsDisplayNameError_andDoesNotCallUpdate`
    4. `onSaveClicked_withValidInput_callsUpdate_withOnlyDisplayNameChanged_andEmitsSavedEvent` — `update(capture(slot))` で slot.entity が `sampleEntity.copy(displayName = trimmed)` と完全一致することを assert
    5. `onSaveClicked_whenRepoThrows_emitsSaveFailedEvent_andClearsInFlight`
    6. `onDeleteConfirmed_whenRepoReturnsSuccess_emitsDeletedEvent`
    7. `onDeleteConfirmed_whenRepoReturnsKeystoreCleanupFailed_emitsDeleteFailedEvent_andDoesNotEmitDeleted`
    8. `onSaveClicked_whileInFlight_isNoOp`
    9. `onDeleteConfirmed_whileInFlight_isNoOp`
  - `kotlinx.coroutines.test.runTest` + `StandardTestDispatcher` でディスパッチャを制御
  - _Requirements: 1.2, 1.10, 2.4, 2.5, 2.7, 2.9, 2.10, 3.8, 3.9, 3.11, 7.1, 7.2, 7.3, 7.4_
  - _Boundary: PasskeyDetailViewModel_
  - _Depends: 2.2_

- [ ] 6.2 `PasskeyDetailActivityTest` (Robolectric) を新規追加 (P)
  - `app/src/test/java/.../ui/passkey/PasskeyDetailActivityTest.kt` を新規作成
  - Robolectric `@RunWith(AndroidJUnit4::class)` + `@Config(sdk = [33])`
  - 検証ケース:
    1. `deleteButton_showsConfirmDialog_withRpSideRetentionMessage` — Activity を `ActivityScenario.launch` で起動 → 削除ボタンを click → `ShadowAlertDialog.getLatestAlertDialog()` で表示されたダイアログを取得 → message TextView の text に `getString(R.string.passkey_detail_delete_confirm_message)` の文字列が含まれることを assert (Req 7.8)
    2. `notFoundIntent_finishesActivity_andShowsNotFoundSnackbar` — Intent extra なしで起動 → Activity が `isFinishing` を assert (Req 1.10)
    3. `saveButton_disabled_whileInFlight` — `inFlight` 状態の StateFlow を流して `binding.btnSave.isEnabled == false` を assert（Req 2.9）
  - `ServiceLocator.passkeyRepository` は test の `@Before` で mockk に差し替えるか、ActivityScenario を使う場合は `ServiceLocator.initialize` 経由で fake repository を流し込む
  - _Requirements: 1.10, 2.9, 3.3, 7.8_
  - _Boundary: PasskeyDetailActivity_
  - _Depends: 4.1_

- [ ] 6.3 `CredentialListActivityPasskeyNavigationTest` を新規追加 (P)
  - `app/src/androidTest/java/.../ui/list/CredentialListActivityPasskeyNavigationTest.kt` を新規作成 (Robolectric instrumentation テスト)
  - 検証ケース:
    1. `passkeyRowTap_startsPasskeyDetailActivity_withCredentialIdExtra` — `ActivityScenario.launch(CredentialListActivity::class.java)` で Activity 起動 → fake `passkeyRepository.listAll()` から 1 件の PassKey を emit → adapter の RecyclerView 1 行目をクリック → `Shadows.shadowOf(activity).nextStartedActivity` を取得し ComponentName が `PasskeyDetailActivity`、extra `EXTRA_CREDENTIAL_ID` が当該 credentialId であることを assert (Req 4.1 / 4.2 / 7.9)
    2. `passwordRowTap_stillStartsCredentialEditActivity` — password 行をクリックして既存挙動 (`CredentialEditActivity` 起動) が破壊されていないことを assert (Req 4.3 / 7.10)
  - _Requirements: 4.1, 4.2, 4.3, 7.9, 7.10_
  - _Boundary: CredentialListActivity_
  - _Depends: 5.1_

- [ ] 7. QA / 動作確認 / PR 準備
- [ ] 7.1 ローカル QA: ビルド + テスト + lint
  - `./gradlew :app:compileDebugKotlin` 成功
  - `./gradlew :app:testDebugUnitTest` 全件 PASS（新規 `PasskeyDetailViewModelTest` / `PasskeyDetailActivityTest` / 拡張 `PasskeyRepositoryTest` 含む）
  - `./gradlew :app:lintDebug` で新規警告ゼロ（既存 baseline を超えない）
  - `./gradlew :app:assembleDebug` で APK ビルド成功
  - `R.string.credential_list_passkey_tap_v1_message` が参照ゼロなら削除済であることを `grep -r` で確認
  - _Requirements: 6.1, 6.2, 6.3, 6.5, 6.6_

- [ ] 7.2 手動動作確認 (端末 / エミュレータ)
  - 一覧画面で PassKey 行をタップして `PasskeyDetailActivity` が起動することを確認 (Req 4.1)
  - 詳細画面で `rpId` / `rpDisplayName` / `userName` / `userDisplayName` / `displayName` / `createdAt` / `lastUsedAt` が正しく表示されることを確認 (Req 1.3)
  - `displayName` を空文字で保存試行 → エラー表示で保存されないことを確認 (Req 2.4)
  - `displayName` に有効値を入力して保存 → Snackbar「保存しました」+ 一覧に戻る (Req 2.6)
  - 一覧画面に新 displayName が反映されていることを確認 (#101 経路の自動 refresh)
  - 「削除」をタップ → 確認ダイアログ表示 → 本文に「RP 側の登録は残ります」相当の文言が含まれることを確認 (Req 3.3)
  - 「キャンセル」で何も起きないこと、「削除」で Snackbar「削除しました」+ 一覧に戻る + 当該 PassKey 行がリストから消えていることを確認 (Req 3.8 / 3.12)
  - back キー / Up ナビゲーションで一覧画面に戻ることを確認 (Req 4.5)
  - en / ja 両ロケールで「PassKey」表記が固定であることを確認 (NFR 3.2)
  - `lastUsedAt` が NULL の PassKey で「未使用」/「Never used」が表示されることを確認 (Req 1.8)
  - `userDisplayName` / `userName` が NULL の PassKey でフォールバック文言 `(ユーザー名なし)` が表示されることを確認 (Req 1.7)
  - _Requirements: 1.x, 2.x, 3.x, 4.x, 5.x, NFR 3_

- [ ] 7.3 PR description の確認事項を design §12 から転記
  - design §12 の確認事項候補 6 件を PR description にコピー
  - 各項目について「実装 / テスト / 文言」のどこで担保したかを 1 行ずつ補足
  - レビュワーへの注意喚起として「`PasskeyRepository.delete` の戻り値型は変更していない」「`PasskeyRepositoryImpl.delete` を `withTransaction` で囲い直した結果、認証セレモニー側 (#100 で `delete` を呼ぶ経路がある場合) の挙動が壊れていないか確認してほしい」を明記
  - _Requirements: (運用)_

---

## マージ前チェック

- [ ] requirements.md の全 Requirement / Acceptance Criteria 番号 (1.x〜7.x + NFR) が design.md / tasks.md の `_Requirements:_` で参照されている
- [ ] DB schema 変更 / migration 追加が無い (Req 6.6 / NFR 4.3) — `app/schemas/` 配下に新規 `.json` が生成されていないこと
- [ ] `PasskeyRepository.delete(credentialId): DeletePasskeyResult` のシグネチャが変更されていない (Req 6.4)
- [ ] `CredentialProviderService` / `PasskeyAuthActivity` / `KeyNestAutofillService` に diff が無い (Req 6.3)
- [ ] `CredentialEditActivity` / `SettingsActivity` の既存 View ID / クリック挙動 / 永続化経路に diff が無い (Req 6.1 / 6.2 / NFR 4.2)
- [ ] `compileSdk` / `targetSdk` / `minSdk` / `applicationId` / `namespace` に diff が無い (Req 6.5 / NFR 4.3)
- [ ] en / ja の両 `strings.xml` に同数の `passkey_detail_*` キーが追加されている (NFR 3.2)
- [ ] 「PassKey」表記が KDoc / コメント / 文言 / ログ全てで固定されている (NFR 3.1 / 3.2)
- [ ] `userHandle` / `encryptedPrivateKey` / `privateKeyIv` / `keyAlias` / `signCount` / AAGUID を表示する View ID が `passkey_detail_activity.xml` に存在しない (NFR 2.1)
- [ ] `SafeLogger` 呼び出しで credentialId raw / userHandle / keyAlias が `info` 以上に出力されていない (NFR 2.2)
- [ ] エクスポート / 共有 / バックアップ系の UI 要素が `PasskeyDetailActivity` / `passkey_detail_activity.xml` に存在しない (NFR 2.4 / Req 5.4)
- [ ] `./gradlew :app:testDebugUnitTest` 全件 PASS
- [ ] `./gradlew :app:lintDebug` 新規警告ゼロ
- [ ] design.md の File Structure Plan に列挙されたファイル群と実装 PR の diff が一致している
