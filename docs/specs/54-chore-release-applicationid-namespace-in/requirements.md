# Requirements Document

## Introduction

KeyNest は現在、Android プロジェクトの applicationId / namespace にプレースホルダの
`com.example.keynest` を採用している。Google Play ストアの仕様上、applicationId は
公開後に変更不可であるため、本番公開前の時点で正式な逆ドメイン `inc.goodanswers.keynest` に
リネームしておく必要がある。本要件は、Gradle 設定・Kotlin ソースのパッケージ階層・
AndroidManifest や autofill 設定など各種 XML・Room スキーマのエクスポート先ディレクトリ・
ProGuard ルールを一括で新識別子に置換し、ビルド・ユニットテスト・lint が全て成功する状態に
到達させることを目的とする。本作業は機能追加を含まないリネーム chore であり、ユーザー
可視の機能挙動は変更しない。

## Requirements

### Requirement 1: Gradle ビルド構成の identifier 切り替え

**Objective:** As a リリース担当者, I want app モジュールの namespace と applicationId が
新識別子に切り替わっていること, so that ビルド成果物（APK / AAB）が Google Play 公開用の
正式パッケージ名で生成される

#### Acceptance Criteria

1. The KeyNest Android Build Configuration shall declare `namespace` の値として `inc.goodanswers.keynest` を保持する
2. The KeyNest Android Build Configuration shall declare `applicationId` の値として `inc.goodanswers.keynest` を保持する
3. When build configuration を読み込んだとき, the KeyNest Android Build Configuration shall 旧識別子 `com.example.keynest` の文字列を 1 件も含まない
4. The KeyNest Android Build Configuration shall Room の schemaLocation 引数が新パッケージ配下の database クラスを指すように更新された値を保持する

### Requirement 2: Kotlin ソースのパッケージ階層リネーム

**Objective:** As a KeyNest 開発者, I want main / test / androidTest 全ソースセットの
Kotlin パッケージ階層が新識別子に再配置されていること, so that 以降のソース追加が
新規 namespace に整合し、ビルドツールが正しい R クラス・BuildConfig を生成する

#### Acceptance Criteria

1. The KeyNest App Module shall main ソースセット配下の Kotlin ファイルを `inc/goodanswers/keynest/` 階層に配置する
2. The KeyNest App Module shall test（unit test）ソースセット配下の Kotlin ファイルを `inc/goodanswers/keynest/` 階層に配置する
3. The KeyNest App Module shall androidTest（instrumented test）ソースセット配下の Kotlin ファイルを `inc/goodanswers/keynest/` 階層に配置する
4. The KeyNest App Module shall 旧 `com/example/keynest/` 階層配下に Kotlin ソースファイルを 1 件も残さない
5. When Kotlin ソースファイルを開いたとき, the KeyNest App Module shall `package` 宣言および `import` 文を新識別子 `inc.goodanswers.keynest` 系に書き換えた状態で保持する
6. When ソースファイルがリネームされるとき, the KeyNest App Module shall git 履歴を追跡可能な移動として記録する

### Requirement 3: XML リソース・マニフェストの参照置換

**Objective:** As a Android ランタイム, I want マニフェストや autofill 設定や layout XML の
FQCN 参照がすべて新識別子で解決可能であること, so that アプリ起動・autofill サービス・
レイアウトプレビューがいずれも ClassNotFoundException や inflate 失敗を起こさず動作する

#### Acceptance Criteria

1. The AndroidManifest shall アプリケーション・アクティビティ・サービス等の FQCN 参照を新識別子で解決できる形式で保持する
2. The Autofill Service Config XML shall `android:settingsActivity` 属性が指す Activity の FQCN を新識別子に置換した値で保持する
3. The Layout XML 群 shall `tools:context` / カスタム View のクラス名属性などに含まれる FQCN を新識別子に置換した値で保持する
4. When XML リソース全体を grep したとき, the KeyNest App Module shall 旧識別子 `com.example.keynest` の文字列を 1 件も含まない

### Requirement 4: Room スキーマエクスポート先ディレクトリの移行

**Objective:** As a Room MigrationTestHelper, I want エクスポートされる schema JSON が
新 namespace のディレクトリ配下に配置されていること, so that マイグレーションテストが
新しい database クラス FQCN でスキーマファイルを解決できる

#### Acceptance Criteria

