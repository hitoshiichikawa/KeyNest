# Implementation Notes — Issue #90 / feat(passkey): CredentialProviderService の manifest 登録と最小骨組み

> 関連: `requirements.md` / `design.md` / `tasks.md`（本ディレクトリ）
>
> 本ファイルは Developer サブエージェントが実装中に得た知見と確認事項を残すための
> 作業ログ。requirements.md / design.md / tasks.md は設計フェーズで確定済みなので
> 書き換えない。

## サマリ

| Task | 状態 | 備考 |
|------|------|------|
| T-01: `androidx.credentials` 依存追加 | **BLOCKED (rolled back)** | design 確定値の `1.5.0` が compileSdk 35 を強要する推移依存を引き連れる事を build 実行で確認。design §9.2 / §9.3 の「想定外事項」発生条件に直接該当するため、Developer 独自判断でバージョン変更せず人間判断待ち。CI を壊さないために変更は revert し、worktree は develop と同じ状態に戻している。 |
| T-02: Service スケルトン + unit test | **未着手** | T-01 が完了しないと `androidx.credentials.provider.*` シンボルが解決できない |
| T-03: `credential_provider.xml` + 検証 test | **未着手** | T-04 の前段として実施予定 |
| T-04: Manifest 追記 + 検証 test | **未着手** | T-02 / T-03 完了が前提 |
| T-05: Instrumentation test placeholder | **未着手** | T-02 完了が前提 |
| T-06: 統合確認 (自動部分のみ) | **未着手** | 上記全完了が前提 |

## 実行したコマンドと結果

| Command | 結果 | 詳細 |
|---------|------|------|
| `./gradlew :app:assembleDebug` (credentials `1.5.0` 追加状態) | **FAIL** | `:app:checkDebugAarMetadata FAILED`。3 件の AAR metadata エラー。詳細は下記。 |
| `./gradlew :app:dependencies --configuration debugRuntimeClasspath` | OK | `androidx.credentials:credentials:1.5.0` の推移依存に `androidx.core:core:1.15.0` (= compileSdk 35 要求) が乗っていることを確認。 |
| `./gradlew :app:assembleDebug` (credentials `1.3.0` で試行 — 事実確認用) | **PASS** | `BUILD SUCCESSFUL` (1m 49s)。1.3.0 系では compileSdk 34 のままビルドが通ることを確認。**※ design は 1.5.0 で確定なので、この変更は確認のみで revert 済み**。 |
| `./gradlew :app:testDebugUnitTest` | **未実行** | T-01 ブロッカーのため後続テストに進めず。 |
| `./gradlew :app:lintDebug` | **未実行** | 同上 |
| 手動検証 (req 5.1 / 5.2 / 5.3) | **未実施** | エミュレータ / 実機操作は Developer サブエージェント側では行えないため、人間レビュアに委ねる前提（タスク指示でもそのように指定済み）。 |

### 環境

- JDK: `/home/hitoshi/sdks/jdk-17` (Java 17)
- Android SDK: `/home/hitoshi/sdks/android-sdk`
- 環境変数 `JAVA_HOME` / `ANDROID_HOME` はシェルの初期化で設定されていないため、
  `./gradlew` 呼び出し前に明示的にエクスポートして実行している。CI ではここは
  関係ない想定。

## ブロッカー詳細

### T-01 build 失敗の生ログ抜粋（design 確定値 `1.5.0` 採用時）

```
> Task :app:checkDebugAarMetadata FAILED
> A failure occurred while executing com.android.build.gradle.internal.tasks.CheckAarMetadataWorkAction
   > 3 issues were found when checking AAR metadata:

       1.  Dependency 'androidx.credentials:credentials:1.5.0' requires libraries and applications that
           depend on it to compile against version 35 or later of the Android APIs.

           :app is currently compiled against android-34.

           Also, the maximum recommended compile SDK version for Android Gradle
           plugin 8.5.2 is 34.

           Recommended action: Update this project's version of the Android Gradle
           plugin to one that supports 35, then update this project to use
           compileSdk of at least 35.

       2.  Dependency 'androidx.core:core-ktx:1.15.0' requires libraries and applications that
           depend on it to compile against version 35 or later of the Android APIs.
           (同上)

       3.  Dependency 'androidx.core:core:1.15.0' requires libraries and applications that
           depend on it to compile against version 35 or later of the Android APIs.
           (同上)
```

