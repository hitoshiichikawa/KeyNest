# Requirements — Issue #5 デザイン適用

## 1. 概要

Issue #1 で実装された KeyNest MVP の UI（XML レイアウト + Material Components）を、
`design/` 配下に置かれたデザイン成果物（`design/spec.md` のトークン、`design/design.md` の
画面別仕様、`design/screens/*.jsx` の参照実装、`design/KeyNest Design.html` のモックアップ）
に沿って刷新する。

本 Issue のゴールは、既存の機能ロジック（ViewModel / Repository / DAO / AutofillService /
セキュリティ層）を一切変更せずに、ユーザーが直接触れる画面の見た目とトーン・マナーを
デザインファイルと整合させることである。デザイン適用後も Issue #1 の機能要件（クレデンシャル
登録・編集・削除、Autofill 候補表示、生体認証アンロック、AutofillService 有効化案内）が
そのまま動作することを必須とする。

本 Issue は **UI 層のみ** を対象とし、新機能の追加・データ層の変更・Autofill 挙動の変更は
行わない。Compose 化も本 Issue のスコープ外（現行 XML + ViewBinding ベースを維持）。

## 2. スコープ

### 2.1 In Scope

- `design/spec.md`（トークン定義）を Android リソースに反映:
  - `app/src/main/res/values/colors.xml`（カラートークン）
  - `app/src/main/res/values/themes.xml`（Material Theme: colorScheme, Typography, Shape の上書き）
  - `app/src/main/res/values/dimens.xml`（スペーシング・角丸トークン、必要なら新規作成）
  - light / dark の二系統（`values/` と `values-night/`）
- 既存実装済み画面の見た目刷新（XML レイアウト・カスタム View / drawable の更新）:
  - クレデンシャル一覧画面（`CredentialListActivity` / `credential_list_activity.xml` /
    `credential_list_item.xml`）— `design/screens/screens-1.jsx` の `ScreenListEmpty` /
    `ScreenListPopulated` および `design/design.md` Section 8 「Credential Card」相当
  - クレデンシャル編集画面（`CredentialEditActivity` / `credential_edit_activity.xml`）—
    `design/screens/screens-1.jsx` の `ScreenEdit` 相当
  - Autofill 有効化案内画面（`AutofillEnableActivity` / `autofill_enable_activity.xml`）—
    `design/screens/screens-1.jsx` の `ScreenOnboarding` 相当
  - インストール済みアプリ選択ボトムシート（`PackagePickerBottomSheet` /
    `package_picker_bottom_sheet.xml`）— `design/screens/screens-2.jsx` の `ScreenPicker` 相当
  - Autofill 候補ドロップダウン（`dataset_presentation.xml`）— `design/screens/screens-2.jsx`
    の `ScreenDataset` 内 `DatasetRow` 相当
- アプリアダプティブアイコン（`design/screens/icons.jsx` IconA「Shelter (recommended)」を採用、
  `design/spec.md` Section 6 のメモに従い foreground 432×432 / canvas 1024×1024）
- `app/src/main/res/values/strings.xml` の表記を `design/spec.md` Section 9「i18n キー命名」の
  日本語文言に合わせて更新（既存キーの value 差し替え。キー名追加は最小限）
- アクセシビリティ要件（最小タップ領域・コントラスト・monospace 表示）の遵守

### 2.2 Out of Scope

- 機能ロジックの追加・変更（ViewModel / UseCase / Repository / DAO / AutofillService /
  Security 層は **一切変更しない**）
- データスキーマ・暗号化方式・Autofill 挙動の変更
- Compose への置き換え（XML + ViewBinding を維持）
- `design/screens/screens-2.jsx` の `ScreenSettings`（設定画面）の新規追加 —
  既存実装に該当画面が存在せず、本 Issue は「既存 UI のデザイン適用」のみ扱う
- `design/screens/screens-2.jsx` の `ScreenUnlock`（BiometricPrompt のカスタムオーバーレイ）—
  既存実装は `BiometricPrompt` 標準 UI を使用しており、本 Issue で独自 Activity への置換は行わない
