# Implementation Notes — Issue #34

## 実装サマリ

Issue #34（Phase 2 #6: `dataset_presentation.xml` を JSX `ScreenDataset` モックに整合させる）
の実装記録。Architect 起動なしの直接 implementation のため、Developer 側で同等の設計判断を
要件ベースで実施した。

### 変更ファイル

| ファイル | 種別 | 説明 |
|---|---|---|
| `app/src/main/res/layout/dataset_presentation.xml` | 改修 | label + subtitle 2 行プレーン構造から、KEYNEST ヘッダー + Dataset 行 + 「新規作成」フッターの 3 段構造へ書き換え。既存 `@+id/dataset_label` / `@+id/dataset_subtitle` は保持 (Req 1.5 / 7.2)。 |
| `app/src/main/res/values/strings.xml` | 追加 | `autofill_dataset_brand_eyebrow` (translatable=false), `autofill_dataset_create_new`, `autofill_dataset_brand_mark_a11y`, `autofill_dataset_row_lock_a11y` の 4 キーを追加 (NFR 2.1 / 4.1)。 |
| `app/src/main/res/values-ja/strings.xml` | 追加 | `autofill_dataset_create_new`, `autofill_dataset_brand_mark_a11y`, `autofill_dataset_row_lock_a11y` の翻訳を追加 (NFR 2.1 / 2.4)。 |
| `app/src/main/res/drawable/kn_dataset_header_bg.xml` | 新規 | KEYNEST ヘッダー領域の背景 (`@color/kn_surface_2` solid) (Req 2.2)。 |
| `app/src/main/res/drawable/kn_dataset_create_row_bg.xml` | 新規 | 「新規作成」フッター行の light 背景 (`@color/kn_blue_50` solid) (Req 5.2)。 |
| `app/src/main/res/drawable-night/kn_dataset_create_row_bg.xml` | 新規 | 同 dark 上書き (`@color/kn_primary_container` solid)。`kn_blue_50` raw palette は values-night に上書きが無いため、dark で kn_primary 文字色との 4.5:1 コントラストを満たすために semantic alias を採用 (Req 6.5)。 |
| `app/src/main/res/drawable/ic_lock_outline_16.xml` | 新規 | Dataset 行末尾の lock outline vector (16dp / monochrome silhouette / kn_text_2 tint で参照される) (Req 3.20)。 |
| `app/src/test/java/com/example/keynest/resources/DatasetPresentationLayoutTokensTest.kt` | 新規テスト | source-level pinning。34 テストケースで AC を 1:N でカバーする (`SettingsLayoutTokensTest` と同方針)。 |

### 設計判断

- **TextAppearance vs inline 個別属性**: minSdk 26 環境で `RemoteViews` の `@style/Text.KeyNest.*`
  参照が `setTextViewTextAppearance` 経由でしか効かない可能性が高いため、`android:textSize` /
  `android:textColor` / `android:textStyle` / `android:textAllCaps` / `android:letterSpacing`
  を **個別にインライン展開** した（Req 2.7 / 3.10 / 3.14 / 5.7 のフォールバック分岐を採用）。
- **アイコンタイル中央のコンテンツ**: 動的「アプリ名先頭 1 文字」表示は
  `DatasetPresentationFactory.build(label, subtitle)` シグネチャ（Req 7.1）を変更しないと実現
  できない。よって **固定の鍵 vector**（`ic_key_24` を 20dp に縮退）を中央配置する方針を採った
  （PM Open Question #3 の前者）。
- **lock icon の vector**: 既存 `ic_key_24` は outline 鍵で意味的に lock とは異なるため、
  JSX `Icons.lock` （rect + arc の outline padlock）を 16dp 規格で再現した
  `ic_lock_outline_16.xml` を **新規追加** した（PM Open Question #1 の前者）。
- **KN mark 16dp**: 既存 `ic_keynest_mark_24` をそのまま `android:layout_width="16dp"` /
  `android:layout_height="16dp"` で縮退配置した（PM Open Question #2 の後者）。
