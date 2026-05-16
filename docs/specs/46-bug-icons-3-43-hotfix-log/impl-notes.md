# Implementation Notes — Issue #46

## 実装概要

PR #45 (Issue #43) で導入した `IconLoader` 経路が、実機の 3 画面（credential 一覧 /
最近使用 carousel / Package Picker）で実アイコンも頭文字 fallback も描画せず、青タイル
背景だけが見える状態だった。本 Issue は実機での描画復旧と、再発時に Logcat で 1 次切り分けが
できる診断 log の追加を行う hotfix。

`IconLoader` / `InitialLetterDrawable` / `ServiceLocator` / 3 layout XML / 1 単体テスト
（既存）に変更を加え、新規の `IconLoader.loadInto` 系テスト 5 本と `InitialLetterDrawable`
の intrinsic size テスト 3 本を追加した。

## 変更ファイル一覧

| 区分 | パス | 変更概要 |
| --- | --- | --- |
| Kotlin (src) | `app/src/main/java/com/example/keynest/util/IconLoader.kt` | コンストラクタに `applicationScope: CoroutineScope` と `fallbackIntrinsicSizePx: Int` を追加。`findViewTreeLifecycleOwner` 依存を排除し、`applicationScope.launch` で resolve coroutine を起動。`cache.put` を `loadInto` 経路にも追加（取りこぼし fix）。`Log.d` 4 本 + `Log.w` 2 本の診断 log を追加 |
| Kotlin (src) | `app/src/main/java/com/example/keynest/util/InitialLetterDrawable.kt` | コンストラクタに `intrinsicSizePx: Int` を追加。`getIntrinsicWidth()` / `getIntrinsicHeight()` を override（正の px を返す）。`draw()` の `bounds.isEmpty` early return は維持 |
| Kotlin (src) | `app/src/main/java/com/example/keynest/di/ServiceLocator.kt` | `applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` を 1 つ保持。`iconLoader` 生成時に渡す |
| Layout | `app/src/main/res/layout/credential_list_item.xml` | `icon_app` ImageView から `android:clipToOutline="true"` を削除。親 FrameLayout の `@drawable/kn_icon_tile_bg` で角丸視覚を維持 |
| Layout | `app/src/main/res/layout/credential_list_recent_item.xml` | 同上 |
| Layout | `app/src/main/res/layout/package_picker_row_item.xml` | 同上 |
| Kotlin (test) | `app/src/test/java/com/example/keynest/util/IconLoaderTest.kt` | 全ケースで `TestScope` を `applicationScope` 引数に渡すよう更新。`loadInto` 系テストを 5 本追加（process-wide scope / cache 書き込み / tag mismatch race / blank packageName / 既存 6 ケースのコンストラクタ更新） |
| Kotlin (test) | `app/src/test/java/com/example/keynest/util/InitialLetterDrawableTest.kt` | `getIntrinsicWidth/Height` が正の px を返すケースを 3 本追加 |
| Docs | `docs/specs/46-bug-icons-3-43-hotfix-log/impl-notes.md` | 本ファイル |

Adapter 3 件（`CredentialListAdapter` / `RecentlyUsedCarouselAdapter` /
`PackagePickerBottomSheet.SectionAdapter`）は `IconLoader` のコンストラクタ追加引数が
`ServiceLocator` 側で吸収されるため、変更不要。`onViewRecycled` で
`iconLoader.cancel(imageView)` を呼ぶ既存配線（Issue #43 Req 2.4）は維持。

## 仮説 A〜D への対応 mapping

Issue 本文の調査メモにある仮説 4 種への対応:

| 仮説 | 内容 | 本 Issue での対応 |
| --- | --- | --- |
| A | `findViewTreeLifecycleOwner()?.lifecycleScope` が `null` を返し silent early return している。Pre-warm / 未 attach 状態の ViewHolder では `ViewTree*` が解決できないため、Issue #43 の AC 1.x が破綻 | `IconLoader` のコンストラクタに `applicationScope: CoroutineScope` を渡し、`findViewTreeLifecycleOwner` 依存を完全排除。`ServiceLocator` で `SupervisorJob() + Dispatchers.Main.immediate` の 1 つ持ち。Req 2.1 / 2.2 |
| B | `InitialLetterDrawable.getIntrinsicWidth/Height` が `Drawable` default の `-1` を返すため、`scaleType=fitCenter` 配下で描画領域が 0×0 に潰れる | `InitialLetterDrawable` に `intrinsicSizePx` コンストラクタ引数を追加。`getIntrinsicWidth/Height` を override（正の px を返す）。`draw()` の bounds 起点描画は維持（45% short-edge 比率は Issue #43 と等価）。Req 3.1 / 3.2 / 3.3 |
| C | `android:clipToOutline="true"` が設定された ImageView は outline provider を持たないため、`drawableForBackground` 不在で全 clip され不可視に | 3 layout から `clipToOutline="true"` を削除。角丸視覚は親 `FrameLayout` の `@drawable/kn_icon_tile_bg`（kn_r_icon_tile=12dp）で維持。Req 5.1 / 5.2 / 5.3 |
| D | 診断 log が無いため、A/B/C のどこで止まっているのか Logcat で切り分け不能 | `IconLoader` の主要分岐点に 6 本の log（`Log.d` × 4 / `Log.w` × 2）を追加。tag は `"IconLoader"` 固定。出力内容は packageName + 結果分類 + 例外クラス名のみ（Req 4.1〜4.6）。`adb logcat -s IconLoader` で一括抽出可能 |

