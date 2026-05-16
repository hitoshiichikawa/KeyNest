package inc.goodanswers.keynest.ui.list

import inc.goodanswers.keynest.domain.model.CredentialId
import inc.goodanswers.keynest.domain.model.CredentialSortOrder
import inc.goodanswers.keynest.domain.model.EncryptedCredentialRecord
import inc.goodanswers.keynest.domain.model.SigningHash
import inc.goodanswers.keynest.domain.usecase.DeleteCredentialUseCase
import inc.goodanswers.keynest.domain.usecase.DuplicateCredentialUseCase
import inc.goodanswers.keynest.domain.usecase.FakeCredentialRepository
import inc.goodanswers.keynest.domain.usecase.ListCredentialsUseCase
import inc.goodanswers.keynest.domain.usecase.ObserveRecentlyUsedUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
 * 4.x, 5.x.
 *
 * Drives a real ViewModel against a [FakeCredentialRepository] so we
 * exercise the actual `combine` / `flatMapLatest` composition rather
 * than mocking individual flows.
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

    // ---- pure helpers (Req 1.1, 1.5, 2.x, 4.x, 1.4) ---------------------

    @Test
    fun applySearch_matchesLabelUsernamePackage_caseInsensitively() {
        // Req 1.1.
        val list = listOf(
            credentialFixture(id = 1, label = "Banana", username = "alice", packageName = "com.fruit.b"),
            credentialFixture(id = 2, label = "Carrot", username = "bob", packageName = "com.veg.c"),
            credentialFixture(id = 3, label = "Apple", username = "alice2", packageName = "com.fruit.a"),
        )

        // Match by label (case-insensitive)
        val byLabel = CredentialListViewModel.applySearch(list, "BAN")
        assertThat(byLabel.map { it.id.value }).containsExactly(1L)

        // Match by username substring
        val byUsername = CredentialListViewModel.applySearch(list, "ali")
        assertThat(byUsername.map { it.id.value }).containsExactly(1L, 3L)

        // Match by packageName substring
        val byPackage = CredentialListViewModel.applySearch(list, "veg")
        assertThat(byPackage.map { it.id.value }).containsExactly(2L)
    }

    @Test
    fun applySearch_returnsEmpty_whenNoMatch() {
        // Req 1.4 (the data-side; the UiState computation is tested below).
        val list = listOf(credentialFixture(id = 1, label = "Banana"))
        val out = CredentialListViewModel.applySearch(list, "carrot")
        assertThat(out).isEmpty()
    }

    @Test
    fun applyFilter_partitionsBySignatureSha256() {
        // Req 2.3, 2.4.
        val list = listOf(
            credentialFixture(id = 1, hasSignature = true),
            credentialFixture(id = 2, hasSignature = false),
            credentialFixture(id = 3, hasSignature = true),
        )

        val matched = CredentialListViewModel.applyFilter(list, CredentialFilter.SignatureMatched)
        assertThat(matched.map { it.id.value }).containsExactly(1L, 3L)

        val missing = CredentialListViewModel.applyFilter(list, CredentialFilter.SignatureMissing)
        assertThat(missing.map { it.id.value }).containsExactly(2L)

        val none = CredentialListViewModel.applyFilter(list, CredentialFilter.None)
        assertThat(none).hasSize(3)
    }

    @Test
    fun computeEmptyKind_returnsInitial_whenNoUserInput_andListEmpty() {
        // Req 1.4: the existing 'no credentials yet' path stays as
        // EmptyKind.Initial when neither chip nor query are active.
        val out = CredentialListViewModel.computeEmptyKind(
            mainList = emptyList(),
            query = "",
            filter = CredentialFilter.None,
        )
        assertThat(out).isEqualTo(EmptyKind.Initial)
    }

    @Test
    fun computeEmptyKind_returnsNoMatch_whenUserNarrowed_andListEmpty() {
        // Req 1.4 / 2.7: with active query, an empty result becomes NoMatch.
        val out = CredentialListViewModel.computeEmptyKind(
            mainList = emptyList(),
            query = "abc",
            filter = CredentialFilter.None,
        )
        assertThat(out).isEqualTo(EmptyKind.NoMatch)
    }

    @Test
    fun computeEmptyKind_returnsNoMatch_whenFilterActive_andListEmpty() {
        // Req 2.7.
        val out = CredentialListViewModel.computeEmptyKind(
            mainList = emptyList(),
            query = "",
            filter = CredentialFilter.SignatureMatched,
        )
        assertThat(out).isEqualTo(EmptyKind.NoMatch)
    }

    @Test
    fun computeEmptyKind_returnsNull_whenListNonEmpty() {
        val out = CredentialListViewModel.computeEmptyKind(
            mainList = listOf(credentialFixture(1)),
            query = "anything",
            filter = CredentialFilter.SignatureMatched,
        )
        assertThat(out).isNull()
    }

    // ---- end-to-end via FakeCredentialRepository (Req 1.x / 2.x / 3.x) -

    @Test
    fun uiState_initialDefault_isUpdatedAtDescNoneEmptyQuery() = runTest(testDispatcher) {
        // Req 4.2 (default sort) + Req 2.2 (None filter) + Req 1.3 (query
        // starts blank).
        val (vm, _, job) = newViewModelWithCollector()
        try {
            advanceUntilIdle()
            val state = vm.uiState.value
            assertThat(state.query).isEqualTo("")
            assertThat(state.filter).isEqualTo(CredentialFilter.None)
            assertThat(state.sort).isEqualTo(CredentialSortOrder.UpdatedAtDesc)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_search_narrowsMainList() = runTest(testDispatcher) {
        // Req 1.1, 1.2, 1.5 (in-memory match, no IO).
        val (vm, repo, job) = newViewModelWithCollector()
        try {
            repo.put(blankRecord("alice", "Apple"))
            repo.put(blankRecord("bob", "Banana"))

            vm.onQueryChanged("ban")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertThat(state.mainList.map { it.username }).containsExactly("bob")
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_emptySearch_setsNoMatch() = runTest(testDispatcher) {
        // Req 1.4.
        val (vm, repo, job) = newViewModelWithCollector()
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
        // Req 1.3.
        val (vm, repo, job) = newViewModelWithCollector()
        try {
            repo.put(blankRecord("alice", "Apple", hasSig = true))
            repo.put(blankRecord("bob", "Banana", hasSig = false))

            vm.onFilterChanged(CredentialFilter.SignatureMatched)
            vm.onQueryChanged("alice")
            advanceUntilIdle()
            assertThat(vm.uiState.value.mainList.map { it.username }).containsExactly("alice")

            // Clear query but keep filter
            vm.onQueryChanged("")
            advanceUntilIdle()
            assertThat(vm.uiState.value.filter).isEqualTo(CredentialFilter.SignatureMatched)
            assertThat(vm.uiState.value.mainList.map { it.username }).containsExactly("alice")
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_filterToggleOff_returnsAllRows() = runTest(testDispatcher) {
        // Req 2.2, 2.5.
        val (vm, repo, job) = newViewModelWithCollector()
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
    fun uiState_sortSwitch_reorders() = runTest(testDispatcher) {
        // Req 4.3, 4.4: filter then sort.
        val (vm, repo, job) = newViewModelWithCollector()
        try {
            repo.put(blankRecord("alice", "Banana", updatedAt = 1L))
            repo.put(blankRecord("bob", "Apple", updatedAt = 2L))

            // Default (UpdatedAtDesc) -> bob (newer) first.
            advanceUntilIdle()
            assertThat(vm.uiState.value.mainList.map { it.username })
                .containsExactly("bob", "alice").inOrder()

            // LabelAsc -> Apple < Banana, so bob (Apple) first.
            vm.onSortChanged(CredentialSortOrder.LabelAsc)
            advanceUntilIdle()
            assertThat(vm.uiState.value.mainList.map { it.label })
                .containsExactly("Apple", "Banana").inOrder()

            // PackageAsc -- alice's pkg (com.example.alice) < bob's
            // (com.example.bob), so alice first.
            vm.onSortChanged(CredentialSortOrder.PackageAsc)
            advanceUntilIdle()
            assertThat(vm.uiState.value.mainList.map { it.username })
                .containsExactly("alice", "bob").inOrder()
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_recentList_isIndependentOfQueryAndFilter() = runTest(testDispatcher) {
        // Req 3.6.
        val (vm, repo, job) = newViewModelWithCollector()
        try {
            val aliceId = repo.save(blankRecord("alice", "Apple", hasSig = false))
            val bobId = repo.save(blankRecord("bob", "Banana", hasSig = true))
            // Stamp both so they appear in the carousel.
            repo.markUsed(aliceId, timestamp = 100L)
            repo.markUsed(bobId, timestamp = 200L)

            // Apply a narrowing filter + query -- the main list shrinks, but
            // recent list must still show both.
            vm.onFilterChanged(CredentialFilter.SignatureMatched)
            vm.onQueryChanged("BAN")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertThat(state.mainList.map { it.username }).containsExactly("bob")
            assertThat(state.recentList.map { it.username }).containsExactly("bob", "alice").inOrder()
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_recentList_isEmpty_whenNoRowHasLastUsedAt() = runTest(testDispatcher) {
        // Req 3.4.
        val (vm, repo, job) = newViewModelWithCollector()
        try {
            repo.put(blankRecord("alice", "Apple"))

            advanceUntilIdle()

            assertThat(vm.uiState.value.recentList).isEmpty()
        } finally {
            job.cancel()
        }
    }

    @Test
    fun onDuplicate_emitsSuccess_andAddsRow() = runTest(testDispatcher) {
        // Req 5.3, 5.4.
        val (vm, repo, job) = newViewModelWithCollector()
        try {
            val srcId = repo.save(blankRecord("alice", "Apple"))
            val before = repo.snapshot().size

            // Subscribe to the side-channel before the action so the
            // tryEmit value is guaranteed to be observable.
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
        val (vm, _, job) = newViewModelWithCollector()
        try {
            val outcomes = mutableListOf<CredentialListViewModel.DuplicateOutcome>()
            val sideJob = launch { vm.duplicateResult.collect { outcomes.add(it) } }

            vm.onDuplicate(CredentialId(9999L))
            advanceUntilIdle()

            assertThat(outcomes).hasSize(1)
            val failure = outcomes.single()
            assertThat(failure).isInstanceOf(CredentialListViewModel.DuplicateOutcome.Failure::class.java)
            // NFR 1.3: reason is the class name only.
            assertThat((failure as CredentialListViewModel.DuplicateOutcome.Failure).reason)
                .isEqualTo("NotFound")
            sideJob.cancel()
        } finally {
            job.cancel()
        }
    }

    // ---- helpers ----

    /**
     * Build a ViewModel + repository pair and start a background collector
     * on the uiState so the WhileSubscribed-backed stateIn flow is active
     * for the duration of the test. The returned [Job] must be cancelled
     * by the test (callers do this in a `finally` block).
     */
    private fun TestScope.newViewModelWithCollector():
        Triple<CredentialListViewModel, FakeCredentialRepository, Job> {
        val repo = FakeCredentialRepository()
        val vm = CredentialListViewModel(
            listUseCase = ListCredentialsUseCase(repo),
            recentUseCase = ObserveRecentlyUsedUseCase(repo),
            duplicateUseCase = DuplicateCredentialUseCase(repo, now = { 42L }),
            deleteUseCase = DeleteCredentialUseCase(repo),
        )
        // Drain uiState so the stateIn upstream stays running.
        val job = vm.uiState.onEach { /* keep alive */ }.launchIn(this)
        return Triple(vm, repo, job)
    }

    private fun credentialFixture(
        id: Long,
        label: String = "L-$id",
        username: String = "u-$id",
        packageName: String = "com.example.$id",
        hasSignature: Boolean = false,
    ) = inc.goodanswers.keynest.domain.model.Credential(
        id = CredentialId(id),
        packageName = packageName,
        username = username,
        label = label,
        signatureSha256 = if (hasSignature) SigningHash(ByteArray(32) { 0x11.toByte() }) else null,
        signatureCapturedAt = if (hasSignature) 1L else null,
        createdAt = 0L,
        updatedAt = 0L,
        lastUsedAt = null,
    )

    private fun blankRecord(
        username: String,
        label: String,
        hasSig: Boolean = false,
        updatedAt: Long = 0L,
    ) = EncryptedCredentialRecord(
        id = CredentialId(0L),
        packageName = "com.example.$username",
        username = username,
        label = label,
        passwordCiphertext = byteArrayOf(1),
        passwordIv = ByteArray(12),
        signatureSha256 = if (hasSig) SigningHash(ByteArray(32) { 0x22.toByte() }) else null,
        signatureCapturedAt = if (hasSig) 1L else null,
        createdAt = 0L,
        updatedAt = updatedAt,
    )
}
