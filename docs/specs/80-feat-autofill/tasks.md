# Task Breakdown — Issue #80 / feat(autofill): クレデンシャル候補リストに入力対象アプリのアイコンを表示

> 関連: `requirements.md`, `design.md`（本ディレクトリ）
>
> 各タスクは独立コミット可能な粒度で、依存順に並べている。Developer はこの順番で実装する。

---

## T-01: `AutofillIconRasterizer` 新規追加 + 単体テスト

### 概要
`PackageManager.getApplicationIcon` で取得した `Drawable` を blue tile 上に center crop し `Bitmap` に焼き込む util を新設する。失敗時は `@drawable/ic_key_24` + `@drawable/kn_icon_tile_bg` の合成 fallback bitmap を返す。

### 変更ファイル
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/icon/AutofillIconRasterizer.kt`
- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/autofill/icon/AutofillIconRasterizerTest.kt`

### 公開 IF
- `class AutofillIconRasterizer(context: Context)` + secondary constructor で `PackageManager` 注入可能。
- `fun loadCallerIconBitmap(callerPackage: String?, sizePx: Int = defaultSizePx): Bitmap`（fallback 込みで必ず非 null を返す）
- `fun loadFallbackBitmap(sizePx: Int = defaultSizePx): Bitmap`
- 内部定数: `DEFAULT_ICON_DP = 48`、`MAX_SIZE_PX = 192`

### 受入基準（要件対応）
- 要件 1.1: `getApplicationIcon` 呼び出し → `Drawable`
- 要件 1.2: 48dp 相当 / 4 bytes/px / 上限 192 px の `Bitmap`
- 要件 1.3: `NameNotFoundException` / `RuntimeException` で fallback bitmap

### 完了条件
- 単体テスト 4 ケース（正常系 / `NameNotFoundException` / `RuntimeException` / blank package）すべて pass
- `./gradlew :app:testDebugUnitTest` が pass

### 依存タスク
- なし（先頭）

---

## T-02: `DatasetPresentationFactory` に `callerPackage` 引数追加 + RemoteViews への bitmap 設定

### 概要
`build(label, subtitle, callerPackage)` シグネチャに拡張し、`callerPackage` 非 null の場合は `AutofillIconRasterizer.loadCallerIconBitmap` を呼んで `RemoteViews.setImageViewBitmap(R.id.dataset_icon, bitmap)` で適用する。`null` / blank の場合は既存 `@drawable/ic_key_24` を維持する（何もしない）。

### 変更ファイル
- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/builder/DatasetPresentationFactory.kt`
- （後述 T-05 と密接だが View ID 追加は本タスクで完結する形でもよい。実装順は Developer 判断）

### 受入基準（要件対応）
- 要件 2.1: dataset 行 ImageView に bitmap を `setImageViewBitmap` で設定
- 要件 2.2: 既存 `dataset_presentation.xml` の View ID 不変（layout 改修は T-05 に切り出し）
- 要件 2.3: blue tile 背景を維持（rasterizer 側で焼き込む or layout 側の `FrameLayout` 背景を残す）
- 要件 4.1 / 4.2: `callerPackage` を受け取って locked / unlocked 経路で同一に使う

### 完了条件
- `FillResponseBuilderTest` が新シグネチャで pass（呼び出し側修正は T-04 で行うが、本タスクで `build()` 単独ユニットテストを追加してもよい）
- lint / detekt 警告なし

### 依存タスク
- T-01（`AutofillIconRasterizer` の存在）

---

## T-03: `DatasetPresentationFactory.buildInline` で `setStartIcon` 適用 + 単体テスト

### 概要
**現状 `buildInlineApiR` は `setStartIcon` を呼んでおらず、inline chip に icon が一切表示されていない**（実機 GBoard で確認済み）。本タスクで `buildInline(label, subtitle, spec, callerPackage)` シグネチャに拡張し、`InlineSuggestionUi.newContentBuilder(...).build()` の前に必ず `setStartIcon(...)` を呼ぶ経路を新規追加する。

- 正常系: `AutofillIconRasterizer.loadCallerIconForInline(callerPackage, sizePx)` が返す `Icon.createWithBitmap(callerBitmap)` を `setStartIcon` に渡す。`spec.maxSize` で bitmap サイズを clip する（`sizePx = min(defaultSizePx, spec.maxSize.width, spec.maxSize.height)`）。
- Fallback: `NameNotFoundException` / `RuntimeException` / `callerPackage == null` / blank では `Icon.createWithResource(context, R.drawable.ic_key_24)` を `setStartIcon` に渡す（popup の blue tile 合成 bitmap とは別 API。設計 §6 Inline fallback と Popup fallback の差異 を参照）。

### 変更ファイル
- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/builder/DatasetPresentationFactory.kt`
- 新規 or 拡張: `app/src/test/java/io/github/hitoshiichikawa/keynest/autofill/builder/DatasetPresentationFactoryTest.kt`

### 受入基準（要件対応）
- 要件 3.1: `setStartIcon(...)` を **build() の前に必ず呼ぶ**（現状未呼び出し状態の解消）
- 要件 3.2: 正常系で caller icon の `Icon.createWithBitmap(...)` が `setStartIcon` に渡される
- 要件 3.3: 失敗 / null / blank 経路で `Icon.createWithResource(context, R.drawable.ic_key_24)` が `setStartIcon` に渡される
- 要件 3.4: `spec.maxSize` 制約に従う

