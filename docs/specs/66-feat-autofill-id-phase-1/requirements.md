# Requirements Document

## 1. 目的・背景

業務アプリや一部の会員制サービスの認証画面は、`ID / Password` の 2 フィールド構成では
なく、**会員番号 / 社員番号 / 店舗コード / 契約番号** 等の独自フィールドを採用している
ことが多い。現状の `AutofillFieldHeuristics`（`app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/parser/AutofillFieldHeuristics.kt`）
は `Username` / `Password` / `Unknown` の 3 値の Role 判定のみを行うため、これらの独自
フィールドには値が流し込まれず、ユーザーは結局手入力に戻ることになり KeyNest を利用する
意義が損なわれる。

本要件は、`Credential` ドメインモデルに **任意の `fieldKey → value` ペアの集合** である
カスタムフィールド機能を追加し、Autofill 時に対象アプリの field 情報（`autofillHints` /
`hint` / `resourceId` / `text` / `contentDescription`）と部分一致 + 正規化（小文字化 +
空白除去）で照合して Dataset に値を含める Phase 1 を完成させることを目的とする。

### Credential Manager API がスコープ外な理由

Android 14+ で導入された Credential Manager API は username / password セマンティクスを
前提とした標準化 API であり、任意 `fieldKey → value` ペアの自動入力を扱うインターフェイス
は提供されない。よって本機能は従来からの Autofill Framework 上に独立して構築し、
Credential Manager 経路には一切手を加えない。

## 2. スコープ

### 対象

| 対象 | 変更内容 |
|---|---|
| `Credential` (domain model) | `customFields: List<CustomField>` を追加。`CustomField(fieldKey: String, value: String)` |
| `CredentialEntity` | Room schema に `custom_fields` 列を追加（JSON 文字列で保存） |
| `Migration_2_3` | 既存 credential を空の customFields で初期化 |
| Room TypeConverter | `List<CustomField>` ⇔ JSON 文字列の相互変換 |
| `CredentialEditActivity` / `CredentialEditViewModel` | Advanced セクションに「カスタムフィールド」リスト動的追加・編集・削除 UI |
| `AutofillFieldHeuristics` | AssistStructure の field から match キーを抽出する API を追加（既存 username/password 判定とは独立） |
| `FillResponseBuilder` | custom field の `fieldKey` と field の match キーを部分一致 + 正規化で照合し、match した field を Dataset に追加 |
| 暗号化 | custom field の `value` を既存 username/password と同様 AES-GCM で暗号化 |

### Out of Scope

- detected_fields ログによる「最近検出されたフィールド」サジェスト（Phase 2 の別 Issue）
- `AutofillFieldHeuristics` への日本語 hint / 一般的 resourceId pattern 追加（Phase 3 の別 Issue）
- Credential Manager API 対応（永久に out of scope）
- カスタムフィールドの並び順カスタマイズ UI（入力順固定）
- 行単位の value 表示マスク切替トグル（Phase 2 で検討）
- Dataset presentation（RemoteViews）のカスタムフィールド向け見た目変更
- 既存 username / password 判定ロジック自体の改変

## 3. 用語定義

| 用語 | 定義 |
|---|---|
| customField | `fieldKey` と `value` のペア。1 credential に最大 10 件まで保持できる |
| fieldKey | ユーザーが定義するカスタムフィールド名（例: `会員番号` / `店舗コード` / `employeeId`）。UI 上の表示ラベル兼 match 用キー |
| value | 当該フィールドに Autofill 時に流し込む値。AES-GCM で暗号化保存 |
| match キー | AssistStructure の field 1 つに対し、`autofillHints` / `hint` / `resourceId` / `text` / `contentDescription` の各文字列を抽出した集合 |
| 正規化 | 「小文字化 + 全空白除去（半角スペース・全角スペース・タブを含むあらゆる空白文字を除去）」の関数 |
| 部分一致 | 正規化済み `fieldKey` が、正規化済み match キー集合のうち **少なくとも 1 つの文字列に `contains` の意味で含まれる** こと |

## 4. ユーザーストーリー

1. **会員番号フィールドを持つ業務アプリの利用者**: 社内勤怠アプリのログイン画面が「社員番号 / パスワード」構成で、社員番号フィールドが standard `username` hint を持たない。Credential 編集画面で `fieldKey="社員番号"` を追加しておくと、次回ログイン画面で社員番号が自動入力される。
2. **複数の独自フィールドを持つサービスの利用者**: あるサービスが「契約番号 + 店舗コード + パスワード」の 3 段認証構成。3 つの fieldKey を定義しておくと、autofill suggestion から 1 タップで 3 フィールドすべて自動入力される。
3. **カスタムフィールドを必要としない既存ユーザー**: アプリ更新後も既存 credential は空の `customFields` で復元され、従来の username / password 補完挙動は一切変わらない。

