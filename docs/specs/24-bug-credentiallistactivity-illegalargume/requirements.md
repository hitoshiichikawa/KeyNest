# Requirements Document

## Introduction

KeyNest はアプリ起動直後（cold start）に `CredentialListActivity` が
`IllegalArgumentException: This component requires that you specify a valid TextAppearance
attribute` でクラッシュする状態にある。原因は、アプリのテーマ `Theme.KeyNest` が Material 2
系（`Theme.MaterialComponents.DayNight.NoActionBar`）を parent としているのに対し、
4 つのレイアウトファイル（`credential_list_activity.xml` / `settings_activity.xml` /
`danger_zone_activity.xml` / `oss_licenses_item.xml`）で **Material 3 系の `Widget.Material3.*`
スタイルおよび `?attr/textAppearanceTitleMedium` / `?attr/textAppearanceBodyLarge` 等の M3 系
TextAppearance attribute** が参照されていることである。M3 系 widget は M3 系 theme の
TextAppearance attribute（`textAppearanceTitleMedium` 等）を解決できる前提で動作するため、
M2 系 theme 上で inflate するとクラッシュする。

Issue #24 では、アプリ全体のテーマを Material 3（`Theme.Material3.DayNight.NoActionBar`）に
移行する方針が確定方針として採用されている。これにより、(a) `themes.xml` の `Theme.KeyNest`
の parent を M3 系に変更し、(b) Issue #13 で導入された 13 個の `TextAppearance.KeyNest.*` の
parent を M2（`TextAppearance.MaterialComponents.*`）から M3（`TextAppearance.Material3.*`）に
変更し、(c) `Theme.KeyNest` 内の textAppearance attribute 名 13 個を M2 系名から M3 系名に
置換することで、4 レイアウト × 17 箇所の `Widget.Material3.*` 参照をそのまま温存しつつ
クラッシュを解消する。

本要件は、(1) 起動時クラッシュの解消、(2) Issue #13（Manrope 同梱）で確定済みの typeface
適用挙動の維持、(3) M3 移行による視覚的崩れの不発生、を達成範囲とする。M3 の追加カラー
ロール導入・Dynamic Color 対応・4 レイアウト内の widget 構成変更は本 Issue のスコープ外とする。

## Requirements

### Requirement 1: 起動時クラッシュの解消

**Objective:** As a KeyNest 利用者, I want アプリを起動した直後にホーム画面（credential 一覧）が
正常に表示されることを期待する, so that KeyNest を実用できる状態に戻る

#### Acceptance Criteria

1.1. When ユーザーが端末上で KeyNest アイコンをタップしてアプリを cold start したとき, the KeyNest App shall `CredentialListActivity` を `IllegalArgumentException` を発生させずに inflate し、credential 一覧画面を表示する。

1.2. When ユーザーが KeyNest を cold start したとき, the KeyNest App shall `This component requires that you specify a valid TextAppearance attribute` を含む `IllegalArgumentException` を Logcat に出力しない。

1.3. While Material 3 系の `Widget.Material3.*` スタイル（合計 17 箇所、4 レイアウト：`credential_list_activity.xml` / `settings_activity.xml` / `danger_zone_activity.xml` / `oss_licenses_item.xml`）がレイアウトファイル内で参照され続けている状態で, the KeyNest App shall それらのスタイル参照を解決できる theme コンテキストで Activity を起動する。

1.4. While Material 3 系の TextAppearance attribute（`?attr/textAppearanceTitleMedium` / `?attr/textAppearanceBodyLarge` / `?attr/textAppearanceBodyMedium` / `?attr/textAppearanceTitleSmall` / `?attr/textAppearanceBodySmall` 等）がレイアウトファイル内で参照され続けている状態で, the KeyNest App shall それらの attribute を解決できる theme コンテキストで Activity を起動する。

### Requirement 2: アプリテーマの Material 3 への移行

**Objective:** As a KeyNest 開発者, I want アプリの基底テーマを Material 3 系に揃える, so that レイアウト内の M3 widget 参照と theme attribute の整合が取れ、同種クラッシュを将来にわたり発生させない

#### Acceptance Criteria

2.1. The KeyNest App shall `Theme.KeyNest` の parent を、Material 3 の DayNight・NoActionBar 系テーマに変更する。

2.2. The KeyNest App shall `Theme.KeyNest` 内で参照されている TextAppearance attribute 名 13 個（Issue #13 で `Theme.KeyNest` に追加された M2 系 `textAppearanceHeadline1`〜`textAppearanceOverline` の 13 個）を、Material 3 type scale 対応に沿った M3 系 TextAppearance attribute 名に置換する。

2.3. The KeyNest App shall Issue #13 Requirement 1 / 2 で導入された 13 個の `TextAppearance.KeyNest.*` スタイルの parent を、Material 2 系の `TextAppearance.MaterialComponents.*` から Material 3 系の `TextAppearance.Material3.*` に変更する。

