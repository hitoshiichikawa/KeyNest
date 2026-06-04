package io.github.hitoshiichikawa.keynest.ui.list

import io.github.hitoshiichikawa.keynest.domain.model.Credential
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.CredentialSortOrder
import io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord
import io.github.hitoshiichikawa.keynest.domain.model.SigningHash
import io.github.hitoshiichikawa.keynest.domain.repository.PasskeyRepository
import io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity
import io.github.hitoshiichikawa.keynest.domain.model.DeletePasskeyResult
import io.github.hitoshiichikawa.keynest.domain.model.Passkey
import io.github.hitoshiichikawa.keynest.domain.model.SavePasskeyRequest
import io.github.hitoshiichikawa.keynest.domain.usecase.DeleteCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.DuplicateCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.FakeCredentialRepository
import io.github.hitoshiichikawa.keynest.domain.usecase.ListCredentialsUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.ListPasskeysUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.ObserveRecentlyUsedUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Behaviour of [CredentialListViewModel]. Issue #9 Req 1.x, 2.x, 3.x,
 * 4.x, 5.x — extended for Issue #101 (Phase 4 of umbrella #89) to
 * cover password+PassKey combine, variant-aware search/filter, and
 * the kindFilter v1 plumbing.
 *
 * Drives a real ViewModel against a [FakeCredentialRepository] and a
 * lightweight [FakePasskeyRepository] so we exercise the actual
 * `combine` / `flatMapLatest` composition rather than mocking
 * individual flows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CredentialListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- pure helpers (Issue #9 Req 1.1, 1.5, 2.x, 4.x) ----------------

    @Test
    fun applySearch_matchesPasswordLabelUsernamePackage_caseInsensitively() {
        // Issue #9 Req 1.1 — kept verbatim (logic unchanged), only the
        // assertion side migrates to `CredentialListItem.Password` wrappers.
        val list = listOf(
            passwordItem(id = 1, label = "Banana", username = "alice", packageName = "com.fruit.b"),
            passwordItem(id = 2, label = "Carrot", username = "bob", packageName = "com.veg.c"),
            passwordItem(id = 3, label = "Apple", username = "alice2", packageName = "com.fruit.a"),
        )

        val byLabel = CredentialListViewModel.applySearch(list, "BAN")
        assertThat(byLabel.map { (it as CredentialListItem.Password).credential.id.value })
            .containsExactly(1L)

        val byUsername = CredentialListViewModel.applySearch(list, "ali")
        assertThat(byUsername.map { (it as CredentialListItem.Password).credential.id.value })
            .containsExactly(1L, 3L)

        val byPackage = CredentialListViewModel.applySearch(list, "veg")
        assertThat(byPackage.map { (it as CredentialListItem.Password).credential.id.value })
            .containsExactly(2L)
    }

    @Test
    fun applySearch_returnsEmpty_whenNoMatch() {
        val list = listOf(passwordItem(id = 1, label = "Banana"))
        val out = CredentialListViewModel.applySearch(list, "carrot")
        assertThat(out).isEmpty()
    }

    @Test
    fun applyFilter_partitionsBySignatureSha256_onPasswordRows() {
        // Issue #9 Req 2.3 / 2.4 — assertion type follows new wrapper.
        val list = listOf(
            passwordItem(id = 1, hasSignature = true),
            passwordItem(id = 2, hasSignature = false),
            passwordItem(id = 3, hasSignature = true),
        )

        val matched = CredentialListViewModel.applyFilter(list, CredentialFilter.SignatureMatched)
        assertThat(matched.map { (it as CredentialListItem.Password).credential.id.value })
            .containsExactly(1L, 3L)

        val missing = CredentialListViewModel.applyFilter(list, CredentialFilter.SignatureMissing)
        assertThat(missing.map { (it as CredentialListItem.Password).credential.id.value })
            .containsExactly(2L)

        val none = CredentialListViewModel.applyFilter(list, CredentialFilter.None)
        assertThat(none).hasSize(3)
    }

    @Test
    fun computeEmptyKind_returnsInitial_whenNoUserInput_andListEmpty() {
        // Issue #9 Req 1.4 — Initial when nothing has narrowed the list.
        val out = CredentialListViewModel.computeEmptyKind(
            mainList = emptyList(),
            query = "",
            filter = CredentialFilter.None,
            kindFilter = KindFilter.All,
        )
        assertThat(out).isEqualTo(EmptyKind.Initial)
    }

    @Test
    fun computeEmptyKind_returnsNoMatch_whenUserNarrowed_andListEmpty() {
        // Issue #9 Req 1.4 / 2.7.
        val out = CredentialListViewModel.computeEmptyKind(
            mainList = emptyList(),
            query = "abc",
            filter = CredentialFilter.None,
            kindFilter = KindFilter.All,
        )
        assertThat(out).isEqualTo(EmptyKind.NoMatch)
    }

    @Test
    fun computeEmptyKind_returnsNoMatch_whenFilterActive_andListEmpty() {
        // Issue #9 Req 2.7.
        val out = CredentialListViewModel.computeEmptyKind(
            mainList = emptyList(),
            query = "",
            filter = CredentialFilter.SignatureMatched,
            kindFilter = KindFilter.All,
        )
        assertThat(out).isEqualTo(EmptyKind.NoMatch)
    }

    @Test
    fun computeEmptyKind_returnsNoMatch_whenKindFilterNarrowsToEmpty() {
        // Issue #101 Q-2 future-facing — a non-default kindFilter also
        // flips the empty-state to NoMatch.
        val out = CredentialListViewModel.computeEmptyKind(
            mainList = emptyList(),
            query = "",
            filter = CredentialFilter.None,
            kindFilter = KindFilter.PasskeyOnly,
        )
        assertThat(out).isEqualTo(EmptyKind.NoMatch)
    }

    @Test
    fun computeEmptyKind_returnsNull_whenListNonEmpty() {
        val out = CredentialListViewModel.computeEmptyKind(
            mainList = listOf(passwordItem(1)),
            query = "anything",
            filter = CredentialFilter.SignatureMatched,
            kindFilter = KindFilter.All,
        )
        assertThat(out).isNull()
    }

    // ---- Issue #101 R2.x: PassKey-aware search -------------------------

    @Test
    fun applySearch_hitsPasskeyUserName_caseInsensitive() {
        // Issue #101 R4.2 / R2.1 / R2.5. The lowercase comparison must
        // hit both an uppercase needle (R2.5) and a userName field.
        val list = listOf(
            passwordItem(id = 1, label = "Twitter", username = "carol", packageName = "com.twitter"),
            passkeyItem(credentialId = "pk-1", userName = "alice", rpId = "github.com"),
        )

        val out = CredentialListViewModel.applySearch(list, "ALICE")

        assertThat(out).hasSize(1)
        val hit = out.single() as CredentialListItem.Passkey
        assertThat(hit.passkey.credentialId).isEqualTo("pk-1")
    }

    @Test
    fun applySearch_hitsPasskeyRpId() {
        // Issue #101 R2.1.
        val list = listOf(
            passwordItem(id = 1, label = "Twitter", username = "alice", packageName = "com.x"),
            passkeyItem(credentialId = "pk-1", rpId = "example.com", userName = null),
        )

        val out = CredentialListViewModel.applySearch(list, "example")

        assertThat(out).hasSize(1)
        assertThat((out.single() as CredentialListItem.Passkey).passkey.rpId).isEqualTo("example.com")
    }

    @Test
    fun applySearch_hitsPasskeyRpDisplayName() {
        // Issue #101 R2.1.
        val list = listOf(
            passkeyItem(
                credentialId = "pk-1",
                rpId = "anonymous-rp",
                rpDisplayName = "MyBank",
                userName = "carol",
            ),
        )

        val out = CredentialListViewModel.applySearch(list, "bank")

        assertThat(out).hasSize(1)
    }

    @Test
    fun applySearch_hitsPasskeyUserDisplayName() {
        // Issue #101 R2.1 — userDisplayName is a separate search field.
        val list = listOf(
            passkeyItem(
                credentialId = "pk-1",
                userDisplayName = "Alice Cooper",
                userName = null,
            ),
        )

        val out = CredentialListViewModel.applySearch(list, "cooper")

        assertThat(out).hasSize(1)
    }

    @Test
    fun applySearch_returnsEmpty_whenNeedleMatchesNoVariant() {
        val list = listOf(
            passwordItem(id = 1, label = "Banana"),
            passkeyItem(credentialId = "pk-1", rpId = "example.com", userName = "alice"),
        )

        val out = CredentialListViewModel.applySearch(list, "nothing-matches")

        assertThat(out).isEmpty()
    }

    @Test
    fun applySearch_ignoresPassKeyRows_whenAllFieldsNull() {
        // Defensive: a PassKey with no searchable text must NOT spuriously
        // hit a non-empty needle (`null ?: ""` contains anything = false).
        val list = listOf(
            passkeyItem(
                credentialId = "pk-1",
                rpId = "ignored.example",
                rpDisplayName = null,
                userName = null,
                userDisplayName = null,
                displayName = null,
            ),
        )

        // `rpId` still matches its own substring, so search for a token
        // that is in *none* of the fields:
        val out = CredentialListViewModel.applySearch(list, "absent-needle-token")

        assertThat(out).isEmpty()
    }

    // ---- Issue #101 R2.7: signature chip filter passes PassKey through

    @Test
    fun applyFilter_signatureMatched_keepsPasskeyRowsRegardlessOfPasswordFilter() {
        // Issue #101 R2.7 / R4.3.
        val list = listOf(
            passwordItem(id = 1, hasSignature = true),
            passwordItem(id = 2, hasSignature = false),
            passkeyItem(credentialId = "pk-1"),
            passkeyItem(credentialId = "pk-2"),
        )

        val out = CredentialListViewModel.applyFilter(list, CredentialFilter.SignatureMatched)

        // Password id=2 (no signature) dropped; both PassKeys pass through.
        assertThat(out).hasSize(3)
        assertThat(out.filterIsInstance<CredentialListItem.Passkey>()).hasSize(2)
    }

    @Test
    fun applyFilter_signatureMissing_keepsPasskeyRowsRegardlessOfPasswordFilter() {
        // Issue #101 R2.7 / R4.3.
        val list = listOf(
            passwordItem(id = 1, hasSignature = true),
            passwordItem(id = 2, hasSignature = false),
            passkeyItem(credentialId = "pk-1"),
        )

        val out = CredentialListViewModel.applyFilter(list, CredentialFilter.SignatureMissing)

        // Password id=1 (has signature) dropped; PassKey passes through.
        assertThat(out).hasSize(2)
        assertThat(out.filterIsInstance<CredentialListItem.Passkey>()).hasSize(1)
    }

    // ---- Issue #101 Q-2: kindFilter helper -----------------------------

    @Test
    fun applyKindFilter_all_passesAllRowsThrough() {
        val list = listOf(passwordItem(1), passkeyItem("pk"))
        val out = CredentialListViewModel.applyKindFilter(list, KindFilter.All)
        assertThat(out).hasSize(2)
    }

    @Test
    fun applyKindFilter_passwordOnly_dropsPasskeyRows() {
        val list = listOf(passwordItem(1), passkeyItem("pk"))
        val out = CredentialListViewModel.applyKindFilter(list, KindFilter.PasswordOnly)
        assertThat(out).hasSize(1)
        assertThat(out.single()).isInstanceOf(CredentialListItem.Password::class.java)
    }

    @Test
    fun applyKindFilter_passkeyOnly_dropsPasswordRows() {
        val list = listOf(passwordItem(1), passkeyItem("pk"))
        val out = CredentialListViewModel.applyKindFilter(list, KindFilter.PasskeyOnly)
        assertThat(out).hasSize(1)
        assertThat(out.single()).isInstanceOf(CredentialListItem.Passkey::class.java)
    }

    // ---- end-to-end via FakeCredentialRepository + FakePasskeyRepository

    @Test
    fun uiState_initialDefault_isUpdatedAtDescNoneEmptyQueryAllKindFilter() = runTest(testDispatcher) {
        // Issue #9 Req 4.2 + Req 2.2 + Req 1.3 + Issue #101 v1 Q-2 default.
        val (vm, _, _, job) = newViewModelWithCollector()
        try {
            advanceUntilIdle()
            val state = vm.uiState.value
            assertThat(state.query).isEqualTo("")
            assertThat(state.filter).isEqualTo(CredentialFilter.None)
            assertThat(state.sort).isEqualTo(CredentialSortOrder.UpdatedAtDesc)
            assertThat(state.kindFilter).isEqualTo(KindFilter.All)
            assertThat(state.passkeyCount).isEqualTo(0)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_search_narrowsMainList_acrossBothVariants() = runTest(testDispatcher) {
        // Issue #9 Req 1.1 + Issue #101 R2.1.
        val (vm, repo, passkeyRepo, job) = newViewModelWithCollector()
        try {
            repo.put(blankRecord("alice", "Apple"))
            repo.put(blankRecord("bob", "Banana"))
            passkeyRepo.emit(
                passkeyEntity(credentialId = "pk-alice", userName = "alice", rpId = "github.com"),
            )

            vm.onQueryChanged("alic")
            advanceUntilIdle()

            val state = vm.uiState.value
            // 1 password (alice) + 1 passkey (alice) survive the search.
            assertThat(state.mainList).hasSize(2)
            assertThat(state.passkeyCount).isEqualTo(1)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_emptySearch_setsNoMatch() = runTest(testDispatcher) {
        // Issue #9 Req 1.4.
        val (vm, repo, _, job) = newViewModelWithCollector()
        try {
            repo.put(blankRecord("alice", "Apple"))

            vm.onQueryChanged("does not exist")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertThat(state.mainList).isEmpty()
            assertThat(state.emptyKind).isEqualTo(EmptyKind.NoMatch)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_clearSearch_returnsToFullList_whileFilterPreserved() = runTest(testDispatcher) {
        // Issue #9 Req 1.3.
        val (vm, repo, _, job) = newViewModelWithCollector()
        try {
            repo.put(blankRecord("alice", "Apple", hasSig = true))
            repo.put(blankRecord("bob", "Banana", hasSig = false))

            vm.onFilterChanged(CredentialFilter.SignatureMatched)
            vm.onQueryChanged("alice")
            advanceUntilIdle()
            assertThat(vm.uiState.value.mainList).hasSize(1)

            vm.onQueryChanged("")
            advanceUntilIdle()
            assertThat(vm.uiState.value.filter).isEqualTo(CredentialFilter.SignatureMatched)
            assertThat(vm.uiState.value.mainList).hasSize(1)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_filterToggleOff_returnsAllRows() = runTest(testDispatcher) {
        // Issue #9 Req 2.2 / 2.5.
        val (vm, repo, _, job) = newViewModelWithCollector()
        try {
            repo.put(blankRecord("alice", "Apple", hasSig = true))
            repo.put(blankRecord("bob", "Banana", hasSig = false))

            vm.onFilterChanged(CredentialFilter.SignatureMatched)
            advanceUntilIdle()
            assertThat(vm.uiState.value.mainList).hasSize(1)

            vm.onFilterChanged(CredentialFilter.None)
            advanceUntilIdle()
            assertThat(vm.uiState.value.mainList).hasSize(2)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_recentList_isIndependentOfQueryAndFilter() = runTest(testDispatcher) {
        // Issue #9 Req 3.6.
        val (vm, repo, _, job) = newViewModelWithCollector()
        try {
            val aliceId = repo.save(blankRecord("alice", "Apple", hasSig = false))
            val bobId = repo.save(blankRecord("bob", "Banana", hasSig = true))
            repo.markUsed(aliceId, timestamp = 100L)
            repo.markUsed(bobId, timestamp = 200L)

            vm.onFilterChanged(CredentialFilter.SignatureMatched)
            vm.onQueryChanged("BAN")
            advanceUntilIdle()

            val state = vm.uiState.value
            // mainList: only bob (passes signature filter + search).
            assertThat(state.mainList).hasSize(1)
            // recentList stays untouched.
            assertThat(state.recentList.map { it.username }).containsExactly("bob", "alice").inOrder()
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_recentList_isEmpty_whenNoRowHasLastUsedAt() = runTest(testDispatcher) {
        // Issue #9 Req 3.4.
        val (vm, repo, _, job) = newViewModelWithCollector()
        try {
            repo.put(blankRecord("alice", "Apple"))
            advanceUntilIdle()
            assertThat(vm.uiState.value.recentList).isEmpty()
        } finally {
            job.cancel()
        }
    }

    // ---- Issue #101 R4.1: combine ordering -----------------------------

    @Test
    fun uiState_combine_mergesPasswordsAndPasskeys_byLastUsedDescNullsLast() =
        runTest(testDispatcher) {
            // Issue #101 R4.1 / R1.4. design.md §15.1 fixture:
            //  - pw1 lastUsedAt=300 createdAt=100  -> top
            //  - pk1 lastUsedAt=200 createdAt=200  -> 2nd
            //  - pw2 lastUsedAt=100 createdAt=50   -> 3rd
            //  - pk2 lastUsedAt=null createdAt=150 -> last
            val (vm, repo, passkeyRepo, job) = newViewModelWithCollector()
            try {
                repo.put(
                    blankRecord(
                        "alice",
                        "GitHub",
                        hasSig = true,
                        lastUsedAt = 300L,
                        createdAt = 100L,
                    ),
                )
                repo.put(
                    blankRecord(
                        "bob",
                        "Twitter",
                        hasSig = false,
                        lastUsedAt = 100L,
                        createdAt = 50L,
                    ),
                )
                passkeyRepo.emit(
                    passkeyEntity(
                        credentialId = "pk-1",
                        rpId = "github.com",
                        userName = "alice",
                        lastUsedAt = 200L,
                        createdAt = 200L,
                    ),
                    passkeyEntity(
                        credentialId = "pk-2",
                        rpId = "example.com",
                        userName = "carol",
                        lastUsedAt = null,
                        createdAt = 150L,
                    ),
                )

                advanceUntilIdle()

                val main = vm.uiState.value.mainList
                assertThat(main).hasSize(4)
                assertThat(main[0].stableId).startsWith("pw:")
                assertThat((main[0] as CredentialListItem.Password).credential.username)
                    .isEqualTo("alice")
                assertThat(main[1].stableId).isEqualTo("pk:pk-1")
                assertThat(main[2].stableId).startsWith("pw:")
                assertThat((main[2] as CredentialListItem.Password).credential.username)
                    .isEqualTo("bob")
                assertThat(main[3].stableId).isEqualTo("pk:pk-2")
                assertThat(vm.uiState.value.passkeyCount).isEqualTo(2)
            } finally {
                job.cancel()
            }
        }

    // ---- Issue #101 NFR 2.3: onPasskeyClicked logging ------------------

    @Test
    fun onPasskeyClicked_doesNotMutateUiState() = runTest(testDispatcher) {
        // Tapping a PassKey row must not move sort / filter / query /
        // kindFilter state. The Activity owns the Snackbar so the
        // ViewModel observable is unaffected.
        val (vm, _, passkeyRepo, job) = newViewModelWithCollector()
        try {
            passkeyRepo.emit(passkeyEntity(credentialId = "pk-1", rpId = "ex.com", userName = "a"))
            advanceUntilIdle()
            val before = vm.uiState.value

            vm.onPasskeyClicked(
                PasskeyDisplayModel(
                    credentialId = "pk-1",
                    rpId = "ex.com",
                    rpDisplayName = null,
                    userName = "a",
                    userDisplayName = null,
                    displayName = null,
                    isDiscoverable = true,
                    createdAt = 0L,
                    lastUsedAt = null,
                ),
            )
            advanceUntilIdle()

            assertThat(vm.uiState.value).isEqualTo(before)
        } finally {
            job.cancel()
        }
    }

    // ---- existing duplicate side-channel cases -------------------------

    @Test
    fun onDuplicate_emitsSuccess_andAddsRow() = runTest(testDispatcher) {
        // Issue #9 Req 5.3 / 5.4.
        val (vm, repo, _, job) = newViewModelWithCollector()
        try {
            val srcId = repo.save(blankRecord("alice", "Apple"))
            val before = repo.snapshot().size

            val outcomes = mutableListOf<CredentialListViewModel.DuplicateOutcome>()
            val sideJob = launch { vm.duplicateResult.collect { outcomes.add(it) } }

            vm.onDuplicate(srcId)
            advanceUntilIdle()

            assertThat(repo.snapshot().size).isEqualTo(before + 1)
            assertThat(outcomes).hasSize(1)
            assertThat(outcomes.single())
                .isInstanceOf(CredentialListViewModel.DuplicateOutcome.Success::class.java)
            sideJob.cancel()
        } finally {
            job.cancel()
        }
    }

    @Test
    fun onDuplicate_emitsFailure_whenSourceMissing() = runTest(testDispatcher) {
        val (vm, _, _, job) = newViewModelWithCollector()
        try {
            val outcomes = mutableListOf<CredentialListViewModel.DuplicateOutcome>()
            val sideJob = launch { vm.duplicateResult.collect { outcomes.add(it) } }

            vm.onDuplicate(CredentialId(9999L))
            advanceUntilIdle()

            assertThat(outcomes).hasSize(1)
            val failure = outcomes.single()
            assertThat(failure).isInstanceOf(CredentialListViewModel.DuplicateOutcome.Failure::class.java)
            assertThat((failure as CredentialListViewModel.DuplicateOutcome.Failure).reason)
                .isEqualTo("NotFound")
            sideJob.cancel()
        } finally {
            job.cancel()
        }
    }

    // ---- helpers -------------------------------------------------------

    private data class Fixture(
        val vm: CredentialListViewModel,
        val passwordRepo: FakeCredentialRepository,
        val passkeyRepo: FakePasskeyRepository,
        val job: Job,
    )

    private fun TestScope.newViewModelWithCollector(): Fixture {
        val passwordRepo = FakeCredentialRepository()
        val passkeyRepo = FakePasskeyRepository()
        val vm = CredentialListViewModel(
            listUseCase = ListCredentialsUseCase(passwordRepo),
            recentUseCase = ObserveRecentlyUsedUseCase(passwordRepo),
            duplicateUseCase = DuplicateCredentialUseCase(passwordRepo, now = { 42L }),
            deleteUseCase = DeleteCredentialUseCase(passwordRepo),
            listPasskeysUseCase = ListPasskeysUseCase(passkeyRepo),
        )
        val job = vm.uiState.onEach { /* keep alive */ }.launchIn(this)
        return Fixture(vm, passwordRepo, passkeyRepo, job)
    }

    private fun passwordItem(
        id: Long,
        label: String = "L-$id",
        username: String = "u-$id",
        packageName: String = "com.example.$id",
        hasSignature: Boolean = false,
        lastUsedAt: Long? = null,
        createdAt: Long = 0L,
    ): CredentialListItem.Password = CredentialListItem.Password(
        Credential(
            id = CredentialId(id),
            packageName = packageName,
            username = username,
            label = label,
            signatureSha256 = if (hasSignature) SigningHash(ByteArray(32) { 0x11.toByte() }) else null,
            signatureCapturedAt = if (hasSignature) 1L else null,
            createdAt = createdAt,
            updatedAt = 0L,
            lastUsedAt = lastUsedAt,
        ),
    )

    private fun passkeyItem(
        credentialId: String,
        rpId: String = "example.com",
        rpDisplayName: String? = "Example",
        userName: String? = "alice@example.com",
        userDisplayName: String? = "Alice",
        displayName: String? = null,
        isDiscoverable: Boolean = true,
        createdAt: Long = 0L,
        lastUsedAt: Long? = null,
    ): CredentialListItem.Passkey = CredentialListItem.Passkey(
        PasskeyDisplayModel(
            credentialId = credentialId,
            rpId = rpId,
            rpDisplayName = rpDisplayName,
            userName = userName,
            userDisplayName = userDisplayName,
            displayName = displayName,
            isDiscoverable = isDiscoverable,
            createdAt = createdAt,
            lastUsedAt = lastUsedAt,
        ),
    )

    private fun blankRecord(
        username: String,
        label: String,
        hasSig: Boolean = false,
        updatedAt: Long = 0L,
        createdAt: Long = 0L,
        lastUsedAt: Long? = null,
    ) = EncryptedCredentialRecord(
        id = CredentialId(0L),
        packageName = "com.example.$username",
        username = username,
        label = label,
        passwordCiphertext = byteArrayOf(1),
        passwordIv = ByteArray(12),
        signatureSha256 = if (hasSig) SigningHash(ByteArray(32) { 0x22.toByte() }) else null,
        signatureCapturedAt = if (hasSig) 1L else null,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastUsedAt = lastUsedAt,
    )

    private fun passkeyEntity(
        credentialId: String,
        rpId: String = "example.com",
        rpDisplayName: String? = "Example",
        userName: String? = "alice",
        userDisplayName: String? = "Alice",
        displayName: String? = null,
        isDiscoverable: Boolean = true,
        createdAt: Long = 0L,
        lastUsedAt: Long? = null,
    ) = PasskeyEntity(
        credentialId = credentialId,
        rpId = rpId,
        rpDisplayName = rpDisplayName,
        userHandle = ByteArray(16) { (credentialId.hashCode() + it).toByte() },
        userName = userName,
        userDisplayName = userDisplayName,
        isDiscoverable = isDiscoverable,
        encryptedPrivateKey = ByteArray(48) { 0x66 },
        privateKeyIv = ByteArray(12) { 0x55 },
        keyAlias = "keynest_passkey_$credentialId",
        signCount = 0L,
        displayName = displayName,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
    )

    /**
     * Minimal in-memory PasskeyRepository for the ViewModel tests. Only
     * [listAll] needs a real Flow; the other interface methods are
     * irrelevant to the credential list combine pipeline and throw if
     * touched (which would signal an unintended dependency).
     */
    private class FakePasskeyRepository : PasskeyRepository {
        private val backing: MutableStateFlow<List<PasskeyEntity>> = MutableStateFlow(emptyList())

        fun emit(vararg entities: PasskeyEntity) {
            backing.value = entities.toList()
        }

        override fun listAll(): Flow<List<PasskeyEntity>> = backing

        // The remaining members are never exercised by the ViewModel
        // pipeline. Default unsupported implementations make sure a
        // future regression that calls them shows up as a clear
        // assertion failure instead of silently returning null.
        //
        // 戻り値の domain 型 (Passkey) は Issue #107 reshape に追従。
        override suspend fun save(request: SavePasskeyRequest): Unit =
            throw UnsupportedOperationException()

        override suspend fun findByCredentialId(credentialId: String): Passkey? =
            throw UnsupportedOperationException()

        override suspend fun findByRpIdAndUserHandle(
            rpId: String,
            userHandle: ByteArray,
        ): Passkey? = throw UnsupportedOperationException()

        override suspend fun listDiscoverableByRpId(rpId: String): List<Passkey> =
            throw UnsupportedOperationException()

        override suspend fun listAllByRpId(rpId: String): List<Passkey> =
            throw UnsupportedOperationException()

        override suspend fun incrementSignCount(credentialId: String, timestamp: Long): Unit =
            throw UnsupportedOperationException()

        override suspend fun loadPrivateKey(credentialId: String): ByteArray? =
            throw UnsupportedOperationException()

        override suspend fun delete(credentialId: String): DeletePasskeyResult =
            throw UnsupportedOperationException()

        override suspend fun <T> signWithIncrement(
            credentialId: String,
            signer: suspend (newSignCount: Long) -> T,
        ): T = throw UnsupportedOperationException()

        override suspend fun update(entity: PasskeyEntity): Unit =
            throw UnsupportedOperationException()
    }
}
