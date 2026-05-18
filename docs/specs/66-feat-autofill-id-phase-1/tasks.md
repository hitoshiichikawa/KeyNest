# Tasks — Issue #66 feat(autofill): カスタムフィールド機能 Phase 1

> 関連: [design.md](./design.md) / [requirements.md](./requirements.md)

## タスク分割方針

- 1 タスク = 1 commit を目安に独立性を確保。
- 依存順に番号付け（T1 → T13）。並行可能タスクは「依存」欄に "T1" 等を明示。
- 各タスクは Developer が独立に着手でき、`./gradlew test` がそのタスク完了時に green になる粒度に分割。
- 既存テスト破壊禁止（design.md §0-1）。`develop` / `main` 直 push 禁止（design.md §0-2）。

---

## T1: CustomField domain model 追加

**目的**: `CustomField` data class を domain 層に追加し、`EncryptedCredentialRecord` / `PlaintextCredential` に customFields プロパティを追加する。`Credential`（プレーンドメイン）には追加しない（design.md §3.2）。

**対象ファイル**:
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/model/CustomField.kt`
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/model/Credential.kt`
  - `EncryptedCredentialRecord` に `customFieldsCiphertext: ByteArray` / `customFieldsIv: ByteArray` を追加
  - `equals` / `hashCode` / `toString` を `contentEquals` / `<NB>` プレビューで更新
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/model/PlaintextCredential.kt`
  - `customFields: List<CustomField>` を追加
  - `close()` 時に value の文字列を best-effort wipe（不変 String の限界はコメントで明示）

**完了条件**:
- 上記ファイルが compile pass。
- 既存テスト（CredentialEditViewModelTest 等）が継続 pass。Result.success 構築箇所で空 ByteArray / 空 List を補う必要あり。
- 新規 unit test: `CustomFieldTest` で data class equals / hashCode を最低限カバー。

**依存タスク**: なし（先頭）。

---

## T2: kotlinx.serialization 依存追加 + EncryptedCustomFieldsCodec 実装

**目的**: `List<CustomField>` ⇔ JSON 文字列 ⇔ AES-GCM ciphertext の往復変換 codec を実装する。design.md §5.2 で推奨された **kotlinx.serialization** を採用。

**対象ファイル**:
- 改修: `app/build.gradle.kts`
  - `plugins { alias(libs.plugins.kotlin.serialization) }` 追加
  - `dependencies { implementation(libs.kotlinx.serialization.json) }` 追加
- 改修: `gradle/libs.versions.toml`
  - kotlin-serialization plugin 定義、kotlinx-serialization-json 依存定義
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/security/CustomFieldJson.kt`
  - `@Serializable` data class（短縮キー or `fieldKey/value` のまま、実装者判断）
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/security/EncryptedCustomFieldsCodec.kt`
  - `encrypt(List<CustomField>) -> EncryptedBlob`
  - `decrypt(EncryptedBlob) -> List<CustomField>`
  - 空 BLOB（length=0）は空リストを返す fallback を含む（design.md §4.1 / §6.1）
- 新規 test: `app/src/test/java/io/github/hitoshiichikawa/keynest/security/EncryptedCustomFieldsCodecTest.kt`
  - 暗号化往復: `encrypt(list) -> decrypt -> 元 list`（NFR テスト Req 6.6）
  - 空リストの暗号化: 非空 BLOB が返ること
  - 空 BLOB の decrypt: 空リスト
  - 不正 JSON の decrypt: 空リスト + warn 出力（fail-open）
  - 中間 byte の wipe 検証（StubAesGcmCipher で encrypt 入力をキャプチャ）

**完了条件**:
- `./gradlew test --tests EncryptedCustomFieldsCodecTest` green。
- APK build success。

**依存タスク**: T1。

---

## T3: CredentialEntity に列追加 + Repository mapping 拡張

**目的**: Room entity に `custom_fields_ciphertext` / `custom_fields_iv` 列を追加し、`CredentialRepositoryImpl` の `toEntity` / `toEncryptedRecord` 変換に新フィールドを追加する。

**対象ファイル**:
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/entity/CredentialEntity.kt`
  - `@ColumnInfo(name = "custom_fields_ciphertext", typeAffinity = ColumnInfo.BLOB) val customFieldsCiphertext: ByteArray`
  - `@ColumnInfo(name = "custom_fields_iv", typeAffinity = ColumnInfo.BLOB) val customFieldsIv: ByteArray`
  - `equals` / `hashCode` / `toString` を更新（既存パターンに揃える）
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/repository/CredentialRepositoryImpl.kt`
  - `toEntity` / `toEncryptedRecord` の mapping に新フィールド 2 つを追加
  - `duplicate()` でも新フィールドをコピー（design.md §6.2）

**完了条件**:
- 既存 unit test が継続 pass。
- KSP 再生成で `CredentialEntity` から生成される DAO 実装が更新される。

**依存タスク**: T1。

---

## T4: Migration_2_3 実装 + KeyNestDatabase version 更新

**目的**: Room schema v2 → v3 の migration を追加し、`KeyNestDatabase.addMigrations(...)` に登録する。

**対象ファイル**:
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/migration/Migration_2_3.kt`
  - `ALTER TABLE credentials ADD COLUMN custom_fields_ciphertext BLOB NOT NULL DEFAULT x''`
  - `ALTER TABLE credentials ADD COLUMN custom_fields_iv BLOB NOT NULL DEFAULT x''`
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/KeyNestDatabase.kt`
  - `@Database(version = 3, ...)`
  - `addMigrations(Migration_1_2, Migration_2_3)`

**完了条件**:
- KSP 再生成で `app/schemas/io.github.hitoshiichikawa.keynest.data.KeyNestDatabase/3.json` が生成される（T6 で commit）。
- 既存 `Migration_1_2_Test` が継続 pass。

**依存タスク**: T3。

---

## T5: Migration_2_3_Test 実装

**目的**: `Migration_1_2_Test` と同じ `SupportSQLiteOpenHelper` パターンで Migration_2_3 を検証する unit test を追加。

**対象ファイル**:
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/data/Migration_2_3_Test.kt`

