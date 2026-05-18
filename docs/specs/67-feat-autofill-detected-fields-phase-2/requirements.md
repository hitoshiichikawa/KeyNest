# Requirements — Issue #67 feat(autofill): detected_fields ログによる「最近検出されたフィールド」サジェスト (Phase 2)

> 関連: [Issue #67](https://github.com/hitoshiichikawa/KeyNest/issues/67) / Phase 1 = Issue #66 / Phase 3 = heuristic 強化 (別 Issue)
> 本書は実装コードを含まない PM 成果物。設計詳細は [design.md](./design.md) を参照。

## 1. 背景と目的

Phase 1 (Issue #66) のカスタムフィールド機能では、ユーザーが対象アプリの hint / autofillHints / resourceId を **目視で確認** してから `fieldKey` を手入力する必要がある。これは非エンジニアにとって参入障壁が高い。

KeyNest の Autofill Service は `FillRequest` 経由で AssistStructure の全 ViewNode（hint / autofillHints / resourceId / contentDescription / text）にアクセスできる。Autofill 発火時にこれら match キーを内部 DB に記録しておけば、編集画面で「最近検出されたフィールド」をサジェストし、クリック 1 つで `fieldKey` に転送できる。

**目的**: カスタムフィールド (Phase 1) のセットアップ UX を改善し、ユーザーが対象アプリの内部構造を意識せずにフィールド追加できる状態を作る。

## 2. スコープ / Out of Scope

### In Scope

- 新規 Room エンティティ `DetectedFieldEntity` と `DetectedFieldDao` の追加。
- Room schema v3 → v4 マイグレーション (`Migration_3_4`) と `detected_fields` テーブル新設。
- `KeyNestAutofillService.onFillRequest` 内での detected field upsert（**KeyNest に credential が登録済みの packageName のみ**、§4 Q2 参照）。
- per-package 検出件数の LRU 上限 (50 件、§4 Q3 参照)。
- `text` source を自動収集の対象外とする（§4 Q1 参照）。
- `CredentialEditActivity` のカスタムフィールド入力欄に「最近検出されたフィールド」サジェスト UI を追加。
- 上記に対する Migration テスト / DAO テスト / Autofill 統合テスト / ViewModel テスト。

### Out of Scope (本 Issue では扱わない)

- 検出履歴の TTL ベース削除（時間経過での自動 purge）。
- 全 user 横断の "popular fields" 統計。
- 未登録 packageName からの収集（Q2-A により除外）。
- detected_fields の export / import / 設定画面での閲覧 UI。
- カスタムフィールド機能本体（Phase 1 = Issue #66 に依存）。

## 3. 依存関係

### 3.1 Phase 1 (Issue #66) への依存

本 Issue は Phase 1 の成果物に **次の点で依存** する：

| 依存項目 | 必要な Phase 1 成果物 |
|---|---|
| Schema バージョン | Phase 1 で v3 が確定していること（本 Issue は v3 → v4 マイグレーション） |
| カスタムフィールド入力 UI | `CredentialEditActivity` の「カスタムフィールド」サブセクション（Advanced セクション配下） |
| サジェスト転送先 | カスタムフィールド入力行の `fieldKey` テキスト入力欄 |
| 正規化関数 | `AutofillFieldHeuristics.normalizeKey()` / `extractMatchKeys()`（一貫性のため流用） |

**着手前提**: Phase 1 の実装 PR が `develop` にマージされた後に Phase 2 実装に着手する。設計フェーズ（本 PR）は Phase 1 設計 PR (#69) と並行で進めてよい。詳細は §10 リスク参照。

### 3.2 Phase 3 (heuristic 強化) との関係

Phase 3 では detected_fields のサンプルから heuristic ルール（USERNAME_KEYWORDS / PASSWORD_KEYWORDS 等）を強化することを想定している。本 Phase はそのためのデータ蓄積基盤を提供する役割も持つ。Phase 3 設計時に detected_fields のスキーマ変更が必要になった場合は、別途 Migration で対応する。

## 4. 人間決定済みの確認事項

Issue 本文「確認事項」セクションの 3 件は、本 Issue のコメントで以下のとおり確定済み（2026-05-18）：

### Q1: text source の自動収集可否 → **Option A: 除外**

- **決定**: detected_fields に保存する `source` は `autofillHints` / `hint` / `resourceId` / `contentDescription` の **4 種類のみ**。`text`（ユーザー入力値を含みうる）は **収集しない**。
- **理由**:
  - `text` はユーザーがすでに入力した値（パスワード平文を含みうる）を含む。これを DB に保存するのは個人情報・セキュリティ上のリスクが大きい。
  - Google Play ポリシー「機密データの取り扱い」に抵触しうる。
  - autofillHints / resourceId で大半のフィールド識別は可能。
- **要件への影響**: Req 1.2 の `source` enum から `text` を除外。Req 3.1 で「`text` を含む全 source」と書きうる箇所も同様。
- **将来の余地**: ユーザーが明示的に opt-in する設定を Phase 3 以降で検討する余地はあるが、Phase 2 ではデフォルト除外を不可逆として扱う。

### Q2: 収集タイミング → **Option A: 登録済み packageName のみ**

- **決定**: `KeyNestAutofillService.onFillRequest` 内で `DetectedFieldDao.upsert` を呼ぶのは、**`CredentialDao.findByPackage(callerPackage)` が 1 件以上の credential を返した場合のみ**。
- **理由**:
  - Phase 2 のユースケースは「カスタムフィールド編集の補助」で、未登録アプリの蓄積は現時点で価値が薄い。
  - データ量・バッテリー影響を最小化できる。
  - 全 FillRequest 記録すると 1 ユーザーあたり数百〜数千の packageName が蓄積される懸念があり、長期運用での DB 肥大化と plausible deniability（ユーザーがどのアプリを開いたかの履歴漏洩）のリスクが増す。
- **要件への影響**: Req 3.1 の upsert ゲート条件として明示。
- **副作用**: 新規 credential 追加時、その packageName の detected_fields は 0 件のまま（次回 FillRequest で蓄積開始）。サジェスト UI は Req 4.3 のとおり「履歴なし」プレースホルダで対応。

### Q3: per-package 検出件数の上限 → **Option A: LRU 50 件**

- **決定**: detected_fields は **packageName ごとに最新 50 件まで保持**。51 件目を upsert する際は同 packageName 内で `lastDetectedAt` が最古の row を 1 件削除する。
- **理由**:
  - TTL 削除を別 Issue に先送りせずに本 Phase 単体で DB 肥大化を防げる。
  - 50 件は 1 アプリの form を見渡すのに十分なサジェスト候補数。
  - 同一 (packageName, fieldKey, source) は複合 PK で upsert されるため、上限到達は実質的に「過去に検出されたが現在は使われていない fieldKey」が淘汰される動き（LRU は時間軸）になる。
- **要件への影響**: Req 1.4 として LRU 制約を追加。
- **実装方針**: upsert 関数内でカウントを取って超過分を `lastDetectedAt ASC LIMIT 1` で削除する `@Transaction` を持たせる。詳細は design.md を参照。

## 5. 機能要件 (EARS)

### Req 1: データモデル

- **Req 1.1** — `DetectedFieldEntity` shall `packageName: String` / `fieldKey: String` / `source: String` / `lastDetectedAt: Long` の 4 カラムを持つ。
- **Req 1.2** — The `source` カラム shall 文字列 enum として **`autofillHints` / `hint` / `resourceId` / `contentDescription` のいずれか** の値を保持する。`text` は除外する（§4 Q1）。
- **Req 1.3** — The primary key shall `(packageName, fieldKey, source)` の **複合キー** とする。
- **Req 1.4** — Per packageName の detected_fields 件数 shall **最新 50 件以内** に保たれる。51 件目の upsert 時、同 packageName 内で `lastDetectedAt` が最古の row を 1 件削除する（LRU、§4 Q3）。
- **Req 1.5** — Table `detected_fields` shall `(packageName, lastDetectedAt DESC)` を高速に走査できる index を持つ（サジェスト UI の主要クエリパス）。

### Req 2: Migration

- **Req 2.1** — When Room schema を v3 → v4 にアップグレードしたとき, the migration shall `detected_fields` テーブルを新規作成する（複合 PK + index を含む）。
- **Req 2.2** — The migration shall 既存の `credentials` テーブルおよびそのデータに **一切影響を与えない**。
- **Req 2.3** — The migration shall `fallbackToDestructiveMigration` に依存しない。
- **Req 2.4** — The migration shall Room の auto-generated schema (`app/schemas/.../4.json`) を git tracking 対象として commit に含める（Issue #63 で確立した運用）。

### Req 3: Autofill による自動収集

- **Req 3.1** — When `KeyNestAutofillService.onFillRequest` が呼ばれ **かつ** `CredentialDao.findByPackage(callerPackage)` が 1 件以上を返したとき, the service shall AssistStructure の全 editable ViewNode について各 source の値を抽出し `DetectedFieldDao.upsert(packageName, fieldKey, source, lastDetectedAt)` を呼ぶ（§4 Q2）。
- **Req 3.2** — The upsert shall `lastDetectedAt = System.currentTimeMillis()` を更新する。同 PK が既存の場合は UPDATE、無い場合は INSERT。
- **Req 3.3** — The detection logic shall main thread を blocking しない。`IO Dispatcher` または `Default Dispatcher` 上で実行し、callback 返却（`callback.onSuccess(...)`）を **遅延させない**（fire-and-forget）。
- **Req 3.4** — The detection logic shall 既存の locked Dataset 構築経路（`FillResponseBuilder.buildLockedResponse`）と **並行 or 後段** で実行され、autofill 応答の latency に **加算してはならない**（NFR 2.1 既存 SLA を維持）。
- **Req 3.5** — When 抽出した `fieldKey` が空文字列または空白のみのとき, the service shall その row を upsert しない（normalize 後に判定）。
- **Req 3.6** — The detection logic shall `text`（ViewNode.text）を **収集対象に含めない**（§4 Q1）。
- **Req 3.7** — The detection logic shall password ViewNode（`AutofillFieldHeuristics.classify` が `Role.Password` を返す node）について、`text` 以外の source（autofillHints / hint / resourceId / contentDescription）は **収集する**（fieldKey ラベルは個人情報ではないため）。Phase 2 では password node 自体を除外しない。
- **Req 3.8** — When upsert 中に例外が発生したとき, the service shall 例外を **swallow** し autofill 応答に影響を与えない（既存 `try/catch` 同様 fail-open）。`SafeLogger.warn` で警告ログを残す。

### Req 4: 編集画面でのサジェスト

- **Req 4.1** — When ユーザーが `CredentialEditActivity` のカスタムフィールド「フィールド追加」ボタンを押したとき, the UI shall 対象 credential の `packageName` と一致する `detected_fields` から **最新 N 件（既定 N = 10）** を `lastDetectedAt DESC` 順で BottomSheet または横スクロール chip リストとして表示する。
- **Req 4.2** — When ユーザーがサジェスト項目をクリックしたとき, the UI shall 該当 row の `fieldKey` を **新規追加された customField 行の `fieldKey` 入力欄に転送** する（value 入力欄は空のまま）。
- **Req 4.3** — If 対象 packageName の `detected_fields` が 0 件のとき, the UI shall 「履歴なし」のプレースホルダ表示を出す（chip 領域に空状態のテキストを表示、またはサジェスト UI 自体を非表示にする）。
- **Req 4.4** — The サジェスト一覧 shall **すでに同じ `fieldKey` が customFields 配列に存在する** 項目を除外するか、視覚的に「追加済み」マークを表示する（誤って重複追加することを防ぐ）。実装方式の選択は design.md で確定する。
- **Req 4.5** — The UI shall サジェスト表示時にメインスレッドを 100 ms 以上ブロックしない（DB クエリは `viewModelScope` 上で実行）。
- **Req 4.6** — The サジェスト UI shall 新規作成モード / 編集モード双方で動作する（Phase 1 が「編集モードでの customField 編集」を含む前提。Phase 1 で編集モードが除外された場合は本要件もそれに従う、§10 リスク参照）。

### Req 5: テスト

- **Req 5.1** — The `Migration_3_4_Test` shall v3 → v4 migration 後に `detected_fields` テーブルが追加され、複合 PK と index が定義どおりであり、既存 `credentials` データが保持されることを検証する（Migration_1_2_Test / Migration_2_3_Test と同じ instrumented test 流儀）。
- **Req 5.2** — The `DetectedFieldDaoTest` shall `upsert` と `getByPackage` の挙動を検証する：(a) 新規行 INSERT、(b) 同一 PK 上書き UPDATE、(c) `lastDetectedAt DESC` 順での取得、(d) Req 1.4 の LRU 上限挙動。
- **Req 5.3** — The `KeyNestAutofillService` 周辺の統合 / 単体テスト shall FillRequest を simulate した後に、登録済み packageName の場合は upsert が呼ばれ、未登録 packageName の場合は呼ばれないことを検証する。
- **Req 5.4** — The `CredentialEditViewModelTest` shall サジェスト項目クリックで `fieldKey` が新規 customField 行に転送されることを検証する。
- **Req 5.5** — All テスト shall 既存テスト (`Migration_1_2_Test` / `Migration_2_3_Test` / Phase 1 が追加するテスト群 / 既存 unit test) を **一切 fail させない**。

## 6. 非機能要件 (NFR)

- **NFR 1 (プライバシー)**: detected_fields には field の plaintext 入力値を **含めない** (`text` 除外、§4 Q1)。`fieldKey` は app 開発者が定めたラベル（resource id / autofillHints 等）であり、個人情報を含まない前提で扱う。
- **NFR 2 (パフォーマンス)**:
  - autofill 応答時間に **加算してはならない**（Req 3.4）。`callback.onSuccess(...)` 呼出後の fire-and-forget で OK。
  - サジェスト UI の DB クエリは 50 件 × 該当 packageName のみ。`(package_name, last_detected_at DESC)` index で O(log n) を確保する。
- **NFR 3 (耐故障性)**: detection upsert の失敗は autofill 本体に影響を与えない（Req 3.8）。DB 破損時もユーザー閲覧体験を阻害しない fail-open 戦略。
- **NFR 4 (互換性)**: Room schema v3 → v4 マイグレーションは forward only。downgrade はサポートしない（既存方針踏襲）。
- **NFR 5 (一貫性)**: Phase 1 と同じ正規化関数 (`AutofillFieldHeuristics.normalizeKey()`) を流用し、`fieldKey` の検出値とユーザー入力値の比較で一貫した挙動を保つ。
- **NFR 6 (i18n)**: サジェスト UI のラベル（「最近検出されたフィールド」「履歴なし」等）は既存リソース命名規約（`strings.xml`、ja デフォルト）に従う。

## 7. UX 仕様

### 7.1 サジェスト表示位置

- 「フィールド追加」ボタン押下 → 新しい customField 行が動的に追加される（Phase 1 仕様）→ その新規行の **直上または直下** に「最近検出されたフィールド」chip リストを 1 段表示。
- 候補 chip タップ → タップした chip の `fieldKey` を新規行の `fieldKey` EditText にセット。サジェスト chip リスト自体は非表示にする（または「追加済み」マークに切り替え）。
- 表示中の chip 数 = **最大 10 件**（Req 4.1）。スクロール可能な横スクロール chip group か BottomSheet の二択は design.md で確定。

### 7.2 「履歴なし」状態の扱い

- 対象 packageName の detected_fields が 0 件のとき、chip 領域に「履歴なし。アプリで一度フォームを開くと候補が表示されます」相当の説明テキストを表示する（i18n 対応）。
- 「履歴なし」表示はあくまで補助情報。手入力での fieldKey 追加は通常どおり可能。

### 7.3 すでに追加済みの fieldKey の扱い

- Req 4.4 に従い、現在の customFields に存在する `fieldKey`（normalize 一致）はサジェストから除外する（design.md で確定）。

## 8. データ移行 / 互換性

- 既存ユーザー（Phase 1 マージ済みで Phase 2 を初めて起動するユーザー）の detected_fields は **空テーブル** からスタートする。
- 初回起動時の Migration_3_4 は SQL のみで完結し、Keystore 等の I/O を伴わない。
- Phase 1 で encrypt 済みの customField データは Phase 2 の Migration で **一切触らない**。

## 9. セキュリティ / プライバシー

| 項目 | 方針 |
|---|---|
| plaintext 値の DB 保存 | しない（`text` 除外、Req 3.6） |
| `fieldKey` の暗号化 | しない（resource id 等のラベルは個人情報を含まない前提） |
| `packageName` の暗号化 | しない（既存 `credentials` 同様） |
| ログ出力 | `SafeLogger` を使用し plaintext 値・packageName の verbose 出力を抑止（既存方針踏襲） |
| Plausible deniability | 登録済み packageName のみ収集する（Q2-A）ことで、ユーザーが KeyNest と無関係なアプリを開いた履歴が DB に残らないようにする |

## 10. リスクと未決事項

### R1: Phase 1 マージ前の本 Issue 着手 (高優先度)

- Phase 1 (Issue #66) の設計 PR (#69) が未マージ。Phase 1 のスキーマ (v3) が確定していない状態で本 Phase 2 設計を進めている。
- **対応**: Phase 1 の最終決定 (v3 のカラム構成 / migration policy / customField の `fieldKey` 入力欄の view id 等) を **本設計 PR レビュー時点で再確認** する。Phase 1 で大幅変更があった場合、本 Phase 2 設計を update する。
- **実装着手の前提**: Phase 1 実装 PR が `develop` にマージされてから Phase 2 実装に着手すること（Issue 本文「依存」セクションを遵守）。

### R2: 編集モードでの customField 編集が Phase 1 でサポートされない場合

- Phase 1 設計 PR (#69) §13 で「Phase 1 では新規作成モードのみ編集可、編集モードは Phase 1.5 として切り出す可能性」が申し送られている。
- **対応**: 本 Phase 2 のサジェスト UI も Phase 1 と同じスコープに合わせる（Phase 1 が新規モードのみなら Phase 2 も新規モードのみ）。Req 4.6 はこの仕様変更に追従する。

### R3: detected_fields の `lastDetectedAt` が衝突する可能性

- `System.currentTimeMillis()` の解像度 (ms) で同時 upsert が衝突する稀ケース。
- **対応**: PK は `(packageName, fieldKey, source)` で行は一意。`lastDetectedAt` の小数点差は LRU 削除順に微影響を与えるが実用上問題ない（同 ms タイブレークは row id 順）。

### R4: 50 件 LRU の上限超過時の DELETE トランザクション競合

- 複数の `FillRequest` が同時並行で同 packageName を upsert すると、LRU 削除トランザクションが互いに干渉する。
- **対応**: DAO 側で `@Transaction` を付けて upsert + count + delete を atomic にする。Room の WAL モードで併発処理は SQLite レベルで shielded。

### R5: 一部 ROM の AssistStructure が autofillHints を返さない

- Android バージョン / vendor 実装によっては `autofillHints` が null。
- **対応**: 既存 `AutofillFieldHeuristics.classify` / `extractMatchKeys` のロジックを流用し、null 安全に扱う。Req 1.2 の source enum は 4 種類のうち availability があるものを記録する。

### R6: detected_fields の発火タイミングが credential 登録前

- ユーザーが KeyNest にアプリの credential を登録していないとき、その packageName の `text` フィールドを開いても **detection は走らない**（Q2-A の挙動）。
- **影響**: 「credential 登録 → 編集画面を開く → サジェストが出る」までに、最低 1 回ユーザーが対象アプリを再度開く必要がある。
- **緩和案**: 編集画面の chip 領域に「アプリで一度フォームを開いてから戻ってください」相当の説明を表示（§7.2）。

## 11. 受入基準まとめ (Definition of Done)

- [x] `docs/specs/67-feat-autofill-detected-fields-phase-2/requirements.md` を本書として確定
- [ ] `docs/specs/67-feat-autofill-detected-fields-phase-2/design.md` で §10 R1〜R6 への対処と公開 IF が示されている
- [ ] `docs/specs/67-feat-autofill-detected-fields-phase-2/tasks.md` で各タスクが独立コミット可能な粒度で分割されている
- [ ] 設計 PR が `develop` を base として作成され、`gh pr view <PR> --json baseRefName` で `develop` と一致を確認
- [ ] Issue #67 に設計 PR リンクと案内コメントが投稿され、ラベルが `claude-claimed` → `awaiting-design-review` に付け替えられている

## 12. 制約

- `develop` / `main` への直接 push は禁止。feature branch + PR レビュー経由。
- 既存テスト (`Migration_1_2_Test` / 既存 unit test / Phase 1 が追加するテスト群) を **一切 fail させない**。
- Credential Manager API 経路には一切手を加えない（Phase 1 と同じ制約）。
- 既存 username / password 補完挙動・Phase 1 の customField 補完挙動を **変更しない**（新規追加のみ）。
- 設計 PR には実装コードを含めない（spec only）。
