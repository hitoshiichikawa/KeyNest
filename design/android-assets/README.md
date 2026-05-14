# KeyNest — App icon assets (Icon A: Shelter)

`Claude Code` で組み込む際の手順。すべて Vector Drawable なので PNG 書き出し不要。

## 1. 配置

```
app/src/main/res/
├── drawable/
│   ├── ic_launcher_background.xml   ← cream 単色
│   ├── ic_launcher_foreground.xml   ← シェルター + 鍵穴 + 小枝
│   └── ic_launcher_monochrome.xml   ← Android 13+ Themed Icons 用
└── mipmap-anydpi-v26/
    ├── ic_launcher.xml              ← Adaptive Icon マニフェスト
    └── ic_launcher_round.xml        ← 同上 (内容は同じ)
```

> `mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi/` の PNG は **削除して構わない**
> （API 26 未満をサポートする場合のみ Asset Studio で再生成）。

## 2. AndroidManifest.xml

既存設定で OK。念のため:

```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    ...>
```

## 3. デザイン仕様

| 項目 | 値 |
|---|---|
| 背景色 | `#F4EBDC` (cream) |
| ルーフ | `#2A7BF5` → `#0F4AA8` 線形グラデ |
| 鍵穴・小枝 | `#F4EBDC` / `#9B7A4A` |
| 安全ゾーン | 中心 (54,54)・半径 36 dp に重要要素を配置済み |
| 拡大時の見え方 | `preview.html` で確認 |

## 4. プレビュー

`android-assets/preview.html` を開くと、Squircle / Circle / 48dp サイズ
そしてホーム画面風グリッドで他アプリと並べた状態を確認できます。