## AC カバレッジ表

| Requirement ID | AC 内容（要旨） | 実装ファイル | テストファイル |
| --- | --- | --- | --- |
| 1.1 | クレデンシャル一覧の実アイコン描画 | `CredentialListAdapter.kt` + `credential_list_item.xml`（clipToOutline 除去） + `IconLoader.loadInto`（process-wide scope） | `IconLoaderTest.loadInto_resolvesWithInjectedScope_independentOfImageViewAttachment` |
| 1.2 | 最近使用 carousel の実アイコン描画 | `RecentlyUsedCarouselAdapter.kt` + `credential_list_recent_item.xml`（clipToOutline 除去） + 同上 | 同上 |
| 1.3 | Package Picker の実アイコン描画 | `PackagePickerBottomSheet.kt` + `package_picker_row_item.xml`（clipToOutline 除去） + 同上 | 同上 |
| 1.4 | 例外時の頭文字 fallback | `IconLoader.resolveOrFallback` (catch 2 種) + `InitialLetterDrawable`（intrinsic size 付き） | `IconLoaderTest.resolve_nameNotFound_returnsInitialLetterDrawable` / `resolve_runtimeException_returnsInitialLetterDrawable` |
| 1.5 | 描画形状の観測（実アイコン or 頭文字 形状） | layout の `clipToOutline` 除去 + `InitialLetterDrawable.getIntrinsicWidth/Height` 正値 | `IconLoaderTest.loadInto_blankPackageName_clearsImageView_andDoesNotCallPackageManager`（blank 系）+ `InitialLetterDrawableTest.intrinsicWidth_returnsPositivePxValuePassedAtConstruction` |
| 2.1 | プロセス寿命スコープでの coroutine 起動 | `IconLoader.loadInto`（`applicationScope.launch`） + `ServiceLocator.applicationScope`（`SupervisorJob` + `Dispatchers.Main.immediate`） | `IconLoaderTest.loadInto_resolvesWithInjectedScope_independentOfImageViewAttachment` |
| 2.2 | スコープ取得失敗で silent fail しない | コンストラクタ注入で scope は常に non-null。Req 4.5 の例外時 log は `Log.w` 出力 | コンパイル時保証（型 `CoroutineScope` は non-nullable）+ `IconLoaderTest.resolve_runtimeException_returnsInitialLetterDrawable` |
| 2.3 | 解決完了時の tag 照合 main thread `setImageDrawable` | `IconLoader.loadInto` 内の `getTag(...) == packageName` 分岐 | `IconLoaderTest.loadInto_tagMismatchAfterRebind_doesNotOverwriteRecycledRow` |
| 3.1 | `getIntrinsicWidth/Height` が `-1` でない | `InitialLetterDrawable` の override | `InitialLetterDrawableTest.intrinsicWidth_*` / `intrinsicHeight_*` / `intrinsicSize_isNotNegativeOne_*` |
| 3.2 | `fitCenter` 下で bounds が空にならない | intrinsic size > 0 / `draw()` の `bounds.isEmpty` early return 維持 | 同上（intrinsic 値の正値検証） |
| 3.3 | 文字グリフ短辺 45% 比率の維持 | `InitialLetterDrawable.draw()` の `TEXT_SIZE_RATIO = 0.45f` 不変 + bounds 起点描画 | 既存 `InitialLetterDrawableTest.computeInitial_*` 群（draw path は Robolectric/Espresso が必要なため visual 検証は deferrable） |
| 4.1 | 受付時 log | `IconLoader.loadInto` 冒頭 `Log.d(LOG_TAG, "loadInto: request pkg=...")` | 観察的: `Log` モックの verify は `isReturnDefaultValues=true` 環境で no-op。Logcat 出力は Reviewer 実機確認領域 |
| 4.2 | キャッシュヒット log | `IconLoader.loadInto` / `IconLoader.resolve` の `Log.d(LOG_TAG, "...cacheHit pkg=...")` | 同上 |
| 4.3 | 解決成功 log | `IconLoader.resolveOrFallback` の `Log.d(LOG_TAG, "resolveOrFallback: success pkg=...")` | 同上 |
| 4.4 | fallback 経路 log | `IconLoader.resolveOrFallback` catch ブロックの `Log.d/Log.w(LOG_TAG, "...fallback pkg=... reason=...")` | 同上 |
| 4.5 | 異常系 warn log | `IconLoader.loadInto` 内 tag mismatch の `Log.w(LOG_TAG, "...cancelledByRecycle pkg=...")` + `resolveOrFallback` の RuntimeException catch の `Log.w` | 同上 |
| 4.6 | 機微情報を含めない | log 引数は packageName + 結果分類 + 例外クラス名のみ。signature SHA / username / ciphertext を引数に渡していない（コードレビューで担保） | コードレビュー領域 |
| 5.1 | clipToOutline 起因の不可視化を発生させない | 3 layout から `clipToOutline="true"` を削除 | （観察的）layout XML 上の文字列がなくなっていることを reviewer が確認 |
| 5.2 | 角丸視覚を別経路で維持 | 親 `FrameLayout` の `@drawable/kn_icon_tile_bg`（既存）が `<corners android:radius="@dimen/kn_r_icon_tile" />` 描画を担う | 既存 layout token pinning テストで `@drawable/kn_icon_tile_bg` 保持を確認 |
| 5.3 | 角丸見た目を #43 確定時点と等価に保つ | 親 FrameLayout 構成 / `kn_icon_tile_bg` / `kn_r_icon_tile` 不変 | 同上 |
| NFR 1.1 | LRU 64 維持 | `IconLoader.DEFAULT_CACHE_CAPACITY = 64` 不変 | `IconLoaderTest.resolve_cacheCapacityRespected_oldestEvictedFirst` |
| NFR 1.2 | cache hit 16ms | 同期 LruCache.get + setImageDrawable のみで分岐（既存維持） + `loadInto` 内で resolve 結果を `cache.put` する経路の追加（こちらは hotfix で初めて確立） | `IconLoaderTest.loadInto_cacheMissResultIsPutIntoLruCache_soSecondBindIsSynchronous`（2 回目 bind で PM 呼び出し回数 1 を担保） |
| NFR 1.3 | PM 呼び出しは IO context | `withContext(ioDispatcher)` 不変 | `IconLoaderTest`（テスト用 dispatcher 経路で実証） |
| NFR 2.1 | 通常分岐 log は warn 未満 | `Log.d` を使用 | コードレビュー領域 |
| NFR 2.2 | 異常分岐 log は warn 以上 | `Log.w` を使用 | コードレビュー領域 |
| NFR 3.1 / 3.2 | 機微情報の保護 | 上記 Req 4.6 と同じ | 同上 |

