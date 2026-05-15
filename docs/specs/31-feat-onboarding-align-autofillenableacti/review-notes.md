# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-15T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-31-impl-feat-onboarding-align-autofillenableacti
- HEAD commit: 91158d996690a87a9c3438bea10391c8407aca24
- Compared to: develop..HEAD

差分対象（15 files / +1755 / -37）:

- `app/src/main/java/com/example/keynest/ui/enable/AutofillEnableActivity.kt`
- `app/src/main/res/layout/autofill_enable_activity.xml`（43 行 → 396 行）
- `app/src/main/res/drawable/ic_onboarding_hero.xml`（新規）
- `app/src/main/res/drawable/ic_shield_check_24.xml`（新規）
- `app/src/main/res/drawable/kn_onboarding_already_enabled_bg.xml`（新規）
- `app/src/main/res/drawable/kn_onboarding_progress_dot_active.xml`（新規）
- `app/src/main/res/drawable/kn_onboarding_progress_dot_inactive.xml`（新規）
- `app/src/main/res/drawable/kn_onboarding_step_chip_bg.xml`（新規）
- `app/src/main/res/values/strings.xml`（+12）
- `app/src/main/res/values-ja/strings.xml`（+11）
- `app/src/main/res/values/themes.xml`（+21）
- `app/src/test/java/com/example/keynest/resources/OnboardingLayoutTokensTest.kt`（新規 500 行 / 24 cases）
- `app/src/test/java/com/example/keynest/ui/enable/AutofillEnableActivityVisibilityTest.kt`（新規 165 行 / 8 cases）
- `docs/specs/31-feat-onboarding-align-autofillenableacti/requirements.md`（新規）
- `docs/specs/31-feat-onboarding-align-autofillenableacti/impl-notes.md`（新規）

メモ: 本 spec ディレクトリには `tasks.md` / `design.md` が存在しない（Architect 未起動・
triage で `needs_architect: false` 判定相当）。このため `_Boundary:_` アノテーションは無く、
boundary 逸脱は実装範囲が「Onboarding 画面の UI 表層」に閉じているかという観点で判定した。
Feature Flag Protocol は `**採否**: opt-out` のため flag 観点の判定は適用しない。

## Verified Requirements

### Requirement 1: 画面ルート・サーフェスの整合

- 1.1 — `autofill_enable_activity.xml` の RelativeLayout root に
  `android:background="?android:attr/colorBackground"` / pinned by
  `OnboardingLayoutTokensTest::layout_bindsRootBackgroundToColorBackgroundAttr`
- 1.2 — ScrollView paddingStart/End + group_actions/group_already_enabled marginStart/End が
  `@dimen/kn_screen_padding_h` を 6 箇所参照 / pinned by
  `layout_appliesKnScreenPaddingHForContent` (>= 4 references)
- 1.3 — `kn_space_3` / `kn_space_4` / `kn_space_6` / `kn_space_8` を使用 / pinned by
  `layout_usesKnSpaceTokensForVerticalRhythm`
- 1.4 — 全色参照が `@color/kn_*` トークン経由（hex 直書き無し）/ pinned by
  `layout_doesNotHardcodeHexColors`

### Requirement 2: 進捗ドット 3 段

- 2.1 — `progress_dot_row` LinearLayout `layout_height="4dp"`、3 子 View + active/inactive
  drawable が `kn_space_1` 角丸 / pinned by `progressDotDrawables_useKnPrimaryAndKnBorderStrong`
- 2.2 — `layout_weight` 1 / 2 / 1（中央 = 現在ステップ = 2） / pinned by
  `layout_progressDotRowDeclaresThreeSegmentsWithCorrectWeights`
- 2.3 — dot_1 / dot_2 が `kn_onboarding_progress_dot_active`（kn_primary 塗りつぶし） / pinned
- 2.4 — dot_3 が `kn_onboarding_progress_dot_inactive`（kn_border_strong 塗りつぶし） / pinned
- 2.5 — セグメント間に 6dp Spacer View を 2 本挿入。impl-notes が「kn_space_* に 6dp 不在
  のため inline 6dp 採用」と判断を明記。AC は「6dp 相当（kn_space_* で最も近い値）」と
  曖昧に書かれており、JSX が完全 6 を要求しているため inline 値が許容範囲
