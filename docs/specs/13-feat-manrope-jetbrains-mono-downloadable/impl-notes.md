# 実装ノート (Issue #13)

## 実装サマリ

Issue #13 で確定した「案 C: フォントを APK に bundle する」方針に沿って、
**Manrope** と **JetBrains Mono** を `.ttf` 形式で `app/src/main/res/font/`
配下に同梱し、Theme（`themes.xml`）越しに本文・見出し全般に Manrope を適用、
`credential_edit_activity.xml` のモノスペース表示箇所に JetBrains Mono を適用した。

- 採用したフォント取得元: Google Fonts 公式 manifest API
  （`https://fonts.google.com/download/list?family=Manrope`）と
  JetBrains Mono v2.304 公式 release zip
  （`https://github.com/JetBrains/JetBrainsMono/releases/download/v2.304/JetBrainsMono-2.304.zip`）
- 各 OFL ライセンス本文（`OFL.txt`）はそれぞれの配布物同梱版をそのまま `res/raw/`
  に静置（`ofl_manrope.txt` / `ofl_jetbrains_mono.txt`）
- `AndroidManifest.xml` の `android.permission.INTERNET` 非宣言状態を維持
  （bundle 方式のためネットワーク取得は発生しない）

## 変更・追加ファイル一覧

### 新規追加（フォントバイナリ）

| ファイル | サイズ (bytes) | 出典 |
|---|---|---|
| `app/src/main/res/font/manrope_regular.ttf` | 96,832 | Google Fonts manifest v20 |
| `app/src/main/res/font/manrope_medium.ttf` | 96,904 | Google Fonts manifest v20 |
| `app/src/main/res/font/manrope_semibold.ttf` | 96,936 | Google Fonts manifest v20 |
| `app/src/main/res/font/manrope_bold.ttf` | 96,800 | Google Fonts manifest v20 |
| `app/src/main/res/font/jetbrains_mono_regular.ttf` | 273,900 | JetBrains Mono v2.304 |

合計フォントバイナリサイズ: **661,372 bytes ≒ 0.63 MB**

### 新規追加（OFL ライセンス本文）

| ファイル | サイズ (bytes) | 内容 |
|---|---|---|
| `app/src/main/res/raw/ofl_manrope.txt` | 4,478 | SIL Open Font License 1.1 (Manrope 同梱版) |
| `app/src/main/res/raw/ofl_jetbrains_mono.txt` | 4,399 | SIL Open Font License 1.1 (JetBrains Mono 同梱版) |

### 新規追加（リソース XML）

- `app/src/main/res/font/manrope.xml` — Manrope の 4 weight を 1 つの font-family にまとめる aggregator
- `app/src/main/res/font/jetbrains_mono.xml` — JetBrains Mono Regular のみの aggregator

### 修正

- `app/src/main/res/values/themes.xml` — Material TextAppearance.* の Manrope variant を 13 個追加し、`Theme.KeyNest` で `textAppearance*` 属性を全 override。さらに theme レベルでも `android:fontFamily="@font/manrope"` を設定（textAppearance を経由しない素 TextView 用のフォールバック）。
- `app/src/main/res/layout/credential_edit_activity.xml` — 既存の `android:fontFamily="monospace"` 2 箇所（signature hex 行 / credential ID 行）を `@font/jetbrains_mono` に置換。レイアウト幅・コピー可否などの他属性は不変（Req 4.4 を維持）。

### 新規追加（テスト）

- `app/src/test/java/com/example/keynest/resources/BundledFontResourcesTest.kt`
  - 各 R.font.* / R.raw.* リソースが merged APK に含まれること、および OFL 本文が SIL OFL の文言を含むことを Robolectric で検証
- `app/src/test/java/com/example/keynest/resources/FontTypefaceWiringTest.kt`
  - `themes.xml` の Material TextAppearance override および theme-level `android:fontFamily` を pinning
  - `credential_edit_activity.xml` で旧 `monospace` リテラルが消えていること、`@font/jetbrains_mono` 参照が存在することを pinning
  - `AndroidManifest.xml` で `INTERNET` 宣言が無いこと、`FontsContractCompat` / `com.google.android.gms.fonts` が参照されていないことを pinning

### 修正（テスト）