**完了条件**:
- 以下のテストが pass:
  - `migrate_preservesExistingRow_andLeavesCustomFieldsBlobEmpty`: v2 で 1 行 insert → migrate → 既存列が保持され `custom_fields_ciphertext` / `custom_fields_iv` が長さ 0 の BLOB であることを SQL 直読で検証（Req 6.1）
  - `migrate_addsColumns_andAllowsSubsequentWrites`: migrate 後に新 2 列を含む insert が成功すること
  - `migrate_isIdempotentOnEmptyDatabase`: 空 DB に対する migrate がエラーなく完了し、COUNT(*) = 0 / 新 2 列が存在すること
- helper として `V2_CREATE_TABLE_SQL` 定数を持ち、v2 schema（`last_used_at` 含む）を厳密に再現する。

**依存タスク**: T4。

---

## T6: schemas/3.json 生成 + git tracking 追加

**目的**: Room schema export の v3.json を生成・commit する。Issue #63 の運用に従う。

**対象ファイル**:
- 新規 commit: `app/schemas/io.github.hitoshiichikawa.keynest.data.KeyNestDatabase/3.json`

**完了条件**:
- `./gradlew :app:assembleDebug` で `3.json` が自動生成される。
- `.gitignore` の現行 applicationId ignore 設定（line 49-50 は legacy のみ）を確認し、`3.json` が tracking 対象に入ることを `git status` で確認。
- 生成された `3.json` の `entities[0].fields` に `customFieldsCiphertext` / `customFieldsIv` が含まれる、`identityHash` が更新されていることを目視確認。

**依存タスク**: T4。

---

## T7: SaveCredentialUseCase / UpdateCredentialUseCase に customFields を追加

**目的**: use case の入出力 DTO に customFields を追加し、`EncryptedCustomFieldsCodec.encrypt` を組み込む。

**対象ファイル**:
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/usecase/SaveCredentialUseCase.kt`
  - `NewCredentialInput.customFields: List<CustomField> = emptyList()` を追加
  - `invoke()` 内で codec.encrypt → `EncryptedCredentialRecord.customFieldsCiphertext/Iv` に詰める
  - 失敗時の wipe / clean-up 既存パターンに揃える
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/usecase/UpdateCredentialUseCase.kt`
  - `UpdateCredentialInput.customFields: List<CustomField>? = null` を追加（null = 既存維持）
  - null 時は既存 `customFieldsCiphertext/Iv` を保持、非 null 時は再暗号化
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/di/ServiceLocator.kt`
  - `EncryptedCustomFieldsCodec` を新 use case にコンストラクタ注入する配線追加

**完了条件**:
- 既存テスト pass（input DTO の deafult パラメータで既存呼び出し元は変更不要）。
- 新規テストを T2 の codec を組み合わせて 1-2 ケース追加（往復確認）。

**依存タスク**: T2, T3.

---

## T8: UnlockVaultUseCase に customFields 復号を追加

**目的**: `UnlockVaultUseCase.invoke` の復号結果に `PlaintextCredential.customFields` を含める。

**対象ファイル**:
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/usecase/UnlockVaultUseCase.kt`
  - `EncryptedCustomFieldsCodec` をコンストラクタ注入
  - decrypt 後に codec.decrypt して `PlaintextCredential.customFields` を埋める
  - codec.decrypt 失敗は warn 出して空リスト fallback（fail-open、design.md §5.4）
