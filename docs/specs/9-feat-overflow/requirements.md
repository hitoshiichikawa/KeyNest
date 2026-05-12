# Requirements Document

## Introduction

KeyNest のクレデンシャル一覧画面は、MVP 要件
（`docs/specs/1--easykeynest-mvp-packagename-autofill/requirements.md`）の Out of Scope に
列挙されていた検索・フィルタ・「最近使った」カルーセル・並び替え・overflow メニューを
未実装のまま残している。PR #7 でデザインモック（`screens-1.jsx`）に沿った UI を導入したものの、
これらの一覧操作系コンポーネントは現状未提供で、ユーザーは登録件数が増えるとお目当ての
credential をスクロールでしか発見できない。

本要件は、一覧画面に「検索バー」「フィルタチップ」「最近使ったカルーセル」「並び替えトグル」
「行ごとの overflow (︙) メニュー」を追加し、保存済みクレデンシャルへの到達性を改善する
ことを目的とする。全機能は完全ローカル動作で、ネットワーク同期・クラウド共有は扱わない。

## 確定事項（人間判断の反映）

Issue #9 の議論で確定済みの判断を以下に反映する。

- **overflow メニューの MVP スコープ**: 行ごとのアクションは **「複製」のみ**とする。
  Issue 本文に列挙されていた「エクスポート」「共有」は本 MVP の overflow メニューには含めない。
  既存 UI / 文言から export / 共有の痕跡を撤去する作業は別 PR で扱う（「確認事項」参照）。
- **「最近使った」判定基準**: `lastUsedAt`（autofill 経由でアンロックされた時点で更新される
  タイムスタンプ）が新しい順で上位 **5 件**を「最近使った」とする。`lastUsedAt` を保持しない
  credential（未使用）は本カルーセルには出現しない。
- **フィルタチップのカテゴリ**: 2 カテゴリのみとする — 「署名一致あり」「未取得のみ」。

## Requirements

### Requirement 1: 検索バー（インクリメンタルサーチ）

**Objective:** As a 一覧画面利用者, I want ラベル / username / packageName で credential を
即時絞り込みたい, so that 登録件数が増えてもスクロールせずに目的の credential を発見できる

#### Acceptance Criteria

1.1. When ユーザーが一覧画面上部の検索入力欄に 1 文字以上を入力したとき, the Credential List Screen shall ラベル (`label`) / ユーザー名 (`username`) / パッケージ名 (`packageName`) のいずれかに当該文字列を **部分一致（大文字小文字を区別しない）** で含む credential のみを一覧に表示する。

1.2. When ユーザーが検索入力欄の文字列を変更したとき, the Credential List Screen shall 追加操作（送信ボタン押下など）なしで結果を即座に更新する。

1.3. When ユーザーが検索入力欄を空にしたとき, the Credential List Screen shall フィルタチップ・並び替えの現在状態を保ったまま、検索による絞り込みのみを解除する。

1.4. If 検索文字列に一致する credential が 0 件であるとき, the Credential List Screen shall 「該当する credential はありません」相当の **空状態メッセージ**を表示する（既存の「クレデンシャルが未登録」状態とは区別する）。

1.5. The Credential List Screen shall 検索文字列の照合を **完全ローカル**で行い、ネットワーク送信を発生させない。

### Requirement 2: フィルタチップ（署名一致あり / 未取得のみ）

**Objective:** As a 一覧画面利用者, I want 署名情報の状態で credential を素早く絞り込みたい,
so that 「署名未取得のまま放置されている credential」「署名一致済みで実用可能な credential」を
分けて点検・整理できる

#### Acceptance Criteria

2.1. The Credential List Screen shall 一覧上部に **「署名一致あり」** と **「未取得のみ」** の 2 つのフィルタチップを提供する。

2.2. While いずれのフィルタチップも選択されていない状態であるとき, the Credential List Screen shall すべての credential を対象とする（フィルタによる除外を行わない）。

2.3. When ユーザーが「署名一致あり」チップを選択したとき, the Credential List Screen shall 署名 SHA-256 ハッシュを保持している credential のみを一覧に表示する。