### 完了条件
- `@RequiresApi(R)` 配下で実装され、API 30 未満端末では既存通り inline は無効
- 単体テストが pass:
  - `setStartIcon` 呼び出し検証（現状未呼び出し状態の解消）
  - 正常系で `Icon.createWithBitmap` 経路
  - 失敗系 / null / blank で `Icon.createWithResource(R.drawable.ic_key_24)` 経路
  - `spec.maxSize` 反映の検証

### 依存タスク
- T-01（rasterizer）
- T-02（同 factory の片方の経路が先行していると差分レビューが楽）

---

## T-04: `FillResponseBuilder` から `callerPackage` を伝搬

### 概要
`buildLockedResponse(...)` に `callerPackage: String?` 引数を追加し、`buildLockedDataset(...)` 経由で `presentationFactory.build / buildInline` に渡す。`KeyNestAutofillService.onFillRequest` の呼び出し側も `callerPackage` を渡すよう更新。

### 変更ファイル
- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/builder/FillResponseBuilder.kt`
- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/KeyNestAutofillService.kt`
- 変更: `app/src/test/java/io/github/hitoshiichikawa/keynest/autofill/FillResponseBuilderTest.kt`
- 変更: `app/src/test/java/io/github/hitoshiichikawa/keynest/autofill/LockedFillResponseSecurityTest.kt`
- 変更: `app/src/test/java/io/github/hitoshiichikawa/keynest/autofill/CustomFieldFillResponseTest.kt`

### 受入基準（要件対応）
- 要件 4.1: `FillRequest` の caller `packageName`（`extractCallerPackage` 経由）を `FillResponseBuilder` → `DatasetPresentationFactory` まで伝搬
- 要件 4.2: locked dataset の全 dataset 行で同一 `callerPackage` を使用
- 要件 NFR 1.4: locked Dataset のセキュリティ境界（`setAuthentication`、`PLACEHOLDER`）が不変

### 完了条件
- `FillResponseBuilderTest` の既存 4 ケースに `callerPackage = "com.example.target"` を追加して pass
- `LockedFillResponseSecurityTest` の parcel byte assertion が pass（caller package が parcel に含まれることは許容）
- `CustomFieldFillResponseTest` の呼び出しサイト更新

### 依存タスク
- T-02 / T-03（`build` / `buildInline` の新シグネチャ）

---

## T-05: `dataset_presentation.xml` の View ID 追加 + token テスト更新

### 概要
dataset 行の icon `ImageView`（既存）に `android:id="@+id/dataset_icon"` を追加する。`@+id/dataset_label` / `@+id/dataset_subtitle` の View ID と layout 構造は変更しない（Issue #34 Req 7 不抵触）。`DatasetPresentationLayoutTokensTest` に `dataset_icon` View ID の存在 assertion を追加する。

### 変更ファイル
- 変更: `app/src/main/res/layout/dataset_presentation.xml`
- 変更: `app/src/test/java/io/github/hitoshiichikawa/keynest/resources/DatasetPresentationLayoutTokensTest.kt`

### 受入基準（要件対応）
- 要件 2.2: View ID 不変ルール踏襲、新規 ID 追加のみ
- 要件 NFR 1.1: 既存 `R.id.dataset_label` / `R.id.dataset_subtitle` 不変
- 要件 5.4: 既存テストが pass

### 完了条件
- `DatasetPresentationLayoutTokensTest` 全 assertion が pass（既存 + 新規）
- layout XML の `?attr/` / hex color 制約に違反しない
- `dataset_label` / `dataset_subtitle` ID は 1 文字も変えない

### 依存タスク
- なし（T-02 と並行でも可、ただし T-02 で `R.id.dataset_icon` を参照するため、T-02 の実装前に layout 側が ID を持っている必要がある。**推奨順序: T-05 → T-02**）

---

## T-06: 統合確認

### 概要
全テスト pass、手動 verification、PR 用の確認事項転記。

### 変更ファイル
- なし（コミット対象なし。確認のみ）

### 受入基準
- `./gradlew :app:testDebugUnitTest` 全 pass
- `./gradlew :app:lintDebug` 警告なし or 既存ベースライン内
- 手動: 実機 / エミュレータで Twitter / Slack / GitHub など 2-3 アプリ別 caller で autofill を発火させ、dataset popup の各行に caller アプリの実 icon が表示されることを確認
- 手動: Gboard など inline suggestion 対応 IME で chip 上に caller icon が表示されることを確認（**変更前は icon が一切表示されていなかった状態が解消されていることを実機で確認する**）
- 手動: inline で caller package 解決失敗時に `R.drawable.ic_key_24`（鍵アイコン、blue tile なし）が chip 上に表示されることを確認
- 手動: アンインストール済み / 取得失敗の package で fallback (鍵アイコン) が表示されることを確認
- 手動: locked 状態で popup を開いて icon が caller アプリのものになっていることを確認
- design.md §10 の「確認事項」を PR 本文に転記

### 完了条件
- 既存 6 テストファイル（`FillResponseBuilderTest` / `LockedFillResponseSecurityTest` / `DatasetPresentationLayoutTokensTest` / `CustomFieldFillResponseTest` / 新規 `AutofillIconRasterizerTest` / 新規 `DatasetPresentationFactoryTest`）全 pass
- PR description に design.md §10 の確認事項 6 項目を転記

### 依存タスク
- T-01 〜 T-05 全完了
