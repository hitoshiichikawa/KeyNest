# Requirements Document

## Introduction

KeyNest の Edit 画面（クレデンシャル編集）には、PR #7 のデザインモックで提示されていた
「詳細設定」セクション（登録日 / SHA-256 表示など）が存在するが、MVP 要件
（`docs/specs/1--easykeynest-mvp-packagename-autofill/requirements.md`）では Out of Scope と
されていたため、現在は未実装である。

本機能はこの「詳細設定」セクションを折りたたみ UI として実装し、保存済みクレデンシャルに紐付く
診断・参照用の情報（登録日時 / 更新日時 / 署名 SHA-256 ハッシュ / 署名取得日時 /
credential ID）をユーザーが必要なときだけ展開して確認・コピーできるようにする。
署名 SHA-256 は閉鎖状態のフォーム上では切り詰め表示が望ましいため、本機能では完全な 64 文字
hex を展開後に提示し、ユーザーが他デバイス・他ツールへ持ち出すためのコピー導線も提供する。

なお、Edit 画面の追加情報を「ユーザーが見える」ことに本機能のスコープは限定され、署名の
**再取得**（既存 credential の signatureSha256 を保存時点の値で更新する操作）は本 Issue では
扱わず、別 Issue として切り出す。

## 確定事項（人間判断の反映）

Issue #14 本文「Open Decisions」のうち、本要件では以下を確定事項として採用する。

- **Option A（credential ID 表示）の採用**: credential の内部 ID（`Credential.id`）は
  詳細設定セクション内に表示するが、**デフォルトでは非表示**とし、ユーザーが明示的にトグルを
  操作した場合にのみ表示する。デバッグ用途であることをユーザーに伝えるため、表示位置は
  詳細設定セクションの末尾とする。
- **Option B（「署名を再取得」ボタン）の Out of Scope 化**: 既存 credential の署名 SHA-256 を
  再取得するボタンは本 Issue のスコープには含めず、別 Issue で扱う。本 Issue は
  「詳細情報の表示のみ」に絞る。

## Requirements

### Requirement 1: 詳細設定セクションの折りたたみ UI

**Objective:** As a クレデンシャル編集中のユーザー, I want Edit 画面下部の「詳細設定」セクションを必要なときだけ展開できる折りたたみ UI として利用したい, so that 普段の編集では画面が散らからず、必要な時にだけ診断情報を確認できる

#### Acceptance Criteria

1.1. When ユーザーが Edit 画面（新規作成・編集の両モード）を開いたとき, the Edit Screen shall 「詳細設定」セクションを **折りたたみ状態（collapsed）** で表示する。

1.2. When ユーザーが「詳細設定」セクション見出しを操作（タップ）したとき, the Edit Screen shall セクションの展開状態をトグル（展開 ⇄ 折りたたみ）する。

1.3. While 「詳細設定」セクションが折りたたみ状態にあるとき, the Edit Screen shall セクションヘッダーのみを表示し、内部の登録日時 / 更新日時 / 署名 SHA-256 hex / 署名取得日時 / credential ID 表示要素を描画しない、または視覚的に隠す。

1.4. While 「詳細設定」セクションが展開状態にあるとき, the Edit Screen shall 折りたたみインジケータ（chevron 等）の向きを「展開済み」を示す状態に切り替える。

1.5. When ユーザーが Edit 画面で他の編集操作（フォーム入力・保存・破棄）を行ったとき, the Edit Screen shall 「詳細設定」セクションの展開／折りたたみ状態を、当該 Edit 画面が画面遷移で破棄されるまで保持する。

### Requirement 2: 登録日時・更新日時の表示

**Objective:** As a 編集モードのユーザー, I want 当該クレデンシャルが登録された日時と最後に更新された日時を確認したい, so that 古い／重複した credential を識別して整理できる

#### Acceptance Criteria

2.1. While Edit 画面が編集モード（既存 credential を編集中）であるとき, the Edit Screen shall 「詳細設定」セクション内に **登録日時**（`createdAt`）と **更新日時**（`updatedAt`）の 2 項目を表示する。