- `design/screens/screens-1.jsx` の `ScreenListPopulated` に登場する以下の要素は
  デザイン参考扱いとし、本 Issue では実装しない（既存機能に対応するロジック・データが無いため）:
  - 検索バー（`SearchBar`）
  - フィルタチップ（「署名 OK」「署名なし」「強度: 弱」）
  - 「最近使った」カルーセル
  - パスワード強度表示（強度判定ロジックが既存実装に存在しない）
  - 「並び替え」アクション
  - クレデンシャルカード右側の more (︙) メニュー — 既存実装の long-press 削除フローを維持
- Onboarding 画面のプログレスドット（3 ステップ）— 既存実装は単一画面で完結しており、
  本 Issue ではドット表示は省略可（残すかどうかは Architect/Developer 判断、Open Question 参照）
- 新規 i18n キーの追加（既存 key の文言差し替えのみ）
- フォントファミリ `Manrope` / `JetBrains Mono` / `Noto Sans JP` の Google Fonts プロバイダ経由
  ダウンロード — Manrope/JetBrains Mono の取り込み可否は Architect/Developer 判断、
  最低限デザイン上の太さ・サイズが再現できれば system default font fallback で可
- ネットワーク権限の追加（`design/design.md` の NFR 1.5 を尊重し、`INTERNET` permission は宣言しない）

## 3. ユーザーストーリー

- US-1: 業務端末利用者として、KeyNest を起動したときに `design/` のモックアップと同じ
  見た目（青を基調としたブランドカラー・統一されたカード UI・読みやすいタイポグラフィ）で
  クレデンシャル一覧を確認したい。視覚的な信頼感を得て安心して機微情報を扱うため。
- US-2: 業務端末利用者として、ダークモードに切り替えたときも本文・コントロール・カードが
  読みやすい配色で表示されてほしい。明所・暗所いずれでも利用するため。
- US-3: 業務端末利用者として、デザイン刷新後も既存の登録・編集・削除・Autofill 候補表示・
  認証アンロックがそのまま動作してほしい。アップデート後に再学習せず使い続けるため。

## 4. 受入基準（AC — EARS 記法）

### 4.1 デザイントークン反映

4.1.1. The system shall expose color resources whose Light theme values match `design/tokens.css`
       `.kn-light` の `--primary` (`#1F6FEB`)、`--primary-hover` (`#1457C9`)、`--bg`
       (`#F6F8FC`)、`--surface` (`#FFFFFF`)、`--surface-2` (`#F1F4FA`)、`--text` (`#0B1220`)、
       `--text-2` (`#5C6B8E`)、`--border` (`rgba(15,23,41,.08)`)、ステータス色
       `--kn-success` (`#10B981`) / `--kn-warning` (`#F59E0B`) / `--kn-danger` (`#EF4444`)
       と完全一致する。

4.1.2. The system shall expose color resources whose Dark theme values match `design/tokens.css`
       `.kn-dark` の `--bg` (`#0A1020`)、`--surface` (`#131C33`)、`--surface-2` (`#1A2540`)、
       `--text` (`#ECF1FA`)、`--text-2` (`#9CA9C7`)、`--primary` (`#4D8DF4`) と完全一致する
       （`values-night/` 配下で同名キーを上書き）。

4.1.3. The Material Theme shall map shape tokens to corner radii defined in `design/spec.md`
       Section 6 / `design/tokens.css` Section "Radii"
       (`small=12dp` / `medium=16dp` / `large=20dp` / `extraLarge=28dp`)。

4.1.4. The Material Theme shall set `colorPrimary` to the Light/Dark primary token
       (`#1F6FEB` / `#4D8DF4`) and `colorOnPrimary` to `#FFFFFF`.

4.1.5. The system shall NOT hard-code raw hex literals for primary/surface/text/status colors
       in layout XML or drawable XML — all such references shall resolve through
       theme attributes (`?attr/colorPrimary` 等) or named color resources.

4.1.6. Where dark mode is active on the device, the application shall render all updated screens
       using the Dark token set without text-on-text contrast failures
       (既存設計の `?attr/colorSurface` を `windowBackground` に指定する規約を維持)。

### 4.2 タイポグラフィ

