# Token → Android Resource Mapping

> 設計トークン (`design/tokens.css`) と各画面 (`design/screens/*.jsx`) を
> Android リソースに 1:1 で写すための単一情報源。
>
> 実装者 (人間 / AI) はこの表だけ見れば、レイアウト XML / Kotlin で
> 「どの dp / 色 / TextAppearance を使えばよいか」が決まります。

---

## 0. 大原則

| Do | Don't |
|---|---|
| `@color/kn_primary` のようにセマンティック層を参照 | `#1F6FEB` を直書き |
| `@dimen/kn_r_md` のように dimens を参照 | `android:layout_margin="16dp"` を直書き |
| `?attr/colorPrimary` で M3 attr を使う | 個別 View に色をハードコード |
| `style="@style/Widget.KeyNest.Card"` を継承 | 毎レイアウトで shape/stroke を再定義 |

---

## 1. Color tokens

### Raw palette → `values/colors.xml` (light) / `values-night/colors.xml` (dark差分)

| CSS token (tokens.css) | Android `@color/...` | Light hex | Dark hex |
|---|---|---|---|
| `--kn-blue-500`   | `kn_blue_500` | `#1F6FEB` | (共有) |
| `--kn-blue-600`   | `kn_blue_600` | `#1457C9` | (共有) |
| `--kn-blue-700`   | `kn_blue_700` | `#0F4AA8` | (共有) |
| `--kn-blue-50`    | `kn_blue_50`  | `#EAF2FE` | (共有) |
| `--kn-accent-500` | `kn_accent_500` | `#6366F1` | (共有) |
| `--kn-success`    | `kn_success`  | `#10B981` | (共有) |
| `--kn-warning`    | `kn_warning`  | `#F59E0B` | (共有) |
| `--kn-danger`     | `kn_danger`   | `#EF4444` | (共有) |
| `--kn-ink-950`    | `kn_ink_950`  | `#0A1020` | (共有) |
| `--kn-paper`      | `kn_paper`    | `#F6F8FC` | (共有) |

### Semantic layer (`kn_*` セマンティックトークン) → 実装で使うのは こちら

| JSX で見る変数 | Android `@color/...` | Light | Dark |
|---|---|---|---|
| `var(--bg)`           | `kn_bg`            | `kn_paper` | `kn_ink_950` |
| `var(--bg-elev)`      | `kn_bg_elev`       | `kn_white` | `kn_ink_900` |
| `var(--surface)`      | `kn_surface`       | `kn_white` | `kn_ink_850` |
| `var(--surface-2)`    | `kn_surface_2`     | `kn_ink_50` | `kn_ink_800` |
| `var(--surface-tint)` | `kn_surface_tint`  | `kn_blue_50` | rgba(31,111,235,.16) |
| `var(--border)`       | `kn_border`        | `#14000000` | `#0FFFFFFF` |
| `var(--border-strong)`| `kn_border_strong` | `#24000000` | `#1FFFFFFF` |
| `var(--text)`         | `kn_text`          | `#0B1220` | `#ECF1FA` |
| `var(--text-2)`       | `kn_text_2`        | `kn_ink_500` | `kn_ink_300` |
| `var(--text-3)`       | `kn_text_3`        | `kn_ink_400` | `kn_ink_400` |
| `var(--primary)`      | `kn_primary`       | `kn_blue_500` | `#4D8DF4` |
| `var(--on-primary)`   | `kn_on_primary`    | `kn_white` | `kn_white` |

### Material3 attribute → semantic token

| `?attr/...` | 参照先 |
|---|---|
| `colorPrimary` | `@color/kn_primary` |
| `colorOnPrimary` | `@color/kn_on_primary` |
| `colorPrimaryContainer` | `@color/kn_primary_container` |
| `colorSurface` | `@color/kn_surface` |
| `colorSurfaceVariant` | `@color/kn_surface_2` |
| `android:colorBackground` | `@color/kn_bg` |
| `colorOnSurface` | `@color/kn_text` |
| `colorOnSurfaceVariant` | `@color/kn_text_2` |
| `colorOutline` | `@color/kn_border_strong` |
| `colorOutlineVariant` | `@color/kn_border` |
| `colorError` | `@color/kn_danger` |

