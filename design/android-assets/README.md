# KeyNest — Android handoff assets

`Claude Code` で実装するときに必要な「設計 → リソース」の橋渡しを 1 箇所にまとめたフォルダ。

## なぜこれが必要か

`design/tokens.css` と `design/screens/*.jsx` は **設計フォーマット** であって、
Android リソースに自動変換されない。マッピングが無いまま実装すると、各実装者が
都度トークンを発明するので、結果として Material3 デフォルト + primary 色だけの
画面になりがち。

このフォルダは **Phase 1 (foundation)** の成果物。一度コピーすれば、以後の画面
実装は `?attr/colorSurface` や `@style/Widget.KeyNest.Card` を貼るだけでデザイン
が効くようになる。

## 何が入っているか

```
android-assets/
├── README.md                   ← このファイル
├── mapping.md                  ← Token → Android resource マッピング (必読)
│
├── res/
│   ├── drawable/
│   │   ├── ic_launcher_background.xml
│   │   ├── ic_launcher_foreground.xml
│   │   └── ic_launcher_monochrome.xml
│   ├── mipmap-anydpi-v26/
│   │   └── ic_launcher.xml
│   ├── font/
│   │   ├── manrope_family.xml
│   │   ├── manrope_regular.xml
│   │   ├── manrope_semibold.xml
│   │   ├── manrope_bold.xml
│   │   ├── manrope_extrabold.xml
│   │   └── jetbrains_mono.xml
│   ├── values/
│   │   ├── colors.xml          ← 全パレット + light セマンティック
│   │   ├── dimens.xml          ← radii / spacing / 各種サイズ
│   │   ├── themes.xml          ← Material3 attr → kn_* マッピング
│   │   └── type.xml            ← Text.KeyNest.* TextAppearance
│   ├── values-night/
│   │   └── colors.xml          ← dark の semantic 上書きのみ
│   └── values-ja/
│       └── strings.xml         ← 日本語訳 (spec.md §9)
│
├── kotlin/
│   └── KeyNestTheme.kt         ← Compose 用 (任意・将来用)
│
├── icon-b.svg                  ← Play Store / README 用
└── preview.html                ← アイコンのサイズ・並び確認
```

## 取り込み手順 (Claude Code 想定)

### Phase 1 — Foundation を入れる (1 PR)

1. `android-assets/res/values/colors.xml` で **既存** `app/src/main/res/values/colors.xml` を置換
2. `android-assets/res/values/{dimens,themes,type}.xml` を `app/src/main/res/values/` にコピー (themes.xml は置換)
3. `android-assets/res/values-night/colors.xml` を `app/src/main/res/values-night/colors.xml` にコピー (新規ディレクトリ)
4. `android-assets/res/values-ja/strings.xml` を `app/src/main/res/values-ja/strings.xml` にコピー (新規)
5. `android-assets/res/font/*` を `app/src/main/res/font/` にコピー (新規)
6. `android-assets/res/drawable/ic_launcher_*.xml` を `app/src/main/res/drawable/` にコピー
7. `android-assets/res/mipmap-anydpi-v26/ic_launcher.xml` を `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` に配置。**round 版** も同じ内容で `ic_launcher_round.xml` として置く
8. 旧 `app/src/main/res/mipmap-*/ic_launcher*.png` (もし残っていれば) を削除
9. `app/src/main/res/values/themes.xml` の `<application android:theme="@style/Theme.KeyNest">` を Manifest で確認
10. ビルド確認 → アプリ起動して既存画面が「色が変わっただけ」で動くことを確認

> ✅ ここまでで **既存画面に手を入れずに** ベース色 / フォント / 角丸 が
> デザイントークンに沿った状態になります。
> 個別画面の作り込みは Phase 2 へ。

### Phase 2 — 画面別の対応 (各画面 1 PR)

`mapping.md` §5 の対応表に従って 1 画面ずつ JSX → Android XML に書き起こす。
各 Issue / PR は `mapping.md` §8 のテンプレに沿って起票するとぶれない。

優先順 (推奨):
1. `CredentialListActivity` (カード型 + 検索 + フィルター chip)
2. `CredentialEditActivity` (ターゲットカード + 強度バー)
3. `AutofillEnableActivity` (ヒーロー + 3 ステップ)
4. `PackagePickerBottomSheet` (セクション分割 + 検索)
5. Settings (新規)
6. `AutofillUnlockActivity` の UI (Biometric prompt の前後)
7. `dataset_presentation.xml` (Autofill dropdown のブランディング)

## デザイン参照

- `design/KeyNest Design.html` を開くと全画面 + Light/Dark + アイコン候補を一覧可能
- `design/spec.md` がタイポ / 色 / 余白 / アクセシビリティの仕様書
- `design/screens/*.jsx` が各画面の実装ヒント (React で書かれているが値は読める)
