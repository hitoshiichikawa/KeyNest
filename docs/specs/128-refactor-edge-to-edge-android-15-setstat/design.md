# Design Document

## Overview

**Purpose**: KeyNest が Android 15 (targetSdk 35) で受けている Play Console の
`setStatusBarColor` / `setNavigationBarColor` deprecation 警告を解消するため、`Theme.KeyNest` から
非推奨 attribute を撤去し、Android 公式の edge-to-edge 方式（`enableEdgeToEdge()` +
`WindowInsetsControllerCompat` + `WindowInsets` padding）に全面移行する。`Theme.KeyNest.Translucent` を
親に持つ 3 つの Activity（AutofillUnlockActivity / PasskeyAuthActivity / PasskeyCreateActivity）は
透過 launch UX を保つため edge-to-edge 化の対象外として明示する。

**Users**: 直接的な受益者は KeyNest 利用者（API 26〜35 の全レンジ）と、将来の OS バージョンで
APK が拒否されないことを必要とするメンテナである。エンドユーザーから見たユーザー体験は
**変化しない**（視覚的等価性が NFR 2 の必須条件）。

**Impact**: 現在 themes.xml で宣言されている 4 つの非推奨 attribute
（`statusBarColor` / `navigationBarColor` / `windowLightStatusBar` / `windowLightNavigationBar`）が
撤去され、代わりに 6 つの opaque Activity が `enableEdgeToEdge()` を呼び出し、システムバーアイコンの
明暗を `WindowInsetsControllerCompat` でランタイム判定する。既存の `applySystemBarsPadding()`
ユーティリティ（`util/EdgeToEdgeInsets.kt`）はそのまま再利用し、padding 適用ロジックの重複は
発生させない。Material3ThemeMigrationTest / FontTypefaceWiringTest の不変条件
（NFR 1.1〜1.4）は変更しない。

### Goals

- Theme.KeyNest から非推奨 attribute 4 件を完全撤去する（Req 1.1, 1.2）
- 6 つの対象 Activity に `enableEdgeToEdge()` を導入し、edge-to-edge レイアウトを有効化する（Req 2.1, 2.4）
- システムバーアイコンの明暗を OS の uiMode 変化に追従して動的に切り替える（Req 3.1〜3.5）
- 透過 Activity（AutofillUnlockActivity / PasskeyAuthActivity / PasskeyCreateActivity）の現状 UX を維持する（Req 4.x）
- API 26〜35 全レンジでビジュアルおよび launch 後方互換性を保つ（Req 5.x）

### Non-Goals

- 配色トークン（`kn_*`）の変更（Out of Scope）
- Theme.KeyNest.Translucent の親テーマ（Material 2）からの移行（Out of Scope）
- `setStatusBarColor` / `setNavigationBarColor` 以外の Android 15 非推奨 API 対応（Out of Scope）
- 既存テスト（Material3ThemeMigrationTest / FontTypefaceWiringTest）の expectation 書き換え（NFR 1.4）
- Material You（dynamic color）有効化（Out of Scope）
- 新規 BaseActivity 共通親クラスの導入（後述「設計判断」で却下）

## Architecture

### Existing Architecture Analysis

- **既存 helper の存在**: `app/src/main/java/io/github/hitoshiichikawa/keynest/util/EdgeToEdgeInsets.kt` に
  `View.applySystemBarsPadding()` が既に実装されており、`systemBars() | displayCutout()` の inset を
  root view の padding に加算する形で Req 2.2 / 2.3 のコア要件は満たしている。
  6 つの opaque Activity（CredentialList / CredentialEdit / Settings / DangerZone / OssLicenses /
  AutofillEnable）と PasskeyCreateActivity は既に同 helper を `setContentView` 後に呼び出している。
- **theme での旧パス**: 現在 `Theme.KeyNest` 内で `android:statusBarColor=@android:color/transparent` /
  `android:navigationBarColor=@color/kn_bg` / `android:windowLightStatusBar=true` /
  `android:windowLightNavigationBar=true` を宣言している。これらが API 35+ で no-op になりかつ
  Play Console の警告対象になる（要件本文の Issue #128 説明）。
