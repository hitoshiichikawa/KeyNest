# Implementation Plan

- [x] 1. `EdgeToEdgeInsets.kt` に edge-to-edge 初期化 helper を追加
  - 既存 `View.applySystemBarsPadding()` は不変
  - 新規 `fun ComponentActivity.enableEdgeToEdgeWithKnDefaults()` を追加:
    - `enableEdgeToEdge()` を呼ぶ
    - `WindowCompat.getInsetsController(window, window.decorView)` を取得
    - `resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
      Configuration.UI_MODE_NIGHT_YES` で uiMode 判定
    - light モード時に `isAppearanceLightStatusBars=true` /
      `isAppearanceLightNavigationBars=true`、dark モード時は両方 false
  - import: `androidx.activity.ComponentActivity`, `androidx.activity.enableEdgeToEdge`,
    `androidx.core.view.WindowCompat`, `android.content.res.Configuration`
  - KDoc に「透過 Activity (Theme.KeyNest.Translucent) では呼ばないこと」を明記
  - _Requirements: 2.1, 3.1, 3.2, 3.3, 3.4, 5.3_
  - _Boundary: EdgeToEdgeInsets_

- [ ] 2. `Theme.KeyNest` から非推奨 attribute を撤去
  - `app/src/main/res/values/themes.xml` の `Theme.KeyNest` 内から以下 4 行を削除:
    - `<item name="android:statusBarColor">@android:color/transparent</item>`
    - `<item name="android:navigationBarColor">@color/kn_bg</item>`
    - `<item name="android:windowLightStatusBar" tools:targetApi="m">true</item>`
    - `<item name="android:windowLightNavigationBar" tools:targetApi="o_mr1">true</item>`
  - `Theme.KeyNest.Translucent` は **無変更**（元から該当 attribute なし）
  - NFR 1.1〜1.3 を満たすため、親テーマ・13 textAppearance slot・Manrope override・colorScheme・
    Shape・Widget スタイル群は変更しない
  - _Requirements: 1.1, 1.2, NFR 1.1, NFR 1.2, NFR 1.3_
  - _Boundary: themes.xml_

- [ ] 3. 6 つの opaque Activity に `enableEdgeToEdgeWithKnDefaults()` 呼び出しを追加 (P)
  - 対象 Activity と編集箇所:
    - `ui/list/CredentialListActivity.kt` (super.onCreate 直後)
    - `ui/edit/CredentialEditActivity.kt` (super.onCreate 直後)
    - `ui/settings/SettingsActivity.kt` (super.onCreate 直後)
    - `ui/danger/DangerZoneActivity.kt` (super.onCreate 直後)
    - `ui/oss/OssLicensesActivity.kt` (super.onCreate 直後)
    - `ui/enable/AutofillEnableActivity.kt` (super.onCreate 直後)
  - 各 Activity の onCreate で `super.onCreate(savedInstanceState)` の **直後** に
    `enableEdgeToEdgeWithKnDefaults()` を 1 行追加
  - import 行 `io.github.hitoshiichikawa.keynest.util.enableEdgeToEdgeWithKnDefaults` を追加
  - 既存の `binding.root.applySystemBarsPadding()` 呼び出し位置は **変更しない**
  - 透過 Activity 3 件（AutofillUnlockActivity / PasskeyAuthActivity / PasskeyCreateActivity）は
    対象外（Req 4.x により edge-to-edge 不適用）
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.5, 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 5.4_
  - _Boundary: CredentialListActivity, CredentialEditActivity, SettingsActivity, DangerZoneActivity, OssLicensesActivity, AutofillEnableActivity_
  - _Depends: 1_

- [ ] 4. 非推奨 API 呼び出し不在のソースレベル pinning と既存テストの非劣化確認
  - `app/src/test/java/io/github/hitoshiichikawa/keynest/resources/` 配下に新規テストファイルを
    1 つ追加し、`themes.xml` を pure-XML scan で以下を verify:
    - `Theme.KeyNest` ブロック内に `android:statusBarColor` が含まれない（Req 1.1）
    - `Theme.KeyNest` ブロック内に `android:navigationBarColor` が含まれない（Req 1.2）
    - `setStatusBarColor` / `setNavigationBarColor` の文字列が `app/src/main/java` 配下の
      production source に存在しない（Req 1.3, 1.4）— File walk + 文字列検索
  - 既存 `Material3ThemeMigrationTest` / `FontTypefaceWiringTest` を改変せず pass することで
    NFR 1.1〜1.4 を担保（テストファイルは変更禁止）
  - Req 1.5（Play Console 警告消失）は手動 AC として impl-notes.md に記録
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, NFR 1.4_
  - _Boundary: themes.xml, EdgeToEdgeInsets_

## Verify

本 spec の実装後、watcher（stage-a-verify gate）が再実行すべき verify コマンドを以下に宣言する。

<!-- stage-a-verify -->
```sh
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```
