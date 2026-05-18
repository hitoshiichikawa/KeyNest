# Requirements Document

## 1. 目的・背景

Phase 1 (Issue #66 / PR #69 merged) で `Credential` に `customFields` を導入したが、
`Mode.Edit` で次の 2 つの編集 UX 制約が残っている。本 Issue (#73 / Phase 1.5) では
両方を同一 Issue として独立に解消する:

1. **customField 編集不可** — `CredentialEditViewModel.load()` が `Mode.Edit` 遷移時に
   `CustomFieldsState(rows = emptyList(), editable = false)` を固定で割り当てるため、
   既存 customField の表示・追加・編集・削除がいずれも不可。ユーザーは
   「credential を一度削除して新規登録し直す」しか実用的な手段がない
2. **password 既存値の確認・編集 UX が貧弱** — Mode.Edit 遷移時に `inputPassword` が空欄で
   開く（実装上 `setText()` を呼ばない設計）。ユーザーは「変更しないなら空欄のまま、
   変更するなら新しい値を入力」という二択しかなく、現在登録されている値を確認できない。
   `UpdateCredentialInput.newPassword: CharArray?` が `null = 既存 ciphertext 温存 /
   non-null = 上書き` というセマンティクスを持つため、空欄送信 = 温存で破壊的変更は
   起きないが、ユーザーは「自分が今まで何を登録していたか」を編集画面から確認できない

なお Phase 1 ではこの暫定挙動を Phase 2 (#67) もしくは Phase 1.5 への申し送りとしたが、
Phase 2 のスコープでも対応されないことが確定したため、本 Issue を Phase 1.5 として処理する。

Phase 2 (#67) の設計 §8.5 は「`editable == true` を suggest UI の表示ゲートとする」設計で
あり、本 Issue で `Mode.Edit` でも `editable = true` を許すと Phase 2 のサジェスト UI が
Mode.Edit でも自動的に動作する。これは Phase 2 設計上の想定挙動である（本 Issue の
Requirement 9 で担保）。

### KeyNest セキュリティモデルとの整合

KeyNest の master AES key (`keynest_aead_v1`) は `setUserAuthenticationRequired(false)` で
構成されており、アプリプロセスから常時 decrypt 可能。biometric 認証は Autofill flow /
DangerZone でのみ要求される。本 Issue でも追加 biometric prompt は要求しない（OS lock
screen 突破を信頼境界とする現行モデルに従う）。

`customField` / `password` ともに load 時に decrypt して memory 上に保持する。既に username
は plaintext で DB 保存されているため、編集画面に到達した時点で同等の機密性レベルの
データが画面に展開される運用は整合的である。

## 2. スコープ

### 対象

| 対象 | 変更内容 |
|---|---|
| `CredentialEditViewModel.load()` | Mode.Edit 遷移時に `customFieldsCiphertext` / `customFieldsIv` を復号して `CustomFieldsState.rows` に展開し `editable = true` に切り替える。**併せて password も復号して `initialPassword` (memory 保持) として State に持たせる** |
| `CredentialEditViewModel.State` (UI state) | 既存 password を **load 時の値** として保持するフィールド（`initialPassword`）を追加。save 時の dirty 判定に使用する |
| `CredentialEditViewModel.addCustomFieldRow()` / `removeCustomFieldRow()` / `updateCustomFieldKey()` / `updateCustomFieldValue()` | `Mode.Edit` でも有効化（既存 `editable` ガードはそのまま再利用、`editable = true` ならば全 reducer が動く） |
| `CredentialEditViewModel.save()` | Mode.Edit でも編集後の `customFields` を `UpdateCredentialInput.customFields` に詰めて永続化（現状の「`editable == false` ならば `null` を渡して既存 ciphertext を温存する」分岐は維持）。**併せて password が `initialPassword` と同値なら `newPassword = null` (温存)、変更されていれば新値で update する dirty 判定を追加** |
| `CredentialEditActivity` | (a) 既存 customField を初期表示し、編集・削除 UI を enable する、(b) 既存 password を `inputPassword.setText(initialPassword)` で初期表示し masking 状態とする、(c) `inputPassword` に focus 取得時に平文表示・focus loss 時に再 masking する `setOnFocusChangeListener` を配線する |
| layout `credential_edit_activity.xml` | password 欄の `endIconMode` 既定値（現状 `password_toggle`）が focus 切替と競合する場合の対応（§9 確認事項 Q1 参照）。layout の機能要件としては変更を最小化し、`endIconMode` の最終形は実装フェーズで設計判断する |

### Out of Scope

- Phase 2 (Issue #67) detected_fields サジェスト機能本体への変更（本 Issue は前提条件の解消のみ。サジェスト UI 側のコードは変更しない）
- customField / password の暗号化方式・鍵管理の変更（既存 AES-GCM / Android Keystore を維持）
- customField の並び順カスタマイズ UI（別 Issue）
- customField value 入力欄の行単位 visibility トグル（Phase 1 確認事項 Q3 と同じく、本 Issue でも導入しない。customField value は plain 表示を維持）
- 既存 username / label の編集挙動への変更
- Phase 3 (`AutofillFieldHeuristics` への日本語 hint / 一般的 resourceId pattern 追加) との関連変更
- 追加 biometric 認証の導入
- password / customField 用の screenshot 防止フラグ等の新規導入

## 3. 用語定義

| 用語 | 定義 |
|---|---|
| Mode.Edit | `CredentialEditViewModel.Mode.Edit`。既存 credential を編集する状態。`load(credentialId)` が成功し `EncryptedCredentialRecord` が解決された遷移先 |
| Mode.New | 新規 credential を作成する状態 |
| customField | Phase 1 で導入した `fieldKey: String` + `value: String` のペア。1 credential あたり最大 10 件（`MAX_CUSTOM_FIELDS = 10`） |
| editable | `CustomFieldsState.editable` フラグ。`true` のとき UI 上で行追加・編集・削除が許容される |
| 既存行 | `Mode.Edit` 遷移時に DB から復号して展開された customField 行（編集中に追加された行と区別する用語） |
| initialPassword | `Mode.Edit` 遷移時に DB から復号した password の **load 時値**。ViewModel の UI state が保持し、save 時の dirty 判定に使用する |
| masking 状態 | `inputPassword` が `PasswordTransformationMethod` を適用された状態（文字がドット等に置換されて表示される） |
| 平文表示 | `inputPassword` が `transformationMethod = null` の状態（テキストがそのまま見える） |
| 復号セッション | 編集画面に遷移するために通過した既存 biometric / device credential unlock のスコープ。本 Issue では「username が plaintext で参照できる地点と同じセッション」と定義する |

## 4. ユーザーストーリー

1. **会員番号を後から追加したい既存ユーザー**: ある業務アプリを Phase 1 リリース前に登録しており、customField を持たない状態で保存されている。Phase 1.5 適用後は当該 credential を編集画面で開き、「フィールド追加」から会員番号を追記できる
2. **登録ミスを訂正したいユーザー (customField)**: 初回登録時に `fieldKey` を typo した、もしくは customField value を誤入力した。編集画面で対象行を直接修正して保存できる
3. **不要 customField を削除したいユーザー**: アプリの認証フォームが変更され、もはや使わない customField が残っている。編集画面で行削除ボタンを押して save すると DB から除去される
4. **既存 customField を確認したいユーザー**: どの fieldKey が登録済みかを編集画面で一覧確認できる（現状は空表示）
5. **登録ミスを訂正したいユーザー (password)**: 初回登録時に password を typo した。編集画面で **現在の値を確認**しながら正しい値に書き直して保存できる
6. **password を確認したいユーザー**: 「自分がそのサイトに何を登録したか」を編集画面で確認できる。誤タップによる漏洩リスクを最小化するため、画面遷移直後は masking で表示され、ユーザーが意図的に `inputPassword` をタップ（focus）したときのみ平文化する
7. **password を変更しないユーザー**: 編集画面で other field（label 等）を修正して保存する際、password 欄を触らなくても既存 password が温存される（「空欄送信 = 温存」の現行セマンティクスを「未変更 = 温存」に置き換える）

## 5. 機能要件 (EARS 形式)

### Requirement 1: Mode.Edit での customField 表示

#### Acceptance Criteria

1. When ユーザーが既存 credential を編集画面で開いたとき, the CredentialEditViewModel shall 対象 credential の `customFieldsCiphertext` / `customFieldsIv` を復号し、`CustomFieldsState.rows` に DB 格納順で展開する
2. The CustomFieldsState shall `Mode.Edit` においても `editable = true` を保持する
3. While `Mode.Edit` で `rows` が空（DB に customField が 0 件）の状態, the UI shall 既存 `Mode.New` と同じく「フィールド追加」ボタンのみを表示する
4. When `rows` に既存 customField が展開されているとき, the UI shall 各行の `fieldKey` と `value` を `inputType="text"` の plain 表示で入力可能なテキストフィールドとして表示する（Phase 1 方針を維持）
5. If `customFieldsCiphertext` が長さ 0 のとき（Migration_2_3 直後で未 re-save の credential）, the CredentialEditViewModel shall `rows = emptyList()` で `editable = true` に遷移する（空 BLOB == 空 customFields 規約を踏襲）

### Requirement 2: customField 編集操作の解放

#### Objective

As a 既存 credential 編集中のユーザー, I want customField 行の追加・編集・削除を `Mode.New` と同じ操作感で行うこと, so that 訂正・追記・削除のたびに credential を作り直さなくて済む

#### Acceptance Criteria

1. When ユーザーが `Mode.Edit` で「フィールド追加」ボタンを押したとき, the CredentialEditViewModel shall 既存の `addCustomFieldRow()` を実行して新規行を append する
2. When ユーザーが既存行の `fieldKey` を編集したとき, the CredentialEditViewModel shall 該当行の `fieldKey` を更新する
3. When ユーザーが既存行の `value` を編集したとき, the CredentialEditViewModel shall 該当行の `value` を更新する
4. When ユーザーが行の削除ボタンを押したとき, the CredentialEditViewModel shall 該当行を `rows` から除去する
5. The CredentialEditViewModel shall `MAX_CUSTOM_FIELDS = 10` の上限を `Mode.Edit` でも維持し、11 件目の追加ボタンを disable する（既存ロード分 + 新規追加分の合算で判定）
6. While `Mode.Edit` の編集中, the UI shall 行操作のレスポンス（`rows` 更新の State 反映）を `Mode.New` と同等の体感速度で提供する

### Requirement 3: customField 保存時の永続化

#### Acceptance Criteria

1. When ユーザーが `Mode.Edit` で保存したとき, the save action shall 編集後の `customFields` 全件を `UpdateCredentialInput.customFields` に詰めて update use case を呼ぶ
2. When save が成功したとき, the persistence layer shall 削除された customField の値を DB から除去する（編集後の `customFields` をそのまま新しい暗号文として再生成する）
3. If `fieldKey` が空白のみの行が存在するとき, the save action shall その行を silent drop する（Phase 1 Req 3.4 と同一挙動）
4. When `Mode.Edit` で customField を 1 件も持たない状態で save したとき, the save action shall 空の `List<CustomField>` を update に渡し、結果として `customFieldsCiphertext` が空 BLOB（または空リストを表す ciphertext）相当となる
5. The save action shall `Mode.Edit` 起動から save までの間に customField 編集が一切行われていない場合でも、復号 → 再暗号化 → 永続化のラウンドトリップ後に元と意味的に等価な customField 集合を保持する（IV の再生成や JSON シリアライズ差異で意味が変わらないこと）

### Requirement 4: Mode.Edit での password 既存値表示

#### Objective

As a 既存 credential 編集中のユーザー, I want 編集画面に遷移した時点で **現在登録されている password が masking 状態で見えること**, so that 「何を登録していたか分からないまま編集する」状態を解消できる

#### Acceptance Criteria

1. When ユーザーが既存 credential を編集画面で開いたとき, the CredentialEditViewModel shall password を復号して UI state の `initialPassword` に保持する
2. When `initialPassword` が UI state に展開されたとき, the CredentialEditActivity shall `binding.inputPassword.setText(initialPassword)` で既存値を入力欄にセットする
3. The CredentialEditActivity shall `inputPassword` の **初期表示状態を masking** とする（`PasswordTransformationMethod` を適用する、または `inputType="textPassword"` に相当する表示モードを維持する）
4. The CredentialEditActivity shall 編集画面遷移直後に `inputPassword` を **自動的に focus 取得させない**（誤タップによる平文化リスクを抑える運用に統一する）
5. If `password` の復号に失敗したとき, the CredentialEditViewModel shall `State.Error` を emit し、`inputPassword` を空欄かつ masking のまま保つ。Activity は画面を閉じない（ユーザーに失敗を可視化）
6. The CredentialEditViewModel shall `initialPassword` を **編集中の `inputPassword.text` の比較基準として** のみ保持し、Activity 破棄時に GC 対象となるよう ViewModel スコープに閉じ込める

### Requirement 5: password masking focus toggle

#### Acceptance Criteria

1. When `inputPassword` が **focus を取得した**とき, the CredentialEditActivity shall `transformationMethod = null`（または等価な平文表示モード）を適用して平文表示する
2. When `inputPassword` が **focus を失った**とき, the CredentialEditActivity shall `transformationMethod = PasswordTransformationMethod.getInstance()`（または等価な masking モード）を適用して再 masking する
3. The transformation method 切替 shall カーソル位置を保持する（切替後に cursor が末尾にジャンプしない）
4. The transformation method 切替 shall `TextInputLayout.endIconMode` の挙動と競合してはならない（§9 Q1 で決定された方針に従う。具体的な実装手段は design.md / 実装フェーズで確定する）
5. The transformation method 切替 shall password の中身（`text`）を変更してはならない（切替は表示のみ）
6. While ユーザーが `inputPassword` を編集中（focus 取得状態）に画面回転や Activity 再生成が起きたとき, the masking 状態の最終的な復元は OS の標準的な `EditText` state restoration に委ねる（本 Issue では明示的な state 保存は要求しない）

### Requirement 6: password 保存時の dirty 判定

#### Acceptance Criteria

1. When ユーザーが `Mode.Edit` で保存し `inputPassword.text` が `initialPassword` と **完全一致** したとき, the save action shall `UpdateCredentialInput.newPassword = null` を渡し、既存 ciphertext を温存する
2. When ユーザーが `Mode.Edit` で保存し `inputPassword.text` が `initialPassword` と異なるとき, the save action shall `UpdateCredentialInput.newPassword = <新しい CharArray>` を渡し、新値で update する
3. If ユーザーが `inputPassword.text` を **空文字に変更した**まま保存しようとしたとき, the save action shall password blank validation エラーを返し、保存を中止する（空 password は許容しない、既存挙動を維持）
4. The save action shall 「未変更 password」と「新しい password」の比較を **`CharArray` の内容等価性** で行う（参照等価ではなく要素ごとの等価）
5. The save action shall 比較完了後、`initialPassword` および編集中 password の `CharArray` を可能な限り zero-fill する（既存の `UpdateCredentialUseCase` の zero-fill ポリシーと整合）

### Requirement 7: 認証フロー

#### Acceptance Criteria

1. The fix shall 編集画面に遷移するための既存 biometric / device credential unlock 経路（username の plaintext 表示を許可している既存セッション）を変更しない
2. The fix shall customField / password 復号のために追加の biometric prompt を提示しない（§9 Q1 の推奨案 A に基づく）
3. If 復号セッションが期限切れ等で `customFieldsCiphertext` または password の復号に失敗したとき, the CredentialEditViewModel shall `State.Error` を emit し、対応する State（customField 側は `rows` 空・`editable = false`、password 側は `initialPassword` 空かつ `inputPassword` 空欄）のままに保つ
4. The fix shall 復号した customField 値 / password 値を ViewModel スコープを超えて保持しない（Activity 破棄時に GC 対象となるよう、StateFlow と ViewModelScope に閉じ込める）

### Requirement 8: ログ規約

#### Acceptance Criteria

1. The implementation shall `Mode.Edit` で復号した customField の `value` / `fieldKey` および password を平文でログ出力しない（Phase 1 NFR 2 / `SafeLogger` 規約を踏襲）
2. The implementation shall デバッグ用途であっても customField 件数のような無害な集計情報のみを許容する
3. If 復号失敗が起きたとき, the implementation shall 例外型・件数等の最小限の情報のみを `SafeLogger.error` で記録し、ciphertext / IV / 平文を一切ログに残さない
4. The implementation shall password の masking 状態（masked / plaintext）をログに残さない（focus 状態の遷移を `SafeLogger.info` で出力しない）

### Requirement 9: Phase 2 (#67) との整合性

#### Acceptance Criteria

1. The fix shall Phase 2 (#67) `design.md §8.5` で定義された「`editable == true` ならばサジェスト UI を表示」というゲート条件を変更しない（Mode.Edit で `editable = true` になった結果、Phase 2 のサジェスト UI が自動的に有効化されることを許容する）
2. The fix shall Phase 2 が追加した `detected_fields` 記録経路（`KeyNestAutofillService.onFillRequest` 内の fire-and-forget detection）を変更しない
3. The fix shall Phase 2 の `bindPackageForSuggestions(record.packageName)` が `load()` 内で既に呼ばれている既存実装をそのまま活用し、`Mode.Edit` でもサジェスト UI が `currentPackageName` 解決済み状態で開けるようにする

### Requirement 10: テスト

#### Acceptance Criteria

1. The CredentialEditViewModelTest shall `Mode.Edit` 遷移時に既存 `customFieldsCiphertext` が復号され、`CustomFieldsState.rows` に DB 格納順で展開されることを検証する
2. The CredentialEditViewModelTest shall `Mode.Edit` 遷移後に `editable = true` であることを検証する
3. The CredentialEditViewModelTest shall `Mode.Edit` で行追加・既存行編集・行削除の各 reducer が動作することを検証する
4. The CredentialEditViewModelTest shall `Mode.Edit` で save したときに編集後の `customFields` が `UpdateCredentialInput.customFields` に渡されることを検証する
5. The CredentialEditViewModelTest shall 暗号化往復（plaintext → AES-GCM ciphertext → 復号 → 同一 plaintext）で customField 値・password 値の両方が一致することを検証する
6. The CredentialEditViewModelTest shall 復号失敗時に `State.Error` を emit し、customField 側は `rows` 空・`editable = false`、password 側は `initialPassword` 空のままになることを検証する
7. The CredentialEditViewModelTest shall `Mode.Edit` 遷移時に password が `initialPassword` に展開されることを検証する
8. The CredentialEditViewModelTest shall password が `initialPassword` と同値で保存されたとき `UpdateCredentialInput.newPassword = null` で update されることを検証する
9. The CredentialEditViewModelTest shall password が変更されたとき新値で update されることを検証する
10. The CredentialEditViewModelTest shall password が空文字で保存されたとき blank validation エラーで save が中止されることを検証する
11. The CredentialEditActivity (UI / Robolectric / unit) test shall `inputPassword` の focus 取得で平文表示、focus loss で masking 適用、カーソル位置保持を検証する（具体的な手段は実装フェーズで決定）
12. The CredentialEditViewModelTest shall 既存 Phase 1 / Phase 2 のテスト（`CredentialEditViewModelCustomFieldsTest` / `CredentialEditViewModelSuggestionTest` を含む）を一切 fail させない

## 6. 非機能要件

### NFR 1: 暗号化と機密性

1. The customField values および password shall 復号後も RAM 上でのみ保持され、ディスク・SharedPreferences・ログのいずれにも平文で書き込まれない
2. The decryption shall Phase 1 と同じ AES-GCM 鍵（Android Keystore 経由）で実行され、新規鍵を生成しない
3. The implementation shall 復号した customField 集合および password を編集画面の ViewModel スコープに閉じ込め、Activity 破棄時に GC 対象となるよう既存 ViewModel lifecycle に従う
4. The implementation shall password の `CharArray` を可能な限り zero-fill する（save 直後・ViewModel destroy 時など、既存 `UpdateCredentialUseCase` のポリシーと整合）。`String` 化を強要する経路（`EditText.text.toString()`）が残ることは許容するが、その場合も保持期間を最短化する

### NFR 2: 後方互換性

1. The fix shall `Mode.New` の挙動を一切変更しない（既存 `editable = true` 経路を共有するため、追加ロジックは `Mode.Edit` 分岐のみ）
2. The fix shall `customFieldsCiphertext` が空 BLOB の credential（Migration_2_3 直後で未 re-save）を読み込んだ場合に `rows = emptyList()` / `editable = true` で正常に開き、save 時には空リストを再暗号化して書き戻す（明示的に save を要求された場合のみ DB を更新）
3. The fix shall `UpdateCredentialInput.customFields` が `null` の場合「既存 ciphertext を温存する」セマンティクスを変更しない（save パスに到達した場合は常に非 null を渡し、明示的に上書きする）
4. The fix shall `UpdateCredentialInput.newPassword` の null セマンティクスを **変更せず継続利用** する（null = 既存 ciphertext 温存）。Phase 1.5 では「空欄送信 = 温存」を「`initialPassword` と一致 = 温存」へ判定基準を切り替えるが、use case 側の null 解釈は変えない

### NFR 3: 性能

1. While `Mode.Edit` の `load()` 中, the CredentialEditViewModel shall 復号処理（customField + password）を非同期で実行し、UI スレッドをブロックしない
2. The decryption shall customField 10 件分の AES-GCM 復号 + JSON パース、および password 1 件の AES-GCM 復号を編集画面の遷移体感速度（既存 username/label 表示と同等のオーダー、目安 10ms 未満）で完了させる
3. The focus toggle shall focus 変化から表示切替までを 16ms 以内（1 フレーム以内）に完了させ、体感的な遅延を発生させない

### NFR 4: 可観測性

1. The implementation shall `SafeLogger.info` で「`Mode.Edit` ロード成功」「customField 件数（数値のみ）」を出力可能とする（値・鍵・password は含めない）
2. If 復号失敗が起きたとき, the implementation shall `SafeLogger.error` で例外クラス名と最小限のコンテキストのみを記録する
3. The implementation shall password masking の focus 切替頻度・タイミングをログに残さない

### NFR 5: 一貫性

1. The CustomFieldsState reducer 群 (`addCustomFieldRow` / `removeCustomFieldRow` / `updateCustomFieldKey` / `updateCustomFieldValue`) shall `Mode.New` / `Mode.Edit` で完全に同一のコードパスを通る（モード分岐を reducer 内に持たず、`editable` フラグだけで制御）
2. The `inputPassword` の masking 挙動は **Mode.Edit でのみ focus toggle を適用する**。Mode.New では従来通り `endIconMode="password_toggle"` の標準挙動を維持する（focus 取得で自動平文化はしない）。差分の境界は Activity 側で明示する

## 7. データ移行戦略

### 移行不要

本 Issue は **Room schema を変更しない**。Phase 1 (#66) で導入された `customFieldsCiphertext` /
`customFieldsIv` 列、および既存の password ciphertext / IV 列をそのまま読み書きするのみで、
新規列追加や migration の発生はない。

### 既存データの取り扱い

- Phase 1 リリース時の `Migration_2_3` で初期化された credential（customFieldsCiphertext が空 BLOB）は、本 Issue 適用後に編集画面で開くと「customField 0 件」として表示される（Requirement 1.5）
- 既存 password ciphertext は Phase 1 以前から存在する。本 Issue 適用後に編集画面で開くと「`initialPassword` に復号された値が masking 表示」される（Requirement 4.2 / 4.3）
- ユーザーが「フィールド追加」→ save を行ったタイミングで、初めて非空の customField ciphertext が書き戻される
- ユーザーが password を一切編集せず save した場合、password ciphertext は temp 暗号文の再生成なしで温存される（Requirement 6.1）

## 8. テスト要件

§5 Requirement 10 に主要 AC を集約。加えて以下の前提を満たすこと。

- 既存テスト（`CredentialEditViewModelTest` / `CredentialEditViewModelCustomFieldsTest` / `CredentialEditViewModelSuggestionTest` / `UpdateCredentialUseCaseTest` / `SaveCredentialUseCaseCustomFieldsTest` / `UnlockVaultUseCaseCustomFieldsTest` / `EncryptedCustomFieldsCodecTest` / `Migration_1_2_Test` / `Migration_2_3_Test` / FillResponseBuilder 系）が一切 fail しないこと
- `Mode.Edit` での復号 → 編集 → save → 再復号のラウンドトリップ test を customField / password の **両方**について 1 件以上含めること
- 「ロード時に復号失敗 → `State.Error`」の異常系 test を customField / password の **両方**について 1 件以上含めること
- password の dirty 判定（`initialPassword` と一致 / 不一致 / 空文字）の 3 系統の AC を test で網羅すること
- focus toggle test は ViewModel ロジックで完結しない領域のため、Robolectric / Espresso / 単純な Activity 単体テストのいずれかで 1 件以上のカバレッジを設けること（具体的手段は実装フェーズで決定）

## 9. 確認事項（Open Questions）

各項について本要件で採用する **推奨案を明示**する。PR レビュー時に人間が up/down で確定する。

### Q1. password 復号 / customField 復号の認証方針

**質問**: `Mode.Edit` 遷移時に既存 customField および password を復号するための authentication を
どう扱うか。

| 案 | 内容 | UX 影響 |
|---|---|---|
| **A（推奨）** | 編集画面 load 時に復号 — username が plaintext で参照できる既存の復号セッションと同一スコープで customField / password も復号 | 追加 biometric prompt なし、username / label と同じ感覚 |
| B | 別 biometric unlock を要求 — customField / password 表示時に biometric prompt | 多重 prompt で UX 悪化、ただしセキュリティは強化 |

**本要件での採用**: **案 A** を Requirement 7.1 / 7.2 として明文化する。

**採用理由**:
- 編集画面に遷移する時点で既に biometric / device credential unlock が通っており、`EncryptedCredentialRecord` の username（plaintext）にアクセス可能な状態にある。customField 値・password 値も同じ機密性レベルで扱うのが UX 整合的
- KeyNest の master AES key (`keynest_aead_v1`) は `setUserAuthenticationRequired(false)` で構成されており、追加 biometric を要求しないことが現行モデルと整合
- Phase 1 実装コメントの "extra biometric unlock" は Phase 1 時点の暫定判断であり、Phase 1.5 として本 Issue で見直す前提
- 案 B は将来「機密性ラベル付き customField」のような細粒度認証が必要になった時点で再導入可能

### Q2. customField value 表示の masking 方針

**質問**: `Mode.Edit` で復号済み customField の value を表示する際、password 風 masking
(`inputType=textPassword`) を適用するか、plain text で表示するか。

**本要件での採用**: **plain text（Phase 1 Q3 と同じ）** を踏襲する。

**採用理由**:
- Phase 1 (#66) §9 Q3 で「value 入力欄は plain text（`inputType=textPassword` を用いない）」が確定済み。`Mode.Edit` で別方針を採るとモード間で UX が分裂する
- 暗号化・ログ除外は Requirement 8 と NFR 1 で担保
- 行単位 visibility トグルは Phase 1 §2 Out of Scope を踏襲し、本 Issue でも導入しない

### Q3. password の `TextInputLayout.endIconMode` と focus toggle の競合

**質問**: 現状の `credential_edit_activity.xml` で `inputPassword` の `TextInputLayout` は
`endIconMode="password_toggle"` が指定されており、ユーザーが endIcon（眼アイコン）をタップして
masking ↔ 平文を toggle できる。本 Issue で導入する `setOnFocusChangeListener` ベースの focus
toggle と組み合わせると、以下の挙動が衝突する可能性がある:

- ユーザーが endIcon をタップして masking → 平文に切り替えた状態で、別の field をタップして
  focus を移すと、本 Issue の focus loss ハンドラが再 masking する。ユーザーの「endIcon で
  明示的に表示し続けたい」意図と矛盾する
- 逆に、ユーザーが `inputPassword` にタッチして focus を取り、本 Issue の focus gain ハンドラが
  平文化した直後に endIcon をタップすると、endIcon の toggle action は「平文 → masking」に
  なり、ユーザーの意図が解釈しづらい

| 案 | 内容 | 評価 |
|---|---|---|
| **A（推奨）** | Mode.Edit では `endIconMode="none"` (または `clear_text`) に切り替え、masking 制御は本 Issue の focus toggle に一本化する。Mode.New では従来通り `password_toggle` を維持する | 競合解消・挙動が単純。ただし Activity / layout に Mode.Edit 専用の endIconMode 切替が必要 |
| B | `endIconMode="password_toggle"` を温存し、ユーザーが endIcon をタップした後は focus loss でも masking しない（明示 toggle 状態を尊重） | 状態管理が複雑（"ユーザー意図 flag" を持つ必要がある）。実装コスト高 |
| C | Mode.Edit では `endIconMode="none"` 固定、Mode.New でも `endIconMode="none"` に統一して挙動を揃える | UX 統一性は高いが、Mode.New 既存 UX を変えてしまうため別 Issue 化が必要 |

**本要件での採用**: **案 A** を推奨し、Requirement 5.4 / NFR 5.2 で「Mode.Edit でのみ focus
toggle を適用、Mode.New では従来挙動を維持」と境界を明示する。最終的な layout / Activity 上の
切替手段は design.md / 実装フェーズで具体化する。

**採用理由**:
- Mode.Edit のユースケース「既存 password を確認しながら編集する」では focus gain で平文化する
  flow が最も自然で、endIcon toggle と組み合わせる必要性が低い
- 案 B は状態管理が複雑化し、本 Issue のスコープを超える
- 案 C は Mode.New 既存挙動を変更するため別 Issue で扱うべき

### Q4. 復号タイミング — load() 即時 vs focus 時 lazy

**質問**: password / customField の復号を `load()` で **即時実行** するか、`inputPassword` の
focus 取得時に **lazy 実行** するか。

**本要件での採用**: **load() 即時実行** を Requirement 1.1 / 4.1 として明文化する。

**採用理由**:
- Phase 1 で customField は load() 即時復号を採用していないが、これは「Mode.Edit で `editable = false`」という暫定挙動に依存していた。本 Issue では Phase 1 の暫定挙動を解消するため、両者を `load()` 内で一括処理する方が一貫性が高い
- lazy 復号は「focus 取得時にプログレスバー or 一瞬の遅延が発生」する UX を発生させるため、編集画面の操作感を損なう
- 性能要件 NFR 3.2 (10ms 未満) を `load()` 内非同期復号で満たすことは AES-GCM 単発復号として現実的

### Q5. password 平文表示の初期挙動

**質問**: 編集画面に遷移した瞬間に `inputPassword` が自動的に focus を取って平文化される挙動を
許容するか。

**本要件での採用**: **画面遷移直後は `inputPassword` に focus を取らない** を Requirement 4.4
として明文化する。

**採用理由**:
- ユーザーが意図せず編集画面を開いた瞬間に password が平文表示されると、shoulder surfing リスクが
  上昇する
- Android の `TextInputLayout` / `TextInputEditText` の標準挙動として、Activity 遷移直後の自動
  focus は label 等の他 field に振ることでも回避可能（具体的手段は実装フェーズで決定）

### Q6. password masking 切替時のカーソル位置

**質問**: `inputPassword` の transformationMethod を切り替えた直後、カーソル位置が末尾に
ジャンプする副作用が知られている（Android の既知挙動）。これをどう扱うか。

**本要件での採用**: **切替前後でカーソル位置を保持する** を Requirement 5.3 として明文化する。
具体的な手段（`setSelection()` で復元する等）は design.md / 実装フェーズで確定する。

**採用理由**:
- ユーザー UX として、masking 切替で意図せずカーソル位置が変わると、編集中の入力が混乱する
- `setSelection(selectionStart, selectionEnd)` で前後保持する実装は Android 標準 API のみで完結する

## 10. 未決事項（architect への申し送り）

設計フェーズ以降で扱う論点を以下に列挙する。本要件書では実装方針を決定しない。

1. **復号の実行レイヤ**: `CredentialEditViewModel.load()` 内で直接復号するか、新規 use case（例: `LoadCredentialForEditUseCase`）を切るかは architect が判断。password は既存 `UnlockVaultUseCase` を編集画面で呼び直すか、ViewModel 内で codec / cipher を直接呼ぶかも論点
2. **JSON デコーダの共通化**: Phase 1 で導入した「`List<CustomField>` ⇔ JSON」のシリアライザを `Mode.Edit` 復号パスでもそのまま再利用するか、復号専用の薄い wrapper を切るかを設計時に決定
3. **空 BLOB ハンドリング**: Requirement 1.5 / NFR 2.2 に従い、空 ciphertext を復号せず即座に空リスト扱いとする分岐をどのレイヤ（ViewModel / use case / cipher 層）に置くかを設計時に決定
4. **`UpdateCredentialInput.customFields` / `newPassword` の null セマンティクス維持**: NFR 2.3 / 2.4 で「`customFields` は save パスで常に非 null を渡す」「`newPassword` の null = 温存セマンティクスは継続利用」と方針を示しているが、現行 ViewModel の `if (_customFields.value.editable) effectiveCustomFields else null` / `if (password.isEmpty()) null else password` 分岐をどう変えるかの整理は architect 領分。Mode.Edit dirty 判定では「`initialPassword` と一致なら null」のロジックが追加される
5. **既存行と新規追加行の区別**: 「ロード時に展開された行」と「ユーザーが追加した行」を区別する必要があるか（例: row ID の付与戦略、削除時の UX 差別化など）を設計時に検討
6. **復号失敗時のリトライ UX**: Requirement 7.3 で「`State.Error` を emit、対応する State を空のまま保つ」と最低限を規定したが、ユーザー向けに「再試行」「画面を閉じる」のどちらを推奨アクションとするかは設計判断
7. **focus toggle の実装手段**: `setOnFocusChangeListener` + `transformationMethod` の組み合わせか、 `inputType` の切替か、`TextInputEditText` のサブクラス化か、いずれを採るか。`endIconMode` の切替方針（Q3 採用案 A）の具体実装も含めて design.md で確定する
8. **password の zero-fill 範囲**: `EditText.text` は `Editable`（内部 `String` baked）で zero-fill が現実的に困難。`initialPassword` を `CharArray` で持つか `String` で持つかも含め、現実的なポリシーを設計時に決定（NFR 1.4 の "可能な限り" の範囲を確定する）
9. **focus toggle の test 戦略**: Robolectric / Espresso / 単純な単体テストのうち、本プロジェクトの既存テスト構成に最も適合するものを設計時に選定（Requirement 10.11）

## 11. 関連 Issue / 後続フェーズ

- **Phase 1 (#66 / PR #69 merged)**: customField の導入。本 Issue が解消する制約 (`Mode.Edit` で `editable = false`、`inputPassword` 空欄スタート) を導入した PR
- **Phase 2 (#67 / PR #70)**: detected_fields サジェスト。本 Issue の `editable = true` 解放により Phase 2 サジェスト UI が `Mode.Edit` でも自動有効化される（Phase 2 design §8.5 参照）
- **Phase 3（別 Issue）**: `AutofillFieldHeuristics` への日本語 hint / 一般的 resourceId pattern 追加。本 Issue とは独立に並行進行可能
- **関連 Issue #14**: AdvancedDetails / credential ID 表示。本 Issue は AdvancedDetails の挙動を変更しない

## 12. 制約

1. The Issue Implementation shall 既存テスト（unit / instrumented を含む）を一切 fail させない
2. The Issue Implementation shall `develop` / `main` ブランチに直接 push しない（feature branch + PR レビュー経由）
3. The requirements.md shall 実装コードを含まない。クラス名・メソッド名への言及は既存実装の同定に必要な最小限のみとする
4. The Issue Implementation shall Phase 1 (#66) で確定した暗号化レイヤ（AES-GCM / Android Keystore）と JSON シリアライザを変更しない。password の暗号化方式も変更しない
5. The Issue Implementation shall Phase 2 (#67) の detection / suggestion ロジックを変更しない（前提条件の解消のみ）
6. The Issue Implementation shall Credential Manager API 経路に変更を加えない（Phase 1 と同じスコープ外扱い）
7. The Issue Implementation shall Room schema を変更しない（migration 不要）
8. The Issue Implementation shall 追加 biometric prompt を導入しない（§9 Q1 採用案 A）
9. The Issue Implementation shall Mode.New の挙動を変更しない（NFR 2.1 / NFR 5.2）
