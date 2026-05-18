# Implementation Notes — Issue #66 feat(autofill): カスタムフィールド機能 Phase 1

> 関連: [requirements.md](./requirements.md) / [design.md](./design.md) / [tasks.md](./tasks.md)

## 実装サマリ

| Task | Commit (subject) | 1-2 行サマリ |
|---|---|---|
| T1 | feat(autofill): add CustomField domain model | `CustomField` data class + `EncryptedCredentialRecord` / `PlaintextCredential` への customFields プロパティ追加。`Credential`（plain domain）には追加しない（design.md §3.2）。 |
| T2 | feat(autofill): add kotlinx.serialization codec for custom fields | `EncryptedCustomFieldsCodec` 実装。kotlinx-serialization-json 1.6.3 を追加。空 BLOB / 不正 JSON は空リストに fail-open（design.md §5.4 / §6.1）。 |
| T3 | feat(autofill): add Room columns and repo mapping for custom fields | `CredentialEntity` に `custom_fields_ciphertext` / `custom_fields_iv` (BLOB NOT NULL DEFAULT empty) を追加し、`CredentialRepositoryImpl` の mapping に接続。 |
| T4 | feat(autofill): add Migration_2_3 and bump KeyNestDatabase to v3 | `ALTER TABLE … ADD COLUMN … DEFAULT x''` で純粋 SQL のみの migration。Keystore に触れない（design.md §4.1）。 |
| T5 | test(migration): add Migration_2_3_Test | `SupportSQLiteOpenHelper` 経由で v2→v3 移行を 3 ケース検証（既存行保持、列追加後の insert、empty DB idempotency）。 |
| T6 | chore(schemas): commit Room v3 schema export | KSP 生成済みの `3.json` を tracking 追加（Issue #63 運用）。 |
| T7 | feat(autofill): wire customFields into Save / Update use cases | `NewCredentialInput.customFields` / `UpdateCredentialInput.customFields` を追加し、codec で暗号化して `EncryptedCredentialRecord` に詰める。 |
| T8 | feat(autofill): decrypt customFields in UnlockVaultUseCase | unlock 時に codec.decrypt → `PlaintextCredential.customFields` に詰める。AEAD 失敗は warn + 空リスト fallback（username/password fill は継続）。 |
| T9 | feat(autofill): add match-key extraction and customField walk | `AutofillFieldHeuristics.extractMatchKeys / normalizeKey` 追加（`text` 除外、design.md §7.2）。`AssistStructureParser` で全 editable view を `CustomFieldCandidate` として収集（早期 break 廃止）。 |
| T10 | feat(autofill): wire customField match into FillResponseBuilder | `CustomFieldMatcher` (pure utility) を切り出し。`buildLockedResponse` / `buildUnlockedDataset` に customField 入力を追加。`AutofillUnlockActivity.newIntent` の signature を拡張。**match は unlock 後段に寄せる**（案 Y、design.md §6.4）。 |
| T11 | feat(autofill): execute customField match in AutofillUnlockActivity | `newIntent` 経由で受け取った descriptor を `CustomFieldMatcher.match` に投入し、`buildUnlockedDataset` の `customFieldValues` に流す。 |
| T12 | feat(autofill): add customFields editor state to ViewModel | `CustomFieldsState`（rows + editable）と reducer を追加。空 fieldKey の silent drop（Req 3.4）、10 件 cap（Req 3.5）、edit モードでは read-only（暫定設計）。 |
| T13 | feat(autofill): add custom fields editor UI in CredentialEditActivity | `view_custom_field_row.xml` 新規＋ activity layout に `row_custom_fields_section` 追加。動的 row + TextWatcher + add/remove ボタン。strings 6 件追加。 |
| T14 | （本 impl-notes 内に audit 記録、コード変更なし） | 全 log 出力を grep audit。値/fieldKey 漏洩なし、count 系のみ。 |

---

## 設計決定の補足

### `Credential` ドメイン型に customFields を追加するか

