# Review Notes — Issue #80 (Round 1)

## Summary

Developer は Issue #80 を tasks.md T-01..T-05 の順に実装している。差分は OK:

- `git diff --stat develop..HEAD`: 23 files, +896 / -262
  - 本実装: `AutofillIconRasterizer` 新規 (+244), `DatasetPresentationFactory` 改修 (+53),
    `FillResponseBuilder` (+9), `KeyNestAutofillService` (+7), `dataset_presentation.xml`
    `@+id/dataset_icon` 追加 (+1)
  - テスト: `AutofillIconRasterizerTest` 新規 (+230, 11 ケース), `DatasetPresentationFactoryTest`
    新規 (+230, 7 ケース), 既存 3 テスト (FillResponseBuilderTest / LockedFillResponseSecurityTest
    / CustomFieldFillResponseTest) を新シグネチャに対応, `DatasetPresentationLayoutTokensTest`
    に `dataset_icon` 存在 assertion 追加
  - スコープ外 (chore 2 件): `credential_list_activity.xml` の重複 empty-state ブロック削除,
    `values-en/strings.xml` の apostrophe escape, orphan `package_picker_row.xml` +
    `bg_*.xml` 8 件削除。**Issue #80 と無関係の merge cleanup**。impl-notes.md
    「Pre-existing build break」に明記され、unit test 実行のための前提整備として
    Developer が同梱した旨が説明されている。

要件カバレッジは AC 1.1〜5.4 まですべて実装 + テストで担保されている。
inline / popup 双方の fallback 経路の API 分離（popup = composite bitmap、
inline = `Icon.createWithResource`）も design.md §6 通り実装されている。

## Findings

### AC Coverage

requirements.md の AC を個別判定する。

- **AC-1.1** (`PackageManager.getApplicationIcon` で Drawable 取得): **covered**
  `AutofillIconRasterizer.loadCallerIconBitmap` L86 + `loadCallerIconForInline` L145。
  `pm.getApplicationIcon(callerPackage)` 直接呼び出し。
- **AC-1.2** (48dp 相当 / Binder 制約内の Bitmap 変換): **covered**
  `DEFAULT_ICON_DP = 48` + `MAX_SIZE_PX = 192` を companion で定義 (L227, L235)。
  `defaultSizePx = (48 * density).toInt().coerceIn(1, 192)` (L61-64)。
  `composeOnTile` / `rasterise` で `sizePx × sizePx` Bitmap.ARGB_8888 を生成。
- **AC-1.3** (`NameNotFoundException` / `RuntimeException` で fallback): **covered**
  `loadCallerIconBitmap` L88-94 で両例外を catch して `loadFallbackBitmap` を返す。
  test: `loadCallerIconBitmap_nameNotFoundException_returnsFallbackBitmap` /
  `loadCallerIconBitmap_runtimeException_returnsFallbackBitmap`。
- **AC-2.1** (`setImageViewBitmap` で bitmap 設定): **covered**
  `DatasetPresentationFactory.build` L60-62 で `iconRasterizer.loadCallerIconBitmap(callerPackage)`
  → `views.setImageViewBitmap(R.id.dataset_icon, bitmap)`。
- **AC-2.2** (既存 View ID 不変 + 互換的拡張): **covered**
  `dataset_presentation.xml` の中央 ImageView に `@+id/dataset_icon` のみ追加。
  既存 `@+id/dataset_label` / `@+id/dataset_subtitle` は不変
  (`DatasetPresentationLayoutTokensTest` の既存 assertion で担保継続)。
- **AC-2.3** (blue tile 背景上に center crop): **covered**
  `composeOnTile` (L158-164) で `drawBlueTile` → `drawCallerDrawable`。
  `drawCallerDrawable` (L195-215) は `intrinsicWidth/Height > 0` で
  aspect-preserve scale + center crop、`<= 0` で stretch fallback。