4.2.1. The system shall expose text appearance styles aligned with `design/spec.md` Section 4
       roles (`App bar title` 26sp/800、`Section title` 19sp/800、`Card title` 15sp/700、
       `Body` 14sp/500–600、`Meta / caption` 12sp/500、`Eyebrow / overline` 11–12sp/700
       + .06em uppercase)。

4.2.2. While displaying package name または SHA-256 ハッシュ値, the system shall apply a
       monospace font family (`design/spec.md` Section 4 — JetBrains Mono、フォールバック可)。

4.2.3. The system shall enforce a minimum text size of 13sp for body text per `design/spec.md`
       Section 4 末尾。

### 4.3 クレデンシャル一覧画面

4.3.1. When the credential list contains 1 件以上, the system shall render each item as a
       horizontal card matching `design/screens/screens-1.jsx` `CredCard`:
       左に IconTile (44dp、12dp 角丸、ブランド色グラデ)、中央にラベル (15sp/700) と
       username (12sp/500、`text-2`)、`package name` (11sp/500、`text-3`、monospace)。

4.3.2. The credential list item shall apply a 1dp border using `--border` token と
       18–20dp 角丸 (`r-lg`) と Light テーマ shadow-1 を満たす elevation。

4.3.3. When the credential list is empty, the system shall render an empty-state composition
       matching `design/screens/screens-1.jsx` `ScreenListEmpty`:
       中央に KeyNest brand mark + ヘッダ「最初の鍵を巣に入れよう」+ 説明文 +
       primary CTA「クレデンシャルを登録」+ フッタ "AES-GCM · 端末ローカルのみ"。

4.3.4. The credential list app bar shall render an eyebrow label "KeyNest"
       (12sp/600/`text-2`、`.04em` letter-spacing) above a title "Vault"
       (`App bar title` 26sp/800) per `design/screens/screens-1.jsx` `ListAppBar`。

4.3.5. The credential add affordance shall be rendered as a primary action button
       (Material primary color, `r-md` 角丸、`design/spec.md` Section 8 `.kn-btn` 仕様の
       高さ 52dp 相当) or floating action button stylistically aligned with the design,
       and shall keep launching `CredentialEditActivity.newIntent(this)` as the existing
       implementation does (機能ロジック不変)。

4.3.6. While a credential row is long-pressed, the system shall continue to surface the
       existing delete confirmation dialog (既存挙動を変更しない)。

### 4.4 クレデンシャル編集画面

4.4.1. The credential edit screen shall present a top app bar matching
       `design/screens/screens-1.jsx` `ScreenEdit`:
       左に close icon、中央にタイトル（新規=「新しいクレデンシャル」、編集=「クレデンシャル編集」）、
       右にテキスト型 primary action「保存」。

4.4.2. The credential edit screen shall render a "target app card" section at the top
       containing IconTile + 表示名 + monospace パッケージ名 + 署名ステータスチップ
       per `design/screens/screens-1.jsx` `ScreenEdit` の上部カード。

4.4.3. Each input field shall be rendered as an outlined field per
       `design/screens/screens-1.jsx` `Field` / `PasswordField`:
       eyebrow ラベル (12sp/700、`.04em` uppercase、`text-2`) + 14dp 角丸 / 1dp `border-strong`
       / フォーカス時 2dp primary 縁取り。

4.4.4. While password 入力フィールドにフォーカスがあるとき、the system shall display the
       password text in monospace with `.1em` letter-spacing per `design/spec.md` Section 10
       「パスワード表示は monospace + letter-spacing .1em で誤読防止」。

4.4.5. The password field shall provide a visibility toggle (eye / eye-off icon)
       per `design/screens/screens-1.jsx` `PasswordField` の末尾アイコン。

4.4.6. If `mode == edit`, the system shall render a destructive delete button at the
       bottom of the screen with `--kn-danger` color and 1dp `border` outline
       per `ScreenEdit` の `isEdit` ブランチ。

4.4.7. The "Pick installed app" affordance shall continue to launch
       `PackagePickerBottomSheet` (既存挙動)、UI は `design/screens/screens-1.jsx` の
       package 入力下「変更」テキストボタンまたは現行 MaterialButton TextButton と同等の
       配置を維持する。

### 4.5 インストール済みアプリ選択ボトムシート

