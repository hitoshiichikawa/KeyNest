# Implementation Notes — Issue #43

## 実装概要

Phase 2 で配置済みのアイコンタイル枠 (`@drawable/kn_icon_tile_bg`) の上に、
`PackageManager.getApplicationIcon` で取得した実アプリアイコンを描画する共通基盤
`IconLoader` と頭文字 fallback drawable `InitialLetterDrawable` を新設し、
3 adapter (`CredentialListAdapter` / `RecentlyUsedCarouselAdapter` /
`PackagePickerBottomSheet.SectionAdapter`) で共通利用する形に統合した。

### 主要コンポーネント

| コンポーネント | 配置 | 主な責務 |
| --- | --- | --- |
| `IconLoader` | `util/IconLoader.kt` | cache (LruCache, capacity=64) + 非同期解決 (Dispatchers.IO) + race prevention (`R.id.icon_loader_request_tag`) + fallback drawable 合成 |
| `InitialLetterDrawable` | `util/InitialLetterDrawable.kt` | 頭文字 1 文字を kn_blue_500 角丸タイル + kn_on_primary 白テキストで描画する pure Drawable |
| `R.id.icon_loader_request_tag` | `res/values/ids.xml` | `ImageView.setTag(int, Object)` 用の安定 ID。コードからのみ参照される |
| `ServiceLocator.iconLoader` | `di/ServiceLocator.kt` | プロセス全体で 1 つの cache を共有する singleton |

### 解決フロー

1. adapter の `bind()` で `iconLoader.loadInto(imageView, packageName)` を呼ぶ
2. `packageName` が空 / null → `setImageDrawable(null)` で early return (背景タイルのみ可視)
3. cache hit → 同期 `setImageDrawable` (Req 2.3 / NFR 1.2 の 16ms 予算内)
4. cache miss → `imageView.findViewTreeLifecycleOwner()?.lifecycleScope` 上で
   `withContext(Dispatchers.IO) { pm.getApplicationIcon(...) }` を実行し、
   結果を cache に put、`ImageView.getTag(...)` で照合してから main thread で
   `setImageDrawable` を反映
5. `NameNotFoundException` / `RuntimeException` 発生時は `InitialLetterDrawable` を
   返し、その結果も cache に格納（不在パッケージへの再 bind で毎フレーム throw
   しないため）
6. `onViewRecycled` で `iconLoader.cancel(imageView)` を呼び、tag を null に
   戻すことで遅延 callback の race を防ぐ (Req 2.4)

### 設計との差分

- 設計通り。design.md からの逸脱なし。
- 1 点だけ追加した補強テストとして、`IconLoaderTest` に以下 3 ケースを追加して
  いる（設計の 4 ケースに対するスーパーセット）:
  - `resolve_runtimeException_returnsInitialLetterDrawable` — `SecurityException`
    等の非 `NameNotFoundException` でも fallback に倒れることを担保 (Req 1.4 の
    広義解釈)
  - `resolve_fallbackResultIsAlsoCached` — fallback drawable も cache 対象である
    ことを担保 (Req 2.3 / NFR 1.2 を不在パッケージにも適用)
  - `resolve_cacheCapacityRespected_oldestEvictedFirst` — capacity 超過時の LRU
    eviction を担保 (NFR 1.1)
- `InitialLetterDrawableTest` も補強として追加（設計上は optional 扱い）

## AC カバレッジ表