> ✅ 通常 View には `?attr/colorSurface` 等の M3 attribute だけ使う。
> 強度バー / 署名バッジ / Status chip のように M3 にない概念だけ直接 `@color/kn_*` を引く。

---

## 2. Dimen tokens

| CSS / JSX  | `@dimen/...` | 値 |
|---|---|---|
| `--kn-r-xs` (8px) | `kn_r_xs` | 8dp |
| `--kn-r-sm` (12px) | `kn_r_sm` | 12dp |
| `--kn-r-md` (14–16px) | `kn_r_md` / `kn_r_input` | 16dp / 14dp |
| `--kn-r-lg` (18–20px) | `kn_r_lg` / `kn_r_card` | 20dp / 18dp |
| `--kn-r-xl` (28px) | `kn_r_xl` / `kn_r_sheet` | 28dp |
| spacing `4 8 12 16 20 24 32 40 48 64` | `kn_space_1..16` | 同 dp |
| screen padding | `kn_screen_padding_h` / `kn_list_padding_h` | 20dp / 16dp |
| icon tile | `kn_icon_tile_md` / `_lg` | 40dp / 44dp |
| input height | `kn_input_height` | 52dp |
| button height | `kn_button_height` | 52dp |
| chip | `kn_chip_height` | 32dp |

---

## 3. Typography roles

| JSX で見る指定 | TextAppearance | sp / weight |
|---|---|---|
| 28/800 (-0.02em) | `Text.KeyNest.Display` | 28 / 800 |
| 26/800 (App bar large) | `Text.KeyNest.TitleL` | 26 / 800 |
| 19/800 (Sheet title) | `Text.KeyNest.TitleM` | 19 / 800 |
| 16/700 (App bar small) | `Text.KeyNest.TitleS` | 16 / 700 |
| 14/600 (Card title) | `Text.KeyNest.Body` | 14 / 600 |
| 13/500 (List subtitle) | `Text.KeyNest.BodyS` | 13 / 500 |
| 12/500 (Helper, meta) | `Text.KeyNest.Caption` | 12 / 500 |
| 11/700 + 0.08em UPPER | `Text.KeyNest.Eyebrow` | 11 / 700 |
| 15/700 (Button) | `Text.KeyNest.LabelL` | 15 / 700 |
| package name / SHA-256 | `Text.KeyNest.Mono` | 12 / 500 mono |
| 入力中のパスワード | `Text.KeyNest.Password` | 17 / 700 mono |

---

## 4. Widget styles

| JSX 概念 | `style="@style/..."` |
|---|---|
| プライマリ CTA ボタン | `Widget.KeyNest.Button.Primary` |
| テキストボタン (Cancel など) | `Widget.KeyNest.Button.Text` |
| クレデンシャルカード | `Widget.KeyNest.Card` |
| 入力フィールド (TextInputLayout) | `Widget.KeyNest.TextField` |
| フィルター chip | `Widget.KeyNest.Chip` |
| Bottom sheet | `Widget.KeyNest.BottomSheet` |

---

## 5. 画面 ↔ JSX ↔ 既存 layout XML 対応

> **Phase 2 issue の起点はこの表。** 各行を 1 PR にできます。

| 画面 (JSX) | 既存 layout | 不足要素 (Phase 2 で実装) |
|---|---|---|
| `ScreenOnboarding` (screens-1.jsx) | `autofill_enable_activity.xml` | 進捗ドット 3 段, ヒーロー SVG, 3 ステップカード, sticky 下部 CTA |
| `ScreenListEmpty` / `ScreenListPopulated` | `credential_list_activity.xml` + `credential_list_item.xml` | 検索バー, フィルター chip 行, 「最近使った」横スクロール, カード型行 (icon tile + 強度バー + 署名 chip + more), 空状態ヒーロー |
| `ScreenEdit` | `credential_edit_activity.xml` | ターゲットアプリカード + 署名 chip, パスワード強度バー, 詳細設定折り畳み, 削除ボタン |
| `ScreenPicker` | `package_picker_bottom_sheet.xml` | 検索バー, 「業務でよく使う」/「すべて」セクション分割, アイコンタイル, 手動入力フォールバック |
| `ScreenDataset` | `dataset_presentation.xml` | KeyNest ヘッダー (mark + eyebrow + 署名 chip), 行 (icon tile + 鍵アイコン), 「KeyNest で新規作成」 |
| `ScreenUnlock` | (新規 — `AutofillUnlockActivity` の UI) | ターゲット chip + 指紋リング + PIN フォールバックボタン |
| `ScreenSettings` | (新規) | Autofill ステータス hero, セキュリティ/Vault/About グループ |

