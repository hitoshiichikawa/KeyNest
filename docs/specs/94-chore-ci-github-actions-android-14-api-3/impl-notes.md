# Implementation Notes — Issue #94 / chore(ci): GitHub Actions workflow for Android 14 (API 34) instrumentation tests

> 関連: `requirements.md`（本ディレクトリ）
>
> 本ファイルは Developer サブエージェントが実装中に得た知見と確認事項を残すための
> 作業ログ。requirements.md は設計フェーズで確定済みなので書き換えない。
> （本 Issue は design.md / tasks.md を伴わない軽量フローのため、本 impl-notes が
> 唯一の実装側ドキュメント。）

## サマリ

| 受入基準 (requirements §) | 対応箇所 | 状態 |
|---|---|---|
| §1.1 API 34 emulator を `reactivecircus/android-emulator-runner` で起動 | `.github/workflows/instrumentation-test.yml` `instrumentation-test` ジョブ | DONE |
| §1.2 `./gradlew connectedDebugAndroidTest` 実行 + ログ出力 | 同 workflow `Run connectedDebugAndroidTest on API 34 emulator` ステップ | DONE |
| §1.3 失敗時に PR status を `failure` にする | Gradle 失敗 → step 失敗 → job 失敗 → check `failure`（GitHub Actions の default 挙動） | DONE |
| §1.4 API 34 固定 / matrix 化しない | `api-level: 34` ハードコード | DONE |
| §1.5 JDK 17 セットアップ | `actions/setup-java@v4` `java-version: 17` `distribution: temurin` | DONE |
| §1.6 emulator 起動失敗時の明示エラー | action 自体の非 0 終了 + `timeout-minutes: 45` で silent skip 防止 | DONE |
| §2.1 unit/lint/build とは独立 job | 本 workflow は emulator job 1 個のみ。他 job は別 workflow で後続追加可能な構成 | DONE |
| §2.2 emulator 単独 fail で他 job の成否が個別に見える | 同上（matrix の片足落ち構造を採らない） | DONE |
| §2.3 cache key が他 job と衝突しない命名 | `avd-api-34-google_apis-x86_64-${{ runner.os }}-v1` を AVD 専用 key として明示 | DONE |
| §3.1 trigger = PR (main/develop) + push (main/develop) | `on.pull_request.branches` / `on.push.branches` 共に `[main, develop]` | DONE |
| §3.2 draft PR では起動しない | job-level `if: github.event_name != 'pull_request' || github.event.pull_request.draft == false` | DONE |
| §3.3 feature branch への push では起動しない | trigger を `pull_request` / `push: branches: [main, develop]` に限定 | DONE |
| §3.4 `ready_for_review` 遷移で再評価 | `on.pull_request.types` に `ready_for_review` を含める | DONE |
| §4.1 dummy passing instrumentation test を 1 件追加 | `app/src/androidTest/.../ci/CiEmulatorSmokeTest.kt` 2 メソッド | DONE |
| §4.2 既存 instrumentation test と同 package 配下に配置 | `io.github.hitoshiichikawa.keynest.ci` 配下 / 既存依存のみ使用 | DONE |
| §4.3 `@Ignore` 解除時の参考実装 | `AndroidJUnit4` runner / Truth 使用 / KDoc に明記 | DONE |
| §4.4 後続 Issue で削除可だが本 Issue では削除しない | KDoc に「lifecycle: deletable when `@Ignore` placeholder activates」と記載 | DONE |
| §4.5 既存 instrumentation test もすべて実行 | `./gradlew connectedDebugAndroidTest` が source set 全体を実行（dummy のみ select しない） | DONE |
| NFR 1.1 `GITHUB_TOKEN` の追加スコープを要求しない | `permissions: contents: read` 明示 | DONE |
| NFR 1.2 third-party action の version pin | `reactivecircus/android-emulator-runner@v2` (major pin。確認事項 1 参照) | DONE |
| NFR 1.3 secrets を emulator job に渡さない | `env:` / `secrets.*` 参照ゼロ | DONE |
| NFR 2.1 既存 `app/build.gradle.kts` / `libs.versions.toml` / `AndroidManifest.xml` を変更しない | コード変更ゼロ。workflow / dummy test / docs のみ | DONE |
| NFR 2.2 既存 instrumentation test の package / 命名規則踏襲 | `ci/CiEmulatorSmokeTest.kt`（既存 `security/` `e2e/` `ui/` `perf/` `credentialprovider/` に並ぶ新 package） | DONE |
| NFR 2.3 README / CONTRIBUTING への追記は最小限 | README に 5 行、CONTRIBUTING に「CI: instrumentation tests on Android 14 (API 34)」節 8 行 | DONE |
| NFR 3.1 失敗時 PR 上で test 落ちを可視化 | `actions/upload-artifact@v4` で `app/build/reports/androidTests/connected/**` / `outputs/androidTest-results/connected/**` を保存 | DONE |
| NFR 3.2 標準で 10〜25 分以内 | AVD snapshot cache 有効化で目標達成想定。`timeout-minutes: 45` は flake セーフティ | 想定 (要実 CI 検証) |

