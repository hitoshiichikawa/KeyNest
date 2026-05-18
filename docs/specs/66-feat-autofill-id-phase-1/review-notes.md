# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-18T04:48:18Z -->

## Reviewed Scope

- Branch: claude/issue-66-impl-feat-autofill-id-phase-1
- HEAD commit: 6a6b604b42ebb1356676f3c054e2f2f2aa6d9a38
- Compared to: develop..HEAD
- Files changed: 38 (+2910 / -32), 14 commits T1–T14
- CLAUDE.md not present in repo → Feature Flag Protocol section not applicable; only the 3 core categories are evaluated.

## Verified Requirements

- 1.1 — `PlaintextCredential.customFields: List<CustomField>` 追加（design.md §3.2 / impl-notes §A の方針に従い `Credential` 平文型ではなく `PlaintextCredential` に追加。Req 1.5 と整合）
- 1.2 — `domain/model/CustomField.kt` data class with `fieldKey: String`, `value: String`; `CustomFieldTest.equals_isStructural_overFieldKeyAndValue`
- 1.3 — `CredentialEntity.customFieldsCiphertext/customFieldsIv` BLOB columns（design.md §3.4 案 A: ciphertext 1 列で JSON `List<CustomField>` を保持）
- 1.4 — `EncryptedCustomFieldsCodec.encrypt/decrypt` が既存 `AesGcmCipher` を流用; `EncryptedCustomFieldsCodecTest.encrypt_then_decrypt_roundTripsList`
- 1.5 — `Credential.kt` には customFields 追加なし（差分でも値プロパティの追加なし）
- 2.1 — `Migration_2_3.kt` の `ALTER TABLE ... DEFAULT x''`; `Migration_2_3_Test.migrate_preservesExistingRow_andLeavesCustomFieldsBlobEmpty`
- 2.2 — `data/migration/Migration_2_3.kt` + `test/.../Migration_2_3_Test.kt`（命名規約一致、`Migration_1_2_Test` と同じ `SupportSQLiteOpenHelper` パターン）
- 2.3 — `app/schemas/io.github.hitoshiichikawa.keynest.data.KeyNestDatabase/3.json` が tracking 済み（116 行追加）
- 3.1 — `credential_edit_activity.xml` の `advanced_content` 配下に `row_custom_fields_section` を追加
- 3.2 — `btn_add_custom_field` クリックで `viewModel.addCustomFieldRow()`; `CredentialEditViewModelCustomFieldsTest.addCustomFieldRow_appendsEmptyRow_andAssignsUniqueId`
- 3.3 — `btn_remove_row` クリックで `viewModel.removeCustomFieldRow(rowId)`; `removeCustomFieldRow_dropsTargetAndPreservesOthers`
- 3.4 — `save()` 内 `filter { it.fieldKey.isNotBlank() }`; `save_dropsRowsWithBlankFieldKey_andPersistsRest`
- 3.5 — `CustomFieldsState.MAX_CUSTOM_FIELDS = 10` / `canAddMore` + `btn_add_custom_field.isEnabled`; `addCustomFieldRow_capsAt10_andCanAddMoreFlipsFalse`
- 3.6 — `view_custom_field_row.xml` の `input_field_value` は `android:inputType="text"`（plain text）
- 3.7 — `CustomField(fieldKey = it.fieldKey, value = it.value)` で入力ままを保存（正規化なし）; 正規化は `AutofillFieldHeuristics.normalizeKey` で match 時のみ実行
- 4.1 — `AutofillFieldHeuristics.extractMatchKeys`（autofillHints / hint / idEntry / contentDescription）+ `normalizeKey`（小文字化 + `Char.isWhitespace()` 除去で全角/タブ含む）; `AutofillFieldHeuristicsExtractMatchKeysTest.normalizeKey_stripsFullWidthSpaceAndTab`
- 4.2 — `CustomFieldMatcher.match` が matched field を `Map<AutofillId, String>` で返す → `FillResponseBuilder.buildUnlockedDataset` で `setValue`; `CustomFieldFillResponseTest.buildUnlockedDataset_includesCustomFieldValues`
- 4.3 — `AssistStructureParser.parse()` 内で `classify` / customFieldCandidates 収集は独立; `AutofillFieldHeuristics.classify` 自体は無改変（diff 確認済み）
- 4.4 — `CustomFieldMatcher.match` 内 `break` で最初の matched customField を採用; `CustomFieldMatcherTest.match_firstRegisteredCustomFieldWins_onMultiMatch`
- 4.5 — outer loop が break しないので同一 customField が複数 field に適用; `CustomFieldMatcherTest.match_sameCustomFieldAppliedToMultipleFields`
- 5.1 — `SafeLogger` 経由、values/fieldKeys はログ非出力（T14 audit + `CustomField.toString` redacted）
- 5.2 — `FillResponseBuilder.buildLockedResponse` は customField 候補の AutofillId を Intent extras に詰めるのみで復号せず、AutofillUnlockActivity が unlock 後に match を実行（design.md §6.4 案 Y）
- 5.3 — locked dataset は `PLACEHOLDER = "••••••"` のみで customField value を含まない（`FillResponseBuilder.buildLockedDataset` で確認）
- 6.1 — `Migration_2_3_Test.migrate_preservesExistingRow_andLeavesCustomFieldsBlobEmpty`
- 6.2 — `CustomFieldMatcherTest.match_singleField_singleMatch_returnsValue` + `CustomFieldFillResponseTest.buildUnlockedDataset_includesCustomFieldValues`
- 6.3 — `CustomFieldMatcherTest.match_firstRegisteredCustomFieldWins_onMultiMatch`
- 6.4 — `CustomFieldMatcherTest.match_omitsCandidatesThatDoNotMatchAnyCustomField`
- 6.5 — `CredentialEditViewModelCustomFieldsTest`（追加 / 削除 / 10 件 cap / 空 fieldKey silent drop / canAddMore すべて検証）
- 6.6 — `EncryptedCustomFieldsCodecTest.encrypt_then_decrypt_roundTripsList` + `SaveCredentialUseCaseCustomFieldsTest.save_persistsEncryptedCustomFields_andRoundTripsViaCodec`

## Findings

なし

## Summary

全 numeric requirement (1.1–6.6) について実装ファイル + テストの観測可能な証跡を確認。tasks.md の `_Boundary:_` 範囲（domain/model, data/entity, data/migration, security, autofill/parser, autofill/builder, autofill/unlock, ui/edit, schemas）内に変更が収まっており boundary 逸脱なし。impl-notes.md にビルド・unit test 全件 BUILD SUCCESSFUL の記録あり。

RESULT: approve