- **未対応箇所**: `enableEdgeToEdge()` の呼び出しがどの Activity にも無く、Android 公式の edge-to-edge
  契約として API 35+ で挙動が暗黙的になる。また、システムバーアイコンの明暗を `windowLightStatusBar`
  attribute に依存しているため、attribute 撤去後はランタイム制御が必須になる。
- **尊重すべき制約**:
  - `Theme.KeyNest` の親 `Theme.Material3.DayNight.NoActionBar`、13 個の M3 textAppearance slot、
    theme-level Manrope override（NFR 1.1〜1.3）は不変。
  - `Theme.KeyNest.Translucent` の親 `Theme.MaterialComponents.DayNight.NoActionBar` も
    Issue #24 Out of Scope 判定で維持済み。
- **解消すべき technical debt**: 旧テーマ attribute と新 edge-to-edge ヘルパが混在しており、
  両系統がどちらも有効な可能性がある。本 spec で前者を撤去し、edge-to-edge 単一経路に統一する。

### Architecture Pattern & Boundary Map

**Architecture Integration**:

- **採用パターン**: 既存の "helper utility + per-Activity call" パターンを継承し、
  edge-to-edge 初期化と icon mode 制御を `util/EdgeToEdgeInsets.kt` に統合する。
  共通 BaseActivity の導入は **採用しない**（後述判断）。
- **境界**: テーマ宣言（resource layer）と Activity onCreate ロジック（presentation layer）の
  2 レイヤに作業が閉じる。ドメイン層・ViewModel・データ層には変更を加えない。
- **既存パターンの維持**: `applySystemBarsPadding()` の inset 適用契約と
  `Theme.KeyNest` の構造（NFR 1.1〜1.3）は不変。
- **新規コンポーネントの根拠**: 既存 helper を拡張して
  `Activity.enableEdgeToEdgeWithKnDefaults()` および
  `Activity.applySystemBarIconsForCurrentMode()` の 2 関数を追加することで、各 Activity の
  onCreate の冒頭 1〜2 行で edge-to-edge 化と icon mode 制御を完結できる。

#### 設計判断 1: BaseActivity 共通化 vs 画面個別対応

| 観点 | 案 A: 共通 BaseActivity | 案 B: 拡張関数による画面個別呼び出し（採用） |
|---|---|---|
| 対象画面数 | 6 Activity | 6 Activity |
| 既存階層との整合 | 既存は AppCompatActivity 直接継承で揃っており、PasskeyAuthActivity / PasskeyCreateActivity は `@RequiresApi(UPSIDE_DOWN_CAKE)` 付きで挙動が異なる | 既存階層を破壊しない |
| Translucent Activity の除外 | BaseActivity を継承するか分岐ロジックが必要 | 対象 Activity のみで呼び出すので明示的 |
| 変更行数 | 各 Activity の `super.onCreate` 直後で同じ 1 行を呼ぶだけ | 各 Activity の `super.onCreate` 直後で同じ 1 行を呼ぶだけ |
| テスト容易性 | Base 全体のテスト追加が必要 | helper の純粋単体テストで十分 |

**採用**: 案 B（拡張関数）。理由は以下。

- 既存の `applySystemBarsPadding()` も同パターンで実装されており、整合性が高い
- PasskeyAuthActivity / PasskeyCreateActivity は edge-to-edge 化の対象外であり、共通親クラスを
  挟むと「適用しない」分岐を明示する必要が出てくる
- 既存 Activity の階層（一部は `@RequiresApi` 付き）に共通親を割り込ませる変更は spec のスコープ
  （非推奨 API 撤去）を逸脱する

#### 設計判断 2: `enableEdgeToEdge()` の導入箇所

