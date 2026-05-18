# Requirements Document

## Introduction

KeyNest の MVP（`docs/specs/1--easykeynest-mvp-packagename-autofill/requirements.md`）と
Issue #9（一覧画面の検索・フィルタ・カルーセル等）は出揃ったが、デザインモック
（`design/screens/screens-2.jsx` の `ScreenSettings`）に存在する「設定画面」自体は
未実装である。ユーザーは現状、Autofill Service の有効化状態・端末ロック方式・Vault の
登録件数・アプリのバージョン / ライセンスといった**運用情報**をアプリ内から確認できず、
万一のリセット（保存済み credential の一括クリア）操作にも導線が無い。

本要件は、`CredentialListActivity` の overflow メニューから到達可能な Settings 画面を
新設し、(1) Autofill サービス有効化状態の表示と Android 設定への遷移、(2) 現在の
端末ロック方式の参照表示と Android Security 設定への遷移、(3) Vault のメタ情報
（登録件数 / 直近更新時刻 / 総 storage 使用量）の表示、(4) アプリ情報
（バージョン / OSS ライセンス一覧）の表示、(5) Vault 一括クリアを担う Danger Zone 画面
（再認証必須・不可逆）への導線、を提供する。Settings 画面自体は不可逆操作を一切含まず、
破壊的操作はすべて Danger Zone 画面に隔離する。

## 確定事項（人間判断の反映）

Issue #10 本文「人間判断が必要な事項」のうち、コメントで確定済みの判断を本要件に反映する。

- **ロック方式切替の扱い（Issue 本文 Option A）**: 本アプリ内ではロック方式（生体認証 /
  PIN / パターン等）の**変更 UI は提供しない**。アプリ内では現在の認証方式を表示するに留め、
  変更操作は Android のシステム設定（Security）へのディープリンクに委ねる。
- **Vault 一括クリア UI の配置（Issue 本文 Option A）**: Vault 一括クリアは Settings 画面
  本体には配置せず、**別画面（Danger Zone）に分離**する。Settings からの明示的遷移が必要で、
  実行時に再認証を必須とし、操作は不可逆とする。
- **エクスポート項目の扱い（Issue #9 で確定済み）**: モック `screens-2.jsx` 内の
  `<SettingRow label="エクスポート" sub="暗号化バックアップ"/>` は本機能で**実装しない**。
  Settings 画面に「エクスポート」項目は配置しない。

## Requirements

### Requirement 1: Settings 画面への導線

**Objective:** As a 一覧画面利用者, I want CredentialListActivity の overflow メニューから設定画面を開きたい, so that 既存の画面構造を壊さずに設定機能へ到達できる

#### Acceptance Criteria

1.1. The Credential List Screen shall ツールバーの overflow メニュー内に「設定」相当のメニュー項目を提供する。

1.2. When ユーザーが overflow メニューの「設定」項目を選択したとき, the Credential List Screen shall Settings 画面（Settings Screen）を新規に起動する。

1.3. The Settings Screen shall アプリバーに戻るアイコンを備え、ユーザーが戻る操作を行ったとき、Settings 画面を閉じて呼び出し元の Credential List Screen に復帰する。

1.4. The Settings Screen shall MVP requirements.md の Requirement 1 / 3 / 4 / 5 / 6（credential 登録・Autofill 候補表示・署名照合・Vault アンロック・Autofill Service 有効化導線）の挙動を変更しない。

### Requirement 2: Autofill サービスの有効化状態表示と設定遷移

**Objective:** As a Settings 画面利用者, I want KeyNest が Android の Autofill Service として有効化されているかを確認し、必要なら Android 設定に遷移したい, so that Autofill が動かない原因の切り分けと再有効化を自己解決できる

#### Acceptance Criteria

2.1. While Settings 画面が表示状態であるとき, the Settings Screen shall KeyNest が現在 Android の Autofill Service として選択されているか否かを、ユーザーが識別可能な形で表示する（「有効」「未設定」相当の文言またはバッジ）。