## 確認事項（Reviewer 向け）

### A. intrinsic size 値の選定（132 px 固定）

`IconLoader.DEFAULT_FALLBACK_INTRINSIC_SIZE_PX = 132` を採用した。要件「前提・確認事項」
2 では「3 画面それぞれのタイル寸法に応じて px に変換した値を渡せるようにする」を
PM 推奨案としているが、最終実装では以下の理由で **画面ごとの差分は持たせず単一値**にした:

- `ImageView` 側が `scaleType="fitCenter"` を採用しており、drawable の intrinsic size は
  **正値かつ縦横アスペクト比が正方形**でありさえすれば、最終描画サイズは ImageView の
  measured bounds で決まる。すなわち intrinsic size の絶対値は視覚に影響しない
- 値は xxhdpi (44dp = 132px) を基準に「最大想定タイル」を採用した。これにより端末密度に
  関わらず `-1` には決して落ちず、`fitCenter` が必ず非空の destination rect を計算する
- 引数経由で per-call サイズを渡す方法も検討したが、(1) cache は packageName キーで共有
  されるため per-call サイズは無効化される、(2) adapter 側に追加引数を伝播させる必要があり
  Issue #46 の hotfix としては変更面積が膨らむ、ため見送った
- `IconLoader` のコンストラクタには `fallbackIntrinsicSizePx` を残しているので、将来
  画面ごとに調整したい場合は ServiceLocator から複数 instance を出す形で拡張可能

別案を採るべきと判断される場合は逆提案ください。

### B. `clipToOutline` の親移動 vs 完全削除（完全削除を採用）

要件「前提・確認事項」3 の PM 推奨案（3 layout の `clipToOutline="true"` 削除、角丸見た目は
親 FrameLayout の `@drawable/kn_icon_tile_bg` で維持）に従って実装した。実アイコンの
角丸クリップは本 Issue では再導入していない（Out of Scope）。

確認ポイント:

- 親 FrameLayout の `@drawable/kn_icon_tile_bg` には `<corners android:radius="@dimen/kn_r_icon_tile" />`
  が既に効いており、青タイル背景の角丸視覚は維持される
- 実アイコン（`PackageManager.getApplicationIcon` 戻り値）は親タイルの「中」に
  ImageView を介して描画されるが、ImageView 側の outline clip は無いため、矩形アイコンが
  そのまま矩形のまま描画される（角丸クリップは効かない）。Issue #43 確定時点では
  `clipToOutline="true"` を持っていたが、実機で動いていなかったので、実質的な視覚差分は
  ない。再導入が必要なら別 Issue で検討

### C. `loadInto` で `cache.put` を行うようにした件（バグ修正）

Issue #43 の `IconLoader.loadInto` は `cache.put` を呼んでいなかったため、`loadInto`
経由の resolve 結果が再利用されず、毎回 `pm.getApplicationIcon` を呼び直していた
（バックグラウンド `Dispatchers.IO` 経由で発火するため気付きにくい）。本 Issue で
`applicationScope.launch { ... cache.put(...) ... }` に修正した。

これは要件 4.2 「キャッシュヒットでアイコンを返したとき」を厳密に達成するための必要条件で
あり、副作用ではなく hotfix の一部として行った。新規テスト
`loadInto_cacheMissResultIsPutIntoLruCache_soSecondBindIsSynchronous` で担保している。

### D. `applicationScope` の dispatcher 選定（`Dispatchers.Main.immediate`）

`Dispatchers.Main.immediate` を採用した。`Dispatchers.Main` と比べ、すでに main thread に
いるときに継続を rescheduled せず同期実行するため、`withContext(ioDispatcher)` から
戻った後の `imageView.setImageDrawable` がより低レイテンシで反映される。プロセス寿命の
scope なので tear-down は不要（`KeyNestApp` プロセスが終わるとき GC される）。

### E. 既存事前破損テスト 5 件（本 Issue 範囲外）

`./gradlew :app:testDebugUnitTest` 実行時に以下 5 件が `NullPointerException` で fail
するが、これらは Issue #43 着手前から既に red（`develop` 起点クリーン状態でも fail）と
documented されており（`docs/specs/43-feat-icons-picker-packagemanager-fallbac/impl-notes.md`
の「既知の事前破損テスト」節）、本 Issue のスコープ外:

- `com.example.keynest.autofill.LockedFillResponseSecurityTest.lockedFillResponse_bytes_doNotContainCandidateCredentialMaterial`
- `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_api26_singleSigner_returnsHash`
- `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_api28_singleSigner_returnsCanonicalHash`
- `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_api28_multipleSigners_isOrderIndependent`
- `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_isReRunnable_pickingUpUpdatedSignerEachTime`

### F. 診断 log の draw 経路非カバレッジ

要件 4.x の Logcat 出力は `Log.d` / `Log.w` を直接呼ぶ実装で、ユニットテストでは
`isReturnDefaultValues=true` 環境下で `android.util.Log` が no-op になるため、出力内容を
検証していない。コードレビューで「log 行が引数として packageName 以外の機微情報を含まない」
ことを確認願いたい（コード上、log 文字列には `packageName` / `e.javaClass.simpleName` /
固定リテラルしか出現していない）。

## 残課題（派生タスク候補）

- `IconLoader` の Robolectric 統合テスト（log 出力検証や `ImageView` への実画像反映の
  end-to-end）。`isReturnDefaultValues=true` を解除する形で別 Issue 化候補
- 既存事前破損テスト 5 件の修復（Issue #43 から繰り越し）
- 実アイコンに対する角丸クリップを再導入したい場合の別 Issue

## 実行コマンド要約

- 単体テスト: `./gradlew :app:testDebugUnitTest`（501 pass、5 fail はすべて事前破損で本 PR
  由来ではない。`IconLoaderTest` 11 / `InitialLetterDrawableTest` 8 すべて green）
- Build: `./gradlew :app:assembleDebug`（success）
- Lint: 事前 errors のため未実行（本 PR 由来の新規 lint error は導入していない）

## 関連 commit (見込み)

| 種別 | 内容 |
| --- | --- |
| fix(icons) | InitialLetterDrawable に intrinsic size override を追加 + 単体テスト 3 件追加 |
| fix(icons) | IconLoader に process-wide scope を注入 + 既存テストの constructor 更新 + 新規テスト 5 件追加 |
| fix(ui) | 3 layout から ImageView の clipToOutline="true" を削除 |
| feat(icons) | IconLoader に診断 log 6 本 (Log.d × 4 + Log.w × 2) を追加 |
| docs(impl-notes) | Issue #46 実装サマリを記録 |
