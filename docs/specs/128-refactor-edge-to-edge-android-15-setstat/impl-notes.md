# Implementation Notes

## Requirement Traceability

- Req 1.1, 1.2: Task 2 で `Theme.KeyNest` から非推奨 attribute 4 行を撤去。Task 4 の
  `DeprecatedSystemBarApiRemovalTest.themeKeyNest_doesNotDeclareAndroidStatusBarColor` /
  `themeKeyNest_doesNotDeclareAndroidNavigationBarColor` で `<item name="android:statusBarColor"` /
  `<item name="android:navigationBarColor"` の anchor をテーマ宣言から pinning。
- Req 1.3, 1.4: 元から `setStatusBarColor` / `setNavigationBarColor` の production source
  呼び出しは存在しないが、Task 4 の `productionSources_doNotCallSetStatusBarColor` /
  `productionSources_doNotCallSetNavigationBarColor` で `app/src/main/java` 配下を file walk
  + 文字列検索する pinning テストを追加し、回帰を防止。
- Req 1.5: Play Console 警告消失は **手動 AC**。次リリース時に internal track アップロード後の
  Play Console 上で edge-to-edge 警告が消えていることを人間が目視確認する。
- Req 2.1, 3.1, 3.2, 3.3, 3.4, 5.3: Task 1 で追加した `ComponentActivity.enableEdgeToEdgeWithKnDefaults()`
  により、edge-to-edge 化と uiMode 連動の icon appearance 設定を一括提供。Req 5.3 の API <27 silent
  degrade は androidx.core 内部実装に委譲（クラッシュなし）。
- Req 2.2, 2.3: 既存 `View.applySystemBarsPadding()` を不変のまま 6 Activity で再利用
  （Task 3）。`systemBars() | displayCutout()` を root view padding に加算する契約を維持。
- Req 2.4: Task 3 で 6 opaque Activity（CredentialList / CredentialEdit / Settings / DangerZone /
  OssLicenses / AutofillEnable）の onCreate 直後に helper を 1 行追加。
- Req 2.5: 既存 layout の `clipToPadding` 設定をそのまま使用（design.md の判断通り XML 変更なし）。
- Req 3.5: AppCompat の dayNight 切替で Activity が再生成される既存契約により、helper が
  onCreate で再実行され icon appearance も追従。
- Req 4.1, 4.2, 4.3, 4.4, 4.5: Task 3 で透過 Activity 3 件（AutofillUnlock / PasskeyAuth /
  PasskeyCreate）には helper を呼ばないことで透過 launch UX を保持。helper の KDoc にも
  「呼ばないこと」と明示。
- Req 5.1: Task 2 で API 26〜34 の旧 OS では `applySystemBarsPadding` の inset は 0、appearance
  flags が API <27 で no-op となり視覚等価性を保持。
- Req 5.2: Task 3 で API 35+ で `enableEdgeToEdge()` 呼び出しにより OS 既定の透明バー描画契約に乗る。
- Req 5.4: `:app:assembleDebug` 成功で全 API レンジ向けビルドが通ることを確認。
- NFR 1.1, 1.2, 1.3: Task 2 で `Theme.KeyNest` の親テーマ・13 textAppearance slot・Manrope
  override に手を入れていない。既存 `Material3ThemeMigrationTest` / `FontTypefaceWiringTest` が
  Task 4 でも無修正で pass することで invariant を担保。
- NFR 1.4: Task 4 で既存テストファイル（Material3ThemeMigrationTest / FontTypefaceWiringTest 等）
  を一切改変せず、新規 `DeprecatedSystemBarApiRemovalTest.kt` を 1 つ追加するのみ。
- NFR 2.1, 2.2, 2.3: themes.xml の `android:windowBackground=?attr/colorSurface` と
  values-night/colors.xml の semantic 色定義により、light/dark 両モードで bar 背景が `kn_bg`
  相当に解決される（手動視覚確認は Req 1.5 と同様に次リリース時に実施）。

## Implementation Notes

### Task 1

- 採用方針: design.md の Service Interface 通り、既存 `View.applySystemBarsPadding()` を温存しつつ
  同ファイル `util/EdgeToEdgeInsets.kt` に `ComponentActivity` の拡張関数を 1 つ追加する形を採用。
- 重要な判断: `WindowCompat.getInsetsController(window, window.decorView)` は `ComponentActivity` の
  thisRef を介して `window` プロパティを参照できる（`Activity` 由来）。light/dark の真理値は
  `isAppearanceLightStatusBars = !isNightMode` の単一式で揃えて統一感を持たせた。design.md には
  独立 `private fun Context.isNightMode()` を切る案もあったが、現状の単一呼び出しでは inline 式の
  方が読みやすいと判断し、helper 化は見送り（必要なら後続タスクで refactor 可能）。
- 残存課題: なし（後続 task 3 で本 helper を 6 Activity の onCreate に組み込む）。

### Task 2

- 採用方針: design.md の File Structure Plan 通り、`Theme.KeyNest` 内の非推奨 4 attribute
  (`android:statusBarColor` / `android:navigationBarColor` / `android:windowLightStatusBar` /
  `android:windowLightNavigationBar`) のみを削除。親テーマ・13 textAppearance slot・
  Manrope override・colorScheme・Shape・Widget スタイル群は一切触れず NFR 1.1〜1.3 を担保。
