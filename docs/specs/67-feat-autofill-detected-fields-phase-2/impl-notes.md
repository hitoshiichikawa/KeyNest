# Implementation Notes — Issue #67 feat(autofill): detected_fields ログによる「最近検出されたフィールド」サジェスト (Phase 2)

> 関連: [requirements.md](./requirements.md) / [design.md](./design.md) / [tasks.md](./tasks.md)

## 実装サマリ

| Task | Commit (subject) | 1-2 行サマリ |
|---|---|---|
| T1 | feat(autofill): add detected_fields entity, DAO, migration, and repository | `DetectedFieldEntity` (複合 PK + `(package_name, last_detected_at)` index) と `DetectedFieldSource` enum を data/entity に追加。`text` は enum に含めない。 |
| T2 | (T1 と同コミット) | `DetectedFieldDao` 追加。`upsertWithLruCap` (`@Transaction`) で REPLACE-on-conflict と per-package 50 件 LRU を atomic に維持。`observeRecentByPackage` Flow / `deleteByPackage` / `deleteAll` ヘルパ。 |
| T3 | (T1 と同コミット) | `Migration_3_4` (純粋 SQL `CREATE TABLE` + `CREATE INDEX`)。`KeyNestDatabase` を v4 へ bump し `addMigrations(...)` に追加。KSP 生成済み `schemas/.../4.json` を git tracking 追加。 |
| T4 | (T1 と同コミット) | `DetectedField` ドメインモデル、`DetectedFieldRepository` interface、`DetectedFieldRepositoryImpl`。未知 `source` は `null` で skip (forward-compat)。 |
| T5 | feat(autofill): add RecordDetectedFieldsUseCase and ObserveRecentDetectedFieldsUseCase | `RecordDetectedFieldsUseCase`。`findByPackage` で gating、`autofillHints` / `hint` / `idEntry` / `contentDescription` を抽出、normalize 前後 blank は skip。raw `fieldKey` を保存 (NFR 5)。`text` 非参照は型レベル担保。 |
| T6 | (T5 と同コミット) | `ObserveRecentDetectedFieldsUseCase` (default limit = 10)。 |
| T7 | feat(autofill): wire detected_fields recording into KeyNestAutofillService | `ServiceLocator` に Phase 2 三点 (`detectedFieldRepository` / `recordDetectedFieldsUseCase` / `observeRecentDetectedFieldsUseCase`) を追加。`KeyNestAutofillService.onFillRequest` が `callerPackage` 確定直後・(b) short-circuit より前に `scope.launch(Dispatchers.IO)` で fire-and-forget。例外は SafeLogger.warn で swallow (Req 3.8)。 |
| T8 / T8b | feat(autofill): add detected_fields suggestion chip group to credential edit | `CredentialEditViewModel.SuggestionState` / `DetectedFieldSuggestion` / `bindPackageForSuggestions` / `onAddCustomFieldClickedForSuggest` / `onSuggestionClicked` / `hideSuggestions` / `refreshSuggestions`。Activity 側は `inputPackage` TextWatcher・PackagePicker callback・Add-field ボタンを wire、`renderDetectedFieldSuggestions` で Chip 動的生成。Layout に `label_detected_fields` / `scroll_detected_field_suggestions` (ChipGroup) / `tv_detected_fields_empty` を追加。 |
| T9 | test(autofill): cover RecordDetectedFieldsUseCase and DetectedFieldDao | `RecordDetectedFieldsUseCaseTest` 9 ケース (gating / 4 source / blank skip / normalize 後 blank / raw 保存 / 型レベル text 排除 / clock 注入 / empty list / 複数 descriptor)。 |
| T10 | (T9 と同コミット) | `DetectedFieldDaoTest` 15 ケース (Robolectric + in-memory Room、Phase 1 `CredentialDaoTest` と同流儀)。`LRU_CAPACITY = 50` を pin。 |
| T11 | test(autofill): cover suggestion chip ViewModel behaviour | `CredentialEditViewModelSuggestionTest` 11 ケース (初期 hidden / Add で展開 / 転送と hide / 既存 key 除外 / normalize dedupe / empty placeholder / hideSuggestions / blank package / rebind / Mode.Edit 抑止 / 末尾行不在時のno-op)。 |
| T12 | test(migration): add Migration_3_4_Test | Robolectric `Migration_3_4_Test` 4 ケース。v3 schema を SQL で構築 → migrate → detected_fields 構造・index・credentials 不変・idempotency・PK 制約。 |
| T13 | (skipped — see 確認事項 §A) | Phase 1 が `KeyNestAutofillService` のテストフィクスチャを整備していないため、`onFillRequest` 経路の統合テストは見送り。ロジック自体は T5+T9 と T7 のコードレビューで担保。 |
| T14 | feat(autofill): wipe detected_fields in ClearVaultUseCase | `ClearVaultUseCase` の 2 段目に `detectedFieldRepository.deleteAll()` を挿入 (失敗は SafeLogger.warn で swallow)。`ClearVaultUseCaseTest` に 2 ケース追加 (happy-path で detected_fields 削除 / 失敗時も Keystore drop は継続)。`DangerZoneViewModelTest` ヘルパも新 ctor に追従。 |
| T15 | (本 impl-notes 内に検証結果記録) | gradle 検証結果を本ファイル末尾に記録。E2E は実機/エミュ不在のため未実施 — Reviewer に on-device 動作確認を委ねる。 |