2.4. When ユーザーが「未取得のみ」チップを選択したとき, the Credential List Screen shall 署名 SHA-256 ハッシュを保持していない credential のみを一覧に表示する。

2.5. When ユーザーが選択中のチップを再度操作したとき, the Credential List Screen shall そのチップの選択を解除し、フィルタなしの状態に戻す。

2.6. When ユーザーが片方のチップを選択中に他方のチップを操作したとき, the Credential List Screen shall **2 チップを排他選択**として扱い、新しく操作されたチップのみを選択状態にする（同時選択を許可しない）。

2.7. If 選択中のチップ条件に合致する credential が 0 件であるとき, the Credential List Screen shall 「該当する credential はありません」相当の空状態メッセージを表示する。

### Requirement 3: 「最近使った」カルーセル

**Objective:** As a 一覧画面利用者, I want 直近で autofill 経由で使った credential を画面上部のカルーセルから即座に再アクセスしたい, so that よく使う credential を毎回検索・スクロールで探さずに済む

#### Acceptance Criteria

3.1. While 一覧画面が表示状態であるとき, the Credential List Screen shall 一覧本体の上部に「最近使った」カルーセル領域を配置し、`lastUsedAt` の **降順（新しい順）で上位 5 件**を横スクロール可能なカードとして表示する。

3.2. When 対象アプリのログイン画面で credential が autofill 経由で確定（Vault アンロックを経て credential 本体が autofill 応答に含まれた時点）されたとき, the Credential List Screen shall 当該 credential の `lastUsedAt` を現在時刻で更新し、次回の一覧表示時にカルーセルへ反映する。

3.3. If `lastUsedAt` を保持する credential が 5 件未満であるとき, the Credential List Screen shall 保持している件数のみを「最近使った」カルーセルに表示する（プレースホルダで 5 件枠を埋めない）。

3.4. If `lastUsedAt` を保持する credential が 0 件であるとき, the Credential List Screen shall 「最近使った」カルーセル領域を **非表示**にする（空のカルーセル領域でレイアウト高さを占有しない）。

3.5. When ユーザーがカルーセル内のカードをタップしたとき, the Credential List Screen shall その credential の編集画面へ遷移する（一覧行タップと同等の挙動）。

3.6. While 検索バー / フィルタチップで一覧本体が絞り込まれているとき, the Credential List Screen shall 「最近使った」カルーセルの 5 件選定ロジックを **検索・フィルタの影響を受けない**形で維持する（カルーセルは一覧全体を対象に `lastUsedAt` 上位 5 件を表示し続ける）。

3.7. The Credential List Screen shall 「最近使った」カルーセル表示のための問い合わせを完全ローカルで行い、ネットワーク送信を発生させない。

### Requirement 4: 並び替え

**Objective:** As a 一覧画面利用者, I want 一覧本体の並び順を切り替えたい, so that 用途（最近更新したものを見たい / アルファベット順で探したい）に応じて視認性を最適化できる

#### Acceptance Criteria

4.1. The Credential List Screen shall **3 つの並び順**を選択肢として提供する: (a) 更新日時の降順（`updatedAt` 新しい順、既定）, (b) ラベル昇順（`label` の自然順）, (c) パッケージ名昇順（`packageName` の自然順）。

4.2. While ユーザーが並び順を一度も変更していない状態であるとき, the Credential List Screen shall 既定として **更新日時の降順**を適用する。

4.3. When ユーザーが並び順を切り替えたとき, the Credential List Screen shall 一覧本体の表示順を即座に更新する。

4.4. When ユーザーが並び替えと検索バー / フィルタチップを **同時に**指定したとき, the Credential List Screen shall まず検索・フィルタで対象を絞り込み、その結果に対して選択中の並び順を適用する。

4.5. The Credential List Screen shall 並び替えの選択状態を、同一 Activity ライフサイクル内（画面遷移で破棄されるまで）に限り保持する。

### Requirement 5: Overflow メニュー（複製のみ）

**Objective:** As a 一覧画面利用者, I want 各行の overflow (︙) メニューから既存 credential を複製したい, so that 似た credential（同一アプリの別アカウント等）を素早く作成できる

