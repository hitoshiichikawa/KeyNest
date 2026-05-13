# Requirements Document

## Introduction

KeyNest の UI デザイン（PR #7 / `design/spec.md` / `design/tokens.css`）は、本文・見出しに
**Manrope**、package name / SHA-256 / password 等のモノスペース表示に **JetBrains Mono** を
指定している。一方で現行実装は両 typeface とも導入されておらず、本文・見出しは Android
system default（Roboto 系）、モノスペース表示は `android:fontFamily="monospace"` リテラル
指定による Android 内蔵モノスペースで代用されている（`app/src/main/res/layout/credential_edit_activity.xml`
の signature hex / credential ID 行が該当）。

KeyNest は MVP requirements.md `NFR 1.5` で「完全ローカル保存・credential のネットワーク
送信なし」を宣言しており、機械的保証として `AndroidManifest.xml` で
`android.permission.INTERNET` を**非宣言**としている（Issue 本文で参照されている "NFR 4.2"
は本 repo 内では MVP NFR 1.5 に対応する）。この制約下で Manrope / JetBrains Mono を採用する
方式として、Issue 本文では (A) 現状維持 / (B) Downloadable Fonts API（`FontsContractCompat`）/
(C) フォントを APK に bundle する の 3 案が提示されたが、Issue コメントで **案 C: APK
bundle 方式**が確定方針として採用された。

本要件は、Manrope と JetBrains Mono を `.ttf` または `.otf` 形式でアプリリソースとして
同梱し、本文・見出し・モノスペース表示それぞれに適用することで、PR #7 デザインとの
typeface 整合を達成しつつ MVP NFR 1.5（INTERNET 非宣言・完全ローカル）を引き続き満たす
ことを目的とする。動的フォント切替・Downloadable Fonts API・ユーザーによるフォント変更
UI は本 Issue のスコープ外とする。

## 確定事項（人間判断の反映）

Issue #13 本文の Open Question OQ-2 のうち、Issue コメントで確定済みの判断を本要件に反映する。

- **採用方式 = 案 C（APK bundle）**: Manrope と JetBrains Mono を `.ttf` または `.otf` 形式で
  アプリリソースとして同梱する。案 B（Downloadable Fonts API）と案 A（現状維持）は本 Issue
  では採用しない。
- **NFR 1.5 整合**: bundle 方式により、フォント取得のためのネットワーク通信は一切発生しない。
  `android.permission.INTERNET` 非宣言の MVP 状態を引き続き維持する。

## Requirements

### Requirement 1: Manrope の同梱と本文・見出しへの適用

**Objective:** As a KeyNest 利用者, I want PR #7 デザインで指定された Manrope typeface が
本文・見出しで実際に表示されることを期待する, so that デザインモックとアプリ実機表示の typeface 不一致が解消され、視認性・ブランド一貫性が担保される

#### Acceptance Criteria

1.1. The KeyNest App shall Manrope typeface のフォントファイル（`.ttf` または `.otf`）をアプリのリソースとして同梱し、ネットワーク取得を伴わずに端末上で利用可能な状態で提供する。

1.2. The KeyNest App shall 同梱した Manrope を、デザインモック（`design/tokens.css` / `design/spec.md`）で **本文** および **見出し**として指定されているテキスト要素に適用する。

1.3. The KeyNest App shall 同梱する Manrope のフォントファイル一式に **SIL Open Font License 1.1 (OFL)** のライセンス本文（`OFL.txt` 相当）をアプリリソースとして同梱する。

1.4. If 同梱した Manrope フォントリソースの読み込みが何らかの理由で失敗したとき, the KeyNest App shall Android system default フォントへフォールバックし、テキスト自体は依然として表示する（クラッシュ・空白表示を起こさない）。

1.5. The KeyNest App shall Manrope を適用したテキスト要素が、デザイン上 **日本語文字を含み得る**箇所（ラベル・本文・credential の `label` 値等）について、日本語グリフが欠落した場合でも Android 既定の日本語フォントフォールバックによって判読可能な状態を維持する。

### Requirement 2: JetBrains Mono の同梱とモノスペース表示への適用

**Objective:** As a KeyNest 利用者, I want package name / SHA-256 / credential ID 等のモノスペース表示が JetBrains Mono で表示されることを期待する, so that 桁揃え・hex 文字列の視認性が向上し、PR #7 デザインと整合する

#### Acceptance Criteria

2.1. The KeyNest App shall JetBrains Mono typeface のフォントファイル（`.ttf` または `.otf`）をアプリのリソースとして同梱し、ネットワーク取得を伴わずに端末上で利用可能な状態で提供する。

2.2. The KeyNest App shall 同梱した JetBrains Mono を、現行実装で `android:fontFamily="monospace"` 等の Android 内蔵モノスペース指定が施されているテキスト要素（少なくとも編集画面の signature hex 表示行および credential ID 表示行）に対して適用する。

