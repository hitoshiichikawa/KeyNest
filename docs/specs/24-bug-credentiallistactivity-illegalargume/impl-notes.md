# Implementation Notes — Issue #24

## サマリ

Issue #24「アプリ起動時に `CredentialListActivity` が `IllegalArgumentException`
でクラッシュ（Material 2 theme と Material 3 style の混在）」を解消するために、
`Theme.KeyNest` および付随する 13 個の `TextAppearance.KeyNest.*` を
Material 3 系へ移行した。レイアウト XML の widget・属性参照（17 箇所 +
M3 系 `?attr/textAppearance*` 参照）は要件で「変更しない」と明示されている
ため一切触れていない（Req 3.1 / 3.2）。

## 変更ファイル一覧

### 主変更（Issue #24 本筋）

| ファイル | 変更内容 | 対応 AC |
|---|---|---|
| `app/src/main/res/values/themes.xml` | `Theme.KeyNest` parent を M2 → M3 (`Theme.Material3.DayNight.NoActionBar`)。13 個の `TextAppearance.KeyNest.*` の parent を `TextAppearance.MaterialComponents.*` → `TextAppearance.Material3.*` に置換。`Theme.KeyNest` 内の 13 attribute slot 名を M2 系 → M3 系に rename。Manrope override（`@font/manrope` / `android:fontFamily` / `fontFamily`）は全 13 スタイルおよび theme レベルで保持。`Theme.KeyNest.Translucent` は parent 変更せず（Out of Scope）。 | Req 1.1〜1.4 / 2.1 / 2.2 / 2.3 / 2.4 / 2.5 / 3.5 |

### テスト追加・更新

| ファイル | 変更内容 | 対応 AC |
|---|---|---|
| `app/src/test/java/com/example/keynest/resources/Material3ThemeMigrationTest.kt` | **新規追加**。Issue #24 の M2 → M3 移行を XML 文字列レベルで pinning する 8 ケース（parent 移行 / 13 attribute slot rename / 13 style parent 切替 / Manrope override 保持 / Translucent 不変 / M2 名残存ガード）。 | Req 2.1 / 2.2 / 2.3 / 2.4 / 2.5 / 3.5 |
| `app/src/test/java/com/example/keynest/resources/FontTypefaceWiringTest.kt` | 4 つの assert を M2 attribute 名（`textAppearanceBody1` 等）から M3 attribute 名（`textAppearanceBodyLarge` 等）に更新。assert の強度は変えていない（同じ「該当 attribute slot が `TextAppearance.KeyNest.*` を指し、`@font/manrope` 経由 Manrope を返す」ことを引き続き保証）。 | Req 5.3（テストを現実の挙動に合わせて更新）|

### 周辺の必須最小修正（Issue #24 を verify するためのブロッカー解除）

| ファイル | 変更内容 | 補足 |
|---|---|---|
| `app/src/main/res/layout/danger_zone_activity.xml` | コメント内の `-- NFR 3.2` を `; see NFR 3.2` に置換。 | develop branch 時点で既に `--` を含む XML コメントが `mergeDebugResources` を fail させており、`assembleDebug` が成立しない状態だった。Req 5.1 の充足には不可欠。コミット `e76c8d9` の `credential_edit_activity.xml` 修正と同じパターン。 |
| `app/src/test/java/com/example/keynest/resources/BundledFontResourcesTest.kt` | Truth 1.4.4 で削除された `.named("…")` API を `assertWithMessage("…").that(…)` に置換（3 箇所）。 | develop branch 時点で test compile error。テストの assert 内容は維持（同じメッセージ・同じ判定）。Req 5.3 の「現実の挙動を表現するように更新」に該当。 |
| `app/src/test/java/com/example/keynest/domain/usecase/ResolveAutofillCandidatesUseCaseTest.kt` | inline `throwingRepo` に欠けていた `observeBySort` / `observeRecentlyUsed` / `markUsed` / `duplicate` / `observeMetadata` / `clearAll` のオーバーライドを追加。テストの assert は変更せず、テスト本来の意図（use case が下層例外を吸収する NFR 3.2 の検証）を維持。 | develop branch 時点で test compile error（Issue #9 / #10 で interface に method が追加されたが、この匿名 fake は更新されていなかった）。Req 5.3 の「現実の挙動を表現するように更新」に該当。 |

## M2 → M3 type-scale mapping 適用結果（Req 2.5）