2.2. The Settings Screen shall Autofill サービス状態表示の判定を、MVP Requirement 6 と同じ判定ロジック（Autofill Service として現在選択されているかを問い合わせる仕組み）に基づいて行う。

2.3. The Settings Screen shall Autofill サービス状態表示の近傍に「Android 設定で確認」相当のアクションを配置する。

2.4. When ユーザーが「Android 設定で確認」アクションを操作したとき, the Settings Screen shall Android の Autofill Service 選択設定画面（MVP Requirement 6.2 と同じ `Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE` 相当の遷移先）を起動する。

2.5. When ユーザーが Android 設定画面から Settings 画面に戻ったとき, the Settings Screen shall Autofill サービス状態表示を最新の有効化状態に更新する。

2.6. If Android 設定画面への遷移に失敗した（対応する Intent を解決できる Activity が存在しない）とき, the Settings Screen shall ユーザーに失敗の旨を提示し、Settings 画面の表示状態を維持する。

### Requirement 3: ロック方式の表示と Android Security 設定への遷移

**Objective:** As a Settings 画面利用者, I want 現在の端末ロック方式を確認し、変更したい場合は Android のシステム設定へ案内されたい, so that BiometricPrompt の挙動と整合する形でロック方式を管理できる

#### Acceptance Criteria

3.1. While Settings 画面が表示状態であるとき, the Settings Screen shall 端末で現在利用可能な認証方式（生体認証の有無 / 端末ロック方式の有無）を要約した**読み取り専用**の表示を提供する。

3.2. The Settings Screen shall ロック方式表示において、認証方式の**変更操作を提供しない**（トグル・チェックボックス等のアプリ内変更 UI を配置しない）。

3.3. The Settings Screen shall ロック方式表示の近傍に「Android のセキュリティ設定を開く」相当のアクションを配置する。

3.4. When ユーザーが「Android のセキュリティ設定を開く」アクションを操作したとき, the Settings Screen shall Android のセキュリティ設定画面（`Settings.ACTION_SECURITY_SETTINGS` 相当）を起動する。

3.5. When ユーザーが Android 設定画面から Settings 画面に戻ったとき, the Settings Screen shall ロック方式表示を最新の状態に更新する。

3.6. If Android のセキュリティ設定画面への遷移に失敗した（対応する Intent を解決できる Activity が存在しない）とき, the Settings Screen shall ユーザーに失敗の旨を提示し、Settings 画面の表示状態を維持する。

### Requirement 4: Vault メタ情報の表示

**Objective:** As a Settings 画面利用者, I want Vault に保存されている credential 件数・直近更新時刻・総 storage 使用量を確認したい, so that バックアップ計画・棚卸し・初期化判断のための情報を一目で得られる

#### Acceptance Criteria

4.1. While Settings 画面が表示状態であるとき, the Settings Screen shall Vault に保存されている credential の**総件数**を整数値で表示する。

4.2. While Settings 画面が表示状態であるとき, the Settings Screen shall いずれかの credential が最後に作成または更新された**直近の更新時刻**を、端末ローカルタイムゾーンに変換した読み取り可能な形式で表示する。

4.3. If 1 件も credential が登録されていないとき, the Settings Screen shall 直近更新時刻を「未登録」または「—」相当の placeholder で表示する（不正な時刻値を露出しない）。

4.4. While Settings 画面が表示状態であるとき, the Settings Screen shall KeyNest の Vault が占有している**総 storage 使用量**を、人間可読の単位（KB / MB 等）に丸めた形式で表示する。

4.5. The Settings Screen shall 件数 / 直近更新時刻 / storage 使用量の表示において、credential 個別の label / username / packageName / password / 署名ハッシュを露出しない（集計値・タイムスタンプ・バイト数のみを表示する）。

4.6. The Settings Screen shall Vault メタ情報の取得を**完全ローカル**で行い、ネットワーク送信を発生させない。

