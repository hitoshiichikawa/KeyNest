package io.github.hitoshiichikawa.keynest.domain.usecase

import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.autofill.parser.AutofillFieldHeuristics
import io.github.hitoshiichikawa.keynest.data.entity.DetectedFieldSource
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Behaviour of [RecordDetectedFieldsUseCase]. Issue #67 Phase 2
 * (design.md §9.3).
 *
 * - Pure JUnit + in-memory fakes; no Robolectric is needed because the
 *   use case never touches Android types.
 * - The fake [FakeDetectedFieldRepository] is intentionally a fresh
 *   instance per test so `upsertCalls` is easy to assert against.
 */
class RecordDetectedFieldsUseCaseTest {

    @Test
    fun invoke_noOp_whenPackageHasNoCredential() = runTest {
        // Req 3.1 / §4 Q2: writes are gated on findByPackage being
        // non-empty. An unregistered package must produce zero upserts
        // even when descriptors look valid.
        val credRepo = FakeCredentialRepository()
        val detectedRepo = FakeDetectedFieldRepository()
        val useCase = RecordDetectedFieldsUseCase(detectedRepo, credRepo, clock = { 1_000L })

        useCase(
            packageName = "com.example.unregistered",
            descriptors = listOf(
                AutofillFieldHeuristics.FieldDescriptor(
                    autofillHints = listOf("username"),
                    inputType = 0,
                    idEntry = "loginEmail",
                    hint = "User ID",
                    contentDescription = "Email input",
                ),
            ),
        )

        assertThat(detectedRepo.upsertCalls).isEmpty()
    }

    @Test
    fun invoke_upsertsAllSources_whenCredentialExists() = runTest {
        // Each descriptor source produces a separate row. autofillHints
        // is a list so each element produces its own row.
        val credRepo = FakeCredentialRepository().apply { put(sample("com.example.target")) }
        val detectedRepo = FakeDetectedFieldRepository()
        val useCase = RecordDetectedFieldsUseCase(detectedRepo, credRepo, clock = { 42L })

        useCase(
            packageName = "com.example.target",
            descriptors = listOf(
                AutofillFieldHeuristics.FieldDescriptor(
                    autofillHints = listOf("username", "emailAddress"),
                    inputType = 0,
                    idEntry = "loginEmail",
                    hint = "User ID",
                    contentDescription = "Email input",
                ),
            ),
        )

        val saved = detectedRepo.upsertCalls
        assertThat(saved).hasSize(5)
        // Two autofillHints rows preserve the raw values.
        val autofillRows = saved.filter { it.source == DetectedFieldSource.AutofillHints }
        assertThat(autofillRows.map { it.fieldKey })
            .containsExactly("username", "emailAddress")
        // Other sources are one row each, fieldKey is the raw value.
        assertThat(saved.first { it.source == DetectedFieldSource.Hint }.fieldKey)
            .isEqualTo("User ID")
        assertThat(saved.first { it.source == DetectedFieldSource.ResourceId }.fieldKey)
            .isEqualTo("loginEmail")
        assertThat(saved.first { it.source == DetectedFieldSource.ContentDescription }.fieldKey)
            .isEqualTo("Email input")
        // All rows share the package name and the injected clock value.
        assertThat(saved.map { it.packageName }.distinct()).containsExactly("com.example.target")
        assertThat(saved.map { it.lastDetectedAt }.distinct()).containsExactly(42L)
    }

    @Test
    fun invoke_skipsBlankFieldKeys() = runTest {
        // Req 3.5: blanks before AND after normalisation are dropped.
        // We mix: null hint (skipped), whitespace-only contentDescription
        // (skipped before normalize), legitimate idEntry (kept).
        val credRepo = FakeCredentialRepository().apply { put(sample("com.example.target")) }
        val detectedRepo = FakeDetectedFieldRepository()
        val useCase = RecordDetectedFieldsUseCase(detectedRepo, credRepo, clock = { 0L })

        useCase(
            packageName = "com.example.target",
            descriptors = listOf(
                AutofillFieldHeuristics.FieldDescriptor(
                    autofillHints = null,
                    inputType = 0,
                    idEntry = "loginEmail",
                    hint = "",
                    contentDescription = "   \t  ",
                ),
            ),
        )

        // Only the idEntry produced an upsert.
        val saved = detectedRepo.upsertCalls
        assertThat(saved).hasSize(1)
        assertThat(saved.single().source).isEqualTo(DetectedFieldSource.ResourceId)
        assertThat(saved.single().fieldKey).isEqualTo("loginEmail")
    }

    @Test
    fun invoke_skipsAutofillHintsThatNormalizeToBlank() = runTest {
        // Req 3.5: a value made entirely of unicode whitespace
        // normalizes to "" and must be dropped even when present as a
        // non-null autofillHints element.
        val credRepo = FakeCredentialRepository().apply { put(sample("com.example.target")) }
        val detectedRepo = FakeDetectedFieldRepository()
        val useCase = RecordDetectedFieldsUseCase(detectedRepo, credRepo, clock = { 0L })

        useCase(
            packageName = "com.example.target",
            descriptors = listOf(
                AutofillFieldHeuristics.FieldDescriptor(
                    autofillHints = listOf("　　"), // full-width space
                    inputType = 0,
                    idEntry = null,
                    hint = null,
                    contentDescription = null,
                ),
            ),
        )

        assertThat(detectedRepo.upsertCalls).isEmpty()
    }