4.5.1. The bottom sheet shall use a top-radius of 28dp (`r-xl`) and render a grab handle
       (36×4dp、`--border-strong`) at the top per `design/spec.md` Section 8 「Bottom sheet」。

4.5.2. The bottom sheet header shall display a title「アプリを選択」(19sp/800) and an
       eyebrow subtitle「署名情報も同時に取得します」per `design/screens/screens-2.jsx`
       `ScreenPicker`。

4.5.3. Each installed-app row shall render IconTile (40dp、11dp 角丸) + アプリ名 (14sp/700)
       + monospace パッケージ名 (11sp/500、`text-3`) per `screens-2.jsx` `PickerRow`。

4.5.4. When a row is selected, the system shall render the row background with
       `--surface-tint` and show a check icon in `--primary` color per `PickerRow` の
       `selected` ブランチ。

### 4.6 Autofill 有効化案内画面

4.6.1. The Autofill enable screen shall render a hero illustration area, a title
       (28sp/800「Android の Autofill を KeyNest に切り替える」), a description block, and
       a 3 ステップ手順カード per `design/screens/screens-1.jsx` `ScreenOnboarding`。

4.6.2. The Autofill enable screen shall display a primary CTA「設定を開く」(52dp 高 /
       `r-md` 角丸 / `--primary` 背景 / `--on-primary` テキスト) anchored near the
       bottom of the screen and shall keep launching
       `Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE` (既存挙動)。

4.6.3. While `AutofillServiceStatus.isCurrentService(this)` returns true, the system shall
       render the "already enabled" state distinctly (既存の `text_already_enabled`
       に相当する表示を、デザインに沿ったチップ／インフォメーションとして提示)
       and keep using `AutofillServiceStatus` for the check (既存ロジック不変)。

### 4.7 Autofill 候補ドロップダウン

4.7.1. The dataset presentation `RemoteViews` shall render a row matching
       `design/screens/screens-2.jsx` `DatasetRow`:
       左に 32dp IconTile (9dp 角丸)、中央にラベル (13sp/700) と username (11sp/500、`text-2`)。
       既存の `dataset_presentation.xml` を更新し、`dataset_label` / `dataset_subtitle` の
       2 TextView を維持しつつ視覚仕様を一致させる（バインド側のコードは変更しない）。

4.7.2. The dataset presentation shall preserve the existing two-id contract
       (`@+id/dataset_label`, `@+id/dataset_subtitle`) used by
       `DatasetPresentationFactory` so that no Kotlin code change is required。

### 4.8 アプリアイコン

4.8.1. The system shall ship an adaptive launcher icon whose foreground/background design
       matches `design/screens/icons.jsx` `IconA` ("Shelter — recommended"): クリーム背景
       (`#F4EBDC`) + ブルーグラデ屋根 + 中央のキー穴。

4.8.2. The adaptive icon foreground shall fit within the 432×432 safe zone of a
       1024×1024 canvas per `design/spec.md` Section 6 末尾。

### 4.9 文言（i18n）

4.9.1. The system shall display the credential list title as「Vault」(EN) /「Vault」(JP)
       per `design/spec.md` Section 9。

4.9.2. The system shall display the empty state message as「最初の鍵を巣に入れよう」(JP) /
       「Tap + to add your first credential.」(EN) per `design/spec.md` Section 9。

4.9.3. The system shall display the signature verified chip text as「署名一致」(JP) /
       「Signature verified」(EN) per `design/spec.md` Section 9。

4.9.4. The system shall display the signature unavailable text as「署名なし」(JP) /
       「Signature unavailable」(EN) per `design/spec.md` Section 9。

4.9.5. The system shall maintain the existing string resource keys (no rename) so that
       Kotlin / layout references continue to compile without code changes.

### 4.10 既存機能との整合

4.10.1. The system shall preserve all behaviors specified in
        `docs/specs/1--easykeynest-mvp-packagename-autofill/requirements.md`
        (Requirements 1–7 and NFR 1–5).

4.10.2. The system shall NOT modify any file under `app/src/main/java/com/example/keynest/`
        outside of UI-binding adjustments strictly required by layout id changes
        (i.e. ViewBinding-generated class references). ViewModel / UseCase / Repository /
        DAO / Security / AutofillService の `.kt` 内ロジックは変更しない。