**結論: 追加しない**（design.md §3.2 推奨に従う）。

- requirements.md Req 1.1 は `Credential.customFields: List<CustomField>` の追加を求めているが、Req 1.5 が「Credential model（domain layer）shall 平文 `value` を保持しない」と明記しており、両者は矛盾している。
- design.md §3.2 は「Phase 1 では `Credential` には何も追加しない」を推奨し、その理由として「Phase 1 では list 画面に customField 表示要件が無いため `customFieldKeys` を載せても実用意味がない」と整理している。
- 本実装は design.md の推奨を採用。**この判断は人間決定が必要** — 後述「確認事項」§A 参照。
- Phase 2 で list 画面に fieldKey 一覧を出す要件が固まった時点で `Credential.customFieldKeys: List<String>` を追加すれば良い。`EncryptedCredentialRecord` から `fieldKey` 一覧だけを取り出す経路は **存在しない**（暗号化境界で value と一体）ため、その時 codec を「key のみ復号」できる形に分割する必要がある（or `Credential.customFieldKeys` を別暗号化列に分ける）。

### locked 段階での match vs unlock 段階での match

**結論: unlock 後に寄せる（案 Y）**（tasks.md T10 / design.md §6.4 の決定）。

- design.md §6.4 は「match は locked 段階で行い、locked Dataset には mapping を Intent extras に詰める」と書かれていたが、これは「`AutofillCandidate` に fieldKey 平文を載せる」前提だった。
- しかし fieldKey と value は同じ ciphertext に同居しているため、locked 段階で fieldKey だけ取り出すには codec.decrypt が必要 → Req 5.2「locked では復号禁止」と衝突。
- tasks.md T10 内で「決定: 案 Y を採用」と整理されている通り、本実装は `FillResponseBuilder.buildLockedResponse` で全 customField 候補の AutofillId / descriptor を Intent extras 経由で渡し、`AutofillUnlockActivity` 内で unlock 完了後に `CustomFieldMatcher.match` を実行する。
- 結果として `AutofillCandidate` には customField 関連のフィールドを追加していない。

### 編集モードでの customField 編集

**結論: Phase 1 では新規モードのみ編集可、編集モードでは read-only**（design.md §9.3 暫定設計）。

- 編集モードでも customField を編集可にするには `UnlockVaultUseCase` を `load()` 中に呼び出す必要があり、追加 biometric unlock が発生する。これは既存 UX（password だけ空欄で開く）と非対称。
- Phase 1 では `CredentialEditViewModel.load()` が呼ばれたら `CustomFieldsState.editable = false` にし、UI は section をそのまま見せつつ add ボタンを GONE にし、説明文 (`tv_custom_fields_readonly_note`) を出して「Editing existing custom fields is not yet supported.」と表示する。
- `UpdateCredentialInput.customFields` には null を渡して既存 ciphertext を保持する。
- Phase 2 で UX 決定（編集モード入場時に必ず unlock を要求 / customField 行単位で unlock など）を改めて検討する。

### Custom field 復号失敗時の fail-open ポリシー

- 空 BLOB（length=0）→ 空リスト返却（migration 後の既存行用、design.md §6.1）。
- JSON parse 失敗 → 空リスト + `SafeLogger.warn`（design.md §5.4）。
- AES-GCM auth tag 失敗 → `UnlockVaultUseCase` 内で catch して warn + 空リスト fallback。**username/password fill は継続させる**（既に primary credential は復号成功しているため、customField 部分だけ silent abort する方が良い）。

### `text` 抽出の除外

- requirements.md §3 用語定義は match キーに `text`（編集中の値）を含めているが、design.md §7.2 で「Phase 1 では `text` を除外」と決定。
- 理由: `text` は editable に既にユーザーが入力した値を含むため、`fieldKey="社員"` の credential が `text="社員 田中太郎"` の入力済み氏名フィールドに誤マッチする事故が起きうる。
- 本実装は `FieldDescriptor` に `text` プロパティ自体を載せていないため、構造的に extractMatchKeys が text に触れる経路がない（テストで `text` キーが含まれないことを `extractMatchKeys_doesNotIncludeText_design_NotInDescriptor` で pin している）。

