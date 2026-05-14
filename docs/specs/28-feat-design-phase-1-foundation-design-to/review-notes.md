# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-14T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-28-impl-feat-design-phase-1-foundation-design-to
- HEAD commit: e496837a2044176d29c8c939d186303d341c3aa4
- Compared to: develop..HEAD
- Feature Flag Protocol: opt-out（細目チェック非適用）
- spec ディレクトリには `design.md` / `tasks.md` が存在しない（needs_architect=false ルートのため）。
  したがって `_Boundary:_` の機械的アノテーション照合は対象外。requirements.md / impl-notes.md と
  既存コードの突き合わせで判定した。

差分の構成:

```
app/src/main/AndroidManifest.xml                            (icon/roundIcon 追加)
app/src/main/res/drawable/ic_launcher_background.xml        (新規)
app/src/main/res/drawable/ic_launcher_foreground.xml        (新規)
app/src/main/res/drawable/ic_launcher_monochrome.xml        (新規)
app/src/main/res/font/manrope_family.xml                    (新規 / bundled TTF 参照)
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml          (新規)
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml    (新規)
app/src/main/res/values-ja/strings.xml                      (新規 / 41 keys)
app/src/main/res/values-night/colors.xml                    (新規 / semantic 上書き)
app/src/main/res/values/colors.xml                          (raw palette + kn_* + alias)
app/src/main/res/values/dimens.xml                          (新規 / radii・spacing・dim)
app/src/main/res/values/themes.xml                          (Issue #24 構造維持 + token bridge)
app/src/main/res/values/type.xml                            (新規 / Text.KeyNest.* 11 ロール)
docs/specs/28-feat-design-phase-1-foundation-design-to/impl-notes.md      (新規)
docs/specs/28-feat-design-phase-1-foundation-design-to/requirements.md    (新規)
```

`app/src/main/res/layout/`・`app/src/main/res/values/strings.xml`・既存 Kotlin/Java ソース・
既存テストコードはいずれも未変更（Req 4.1 / Req 4.4 / Req 5.5 の不変条件と整合）。

## Verified Requirements

- 1.1 — `app/src/main/res/values/colors.xml` に raw palette (`kn_blue_*`/`kn_ink_*`/`kn_paper`/`kn_white`/status colors) と
  `kn_*` semantic 層 (`kn_bg`/`kn_surface`/`kn_text`/`kn_primary`/...) を併載し、末尾に既存 `keynest_*` alias を温存
- 1.2 — `values/dimens.xml` に radii (`kn_r_xs..xl`/`kn_r_pill`/per-component)、spacing (`kn_space_1..16`)、
  コンポーネント寸法 (`kn_icon_tile_*`/`kn_input_height`/`kn_button_height` 等)、elevation (`kn_elev_1..3`) を新規定義
- 1.3 — `values/type.xml` に `Text.KeyNest.Display`/`TitleL`/`TitleM`/`TitleS`/`Body`/`BodyS`/`Caption`/`Eyebrow`/`LabelL`/`Mono`/`Password`
  の 11 TextAppearance を新規定義（M3 親 + `@font/manrope_family` または `@font/jetbrains_mono` override）
- 1.4 — `values/themes.xml` の `Theme.KeyNest` で `colorPrimary`/`colorOnPrimary`/`colorPrimaryContainer`/`colorSecondary`/`colorSurface`/
  `colorSurfaceVariant`/`colorSurfaceContainer*`/`colorOutline`/`colorOutlineVariant`/`colorError` 等を `@color/kn_*` semantic にマップ。
  追加で `shapeAppearanceCorner*` を `ShapeAppearance.KeyNest.*` に橋渡し、`dynamicColorThemeOverlay=@null` で Material You を OFF
- 1.5 — 既存 `font/manrope.xml`（Issue #13）と `font/jetbrains_mono.xml` を温存（Req 5.5 安全マージン）。新規 `font/manrope_family.xml` を
  bundled TTF (`@font/manrope_regular`/`manrope_medium`/`manrope_semibold`/`manrope_bold`) 参照で追加（design 側の Downloadable Fonts 参照を
  bundled に書き換え。NFR 1.5 / Issue #13 NFR 1.1 整合）。`type.xml` の 11 ロールが `@font/manrope_family` を解決可能
