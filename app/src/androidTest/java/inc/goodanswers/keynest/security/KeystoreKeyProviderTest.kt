package inc.goodanswers.keynest.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/**
 * Round-trip tests for [KeystoreKeyProvider.deleteKey] / [hasKey] against
 * the real AndroidKeyStore.
 *
 * Runs as an instrumentation test (mirroring [AesGcmCipherTest]) because
 * AndroidKeyStore is unavailable on the JVM / Robolectric.
 *
 * Issue #10 Req 7.5, 7.7.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreKeyProviderTest {

    private val testAlias = "keynest_test_delete_alias_v1"

    @Before
    fun clearTestKey() = removeAlias()

    @After
    fun cleanup() = removeAlias()

    private fun removeAlias() {
        val ks = KeyStore.getInstance(KeystoreKeyProvider.ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(testAlias)) ks.deleteEntry(testAlias)
    }

    private fun newProvider() = KeystoreKeyProvider(keyAlias = testAlias)

    @Test
    fun deleteKey_removesAlias_afterGetOrCreateKey() {
        // Arrange
        val provider = newProvider()
        provider.getOrCreateKey()
        assertThat(provider.hasKey()).isTrue()

        // Act
        provider.deleteKey()

        // Assert
        assertThat(provider.hasKey()).isFalse()
    }

    @Test
    fun deleteKey_isIdempotent_whenAliasIsAbsent() {
        // Issue #10 Req 7.7: a Danger Zone retry should not throw if the
        // alias was already removed on the previous attempt.
        val provider = newProvider()
        assertThat(provider.hasKey()).isFalse()

        // Act + Assert: no exception
        provider.deleteKey()
        provider.deleteKey()
        assertThat(provider.hasKey()).isFalse()
    }

    @Test
    fun hasKey_returnsFalse_beforeFirstUse() {
        val provider = newProvider()
        assertThat(provider.hasKey()).isFalse()
    }
}