## 5. 機能要件 (EARS 形式)

### Requirement 1: データモデル

- 1.1 The Credential model shall `customFields: List<CustomField>` プロパティを持つ（既定値は空リスト）
- 1.2 The CustomField data class shall `fieldKey: String` および `value: String` を持つ
- 1.3 The CredentialEntity shall Room 列 `custom_fields: String NOT NULL DEFAULT '[]'` を追加し、JSON シリアライズで `List<CustomField>` を保存する
- 1.4 The custom fields' values shall 既存 username / password と同じ AES-GCM 暗号化レイヤ（`AesGcmCipher` / `EncryptedBlob`）で暗号化される
- 1.5 The Credential model（domain layer）shall 平文 `value` を保持しない（既存設計どおり `EncryptedCredentialRecord` 経由で復号する）

### Requirement 2: Migration

- 2.1 When Room schema を v2 → v3 にアップグレードしたとき, the migration shall 全 credential に空の `custom_fields = '[]'` をセットする
- 2.2 The migration shall 既存 `Migration_1_2` / `Migration_1_2_Test` と同じ命名規約・テストパターンで `Migration_2_3` および `Migration_2_3_Test` を持つ
- 2.3 The Room schema export shall v3 の JSON を `app/schemas/<applicationId>.data.KeyNestDatabase/3.json` に出力し、commit 対象とする（Issue #63 の運用に従う）

### Requirement 3: 編集 UI

- 3.1 The CredentialEditActivity shall Advanced セクション（既存 collapsible LinearLayout）配下に「カスタムフィールド」リストを表示する
- 3.2 When ユーザーが「フィールド追加」ボタンを押したとき, the UI shall 新しい行（フィールド名 + 値の 2 入力欄）を追加する
- 3.3 When ユーザーが行の削除ボタンを押したとき, the UI shall 対応する行を削除する
- 3.4 If `fieldKey` が空のとき, the save action shall その行を無視して save 可能とする（バリデーションエラーにせず silent drop）
- 3.5 The UI shall カスタムフィールド最大 10 件まで追加可能とする（11 件目の追加ボタンを disable）
- 3.6 The value 入力欄 shall plain text（`inputType` に `textPassword` を**用いない**）で表示する（確認事項 Q3 の決定に基づく）
- 3.7 The save action shall `fieldKey` を **入力ままで保存** し、正規化は match 時にのみ行う（確認事項 Q1 の決定に基づく）。表示ラベルはユーザー入力をそのまま保持する

### Requirement 4: Autofill matching

- 4.1 When AutofillService が FillRequest を受けたとき, the FillResponseBuilder shall 対象 credential の各 customField について、AssistStructure の全 field の match キー集合（autofillHints / hint / resourceId / text / contentDescription）に対し、正規化（小文字化 + 全空白除去：半角スペース・全角スペース・タブを含む）したうえで部分一致照合する
- 4.2 If カスタムフィールドが対象アプリの field と match したとき, the Dataset shall その field に対応する `value` を含む
- 4.3 The matching shall username / password の heuristic 判定（`AutofillFieldHeuristics.classify`）と独立に行われ、既存挙動を変更しない
- 4.4 If 同一 field が複数の customField と match した場合, the Dataset shall **最初に登録された customField** の値を採用する（決定的）
- 4.5 If 同一 customField が複数の field と match した場合, the Dataset shall **match したすべての field に同一 `value` を流し込む**（field 側の重複適用は許容）

### Requirement 5: 暗号化と機密処理

- 5.1 The custom field values shall ログ出力されない（既存 `SafeLogger` 規約に従い、`fieldKey` も含めて値は SafeLogger 経由で扱う）
- 5.2 The custom field values shall locked state では復号せず、locked dataset の auth pendingIntent 経由でのみ復号される（既存 username / password と同等の二系統設計を踏襲）
- 5.3 The locked-state FillResponse shall customField の値を含まず、unlocked-state でのみ Dataset に値が埋め込まれる

### Requirement 6: テスト