### Requirement 5: アプリ情報（バージョン / OSS ライセンス一覧）

**Objective:** As a Settings 画面利用者, I want アプリのバージョンと OSS ライセンス一覧を確認したい, so that 動作中のビルドを特定し、第三者ライブラリのライセンス義務を充足できる

#### Acceptance Criteria

5.1. While Settings 画面が表示状態であるとき, the Settings Screen shall 現在インストールされている KeyNest のバージョン名（`versionName` 相当）を識別可能な形で表示する。

5.2. The Settings Screen shall 「OSS ライセンス一覧」相当のメニュー項目を提供する。

5.3. When ユーザーが「OSS ライセンス一覧」項目を操作したとき, the Settings Screen shall 第三者ライブラリのライセンス情報を一覧表示する画面（androidx の `OssLicensesMenuActivity` 相当）を起動する。

5.4. If OSS ライセンス一覧画面の起動に失敗した（対応する Activity が存在しない / 一覧データの生成に失敗した）とき, the Settings Screen shall ユーザーに失敗の旨を提示し、Settings 画面の表示状態を維持する。

### Requirement 6: Danger Zone 画面への遷移

**Objective:** As a Settings 画面利用者, I want 不可逆な破壊的操作（Vault 一括クリア）を通常の設定項目とは分離された画面でのみ実行したい, so that 誤タップによる全データ消失を防げる

#### Acceptance Criteria

6.1. The Settings Screen shall Vault 一括クリア相当の不可逆操作を Settings 画面本体に**直接配置しない**。

6.2. The Settings Screen shall Danger Zone 画面（Danger Zone Screen）への明示的な遷移メニュー項目を提供する。

6.3. When ユーザーが Danger Zone 画面への遷移項目を操作したとき, the Settings Screen shall Danger Zone Screen を新規に起動する。

6.4. The Settings Screen shall Danger Zone 画面遷移項目を、視覚的に他の設定項目と区別可能な形（警告色・「危険」相当のラベル等）で提示する。

### Requirement 7: Vault 一括クリア（Danger Zone）

**Objective:** As a Danger Zone 画面利用者, I want 再認証と明示的な確認を経て Vault に保存されたすべての credential を消去したい, so that 端末譲渡・棚卸しリセット時に保存済み credential を確実かつ完全に削除できる

#### Acceptance Criteria

7.1. The Danger Zone Screen shall 「Vault をすべて削除する」相当の唯一の破壊的アクションを提供する。

7.2. When ユーザーが「Vault をすべて削除する」アクションを操作したとき, the Danger Zone Screen shall まず BiometricPrompt（または端末認証フォールバック）を起動し、ユーザー認証を要求する。

7.3. If 再認証がキャンセルまたは失敗したとき, the Danger Zone Screen shall Vault に対する破壊的操作を一切行わず、Danger Zone 画面の表示状態を維持する。

7.4. When 再認証が成功したとき, the Danger Zone Screen shall 「この操作は取り消せません」相当の文言と最終確認ボタンを含む確認ダイアログを表示する。

7.5. When ユーザーが確認ダイアログ上で最終確認を承認したとき, the Danger Zone Screen shall Vault に保存されているすべての credential を永続化ストレージから消去し、credential 復号鍵に紐付く Android Keystore のエイリアスを削除する。

7.6. When 一括クリア処理が成功したとき, the Danger Zone Screen shall ユーザーに完了の旨を提示し、Settings 画面または Credential List 画面まで自動的に戻し、credential 一覧を空状態として再描画する。

7.7. If 一括クリア処理の途中で永続化ストレージの消去または Keystore エイリアス削除のいずれかが失敗したとき, the Danger Zone Screen shall ユーザーに失敗の旨を提示し、再試行の手段（再認証からやり直す等）を提供する。

7.8. The Danger Zone Screen shall 一括クリア処理を**完全ローカル**で行い、消去された credential の情報をネットワーク経由に送出しない。

