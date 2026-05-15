# Implementation Notes — Issue #33

`feat(settings): align SettingsActivity to design/screens-2.jsx ScreenSettings (Phase 2 #5)`

## サマリ

Issue #10 で実装された `SettingsActivity` / `DangerZoneActivity` /
`OssLicensesActivity` の 5 枚 MaterialCardView + Material3 Tonal/Text button
構成を、`design/screens/screens-2.jsx` の `ScreenSettings` モックに揃える
KeyNest セマンティック化 (Phase 1 #28 で導入された `@color/kn_*` /
`@dimen/kn_*` / `@style/Widget.KeyNest.*` / `@style/Text.KeyNest.*` の貼り
込み) 作業。

UI 表層のみを扱い、`SettingsViewModel.uiState` の計算 / Vault metadata
取得 / OSS ライセンス JSON ロード / Vault クリアロジック / 既存 View ID /
公開 API シグネチャ / AndroidManifest entry はすべて維持。

## 実装の主要ポイント

### 1. Drawable 層 (Phase 1 トークン層への追補)

Phase 1 で用意済みのトークン (`@color/kn_*` / `@dimen/kn_*`) を素材として、
Settings 専用のシェイプ drawable を新規 10 件追加した。

| Drawable | 用途 |
|---|---|
| `kn_settings_hero_gradient_enabled.xml` | Autofill hero (Enabled): `kn_primary` → `kn_blue_700` 斜めグラデ + `kn_r_lg` |
| `kn_settings_hero_bg_notenabled.xml` | Autofill hero (NotEnabled): `kn_warning_soft` + `kn_r_lg` |
| `kn_settings_hero_chip_bg_enabled.xml` | Hero status chip (Enabled): 白 20% + `kn_r_pill` |
| `kn_settings_hero_chip_bg_notenabled.xml` | Hero status chip (NotEnabled): `kn_warning` 20% + `kn_r_pill` |
| `kn_settings_hero_cta_bg_enabled.xml` | Hero CTA (Enabled): 白 18% + 白 30% stroke + `kn_r_sm` |
| `kn_settings_hero_cta_bg_notenabled.xml` | Hero CTA (NotEnabled): `kn_warning` 12% + 32% stroke |
| `kn_settings_group_bg.xml` | SettingGroup 共通: `kn_surface` + `kn_border` 1dp + `kn_r_md` |
| `kn_settings_group_bg_danger.xml` | Danger SettingGroup: `kn_danger_soft` + `kn_r_md` |
| `kn_settings_row_divider.xml` | SettingRow 行間 1dp 区切り線 |
| `kn_settings_row_icon_tile_bg.xml` | leading icon タイル: `kn_surface_2` + `kn_r_sm` |
| `kn_settings_danger_card_bg.xml` | DangerZoneActivity destructive カード: `kn_danger_soft` + `kn_r_md` |

加えて 9 個の vector icon を新規追加: `ic_fingerprint_24` (ロック解除方法) /
`ic_key_24` (件数) / `ic_clock_24` (最終更新) / `ic_database_24` (DB サイズ) /
`ic_keynest_mark_24` (KeyNest brand 簡略 silhouette) / `ic_description_24`
(OSS) / `ic_shield_outline_24` (Privacy) / `ic_warning_24` (Danger) /
`ic_chevron_right_24` (行末 chevron)。

### 2. Layout 層

3 layout ファイルを書き換え。

#### `settings_activity.xml`

- Root: `?android:attr/colorBackground` (Theme.KeyNest → `kn_bg`) +
  `@dimen/kn_screen_padding_h` (20dp) 横余白。
- Toolbar title TextAppearance: `Text.KeyNest.TitleS`。
- **Autofill hero**: 18dp 内パディングの `LinearLayout`。背景は
  `kn_settings_hero_gradient_enabled` を初期値とし、Activity bind コードで
  `AutofillStatus` に応じて差し替える。中に chip / hero title / 補足文 /
  hero CTA。
- **SettingGroup (4 件)**: Eyebrow header + 共通カード (
  `kn_settings_group_bg` / `kn_settings_group_bg_danger`) +
  `showDividers="middle"` で 1dp 区切り線。
