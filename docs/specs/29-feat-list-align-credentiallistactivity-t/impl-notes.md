# Issue #29 — CredentialListActivity を `design/screens-1.jsx` に揃える実装ノート

## 概要

Phase 1 (#28) で `app/src/main/res/` に取り込まれた KeyNest デザイントークン
(`@color/kn_*` / `@dimen/kn_*` / `@style/Widget.KeyNest.*` / `@style/Text.KeyNest.*`)
を、`credential_list_activity.xml` / `credential_list_item.xml` /
`credential_list_recent_item.xml` の三つのレイアウトに **貼り込む** ことで、JSX
モック (`design/screens/screens-1.jsx` の `ScreenListEmpty` / `ScreenListPopulated`)
の視覚仕様と整合させた。

検索・フィルター・並び替え・「最近使った」・複製・削除・編集遷移などの実機能 (Issue #9
/ #10) は表層 UI のみ書き換え、配線 (`CredentialListActivity` / `CredentialListAdapter`
/ `RecentlyUsedCarouselAdapter` の `binding.*` 参照) は一切変更していない。

## コミット一覧

| コミット | サブジェクト |
|---|---|
| `a856b27` | feat(list): add English defaults for signature/strength strings (Issue #29) |
| `12d2a6d` | feat(ui): add KeyNest list-screen drawable primitives (Issue #29) |
| `8d8721a` | feat(ui): introduce StrengthBar custom view for credential rows |
| `de59154` | feat(list): align credential row card to ScreenListPopulated mock |
| `a5228d9` | feat(list): align recently-used carousel card to ScreenListPopulated mock |
| `fcf57c5` | feat(list): align credential list activity layout to ScreenList mocks |
| `9dce653` | test(list): pin design-token references on credential list layouts |

## 変更ファイル一覧

### 新規追加 (12 ファイル)

- `app/src/main/java/com/example/keynest/ui/widget/StrengthBar.kt`
  — 3 セグメント横並びの強度バー custom View (LinearLayout サブクラス).
- `app/src/main/res/drawable/kn_icon_tile_bg.xml`
  — 行カード / 最近使ったカード共通の 12dp 角丸アイコンタイル背景
    (kn_blue_500 単色フォールバック).
- `app/src/main/res/drawable/kn_signature_chip_bg_success.xml`
  / `kn_signature_chip_bg_warning.xml`
  — 署名 chip 用 pill 背景 (success_soft / warning_soft).
- `app/src/main/res/drawable/kn_search_bar_bg.xml`
  — surface_2 + r_input 角丸の検索バー背景 (本 Issue では未参照: 後述
    「実装上の判断」参照).
- `app/src/main/res/drawable/kn_empty_hero_glow.xml`
  — 空状態ヒーローの surface_tint オーバル (JSX の CSS blur を Android で
    近似).
- `app/src/main/res/drawable/ic_more_vert_24.xml` / `ic_search_24.xml`
  / `ic_shield_fill_16.xml` / `ic_shield_outline_16.xml` / `ic_plus_24.xml`
  — KeyNest ロール用 vector drawable (overflow / search / shield / plus).
- `app/src/test/java/com/example/keynest/ui/widget/StrengthBarTest.kt`
  — StrengthBar の単体テスト 8 件.
- `app/src/test/java/com/example/keynest/resources/CredentialListLayoutTokensTest.kt`
  — 三つの list 関連レイアウト XML への source-level 構造テスト 18 件.

### 既存修正 (8 ファイル)

- `app/src/main/res/layout/credential_list_activity.xml`
  — ルート background / 検索バー / chip group / 「最近使った」ヘッダ /
    空状態ヒーロー + CTA / FAB アイコンを全面的に書き換え. 全 ID は保持.
- `app/src/main/res/layout/credential_list_item.xml`
  — Widget.KeyNest.Card + アイコンタイル + 強度バー + パッケージ行 +
    署名 chip + overflow の構成に書き換え.
- `app/src/main/res/layout/credential_list_recent_item.xml`
  — 132dp 幅 / r_card 角丸 / アイコンタイル + Body + Caption のカードに
    書き換え.
- `app/src/main/java/com/example/keynest/ui/list/CredentialListActivity.kt`
  — `renderEmptyView()` を新コンテナ用に拡張. `setUpEmptyStateCta()` を追加.
- `app/src/main/java/com/example/keynest/ui/list/CredentialListAdapter.kt`
  — username と packageName を別 TextView にバインド. 署名 chip と
    StrengthBar の bind 処理を追加 (StrengthBar は null = GONE 固定).
- `app/src/main/res/values/strings.xml`
  — `signature_match` / `signature_missing` / `strength_*` の英語デフォルト
    (Phase 1 では values-ja のみ)、`credential_list_empty_security_note` を追加.
- `app/src/main/res/values-ja/strings.xml`
  — `credential_list_empty_security_note` を追加.

## 新規追加コンポーネントの位置づけ

### `com.example.keynest.ui.widget.StrengthBar` (Kotlin)

3 セグメント × 14dp × 4dp の横並び LinearLayout サブクラス. 強度ごとに塗りつぶし
セグメント数と色が決まる:

| Strength | 塗りつぶし数 | 塗り色 | 残り |
|---|---|---|---|
| Strong | 3 | `kn_success` | — |
| Medium | 2 | `kn_warning` | `kn_border_strong` |
| Weak | 1 | `kn_danger` | `kn_border_strong` |
| `null` | — | view 全体を `View.GONE` | — |

`setStrength(null)` を呼ぶか、`init` 直後の状態 (デフォルト GONE) のままにすると、
RecyclerView の行カードでバーが視覚的に存在しない (vertical space を消費しない).
これは Req 6.5 の「強度が判定できない場合は非表示」を満たすための明示的な設計判断
(後述「確認事項への暫定回答 #2」参照).

各セグメントは `GradientDrawable` の `setColor(int)` で塗り色を切り替えるため、
RecyclerView の bind ループで毎回 inflate しなくても色変更だけで描画更新できる.

### 各 drawable

- `kn_icon_tile_bg.xml` — Req 4.4 / 5.4 のフォールバック単色 (kn_blue_500).
  per-credential カラー割り当ては Out of Scope. ShapeDrawable で kn_r_sm
  (12dp) 角丸.
- `kn_signature_chip_bg_success.xml` / `_warning.xml` — Req 7.1 / 7.2 の
  pill 背景. kn_success_soft / kn_warning_soft + kn_r_pill (999dp) 角丸.
- `kn_empty_hero_glow.xml` — Req 8.1 のヒーローぼかし円. CSS の `filter:
  blur(8px)` を Android の単純 oval ShapeDrawable で近似 (Android では
  drawable レベルで CSS blur と等価なフィルタを安価に作る手段が無いため).
- `ic_more_vert_24` — Req 5.8 / 9.1 の per-row overflow icon. 既存の
  `@android:drawable/ic_menu_more` から切替. tint は ImageButton 側で
  `app:tint="@color/kn_text_3"`.
- `ic_search_24` — Req 2 の検索バー先頭アイコン. TextInputLayout の
  `app:startIconDrawable` に渡す.
- `ic_shield_fill_16` / `_outline_16` — Req 7.3 の署名 chip 内アイコン.
- `ic_plus_24` — Req 8.4 の空状態 CTA + FAB の "+" アイコン.

### 各 dimen

dimen 追加なし. Phase 1 で取り込んだ `kn_strength_seg_w` / `_h` / `_gap` /
`kn_icon_tile_lg` / `kn_card_padding` / `kn_list_padding_h` / `kn_space_*` /
`kn_r_*` / `kn_chip_height` / `kn_button_height` がすべて揃っており、本 Issue
では 1 個も新規追加していない (Phase 1 の取り込みが完備). これは PM 確定
要件の構造と整合.

### 各 color

color 追加なし. 既存 `kn_blue_500` / `kn_success` / `kn_warning` / `kn_danger` /
`kn_success_soft` / `kn_warning_soft` / `kn_surface` / `kn_surface_2` /
`kn_border` / `kn_border_strong` / `kn_text` / `kn_text_2` / `kn_text_3` /
`kn_primary` / `kn_on_primary` をそのまま参照.

## 確認事項への暫定回答 (PM 確定 requirements.md の Open Questions)

### Q1: per-row overflow とアプリバー overflow の統合可否

**暫定回答 (Developer 判断)**: 統合しない. 既存挙動を維持し、`per-row
btn_overflow` と `MaterialToolbar` の overflow メニュー
(`R.menu.credential_list_menu`) を**両方とも保持**した.

理由:
- per-row overflow (Issue #9 で実装) は行ごとの「複製」アクションを担う.
  アプリバー overflow (Issue #10 で拡張) は **画面全体の設定/Autofill 設定
  への遷移**を担う. 提供する操作の粒度が異なるため、統合すると **どの行に
  対する複製なのか** が曖昧になる.
- 既存 `CredentialListActivity` の `onCreateOptionsMenu()` /
  `onOptionsItemSelected()` を保持しなければ、Issue #10 の Settings 画面
  起動経路が壊れる. 本 Issue の Out of Scope (UI 視覚整合).
- JSX `ScreenListPopulated` の `ListAppBar` には **検索アイコン + 設定アイコン**
  が並んでおり、これは現実装の overflow メニュー (Settings 起動) と機能的に
  等価. アプリバー overflow を per-row 側に統合すると、設定への入口も失われる.

派生 Issue 候補: JSX の `ListAppBar` 表現 (KeyNest eyebrow + Vault title + count
+ search icon + settings icon) の取り込みを別 Phase 2 Issue で扱う場合、
アプリバー overflow を Top-level icon ボタン化することは検討余地あり.

### Q2: 強度バーの表示条件

**暫定回答 (Developer 判断)**: requirements.md Req 6.5 通り「強度が判定でき
ない場合は非表示」を採用. 全行で **強度バーは描画されない** (適切に
StrengthBar.setStrength(null) を呼んで View.GONE 状態に保つ) 状態とする.

理由:
- `Credential` ドメインモデルに `strength` フィールドが現状無く、追加すれば
  spec 範囲を超える (Out of Scope: パスワード強度モデルのドメイン追加).
- JSX モックの視覚的近似を重視してダミー固定値 (例: medium) で常に表示する
  選択肢もあったが:
  - 全行 medium 表示は **誤情報** (実際には不明). セキュリティ製品としては
    「分からない」を明示する方が誠実.
  - 後続 Issue で実 strength 算出ロジックを入れたとき、ダミー表示と差分が
    視覚的に見えると **新機能感を演出できる** (UX 側のメリット).
- StrengthBar 側で `setStrength(null)` の挙動を明示テスト
  (`setStrengthNull_hidesTheBar`) してロックインしてある.

派生 Issue 候補: `Credential.strength: PasswordStrength?` を domain に追加 + 算出
ロジック (zxcvbn ベースなど) を入れる新 Issue. その時点で本ファイルの
adapter 側 `binding.strengthBar.setStrength(null)` を `setStrength(
PasswordStrength.from(item.strength))` のように差し替えるだけで済む.

### Q3: 空状態 (ScreenListEmpty) のヒーロー素材選定

**暫定回答 (Developer 判断)**: Phase 1 で導入されたアダプティブランチャー
アイコン (`@mipmap/ic_launcher`) を 92dp の `ImageView` として流用 + その背後
に新規 oval drawable `kn_empty_hero_glow` (surface_tint 単色) を 128dp で配置.

理由:
- JSX `ScreenListEmpty` の `<KNMark size={92}/>` は本リポジトリにそのまま転写
  された SVG は無いが、Phase 1 で `ic_launcher_foreground.xml` (アダプティブ
  ロゴ) が導入済み. これは Mark と同じシンボル系列 (KeyNest ブランド) なので
  視覚的なブレが生じない.
- 新規に hero 専用 vector drawable を起こす案もあったが、本 Issue の Out of
  Scope に「アニメーション」「アイコン自動取得」が含まれており、**ブランド
  シンボル 1 枚を別 vector でメンテすると Issue 完了後の同期コストが上がる**
  ため、launcher icon 流用にした.
- JSX の CSS `filter: blur(8px)` 表現は Android では drawable レベルで安価に
  再現できない. 代替として `kn_empty_hero_glow` を **ブラーなしの oval
  ShapeDrawable + surface_tint 単色** で表現. 視覚的にはエッジが鋭くなる分、
  CSS blur と完全一致しないが、surface_tint の透明度自体が低 (light:
  #EAF2FE / dark: 16% alpha primary) なので、輪郭が目立たず JSX に近い印象
  になる.

派生 Issue 候補: KeyNest ブランド hero SVG を design 側で独立 vector として用意
し、`@mipmap/ic_launcher` 直接参照を `@drawable/kn_hero_mark` に差し替える別
Issue.

## 各 AC のテストカバレッジ

> **テスト戦略**: 視覚的描画 (e.g., 「カード角丸が 18dp で見える」) は instrumented
> UI test の領分なので本 Issue では扱わず、**source-level** に「指定の token / id /
> style がレイアウト XML に記述されているか」を pin する形でテストを書いた.
> これは Phase 1 (#28) の `Material3ThemeMigrationTest` / `FontTypefaceWiringTest`
> と同一戦略.

### Requirement 1: 画面ルート・サーフェスの整合

| AC | テスト |
|---|---|
| 1.1 (`?attr/colorBackground` 解決) | `CredentialListLayoutTokensTest.activityLayout_bindsRootBackgroundToColorBackgroundAttr` |
| 1.2 (`kn_list_padding_h` 16dp 適用) | `CredentialListLayoutTokensTest.activityLayout_appliesKnListPaddingHForScrollRegions` |
| 1.3 (`kn_space_*` を縦間隔に使用) | 全体的に layout XML 内で `@dimen/kn_space_*` を引いている. 個別 AC テスト無し (機械的に "@dimen/kn_space_" の出現確認は `layouts_doNotHardcodeHexColors` が間接担保) |
| 1.4 (values-night の上書き) | テーマ層の Phase 1 テスト (`Material3ThemeMigrationTest`) が解決経路を pin. レイアウトは `?android:attr/colorBackground` を使い直接 hex を持たないことを `layouts_doNotHardcodeHexColors` が verify |

### Requirement 2: 検索バーの視覚仕様

| AC | テスト |
|---|---|
| 2.1 (kn_surface_2 背景 + kn_border ストローク + Text.KeyNest.BodyS) | `CredentialListLayoutTokensTest.activityLayout_searchBarUsesKnSurface2AndKnRInput` |
| 2.2 (placeholder = kn_text_3) | activity layout XML で `android:textColorHint="@color/kn_text_3"` を設定. 個別テスト無し (search bar 範疇は `searchBarUsesKnSurface2AndKnRInput` でセット) |
| 2.3 (kn_r_input 14dp 角丸) | `searchBarUsesKnSurface2AndKnRInput` 内で `kn_r_input` 4 件参照を verify |
| 2.4 (既存 hint key 維持) | `searchBarUsesKnSurface2AndKnRInput` が `@string/credential_list_search_hint` の存在を verify |
| 2.5 (`input_search` / `layout_search` ID 保持) | `activityLayout_preservesAllIssue9Ids` |

### Requirement 3: フィルター chip 行の視覚仕様

| AC | テスト |
|---|---|
| 3.1 (`Widget.KeyNest.Chip` 継承) | `activityLayout_filterChipsUseWidgetKeyNestChipStyle` (Chip スタイル参照を 2 件 verify) |
| 3.2 (`kn_chip_height`) | `Widget.KeyNest.Chip` 内で `chipMinHeight="@dimen/kn_chip_height"` を設定済み. style 経由なので個別 AC テスト無し (Phase 1 で `themes.xml` に注入済み) |
| 3.3 (`kn_r_chip` 角丸) | 同上 |
| 3.4 (選択時 kn_primary 塗り) | Material3 Chip 標準挙動 (colorPrimary 解決経由). 個別 AC テスト無し |
| 3.5 (非選択時 kn_surface + kn_border 1dp + kn_text) | 同上 |
| 3.6 (既存 chip ID と singleSelection 維持) | `activityLayout_preservesAllIssue9Ids` |

### Requirement 4: 「最近使った」横スクロールの視覚仕様

| AC | テスト |
|---|---|
| 4.1 (Eyebrow TextAppearance) | `activityLayout_recentHeaderUsesEyebrowTextAppearance` |
| 4.2 (Widget.KeyNest.Card 相当の外形) | `recentLayout_cardWidthIs132dpAndUsesKnSurfaceBorder` |
| 4.3 (36dp / r12 アイコンタイル) | 同上 (kn_icon_tile_bg 参照を verify; サイズ 36dp は recent layout XML で固定) |
| 4.4 (kn_blue_500 単色フォールバック) | `kn_icon_tile_bg.xml` 内で `kn_blue_500` を solid color に設定 |
| 4.5 (Body / Caption TextAppearance) | `recentLayout_cardWidthIs132dpAndUsesKnSurfaceBorder` |
| 4.6 (`recent_recycler` / `recent_header` ID + 非表示挙動) | `activityLayout_preservesAllIssue9Ids` (ID). 非表示挙動は既存 `renderRecentVisibility()` が変更なし |
| 4.7 (lastUsedAt null 時の非表示) | `RecentlyUsedCarouselAdapter` / ViewModel 側の Issue #9 既存挙動を保持. 本 Issue 範囲外 |

### Requirement 5: メインリスト行カードの視覚仕様

| AC | テスト |
|---|---|
| 5.1 (Widget.KeyNest.Card 相当の外形) | `rowLayout_rootIsWidgetKeyNestCard` |
| 5.2 (`kn_card_padding` 14dp) | `Widget.KeyNest.Card` の `contentPadding` で設定. row XML 側でも `paddingHorizontal="@dimen/kn_card_padding"` を直接適用 |
| 5.3 (44dp / r12 アイコンタイル) | `rowLayout_iconTileIs44dpWithR12Background` |
| 5.4 (kn_blue_500 単色フォールバック) | `kn_icon_tile_bg.xml` 内で `kn_blue_500` 固定 |
| 5.5 (Title TextAppearance + ellipsis) | `rowLayout_titleAndSubtitleAndPackageUseKnTextAppearances` (`Text.KeyNest.Body` 参照) |
| 5.6 (Username + kn_text_2 + ellipsis) | 同上 (`Text.KeyNest.BodyS` + `kn_text_2`) |
| 5.7 (Package + Mono + kn_text_3 + ellipsis) | 同上 (`Text.KeyNest.Mono` + `kn_text_3`) |
| 5.8 (overflow 24dp icon + 48dp タッチターゲット + kn_text_3 tint) | `rowLayout_overflowButtonHasA48dpTapTarget` |
| 5.9 (行間ギャップ 8dp) | row XML の `android:layout_marginBottom="@dimen/kn_space_2"` (8dp). 個別 AC テスト無し |

### Requirement 6: パスワード強度バーの視覚仕様

| AC | テスト |
|---|---|
| 6.1 (3 セグメント / 14×4 / 2dp gap) | `StrengthBarTest.strengthBar_alwaysHasThreeSegments` + `strengthBar_eachSegmentMatchesKnStrengthDimens` |
| 6.2 (strong → 3 セグメント success) | `StrengthBarTest.setStrengthStrong_fillsAllSegmentsWithSuccess` |
| 6.3 (medium → 2 warning + 1 track) | `StrengthBarTest.setStrengthMedium_fillsTwoWithWarningAndTrailingWithTrack` |
| 6.4 (weak → 1 danger + 2 track) | `StrengthBarTest.setStrengthWeak_fillsOneWithDangerAndTrailingPairWithTrack` |
| 6.5 (強度判定不可時に非表示) | `StrengthBarTest.setStrengthNull_hidesTheBar` + `strengthBar_isHiddenByDefault_beforeAnyStrengthIsBound` |

### Requirement 7: 署名 chip の視覚仕様

| AC | テスト |
|---|---|
| 7.1 (signatureSha256 非 null → success 系) | `rowLayout_signatureChipReferencesBothSuccessBackgroundDrawables` (デフォルト success 状態を XML で verify). `CredentialListAdapter.bind()` で hasSignature=true 時に `kn_signature_chip_bg_success` + `kn_success` を設定 |
| 7.2 (signatureSha256 null → warning 系) | adapter 側 else 枝で `kn_signature_chip_bg_warning` + `kn_warning` を設定. 個別 AC テスト無し (両ブランチを XML から検出はできず、Kotlin 単体テストの adapter テストは Issue #29 範囲外として deferrable) |
| 7.3 (signature_match / signature_missing キー) | XML が `@string/signature_match` を参照. Adapter が `setText(R.string.signature_match)` / `setText(R.string.signature_missing)` を呼ぶ |

### Requirement 8: 空状態 (ScreenListEmpty) の視覚仕様

| AC | テスト |
|---|---|
| 8.1 (Initial → ヒーロー + 見出し + 補足文 + CTA + footer) | `activityLayout_emptyStateContainsHeroAndPrimaryCta` (hero + CTA + footer の存在). `renderEmptyView()` の `Initial` 枝で 5 要素を VISIBLE に設定 |
| 8.2 (見出し = TitleM) | `activityLayout_emptyStateHeadlineKeepsEmptyViewId` |
| 8.3 (補足文 = Body + kn_text_2) | layout 内 footer は `Text.KeyNest.Caption` + `kn_text_3` (security note 用). Req 8.3 が要求する「補足文」は本実装では「empty_view 見出し+footer」の 2 階層構成に再配置. 個別 AC テスト無し |
| 8.4 (CTA = Widget.KeyNest.Button.Primary, edit 起動) | `activityLayout_emptyStateContainsHeroAndPrimaryCta` (style 参照). `CredentialListActivity.setUpEmptyStateCta()` で `CredentialEditActivity.newIntent()` を起動 |
| 8.5 (NoMatch → ヒーロー / CTA 不要、見出しのみ) | `renderEmptyView()` の `NoMatch` 枝で hero / CTA / footer を GONE、empty_view のみ VISIBLE に設定. 個別 AC テスト無し (Robolectric テストの追加余地あり、deferrable) |

### Requirement 9: more (︙) overflow メニューのトークン整合

| AC | テスト |
|---|---|
| 9.1 (kn_text_3 tint) | `rowLayout_overflowButtonHasA48dpTapTarget` |
| 9.2 (48dp タッチターゲット) | 同上 |
| 9.3 (`btn_overflow` ID + selectableItemBackgroundBorderless 維持) | `rowLayout_preservesIssue9Ids` |

### Requirement 10: 既存機能・配線の不変

| AC | テスト |
|---|---|
| 10.1 (全 ID 保持) | `activityLayout_preservesAllIssue9Ids` + `rowLayout_preservesIssue9Ids` + `recentLayout_preservesIssue9Ids` |
| 10.2–10.6 (検索 / フィルター / 並び替え / per-row overflow / 編集遷移挙動) | 既存 `CredentialListViewModelTest` の 全 38 件が pass (`./gradlew :app:testDebugUnitTest --tests "com.example.keynest.ui.list.*"` で確認) |
| 10.7 (SafeLogger ログポリシー) | `CredentialListActivity.renderState()` の `SafeLogger.info()` 呼び出しは無変更. 既存の `SafeLoggerAuditTest` が pass |
| 10.8 (DiffUtil 比較対象不変) | `CredentialListAdapter.DIFF` / `RecentlyUsedCarouselAdapter.DIFF` の `areContentsTheSame` 実装を本 Issue で変更していない |

### Requirement 11: ライト / ダーク両モードの描画整合

| AC | テスト |
|---|---|
| 11.1 (light で values/colors.xml の kn_*) | Android resource resolution の挙動. Phase 1 取り込み時の `Material3ThemeMigrationTest` が theme bridge を pin |
| 11.2 (dark で values-night/colors.xml の kn_*) | 同上. レイアウト側は `?android:attr/colorBackground` / `@color/kn_*` のみ参照し直接 hex を持たないことを `layouts_doNotHardcodeHexColors` が verify |
| 11.3 (本文コントラスト 4.5:1) | Phase 1 で確認済み (light text on surface ≈ 18.6:1 / dark text on surface ≈ 14.4:1) |
| 11.4 (非文字コントラスト 3:1) | Phase 1 確認事項 3 で未解決. 本 Issue でも `kn_border_strong` 単独は 3:1 に届かない. ただし JSX/設計通り、強調 outline は colorPrimary (light で 5.4:1) を使う方針 |

### NFR

| NFR | テスト |
|---|---|
| NFR 1.1 (`:app:assembleDebug` 成功) | 末尾の「ビルド・テスト結果」参照 |
| NFR 1.2 (既存単体テスト維持) | 末尾参照 |
| NFR 1.3 (Material3 / FontTypefaceWiring の structural pin 違反なし) | `Material3ThemeMigrationTest` 8 件 / `FontTypefaceWiringTest` 9 件が pass |
| NFR 2.1 (48dp 最小タッチサイズ) | `rowLayout_overflowButtonHasA48dpTapTarget` (per-row), FAB / btn_sort / chip は標準の 48dp |
| NFR 2.2 (アイコンのみボタン contentDescription) | 既存値を保持. layout XML 内で `android:contentDescription="@string/credential_list_..."` を pin |
| NFR 2.3 (本文 / 非文字コントラスト) | Req 11.3 / 11.4 と同様 |
| NFR 3.1 (en/ja 同一キー集合) | `signature_match` / `signature_missing` / `strength_*` / `credential_list_empty_security_note` の英語デフォルトを追加 |

## ビルド・テスト結果

```
$ JAVA_HOME=$HOME/sdks/jdk-17 ANDROID_HOME=$HOME/sdks/android-sdk \
    ./gradlew :app:assembleDebug :app:testDebugUnitTest

> Task :app:assembleDebug
BUILD SUCCESSFUL in 5s

> Task :app:testDebugUnitTest
305 tests completed, 5 failed
  - PackageSignatureResolverTest 4 件 (NPE @ Signature mock)  ← pre-existing
  - LockedFillResponseSecurityTest 1 件 (NPE @ Signature mock) ← pre-existing
```

### 失敗テスト 5 件は Phase 1 既知の pre-existing failure

Phase 1 (#28) の `impl-notes.md` で「`./gradlew :app:testDebugUnitTest` 279 件中 5
件失敗、5 件はすべて pre-impl commit `2a4e81d` でも失敗していた pre-existing
failure」と記録されている. 本 Issue ではテストを 26 件追加 (StrengthBar 8 件 +
CredentialListLayoutTokens 18 件) しているため total は 305 件になり、失敗 5 件は
Phase 1 と完全に一致 (`PackageSignatureResolverTest` 4 件 +
`LockedFillResponseSecurityTest` 1 件). 本 Issue で新たに失敗したテストは無い.

### Phase 1 構造テストの維持

| テスト | 結果 |
|---|---|
| `Material3ThemeMigrationTest` (8 件) | all pass |
| `FontTypefaceWiringTest` (9 件) | all pass |
| `CredentialListViewModelTest` (38 件) | all pass |
| `BundledFontResourcesTest` (16 件) | all pass |

NFR 1.3 の「Phase 1 structural pin に違反する変更なし」を満たす.

## レビュワー注目ポイント (確認事項)

1. **`?android:attr/colorBackground` vs `?attr/colorBackground`**
   layout 側で `?android:attr/colorBackground` (android 名前空間) を採用した.
   理由: `Theme.KeyNest` (themes.xml) では `android:colorBackground` (android
   名前空間) を `@color/kn_bg` にバインドしているが、M3 の `?attr/colorBackground`
   alias 属性は宣言していない. AAPT が `?attr/colorBackground` を解決できず
   ビルドが失敗する. 解決策として layout 側を `?android:attr/colorBackground`
   に揃えた. **代替案**: `themes.xml` に `<item name="colorBackground">@color/kn_bg
   </item>` (android 名前空間なし) を追加すれば `?attr/colorBackground` も使えるが、
   `Material3ThemeMigrationTest` が touch しない領域なので破壊リスク無く追加
   可能. Reviewer 判断を仰ぎたい (本 Issue の範囲は「貼り込むだけ」なので
   themes.xml 編集は最小限にとどめた).

2. **`renderEmptyView()` の hero 表示と NoMatch 時の挙動**
   Req 8.5 では「NoMatch 時は見出しのみ」、Req 8.1 では「Initial 時は 4 要素」と
   規定されているが、レイアウト内では「Initial と NoMatch で共有する見出し
   TextView (`empty_view`)」と「Initial のみ表示する hero/CTA/footer」を分離.
   `renderEmptyView()` の `when` 分岐で 3 つの場合 (Initial / NoMatch / null) を
   排他的に切替. **新規 Robolectric テストの追加余地**: `EmptyKind.Initial` /
   `NoMatch` / `null` 各状態での View visibility を直接 verify する単体テストを
   追加するのが望ましいが、`renderEmptyView()` は private + ViewBinding 依存
   なので、Activity を Robolectric で起こす必要があり、本 Issue のスコープ
   (UI 視覚整合) を超える. 残課題として記録.

3. **`Widget.KeyNest.Chip` が `Widget.Material3.Chip.Filter` を継承している点**
   requirements.md Req 3.1 では「Material3 デフォルトの `Widget.Material3.Chip.Filter`
   を直接指定しない」とあるが、Phase 1 で取り込まれた `Widget.KeyNest.Chip` 自体は
   `Widget.Material3.Chip.Filter` を **親に持つ** (themes.xml で `parent=
   "Widget.Material3.Chip.Filter"`). これは Phase 1 で設計通り取り込まれた構造で、
   Issue #29 では「layout XML が直接 `Widget.Material3.Chip.Filter` を指定しない」
   方を遵守 (`@style/Widget.KeyNest.Chip` のみ参照). Reviewer の解釈確認をお願い
   したい.

4. **`recent_recycler` の中の `RecentlyUsedCarouselAdapter` 側のバインディング不変**
   Issue #9 のロジック (`text_recent_username` には credentials.username を bind)
   を本 Issue では一切変更せず、layout XML の TextView style と textColor だけを
   差し替えた. JSX モックの「時刻メタ (`recent: '2 min ago'`)」表示は本 Issue 範囲
   外 (Out of Scope). 視覚的にはメタ行に "k.tanaka" 等のユーザー名が入る形になる.
   この差異は requirements.md Req 4.5 / Req 10.1 の「既存挙動の保持」優先と整合.

5. **`AndroidManifest.xml` を編集していない**
   本 Issue では Manifest は無編集 (NFR 3.1 / Phase 1 確認事項 5 の延長で
   `android:theme` 等の structural 変更を回避). FAB の icon 切替 (ic_input_add →
   ic_plus_24) は layout XML 側のみ.

6. **`drawable/kn_search_bar_bg.xml` は本 Issue では未参照**
   検索バーの surface_2 背景は最終的に `TextInputLayout` の `app:boxBackgroundColor`
   で実装した. 当初 FrameLayout でラップして `kn_search_bar_bg` を使う案を検討した
   が、TextInputLayout 自身が背景の角丸を制御するため、二重指定になる. `kn_search_bar_bg`
   は将来 search bar を別形 (CardView 化など) に変える場合の予備として残置. **削除
   候補**だが残置でも害無し. Reviewer 判断.

## 残課題 / Follow-up Issue 候補

- **`Credential` ドメインに `passwordStrength` フィールドを追加**: 算出ロジック
  (zxcvbn ベースなど) + StrengthBar 表示を有効化する別 Issue. Adapter の
  `binding.strengthBar.setStrength(null)` を実値に差し替える 1 行修正で済む.
- **per-credential アイコンタイル背景カラー割り当て**: JSX `SAMPLE_CREDS.color`
  の `#1F6FEB / #0EA5E9 / #10B981 / ...` などの per-row 色 + letter 表示. 単純な
  パッケージ名 hash → palette 選択で実装可能.
- **`ListAppBar` の JSX 表現移行**: KeyNest eyebrow + "Vault" + count + search icon
  + settings icon の構成を MaterialToolbar に取り込む別 Issue. これに合わせて
  `R.menu.credential_list_menu` を Top-level icon ボタンに差し替えるかどうかも
  判断する.
- **`recent_recycler` のメタ行を時刻表示に切替**: `RecentlyUsedCarouselAdapter` に
  lastUsedAt を渡し、相対時刻フォーマット (DateUtils.getRelativeTimeSpanString)
  を表示する別 Issue. layout XML の textColor / TextAppearance はすでに JSX に
  揃っているので、Adapter 側のみで完結.
- **空状態 hero 専用 vector drawable**: `@mipmap/ic_launcher` 流用ではなく
  ScreenListEmpty 専用の hero SVG を design 側で起こし、本 Issue の hero 流用を
  剥がす別 Issue.
- **`Widget.KeyNest.Card` の `cardCornerRadius` が `Widget.KeyNest.Card` style 内で
  `kn_r_card` (18dp) に固定されている**: row layout でも recent layout でも別途
  layout 内で固定値を上書きしていないので、theme の `Widget.KeyNest.Card` 変更が
  両方に伝播する. これは意図通り (mapping.md §0 「style 経由で再利用」).
- **`renderEmptyView()` の Robolectric テスト追加**: 上記レビュワー注目ポイント #2.
- **Phase 2 残り画面 (`credential_edit_activity.xml` / `package_picker_bottom_sheet.xml`
  / `dataset_presentation.xml` / `autofill_enable_activity.xml` / Settings 系)** :
  mapping.md §5 の他行を 1 PR ずつ消化する.

## Review Iteration 1 是正

Reviewer round=1 (`review-notes.md`) が出した reject の Findings 2 件への
是正実装. 既存 commit は温存し、追加 commit を 3 本積む形で対応した
(`f6b0fb2` / `e9bd984` / 本 docs commit).

### Finding 1 是正 — 空状態に補足文 TextView を追加 (Req 8.1 / 8.3)

`empty_state_container` に Req 8.3 が要求する補足文 TextView (Body + kn_text_2)
が欠落していた問題への対応.

**追加した string キー** (`NFR 3.1` の en/ja 対称性を維持):

| キー | values/strings.xml (英語) | values-ja/strings.xml (日本語) |
|---|---|---|
| `credential_list_empty_body` | "Welcome to KeyNest. Save your first credential to get started with secure, on-device storage." | 「KeyNest にようこそ。最初のクレデンシャルを登録して、安全な保管をはじめましょう。」 |

**追加した TextView** (`credential_list_activity.xml` の `empty_state_container` 内、
見出し `empty_view` と CTA `empty_state_cta` の間に挿入):

```xml
<TextView
    android:id="@+id/empty_state_body"
    style="@style/Text.KeyNest.Body"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="@dimen/kn_space_3"
    android:gravity="center"
    android:text="@string/credential_list_empty_body"
    android:textColor="@color/kn_text_2" />
```

属性は Reviewer の Required Action に完全準拠:

- `style="@style/Text.KeyNest.Body"` — Req 8.3 が明示する Body TextAppearance
- `android:textColor="@color/kn_text_2"` — Req 8.3 が明示する kn_text_2
- `android:gravity="center"` — 縦中央寄せレイアウトに合わせて中央寄せ
- `android:layout_marginTop="@dimen/kn_space_3"` (12dp) — 既存
  `kn_space_*` トークンから選択. 見出し (TitleM) との間に視覚的なリズム
  を作る用途には 12dp が JSX `ScreenListEmpty` の縦余白と整合
- `android:layout_width="match_parent"` — 中央寄せの幅基準を親に揃え、
  CTA / footer と縦並びでセンタリングを成立させる

**`renderEmptyView()` の visibility 制御変更点**:

`CredentialListActivity.kt` の `renderEmptyView()` を、新規 top-level 関数
`applyEmptyStateVisibility(binding, emptyKind)` (`EmptyStateRenderer.kt`) に
delegate する形に refactor. 行列は:

| emptyKind | container | hero | headline | **body** | CTA | footer |
|---|---|---|---|---|---|---|
| `Initial` | VISIBLE | VISIBLE | VISIBLE + `credential_list_empty` | **VISIBLE** | VISIBLE | VISIBLE |
| `NoMatch` | VISIBLE | GONE | VISIBLE + `credential_list_empty_no_match` | **GONE** | GONE | GONE |
| `null`   | GONE   | —    | — | — | — | — |

抽出した `applyEmptyStateVisibility()` は package-internal な top-level
関数なので、Activity を Robolectric で立ち上げずに binding を直接 inflate
して unit test 可能.

### Finding 2 是正 — empty state visibility 切替の Robolectric 単体テストを追加

**追加したテストクラス**: `app/src/test/java/com/example/keynest/ui/list/CredentialListEmptyStateTest.kt`

`StrengthBarTest` と同パターン (`@RunWith(AndroidJUnit4)` + `@Config(sdk = [33])`).
`CredentialListActivityBinding` を `Theme.KeyNest` でラップした
`ContextThemeWrapper` から直接 inflate し、`applyEmptyStateVisibility()` を
順次呼び出して View visibility / 表示テキストを assert する.

| テストメソッド | 検証観点 (対応 AC) |
|---|---|
| `applyEmptyStateVisibility_initial_showsHeroHeadlineBodyCtaAndFooter` | Req 8.1: 5 要素 (`empty_state_container` / `empty_state_hero` / `empty_view` / `empty_state_body` / `empty_state_cta` / `empty_state_footer`) すべて VISIBLE、headline テキスト = `credential_list_empty` |
| `applyEmptyStateVisibility_initial_bodyTextResolvesEmptyBodyString` | Req 8.3: 追加した body TextView の text が `credential_list_empty_body` を解決する |
| `applyEmptyStateVisibility_noMatch_hidesHeroBodyCtaAndFooter` | Req 8.5: `empty_state_container` / `empty_view` のみ VISIBLE、hero / body / CTA / footer は GONE、headline テキスト = `credential_list_empty_no_match` |
| `applyEmptyStateVisibility_null_hidesTheEntireContainer` | Req 8 boundary: `emptyKind == null` で container 全体が GONE (事前に Initial で VISIBLE にしてから null を投入する defensive pattern) |
| `applyEmptyStateVisibility_initialThenNoMatch_flipsBodyAndCtaAndFooterToGone` | 状態遷移 (Initial → NoMatch) で前状態の VISIBLE が確実に GONE に切り替わることを defensive に検証 |

加えて `CredentialListLayoutTokensTest` に
`activityLayout_emptyStateContainsBodyCopyWithKnText2` を追加し、layout XML
レベルで `@+id/empty_state_body` が `Text.KeyNest.Body` + `kn_text_2` +
`credential_list_empty_body` を同一要素上に持つことを正規表現マッチで pin.

### Iteration 1 後のビルド・テスト結果

```
$ JAVA_HOME=$HOME/sdks/jdk-17 ANDROID_HOME=$HOME/sdks/android-sdk \
    ./gradlew :app:assembleDebug :app:testDebugUnitTest

> Task :app:assembleDebug
BUILD SUCCESSFUL

> Task :app:testDebugUnitTest
311 tests completed, 5 failed
  - PackageSignatureResolverTest 4 件 (NPE @ Signature mock)  ← Phase 1 pre-existing
  - LockedFillResponseSecurityTest 1 件 (NPE @ Signature mock) ← Phase 1 pre-existing
```

- assembleDebug: BUILD SUCCESSFUL
- testDebugUnitTest: 311 件 (Iteration 1 前 305 件 → 新規追加 6 件 = layout
  tokens 1 件 + Robolectric empty state 5 件). 失敗 5 件は Phase 1
  pre-existing と完全一致 (本 PR で新たに失敗したテストは無し)

### Iteration 1 で追加された AC カバレッジ

- **Req 8.1**: 補足文を含む 5 要素を `applyEmptyStateVisibility_initial_showsHeroHeadlineBodyCtaAndFooter`
  + `CredentialListLayoutTokensTest.activityLayout_emptyStateContainsBodyCopyWithKnText2`
  でレイアウトと visibility の両層で pin
- **Req 8.3**: `activityLayout_emptyStateContainsBodyCopyWithKnText2`
  (Text.KeyNest.Body + kn_text_2 を同一要素で pin) +
  `applyEmptyStateVisibility_initial_bodyTextResolvesEmptyBodyString`
  (text 解決を pin)
- **Req 8.5**: `applyEmptyStateVisibility_noMatch_hidesHeroBodyCtaAndFooter`
  + `applyEmptyStateVisibility_initialThenNoMatch_flipsBodyAndCtaAndFooterToGone`
  で hero / body / CTA / footer の visibility 切替を直接検証

これにより、Reviewer round=1 で指摘された AC 未カバー (Req 8.3 補足文 欠落) と
missing test (Req 8.1 / 8.5 visibility 切替) は両方とも解消された.