各 opaque Activity の `super.onCreate(savedInstanceState)` の **直後** に呼ぶ。`setContentView()`
よりも前で呼ぶことが AndroidX `androidx.activity:activity-ktx:1.9.2`（既存依存、libs.versions.toml
で確認済み）の公式契約。`setContentView` 後に存在する `applySystemBarsPadding()` 呼び出しはその
ままの位置で動作する（`ViewCompat.setOnApplyWindowInsetsListener` は view tree attach 後でも有効）。

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdgeWithKnDefaults()     // 新規 helper
    binding = XxxActivityBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.root.applySystemBarsPadding() // 既存 helper（不変）
    // ...
}
```

#### 設計判断 3: `WindowInsetsControllerCompat` でのアイコン明暗制御

- `enableEdgeToEdgeWithKnDefaults()` 内で `WindowCompat.getInsetsController(window, decorView)` を取得し、
  `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` を **現在の uiMode** に応じて
  設定する。
- uiMode 判定は `resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
  Configuration.UI_MODE_NIGHT_YES` で実施。light mode のとき light bar appearance（暗いアイコン）を、
  dark mode のとき dark bar appearance（明るいアイコン）を選択する。
- Req 3.5（実行中に dark/light を切り替えたら追従）は AppCompat の dayNight 切替で Activity が
  再生成（onCreate 再実行）される既存契約により自動的に満たされる。`Activity.onConfigurationChanged`
  を override する追加対応は不要。
- API レベル別 fallback: `isAppearanceLightNavigationBars` は内部で API 27 (O_MR1) 未満では no-op
  となる（androidx.core 実装。Req 5.3）。

#### 設計判断 4: `systemBars()` insets の適用方針

- 既存 `applySystemBarsPadding()` が `systemBars() | displayCutout()` を root view padding に加算
  する契約を変更しない（Req 2.2, 2.3）。
- スクロール可能コンテンツは XML 側で `android:clipToPadding="false"` が必要だが、現状の
  layout 構造をそのまま使う（既に CredentialListActivity 等は applySystemBarsPadding 適用済みで
  動作確認されている）。本 spec では XML 変更は実施しない（layout を変更すると Out of Scope の
  「システムバー以外の UI 配色・タイポグラフィ・レイアウトの刷新」に触れるため）。Req 2.5 は
  既存 layout の clipToPadding 設定で担保する想定で、もし未設定の layout があった場合は
  Reviewer の AC 判定で追加対応する（impl-notes に明記する）。

#### 設計判断 5: Translucent な Activity の扱い

- AutofillUnlockActivity / PasskeyAuthActivity / PasskeyCreateActivity は `enableEdgeToEdge()` を
  呼ばない。
  - AutofillUnlockActivity: そもそも `setContentView` を呼ばず BiometricPrompt のみ表示する。
    edge-to-edge 適用は意味を持たず、かつ caller のシステムバー styling を変更してしまうリスク
    がある（Req 4.5）。
  - PasskeyAuthActivity: setContentView せず Authentication Dialog のみ。
  - PasskeyCreateActivity: `setContentView(R.layout.activity_passkey_create)` で確認画面を出すが、
    親テーマが `Theme.KeyNest.Translucent`（Material 2 ベース）であり edge-to-edge を強制すると
    caller の透過 launch 体験が壊れるリスクがある。現状 `applySystemBarsPadding()` も呼んでいないが、
    Issue 本文の調査と requirements.md Req 4.4 で「BiometricPrompt / Credential Manager の
    システムダイアログがクリップされない」ことが要件なので、Confirm UI 側の inset 追加は本 spec
    のスコープ外（既存の透過テーマと layout の組合せで動いている前提を尊重）。
- Translucent テーマからは元から `statusBarColor` / `navigationBarColor` 宣言は無いため、撤去対象も無い。

### Technology Stack

| Layer | Choice / Version | Role in Feature | Notes |
|-------|------------------|-----------------|-------|
| Frontend / CLI | n/a | n/a | Android-only feature |
| Backend / Services | n/a | n/a | UI presentation のみ |
| Data / Storage | n/a | n/a | データ層変更なし |
| Messaging / Events | n/a | n/a | |
| Infrastructure / Runtime | Android `androidx.activity:activity-ktx:1.9.2` | `ComponentActivity.enableEdgeToEdge()` 提供 | 既存依存（libs.versions.toml 既存） |
| Infrastructure / Runtime | Android `androidx.core:core-ktx` | `WindowCompat` / `WindowInsetsControllerCompat` / `ViewCompat` | 既存依存（applySystemBarsPadding が既に使用） |
| Resource | `app/src/main/res/values/themes.xml` | Theme.KeyNest から非推奨 attribute を撤去 | NFR 1.1〜1.3 不変条件あり |
| Test | Robolectric + Truth | Material3ThemeMigrationTest 不変条件継続 | NFR 1.4 |

## File Structure Plan

### Directory Structure

```
app/src/main/
├── res/values/themes.xml                            # MODIFIED: Theme.KeyNest から 4 attribute 撤去
└── java/io/github/hitoshiichikawa/keynest/
    ├── util/EdgeToEdgeInsets.kt                     # MODIFIED: helper を拡張（新規 2 関数を追加）
    ├── ui/list/CredentialListActivity.kt            # MODIFIED: enableEdgeToEdgeWithKnDefaults() 追加
    ├── ui/edit/CredentialEditActivity.kt            # MODIFIED: 同上
    ├── ui/settings/SettingsActivity.kt              # MODIFIED: 同上
    ├── ui/danger/DangerZoneActivity.kt              # MODIFIED: 同上
    ├── ui/oss/OssLicensesActivity.kt                # MODIFIED: 同上
    ├── ui/enable/AutofillEnableActivity.kt          # MODIFIED: 同上
    ├── autofill/unlock/AutofillUnlockActivity.kt    # NO CHANGE: 透過 launch（Req 4.x で除外）
    ├── credentialprovider/authentication/
    │   └── PasskeyAuthActivity.kt                   # NO CHANGE: 透過 launch
    └── credentialprovider/registration/
        └── PasskeyCreateActivity.kt                 # NO CHANGE: 透過 launch
