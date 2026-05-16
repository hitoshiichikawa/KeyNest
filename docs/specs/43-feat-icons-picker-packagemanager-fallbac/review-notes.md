# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-16T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-43-impl-feat-icons-picker-packagemanager-fallbac
- HEAD commit: 8ef8e5e513d7ecc3c6ebe5e28aa4a4f27eec8246
- Compared to: develop..HEAD

`CLAUDE.md` の `## Feature Flag Protocol` 採否は `opt-out` のため、本レビューは
通常の 3 カテゴリ判定（AC 未カバー / missing test / boundary 逸脱）で実施した。

## Verified Requirements

- 1.1 — `CredentialListAdapter.bind()` 末尾で
  `iconLoader.loadInto(binding.iconApp, item.packageName)` を呼び、
  `credential_list_item.xml` の親 FrameLayout 子に `@+id/icon_app` ImageView を追加
- 1.2 — `RecentlyUsedCarouselAdapter.bind()` 末尾で同 `loadInto` を呼び、
  `credential_list_recent_item.xml` に同様の `@+id/icon_app` を追加
- 1.3 — `PackagePickerBottomSheet.RowVH.bind()` で同 `loadInto` を呼び、
  `package_picker_row_item.xml` に `@+id/icon_app` を追加。`SectionAdapter`
  constructor に `iconLoader` を注入
- 1.4 — `IconLoader.resolveOrFallback` で `NameNotFoundException` と
  `RuntimeException` を catch し `InitialLetterDrawable` を返す。
  `IconLoaderTest.resolve_nameNotFound_returnsInitialLetterDrawable` /
  `resolve_runtimeException_returnsInitialLetterDrawable` で網羅
- 1.5 — `IconLoader.loadInto` 冒頭で `packageName.isNullOrBlank()` のとき
  `setImageDrawable(null)` で early return（tag も null 化）。
  `IconLoaderTest.resolve_blankPackageName_returnsNullWithoutCallingPackageManager`
  で空 / 空白 / null の 3 ケースを検証
- 2.1 — `IconLoader.cache: LruCache<String, Drawable>(cacheCapacity)`、
  `DEFAULT_CACHE_CAPACITY = 64`。
  `IconLoaderTest.resolve_cacheCapacityRespected_oldestEvictedFirst` で LRU 動作確認
- 2.2 — `IconLoader.loadInto` / `resolve` 内で `withContext(ioDispatcher)` を経由して
  `pm.getApplicationIcon` を呼ぶ。デフォルト dispatcher は `Dispatchers.IO`
- 2.3 — cache hit 経路は `cache.get(...)` + 同期 `setImageDrawable` のみ。
  `IconLoaderTest.resolve_secondCallSamePackage_usesCacheAndSkipsPackageManager` で
  PM 呼び出しが 1 回に抑制されることを検証
- 2.4 — `ImageView.setTag(R.id.icon_loader_request_tag, packageName)` で照合 ID を
  打刻し、非同期完了時の `getTag` 一致時のみ `setImageDrawable` 反映。
  3 adapter (`CredentialListAdapter` / `RecentlyUsedCarouselAdapter` /
  `PackagePickerBottomSheet.SectionAdapter`) すべてで `onViewRecycled` を override し
  `iconLoader.cancel(...)` を呼ぶ
- 3.1 — 3 layout の親 FrameLayout で `android:background="@drawable/kn_icon_tile_bg"`
  を維持し、子 ImageView を `setImageDrawable(null)` で透過にする間は tile bg が見える
- 3.2 — 子 ImageView に `android:clipToOutline="true"` と
  `android:scaleType="fitCenter"` を指定。親 tile bg の outline で角丸 clip
- 3.3 — `credential_list_item.xml` の親 FrameLayout 寸法 `@dimen/kn_icon_tile_lg` を維持
- 3.4 — `credential_list_recent_item.xml` の 36dp / `package_picker_row_item.xml` の
  `@dimen/kn_icon_tile_sm` を維持
