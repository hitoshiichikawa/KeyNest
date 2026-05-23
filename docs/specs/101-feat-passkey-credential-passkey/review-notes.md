# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-23T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-101-impl-feat-passkey-credential-passkey
- HEAD commit: 6d26c329ffb4e6a5c9fd10192a870ac1b41e0eb8
- Compared to: develop..HEAD (with merge-base = 5d85db0 used for the
  real impl diff; the `docs/specs/103-*` deletion that appears in
  `git diff develop..HEAD` is a base-divergence artifact — Issue #103's
  spec was added to develop after this impl branch was cut, so the
  developer never touched it)

## Verified Requirements

- 1.1 — `CredentialListActivity.setUpMainList` wires a single
  `binding.recycler` to the new multi-viewType `CredentialListAdapter`;
  `bindPassword_*` + `bindPasskey_*` cases in
  `CredentialListAdapterInstrumentationTest` cover both rows
- 1.2 — `CredentialListAdapter.getItemViewType` + viewType-specific
  bind paths (`iconLoader.loadInto` vs
  `setImageResource(R.drawable.ic_passkey_24)`);
  `getItemViewType_returns*` and `bindPasskey_paintsIcPasskey24_*` +
  `bindPassword_paintsAppIconViaIconLoader_notSetImageResource` tests
- 1.3 — `PasskeyViewHolder.bind` 3-line fallback chain (line 1
  `displayName ?: rpDisplayName ?: rpId`, line 2 `userDisplayName ?:
  userName ?: R.string.credential_list_passkey_unknown_user`, line 3
  fixed `rpId`); `bindPasskey_textLabel_fallsBackThrough*` /
  `bindPasskey_textSubtitle_fallsBackThrough*` /
  `bindPasskey_textPackage_isAlwaysRpId`
- 1.4 — `CredentialListSorting.byLastUsedThenCreatedDesc` uses
  `nullsLast(reverseOrder())` then `thenByDescending { createdAt }`;
  `mergeAndSort` is called inside `CredentialListViewModel.mainListFlow`;
  `CredentialListSortingTest` (13 tests including
  `nullLastUsed_landsAfterNonNull` / `longBoundary_handlesMaxValue` /
  `variantsInterleave_correctly`) + ViewModel's
  `uiState_combine_mergesPasswordsAndPasskeys_byLastUsedDescNullsLast`
- 1.5 — `CredentialListItem.stableId` uses `"pw:"` / `"pk:"` prefix;
  `CredentialListAdapter.DIFF.areItemsTheSame` compares stableId;
  `diffAreItemsTheSame_passwordIdOne_andPasskeyCredentialIdOne_returnsFalse`
- 1.6 — `DIFF.areContentsTheSame` switches on variant pairs
  (Password = 5-field equality of packageName/username/label/updatedAt/
  signatureSha256; Passkey = full data-class equality of
  PasskeyDisplayModel); `diffAreItemsTheSame_samePasskeyCredentialId_returnsTrue`
  / `diffAreItemsTheSame_samePasswordId_returnsTrue`
- 1.7 — `computeEmptyKind` keeps the Initial/NoMatch contract and now
  also flips to NoMatch when kindFilter != All; 5
  `computeEmptyKind_*` tests
- 1.8 — `PasskeyViewHolder.bind` uses
  `binding.iconApp.setImageResource(R.drawable.ic_passkey_24)` directly
  (no `iconLoader.loadInto` for PassKey rows); covered by the same
  `bindPasskey_paintsIcPasskey24_*` tests as 1.2
- 1.9 — `PasskeyViewHolder.bind` sets `chipSignature.visibility`,
  `strengthBar.visibility`, `btnOverflow.visibility` all to `View.GONE`;
  `bindPasskey_paintsIcPasskey24_andHidesChipSignatureStrengthBarAndOverflow`
- 2.1 / 2.2 — `CredentialListViewModel.applySearch` `when (item)`
  sealed-when over CredentialListItem; PassKey branch hits rpId,
  rpDisplayName, userName, userDisplayName, displayName;
  `applySearch_hitsPasskeyRpId` / `applySearch_hitsPasskeyRpDisplayName`
  / `applySearch_hitsPasskeyUserName_caseInsensitive` /
  `applySearch_hitsPasskeyUserDisplayName` /
  `applySearch_matchesPasswordLabelUsernamePackage_caseInsensitively`
- 2.3 — pipeline is in-memory pure functions inside
  `mainListFlow = combine(...)`; Room Flow handles IO; reviewer accepts
  the design's choice not to add `flowOn(Dispatchers.Default)` because
  the upstream Room Flow already provides off-main execution
- 2.5 — `applySearch` lowercases both needle and field;
  `applySearch_hitsPasskeyUserName_caseInsensitive` asserts case-
  insensitive match