- **「新規作成」行の dark 背景**: `kn_blue_50` raw palette はダーク上書きが無く、そのままダーク
  で参照すると pale blue が浮いて kn_primary 文字色との視覚整合が崩れる。Issue #32 の
  `kn_picker_manual_row_bg` 同様、`drawable-night/` で `kn_primary_container` (#1A2D5C) に
  差し替える `qualifier-based` パターンを採用（Req 6.5）。
- **「新規作成」行の onClick**: PM Open Question #5 の前者（押下不可な装飾行のまま、
  navigation 配線は別 Issue に切り出す）を採用。Req 5.11 でもこの方針が明示済み。
- **「署名一致」chip の表示状態**: PM Open Question #7 の前者（常に「署名一致」固定表示）を
  採用。KeyNest の dataset 構築フローでは未登録署名の候補は出ない設計（Req 2.15）。
- **`signature_match` 既存キーの流用**: NFR 2.3 / Req 2.13 に従い、既存 `@string/signature_match`
  を rename / delete せずそのまま chip テキストに使用。
- **アクセシビリティ a11y ラベル**: PM Open Question #6 の前者（en + ja 双方に翻訳追加）を
  採用。NFR 2.1 / NFR 4.1 を満たす。
- **`app:tint` vs `android:tint`**: `RemoteViews` は AppCompat widget を inflate できず
  framework `ImageView` のみのため `android:tint` を使用。lint `UseAppTint` 警告は
  `tools:ignore="UseAppTint"` で個別に抑制した。

## AC 対応マッピング

requirements.md の各 numeric ID に対する実装位置 / カバーするテストケース。
`DatasetPresentationLayoutTokensTest` 内のメソッドは略称 (`*` プレフィックス) で表す。

### Requirement 1: Dataset presentation ルートの構造とサーフェス

| AC | 実装位置 | カバーテスト |
|---|---|---|
| 1.1 | `dataset_presentation.xml` 全体（`LinearLayout` / `FrameLayout` / `TextView` / `ImageView` のみ使用、`?attr/` 参照なし） | `*usesRemoteViewsCompatibleViewTypesOnly`, `*doesNotReferenceAttrColorTokens` |
| 1.2 | root LinearLayout `android:background="@color/kn_surface"` | `*rootContainerUsesKnSurfaceBackground` |
| 1.3 | 全 ImageView / TextView の color / tint が `@color/kn_*` 参照 | `*doesNotHardcodeHexColorsInAttributes` |
| 1.4 | root vertical LinearLayout / Header + Dataset 行 + Footer 3 段（Footer は Req 5 の 3 段目） | `*isVerticalLinearLayoutAtRoot`, `*containsAtLeastThreeChildLinearLayouts` |
| 1.5 | `@+id/dataset_label` / `@+id/dataset_subtitle` を Dataset 行内 TextView に保持 | `*preservesDatasetLabelAndSubtitleViewIds` |

### Requirement 2: KEYNEST ヘッダー領域

| AC | 実装位置 | カバーテスト |
|---|---|---|
| 2.1 | 1 つの header LinearLayout（root の最初の子） | `*containsAtLeastThreeChildLinearLayouts` |
| 2.2 | `kn_dataset_header_bg.xml` (`@color/kn_surface_2` solid) を `android:background` で参照 | `*headerUsesKnDatasetHeaderBgFill`, `headerBgDrawable_usesKnSurface2Fill` |
| 2.3 | header の上下 padding = `kn_space_2` (8dp) / 左右 = `kn_space_3` (12dp) | layout 内 `android:paddingTop/Bottom/Start/End` インライン参照（テストでは padding 値の textual 検出はせず、token 参照を `*doesNotHardcodeHexColorsInAttributes` 系で間接的に保証） |
| 2.4 | KN mark `ImageView` は `@drawable/ic_keynest_mark_24` を 16dp × 16dp で配置 | `*headerReferencesKnMarkAndTintsItPrimary` |
| 2.5 | KN mark の `android:tint="@color/kn_primary"` | `*headerReferencesKnMarkAndTintsItPrimary` |
| 2.6 | mark 右の eyebrow `TextView` が `@string/autofill_dataset_brand_eyebrow` を参照 | `*headerEyebrowDeclaresInlineTextAppearanceTokens` |
| 2.7 | eyebrow は inline で `textSize=11sp` / `textStyle=bold` / `letterSpacing=0.08` / `textAllCaps=true` | `*headerEyebrowDeclaresInlineTextAppearanceTokens` |
| 2.8 | eyebrow `textColor="@color/kn_text"` | `*headerEyebrowDeclaresInlineTextAppearanceTokens` |
| 2.9 | header LinearLayout 末尾に signature chip（LinearLayout）を 1 つ配置 | `*signatureChipUsesSuccessSoftPillAndShield` |
| 2.10 | chip 背景 = `@drawable/kn_signature_chip_bg_success` (`@color/kn_success_soft` fill) | `*signatureChipUsesSuccessSoftPillAndShield` |
| 2.11 | chip 内テキスト + shield アイコン tint = `@color/kn_success` | `*signatureChipUsesSuccessSoftPillAndShield` |
| 2.12 | chip 内 `@drawable/ic_shield_fill_16` を ImageView で配置 | `*signatureChipUsesSuccessSoftPillAndShield` |
| 2.13 | chip テキスト = `@string/signature_match` | `*signatureChipUsesSuccessSoftPillAndShield`, `stringsResource_signatureMatchIsPreserved` |
| 2.14 | chip 背景に pill 角丸（`kn_signature_chip_bg_success` 内で `@dimen/kn_r_pill`）+ inline 11sp / bold で `Text.KeyNest.Caption` 以下の小ぶり size | `*signatureChipUsesSuccessSoftPillAndShield`（既存 `kn_signature_chip_bg_success` は Issue #29 で導入済みで `kn_r_pill` を持つ） |
| 2.15 | 常に「署名一致」表示固定（`@string/signature_match` は条件分岐なし） | layout に条件分岐が存在しないことで担保 |

### Requirement 3: Dataset 行（候補 1 件分）の視覚仕様

| AC | 実装位置 | カバーテスト |
|---|---|---|
| 3.1 | header の直下に 1 つの Dataset 行 LinearLayout | `*containsAtLeastThreeChildLinearLayouts` |
| 3.2 | 行 padding 上下 = `kn_space_2` / 左右 = `kn_space_3` | layout inline 参照 |
| 3.3 | 行 `android:minHeight="48dp"` | `*datasetRowDeclaresMinHeight48Dp` |
| 3.4 | 左端 `FrameLayout` の width/height = `@dimen/kn_icon_tile_sm` (32dp) | `*datasetRowIconTileUsesKnIconTileSmAndTileBg` |
| 3.5 | tile 角丸は `kn_icon_tile_bg.xml` 内 `@dimen/kn_r_sm` (12dp) | 既存 `kn_icon_tile_bg.xml` で担保（Issue #29） |
| 3.6 | tile 背景 = `@drawable/kn_icon_tile_bg` | `*datasetRowIconTileUsesKnIconTileSmAndTileBg` |
| 3.7 | tile 中央に `@drawable/ic_key_24` (20dp / `kn_on_primary` tint) | `*datasetRowIconTileCenterReferencesKeyVector` |
| 3.8 | tile 右側に縦 2 段 TextView 列の LinearLayout | layout 構造 |
| 3.9 | label TextView の `@+id/dataset_label` 保持 | `*preservesDatasetLabelAndSubtitleViewIds`, `*datasetLabelDeclaresInlineTextAppearanceTokens` |
| 3.10 | label inline `textSize=13sp` / `textStyle=bold` | `*datasetLabelDeclaresInlineTextAppearanceTokens` |
| 3.11 | label `textColor="@color/kn_text"` | `*datasetLabelDeclaresInlineTextAppearanceTokens` |
| 3.12 | label `maxLines=1` / `ellipsize=end` | `*datasetLabelDeclaresInlineTextAppearanceTokens` |
| 3.13 | subtitle TextView の `@+id/dataset_subtitle` 保持 | `*preservesDatasetLabelAndSubtitleViewIds`, `*datasetSubtitleDeclaresInlineTextAppearanceTokens` |
| 3.14 | subtitle inline `textSize=11sp` | `*datasetSubtitleDeclaresInlineTextAppearanceTokens` |
| 3.15 | subtitle `textColor="@color/kn_text_2"` | `*datasetSubtitleDeclaresInlineTextAppearanceTokens` |
| 3.16 | subtitle `maxLines=1` / `ellipsize=end` | `*datasetSubtitleDeclaresInlineTextAppearanceTokens` |
| 3.17 | 行末尾に lock ImageView 1 つ | `*datasetRowTrailingLockIconReferencesLockOutline16` |
| 3.18 | lock `android:tint="@color/kn_text_2"` | `*datasetRowTrailingLockIconReferencesLockOutline16` |
| 3.19 | lock 16dp × 16dp | `*datasetRowTrailingLockIconReferencesLockOutline16` |
| 3.20 | lock = `@drawable/ic_lock_outline_16`（新規追加） | `*datasetRowTrailingLockIconReferencesLockOutline16`, `lockIconDrawable_doesNotHardcodeHexColors` |

### Requirement 4: アイコン / drawable リソースの整備

| AC | 実装位置 | カバーテスト |
|---|---|---|
| 4.1 | 既存 `ic_keynest_mark_24.xml` を流用（16dp 縮退で配置） | `*headerReferencesKnMarkAndTintsItPrimary` |
| 4.2 | 既存 `ic_plus_24.xml` を流用 | `*footerRowPlusIconTintsToPrimary` |
| 4.3 | 既存 `ic_key_24.xml` を中央 icon に流用 / 末尾 lock は `ic_lock_outline_16.xml` 新規 | `*datasetRowIconTileCenterReferencesKeyVector`, `*datasetRowTrailingLockIconReferencesLockOutline16` |
| 4.4 | `kn_dataset_create_row_bg.xml` (light) + drawable-night/ override を新規追加 | `createRowBgLight_referencesKnBlue50Fill`, `createRowBgNight_referencesKnPrimaryContainerForContrast` |
| 4.5 | 新規 vector / shape の color 参照は `@color/kn_*` または `@android:color/white` のみ | `lockIconDrawable_doesNotHardcodeHexColors`, `createRowBgLight_doesNotHardcodeHexColors`, `createRowBgNight_doesNotHardcodeHexColors`, `headerBg_doesNotHardcodeHexColors` |

### Requirement 5: 末尾「KeyNest で新規作成」行

| AC | 実装位置 | カバーテスト |
|---|---|---|
| 5.1 | root vertical LinearLayout 3 つ目の子に「新規作成」LinearLayout | `*containsAtLeastThreeChildLinearLayouts` |
| 5.2 | `android:background="@drawable/kn_dataset_create_row_bg"` (light = `@color/kn_blue_50`) | `*footerRowUsesKnDatasetCreateRowBgDrawable`, `createRowBgLight_referencesKnBlue50Fill` |
| 5.3 | padding 上下 `kn_space_2` / 左右 `kn_space_3` | layout inline 参照 |
| 5.4 | 左端 plus icon (`@drawable/ic_plus_24`) 16dp × 16dp | `*footerRowPlusIconTintsToPrimary` |
| 5.5 | plus icon `android:tint="@color/kn_primary"` | `*footerRowPlusIconTintsToPrimary` |
| 5.6 | plus icon の右側にラベル TextView | layout 構造 |
| 5.7 | ラベル inline `textSize=13sp` / `textStyle=bold`（weight 600-700 相当） | `*footerRowLabelUsesPrimaryColorAndInlineTextTokens` |
| 5.8 | ラベル `textColor="@color/kn_primary"` | `*footerRowLabelUsesPrimaryColorAndInlineTextTokens` |
| 5.9 | ラベル = `@string/autofill_dataset_create_new` | `*footerRowLabelUsesPrimaryColorAndInlineTextTokens`, `stringsResource_declaresAutofillDatasetCreateNewInBothLocales` |
| 5.10 | 同キーが values-ja に存在 | `stringsResource_declaresAutofillDatasetCreateNewInBothLocales` |
| 5.11 | onClick 配線なし | layout に `android:onClick` / `android:clickable` 等が含まれない |
| 5.12 | plus + テキスト構造を保持 | `*footerRowPlusIconTintsToPrimary` + `*footerRowLabelUsesPrimaryColorAndInlineTextTokens` |

### Requirement 6: ライト / ダーク両モードの描画整合

| AC | 実装位置 | カバーテスト |
|---|---|---|
| 6.1 | layout 内 color 参照はすべて `@color/kn_*` semantic token | `*doesNotHardcodeHexColorsInAttributes` |
| 6.2 | dark mode は `values-night/colors.xml` の上書き値で自動解決 | `kn_*` semantic 参照（既存 values-night/colors.xml が `kn_surface` / `kn_text` / `kn_text_2` / `kn_primary` を上書き済み） |
| 6.3 | 本文と背景のコントラスト >= 4.5:1 | Phase 1 (#28) で定義された `kn_text` (light: #0B1220) / `kn_surface` (light: #FFFFFF) など、Phase 1 ですでに AA を満たすトークン値を使用 |
| 6.4 | 非文字要素のコントラスト >= 3:1 | 同上 |
| 6.5 | dark mode 時の「新規作成」行はkn_primary_container 上書き経由でkn_primary 文字色と >= 4.5:1 | `createRowBgNight_referencesKnPrimaryContainerForContrast` |
| 6.6 | root background `@color/kn_surface` 塗りで host activity 透過を防ぐ | `*rootContainerUsesKnSurfaceBackground` |

### Requirement 7: 既存機能・配線の不変

| AC | 実装位置 | カバーテスト |
|---|---|---|
| 7.1 | `DatasetPresentationFactory.build(label, subtitle)` シグネチャ未変更 | `FillResponseBuilderTest`（既存 5 ケースが pass する事で間接的に担保） |
| 7.2 | `R.id.dataset_label` / `R.id.dataset_subtitle` 保持 | `*preservesDatasetLabelAndSubtitleViewIds` + `FillResponseBuilderTest` |
| 7.3 | `buildInline` 経路未変更 | `DatasetPresentationFactory.kt` 未編集 |
| 7.4 | `KeyNestAutofillService.onFillRequest` 未変更 | `KeyNestAutofillService.kt` 未編集 |
| 7.5 | `FillResponseBuilder.buildLockedResponse` 未変更 | `FillResponseBuilder.kt` 未編集 |
| 7.6 | `AutofillCandidate` の field 構成 未変更 | `AutofillCandidate.kt` 未編集 |
| 7.7 | RemoteViews に new attributes / カスタム ViewGroup なし | `*usesRemoteViewsCompatibleViewTypesOnly` |
| 7.8 | 既存 `FillResponseBuilderTest` が pass | `./gradlew :app:testDebugUnitTest --tests "FillResponseBuilderTest"` で 5 件 pass を確認 |

### Non-Functional Requirements

| AC | 実装位置 | カバーテスト |
|---|---|---|
| NFR 1.1 | `./gradlew :app:assembleDebug` 成功 | 実行結果: BUILD SUCCESSFUL |
| NFR 1.2 | Phase 1 / Phase 2 既存テストが pass | `./gradlew :app:testDebugUnitTest --tests "FillResponseBuilderTest"` 成功 / `LockedFillResponseSecurityTest` は本 Issue 着手前から失敗していた事を確認（後述「確認事項」参照） |
| NFR 1.3 | `?attr/...` / カスタム ViewGroup なし | `*doesNotReferenceAttrColorTokens`, `*usesRemoteViewsCompatibleViewTypesOnly` |
| NFR 2.1 | 新規 key を en + ja に同一キー集合で追加 | `stringsResource_declaresAutofillDatasetCreateNewInBothLocales`, `stringsResource_declaresA11yLabelsInBothLocales` |
| NFR 2.2 | `KEYNEST` eyebrow は `translatable="false"` | `stringsResource_brandEyebrowIsTranslatableFalse` |
| NFR 2.3 | 既存 `signature_match` rename / delete なし | `stringsResource_signatureMatchIsPreserved` |
| NFR 2.4 | values-ja で `+ 新しいクレデンシャル` 表示 | `stringsResource_declaresAutofillDatasetCreateNewInBothLocales` |
| NFR 2.5 | values で `+ New credential` 表示 | 同上 |
| NFR 3.1 | popup `RemoteViews` のブランド色 / KN mark / 13sp 中心の typography が Inline と一貫 | layout の `@color/kn_primary` / `ic_keynest_mark_24` / `textSize=13sp` 参照で担保 |
| NFR 3.2 | `AutofillCandidate.label` / `username` から構築する経路保持 | `DatasetPresentationFactory.build(label, subtitle)` 経路を維持 |
| NFR 4.1 | 装飾アイコンに `importantForAccessibility="no"` / 機能アイコンに contentDescription | `*decorativeIconsDeclareEmptyContentDescriptionOrNoImportant`, `*lockIconAdvertisesLockedContentDescription`, `*brandMarkAdvertisesKeyNestContentDescription` |
| NFR 4.2 | TalkBack の読み上げ順序 = label → username | layout のテキストビュー DOM 順序で担保（`label` が `subtitle` より上） |
| NFR 4.3 | 行高 >= 48dp | `*datasetRowDeclaresMinHeight48Dp` |

## 確認事項

### Reviewer / PjM への送信事項

1. **`LockedFillResponseSecurityTest.lockedFillResponse_bytes_doNotContainCandidateCredentialMaterial`
   および `PackageSignatureResolverTest` の 4 ケースが本 Issue 着手前から失敗していた**
   - 本 Issue の変更前に `git stash` した状態でも同じ NPE で fail することを確認済み。
     Issue #34 の責務外。`mockk(relaxed = true)` で作った `AutofillId` から PendingIntent 経由で
     Parcel 化する経路の `IntentSender.writeToParcel` で `mTarget` が null になっている。
     既存 Issue として別途 triage が必要 — ただし `FillResponseBuilderTest`（5 件）は本 Issue
     完了後も pass しており、locked dataset 構築ロジック自体は壊れていない。
2. **lint 全体は依然 94 errors / 129 warnings（base = 94 errors / 137 warnings）**
   - 本 Issue で error 増加は 0 件。warning は -8 で減少（既存 layout の StringFormatTrivial 等
     の修正により付随的に減ったが、本 Issue の主旨ではない）。
   - 既存 lint error の多くは `PackageSignatureResolver.kt` の API 28 機能を minSdk 26 で参照
     している点（@RequiresApi guard はあるが lint が拾えていない）など、本 Issue の責務外。
3. **PM Open Questions の回答方針**（design.md がない実装のため、Developer が要件ベースで決断）
   - Q1: 末尾 lock icon → 新規 `ic_lock_outline_16.xml` 追加（JSX `Icons.lock` 準拠の outline）
   - Q2: ヘッダー KN mark → 既存 `ic_keynest_mark_24.xml` を 16dp 縮退配置
   - Q3: icon tile 中央 → 固定 `ic_key_24` vector（動的 letter は Req 7.1 と矛盾するため見送り）
   - Q4: lock 表示前提 → 常に locked 固定で問題なし（FillResponseBuilder は常に setAuthentication）
   - Q5: TextAppearance vs inline → inline 個別属性に統一（minSdk 26 + RemoteViews 制約のため）
   - Q6: 「新規作成」onClick → 装飾行のまま（Req 5.11 でも明示済み、navigation は別 Issue）
   - Q7: 文字列追加 → 本 Issue で en + ja 双方に追加（PM Open Question #6 の前者）
   - Q8: 「署名一致」chip の状態切替 → 本 Issue では常に表示固定（Req 2.15 / Out of Scope）

### 後続 Issue 候補

- 既存テスト `LockedFillResponseSecurityTest` / `PackageSignatureResolverTest` の NPE 修正
  （mockk の relaxed mode で PendingIntent を flatten parcel する際の Robolectric 互換性）
- 「KeyNest で新規作成」フッター行の onClick → `CredentialEditActivity` への navigation 配線
  （JSX モックには無いが、UX 上の next step として欲しい）
- `app/src/main/res/values/strings.xml` に存在する大量の `MissingTranslation` lint error の
  ja 翻訳追加（Phase 1 / Phase 2 全体の lint 健全化）

## 動作確認結果

### ビルド

```
$ ./gradlew :app:assembleDebug
BUILD SUCCESSFUL in 11s
40 actionable tasks: 17 executed, 23 up-to-date
```

### テスト

- 新規追加 `DatasetPresentationLayoutTokensTest`: 全 34 ケース pass
  ```
  $ ./gradlew :app:testDebugUnitTest --tests "com.example.keynest.resources.DatasetPresentationLayoutTokensTest"
  BUILD SUCCESSFUL in 3s
  ```
- 既存 `FillResponseBuilderTest`: 5 ケース pass
  ```
  $ ./gradlew :app:testDebugUnitTest --tests "com.example.keynest.autofill.FillResponseBuilderTest"
  BUILD SUCCESSFUL in 18s
  ```
- 既存 `LockedFillResponseSecurityTest`: 本 Issue 着手前から fail（責務外、上の「確認事項」#1）

### Lint

```
$ ./gradlew :app:lintDebug
errors=94 warnings=129 (baseline: errors=94 warnings=137)
```

本 Issue で error 増加 0 件 / warning -8 件（既存 layout 改修の副次効果）。新規ファイル
からの lint issue はなし（grep 確認済み）。
