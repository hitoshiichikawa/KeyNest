# Issue #28 — Phase 1 (Foundation) 実装ノート

## 概要

`design/android-assets/res/` 配下のデザイントークン群（colors / dimens / typography /
themes / fonts / 日本語 strings / launcher icon drawable / adaptive icon mipmap）を
`app/src/main/res/` の対応リソースに 1:1 で取り込んだ。既存画面の widget 入れ替えや
layout 書き換えは行っていない（Phase 2 スコープ）。

## コミット一覧

| コミット | 内容 |
|---|---|
| `98f31ae` | feat(design): import design tokens into values/ resources |
| `8b9469c` | feat(design): add dark color overrides and Japanese strings |
| `0a637db` | feat(design): add Manrope font family aggregator |
| `0456c8b` | feat(design): add adaptive launcher icon resources |
| `5f9719b` | refactor(design): keep Issue #24 theme structure while bridging design tokens |

## 各 AC のカバレッジ

### Requirement 1: デザイントークンのリソース取り込み範囲

| AC | 担保箇所 | 検証 |
|---|---|---|
| 1.1 (色定義 = `values/colors.xml`) | `app/src/main/res/values/colors.xml` (raw palette `kn_blue_*`/`kn_ink_*`/`kn_white`/`kn_paper`/status colors + `kn_*` semantic 層 + `keynest_*` aliases) | `:app:assembleDebug` がリンク成功. `themes.xml` から `@color/kn_primary` 等を解決できる |
| 1.2 (寸法 = `values/dimens.xml`) | `app/src/main/res/values/dimens.xml` (radii / spacing / icon tile / button height / strength bar / elevation) | `:app:assembleDebug` 成功. `themes.xml` から `@dimen/kn_r_xs..xl` を解決 |
| 1.3 (TextAppearance = `values/type.xml`) | `app/src/main/res/values/type.xml` (`Text.KeyNest.Display` / `TitleL` / `TitleM` / `TitleS` / `Body` / `BodyS` / `Caption` / `Eyebrow` / `LabelL` / `Mono` / `Password` の 11 ロール) | `:app:assembleDebug` 成功. `Widget.KeyNest.Button.Primary` の `android:textAppearance` が `@style/Text.KeyNest.LabelL` を解決 |
| 1.4 (M3 attribute → kn_* マッピング = `values/themes.xml`) | `app/src/main/res/values/themes.xml` の `Theme.KeyNest` 内 `colorPrimary` 等 → `@color/kn_primary` 等 | `Material3ThemeMigrationTest` (Issue #24) の 8 件と `FontTypefaceWiringTest` (Issue #13) の 9 件が pass |
| 1.5 (フォント = `font/`) | 既存 `font/manrope.xml` `font/jetbrains_mono.xml` を温存 (Req 5.5) + 新規 `font/manrope_family.xml` を bundled TTF 参照で追加 | `:app:assembleDebug` 成功. `themes.xml` の `@font/manrope` と `type.xml` の `@font/manrope_family` 両方が解決 |
| 1.6 (日本語 strings = `values-ja/`) | `app/src/main/res/values-ja/strings.xml` (新規) | `:app:assembleDebug` 成功. ロケール ja の端末で 41 個の翻訳済みキーが優先解決 |
| 1.7 (launcher icon drawable / adaptive icon mipmap) | `app/src/main/res/drawable/ic_launcher_background.xml` / `ic_launcher_foreground.xml` / `ic_launcher_monochrome.xml` + `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` | `:app:assembleDebug` 成功. `AndroidManifest.xml` の `android:icon=@mipmap/ic_launcher` / `android:roundIcon=@mipmap/ic_launcher_round` が解決 |
| 1.8 (ソース側の値を正として採用) | 各リソースは `design/android-assets/res/` のソースを優先採用 | `diff` で比較済み. font 周辺のみ後述のとおり bundled 方式に書き換え |

### Requirement 2: ライト / ダーク二系統の同時提供

| AC | 担保箇所 |
|---|---|
| 2.1 (light semantic = `values/colors.xml`) | `kn_bg=kn_paper`, `kn_surface=kn_white` 等を light に定義 |
| 2.2 (dark semantic = `values-night/colors.xml`) | `kn_bg=kn_ink_950`, `kn_surface=kn_ink_850`, `kn_text=#ECF1FA` 等の上書き |
| 2.3 (dark mode で values-night を優先解決) | Android framework の resource resolution に依存. `values-night/` ディレクトリ命名で自動解決 |
| 2.4 (light mode で values を解決) | 同上 |
| 2.5 (本文コントラスト 4.5:1 以上) | light: `kn_text=#0B1220` on `kn_white` ≈ 18.6:1, dark: `#ECF1FA` on `kn_ink_850=#131C33` ≈ 14.4:1. いずれも 4.5:1 を大幅クリア |
| 2.6 (UI 輪郭コントラスト 3:1 以上) | light: `kn_border_strong=#24000000` (alpha 14% ink) on `kn_white` のコントラスト比は alpha blend 後 #DBDBDB on white ≈ 1.4:1 だが、これは Material 3 の outline 設計通りの「微弱な輪郭」であり実装上 `colorOutline` は補助 outline にのみ使う. 強調 outline には `kn_primary` (light で #1F6FEB on white ≈ 5.4:1) が使われる. **要レビュー判断**: 確認事項参照 |

### Requirement 3: アダプティブランチャーアイコンの統一

| AC | 担保箇所 |
|---|---|
| 3.1 (`mipmap-anydpi-v26/ic_launcher.xml` を提供) | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (新規) |
| 3.2 (`ic_launcher_round.xml` も提供) | `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` を `ic_launcher.xml` と同内容で配置 (README §7 / Issue 本文 §7 の指示通り) |
| 3.3 (旧 PNG 削除) | 既存 `app/src/main/res/mipmap-*` ディレクトリ自体が存在せず空集合として満たされる |
| 3.4 (新 adaptive icon を欠落・破損なく表示) | `AndroidManifest.xml` の `android:icon` を `@android:drawable/sym_def_app_icon` から `@mipmap/ic_launcher` に切替 + `android:roundIcon=@mipmap/ic_launcher_round` を追加. `:app:assembleDebug` 成功で resource リンク確認済み. **実機表示の AC は手動 / 自動 UI テストの領分** |

### Requirement 4: 既存画面の振る舞い不変

| AC | 担保箇所 |
|---|---|
| 4.1 (View 階層・ID・属性構造を保持) | `app/src/main/res/layout/*.xml` 11 ファイルを **一切編集していない**. `git diff 2a4e81d..HEAD -- app/src/main/res/layout/` で空 |
| 4.2 (CredentialListActivity がクラッシュせず描画) | `Material3ThemeMigrationTest` がテーマ inflate 時の crash 回帰 (Issue #24) をピン留め. Phase 1 取り込み後も pass. **実機起動の AC は手動 / 自動 UI テストの領分** |
| 4.3 (各既存画面がクラッシュせず描画) | 同上. 全 layout XML 不変 + theme structure を Issue #24 互換に維持 |
| 4.4 (文字列キー / ナビゲーション / 押下挙動を変更しない) | `values/strings.xml` を編集していない. ナビは Manifest activity 宣言を編集していない (icon 属性のみ追加) |
| 4.5 (ja ロケールで `values-ja/strings.xml` を表示) | `values-ja/strings.xml` 新規追加. AAPT2 のリンクログで `values-ja` 配下のキーが認識されている (ExtraTranslation 警告がそれを示す) |
| 4.6 (非 ja ロケールで既定 `values/strings.xml` を表示) | `values-ja/strings.xml` から欠落するキーは AAPT2 が `removing resource ... without required default value` 警告を出して default に fall through. これは NFR 2.2 の「ローカライズ未完了として明示」通りの挙動 |

### Requirement 5: ビルド・テスト・既存 PR との整合

| AC | 担保箇所 |
|---|---|
| 5.1 (`:app:assembleDebug` 成功) | クリーン実行で `BUILD SUCCESSFUL in 50s` (clean), `BUILD SUCCESSFUL in 1s` (re-run UP-TO-DATE) |
| 5.2 (Phase 1 取り込み前に成功していたテストはすべて成功) | `:app:testDebugUnitTest` 279 件中 5 件失敗. **5 件はすべて pre-existing failure** (`PackageSignatureResolverTest` 4 件 + `LockedFillResponseSecurityTest` 1 件). pre-impl 状態 (commit `2a4e81d`) で `gradlew testDebugUnitTest --tests` を実行して同じ失敗を確認済み. Phase 1 で新たに失敗したテストは無い |
| 5.3 (PR #25 / Issue #24 の Material 3 移行を保持) | `Theme.KeyNest` parent = `Theme.Material3.DayNight.NoActionBar` を維持. `TextAppearance.KeyNest.Headline1..Overline` 13 個が M3 親 + `@font/manrope`. `Theme.KeyNest.Translucent` は M2 親で温存. `Material3ThemeMigrationTest` 8 件が pass |
| 5.4 (PR #27 のランチャーアイコン整合) | adaptive icon 一式 (`drawable/ic_launcher_background/foreground/monochrome.xml` + `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`) を本 PR で新規追加. PR #27 が PNG ベースだった場合は本 PR で adaptive 形式に統一. **PR #27 の到達点と本 PR の追加分が矛盾しないかは確認事項参照** |
| 5.5 (既存リソース名解決を維持) | `keynest_primary` / `keynest_primary_variant` / `keynest_secondary` を `kn_blue_500` / `kn_blue_600` / `kn_accent_500` への alias として `colors.xml` 末尾に保持. 既存 `font/manrope.xml` / `font/jetbrains_mono.xml` を温存. 既存 strings キー集合は無変更 |

### NFR 1: アクセシビリティ

| NFR | 担保箇所 |
|---|---|
| 1.1 (本文 4.5:1) | Req 2.5 と同様 |
| 1.2 (UI 輪郭 3:1) | Req 2.6 と同様. **要レビュー判断** (確認事項参照) |

### NFR 2: ローカライズ

| NFR | 担保箇所 |
|---|---|
| 2.1 (en/ja 同一キー集合) | **完全には満たせていない**. `values/strings.xml` (109 個) > `values-ja/strings.xml` (41 個). 76 個の英語キーが ja 未翻訳, かつ 9 個の ja 専用キー (`package_picker_section_*` 等) は Phase 2 で英語に追加予定. AAPT2 は ja 専用キーを default 不存在として削除 (`removing resource ... without required default value`). **要レビュー判断** (確認事項参照) |
| 2.2 (ja 欠落キーは default にフォールバック) | AAPT2 のリンク挙動で自動フォールバック. ja のみキーは default 不存在なので警告 + 自動削除されるが、英語側に追加された段階で双方残る |

### NFR 3: 互換性

| NFR | 担保箇所 |
|---|---|
| 3.1 (Manifest の `<application android:theme="@style/Theme.KeyNest">` を変更しない) | Manifest の `android:theme` は無編集. `android:icon` / `android:roundIcon` のみ追加 (Req 3.4 を満たすため不可避) |
| 3.2 (`Theme.KeyNest.Translucent` の挙動を保持) | `themes.xml` 末尾に Issue #24 と同じ M2 親実装を温存. `AndroidManifest.xml` の AutofillUnlockActivity への参照も無変更 |

## ビルド・テスト実行コマンドと結果

```
$ JAVA_HOME=$HOME/sdks/jdk-17 ANDROID_HOME=$HOME/sdks/android-sdk \
    ./gradlew clean :app:assembleDebug :app:testDebugUnitTest

> Task :app:assembleDebug
BUILD SUCCESSFUL in 50s

> Task :app:testDebugUnitTest
279 tests completed, 5 failed
  - PackageSignatureResolverTest 4 件 (NPE @ Signature mock)  ← pre-existing
  - LockedFillResponseSecurityTest 1 件 (NPE)                 ← pre-existing
```

`./gradlew :app:assembleDebug` 単体実行: `BUILD SUCCESSFUL`.

`./gradlew :app:testDebugUnitTest` の失敗 5 件は pre-impl commit `2a4e81d` で
個別実行しても同じ NPE で落ちることを確認済み (Phase 1 取り込み前から失敗
していた). 要件 5.2 (「Phase 1 取り込み前に成功していたものをすべて成功
させる」) は満たしている.

## 想定外の issue と解決方法

### 1. design 側 font/*.xml が Downloadable Fonts 形式

`design/android-assets/res/font/` 配下の `manrope_*.xml` と `jetbrains_mono.xml`
は Google Fonts provider 経由で `@array/com_google_android_gms_fonts_certs` を
要求する Downloadable Fonts 形式だった. これをそのまま取り込むと:

- ビルドエラー (`com_google_android_gms_fonts_certs` 未定義).
- INTERNET 権限の要求が発生し NFR 1.5 / Issue #13 NFR 1.1 に違反.
- PR #13 (Issue #13) で確立した「Manrope / JetBrains Mono を bundled TTF
  として APK 同梱、INTERNET 権限を declare しない」方針と矛盾.

**解決**: `manrope_family.xml` のみ取り込み、内部で Downloadable Fonts 参照
を bundled TTF (`@font/manrope_regular` etc.) への参照に書き換えた. 設計側
の weight 別 wrapper (`manrope_regular.xml` 等) は配置しない (重複 resource
名でビルドエラーになるため). weight 800 は APK 同梱せず framework の
closest-match 解決で weight 700 にフォールバックする (Out of Scope に明記).

詳細: コミット `0a637db` メッセージ.

### 2. Phase 1 取り込み直後の themes.xml が Issue #24 テストを破壊した

design 側 themes.xml をそのまま取り込んだ初版 (commit `98f31ae`) は
`Base.Theme.KeyNest` を介して `Theme.KeyNest` を定義する構造で、Issue #24
の `Material3ThemeMigrationTest` および Issue #13 の `FontTypefaceWiringTest`
を破壊した. これらのテストは:

- `Theme.KeyNest` が `Theme.Material3.DayNight.NoActionBar` を **直接** 親に
  持つこと.
- 13 個の `TextAppearance.KeyNest.Headline1..Overline` が M3 親で定義され
  `@font/manrope` override を持つこと.
- 13 個の M3 textAppearance attribute slot が `Theme.KeyNest` 内に明示列挙
  され `TextAppearance.KeyNest.*` を指すこと.
- `Theme.KeyNest` 自身が `android:fontFamily` / `fontFamily` を `@font/manrope`
  で override していること.

を厳密に source-level で verify する.

**解決**: コミット `5f9719b` で themes.xml を再構成. 中間 `Base.Theme.KeyNest`
を廃し `Theme.KeyNest` が直接 M3 親を継承する形に戻し、13 個の旧
`TextAppearance.KeyNest.Headline1..Overline` を本ファイル先頭で再宣言、
`Theme.KeyNest` 内で 13 個の M3 attribute slot に紐付けた. 同時に Phase 1
が要請する design token bridge (`colorPrimary` → `@color/kn_primary`、
`shapeAppearanceCorner*` → `ShapeAppearance.KeyNest.*`、
`dynamicColorThemeOverlay=@null`、system bar 配色) を `Theme.KeyNest` 内に
直接追加. 結果として:

- `Material3ThemeMigrationTest` 8 件 / `FontTypefaceWiringTest` 9 件が pass
  (Req 5.2 / Req 5.3 を満たす).
- design tokens (`Text.KeyNest.*`, `Widget.KeyNest.*`, `kn_*` semantic 色,
  `kn_*` dimens) はすべて取り込まれており、Phase 2 で layout から参照可能.

ただし Issue #24 のテストが極めて厳格な structural pin として動作するため、
Phase 2 で `Text.KeyNest.Display` を実際の M3 attribute slot に切り替える
段階で、`TextAppearance.KeyNest.Headline1..Overline` 13 個を廃止する判断
が必要になる. その場合は Issue #24 のテスト自体を見直すか、本ハイブリッド
構造を維持するかをアーキテクトレビューで決定する必要がある (確認事項
参照).

### 3. design 側 type.xml の `xmlns:tools` インラインスコープ

`type.xml` の各 `<style>` 内で `<item ... xmlns:tools="http://...">` を
item-level で宣言している. 本来は `<resources>` ルートで宣言する方が
慣習的だが、AAPT2 は item-level スコープも許容しビルドは成功. design 側
を尊重しそのまま温存した.

## 確認事項 (人間レビュワー / Reviewer 判断ポイント)

1. **`values-ja/strings.xml` のキー集合不整合**: `values/strings.xml` (109 個) と
   `values-ja/strings.xml` (41 個) は同一キー集合になっておらず NFR 2.1 を
   厳密には満たさない. 設計側ファイルを 1:1 で取り込む方針の必然的帰結だが:
   - 76 個の英語キー (Issue #9/10 由来の最近追加された UI 文字列等) が
     ja 未翻訳 → 日本語ロケールでも英語にフォールバック (NFR 2.2 の挙動).
   - 9 個の ja 専用キー (`package_picker_section_used` / `signature_match` /
     `strength_strong` 等) が default に存在せず AAPT2 が build-time に
     ja から削除する (リンク警告: `removing resource ... without required
     default value`). これらは Phase 2 で対応する英語キーが追加された
     時点で残るようになる.
   - **判断**: NFR 2.1 の整合は本 Phase 1 ではなく Phase 2 (または別 PR)
     で取るべきか? 取るなら本 PR で空文字 `<string name="..."/>` でも
     先行追加すべきか? 設計判断を仰ぎたい.

2. **`Material3ThemeMigrationTest` の生命線**: Issue #24 のテストが extremely
   strict な structural pin として動作しており、Phase 1 で themes.xml を
   素直に design 構造に置き換えるとほぼ確実に破壊する. 今回はテスト互換
   構造に themes.xml を適合させたが、Phase 2 で `Widget.KeyNest.Card` 等の
   design tokens を実 layout が参照する段階で、`TextAppearance.KeyNest.*`
   の 13 alias 系統を廃止できないか. 廃止するなら `Material3ThemeMigrationTest`
   自体を再設計する必要がある. **Architect レビュー要件**.

3. **`Req 2.6 / NFR 1.2` (UI 輪郭 3:1)**: `kn_border_strong` (#24000000, light で
   alpha 14% ink) on white は alpha blend 後のコントラスト比が 1.4:1 程度で
   3:1 を満たさない. これは Material 3 の outline 設計思想 (subtle outline は
   1.5:1 程度、強調 outline は colorPrimary 等を使う) と整合しており、design
   側の token 値そのまま. UI コンポーネントが `colorOutline` を強調 outline
   に使わない限り問題ないが、要件文言が `colorOutline` 単独で 3:1 を求めて
   いるとも読める. **要件解釈確認**.

4. **PR #27 との整合**: PR #27 で adaptive icon が既に整備済みかどうかに
   よって本 PR の adaptive icon コミット (`0456c8b`) が重複する可能性. リモ
   ート main の `app/src/main/res/mipmap-anydpi-v26/` の存在状況を Reviewer
   側で確認願いたい. 重複している場合は、design 側 (本 PR) が PR #27 と
   bit-for-bit 同じか確認の上、いずれか一方の commit を除く判断が必要.
   ローカルワーキングコピーには `mipmap-*` ディレクトリが存在しなかった
   ので、本 PR は新規追加として進めた.

5. **`AndroidManifest.xml` の `android:icon` 切替**: 既存 manifest の
   `android:icon="@android:drawable/sym_def_app_icon"` (Android system default)
   から `@mipmap/ic_launcher` に切り替えた. README §10 の「ビルド確認 →
   アプリ起動して既存画面が「色が変わっただけ」で動くことを確認」を満たす
   ためには icon resource 解決が必須なので不可避な変更だが、「Manifest を
   触らない」というガードの境界判断として Reviewer 確認をお願いしたい
   (NFR 3.1 は `android:theme` のみ言及、`android:icon` は明示されていない).

6. **既存 `font/manrope.xml` の今後**: 本 PR では Req 5.5 (既存リソース名
   解決を維持) の安全マージンとして `font/manrope.xml` (Issue #13 で導入
   した bundled font-family) を温存し、新規に `font/manrope_family.xml` を
   並列に追加した. 結果として 2 つの font family ファイルが共存している.
   Phase 2 で `manrope.xml` を `manrope_family.xml` に統一するクリーン
   アップを別 Issue で起票するかどうか、運用判断を仰ぎたい.

## 派生 Issue 候補

- `values-ja/strings.xml` の Phase 2 用キー (`package_picker_*`, `signature_*`,
  `strength_*`) を `values/strings.xml` に追加する PR (NFR 2.1 整合)
- `Material3ThemeMigrationTest` の structural pin 緩和 (Phase 2 での design
  tokens 移行容易性のため)
- `font/manrope.xml` ↔ `font/manrope_family.xml` の統一 (本 PR では Req 5.5
  の安全マージンとして両方残置)
- Phase 2: `mapping.md §5` の各画面 1 PR (CredentialListActivity から優先)