- 6.1 The Migration_2_3_Test shall v2 → v3 で既存 credential が空 customFields（`'[]'`）で復元されることを検証
- 6.2 The CustomFieldFillResponseTest shall 単一 customField + 単一 match field のケースで Dataset に正しい値が入ることを検証
- 6.3 The CustomFieldFillResponseTest shall 複数 match 時の決定的順序（Requirement 4.4 の「最初に登録された customField」）を検証
- 6.4 The CustomFieldFillResponseTest shall match されない customField が Dataset に含まれないことを検証
- 6.5 The CredentialEditViewModelTest shall customFields の追加・削除・保存ロジック（最大 10 件制限 / fieldKey 空 silent drop を含む）を検証
- 6.6 The encryption coverage shall custom field 値の暗号化往復（plaintext → AES-GCM ciphertext → plaintext）を検証

## 6. 非機能要件

### NFR 1: 暗号化と機密性

1. The custom field values shall 既存 username / password と同等の AES-GCM 鍵管理（Android Keystore 経由）で保護される
2. The persisted form shall いかなる経路でも平文 value をディスクに書き込まない（Room 列・SharedPreferences・ログを含む）

### NFR 2: ログ規約

1. The implementation shall `SafeLogger` の現規約（値そのものをログに残さない）に従い、customField の `value` および機微に取り得る `fieldKey` をログ出力しない
2. The implementation shall デバッグ用途であっても customField 件数のような無害な集計情報のみを許容する

### NFR 3: 性能

1. The FillResponseBuilder shall customField 最大 10 件 × AssistStructure の field 数の照合処理を、Autofill 応答 SLA（Android Autofill の応答タイムアウト範囲内）で完了させる
2. The matching shall 単純な文字列 `contains` ベースとし、正規化処理を 1 field あたり O(n) に保つ

### NFR 4: 後方互換性

1. The Credential domain model shall `customFields` の既定値を空リストとし、既存呼び出し元のコードを破壊しない
2. The Migration_2_3 shall 既存 credential の username / password 復元挙動を一切変更しない
3. The CredentialEditActivity shall customFields を持たない credential 編集時、UI 上は空のカスタムフィールドリスト（追加ボタンのみ表示）の状態となる

### NFR 5: 一貫性

1. The fieldKey の正規化規則 shall match パス（FillResponseBuilder）と将来追加されるサジェスト経路（Phase 2）で同一の関数を共有できるように、正規化処理を単一の utility として実装することを推奨する

## 7. データ移行戦略

### v2 → v3 migration の手順概要

1. `KeyNestDatabase` の `@Database(version = 3, ...)` に更新し、`exportSchema = true` の現行設定下で gradle ビルドにより `app/schemas/<applicationId>.data.KeyNestDatabase/3.json` を自動生成する
2. 生成された `3.json` を git tracking 対象に追加（Issue #63 で確定した運用に従い、現行 applicationId 配下のみ commit）
3. `Migration_2_3 : Migration(2, 3)` を実装し、`credentials` テーブルに `custom_fields TEXT NOT NULL DEFAULT '[]'` を追加する単一 `ALTER TABLE` を発行
4. `KeyNestDatabase.Builder` の `addMigrations(...)` に `Migration_2_3` を登録
5. Room TypeConverter（`List<CustomField>` ⇔ JSON 文字列）を新規追加し、`@TypeConverters` を `CredentialEntity` または `KeyNestDatabase` に適用
6. `Migration_2_3_Test` を `Migration_1_2_Test` と同等の `MigrationTestHelper` パターンで作成し、v2 → v3 の自動 migration 検証 + 既存 credential の `custom_fields = '[]'` 復元を確認

### 失敗時のフォールバック

- Migration が例外を投げた場合は Room の標準動作（`fallbackToDestructiveMigration` は使用しない）に従いクラッシュさせ、データ破損を未然に検知する。本機能のために destructive migration を導入しない

## 8. テスト要件

Issue 受入基準 6.1 - 6.6 をそのまま採用（§5 Requirement 6 を参照）。加えて、本要件で
明示する以下の前提を満たすこと。

- 既存テスト（特に `Migration_1_2_Test`、既存 `FillResponseBuilder` 系テスト、`CredentialEditViewModelTest` の既存ケース）が**一切 fail しない**こと
- `Migration_2_3_Test` は `MigrationTestHelper` を用い、v2 で挿入した credential 行が v3 移行後に `custom_fields = '[]'` を持つことを SQL 直読で検証
- `CustomFieldFillResponseTest` は FakeAssistStructure / FakeFieldDescriptor を用い、`AutofillFieldHeuristics` の Role 判定経路と完全に独立して match ロジックのみを検証

## 9. 確認事項（人間決定済み）

