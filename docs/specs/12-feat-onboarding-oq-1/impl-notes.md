# Implementation Notes — Issue #12 (OQ-1 closure / 案 A 確定)

## 対応方針

本 Issue はアプリケーションコード変更を伴わない **ドキュメンテーション中心の対応** である。
Open Question OQ-1（Onboarding を複数ステップ化するか）について、リポジトリオーナーが
Issue #12 上で **案 A（単一画面継続）** を選択したため、その決定を本 spec ディレクトリ
(`docs/specs/12-feat-onboarding-oq-1/`) の存在によって記録する。

requirements.md の Non-Goals に従い、以下は一切行わない:

- 案 B（複数ステップ Onboarding）の実装
- プログレスドット UI コンポーネントの追加
- `AutofillEnableActivity` の振る舞い変更
- 多言語化リソースの増減
- 既存 MVP spec (`docs/specs/1--easykeynest-mvp-packagename-autofill/requirements.md`) の本文書き換え

## OQ-1 決定の根拠

- 決定: **案 A（単一画面継続）**
- 根拠: Issue #12 のオーナー（`hitoshiichikawa`）コメント「案AでOK」
  - 参照: <https://github.com/hitoshiichikawa/KeyNest/issues/12>（コメント本文のみ。commit には含められないため URL のみ記録）
- 影響範囲:
  - 現行 `AutofillEnableActivity` 単一画面構成を継続する
  - プログレスドット UI 実装は行わない
  - 多言語化リソースは現状（ja_JP 既定）を温存
  - 将来案 B が必要になった場合は本 OQ-1 を覆す **新 Issue を起票** してから着手する（requirements.md AC 2.2 参照）

## AC トレーサビリティ表

| AC ID | AC 要旨 | どう満たすか |
|---|---|---|
| 1.1 | Onboarding 動線は `AutofillEnableActivity` 単一画面で完結する | 本 Issue で複数 Activity / Fragment を追加していない（`app/src/main/java/com/example/keynest/ui/enable/AutofillEnableActivity.kt` が単独で当該責務を担う既存構成のまま、touch されていない）ことにより自明に満たす |
| 1.2 | Onboarding 関連画面はプログレスドット UI を含まない | プログレスドット UI を実装する layout xml / drawable / カスタム View を本 Issue で追加していない（`app/src/main/res/layout/` 配下の新規追加なし）ことにより満たす |
| 1.3 | 初回起動から Autofill 設定完了までの間に中間ステップ画面を挿入しない | 中間ステップ用の新規 Activity / Fragment / Intent 遷移を本 Issue で追加していない（`AutofillEnableActivity` の startActivity 系コードに変更がない）ことにより満たす |
| 2.1 | 本 spec ディレクトリで OQ-1 の決定結果を明示的に記録する | `docs/specs/12-feat-onboarding-oq-1/requirements.md` の Introduction で「案 A として確定」と明記、本 impl-notes.md でも明示記録 |
| 2.2 | 将来案 B が必要になった場合は新 Issue を起票してから着手する | 本 impl-notes.md の「OQ-1 決定の根拠」節および requirements.md AC 2.2 にプロセスとして明記 |
| 2.3 | 本 spec の Introduction に決定経路（Issue #12 上でオーナーが案 A を選択した事実）を記録する | requirements.md の Introduction 第 2 段落で「Issue オーナーが Issue コメントで案 A を選択」と記録、本 impl-notes.md の「OQ-1 決定の根拠」で Issue URL を明示 |
| 3.1 | `AutofillEnableActivity` の振る舞いは本 Issue で変更されない | `git diff origin/main...HEAD -- app/src/main/java/com/example/keynest/ui/enable/AutofillEnableActivity.kt` が空（diff なし）であることを確認済み |
| 3.2 | MVP spec Requirement 6（Autofill 有効化導線）に対するカバレッジが低下しない | MVP spec 本文・テスト・ソースコードのいずれも本 Issue で書き換えておらず、既存カバレッジが温存されている |
| 3.3 | 本 Issue 対応の前後で同一の初回起動シナリオは同等挙動を示す | コード変更ゼロのため挙動は完全に同一（diff 等価。git diff 結果が空であることが根拠） |
| 4.1 | 多言語化リソース構成（ja_JP 既定 + `values-en/`）を維持する | `app/src/main/res/values-*/` の構成を本 Issue で変更していない（追加・削除なし）。なお現状 `values-en/` 配下のリソースは実体としては未配置（`values/strings.xml` のみ）であり、requirements.md の想定と乖離する可能性は「確認事項 3」に記録 |
| 4.2 | Onboarding 関連 strings リソースは本 Issue で追加・削除・改名されない | `git diff origin/main...HEAD -- app/src/main/res/values/strings.xml` を確認した結果、変更内容はすべて Issue #9 / #10 / #14 由来の credential list / settings / advanced details / danger zone 系 entries であり、Onboarding 相当（`autofill_enable_*` 系）の entries は touch されていない |

