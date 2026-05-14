# Requirements Document

## Introduction

KeyNest はこれまで `design/tokens.css` / `design/spec.md` を設計の単一情報源として保持してきたが、
Android リソース層には Material 3 デフォルト + 3 色のスタブ（`keynest_primary` 等）しか反映されておらず、
画面実装ごとにトークンが再発明される状態にある。本 Issue (#28) は **Phase 1 (foundation)** として、
`design/android-assets/res/` 配下に既に整備済みの設計トークン群（colors / dimens / typography /
themes / fonts / 日本語 strings / launcher icon drawable / adaptive icon mipmap）を、
`app/src/main/res/` の対応リソースに 1:1 で取り込むことだけを扱う。
個別画面の widget 入れ替えや layout 書き換え（Phase 2）は本 Issue のスコープ外であり、
取り込み後も既存画面は「色味が変わっただけ」で従来どおり動作することが要請される。
PR #25 (Material 3 移行 / Issue #24) と PR #27 (アイコン整備) で導入された変更は、
本 Issue 完了後も保持される必要がある。

## Requirements

### Requirement 1: デザイントークンのリソース取り込み範囲

**Objective:** As a Android アプリ開発者, I want `design/android-assets/res/` のトークン群を `app/src/main/res/` に 1:1 で取り込みたい, so that 以後の画面実装で `?attr/colorSurface` や `@dimen/kn_r_md` を参照するだけでデザイン仕様に揃えられる

#### Acceptance Criteria

1. The KeyNest Android resource layer shall 設計トークン由来の色定義を `values/colors.xml` に保持する（raw palette および `kn_*` semantic 層を含む）
2. The KeyNest Android resource layer shall 寸法トークン（radii / spacing / コンポーネント寸法 / elevation）を `values/dimens.xml` に保持する
3. The KeyNest Android resource layer shall タイポグラフィロール（`Text.KeyNest.*` TextAppearance 群）を `values/type.xml` に保持する
4. The KeyNest Android resource layer shall Material 3 attribute（`colorPrimary` / `colorSurface` / `colorOutline` 等）を `kn_*` semantic 色に紐付けるテーマ定義を `values/themes.xml` に保持する
5. The KeyNest Android resource layer shall Manrope（`@font/manrope_family` を含む weight 別ファミリ定義）と JetBrains Mono のフォントリソースを `font/` 配下に保持する
6. The KeyNest Android resource layer shall 日本語ローカライズ済み文字列を `values-ja/strings.xml` に保持する
7. The KeyNest Android resource layer shall アダプティブランチャーアイコンの drawable（背景 / 前景 / モノクロ）と adaptive icon mipmap を提供する
8. Where 取り込み対象のトークン値が `design/android-assets/res/` 内のソースと意味的に一致しない場合, the KeyNest Android resource layer shall ソース側の値を正として採用する

### Requirement 2: ライト / ダーク二系統の同時提供

**Objective:** As a エンドユーザー, I want ライトモードとダークモードのどちらでも適切なコントラストで KeyNest を使いたい, so that システム設定に追従して常に読みやすい状態で利用できる

#### Acceptance Criteria

1. The KeyNest Android resource layer shall ライトテーマ用のセマンティック色定義（`kn_bg` / `kn_surface` / `kn_text` 等）を `values/colors.xml` に保持する
2. The KeyNest Android resource layer shall ダークテーマ用のセマンティック色上書きを `values-night/colors.xml` に保持する
3. While 端末のシステム設定がダークモードである場合, the KeyNest Android resource layer shall `values-night` の上書きを優先して解決する
4. While 端末のシステム設定がライトモードである場合, the KeyNest Android resource layer shall `values/colors.xml` のセマンティック色を解決する
5. The KeyNest Android resource layer shall ライト / ダークいずれのモードにおいても、本文テキスト（`kn_text` 等の primary text role）と背景（`kn_bg` / `kn_surface`）の組み合わせが WCAG 2.1 AA の本文最小コントラスト比 4.5:1 以上となる色値を保持する
6. The KeyNest Android resource layer shall ライト / ダークいずれのモードにおいても、UI コンポーネント輪郭・補助 icon と背景の組み合わせが WCAG 2.1 AA の非文字最小コントラスト比 3:1 以上となる色値を保持する

### Requirement 3: アダプティブランチャーアイコンの統一

**Objective:** As a エンドユーザー, I want ホーム画面のランチャーアイコンが Phase 1 取り込み後も正しく表示されたい, so that アイコンが欠落・破損した状態でアプリを起動せずに済む

#### Acceptance Criteria

1. The KeyNest Android resource layer shall `mipmap-anydpi-v26/ic_launcher.xml` として adaptive icon 定義を提供する
2. The KeyNest Android resource layer shall 通常版 (`ic_launcher`) と round 版 (`ic_launcher_round`) の双方の adaptive icon 定義を提供する
3. If 旧形式の `mipmap-*/ic_launcher*.png` が `app/src/main/res/` に残存している場合, the KeyNest Android resource layer shall 取り込み完了時点でそれらを保持しない
4. When ユーザーが API 26 以上の端末で KeyNest をインストールしホーム画面を開いたとき, the Android launcher shall 新しい adaptive icon を欠落・破損なく表示する

### Requirement 4: 既存画面の振る舞い不変

**Objective:** As a 既存ユーザー, I want Phase 1 取り込みによって既存画面の動作・遷移・操作性が壊されないこと, so that 「色味だけ変わった」体感でアップデートを受け入れられる

#### Acceptance Criteria

1. The KeyNest application shall Phase 1 取り込み後も既存 layout XML（`credential_list_activity.xml` / `credential_edit_activity.xml` / `autofill_enable_activity.xml` / `package_picker_bottom_sheet.xml` / `dataset_presentation.xml` / `settings_activity.xml` / `danger_zone_activity.xml` / `oss_licenses_*.xml`）の View 階層・ID・属性構造を保持する
2. When ユーザーが Phase 1 取り込み後の APK で `CredentialListActivity` を起動したとき, the KeyNest application shall クラッシュせず一覧画面を表示する
3. When ユーザーが Phase 1 取り込み後の APK で既存画面（クレデンシャル編集 / Autofill 有効化 / 設定 / Danger Zone / OSS ライセンス / Package Picker）を順次開いたとき, the KeyNest application shall いずれの画面においてもクラッシュせず描画する
4. The KeyNest application shall Phase 1 取り込み後も既存画面の文字列キー・ナビゲーション・押下挙動を変更しない（差分は色 / 余白 / フォント等の見た目に限定する）
5. While 端末のロケールが日本語に設定されている場合, the KeyNest application shall `values-ja/strings.xml` に定義された訳語で既存画面の文字列を表示する
6. While 端末のロケールが日本語以外に設定されている場合, the KeyNest application shall 既定 `values/strings.xml`（英語）の文字列で既存画面を表示する

### Requirement 5: ビルド・テスト・既存 PR との整合

**Objective:** As a メンテナ, I want Phase 1 取り込みが既存ビルドと既存テスト・既存 PR の成果を破壊しないこと, so that 安全に main へマージできる

#### Acceptance Criteria

1. When `./gradlew :app:assembleDebug` を CI またはローカルで実行したとき, the Android build pipeline shall 当該タスクを成功（exit code 0）で終了する
2. When 既存の単体テストスイートを Phase 1 取り込み後に実行したとき, the Android test runner shall 既存テストのうち Phase 1 取り込み前に成功していたものをすべて成功させる
3. The KeyNest Android resource layer shall PR #25 (Issue #24) で導入された Material 3 への移行（`Theme.Material3.DayNight.NoActionBar` 系の親テーマ採用、`Text.KeyNest.*` / `TextAppearance.KeyNest.*` の Material 3 親への接続、`Theme.KeyNest.Translucent` の挙動）を保持する
4. The KeyNest Android resource layer shall PR #27 で整備されたランチャーアイコン関連リソースとの整合性を保持する（取り込み後に PR #27 の到達点と矛盾しない adaptive icon 一式を提供する）
5. If 既存コードが参照しているリソース名（`@color/keynest_primary` / `@color/keynest_primary_variant` / `@color/keynest_secondary` 等のエイリアス、および既存 strings キー）が存在する場合, the KeyNest Android resource layer shall それらのリソース名解決を維持する（直接削除またはリネームしない）

## Non-Functional Requirements

### NFR 1: アクセシビリティ

1. The KeyNest Android resource layer shall ライト / ダーク両モードで本文テキスト（primary text role）と背景の組み合わせが WCAG 2.1 AA 基準（コントラスト比 4.5:1 以上）を満たす色値を保持する
2. The KeyNest Android resource layer shall ライト / ダーク両モードでアクティブな UI コンポーネント輪郭（border / outline / focus ring）と背景の組み合わせが WCAG 2.1 AA 基準（コントラスト比 3:1 以上）を満たす色値を保持する

### NFR 2: ローカライズ

1. The KeyNest Android resource layer shall `values/strings.xml`（既定 = 英語）と `values-ja/strings.xml`（日本語）に同一キー集合（既存キーすべて）を保持する
2. If `values-ja/strings.xml` に既定 `values/strings.xml` の特定キーが欠落する場合, the KeyNest Android resource layer shall 当該キーをローカライズ未完了として明示し、ビルドが既存挙動どおり既定 `values/strings.xml` にフォールバックできるよう保持する

### NFR 3: 互換性

1. The KeyNest Android resource layer shall 取り込み完了時点で、Phase 1 取り込み前の既存 `Manifest` の `<application android:theme="@style/Theme.KeyNest">` 宣言を変更せずに動作する
2. The KeyNest Android resource layer shall 取り込み完了時点で、`Theme.KeyNest.Translucent`（`AutofillUnlockActivity` が利用する透過テーマ）の従来挙動を保持する

## Out of Scope

- 個別画面の widget 入れ替え・layout 書き換え（Phase 2 の各画面 1 PR で扱う）
- 検索バー / フィルター chip 行 / 「最近使った」横スクロール / カード型行 / 強度バー / 署名 chip 等の新規 UI 部品実装（Phase 2）
- 設定画面 (`ScreenSettings`) や Unlock 画面 (`ScreenUnlock`) の新規 UI 構築（Phase 2）
- Autofill dropdown (`dataset_presentation.xml`) のブランディング適用（Phase 2）
- Compose 化（`KeyNestTheme.kt` の Compose 配線は将来用、本 Issue では取り込み対象外）
- Manrope ExtraBold (weight 800) のバンドル化（Issue #13 NFR 1.1 に従い weight 700 でフォールバック解決される現行運用を維持。weight 800 ファイルを APK に追加しない）
- Noto Sans JP の APK バンドル（既存方針を踏襲し、本 Issue では同梱しない）
- 動的カラー（Material You / `dynamicColorThemeOverlay`）の有効化（テーマ側で明示的に OFF を保持する）
- Phase 2 着手の優先順序決定（`mapping.md` §5 の推奨はあるが、Issue 化は別途）

## Open Questions

- なし（受入基準・スコープ・取り込み対象は Issue 本文・`design/android-assets/README.md` §「Phase 1 — Foundation を入れる」・`design/android-assets/mapping.md` から確定可能と判断）

## 確認事項（人間レビュワー向け）

- 本 requirements は実装手順（どのファイルをどの順序でコピー / 置換するか）を含めていない。具体的なファイル操作と順序は `design/android-assets/README.md` §「Phase 1 — Foundation を入れる」（10 手順）を Architect / Developer がそのまま参照する想定で問題ないか
- Requirement 5.5 で「既存エイリアス（`keynest_primary` 等）の解決を維持する」と要請しているが、`design/android-assets/res/values/colors.xml` 末尾の "Aliases the existing code already references" 節で既に提供されているため、取り込み時にこの節を欠落させないことの確認に留めて差し支えないか
- Requirement 3.2 で round 版 adaptive icon (`ic_launcher_round.xml`) を要請しているが、`design/android-assets/res/mipmap-anydpi-v26/` には `ic_launcher.xml` のみ存在し、`README.md` §7 では「round 版も同じ内容で `ic_launcher_round.xml` として置く」と指示している。当該複製を Phase 1 のスコープに含めて差し支えないか（含めない場合、Issue #28 の「PR #27 retention」要件に沿うため別 Issue 化が必要）