- **SettingRow (8 件)**: horizontal LinearLayout = 32dp icon tile + (label +
  sub) + 右端 chevron|right-value。最小タッチ 48dp。
- 押下対象の行 (security / OSS / danger) は MaterialButton ではなく
  LinearLayout に既存 View ID (`btn_open_security_settings` /
  `btn_oss_licenses` / `btn_open_danger_zone`) を割り当て、
  `?attr/selectableItemBackground` で ripple。

#### `danger_zone_activity.xml`

- Material3 `?attr/colorErrorContainer` / `?attr/colorOnErrorContainer`
  直参照を撤去し、`kn_settings_danger_card_bg` 背景の素 LinearLayout
  カードに置換。
- `btn_clear` → `@style/Widget.KeyNest.Button.Destructive`。
- `btn_retry` → `@style/Widget.KeyNest.Button.Text` + `@color/kn_danger`。
- `text_section_heading` → `Text.KeyNest.TitleM` + `@color/kn_danger`。
- `text_description` → `Text.KeyNest.BodyS` + `@color/kn_text`。

#### `oss_licenses_item.xml`

- `Widget.Material3.CardView.Outlined` → `@style/Widget.KeyNest.Card`。
- `text_name` → `Text.KeyNest.TitleS`。
- `text_license` / `text_body` → `Text.KeyNest.BodyS` / `Text.KeyNest.Caption`
  + `@color/kn_text_2`。
- `btn_url` → `@style/Widget.KeyNest.Button.Text` + `@color/kn_primary`。

### 3. Activity 層

`SettingsActivity.kt`:
- `bindAutofill(status: AutofillStatus)` を二系統に分割
  (`applyAutofillHeroEnabled()` / `applyAutofillHeroNotEnabled()`)。
  Enabled では gradient + `kn_on_primary` (白) palette、NotEnabled では
  `kn_warning_soft` + `kn_warning` accent に切り替え。
- `chipAutofillStatus` に `settings_autofill_badge_a11y` composite contentDescription
  を再設定 (NFR 2.5)。
- `text_autofill_status` ID は hero の補足文 TextView に再割り当て
  (description テキストは AutofillStatus に応じて
  `settings_autofill_hero_description_enabled|not_enabled` を切替)。
- `btn_open_autofill_settings` は同 ID のまま hero 内 CTA に。
- `btn_open_security_settings` / `btn_oss_licenses` / `btn_open_danger_zone`
  も View 型を MaterialButton → LinearLayout に変えたが、`OnClickListener`
  経由のクリック発火 / 既存 Intent ロジックは無変更。

`DangerZoneActivity.kt` / `OssLicensesActivity.kt` / `OssLicensesAdapter.kt`:
- **無変更**。layout XML 側の置換だけで視覚整合が成立。

### 4. 文字列リソース

`values/strings.xml` と `values-ja/strings.xml` に新規 13 キーを同時追加:

- `settings_autofill_hero_title`
- `settings_autofill_hero_description_enabled`
- `settings_autofill_hero_description_not_enabled`
- `settings_autofill_hero_cta`
- `settings_lock_status_label`
- `settings_lock_status_a11y_label`
- `settings_about_keynest_label`
- `settings_about_privacy_label`
- `settings_about_privacy_sub`
- `settings_about_oss_licenses_a11y_label`
- `settings_danger_delete_all_label`
- `settings_danger_delete_all_sub`
- `settings_danger_delete_all_a11y_label`

Issue #10 で導入された既存 `settings_*` キー (26 件) は **en にのみ存在し ja
未訳のまま**。これは requirements.md の Open Question で「既存キーの ja
翻訳追加は別 Issue に切り出すか」を保留にしているため、本 Issue では新規
追加分のみを ja 翻訳した (NFR 3.4 の最低要求)。

## AC ↔ 実装ファイル / commit のマッピング

