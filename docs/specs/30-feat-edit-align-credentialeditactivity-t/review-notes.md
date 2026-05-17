# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-15T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-30-impl-feat-edit-align-credentialeditactivity-t
- HEAD commit: 557581ab0d97a611c8ec9cf40d029a78fc41978b
- Compared to: develop..HEAD
- Feature Flag Protocol: 採否 = `opt-out` (CLAUDE.md `## Feature Flag Protocol` 節で `**採否**: opt-out`). flag 観点は適用せず通常の 3 カテゴリのみで判定。
- spec dir には `tasks.md` / `design.md` は存在しない (PM のみ / Architect 未起動の impl Issue)。`_Boundary:_` アノテーション無しのため boundary 違反は requirements.md の Out of Scope / 既存配線保持観点で確認。

## Verified Requirements

- 1.1 — `?android:attr/colorBackground` を layout root に bind。`CredentialEditLayoutTokensTest.editLayout_bindsRootBackgroundToColorBackgroundAttr` で pin。
- 1.2 — `@dimen/kn_screen_padding_h` を ScrollView の paddingHorizontal に適用。`editLayout_appliesKnScreenPaddingHForScrollRegion` で pin。
- 1.3 — 各セクション間 margin に `@dimen/kn_space_1/2/3/4/5/6` を使用 (layout 内に複数箇所)。`editLayout_doesNotHardcodeHexColors` が間接的に hex 禁止を担保。
- 1.4 — layout が hex を持たず `?attr/colorBackground` / `@color/kn_*` のみ参照。Phase 1 `Material3ThemeMigrationTest` が values-night 解決経路を pin。
- 2.1 — `app:titleTextAppearance="@style/Text.KeyNest.TitleS"`。`editLayout_appBarUsesTitleSAppearanceAndKnTextTintedCloseIcon` で pin。
- 2.2 — `app:navigationIcon="@drawable/ic_close_24"` + `app:navigationIconTint="@color/kn_text"`。同テストで pin。
- 2.3 / 2.4 — Activity `onCreate` の `title = getString(if (editingId == null) credential_edit_title_new else credential_edit_title_edit)` (既存挙動を保持)。
- 2.5 — `@+id/toolbar` 保持 + 既存 `setSupportActionBar(binding.toolbar)` + `setNavigationOnClickListener { finish() }` 挙動無変更。`editLayout_preservesAllPreIssue30Ids` で ID を pin。
- 3.1 — `style="@style/Widget.KeyNest.Card"`。`editLayout_targetAppCardUsesWidgetKeyNestCardAndKnIconTile` で pin。
- 3.2 — カード内 `paddingHorizontal/Vertical="@dimen/kn_card_padding"`。
- 3.3 — `@dimen/kn_icon_tile_lg` (44dp) アイコンタイル + `@drawable/kn_icon_tile_bg`。同テストで pin。
- 3.4 — `kn_icon_tile_bg` drawable 内で `kn_blue_500` 単色 (Issue #29 で導入済み)。
- 3.5 — `@+id/tv_target_app_label` + `style="@style/Text.KeyNest.Body"` + ellipsize=end + maxLines=1。`editLayout_targetAppCardHasLabelPackageAndSignatureChip` で pin。
- 3.6 — `@+id/tv_target_app_package` + `style="@style/Text.KeyNest.Mono"` + `textColor=kn_text_2` + ellipsize=end + maxLines=1。
- 3.7 — Activity `renderSignatureChip(hasSignature=true)` が `kn_signature_chip_bg_success` / `kn_success` / `@string/signature_match` に切替。
- 3.8 — layout default が `kn_signature_chip_bg_warning` / `kn_warning` / `@string/signature_missing`。
- 3.9 — `@+id/input_package` + `@+id/btn_pick_installed_app` 保持 (`editLayout_preservesAllPreIssue30Ids` + `editLayout_keepsHiddenEditablePackageField`)。Activity `binding.btnPickInstalledApp.setOnClickListener { PackagePickerBottomSheet.show(...) }` 無変更。
- 4.1 — 4 件の `TextInputLayout` がすべて `style="@style/Widget.KeyNest.TextField"` を参照。`editLayout_textInputLayoutsUseWidgetKeyNestTextField` で count==4 を pin。
- 4.2 — `Widget.KeyNest.TextField` style 内で `boxCornerRadius*` を `kn_r_input` で固定 (themes.xml 既存)。
- 4.3 — 各 TextInputEditText に `minHeight="@dimen/kn_input_height"`。`editLayout_inputFieldsApplyKnInputHeight` で count>=4 を pin。
- 4.4 — 各 EditText に `textAppearance="@style/Text.KeyNest.Body"` + `textColor="@color/kn_text"`。
- 4.5 — 各 TextInputLayout に `android:textColorHint="@color/kn_text_2"`。
- 4.6 — layout は `@string/label_display_name` / `@string/label_username` をそのまま参照 (XML hint 属性)。
- 4.7 — `@+id/layout_label` / `@+id/input_label` / `@+id/layout_username` / `@+id/input_username` 全件保持。
- 5.1 — password TextInputLayout も `Widget.KeyNest.TextField` 適用 (count==4 に含まれる)。
- 5.2 — `android:textAppearance="@style/Text.KeyNest.Password"`。`editLayout_passwordFieldUsesPasswordTextAppearance` で pin。
- 5.3 — password TextInputLayout も `textColorHint=kn_text_2` (Req 4.5 と同機構)。
- 5.4 — `app:passwordToggleEnabled="true"` + `app:endIconTint="@color/kn_text_2"`。`editLayout_passwordFieldHasToggleAndKnText2Tint` で pin。
- 5.5 — `@+id/input_password` + `android:inputType="textPassword"` 保持 (同テスト + ID pin)。Activity `takePasswordCharArray()` 無変更。
- 6.1 — `com.example.keynest.ui.widget.StrengthBar` を password header row に embed。`editLayout_embedsStrengthBarCustomView` で pin。
- 6.2-6.5 — Issue #29 `StrengthBarTest` 4 件 (`alwaysHasThreeSegments` / `eachSegmentMatchesKnStrengthDimens` / `setStrengthStrong/Medium/Weak`) が widget の寸法・配色を pin 済み。本 Issue では widget を再利用。
- 6.6 — Activity `binding.strengthBarEdit.setStrength(null)` で widget の default 挙動 (GONE) を発火。impl-notes に「Issue #29 確認事項 #2 と同方針」として明記。AC は「widget のデフォルト値で描画する」「表示要素の存在と配色トークンのみを担保する」と幅を持たせており、layout に widget を pin した時点で要件を満たすと解釈。
- 7.1 / 7.2 — `@drawable/kn_advanced_section_bg` (内部に `kn_surface_2` + `kn_r_input`)。`editLayout_advancedSectionUsesKnSurface2BackgroundAndKnRInputRadius` で pin。
- 7.3 — 詳細設定パネルの `paddingHorizontal/Vertical="@dimen/kn_card_padding"`。
- 7.4 — `@+id/advanced_title` に `style="Text.KeyNest.Body"` + `textColor="@color/kn_text_2"`。`editLayout_advancedHeaderUsesBodyAppearanceAndKnText2` で pin。
- 7.5 — `@+id/advanced_chevron` に `app:tint="@color/kn_text_3"`。`editLayout_advancedChevronTintsKnText3` で pin。
- 7.6 — 各行ラベル TextView に `style="Text.KeyNest.Caption"` + `textColor="@color/kn_text_2"` 適用。
- 7.7 — `Text.KeyNest.Mono` の参照 count>=3 を `editLayout_advancedRowsUseMonoForSha256AndCredentialIdAndPackage` で pin。`@+id/value_signature_hex` / `@+id/value_credential_id` / `@+id/tv_target_app_package`。
- 7.8 — `@drawable/kn_advanced_row_divider` (内部 `kn_border`)。`editLayout_advancedRowDividerColor` で pin。
- 7.9 — `advanced_header` / `advanced_title` / `advanced_chevron` / `advanced_content` / `row_*` / `value_*` / `btn_copy_signature_hex` / `toggle_credential_id` 全件保持。
- 7.10 — `CredentialEditAdvancedStateTest` 8 件が 4-arg ViewModel factory に追従させても全件 pass (impl-notes)。chevron rotation / advanced toggle / id toggle / copy 挙動は無変更。
- 8.1 / 8.2 — Activity `binding.btnDelete.visibility = if (editingId == null) View.GONE else View.VISIBLE`。layout default が `android:visibility="gone"` で `editLayout_deleteCtaUsesWidgetKeyNestButtonDestructive` が pin。
- 8.3 — `android:layout_width="match_parent"` を同テストで pin。
- 8.4-8.7 — `Widget.KeyNest.Button.Destructive` style (新規定義) が `minHeight=kn_button_height` / `strokeColor=kn_border` / `strokeWidth=1dp` / `cornerRadius=kn_r_input` / `textColor=kn_danger` / `textAppearance=Text.KeyNest.LabelL`。
- 8.8 — `android:text="@string/action_delete_credential"`。同テストで pin。
- 8.9 — Activity `binding.btnDelete.setOnClickListener { showDeleteConfirmation() }` で `MaterialAlertDialogBuilder` を起動。
- 8.10 — `viewModel.delete(id)` → 成功で `_navigation.tryEmit(Unit)` → 既存 navigation collector で `finish()` 発火。`CredentialEditViewModelTest.delete_onSuccess_emitsNavigationEvent_andLeavesStateIdle` + `delete_onMissingId_treatsAsSuccess_perRepoIdempotency` で navigation event を直接 verify。
- 8.11 — `MaterialAlertDialogBuilder.setNegativeButton(...negative, null)` で null listener → Material 標準の dismiss 挙動。
- 8.12 — Activity `renderState(DeleteFailed)` → `Snackbar.make(...credential_edit_delete_failed...).show()` (screen stays open)。`CredentialEditViewModelTest.delete_onStorageFailure_setsDeleteFailedState_andDoesNotEmitNavigation` で `State.DeleteFailed` 発行 + navigation 不発火を verify。
- 9.1 / 9.3 — `Widget.KeyNest.Button.Primary` を `@+id/btn_save` に適用 (画面下部 Primary CTA を選択)。`editLayout_saveCtaUsesWidgetKeyNestButtonPrimary` で pin。
- 9.2 — Req 9.1 で「アプリバー右側または画面下部」と二択。下部配置を採用したため当該 AC は条件節 (`While 保存ボタンがアプリバー内に配置される場合`) が発火せず空 vacuously true。
- 9.4 — `android:text="@string/action_save"`。同テストで pin。
- 9.5 — `@+id/btn_save` + Activity `binding.btnSave.setOnClickListener { onSaveClicked() }` 無変更 (`editLayout_preservesAllPreIssue30Ids`)。
- 10.1 — 27 件の既存 ID をすべて保持 (`editLayout_preservesAllPreIssue30Ids` で全件列挙)。
- 10.2 — `CredentialEditViewModelTest.save_*` 4 件 (既存) が 4-arg factory 化に追従して全件 pass (impl-notes)。
- 10.3 — `binding.btnPickInstalledApp.setOnClickListener { PackagePickerBottomSheet.show(...) }` 無変更。callback で `binding.inputPackage.setText(picked)` + `binding.tvTargetAppPackage.text = picked` (新規 mirror) を実行。
- 10.4 — `CredentialEditAdvancedStateTest.toggleAdvancedExpanded_flipsBetweenCollapsedAndExpanded` (既存、4-arg 化対応で全件 pass)。
- 10.5 — `binding.btnCopySignatureHex.setOnClickListener { copySignatureHexToClipboard() }` 無変更。
- 10.6 — `CredentialEditAdvancedStateTest.toggleCredentialIdVisible_flipsBetweenHiddenAndVisible` (既存)。
- 10.7 — `takePasswordCharArray()` 本体無変更。既存 `CredentialEditViewModelTest` の save 系 4 件で indirect pass。
- 10.8 — Activity `SafeLogger.info("signature hex copied (preview=${SafeLogger.previewHex(hex)})")` + ViewModel `delete()` 内 `SafeLogger.info("credential delete ok")` / `SafeLogger.error(...)` (preview 8 文字制約は plaintext を扱わないので維持)。既存 `SafeLoggerAuditTest` pass。
- 10.9 — error_*  / message_credential_saved の getString 解決経路無変更。`CredentialEditViewModelTest.save_mapsPackageNameBlank_toFieldError` 等 4 件で indirect pass。
- 11.1 / 11.2 — layout は `?attr/colorBackground` / `@color/kn_*` のみ参照。`editLayout_doesNotHardcodeHexColors` で hex 禁止を pin。Phase 1 `Material3ThemeMigrationTest` がテーマブリッジを pin。
- 11.3 / 11.4 — Phase 1 / Issue #29 と同じトークンセット (`kn_text` on `kn_surface` / `kn_surface_2` / `kn_bg`) を使用。
- NFR 1.1 — `./gradlew :app:assembleDebug` BUILD SUCCESSFUL (impl-notes)。
- NFR 1.2 — `./gradlew :app:testDebugUnitTest` 334/334 - 5 = 329 件 pass。失敗 5 件は Phase 1 / Issue #29 既知の pre-existing failure (`PackageSignatureResolverTest` 4 + `LockedFillResponseSecurityTest` 1) で一致。本 Issue 由来の新規失敗は無い。
- NFR 1.3 — `Material3ThemeMigrationTest` (8) / `FontTypefaceWiringTest` (9) / `CredentialEditLayoutAuditTest` (7) すべて pass。commit `10680cd` で `value_signature_hex` / `value_credential_id` に explicit `android:fontFamily="@font/jetbrains_mono"` を残し Issue #13 pin との両立を担保。
- NFR 2.1 — Toolbar 閉じる × は MaterialToolbar 標準 48dp+。`btn_delete` / `btn_save` は `kn_button_height=52dp`。`advanced_header` は `minHeight=48dp` (既存)。`btn_copy_signature_hex` / `toggle_credential_id` は既存 48dp 保持。
- NFR 2.2 — `app:navigationContentDescription="@string/credential_edit_close_a11y"` / `android:contentDescription="@string/credential_edit_advanced_chevron_a11y"` / `_target_app_change_a11y` / 既存 `_signature_a11y` / `_show_id_a11y` 全件設定。
- NFR 2.3 — Req 11.3 / 11.4 と同根拠。
- NFR 2.4 — `android:text="@string/action_delete_credential"` (可視テキスト) で SR に意図伝達。
- NFR 3.1 — 9 件の新規キーを values/strings.xml + values-ja/strings.xml の両方に同タイミング追加 (diff 検証済み)。
- NFR 3.2 — values-ja に 9 件全件追加済み。

## Findings

なし

## Summary

Issue #30 の全 11 機能要件 (1.1〜11.4) + NFR 1-3 のすべての numeric AC について、`credential_edit_activity.xml` への design-token 貼り込み・`CredentialEditActivity` の削除動線追加・`CredentialEditViewModel` の `delete()` パイプライン追加・`Widget.KeyNest.Button.Destructive` style 新規定義・en/ja 9 件の string 追加で実装が網羅されている。AC 対応テストは `CredentialEditLayoutTokensTest` 16 件 (新規) + `CredentialEditViewModelTest` delete 系 3 件 (新規) + Issue #14 系 `CredentialEditAdvancedStateTest` 8 件 (4-arg factory 化に追従) + Phase 1 構造 pin テスト 24 件 (全件 pass) で担保。既存 27 件の View ID と SafeLogger / CharArray パスワード取扱 / 詳細設定折りたたみ等の既存挙動はすべて温存。`./gradlew :app:assembleDebug` は SUCCESSFUL、`testDebugUnitTest` 334 件中失敗 5 件は Phase 1 / Issue #29 既知の pre-existing failure と完全一致し本 Issue 由来の新規失敗は無し。`tasks.md` / `design.md` は未生成 (PM のみ / Architect 未起動) のため boundary 制約は requirements.md の Out of Scope と既存配線保持観点で確認し、いずれにも違反なし。

RESULT: approve