---

## 設計決定の補足

### `KeyNestAutofillService.onFillRequest` の detection 発火位置

設計 §7.3 に従い、**`callerPackage` 確定直後・`(b) !parsed.hasUsernameAndPassword` short-circuit の前** に `scope.launch(Dispatchers.IO)` で起動する。これにより Phase 1 ヒューリスティクスでは username/password を認識できない custom-field-only フォーム (Phase 2 の主要ユースケース) でも detection が走る。

- `descriptors.isEmpty()` のときは launch 自体を skip して coroutine 起動コストを 0 に。
- launch は親 `scope` 直下 (handlerJob には bind しない)。Cancellation や callback タイミングと完全 decouple される。
- 例外は内側 `try/catch` で swallow し `SafeLogger.warn` に message + throwable class を残す (Req 3.8)。

### 「直前 add した行」への転送ターゲット決定

Phase 1 の `CustomFieldsState` は `focusedRowIndex` 相当の状態を持たない (design.md §0.1 確認結果)。Phase 2 で UI 状態を追加するよりも、`onSuggestionClicked` が `rows.last()` の rowId に転送する単純なアプローチを採用 (design.md §8.1)。

- ユーザー操作フロー: 「Add field」タップ → 末尾行追加 → サジェスト strip 出現 → chip タップ → 末尾行に転送 → strip 非表示。
- 防御: 行が無いタイミング (ユーザーが Add 後すぐ remove した) で chip クリックが届いても `hideSuggestions()` を呼んで no-op (`onSuggestionClicked_isNoOpWhenNoRows` でカバー)。

### Package 名バインドの責務

`bindPackageForSuggestions(packageName)` を ViewModel に持ち、Activity 側で:

1. `inputPackage` の TextWatcher (manual entry / restore from rotation)
2. `PackagePickerBottomSheet` の選択 callback
3. `load()` 内 (edit mode で record.packageName)

の 3 経路で呼ぶ。同値再バインドは no-op で chip strip の状態が安定する。空文字列は `currentPackageName = null` として扱い、`refreshSuggestions()` が早期 return する。

### Edit mode での chip strip 非表示

`refreshSuggestions()` の冒頭で `_customFields.value.editable == false` を見て早期 return する (`SuggestionState.Hidden` を emit)。これは Phase 1 §13 が "Phase 2 will revisit" として申し送ったままの状態を継承する設計 (§8.5)。

将来 Phase 1.5 が `editable = true` を edit mode に解放する場合、`refreshSuggestions()` は **追加変更なしでそのまま動く** (Mode に直接依存していないため)。`load()` 内で `bindPackageForSuggestions(record.packageName)` を呼ぶ準備も済んでいる。

### `text` 除外の型レベル担保 (再確認)

Phase 1 の `AutofillFieldHeuristics.FieldDescriptor` data class には `text` プロパティが存在しない (Phase 1 §7.2 で意図的に除外)。Phase 2 `RecordDetectedFieldsUseCase.buildRecords` は `text` を参照する経路を構造的に持たないため、requirements §4 Q1 の方針はコンパイル時に担保される。`RecordDetectedFieldsUseCaseTest.invoke_typeLevelTextExclusion_isStructural` で reflection を介してこの不変条件を pin している。

### `ClearVaultUseCase` の detected_fields 削除を best-effort にした理由

`credentialRepository.clearAll()` (ユーザーの「全部消す」意図の主目的) と `keystoreKeyProvider.deleteKey()` (鍵ローテーション) の間に挟まる位置に置いた。

- 失敗時は SafeLogger.warn で残してフローを継続させる。理由は (1) detected_fields は plaintext label のみ (NFR 1 で許容) で漏洩リスクが credentials 本体に比して小さく、(2) ここで abort すると Keystore alias drop が走らず、ユーザーの "delete everything" 意図のうち最も重要な部分が果たせなくなる。
- `ClearVaultUseCaseTest.invoke_detectedFieldsFailure_doesNotAbort_andKeystoreStillCleared` で挙動を pin。