---

## 確認事項

### A. `Credential` (plain domain) に何を載せるか

**Status**: design.md §3.2 推奨 = 何も載せない を採用したが、requirements.md Req 1.1 と表面上矛盾。Phase 2 で list 画面に fieldKey 一覧表示が要件として浮上した場合、本実装の方針を `Credential.customFieldKeys: List<String>` 追加に切り替える必要がある。

**Recommendation**: 人間（PM）に確認すること。本 PR 採用後、Phase 2 着手前に決定。

### B. 編集モードでの customField unlock 要否

**Status**: 暫定で「編集モードでは customField セクションを read-only 表示」を採用（design.md §9.3 暫定）。

**Recommendation**: UX チームとの協議要。選択肢:
1. 編集画面入場時に常に biometric unlock を要求し、customField を編集可能にする（既存 password 編集 UX との非対称が解消される一方、unlock 摩擦は増える）。
2. customField セクションの「編集する」CTA を tap した時点で unlock を要求する（遅延 unlock）。
3. Phase 1 のまま read-only に留め、Phase 2 まで延期する。

### C. 既存テスト破壊について

**Status**: 既存テスト全件 green を確認（後述「ビルド・テスト検証結果」参照）。

**Notes**:
- `AssistStructureParserTest`: 既存 `parse_returnsEmpty_whenNoCandidatesFound` などは `ParsedFields.isEmpty` 経由でアサーション。`isEmpty` の semantics を「username/password が両方 null」に限定したまま据え置きしたため、customFieldCandidates が非空でも既存テストは pass する。**ただし** `isEmpty` の意味を変えていることを Reviewer が認識する必要あり。docstring に明示済み。
- `CredentialEditViewModelTest` / `LockedFillResponseSecurityTest` 等の既存テストは入力 DTO のデフォルト値（emptyList()）により無改修で pass。
- `FakeCredentialRepository` は `EncryptedCredentialRecord` の data class copy を使うため、追加した customFieldsCiphertext / customFieldsIv は自動的に propagate される（変更不要）。

### D. `UpdateCredentialUseCase` で `lastUsedAt` が消失する pre-existing bug

**Status**: 本 PR では修正していない（スコープ外）。

`UpdateCredentialUseCase` は `EncryptedCredentialRecord(... )` を新規構築する際、`lastUsedAt` 引数を省略する → デフォルト null になり、`recently used` carousel feed が update のたびに silent reset される。これは Issue #66 と無関係の pre-existing bug。Issue #9 の運用に影響があるため別 Issue 化を推奨。

### E. 短い fieldKey 警告 UI

**Status**: design.md §12.2 / §13-4 で「Phase 1 では緩和策なし」が確定済み。本 PR でも対応せず。

### F. Dataset presentation の文言

**Status**: design.md §13-5 / requirements §10-4 で未決。本 PR は customField 用の特別な RemoteViews を作らず、credential 本体のラベルがそのまま表示される（既存挙動を継承）。Phase 2 で「カスタム: 会員番号」のような subtitle 装飾を検討。

### G. 3.json schema の人間レビュー

**Status**: KSP 生成済み・commit 済み。identityHash 更新 `4dfde8353e63ee3e22b8a641992bb973`、`fields` に `customFieldsCiphertext` / `customFieldsIv` が含まれることを目視確認済み。

---

## ビルド・テスト検証結果

### 環境

- JDK: Temurin-17.0.19+10（`/home/hitoshi/sdks/jdk-17`）
- Android SDK: `/home/hitoshi/sdks/android-sdk`
- Gradle: 8.10.2 (project wrapper)
- AGP: 8.5.2 / Kotlin: 1.9.24 / KSP: 1.9.24-1.0.20 / kotlinx-serialization: 1.6.3
- `local.properties` は worktree 直下に手動作成（gitignore 対象なので commit には含めず）