- `app/src/test/java/com/example/keynest/ui/edit/CredentialEditLayoutAuditTest.kt`
  - 旧テスト `signatureHexValue_usesMonospaceFont` を `signatureHexValue_usesJetBrainsMonoFont` にリネームし、期待値を `"@font/jetbrains_mono"` へ更新。**これは仕様変更 (Req 2.2) に伴う期待値の正当な更新**であり、assert を弱める変更ではない。

## AC ID と実装ファイルの traceability マッピング

| AC ID | 内容 | 実装ファイル / テスト |
|---|---|---|
| 1.1 | Manrope を `.ttf` でアプリ同梱、ネットワーク取得なし | `app/src/main/res/font/manrope_*.ttf` / `manrope.xml`; `BundledFontResourcesTest#manrope_regular_isBundledAsNonEmptyAsset`〜`manrope_bold_isBundledAsNonEmptyAsset` / `manrope_fontFamily_isBundledAsResource` |
| 1.2 | 本文・見出しに Manrope を適用 | `app/src/main/res/values/themes.xml` (Theme.KeyNest の textAppearance* override および android:fontFamily); `FontTypefaceWiringTest#keyNestTheme_overridesTextAppearance*` 系 5 件 |
| 1.3 | Manrope の OFL ライセンス本文同梱 | `app/src/main/res/raw/ofl_manrope.txt`; `BundledFontResourcesTest#manrope_oflLicenseText_isBundledAsNonEmptyRawAsset` / `manrope_oflLicense_referencesSilOpenFontLicense` |
| 1.4 | Manrope 読み込み失敗時に system default にフォールバック | `themes.xml` の TextAppearance.KeyNest.* は親 (TextAppearance.MaterialComponents.*) を inherit しており、`fontFamily` 解決失敗時は親側の Roboto に戻る Android の標準挙動が働く。コード追加なし（フレームワーク既定動作で担保）。 |
| 1.5 | 日本語フォントフォールバック維持 | Manrope に日本語グリフは含まれないため、Android Typeface のフォールバック chain で system 日本語フォントが解決される標準挙動を温存。コード変更なし（追加の typeface 指定をしないことで確保）。 |
| 2.1 | JetBrains Mono を `.ttf` でアプリ同梱、ネットワーク取得なし | `app/src/main/res/font/jetbrains_mono_regular.ttf` / `jetbrains_mono.xml`; `BundledFontResourcesTest#jetbrainsMono_regular_isBundledAsNonEmptyAsset` / `jetbrainsMono_fontFamily_isBundledAsResource` |
| 2.2 | 既存 monospace 指定 (signature hex / credential ID) を JetBrains Mono に切替 | `app/src/main/res/layout/credential_edit_activity.xml` の 2 箇所; `CredentialEditLayoutAuditTest#signatureHexValue_usesJetBrainsMonoFont` / `FontTypefaceWiringTest#credentialIdValue_usesJetBrainsMonoFont` / `credentialEditLayout_hasNoRemainingMonospaceLiteral` |
| 2.3 | デザインモック上の package name / SHA-256 / password にも適用 | 現行実装で `monospace` 明示指定があるのは signature_hex / credential_id の 2 箇所のみ（Grep 確認済み）。package name / password 表示の typeface 指定箇所が存在しないため、本 Issue 範囲ではこれら 2 箇所への切替で AC を満たす。design/spec.md の表示要素が新規追加された時点で同じ pattern を適用する想定 |
| 2.4 | JetBrains Mono の OFL ライセンス本文同梱 | `app/src/main/res/raw/ofl_jetbrains_mono.txt`; `BundledFontResourcesTest#jetbrainsMono_oflLicenseText_isBundledAsNonEmptyRawAsset` / `jetbrainsMono_oflLicense_referencesSilOpenFontLicense` |
| 2.5 | JetBrains Mono 読み込み失敗時に Android 内蔵 monospace にフォールバック | `@font/jetbrains_mono` の解決失敗時は Android Typeface の標準フォールバック動作が働く（コード追加なし） |
| 3.1 | `android.permission.INTERNET` 未宣言 | `AndroidManifest.xml` 不変; 既存 `InternetPermissionAbsenceTest` / 新規 `FontTypefaceWiringTest#manifest_doesNotDeclareInternetPermission_afterFontBundle` |
| 3.2 | Downloadable Fonts API / FontsContractCompat を呼び出さない | フォント参照は全て `@font/...` 形式の bundle 参照のみ。`FontTypefaceWiringTest#manifest_doesNotReferenceFontsContractCompat_orGoogleFontsProvider` で manifest を scan |
| 3.3 | クラッシュレポート等でもネットワーク通信を起こさない | フォント関連のクラッシュレポート機構は本実装で導入していない（Req 3.3 を後退させる要素なし） |
| 4.1〜4.3 | MVP / Issue #9 / Issue #10 の挙動を変更しない | コード変更は (a) 新規リソース追加 / (b) `themes.xml` への TextAppearance 追加と Theme.KeyNest への textAppearance* override 追加 / (c) `credential_edit_activity.xml` の `monospace` リテラル 2 箇所を `@font/jetbrains_mono` に置換、の 3 種のみ。挙動を変更しないことは静的に明白（既存テスト群が引き続き pass することで担保） |
| 4.4 | モノスペース表示の非フォント属性を不変 | `credential_edit_activity.xml` の `layout_width` / `textIsSelectable` / `breakStrategy` / `focusable` / `longClickable` 等は不変（diff で fontFamily 行のみ変更） |
| NFR 1.1 | APK サイズ増分 +3 MB 以内 | 同梱物合計 0.63 MB（フォント） + 8.7 KB（OFL）= **約 0.65 MB 増**（圧縮前バイナリサイズ。aapt 圧縮で更に縮む可能性あり）。3 MB 上限を大きく下回る |
| NFR 2.1 | OFL 本文がアプリ内から閲覧可能な経路で提供 | `res/raw/` に同梱（`Resources.openRawResource(R.raw.ofl_manrope)` 等で参照可能）。**OSS ライセンス画面への組込は Issue #10 の OSS ライセンス一覧画面の改修によって行う想定で、本 Issue ではスコープ外（requirements Out of Scope に明記）**。raw に静置されていれば「閲覧可能な経路」が技術的に成立しているため AC を満たすと判断 |
| NFR 2.2 | OFL が禁ずる行為を行わない | OFL.txt 本文を同梱、フォント名 (`Manrope` / `JetBrains Mono`) を変更せず、フォント単体販売・OFL を含まない再配布は行わない |
| NFR 3.1 / 3.2 | 初期描画遅延 / 同一フレーム内描画 | bundle 方式（システム fontResource）は SystemBootClassLoader 経由で同期的にロードされるため、Downloadable Fonts と異なり async 解決のちらつきが発生しない（Req 3.2 は構造的に担保） |
| NFR 4.1 | fontScale が引き続き反映 | `TextAppearance.MaterialComponents.*` 系の親 style を継承し、`textSize` 等のスケール対応属性は親側の `sp` 指定をそのまま使用するため不変 |
| NFR 4.2 | コントラスト比 WCAG 2.1 AA 維持 | テキスト色は `textColorPrimary` 等の theme 属性経由で解決されており、本実装はフォントのみ変更してテキスト色を変更していないためコントラスト比は不変 |