```

### Modified Files

- `app/src/main/res/values/themes.xml`
  - `Theme.KeyNest` から以下 4 attribute を **削除**:
    - `<item name="android:statusBarColor">@android:color/transparent</item>`
    - `<item name="android:navigationBarColor">@color/kn_bg</item>`
    - `<item name="android:windowLightStatusBar">true</item>`
    - `<item name="android:windowLightNavigationBar">true</item>`
  - `Theme.KeyNest.Translucent` は **無変更**（元から非推奨 attribute を持たない）
- `app/src/main/java/io/github/hitoshiichikawa/keynest/util/EdgeToEdgeInsets.kt`
  - 既存 `View.applySystemBarsPadding()` は不変
  - 新規追加:
    - `fun ComponentActivity.enableEdgeToEdgeWithKnDefaults()` — `enableEdgeToEdge()` を呼び、
      `WindowInsetsControllerCompat` で uiMode に応じた icon appearance を設定
    - 内部 helper `private fun Context.isNightMode(): Boolean`
- 6 つの opaque Activity（CredentialListActivity / CredentialEditActivity / SettingsActivity /
  DangerZoneActivity / OssLicensesActivity / AutofillEnableActivity）
  - `super.onCreate(...)` 直後に `enableEdgeToEdgeWithKnDefaults()` を 1 行追加
  - import 文を 1 行追加
  - 既存の `binding.root.applySystemBarsPadding()` は不変

## Requirements Traceability

| Requirement | Summary | Components | Flows |
|-------------|---------|------------|-------|
| 1.1 | `statusBarColor` を撤去 | themes.xml | XML edit |
| 1.2 | `navigationBarColor` を撤去 | themes.xml | XML edit |
| 1.3 | `setStatusBarColor` 呼び出しなし | (既に未使用) | grep verify |
| 1.4 | `setNavigationBarColor` 呼び出しなし | (既に未使用) | grep verify |
| 1.5 | Play Console 警告消失 | themes.xml + grep verify | リリース APK 検証（手動） |
| 2.1 | edge-to-edge レイアウト | enableEdgeToEdgeWithKnDefaults | onCreate 呼び出し |
| 2.2 | systemBars insets を padding 適用 | applySystemBarsPadding (既存) | 既存契約継続 |
| 2.3 | displayCutout insets 適用 | applySystemBarsPadding (既存) | 既存契約継続 |
| 2.4 | 対象 Activity 6 件 | 6 Activity 個別変更 | 各 onCreate 編集 |
| 2.5 | スクロール可コンテンツの後ろに描画 | 既存 layout（clipToPadding） | 確認のみ |
| 3.1 | ライトモードで dark icon | enableEdgeToEdgeWithKnDefaults | uiMode 判定 → isAppearanceLightStatusBars=true |
| 3.2 | ダークモードで light icon | enableEdgeToEdgeWithKnDefaults | uiMode 判定 → isAppearanceLightStatusBars=false |
| 3.3 | ライトモードで dark nav icon | enableEdgeToEdgeWithKnDefaults | isAppearanceLightNavigationBars=true |
| 3.4 | ダークモードで light nav icon | enableEdgeToEdgeWithKnDefaults | isAppearanceLightNavigationBars=false |
| 3.5 | uiMode 切替に追従 | AppCompat dayNight 再生成契約 | onCreate 再実行で再適用 |
| 4.1 | 透過 Activity 集合の定義 | 3 Activity（AutofillUnlock / PasskeyAuth / PasskeyCreate） | 変更対象から除外 |
| 4.2 | windowIsTranslucent 維持 | Theme.KeyNest.Translucent 不変 | Theme 編集なし |
| 4.3 | 不透明 bar 背景を導入しない | 透過 Activity に enableEdgeToEdge() を呼ばない | 適用範囲制御 |
| 4.4 | BiometricPrompt 非クリップ | 透過 Activity に inset 追加せず | 適用範囲制御 |
| 4.5 | caller の bar styling を残さない | enableEdgeToEdge() を透過 Activity に適用しない | 適用範囲制御 |
| 5.1 | API 26〜34 で視覚等価 | themes.xml + helper | 旧 OS では `applySystemBarsPadding` の inset は 0、appearance flags が API <27 で no-op |
| 5.2 | API 35+ で edge-to-edge | enableEdgeToEdgeWithKnDefaults | OS 既定の透明バー描画契約 |
| 5.3 | 未サポート flag は no-op | androidx.core 実装 | API <27 で `isAppearanceLightNavigationBars` は no-op |
| 5.4 | 全 API レンジで起動成功 | themes.xml + Activity | ビルド + smoke test |
| NFR 1.1 | parent `Theme.Material3.DayNight.NoActionBar` 維持 | themes.xml | Material3ThemeMigrationTest |
| NFR 1.2 | 13 textAppearance slot 維持 | themes.xml | Material3ThemeMigrationTest |
| NFR 1.3 | Manrope override 維持 | themes.xml | FontTypefaceWiringTest |
| NFR 1.4 | 既存テスト無修正パス | 既存テスト | gradle test |
| NFR 2.1 | light モード bar 色が `@color/kn_bg` 相当 | 既定の `windowBackground=?attr/colorSurface` 経由 | 視覚確認 |
| NFR 2.2 | dark モード bar 色が `@color/kn_bg` 相当 | values-night/colors.xml | 視覚確認 |
| NFR 2.3 | nav bar 色が status bar と同基準 | edge-to-edge 既定（透明 bar） | 視覚確認 |

## Components and Interfaces

### Util Layer

#### EdgeToEdgeInsets (extended)

| Field | Detail |
|-------|--------|
| Intent | Activity に edge-to-edge を有効化し、uiMode に応じてシステムバーアイコン明暗を設定する一括 helper |
| Requirements | 2.1, 3.1, 3.2, 3.3, 3.4, 3.5, 5.3 |

**Responsibilities & Constraints**

- 主責務: `enableEdgeToEdge()` 呼び出し + icon appearance 設定の集約
- 単一の Activity スコープに閉じる純粋な拡張関数（state を持たない）
- API <27 で `isAppearanceLightNavigationBars` が no-op になる挙動は androidx.core に委譲（silent degrade, Req 5.3）

**Dependencies**

- Inbound: 6 つの opaque Activity — onCreate で 1 行呼び出し (Critical)
- Outbound: `androidx.activity.ComponentActivity#enableEdgeToEdge()` (Critical)
- Outbound: `androidx.core.view.WindowCompat#getInsetsController` (Critical)
- External: なし

