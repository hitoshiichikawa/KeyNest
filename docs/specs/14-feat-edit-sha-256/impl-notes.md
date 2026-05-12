# Implementation Notes — Issue #14 (Edit "詳細設定" collapsible)

## 追加・変更ファイル

### 本体 (src/main)

| Path | 役割 |
|---|---|
| `app/src/main/java/com/example/keynest/util/AdvancedDetailsFormatter.kt` | 表示専用フォーマッタ。`formatTimestamp(epochMillis, zone)` → `yyyy-MM-dd HH:mm`、`formatSha256Hex(SigningHash?)` → 64文字 lowercase hex / null。Android 非依存（pure JVM JUnit でテスト可能）。 |
| `app/src/main/java/com/example/keynest/ui/edit/CredentialEditViewModel.kt` | `AdvancedDetails` data class（mode / credentialId / createdAt / updatedAt / signatureSha256Hex / signatureCapturedAt / expanded / credentialIdVisible）と `advancedDetails: StateFlow<AdvancedDetails>` を追加。`load()` で edit-mode のデータ補完、`toggleAdvancedExpanded()` / `toggleCredentialIdVisible()` を公開。toggle 状態は load 時に保持。 |
| `app/src/main/java/com/example/keynest/ui/edit/CredentialEditActivity.kt` | `advancedDetails` を collect、ヘッダ click で expand toggle、ID toggle、Copy ボタンを wiring。SafeLogger には `previewHex` 経由でのみ hex を渡す。`chevronRotationFor` を companion に extract。 |
| `app/src/main/java/com/example/keynest/ui/edit/SignatureClipboardPayload.kt` | `ClipData` 構築だけを担う小ヘルパ。テスト分離のために独立。 |
| `app/src/main/res/layout/credential_edit_activity.xml` | 詳細設定セクションを追記（header / chevron / content 5 行 + Copy ボタン / ID toggle）。`fontFamily="monospace"` / `focusable=false` / 48dp タッチ領域を直接 attribute で指定。 |
| `app/src/main/res/values/strings.xml` | 13 個の新規 string リソース（タイトル / 各ラベル / "Not saved yet" / "Not captured" / ID 隠蔽プレースホルダ / トグル / Copy / a11y descriptions / clipboard label / コピー成功 toast）。 |

### テスト (src/test)

| Path | 粒度 | 役割 |
|---|---|---|
| `app/src/test/java/com/example/keynest/util/AdvancedDetailsFormatterTest.kt` | unit (JVM) | timestamp UTC / JST / epoch 0 boundary、hex 64-char lowercase / null / 0xAB byte → "ab" |
| `app/src/test/java/com/example/keynest/ui/edit/CredentialEditAdvancedStateTest.kt` | unit (JVM, coroutines-test) | 初期状態 collapsed、toggle 動作、load で edit-mode へ遷移、null signature、equal createdAt/updatedAt、不在 ID、toggle が state を破壊しないこと |
| `app/src/test/java/com/example/keynest/ui/edit/AdvancedSectionChevronTest.kt` | unit (JVM) | chevronRotationFor(false/true) と「両者が異なる」defensive 性質 |
| `app/src/test/java/com/example/keynest/ui/edit/SignatureClipboardPayloadTest.kt` | Robolectric (`@RunWith(AndroidJUnit4)`, sdk=33) | full 64-char hex が ClipData にそのまま入ること、empty 入力でクラッシュしないこと |
| `app/src/test/java/com/example/keynest/ui/edit/CredentialEditActivityLogAuditTest.kt` | unit (file scan) | NFR 1.1 / 1.2: `SafeLogger.*` 呼び出し内で `hex` 変数を扱う行は必ず `SafeLogger.previewHex(...)` を経由。`${'$'}hex` 直挿入は禁止 |
| `app/src/test/java/com/example/keynest/ui/edit/CredentialEditLayoutAuditTest.kt` | unit (file scan) | NFR 2.1 (contentDescription), NFR 2.3 (48dp), NFR 3.1 (@string 参照), Req 3.5 (monospace), Req 6.3 (read-only attributes) |

合計テスト数: 6 ファイル / 約 26 ケース（うち 1 ファイル Robolectric, 5 ファイル pure JVM）。

## 重要な設計判断