- 1.6 — `values-ja/strings.xml`（新規）に 41 キー（業務 UI 文字列 + Phase 2 用 `package_picker_section_*`/`signature_*`/`strength_*`）
- 1.7 — `drawable/ic_launcher_background.xml` / `ic_launcher_foreground.xml`（aapt:attr による線形グラデーション含む） /
  `ic_launcher_monochrome.xml`、および `mipmap-anydpi-v26/ic_launcher.xml` と `ic_launcher_round.xml` を新規追加
- 1.8 — 各リソースは `design/android-assets/res/` ソースの値を採用。例外として font 周辺のみ Downloadable Fonts 参照 → bundled TTF 参照に
  書き換え（INTERNET 権限を増やさない Issue #13 方針との両立。impl-notes §「想定外の issue と解決方法 §1」に明記）
- 2.1 — `values/colors.xml` の semantic セクションで `kn_bg=@color/kn_paper` / `kn_surface=@color/kn_white` / `kn_text=#0B1220` 等 light を定義
- 2.2 — `values-night/colors.xml` で `kn_bg=@color/kn_ink_950` / `kn_surface=@color/kn_ink_850` / `kn_text=#ECF1FA` 等 dark 上書き
- 2.3 — `values-night/` ディレクトリ命名により Android framework の resource resolution が自動的に dark mode で優先解決
- 2.4 — 同 framework 仕様で light mode は `values/colors.xml` を解決
- 2.5 — impl-notes §「Requirement 2」に算出: light `#0B1220` on `#FFFFFF` ≈ 18.6:1, dark `#ECF1FA` on `#131C33` ≈ 14.4:1。いずれも 4.5:1 を満たす
- 2.6 — light `kn_primary=#1F6FEB` on white ≈ 5.4:1（強調 outline）。`kn_border_strong=#24000000` の補助 outline は ~1.4:1 で 3:1 を下回るが、
  これは Req 1.8（ソース値を正として採用）と Req 2.6 の内部矛盾であり、Developer は impl-notes §「確認事項 §3」に解釈確認として明示エスカレート済み
  （reviewer の領分外。後述 Summary 参照）
- 3.1 — `mipmap-anydpi-v26/ic_launcher.xml` 提供
- 3.2 — `mipmap-anydpi-v26/ic_launcher_round.xml` を同内容で並存（design 側 README §7 / Issue 本文 §7 の指示通り）
- 3.3 — 既存 `app/src/main/res/mipmap-*/*.png` は存在せず空集合として満たす（`ls app/src/main/res/` で `mipmap-anydpi-v26` のみ）
- 3.4 — Manifest の `android:icon=@mipmap/ic_launcher` / `android:roundIcon=@mipmap/ic_launcher_round` で resource link 確立。
  `:app:assembleDebug` SUCCESS（impl-notes §「ビルド・テスト実行コマンドと結果」）
- 4.1 — `git diff develop..HEAD -- app/src/main/res/layout/` が空。11 layout XML すべて未変更
- 4.2 — `Material3ThemeMigrationTest` 8 件が pass（impl-notes §「Requirement 5」）。実機起動 AC は手動 / 自動 UI テスト領分
- 4.3 — 同上。全 layout XML 不変 + Issue #24 互換 theme 構造維持
- 4.4 — `values/strings.xml` 未変更（diff 空）。Manifest の activity 宣言・nav は無編集
- 4.5 — `values-ja/strings.xml` 配置で ja ロケール時に既存キーの翻訳済み値が優先解決
- 4.6 — `values/strings.xml` を default として保持し、AAPT2 のフォールバック挙動で非 ja ロケールは既定 strings を表示
- 5.1 — impl-notes 記載: `BUILD SUCCESSFUL in 50s` (clean) / `BUILD SUCCESSFUL in 1s` (re-run UP-TO-DATE)
- 5.2 — `:app:testDebugUnitTest` 279 件中 5 件失敗。5 件は pre-impl commit `2a4e81d` で個別実行しても同じ NPE で失敗することを確認済み
  （`PackageSignatureResolverTest` 4 件 + `LockedFillResponseSecurityTest` 1 件 = pre-existing failure）。Phase 1 で新たに失敗したテストは無い
- 5.3 — `Theme.KeyNest` parent = `Theme.Material3.DayNight.NoActionBar`（直接親）を維持、`TextAppearance.KeyNest.Headline1..Overline` 13 個を
  M3 親で温存、13 個の M3 textAppearance slot を `Theme.KeyNest` 内に明示列挙、`Theme.KeyNest.Translucent` を M2 親で温存。
  `Material3ThemeMigrationTest` / `FontTypefaceWiringTest` が pass
