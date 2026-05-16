# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-16T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-34-impl-feat-autofill-align-dataset-presentation
- HEAD commit: 3c439fc724be1cd2815b56392bb34cf599a04c83
- Compared to: develop..HEAD
- Feature Flag Protocol: opt-out（CLAUDE.md L233 `**採否**: opt-out`）→ 通常の 3 カテゴリ判定のみ実施

## Verified Requirements

### Requirement 1: Dataset presentation ルートの構造とサーフェス
- 1.1 — `dataset_presentation.xml` は LinearLayout / FrameLayout / TextView / ImageView のみ。`?attr/` 参照なし（`DatasetPresentationLayoutTokensTest.layout_usesRemoteViewsCompatibleViewTypesOnly` / `layout_doesNotReferenceAttrColorTokens` で textual pin）
- 1.2 — root LinearLayout `android:background="@color/kn_surface"`（`layout_rootContainerUsesKnSurfaceBackground`）
- 1.3 — layout 内 color/tint はすべて `@color/kn_*` 参照（`layout_doesNotHardcodeHexColorsInAttributes`）
- 1.4 — root vertical LinearLayout が header + dataset 行 + footer の 3 子要素を持つ（Req 5.1 で 3 段目の footer が必要なため上位互換）（`layout_isVerticalLinearLayoutAtRoot` / `layout_containsAtLeastThreeChildLinearLayouts`）
- 1.5 — `@+id/dataset_label` / `@+id/dataset_subtitle` を dataset 行内に保持（`layout_preservesDatasetLabelAndSubtitleViewIds`）

### Requirement 2: KEYNEST ヘッダー領域
- 2.1 — header LinearLayout が root の最初の子として 1 つ配置
- 2.2 — header bg = `@drawable/kn_dataset_header_bg`（中身は `@color/kn_surface_2` solid）（`layout_headerUsesKnDatasetHeaderBgFill` / `headerBgDrawable_usesKnSurface2Fill`）
- 2.3 — header padding 上下 `kn_space_2`(8dp) / 左右 `kn_space_3`(12dp)（layout inline 参照、AC 範囲内）
- 2.4 — KN mark `@drawable/ic_keynest_mark_24` を 16dp で配置（`layout_headerReferencesKnMarkAndTintsItPrimary`）
- 2.5 — KN mark `android:tint="@color/kn_primary"`（同上）
- 2.6 — eyebrow `@string/autofill_dataset_brand_eyebrow` を参照（`layout_headerEyebrowDeclaresInlineTextAppearanceTokens`）
- 2.7 — eyebrow inline `textSize=11sp` / `textStyle=bold` / `letterSpacing=0.08` / `textAllCaps=true`（同上）
- 2.8 — eyebrow `textColor="@color/kn_text"`（同上）
- 2.9 — header 末尾に signature chip LinearLayout 1 つを配置
- 2.10 — chip 背景 `@drawable/kn_signature_chip_bg_success`（kn_success_soft fill / kn_r_pill）（`layout_signatureChipUsesSuccessSoftPillAndShield`）
- 2.11 — chip テキスト + shield アイコン tint = `@color/kn_success`（同上）
- 2.12 — chip 内に `@drawable/ic_shield_fill_16` を配置（同上）
- 2.13 — chip text = `@string/signature_match`（既存キー流用）（同上 / `stringsResource_signatureMatchIsPreserved`）
- 2.14 — pill 角丸 + 11sp / bold（Text.KeyNest.Caption 以下の小ぶりサイズ）（同上）
- 2.15 — 常に「署名一致」表示固定（layout に条件分岐なし）

### Requirement 3: Dataset 行
- 3.1 — header 直下に dataset 行 LinearLayout 1 つ（`layout_containsAtLeastThreeChildLinearLayouts`）
- 3.2 — 行 padding 上下 `kn_space_2` / 左右 `kn_space_3`（AC 範囲内）
- 3.3 — 行 `android:minHeight="48dp"`（`layout_datasetRowDeclaresMinHeight48Dp`）
- 3.4 — 左端 FrameLayout size = `@dimen/kn_icon_tile_sm`（32dp）（`layout_datasetRowIconTileUsesKnIconTileSmAndTileBg`）
- 3.5 — tile 角丸は既存 `kn_icon_tile_bg.xml` の `@dimen/kn_r_sm` (12dp)（Issue #29 で担保）
- 3.6 — tile 背景 `@drawable/kn_icon_tile_bg`（同上）
- 3.7 — tile 中央に `@drawable/ic_key_24` を 20dp で配置（PM Open Question #3 の前者選択）（`layout_datasetRowIconTileCenterReferencesKeyVector`）
- 3.8 — tile 右に label/username 縦 2 段 LinearLayout
- 3.9 — `@+id/dataset_label` 保持（`layout_preservesDatasetLabelAndSubtitleViewIds`）
- 3.10 — label inline `textSize=13sp` / `textStyle=bold`（`layout_datasetLabelDeclaresInlineTextAppearanceTokens`）
- 3.11 — label `textColor="@color/kn_text"`（同上）
- 3.12 — label `maxLines=1` / `ellipsize=end`（同上）
- 3.13 — `@+id/dataset_subtitle` 保持（`layout_preservesDatasetLabelAndSubtitleViewIds`）
- 3.14 — subtitle inline `textSize=11sp`（AC は 11sp/12sp 相当を許容）（`layout_datasetSubtitleDeclaresInlineTextAppearanceTokens`）
- 3.15 — subtitle `textColor="@color/kn_text_2"`（同上）
- 3.16 — subtitle `maxLines=1` / `ellipsize=end`（同上）
- 3.17 — 行末尾に lock ImageView 1 つ（`layout_datasetRowTrailingLockIconReferencesLockOutline16`）
- 3.18 — lock `android:tint="@color/kn_text_2"`（同上）
- 3.19 — lock 16dp × 16dp（同上）
- 3.20 — lock = `@drawable/ic_lock_outline_16`（新規追加）（同上）

