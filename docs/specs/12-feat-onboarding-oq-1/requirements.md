# Requirements Document

## Introduction

PR #7 で取り込まれた design mock (`screens-2.jsx`) には Onboarding 画面のプログレスドット表現が
含まれていたが、現行の Onboarding 相当画面は `AutofillEnableActivity` の単一画面で完結している。
このギャップは Open Question OQ-1（Onboarding を複数ステップ化するかどうか）として MVP spec の
スコープ外に切り出され、決定待ちのまま残されていた。

本 Issue（#12）では、リポジトリオーナーが Issue コメントで **案 A（単一画面継続）** を選択したため、
OQ-1 を「Closed: 案 A」として確定する。本 Issue は実装変更を伴わないドキュメンテーション中心の対応で
あり、Onboarding 周辺の挙動・UI・多言語化方針は現状を温存する。同時に、将来の意図せぬ多段化を
防ぐため「Onboarding は単一画面である」ことを EARS 形式の AC として固定する。

## Goals / Non-Goals

### Goals

- OQ-1 の確定（案 A: 単一画面継続）を本 spec ディレクトリに記録する。
- Onboarding が単一画面で完結することを EARS 形式の AC で固定し、将来の意図せぬ多段化を防ぐ。
- MVP spec（`docs/specs/1--easykeynest-mvp-packagename-autofill/`）の Requirement 6（Autofill 有効化導線）
  に対するカバレッジを本 Issue で低下させない。

### Non-Goals

- 複数ステップ Onboarding（案 B）の実装。
- プログレスドット UI コンポーネントおよび関連画面遷移ロジックの実装。
- `AutofillEnableActivity` の振る舞いの変更（既存挙動を温存）。
- 多言語化ポリシーの変更（現状の ja_JP 既定 + `values-en/` 構成を維持）。
- 既存 MVP spec (`docs/specs/1--easykeynest-mvp-packagename-autofill/requirements.md`) の
  本文書き換え（本 spec から参照するに留める）。

## Requirements

### Requirement 1: Onboarding は単一画面で完結する

**Objective:** As a 業務端末利用者, I want Onboarding が単一画面で完結すること, so that 初回セットアップで余計なステップを踏まずに Autofill 設定へ到達できる

#### Acceptance Criteria

1.1. The アプリ起動時の Onboarding 動線 shall `AutofillEnableActivity` 単一画面で完結し、複数ステップに分割されない。

1.2. The Onboarding 関連画面 shall プログレスドット UI を含まない。

1.3. When ユーザーが初回起動から Autofill 設定完了までを進めるとき, the アプリ shall 中間ステップ画面（ようこそ画面・プライバシー方針画面・初回登録誘導画面など）を Onboarding 動線に挿入しない。

### Requirement 2: OQ-1 の確定をドキュメントに記録する

**Objective:** As a 本リポジトリの開発者・レビュワー, I want OQ-1 の決定内容と決定経路を spec として確認できること, so that 将来の判断逆転時に意思決定の根拠と影響範囲を追跡できる

#### Acceptance Criteria

2.1. The 本 spec ディレクトリ (`docs/specs/12-feat-onboarding-oq-1/`) shall OQ-1 の決定結果（案 A: 単一画面継続）を明示的に記録する。

2.2. If 将来 Onboarding の多段化（案 B）が必要になった場合, the 開発者 shall 本 OQ-1 の決定を覆す新 Issue を起票してから着手する。

2.3. The 本 spec の Introduction shall 決定経路（Issue #12 上で Issue オーナーが案 A を選択した事実）を記録する。

### Requirement 3: 既存挙動を変更しない

**Objective:** As a 業務端末利用者, I want 本 Issue の対応によって既存の Autofill 有効化導線の挙動が変わらないこと, so that MVP で達成済みのユーザー体験が退行しない

#### Acceptance Criteria

3.1. The `AutofillEnableActivity` の振る舞い shall 本 Issue の対応によって変更されない（初期表示・有効化操作トリガー・Autofill 設定画面遷移ロジックは温存される）。

3.2. The MVP spec Requirement 6（Autofill Service 有効化導線）に対するカバレッジ shall 本 Issue の対応により低下しない。

3.3. When 本 Issue 対応の前後で同一の初回起動シナリオを実行するとき, the アプリ shall 同等の Onboarding 表示および同等の Autofill 設定画面遷移挙動を提示する。

### Requirement 4: 多言語化ポリシーは現状維持

**Objective:** As a 業務端末利用者, I want 本 Issue の対応によって表示言語と文言が変わらないこと, so that 既存利用者が違和感なく継続利用できる

#### Acceptance Criteria

4.1. The 多言語化リソース構成 shall 現行の ja_JP 既定 + `values-en/` 構成を維持する。

4.2. The Onboarding 関連 strings リソース shall 本 Issue の対応によって追加・削除・改名されない。

## Non-Functional Requirements

### NFR 1: トレーサビリティ

1.1. The 本 spec ドキュメント shall Issue #12 と Open Question OQ-1 を相互参照可能な記述で結びつける（Issue 番号と OQ ID の双方を本文に明示する）。

1.2. The 本 spec ドキュメント shall MVP spec の Requirement 6（Autofill Service 有効化導線）への参照を保持し、本 Issue 対応後も MVP 要件の所在が辿れる状態を維持する。

## Out of Scope

- 案 B（複数ステップ Onboarding）の設計および実装。将来必要になった場合は別 Issue で扱う。
- プログレスドット UI コンポーネントの実装・スタイル定義。
- Onboarding 文言の変更・追加翻訳（ja_JP / en 以外への展開を含む）。
- `AutofillEnableActivity` 以外の画面の Onboarding 化（例: ようこそ画面・チュートリアル画面の新設）。
- 既存 MVP spec (`docs/specs/1--easykeynest-mvp-packagename-autofill/`) 本文の書き換え。

## Open Questions

- 確認事項 1: Issue 本文には「`requirements.md §2.2` で Out of Scope 宣言済み」という記述があるが、
  実際の MVP requirements.md (`docs/specs/1--easykeynest-mvp-packagename-autofill/requirements.md`)
  §2.2 は credential 保存時の署名ハッシュ未保存ケースを扱っており、Onboarding には言及していない。
  本 spec ではこの参照は引用しない方針とした。Issue 起票者の認識違いと思われるが、引用すべき正しい
  参照先があれば人間にエスカレーションする。
- 確認事項 2: 既存 MVP spec の OQ-1 セクションを本 Issue 対応で更新するか（例: 「Closed (案 A)」と
  記載する追記を行うか）は、PM フェーズで確定させず Developer / 人間判断に委ねる。本 spec の存在
  自体が確定の記録となるため、MVP spec 本文書き換えは Non-Goals に含めている。