## 採用した設計判断

タスク指示にあった「推奨デフォルト」をすべてそのまま採用した。本 Issue 内で
明示の判断材料が増えた点 / 補足したい点だけ以下に記録する。

| 項目 | 採用値 | 補足理由 |
|---|---|---|
| `reactivecircus/android-emulator-runner` version pin | `@v2`（major pin） | NFR 1.2 / 確認事項 1 のレンジ内。タスク指示の推奨デフォルト。 |
| AVD snapshot キャッシュ | **有効化** | NFR 3.2 の「10〜25 分以内」目標達成のため。タスク指示の推奨デフォルト。Cache key は `avd-api-34-google_apis-x86_64-${{ runner.os }}-v1` で AVD 仕様変更 (target/arch/version) と分離。 |
| API レベル | **34 のみ**（matrix 化しない） | requirements §1.4 / Out of Scope 明示。 |
| target / arch | `google_apis` / `x86_64` | Credentials API の解決に google_apis が必要（PassKey provider 系の `androidx.credentials` を将来テストするため）。x86_64 は GitHub Actions runner の標準。 |
| 起動オプション | `-no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none` + `disable-animations: true` | `reactivecircus/android-emulator-runner` README の標準パターン。本 run のみ `-no-snapshot-save` を追加し、cache に書き戻すのは前段の AVD snapshot 生成ステップに限定（メイン run の終了が高速化）。 |
| Java 版 | `temurin` JDK 17 | `app/build.gradle.kts` の `JavaVersion.VERSION_17` / `kotlinOptions.jvmTarget = "17"` と整合。 |
| trigger | `pull_request` (branches: [main, develop], types += ready_for_review) + `push` (branches: [main, develop]) | requirements §3.1 / §3.4。 |
| draft PR スキップ | job-level `if` | requirements §3.2。 |
| concurrency | `cancel-in-progress: true` (group = workflow + ref) | コスト削減（タスク指示の推奨デフォルト）。同一 PR への連続 push でも最新 commit のみ走る。 |
| Gradle セットアップ | `gradle/actions/setup-gradle@v4` | Gradle dep cache が AVD cache とキー衝突しないよう、AVD cache とは別 action で管理。 |
| KVM 有効化 | udev rule で `MODE=0666` | `reactivecircus/android-emulator-runner` README 推奨の KVM 設定（GitHub Actions の `ubuntu-latest` runner は KVM 利用可だが default で permission がないため）。 |
| test report artifact | `actions/upload-artifact@v4` (`if: always()`, `retention-days: 14`) | NFR 3.1。失敗時もログを残せるよう `always()`。 |
| `permissions:` default | `contents: read` 明示 | NFR 1.1。`actions/checkout@v4` の最小権限。 |
| timeout-minutes | 45 | NFR 3.2 の 10〜25 分目標を超えても卓越 hang のみ kill する flake セーフティ。typical 完了時間とは別の数値（fail-fast ではなく fail-bounded）。 |

## 推奨デフォルトから変更したもの

なし。タスク指示の表に列挙された推奨デフォルトをすべて踏襲した。

## 実行したコマンドと結果