#### Acceptance Criteria

5.1. The Credential List Screen shall 一覧の各行に **overflow (︙) アイコン**を表示し、行を操作するための副次メニューの起動点として機能させる。

5.2. When ユーザーが行の overflow アイコンを操作したとき, the Credential List Screen shall 「複製」**1 項目のみ**を含む副次メニューを表示する。

5.3. When ユーザーが「複製」を選択したとき, the Credential List Screen shall 当該 credential の **ラベル / username / packageName / password / 署名関連情報**を初期値として持つ新規 credential を作成し、`createdAt` と `updatedAt` を現在時刻で初期化する。

5.4. When 複製操作によって新規 credential が作成されたとき, the Credential List Screen shall 複製元の credential を変更せずに残し、複製先を一覧（および「最近使った」カルーセル判定対象）に追加する。

5.5. The Credential List Screen shall overflow メニューに「エクスポート」「共有」を **表示しない**（MVP スコープ外）。

5.6. The Credential List Screen shall 既存の「行長押しで削除」導線を引き続き提供する（本機能の追加によって削除導線を撤去しない）。

### Requirement 6: 既存挙動の保持

**Objective:** As a 既存の一覧画面ユーザー, I want 検索・フィルタ・カルーセル・並び替え・overflow メニューの追加によって既存の登録・編集・削除・autofill 動線が影響を受けないことを保証されたい, so that 既存挙動を壊さずに本機能を導入できる

#### Acceptance Criteria

6.1. The Credential List Screen shall 本機能の追加によって、MVP requirements.md の Requirement 1.1 / 1.5 / 6.1 / 6.3 で規定された credential 登録・編集・削除・Autofill Service 有効化導線の挙動を変更しない。

6.2. The Credential List Screen shall 検索文字列・フィルタチップ・並び替えの状態がいずれであっても、一覧行のタップによる編集画面遷移挙動を維持する。

6.3. The Credential List Screen shall 検索 / フィルタ / カルーセル / 並び替え / overflow メニュー操作によって、Autofill Service 側の `onFillRequest` 応答挙動（MVP Requirement 3 / 4 / 5）に副作用を与えない。

## Non-Functional Requirements

### NFR 1: セキュリティ・プライバシー

1.1. The Credential List Screen shall 検索文字列 / フィルタ選択 / 並び順 / 「最近使った」一覧の内容を、ネットワーク送信・analytics・クラッシュレポートに送出しない。

1.2. The Credential List Screen shall 検索クエリ・フィルタ状態・並び順を診断ログに出力する場合でも、credential の username / packageName / label の **平文を含めない**形で記録する（MVP NFR 5.1 を継承）。

1.3. The Credential List Screen shall 「最近使った」カルーセルや一覧上で credential の **password 平文**を表示しない（既存の表示ポリシーを維持する）。

1.4. The Credential List Screen shall 複製操作で生成する新規 credential を完全ローカルに保存し、複製の事実をネットワーク経由に通知しない。

### NFR 2: 応答性能

2.1. When ユーザーが検索入力欄に 1 文字を入力したとき, the Credential List Screen shall 入力イベントから一覧の絞り込み結果が画面に反映されるまでの所要時間中央値を **200ms 以内**に収める（登録件数 500 件規模の端末で計測）。

2.2. The Credential List Screen shall 「最近使った」カルーセルの初期表示および並び替え切替に伴う再描画を、Autofill Service の `onFillRequest` 応答時間（MVP NFR 2.1 = 中央値 300ms 以内）に影響を与えない形で実行する。

### NFR 3: アクセシビリティ・操作性

3.1. The Credential List Screen shall 検索入力欄・フィルタチップ・並び替えコントロール・overflow アイコンに対し、TalkBack 等のスクリーンリーダーで認識可能な accessibility ラベルを付与する。

3.2. The Credential List Screen shall overflow (︙) アイコンおよびカルーセル内カードのタップ可能領域の最小寸法を、Android 標準のアクセシビリティ推奨値（48dp 相当）以上で提供する。