## Open Questions と本実装での判断

requirements.md の Open Questions について、Developer 判断で確定した既定値:

1. **Manrope の同梱 weight 集合**: **Regular (400) / Medium (500) / SemiBold (600) / Bold (700)** の 4 weight を採用。`design/tokens.css` の 800 ExtraBold は本実装では同梱せず、weight 700 で代用（Android の `<font-family>` weight 解決機構が closest match を選ぶため runtime クラッシュは発生しない）。
2. **JetBrains Mono の同梱 weight 集合**: **Regular (400)** のみを採用。Issue #13 本文の案 B スコープ説明と整合（`design/tokens.css` の 500 / 700 は本実装では Regular で統一）。
3. **Noto Sans JP の取扱**: **同梱しない**。日本語表示は Android 既定の日本語フォントフォールバック（Noto Sans CJK 等）に委ねる（requirements Req 1.5 / Out of Scope）。
4. **APK サイズ上限**: requirements NFR 1.1 の **+3 MB 以内**を維持。実測（バイナリ合計のみ）は約 0.65 MB であり余裕で収まる。
5. **同梱フォントの適用範囲**: Material TextAppearance.* を経由する経路（headlines / subtitles / body / button / caption / overline）に加え、theme レベルで `android:fontFamily` も上書き。本文・見出し全般に均一に Manrope が適用される。
6. **フォントリソースの圧縮配置可否 (`aaptOptions.noCompress`)**: **`.ttf` を `noCompress` に追加しない**（既定の圧縮配置のまま）。NFR 1.1 (+3 MB 以内) を優先。bundle 方式のロード経路では同期解決のためちらつきは発生せず、NFR 3.2 への影響は最小と判断。