4.10.3. When all existing unit tests and instrumented tests run after the redesign,
        they shall all pass (regression なし)。

4.10.4. The system shall keep `AndroidManifest.xml` permission declarations unchanged
        (specifically: no `android.permission.INTERNET` addition; existing
        `BIND_AUTOFILL_SERVICE` etc. remain)。

## 5. デザイン適用対象の画面・コンポーネント一覧

| 既存ファイル | 対応デザイン参照 | 主な変更点 |
|---|---|---|
| `res/values/colors.xml` | `design/tokens.css` `.kn-light` | ブランド/ニュートラル/ステータスカラー追加 |
| `res/values-night/colors.xml`（新規） | `design/tokens.css` `.kn-dark` | ダークテーマ用カラー |
| `res/values/themes.xml` | `design/spec.md` §11 | Material Theme の `colorScheme` / `Typography` / `Shape` をトークンで上書き |
| `res/values/dimens.xml`（必要なら新規） | `design/spec.md` §5,§6 | spacing / radius トークン |
| `res/layout/credential_list_activity.xml` | `screens-1.jsx` `ScreenListPopulated` / `ScreenListEmpty` | AppBar の eyebrow + title、空状態の brand mark + CTA、FAB→ primary button への置換可 |
| `res/layout/credential_list_item.xml` | `screens-1.jsx` `CredCard` | IconTile、ラベル/サブタイトル/パッケージ名の階層、`r-lg` カード化 |
| `res/layout/credential_edit_activity.xml` | `screens-1.jsx` `ScreenEdit` | 上部 target app card、`Field` 系入力欄、password の monospace + .1em、削除ボタン |
| `res/layout/autofill_enable_activity.xml` | `screens-1.jsx` `ScreenOnboarding` | hero + ステップカード + primary CTA |
| `res/layout/package_picker_bottom_sheet.xml` | `screens-2.jsx` `ScreenPicker` | 28dp top-radius、grab handle、IconTile 行 |
| `res/layout/dataset_presentation.xml` | `screens-2.jsx` `DatasetRow` | 行レイアウト（既存 id 維持） |
| `res/mipmap-anydpi-v26/ic_launcher*.xml`（新規/更新） | `design/screens/icons.jsx` `IconA` | アダプティブアイコン |
| `res/values/strings.xml` | `design/spec.md` §9 | 既存キーの value を JP/EN ともに調整 |

## 6. デザイントークン

`design/spec.md` および `design/tokens.css` の以下を Android リソースに反映する。
詳細マッピングは `design.md`（Architect）で確定する想定。

- **カラー**: Light は `values/colors.xml`、Dark は `values-night/colors.xml`。
  トークン → `colorPrimary` / `colorOnPrimary` / `colorSurface` / `colorOnSurface` /
  `colorError` 等の Material Theme attribute にもマッピング。
- **タイポグラフィ**: Material `Typography` の `displayLarge`(App bar title) /
  `titleLarge`(Section title) / `titleMedium`(Card title) / `bodyMedium`(Body) /
  `labelSmall`(Meta) / `labelMedium`(Eyebrow) に役割を割当。font family は最低限
  system default sans + monospace、追加導入は Architect 判断。
- **スペーシング**: 4pt スケール (4/8/12/16/20/24/32/40/48/64dp) を `dimens.xml` または
  カスタムテーマ attribute として表現。
- **角丸**: Material `Shape` を `small=12dp / medium=16dp / large=20dp / extraLarge=28dp` に固定。
- **シャドウ**: Material elevation を Light は 1/2/3dp 段階、Dark は背景を一段沈める方針で
  代用（Compose 以外で CSS shadow を厳密再現するのは困難なため、近似で可）。

## 7. 非機能要件

### NFR 1: アクセシビリティ

NFR 1.1. The system shall provide a minimum interactive target size of 44×44 dp for all
        tappable controls per `design/spec.md` §10。

NFR 1.2. The system shall achieve a body-text contrast ratio of 4.5:1 or better in Light theme
        and 7:1 or better in Dark theme per `design/spec.md` §10。

