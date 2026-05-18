# Requirements Document

## Introduction

PR #45（Issue #43）で `IconLoader` を導入し、credential 一覧 / 最近使用 carousel /
Package Picker の 3 画面で実アプリアイコンを表示する実装を main にマージ済みである。
しかし、実機検証では 3 画面すべてで実アイコンも頭文字 fallback も表示されず、
`@drawable/kn_icon_tile_bg` の青タイル背景のみが見える状態が継続している。
更に、`IconLoader` には診断 log が一切無いため、cache miss が発生しているのか・
coroutine 起動に失敗しているのか・描画段階で潰れているのかを Logcat から切り分けできない。

本 Issue (#46) は、Issue #43 で達成済みとされた 3 画面のアイコン表示挙動を実機で再現させ、
かつ将来同種の表示不具合が再発した際に Logcat だけで一次切り分けが可能な状態にする
hotfix である。Issue #43 で確定済みの NFR（LRU キャッシュ容量 64、`Dispatchers.IO` での
解決、頭文字 fallback の視覚仕様）は維持する。

## Scope

### 対象ファイル

| 区分 | 対象 |
| --- | --- |
| Kotlin | `app/src/main/java/com/example/keynest/util/IconLoader.kt` |
| Kotlin | `app/src/main/java/com/example/keynest/util/InitialLetterDrawable.kt` |
| Kotlin | `app/src/main/java/com/example/keynest/di/ServiceLocator.kt` |
| Layout | `app/src/main/res/layout/credential_list_item.xml` |
| Layout | `app/src/main/res/layout/credential_list_recent_item.xml` |
| Layout | `app/src/main/res/layout/package_picker_row_item.xml` |

### 対象画面

1. クレデンシャル一覧本体（`CredentialListAdapter` 経由）
2. 最近使用 carousel（`RecentlyUsedCarouselAdapter` 経由）
3. Package Picker 行（`PackagePickerBottomSheet` 内 `RecyclerView.Adapter` 経由）

## Requirements

### Requirement 1: 実機での実アイコン描画の復旧

**Objective:** As a KeyNest エンドユーザー, I want クレデンシャル一覧 / 最近使用 carousel / Package Picker の 3 画面で対応アプリの実アイコンが青タイル背景の上に重畳描画されること, so that 単色タイルでは判別不能だった保存済み credential / 候補アプリを視覚的に識別できる

#### Acceptance Criteria

1.1. When クレデンシャル一覧の行が画面に表示されるためにバインドされたとき, the IconLoader shall 当該行のアイコン `ImageView` に対応パッケージの実アイコン `Drawable` を `setImageDrawable` で適用する
1.2. When 最近使用 carousel の行が画面に表示されるためにバインドされたとき, the IconLoader shall 当該行のアイコン `ImageView` に対応パッケージの実アイコン `Drawable` を `setImageDrawable` で適用する
1.3. When Package Picker の行が画面に表示されるためにバインドされたとき, the IconLoader shall 当該行のアイコン `ImageView` に対応パッケージの実アイコン `Drawable` を `setImageDrawable` で適用する
1.4. If 対象パッケージが未インストール、またはアイコン取得処理で例外が発生したとき, the IconLoader shall 頭文字 fallback `Drawable` を当該行のアイコン `ImageView` に `setImageDrawable` で適用する
1.5. While 3 画面のいずれかが表示されている状態で, the KeyNest App shall アイコン `ImageView` に実アイコン `Drawable` または頭文字 fallback `Drawable` のいずれかが適用された結果、当該 `ImageView` の描画領域に実アイコン形状または頭文字の文字形が観測される状態にする

### Requirement 2: 解決完了前に coroutine が破棄されないこと

**Objective:** As a KeyNest エンドユーザー, I want アイコン解決の coroutine が ViewHolder ライフサイクルや View 取り付け状態に影響されず最後まで完走すること, so that 行が初めて表示された直後でもアイコンが欠落せず描画される

#### Acceptance Criteria

2.1. When `IconLoader.loadInto` がアイコン解決の coroutine を起動したとき, the IconLoader shall ViewHolder の取り付け状態や Activity のライフサイクル状態に依存しない、プロセス寿命のスコープ上で当該 coroutine を起動する
2.2. If アイコン解決の coroutine の起動経路上で必要な前提（スコープ取得など）を満たせなかったとき, the IconLoader shall 早期 return で silent fail せず、診断 log（後述 Requirement 4）を出力した上で頭文字 fallback または既知の不在状態に解決する
2.3. When プロセス寿命の解決スコープ上で coroutine が解決処理を完了したとき, the IconLoader shall 当該 `ImageView` のタグが起動時のパッケージ名と一致する場合にのみ `setImageDrawable` を main thread で実行する（Issue #43 Req 2.4 の race 防止挙動を維持する）

### Requirement 3: 頭文字 fallback の固有寸法

**Objective:** As a KeyNest エンドユーザー, I want アイコン取得失敗時に表示される頭文字 fallback が `fitCenter` 等の scale type 配下でも描画領域いっぱいに表示されること, so that 実アイコン未取得時にも、青タイル上の白い頭文字を視認できる

#### Acceptance Criteria

3.1. The InitialLetterDrawable shall 自身の intrinsic 幅・高さに正の整数 px 値を返し、`Drawable.getIntrinsicWidth()` と `Drawable.getIntrinsicHeight()` の戻り値が `-1` ではない状態にする
3.2. While `ImageView` の `scaleType` が `fitCenter` に設定されている状態で, the KeyNest App shall `InitialLetterDrawable` を `setImageDrawable` で適用したとき、`ImageView` の bounds が空にならず文字形が描画される状態にする
3.3. The InitialLetterDrawable shall intrinsic 寸法と実際に描画される文字グリフサイズが、Issue #43 Req 3.x で定めた視覚仕様（タイル短辺の 45% を文字グリフ短辺に割り当てる）と矛盾しない比率になるよう構築される

### Requirement 4: 診断 log による Logcat 切り分け

**Objective:** As a KeyNest 開発者・QA, I want `IconLoader` の主要分岐点で診断 log が Logcat に出力されること, so that アイコン表示が再度欠落した際に Logcat だけで cache hit / cache miss / 解決成功 / 解決失敗 / 描画キャンセル のどこで止まっているかを切り分けられる

#### Acceptance Criteria

4.1. When `IconLoader.loadInto` がアイコン解決要求を受け付けたとき, the IconLoader shall パッケージ名（または末尾セグメント）を含む診断 log を Logcat に 1 行出力する
4.2. When キャッシュヒットでアイコンを返したとき, the IconLoader shall キャッシュヒットであることとパッケージ名を含む診断 log を Logcat に 1 行出力する
4.3. When キャッシュミスで `PackageManager` から実アイコン取得に成功したとき, the IconLoader shall 解決成功であることとパッケージ名を含む診断 log を Logcat に 1 行出力する
4.4. When キャッシュミスかつ頭文字 fallback に切り替わったとき, the IconLoader shall fallback 経路に入ったこととパッケージ名を含む診断 log を Logcat に 1 行出力する
4.5. If アイコン解決経路上で coroutine スコープ取得失敗・予期せぬ例外発生・適用前のキャンセル等の異常が発生したとき, the IconLoader shall 警告レベルの診断 log を Logcat に 1 行出力する
4.6. The IconLoader shall 診断 log の出力内容に、credential のパスワード・暗号文・ユーザー名・ラベル・署名 SHA-256 等の機微情報を含めない（含めてよいのはパッケージ名・末尾セグメント・解決結果分類・例外クラス名のみ）

### Requirement 5: ImageView クリップ副作用の除去

**Objective:** As a KeyNest エンドユーザー, I want 行の `ImageView` が `clipToOutline` 起因で全描画クリップされて見えなくなることが無いこと, so that 実アイコンと頭文字 fallback の双方が描画後に正しく可視化される

#### Acceptance Criteria

5.1. While 3 画面いずれかの行がバインドされた状態で, the KeyNest App shall アイコン `ImageView` の `clipToOutline` 起因の outline 不在クリップによって、適用済み `Drawable` が不可視となる挙動を発生させない
5.2. The KeyNest App shall タイルの角丸視覚仕様（Issue #43 Req 3.x で定めた `kn_r_icon_tile` 等）を、`ImageView` の outline クリップ以外の手段（親 `FrameLayout` の背景描画など）で維持する
5.3. While 3 画面いずれかの行がバインドされた状態で, the KeyNest App shall アイコンタイルの角丸描画見た目を、Issue #43 確定時点の視覚仕様と等価に保つ

## Non-Functional Requirements

### NFR 1: パフォーマンス / 既存 NFR の維持

1.1. The IconLoader shall LRU キャッシュの最大エントリ数を 64 のまま維持する（Issue #43 NFR 1.1 を変更しない）
1.2. While RecyclerView がスクロール中である状態で, the IconLoader shall 解決済みパッケージのアイコン要求についてキャッシュヒット経路で 16ms 以内に呼び出し元に結果を返す（Issue #43 NFR 1.2 を維持する）
1.3. The IconLoader shall `PackageManager.getApplicationIcon` の呼び出しを main thread 以外（`Dispatchers.IO` 相当のバックグラウンド実行コンテキスト）で実行する（Issue #43 NFR 1.3 を維持する）

### NFR 2: ログのノイズ抑制

2.1. While アプリが通常稼働している状態で, the IconLoader shall Requirement 4.1〜4.4 の通常分岐 log を、本番ビルドで Logcat の `warn` レベル以上で出力しない
2.2. If Requirement 4.5 に該当する異常が発生したとき, the IconLoader shall 当該 log を `warn` レベル以上で出力し、通常分岐との区別を可能にする

### NFR 3: 機微情報の保護

3.1. The IconLoader shall 診断 log 出力に credential のパスワード・暗号文・ユーザー名・ラベル・署名 SHA-256・端末識別子を含めない
3.2. The IconLoader shall 診断 log に含めるパッケージ名以外の文字列を、解決結果分類・例外クラス名・キャッシュ統計（件数）等の非機微情報に限定する

## 前提・確認事項

本要件を確定するにあたり、Issue 本文末尾で人間レビュワーが残した 4 つの判断ポイントについて、
PM 推奨案を以下に整理する。Architect / Developer は別案を採る場合、本セクションを起点に
逆提案を行うこと。

1. **解決スコープの cancel 戦略**: プロセス寿命を持つ解決スコープを採用し、明示的な cancel
   は行わない。`SupervisorJob` 等で個別の解決失敗が他の解決を巻き込まないよう隔離する。
   ViewHolder 再利用時の race 防止は Issue #43 Req 2.4 の tag 照合に委ねる。
2. **頭文字 fallback drawable の intrinsic size**: コンストラクタ引数として px 値を受け取る
   案を推奨。Resources へのアクセス可否に依存せず、`IconLoader` 側で 3 画面それぞれのタイル
   寸法（`kn_icon_tile_lg` = 44dp / 36dp / `kn_icon_tile_sm` = 32dp）に応じて px に変換した
   値を渡せるようにする。
3. **`clipToOutline` の扱い**: 3 layout から ImageView の `clipToOutline="true"` 属性を削除する
   方針を推奨。角丸視覚仕様は親 `FrameLayout` の `@drawable/kn_icon_tile_bg` の角丸描画で維持する。
   実アイコンの角丸が必要な場合は別 Issue で再検討する。
4. **診断 log の Logcat レベル**: 通常分岐（4.1〜4.4）は `Log.d`、異常分岐（4.5）は `Log.w` を
   既定とする（NFR 2.x に対応）。tag 文字列は `IconLoader` 固定で、grep で一括抽出可能とする。

## Out of Scope

- Frequently used セクションの sample data 入れ替え（別 Issue で扱う）
- adaptive icon の custom mask 描画
- icon cache size の調整（Issue #43 NFR 1.1 = 64 を維持する）
- Coil / Glide 等の外部画像ローダの導入
- web favicon / icon-pack 解決
- 実アイコンに対する角丸クリップの再導入（角丸見た目は親 FrameLayout 背景で維持）
- アイコン解決失敗率の自動テレメトリ送信

## Open Questions

- なし（本要件で生じた未決定事項は「前提・確認事項」セクションに PM 推奨案として明記済み。
  別案採用が必要な場合は Architect / Developer 側から Issue コメントでエスカレーションすること）
