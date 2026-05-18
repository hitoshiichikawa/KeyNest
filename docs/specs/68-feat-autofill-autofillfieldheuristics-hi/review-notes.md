# Review Notes — Issue #68 (Round 1)

- HEAD: dd59a5bbed67260cb56701a38dac727259457d83
- Base: develop
- Reviewed at: 2026-05-18

## Summary

Issue #68 Phase 3 の独立レビュー（round=1）。spec dir には `requirements.md` と `impl-notes.md` のみ存在し、`tasks.md` および `design.md` は本 Issue では作成されていない（PM/Developer 運用上、Phase 3 は heuristic キーワード集合への単純追加で済むため tasks/design 不要と整理されているものと判断）。`CLAUDE.md` はリポジトリルートには存在しなかった。

差分は以下の 4 ファイル、586 行追加 / 2 行削除:

- `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/parser/AutofillFieldHeuristics.kt` (実装、Stage 4 のキーワード集合のみ拡張)
- `app/src/test/java/io/github/hitoshiichikawa/keynest/autofill/AutofillFieldHeuristicsTest.kt` (Req 4.1: 9 ケース、Req 4.2: 13 ケースの計 22 `@Test` 追加)
- `docs/specs/68-feat-autofill-autofillfieldheuristics-hi/requirements.md`
- `docs/specs/68-feat-autofill-autofillfieldheuristics-hi/impl-notes.md`

レビュアー側でのテスト実行は行っていない（reviewer ロールでは git diff と spec 突き合わせに限定）。impl-notes には `./gradlew :app:testDebugUnitTest` フル実行で 624/624 pass、`AutofillFieldHeuristicsTest` 単体で 40/40 pass（既存 18 + 新規 22）の報告がある。

実装の本質的レビュー観点:

- `classify` 内部の Stage 4 は `descriptorText = (idEntry + ' ' + hint + ' ' + contentDescription).lowercase()` 上での `contains` 評価のため、`hint="ユーザーID"` は実行時に `ユーザーid` に lowercase される。これに合わせて実装側 `USERNAME_KEYWORDS` には小文字済みリテラル `"ユーザーid"` / `"eメール"` を格納している。Issue 本文の「ユーザー目視のリテラル」(NFR 3) は半角 `ID` / 半角 `E` のままであり、`lowercase()` 前後の対称性は崩れていない (impl-notes §B で明示)。
- Stage 1〜3、`Role` enum、`FieldDescriptor`、`extractMatchKeys` / `normalizeKey` の public シグネチャは未変更 (Req 3.3 / 3.4 / 3.5 / NFR 2)。
- tie-breaking (password 優先) の評価順序 (Lines 59-62) も未変更 (Req 3.2)。既存テスト `classify_prefersPasswordOverUsername_whenBothKeywordsAppear` で pin 済み。

## Findings

### AC 未カバー

なし。requirements §5 / §7 (DoD) の各項目を以下のとおり差分から確認:

- Req 1.1〜1.5: 実装 (`AutofillFieldHeuristics.kt:114-115, 129`) と Test (Lines 161-208) でそれぞれ pattern が追加されている。
- Req 2.1〜2.4: 実装 (`AutofillFieldHeuristics.kt:120-123`) と Test (Lines 215-272) で 13 pattern 全てが追加されている。
- Req 3.1: 既存 5 件の英語 username キーワードと 3 件の password キーワードは差分内で残置されており、OR 結合追加のみ。
- Req 3.2: tie-breaking の評価順序 (password 先、username 後) は未変更。
- Req 3.3: Stage 1〜3 のコードは差分外。
- Req 3.4: `Role` enum (Line 91) は `Username` / `Password` / `Unknown` の 3 値のまま。
- Req 3.5: `extractMatchKeys` / `normalizeKey` (Lines 157-181) は差分外。
- Req 4.1: 9 ケース全て独立 `@Test` で追加 (Lines 161-208)。
- Req 4.2: 13 ケース全て独立 `@Test` で追加 (Lines 215-272)。
- Req 4.3: impl-notes 報告で 624/624 pass。
- Req 4.4: 新規テストは `blank().copy(hint = ...)` / `blank().copy(idEntry = ...)` パターンで既存ヘルパー流儀と一致。

### missing test

なし。Req 4.1 (9 ケース) / Req 4.2 (13 ケース) は計 22 個の独立 `@Test` として完全追加。各テストは要件にあるリテラルとビット一致 (`"ユーザー名"`, `"ユーザーID"`, `"メールアドレス"`, `"Eメール"`, `"電話番号"`, `"会員番号"`, `"社員番号"`, `"パスワード"`, `"暗証番号"`, および 13 件の snake_case)。

### boundary 逸脱

なし。差分は requirements §2.1 In Scope の 2 ファイル + spec ドキュメント 2 ファイルのみで、Out of Scope §2.2 のいずれにも触れていない:

- AssistStructureParser 未変更
- Credential Manager / Room / UI 経路未変更 (Phase 1 / Phase 2 への波及なし)
- form context フィルタリング未導入 (Q2 Option A 遵守)
- `Role` enum 拡張なし
- 日本語以外の言語の hint 追加なし (Q1 遵守)

なお tasks.md が存在しないため、本レビューは requirements.md §2 (Scope) と impl-notes.md の宣言範囲を基準に boundary を判定した。

## Notes

- impl-notes §A / §C / §E の Follow-up (全角バリアント、`id` 集約検討、多言語) は requirements §10 と整合した別 Issue 候補としての申し送り。本 Issue のスコープには含まれないため reject 事由にはしない。
- requirements.md §10 R3 が指摘するとおり Req 2 の `member_id` などは既存 `id` キーワードで既にマッチしうるが、明示パターン併記は Req 4.2 が要求するテスト独立性 (1 pattern 1 test) のためにも有用で、可読性も向上している。OR 結合のため挙動の重複は無害。
- `descriptorText` の `lowercase()` 後の比較に合わせて `"ユーザーid"` / `"eメール"` を格納している点は、コード上のコメント (Lines 102-109) と impl-notes §B で意図が明示されており、誤読リスクは低い。
- `lint` は impl-notes §「ビルド・テスト検証結果」末尾で「pre-existing failure 多数のため Phase 1/2 と同様に検証対象外」と明示。本 PR は Stage 4 list への append のみで lint 新規違反を増やす変更ではないため受容可能。
- spec dir に `tasks.md` / `design.md` が存在しないことは本 Issue のスコープの軽さを反映したものと解釈。次の Developer / PjM が以後の Issue でも同方針で進める場合は明示しておくと運用が安定する。

RESULT: approve
