# Requirements Document

## Introduction

KeyNest は umbrella Issue #89 で「Android Credential Manager API 経由の PassKey
プロバイダ対応」を進めており、Phase 1〜7 で Service 登録・保管モデル・登録/認証
セレモニー・一覧 UI・個別管理 UI・設定画面導線までを実装済み/実装中である。一方で
公開ドキュメント（README / Privacy Policy / Support / CONTRIBUTING）はパスワード機能
を前提とした記述のままで、PassKey 機能・その保管/暗号化方針・export 禁止・端末紛失時
の挙動・API 34+ 制約に触れていない。本 Issue (#104, Phase 8) はこの差分を埋め、
ユーザーと貢献者が PassKey 機能を正確に理解できるよう既存ドキュメントへ追記する。
これは **ドキュメントのみ** の変更であり、アプリのコード・テスト・リソースは変更しない。

## Requirements

### Requirement 1: README への PassKey 機能概要の追記

**Objective:** As a KeyNest を検討する潜在ユーザー / 開発者, I want README の Features
セクションで PassKey 対応とその提供形態・対応 OS を把握できること, so that インストール
前に PassKey をどう使えるかと前提条件を理解できる

対象ファイル: `README.md`

#### Acceptance Criteria

1.1. The `README.md` の Features セクション shall PassKey が Android Credential Manager
経由の PassKey プロバイダとして提供される旨の項目を含む。

1.2. Where Features セクションに PassKey 項目を追加するとき, the `README.md` shall
既存の Autofill 関連項目および Custom fields 項目の **後ろ** に PassKey 項目を配置する
（先頭ではない。Issue #104 確認事項1 で人間が Option B を選択済み）。

1.3. The `README.md` shall PassKey 機能が Android 14 (API 34) 以降でのみ利用可能である
旨を明記する。

1.4. The `README.md` shall PassKey 機能の追記において表記を **「PassKey」** に統一し、
"passkey"（小文字）や "Passkey" を新規追加文に用いない。

### Requirement 2: Privacy Policy への PassKey 取り扱いの追記

**Objective:** As a プライバシーを気にするエンドユーザー / 審査者, I want Privacy
Policy で PassKey（秘密鍵）の保管場所・暗号化・外部送信有無・export 可否を確認できること,
so that PassKey も既存のパスワードと同じくオフライン・端末内完結であることを信頼できる

対象ファイル: `docs/privacy-policy.md`

#### Acceptance Criteria

2.1. The `docs/privacy-policy.md` shall PassKey の秘密鍵が端末内で AES-GCM により暗号化
され、暗号鍵が Android Keystore に保管される旨を記述する。

2.2. The `docs/privacy-policy.md` shall PassKey 関連データが端末外（開発者・第三者の
いずれにも）に送信・アップロードされない旨を記述する。

2.3. The `docs/privacy-policy.md` shall KeyNest が PassKey の export / backup 機能を
提供しない旨を明記する（既存 credential と同一の export 禁止ポリシー、umbrella #89 確定）。

2.4. The `docs/privacy-policy.md` shall 既存の "Last updated" 日付を本変更の反映日に
更新する。

### Requirement 3: Support への PassKey FAQ の追記

**Objective:** As a PassKey を利用中で困っているエンドユーザー, I want Support ページで
端末紛失・RP 側再登録・端末移行・OS Credential Manager からの解除に関する案内を読めること,
so that PassKey 特有の制約に直面したとき自力で対処できる

対象ファイル: `docs/support.md`

#### Acceptance Criteria

3.1. The `docs/support.md` shall 端末紛失/初期化時に当該端末上の PassKey は復元できず、
各 RP（サービス）側での再登録が必要である旨の FAQ を含む。

3.2. The `docs/support.md` shall PassKey の端末間移行（クロスデバイス同期/移行）が現状
サポートされない旨の FAQ を含む。

3.3. The `docs/support.md` shall OS の「パスワードと PassKey」設定から KeyNest を
PassKey プロバイダとして解除/無効化する手順への案内を含む FAQ を含む。

3.4. The `docs/support.md` shall PassKey FAQ の追記において表記を **「PassKey」** に統一する。

### Requirement 4: CONTRIBUTING への PassKey 開発前提の追記

**Objective:** As a KeyNest への貢献を検討する開発者, I want CONTRIBUTING で PassKey
関連変更の前提（対応 OS・オフライン境界・表記規約）を把握できること, so that PassKey
コードへ貢献する際に既存方針を踏襲できる

対象ファイル: `CONTRIBUTING.md`

#### Acceptance Criteria

4.1. The `CONTRIBUTING.md` shall PassKey 機能が Android 14 (API 34) 以降を前提とする旨を
簡潔に記述する。

4.2. The `CONTRIBUTING.md` shall PassKey 関連の変更でも `INTERNET` permission 追加・
ネットワーク IO を伴ってはならない旨（既存のオフライン境界方針の踏襲）を明記する。

4.3. The `CONTRIBUTING.md` shall ドキュメント/コード上の PassKey 表記を **「PassKey」**
に統一する規約に言及する。

### Requirement 5: ドキュメント横断の表記・言語の一貫性

**Objective:** As a ドキュメントの読者, I want すべての追記が一貫した表記・言語で書かれて
いること, so that ドキュメント間で用語や言語が揺れず混乱しない

対象ファイル: `README.md`, `docs/privacy-policy.md`, `docs/support.md`, `CONTRIBUTING.md`

#### Acceptance Criteria

5.1. The 追記対象の各ドキュメント shall PassKey 機能に言及する箇所で表記を **「PassKey」**
に統一する（"passkey" / "Passkey" を新規追加文に用いない）。

5.2. The 追記対象の各ドキュメント shall 既存ドキュメントが英語であることに合わせ、
新規追記分も英語で記述する（Issue #104 確認事項2、既存 docs 言語ポリシーに準拠）。

5.3. The 追記分 shall 生体認証フォールバックに言及する場合、その挙動を「生体認証
（生体未設定なら Device Credential: PIN/Pattern）にフォールバックする」と既存 #89 確定方針
に整合する形で記述する。

5.4. The 追記分 shall 既存ドキュメントが宣言する「端末内完結・外部送信なし」境界と矛盾
する記述を含まない。

## Non-Functional Requirements

### NFR 1: 変更範囲の限定（ドキュメントのみ）

1. The 本 Issue の変更 shall `README.md` / `docs/privacy-policy.md` / `docs/support.md` /
   `CONTRIBUTING.md` の 4 ファイルのみを対象とし、アプリのソースコード（`app/src/**`）・
   テスト・リソース・ビルド設定を変更しない。
2. The 本 Issue の変更 shall ドキュメント変更のみであるため、CI のインスツルメンテーション
   テスト結果に影響を与えない（コードパスへの変更を含まない）。

### NFR 2: 既存ドキュメント構造の保全

1. The 追記 shall 既存ドキュメントの見出し構成・トーン・Markdown フロントマター
   (`--- title: ... ---`) を破壊せず、既存セクションへの追記または新規セクション追加の形で行う。
2. The README への PassKey 項目追加 shall 既存の Documentation / Build / CI / Contributing /
   Security / License / Acknowledgements セクションの内容・順序を変更しない。

## Out of Scope

- 既存ドキュメントの **英語以外への翻訳**（多言語 docs の整備）。
- **ブログ記事 / リリースノート / Google Play ストア掲載文** の作成・更新。
- アプリ内の文言（`strings.xml`）・UI・設定画面・一覧 UI の変更（別 Issue が担当）。
- PassKey 機能そのものの実装・修正（#89 配下の他 Phase が担当）。
- SECURITY.md / Terms of Service ページの PassKey 追記（本 Issue の対象 4 ファイルに含まない）。
- README の Features 列挙以外への大幅な再構成（既存項目の文言改変・並べ替え）。

## Open Questions

- README の PassKey 項目を「単一の箇条書き 1 行」とするか「複数行の説明付き」とするかの
  粒度は明示指定がない（既存 Features 項目は太字ラベル + 短い説明の形式のため、それに
  倣う想定。design / 実装時に既存トーンへ合わせて確定する）。
- Support の「OS Credential Manager から KeyNest を解除する手順」を具体的なメニュー名
  （例: 設定 → パスワード、パスキー、アカウント → 優先サービス）まで記載するか、概略案内に
  留めるかは明示指定がない（端末/OS バージョンによりメニュー文言が異なるため、概略 + OS 設定
  への誘導に留めるのが安全と想定）。
