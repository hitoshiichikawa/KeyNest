# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-16T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-46-impl-bug-icons-3-43-hotfix-log
- HEAD commit: 928feaadc9543de661934e977a917179150841e8
- Compared to: develop..HEAD
- Feature Flag Protocol: opt-out（CLAUDE.md `**採否**: opt-out`。flag 観点の確認は実施しない）
- 注記: 本 spec ディレクトリには `tasks.md` / `design.md` が存在せず、Architect ステージを
  経ていない hotfix。boundary 判定は `requirements.md` § Scope の対象ファイル表を準正本として扱った。

## Verified Requirements

- 1.1 — `CredentialListAdapter.kt:131` が `iconLoader.loadInto(binding.iconApp, ...)` を呼び、
  `IconLoader.loadInto`（`IconLoader.kt:110-152`）が `applicationScope.launch` で resolve を
  行い `setImageDrawable` 適用。`credential_list_item.xml` の `clipToOutline` 除去で可視化。
  テスト: `IconLoaderTest.loadInto_resolvesWithInjectedScope_independentOfImageViewAttachment`
- 1.2 — `RecentlyUsedCarouselAdapter.kt:74` が同様に `loadInto` を呼ぶ。
  `credential_list_recent_item.xml` の `clipToOutline` 除去。テスト: 同上の loadInto 系
- 1.3 — `PackagePickerBottomSheet.kt:285` が `loadInto` を呼ぶ。
  `package_picker_row_item.xml` の `clipToOutline` 除去。テスト: 同上
- 1.4 — `IconLoader.resolveOrFallback`（`IconLoader.kt:201-219`）が `NameNotFoundException` /
  `RuntimeException` を catch して `InitialLetterDrawable` にフォールバック。
  テスト: 既存 `resolve_nameNotFound_returnsInitialLetterDrawable` /
  `resolve_runtimeException_returnsInitialLetterDrawable`
- 1.5 — blank packageName は `setImageDrawable(null)` で背景タイルを残し、それ以外は実
  drawable または fallback drawable を確実に適用。テスト:
  `loadInto_blankPackageName_clearsImageView_andDoesNotCallPackageManager` +
  `InitialLetterDrawable.intrinsic*` 群（fitCenter で文字形が描画可能なこと）
- 2.1 — `IconLoader.kt:134` `applicationScope.launch`（`ServiceLocator.applicationScope` =
  `SupervisorJob() + Dispatchers.Main.immediate`）でプロセス寿命スコープ起動。
  テスト: `loadInto_resolvesWithInjectedScope_independentOfImageViewAttachment`
  （未 attach の `ImageView` でも resolve が完走することを検証）
- 2.2 — `applicationScope: CoroutineScope` は non-null 型でコンパイル時保証。RuntimeException
  経路で silent fail せず `Log.w` + fallback drawable 解決。
  テスト: 型契約 + `resolve_runtimeException_returnsInitialLetterDrawable`
- 2.3 — `IconLoader.kt:141` `imageView.getTag(R.id.icon_loader_request_tag) == packageName`
  の tag 照合分岐は維持。テスト:
  `loadInto_tagMismatchAfterRebind_doesNotOverwriteRecycledRow`
- 3.1 — `InitialLetterDrawable.getIntrinsicWidth/Height` が `intrinsicSizePx`（正の px）を
  返す override。テスト:
  `intrinsicWidth_returnsPositivePxValuePassedAtConstruction` /
  `intrinsicHeight_returnsPositivePxValuePassedAtConstruction` /
  `intrinsicSize_isNotNegativeOne_evenForSmallTilePx`
- 3.2 — 正の intrinsic 値で `fitCenter` の destination rect が非空となる。同上の 3 テストが
  `> 0` および `!= -1` を直接 assert
