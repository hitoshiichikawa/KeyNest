# Requirements Document

## Introduction

KeyNest は targetSdk 35 (Android 15) で動作しており、Google Play Console から
`android:statusBarColor` / `android:navigationBarColor` および対応する Window setter API
が Android 15 で非推奨かつ no-op になる旨の警告を受けている。これらは現在 `Theme.KeyNest`
の theme attribute として宣言されているのみで、Java / Kotlin 側に直接の setter 呼び出しは
無いが、theme attribute も実行時に setter 経由で適用されるため Play Console の警告対象に
含まれる。本 Issue では theme から該当 attribute を撤去し、Android 公式の edge-to-edge
方式（`enableEdgeToEdge()` + `WindowInsetsControllerCompat` + `WindowInsets` padding）に
全面移行する。視覚的なリグレッション（システムバー配色・アイコン濃淡・コンテンツ被り）を
ライト／ダーク両モード、API 26〜35 の全サポート範囲で発生させないことを必須要件とする。

## Requirements

### Requirement 1: 非推奨 API の撤去

**Objective:** As a KeyNest maintainer, I want 非推奨となった Window 配色 setter とそれを
誘発する theme attribute を撤去したい, so that Play Console の Android 15 警告が解消し、
将来のサポート対象 OS で APK が拒否されない

#### Acceptance Criteria

1. The Theme.KeyNest shall not declare `android:statusBarColor`
2. The Theme.KeyNest shall not declare `android:navigationBarColor`
3. The KeyNest application shall not call `Window.setStatusBarColor` from any production code path
4. The KeyNest application shall not call `Window.setNavigationBarColor` from any production code path
5. When the release APK is uploaded to Play Console, the Console shall not report edge-to-edge deprecation warnings related to `setStatusBarColor` or `setNavigationBarColor`

### Requirement 2: edge-to-edge レイアウト適用

**Objective:** As a KeyNest end user on Android 15, I want アプリのコンテンツが画面端まで
描画されつつ操作可能領域がシステムバーに隠れない状態を保ちたい, so that 配色トランジションが
途切れず、かつタップターゲットが常に視認・操作可能である

#### Acceptance Criteria

1. When 対象 Activity が起動したとき, the KeyNest UI shall draw content behind the system status bar and navigation bar (edge-to-edge)
2. While 対象 Activity が前面表示されている間, the KeyNest UI shall apply system bar insets as padding to interactive root content so that no interactive element is occluded by the status bar or navigation bar
3. While 対象 Activity が表示されている間, the KeyNest UI shall apply display cutout insets in addition to system bar insets when running on devices with cutouts
4. The set of Activity affected by this requirement shall be CredentialListActivity, CredentialEditActivity, SettingsActivity, DangerZoneActivity, OssLicensesActivity, and AutofillEnableActivity
5. Where 対象 Activity がスクロール可能なコンテンツを含む場合, the KeyNest UI shall allow scrolled content to render behind the system bars while keeping the scroll handles and final content reachable beyond the inset region

### Requirement 3: システムバーアイコンの視認性

**Objective:** As a KeyNest end user, I want システムバーのアイコン（時計・電池・戻る等）が
背景配色に対し十分なコントラストで見えること, so that OS の状態情報が常に判読できる

#### Acceptance Criteria

1. While 端末がライトモードで動作している間, the KeyNest UI shall render the status bar icons in the dark (light-status-bar) appearance
2. While 端末がダークモードで動作している間, the KeyNest UI shall render the status bar icons in the light (dark-status-bar) appearance
3. While 端末がライトモードで動作している間, the KeyNest UI shall render the navigation bar icons / gesture handle in the dark (light-navigation-bar) appearance on devices supporting that mode
4. While 端末がダークモードで動作している間, the KeyNest UI shall render the navigation bar icons / gesture handle in the light (dark-navigation-bar) appearance on devices supporting that mode
5. When the user toggles system dark mode while a KeyNest Activity is visible, the KeyNest UI shall update the system bar icon appearance to match the new mode without requiring app restart