| Requirement ID | AC 内容（要旨） | 実装ファイル | テストファイル |
| --- | --- | --- | --- |
| 1.1 | クレデンシャル一覧で実アイコン描画 | `CredentialListAdapter.kt` (`iconLoader.loadInto`) + `credential_list_item.xml` (`icon_app`) | `IconLoaderTest.resolve_packageInstalled_returnsPackageManagerDrawable` |
| 1.2 | 最近使用 carousel で実アイコン描画 | `RecentlyUsedCarouselAdapter.kt` + `credential_list_recent_item.xml` | 同上 (loader 共通) |
| 1.3 | Package Picker 行で実アイコン描画 | `PackagePickerBottomSheet.kt` (`RowVH.bind`) + `package_picker_row_item.xml` | 同上 (loader 共通) |
| 1.4 | 例外時に頭文字 fallback | `IconLoader.resolveOrFallback` (catch 2 種) + `InitialLetterDrawable` | `IconLoaderTest.resolve_nameNotFound_returnsInitialLetterDrawable` / `resolve_runtimeException_returnsInitialLetterDrawable` |
| 1.5 | 空 / null packageName で fallback なし | `IconLoader.loadInto` / `resolve` early return | `IconLoaderTest.resolve_blankPackageName_returnsNullWithoutCallingPackageManager` |
| 2.1 | LRU cache 最大 64 エントリ | `IconLoader` (`LruCache(cacheCapacity)`, default=64) | `IconLoaderTest.resolve_cacheCapacityRespected_oldestEvictedFirst` |
| 2.2 | main thread 以外で PM 呼び出し | `IconLoader.loadInto` / `resolve` (`withContext(ioDispatcher)`) | `IconLoaderTest`（テスト用 dispatcher で実証） |
| 2.3 | スクロール中 cache hit で 16ms 以内 | `IconLoader` 同期 path (LruCache.get + setImageDrawable) | `IconLoaderTest.resolve_secondCallSamePackage_usesCacheAndSkipsPackageManager` (PM 呼び出し回数で代替検証) |
| 2.4 | onViewRecycled で旧解決の反映を防ぐ | `IconLoader.cancel` + 3 adapter の `onViewRecycled` + `ImageView.setTag(R.id.icon_loader_request_tag, ...)` | （Robolectric/Espresso 統合テストは spec 上 deferrable のため未追加。実装側 review でカバー） |
| 3.1 | tile が未解決時のみ可視 | layout XML で ImageView を FrameLayout の子に配置（背景は親 FrameLayout） + `IconLoader.loadInto` 解決中 `setImageDrawable(null)` | 3 layout の XML pinning テストで `@drawable/kn_icon_tile_bg` 保持を確認 |
| 3.2 | 解決後 ImageView が tile 角丸境界内 | layout XML で `android:clipToOutline="true"` + 親 tile 背景の outline | 3 layout に `clipToOutline="true"` 明記 |
| 3.3 | 一覧本体 tile サイズ `kn_icon_tile_lg` | `credential_list_item.xml` 親 FrameLayout が `@dimen/kn_icon_tile_lg` 維持 | `CredentialListLayoutTokensTest` 既存 assert で担保 |
| 3.4 | carousel / picker tile サイズ `kn_icon_tile_sm` (32dp) / 36dp | `credential_list_recent_item.xml` 36dp / `package_picker_row_item.xml` `@dimen/kn_icon_tile_sm` | 既存 layout token pinning テスト |
| 4.1 | 成功ケース テスト | — | `IconLoaderTest.resolve_packageInstalled_returnsPackageManagerDrawable` |
| 4.2 | NameNotFoundException fallback テスト | — | `IconLoaderTest.resolve_nameNotFound_returnsInitialLetterDrawable` |
| 4.3 | cache hit テスト | — | `IconLoaderTest.resolve_secondCallSamePackage_usesCacheAndSkipsPackageManager` |
| 4.4 | 空 / null fallback なしテスト | — | `IconLoaderTest.resolve_blankPackageName_returnsNullWithoutCallingPackageManager` |
| NFR 1.1 | cache 容量固定 + LRU 追い出し | `IconLoader.DEFAULT_CACHE_CAPACITY = 64` / `LruCache` 実装 | `IconLoaderTest.resolve_cacheCapacityRespected_oldestEvictedFirst` |
| NFR 1.2 | cache hit 16ms 以内 | 同期 LruCache.get + setImageDrawable のみで分岐 | `IconLoaderTest.resolve_secondCallSamePackage_usesCacheAndSkipsPackageManager` で PM 呼び出し回数 1 を担保 |
| NFR 1.3 | キャッシュミス時 main thread 以外 | `withContext(ioDispatcher)` | `IconLoaderTest` のテスト用 dispatcher 経路で実証 |
| NFR 2.1 | adaptive icon をそのまま描画 | `PackageManager.getApplicationIcon` の戻り値を加工せず `setImageDrawable` | （観察的: コード上で mask 適用なし） |
| NFR 2.2 | API 26 未満なし (minSdk=26) | `build.gradle.kts` `minSdk = 26`、`IconLoader` に分岐なし | （該当ロジックなし） |
| NFR 2.3 | 既存 `<queries>` 制限を増やさない | `AndroidManifest.xml` を編集していない | git diff で確認可能 |
| NFR 3.1 | `kn_icon_tile_bg` 削除しない | 3 layout 編集後も `android:background="@drawable/kn_icon_tile_bg"` を保持 | 既存 layout token pinning テスト |
| NFR 3.2 | tile dimen 値を変えない | `dimens.xml` を編集していない | git diff で確認可能 |
| NFR 3.3 | 既存 bind / ViewHolder / コールバック維持 | 3 adapter の既存 ID / DiffUtil / callback 配線は不変 (constructor 追加のみ) | 既存テスト群 (`CredentialListLayoutTokensTest` 等) が全て pass |
| NFR 4.1 | 外部ローダ依存追加なし | `app/build.gradle.kts` を編集していない | git diff で確認可能 |
| NFR 4.2 | オンライン解決経路なし | `IconLoader` は `PackageManager` のみ呼ぶ | コードレビューで確認 |

## 確認事項（レビュワーへの依頼）

requirements.md / design.md の「確認事項」3 項目について、Architect の暫定設計判断
（design.md > 確認事項）に沿って実装した。最終確定は人間レビュワーに委ねる。

