# Requirements Document

## Introduction

Issue #46 の hotfix によりクレデンシャル一覧 / 最近使用 carousel / Package Picker の 3 画面で
アプリアイコンが描画されるようになった一方、実機検証では `AdaptiveIconDrawable` に対する
システムランチャー側の円形マスクと、親 `FrameLayout` に設定された `@drawable/kn_icon_tile_bg`
（12dp 角丸矩形 / `kn_blue_500` fill）の **境界差分**領域に青色が透けて見える視覚不具合が
確認されている。特にアイコンの四隅で青色が露出するため、保存済み credential / 候補アプリの
視覚的識別性が損なわれている。

本 Issue (#51) は、Issue #46 で確定済みの 3 画面のアイコン描画挙動（`IconLoader` による
実アイコン解決 + `InitialLetterDrawable` による fallback）を維持しつつ、四隅から青が透ける
現象を解消するための背景属性削除タスクである。Issue #43 / #46 で確定済みの NFR
（LRU キャッシュ容量 64、`Dispatchers.IO` 相当での解決、`InitialLetterDrawable` の視覚仕様）は
変更しない。

## Scope

### 対象ファイル

| 区分 | 対象 |
| --- | --- |
| Layout | `app/src/main/res/layout/credential_list_item.xml` |
| Layout | `app/src/main/res/layout/credential_list_recent_item.xml` |
| Layout | `app/src/main/res/layout/package_picker_row_item.xml` |
| Kotlin | `app/src/main/java/com/example/keynest/util/IconLoader.kt`（必要に応じて） |

### 対象画面

1. クレデンシャル一覧本体（`CredentialListAdapter` 経由）
2. 最近使用 carousel（`RecentlyUsedCarouselAdapter` 経由）
3. Package Picker 行（`PackagePickerBottomSheet` 内 `RecyclerView.Adapter` 経由）

## Requirements

### Requirement 1: 親 FrameLayout からの background 削除

**Objective:** As a KeyNest エンドユーザー, I want 3 画面のアイコンタイル親 `FrameLayout` から
`kn_icon_tile_bg` 背景属性が除去されていること, so that AdaptiveIconDrawable の円形マスクと
角丸矩形タイル背景の境界差分から青色が透けて見える現象が発生しなくなる

#### Acceptance Criteria

1. The `credential_list_item.xml` のアイコンタイル親 `FrameLayout` shall `android:background="@drawable/kn_icon_tile_bg"` 属性を保持しない状態にする
2. The `credential_list_recent_item.xml` のアイコンタイル親 `FrameLayout` shall `android:background="@drawable/kn_icon_tile_bg"` 属性を保持しない状態にする
3. The `package_picker_row_item.xml` のアイコンタイル親 `FrameLayout` shall `android:background="@drawable/kn_icon_tile_bg"` 属性を保持しない状態にする

### Requirement 2: 表示挙動

**Objective:** As a KeyNest エンドユーザー, I want 実アイコン解決成功時はシステムマスク
（円形）外側が透明で表示され、解決失敗時の fallback では従来通り角丸 + 青タイル + 白文字が
表示されること, so that アイコンの四隅から青が透ける視覚不具合を解消しつつ、未インストール
アプリ向けの視認性は維持される

#### Acceptance Criteria

1. When `IconLoader` が `PackageManager.getApplicationIcon` から実アイコン（`AdaptiveIconDrawable` 等）を取得して `ImageView` に適用したとき, the KeyNest App shall システムマスク（円形）外側の領域を透明で描画する（青タイル色が透けない状態にする）
2. When `IconLoader` が解決失敗により `InitialLetterDrawable` を `ImageView` に適用したとき, the `InitialLetterDrawable` shall Issue #43 / #46 で確定済みの視覚仕様（12dp 角丸 + `kn_blue_500` タイル + 白文字頭文字）を自身で描画する
3. While `IconLoader` が cache miss の解決処理を実行中である状態で, the `ImageView` shall 一時的に空白（drawable 未適用）状態を表示する

### Requirement 3: 既存テストの互換

**Objective:** As a KeyNest 開発者・QA, I want 本変更によって Issue #43 / #46 で追加済みの
既存テストが破壊されないこと, so that 過去 Issue で確定済みの挙動（race 防止、診断 log、
fallback 描画寸法等）が回帰していないことを CI で機械的に確認できる

#### Acceptance Criteria

1. The `IconLoaderTest` の既存テストケース shall 本変更前と同じ pass / fail 結果（全 pass）を維持する
2. The `InitialLetterDrawableTest` の既存テストケース shall 本変更前と同じ pass / fail 結果（全 pass）を維持する
3. The `PackagePickerLayoutTokensTest` 等の layout token 系既存テスト shall 本変更前と同じ pass / fail 結果（全 pass）を維持する

## Non-Functional Requirements

### NFR 1: 既存 NFR の維持

1. The `IconLoader` shall Issue #43 NFR 1.1 で定めた LRU キャッシュ容量 64 を変更しない
2. The `IconLoader` shall Issue #43 NFR 1.2 で定めた cache hit 経路の 16ms 以内応答を変更しない
3. The `IconLoader` shall Issue #43 NFR 1.3 で定めた `PackageManager.getApplicationIcon` の main thread 外実行を変更しない
4. The `InitialLetterDrawable` shall Issue #43 Req 3.x / Issue #46 Req 3.x で定めた intrinsic 寸法 / グリフサイズ比率（タイル短辺の 45%）を変更しない

## Out of Scope

- `AdaptiveIconDrawable` から foreground / background layer を分離して 12dp 角丸で
  カスタム合成する実装（過剰スコープ）
- ランチャーマスク差異（円形 / squircle / teardrop 等）の正規化
- `AdaptiveIconDrawable` 非対応のレガシーアイコン（旧 BitmapDrawable のみのアプリ）の
  角丸 / 中央配置対応（API 26+ ターゲット前提）
- アイコン取得失敗率の自動テレメトリ送信
- Coil / Glide 等の外部画像ローダの導入

## Open Questions

1. **resolve 中の loading 表示**: Requirement 1 で親 `FrameLayout` の青タイルを削除すると、
   cache miss の resolve フェーズ（数ミリ秒）中に当該 `ImageView` 領域が完全に空白になる。
   これを UX 上許容するか、または resolve 中も先に `InitialLetterDrawable` を表示し解決後に
   実アイコンに差し替える「2 段階表示」案を採用するか、人間レビュワーの判断を求める。
2. **`InitialLetterDrawable` の存在意義**: 親 `FrameLayout` の background を削除することで
   「未インストール時 / 解決失敗時のみ fallback drawable が描画される」設計が明確になる
   （解決成功時は drawable 自身が完結）。この仕様変更を Issue #43 / #46 の設計意図と整合的と
   みなしてよいか、人間レビュワーの最終確認を求める。
3. **`AdaptiveIconDrawable` 非対応の旧アイコン**: API 26+ ターゲットでも、旧 BitmapDrawable
   のみを提供するアプリは存在し、これらは円形マスクなしで矩形のまま `scaleType="fitCenter"` で
   中央配置される。本 Issue ではこれを Out of Scope として許容するか、別 Issue で扱うかを
   人間レビュワーに判断委任する。
