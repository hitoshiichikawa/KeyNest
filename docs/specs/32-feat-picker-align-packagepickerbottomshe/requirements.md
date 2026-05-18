# Requirements Document

## Introduction

KeyNest の `PackagePickerBottomSheet`（レイアウト `app/src/main/res/layout/package_picker_bottom_sheet.xml`）は、
現状「タイトル + プレーンな RecyclerView の 1 行（label\nパッケージ名）」のみで、
`design/screens/screens-2.jsx` の `ScreenPicker` モックで定義された KeyNest 固有の
ビジュアル仕様（drag handle / タイトル + サブタイトル / 検索バー / 「業務でよく使う」「すべてのアプリ」の
2 セクション分割 / アイコンタイル付き行 / 「手動入力」フォールバック行）と乖離している。
本 Issue (#32) は Phase 2（`design/android-assets/mapping.md` §5 の `ScreenPicker` 行）の 4 件目として、
Phase 1 (#28) で `app/src/main/res/` に取り込まれた `@color/kn_*` / `@dimen/kn_*` /
`@style/Widget.KeyNest.*` / `@style/Text.KeyNest.*` リソースを既存レイアウトに貼り込み、
JSX モックと視覚的に整合させる。インストール済みアプリ取得・選択結果のコールバック配線
（`onPicked: (String) -> Unit`）・`Dispatchers.IO` 上でのリスト取得など、既存の機能ロジックは
そのまま維持する。「業務でよく使う」セクションの推奨判定ロジックは本 Issue の対象外であり、
SAMPLE データ（固定の代表的な業務アプリ一覧）で表示する。

## Requirements

### Requirement 1: Bottom sheet 全体のシェル

**Objective:** As a エンドユーザー, I want アプリ選択シートの外形・地色・角丸が KeyNest 設計トークンと一致していること, so that 他画面（クレデンシャル一覧 / 編集）と矛盾しないブランド体験で操作できる

#### Acceptance Criteria

1. The Package Picker bottom sheet shall ルート背景色として Material3 attribute `?attr/colorSurface` または `@color/kn_bg_elev` 相当のセマンティックトークンを解決する（直接 `#` 指定や Material3 既定解決を行わない）
2. The Package Picker bottom sheet shall 上端の角丸として `@dimen/kn_r_sheet`（28dp）相当を適用する
3. The Package Picker bottom sheet shall 内部の左右パディングに `@dimen/kn_list_padding_h`（16dp）または `@dimen/kn_screen_padding_h`（20dp）を、`design/spec.md` §5 のスペーシングに沿って適用する
4. The Package Picker bottom sheet shall 既存の `BottomSheetDialogFragment` 起動経路（`PackagePickerBottomSheet.show(manager, onPicked)`）を保持する

### Requirement 2: Drag handle（つかみハンドル）

**Objective:** As a エンドユーザー, I want シート上端に物理的に「下に引っ張れる」ことが分かるハンドルが表示されること, so that シートを閉じる動作を直感的に発見できる

#### Acceptance Criteria

1. The Package Picker bottom sheet shall シート上端中央に drag handle を 1 つ表示する
2. The drag handle shall 幅 `@dimen/kn_handle_w`（36dp）/ 高さ `@dimen/kn_handle_h`（4dp）で描画する
3. The drag handle shall 背景色に `@color/kn_ink_200` 相当（Issue 本文 DoD で指定されたトークン）を適用する
4. The drag handle shall 角丸として `@dimen/kn_r_pill`（999dp）を適用する
5. The drag handle shall シート上端からの上下マージンに `design/spec.md` §8 の "padding: 12 0 24" を満たす値（上 12dp 程度 / 下 14dp 程度）を適用する

### Requirement 3: タイトル + サブタイトル

**Objective:** As a エンドユーザー, I want シート上部に「アプリを選択」「署名情報も同時に取得します」というタイトルと補足文が JSX モックと同じトーンで表示されること, so that このシートで何を選ぶか・選択の副作用（署名取得）を理解した上で操作できる

#### Acceptance Criteria

1. The Package Picker bottom sheet shall drag handle の直下にタイトル行 + サブタイトル行を 2 段で表示する
2. The Package Picker title shall TextAppearance に `@style/Text.KeyNest.TitleM`（19sp / weight 800）相当を適用する
3. The Package Picker title shall テキスト色に `@color/kn_text`（または `?attr/colorOnSurface` 経由）を適用する
4. The Package Picker title shall 文字列リソース `@string/package_picker_title` を表示する
5. The Package Picker subtitle shall TextAppearance に `@style/Text.KeyNest.BodyS`（13sp / weight 500）相当を適用する
6. The Package Picker subtitle shall テキスト色に `@color/kn_text_2`（または `?attr/colorOnSurfaceVariant` 経由）を適用する
7. The Package Picker subtitle shall 文字列リソース `@string/package_picker_subtitle` を表示する
8. The Package Picker bottom sheet shall タイトル行右端に閉じる × ボタンを 1 つ配置し、`contentDescription` をローカライズ済み文字列（既存の閉じる × アイコン規約に倣う）で設定する
9. When ユーザーが閉じる × ボタンを押下したとき, the Package Picker bottom sheet shall `dismiss()` を呼び出してシートを閉じる

### Requirement 4: 検索バー

**Objective:** As a エンドユーザー, I want インストール済みアプリ一覧をアプリ名・パッケージ名で絞り込める検索バーが見えること, so that 数十〜数百のインストール済みアプリから目的のアプリを素早く特定できる

#### Acceptance Criteria

1. The Package Picker bottom sheet shall タイトル + サブタイトルの直下に検索バーを 1 つ表示する
2. The Package Picker search bar shall コンテナ背景色に `@color/kn_ink_50`（または `@color/kn_surface_2` 経由のセマンティックトークン）を適用する
3. The Package Picker search bar shall コンテナ角丸として `@dimen/kn_r_sm`（12dp）を適用する
4. The Package Picker search bar shall 左端に検索アイコン（虫眼鏡）を配置し、tint を `@color/kn_text_3` 相当で描画する
5. The Package Picker search bar shall プレースホルダー文字列として `@string/package_picker_search_hint`（または同等の "アプリ名・パッケージ名で絞り込み" 文字列）を `@color/kn_text_3` で表示する
6. The Package Picker search bar shall 入力テキストの TextAppearance に `@style/Text.KeyNest.BodyS`（13sp / weight 500）または `@style/Text.KeyNest.Body`（14sp / weight 600）相当を適用する
7. When ユーザーが検索バーに文字を入力したとき, the Package Picker bottom sheet shall 入力文字列（前方一致または部分一致）でアプリ名およびパッケージ名のリストを絞り込む
8. While 検索バーが空文字列である場合, the Package Picker bottom sheet shall フィルタを適用せず、全件を「業務でよく使う」「すべてのアプリ」セクションに振り分けて表示する
9. If 絞り込み結果が 0 件である場合, the Package Picker bottom sheet shall 「該当アプリなし」相当のメッセージ（または手動入力フォールバック行のみ）をユーザーに見える形で提示する

### Requirement 5: 「業務でよく使う」セクション

**Objective:** As a エンドユーザー, I want 業務で頻出する代表的アプリが画面冒頭にまとまって表示されること, so that 候補を一覧から探す手間なく素早く選択できる

#### Acceptance Criteria

1. The Package Picker bottom sheet shall 検索バーの下に「業務でよく使う」セクションを 1 つ配置する
2. The Package Picker "業務でよく使う" section header shall TextAppearance に `@style/Text.KeyNest.Eyebrow`（11sp / weight 700 / letter-spacing 0.08em / UPPERCASE）相当を適用する
3. The Package Picker "業務でよく使う" section header shall テキスト色に `@color/kn_text_3` を適用する
4. The Package Picker "業務でよく使う" section header shall 文字列リソース `@string/package_picker_section_used` を表示する
5. The Package Picker "業務でよく使う" section shall 本 Issue では SAMPLE データ（固定の代表業務アプリ一覧）を 3〜5 件描画する
6. The Package Picker "業務でよく使う" section shall SAMPLE データの行レイアウトを Requirement 7 の行レイアウト規定に従って描画する
7. If SAMPLE データ件数が 0 件である（実装側の判断で SAMPLE 表示しない場合）場合, the Package Picker bottom sheet shall 当該セクション（ヘッダーと行の両方）を表示しない

### Requirement 6: 「すべてのアプリ」セクション

**Objective:** As a エンドユーザー, I want 端末にインストール済みのアプリ全件をアルファベット順で確認できる, so that 業務アプリでないものも含めて目的のアプリを選べる

#### Acceptance Criteria

1. The Package Picker bottom sheet shall 「業務でよく使う」セクションの下に「すべてのアプリ」セクションを 1 つ配置する
2. The Package Picker "すべてのアプリ" section header shall TextAppearance / テキスト色 / 文字間 / UPPERCASE を Requirement 5 のセクションヘッダー（5.2 / 5.3）と同一仕様で描画する
3. The Package Picker "すべてのアプリ" section header shall 文字列リソース `@string/package_picker_section_all` を表示する
4. The Package Picker "すべてのアプリ" section shall 既存 `PackagePickerBottomSheet.loadInstalledApps()` の挙動（`PackageManager.getInstalledApplications(0)` を `Dispatchers.IO` で取得し、`label.lowercase()` で昇順ソート）を変更しない
5. The Package Picker "すべてのアプリ" section shall 取得したインストール済みアプリ一覧を Requirement 7 の行レイアウト規定に従って描画する
6. While インストール済みアプリの取得が非同期で進行中である場合, the Package Picker bottom sheet shall 既存の挙動（取得完了後にリスト差し替え）を変更しない（プレースホルダー / スピナーの追加は本 Issue の対象外）

### Requirement 7: 行レイアウト（共通）

**Objective:** As a エンドユーザー, I want 各アプリ行がアイコンタイル + アプリ名 + パッケージ名で構成され、JSX モックの視覚仕様と一致していること, so that どのアプリを選ぼうとしているか視覚的に識別でき、誤タップを防げる

#### Acceptance Criteria

1. The Package Picker row shall 左端に 32dp × 32dp（`@dimen/kn_icon_tile_sm`）相当のアイコンタイルを配置する
2. The Package Picker row icon tile shall 角丸として `@dimen/kn_r_sm`（12dp）相当（JSX `radius=10` と KeyNest トークンの最寄り値）を適用する
3. The Package Picker row icon tile shall 背景色に `@color/kn_blue_500` 単色フォールバックを適用する（per-package の動的カラー割り当ては本 Issue のスコープ外）
4. The Package Picker row shall アイコンタイル右側にアプリ名（label）行とパッケージ名（packageName）行を 2 段で表示する
5. The Package Picker row app name shall TextAppearance に `@style/Text.KeyNest.Body`（14sp / weight 600）または `@style/Text.KeyNest.TitleS`（16sp / weight 700）相当を適用し、1 行 ellipsis を維持する
6. The Package Picker row package name shall TextAppearance に `@style/Text.KeyNest.Mono`（JetBrains Mono / 12sp / weight 500）相当を適用する
7. The Package Picker row package name shall テキスト色に `@color/kn_text_3` を適用し、1 行 ellipsis を維持する
8. The Package Picker row shall 行全体の縦パディングを `@dimen/kn_space_3`（12dp）程度、アイコンとテキスト間の横ギャップを `@dimen/kn_space_3`（12dp）相当で適用する
9. The Package Picker row shall 行全体の角丸として `@dimen/kn_r_sm`（12dp）相当を適用する（タップ時のリップル領域と整合させる）
10. When ユーザーが行を押下したとき, the Package Picker bottom sheet shall 既存の `onPicked(packageName)` コールバックを 1 回だけ呼び出し、続けて `dismiss()` を呼び出してシートを閉じる
11. The Package Picker row shall 最小タッチサイズ 48dp × 48dp（`design/spec.md` §10 の 44dp 視覚 + 4dp 余白）を維持する

### Requirement 8: 「手動入力」フォールバック行

**Objective:** As a エンドユーザー, I want 一覧に表示されない（または非ランチャーアプリの）パッケージ名を直接手で入力できるフォールバック動線が常に画面下部に見えること, so that リストに出ないアプリでも手動でパッケージ名を指定して保存できる

#### Acceptance Criteria

1. The Package Picker bottom sheet shall シート最下部に「手動入力」フォールバック行を 1 つ配置する
2. The Package Picker manual entry row shall リストスクロール領域の外側（リストをスクロールしても常に画面下部に固定で見える位置）に配置するか、リストの末尾に最終行として配置する。いずれの場合も全アプリ行よりも下に表示する
3. The Package Picker manual entry row shall 背景色に `@color/kn_blue_50` を適用する（Issue 本文 DoD で指定されたトークン）
4. The Package Picker manual entry row shall テキスト色に `@color/kn_primary` 相当（または `?attr/colorPrimary` 経由）を適用する
5. The Package Picker manual entry row shall TextAppearance に `@style/Text.KeyNest.Body`（14sp / weight 600）または `@style/Text.KeyNest.LabelL`（15sp / weight 700）相当を適用する
6. The Package Picker manual entry row shall 文字列リソース `@string/package_picker_manual` を表示する
7. When ユーザーが「手動入力」フォールバック行を押下したとき, the Package Picker bottom sheet shall パッケージ名を手で入力するためのテキスト入力フロー（インラインの EditText 展開、または手動入力用の遷移）に進める
8. When 手動入力フローでユーザーが確定したとき, the Package Picker bottom sheet shall 入力されたパッケージ名を引数に既存の `onPicked(packageName)` コールバックを 1 回だけ呼び出し、続けて `dismiss()` を呼び出す
9. If 手動入力されたパッケージ名が空文字列または明らかに不正な形式（ピリオドを含まない、空白を含む等）である場合, the Package Picker bottom sheet shall `onPicked` を呼び出さず、ユーザーに見える形でエラー（フィールド下の error テキストまたは Snackbar）を提示する

### Requirement 9: 既存機能・配線の不変

**Objective:** As a 既存ユーザー, I want UI の見た目変更によってアプリ選択のコールバック・インストール済みアプリ取得・スレッド分離・ライフサイクルの挙動が壊れないこと, so that 本 Issue のリリース前後で機能差分を意識せずに利用できる

#### Acceptance Criteria

1. The Package Picker bottom sheet shall 既存のクラス名 `com.example.keynest.ui.edit.PackagePickerBottomSheet` と `companion object` の `show(manager: FragmentManager, onPicked: (String) -> Unit)` シグネチャを保持する
2. The Package Picker bottom sheet shall 既存の RecyclerView ID `@id/recycler` の参照経路を保持する（リスト本体を RecyclerView 以外に置き換える設計判断を採る場合でも、`PackagePickerBottomSheet.onViewCreated()` の代替経路で既存呼び出し元から見て選択結果のコールバックが等価に動作すること）
3. The Package Picker bottom sheet shall 既存の `Dispatchers.IO` 上でのインストール済みアプリ取得（`PackageManager.getInstalledApplications(0)`）を変更しない
4. The Package Picker bottom sheet shall 既存の `onDestroyView()` でのバインディング解放・adapter クリアを変更しない
5. When `CredentialEditActivity.onPickInstalledAppClicked()`（既存呼び出し元）から `PackagePickerBottomSheet.show()` が呼び出されたとき, the Package Picker bottom sheet shall 既存どおりシートを表示し、選択結果を呼び出し元の `input_package` 入力フィールドに反映できる
6. The Package Picker bottom sheet shall 既存テスト（`PackagePickerBottomSheet` に関する単体テストが存在する場合）の前提（API シグネチャ・ライフサイクル動作）を破壊しない

### Requirement 10: ライト / ダーク両モードの描画整合

**Objective:** As a エンドユーザー, I want システムテーマ（Light / Dark）の切り替え後も配色が破綻せず読みやすいこと, so that 端末設定を変えても KeyNest の体感品質が落ちない

#### Acceptance Criteria

1. While 端末のシステム設定がライトモードである場合, the Package Picker bottom sheet shall `values/colors.xml` の `kn_*` セマンティックトークンを解決する
2. While 端末のシステム設定がダークモードである場合, the Package Picker bottom sheet shall `values-night/colors.xml` の `kn_*` セマンティックトークンを解決する
3. The Package Picker bottom sheet shall ライト / ダーク両モードで本文テキスト（タイトル / アプリ名 / パッケージ名 / セクションヘッダー / 手動入力行）と背景のコントラスト比が WCAG 2.1 AA の本文最小 4.5:1 以上となる Phase 1 で定義済みのトークン値を使用する
4. The Package Picker bottom sheet shall ライト / ダーク両モードで drag handle / 検索バー背景 / 行 outline 等の非文字要素と背景のコントラスト比が WCAG 2.1 AA の非文字最小 3:1 以上となる Phase 1 で定義済みのトークン値を使用する
5. The Package Picker manual entry row shall ダークモード時、`@color/kn_blue_50`（ライト用）に相当するダーク用トークン（`values-night/colors.xml` で定義済みの surface-tint 系色など）を使用してテキスト視認性を 4.5:1 以上に維持する

## Non-Functional Requirements

### NFR 1: ビルドと既存テスト

1. When `./gradlew :app:assembleDebug` を CI またはローカルで実行したとき, the Android build pipeline shall 当該タスクを成功（exit code 0）で終了する
2. When 既存の単体テストスイート (`./gradlew :app:testDebugUnitTest`) を本 Issue の変更後に実行したとき, the Android test runner shall Phase 1 / Phase 2 既存完了分（Issue #29 / #30 / #31）で成功していたテストをすべて成功させる
3. The Package Picker bottom sheet shall 既存の `Material3ThemeMigrationTest` および `FontTypefaceWiringTest` の `Theme.KeyNest` / `TextAppearance.KeyNest.*` の structural pin に違反する変更を行わない

### NFR 2: アクセシビリティ

1. The Package Picker bottom sheet shall すべてのタップ可能要素（閉じる × ボタン / 検索バー / 各アプリ行 / 手動入力行）について最小タッチサイズ 48dp × 48dp を維持する
2. The Package Picker bottom sheet shall アイコンのみのボタン（閉じる × / 検索アイコン）について、TalkBack 用 `contentDescription` をローカライズ済み文字列で設定する
3. The Package Picker bottom sheet shall ライト / ダーク両モードで本文最小 4.5:1 / 非文字最小 3:1 のコントラスト基準を満たすトークン値を使用する（Req 10.3 / 10.4 の再掲）
4. The Package Picker bottom sheet shall セクションヘッダー（「業務でよく使う」「すべてのアプリ」）について、TalkBack で section heading として読み上げられるよう `android:accessibilityHeading="true"` または同等の意味づけを適用する

### NFR 3: ローカライズ

1. The Package Picker bottom sheet shall 本 Issue で表示する全テキスト（タイトル / サブタイトル / 検索プレースホルダー / セクションヘッダー / 手動入力行 / エラーメッセージ）について、英語デフォルト (`values/strings.xml`) と日本語 (`values-ja/strings.xml`) の双方に対応する string キーを持つ
2. The Package Picker bottom sheet shall 新規 string キーを追加する場合は、英語 (`values/strings.xml`) と日本語 (`values-ja/strings.xml`) の両ロケールに同時追加する
3. While 端末のロケールが日本語に設定されている場合, the Package Picker bottom sheet shall `values-ja/strings.xml` に定義された訳語で全テキストを表示する

## Out of Scope

- 「業務でよく使う」セクションの推奨判定ロジック（端末利用履歴 / 業務アプリ辞書 / signature ベースのカテゴリ判定など、実データから「業務でよく使う」を導出する処理）— 本 Issue では SAMPLE データの固定表示にとどめ、推奨判定アルゴリズムの実装は別 Issue とする
- インストール済みアプリ取得の機能側（`PackageManager.getInstalledApplications(0)` の挙動・API 30+ の `<queries>` マニフェスト制限・ローダー分離など、既存 `loadInstalledApps()` の挙動）— 既に実装済みであり、本 Issue は UI 表層のみを扱う
- アプリアイコンの動的取得（`PackageManager.getApplicationIcon()` 経由でアプリ正規アイコンを Drawable として取得して表示する機能）— 本 Issue では `@color/kn_blue_500` 単色フォールバックに固定し、本格的なアイコン取得は別 Issue
- アイコンタイル背景色の per-package 出し分け（JSX `installed[i].c` に相当する動的カラー割り当て）— 本 Issue は単色フォールバックのみ
- アイコンタイル上のレターアイコン（JSX `IconTile.letter` に相当する 1 文字頭文字）の自動選定ロジック — 表示の有無・選定ルールは設計判断で決定し、要件としては「32dp / 角丸 12dp / `kn_blue_500` 背景」のみ規定する
- 検索の高度な機能（ファジー検索 / 並び順の重み付け / カテゴリフィルター chip など） — 本 Issue は前方一致または部分一致の単純絞り込みのみ
- 「業務でよく使う」と「すべてのアプリ」の選択行ハイライト（JSX `PickerRow` の `selected` 状態） — 既存のシートは選択即 dismiss であり、状態保持型の選択 UI は本 Issue の対象外
- Compose 化（既存 View ベースのレイアウト XML 改修にとどめる）
- アニメーション（シート展開アニメーション以外の独自トランジション・モーション設計）
- 他 Phase 2 画面の整合（`mapping.md` §5 の `ScreenDataset` / `ScreenUnlock` / `ScreenSettings` 行は別 Issue）

## Open Questions

- Issue 本文 DoD #1 では drag handle に `@style/Widget.KeyNest.DragHandle` 相当 / `@style/Widget.KeyNest.SearchField` 相当を適用するよう記載しているが、現状 `app/src/main/res/values/themes.xml` には `Widget.KeyNest.DragHandle` および `Widget.KeyNest.SearchField` スタイルが定義されていない。本 Issue 内で当該スタイルを新規定義する（Phase 1 リソース層への追補として）か、既存トークン (`@color/kn_ink_200` / `@color/kn_ink_50` / `@dimen/kn_handle_w` / `@dimen/kn_handle_h` / `@dimen/kn_r_pill` / `@dimen/kn_r_sm`) を個別に貼って同等視覚を実現するかは Architect 判断としてよいか
- Issue 本文 DoD #5 では「すべてのアプリ」セクションのソート順を規定していないが、既存 `loadInstalledApps()` は `label.lowercase()` 昇順ソートを行っている。本要件 6.4 ではこの既存挙動を維持する形にしているが、JSX モックでは "Microsoft Teams / Notion / Zendesk / BizReach / Redmine / Figma" の順（インデックス順）になっており、ソート規約の明確化が必要か（本要件では既存実装に従う方針）
- Issue 本文 DoD #4 では「業務でよく使う」セクションを SAMPLE 表示としているが、具体的な SAMPLE 対象アプリ一覧（JSX では Salesforce / Workday / Kintone の 3 件）と、それを「実在パッケージ名 + ハードコード文字列」「現在のインストール済みアプリ一覧から該当する場合のみ表示」「常に固定行として表示」のどの方針で実装するかは Architect 判断としてよいか
- Issue 本文 DoD #7 では「手動入力」フォールバック行を「`kn_blue_50` 背景 + テキスト入力遷移」と記載しているが、遷移先（インラインで EditText に展開 / 別ダイアログ起動 / 親 Activity に手動入力モードを通知して dismiss）の選定は Architect 判断としてよいか。本要件では「テキスト入力フローに進める」とのみ記述し、UI モダリティは設計判断に委ねている
- 検索バーは現状 `string` リソースとして `package_picker_search_hint` が `values/` / `values-ja/` のどちらにも存在しないため、本 Issue で新規追加が必要。同様に英語側に欠落している `package_picker_subtitle` / `package_picker_section_used` / `package_picker_section_all` / `package_picker_manual` も本 Issue 内で英語訳を追加する想定で問題ないか
- `Widget.KeyNest.BottomSheet` スタイル（Phase 1 で themes.xml に定義済み）を本シートのテーマに割り当てる方法（`BottomSheetDialogFragment.getTheme()` をオーバーライドして `Theme.Material3.Light.BottomSheetDialog` に `bottomSheetStyle` を差し込む等）の具体は Architect / Developer 判断としてよいか