- 改修: `ServiceLocator` の `unlockVaultUseCase` 配線

**完了条件**:
- 新規テスト: encrypt 済み customFields が unlock 後に正しい List を返すこと
- 空 BLOB の credential が空 customFields を返すこと（migration 後の既存行向け）
- 既存 `UnlockVaultUseCase` 利用テストが pass

**依存タスク**: T2, T3, T7。

---

## T9: AutofillFieldHeuristics.extractMatchKeys / normalizeKey + AssistStructureParser 拡張

**目的**: match キー抽出 API と正規化関数を追加し、`AssistStructureParser.parse()` の戻り値に `customFieldCandidates` を追加する。

**対象ファイル**:
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/parser/AutofillFieldHeuristics.kt`
  - `fun extractMatchKeys(descriptor: FieldDescriptor): Set<String>` 追加
  - `fun normalizeKey(raw: String): String` 追加（小文字化 + `isWhitespace()` 除去）
  - 既存 `classify` は変更しない（Req 4.3 / design.md §7.1）
  - `text` を抽出対象から除外（design.md §7.2）
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/parser/AssistStructureParser.kt`
  - `ParsedFields.customFieldCandidates: List<CustomFieldCandidate>` 追加
  - walk loop で username/password 検出と並行して全 editable view を `customFieldCandidates` に収集
  - 早期 break は customField 抽出が完了する until 維持しない（design.md §8.2）
- 新規 test: `AutofillFieldHeuristicsExtractMatchKeysTest`
  - autofillHints / hint / idEntry / contentDescription 各単独 / 組み合わせケース
  - 全角/半角空白を含む文字列の正規化検証
  - `text` が match キーに含まれないことの検証

**完了条件**:
- 既存 `AutofillFieldHeuristicsTest`（classify 系）が pass（変更ないこと）。
- 新規テスト pass。

**依存タスク**: なし（T1〜T8 と並行可能）。

---

## T10: FillResponseBuilder に customField 対応 + CustomFieldFillResponseTest

**目的**: locked / unlocked 両 dataset に customField を組み込み、Req 6.2 / 6.3 / 6.4 のテストを追加する。

**対象ファイル**:
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/builder/FillResponseBuilder.kt`
  - `buildLockedResponse` シグネチャに `customFieldCandidates: List<CustomFieldCandidate>` 追加
  - `buildUnlockedDataset` シグネチャに `customFieldValues: Map<AutofillId, String>` 追加
  - locked 段階で match algorithm 実行（design.md §8.1）。match 結果の AutofillId 全てに placeholder + auth IntentSender を setValue
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/usecase/ResolveAutofillCandidatesUseCase.kt`
  - `AutofillCandidate.customFieldKeys: List<String>` を追加（fieldKey 平文ラベルのみ）
  - これは locked 段階での match に必要（design.md §6.2 / §6.4）
  - **重要**: customField のラベルだけを `EncryptedCredentialRecord` から取り出すには…困難（value と一体で暗号化されている）。よって **locked 段階での match を諦め、match を unlocked 段階に移す** ことも検討する。実装者は以下 2 案から選択し、選択理由をコミットメッセージに記す:
    - 案 X: locked 段階で AutofillCandidate に fieldKey list を載せる場合、`AutofillCandidate` 生成時に `EncryptedCustomFieldsCodec.decrypt` を行う必要があるが、これは Req 5.2（locked 段階で復号禁止）に違反する。**よって locked 段階での match は不可**。
    - 案 Y: locked 段階では全 customFieldCandidates の AutofillId だけを Intent extras に渡して unlock activity に投げ、unlock 後に復号 → match を実行する方式に変更する。**こちらが Req 5.2 と整合**。
  - **決定**: 案 Y を採用する。`buildLockedResponse` は customFieldCandidates 全件の AutofillId を Intent extras に詰める。実 match は AutofillUnlockActivity 内で復号後に実行（T11）。これに合わせて `FillResponseBuilder.buildLockedResponse` は customField match を行わず、AutofillId list だけを `AutofillUnlockActivity.newIntent` に渡す