2.4. While 13 個の `TextAppearance.KeyNest.*` スタイルが parent を M3 系に切り替えた状態で, the KeyNest App shall 各スタイルの `android:fontFamily` / `fontFamily` の Manrope 指定（Issue #13 Req 1.2 で導入）を維持する。

2.5. The KeyNest App shall M2 系 ↔ M3 系の type scale 対応関係（Material Design の公開対応表に従う）に基づいて 13 個の TextAppearance / textAppearance attribute を置換し、独自の対応関係を発明しない。

### Requirement 3: 既存レイアウトおよび typeface 挙動の保持

**Objective:** As a KeyNest 利用者, I want 起動できるようになった後も、Manrope の表示と各画面のレイアウト・動線が従前どおりであることを期待する, so that 本修正によって新たな視覚的・機能的リグレッションが発生しない

#### Acceptance Criteria

3.1. The KeyNest App shall `credential_list_activity.xml` / `settings_activity.xml` / `danger_zone_activity.xml` / `oss_licenses_item.xml` の 4 レイアウトファイルにおける `Widget.Material3.*` スタイル参照（合計 17 箇所）を書き換えない。

3.2. The KeyNest App shall 4 レイアウトファイルにおける M3 系 TextAppearance attribute 参照（`?attr/textAppearanceTitleMedium` / `?attr/textAppearanceBodyLarge` / `?attr/textAppearanceBodyMedium` / `?attr/textAppearanceTitleSmall` / `?attr/textAppearanceBodySmall` 等）を書き換えない。

3.3. When ユーザーが credential 一覧・credential 編集・Settings・Danger Zone・OSS ライセンス・Autofill 有効化導線・package picker のいずれかの画面を開いたとき, the KeyNest App shall Manrope typeface（Issue #13 Req 1.2 で導入）が本文・見出し・ボタンラベルに適用された状態で表示する。

3.4. The KeyNest App shall 本修正によって、MVP / Issue #9 / Issue #10 / Issue #12 / Issue #13 / Issue #14 で確定済みの画面遷移・入力検証・コピー導線・並び替え・検索・Autofill 候補表示・Vault アンロック・SHA-256 表示・onboarding 導線の挙動を変更しない。

3.5. The KeyNest App shall `Theme.KeyNest.Translucent`（package picker bottom sheet 用）の起動・透過挙動を本修正の前後で同等に保つ。

### Requirement 4: 主要画面の動作検証

**Objective:** As a KeyNest 開発者, I want 本修正後に主要 7 画面が確実に起動・表示できることを保証されたい, so that 修正の副作用として別画面が壊れていないことを確認できる

#### Acceptance Criteria

4.1. When ユーザーが KeyNest を cold start したとき, the KeyNest App shall credential 一覧画面（`CredentialListActivity`）を初期描画する。

4.2. When ユーザーが credential 一覧画面から credential を新規追加または既存 credential を編集するために遷移したとき, the KeyNest App shall credential 編集画面（`CredentialEditActivity`）を初期描画する。

4.3. When ユーザーが Settings 画面（`SettingsActivity`）へ遷移したとき, the KeyNest App shall Settings 画面を初期描画する。

4.4. When ユーザーが Settings 画面から Danger Zone 画面（`DangerZoneActivity`）へ遷移したとき, the KeyNest App shall Danger Zone 画面を初期描画する。

4.5. When ユーザーが Settings 画面から OSS ライセンス画面（`OssLicensesActivity`）へ遷移したとき, the KeyNest App shall OSS ライセンス画面を初期描画する。

4.6. When ユーザーが Autofill Service 有効化導線（`AutofillEnableActivity`）へ遷移したとき, the KeyNest App shall Autofill 有効化画面を初期描画する。

4.7. When ユーザーが package name 選択用 bottom sheet を起動したとき, the KeyNest App shall package picker bottom sheet を初期描画する。

4.8. While 端末のシステム設定がライトモードの状態で, the KeyNest App shall 上記 7 画面（Req 4.1〜4.7）を `IllegalArgumentException` を発生させずに初期描画する。

4.9. While 端末のシステム設定がダークモードの状態で, the KeyNest App shall 上記 7 画面（Req 4.1〜4.7）を `IllegalArgumentException` を発生させずに初期描画する。

### Requirement 5: ビルドおよび既存テストの保全

**Objective:** As a KeyNest 開発者, I want 本修正後にビルドおよび既存テストが従前どおり成功することを保証されたい, so that CI / リリースパイプラインへの後退影響がない

#### Acceptance Criteria

5.1. When 開発者が `./gradlew assembleDebug` を実行したとき, the KeyNest Build Pipeline shall ビルドを失敗させない。

5.2. When CI が既存の単体テストスイートを実行したとき, the KeyNest Test Suite shall 本修正以前から pass している全テストを引き続き pass させる。

5.3. If 既存テストの assert 内容が M2 系 TextAppearance を直接前提としていて本修正で破綻するケースが見つかったとき, the KeyNest Development Process shall そのテストを「テストを通すために assert を緩める」のではなく、現実の挙動を表現するように更新するか、当該テストの維持可否を `Open Questions` または PR 本文「確認事項」で人間判断にエスカレートする。