### 実行コマンドと結果

| コマンド | 結果 | 備考 |
|---|---|---|
| `./gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL | 既存の deprecation 警告 2 件以外は新規警告なし。 |
| `./gradlew :app:kspDebugKotlin` | BUILD SUCCESSFUL | `app/schemas/.../3.json` が生成された（T6 で commit）。 |
| `./gradlew :app:testDebugUnitTest`（フル） | BUILD SUCCESSFUL | 全テスト pass（新規追加分を含む）。 |
| `./gradlew :app:assembleDebug`（最終） | BUILD SUCCESSFUL | `app-debug.apk` 生成成功。 |

### Instrumented test

JVM 環境のため未実行。`./gradlew :app:connectedDebugAndroidTest` は Android emulator / device が必要。Reviewer または CI で別途実施を推奨。

---

## T14 SafeLogger audit

本 PR で追加した全ログ出力箇所を確認した結果、value / fieldKey の漏洩なし。

| 場所 | ログ内容 | 安全性 |
|---|---|---|
| `EncryptedCustomFieldsCodec` warn × 2 | "JSON parse failed" / "JSON decode rejected" + throwable class | message 文言は固定、throwable は `SafeLogger.warn` 内で class name のみ抽出 |
| `UnlockVaultUseCase.decryptCustomFields` warn | "customFields decrypt failed; returning empty list" + throwable | 同上 |
| `AutofillUnlockActivity` 起動ログ | `customFieldCandidates=N`（カウントのみ） | descriptor 内容は出力しない |
| `AutofillUnlockActivity` match 失敗 warn | "customField match failed; proceeding without customField values" + throwable | 同上 |
| `AutofillUnlockActivity` extras shape mismatch warn | 文言固定 | 同上 |
| `AutofillUnlockActivity` 完了 info | `customFieldsMatched=N`（カウントのみ） | 値・fieldKey なし |
| `KeyNestAutofillService.onFillRequest` info | `customFieldCandidates=N`（カウントのみ） | 同上 |

`CredentialEntity.toString()` / `EncryptedCredentialRecord.toString()` には新 BLOB の **サイズだけ** が出力される（既存パターン `<NB>` プレビューに揃え）。 `PlaintextCredential.toString()` は `customFields=<N entries>` の件数表記のみ。`CustomField.toString()` は両方 redacted。

---

## 既知の TODO / Follow-up

- **編集モードでの customField 編集**（C above / design.md §9.3）: Phase 2 で UX 決定要。
- **`Credential` plain domain に customFieldKeys を載せるか**（A above）: Phase 2 list 画面要件に依存。
- **`UpdateCredentialUseCase` の `lastUsedAt` 消失 pre-existing bug**（D above）: 別 Issue 化を推奨。
- **Phase 2: detected_fields ログ機能**（requirements.md §11）: 別 Issue。
- **Phase 3: 日本語 hint / resourceId pattern を heuristic 段階で扱う**（requirements.md §11）: 別 Issue。
- **`MAX_NODES = 500` 引き上げ判断**（design.md §13-8）: 複雑 WebView を持つ業務アプリで username/password が押し出されて customField 抽出に失敗するリスク。実測ベースで Phase 2 以降で判断。
- **短い fieldKey の警告 UI**（design.md §12.2 / §13-4）: Phase 2 で edit UI に warning を出すか検討。
- **customField 用 Dataset presentation**（design.md §13-5 / requirements §10-4）: Phase 2 で `DatasetPresentationFactory` に customField 用ラベル装飾を追加するか検討。
- **`PlaintextCredential.customFields` の wipe**: 現状は `close()` で list reference を空に差し替えるだけ。文字列の中身は GC 任せ（JVM の String 不変性による限界）。Phase 2 で `value` を CharArray にして deterministic wipe する案を検討。
- **Instrumented test での customField 流入確認**: emulator を介した手動テストが推奨される（unlock → biometric → Dataset 反映が実機で動作することを確認）。