### Layout の Chip 生成方針

Activity 側 `renderDetectedFieldSuggestions` で `ChipGroup.removeAllViews()` → `Chip` 生成 → `addView()` の組み合わせを各 emission で実行する。

- Chip 数は ViewModel 側で 10 件 cap (Req 4.1) されているため teardown コストは無視できる。
- Chip text は `fieldKey` のみ (source 表示なし、design.md §8.2 シンプル化)。
- `ellipsize = END` で長い fieldKey が見切れ表示される。
- `singleLine = true` (XML 側) + `HorizontalScrollView` で横スクロール表示。

---

## 確認事項

### A. T13 (KeyNestAutofillService 統合テスト) のスコープ削減

**Status**: スキップ (tasks.md T13 末尾の「Phase 1 がテストフィクスチャを整備していない場合は T13 を skip し、tasks.md の確認事項に追記」に該当)。

**理由**:
- Phase 1 のテストコードを grep した結果、`KeyNestAutofillService` を直接インスタンス化するテストは存在せず (`FillResponseBuilderTest` がコメントで言及するのみ)、Robolectric ベースの service-binding ハーネスや `ServiceLocator` の差し替え機構も整備されていない。
- Phase 2 で新規にハーネスを構築するのは tasks.md 推奨工数 (100〜200 LOC) を超え、かつ「service binding を mock するか / `ServiceLocator` を可変にするか」という設計判断が新たに必要になる。
- 代替カバレッジ: (1) `RecordDetectedFieldsUseCaseTest` (gating / source 抽出 / blank skip) が use case 全パスを担保、(2) `KeyNestAutofillService.onFillRequest` の変更箇所は ~30 行で純粋に「`scope.launch(Dispatchers.IO) { useCase(...) }` を呼ぶだけ」、(3) Reviewer の on-device E2E (T15) で fire-and-forget の実機動作を確認できる。

**Recommendation**: Phase 3 / 別 Issue で `KeyNestAutofillService` のテストハーネスを整備するときに、本 Phase 2 の検証もそこに加える。

### B. 既存 `*Test.kt` の constructor 追従

`CredentialEditViewModel` に `ObserveRecentDetectedFieldsUseCase` 引数が増えたため、既存の以下のテストファイルの helper `newViewModel(...)` を `ObserveRecentDetectedFieldsUseCase(FakeDetectedFieldRepository())` を渡すよう更新した:

- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/CredentialEditViewModelTest.kt`
- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/CredentialEditViewModelCustomFieldsTest.kt`
- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditAdvancedStateTest.kt`

同じく `ClearVaultUseCase` に `DetectedFieldRepository` 引数が増えたため:

- `app/src/test/java/io/github/hitoshiichikawa/keynest/domain/usecase/ClearVaultUseCaseTest.kt`
- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/danger/DangerZoneViewModelTest.kt`

の helper も追従。挙動は変えていない (空 `FakeDetectedFieldRepository` で十分)。

### C. `values-ja/strings.xml` 翻訳の補完