## Non-Functional Requirements

### NFR 1: 視覚的回帰の抑制

1.1. The KeyNest App shall M3 type scale 対応表に基づく TextAppearance 置換の結果として発生する、本文・見出し・ボタンラベルのフォントサイズ・行間・letter spacing の変化を、ユーザーが「画面が壊れて見える」と認識する規模（テキストが UI 要素からはみ出る・改行が崩れて読めない・タップターゲットが消失する 等）にしない。

1.2. The KeyNest App shall ライトモード／ダークモード双方で、本修正の前後でテキスト色とコントラスト比を WCAG 2.1 AA 相当（通常テキスト 4.5:1、大テキスト 3:1）の基準を下回らせない。

### NFR 2: typeface 整合性の維持

2.1. The KeyNest App shall 本修正の前後で、Issue #13 で確定した「Manrope を本文・見出し・ボタンラベルに適用する」挙動を維持し、Material 3 既定の typeface（Roboto / Roboto Flex 系）へのフォールバック表示を発生させない。

### NFR 3: クラッシュ非発生

3.1. While アプリが起動から credential 一覧画面の初期描画を完了するまでの間, the KeyNest App shall TextAppearance attribute 解決に起因する `IllegalArgumentException` を発生させない。

## Out of Scope

以下は本 Issue では実装しない。

- **Material 3 のフルカラーロール（`colorPrimaryContainer` / `colorOnPrimaryContainer` /
  `colorSurfaceVariant` / `colorOutline` 等）の追加導入**: 本 Issue は起動クラッシュ解消に
  必要な theme parent / TextAppearance 整合のみを対象とし、M3 カラーパレット全体の再構築は
  行わない。Issue #13 で導入済みの `colorPrimary` / `colorOnPrimary` / `colorSecondary` /
  `colorSurface` の参照は維持する。
- **Dynamic Color（Android 12+ の `DynamicColors.applyToActivitiesIfAvailable()` / Material You 連動）対応**:
  ユーザー壁紙に応じた色変更は本 Issue では導入しない。
- **17 箇所の `Widget.Material3.*` スタイル参照の書き換え**: 4 レイアウトファイル内で既に
  M3 widget を参照している箇所を別 widget スタイルに差し替える作業は行わない（Req 3.1）。
- **4 レイアウトファイルの widget 構成・階層構造の変更**: layout XML の view tree や
  ConstraintLayout 制約・margin / padding 値の変更は本 Issue では行わない（Req 3.2）。
- **Compose Theme への移行**: 本 Issue は XML theme 内のクラッシュ修正に閉じる。
  Jetpack Compose / `MaterialTheme` 化は別 Issue で扱う。
- **`Theme.KeyNest.Translucent` の parent 変更**: 本 Issue は `Theme.KeyNest` 本体のクラッシュ
  解消が目的であり、`Theme.KeyNest.Translucent`（package picker 用）の parent 変更が
  クラッシュ解消に不要であれば変更しない（Req 3.5 で挙動保持のみ規定）。必要性が判明した
  場合は設計フェーズで判断する。
- **`TextAppearance.KeyNest.*` スタイル名そのものの改名**: 13 個のスタイル名（`Headline1` 等の
  M2 命名）を M3 命名（`HeadlineLarge` 等）に rename する作業は行わない。parent と
  `Theme.KeyNest` 側 attribute 名のみ M3 系に切り替える。
- **アプリ内の他 typeface（JetBrains Mono）への影響範囲再評価**: JetBrains Mono は
  Issue #13 / Issue #14 のスコープであり、本 Issue では参照しない。

## Open Questions

以下は本 Issue 範囲内では確定させず、設計フェーズ（Developer 着手前）または PR 本文の
「確認事項」で人間判断にエスカレートする事項。

- **M2 → M3 type scale の具体対応**: Issue 本文では「Material 3 type scale 対応表に沿った
  置換」と方針のみ指定されており、`Headline1`〜`Overline`（13 種）→ `DisplayLarge` /
  `HeadlineLarge` / `TitleLarge` / `BodyLarge` 等への具体的なマッピングは Material Design
  公式対応表に従う。曖昧さが残る対応（例: M2 `Subtitle1` / `Subtitle2` → M3 `TitleMedium` /
  `TitleSmall` 等の判断）は設計フェーズで確定する。本要件 Req 2.5 では公開対応表に従う
  ことを規定するに留める。
- **`Theme.KeyNest` の M3 parent 選択**: `Theme.Material3.DayNight.NoActionBar` を採用するか、
  `Theme.Material3.Light.NoActionBar` / `Theme.Material3.Dark.NoActionBar` の組合せ（`values-night/`
  分割）を採用するかは設計フェーズで判断する。本要件 Req 2.1 では DayNight 系であることを
  規定するに留める。
- **既存テストの破綻有無**: 本修正で `CredentialListActivityTest` および他の Activity 単体・
  UI テストの assert が破綻するか否かは、実装着手後に判明する。破綻が発生した場合の対応は
  Req 5.3 に従う。