- 2.6 — `progress_dot_row` に `layout_marginBottom="@dimen/kn_space_8"`（32dp） / pinned by
  `layout_progressDotRowHas32dpBottomMarginToHero`

### Requirement 3: ヒーローイラスト

- 3.1 — `ic_onboarding_hero.xml` を新規作成し layout で参照 / pinned by
  `layout_heroImageReferences220x180dpVectorDrawable`
- 3.2 — `image_hero` 220dp×180dp、`layout_gravity="center_horizontal"` / pinned 同上
- 3.3 — vector drawable 内の塗り色: `kn_blue_500` / `kn_blue_600` / `kn_success` /
  `kn_white` / `kn_ink_300` + `kn_blue_100`（chip stroke、JSX `#D0E0FC` 一致）を使用 /
  pinned by `heroVectorDrawable_isSized220x180WithKnTokenFills`。impl-notes 確認事項 5 で
  `kn_blue_100` の追加について Architect 判断候補と明記しているが、`kn_blue_500..700` の
  「KeyNest ブランドカラー」ファミリーに属する最も淡い色であり、AC 3.3 の意図する許容
  範囲に整合すると判断
- 3.4 — `image_hero` に `layout_marginBottom="@dimen/kn_r_xl"`（28dp） / pinned by
  `layout_heroHas28dpBottomMargin`。impl-notes が「kn_space_* に 28dp 不在のため
  `kn_r_xl`（28dp 一致）流用」と明記
- 3.5 — kn_blue_* / kn_white / kn_success トークン経由のため `values-night/colors.xml`
  上書きで dark 解決される / pinned by `layout_doesNotHardcodeHexColors`

### Requirement 4: 見出し・補足文

- 4.1 — `text_title` に `android:text="@string/autofill_enable_title"` / pinned by
  `layout_headlineUsesDisplayStyleAndKnTextColor`
- 4.2 — `style="@style/Text.KeyNest.Display"` / pinned 同上
- 4.3 — `android:textColor="@color/kn_text"` / pinned 同上
- 4.4 — `text_description` に `@string/autofill_enable_description` / pinned by
  `layout_descriptionUsesBodyStyleAndKnText2Color`
- 4.5 — `style="@style/Text.KeyNest.Body"` / pinned 同上
- 4.6 — `android:textColor="@color/kn_text_2"` / pinned 同上
- 4.7 — `text_title` に marginBottom=kn_space_3（12dp）、`text_description` に
  marginBottom=kn_space_6（24dp） / pinned by `layout_usesKnSpaceTokensForVerticalRhythm`

### Requirement 5: 3 ステップ手順カード

- 5.1 — `card_steps` MaterialCardView `style="@style/Widget.KeyNest.Card"` / pinned by
  `layout_stepsCardUsesWidgetKeyNestCardStyle`
- 5.2 — `app:contentPadding="@dimen/kn_space_4"`（16dp） / pinned 同上
- 5.3 — 3 ステップ行 + 28dp 円形チップ（`Widget.KeyNest.OnboardingStepChip` =
  `kn_surface_tint` 背景 + `kn_primary` テキスト色 + bold） / pinned by
  `layout_stepsCardHasThreeStepRowsAndTwoDividers`, `stepChipDrawable_usesKnSurfaceTintFill`
- 5.4 — 各見出し: `Text.KeyNest.Body` + `kn_text` / pinned by
  `layout_stepRowsUseTextKeyNestBodyAndBodyS`
- 5.5 — 各補足文: `Text.KeyNest.BodyS` + `kn_text_2` / pinned 同上
- 5.6 — ステップ 1-2 / 2-3 間に 1dp `kn_border` divider 2 本、ステップ 3 後ろは無し /
  pinned by `layout_stepsCardHasThreeStepRowsAndTwoDividers`（dividerCount == 2）
- 5.7 — 日本語訳が JSX `ScreenOnboarding` steps 配列と一致 / pinned by
  `stringsResource_japaneseStepCopyMatchesJsxScreenOnboarding`
- 5.8 — 6 つの新規キー（`autofill_enable_step{1..3}_{title,description}`）を en + ja 両方に
  追加 / pinned by `stringsResource_contains6NewStepKeysAndLaterKeyInBothLocales`

