# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-04T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-128-impl-refactor-edge-to-edge-android-15-setstat
- HEAD commit: d50b5dbb5cabc00ef98c107cb8113d68fff9707d
- Compared to: develop..HEAD (full spec HEAD review)

## Verified Requirements

- 1.1 — themes.xml diff で `<item name="android:statusBarColor">@android:color/transparent</item>` を削除。`DeprecatedSystemBarApiRemovalTest.themeKeyNest_doesNotDeclareAndroidStatusBarColor` で Theme.KeyNest ブロックを切り出して pinning
- 1.2 — themes.xml diff で `<item name="android:navigationBarColor">@color/kn_bg</item>` を削除。`DeprecatedSystemBarApiRemovalTest.themeKeyNest_doesNotDeclareAndroidNavigationBarColor` で pinning（`"Theme.KeyNest" parent` でスライスし Translucent と弁別）
- 1.3 — `DeprecatedSystemBarApiRemovalTest.productionSources_doNotCallSetStatusBarColor` が `app/src/main/java` を file walk して文字列検索（test 配下と build/ は除外）
- 1.4 — `DeprecatedSystemBarApiRemovalTest.productionSources_doNotCallSetNavigationBarColor` で同様の pinning
- 1.5 — 手動 AC として impl-notes.md「Task 4」「Requirement Traceability」に記録。Play Console での目視確認は次リリース時の人間チェックポイント — 規約として許容
- 2.1 — `EdgeToEdgeInsets.kt::enableEdgeToEdgeWithKnDefaults()` が `enableEdgeToEdge()` を呼び出す。6 opaque Activity の `super.onCreate(...)` 直後で呼ばれている
- 2.2 — 既存 `View.applySystemBarsPadding()` は diff hunk 内で本体未変更（import 追加と新規関数追加のみ）。6 Activity で `binding.root.applySystemBarsPadding()` 呼び出しを保持
- 2.3 — 既存 `applySystemBarsPadding()` の `systemBars() | displayCutout()` 加算契約を維持
- 2.4 — diff stat で対象 6 Activity（CredentialList / CredentialEdit / Settings / DangerZone / OssLicenses / AutofillEnable）すべてに helper 呼び出し追加を確認
- 2.5 — 既存 layout の clipToPadding 設定を温存（design.md 設計判断 4 と整合、XML 変更なし）
- 3.1 — light モード時に `controller.isAppearanceLightStatusBars = !isNightMode = true`
- 3.2 — dark モード時に同 flag が false（暗背景に light icon）
- 3.3 — light モード時に `controller.isAppearanceLightNavigationBars = true`
- 3.4 — dark モード時に同 flag が false
- 3.5 — AppCompat dayNight 切替で Activity 再生成 → onCreate 再実行 → helper 再評価で追従（impl-notes.md 通り）
- 4.1 — 透過 3 Activity（AutofillUnlock / PasskeyAuth / PasskeyCreate）は diff stat に登場せず未改変
- 4.2 — `Theme.KeyNest.Translucent` は themes.xml diff の対象外（Theme.KeyNest 末尾のみ編集）
- 4.3 — 透過 Activity に enableEdgeToEdge() を呼ばないため不透明 bar 背景は導入されない
- 4.4 — 透過 Activity に inset 追加なし、BiometricPrompt / Credential Manager UI は無変更
- 4.5 — 透過 Activity で window styling 変更なし、caller 復帰時に残存変更なし
- 5.1 — themes.xml の旧 attribute 撤去後も API 26〜34 では従来の OS 既定挙動に戻り、`applySystemBarsPadding` は既存契約のまま inset を加算
- 5.2 — API 35+ で `enableEdgeToEdge()` 呼び出しにより OS 既定の透明バー描画契約に乗る
- 5.3 — `isAppearanceLightNavigationBars` は androidx.core 実装により API <27 で internally no-op（helper KDoc にも明記）。クラッシュなし
- 5.4 — impl-notes.md で `:app:assembleDebug` pass を確認
- NFR 1.1 — themes.xml diff は Theme.KeyNest 内の 4 attribute 削除とコメント書き換えのみ。parent `Theme.Material3.DayNight.NoActionBar` 不変
- NFR 1.2 — 13 個の M3 textAppearance slot は diff の対象外（unchanged）
- NFR 1.3 — `android:fontFamily=@font/manrope` / `fontFamily=@font/manrope` は diff で削除されておらず温存
- NFR 1.4 — `Material3ThemeMigrationTest` / `FontTypefaceWiringTest` は diff に含まれていない。impl-notes.md で無修正 pass を申告
- NFR 2.1, 2.2, 2.3 — themes.xml の `android:windowBackground=?attr/colorSurface`（不変）と values-night の semantic 色定義により light/dark で `kn_bg` 相当に解決（手動視覚確認は次リリース時）

## Boundary Check

tasks.md の `_Boundary:_` で許可された範囲のみが変更されており、逸脱なし:

- `util/EdgeToEdgeInsets.kt` — Task 1 boundary
- `res/values/themes.xml` — Task 2 boundary
- 6 opaque Activity（CredentialList / CredentialEdit / Settings / DangerZone / OssLicenses / AutofillEnable） — Task 3 boundary
- `test/.../resources/DeprecatedSystemBarApiRemovalTest.kt` — Task 4 で tasks.md 自身が指定したパスに新規追加
- `docs/specs/128-.../impl-notes.md` および `tasks.md` — Developer の作業記録更新（boundary 規約対象外）

透過 Activity 3 件（AutofillUnlock / PasskeyAuth / PasskeyCreate）および既存テスト（Material3ThemeMigrationTest / FontTypefaceWiringTest）への変更は diff stat に存在せず、Req 4.x / NFR 1.4 の不変条件を満たす。

## Findings

なし

## Summary

requirements.md の全 numeric ID（Req 1.1〜1.5 / 2.1〜2.5 / 3.1〜3.5 / 4.1〜4.5 / 5.1〜5.4 / NFR 1.1〜1.4 / NFR 2.1〜2.3）が diff 内の実装 / 既存契約継続 / pinning テスト / 手動 AC 記録のいずれかでカバーされており、`_Boundary:_` 逸脱も検出されない。新規 `DeprecatedSystemBarApiRemovalTest` が Req 1.1〜1.4 を機械検証で pinning し、`enableEdgeToEdgeWithKnDefaults()` helper が 6 opaque Activity の onCreate 直後で edge-to-edge 化と uiMode 連動の icon appearance を提供。透過 Activity と既存不変条件テストは無変更。

RESULT: approve