- 4.1 — `IconLoaderTest.resolve_packageInstalled_returnsPackageManagerDrawable` で
  `assertThat(result).isSameInstanceAs(stub)` と PM 呼び出し回数 1 を検証
- 4.2 — `IconLoaderTest.resolve_nameNotFound_returnsInitialLetterDrawable` で
  `NameNotFoundException` 時に `InitialLetterDrawable` インスタンスを返すことを検証
- 4.3 — `IconLoaderTest.resolve_secondCallSamePackage_usesCacheAndSkipsPackageManager`
  で 2 回目以降が cache から返ることを `verify(exactly = 1)` で担保
- 4.4 — `IconLoaderTest.resolve_blankPackageName_returnsNullWithoutCallingPackageManager`
  で空 / 空白 / null が `null` を返し PM が呼ばれないことを検証
- NFR 1.1 — `DEFAULT_CACHE_CAPACITY = 64` 固定。
  `resolve_cacheCapacityRespected_oldestEvictedFirst` でキャパ超過時の LRU eviction を検証
- NFR 1.2 — cache hit 経路は同期のみ（`LruCache.get` + `setImageDrawable`）。
  PM 呼び出し回数のテストで再解決が発生しないことを担保
- NFR 1.3 — `withContext(ioDispatcher)` で main 以外に switch
- NFR 2.1 — `pm.getApplicationIcon` の戻り値をそのまま `setImageDrawable`
  に渡し、独自 mask 描画なし
- NFR 2.2 — `IconLoader` 内に API 26 未満分岐なし（minSdk=26）
- NFR 2.3 — `AndroidManifest.xml` を編集していない（差分なし）
- NFR 3.1 — 3 layout とも `@drawable/kn_icon_tile_bg` の背景指定を保持
- NFR 3.2 — `dimens.xml` を編集していない（差分なし）
- NFR 3.3 — 3 adapter で既存 ID / DiffUtil / コールバック群は変更なし。
  constructor 引数追加と `iconAppView` getter / `onViewRecycled` 追加のみ
- NFR 4.1 — `app/build.gradle.kts` を編集していない（外部ローダ依存追加なし）
- NFR 4.2 — `IconLoader` は `PackageManager` のみを呼ぶ。HTTP / favicon 取得経路なし

## Boundary 確認

`tasks.md` の `_Boundary:_` で許可されたコンポーネントの範囲内に変更が収まっている:

- `IconLoader` / `InitialLetterDrawable` (util レイヤ新規追加)
- `R.id.icon_loader_request_tag` (`res/values/ids.xml` 新規追加)
- `ServiceLocator.iconLoader` (di 編集)
- `CredentialListAdapter` / `CredentialListActivity` (一覧本体)
- `RecentlyUsedCarouselAdapter` (carousel)
- `PackagePickerBottomSheet.SectionAdapter` / `RowVH` (Picker)
- 対象 3 layout XML
- `IconLoaderTest` / `InitialLetterDrawableTest` (unit test)

`requirements.md` / `design.md` の書き換えなし。`tasks.md` は進捗マーカー
(`- [ ]` → `- [x]`) の 4 文字差分のみで、本文 / `_Requirements:_` /
`_Boundary:_` / `_Depends:_` / 順序の改変なし。`impl-notes.md` は新規追加で、
`IMPL_RESUME_PROGRESS_TRACKING=true` の opt-in 規約に整合。

## Findings

なし

## Summary

requirements.md の全 numeric ID (1.1–1.5 / 2.1–2.4 / 3.1–3.4 / 4.1–4.4 /
NFR 1.1–1.3 / NFR 2.1–2.3 / NFR 3.1–3.3 / NFR 4.1–4.2) について、対応する
実装または単体テストが本ブランチ差分または既存コードのいずれかに存在することを確認した。
`tasks.md` の `_Boundary:_` 違反なし、AC 4 件の単体テストは追加済み (補強 3 ケースも有)。
spec ファイル (requirements.md / design.md / tasks.md 本文) の書き換えはなく、
impl-resume の opt-in 進捗マーカー更新規約にも整合している。

RESULT: approve