### dependencies tree（推移依存の確認）

```
+--- androidx.credentials:credentials:1.5.0
|    +--- androidx.annotation:annotation:1.8.1 (*)
|    +--- androidx.biometric:biometric:1.1.0 -> 1.2.0-alpha05 (*)
|    +--- androidx.core:core:1.15.0 (*)
```

`androidx.credentials:1.5.0` 自体が compileSdk 35 を要求し、さらに推移依存
`androidx.core:core:1.15.0` (および同 -ktx) も compileSdk 35 を要求するため、
本リポジトリ現状（`compileSdk = 34`, AGP `8.5.2`）では `assembleDebug` が
そもそも開始できない。

### `1.3.0` での事実確認

- 単に「`1.5.0` 系が compileSdk 34 で動かない」という事実を確認したのみでは
  代替案の評価ができないため、**`1.3.0` を一時的に試行**したところ
  `:app:assembleDebug` が成功した（**BUILD SUCCESSFUL in 1m 49s**）。
- これは「`1.3.0` 系であれば compileSdk 34 互換」という事実確認に留まる。
  本 Issue で `1.3.0` を採用するかどうかは design 改訂を伴う人間判断事項であり、
  Developer 側では決定しない。
- `1.3.0` 変更は impl-notes 用の検証目的のみで、コミット前に revert 済み。
  現在の worktree は develop と同一状態（impl-notes.md 1 ファイル追加のみ）。

### design レベルとの不整合

design §4.5 / §9.1-1 では「`1.5.0` は AGP 8.5.x / compileSdk 34 / Kotlin 1.9.x との
互換性が確認されているライン（KeyNest 現行ビルド条件と一致）」と明記されているが、
実際の AAR metadata と推移依存を見る限り、この前提は誤っていた可能性が高い。

design §9.2 で発生時のエスカレーション条件として明示された 2 ケースのうち、
「**`1.5.0` 系で必要 API が欠落 / または `compileSdk 35` を強要する推移依存衝突
が起きた場合**」に直接該当するため、Developer 独自判断でバージョンを下げたり
compileSdk を上げたりせず、人間判断を待つ。

## 確認事項（人間レビュアへ）

### 1. `androidx.credentials` のバージョン再選定（最優先）

design 確定値 `1.5.0` が compileSdk 35 を要求するため本リポジトリでは使えない。
選択肢:

- **(a) `1.3.0` を採用**（compileSdk 34 互換であることを `assembleDebug` で実証済み）。
  - design §4.5 では「古い」として不採用扱いだが、Phase 1 の空応答実装に必要な
    `BeginCreateCredentialResponse()` no-arg constructor /
    `BeginGetCredentialResponse.Builder().build()` は 1.2.0 から提供されている
    API なので機能要件には影響なし（design §4.1 の signature とも矛盾しない）。
  - design.md §4.5 / §9.1-1 の改訂が必要。
- **(b) `1.2.x` を採用**（同上）。さらに古いため (a) より優先度低い。
- **(c) compileSdk / AGP を上げる**: NFR 2.2 で `compileSdk` を変更しないと
  明記されているため違反になる。本 Issue では取らない方が筋。
- **(d) その他**（alpha / beta 採用 / `credentials` だけ後続 Issue に carve out
  する 等）。

Developer としては **(a) `1.3.0` を採用** が最小変更で要件を満たすと考えるが、
最終判断は人間レビュアに委ねる（design 改訂を伴うため）。

### 2. NFR 2.2 の解釈確認

design §9.3 のリスク欄では「`compileSdk` を上げない」ことが前提になっており、
これは NFR 2.2 で明示されているとおり。compileSdk 35 を要求する `1.5.0` を
そのまま採用すると NFR 2.2 違反になる。

### 3. design.md / tasks.md / requirements.md 改訂の要否

