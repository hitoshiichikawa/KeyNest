# Implementation Plan

各タスクは独立コミット可能（1 タスク = 1 PR の単位）。`(P)` は並列実行可能を示し、`_Boundary:_` で
触る design.md のコンポーネント境界を明示する。`_Depends:_` は非自明な前提タスクを示す。
`_Requirements:_` は requirements.md の numeric ID を列挙する。

---

## Phase 0: プロジェクトセットアップ

- [x] **T1. Android プロジェクト雛形と Gradle 構成**
- [x] T1.1 Gradle wrapper / ルート設定の作成
  - 触るファイル: `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`
  - Gradle 8.7+ / AGP 8.5+ / Kotlin DSL を採用
  - `settings.gradle.kts` で `app` モジュールを include
  - 完了条件: `./gradlew tasks` が成功
  - テスト方針: CI で `./gradlew tasks` 実行（unit/instrumented なし）
  - _Requirements: 7.1, 7.2, 7.3_
- [x] T1.2 Version catalog の整備
  - 触るファイル: `gradle/libs.versions.toml`
  - エントリ: kotlin, agp, androidx-core, androidx-appcompat, material, androidx-activity, androidx-fragment, androidx-lifecycle, androidx-room, androidx-biometric, kotlinx-coroutines, junit, mockk, robolectric, androidx-test, espresso
  - 完了条件: 後続タスクから `libs.xxx` で参照可能
  - _Requirements: 7.1_
  - _Depends: T1.1_
- [x] T1.3 `app` モジュール build.gradle.kts
  - 触るファイル: `app/build.gradle.kts`, `app/proguard-rules.pro`
  - `minSdk = 26`, `compileSdk = 34`, `targetSdk = 34`
  - Kotlin / Room / Biometric / Coroutines プラグインを適用
  - `kapt` または `ksp` で Room コンパイラを有効化
  - 完了条件: `./gradlew :app:assembleDebug` が成功（空 Activity でも可）
  - _Requirements: 7.1, 7.2, 7.3_
  - _Depends: T1.2_
- [x] T1.4 AndroidManifest スケルトンと Application クラス (P)
  - 触るファイル: `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/example/keynest/KeyNestApp.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`
  - `<application android:name=".KeyNestApp" android:allowBackup="false">` を宣言
  - `android.permission.INTERNET` は宣言しない（NFR 1.5 機械的保証）
  - 完了条件: アプリが起動できる
  - テスト方針: なし
  - _Requirements: 7.1, 7.2, 7.3_
  - _Boundary: KeyNestApp_
  - _Depends: T1.3_
- [x] T1.5 `.gitignore` の Android 用エントリ追記 (P)
  - 触るファイル: `.gitignore`
  - `build/`, `.gradle/`, `local.properties`, `*.iml`, `.idea/`, `captures/` を追加
  - 完了条件: `git status` で生成物が ignore される
  - _Requirements: —（プロジェクト衛生）_
  - _Boundary: project root_

---

## Phase 1: Security & Util 基盤

