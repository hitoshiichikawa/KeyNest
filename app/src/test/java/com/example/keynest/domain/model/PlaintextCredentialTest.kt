package com.example.keynest.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Behaviour around [PlaintextCredential.close] zero-fill semantics.
 * Backs Req 5.5 (do not retain decrypted credential in memory) and NFR 1.4
 * (plain password must not survive once the credential is dismissed).
 */
class PlaintextCredentialTest {

    @Test
    fun close_zeroFillsPasswordBuffer() {
        // Arrange
        val pwd = "p@ssw0rd".toCharArray()
        val original = pwd.copyOf()
        val cred = PlaintextCredential(
            id = CredentialId(1L),
            packageName = "com.example",
            username = "alice",
            label = "Example",
            password = pwd,
        )
        assertThat(cred.isClosed).isFalse()
        // pre-condition: buffer still contains real data
        assertThat(pwd).isEqualTo(original)

        // Act
        cred.close()

        // Assert
        assertThat(cred.isClosed).isTrue()
        for (c in pwd) {
            assertThat(c).isEqualTo(' ')
        }
        // Reference held by callers is now scrubbed.
        assertThat(String(pwd)).doesNotContain("p@")
    }

    @Test
    fun close_isIdempotent() {
        val pwd = "abc".toCharArray()
        val cred = newCred(pwd)

        cred.close()
        cred.close() // second call must not throw

        assertThat(cred.isClosed).isTrue()
        assertThat(pwd).isEqualTo("   ".toCharArray())
    }

    @Test
    fun toString_redactsPassword() {
        val cred = newCred("p@ssw0rd".toCharArray())
        val rendered = cred.toString()
        assertThat(rendered).doesNotContain("p@ssw0rd")
        assertThat(rendered).contains("<redacted")
    }

    @Test
    fun close_onEmptyPassword_doesNotThrow() {
        val cred = newCred(charArrayOf())
        cred.close()
        assertThat(cred.isClosed).isTrue()
    }

    private fun newCred(password: CharArray) = PlaintextCredential(
        id = CredentialId(1L),
        packageName = "com.example",
        username = "alice",
        label = "Example",
        password = password,
    )
}
