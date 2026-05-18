# KeyNest — Design Tokens

実装 (Claude Code) で利用する単一情報源。色・タイポ・余白・角丸はすべて
このトークンを参照する。Android 側では `app/src/main/res/values/` に
`colors.xml` / `themes.xml` / `dimens.xml` として落とし込む想定。

---

## 1. ブランド

| Token | Hex | 用途 |
|---|---|---|
| `kn-blue-500` | `#1F6FEB` | Primary / 主要 CTA・選択状態 |
| `kn-blue-600` | `#1457C9` | Pressed / Primary hover |
| `kn-blue-700` | `#0F4AA8` | Hero グラデ終点 |
| `kn-blue-50`  | `#EAF2FE` | Selected / Tint background |
| `kn-accent-500` | `#6366F1` | 補助強調 (Settings 等のチップ) |

## 2. ニュートラル

| Light  |  Dark  |
|---|---|
| bg `#F6F8FC` | bg `#0A1020` |
| surface `#FFFFFF` | surface `#131C33` |
| surface-2 `#F1F4FA` | surface-2 `#1A2540` |
| text `#0B1220` | text `#ECF1FA` |
| text-2 `#5C6B8E` | text-2 `#9CA9C7` |
| border `rgba(15,23,41,.08)` | border `rgba(255,255,255,.06)` |

## 3. ステータス

| Token | Hex | 用途 |
|---|---|---|
| `success` | `#10B981` | パスワード強度: 強 / 署名 OK |
| `warning` | `#F59E0B` | 強度: 中 / 署名未取得 |
| `danger`  | `#EF4444` | 強度: 弱 / 削除 |

## 4. タイポグラフィ

- UI: **Manrope** (700/800 を多用, -.02em letter-spacing) + **Noto Sans JP** (JP)
- Mono: **JetBrains Mono** (package name / SHA-256 / password)

| Role | Size / Weight |
|---|---|
| App bar title | 26 / 800 |
| Section title | 19 / 800 |
| Card title    | 15 / 700 |
| Body          | 14 / 500–600 |
| Meta / caption | 12 / 500 |
| Eyebrow / overline | 11–12 / 700 + .06em uppercase |

最小 13 sp。日本語は Noto Sans JP fallback で `feature-settings: 'palt'` 推奨。

## 5. スペーシング (4pt)

```
4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 48 · 64
```

- 画面横パディング: **20px** (Edit/Onboarding/Settings) / **16px** (List)
- カード内パディング: **14–18px**
- セクション間: **24px**

## 6. 角丸

| Token | px | 用途 |
|---|---|---|
| `r-xs` | 8  | 小さい chip |
| `r-sm` | 12 | inline pill / input |
| `r-md` | 14–16 | 入力フィールド・アイコン下地 |
| `r-lg` | 18–20 | クレデンシャルカード・グループ |
| `r-xl` | 28 | Bottom sheet / Unlock card |
| `r-pill` | 999 | tag chip |

アプリアイコン (Adaptive): foreground 432x432 / canvas 1024x1024 / mask 22% squircle。

## 7. シャドウ

```
shadow-1 : 0 1px 2px rgba(15,23,41,.06)
shadow-2 : 0 4px 12px rgba(15,23,41,.08)
shadow-3 : 0 12px 32px rgba(15,23,41,.14)   ← 浮遊カード / sheet
```

Dark では `rgba(0,0,0,.4–.55)` ベースに置換。

## 8. コンポーネント仕様 (要点)

### Credential Card
- 横並び: `IconTile(44)` + (title / username / strength · pkg) + `more (24)`
- 高さ実測 ≒ 84px、`gap: 12`、`padding: 14`
- 署名OK バッジ: タイトル右に `shield-fill` 13px (success)

### Strength Bar
- 3 セグメント × 14 × 4 px、`gap: 2`、色は status の `weak/medium/strong`
- ラベル併記時: `UPPERCASE 11/700 + .04em`

### Bottom sheet
- top-radius 28、grab handle 36×4、`padding: 12 0 24`
- セクション見出し: 11/700 + .08em UPPERCASE, color `text-3`

