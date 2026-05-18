# Requirements Document

## 1. 目的・背景

Phase 1 (Issue #66 / PR #69 merged) で `Credential` に `customFields` を導入したが、
**既存 credential を編集する画面 (`Mode.Edit`) では customField の表示・追加・編集・
削除がいずれも不可** という状態にある。`CredentialEditViewModel.load()` が `Mode.Edit`
遷移時に `CustomFieldsState(rows = emptyList(), editable = false)` をセットして
セクション全体を読み取り専用にしているため:

- 既存 customField の `rows` が空表示となり、ユーザーは登録済みの内容を確認できない
- 「フィールド追加」ボタンと既存行の編集 UI が disable され、訂正・削除手段がない
- 結果として「credential を一度削除して新規登録し直す」しか実用的な手段がない

Phase 1 ではこの暫定挙動を Phase 2 (#67) もしくは Phase 1.5 への申し送りとしたが、
Phase 2 のスコープでも対応されないことが確定したため、本 Issue (#73) を Phase 1.5
として独立に処理し、Mode.Edit でも customField の表示・編集・削除を可能にする。

なお Phase 2 (#67) の設計 §8.5 は「`editable == true` を suggest UI の表示ゲートと
する」設計であり、本 Issue で `Mode.Edit` でも `editable = true` を許すと Phase 2 の
サジェスト UI が Mode.Edit でも自動的に動作する。これは Phase 2 設計上の想定挙動で
ある（本 Issue の Requirement 6 で担保）。

## 2. スコープ

### 対象

| 対象 | 変更内容 |
|---|---|
| `CredentialEditViewModel.load()` | `Mode.Edit` 遷移時に既存 `customFieldsCiphertext` / `customFieldsIv` を復号して `CustomFieldsState.rows` に展開し、`editable = true` に切り替える |
| `CredentialEditViewModel.addCustomFieldRow()` / `removeCustomFieldRow()` / `updateCustomFieldKey()` / `updateCustomFieldValue()` | `Mode.Edit` でも有効化（既存 `editable` ガードはそのまま再利用、`editable = true` ならば全 reducer が動く） |
| `CredentialEditViewModel.save()` | `Mode.Edit` でも編集後の `customFields` を `UpdateCredentialInput.customFields` に詰めて永続化（現状の「`editable == false` ならば `null` を渡して既存 ciphertext を温存する」分岐は維持） |
| `CredentialEditActivity` | 既存 customField を初期表示し、編集・削除 UI を enable する（既存 `renderCustomFields()` が `editable` フラグを尊重しているため UI 側は ViewModel の State 変化に追従するだけで足りる想定） |

### Out of Scope

- Phase 2 (Issue #67) detected_fields サジェスト機能本体への変更（本 Issue は前提条件の解消のみ。サジェスト UI 側のコードは変更しない）
- customField の暗号化方式・鍵管理の変更（既存 AES-GCM / Android Keystore を維持）
- customField の並び順カスタマイズ UI（別 Issue。本 Issue では「ロード時に DB 格納順、編集中は表示順を維持」とする）
- value 入力欄の行単位 visibility トグル（Phase 1 確認事項 Q3 と同じく、本 Issue でも導入しない）
- 既存 username / password / label の編集挙動への変更（password が `Mode.Edit` で空欄スタートする現状挙動は維持）
- Phase 3 (`AutofillFieldHeuristics` への日本語 hint / 一般的 resourceId pattern 追加) との関連変更

## 3. 用語定義

| 用語 | 定義 |
|---|---|
| Mode.Edit | `CredentialEditViewModel.Mode.Edit`。既存 credential を編集する状態。`load(credentialId)` が成功し `EncryptedCredentialRecord` が解決された遷移先 |
| Mode.New | 新規 credential を作成する状態 |
| customField | Phase 1 で導入した `fieldKey: String` + `value: String` のペア。1 credential あたり最大 10 件（`MAX_CUSTOM_FIELDS = 10`） |
| editable | `CustomFieldsState.editable` フラグ。`true` のとき UI 上で行追加・編集・削除が許容される |
| 既存行 | `Mode.Edit` 遷移時に DB から復号して展開された customField 行（編集中に追加された行と区別するため） |
| 復号セッション | 編集画面に遷移するために通過した既存 biometric / device credential unlock のスコープ。本 Issue では「username が plaintext で参照できる地点と同じセッション」と定義する |

## 4. ユーザーストーリー

1. **会員番号を後から追加したい既存ユーザー**: ある業務アプリを Phase 1 リリース前に登録しており、customField を持たない状態で保存されている。Phase 1.5 適用後は当該 credential を編集画面で開き、「フィールド追加」から会員番号を追記できる
2. **登録ミスを訂正したいユーザー**: 初回登録時に `fieldKey` を typo した、もしくは value を誤入力した。編集画面で対象行を直接修正して保存できる
3. **不要 customField を削除したいユーザー**: アプリの認証フォームが変更され、もはや使わない customField が残っている。編集画面で行削除ボタンを押して save すると DB から除去される
4. **既存 customField を確認したいユーザー**: どの fieldKey が登録済みかを編集画面で一覧確認できる（現状は空表示）

## 5. 機能要件 (EARS 形式)

### Requirement 1: Mode.Edit での customField 表示

#### Acceptance Criteria

1. When ユーザーが既存 credential を編集画面で開いたとき, the CredentialEditViewModel shall 対象 credential の `customFieldsCiphertext` / `customFieldsIv` を復号し、`CustomFieldsState.rows` に DB 格納順で展開する
2. The CustomFieldsState shall `Mode.Edit` においても `editable = true` を保持する
3. While `Mode.Edit` で `rows` が空（DB に customField が 0 件）の状態, the UI shall 既存 `Mode.New` と同じく「フィールド追加」ボタンのみを表示する
4. When `rows` に既存 customField が展開されているとき, the UI shall 各行の `fieldKey` と `value` を入力可能なテキストフィールドとして表示する
5. If `customFieldsCiphertext` が長さ 0 のとき（Migration_2_3 直後で未 re-save の credential）, the CredentialEditViewModel shall `rows = emptyList()` で `editable = true` に遷移する（空 BLOB == 空 customFields 規約を踏襲）

### Requirement 2: 編集操作の解放

#### Objective

As a 既存 credential 編集中のユーザー, I want customField 行の追加・編集・削除を `Mode.New` と同じ操作感で行うこと, so that 訂正・追記・削除のたびに credential を作り直さなくて済む

#### Acceptance Criteria

1. When ユーザーが `Mode.Edit` で「フィールド追加」ボタンを押したとき, the CredentialEditViewModel shall 既存の `addCustomFieldRow()` を実行して新規行を append する
2. When ユーザーが既存行の `fieldKey` を編集したとき, the CredentialEditViewModel shall 該当行の `fieldKey` を更新する
3. When ユーザーが既存行の `value` を編集したとき, the CredentialEditViewModel shall 該当行の `value` を更新する
4. When ユーザーが行の削除ボタンを押したとき, the CredentialEditViewModel shall 該当行を `rows` から除去する
5. The CredentialEditViewModel shall `MAX_CUSTOM_FIELDS = 10` の上限を `Mode.Edit` でも維持し、11 件目の追加ボタンを disable する（既存ロード分 + 新規追加分の合算で判定）
6. While `Mode.Edit` の編集中, the UI shall 行操作のレスポンス（`rows` 更新の State 反映）を `Mode.New` と同等の体感速度で提供する

### Requirement 3: 保存時の永続化

#### Acceptance Criteria

1. When ユーザーが `Mode.Edit` で保存したとき, the save action shall 編集後の `customFields` 全件を `UpdateCredentialInput.customFields` に詰めて update use case を呼ぶ
2. When save が成功したとき, the persistence layer shall 削除された customField の値を DB から除去する（編集後の `customFields` をそのまま新しい暗号文として再生成する）
3. If `fieldKey` が空白のみの行が存在するとき, the save action shall その行を silent drop する（Phase 1 Req 3.4 と同一挙動）
4. When `Mode.Edit` で customField を 1 件も持たない状態で save したとき, the save action shall 空の `List<CustomField>` を update に渡し、結果として `customFieldsCiphertext` が空 BLOB（または空リストを表す ciphertext）相当となる
5. The save action shall `Mode.Edit` 起動から save までの間に customField 編集が一切行われていない場合でも、復号 → 再暗号化 → 永続化のラウンドトリップ後に元と意味的に等価な customField 集合を保持する（IV の再生成や JSON シリアライズ差異で意味が変わらないこと）

### Requirement 4: 認証フロー

#### Acceptance Criteria

1. The fix shall 編集画面に遷移するための既存 biometric / device credential unlock 経路（username の plaintext 表示を許可している既存セッション）を変更しない
2. The fix shall customField 復号のために追加の biometric prompt を提示しない（確認事項 1 の推奨案 A に基づく）
3. If 復号セッションが期限切れ等で `customFieldsCiphertext` の復号に失敗したとき, the CredentialEditViewModel shall `State.Error` を emit し、`rows` を空・`editable = false` のままに保つ（編集画面ごと閉じるのではなく、ユーザーに失敗を可視化）
4. The fix shall 復号した customField 値を ViewModel スコープを超えて保持しない（Activity 破棄時に GC 対象となるよう、StateFlow と ViewModelScope に閉じ込める）

### Requirement 5: ログ規約

#### Acceptance Criteria

1. The implementation shall `Mode.Edit` で復号した customField の `value` および `fieldKey` を平文でログ出力しない（Phase 1 NFR 2 / `SafeLogger` 規約を踏襲）
2. The implementation shall デバッグ用途であっても customField 件数のような無害な集計情報のみを許容する
3. If 復号失敗が起きたとき, the implementation shall 例外型・件数等の最小限の情報のみを `SafeLogger.error` で記録し、ciphertext / IV / 平文を一切ログに残さない

### Requirement 6: Phase 2 (#67) との整合性

#### Acceptance Criteria

1. The fix shall Phase 2 (#67) `design.md §8.5` で定義された「`editable == true` ならばサジェスト UI を表示」というゲート条件を変更しない（Mode.Edit で `editable = true` になった結果、Phase 2 のサジェスト UI が自動的に有効化されることを許容する）
2. The fix shall Phase 2 が追加した `detected_fields` 記録経路（`KeyNestAutofillService.onFillRequest` 内の fire-and-forget detection）を変更しない
3. The fix shall Phase 2 の `bindPackageForSuggestions(record.packageName)` が `load()` 内で既に呼ばれている既存実装をそのまま活用し、`Mode.Edit` でもサジェスト UI が `currentPackageName` 解決済み状態で開けるようにする

### Requirement 7: テスト

#### Acceptance Criteria

1. The CredentialEditViewModelTest shall `Mode.Edit` 遷移時に既存 `customFieldsCiphertext` が復号され、`CustomFieldsState.rows` に DB 格納順で展開されることを検証する
2. The CredentialEditViewModelTest shall `Mode.Edit` 遷移後に `editable = true` であることを検証する
3. The CredentialEditViewModelTest shall `Mode.Edit` で行追加・既存行編集・行削除の各 reducer が動作することを検証する
4. The CredentialEditViewModelTest shall `Mode.Edit` で save したときに編集後の `customFields` が `UpdateCredentialInput.customFields` に渡されることを検証する
5. The CredentialEditViewModelTest shall 暗号化往復（plaintext → AES-GCM ciphertext → 復号 → 同一 plaintext）で customField 値が一致することを検証する
6. The CredentialEditViewModelTest shall 復号失敗時に `State.Error` を emit し、`rows` が空・`editable = false` のままになることを検証する
7. The CredentialEditViewModelTest shall 既存 Phase 1 / Phase 2 のテスト（`CredentialEditViewModelCustomFieldsTest` / `CredentialEditViewModelSuggestionTest` を含む）を一切 fail させない

## 6. 非機能要件

### NFR 1: 暗号化と機密性

1. The customField values shall 復号後も RAM 上でのみ保持され、ディスク・SharedPreferences・ログのいずれにも平文で書き込まれない
2. The decryption shall Phase 1 と同じ AES-GCM 鍵（Android Keystore 経由）で実行され、新規鍵を生成しない
3. The implementation shall 復号した customField 集合を編集画面の ViewModel スコープに閉じ込め、Activity 破棄時に GC 対象となるよう既存 ViewModel lifecycle に従う

### NFR 2: 後方互換性

1. The fix shall `Mode.New` の挙動を一切変更しない（既存 `editable = true` 経路を共有するため、追加ロジックは `Mode.Edit` 分岐のみ）
2. The fix shall `customFieldsCiphertext` が空 BLOB の credential（Migration_2_3 直後で未 re-save）を読み込んだ場合に `rows = emptyList()` / `editable = true` で正常に開き、save 時には空リストを再暗号化して書き戻す（明示的に save を要求された場合のみ DB を更新）
3. The fix shall 既存 `UpdateCredentialInput.customFields` が `null` の場合「既存 ciphertext を温存する」セマンティクスを変更しない（save パスに到達した場合は常に非 null を渡し、明示的に上書きする）

### NFR 3: 性能

1. While `Mode.Edit` の `load()` 中, the CredentialEditViewModel shall 復号処理を非同期で実行し、UI スレッドをブロックしない
2. The decryption shall customField 10 件分の AES-GCM 復号 + JSON パースを編集画面の遷移体感速度（既存 username/label 表示と同等のオーダー）で完了させる

### NFR 4: 可観測性

1. The implementation shall `SafeLogger.info` で「`Mode.Edit` ロード成功」「customField 件数（数値のみ）」を出力可能とする（値・鍵は含めない）
2. If 復号失敗が起きたとき, the implementation shall `SafeLogger.error` で例外クラス名と最小限のコンテキストのみを記録する

### NFR 5: 一貫性

1. The CustomFieldsState reducer 群 (`addCustomFieldRow` / `removeCustomFieldRow` / `updateCustomFieldKey` / `updateCustomFieldValue`) shall `Mode.New` / `Mode.Edit` で完全に同一のコードパスを通る（モード分岐を reducer 内に持たず、`editable` フラグだけで制御）

## 7. データ移行戦略

### 移行不要

本 Issue は **Room schema を変更しない**。Phase 1 (#66) で導入された `customFieldsCiphertext` / `customFieldsIv` 列をそのまま読み書きするのみで、新規列追加や migration の発生はない。

### 既存データの取り扱い

- Phase 1 リリース時の `Migration_2_3` で初期化された credential（customFieldsCiphertext が空 BLOB）は、本 Issue 適用後に編集画面で開くと「customField 0 件」として表示される（Requirement 1.5）
- ユーザーが「フィールド追加」→ save を行ったタイミングで、初めて非空の ciphertext が書き戻される

## 8. テスト要件

Issue 受入基準 5.1 - 5.4 を §5 Requirement 7 として包含。加えて以下の前提を満たすこと。

- 既存テスト（`CredentialEditViewModelTest` / `CredentialEditViewModelCustomFieldsTest` / `CredentialEditViewModelSuggestionTest` / Migration_1_2_Test / Migration_2_3_Test / FillResponseBuilder 系）が一切 fail しないこと
- `Mode.Edit` での復号 → 編集 → save → 再復号のラウンドトリップ test を 1 件以上含めること
- 「ロード時に復号失敗 → `State.Error` & `editable = false`」の異常系 test を 1 件以上含めること

## 9. 確認事項（Open Questions）

Issue #73 本文の確認事項を以下に列挙する。各項について本要件で採用する **推奨案を明示**し、PR レビュー時に人間が up/down で確定できる形にしている。

### Q1. customField 復号の認証方針

**質問**: `Mode.Edit` 遷移時に既存 customField を復号するための authentication をどう扱うか。

| 案 | 内容 | UX 影響 |
|---|---|---|
| **A（推奨）** | 編集画面 load 時に復号 — username が plaintext で参照できる既存の復号セッションと同一スコープで customField も復号 | 追加 biometric prompt なし、username / label と同じ感覚 |
| B | 別 biometric unlock を要求 — customField 表示時に biometric prompt | 多重 prompt で UX 悪化、ただしセキュリティは強化 |

**本要件での採用**: **案 A** を Requirement 4.1 / 4.2 として明文化する。

**採用理由**:
- 編集画面に遷移する時点で既に biometric / device credential unlock が通っており、`EncryptedCredentialRecord` の username（plaintext）にアクセス可能な状態にある。customField 値も同じ機密性レベルで扱うのが UX 整合的
- Phase 1 実装コメントの "extra biometric unlock" は Phase 1 時点の暫定判断であり、Phase 1.5 として本 Issue で見直す前提
- 案 B は将来「機密性ラベル付き customField」のような細粒度認証が必要になった時点で再導入可能

### Q2. value 表示の masking 方針

**質問**: `Mode.Edit` で復号済み customField の value を表示する際、password 風 masking (`inputType=textPassword`) を適用するか、plain text で表示するか。

**本要件での採用**: **plain text（Phase 1 Q3 と同じ）** を踏襲する。

**採用理由**:
- Phase 1 (#66) §9 Q3 で「value 入力欄は plain text（`inputType=textPassword` を用いない）」が確定済み。`Mode.Edit` で別方針を採るとモード間で UX が分裂する
- 暗号化・ログ除外は Requirement 5 と NFR 1 で担保
- 行単位 visibility トグルは Phase 1 §2 Out of Scope を踏襲し、本 Issue でも導入しない

### Q3. Phase 2 (#67) との進行順

**質問**: 本 Issue を Phase 2 (#67) より先にマージするか、並行進行するか。

**本要件での採用**: **本 Issue を Phase 2 より先にマージ（または独立に merge 可能）** を推奨する。

**採用理由**:
- Phase 2 design.md §8.5 は「`editable == true` を suggest UI 表示ゲートとする」設計であり、本 Issue で `Mode.Edit` の `editable = true` を許すと **Phase 2 のサジェスト UI が Mode.Edit でも自動有効化** される
- これは Phase 2 設計上の想定挙動（design.md §8.5 「Phase 1.5 / Phase 3 等で編集モード対応が入ったときに、本 Phase 2 のロジックを **そのまま流用** できるよう…」）と整合
- 本 Issue 単体は Phase 2 の存在に依存しない（Phase 2 未 merge の状態でも本 Issue は機能要件を満たせる）
- ただし両 Issue が同時期に走る場合、Phase 2 の `CredentialEditViewModelSuggestionTest` が `Mode.Edit + editable = true` の組み合わせで意図せず通過する可能性があるため、PR レビュー時に Phase 2 テスト群の Mode.Edit ケースが妥当か再確認すること

## 10. 未決事項（architect への申し送り）

設計フェーズ以降で扱う論点を以下に列挙する。本要件書では実装方針を決定しない。

1. **復号の実行レイヤ**: `CredentialEditViewModel.load()` 内で直接復号するか、新規 use case（例: `LoadCredentialWithCustomFieldsUseCase`）を切るかは architect が判断
2. **JSON デコーダの共通化**: Phase 1 で導入した「`List<CustomField>` ⇔ JSON」のシリアライザを `Mode.Edit` 復号パスでもそのまま再利用するか、復号専用の薄い wrapper を切るかを設計時に決定
3. **空 BLOB ハンドリング**: Requirement 1.5 / NFR 2.2 に従い、空 ciphertext を復号せず即座に空リスト扱いとする分岐をどのレイヤ（ViewModel / use case / cipher 層）に置くかを設計時に決定
4. **`UpdateCredentialInput.customFields` の null セマンティクス維持**: NFR 2.3 で「save パスに到達した場合は常に非 null を渡す」と方針を示しているが、現行 ViewModel の `if (_customFields.value.editable) effectiveCustomFields else null` 分岐をどう変えるか（`editable = true` が `Mode.Edit` でも成立するため、null パスは事実上消える）の整理は architect 領分
5. **既存行と新規追加行の区別**: 「ロード時に展開された行」と「ユーザーが追加した行」を区別する必要があるか（例: row ID の付与戦略、削除時の UX 差別化など）を設計時に検討
6. **復号失敗時のリトライ UX**: Requirement 4.3 で「`State.Error` を emit して editable = false のまま」と最低限を規定したが、ユーザー向けに「再試行」「画面を閉じる」のどちらを推奨アクションとするかは設計判断

## 11. 関連 Issue / 後続フェーズ

- **Phase 1 (#66 / PR #69 merged)**: customField の導入。本 Issue が解消する制約 (`Mode.Edit` で `editable = false`) を導入した PR
- **Phase 2 (#67 / PR #70)**: detected_fields サジェスト。本 Issue の `editable = true` 解放により Phase 2 サジェスト UI が `Mode.Edit` でも自動有効化される（Phase 2 design §8.5 参照）
- **Phase 3（別 Issue）**: `AutofillFieldHeuristics` への日本語 hint / 一般的 resourceId pattern 追加。本 Issue とは独立に並行進行可能
- **関連 Issue #14**: AdvancedDetails / credential ID 表示。本 Issue は AdvancedDetails の挙動を変更しない

## 12. 制約

1. The Issue Implementation shall 既存テスト（unit / instrumented を含む）を一切 fail させない
2. The Issue Implementation shall `develop` / `main` ブランチに直接 push しない（feature branch + PR レビュー経由）
3. The requirements.md shall 実装コードを含まない。クラス名・メソッド名への言及は既存実装の同定に必要な最小限のみとする
4. The Issue Implementation shall Phase 1 (#66) で確定した暗号化レイヤ（AES-GCM / Android Keystore）と JSON シリアライザを変更しない
5. The Issue Implementation shall Phase 2 (#67) の detection / suggestion ロジックを変更しない（前提条件の解消のみ）
6. The Issue Implementation shall Credential Manager API 経路に変更を加えない（Phase 1 と同じスコープ外扱い）
7. The Issue Implementation shall Room schema を変更しない（migration 不要）