**Contracts**: Service [x] / API [ ] / Event [ ] / Batch [ ] / State [ ]

##### Service Interface

```kotlin
// app/src/main/java/io/github/hitoshiichikawa/keynest/util/EdgeToEdgeInsets.kt

/**
 * Activity を edge-to-edge レイアウトに切り替え、現在の uiMode (light/dark) に応じて
 * システムバーアイコンの明暗を設定する。
 *
 * 呼び出しタイミング: super.onCreate(...) の直後、setContentView() の前。
 * 透過 Activity (Theme.KeyNest.Translucent) では呼ばないこと。
 */
fun ComponentActivity.enableEdgeToEdgeWithKnDefaults()

/** 既存（不変） */
fun View.applySystemBarsPadding()
```

- Preconditions: `ComponentActivity#onCreate` のコンテキストで呼ばれる。
- Postconditions: window が edge-to-edge、`WindowInsetsControllerCompat#isAppearanceLightStatusBars`
  / `isAppearanceLightNavigationBars` が現在の uiMode に対応する真偽値で設定される。
- Invariants: state を持たない純関数。呼び出しは idempotent（複数回呼んでも最終状態は同一）。

### Presentation Layer

#### Opaque Activities (6 件)

| Field | Detail |
|-------|--------|
| Intent | `enableEdgeToEdgeWithKnDefaults()` を onCreate で呼び、edge-to-edge レイアウトを有効化する |
| Requirements | 2.1, 2.4, 3.x, 5.2 |