### Requirement 4: 透過 Activity 系の挙動維持

**Objective:** As a KeyNest end user invoking Autofill / Credential Manager flows, I want
生体認証ダイアログを透過的に表示する Activity が現状と同じ UX を保ちたい, so that 既存の
Autofill / PassKey フローが視覚的・機能的にリグレッションを起こさない

#### Acceptance Criteria

1. The set of translucent Activity governed by this requirement shall be AutofillUnlockActivity, PasskeyAuthActivity, and PasskeyCreateActivity
2. While 透過 Activity が表示されている間, the KeyNest UI shall preserve the windowIsTranslucent transparent-launch behaviour currently provided by Theme.KeyNest.Translucent
3. While 透過 Activity が表示されている間, the KeyNest UI shall not introduce an opaque status bar or navigation bar background that overlays the underlying caller surface
4. While 透過 Activity が前面にある間, the KeyNest UI shall keep the BiometricPrompt / Credential Manager system dialog fully visible and not clipped by added insets
5. When the translucent Activity finishes, the KeyNest UI shall restore control to the caller without leaving residual system bar styling changes applied to the caller window

### Requirement 5: 後方互換性 (API 26〜35)

**Objective:** As a KeyNest end user on any supported Android version, I want アプリの見た目と
操作性が OS バージョンによらず一貫すること, so that 古い端末利用者が新しい挙動の犠牲にならない

#### Acceptance Criteria

1. While 対象 Activity が API level 26 から 34 の端末で動作している間, the KeyNest UI shall remain visually equivalent to the pre-change behaviour with no user-perceivable regression in system bar colouring, icon contrast, or content layout
2. While 対象 Activity が API level 35 以上の端末で動作している間, the KeyNest UI shall apply the edge-to-edge layout defined in Requirement 2 and Requirement 3
3. If the running API level does not support a specific window appearance flag (例: light navigation bar on API < 27), the KeyNest UI shall degrade silently and shall not crash or log an error
4. The KeyNest application shall continue to launch successfully on the full supported API range (26 through 35) after the change

## Non-Functional Requirements

### NFR 1: 既存テスト・既存テーマ不変条件の維持

1. The KeyNest application shall keep `Theme.KeyNest` inheriting directly from `Theme.Material3.DayNight.NoActionBar` (no intermediate Base style)
2. The KeyNest application shall keep the 13 M3 textAppearance slot bindings declared on `Theme.KeyNest` intact
3. The KeyNest application shall keep the theme-level Manrope font override (`android:fontFamily` / `fontFamily`) declared on `Theme.KeyNest` intact
4. The KeyNest test suite (Material3ThemeMigrationTest, FontTypefaceWiringTest, and other pre-existing theme invariants) shall continue to pass without modification of test expectations

### NFR 2: 視覚的等価性の測定可能性

1. While 対象 Activity がライトモードで表示されている間, the KeyNest UI shall present a status bar background colour visually indistinguishable from `@color/kn_bg` in light mode (current baseline)
2. While 対象 Activity がダークモードで表示されている間, the KeyNest UI shall present a status bar background colour visually indistinguishable from `@color/kn_bg` in dark mode (current baseline)
3. The navigation bar background colour shall match the same baseline as the status bar background under both modes

## Out of Scope

- 配色トークン (`kn_*` colour palette) 自体の変更
- システムバー以外の UI 配色・タイポグラフィ・レイアウトの刷新
- `setStatusBarColor` / `setNavigationBarColor` 以外の Android 15 非推奨 API への対応
- Material You (dynamic color) の有効化
- Theme.KeyNest.Translucent の親テーマ（Material 2）からの移行
- Play Console 警告解消以外の Play Console 起因の改善対応
- 既存 spec で網羅されている FontTypefaceWiringTest / Material3ThemeMigrationTest の不変条件の変更
- 既存 unit test / instrumentation test の expectation 書き換え（前述 NFR 1.4 で禁止）

## Open Questions

- なし（Issue 本文と関連ファイル調査で要件は確定できた）

## 関連

- Parent: #128
