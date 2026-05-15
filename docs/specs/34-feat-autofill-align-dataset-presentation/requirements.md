# Requirements Document

## Introduction

KeyNest の Autofill ドロップダウン内で各候補（Dataset）の見た目を構成する
`app/src/main/res/layout/dataset_presentation.xml` は、現状「label（太字 14sp）+ subtitle（12sp）の
縦 2 行 TextView だけ」というプレーンな実装で、`design/screens/screens-2.jsx` の `ScreenDataset`
モックで定義された KeyNest 固有のビジュアル仕様（KEYNEST ヘッダー：mark + eyebrow text + 「署名一致」
chip / 各行：アイコンタイル + label + username + 鍵アイコン / 末尾「KeyNest で新規作成」行）と乖離して
いる。本 Issue (#34) は Phase 2（`design/android-assets/mapping.md` §5 の `ScreenDataset` 行）の 6 件目
として、Phase 1 (#28) で `app/src/main/res/` に取り込まれた `@color/kn_*` / `@dimen/kn_*` /
`@style/Text.KeyNest.*` リソース、および Phase 2 先行 Issue で追加された vector drawables
（`ic_keynest_mark_24` / `ic_key_24` / `ic_plus_24` / `ic_shield_fill_16` / `kn_icon_tile_bg` 等）
を既存レイアウトに **貼り込む** ことで JSX モックと視覚的に整合させる。本 Issue は Autofill ドロップ
ダウン内に Android Autofill framework が `RemoteViews` 経由でインフレートする領域に限定して扱う。
Autofill candidate 取得ロジック (`ResolveAutofillCandidatesUseCase`) ・`KeyNestAutofillService`
のセキュリティ境界（locked Dataset / `AutofillUnlockActivity` 連携 / placeholder の `PLACEHOLDER`
処理）・`FillResponseBuilder` の dataset 構築フロー・Inline Suggestions API
（`DatasetPresentationFactory.buildInline` / `InlinePresentation`）・既存の公開シグネチャ
（`DatasetPresentationFactory.build(label, subtitle)` / `R.id.dataset_label` / `R.id.dataset_subtitle`）
は変更しない。RemoteViews は限られた View / Attribute のみサポートする（`?attr/colorXxx` が解決できない
ケースがあり、セマンティック `@color/kn_*` を直接参照する形でリソースを差し込む必要がある）点を踏まえた
要件をここで明示する。

## Requirements

### Requirement 1: Dataset presentation ルートの構造とサーフェス

**Objective:** As an エンドユーザー, I want Autofill ドロップダウン全体が KeyNest らしい白いカード状の
コンテナとして読めること, so that 他の Android Autofill provider と区別でき、自分が KeyNest の
ドロップダウンを操作していると即座に認識できる

#### Acceptance Criteria

1. The Dataset presentation shall RemoteViews がサポートする View 種別（`LinearLayout` / `FrameLayout` / `TextView` / `ImageView`）のみで構成され、`?attr/...` 形式の Material3 attribute 参照を含まない
2. The Dataset presentation shall ルートコンテナ背景色に `@color/kn_surface` を直接参照する形で適用する
3. The Dataset presentation shall ルートコンテナ内のテキスト色・アイコン tint を `@color/kn_*` セマンティックトークン（`kn_text` / `kn_text_2` / `kn_primary` 等）から直接参照する形で適用する
4. The Dataset presentation shall ルートコンテナを「KEYNEST ヘッダー領域 1 つ + Dataset 行領域 1 つ」の縦 2 段構造で構成する
5. The Dataset presentation shall 既存の View ID `dataset_label`（`R.id.dataset_label`）と `dataset_subtitle`（`R.id.dataset_subtitle`）を Dataset 行領域内に保持し、`DatasetPresentationFactory.build(label, subtitle)` から `setTextViewText` で書き込める参照経路を維持する

### Requirement 2: KEYNEST ヘッダー領域

**Objective:** As an エンドユーザー, I want ドロップダウンの上部に「KeyNest が候補を提示している」ことと
「呼び出し元アプリの署名が一致している」ことが視覚的に分かるヘッダーが表示されること, so that 候補の
出所と署名整合性を一目で確認できる

#### Acceptance Criteria

1. The Dataset presentation header shall ルートコンテナ上端に 1 つ配置される
2. The Dataset presentation header shall 背景色に `@color/kn_ink_50` 相当（JSX `#F1F4FA` 相当の `@color/kn_surface_2` セマンティックトークン）を直接参照する形で適用する
3. The Dataset presentation header shall 内側パディングに上下 `@dimen/kn_space_2`（8dp）/ 左右 `@dimen/kn_space_3`（12dp）または 14dp 相当を適用する（JSX `padding: '8px 14px'` 相当）
4. The Dataset presentation header shall 左端に KeyNest mark vector drawable（`@drawable/ic_keynest_mark_24` を含む KNMark 相当の vector）を 16dp × 16dp 相当のサイズで配置する
5. The Dataset presentation header KNMark shall アイコン tint に `@color/kn_primary` を直接参照する形で適用する
6. The Dataset presentation header shall mark の右側に eyebrow テキスト「KEYNEST」を配置する
7. The Dataset presentation header eyebrow shall TextAppearance に `@style/Text.KeyNest.Eyebrow`（11sp / weight 700 / letter-spacing 0.04em 〜 0.08em / UPPERCASE）相当を適用するか、または RemoteViews 制約上 TextAppearance が解決できない場合は同等の `textSize` / `textColor` / `textAllCaps` / `letterSpacing` をインラインで指定する
8. The Dataset presentation header eyebrow shall テキスト色に `@color/kn_text` を直接参照する形で適用する
9. The Dataset presentation header shall eyebrow テキストの右側（行末）に「署名一致」chip を 1 つ配置する
10. The Dataset presentation header signature chip shall 背景色に `@color/kn_success_soft`（JSX `#D1FAE5` 相当）を直接参照する形で適用する
11. The Dataset presentation header signature chip shall テキスト色および内側アイコン tint に `@color/kn_success` 相当（JSX `#047857` ≒ `@color/kn_success` の濃いめ系）を直接参照する形で適用する
12. The Dataset presentation header signature chip shall 内側に shield icon（`@drawable/ic_shield_fill_16` または同等の 16dp 以下の shield fill vector）を配置する
13. The Dataset presentation header signature chip shall 文字列に既存 `@string/signature_match`（"Signature OK" / "署名一致"）を表示する
14. The Dataset presentation header signature chip shall TextAppearance に `@style/Text.KeyNest.Caption`（12sp / weight 500）以下の小ぶりサイズを適用し、ピル型の角丸（`@dimen/kn_r_pill` または 999dp 相当）で描画する
15. While 呼び出し元アプリの署名が KeyNest 側に登録された署名と一致しない場合（または署名情報が未取得の場合）, the Dataset presentation header shall 当該 chip を「署名一致」表示のままレンダリングする（候補リスト自体に未登録署名のアプリは出ない設計のため、表示時は常に「署名一致」状態で問題ないことを明示）

### Requirement 3: Dataset 行（候補 1 件分）の視覚仕様

**Objective:** As an エンドユーザー, I want 候補 1 件が「アイコンタイル + label（アプリ名）+ username
+ 右端の鍵アイコン」の統一フォーマットで描画されること, so that 複数候補が並んだ際に視覚的に
区別でき、ロックされた状態（タップ後に生体認証が必要であること）が認識できる

#### Acceptance Criteria

1. The Dataset row shall 縦の 1 行として KEYNEST ヘッダーの直下に配置される
2. The Dataset row shall 行内パディングに上下 `@dimen/kn_space_2`〜`@dimen/kn_space_3`（8dp〜12dp）/ 左右 `@dimen/kn_space_3`（12dp）または 14dp を適用する（JSX `padding: '10px 14px'` 相当）
3. The Dataset row shall 行全体の最小高さ 48dp（Android Autofill タップ領域のガイドライン）以上を維持する
4. The Dataset row shall 左端に 32dp × 32dp（`@dimen/kn_icon_tile_sm`）相当のアイコンタイルを配置する
5. The Dataset row icon tile shall 角丸として `@dimen/kn_r_sm`（12dp）または `@dimen/kn_r_icon_tile`（12dp）相当を適用する（JSX `radius: 9` に最も近い KeyNest トークン）
6. The Dataset row icon tile shall 背景に `@drawable/kn_icon_tile_bg`（Phase 1 で定義済み ShapeDrawable）または同等の `@color/kn_surface_2` 背景 + 角丸の単色 ShapeDrawable を適用する
7. The Dataset row icon tile shall タイルの中央に表示する内容として「アプリ名先頭 1 文字」テキスト、または鍵 vector drawable（`@drawable/ic_key_24` 相当）のいずれかを表示する（具体的選定は design.md で確定する）
8. The Dataset row shall アイコンタイルの右側に label 行と username 行の縦 2 段テキストを表示する
9. The Dataset row label shall View ID `R.id.dataset_label` を保持し、`DatasetPresentationFactory.build(label, ...)` が渡す文字列をそのまま `setTextViewText` で書き込めること
10. The Dataset row label shall TextAppearance に `@style/Text.KeyNest.BodyS`（13sp / weight 500〜700）相当を適用するか、または RemoteViews 制約上同等の `textSize` / `textColor` / `textStyle` をインラインで指定する
11. The Dataset row label shall テキスト色に `@color/kn_text` を直接参照する形で適用する
12. The Dataset row label shall テキストの末尾省略（`maxLines="1"` + `ellipsize="end"`）を適用する
13. The Dataset row username shall View ID `R.id.dataset_subtitle` を保持し、`DatasetPresentationFactory.build(..., subtitle)` が渡す文字列をそのまま `setTextViewText` で書き込めること
14. The Dataset row username shall TextAppearance に `@style/Text.KeyNest.Caption`（12sp / weight 500）または 11sp 相当を適用するか、または RemoteViews 制約上同等の `textSize` / `textColor` / `textStyle` をインラインで指定する
15. The Dataset row username shall テキスト色に `@color/kn_text_2`（JSX `#5C6B8E` 相当）を直接参照する形で適用する
16. The Dataset row username shall テキストの末尾省略（`maxLines="1"` + `ellipsize="end"`）を適用する
17. The Dataset row shall 右端に lock 状態を示すアイコンを 1 つ配置する（JSX `DatasetRow` の `locked` 表示。本 Issue では `locked === true` 固定として描画する）
18. The Dataset row lock icon shall icon tint に `@color/kn_text_2`（JSX `#5C6B8E`）または `@color/kn_text_3` を直接参照する形で適用する
19. The Dataset row lock icon shall 16dp × 16dp 相当のサイズで描画する
20. The Dataset row lock icon shall アイコンソースとして「鍵 / ロックの状態を示す vector drawable」を表示する（既存 `@drawable/ic_key_24` のサイズ縮退、または `ic_key_filled` 等の新規 vector を選択。具体ソースの確定は design.md に委ねる）

### Requirement 4: アイコン / drawable リソースの整備

**Objective:** As a 実装メンテナ, I want 本 Issue で表示する追加 drawable（KeyNest mark / 鍵 / plus）が
`design/screens` の JSX 規格と一致した vector として `app/src/main/res/drawable/` 配下に存在する状態
を保つこと, so that 後続 Phase の他画面（Inline Suggestions / Unlock 画面など）でも同一 vector を再利用
できる

#### Acceptance Criteria

1. The Drawable resources shall KeyNest mark の vector drawable を `app/src/main/res/drawable/` 配下に持つ（既存 `ic_keynest_mark_24.xml` を流用するか、本 Issue でヘッダー向け 16dp 版を新規追加するかは design.md で確定する）
2. The Drawable resources shall plus icon vector drawable を `app/src/main/res/drawable/` 配下に持つ（既存 `ic_plus_24.xml` を流用する）
3. The Drawable resources shall 鍵 / ロック icon vector drawable を `app/src/main/res/drawable/` 配下に持つ（既存 `ic_key_24.xml` を流用するか、Dataset 行末尾の 16dp filled lock 用に新規 vector を追加するかは design.md で確定する）
4. The Drawable resources shall 「KeyNest で新規作成」行の背景に使う単色 ShapeDrawable（`@color/kn_blue_50` 背景 + 角丸 0dp）を、既存 ShapeDrawable で代替できない場合は新規追加する（既存リソースで代替する場合は新規追加を行わない）
5. The Drawable resources shall 本 Issue で新規追加した drawable のラスタライズ済み色値（`fillColor`）が `@color/kn_*` セマンティックトークンを参照するか、または `@android:color/white` のような monochrome 既定色を参照する形にし、`#RRGGBB` 直書きを行わない

### Requirement 5: 末尾「KeyNest で新規作成」行

**Objective:** As an エンドユーザー, I want 表示中の候補とは別に「新規クレデンシャルを KeyNest に登録する」
導線が末尾に表示されること, so that 既存の候補にマッチしないアプリでも、その場で新規登録するという
次のアクションを発見できる

#### Acceptance Criteria

1. The Dataset presentation shall Dataset 行の下に「KeyNest で新規作成」行を 1 つ表示する
2. The "新規作成" row shall 背景色に `@color/kn_blue_50`（JSX `#EAF2FE` 相当）を直接参照する形で適用する
3. The "新規作成" row shall 行内パディングに上下 `@dimen/kn_space_2`〜`@dimen/kn_space_3`（8dp〜12dp）/ 左右 `@dimen/kn_space_3`（12dp）または 14dp を適用する（JSX `padding: '10px 14px'` 相当）
4. The "新規作成" row shall 左端に plus icon（`@drawable/ic_plus_24`）を 16dp × 16dp 相当で配置する
5. The "新規作成" row plus icon shall icon tint に `@color/kn_primary` を直接参照する形で適用する
6. The "新規作成" row shall plus icon の右側にラベル文字列を配置する
7. The "新規作成" row label shall TextAppearance に `@style/Text.KeyNest.BodyS`（13sp / weight 500〜700）または 13sp / weight 600 相当を適用するか、または RemoteViews 制約上同等の `textSize` / `textColor` / `textStyle` をインラインで指定する
8. The "新規作成" row label shall テキスト色に `@color/kn_primary` を直接参照する形で適用する
9. The "新規作成" row label shall ラベル文字列として新規ローカライズ済み文字列リソース（「+ 新しいクレデンシャル」相当）を表示する
10. The "新規作成" row label shall 当該文字列を英語 (`values/strings.xml`) と日本語 (`values-ja/strings.xml`) の両方に同一キーで追加する
11. While 本 Issue の現スコープでは, the "新規作成" row shall RemoteViews 上の表示要素として描画されるが、押下時の遷移（`CredentialEditActivity` 等への onClick）は実装しない（押下を発生させない表示専用行として扱う）
12. The "新規作成" row shall Issue 本文 DoD「末尾『KeyNest で新規作成』行」の視覚要件を満たすため、JSX `ScreenDataset` のフッター行と同等の plus icon + テキスト構造を保持する

### Requirement 6: ライト / ダーク両モードの描画整合

**Objective:** As an エンドユーザー, I want システムテーマ（Light / Dark）の切り替え後も Autofill
ドロップダウンの配色が破綻せず読みやすいこと, so that 端末設定を変えても KeyNest の候補が常に
判読可能である

#### Acceptance Criteria

1. While 端末のシステム設定がライトモードである場合, the Dataset presentation shall `values/colors.xml` の `kn_*` セマンティックトークンで本文・背景・アイコンを解決する
2. While 端末のシステム設定がダークモードである場合, the Dataset presentation shall `values-night/colors.xml` の `kn_*` セマンティックトークンで本文・背景・アイコンを解決するか、または Android Autofill framework が dataset presentation 用に dark variant を自動適用する仕組みに従う
3. The Dataset presentation shall ライト / ダーク両モードで本文テキスト（KEYNEST eyebrow / label / username / 「新規作成」ラベル）と背景のコントラスト比が WCAG 2.1 AA の本文最小 4.5:1 以上となる Phase 1 で定義済みのトークン値を使用する
4. The Dataset presentation shall ライト / ダーク両モードで非文字要素（KEYNEST ヘッダー背景 / アイコンタイル背景 / 「新規作成」行背景 / lock icon）と隣接要素のコントラスト比が WCAG 2.1 AA の非文字最小 3:1 以上となる Phase 1 で定義済みのトークン値を使用する
5. While 端末のシステム設定がダークモードである場合, the "新規作成" row shall `@color/kn_blue_50` の `values-night` 上書き値、または同等のセマンティックトークンでダーク環境上でラベル文字色 `@color/kn_primary` と 4.5:1 以上のコントラストを維持する
6. If Android Autofill framework が `RemoteViews` 経由で本 dataset を任意の host activity（呼び出し元アプリ）上に描画する場合, the Dataset presentation shall 自身の背景色（`@color/kn_surface` 等）を確実に塗ることで、host activity 側の地色が透過してテキストが判読不能になる事態を避ける

### Requirement 7: 既存機能・配線の不変

**Objective:** As an 既存ユーザー / メンテナ, I want UI の見た目変更によって Autofill のセキュリティ
境界・データセット構築フロー・既存テストが壊れないこと, so that 本 Issue のリリース前後で Autofill
の機能差分を意識せずに利用できる

#### Acceptance Criteria

1. The Dataset presentation shall 既存の `DatasetPresentationFactory.build(label: String, subtitle: String): RemoteViews` 公開シグネチャを保持する
2. The Dataset presentation shall 既存の `R.id.dataset_label` および `R.id.dataset_subtitle` の View ID を保持し、`setTextViewText` で書き込み可能であること
3. The Dataset presentation shall 既存の `DatasetPresentationFactory.buildInline(label, subtitle, spec)` 経路を変更しない（本 Issue は popup 表示用 `RemoteViews` のみを対象とする）
4. The Dataset presentation shall 既存の `KeyNestAutofillService.onFillRequest` セキュリティ境界（locked Dataset / `AutofillUnlockActivity` への `setAuthentication` / `FillResponseBuilder.PLACEHOLDER` = "••••••" 注入）を変更しない
5. The Dataset presentation shall `FillResponseBuilder.buildLockedResponse` / `buildLockedDataset` の Dataset 構築ロジックを変更しない（本 Issue は presentation の `RemoteViews` 内のみを変更する）
6. The Dataset presentation shall `AutofillCandidate` のフィールド構成（`id` / `label` / `username` / `packageName`）を変更しない
7. The Dataset presentation shall RemoteViews に new attributes（カスタム ViewGroup / カスタム attribute）を追加しないことで、Android Autofill framework の `RemoteViews` バリデーション制約を満たす
8. When 既存の単体テスト (`FillResponseBuilderTest` / `LockedFillResponseSecurityTest`) を本 Issue の変更後に実行したとき, the Android test runner shall 当該テストすべてを成功させる

## Non-Functional Requirements

### NFR 1: ビルドと既存テスト

1. When `./gradlew :app:assembleDebug` を CI またはローカルで実行したとき, the Android build pipeline shall 当該タスクを成功（exit code 0）で終了する
2. When 既存の単体テストスイート (`./gradlew :app:testDebugUnitTest`) を本 Issue の変更後に実行したとき, the Android test runner shall Phase 1 / Phase 2 既存完了分（Issue #28 / #29 / #30 / #31 / #32 / #33）で成功していたテストをすべて成功させる
3. The Dataset presentation shall `RemoteViews` がサポートする View / Attribute の範囲を超える `style` / `?attr/...` / カスタム ViewGroup 参照を含まないことで、Android Autofill framework が `RemoteViews` を inflate する際の `InflateException` を回避する

### NFR 2: ローカライズ

1. The Dataset presentation shall 本 Issue で新規に表示するテキスト（KEYNEST eyebrow（固定文字列のため翻訳不要）/ 「+ 新しいクレデンシャル」相当の「新規作成」ラベル）について、必要な新規キーを英語デフォルト (`values/strings.xml`) と日本語 (`values-ja/strings.xml`) の双方に同一キー集合で追加する
2. The Dataset presentation shall 「KEYNEST」eyebrow テキストはブランド名としてラテン大文字固定で表示し、ロケールに依存しない（翻訳キーを追加しない）か、または翻訳キーを追加する場合でも英語 / 日本語ともに「KEYNEST」値とする
3. The Dataset presentation header signature chip shall 既存 `@string/signature_match` を表示し、本 Issue で当該キーをリネーム・削除しない
4. While 端末のロケールが日本語に設定されている場合, the Dataset presentation shall `values-ja/strings.xml` の翻訳済み文字列で「新規作成」ラベルを表示する
5. While 端末のロケールが日本語以外に設定されている場合, the Dataset presentation shall `values/strings.xml` の英語デフォルト文字列で「新規作成」ラベルを表示する

### NFR 3: Inline Suggestions との視覚整合

1. The Dataset presentation shall Inline Suggestions（Issue #13 で導入される IME suggestion strip 上の chip）と並べて表示される文脈で、ブランド色（`@color/kn_primary`）/ アイコンモチーフ（KeyNest mark）/ タイポグラフィスケール（13sp 中心）が一貫していることを保つ
2. The Dataset presentation shall 本 Issue の popup `RemoteViews` 表現を、Issue #13 で実装される Inline Suggestion の chip ラベル / subtitle と同じ文字列ソース（`AutofillCandidate.label` / `AutofillCandidate.username`）から構築する経路を保つ

### NFR 4: アクセシビリティ

1. The Dataset presentation shall ヘッダーの KeyNest mark / 「署名一致」chip 内の shield icon / Dataset 行末尾の lock icon / 「新規作成」行の plus icon について、装飾的な要素は `android:importantForAccessibility="no"` または空 `contentDescription` を、機能を伝える要素は適切なローカライズ済み文字列を `contentDescription` に設定する
2. The Dataset presentation shall Dataset 1 行（label + username）を TalkBack で「label, username」の順序で読み上げられる構造を保つ
3. The Dataset presentation shall Dataset 行の最小タップ領域として高さ 48dp 以上を維持する（Android Autofill framework 側のタップハンドリングは framework 標準に従う）

## Out of Scope

- Autofill candidate 取得ロジック (`ResolveAutofillCandidatesUseCase` / `CredentialRepository.findByPackage` 経路) の変更
- `KeyNestAutofillService` の `onFillRequest` / `onSaveRequest` の挙動・セキュリティ境界の変更（dataset 構築フロー・locked Dataset / `AutofillUnlockActivity` 連携・`PLACEHOLDER` 注入は本 Issue 対象外）
- `FillResponseBuilder.buildLockedResponse` / `buildLockedDataset` / `buildUnlockedDataset` のロジック変更
- Inline Suggestions API 周り（`DatasetPresentationFactory.buildInline` / `InlinePresentation` / `InlineSuggestionUi`）の変更（Issue #13 の範疇）
- 「KeyNest で新規作成」行押下時の onClick 遷移（`CredentialEditActivity` 等への navigation 配線は本 Issue 対象外。表示のみで押下挙動は実装しない）
- 候補件数 0 件時の専用空状態 UI（候補 0 件時は本 dataset 自体が表示されない既存挙動を踏襲）
- 候補の並び順 / フィルタリング（既存 `ResolveAutofillCandidatesUseCase` の出力順序を踏襲）
- Inline Suggestions 上の chip スタイリング（Issue #13 のスコープ）
- 呼び出し元 host activity の地色や theme を踏まえた自動配色（dataset presentation は KeyNest 側で完結した塗りを保つことのみ規定）
- `AutofillUnlockActivity` 上の UI スタイリング（別 Issue。`ScreenUnlock` モック対応）
- Compose 化（既存 View ベースの `RemoteViews` レイアウト XML 改修にとどめる）
- アニメーション（独自トランジション・モーション設計は本 Issue 対象外）
- 他 Phase 2 画面の整合（`mapping.md` §5 の他行は別 Issue）
- 「業務でよく使う」「履歴」のような Dataset セクション分割（JSX `ScreenDataset` モックは単一フラット行のため対象外）

## Open Questions

- Issue 本文では Dataset 行末尾の lock icon ソースとして「`@drawable/ic_key_filled` 新規」が DoD に明記されているが、現在のリポジトリには `ic_key_24.xml`（outline / stroke 風）が既に存在する。本 Issue で `ic_key_filled` を新規追加するか、既存 `ic_key_24` の 16dp 縮退で代替するかは Architect 判断としてよいか
- Issue 本文では KEYNEST ヘッダー左端の mark について「`@drawable/ic_keynest_mark` 新規 vector」が DoD に明記されているが、現在のリポジトリには `ic_keynest_mark_24.xml`（Issue #33 で追加された 24dp 版）が既に存在する。本 Issue でヘッダー向けの 16dp 版（`ic_keynest_mark_16.xml`）を新規追加するか、既存 `ic_keynest_mark_24` をそのまま 16dp サイズで配置するかは Architect 判断としてよいか
- Dataset 行のアイコンタイル（32dp / r10）の中身（JSX では `IconTile color="#1F6FEB" letter="S"` のように「アプリ名 1 文字 + ブランドカラー radial gradient」）について、RemoteViews 上でアプリ名先頭文字を動的に描画するには `setTextViewText` 経由で別 TextView を仕込む必要がある。本 Issue では「文字なし固定タイル背景 + 中央に鍵 vector」または「アプリ名先頭文字をタイル中央 TextView に動的注入」のどちらを採るかは Architect 判断としてよいか。後者を採る場合は `DatasetPresentationFactory.build` の引数追加が必要となり、Requirement 7.1 の「シグネチャ保持」と矛盾するため、追加が必要なら別 Issue で扱う前提でよいか
- Dataset 行末尾の lock icon は JSX `ScreenDataset` の `DatasetRow.locked` 表示に対応するが、KeyNest の Autofill は常に locked Dataset を返す設計（`FillResponseBuilder.buildLockedResponse` に `setAuthentication` が必ず付く）であり、unlocked 状態の Dataset がそもそも presentation を表示しない（直接認証フローに遷移する）。したがって本 Issue では「常に lock icon を表示する」固定要件としているが、この前提で Issue 本文 DoD と整合しているか
- RemoteViews 上で `@style/Text.KeyNest.*` の `TextAppearance` 参照が解決できる Android API レベルの境界は API 31 (Android 12) 以降であるとの一般的な制約があるが、本プロジェクトの `minSdk` 値と組み合わせた場合に `@style` 参照が動作するか、それともインラインで `textSize` / `textColor` / `textStyle` を個別指定する必要があるかは Architect 判断としてよいか（Requirement 2.7 / 3.10 / 3.14 / 5.7 で「TextAppearance 解決できない場合はインライン指定」と冗長性を残してある）
- 「KeyNest で新規作成」行の押下挙動（`CredentialEditActivity` 等への navigation）は Out of Scope に振っているが、視覚整合の観点で「押下不可な装飾行」のままで Issue 本文 DoD（"末尾「KeyNest で新規作成」行" 相当）を満たしているか、それとも navigation 配線まで本 Issue に含めるべきかは Architect / 人間判断としてよいか。後者を採る場合は別 Issue を起票する想定でよいか
- 「KEYNEST」eyebrow と「+ 新しいクレデンシャル」相当の「新規作成」ラベルについて、`values/strings.xml`（英語）と `values-ja/strings.xml`（日本語）の双方への追加スコープに含めて差し支えないか。あるいは `values-ja` への翻訳追加は別 Issue に切り出すべきか
- 「署名一致」chip の表示状態について、本要件 2.15 では「常に署名一致表示」固定としているが、Issue 本文 DoD「署名 chip」の表示要件が「動的に署名一致 / 不一致を切り替える」想定であった場合、対応する状態切替ロジック（`AutofillCandidate` に署名整合フィールドを追加する等）の実装が必要となる。本 Issue では追加せず「常に表示」固定でよいか
