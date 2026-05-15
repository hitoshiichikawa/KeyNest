# Requirements Document

## Introduction

KeyNest の `SettingsActivity`（`app/src/main/res/layout/settings_activity.xml`）および
関連子画面（`DangerZoneActivity` / `OssLicensesActivity`）は、Issue #10 / PR #21 で
`Widget.Material3.CardView.Outlined` / `Widget.Material3.Button.TonalButton` /
`?attr/colorErrorContainer` 等の Material 3 デフォルトトークンを直接参照する形で実装され、
`design/screens/screens-2.jsx` の `ScreenSettings` モックで定義された KeyNest 固有のビジュアル
仕様（プライマリブルーグラデーションの Autofill ステータス hero、Eyebrow 見出し +
`@color/kn_surface` 背景 + `@color/kn_border` 外枠の SettingGroup、アイコンタイル + label +
sub + 右寄せ chevron/right-value の SettingRow、`@color/kn_danger_soft` 背景の Danger zone）と
乖離している。本 Issue (#33) は Phase 2（`design/android-assets/mapping.md` §5 の
`ScreenSettings` 行）の 5 件目として、Phase 1 (#28) で `app/src/main/res/` に取り込まれた
`@color/kn_*` / `@dimen/kn_*` / `@style/Widget.KeyNest.*` / `@style/Text.KeyNest.*` リソースを
既存レイアウトに **貼り込む** ことで JSX モックと視覚的に整合させる。Autofill 有効化判定
(`SettingsViewModel.uiState`)・Vault メタデータ取得・OSS ライセンス表示・Vault クリア機能の
ビジネスロジック・遷移先 Intent（`SystemSettingsIntents.openAutofillServiceChooser` /
`openSecuritySettings` / `OssLicensesActivity.newIntent` / `DangerZoneActivity.newIntent`）・
公開 API（`SettingsActivity.newIntent(context)`）・既存 View ID（`btn_open_autofill_settings`
/ `btn_open_security_settings` / `btn_open_danger_zone` / `btn_oss_licenses` /
`text_autofill_status` / `text_lock_status` / `text_vault_count` / `text_vault_latest_updated`
/ `text_vault_storage` / `text_app_version`）は Issue #10 で実装済みであり、本 Issue は UI 表層
のみを扱う。ロック方式の動的切替 UI は Issue #10 のスコープ判断で「Android 設定にディープリンク」
（`openSecuritySettings()`）に倒している既存方針を踏襲する。

## Requirements

### Requirement 1: 画面ルート・サーフェスの整合

**Objective:** As a エンドユーザー, I want 設定画面の地色・横余白が KeyNest 設計トークンと一致していること, so that 他画面（クレデンシャル一覧 / 編集 / オンボーディング / Picker）と矛盾しないブランド体験で操作できる

#### Acceptance Criteria

1. The Settings screen shall ルート背景色として Material3 attribute `?attr/colorBackground`（Phase 1 で `@color/kn_bg` に接続済み）を解決する
2. The Settings screen shall コンテンツ領域の左右パディングに `@dimen/kn_screen_padding_h`（20dp）を適用する
3. The Settings screen shall AppBar 領域に既存 `MaterialToolbar`（タイトル `@string/settings_title` / ナビゲーション戻る icon）を保持する
4. The Settings screen shall AppBar タイトルの TextAppearance に `@style/Text.KeyNest.TitleS`（16sp / weight 700）相当を適用する
5. The Settings screen shall hero と各 SettingGroup の縦間隔を `@dimen/kn_space_*` トークン値（`kn_space_5`=20dp / `kn_space_6`=24dp / `kn_section_gap`=24dp のいずれか）から選択して適用する

### Requirement 2: Autofill ステータス hero

**Objective:** As a エンドユーザー, I want 画面上部に Autofill サービスの有効/無効が一目で判別できる視覚的にリッチな hero カードが表示されること, so that 設定画面を開いた瞬間に KeyNest が Android の Autofill として動作しているかを把握できる

#### Acceptance Criteria

1. The Settings screen shall AppBar 直下に Autofill ステータス hero を 1 つ配置する
2. The Autofill status hero shall 角丸として `@dimen/kn_r_lg`（20dp）相当を適用する
3. The Autofill status hero shall 内側パディングに `@dimen/kn_card_padding_lg`（18dp）相当を適用する
4. While `SettingsViewModel.uiState.autofillStatus` が `AutofillStatus.Enabled` を返している場合, the Autofill status hero shall 背景に `@color/kn_primary` から `@color/kn_blue_700` への斜め方向グラデーション（JSX `linear-gradient(140deg, var(--primary) 0%, var(--kn-blue-700) 100%)` 相当）を適用する
5. While `SettingsViewModel.uiState.autofillStatus` が `AutofillStatus.Enabled` を返している場合, the Autofill status hero shall タイトル `Autofill サービス` / 補足文 `KeyNest が Android のパスワード Autofill として動作しています。` 相当を `@color/kn_on_primary`（白）で描画する
6. While `SettingsViewModel.uiState.autofillStatus` が `AutofillStatus.Enabled` を返している場合, the Autofill status hero shall 「有効」を示すステータスチップを左上に表示し、チップ背景に半透明白（rgba(255,255,255,.20) 相当）、チップ文字色に `@color/kn_on_primary` を適用する
7. While `SettingsViewModel.uiState.autofillStatus` が `AutofillStatus.NotEnabled` を返している場合, the Autofill status hero shall 背景に `@color/kn_warning_soft`、タイトル / 補足文の文字色に `@color/kn_text`、ステータスチップに `@color/kn_warning` accent を適用する
8. The Autofill status hero shall 既存の文字列リソース `@string/settings_autofill_badge_enabled` / `@string/settings_autofill_badge_not_enabled` をステータスチップのラベルとして表示する
9. The Autofill status hero shall タイトル文字列の TextAppearance に `@style/Text.KeyNest.TitleM`（19sp / weight 800）相当を適用する
10. The Autofill status hero shall 補足文の TextAppearance に `@style/Text.KeyNest.BodyS`（13sp / weight 500）または `@style/Text.KeyNest.Caption`（12sp / weight 500）相当を適用する
11. The Autofill status hero shall 「Android 設定で確認」相当のセカンダリ CTA ボタン（既存 ID `btn_open_autofill_settings`）を hero 内に保持する
12. The Autofill status hero shall セカンダリ CTA ボタンを hero 内では半透明アウトライン（背景 rgba(255,255,255,.18) / 1dp ストローク rgba(255,255,255,.30) 相当）+ `@color/kn_on_primary` 文字色で描画する（Enabled 時のみ。NotEnabled 時は本要件 2.7 の `kn_warning_soft` 背景上で可読となるトークンを適用する）
13. When ユーザーが Autofill 設定画面 CTA を押下したとき, the Settings screen shall 既存の `SystemSettingsIntents.openAutofillServiceChooser(this)` 経路を呼び出す
14. The Autofill status hero shall ステータスチップ / タイトル / 補足文の TalkBack 読み上げ順序を「ステータス → タイトル → 補足文 → CTA」とし、`accessibilityHeading="true"` を hero タイトルに付与する

### Requirement 3: SettingGroup（共通コンテナ）の視覚仕様

**Objective:** As a エンドユーザー, I want セキュリティ / Vault / About / Danger zone の各グループが共通のカード形式で並び、KeyNest らしい一体感のあるリストとして読めること, so that グループの区切りと内部の行構造を視覚的に即座に理解できる

#### Acceptance Criteria

1. The SettingGroup shall ルートコンテナ背景色に `@color/kn_surface` を適用する
2. The SettingGroup shall ルートコンテナ角丸として `@dimen/kn_r_md`（16dp）相当を適用する
3. The SettingGroup shall ルートコンテナの 1dp 外枠に `@color/kn_border` を適用する
4. The SettingGroup shall グループ上部に Eyebrow ヘッダー（11sp / weight 700 / letter-spacing 0.08em / UPPERCASE）を 1 つ配置する
5. The SettingGroup eyebrow header shall TextAppearance に `@style/Text.KeyNest.Eyebrow` を適用する
6. The SettingGroup eyebrow header shall テキスト色に `@color/kn_text_3` を適用する
7. The SettingGroup eyebrow header shall ヘッダーとカードコンテナの間に縦 8dp（`@dimen/kn_space_2`）の余白を適用する
8. The SettingGroup eyebrow header shall ヘッダーの左パディングに 4dp 相当を適用する（JSX `padding: '0 4px 8px'` 相当）
9. The SettingGroup eyebrow header shall `accessibilityHeading="true"` を付与し、TalkBack で section heading として読み上げられること
10. The SettingGroup shall グループ間の縦余白に 24dp（`@dimen/kn_space_6` または `@dimen/kn_section_gap`）を適用する

### Requirement 4: SettingRow（共通行）の視覚仕様

**Objective:** As a エンドユーザー, I want 各設定行が「leading アイコンタイル + label + sub + 右側エリア（chevron / right-value）」の統一フォーマットで描画されること, so that 行の意味（情報表示か遷移かトグルか）を視覚的に判別でき、誤タップを防げる

#### Acceptance Criteria

1. The SettingRow shall 左端に 32dp × 32dp（`@dimen/kn_icon_tile_sm`）相当のアイコンタイルを配置する
2. The SettingRow icon tile shall 角丸として `@dimen/kn_r_sm`（12dp）相当（JSX `borderRadius: 10` に最も近い KeyNest トークン）を適用する
3. The SettingRow icon tile shall 背景色に `@color/kn_surface_2` を適用する
4. The SettingRow icon tile shall 内側 leading アイコンの tint に `@color/kn_text_2` を適用する（Danger 行の場合は本要件 4.13 を優先）
5. The SettingRow shall アイコンタイル右側に label 行と sub 行（任意）の 2 段テキストを表示する
6. The SettingRow label shall TextAppearance に `@style/Text.KeyNest.Body`（14sp / weight 600〜700）相当を適用する
7. The SettingRow label shall テキスト色に `@color/kn_text` を適用する（Danger 行の場合は本要件 4.13 を優先）
8. The SettingRow sub shall TextAppearance に `@style/Text.KeyNest.Caption`（12sp / weight 500）または `@style/Text.KeyNest.BodyS`（13sp / weight 500）相当を適用する
9. The SettingRow sub shall テキスト色に `@color/kn_text_2` を適用する
10. The SettingRow shall 右端エリアに「chevron 用 ImageView」または「right-value 用 TextView」のいずれかを表示する（toggle の必要性は本 Issue のスコープ外、本要件 4.16 で明示的に除外する）
11. The SettingRow right-value text shall TextAppearance に `@style/Text.KeyNest.BodyS`（13sp / weight 500〜600）相当を適用し、テキスト色に `@color/kn_text_2` を適用する
12. The SettingRow chevron shall tint に `@color/kn_text_3` を適用する
13. Where 行が destructive（Danger zone 内の行など）である場合, the SettingRow shall label テキスト色および leading アイコン tint に `@color/kn_danger` を適用する
14. The SettingRow shall 行内パディングに「上下 `@dimen/kn_space_3`（12dp）/ 左右 14dp（`@dimen/kn_space_3` と `@dimen/kn_space_4` の中間）」を適用する（JSX `padding: '12px 14px'` 相当）
15. The SettingRow shall アイコンタイルと label テキストの間の横ギャップを `@dimen/kn_space_3`（12dp）相当で適用する
16. The SettingRow shall 最小タッチサイズ 48dp × 48dp（`@dimen/kn_iconbtn_touch` = 44dp 視覚 + 4dp 余白 = 48dp）を維持する
17. The SettingGroup shall 行間に 1dp の区切り線（`@color/kn_border`）を描画し、最終行の下端には区切り線を描画しない

### Requirement 5: セキュリティ グループ

**Objective:** As a エンドユーザー, I want ロック解除方法（生体認証ステータス）が読み取り専用で表示されること, so that 現在の Vault ロック構成を確認でき、必要なら Android 設定にディープリンクで遷移できる

#### Acceptance Criteria

1. The Settings screen shall Autofill ステータス hero の下に「セキュリティ」グループを 1 つ配置する
2. The Settings screen shall 「セキュリティ」グループの Eyebrow ヘッダー文字列として既存 `@string/settings_section_security_title` を表示する
3. The Settings screen shall 「セキュリティ」グループ内に「ロック解除方法」行を 1 つ配置する
4. The "ロック解除方法" row shall label に「ロック解除方法」相当の新規ローカライズ済み文字列を表示する
5. The "ロック解除方法" row shall sub に既存 `@string/settings_lock_status_biometric_and_device` / `_device_only` / `_none` / `_update_required` のうち `SettingsViewModel.uiState.lockStatus` から導出される 1 件を表示する
6. The "ロック解除方法" row shall leading アイコンとして指紋系 vector drawable（JSX `Icons.fingerprint` 相当）を表示する
7. The "ロック解除方法" row shall 右端に chevron を表示する（行押下で Android 設定にディープリンクするため）
8. When ユーザーが「ロック解除方法」行を押下したとき, the Settings screen shall 既存の `SystemSettingsIntents.openSecuritySettings(this)` 経路を呼び出す
9. The Settings screen shall 既存 View ID `btn_open_security_settings` の参照経路を保持する（Material3 ボタンを SettingRow 化する場合でも、既存 `SettingsActivityTest` が `withId(R.id.btn_open_security_settings)` で押下できる ID を行ルートに割り当てる）
10. The Settings screen shall 既存 View ID `text_lock_status` の参照経路を保持する（行内の sub TextView に当該 ID を割り当てる）
11. If `Intent` 解決に失敗した場合, the Settings screen shall 既存どおり `@string/settings_intent_unavailable` の Snackbar を表示する

### Requirement 6: Vault グループ

**Objective:** As a エンドユーザー, I want 登録件数 / 最終更新時刻 / DB サイズが一目で確認できること, so that 自分の Vault のボリュームと最終更新タイミングを把握できる

#### Acceptance Criteria

1. The Settings screen shall 「セキュリティ」グループの下に「Vault」グループを 1 つ配置する
2. The Settings screen shall 「Vault」グループの Eyebrow ヘッダー文字列として既存 `@string/settings_section_vault_title` を表示する
3. The Settings screen shall 「Vault」グループ内に「クレデンシャル数」「最終更新」「DB サイズ」の 3 行を上から順に配置する
4. The "クレデンシャル数" row shall label に既存 `@string/settings_vault_count_label`、右端 right-value TextView（既存 ID `text_vault_count`）に `SettingsViewModel.uiState.metadata.count` を `@string/settings_vault_count_format` で整形した値を表示する
5. The "クレデンシャル数" row shall leading アイコンとして鍵系 vector drawable（JSX `Icons.key` 相当）を表示する
6. The "最終更新" row shall label に既存 `@string/settings_vault_latest_updated_label`、右端 right-value TextView（既存 ID `text_vault_latest_updated`）に既存 `AdvancedDetailsFormatter.formatTimestamp` 経由の値、または値が null の場合は既存 `@string/settings_vault_latest_updated_empty`（"—"）を表示する
7. The "最終更新" row shall leading アイコンとして時計系 vector drawable（JSX `Icons.clock` 相当）を表示する
8. The "DB サイズ" row shall label に既存 `@string/settings_vault_storage_label`、右端 right-value TextView（既存 ID `text_vault_storage`）に既存 `android.text.format.Formatter.formatShortFileSize` 経由の値を表示する
9. The "DB サイズ" row shall leading アイコンとしてストレージ / ファイル系 vector drawable を表示する
10. The Settings screen shall 「Vault」グループ 3 行いずれも chevron は表示せず right-value のみとする（情報表示型行）

### Requirement 7: About グループ

**Objective:** As a エンドユーザー, I want バージョン / OSS ライセンス / プライバシーポリシーへの導線が読みやすい形で並んでいること, so that アプリのアイデンティティとコンプライアンス情報に素早くアクセスできる

#### Acceptance Criteria

1. The Settings screen shall 「Vault」グループの下に「About」グループを 1 つ配置する
2. The Settings screen shall 「About」グループの Eyebrow ヘッダー文字列として既存 `@string/settings_section_about_title` を表示する
3. The Settings screen shall 「About」グループ内に「KeyNest（バージョン表示）」「OSS ライセンス」「プライバシー」の 3 行を上から順に配置する
4. The "KeyNest" row shall label に「KeyNest」相当の新規ローカライズ済み文字列を表示する
5. The "KeyNest" row shall sub にバージョン情報（既存 `@string/settings_about_version_format` で整形した `versionName (versionCode)`）を表示する
6. The "KeyNest" row shall 既存 View ID `text_app_version` の参照経路を保持する（行内の sub TextView に当該 ID を割り当てる）
7. The "KeyNest" row shall leading アイコンとして KeyNest mark vector drawable（JSX `<KNMark size={20}/>` 相当）を表示する
8. The "KeyNest" row shall chevron も right-value も表示しない（情報表示のみで遷移しないため）
9. The "OSS ライセンス" row shall label に既存 `@string/settings_about_oss_licenses_label` を表示する
10. The "OSS ライセンス" row shall leading アイコンとしてドキュメント系 vector drawable を表示する
11. The "OSS ライセンス" row shall 右端に chevron を表示する
12. When ユーザーが「OSS ライセンス」行を押下したとき, the Settings screen shall 既存どおり `OssLicensesActivity.newIntent(this)` で `OssLicensesActivity` を起動する
13. The Settings screen shall 既存 View ID `btn_oss_licenses` の参照経路を保持する
14. The "プライバシー" row shall label に「プライバシー」相当、sub に「ネットワーク送信なし · Keystore 保護」相当の新規ローカライズ済み文字列を表示する
15. The "プライバシー" row shall leading アイコンとして shield 系 vector drawable（JSX `Icons.shield` 相当）を表示する
16. The "プライバシー" row shall chevron も right-value も表示しない（説明文の情報表示のみ）か、もしくは将来のプライバシーポリシー画面 / 外部 URL 遷移用に chevron を表示する（本 Issue のスコープでは押下しても遷移を発生させず、装飾のみとする）

### Requirement 8: Danger zone グループ

**Objective:** As a エンドユーザー, I want 「すべて削除」等の破壊的アクションへの導線が画面下部に視覚的に区別された destructive スタイルで配置されていること, so that 通常操作と破壊的操作を取り違えるリスクを下げられる

#### Acceptance Criteria

1. The Settings screen shall 「About」グループの下（セクション末尾）に「Danger zone」グループを 1 つ配置する
2. The Settings screen shall 「Danger zone」グループの Eyebrow ヘッダー文字列として既存 `@string/settings_section_danger_title` を表示する
3. The "Danger zone" SettingGroup shall ルートコンテナ背景色に `@color/kn_danger_soft` を適用する（標準 SettingGroup の `kn_surface` を上書きする destructive 派生）
4. The "Danger zone" SettingGroup shall ルートコンテナ角丸として `@dimen/kn_r_md`（16dp）相当を適用する
5. The "Danger zone" SettingGroup shall グループ内に「すべて削除」行を 1 つ配置する
6. The "すべて削除" row shall Requirement 4 の destructive 派生（4.13）を適用する（label テキスト色 `@color/kn_danger` / leading アイコン tint `@color/kn_danger`）
7. The "すべて削除" row shall label に既存 `@string/settings_danger_open_action` または「すべて削除」相当の新規ローカライズ済み文字列を表示する
8. The "すべて削除" row shall sub に「復元できません」相当の新規ローカライズ済み文字列を表示する
9. The "すべて削除" row shall leading アイコンとして警告系 vector drawable（JSX `Icons.warning` 相当）を表示する
10. When ユーザーが「すべて削除」行を押下したとき, the Settings screen shall 既存どおり `DangerZoneActivity.newIntent(this)` で `DangerZoneActivity` を起動する
11. The Settings screen shall 既存 View ID `btn_open_danger_zone` の参照経路を保持する

### Requirement 9: DangerZoneActivity 内部の視覚整合

**Objective:** As a エンドユーザー, I want Danger zone 子画面に遷移した後も KeyNest 設計トークンと一致した destructive スタイリングが続いていること, so that 設定画面から遷移してきても視覚的な連続性が損なわれない

#### Acceptance Criteria

1. The DangerZone screen shall ルートコンテナの destructive カード背景色に `@color/kn_danger_soft` を適用する（`?attr/colorErrorContainer` 直参照から KeyNest セマンティックトークンに置換）
2. The DangerZone screen shall destructive カードの角丸として `@dimen/kn_r_md`（16dp）または `@dimen/kn_r_card`（18dp）相当を適用する
3. The DangerZone screen shall destructive カード内の見出し / 説明文の文字色に `@color/kn_danger` または `@color/kn_text` を適用する（`?attr/colorOnErrorContainer` 直参照から KeyNest セマンティックトークンに置換）
4. The DangerZone screen shall 「削除」プライマリ CTA を `@style/Widget.KeyNest.Button.Destructive`（Phase 1 で Issue #30 に向け定義済み）または `@color/kn_danger` 背景 + `@color/kn_on_primary`（白）文字色の destructive 派生スタイルで描画する
5. The DangerZone screen shall 「Retry」サブアクションボタンを `@style/Widget.KeyNest.Button.Text` 相当（テキスト文字色 `@color/kn_danger` または `@color/kn_text_2`）で描画する
6. The DangerZone screen shall 既存 View ID（`btn_clear` / `btn_retry` / `group_progress` / `text_section_heading` / `text_description`）の参照経路を保持する
7. The DangerZone screen shall 既存の Vault クリアロジック（生体認証プロンプト / `ClearVaultUseCase` 呼び出し / progress / retry / Snackbar 表示）を変更しない

### Requirement 10: OssLicensesActivity 内部の視覚整合

**Objective:** As a エンドユーザー, I want OSS ライセンス子画面のリストカードが KeyNest カード仕様と一致していること, so that 親画面（SettingsActivity）と子画面の間で視覚スタイルが破綻しない

#### Acceptance Criteria

1. The OSS licenses screen shall 各ライブラリ行のカードスタイルを `@style/Widget.KeyNest.Card`（Phase 1 で定義済み: `@color/kn_surface` 背景 / `@color/kn_border` 1dp 外枠 / `@dimen/kn_r_card` 角丸）に置換する
2. The OSS licenses screen shall 行内のライブラリ名 TextView の TextAppearance に `@style/Text.KeyNest.Body`（14sp / weight 600）または `@style/Text.KeyNest.TitleS`（16sp / weight 700）相当を適用する
3. The OSS licenses screen shall 行内のライセンス種別 / 本文 TextView の TextAppearance に `@style/Text.KeyNest.BodyS` または `@style/Text.KeyNest.Caption` 相当を適用し、テキスト色に `@color/kn_text_2` を適用する
4. The OSS licenses screen shall 行内の URL ボタンを `@style/Widget.KeyNest.Button.Text` 相当（テキスト文字色 `@color/kn_primary`）に置換する
5. The OSS licenses screen shall 既存 View ID（`text_name` / `text_license` / `btn_url` / `text_body`）の参照経路を保持する
6. The OSS licenses screen shall 既存の OSS ライセンス取得・展開ロジックを変更しない

### Requirement 11: ライト / ダーク両モードの描画整合

**Objective:** As a エンドユーザー, I want システムテーマ（Light / Dark）の切り替え後も配色が破綻せず読みやすいこと, so that 端末設定を変えても KeyNest の体感品質が落ちない

#### Acceptance Criteria

1. While 端末のシステム設定がライトモードである場合, the Settings screen shall `values/colors.xml` の `kn_*` セマンティックトークンを解決する
2. While 端末のシステム設定がダークモードである場合, the Settings screen shall `values-night/colors.xml` の `kn_*` セマンティックトークンを解決する
3. The Settings screen shall ライト / ダーク両モードで本文テキスト（hero タイトル / 補足文 / 各 SettingRow の label / sub / right-value / Eyebrow ヘッダー）と背景のコントラスト比が WCAG 2.1 AA の本文最小 4.5:1 以上となる Phase 1 で定義済みのトークン値を使用する
4. The Settings screen shall ライト / ダーク両モードで SettingGroup の 1dp 外枠 / SettingRow の区切り線 / アイコンタイル背景等の非文字要素と背景のコントラスト比が WCAG 2.1 AA の非文字最小 3:1 以上となる Phase 1 で定義済みのトークン値を使用する
5. While 端末のシステム設定がダークモードである場合, the Autofill status hero shall NotEnabled 状態の `@color/kn_warning_soft` 背景 / `@color/kn_warning` accent を、`values-night` 上書きまたは適切なセマンティックトークンでダーク環境上で 4.5:1 以上のコントラストを維持する形で描画する
6. While 端末のシステム設定がダークモードである場合, the "Danger zone" SettingGroup shall `@color/kn_danger_soft` 背景を、`values-night` 上書きまたは適切なセマンティックトークンでダーク環境上で 4.5:1 以上のコントラストを維持する形で描画する

### Requirement 12: 既存機能・配線の不変

**Objective:** As a 既存ユーザー / メンテナ, I want UI の見た目変更によって設定画面の状態取得・遷移・コールバック・既存テストが壊れないこと, so that 本 Issue のリリース前後で機能差分を意識せずに利用できる

#### Acceptance Criteria

1. The Settings screen shall 既存の `SettingsActivity.newIntent(context)` 公開 API シグネチャを保持する
2. The Settings screen shall 既存の `SettingsViewModel` への ViewModel 接続（`ServiceLocator.observeVaultMetadataUseCase` / `getVaultStorageUsageUseCase` / `getDeviceLockStatusUseCase` / `appInfoProvider`）を保持する
3. The Settings screen shall 既存の `onResume()` での `SettingsViewModel.refresh()` 再評価経路を保持する
4. The Settings screen shall 既存 View ID（`btn_open_autofill_settings` / `btn_open_security_settings` / `btn_oss_licenses` / `btn_open_danger_zone` / `text_autofill_status` / `text_lock_status` / `text_vault_count` / `text_vault_latest_updated` / `text_vault_storage` / `text_app_version` / `toolbar`）を保持する
5. The Settings screen shall 既存の `AndroidManifest.xml` の `<activity android:name=".ui.settings.SettingsActivity" android:exported="false">`（および `DangerZoneActivity` / `OssLicensesActivity` の `parentActivityName` 経路）を変更しない
6. The Settings screen shall 既存の文字列リソースキー（`settings_title` / `settings_back_a11y` / `settings_section_autofill_title` / `settings_autofill_badge_enabled` / `settings_autofill_badge_not_enabled` / `settings_autofill_badge_a11y` / `settings_autofill_open_settings_action` / `settings_section_security_title` / `settings_lock_status_*` / `settings_lock_open_security_settings_action` / `settings_section_vault_title` / `settings_vault_count_label` / `settings_vault_count_format` / `settings_vault_latest_updated_label` / `settings_vault_latest_updated_empty` / `settings_vault_storage_label` / `settings_section_about_title` / `settings_about_version_label` / `settings_about_version_format` / `settings_about_oss_licenses_label` / `settings_section_danger_title` / `settings_danger_open_action` / `settings_intent_unavailable`）を本 Issue で削除・リネームしない
7. If `SystemSettingsIntents.openAutofillServiceChooser(this)` または `openSecuritySettings(this)` が `Result.failure` を返した場合, the Settings screen shall 既存どおり `@string/settings_intent_unavailable` の Snackbar を表示する
8. When `SettingsActivityTest` (`app/src/androidTest/`) が本 Issue の変更後に実行されたとき, the Android instrumentation test runner shall 既存テスト（少なくとも `btn_open_autofill_settings` / `btn_open_security_settings` / `btn_open_danger_zone` の押下経路を検証する 3 件）をすべて成功させる

## Non-Functional Requirements

### NFR 1: ビルドと既存テスト

1. When `./gradlew :app:assembleDebug` を CI またはローカルで実行したとき, the Android build pipeline shall 当該タスクを成功（exit code 0）で終了する
2. When 既存の単体テストスイート (`./gradlew :app:testDebugUnitTest`) を本 Issue の変更後に実行したとき, the Android test runner shall Phase 1 / Phase 2 既存完了分（Issue #28 / #29 / #30 / #31 / #32）で成功していたテストをすべて成功させる
3. The Settings screen shall 既存の `Material3ThemeMigrationTest` および `FontTypefaceWiringTest` の `Theme.KeyNest` / `TextAppearance.KeyNest.*` の structural pin に違反する変更を行わない

### NFR 2: アクセシビリティ

1. The Settings screen shall すべてのタップ可能要素（Autofill hero CTA / 各 SettingRow / Toolbar 戻るボタン）について最小タッチサイズ 48dp × 48dp（`design/spec.md` §10 の 44dp 視覚 + 4dp 余白）を維持する
2. The Settings screen shall アイコンのみの要素（Toolbar 戻る / SettingRow の leading アイコン）について、装飾用の場合は `android:importantForAccessibility="no"` または空 `contentDescription` を、機能用の場合はローカライズ済み文字列を `contentDescription` に設定する
3. The Settings screen shall Autofill ステータス hero のタイトル、および各 SettingGroup の Eyebrow ヘッダーについて `android:accessibilityHeading="true"` を付与し、TalkBack で section heading として読み上げられること
4. The Settings screen shall ライト / ダーク両モードで本文最小 4.5:1 / 非文字最小 3:1 のコントラスト基準を満たすトークン値を使用する（Req 11.3 / 11.4 の再掲）
5. The Settings screen shall Autofill ステータスチップに `@string/settings_autofill_badge_a11y`（"Autofill service status: %1$s"）相当のコンポジット contentDescription を保持し、ステータスが TalkBack で単独で読み上げ可能とする

### NFR 3: ローカライズ

1. The Settings screen shall 本 Issue で新規に表示するテキスト（hero タイトル "Autofill サービス" / hero 補足文 / ロック解除方法の label / About グループ KeyNest 行の label "KeyNest" / About グループ プライバシー行の label と sub / Danger zone 行の label / sub "復元できません" など、JSX `ScreenSettings` 由来の新規文字列）について、英語デフォルト (`values/strings.xml`) と日本語 (`values-ja/strings.xml`) の双方に同一キー集合で追加する
2. While 端末のロケールが日本語に設定されている場合, the Settings screen shall `values-ja/strings.xml` の翻訳済み文字列で全要素を表示する
3. While 端末のロケールが日本語以外に設定されている場合, the Settings screen shall `values/strings.xml` の英語デフォルト文字列で全要素を表示する
4. The Settings screen shall 本 Issue で新規追加する string キーを、英語 (`values/strings.xml`) と日本語 (`values-ja/strings.xml`) の両方で同時に追加する（Phase 1 NFR 2.1 整合）

## Out of Scope

- ロック方式の動的切替 UI（Issue #10 のスコープ判断で「Android 設定にディープリンク」に倒している既存方針を踏襲。本 Issue でも当該行は読み取り専用 + chevron + ディープリンクのまま）
- 生体認証切替 / Vault クリア / OSS ライセンス取得の機能側ロジック（Issue #10 で実装済み。本 Issue は UI 表層のみ）
- Autofill 有効化判定ロジック (`AutofillServiceStatus.isCurrentService` 経路) の変更（Issue #31 で扱っており本 Issue では UI スタイリングのみ）
- 「アンロック保持時間」「署名照合の厳密性」などのトグル / 値設定行の機能実装（JSX `ScreenSettings` の SettingRow `toggle` / `right="毎回"` 行は将来の機能拡張に伴う行であり、本 Issue では実装しない。本要件では「セキュリティ グループに必ず追加する」とは規定していない）
- 「エクスポート」行の機能実装（JSX `ScreenSettings` の Vault グループに含まれる "暗号化バックアップ" 行は将来機能であり、本 Issue では実装しない）
- プライバシーポリシー画面 / 外部 URL 遷移先の構築（本 Issue では情報表示行のみで遷移を発生させない）
- Vault 削除 BottomSheet / 確認ダイアログのデザイン整合（DangerZoneActivity 内部のスタイリングは Req 9 に限定し、確認ダイアログのスタイルは別 Issue）
- フッターの「すべてのデータは Android Keystore で保護された AES-GCM で暗号化されます」相当の説明文（JSX 末尾の caption。本 Issue では追加可否を design.md に委ねる）
- Compose 化（既存 View ベースのレイアウト XML 改修にとどめる）
- アニメーション（hero グラデーション以外の独自トランジション・モーション設計は本 Issue 対象外）
- 他 Phase 2 画面の整合（`mapping.md` §5 の他行は別 Issue）

## Open Questions

- Issue 本文 DoD #6 / #7 では `@style/Widget.KeyNest.SettingGroup` / `@style/Widget.KeyNest.SettingRow` 相当を要請しているが、現状 `app/src/main/res/values/themes.xml` には当該スタイルが定義されていない。本 Issue 内で当該スタイルを新規定義する（Phase 1 リソース層への追補として）か、既存トークン（`@color/kn_surface` / `@color/kn_border` / `@dimen/kn_r_md` / `@color/kn_text_3` / `@style/Text.KeyNest.Eyebrow` / `@dimen/kn_icon_tile_sm` / `@dimen/kn_r_sm` / `@color/kn_surface_2` / `@color/kn_text_2` 等）を個別に貼って同等視覚を実現するかは Architect 判断としてよいか
- Autofill ステータス hero のグラデーション背景（JSX `linear-gradient(140deg, var(--primary) 0%, var(--kn-blue-700) 100%)`）を Android で実現する手法（新規 `@drawable/kn_autofill_hero_gradient.xml` を `GradientDrawable` で起こす / `View.background` に動的に `GradientDrawable` を割り当てる / `@android:attr/colorPrimary` をベタ塗りに簡略化する）の選定は Architect 判断としてよいか。本要件では「斜め方向グラデーション」とのみ規定し、実現手法は設計判断に委ねている
- Autofill ステータス hero の NotEnabled 状態の hero 上 CTA ボタン（Req 2.12）は、Enabled 時の半透明白ボタンとは異なる外観が必要となるが、具体的トークン（`@color/kn_warning` 文字色 + `@color/kn_warning_soft` 上のアウトライン or テキストボタン）の選定は Architect 判断としてよいか
- JSX `ScreenSettings` の SettingRow 群（「ロック解除方法」「アンロック保持時間」「署名照合の厳密性」「クレデンシャル数」「エクスポート」「すべて削除」「KeyNest」「プライバシー」）のうち、本 Issue で実装する 7 行（ロック解除方法 / クレデンシャル数 / 最終更新 / DB サイズ / KeyNest / OSS ライセンス / プライバシー / すべて削除）と Out of Scope に振った 3 行（アンロック保持時間 / 署名照合の厳密性 / エクスポート）の選別は、Issue #10 の既存スコープと整合させた本要件の判断でよいか。新規行を含めるべきという判断がある場合は別 Issue を起票する想定でよいか
- 既存 `Material3.Button.TonalButton`（btn_open_autofill_settings / btn_open_security_settings）を Autofill hero 内 CTA / SettingRow に置換した結果、既存 `SettingsActivityTest` の `withId(R.id.btn_open_autofill_settings)` / `withId(R.id.btn_open_security_settings)` / `withId(R.id.btn_open_danger_zone)` の `perform(click())` が壊れないことの担保は、Req 5.9 / 12.4 のとおり「既存 ID を新規行ルート View に割り当てる」方針で問題ないか（テスト変更が必要な場合は別 Issue を起票する）
- 設定画面の文字列リソースは現状 `values/strings.xml`（英語）のみで `values-ja/strings.xml` への翻訳が未提供である。本 Issue で日本語訳を追加（NFR 3.1 / 3.4 のとおり同一キー集合での同時追加）するスコープに含めて差し支えないか。あるいは日本語訳追加は別 Issue に切り出し、本 Issue では新規追加文字列のみ ja / en 同時追加する範囲に留めるべきか
- JSX `ScreenSettings` 末尾のフッター caption「すべてのデータはこの端末の Android Keystore で保護された AES-GCM で暗号化されます」を本 Issue で追加するかは Architect 判断としてよいか。本要件では Out of Scope に振っているが、視覚整合の観点で追加が望ましい場合は design.md / tasks.md で具体化する余地を残す
