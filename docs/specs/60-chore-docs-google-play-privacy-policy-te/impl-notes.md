# Implementation Notes: Issue #60

## 概要

Google Play Console 申請に必要な法務・サポート関連ドキュメントを
GitHub Pages (`source: branch main / folder /docs`, theme `minima`) で
公開できるように、`docs/` 直下に 5 ファイルを新規配置した。
GitHub Pages 機能の有効化操作およびリポジトリ public 化は本 Issue の
スコープ外（人間が GitHub UI 上で実施）。

## 実装したファイル一覧

| ファイル | 主要セクション |
| --- | --- |
| `docs/_config.yml` | `title: KeyNest` / `theme: minima` の 2 キーのみ（Jekyll ビルド最小構成） |
| `docs/index.md` | アプリ概要（Android パスワード/クレデンシャル管理、ローカル保存・外部送信なし、AES-GCM + Android Keystore） / Pages リンク（privacy / terms / support） / GitHub repo リンク |
| `docs/privacy-policy.md` | Last updated / Information We Collect (none) / Information We Share (none) / Data Storage and Encryption (AES-GCM + Android Keystore) / Children's Privacy / Changes / Contact (`hitoshi.ichikawa@gmail.com`) |
| `docs/terms.md` | Last updated / License (MIT, `LICENSE` 参照) / Disclaimer (AS IS 標準テンプレ) / Developer (Hitoshi Ichikawa / @hitoshiichikawa) / Changes |
| `docs/support.md` | Last updated / Contact (`hitoshi.ichikawa@gmail.com` / GitHub Issues) / Known Issues (TBD) / FAQ (TBD) |

すべて英語で記述。`docs/specs/` 配下のファイルには変更なし
（本 Issue 用ディレクトリ `docs/specs/60-chore-docs-google-play-privacy-policy-te/`
への新規追加のみ）。

## 採用した Last updated 日付

`May 18, 2026` を採用（実装日 = 2026-05-18 を英語表記化）。

要件 3.5 では「実装時に固定の日付」と規定されているのみで、日付の
書式・値は実装者裁量。privacy-policy / terms / support の 3 ファイルで
同一の日付に揃えた。

## 確認事項（次工程の Reviewer / 人間に判断委ねたい点）

1. **Last updated 日付の妥当性**: `May 18, 2026` で問題ないか。
   Play 申請日との関係で別日付に揃えたい場合は人間が後続で更新する想定。
2. **法的文言のレビュー**: requirements.md NFR 1 のとおり、本実装は
   テンプレベース。Play 申請前に人間（または法務）による文言レビュー・
   調整が必須。特に privacy-policy.md の "Information We Collect: none"
   は KeyNest が本当に Android Keystore 経由の crash log / system log 等を
   含めても外部送信ゼロであることを実装側で再確認する必要あり
   （現状の `app/` 仕様前提では満たしている認識）。
3. **GitHub Pages 有効化**: 本 Issue では行わない。リポジトリ public 化と
   合わせて人間が GitHub UI で `Settings > Pages > Source: main branch / /docs`
   を設定する必要あり（requirements.md Requirement 6.3 / 6.4）。
4. **README からのリンク追加**: requirements.md Out of Scope のとおり
   本 Issue では対象外。別 Issue で対応想定。
5. **トップページの YAML front matter**: `index.md` 含む全ページに
   `title:` のみ front matter を付与した。`minima` テーマはこれを使って
   サイドナビ等を構成するため付与したが、不要であれば削除可。
6. **`terms.md` の LICENSE リンク**: 公開後にリポジトリが public 化される
   前提で `https://github.com/hitoshiichikawa/KeyNest/blob/main/LICENSE`
   を直接張った。private のままだと 404 になる点に留意。

## 検証手順と結果

### 1. ファイル存在確認

```
ls -la docs/
```

結果:

```
-rw-rw-r-- _config.yml
-rw-rw-r-- index.md
-rw-rw-r-- privacy-policy.md
drwxrwxr-x specs/
-rw-rw-r-- support.md
-rw-rw-r-- terms.md
```

要件どおり 5 ファイル + 既存 `specs/` ディレクトリが存在することを確認。

### 2. 必須キーワード grep

| キーワード | 確認対象 | 結果 |
| --- | --- | --- |
| `hitoshi.ichikawa@gmail.com` | `privacy-policy.md` / `support.md` | 両方ヒット（一致） |
| `AES-GCM` | `privacy-policy.md` / `index.md` | 両方ヒット |
| `Android Keystore` | `privacy-policy.md` / `index.md` | 両方ヒット |
| `MIT` | `terms.md` | ヒット（License セクション） |
| `theme: minima` | `_config.yml` | ヒット |
| `https://github.com/hitoshiichikawa/KeyNest/issues` | `support.md` | ヒット |
| `https://github.com/hitoshiichikawa/KeyNest` | `index.md` | ヒット |
| `@hitoshiichikawa` | `terms.md` | ヒット |
| `AS IS` | `terms.md` | ヒット（Disclaimer セクション） |

### 3. 非侵襲性確認

`git status` 結果: untracked files は以下のみで、`app/`, `LICENSE`,
`README.md`, 既存 `docs/specs/*/` ファイルへの modification なし。

```
docs/_config.yml
docs/index.md
docs/privacy-policy.md
docs/specs/60-chore-docs-google-play-privacy-policy-te/  (本 Issue 用)
docs/support.md
docs/terms.md
```

`git diff --stat` は空（既存 tracked ファイルへの変更なし）。

requirements.md Requirement 7（既存資産への非侵襲性）の全 acceptance
criteria を満たすことを確認。
