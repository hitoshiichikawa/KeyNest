package com.example.keynest.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Provides the singleton AES-256-GCM key used to encrypt the credential
 * `password` column.
 *
 * Requirements:
 * - 1.2  - password must never be written to disk in plaintext
 * - NFR 1.1 - AES-GCM with Android Keystore protected key
 * - NFR 1.2 - the raw key bytes must never leave the Keystore TEE
 *
 * Design decisions:
 * - Key alias: `keynest_aead_v1` (versioned so future re-keying is possible)
 * - 256 bit AES, GCM mode, no padding (the only mode AES-GCM supports)
 * - randomizedEncryption = true so the platform refuses to encrypt twice
 *   with the same IV (defense in depth on top of automatic IV generation)
 * - setUserAuthenticationRequired(false) on the key itself. The Vault is
 *   gated by the app-level BiometricPrompt in [com.example.keynest.auth.
 *   BiometricAuthenticator] / [com.example.keynest.autofill.unlock.
 *   AutofillUnlockActivity]. See design.md "Security Considerations" and
 *   "確定事項" - this matches the human-approved design.
 */
open class KeystoreKeyProvider(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val keystoreProvider: String = ANDROID_KEYSTORE,
) {

    /**
     * Returns the AES key associated with [keyAlias]. Creates a new one on the
     * first call. Thread-safe (Keystore.getKey() is itself synchronized inside
     * the platform implementation, and we never expose a stale reference).
     *
     * `open` so that JVM unit tests can plug in a non-Keystore SecretKey
     * (AndroidKeyStore is only available under instrumented / Robolectric
     * test runs).
     */
    open fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(keystoreProvider).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keystoreProvider)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /**
     * Returns true when the AndroidKeyStore currently holds an entry for
     * [keyAlias]. Used by tests / the vault-clear flow to assert that
     * [deleteKey] really removed the entry.
     *
     * Issue #10 Req 7.5, 7.7.
     *
     * `open` so that JVM unit tests can stub the Keystore lookup; the
     * AndroidKeyStore provider is only available under instrumented /
     * Robolectric runs.
     */
    open fun hasKey(): Boolean {
        val keyStore = KeyStore.getInstance(keystoreProvider).apply { load(null) }
        return keyStore.containsAlias(keyAlias)
    }

    /**
     * Removes the AndroidKeyStore entry for [keyAlias] if it exists.
     * Idempotent / silent when the alias is already absent so that
     * Danger Zone "vault clear" retries do not crash on the second call.
     *
     * Issue #10 Req 7.5, 7.7. Callers (ClearVaultUseCase) catch
     * KeyStoreException and surface it as
     * [com.example.keynest.domain.model.ClearVaultFailure.KeystoreAlias].
     */
    open fun deleteKey() {
        val keyStore = KeyStore.getInstance(keystoreProvider).apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
        // Silent no-op when alias is absent (idempotent).
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "keynest_aead_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_SIZE_BITS = 256
    }
}