- 2.6 — `if (input.query.isBlank()) afterKind else applySearch(...)` in
  `mainListFlow`; `uiState_clearSearch_returnsToFullList_*`
- 2.7 — `applyFilter` `when (item)` keeps Passkey rows untouched for
  SignatureMatched and SignatureMissing;
  `applyFilter_signatureMatched_keepsPasskeyRowsRegardlessOfPasswordFilter`
  / `applyFilter_signatureMissing_keepsPasskeyRowsRegardlessOfPasswordFilter`
- 3.1 — `credential_list_passkey_kind_label` = "PassKey" in
  `values/strings.xml`, `values-en/strings.xml`, `values-ja/strings.xml`
- 3.2 — `credential_list_password_kind_label` = "Password" (en) /
  "パスワード" (ja+default) — all three locale files carry it
- 3.3 — `CredentialListAdapter.PasskeyViewHolder.bind` references
  `R.string.credential_list_passkey_kind_label` for contentDescription;
  `CredentialListActivity.showPasskeyTapSnackbar` references
  `R.string.credential_list_passkey_tap_v1_message`; no hardcoded
  user-facing PassKey strings in changed files
- 3.4 — grep over the changed `values-ja/strings.xml` finds only
  "PassKey" + the explicit policy reminder comments listing the
  forbidden spellings; no forbidden Japanese spellings used in user-
  visible string values (the matches for `passkey_*` / `Passkey*` are
  pre-existing Issue #99 / #100 resource keys and class names, not
  values)
- 3.5 — `ic_passkey_24.xml` / `ic_password_24.xml` follow the
  lowercase + underscore Android resource naming convention
- 4.1 — `uiState_combine_mergesPasswordsAndPasskeys_byLastUsedDescNullsLast`
  uses a 4-item fixture (2 passwords + 2 PassKeys, including a
  NULL `lastUsedAt`) and asserts both interleaving and NULL-last
- 4.2 — `applySearch_hitsPasskeyUserName_caseInsensitive` uses query
  "ALICE" against userName "alice"; passes case-insensitive contains
- 4.3 — `applyFilter_signatureMatched_keepsPasskeyRowsRegardlessOfPasswordFilter`
  / `applyFilter_signatureMissing_keepsPasskeyRowsRegardlessOfPasswordFilter`
- 4.4 — `getItemViewType_returnsPasswordCode_forPasswordVariant` /
  `getItemViewType_returnsPasskeyCode_forPasskeyVariant` /
  `onCreateViewHolder_password_returnsPasswordViewHolderClass` /
  `onCreateViewHolder_passkey_returnsPasskeyViewHolderClass`
- 4.5 — `bindPasskey_paintsIcPasskey24_andHidesChipSignatureStrengthBarAndOverflow`
  reads `iconApp.drawable` and asserts it is the vector drawable
  for PassKey rows; `bindPassword_paintsAppIconViaIconLoader_notSetImageResource`
  guards the password path
- 4.6 — `PasskeyDisplayModelTest.dataClassDoesNotDeclareSensitiveEntityFields_reflection`
  walks `declaredFields` and asserts none of userHandle /
  encryptedPrivateKey / privateKeyIv / keyAlias / signCount appear;
  `dataClassDoesNotMentionSensitiveFields_inToString` is an extra
  belt-and-suspenders check
- 4.7 — 5 `computeEmptyKind_*` tests in `CredentialListViewModelTest`
  cover all branches (Initial / NoMatch by query / NoMatch by filter /
  NoMatch by kindFilter / null when non-empty)
- 4.8 — existing password-only `applySearch_matchesPasswordLabelUsernamePackage_caseInsensitively`
  / `applyFilter_partitionsBySignatureSha256_onPasswordRows` /
  `applySearch_returnsEmpty_whenNoMatch` keep their assertion bodies
  after the type follow-up
- 4.9 — `CredentialListEmptyStateTest` untouched in the diff; the
  EmptyKind helper signature changed to take a kindFilter but the
  test does not exercise that helper, so no follow-up needed there
- 4.10 — `PasskeyDaoTest` has 6 new `listAll_*` cases (empty / single /
  multiple / ordering / tiebreaker / discoverable+non-discoverable)
- 4.11 — `PasskeyRepositoryTest.listAll_delegatesToDao` +
  `listAll_emitsEmptyList_whenDaoEmits_empty`
- 5.1 / 5.2 — `CredentialListActivity.onItemClick = { item -> when (item)
  { is Passkey -> { viewModel.onPasskeyClicked(it.passkey);
  showPasskeyTapSnackbar() } } }`; `showPasskeyTapSnackbar()` uses
  `R.string.credential_list_passkey_tap_v1_message` with
  `Snackbar.LENGTH_SHORT`; ViewModel test
  `onPasskeyClicked_doesNotMutateUiState` confirms no state side effect