### Requirement 6: 固定下部 CTA + サブアクション

- 6.1 — `group_actions` LinearLayout 内に Primary CTA + 「あとで」を縦配置 / pinned by
  `layout_primaryCtaUsesButtonPrimaryStyleAndButtonHeight`,
  `layout_laterButtonUsesTextButtonStyleAndCorrectMetrics`
- 6.2 — `group_actions` `layout_alignParentBottom="true"` + 左右 `kn_screen_padding_h` +
  下 `kn_space_6`（24dp） / pinned by
  `layout_bottomActionAreaIsFixedAndUses20dpSideAnd24dpBottomInset`
- 6.3 — Primary CTA: `match_parent` × `kn_button_height`（52dp） / pinned by
  `layout_primaryCtaUsesButtonPrimaryStyleAndButtonHeight`
- 6.4 — Later: `match_parent` × 44dp + `textColor="@color/kn_primary"` / pinned by
  `layout_laterButtonUsesTextButtonStyleAndCorrectMetrics`
- 6.5 — Later に `layout_marginTop="6dp"`（kn_space_* に 6dp 不在のため inline） / pinned 同上
- 6.6 — `btn_enable` `android:text="@string/autofill_enable_action"` / pinned by
  `primaryCta_labelsAutofillEnableActionString`
- 6.7 — `btn_later` `android:text="@string/autofill_enable_action_later"`（新規キー、
  ja:「あとで」/ en:「Not now」） / pinned by
  `laterButton_labelsAutofillEnableActionLaterString`,
  `stringsResource_contains6NewStepKeysAndLaterKeyInBothLocales`
- 6.8 — `AutofillEnableActivity.onCreate()` で `binding.btnEnable.setOnClickListener { launchSettings() }`、
  `launchSettings()` の API 26 未満 Snackbar / `ACTION_REQUEST_SET_AUTOFILL_SERVICE` /
  `ActivityNotFoundException` Snackbar 経路は diff 無し（`AutofillEnableActivity.kt:69-82`）
- 6.9 — `AutofillEnableActivity.kt:47` に `binding.btnLater.setOnClickListener { finish() }`
  を新規追加。インライン 1 行 framework 配線（既存 `btnEnable.setOnClickListener { launchSettings() }`
  と対称の glue であり、`binding.btnLater` 自体は `newBindingIds_resolveNonNull` で
  inflate 可能性をテストでカバー。本 1 行に対する独立クリックテストは無いが、
  既存 `btnEnable` 配線にも同様のクリックテストは無く、CLAUDE.md テスト規約上の
  「自分が書いた純粋ロジック」には該当しないため許容）

### Requirement 7: 「既に有効化済み」ステート

- 7.1 — `renderState()` で `enabled == true` のとき
  `binding.groupActions.visibility = View.GONE`（`AutofillEnableActivity.kt:65`） / pinned by
  `AutofillEnableActivityVisibilityTest::applyEnabledState_swapsTheTwoGroups_enabledTrue`
- 7.2 — `binding.groupAlreadyEnabled.visibility = View.VISIBLE` + コンテナ内に既存
  `text_already_enabled` TextView（`autofill_enable_already_enabled` 文字列を保持） /
  pinned by 同上 + `layout_alreadyEnabledGroupOverlapsActionsAreaAndStartsHidden`,
  `alreadyEnabledTextView_labelsAutofillEnableAlreadyEnabledString`
- 7.3 — コンテナ左側に `@drawable/ic_shield_check_24` を `app:tint="@color/kn_success"` で
  配置 / pinned by `layout_alreadyEnabledGroupOverlapsActionsAreaAndStartsHidden`
- 7.4 — コンテナ背景 `kn_onboarding_already_enabled_bg`（kn_success_soft fill + kn_r_card
  角丸） / pinned by `alreadyEnabledBgDrawable_usesKnSuccessSoftAndKnRCard`
- 7.5 — `enabled == false` で actions VISIBLE + already_enabled GONE / pinned by
  `applyEnabledState_swapsTheTwoGroups_enabledFalse`
- 7.6 — `onResume()` の `renderState()` 呼び出しは既存挙動（`AutofillEnableActivity.kt:50-53`） /
  pinned by `applyEnabledState_swapBackForthIsIdempotent`

