# Review Notes: Issue #60 (round 1)

## Summary

- **BASE_BRANCH 実使用値**: `develop`（リポジトリに `develop` ブランチが存在することを `git rev-parse --verify develop` で確認したため、fallback ではなく `develop..HEAD` を比較ベースとして採用した）。
- **差分取得結果の要約**:
  - 変更ファイル数: 7 ファイル / 追加 409 行 / 削除 0 行（既存 tracked ファイルへの変更は一切なし）。
  - 公開対象 5 ファイル: `docs/_config.yml` (+2), `docs/index.md` (+21), `docs/privacy-policy.md` (+53), `docs/terms.md` (+48), `docs/support.md` (+31)。
  - 仕様書 2 ファイル: `docs/specs/60-chore-docs-google-play-privacy-policy-te/requirements.md` (+147), `impl-notes.md` (+107)（本 Issue 用ディレクトリへの新規追加のみ）。
  - `app/` / `README.md` / `LICENSE` / `docs/specs/` 既存ディレクトリへの変更は `git diff --stat` で 0 件であることを確認済み。
- **tasks.md / design.md の有無**: 本 spec ディレクトリには `requirements.md` と `impl-notes.md` のみが存在し、`tasks.md` および `design.md` は存在しない。本 Issue は静的ドキュメント chore であり、要件・NFR にも tasks/design ドキュメントの作成要求は無いため、欠落自体は AC 違反としては扱わない（情報提供レベルで明記）。
- **Developer の impl-notes.md 自己検証結果に対する独立検証の所感**:
  - impl-notes.md §2 の grep 表（メール一致 / `AES-GCM` / `Android Keystore` / `MIT` / `@hitoshiichikawa` / `AS IS` / `theme: minima` / GitHub Issues URL / GitHub repo URL）は reviewer 側でも一次ソース Read により再確認し、すべて整合していた。
  - impl-notes.md §3 の非侵襲性主張も `git diff --stat develop..HEAD -- app/ README.md LICENSE` の出力が空であることおよび `docs/specs/` 配下が 60- ディレクトリの新規追加のみであることで再現確認できた。
  - 自己検証結果は信頼できる内容で、reviewer の独立判定とも一致した。

## AC Coverage

| ID | 要件抜粋 | 判定 | エビデンス |
| --- | --- | --- | --- |
| 1.1 | `docs/` 直下に index/privacy-policy/terms/support/_config の 5 ファイル | OK | `ls docs/` で 5 ファイルすべて存在 |
| 1.2 | `_config.yml` に `title: KeyNest` | OK | `docs/_config.yml:1` `title: KeyNest` |
| 1.3 | `_config.yml` に `theme: minima` | OK | `docs/_config.yml:2` `theme: minima` |
| 1.4 | 全ページを英語で記述 | OK | index/privacy/terms/support すべて英語本文（front matter `title` も英語）。日本語は含まれない |
| 1.5 | `docs/ja/` 等の多言語サブディレクトリを作成しない | OK | `docs/` 配下サブディレクトリは `specs/` のみ |
| 2.1 | index.md にアプリ概要（Android パスワード / クレデンシャル管理） | OK | `docs/index.md:7-10` "KeyNest is an Android application for managing passwords and credentials locally..." |
| 2.2 | index.md に privacy-policy へのリンク | OK | `docs/index.md:14` `[Privacy Policy](privacy-policy.md)` |
| 2.3 | index.md に terms へのリンク | OK | `docs/index.md:15` `[Terms of Service](terms.md)` |
| 2.4 | index.md に support へのリンク | OK | `docs/index.md:16` `[Support](support.md)` |
| 2.5 | index.md に GitHub repo (`https://github.com/hitoshiichikawa/KeyNest`) へのリンク | OK | `docs/index.md:21` |
| 3.1 | privacy-policy に収集情報セクション、ローカル保存・外部送信なしを `none` の趣旨で明示 | OK | `docs/privacy-policy.md:12-19` "Information We Collect" + "None." + "stores all user data... locally" + "does not collect, transmit, or upload..." |
| 3.2 | privacy-policy にデータ共有セクション、第三者共有なしを `none` の趣旨で明示 | OK | `docs/privacy-policy.md:21-26` "Information We Share" + "None." + "does not share any user data with third parties" |
| 3.3 | `Android Keystore` 経由の `AES-GCM` を明示 | OK | `docs/privacy-policy.md:30-33` "encrypted on the device using AES-GCM. Encryption keys are generated and stored in the Android Keystore system" |
| 3.4 | `hitoshi.ichikawa@gmail.com` を含む | OK | `docs/privacy-policy.md:53` `- Email: hitoshi.ichikawa@gmail.com` |
| 3.5 | 改定日（Last updated）として固定日付を記載 | OK | `docs/privacy-policy.md:7` `Last updated: May 18, 2026` |
| 3.6 | 英語で記述 | OK | privacy-policy.md 全文英語 |
| 4.1 | `MIT License` 配布と `LICENSE` ファイル参照 | OK | `docs/terms.md:15-18` "distributed under the MIT License" + リポジトリ LICENSE への外部リンク |
| 4.2 | `AS IS` 標準免責テンプレ | OK | `docs/terms.md:26-32` `THE APP IS PROVIDED "AS IS"...` |
| 4.3 | 開発者名 `Hitoshi Ichikawa` + GitHub ハンドル `@hitoshiichikawa` | OK | `docs/terms.md:41-42` `Name: Hitoshi Ichikawa` / `GitHub: [@hitoshiichikawa](...)` |
| 4.4 | 英語で記述 | OK | terms.md 全文英語 |
| 5.1 | support.md に `hitoshi.ichikawa@gmail.com` | OK | `docs/support.md:14` |
| 5.2 | GitHub Issues `https://github.com/hitoshiichikawa/KeyNest/issues` | OK | `docs/support.md:15` |
| 5.3 | 既知の問題 / FAQ プレースホルダ | OK | `docs/support.md:20-22` "Known Issues" + "TBD." / `docs/support.md:26-28` "Frequently Asked Questions (FAQ)" + "TBD." |
| 5.4 | 英語で記述 | OK | support.md 全文英語 |
| 5.5 | privacy-policy.md と support.md のメール一致 | OK | 両ファイルで `hitoshi.ichikawa@gmail.com` が同一文字列で出現（Grep で確認） |
| 6.1 | 公開対象 Markdown を `docs/` 直下に配置（`docs/specs/` 配下へ移動しない） | OK | 公開対象 4 Markdown + `_config.yml` がすべて `docs/` 直下 |
| 6.2 | Jekyll ビルド成立する最小 YAML キー（`title` / `theme`） | OK | `docs/_config.yml` 内容が 2 キーのみで両方含む |
| 6.3 | private のままで Pages enable 不可な場合はファイル配置のみで完了 | OK | ファイル配置のみ実施・Pages 有効化操作なし（要件どおりスコープ外扱い） |
| 6.4 | リポジトリ設定（Pages 有効化）を Claude が自動で行わない | OK | 差分にリポジトリ設定変更は含まれない |
| 7.1 | `app/` 配下に変更なし | OK | `git diff --stat develop..HEAD -- app/` 出力空 |
| 7.2 | `docs/specs/` 既存仕様書ファイルに変更なし（本 Issue 用新規ディレクトリ追加のみ許容） | OK | 変更は `docs/specs/60-chore-docs-google-play-privacy-policy-te/` 配下の 2 新規ファイルのみ |
| 7.3 | `README.md` に変更なし | OK | `git diff --stat develop..HEAD -- README.md` 出力空 |
| 7.4 | `LICENSE` に変更なし | OK | `git diff --stat develop..HEAD -- LICENSE` 出力空 |
| 7.5 | `Gemfile` / `_layouts/` / `_includes/` 等 Jekyll 関連ファイルをリポジトリ直下および `docs/` 配下に追加しない | OK | リポジトリ直下 `ls -la` / `docs/` 配下 `ls -la` ともに該当ファイル・ディレクトリなし |
| NFR 1 | privacy/terms/support をテンプレ扱い、法的最終化はスコープ外 | N/A | 文面はテンプレとして妥当な記述。法的レビューは impl-notes.md §3.2 で人間判断委ね（要件どおり） |
| NFR 2 | `https://hitoshiichikawa.github.io/KeyNest/` 配下から各 sub page に Jekyll デフォルトルーティングで到達可能 | OK | `docs/` 直下に Markdown を平置きしているため Jekyll の標準ルーティング（`/privacy-policy/` 等）で到達可能な配置（実 URL 確認は public 化後に人間が確認） |

