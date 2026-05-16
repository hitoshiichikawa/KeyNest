# Requirements Document

## Introduction

KeyNest の Phase 2 (#31〜#34) では、`credential_list_item.xml` /
`credential_list_recent_item.xml` / `package_picker_row_item.xml` のアイコン枠を
`@drawable/kn_icon_tile_bg`（`@color/kn_blue_500` fill + 角丸の単色タイル）を
`android:background` として配置するところまで実装したが、その上に実アプリアイコンを
載せる処理は仕様に含まれていなかった。実機ではすべての行が単色タイルで描画されるため、
保存済み credential や picker 候補を視覚的に識別しづらい状態が継続している。
本 Issue (#43) は、3 つの画面（クレデンシャル一覧本体 / 最近使用 carousel / Package Picker 行）
で共通利用される `IconLoader`（または同等責務のヘルパー）を新設し、
`PackageManager.getApplicationIcon(packageName)` 経由で実アイコンを取得・描画する。
未インストール / 取得失敗時はパッケージ名の末尾セグメント先頭 1 文字を大文字化した
頭文字 fallback を描画して UX を担保する。外部画像ローダ（Coil / Glide）への依存追加、
adaptive icon の独自 mask 描画、オンライン icon 解決は本 Issue の対象外であり、
既存のアイコンタイル寸法・角丸トークン・`Dispatchers.IO` を用いた既存スレッドモデルは
そのまま維持する。

## Scope

### 対象 layout / adapter

| 画面 | 対象 layout | 対象 adapter |
| --- | --- | --- |
| クレデンシャル一覧本体 | `credential_list_item.xml`（icon: line 58-61） | `CredentialListAdapter` |
| 最近使用 carousel | `credential_list_recent_item.xml`（icon: line 50） | `RecentlyUsedCarouselAdapter` |
| Package Picker 行 | `package_picker_row_item.xml`（icon: line 45-48） | `PackagePickerBottomSheet` 内 `RecyclerView.Adapter` |

3 つの adapter で共通利用する `IconLoader`（または同等の責務を持つ helper）を新設する。

## Requirements

### Requirement 1: アプリアイコン解決

**Objective:** As a エンドユーザー, I want クレデンシャル一覧 / 最近使用 carousel / Package Picker の各行に対応アプリの実アイコンが表示されること, so that 単色タイルだけでは判別できなかった保存済み credential / 候補アプリを視覚的に素早く識別できる

#### Acceptance Criteria

1. When クレデンシャル一覧の行をバインドしたとき, the IconLoader shall `PackageManager.getApplicationIcon(packageName)` の結果を当該行のアイコン `ImageView` に設定する
2. When 最近使用 carousel の行をバインドしたとき, the IconLoader shall `PackageManager.getApplicationIcon(packageName)` の結果を当該行のアイコン `ImageView` に設定する
3. When Package Picker の行をバインドしたとき, the IconLoader shall `PackageManager.getApplicationIcon(packageName)` の結果を当該行のアイコン `ImageView` に設定する
4. If 対象パッケージが未インストール（`NameNotFoundException` 発生）またはアイコン取得処理で例外が発生したとき, the IconLoader shall 頭文字 fallback（パッケージ名末尾セグメントの先頭 1 文字を大文字化し、白色テキストで色タイル上に描画する表示）を当該行に表示する
5. If `packageName` が空文字または `null` であるとき, the IconLoader shall 頭文字 fallback を描画せず、既定の色タイル（`@drawable/kn_icon_tile_bg`）のみを当該行に表示する

### Requirement 2: パフォーマンス / キャッシュ

**Objective:** As a エンドユーザー, I want スクロール時に行のアイコン描画でフレーム落ちや UI スレッドのジャンクが発生しないこと, so that 一覧画面・carousel・Picker のスクロールが滑らかに保たれ、保存済み credential 数が増えても操作感が劣化しない

#### Acceptance Criteria

1. The IconLoader shall アイコン解決結果を最大 64 エントリの LRU キャッシュに保持し、同一 `packageName` の再解決時にディスク I/O を発生させない
2. The IconLoader shall アイコン解決処理（`PackageManager.getApplicationIcon` 呼び出し）を main thread 以外（`Dispatchers.IO` 相当のバックグラウンド実行コンテキスト）で実行する
3. While RecyclerView がスクロール中である場合, the IconLoader shall 既に解決済みのアイコン要求について、キャッシュヒット経路で 16ms 以内に呼び出し元に結果を返す
4. When ViewHolder が `onViewRecycled` で再利用されるとき, the adapter shall 解決中であった旧 `packageName` のアイコン解決結果を再利用後の新 ViewHolder に反映させない（race 防止のため tag 等で要求と ViewHolder の対応関係を照合する）

### Requirement 3: design 整合

**Objective:** As a エンドユーザー, I want アイコンが既存の KeyNest デザイントークン（タイル寸法・角丸・色）と矛盾しない外観で描画されること, so that Phase 2 で整えた一覧 / carousel / Picker の視覚仕様が崩れない

#### Acceptance Criteria

1. The icon tile background (`@drawable/kn_icon_tile_bg` の角丸 + `@color/kn_blue_500` fill) shall アイコン未解決時（解決待ち・fallback 表示時）の fallback 表示としてのみユーザーに可視となる
2. When アプリアイコンが解決されたとき, the ImageView shall アイコンタイルの角丸境界内に収まるサイズで実アイコンを描画する
3. The icon tile size shall クレデンシャル一覧本体において `@dimen/kn_icon_tile_lg` を維持する
4. The icon tile size shall 最近使用 carousel および Package Picker 行において `@dimen/kn_icon_tile_sm` を維持する

### Requirement 4: テスト

**Objective:** As a 開発者 / メンテナ, I want IconLoader の主要分岐（成功・例外 fallback・キャッシュヒット・空入力）が自動テストで網羅されること, so that 後続変更時のリグレッションを早期検知できる

#### Acceptance Criteria

1. The IconLoader unit test shall パッケージが存在するケースで `PackageManager.getApplicationIcon(packageName)` の戻り値を `Drawable` としてそのまま返すことを検証する
2. The IconLoader unit test shall `NameNotFoundException` が発生したケースで頭文字 fallback を返すことを検証する
3. The IconLoader unit test shall 同一 `packageName` の 2 回目以降の呼び出しで LRU キャッシュヒット経路が使われることを検証する
4. The IconLoader unit test shall `packageName` が空文字または `null` のケースで頭文字 fallback を返さず、`null` または既定 placeholder を返すことを検証する

## Non-Functional Requirements

### NFR 1: パフォーマンス目標

1. The IconLoader shall LRU キャッシュサイズを 64 エントリに固定し、超過時は最も古いエントリから順に追い出す
2. While RecyclerView スクロール中である場合, the IconLoader shall キャッシュヒット経路で 16ms 以内に結果を返却し、1 フレーム（約 16.67ms）あたりの描画予算を超過させない
3. The IconLoader shall キャッシュミス時のアイコン解決を main thread 以外で実行し、UI スレッドの応答性を維持する

### NFR 2: API 互換性

1. The IconLoader shall API 26 以上の端末で `PackageManager.getApplicationIcon(packageName)` が adaptive icon を返す場合、返却された `Drawable` をそのまま `ImageView` に設定する（独自 mask 描画は行わない）
2. The IconLoader shall API 26 未満の端末では `PackageManager.getApplicationIcon(packageName)` の戻り値をそのまま `ImageView` に設定し、独自の円形 mask 等の追加加工を行わず、視覚的なまとまりはアイコンタイルの角丸 clip にのみ依存させる
3. The icon resolution flow shall 既存の `PackageManager.getApplicationIcon(packageName)` API 経路に新たな AndroidManifest `<queries>` 制限を課さない（既存のパッケージ取得経路で参照可能なパッケージ範囲を維持する）

### NFR 3: 既存機能・配線の不変

1. The icon tile background drawable `@drawable/kn_icon_tile_bg`（`@color/kn_blue_500` fill + 角丸）shall 本 Issue で削除・リネームしない
2. The icon tile dimension tokens `@dimen/kn_icon_tile_lg` および `@dimen/kn_icon_tile_sm` shall 本 Issue で値を変更しない
3. The 3 adapter (`CredentialListAdapter` / `RecentlyUsedCarouselAdapter` / `PackagePickerBottomSheet` 内 `RecyclerView.Adapter`) shall 本 Issue 変更後も既存の bind 経路・ViewHolder ライフサイクル・コールバック挙動を保持する

### NFR 4: 依存範囲

1. The icon resolution feature shall 外部画像ローダ（Coil / Glide 等）への依存を追加しない
2. The icon resolution feature shall Web favicon 取得・icon pack 連携などのオンライン解決経路を実装しない

## Out of Scope

- Coil / Glide 等の外部画像ローダ依存追加（KeyNest は依存追加に保守的な方針を継続）
- adaptive icon の独自 mask 描画（角丸 clip で十分。API 26+ の adaptive icon は framework `Drawable` をそのまま使用）
- Web favicon 取得や icon pack 連携などのオンライン解決経路
- アイコンタイル背景色の per-package 動的カラー割り当て（既存の `@color/kn_blue_500` 単色フォールバックを維持）
- `pm.getApplicationLabel(...)` ベースの頭文字算出（API 呼び出し増のため不採用。本 Issue では package name の末尾セグメントを使う方針）
- semaphore 等による多数並列解決のスロットリング（本 Issue では単純な `Dispatchers.IO` + `lifecycleScope.launch` ベースの実装にとどめる）
- design mock 表層トークン整合（Phase 2 #31〜#34 で完了済み）
- Compose 化（既存 View ベースのレイアウト XML / Adapter 改修にとどめる）

## 確認事項（レビュワーへの依頼）

> 以下は Issue 本文に記載され、人間レビュワーからの明示的回答がまだ得られていない判断ポイント。
> PM 側で結論を確定せず、Architect / 人間レビュワー判断に委ねるため本セクションに残置する。

1. **頭文字の算出ルール**: package name の末尾セグメント（例: `com.example.keynest` → `keynest`）の先頭 1 文字を大文字化する想定。`com.android.chrome` → `C` / `org.mozilla.firefox` → `F`。`label = pm.getApplicationLabel(...)` ベースの算出も選択肢としてあり得るが、ラベル取得は API 呼び出し増のため見送る。これで問題ないか
2. **非同期実装の粒度**: `Dispatchers.IO` + `lifecycleScope.launch` ベースの簡易実装で十分か。多数行同時の解決時に過剰な並列化を防ぐ semaphore 等は不要か
3. **adaptive icon の扱い**: API 26+ は `Drawable` として adaptive icon が返るのでそのまま描画する。古い API では円形 mask は適用せず、icon tile の角丸 clip のみで統一する（独自 mask 描画はスコープ外）。これで問題ないか

## 用語集

- **アイコンタイル**: `@drawable/kn_icon_tile_bg`（`@color/kn_blue_500` fill + 角丸）で描画される、各行のアイコン枠。Phase 2 #31〜#34 で配置済み
- **頭文字 fallback**: 実アイコン取得に失敗した場合の代替表示。パッケージ名末尾セグメントの先頭 1 文字を大文字化し、白色テキストで色タイル上に描画する
- **末尾セグメント**: ドットで区切られたパッケージ名の最後の要素。例: `com.example.keynest` → `keynest`
- **LRU キャッシュ**: 最近最少使用されたエントリから順に追い出す方式のキャッシュ。本 Issue では最大 64 エントリで運用する
