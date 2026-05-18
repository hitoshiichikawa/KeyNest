# Design Document

## Overview

**Purpose**: Phase 1 (#66 / PR #69) で `Credential` に customField を導入したが、`Mode.Edit` では
次の 2 つの編集 UX 制約が残っている。Phase 1.5 (本 Issue #73) で両方を同一 PR の中で解消する:

1. **customField 編集不可** — `CredentialEditViewModel.load()` が `Mode.Edit` 遷移時に
   `CustomFieldsState(rows = emptyList(), editable = false)` を固定で割り当てているため、
   既存 customField の表示・追加・編集・削除が一切できない
2. **password 既存値の確認・編集 UX が貧弱** — Mode.Edit で `inputPassword` が空欄で開く
   (Activity が `setText()` を呼ばない設計)。ユーザーは「変更しないなら空欄送信、変更なら新値入力」
   の二択しかなく、登録済みの password を編集画面で確認できない

本設計では編集画面 load 時に既存 customField および password を **既存 unlock セッションと同じ
スコープで** 復号し、それぞれ `CustomFieldsState.rows` / `EditState.initialPassword` に展開、
UI 上で参照・編集可能にする。さらに `inputPassword` には focus 取得で平文表示、focus loss で
再 masking する `OnFocusChangeListener` を配線し、初期表示は masking 状態とする。
save 時は customField を編集後リストで上書き、password は `initialPassword` との内容等価性で
dirty 判定して `UpdateCredentialInput.newPassword = null/non-null` を切り替える。

**Users**: 既に credential を登録済みのユーザーが、業務アプリの会員番号など後付けの customField
を追記する／登録ミスを訂正する／不要 customField を削除する／登録済み password を確認・訂正する、
といった日常的な保守 workflow で利用する。

**Impact**: 「customField や password を直したいときに credential を作り直すしかない」「登録済み
password を確認する手段がない」という運用上の障害を取り除く。本変更は **ViewModel・Activity UI・
layout の `endIconMode` 切替** の 3 レイヤにまたがる中規模変更だが、Room schema・暗号化方式・
既存 use case 構造には触れない。Phase 2 (#67) のサジェスト UI は `editable == true` を表示
ゲートとしているため、本 Issue の merge 後に Mode.Edit でも自動的に有効化される（Phase 2 設計
§8.5 の想定どおり）。

### Goals

- `Mode.Edit` 遷移時に既存 `customFieldsCiphertext` / `customFieldsIv` を **追加 biometric prompt
  なしで** 復号し、`CustomFieldsState.rows` に DB 格納順で展開する（Q1 採用案 A）
- `Mode.Edit` でも `editable = true` を割り当て、`Mode.New` と同一 reducer・同一 UI で
  customField の add / remove / update / save が動作する（NFR 5.1 = 完全に同一コードパス）
- `Mode.Edit` 遷移時に既存 password を **同一 unlock セッションで** 復号し、UI state の
  `initialPassword` に展開する（Req 4.1）
- `inputPassword` を初期表示で masking 状態とし、ユーザーが意図的に focus 取得したときのみ
  平文表示する（Req 4.3 / 5.1 / 5.2）。focus loss で再 masking する
- save 時に編集後の `customFields` を `UpdateCredentialInput.customFields` に詰めて既存
  ciphertext を上書きする。password は `initialPassword` との内容等価性で dirty 判定し、
  一致なら `newPassword = null` (温存)、不一致なら `newPassword = <新 CharArray>` (上書き) を渡す
- 復号失敗時は `State.Error` を emit しつつ、customField 側は `rows` 空・`editable = false`、
  password 側は `initialPassword` 空・`inputPassword` 空欄で画面を保つ（Activity を閉じない）
- Phase 2 (#67) のサジェスト・検出ロジックを変更しない

### Non-Goals

- customField / password の暗号化方式・鍵管理の変更（既存 AES-GCM / Android Keystore /
  `EncryptedCustomFieldsCodec` をそのまま再利用）
- customField 表示時の value masking 行単位トグル（Phase 1 Q3 と同じく plain text、本 Issue
  でも導入しない）
- customField の並び順カスタマイズ UI（別 Issue）
- username / label の編集挙動の変更
- Phase 2 (#67) の detection / suggestion ロジック本体への変更
- Room schema の変更（migration なし）
- 追加 biometric prompt の導入（Q1 採用案 A）
- Mode.New の `inputPassword` 挙動の変更（NFR 5.2 / Q3 採用案 A）

---

## Architecture

### Existing Architecture Analysis

Phase 1 (#66) で確立した境界をそのまま踏襲する:

- **Domain layer**: `EncryptedCredentialRecord` が `passwordCiphertext` / `passwordIv`
  (Phase 1 以前から) と `customFieldsCiphertext` / `customFieldsIv` (Phase 1 で追加、両 `ByteArray`)
  を保持。`UpdateCredentialInput.newPassword: CharArray?` は **null = 既存 ciphertext 温存 /
  non-null = 新規 IV で上書き**、`UpdateCredentialInput.customFields: List<CustomField>?` は
  **null = 既存 ciphertext 温存 / non-null（空 list 含む） = 上書き** のセマンティクス。
- **Security layer**: `EncryptedCustomFieldsCodec` が `List<CustomField>` ⇔ AES-GCM `EncryptedBlob`
  を双方向で扱う。空 BLOB を decrypt すると空 list を返す fail-open 仕様（Phase 1 §6.1）。
  鍵は Android Keystore 内の AES key で、`UnlockVaultUseCase` 内 password decrypt と同一鍵を使用。
  password 単体の暗号化は `AesGcmCipher` で直接行われている（既存）。
- **Use case layer**: `UnlockVaultUseCase` が既に password / customField の decrypt path を持ち
  `PlaintextCredential` を返す（Autofill / DangerZone 等で使用、`AutoCloseable` で zero-fill 責任）。
  `UpdateCredentialUseCase` は `newPassword != null` のとき AES-GCM で再 encrypt、null なら
  既存 ciphertext を温存する。`customFields != null` のとき codec で再 encrypt して
  `EncryptedCredentialRecord` を build。
- **UI layer**: `CredentialEditViewModel.load(credentialId)` で `repository.findById` を呼び、
  `EncryptedCredentialRecord` を直接受け取る。現状 Mode.Edit では password / customField のいずれも
  decrypt しない。`CustomFieldsState.editable` フラグで reducer の通過可否を制御し、Activity 側
  `renderCustomFields()` は `editable` をそのまま尊重して add CTA の表示・行 view 描画を切り替える
  パターンが完成済み。`inputPassword` は layout で `inputType="textPassword"` /
  `endIconMode="password_toggle"` が指定されており、Activity は Mode.Edit で `setText()` を呼ばない
  実装になっている（password はユーザーが書き直すなら新値を type する設計）。
- **Phase 2 (#67) の追加**: `CredentialEditViewModel` に `SuggestionState` / `bindPackageForSuggestions`
  / `refreshSuggestions()` を導入。`refreshSuggestions()` 冒頭で `state.editable == false` を
  見て early return しているため (Phase 2 §8.5)、本 Issue で `editable = true` に切り替えると
  **追加コードなし** で Mode.Edit でもサジェスト UI が動く。

**尊重する制約**:
- 復号した plaintext (`List<CustomField>` / password) は ViewModel スコープ
  （`StateFlow<CustomFieldsState>` / `StateFlow<EditState>`）に閉じ込め、Activity 破棄時に
  GC 対象とする。
- ログには customField の `fieldKey` / `value` / password を平文出力しない
  （`SafeLogger` 規約、Phase 1 NFR 2）。
- AES-GCM 失敗時は username/password decrypt と同じ深刻度として扱うが、本画面は既に load された
  EncryptedCredentialRecord（username plaintext を含む）にアクセス済みなので、復号失敗を
  `State.Error` に橋渡しすれば足りる（画面を閉じない、Req 7.3）。
- password の `CharArray` は可能な限り zero-fill する（NFR 1.4）。ただし `EditText.text` は
  `Editable` 内部で `String` 化されるため "可能な限り" の精神とし、`initialPassword` の保持期間を
  最短化する方針で十分とする。

### Architecture Pattern & Boundary Map

採用パターン: **既存 MVVM + UseCase 構造の継続**。新規コンポーネントは導入せず、Phase 1 で導入した
`EncryptedCustomFieldsCodec` を ViewModel から **直接呼び出す** 案を採用する（後述 §代替案比較）。
password 復号は `AesGcmCipher.decrypt(...)` を ViewModel から呼ぶ最小経路を採用する（既存
`UnlockVaultUseCase` 全体は Autofill / DangerZone 向けで `AutoCloseable` lifetime を持つため、
編集画面の load() に乗せると過剰になる）。

```mermaid
flowchart LR
  A[CredentialEditActivity.onCreate] --> B[viewModel.load(id)]
  B --> C[repository.findById]
  C --> D[EncryptedCredentialRecord]
  D --> E1[EncryptedCustomFieldsCodec.decrypt]
  D --> E2[AesGcmCipher.decrypt password]
  E1 -- success --> F1[CustomFieldsState rows=decrypted, editable=true]
  E1 -- throw --> G[State.Error + editable=false]
  E2 -- success --> F2[EditState initialPassword=decrypted]
  E2 -- throw --> G
  F1 --> H1[renderCustomFields → rows 表示 + add CTA 有効化]
  F2 --> H2[inputPassword.setText(initialPassword) + masking 初期表示 + focus listener 配線]
  H1 --> I1[reducer: add/remove/update]
  H2 --> I2[focus toggle: gain→plain / loss→mask]
  I1 --> J[viewModel.save]
  I2 --> J
  J --> K[UpdateCredentialInput<br/>customFields=effective<br/>newPassword=dirty判定で null|non-null]
  K --> L[UpdateCredentialUseCase → codec.encrypt + AesGcmCipher.encrypt → repository.update]
```

**Architecture Integration**:
- 採用パターン: **MVVM + use case + 既存 codec / cipher**（Phase 1 構造の継続）
- ドメイン／機能境界: ViewModel が UI state を所有、codec が customField の暗号 ↔ 平文変換を
  所有、`AesGcmCipher` が password の暗号 ↔ 平文変換を所有、use case が validation + persistence
  を所有。本 Issue では境界を変えず、ViewModel の `load()` 内処理を「rows 空 / password 空欄」固定から
  「codec.decrypt + cipher.decrypt 呼び出し」に置き換える。
- 既存パターンの維持: `editable` フラグによる reducer ゲート、`SuggestionState.refreshSuggestions()`
  の早期 return パターン、`UpdateCredentialInput.customFields` / `newPassword` の null セマンティクス
- 新規コンポーネント: なし。既存 `EncryptedCustomFieldsCodec` および `AesGcmCipher` を ViewModel に
  inject する Factory 拡張のみ

### 代替案検討（採用しなかった案の記録）

#### customField 復号レイヤ

| 案 | 内容 | 採用可否と理由 |
|---|---|---|
| **A（採用）** | ViewModel が `EncryptedCustomFieldsCodec` を直接 inject して `load()` 内で decrypt | 最小変更。Phase 1/2 と一貫性。codec は元々 ViewModel スコープのライフタイム前提で設計されている |
| B | 新 use case `LoadCredentialForEditUseCase` を導入し customField / password を一括 decrypt | use case 層に "ViewModel 専用 helper" を増やす過剰抽象化。`PlaintextCredential` を返す `UnlockVaultUseCase` は password 復号と紐づいておりここでは不要 |
| C | `UnlockVaultUseCase` を編集画面でも呼ぶ | `AutoCloseable` の lifetime が editor flow に合わない。Autofill / DangerZone とライフタイム責務が異なる |

#### password 復号レイヤ

| 案 | 内容 | 採用可否と理由 |
|---|---|---|
| **A（採用）** | ViewModel が `AesGcmCipher` を直接 inject して `load()` 内で `cipher.decrypt(EncryptedBlob(iv = record.passwordIv, ciphertext = record.passwordCiphertext))` を呼ぶ | 最小変更。`AesGcmCipher` は Keystore alias 経由で純粋関数的に動作するため ViewModel から呼んでも責務逸脱にならない |
| B | 新 use case `DecryptCredentialPasswordUseCase` を導入 | 過剰抽象化。`AesGcmCipher` を直接呼ぶ層と等価で価値が薄い |
| C | `UnlockVaultUseCase` を編集画面でも呼ぶ | customField 側案 C と同じ理由で却下 (`AutoCloseable` lifetime ミスマッチ) |

#### password masking toggle の実装手段

| 案 | 内容 | 採用可否と理由 |
|---|---|---|
| **A（採用）** | `binding.inputPassword.setOnFocusChangeListener` + `transformationMethod` 切替 + `setSelection()` でカーソル保持 | Android 標準 API のみで完結。test も比較的容易 |
| B | `inputType` を `textPassword` ⇔ `text` で切り替える | `inputType` 切替は IME state の再初期化を伴うため UX が乱れる（既知の Android quirk） |
| C | `TextInputEditText` のサブクラスを作って focus-aware masking を内蔵 | 1 箇所のためにサブクラスを切るのは過剰抽象化 |

#### `TextInputLayout.endIconMode` の扱い（§9 Q3）

| 案 | 内容 | 採用可否と理由 |
|---|---|---|
| **A（採用）** | Mode.Edit で `endIconMode="none"` に動的切替、Mode.New では `password_toggle` を維持 | 競合解消・挙動が単純。Activity から `binding.passwordLayout.endIconMode = TextInputLayout.END_ICON_NONE` で切替可能 |
| B | 既存 `password_toggle` を温存、ユーザー意図 flag を持って focus loss でも masking しない | 状態管理が複雑、本 Issue スコープを超える |
| C | Mode.New / Mode.Edit ともに `endIconMode="none"` に統一 | Mode.New の挙動を変えてしまうため別 Issue 化が必要 |

採用理由の補足: codec / cipher は Phase 1 で既に複数の use case から呼ばれており、ViewModel から
呼んでも責務逸脱にならない（純粋関数的な変換）。`endIconMode` の動的切替は `TextInputLayout` の
公開 API (`setEndIconMode`) で完結する。

### Technology Stack

| Layer | Choice / Version | Role in Feature | Notes |
|-------|------------------|-----------------|-------|
| UI | Android View binding + Kotlin Coroutines + StateFlow | `renderCustomFields` で customField rows を描画、`inputPassword` に initialPassword を setText、focus listener で masking 切替 | 既存 layout を再利用、`endIconMode` のみ Mode.Edit で動的に変更 |
| ViewModel | androidx.lifecycle.ViewModel + StateFlow | `load()` 内 decrypt (customField / password) / reducer の `editable` ゲート / `save()` の null 撤廃 + password dirty 判定 | Factory に codec + cipher inject 追加 |
| Use Case | `UpdateCredentialUseCase` | `customFields != null` 時に既存 ciphertext を上書き、`newPassword != null` 時に password を再暗号化 | **変更なし**（既存パスに乗る） |
| Security | `EncryptedCustomFieldsCodec` (AES-GCM + Android Keystore), `AesGcmCipher` | `decrypt(EncryptedBlob)` を ViewModel から呼ぶ | **変更なし** |
| Persistence | Room (`CredentialEntity.customFieldsCiphertext` / `customFieldsIv` / `passwordCiphertext` / `passwordIv`) | 読み書きのみ、schema 変更なし | Migration 不要 |
| DI | `ServiceLocator` | `ViewModelProvider.Factory` への codec / cipher 引き渡しを追加 | Phase 1 で codec / cipher は既に登録済み |

---

## File Structure Plan

### Directory Structure（変更されるパス相当のみ抜粋）

```
app/src/main/java/io/github/hitoshiichikawa/keynest/
├── ui/edit/
│   ├── CredentialEditViewModel.kt        # 主変更: load() / save() / Factory / EditState 拡張
│   └── CredentialEditActivity.kt         # 変更: setText(initialPassword) / focus listener / endIconMode 切替
├── di/
│   └── ServiceLocator.kt                 # 必要なら customFieldsCodec / aesGcmCipher の expose を確認
└── security/
    ├── EncryptedCustomFieldsCodec.kt     # 変更なし（reference として参照）
    └── AesGcmCipher.kt                   # 変更なし（reference として参照）

app/src/main/res/layout/
└── credential_edit_activity.xml          # password 欄の `(optional)` hint 削除（Issue 本文記載）。
                                          # `endIconMode` は Activity 側で Mode.Edit 時に動的切替するため
                                          # layout 自体の `endIconMode` 既定値は変更しない or `none` 化のいずれかを実装で確定する

app/src/test/java/io/github/hitoshiichikawa/keynest/
├── ui/edit/
│   ├── CredentialEditViewModelCustomFieldsTest.kt
│   │   # 既存 Phase 1 reducer テスト + Mode.Edit 行追加 / 編集 / 削除を追加
│   ├── CredentialEditViewModelSuggestionTest.kt
│   │   # 既存 Phase 2 テストを fail させない（editable = true へ切り替わってもサジェスト挙動が崩れない確認）
│   ├── CredentialEditViewModelEditModeCustomFieldsTest.kt  ← 新規追加
│   │   # Mode.Edit 専用 customField: load 時 decrypt 展開 / editable = true / save 時上書き / 復号失敗 → State.Error
│   └── CredentialEditViewModelEditModePasswordTest.kt      ← 新規追加
│       # Mode.Edit 専用 password: load 時 decrypt → initialPassword / save 時 dirty 判定 (null / 新値 / blank validation) /
│       # 復号失敗 → State.Error / ラウンドトリップ等価性
└── domain/usecase/
    └── FakeCredentialRepository.kt       # 必要に応じて customFields + password 込み record を返す helper 追加

app/src/androidTest/java/io/github/hitoshiichikawa/keynest/   # ※ Robolectric / Espresso のうち本プロジェクトの規約に合うものを採用
└── ui/edit/
    └── CredentialEditActivityPasswordFocusTest.kt           ← 新規追加
        # inputPassword の focus 取得で平文表示 / focus loss で masking / カーソル位置保持 を検証
```

### Modified Files

- `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditViewModel.kt`
  - **State 拡張**: `EditState`（または現存する `State.Loaded` の代替プロパティ）に `initialPassword: String?`
    を追加。null は「未復号 / 復号失敗 / 編集画面でまだ load 未完了」を意味する
  - `load(credentialId)`:
    - 復号した `List<CustomField>` を rowId 付与で `CustomFieldsState.Row` に map →
      `CustomFieldsState(rows = mapped, editable = true)` を emit
    - `AesGcmCipher.decrypt(EncryptedBlob(iv = record.passwordIv, ciphertext = record.passwordCiphertext))`
      で password を復号して `initialPassword` に格納し、UI state に emit
    - 復号失敗 (`Throwable` catch) 時に `_state.value = State.Error(cause = "decrypt_credential")`
      を emit し、`CustomFieldsState(emptyList(), editable = false)` + `initialPassword = null` を維持
    - 両者は **並列の `viewModelScope.async`** ではなく **逐次** で復号する（既存 password と
      customField の依存関係は無いが、性能的に必要にならない限り単純化を優先）
  - `save()`:
    - customField: 既存 `if (_customFields.value.editable) effective else null` 三項は形式上残すが、
      Mode.Edit でも `editable = true` 経路を通るため null パスは事実上到達しない。コメント更新
    - password: 新たに dirty 判定を追加
      ```
      val typedPassword = state.passwordInput   // EditText から取得
      val newPasswordArg: CharArray? = when {
          typedPassword.isEmpty() && state.initialPassword.isNullOrEmpty() -> null  // 異常系: Mode.Edit で空欄継続
          typedPassword.isEmpty() -> /* validation error: blank not allowed */
          state.initialPassword != null && typedPassword.contentEquals(state.initialPassword!!.toCharArray()) -> null  // 未変更 → 温存
          else -> typedPassword.toCharArray()  // 変更あり → 上書き
      }
      ```
      （実装の詳細表現は Developer 領分。dirty 判定の意味論を守ること）
  - 既存 reducer (`addCustomFieldRow` / `removeCustomFieldRow` / `updateCustomFieldKey` /
    `updateCustomFieldValue`) は **変更しない**（NFR 5.1: モード分岐を内部に持たない）
  - `Factory` クラスに `customFieldsCodec: EncryptedCustomFieldsCodec` + `aesGcmCipher: AesGcmCipher`
    パラメータ追加、`ViewModelProvider.Factory.create` で ViewModel コンストラクタに引き渡し
  - companion の `TAG` を流用して `SafeLogger.info("edit mode loaded: customFields=${rows.size}, password=loaded")`
    を出力（値は含めない、NFR 4.1）

- `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditActivity.kt`
  - `CredentialEditViewModel.Factory` の呼び出し箇所に `ServiceLocator.encryptedCustomFieldsCodec`
    + `ServiceLocator.aesGcmCipher` を追加（呼び出し点 1 か所）
  - `renderCustomFields` / `binding.btnAddCustomField` 周りは既存実装で `editable` を尊重して
    いるため **本体ロジック変更なし**
  - **password 初期表示**: `viewModel.state.collect { state -> ... }` 内（または `load` 完了通知）で
    `state.initialPassword?.let { binding.inputPassword.setText(it) }` を 1 回だけ実行する（再 emit
    で setText が連打されないよう "一度きり" の guard を入れる）。setText 後に
    `binding.inputPassword.transformationMethod = PasswordTransformationMethod.getInstance()` で
    masking 初期化
  - **focus toggle** (Mode.Edit 時のみ): `binding.inputPassword.setOnFocusChangeListener { _, hasFocus ->
      val cursorStart = binding.inputPassword.selectionStart
      val cursorEnd = binding.inputPassword.selectionEnd
      binding.inputPassword.transformationMethod =
          if (hasFocus) null else PasswordTransformationMethod.getInstance()
      binding.inputPassword.setSelection(cursorStart, cursorEnd)
    }`
    （Mode.New では listener を配線しない。Mode.New / Mode.Edit の判定は ViewModel が公開する
    `mode` プロパティで行う）
  - **`endIconMode` 動的切替** (§9 Q3 採用案 A): Mode.Edit 確定後に
    `binding.passwordLayout.endIconMode = TextInputLayout.END_ICON_NONE` を 1 回設定する。
    Mode.New では layout 既定値 `password_toggle` をそのまま使用する
  - **初期 focus 抑制** (Req 4.4): Activity / Fragment の `android:windowSoftInputMode` 設定または
    `inputPassword.setFocusable(false); setFocusableInTouchMode(false); setFocusable(true)`
    パターンで「画面遷移直後に inputPassword に focus が当たらない」状態を作る（具体的手段は
    Developer 領分。初期 focus を `label` 入力欄等に振る簡便な方法でも可）

- `app/src/main/java/io/github/hitoshiichikawa/keynest/di/ServiceLocator.kt`
  - Phase 1 で codec / cipher が既に組み立てられている場合は公開プロパティ
    `encryptedCustomFieldsCodec` / `aesGcmCipher` を expose（存在しなければ追加）

- `app/src/main/res/layout/credential_edit_activity.xml`
  - password 欄の hint `(optional)` 表記を削除（Issue 本文記載通り、既存 password が表示される
    UX では "optional" が誤解を招くため）
  - `endIconMode` の layout 既定値は **変更しない**（Activity 側で Mode.Edit 時のみ動的切替する
    ため、Mode.New の挙動を保つ Q3 採用案 A と整合）。ただし Developer 判断で layout を
    `endIconMode="none"` に変更し Activity 側で Mode.New 時に `password_toggle` に戻す
    実装パターンも許容する（最終形は実装フェーズで確定）

### New Files

- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditViewModelEditModeCustomFieldsTest.kt`
  - Mode.Edit 専用 customField テスト。load 時の decrypt 展開、`editable = true` 確認、save 時に
    `UpdateCredentialInput.customFields` に編集後リストが渡ることを `FakeUpdateCredentialUseCase` 経由で検証、
    decrypt 失敗時の `State.Error` 検証、ラウンドトリップ等価性検証
- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditViewModelEditModePasswordTest.kt`
  - Mode.Edit 専用 password テスト。load 時の decrypt → `initialPassword` 反映、save 時の dirty 判定
    （未変更 → newPassword = null、変更あり → 新値、空欄 → blank validation error）、ラウンド
    トリップ等価性、decrypt 失敗時の `State.Error` 検証
- `app/src/androidTest/java/.../CredentialEditActivityPasswordFocusTest.kt`
  - focus toggle の UI test。focus 取得で `transformationMethod == null`、focus loss で
    `transformationMethod is PasswordTransformationMethod`、カーソル位置保持を検証。
    Robolectric / Espresso のいずれを採るかは Developer が既存テスト規約に従って選定

---

## Requirements Traceability

| Requirement | Summary | Components | Interfaces / Method | Flows |
|---|---|---|---|---|
| 1.1 | Mode.Edit で既存 customField を復号して rows に展開 | CredentialEditViewModel, EncryptedCustomFieldsCodec | `load()`, `codec.decrypt()` | load → findById → codec.decrypt → CustomFieldsState |
| 1.2 | Mode.Edit でも `editable = true` を保持 | CredentialEditViewModel | `load()` reducer | 同上 |
| 1.3 | DB 0 件のとき add CTA のみ表示 | CredentialEditActivity | `renderCustomFields()` | rows = [], editable = true → add CTA 表示 |
| 1.4 | 既存行を `inputType="text"` の plain 表示で表示 | CredentialEditActivity | `renderCustomFields()` | editable = true 経路で view inflate |
| 1.5 | `customFieldsCiphertext` が長さ 0 のとき rows 空 / editable = true | CredentialEditViewModel, EncryptedCustomFieldsCodec | `codec.decrypt(empty BLOB)` | codec は既に空 list を返す。ViewModel は `editable = true` をセット |
| 2.1 | 行追加 reducer 動作 | CredentialEditViewModel | `addCustomFieldRow()` | 既存 reducer、`editable = true` で通過 |
| 2.2 | fieldKey 編集 reducer 動作 | CredentialEditViewModel | `updateCustomFieldKey()` | 既存 reducer |
| 2.3 | value 編集 reducer 動作 | CredentialEditViewModel | `updateCustomFieldValue()` | 既存 reducer |
| 2.4 | 行削除 reducer 動作 | CredentialEditViewModel | `removeCustomFieldRow()` | 既存 reducer |
| 2.5 | 上限 10 件を Mode.Edit でも維持 | CredentialEditViewModel | `CustomFieldsState.canAddMore` | rows.size < MAX_CUSTOM_FIELDS（既存ロジック） |
| 2.6 | UI 体感速度 | CredentialEditViewModel | StateFlow reducer は同期 update | 既存実装そのまま |
| 3.1 | save 時に編集後 `customFields` を update input に詰める | CredentialEditViewModel, UpdateCredentialUseCase | `save()` → `UpdateCredentialInput(customFields = effective)` | save → updateUseCase → codec.encrypt → repo.update |
| 3.2 | 削除行を DB から除去（再 encrypt） | UpdateCredentialUseCase, EncryptedCustomFieldsCodec | `codec.encrypt(newList)` | 既存 use case が `customFields != null` で上書き |
| 3.3 | blank fieldKey を silent drop | CredentialEditViewModel | `save()` の `.filter { it.fieldKey.isNotBlank() }` | 既存ロジック（Phase 1 Req 3.4） |
| 3.4 | 0 件 save で空 list を渡す | CredentialEditViewModel, UpdateCredentialUseCase | `effective = emptyList()` | non-null 空 list → codec が `[]` JSON を encrypt |
| 3.5 | 無編集 save でもラウンドトリップ等価性を保つ | EncryptedCustomFieldsCodec | `decrypt` / `encrypt` の対称性 | 既存 codec の往復で意味的等価性が保たれる |
| 4.1 | Mode.Edit で password を復号して `initialPassword` に展開 | CredentialEditViewModel, AesGcmCipher | `load()`, `cipher.decrypt()` | load → findById → cipher.decrypt → EditState.initialPassword |
| 4.2 | Activity が `inputPassword.setText(initialPassword)` で既存値をセット | CredentialEditActivity | state 観測 → `binding.inputPassword.setText` | state collect 内で一度きり setText |
| 4.3 | `inputPassword` 初期表示で masking 状態 | CredentialEditActivity | `transformationMethod = PasswordTransformationMethod.getInstance()` | setText 直後に masking |
| 4.4 | 編集画面遷移直後に inputPassword に自動 focus させない | CredentialEditActivity | `windowSoftInputMode` / 初期 focus 制御 | 標準 Android API で他 field を初期 focus に |
| 4.5 | password 復号失敗時 `State.Error` + inputPassword 空欄 + masking | CredentialEditViewModel | `load()` の catch | catch → `_state.value = State.Error("decrypt_credential")`、`initialPassword = null` |
| 4.6 | `initialPassword` を ViewModel スコープに閉じ込める | CredentialEditViewModel | `_state: MutableStateFlow<EditState>` | Activity 破棄で ViewModel 破棄 → GC |
| 5.1 | focus 取得時に `transformationMethod = null`（平文表示） | CredentialEditActivity | `setOnFocusChangeListener` | hasFocus == true 分岐 |
| 5.2 | focus 喪失時に `transformationMethod = PasswordTransformationMethod.getInstance()`（masking） | CredentialEditActivity | `setOnFocusChangeListener` | hasFocus == false 分岐 |
| 5.3 | transformation 切替後のカーソル位置保持 | CredentialEditActivity | `setSelection(selectionStart, selectionEnd)` | 切替前後の selection を取得して再適用 |
| 5.4 | `endIconMode` と competing しない | CredentialEditActivity | `binding.passwordLayout.endIconMode = END_ICON_NONE` (Mode.Edit) | Q3 採用案 A による endIconMode の動的切替 |
| 5.5 | 切替は text を変更しない | CredentialEditActivity | `transformationMethod` のみ操作 | text には触れない |
| 5.6 | 画面回転時の masking 状態復元 | （OS 標準） | `EditText` state restoration | 本 Issue では明示的 state 保存しない |
| 6.1 | dirty 一致時 `newPassword = null`（温存） | CredentialEditViewModel | `save()` 内 dirty 判定 | `contentEquals` が true → newPassword = null |
| 6.2 | dirty 不一致時 `newPassword = <新値>` | CredentialEditViewModel | `save()` 内 dirty 判定 | `contentEquals` が false → newPassword = CharArray |
| 6.3 | 空文字 password で blank validation error | CredentialEditViewModel | `save()` 内 validation | 空文字検出 → State.FieldError(password) emit |
| 6.4 | dirty 判定は CharArray の内容等価 | CredentialEditViewModel | `CharArray.contentEquals` | 参照ではなく要素ごとの等価 |
| 6.5 | password 比較後の zero-fill | CredentialEditViewModel, UpdateCredentialUseCase | `CharArray.fill(' ')` ベストエフォート | `initialPassword` の CharArray 表現を破棄、use case の既存 zero-fill にも乗る |
| 7.1 | 既存 biometric / device credential unlock 経路を変えない | CredentialEditActivity | （変更なし） | screen 起動前の既存パスのまま |
| 7.2 | 復号のための追加 biometric prompt を出さない（案 A） | CredentialEditViewModel | `load()` 内で codec.decrypt + cipher.decrypt を **同一スコープで** 直接呼ぶ | 別 prompt を介さない |
| 7.3 | 復号失敗時 `State.Error` を emit、対応 State を空のまま保つ | CredentialEditViewModel | `load()` の catch | customField 側: rows 空 / editable = false、password 側: initialPassword null / inputPassword 空欄 |
| 7.4 | 復号値を ViewModel スコープ外へ持ち出さない | CredentialEditViewModel | `_customFields` + `_state` の StateFlow | Activity 破棄で ViewModel 破棄 → GC |
| 8.1 | 平文ログ禁止（customField / password） | CredentialEditViewModel, EncryptedCustomFieldsCodec, AesGcmCipher | `SafeLogger.info/warn/error` | 値・鍵・password を文字列に含めない |
| 8.2 | 件数等の集計のみログ可 | CredentialEditViewModel | `SafeLogger.info("count=N")` | NFR 4.1 |
| 8.3 | 復号失敗時に最小限のログ | CredentialEditViewModel | `SafeLogger.error(throwable = ex)` | 例外クラス名のみ |
| 8.4 | password masking 状態をログに残さない | CredentialEditActivity | （focus 切替で log を呼ばない） | listener 内で SafeLogger を呼び出さない |
| 9.1 | Phase 2 サジェスト UI の表示ゲート（editable == true）を変えない | CredentialEditViewModel | `refreshSuggestions()` 内の早期 return | Phase 2 §8.5 のロジックをそのまま流用 |
| 9.2 | Phase 2 detected_fields 記録経路を変えない | （触らない） | — | 本 Issue ではコード変更しない |
| 9.3 | `bindPackageForSuggestions(record.packageName)` を `load()` 内既存呼び出しのまま活用 | CredentialEditViewModel | `load()` 末尾の既存 `bindPackageForSuggestions` 呼び出し | Phase 2 で追加済みの行を残置 |
| 10.1-10.12 | テスト要件 | （tasks.md T5-T7 で配備） | — | §Testing Strategy で詳述 |

---

## Components and Interfaces

### UI / ViewModel Layer

#### CredentialEditViewModel（拡張）

| Field | Detail |
|---|---|
| Intent | 編集画面の state を集約。Mode.Edit でも customField / password を復号して編集可能にする |
| Requirements | 1.1, 1.2, 1.5, 2.1-2.6, 3.1-3.5, 4.1, 4.5, 4.6, 6.1-6.5, 7.2, 7.3, 7.4, 8.1-8.3, 9.3 |

**Responsibilities & Constraints**
- `load(credentialId)` 内で `EncryptedCustomFieldsCodec.decrypt` を呼び、結果を `CustomFieldsState.Row`
  に rowId を付与しながら map し、`editable = true` でセットする
- `load(credentialId)` 内で `AesGcmCipher.decrypt(EncryptedBlob(iv = record.passwordIv,
  ciphertext = record.passwordCiphertext))` を呼び、結果を `initialPassword: String` として
  UI state に格納する
- 復号失敗時は `_state.value = State.Error("decrypt_credential")` を emit し、
  `CustomFieldsState(rows = emptyList(), editable = false)` + `initialPassword = null` を保つ
  （Activity を閉じない、Req 4.5 / 7.3）
- reducer 群 (`addCustomFieldRow`, `removeCustomFieldRow`, `updateCustomFieldKey`, `updateCustomFieldValue`)
  は **モード分岐を持たず** `editable` フラグだけで通過判定する（NFR 5.1）。Phase 1 既存実装のまま
- `save()` の customField 三項は形式上残すが Mode.Edit でも `editable = true` 経路を通るため
  null パスは事実上到達しない。コメントで Phase 1.5 経緯を明記
- `save()` の password dirty 判定:
  - `typedPassword.contentEquals(initialPassword.toCharArray())` ならば `newPassword = null`
  - 一致しないなら `newPassword = typedPassword.toCharArray()` で渡す
  - typedPassword が空文字なら validation エラー emit（既存挙動を維持）
- 復号した plaintext は `StateFlow<CustomFieldsState>` / `StateFlow<EditState>` の中だけで保持し、
  Activity 破棄時 ViewModel destroy で GC 対象（NFR 1.3 / Req 4.6 / 7.4）
- `nextRowId` は ViewModel に持つ既存の単調増加カウンタを流用し、ロード行に対しても新規発番する
  （既存行と新規行の区別は不要、未決事項 §10 Q5 への回答: ID 戦略は単一カウンタで足りる）

**Dependencies**
- Inbound: `CredentialEditActivity.onCreate` → `viewModel.load(id)` (Critical)
- Outbound: `CredentialRepository.findById` (Critical), `EncryptedCustomFieldsCodec.decrypt` (Critical),
  `AesGcmCipher.decrypt` (Critical), `UpdateCredentialUseCase` (Critical), `SafeLogger` (Optional)

**Contracts**: Service [x] / API [ ] / Event [ ] / Batch [ ] / State [x]

##### Service Interface（拡張箇所のみ擬似コード）

```kotlin
class CredentialEditViewModel(
    private val repository: CredentialRepository,
    private val saveUseCase: SaveCredentialUseCase,
    private val updateUseCase: UpdateCredentialUseCase,
    private val deleteUseCase: DeleteCredentialUseCase,
    private val observeRecentDetectedFieldsUseCase: ObserveRecentDetectedFieldsUseCase,
    // NEW: Phase 1.5
    private val customFieldsCodec: EncryptedCustomFieldsCodec,
    private val aesGcmCipher: AesGcmCipher,
) : ViewModel() {

    suspend fun load(credentialId: Long): EncryptedCredentialRecord? {
        val record = repository.findById(CredentialId(credentialId)) ?: return null
        // (advanced details の既存 update はそのまま)

        // Phase 1.5: decrypt customFields under the existing unlock session (Q1 案 A).
        try {
            val decrypted: List<CustomField> = customFieldsCodec.decrypt(
                EncryptedBlob(iv = record.customFieldsIv, ciphertext = record.customFieldsCiphertext),
            )
            val rows = decrypted.map { cf ->
                CustomFieldsState.Row(rowId = nextRowId++, fieldKey = cf.fieldKey, value = cf.value)
            }
            _customFields.value = CustomFieldsState(rows = rows, editable = true)

            // Phase 1.5: decrypt password under the same unlock session.
            val passwordPlain: String = aesGcmCipher.decrypt(
                EncryptedBlob(iv = record.passwordIv, ciphertext = record.passwordCiphertext),
            ).toString(Charsets.UTF_8)
            _state.update { current -> current.copy(initialPassword = passwordPlain) }

            SafeLogger.info(TAG, "edit mode loaded (customFields=${rows.size}, password=loaded)")
        } catch (t: Throwable) {
            SafeLogger.error(TAG, "edit mode decrypt failed", throwable = t)
            _customFields.value = CustomFieldsState(rows = emptyList(), editable = false)
            _state.update { current -> current.copy(initialPassword = null) }
            _state.value = State.Error(cause = "decrypt_credential")
        }

        bindPackageForSuggestions(record.packageName)  // 既存呼び出しそのまま (Req 9.3)
        return record
    }

    fun save() {
        // ... 既存 validation ...

        val typed: CharArray = state.passwordInput.toCharArray()
        val initial: String? = state.initialPassword
        val newPasswordArg: CharArray? = when {
            typed.isEmpty() -> { /* emit blank validation error; return */ }
            initial != null && typed.contentEquals(initial.toCharArray()) -> null  // 未変更 → 温存
            else -> typed
        }
        val effectiveCustomFields = /* 既存 ロジック (filter blank fieldKey 等) */
        val input = UpdateCredentialInput(
            // ... 既存フィールド ...
            newPassword = newPasswordArg,
            customFields = if (_customFields.value.editable) effectiveCustomFields else null,  // null パスは事実上到達しない
        )
        updateUseCase(input)  // use case 側で newPassword の zero-fill が行われる
    }

    class Factory(
        private val repository: CredentialRepository,
        private val saveUseCase: SaveCredentialUseCase,
        private val updateUseCase: UpdateCredentialUseCase,
        private val deleteUseCase: DeleteCredentialUseCase,
        private val observeRecentDetectedFieldsUseCase: ObserveRecentDetectedFieldsUseCase,
        private val customFieldsCodec: EncryptedCustomFieldsCodec,  // NEW
        private val aesGcmCipher: AesGcmCipher,                     // NEW
    ) : ViewModelProvider.Factory { /* ... */ }
}
```

- Preconditions: `repository.findById` が non-null を返した直後にのみ decrypt を試行する
- Postconditions:
  - 成功時: `CustomFieldsState.editable = true` かつ `rows.size == decrypted.size`、
    `EditState.initialPassword != null`
  - 失敗時: `CustomFieldsState.editable = false` かつ `initialPassword = null`、
    `_state` が `State.Error` を保持
- Invariants: 復号した平文は ViewModel 破棄まで `StateFlow` の中だけに存在し、ログ・SharedPreferences
  には絶対に書き出さない。`initialPassword` の `String` 表現は zero-fill 不能だが、ViewModel
  destroy で参照が断たれ GC 対象となるよう保持期間を最短化する

#### CredentialEditActivity（変更あり: password 周り）

| Field | Detail |
|---|---|
| Intent | UI render を `CustomFieldsState` / `EditState` に追従させる。Phase 1 の `renderCustomFields()` を再利用しつつ、password の初期表示・focus toggle・endIconMode 切替を追加する |
| Requirements | 1.3, 1.4, 2.6, 4.2, 4.3, 4.4, 5.1-5.6, 8.4 |

**Responsibilities & Constraints**
- 既存 `renderCustomFields(state)` は `state.editable` を判定して view inflate / add CTA 表示を
  切り替える設計になっているため、ViewModel の state 切替に追従するだけで Mode.Edit でも正しく
  動作する
- **新規ロジック (password 周り)**:
  - `viewModel.state` を collect し、`initialPassword != null` が **初めて成立した瞬間** に
    `binding.inputPassword.setText(initialPassword)` を 1 回だけ呼ぶ（一度きり guard を持つ
    `var passwordInitialized = false` 等）
  - setText 直後に `binding.inputPassword.transformationMethod = PasswordTransformationMethod.getInstance()`
    で masking 初期化
  - Mode.Edit のみ `binding.passwordLayout.endIconMode = TextInputLayout.END_ICON_NONE` で
    endIcon を抑止（Q3 採用案 A）。Mode.New では layout 既定値 `password_toggle` を維持
  - Mode.Edit のみ `binding.inputPassword.setOnFocusChangeListener` を配線し、focus 取得で
    `transformationMethod = null` + selection 保持、focus 喪失で
    `transformationMethod = PasswordTransformationMethod.getInstance()` + selection 保持
  - 画面遷移直後に `inputPassword` に自動 focus が当たらないよう、`label` 入力欄を初期 focus に
    振る（`binding.inputLabel.requestFocus()` 等）または `android:windowSoftInputMode="stateHidden"`
    を Activity 設定で適用する

**Dependencies**
- Inbound: lifecycle → `onCreate` → `viewModel.load(id)`
- Outbound: `ServiceLocator.encryptedCustomFieldsCodec`, `ServiceLocator.aesGcmCipher`（Factory 引数）,
  `TextInputLayout.endIconMode` setter

**Contracts**: Service [ ] / API [ ] / Event [ ] / Batch [ ] / State [x]

### Security Layer（再利用、変更なし）

#### EncryptedCustomFieldsCodec（既存）

| Field | Detail |
|---|---|
| Intent | `List<CustomField>` ⇔ AES-GCM `EncryptedBlob` の双方向変換 |
| Requirements | 1.1, 1.5, 3.2, 3.4, 3.5, 7.2 |

**Responsibilities & Constraints**
- 本 Issue では **変更しない**。`decrypt(EncryptedBlob)` を ViewModel から呼ぶことが新規ユースケース
- 空 BLOB → 空 list の fail-open 仕様 (Phase 1 §6.1) は Req 1.5 をそのまま満たす
- JSON parse 失敗は内部で warn + 空 list 返却 (Phase 1 §5.4)。これも本 Issue の Req 7.3 観点では
  「rows 空 + editable = false にはならない（成功扱いで rows 空 + editable = true になる）」が、
  これは Phase 1 で確立した fail-open ポリシーに従う想定挙動とする

**Dependencies**
- Inbound: `UnlockVaultUseCase`, `UpdateCredentialUseCase`（既存）, **`CredentialEditViewModel`（新規）**
- Outbound: `AesGcmCipher` (Critical)

**Contracts**: Service [x]

#### AesGcmCipher（既存、再利用）

| Field | Detail |
|---|---|
| Intent | AES-GCM での `ByteArray` ⇔ `EncryptedBlob` 双方向変換。Android Keystore 内 master key を利用 |
| Requirements | 4.1, 4.5, 6.5, 7.2 |

**Responsibilities & Constraints**
- 本 Issue では **変更しない**。`decrypt(EncryptedBlob): ByteArray` を ViewModel から呼ぶことが
  新規ユースケース（既存は `UnlockVaultUseCase` / `UpdateCredentialUseCase` / codec 内）
- Keystore alias 経由でステートレスに動作するため、ViewModel スコープから安全に呼び出せる

**Dependencies**
- Inbound: `UnlockVaultUseCase`, `UpdateCredentialUseCase`, `EncryptedCustomFieldsCodec`,
  **`CredentialEditViewModel`（新規）**
- Outbound: Android Keystore

**Contracts**: Service [x]

### Use Case Layer（既存、変更なし）

#### UpdateCredentialUseCase（既存）

| Field | Detail |
|---|---|
| Intent | `EncryptedCredentialRecord` の更新。`newPassword != null` のとき password を再暗号化、`customFields != null` のとき codec で再 encrypt |
| Requirements | 3.1, 3.2, 3.4, 6.1, 6.2, 6.5 |

**Responsibilities & Constraints**
- 本 Issue では **変更しない**。`UpdateCredentialInput.customFields` の null/non-null セマンティクスは
  そのまま：non-null（空 list 含む）であれば codec で再 encrypt して既存 ciphertext を上書きする
  （NFR 2.3 維持）
- `UpdateCredentialInput.newPassword` の null/non-null セマンティクスもそのまま：null なら既存
  ciphertext を温存、non-null なら再暗号化して上書きし、入力 `CharArray` を zero-fill する
  （NFR 2.4 維持）
- 結果として「削除された customField を DB から取り除く」要件（Req 3.2）「未変更 password を
  温存する」要件（Req 6.1）は既存ロジックでカバー済み

**Dependencies**
- Inbound: `CredentialEditViewModel.save()`
- Outbound: `CredentialRepository.update`, `EncryptedCustomFieldsCodec.encrypt`, `AesGcmCipher.encrypt`,
  `PackageSignatureResolver`

**Contracts**: Service [x]

### DI Layer

#### ServiceLocator（小規模拡張）

| Field | Detail |
|---|---|
| Intent | `EncryptedCustomFieldsCodec` および `AesGcmCipher` のシングルトンインスタンスを ViewModelFactory へ提供 |
| Requirements | 1.1, 4.1, 7.2 |

**Responsibilities & Constraints**
- Phase 1 で codec / cipher のインスタンスは生成済みのはずなので、それを
  `val encryptedCustomFieldsCodec` / `val aesGcmCipher` として expose する
  （既に存在すれば変更不要、なければ追加）

**Contracts**: Service [ ] / API [ ] / Event [ ] / Batch [ ] / State [ ]

---

## Data Models

### Domain Model（既存、変更なし）

- `EncryptedCredentialRecord.passwordCiphertext: ByteArray` / `passwordIv: ByteArray`（Phase 0 から
  存在、本 Issue で読み出しを追加）
- `EncryptedCredentialRecord.customFieldsCiphertext: ByteArray` / `customFieldsIv: ByteArray`
  （Phase 1 で導入済み、本 Issue で変更なし）
- `CustomField(fieldKey: String, value: String)`（Phase 1 で導入済み、変更なし）
- `CustomFieldsState(rows: List<Row>, editable: Boolean)` / `CustomFieldsState.Row(rowId, fieldKey, value)`
  （Phase 1 で導入済み、本 Issue ではフィールド追加なし）

### Domain Model（新規 / 拡張）

- `CredentialEditViewModel.EditState`（または既存の `State.Loaded` 系プロパティ）に
  **`initialPassword: String?`** フィールドを追加。`null` は「未復号 / 復号失敗 / Mode.New」を意味する。
  Mode.New では常に `null` のまま運用される

### Logical / Physical Data Model

**Room schema**: 変更なし。`CredentialEntity.customFieldsCiphertext` / `customFieldsIv` 列は
Phase 1 で `Migration_2_3` により導入済み。`passwordCiphertext` / `passwordIv` 列は Phase 0 以前
から存在。本 Issue は読み書きのみ。

---

## Error Handling

### Error Strategy

- **decrypt 成功**: 通常パス。`CustomFieldsState(rows = decrypted, editable = true)` +
  `EditState.initialPassword = passwordPlain` を emit
- **decrypt 失敗 (AES-GCM auth tag mismatch, key 不在, IV 不整合 など)**:
  catch して `State.Error("decrypt_credential")` を emit、`CustomFieldsState(emptyList(), editable = false)`
  + `initialPassword = null` を維持。Activity 側 `renderState` は既存の Snackbar 表示パスに乗る。
  **画面は閉じない**（Req 4.5 / 7.3）
- **JSON parse 失敗 (customField のみ)**: codec 内部で warn + 空 list 返却（Phase 1 既存挙動）。
  ViewModel 側からは「rows 空 + editable = true」の正常系として扱われる。Phase 1 で確定済みの
  fail-open ポリシー継承
- **空 BLOB（Migration_2_3 直後で未 re-save、customField のみ）**: codec が空 list を返す。
  ViewModel は通常パスで `editable = true` をセット。「フィールド追加」→ save で初めて非空
  ciphertext が書き戻される
- **save 時 password 空欄**: `inputPassword.text` が空文字のとき、既存の blank validation で
  `State.FieldError(password)` を emit して save を中断する（Req 6.3）

### Error Categories and Responses

- **User Errors (ユーザー入力起因)**:
  - 既存 `State.FieldError` パスをそのまま利用。本 Issue で customField 専用の新規 error kind は
    追加しない
  - password 空欄は既存 `State.FieldError(password)` で対応（既存挙動を維持）
- **System Errors (decrypt 失敗)**:
  - `State.Error("decrypt_credential")` を新たな cause 文字列で emit
  - `renderState` は既存 `R.string.error_save_failed_with_reason` フォーマッタに渡って Snackbar 表示される
  - 詳細メッセージ／リトライ UI は未決事項 §10 Q6 として後続フェーズへ繰越
- **Logging Errors**: `SafeLogger.error(tag, message, throwable = ex)` で例外クラス名のみ記録。
  ciphertext / IV / 平文 / password は決して文字列化しない（Req 8.1 / 8.3）

---

## Testing Strategy

### Unit Tests（新規 / 拡張）

#### customField (CredentialEditViewModelEditModeCustomFieldsTest)

1. `loadInEditMode_decryptsAndExposesRows` — 2 件の customField を仕込んだ
   `EncryptedCredentialRecord` で load → `customFields.value.rows.size == 2` + `editable == true`
   （Req 1.1, 1.2, 10.1, 10.2）
2. `loadInEditMode_emptyCiphertext_yieldsEditableEmptyRows` — `customFieldsCiphertext = ByteArray(0)`
   の record で load → rows 空 + `editable = true`（Req 1.5）
3. `editModeReducers_addRemoveUpdate_workEndToEnd` — load 後に reducer を呼び、行追加・既存行
   fieldKey/value 編集・削除を検証（Req 2.1-2.4, 10.3）
4. `editModeSave_passesEditedListToUpdateUseCase` — fake update use case で
   `UpdateCredentialInput.customFields` の中身を assert（Req 3.1, 10.4）
5. `editModeRoundTrip_preservesCustomFieldsValuesSemantically` — load → 無編集 → save → 再 load で
   同じ集合になることを検証（Req 3.5, 10.5 customField 側）
6. `editModeDecryptFailure_emitsErrorAndKeepsLocked` — codec.decrypt が throw する stub →
   `State.Error` + rows 空 + `editable = false`（Req 7.3, 10.6 customField 側）

#### password (CredentialEditViewModelEditModePasswordTest)

7. `loadInEditMode_decryptsPasswordToInitialPassword` — `EncryptedCredentialRecord` で load →
   `state.value.initialPassword == decryptedPlain`（Req 4.1, 10.7）
8. `editModeSave_unchangedPassword_passesNullToUpdateInput` — load 後 `inputPassword.text` を
   `initialPassword` と同値のまま save → `UpdateCredentialInput.newPassword == null`（Req 6.1, 10.8）
9. `editModeSave_changedPassword_passesNewCharArrayToUpdateInput` — load 後 typedPassword を変更 →
   `UpdateCredentialInput.newPassword?.concatToString() == changed`（Req 6.2, 10.9）
10. `editModeSave_blankPassword_emitsValidationErrorAndAborts` — typedPassword を空文字 →
    `State.FieldError(password)` emit + save が中断（Req 6.3, 10.10）
11. `editModeRoundTrip_preservesPasswordSemantically` — load → 無編集 → save → 再 load で同じ
    plaintext（Req 10.5 password 側）
12. `editModeDecryptFailure_emitsErrorAndKeepsPasswordNull` — cipher.decrypt が throw → `State.Error`
    + `initialPassword == null`（Req 4.5, 10.6 password 側）

### Integration / UI Tests

13. `CredentialEditActivityPasswordFocusTest.focusGain_appliesPlaintextMode` — Robolectric /
    Espresso で `inputPassword.requestFocus()` → `transformationMethod == null`（Req 5.1, 10.11）
14. `CredentialEditActivityPasswordFocusTest.focusLoss_appliesMaskingMode` — focus を別の field
    に移す → `transformationMethod is PasswordTransformationMethod`（Req 5.2, 10.11）
15. `CredentialEditActivityPasswordFocusTest.toggle_preservesCursorPosition` — typedPassword に
    selection を設定 → focus 切替 → selection が保持されている（Req 5.3, 10.11）
16. 既存 `CredentialEditViewModelCustomFieldsTest`（Phase 1）が **全件 pass** することを確認（Req 10.12）
17. 既存 `CredentialEditViewModelSuggestionTest`（Phase 2）が **全件 pass** することを確認（Req 9.1, 9.2, 10.12）。
    特に Phase 2 §8.5 「editable = false で suggest UI 非表示」テストが、本 Issue では Mode.Edit でも
    editable = true になるため、もし Mode.Edit を仮定したテストがあれば挙動の妥当性を再評価する
18. 既存 `UpdateCredentialUseCaseTest` / `SaveCredentialUseCaseCustomFieldsTest` /
    `UnlockVaultUseCaseCustomFieldsTest` / `EncryptedCustomFieldsCodecTest` /
    `Migration_1_2_Test` / `Migration_2_3_Test` が pass することを確認

### E2E / UI Tests

本 Issue では Espresso による完全な end-to-end test は追加しない（Phase 1 / 2 でも UI ロジックは
ViewModel に集約して unit test 化する方針を踏襲）。`renderCustomFields()` は Phase 1 で動作実証
済みのため、ViewModel の state 切替を通じて間接的にカバーする。focus toggle は Activity ロジック
に依存するため、Robolectric / Activity 単体 test で最低限のカバレッジを設ける。

### Performance / Load

- customField 10 件 (= MAX) の AES-GCM decrypt + JSON parse、および password 1 件の AES-GCM
  decrypt を `load()` 内で実行しても 10ms 未満で完了する想定（既存 password decrypt と同オーダー）。
  専用の performance test は追加しない
- focus toggle は `transformationMethod` セットと `setSelection` のみで、16ms 以内に完了する想定
- 必要なら NFR 3.2 / 3.3 を満たすかは手動 QA で確認

---

## Security Considerations

- **復号 trigger**: 既存の biometric / device credential unlock を通過した後にのみ編集画面に到達する
  という運用前提を維持（Req 7.1）。追加 prompt は提示しない（Q1 採用案 A、Req 7.2）
- **plaintext lifetime**:
  - 復号した `List<CustomField>` は `CustomFieldsState.Row` に展開後、ViewModel スコープの
    `StateFlow` 内のみに存在。Activity 破棄で ViewModel destroy → GC（Req 7.4 / NFR 1.1 / NFR 1.3）
  - 復号した password は `EditState.initialPassword: String?` として ViewModel スコープに保持。
    `String` は immutable で zero-fill 不能だが、ViewModel destroy で参照が断たれ GC 対象となる。
    保持期間は load() ~ Activity 破棄まで（NFR 1.1 / 1.3 / 1.4）
  - dirty 判定後、`CharArray` 表現は use case 内で zero-fill される（既存 `UpdateCredentialUseCase`
    の責務、NFR 1.4）
- **ログ**: `SafeLogger.info` で件数のみ、`SafeLogger.error` で例外クラス名のみ。
  `fieldKey` / `value` / ciphertext / IV / password は一切ログに残さない（Req 8.1, 8.2, 8.3 /
  NFR 4.1, NFR 4.2）。focus toggle 状態 (masked / plaintext) もログしない（NFR 4.3 / Req 8.4）
- **暗号化方式の不変性**: AES-GCM / Android Keystore / `EncryptedCustomFieldsCodec` / `AesGcmCipher`
  を変更しない（NFR 1.2、制約 4）
- **初期 focus 抑制**: 編集画面遷移直後に `inputPassword` に自動 focus が当たらないようにすることで、
  shoulder surfing リスクを低減（Req 4.4 / §9 Q5）
- **`endIconMode` 動的切替**: Mode.Edit で `END_ICON_NONE` に切り替えることで、focus toggle と
  endIcon toggle の競合を回避し、ユーザーの意図解釈を一意にする（Req 5.4 / §9 Q3 採用案 A）

---

## Performance & Scalability

- **load() 復号オーバーヘッド**: customField 10 件で AES-GCM decrypt 1 回 + JSON deserialize 1 回、
  および password 1 件の AES-GCM decrypt 1 回。既存 password decrypt と同 dispatcher
  （`viewModelScope` 経由 = Default）で実行され、UI スレッドをブロックしない（NFR 3.1）
- **focus toggle のオーバーヘッド**: `transformationMethod` 切替 + `setSelection` のみで、
  16ms 以内（1 フレーム以内）に完了する想定（NFR 3.3）
- **save() の追加コスト**: 既存 `UpdateCredentialUseCase` パスのまま codec.encrypt + cipher.encrypt
  （password 変更時のみ）が走るだけで、本 Issue による追加の I/O はない。dirty 判定で未変更 password
  の暗号化が省略されることで、むしろわずかに save コストが減る

---

## Migration Strategy

**Room migration なし** (制約 7)。Phase 1 で導入した `Migration_2_3` で初期化された空 BLOB の
credential は、本 Issue 適用後に編集画面で開くと「customField 0 件 + editable = true」として表示され、
ユーザーが追加・save した時点で初めて非空 ciphertext が書き戻される（Req 1.5 / NFR 2.2）。

password ciphertext は Phase 0 以前から存在しているため、本 Issue 適用後に編集画面で開くと
`initialPassword` に既存値が復号展開される。ユーザーが一切編集せず save した場合は dirty 判定で
`newPassword = null` が渡され、既存 ciphertext が温存される（Req 6.1）。

ロールバック戦略: 本 Issue を revert しても、save 済みの ciphertext は Phase 1 のロード経路
（`UnlockVaultUseCase`）から引き続き読める。前方互換性は保たれる。

---

## 自己レビュー結果

- [x] Requirements traceability: requirements.md の全 numeric ID（1.1-1.5, 2.1-2.6, 3.1-3.5,
      4.1-4.6, 5.1-5.6, 6.1-6.5, 7.1-7.4, 8.1-8.4, 9.1-9.3, 10.1-10.12, NFR 1-5）が
      design.md / tasks.md の `_Requirements:_` で参照されている
- [x] File Structure Plan の充填: 具体的なファイルパスを列挙、"TBD" なし
- [x] orphan component なし: Components 名（CredentialEditViewModel, CredentialEditActivity,
      EncryptedCustomFieldsCodec, AesGcmCipher, UpdateCredentialUseCase, ServiceLocator）が
      File Structure Plan に対応
- [x] tasks.md の各タスクが独立にコミット可能な粒度
- [x] `(P)` タスクには `_Boundary:_` が明示されている

---

## 末尾制約（再掲）

- Room schema を変更しない（制約 7）
- Phase 1 の AES-GCM / Android Keystore / JSON シリアライザを変更しない（制約 4）
- Phase 2 (#67) の detection / suggestion ロジックを変更しない（制約 5）
- Credential Manager API 経路に変更を加えない（制約 6）
- 追加 biometric prompt を導入しない（制約 8 / Q1 採用案 A）
- Mode.New の挙動を変更しない（制約 9 / NFR 2.1 / NFR 5.2）
- 直接 `develop` / `main` への push を行わない（制約 2）
