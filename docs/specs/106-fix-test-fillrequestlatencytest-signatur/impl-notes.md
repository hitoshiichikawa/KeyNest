# Implementation Notes — Issue #106

## 1. 採用方針

**方針 (B): `mockk<PackageSignatureResolver>()` を anonymous-object stub に置換**

### 判断根拠

| 方針 | 評価 |
|---|---|
| (A) mockk bump | mockk 1.13.13 / 1.14.x のリリースノートを見ても Android 14 / API 34 emulator 上の Byte Buddy / objenesis 問題に明確に対応した記述は確認できず、bump しても効く保証がない。bump は `[versions] mockk` 経由で `testImplementation(libs.mockk)` 側にも強制的に波及し、unit test 全体（680 件）の回帰リスクが発生する（Issue 本文「unit test 側への ripple は最小に」に反する）。 |
| (B) anonymous-object stub | mockk-android のクラス生成経路を完全に回避し、CI 上で確実に動く。androidTest 2 ファイル + 製品コード 1 ファイル（`open` 修飾子追加のみ）の小さな変更で済む。`testImplementation(libs.mockk)` には ripple ゼロ。 |
| (C) runner 構成 | API 34 emulator の hidden API restriction を bypass できる保証がなく、CI フィードバックループが遅い。最終手段。 |

(B) を採用。製品コード変更は `final class` → `open class` + 関数 1 個に `open`
を付けるのみ（DI 配線・interface 抽出なし）。requirements.md 209-247 行の指針
通り「最小スコープ」を選択した。

## 2. 変更ファイル一覧

| ファイル | 変更概要 |
|---|---|
| `app/src/main/java/io/github/hitoshiichikawa/keynest/util/PackageSignatureResolver.kt` | `class` → `open class`、`fun resolveSha256` → `open fun resolveSha256`。anonymous-object で override 可能にする。WHY コメント 1 行追加。 |
| `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/perf/FillRequestLatencyTest.kt` | `mockk<PackageSignatureResolver>()` を anonymous-object stub に置換。`io.mockk.*` import 削除。 |
| `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/security/SignatureMismatchTest.kt` | 2 箇所の `mockk<PackageSignatureResolver>()` を共通 helper `stubResolver(hash)` に集約 + anonymous-object stub 化。`InstrumentationRegistry` import 追加、`io.mockk.*` import 削除。 |

合計 3 ファイル変更。dependency（`gradle/libs.versions.toml`, `app/build.gradle.kts`）は変更なし。

### 設計上のポイント

- `PackageSignatureResolver(pm: PackageManager, sdkVersion: Int = Build.VERSION.SDK_INT)`
  の primary constructor が `PackageManager` を要求するため、androidTest 側で
  `InstrumentationRegistry.getInstrumentation().targetContext.packageManager` を渡す。
  stub 側で `pm` は実際には触らない（`resolveSha256` を override で完全置換）。
- `SignatureMismatchTest` の helper は `if (packageName == "com.example.target") hash else null`
  という分岐にして、既存 mockk stub の挙動（`every { resolveSha256("com.example.target") } returns matching`
  — 他 packageName は呼ばれた場合 mockk が throw する）と等価にした。
- 既存テストの method 名 / assertion / setup ロジック / latency 数値 (300/600) は不変。
- `testImplementation(libs.mockk)` は維持（unit test 側の他 mockk 利用はそのまま）。
- `androidTestImplementation(libs.mockk.android)` は要件通り依存自体は維持（他 androidTest 内の使用箇所への影響回避のため。本 PR では使用箇所がなくなったが将来追加分のために残す）。

## 3. ローカル検証結果

JAVA_HOME / ANDROID_HOME を設定して以下を実行:

| # | コマンド | 結果 |
|---|---|---|
| 1 | `./gradlew :app:compileDebugAndroidTestKotlin` | **BUILD SUCCESSFUL** in 20s |
| 2 | `./gradlew :app:testDebugUnitTest` | 680 tests / 1 failed (`AppInfoProviderTest.get_returnsVersionNameFromBuildGradle`) — **本 PR の変更に起因しない既存の失敗**。git stash で本 PR 変更を退避した状態でも同じ 1 件が失敗することを確認済み。`PackageSignatureResolverTest` と `ResolveAutofillCandidatesUseCaseTest` は両方 pass。 |
| 3 | `./gradlew :app:assembleDebug` | **BUILD SUCCESSFUL** in 8s |

`testDebugUnitTest` の 1 件失敗は `versionName` を `"0.1.0"` で assert しているが
`app/build.gradle.kts` の現値が `"1.0.0"` であるという無関係なテストデータ齟齬。
別 Issue として follow-up を検討する余地はあるが、本 PR スコープ外。

## 4. CI で必ず確認が必要な事項

**AC-1 / AC-2 の最終検証はローカル emulator なしの開発環境では実施不可。**
CI workflow `connectedDebugAndroidTest (API 34)` (`reactivecircus/android-emulator-runner@v2`)
で以下が pass することを確認すること:

- AC-1: `FillRequestLatencyTest.resolveCandidates_medianLatency_within300ms_for100Credentials`
  が `ExceptionInInitializerError` を出さずに完了し、median <= 300ms / p95 <= 600ms を満たす。
- AC-2: `SignatureMismatchTest` の test runner instantiation 成功 →
  `candidateFilter_dropsMismatched_andRetainsMatched` /
  `candidateFilter_returnsEmpty_whenAllAreMismatched` 両方が pass する。

## 5. 確認事項（requirements.md「確認事項」への回答）

1. **(A) mockk bump の最低バージョン基準** → 採用せず。mockk リリースノートに
   API 34 emulator 上の Byte Buddy 問題への明示的 fix が見当たらないため、bump
   による解決の確度が低い。
2. **(B) 採用時の製品コード変更スコープ** → `open class` 化 + `open fun` 化のみ。
   interface 抽出は DI 配線書き換えを伴うため不採用。`PackageSignatureResolver`
   を継承する具体クラスは現状なく、`open` 化による副作用は実質ない。
3. **(B) で `packageName` 引数で分岐すべきか** → `SignatureMismatchTest` は引数で
   分岐（既存 mockk 動作と等価）、`FillRequestLatencyTest` は単一 packageName
   想定なので引数無視で固定値を返す（latency 計測でブランチコストを増やさない
   ため）。
4. **(C) を試す価値** → 試さず。フィードバックループが遅すぎる。
5. **mockk-android 1.13.12 の Android 14 上での既知 issue** → 詳細根因の特定は
   行わず、(B) の anonymous-object stub で迂回する方針を選択（迂回コストが
   小さいため）。
6. **`FillRequestLatencyTest.kt:111` の合成行番号** → コルーチン bytecode の
   合成行と解釈。実 fix 対象は `mockk<PackageSignatureResolver>()` 周辺と判断し、
   そこを置換した。

## 6. Future work / 関連 follow-up Issue 候補

- `AppInfoProviderTest.get_returnsVersionNameFromBuildGradle` の assertion
  値（"0.1.0" / 1L）が `app/build.gradle.kts` の現値（"1.0.0" / 1）と乖離。
  別 Issue で fixture 更新を検討する余地あり。
- `androidTestImplementation(libs.mockk.android)` 依存は本 PR では使用箇所が
  なくなった。将来再度使うことが想定されない場合は dependency を外す
  follow-up が考えられるが、Issue 本文「依存自体は外さない」の指針に従い本 PR
  では維持。