- **AC-3.1** (`setStartIcon` を build() の前に必ず呼ぶ): **covered**
  `buildInlineApiR` L122-127 で `loadCallerIconForInline(callerPackage, sizePx)` →
  `InlineSuggestionUi.newContentBuilder(...).setStartIcon(icon).setTitle(...)...build()` の順。
  test: `buildInline_callsSetStartIconWithBitmapIconOnHappyPath` /
  `buildInline_withNullCallerPackage_stillCallsRasterizerForFallbackIcon` で
  rasterizer 呼び出し検証経由で担保。
- **AC-3.2** (正常系で caller icon bitmap が `setStartIcon`): **covered**
  `loadCallerIconForInline` L151-152 で `rasterise(drawable, size)` → `Icon.createWithBitmap(bitmap)`。
  test: `loadCallerIconForInline_happyPath_returnsBitmapIcon` で
  `Icon.type IN (TYPE_BITMAP, TYPE_ADAPTIVE_BITMAP)` を assert。
- **AC-3.3** (fallback で `Icon.createWithResource(ic_key_24)`): **covered**
  `fallbackInlineIcon()` (L155-156) が `Icon.createWithResource(context, R.drawable.ic_key_24)`
  を返し、`NameNotFoundException` / `RuntimeException` / `null` / blank すべての
  経路で呼ばれる (L141-149)。test: 4 ケース
  (`*_nameNotFoundException_*` / `*_runtimeException_*` / `*_nullPackage_*` /
  `*_blankPackage_*`) で `Icon.type == TYPE_RESOURCE && resId == R.drawable.ic_key_24`
  を assert。
- **AC-3.4** (`spec.maxSize` 制約に従う): **covered**
  `buildInlineApiR` L113-121 で
  `cap = if (maxW <= 0 || maxH <= 0) defaultSizePx else min(maxW, maxH)`
  → `sizePx = min(defaultSizePx, cap)`。
  test: `buildInline_clampsBitmapSizeToSpecMaxSize` (maxSize=48 → captured=48) /
  `buildInline_treatsZeroMaxSizeAsUnconstrained` (maxSize=0 → captured=96)。
- **AC-4.1** (`FillRequest` の caller `packageName` を伝搬): **covered**
  `KeyNestAutofillService.kt` で `extractCallerPackage` 結果を
  `buildLockedResponse(..., callerPackage = callerPackage)` に名前付きで渡す
  (L167+ の named-arg 追加部分)。
- **AC-4.2** (locked / unlocked 両方の dataset 行に伝搬): **covered (解釈付き)**
  `buildLockedDataset` 内で `presentationFactory.build` / `buildInline` 双方に
  同一 `callerPackage` を渡す (FillResponseBuilder.kt L103-113)。
  impl-notes §確認事項 4 で「unlocked Dataset は picker に出ないため presentation
  を持たず icon 描画対象外」という design.md §4.3 の解釈を踏襲、`buildUnlockedDataset`
  は `callerPackage` を扱わない。本 reviewer も同解釈を妥当と判定する
  （要件文「両方の dataset 行」は picker 上の行を指すと解釈。design.md §10 確認事項 4
  でも明示）。
- **AC-5.1** (正常系 bitmap が RemoteViews に設定されることを検証): **covered**
  `DatasetPresentationFactoryTest.build_withCallerPackage_invokesRasterizerAndProducesRemoteViews`
  で rasterizer mock の `loadCallerIconBitmap` 呼び出しを `verify(exactly = 1)`。
  rasterizer 出力 bitmap が `RemoteViews` 内に実際に格納されたかは
  Robolectric の `RemoteViews` 検証経路の難しさを理由に rasterizer モック検証で代替
  （impl-notes §確認事項 6 で明記、design.md §7 末尾 Note の方針通り）。
  実 ImageView ID 着地は `DatasetPresentationLayoutTokensTest` の
  `layout_declaresDatasetIconViewIdForCallerIconRebinding` で担保。
