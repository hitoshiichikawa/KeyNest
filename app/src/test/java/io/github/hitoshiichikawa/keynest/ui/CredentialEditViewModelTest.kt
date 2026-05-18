package io.github.hitoshiichikawa.keynest.ui

import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.usecase.DeleteCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.FakeCredentialRepository
import io.github.hitoshiichikawa.keynest.domain.usecase.SaveCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.StubAesGcmCipher
import io.github.hitoshiichikawa.keynest.domain.usecase.UpdateCredentialUseCase
import io.github.hitoshiichikawa.keynest.ui.edit.CredentialEditViewModel
import io.github.hitoshiichikawa.keynest.util.PackageSignatureResolver
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Behaviour of [CredentialEditViewModel] error / success mapping.
 *
 * Backs Req 1.3 (validation error surface) and Req 1.5 (edit path
 * dispatches to UpdateCredentialUseCase). Runs as pure JUnit - the
 * ViewModel only touches use cases and StateFlow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CredentialEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun save_mapsPackageNameBlank_toFieldError() = runTest(testDispatcher) {
        val vm = newViewModel()

        vm.save(existingId = null, packageName = "  ", username = "alice", password = "pw".toCharArray(), label = "L")
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state).isInstanceOf(CredentialEditViewModel.State.FieldError::class.java)
        val err = state as CredentialEditViewModel.State.FieldError
        assertThat(err.field).isEqualTo(CredentialEditViewModel.Field.PackageName)
        assertThat(err.kind).isEqualTo(CredentialEditViewModel.ErrorKind.Blank)
    }

    @Test
    fun save_mapsPackageNameInvalid_toFieldError() = runTest(testDispatcher) {
        val vm = newViewModel()

        vm.save(null, "noDots", "alice", "pw".toCharArray(), "L")
        advanceUntilIdle()

        val err = vm.state.value as CredentialEditViewModel.State.FieldError
        assertThat(err.field).isEqualTo(CredentialEditViewModel.Field.PackageName)
        assertThat(err.kind).isEqualTo(CredentialEditViewModel.ErrorKind.Invalid)
    }

    @Test
    fun save_emitsSavedNavigation_onSuccess() = runTest(testDispatcher) {
        val vm = newViewModel()
        val capturedNav = mutableListOf<Unit>()
        // Collect in a child coroutine inside the same TestScope so the
        // StandardTestDispatcher controls progression.
        val collectorJob = launch { vm.navigation.collect { capturedNav.add(it) } }

        vm.save(null, "com.example.target", "alice", "pw".toCharArray(), "L")
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(CredentialEditViewModel.State.Saved)
        assertThat(capturedNav).hasSize(1)
        collectorJob.cancel()
    }

    @Test
    fun save_inEditMode_dispatchesToUpdateUseCase_andUpdatesSignature() = runTest(testDispatcher) {
        val repo = FakeCredentialRepository()
        repo.put(
            io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord(
                id = CredentialId(0L),
                packageName = "com.example.target",
                username = "alice",
                label = "L",
                passwordCiphertext = byteArrayOf(1),
                passwordIv = ByteArray(12),
                signatureSha256 = null,
                signatureCapturedAt = null,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        val id = repo.snapshot().single().id.value
        val sigResolver = mockk<PackageSignatureResolver>()
        val newHash = io.github.hitoshiichikawa.keynest.domain.model.SigningHash.ofSha256("NEW".toByteArray())
        every { sigResolver.resolveSha256(any()) } returns newHash
        val cipher = StubAesGcmCipher()
        val save = SaveCredentialUseCase(repo, cipher, sigResolver)
        val update = UpdateCredentialUseCase(repo, cipher, sigResolver)
        val delete = DeleteCredentialUseCase(repo)
        val vm = CredentialEditViewModel(repo, save, update, delete)

        vm.save(existingId = id, packageName = "com.example.target", username = "alice2", password = charArrayOf(), label = "L2")
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(CredentialEditViewModel.State.Saved)
        val updated = repo.snapshot().single()
        assertThat(updated.username).isEqualTo("alice2")
        assertThat(updated.label).isEqualTo("L2")
        assertThat(updated.signatureSha256).isEqualTo(newHash)
    }

    // ---- Issue #30 Req 8.10 / 8.12: delete pipeline -------------------------

    @Test
    fun delete_onSuccess_emitsNavigationEvent_andLeavesStateIdle() = runTest(testDispatcher) {
        // Arrange: an existing credential the ViewModel can delete.
        val repo = FakeCredentialRepository()
        repo.put(
            io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord(
                id = CredentialId(0L),
                packageName = "com.example.target",
                username = "alice",
                label = "L",
                passwordCiphertext = byteArrayOf(1),
                passwordIv = ByteArray(12),
                signatureSha256 = null,
                signatureCapturedAt = null,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        val id = repo.snapshot().single().id.value
        val vm = newViewModel(repo)
        val capturedNav = mutableListOf<Unit>()
        val collectorJob = launch { vm.navigation.collect { capturedNav.add(it) } }

        // Act
        vm.delete(id)
        advanceUntilIdle()

        // Assert: navigation emitted (Activity will finish()) and no error
        // state set (Idle is the initial value).
        assertThat(capturedNav).hasSize(1)
        assertThat(vm.state.value).isEqualTo(CredentialEditViewModel.State.Idle)
        assertThat(repo.snapshot()).isEmpty()
        collectorJob.cancel()
    }

    @Test
    fun delete_onStorageFailure_setsDeleteFailedState_andDoesNotEmitNavigation() = runTest(testDispatcher) {
        // Arrange: a DeleteCredentialUseCase whose invoke() returns a failure
        // Result to simulate a true storage error. This is the only failure
        // path observable by the ViewModel — the use case wraps repository
        // exceptions in DeleteFailure.
        val repo = FakeCredentialRepository()
        val sigResolver = mockk<PackageSignatureResolver>().also { every { it.resolveSha256(any()) } returns null }
        val cipher = StubAesGcmCipher()
        val save = SaveCredentialUseCase(repo, cipher, sigResolver)
        val update = UpdateCredentialUseCase(repo, cipher, sigResolver)
        val deleteUseCase = mockk<DeleteCredentialUseCase>()
        coEvery { deleteUseCase.invoke(any()) } returns Result.failure(RuntimeException("disk full"))
        val vm = CredentialEditViewModel(repo, save, update, deleteUseCase)
        val capturedNav = mutableListOf<Unit>()
        val collectorJob = launch { vm.navigation.collect { capturedNav.add(it) } }

        // Act
        vm.delete(credentialId = 42L)
        advanceUntilIdle()

        // Assert: DeleteFailed state is published, navigation is NOT emitted
        // (Req 8.12: screen stays open on failure).
        assertThat(vm.state.value).isEqualTo(CredentialEditViewModel.State.DeleteFailed)
        assertThat(capturedNav).isEmpty()
        collectorJob.cancel()
    }

    @Test
    fun delete_onMissingId_treatsAsSuccess_perRepoIdempotency() = runTest(testDispatcher) {
        // Arrange: an empty repo. The repository's delete() is idempotent,
        // so deleting a non-existent id is treated as success — the
        // ViewModel must still emit navigation and finish the screen.
        val vm = newViewModel()
        val capturedNav = mutableListOf<Unit>()
        val collectorJob = launch { vm.navigation.collect { capturedNav.add(it) } }

        // Act
        vm.delete(credentialId = 9999L)
        advanceUntilIdle()

        // Assert
        assertThat(capturedNav).hasSize(1)
        assertThat(vm.state.value).isEqualTo(CredentialEditViewModel.State.Idle)
        collectorJob.cancel()
    }

    private fun newViewModel(repo: FakeCredentialRepository = FakeCredentialRepository()): CredentialEditViewModel {
        val sigResolver = mockk<PackageSignatureResolver>().also { every { it.resolveSha256(any()) } returns null }
        val cipher = StubAesGcmCipher()
        val save = SaveCredentialUseCase(repo, cipher, sigResolver)
        val update = UpdateCredentialUseCase(repo, cipher, sigResolver)
        val delete = DeleteCredentialUseCase(repo)
        return CredentialEditViewModel(repo, save, update, delete)
    }
}