| Req | カバレッジ |
|---|---|
| 1.1 (`?attr/colorBackground` 解決) | `settings_activity.xml` / `settingsLayout_bindsRootBackgroundToColorBackgroundAttr` |
| 1.2 (`kn_screen_padding_h`) | `settings_activity.xml` / `settingsLayout_appliesScreenHorizontalPaddingToken` |
| 1.3 (Toolbar 保持) | `settings_activity.xml` / `settingsLayout_preservesToolbar` |
| 1.4 (Toolbar `Text.KeyNest.TitleS`) | `settings_activity.xml` / `settingsLayout_toolbarTitleUsesTitleSStyle` |
| 1.5 (`kn_space_*` 縦間隔) | `settings_activity.xml` (paddingBottom=`kn_space_6` / 行間 `kn_section_gap` / hero `kn_space_5` margin) — テストは horizontal padding で代替 |
| 2.1 (hero 配置) | `settings_activity.xml#group_autofill_hero` |
| 2.2 (`kn_r_lg` 20dp) | `kn_settings_hero_gradient_enabled.xml` / `heroGradientDrawable_usesPrimaryAndBlue700AndKnRLgCorners` |
| 2.3 (`kn_card_padding_lg`) | `settings_activity.xml` / `settingsLayout_heroAppliesCardPaddingLg` |
| 2.4 (斜めグラデ Enabled) | `kn_settings_hero_gradient_enabled.xml` (angle=315) |
| 2.5 (Enabled タイトル/補足文) | `settings_activity.xml#text_autofill_hero_title` / `text_autofill_status` + `SettingsActivity.applyAutofillHeroEnabled()` |
| 2.6 (Enabled chip 半透明白) | `kn_settings_hero_chip_bg_enabled.xml` / `heroChipEnabledDrawable_usesPillCorners` |
| 2.7 (NotEnabled palette) | `kn_settings_hero_bg_notenabled.xml` / `kn_settings_hero_chip_bg_notenabled.xml` + `SettingsActivity.applyAutofillHeroNotEnabled()` |
| 2.8 (chip 文字列) | `settings_activity.xml` + `SettingsActivity.bindAutofill()` |
| 2.9 (`Text.KeyNest.TitleM`) | `settings_activity.xml` / `settingsLayout_heroHasTitleAndAccessibilityHeading` |
| 2.10 (補足文 `Text.KeyNest.Caption`) | `settings_activity.xml` / `settingsLayout_heroDescriptionUsesCaptionStyleAndKnTextAutofillStatusId` |
| 2.11 (hero CTA 保持) | `settings_activity.xml#btn_open_autofill_settings` |
| 2.12 (CTA palette) | `kn_settings_hero_cta_bg_enabled.xml` / `kn_settings_hero_cta_bg_notenabled.xml` + `SettingsActivity.apply*()` |
| 2.13 (Autofill Intent) | `SettingsActivity.wireRows()` (無変更) |
| 2.14 (a11y 読み上げ順) | `settings_activity.xml` (DOM 順 = chip → title → 補足文 → CTA), `accessibilityHeading=true` on title |
| 3.1 (kn_surface) | `kn_settings_group_bg.xml` / `groupBgDrawable_usesKnSurfaceFillAndKnBorderStrokeAndKnRMd` |
| 3.2 (`kn_r_md` 16dp) | 同上 |
| 3.3 (`kn_border` 1dp) | 同上 |
| 3.4 (Eyebrow 配置) | `settings_activity.xml#eyebrow_*` / `settingsLayout_eyebrowHeadingsUseEyebrowStyleAndAccessibilityHeading` |
| 3.5 (`Text.KeyNest.Eyebrow`) | 同上 |
| 3.6 (`kn_text_3`) | 同上 |
| 3.7 (ヘッダ↔カード 8dp) | `settings_activity.xml` (`layout_marginBottom=@dimen/kn_space_2`) |
| 3.8 (左 4dp padding) | `settings_activity.xml` (`paddingHorizontal=@dimen/kn_space_1`) |
| 3.9 (`accessibilityHeading`) | テスト同上 |
| 3.10 (グループ間 24dp) | `settings_activity.xml` (`layout_marginBottom=@dimen/kn_section_gap`) |
| 4.1 (`kn_icon_tile_sm` 32dp) | `settings_activity.xml` / `settingsLayout_rowsApplyIconTileSmAndDividerAndShowDividersMiddle` |
| 4.2 (`kn_r_sm` 角丸) | `kn_settings_row_icon_tile_bg.xml` / `rowIconTileBgDrawable_*` |
| 4.3 (`kn_surface_2` 背景) | 同上 |
| 4.4 (`kn_text_2` icon tint) | `settings_activity.xml` (`app:tint="@color/kn_text_2"`) |
| 4.5 / 4.6 (label/sub 2 段) | `settings_activity.xml` 各 row の inner LinearLayout vertical |
| 4.6 (`Text.KeyNest.Body`) | 同上 |
| 4.7 (`kn_text`) | 同上 |
| 4.8 (`Text.KeyNest.Caption`) | 同上 (sub) |
| 4.9 (`kn_text_2`) | 同上 |
| 4.10 / 4.12 (chevron) | `ic_chevron_right_24.xml` + `settingsLayout_chevronImageReferencesIcChevronRight24` |
| 4.11 (right-value) | `text_vault_*` の Vault rows |
| 4.13 (destructive 派生) | `settings_activity.xml#btn_open_danger_zone` icon tint = `kn_danger`, label color = `kn_danger` / `settingsLayout_dangerRowUsesWarningIconAndKnDangerTint` |
| 4.14 (上下/左右 padding) | `settings_activity.xml` (`paddingHorizontal=kn_space_3` / `paddingVertical=kn_space_3`) |
| 4.15 (icon↔label gap) | `settings_activity.xml` (`layout_marginStart=@dimen/kn_space_3`) |
| 4.16 (48dp 最小タッチ) | `settings_activity.xml` (`minHeight=48dp` 各行) / `settingsLayout_rowsReserveMinTouchSize48dp` |
| 4.17 (1dp 区切り線) | `kn_settings_row_divider.xml` + `showDividers=middle` / `rowDividerDrawable_*` + `settingsLayout_rowsApplyIconTileSmAndDividerAndShowDividersMiddle` |
| 5.1..5.11 (セキュリティ) | `settings_activity.xml#group_security`, `btn_open_security_settings`, `text_lock_status` / `settingsLayout_lockStatusRow*` |
| 6.1..6.10 (Vault) | `settings_activity.xml#group_vault`, `text_vault_count` / `text_vault_latest_updated` / `text_vault_storage` / `settingsLayout_vault*` |
| 7.1..7.16 (About) | `settings_activity.xml#group_about`, `text_app_version`, `btn_oss_licenses` / `settingsLayout_about*` |
| 8.1..8.11 (Danger zone in Settings) | `settings_activity.xml#group_danger`, `btn_open_danger_zone` / `kn_settings_group_bg_danger.xml` / `settingsLayout_dangerRow*` |
| 9.1..9.7 (DangerZoneActivity) | `danger_zone_activity.xml` + `kn_settings_danger_card_bg.xml` / `dangerZoneLayout_*` |
| 10.1..10.6 (OssLicensesActivity item) | `oss_licenses_item.xml` / `ossLicensesItem_*` |
| 11.1..11.6 (Light/Dark) | values/colors.xml + values-night/colors.xml の Phase 1 セマンティックトークン解決 (新規 drawable はすべて `kn_*` トークン経由) |
| 12.1..12.8 (既存配線) | `SettingsActivity.kt` newIntent / ViewModel 接続 / onResume / 既存 View ID 保持 / 既存 String key 保持 / `settingsLayout_preservesAllExistingViewIds` / `stringsResource_preservesAllExistingSettingsStringKeys` |
| NFR 1.1 (assembleDebug) | `./gradlew :app:assembleDebug` 成功 |
| NFR 1.2 (既存単体テスト) | 既存 unit test の合否は本 Issue 前後で不変 (5 件の事前失敗は無関係) |
| NFR 1.3 (Material3ThemeMigrationTest / FontTypefaceWiringTest) | Theme.KeyNest / TextAppearance.KeyNest.* に変更を加えていない |
| NFR 2.1 (48dp タッチ) | `settingsLayout_rowsReserveMinTouchSize48dp` |
| NFR 2.2 (装飾/機能 a11y) | leading icon は `importantForAccessibility="no"`、押下対象に `contentDescription` |
| NFR 2.3 (`accessibilityHeading`) | テスト同上 + hero title |
| NFR 2.4 (コントラスト) | Phase 1 で定義済みのトークン (`kn_text` / `kn_text_2` / `kn_text_3` / `kn_warning` 等) を継承 |
| NFR 2.5 (chip composite a11y) | `SettingsActivity.bindAutofill()` で `contentDescription = settings_autofill_badge_a11y` を再設定 |
| NFR 3.1 / 3.4 (新規 string キー en+ja 同時追加) | `stringsResource_containsNewSettingsKeysInBothLocales` |
| NFR 3.2 / 3.3 (ロケール解決) | Android リソース解決の標準動作 (XML レベルでは確認できないため、テストはキー存在のみ確認) |