- 新規 test: `app/src/test/java/io/github/hitoshiichikawa/keynest/autofill/CustomFieldFillResponseTest.kt`（または FillResponseBuilderTest 内に追加）
  - Req 6.2: 単一 customField + 単一 match field → Dataset に正しい value
  - Req 6.3: 単一 field が複数 customField と match した場合に **最初の** customField の値が採用されること
  - Req 6.4: match されない customField が Dataset に含まれないこと
  - 注: Phase 1 では match を unlock 後の AutofillUnlockActivity に寄せるため、match algorithm 自体は `CustomFieldMatcher` のような pure utility に切り出してテストする方が unit test しやすい

**完了条件**:
- 既存 `FillResponseBuilderTest` が pass（既存挙動を変えていないこと）。
- 新規テスト pass。
- KeyNestAutofillService 側の build call も `customFieldCandidates` を渡すよう更新（compile pass）。

**依存タスク**: T9。

---

## T11: AutofillUnlockActivity に customField match + buildUnlockedDataset 呼び出し拡張

**目的**: `AutofillUnlockActivity` が復号後に customField match を実行し、`buildUnlockedDataset` に `customFieldValues` を渡す。

**対象ファイル**:
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/unlock/AutofillUnlockActivity.kt`
  - `EXTRA_CUSTOM_FIELD_AUTOFILL_IDS: ArrayList<AutofillId>` / `EXTRA_CUSTOM_FIELD_DESCRIPTORS_*` を Intent に追加
  - `newIntent` シグネチャ拡張（design.md §8.5）
  - unlock 完了後に `CustomFieldMatcher`（T10 で切り出した pure utility）を呼んで `Map<AutofillId, String>` を構築
  - `buildUnlockedDataset` に渡す
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/KeyNestAutofillService.kt`
  - `extractInlineSpecs` の隣で `customFieldCandidates` を Intent extras に詰める処理を追加

**完了条件**:
- 既存 `AutofillUnlockActivityTest`（存在する場合）pass。
- 新規 integration test（Robolectric）: customField を含む credential を unlock → Dataset に value が入ること
- 既存 username/password 経路が unchanged であることを既存テストで確認

**依存タスク**: T8, T10。

---

## T12: CredentialEditViewModel に CustomFieldsState 追加 + reducer + テスト拡充

**目的**: ViewModel に customField 編集 state を追加し、追加/削除/編集/保存の reducer を実装。Req 6.5 のテストを追加。

