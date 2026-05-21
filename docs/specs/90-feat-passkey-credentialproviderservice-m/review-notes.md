# Review Notes — Issue #90 round 2

- Reviewer: claude (general-purpose subagent)
- Branch: claude/issue-90-impl-feat-passkey-credentialproviderservice-m
- HEAD: bd67ca676ffa369579b503da12f855d4a199428e
- Base: develop
- Round: 2 / 2
- Date: 2026-05-21

## Summary

Round 2 で Developer は orchestrator 判断のもと `androidx.credentials:1.3.0` を採用し
（design.md / tasks.md の確定値 `1.5.0` から逸脱する点は impl-notes に明示）、
T-01〜T-05 をすべて実装した。差分は 10 ファイル / +760 行 / -1 行で、
`gradle/libs.versions.toml` への version+library 追加、`app/build.gradle.kts` への
`implementation` 行追加、`KeyNestCredentialProviderService.kt` 本体、
`res/xml/credential_provider.xml`、`AndroidManifest.xml` への `<service>` ブロックと
`xmlns:tools` 追加、Robolectric unit test 3 本（Service / Manifest / xml）、
instrumentation test placeholder（`@Ignore` 付き 3 メソッド）が揃っている。
impl-notes によれば `./gradlew :app:assembleDebug` PASS、新規 unit test 13 件 PASS、
既存 lint / `AppInfoProviderTest` の pre-existing failure は本 Issue とは無関係であることが
明示されている。

round 1 で reject 理由となっていた「Service / xml / Manifest / 依存追加すべて未着手」
「unit test 0 件」の両ブロッカーは round 2 で解消されており、requirements.md の
AC 1.x / 2.x / 3.x / 4.x / 6.x は実装ファイルとテストファイルにそれぞれ対応物を持つ。
バージョン値 `1.3.0` は design.md §4.5 / §9.1-1 の確定値 `1.5.0` と乖離しているが、
これは impl-notes に「orchestrator 判断による deviation」として明記されており、かつ
requirements.md の req 4.1 自体は「安定版」を要求するに留まる（具体バージョン値は
要求していない）。design.md の改訂が必要なドキュメント不整合は残るが、これは reviewer
の判定軸（AC / missing test / boundary）外であり、人間レビュアが design 側で吸収する
事項として impl-notes に明示的に保留されている。

CONTRIBUTING.md（INTERNET 禁止 / Kotlin official style / テスト必須）への抵触なし。
新規 `<service>` は `<service android:permission>` であり `<uses-permission>` 追加では
ないため、既存 `InternetPermissionAbsenceTest` の制約も維持されている。

## Diff overview

`git diff --stat develop..HEAD`:

```
 app/build.gradle.kts                               |   1 +
 ...CredentialProviderServiceInstrumentationTest.kt |  51 ++++
 app/src/main/AndroidManifest.xml                   |  25 +-
 .../KeyNestCredentialProviderService.kt            |  68 ++++++
 app/src/main/res/xml/credential_provider.xml       |  16 ++
 .../CredentialProviderXmlTest.kt                   | 118 +++++++++
 .../KeyNestCredentialProviderServiceTest.kt        | 115 +++++++++
 .../CredentialProviderServiceManifestTest.kt       |  99 ++++++++
 .../impl-notes.md                                  | 266 +++++++++++++++++++++
 gradle/libs.versions.toml                          |   2 +
 10 files changed, 760 insertions(+), 1 deletion(-)
```

`git log --oneline develop..HEAD`:

```
bd67ca6 docs(impl-notes): record Issue #90 round 2 implementation outcomes
da15ae9 test(passkey): add instrumentation placeholder for KeyNestCredentialProviderService #90
c5961d3 feat(passkey): register CredentialProviderService in AndroidManifest for #90
6bb963a feat(passkey): add credential_provider.xml capability declaration for #90
8f4a4d5 feat(passkey): add KeyNestCredentialProviderService skeleton for #90
907054a feat(passkey): add androidx.credentials 1.3.0 dependency for #90
56fb0bf docs(impl-notes): record Issue #90 T-01 blocker on androidx.credentials 1.5.0
```

注目した変更ファイル:

- `gradle/libs.versions.toml`: `credentials = "1.3.0"` と `androidx-credentials` lib を追加。
- `app/build.gradle.kts`: `implementation(libs.androidx.credentials)` を 1 行追加。
- `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/KeyNestCredentialProviderService.kt`: 新規。`CredentialProviderService` を継承、`@RequiresApi(UPSIDE_DOWN_CAKE)` 付与、3 callback が `BeginCreateCredentialResponse()` / `BeginGetCredentialResponse.Builder().build()` / `callback.onResult(null)` で空応答。
- `app/src/main/res/xml/credential_provider.xml`: 新規。`<credential-provider>` ルート + `<capabilities><capability android:name="androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL" /></capabilities>`。`TYPE_PASSWORD_CREDENTIAL` の宣言なし、discoverable 限定属性なし。
- `app/src/main/AndroidManifest.xml`: ルートに `xmlns:tools` 追加、`<application>` 末尾に `<service>` ブロック追加（`android:name=".credentialprovider.KeyNestCredentialProviderService"`、`exported="true"`、`label="@string/app_name"`、`permission="BIND_CREDENTIAL_PROVIDER_SERVICE"`、`tools:targetApi="34"`、intent-filter action `android.service.credentials.CredentialProviderService`、meta-data `android.credentials.provider` → `@xml/credential_provider`）。既存 service / activity 宣言は無変更。
- `app/src/test/.../credentialprovider/KeyNestCredentialProviderServiceTest.kt`: Robolectric `@Config(sdk=[34])` + `mockk` で 4 ケース（create / get / clear / no-throw）。
- `app/src/test/.../credentialprovider/CredentialProviderXmlTest.kt`: Robolectric `@Config(sdk=[33])` で ルート要素 / capability 名 / `TYPE_PASSWORD_CREDENTIAL` 非含有 / 属性ホワイトリストの 4 ケース。
- `app/src/test/.../manifest/CredentialProviderServiceManifestTest.kt`: `@Config(sdk=[33])` で `PackageManager.getPackageInfo` 経由 merged manifest を読み、name / permission / exported / intent-filter / meta-data resource id の 5 ケース。
- `app/src/androidTest/.../credentialprovider/KeyNestCredentialProviderServiceInstrumentationTest.kt`: `@SdkSuppress(34)` + 3 メソッドすべて `@Ignore` 付き placeholder。
- `docs/specs/.../impl-notes.md`: Round 2 の実装結果と `1.3.0` 採用判断を追記。

CLAUDE.md はリポジトリルートに存在しないため CONTRIBUTING.md で代用。

## AC coverage