---

## 6. 画面別 — 主要 view に貼るトークン例

### Credential card 行 (`credential_list_item.xml`)

```xml
<com.google.android.material.card.MaterialCardView
    style="@style/Widget.KeyNest.Card">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="@dimen/kn_card_padding">

        <!-- アイコンタイル: 動的に bg tint を差し込む -->
        <FrameLayout
            android:layout_width="@dimen/kn_icon_tile_lg"
            android:layout_height="@dimen/kn_icon_tile_lg"
            android:background="@drawable/kn_icon_tile_bg"/>  <!-- 新規: ShapeDrawable r=12 -->

        <LinearLayout android:layout_marginStart="@dimen/kn_space_3" ...>

            <TextView
                style="@style/Text.KeyNest.Body"
                android:text="表示名" />

            <TextView
                style="@style/Text.KeyNest.BodyS"
                android:textColor="@color/kn_text_2"
                android:text="ユーザー名" />

            <!-- 強度バー + パッケージ名 行 -->
            <LinearLayout android:orientation="horizontal" ...>
                <com.example.keynest.ui.widget.StrengthBar .../>
                <TextView style="@style/Text.KeyNest.Mono" android:text="com.x.y"/>
            </LinearLayout>
        </LinearLayout>

        <ImageButton
            android:layout_width="@dimen/kn_iconbtn_touch"
            android:layout_height="@dimen/kn_iconbtn_touch"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@drawable/ic_more"
            android:tint="@color/kn_text_3"/>
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

### Onboarding hero CTA

```xml
<com.google.android.material.button.MaterialButton
    style="@style/Widget.KeyNest.Button.Primary"
    android:layout_width="match_parent"
    android:text="@string/autofill_enable_action"/>
```

---

## 7. 命名規約

- 色: `kn_<role>_<step>` (例: `kn_blue_500`) / セマンティック: `kn_<name>` (例: `kn_surface_2`)
- dimen: `kn_<category>_<size>` (例: `kn_r_md`, `kn_space_4`, `kn_icon_tile_lg`)
- style: `Widget.KeyNest.<Component>[.<Variant>]`
- TextAppearance: `Text.KeyNest.<Role>`
- string: 既存命名を踏襲。新規追加分は `package_picker_*` 等のドメイン prefix。

---

## 8. Phase 2 を AI 実装に渡すときのテンプレ

> ```
> # feat(<area>): align <Activity> to design/screens-X.jsx
>
> ## Source of truth
> - JSX: design/screens/screens-1.jsx → `ScreenListPopulated`
> - Tokens: design/spec.md §1 / android-assets/res/values/colors.xml
> - Mapping: design/mapping.md §5
>
> ## DoD
> - [ ] ルートは `?attr/colorBackground`, padding-h = `@dimen/kn_screen_padding_h`
> - [ ] 行カードは `style="@style/Widget.KeyNest.Card"`, padding = `@dimen/kn_card_padding`
> - [ ] アイコンタイルは 44dp / r12, `kn_icon_tile_bg` ShapeDrawable
> - [ ] 強度バーは独自 View `StrengthBar`、色は `kn_success/warning/danger`
> - [ ] 「最近使った」横スクロールは HorizontalScrollView または小さな RecyclerView
> - [ ] Dark mode: values-night の override で破綻しないこと
>
> ## 非対象
> - 検索の実機能 (UI のみ)
> - 「最近使った」のデータ (last_used_at は未追加、SAMPLE で表示)
> ```