Phase 1 (#66) は `credential_edit_custom_fields_*` 系の string キーを `values-ja/` に翻訳しないまま develop へマージされている (NFR 3.4 違反の可能性)。本 Phase 2 で追加した `credential_edit_detected_fields_label` / `_empty` は design.md §8.3 の日本語文言を `values-ja/strings.xml` に追加してある。Phase 1 の漏れは本 Issue のスコープ外として手を付けていないが、Reviewer が気付くなら別 Issue 化を推奨。

### D. `lintDebug` の既存エラーについて

`./gradlew :app:lintDebug` は 100 件の `Error:` と 129 件の `Warning:` で fail するが、**すべて Phase 2 着手前から存在する pre-existing 問題** であり本 PR の新規ファイルや変更箇所はゼロ件:

- 主な内訳: `PackageSignatureResolver.kt:50` の `NewApi` (API 28 vs minSdk 26)、`AutofillEnableActivity.kt:70` の `ObsoleteSdkInt`、`autofill_enable_activity.xml` の `HardcodedText`、`colors.xml` の `UnusedResources` 多数。
- 確認方法: lint レポート (`app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt`) を `DetectedField` / `RecordDetected` / `ObserveRecent` で grep してヒットなし。
- Phase 1 (#66) の impl-notes でも `lintDebug` の状況には触れておらず、検証コマンドリストにも含めていない (verify したのは `assembleDebug` と `testDebugUnitTest` のみ)。
- 本 PR でも同じ運用に従い、`assembleDebug` と `testDebugUnitTest` のグリーンを完了条件とする。`lintDebug` の解消は別 Issue (Issue #?) 化を推奨。

### E. en リソースの文言

`values/strings.xml` (en default) には英語版を入れた:
- `credential_edit_detected_fields_label` = "Recently detected fields"
- `credential_edit_detected_fields_empty` = "No detection history yet. Open the app's form once and we'll suggest fields here."

design.md §8.3 は日本語のみ提示していたが、Phase 1 と同じ「default = English, values-ja = Japanese」運用に揃えた (Phase 1 `credential_edit_custom_fields_*` のリソース配置と一致)。

---

## ビルド・テスト検証結果

### 環境

- JDK: Temurin-17.0.19+10 (`/home/hitoshi/sdks/jdk-17`)
- Android SDK: `/home/hitoshi/sdks/android-sdk`
- Gradle: 8.10.2 (project wrapper)
- AGP: 8.5.2 / Kotlin: 1.9.24 / KSP: 1.9.24-1.0.20
- `local.properties` は worktree 直下に手動作成 (gitignore 対象なので commit には含めず)

### 実行コマンドと結果

| コマンド | 結果 | 備考 |
|---|---|---|
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL | Phase 2 全コミット適用後の最終 build。 |
| `./gradlew :app:testDebugUnitTest` | BUILD SUCCESSFUL | Phase 2 新規テスト (T9 / T10 / T11 / T12) + Phase 1 既存テストすべて pass。 |
| `./gradlew :app:lintDebug` | BUILD FAILED (pre-existing) | 既存エラー 100 件 / 警告 129 件。**本 PR の新規ファイルはゼロヒット** (確認事項 §D 参照)。 |

### 個別テストファイルの pass 状況

| テストファイル | tests | failures |
|---|---|---|
| `RecordDetectedFieldsUseCaseTest` | 9 | 0 |
| `DetectedFieldDaoTest` | 15 | 0 |
| `CredentialEditViewModelSuggestionTest` | 11 | 0 |
| `Migration_3_4_Test` | 4 | 0 |
| `ClearVaultUseCaseTest` | 6 (うち 2 件 Phase 2 追加) | 0 |
| `DangerZoneViewModelTest` | 11 (Phase 2 で ctor 追従のみ) | 0 |
| 既存 `CredentialEditViewModelTest` 系 | (変更なし、ctor 追従のみ) | 0 |
| 既存 `CredentialDaoTest` / `Migration_1_2_Test` / `Migration_2_3_Test` 等 | 影響なし | 0 |

### T15: 手動 E2E 動作確認

JVM/agent 環境のため実機/エミュレータ上での E2E は本 worktree から実施不可。以下は **Reviewer に on-device での確認を委ねる**:

1. 登録済み packageName のアプリで autofill を発火 → `detected_fields` に row が追加されることを Room Inspector で確認。Phase 1 ヒューリスティクスが username/password を認識できないフォーム (custom field only) でも追加されることを確認。
2. その credential を編集画面で開き → 「Add field」をタップ → 「Recently detected fields」chip strip が表示されることを確認。
3. chip をタップ → 新規 customField 行の fieldKey フィールドに転送され、chip strip が閉じることを確認。
4. 設定 → Danger Zone → Vault clear を実行 → `detected_fields` テーブルも空になることを確認。

E2E の代替として、本 PR は単体 + 統合テスト (T9 / T10 / T11 / T12 / T14) で各レイヤを個別に検証してある。

---

## 既知の TODO / Follow-up

- **`KeyNestAutofillService` のテストハーネス整備** (確認事項 §A): Phase 3 / 別 Issue で着手予定。
- **Phase 1 の `values-ja/strings.xml` 翻訳漏れ** (確認事項 §C): Phase 1 が `credential_edit_custom_fields_*` を ja に未翻訳。本 PR スコープ外。
- **`lintDebug` の pre-existing エラー解消** (確認事項 §D): 別 Issue で `NewApi` / `ObsoleteSdkInt` / `HardcodedText` / `UnusedResources` を順次潰す。
- **detected_fields の TTL 削除**: requirements §2 Out of Scope。Phase 3 以降で検討。
- **未登録 packageName からの収集 opt-in**: requirements §4 Q1 「将来の余地」で言及。Phase 3 以降で UX 議論。
- **個別 credential 削除時の detected_fields purge**: design.md §12.5 で「Phase 2 では明示削除しない」と決定済み。別 Issue 候補。