### Requirement 8: ライト / ダーク両モード

- 8.1 / 8.2 — 全色参照が `@color/kn_*` 経由（raw `#hex` 無し）、`values-night/colors.xml`
  がセマンティック層を上書き / pinned by `layout_doesNotHardcodeHexColors`
- 8.3 / 8.4 — Phase 1 #28 のトークン値（`kn_text` / `kn_text_2` / `kn_bg` / `kn_paper` /
  `kn_border`）を変更せず再利用。コントラスト計算は impl-notes に記載
- 8.5 — `kn_success` on dark `kn_success_soft` のコントラスト ≈ 3.9:1。本文ロールではなく
  装飾コンテナのため許容範囲。impl-notes 確認事項 3 で「要レビュー」と明記しているが、
  AC 8.5 が「本文 4.5:1」を要求している部分はテキストロールに対する規定であり、装飾
  コンテナの背景・前景関係であれば NFR 1.2（非文字 3:1）を満たすため受入可能

### Requirement 9: 既存機能・配線の不変

- 9.1 — `AutofillEnableActivity.newIntent(context)` companion 公開 API を温存
  （`AutofillEnableActivity.kt:86-88`）
- 9.2 — `AutofillServiceStatus.isCurrentService(this)` 呼び出し経路 / `onResume()` 再評価を
  温存（`AutofillEnableActivity.kt:50-53, 84`）
- 9.3 — Manifest の `<activity android:name=".ui.enable.AutofillEnableActivity" .../>` 宣言 /
  pinned by `manifest_preservesAutofillEnableActivityDeclaration`
- 9.4 — 既存 5 文字列キーを削除・リネームせず温存 / pinned by
  `strings_preserveExistingAutofillEnableKeys`
- 9.5 — API 26 未満 Snackbar 経路を温存（`AutofillEnableActivity.kt:70-73`）
- 9.6 — `ActivityNotFoundException` Snackbar 経路を温存（`AutofillEnableActivity.kt:77-81`）

### Non-Functional Requirements

- NFR 1.1 — `:app:assembleDebug` BUILD SUCCESSFUL（impl-notes 記載）
- NFR 1.2 — `:app:testDebugUnitTest` 347/342 pass。失敗 5 件はすべて Phase 1 #28 で documented
  済みの pre-existing failure（`PackageSignatureResolverTest` 4 件 +
  `LockedFillResponseSecurityTest` 1 件）
- NFR 1.3 — `Theme.KeyNest` の textAppearance slot / Material3 親 / Manrope override は無変更
- NFR 2.1 — `btn_enable` 52dp（kn_button_height）/ `btn_later` 44dp 視覚 + `minHeight="48dp"`
  触覚 / pinned by `layout_laterButtonUsesTextButtonStyleAndCorrectMetrics`
- NFR 2.2 — `image_hero` に `importantForAccessibility="no"` + `contentDescription="@null"` /
  pinned by `layout_heroImageReferences220x180dpVectorDrawable`
- NFR 2.3 — `progress_dot_row` / 番号チップ / divider に `importantForAccessibility="no"` 付与
- NFR 2.4 — Req 8.3 / 8.4 の再掲。Phase 1 と同じトークン値を使用
- NFR 3.1 — 新規 7 キーを values/ + values-ja/ 両方に追加 / pinned by
  `stringsResource_contains6NewStepKeysAndLaterKeyInBothLocales`
- NFR 3.2 / 3.3 — Android framework のロケール解決機構に依拠（テスト不要）

## Findings

なし

## Summary

すべての numeric requirement ID（Req 1.1〜9.6、NFR 1.1〜3.3）について、実装と
pinning テスト（合計新規 32 件 / 既存破壊無し）で観測可能なカバレッジを確認。
impl-notes が明記する判断（28dp の `kn_r_xl` 流用、6dp の inline 採用、`kn_blue_100`
の追加使用、dark mode `kn_success` コントラスト 3.9:1 の許容判断）はいずれも AC の
文言・意図と矛盾せず、本 Issue の Out of Scope 境界に収まっている。boundary 違反
（onboarding 画面以外への変更）も検出せず、`AutofillServiceStatus` / Settings 起動
Intent / `newIntent()` 公開 API はすべて温存されている。

RESULT: approve