- **AC-5.2** (`NameNotFoundException` 経路で fallback bitmap 検証): **covered**
  `AutofillIconRasterizerTest.loadCallerIconBitmap_nameNotFoundException_returnsFallbackBitmap`
  で bitmap が非 null + 正方形であることを assert。
- **AC-5.3** (inline 経路の (a)(b)(c) すべて検証):
  - (a) `setStartIcon` 呼び出し: **covered** —
    `buildInline_callsSetStartIconWithBitmapIconOnHappyPath` /
    `buildInline_withNullCallerPackage_stillCallsRasterizerForFallbackIcon`
    で rasterizer の `loadCallerIconForInline` 呼び出しを `verify(exactly = 1)`。
    factory には他に `Icon` を得る経路がないため、これが setStartIcon 呼び出し
    の間接担保となる旨が test docstring L116-119 に明記されている。
  - (b) 正常系 `Icon.createWithBitmap`: **covered** —
    `AutofillIconRasterizerTest.loadCallerIconForInline_happyPath_returnsBitmapIcon`
    で `icon.type IN (TYPE_BITMAP, TYPE_ADAPTIVE_BITMAP)`。
  - (c) 失敗 / null `callerPackage` で `Icon.createWithResource(ic_key_24)`:
    **covered** — 上記 AC-3.3 の 4 テストケース。
- **AC-5.4** (既存テスト pass): **covered** (developer 自己申告)
  `FillResponseBuilderTest` / `LockedFillResponseSecurityTest` /
  `CustomFieldFillResponseTest` / `DatasetPresentationLayoutTokensTest` は
  新シグネチャに対応済み。impl-notes §既存テスト pre-existing failure で
  `AppInfoProviderTest` 1 件のみが本 Issue 無関係で失敗していると報告
  （`versionName 0.1.0 → 1.0.0` の hardcode 漏れ、別 Issue 化推奨）。
- **NFR 1.1** (既存 View ID 不変): **covered** — `@+id/dataset_label` /
  `@+id/dataset_subtitle` は 1 文字も変更なし。
- **NFR 1.2 / 1.3** (locked Dataset セキュリティ境界不変): **covered** —
  `buildLockedResponse` の `setAuthentication` / `PLACEHOLDER` 経路は不変
  (FillResponseBuilder.kt L143-157)。`LockedFillResponseSecurityTest` も
  「caller package が parcel に含まれることは許容、password 値は不在」を
  明示的に再 assert (新コメント L88-93)。
- **NFR 2.1** (Bitmap サイズ Binder 制約内): **covered** —
  `MAX_SIZE_PX = 192` で coerceIn、N=6 datasets × 144 KB = 864 KB の
  Binder 制限内見積もりが impl-notes §論点 1 で再確認されている。
- **NFR 2.2** (キャッシュなし、毎回 PM 呼び出し): **covered** —
  rasterizer は no-cache 実装。class docstring L36-40 に明記。
- **NFR 3.1** (caller 情報を bitmap 以外で埋め込まない): **covered** —
  factory は bitmap / Icon のみ生成。署名等は触れない。
- **NFR 3.2** (icon 取得失敗時に warn ログ出さない): **covered** —
  rasterizer の catch 節は `Log.w` 等を呼ばず silent fail
  (`L91 // SecurityException / DeadObjectException etc. degrade silently`)。

### Missing Tests

- **なし**。tasks.md T-01..T-05 で要求された全テスト追加が確認できた:
  - `AutofillIconRasterizerTest`: 11 ケース（要求は最低 4 ケース、超過実装で
    drawable variants / size clamping / fallback bitmap 単独 / inline 各経路まで網羅）
  - `DatasetPresentationFactoryTest`: 7 ケース（popup 3 + inline 4）
  - 既存 4 テストの signature 追従と `buildLockedResponse_acceptsNullCallerPackage`
    新規追加
  - `DatasetPresentationLayoutTokensTest`: `@+id/dataset_icon` 存在 assertion