| AC ID | 内容（要約） | 判定 | 根拠 / 該当ファイル |
|-------|---------------|------|---------------------|
| 1.1   | `<service>` で `KeyNestCredentialProviderService` を宣言 | カバー | `AndroidManifest.xml` 新 `<service android:name=".credentialprovider.KeyNestCredentialProviderService">` |
| 1.2   | `BIND_CREDENTIAL_PROVIDER_SERVICE` permission | カバー | 同上 `android:permission="android.permission.BIND_CREDENTIAL_PROVIDER_SERVICE"` |
| 1.3   | `android:exported="true"` | カバー | 同上 |
| 1.4   | `<intent-filter>` action `android.service.credentials.CredentialProviderService` | カバー | 同上 intent-filter |
| 1.5   | `<meta-data android:name="android.credentials.provider" android:resource="@xml/credential_provider" />` | カバー | 同上 meta-data |
| 1.6   | `tools:targetApi="34"` | カバー | 同上 + ルートに `xmlns:tools` 追加 |
| 2.1   | `res/xml/credential_provider.xml` 新規 / `<credential-provider>` ルート | カバー | `app/src/main/res/xml/credential_provider.xml` |
| 2.2   | `TYPE_PUBLIC_KEY_CREDENTIAL` 1 件以上宣言 | カバー | 同上 `<capability android:name="androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL" />` |
| 2.3   | `TYPE_PASSWORD_CREDENTIAL` を宣言しない | カバー | 同上（実宣言は 1 件のみ） + `CredentialProviderXmlTest.doesNotDeclare_passwordCredential_capability` で明示テスト |
| 2.4   | discoverable/non-discoverable 両対応（片限定属性なし）| カバー | xml に discoverable-only 系属性なし + `CredentialProviderXmlTest.rootAndCapability_haveNoDiscoverabilityRestrictingAttributes` で属性 allowlist テスト |
| 3.1   | Service が `…/credentialprovider/` 配下に新規作成、`CredentialProviderService` 継承 | カバー | `KeyNestCredentialProviderService.kt` |
| 3.2   | `onBeginCreateCredentialRequest` でエントリ 0 件 `BeginCreateCredentialResponse` を `callback.onResult` | カバー | `KeyNestCredentialProviderService.onBeginCreateCredentialRequest` |
| 3.3   | `onBeginGetCredentialRequest` でエントリ 0 件 `BeginGetCredentialResponse` を `callback.onResult` | カバー | `KeyNestCredentialProviderService.onBeginGetCredentialRequest` |
| 3.4   | `onClearCredentialStateRequest` で `callback.onResult(null)` | カバー | `KeyNestCredentialProviderService.onClearCredentialStateRequest` |
| 3.5   | callback で例外をスローしない / `onError` 経路を使わない | カバー | 実装上いずれも `onResult` 一発呼び出しのみ + `KeyNestCredentialProviderServiceTest.allCallbacks_doNotThrow_andDoNotInvokeOnError` |
| 3.6   | API 34 未満での防御層（`@RequiresApi(34)` + Manifest gate）| カバー | class に `@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)`。design §6.2 で runtime SDK_INT ガードは置かない方針が確定済みで、本実装はこれに整合 |
| 4.1   | version catalog に `androidx.credentials:credentials` 安定版を追加 | カバー | `gradle/libs.versions.toml` に `credentials = "1.3.0"` と `androidx-credentials` lib。要件は「安定版」のみで具体バージョン値は要求していない。`1.3.0` は Google Maven 安定版系統に属する |
| 4.2   | `app/build.gradle.kts` で `implementation` 参照 | カバー | `implementation(libs.androidx.credentials)` |
| 4.3   | `credentials-play-services-auth` を追加しない | カバー | 追加されていない（grep で不在を確認可能） |
| 4.4   | `./gradlew :app:assembleDebug` 成功、Manifest merger 警告なし | カバー（impl-notes ログ依拠）| impl-notes に `BUILD SUCCESSFUL in 31s` / `BUILD SUCCESSFUL in 8s` の記録。本 reviewer は build 実行していないが、impl-notes 記録と差分内容の整合性は確認した |
| 5.1   | OS 設定画面で KeyNest 表示（手動）| 未カバー（手動委譲）| 自動テスト不能。impl-notes で人間レビュアへ委譲明記。Manifest / xml の構成自体は req 1.x / 2.x で機械的にカバーされており、5.1 達成の必要条件は満たしている |
| 5.2   | KeyNest 選択でクラッシュしない（手動）| 未カバー（手動委譲）| 同上 |
| 5.3   | API 33 以下で既存機能非破壊 | カバー | Manifest 追加は新規 `<service>` のみ、既存 service / activity / `<uses-permission>` は無変更。`InternetPermissionAbsenceTest` / `OnBackInvokedCallbackEnabledTest` 等への影響なし（impl-notes に既存テスト全 pass の旨記録、`AppInfoProviderTest` の 1 件 fail は develop branch 由来の pre-existing） |
| 6.1   | unit test: `onBeginCreateCredentialRequest` 空応答検証 | カバー | `KeyNestCredentialProviderServiceTest.onBeginCreateCredentialRequest_invokesOnResult_withEmptyResponse` |
| 6.2   | unit test: `onBeginGetCredentialRequest` 空応答検証 | カバー | `KeyNestCredentialProviderServiceTest.onBeginGetCredentialRequest_invokesOnResult_withEmptyResponse` |
| 6.3   | unit test: `onClearCredentialStateRequest` 正常終了 | カバー | `KeyNestCredentialProviderServiceTest.onClearCredentialStateRequest_invokesOnResult_withNull` |
| 6.4   | Manifest 検証テスト（permission / intent-filter / meta-data / exported）| カバー | `CredentialProviderServiceManifestTest`（5 ケース） |
| 6.5   | `credential_provider.xml` 検証テスト（`TYPE_PUBLIC_KEY_CREDENTIAL` 含有）| カバー | `CredentialProviderXmlTest`（4 ケース） |
| 6.6   | 既存テスト引き続き pass | カバー（pre-existing failure 除く）| impl-notes に「新規 13 件 pass / 既存 666 件 pass / 1 件 `AppInfoProviderTest` は develop branch 由来の pre-existing failure」と記録 |

カバー: 27 / 29。
手動検証（5.1 / 5.2）のみ未自動化だが、これは仕様上 reviewer 側でも自動化不能で、
impl-notes に明示的に人間レビュアへ委譲されている。round 1 で未カバーだった
全 AC が round 2 で解消した形。

## Boundary check

tasks.md の各タスクの「変更ファイル」「完了条件」境界に対し、

