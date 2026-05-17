# 実装ノート: Issue #50 OnBackInvokedCallback warning 解消

## 概要

`AndroidManifest.xml` の `<application>` 要素に
`android:enableOnBackInvokedCallback="true"` 属性を追加し、Android 13 (API 33) 以降の
端末で `OnBackInvokedCallback is not enabled for the application` warning を解消した。
predictive back gesture が OS から正式有効化された状態となる。

## 変更ファイル一覧

| 種別 | パス | 変更内容 |
| --- | --- | --- |
| Manifest | `app/src/main/AndroidManifest.xml` | `<application>` 要素に `android:enableOnBackInvokedCallback="true"` を 1 行追加 |
| Test (新規) | `app/src/test/java/com/example/keynest/manifest/OnBackInvokedCallbackEnabledTest.kt` | 上記属性が manifest に宣言されていることを機械的に検証 |

`<queries>` / `<service>` / 全 `<activity>` / 既存の `<application>` 属性
（`android:name` / `android:allowBackup` / `android:fullBackupContent` /
`android:dataExtractionRules` / `android:icon` / `android:roundIcon` / `android:label` /
`android:supportsRtl` / `android:theme`）は変更していない（Requirement 1.2 / 1.3）。

## onBackPressed override 調査結果（Requirement 2.5）

Developer 着手時点で以下の Grep を再実行した:

```
pattern: onBackPressed|BackPressedCallback|backPressedDispatcher|onBackInvoked
glob:    *.{kt,java}
result:  No matches found
```

`app/src/main`, `app/src/test`, `app/src/androidTest` 配下を含む全 Kotlin / Java
ファイル 0 件で hit せず、`onBackPressed()` を override している箇所は存在しない。
したがって Requirement 2.4（`OnBackPressedCallback` への移行）の作業対象は 0 件。

## ビルド・テスト結果

実行コマンド（環境変数 `JAVA_HOME=$HOME/sdks/jdk-17`,
`ANDROID_HOME=$HOME/sdks/android-sdk` を export した上で実行）:

| コマンド | 結果 | 備考 |
| --- | --- | --- |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL | debug APK 生成成功 |
| `./gradlew :app:testDebugUnitTest --tests OnBackInvokedCallbackEnabledTest` | PASSED | 新規追加テスト |
| `./gradlew :app:testDebugUnitTest --tests InternetPermissionAbsenceTest` | PASSED | 既存 manifest テスト |
| `./gradlew :app:testDebugUnitTest`（フル実行） | 507 tests / 502 passed / 5 failed | 失敗 5 件は **本変更とは無関係の既存失敗** |

### 既存失敗テストの内訳（本 PR スコープ外）

- `LockedFillResponseSecurityTest.lockedFillResponse_bytes_doNotContainCandidateCredentialMaterial`
- `PackageSignatureResolverTest.resolveSha256_api26_singleSigner_returnsHash`
- `PackageSignatureResolverTest.resolveSha256_api28_singleSigner_returnsCanonicalHash`
- `PackageSignatureResolverTest.resolveSha256_api28_multipleSigners_isOrderIndependent`
- `PackageSignatureResolverTest.resolveSha256_isReRunnable_pickingUpUpdatedSignerEachTime`

いずれも `java.lang.NullPointerException` で Robolectric の `PackageInfo` モック関連で
失敗している。`git stash` で本 PR の変更を退避してベース状態（`f9596df`）で同テストを
実行しても **同一の 5 件が失敗** することを確認済み（NFR 1.2 でいう「本対応前に成立して
いた既存テスト」には該当しない既存債務）。本 Issue のスコープ外のため別 Issue として
切り出すことを推奨。

### Lint について

`./gradlew :app:lintDebug` はベース状態でも errors 94 / warnings 129 を出して失敗する
既存債務がある（`PackageSignatureResolver.kt` 等の `NewApi` 系 Error）ため、本 PR で
lint task の SUCCESS を担保することは不可能。

ただし本 PR の差分（manifest への 1 属性追加）に起因する **追加の lint 影響**は以下の
1 件の **Warning** のみで、Error は新たに発生していない:

```
AndroidManifest.xml:25: Warning: Attribute enableOnBackInvokedCallback is
only used in API level 33 and higher (current min is 26) [UnusedAttribute]
```

これは NFR 1.1（API 32 以下では属性が無視される）の意図そのものを lint が指摘して
いるだけで、属性自体は OS が無視する設計のため抑止せずに残す方が運用上有益。

## Requirement 別 検証マッピング

| Req ID | 検証手段 | 結果 |
| --- | --- | --- |
| 1.1 | `OnBackInvokedCallbackEnabledTest.applicationManifest_optsIntoOnBackInvokedCallback` で manifest XML をパースし `android:enableOnBackInvokedCallback="true"` を assert | PASS |
| 1.2 | manifest 差分の目視確認（追加行は 1 行のみ。既存 9 属性の値はすべて未変更） | PASS |
| 1.3 | manifest 差分の目視確認（`<queries>` / 全 `<activity>` / `<service>` / 子 `<intent-filter>` / `<meta-data>` は無変更） | PASS |
| 2.1 / 2.2 / 2.3 | 戻る操作は OS 既定の `finish()` 経路をそのまま使用しており、コード変更 0 件。挙動差分は理論上発生しない（後述「3.1 の検証方法」で実機確認推奨） | コード変更 0 件で論理的に維持 |
| 2.4 | 移行対象 0 件（下記 Grep 結果） | N/A |
| 2.5 | 上述 "onBackPressed override 調査結果" 節を参照 | PASS |
| 3.1 | 後述「Requirement 3.1 の検証方法」を参照 | 手動検証手順を記載 |
| 3.2 | 本 `impl-notes.md` への記録（このセクション） | PASS |
| NFR 1.1 | `android:enableOnBackInvokedCallback` は API 33 で導入され、それ未満は OS が単に無視する仕様（lint Warning も同旨）。`minSdk=26` のため低 OS では属性無視で従来挙動を維持 | PASS |
| NFR 1.2 | 新規追加テスト 1 件が PASS、既存 manifest テスト `InternetPermissionAbsenceTest` も PASS。本変更で新たに壊れた既存テストは 0 件 | PASS |
| NFR 2.1 | アプリコードへの変更 0 件（manifest 属性追加のみ）のため、ランタイムログ追加なし | PASS |

## Requirement 3.1 の検証方法

CI 環境にはエミュレータが無いため、本 PR では logcat の warning 解消を自動検証
できない。以下を **手動検証手順** として記録する（Reviewer / QA 向け）。

### 推奨手順（実機 / エミュレータ）

1. Android 13 (API 33) 以上のエミュレータまたは実機を用意
2. `./gradlew :app:installDebug` で APK をインストール
3. `adb logcat -c && adb logcat | grep -i "OnBackInvokedCallback"` を別ターミナルで起動
4. ランチャーから KeyNest を起動し、クレデンシャル一覧 → 編集画面 → 設定 など主要画面を
   一通り遷移
5. logcat 出力に `OnBackInvokedCallback is not enabled for the application` または
   同等の WARN 行が **出力されない** ことを確認

### 代替検証（CI / 自動化向け）

- 本 PR では manifest の merged 出力に `android:enableOnBackInvokedCallback="true"` が
  含まれていることを自動検証している（`OnBackInvokedCallbackEnabledTest`）。OS 側の
  warning 出力ロジックは「merged manifest にこの属性が無い場合のみ警告」なので、
  merged manifest が `true` を含んでいる時点で warning は発生し得ない。
- merged manifest の確認: `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml` で
  該当行が出力されることを目視確認済み（line 46）。

## 確認事項

特になし。

- requirements.md の対象画面リスト（8 画面）はすべて、戻る操作のカスタム実装を持たず
  OS 既定の戻る挙動を利用している（`onBackPressed` override 0 件で確認済み）ため、
  manifest 属性 1 行の追加のみで Requirement 2.x を満たす。
- predictive back gesture のカスタムアニメーション (`OnBackAnimationCallback`) や
  Compose `BackHandler` への移行は Out of Scope セクションで明示的に除外されている。
