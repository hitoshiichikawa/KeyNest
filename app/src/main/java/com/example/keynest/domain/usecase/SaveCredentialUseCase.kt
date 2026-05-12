package com.example.keynest.domain.usecase

import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.model.EncryptedCredentialRecord
import com.example.keynest.domain.repository.CredentialRepository
import com.example.keynest.security.AesGcmCipher
import com.example.keynest.util.PackageSignatureResolver
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * Persists a new credential.
 *
 * Flow (Req 1.1, 1.2, 1.3, 1.4, 2.1, 2.2):
 *   1. Validate inputs (Req 1.3 - blank or malformed packageName must fail).
 *   2. Resolve current signing hash via PackageManager (Req 2.1 / 2.2).
 *   3. Encrypt the password with AES-GCM (Req 1.2, NFR 1.1).
 *   4. Insert the record into the repository.
 *
 * The plaintext password CharArray is zero-filled before this function
 * returns so that even if the caller forgets, we do not leave it around in
 * the JVM heap (NFR 1.3 / Req 5.5).
 *
 * On failure the returned [Result] carries a typed [SaveFailure]. The
 * failure object never includes the password (NFR 1.3).
 */
class SaveCredentialUseCase(
    private val repo: CredentialRepository,
    private val cipher: AesGcmCipher,
    private val sigResolver: PackageSignatureResolver,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend operator fun invoke(input: NewCredentialInput): Result<CredentialId> {
        // 1) Validate. We deliberately fail fast BEFORE touching the cipher so
        //    that the password bytes spend as little time as possible in the
        //    encrypted form's lifecycle.
        val validation = validate(input)
        if (validation != null) {
            wipe(input.password)
            return Result.failure(validation)
        }

        return try {
            // 2) Resolve current signing hash.
            val sigHash = sigResolver.resolveSha256(input.packageName)
            val sigCapturedAt = if (sigHash != null) now() else null

            // 3) Encrypt the password.
            val passwordBytes = encodeUtf8(input.password)
            val blob = try {
                cipher.encrypt(passwordBytes)
            } finally {
                // The intermediate UTF-8 byte form is also sensitive. Wipe it
                // before any further work / exception can let it leak.
                Arrays.fill(passwordBytes, 0.toByte())
            }

            // 4) Save.
            val timestamp = now()
            val id = repo.save(
                EncryptedCredentialRecord(
                    id = CredentialId(0L),
                    packageName = input.packageName,
                    username = input.username,
                    label = input.label,
                    passwordCiphertext = blob.ciphertext,
                    passwordIv = blob.iv,
                    signatureSha256 = sigHash,
                    signatureCapturedAt = sigCapturedAt,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                ),
            )
            Result.success(id)
        } catch (t: Throwable) {
            // Wrap to make sure no sensitive payload appears in the message.
            Result.failure(SaveFailure.Storage(reason = t.javaClass.simpleName))
        } finally {
            wipe(input.password)
        }
    }

    private fun validate(input: NewCredentialInput): SaveFailure? {
        if (input.packageName.isBlank()) return SaveFailure.PackageNameBlank
        if (!PackageNameValidator.isValid(input.packageName)) return SaveFailure.PackageNameInvalid
        if (input.username.isBlank()) return SaveFailure.UsernameBlank
        if (input.password.isEmpty()) return SaveFailure.PasswordBlank
        if (input.label.isBlank()) return SaveFailure.LabelBlank
        return null
    }

    private fun encodeUtf8(chars: CharArray): ByteArray {
        // Encode WITHOUT going through String to avoid a long-lived String in
        // the JVM intern table / GC pool.
        val byteBuffer: ByteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        val out = ByteArray(byteBuffer.remaining())
        byteBuffer.get(out)
        // Zero the temporary ByteBuffer backing array if accessible.
        if (byteBuffer.hasArray()) {
            Arrays.fill(byteBuffer.array(), byteBuffer.arrayOffset(), byteBuffer.arrayOffset() + byteBuffer.limit(), 0)
        }
        return out
    }

    private fun wipe(chars: CharArray) = Arrays.fill(chars, ' ')
}

/**
 * Input DTO. [password] ownership transfers to [SaveCredentialUseCase] which
 * zero-fills it before returning.
 */
data class NewCredentialInput(
    val packageName: String,
    val username: String,
    val password: CharArray,
    val label: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NewCredentialInput) return false
        return packageName == other.packageName &&
            username == other.username &&
            password.contentEquals(other.password) &&
            label == other.label
    }

    override fun hashCode(): Int {
        var r = packageName.hashCode()
        r = 31 * r + username.hashCode()
        r = 31 * r + password.contentHashCode()
        r = 31 * r + label.hashCode()
        return r
    }

    override fun toString(): String =
        "NewCredentialInput(packageName=$packageName, username=$username, label=$label, password=<redacted>)"
}

/** Sealed error surface that never carries plaintext. NFR 1.3. */
sealed class SaveFailure(message: String) : Exception(message) {
    object PackageNameBlank : SaveFailure("packageName is required")
    object PackageNameInvalid : SaveFailure("packageName format is invalid")
    object UsernameBlank : SaveFailure("username is required")
    object PasswordBlank : SaveFailure("password is required")
    object LabelBlank : SaveFailure("label is required")
    data class Storage(val reason: String) : SaveFailure("storage error: $reason")
}