- T-01: `gradle/libs.versions.toml` と `app/build.gradle.kts` のみ touch。要求どおり 3 行追加で済んでいる（version + lib + implementation 1 行）。`credentials-play-services-auth` は追加されていない（req 4.3）。既存 `androidx.*` 依存のバージョンも触っていない（NFR 2.3）。
- T-02: `app/src/main/java/.../credentialprovider/KeyNestCredentialProviderService.kt` および対応 unit test の 2 ファイル新規のみ。design §4.1 のシグネチャ / KDoc コメントと完全一致。
- T-03: `app/src/main/res/xml/credential_provider.xml` および `CredentialProviderXmlTest.kt` の 2 ファイル新規のみ。
- T-04: `AndroidManifest.xml` への追記（`xmlns:tools` + 1 つの `<service>` ブロック）と `CredentialProviderServiceManifestTest.kt` の 1 ファイル新規。既存 `KeyNestAutofillService` / `AutofillUnlockActivity` / `CredentialListActivity` / `SettingsActivity` 等の宣言には**一切手が入っていない**（diff 確認済み）。
- T-05: `app/src/androidTest/.../credentialprovider/KeyNestCredentialProviderServiceInstrumentationTest.kt` の 1 ファイル新規。3 メソッドすべて `@Ignore` 付き placeholder で本体空。タスク要求どおり「実装ではなく placeholder 配置」のみ。

scope 外への侵入なし:

- PassKey 生成 / 保管モデル / Room migration への変更なし（umbrella #89 分割案 2）。
- 登録 / 認証セレモニーの実体実装なし（分割案 3 / 4）。
- 一覧 UI / 設定画面 / docs への変更なし（分割案 5 / 7 / 8）。
- `AAGUID` 定数の本 Issue 内定義なし（design §9.1-6 / §9.2 に整合）。
- `INTERNET` / `BIND_ACCESSIBILITY_SERVICE` / DeviceOwner 系 permission の追加なし（NFR 1.1 / 1.2）。
- `minSdk` / `targetSdk` / `compileSdk` / `applicationId` / `namespace` は無変更（NFR 2.2）。

唯一の design 不整合は **`androidx.credentials` のバージョン `1.3.0` vs design §4.5 確定の `1.5.0`** だが、これは:
- requirements.md req 4.1 は「安定版」要求のみで具体値は指定していない（AC レベルでは要件充足）。
- impl-notes.md に「orchestrator 判断による deviation」として明示記録 + design 改訂の要否を人間レビュアへ申し送り。
- design.md 本体を Developer が書き換えていない（Developer 制約遵守）。

したがって「reviewer の境界判定軸（tasks.md `_Boundary:_` / scope 外侵入）」では逸脱と見なさず、design / tasks のドキュメント整備事項として人間レビュアに委ねる扱いとする。

結論: boundary 逸脱は **無し**。

## Test coverage

req 6.1〜6.6 に対する充足:

- **req 6.1 / 6.2 / 6.3** — `KeyNestCredentialProviderServiceTest`（Robolectric `@Config(sdk=[34])` + `mockk`）が 4 メソッドで網羅。`OutcomeReceiver.onResult` の captor で「エントリ 0 件」を実値検証（`createEntries.isEmpty()` / `credentialEntries.isEmpty()` / `remoteEntry` が null / `authenticationActions.isEmpty()` 等）し、`onError` が 0 回であることも検証。`onClearCredentialStateRequest` は `onResult(null)` を `verify` で明示。
- **req 6.4** — `CredentialProviderServiceManifestTest`（Robolectric `@Config(sdk=[33])`）が `PackageManager.getPackageInfo` 経由で merged manifest を読み、(a) name / (b) permission / (c) exported / (d) intent-filter action / (e) meta-data resource id を 5 メソッドで個別検証。既存 `InternetPermissionAbsenceTest` と同パターンで実績ある経路。
- **req 6.5** — `CredentialProviderXmlTest`（Robolectric `@Config(sdk=[33])`）が `Resources.getXml(R.xml.credential_provider)` 経由でルート要素 / capability 名 / 属性 allowlist を 4 メソッドで検証。req 2.3（password 非宣言）と req 2.4（discoverable 限定属性なし）の両方を mechanical にカバー。
- **req 6.6** — impl-notes で `./gradlew :app:testDebugUnitTest` 全 680 件のうち 679 pass / 1 fail（`AppInfoProviderTest` は develop branch 由来の pre-existing）と記録。Manifest 変更 / 依存追加による既存テスト破壊は発生していない。

`@Ignore` の扱い:
- instrumentation test (T-05) は 3 メソッドすべて `@Ignore` 付きで body 空。これは tasks.md T-05 が明示的に要求している placeholder 配置であり、unit test 側（T-02 の Robolectric）が req 6.1 / 6.2 / 6.3 を主検証している（design §7.2 / requirements 確認事項 3）。`@Ignore` は #94（CI に API 34 emulator 導入）完了後に外す前提が impl-notes / コメントに記録されている。

missing test: **無し**。

## Findings