## NFR トレーサビリティ

| NFR ID | NFR 要旨 | どう満たすか |
|---|---|---|
| 1.1 | 本 spec ドキュメントに Issue #12 と OQ-1 を相互参照可能な記述で結びつける | requirements.md タイトル直下の Introduction に Issue #12 と OQ-1 両方を明示、本 impl-notes.md でも Issue URL を併記 |
| 1.2 | MVP spec の Requirement 6 への参照を保持する | requirements.md Goals 第 3 項および Non-Goals 第 5 項で MVP spec パスを明示参照、本 impl-notes.md の AC 3.2 行でも参照 |

## 既存挙動の温存検証

`origin/main` を基準として、Onboarding 動線および多言語化リソースが本 Issue で touch されていないことを以下のコマンド結果（いずれも diff 出力なし）で確認した:

- `git diff origin/main...HEAD -- app/src/main/java/com/example/keynest/ui/enable/AutofillEnableActivity.kt` → diff 空
- `git diff origin/main...HEAD -- app/src/main/res/layout/autofill_enable_activity.xml` → diff 空

なお `git diff origin/main...HEAD -- app/src/main/res/values/strings.xml` には差分があるが、内容は Issue #9 / #10 / #14 で導入された credential list / settings / advanced details / danger zone 関連 entries のみであり、Onboarding (`autofill_enable_*`) entries は touch されていないことを目視確認した。本 Issue (#12) 単体のスコープに対しては追加変更ゼロである。

`git status` 結果: untracked の `docs/specs/12-feat-onboarding-oq-1/` のみで、app/ 配下に変更なし。

## テスト方針

本 Issue はコード変更ゼロのため新規ユニットテストは追加しない。AC 3.1 / 3.3 はコード未変更であること（diff 等価）が根拠そのものであり、追加テストは spec ガード以上の意味を持たない。

既存テストスイートの実行確認:

- `./gradlew testDebugUnitTest --no-daemon -q` を試行したが、本 worktree の実行環境に Java が
  存在せず (`JAVA_HOME is not set and no 'java' command could be found in your PATH.`)、JVM ユニット
  テストを実行できなかった。
- 実機 / 開発端末上での確認は人間（PjM / Reviewer）に委ねる。コード変更ゼロのため、`origin/main`
  でテストが通っている限り本ブランチでもテスト結果は同等であることが論理的に保証される
  （変更されたファイルが docs 配下のみであるため）。

## 将来作業（案 B 移行時の参考）

仮に将来案 B（複数ステップ Onboarding）が必要になった場合に発生する作業項目（**本 Issue では実装しない**。本 OQ-1 を覆す新 Issue 起票が前提）:

