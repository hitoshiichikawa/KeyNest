# Requirements Document

## 冒頭メタ

| 項目 | 値 |
|---|---|
| **Issue** | #102 feat(passkey): PassKey 単位の個別管理 UI (rename / delete) |
| **Parent (umbrella)** | #89 feat(passkey): Android Credential Manager 経由の passkey プロバイダ対応 |
| **Phase** | **Phase 5** (umbrella #89 サブ分割案 6「PassKey 単位の rename / 削除 UI」に対応) |
| **Depends on** | #91 (Room migration / `PasskeyEntity` / `PasskeyDao` / `PasskeyRepository`、merged) / #101 (一覧 UI 統合、merged) |
| **(参考) 先行 Phase** | #90 (Service 骨組み) / #99 (登録セレモニー) / #100 (認証セレモニー) / #103 (設定画面 / OS 設定導線) |
| **作業ブランチ** | `claude/issue-102-design-feat-passkey-passkey-ui-rename-delete` |
| **PR base** | `develop` |
| **表記ポリシー** | umbrella #89 確認事項 3 に従い、UI ラベル / KDoc / コメント / ログメッセージで PassKey 機能に言及する箇所は **「PassKey」** で統一 (「passkey」「Passkey」「passKey」「パスキー」は禁止)。リソース ファイル名は OS 制約により小文字 (`ic_passkey_*` 等) で可 |

## Introduction

KeyNest は #91 で PassKey の永続化層、#99 で登録セレモニー、#100 で認証セレモニー、
#101 で一覧 UI 統合まで到達し、PassKey の保管 / 認証 / 一覧表示が動作する状態に
ある。しかし保管された個々の PassKey をユーザーが KeyNest 上で **管理 (rename /
delete) する手段は未提供** であり、登録した PassKey の displayName を後から変えたい、
不要になった PassKey を KeyNest から消したい、といったユースケースに応えられない。

本 Issue (#102 = umbrella #89 分割案 6 = **Phase 5**) は `PasskeyDetailActivity` を
新規追加し、(1) PassKey の主要メタデータの表示、(2) `displayName` の編集 / 保存、
(3) 削除確認ダイアログを介した PassKey 削除 (RP 側の登録は残る旨を明示) までを
1 PR の到達点とする。一覧画面 (#101) で PassKey 行をタップしたとき、本 Issue で
追加する `PasskeyDetailActivity` に遷移する経路を確立する。

PassKey の **暗号化 private key / userHandle / Keystore alias / signCount 等の
内部識別子は UI に表示しない** (umbrella #89 のセキュリティ境界ポリシー / #101 NFR 2
継承)。export ボタン / 同期 / 移行系機能は umbrella #89 で確定済みの「エクスポート
禁止」方針に従い本 Issue 範囲外とする。

## Requirements

### Requirement 1: PassKey 詳細表示

**Objective:** As an エンドユーザー, I want 一覧画面で選んだ PassKey の主要メタデータと KeyNest 上の別名 (displayName) を 1 画面で確認できること, so that どの RP / どのユーザー名で登録したかを思い出し、必要に応じて編集 / 削除に進める

#### Acceptance Criteria

1. When ユーザーが `CredentialListActivity` の PassKey 行をタップしたとき, the app shall `PasskeyDetailActivity` を `credentialId` 付きで起動する
2. The `PasskeyDetailActivity` shall 起動時に `PasskeyRepository.findByCredentialId(credentialId)` を呼び、対象 PassKey のメタデータを取得する
3. The `PasskeyDetailActivity` shall 取得したメタデータのうち `rpId` / `rpDisplayName` / `userName` / `userDisplayName` / `displayName` / `createdAt` / `lastUsedAt` を画面に表示する
4. The `PasskeyDetailActivity` shall `userHandle` / `encryptedPrivateKey` / `privateKeyIv` / `keyAlias` / `signCount` / `isDiscoverable` を画面に表示しない (内部識別子の漏洩防止)
5. The `PasskeyDetailActivity` shall AAGUID (`2a56cf86-8332-4829-9f2a-e9a4adbc7abe`) を画面に表示しない (#103 NFR 2.2 と整合)
6. Where `rpDisplayName` が NULL または空文字列のとき, the PasskeyDetailActivity shall フォールバックとして `rpId` を表示する
7. Where `userDisplayName` が NULL または空文字列のとき, the PasskeyDetailActivity shall フォールバックとして `userName` を、それも NULL / 空のときは `R.string.passkey_detail_unknown_user` (= 「(ユーザー名なし)」相当) を表示する
8. Where `lastUsedAt` が NULL のとき, the PasskeyDetailActivity shall ステータス文言として `R.string.passkey_detail_last_used_never` (= 「未使用」相当) を表示する (確認事項 1 で決定済み)
9. The `PasskeyDetailActivity` shall `createdAt` / `lastUsedAt` を端末ロケールに従った日付文字列で表示する (`DateUtils` または既存 `CredentialEditActivity` と同方式)
10. If `findByCredentialId` の戻り値が `null` (= 既に削除されている / 不正な引数) のとき, the PasskeyDetailActivity shall `R.string.passkey_detail_not_found` (= 「PassKey が見つかりません」相当) を Snackbar で表示し、画面を `finish()` で閉じる

### Requirement 2: displayName の編集 / 保存

**Objective:** As an エンドユーザー, I want KeyNest 上の別名 (displayName) を後から自由に変更できること, so that 登録時の自動採取テキスト (RP 提供値) と区別して、自分が思い出しやすい名前を付け直せる

#### Acceptance Criteria

1. The `PasskeyDetailActivity` shall `displayName` 編集用の EditText を表示し、初期値として `PasskeyEntity.displayName` (NULL のときは空文字列) を流し込む
2. The `PasskeyDetailActivity` shall `rpId` / `userHandle` / `signCount` / `createdAt` 等の他フィールドを **編集不可** (read-only / `View.GONE` 等) として扱う
3. When ユーザーが「保存」ボタンをタップしたとき, the PasskeyDetailActivity shall 入力値を `trim()` した上で空文字列でないことを検証する
4. If 入力値の `trim()` 結果が空文字列のとき, the PasskeyDetailActivity shall エラー文言 `R.string.passkey_detail_displayname_required` (= 「KeyNest 上の別名を入力してください」) を EditText にエラー表示し、保存処理を実行しない
5. When 入力値の検証が成功したとき, the PasskeyDetailActivity shall `PasskeyRepository.update(passkey.copy(displayName = trimmed))` 相当の更新 API を呼び出す
6. When `PasskeyRepository.update(...)` が正常完了したとき, the PasskeyDetailActivity shall `R.string.passkey_detail_saved` (= 「保存しました」相当) を Snackbar で表示し、`finish()` で一覧画面に戻る
7. If `PasskeyRepository.update(...)` が例外をスローしたとき, the PasskeyDetailActivity shall `R.string.passkey_detail_save_failed` (= 「保存に失敗しました」相当) を Snackbar で表示し、画面を維持する
8. The `PasskeyDetailActivity` shall 保存処理を `lifecycleScope` / `viewModelScope` 上で非同期に実行し、main thread を blocking しない
9. While `PasskeyRepository.update(...)` 実行中, the PasskeyDetailActivity shall 保存ボタンを `isEnabled = false` とし、二重タップ送信を防ぐ
10. The PassKey の `displayName` 更新 shall `credentialId` / `rpId` / `userHandle` / `encryptedPrivateKey` / `privateKeyIv` / `keyAlias` / `signCount` / `isDiscoverable` / `createdAt` / `lastUsedAt` を変更しない (displayName 列のみの更新)

### Requirement 3: PassKey の削除

**Objective:** As an エンドユーザー, I want 不要になった PassKey を KeyNest から確実に削除でき、その操作が RP 側の登録に影響しないことを事前に理解できること, so that KeyNest 上の整理操作で意図せず RP ログインを失う事故を起こさない

#### Acceptance Criteria

1. The `PasskeyDetailActivity` shall 「削除」ボタンを表示する
2. When ユーザーが「削除」ボタンをタップしたとき, the PasskeyDetailActivity shall 確認ダイアログ (`AlertDialog` 系) を表示し、削除処理は即時実行しない
3. The 確認ダイアログ shall 本文に `R.string.passkey_detail_delete_confirm_message` (= 「KeyNest から削除します。RP 側の登録は残ります。」相当) を表示し、RP 側で別途 PassKey 解除操作が必要である旨を明示する
4. The 確認ダイアログ shall 「削除」「キャンセル」の 2 ボタンを持ち、destructive アクション (「削除」) には Material 系の警告色 (`colorError` / `kn_danger` 等の既存トークン) を適用する
5. When ユーザーが確認ダイアログで「削除」を選んだとき, the PasskeyDetailActivity shall `PasskeyRepository.delete(credentialId)` を `lifecycleScope` / `viewModelScope` 上で呼び出す
6. The `PasskeyRepository.delete(credentialId)` の内部順序 shall **(a) Room トランザクション内で DB 行を削除 → (b) DB 削除コミット後に Keystore alias `keynest_passkey_<credentialId>` を破棄** とする (確認事項 2 / Option A: DB first で決定済み)
7. If Keystore alias 破棄が例外をスローしたとき, the PasskeyRepository.delete shall Room トランザクションをロールバックし、DB 行を削除前の状態に戻す (補償ロジック不要のシンプル構成)
8. When DB 削除 + Keystore alias 破棄が両方成功したとき, the PasskeyDetailActivity shall `R.string.passkey_detail_deleted` (= 「削除しました」相当) を Snackbar で表示し、`finish()` で一覧画面に戻る
9. If `PasskeyRepository.delete(credentialId)` が例外を伝播してきた場合 (DB ロールバック後), the PasskeyDetailActivity shall `R.string.passkey_detail_delete_failed` (= 「削除に失敗しました」相当) を Snackbar で表示し、画面を維持する (PassKey は削除前の状態のまま存在)
10. The 削除処理 shall main thread を blocking しない (`Dispatchers.IO` 上で Room / Keystore 操作を実行)
11. While `PasskeyRepository.delete(...)` 実行中, the PasskeyDetailActivity shall 削除ボタン / 保存ボタンを `isEnabled = false` とし、二重操作を防ぐ
12. When 削除が完了し一覧画面 (`CredentialListActivity`) に戻ったとき, the CredentialListActivity shall 既存 Flow 観測経路 (#101 の `PasskeyRepository.listAll()`) により当該 PassKey 行が自動的にリストから消えていることを反映する (本 Issue 側で明示的な refresh コマンドを送らない)

### Requirement 4: 一覧画面からの遷移

**Objective:** As an エンドユーザー (一覧画面利用), I want PassKey 行をタップしたら個別管理画面に遷移できること, so that #101 で表示された PassKey に対して即座に rename / delete 操作に進める

#### Acceptance Criteria

1. The `CredentialListActivity` shall `CredentialListItem.Passkey` variant のタップ時に、現状の v1 暫定 Snackbar (`credential_list_passkey_tap_v1_message`) を撤去し、`PasskeyDetailActivity` を起動する経路に差し替える
2. The CredentialListActivity shall `PasskeyDetailActivity` を起動する際、`Intent` extra として `credentialId: String` を渡す
3. The CredentialListActivity shall password 行 (`CredentialListItem.Password` variant) のタップ挙動 (= 既存 `CredentialEditActivity` 起動) を変更しない
4. The CredentialListActivity shall 長押し / overflow / 検索 / フィルタ / sort の既存挙動を変更しない
5. The `PasskeyDetailActivity` shall back キー / Up ナビゲーションで一覧画面に戻る (`finish()` のみ、追加の result 引き渡しは不要)

### Requirement 5: 表記ポリシーと文言

**Objective:** As an エンドユーザー, I want 個別管理画面の PassKey 関連表記が他画面 (#101 / #103) と一貫していること, so that 「passkey」「Passkey」「パスキー」のブレで違和感を覚えない

#### Acceptance Criteria

1. The `PasskeyDetailActivity` shall PassKey 機能に言及する文言で **「PassKey」** 表記を用いる (umbrella #89 確認事項 3 確定済み)
2. The `PasskeyDetailActivity` shall タイトル / ラベル / ボタン文言 / Snackbar メッセージ / contentDescription のいずれも、ハードコード文字列ではなく `R.string.passkey_detail_*` 系の string resource を経由して表示する
3. The newly added string resources shall `values/strings.xml` (en) と `values-ja/strings.xml` (ja) の両方に追加し、「PassKey」表記を i18n 不要の固定表記として扱う (#99 / #101 / #103 の `passkey_*` 系既存 key と命名整合)
4. The PassKey detail screen shall エクスポート / バックアップ / 同期 / 共有 機能の UI 要素を持たない (umbrella #89 確認事項 7「エクスポート禁止」確定済み / 本 Issue Out of Scope)

### Requirement 6: 既存実装への非干渉

**Objective:** As a メンテナ, I want `PasskeyDetailActivity` 追加が既存の password 編集画面 / 一覧画面 / 設定画面 / Service 系 (`CredentialProviderService`) の挙動を破壊しないこと, so that PassKey 個別管理 UI の追加をきっかけに既存資産を失わない

#### Acceptance Criteria

1. The new screen shall 既存 `CredentialEditActivity` (password 編集) の View ID / クリック挙動 / 永続化経路を変更しない
2. The new screen shall 既存 `CredentialListActivity` の 5 セクション (検索 / フィルタ / sort / mainList / Empty state) の表示順 / レイアウト / View ID を変更しない (追加変更は Requirement 4 で規定する PassKey 行タップ挙動の差し替えのみ)
3. The new screen shall `CredentialProviderService` / `PasskeyAuthActivity` / `KeyNestAutofillService` を変更しない (本 Issue は UI 層と既存 `PasskeyRepository` の **読み出し / 既存 update / 既存 delete API** の活用のみで完結する)
4. The PasskeyRepository.delete 実装 shall 既存呼び出し元 (例: 認証セレモニー側で異常検知時に呼ぶ経路があれば) のシグネチャを変更しない (= signature backward compatible。内部順序を「DB first」に確定するだけ)
5. The new screen shall `compileSdk` / `targetSdk` / `minSdk` / `applicationId` / `namespace` を変更しない
6. The new screen shall DB schema 変更 / migration 追加を伴わない (#91 で `passkeys` テーブル v5 確定済み、本 Issue は schema を変更しない)

### Requirement 7: テスト

**Objective:** As a メンテナ, I want displayName 更新 / 削除 / 確認ダイアログ文言 / ロールバックの主要経路が自動テストで回帰検知できること, so that 後続 Issue 実装中に PassKey 個別管理画面の退行を早期に検知できる

#### Acceptance Criteria

1. The `PasskeyDetailViewModelTest` (拡張 or 新規) shall displayName を空文字列 / 空白のみで保存しようとしたとき、`update` API が呼ばれず、エラー State が公開されることを検証する
2. The PasskeyDetailViewModelTest shall displayName に有効な文字列を渡したとき、`PasskeyRepository.update(...)` が `credentialId` / `rpId` / `userHandle` / `keyAlias` / `signCount` 等の他フィールドを変更せず、`displayName` のみ更新した entity で呼び出されることを検証する
3. The PasskeyDetailViewModelTest shall `PasskeyRepository.delete(credentialId)` 正常完了時に削除完了の Event を発火することを検証する
4. The PasskeyDetailViewModelTest shall `PasskeyRepository.delete(credentialId)` 例外時にエラー文言を伴う Event を発火し、`finish()` 相当の遷移指示を出さないことを検証する
5. The `PasskeyRepositoryTest` (拡張) shall `delete(credentialId)` 内で **DB 削除 → Keystore alias 破棄** の順序で呼ばれることを検証する (mock の call order 検証)
6. The PasskeyRepositoryTest shall Keystore alias 破棄が例外をスローした場合に、Room トランザクションがロールバックされ、DB 行が削除前の状態で残ることを検証する (`findByCredentialId` で取り出せること)
7. The PasskeyRepositoryTest shall `update(entity)` が DB 行の `displayName` のみ更新し、`encryptedPrivateKey` / `privateKeyIv` / `keyAlias` / `signCount` / `createdAt` を変更しないことを検証する
8. The instrumentation / Robolectric test shall 削除確認ダイアログに `R.string.passkey_detail_delete_confirm_message` が表示され、「RP 側の登録は残ります」相当の文言が含まれることを検証する
9. The instrumentation / Robolectric test shall 一覧画面の PassKey 行タップで `PasskeyDetailActivity` が起動し、Intent extra `credentialId` が渡されることを `ShadowActivity.getNextStartedActivity()` または Espresso intent 検証で確認する
10. The 既存 `CredentialListAdapterInstrumentationTest` / `CredentialListViewModelTest` (#101 で追加) shall 本 Issue 変更後も全件 pass し、password 行 / PassKey 行の表示と検索 / フィルタの回帰がないことを確認する

## Non-Functional Requirements

### NFR 1: パフォーマンス / 応答性

1. The `PasskeyDetailActivity` shall `onCreate` から初描画 (詳細メタデータ表示完了) までを 1 秒以内に完了させる (端末 IO 性能を加味して、Room の `findByCredentialId` 1 件取得 + Activity inflate のみで構成)
2. The PasskeyDetailActivity shall 保存 / 削除処理を `Dispatchers.IO` で実行し、main thread blocking で操作不能にしない
3. The PasskeyDetailActivity shall 保存 / 削除中の二重タップを防ぐため、進行中はボタンを `isEnabled = false` にする (Requirement 2.9 / 3.11 と整合)

### NFR 2: セキュリティ / 秘匿性

1. The PasskeyDetailActivity shall 暗号化 blob (`encryptedPrivateKey` / `privateKeyIv`) / `userHandle` / `keyAlias` / `signCount` / AAGUID を画面・ログ・例外メッセージのいずれにも表示しない (#101 NFR 2 / #103 NFR 2 と整合)
2. The PasskeyRepository shall `delete(credentialId)` 失敗時のログ出力で `credentialId` raw value を `info` レベル以上に出さない (`debug` レベルのみ可、#91 NFR 2.4 と整合)
3. The new string resources shall PassKey の暗号鍵 / private key / userHandle 等の機微情報を文字列に埋め込まない
4. The new screen shall エクスポート / 共有 / バックアップ系 UI 要素を持たない (umbrella #89 確認事項 7「エクスポート禁止」と整合)

### NFR 3: 表記統一

1. The new screen の UI ラベル / KDoc / コメント / ログメッセージ shall PassKey 機能に言及する箇所で **「PassKey」** 表記を用いる (umbrella #89 確認事項 3)
2. The new string resources shall ja / en で「PassKey」表記を統一する (i18n 不要の固定表記)

### NFR 4: 既存挙動への非干渉

1. The new screen 追加 shall 既存 `CredentialListActivity.newIntent(context)` / `CredentialEditActivity.newIntent(...)` / `SettingsActivity.newIntent(...)` の公開 API シグネチャを変更しない
2. The new screen 追加 shall 既存 View ID (`text_*` / `btn_*` / `chip_*` / `recycler` / `inputSearch` 等の一覧画面 / 編集画面 / 設定画面で定義済みのもの) を削除 / 改名しない
3. The new screen 追加 shall `compileSdk` / `targetSdk` / `minSdk` / `applicationId` / `namespace` / Room schema version を変更しない (Requirement 6.5 / 6.6 と整合)

## Out of Scope

- **PassKey のエクスポート / バックアップ / 共有 / 同期** — umbrella #89 確認事項 7「エクスポート禁止」確定済み
- **PassKey の RP 側登録解除導線** (RP の管理画面に飛ばす deeplink 等) — 本 Issue は「KeyNest 内の削除」のみを扱い、RP 側操作は確認ダイアログの文言で「別途必要」と明示するに留める
- **displayName 以外のフィールド (rpId / userName / userDisplayName / userHandle / signCount / isDiscoverable / lastUsedAt / createdAt) の編集** — `rpId` / `userHandle` は WebAuthn 仕様上 RP との一意性で固定、signCount は認証セレモニーが管理、その他 RP 提供値は登録時固定が UX として自然なため、本 Issue では `displayName` のみ編集可とする
- **PassKey の複製 / 移行 / 別アカウント紐付け** — KeyNest は単一 vault のみ扱う設計 (umbrella #89)
- **一覧画面 (`CredentialListActivity`) への種別フィルタ chip / sort オプション追加** — #101 の Out of Scope を踏襲、本 Issue でも追加しない
- **Recently used carousel への PassKey 統合** — #101 / 後続 UI 整備 Issue
- **DB schema 変更 / migration 追加** — 本 Issue は UI 層と既存 `PasskeyRepository` API の活用のみで、PassKey 永続化層 (#91) には触らない
- **`CredentialProviderService` / `PasskeyAuthActivity` / `KeyNestAutofillService` 自体の変更** — 本 Issue は UI 層と Repository delete の内部順序確定のみで、Service / Authenticator 系には触らない
- **AAGUID / Keystore alias / signCount の UI 表示** — NFR 2.1 と整合、内部識別子はエンドユーザー画面に出さない
- **README / Privacy Policy / Support ページの PassKey 個別管理機能追記** — umbrella #89 分割案 8 = 別 Issue

## Open Questions

> 本 Issue 本文「Open Questions」2 件は本 requirements 作成段階で全て決定済み。
> Architect / Developer に申し送る未決事項は **なし**。
>
> 参考までに、決定済み事項を以下に明記する (Requirement 本文中の該当 AC に既に反映済み)。

1. **未使用 PassKey の表示文言** (Issue 本文 Open Question 1): **「未使用」** (= `R.string.passkey_detail_last_used_never`) を採用する。これは PM 段階の自律判断 (Issue コメントでの人間からの明示回答なし)。Requirement 1.8 に反映済み。理由: 「履歴なし」「(未使用)」等の候補もあるが、最短かつ意味が明確な「未使用」が #101 / #103 の文言トーン (簡潔・中立) と整合する。日本語固定文言として `values-ja/strings.xml` に置く。英語ロケール (`values/strings.xml`) では `"Never used"` 相当の文言を併設するが、最終的な英語訳は Architect (design.md) に委ねてもよい
2. **Keystore alias 破棄と DB 削除の rollback 粒度** (Issue 本文 Open Question 2): **Option A: DB first** を採用する (人間確定済み)。Requirement 3.6 / 3.7 および Requirement 7.5 / 7.6 に反映済み。Room トランザクション内で DB 行を削除し、コミット後に Keystore alias 破棄を実行。Keystore alias 破棄が失敗した場合は Room トランザクションをロールバックする (補償ロジック不要のシンプル構成)。理由: Keystore 破棄失敗時に DB がコミット済みだと「DB 上は消えたが Keystore alias は残る」状態 (鍵リーク方向) を防げる。逆順 (Keystore 先 → DB 後) だと Keystore 破棄成功 + DB 削除失敗時に「Keystore は消えたが DB に死んだ参照が残る」状態 (復号不能の死亡列) になり、後続セレモニーが落ちる可能性がある。DB first は「失敗したらユーザーから見て何も変わらない」というシンプルな保証を提供する

## 関連 Issue / PR

- **Parent (umbrella)**: #89 feat(passkey): Android Credential Manager 経由の passkey プロバイダ対応
- **Depends on**:
  - #91 feat(passkey): Room migration + PassKey 永続化 (`PasskeyEntity` / `PasskeyDao` / `PasskeyRepository`) — 本 Issue は `findByCredentialId` / `update` / `delete` を活用
  - #101 feat(passkey): 既存 credential 一覧への PassKey 統合 — 本 Issue は #101 で確立した `CredentialListItem.Passkey` 行タップ経路を `PasskeyDetailActivity` 起動に差し替える
- **先行確立済み (Phase 2/3/6、本 Issue では非依存だが用語整合のため参照)**:
  - #90 feat(passkey): `CredentialProviderService` 最小実装 / Manifest 登録
  - #99 feat(passkey): 登録セレモニー (`onBeginCreateCredentialRequest`) — 表記ポリシー「PassKey」確立、Keystore alias 命名 `keynest_passkey_<credentialId>` を確立済み
  - #100 feat(passkey): 認証セレモニー (`onBeginGetCredentialRequest`)
  - #103 feat(passkey): 設定画面に PassKey プロバイダ登録状態と OS 設定への導線を追加
- **後続予定**:
  - umbrella #89 分割案 8: README / Privacy Policy / Support ページの PassKey 関連更新
