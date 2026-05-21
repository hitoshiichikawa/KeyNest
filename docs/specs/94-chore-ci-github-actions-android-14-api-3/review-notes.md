# Review Notes — Issue #94 (round 1)

## Summary

本 Issue は KeyNest リポジトリ初の GitHub Actions workflow として
Android 14 (API 34) emulator 上で `./gradlew connectedDebugAndroidTest`
を実行する `.github/workflows/instrumentation-test.yml` を導入する CI
タスクである。本 Issue には `design.md` / `tasks.md` が存在せず、
`impl-notes.md` 冒頭で「軽量フロー」として明示されているため、
Boundary 判定は requirements.md の Out of Scope / Non-Goal を参照する。

`git diff --stat develop..HEAD` で確認した変更は以下 6 ファイル:

- `.github/workflows/instrumentation-test.yml`（新規 167 行）
- `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/ci/CiEmulatorSmokeTest.kt`（新規 71 行）
- `README.md`（+8 行、`## CI` 節追加）
- `CONTRIBUTING.md`（+9 行、`### CI: instrumentation tests on Android 14 (API 34)` 節追加）
- `docs/specs/94-chore-ci-github-actions-android-14-api-3/requirements.md`（新規 381 行）
- `docs/specs/94-chore-ci-github-actions-android-14-api-3/impl-notes.md`（新規 197 行）

`app/build.gradle.kts` / `gradle/libs.versions.toml` /
`AndroidManifest.xml` のいずれにも diff が無いことを `git diff develop..HEAD --
app/build.gradle.kts gradle/libs.versions.toml app/src/main/AndroidManifest.xml`
で確認済み（NFR 2.1 充足）。既存 `KeyNestCredentialProviderServiceInstrumentationTest`
の 3 メソッドは `@Ignore` のまま保持されている（Non-Goal §「@Ignore 除去は本
Issue では行わない」遵守）。

Developer impl-notes の要点:

- `reactivecircus/android-emulator-runner@v2`（major pin）+ `actions/setup-java@v4`
  （Temurin JDK 17）+ `gradle/actions/setup-gradle@v4` + `actions/cache@v4`
  （AVD snapshot 用、key = `avd-api-34-google_apis-x86_64-${{ runner.os }}-v1`）
  + `actions/upload-artifact@v4` の構成。
- AVD snapshot cache 採用、KVM udev rule 設定済み、`concurrency.cancel-in-progress: true`、
  `permissions: contents: read`、`timeout-minutes: 45`。
- Dummy smoke test は `ci/` パッケージに 2 メソッド配置、Truth + AndroidJUnit4
  の既存依存のみ使用、新規 dependency なし。
- pre-existing な `SignatureMismatchTest.kt:79` の compile error（`CredentialRepository.clearAll()`
  未 override）は本 Issue 起因ではないことを stash / 退避実験で確証。本 Issue
  では修正せず別 Issue 対応として確認事項 4 に記載（requirements §4.5 の方針に整合）。

## AC Coverage

| AC ID | 判定 | 根拠 |
|---|---|---|
| 1.1 emulator 起動 | covered | `reactivecircus/android-emulator-runner@v2` を `api-level: 34` で 2 ステップ（snapshot 作成 + main run）使用 |
| 1.2 `./gradlew connectedDebugAndroidTest` 実行 + ログ | covered | `script: ./gradlew connectedDebugAndroidTest --stacktrace`（Actions runner が stdout/stderr を CI ログに転送） |
| 1.3 失敗時 status `failure` | covered | Gradle 非 0 → step fail → job fail → check `failure`（GH Actions default 挙動。明示的 `continue-on-error` 等は無い） |
| 1.4 API 34 固定 / matrix なし | covered | `api-level: 34` ハードコード、`matrix:` 使用なし |
| 1.5 JDK 17 | covered | `actions/setup-java@v4` `java-version: 17` `distribution: temurin` |
| 1.6 emulator 起動失敗時の明示エラー | covered | action 自体の非 0 終了 + `timeout-minutes: 45` で silent skip 防止 |
| 2.1 emulator job が独立 | covered | 単一 job `instrumentation-test` のみ、`needs:` なし。将来の unit/lint job を別 job として追加できる構成 |
| 2.2 単独 fail が他 job を巻き込まない | covered | matrix の片足落ち構造を採用していない（matrix 自体を使っていない） |
| 2.3 cache key 競合回避 | covered | AVD cache を `actions/cache@v4` で `avd-api-34-google_apis-x86_64-${{ runner.os }}-v1` 専用 key にし、Gradle cache は `gradle/actions/setup-gradle@v4` で別管理 |
| 2.4 後続 job 追加余地 | covered | workflow が emulator job 単独で、将来 `unit-test:` 等を兄弟 job として追加可能 |
| 3.1 trigger = PR(main/develop) + push(main/develop) | covered | `on.pull_request.branches: [main, develop]` / `on.push.branches: [main, develop]` |
| 3.2 draft PR で起動しない | covered | job-level `if: github.event_name != 'pull_request' || github.event.pull_request.draft == false` |
| 3.3 feature branch push では起動しない | covered | trigger を `pull_request` と `push` (branches 限定) に絞っている |
| 3.4 `ready_for_review` で再評価 | covered | `on.pull_request.types: [opened, synchronize, reopened, ready_for_review]` |
| 4.1 dummy passing instrumentation test 1 件追加 | covered | `CiEmulatorSmokeTest.kt` に 2 メソッド (`jvmArithmetic_isFunctional`, `instrumentationRegistry_targetContext_isOurApp`) |
| 4.2 既存 instrumentation test と同 package 配下 / 既存依存のみ | covered | `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/ci/` 配下、AndroidJUnit4 + Truth + `InstrumentationRegistry` の既存依存のみ使用、`@SdkSuppress` 不付与 |
| 4.3 `@Ignore` 解除時の参考実装 | covered | KDoc に参考実装としての位置付け明記、AndroidJUnit4 runner / Truth で実装 |
| 4.4 削除可能だが本 Issue では削除しない | covered | KDoc の `Lifecycle:` 節に「deletable when placeholder activates, should not be removed in this Issue」と明記 |
| 4.5 既存 instrumentation test も実行 | covered | `connectedDebugAndroidTest` は source set 全体を実行（select 絞り込みなし）。pre-existing な `SignatureMismatchTest.kt` compile error は impl-notes 確認事項 4 で「本 Issue では修正せず別 Issue」と明示 |
| NFR 1.1 `GITHUB_TOKEN` 追加スコープ要求しない | covered | `permissions: contents: read` 明示 |
| NFR 1.2 third-party action の version pin | covered | `@v2` major pin（粒度選択は impl-notes 確認事項 1 で人間 reviewer に委ねる構成、requirements の許容範囲内） |
| NFR 1.3 secrets を渡さない | covered | `env:` / `secrets.*` 参照無し |
| NFR 2.1 既存 build / manifest 不変 | covered | `git diff` で `app/build.gradle.kts` / `libs.versions.toml` / `AndroidManifest.xml` への diff 無しを確認 |
| NFR 2.2 dummy test の package / 命名規則踏襲 | covered | 既存 `security/` `e2e/` `ui/` `perf/` `credentialprovider/` と同列に `ci/` を新設、AndroidJUnit4 + Truth 既存パターン |
| NFR 2.3 README / CONTRIBUTING 追記は最小限 | covered | README +8 行、CONTRIBUTING +9 行、既存セクション構成を破壊していない |
| NFR 3.1 test report 可視化 | covered | `actions/upload-artifact@v4`（`if: always()`、`retention-days: 14`）で `app/build/reports/androidTests/connected/**` と `app/build/outputs/androidTest-results/connected/**` を保存 |
| NFR 3.2 10〜25 分目標 | covered (best-effort) | AVD snapshot cache 有効化で目標範囲内見込み。`timeout-minutes: 45` は flake セーフティ。requirements 自体が「ハードな受入条件にはしない」明記のため不問。 |

