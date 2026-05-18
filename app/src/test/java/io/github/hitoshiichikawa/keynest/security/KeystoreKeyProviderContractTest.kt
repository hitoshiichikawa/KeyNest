package io.github.hitoshiichikawa.keynest.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * JVM-only contract test for [KeystoreKeyProvider]. Verifies the
 * `open fun deleteKey()` / `hasKey()` methods can be overridden by
 * test doubles, which is what [io.github.hitoshiichikawa.keynest.domain.usecase.
 * ClearVaultUseCaseTest] relies on (it can't drive the real
 * AndroidKeyStore from JVM).
 *
 * The real Keystore round-trip is covered by the instrumentation test
 * [io.github.hitoshiichikawa.keynest.security.KeystoreKeyProviderTest].
 *
 * Issue #10 Req 7.5, 7.7.
 */
class KeystoreKeyProviderContractTest {

    @Test
    fun deleteKey_canBeOverriddenByTestDouble() {
        // Arrange
        var deleteCallCount = 0
        var alive = true
        val provider = object : KeystoreKeyProvider() {
            override fun hasKey(): Boolean = alive
            override fun deleteKey() {
                deleteCallCount += 1
                alive = false
            }
        }

        // Act
        assertThat(provider.hasKey()).isTrue()
        provider.deleteKey()

        // Assert
        assertThat(deleteCallCount).isEqualTo(1)
        assertThat(provider.hasKey()).isFalse()
    }

    @Test
    fun deleteKey_canSimulateKeystoreFailure() {
        // The Danger Zone use case must surface KeyStoreException via
        // ClearVaultFailure.KeystoreAlias. Verify the contract allows a
        // test double to throw from deleteKey() so use-case tests can
        // exercise that failure mode (Req 7.7).
        val provider = object : KeystoreKeyProvider() {
            override fun deleteKey(): Unit = error("simulated KeyStoreException")
        }

        try {
            provider.deleteKey()
            error("expected throw")
        } catch (t: Throwable) {
            assertThat(t.message).isEqualTo("simulated KeyStoreException")
        }
    }
}