- AC-5.1 に対する「実 `RemoteViews` 内に bitmap が乗ったかの直接検証」は
  rasterizer モック検証 + ID 存在 assertion の 2 段構えで代替している。
  design.md §10 確認事項 6 / impl-notes §確認事項 6 で許容方針が明示されており、
  これは missing test ではなく方針判断として妥当。

### Boundary Violations

- **逸脱あり (documented)**: chore コミット f5b6920 / e41dd7d が tasks.md
  「変更ファイル」に列挙されていない 11 ファイルを変更している:
  - `app/src/main/res/layout/credential_list_activity.xml` (-74 lines)
  - `app/src/main/res/values-en/strings.xml` (apostrophe escape)
  - `app/src/main/res/drawable/bg_card_surface.xml` ほか drawables 8 件 (削除)
  - `app/src/main/res/layout/package_picker_row.xml` (削除)

  これらは Issue #80 のスコープ外（dataset presentation / autofill icon 経路と
  無関係）。tasks.md / requirements.md / design.md には言及がない。

  ただし impl-notes.md §「Pre-existing build break」で以下の通り正当化されている:
  - 修正対象は merge `1b6c058` の取り残しで `mergeDebugResources` /
    `processDebugResources` 段階で AAPT2 が reject していた。
  - これを修正しないと `:app:testDebugUnitTest` 自体が走らず、Issue #80 の
    AC-5.4「既存テスト pass」検証ができない。
  - 削除した orphan resource は Kotlin から参照されておらず、削除しても
    機能影響なし（コミットメッセージで `R.layout.package_picker_row` の
    zero match と FAB style の develop 不在を裏付け）。
  - Reviewer に「本 PR スコープ外だが unit test 実行前提として同梱した、
    本来は別 PR が望ましい」と明示伝達されている。

  本 reviewer は「Issue #80 の AC 検証 (5.4) を成立させるための必要悪であり、
  かつ削除のみで機能リグレッションリスクが低い」と判断、boundary violation
  ではあるが **reject 事由としない**。ただし将来的には別 PR 分離が望ましい
  という Developer の自己認識に同意する。

- 本 Issue 範囲内の boundary は遵守されている:
  - `dataset_presentation.xml`: View ID 追加のみ、layout 構造不変 (AC-2.2 /
    NFR 1.1)
  - `FillResponseBuilder.buildLockedResponse`: 末尾に `callerPackage: String? = null`
    のみ追加、既存引数列順は不変 (impl-notes §実装メモ「引数順」と一致)
  - `DatasetPresentationFactory`: secondary constructor 案ではなく
    constructor default 引数 (`iconRasterizer = AutofillIconRasterizer(context)`)
    を採用、design.md §2 の note 「テスト容易性のため secondary constructor
    を用意してもよい」の範囲内
  - `buildUnlockedDataset` は不変（design.md §4.3 / impl-notes §確認事項 4 解釈通り）
  - `IconLoader` / `ServiceLocator` への変更なし（requirements §Out of Scope 遵守）

## Verdict

approve。requirements.md §1〜§5 / NFR 1〜3 の全 AC が実装 + テストでカバーされており、
design.md §6 / §7 / §10 で示された方針判断（rasterizer の inline / popup API 分離、
RemoteViews 検証の rasterizer モック代替、blue tile inline 焼き込み無し、unlocked
への伝搬不要解釈、キャッシュなし）はすべて impl-notes §確認事項 1〜8 で明示的に
裏付けられている。inline 経路の `setStartIcon` 必須呼び出し（AC-3.1）も `verify`
ベースで担保されている。

唯一の論点は 2 件の chore commit による merge cleanup の同梱だが、これは Issue #80
の AC-5.4 検証成立のための前提整備であり、削除対象が Kotlin 参照を持たない orphan
resource に限定されている点・Developer から Reviewer への明示的な伝達がある点から
reject 事由にはしない（impl-notes §「Pre-existing build break」で別 PR 分離が望ましい
と Developer 自身も認識している）。スタイル / 命名 / lint / フォーマットは判定対象外。

RESULT: approve