7.9. The Danger Zone Screen shall 一括クリア完了後、消去された credential が Autofill 候補（MVP Requirement 3）として返却され得ない状態にする。

### Requirement 8: モック上の「エクスポート」項目の取り扱い

**Objective:** As a 実装担当者, I want デザインモックに含まれる「エクスポート」項目を Settings 画面に持ち込まない方針を明示的に保証されたい, so that スコープ外の機能をうっかり実装することがない

#### Acceptance Criteria

8.1. The Settings Screen shall `screens-2.jsx` の `ScreenSettings` に存在する `<SettingRow label="エクスポート" sub="暗号化バックアップ"/>` 相当の項目を**配置しない**。

8.2. The Settings Screen shall 「エクスポート」「暗号化バックアップ」相当のラベル文言・アクションを画面上に表示しない。

8.3. The Settings Screen shall credential を端末外へ書き出す操作（ファイル出力 / 共有 Intent / クリップボードへの一括コピー等）を**いかなる導線からも提供しない**。

## Non-Functional Requirements

### NFR 1: セキュリティ・プライバシー

1.1. The Settings Screen shall Autofill 状態 / ロック方式 / Vault メタ情報 / アプリバージョン情報をネットワーク送信・analytics・クラッシュレポートに送出しない（MVP NFR 1.5 を継承）。

1.2. The Settings Screen shall Vault メタ情報の表示・診断ログ出力において、credential の username / packageName / label / password / 署名 SHA-256 ハッシュの平文を出力しない（MVP NFR 5.1 を継承）。

1.3. The Danger Zone Screen shall Vault 一括クリアを実行する前に、必ず BiometricPrompt（または端末認証フォールバック）による再認証成功を要求し、認証なしでの破壊的操作実行を不可とする。

1.4. The Danger Zone Screen shall 一括クリア完了後、復号済み credential をプロセスメモリ上に保持しない（MVP NFR 1.4 / 5.5 と整合）。

### NFR 2: 応答性能

2.1. When ユーザーが Settings 画面を起動したとき, the Settings Screen shall Vault メタ情報（件数 / 直近更新時刻 / storage 使用量）の取得を、Autofill Service の `onFillRequest` 応答時間（MVP NFR 2.1 = 中央値 300ms 以内）に影響を与えない形で実行する。

2.2. When ユーザーが Settings 画面を起動したとき, the Settings Screen shall 画面の主要コンテンツ（Autofill 状態 / ロック方式 / Vault 件数 / バージョン）の初期描画までの所要時間中央値を **500ms 以内** に収める（登録件数 500 件規模の端末で計測）。

### NFR 3: アクセシビリティ・操作性

3.1. The Settings Screen shall すべての設定項目・遷移アクション・状態バッジに、TalkBack 等のスクリーンリーダーで認識可能な accessibility ラベルを付与する。

3.2. The Settings Screen shall 各タップ可能要素のタップ可能領域の最小寸法を、Android 標準のアクセシビリティ推奨値（48dp 相当）以上で提供する。

3.3. The Danger Zone Screen shall 破壊的アクション・確認ダイアログのボタンを、スクリーンリーダーが「危険」「取り消し不可」の意図を識別可能な形で公開する。

### NFR 4: 国際化

4.1. The Settings Screen shall すべての画面表示文言（画面タイトル / 各設定項目ラベル / バッジ / アクションボタン / エラーメッセージ / placeholder）を、既存のローカライズリソース機構を用いて提供する。

4.2. The Danger Zone Screen shall すべての画面表示文言（画面タイトル / 破壊的アクションラベル / 確認ダイアログ文言 / 完了・失敗メッセージ）を、既存のローカライズリソース機構を用いて提供する。

### NFR 5: 既存挙動の保持