## Boundary Check

本 Issue は `tasks.md` を持たない軽量フローのため `_Boundary:_` アノテー
ションは存在しない。代わりに requirements.md の Non-Goal / Out of Scope を
boundary として評価した。

- Non-Goal「API 34 以外の matrix」: matrix 化されていない ✓
- Non-Goal「`@Ignore` 除去」: `KeyNestCredentialProviderServiceInstrumentationTest`
  の 3 メソッドの `@Ignore` は手付かず ✓（`Read` で内容確認済み）
- Non-Goal「`compileSdk` / `targetSdk` 引き上げ」: `app/build.gradle.kts` に diff 無し ✓
- Non-Goal「emulator 上で `:app:lintDebug`」: workflow の `script:` は
  `connectedDebugAndroidTest` のみ ✓
- Non-Goal「pre-existing lint error / unit test failure の修正」: 該当ファイルへの
  diff 無し ✓
- NFR 2.1「既存 `app/build.gradle.kts` / `libs.versions.toml` / `AndroidManifest.xml`
  を変更しない」: `git diff` 0 行で確認 ✓

Boundary 逸脱は **無し**。

## Test Coverage

- 本 Issue は CI 設定追加が主目的で、製品コード変更を伴わない。
- requirements §4.1 で求められた dummy passing instrumentation test は
  `CiEmulatorSmokeTest.kt`（2 メソッド）として追加済み。
- CONTRIBUTING.md「Tests required for any logic change」は logic 変更が無いため
  非該当。
- CLAUDE.md は本 worktree に存在しないため、追加のテスト規約は未定義。
- Workflow YAML の `actionlint` / `yamllint` による静的検証は impl-notes で
  「未実施（バイナリ未配置）」と明記されているが、実行確認は最初の CI run
  に委ねられる構造であり、本 Issue の boundary 内では受容できる。

テスト規約上の必須テスト欠落は **無し**。

## Findings

reject 理由となる AC 未カバー / missing test / boundary 逸脱はいずれも検出
されなかった。

参考所見（reject 理由ではなく次の Developer/PjM への引き継ぎ情報）:

- impl-notes 確認事項 4 が指摘する `SignatureMismatchTest.kt` の pre-existing
  compile error（`CredentialRepository.clearAll()` 未 override）は、本 workflow
  が CI で動き出した瞬間に emulator job を red にする可能性が高い。
  requirements §4.5 が「pre-existing failure の修正は本 Issue では行わない /
  別 Issue で扱う」と明示しているため、これは本 review では reject 事由に
  ならないが、初回 CI run 前に別 Issue で先行 fix するか、本 PR merge 前後の
  対応方針を PjM 段階で確認することを推奨する。
- 確認事項 1（version pin 粒度）/ 2（AVD cache 採否）/ 3（matrix 化想定）は
  requirements の確認事項として未決のまま impl-notes に持ち越されており、
  Developer が推奨デフォルトを採用している。これも reject 事由ではないが、
  最終的な採否判断は人間 reviewer に委ねられる。

RESULT: approve
