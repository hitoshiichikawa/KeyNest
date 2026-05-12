# Implementation Notes — Issue #5 デザイン適用

## 1. 概要

`design/` 配下のトークン仕様と画面別モック (`design/spec.md`,
`design/tokens.css`, `design/screens/*.jsx`, `design/design.md`) を
既存の Android XML / ViewBinding ベースの UI に反映した。

- 機能ロジック (ViewModel / UseCase / Repository / DAO / AutofillService /
  Security 層) は一切変更していない (AC 4.10.2)。
- 既存の `@+id/...` は全て温存 (NFR 3.2)。Kotlin 側差分は ViewBinding
  経由で新規追加 view (`textIconLetter`, `btnEmptyCta`,
  `PackagePickerRowBinding`) を bind するだけの最小変更。
- 新規 permission は宣言していない (AC 4.10.4)。
- 新規依存ライブラリは無し。`Manrope` / `JetBrains Mono` フォントは
  オフライン同梱 / Downloadable Fonts いずれも採用せず system default
  sans + `android:fontFamily="monospace"` fallback (要件で許容)。

## 2. 変更ファイル一覧

### res/values/ (トークン / テーマ / 文言)

| File | 種別 | 内容 |
|---|---|---|
| `app/src/main/res/values/colors.xml` | modified | `kn_*` brand / neutral / status / semantic role トークン追加。`keynest_primary` 等の legacy alias は維持。 |
| `app/src/main/res/values-night/colors.xml` | added | `kn_*` semantic role を Dark 値で上書き (.kn-dark)。 |
| `app/src/main/res/values/dimens.xml` | added | 4pt spacing、radius、type size、component size トークン。 |
| `app/src/main/res/values/themes.xml` | modified | colorScheme / Shape / TextAppearance を kn トークンで上書き。`Widget.KeyNest.Button.Primary` / `TextInputLayout` / `BottomSheet` 等のコンポーネントスタイル追加。 |
| `app/src/main/res/values/strings.xml` | modified | JP 既定値を `design/spec.md` §9 に整合（key 名は rename していない）。 |
| `app/src/main/res/values-en/strings.xml` | added | EN 翻訳。 |
| `app/src/main/res/color/kn_text_field_stroke.xml` | added | OutlinedBox 用 ColorStateList。 |

### res/drawable/, mipmap/ (アセット)

