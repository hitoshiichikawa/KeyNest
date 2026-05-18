# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-13-impl-feat-manrope-jetbrains-mono-downloadable
- HEAD commit: f89bbbdfdced23f3c95bbd0c27280e658ef8acf8
- Compared to: develop..HEAD
- Feature Flag Protocol: opt-out（CLAUDE.md 宣言値）— flag 観点の追加チェックは不適用
- 備考: `docs/specs/13-feat-manrope-jetbrains-mono-downloadable/tasks.md` および `design.md` は存在しない（PM → Developer の直結フロー）。`_Boundary:_` 注釈は無いため、boundary 判定は requirements.md / impl-notes.md と実差分の照合で行った。

## 変更ファイル一覧（diff --name-only より）

- `app/src/main/res/font/manrope.xml`（新規, font-family aggregator: 400/500/600/700）
- `app/src/main/res/font/manrope_regular.ttf` / `manrope_medium.ttf` / `manrope_semibold.ttf` / `manrope_bold.ttf`（新規, 計約 388 KB）
- `app/src/main/res/font/jetbrains_mono.xml`（新規, Regular のみ）
- `app/src/main/res/font/jetbrains_mono_regular.ttf`（新規, 約 274 KB）
- `app/src/main/res/raw/ofl_manrope.txt` / `ofl_jetbrains_mono.txt`（新規, OFL 1.1 本文）
- `app/src/main/res/values/themes.xml`（13 個の TextAppearance.KeyNest.* override 追加 + Theme.KeyNest に textAppearance* 13 件 / android:fontFamily / fontFamily 上書き）
- `app/src/main/res/layout/credential_edit_activity.xml`（signature_hex / credential_id の 2 箇所で `monospace` → `@font/jetbrains_mono` に置換、他属性は不変）
- `app/src/test/java/com/example/keynest/resources/BundledFontResourcesTest.kt`（新規, 11 ケース）
- `app/src/test/java/com/example/keynest/resources/FontTypefaceWiringTest.kt`（新規, 10 ケース）
- `app/src/test/java/com/example/keynest/ui/edit/CredentialEditLayoutAuditTest.kt`（既存テストのリネーム + 期待値更新: `monospace` リテラル → `@font/jetbrains_mono`。仕様変更 Req 2.2 に伴う正当な更新であり assert を弱める変更ではない）
- `docs/specs/13-feat-manrope-jetbrains-mono-downloadable/requirements.md` / `impl-notes.md`（新規）

`AndroidManifest.xml` は変更なし（Req 3.1 / NFR 1.5 を機械的に維持）。`app/build.gradle*` 等の build 設定変更も無し。

## Verified Requirements