NFR 1.3. While an icon button has no visible label, the system shall expose a
        `contentDescription` (or equivalent) for screen readers (既存 `contentDescription`
        を維持・拡充)。

### NFR 2: ダークモード

NFR 2.1. Where the device system theme is set to dark, the system shall render every updated
        screen using the Dark token set (`values-night/`) without falling back to the
        Light palette。

NFR 2.2. While switching between light and dark mode, the system shall preserve all credential
        list state and edit field input (既存設計の `Theme.MaterialComponents.DayNight`
        親テーマと `?attr/colorSurface` windowBackground 規約を維持)。

### NFR 3: 既存テストとの互換

NFR 3.1. When the existing JVM unit tests and AndroidX instrumented tests are executed against
        the redesigned UI, the test pass rate shall remain 100% (機能回帰ゼロ)。

NFR 3.2. The system shall preserve all `@+id/...` references used by Kotlin code
        (e.g. `dataset_label`, `dataset_subtitle`, `recycler`, `fab_add`, `toolbar`,
        `input_*`, `btn_*`, `empty_view`, `text_already_enabled`); rename は不可。

### NFR 4: パフォーマンス

NFR 4.1. The system shall not introduce per-frame work exceeding 16 ms on the credential list
        scroll path (existing performance NFR 2.1 of Issue #1 を破らないこと)。

NFR 4.2. The system shall not perform network I/O at any redesigned screen
        (Issue #1 NFR 1.5 を継承)。

## 8. 制約・前提

- 既存機能ロジック・データ層・Autofill 挙動は変更しない（Section 4.10 参照）。
- 既存テストを壊さない（NFR 3.1 / 3.2）。
- ブランチ運用ルールに従い、`develop` / `main` への直接 push は行わず PR 経由で取り込む。
- `design/` 配下のファイルは git に push 済み (Issue #5 コメントで確認済み)。
- 現行 UI は XML レイアウト + Material Components (View-based) ベース。本 Issue では
  Compose への移行は行わない。
- 新規依存ライブラリ追加は最小限とする。Manrope/JetBrains Mono フォントの取り込みは
  Architect 判断（採用する場合は Downloadable Fonts / Google Fonts provider を推奨、
  ネットワーク前提のためオフライン同梱に置換する場合はその旨を `design.md` で明示）。

## 9. 確認事項（Open Questions）

- OQ-1: `design/screens/screens-1.jsx` `ScreenOnboarding` 上部の 3 ステップ・プログレスドットは、
  現行 `AutofillEnableActivity` が単一画面で完結するため不要と判断したが、将来の onboarding 拡張を
  見据えてドット表示だけ先に置くべきか — Architect 判断に委ねる（Out of Scope に倒し可）。
- OQ-2: フォントファミリ `Manrope` / `JetBrains Mono` の取り込み手段（Downloadable Fonts vs
  アプリ同梱 TTF vs system default fallback）— 本 Issue では「最低限デザイン上の太さ・サイズが
  再現できれば fallback で可」と要件化したが、最終的な選択は Architect / Developer 判断。
- OQ-3: 既存 FAB（`fab_add`）を `design/screens/screens-1.jsx` の primary button 風 CTA に
  完全置換するか、FAB は維持しつつ色だけトークン化するか — 4.3.5 で「機能ロジック不変」を必須に
  したうえで見た目選択肢を残しているが、最終形は `design.md` で確定する。
- OQ-4: `design/screens/screens-1.jsx` `CredCard` 内の「署名 OK」シールドアイコン・パスワード強度バー
  は本 Issue 範囲外（Out of Scope）としたが、署名取得済みの credential については
  `signatureSha256 != null` で機械的に判定可能なため、シールドアイコンのみ追加実装することは
  許容するか — Out of Scope に倒すかどうかを `design.md` で再確認したい。
- OQ-5: ダークモードの primary 色 (`#4D8DF4`) と Light の primary (`#1F6FEB`) の使い分けにより、
  `colorPrimary` を DayNight で切替する前提だが、既存 `colors.xml` には単一色しか定義されていない。
  `values-night/colors.xml` 新規作成 + 既存 `keynest_primary` の上書きで合意可か（要 Architect 確認）。
