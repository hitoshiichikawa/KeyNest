# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-18T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-67-impl-feat-autofill-detected-fields-phase-2
- HEAD commit: 7f19b06b5fe4d4f317ec5f5e81731ff1e8d2b222
- Compared to: develop..HEAD

Note: Repository root has no `CLAUDE.md` and no `.claude/rules/feature-flag.md`,
so the Feature Flag Protocol is not adopted (treated as non-opt-in). Standard
3-category review applied without flag細目.

## Verified Requirements

- 1.1 — `DetectedFieldEntity` defines `packageName` / `fieldKey` / `source` / `lastDetectedAt` columns (`app/src/main/java/io/github/hitoshiichikawa/keynest/data/entity/DetectedFieldEntity.kt:38-63`).
- 1.2 — `DetectedFieldSource` enum has the 4 storage keys (`autofillHints` / `hint` / `resourceId` / `contentDescription`), `text` excluded (`app/src/main/java/io/github/hitoshiichikawa/keynest/data/entity/DetectedFieldSource.kt:24-44`).
- 1.3 — Composite PK declared on entity (`primaryKeys = ["package_name", "field_key", "source"]`) and asserted in `Migration_3_4_Test.migrate_compositePrimaryKey_rejectsDuplicateInserts`.
- 1.4 — LRU cap = 50 in `DetectedFieldDao.LRU_CAPACITY` and `upsertWithLruCap` keeps capacity (DAO `app/src/main/java/io/github/hitoshiichikawa/keynest/data/dao/DetectedFieldDao.kt:94-127`; tests `DetectedFieldDaoTest.upsertWithLruCap_keepsCapacity_andDropsOldest`, `defaultCapacityIs50`, `upsertWithLruCap_doesNotAffectOtherPackages`).
- 1.5 — Composite index `(package_name ASC, last_detected_at DESC)` declared on entity and present in `4.json` createSql.
- 2.1 — `Migration_3_4` creates `detected_fields` table + index (`app/src/main/java/io/github/hitoshiichikawa/keynest/data/migration/Migration_3_4.kt:31-47`; test `Migration_3_4_Test.migrate_createsDetectedFieldsTable_andPreservesCredentials`).
- 2.2 — Migration touches only `detected_fields`; `Migration_3_4_Test.migrate_createsDetectedFieldsTable_andPreservesCredentials` asserts existing credentials row preserved and credentials columns unchanged.
- 2.3 — `KeyNestDatabase.create` adds explicit migrations and does not call `fallbackToDestructiveMigration` (`app/src/main/java/io/github/hitoshiichikawa/keynest/data/KeyNestDatabase.kt:48-56`).
- 2.4 — `app/schemas/io.github.hitoshiichikawa.keynest.data.KeyNestDatabase/4.json` committed in the diff.
- 3.1 — `KeyNestAutofillService.onFillRequest` calls `RecordDetectedFieldsUseCase` for resolved `callerPackage`; use case gates on `credentialRepository.findByPackage(...).isEmpty()` (`RecordDetectedFieldsUseCase.kt:49-63`; test `invoke_noOp_whenPackageHasNoCredential`).
- 3.2 — `clock()` defaults to `System.currentTimeMillis()` and is applied to every upserted row (`RecordDetectedFieldsUseCase.kt:39, 59-63, 90-109`; test `invoke_clockIsInjectable`).
- 3.3 — Detection runs on `Dispatchers.IO` and is not bound to `handlerJob`, so callback latency is unaffected (`KeyNestAutofillService.kt:127-144`).
- 3.4 — Detection coroutine is launched on `scope` directly, decoupled from the callback path (same site as 3.3).
- 3.5 — `upsertIfNotBlank` rejects raw `isNullOrBlank` and post-normalization blanks (`RecordDetectedFieldsUseCase.kt:90-109`; tests `invoke_skipsBlankFieldKeys`, `invoke_skipsAutofillHintsThatNormalizeToBlank`).
- 3.6 — `FieldDescriptor` lacks a `text` property so omission is structural; pinned by `invoke_typeLevelTextExclusion_isStructural` and documented in `RecordDetectedFieldsUseCase.kt:84-87`.
- 3.7 — Use case iterates every descriptor and extracts `autofillHints` / `hint` / `idEntry` / `contentDescription` regardless of password-role classification; `parsed.customFieldCandidates` per Phase 1 includes all editable views (design §0.1). Coverage: `invoke_upsertsAllSources_whenCredentialExists` exercises a descriptor that carries all four sources.
- 3.8 — Inner `try/catch` around `recordDetectedFieldsUseCase(...)` swallows exceptions and logs via `SafeLogger.warn` (`KeyNestAutofillService.kt:130-142`).
- 4.1 — `SUGGESTION_LIMIT = 10` passed to `observeRecentDetectedFieldsUseCase`; DAO query orders by `last_detected_at DESC` (`CredentialEditViewModel.kt:520-545, 602`).
- 4.2 — `onSuggestionClicked` transfers `fieldKey` to `rows.last().rowId` and hides the strip (`CredentialEditViewModel.kt:477-493`; test `onSuggestionClicked_transfersFieldKeyToLastRow_andHidesStrip`).
- 4.3 — Empty-list emission sets `emptyMessage = true` and the Activity renderer shows the placeholder TextView (`CredentialEditViewModel.kt:538-543`; `CredentialEditActivity.kt:434-444`; test `suggestions_showEmptyMessage_whenNoDetectedFields`).
- 4.4 — `refreshSuggestions` filters out fieldKeys whose normalized form already exists in the customField rows and dedupes by normalized key (`CredentialEditViewModel.kt:519-545`; tests `suggestions_filterOutExistingFieldKeys`, `suggestions_dedupeByNormalizedKey`).
- 4.5 — Subscription runs inside `viewModelScope.launch` so DB collect happens off the main thread (`CredentialEditViewModel.kt:520`).
- 4.6 — Edit-mode disables the chip strip when `customFields.editable` is false (early-return in `refreshSuggestions`); aligns with Phase 1's "edit mode read-only" decision per requirements §10 R2 / design §8.5 (`CredentialEditViewModel.kt:511-518`; test `suggestions_hiddenWhenCustomFieldsNotEditable`).
- 5.1 — `Migration_3_4_Test` covers detected_fields table creation, composite PK rejection of duplicates, idempotency, credentials preservation, and credentials column shape unchanged (4 `@Test` methods).
- 5.2 — `DetectedFieldDaoTest` covers (a) insert, (b) REPLACE on composite-PK conflict, (c) `ORDER BY last_detected_at DESC`, (d) LRU capacity behaviour incl. cross-package isolation (15 `@Test` methods).
- 5.3 — Gating logic verified at the use-case layer (`RecordDetectedFieldsUseCaseTest.invoke_noOp_whenPackageHasNoCredential` and `invoke_upsertsAllSources_whenCredentialExists`). Per `tasks.md` T13: skipping the AutofillService-level integration test is pre-authorized when Phase 1 testing harness is absent; impl-notes.md §A documents the skip and the alternative coverage.
- 5.4 — `CredentialEditViewModelSuggestionTest.onSuggestionClicked_transfersFieldKeyToLastRow_andHidesStrip` verifies the click→transfer behaviour (plus 10 additional cases for suggestion lifecycle).
- 5.5 — Existing tests' constructors updated to follow the new `ObserveRecentDetectedFieldsUseCase` / `DetectedFieldRepository` parameters (impl-notes §B); per impl-notes the full `testDebugUnitTest` run is BUILD SUCCESSFUL.

## Findings

なし

## Summary

requirements.md の全 numeric ID (1.1-1.5 / 2.1-2.4 / 3.1-3.8 / 4.1-4.6 / 5.1-5.5) について実装または既存テストでカバーが確認できた。T13 のスキップは tasks.md で事前許可されており、代替カバレッジは impl-notes.md §A に明記されている。tasks.md の `ファイル` 指定で許可された範囲外への変更は確認されない。Feature Flag Protocol は repository に CLAUDE.md / feature-flag.md が存在しないため非採用扱い。

RESULT: approve
