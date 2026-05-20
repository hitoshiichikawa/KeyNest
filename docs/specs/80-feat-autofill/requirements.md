# Requirements Document

## 概要 / Goal / Non-Goal

### 概要

KeyNest が autofill 対象アプリで提示するクレデンシャル候補リスト（Dataset popup
+ Inline suggestion）について、現状は以下の状態である:

- **Popup (`RemoteViews`)**: KeyNest 内蔵の鍵アイコン（`@drawable/ic_key_24`）を
  `kn_blue_500` の青タイル上に固定描画している。
- **Inline (IME suggestion strip, GBoard 等)**: `DatasetPresentationFactory.
  buildInlineApiR()` が `InlineSuggestionUi.newContentBuilder(pending).setTitle(...).
  setSubtitle(...).build()` のみを呼んでおり、**`setStartIcon` を呼んでいないため
  icon が一切表示されていない** 実機検証で確認済み）。

本 Issue (#80) は、この dataset 行のアイコンを **入力対象アプリ（autofill caller）
の実 icon** に動的差し替えする。具体的には:

- **Popup**: 現行の鍵アイコン固定描画を caller アプリの実 icon の `Bitmap` に
  差し替える（`RemoteViews.setImageViewBitmap`）。
- **Inline**: `setStartIcon(Icon.createWithBitmap(...))` を新規に追加し、現状の
  「icon 完全非表示」状態を解消する。表示する icon は popup と同じ caller アプリの
  実 icon を bitmap 化したものとする。

複数アプリで KeyNest を使うユーザーが「どのアプリの credential か」を視覚的に
識別しやすくすることが本 Issue の目的である。

caller package の icon が `PackageManager` から取得できない場合の fallback は
経路ごとに異なる API を使う:

- **Popup の fallback**: 既存と同等の「blue tile + `@drawable/ic_key_24`」合成
  `Bitmap`（`setImageViewBitmap`）。
- **Inline の fallback**: `Icon.createWithResource(context, R.drawable.ic_key_24)`
  を `setStartIcon` に渡す（blue tile は焼き込まない。IME suggestion strip の
  視覚仕様に合わせる）。

### Goal

- Dataset popup の各 dataset 行（locked / unlocked dataset 両方）に、caller
  package の実 application icon を bitmap として表示する。
- Inline suggestion の各 chip に対し、**現状 `setStartIcon` 未呼び出しで icon が
  一切表示されていない状態を解消** し、popup と同じ caller package icon を
  `setStartIcon(Icon.createWithBitmap(...))` で表示する。
- icon 取得失敗時は popup / inline それぞれの API で `ic_key_24` 系 fallback に
  静かに切り替え、UX を壊さない。

### Non-Goal (Out of Scope)

- 初期リリースに含めない（本 Issue は低優先度。リリース後のフォローアップ）。
- KEYNEST ヘッダー領域の mark icon（`@drawable/ic_keynest_mark_24`）の差し替え。
- 「署名一致」chip 内の shield icon（`@drawable/ic_shield_fill_16`）の差し替え。
- フッター「+ 新しいクレデンシャル」行の plus icon（`@drawable/ic_plus_24`）の差し替え。
- Issue #43 で導入した `IconLoader`（`lifecycleScope` ベース）との統合・共有
  キャッシュ。autofill Service は Activity lifecycle に紐付かないため、本 Issue
  では `IconLoader` を呼ばず autofill 経路で完結した別実装を採る。
- locked dataset と unlocked dataset の差別化 UI（どちらも同じ caller icon を
  描画する。lock 状態の表現は既存 `ic_lock_outline_16` 末尾アイコンで継続）。
- adaptive icon の独自 mask 描画（framework がラスタライズした bitmap をそのまま
  使う）。
- web favicon / icon pack 等のオンライン icon 解決。
- bitmap の長期キャッシュ（本 Issue では Service 内キャッシュなし。確認事項 4
  の採用方針を参照）。

## 背景

Issue #43 / PR #45 で `IconLoader` を導入し、in-app 一覧画面（クレデンシャル
一覧本体・最近使用 carousel・Package Picker 行）で `PackageManager.
getApplicationIcon(packageName)` 経由の実アイコン表示を実現している。
ただし `IconLoader` は `applicationScope`（ServiceLocator が保持する
`SupervisorJob() + Dispatchers.Main.immediate`）と `Resources` / `ImageView`
を前提とした in-app UI 専用の実装であり、`ImageView.setImageDrawable` で
`Drawable` を直接適用する経路に依存する。

一方、autofill 経路で表示される dataset は Android Autofill framework が
`RemoteViews` 経由で host activity（任意の third-party アプリ）の UI 上に
inflate する。`RemoteViews` は `setImageViewBitmap`（または `setImageViewIcon`）
のように **bitmap / `Icon` 型でしか画像を渡せない** ため、`IconLoader` が返す
`Drawable`（特に `AdaptiveIconDrawable`）をそのまま使えない。
更に inline suggestion 経路は `Slice` API（`Icon.createWithBitmap(...)`）を
要求する。したがって autofill 経路では、`PackageManager.getApplicationIcon`
で取得した `Drawable` を bitmap にラスタライズする変換層が別途必要となる。

## ユーザーストーリー

- As a 複数アプリ（例: Twitter / Slack / GitHub）で KeyNest を使うエンドユーザー,
  I want Autofill dropdown の各 dataset 行に「どのアプリのクレデンシャルか」を
  示すアイコンが表示されること, so that label / username だけでなくアプリ icon の
  視覚的手がかりで対象クレデンシャルを素早く識別できる。
- As a locked dataset 状態（vault 未解除）でも unlocked dataset 状態（vault 解除済み）
  でも一貫した dataset 提示を求めるエンドユーザー, I want どちらの状態でも同じ
  caller package icon が表示されること, so that lock 状態に関わらず「対象アプリ」を
  視覚的に識別する手がかりが失われない。
- As a IME 連携で inline suggestion を使うエンドユーザー, I want IME suggestion strip
  上の chip にも caller package icon が表示されること, so that popup を開かずに
  inline suggestion 段階で対象アプリを識別できる。

## Requirements

### Requirement 1: 対象アプリの icon 取得

**Objective:** As an エンドユーザー, I want dataset 行に caller package の application
icon が表示されること, so that 複数アプリで KeyNest を使うときに対象アプリを
視覚的に識別できる。

#### Acceptance Criteria

1.1. When `DatasetPresentationFactory.build` が `callerPackage` を受け取ったとき,
the factory shall `PackageManager.getApplicationIcon(callerPackage)` で `Drawable`
を取得する

1.2. The factory shall 取得した `Drawable` を 48dp 相当（端末 density に応じた px）
の `Bitmap` に変換する（`RemoteViews` IPC の Binder transaction 制約を超えない
範囲。具体的な px 値の最終確定は design 側で行う）

1.3. If `PackageManager.NameNotFoundException` または `RuntimeException` が
発生したとき, the factory shall 既存の `@drawable/ic_key_24` を fallback として
使用する（採用方針: 確認事項 3 の (a)）

### Requirement 2: `RemoteViews` への bitmap 差し替え

**Objective:** As an エンドユーザー, I want popup の dataset 行 ImageView に
caller icon が描画されること, so that popup 表示時にアプリ識別ができる。

#### Acceptance Criteria

2.1. When `build()` が呼ばれたとき, the `RemoteViews` shall dataset 行 ImageView
に bitmap を `setImageViewBitmap` で設定する

2.2. The layout shall 既存 `dataset_presentation.xml` の dataset 行 ImageView ID
を保持または互換的に拡張する（既存テスト Req 7 の View ID 不変ルールに従う）

2.3. The bitmap shall blue tile 背景の上に center crop で表示される（既存
視覚整合: dataset row icon tile が `@drawable/kn_icon_tile_bg` + 中央 20dp icon
の構造を維持する）

### Requirement 3: Inline (IME suggestion) presentation 対応

**Objective:** As an エンドユーザー, I want IME suggestion strip 上の chip にも
caller icon が表示されること, so that 現状の「icon 完全非表示」状態を解消し、
inline suggestion 段階でも対象アプリを識別できる。

#### Acceptance Criteria

3.1. The `DatasetPresentationFactory.buildInlineApiR` shall `InlineSuggestionUi.
newContentBuilder(pending)...build()` の前に `setStartIcon(Icon.createWithBitmap(bitmap))`
を呼ぶ（現状は `setStartIcon` 未呼び出しで icon が一切表示されていないため、
本 AC を満たすには新規 API 呼び出しの追加が必要）

3.2. When `callerPackage` が解決できた場合, the slice shall popup と同一の caller
package icon を bitmap 化したものを `setStartIcon` に渡す

3.3. If `NameNotFoundException` / `RuntimeException` / `callerPackage == null` /
blank が発生したとき, the slice shall `Icon.createWithResource(context, R.drawable.
ic_key_24)` を fallback として `setStartIcon` に渡す（popup の blue tile 合成 bitmap
とは異なる API で、IME suggestion strip の視覚仕様に合わせる）

3.4. The inline icon bitmap size shall `InlinePresentationSpec` の `maxSize` 制約に
従う（`min(defaultSizePx, spec.maxSize.width, spec.maxSize.height)` で clip）

### Requirement 4: 呼び出し元の伝搬

**Objective:** As a 実装メンテナ, I want FillRequest の caller `packageName` が
`DatasetPresentationFactory` まで一貫して伝わること, so that locked / unlocked
両方の dataset 行で同じ caller icon が描画される。

#### Acceptance Criteria

4.1. The `FillResponseBuilder` shall `FillRequest` の caller `packageName` を
`DatasetPresentationFactory` に伝える

4.2. The caller `packageName` shall locked dataset / unlocked dataset 両方の
dataset 行に伝搬される

### Requirement 5: テスト

**Objective:** As a 開発者 / メンテナ, I want 主要分岐（正常系 / `NameNotFoundException`
fallback / inline presentation）が自動テストで検証されること, so that 後続変更時の
リグレッションを早期検知できる。

#### Acceptance Criteria

5.1. The `DatasetPresentationFactoryTest` shall 正常系で `getApplicationIcon` から
得た `Drawable` が `Bitmap` として `RemoteViews` に設定されることを検証する

5.2. The `DatasetPresentationFactoryTest` shall `NameNotFoundException` 発生時に
既存 `@drawable/ic_key_24` が fallback として使われることを検証する

5.3. The `DatasetPresentationFactoryTest` shall `InlinePresentation` 経路で以下を
検証する: (a) `setStartIcon` が呼ばれること（現状未呼び出し状態の解消検証）, (b)
正常系で caller icon bitmap が渡されること, (c) 失敗時 / null `callerPackage` 時
に `Icon.createWithResource(R.drawable.ic_key_24)` が渡されること

5.4. 既存テスト（Issue #34 Req 7 の View ID 不変、`FillResponseBuilderTest`,
`LockedFillResponseSecurityTest` 等）shall 本 Issue の変更後に実行したとき
すべて pass する

## Non-Functional Requirements

### NFR 1: 既存テストの不変

1. The Dataset presentation shall 既存 View ID `R.id.dataset_label` /
   `R.id.dataset_subtitle` を Issue #34 Req 7.2 のルールに従い保持する
2. The Dataset presentation shall `FillResponseBuilder.buildLockedResponse` /
   `buildLockedDataset` / `buildUnlockedDataset` のセキュリティ境界（locked
   Dataset / `AutofillUnlockActivity` への `setAuthentication` /
   `FillResponseBuilder.PLACEHOLDER` = "••••••" 注入）を変更しない
3. When `FillResponseBuilderTest` および `LockedFillResponseSecurityTest` を
   本 Issue の変更後に実行したとき, the Android test runner shall 当該テストを
   すべて成功させる

### NFR 2: IPC 制約と性能

1. The factory shall `RemoteViews` の Binder transaction 制約（約 1MB）を超え
   ないように bitmap サイズを制限する（48dp 相当 / 4 bytes per pixel で
   1 bitmap あたり 100KB 未満の目安。具体上限は design で確定）
2. The factory shall caller package の icon 取得を毎回 `PackageManager` に
   問い合わせる（本 Issue 初期実装ではキャッシュなし。確認事項 4 採用方針）

### NFR 3: セキュリティ境界の不変

1. The factory shall 取得した caller icon bitmap 以外の情報（caller の署名 /
   バージョン等）を `RemoteViews` / `Slice` に埋め込まない
2. The factory shall icon 取得失敗時に caller `packageName` 等を Logcat に
   `warn` レベル以上で出力しない（既存 autofill 経路のログ運用と整合）

## Out of Scope

- KEYNEST ヘッダー領域の mark icon（`@drawable/ic_keynest_mark_24`）の差し替え
- 「署名一致」chip 内 shield icon（`@drawable/ic_shield_fill_16`）の差し替え
- フッター「+ 新しいクレデンシャル」行 plus icon（`@drawable/ic_plus_24`）の差し替え
- Issue #43 の `IconLoader` との統合（autofill Service は Activity lifecycle に
  紐付かないため別実装で完結する）
- locked dataset / unlocked dataset の差別化 UI（icon は両方同じ caller icon を描画）
- adaptive icon の独自 mask 描画（framework がラスタライズした bitmap を使う）
- bitmap の `LruCache` による永続キャッシュ（本 Issue は確認事項 4 の方針通り
  キャッシュなし。Service 連続呼び出しの性能観点で必要が生じれば別 Issue で扱う）
- web favicon / icon pack 等のオンライン icon 解決
- caller icon の per-credential override（例: クレデンシャル個別に上書き icon を
  保存する）
- Compose 化（既存 View / `RemoteViews` レイアウト XML を維持）
- アニメーション・遷移演出

## 確認事項 / オープン課題

> 本セクションは Issue 本文「確認事項」4 項目を整理したもの。
> 採用方針が明記されているものは本 Issue でその方針を採用する。
> 数値の最終確定や別案採用が必要な場合は design.md でエスカレーションすること。

1. **Bitmap サイズと IPC 制約**: `RemoteViews` は Binder transaction limit（約 1MB）
   に制約される。本 Issue では 48dp 相当（およそ 96〜144 px）/ 4 bytes per pixel
   想定で 1 bitmap あたり 100KB 未満を目安にする。複数 dataset を返す場合は
   累積するため、具体的な px 上限の最終確定は実装時の design で行う。

2. **旧 BitmapDrawable のみのアプリ**: `compileSdk` / `targetSdk` は API 26+ だが、
   AdaptiveIconDrawable 化されていない（matrix `BitmapDrawable` のみの）アプリも
   存在する。本 Issue では中央配置でそのまま描画する。matrix bitmap の縦横比が
   極端な場合に blue tile 上で違和感が出る可能性は許容する（独自 mask 描画は
   Out of Scope）。

3. **fallback の選び方**: `NameNotFoundException` 時の fallback として
   - (a) 既存 `@drawable/ic_key_24` を使う
   - (b) Issue #43 の `InitialLetterDrawable` パターンで頭文字を bitmap 化する
   の 2 案があるが、**本 Issue では (a) を採用する**（autofill 経路の負担を
   最小化するため、`Drawable → Bitmap` 変換ルートを 1 本にまとめる）。

4. **キャッシュ**: `KeyNestAutofillService` 内で複数 `FillRequest` が連続する
   場面で `LruCache<String, Bitmap>` による短期キャッシュは有利だが、`IconLoader`
   と異なり Service lifecycle 管理になるため別管理が必要。**本 Issue では
   キャッシュなし** で毎回 `PackageManager` 呼び出しを行う方針を採用する
   （初期実装の単純化を優先。必要が生じれば別 Issue で追加検討）。

## 関連 Issue / PR

- Issue #43 / PR #45: `IconLoader` 導入（in-app 一覧画面のアイコン表示）。
  本 Issue は autofill 経路の別実装であり、`IconLoader` との統合は Out of Scope。
- Issue #46 / PR #47: Issue #43 のアイコン表示 hotfix。`lifecycleScope` 依存で
  silent fail した教訓は autofill 経路でも参照する（Service には Activity
  lifecycle が無い前提を再確認）。
- Issue #34: `dataset_presentation.xml` 初期実装。本 Issue は同 layout の dataset
  行 ImageView を bitmap 差し替え可能化する。Issue #34 Req 7 の View ID 不変
  ルールに従う。

## 用語集

- **caller package**: autofill の入力先となるアプリ（host activity の所属パッケージ）。
  `FillRequest` の `getActivityComponent()` 等から取得する `packageName`。
- **Dataset popup**: Android Autofill framework が host activity 上に表示する
  従来の floating panel 候補リスト。`RemoteViews` で構築する。
- **Inline suggestion**: API 30+ で IME suggestion strip 上に chip として表示
  される候補。`Slice` API（`Icon.createWithBitmap(...)`）で構築する。
- **fallback icon**: caller package の icon が取得できない場合に表示する代替 icon。
  本 Issue では `@drawable/ic_key_24` を採用する。