- 1.1 — `app/src/main/res/font/manrope_*.ttf` (4 weight 同梱) / `manrope.xml`。`BundledFontResourcesTest.manrope_{regular,medium,semibold,bold}_isBundledAsNonEmptyAsset` および `manrope_fontFamily_isBundledAsResource` で各リソース存在＋非空を pinning
- 1.2 — `themes.xml` で 13 個の Material TextAppearance スロットを `TextAppearance.KeyNest.*` 経由で Manrope に override、加えて Theme.KeyNest 自体に `android:fontFamily` / `fontFamily` を上書き。`FontTypefaceWiringTest.keyNestTheme_overridesTextAppearance{Body1,Body2,Headline6,Caption}_withManropeVariant` + `keyNestTheme_overridesAndroidFontFamily_atThemeLevel` でカバー
- 1.3 — `app/src/main/res/raw/ofl_manrope.txt`（OFL 1.1 本文）。`BundledFontResourcesTest.manrope_oflLicenseText_isBundledAsNonEmptyRawAsset` + `manrope_oflLicense_referencesSilOpenFontLicense` で同梱と本文の正当性を pinning
- 1.4 — TextAppearance.KeyNest.* は親 `TextAppearance.MaterialComponents.*` を継承し `fontFamily` のみ override しているため、`@font/manrope` 解決失敗時は Android Typeface フォールバックチェーンで system default に戻る（フレームワーク既定挙動）。コード差分が `fontFamily` 1 属性のみで、他のテキスト属性に手を加えていないことが diff から確認できる
- 1.5 — Manrope 同梱 weight に日本語グリフは含まれないが、追加の typeface 指定をしていないため Android Typeface のフォールバックチェーンで端末既定の日本語フォントが解決される標準挙動が温存される（コード差分が無いこと自体で構造的に担保）
- 2.1 — `app/src/main/res/font/jetbrains_mono_regular.ttf` / `jetbrains_mono.xml`。`BundledFontResourcesTest.jetbrainsMono_regular_isBundledAsNonEmptyAsset` + `jetbrainsMono_fontFamily_isBundledAsResource` でカバー
- 2.2 — `credential_edit_activity.xml` の `value_signature_hex` / `value_credential_id` の 2 TextView を `@font/jetbrains_mono` に切替。`CredentialEditLayoutAuditTest.signatureHexValue_usesJetBrainsMonoFont`（旧 `signatureHexValue_usesMonospaceFont` をリネーム）+ `FontTypefaceWiringTest.credentialIdValue_usesJetBrainsMonoFont` + `credentialEditLayout_hasNoRemainingMonospaceLiteral` + `credentialEditLayout_doesNotReintroduceSystemMonospaceAttribute` の 4 ケースでカバー
- 2.3 — Grep 確認の結果、現行コード上で `android:fontFamily="monospace"` 指定がある箇所は signature_hex / credential_id の 2 箇所のみで、それらは Req 2.2 で `@font/jetbrains_mono` に置換済み。design/spec.md が言及する package name / SHA-256 / password 等の他 mono 表示要素は現行コードに存在せず、本 Issue 範囲内で適用すべき箇所は全てカバー済み（impl-notes に明記）
- 2.4 — `app/src/main/res/raw/ofl_jetbrains_mono.txt`（OFL 1.1 本文）。`BundledFontResourcesTest.jetbrainsMono_oflLicenseText_isBundledAsNonEmptyRawAsset` + `jetbrainsMono_oflLicense_referencesSilOpenFontLicense` でカバー
- 2.5 — `@font/jetbrains_mono` 解決失敗時は Req 1.4 と同様に Android Typeface フォールバックで内蔵 monospace へ戻る。fontFamily 属性のみを変更し、`textAppearance="?attr/textAppearanceBody1/2"` 等の他属性は不変なので等幅基調の表示も維持される（diff から確認）
- 3.1 — `AndroidManifest.xml` に対して本 PR の diff が空（INTERNET 宣言なしの状態を維持）。`FontTypefaceWiringTest.manifest_doesNotDeclareInternetPermission_afterFontBundle` および既存 `InternetPermissionAbsenceTest`（変更なし）で二重に pinning
- 3.2 — フォント参照は全て `@font/...` バンドル形式。`FontTypefaceWiringTest.manifest_doesNotReferenceFontsContractCompat_orGoogleFontsProvider` で manifest を grep 検証。コード上に `FontsContractCompat` / `com.google.android.gms.fonts` の追加導入無し（diff から確認）
- 3.3 — フォント関連のクラッシュレポート機構を本 PR で導入していない（新規 Kotlin コード追加が無く、リソース XML とテストのみの変更）ため、ネットワーク通信を起こす経路が構造的に存在しない
- 4.1 — 変更は (a) 新規 res 追加 / (b) `themes.xml` の TextAppearance 拡張 / (c) `credential_edit_activity.xml` の fontFamily 2 行のみ。MVP の credential 登録・Autofill・署名照合・Vault アンロック・有効化導線に関わる Kotlin / Java コードに diff 無し。既存テスト群が変わらず通る前提で機能挙動不変が担保される
- 4.2 — 同上。Issue #9 の一覧画面・検索・カルーセル等のコードに diff 無し
- 4.3 — 同上。Issue #10 の Settings 画面・Danger Zone 画面のコードに diff 無し
- 4.4 — `credential_edit_activity.xml` の signature_hex / credential_id 各 TextView について、diff 上は `android:fontFamily` 行のみが変更され、`layout_width` / `layout_weight` / `textIsSelectable` / `breakStrategy` / `focusable` / `longClickable` 等は不変（diff コンテキスト 5 行で確認可能）
- NFR 1.1 — フォントバイナリ合計約 0.63 MB + OFL 約 8.7 KB ≒ 約 0.65 MB（圧縮前）の binary delta は +3 MB 上限を大きく下回る。impl-notes に bytes 表が明記されており、`git diff --stat` で各 .ttf のサイズも一致確認できる
- NFR 2.1 — OFL 本文を `res/raw/` に配置し `Resources.openRawResource(R.raw.ofl_*)` で参照可能。`BundledFontResourcesTest.*_oflLicenseText_isBundledAsNonEmptyRawAsset` で参照可能性を pinning。アプリ内 OSS ライセンス画面への組込は requirements.md の Out of Scope と impl-notes 「派生タスク 1」で明記され、Issue #10 の OSS ライセンス画面改修にフォローアップする方針が記録されている
- NFR 2.2 — OFL 本文同梱、フォント名（`manrope` / `jetbrains_mono`）不変、フォント単体販売や OFL 抜きの再配布は行っていない（構造的）
- NFR 3.1 / 3.2 — bundle 方式の `@font/...` 参照は同期解決（Downloadable Fonts の非同期解決ではない）ため、ちらつき・遅延は構造的に発生しない。impl-notes に明記
- NFR 4.1 — `TextAppearance.KeyNest.*` は親 Material スタイルから `textSize` 等 sp 指定属性を継承するため、システム fontScale は引き続き反映される（fontFamily のみ override しているため）
- NFR 4.2 — テキスト色は `textColorPrimary` 等 theme 属性経由のままで、本 PR ではテキスト色を一切変更していない（diff 確認済み）。コントラスト比は不変