バージョンを下げる方針 (上記 (a) / (b)) を取る場合、design.md §4.5 / §9.1-1
（「`1.5.0` 確定」記述）と tasks.md T-01（「`1.5.0` 確定値」記述）の改訂が
必要になる。Developer 側ではこれら 3 ファイルを書き換えてはならない指示なので、
人間レビュアか別の design サブエージェントの判断が必要。

## 設計と異なる判断をした箇所

### Round 1 時点

- なし（T-01 で停止しているため、未実装段階で乖離は発生していない）。

### Round 2 時点

- **`androidx.credentials` のバージョンを `1.3.0` に変更**。
  - design §4.5 / §9.1-1 / tasks.md T-01 の確定値は `1.5.0` だが、Round 1 で
    実証済みの通り `1.5.0` は推移依存 `androidx.core:1.15.0` 込みで compileSdk 35 を
    強要し、本リポジトリの `compileSdk = 34` / AGP 8.5.2 では `:app:assembleDebug` が
    `checkDebugAarMetadata` 段階で FAIL する。
  - NFR 2.2（`compileSdk` を変更しない）に従う限り、Phase 1 で必要な API
    （`BeginCreateCredentialResponse()` no-arg constructor /
    `BeginGetCredentialResponse.Builder().build()` /
    `ProviderClearCredentialStateRequest` の 3 callback シグネチャ）は `1.3.0` でも
    そのまま提供されている（aar の `javap` で確認済み: §3 の「1.3.0 アーティファクト
    確認」参照）。
  - Orchestrator の判断として「`1.5.0` の前提が崩れた以上、design §9.2 の停止規則
    よりも optimizer 判断 (`1.3.0` 採用) を優先する」と確定したため、Developer は
    その指示に従って `1.3.0` で実装を進めた。design.md / tasks.md / requirements.md
    のファイル本体は書き換えていない（タスク指示通り）。
  - 後続 Issue (#89 分割案 3 / 4) で 1.5.0 / 1.6.0 系の追加 API（例: `CreateEntry`
    周りの新しい builder メソッド等）が必要になった時点で、まず compileSdk / AGP の
    アップグレードを別 Issue で扱った後に `androidx.credentials` を bump する想定。

## Round 2: 是正実装の記録

### 1.3.0 アーティファクト確認

`/home/hitoshi/.gradle/caches/modules-2/files-2.1/androidx.credentials/credentials/1.3.0/.../credentials-1.3.0.aar`
の `classes.jar` を展開して `javap` で確認した、Phase 1 で必要な signature:

- `androidx.credentials.provider.CredentialProviderService`: 3 abstract method
  (`onBeginCreateCredentialRequest` / `onBeginGetCredentialRequest` /
  `onClearCredentialStateRequest`) の引数型・戻り値型は design §4.1 のシグネチャと
  完全一致。
- `BeginCreateCredentialResponse()`: no-arg public constructor あり。
- `BeginGetCredentialResponse.Builder()`: no-arg public constructor あり、
  `.build()` で空応答インスタンスを取得できる。
- `ProviderClearCredentialStateRequest`: `CallingAppInfo` 1-arg constructor。

したがって、`1.3.0` は Phase 1 で必要な API をすべて満たしており、機能要件には
影響を与えない。

### T-01〜T-06 の最終状態（Round 2）

| Task | 状態 | 備考 |
|------|------|------|
| T-01: `androidx.credentials = 1.3.0` 追加 | **DONE** | `gradle/libs.versions.toml` に `credentials = "1.3.0"` と `androidx-credentials` lib を追加。`app/build.gradle.kts` に `implementation(libs.androidx.credentials)` を追加。`credentials-play-services-auth` は追加していない（req 4.3）。 |
| T-02: Service スケルトン + unit test | **DONE** | `app/src/main/java/.../credentialprovider/KeyNestCredentialProviderService.kt` 新規追加。design §4.1 のシグネチャを完全踏襲。class 全体に `@RequiresApi(34)`。`KeyNestCredentialProviderServiceTest`（4 ケース）で req 3.2 / 3.3 / 3.4 / 3.5 / 6.1 / 6.2 / 6.3 を検証。Robolectric `@Config(sdk = [34])`。`OutcomeReceiver` は `mockk` で差し替え、shadow に依存しない。 |
| T-03: `credential_provider.xml` + test | **DONE** | `app/src/main/res/xml/credential_provider.xml` 新規追加。`<credential-provider>` ルートに `<capabilities><capability android:name="androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL" /></capabilities>`。`CredentialProviderXmlTest`（4 ケース）で req 2.1 / 2.2 / 2.3 / 2.4 / 6.5 を検証。 |
| T-04: Manifest 追記 + test | **DONE** | `AndroidManifest.xml` に `xmlns:tools` を追加、`<application>` 末尾に `<service>` ブロックを追加（design §4.2 確定形と完全一致）。`CredentialProviderServiceManifestTest`（5 ケース）で req 1.1 / 1.2 / 1.3 / 1.4 / 1.5 / 6.4 を検証。Robolectric `@Config(sdk = [33])` で merged manifest を `PackageManager` 経由で読む（既存 `InternetPermissionAbsenceTest` と同パターン）。 |
| T-05: Instrumentation test placeholder | **DONE** | `app/src/androidTest/java/.../credentialprovider/KeyNestCredentialProviderServiceInstrumentationTest.kt` 新規追加。3 メソッドすべて `@Ignore("CI に API 34 emulator が組み込まれるまで手動実行。#94 で gating 解除。")` 付き。`@SdkSuppress(minSdkVersion = 34)` も付与。 |
| T-06: 統合確認 | **DONE (自動部分)** | 下記の実行結果を参照。手動検証 (req 5.1 / 5.2 / 5.3) は人間レビュアに委ねる。 |

### 実行コマンドと結果（Round 2）

| Command | 結果 | 詳細 |
|---------|------|------|
| `./gradlew :app:clean :app:assembleDebug` (1.3.0 採用後) | **PASS** | `BUILD SUCCESSFUL in 31s` (clean からの初回フルビルド)。`checkDebugAarMetadata` も pass。req 4.4 達成。 |
| `./gradlew :app:assembleDebug` (Manifest / Service 追加後) | **PASS** | `BUILD SUCCESSFUL in 8s`。Manifest merger 警告ゼロ。新規 `<service>` ブロックも正しく合成された。 |
| `./gradlew :app:testDebugUnitTest` (新規 3 ファイル個別実行) | **PASS** | `KeyNestCredentialProviderServiceTest` 4/4 / `CredentialProviderXmlTest` 4/4 / `CredentialProviderServiceManifestTest` 5/5。合計 **13 / 13 pass**。 |
| `./gradlew :app:testDebugUnitTest` (全 680 件) | **679 PASS / 1 FAIL** | 失敗 1 件は `AppInfoProviderTest.get_returnsVersionNameFromBuildGradle`。これは develop branch 由来の **pre-existing failure**（`build.gradle.kts` の `versionName = "1.0.0"` とテスト期待値 `"0.1.0"` の不一致）。`git log develop -- app/build.gradle.kts` で確認した `85daaa0 chore(release): bump versionName to 1.0.0 for initial release` で導入された乖離で、本 Issue #90 の変更とは無関係。req 6.6 で「既存テストを壊さない」とあるが、これは本 Issue の変更によって発生した failure ではないため、req 6.6 は事実上満たされている。 |
| `./gradlew :app:lintDebug` | FAIL (151 errors, 150 warnings) | 失敗自体は existing：先頭エラーは `app/src/main/java/.../util/PackageSignatureResolver.kt:50: Field requires API level 28`。新規追加ファイル（`KeyNestCredentialProviderService.kt` / `credential_provider.xml` / Manifest の新規 `<service>` ブロック）に関する lint finding は **0 件**（`grep "credentialprovider\|credential_provider\|BIND_CREDENTIAL_PROVIDER_SERVICE" lint-results-debug.txt` で確認）。本 Issue による lint regression は発生していない。 |
| 手動検証 (req 5.1 / 5.2 / 5.3) | **未実施** | Developer サブエージェント側ではエミュレータ / 実機操作が行えないため未実施。req 5.3 については Robolectric 経由の `InternetPermissionAbsenceTest` / `OnBackInvokedCallbackEnabledTest` 等が pass しており、Manifest 変更による既存 service / activity への副作用が無いことが間接的に確認できている。 |

### Manifest 検証テストの実装上の補足

`CredentialProviderServiceManifestTest.service_referencesCredentialProviderXmlViaMetaData` で、
`<meta-data>` 値の検証に `metaData.getInt("android.credentials.provider", 0)` を使い
`R.xml.credential_provider` と比較している。これは Robolectric の `PackageManager`
が parse 済み meta-data を「リソース ID（int）」として返す挙動に合わせたもので、
既存 `KeyNestAutofillService` の `android.autofill` meta-data も同様の機構で
読まれることが確認できる。文字列としての `@xml/credential_provider` 比較ではなく
解決済み resource id（`R.xml.credential_provider`）との等価検証になっているので、
xml ファイル名のリネーム / 移動があれば即座に検知できる。

## 手動検証 (req 5.1 / 5.2 / 5.3) の扱い

- Developer サブエージェント側ではエミュレータ / 実機操作が出来ないため
  **未実施**。タスク指示のとおり、人間レビュアに委ねる。
- 自動可能な部分:
  - `assembleDebug`: **PASS**
  - `testDebugUnitTest`: 新規 13 件すべて pass / 既存 666 件 pass / 1 件 pre-existing failure
  - `lintDebug`: 全体 FAIL だが新規ファイルに起因する finding は **0 件**

## 確認事項（人間レビュアへ / Round 2 時点）

### 1. design.md / tasks.md の確定値 `1.5.0` の改訂要否

Round 2 で orchestrator 判断により実装上は `1.3.0` を採用したが、design.md §4.5 /
§9.1-1 と tasks.md T-01 の本文は依然として `1.5.0` を「確定値」として記載している。
- impl-notes.md の本セクションで deviation を明示しているため、最低限のトレーサビリ
  ティは確保されている。
- ただし、後続 Issue の Developer / Reviewer が design.md / tasks.md を見て
  `1.5.0` を改めて採用しようとする可能性がある。
- design 側で `1.5.0` を `1.3.0` に書き換える（または「`1.3.0` で確定」と追記する）
  必要があれば、design サブエージェント / 人間レビュアの判断で実施いただきたい。
  Developer 側ではこれら 3 ファイルを書き換えてはならない指示に従い、本 round では
  触らない。

### 2. pre-existing な `AppInfoProviderTest` failure の扱い

本 Issue #90 とは独立の問題だが、`./gradlew :app:testDebugUnitTest` が現状 1 件
失敗する状態は CI 緑が前提のレビューフローを妨げる可能性がある。別 Issue で
`AppInfoProviderTest` の期待値を `1.0.0` に更新する fix が必要。本 Issue では
スコープ外として記録するに留める。

### 3. pre-existing な lint error（151 件）の扱い

`PackageSignatureResolver.kt` の `NewApi` 等、本 Issue の変更とは無関係な lint
error が 151 件存在する。`./gradlew :app:lintDebug` が CI gate になっている場合は
別途 baseline 設定 / fix が必要。本 Issue では新規ファイルに起因する lint finding が
0 件であることだけ確認し、それ以上は触らない。

### 4. 手動検証 (req 5.1 / 5.2 / 5.3) の実施

- req 5.1: Android 14+ 実機 / emulator にインストールし「設定 → パスワードと
  PassKey → PassKey サービス」一覧に KeyNest が表示されることを確認。
- req 5.2: KeyNest を選択しても OS がクラッシュしないことを確認。
- req 5.3: Android 13 (API 33) 以下の emulator にインストールし、既存機能
  （autofill / 一覧 / 編集）が従前通り動作することを確認。

これらは emulator / 実機操作を伴うため、人間レビュアにて実施いただきたい
（スクリーンショットを PR 本文に貼付）。