- ステップ数の決定（screens-2.jsx を再評価し、画面遷移シーケンスを定義）
- 各ステップ画面のコンテンツ定義（タイトル / 説明 / イラスト or アイコン）
- `ProgressDotsView` 等のカスタム View 実装（または `TabLayoutMediator` + `ViewPager2` の流用）
- 画面遷移基盤の選定（`ViewPager2` / `Navigation Component` / 単純な Activity 連鎖）
- 多言語化 entries の追加（ja / en / 必要に応じ他ロケール、`values-en/strings.xml` の整備を含む）
- 既存 `AutofillEnableActivity` を最終ステップとして吸収するか、別 Activity に分けるかの判断
- 既存 Autofill 設定遷移ロジックの巻き取り設計（PendingIntent / startActivityForResult 等）
- MVP spec Requirement 6 のカバレッジ温存方針の再確認（Onboarding 多段化がフロー先頭挿入なのか、Autofill 有効化導線そのものを再設計するのか）
- 計装テストの再設計（`ActivityScenarioRule` / Espresso ベースのフロー検証）

## 確認事項（PR レビュワー向け）

requirements.md の Open Questions を引き継ぎつつ、Developer 判断としての結論を記す。

### 確認事項 1: Issue 本文の `requirements.md §2.2` 参照について

- Issue #12 本文には「`requirements.md §2.2` で Out of Scope 宣言済み」という記述があるが、実際の
  MVP spec (`docs/specs/1--easykeynest-mvp-packagename-autofill/requirements.md`) §2.2 は credential
  保存時の署名ハッシュ未保存ケースを扱っており、Onboarding には言及していない。
- Developer 判断: 本 spec ではこの参照を引用しない（誤参照と思われるため）。正しい参照先が
  存在する場合は人間にエスカレーション。
- 本 Issue では requirements.md / impl-notes.md ともに当該誤参照を引用していないため、誤った
  情報が spec に混入するリスクは無い。

### 確認事項 2: 既存 MVP spec の OQ-1 セクションを更新するか

- requirements.md Non-Goals 第 5 項に「既存 MVP spec 本文の書き換え」を明示的に除外しているため、
  本 Issue では MVP spec の本文を一切書き換えない。
- Developer 判断: 本 spec ディレクトリ (`docs/specs/12-feat-onboarding-oq-1/`) の存在自体が OQ-1
  Closure の記録となるため、MVP spec 側に「Closed (案 A)」を追記する必要性は薄い。MVP spec の
  Open Questions 節を将来 grep するレビュワーが本 spec へ辿り着けるかは別途検討が必要だが、本
  Issue のスコープ外として扱う。
- 派生課題候補: MVP spec 側の Open Questions 節に「OQ-1: Closed → Issue #12 参照」リンクを追記
  する別 Issue を起票する選択肢があるが、本 Issue では起票しない（人間判断に委ねる）。

### 確認事項 3: `values-en/` の実体不在

- requirements.md AC 4.1 は「ja_JP 既定 + `values-en/` 構成を維持する」としているが、現行
  リポジトリには `app/src/main/res/values-en/` ディレクトリ自体が存在せず、英語文言は
  `app/src/main/res/values/strings.xml`（既定値、内容は英語）に直接配置されている。
- Developer 判断: 本 Issue は多言語化ポリシー変更を Non-Goals としているため、現状構成
  （`values/` のみ・英語ベース）を温存する。requirements.md の表現と現実構成に乖離があるが、本
  Issue では requirements 側を書き換えない方針とし、本確認事項として記録する。
- 派生課題候補: 本格的なロケール対応（日本語の `values-ja/` 追加 or 既定の日本語化）は別 Issue
  での対応とする。

## 派生 / 派生候補

- MVP spec 側の Open Questions 節に OQ-1 Closure リンクを追記する別 Issue（確認事項 2 参照）
- 多言語化リソース構成の整理（`values-en/` 追加 or 既定言語の見直し、確認事項 3 参照）
- 案 B（複数ステップ Onboarding）が必要になった場合の新 Issue 起票（AC 2.2 参照）