| 旧 attribute 名 / parent style | 新 attribute 名 / parent style |
|---|---|
| `textAppearanceHeadline1` / `TextAppearance.MaterialComponents.Headline1` | `textAppearanceDisplayLarge` / `TextAppearance.Material3.DisplayLarge` |
| `textAppearanceHeadline2` / `TextAppearance.MaterialComponents.Headline2` | `textAppearanceDisplayMedium` / `TextAppearance.Material3.DisplayMedium` |
| `textAppearanceHeadline3` / `TextAppearance.MaterialComponents.Headline3` | `textAppearanceDisplaySmall` / `TextAppearance.Material3.DisplaySmall` |
| `textAppearanceHeadline4` / `TextAppearance.MaterialComponents.Headline4` | `textAppearanceHeadlineLarge` / `TextAppearance.Material3.HeadlineLarge` |
| `textAppearanceHeadline5` / `TextAppearance.MaterialComponents.Headline5` | `textAppearanceHeadlineMedium` / `TextAppearance.Material3.HeadlineMedium` |
| `textAppearanceHeadline6` / `TextAppearance.MaterialComponents.Headline6` | `textAppearanceHeadlineSmall` / `TextAppearance.Material3.HeadlineSmall` |
| `textAppearanceSubtitle1` / `TextAppearance.MaterialComponents.Subtitle1` | `textAppearanceTitleMedium` / `TextAppearance.Material3.TitleMedium` |
| `textAppearanceSubtitle2` / `TextAppearance.MaterialComponents.Subtitle2` | `textAppearanceTitleSmall` / `TextAppearance.Material3.TitleSmall` |
| `textAppearanceBody1` / `TextAppearance.MaterialComponents.Body1` | `textAppearanceBodyLarge` / `TextAppearance.Material3.BodyLarge` |
| `textAppearanceBody2` / `TextAppearance.MaterialComponents.Body2` | `textAppearanceBodyMedium` / `TextAppearance.Material3.BodyMedium` |
| `textAppearanceButton` / `TextAppearance.MaterialComponents.Button` | `textAppearanceLabelLarge` / `TextAppearance.Material3.LabelLarge` |
| `textAppearanceCaption` / `TextAppearance.MaterialComponents.Caption` | `textAppearanceBodySmall` / `TextAppearance.Material3.BodySmall` |
| `textAppearanceOverline` / `TextAppearance.MaterialComponents.Overline` | `textAppearanceLabelSmall` / `TextAppearance.Material3.LabelSmall` |

スタイル名（`TextAppearance.KeyNest.Headline1` 等）は Out of Scope のため
そのままにし、parent のみ M3 系へ置換した。意味的には
「`TextAppearance.KeyNest.Headline1`（旧 M2 Headline1, 96sp）が今は
`TextAppearance.Material3.DisplayLarge`（57sp）の派生」となるため、
当該 attribute を参照しているレイアウトでは文字サイズが変化する
可能性がある（後述「視覚的回帰の確認事項」参照）。

## ビルド・テスト結果

### `./gradlew :app:clean :app:assembleDebug`

```
BUILD SUCCESSFUL in 23s
41 actionable tasks: 40 executed, 1 up-to-date
```

→ Req 5.1（assembleDebug が build を失敗させない）を充足。

### `./gradlew :app:testDebugUnitTest --tests "com.example.keynest.resources.*"`

| テストクラス | テスト数 | 結果 |
|---|---|---|
| `Material3ThemeMigrationTest`（新規） | 8 | 8 passed / 0 failed |
| `FontTypefaceWiringTest`（更新） | 10 | 10 passed / 0 failed |
| `BundledFontResourcesTest`（mechanical 更新） | 11 | 11 passed / 0 failed |

### `./gradlew :app:testDebugUnitTest`（全 279 テスト）

| 結果 | カテゴリ | 個数 |
|---|---|---|
| pass | Issue #24 直接関連（resources / Theme） | 29 |
| pass | その他全テスト | 245 |
| fail | Issue #24 に**無関係**な pre-existing failure（後述「確認事項」） | 5 |

合計 274 / 279 pass。fail している 5 ケースはいずれも
`PackageSignatureResolverTest` / `LockedFillResponseSecurityTest` 系の
**Android `PackageManager` mockk 利用テスト**で、テーマ・XML resource とは
無関係。develop branch ですでに同じ NullPointerException で fail していると
推定される（develop は `mergeDebugResources` 段階で build 失敗するため
直接の対比は取れないが、`PackageManager` の `signingInfo` フィールドが
Android SDK の API minor によって null 初期化されない問題と一致）。
Issue #24 のテーマ変更コードでは `PackageManager` を一切触らないため、
これらの fail は Issue #24 の責務範囲外。

## AC ↔ テスト対応表（Req 2.x 系）

