# Requirements Document

## Introduction

KeyNest の `CredentialListActivity`（および周辺レイアウト `credential_list_activity.xml` /
`credential_list_item.xml` / `credential_list_recent_item.xml`）は、現状 Material3 デフォルト
ウィジェットを直接利用しており、`design/screens/screens-1.jsx` の `ScreenListEmpty` /
`ScreenListPopulated` モックで定義された KeyNest 固有のビジュアル仕様（カード型行・
アイコンタイル・強度バー・署名 chip・検索バー外観・フィルター chip 外観・空状態ヒーロー）と
乖離している。本 Issue (#29) は Phase 2 (`design/android-assets/mapping.md` §5 行先頭) の最初の
画面として、Phase 1 (#28) で `app/src/main/res/` に取り込まれた `@color/kn_*` / `@dimen/kn_*` /
`@style/Widget.KeyNest.*` / `@style/Text.KeyNest.*` リソースを既存レイアウトに **貼り込む** ことで、
JSX モックと視覚的に整合させる。既存の検索・フィルター・並び替え・「最近使った」・per-row
overflow メニュー機能（Issue #9 / #10 で実装済み）はそのまま維持し、本 Issue は UI 表層のみを
扱う。

## Requirements

### Requirement 1: 画面ルート・サーフェスの整合

**Objective:** As a エンドユーザー, I want クレデンシャル一覧画面の地色・横余白が KeyNest 設計トークンと一致していること, so that 他画面と矛盾しない一体感のあるブランド体験を受け取れる

#### Acceptance Criteria

1. The Credential List screen shall ルート背景色として Material3 attribute `?attr/colorBackground` を解決する（直接 `#` 指定や Material3 デフォルト解決ではなく、Phase 1 で `kn_bg` を指す形で接続済みの attribute を使用する）
2. The Credential List screen shall 一覧スクロール領域の左右パディングに `@dimen/kn_list_padding_h`（16dp）を適用する
3. The Credential List screen shall アプリバー / 検索バー / フィルター chip / 「最近使った」セクション / メインリスト間の縦間隔を `@dimen/kn_space_*` トークン値（4 / 8 / 12 / 16 / 20 / 24dp のいずれか）から選択して適用する
4. While 端末のシステム設定がダークモードである場合, the Credential List screen shall `values-night` の `kn_*` セマンティックトークン上書きで描画され、ライト用の hex 値を直接表示しない

### Requirement 2: 検索バーの視覚仕様

**Objective:** As a エンドユーザー, I want 検索バーが JSX モックの「淡い surface-2 背景 + 角丸 + placeholder 色」と一致していること, so that タップ可能なヒント領域として直感的に認識できる

#### Acceptance Criteria

1. The search bar shall 既存の `TextInputLayout`（Material3 OutlinedBox）スタイルから KeyNest 設計トークンを使う外観へ差し替える（外枠ストロークは `@color/kn_border`、入力背景は `?attr/colorSurface` 系から `@color/kn_surface_2` 相当へ、placeholder テキストは `@style/Text.KeyNest.BodyS` のフォント / サイズ）
2. The search bar shall placeholder テキストカラーに `@color/kn_text_3`（または `?attr/colorOnSurfaceVariant` 経由で同等のトークン）を適用する
3. The search bar shall 角丸半径として `@dimen/kn_r_md`（16dp）または `@dimen/kn_r_input`（14dp）のいずれか mapping.md §2 で input 用に定義された値を適用する
4. The search bar shall 既存の文字列リソース `@string/credential_list_search_hint` を hint テキストとして使用する（文字列キー自体は本 Issue で変更しない）
5. The search bar shall 既存の `id/input_search` および `id/layout_search` ID を保持し、`CredentialListActivity.setUpSearch()` の `addTextChangedListener` 配線が壊れないようにする

### Requirement 3: フィルター chip 行の視覚仕様

**Objective:** As a エンドユーザー, I want フィルター chip が JSX モックの「pill 形状・選択時 primary 塗りつぶし・非選択時 outline」と一致していること, so that 現在絞り込み中の条件を一目で把握できる

#### Acceptance Criteria

1. The filter chip row shall 各 chip のスタイルとして `@style/Widget.KeyNest.Chip` を継承する（Material3 デフォルトの `Widget.Material3.Chip.Filter` を直接指定しない）
2. The filter chip row shall chip の最小高さに `@dimen/kn_chip_height`（32dp）を適用する
3. The filter chip row shall chip の角丸として `@dimen/kn_r_chip`（pill 相当）を適用する
4. While 任意のフィルター chip が選択状態である場合, the filter chip row shall その chip の背景色を `@color/kn_primary`、テキスト色を `@color/kn_on_primary` で描画する
5. While 任意のフィルター chip が非選択状態である場合, the filter chip row shall その chip の背景色を `@color/kn_surface`、外枠を `@color/kn_border` 1dp、テキスト色を `@color/kn_text` で描画する
6. The filter chip row shall 既存 chip ID（`chip_signature_matched` / `chip_signature_missing`）と既存 `ChipGroup` の `singleSelection=true` 挙動を保持する

### Requirement 4: 「最近使った」横スクロールの視覚仕様

**Objective:** As a エンドユーザー, I want 「最近使った」カードが JSX モックの「132dp 幅・r18 角丸・アイコンタイル + ラベル + 時刻メタ」の構成と一致していること, so that 直近の利用履歴をすばやく再利用できる

#### Acceptance Criteria

1. The recently-used carousel shall セクションヘッダーに `@style/Text.KeyNest.Eyebrow`（11sp / weight 700 / UPPERCASE / letter-spacing 0.08em）相当の TextAppearance を適用する
2. The recently-used carousel shall 各カードの外形を `@style/Widget.KeyNest.Card` または同等の `kn_surface` 背景 + `kn_border` 1dp 外枠 + `@dimen/kn_r_lg`（20dp）または `@dimen/kn_r_card`（18dp）の角丸で描画する
3. The recently-used carousel shall 各カード左上に 36dp × 36dp、角丸 `@dimen/kn_r_sm`（12dp）相当のアイコンタイルを配置する
4. The recently-used carousel shall アイコンタイルの背景色に、本 Issue では `@color/kn_blue_500` を単色フォールバックとして使用する（per-credential のカラー割り当ては別 Issue とする）
5. The recently-used carousel shall カード内のラベルに `@style/Text.KeyNest.Body`（14sp / weight 600）相当、時刻メタに `@style/Text.KeyNest.Caption`（12sp / weight 500）相当の TextAppearance を適用する
6. The recently-used carousel shall 既存 `id/recent_recycler` / `id/recent_header` を保持し、`renderRecentVisibility()` の View.GONE 切替（空時に非表示）の挙動を変更しない
7. Where `Credential.lastUsedAt` が `null` のレコードしか存在しない場合, the recently-used carousel shall セクションヘッダーとカード列の両方を非表示にする（Issue #9 既存挙動の保持）

### Requirement 5: メインリスト行カードの視覚仕様

**Objective:** As a エンドユーザー, I want 一覧の各行が JSX モック `CredCard` と一致した「カード型 + アイコンタイル + タイトル / ユーザー名 / 強度バー・パッケージ名 + more ボタン」の構成で描画されること, so that 個々のクレデンシャルを視覚的に区別しやすい

#### Acceptance Criteria

1. The credential row shall ルートのコンテナ外形を `@style/Widget.KeyNest.Card` または同等の `kn_surface` 背景 + `kn_border` 1dp 外枠 + `@dimen/kn_r_card`（18dp）角丸で描画する
2. The credential row shall 内側パディングに `@dimen/kn_card_padding`（14dp）を適用する
3. The credential row shall 行の左端に 44dp × 44dp、角丸 `@dimen/kn_r_sm`（12dp）相当のアイコンタイルを配置する
4. The credential row shall アイコンタイルの背景色に、本 Issue では `@color/kn_blue_500` を単色フォールバックとして使用する（per-credential のカラー割り当ては別 Issue とする）
5. The credential row shall タイトル（既存 `text_label`）の TextAppearance に `@style/Text.KeyNest.Body`（14sp / weight 600）または `@style/Text.KeyNest.TitleS`（16sp / weight 700）相当を適用し、1 行 ellipsis を維持する
6. The credential row shall ユーザー名（既存 `text_subtitle` のうちユーザー名部分）の TextAppearance に `@style/Text.KeyNest.BodyS`（13sp / weight 500）相当、テキスト色に `@color/kn_text_2` を適用し、1 行 ellipsis を維持する
7. The credential row shall パッケージ名表示に `@style/Text.KeyNest.Mono`（JetBrains Mono / 12sp）相当の TextAppearance、テキスト色に `@color/kn_text_3` を適用し、1 行 ellipsis を維持する
8. The credential row shall 既存 `btn_overflow` の押下挙動・content description ロジック（`CredentialListAdapter` の `onOverflow` コールバック呼び出し）を保持し、見た目だけ 24dp アイコン + 48dp タッチターゲット + tint `@color/kn_text_3` に揃える
9. The credential row shall 行間ギャップ（縦方向）に `@dimen/kn_space_2`（8dp）を適用する

### Requirement 6: パスワード強度バーの視覚仕様

**Objective:** As a エンドユーザー, I want パスワード強度を 3 セグメントのバーで視覚的に把握できる, so that 弱いパスワードを目視で見つけて差し替え判断ができる

#### Acceptance Criteria

1. The strength bar shall 1 セグメントの幅を `@dimen/kn_strength_seg_w`（14dp）、高さを `@dimen/kn_strength_seg_h`（4dp）、セグメント間ギャップを `@dimen/kn_strength_seg_gap`（2dp）として 3 セグメントを横並びで描画する
2. While 強度が "strong" である場合, the strength bar shall 3 セグメントすべてを `@color/kn_success` で塗りつぶす
3. While 強度が "medium" である場合, the strength bar shall 先頭 2 セグメントを `@color/kn_warning`、末尾 1 セグメントを `@color/kn_border_strong` で塗りつぶす
4. While 強度が "weak" である場合, the strength bar shall 先頭 1 セグメントを `@color/kn_danger`、後続 2 セグメントを `@color/kn_border_strong` で塗りつぶす
5. If 行データから強度が判定できない（既存 `Credential` ドメインに強度フィールドが存在しないため、本 Issue 範囲では実値が常に取れない）場合, the strength bar shall その行において強度バーを非表示にする（プレースホルダや誤った色を描画しない）

### Requirement 7: 署名 chip の視覚仕様

**Objective:** As a エンドユーザー, I want 各クレデンシャルの署名取得状態を chip で識別できる, so that 署名検証済みのものとそうでないものを取り違えずに使い分けられる

#### Acceptance Criteria

1. While `Credential.signatureSha256` が非 null である場合, the credential row shall 署名 chip を表示し、背景色に `@color/kn_success_soft`、テキスト色に `@color/kn_success` を適用する
2. While `Credential.signatureSha256` が null である場合, the credential row shall 署名 chip を表示し、背景色に `@color/kn_warning_soft`、テキスト色に `@color/kn_warning` を適用する
3. The credential row shall 署名 chip 内のテキストに、署名取得済み状態では `@string/signature_match` を、未取得状態では `@string/signature_missing` を表示する（両キーは Phase 1 で `values-ja/strings.xml` に追加済み。`values/strings.xml` への既定値追加が必要な場合は本 Issue のスコープに含む）

### Requirement 8: 空状態 (ScreenListEmpty) の視覚仕様

**Objective:** As a 新規ユーザー, I want クレデンシャルが 0 件のときに KeyNest のヒーロー演出と登録 CTA を見られる, so that 初回の登録動線が直感的に理解できる

#### Acceptance Criteria

1. While `state.emptyKind` が `EmptyKind.Initial` である場合, the empty state view shall KeyNest マーク（既存ランチャーアイコン / 設計トークンと整合するヒーロー描画）と見出し文字列 `@string/credential_list_empty`、補足文、登録 CTA ボタン、フッターのセキュリティ表記の 4 要素を縦方向中央寄せで描画する
2. The empty state view shall 見出しテキストに `@style/Text.KeyNest.TitleM`（19sp / weight 800）または同等の TextAppearance を適用する
3. The empty state view shall 補足文テキストに `@style/Text.KeyNest.Body` 相当 + `@color/kn_text_2` を適用する
4. The empty state view shall 登録 CTA ボタンに `@style/Widget.KeyNest.Button.Primary` を適用し、押下時に既存 `CredentialEditActivity.newIntent(this)` 起動と等価なナビゲーションを行う（押下挙動自体は既存 FAB と統一できる場合は統合してよい）
5. While `state.emptyKind` が `EmptyKind.NoMatch` である場合, the empty state view shall 既存挙動どおり `@string/credential_list_empty_no_match` を見出しとして表示し（ヒーロー描画と CTA は不要）、Initial 用のヒーロー / CTA は描画しない

### Requirement 9: more (︙) overflow メニューのトークン整合

**Objective:** As a エンドユーザー, I want per-row overflow メニュー（複製等）のアイコン・タッチ領域が他要素と視覚的に整合していること, so that 機能の場所が変わったと誤認しない

#### Acceptance Criteria

1. The per-row overflow button shall アイコン色を `@color/kn_text_3` で tint する
2. The per-row overflow button shall タッチターゲットを最低 48dp × 48dp 維持する（既存 `minWidth` / `minHeight` の 48dp 規定を保持する）
3. The per-row overflow button shall 既存の `btn_overflow` ID と `selectableItemBackgroundBorderless` のリップル挙動を保持する

### Requirement 10: 既存機能・配線の不変

**Objective:** As a 既存ユーザー, I want UI の見た目変更によって検索・フィルター・並び替え・「最近使った」・複製・削除・編集遷移の機能が壊れないこと, so that 本 Issue のリリース前後で機能差分を意識せずに利用できる

#### Acceptance Criteria

1. The Credential List screen shall 既存の View ID（`toolbar` / `input_search` / `layout_search` / `chip_group_filters` / `chip_signature_matched` / `chip_signature_missing` / `btn_sort` / `recent_header` / `recent_recycler` / `recycler` / `empty_view` / `fab_add` / `text_label` / `text_subtitle` / `btn_overflow` / `card_recent` / `text_recent_label` / `text_recent_username`）をすべて保持する
2. When ユーザーが検索バーに文字列を入力したとき, the Credential List screen shall 既存どおり `CredentialListViewModel.onQueryChanged()` を呼び出してインクリメンタル絞り込みを実行する
3. When ユーザーが任意のフィルター chip を選択 / 解除したとき, the Credential List screen shall 既存どおり `CredentialListViewModel.onFilterChanged()` を呼び出してフィルタを更新する
4. When ユーザーが並び替えボタン (`btn_sort`) を押下したとき, the Credential List screen shall 既存どおり PopupMenu を表示し、選択された `CredentialSortOrder` で `CredentialListViewModel.onSortChanged()` を呼び出す
5. When ユーザーが per-row overflow ボタン (`btn_overflow`) を押下したとき, the Credential List screen shall 既存どおり PopupMenu を表示し、複製 (`R.id.action_duplicate`) 等の既存メニュー項目を提示する
6. When ユーザーが任意の行をタップ / 長押ししたとき, the Credential List screen shall 既存どおり編集画面遷移 / 削除確認ダイアログをそれぞれ起動する
7. The Credential List screen shall Issue #9 / Issue #10 で定義されたログポリシー（NFR 1.2: 検索クエリ / ユーザー名 / ラベル / パッケージ名の平文を SafeLogger に流さない）を変更しない
8. The Credential List screen shall `CredentialListAdapter` / `RecentlyUsedCarouselAdapter` の `DiffUtil.ItemCallback` 比較対象（id / label / username / packageName / updatedAt / signatureSha256 / lastUsedAt）を本 Issue で変更しない

### Requirement 11: ライト / ダーク両モードの描画整合

**Objective:** As a エンドユーザー, I want システムテーマ（Light / Dark）の切り替え後も配色が破綻せず読みやすいこと, so that 端末設定を変えても KeyNest の体感品質が落ちない

#### Acceptance Criteria

1. While 端末のシステム設定がライトモードである場合, the Credential List screen shall `values/colors.xml` の `kn_*` セマンティックトークンを解決する
2. While 端末のシステム設定がダークモードである場合, the Credential List screen shall `values-night/colors.xml` の `kn_*` セマンティックトークンを解決する
3. The Credential List screen shall ライト / ダーク両モードでカード行のタイトルテキスト（`@color/kn_text`）とカード背景（`@color/kn_surface`）のコントラスト比が WCAG 2.1 AA の本文最小 4.5:1 以上となる
4. The Credential List screen shall ライト / ダーク両モードで chip outline・カード outline・アイコン tint 等の非文字要素と背景のコントラスト比が WCAG 2.1 AA の非文字最小 3:1 以上となる Phase 1 で定義済みのトークン値を使用する（Phase 1 NFR 1.2 で確認事項に挙げられた `kn_border_strong` 単独のコントラスト 1.4:1 は強調 outline 用途では使わない方針を踏襲する）

## Non-Functional Requirements

### NFR 1: ビルドと既存テスト

1. When `./gradlew :app:assembleDebug` を CI またはローカルで実行したとき, the Android build pipeline shall 当該タスクを成功（exit code 0）で終了する
2. When 既存の単体テストスイート (`./gradlew :app:testDebugUnitTest`) を本 Issue の変更後に実行したとき, the Android test runner shall Phase 1 完了時点で成功していたテスト（Phase 1 `impl-notes.md` の 279 件中 274 件成功分）をすべて成功させる
3. The Credential List screen shall 既存の `Material3ThemeMigrationTest` 8 件および `FontTypefaceWiringTest` 9 件の `Theme.KeyNest` / `TextAppearance.KeyNest.*` の structural pin に違反する変更を行わない（Phase 1 確認事項 2 の延長として、本 Issue では 13 alias 系統の TextAppearance を撤去しない）

### NFR 2: アクセシビリティ

1. The Credential List screen shall すべてのタップ可能要素（per-row overflow / sort / chip / FAB / 行全体）について最小タッチサイズ 48dp × 48dp（`spec.md` §10 の 44dp 視覚 + 4dp 余白規定を満たす dp）を維持する
2. The Credential List screen shall アイコンのみのボタン（per-row overflow / sort）について、TalkBack 用 `contentDescription` を既存どおりローカライズ済み文字列で設定する
3. The Credential List screen shall ライト / ダーク両モードで本文最小 4.5:1 / 非文字最小 3:1 のコントラスト基準を満たすトークン値を使用する（Req 11.3 / 11.4 の再掲）

### NFR 3: ローカライズ

1. The Credential List screen shall 本 Issue で新規に表示するテキスト（署名 chip 等）について、英語デフォルト (`values/strings.xml`) と日本語 (`values-ja/strings.xml`) の双方に対応するキーを用意する（Phase 1 確認事項 1 の派生として、`signature_match` / `signature_missing` の英語デフォルト追加を本 Issue のスコープに含む）

## Out of Scope

- 検索 / フィルター / 並び替え / 「最近使った」のロジック変更（Issue #9 で実装済み。本 Issue は表層 UI のみ）
- 「最近使った」セクションの新規データ取得・サンプルデータ投入（`last_used_at` カラムは Issue #9 で追加済み、本 Issue は既存 `ObserveRecentlyUsedUseCase` の出力を表示するだけ）
- per-credential のアイコンタイル背景カラー割り当てロジック（`SAMPLE_CREDS` の `color` フィールドに相当する出し分け）— 本 Issue では `@color/kn_blue_500` 単色フォールバックに固定し、本格的なカラー割り当ては別 Issue
- パスワード強度モデルのドメイン追加（現在 `Credential` には強度フィールドが無いため、本 Issue では強度バーは「強度が判定できる場合のみ表示」する。強度算出ロジックの実装は別 Issue）
- アイコンタイルに表示するレターアイコン / SVG アイコンの自動選定ロジック（JSX `SAMPLE_CREDS.letter` に相当する 1 文字頭文字を本 Issue で導入するか、空タイルにするかは設計判断で決定し、要件としては「44dp / r12 / `kn_blue_500` 背景」のみ規定する）
- アプリバー（Toolbar）のタイトル / アイコンレイアウト変更（JSX `ListAppBar` の「KeyNest eyebrow + Vault title + count」表現の取り込みは Phase 2 別 Issue で扱う候補。本 Issue では既存 MaterialToolbar の見た目を維持してよい）
- `Material3ThemeMigrationTest` の structural pin 緩和（Phase 1 確認事項 2 として別 Issue 候補に挙がっている。本 Issue で test 自体は書き換えない）
- `dataset_presentation.xml` / `package_picker_bottom_sheet.xml` / `credential_edit_activity.xml` 等、他 Phase 2 画面の整合（`mapping.md` §5 の他行は別 Issue）
- Compose 化（既存 View ベースのレイアウト XML 改修にとどめる）
- アニメーション（リップル以外の独自トランジション・モーション設計）
- パッケージアイコンの自動取得（PackageManager 経由でアプリアイコンを取得する機能は別 Issue）

## Open Questions

- Issue 本文 DoD では「more (︙) overflow メニュー（Issue #9 で実装済み）」と記載されているが、JSX `ScreenListPopulated` の `CredCard` 内 more ボタンは行右端 1 つに対し、現状実装は per-row `btn_overflow` のみ存在する。本 Issue で行レベルの more ボタンと、行カード外側のアプリバー overflow（既存 `R.menu.credential_list_menu`）を 1 つに統合するかどうかは要件上は触れず、Architect 判断としてよいか
- 強度バーの表示条件について、`Credential` ドメインに強度フィールドが無い現状で「常に非表示」とするか、JSX モックの見栄えを維持するため「ダミー固定値（例: medium）で常に表示」とするかは設計判断が必要。本 requirements は Req 6.5 で「強度が判定できない場合は非表示」を採用しているが、UI モックとの視覚的近似を重視するなら逆の選択もあり得る。Architect / 人間レビュワーに確認したい
- 空状態 (`ScreenListEmpty`) のヒーロー描画（JSX `KNMark size={92}` + ぼかし円）について、Phase 1 で導入されたアダプティブランチャー drawable (`ic_launcher_foreground.xml` 等) を流用するか、新規に ScreenListEmpty 専用の vector drawable を起こすかは設計判断としてよいか