### Autofill dropdown (dataset presentation)
- 幅: ホストの input にフィット、影 `0 16px 48px rgba(15,23,41,.18)`
- ヘッダー: KeyNest mark + "KEYNEST" eyebrow + 署名一致 chip
- 行: `IconTile(32)` + title/user + `lock(16)`
- 末尾: "KeyNest で新規作成" (primary, plus icon)

### Biometric unlock card
- 全画面ディム `rgba(11,18,32,.55)`
- カード: `bg-elev` / radius 24 / padding 24 / shadow-3 強め
- ターゲット chip + 指紋リング (96px, surface-tint 背景) + fallback ボタン

## 9. i18n キー命名 (現状の strings.xml を踏襲)

| Key | EN | JP |
|---|---|---|
| `credential_list_title` | Vault | Vault |
| `credential_list_empty` | Tap + to add your first credential. | 最初の鍵を巣に入れよう |
| `action_add_credential` | Add credential | クレデンシャルを登録 |
| `credential_edit_title_new` | New credential | 新しいクレデンシャル |
| `credential_edit_title_edit` | Edit credential | クレデンシャル編集 |
| `label_display_name` | Display name | 表示名 |
| `label_username` | Username | ユーザー名 / メール |
| `label_password` | Password | パスワード |
| `autofill_enable_title` | Switch Android Autofill to KeyNest | Android の Autofill を KeyNest に切り替える |
| `biometric_prompt_title` | Unlock credential | クレデンシャルをアンロック |
| `signature_match` | Signature verified | 署名一致 |
| `signature_missing` | Signature unavailable | 署名なし |

> 文体: 日本語は「です・ます」、敬語は最小限。SHA-256 / Autofill / Vault などの
> 技術語は原語のまま。

## 10. アクセシビリティ

- タップ領域 最低 44×44 dp (icon button は 40 視覚 + 4 余白)
- コントラスト: Light 本文 4.7:1+ / Dark 本文 7:1+ (text `#ECF1FA` on `#131C33`)
- パスワード表示は `monospace + letter-spacing .1em` で誤読防止
- パッケージ名は常に monospace で 1 行 ellipsis (詳細設定では折り返し可)

## 11. Token → Android Resource Mapping

`android-assets/mapping.md` を **必ず** 参照すること。本 spec の値は
`android-assets/res/values/{colors,dimens,themes,type}.xml` および
`values-night/colors.xml` に 1:1 でマップされており、実装側はそのトークンだけを
引いて作業する。手書きで dp / hex を入れない。

| 設計トークン | Android リソース |
|---|---|
| `var(--primary)` | `?attr/colorPrimary` → `@color/kn_primary` |
| `var(--surface)` | `?attr/colorSurface` → `@color/kn_surface` |
| `var(--text)` | `?attr/colorOnSurface` → `@color/kn_text` |
| `var(--text-2)` | `?attr/colorOnSurfaceVariant` → `@color/kn_text_2` |
| `var(--border)` | `?attr/colorOutlineVariant` → `@color/kn_border` |
| `--kn-r-md` | `@dimen/kn_r_md` (16dp) |
| `--kn-r-lg` | `@dimen/kn_r_card` (18dp) for cards |
| `--kn-r-xl` | `@dimen/kn_r_sheet` (28dp) for bottom sheets |
| Manrope 800/28 | `style="@style/Text.KeyNest.Display"` |
| Manrope 800/26 (Vault title) | `Text.KeyNest.TitleL` |
| Manrope 700/15 (Button) | `Text.KeyNest.LabelL` |
| JetBrains Mono 12 (package) | `Text.KeyNest.Mono` |

> 完全な表は `android-assets/mapping.md` §1〜4 を参照。

## 12. Android 実装メモ

- `colors.xml` には light/dark で同名キーを `values/` と `values-night/` に分ける
- `MaterialTheme` の `colorScheme` を上記トークンで上書き
- `Typography` の `displayLarge / headlineMedium / titleMedium / bodyMedium / labelSmall` に
  Manrope / Noto Sans JP を割当 (Compose は `FontFamily.of()` + Google Fonts Provider)
- `Shape` は `small=12 / medium=16 / large=20 / extraLarge=28`
- アイコンは `androidx.compose.material.icons.Outlined` を基本、本デザインの
  shield-fill / fingerprint カスタムは `material-symbols` outlined / filled を使い分け