## テスト概要

新規追加: `app/src/test/java/com/example/keynest/resources/SettingsLayoutTokensTest.kt`
(47 ケース)。

戦略:
- Robolectric を使わず、`File.readText()` で XML を文字列として読み、AC ↔
  トークンを源コードレベルで pin する textual な layout token test。
  `PackagePickerLayoutTokensTest` (Issue #32) / `OnboardingLayoutTokensTest`
  (Issue #31) と同方針。
- 抽出は最初 Kotlin `Regex` の lazy match で `<Tag…id…/>` を切り出そうとしたが、
  multi-line XML + 階層化された LinearLayout/TextView で brittle だったため、
  `indexOf` + `substring(start, end)` の単純 slice に統一。
- 異常系 (Req 11.x の hex 直書き禁止) と境界値 (rowsReserveMinTouchSize48dp で
  `48dp` 出現回数 ≥ 4) を含む。
- 新規キー en + ja の双方存在を pin することで NFR 3.x の同時追加違反を検出。

ビルド結果:
- `./gradlew :app:assembleDebug`: 成功
- `./gradlew :app:testDebugUnitTest --tests "*.SettingsLayoutTokensTest*"`:
  47/47 passed
- `./gradlew :app:testDebugUnitTest`: 453 件中 447 成功 / 5 件は事前失敗
  (`LockedFillResponseSecurityTest` / `PackageSignatureResolverTest` の NPE。
  本 Issue 無関係。develop でも同条件で失敗することを確認済)

## 確認事項 (Architect / 人間判断が必要な未解決事項)

PR レビューでの判断ポイント:

1. **Open Question (requirements.md L273)**: 「`@style/Widget.KeyNest.SettingGroup`
   / `@style/Widget.KeyNest.SettingRow` を新規定義するか、既存トークンを
   個別に貼って同等視覚を実現するか」 → 本 Issue は **後者** (個別貼り)
   で実装した。理由: ① 同等視覚は drawable + 既存 `Text.KeyNest.*` で達成
   できる、② SettingGroup / SettingRow を共通 style 化すると `<style>`
   の attribute では `android:background` を default 上書きしか出来ず、
   `kn_settings_group_bg` と `kn_settings_group_bg_danger` を行ごとに切り
   替えるレイアウト多態が逆に複雑になる、③ Phase 2 の他 Issue で同等
   pattern が今後発生する見込みが低い (Phase 2 mapping.md §5 は ScreenSettings
   が最後)。将来必要になれば別 Issue で `Widget.KeyNest.SettingGroup` /
   `Widget.KeyNest.SettingRow` を切り出して既存レイアウトを書き換える
   方針で良いか?
2. **Open Question (requirements.md L274)**: 「Autofill ステータス hero の
   gradient 実現手法」 → 本 Issue は **静的 drawable XML** で `<gradient
   angle="315">` を使う方式を採用した。CSS の 140deg は厳密には Android の
   45deg 刻みでは表現できないため、最も近い 315deg (top-left → bottom-right
   = CSS 135deg 相当) を採用。視覚差はサブピクセルレベル。これで十分か、
   それとも `ShapeDrawable` を Activity 側で動的に組み立てて 140deg を厳密に
   再現すべきか?
3. **Open Question (requirements.md L275)**: 「NotEnabled hero 上 CTA の
   palette 選定」 → 本 Issue は `kn_warning` 12% 背景 + `kn_warning` 32%
   stroke + `kn_warning` text に倒した (warning accent を強調)。Architect
   判断で確定して良いか?
4. **Open Question (requirements.md L276)**: 「実装する 7 行 + Out of Scope の
   3 行 (アンロック保持時間 / 署名照合の厳密性 / エクスポート) の選別」 →
   本 Issue は requirements.md 通り 7 行のみ実装。将来必要になれば別 Issue
   で追加する方針で良いか?
5. **Open Question (requirements.md L277)**: 「既存 `SettingsActivityTest`
   の `withId(R.id.btn_open_*)` が壊れないことの担保」 → 既存テストは
   `@Ignore` でスキップ中 (manual UI verification 待ち)。本 Issue では
   `btn_open_security_settings` / `btn_oss_licenses` / `btn_open_danger_zone`
   の View 型を MaterialButton → LinearLayout に変更したが、ID は維持。
   Espresso の `withId` は View 型を問わないため `perform(click())` は
   そのまま動作するはず。テストが ignore 解除されたタイミングで manual
   検証が必要。
6. **Open Question (requirements.md L278)**: 「既存 `settings_*` キー (26 件)
   の ja 翻訳を本 Issue に含めるか」 → 本 Issue では **新規追加分 13 キー
   のみ ja 翻訳** に留めた。既存キーの翻訳は別 Issue (例えば「Phase 2 #5
   follow-up: Issue #10 settings strings ja 翻訳」) を切り出す前提で良いか?
   この判断は Open Question で「別 Issue に切り出すべきか」と書かれて
   いた通り、conservative を選択。
7. **Open Question (requirements.md L279)**: 「JSX フッター caption (AES-GCM
   説明文) を本 Issue で追加するか」 → 本 Issue では **追加しない** 判断
   を取った。理由: requirements.md Out of Scope で同 caption が明示的に
   除外されており、Open Question では「設計判断で追加可能」と書かれて
   いたが、design.md が存在しない (Architect 起動なしの直行 Issue) ため
   現状では追加根拠が薄い。将来必要になれば別 Issue で対応する想定で
   良いか?
8. **Design judgment (本 Issue 内)**:
   - `text_autofill_status` 既存 ID の意味変更: Issue #10 では `text_autofill_status`
     は「Enabled / Not set」のステータス文字列を表示する `?attr/textAppearanceBodyLarge`
     な TextView だった。Issue #33 では同 ID を「Autofill サービスの説明文」
     (settings_autofill_hero_description_*) を表示する補足文 TextView に再
     割り当てした。これにより既存テストが文字列を assertion すると壊れる
     可能性がある (`@Ignore` 中の `autofillBadge_showsNotEnabled_*` テスト)。
     ステータス文字列 (Enabled / Not set) は別途新規 `chip_autofill_status`
     ID で出すように分離。
   - `btn_open_security_settings` / `btn_oss_licenses` / `btn_open_danger_zone`
     の View 型を `MaterialButton` → `LinearLayout` に変更。`OnClickListener`
     経由のクリック発火は維持。
   - DangerZoneActivity の hero カードを `MaterialCardView` → 素 `LinearLayout`
     に変更。`app:cardBackgroundColor` を介した Material attribute 直参照を
     撤去し、`android:background="@drawable/kn_settings_danger_card_bg"` に
     置換。`MaterialCardView` の外枠 (`strokeColor` / `strokeWidth`) は
     Danger zone では使わない設計判断 (Req 9 で外枠の指示なし)。
9. **Lint warning**: `accessibilityHeading` は API 28+ のみ有効 (minSdk
   = 26)。lint は warning を出すがランタイムでは無視されるため意図通り。
   将来 minSdk を 28 に上げた場合 warning は消える。
10. **vector path 長**: `ic_fingerprint_24` の path が 2089 文字あり lint
    が "Very long vector path" を警告。これは Material Symbols の
    fingerprint outline path をそのまま採用したためで、機能的問題は
    なし。簡略化するなら別 Issue で扱う。

## 副次変更 (本 Issue 内)

- `values/colors.xml`: `@color/kn_hero_ripple_on_primary` (#33FFFFFF) を
  追加。`settings_activity.xml` の hero CTA `app:rippleColor` 用。
- `values/strings.xml` / `values-ja/strings.xml`: 13 個の新規 string キーを
  同時追加。
