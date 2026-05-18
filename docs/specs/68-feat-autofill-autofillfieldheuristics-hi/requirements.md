# Requirements — Issue #68 feat(autofill): AutofillFieldHeuristics に日本語 hint と一般的 resourceId pattern を追加 (Phase 3)

> 関連: [Issue #68](https://github.com/hitoshiichikawa/KeyNest/issues/68) / Phase 1 = Issue #66（カスタムフィールド機能）/ Phase 2 = Issue #67（detected_fields サジェスト）
> 本書は実装コードを含まない PM 成果物。設計詳細は後続の design.md に委ねる。

## 1. 背景と目的

### 1.1 現状

`app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/parser/AutofillFieldHeuristics.kt` は、AssistStructure の field を `Role.Username` / `Role.Password` / `Role.Unknown` のいずれかに分類する純粋ロジックである。判定は次の 4 段優先順位で行われる：

1. `autofillHints` の明示値（`AUTOFILL_HINT_USERNAME` / `AUTOFILL_HINT_EMAIL_ADDRESS` / `AUTOFILL_HINT_PASSWORD`）
2. `inputType` の password variation
3. `inputType` の email variation
4. `idEntry` / `hint` / `contentDescription` を結合・lowercase 化した文字列に対するキーワード照合

Stage 4 のキーワードは現状以下のみ：

- `USERNAME_KEYWORDS = listOf("user", "email", "id", "account", "login")`
- `PASSWORD_KEYWORDS = listOf("pass", "pwd", "secret")`

### 1.2 問題

日本語環境の業務アプリ・会員制サービスでは以下のような hint / resourceId が使われることが多く、現状の Stage 4 では検出漏れする：

- **日本語 hint**: `ユーザー名` / `ユーザーID` / `メールアドレス` / `Eメール` / `電話番号` / `会員番号` / `社員番号` / `パスワード` / `暗証番号`
- **resourceId pattern**: `member_no` / `member_id` / `customer_id` / `customer_no` / `email_input` / `email_field` / `mail_address` / `tel_input` / `phone_input` / `tel_no` / `staff_id` / `employee_id` / `account_id`

結果として username / password 自動入力が発火せず、ユーザーは手入力に戻ることになる。

### 1.3 目的

`AutofillFieldHeuristics` の Stage 4 キーワード集合に **日本語 hint** と **一般的な業務系 resourceId pattern** を追加し、Phase 1 のカスタムフィールド機能を使わなくても標準の username / password 補完が動く範囲を広げる。

### 1.4 Phase 1 / Phase 2 との関係

- **Phase 1 (Issue #66)** はユーザーが任意の `fieldKey` を手で登録するカスタムフィールド機能。
- **Phase 2 (Issue #67)** は detected_fields ログによる「最近検出されたフィールド」サジェスト UI。
- **本 Phase 3** は heuristic 自体を強化することで、上記いずれの機能にも頼らずに既存 username / password 経路で自動入力が動く範囲を広げる、独立かつ並行進行可能な改善である。

Phase 3 で検出漏れを完全に網羅することはできないため、漏れた fieldKey は引き続き Phase 1 のカスタムフィールド機能で補完される（§4 Q3 決定事項）。

## 2. スコープ

### 2.1 In Scope（変更対象）

| 対象 | 変更内容 |
|---|---|
| `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/parser/AutofillFieldHeuristics.kt` | Stage 4 のキーワード照合に **日本語 hint pattern** と **一般的 resourceId pattern** を OR 結合で追加する |
| `app/src/test/java/io/github/hitoshiichikawa/keynest/autofill/AutofillFieldHeuristicsTest.kt` | 新規 hint / resourceId pattern を網羅する単体テストを追加する |

### 2.2 Out of Scope（本 Issue では扱わない）

- 日本語以外の言語（中国語簡体 / 繁体 / 韓国語 等）の hint pattern 追加（必要に応じて別 Issue。Issue 本文「確認事項 1」の決定により本 Issue は日本語のみに限定）
- カスタムフィールド機能本体（Phase 1 = Issue #66）の変更
- detected_fields の自動収集および編集画面サジェスト UI（Phase 2 = Issue #67）の変更
- AssistStructureParser のロジック変更（既存 tie-breaking ルールに依拠）
- `classify` の戻り値型（`Role` enum）の拡張
- 4 段優先順位（Stage 1〜4）の構造変更。新規 pattern は Stage 4 のキーワード集合への追加に限る
- form context フィルタリング（同一フォーム内の password field 存在を条件にする等。§4 Q2 決定により Option A 採用＝form context フィルタリングなし）
- 既存 Stage 1〜3（autofillHints / inputType）の挙動変更
- 文字正規化アルゴリズム（lowercase + whitespace 除去）の変更
- `Role.PhoneNumber` 等の新しい Role 値の追加（電話番号は username 候補として扱う、Req 1.3）
- `Role.MemberNumber` 等の専用 Role 追加（会員番号 / 社員番号は username 候補として扱う、Req 1.4）

## 3. 用語定義

| 用語 | 定義 |
|---|---|
| Stage 4 | `AutofillFieldHeuristics.classify` 内の 4 段目の判定。`idEntry` / `hint` / `contentDescription` を結合・lowercase 化した文字列に対しキーワード照合を行うフォールバック層 |
| descriptorText | 現実装で Stage 4 が組み立てる比較対象文字列。`idEntry + ' ' + hint + ' ' + contentDescription` を `lowercase()` した結果 |
| `USERNAME_KEYWORDS` | Stage 4 が username 候補と判定する文字列の集合（現状: `user`, `email`, `id`, `account`, `login`） |
| `PASSWORD_KEYWORDS` | Stage 4 が password 候補と判定する文字列の集合（現状: `pass`, `pwd`, `secret`） |
| 日本語 hint pattern | 日本語アプリで使われる hint / contentDescription 文字列のリテラル集合（例: `ユーザー名`, `パスワード`） |
| 一般的 resourceId pattern | 業務系 Android アプリの resourceId 命名で頻出する snake_case 識別子（例: `member_no`, `staff_id`） |
| username 候補 | `classify` が `Role.Username` を返すこと |
| password 候補 | `classify` が `Role.Password` を返すこと |
| 既存 tie-breaking | 現実装の Stage 4 では「password キーワード判定を先に行い、password がマッチすれば即 return `Password`、しなければ username キーワードを照合」という順序。本 Issue でも維持する（Req 3.2） |

## 4. 人間決定済みの確認事項

Issue 本文「確認事項」セクションの 3 件は、本 Issue のコメントで以下のとおり確定済み（2026-05-18）：

### Q1: 国際化の方針 → **日本語のみ**

- **決定**: 本 Issue では **日本語 hint pattern のみ** を追加対象とする。中国語（簡体 / 繁体）/ 韓国語 等の他言語 hint pattern は **別 Issue** で扱う。
- **理由**: Issue 本文の Out of Scope 節で「日本語以外の言語（中国語 / 韓国語 等）の hint pattern 追加（必要に応じて別 Issue）」と明示されていることに従う。本 Issue のスコープを限定し、各言語ごとに独立した品質担保を可能にする。
- **要件への影響**: Req 1 系で挙げる hint pattern は日本語リテラルのみ。Req 2 系の resourceId pattern は ASCII の snake_case であり言語非依存。

### Q2: 誤検出リスクの扱い → **Option A 採用（form context フィルタリングなし）**

- **決定**: `hint="電話番号"` 等が認証用でない入力欄（例: 連絡先登録フォーム）に現れた場合の誤 autofill 提案を **許容する**。既存 OR マッチングに日本語 hint / resourceId pattern を **単純追加** するだけにする（同一フォーム内に password field が存在することを条件とする form context チェックは **追加しない**）。
- **決定者**: Issue コメントで Issue author（人間）が「一旦 Option A にしましょう」と明示。
- **理由**:
  - Autofill Framework の UX 設計上、suggestion が出ても無視できるため誤検出のユーザー影響は軽微。
  - Option B（form context チェック）は AssistStructure のフォーム境界解析が必要となり実装工数が 3〜5 倍。Phase 2（detected_fields）実装にも設計上の影響が波及するため、Phase 3 の単純 heuristic 追加とは責務を分離する方が設計効率が高い。
  - form context フィルタリングは将来 Phase 2 完了後に横断的に導入する余地を残せる。
- **要件への影響**: Requirement 1 / 2 の全ての pattern は AssistStructure の form 構造を一切参照せず、**当該 field 単独の `hint` / `idEntry` / `contentDescription`** のみで判定する。

### Q3: resourceId pattern の網羅性 → **Phase 1 カスタムフィールド機能で補完**

- **決定**: 本 Issue で追加する pattern を組み込んでも検出漏れする resourceId（業務アプリ独自命名）は、ユーザーが Phase 1 のカスタムフィールド機能で `fieldKey` を手動登録することで補完する方針で確定。
- **理由**: 業務アプリの resourceId 命名は事実上無限のバリエーションがあり、heuristic で完全網羅は不可能。Phase 1 がカスタムフィールドという escape hatch を提供しているため、heuristic 側は「典型パターンを広めにカバーする」ことに専念する。
- **要件への影響**: Req 2 系で列挙する pattern は Issue 本文の例示リストに従う。網羅性で漏れた resourceId は Phase 1 経路へ誘導する設計（本 Issue 内で追加で網羅する義務はない）。

## 5. 機能要件 (EARS)

### Req 1: 日本語 hint の検出

`AutofillFieldHeuristics.classify` の Stage 4 で、`hint` / `contentDescription` / `idEntry` を結合した descriptorText が以下の日本語リテラルを含むとき、当該 field を適切な Role 候補として判定する。

- **Req 1.1** — When `hint`, `contentDescription`, or `idEntry` のいずれかが文字列 `ユーザー名` または `ユーザーID` を含むとき, the heuristic shall その field を **username 候補**（`Role.Username`）として判定する。
- **Req 1.2** — When `hint`, `contentDescription`, or `idEntry` のいずれかが文字列 `メールアドレス` または `Eメール` を含むとき, the heuristic shall その field を **username 候補**（`Role.Username`）として判定する。
- **Req 1.3** — When `hint`, `contentDescription`, or `idEntry` のいずれかが文字列 `電話番号` を含むとき, the heuristic shall その field を **username 候補**（`Role.Username`）として判定する。
- **Req 1.4** — When `hint`, `contentDescription`, or `idEntry` のいずれかが文字列 `会員番号` または `社員番号` を含むとき, the heuristic shall その field を **username 候補**（`Role.Username`）として判定する。
- **Req 1.5** — When `hint`, `contentDescription`, or `idEntry` のいずれかが文字列 `パスワード` または `暗証番号` を含むとき, the heuristic shall その field を **password 候補**（`Role.Password`）として判定する。

> 補足: 現実装の Stage 4 は `descriptorText` を `lowercase()` してから keyword の `contains` を行っている。日本語文字（ひらがな / カタカナ / 漢字 / 全角英字）は Unicode 上 `lowercase()` で変化しないため、日本語リテラルをそのまま `USERNAME_KEYWORDS` / `PASSWORD_KEYWORDS` に追加すれば既存ロジックがそのまま動作する。実装方針の詳細（リスト merge 形式 / 別変数化 等）は design.md で決定する。

### Req 2: resourceId pattern の expand

`AutofillFieldHeuristics.classify` の Stage 4 において、`idEntry` を含む descriptorText が以下の snake_case 識別子のいずれかを含むとき、当該 field を **username 候補**（`Role.Username`）として判定する。

- **Req 2.1** — When the resourceId（`idEntry`）が `member_no` / `member_id` / `customer_id` / `customer_no` のいずれかを含むとき, the heuristic shall その field を **username 候補** として判定する。
- **Req 2.2** — When the resourceId（`idEntry`）が `email_input` / `email_field` / `mail_address` のいずれかを含むとき, the heuristic shall その field を **username 候補** として判定する。
- **Req 2.3** — When the resourceId（`idEntry`）が `tel_input` / `phone_input` / `tel_no` のいずれかを含むとき, the heuristic shall その field を **username 候補** として判定する。
- **Req 2.4** — When the resourceId（`idEntry`）が `staff_id` / `employee_id` / `account_id` のいずれかを含むとき, the heuristic shall その field を **username 候補** として判定する。

> 補足: Req 2 のキーワードは ASCII snake_case のため `lowercase()` 適用後の descriptorText に対して `contains` 一致を行えばよい。既存 USERNAME_KEYWORDS の `user` / `email` / `id` / `account` などと一部重複する文字列（例: `email_input` には `email` が含まれる）があるが、OR 結合のため判定結果は同一であり問題にならない。

### Req 3: 既存挙動の維持

- **Req 3.1** — The new patterns shall 既存の英語 hint / resourceId pattern（`user` / `email` / `id` / `account` / `login` / `pass` / `pwd` / `secret`）と **OR 結合で判定**される。既存パターンによる既存挙動は **一切変更されない**。
- **Req 3.2** — When 同一 descriptorText が password キーワードと username キーワードの両方にマッチする場合, the existing tie-breaking rule shall 適用される（現実装どおり password を優先する）。例: `idEntry="user_password"` は Phase 3 後も `Role.Password` を返す。
- **Req 3.3** — The Stage 1〜3（autofillHints / inputType password / inputType email）shall **一切変更されない**。Phase 3 の追加は Stage 4 のキーワード集合に対するもののみとする。
- **Req 3.4** — The `Role` enum shall **拡張されない**（`Username` / `Password` / `Unknown` の 3 値のまま）。電話番号 / 会員番号 / 社員番号は全て `Role.Username` として返す。
- **Req 3.5** — The `extractMatchKeys` および `normalizeKey` API（Phase 1 で追加された custom field 用ユーティリティ）shall **一切変更されない**。Phase 3 は `classify` 内の Stage 4 ロジックのみを対象とする。

### Req 4: テスト

- **Req 4.1** — The `AutofillFieldHeuristicsTest` shall 日本語 hint の各 pattern について、それぞれ独立した `@Test` で username/password 候補として判定されることを検証する。最低限以下を含む：
  - `hint="ユーザー名"` → `Role.Username`
  - `hint="ユーザーID"` → `Role.Username`
  - `hint="メールアドレス"` → `Role.Username`
  - `hint="Eメール"` → `Role.Username`
  - `hint="電話番号"` → `Role.Username`
  - `hint="会員番号"` → `Role.Username`
  - `hint="社員番号"` → `Role.Username`
  - `hint="パスワード"` → `Role.Password`
  - `hint="暗証番号"` → `Role.Password`
- **Req 4.2** — The `AutofillFieldHeuristicsTest` shall 新規 resourceId pattern について同様に独立した `@Test` で検証する。最低限以下を含む：
  - `idEntry="member_no"` → `Role.Username`
  - `idEntry="member_id"` → `Role.Username`
  - `idEntry="customer_id"` → `Role.Username`
  - `idEntry="customer_no"` → `Role.Username`
  - `idEntry="email_input"` → `Role.Username`
  - `idEntry="email_field"` → `Role.Username`
  - `idEntry="mail_address"` → `Role.Username`
  - `idEntry="tel_input"` → `Role.Username`
  - `idEntry="phone_input"` → `Role.Username`
  - `idEntry="tel_no"` → `Role.Username`
  - `idEntry="staff_id"` → `Role.Username`
  - `idEntry="employee_id"` → `Role.Username`
  - `idEntry="account_id"` → `Role.Username`
- **Req 4.3** — All テスト shall 既存テスト（`AutofillFieldHeuristicsTest` の現行 18 ケース、`Migration_1_2_Test`、Phase 1 / Phase 2 が追加したテスト群、その他 unit / instrumented test）を **一切 fail させない**。
- **Req 4.4** — Tests shall 既存ファイル `app/src/test/java/io/github/hitoshiichikawa/keynest/autofill/AutofillFieldHeuristicsTest.kt` の既存 `private fun blank()` ヘルパーと同じパターンで `FieldDescriptor` を組み立てる（テスト追加箇所の一貫性を維持）。

## 6. 非機能要件 (NFR)

- **NFR 1 (パフォーマンス)**: Stage 4 のキーワード照合は依然 `lowercase()` 後の単純 `contains` ループ。追加するキーワード数（hint 約 9 件、resourceId 約 13 件）は O(n) で、Autofill 応答 SLA を逼迫しない。
- **NFR 2 (後方互換性)**: Stage 1〜3 / `extractMatchKeys` / `normalizeKey` / `Role` enum / `FieldDescriptor` data class の public シグネチャは変更しない。
- **NFR 3 (一貫性)**: 新規日本語 hint pattern は **Issue 本文と requirements §5 で挙げたリテラルとビット一致** で実装する（例: `ユーザー名` の `ー` は長音記号 U+30FC、`ユーザーID` の `ID` は半角英字）。表記揺れ（全角 ID / 半角 ID / ハイフンの違い等）は将来の別 Issue で扱う。
- **NFR 4 (i18n)**: 本 Issue は日本語限定（§4 Q1）。多言語化は別 Issue。
- **NFR 5 (誤検出許容)**: §4 Q2 の決定に従い、form context フィルタリングは **行わない**。`hint="電話番号"` 等が認証外フォームに現れた場合の誤 autofill suggestion は許容範囲とする。
- **NFR 6 (テスト独立性)**: 既存 `AutofillFieldHeuristicsTest` は Robolectric / instrumentation を必要としない pure unit test（`object` + `FieldDescriptor` data class）。Phase 3 で追加するテストも同方針を維持する。

## 7. 受入基準まとめ (Definition of Done)

- [ ] `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/parser/AutofillFieldHeuristics.kt` の Stage 4 キーワード集合に Req 1 / Req 2 の全 pattern が追加されている
- [ ] `app/src/test/java/io/github/hitoshiichikawa/keynest/autofill/AutofillFieldHeuristicsTest.kt` に Req 4.1 / Req 4.2 が要求する全テストケースが追加されている
- [ ] 既存テスト（`AutofillFieldHeuristicsTest` の現行 18 ケースを含む）が全て pass
- [ ] `./gradlew test` がローカルで成功
- [ ] PR が `develop` を base として作成される
- [ ] Issue #68 に PR リンクと案内コメントが投稿される

## 8. 依存・関連

### 8.1 依存（前提）

- **Phase 1 (Issue #66)** で追加された `extractMatchKeys` / `normalizeKey` API は本 Issue で **触らない**。Phase 1 マージ済みであることは前提とするが、ロジック依存はない（Stage 4 のキーワード集合のみを変更するため）。
- **Phase 2 (Issue #67)** とは並行進行可能。Phase 2 が detected_fields に蓄積する fieldKey は本 Issue の heuristic 強化と独立に動作する。

### 8.2 関連 Issue

- [Issue #66](https://github.com/hitoshiichikawa/KeyNest/issues/66) — Phase 1: カスタムフィールド機能。本 Issue で検出漏れする resourceId はこちらで補完。
- [Issue #67](https://github.com/hitoshiichikawa/KeyNest/issues/67) — Phase 2: detected_fields サジェスト。Phase 3 で heuristic 強化されると `Role.Unknown` が減るため、detected_fields に蓄積される「未識別 fieldKey」も相対的に減る効果がある（副次効果。本 Issue の必須要件ではない）。

### 8.3 同時に動かす可能性のあるブランチ

- 本 Issue の作業ブランチ: `claude/issue-68-impl-feat-autofill-autofillfieldheuristics-hi`
- Phase 1 / Phase 2 が並行マージ中の場合、`AutofillFieldHeuristics.kt` への変更コンフリクトに注意。Phase 1 は `extractMatchKeys` / `normalizeKey` を追加済（既マージ）、Phase 2 はこのファイルを変更しない予定。

## 9. テスト方針

### 9.1 テストフレームワーク

既存 `AutofillFieldHeuristicsTest` と同じ方針を踏襲する：

- JUnit 4（`@Test`）
- Truth (`com.google.common.truth.Truth.assertThat`)
- pure unit test（Robolectric 不要 / instrumentation 不要）
- `FieldDescriptor` を直接組み立てる（`private fun blank()` ヘルパーを再利用）

### 9.2 テストケース粒度

- 1 pattern につき 1 `@Test`（Req 4.1 / Req 4.2 で列挙した約 22 ケース分）
- 各テストは Issue 本文と requirements §5 のリテラルとビット一致で `hint` / `idEntry` を設定する
- 既存ヘルパーパターン（`blank().copy(hint = "...")` または `blank().copy(idEntry = "...")`）を継承

### 9.3 既存挙動回帰テスト

新規テストの追加だけでなく、以下の既存挙動が壊れていないことを `./gradlew test` で確認する：

- `classify_returnsUnknown_whenNoSignalsPresent` 系（descriptor が空 → `Unknown` のまま）
- `classify_returnsUnknown_whenIdEntryHasUnrelatedKeyword`（`submit_button` などは引き続き `Unknown`）
- `classify_prefersPasswordOverUsername_whenBothKeywordsAppear`（既存 tie-breaking ルールが維持される）
- Stage 1〜3（autofillHints / inputType）の全テスト

### 9.4 マニュアル / インテグレーションテスト

本 Issue は pure unit test 粒度で完結する。Autofill 経路の end-to-end 検証は既存の `AssistStructureParser` 系テストに委ねる（本 Issue ではテスト追加しない）。

## 10. リスクと未決事項

### R1: descriptorText の日本語と ASCII 混在による誤マッチ

- `descriptorText` は `idEntry + ' ' + hint + ' ' + contentDescription` を連結し `lowercase()` した結果。例えば `idEntry="user_id"` + `hint="ユーザー名"` のとき descriptorText は `"user_id ユーザー名 "` となり、`user_id` 部分のスペースなどとの境界で偶発マッチが起きるかは要設計確認。
- **対応**: 現実装は `contains` ベースのため境界は問わない（部分一致）。本 Issue で `contains` 方式を変えないため、Phase 1 で確立された方針に沿う。検出精度の精緻化（境界付き照合 / 単語境界正規表現）は別 Issue とする。

### R2: `ID` の半角 vs 全角

- 日本語 hint `ユーザーID` の `ID` は半角英字（U+0049 U+0044）を想定。アプリによっては全角 `ＩＤ`（U+FF29 U+FF24）を使う可能性がある。
- **対応**: NFR 3 に従い本 Issue では Issue 本文どおりの半角リテラルのみ採用。全角バリアントは別 Issue で対応する。

### R3: 既存キーワード `id` との重複

- 既存 `USERNAME_KEYWORDS` に `id` が含まれており、Req 2 の `member_id` / `customer_id` / `staff_id` / `employee_id` / `account_id` は `id` を含むため、新規追加せずとも既存 `id` キーワードで username 判定される（既に動作している）。
- **対応**: それでも Req 2 の明示パターンを追加する意味は「`id` のみだとあいまい（"spinner_id" 等の誤マッチ）が起きうるが、`member_id` / `staff_id` 等の具体的 pattern を併記することで意図と Read 性が向上する」点にある。実装側で重複する pattern を残すか集約するかは design.md で判断する（テスト要件 Req 4.2 は満たすこと）。

### R4: 日本語キーワードと既存英語キーワードの順序

- 現実装は `PASSWORD_KEYWORDS.any { descriptorText.contains(it) }` で判定し、true なら即 return。よって password キーワードに `パスワード` / `暗証番号` を追加すれば既存挙動を保ったまま機能する。
- **対応**: 設計時に「日本語キーワードを既存リストに append するか、別 list として OR 結合するか」を選択。挙動上は等価であり、可読性で判断する。

### R5: 誤検出による UX 悪化（§4 Q2 Option A 採用の帰結）

- §4 Q2 で form context フィルタリングを **行わない** ことを決定済みのため、`hint="電話番号"` を持つ連絡先入力欄等で誤った autofill suggestion が出る可能性がある。
- **対応**: Autofill Framework の標準 UX（suggestion を無視できる）に依拠し、Phase 3 では受容する。Phase 2 完了後に detected_fields の運用データを観察し、誤検出が頻発するようなら別 Issue で form context フィルタリング導入を検討する。

## 11. 制約

1. The Issue Implementation shall 既存テスト（unit / instrumented を含む）を **一切 fail させない**（Req 4.3 / NFR 6）。
2. The Issue Implementation shall `develop` ブランチに直接 push しない（feature branch + PR レビュー経由）。
3. The Issue Implementation shall `main` ブランチに直接 push しない。
4. The Issue Implementation shall `AutofillFieldHeuristics` の `classify` / Stage 1〜3 / `Role` enum / `FieldDescriptor` / `extractMatchKeys` / `normalizeKey` の public シグネチャを変更しない（NFR 2）。
5. The Issue Implementation shall Phase 1 / Phase 2 の Credential / Room / UI 経路に一切手を加えない（Out of Scope §2.2）。
6. The Issue Implementation shall Credential Manager API 経路に一切手を加えない（Phase 1 と同じ制約）。
7. The requirements.md shall 実装コードを含まない。データ構造・キーワード集合の具体例は最小限の擬似コード / リテラル列挙のみ可とする。
8. The Issue Implementation shall §4 Q2 決定（Option A）に従い form context フィルタリングを **導入しない**（NFR 5）。
9. The Issue Implementation shall §4 Q1 決定に従い日本語以外の hint pattern を本 Issue で追加しない（NFR 4）。