    @Test
    fun invoke_persistsRawFieldKey_notNormalized() = runTest {
        // design.md §7.1: the persisted fieldKey is the raw value so
        // the suggestion UI can show the developer-authored label.
        val credRepo = FakeCredentialRepository().apply { put(sample("com.example.target")) }
        val detectedRepo = FakeDetectedFieldRepository()
        val useCase = RecordDetectedFieldsUseCase(detectedRepo, credRepo, clock = { 0L })

        useCase(
            packageName = "com.example.target",
            descriptors = listOf(
                AutofillFieldHeuristics.FieldDescriptor(
                    autofillHints = null,
                    inputType = 0,
                    idEntry = "LoginEmail", // mixed case must survive
                    hint = null,
                    contentDescription = null,
                ),
            ),
        )

        assertThat(detectedRepo.upsertCalls.single().fieldKey).isEqualTo("LoginEmail")
    }

    @Test
    fun invoke_typeLevelTextExclusion_isStructural() {
        // §0.1 / requirements Q1: FieldDescriptor does not even expose
        // a `text` property, so the use case cannot accidentally emit
        // a `text`-sourced row. This compile-time check is the
        // strongest guarantee — if Phase 3 ever adds `text` back, this
        // test name should be the first canary surface.
        //
        // We assert presence of the four known properties only;
        // anything else is a structural change that warrants a code
        // review.
        val klass = AutofillFieldHeuristics.FieldDescriptor::class.java
        val fieldNames = klass.declaredFields.map { it.name }.toSet()
        // synthetic fields like Companion may also appear; we check
        // the EXPECTED data-class fields are present and `text` is
        // NOT.
        assertThat(fieldNames).containsAtLeast(
            "autofillHints", "inputType", "idEntry", "hint", "contentDescription",
        )
        assertThat(fieldNames).doesNotContain("text")
    }

    @Test
    fun invoke_clockIsInjectable() = runTest {
        // Lets the autofill service inject System.currentTimeMillis()
        // while tests use a frozen clock. Verified by passing a clock
        // that always returns a sentinel value.
        val credRepo = FakeCredentialRepository().apply { put(sample("com.example.target")) }
        val detectedRepo = FakeDetectedFieldRepository()
        val useCase = RecordDetectedFieldsUseCase(
            detectedRepo,
            credRepo,
            clock = { 9_999_999L },
        )

        useCase(
            packageName = "com.example.target",
            descriptors = listOf(
                AutofillFieldHeuristics.FieldDescriptor(
                    autofillHints = listOf("username"),
                    inputType = 0,
                    idEntry = null,
                    hint = null,
                    contentDescription = null,
                ),
            ),
        )

        assertThat(detectedRepo.upsertCalls.single().lastDetectedAt).isEqualTo(9_999_999L)
    }

    @Test
    fun invoke_handlesEmptyDescriptorList() = runTest {
        // Defensive: an empty descriptor list (no editable views in the
        // structure) must still pass the credential gate and then no-op.
        val credRepo = FakeCredentialRepository().apply { put(sample("com.example.target")) }
        val detectedRepo = FakeDetectedFieldRepository()
        val useCase = RecordDetectedFieldsUseCase(detectedRepo, credRepo, clock = { 0L })

        useCase(packageName = "com.example.target", descriptors = emptyList())

        assertThat(detectedRepo.upsertCalls).isEmpty()
    }

    @Test
    fun invoke_upsertsAcrossMultipleDescriptors() = runTest {
        // Sanity: multiple descriptors each contribute their own rows.
        val credRepo = FakeCredentialRepository().apply { put(sample("com.example.target")) }
        val detectedRepo = FakeDetectedFieldRepository()
        val useCase = RecordDetectedFieldsUseCase(detectedRepo, credRepo, clock = { 0L })

        useCase(
            packageName = "com.example.target",
            descriptors = listOf(
                AutofillFieldHeuristics.FieldDescriptor(
                    autofillHints = listOf("username"),
                    inputType = 0,
                    idEntry = null,
                    hint = null,
                    contentDescription = null,
                ),
                AutofillFieldHeuristics.FieldDescriptor(
                    autofillHints = listOf("password"),
                    inputType = 0,
                    idEntry = null,
                    hint = null,
                    contentDescription = null,
                ),
            ),
        )

        assertThat(detectedRepo.upsertCalls.map { it.fieldKey })
            .containsExactly("username", "password")
    }

    // ---- helpers --------------------------------------------------------

    private fun sample(pkg: String) = EncryptedCredentialRecord(
        id = CredentialId(0L),
        packageName = pkg,
        username = "alice",
        label = "Sample",
        passwordCiphertext = byteArrayOf(1, 2, 3),
        passwordIv = ByteArray(12),
        signatureSha256 = null,
        signatureCapturedAt = null,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