### Requirement 4: drawable 整備
- 4.1 — 既存 `ic_keynest_mark_24.xml` を流用（Open Question #2 の後者）
- 4.2 — 既存 `ic_plus_24.xml` を流用
- 4.3 — 既存 `ic_key_24.xml` を tile 中央に流用 / 末尾 lock は `ic_lock_outline_16.xml` 新規（Open Question #1 の前者）
- 4.4 — `kn_dataset_create_row_bg.xml`（light）+ `drawable-night/kn_dataset_create_row_bg.xml`（dark 上書き）新規追加
- 4.5 — 新規 drawable は `@color/kn_*` / `@android:color/white` のみ参照（`lockIconDrawable_doesNotHardcodeHexColors` / `createRowBgLight_doesNotHardcodeHexColors` / `createRowBgNight_doesNotHardcodeHexColors` / `headerBg_doesNotHardcodeHexColors`）

### Requirement 5: 末尾「KeyNest で新規作成」行
- 5.1 — dataset 行下に footer LinearLayout 1 つ（`layout_containsAtLeastThreeChildLinearLayouts`）
- 5.2 — `android:background="@drawable/kn_dataset_create_row_bg"`（light = `@color/kn_blue_50`）（`layout_footerRowUsesKnDatasetCreateRowBgDrawable` / `createRowBgLight_referencesKnBlue50Fill`）
- 5.3 — footer padding 上下 `kn_space_2` / 左右 `kn_space_3`
- 5.4 — 左端 plus icon `@drawable/ic_plus_24` を 16dp で配置（`layout_footerRowPlusIconTintsToPrimary`）
- 5.5 — plus icon `android:tint="@color/kn_primary"`（同上）
- 5.6 — plus icon 右側にラベル TextView
- 5.7 — ラベル inline `textSize=13sp` / `textStyle=bold`（weight 600-700 相当）（`layout_footerRowLabelUsesPrimaryColorAndInlineTextTokens`）
- 5.8 — ラベル `textColor="@color/kn_primary"`（同上）
- 5.9 — ラベル = `@string/autofill_dataset_create_new`（`stringsResource_declaresAutofillDatasetCreateNewInBothLocales`）
- 5.10 — values-ja に同キーが存在（同上）
- 5.11 — onClick 配線なし（layout に `android:onClick` / `android:clickable` 等が含まれない）
- 5.12 — plus + テキスト構造を保持（5.4 / 5.7 のテストで担保）

### Requirement 6: ライト / ダーク両モードの描画整合
- 6.1 — layout は `@color/kn_*` semantic token のみ参照（`layout_doesNotHardcodeHexColorsInAttributes`）
- 6.2 — dark は `values-night/colors.xml` 上書きで自動解決（既存 Phase 1 で kn_surface / kn_text / kn_text_2 / kn_primary が dark に再定義済み）
- 6.3 / 6.4 — Phase 1 で WCAG AA を満たすトークン値を使用（既存 `values-night/colors.xml` 由来）
- 6.5 — footer 行は `drawable-night/kn_dataset_create_row_bg.xml` で `kn_primary_container` に差し替え、kn_primary 文字色との 4.5:1 を確保（`createRowBgNight_referencesKnPrimaryContainerForContrast`）
- 6.6 — root が `@color/kn_surface` を塗ることで host activity 透過を防ぐ（`layout_rootContainerUsesKnSurfaceBackground`）

