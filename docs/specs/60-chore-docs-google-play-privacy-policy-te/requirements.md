# Requirements Document

## Introduction

KeyNest を Google Play Console へ申請するためには、**プライバシーポリシー URL**
および **サポート連絡先** の公開 URL が必須であり、加えて利用規約の公開も推奨される。
本要件は、`hitoshiichikawa/KeyNest` リポジトリの GitHub Pages 機能（source: `main`
branch / `/docs` フォルダ、Jekyll 標準テーマ `minima`）を利用して、追加コスト 0 で
これらの法務・サポート関連ドキュメントを英語で公開可能な状態にすることを目的とする。
対象は `docs/index.md` / `docs/privacy-policy.md` / `docs/terms.md` / `docs/support.md`
/ `docs/_config.yml` の 5 ファイルであり、公開連絡先メールアドレスは Issue Triage
にて確定した `hitoshi.ichikawa@gmail.com` を採用する。本作業は静的ドキュメントの
新規配置のみを扱う chore であり、Android アプリのコード・テスト・ビルド設定には
一切影響を与えない。GitHub Pages 機能そのものの有効化（リポジトリ設定変更）および
README からのリンク追加は本 Issue では対象外であり、前者は人間による GitHub UI 操作、
後者は別 Issue で扱う。

## Requirements

### Requirement 1: ファイル配置と Jekyll サイト構成

**Objective:** As a Google Play 申請担当者, I want `docs/` ディレクトリ配下に GitHub
Pages で公開可能な 5 ファイルが配置されていること, so that リポジトリ public 化と
Pages 有効化操作のみで `https://hitoshiichikawa.github.io/KeyNest/` から法務・サポート
関連ドキュメントが英語で参照可能となる

#### Acceptance Criteria

1. The KeyNest Docs Site shall リポジトリの `docs/` ディレクトリ直下に `index.md` / `privacy-policy.md` / `terms.md` / `support.md` / `_config.yml` の 5 ファイルを保持する
2. The `docs/_config.yml` shall サイトタイトルとして `title: KeyNest` を含む
3. The `docs/_config.yml` shall Jekyll テーマとして `theme: minima` を含む
4. The KeyNest Docs Site shall 全ページ（`index.md` / `privacy-policy.md` / `terms.md` / `support.md`）を英語で記述する
5. The KeyNest Docs Site shall `docs/` 配下に多言語化用のサブディレクトリ（例: `docs/ja/`）を作成しない

### Requirement 2: トップページ (index.md) の内容

**Objective:** As a Play Store からの来訪者, I want トップページからアプリ概要と
各 sub page（privacy / terms / support）および GitHub repository への導線が把握できる
こと, so that 申請審査者および一般ユーザーが必要な情報に最短経路で到達できる

#### Acceptance Criteria

1. The `docs/index.md` shall KeyNest アプリの概要説明（Android 向けパスワード / クレデンシャル管理アプリである旨）を含む
2. The `docs/index.md` shall プライバシーポリシーページ (`privacy-policy.md` 相当) へのリンクを含む
3. The `docs/index.md` shall 利用規約ページ (`terms.md` 相当) へのリンクを含む
4. The `docs/index.md` shall サポートページ (`support.md` 相当) へのリンクを含む
5. The `docs/index.md` shall GitHub repository (`https://github.com/hitoshiichikawa/KeyNest`) へのリンクを含む

### Requirement 3: プライバシーポリシー (privacy-policy.md) の内容

**Objective:** As a Google Play Console の審査プロセス, I want プライバシーポリシー
URL の本文が KeyNest のデータ収集・共有・暗号化方式・連絡先・改定日を網羅すること,
so that Play 申請の必須要件であるプライバシーポリシーの掲載基準を満たす

#### Acceptance Criteria

1. The `docs/privacy-policy.md` shall 収集する情報セクションを含み、`KeyNest` がデータをローカル保存のみで外部送信を行わない旨を `none` の趣旨で明示する
2. The `docs/privacy-policy.md` shall データ共有セクションを含み、第三者へのデータ共有を行わない旨を `none` の趣旨で明示する
3. The `docs/privacy-policy.md` shall 暗号化方式として `Android Keystore` 経由の `AES-GCM` を明示する
4. The `docs/privacy-policy.md` shall 公開連絡先メールアドレスとして文字列 `hitoshi.ichikawa@gmail.com` を含む
5. The `docs/privacy-policy.md` shall 改定日（Last updated）として実装時に固定の日付を記載する
6. The `docs/privacy-policy.md` shall 英語で記述する

### Requirement 4: 利用規約 (terms.md) の内容

**Objective:** As a Google Play Console の審査プロセス, I want 利用規約ページが
ライセンス・免責事項・開発者情報の標準セクションを含むこと, so that Play 申請の
推奨項目である利用規約の掲載基準を満たす

#### Acceptance Criteria

1. The `docs/terms.md` shall ライセンスセクションを含み、KeyNest が `MIT License` の下で配布される旨と、リポジトリの `LICENSE` ファイルへの参照を含む
2. The `docs/terms.md` shall 免責事項セクションを含み、`AS IS`（無保証）の標準テンプレ文言を含む
3. The `docs/terms.md` shall 開発者情報セクションを含み、開発者名 `Hitoshi Ichikawa` および GitHub ハンドル `@hitoshiichikawa` を明示する
4. The `docs/terms.md` shall 英語で記述する