- [x] **T2. 暗号化基盤の実装**
- [x] T2.1 `KeystoreKeyProvider` 実装 (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/security/KeystoreKeyProvider.kt`
  - Alias `keynest_aead_v1`、AES-256-GCM、`KeyGenParameterSpec` で `setRandomizedEncryptionRequired(true)`、`setUserAuthenticationRequired(false)`
  - 完了条件: `getOrCreateKey()` で SecretKey が取得できる
  - テスト方針: instrumented test（Robolectric では AndroidKeyStore 不可、`androidTest` 側で確認）
  - _Requirements: 1.2, NFR 1.1, NFR 1.2_
  - _Boundary: KeystoreKeyProvider_
  - _Depends: T1.4_
- [x] T2.2 `AesGcmCipher` と `EncryptedBlob` 実装 (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/security/AesGcmCipher.kt`, `app/src/main/java/com/example/keynest/security/EncryptedBlob.kt`
  - `encrypt(ByteArray) → EncryptedBlob(iv, ciphertext)`、`decrypt(EncryptedBlob) → ByteArray`
  - IV は Cipher が自動生成（12byte）
  - 完了条件: 同一平文を 2 回暗号化して ciphertext が異なる、復号で元に戻る
  - テスト方針: instrumented unit test（Keystore 必須のため `androidTest`）
  - _Requirements: 1.2, NFR 1.1, NFR 1.2_
  - _Boundary: AesGcmCipher, KeystoreKeyProvider_
  - _Depends: T2.1_
- [x] T2.3 `SafeLogger` 実装 (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/util/SafeLogger.kt`
  - `debug/info/warn/error` API、password / 復号 credential / 完全な署名 hex のログ出力を機械的に遮断する utility
  - 完了条件: 与えられた sensitive キーワードがログに出ないことの unit test が通る
  - テスト方針: JVM unit test
  - _Requirements: NFR 1.3, NFR 5.1_
  - _Boundary: SafeLogger_
  - _Depends: T1.4_
- [x] T2.4 `HexEncoding` ユーティリティ (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/util/HexEncoding.kt`
  - ByteArray ↔ Hex の相互変換
  - 完了条件: 32byte ハッシュ→64文字 hex のラウンドトリップ test 通過
  - テスト方針: JVM unit test
  - _Requirements: 2.1_
  - _Boundary: HexEncoding_
- [x] T2.5 `PackageSignatureResolver` 実装
  - 触るファイル: `app/src/main/java/com/example/keynest/util/PackageSignatureResolver.kt`
  - API 28+: `GET_SIGNING_CERTIFICATES` → `SigningInfo.apkContentsSigners`
  - API 26–27: `GET_SIGNATURES` → `signatures[]`
  - 複数署名: 各 SHA-256 をソート結合して再度 SHA-256
  - 未インストール (`NameNotFoundException`) → null
  - 完了条件: 上記分岐をすべて通過する unit test 完了
  - テスト方針: JVM unit test（Robolectric で PackageManager mock）
  - _Requirements: 2.1, 2.2, 2.3, 4.1_
  - _Boundary: PackageSignatureResolver_
  - _Depends: T2.4_

---

## Phase 2: Domain & Data 層

- [x] **T3. ドメインモデルと UseCase 雛形**
- [x] T3.1 `Credential` / `PlaintextCredential` / `SigningHash` (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/domain/model/Credential.kt`, `PlaintextCredential.kt`, `SigningHash.kt`
  - `PlaintextCredential` は `AutoCloseable`、`close()` で password CharArray を zero-fill
  - `SigningHash.equals` は `MessageDigest.isEqual` を利用（タイミング攻撃耐性）
  - 完了条件: `close()` 後 password CharArray が空白埋めされている test
  - テスト方針: JVM unit test
  - _Requirements: 1.1, 1.2, 2.1, 2.2, 5.5_
  - _Boundary: Credential, PlaintextCredential, SigningHash_
  - _Depends: T1.4_
- [x] T3.2 `CredentialRepository` interface 定義 (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/domain/repository/CredentialRepository.kt`
  - `save / update / delete / findByPackage / findById / observeAll` を定義
  - 完了条件: interface コンパイル成功、依存先で参照可能
  - _Requirements: 1.1, 1.4, 1.5_
  - _Boundary: CredentialRepository_
  - _Depends: T3.1_

- [x] **T4. Room データ層実装**
- [x] T4.1 `CredentialEntity` と DAO
  - 触るファイル: `app/src/main/java/com/example/keynest/data/entity/CredentialEntity.kt`, `app/src/main/java/com/example/keynest/data/dao/CredentialDao.kt`
  - 設計書 schema どおりにカラム定義（password_ciphertext / password_iv / signature_sha256 nullable / package_name index）
  - 完了条件: Room がコンパイル時にスキーマ検証を通す
  - テスト方針: Room schema 検証 + 簡易 insert/find unit test (Robolectric)
  - _Requirements: 1.1, 1.2, 1.4, 2.1, 2.2_
  - _Boundary: CredentialEntity, CredentialDao_
  - _Depends: T3.2, T1.3_
- [x] T4.2 `KeyNestDatabase` と Migration スケルトン
  - 触るファイル: `app/src/main/java/com/example/keynest/data/KeyNestDatabase.kt`
  - DB ファイル名 `keynest.db`、version 1
  - 完了条件: `Room.databaseBuilder` でビルドできる
  - テスト方針: in-memory DB unit test
  - _Requirements: 1.1_
  - _Boundary: KeyNestDatabase_
  - _Depends: T4.1_
- [x] T4.3 `CredentialRepositoryImpl` 実装
  - 触るファイル: `app/src/main/java/com/example/keynest/data/repository/CredentialRepositoryImpl.kt`
  - DAO ↔ ドメイン型のマッピング、`observeAll()` で Flow を返却
  - 完了条件: domain interface を満たす
  - テスト方針: in-memory Room を使った unit test（保存→取得→削除のラウンドトリップ）
  - _Requirements: 1.1, 1.4, 1.5, 2.1, 2.2, 2.3_
  - _Boundary: CredentialRepositoryImpl, CredentialDao_
  - _Depends: T4.2, T3.2_

- [x] **T5. UseCase 実装**
- [x] T5.1 `SaveCredentialUseCase` (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/domain/usecase/SaveCredentialUseCase.kt`
  - バリデーション（packageName 正規表現、必須項目）→ 署名取得 → 暗号化 → save
  - 失敗時は `Result.failure`、例外メッセージに password を含めない
  - 完了条件: 正常系・packageName 不正・未インストール時の各 unit test 通過
  - テスト方針: JVM unit test（mockK で repository / cipher / sigResolver）
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2_
  - _Boundary: SaveCredentialUseCase, CredentialRepository, AesGcmCipher, PackageSignatureResolver_
  - _Depends: T2.2, T2.5, T4.3_
- [x] T5.2 `UpdateCredentialUseCase` / `DeleteCredentialUseCase` (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/domain/usecase/UpdateCredentialUseCase.kt`, `DeleteCredentialUseCase.kt`
  - update 時に署名ハッシュを再取得（Req 2.3）
  - 完了条件: 更新後 `signatureSha256` と `signatureCapturedAt` が更新される unit test
  - テスト方針: JVM unit test
  - _Requirements: 1.5, 2.3_
  - _Boundary: UpdateCredentialUseCase, DeleteCredentialUseCase, CredentialRepository_
  - _Depends: T5.1_
- [x] T5.3 `ListCredentialsUseCase` (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/domain/usecase/ListCredentialsUseCase.kt`
  - `Flow<List<Credential>>` を返す
  - _Requirements: 1.5_
  - _Boundary: ListCredentialsUseCase, CredentialRepository_
  - _Depends: T4.3_
- [x] T5.4 `ResolveAutofillCandidatesUseCase` (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/domain/usecase/ResolveAutofillCandidatesUseCase.kt`
  - packageName で取得 → 呼び出し元現行 SHA-256 取得 → 署名一致のみ filter（NULL／不一致を除外）
  - **復号は行わない**（NFR 2.2）。返却型は `AutofillCandidate`（id/label/username/packageName のみ）
  - 完了条件: 一致／不一致／NULL／混在の各テストが通る
  - テスト方針: JVM unit test
  - _Requirements: 3.1, 3.5, 4.1, 4.2, 4.3, 4.4, NFR 2.2, NFR 3.2_
  - _Boundary: ResolveAutofillCandidatesUseCase, CredentialRepository, PackageSignatureResolver_
  - _Depends: T4.3, T2.5_
- [x] T5.5 `UnlockVaultUseCase` (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/domain/usecase/UnlockVaultUseCase.kt`
  - id → 暗号文取得 → `AesGcmCipher.decrypt` → `PlaintextCredential` を返す
  - 完了条件: round-trip で password が復号できる instrumented test
  - テスト方針: instrumented test（Keystore 必須）
  - _Requirements: 5.3, NFR 1.4_
  - _Boundary: UnlockVaultUseCase, CredentialRepository, AesGcmCipher_
  - _Depends: T2.2, T4.3_

---

## Phase 3: Auth & DI

- [x] **T6. 認証ラッパー**
- [x] T6.1 `BiometricAuthenticator` 実装
  - 触るファイル: `app/src/main/java/com/example/keynest/auth/BiometricAuthenticator.kt`
  - `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` を要求
  - `AuthResult` sealed class を返却
  - 完了条件: API 経由で `Succeeded / Failed / Cancelled / Unavailable` を区別できる
  - テスト方針: instrumented test（モックは難しいが起動可能性のみ確認）
  - _Requirements: 5.2, 5.3, 5.4_
  - _Boundary: BiometricAuthenticator_
  - _Depends: T1.3_

- [ ] **T7. ServiceLocator（軽量 DI）**
- [ ] T7.1 `ServiceLocator` 実装
  - 触るファイル: `app/src/main/java/com/example/keynest/di/ServiceLocator.kt`
  - Database / Repository / Cipher / Resolver / UseCase 群の singleton 生成
  - 完了条件: `KeyNestApp.onCreate` から初期化可能
  - テスト方針: なし（後続タスクで間接検証）
  - _Requirements: —_
  - _Boundary: ServiceLocator, KeyNestApp_
  - _Depends: T4.2, T2.2, T2.5, T5.1, T5.2, T5.3, T5.4, T5.5_

---

## Phase 4: AutofillService 実装

- [ ] **T8. Autofill 層**
- [ ] T8.1 `AutofillFieldHeuristics` 実装 (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/autofill/parser/AutofillFieldHeuristics.kt`
  - 4 段優先順位（autofillHints → inputType password → inputType email → idEntry/hint テキスト一致）
  - 完了条件: 各経路のフィクスチャで username/password ノードが特定／不能を返す
  - テスト方針: JVM unit test
  - _Requirements: 3.1, 3.2, NFR 3.1_
  - _Boundary: AutofillFieldHeuristics_
  - _Depends: T1.4_
- [ ] T8.2 `AssistStructureParser` 実装
  - 触るファイル: `app/src/main/java/com/example/keynest/autofill/parser/AssistStructureParser.kt`
  - AssistStructure を最大 500 ノード／深さ 50 で走査
  - 例外を内部で捕捉して空結果を返す
  - 完了条件: 推定不能ケースで両 AutofillId が null になる test
  - テスト方針: Robolectric unit test（AssistStructure フィクスチャ）
  - _Requirements: 3.1, 3.2, NFR 3.1_
  - _Boundary: AssistStructureParser, AutofillFieldHeuristics_
  - _Depends: T8.1_
- [ ] T8.3 `DatasetPresentationFactory` 実装 (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/autofill/builder/DatasetPresentationFactory.kt`, `app/src/main/res/layout/dataset_presentation.xml`
  - RemoteViews ベースの presentation（API 30+ では `Presentations` を併用）
  - 完了条件: label 文字列が表示される presentation を生成できる
  - テスト方針: Robolectric unit test
  - _Requirements: 3.3_
  - _Boundary: DatasetPresentationFactory_
  - _Depends: T1.4_
- [ ] T8.4 `FillResponseBuilder` 実装
  - 触るファイル: `app/src/main/java/com/example/keynest/autofill/builder/FillResponseBuilder.kt`
  - ロック中：各候補に `setAuthentication(IntentSender, presentation)` 付き Dataset を生成
  - 認証後：username/password の `AutofillValue` をセットした Dataset を生成
  - 完了条件: ロック中の FillResponse バイト列に元 password 文字列が含まれない assertion
  - テスト方針: Robolectric unit test
  - _Requirements: 3.3, 3.4, 5.1, NFR 1.4_
  - _Boundary: FillResponseBuilder, DatasetPresentationFactory_
  - _Depends: T8.3, T5.4_
- [ ] T8.5 `KeyNestAutofillService` 実装と Manifest 宣言
  - 触るファイル: `app/src/main/java/com/example/keynest/autofill/KeyNestAutofillService.kt`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/autofill_service_config.xml`
  - Manifest に `<service ... permission="BIND_AUTOFILL_SERVICE">` と `<intent-filter>`、`meta-data` を追加
  - `onFillRequest`: AssistStructureParser → ResolveAutofillCandidatesUseCase → FillResponseBuilder
  - `onSaveRequest`: 空実装（MVP スコープ外）
  - 例外をすべてキャッチして `callback.onSuccess(null)`（NFR 3.1 / 3.2）
  - 完了条件: 模擬呼び出しで空候補時に null 応答、候補あり時に Authentication 付き Dataset を返す
  - テスト方針: Robolectric instrumentation test
  - _Requirements: 3.1, 3.2, 3.3, 3.5, 4.1, 4.2, 4.3, 4.4, 5.1, 7.2, NFR 1.4, NFR 2.1, NFR 2.2, NFR 3.1, NFR 3.2_
  - _Boundary: KeyNestAutofillService, AssistStructureParser, FillResponseBuilder, ResolveAutofillCandidatesUseCase_
  - _Depends: T8.2, T8.4, T7.1_
- [ ] T8.6 `AutofillUnlockActivity` 実装と Manifest 宣言
  - 触るファイル: `app/src/main/java/com/example/keynest/autofill/unlock/AutofillUnlockActivity.kt`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/themes.xml`
  - extra から credentialId と AutofillId ペアを受け取り、BiometricPrompt を起動
  - 成功時：UnlockVaultUseCase で復号 → Dataset を組み立てて `EXTRA_AUTHENTICATION_RESULT` に set → `setResult(RESULT_OK)`
  - 失敗／キャンセル時：`setResult(RESULT_CANCELED)`
  - finish 直後に `PlaintextCredential.close()` で zero-fill
  - 完了条件: 成功／失敗／キャンセルの 3 経路 instrumented test
  - テスト方針: instrumented test（BiometricPrompt は androidx-biometric の test API を使用）
  - _Requirements: 5.2, 5.3, 5.4, 5.5_
  - _Boundary: AutofillUnlockActivity, BiometricAuthenticator, UnlockVaultUseCase, FillResponseBuilder_
  - _Depends: T6.1, T5.5, T8.4_

---

## Phase 5: UI 層

- [ ] **T9. UI 実装**
- [ ] T9.1 `CredentialListActivity` / Adapter / ViewModel (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/ui/list/CredentialListActivity.kt`, `CredentialListAdapter.kt`, `CredentialListViewModel.kt`, `app/src/main/res/layout/credential_list_activity.xml`, `credential_list_item.xml`
  - `ListCredentialsUseCase` の Flow を購読
  - リストアイテムから編集／削除メニュー導線
  - 完了条件: 登録済み credential が一覧表示される
  - テスト方針: instrumented Espresso test（追加→表示）
  - _Requirements: 1.5_
  - _Boundary: CredentialListActivity, CredentialListViewModel, ListCredentialsUseCase_
  - _Depends: T5.3, T7.1_
- [ ] T9.2 `CredentialEditActivity` / ViewModel (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/ui/edit/CredentialEditActivity.kt`, `CredentialEditViewModel.kt`, `app/src/main/res/layout/credential_edit_activity.xml`
  - 入力: packageName / username / password / label
  - バリデーションエラー表示（Req 1.3）
  - password は `CharArray` で扱い、保存後即 zero-fill
  - 完了条件: 正常保存、バリデーションエラー、編集の各 instrumented test
  - テスト方針: instrumented Espresso test
  - _Requirements: 1.1, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3_
  - _Boundary: CredentialEditActivity, CredentialEditViewModel, SaveCredentialUseCase, UpdateCredentialUseCase_
  - _Depends: T5.1, T5.2, T7.1_
- [ ] T9.3 `PackagePickerBottomSheet`（インストール済みアプリ選択） (P)
  - 触るファイル: `app/src/main/java/com/example/keynest/ui/edit/PackagePickerBottomSheet.kt`, `app/src/main/res/layout/package_picker_bottom_sheet.xml`
  - `PackageManager.getInstalledApplications` を一覧表示し、選択結果を CredentialEditActivity に返す
  - 完了条件: 選択された packageName が編集画面に反映される
  - テスト方針: instrumented test
  - _Requirements: 1.1, 2.1_
  - _Boundary: PackagePickerBottomSheet, CredentialEditActivity_
  - _Depends: T9.2_
- [ ] T9.4 `AutofillEnableActivity`
  - 触るファイル: `app/src/main/java/com/example/keynest/ui/enable/AutofillEnableActivity.kt`, `app/src/main/res/layout/autofill_enable_activity.xml`
  - `AutofillManager.hasEnabledAutofillServices()` で状態確認
  - 「有効化」ボタンで `Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE` + `package:` URI Intent を起動
  - 完了条件: 未有効化 / 有効化済みで UI 出し分け、Intent 発火を instrumented test で検証
  - テスト方針: instrumented Espresso test（Intents.intended で発火確認）
  - _Requirements: 6.1, 6.2, 6.3_
  - _Boundary: AutofillEnableActivity_
  - _Depends: T1.4_
- [ ] T9.5 アプリ起動時の Autofill 有効化案内導線
  - 触るファイル: `app/src/main/java/com/example/keynest/ui/list/CredentialListActivity.kt`, `app/src/main/AndroidManifest.xml`
  - launcher Activity を `CredentialListActivity` とし、起動時に未有効化なら `AutofillEnableActivity` を表示
  - メニューから随時 `AutofillEnableActivity` に到達可能
  - 完了条件: 初回未有効化時に案内が出る、有効化済みでは出ない
  - テスト方針: instrumented test
  - _Requirements: 6.1, 6.3_
  - _Boundary: CredentialListActivity, AutofillEnableActivity_
  - _Depends: T9.1, T9.4_

---

## Phase 6: 結合テスト・性能・セキュリティ検証

- [ ] **T10. 統合・性能・セキュリティ検証**
- [ ] T10.1 End-to-End Autofill フロー instrumented test (P)
  - 触るファイル: `app/src/androidTest/java/com/example/keynest/e2e/AutofillFlowTest.kt`
  - フロー: credential 登録 → 別アプリの login Activity に AutofillManager をトリガ → 候補表示 → BiometricPrompt 模擬通過 → username/password 入力
  - 完了条件: 一連の操作が成功し、対象 EditText に値がセットされる
  - テスト方針: instrumented test
  - _Requirements: 3.3, 3.4, 5.2, 5.3_
  - _Boundary: KeyNestAutofillService, AutofillUnlockActivity, BiometricAuthenticator_
  - _Depends: T8.6, T9.2_
- [ ] T10.2 署名不一致／NULL の候補除外 instrumented test (P)
  - 触るファイル: `app/src/androidTest/java/com/example/keynest/security/SignatureMismatchTest.kt`
  - PackageSignatureResolver を test double で差し替え、不一致時に候補が出ないことを検証
  - 完了条件: 不一致／NULL の credential が一切返らない
  - _Requirements: 4.2, 4.3, 4.4_
  - _Boundary: ResolveAutofillCandidatesUseCase, PackageSignatureResolver_
  - _Depends: T8.5_
- [ ] T10.3 ロック中の FillResponse バイト列検査 unit test (P)
  - 触るファイル: `app/src/test/java/com/example/keynest/autofill/LockedFillResponseSecurityTest.kt`
  - ロック中 FillResponse を Parcel 経由で `marshall()` し、バイト列に元 password 文字列・復号値が含まれないことを検証
  - 完了条件: assertion 通過
  - テスト方針: Robolectric unit test
  - _Requirements: 5.1, NFR 1.4_
  - _Boundary: FillResponseBuilder_
  - _Depends: T8.4_
- [ ] T10.4 NFR 2.1 性能テスト (P)
  - 触るファイル: `app/src/androidTest/java/com/example/keynest/perf/FillRequestLatencyTest.kt`
  - credential 100 件登録状態で `onFillRequest` 〜 FillResponse 構築を 50 回計測
  - 完了条件: 中央値 ≤ 300ms、p95 ≤ 600ms
  - テスト方針: instrumented benchmark
  - _Requirements: NFR 2.1_
  - _Boundary: KeyNestAutofillService, ResolveAutofillCandidatesUseCase_
  - _Depends: T8.5_
- [ ] T10.5 ログマスク／例外マスクの監査テスト (P)
  - 触るファイル: `app/src/test/java/com/example/keynest/util/SafeLoggerAuditTest.kt`
  - 全 UseCase の異常系で例外メッセージ／スタックトレースに password 平文が含まれないことを検証
  - 完了条件: assertion 通過
  - テスト方針: JVM unit test
  - _Requirements: NFR 1.3, NFR 5.1_
  - _Boundary: SafeLogger, SaveCredentialUseCase, UnlockVaultUseCase_
  - _Depends: T5.1, T5.5, T2.3_
- [ ] T10.6 `INTERNET` permission 非宣言の Manifest 検証 (P)
  - 触るファイル: `app/src/test/java/com/example/keynest/manifest/InternetPermissionAbsenceTest.kt`
  - Robolectric で `PackageManager.getPackageInfo(..., GET_PERMISSIONS)` を取得し、`android.permission.INTERNET` が含まれないことを assert
  - _Requirements: NFR 1.5_
  - _Boundary: AndroidManifest_
  - _Depends: T1.4_

---

## AC ↔ タスク被覆チェック

requirements.md の全 numeric ID について、少なくとも 1 つのタスクで `_Requirements:_` に列挙されていることを示す。

| Requirement | Covering Tasks |
|---|---|
| 1.1 | T3.1, T4.1, T4.3, T5.1, T9.2, T9.3 |
| 1.2 | T3.1, T4.1, T4.3, T5.1, T2.1, T2.2 |
| 1.3 | T5.1, T9.2 |
| 1.4 | T3.2, T4.1, T4.3, T5.1, T9.2 |
| 1.5 | T3.2, T4.3, T5.2, T5.3, T9.1, T9.2 |
| 2.1 | T2.5, T3.1, T4.1, T4.3, T5.1, T9.2, T9.3 |
| 2.2 | T2.5, T3.1, T4.1, T4.3, T5.1, T9.2 |
| 2.3 | T2.5, T4.3, T5.2, T9.2 |
| 3.1 | T5.4, T8.1, T8.2, T8.5 |
| 3.2 | T8.1, T8.2, T8.5 |
| 3.3 | T8.3, T8.4, T8.5, T10.1 |
| 3.4 | T8.4, T10.1 |
| 3.5 | T5.4, T8.5 |
| 4.1 | T2.5, T5.4, T8.5 |
| 4.2 | T5.4, T8.5, T10.2 |
| 4.3 | T5.4, T8.5, T10.2 |
| 4.4 | T5.4, T8.5, T10.2 |
| 5.1 | T8.4, T8.5, T10.3 |
| 5.2 | T6.1, T8.6, T10.1 |
| 5.3 | T5.5, T6.1, T8.6, T10.1 |
| 5.4 | T6.1, T8.6 |
| 5.5 | T3.1, T8.6 |
| 6.1 | T9.4, T9.5 |
| 6.2 | T9.4 |
| 6.3 | T9.4, T9.5 |
| 7.1 | T1.1, T1.2, T1.3, T1.4 |
| 7.2 | T1.1, T1.3, T1.4, T8.5 |
| 7.3 | T1.1, T1.3, T1.4 |
| NFR 1.1 | T2.1, T2.2, T4.1 |
| NFR 1.2 | T2.1, T2.2 |
| NFR 1.3 | T2.3, T10.5 |
| NFR 1.4 | T5.5, T8.4, T8.5, T10.3 |
| NFR 1.5 | T1.4, T10.6 |
| NFR 2.1 | T8.5, T10.4 |
| NFR 2.2 | T5.4, T8.5 |
| NFR 3.1 | T8.1, T8.2, T8.5 |
| NFR 3.2 | T5.4, T8.5 |
| NFR 4.1 | T1.4（Accessibility permission 不宣言で機械的に担保） |
| NFR 4.2 | T1.4（Google Sign-In 依存ライブラリを Gradle に含めない） |
| NFR 4.3 | T1.4（DeviceOwner/root API 不使用） |
| NFR 5.1 | T2.3, T10.5 |

すべての requirements.md numeric ID が少なくとも 1 タスクでカバーされていることを確認した。