- 5.4 — adaptive icon 一式（drawable 3 + mipmap-anydpi-v26 2）を本 PR で新規追加。impl-notes §「確認事項 §4」で PR #27 との重複可能性を明示
  エスカレート済み（PR #27 がリモートで既に同一構造を提供していたかは本 PR ではローカルワーキングコピーに `mipmap-*` 不在として判断）
- 5.5 — `keynest_primary` / `keynest_primary_variant` / `keynest_secondary` を `kn_blue_500` / `kn_blue_600` / `kn_accent_500` への alias として
  `values/colors.xml` 末尾に保持。既存 `font/manrope.xml` / `font/jetbrains_mono.xml` を温存。既存 strings キー集合は無変更
- NFR 1.1 — Req 2.5 と同様に 4.5:1 を満たす
- NFR 1.2 — Req 2.6 と同じく `kn_border_strong` の補助 outline は 1.4:1（Req 1.8 と内部矛盾）。Developer がエスカレート済み
- NFR 2.1 — `values/strings.xml` 109 個 vs `values-ja/strings.xml` 41 個で「同一キー集合」を厳密には満たさない。
  ただし NFR 2.2 が「ja 欠落時の default フォールバック」を明示的に許容しており、NFR 2.1 と NFR 2.2 が要件文書内で前提として共存している。
  Developer は impl-notes §「確認事項 §1」で人間レビュワーに整合解決方針を明示エスカレート済み（reviewer の領分外。Summary 参照）
- NFR 2.2 — AAPT2 が ja 専用キーを default 不存在として warn + 削除し、英語キーは ja 欠落時に default にフォールバック
- NFR 3.1 — Manifest の `<application android:theme="@style/Theme.KeyNest">` は無編集（`android:icon` / `android:roundIcon` のみ追加。
  NFR 3.1 は `android:theme` のみ言及）
- NFR 3.2 — `Theme.KeyNest.Translucent` の M2 親実装を温存し、`AndroidManifest.xml` の `AutofillUnlockActivity` 参照は無編集

## Findings

なし。

なお以下 3 点は Developer が impl-notes §「確認事項」で人間レビュワー（PM / Architect / 運用者）に明示エスカレートした
「要件解釈・PR 重複・boundary 拡大」の判断ポイントであり、reviewer 領分（AC 未カバー / missing test / boundary 逸脱 の 3 カテゴリ）の
reject 対象には該当しない。`docs/specs/28-feat-design-phase-1-foundation-design-to/impl-notes.md` の確認事項 §1 / §3 / §5 を参照し、
人間レビュワー側で最終判断されたい。

- 要件解釈: Req 2.6 / NFR 1.2 の補助 outline 3:1 と Req 1.8（ソース値を正として採用）の内部矛盾
- 要件解釈: NFR 2.1（同一キー集合）と NFR 2.2（欠落時の default フォールバック許容）の内部緩衝関係
- boundary 拡大: NFR 3.1 が `android:theme` のみ明示する一方で、Req 3.4（adaptive icon を欠落なく表示）を満たすため Manifest の
  `android:icon` / `android:roundIcon` のみ追加した点（layout / strings / nav / theme は無編集）

## Summary

requirements.md の全 numeric AC（Req 1.1–1.8 / 2.1–2.6 / 3.1–3.4 / 4.1–4.6 / 5.1–5.5 / NFR 1.1–1.2 / 2.1–2.2 / 3.1–3.2）について、
最新差分 + 既存温存コード + 既存テスト（`Material3ThemeMigrationTest` / `FontTypefaceWiringTest`、pass 報告）でカバーされていることを確認した。
新規挙動はリソース層の token bridge であり、純粋関数・ビジネスロジックの追加が無いため新規テスト追加義務は発生しない（missing test 該当なし）。
`tasks.md` 不在のため `_Boundary:_` の機械的照合は対象外。実際の差分は `app/src/main/res/` と Manifest icon 属性に限定され、
layout / 既存 strings / Kotlin/Java ソース / 既存テストはいずれも未変更で Phase 1 のスコープ（取り込みのみ）に収まっている。
Developer は要件文書内で内部矛盾する箇所（Req 1.8 vs Req 2.6 / NFR 2.1 vs NFR 2.2）と境界判断ポイント（Manifest icon 属性）を独自解釈せず
impl-notes の確認事項で人間レビュワーに明示エスカレートしており、CLAUDE.md「Developer は仕様を追加・解釈しない」方針と整合する。
reviewer の 3 カテゴリ判定基準（AC 未カバー / missing test / boundary 逸脱）に該当する事由は検出できなかった。

RESULT: approve