- 5.3 — `PasskeyViewHolder.bind` long-click handler returns `true` and
  Activity's `onItemLongClick = { item -> when (item) { is Passkey -> Unit } }`
  — promptDelete is unreachable
- 5.4 — `PasskeyViewHolder.bind` sets `btnOverflow.visibility = View.GONE`;
  the Activity's overflow callback also defensively pattern-matches to
  `Unit` for Passkey rows
- 5.5 — `onItemClick: (CredentialListItem) -> Unit` callback shape
  established; future PassKey edit Activity can swap behaviour without
  changing the Adapter contract
- 6.1 — `PasskeyViewHolder.bind` sets `iconApp.contentDescription =
  ctx.getString(R.string.credential_list_passkey_kind_label)`;
  `bindPasskey_setsIconContentDescription_toPassKey`
- 6.2 — `PasswordViewHolder.bind` explicitly resets
  `iconApp.contentDescription = null`;
  `bindPassword_setsIconContentDescription_toNull`
- 6.3 — covered structurally by 6.1 + 6.2 (TalkBack reads the row
  contents from the underlying text views); manual QA is out of
  reviewer scope
- 6.4 — All 5 PassKey-list strings present in `values/`, `values-en/`
  and `values-ja/` (verified by direct read)
- NFR 1.1 — `PasskeyDao.listAll(): Flow<List<PasskeyEntity>>` is a
  Room `@Query` Flow that runs off the main thread by Room's
  invalidation tracker; `PasskeyRepositoryImpl.listAll` is a pure
  delegate
- NFR 1.2 — `mainListFlow` is a single `combine` inside
  `viewModelScope`; no blocking calls; helpers are pure functions
- NFR 1.5 — `mergeAndSort` uses `sortedWith(comparator)` = TimSort =
  O(N log N)
- NFR 2.1 — `PasskeyDisplayModel` declares only 9 non-sensitive fields;
  `dataClassDoesNotDeclareSensitiveEntityFields_reflection`
- NFR 2.2 — `ListPasskeysUseCase` maps `PasskeyEntity` →
  `PasskeyDisplayModel` inside the use case so ViewModel never
  observes the Entity type
- NFR 2.3 — `CredentialListViewModel.onPasskeyClicked` logs only
  `rpIdLength=${passkey.rpId.length}`;
  `CredentialListActivity.renderState` logs only counts / state-kinds;
  no raw credentialId / rpId / userName / query reaches logcat in
  changed code
- NFR 3.1 — `PasskeyDao` existing 8 methods unchanged; `PasskeyRepository`
  existing 4+3 methods unchanged; only additive new methods
- NFR 3.5 — existing password-only helper tests preserved; signature
  follow-up changes the input wrapping only, not the assertion logic
- NFR 4.1 — no Manifest / permission changes in the diff
- NFR 5.1 / 5.2 — new strings use the `credential_list_passkey_*` /
  `credential_list_password_*` prefix; class names / KDoc use
  "PassKey" spelling consistently
- D-1 — confirmed across new strings (PassKey only), KDoc, and code
- D-8 — confirmed via `stableId` prefix + DIFF.areItemsTheSame test
- D-9 — no `KeyNestDatabase.version` change and no new
  `app/schemas/*.json` entries in the diff
- Boundary check — all changed source files fall inside the scope
  enumerated by tasks.md T-01..T-10 (`data/dao/PasskeyDao.kt`,
  `data/repository/PasskeyRepositoryImpl.kt`,
  `domain/repository/PasskeyRepository.kt`,
  `domain/usecase/ListPasskeysUseCase.kt`,
  `di/ServiceLocator.kt`, `ui/list/*`, `res/drawable/ic_passkey_24.xml`
  + `ic_password_24.xml`, `res/values{,-en,-ja}/strings.xml`,
  tests under `app/src/test/.../ui/list/` and `.../data/`,
  `docs/specs/101-*/impl-notes.md`). No
  `credentialprovider/`, `autofill/`, `Migration_*`,
  `KeyNestDatabase` schema or `Manifest` files were touched —
  matches the Non-Goal list in requirements.md

## Findings

なし

## Summary

All 30+ acceptance criteria across Requirements 1–6 (statements, search,
display, tests, tap, accessibility) plus NFR 1/2/3/4/5/6 are covered by
either the added implementation or new/extended tests. Boundary scope
matches the tasks.md plan exactly — only additive changes to
`PasskeyDao` / `PasskeyRepository`, new UI-layer files in `ui/list/`,
the `ListPasskeysUseCase`, two vector drawables, five string keys per
locale, and the ServiceLocator/Activity wire-up. No DB schema change,
no Manifest change, no credentialprovider/autofill regression risk.
Impl-notes.md transparently records pre-existing failures
(`AppInfoProviderTest` and `PackageSignatureResolver` lint) that are
not introduced by this PR.

RESULT: approve