2.3. The KeyNest App shall 同梱した JetBrains Mono を、デザインモック（`design/spec.md` "Mono" 節）で **モノスペース表示**として指定されている package name 表示・SHA-256 表示・password 表示等の箇所に適用する。

2.4. The KeyNest App shall 同梱する JetBrains Mono のフォントファイル一式に **SIL Open Font License 1.1 (OFL)** のライセンス本文（`OFL.txt` 相当）をアプリリソースとして同梱する。

2.5. If 同梱した JetBrains Mono フォントリソースの読み込みが何らかの理由で失敗したとき, the KeyNest App shall Android 内蔵モノスペースフォントへフォールバックし、テキスト自体は依然として等幅で表示する（クラッシュ・空白表示・プロポーショナル化を起こさない）。

### Requirement 3: ネットワーク非依存性の保証（MVP NFR 1.5 継承）

**Objective:** As a KeyNest 運用者, I want フォント導入によって完全ローカル動作・INTERNET 非宣言の MVP 機械的保証が破られないことを保証されたい, so that 本機能の導入が MVP セキュリティ・プライバシー要件（NFR 1.5）に対する後退を引き起こさない

#### Acceptance Criteria

3.1. The KeyNest App shall フォント表示のために `android.permission.INTERNET` を `AndroidManifest.xml` で宣言しない（MVP NFR 1.5 を継承）。

3.2. The KeyNest App shall フォント表示のために Downloadable Fonts API（`androidx.core.provider.FontsContractCompat` / Google Fonts Provider 等）を呼び出さず、ネットワーク経由のフォント取得を発生させない。

3.3. The KeyNest App shall フォント関連のクラッシュレポート・診断ログを送出する場合でも、フォント取得・適用の事実を起点としてネットワーク通信を発生させない（MVP NFR 1.5 を継承）。

### Requirement 4: 既存挙動の保持

**Objective:** As a 既存の KeyNest ユーザー / 運用者, I want Manrope / JetBrains Mono の導入によって、credential 登録・編集・削除・Autofill 候補表示・Vault アンロックなど MVP / Issue #9 / Issue #10 で確定済みの挙動が影響を受けないことを保証されたい, so that フォント導入が機能的リグレッションを引き起こさない

#### Acceptance Criteria

4.1. The KeyNest App shall 本機能の追加によって、MVP requirements.md の Requirement 1 / 3 / 4 / 5 / 6（credential 登録・Autofill 候補表示・署名照合・Vault アンロック・Autofill Service 有効化導線）の挙動を変更しない。

4.2. The KeyNest App shall 本機能の追加によって、Issue #9 requirements.md の Requirement 1〜6（一覧画面の検索・フィルタ・最近使ったカルーセル・並び替え・overflow メニュー）の挙動を変更しない。

4.3. The KeyNest App shall 本機能の追加によって、Issue #10 requirements.md の Requirement 1〜8（Settings 画面・Danger Zone 画面）の挙動を変更しない。

4.4. The KeyNest App shall モノスペース表示の対象テキスト要素（signature hex / credential ID 等）について、現行のレイアウト幅・コピー可否（`textIsSelectable`）・タップ可否などの非フォント属性を変更しない。

## Non-Functional Requirements

### NFR 1: APK サイズ・配布インパクト

1.1. The KeyNest App shall Manrope + JetBrains Mono + 各 OFL ライセンス本文の同梱による APK サイズ増分を、フォント導入前のリリース APK サイズに対して **+3 MB 以内**に収める（フォントファイル合計および OFL ライセンスの両方を含む計測値）。

### NFR 2: ライセンス遵守

2.1. The KeyNest App shall Manrope および JetBrains Mono の各 OFL ライセンス本文を、ユーザーがアプリ内から閲覧可能な経路（Issue #10 Requirement 5 の OSS ライセンス一覧、または同等の表示画面）で提供する。

2.2. The KeyNest App shall 同梱する Manrope および JetBrains Mono に対して、SIL Open Font License 1.1 が禁ずる行為（OFL を含まない再配布・フォント名そのままの改変版同梱・フォントを単体販売する等）を行わない。

### NFR 3: 応答性能

3.1. When ユーザーが任意の画面（一覧 / 編集 / Settings / Danger Zone）を起動したとき, the KeyNest App shall 同梱フォントの読み込みに起因する初期描画遅延を、Autofill Service の `onFillRequest` 応答時間（MVP NFR 2.1 = 中央値 300ms 以内）に影響を与えない形で行う。

3.2. When ユーザーが credential 編集画面を開いたとき, the KeyNest App shall signature hex 行および credential ID 行の JetBrains Mono 表示が、画面の主要コンテンツの初期描画と**同一フレーム内**で完了する（フォント未ロード状態での暫定描画 → ロード後の再描画によるテキストのちらつきを発生させない）。

### NFR 4: アクセシビリティ

4.1. The KeyNest App shall Manrope / JetBrains Mono 適用箇所において、Android システムのフォントスケール設定（`fontScale`）変更が引き続き反映される状態を維持する。

