package io.github.hitoshiichikawa.keynest.domain.usecase

import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.PlaintextCredential
import io.github.hitoshiichikawa.keynest.domain.repository.CredentialRepository
import io.github.hitoshiichikawa.keynest.security.AesGcmCipher
import io.github.hitoshiichikawa.keynest.security.EncryptedBlob
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * Decrypts and returns the credential identified by [CredentialId].
 *
 * Requirements: 5.3, 5.5, NFR 1.4
 *
 * Contract:
 * - The plaintext password is returned wrapped in [PlaintextCredential],
 *   which is `AutoCloseable` and zero-fills the buffer on `close()`. Callers
 *   MUST `use { }` it or call `close()` in a finally block.
 * - Intermediate decrypted ByteArrays inside this function are zero-filled
 *   before the function returns (whether successfully or not).
 * - On failure returns [Result.failure] - the error object does not contain
 *   any decrypted bytes (NFR 1.3).
 */
class UnlockVaultUseCase(
    private val repo: CredentialRepository,
    private val cipher: AesGcmCipher,
) {

    suspend operator fun invoke(id: CredentialId): Result<PlaintextCredential> {
        val record = repo.findById(id) ?: return Result.failure(UnlockFailure.NotFound)

        var plaintextBytes: ByteArray? = null
        return try {
            plaintextBytes = cipher.decrypt(EncryptedBlob(iv = record.passwordIv, ciphertext = record.passwordCiphertext))
            val passwordChars = decodeUtf8(plaintextBytes)
            Result.success(
                PlaintextCredential(
                    id = record.id,
                    packageName = record.packageName,
                    username = record.username,
                    label = record.label,
                    password = passwordChars,
                ),
            )
        } catch (t: Throwable) {
            Result.failure(UnlockFailure.Decrypt(reason = t.javaClass.simpleName))
        } finally {
            // Zero-fill the intermediate decrypted bytes.
            plaintextBytes?.let { Arrays.fill(it, 0.toByte()) }
        }
    }

    private fun decodeUtf8(bytes: ByteArray): CharArray {
        val charBuffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes))
        val out = CharArray(charBuffer.remaining())
        charBuffer.get(out)
        if (charBuffer.hasArray()) {
            Arrays.fill(
                charBuffer.array(),
                charBuffer.arrayOffset(),
                charBuffer.arrayOffset() + charBuffer.limit(),
                ' ',
            )
        }
        return out
    }
}

sealed class UnlockFailure(message: String) : Exception(message) {
    object NotFound : UnlockFailure("credential not found")
    data class Decrypt(val reason: String) : UnlockFailure("decrypt failed: $reason")
}
