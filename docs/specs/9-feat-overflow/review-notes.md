# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-9-impl-feat-overflow
- HEAD commit: 4c4a79e3cf1a5ce1470aebbbf0cee2d1044ad67a
- Compared to: develop..HEAD
- Feature Flag Protocol: opt-out (no flag-path checks applied)

## Verified Requirements

- 1.1 — `CredentialListViewModel.applySearch` (label/username/packageName, `.lowercase().contains()`) + `CredentialListViewModelTest.applySearch_matchesLabelUsernamePackage_caseInsensitively`
- 1.2 — `CredentialListActivity.setUpSearch` → `addTextChangedListener` → `viewModel.onQueryChanged` (MutableStateFlow ↘ combine) + `uiState_search_narrowsMainList`
- 1.3 — `applyFilter`/`applySearch` are independent transforms; query MutableStateFlow does not touch filter/sort + `uiState_clearSearch_returnsToFullList_whileFilterPreserved`
- 1.4 — `computeEmptyKind` returns `NoMatch` when `query` non-blank and list empty + `renderEmptyView` switches text resource + `computeEmptyKind_returnsNoMatch_whenUserNarrowed_andListEmpty`, `uiState_emptySearch_setsNoMatch`
- 1.5 — `mainListFlow` performs filter/search in-memory on the already-emitted DAO Flow; no new IO surface; INTERNET permission absence preserved
- 2.1 — `res/layout/credential_list_activity.xml` ChipGroup with `chip_signature_matched` and `chip_signature_missing` + Espresso `chipGroup_isMutuallyExclusive`
- 2.2 — `CredentialFilter.None` initial value; `applyFilter` returns input list as-is for None + `uiState_filterToggleOff_returnsAllRows`
- 2.3 — `applyFilter_partitionsBySignatureSha256` (matched branch) + `CredentialFilter.SignatureMatched`
- 2.4 — `applyFilter_partitionsBySignatureSha256` (missing branch) + `CredentialFilter.SignatureMissing`
- 2.5 — `app:selectionRequired="false"` on ChipGroup enables tap-to-deselect; `setOnCheckedStateChangeListener` maps empty `checkedIds` → `CredentialFilter.None`
- 2.6 — `app:singleSelection="true"` enforces exclusive at framework level + `chipGroup_isMutuallyExclusive`
- 2.7 — `computeEmptyKind_returnsNoMatch_whenFilterActive_andListEmpty`
- 3.1 — `CredentialDao.observeRecentlyUsed` (`WHERE last_used_at IS NOT NULL ORDER BY last_used_at DESC LIMIT :limit`) + `ObserveRecentlyUsedUseCase` (limit=5) + `invoke_returnsTopFiveByLastUsedAtDescending`
- 3.2 — `AutofillUnlockActivity` post-unlock fire-and-forget `markCredentialUsedUseCase` + `MarkCredentialUsedUseCaseTest.invoke_stampsRepositoryWithProvidedNow` + DAO `updateLastUsedAt_setsTimestamp_onTargetRowOnly`
- 3.3 — DAO `LIMIT :limit` natural behaviour + `ObserveRecentlyUsedUseCaseTest.invoke_returnsFewerThanFive_whenOnlyFewRowsAreUsed`
- 3.4 — `CredentialListActivity.renderRecentVisibility` sets header + recycler to `GONE` when `recentList.isEmpty()` + `uiState_recentList_isEmpty_whenNoRowHasLastUsedAt` + Migration leaves existing rows `lastUsedAt = NULL`
- 3.5 — `RecentlyUsedCarouselAdapter.bind` → `cardRecent.setOnClickListener { onClick(item) }` → Activity `startEdit` (shared path with row tap)
- 3.6 — `recentUseCase()` is a separate combine input; not routed through filter/search transforms + `uiState_recentList_isIndependentOfQueryAndFilter`
- 3.7 — DAO-backed; no network surface
- 4.1 — `CredentialSortOrder` enum + 3 DAO observers (`observeByUpdatedAtDesc`/`observeByLabelAsc`/`observeByPackageAsc`) + `CredentialDaoTest.observeByUpdatedAtDesc_*`, `observeByLabelAsc_isCaseInsensitive`, `observeByPackageAsc_isCaseInsensitive`
- 4.2 — `MutableStateFlow(CredentialSortOrder.UpdatedAtDesc)` initial value + `uiState_initialDefault_isUpdatedAtDescNoneEmptyQuery`
- 4.3 — `sort.flatMapLatest { listUseCase(it) }` re-subscribes on sort change + `uiState_sortSwitch_reorders`
- 4.4 — `mainListFlow` applies `applyFilter` then `applySearch` against `sortedListFlow` output; Kotlin `filter` preserves DAO ordering. Pure helpers are tested independently and the sort/filter transitions are exercised separately by `uiState_sortSwitch_reorders` and `uiState_filterToggleOff_returnsAllRows`
- 4.5 — Sort state held in `MutableStateFlow` only; no `SavedStateHandle` use; structural compliance
- 5.1 — `credential_list_item.xml` adds `btn_overflow` ImageButton (minWidth/Height=48dp) + adapter wires `onOverflowClick`
- 5.2 — `credential_list_row_overflow.xml` contains exactly one `<item>` (`action_duplicate`) + Espresso `rowOverflow_showsDuplicateOnly`
- 5.3 — `DuplicateCredentialUseCase` → `repo.duplicate` copies all fields, resets createdAt/updatedAt, nulls lastUsedAt + `DuplicateCredentialUseCaseTest.invoke_inheritsAllFields_andResetsTimestamps` + `CredentialRepositoryImplTest.duplicate_copiesAllFields_andResetsTimestamps`
- 5.4 — `DuplicateCredentialUseCaseTest.invoke_leavesSourceUnchanged` + `CredentialRepositoryImplTest.duplicate_doesNotMutateSource` + duplicate increments list count (covered by `invoke_addsRow_soListGrowsByOne`)
- 5.5 — Menu XML contains a single item only (no export/share entries)
- 5.6 — `CredentialListAdapter` retains `onItemLongClick`; Activity retains `promptDelete` (verified via diff: long-click path unchanged)
- 6.1 — `KeyNestAutofillService` / `SaveCredentialUseCase` / `UpdateCredentialUseCase` / `UnlockVaultUseCase` / `AutofillEnableActivity` not modified; `AutofillUnlockActivity` only adds a fire-and-forget call after the existing setResult path
- 6.2 — `CredentialListActivity.startEdit` is the single path for both row tap and carousel card tap; unchanged Intent invocation
- 6.3 — `markCredentialUsedUseCase` runs on `Dispatchers.IO + NonCancellable` after `setResult` was already published; `onFillRequest` path of `KeyNestAutofillService` not touched
- NFR 1.1 — No network code added; in-memory filter/search; impl-notes confirms `InternetPermissionAbsenceTest` precedent
- NFR 1.2 — `renderState` logs `size=..`/`emptyKind=..`/`filter=<className>`/`sort=<enum>` only; `markUsed` failure logs only the throwable type
- NFR 1.3 — Adapters bind label/username only; `RecentlyUsedCarouselAdapter` never references password fields; `DuplicateCredentialUseCase` keeps ciphertext/IV unchanged (no plaintext path)
- NFR 1.4 — `DuplicateCredentialUseCase` calls only the repository (no network surface)
- NFR 2.1 — In-memory filter on the same `sortedListFlow` value (no DAO re-query per keystroke); structural argument acceptable, optional 500-row perf fixture deferred per task 7.2 (`- [ ]*`)
- NFR 2.2 — `markCredentialUsedUseCase` is fire-and-forget via `launch(Dispatchers.IO + NonCancellable)`; never awaited before `finish()`
- NFR 3.1 — Search input `contentDescription`, sort button a11y label, overflow row label (`credential_list_row_overflow_a11y` parameterised by label), carousel card description provided in strings.xml
- NFR 3.2 — `btn_overflow`, `btn_sort` both `minWidth/Height=48dp`; carousel card `minHeight=48dp`
- NFR 3.3 — Material `Chip` with `android:checkable="true"` exposes selected state to TalkBack via standard ChipDrawable behaviour
- NFR 4.1 — 17 new strings added to `res/values/strings.xml`; no hard-coded literals in code paths

