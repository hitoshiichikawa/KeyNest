# Requirements Document

## Introduction

PR #55 で applicationId / namespace を `com.example.keynest` から `inc.goodanswers.keynest` に
リネームした際、Room がエクスポートする schema JSON のディレクトリ名 `app/schemas/` 配下に
旧 applicationId 系（`com.example.keynest.data.KeyNestDatabase/`）と新 applicationId 系
（`inc.goodanswers.keynest.data.KeyNestDatabase/`）が混在する状態が残り、いずれも git に
tracking されていなかったため、issue-watcher が working tree dirty を理由に 50 分以上連続で
ジョブを skip する事象が発生した。本要件は、`app/schemas/` 配下の git 運用方針（どのスキーマを
追跡し、どれを ignore するか）を恒久的に確定し、Room の MigrationTestHelper が schema を解決
できる再現性と、watcher が dirty 誤検出を起こさない clean working tree の双方を満たすことを
目的とする。あわせて、将来 applicationId を変更する際に旧ディレクトリを残置しないための
開発者手順を文書化する。

## Requirements

### Requirement 1: 現行 applicationId スキーマの tracking

**Objective:** As a KeyNest 開発者, I want 現行 applicationId に対応する Room schema JSON が
git に追跡されている状態, so that MigrationTestHelper がリポジトリ clone 直後でも全バージョンの
schema を解決でき、Migration テストが再現性をもって実行できる

#### Acceptance Criteria

1. The KeyNest Repository shall `app/schemas/inc.goodanswers.keynest.data.KeyNestDatabase/` 配下の
   すべての schema JSON ファイルを git tracking 対象として保持する
2. When `gradlew assembleDebug` 相当のビルドを実行したとき, the Room Schema Export shall 出力された
   全バージョン（v1.json、v2.json 等、現時点で存在するすべてのバージョン）の JSON が tracking
   対象配下に揃った状態となる
3. When `git ls-files app/schemas/inc.goodanswers.keynest.data.KeyNestDatabase/` を実行したとき,
   the KeyNest Repository shall 該当ディレクトリ配下の schema JSON を 1 件以上列挙する
4. If 現行 applicationId 配下に未 tracking の schema JSON が存在するなら, the KeyNest Repository
   shall それらを追跡対象に追加した状態で commit する

### Requirement 2: 旧 applicationId ディレクトリの除外

**Objective:** As a issue-watcher, I want 旧 applicationId 配下の schema ディレクトリが working tree
に残置されない、かつ ignore 規則で防衛されている状態, so that 残置や再生成によって dirty 誤検出が
発生しない

#### Acceptance Criteria

1. The KeyNest Repository shall `app/schemas/com.example.keynest.data.KeyNestDatabase/` を作業ツリー
   から物理削除した状態を保持する
2. The `.gitignore` shall 旧 applicationId 配下のスキーマディレクトリを ignore するパターンを含む
3. The `.gitignore` shall 当該 ignore パターンが「旧 applicationId 防衛用」である旨を説明する
   コメントを伴う
4. While 旧 applicationId 配下にディレクトリ・ファイルが存在しない状態であるなら, the
   KeyNest Repository shall `git status` の出力に当該パスを untracked / modified として
   列挙させない

### Requirement 3: applicationId 変更時の開発者手順ドキュメント

**Objective:** As a 将来 applicationId を変更する開発者, I want 旧 schema ディレクトリの削除と
新 schema ディレクトリの追加に必要な git 操作および `.gitignore` 更新手順が文書化された状態,
so that 同様の残置事象を再発させずに applicationId 変更を完了できる

#### Acceptance Criteria

1. The KeyNest Repository shall `README.md` または `docs/development.md` 相当のドキュメント上に
   applicationId 変更時の schema ディレクトリ移行手順セクションを保持する
2. The 当該ドキュメントセクション shall 旧 applicationId 配下を作業ツリーから削除する git 操作を
   明示する
3. The 当該ドキュメントセクション shall 新 applicationId 配下を追跡対象に追加する git 操作を
   明示する
4. The 当該ドキュメントセクション shall `.gitignore` に旧 applicationId 防衛パターンを追記する
   方法を明示する
5. The 当該ドキュメントセクション shall 手順完了後に `git status` が clean になることを確認する
   手順を明示する

### Requirement 4: clean working tree 維持と watcher dirty 検出の再発防止

**Objective:** As a issue-watcher 運用者, I want 本 Issue の対応 PR 完了後の clean state、および
applicationId 変更を伴う後続 PR 完了後の clean state、いずれにおいても作業ツリーが dirty に
ならないこと, so that watcher が dirty 誤検出によりジョブを skip し続ける状態を再発させない

#### Acceptance Criteria

1. When 本 Issue 対応 PR の作業完了後に `gradlew assembleDebug` 相当のビルドを実行したとき, the
   KeyNest Repository shall その直後の `git status` 出力が clean working tree を示す
2. When PR #58 など applicationId 変更を含む後続 PR の作業完了後に同等のビルドを実行したとき,
   the KeyNest Repository shall 本 Issue で定義した手順に従う限り `git status` 出力が clean
   working tree を示す
3. If schema 再生成によって新 applicationId 配下に未 tracking ファイルが現れたなら, the
   KeyNest Repository shall そのファイルを Requirement 1 の追跡対象に追加することで clean
   state へ復帰できる
4. If 旧 applicationId 配下に再生成・残置が発生したなら, the `.gitignore` shall そのパスを
   ignore することで `git status` 出力に含めない

## Non-Functional Requirements

### NFR 1: ドキュメント所在の発見容易性

1. The KeyNest Repository shall applicationId 変更時の schema 手順ドキュメントを、リポジトリ
   ルートから 1 ホップ（`README.md` 直下リンク、もしくは `docs/` 直下の単一ファイル）以内で
   到達可能な位置に配置する

### NFR 2: マージ順序の前提

1. The KeyNest Repository shall 本 Issue の対応 PR を、applicationId を変更する後続 PR
   （PR #58 を含む）より先に main にマージできる状態で完成させる

## Out of Scope

- CI 上での Room schema 整合性自動検証（schema JSON と migration 実装の差分検出など）の追加は
  将来案として扱い、本 Issue では実装しない
- 過去の applicationId（`com.example.keynest`）に対応する schema JSON の git 履歴への保存・
  アーカイブ。tracking 対象は現行 applicationId のみとする
- applicationId 変更時の Room migration 実装の追加・修正（本 Issue は git 運用と schema
  ディレクトリ管理に限定）
- Room schema export の出力先パス自体の変更（既存の `app/schemas/<fqcn>/` 構造を維持する）
- issue-watcher 側のロジック変更（dirty 検出基準の緩和など）。本 Issue はリポジトリ側で
  clean state を保つアプローチに限定する

## Open Questions

なし（Issue 本文の「確認事項」3 点は以下のとおり方針確定済みとして本要件に取り込み済み）:

- 確認事項 1（コミット粒度）: 本 Issue では初期 commit のみを扱い、以降の migration 追加 PR
  では各 PR 担当者がその PR 内で schema JSON を追加・commit する運用とする → Requirement 1
  および Requirement 4 でカバー
- 確認事項 2（PR 順序）: 本 Issue の対応 PR は applicationId 変更を含む後続 PR より先に merge
  される前提とする → NFR 2 でカバー
- 確認事項 3（CI での schema 検証）: 本 Issue のスコープ外とし、将来案として記録 → Out of
  Scope に明記
