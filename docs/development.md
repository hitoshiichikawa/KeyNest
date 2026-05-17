# 開発者向け手順

KeyNest リポジトリで開発を進める際に必要な、Issue / PR 単位では収まらない横断的な
運用手順を集約するドキュメントです。

## 目次

- [applicationId 変更時の schema ディレクトリ移行手順](#applicationid-変更時の-schema-ディレクトリ移行手順)

---

## applicationId 変更時の schema ディレクトリ移行手順

KeyNest の Room データベース (`KeyNestDatabase`) は `exportSchema = true` を有効化して
おり、KSP ビルド時に `app/build.gradle.kts` の `room.schemaLocation` 設定に従って
`app/schemas/<applicationId>.data.KeyNestDatabase/<version>.json` を生成します。
applicationId を変更すると Room の出力先 FQCN が連動して変わるため、旧 applicationId
配下のディレクトリが working tree に取り残され、issue-watcher が dirty 検出により
ジョブを skip し続ける事象が発生します（Issue #63 参照）。

将来 applicationId を変更する PR では、以下の手順を **同一 PR 内で完結** させてくだ
さい。手順は順序を守ること（特に手順 4 の `.gitignore` 更新を手順 5 のローカルビルド
より前に行うことで、再生成された旧ディレクトリが dirty 扱いされなくなります）。

### 前提

- 旧 applicationId を `OLD_APP_ID`、新 applicationId を `NEW_APP_ID` と表記します。
- 既存例（PR #55）の場合: `OLD_APP_ID=com.example.keynest`、
  `NEW_APP_ID=inc.goodanswers.keynest`。
- 作業はリポジトリルートで実行します。

### 手順 1: 旧 applicationId 配下を作業ツリーから削除する

旧 FQCN 配下のディレクトリを物理削除し、tracking 対象だった場合は git からも除去
します。tracking されていない (untracked) 場合でも `git rm -r` は失敗しないよう
`--ignore-unmatch` を併用します（後段の手順 4 で `.gitignore` 経由でも防衛するため、
ここでの削除が冪等であることが重要です）。

```sh
# tracked 状態だった場合の除去（履歴上は最後の状態が残る）
git rm -r --ignore-unmatch "app/schemas/${OLD_APP_ID}.data.KeyNestDatabase/"

# untracked 残置を含めて作業ツリーから物理削除
rm -rf "app/schemas/${OLD_APP_ID}.data.KeyNestDatabase/"
```

### 手順 2: 新 applicationId 配下を追跡対象に追加する

新 FQCN 配下に生成された schema JSON をすべて `git add` で追跡対象に加えます。
通常はローカルで `./gradlew :app:assembleDebug`（または `:app:compileDebugKotlin`）を
1 度実行して KSP に schema を再生成させてから add します。

```sh
# ローカル KSP ビルドで schema を再生成
./gradlew :app:assembleDebug

# 生成された JSON をすべて tracking 対象に追加
git add "app/schemas/${NEW_APP_ID}.data.KeyNestDatabase/"

# 追跡状態の確認
git ls-files "app/schemas/${NEW_APP_ID}.data.KeyNestDatabase/"
```

`git ls-files` の出力に `v1.json`、`v2.json` など現時点で存在するバージョンの
JSON が 1 件以上列挙されていることを確認します。

### 手順 3: コード側の Room 参照を新 applicationId に追従させる

`MigrationTestHelper` の引数や DI 設定など、FQCN 文字列を参照している箇所があれば
新 applicationId に追従させます。Room の出力先は `app/build.gradle.kts` の
`room.schemaLocation` で制御されており、テストは applicationId に基づいた FQCN 配下を
読みに行くため、コード側と schema ディレクトリ名が一致している必要があります。

### 手順 4: `.gitignore` に旧 applicationId 防衛パターンを追記する

以後 KSP が誤って旧 FQCN 配下に schema を再生成しても dirty 検出されないよう、
`.gitignore` に旧 applicationId 配下のパスを ignore として追加します。コメントで
「旧 applicationId 防衛用」である旨を明記してください。

```diff
 # OS junk
 .DS_Store
 Thumbs.db
+
+# Room schema export defensive ignore
+#
+# applicationId 変更前 (<OLD_APP_ID>) の Room schema 出力先。
+# 旧 FQCN 配下に再生成された schema を dirty 扱いさせないための防衛用 ignore。
+app/schemas/<OLD_APP_ID>.data.KeyNestDatabase/
```

既存の Issue #63 対応で `com.example.keynest.data.KeyNestDatabase/` のエントリが
追加済みです。さらに applicationId を変更する場合は、エントリを追加で積み増します
（既存エントリは履歴防衛として残します）。

### 手順 5: `git status` が clean であることを確認する

すべての変更を commit した上で `git status` を実行し、出力が clean working tree を
示すことを確認します。

```sh
git status
# On branch <ブランチ名>
# nothing to commit, working tree clean
```

旧 applicationId 配下や新 applicationId 配下のいずれにも untracked / modified が
残っていないこと、特に `app/schemas/${OLD_APP_ID}.data.KeyNestDatabase/` に関する
記述が `git status` 出力に **一切現れない** ことを確認してください。

### コミット粒度の推奨

上記手順は 1 PR 内で以下のように commit を分割すると、レビュー時に意図が読みやすく
なります（Issue #63 の対応 PR を参考にしてください）:

1. `chore(schemas): track current applicationId schema JSON`
   — 新 applicationId 配下の JSON を `git add`
2. `chore(gitignore): add defensive ignore for legacy applicationId schema dirs`
   — `.gitignore` への旧 FQCN 追記
3. `chore(release): update applicationId to <NEW_APP_ID>`
   — Gradle / AndroidManifest 等の applicationId 本体の更新

順序は前後しても良いですが、commit 単位での後戻りを容易にするため
**schema 追加と applicationId 変更は別 commit** にすることを推奨します。
