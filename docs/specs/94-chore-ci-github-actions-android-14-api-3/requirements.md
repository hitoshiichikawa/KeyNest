# Requirements Document

## 概要 / Goal / Non-Goal

### 概要

KeyNest は PassKey 対応（umbrella Issue #89）の Phase 1 として、#90 で
`CredentialProviderService` の Manifest 登録・最小骨組み・空応答実装を導入した。
`CredentialProviderService` は **Android 14 (API 34) 以降の OS framework から
bind されることで初めて挙動が確定** するため、Service の bind 検証や
`BeginCreateCredentialResponse` / `BeginGetCredentialResponse` の往復確認は
**API 34+ エミュレータ上での instrumentation test** が必須となる。

一方、現状の本リポジトリには `.github/workflows/` 配下の GitHub Actions
workflow が存在しない。#90 round 2 の impl-notes.md の通り、Phase 1 で追加した
`KeyNestCredentialProviderServiceInstrumentationTest` は `@Ignore` placeholder
として配置されたままで、CI 上で自動実行されていない（#90 design §7.2 / §9.4 で
本 Issue (#94) に carve out 済み）。

本 Issue (#94) は Phase 2 以降 (登録セレモニー = #89 分割案 3 / 認証セレモニー
= #89 分割案 4) で instrumentation test を必須化する前提として、

- `reactivecircus/android-emulator-runner`（または同等 action）を使い
  **API 34 emulator を起動して `./gradlew connectedDebugAndroidTest` を実行する
  workflow** を新規追加する
- 既存の unit test / lint / build とは **別 job** に分離する（emulator job は
  実行時間が長いため）
- workflow 自体の動作確認のため、**ダミーの passing instrumentation test を
  1 件追加**する
- README / CONTRIBUTING.md に CI 構成変更のメモを追記する

ところまでを到達点とする。
**既存の `@Ignore` placeholder（`KeyNestCredentialProviderServiceInstrumentationTest`
3 メソッド）の `@Ignore` 除去は本 Issue では行わない**。各機能 Issue (Phase 2
以降) で当該 callback を実装する際に外す。

### Goal

- `.github/workflows/instrumentation-test.yml`（仮称）を新規追加し、API 34
  emulator を `reactivecircus/android-emulator-runner` で起動して
  `./gradlew connectedDebugAndroidTest` を実行する job を整備する。
- emulator job を unit test / lint / build job とは独立した job として並列実行
  可能な構成にする（matrix の片足落ちで他 job の成否が見えなくなる構造は採らな
  い）。
- PR / `main` / `develop` への push をトリガとする。draft PR では起動しない。
- workflow の動作確認のため、dummy passing instrumentation test を 1 件追加し、
  emulator 上で実際に pass することを示す。
- README または CONTRIBUTING.md に CI 構成変更のメモを追記する。

### Non-Goal (Out of Scope)

- API 34 以外（33 以下 / 35 以上）のマトリクスビルド（将来必要になったら別 Issue）。
- ローカル開発者向けの「自宅エミュレータで instrumentation test を走らせる手順
  書」（別 Issue）。
- `connectedDebugAndroidTest` 以外の UI 自動化（Espresso 拡張 / Macrobenchmark
  / screenshot test 等）整備。
- 既存 instrumentation test の `@Ignore` 除去（#90 で追加された
  `KeyNestCredentialProviderServiceInstrumentationTest` の 3 メソッドを含む）。
  これらは各機能 Issue（#89 分割案 3 / 4）で本来の bind 検証実装と共に外す。
- 既存テストの `compileSdk` / `targetSdk` 引き上げ（#90 NFR 2.2 で `compileSdk`
  を上げない方針が明示されている）。
- emulator 上で `:app:lintDebug` を回す統合（pre-existing な lint error 151 件
  の解消は別 Issue。#90 impl-notes 確認事項 3 参照）。
- AVD snapshot キャッシュの最終採否（確認事項 2 で未決のため設計判断は本要件で
  確定しない）。

## 背景

- umbrella Issue #89 で、KeyNest は Android Credential Manager API
  (`CredentialProviderService`) に PassKey プロバイダとして登録される方針が
  確定済み。
- #90 (`feat(passkey): CredentialProviderService の manifest 登録と最小骨組み`)
  で `KeyNestCredentialProviderService` の空応答スケルトンが導入され、
  Phase 1 として Robolectric / JVM 単体テストで requirements 6 を満たした。
- 同時に、Service の **OS bind 経路** は Robolectric では完全には再現できない
  ため、`app/src/androidTest/java/io/github/hitoshiichikawa/keynest/credentialprovider/KeyNestCredentialProviderServiceInstrumentationTest.kt`
  に `@Ignore` 付きの placeholder を 3 メソッド配置した（#90 tasks T-05 /
  design §7.2）。`@SdkSuppress(minSdkVersion = 34)` で API 34+ にゲーティングし
  ている。
- これら placeholder の `@Ignore` を外して CI で実行するためには、**CI 上で
  API 34 emulator が利用可能であること** が前提となる。本 Issue (#94) はその
  前提を整備する。
- 現状の本リポジトリには `.github/workflows/` ディレクトリが存在しない（本 Issue
  で新規作成）。
- 現行 `app/build.gradle.kts` は `compileSdk = 34` / `minSdk = 26` /
  `targetSdk = 34` / `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`
  設定済み。`app/src/androidTest/` 配下には既存 instrumentation test が複数
  存在する（`AesGcmCipherTest`、`AutofillFlowTest`、`KeystoreKeyProviderTest`、
  `CredentialListActivityTest`、`DangerZoneActivityTest`、`SettingsActivityTest`、
  `SignatureMismatchTest`、`FillRequestLatencyTest`、上述 `KeyNestCredentialProviderServiceInstrumentationTest`
  等）。これらは現在 CI で自動実行されておらず、本 Issue の workflow 整備により
  まとめて CI 経路に乗ることになる。
- CONTRIBUTING.md は base branch を `develop` と明記しており（`main` は release
  branch）、PR は `develop` 宛て。emulator job のトリガもこれに合わせる
  （Requirement 3.1）。

## ユーザーストーリー

- As a 後続セレモニー Issue (#89 分割案 3 / 4) の実装担当, I want 自分の Issue
  で `KeyNestCredentialProviderService` の callback 実体を実装したとき、CI 上で
  Android 14 emulator が立ち上がり instrumentation test が自動実行されること,
  so that **OS から bind される経路の挙動** を Robolectric で再現できない部分も
  含めて回帰検知できる。
- As a メンテナ / レビュア, I want PR ごとに emulator job が走り、結果が PR
  status check に反映されること, so that **API 34+ 限定の機能** が壊れたまま
  merge されるリスクを CI で検知できる。
- As a 一般 contributor, I want emulator job が unit test / build job とは独立
  しており、emulator が flaky のときでも他 job の合否が独立して可視化される
  こと, so that 「emulator が落ちただけで build まで赤く見える」状態を避け
  られる。
- As a CI コスト管理者, I want emulator job が draft PR では起動しないこと,
  so that **review-ready でない PR** で重い emulator を立ち上げて CI 時間を
  浪費しない。

## Requirements

> EARS 形式 (The X shall …, When … the X shall …, While … the X shall …) で
> 記述。Issue #94 本文の EARS draft を踏襲し、必要に応じて補強している。

### Requirement 1: emulator job 追加

**Objective:** As a CI システム, I want API 34 emulator を起動して
`./gradlew connectedDebugAndroidTest` を実行する job を持つこと, so that
`CredentialProviderService` 系の bind 検証を含む instrumentation test を CI で
自動回帰検知できる。

#### Acceptance Criteria

1.1. The CI workflow shall API 34 emulator を `reactivecircus/android-emulator-runner`
（または同等の action）で起動できる。

1.2. The emulator job shall `./gradlew connectedDebugAndroidTest` を実行し、
標準出力 / 標準エラーの結果を CI ログに出力する。

1.3. While `connectedDebugAndroidTest` が 1 件でも失敗した場合, the emulator job
shall PR / push の status check を `failure` にする。

1.4. The emulator job shall 利用する emulator API level を **API 34** に固定する
（マトリクス化はしない、Out of Scope）。

1.5. The emulator job shall JDK 17 をセットアップする（`app/build.gradle.kts` の
`compileOptions = JavaVersion.VERSION_17` / `kotlinOptions.jvmTarget = "17"`
に合わせる）。

1.6. The emulator job shall job が起動できなかった場合（emulator 自体の起動失敗 /
SDK 取得失敗等）に明確なエラーメッセージを CI ログに残し、`failure` で終了する
（silent skip しない）。

### Requirement 2: 既存 job との分離

**Objective:** As a メンテナ / レビュア, I want emulator job と unit test /
lint / build job が独立した job であること, so that emulator が flaky のときでも
他 job の合否が独立して見え、PR review の判断を妨げない。

#### Acceptance Criteria

2.1. The emulator job shall 既存の unit test / build job とは独立した job
として並列実行可能である（GitHub Actions の `jobs:` ブロック内で別 job として
定義し、`needs:` で順序依存を作らない、あるいは最小限の依存のみとする）。

2.2. If emulator job だけが失敗したとき, the unit test / build job の成否は
PR / push の status check 上で **個別に** 確認可能であること（matrix の片足落ち
で他 job の成功表示が消える構成は採らない）。

2.3. The emulator job shall 既存 unit test / lint / build job のキャッシュ
（gradle / SDK 等）と **競合しない** キャッシュ key 命名を用いる（emulator AVD
スナップショットを cache 化する場合は確認事項 2 を参照）。

2.4. The unit test / lint / build job が本 Issue で **新規に追加** されるか、
既存の手元 CI ファイルが存在しないため emulator job 1 個だけを最初に追加するか
は本要件では強制しない（実装フェーズで決定）。**ただし、emulator job のみを単独
で追加する場合でも、後続で unit test job を足したときに 2.1 / 2.2 が成立する
ように workflow ファイルを構成すること**。

### Requirement 3: 実行範囲

**Objective:** As a CI コスト管理者 / コントリビュータ, I want emulator job が
review-ready な PR と `main` / `develop` への push でのみ起動すること, so that
draft / WIP 状態の PR で emulator を立ち上げて CI 時間を浪費しない。

#### Acceptance Criteria

3.1. The emulator job shall **PR が `main` または `develop` を target にしている
とき** と、`main` / `develop` branch への push 時に起動する。
（CONTRIBUTING.md の通り PR は `develop` 宛てが基本だが、`main` への直接 push
／ release 由来の merge にも備えて両方を対象とする。）

3.2. The emulator job shall **draft PR では起動しない** （`if:
github.event.pull_request.draft == false` 等の gating を入れる）。

3.3. The emulator job shall feature branch への push（PR 未作成）では起動しない
（trigger を `pull_request` / `push: branches: [main, develop]` に限定する）。

3.4. The emulator job shall PR の **`ready_for_review` 遷移** で再評価される
（draft → ready で起動するか、ready 後の commit push で起動するかは設計判断。
最低でも「draft 時にスキップ」「ready_for_review 後の commit で実行」のいずれかの
経路で必ず 1 回は実行されること）。

### Requirement 4: テスト

**Objective:** As a 開発者 / メンテナ, I want 本 Issue で追加した workflow が
実際に emulator 上で test を走らせられることを示す proof を持つこと, so that
将来 `@Ignore` 解除 Issue（#89 分割案 3 / 4）で「workflow が機能していること」
を前提に作業できる。

#### Acceptance Criteria

4.1. The CI workflow shall 自身の動作確認のため、本 Issue で **ダミーの
passing instrumentation test を 1 件追加** し、emulator 上で実行できること
を示す。

4.2. The dummy test shall **既存 instrumentation test と同じ package 構成
配下**（`app/src/androidTest/java/io/github/hitoshiichikawa/keynest/...`）
に配置する。具体的な配置先は実装フェーズで決定して良いが、以下の指針に従う:

   - 既存の `credentialprovider/`（#90 で新設）配下に配置し、機能上の context
     を持たないことを class 名と KDoc で明示する選択肢が有力（後続 `@Ignore`
     解除時に同 package で隣接させやすい）。
   - **クラス名**は「目的が CI smoke であること」が読み取れる名前にする
     （例: `CiEmulatorSmokeTest`、`InstrumentationCiSmokeTest`、`AndroidTestEmulatorBootTest`
     等。最終決定は design / 実装フェーズで Developer に委ねる）。
   - **テスト内容**は emulator が起動し JUnit runner が test を実行できることを
     確認する最小実装（例: `assertThat(true).isTrue()` や `InstrumentationRegistry.getInstrumentation().targetContext.packageName` が空でないことの assertion 等）。
   - 既存テスト（`AesGcmCipherTest` 等）の依存（`AndroidJUnit4` runner / `Truth` /
     `androidx.test.ext.junit`）に揃え、依存追加は **行わない**。
   - `@SdkSuppress(minSdkVersion = 34)` は付与しない（dummy test は API 34
     限定の機能を触らないため、minSdk 26 を含む全 API レベルで pass する）。
     emulator API を本 Issue で 34 に固定するため、現状では `minSdk = 26`
     との差で skip されることはない。

4.3. The dummy test shall 機能 Issue が `@Ignore` 解除する際の **参考実装** と
しても機能する（既存 `KeyNestCredentialProviderServiceInstrumentationTest` の
`@Ignore` を外したときに、同じパターンで JUnit runner / `AndroidJUnit4` が使え
ることが読み取れる）。

4.4. The dummy test shall 本 Issue 完了後、後続 Issue で `@Ignore` 解除した
本物の bind 検証 test が pass し始めた段階で **削除可能** であるが、本 Issue
の時点では削除しない（CI workflow が常に「最低 1 件は instrumentation test を
emulator 上で実行している」状態を維持するため）。

4.5. The emulator job shall 既存 instrumentation test
（`AesGcmCipherTest` / `AutofillFlowTest` / `KeystoreKeyProviderTest` /
`SignatureMismatchTest` / `CredentialListActivityTest` /
`DangerZoneActivityTest` / `SettingsActivityTest` / `FillRequestLatencyTest`
等）も `connectedDebugAndroidTest` ターゲットで実行する。これらが既存で pass
する前提が崩れていた場合（pre-existing failure / 環境依存 flaky）は、修正は
本 Issue では行わず、`@Ignore` / `@FlakyTest` 等の最小調整に留めるか、別 Issue
で扱う。

## Non-Functional Requirements

### NFR 1: セキュリティ境界の不変

1. The workflow shall `GITHUB_TOKEN` の追加スコープを要求しない（read 権限の
   default 設定で動作すること）。
2. The workflow shall third-party action として `reactivecircus/android-emulator-runner`
   等を利用するが、`actions/checkout` / `actions/setup-java` 等 GitHub 公式以外の
   action は version pin（tag / commit SHA）して supply chain リスクを抑える
   （pin の粒度は確認事項 1 参照）。
3. The workflow shall リポジトリ secrets（keystore / API key 等）を emulator job
   に渡さない。本 Issue の dummy test / 既存 instrumentation test は debug
   build を使うため signing 情報は不要。

### NFR 2: 既存 job への非干渉

1. The workflow 追加 shall 既存の `app/build.gradle.kts` / `gradle/libs.versions.toml` /
   `AndroidManifest.xml` を変更しない（CI 設定のみの変更。コード変更を伴う場合は
   別 Issue で扱う）。
2. The dummy instrumentation test 追加 shall 既存 instrumentation test の package
   配置 / class 命名規則を踏襲し、既存 test の挙動を変えない。
3. The README / CONTRIBUTING.md への CI メモ追記 shall 既存セクション構成を破壊
   せず、最小限の追記（数行）に留める。

### NFR 3: 開発者体験

1. The workflow shall 失敗時に PR 上のチェック一覧から **どの test が落ちたか**
   が読み取れるよう、`gradle` の `--info` / test report artifact upload 等で
   結果を可視化する（具体的な手段は実装フェーズで決定）。
2. The emulator job shall 標準的なケースで **10〜25 分以内** に完了することを
   目標とする（厳密な数値は ハードな受入条件にはしない。AVD snapshot キャッシュ
   採否＝確認事項 2 と関連）。

## Out of Scope

- API 34 以外（33 以下 / 35 以上）の emulator マトリクス build。
- ローカル開発者向けの emulator 手順書 / Makefile 整備。
- `connectedDebugAndroidTest` 以外の UI 自動化（Espresso 拡張 / screenshot test /
  Macrobenchmark）の CI 統合。
- 既存 instrumentation test の `@Ignore` 除去（#90 で追加された 3 メソッドを
  含む）。
- pre-existing な unit test failure（`AppInfoProviderTest.get_returnsVersionNameFromBuildGradle`
  の `versionName` 期待値差）の修正（#90 impl-notes 確認事項 2 / 別 Issue 起票
  対象）。
- pre-existing な lint error 151 件の修正（#90 impl-notes 確認事項 3 / 別 Issue
  対象）。
- `:app:lintDebug` を emulator job に組み込むこと（unit test / lint / build は
  別 job で扱う方針なので、emulator job では `connectedDebugAndroidTest` のみ
  実行）。
- `compileSdk` / `targetSdk` / AGP 引き上げ（#90 NFR 2.2 / impl-notes Round 2 で
  `androidx.credentials` を `1.3.0` に下げて compileSdk 34 を維持した経緯あり。
  引き上げは別 Issue）。

## 確認事項 / オープン課題

> 本セクションは Issue #94 本文「確認事項」3 項目をそのまま転記したもの。
> **本 Issue 単体で依然未決の項目はすべて質問として残す**（推測で「決定済み」
> と書かない）。design / 実装フェーズで決着する場合は本 Issue 内の `design.md` /
> `impl-notes.md` で更新する想定。

### 本 Issue で未決（design / 実装フェーズで決着が必要）

1. **`reactivecircus/android-emulator-runner` の version 採用方針**:
   `@v2.x.y` 系の **最新固定（tag）** で良いか、`@v2` のように **major のみ pin**
   で良いか、それとも commit SHA pin まで踏み込むかを決める必要がある。
   NFR 1.2 で third-party action の version pin を要求しているが、粒度の選択肢
   は以下:
   - (a) `@v2`（major のみ pin）: 簡易だが micro release で挙動変化のリスク。
   - (b) `@v2.x.y`（patch まで pin）: 安定だが手動更新コスト。
   - (c) commit SHA pin: 最も厳格だが、メンテナンスが重い。

   どれを採用するかは design フェーズで確定する。

2. **emulator AVD のキャッシュ採否**:
   起動時間短縮のため `actions/cache` 等で AVD snapshot を GitHub Actions cache
   に保存するか。初回実行時は cache miss でコストが上乗せされるが、2 回目以降
   の emulator boot 時間が短縮できる。
   - 採用する場合: cache key の命名（API level / target / arch / system image
     hash 等を含めるか）を design で決める必要あり。
   - 採用しない場合: NFR 3.2 の「10〜25 分以内」目標に到達できるかは要検証。

   本 Issue では default では **採否を確定しない**。design フェーズで実測 or
   既知の reference（`reactivecircus/android-emulator-runner` README 推奨）を
   踏まえて決定する。

3. **API 34 のみで十分か**:
   `targetSdkVersion` を将来 35（Android 15）に上げた場合、emulator も 35 へ
   引き上げる必要が生じる可能性がある。本 Issue では API 34 単独で固定する
   (Requirement 1.4) が、将来的に matrix 化（34 / 35 並列実行）する想定がある
   かを明確にしておく必要がある。
   - 当面（PassKey Phase 1〜2 完了まで）は **API 34 単独** で十分という前提で
     本要件を書いているが、umbrella #89 の後続 Issue で `compileSdk` / `targetSdk`
     を 35 に引き上げる別 Issue が起票されたとき、本 workflow を matrix 化
     するか、API level 引き上げのみで対応するかは別途判断する。
   - 本 Issue では matrix 化は Out of Scope。

## 関連 Issue / PR

- **Parent (umbrella)**: #89 (feat(passkey): Android Credential Manager 経由の
  PassKey プロバイダ対応)
- **Refs**: #90 (Phase 1: `CredentialProviderService` Manifest 登録 + 最小骨組み。
  本 Issue が解除しようとする `@Ignore` placeholder の追加元)
- **Refs**: #93 (#90 設計 PR / design.md §9.2 確認事項 B / §9.4 で本 Issue
  切り出しを決定)
- **後続予定**:
  - #89 分割案 3: 登録セレモニー（`onBeginCreateCredentialRequest` 実体実装。
    本 Issue 完了後に `KeyNestCredentialProviderServiceInstrumentationTest.serviceBinding_returnsEmptyCreateResponse`
    の `@Ignore` を解除する想定）
  - #89 分割案 4: 認証セレモニー（`onBeginGetCredentialRequest` 実体実装。
    同 `serviceBinding_returnsEmptyGetResponse` の `@Ignore` を解除する想定）
- **参考**:
  - `reactivecircus/android-emulator-runner`（https://github.com/ReactiveCircus/android-emulator-runner）
  - GitHub Actions on Android: https://docs.github.com/en/actions

## 用語集

- **emulator job**: GitHub Actions 上で Android emulator (AVD) を起動し
  `connectedDebugAndroidTest` を実行する job。本 Issue で新設。
- **`reactivecircus/android-emulator-runner`**: GitHub Actions 上で AVD を立ち
  上げて Gradle task を実行する third-party action。Android CI で広く使われる
  リファレンス実装。
- **`connectedDebugAndroidTest`**: AGP 標準の Gradle task。接続中（emulator /
  実機）の端末に debug APK と test APK を install し、`androidTest/` 配下の
  instrumentation test を実行する。
- **AVD snapshot**: AVD の起動済み状態を保存したスナップショット。GitHub
  Actions cache に保存しておくと 2 回目以降の emulator boot が短縮できる。
- **draft PR**: GitHub の "draft" 状態にある Pull Request。CI を回したくない
  WIP 状態を示すフラグ。
- **`@Ignore` placeholder**: #90 で追加された
  `KeyNestCredentialProviderServiceInstrumentationTest` の 3 メソッド。
  `@Ignore("CI に API 34 emulator が組み込まれるまで手動実行。#94 で gating
  解除。")` 付き。本 Issue 完了後に各機能 Issue で順次解除される想定。