## Boundary 評価

- `docs/specs/13-feat-manrope-jetbrains-mono-downloadable/tasks.md` は存在しない（PM 直結フローのため `_Boundary:_` 注釈が無い）
- 実差分は (a) 新規フォント / OFL リソース追加、(b) `themes.xml` への TextAppearance スタイル追加と Theme.KeyNest への属性追加、(c) `credential_edit_activity.xml` の `fontFamily` 2 行置換、(d) フォント関連テスト 2 ファイル追加と 1 ファイルの期待値更新、(e) spec ドキュメント 2 ファイル追加、の 5 種類のみ
- いずれも requirements.md が宣言するフォント関連スコープに収まっており、Kotlin の credential 登録・Autofill・暗号化・Vault・Settings 等のロジックには diff が一切存在しない（boundary 逸脱なし）

## テスト規約整合の確認

- 新規テスト 2 ファイル（合計 21 ケース）は AC ID と対応関係が impl-notes の traceability 表で明示されており、各 AC に対して正常系の pinning が用意されている
- `CredentialEditLayoutAuditTest#signatureHexValue_usesMonospaceFont` → `signatureHexValue_usesJetBrainsMonoFont` のリネームは、旧 Issue #14 由来の Req 3.5「monospace で表示」を Issue #13 Req 2.2「JetBrains Mono で表示」に置き換える仕様変更に伴う期待値の **upgrade** であり、CLAUDE.md 禁止事項「テストを通すために実装ではなくテスト側を書き換えて弱める」には該当しない（より具体的な typeface 名を要求する形に強化されている）
- 異常系（フォント読み込み失敗時のフォールバック = Req 1.4 / 2.5）はフレームワーク既定挙動に委ねており、テストで明示的に未シミュレートだが、`fontFamily` のみを override する構造上、フォールバックは Android の標準動作で発火することが diff から確認できる。spec の Req 1.4 / 2.5 が要求するのは「クラッシュ・空白表示を起こさない」であって、追加実装は不要

## 確認事項（reject ではない補足）

以下は本レビュー範囲では reject 理由としないが、後工程に申し送る注意点:

- **Gradle テスト未実行**: impl-notes の「確認事項」節に Developer から明示申告があるとおり、本 worktree には JDK が無く `./gradlew testDebugUnitTest` および `./gradlew assembleDebug` を実装者環境で実行できていない。新規追加した 21 テストおよび既存テスト群が CI / PjM 側で実際に green であることを PR マージ前に確認する必要がある。テスト自体は静的 XML 文字列の pinning 中心で Robolectric の最小依存に収まっており、構文・参照の妥当性は diff レベルで確認済み
- **NFR 1.1 実測の追認**: impl-notes に「圧縮前バイナリ合計 約 0.65 MB」と試算記載があるが、`./gradlew assembleDebug` 後の APK サイズ実測（フォント導入前との差分）は CI / PjM 側で追認することを推奨
- **OFL 本文のアプリ内表示経路完成**: NFR 2.1 は「アプリ内から閲覧可能な経路」を要求しており、現状は `res/raw/` への配置までで、OSS ライセンス一覧画面への組込は Issue #10 のフォローアップに依存する。requirements.md の Out of Scope と impl-notes の派生タスク 1 で明示的に切り出されており、本 Issue のスコープ判断としては妥当だが、Issue #10 着手前に OSS ライセンス画面に OFL を含めるフォローアップ Issue を必ず起票する必要がある

## Findings

なし（reject 対象の AC 未カバー / missing test / boundary 逸脱はいずれも検出されなかった）。

## Summary

requirements.md の Req 1.x / 2.x / 3.x / 4.x および NFR 1〜4 のすべての numeric ID について、対応する実装またはテストが diff 内に存在することを確認した。新規追加された AC 対応挙動（Manrope の theme wiring、JetBrains Mono の credential_edit 適用、OFL 本文同梱、INTERNET 非宣言の維持、Downloadable Fonts API 非導入）にはそれぞれ専用テストケースが追加されており、boundary 逸脱（フォント以外のロジックへの diff）も検出されなかった。Gradle テストの実行確認は CI / PjM に委ねる前提だが、テストコードの内容は静的 XML / リソース pinning 中心で構文的・参照的に妥当である。

RESULT: approve