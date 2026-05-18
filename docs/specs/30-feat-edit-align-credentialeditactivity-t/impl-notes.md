# Issue #30 — CredentialEditActivity を `design/screens-1.jsx` に揃える実装ノート

## 概要

Phase 1 (#28) で `app/src/main/res/` に取り込まれた KeyNest デザイントークン
(`@color/kn_*` / `@dimen/kn_*` / `@style/Widget.KeyNest.*` / `@style/Text.KeyNest.*`)
を `credential_edit_activity.xml` に貼り込み、JSX モック
(`design/screens/screens-1.jsx` の `ScreenEdit`, 314-364 行) の視覚仕様と
整合させた. 同時に Issue #5/#7 で導入予定だった編集画面からの削除動線
(削除確認ダイアログ + `DeleteCredentialUseCase` 呼び出し + Snackbar 失敗通知)
を `CredentialEditViewModel` / `CredentialEditActivity` に実装した.

既存の保存・更新・パッケージピッカー起動・署名取得・詳細設定 (Issue #14)
折りたたみ・SHA-256 hex コピー・クレデンシャル ID トグル・CharArray 安全
取扱 (NFR 1.3) のロジックは一切変更していない. View ID 27 件 (Req 10.1
列挙) はすべて温存され、Activity の `findViewById<*>()` / ViewBinding 参照
経路を破壊していない.

## コミット一覧

| コミット | サブジェクト |
|---|---|
| `81604a0` | feat(edit): add credential edit screen strings for Issue #30 |
| `1ee61e6` | feat(ui): add KeyNest edit-screen drawable primitives (Issue #30) |
| `ec907d0` | feat(ui): add Widget.KeyNest.Button.Destructive style (Issue #30) |
| `1160eee` | fix(ui): remove XML-comment forbidden double-dash sequences in themes.xml |
| `c07ca07` | feat(edit): add delete pipeline to CredentialEditViewModel (Issue #30) |
| `77525e5` | fix(ui): remove XML-comment forbidden double-dash sequences in edit drawables |
| `87a7f21` | feat(edit): align CredentialEditActivity to design/screens-1.jsx (Issue #30) |
| `ebe8fbb` | test(edit): align CredentialEditAdvancedStateTest to new 4-arg ViewModel |
| `f2de32a` | test(edit): pin design-token references on credential_edit_activity.xml |
| `10680cd` | fix(edit): keep explicit jetbrains_mono fontFamily on value_signature_hex and value_credential_id |

## 変更ファイル一覧

### 新規追加 (6 ファイル)

- `app/src/main/res/drawable/ic_close_24.xml`
  — アプリバーの閉じる × アイコン (24dp / 2dp stroke).
- `app/src/main/res/drawable/ic_chevron_down_24.xml`
  — 詳細設定 chevron.
- `app/src/main/res/drawable/kn_advanced_section_bg.xml`
  — 詳細設定パネルの `kn_surface_2 + r14` 背景 (ShapeDrawable).
- `app/src/main/res/drawable/kn_advanced_row_divider.xml`
  — 行間 1dp `kn_border` 分割線 (ShapeDrawable).
- `app/src/test/java/com/example/keynest/resources/CredentialEditLayoutTokensTest.kt`
  — Issue #30 用の layout XML source-level pin テスト 16 件.
- `docs/specs/30-feat-edit-align-credentialeditactivity-t/impl-notes.md`
  — 本ノート.

### 既存修正 (8 ファイル)

- `app/src/main/res/layout/credential_edit_activity.xml`
  — 全面書き換え. ルート背景 / 横余白 / アプリバー / ターゲットアプリ
    カード / 4 つの TextInputLayout / パスワード強度バー / 詳細設定
    セクション / 削除 CTA / 保存 CTA を JSX `ScreenEdit` に揃える.
    既存 ID 27 件は全件保持.
- `app/src/main/java/com/example/keynest/ui/edit/CredentialEditActivity.kt`
  — 削除確認ダイアログ (`showDeleteConfirmation`), 署名 chip render
    (`renderSignatureChip`), 編集モード時の削除ボタン visibility,
    ターゲットアプリカードへの label/package mirror, パッケージ
    ピッカー callback の card 同期, `DeleteFailed` 状態の Snackbar 表示
    を追加. 既存の onSaveClicked / takePasswordCharArray / renderState
    (Saved / Saving / Idle / FieldError / Error) / renderAdvancedDetails /
    SafeLogger ポリシーは無変更.
- `app/src/main/java/com/example/keynest/ui/edit/CredentialEditViewModel.kt`
  — Factory コンストラクタを 4 引数に拡張し `DeleteCredentialUseCase` を
    注入. `State.DeleteFailed` を追加し `delete(credentialId)` メソッドで
    成功時は既存 `_navigation` SharedFlow に emit / 失敗時は
    `_state = DeleteFailed` を発行. SafeLogger.info / error はパスワード
    plaintext を含まないことを引き続き満たす.
- `app/src/test/java/com/example/keynest/ui/CredentialEditViewModelTest.kt`
  — ViewModel コンストラクタ更新に追従 + 削除 3 ケース追加
    (`delete_onSuccess_emitsNavigationEvent_andLeavesStateIdle` /
     `delete_onStorageFailure_setsDeleteFailedState_andDoesNotEmitNavigation` /
     `delete_onMissingId_treatsAsSuccess_perRepoIdempotency`).
- `app/src/test/java/com/example/keynest/ui/edit/CredentialEditAdvancedStateTest.kt`
  — newViewModel ファクトリに DeleteCredentialUseCase 引数を追加 (本体無変更).
- `app/src/main/res/values/strings.xml` / `app/src/main/res/values-ja/strings.xml`
  — Issue #30 用の英語デフォルト + 日本語訳を 9 キー追加
    (`credential_edit_password_label` / `credential_edit_advanced_chevron_a11y` /
     `credential_edit_target_app_change_a11y` / `credential_edit_close_a11y` /
     `credential_edit_delete_confirm_title` /
     `credential_edit_delete_confirm_message` /
     `credential_edit_delete_confirm_positive` /
     `credential_edit_delete_confirm_negative` /
     `credential_edit_delete_failed`).
- `app/src/main/res/values/themes.xml`
  — `Widget.KeyNest.Button.Destructive` スタイル (OutlinedButton + 透過 +
    kn_border 1dp + kn_danger text + kn_button_height + r14 + kn_danger_soft
    ripple) を新規追加. 既存 `Widget.KeyNest.*` 構造には触れていない.

## 新規追加コンポーネントの位置づけ

### 新規 drawable

- `ic_close_24.xml` — アプリバーの閉じる × アイコン. ベクター. consumer
  側で `app:tint="@color/kn_text"` を指定する形 (Req 2.2). 既存
  `@android:drawable/ic_menu_close_clear_cancel` に代わって採用.
- `ic_chevron_down_24.xml` — 詳細設定 chevron. ベクター. consumer 側で
  `app:tint="@color/kn_text_3"` を指定. 既存 Issue #14 の chevron rotation
  制御 (`chevronRotationFor`) はそのまま利用.
- `kn_advanced_section_bg.xml` — 詳細設定パネル背景. ShapeDrawable
  (`kn_surface_2` + `kn_r_input` = 14dp). dark mode は kn_surface_2 が
  values-night で kn_ink_800 に解決されるので自動対応.
- `kn_advanced_row_divider.xml` — 詳細設定の各行間 1dp 区切り線. 単純な
  `kn_border` ShapeDrawable.

### 新規 style

- `Widget.KeyNest.Button.Destructive` (themes.xml)
  - parent: `Widget.Material3.Button.OutlinedButton`
  - strokeColor: `@color/kn_border` (1dp)
  - cornerRadius: `@dimen/kn_r_input` (14dp)
  - minHeight: `@dimen/kn_button_height` (52dp)
  - textAppearance: `@style/Text.KeyNest.LabelL` (15sp / weight 700)
  - textColor: `@color/kn_danger`
  - rippleColor: `@color/kn_danger_soft`

  Issue #30 の Open Question #1 (削除ボタンを `Widget.KeyNest.Button.
  Destructive` 新規定義するか `?attr/colorError` を引いた MaterialButton
  にするか) への暫定回答として、新規 style を定義. 理由:
  - layout XML の attribute を最小化できる (style 1 つを参照するだけ)
  - JSX `ScreenEdit` の削除ボタン (`background: transparent`, `border:
    '1px solid var(--border)'`, `borderRadius: 14`, `color: 'var(--kn-danger)'`,
    `font: '700 14px/1 var(--kn-font-ui)'`) と 1:1 に対応する
  - 他画面 (削除確認ダイアログ内の "削除" ボタンや、将来の
    Settings / DangerZone 系画面) で再利用しやすい
  - `?attr/colorError` 経由案も同等の視覚を実現できるが、destructive 系の
    ripple / text の 1 箇所変更で済むよう style 化を採用

### 新規 string キー (en/ja 対称)

| キー | 用途 |
|---|---|
| `credential_edit_password_label` | パスワード eyebrow ラベル ("PASSWORD" / "パスワード") |
| `credential_edit_advanced_chevron_a11y` | 詳細設定 chevron の contentDescription |
| `credential_edit_target_app_change_a11y` | ターゲットアプリ「変更」ボタンの contentDescription |
| `credential_edit_close_a11y` | アプリバー閉じる × アイコンの contentDescription |
| `credential_edit_delete_confirm_title` | 削除確認ダイアログのタイトル |
| `credential_edit_delete_confirm_message` | 削除確認ダイアログの本文 |
| `credential_edit_delete_confirm_positive` | 削除確認ダイアログの肯定ボタン文言 |
| `credential_edit_delete_confirm_negative` | 削除確認ダイアログの否定ボタン文言 |
| `credential_edit_delete_failed` | 削除失敗時 Snackbar 文言 |

en/ja 両方に同タイミングで追加 (NFR 3.1 / Issue #29 と同じパターン).

### 新規 dimen / color

なし. Phase 1 (#28) 取り込み済みの
`kn_screen_padding_h` / `kn_card_padding` / `kn_icon_tile_lg` / `kn_r_input` /
`kn_r_sm` / `kn_input_height` / `kn_button_height` / `kn_space_*` /
`kn_text` / `kn_text_2` / `kn_text_3` / `kn_surface_2` / `kn_border` /
`kn_danger` / `kn_danger_soft` / `kn_success` / `kn_warning` をすべて
直接参照. Issue #29 と同様、Phase 1 の取り込みが完備していたため新規追加
なしで完結.

## 確認事項への暫定回答 (PM 確定 requirements.md の Open Questions)

### Q1: 削除ボタンを `Widget.KeyNest.Button.Destructive` 新規定義するか `?attr/colorError` で実現するか

**暫定回答 (Developer 判断)**: 前者を採用. `Widget.KeyNest.Button.
Destructive` を `themes.xml` に新規定義し、`OutlinedButton` 親 + 透過
background + kn_border 1dp outline + kn_danger text + r14 角丸 +
kn_button_height (52dp) + Text.KeyNest.LabelL appearance + kn_danger_soft
ripple で実装した. 詳細は前述「新規 style」節を参照.

理由 (要約):
- JSX `ScreenEdit` の視覚仕様と 1:1 対応
- layout XML 側の attribute を最小化 (style 1 つ参照のみ)
- 将来の destructive 系 CTA 再利用が容易

### Q2: 強度バーを入力に追従させずモック近似 (常に "strong" 等) で良いか

**暫定回答 (Developer 判断)**: requirements.md Req 6.6 通り「強度判定
ロジックが未実装のためサンプル固定値で描画する」方針を実装. ただし
Issue #29 の `StrengthBar.setStrength(null)` の挙動 (View.GONE) を採用し、
**バーは描画されない (vertical space を消費しない)** 状態とした.

理由 (Issue #29 確認事項 #2 と同根拠):
- `Credential` ドメインモデルに `strength` フィールドが現状無く、
  追加すれば spec 範囲を超える
- 全行 / 編集画面で固定値 (medium 等) を表示すると **誤情報**
  (実際には不明) になる. セキュリティ製品としては「分からない」を
  明示する方が誠実
- 後続 Issue で実 strength 算出ロジックを入れたとき、ダミー表示と
  差分が視覚的に見えると **新機能感を演出できる**

`StrengthBar` widget そのものは Issue #29 で導入済み・テスト済みで、
Issue #30 では layout XML に 1 ノード追加するだけで再利用している.

派生 Issue 候補 (Issue #29 と同):
- `Credential.passwordStrength: PasswordStrength?` を domain に追加 + 算出
  ロジック (zxcvbn ベースなど) を入れる. その時点で本ファイル Activity 側
  `binding.strengthBarEdit.setStrength(null)` を実値に差し替える 1 行修正.

### Q3: 保存ボタンの配置 (アプリバー右側 vs 画面下部) はどちらを採用するか

**暫定回答 (Developer 判断)**: 画面下部に **`Widget.KeyNest.Button.
Primary`** で配置する (Req 9.1 の後者の選択肢を採用).

理由:
- 既存 `CredentialEditActivity.onCreate()` の `binding.btnSave.
  setOnClickListener { onSaveClicked() }` 配線をそのまま再利用できる
  (Req 9.5 / 10.2 の「既存 ID と配線を保持」を最小差分で満たす)
- 画面下部の primary CTA は Issue #29 の空状態 CTA と視覚的に統一感を
  生む (両画面で同じ `Widget.KeyNest.Button.Primary` を使用)
- JSX `ScreenEdit` のアプリバー右側「保存」テキストボタン (`color:
  'var(--primary)'` の Text Button) は別 Issue で取り込めるよう、本 Issue
  では layout XML の構造を **両方の選択肢で書き換え可能な形**に整えた
  (アプリバー右側に追加するなら `MaterialToolbar` 子要素として
  `MaterialButton style=Widget.KeyNest.Button.Text` を追加し、bottom の
  save ボタンを GONE にするだけで切替可能)

派生 Issue 候補: JSX ScreenEdit のアプリバー右側「保存」テキストボタンの
配置を取り込む別 Issue. layout のみの単一画面修正で済む.

### Q4: ターゲットアプリカードの表示アプリ名は `Credential.label` で差し支えないか

**暫定回答 (Developer 判断)**: はい. `Credential.label` を `tv_target_app_
label` に bind する形で実装した. 新規モードでは `credential_edit_title_new`
("新しいクレデンシャル" / "New credential") をプレースホルダ表示.

理由:
- `PackageManager.getApplicationLabel()` による正式アプリ名取得は
  Out of Scope (PackageManager 動的取得は別 Issue)
- `Credential.label` はユーザーが設定する表示用ラベルで、Autofill 候補や
  一覧画面でもアプリ名相当の表示として既に使われている (Issue #9)
- 編集中にユーザーが `input_label` を変更してもターゲットカードは
  load 時点の値で固定する (real-time mirror はしない) — 編集中の値が
  両方に同時表示されると視覚的に冗長になるため

派生 Issue 候補 (Out of Scope と同様):
- PackageManager 経由でアプリ名 / アイコン Drawable を動的取得する別
  Issue. 取得 cache + プレースホルダ fallback を入れることで安定動作する.

### Q5: ターゲットアプリカード内に「変更」ボタンを統合するか

**暫定回答 (Developer 判断)**: 統合した. `btn_pick_installed_app` を
ターゲットアプリカード内右端に配置し、`Widget.KeyNest.Button.Text` を
適用. 既存の click handler (`PackagePickerBottomSheet.show()`) は無変更.

旧レイアウトでは `btn_pick_installed_app` が `layout_package` (パッケージ
名 input) の直下に独立配置されていたが、本 Issue では:
- `layout_package` を visibility=gone に隠す (Req 3.9 / Out of Scope:
  「TextInputLayout として残すかは設計判断」)
- 視覚的なパッケージ名表示は `tv_target_app_package` (mono / kn_text_2)
- 「変更」動線は **ターゲットアプリカード内のテキストボタン** に集約

これは JSX `ScreenEdit` の最右側「変更」テキストボタンと完全に一致する.

派生 Issue 候補: なし (本 Issue 内で対応完了).

## 各 AC のテストカバレッジ

> **テスト戦略**: Issue #29 と同じく **source-level XML pin** を採用する.
> Visual rendering (e.g. 「カード角丸が 18dp で見える」) は instrumented UI
> test の領分なので本 Issue では扱わず、`@color/kn_*` / `@dimen/kn_*` /
> `@style/...` / `@+id/...` のレイアウト記述レベルで pin することで設計
> トークンの drift を検出可能にする. ViewModel の `delete()` フローは
> 純 JUnit + mockk で 3 ケース直接 verify.

### Requirement 1: 画面ルート・サーフェスの整合

| AC | テスト |
|---|---|
| 1.1 (`?attr/colorBackground` 解決) | `CredentialEditLayoutTokensTest.editLayout_bindsRootBackgroundToColorBackgroundAttr` |
| 1.2 (`kn_screen_padding_h` 20dp 適用) | `CredentialEditLayoutTokensTest.editLayout_appliesKnScreenPaddingHForScrollRegion` |
| 1.3 (`kn_space_*` を縦間隔に使用) | 全体的に layout XML 内で `@dimen/kn_space_*` を引いている. `layouts_doNotHardcodeHexColors` 系で間接担保 |
| 1.4 (values-night の上書き) | テーマ層の Phase 1 テスト (`Material3ThemeMigrationTest`) が解決経路を pin. レイアウトは `?android:attr/colorBackground` / `@color/kn_*` のみ参照し直接 hex を持たないことを `editLayout_doesNotHardcodeHexColors` が verify |

### Requirement 2: アプリバーの視覚仕様

| AC | テスト |
|---|---|
| 2.1 (TitleS titleAppearance) | `editLayout_appBarUsesTitleSAppearanceAndKnTextTintedCloseIcon` |
| 2.2 (close icon kn_text tint) | 同上 |
| 2.3 / 2.4 (edit / new でタイトル文字列切替) | `CredentialEditActivity.onCreate()` の `title = getString(...)` 分岐 (既存挙動、本 Issue 無変更). 個別 AC テスト無し |
| 2.5 (`toolbar` ID + setNavigationOnClickListener 維持) | `editLayout_preservesAllPreIssue30Ids` (ID) + Activity 側既存挙動 |

### Requirement 3: ターゲットアプリカードの視覚仕様

| AC | テスト |
|---|---|
| 3.1 (Widget.KeyNest.Card 外形 + kn_r_card 角丸) | `editLayout_targetAppCardUsesWidgetKeyNestCardAndKnIconTile` (`Widget.KeyNest.Card` style 参照を verify) |
| 3.2 (`kn_card_padding` 14dp) | `Widget.KeyNest.Card` style の `contentPadding` で設定済み + layout 内で paddingHorizontal/Vertical=`kn_card_padding` を二重指定 |
| 3.3 (44dp アイコンタイル) | `editLayout_targetAppCardUsesWidgetKeyNestCardAndKnIconTile` (`@dimen/kn_icon_tile_lg` + `@drawable/kn_icon_tile_bg` を verify) |
| 3.4 (kn_blue_500 単色フォールバック) | `kn_icon_tile_bg.xml` 内で `kn_blue_500` 固定 (Issue #29 で導入済み) |
| 3.5 (アプリ名 Text.KeyNest.Body or TitleS) | `editLayout_targetAppCardHasLabelPackageAndSignatureChip` (`tv_target_app_label` ID の存在を verify, style は Text.KeyNest.Body) |
| 3.6 (パッケージ名 Mono + kn_text_2) | 同上 (`tv_target_app_package` ID + 3 箇所以上の `Text.KeyNest.Mono` 参照を verify) |
| 3.7 / 3.8 (signature chip success/warning) | `editLayout_targetAppCardHasLabelPackageAndSignatureChip` (warning drawable + signature_missing 文字列を default として verify). success 系への切替は `CredentialEditActivity.renderSignatureChip(hasSignature = true)` で実装 |
| 3.9 (input_package / btn_pick_installed_app 維持) | `editLayout_preservesAllPreIssue30Ids` + `editLayout_keepsHiddenEditablePackageField` |

### Requirement 4: 入力フィールド群 (表示名・ユーザー名) の視覚仕様

| AC | テスト |
|---|---|
| 4.1 (Widget.KeyNest.TextField) | `editLayout_textInputLayoutsUseWidgetKeyNestTextField` (4 件参照を count 4 で verify) |
| 4.2 (kn_r_input 14dp 角丸) | `Widget.KeyNest.TextField` style の `boxCornerRadius*` で設定済み |
| 4.3 (kn_input_height 52dp) | `editLayout_inputFieldsApplyKnInputHeight` (4 件以上参照を verify) |
| 4.4 (Text.KeyNest.Body + kn_text) | 各 TextInputEditText で `android:textAppearance="@style/Text.KeyNest.Body"` + `android:textColor="@color/kn_text"`. 個別 AC テスト無し (style 経由) |
| 4.5 (hint BodyS/Eyebrow + kn_text_2) | TextInputLayout `android:textColorHint="@color/kn_text_2"` 適用. eyebrow ラベルは password label のみ別途追加 (Req 6 と兼ねる) |
| 4.6 (既存 hint 文字列キー維持) | `label_display_name` / `label_username` 等の `@string/...` 参照を XML が保持. 個別 AC テスト無し |
| 4.7 (既存 ID 維持) | `editLayout_preservesAllPreIssue30Ids` |

### Requirement 5: パスワードフィールドの視覚仕様

| AC | テスト |
|---|---|
| 5.1 (Widget.KeyNest.TextField または同等) | `editLayout_textInputLayoutsUseWidgetKeyNestTextField` (count 4 に password も含まれる) |
| 5.2 (Text.KeyNest.Password) | `editLayout_passwordFieldUsesPasswordTextAppearance` |
| 5.3 (ラベル hint TextAppearance) | Req 4.5 と同じ機構. 個別 AC テスト無し |
| 5.4 (passwordToggle + kn_text_2 tint) | `editLayout_passwordFieldHasToggleAndKnText2Tint` |
| 5.5 (input_password / inputType=textPassword 維持) | `editLayout_preservesAllPreIssue30Ids` + `editLayout_passwordFieldUsesPasswordTextAppearance` (`inputType=textPassword` を verify) |

### Requirement 6: パスワード強度バーの視覚仕様

| AC | テスト |
|---|---|
| 6.1 (パスワードフィールド近傍配置) | `editLayout_embedsStrengthBarCustomView` (StrengthBar の存在を verify) + layout XML 構造上 password TextInputLayout の直前 LinearLayout に配置 |
| 6.2 (3 セグメント / 14×4 / 2dp gap) | Issue #29 の `StrengthBarTest.strengthBar_alwaysHasThreeSegments` + `strengthBar_eachSegmentMatchesKnStrengthDimens` で widget 自身は pin 済み |
| 6.3 (strong → 3 success) | Issue #29 `StrengthBarTest.setStrengthStrong_fillsAllSegmentsWithSuccess` |
| 6.4 (medium → 2 warning + 1 track) | Issue #29 `StrengthBarTest.setStrengthMedium_fillsTwoWithWarningAndTrailingWithTrack` |
| 6.5 (weak → 1 danger + 2 track) | Issue #29 `StrengthBarTest.setStrengthWeak_fillsOneWithDangerAndTrailingPairWithTrack` |
| 6.6 (強度判定なし → サンプル固定値) | Activity 側 `binding.strengthBarEdit.setStrength(null)` で widget の default GONE 挙動を選択 (Issue #29 確認事項 #2 と同方針). 個別 AC テスト無し |

### Requirement 7: 詳細設定セクションの視覚仕様

| AC | テスト |
|---|---|
| 7.1 (kn_surface_2 背景) | `editLayout_advancedSectionUsesKnSurface2BackgroundAndKnRInputRadius` (`kn_advanced_section_bg` drawable の参照を verify, drawable 内で `kn_surface_2`) |
| 7.2 (kn_r_input 角丸) | 同上 (drawable 内で `kn_r_input`) |
| 7.3 (kn_card_padding 内側) | layout XML 内 `paddingHorizontal/Vertical="@dimen/kn_card_padding"`. 個別 AC テスト無し |
| 7.4 (Body + kn_text_2 ヘッダー) | `editLayout_advancedHeaderUsesBodyAppearanceAndKnText2` |
| 7.5 (chevron tint kn_text_3) | `editLayout_advancedChevronTintsKnText3` |
| 7.6 (Caption + kn_text_2 ラベル) | 各行のラベル TextView で `style="@style/Text.KeyNest.Caption"` + `android:textColor="@color/kn_text_2"`. 個別 AC テスト無し |
| 7.7 (mono 系 / kn_text) | `editLayout_advancedRowsUseMonoForSha256AndCredentialIdAndPackage` (`Text.KeyNest.Mono` の 3 箇所以上参照を verify) |
| 7.8 (kn_border 区切り線) | `editLayout_advancedRowDividerColor` (`kn_advanced_row_divider` drawable の参照を verify, drawable 内で `kn_border`) |
| 7.9 (既存 advanced ID 全件保持) | `editLayout_preservesAllPreIssue30Ids` |
| 7.10 (既存 Issue #14 挙動不変) | `CredentialEditAdvancedStateTest` 8 件 (既存) を Issue #30 の ViewModel 4-arg 化に追従させても全件 pass. `chevronRotationFor()` / advanced toggle / id toggle / signature copy の挙動は無変更 |

### Requirement 8: 削除ボタンの視覚仕様と表示制御

| AC | テスト |
|---|---|
| 8.1 (editingId != null で表示) | Activity 側 `binding.btnDelete.visibility = if (editingId == null) View.GONE else View.VISIBLE`. layout 側は default GONE で `editLayout_deleteCtaUsesWidgetKeyNestButtonDestructive` が pin |
| 8.2 (editingId == null で非表示) | 同上 |
| 8.3 (match_parent 幅) | `editLayout_deleteCtaUsesWidgetKeyNestButtonDestructive` |
| 8.4 (kn_button_height) | `Widget.KeyNest.Button.Destructive` style の `minHeight`. 個別 AC テスト無し (style 経由) |
| 8.5 (kn_border + 透過 + r14) | 同上 (style の `strokeColor` / `strokeWidth` / parent OutlinedButton transparent / `cornerRadius`) |
| 8.6 (kn_danger テキスト色) | 同上 (style の `textColor`) |
| 8.7 (LabelL or Body) | 同上 (style の `textAppearance="@style/Text.KeyNest.LabelL"`) |
| 8.8 (`action_delete_credential` 文字列共有) | `editLayout_deleteCtaUsesWidgetKeyNestButtonDestructive` (`@string/action_delete_credential` の参照を verify) |
| 8.9 (押下で確認ダイアログ) | Activity 側 `binding.btnDelete.setOnClickListener { showDeleteConfirmation() }` で `MaterialAlertDialogBuilder` を起動. Robolectric 起動テストは Out of Scope (Issue #29 と同方針) |
| 8.10 (肯定で delete + 画面 finish) | `CredentialEditViewModelTest.delete_onSuccess_emitsNavigationEvent_andLeavesStateIdle` (use case 呼び出し + navigation event emit を直接 verify, finish() 自体は既存 navigation collector で発火) |
| 8.11 (否定で dialog 閉じる) | `MaterialAlertDialogBuilder.setNegativeButton(R.string.credential_edit_delete_confirm_negative, null)` で null listener を渡し default close 挙動を取得. 個別 AC テスト無し (Material 標準挙動) |
| 8.12 (失敗時 Snackbar + 画面残留) | `CredentialEditViewModelTest.delete_onStorageFailure_setsDeleteFailedState_andDoesNotEmitNavigation` (DeleteFailed state 発行 + navigation 不発火を verify) + Activity 側 `renderState` の `DeleteFailed -> Snackbar.make(...)` |

### Requirement 9: 保存ボタンの視覚仕様

| AC | テスト |
|---|---|
| 9.1 / 9.3 (画面下部 Primary CTA) | `editLayout_saveCtaUsesWidgetKeyNestButtonPrimary` (`Widget.KeyNest.Button.Primary` style + `match_parent` width を pin) |
| 9.2 (アプリバー配置時 kn_primary text) | 本 Issue では下部配置を採用 (Q3 暫定回答). 個別 AC テスト無し |
| 9.4 (`@string/action_save` 文字列) | `editLayout_saveCtaUsesWidgetKeyNestButtonPrimary` |
| 9.5 (`btn_save` ID + 配線維持) | `editLayout_preservesAllPreIssue30Ids` + Activity 既存挙動 (Issue #29 確認事項 #5 と同方針で Manifest / VM factory 構造は最小差分) |

### Requirement 10: 既存機能・配線の不変

| AC | テスト |
|---|---|
| 10.1 (全 27 ID 保持) | `editLayout_preservesAllPreIssue30Ids` (リスト網羅) |
| 10.2 (保存ボタン → ViewModel.save 配線) | 既存 `CredentialEditViewModelTest.save_*` 4 件が 4-arg factory 化に追従して pass. Activity 側 `binding.btnSave.setOnClickListener { onSaveClicked() }` 無変更 |
| 10.3 (PackagePickerBottomSheet 起動) | layout で `btn_pick_installed_app` を保持 + Activity 側 `binding.btnPickInstalledApp.setOnClickListener { ... show ... }` 無変更 |
| 10.4 (advanced toggle 挙動) | `CredentialEditAdvancedStateTest.toggleAdvancedExpanded_flipsBetweenCollapsedAndExpanded` (既存、4-arg 化対応で全件 pass) |
| 10.5 (signature hex コピー) | `binding.btnCopySignatureHex.setOnClickListener { copySignatureHexToClipboard() }` 無変更. `copySignatureHexToClipboard` 本体も無変更 |
| 10.6 (credential ID トグル) | `CredentialEditAdvancedStateTest.toggleCredentialIdVisible_flipsBetweenHiddenAndVisible` (既存) |
| 10.7 (CharArray パスワード取扱) | `takePasswordCharArray()` 本体無変更. 既存 `CredentialEditViewModelTest` の save 系 4 件で indirect pass |
| 10.8 (SafeLogger ログポリシー) | Activity 側 `SafeLogger.info(... "signature hex copied (preview=${SafeLogger.previewHex(hex)})")` 無変更. 既存 `SafeLoggerAuditTest` が pass |
| 10.9 (エラー文字列リソース解決) | layout で `@string/error_*` 直接参照無し (Activity 側で getString) + 既存 `CredentialEditViewModelTest.save_mapsPackageNameBlank_toFieldError` 等 4 件で indirect pass |

### Requirement 11: ライト / ダーク両モードの描画整合

| AC | テスト |
|---|---|
| 11.1 (light で values/colors.xml の kn_*) | Android resource resolution の挙動. Phase 1 取り込み時の `Material3ThemeMigrationTest` が theme bridge を pin |
| 11.2 (dark で values-night/colors.xml の kn_*) | 同上. レイアウト側は `?android:attr/colorBackground` / `@color/kn_*` のみ参照し直接 hex を持たないことを `editLayout_doesNotHardcodeHexColors` が verify |
| 11.3 (本文コントラスト 4.5:1) | Phase 1 / Issue #29 と同じトークン (`kn_text` on `kn_surface` / `kn_surface_2` / `kn_bg`) を使用. light ≈ 18.6:1, dark ≈ 14.4:1 |
| 11.4 (非文字コントラスト 3:1) | Issue #29 確認事項 #1 と同じ留意点 (`kn_border_strong` 単独は 3:1 に届かない). 強調 outline は colorPrimary 経由 (light 5.4:1) で担保 |

### NFR

| NFR | テスト |
|---|---|
| NFR 1.1 (`:app:assembleDebug` 成功) | 末尾「ビルド・テスト結果」参照 |
| NFR 1.2 (既存単体テスト維持) | 末尾参照. 334 件中失敗 5 件は Phase 1 pre-existing と完全一致 |
| NFR 1.3 (Material3 / FontTypefaceWiring の structural pin 違反なし) | `Material3ThemeMigrationTest` 8 件 / `FontTypefaceWiringTest` 9 件 / `CredentialEditLayoutAuditTest` 7 件すべて pass (特に Issue #13 の `signatureHexValue_usesJetBrainsMonoFont` / `credentialIdValue_usesJetBrainsMonoFont` は明示的 `android:fontFamily="@font/jetbrains_mono"` 属性を value_signature_hex / value_credential_id 上に維持して対応) |
| NFR 2.1 (48dp 最小タッチサイズ) | アプリバーの閉じる × は MaterialToolbar 標準. `btn_delete` は `kn_button_height=52dp`. `btn_pick_installed_app` は `Widget.KeyNest.Button.Text` で 44dp + paddingHorizontal で 48dp 相当. `advanced_header` は `minHeight=48dp`. `btn_copy_signature_hex` / `toggle_credential_id` は既存 `minWidth/minHeight="48dp"` を維持 |
| NFR 2.2 (アイコンのみボタン contentDescription) | アプリバー閉じる × は `app:navigationContentDescription="@string/credential_edit_close_a11y"`. 詳細設定 chevron は `android:contentDescription="@string/credential_edit_advanced_chevron_a11y"`. btn_pick_installed_app は `android:contentDescription="@string/credential_edit_target_app_change_a11y"`. 既存の advanced_header / btn_copy_signature_hex / toggle_credential_id の contentDescription は全件保持 |
| NFR 2.3 (本文 / 非文字コントラスト) | Req 11.3 / 11.4 と同様 |
| NFR 2.4 (削除ボタン contentDescription) | `android:text="@string/action_delete_credential"` (一覧と共有) の可視テキストでスクリーンリーダーに「削除」相当が伝わる |
| NFR 3.1 (en/ja 同一キー集合) | `credential_edit_password_label` / `_close_a11y` / `_advanced_chevron_a11y` / `_target_app_change_a11y` / `_delete_confirm_*` / `_delete_failed` の 9 キーを values/strings.xml と values-ja/strings.xml の両方に同時追加 |
| NFR 3.2 (日本語ロケール表示) | values-ja/strings.xml に 9 キーすべて追加. Android resource resolution が自動で切替 |

## ビルド・テスト結果

```
$ JAVA_HOME=$HOME/sdks/jdk-17 ANDROID_HOME=$HOME/sdks/android-sdk \
    ./gradlew :app:assembleDebug :app:testDebugUnitTest

> Task :app:assembleDebug
BUILD SUCCESSFUL

> Task :app:testDebugUnitTest
334 tests completed, 5 failed
  - PackageSignatureResolverTest 4 件 (NPE @ Signature mock)    ← Phase 1 pre-existing
  - LockedFillResponseSecurityTest 1 件 (NPE @ Signature mock)  ← Phase 1 pre-existing
```

### 失敗テスト 5 件は Phase 1 / Issue #29 既知の pre-existing failure

Phase 1 (#28) の `impl-notes.md` で「`./gradlew :app:testDebugUnitTest` 279 件中
5 件失敗、5 件はすべて pre-impl commit `2a4e81d` でも失敗していた pre-existing
failure」と記録され、Issue #29 (#36) でも 311 件中 5 件失敗が同じ理由で記録
されている. 本 Issue では新規テストを **19 件**追加 (`CredentialEditLayoutTokens`
16 件 + `CredentialEditViewModelTest` の delete 系 3 件) しているため total は
311 + 19 + 4 ≈ 334 件になり、失敗 5 件は Phase 1 / Issue #29 と完全に一致
(`PackageSignatureResolverTest` 4 件 + `LockedFillResponseSecurityTest` 1 件).
**本 Issue で新たに失敗したテストは無い**.

### Phase 1 / Issue #29 構造テストの維持

| テスト | 結果 |
|---|---|
| `Material3ThemeMigrationTest` (8 件) | all pass |
| `FontTypefaceWiringTest` (9 件) | all pass |
| `CredentialEditLayoutAuditTest` (7 件 / Issue #14) | all pass |
| `CredentialEditAdvancedStateTest` (8 件 / Issue #14) | all pass (Factory 4-arg 化に追従) |
| `CredentialEditViewModelTest` (4 + 3 件) | all pass (delete 系 3 件は新規追加) |
| `CredentialListLayoutTokensTest` (19 件 / Issue #29) | all pass (本 Issue で touch 無し) |
| `BundledFontResourcesTest` (16 件) | all pass |

NFR 1.3 の「Phase 1 / Issue #29 structural pin に違反する変更なし」を満たす.

### lint

`./gradlew :app:lintDebug` は project 全体で 98 errors / 129 warnings 出るが、
本 Issue の差分由来は **2 件の Overdraw warning** のみ (`credential_edit_activity.xml`
root LinearLayout の `background="?android:attr/colorBackground"`).
これは Issue #29 の `credential_list_activity.xml` が同様の警告を出す
パターンと**完全一致**しており (sibling PR #36 の認識下), Phase 2 共通の
許容範囲 (本 Issue は視覚整合, lint cleanup は Out of Scope).

## レビュワー注目ポイント (確認事項)

1. **`Widget.KeyNest.Button.Destructive` の新規追加**
   Open Question #1 に対する暫定回答として themes.xml に新規 style を
   追加した. `?attr/colorError` ベースの代替案 (style 追加なし、layout で
   `android:textColor="?attr/colorError"` を直接書く) と同等の視覚を実現
   できるが、style 化したほうが将来の destructive 系 CTA 再利用が楽
   (Settings DangerZone 等). Reviewer / 設計レビューでの判断をお願いしたい.

2. **`Material3ThemeMigrationTest` の structural pin と新 style の関係**
   themes.xml に `Widget.KeyNest.Button.Destructive` を追加しても
   `Material3ThemeMigrationTest` の検証範囲 (`Theme.KeyNest` 親, 13 個の
   M3 textAppearance slot, `TextAppearance.KeyNest.*` の `@font/manrope`
   override) は何も触っていないため、テストは全件 pass する. 既存
   structural pin との backward-compat を維持.

3. **`layout_package` を visibility=gone で隠す判断 (Req 3.9)**
   Open Question / Out of Scope の通り「`layout_package` / `input_package`
   は配置変更可、ターゲットアプリカードに集約してよい」となっている. 本
   Issue では「カードに集約 + 隠す」を採用した. 既存 ID と機能配線は完全
   保持しているため、Reviewer が「visibility=invisible のほうがよい」
   「pull-down dialog 化 のほうがよい」等の代替案を提示する場合は別 PR で
   差し替え可能.

4. **保存ボタンの配置を画面下部に維持した判断 (Req 9.1)**
   Open Question #3 への暫定回答の通り、JSX `ScreenEdit` のアプリバー右側
   「保存」テキストボタンは別 Issue で取り込む方針とし、本 Issue では
   下部 Primary CTA で対応した. Reviewer がアプリバー配置を本 Issue 内で
   実現したい場合は `MaterialToolbar` 子要素として MaterialButton を
   追加し、btn_save を GONE にするだけで切替可能 (layout XML 構造は両方
   を意識した形で書いてある).

5. **削除ボタンの visibility 切替を Activity 側で行っている点**
   Req 8.1 / 8.2 の「edit mode で表示 / new mode で非表示」は Activity の
   `onCreate` で `binding.btnDelete.visibility = if (editingId == null)
   View.GONE else View.VISIBLE` を一度設定する形で実装した. configuration
   change (rotation) では Activity が再 inflate されるため、再度 onCreate
   が走り visibility が再設定される (intent extra が source of truth).
   Robolectric テストは Out of Scope (Issue #29 と同じ判断) としたため、
   visibility 切替の単体テストは追加していない.

6. **target app card のラベル / パッケージ表示の real-time mirror なし**
   Open Question #4 への暫定回答の通り、`tv_target_app_label` /
   `tv_target_app_package` は load 時点の `Credential.label` /
   `Credential.packageName` で固定し、編集中の `input_label` /
   `input_package` 変更には追従させない. real-time mirror すると視覚的に
   冗長になる + 既存の `binding.inputLabel.setText` でカードも書き換わる
   と「保存前のラベルなのに card に確定的に見える」感覚を作りやすい.
   Reviewer 判断で実装方針を変える場合は Activity の TextWatcher 1 個
   追加で対応可能.

7. **アプリバーの title が `Activity.title = ...` 経由で設定される点**
   既存実装と同じく `setSupportActionBar(binding.toolbar)` + `title =
   getString(R.string.credential_edit_title_new or _edit)` で設定する.
   layout XML の `app:title=...` は使っていない (Issue #29 の List 側は
   xml で固定 title を持つが、Edit 画面は new/edit でタイトルが切替わる
   ため Activity 側設定が必要). `app:titleTextAppearance` は layout XML
   で `Text.KeyNest.TitleS` を pin.

## 残課題 / Follow-up Issue 候補

- **PackageManager 経由のアプリ名 / アイコン動的取得**: Out of Scope.
  `Credential.label` 表示と `kn_blue_500` 単色フォールバックタイルを
  `PackageManager.getApplicationLabel()` / `getApplicationIcon()` 経由に
  差し替える別 Issue. Issue #29 と同じく派生 Issue 候補に挙がっている.
- **`Credential.passwordStrength` ドメイン追加 + 強度算出ロジック**:
  Issue #29 確認事項 #2 と同根拠の派生 Issue. 実装時は Activity 側
  `binding.strengthBarEdit.setStrength(...)` を実値に差し替えるだけで
  対応できる.
- **JSX `ScreenEdit` アプリバー右側「保存」テキストボタンの取り込み**:
  本 Issue Open Question #3 への暫定回答で残置. layout XML を MaterialToolbar
  子要素として `MaterialButton style=Widget.KeyNest.Button.Text` 追加 +
  btn_save の visibility=gone への切替で対応可能.
- **削除ボタンの Robolectric 表示切替テスト**: 上記レビュワー注目ポイント #5.
  Issue #29 の `CredentialListEmptyStateTest` と同パターンで
  `applyDeleteCtaVisibility(binding, editingId)` を top-level 関数として
  抽出し、AndroidJUnit4 + ContextThemeWrapper + Theme.KeyNest で binding
  inflate + visibility assert を書く形で追加可能.
- **per-credential アイコンタイル背景カラー割り当て**: Issue #29 派生
  Issue 候補と統合. ターゲットアプリカードの 44dp タイルも同じ機構で
  per-credential color を引けるようにする.
- **Phase 2 残り画面 (`package_picker_bottom_sheet.xml` / `dataset_presentation.xml`
  / `autofill_enable_activity.xml` / Settings / Unlock)** : mapping.md §5 の
  他行を 1 PR ずつ消化する.