### Blocking（reject 理由になり得るもののみ）

- なし。

### Non-blocking（参考コメント。reject 理由にはしない）

- [N1] `androidx.credentials` 採用バージョンが `1.3.0` であるのに対し design.md §4.5 / §9.1-1 と tasks.md T-01 は依然 `1.5.0` を「確定値」として記載している。impl-notes に deviation が明記されており Developer 側の制約（design / tasks を書き換えない）には合致するが、後続 Issue の Developer / Reviewer が design を素直に読むと `1.5.0` に戻そうとする可能性がある。design サブエージェント or 人間レビュアにて `1.3.0` への改訂、または `1.3.0 を採用済み` の追記が望ましい。reviewer の判定軸（AC / missing test / boundary）外。
- [N2] 既存 `AppInfoProviderTest` の pre-existing failure（`versionName` 期待値ズレ）は本 Issue とは独立だが、CI 緑が前提のレビューフローではノイズになる。本 Issue では req 6.6 が「本 Issue 変更による既存テスト破壊なし」を要求しているのみであり、これは満たされているため reject 理由にはしない。別 Issue 案件。
- [N3] `./gradlew :app:lintDebug` の 151 件 pre-existing error も同様に scope 外。新規ファイル（`KeyNestCredentialProviderService.kt` / `credential_provider.xml` / 新規 `<service>` ブロック）に起因する lint finding は 0 件と impl-notes に記録されており、req 4.4 の Manifest merger 警告も発生していない。
- [N4] 手動検証（req 5.1 / 5.2 / 5.3）は impl-notes で明示的に人間レビュアへ委譲されている。req 5.3 については Manifest 変更が `<service>` 追加のみで既存 `<uses-permission>` / `<service>` / `<activity>` を一切触っていないため、Robolectric 経由の `InternetPermissionAbsenceTest` / `OnBackInvokedCallbackEnabledTest` 等が引き続き pass している事実から間接的に強い裏付けがある。
- [N5] design §9.1-7 で確定の `android:label="@string/app_name"` 流用、§9.1-4 で確定の `android.credentials.provider` meta-data name、§4.3 確定の xml 構造、§4.1 のクラスシグネチャ、いずれも diff 内容と完全一致しており design への忠実度は高い。

## Verdict

round 1 で reject 理由となっていた 2 つのブロッカー（B1: AC 1.x〜4.x / 5.x / 6.x の広範未カバー、B2: 要求された unit test 5 ファイル全て不在）は round 2 で完全に解消された。AC matrix 上、自動化可能な 27 項目はすべてカバーされており、残る 2 項目（5.1 / 5.2 の手動検証）は仕様上自動化不能で impl-notes により人間レビュアへ明示委譲されている。

実装ファイル `KeyNestCredentialProviderService.kt` は design §4.1 のシグネチャ / KDoc / `@RequiresApi(34)` 方針と完全一致し、3 callback は要求どおり `onResult` 一発で空応答を返す（`onError` 経路なし、例外なし）。Manifest 追記は design §4.2 の確定形と一字一句一致し、既存 service / activity 宣言は無変更で NFR 2.1 を満たす。`credential_provider.xml` は design §4.3 の確定形と一致し、`TYPE_PUBLIC_KEY_CREDENTIAL` のみを宣言、password type および discoverable 限定属性なしで req 2.3 / 2.4 を満たす。

テスト面では `KeyNestCredentialProviderServiceTest`（4 ケース）/ `CredentialProviderServiceManifestTest`（5 ケース）/ `CredentialProviderXmlTest`（4 ケース）の合計 13 ケースが新規追加され、req 6.1〜6.5 を mechanical に網羅。instrumentation placeholder（T-05）は tasks 要求どおり `@Ignore` 付きで配置され、CI 緑を保ちつつ #94 完了後の即時起動に備えている。

`androidx.credentials = 1.3.0` 採用は design.md §4.5 / §9.1-1 / tasks.md T-01 の確定値 `1.5.0` から逸脱しているが、(i) requirements.md req 4.1 自体は「安定版」要求のみで具体値を指定していないため AC レベルでは充足、(ii) Developer は design / tasks を書き換えず impl-notes に deviation を明示しており Developer 制約を遵守、(iii) deviation は orchestrator 判断として impl-notes に文書化されている、という 3 点から reviewer の判定軸（AC / missing test / boundary）には抵触しない。design.md 側の改訂は人間レビュアに委ねる non-blocking 事項として N1 に記録した。

boundary 逸脱なし。後続 Issue scope への侵入なし。AC 未カバーなし。missing test なし。判定基準上 reject 理由が存在しないため、approve とする。

RESULT: approve
