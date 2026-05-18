# Design Document

## Overview

**Purpose**: Phase 1 (#66 / PR #69) で `Credential` に customField を導入したが、`Mode.Edit` では
`CredentialEditViewModel.load()` が `CustomFieldsState(rows = emptyList(), editable = false)` を
固定で割り当てているため、既存 customField の表示・追加・編集・削除が一切できない。本 Issue
(Phase 1.5) では編集画面 load 時に既存 customField を **既存 unlock セッションと同じスコープで**
復号して `rows` に展開し、`editable = true` に切り替え、`Mode.Edit` でも `Mode.New` と同じ
編集体験を提供する。

**Users**: 既に credential を登録済みのユーザーが、業務アプリの会員番号など後付けの customField
を追記する、登録ミスを訂正する、不要 customField を削除する、といった日常的な保守 workflow で
利用する。

**Impact**: 「customField を直したいときに credential を作り直すしかない」という運用上の障害を
取り除く。本変更は **ViewModel・Activity UI・use case 入力 DTO の null セマンティクス整理** の
3 レイヤにまたがる中規模変更だが、Room schema・暗号化方式・既存 use case 構造には触れない。
Phase 2 (#67) のサジェスト UI は `editable == true` を表示ゲートとしているため、本 Issue の merge
後に Mode.Edit でも自動的に有効化される（Phase 2 設計 §8.5 の想定どおり）。

### Goals

- `Mode.Edit` 遷移時に既存 `customFieldsCiphertext` / `customFieldsIv` を **追加 biometric prompt
  なしで** 復号し、`CustomFieldsState.rows` に DB 格納順で展開する（Q1 採用案 A）
- `Mode.Edit` でも `editable = true` を割り当て、`Mode.New` と同一 reducer・同一 UI で
  add / remove / update / save が動作する（NFR 5 = 完全に同一コードパス）
- 保存時に編集後の `customFields` を `UpdateCredentialInput.customFields` に詰めて
  既存 ciphertext を上書きし、削除された行を DB から除去する
- 復号失敗時は `State.Error` を emit しつつ `rows` 空・`editable = false` のまま編集画面を保つ
- Phase 2 (#67) のサジェスト・検出ロジックを変更しないこと

### Non-Goals

- customField の暗号化方式・鍵管理の変更（既存 AES-GCM / Android Keystore / `EncryptedCustomFieldsCodec`
  をそのまま再利用）
- customField 表示時の value masking 行単位トグル（Phase 1 Q3 と同じく plain text、本 Issue でも導入しない）
- customField の並び順カスタマイズ UI（別 Issue）
- username / password / label の編集挙動の変更（`Mode.Edit` で password 空欄スタート維持）
- Phase 2 (#67) の detection / suggestion ロジック本体への変更
- Room schema の変更（migration なし）

---

## Architecture

### Existing Architecture Analysis

Phase 1 (#66) で確立した境界をそのまま踏襲する:

- **Domain layer**: `EncryptedCredentialRecord` が `customFieldsCiphertext` / `customFieldsIv`
  (両 `ByteArray`) を保持。`UpdateCredentialInput.customFields: List<CustomField>?` は **null =
  既存ciphertext温存 / non-null（空 list 含む） = 上書き** のセマンティクス。
- **Security layer**: `EncryptedCustomFieldsCodec` が `List<CustomField>` ⇔ AES-GCM `EncryptedBlob`
  を双方向で扱う。空 BLOB を decrypt すると空 list を返す fail-open 仕様（Phase 1 §6.1）。
  鍵は Android Keystore 内の AES key で、`UnlockVaultUseCase` 内 password decrypt と同一鍵を使用。
- **Use case layer**: `UnlockVaultUseCase` が既に decrypt path を持つ。`UpdateCredentialUseCase`
  が `customFields != null` の場合 codec で再 encrypt して `EncryptedCredentialRecord` を build。
- **UI layer**: `CredentialEditViewModel.load(credentialId)` で `repository.findById` を呼び、
  `EncryptedCredentialRecord` を直接受け取る。`CustomFieldsState.editable` フラグで reducer の
  通過可否を制御し、Activity 側 `renderCustomFields()` は `editable` をそのまま尊重して
  add CTA の表示・行 view 描画を切り替えるパターンが完成済み。
- **Phase 2 (#67) の追加**: `CredentialEditViewModel` に `SuggestionState` / `bindPackageForSuggestions`
  / `refreshSuggestions()` を導入。`refreshSuggestions()` 冒頭で `state.editable == false` を
  見て early return しているため (Phase 2 §8.5)、本 Issue で `editable = true` に切り替えると
  **追加コードなし** で Mode.Edit でもサジェスト UI が動く。

**尊重する制約**:
- 復号した plaintext は ViewModel スコープ（`StateFlow<CustomFieldsState>`）に閉じ込め、Activity
  破棄時に GC 対象とする（既存 password CharArray と同じ lifecycle 規約）。
- ログには customField の `fieldKey` / `value` を平文出力しない（`SafeLogger` 規約、Phase 1 NFR 2）。
- AES-GCM 失敗時は username/password decrypt と同じ深刻度として扱うが、本画面は既に load された
  EncryptedCredentialRecord（username plaintext を含む）にアクセス済みなので、復号失敗を `State.Error`
  に橋渡しすれば足りる。

### Architecture Pattern & Boundary Map

採用パターン: **既存 MVVM + UseCase 構造の継続**。新規コンポーネントは導入せず、Phase 1 で導入した
`EncryptedCustomFieldsCodec` を ViewModel から **直接呼び出す** 案を採用する（後述 §代替案比較）。

```mermaid
flowchart LR
  A[CredentialEditActivity.onCreate] --> B[viewModel.load(id)]
  B --> C[repository.findById]
  C --> D[EncryptedCredentialRecord]
  D --> E[EncryptedCustomFieldsCodec.decrypt]
  E -- success --> F[CustomFieldsState rows=decrypted, editable=true]
  E -- throw --> G[State.Error + editable=false]
  F --> H[renderCustomFields → rows 表示 + add CTA 有効化]
  H --> I[reducer: add/remove/update]
  I --> J[viewModel.save → UpdateCredentialInput.customFields = effective]
  J --> K[UpdateCredentialUseCase → codec.encrypt → repository.update]
```

**Architecture Integration**:
- 採用パターン: **MVVM + use case + 既存 codec**（Phase 1 構造の継続）
- ドメイン／機能境界: ViewModel が UI state を所有、codec が暗号 ↔ 平文変換を所有、use case が
  validation + persistence を所有。本 Issue では境界を変えず、ViewModel の `load()` 内処理を
  「rows 空固定」から「codec.decrypt 呼び出し」に置き換えるのみ。
- 既存パターンの維持: `editable` フラグによる reducer ゲート、`SuggestionState.refreshSuggestions()`
  の早期 return パターン、`UpdateCredentialInput.customFields` の null セマンティクス
- 新規コンポーネント: なし。既存 `EncryptedCustomFieldsCodec` を ViewModel に inject する
  ファクトリ拡張のみ

### 代替案検討（採用しなかった案の記録）

| 案 | 内容 | 採用可否と理由 |
|---|---|---|
| **A（採用）** | ViewModel が `EncryptedCustomFieldsCodec` を直接 inject して `load()` 内で decrypt | 最小変更。Phase 1/2 と一貫性。codec は元々 ViewModel スコープのライフタイム前提で設計されている |
| B | 新 use case `LoadCredentialWithCustomFieldsUseCase` を導入 | use case 層に "ViewModel 専用 helper" を増やす過剰抽象化。`PlaintextCredential` を返す `UnlockVaultUseCase` は password 復号と紐づいておりここでは不要 |
| C | `UnlockVaultUseCase` を編集画面でも呼ぶ | password も decrypt してしまい不要な plaintext 露出。ライフタイム責務（`AutoCloseable`）が editor flow に合わない |

採用理由の補足: codec は Phase 1 で既に `UnlockVaultUseCase` と `UpdateCredentialUseCase` の両方
から呼ばれており、ViewModel から呼んでも責務逸脱にならない（純粋関数的な変換）。

### Technology Stack

| Layer | Choice / Version | Role in Feature | Notes |
|-------|------------------|-----------------|-------|
| UI | Android View binding + Kotlin Coroutines + StateFlow | `renderCustomFields` で rows を描画 / `editable` で add CTA 切替 | 既存実装そのまま再利用 |
| ViewModel | androidx.lifecycle.ViewModel + StateFlow | `load()` 内 decrypt / reducer の `editable` ゲート / `save()` の null 撤廃 | Factory に codec inject 追加 |
| Use Case | `UpdateCredentialUseCase` | `customFields != null` 時に既存 ciphertext を上書き | **変更なし**（既存パスに乗る） |
| Security | `EncryptedCustomFieldsCodec` (AES-GCM + Android Keystore) | `decrypt(EncryptedBlob)` を ViewModel から呼ぶ | **変更なし** |
| Persistence | Room (`CredentialEntity.customFieldsCiphertext` / `customFieldsIv`) | 読み書きのみ、schema 変更なし | Migration 不要 |
| DI | `ServiceLocator` | `ViewModelProvider.Factory` への codec 引き渡しを追加 | Phase 1 で codec は既に登録済み |

---

## File Structure Plan

### Directory Structure（変更されるパス相当のみ抜粋）

```
app/src/main/java/io/github/hitoshiichikawa/keynest/
├── ui/edit/
│   ├── CredentialEditViewModel.kt        # 主変更: load() / save() / Factory
│   └── CredentialEditActivity.kt         # 変更ほぼ無し（既存 renderCustomFields 流用、確認のみ）
├── di/
│   └── ServiceLocator.kt                 # 必要なら customFieldsCodec の expose を確認
└── security/
    └── EncryptedCustomFieldsCodec.kt     # 変更なし（reference として参照）

app/src/test/java/io/github/hitoshiichikawa/keynest/
├── ui/
│   ├── CredentialEditViewModelCustomFieldsTest.kt
│   │   # 既存 Phase 1 reducer テストを fail させないことの担保 + Mode.Edit 行追加 / 編集 / 削除を追加
│   └── edit/
│       ├── CredentialEditViewModelSuggestionTest.kt
│       │   # 既存 Phase 2 テストを fail させないこと（editable = true へ切り替わってもサジェスト挙動が崩れない確認）
│       └── CredentialEditViewModelEditModeCustomFieldsTest.kt  ← 新規追加
│           # Mode.Edit 専用: load 時 decrypt 展開 / editable = true / save 時上書き / 復号失敗 → State.Error
└── domain/usecase/
    └── FakeCredentialRepository.kt       # 必要に応じて customFields 込み record を返す helper 追加
```

### Modified Files

- `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditViewModel.kt`
  - `load(credentialId)`: 復号した `List<CustomField>` を rowId 付与で `CustomFieldsState.Row` に
    map → `CustomFieldsState(rows = mapped, editable = true)` を emit。
  - `load(credentialId)`: 復号失敗 (`Throwable` catch) 時に `_state.value = State.Error(cause = "decrypt_custom_fields")`
    を emit し、`CustomFieldsState(emptyList(), editable = false)` を維持。
  - `save()`: `customFields = if (_customFields.value.editable) effectiveCustomFields else null`
    の三項を **`Mode.Edit` でも `editable = true` が成立するため事実上 non-null パスへ収束** する。
    既存ロジックを変更せずに済むが、コメントを「Phase 1.5 で edit mode も editable=true になり、
    null パスは事実上到達しない」と更新する（NFR 2.3 のセマンティクス維持を文書化）。
  - 既存 reducer (`addCustomFieldRow` / `removeCustomFieldRow` / `updateCustomFieldKey` /
    `updateCustomFieldValue`) は **変更しない**（NFR 5: モード分岐を内部に持たない）。
  - `Factory` クラスに `customFieldsCodec: EncryptedCustomFieldsCodec` パラメータ追加、
    `ViewModelProvider.Factory.create` で ViewModel コンストラクタに引き渡し。
  - companion の `TAG` を流用して `SafeLogger.info("edit mode decrypted: count=${rows.size}")`
    を出力（値は含めない、NFR 4.1）。
- `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditActivity.kt`
  - `CredentialEditViewModel.Factory` の呼び出し箇所に `ServiceLocator.encryptedCustomFieldsCodec`
    を追加（呼び出し点 1 か所）。
  - `renderCustomFields` / `binding.btnAddCustomField` 周りは既存実装で `editable` を尊重して
    いるため **本体ロジック変更なし**。Mode.Edit でも自然に既存行 / add CTA が表示される。
- `app/src/main/java/io/github/hitoshiichikawa/keynest/di/ServiceLocator.kt`
  - Phase 1 で codec が既に組み立てられている場合は公開プロパティ `encryptedCustomFieldsCodec` を
    expose（存在しなければ追加）。`ViewModelProvider.Factory` に渡せる単一インスタンスを確保する。

### New Files

- `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditViewModelEditModeCustomFieldsTest.kt`
  - Mode.Edit 専用テスト。load 時の decrypt 展開、`editable = true` 確認、save 時に
    `UpdateCredentialInput.customFields` に編集後リストが渡ることを `FakeUpdateCredentialUseCase` 経由で検証、
    decrypt 失敗時の `State.Error` 検証、ラウンドトリップ等価性検証。

---

## Requirements Traceability

| Requirement | Summary | Components | Interfaces / Method | Flows |
|---|---|---|---|---|
| 1.1 | Mode.Edit で既存 customField を復号して rows に展開 | CredentialEditViewModel, EncryptedCustomFieldsCodec | `load()`, `codec.decrypt()` | load → findById → codec.decrypt → CustomFieldsState |
| 1.2 | Mode.Edit でも `editable = true` を保持 | CredentialEditViewModel | `load()` reducer | 同上 |
| 1.3 | DB 0 件のとき add CTA のみ表示 | CredentialEditActivity | `renderCustomFields()` | rows = [], editable = true → add CTA 表示 |
| 1.4 | 既存行を入力可能なテキストフィールドで表示 | CredentialEditActivity | `renderCustomFields()` | editable = true 経路で view inflate |
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
| 4.1 | 既存 biometric / device credential unlock 経路を変えない | CredentialEditActivity | （変更なし） | screen 起動前の既存パスのまま |
| 4.2 | 復号のための追加 biometric prompt を出さない（案 A） | CredentialEditViewModel | `load()` 内で codec.decrypt を **同一スコープで** 直接呼ぶ | 別 prompt を介さない |
| 4.3 | 復号失敗時 `State.Error` を emit, rows 空 / editable = false | CredentialEditViewModel | `load()` の catch | catch → `_state.value = State.Error("decrypt_custom_fields")` |
| 4.4 | 復号値を ViewModel スコープ外へ持ち出さない | CredentialEditViewModel | `_customFields: MutableStateFlow` | Activity 破棄で ViewModel 破棄 → GC |
| 5.1 | 平文ログ禁止 | CredentialEditViewModel, EncryptedCustomFieldsCodec | `SafeLogger.info/warn/error` | 値・鍵を文字列に含めない |
| 5.2 | 件数等の集計のみログ可 | CredentialEditViewModel | `SafeLogger.info("count=N")` | NFR 4.1 |
| 5.3 | 復号失敗時に最小限のログ | CredentialEditViewModel | `SafeLogger.error(throwable = ex)` | 例外クラス名のみ |
| 6.1 | Phase 2 サジェスト UI の表示ゲート（editable == true）を変えない | CredentialEditViewModel | `refreshSuggestions()` 内の早期 return | Phase 2 §8.5 のロジックをそのまま流用 |
| 6.2 | Phase 2 detected_fields 記録経路を変えない | （触らない） | — | 本 Issue ではコード変更しない |
| 6.3 | `bindPackageForSuggestions(record.packageName)` を `load()` 内既存呼び出しのまま活用 | CredentialEditViewModel | `load()` 末尾の既存 `bindPackageForSuggestions` 呼び出し | Phase 2 で追加済みの行を残置 |
| 7.1-7.7 | テスト要件 | （tasks.md T4, T5 で配備） | — | §Testing Strategy で詳述 |

---

## Components and Interfaces

### UI / ViewModel Layer

#### CredentialEditViewModel（拡張）

| Field | Detail |
|---|---|
| Intent | 編集画面の state を集約。Mode.Edit でも customField を復号して編集可能にする |
| Requirements | 1.1, 1.2, 1.5, 2.1-2.6, 3.1-3.5, 4.2, 4.3, 4.4, 5.1-5.3, 6.3 |

**Responsibilities & Constraints**
- `load(credentialId)` 内で `EncryptedCustomFieldsCodec.decrypt` を呼び、結果を `CustomFieldsState.Row`
  に rowId を付与しながら map し、`editable = true` でセットする。
- 復号失敗時は `_state.value = State.Error("decrypt_custom_fields")` を emit し、`CustomFieldsState(rows = emptyList(), editable = false)`
  を保つ（Activity を閉じない、Req 4.3）。
- reducer 群 (`addCustomFieldRow`, `removeCustomFieldRow`, `updateCustomFieldKey`, `updateCustomFieldValue`)
  は **モード分岐を持たず** `editable` フラグだけで通過判定する（NFR 5）。Phase 1 既存実装のまま。
- `save()` の `if (_customFields.value.editable) effective else null` 三項は形式上残すが、
  Mode.Edit でも `editable = true` 経路を通るため null パスは事実上到達しない。
  コメントで Phase 1.5 経緯を明記。
- 復号した plaintext は `StateFlow<CustomFieldsState>` 内でのみ保持し、Activity 破棄時 ViewModel
  destroy で GC 対象（NFR 1.3 / Req 4.4）。
- `nextRowId` は ViewModel に持つ既存の単調増加カウンタを流用し、ロード行に対しても新規発番する
  （既存行と新規行の区別は不要、未決事項 §10 Q5 への回答: ID 戦略は単一カウンタで足りる）。

**Dependencies**
- Inbound: `CredentialEditActivity.onCreate` → `viewModel.load(id)` (Critical)
- Outbound: `CredentialRepository.findById` (Critical), `EncryptedCustomFieldsCodec.decrypt` (Critical),
  `UpdateCredentialUseCase` (Critical), `SafeLogger` (Optional)

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
            SafeLogger.info(TAG, "edit mode customFields loaded (count=${rows.size})")
        } catch (t: Throwable) {
            SafeLogger.error(TAG, "edit mode customFields decrypt failed", throwable = t)
            _customFields.value = CustomFieldsState(rows = emptyList(), editable = false)
            _state.value = State.Error(cause = "decrypt_custom_fields")
        }

        bindPackageForSuggestions(record.packageName)  // 既存呼び出しそのまま (Req 6.3)
        return record
    }

    class Factory(
        private val repository: CredentialRepository,
        private val saveUseCase: SaveCredentialUseCase,
        private val updateUseCase: UpdateCredentialUseCase,
        private val deleteUseCase: DeleteCredentialUseCase,
        private val observeRecentDetectedFieldsUseCase: ObserveRecentDetectedFieldsUseCase,
        private val customFieldsCodec: EncryptedCustomFieldsCodec,  // NEW
    ) : ViewModelProvider.Factory { /* ... */ }
}
```

- Preconditions: `repository.findById` が non-null を返した直後にのみ decrypt を試行する。
- Postconditions: 成功時は `CustomFieldsState.editable = true` かつ `rows.size == decrypted.size`。
  失敗時は `CustomFieldsState.editable = false` かつ `_state` が `State.Error` を保持。
- Invariants: 復号した平文は ViewModel 破棄まで `StateFlow` の中だけに存在し、ログ・SharedPreferences
  には絶対に書き出さない。

#### CredentialEditActivity（変更ほぼなし）

| Field | Detail |
|---|---|
| Intent | UI render を `CustomFieldsState` に追従させる。Phase 1 の `renderCustomFields()` を
そのまま再利用 |
| Requirements | 1.3, 1.4, 2.6 |

**Responsibilities & Constraints**
- 既存 `renderCustomFields(state)` は `state.editable` を判定して view inflate / add CTA 表示を
  切り替える設計になっているため、ViewModel の state 切替に追従するだけで Mode.Edit でも正しく
  動作する。本 Issue ではロジックを変更しない。
- 唯一の変更点: `viewModels { CredentialEditViewModel.Factory(...) }` 呼び出しに codec を渡す。

**Dependencies**
- Inbound: lifecycle → `onCreate` → `viewModel.load(id)`
- Outbound: `ServiceLocator.encryptedCustomFieldsCodec`（Factory 引数）

**Contracts**: Service [ ] / API [ ] / Event [ ] / Batch [ ] / State [x]

### Security Layer（再利用、変更なし）

#### EncryptedCustomFieldsCodec（既存）

| Field | Detail |
|---|---|
| Intent | `List<CustomField>` ⇔ AES-GCM `EncryptedBlob` の双方向変換 |
| Requirements | 1.1, 1.5, 3.2, 3.4, 3.5, 4.2 |

**Responsibilities & Constraints**
- 本 Issue では **変更しない**。`decrypt(EncryptedBlob)` を ViewModel から呼ぶことが新規ユースケース。
- 空 BLOB → 空 list の fail-open 仕様 (Phase 1 §6.1) は Req 1.5 をそのまま満たす。
- JSON parse 失敗は内部で warn + 空 list 返却 (Phase 1 §5.4)。これも本 Issue の Req 4.3 観点では
  「rows 空 + editable = false にはならない（成功扱いで rows 空 + editable = true になる）」が、
  これは Phase 1 で確立した fail-open ポリシーに従う想定挙動とする。

**Dependencies**
- Inbound: `UnlockVaultUseCase`, `UpdateCredentialUseCase`（既存）, **`CredentialEditViewModel`（新規）**
- Outbound: `AesGcmCipher` (Critical)

**Contracts**: Service [x]

### Use Case Layer（既存、変更なし）

#### UpdateCredentialUseCase（既存）

| Field | Detail |
|---|---|
| Intent | `EncryptedCredentialRecord` の更新。`customFields != null` のとき codec で再 encrypt |
| Requirements | 3.1, 3.2, 3.4 |

**Responsibilities & Constraints**
- 本 Issue では **変更しない**。`UpdateCredentialInput.customFields` の null/non-null セマンティクスは
  そのまま：non-null（空 list 含む）であれば codec で再 encrypt して既存 ciphertext を上書きする。
- 結果として「削除された customField を DB から取り除く」要件（Req 3.2）は既存ロジックでカバー済み。

**Dependencies**
- Inbound: `CredentialEditViewModel.save()`
- Outbound: `CredentialRepository.update`, `EncryptedCustomFieldsCodec.encrypt`,
  `PackageSignatureResolver`, `AesGcmCipher`

**Contracts**: Service [x]

### DI Layer

#### ServiceLocator（小規模拡張）

| Field | Detail |
|---|---|
| Intent | `EncryptedCustomFieldsCodec` のシングルトンインスタンスを ViewModelFactory へ提供 |
| Requirements | 1.1, 4.2 |

**Responsibilities & Constraints**
- Phase 1 で codec のインスタンスは生成済みのはずなので、それを `val encryptedCustomFieldsCodec`
  として expose する（既に存在すれば変更不要、なければ追加）。

**Contracts**: Service [ ] / API [ ] / Event [ ] / Batch [ ] / State [ ]

---

## Data Models

### Domain Model（既存、変更なし）

- `EncryptedCredentialRecord.customFieldsCiphertext: ByteArray` / `customFieldsIv: ByteArray`
  （Phase 1 で導入済み、本 Issue で変更なし）
- `CustomField(fieldKey: String, value: String)`（Phase 1 で導入済み、変更なし）
- `CustomFieldsState(rows: List<Row>, editable: Boolean)` / `CustomFieldsState.Row(rowId, fieldKey, value)`
  （Phase 1 で導入済み、本 Issue ではフィールド追加なし）

### Logical / Physical Data Model

**Room schema**: 変更なし。`CredentialEntity.customFieldsCiphertext` / `customFieldsIv` 列は
Phase 1 で `Migration_2_3` により導入済み。本 Issue は読み書きのみ。

---

## Error Handling

### Error Strategy

- **decrypt 成功**: 通常パス。`CustomFieldsState(rows = decrypted, editable = true)` を emit。
- **decrypt 失敗 (AES-GCM auth tag mismatch, key 不在, IV 不整合 など)**:
  catch して `State.Error("decrypt_custom_fields")` を emit、`CustomFieldsState(emptyList(), editable = false)`
  を維持。Activity 側 `renderState` は既存の Snackbar 表示パスに乗る。**画面は閉じない**（Req 4.3）。
- **JSON parse 失敗**: codec 内部で warn + 空 list 返却（Phase 1 既存挙動）。ViewModel 側からは
  「rows 空 + editable = true」の正常系として扱われる。Phase 1 で確定済みの fail-open ポリシー継承。
- **空 BLOB（Migration_2_3 直後で未 re-save）**: codec が空 list を返す。ViewModel は通常パスで
  `editable = true` をセット。「フィールド追加」→ save で初めて非空 ciphertext が書き戻される。

### Error Categories and Responses

- **User Errors (ユーザー入力起因)**: 既存 `State.FieldError` パスをそのまま利用。本 Issue で
  customField 専用の新規 error kind は追加しない。
- **System Errors (decrypt 失敗)**: `State.Error("decrypt_custom_fields")` を新たな cause 文字列で
  emit。`renderState` は既存 `R.string.error_save_failed_with_reason` フォーマッタに渡って
  Snackbar 表示される。詳細メッセージ／リトライ UI は未決事項 §10 Q6 として後続フェーズへ繰越。
- **Logging Errors**: `SafeLogger.error(tag, message, throwable = ex)` で例外クラス名のみ記録。
  ciphertext / IV / 平文は決して文字列化しない（Req 5.3）。

---

## Testing Strategy

### Unit Tests（新規 / 拡張）

1. `CredentialEditViewModelEditModeCustomFieldsTest.loadInEditMode_decryptsAndExposesRows`
   — DB に customField 2 件を仕込んだ EncryptedCredentialRecord を返す FakeRepository を使い、
   `load()` 後に `customFields.value.rows.size == 2` かつ `editable == true` を検証（Req 1.1, 1.2, 7.1, 7.2）。
2. `CredentialEditViewModelEditModeCustomFieldsTest.loadInEditMode_emptyCiphertext_yieldsEditableEmptyRows`
   — `customFieldsCiphertext = ByteArray(0)` の record で load → rows 空 + `editable = true`（Req 1.5）。
3. `CredentialEditViewModelEditModeCustomFieldsTest.editModeReducers_addRemoveUpdate_workEndToEnd`
   — load 後に reducer を呼び、行追加・既存行 fieldKey/value 編集・削除を検証（Req 2.1-2.4, 7.3）。
4. `CredentialEditViewModelEditModeCustomFieldsTest.editModeSave_passesEditedListToUpdateUseCase`
   — fake update use case で `UpdateCredentialInput.customFields` の中身を assert（Req 3.1, 7.4）。
5. `CredentialEditViewModelEditModeCustomFieldsTest.editModeRoundTrip_preservesValuesSemantically`
   — load → 無編集 → save → 再 load で同じ集合になることを検証（Req 3.5, 7.5）。
6. `CredentialEditViewModelEditModeCustomFieldsTest.editModeDecryptFailure_emitsErrorAndKeepsLocked`
   — codec.decrypt が throw する stub を使い、`State.Error` + rows 空 + `editable = false` を検証（Req 4.3, 7.6）。

### Integration Tests

1. 既存 `CredentialEditViewModelCustomFieldsTest`（Phase 1）が **全件 pass** することを確認（Req 7.7）。
2. 既存 `CredentialEditViewModelSuggestionTest`（Phase 2）が **全件 pass** することを確認（Req 6.1, 6.2, 7.7）。
   特に Phase 2 §8.5 「editable = false で suggest UI 非表示」テストが、本 Issue では Mode.Edit でも
   editable = true になるため、もし Mode.Edit を仮定したテストがあれば挙動の妥当性を再評価する。
3. 既存 `UpdateCredentialUseCaseTest` / `SaveCredentialUseCaseCustomFieldsTest` が pass することを確認。

### E2E / UI Tests

本 Issue では Espresso/Compose UI test は追加しない（Phase 1 / 2 でも UI ロジックは ViewModel に
集約して unit test 化する方針を踏襲）。`renderCustomFields()` は Phase 1 で動作実証済みのため、
ViewModel の state 切替を通じて間接的にカバーする。

### Performance / Load

- customField 10 件 (= MAX) の AES-GCM decrypt + JSON parse を `load()` 内で実行しても 10ms 未満で
  完了する想定（既存 password decrypt と同オーダー）。専用の performance test は追加しない。
- 必要なら NFR 3.2 を満たすかは手動 QA で確認。

---

## Security Considerations

- **復号 trigger**: 既存の biometric / device credential unlock を通過した後にのみ編集画面に到達する
  という運用前提を維持（Req 4.1）。追加 prompt は提示しない（Q1 採用案 A、Req 4.2）。
- **plaintext lifetime**: 復号した `List<CustomField>` は `CustomFieldsState.Row` に展開後、
  ViewModel スコープの `StateFlow` 内のみに存在。Activity 破棄で ViewModel destroy → GC（Req 4.4 /
  NFR 1.3）。明示的な zero-fill は文字列 immutability の都合で困難だが、ViewModel スコープを
  逸脱しないことで露出時間を最小化する。
- **ログ**: `SafeLogger.info` で件数のみ、`SafeLogger.error` で例外クラス名のみ。`fieldKey` / `value`
  / ciphertext / IV は一切ログに残さない（Req 5.1, 5.2, 5.3 / NFR 4.1, 4.2）。
- **暗号化方式の不変性**: AES-GCM / Android Keystore / `EncryptedCustomFieldsCodec` を変更しない
  （NFR 1.2、制約 4）。

---

## Performance & Scalability

- **load() 復号オーバーヘッド**: customField 10 件で AES-GCM decrypt 1 回 + JSON deserialize 1 回。
  既存 password decrypt と同 dispatcher（`viewModelScope` 経由 = Default）で実行され、UI スレッドを
  ブロックしない（NFR 3.1）。
- **save() の追加コスト**: 既存 `UpdateCredentialUseCase` パスのまま codec.encrypt が走るだけで、
  本 Issue による追加の I/O はない。

---

## Migration Strategy

**Room migration なし** (制約 7)。Phase 1 で導入した `Migration_2_3` で初期化された空 BLOB の
credential は、本 Issue 適用後に編集画面で開くと「customField 0 件 + editable = true」として表示され、
ユーザーが追加・save した時点で初めて非空 ciphertext が書き戻される（Req 1.5 / NFR 2.2）。

ロールバック戦略: 本 Issue を revert しても、save 済みの非空 ciphertext は Phase 1 のロード経路
（`UnlockVaultUseCase`）から引き続き読める。前方互換性は保たれる。

---

## 自己レビュー結果

- [x] Requirements traceability: requirements.md の全 numeric ID（1.1-1.5, 2.1-2.6, 3.1-3.5,
      4.1-4.4, 5.1-5.3, 6.1-6.3, 7.1-7.7, NFR 1-5）が design.md / tasks.md の `_Requirements:_` で
      参照されている
- [x] File Structure Plan の充填: 具体的なファイルパスを列挙、"TBD" なし
- [x] orphan component なし: Components 名（CredentialEditViewModel, CredentialEditActivity,
      EncryptedCustomFieldsCodec, UpdateCredentialUseCase, ServiceLocator）が File Structure Plan
      に対応
- [x] tasks.md の各タスクが独立にコミット可能な粒度
- [x] `(P)` タスクには `_Boundary:_` が明示されている

---

## 末尾制約（再掲）

- Room schema を変更しない（制約 7）
- Phase 1 の AES-GCM / Android Keystore / JSON シリアライザを変更しない（制約 4）
- Phase 2 (#67) の detection / suggestion ロジックを変更しない（制約 5）
- Credential Manager API 経路に変更を加えない（制約 6）
- 直接 `develop` / `main` への push を行わない（制約 2）