### Requirement 5: サポート連絡先 (support.md) の内容

**Objective:** As a Google Play Console の審査プロセス および一般ユーザー, I want
サポートページがメール・GitHub Issues・FAQ のプレースホルダを含むこと, so that Play
申請の必須要件であるサポート連絡先の掲載基準を満たし、ユーザーからの問い合わせ経路を
確保する

#### Acceptance Criteria

1. The `docs/support.md` shall 公開連絡先メールアドレスとして文字列 `hitoshi.ichikawa@gmail.com` を含む
2. The `docs/support.md` shall GitHub Issues のリンクとして文字列 `https://github.com/hitoshiichikawa/KeyNest/issues` を含む
3. The `docs/support.md` shall 既知の問題 / FAQ セクションをプレースホルダとして含む（具体的な Q&A は空 or "TBD" 等で可）
4. The `docs/support.md` shall 英語で記述する
5. The `docs/support.md` shall 記載するメールアドレスを `docs/privacy-policy.md` のお問い合わせ先と一致させる

### Requirement 6: GitHub Pages 有効化との整合性

**Objective:** As a リポジトリオーナー, I want 配置されたファイル群が GitHub Pages
`source: branch main / folder /docs` のビルド対象として整合していること, so that
public 化と Pages 有効化操作（GitHub UI 上での 1 度の設定変更）のみで公開サイトが
ビルド可能となる

#### Acceptance Criteria

1. The KeyNest Docs Site shall すべての公開対象 Markdown ファイルを `docs/` ディレクトリ直下に配置する（`docs/specs/` 配下にドキュメントを移動しない）
2. The `docs/_config.yml` shall GitHub Pages の Jekyll ビルドが成立する最小限の YAML キー（`title` / `theme`）を含む
3. If repository が private のままで GitHub Pages を enable できない場合, the KeyNest Docs Site shall 本 Issue のスコープ外として扱い、ファイル配置のみで完了とする
4. The Issue Implementation shall リポジトリ設定（GitHub Pages 有効化操作）を Claude が自動で行わない（人間が GitHub UI 上で実施する）

### Requirement 7: 既存資産への非侵襲性

**Objective:** As a PR レビュアー, I want 本 chore が Android アプリコード・テスト・
ビルド設定・既存 `docs/specs/` 配下に一切の変更を加えないこと, so that ドキュメント
追加に伴う機能回帰・既存仕様書の改変が発生していないことを機械的に確認できる

#### Acceptance Criteria

1. When PR diff を確認したとき, the KeyNest Repository shall `app/` 配下のソース・リソース・Gradle 設定に変更を含まない
2. When PR diff を確認したとき, the KeyNest Repository shall `docs/specs/` 配下の既存仕様書ファイルに変更を含まない（本 Issue 用の新規ディレクトリ追加のみ許容）
3. When PR diff を確認したとき, the KeyNest Repository shall `README.md` への変更を含まない
4. When PR diff を確認したとき, the KeyNest Repository shall `LICENSE` への変更を含まない
5. The KeyNest Repository shall `docs/_config.yml` 以外の Jekyll 関連ファイル（`Gemfile` / `_layouts/` / `_includes/` 等）をリポジトリ直下および `docs/` 配下に追加しない

## Non-Functional Requirements

### NFR 1: 文言の法的位置づけ

1. The KeyNest Docs Site shall 公開する privacy-policy / terms / support の文面を、Play 申請前に人間によるレビュー・調整を前提とした **テンプレ** として扱う
2. The KeyNest Docs Site shall 法的に正確な文面の最終化を本 Issue のスコープ外とする

### NFR 2: 公開 URL の整合性

1. The KeyNest Docs Site shall 公開予定 URL `https://hitoshiichikawa.github.io/KeyNest/` の配下から各 sub page（privacy-policy / terms / support）に Jekyll のデフォルトルーティングで到達可能な状態を維持する

## Out of Scope

- 法的に正確な文面の最終化（テンプレベースで起票、レビュー時に人間が文言調整する）
- カスタムドメイン取得・設定
- アクセス計測（Google Analytics 等）の導入
- 多言語化（英語以外の言語ページ追加）
- GitHub Pages 機能そのものの有効化操作（リポジトリ設定変更は GitHub UI 上で人間が実施 / Requirement 6.3 のフォールバック適用）
- README からのリンク追加（Issue 本文 Requirement 6 で別 Issue 許容と明示されているため本 Issue では対象外）
- 既存 Android アプリコード・テスト・ビルド設定への変更
- `docs/specs/` 配下の既存仕様書ファイルへの変更
- Google Play Console 上のアプリ登録・内部テスト配信・ストア掲載情報入力
- リポジトリの public 化操作そのもの（人間が GitHub UI 上で実施）
- Jekyll の `Gemfile` / `_layouts/` / `_includes/` 等カスタマイズファイルの追加

## Open Questions

- GitHub Pages の有効化タイミング: リポジトリ public 化と連動するため、本 Issue ではファイル配置のみで完了とする（Requirement 6.3 のフォールバックで吸収）。public 化および Pages 有効化のスケジュールは別途人間が判断する