| Command | 結果 | 詳細 |
|---------|------|------|
| `./gradlew :app:assembleDebugAndroidTest` (1 回目) | **FAIL** | `compileDebugAndroidTestKotlin` で既存 `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/security/SignatureMismatchTest.kt:79` が `CredentialRepository.clearAll(): Unit` を実装していないため compile error。これは **本 Issue の変更とは無関係な pre-existing failure**（develop / fb6ef77 時点で再現する。下記参照）。 |
| `./gradlew :app:assembleDebugAndroidTest`（HEAD 変更を `git stash -u` した状態で再実行 = pre-existing 確認） | **FAIL** | 同じ `SignatureMismatchTest.kt:79` で同じ error。本 Issue で導入したものではないことを確証。 |
| `mv SignatureMismatchTest.kt /tmp/ → ./gradlew :app:compileDebugAndroidTestKotlin` (本 Issue 変更による回帰の有無を確認) | **PASS** | `BUILD SUCCESSFUL in 4s`。新規 `CiEmulatorSmokeTest.kt` は問題なくコンパイルできる。warning も 0 件（既存 ui/list / settings の unused param warning のみ）。完了後 `SignatureMismatchTest.kt` を元の位置に restore 済み。 |
| 環境変数 | `JAVA_HOME=/home/hitoshi/sdks/jdk-17` / `ANDROID_HOME=/home/hitoshi/sdks/android-sdk` | #90 impl-notes と同じ環境。 |

### Workflow ファイル自体の妥当性

- `actionlint` / `yamllint` バイナリは worktree 内に未配置。本 Issue では workflow を
  実行する Gradle / Kotlin の compile 検証 (`assembleDebugAndroidTest`) は実施した
  が、YAML schema レベルの検証は **目視 + GitHub Actions reference に照合** のみ。
- `reactivecircus/android-emulator-runner` v2 系の README サンプルとほぼ一致する
  構成にしてあるため、構文 invalid の確率は低い。
- GitHub の YAML パーサーは PR を開いた時点で `--workflow-validation: true` 相当の
  検証を行い、不正があれば Actions tab に red badge を出すため、初回 CI run が
  Reviewer ステージで赤くなれば即座に検知可能。

## 設計と異なる判断をした箇所

- なし。

## 確認事項（人間レビュアへ）

### 1. `reactivecircus/android-emulator-runner` の version pin 粒度

requirements §確認事項 1 そのまま。本 Issue では `@v2`（major pin）を採用した。
選択肢:

- **(a) `@v2`（採用）**: 簡易。security patch / bugfix を自動受信できる。tag が
  動かされた場合に挙動変化のリスクがあるが、KeyNest の既存依存ポリシー
  （`gradle/libs.versions.toml` の他 lib も major pin が多い）と整合する保守的選択。
- (b) `@v2.32.0` 等 patch pin: 安定だが手動 bump コストがかかる。Dependabot 等の
  自動化が未導入のリポジトリでは古い tag を踏み続けるリスクが大きい。
- (c) commit SHA pin: 最厳格だが、メンテナンスが重く 1 contributor リポジトリには
  オーバースペック。

人間レビュアが「supply chain security をもっと厳格にしたい」と判断する場合は
patch pin / SHA pin へ変更する余地あり。

### 2. emulator AVD snapshot cache の最終採否

requirements §確認事項 2 そのまま。本 Issue では「初回起動コスト > 2 回目以降の
boot 短縮メリット」が成立する一般的ケースを想定して **有効化** を選択した。
- 初回 run（cache miss）は AVD snapshot 生成ステップが追加で約 5〜10 分かかる
  想定。2 回目以降の run はこの分が省ける。
- cache key を `avd-api-34-google_apis-x86_64-${{ runner.os }}-v1` としたので、
  API level / target / arch を変えると自動で invalidate される。明示的に invalidate
  したいときは末尾の `-v1` を `-v2` に bump する。
- **実 CI で「むしろ初回コストが受容できない」「cache 容量が問題」となれば、
  AVD snapshot 関連の 2 ステップ（`AVD cache` / `Create AVD snapshot for caching`）
  を削除するだけで無効化できる**（メイン run のステップ側はそのまま動く）。

### 3. API 34 のみで十分か（matrix 化想定の有無）

requirements §確認事項 3 そのまま。本 Issue では API 34 単独で固定（matrix 化なし）。
- 将来 `compileSdk` / `targetSdk` を 35 へ引き上げる別 Issue が起票された際、本
  workflow を `matrix: api-level: [34, 35]` に拡張するか、API level の置換のみで
  済ませるかは別途判断。
- 拡張時は `fail-fast: false` の設定 + matrix の片足落ちで status check が混乱
  しないよう、必要なら job 名を `${{ matrix.api-level }}` で展開する形にする。

### 4. pre-existing な `SignatureMismatchTest.kt` compile error

`./gradlew :app:assembleDebugAndroidTest` が現在 fail する根本原因は本 Issue とは
独立の compile error である:

```
e: app/src/androidTest/java/io/github/hitoshiichikawa/keynest/security/SignatureMismatchTest.kt:79:9
   Object is not abstract and does not implement abstract member
   public abstract suspend fun clearAll(): Unit
   defined in io.github.hitoshiichikawa.keynest.domain.repository.CredentialRepository
```

`SignatureMismatchTest.stubRepo` 内の anonymous object が `CredentialRepository.clearAll()`
を override していないために発生している。`git log` 上 `SignatureMismatchTest.kt`
への直近変更は `3149397 chore(release): Kotlin パッケージを io.github.hitoshiichikawa.keynest にリネーム`
で、`clearAll()` メソッド追加はこれより後のコミットで `CredentialRepository`
interface に入った可能性が高い。

- **影響**: 本 Issue が CI workflow を入れることで、この pre-existing な
  compile error が初回 emulator run でも顕在化する。emulator が起動して
  Gradle task に到達した段階で compile fail し、emulator job が `failure` になる。
- **本 Issue では修正しない**（requirements §4.5「既存で pass する前提が崩れて
  いた場合は本 Issue では行わない」「別 Issue で扱う」に従う）。
- 別 Issue で `SignatureMismatchTest.stubRepo` の anonymous object に
  `override suspend fun clearAll() = error("n/a")` 等を追加する fix が必要。
  本 issue で 1 行 fix を入れることも可能だが、requirements の Out of Scope を
  優先し、Developer 単独判断では触らない。**Reviewer / PjM 段階で「先に直して
  しまうか別 Issue にするか」を判断願う**。

### 5. AVD AAR metadata の API 35 強制問題（#90 で blocker だった件）への影響

#90 で `androidx.credentials` を `1.5.0` → `1.3.0` に下げて compileSdk 34 を維持
した経緯がある（#90 impl-notes Round 2 参照）。本 Issue の workflow は
`compileSdk = 34` の前提で書いてあるため、`androidx.credentials` を将来 1.5.0+
にアップグレードする別 Issue ではこの workflow を一緒に確認しておく必要がある。
- 具体的には: AVD の system image が `android-34` 系 (`google_apis`) であることに
  依存しているため、AGP / compileSdk を 35 に上げるなら本 workflow の `api-level: 34`
  も 35 に揃える（または matrix 化する）。

## 後続作業（本 Issue 外）

- 別 Issue: `SignatureMismatchTest.kt` の `clearAll()` 未実装 fix（確認事項 4）
- 別 Issue: pre-existing な lint error 151 件の baseline 設定 / 解消（#90 impl-notes
  確認事項 3 と同一テーマ）
- 別 Issue (#89 分割案 3): `KeyNestCredentialProviderServiceInstrumentationTest.serviceBinding_returnsEmptyCreateResponse`
  の `@Ignore` 解除。**本 Issue の workflow が動き始めた後でないと CI 上で動かない**。
- 別 Issue (#89 分割案 4): 同 `.serviceBinding_returnsEmptyGetResponse` /
  `.serviceBinding_clearCredentialState_succeeds` の `@Ignore` 解除。

## 作業ログ

- requirements.md / CONTRIBUTING.md / `app/build.gradle.kts` /
  既存 `KeyNestCredentialProviderServiceInstrumentationTest.kt` /
  #90 impl-notes.md を読了。
- 既存 `.github/` 配下に workflows ディレクトリが存在しないことを確認
  （`.github/ISSUE_TEMPLATE/` のみ）。本 Issue が最初の workflow を導入する。
- `.github/workflows/instrumentation-test.yml` を新規作成。
- `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/ci/CiEmulatorSmokeTest.kt`
  を新規作成（既存 `AesGcmCipherTest` 等と同じ runner / 依存パターン）。
- `README.md` に CI 節を追加（5 行）。
- `CONTRIBUTING.md` の Pull Requests 節末尾に CI 説明を追加（8 行）。
- `./gradlew :app:assembleDebugAndroidTest` を 2 回実行（本 Issue 変更込 / 変更
  抜き）し、pre-existing な `SignatureMismatchTest.kt` compile error が本 Issue
  起因ではないことを確証。問題ファイルを一時退避した状態での
  `:app:compileDebugAndroidTestKotlin` が PASS することで、新規 dummy test の
  コンパイル妥当性を確認。
- 本 impl-notes.md を作成。