1. The Room Schema Export Directory shall 新識別子に基づく database クラスのフルパッケージ名を含むディレクトリ名でスキーマ JSON を保持する
2. When 既存の schema JSON が存在するとき, the Room Schema Export Directory shall 旧ディレクトリ配下のすべての version JSON を新ディレクトリ配下に欠落なく移動する
3. While 旧 namespace 由来のスキーマディレクトリが残っているなら, the Room Schema Export Directory shall そのディレクトリを削除する
4. The Room Schema Export Directory shall MigrationTestHelper がテスト実行時に新ディレクトリから schema を解決できる状態を維持する

### Requirement 5: ProGuard 規則の参照更新

**Objective:** As a R8 / ProGuard, I want keep ルールが新パッケージの FQCN を指していること,
so that minify 有効化時に Room エンティティ・AutofillService・Application クラスが
誤って難読化・削除されない

#### Acceptance Criteria

1. The ProGuard Rules File shall Room エンティティ・AutofillService 系クラス・Application クラスに対する `-keep` 指定の FQCN を新識別子で記述する
2. When ProGuard Rules File を grep したとき, the KeyNest App Module shall 旧識別子 `com.example.keynest` の文字列を 1 件も含まない

### Requirement 6: ビルド・テスト・lint による検証

**Objective:** As a レビュアー, I want リネーム後の状態で標準ビルドタスクが全て成功すること,
so that リネーム作業によって機能回帰・ビルド破壊・lint 違反が発生していないことを
機械的に確認できる

#### Acceptance Criteria

1. When `assembleDebug` 相当のクリーンビルドを実行したとき, the KeyNest App Module shall ビルド成功で終了する
2. When ユニットテストタスクを実行したとき, the KeyNest App Module shall 全テストが成功で終了する
3. When lint タスクを実行したとき, the KeyNest App Module shall エラーレベルの新規違反を 0 件で終了する
4. The KeyNest App Module shall リネーム作業に伴う既存テストの skip / 削除 / assert 弱化を行わない

### Requirement 7: リネーム残存物の不在確認

**Objective:** As a PR レビュアー, I want アプリコード・リソース・ビルド設定・スキーマ・ProGuard
配下に旧識別子が 1 件も残っていないことが機械的に確認できること, so that リネーム漏れに
よる潜在的な動作不良を未然に検知できる

#### Acceptance Criteria

1. When app モジュール配下を再帰検索したとき, the KeyNest App Module shall 文字列 `com.example.keynest` を 1 件も含まない
2. When app モジュール配下のパス名を再帰列挙したとき, the KeyNest App Module shall パスセグメント `com/example/keynest` を含むディレクトリまたはファイルを 1 件も含まない
3. Where `docs/` および `design/` 配下の履歴ドキュメントを対象にする場合, the Residual Check shall それらを検索対象から除外し、過去仕様の履歴参照を保持する

## Non-Functional Requirements

### NFR 1: 機能回帰の不在

1. The KeyNest App Module shall リネーム前後でユーザー可視の機能挙動（画面遷移・autofill 提案・credential CRUD・danger zone 操作・設定画面）の差分を発生させない
2. The KeyNest App Module shall リネーム前後で公開 API（ファイル単位の public class / public function のシグネチャ）を package 名以外の点で変更しない

### NFR 2: ビルド・テスト所要時間

1. The KeyNest App Module shall リネーム後のクリーンビルド + ユニットテスト + lint の合計所要時間を、リネーム前の同一マシン基準で +20% 以内に収める

### NFR 3: トレーサビリティ

1. The Rename Commits shall 各ファイル移動を `git mv` 相当の rename として記録し、Git の rename 検出（既定の類似度しきい値）で履歴が追跡可能な状態を保つ

## Out of Scope

- `docs/specs/` 配下の過去仕様書ファイル中に登場する旧識別子文字列のリライト（履歴保存のため温存する）
- `design/` 配下のリファレンス資産（`design/android-assets/` 等）に含まれる旧識別子文字列のリライト
- Google Play Console 上のアプリ登録・内部テスト配信・Play App Signing 鍵の生成および移行手順
- ストア掲載情報（アプリ名・スクリーンショット・説明文）の更新
- バージョン番号（`versionCode` / `versionName`）の引き上げ
- minify / R8 を本リネームと同時に有効化する変更
- ユーザー可視文言・UI レイアウト・アイコン・テーマの変更
- 既存のデバッグ端末上にインストールされた旧パッケージの自動アンインストール・データ移行（未公開前提のため許容）

## Open Questions

- なし（Issue 本文で「docs/specs および design 配下は履歴保存のため置換しない」「未公開のためデバッグ端末上のデータ消失は許容」が明示されており、本要件はそれに整合している）