- 3.3 — `TEXT_SIZE_RATIO = 0.45f` および `bounds.width()/height()` 起点描画は維持
  （`InitialLetterDrawable.kt:143` 不変）。既存 `computeInitial_*` 群が glyph 計算ロジックを
  継続カバー
- 4.1 — `IconLoader.kt:118` `Log.d(LOG_TAG, "loadInto: request pkg=$packageName")`
- 4.2 — `IconLoader.kt:123` `Log.d(LOG_TAG, "loadInto: cacheHit pkg=$packageName")` +
  `IconLoader.kt:185` `Log.d(LOG_TAG, "resolve: cacheHit pkg=$packageName")`
- 4.3 — `IconLoader.kt:204` `Log.d(LOG_TAG, "resolveOrFallback: success pkg=$packageName")`
- 4.4 — `IconLoader.kt:207-210` `Log.d(LOG_TAG, "resolveOrFallback: fallback pkg=...
  reason=NameNotFoundException")`
- 4.5 — `IconLoader.kt:149` `Log.w(LOG_TAG, "loadInto: cancelledByRecycle pkg=...")` +
  `IconLoader.kt:213-216` `Log.w(LOG_TAG, "resolveOrFallback: fallback pkg=...
  reason=RuntimeException派生クラス名")`
- 4.6 — log 引数は `packageName` / `e.javaClass.simpleName` / 固定リテラルのみ
  （ソース全 6 箇所をコードレビューで確認）。machine-sensitive な credential 文字列の伝播は無し
- 5.1 — 3 layout から `android:clipToOutline="true"` を削除済み
  （`credential_list_item.xml:74`, `credential_list_recent_item.xml:64`,
  `package_picker_row_item.xml:61` の各 hunk で除去確認）
- 5.2 — 親 `FrameLayout` の `android:background="@drawable/kn_icon_tile_bg"` は 3 layout で
  維持（diff 範囲外で不変）。`kn_icon_tile_bg` は `kn_r_icon_tile=12dp` の角丸描画を担保
- 5.3 — 5.2 と同一実装で角丸視覚を保持
- NFR 1.1 — `DEFAULT_CACHE_CAPACITY = 64` 不変（`IconLoader.kt:247`）。
  テスト: `resolve_cacheCapacityRespected_oldestEvictedFirst`
- NFR 1.2 — cache hit 経路は `LruCache.get` + `setImageDrawable` のみで同期実行。
  テスト: `loadInto_cacheMissResultIsPutIntoLruCache_soSecondBindIsSynchronous`
  （2 回目 bind で PM 呼び出し回数 1 を assert）
- NFR 1.3 — `withContext(ioDispatcher)` で `pm.getApplicationIcon` を実行
  （`IconLoader.kt:135`）
- NFR 2.1 — 通常分岐 4 本は `Log.d`（warn 未満）
- NFR 2.2 — 異常分岐 2 本は `Log.w`（warn 以上）
- NFR 3.1 / 3.2 — 4.6 と同一根拠

## Findings

なし

## Summary

Issue #46 の 5 つの Requirement と 3 つの NFR について、実装・layout 変更・テスト追加を
網羅的に確認した。AC 全 ID（1.1〜1.5 / 2.1〜2.3 / 3.1〜3.3 / 4.1〜4.6 / 5.1〜5.3 +
NFR 1.x / 2.x / 3.x）に対応する観測可能な実装またはテストが存在し、boundary も
`requirements.md` § Scope 対象ファイル表内に収まっている。Developer の `impl-notes.md` で
申告されている既知の事前破損テスト 5 件（PackageSignatureResolverTest 4 + Autofill 1）は
Issue #43 着手前から存在する事前破損で本 PR 由来ではないことを確認済み。診断 log の
Logcat 出力検証は `isReturnDefaultValues=true` 環境で no-op となるためコードレビューで
担保しており、log 引数は packageName + 結果分類 + 例外クラス名のみで機微情報を含まない
（NFR 3.1/3.2 充足）。

RESULT: approve