5.1. The Settings Screen shall 本機能の追加によって、MVP requirements.md の Requirement 1 / 3 / 4 / 5 / 6 および Issue #9 requirements.md の Requirement 1〜6 で規定された credential 登録・編集・削除・一覧操作・Autofill 候補表示・Vault アンロック・Autofill Service 有効化導線の挙動を変更しない。

5.2. The Settings Screen shall Settings 画面・Danger Zone 画面の操作（遷移・状態表示・破壊的操作以外）によって、Autofill Service 側の `onFillRequest` 応答挙動（MVP Requirement 3 / 4 / 5）に副作用を与えない。

## Out of Scope

以下は本 Issue では実装しない。

- **ロック方式（生体 / PIN / パターン等）のアプリ内変更 UI**: 確定事項により Android システム設定（Security）へのディープリンクに委ねる（Req 3.2）。
- **個別 credential 単位の削除 UI の Settings 画面への追加**: 既存の一覧画面上の長押し削除・編集画面上の削除のみを引き続き利用する。
- **credential のエクスポート / インポート / 暗号化バックアップ機能**: 確定事項により Settings 画面には配置しない（Req 8）。MVP requirements.md の Out of Scope を継承する。
- **アンロック保持時間の変更 UI**: モックの `<SettingRow label="アンロック保持時間" />` は本要件では扱わない（Vault アンロック有効期間そのものが MVP の Open Questions に残っているため）。
- **署名照合の厳密性トグル**: モックの `<SettingRow label="署名照合の厳密性" toggle/>` は本要件では扱わない（MVP Requirement 4 で常時厳密照合と確定済みのため、トグルを設けない）。
- **Settings 画面でのテーマ切替（ダーク / ライト等）・フォントサイズ調整**: 端末システム設定に従う前提。
- **Vault 一括クリアの「部分クリア」「カテゴリ単位クリア」**: 全件消去のみを対象とする（Req 7.1）。
- **Vault 一括クリア後の自動エクスポート・自動バックアップ**: 完全ローカルかつ復元手段を提供しない（Req 7.5）。
- **Settings / Danger Zone 画面の状態（スクロール位置等）の永続化**: Activity ライフサイクル内でのみ保持し、再起動をまたいだ復元は扱わない。

## Open Questions

以下は本 Issue 範囲内では確定させず、設計フェーズ（Architect / Developer）または人間判断に委ねる事項。

- **「総 storage 使用量」の計測対象範囲**: Room データベースファイルのみを対象とするか、それに加えて WAL / journal ファイル、Android Keystore に保持される暗号鍵のメタ情報、共有設定（SharedPreferences）等を加算するかを設計フェーズで確定する必要がある。Req 4.4 では「Vault が占有している総 storage 使用量」とのみ定義し、内訳の確定は委ねる。
- **「ロック方式」表示の具体的な記述粒度**: 「生体認証 + PIN」「生体認証のみ」「ロック未設定」のいずれの 3 値分類を採用するか、それとも `BiometricManager.canAuthenticate()` の戻り値を直接マッピングするかは設計フェーズで確定する必要がある。Req 3.1 では「端末で現在利用可能な認証方式を要約した読み取り専用表示」とのみ定義し、表現方法は委ねる。
- **Danger Zone 画面遷移時の追加認証要否**: 確定事項では「Vault 一括クリア実行時の再認証必須」のみが確定しており、Danger Zone 画面**を開く**時点での認証が必要かは未確定。Req 6.3 では認証なしで遷移する前提で記述しているが、運用上「画面表示時にも認証する」運用に振る場合は人間判断が必要。
- **OSS ライセンス一覧画面の生成手段**: `OssLicensesMenuActivity` は Google Play Services 系の依存を要求するため、Play Services 非搭載端末（MDM 管理端末を想定）でも動作するか、または自前のライセンス JSON / Markdown を埋め込むかを設計フェーズで判断する必要がある。Req 5.3 では「`OssLicensesMenuActivity` 相当」とのみ定義し、代替実装の余地を残す。