3.3. The Credential List Screen shall フィルタチップの選択 / 解除状態をスクリーンリーダーが識別可能な形（selected / not selected アクセシビリティ状態）で公開する。

### NFR 4: 国際化

4.1. The Credential List Screen shall 検索入力欄プレースホルダ・フィルタチップ表示文言（「署名一致あり」「未取得のみ」）・カルーセル見出し（「最近使った」）・並び替え選択肢ラベル・overflow メニュー項目（「複製」）・空状態メッセージを、既存のローカライズリソース機構を用いて提供する。

## Out of Scope

以下は本 Issue では実装しない。

- **overflow メニューの「エクスポート」「共有」項目**: 本 MVP では「複製」のみを採用する（人間判断による確定事項）。
- **既存 UI / 文言からの「エクスポート」「共有」痕跡の撤去 PR**: 本 Issue では overflow メニュー新設のみを扱い、既存リソース / 文字列リソース / その他 UI における export / share 文言の撤去は **別 PR**として切り出す（「確認事項」参照）。
- **`lastUsedAt` を Autofill 経路以外（手動編集・編集画面オープン・コピー操作等）で更新すること**: 本 Issue では autofill 経路（credential 本体が autofill 応答に含まれた時点）のみで更新する。
- **「最近使った」を 5 件以外に設定可能とする設定 UI**: 件数は 5 件で固定する。
- **検索・フィルタ・並び替え状態の永続化**: Activity ライフサイクル内でのみ状態を保持し、アプリ再起動・画面破棄をまたいだ復元は扱わない。
- **複製機能の対象 credential を複数選択して一括複製する操作**: 1 行ずつの複製のみを対象とする。
- **クラウド同期・複数端末同期・credential エクスポート / インポート機能**: MVP requirements.md の Out of Scope を継承する。
- **「最近使った」カルーセルの並び順を `lastUsedAt` 以外（利用頻度等）に切り替える機能**: 降順固定とする。

## 確認事項

以下は本 Issue 範囲外の人間判断が必要な事項。Issue コメントでの確認またはサブ Issue 起票を提案する。

- **既存 UI / 文言からの「エクスポート」「共有」撤去の別 PR 起票要否**: 現リポジトリ内の文字列リソース / レイアウト / メニューリソース / コメント等に、過去 PR 由来の「エクスポート」「共有」関連の文言・アイコンが残っている可能性がある。本 Issue では overflow メニュー新設のみを扱う方針のため、撤去作業は **別 Issue / 別 PR**として切り出すかを人間判断に委ねる。
- **`lastUsedAt` フィールド追加に伴う既存データの初期値の扱い**: 既存 credential には `lastUsedAt` 値が存在しないため、(a) 「未使用」状態（NULL 相当）として残す、(b) 既存行に `0` を初期値として埋める、(c) 既存行を `updatedAt` で初期化する、のいずれを採用するかを人間判断に委ねる。選択結果が `lastUsedAt` を保持する credential 件数 = 「最近使った」カルーセル初期表示件数（Req 3.3 / 3.4）に直接影響するため、要件側では確定させず設計フェーズで決定する。
- **overflow メニュー「複製」が複製するフィールド範囲**: 複製対象に Issue #14 で導入された **詳細設定（advanced details）系フィールド**（`signatureSha256` / `signatureCapturedAt` / `createdAt` / `updatedAt` 等）まで含めるか、それとも編集可能フィールド（label / username / packageName / password）のみを引き継ぎ、署名関連は新規取得し直すかを人間判断に委ねる。Req 5.3 では「署名関連情報」を初期値に含めるとしているが、これは保存時点の署名一致状態をそのまま引き継ぐ前提で記述しており、実装時には署名再取得を伴わせるかを別途確認する必要がある。

## Open Questions

なし。

確定事項として人間判断が反映されており（overflow MVP = 複製のみ / 「最近使った」= `lastUsedAt`
降順上位 5 件 / フィルタチップ = 2 カテゴリ）、本要件範囲では仕様レベルの未確定事項は残って
いない。スキーマ初期値・複製範囲・既存文言撤去 PR は「確認事項」として人間にエスカレーション
する。