- 重要な判断: 削除した 4 行の上にあった日本語コメント（"Phase 2 の前段階として system bar
  配色を kn_bg に揃える…"）も同時に Issue #128 由来の説明に置換した。元コメントは削除した
  attribute の存在意義を説明するもので、attribute と一体で意味を成すため孤立残置すると
  逆に混乱を招くと判断。新コメントでは「Android 15 で非推奨化したため撤去し、edge-to-edge
  と icon appearance は `enableEdgeToEdgeWithKnDefaults()` 経由で設定する」と次の参照先を
  明示することで、後続タスクとの繋がりを残す。`Theme.KeyNest.Translucent` は元から該当
  attribute なしのため touch せず（design.md 記載通り）。
- 残存課題: なし（task 3 で 6 opaque Activity の onCreate に helper を組み込む、task 4 で
  XML scan テスト追加でこの削除を pinning する予定）。

### Task 3

- 採用方針: tasks.md の指示通り、6 opaque Activity 全てで `super.onCreate(savedInstanceState)`
  の直後 (`binding = ...Inflate()` の前) に `enableEdgeToEdgeWithKnDefaults()` を 1 行追加。
  import は既存の `applySystemBarsPadding` と同 package のため隣接して alphabetical order
  (`applySystemBarsPadding` < `enableEdgeToEdgeWithKnDefaults`) で挿入。
- 重要な判断: 既存の `binding.root.applySystemBarsPadding()` 呼び出し位置はそのまま温存
  （tasks.md 明示）。これにより edge-to-edge 化 → inset 取得 → root に padding 加算という
  順序が成立し、Issue #128 の Req 2.x (edge-to-edge 切替) と Req 3.5 (icon appearance) が
  6 Activity すべてで同一パスで担保される。透過 Activity 3 件 (AutofillUnlockActivity /
  PasskeyAuthActivity / PasskeyCreateActivity) は Theme.KeyNest.Translucent を使用しており
  helper の KDoc にも「呼ばないこと」と明示されているため対象外。assembleDebug と
  testDebugUnitTest は pass を確認。lintDebug は task 3 と無関係な既存問題
  (`PackageSignatureResolver.kt:52` の NewApi error。最終更新 Issue #106、本 task で touch
  していない) で failure するが、task 3 で導入した変更には lint 警告ゼロ。
- 残存課題: なし（task 4 で themes.xml の pure-XML scan + `setStatusBarColor` /
  `setNavigationBarColor` の文字列検索テストを追加して Req 1.x を pinning する予定）。

### Task 4

- 採用方針: tasks.md の指示通り、`app/src/test/java/io/github/hitoshiichikawa/keynest/resources/`
  配下に新規 `DeprecatedSystemBarApiRemovalTest.kt` を 1 つだけ追加し、既存
  `Material3ThemeMigrationTest` / `FontTypefaceWiringTest` には一切手を入れない方針。
  既存テスト 2 件は無修正で pass することを `:app:testDebugUnitTest` で確認済み（NFR 1.1〜1.4
  担保）。テストは Robolectric を起こさない pure-XML scan + Kotlin file walk のみで構成し、
  既存 `Material3ThemeMigrationTest` / `BundledFontResourcesTest` と同じ単純テキスト走査
  パターンに揃えた（spec の Testing Strategy 通り）。
- 重要な判断: 当初 Req 1.1 / 1.2 のチェックを「Theme.KeyNest ブロック内に `android:statusBarColor`
  文字列が含まれない」という素朴な substring 検査で書いたところ、themes.xml の Theme.KeyNest
  ブロック内に Task 2 で追加した「Android 15 で `android:statusBarColor` / ... が非推奨化した
  ため撤去」という日本語コメントが残っており false-positive で fail した。`<item name="…">`
  という XML attribute 宣言の anchor 文字列に絞ることで、コメント内の言及と実宣言を弁別。
  Task 2 のコメントは attribute 撤去の意図（後続参照先 `enableEdgeToEdgeWithKnDefaults()`）を
  示す意義あるドキュメントなので、コメント側を削るのではなく、テスト側で正確な anchor を
  選ぶアプローチを採用した。Req 1.3 / 1.4 は `app/src/main/java` 限定の file walk で実施し、
  test source 配下（本テストファイル自身が `setStatusBarColor` / `setNavigationBarColor` を
  文字列リテラルとして含む）と `build/` 配下を自然に除外。Req 1.5（Play Console 警告消失）は
  unit test のスコープ外で、リリース APK の internal track アップロード後に人間が Play
  Console を目視確認する **手動 AC**。次リリース時の手動チェックポイントとして本 notes に
  明記する。CI / 自動 verify では担保しない。
- 残存課題: Req 1.5 のみ手動 AC として次リリース時に確認が必要（上記）。`lintDebug` は task 3
  learning で記録済みの既存 `PackageSignatureResolver.kt:52` NewApi error で失敗するが、本
  task 4 で touch した範囲（test source 1 ファイル新規追加 + impl-notes 更新 + tasks.md
  marker 更新）には lint 違反ゼロ。verify として `:app:assembleDebug :app:testDebugUnitTest`
  両方 pass を確認。

## 確認事項

- なし

STATUS: complete
