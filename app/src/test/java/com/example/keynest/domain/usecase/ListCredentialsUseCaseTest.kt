package com.example.keynest.domain.usecase

import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.model.EncryptedCredentialRecord
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Behaviour of [ListCredentialsUseCase]. Covers Req 1.5. */
class ListCredentialsUseCaseTest {

    private val repo = FakeCredentialRepository()
    private val useCase = ListCredentialsUseCase(repo)

    @Test
    fun invoke_returnsAllCredentials_orderedByUpdatedAtDesc() = runTest {
        repo.put(blankRec("a", "u1", updatedAt = 1L))
        repo.put(blankRec("b", "u2", updatedAt = 5L))
        repo.put(blankRec("c", "u3", updatedAt = 3L))

        val emitted = useCase().first()

        assertThat(emitted.map { it.username }).containsExactly("u2", "u3", "u1").inOrder()
    }

    @Test
    fun invoke_returnsEmpty_whenNoCredentials() = runTest {
        val emitted = useCase().first()
        assertThat(emitted).isEmpty()
    }

    private fun blankRec(pkg: String, username: String, updatedAt: Long) = EncryptedCredentialRecord(
        id = CredentialId(0L),
        packageName = "com.example.$pkg",
        username = username,
        label = "L-$username",
        passwordCiphertext = byteArrayOf(1),
        passwordIv = ByteArray(12),
        signatureSha256 = null,
        signatureCapturedAt = null,
        createdAt = 0L,
        updatedAt = updatedAt,
    )
}
