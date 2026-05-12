# Requirements Document

## Introduction

EasyKeyNest は、Google アカウントや Google Password Manager に依存せずに、Android 端末上で
アプリ単位のクレデンシャル（package name / username / password / 表示名）をローカルに手動登録し、
Android Autofill Framework 経由で対象アプリのログイン画面に入力候補を提示するパスワードマネージャ
アプリの MVP である。想定ユースケースは、Google アカウントを運用しない業務端末や
MDM Dedicated Device において、ユーザーが毎回 ID/PW を手入力する負担を軽減することにある。

package name のみの一致では署名違いの偽アプリへ credential が漏出するリスクがあるため、
本 MVP では package name に加えて署名証明書の SHA-256 ハッシュ照合を必須とする。
本ドキュメントはこの MVP のスコープ・受入基準・非機能要件を定義する。

なお現リポジトリは LICENSE と .gitignore のみが存在する初期状態であり、本要件は新規実装を前提とする。

## 確定事項（人間判断の反映）

Issue 本文「判断を委ねたい点」のうち、本 MVP では以下を確定事項として採用する。

- **暗号化方式**: Room（永続化）+ Android Keystore による AES-GCM 暗号化を採用する。
  EncryptedSharedPreferences / EncryptedFile / SQLCipher は採用しない。
- **Vault アンロックのタイミング**: Autofill 候補選択時にアンロックする方式を採用する。
  具体的には `onFillRequest` 時点では認証要求の Dataset（Authentication Intent 付き）を返し、
  ユーザーが候補を選択した時点で BiometricPrompt を起動し、生体／端末認証成功後に
  実際の credential を含む FillResponse を遅延返却する。
- **`onSaveRequest`**: MVP スコープには含めない。クレデンシャル登録はアプリ内 UI からの
  手動登録のみとする。

その他の判断委任項目は「Open Questions」に整理する。

## スコープ

### In Scope

- アプリ内 UI からの手動 credential 登録（package name / username / password / 表示名）
- 登録時の対象アプリ署名証明書 SHA-256 ハッシュ取得と保存
- AutofillService 実装（`onFillRequest` のみ）
- Autofill 候補選択時の生体／端末認証によるアンロック
- package name + 署名 SHA-256 ハッシュ照合に基づく候補表示制御
- Vault ロック中の credential 直接返却の禁止
- Room + Android Keystore (AES-GCM) によるローカル暗号化保存
- Android 設定画面の Autofill Service 有効化への導線
- Android 8.0 以上での基本動作および Android 14 以上での AutofillService 動作

### Out of Scope

以下は本 MVP では実装しない。

- Passkey 対応
- Android 14+ Credential Manager Provider 対応（将来拡張）
- Google Password Manager との同期
- クラウド同期
- 複数端末同期
- Web サイト URL / Digital Asset Links 連携
- WebView 内フォームへの高度な対応
- `onSaveRequest` による自動保存提案
- OCR / Accessibility Service を使った入力欄検出
- 画面上の文字を読み取って強制入力する機能
- root 権限 / Device Owner 権限を前提とする実装
- MDM 管理 API による設定配布
- パスワード生成機能
- 共有 Vault / チーム管理機能

## ユーザーストーリー

- US-1: 業務端末利用者として、対象アプリの package name / username / password / 表示名を
  本アプリに登録したい。毎回手入力する手間を省くため。
- US-2: 業務端末利用者として、対象アプリのログイン画面で保存済み credential を Autofill 候補として
  選択し入力したい。ID/PW 入力の負担と入力ミスを減らすため。
- US-3: 業務端末利用者として、credential 利用時に生体認証または端末認証を求められたい。
  端末紛失時の不正利用を防ぐため。
- US-4: 業務端末利用者として、同名 package を持つ偽装アプリには credential が出ないことを保証
  されたい。誤入力による情報漏出を避けるため。
- US-5: 業務端末利用者として、本アプリを Android Autofill Service として有効化する手順を
  アプリ内から案内されたい。初期セットアップで迷わないため。

## Requirements

### Requirement 1: Credential 手動登録

**Objective:** As a 業務端末利用者, I want アプリ内 UI から credential を手動登録すること, so that 対象アプリのログイン時に Autofill 候補として再利用できる

#### Acceptance Criteria

1.1. When ユーザーが credential 新規作成画面で package name・username・password・表示名 (label) を入力し保存を確定したとき, the システム shall その 4 項目を 1 件の credential レコードとして永続化する。

1.2. When credential 保存処理が実行されるとき, the システム shall password を平文のまま永続化ストレージ（Room / SharedPreferences / ファイル）に書き込まないこと。