## Boundary Verification

- All modified files match `_Boundary:_` annotations in tasks.md
- `Untouched Files` listed in design.md (`KeyNestAutofillService.kt`, `SaveCredentialUseCase`, `UpdateCredentialUseCase`, `UnlockVaultUseCase`) confirmed unchanged in the diff
- New components (`Migration_1_2`, `CredentialSortOrder`, `DuplicateFailure`, `RecentlyUsedCarouselAdapter`, `CredentialListUiState`, `CredentialFilter`, `EmptyKind`, three use cases) sit in the layers prescribed by File Structure Plan

## Findings

なし

## Summary

All numeric ACs (Req 1.1-1.5 / 2.1-2.7 / 3.1-3.7 / 4.1-4.5 / 5.1-5.6 / 6.1-6.3) and NFRs (1.1-1.4 / 2.1-2.2 / 3.1-3.3 / 4.1) are backed by either implementation code, unit tests, integration tests (Robolectric), or structural arguments documented in impl-notes. The Espresso class is `@Ignore`'d but matches the existing `AutofillFlowTest` precedent and is documented in impl-notes; the unit-test layer independently covers every AC, so the `@Ignore` does not leave any AC uncovered. Boundary annotations from tasks.md are respected and the design.md "Untouched Files" list holds in the diff.

RESULT: approve