4.2. The KeyNest App shall Manrope / JetBrains Mono 適用箇所のテキスト色・コントラスト比を、フォント変更前と同等以上に維持し、WCAG 2.1 AA 相当（通常テキスト 4.5:1、大テキスト 3:1）の基準を下回らせない。

## Out of Scope

以下は本 Issue では実装しない。

- **案 A（現状維持）および案 B（Downloadable Fonts API / `FontsContractCompat` / Google Fonts Provider 経由）**: 確定事項により案 C（APK bundle）を採用するため、案 A / B の実装・併用は行わない。
- **動的フォント切替**: ユーザーが Manrope / JetBrains Mono 以外の typeface を選択するためのアプリ内 UI は提供しない。
- **ユーザーによるフォント変更 UI（Settings 画面でのフォント設定項目）**: Settings 画面（Issue #10）には本 Issue でフォント関連項目を追加しない。
- **多言語フォント対応**: CJK 拡張・アラビア語・ヘブライ語・タイ語等、Manrope / JetBrains Mono がカバーしない言語向けの追加フォント同梱は本 Issue では扱わない。日本語表示は Android 既定の日本語フォントフォールバックに委ねる（Req 1.5）。
- **`Noto Sans JP` の APK bundle**: `design/tokens.css` で日本語 fallback として参照されている Noto Sans JP は、本 Issue では bundle しない（端末既定の日本語フォントフォールバックに依存する）。bundle の要否は Open Questions に列挙する。
- **動的フォントテーマ（ダーク / ライト / 高コントラストごとに typeface を変える等）**: 単一の typeface セットを全テーマで使用する。
- **アプリ内 OSS ライセンス一覧画面の新設**: Issue #10 Requirement 5 で既定された OSS ライセンス画面に OFL を含めることのみを対象とし、ライセンス画面そのものの再設計は別 Issue で扱う。

## Open Questions

以下は本 Issue 範囲内では確定させず、人間判断（Issue コメント）または設計フェーズに委ねる事項。

- **Manrope の同梱 weight 集合**: `design/tokens.css` および `design/KeyNest Design.html` は Manrope を `400 / 500 / 600 / 700 / 800` の **5 weight 全て** Google Fonts から読み込んでいるが、案 C（APK bundle）採用時は APK サイズ抑制のために実使用 weight に絞る判断が必要。デザインモック上の優先順位は (a) `500`（本文）/ (b) `700`（中見出し・UI emphasis）/ (c) `800`（大見出し）の 3 weight が頻出であり、`400` / `600` は使用箇所が限定的に見える。本要件 Req 1.1 では「Manrope を同梱する」とのみ規定し、具体的な weight 集合は人間判断（または Architect の判断）に委ねる。
- **JetBrains Mono の同梱 weight 集合**: Issue #13 本文の案 B スコープ説明では「JetBrains Mono Regular のみ」と書かれているが、`design/tokens.css` および `design/KeyNest Design.html` では JetBrains Mono を `500 / 700` の 2 weight で読み込んでいる。本 Issue では bundle 方式（案 C）に切り替わったため、Issue 本文の「Regular のみ」をそのまま採用するか、デザインに合わせて `500 / 700` を採用するか、人間判断に委ねる。本要件 Req 2.1 では「JetBrains Mono を同梱する」とのみ規定する。
- **`Noto Sans JP` の取扱**: 日本語表示について、Android 既定の日本語フォントフォールバック（端末ベンダーごとに Noto Sans CJK / Droid Sans Japanese 等が異なる）に委ねるか、デザイン整合のために Noto Sans JP も同梱するか、人間判断に委ねる。同梱する場合は APK サイズ増分の規模が大きく変化する（Noto Sans JP は単一 weight でも 1〜2 MB 規模）ため、NFR 1.1 の +3 MB 上限の見直しも必要になる。
- **同梱フォントの「適用範囲」の具体特定**: 本要件 Req 1.2 / Req 2.3 は「デザインモックで本文・見出し・モノスペース指定された箇所」と抽象的に規定している。具体的にどの XML レイアウトファイル / どの Compose Theme typography slot に Manrope / JetBrains Mono を適用するかは、設計フェーズ（Architect）で確定する。
- **APK サイズ上限 +3 MB の妥当性**: NFR 1.1 で +3 MB を上限と定めたが、上記 weight 集合決定および Noto Sans JP 同梱可否が確定するまでは暫定値である。weight 集合確定後に上限を再設定する判断を人間に委ねる。
- **フォントリソースの圧縮配置可否**: Android の `aaptOptions.noCompress` に `.ttf` / `.otf` を追加するか否か（未圧縮配置は読込速度を稼げるが APK サイズが増える）。本要件 NFR 3.2「同一フレーム内描画」と NFR 1.1「APK +3 MB 以内」のトレードオフであり、設計フェーズで判断する。
