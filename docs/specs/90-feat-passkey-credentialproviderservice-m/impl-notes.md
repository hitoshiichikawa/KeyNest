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

- なし（T-01 で停止しているため、未実装段階で乖離は発生していない）。

## 手動検証 (req 5.1 / 5.2 / 5.3) の扱い

- Developer サブエージェント側ではエミュレータ / 実機操作が出来ないため
  **未実施**。タスク指示のとおり、人間レビュアに委ねる。
- 自動可能な部分（`assembleDebug` / `testDebugUnitTest` / `lintDebug`）も
  T-01 のブロッカーにより未完了。