### 1. AdvancedDetails 状態を ViewModel に置いた理由

Req 1.5「Edit 画面が画面遷移で破棄されるまで保持」を満たすには rotation でも生き残る必要がある。
`savedInstanceState` 経由でも実現できるが、

- ViewModel-scoped に置けば configuration change (rotation) を自動でまたぐ
- `finish()` で ViewModel が destroy されるので「画面遷移で破棄」のスコープと完全一致
- Out of Scope に「アプリ再起動をまたいだ保持」が明記されている → process death でクリアされて OK

したがって `MutableStateFlow<AdvancedDetails>` を ViewModel に持たせるのが最も自然。

### 2. AdvancedDetailsFormatter を util に置いた理由

`com.example.keynest.util` には既に `HexEncoding` `SafeLogger` 等の純粋ユーティリティが集約されている。Android 依存を避けるためここに置けば、unit テストを `@RunWith(AndroidJUnit4)` 無しで実行できる（pure JVM JUnit）。

### 3. SHA-256 完全 hex の redaction（NFR 1.1）

`SafeLogger.previewHex(hex)` は既存実装で「先頭 8 文字 + ...」を返す（hex が 8 文字以下なら全 redaction）。
コピー成功時のログは `"signature hex copied (preview=deadbeef...)"` の形で出力される。
完全 hex が `Throwable.message` / `Log.X` / analytics に渡る経路を `CredentialEditActivityLogAuditTest` で **ソースレベルで** 機械的に検出（runtime 経路ではなく source scan で regression を防ぐ）。

### 4. credential ID の文字列化タイミング（NFR 1.3）

`renderCredentialIdRow` で `id.toString()` を呼ぶのは `details.credentialIdVisible == true` のときのみ。off 状態ではプレースホルダ `••••` のみが描画される。`Credential.id` の数値そのものは Activity の memory にも置かない（`details.credentialId: Long?` は ViewModel が保持するが、UI から目視できる位置には決して書かない）。

### 5. SHA-256 hex 表示装飾（コロン区切り vs 連続 hex）

要件 Out of Scope: 「コロン区切り表示の採用判断」は実装で決定する。本実装では **連続 64 文字 lowercase hex** を採用した。理由:

- 他ツール（apksigner / openssl）の出力と直接 diff できる（コロン区切りは整形が必要）
- monospace + `breakStrategy="simple"` でデフォルト 2 行折返しが自然になる
- 折返しを抑止する場合は将来要件で「常に 1 行 + horizontal scroll」を別途指示できる

確認事項に列挙。

### 6. timestamp フォーマット選択

`yyyy-MM-dd HH:mm`（秒なし）を採用。Req 2.2 が要求するのは「年・月・日・時・分が読み取れる形式」なので秒は冗長。i18n の観点では DateFormat.getDateTimeInstance(SHORT, SHORT) で端末ロケール準拠にもできるが、

- ISO 風 `yyyy-MM-dd` 表記は世界共通で読める
- ロケール依存にすると test 結果が JVM の default locale に左右されて flaky

`Locale.ROOT` で固定。確認事項に列挙。

### 7. flag protocol

`CLAUDE.md` の `**採否**: opt-out` を確認済み。Feature Flag Protocol は適用せず、通常の単一実装パスで実装した（Req 1.3, NFR 1.1）。

## AC × テスト 対応表