2.2. The Edit Screen shall 登録日時・更新日時を **端末のローカルタイムゾーン** に変換した表示形式（年・月・日・時・分が読み取れる形式）でユーザーに提示する。

2.3. While Edit 画面が新規作成モード（既存 credential を編集していない）であるとき, the Edit Screen shall 登録日時・更新日時を表示しない、または「未保存」相当の placeholder を提示する。

2.4. If 登録日時と更新日時が同一値であるとき, the Edit Screen shall 両方を区別して表示する（同一値であっても片方を省略しない）。

### Requirement 3: 署名 SHA-256 完全 hex 表示とコピー導線

**Objective:** As a 編集モードのユーザー, I want 当該クレデンシャル保存時の署名証明書 SHA-256 ハッシュを完全な 64 文字 hex で確認・コピーしたい, so that 他デバイスや別ツール（apksigner 等）の出力と直接突合できる

#### Acceptance Criteria

3.1. While Edit 画面が編集モードかつ「詳細設定」セクションが展開状態にあり, 編集対象 credential が signature SHA-256 を保有しているとき, the Edit Screen shall 当該 SHA-256 ハッシュを **64 文字の lowercase hex 文字列**として完全表示する。

3.2. The Edit Screen shall SHA-256 hex 文字列に対する **コピー導線**（コピーボタン等の操作可能要素）を 1 つ以上提供する。

3.3. When ユーザーがコピー導線を操作したとき, the Edit Screen shall 完全 64 文字の lowercase hex 文字列をシステムクリップボードに書き込み、コピー成功をユーザーに通知（toast 等）する。

3.4. If 編集対象 credential が signature SHA-256 を保有していない（Requirement 2.2 of MVP, すなわち保存時に対象アプリ未インストールだったケース）とき, the Edit Screen shall hex 文字列の代わりに「未取得」相当の文言を表示し、コピー導線を無効化または非表示にする。

3.5. The Edit Screen shall SHA-256 hex 文字列を等幅フォント（monospace）で表示する。

### Requirement 4: 署名取得日時の表示

**Objective:** As a 編集モードのユーザー, I want 署名 SHA-256 ハッシュが取得された日時を確認したい, so that 当該 credential の署名情報が古い（OS 入れ替え・対象アプリの署名更新等で陳腐化している）可能性を判断できる

#### Acceptance Criteria

4.1. While Edit 画面が編集モードかつ「詳細設定」セクションが展開状態にあり, 編集対象 credential が `signatureCapturedAt` を保有しているとき, the Edit Screen shall 当該タイムスタンプを **端末のローカルタイムゾーン**に変換した形式で表示する。

4.2. If 編集対象 credential が `signatureCapturedAt` を保有していない（signature 未取得状態）とき, the Edit Screen shall 「未取得」相当の文言を表示する。

### Requirement 5: Credential ID 表示（デバッグ用途・デフォルト非表示）

**Objective:** As a デバッグ作業中のユーザーまたは開発者, I want 当該クレデンシャルの内部 ID を必要なときに確認したい, so that Issue 報告やログ突き合わせ時に対象 credential を一意に指定できる

#### Acceptance Criteria

5.1. While Edit 画面が編集モードかつ「詳細設定」セクションが展開状態にあるとき, the Edit Screen shall credential ID 表示用のトグル（「ID を表示」相当の操作要素）を提供する。

5.2. While 「詳細設定」セクションが初めて展開された直後の状態であるとき, the Edit Screen shall credential ID の値を **非表示（hidden）** で初期化する。

5.3. When ユーザーが credential ID 表示トグルを操作（オン）したとき, the Edit Screen shall 当該 credential の内部 ID（`Credential.id` の数値表現）を表示する。

5.4. When ユーザーが credential ID 表示トグルを操作（オフ）したとき, the Edit Screen shall credential ID の値を再度非表示に戻す。

5.5. While Edit 画面が新規作成モードであるとき, the Edit Screen shall credential ID 行および表示トグルを表示しない、または「未保存」相当の placeholder のみを提示する。

### Requirement 6: 既存 Edit 画面挙動の保持

**Objective:** As a 既存のクレデンシャル編集ユーザー, I want 詳細設定セクションの追加によって既存の保存・編集・削除フローが変化しないことを保証されたい, so that 既存の動作に影響を受けず本機能を導入できる