## 確認事項（人間判断が必要な事項 / レビュワー判断ポイント）

### テスト実行の未確認

- 本作業環境（`/home/hitoshi/.issue-watcher/worktrees/...` 配下の worktree）には **Java JDK がインストールされていない**（`which java` で見つからず、`sudo apt install openjdk-17-jdk-headless` は権限不足）。
- そのため `./gradlew testDebugUnitTest` および `./gradlew assembleDebug` を **実行できなかった**。
- 通常の開発環境（`/home/hitoshi/github/KeyNest/`）または Reviewer / PjM 側の環境で以下を確認することを依頼する:
  - `./gradlew testDebugUnitTest` の pass 確認（既存テストの非リグレッションと新規テスト 13 件の pass）
  - `./gradlew assembleDebug` 成功確認
  - 生成 APK のサイズ実測（フォント導入前との差分計測、NFR 1.1 (+3 MB) 検証）

### 設計レビューに差し戻すべき判断点

設計レビュー（人間レビュー）で確認・変更したい場合の判断ポイント:

1. **Manrope weight 800 ExtraBold を同梱するか**: 本実装では 700 で代用したが、`design/tokens.css` が 800 を明示している。同梱する場合は APK サイズ +96 KB 程度の追加コスト。
2. **JetBrains Mono に 500 Medium / 700 Bold を追加するか**: 本実装では Regular のみ。design tokens に合わせるなら追加。1 weight あたり 270 KB 程度の追加コスト。
3. **Noto Sans JP の bundle**: 日本語表示の典型実機挙動が端末ベンダー依存（Noto Sans CJK の有無）になる現状を許容するか、整合性のために bundle するか。同梱する場合 1〜2 MB の追加で NFR 1.1 の見直しが必要。
4. **OFL ライセンス本文を OSS ライセンス画面に組み込むタイミング**: 現状 `res/raw/` に静置のみ。Issue #10 の OSS ライセンス一覧画面の改修によってアプリ内からの表示経路を完成させる必要がある（requirements Out of Scope に明記）。**新規 Issue として切り出すことを推奨**:
   - 提案 Issue 名: `feat(licenses): expose bundled font OFL.txt in OSS licenses screen (followup of #13)`

### 既存仕様との小さな整合点

- `CredentialEditLayoutAuditTest#signatureHexValue_usesMonospaceFont` を本 Issue で `signatureHexValue_usesJetBrainsMonoFont` にリネーム・期待値更新した。Issue #14 由来の旧 Req 3.5 (「monospace で表示する」) を Issue #13 Req 2.2 (「JetBrains Mono で表示する」) に置き換える形になる。Issue #14 の AC を後退させていないこと（より具体的な typeface 指定への upgrade）はテストコメントに明示。

## 派生タスク（次の Issue として切り出す候補）

1. **OSS ライセンス画面への OFL 組込**: Issue #10 で実装される OSS ライセンス一覧画面に `R.raw.ofl_manrope` / `R.raw.ofl_jetbrains_mono` を追加するフォローアップ Issue
2. **Settings / Danger Zone / 一覧画面など他画面のフォント表示確認**: 本実装は theme レベル override で全画面に Manrope が伝播する設計だが、Issue #10 で実装される Settings 画面・Danger Zone 画面では実機表示確認を行うのが望ましい
3. **package name 表示・password 表示への JetBrains Mono 適用**: design/spec.md "Mono" 節は signature hex / credential id 以外の mono 表示も想定しているが、現状コード上にそれらの mono 指定箇所が無いため本 Issue では対応していない。各表示要素が新規追加されるタイミングで `@font/jetbrains_mono` を適用する
4. **weight 800 / Noto Sans JP の同梱検討**: 上記「設計レビューに差し戻すべき判断点」1〜3 のうち人間判断で「やる」と決まったものを別 Issue で実装

## 追加依存

- 新規ライブラリ依存追加なし。
- `androidx.core` 系の既存依存だけで `<font-family>` リソースの XML 解決は完結する。

## ネットワーク取得の記録

- 本実装ではビルド成果物に組み込むフォントを **作業時に外部から取得**したが、`AndroidManifest.xml` への `INTERNET` permission 追加は **行っていない**（実行時にネットワーク取得は発生しないため）。
- 取得した URL は本 impl-notes.md の「実装サマリ」節に明記済み。再取得が必要な場合は Google Fonts manifest API および JetBrains Mono GitHub release から再度入手可能。