| AC | 実装箇所 | カバーするテスト |
|---|---|---|
| 1.1 collapsed initial | `AdvancedDetails.NewMode.expanded=false` / layout `visibility="gone"` | `CredentialEditAdvancedStateTest.advancedDetails_initialValue_isCollapsedAndNewMode` |
| 1.2 toggle | `toggleAdvancedExpanded()` | `toggleAdvancedExpanded_flipsBetweenCollapsedAndExpanded` |
| 1.3 collapsed hides content | layout `advanced_content.visibility=GONE` + `renderAdvancedDetails` | （1.1 と同テストで間接） |
| 1.4 chevron 向き反転 | `chevronRotationFor(expanded)` | `AdvancedSectionChevronTest`（3 ケース） |
| 1.5 ライフサイクル内保持 | ViewModel-scoped、`load` で toggle 保持 | `load_inEditMode_populatesAllAdvancedFields_andPreservesTogglestate` |
| 2.1 createdAt/updatedAt 表示 | `AdvancedDetails.createdAt/updatedAt` + layout 2 行 | `load_inEditMode_populatesAllAdvancedFields` |
| 2.2 端末ローカル TZ | `AdvancedDetailsFormatter.formatTimestamp(zone=systemDefault)` | `formatTimestamp_inUtc`, `formatTimestamp_inJst` |
| 2.3 新規モードは未保存 placeholder | `renderTimestampRow` else 分岐 → "Not saved yet" | `advancedDetails_initialValue_isCollapsedAndNewMode`（fields null） |
| 2.4 createdAt==updatedAt 両方表示 | 両 row 独立 | `load_inEditMode_whenSignatureMissing_setsHexAndCapturedAtToNull`（createdAt == updatedAt） |
| 3.1 64-char lowercase hex | `formatSha256Hex` | `formatSha256Hex_returns64CharLowercaseHex_forValidHash`, `..._isAllLowercase...` |
| 3.2 コピー導線提供 | layout `btn_copy_signature_hex` | `CredentialEditLayoutAuditTest`（resource 存在） |
| 3.3 クリップボード書き込み | `SignatureClipboardPayload.build` + `clipboard.setPrimaryClip` + toast | `SignatureClipboardPayloadTest.build_preservesFullHexInClipboardItemText` |
| 3.4 null → placeholder + disable | `renderSignatureRow` null branch (`btn.isEnabled=false`, `visibility=GONE`) | `load_inEditMode_whenSignatureMissing_setsHexAndCapturedAtToNull` |
| 3.5 monospace | layout `fontFamily="monospace"` | `CredentialEditLayoutAuditTest.signatureHexValue_usesMonospaceFont` |
| 4.1 captured at TZ | 同じ `formatTimestamp` | `formatTimestamp_inUtc`/`inJst`（共有） |
| 4.2 null → 「未取得」 | `renderTimestampRow useNotCapturedPlaceholder=true` | `load_inEditMode_whenSignatureMissing_setsHexAndCapturedAtToNull` |
| 5.1 トグル提供 | layout `toggle_credential_id` | `CredentialEditLayoutAuditTest`（resource 存在） |
| 5.2 ID 初期非表示 | `AdvancedDetails.NewMode.credentialIdVisible=false` | `advancedDetails_initialValue_isCollapsedAndNewMode` |
| 5.3 トグル on → 表示 | `renderCredentialIdRow` if `credentialIdVisible` | `toggleCredentialIdVisible_flipsBetweenHiddenAndVisible` |
| 5.4 トグル off → 非表示 | 同上 | 同テスト |
| 5.5 新規モードは行非表示 | `renderCredentialIdRow` if `id==null` → `View.GONE` | `advancedDetails_initialValue_isCollapsedAndNewMode`（credentialId=null） |
| 6.1 MVP AC 不変更 | 既存 save / load / validate を一切触らず | 既存テスト（`CredentialEditViewModelTest`, `SaveCredentialUseCaseTest` 等）は変更なし |
| 6.2 入力 state 破壊しない | toggle は `_advancedDetails` のみ mutate / Activity の inputs を touch しない | `toggleAdvancedExpanded_doesNotResetTransientFormState_norTriggerSave` |
| 6.3 読み取り専用 | layout `focusable=false`, `textIsSelectable=false`（hex 行は select 可だが選択のみで edit 不可） | `CredentialEditLayoutAuditTest.advancedRows_areReadOnly_notFocusableNorEditable` |
| NFR 1.1 hex を log に出さない | `SafeLogger.previewHex(hex)` のみ使用 | `CredentialEditActivityLogAuditTest`（source scan） |
| NFR 1.2 既存 preview 形式に準拠 | `SafeLogger.previewHex` は先頭 8 文字 + "..."（既存） | 既存 `SafeLoggerTest` が担保 |
| NFR 1.3 ID 非表示時に描画しない | `renderCredentialIdRow` if `!visible` → placeholder | `toggleCredentialIdVisible_flipsBetweenHiddenAndVisible`（state レベル） |
| NFR 1.4 平文 password 露出なし | section は password 関連 view / state に一切触れない | 既存テストで担保 |
| NFR 2.1 a11y label | layout `contentDescription` on header + toggle | `CredentialEditLayoutAuditTest.advancedHeader_hasAccessibilityContentDescription` 他 |
| NFR 2.2 a11y イベント通知 | `View.visibility` 変化は Android Framework が AccessibilityEvent を自動発火 | Android Framework default behavior（test では確認しない） |
| NFR 2.3 48dp タッチ領域 | layout `android:minWidth/minHeight="48dp"` | `CredentialEditLayoutAuditTest.copyButton_meetsAccessibilityTouchTarget_48dp` 他 |
| NFR 3.1 i18n リソース | すべての文言 `@string/...` | `CredentialEditLayoutAuditTest.allAdvancedSectionStrings_areResources_notLiterals` |

