# Implementation Notes — Issue #68 feat(autofill): AutofillFieldHeuristics に日本語 hint と一般的 resourceId pattern を追加 (Phase 3)

> 関連: [requirements.md](./requirements.md) / [Issue #68](https://github.com/hitoshiichikawa/KeyNest/issues/68)

## 実装サマリ

| 変更ファイル | 1-2 行サマリ |
|---|---|
| `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/parser/AutofillFieldHeuristics.kt` | Stage 4 の `USERNAME_KEYWORDS` / `PASSWORD_KEYWORDS` に Req 1（日本語 hint）と Req 2（snake_case resourceId pattern）を OR 結合で追加。`classify` のロジック・順序・Stage 1〜3・`Role` enum・`extractMatchKeys` / `normalizeKey` は一切変更していない（Req 3）。 |
| `app/src/test/java/io/github/hitoshiichikawa/keynest/autofill/AutofillFieldHeuristicsTest.kt` | Req 4.1 の 9 ケース（日本語 hint）と Req 4.2 の 13 ケース（resourceId pattern）を独立した `@Test` として追加。既存 `blank()` ヘルパーを再利用し pure unit test 方針を維持（NFR 6）。 |

## 設計決定の補足

### キーワードの追加先と並べ方

requirements.md R3 / R4 が論じた「日本語キーワードを既存 list に append するか、別 list として OR 結合するか」の選択肢のうち、**既存 list への append** を採用した。

- 挙動上は等価（どちらも `descriptorText.contains(it)` の `any` 評価）。
- 別 list を作ると Stage 4 の `any` ループを 4 つに分ける必要があり、追加抽象化コストに対する得が無い（NFR 1 のパフォーマンス予算的にも 22 件規模の `contains` は無視できる）。
- list 内のグループ境界は **コメントで「Existing」「Req 1」「Req 2」と区分**して可読性を担保。

### Issue 本文どおりのリテラル一致

NFR 3 に従い、Issue 本文 / requirements §5 のリテラルとビット一致で実装した。具体的には:

- `ユーザー` の長音記号は **U+30FC（KATAKANA-HIRAGANA PROLONGED SOUND MARK）**。U+002D（ASCII hyphen-minus）や U+2014 等の類似字形は採用しない。
- `ユーザーID` の `ID` は **半角 ASCII（U+0049 U+0044）**。全角 `ＩＤ`（U+FF29 U+FF24）は本 Issue のスコープ外（R2 で別 Issue 化を申し送り）。
- `Eメール` の `E` も半角 ASCII。
- 比較対象の `descriptorText` は `lowercase()` を適用済みのため、`Eメール` は実装側 list で `eメール` と小文字化したリテラルを格納している（NFR 3 「Issue 本文どおり」は **ユーザー目視のリテラル** を意味し、`lowercase()` 後の比較対象を作るのは実装詳細のため逸脱しない）。

### 既存 `id` キーワードとの重複（R3）

Req 2 の `member_id` / `customer_id` / `staff_id` / `employee_id` / `account_id` は既存の `id` キーワードでも一致するが、明示パターンを併記した理由は requirements §10 R3 のとおり「`id` のみだとあいまいだが具体パターンを並べることで意図が明確になる」点と、Req 4.2 が **明示パターンごとの独立テスト** を求めていることによる。OR 結合のため挙動は同一であり、誤検出リスクは増えない。

### tie-breaking（password 優先）の維持

Req 3.2 が要求する「同一 descriptorText が password / username 両方のキーワードにマッチするときは password を優先」は、既存実装の以下のコード順序によって自動的に維持される：

```kotlin
val mentionsPassword = PASSWORD_KEYWORDS.any { descriptorText.contains(it) }
if (mentionsPassword) return Role.Password
val mentionsUsername = USERNAME_KEYWORDS.any { descriptorText.contains(it) }
if (mentionsUsername) return Role.Username
```

本 Issue ではこの順序を変更していないため、`idEntry="user_password"` が `Role.Password` を返す既存挙動は壊れていない（`classify_prefersPasswordOverUsername_whenBothKeywordsAppear` テストで pin 済み）。

### form context フィルタリングを行わない（Q2 Option A）

requirements §4 Q2 で「form context フィルタリングは追加しない」が確定済みのため、本実装は当該 field 単独の `hint` / `idEntry` / `contentDescription` のみで判定する。AssistStructure のフォーム境界を参照する経路は一切追加していない（Stage 4 内のキーワード集合のみへの追加）。

## テスト戦略

### 追加テストの粒度（Req 4.1 / Req 4.2）

requirements §9.2 のガイドラインに従い、**1 pattern につき 1 `@Test`** で計 22 ケース追加した:

- 日本語 hint: 9 ケース（`hint = "ユーザー名"` 〜 `hint = "暗証番号"`）
- resourceId pattern: 13 ケース（`idEntry = "member_no"` 〜 `idEntry = "account_id"`）

各テストは `blank().copy(hint = ...)` または `blank().copy(idEntry = ...)` の形で `FieldDescriptor` を組み立て、`assertThat(classify(...)).isEqualTo(Role.Username | Role.Password)` で判定。既存 18 ケースのスタイルと完全に揃えてある（Req 4.4）。

### 既存挙動の回帰

requirements §9.3 が指摘する以下の既存テストは全て pass：

- `classify_returnsUnknown_whenNoSignalsPresent`（descriptor 空 → `Unknown`）
- `classify_returnsUnknown_whenIdEntryHasUnrelatedKeyword`（`submit_button` → `Unknown`）
- `classify_prefersPasswordOverUsername_whenBothKeywordsAppear`（tie-breaking）
- Stage 1〜3 の全テスト（autofillHints / inputType password / inputType email）
- `AutofillFieldHeuristicsExtractMatchKeysTest` の 13 ケース（Phase 1 で追加された `extractMatchKeys` / `normalizeKey` の挙動）

## ビルド・テスト検証結果

### 環境

- JDK: Temurin-17.0.19+10（`/home/hitoshi/sdks/jdk-17`）
- Android SDK: `/home/hitoshi/sdks/android-sdk`
- Gradle: 8.10.2（project wrapper）
- AGP: 8.5.2 / Kotlin: 1.9.24 / KSP: 1.9.24-1.0.20
- `local.properties` は worktree 直下に手動作成（gitignore 対象なので commit には含めず）

### 実行コマンドと結果

| コマンド | 結果 | 備考 |
|---|---|---|
| `./gradlew :app:testDebugUnitTest --tests "io.github.hitoshiichikawa.keynest.autofill.AutofillFieldHeuristicsTest"` | BUILD SUCCESSFUL | 40 / 40 pass（既存 18 + 新規 22）。 |
| `./gradlew :app:testDebugUnitTest`（フル） | BUILD SUCCESSFUL | **624 / 624 pass / 0 failures / 0 errors / 0 skipped**。 |

### 個別テストファイルの pass 状況（本 PR で触れた / 影響範囲）

| テストファイル | tests | failures |
|---|---|---|
| `AutofillFieldHeuristicsTest`（本 PR で 22 ケース追加） | 40 | 0 |
| `AutofillFieldHeuristicsExtractMatchKeysTest`（Phase 1 既存） | 13 | 0 |
| `AssistStructureParserTest` | 影響なし、全 pass | 0 |
| その他全テスト（Phase 1 / Phase 2 のもの含む） | 全 pass | 0 |

`./gradlew :app:lintDebug` は実行していない。Phase 2 の impl-notes §D で確認済みのとおり pre-existing failure 多数で Phase 1/2 の運用に合わせて検証対象外とした。本 PR は `AutofillFieldHeuristics.kt` のキーワード集合への append のみで lint カテゴリの新規違反を増やす変更を行っていない。

### Instrumented test

JVM 環境のため未実行。本 Issue は pure unit test 粒度で完結する設計（requirements §9.4）であり、`./gradlew :app:connectedDebugAndroidTest` は不要。

## 確認事項

### A. 全角バリアントの扱い（R2）

**Status**: NFR 3 の指示通り、本 Issue では Issue 本文どおりの **半角 ASCII** のみを採用。全角 `ＩＤ` / `Ｅメール` 等のバリアントは別 Issue で扱う旨が requirements.md §10 R2 で明示されている。

**Notes**: 実際の Android アプリで `hint="ユーザーＩＤ"` のような全角混在表記がどれくらい出現するかは未調査。Reviewer / PM が必要と判断した場合は別 Issue 化を推奨。

### B. 「`Eメール`」の判定経路

**Status**: `descriptorText.lowercase()` が `Eメール` を `eメール` に変換するため、実装側 `USERNAME_KEYWORDS` には `"eメール"`（先頭 e は半角小文字）を格納している。

**Notes**:
- 大文字 `E` のリテラルは Issue 本文どおりだが、Stage 4 の比較は `lowercase()` 後に行われる現実装のセマンティクスに合わせている。実装コメントでこの点を明示済み。
- 別案として「`USERNAME_KEYWORDS` を `lowercase()` した結果と一致させる」のではなく「`USERNAME_KEYWORDS` を `lowercase()` してから格納する」ヘルパを介する案もありえるが、現状 list は const 相当で 1 度しか評価されないため過剰最適化と判断。

### C. 既存 `id` キーワードを残すか集約するか（R3）

**Status**: 残した。`USERNAME_KEYWORDS` の既存 5 件（`user` / `email` / `id` / `account` / `login`）は **一切変更していない**（Req 3.1）。Req 2 の `member_id` 等は明示追加。

**Notes**: 集約案（既存 `id` を消して具体パターンのみにする）も検討対象だが、(a) 「既存挙動を変更しない」が Req 3.1 / NFR 2 の強い要求であり、(b) 既存 `id` の挙動に依存している Phase 1 / Phase 2 のテストや実利用パスがある可能性を排除しきれないため、保守的に保持した。

### D. 新パターンの誤検出（R5）

**Status**: Q2 Option A 採用済み（form context フィルタリングなし）。`hint="電話番号"` 等が認証外の連絡先入力欄に現れた場合の誤 autofill suggestion は許容する。

**Notes**: Phase 2 で蓄積される detected_fields の運用データを後日観察し、誤検出頻度が高いようなら別 Issue で form context チェックの導入を検討する余地を残してある（requirements §10 R5 と同じ申し送り）。

### E. 多言語化（NFR 4）

**Status**: 本 Issue は日本語限定（Q1）。中国語簡体 / 繁体 / 韓国語等は別 Issue。

**Notes**: 同種パターンを言語ごとに別 Issue で扱う運用は requirements.md §4 Q1 で確定済み。

## 既知の TODO / Follow-up

- **全角 `ＩＤ` / `Ｅメール` バリアント** (R2 / 確認事項 §A): 別 Issue。
- **多言語 hint pattern** (Q1 / 確認事項 §E): 中国語 / 韓国語等は別 Issue。
- **form context フィルタリング** (R5 / 確認事項 §D): Phase 2 の detected_fields 運用後に判断する別 Issue 候補。
- **`USERNAME_KEYWORDS` 内既存 `id` の集約判断** (R3 / 確認事項 §C): 既存挙動依存箇所の調査が前提。本 Issue では保守的に保持。