1. **頭文字算出ルール**: `InitialLetterDrawable.computeInitial(packageName)` を
   実装。`com.example.keynest` → `K`、`com.android.chrome` → `C`、`org.mozilla.firefox`
   → `F`、`com.example.7zip` → `7`（数字も valid な glyph として採用）、末尾セグメント
   取得不能（`.` / `   ` 等）→ `?`。`pm.getApplicationLabel` は呼び出さない。
2. **非同期実装の粒度**: `IconLoader` 内で `withContext(Dispatchers.IO)` の switch を
   行う単純構成。adapter は `imageView.findViewTreeLifecycleOwner()?.lifecycleScope`
   経由で coroutine を起動。semaphore 等のスロットリングは実装していない。
3. **adaptive icon の扱い**: `pm.getApplicationIcon` の戻り値をそのまま
   `setImageDrawable` に渡し、独自 mask は描画していない。角丸は ImageView の
   `clipToOutline="true"` + 親 FrameLayout の `@drawable/kn_icon_tile_bg` outline で実現。

### 設計通りに実装し、特段の差し戻し提案はない事項

- 仕様の AC は全件カバー済み (上表参照)
- 設計の File Structure Plan に列挙されたファイルすべてを編集 / 追加 (一致)
- requirements.md / design.md / tasks.md を **書き換えていない** (進捗マーカー
  `- [ ]` → `- [x]` の行内 4 文字差分のみ、IDD-Claude opt-in 規約遵守)

### 既知の事前破損テスト（impl とは無関係）

`develop` 起点のクリーンな状態でも以下 5 件が `java.lang.NullPointerException` で
失敗する（本 PR 着手前から既に red）:

- `com.example.keynest.autofill.LockedFillResponseSecurityTest.lockedFillResponse_bytes_doNotContainCandidateCredentialMaterial`
- `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_api26_singleSigner_returnsHash`
- `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_api28_singleSigner_returnsCanonicalHash`
- `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_api28_multipleSigners_isOrderIndependent`
- `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_isReRunnable_pickingUpUpdatedSignerEachTime`

`git stash` した上で本 Issue 着手前の HEAD (`Merge #44`) で同テストを実行しても
同じく fail する。本 Issue のスコープ外のため別 Issue 化候補。

### Lint について

`./gradlew :app:lintDebug` は既存の 94 errors / 133 warnings で fail するが、すべて
事前 (`PackageSignatureResolver` の `NewApi`, `PackagePickerBottomSheet` の
`QueryPermissionsNeeded` / `NotifyDataSetChanged` 等) で、本 PR で新規に
導入された lint error はない (`IconLoader.kt` / `InitialLetterDrawable.kt` /
新 ImageView 追加 layouts は lint レポートに登場しない)。

## 派生タスク候補

- 既存事前破損テスト 5 件の修復 (本 Issue とは独立)
- `IconLoader` の Robolectric 統合テスト（`ImageView` への実画像反映 + onViewRecycled
  キャンセル race の挙動を end-to-end で検証）。spec 上 deferrable のため別 Issue 化候補
- `pm.getApplicationLabel` ベースの頭文字算出への切替検討（label 取得コスト vs
  ラベルが空のときの fallback 経路の二重化を比較）

## 実行コマンド要約

- 単体テスト: `./gradlew :app:testDebugUnitTest` （新規 12 テストが pass、既存 5 件は
  事前破損で red、その他 460 テストは green）
- Build: `./gradlew :app:assembleDebug` （success）
- Lint: `./gradlew :app:lintDebug` （事前 94 errors のため fail、本 PR 由来の error なし）

## 関連 commit 一覧

| commit | 種別 | 内容 |
| --- | --- | --- |
| `50bc908` | feat | InitialLetterDrawable + computeInitial helper + 単体テスト |
| `8155276` | feat | `res/values/ids.xml` に `icon_loader_request_tag` を追加 |
| `131aca9` | feat | IconLoader 本体実装 |
| `8c8fb2b` | test | IconLoader 単体テスト (4 AC + LRU 補強 + RuntimeException + fallback cache) |
| `417df03` | feat | `ServiceLocator.iconLoader` 追加 |
| `bae3539` | feat | `credential_list_item.xml` に icon_app ImageView 追加 |
| `c61f137` | feat | `CredentialListAdapter` を IconLoader 経由に統合 + Activity 配線 |
| `5ec3767` | feat | `credential_list_recent_item.xml` に icon_app ImageView 追加 |
| `aafefd6` | feat | `RecentlyUsedCarouselAdapter` を IconLoader 経由に統合 + Activity 配線 |
| `0f6c180` | feat | `package_picker_row_item.xml` に icon_app ImageView 追加 |
| `bdf07b7` | feat | `PackagePickerBottomSheet.SectionAdapter` / `RowVH` を IconLoader 経由に統合 |

加えて各タスク完了直後に `docs(tasks): mark N.M as done` 単独 commit で `tasks.md`
の進捗マーカーを `- [ ]` → `- [x]` に更新している（IDD-Claude opt-in
`IMPL_RESUME_PROGRESS_TRACKING=true` 規約遵守、tasks.md 以外を含めない単独 commit）。