## 確認事項（Reviewer / Designer / PM 判断）

1. **timestamp フォーマット**: `yyyy-MM-dd HH:mm`（Locale.ROOT 固定）で確定して良いか。ロケール準拠 (`DateFormat.getDateTimeInstance(SHORT, SHORT)`) に切り替えるかどうかは Reviewer 判断。
2. **SHA-256 hex 表示装飾**: 連続 64 文字 lowercase hex を採用。コロン区切り（`a1:2f:...:01`）にするかは Out of Scope なので将来 Issue で判断。デザインモック (`screens-1.jsx`) は装飾形を提示しているが、本要件は連続 hex を選択した（apksigner と直接 diff できる利点）。
3. **chevron アイコン**: 現在 `@android:drawable/arrow_down_float` を使用。Material のアイコンセット（`ic_arrow_drop_down_24` など）に置き換える方が UI ガイドライン的に望ましい可能性。デザイン承認が必要。
4. **クリップボード機微化（API 33+）**: API 33+ では `setPrimaryClip` 時に `ClipDescription.EXTRA_IS_SENSITIVE = true` を渡せば OS の clipboard preview から内容を隠せる。SHA-256 hex は機微情報ではないので不要だが、UX 上 toast で「コピーしました」を見せる以上、追加の保護は提案として残す。
5. **a11y イベント通知 (NFR 2.2)**: View visibility 変化は Framework が自動でイベントを発火するため明示的 announce はしていない。スクリーンリーダー利用者の体感を最終確認したい場合は手動テストが必要。
6. **i18n**: 現状英語のみ。日本語リソース（`values-ja/strings.xml`）の追加は本 Issue スコープ外と判断（既存資源が英語のみのため）。Reviewer 確認。
7. **削除導線**: モック `screens-1.jsx` には Edit 画面下部に「このクレデンシャルを削除」ボタンがあるが、本要件 Out of Scope（削除導線は別途存在）。誤解防止のため言及。
8. **build / lint / test 実行**: 本作業環境では Android SDK / JDK が未インストールのため `./gradlew test` `./gradlew lint` を実行できていない。次のサイクル（Reviewer / CI）で実機ビルド & テストの確認を依頼。

## ビルド / テスト / lint 結果

- ローカル環境に Java / Android SDK 未インストールのため `./gradlew testDebugUnitTest`、`./gradlew lint`、`./gradlew assembleDebug` の実行不能を確認した。
- 追加した unit テストは Vitest 風命名 (`describe > it`) ではなく既存 KeyNest の JUnit4 / Truth / mockk / Robolectric 慣習に追従。
- 既存テストには **触れていない**。`CredentialEditViewModelTest.kt` などは元の状態のまま。
- 追加した lint-loadbearing パターン: `breakStrategy="simple"`（API 23+、minSdk 26 で安全）、`switchmaterial.SwitchMaterial`（Material Components 1.12 で利用可、本プロジェクトの version catalog で確認済み）。

## 次の Issue 候補（派生）

- **「署名を再取得」ボタン**（Out of Scope 5.: Option B）の実装。Edit 画面の `signatureSha256` 欄に再取得アクションを追加し、`UpdateCredentialUseCase` を SignatureRefresh-only モードで呼ぶ。
- **詳細設定セクション展開状態の永続化**（Out of Scope 6.: アプリ再起動・画面再生成をまたぐ保持）。`SharedPreferences` を導入する場合、保存時のセキュリティ評価が必要。
- **SHA-256 hex コロン区切り表示の選択肢**（Out of Scope）。デザイン承認の上で実装。
- **日本語 string リソース**（`values-ja/strings.xml`）の整備。