**Responsibilities & Constraints**

- 主責務: edge-to-edge helper の 1 行呼び出し
- 既存の `applySystemBarsPadding()` 呼び出し位置・タイミングは不変
- 既存のビジネスロジック・ViewModel 連携・lifecycle 処理に手を入れない

**Dependencies**

- Inbound: AndroidManifest からの Activity 起動 (Critical)
- Outbound: `EdgeToEdgeInsets#enableEdgeToEdgeWithKnDefaults` (Critical)

**Contracts**: Service [ ] / API [ ] / Event [ ] / Batch [ ] / State [x]

##### State Transition

```
[Activity created] --super.onCreate--> [base init]
                                          |
                                          v
                              [enableEdgeToEdgeWithKnDefaults()] (NEW)
                                          |
                                          v
                              [binding.inflate + setContentView]
                                          |
                                          v
                              [binding.root.applySystemBarsPadding()] (既存)
                                          |
                                          v
                                    [normal flow]
```

#### Translucent Activities (3 件, NO CHANGE)

| Field | Detail |
|-------|--------|
| Intent | edge-to-edge 適用対象から除外（Req 4.x） |
| Requirements | 4.1, 4.2, 4.3, 4.4, 4.5 |

**Responsibilities & Constraints**

- AutofillUnlockActivity / PasskeyAuthActivity / PasskeyCreateActivity は `enableEdgeToEdge()` を呼ばない
- Theme.KeyNest.Translucent の親（Material 2）は不変

### Resource Layer

#### Theme.KeyNest (modified)

| Field | Detail |
|-------|--------|
| Intent | 非推奨 attribute を撤去し、edge-to-edge の前提条件を満たす |
| Requirements | 1.1, 1.2, NFR 1.1, 1.2, 1.3 |

