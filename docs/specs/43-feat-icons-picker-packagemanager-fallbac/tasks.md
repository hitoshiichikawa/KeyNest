# Implementation Plan

- [x] 1. IconLoader / InitialLetterDrawable / 共通リソースの追加
- [x] 1.1 InitialLetterDrawable + 頭文字算出 helper の実装
  - `app/src/main/java/com/example/keynest/util/InitialLetterDrawable.kt` を新規追加
  - `companion object fun computeInitial(packageName: String): String` を提供
    （末尾セグメント先頭 1 文字を大文字化、取得不能時は `"?"`）
  - `Drawable.draw(Canvas)` で `@color/kn_blue_500` の角丸 (`@dimen/kn_r_icon_tile`) 矩形 +
    白色 (`@color/kn_on_primary`) テキストを描画
  - `_Requirements: 1.4_`
- [x] 1.2 IconLoader 本体（cache / 非同期 / race prevention / fallback 統合）の実装
  - `app/src/main/java/com/example/keynest/util/IconLoader.kt` を新規追加
  - `LruCache<String, Drawable>(64)` を内部に保持（NFR 1.1）
  - `loadInto(ImageView, String?)` で cache hit は同期 setImageDrawable、miss は
    `findViewTreeLifecycleOwner()?.lifecycleScope.launch { withContext(Dispatchers.IO) { ... } }` で解決
  - `setTag(R.id.icon_loader_request_tag, packageName)` + 非同期完了時の tag 一致照合で
    race prevention を担保（Req 2.4）
  - 空 / null packageName は `setImageDrawable(null)` で early return（Req 1.5）
  - `NameNotFoundException` / その他 `RuntimeException` を catch して
    `InitialLetterDrawable` を返す（Req 1.4）
  - `cancel(ImageView)` で tag を null に戻す API を公開
  - `suspend fun resolve(packageName: String?)` を unit test 用に切り出す
  - 外部画像ローダ (Coil / Glide) / オンライン解決経路を呼ばない（NFR 4.1 / 4.2）
  - AndroidManifest の `<queries>` を変更しない (NFR 2.3)
  - minSdk=26 のため API 26 未満分岐は実装しない (NFR 2.2)
  - `_Requirements: 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, NFR 1.1, NFR 1.2, NFR 1.3, NFR 2.1, NFR 2.2, NFR 2.3, NFR 4.1, NFR 4.2_`
- [x] 1.3 IconLoader request tag ID リソースを追加
  - `app/src/main/res/values/ids.xml` を新規追加し
    `<item name="icon_loader_request_tag" type="id"/>` を宣言
  - `_Requirements: 2.4_`
- [x] 1.4 IconLoader 単体テスト 4 ケースを追加
  - `app/src/test/java/com/example/keynest/util/IconLoaderTest.kt` を新規追加
  - 成功 / NameNotFoundException fallback / cache hit / 空・null 入力の 4 ケース
  - mockk で `PackageManager` を stub、`PackageSignatureResolverTest.kt` のパターンに準拠
  - `kotlinx.coroutines.test.runTest` でディスパッチャ制御
  - (任意) `InitialLetterDrawableTest.kt` で `computeInitial` の pure helper を補強
  - `_Requirements: 4.1, 4.2, 4.3, 4.4_`
- [x] 1.5 ServiceLocator に iconLoader プロパティを追加
  - `app/src/main/java/com/example/keynest/di/ServiceLocator.kt` を編集
  - `val iconLoader: IconLoader by lazy { IconLoader(requireAppContext().packageManager, requireAppContext().resources) }`
  - 既存 `appInfoProvider` / `vaultStorageMeasurer` 等のセクションに並べる
  - `_Requirements: NFR 3.3_`

- [x] 2. クレデンシャル一覧本体への統合 (P)
- [x] 2.1 `credential_list_item.xml` にアイコン ImageView を追加 (P)
  - 既存 `<FrameLayout android:background="@drawable/kn_icon_tile_bg" .../>`（58-61 行目）
    の子として `<ImageView android:id="@+id/icon_app" android:layout_width="match_parent"
    android:layout_height="match_parent" android:scaleType="fitCenter"
    android:clipToOutline="true" android:contentDescription="@null"/>` を追加
  - 親 FrameLayout の `kn_icon_tile_lg` (44dp) は不変 (Req 3.3 / NFR 3.2)
  - `@drawable/kn_icon_tile_bg` を残す (Req 3.1 / NFR 3.1)
  - `_Requirements: 1.1, 3.1, 3.2, 3.3, NFR 3.1, NFR 3.2_`
  - `_Boundary: CredentialListAdapter_`