#### Acceptance Criteria

6.1. The Edit Screen shall 詳細設定セクションの追加によって、Requirement 1.1 / 1.3 / 1.4 / 1.5 / 2.1 / 2.2 / 2.3（MVP requirements.md の Credential 登録・署名取得関連 AC）の挙動を変更しない。

6.2. The Edit Screen shall 詳細設定セクションの表示・展開・credential ID トグル操作によって、フォーム入力中の package name / username / password / 表示名（label）の入力状態をクリアしない。

6.3. While Edit 画面が編集モードであるとき, the Edit Screen shall 詳細設定セクション内のいかなる表示要素も **編集（書き換え）不可** なものとして提示する（読み取り専用）。

## Non-Functional Requirements

### NFR 1: セキュリティ・可観測性

1.1. The Edit Screen shall 完全な 64 文字 SHA-256 hex 文字列を logcat、Throwable のメッセージ／スタックトレース、analytics、クラッシュレポートに出力しない（MVP の NFR 5.1 を継承）。

1.2. Where SHA-256 hex を診断目的で記録する必要があるとき, the Edit Screen shall 既存の hex プレビュー方針（先頭 8 文字 + 省略マーカー）に準拠した形式のみを記録する。

1.3. The Edit Screen shall credential ID 表示トグルがオフの状態で credential ID の数値そのものが画面上に描画されないことを保証する（ID プレースホルダ・遮蔽文字以外の数値情報を露出しない）。

1.4. The Edit Screen shall 詳細設定セクション内のいずれの表示要素も、平文 password・復号済み credential 本体を露出しない（password 入力フィールドの既存挙動は本機能で変更しない）。

### NFR 2: アクセシビリティ・操作性

2.1. The Edit Screen shall 詳細設定セクション見出しおよび credential ID 表示トグルに対し、TalkBack 等のスクリーンリーダーで操作可能な accessibility ラベルを付与する。

2.2. The Edit Screen shall 詳細設定セクションの展開・折りたたみ状態変化を、スクリーンリーダーが認識できるアクセシビリティイベントとして通知する。

2.3. The Edit Screen shall SHA-256 hex のコピー導線に対し、タップ可能領域の最小寸法を Android 標準のアクセシビリティ推奨値（48dp 相当）以上で提供する。

### NFR 3: 国際化

3.1. The Edit Screen shall 詳細設定セクションのラベル（「詳細設定」「登録日」「更新日」「署名 SHA-256」「署名取得日」「ID を表示」等）および「未取得」「コピーしました」等の状態文言を、既存のローカライズリソース機構を用いて提供する。

## Out of Scope

以下は本 Issue では実装しない。

- **「署名を再取得」ボタン**（既存 credential の signatureSha256 / signatureCapturedAt を保存時点の値で更新する操作）。Option B の決定により、別 Issue として切り出す。
- 詳細設定セクションへの **新規データ項目の追加**（パスワード強度履歴、最終 Autofill 利用日時、利用回数統計等）。Issue 本文に列挙された 5 項目のみを対象とする。
- 詳細設定セクション経由での **credential 削除導線**（既存の削除導線は別途存在しており、本機能で変更しない）。
- Credential List 画面・Onboarding 画面・Autofill UI 等、Edit 画面 **以外**への詳細情報表示。
- SHA-256 hex の **コロン区切り表示**（`a1:2f:9e:…:cb:01` のような mock 上のフォーマット）の採用判断。展開後の完全表示は連続 64 文字 lowercase hex とし、表示装飾は design.md / 実装で決定する。
- 詳細設定セクションの **状態永続化**（アプリ再起動や画面再生成をまたいだ展開状態の保持）。本 Issue では同一 Edit 画面ライフサイクル内のみで状態を保持する。

## Open Questions

なし。

確定事項として人間判断が反映されており（Option A: credential ID をデフォルト非表示で表示 /
Option B: 「署名を再取得」ボタンは別 Issue 化）、本要件範囲では未確定事項は残っていない。
詳細な日時フォーマット文字列・装飾の選択は design / 実装フェーズで決定する。