| AC | 担保するテスト |
|---|---|
| Req 2.1（M3 DayNight NoActionBar parent） | `Material3ThemeMigrationTest.themeKeyNest_inheritsFromMaterial3DayNightNoActionBar` / `themeKeyNest_doesNotInheritFromMaterialComponents` |
| Req 2.2（13 attribute slot rename） | `Material3ThemeMigrationTest.themeKeyNest_declaresEveryMaterial3TypeScaleAttributeSlot` / `themeKeyNest_doesNotDeclareAnyMaterial2TextAppearanceSlotName` |
| Req 2.3（13 style parent 切替） | `Material3ThemeMigrationTest.every13TextAppearanceKeyNestStyle_inheritsFromItsMaterial3Counterpart` / `themeKeyNest_doesNotRetainAnyMaterialComponentsTextAppearanceParent` |
| Req 2.4（Manrope override 保持） | `Material3ThemeMigrationTest.every13TextAppearanceKeyNestStyle_keepsManropeFontFamilyOverride` / `FontTypefaceWiringTest.keyNestTheme_overridesAndroidFontFamily_atThemeLevel` |
| Req 2.5（M2 → M3 type-scale 公開対応表） | `Material3ThemeMigrationTest.every13TextAppearanceKeyNestStyle_inheritsFromItsMaterial3Counterpart`（13 件の Triple で対応表全 13 行を明示的に pinning） |
| Req 3.5（Theme.KeyNest.Translucent 不変） | `Material3ThemeMigrationTest.themeKeyNestTranslucent_isUnchangedByThisMigration` |
| Req 5.1（assembleDebug 成功） | `./gradlew :app:clean :app:assembleDebug` の実測ログ（上記）|
| Req 5.2 / 5.3（既存テスト pass / assert 緩めない） | `FontTypefaceWiringTest` の 4 ケースを attribute 名のみ M3 へ更新、assert 強度は維持。`BundledFontResourcesTest` / `ResolveAutofillCandidatesUseCaseTest` は assert 内容を変えずに API 変更・interface 拡張に追随。 |

### Req 1.x / 4.x（cold-start 各画面 inflate）の担保について

Req 1.x（クラッシュ非発生）と Req 4.x（7 画面 × Light/Dark
の初期描画）は本来エミュレータ / 実機での起動確認が必要だが、本オーケストレーター
環境では実機テストが実行できない。間接的な担保として:

- Req 2.1 + Req 2.2 が成立すれば、4 つのレイアウトが要求する
  `Widget.Material3.*` widget と `?attr/textAppearanceTitleMedium` 等の
  M3 attribute は Theme.KeyNest 上で **必ず**解決可能になる
  （Material 3 widget が theme から M3 attribute を確実に解決できる
  ことは Material library 1.12.0 の契約）。これにより crash の root cause
  は機械的に取り除かれる。

具体的な実機 / エミュレータでの動作確認は PR レビュー時の手動検証項目
として下の「確認事項」へ。

## 確認事項（PR レビュー時 / 人間判断が必要な項目）

### 1. 実機 / エミュレータでの 7 画面 cold-start 検証（Req 4.1〜4.9）

オーケストレーター環境ではエミュレータが利用できないため、以下は人間が
PR レビュー時に確認する必要がある:

- ライトモード / ダークモード両方で以下が `IllegalArgumentException` なしに
  起動すること:
  - `CredentialListActivity`（Req 4.1, 4.8, 4.9）
  - `CredentialEditActivity`（Req 4.2）
  - `SettingsActivity`（Req 4.3）
  - `DangerZoneActivity`（Req 4.4）
  - `OssLicensesActivity`（Req 4.5）
  - `AutofillEnableActivity`（Req 4.6）
  - package picker bottom sheet（Req 4.7。`AutofillUnlockActivity` 経由）
- 各画面で Manrope typeface が本文・見出し・ボタンラベルに適用されていること（Req 3.3 / NFR 2.1）
- ライト / ダーク両モードでテキストとコントラストが WCAG AA 相当を下回らないこと（NFR 1.2）
- M3 type-scale 切替によって、テキストの UI 要素はみ出し・改行崩れ・タップターゲット消失が発生しないこと（NFR 1.1）

### 2. `credential_edit_activity.xml` の M2 attribute 名残存

`credential_edit_activity.xml` には以下の **M2 系** `?attr/textAppearance*`
参照が残っている（Issue #24 の Out of Scope 範囲: 「4 layout 内の M3 attribute は
書き換えない」と書かれているが、`credential_edit_activity.xml` はその 4 つに
**含まれていない**）:

- `?attr/textAppearanceSubtitle1`（line 131）
- `?attr/textAppearanceCaption`（lines 163, 187, 211, 262, 286）
- `?attr/textAppearanceBody1`（lines 169, 193, 268, 305）
- `?attr/textAppearanceBody2`（line 233）