**Responsibilities & Constraints**

- 削除: `android:statusBarColor` / `android:navigationBarColor` / `android:windowLightStatusBar` /
  `android:windowLightNavigationBar`
- 維持: 親テーマ、13 個の M3 textAppearance slot、theme-level Manrope override、
  `android:windowBackground=?attr/colorSurface`、Material3 colorScheme、Shape、Widget スタイル群

##### Resource Contract（diff）

```xml
<style name="Theme.KeyNest" parent="Theme.Material3.DayNight.NoActionBar">
    <!-- ...（既存 attribute はすべて保持）... -->

-   <item name="android:statusBarColor">@android:color/transparent</item>
-   <item name="android:navigationBarColor">@color/kn_bg</item>
-   <item name="android:windowLightStatusBar" tools:targetApi="m">true</item>
-   <item name="android:windowLightNavigationBar" tools:targetApi="o_mr1">true</item>
</style>
```

## Data Models

データモデルに変更はない（UI presentation 層のみの変更）。

## Error Handling

### Error Strategy

本機能は presentation/resource layer のみの変更で、ランタイム例外を発生させ得るパスは無い。
ただし API レベル別の silent degrade（Req 5.3）を以下のとおり明示する。

### Error Categories and Responses

- **Build-time errors**: themes.xml の構文エラーは AAPT2 が早期に検出する。CI の
  `./gradlew assembleDebug` で fail-fast。
- **Runtime no-op (Req 5.3)**: `WindowInsetsControllerCompat#isAppearanceLightNavigationBars` は
  API < 27 で内部的に no-op になる。クラッシュ・例外・ログは発生しない（androidx.core 実装契約）。
- **Test regression**: NFR 1.4 で禁止された expectation 変更が無いことは Material3ThemeMigrationTest
  / FontTypefaceWiringTest が自動的に検出する。

## Testing Strategy

- **Unit Tests** (Robolectric / pure XML pinning):
  1. Material3ThemeMigrationTest が **無修正で pass** することを確認（NFR 1.4）
  2. FontTypefaceWiringTest が **無修正で pass** することを確認（NFR 1.4）
  3. 新規 / 任意: `Theme.KeyNest` から 4 つの非推奨 attribute が消えていることを pinning する
     unit test を追加（Req 1.1, 1.2 の機械検証）。実装位置は `app/src/test/java/.../resources/`
- **Integration Tests** (deferrable):
  1. 既存 Robolectric Activity テスト群（PasskeyAuthActivityTest 等）が pass することの確認
  2. AutofillUnlockActivity の launch flow が変化していないことを既存テストで担保
- **E2E/UI Tests** (manual, AC 判定):
  1. 端末で 6 つの opaque Activity を light / dark モードでそれぞれ起動し、status bar / nav bar の
     アイコン明暗が背景に対し十分なコントラストを持つことを確認（Req 3.1〜3.5）
  2. 端末の dark mode を Activity 表示中にトグルし、再生成後にアイコンが追従することを確認（Req 3.5）
  3. AutofillUnlockActivity / PasskeyAuthActivity から BiometricPrompt が表示され、システム
     ダイアログがクリップされず、終了後に caller 側のシステムバー styling に残存変更がないことを
     確認（Req 4.4, 4.5）
  4. リリース APK を internal track にアップロードし、Play Console で edge-to-edge 警告が消失
     することを確認（Req 1.5）
- **Performance/Load**: 適用なし

## Security Considerations

本機能はシステムバー styling のみの変更で、認証・暗号・秘密情報の取り扱いに影響しない。
BiometricPrompt / Credential Manager のフロー（PasskeyAuth / PasskeyCreate / AutofillUnlock）は
edge-to-edge 適用対象から除外しているため、生体認証ダイアログの UX に変化は無い（Req 4.x）。

## Migration Strategy

resource-only / per-Activity onCreate-only の変更で、データ移行・スキーマ移行・段階リリース戦略は
不要。リリースは単一 PR で完結する。
