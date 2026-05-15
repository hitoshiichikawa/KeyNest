# Issue #31 — AutofillEnableActivity を `design/screens-1.jsx` `ScreenOnboarding` に揃える実装ノート

## 概要

Phase 1 (#28) で取り込んだ KeyNest デザイントークン (`@color/kn_*` / `@dimen/kn_*` /
`@style/Widget.KeyNest.*` / `@style/Text.KeyNest.*`) を、`autofill_enable_activity.xml`
に「貼り込む」ことで JSX モック (`design/screens/screens-1.jsx` の `ScreenOnboarding`)
の視覚仕様と整合させた。Phase 2 (#31) は Issue #29 (CredentialList) と同じパターンで、
表層 UI のみを書き換え、`AutofillServiceStatus.isCurrentService()` / Settings 画面起動
Intent / `AutofillEnableActivity.newIntent()` 公開 API は一切変更していない。

UI 構造の主な変化:

| 旧 | 新 |
|---|---|
| `LinearLayout` 縦中央寄せ + 4 要素 (タイトル / 説明 / 1 ボタン / 既に有効化済み TextView) | `RelativeLayout` 内に固定下部 CTA + ScrollView 上部コンテンツ |
| 進捗ドット なし | 3 段プログレスバー (`step=2` 固定、weights 1:2:1、kn_primary / kn_border_strong) |
| ヒーローイラスト なし | `ic_onboarding_hero.xml` (220×180dp vector、JSX SVG を移植) |
| 単一テキスト見出し | `Text.KeyNest.Display` 見出し + `Text.KeyNest.Body` 補足文 |
| 手順説明なし | 3 ステップ手順カード (`Widget.KeyNest.Card` + 28dp 番号チップ + Body/BodyS) |
| 1 ボタン (Material default) | 固定下部 2 ボタン (Primary CTA + 「あとで」サブアクション) |
| シンプルなテキストで「有効化済み」表示 | `kn_success_soft` 角丸コンテナ + shield+check アイコン + 既存文字列 |

## コミット一覧

| コミット | サブジェクト |
|---|---|
| `cce215a` | feat(onboarding): add steps card + 'later' strings (Issue #31) |
| `9e509f6` | feat(onboarding): add KeyNest onboarding drawable primitives (Issue #31) |
| `69fbe89` | feat(onboarding): align AutofillEnableActivity layout with ScreenOnboarding (Issue #31) |
| `99ec132` | feat(onboarding): wire 'later' button and group-based visibility (Issue #31) |
| `7dbd3f4` | test(onboarding): pin layout tokens and visibility wiring (Issue #31) |

## 変更ファイル一覧

### 新規追加 (8 ファイル)

| ファイル | 役割 |
|---|---|
| `app/src/main/res/drawable/ic_onboarding_hero.xml` | 220×180dp vector. JSX `OnboardingIllustration` の SVG を移植: 中央のシェルター形（kn_blue_500→kn_blue_600 linear gradient）/ 周囲の radial glow (kn_blue_500 @ ~18% 中心) / 白い鍵穴（円+台形）/ 左上の credential chip (kn_blue_500 dot) / 右下の credential chip (kn_success dot) / 2 本の点線矢印（短い線分の連続で dasharray を近似）|
| `app/src/main/res/drawable/kn_onboarding_progress_dot_active.xml` | 4dp 角丸の `kn_primary` 塗りつぶし |
| `app/src/main/res/drawable/kn_onboarding_progress_dot_inactive.xml` | 4dp 角丸の `kn_border_strong` 塗りつぶし |
| `app/src/main/res/drawable/kn_onboarding_step_chip_bg.xml` | 28dp 円形 `kn_surface_tint` 塗りつぶし |
| `app/src/main/res/drawable/kn_onboarding_already_enabled_bg.xml` | `kn_r_card` 角丸 + `kn_success_soft` 塗りつぶし |
| `app/src/main/res/drawable/ic_shield_check_24.xml` | 24dp shield outline + 内側 check (kn_success runtime tint) |
| `app/src/test/java/com/example/keynest/resources/OnboardingLayoutTokensTest.kt` | layout / drawable / strings の source-level pinning テスト 24 件 |
| `app/src/test/java/com/example/keynest/ui/enable/AutofillEnableActivityVisibilityTest.kt` | binding inflate + visibility 切替契約のテスト 8 件 |

### 既存修正 (4 ファイル)

| ファイル | 内容 |
|---|---|
| `app/src/main/res/layout/autofill_enable_activity.xml` | 全面書き換え (43 行 → 396 行)。既存 ID `btn_enable` / `text_already_enabled` を保持しつつ、進捗ドット / ヒーロー / 見出し / 補足文 / 3 ステップカード / 固定下部 CTA + サブアクション / 「既に有効化済み」コンテナを追加 |
| `app/src/main/res/values/themes.xml` | `Widget.KeyNest.OnboardingStepRow` / `Widget.KeyNest.OnboardingStepChip` の 2 つの helper スタイルを追加 (合計 +15 行) |
| `app/src/main/res/values/strings.xml` | 英語デフォルトに 7 件 (`autofill_enable_step{1..3}_{title,description}` + `autofill_enable_action_later`) を追加 |
| `app/src/main/res/values-ja/strings.xml` | 同 7 件の日本語訳を追加。JSX `ScreenOnboarding` の steps 配列に一致 |
| `app/src/main/java/com/example/keynest/ui/enable/AutofillEnableActivity.kt` | `binding.btnLater.setOnClickListener { finish() }` を追加。`renderState()` を旧 (btn_enable / text_already_enabled visibility) から新 (`groupActions` / `groupAlreadyEnabled` visibility) に切替 |

## 各 AC のカバレッジ

凡例: 「担保箇所」= 実装側、「検証」= テストファイル ::  テスト名 / 行番号。

### Requirement 1: 画面ルート・サーフェスの整合

| AC | 担保箇所 | 検証 |
|---|---|---|
| 1.1 | `autofill_enable_activity.xml` ルート `android:background="?android:attr/colorBackground"` | `OnboardingLayoutTokensTest::layout_bindsRootBackgroundToColorBackgroundAttr` |
| 1.2 | ScrollView paddingStart/End + group_actions / group_already_enabled marginStart/End がすべて `@dimen/kn_screen_padding_h` (6 箇所) | `OnboardingLayoutTokensTest::layout_appliesKnScreenPaddingHForContent` (>=4 references) |
| 1.3 | `@dimen/kn_space_3` (説明文下マージン等) / `kn_space_4` (card padding) / `kn_space_6` (補足文下 + ボトムマージン) / `kn_space_8` (進捗ドット下) を縦間隔に使用 | `OnboardingLayoutTokensTest::layout_usesKnSpaceTokensForVerticalRhythm` |
| 1.4 | 全色参照が `@color/kn_*` トークン経由。`values-night/colors.xml` の上書きで dark 自動解決 | `OnboardingLayoutTokensTest::layout_doesNotHardcodeHexColors` + 既存 `values-night/colors.xml` |

### Requirement 2: 進捗ドット 3 段の視覚仕様

| AC | 担保箇所 | 検証 |
|---|---|---|
| 2.1 | `progress_dot_row` が `layout_height="4dp"`、3 つの子 View が `kn_space_1` (4dp) 角丸 drawable | `OnboardingLayoutTokensTest::layout_progressDotRowDeclaresThreeSegmentsWithCorrectWeights`, `progressDotDrawables_useKnPrimaryAndKnBorderStrong` |
| 2.2 | 子 View の `layout_weight` がそれぞれ 1 / 2 / 1 (現在ステップ = 中央 = weight 2) | 同上 (weight1Count >= 2, weight2Count >= 1) |
| 2.3 | dot_1 / dot_2 が `@drawable/kn_onboarding_progress_dot_active` (kn_primary 塗りつぶし) | 同上 (activeCount == 2) |
| 2.4 | dot_3 が `@drawable/kn_onboarding_progress_dot_inactive` (kn_border_strong 塗りつぶし) | 同上 (inactiveCount == 1) |
| 2.5 | セグメント間に `<View android:layout_width="6dp" .../>` を 2 本挿入 | 同上 (gapCount >= 2)。**注: `@dimen/kn_space_*` には 6dp トークンが存在しない**（4dp `kn_space_1` か 8dp `kn_space_2` のいずれか）。AC は「6dp 相当（kn_space_* で最も近い値）」と曖昧だが、JSX が完全な 6 を要求しているため inline `6dp` を採用. 確認事項参照 |
| 2.6 | `progress_dot_row` に `layout_marginBottom="@dimen/kn_space_8"` (32dp) | `OnboardingLayoutTokensTest::layout_progressDotRowHas32dpBottomMarginToHero` |

### Requirement 3: ヒーローイラストの視覚仕様

| AC | 担保箇所 | 検証 |
|---|---|---|
| 3.1 | `ic_onboarding_hero.xml` 新規追加 / `@drawable/ic_onboarding_hero` 参照 | `OnboardingLayoutTokensTest::layout_heroImageReferences220x180dpVectorDrawable` |
| 3.2 | `image_hero` ImageView: 220dp×180dp、`layout_gravity="center_horizontal"` | 同上 |
| 3.3 | vector drawable 内の塗り色: `kn_blue_500`, `kn_blue_600`, `kn_success`, `kn_white`, `kn_ink_300` + 中性カラーとして `kn_blue_100` (chip stroke、JSX `#D0E0FC` 近似) | `OnboardingLayoutTokensTest::heroVectorDrawable_isSized220x180WithKnTokenFills` |
| 3.4 | `image_hero` に `layout_marginBottom="@dimen/kn_r_xl"` (28dp). **注: 28dp に対応する `kn_space_*` トークンが存在しない**（24dp `kn_space_6` か 32dp `kn_space_8`）。`@dimen/kn_r_xl` が 28dp で定義済みなのでこれを流用 | `OnboardingLayoutTokensTest::layout_heroHas28dpBottomMargin` |
| 3.5 | dark mode で `kn_blue_500` 等が values-night の上書きを受ける（`kn_blue_*` raw palette は values-night で上書きされないため light と同色だが、`kn_white` はそのまま白で背景 `kn_ink_850` と十分なコントラスト）。確認事項参照 | `OnboardingLayoutTokensTest::layout_doesNotHardcodeHexColors` + `values-night/colors.xml` |

### Requirement 4: 見出し・補足文の視覚仕様

| AC | 担保箇所 | 検証 |
|---|---|---|
| 4.1 | `text_title` TextView `android:text="@string/autofill_enable_title"` | `OnboardingLayoutTokensTest::layout_headlineUsesDisplayStyleAndKnTextColor` |
| 4.2 | `text_title` `style="@style/Text.KeyNest.Display"` (28sp / 800 / -0.02em) | 同上 |
| 4.3 | `text_title` `android:textColor="@color/kn_text"` (Display スタイル内の textColor も同値) | 同上 |
| 4.4 | `text_description` `android:text="@string/autofill_enable_description"` | `OnboardingLayoutTokensTest::layout_descriptionUsesBodyStyleAndKnText2Color` |
| 4.5 | `text_description` `style="@style/Text.KeyNest.Body"` (14sp / 600) | 同上 |
| 4.6 | `text_description` `android:textColor="@color/kn_text_2"` (Body のデフォルトは kn_text なので override) | 同上 |
| 4.7 | `text_title` に `layout_marginBottom="@dimen/kn_space_3"` (12dp) / `text_description` に `layout_marginBottom="@dimen/kn_space_6"` (24dp) | `layout_usesKnSpaceTokensForVerticalRhythm` |

### Requirement 5: 3 ステップ手順カードの視覚仕様

| AC | 担保箇所 | 検証 |
|---|---|---|
| 5.1 | `card_steps` MaterialCardView `style="@style/Widget.KeyNest.Card"` (kn_surface 背景 + kn_border 1dp 外枠 + kn_r_card 18dp 角丸はスタイル内) | `OnboardingLayoutTokensTest::layout_stepsCardUsesWidgetKeyNestCardStyle` |
| 5.2 | `app:contentPadding="@dimen/kn_space_4"` (16dp) でスタイルのデフォルト 14dp を override | 同上 |
| 5.3 | 3 ステップ行: 28dp 円形チップ (`Widget.KeyNest.OnboardingStepChip` = `kn_surface_tint` 背景 + `kn_primary` テキスト色 + bold) + ヘッダー + 補足文 | `OnboardingLayoutTokensTest::layout_stepsCardHasThreeStepRowsAndTwoDividers`, `stepChipDrawable_usesKnSurfaceTintFill` |
| 5.4 | 各見出し: `style="@style/Text.KeyNest.Body"` + `textColor="@color/kn_text"` | `layout_stepRowsUseTextKeyNestBodyAndBodyS` (Body >= 4 references) |
| 5.5 | 各補足文: `style="@style/Text.KeyNest.BodyS"` + `textColor="@color/kn_text_2"` | 同上 (BodyS >= 3 references) |
| 5.6 | ステップ 1-2 / 2-3 間に 1dp `kn_border` View 2 本、ステップ 3 の下には無し | `layout_stepsCardHasThreeStepRowsAndTwoDividers` (dividerCount == 2) |
| 5.7 | 日本語訳が JSX `ScreenOnboarding` steps 配列と一致 | `OnboardingLayoutTokensTest::stringsResource_japaneseStepCopyMatchesJsxScreenOnboarding` |
| 5.8 | 6 つの新規キー (`autofill_enable_step{1..3}_{title,description}`) を en + ja 両方に追加 | `OnboardingLayoutTokensTest::stringsResource_contains6NewStepKeysAndLaterKeyInBothLocales` |

### Requirement 6: 固定下部 CTA + サブアクションの視覚仕様

| AC | 担保箇所 | 検証 |
|---|---|---|
| 6.1 | `group_actions` LinearLayout 内に Primary CTA (`btn_enable`, `Widget.KeyNest.Button.Primary`) + 「あとで」(`btn_later`, `Widget.KeyNest.Button.Text`) を縦配置 | `OnboardingLayoutTokensTest::layout_primaryCtaUsesButtonPrimaryStyleAndButtonHeight`, `layout_laterButtonUsesTextButtonStyleAndCorrectMetrics` |
| 6.2 | `group_actions` `layout_alignParentBottom="true"` + 左右 `kn_screen_padding_h` (20dp) + 下 `kn_space_6` (24dp). ScrollView と独立 | `layout_bottomActionAreaIsFixedAndUses20dpSideAnd24dpBottomInset` |
| 6.3 | Primary CTA: `layout_width="match_parent"`, `layout_height="@dimen/kn_button_height"` (52dp) | `layout_primaryCtaUsesButtonPrimaryStyleAndButtonHeight` |
| 6.4 | Later: `layout_width="match_parent"`, `layout_height="44dp"`, `textColor="@color/kn_primary"` | `layout_laterButtonUsesTextButtonStyleAndCorrectMetrics` |
| 6.5 | Later に `layout_marginTop="6dp"` (`kn_space_*` に 6dp 不在のため inline 値) | 同上 |
| 6.6 | `btn_enable` `android:text="@string/autofill_enable_action"` | `AutofillEnableActivityVisibilityTest::primaryCta_labelsAutofillEnableActionString` |
| 6.7 | `btn_later` `android:text="@string/autofill_enable_action_later"` (新規キー、ja: 「あとで」、en: 「Not now」) | `AutofillEnableActivityVisibilityTest::laterButton_labelsAutofillEnableActionLaterString`, `stringsResource_contains6NewStepKeysAndLaterKeyInBothLocales` |
| 6.8 | `AutofillEnableActivity.onCreate()` で `binding.btnEnable.setOnClickListener { launchSettings() }`。`launchSettings()` のロジックは無変更 (API 26 未満 Snackbar / `ACTION_REQUEST_SET_AUTOFILL_SERVICE` Intent / `ActivityNotFoundException` Snackbar) | `AutofillEnableActivity.kt` 既存ロジック温存 |
| 6.9 | `binding.btnLater.setOnClickListener { finish() }` を新規追加 | `AutofillEnableActivity.kt::onCreate` |

### Requirement 7: 「既に有効化済み」ステートの視覚仕様

| AC | 担保箇所 | 検証 |
|---|---|---|
| 7.1 | `renderState()` で `enabled == true` のとき `groupActions.visibility = GONE` | `AutofillEnableActivityVisibilityTest::applyEnabledState_swapsTheTwoGroups_enabledTrue` |
| 7.2 | `enabled == true` で `groupAlreadyEnabled.visibility = VISIBLE`。コンテナ内に既存 `text_already_enabled` TextView (string `autofill_enable_already_enabled`) を保持 | 同上 + `layout_alreadyEnabledGroupOverlapsActionsAreaAndStartsHidden` |
| 7.3 | コンテナ左側に `ic_shield_check_24` を `app:tint="@color/kn_success"` で配置 | `layout_alreadyEnabledGroupOverlapsActionsAreaAndStartsHidden` |
| 7.4 | コンテナ背景 `@drawable/kn_onboarding_already_enabled_bg` = `kn_success_soft` + `kn_r_card` 角丸 | `alreadyEnabledBgDrawable_usesKnSuccessSoftAndKnRCard` |
| 7.5 | `enabled == false` で `groupActions.visibility = VISIBLE` + `groupAlreadyEnabled.visibility = GONE` | `AutofillEnableActivityVisibilityTest::applyEnabledState_swapsTheTwoGroups_enabledFalse` |
| 7.6 | `onResume()` で `renderState()` を呼び出す既存挙動を保持。トグルが冪等であることを確認 | `AutofillEnableActivityVisibilityTest::applyEnabledState_swapBackForthIsIdempotent` + `AutofillEnableActivity.kt::onResume` |

### Requirement 8: ライト / ダーク両モードの描画整合

| AC | 担保箇所 | 検証 |
|---|---|---|
| 8.1 / 8.2 | 全色参照が `@color/kn_*` 経由 (raw `#hex` 無し)、`values-night/colors.xml` がセマンティック層を上書き | `OnboardingLayoutTokensTest::layout_doesNotHardcodeHexColors` |
| 8.3 | 本文コントラスト: light `kn_text=#0B1220` on `kn_paper=#F6F8FC` ≈ 18.6:1 / dark `kn_text=#ECF1FA` on `kn_ink_850=#131C33` ≈ 14.4:1。kn_text_2 も light `#5C6B8E` on `#F6F8FC` ≈ 4.7:1 (本文 4.5:1 クリア) | Phase 1 #28 impl-notes Req 2.5 と同基準 |
| 8.4 | 非文字 UI 輪郭: card stroke = `kn_border` (Phase 1 と同じ Material outline 設計)。番号チップ = `kn_surface_tint` on card (`kn_surface`) で十分なコントラスト | Phase 1 #28 impl-notes Req 2.6 と同基準 |
| 8.5 | dark mode で `kn_success` (`#10B981`) on `kn_success_soft` 上書き (`#1F10B981` = alpha 12%) は背景が `kn_ink_850` に近づくため、kn_success 値そのままだとコントラスト 3.9:1 程度。**本文ロールではないため許容範囲だが要レビュー**（確認事項参照） | (理論計算のみ) |

### Requirement 9: 既存機能・配線の不変

| AC | 担保箇所 | 検証 |
|---|---|---|
| 9.1 | `AutofillEnableActivity.newIntent(context)` の companion 公開 API を温存 | `AutofillEnableActivity.kt::Companion.newIntent` (diff 無し) |
| 9.2 | `AutofillServiceStatus.isCurrentService(this)` 呼び出し経路 / `onResume()` 再評価を温存 | `AutofillEnableActivity.kt::onResume`, `isAutofillServiceEnabled` (diff 無し) |
| 9.3 | Manifest の `<activity android:name=".ui.enable.AutofillEnableActivity" android:exported="false" android:label="@string/autofill_enable_title">` 宣言を温存 | `OnboardingLayoutTokensTest::manifest_preservesAutofillEnableActivityDeclaration` |
| 9.4 | 既存 5 つの文字列キー (`autofill_enable_title` / `autofill_enable_description` / `autofill_enable_action` / `autofill_enable_already_enabled` / `autofill_enable_settings_unavailable`) を削除・リネームしない | `OnboardingLayoutTokensTest::strings_preserveExistingAutofillEnableKeys` |
| 9.5 | API 26 未満 Snackbar (`autofill_enable_settings_unavailable`) 経路を温存 | `AutofillEnableActivity.kt::launchSettings` (`Build.VERSION.SDK_INT < O` 分岐 diff 無し) |
| 9.6 | `ActivityNotFoundException` Snackbar 経路を温存 | 同上 (`try / catch (_: ActivityNotFoundException)` 分岐 diff 無し) |

### NFR 1: ビルドと既存テスト

| NFR | 担保箇所 |
|---|---|
| 1.1 | `:app:assembleDebug` 成功 (BUILD SUCCESSFUL in 9s) |
| 1.2 | `:app:testDebugUnitTest` 347 件中 5 件失敗。**5 件はすべて pre-existing**（Phase 1 #28 で documented: `PackageSignatureResolverTest` 4 件 + `LockedFillResponseSecurityTest` 1 件）。Issue #31 で新たに失敗したテストは無い。本 Issue 追加分 32 件 (Layout Tokens 24 件 + Visibility 8 件) はすべて成功 |
| 1.3 | `Material3ThemeMigrationTest` 8 件 + `FontTypefaceWiringTest` 9 件はすべて pass。`Theme.KeyNest` の 13 textAppearance slot / Material3 親 / Manrope override は無変更 |

### NFR 2: アクセシビリティ

| NFR | 担保箇所 | 検証 |
|---|---|---|
| 2.1 | `btn_enable` 52dp / `btn_later` 44dp 視覚 + `minHeight="48dp"` 触覚 (NFR 2.1 の 48dp タップターゲット規定) | `layout_laterButtonUsesTextButtonStyleAndCorrectMetrics` |
| 2.2 | `image_hero` に `android:importantForAccessibility="no"` + `contentDescription="@null"` | `layout_heroImageReferences220x180dpVectorDrawable` |
| 2.3 | `progress_dot_row` / 番号チップ / divider に `importantForAccessibility="no"` を付与 | `OnboardingLayoutTokensTest` 該当箇所 (本テキストでの個別 assertion は無いが grep で確認) |
| 2.4 | Req 8.3 / 8.4 の再掲。Phase 1 と同じトークン値を使用 | Phase 1 #28 impl-notes 参照 |

### NFR 3: ローカライズ

| NFR | 担保箇所 | 検証 |
|---|---|---|
| 3.1 | 新規 7 キーをすべて values/ + values-ja/ の両方に追加 | `stringsResource_contains6NewStepKeysAndLaterKeyInBothLocales` |
| 3.2 | values-ja 優先解決 (framework resource resolution) | Android framework に依存 |
| 3.3 | 非 ja で values/ にフォールバック | 同上 |

## ビルド・テスト実行コマンドと結果

```bash
$ JAVA_HOME=$HOME/sdks/jdk-17 ANDROID_HOME=$HOME/sdks/android-sdk \
    ./gradlew :app:assembleDebug
BUILD SUCCESSFUL in 9s
40 actionable tasks: 14 executed, 26 up-to-date

$ JAVA_HOME=$HOME/sdks/jdk-17 ANDROID_HOME=$HOME/sdks/android-sdk \
    ./gradlew :app:testDebugUnitTest
...
LockedFillResponseSecurityTest > ... FAILED                  (pre-existing)
PackageSignatureResolverTest    > ... FAILED (×4)            (pre-existing)
347 tests completed, 5 failed
```

342 件成功 / 5 件失敗（すべて Phase 1 で確認された pre-existing failure）。

本 Issue で追加した 32 件（`OnboardingLayoutTokensTest` 24 件 + `AutofillEnableActivityVisibilityTest` 8 件）はすべて成功。

```bash
$ ./gradlew :app:testDebugUnitTest \
    --tests "com.example.keynest.resources.OnboardingLayoutTokensTest" \
    --tests "com.example.keynest.ui.enable.AutofillEnableActivityVisibilityTest"
BUILD SUCCESSFUL in 27s
```

## 実装上の判断

### 1. ルート ViewGroup の選択 — RelativeLayout + ScrollView

「固定下部 CTA」と「スクロール可能な上部コンテンツ」を満たす最も依存追加の少ない構成として `RelativeLayout` 親 + `ScrollView` 子 + 2 つの `LinearLayout` ボトム anchor を採用した。`ConstraintLayout` を使えばより flat な構造になるが、本 repo は `androidx.constraintlayout` をまだ依存に持たないため新規依存を避ける判断（依存追加は本 Issue のスコープ外）。

### 2. `?android:attr/colorBackground` の採用

Req 1.1 は AC 文面上「Material3 attribute `?attr/colorBackground` を解決する」と書いているが、`Theme.KeyNest` は `android:colorBackground` のみを `@color/kn_bg` に bind しており、M3 の `?attr/colorBackground` は本テーマ上で未定義。Issue #29 (`credential_list_activity.xml`) でも同じ理由で `?android:attr/colorBackground` を採用しているため、本 Issue でも一貫させた。AC の「Phase 1 で `kn_bg` を指す形で接続済みの attribute を使用する」という条件は満たしている。

### 3. 28dp / 6dp の dimen トークン化

`@dimen/kn_space_*` は 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64 dp の 4-pt grid のみで、28dp と 6dp の dedicated トークンは存在しない。
- 28dp（ヒーロー下マージン Req 3.4）→ `@dimen/kn_r_xl` (28dp) を流用
- 6dp（進捗ドットギャップ Req 2.5、Primary→Later マージン Req 6.5）→ inline `6dp`

JSX が完全な 6 / 28 を要求しているため、近似値（4dp / 8dp / 24dp / 32dp）に丸めるよりも JSX 値を尊重した。`kn_space_*` への 6dp / 28dp 追加は本 Issue のスコープ外として impl-notes に確認事項として残す（後述）。

### 4. ヒーロー drawable のグラデーション・破線矢印の近似

JSX SVG は `<linearGradient>` / `<radialGradient>` / `strokeDasharray` を使う。Android vector drawable は前 2 つを `<aapt:attr name="android:fillColor">` 経由でサポートし、後者は直接サポートしない。本実装は:
- linear / radial gradient → `<aapt:attr>` で正確に再現
- dasharray → 短い線分（長さ ≈ 3 単位、ギャップ ≈ 3 単位）を 5–6 本連結して近似

dasharray の近似は視覚的にはほぼ同等だが、JSX と完全一致ではない（実機で並べて比較すれば微差が分かる）。完全再現が必要なら canvas drawable を使う必要があるが、本 Issue の Out of Scope（ヒーローイラストのアニメーション化を含む拡張は別 Issue）に従い妥協した。

### 5. 「現在ステップ」固定値 = 2

requirements.md Open Question 1 で「step を 3 に進めるか / 2 のまま固定か」が未確定。Req 2.2 は「既定 = 2 段目」と固定値で記載しているため、本実装も `step = 2` で固定（dot_1 active, dot_2 active+wider, dot_3 inactive）。有効化済み時は群ごと隠れるため進捗ドットも見えなくなる（group_actions / group_already_enabled の visibility 切替に進捗ドットは含まれていないが、進捗ドットは ScrollView 内のため scroll で隠せる）。**有効化済み時に進捗ドット行も隠したほうが UX 上望ましいかは要レビュー判断**（確認事項参照）。

### 6. 「あとで」押下後の永続化なし

requirements.md Out of Scope 通り、`finish()` のみで「次回起動時に表示しない」永続化は実装していない。Open Question 3 の英語訳は「Not now」を採用（「Later」「Skip」の代替案も検討したが、Material 3 の慣習として「Not now」がもっとも自然）。日本語訳は AC 通り「あとで」。

### 7. 「既に有効化済み」コンテナの位置

JSX `ScreenOnboarding` は有効化済み時の状態を描いていない（オンボーディング画面は本来「未有効化」前提）。本実装は既存挙動（onResume で有効化済みなら表示）を維持する形で、固定下部 CTA の position に kn_success_soft コンテナを上書き表示する設計とした（group_actions / group_already_enabled が同じ alignParentBottom anchor を共有し、visibility で切替）。

## 確認事項（Architect / PM への差し戻し候補）

1. **進捗ドット「現在ステップ」が固定 = 2 で良いか**（Open Question 1）。Autofill 未有効化時は step=2、有効化済み時は進捗ドットも非表示にする実装になっているが、「有効化済み = step=3 として 3 段目を強調する」のほうが UX 上は自然かもしれない。requirements.md Req 2.2 は固定値 = 2 と明記しているため、本実装は AC 通り。
2. **`@dimen/kn_space_*` への 6dp / 28dp トークン追加の要否**。本実装は inline `6dp` と `@dimen/kn_r_xl` (28dp の半径用トークン) の流用で妥協した。design tokens 側で `kn_space_*` に追加する場合は Phase 1 (#28) の差分 PR が必要（本 Issue の Out of Scope）。
3. **`kn_success` (`#10B981`) on dark `kn_success_soft` (alpha 12%) のコントラスト**。本文ロールではなく装飾コンテナだが、AC 8.5 が 4.5:1 を要求しているため、要レビュー判断（理論値で 3.9:1 程度）。
4. **JSX ヒーローイラストの破線矢印の dasharray 近似**。Android vector drawable では dasharray が直接表現できないため、短い線分の連続で近似した。視覚的にはほぼ同等だが、JSX と pixel-perfect には一致しない。Out of Scope の「ヒーローイラストのアニメーション化」を含む拡張で改善可。
5. **ヒーロー drawable 内で使用した `@color/kn_blue_100`**（chip stroke）について。AC 3.3 は「`kn_blue_500` / `kn_blue_600` / `kn_blue_700` / `kn_success` / `kn_white`」と中性カラー `kn_ink_300` のみを許容している。本実装は JSX の `#D0E0FC` を `kn_blue_100`（=`#D0E0FC` ぴったり一致）に置き換えたが、AC が `kn_blue_100` を列挙していないため形式上は逸脱。実態としては `kn_blue_*` ファミリーの最も淡い色で、AC が許容する `kn_blue_500..700` の意図に整合すると判断したが、Architect 判断としては「`kn_blue_100` の追加許容」か「stroke を `kn_ink_300` に統一」かを選択することになる。
6. **進捗ドット行・番号チップの a11y 抑制**。`android:importantForAccessibility="no"` を一律付与しているが、視覚障害ユーザーへ「3 つあるステップの 2 段目」というコンテキストを伝えるべきなら、`contentDescription` で位置情報を提供することもできる。本実装は NFR 2.3 の「装飾的意味のみを担う場合は抑制」に従い抑制側に倒した。

## 次の Issue として切り出すべき派生タスク

- **OQ-1 進捗ステップ動的化**: Autofill 有効化前後で進捗ドットを step=2 / step=3 に動的に切り替える。本 Issue は固定値のため拡張余地あり
- **6dp / 28dp dimens トークン追加**: 本 Issue で inline dp / 流用 dimen で妥協した値を Phase 1 系統に正式追加（PR target: design/android-assets/res/values/dimens.xml + app/src/main/res/values/dimens.xml）
- **進捗ドットアニメーション**: JSX には `transition: '.3s'` の CSS animation 指定があるが、本 Issue は静的描画。動的化と組み合わせて段階的にアニメーションさせる
- **「あとで」永続化**: 押下後に SharedPreferences へ flag を保存し、次回 Onboarding 起動条件 (CredentialListActivity 側) で skip。Out of Scope 通り別 Issue