## Findings

### AC 未カバー

- なし

### missing test

- なし（本 Issue は静的ドキュメント chore で、要件・NFR に自動検証追加要求は明示されていない）

### boundary 逸脱

- なし（Out of Scope 各項目すなわち README 変更 / `app/` 変更 / `docs/specs/` 既存ファイル変更 / `Gemfile`・`_layouts/`・`_includes/` 追加 / 多言語化 / Pages 有効化 / public 化 すべて侵食していないことを diff および ls で確認済み）

## Manual Check Notes (informational, not blocking)

- **Last updated 日付の妥当性**: 3 ファイルすべて `May 18, 2026` で揃っている。Play 申請日との関係で別日付に揃えたい場合は人間が後続で更新する必要あり（impl-notes.md §3.1 と整合）。
- **NFR 1（法的文言レビュー）**: privacy-policy.md の "Information We Collect: None." は、KeyNest が crash log / system log 等を含めても外部送信ゼロであることが前提。Play 申請前に `app/` 側の実装と突き合わせて人間（または法務）による文言レビューが必須（impl-notes.md §3.2 と整合）。
- **terms.md の LICENSE リンク**: `https://github.com/hitoshiichikawa/KeyNest/blob/main/LICENSE` を直接張っているため、リポジトリが private のままだと 404 になる。public 化前に踏まれた場合のリンク切れに留意（impl-notes.md §3.6 と整合）。
- **GitHub Pages 有効化**: Requirement 6.3 / 6.4 のとおり本 Issue では対象外。public 化と合わせて人間が GitHub UI 上で `Settings > Pages > Source: main branch / /docs` を設定する必要あり。
- **README からのリンク追加**: Out of Scope のとおり別 Issue 対応想定。
- **front matter `title`**: `index.md` を含む全 4 Markdown に `title:` front matter を付与している。`minima` テーマでサイドナビ等を構成するため有用だが、不要であれば人間判断で削除可（AC には影響しない）。
- **tasks.md / design.md の不存在**: 本 spec には tasks.md / design.md が存在しないが、要件・NFR で作成が求められておらず chore 規模も小さいため AC 違反としては扱わない。将来同様の chore で tasks.md を作るか否かはオーケストレータ側の運用判断。

## Round
- round: 1 / max: 2

RESULT: approve