**対象ファイル**:
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditViewModel.kt`
  - `CustomFieldsState` data class（design.md §9.3）
  - `customFields: StateFlow<CustomFieldsState>` 追加
  - `addCustomFieldRow()` / `removeCustomFieldRow(rowId)` / `updateCustomFieldKey(rowId, str)` / `updateCustomFieldValue(rowId, str)` reducer 追加
  - `save()` 内で空 fieldKey 行を silent drop し `CustomField` list を構築（Req 3.4）
  - `load()` の編集モード時の挙動を design.md §9.3 の暫定設計に従い実装（Phase 1: 新規モードのみ customField 編集可、編集モードでは customField セクション非表示 or read-only）
- 改修: `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/CredentialEditViewModelTest.kt`
  - Req 6.5: 追加 / 削除 / 最大 10 件制限 / 空 fieldKey silent drop / canAddMore フラグの検証

**完了条件**:
- 既存 `CredentialEditViewModelTest` の全テストが pass。
- 新規テスト pass。

**依存タスク**: T1, T7.

---

## T13: CredentialEditActivity / XML に customField UI 実装

**目的**: Advanced セクション配下に customField サブセクションを動的 row で実装。

**対象ファイル**:
- 改修: `app/src/main/res/layout/credential_edit_activity.xml`
  - `advanced_content` 配下、`row_credential_id` の直後に `row_custom_fields_section` を追加（design.md §9.1）
- 新規: `app/src/main/res/layout/view_custom_field_row.xml`
  - 横並びの fieldKey EditText + value EditText + 削除 ImageButton
- 改修: `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditActivity.kt`
  - ViewModel `customFields` StateFlow を購読し、`renderCustomFieldRows(state)` で `container_custom_fields` を再構築
  - `btn_add_custom_field` の click handler でアクション dispatch
  - 各 row の TextWatcher で `updateCustomFieldKey` / `updateCustomFieldValue` を呼ぶ
  - `btn_remove_row` の click handler で `removeCustomFieldRow(rowId)`
  - 10 件到達時に `btn_add_custom_field` を disable（Req 3.5）
  - 保存時に `viewModel.save(...)` に customFields を渡す（T12 で署名拡張済み）
- 改修: `app/src/main/res/values/strings.xml`
  - `credential_edit_custom_fields_title` / `credential_edit_custom_field_key_hint` / `credential_edit_custom_field_value_hint` / `credential_edit_custom_field_remove_a11y` 等の文字列追加

**完了条件**:
- 既存 `CredentialEditLayoutAuditTest`（存在する場合）の既存 ID 保持アサーションが pass。
- 新規 Robolectric/instrumented test（時間が許せば）で row 追加 / 削除の動作を検証。
- 手動テスト（emulator）でフィールド追加 → 値入力 → 保存 → 再 open で永続化されることを確認（編集モードでの読み出しは T12 の暫定設計に依存）。

**依存タスク**: T12。

---

## T14: SafeLogger 適用箇所のレビュー + final チェック

**目的**: 本 PR で追加した customField 関連のログ出力箇所を一通り見直し、Req 5.1 / NFR 2.1 / 2.2 違反がないことを確認。

**対象ファイル**:
- 全タスクで追加したログ出力（grep `Log.` / `SafeLogger.` で確認）

**完了条件**:
- customField の `value` / `fieldKey` がログに露出していないことを目視チェック。
- `SafeLogger.Redacted` で wrap されているか、または件数等の集計情報のみが出力されていることを確認。
- design.md §11 の表に従い、`CredentialEntity.toString()` / `EncryptedCredentialRecord.toString()` が customField 情報を含まないことを確認。
- PR description に「ログ漏洩がないことを T14 で確認済み」と明記。

**依存タスク**: T1 〜 T13 のすべて。

---

## 全体合計見積り

| Task | 概算工数 (h) | 並行可否 |
|---|---|---|
| T1 | 1.5 | 先頭、並行不可 |
| T2 | 3.0 | T1 後、T3 / T9 と並行可 |
| T3 | 1.5 | T1 後、T9 と並行可 |
| T4 | 1.0 | T3 後 |
| T5 | 2.0 | T4 後 |
| T6 | 0.5 | T4 後、T5 と並行可 |
| T7 | 2.5 | T2 + T3 後、T9 と並行可 |
| T8 | 2.0 | T7 後、T9 / T10 と並行可 |
| T9 | 3.0 | 独立、T1〜T8 と並行可 |
| T10 | 4.0 | T9 後 |
| T11 | 3.0 | T8 + T10 後 |
| T12 | 3.0 | T1 + T7 後、T9〜T11 と並行可 |
| T13 | 4.0 | T12 後 |
| T14 | 1.0 | 末尾 |
| **合計** | **約 32h** | |

### 着手順序チャート（直列依存の最長パス）

```
T1 → T3 → T4 → T5  ── (Migration 系の最長: 6h)
       └→ T6
T1 → T2 → T7 → T8 → T11 → T14  ── (use case + autofill: 13h)
T9 → T10 → T11                  ── (autofill builder: 10h)
T1 → T7 → T12 → T13 → T14       ── (UI 系: 12h)
```

最長クリティカルパス: T1 → T2 → T7 → T8 → T11 → T14 = 約 13h。並行作業を入れれば 1 名で 4-5 営業日、2 名並行で 2-3 営業日が見込み。

---

## 制約再掲

- 既存テスト（unit / instrumented / `Migration_1_2_Test`）を一切 fail させないこと。
- `develop` / `main` への直接 push 禁止。feature branch + PR レビュー経由。
- ライブラリ追加: **kotlinx.serialization** を採用（design.md §5.2 推奨）。`build.gradle.kts` / `libs.versions.toml` 変更を T2 で commit。
- 暗号化粒度: **配列全体を 1 ciphertext**（design.md §3.4 案 A）。`custom_fields_ciphertext` / `custom_fields_iv` の 2 列で完結。