1.3. If 入力された package name が空または形式不正であるなら, the システム shall 保存を中止し、ユーザーにバリデーションエラーを提示する。

1.4. When credential 保存処理が成功したとき, the システム shall 同じ package name に対し複数件の credential が登録されることを許容する。

1.5. When ユーザーが既存 credential を編集または削除したとき, the システム shall その変更を永続化し、以降の Autofill 候補に反映する。

### Requirement 2: 署名証明書ハッシュの取得と保存

**Objective:** As a 業務端末利用者, I want 登録時点の対象アプリ署名情報を記録すること, so that 後から署名違いの偽アプリに credential が漏れないようにできる

#### Acceptance Criteria

2.1. When credential 保存時に指定 package name のアプリが端末にインストールされているとき, the システム shall そのアプリの署名証明書の SHA-256 ハッシュを取得し、credential レコードと紐付けて保存する。

2.2. If credential 保存時に指定 package name のアプリが端末にインストールされていないとき, the システム shall 署名ハッシュなしで credential を保存することを許容し、その状態をレコード上に区別可能な形で記録する。

2.3. When 同一 package name の credential を再保存または更新したとき, the システム shall 保存時点で取得できる最新の署名ハッシュを記録する。

### Requirement 3: AutofillService による候補表示

**Objective:** As a 業務端末利用者, I want 対象アプリのログイン画面で保存済み credential を Autofill 候補として提示されること, so that ID/PW を手入力せずに済む

#### Acceptance Criteria

3.1. When 対象アプリのログイン画面で username または password 入力欄にフォーカスが移り Android Autofill Framework が `onFillRequest` を呼び出したとき, the システム shall 呼び出し元アプリの package name と登録済み credential を突合し、候補生成処理を開始する。

3.2. When `onFillRequest` 内で `AssistStructure` を解析した結果、username 欄または password 欄を推定できないとき, the システム shall クラッシュせずに空または null の FillResponse を返す。

3.3. When 該当する credential が 1 件以上存在し、後述の署名照合および Vault 状態条件を満たすとき, the システム shall Autofill UI に各 credential を表示名 (label) 付きの Dataset 候補として返す。

3.4. When ユーザーが Autofill UI 上で候補を確定したとき, the システム shall その credential の username 値を username 欄に、password 値を password 欄に入力させる。

3.5. The システム shall `onFillRequest` 内でネットワーク通信および長時間ブロッキング処理を行わない。

### Requirement 4: 署名証明書ハッシュ照合

**Objective:** As a 業務端末利用者, I want package name が同一でも署名が異なるアプリには credential が表示されないこと, so that 偽装アプリへの認証情報漏出を防げる

#### Acceptance Criteria

4.1. When `onFillRequest` の呼び出し元 package name に一致する credential が存在するとき, the システム shall 呼び出し元アプリの現在の署名証明書 SHA-256 ハッシュを取得し、credential 保存時のハッシュと比較する。

4.2. If 呼び出し元アプリの署名ハッシュが credential 保存時のハッシュと一致しないとき, the システム shall その credential を Autofill 候補として返さない。

4.3. If credential が「署名ハッシュ未保存」の状態（Requirement 2.2 のケース）であるとき, the システム shall その credential を Autofill 候補として返さない。

4.4. When 同一 package name に対し署名照合に成功した credential と失敗した credential が混在するとき, the システム shall 照合に成功したものだけを候補として返す。

### Requirement 5: Vault アンロック（Autofill 候補選択時）

**Objective:** As a 業務端末利用者, I want credential 利用直前に生体／端末認証で Vault をアンロックすること, so that 端末を一時的に他人が操作しても credential が無断利用されない

#### Acceptance Criteria

5.1. While Vault がロック状態にあるとき, the システム shall `onFillRequest` の応答として認証用 Authentication Intent を伴う Dataset（候補プレースホルダ）のみを返し、復号済み credential 本体を含めない。

5.2. When ユーザーが Autofill UI 上で候補プレースホルダを選択したとき, the システム shall BiometricPrompt（または端末認証フォールバック）を起動し、ユーザー認証を要求する。

5.3. When 生体／端末認証が成功したとき, the システム shall Vault をアンロックし、復号済み credential を含む FillResponse を遅延返却する。

5.4. If 生体／端末認証がキャンセルまたは失敗したとき, the システム shall credential を返さず、Autofill UI に対し失敗応答を返す。

5.5. While Vault がロック中であるとき, the システム shall 復号済み credential をプロセスメモリ上で長時間保持しない。

### Requirement 6: Autofill Service 有効化導線

**Objective:** As a 業務端末利用者, I want 本アプリを Autofill Service として有効化する手順をアプリ内から案内されること, so that 初期セットアップで迷わずに済む