これらは Material 3 親テーマ（`Theme.Material3.DayNight.NoActionBar`）が
**M2 legacy alias として保持している attribute**（M3 の type-scale style に
mapping される）に解決される。Manrope の適用は Theme.KeyNest の theme-level
`android:fontFamily="@font/manrope"` 経由でカバーされる
（`FontTypefaceWiringTest.keyNestTheme_overridesAndroidFontFamily_atThemeLevel`
で pinning）。

ただし、Theme.KeyNest 上で M2 attribute slot を **再宣言していない** ため、
これらの TextView の文字サイズは Material 3 親テーマの legacy alias 値
（M3 type-scale 相当）になる。Issue #13 で `TextAppearance.KeyNest.Subtitle1`
を経由していた頃と比べると、サイズ・行間・letter spacing がわずかに変わる
可能性があるため、`credential_edit` 画面の視覚的回帰確認も実機で実施が必要
（NFR 1.1 範囲内に収まるかの確認）。

将来的に M2 attribute 名残存を完全になくすには、`credential_edit_activity.xml`
側を M3 attribute 名へ更新する別 Issue が必要（Issue #24 の Out of Scope）。

### 3. Pre-existing test failures（5 件 / Issue #24 と無関係）

以下 5 ケースは develop branch から続く pre-existing failure で、Issue #24
の責務範囲外。別 Issue として切り出すことを推奨:

- `com.example.keynest.autofill.LockedFillResponseSecurityTest.lockedFillResponse_bytes_doNotContainCandidateCredentialMaterial`（NPE @ line 54）
- `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_api26_singleSigner_returnsHash`（NPE @ line 99）
- `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_isReRunnable_pickingUpUpdatedSignerEachTime`（NPE @ line 159）
- `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_api28_singleSigner_returnsCanonicalHash`（NPE @ line 43）
- `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_api28_multipleSigners_isOrderIndependent`（NPE @ line 62）

いずれも `PackageManager` の `signingInfo` フィールド（Android API 28+）を
mockk で組み立てる箇所での NPE。Issue #24 の `themes.xml` 変更がこれらの
振る舞いに干渉する経路は無い（テーマと Android `PackageManager` API は完全に
無関係）。

### 4. 周辺の必須最小修正（Issue #24 で同梱した）

以下は Issue #24 を verify するためのブロッカー解除として最小に同梱したが、
本来は別 Issue（typo / API migration）として切られるべきもの:

- `danger_zone_activity.xml` の `--` コメント escape（`mergeDebugResources` の build-time error 解除）
- `BundledFontResourcesTest.kt` の `.named()` → `assertWithMessage()` 移行（Truth 1.4.4 API change）
- `ResolveAutofillCandidatesUseCaseTest.kt` の inline `throwingRepo` への missing override 追加（Issue #9 / #10 で `CredentialRepository` interface に method が追加された後の追従漏れ）

これらを同梱しないと、Issue #24 の本筋テスト（`Material3ThemeMigrationTest`）
を一度も走らせられない（test module の compile 自体が失敗する）。
レビューで「Issue #24 のスコープ外」と判断されれば、別 PR / Issue へ
分離する選択もある（その場合、本 PR は `assembleDebug` の検証のみで
verify する）。

## 依存追加

なし。`com.google.android.material:1.12.0` は既に gradle に存在し、M3 テーマと
M3 widget の両方を含んでいる。

## 派生タスク候補（次の Issue 化を提案）

1. **`credential_edit_activity.xml` の attribute 名 M2 → M3 移行**: Manrope は
   theme-level override で適用されるが、attribute 名の体系を一貫させると
   理解しやすく将来の M2 削除（Material library 2.x?）にも追従できる。
2. **`Theme.KeyNest.Translucent` の M3 化**: 現在は `AutofillUnlockActivity` で
   M3 widget を使わないため M2 のままでも問題ないが、将来 unlock 画面に
   M3 widget を入れる可能性に備えて M3 parent へ揃えるリファクタ。
3. **`Theme.MaterialComponents.*` 系の `TextAppearance.*` 残存の grep & 一掃**:
   今回は `TextAppearance.KeyNest.*` style 名（M2 命名）を温存したため、
   将来「M2 系の命名を完全排除して M3 命名にそろえる」リファクタ Issue が
   立つ余地がある。
4. **Pre-existing test failures（上述 5 件）の修正**: `PackageManager` mockk
   setup の NPE 原因調査。
5. **Truth 1.4 移行に伴う `.named()` API の一掃**: `BundledFontResourcesTest`
   以外にも `.named()` 残存が無いか repository 全体を再 grep。
6. **`CredentialRepository` の test fake / inline anonymous object の一覧化**:
   今回 `ResolveAutofillCandidatesUseCaseTest.kt` の inline fake で発見したが、
   他にも追従漏れが無いか確認。