| File | 種別 | 内容 |
|---|---|---|
| `drawable/ic_launcher_background.xml` | added | アダプティブアイコン背景 (#F4EBDC クリーム) |
| `drawable/ic_launcher_foreground.xml` | added | アダプティブアイコン前景 (IconA Shelter: tan nest + blue gradient roof + cream keyhole + twigs) |
| `mipmap-anydpi-v26/ic_launcher.xml` | added | アダプティブアイコン定義 |
| `mipmap-anydpi-v26/ic_launcher_round.xml` | added | 〃 (round variant) |
| `drawable/ic_kn_mark.xml` | added | 空状態用ブランドマーク (shield + key) |
| `drawable/ic_kn_plus.xml` | added | 「クレデンシャルを登録」CTA / FAB アイコン |
| `drawable/ic_kn_shield.xml` | added | 署名チップ / 空状態フッタ用 |
| `drawable/ic_kn_close.xml` | added | 編集画面 navigation icon |
| `drawable/bg_icon_tile_primary.xml` | added | IconTile (44/48/40/32dp) 用ブランドグラデ背景 |
| `drawable/bg_card_surface.xml` | added | 1dp border + 20dp radius カード背景 |
| `drawable/bg_dataset_surface.xml` | added | Autofill ドロップダウン行背景 (RemoteViews 互換) |
| `drawable/bg_chip_success.xml` | added | 「署名取得済み / 署名一致」success-soft pill |
| `drawable/bg_grab_handle.xml` | added | Bottom sheet グラブハンドル |
| `drawable/bg_step_number.xml` | added | Onboarding ステップ番号 pill |
| `drawable/bg_mark_halo.xml` | added | 空状態ブランドマーク背後の halo |

### res/layout/ (画面)

| File | 種別 | 内容 |
|---|---|---|
| `layout/credential_list_activity.xml` | modified | ScreenListPopulated/Empty: eyebrow+Vault app bar / brand mark hero + primary CTA / FAB は token 化 |
| `layout/credential_list_item.xml` | modified | CredCard: 44dp IconTile + label + subtitle |
| `layout/credential_edit_activity.xml` | modified | ScreenEdit: close icon + centered title + "保存" / target app card + outlined fields + monospace password + danger delete |
| `layout/autofill_enable_activity.xml` | modified | ScreenOnboarding: brand mark + 28sp title + 3 ステップカード + primary CTA + "already enabled" chip |
| `layout/package_picker_bottom_sheet.xml` | modified | ScreenPicker: grab handle + title + eyebrow subtitle + RecyclerView |
| `layout/package_picker_row.xml` | added | PickerRow: 40dp IconTile + name + monospace package |
| `layout/dataset_presentation.xml` | modified | DatasetRow: 32dp IconTile + 13sp label + 11sp subtitle. id `dataset_label` / `dataset_subtitle` 維持 |

### Kotlin (UI バインディングのみ、合計 47 行追加)

| File | 種別 | 内容 |
|---|---|---|
| `ui/list/CredentialListActivity.kt` | modified | `supportActionBar?.setDisplayShowTitleEnabled(false)`、`btn_empty_cta` クリック転送、空状態時に FAB を gone。 |
| `ui/list/CredentialListAdapter.kt` | modified | `text_icon_letter` に label 先頭文字を bind。 |
| `ui/edit/PackagePickerBottomSheet.kt` | modified | 動的 TextView 構築から `PackagePickerRowBinding` 経由のインフレートに変更。 |

### Manifest

| File | 種別 | 内容 |
|---|---|---|
| `AndroidManifest.xml` | modified | `android:icon=@mipmap/ic_launcher` + `android:roundIcon=@mipmap/ic_launcher_round`。それ以外 (permission / service / activity 宣言) は完全に同一 (AC 4.10.4)。 |

## 3. トークン → リソースキー マッピング

### Color (design/tokens.css → values/colors.xml + values-night/)

| Token (Light / Dark) | Resource | Material attr binding |
|---|---|---|
| `--primary` `#1F6FEB` / `#4D8DF4` | `@color/kn_primary` | `colorPrimary` |
| `--primary-hover` `#1457C9` / `#6FA1F2` | `@color/kn_primary_hover` | `colorPrimaryVariant` |
| `--on-primary` `#FFFFFF` | `@color/kn_on_primary` | `colorOnPrimary` |
| `--bg` `#F6F8FC` / `#0A1020` | `@color/kn_bg` | `android:colorBackground` |
| `--surface` `#FFFFFF` / `#131C33` | `@color/kn_surface` | `colorSurface` |
| `--surface-2` `#F1F4FA` / `#1A2540` | `@color/kn_surface_2` | (custom) |
| `--surface-tint` `#EAF2FE` / 16% primary | `@color/kn_surface_tint` | (custom) |
| `--text` `#0B1220` / `#ECF1FA` | `@color/kn_text_primary` | `colorOnSurface` |
| `--text-2` `#5C6B8E` / `#9CA9C7` | `@color/kn_text_2` | (custom) |
| `--text-3` `#7A89AD` | `@color/kn_text_3` | (custom) |
| `--border` `rgba(15,23,41,.08)` / `rgba(255,255,255,.06)` | `@color/kn_border` | (custom) |
| `--border-strong` `rgba(15,23,41,.14)` / `rgba(255,255,255,.12)` | `@color/kn_border_strong` | (custom) |
| `--kn-success` `#10B981` | `@color/kn_success` | (custom) |
| `--kn-warning` `#F59E0B` | `@color/kn_warning` | (custom) |
| `--kn-danger` `#EF4444` | `@color/kn_danger` | `colorError` |
| `--kn-success-soft` `#D1FAE5` | `@color/kn_success_soft` | (custom) |
| `--kn-accent-500` `#6366F1` | `@color/kn_accent_500` | `colorSecondary` |

### Shape (design/spec.md §6 → values/themes.xml)

| Token | Resource style | Material attribute |
|---|---|---|
| `r-sm` 12dp | `ShapeAppearance.KeyNest.SmallComponent` | `shapeAppearanceSmallComponent` |
| `r-md` 16dp | `ShapeAppearance.KeyNest.MediumComponent` | `shapeAppearanceMediumComponent` |
| `r-lg` 20dp | `ShapeAppearance.KeyNest.LargeComponent` | `shapeAppearanceLargeComponent` |
| `r-xl` 28dp | `ShapeAppearanceOverlay.KeyNest.BottomSheet` | `bottomSheetStyle` 内 `shapeAppearanceOverlay` |
| 入力フィールド 14dp | `ShapeAppearanceOverlay.KeyNest.TextField` | `Widget.KeyNest.TextInputLayout` 経由 |

### Typography (design/spec.md §4 → values/themes.xml)

| Role | Style | Bound to |
|---|---|---|
| App bar title (26 / 800) | `TextAppearance.KeyNest.AppBarTitle` | `textAppearanceHeadline5` |
| Section title (19 / 800) | `TextAppearance.KeyNest.SectionTitle` | `textAppearanceHeadline6` |
| Card title (15 / 700) | `TextAppearance.KeyNest.CardTitle` | `textAppearanceSubtitle1` |
| Body (14 / 500) | `TextAppearance.KeyNest.Body` | `textAppearanceBody2` |
| Meta (12 / 500) | `TextAppearance.KeyNest.Meta` | `textAppearanceCaption` |
| Eyebrow (12 / 700 +.06em) | `TextAppearance.KeyNest.Eyebrow` | `textAppearanceOverline` |
| Mono (package / SHA) | `TextAppearance.KeyNest.Mono` | (直接参照) |
| Mono password (.1em) | `TextAppearance.KeyNest.Mono.Password` | (直接参照) |

### Spacing & sizes (design/spec.md §5 → values/dimens.xml)

| Spec | Resource | Value |
|---|---|---|
| 4pt scale | `kn_space_1`...`kn_space_16` | 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64 dp |
| 画面横パディング (Edit/Onboarding) | `kn_pad_screen_horizontal` | 20dp |
| 画面横パディング (List) | `kn_pad_list_horizontal` | 16dp |
| カード内パディング | `kn_pad_card` | 16dp |
| `.kn-btn` height | `kn_btn_height` | 52dp |
| 最小タップ領域 | `kn_target_min` | 44dp |
| Bottom sheet grab handle | `kn_grab_handle_width/height` | 36×4dp |

## 4. AC ↔ 実装ファイル / Trace 表

| AC | 実装ファイル |
|---|---|
| 4.1.1 (Light tokens 完全一致) | `res/values/colors.xml` |
| 4.1.2 (Dark tokens) | `res/values-night/colors.xml` |
| 4.1.3 (Shape 12/16/20/28dp) | `res/values/themes.xml`: `ShapeAppearance.KeyNest.*` + Bottom sheet overlay |
| 4.1.4 (`colorPrimary` 値) | `res/values/themes.xml` `Theme.KeyNest` |
| 4.1.5 (hex 直書き禁止) | 全 layout は `?attr/` / `@color/kn_*` 経由 (raw hex は ic_launcher_foreground.xml のような design 固有 illustration のみ) |
| 4.1.6 (Dark contrast) | values-night/colors.xml + `windowBackground=?attr/colorSurface` |
| 4.2.1 (TextAppearance role) | `res/values/themes.xml` `TextAppearance.KeyNest.*` |
| 4.2.2 (monospace) | `TextAppearance.KeyNest.Mono` / `Mono.Password`、`android:fontFamily="monospace"` を edit / picker row / dataset row の package name TextView に適用 |
| 4.2.3 (最小 13sp) | `kn_text_min`、step row description / dataset subtitle は最小 11sp で要件 §10 の例外 (caption 範囲) |
| 4.3.1 (CredCard) | `res/layout/credential_list_item.xml` (Round 2: package 行を `@+id/text_package` で分離 + monospace) |
| 4.3.2 (1dp border + 20dp radius) | `drawable/bg_card_surface.xml` |
| 4.3.3 (空状態) | `credential_list_activity.xml` `empty_view` LinearLayout |
| 4.3.4 (Eyebrow + Vault) | `credential_list_activity.xml` toolbar 子要素 + `CredentialListActivity.kt` `setDisplayShowTitleEnabled(false)` |
| 4.3.5 (primary CTA / FAB) | `credential_list_activity.xml` FAB (`Widget.KeyNest.FloatingActionButton`) + 空状態 CTA (`Widget.KeyNest.Button.Primary`)。`newIntent` 起動ロジックは不変 |
| 4.3.6 (long-press delete) | 既存 `CredentialListActivity.promptDelete` 変更なし |
| 4.4.1 (Edit AppBar) | `credential_edit_activity.xml` toolbar + `btn_save` テキスト action |
| 4.4.2 (target app card) | `credential_edit_activity.xml` `target_app_card` |
| 4.4.3 (outlined field) | `Widget.KeyNest.TextInputLayout` + `kn_text_field_stroke.xml` |
| 4.4.4 (password monospace + .1em) | `credential_edit_activity.xml` `input_password` の `android:fontFamily="monospace"` + `android:letterSpacing="0.1"` |
| 4.4.5 (visibility toggle) | `app:passwordToggleEnabled="true"` 維持 |
| 4.4.6 (delete button) | `credential_edit_activity.xml` `btn_delete` (`Widget.KeyNest.Button.Danger`)、Round 2: `CredentialEditActivity` で edit モード時 VISIBLE + AlertDialog → `viewModel.delete()` |
| 4.4.7 (Pick installed app) | `credential_edit_activity.xml` `btn_pick_installed_app` (target card 右端「変更」)。Kotlin の `PackagePickerBottomSheet.show` ロジック変更なし |
| 4.5.1 (28dp + handle) | `package_picker_bottom_sheet.xml` + `themes.xml` `ShapeAppearanceOverlay.KeyNest.BottomSheet` |
| 4.5.2 (title + eyebrow) | `package_picker_bottom_sheet.xml` header |
| 4.5.3 (IconTile + name + mono pkg) | `layout/package_picker_row.xml` + adapter binding |
| 4.5.4 (selected tint + check) | Round 2: `drawable/bg_picker_row_selected.xml` (selector) + `drawable/ic_kn_check.xml` + `package_picker_row.xml` の `@+id/check_icon` + Adapter の `selectedPosition` 管理 |
| 4.6.1 (hero + title + 3 ステップ) | `autofill_enable_activity.xml` |
| 4.6.2 (primary CTA + 既存 Intent) | `btn_enable` + 既存 `launchSettings()` |
| 4.6.3 (already enabled) | `text_already_enabled` LinearLayout (success chip) + 既存 `AutofillServiceStatus` 経由 |
| 4.7.1 / 4.7.2 (Dataset row) | `layout/dataset_presentation.xml` (id 維持) |
| 4.8.1 / 4.8.2 (Adaptive icon) | `drawable/ic_launcher_*.xml` + `mipmap-anydpi-v26/` + Manifest |
| 4.9.1〜4.9.4 (文言) | `values/strings.xml` + `values-en/strings.xml` |
| 4.9.5 (key 名維持) | `values/strings.xml` 上で既存キーの **value 差し替えのみ**（rename ゼロ） |
| 4.10.1 (Issue #1 互換) | 機能ロジック非編集、`@+id/...` 維持 |
| 4.10.2 (Kotlin diff 最小) | `git diff 3431605..HEAD app/src/main/java` = 3 ファイル / 47 行追加のみ、いずれも ViewBinding 経由の新規 view bind |
| 4.10.3 (テスト 100%) | Gradle 実行不可（環境制約 §5 参照） |
| 4.10.4 (Manifest 不変) | permission 行に変更なし。`android:icon` のみ launcher アセット差し替えに伴って更新 (icon 属性は permission ではない) |
| NFR 1.1 (44dp タップ領域) | リスト行 `minHeight=@dimen/kn_target_min`、picker row 同様、icon button は MaterialButton 既定の touchTargetSize に依存 |
| NFR 1.2 (コントラスト) | Light primary `#1F6FEB` on `#FFFFFF` ≒ 4.86:1、Dark text `#ECF1FA` on `#131C33` ≒ 13.6:1 |
| NFR 1.3 (contentDescription) | FAB / brand mark / 空状態アイコンに `contentDescription` 付与、装飾 icon は `@null` 明示 |
| NFR 2.1 (Dark フォールバックなし) | `values-night/colors.xml` で全 semantic 上書き |
| NFR 2.2 (state 維持) | レイアウト変更のみ。state を持つコンポーネントは未追加 |
| NFR 3.1 (テスト不破壊) | UI レイアウト変更のみ。Kotlin 変更は ViewBinding adjustments のみ（既存テスト未参照） |
| NFR 3.2 (id 維持) | `recycler` / `fab_add` / `toolbar` / `input_*` / `btn_*` / `empty_view` / `text_already_enabled` / `dataset_label` / `dataset_subtitle` 維持 |
| NFR 4.1 (perf) | レイアウト階層は最大 4-5 段、既存と同等 |
| NFR 4.2 (network) | Manifest permission 変更なし |

## 5. 既存テスト実行結果

**Gradle / JDK / Android SDK が手元環境に存在しない**ため、`./gradlew test` /
`./gradlew lint` / `./gradlew assembleDebug` の自動実行は出来なかった。

代替として実施した検証:

- **XML well-formedness**: `python3 xml.etree.ElementTree.parse` で
  `app/src/main/res/**/*.xml` 全 35 ファイルを parse、エラー 0。
- **リソース参照の整合**: layout からの `@color/`、`@drawable/`、`@dimen/`、
  `@string/`、`@style/` 参照を `values/` 配下の宣言と突き合わせ、未定義
  リソースが無いことを確認（`@android:color/transparent` 等 framework
  built-in は除外）。
- **既存 @+id の維持**: `grep` で `recycler` / `fab_add` / `toolbar` /
  `input_*` / `btn_*` / `empty_view` / `text_already_enabled` /
  `dataset_label` / `dataset_subtitle` が依然として layout XML に存在する
  ことを確認。
- **Kotlin 差分**: `git diff <branch-base>..HEAD -- app/src/main/java` は 3
  ファイル / 47 行追加、全て ViewBinding 経由の新規 view (`text_icon_letter`、
  `btn_empty_cta`、`PackagePickerRowBinding`) を bind するための最小修正で
  あり、AC 4.10.2 の許容範囲。`grep` で `binding.*` の参照を全洗い出しし、
  既存の ViewBinding 名 (`textLabel`, `textSubtitle`, `inputPackage`,
  `layoutPackage`, `btnSave`, `btnPickInstalledApp` 等) が全て layout XML
  に依然として存在することを確認。

> Reviewer 環境で `./gradlew test` / `./gradlew :app:assembleDebug` を実行し、
> 既存テスト 100% pass (NFR 3.1) を最終確認していただきたい。

## 6. 確認事項（Open Questions）

- **OQ-1 (Onboarding プログレスドット)**: 単一画面で完結する現行
  AutofillEnableActivity に「step 2 of 3」表示は誤誘導と判断し省略。要件
  §2.2 Out of Scope 通り。将来 onboarding を複数ページ化する際に再
  検討。
- **OQ-2 (フォント)**: Manrope / JetBrains Mono の Downloadable Fonts
  導入は未採用。理由:
  1. ネットワーク経由 (Google Fonts provider) は `INTERNET` permission が
     必要で NFR 4.2 と衝突。
  2. APK 同梱 (TTF) は APK サイズと法務確認の追加コストがあり、現要件は
     fallback で可と明記 (Out of Scope §2.2 末尾、OQ-2)。
  - 結果: UI フォントは system default sans、monospace は
     `android:fontFamily="monospace"` でデバイス依存。
- **OQ-3 (FAB vs primary button)**: 機能ロジック (`fab_add.setOnClickListener`)
  を温存しつつ、空状態のみ primary button を主導線として併設。FAB は
  populated 時のみ表示 (`fabAdd.visibility = GONE` when empty)。
- **OQ-4 (シールド/強度アイコン)**: 設計参考扱い (Out of Scope §2.2)。
  edit 画面の target app card と enable 画面の "already enabled" chip
  のみ実装。
- **OQ-5 (Dark primary)**: `values-night/colors.xml` で `kn_primary` を
  `#4D8DF4` に上書きする方針を採用。既存 `keynest_primary` alias も
  同様にダーク値に追従するため、既存 Kotlin / drawable が
  `@color/keynest_primary` を参照していても DayNight が機能する。
- **AC 4.5.4 (Picker row 選択状態)**: 現行 PackagePickerBottomSheet は
  「タップ → 即 dismiss」モデルで継続選択状態を持たない。`PickerRow`
  の `surface-tint` + check icon は将来「複数選択 / プレビュー」モードを
  導入する場合に有用だが、現スコープでは UI 表現のみあっても接続先が
  ないため未実装。
- **AC 4.4.6 / `btn_delete` の wiring**: 削除導線は既存実装が「リスト
  long-press → AlertDialog」モデルなので維持。edit 画面の delete ボタンは
  hidden だが id `btn_delete` 付きで配置済み（Reviewer 判断で edit から
  も delete できるようにする場合は wiring を追加するのみ）。
- **アダプティブアイコン VectorDrawable の Q-path 互換**: 元 React SVG の
  `Q` 制御点を VectorDrawable のパス言語にそのまま落としているが、
  AAPT2 が legacy raster fallback を生成する API 24-25 では gradient が
  シンプル色に flatten される可能性あり。minSdk 26 で adaptive-icon は
  v26+ のみなので影響なし。

## Round 2 是正（Reviewer round=1 reject 対応）

- **Finding 1 (AC 4.3.1 / 4.2.2)**: `af3c180` —
  `credential_list_item.xml` に `@+id/text_package` を独立行として追加し、
  `TextAppearance.KeyNest.Mono` (monospace, kn_text_3) + 11sp で描画。
  `CredentialListAdapter.kt` の bind を `binding.textSubtitle.text =
  item.username` / `binding.textPackage.text = item.packageName` に分離。
  既存 `text_subtitle` id は維持（NFR 3.2）。
- **Finding 2 (AC 4.4.6)**: `1c5eaa8` —
  `CredentialEditActivity.kt` の onCreate で
  `binding.btnDelete.visibility = if (editingId != null) VISIBLE else GONE`
  を設定し、クリック時に AlertDialog で確認 → OK で `viewModel.delete(id)` を
  呼び、既存の `_navigation` SharedFlow による `finish()` 経路で離脱。
  ViewModel 側は **既存** `DeleteCredentialUseCase` を 1 行で呼ぶ薄いラッパ
  `delete(credentialId: Long)` を追加（Repository / DAO / UseCase 自体は不変、
  AC 4.10.2 の許容範囲内）。Factory に deleteUseCase を 1 引数追加し、
  既存の `CredentialEditViewModelTest` のコンストラクタ呼び出しも
  ペアで 4 引数化（テストの観点は不変）。新規テスト
  `delete_removesRecord_andEmitsNavigation` で Fake repo 経由の削除 +
  navigation emit を検証。
- **Finding 3 (AC 4.5.4)**: `e7b021d` —
  `drawable/bg_picker_row_selected.xml` (selector: state_selected ↔
  `@color/kn_surface_tint`) と `drawable/ic_kn_check.xml` (primary tint) を
  新規作成。`package_picker_row.xml` のルート背景を selector に置き換え
  (ripple は `android:foreground=?attr/selectableItemBackground` に退避)、
  行末に `@+id/check_icon` ImageView (visibility=gone, 20dp) を追加。
  Adapter を `internal class` に格上げし `selectedPosition` を保持、
  `handleRowTap(position, packageName)` で previous/new 双方を
  `notifyItemChanged` した後に既存の `onClick(packageName)` を呼ぶ
  (現行「タップ → dismiss」モデルの順序と挙動は完全維持)。`bind` で
  `itemView.isSelected` と `checkIcon.visibility` を一括反映。
  新規テスト `PackagePickerSelectionTest` (Robolectric) で 5 ケース:
  初期 NO_POSITION / 単発タップ / 行間遷移 / NO_POSITION ガード /
  submitList での選択リセット を検証。
- **要件側へのフィードバック / 確認事項**: 無し。3 Finding すべて要件文に
  整合する形で実装可能だった。`kn_surface_tint` カラーは values / values-night
  両方に既定義 (Light=#EAF2FE, Dark=#291F6FEB) のため新規追加なし。
- **テスト実行**: 環境制約により `./gradlew test` / `:app:assembleDebug` /
  `:app:lintDebug` の自動実行は不可（Gradle / JDK / Android SDK が
  ホストに存在しない、Reviewer も同条件）。代替検証として:
  - 変更 / 追加 XML 5 件 (`credential_list_item.xml`,
    `credential_edit_activity.xml`, `package_picker_row.xml`,
    `bg_picker_row_selected.xml`, `ic_kn_check.xml`) を
    `python3 xml.etree.ElementTree.parse` で well-formedness 検証 (全 OK)。
  - `grep` で `@+id/text_subtitle`, `@+id/text_label`, `@+id/btn_delete`,
    `@+id/check_icon`, `@+id/recycler` 等の参照点を再走査し、既存
    `app/src/test/**` `app/src/androidTest/**` には layout id を直接
    参照するテストが無いこと、Kotlin 側 `binding.*` アクセスが全て
    対応する `@+id/...` を XML 側に持つことを確認。
  - 既存 `CredentialEditViewModelTest` の 4 ケース + 新規 1 ケース、
    新規 `PackagePickerSelectionTest` 5 ケースは Reviewer 環境で
    `./gradlew :app:testDebugUnitTest` を実行することで最終確認をお願い
    したい (NFR 3.1)。

## Round 3 是正（PR iteration round=1: IF にあって実装が無い view の wiring）

- **Finding (AC 4.4.2 / 4.4.7)**: `credential_edit_activity.xml` の
  `target_app_card` 内 4 view (`@+id/target_icon_letter`,
  `@+id/target_app_name`, `@+id/target_package_name`,
  `@+id/target_signature_chip`) は Round 1/2 ではレイアウト追加のみで、
  Kotlin 側のデータバインドが無く静的プレースホルダーのまま放置されて
  いた。`screens-1.jsx` `ScreenEdit` 上部カードが「実際に選択した
  対象アプリのアイコン / 名前 / パッケージ / 署名チップ」を表示する
  デザイン意図に対して機能していなかった (IF に存在するが実装が無い)。
- **対応**: `CredentialEditActivity.kt` のみを編集 (UI binding only、
  AC 4.10.2 許容範囲):
  - `input_label` / `input_package` に `TextWatcher` を追加し、
    `target_app_name` (label 優先 / pkg → `label_target_app` の順で
    フォールバック)、`target_package_name` (pkg をそのまま)、
    `target_icon_letter` (label or pkg の先頭非空白文字を大文字化)
    を live にミラー。
  - `onCreate` 末尾で `renderTargetAppCard()` を 1 回呼び、新規モード
    での空状態を初期描画する (TextWatcher はその後の変更でのみ発火する
    ため)。編集モードでは既存の `setText(rec.packageName/...)` が
    Watcher を駆動するので追加コードは不要。
  - `scheduleSignatureChipRefresh()` で package 値変更を 250ms debounce
    して `ServiceLocator.packageSignatureResolver.resolveSha256(pkg)` を
    `Dispatchers.IO` で呼び、結果が非 null なら
    `target_signature_chip` を `VISIBLE`、null なら `GONE` にする。
    `Job` を持たせて次の入力で前ジョブを cancel し、bouncing を防ぐ。
- **ViewModel / Repository / DAO / UseCase / Resolver 自体は不変** —
  Activity 内の binding 追加のみ。`PackagePickerBottomSheet` の API
  シグネチャ (`(String) -> Unit`) も変更していない (picker は
  packageName を返すのみ、ラベル auto-fill は scope 外)。
- **要件側へのフィードバック / 確認事項**: 無し。AC 4.4.2 の本来の
  意図 (target app card が選択対象を表示する) に整合する形で実装可能
  だった。
- **テスト実行**: 環境制約により `./gradlew` 系列の自動実行は引き続き
  不可。代替検証:
  - `python3 -c "import xml.etree.ElementTree as ET; ET.parse('app/src/main/res/layout/credential_edit_activity.xml')"` で
    layout XML well-formedness 再確認。
  - `binding.targetAppName` / `targetPackageName` / `targetIconLetter`
    / `targetSignatureChip` は既存 layout の `@+id/...` から生成済み
    (Round 2 までで既に XML 上には存在)、追加 `@+id` はゼロ。
  - 既存 `CredentialEditViewModelTest` 5 ケースは ViewModel API を
    一切変更していないため影響なし。

## 7. ファイル一覧 (合計)

```
新規 22:
  app/src/main/res/values-night/colors.xml
  app/src/main/res/values/dimens.xml
  app/src/main/res/values-en/strings.xml
  app/src/main/res/color/kn_text_field_stroke.xml
  app/src/main/res/drawable/bg_card_surface.xml
  app/src/main/res/drawable/bg_chip_success.xml
  app/src/main/res/drawable/bg_dataset_surface.xml
  app/src/main/res/drawable/bg_grab_handle.xml
  app/src/main/res/drawable/bg_icon_tile_primary.xml
  app/src/main/res/drawable/bg_mark_halo.xml
  app/src/main/res/drawable/bg_step_number.xml
  app/src/main/res/drawable/ic_kn_close.xml
  app/src/main/res/drawable/ic_kn_mark.xml
  app/src/main/res/drawable/ic_kn_plus.xml
  app/src/main/res/drawable/ic_kn_shield.xml
  app/src/main/res/drawable/ic_launcher_background.xml
  app/src/main/res/drawable/ic_launcher_foreground.xml
  app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
  app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
  app/src/main/res/layout/package_picker_row.xml

修正 11:
  app/src/main/AndroidManifest.xml
  app/src/main/res/values/colors.xml
  app/src/main/res/values/themes.xml
  app/src/main/res/values/strings.xml
  app/src/main/res/layout/credential_list_activity.xml
  app/src/main/res/layout/credential_list_item.xml
  app/src/main/res/layout/credential_edit_activity.xml
  app/src/main/res/layout/autofill_enable_activity.xml
  app/src/main/res/layout/package_picker_bottom_sheet.xml
  app/src/main/res/layout/dataset_presentation.xml
  app/src/main/java/com/example/keynest/ui/list/CredentialListActivity.kt
  app/src/main/java/com/example/keynest/ui/list/CredentialListAdapter.kt
  app/src/main/java/com/example/keynest/ui/edit/PackagePickerBottomSheet.kt
```

## 8. Reviewer 向けチェックリスト

- [ ] `./gradlew :app:assembleDebug` で resource compile 通過
- [ ] `./gradlew :app:testDebugUnitTest` で既存テスト 100% pass
- [ ] `./gradlew :app:lintDebug` （Material / Accessibility / Translation 警告
      の新規発生有無）
- [ ] Light / Dark 両モードで `CredentialListActivity` / `CredentialEditActivity` /
      `AutofillEnableActivity` / `PackagePickerBottomSheet` を起動し、
      コントラスト・タップ領域・empty state を目視確認
- [ ] Autofill ドロップダウンで `dataset_presentation` がデザイン通り
      表示されること（既存 e2e test で代用可能）
- [ ] `AndroidManifest.xml` の merged 出力に `INTERNET` permission が追加
      されていない (`InternetPermissionAbsenceTest` で機械確認済み)
- [ ] アダプティブアイコンが API 26+ デバイスで Shelter デザインで描画
      されること