### Requirement 7: 既存機能・配線の不変
- 7.1 — `DatasetPresentationFactory.kt` は本 PR で未編集（git diff 確認済み）
- 7.2 — `@+id/dataset_label` / `@+id/dataset_subtitle` を保持（`layout_preservesDatasetLabelAndSubtitleViewIds`）
- 7.3 — `DatasetPresentationFactory.kt` 未編集 → `buildInline` 経路未変更
- 7.4 — `KeyNestAutofillService.kt` は本 PR で未編集
- 7.5 — `FillResponseBuilder.kt` は本 PR で未編集
- 7.6 — `AutofillCandidate.kt` は本 PR で未編集
- 7.7 — RemoteViews 範囲外要素なし（`layout_usesRemoteViewsCompatibleViewTypesOnly` / `layout_doesNotReferenceAttrColorTokens`）
- 7.8 — `FillResponseBuilderTest` 5 件 pass（impl-notes.md L233-237 に実行結果）。`LockedFillResponseSecurityTest` の既存 fail は本 Issue 着手前から発生しており、本 Issue 責務外（impl-notes.md L186-192 で「変更前に git stash した状態でも同じ NPE」と記録、新規 Issue 起票候補として明示）

### Non-Functional Requirements
- NFR 1.1 — `./gradlew :app:assembleDebug` 成功（impl-notes.md L222-225）
- NFR 1.2 — Phase 1/2 既存テスト（`FillResponseBuilderTest`）pass（同 L234-237）。LockedFillResponseSecurityTest の既存失敗は本 Issue 着手前から存在し、NFR 1.2 の "成功していたテスト" には該当しないことを Developer が確認済み
- NFR 1.3 — `?attr/...` / カスタム ViewGroup なし（`layout_doesNotReferenceAttrColorTokens` / `layout_usesRemoteViewsCompatibleViewTypesOnly`）
- NFR 2.1 — `autofill_dataset_create_new` / `autofill_dataset_brand_mark_a11y` / `autofill_dataset_row_lock_a11y` を en + ja に同一キーで追加（`stringsResource_declaresAutofillDatasetCreateNewInBothLocales` / `stringsResource_declaresA11yLabelsInBothLocales`）
- NFR 2.2 — `autofill_dataset_brand_eyebrow` は `translatable="false"`（`stringsResource_brandEyebrowIsTranslatableFalse`）
- NFR 2.3 — `signature_match` 既存キー保持（`stringsResource_signatureMatchIsPreserved`）
- NFR 2.4 / 2.5 — en/ja 双方で対応する文字列値を表示（`stringsResource_declaresAutofillDatasetCreateNewInBothLocales`）
- NFR 3.1 / 3.2 — `@color/kn_primary` / `ic_keynest_mark_24` / 13sp 中心 typography 一貫、`DatasetPresentationFactory.build(label, subtitle)` 経路保持
- NFR 4.1 — 装飾アイコン（shield / plus / center key）は `importantForAccessibility="no"` + `contentDescription=@null`、機能アイコン（KN mark / trailing lock）は `contentDescription` 文字列リソース（`layout_decorativeIconsDeclareEmptyContentDescriptionOrNoImportant` / `layout_lockIconAdvertisesLockedContentDescription` / `layout_brandMarkAdvertisesKeyNestContentDescription`）
- NFR 4.2 — layout DOM 順序で label → username の順序を担保（`@+id/dataset_label` が `@+id/dataset_subtitle` より上）
- NFR 4.3 — `minHeight=48dp`（`layout_datasetRowDeclaresMinHeight48Dp`）

## Boundary 確認

本 Issue は Architect 起動なしの直接 implementation のため `tasks.md` が存在せず、formal な `_Boundary:_` アノテーションは無い。Req 7（既存配線の不変）を境界条件として扱い、以下を確認:

- 変更ファイルは `app/src/main/res/` 配下の resource（layout / drawable / strings）+ 新規テスト 1 ファイル + spec docs のみ
- `DatasetPresentationFactory.kt` / `FillResponseBuilder.kt` / `KeyNestAutofillService.kt` / `AutofillCandidate.kt` への変更なし（git diff --name-only develop..HEAD で確認）
- 既存 View ID（`R.id.dataset_label` / `R.id.dataset_subtitle`）保持

境界逸脱なし。

## Findings

なし

## Summary

Issue #34 の全 numeric AC（Req 1.1〜7.8 / NFR 1.1〜4.3）について、`dataset_presentation.xml` 改修・新規 drawable（`kn_dataset_header_bg` / `kn_dataset_create_row_bg` (+ night override) / `ic_lock_outline_16`）追加・en/ja strings 追加・新規 `DatasetPresentationLayoutTokensTest`（34 ケース）で textual pinning による source-level 担保が確認できた。既存ファクトリ / Service / Builder への侵襲なし、`FillResponseBuilderTest` 既存 5 件 pass。`LockedFillResponseSecurityTest` の既存失敗は impl-notes.md で本 Issue 着手前からの NPE と明記され、責務外。

RESULT: approve
