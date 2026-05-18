package io.github.hitoshiichikawa.keynest.ui.edit

import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.data.entity.DetectedFieldSource
import io.github.hitoshiichikawa.keynest.domain.model.DetectedField
import io.github.hitoshiichikawa.keynest.domain.usecase.DeleteCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.FakeCredentialRepository
import io.github.hitoshiichikawa.keynest.domain.usecase.FakeDetectedFieldRepository
import io.github.hitoshiichikawa.keynest.domain.usecase.ObserveRecentDetectedFieldsUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.SaveCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.StubAesGcmCipher
import io.github.hitoshiichikawa.keynest.domain.usecase.UpdateCredentialUseCase
import io.github.hitoshiichikawa.keynest.security.EncryptedCustomFieldsCodec
import io.github.hitoshiichikawa.keynest.util.PackageSignatureResolver
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Issue #67 Phase 2 ViewModel behaviour for the
 * "recently detected fields" suggestion chip strip. Backs the T11
 * spec checklist in design.md §9.5.
 *
 * Conventions match the other CredentialEditViewModel tests:
 *   - StandardTestDispatcher pinned to Dispatchers.Main so
 *     viewModelScope coroutines are deterministic.
 *   - FakeDetectedFieldRepository pre-populated with sample rows
 *     before the ViewModel is constructed so the Flow has data ready
 *     when the collector starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CredentialEditViewModelSuggestionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val pkg = "com.example.target"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun suggestions_areHidden_beforeAddClick() = runTest(testDispatcher) {
        // The chip strip must NOT pop into view just because the
        // ViewModel exists and a package was bound. It only appears
        // after the user explicitly taps "Add field".
        val detected = FakeDetectedFieldRepository().apply {
            put(field("loginEmail", DetectedFieldSource.ResourceId, 100L))
        }
        val vm = newViewModel(detectedRepo = detected)
        vm.bindPackageForSuggestions(pkg)
        advanceUntilIdle()

        assertThat(vm.suggestion.value.visible).isFalse()
        assertThat(vm.suggestion.value.items).isEmpty()
    }

    @Test
    fun onAddCustomFieldClickedForSuggest_loadsAndShowsSuggestions() = runTest(testDispatcher) {
        val detected = FakeDetectedFieldRepository().apply {
            put(field("loginEmail", DetectedFieldSource.ResourceId, 200L))
            put(field("password", DetectedFieldSource.AutofillHints, 100L))
        }
        val vm = newViewModel(detectedRepo = detected)
        vm.bindPackageForSuggestions(pkg)
        vm.addCustomFieldRow()
        vm.onAddCustomFieldClickedForSuggest()
        advanceUntilIdle()

        val state = vm.suggestion.value
        assertThat(state.visible).isTrue()
        // Sorted by lastDetectedAt DESC (DAO-equivalent ordering).
        assertThat(state.items.map { it.fieldKey })
            .containsExactly("loginEmail", "password").inOrder()
        assertThat(state.emptyMessage).isFalse()
    }

    @Test
    fun onSuggestionClicked_transfersFieldKeyToLastRow_andHidesStrip() = runTest(testDispatcher) {
        val detected = FakeDetectedFieldRepository().apply {
            put(field("loginEmail", DetectedFieldSource.ResourceId, 100L))
        }
        val vm = newViewModel(detectedRepo = detected)
        vm.bindPackageForSuggestions(pkg)
        vm.addCustomFieldRow()
        vm.onAddCustomFieldClickedForSuggest()
        advanceUntilIdle()
        val item = vm.suggestion.value.items.single()

        vm.onSuggestionClicked(item)
        advanceUntilIdle()

        // The new (only) row received the suggested fieldKey.
        val rows = vm.customFields.value.rows
        assertThat(rows).hasSize(1)
        assertThat(rows.single().fieldKey).isEqualTo("loginEmail")
        // The chip strip closes after a successful transfer.
        assertThat(vm.suggestion.value.visible).isFalse()
    }

    @Test
    fun suggestions_filterOutExistingFieldKeys() = runTest(testDispatcher) {
        // Req 4.4: do not suggest keys the user already typed into a
        // customField row.
        val detected = FakeDetectedFieldRepository().apply {
            put(field("loginEmail", DetectedFieldSource.ResourceId, 300L))
            put(field("password", DetectedFieldSource.AutofillHints, 200L))
            put(field("notes", DetectedFieldSource.Hint, 100L))
        }
        val vm = newViewModel(detectedRepo = detected)
        vm.bindPackageForSuggestions(pkg)
        // Pre-populate one row whose key matches a suggestion candidate.
        vm.addCustomFieldRow()
        val firstRowId = vm.customFields.value.rows.single().rowId
        vm.updateCustomFieldKey(firstRowId, "loginEmail")
        // Now click "Add" again to surface a second row + suggestions.
        vm.addCustomFieldRow()
        vm.onAddCustomFieldClickedForSuggest()
        advanceUntilIdle()

        val items = vm.suggestion.value.items
        assertThat(items.map { it.fieldKey }).doesNotContain("loginEmail")
        assertThat(items.map { it.fieldKey }).containsExactly("password", "notes").inOrder()
    }

    @Test
    fun suggestions_dedupeByNormalizedKey() = runTest(testDispatcher) {
        // The same fieldKey observed from two different sources
        // surfaces twice in the repository Flow but should collapse to
        // one suggestion chip (Req 4.5). The ViewModel uses
        // AutofillFieldHeuristics.normalizeKey which is case-insensitive
        // and whitespace-stripping.
        val detected = FakeDetectedFieldRepository().apply {
            put(field("loginEmail", DetectedFieldSource.ResourceId, 200L))
            put(field("LoginEmail", DetectedFieldSource.AutofillHints, 100L))
        }
        val vm = newViewModel(detectedRepo = detected)
        vm.bindPackageForSuggestions(pkg)
        vm.addCustomFieldRow()
        vm.onAddCustomFieldClickedForSuggest()
        advanceUntilIdle()

        val items = vm.suggestion.value.items
        assertThat(items).hasSize(1)
        // The newer of the two duplicates wins (sorted DESC, then
        // deduped — we want the most-recent display value).
        assertThat(items.single().fieldKey).isEqualTo("loginEmail")
    }

    @Test
    fun suggestions_showEmptyMessage_whenNoDetectedFields() = runTest(testDispatcher) {
        // Req 4.3: zero detected fields surfaces as
        // emptyMessage = true (the Activity renders the placeholder
        // text instead of an empty chip group).
        val detected = FakeDetectedFieldRepository()
        val vm = newViewModel(detectedRepo = detected)
        vm.bindPackageForSuggestions(pkg)
        vm.addCustomFieldRow()
        vm.onAddCustomFieldClickedForSuggest()
        advanceUntilIdle()

        val state = vm.suggestion.value
        assertThat(state.visible).isTrue()
        assertThat(state.items).isEmpty()
        assertThat(state.emptyMessage).isTrue()
    }

    @Test
    fun hideSuggestions_resetsStateToHidden() = runTest(testDispatcher) {
        val detected = FakeDetectedFieldRepository().apply {
            put(field("loginEmail", DetectedFieldSource.ResourceId, 100L))
        }
        val vm = newViewModel(detectedRepo = detected)
        vm.bindPackageForSuggestions(pkg)
        vm.addCustomFieldRow()
        vm.onAddCustomFieldClickedForSuggest()
        advanceUntilIdle()
        assertThat(vm.suggestion.value.visible).isTrue()

        vm.hideSuggestions()

        assertThat(vm.suggestion.value.visible).isFalse()
        assertThat(vm.suggestion.value.items).isEmpty()
    }

    @Test
    fun bindPackageForSuggestions_blank_doesNotSurfaceSuggestions() = runTest(testDispatcher) {
        // The Activity's TextWatcher will call bindPackage with
        // partial / empty strings while the user is typing. The
        // ViewModel must treat blank as "no package bound" — pressing
        // Add at that moment should not produce a Flow subscription.
        val detected = FakeDetectedFieldRepository().apply {
            put(field("loginEmail", DetectedFieldSource.ResourceId, 100L))
        }
        val vm = newViewModel(detectedRepo = detected)
        vm.bindPackageForSuggestions("")
        vm.addCustomFieldRow()
        vm.onAddCustomFieldClickedForSuggest()
        advanceUntilIdle()

        assertThat(vm.suggestion.value.visible).isFalse()
    }

    @Test
    fun rebindingDifferentPackage_cancelsPreviousSubscription() = runTest(testDispatcher) {
        // After binding to pkg A and showing suggestions, switching to
        // pkg B with no detected_fields must reset the visible state
        // (we don't keep stale pkg A chips on screen).
        val detected = FakeDetectedFieldRepository().apply {
            put(DetectedField("pkg.a", "alpha", DetectedFieldSource.Hint, 100L))
            // pkg.b has no rows
        }
        val vm = newViewModel(detectedRepo = detected)
        vm.bindPackageForSuggestions("pkg.a")
        vm.addCustomFieldRow()
        vm.onAddCustomFieldClickedForSuggest()
        advanceUntilIdle()
        assertThat(vm.suggestion.value.items.map { it.fieldKey }).containsExactly("alpha")

        vm.bindPackageForSuggestions("pkg.b")
        advanceUntilIdle()

        // Rebinding hides the strip; the user needs to click Add again
        // for the new package's suggestions to populate.
        assertThat(vm.suggestion.value.visible).isFalse()
    }

    @Test
    fun suggestions_hiddenWhenCustomFieldsNotEditable() = runTest(testDispatcher) {
        // design.md §8.5: edit mode disables the customField editor;
        // the suggestion strip must also be suppressed even if the
        // package binding is valid.
        val credRepo = FakeCredentialRepository()
        // Pre-populate a credential so the load() path fires the
        // Mode.Edit branch which flips editable = false.
        credRepo.put(
            io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord(
                id = io.github.hitoshiichikawa.keynest.domain.model.CredentialId(0L),
                packageName = pkg,
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
        val id = credRepo.snapshot().single().id.value
        val detected = FakeDetectedFieldRepository().apply {
            put(field("loginEmail", DetectedFieldSource.ResourceId, 100L))
        }
        val vm = newViewModel(repo = credRepo, detectedRepo = detected)
        vm.load(id)
        advanceUntilIdle()
        // load() in edit mode flipped editable=false, so the click
        // sequence below must still produce a Hidden state.
        vm.onAddCustomFieldClickedForSuggest()
        advanceUntilIdle()

        assertThat(vm.customFields.value.editable).isFalse()
        assertThat(vm.suggestion.value.visible).isFalse()
    }

    @Test
    fun onSuggestionClicked_isNoOpWhenNoRows() = runTest(testDispatcher) {
        // Defensive: a chip click that arrives after the user removed
        // the row must not throw and must hide the strip.
        val detected = FakeDetectedFieldRepository().apply {
            put(field("loginEmail", DetectedFieldSource.ResourceId, 100L))
        }
        val vm = newViewModel(detectedRepo = detected)
        vm.bindPackageForSuggestions(pkg)
        vm.addCustomFieldRow()
        vm.onAddCustomFieldClickedForSuggest()
        advanceUntilIdle()
        val item = vm.suggestion.value.items.single()
        // Now remove the row before clicking the chip.
        val rowId = vm.customFields.value.rows.single().rowId
        vm.removeCustomFieldRow(rowId)
        advanceUntilIdle()

        vm.onSuggestionClicked(item)
        advanceUntilIdle()

        assertThat(vm.customFields.value.rows).isEmpty()
        assertThat(vm.suggestion.value.visible).isFalse()
    }

    // ---- helpers --------------------------------------------------------

    private fun field(
        key: String,
        source: DetectedFieldSource,
        ts: Long,
        packageName: String = pkg,
    ) = DetectedField(packageName, key, source, ts)

    private fun newViewModel(
        repo: FakeCredentialRepository = FakeCredentialRepository(),
        detectedRepo: FakeDetectedFieldRepository = FakeDetectedFieldRepository(),
    ): CredentialEditViewModel {
        val sigResolver = mockk<PackageSignatureResolver>().also {
            every { it.resolveSha256(any()) } returns null
        }
        val cipher = StubAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)
        val save = SaveCredentialUseCase(repo, cipher, sigResolver, codec)
        val update = UpdateCredentialUseCase(repo, cipher, sigResolver, codec)
        val delete = DeleteCredentialUseCase(repo)
        val observeRecent = ObserveRecentDetectedFieldsUseCase(detectedRepo)
        return CredentialEditViewModel(repo, save, update, delete, observeRecent, codec, cipher)
    }
}