| # | 質問 | 決定 | 決定理由 |
|---|---|---|---|
| Q1 | `fieldKey` を保存時に正規化するか | **Option A 採用：入力ままで保存。match 時にのみ正規化済みキーで照合し、表示ラベルはユーザー入力を保持** | 保存値と表示値が一致し、ユーザーが「自分が入れた文字列」をそのまま見られる UX が自然。match は正規化済みキーで照合するため機能上の問題なし |
| Q2 | カスタムフィールド最大件数 | **10 件**（Issue 本文 Requirement 3.5 の既定値） | 典型業務アプリは 3-5 フィールド想定。10 件あれば実用上十分かつ、Autofill 応答 SLA（NFR 3）内で照合可能 |
| Q3 | value 入力欄の表示モード | **Option A 採用：plain text（`inputType=textPassword` を用いない）** | Requirement 5.1 / 5.2 で暗号化・ログ除外は担保済み。入力 UI でのマスクは「自分が何を入れたか確認できない」弊害が大きく、会員番号・社員番号は視認性が高い方が UX 上望ましい。Phase 2 で行単位 visibility トグル追加を検討（本 Phase ではスコープ外） |

## 10. 未決事項（architect への申し送り）

設計フェーズ以降で扱う論点を以下に列挙する。本要件書では実装方針を決定しない。

1. **JSON シリアライザの選定**: `kotlinx.serialization` / Moshi / org.json 等の選択肢があるが、現状リポジトリに JSON ライブラリは未採用。新規依存を追加するか、最小限の手書きエンコーダで済ませるかを architect が判断する
2. **TypeConverter の実装方針**: `@TypeConverter` を `CredentialEntity` レベルに付けるか、`KeyNestDatabase` レベルに `@TypeConverters` で広く適用するか。Room 全体での再利用性とテスト容易性で判断する
3. **正規化関数の配置**: `fieldKey` の正規化処理を `AutofillFieldHeuristics` 内に置くか、独立した utility object（例: `CustomFieldMatcher`）として切り出すかを設計時に決定する。NFR 5 では「単一 utility として実装することを推奨」と方向性のみ示し、配置先はスコープ外
4. **Dataset presentation の見た目**: customField 用 Dataset の RemoteViews 表示文言（例: ラベル `fieldKey: 値` の見せ方）は本要件で規定せず、既存 Dataset presentation との一貫性を保つ範囲で architect が決定
5. **`AesGcmCipher` の入力単位**: customField 1 件ごとに encrypt するか、`List<CustomField>` JSON 全体を 1 ciphertext として encrypt するかを設計時に決定。後者の場合 EncryptedBlob 1 つで完結し列追加が 1 ペアで済むメリットがある
6. **`fieldKey` の重複ハンドリング**: 同一 credential 内に同一 `fieldKey` を 2 件登録した場合の挙動（許容 / save 時に dedupe）。本要件では Requirement 4.4 の「最初に登録された customField を優先」で match 側を決定的にしているため、保存側の dedupe は必須ではないが UX として要検討
7. **入力欄レイアウト**: `credential_edit_activity.xml` の Advanced セクション内に dynamic に行を追加するアプローチ（ViewGroup `addView` / RecyclerView / etc.）の選定

## 11. 関連 Issue / 後続フェーズ

- **Phase 2（別 Issue）**: `detected_fields` ログによる「最近検出されたフィールド」サジェスト機能。autofill 経路で出会った未知の field を蓄積し、credential 編集 UI からワンタップで customField として追加できるようにする
- **Phase 3（別 Issue）**: `AutofillFieldHeuristics` への日本語 hint / 一般的 resourceId pattern 追加（例: 「社員番号」「店舗コード」を heuristic 段階で Role 判定）。Phase 1 の customField とは独立に並行進行可能
- **関連 Issue #63**: Room schema 運用方針（`app/schemas/<applicationId>.data.KeyNestDatabase/` の git tracking）。本 Issue で生成する `3.json` は同運用に従う

## 12. 制約

1. The Issue Implementation shall 既存テスト（unit / instrumented / Migration_1_2_Test を含む）を一切 fail させない
2. The Issue Implementation shall `develop` ブランチに直接 push しない（feature branch + PR レビュー経由）
3. The Issue Implementation shall `main` ブランチに直接 push しない
4. The requirements.md shall 実装コードを含まない。データ構造・API シグネチャの具体例は最小限の擬似コードのみ可とする
5. The Issue Implementation shall Credential Manager API 経路に変更を加えない（スコープ外）
6. The Issue Implementation shall 既存 username / password 補完挙動を変更しない（Requirement 4.3）
