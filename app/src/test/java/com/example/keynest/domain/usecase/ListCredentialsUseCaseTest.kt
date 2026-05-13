package com.example.keynest.domain.usecase

import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.model.CredentialSortOrder
import com.example.keynest.domain.model.EncryptedCredentialRecord
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Behaviour of [ListCredentialsUseCase]. Covers Req 1.5 (MVP); Issue #9
 * Req 4.1, 4.2, 4.3 (sort variants).
 */
class ListCredentialsUseCaseTest {

    private val repo = FakeCredentialRepository()
    private val useCase = ListCredentialsUseCase(repo)

    @Test
    fun invoke_default_returnsAllCredentials_orderedByUpdatedAtDesc() = runTest {
        // Req 1.5, Req 4.2 (default = UpdatedAtDesc).
        repo.put(blankRec("a", "u1", updatedAt = 1L))
        repo.put(blankRec("b", "u2", updatedAt = 5L))
        repo.put(blankRec("c", "u3", updatedAt = 3L))

        val emitted = useCase().first()

        assertThat(emitted.map { it.username }).containsExactly("u2", "u3", "u1").inOrder()
    }

    @Test
    fun invoke_default_returnsEmpty_whenNoCredentials() = runTest {
        val emitted = useCase().first()
        assertThat(emitted).isEmpty()
    }

    @Test
    fun invoke_withLabelAsc_sortsCaseInsensitive() = runTest {
        // Issue #9 Req 4.1(b).
        repo.put(blankRec("a", "u1", updatedAt = 0L, label = "banana"))
        repo.put(blankRec("b", "u2", updatedAt = 0L, label = "Apple"))
        repo.put(blankRec("c", "u3", updatedAt = 0L, label = "carrot"))

        val emitted = useCase(CredentialSortOrder.LabelAsc).first()

        assertThat(emitted.map { it.label }).containsExactly("Apple", "banana", "carrot").inOrder()
    }

    @Test
    fun invoke_withPackageAsc_sortsCaseInsensitive() = runTest {
        // Issue #9 Req 4.1(c).
        repo.put(blankRec("Banana", "u1", updatedAt = 0L))
        repo.put(blankRec("apple", "u2", updatedAt = 0L))

        val emitted = useCase(CredentialSortOrder.PackageAsc).first()

        assertThat(emitted.map { it.packageName })
            .containsExactly("com.example.apple", "com.example.Banana")
            .inOrder()
    }

    private fun blankRec(
        pkg: String,
        username: String,
        updatedAt: Long,
        label: String = "L-$username",
    ) = EncryptedCredentialRecord(
        id = CredentialId(0L),
        packageName = "com.example.$pkg",
        username = username,
        label = label,
        passwordCiphertext = byteArrayOf(1),
        passwordIv = ByteArray(12),
        signatureSha256 = null,
        signatureCapturedAt = null,
        createdAt = 0L,
        updatedAt = updatedAt,
    )
}
