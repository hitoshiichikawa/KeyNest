# Requirements Document

## Introduction

Google Play は 2026 年 8 月 31 日以降、「最新 Android リリースから 1 年以内の
API レベル」を対象としないアプリの更新配信を停止する。現時点で KeyNest は
`targetSdk = 35` / `compileSdk = 35`（Android 15 世代）で構築されており、この
ポリシーには非準拠となる。継続して Play Console から更新を配信可能な状態を
保つため、アプリのターゲット API レベルを **Android 16 (API level 36)** 以上
へ引き上げる。本 Issue は「ターゲット API レベルの引き上げに伴い、既存の
ユーザー可観測な挙動・既存テスト・リリース配信経路がリグレッションを起こさ
ないこと」をゴールとし、機能追加・UI 刷新・minSdk 変更・依存ライブラリの
自発的なメジャーアップグレードは対象としない。

## Requirements

### Requirement 1: ターゲット API レベルの引き上げ

**Objective:** As a KeyNest メンテナ, I want KeyNest アプリのターゲット API
レベルが Android 16 (API level 36) 以上になっていること, so that 2026 年 8 月
31 日以降も Google Play Console から既存ユーザーへの更新配信を継続できる

#### Acceptance Criteria

1. The KeyNest application shall declare a target API level of 36 or higher for its release artifact (APK / AAB)
2. The KeyNest application shall declare a compile API level of 36 or higher so that the release artifact can reference API level 36 platform symbols
3. When the release artifact is uploaded to Google Play Console after 2026-08-31, the Play Console shall not reject the upload due to the target API level policy for existing apps
4. When the release artifact is uploaded to Google Play Console, the Play Console shall not display a warning stating that the target API level is below the currently required minimum

### Requirement 2: 既存サポート端末範囲の維持

**Objective:** As a 既存 KeyNest エンドユーザー（Android 8.0 / API 26 以降を
利用中）, I want ターゲット API レベルの引き上げ後もこれまでと同じ端末で
KeyNest をインストール・利用できること, so that 本対応を理由に既存の
サポート範囲から外されない

#### Acceptance Criteria

1. The KeyNest application shall declare a minimum supported API level no higher than 26
2. When the updated release artifact is installed on a device running API level 26, the KeyNest application shall launch successfully and reach its top screen without crashing
3. While KeyNest is running on any device within the supported API range (26 through 36+), the KeyNest application shall not surface a "unsupported OS version" style error caused solely by this change

### Requirement 3: 既存ユーザー可観測な機能の回帰防止

**Objective:** As a 既存 KeyNest エンドユーザー, I want 本対応後もオート
フィル・パスキー・生体認証・設定画面などの既存機能が対応前と同じように
動作すること, so that ターゲット API 引き上げの副作用で普段の利用が
壊れない

#### Acceptance Criteria

1. When ユーザーが対応アプリからオートフィル候補を要求したとき, the KeyNest Autofill flow shall 対応前と等価に候補を提示し、選択した候補で対象フィールドが埋まる挙動を維持する
2. When ユーザーが対応アプリから新規パスキー作成 / 既存パスキーによる認証を要求したとき, the KeyNest Credential Provider shall 対応前と等価にパスキー作成 / 認証フローを完了できる
3. When ユーザーがオートフィル / パスキー操作で生体認証プロンプトに到達したとき, the KeyNest application shall 対応前と等価に生体認証プロンプトを表示し、認証成否に応じた分岐を維持する
4. When ユーザーがクレデンシャル一覧・クレデンシャル編集・設定・Danger Zone・OSS ライセンス・オートフィル有効化の各画面を開いたとき, the KeyNest application shall 対応前と等価にその画面を表示・操作できる状態にする
5. If Android 16 が対象 API での挙動を変更した OS API を KeyNest が利用しており、それがユーザー可観測な挙動差を発生させ得る場合, the KeyNest application shall 対応前と等価なユーザー可観測な挙動を維持するように調整する

