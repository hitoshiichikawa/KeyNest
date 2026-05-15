# Requirements Document

## Introduction

KeyNest の `CredentialEditActivity`（および `credential_edit_activity.xml`）は、現状 Material3 デフォルト
ウィジェットの素のままで構成されており、`design/screens/screens-1.jsx` の `ScreenEdit` モックで
定義された KeyNest 固有のビジュアル仕様（ターゲットアプリカード・トークン駆動の入力フィールド・
パスワード強度バー・詳細設定セクション・削除ボタン）と乖離している。本 Issue (#30) は
Phase 2 (`design/android-assets/mapping.md` §5 の `ScreenEdit` 行) の 2 件目の画面として、
Phase 1 (#28) で `app/src/main/res/` に取り込まれた `@color/kn_*` / `@dimen/kn_*` /
`@style/Widget.KeyNest.*` / `@style/Text.KeyNest.*` リソースを既存レイアウトに貼り込み、
JSX モックと視覚的に整合させる。既存の保存・編集・パッケージピッカー起動・署名取得・
詳細設定折りたたみ (Issue #14) ・パスワード安全取扱（CharArray + ゼロクリア）等のロジックは
そのまま維持し、本 Issue は UI 表層と、編集モードでの削除 CTA 表示（Issue #5/#7 の系列）に限定する。
パスワード強度判定ロジックと、ターゲットアプリのアイコン動的取得は本 Issue のスコープ外とする。

## Requirements

### Requirement 1: 画面ルート・サーフェスの整合

**Objective:** As a エンドユーザー, I want クレデンシャル編集画面の地色・横余白が KeyNest 設計トークンと一致していること, so that 一覧画面など他画面と矛盾しない一体感のあるブランド体験を受け取れる

#### Acceptance Criteria

1. The Credential Edit screen shall ルート背景色として Material3 attribute `?attr/colorBackground` を解決する（Phase 1 で `kn_bg` を指す形で接続済みの attribute を使用し、直接 `#` 指定や Material3 デフォルト解決を行わない）
2. The Credential Edit screen shall スクロール領域の左右パディングに `@dimen/kn_screen_padding_h`（20dp）を適用する
3. The Credential Edit screen shall アプリバー / ターゲットアプリカード / 入力フィールド群 / 詳細設定 / 削除ボタン間の縦間隔を `@dimen/kn_space_*` トークン値（4 / 8 / 12 / 16 / 20 / 24dp のいずれか）から選択して適用する
4. While 端末のシステム設定がダークモードである場合, the Credential Edit screen shall `values-night` の `kn_*` セマンティックトークン上書きで描画され、ライト用の hex 値を直接表示しない

### Requirement 2: アプリバーの視覚仕様

**Objective:** As a エンドユーザー, I want クレデンシャル編集画面のアプリバーが JSX モックの「閉じる × アイコン + タイトル + 保存テキストボタン」構成と整合していること, so that 編集をやめる動線と保存動線が一目で識別できる

#### Acceptance Criteria

1. The Credential Edit screen app bar shall タイトルテキストに `@style/Text.KeyNest.TitleS`（16sp / weight 700）相当の TextAppearance を適用する
2. The Credential Edit screen app bar shall ナビゲーション（閉じる）アイコンの tint を `@color/kn_text` 相当のセマンティックトークン経由で解決する
3. While 編集モード (`editingId != null`) である場合, the Credential Edit screen app bar shall タイトル文字列に `@string/credential_edit_title_edit` を表示する
4. While 新規モード (`editingId == null`) である場合, the Credential Edit screen app bar shall タイトル文字列に `@string/credential_edit_title_new` を表示する
5. The Credential Edit screen app bar shall 既存の `id/toolbar` ID と `setNavigationOnClickListener { finish() }` の閉じる挙動を保持する

### Requirement 3: ターゲットアプリカードの視覚仕様

**Objective:** As a エンドユーザー, I want 編集対象のアプリ（パッケージ名）を JSX モックのアイコンタイル付きカード形式で確認できる, so that どのアプリ向けのクレデンシャルを編集中か視覚的に把握でき、誤入力を防げる

#### Acceptance Criteria

1. The target-app card shall ルートのコンテナ外形を `@style/Widget.KeyNest.Card` または同等の `kn_surface` 背景 + `kn_border` 1dp 外枠 + `@dimen/kn_r_card`（18dp）角丸で描画する
2. The target-app card shall 内側パディングに `@dimen/kn_card_padding`（14dp）を適用する
3. The target-app card shall 左端に 44dp × 44dp 相当（`@dimen/kn_icon_tile_lg`）、角丸 `@dimen/kn_r_sm`（12dp）以上 14dp 以下のアイコンタイルを配置する
4. The target-app card shall アイコンタイルの背景色に、本 Issue では `@color/kn_blue_500` を単色フォールバックとして使用する（PackageManager からのアプリアイコン動的取得は別 Issue とする）
5. The target-app card shall アプリ名表示行（編集対象のパッケージ名から派生する表示用ラベル）の TextAppearance に `@style/Text.KeyNest.Body`（14sp / weight 600）または `@style/Text.KeyNest.TitleS`（16sp / weight 700）相当を適用し、1 行 ellipsis を維持する
6. The target-app card shall パッケージ名表示の TextAppearance に `@style/Text.KeyNest.Mono`（JetBrains Mono / 12sp）相当、テキスト色に `@color/kn_text_2` を適用し、1 行 ellipsis を維持する
7. While ロード中のクレデンシャルレコードの `signatureSha256` が非 null である場合, the target-app card shall 署名 chip を表示し、背景色に `@color/kn_success_soft`、テキスト色に `@color/kn_success`、テキスト文字列に `@string/signature_match` を適用する
8. While ロード中のクレデンシャルレコードの `signatureSha256` が null である、または signature が未取得である場合, the target-app card shall 署名 chip を表示し、背景色に `@color/kn_warning_soft`、テキスト色に `@color/kn_warning`、テキスト文字列に `@string/signature_missing` を適用する
9. The target-app card shall 既存の `id/input_package` および `id/btn_pick_installed_app` View（パッケージピッカーを呼び出すための入力フィールドおよび「インストール済みアプリから選択」ボタン）を画面上のどこかに保持し、`CredentialEditActivity.PackagePickerBottomSheet.show()` の呼び出し配線が壊れないようにする

### Requirement 4: 入力フィールド群（表示名・ユーザー名）の視覚仕様

**Objective:** As a エンドユーザー, I want 表示名・ユーザー名の入力フィールドが JSX モックの「ラベル + アウトライン入力 + ヘルパー」構成・トークンと一致していること, so that 入力対象が直感的に分かり、視覚的なコントラストで読み違えない

#### Acceptance Criteria

1. The Credential Edit screen shall 表示名 (`id/layout_label` / `id/input_label`) およびユーザー名 (`id/layout_username` / `id/input_username`) の `TextInputLayout` スタイルを `@style/Widget.KeyNest.TextField` に差し替える（Material3 デフォルトの `Widget.Material3.TextInputLayout.OutlinedBox` を直接指定しない）
2. The Credential Edit screen shall 各 `TextInputLayout` の角丸として `@dimen/kn_r_input`（14dp）を適用する
3. The Credential Edit screen shall 各入力フィールドの最小高さに `@dimen/kn_input_height`（52dp）を適用する
4. The Credential Edit screen shall 入力テキストの TextAppearance に `@style/Text.KeyNest.Body`（14sp / weight 600）相当、テキスト色に `@color/kn_text` を適用する
5. The Credential Edit screen shall ラベル（hint）の TextAppearance に `@style/Text.KeyNest.BodyS`（13sp / weight 500）または Eyebrow 相当、テキスト色に `@color/kn_text_2` を適用する
6. The Credential Edit screen shall 既存のフィールド hint 文字列リソース（`@string/label_display_name` / `@string/label_username`）をそのまま使用する（文字列キー自体は本 Issue で変更しない）
7. The Credential Edit screen shall 既存の `id/input_label` / `id/input_username` / `id/layout_label` / `id/layout_username` ID を保持し、`onSaveClicked()` が参照する入力経路を壊さない

### Requirement 5: パスワードフィールドの視覚仕様

**Objective:** As a エンドユーザー, I want パスワードフィールドが JSX モックの「強調アウトライン + モノスペース表示 + 目アイコン」構成と一致していること, so that 編集中のパスワード入力フィールドであることを視覚的に強く認識でき、誤入力を防げる

#### Acceptance Criteria

1. The password field shall `TextInputLayout` スタイルを `@style/Widget.KeyNest.TextField` に差し替える、または同等の `@dimen/kn_r_input`（14dp）角丸 + `@dimen/kn_input_height`（52dp）高さで描画する
2. The password field shall 入力テキストの TextAppearance に `@style/Text.KeyNest.Password`（17sp / weight 700 / mono / letter-spacing 0.1em）相当、テキスト色に `@color/kn_text` を適用する
3. The password field shall ラベル（hint）の TextAppearance に表示名・ユーザー名フィールド（Req 4.5）と同等の TextAppearance を適用する
4. The password field shall パスワード表示切替アイコン（既存 `app:passwordToggleEnabled="true"` 由来）または同等のトグルを保持し、`@color/kn_text_2` 相当の tint で描画する
5. The password field shall 既存の `id/input_password` / `id/layout_password` ID と `inputType="textPassword"` 属性を保持し、`takePasswordCharArray()` が参照する EditText buffer 経路を壊さない

### Requirement 6: パスワード強度バーの視覚仕様

**Objective:** As a エンドユーザー, I want パスワード強度を 3 セグメントのバーで視覚的に確認できる, so that 弱いパスワードを入力したまま保存していないかを目視で判断できる

#### Acceptance Criteria

1. The password strength bar shall パスワードフィールドの近傍（JSX `PasswordField` の右上、ラベルと同一行）に配置される
2. The password strength bar shall 1 セグメントの幅を `@dimen/kn_strength_seg_w`（14dp）、高さを `@dimen/kn_strength_seg_h`（4dp）、セグメント間ギャップを `@dimen/kn_strength_seg_gap`（2dp）として 3 セグメントを横並びで描画する（Issue #29 Req 6 で導入された一覧側の `StrengthBar` 寸法と一致させる）
3. While 強度が "strong" である場合, the password strength bar shall 3 セグメントすべてを `@color/kn_success` で塗りつぶす
4. While 強度が "medium" である場合, the password strength bar shall 先頭 2 セグメントを `@color/kn_warning`、末尾 1 セグメントを `@color/kn_border_strong` で塗りつぶす
5. While 強度が "weak" である場合, the password strength bar shall 先頭 1 セグメントを `@color/kn_danger`、後続 2 セグメントを `@color/kn_border_strong` で塗りつぶす
6. If 強度判定ロジックが未実装である（現時点では本 Issue のスコープ外）場合, the password strength bar shall サンプル固定値（強度バー widget のデフォルト値、たとえば "strong"）で描画する（強度算出ロジックの本格実装は別 Issue とし、本 Issue は表示要素の存在と配色トークンのみを担保する）

### Requirement 7: 詳細設定（折りたたみ）セクションの視覚仕様

**Objective:** As a エンドユーザー, I want 詳細設定セクション（パッケージ名・署名 SHA-256・登録日）が JSX モックの「surface-2 背景 + ラベル/値の 2 カラム」構成と一致していること, so that 既存の Issue #14 で導入された詳細表示が KeyNest 全体のビジュアル言語に統合される

#### Acceptance Criteria

1. The advanced details section shall コンテナ背景に `@color/kn_surface_2` を適用する
2. The advanced details section shall コンテナ外形の角丸として `@dimen/kn_r_input`（14dp）または同等のトークンを適用する
3. The advanced details section shall コンテナ内側パディングに `@dimen/kn_card_padding`（14dp）を適用する
4. The advanced details section shall ヘッダーテキスト（`id/advanced_title`）の TextAppearance に `@style/Text.KeyNest.Body`（14sp / weight 600）相当、テキスト色に `@color/kn_text_2` を適用する
5. The advanced details section shall ヘッダーの chevron アイコン (`id/advanced_chevron`) の tint を `@color/kn_text_3` で適用する
6. The advanced details section shall 各行（パッケージ名 / 署名 SHA-256 / 登録日 / 署名取得日時 / クレデンシャル ID）のラベル TextAppearance に `@style/Text.KeyNest.Caption`（12sp / weight 500）相当、テキスト色に `@color/kn_text_2` を適用する
7. The advanced details section shall 各行の値表示について、モノスペース対象（パッケージ名・署名 SHA-256・クレデンシャル ID）は `@style/Text.KeyNest.Mono`（JetBrains Mono / 12sp）相当、それ以外（登録日・署名取得日時）は `@style/Text.KeyNest.Caption` または `Text.KeyNest.Body` 相当を適用し、テキスト色に `@color/kn_text` を適用する
8. The advanced details section shall 行と行の区切り線色に `@color/kn_border` を適用する
9. The advanced details section shall 既存の `id/advanced_header` / `id/advanced_chevron` / `id/advanced_content` / `id/row_created_at` / `id/value_created_at` / `id/row_updated_at` / `id/value_updated_at` / `id/row_signature_hex` / `id/value_signature_hex` / `id/btn_copy_signature_hex` / `id/row_signature_captured_at` / `id/value_signature_captured_at` / `id/row_credential_id` / `id/value_credential_id` / `id/toggle_credential_id` を保持する
10. The advanced details section shall Issue #14 で実装済みの「ヘッダータップで content 表示切替」「chevron 回転」「初期状態は折りたたみ」「コピーボタンの enable/disable 制御」「ID トグル」のいずれの挙動も変更しない

### Requirement 8: 削除ボタンの視覚仕様と表示制御

**Objective:** As a エンドユーザー, I want 編集中のクレデンシャルを編集画面から削除できる, so that 一覧画面に戻って長押しする手間なく、編集の流れで削除動線まで完結できる

#### Acceptance Criteria

1. While 編集モード (`editingId != null`) である場合, the Credential Edit screen shall 削除ボタンを画面下部に表示する
2. While 新規モード (`editingId == null`) である場合, the Credential Edit screen shall 削除ボタンを表示しない
3. The delete button shall ボタンの幅を画面幅いっぱい（`match_parent` 相当）に設定する
4. The delete button shall 最小高さに `@dimen/kn_button_height`（52dp）を適用する
5. The delete button shall ボタン外形を `@color/kn_border` 1dp 外枠 + 透過背景 + `@dimen/kn_r_input`（14dp）角丸（JSX `border: '1px solid var(--border)'` + `borderRadius: 14` 相当）で描画する
6. The delete button shall テキスト色に `@color/kn_danger`（または Material3 attribute `?attr/colorError` 経由で同等のトークン）を適用する
7. The delete button shall テキスト TextAppearance に `@style/Text.KeyNest.LabelL`（15sp / weight 700）または `@style/Text.KeyNest.Body`（14sp / weight 600）相当を適用する
8. The delete button shall ボタンテキストに `@string/action_delete_credential` を使用する（一覧画面側の長押し削除動線と同一の文字列リソースを共有する）
9. When ユーザーが削除ボタンを押下したとき, the Credential Edit screen shall 削除確認ダイアログを表示する（誤タップ防止のため、即時削除は行わない）
10. When ユーザーが削除確認ダイアログで肯定 (positive) ボタンを押下したとき, the Credential Edit screen shall 現在編集中のクレデンシャル `editingId` を引数に既存の `DeleteCredentialUseCase` を呼び出し、成功後に `finish()` で画面を閉じて一覧画面へ戻る
11. When ユーザーが削除確認ダイアログで否定 (negative) ボタンを押下したとき, the Credential Edit screen shall ダイアログを閉じ、編集画面のまま状態を維持する
12. If 削除処理が失敗した場合, the Credential Edit screen shall Snackbar またはトースト等のユーザー可視通知でエラーを伝え、画面を閉じない

### Requirement 9: 保存ボタンの視覚仕様

**Objective:** As a エンドユーザー, I want 保存ボタンが JSX モックの「右上テキストボタン」または主要 CTA として、KeyNest プライマリ配色で描画されること, so that 編集確定動線をブランドに沿った形で一貫して認識できる

#### Acceptance Criteria

1. The save button shall アプリバー右側に配置される、または画面下部に配置される。前者の場合 `@style/Widget.KeyNest.Button.Text` 相当、後者の場合 `@style/Widget.KeyNest.Button.Primary` 相当のスタイルで描画する
2. While 保存ボタンがアプリバー内に配置される場合, the save button shall テキスト色を `@color/kn_primary` で描画する（JSX `ScreenEdit` のアプリバー右側「保存」テキストボタンと整合させる）
3. While 保存ボタンが画面下部の Primary CTA として配置される場合, the save button shall 背景色を `@color/kn_primary`、テキスト色を `@color/kn_on_primary`、最小高さを `@dimen/kn_button_height`（52dp）、角丸を `@dimen/kn_r_input`（14dp）で描画する
4. The save button shall ボタンテキストに `@string/action_save` を使用する
5. The save button shall 既存の `id/btn_save` ID および `setOnClickListener { onSaveClicked() }` 配線を保持する（配置位置を変更しても既存 ViewModel 配線は壊さない）

### Requirement 10: 既存機能・配線の不変

**Objective:** As a 既存ユーザー, I want UI の見た目変更によって保存・編集・パッケージピッカー起動・署名取得・詳細設定折りたたみ・パスワード安全取扱の機能が壊れないこと, so that 本 Issue のリリース前後で機能差分を意識せずに利用できる

#### Acceptance Criteria

1. The Credential Edit screen shall 既存の View ID（`toolbar` / `layout_package` / `input_package` / `btn_pick_installed_app` / `layout_username` / `input_username` / `layout_password` / `input_password` / `layout_label` / `input_label` / `btn_save` / `advanced_header` / `advanced_title` / `advanced_chevron` / `advanced_content` / `row_created_at` / `value_created_at` / `row_updated_at` / `value_updated_at` / `row_signature_hex` / `value_signature_hex` / `btn_copy_signature_hex` / `row_signature_captured_at` / `value_signature_captured_at` / `row_credential_id` / `value_credential_id` / `toggle_credential_id`）をすべて保持する
2. When ユーザーが保存ボタン (`btn_save`) を押下したとき, the Credential Edit screen shall 既存どおり `CredentialEditViewModel.save()` を `editingId` / packageName / username / password (CharArray) / label の組で呼び出す
3. When ユーザーが「インストール済みアプリから選択」ボタン (`btn_pick_installed_app`) を押下したとき, the Credential Edit screen shall 既存どおり `PackagePickerBottomSheet.show()` を起動し、選択結果を `input_package` に反映する
4. When ユーザーが詳細設定ヘッダー (`advanced_header`) を押下したとき, the Credential Edit screen shall 既存どおり `viewModel.toggleAdvancedExpanded()` を呼び出して content の表示を切り替える
5. When ユーザーが署名 SHA-256 のコピーボタン (`btn_copy_signature_hex`) を押下したとき, the Credential Edit screen shall 既存どおり全 64 文字 SHA-256 hex をクリップボードへコピーする
6. When ユーザーがクレデンシャル ID トグル (`toggle_credential_id`) を切り替えたとき, the Credential Edit screen shall 既存どおり `viewModel.toggleCredentialIdVisible()` を呼び出して値表示・伏字を切り替える
7. The Credential Edit screen shall 既存の CharArray ベースのパスワード取扱（`takePasswordCharArray()` による `Editable` から `CharArray` への取り出し + 元 buffer のゼロクリア）を変更しない
8. The Credential Edit screen shall Issue #14 で導入された SafeLogger 制約（全 64 文字 SHA-256 hex を logcat / SafeLogger に出力せず、preview 8 文字のみ出力する）を変更しない
9. The Credential Edit screen shall 既存のフィールド入力エラー表示（`error_package_name_blank` / `error_package_name_invalid` / `error_username_blank` / `error_password_blank` / `error_label_blank` / `error_save_failed_with_reason` / `message_credential_saved`）の文字列リソース解決を変更しない

### Requirement 11: ライト / ダーク両モードの描画整合

**Objective:** As a エンドユーザー, I want システムテーマ（Light / Dark）の切り替え後も配色が破綻せず読みやすいこと, so that 端末設定を変えても KeyNest の体感品質が落ちない

#### Acceptance Criteria

1. While 端末のシステム設定がライトモードである場合, the Credential Edit screen shall `values/colors.xml` の `kn_*` セマンティックトークンを解決する
2. While 端末のシステム設定がダークモードである場合, the Credential Edit screen shall `values-night/colors.xml` の `kn_*` セマンティックトークンを解決する
3. The Credential Edit screen shall ライト / ダーク両モードでカード（ターゲットアプリカード / 入力フィールド / 詳細設定）のタイトル・本文テキスト（`@color/kn_text`）と背景（`@color/kn_surface` / `@color/kn_surface_2`）のコントラスト比が WCAG 2.1 AA の本文最小 4.5:1 以上となる
4. The Credential Edit screen shall ライト / ダーク両モードで chip outline・カード outline・アイコン tint 等の非文字要素と背景のコントラスト比が WCAG 2.1 AA の非文字最小 3:1 以上となる Phase 1 で定義済みのトークン値を使用する

## Non-Functional Requirements

### NFR 1: ビルドと既存テスト

1. When `./gradlew :app:assembleDebug` を CI またはローカルで実行したとき, the Android build pipeline shall 当該タスクを成功（exit code 0）で終了する
2. When 既存の単体テストスイート (`./gradlew :app:testDebugUnitTest`) を本 Issue の変更後に実行したとき, the Android test runner shall Phase 1 / Issue #29 完了時点で成功していたテストをすべて成功させる
3. The Credential Edit screen shall 既存の `Material3ThemeMigrationTest` および `FontTypefaceWiringTest` の `Theme.KeyNest` / `TextAppearance.KeyNest.*` の structural pin に違反する変更を行わない

### NFR 2: アクセシビリティ

1. The Credential Edit screen shall すべてのタップ可能要素（保存ボタン / 削除ボタン / 「インストール済みアプリから選択」ボタン / 詳細設定ヘッダー / 署名コピーボタン / クレデンシャル ID トグル / アプリバーの閉じる × アイコン）について最小タッチサイズ 48dp × 48dp（`spec.md` §10 の 44dp 視覚 + 4dp 余白規定を満たす dp）を維持する
2. The Credential Edit screen shall アイコンのみのボタン（アプリバーの閉じる × / 署名コピーボタン）について、TalkBack 用 `contentDescription` を既存どおりローカライズ済み文字列で設定する
3. The Credential Edit screen shall ライト / ダーク両モードで本文最小 4.5:1 / 非文字最小 3:1 のコントラスト基準を満たすトークン値を使用する（Req 11.3 / 11.4 の再掲）
4. The Credential Edit screen shall 削除ボタンが表示されている場合、`contentDescription` または可視テキストでスクリーンリーダーに「削除」相当の意図が伝わるよう既存の `@string/action_delete_credential`（"Delete" / "削除"）を使用する

### NFR 3: ローカライズ

1. The Credential Edit screen shall 本 Issue で表示する全テキスト（タイトル / ラベル / ボタンキャプション / 署名 chip / エラーメッセージ）について、英語デフォルト (`values/strings.xml`) と日本語 (`values-ja/strings.xml`) の双方に既存キーが存在することを前提とし、新規キーを追加する場合は両 locale に同時に追加する
2. While 端末のロケールが日本語に設定されている場合, the Credential Edit screen shall `values-ja/strings.xml` に定義された訳語で全テキストを表示する

## Out of Scope

- 保存 / 編集 / 削除 / 署名取得の機能ロジック（既存 `CredentialEditViewModel` / `SaveCredentialUseCase` / `UpdateCredentialUseCase` / `DeleteCredentialUseCase` / 署名 resolver）を変更すること。本 Issue は UI 表層と、編集モードでの削除 CTA 表示・確認ダイアログ・既存 UseCase 配線のみを扱う
- ターゲットアプリのアイコン動的取得（PackageManager 経由でアプリアイコン Drawable を取得して表示する機能）— 本 Issue では `@color/kn_blue_500` 単色フォールバックに固定し、本格的なアイコン取得は別 Issue
- パスワード強度判定ロジックの実装（パスワード文字列から strong / medium / weak を導出する処理）— 本 Issue では強度バーの表示要素と配色のみを担保し、入力に追従する動的な強度算出は別 Issue
- アイコンタイル背景色の per-credential 出し分け（JSX `SAMPLE_CREDS.color` に相当する動的カラー割り当て）— 本 Issue は単色フォールバックのみ
- アイコンタイル上のレターアイコン（JSX `IconTile.letter` に相当する 1 文字頭文字）の自動選定ロジック — 表示の有無・選定ルールは設計判断で決定し、要件としては「44dp / r12〜14 / `kn_blue_500` 背景」のみ規定する
- パッケージ名入力フィールド (`layout_package` / `input_package`) の配置変更 — 既存 ID と機能配線を保持できれば、ターゲットアプリカードに集約する／既存どおり TextInputLayout として残すかは設計判断としてよい
- 削除確認ダイアログのカスタムテーマ化（一覧画面側の長押し削除確認ダイアログと同等の `MaterialAlertDialogBuilder` 既定スタイルで差し支えない）
- Compose 化（既存 View ベースのレイアウト XML 改修にとどめる）
- アニメーション（既存の chevron 回転以外の独自トランジション・モーション設計）
- `Material3ThemeMigrationTest` の structural pin 緩和（Phase 1 確認事項として別 Issue 候補に挙がっている。本 Issue で test 自体は書き換えない）
- `dataset_presentation.xml` / `package_picker_bottom_sheet.xml` / `autofill_enable_activity.xml` 等、他 Phase 2 画面の整合（`mapping.md` §5 の他行は別 Issue）

## Open Questions

- Issue 本文 DoD #6 では削除ボタンを「`?attr/colorError` / `@style/Widget.KeyNest.Button.Destructive` 相当」と記載しているが、現状の `app/src/main/res/values/themes.xml` には `Widget.KeyNest.Button.Destructive` スタイルが定義されていない。本 Issue 内で当該スタイルを新規定義するか、`?attr/colorError` を引いた MaterialButton（OutlinedButton 等）で同等の視覚を実現するかは Architect 判断としてよいか
- Issue 本文 DoD #4 では強度バーを「行リスト用 `StrengthBar` と共用、強/中/弱で `kn_success/warning/danger`」と記載しているが、現時点で `Credential` ドメインにパスワード強度フィールドは存在せず、入力文字列からリアルタイム強度算出を行うロジックも未実装である。本 Issue では Req 6.6 のとおり「サンプル固定値で常に "strong" 等を表示する」方針としているが、入力フィールドの値変更に追従させずモック近似で良いか、Architect / 人間レビュワーに確認したい
- JSX `ScreenEdit` ではアプリバー右側に「保存」テキストボタンが配置されており、現状実装では画面下部に `MaterialButton btn_save` として配置されている。Req 9.1 では両方の配置を許容する形にしているが、最終的にどちらを採用するかは設計判断としてよいか（既存 `id/btn_save` を保持できれば配置変更可）
- JSX `ScreenEdit` のターゲットアプリカードには「変更」テキストボタン（パッケージ変更動線）が含まれている。現状実装は `btn_pick_installed_app`（"インストール済みアプリから選択"）として独立配置されている。両者の配置統合（ターゲットアプリカード内に「変更」ボタンを集約する）を本 Issue で行うか、別 Issue にするかは設計判断としてよいか
- 編集モードのターゲットアプリカードに表示するアプリ名は、現状 `Credential.label`（ユーザー設定の表示名）と `Credential.packageName` のみで、JSX モックのような「Salesforce Mobile」風アプリ正式名称を取得する手段は本 Issue のスコープ外（PackageManager 動的取得は Out of Scope）。本 Issue では `Credential.label` を表示することで差し支えないか