- [x] 2.2 `CredentialListAdapter` を IconLoader 経由のバインドに更新 (P)
  - constructor に `iconLoader: IconLoader` を追加（既存 3 callback の末尾）
  - `ViewHolder.bind()` 末尾で `iconLoader.loadInto(binding.iconApp, item.packageName)` を呼ぶ
  - `onViewRecycled(holder)` を override して `iconLoader.cancel(holder.binding.iconApp)`
  - 既存 ID / DiffUtil / callback 群は不変 (NFR 3.3)
  - `CredentialListActivity.setUpMainList()` の adapter コンストラクタ呼び出しに
    `ServiceLocator.iconLoader` を渡す
  - `_Requirements: 1.1, 2.4, NFR 3.3_`
  - `_Boundary: CredentialListAdapter_`
  - `_Depends: 1.2, 1.3, 1.5, 2.1_`

- [x] 3. 最近使用 carousel への統合 (P)
- [x] 3.1 `credential_list_recent_item.xml` にアイコン ImageView を追加 (P)
  - 既存 FrameLayout（45-50 行目、36dp 固定）の子として `<ImageView android:id="@+id/icon_app"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:scaleType="fitCenter" android:clipToOutline="true"
    android:contentDescription="@null"/>` を追加
  - 親寸法 36dp と `@drawable/kn_icon_tile_bg` は不変 (Req 3.4 / NFR 3.1)
  - `_Requirements: 1.2, 3.1, 3.2, 3.4, NFR 3.1_`
  - `_Boundary: RecentlyUsedCarouselAdapter_`
- [x] 3.2 `RecentlyUsedCarouselAdapter` を IconLoader 経由に更新 (P)
  - constructor に `iconLoader: IconLoader` を追加
  - `ViewHolder.bind()` 末尾で `iconLoader.loadInto(binding.iconApp, item.packageName)`
  - `onViewRecycled(holder)` を override して `iconLoader.cancel(...)`
  - `CredentialListActivity.setUpRecentCarousel()` の adapter コンストラクタに
    `ServiceLocator.iconLoader` を渡す
  - 既存 ID / DiffUtil / callback は不変 (NFR 3.3)
  - `_Requirements: 1.2, 2.4, NFR 3.3_`
  - `_Boundary: RecentlyUsedCarouselAdapter_`
  - `_Depends: 1.2, 1.3, 1.5, 3.1_`

- [x] 4. Package Picker への統合 (P)
- [x] 4.1 `package_picker_row_item.xml` にアイコン ImageView を追加 (P)
  - 既存 FrameLayout（44-48 行目、`kn_icon_tile_sm`）の子として `<ImageView
    android:id="@+id/icon_app" android:layout_width="match_parent"
    android:layout_height="match_parent" android:scaleType="fitCenter"
    android:clipToOutline="true" android:contentDescription="@null"/>` を追加
  - 親寸法 `kn_icon_tile_sm` (32dp) と `@drawable/kn_icon_tile_bg` は不変
    (Req 3.4 / NFR 3.1 / NFR 3.2)
  - `_Requirements: 1.3, 3.1, 3.2, 3.4, NFR 3.1, NFR 3.2_`
  - `_Boundary: PackagePickerBottomSheet.SectionAdapter_`
- [x] 4.2 `PackagePickerBottomSheet.SectionAdapter` / `RowVH` を IconLoader 経由に更新 (P)
  - `SectionAdapter` constructor に `iconLoader: IconLoader` を追加
    （既存 `onClick: (String) -> Unit` の隣）
  - `RowVH.bind()` で `iconLoader.loadInto(binding.iconApp, item.app.packageName)`
  - `SectionAdapter.onViewRecycled(holder)` を override し、`holder is RowVH` の時に
    `iconLoader.cancel(...)` を呼ぶ
  - `PackagePickerBottomSheet.onViewCreated` で `SectionAdapter(::onRowPicked,
    ServiceLocator.iconLoader)` のように singleton を注入
  - 既存 `show()` signature / 既存 ID (`text_app_label` / `text_app_package`) は不変
    (NFR 3.3)
  - `_Requirements: 1.3, 2.4, NFR 3.3_`
  - `_Boundary: PackagePickerBottomSheet.SectionAdapter_`
  - `_Depends: 1.2, 1.3, 1.5, 4.1_`