### Requirement 4: 既存テストの通過

**Objective:** As a KeyNest 開発者 / CI, I want 本対応後も既存ユニット
テストが全件パスすること, so that ターゲット API 引き上げに伴う内部
リグレッションを既存テスト資産で検知できる

#### Acceptance Criteria

1. When `./gradlew test` を本対応後のツリーで実行したとき, the unit test suite shall 全テストケースを成功で終了する
2. The KeyNest test suite shall 既存テストの期待値（assertion）を本対応の都合で書き換えないことを条件として全件パスする
3. If 既存テストのうち、ターゲット API レベル引き上げ以外の pre-existing failure に該当するケースが存在する場合, the KeyNest maintainer shall 該当テストの pre-existing 事由と本対応との無関係性を `impl-notes.md` に記録する

### Requirement 5: リリース配信経路の維持

**Objective:** As a KeyNest リリース担当, I want 本対応後の release artifact
が既存の署名 / バージョニング運用で Play Console にアップロード可能な状態
であること, so that Play Console への公開手順が本対応の都合で追加変更を
強いられない

#### Acceptance Criteria

1. When 現行の署名認証情報（既存の upload keystore 一式）が揃っている状態で release build を作成したとき, the KeyNest release artifact shall 現行と同じ upload key で署名された状態で生成される
2. The KeyNest release artifact shall バージョンコードを現行 (3) 以上の整数で、かつ既存 Play Console 上の公開バージョンより厳密に大きい値として持つ
3. The KeyNest release artifact shall アプリケーション ID を現行 (`io.github.hitoshiichikawa.keynest`) から変更しない
4. When 本対応の release artifact を Play Console の内部テスト / クローズドテスト / オープンテストトラックにアップロードしたとき, the Play Console shall アップロード自体を target API level 以外の理由で拒否しない（既存ポリシー準拠の状態を維持する）

## Non-Functional Requirements

### NFR 1: 事前検証経路の可用性

1. The KeyNest release artifact shall Play Console の内部テスト / クローズドテスト / オープンテストのいずれかのトラック経由で、本対応を本番リリースする前に配信して検証可能な状態を維持する
2. The KeyNest maintainer shall 本対応の実機 / エミュレータ検証結果（少なくとも API 26 相当の下限と API 36 相当の上限で起動・主要 flow 実行に成功したこと）を `impl-notes.md` または PR 本文に記録する

### NFR 2: 既存永続データとの互換

1. When 本対応前のバージョンでインストール済みのユーザーが本対応後のバージョンへ更新したとき, the KeyNest application shall 既存のクレデンシャル / パスキー / 設定を失わずに継続利用できる状態を維持する
2. The KeyNest application shall 本対応を理由としたユーザーデータの初期化・再入力・再ログインをユーザーに要求しない

## Out of Scope

- `minSdk` の引き上げ（現行 26 を維持）
- 新機能追加・UI 刷新・配色 / タイポグラフィ変更
- Android 16 で新しく提供された API を使った新機能実装
- 本対応と直接関係しない依存ライブラリの自発的なメジャーアップグレード
- Play Console の Data safety / プライバシーポリシー等、target API 引き上げと独立したストア提出物の更新
- CI ワークフロー（例: emulator matrix の API 36 追加）の再構成 — 別 Issue で扱う
- 対応前から存在する既存 lint 警告 / pre-existing test failure の解消
- バージョン名の命名規則変更（バージョンコードの増分方針は既存運用を踏襲する）

## Open Questions

- なし（Issue 本文と現行ビルド構成の確認により、要件は確定可能。実装フェーズで採用する compileSdk / targetSdk の具体値、ビルドツールチェーンのバージョン、依存ライブラリの調整範囲は design.md / 実装フェーズで確定する）

## 関連

- Related: #128 （Android 15 世代の edge-to-edge 対応。同系統の OS 世代追随作業）
- Related: #94 （CI 上での emulator API レベル選定。将来 API 36 emulator 追加が必要になった場合の参照点）
