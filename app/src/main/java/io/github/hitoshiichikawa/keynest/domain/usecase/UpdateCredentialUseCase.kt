package io.github.hitoshiichikawa.keynest.domain.usecase

import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord
import io.github.hitoshiichikawa.keynest.domain.repository.CredentialRepository
import io.github.hitoshiichikawa.keynest.security.AesGcmCipher
import io.github.hitoshiichikawa.keynest.util.PackageSignatureResolver
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * Updates an existing credential.
 *
 * Requirements: 1.5, 2.3
 *
 * Update semantics:
 * - The signing certificate hash is **re-resolved** every time (Req 2.3) so
 *   that if the target app rotated its signing cert, the credential picks
 *   up the new value and continues to match.
 * - If the password is supplied (non-null), it is re-encrypted with a fresh
 *   IV. If the caller passes null, the existing ciphertext / IV are kept.
 *   This makes "edit username only" cheap without leaking the old password.
 *
 * On failure returns [Result.failure] with a typed [UpdateFailure] - no
 * plaintext is in the error path.
 */
class UpdateCredentialUseCase(
    private val repo: CredentialRepository,
    private val cipher: AesGcmCipher,
    private val sigResolver: PackageSignatureResolver,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend operator fun invoke(input: UpdateCredentialInput): Result<Unit> {
        val newPasswordCopy = input.newPassword

        val pkg = input.packageName.trim()
        if (pkg.isBlank()) {
            wipeIfPresent(newPasswordCopy)
            return Result.failure(UpdateFailure.PackageNameBlank)
        }
        if (!PackageNameValidator.isValid(pkg)) {
            wipeIfPresent(newPasswordCopy)
            return Result.failure(UpdateFailure.PackageNameInvalid)
        }
        if (input.username.isBlank()) {
            wipeIfPresent(newPasswordCopy)
            return Result.failure(UpdateFailure.UsernameBlank)
        }
        if (input.label.isBlank()) {
            wipeIfPresent(newPasswordCopy)
            return Result.failure(UpdateFailure.LabelBlank)
        }

        return try {
            val existing = repo.findById(input.id)
                ?: return Result.failure(UpdateFailure.NotFound)

            // Re-resolve signing hash (Req 2.3).
            val sigHash = sigResolver.resolveSha256(pkg)
            val sigCapturedAt = if (sigHash != null) now() else null

            // Optionally re-encrypt the password with a fresh IV.
            val (ciphertext, iv) = if (newPasswordCopy != null) {
                val bytes = encodeUtf8(newPasswordCopy)
                try {
                    val blob = cipher.encrypt(bytes)
                    blob.ciphertext to blob.iv
                } finally {
                    Arrays.fill(bytes, 0.toByte())
                }
            } else {
                existing.passwordCiphertext to existing.passwordIv
            }

            repo.update(
                EncryptedCredentialRecord(
                    id = existing.id,
                    packageName = pkg,
                    username = input.username,
                    label = input.label,
                    passwordCiphertext = ciphertext,
                    passwordIv = iv,
                    signatureSha256 = sigHash,
                    signatureCapturedAt = sigCapturedAt,
                    createdAt = existing.createdAt,
                    updatedAt = now(),
                ),
            )
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(UpdateFailure.Storage(reason = t.javaClass.simpleName))
        } finally {
            wipeIfPresent(newPasswordCopy)
        }
    }

    private fun encodeUtf8(chars: CharArray): ByteArray {
        val byteBuffer: ByteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        val out = ByteArray(byteBuffer.remaining())
        byteBuffer.get(out)
        if (byteBuffer.hasArray()) {
            Arrays.fill(
                byteBuffer.array(),
                byteBuffer.arrayOffset(),
                byteBuffer.arrayOffset() + byteBuffer.limit(),
                0,
            )
        }
        return out
    }

    private fun wipeIfPresent(chars: CharArray?) {
        if (chars != null) Arrays.fill(chars, ' ')
    }
}

data class UpdateCredentialInput(
    val id: CredentialId,
    val packageName: String,
    val username: String,
    val label: String,
    /**
     * If non-null the credential's password is rewritten. Caller passes the
     * CharArray ownership in - the use case zero-fills it on the way out.
     */
    val newPassword: CharArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UpdateCredentialInput) return false
        return id == other.id &&
            packageName == other.packageName &&
            username == other.username &&
            label == other.label &&
            ((newPassword == null && other.newPassword == null) ||
                (newPassword != null && other.newPassword != null &&
                    newPassword.contentEquals(other.newPassword)))
    }

    override fun hashCode(): Int {
        var r = id.hashCode()
        r = 31 * r + packageName.hashCode()
        r = 31 * r + username.hashCode()
        r = 31 * r + label.hashCode()
        r = 31 * r + (newPassword?.contentHashCode() ?: 0)
        return r
    }

    override fun toString(): String =
        "UpdateCredentialInput(id=$id, packageName=$packageName, username=$username, label=$label, " +
            "newPassword=${if (newPassword != null) "<redacted>" else "null"})"
}

sealed class UpdateFailure(message: String) : Exception(message) {
    object PackageNameBlank : UpdateFailure("packageName is required")
    object PackageNameInvalid : UpdateFailure("packageName format is invalid")
    object UsernameBlank : UpdateFailure("username is required")
    object LabelBlank : UpdateFailure("label is required")
    object NotFound : UpdateFailure("credential not found")
    data class Storage(val reason: String) : UpdateFailure("storage error: $reason")
}
