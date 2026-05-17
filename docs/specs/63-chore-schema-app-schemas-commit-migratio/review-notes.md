# Review Notes

<!-- idd-claude:review round=2 model=claude-opus-4-7 timestamp=2026-05-17T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-63-impl-chore-schema-app-schemas-commit-migratio
- HEAD commit: 0ce3018e6fd787284ba1b55a392497addbc584e4
- Compared to: develop..HEAD
- Feature Flag Protocol: 対象 repo の `CLAUDE.md` で `**採否**: opt-out` のため、flag 観点の細目チェックは適用しない（通常 3 カテゴリ判定のみ）。
- tasks.md: 本 spec dir には `tasks.md` が存在しない（Architect 不起動の Issue。`_Boundary:_` 境界は requirements.md の Out of Scope と各 AC で示された対象パスで代替評価）。
- design.md: 本 spec dir には存在しない。

## Round 1 reject 指摘事項の解消確認

Round 1 で出した 3 件の Finding は以下のとおりすべて解消されている。

| 旧 Finding | 旧 Target | 解消状況 | 根拠 |
|---|---|---|---|
| Finding 1 | 1.1, 1.3 | 解消 | commit `d30f226 chore(schemas): track current applicationId schema JSON` で `app/schemas/inc.goodanswers.keynest.data.KeyNestDatabase/2.json` を tracking 対象に追加。`git ls-files app/schemas/inc.goodanswers.keynest.data.KeyNestDatabase/` が `2.json` を 1 件列挙することを確認 |
| Finding 2 | 1.2, 1.4 | 解消 | 同 commit により KSP が export する現行 DB version (`KeyNestDatabase.kt:27` の `version = 2`) に対応する `2.json` 1 件が tracking 対象に揃った。`v1.json` は KSP が現行版のみ export する仕様のため不在で、requirements.md Out of Scope（過去 applicationId / 過去 version JSON の保存）と整合 |
| Finding 3 | 4.1 | 解消 | impl-notes.md「Reviewer round=1 reject 対応 (2026-05-17)」セクションで `./gradlew :app:kspDebugKotlin --no-daemon` および `./gradlew :app:assembleDebug --no-daemon` を実行し、いずれの直後も `git status` が clean working tree（review-notes.md を除く）であることを記録 |

## Verified Requirements

- 1.1 — `app/schemas/inc.goodanswers.keynest.data.KeyNestDatabase/2.json` が tracking 対象として保持されている（commit `d30f226`）
- 1.2 — `./gradlew :app:assembleDebug --no-daemon` 後に tracking 対象配下に現行 DB version (v=2) の JSON が揃った状態を確認（impl-notes.md「Reviewer round=1 reject 対応」セクション参照）
- 1.3 — `git ls-files app/schemas/inc.goodanswers.keynest.data.KeyNestDatabase/` → `app/schemas/inc.goodanswers.keynest.data.KeyNestDatabase/2.json`（1 件列挙）
- 1.4 — KSP 実行で untracked となった `2.json` を `d30f226` で tracking 対象に追加
- 2.1 — 旧 FQCN ディレクトリ `app/schemas/com.example.keynest.data.KeyNestDatabase/` は working tree に存在しない（`ls app/schemas/com.example.keynest.data.KeyNestDatabase/` → No such file or directory。`app/schemas/` 直下は `inc.goodanswers.keynest.data.KeyNestDatabase/` のみ）
- 2.2 — `.gitignore:50` に `app/schemas/com.example.keynest.data.KeyNestDatabase/` の ignore エントリを追加
- 2.3 — `.gitignore:41-49` に「Room schema export defensive ignore (Issue #63)」コメントブロックがあり、「旧 applicationId 防衛用」「PR #55 の名残」など由来を明示
- 2.4 — `git status` 実行で `nothing to commit, working tree clean`（untracked は本 Reviewer 産物の `review-notes.md` のみ。impl PR スコープ外）
- 3.1 — `docs/development.md`「applicationId 変更時の schema ディレクトリ移行手順」セクションが存在（目次リンクあり）
- 3.2 — `docs/development.md` 手順 1 で `git rm -r --ignore-unmatch` と `rm -rf` による削除手順を明示
- 3.3 — `docs/development.md` 手順 2 で `./gradlew :app:assembleDebug` → `git add` → `git ls-files` 確認の手順を明示
- 3.4 — `docs/development.md` 手順 4 で diff 形式 (`<OLD_APP_ID>` プレースホルダ) による `.gitignore` 追記方法を明示
- 3.5 — `docs/development.md` 手順 5 で `git status` clean 確認手順を明示
- 4.1 — `./gradlew :app:assembleDebug --no-daemon` 後に `git status` clean を確認（impl-notes.md 検証ログ）
- 4.2 — `docs/development.md` の手順 1 - 5 により後続 applicationId 変更 PR でも本手順に従う限り clean state を維持できる手順面の担保
- 4.3 — `docs/development.md` 手順 2 で新 FQCN 配下を `git add` で復帰させる手順を明示
- 4.4 — Req 2.2 の `.gitignore` エントリにより、旧 FQCN 配下に再生成があっても `git status` に現れない
- NFR 1.1 — `docs/development.md` はリポジトリルートから 1 ホップ（`docs/` 直下）に配置されており、発見容易性を満たす
- NFR 2.1 — 本 PR の変更は `.gitignore` 追記、`docs/development.md` 新規追加、および現行 FQCN 配下 `2.json` 追加のみで、applicationId 変更 PR (PR #58 等) との競合がなく、先に merge 可能

## Findings

なし。

## Summary

Round 1 で reject した 3 件の Finding（Req 1.1 / 1.2 / 1.3 / 1.4 / 4.1）はすべて解消された。
Developer は実機で `./gradlew :app:assembleDebug --no-daemon` を実行し、KSP が生成した
`app/schemas/inc.goodanswers.keynest.data.KeyNestDatabase/2.json` を commit `d30f226` で
tracking 対象に追加。`git ls-files` での確認、ビルド後 `git status` clean の確認も
impl-notes.md に記録済み。本 Issue の 4 つの Requirement と 2 つの NFR の全 AC が
カバーされており、boundary 逸脱・missing test も検出されないため approve とする。

RESULT: approve
