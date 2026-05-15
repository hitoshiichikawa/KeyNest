# Requirements Document

## Introduction

KeyNest の `AutofillEnableActivity`（`autofill_enable_activity.xml`）は、現状「タイトル + 説明 +
1 つのボタン + 既に有効化済みのテキスト」を縦中央寄せで並べただけの最小レイアウトで、
`design/screens/screens-1.jsx` の `ScreenOnboarding` モックで定義された KeyNest 固有のビジュアル
仕様（3 段進捗ドット・ヒーローイラスト・3 ステップ手順カード・固定下部 CTA + サブアクション・
有効化済みステートの success スタイリング）と乖離している。本 Issue (#31) は Phase 2
(`design/android-assets/mapping.md` §5 の Onboarding 行) の画面として、Phase 1 (#28) で
`app/src/main/res/` に取り込まれた `@color/kn_*` / `@dimen/kn_*` / `@style/Widget.KeyNest.*` /
`@style/Text.KeyNest.*` リソースを既存レイアウトに **貼り込む** ことで JSX モックと視覚的に整合
させる。Issue #12 の OQ-1（案 A: 単一画面 / 案 B: 複数ステップ Activity）は「案 A: 単一画面」で
確定済みであり、進捗ドット 3 段は単一画面内のビジュアル要素として描画する（複数 Activity への
分割は行わない）。既存の Autofill 有効化判定ロジック (`AutofillServiceStatus.isCurrentService`)・
Settings 画面起動 Intent (`Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE`)・
`AutofillEnableActivity.newIntent()` 公開 API はそのまま維持し、本 Issue は UI 表層のみを扱う。

## Requirements

### Requirement 1: 画面ルート・サーフェスの整合

**Objective:** As a エンドユーザー, I want オンボーディング画面の地色・横余白が KeyNest 設計トークンと一致していること, so that 他画面と矛盾しない一体感のあるブランド体験を受け取れる

#### Acceptance Criteria

1. The Onboarding screen shall ルート背景色として Material3 attribute `?attr/colorBackground` を解決する（直接 `#` 指定や Material3 デフォルト解決ではなく、Phase 1 で `kn_bg` を指す形で接続済みの attribute を使用する）
2. The Onboarding screen shall コンテンツ領域の左右パディングに `@dimen/kn_screen_padding_h`（20dp）を適用する
3. The Onboarding screen shall 進捗ドット / ヒーロー / 見出し / 補足文 / 手順カード / 下部 CTA 群の縦間隔を `@dimen/kn_space_*` トークン値（4 / 8 / 12 / 16 / 20 / 24 / 32dp のいずれか）から選択して適用する
4. While 端末のシステム設定がダークモードである場合, the Onboarding screen shall `values-night` の `kn_*` セマンティックトークン上書きで描画され、ライト用の hex 値を直接表示しない

### Requirement 2: 進捗ドット 3 段の視覚仕様

**Objective:** As a エンドユーザー, I want オンボーディングの現在位置が 3 段の進捗ドットで一目で分かること, so that オンボーディングの全体ボリューム感と「いま何段目か」を把握できる

#### Acceptance Criteria

1. The progress dot row shall 3 つのセグメント（高さ 4dp / 角丸 4dp）を横方向に等間隔で並べる
2. The progress dot row shall 現在ステップ（既定: 2 段目）のセグメントを、それ以外のセグメントの 2 倍の幅比率（flex 比 2 : 1）で描画する
3. While 任意のセグメントが「現在ステップ以下（達成済み + 現在）」である場合, the progress dot row shall そのセグメントを `@color/kn_primary` で塗りつぶす
4. While 任意のセグメントが「現在ステップより後（未達）」である場合, the progress dot row shall そのセグメントを `@color/kn_border_strong` で塗りつぶす
5. The progress dot row shall セグメント間ギャップに 6dp 相当（`@dimen/kn_space_*` で最も近い値）を適用する
6. The progress dot row shall 進捗ドット行と直下のヒーロー領域の間に縦 32dp（`@dimen/kn_space_8`）の余白を確保する

### Requirement 3: ヒーローイラストの視覚仕様

**Objective:** As a エンドユーザー, I want オンボーディング上部に KeyNest のヒーローイラストが描画されること, so that 単なるシステム設定画面ではなく KeyNest のブランド世界観に入った実感を得られる

#### Acceptance Criteria

1. The Onboarding screen shall ヒーロー領域に新規 vector drawable `@drawable/ic_onboarding_hero`（JSX `OnboardingIllustration` 相当: 中央のシェルター形・両側のクレデンシャル chip・破線矢印・radial glow）を表示する
2. The Onboarding screen shall ヒーロー drawable の描画サイズを 220dp × 180dp（JSX `width=220 height=180` と一致）相当とし、横方向中央寄せする
3. The Onboarding screen shall ヒーロー drawable 内の塗り色として、KeyNest ブランドカラー（`@color/kn_blue_500` / `@color/kn_blue_600` / `@color/kn_blue_700` / `@color/kn_success` / `@color/kn_white`）と中性カラー（`@color/kn_ink_300` 相当）のみを使用する
4. The Onboarding screen shall ヒーロー領域とその下の見出しテキストの間に縦 28dp 相当（`@dimen/kn_space_*` で最も近い値）の余白を確保する
5. Where 端末のシステム設定がダークモードである場合, the Onboarding screen shall ヒーロー drawable がダーク背景上で破綻しない配色で描画されること（白塗りの単純コピーで読みづらくならない値を選ぶ）

### Requirement 4: 見出し・補足文の視覚仕様

**Objective:** As a エンドユーザー, I want オンボーディングの目的（Android Autofill を KeyNest に切り替える）と補足説明が KeyNest のタイポグラフィスケールで読めること, so that 何をする画面かを 3 秒以内に理解できる

#### Acceptance Criteria

1. The Onboarding screen shall 見出しテキストに `@string/autofill_enable_title`（既存キー、ja: 「Android の Autofill を\nKeyNest に切り替える」）を表示する
2. The Onboarding screen shall 見出しテキストの TextAppearance に `@style/Text.KeyNest.Display`（28sp / weight 800 / letter-spacing -0.02em）を適用する
3. The Onboarding screen shall 見出しテキストの色に `@color/kn_text`（Material3 attribute 経由でも可）を適用する
4. The Onboarding screen shall 補足文テキストに `@string/autofill_enable_description`（既存キー）を表示する
5. The Onboarding screen shall 補足文テキストの TextAppearance に `@style/Text.KeyNest.Body`（14sp / weight 600 相当）または同等の本文ロールを適用する
6. The Onboarding screen shall 補足文テキストの色に `@color/kn_text_2`（Material3 attribute `?attr/colorOnSurfaceVariant` 経由でも可）を適用する
7. The Onboarding screen shall 見出し / 補足文 / 手順カードの間に縦 12dp / 24dp（`@dimen/kn_space_3` / `@dimen/kn_space_6`）相当の余白を JSX モックに従って適用する

### Requirement 5: 3 ステップ手順カードの視覚仕様

**Objective:** As a エンドユーザー, I want 「設定を開く → KeyNest を選択 → 戻ってきたら完了」の 3 ステップが番号付きで明示されること, so that 設定アプリに遷移した後で迷わず操作を完了できる

#### Acceptance Criteria

1. The steps card shall ルートのコンテナを `@style/Widget.KeyNest.Card` または同等の `@color/kn_surface` 背景 + `@color/kn_border` 1dp 外枠 + `@dimen/kn_r_card`（18dp）角丸で描画する
2. The steps card shall 内側パディングに 16dp（`@dimen/kn_space_4`）を適用する
3. The steps card shall 3 ステップを縦方向に並べ、各ステップ行に番号チップ（28dp 円形 / `@color/kn_surface_tint` 背景 / `@color/kn_primary` テキスト色 / 太字数字）+ 見出し + 補足文の 3 要素を含める
4. The steps card shall 各ステップ行の見出しに TextAppearance `@style/Text.KeyNest.Body`（14sp / weight 700 相当）、`@color/kn_text` を適用する
5. The steps card shall 各ステップ行の補足文に TextAppearance `@style/Text.KeyNest.BodyS`（13sp / weight 500 相当）、`@color/kn_text_2` を適用する
6. The steps card shall ステップ間に 1dp の区切り線（`@color/kn_border`）を描画し、最終ステップ行の下端には区切り線を描画しない
7. The steps card shall 各ステップの本文として、ステップ 1: 「『設定を開く』をタップ」+「Android の設定画面に移動します」、ステップ 2: 「『KeyNest』を選択」+「パスワードとアカウントの項目で」、ステップ 3: 「戻ってきたら準備完了」+「クレデンシャルを登録できます」を表示する（JSX `ScreenOnboarding` の steps 配列に一致）
8. The steps card shall 上記 6 つの新規日本語文字列を `values-ja/strings.xml` に、対応する英語訳を `values/strings.xml` に、それぞれ同一キー集合で追加する（Phase 1 NFR 2.1 整合）

### Requirement 6: 固定下部 CTA（プライマリ + サブアクション）の視覚仕様

**Objective:** As a エンドユーザー, I want 「設定を開く」と「あとで」のアクションが画面下部に固定表示され、スクロール位置に依存せず常に押せること, so that 長い手順説明を読み終えなくても直ちに次の操作に移れる

#### Acceptance Criteria

1. The bottom action area shall プライマリ CTA ボタン（`@style/Widget.KeyNest.Button.Primary`）と「あとで」サブアクションボタン（`@style/Widget.KeyNest.Button.Text` または同等の text-only スタイル）の 2 つを縦に並べる
2. The bottom action area shall 画面下端から 24dp（`@dimen/kn_space_6`）、左右端から 20dp（`@dimen/kn_screen_padding_h`）の余白を保ち、スクロール領域とは独立した固定領域として表示する
3. The bottom action area shall プライマリ CTA を横幅 `match_parent`、高さ `@dimen/kn_button_height`（52dp）相当で描画する
4. The bottom action area shall サブアクション「あとで」ボタンを横幅 `match_parent`、高さ 44dp 相当、テキスト色 `@color/kn_primary` で描画する
5. The bottom action area shall プライマリ CTA とサブアクションの間に 6dp 相当（`@dimen/kn_space_*` で最も近い値）の縦余白を適用する
6. The bottom action area shall プライマリ CTA のラベルとして `@string/autofill_enable_action`（既存キー、ja: 「設定を開く」）を表示する
7. The bottom action area shall サブアクション「あとで」ボタンのラベルとして新規文字列リソース（ja: 「あとで」、en: 同義の英語訳）を表示する
8. When ユーザーがプライマリ CTA を押下したとき, the Onboarding screen shall 既存の `launchSettings()` 経路（API 26 以上では `Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE` Intent、API 26 未満では `@string/autofill_enable_settings_unavailable` の Snackbar）を呼び出す
9. When ユーザーがサブアクション「あとで」ボタンを押下したとき, the Onboarding screen shall 当該 Activity を `finish()` で閉じ、呼び出し元（既存 `CredentialListActivity` フロー）に戻る

### Requirement 7: 「既に有効化済み」ステートの視覚仕様

**Objective:** As a 既に有効化済みのユーザー, I want オンボーディング画面を再訪した際に「KeyNest が Autofill サービスとして有効」であることが視覚的に明示されること, so that 同じ手順を再実行する必要がないことを直ちに理解できる

#### Acceptance Criteria

1. While `AutofillServiceStatus.isCurrentService()` が true を返している場合, the Onboarding screen shall プライマリ CTA とサブアクション「あとで」ボタンの両方を非表示にする
2. While `AutofillServiceStatus.isCurrentService()` が true を返している場合, the Onboarding screen shall 「既に有効化済み」ステート用の表示要素を表示し、`@string/autofill_enable_already_enabled`（既存キー、ja: 「KeyNest は既に Autofill サービスとして有効です。」）を本文として表示する
3. The already-enabled state view shall 表示要素の左側または上部に成功アイコン（`@color/kn_success` で tint された shield / checkmark 系 vector drawable）を配置し、テキスト色には `@color/kn_success` または `@color/kn_text` を適用する
4. The already-enabled state view shall 背景に `@color/kn_success_soft` を `@dimen/kn_r_md`（16dp）または `@dimen/kn_r_card`（18dp）相当の角丸コンテナで適用する
5. While `AutofillServiceStatus.isCurrentService()` が false を返している場合, the Onboarding screen shall 「既に有効化済み」ステート用の表示要素を非表示にし、プライマリ CTA とサブアクション「あとで」ボタンを表示する
6. When ユーザーが Settings 画面から戻ってきた（`onResume()` が再実行された）とき, the Onboarding screen shall 既存どおり `AutofillServiceStatus.isCurrentService()` を再評価し、結果に応じて Req 7.1 / 7.2 / 7.5 の visibility 切替を反映する

### Requirement 8: ライト / ダーク両モードの描画整合

**Objective:** As a エンドユーザー, I want システムテーマ（Light / Dark）の切り替え後も配色が破綻せず読みやすいこと, so that 端末設定を変えても KeyNest の体感品質が落ちない

#### Acceptance Criteria

1. While 端末のシステム設定がライトモードである場合, the Onboarding screen shall `values/colors.xml` の `kn_*` セマンティックトークンを解決する
2. While 端末のシステム設定がダークモードである場合, the Onboarding screen shall `values-night/colors.xml` の `kn_*` セマンティックトークンを解決する
3. The Onboarding screen shall ライト / ダーク両モードで見出し / 本文テキスト（`@color/kn_text` / `@color/kn_text_2`）と背景（`@color/kn_bg` / `@color/kn_surface`）のコントラスト比が WCAG 2.1 AA の本文最小 4.5:1 以上となるトークン値を使用する
4. The Onboarding screen shall ライト / ダーク両モードでカード outline・進捗ドット未達セグメント・番号チップ等の非文字要素と背景のコントラスト比が WCAG 2.1 AA の非文字最小 3:1 以上となるトークン値を使用する（Phase 1 NFR 1.2 で確認事項に挙げられた `kn_border_strong` 単独のコントラスト 1.4:1 は強調 outline 用途では使わない方針を踏襲する）
5. While 端末のシステム設定がダークモードである場合, the already-enabled state view shall `@color/kn_success` / `@color/kn_success_soft` 由来の配色が dark mode のサーフェス上で十分なコントラスト（本文 4.5:1 以上）を保てる値で描画する

### Requirement 9: 既存機能・配線の不変

**Objective:** As a 既存ユーザー / メンテナ, I want UI の見た目変更によって Autofill 有効化の判定・遷移・呼び出し元配線が壊れないこと, so that 本 Issue のリリース前後で機能差分を意識せずに利用できる

#### Acceptance Criteria

1. The Onboarding screen shall 既存の `AutofillEnableActivity.newIntent(context)` 公開 API シグネチャを保持し、`CredentialListActivity` からの 2 箇所の起動経路を変更しない
2. The Onboarding screen shall 既存の `AutofillServiceStatus.isCurrentService(this)` 判定経路を変更せず、`onResume()` で再評価する挙動を保持する
3. The Onboarding screen shall 既存の `AndroidManifest.xml` の `<activity android:name=".ui.enable.AutofillEnableActivity" android:exported="false" android:label="@string/autofill_enable_title">` 宣言（テーマ・exported 属性）を変更しない
4. The Onboarding screen shall 既存の文字列リソースキー（`autofill_enable_title` / `autofill_enable_description` / `autofill_enable_action` / `autofill_enable_already_enabled` / `autofill_enable_settings_unavailable`）を本 Issue で削除・リネームしない
5. If `Build.VERSION.SDK_INT` が API 26 未満である場合, the Onboarding screen shall 既存どおり `@string/autofill_enable_settings_unavailable` の Snackbar を表示する
6. If `Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE` Intent が `ActivityNotFoundException` で失敗した場合, the Onboarding screen shall 既存どおり `@string/autofill_enable_settings_unavailable` の Snackbar を表示する

## Non-Functional Requirements

### NFR 1: ビルドと既存テスト

1. When `./gradlew :app:assembleDebug` を CI またはローカルで実行したとき, the Android build pipeline shall 当該タスクを成功（exit code 0）で終了する
2. When 既存の単体テストスイート (`./gradlew :app:testDebugUnitTest`) を本 Issue の変更後に実行したとき, the Android test runner shall Phase 1 完了時点で成功していたテスト（Phase 1 `impl-notes.md` の 279 件中 274 件成功分）をすべて成功させる
3. The Onboarding screen shall 既存の `Material3ThemeMigrationTest` 8 件および `FontTypefaceWiringTest` 9 件の `Theme.KeyNest` / `TextAppearance.KeyNest.*` の structural pin に違反する変更を行わない

### NFR 2: アクセシビリティ

1. The Onboarding screen shall すべてのタップ可能要素（プライマリ CTA / サブアクション「あとで」ボタン）について最小タッチサイズ 48dp × 48dp（`spec.md` §10 の 44dp 視覚 + 4dp 余白規定を満たす dp）を維持する
2. The Onboarding screen shall ヒーローイラスト（装飾要素）について TalkBack 用 `contentDescription` を空文字または `android:importantForAccessibility="no"` で抑制し、見出し / 補足文 / 手順カードの読み上げ順序を妨げない
3. The Onboarding screen shall 進捗ドット行・番号チップ（装飾要素）について、装飾的意味のみを担う場合は `contentDescription` を抑制し、読み上げ順序の冗長性を避ける
4. The Onboarding screen shall ライト / ダーク両モードで本文最小 4.5:1 / 非文字最小 3:1 のコントラスト基準を満たすトークン値を使用する（Req 8.3 / 8.4 の再掲）

### NFR 3: ローカライズ

1. The Onboarding screen shall 本 Issue で新規に表示するテキスト（3 ステップ手順カードの 6 文字列、サブアクション「あとで」、ヒーロー contentDescription を設定する場合はそのテキスト）について、英語デフォルト (`values/strings.xml`) と日本語 (`values-ja/strings.xml`) の双方に同一キー集合で追加する（Phase 1 確認事項 1 の方針に準拠）
2. While 端末のロケールが日本語に設定されている場合, the Onboarding screen shall `values-ja/strings.xml` の翻訳済み文字列で全要素を表示する
3. While 端末のロケールが日本語以外に設定されている場合, the Onboarding screen shall `values/strings.xml` の英語デフォルト文字列で全要素を表示する

## Out of Scope

- 複数ステップ Activity への分割（Issue #12 OQ-1 = 案 A 単一画面 で確定済み。本 Issue では単一 Activity 内のビジュアル要素として進捗ドットを描画するに留める）
- Autofill サービスの有効化判定ロジック自体の変更（`AutofillServiceStatus.isCurrentService()` の実装は本 Issue で書き換えない）
- Settings 画面起動 Intent の変更（`Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE` + `package:` URI の組み立ては本 Issue で変更しない）
- 「既に有効化済み」状態における自動 close / リダイレクト（現状は表示するのみで本 Issue でも同じ）
- 進捗ドットの「現在ステップ」を動的に変更するロジック（本 Issue では JSX に従い既定 = 2 段目固定の表示。将来 Welcome / クレデンシャル登録ステップとの連動は別 Issue）
- ヒーローイラスト（`ic_onboarding_hero.xml`）のアニメーション化（静的 vector drawable に留める）
- Compose 化（既存 View ベースのレイアウト XML 改修にとどめる）
- `Material3ThemeMigrationTest` の structural pin 緩和（Phase 1 確認事項 2 として別 Issue 候補）
- `dataset_presentation.xml` / `package_picker_bottom_sheet.xml` / `credential_edit_activity.xml` / 設定画面等、他 Phase 2 画面の整合（`mapping.md` §5 の他行は別 Issue）
- 呼び出し元 (`CredentialListActivity`) からの Onboarding 起動条件の変更（既存 2 経路のまま維持）
- サブアクション「あとで」押下後に「次回起動時には表示しない」等の永続化（本 Issue では `finish()` のみ）

## Open Questions

- 進捗ドット 3 段の「現在ステップ」初期値を JSX モックと同じ `step = 2` で固定するか、Autofill 未有効化時は `step = 2` / 有効化済み時は `step = 3` のように動的にするか。本 requirements は Req 2.2 で「既定 = 2 段目」と固定値で記載しているが、有効化済み時の挙動（Req 7 の「既に有効化済み」ステート）と整合させる場合は Architect 判断としてよいか（要件としては「ステート切替時に進捗ドットの表示自体を非表示にする」「step を 3 に進める」「step = 2 のまま固定する」のいずれも許容するが、Architect は 1 つを選択して design.md に明記すること）
- ヒーロー drawable `ic_onboarding_hero.xml` を Phase 1 で導入されたアダプティブランチャー drawable (`ic_launcher_foreground.xml` 等) のいずれかから派生・流用するか、Issue 本文の DoD どおり完全新規の vector drawable として起こすか。Issue 本文は「Hero section with SVG/vector drawable (`drawable/ic_onboarding_hero.xml`)」と明示しているため新規ファイル作成を採用しているが、JSX `OnboardingIllustration` の SVG パスをそのまま vector drawable に変換すれば良い前提でよいか
- サブアクション「あとで」ボタンの新規文字列キー命名（`autofill_enable_action_later` / `autofill_enable_postpone` 等）と英語訳（"Not now" / "Later" / "Skip"）の確定。本 requirements は Req 6.7 / NFR 3.1 で「新規文字列リソース」とだけ規定し、キー名と訳語を Architect / Developer 判断に委ねている
- 3 ステップ手順カードの 6 文字列（ステップ 1〜3 の見出し + 補足文）に対する英語訳の確定。本 requirements は Req 5.7 で日本語訳を JSX に従って固定しているが、英語訳は Architect / Developer 判断に委ねている