#### Acceptance Criteria

6.1. When 本アプリが Android Autofill Service として未選択の状態でユーザーがアプリを起動したとき, the システム shall Autofill Service 有効化の案内画面を提示する。

6.2. When ユーザーが案内画面上で有効化操作を実行したとき, the システム shall Android の Autofill Service 選択設定画面（`Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE` 相当）に遷移させる。

6.3. When 本アプリが既に Autofill Service として有効化されているとき, the システム shall 案内画面を必須表示しない、または有効化済みである旨を表示する。

### Requirement 7: 対応 Android バージョン

**Objective:** As a 業務端末利用者, I want Android 8.0 以上で本機能が動作すること, so that 既存業務端末上でも利用できる

#### Acceptance Criteria

7.1. The システム shall Android 8.0 (API 26) 以上の端末で credential 登録および Autofill 候補表示が動作する。

7.2. Where Android 14 (API 34) 以上の端末で実行される場合, the システム shall AutofillService として支障なく動作する。

7.3. The システム shall Accessibility Service / OCR / Device Owner / root 権限を必須としない。

## Non-Functional Requirements

### NFR 1: セキュリティ・暗号化

1.1. The システム shall password を Room データベース上に保存する際、Android Keystore で保護された鍵を用いた AES-GCM で暗号化した状態で保存する。

1.2. The システム shall AES-GCM の暗号鍵を Android Keystore 内に保持し、平文の鍵をアプリプロセス外へ書き出さない。

1.3. The システム shall password の平文を logcat、Throwable のメッセージ／スタックトレース、analytics、クラッシュレポートに出力しない。

1.4. While Vault がロック中であるとき, the システム shall 復号済み password を Autofill 応答に含めない。

1.5. The システム shall MVP では完全ローカル保存とし、credential をネットワーク送信しない。

### NFR 2: 応答性能（Autofill 応答時間）

2.1. The システム shall `onFillRequest` 受信から FillResponse 返却までの処理時間中央値を 300ms 以内に収める（Vault ロック中の認証用 Dataset 返却時を計測対象とする）。

2.2. The システム shall `onFillRequest` 内でネットワーク通信、ディスク全走査、ブロッキング暗号化処理を行わない。

### NFR 3: 障害耐性

3.1. When `AssistStructure` から username/password 欄を特定できないとき, the システム shall 例外を発生させずに空 FillResponse を返す。

3.2. When 対象 package name の credential が存在しないとき, the システム shall 例外を発生させずに空 FillResponse を返す。

### NFR 4: 互換性・依存性

4.1. The システム shall Accessibility Service に依存しないで全機能を提供する。

4.2. The システム shall Google アカウントへのサインインを前提としない。

4.3. The システム shall MDM 管理 API、Device Owner 権限、root 権限を前提としない。

### NFR 5: 可観測性

5.1. The システム shall Autofill 応答に関する診断ログを出力する場合でも、password、復号済み credential 本体、署名照合結果に含まれるユーザー固有値を平文で出力しない。

## 制約・前提

- 本リポジトリは初期状態であり、Android アプリ本体・Gradle 構成・Manifest を含むすべてを新規構築する前提である。
- 完全ローカル保存とし、クラウド同期・複数端末同期は MVP では一切行わない。
- AutofillService は Accessibility Service ではなく、Android 公式 `android.service.autofill.AutofillService` を利用する。
- Credential Manager Provider 対応は将来拡張とし、MVP の必須スコープには含めない。
- Autofill 対象は対象アプリのログイン画面に限定し、WebView 内フォームの高度な対応は行わない。
- `onSaveRequest` は実装せず、credential は本アプリ内 UI からの手動登録のみで作成する。

## Open Questions

Issue 本文「判断を委ねたい点」のうち、本要件で確定していない項目を以下に整理する。
本項目は Architect / Designer フェーズで決定する必要がある。

- credential 登録時に package name を **手入力させるか / インストール済みアプリ一覧から選択させるか**
  （UX 選定。両対応の余地もあり）
- `AssistStructure` を用いた username/password 欄推定のロジック詳細と、推定精度の MVP 受入水準
  （例: autofillHints / inputType / hint テキストヒューリスティクスのどこまでをカバーするか）
- 同一 package name に対し credential が複数件存在する場合の Autofill UI 上の並び順・絞り込み方針
- Vault アンロック有効期間（候補選択ごとに毎回認証するか、短時間のセッション保持を許すか）
- Autofill Service 有効化導線を「アプリ起動時に必ず提示」するか「設定画面からのみ到達」とするかの UX 方針
- `onFillRequest` 応答時間 NFR 2.1 の計測手段とリリース判定基準

