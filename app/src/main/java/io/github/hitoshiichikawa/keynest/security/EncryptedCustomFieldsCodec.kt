package io.github.hitoshiichikawa.keynest.security

import io.github.hitoshiichikawa.keynest.domain.model.CustomField
import io.github.hitoshiichikawa.keynest.util.SafeLogger
import java.util.Arrays
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Round-trips `List<CustomField>` through JSON and AES-GCM, reusing the
 * existing [AesGcmCipher] (and therefore the existing Keystore key — see
 * Req 1.4).
 *
 * Issue #66 Phase 1. Design.md §6.1.
 *
 * - [encrypt] always produces a non-empty [EncryptedBlob], even for an
 *   empty list, because the codec serialises `[]` first. Callers therefore
 *   always have a deterministic 12-byte IV + ciphertext pair to persist.
 * - [decrypt] is lenient on the migration boundary: a zero-length BLOB
 *   means "row predates v3 migration, treat as empty" rather than an
 *   error. Anything non-empty is decrypted strictly — JSON parse failures
 *   surface an empty list (fail-open per design.md §5.4) and emit a
 *   `SafeLogger.warn` so the regression is visible without exposing
 *   plaintext.
 * - Intermediate UTF-8 bytes are zero-filled with [Arrays.fill] before
 *   either method returns, matching the wipe pattern used by
 *   [io.github.hitoshiichikawa.keynest.domain.usecase.SaveCredentialUseCase].
 */
class EncryptedCustomFieldsCodec(
    private val cipher: AesGcmCipher,
    private val json: Json = DEFAULT_JSON,
) {

    /**
     * Serialise [customFields] to JSON, encrypt with [AesGcmCipher.encrypt],
     * and return the resulting [EncryptedBlob]. The IV is freshly generated
     * by the cipher on every call.
     */
    fun encrypt(customFields: List<CustomField>): EncryptedBlob {
        val payload = customFields.map { CustomFieldJson(fieldKey = it.fieldKey, value = it.value) }
        val jsonString = json.encodeToString(ListSerializer(CustomFieldJson.serializer()), payload)
        val bytes = jsonString.toByteArray(Charsets.UTF_8)
        return try {
            cipher.encrypt(bytes)
        } finally {
            // The intermediate UTF-8 form holds plaintext value bytes; wipe
            // before returning to minimise heap-resident sensitive bytes
            // (Req 5.1 / NFR 1.4).
            Arrays.fill(bytes, 0.toByte())
        }
    }

    /**
     * Inverse of [encrypt]. Tolerates:
     * - empty [EncryptedBlob.ciphertext] → empty list (migration default
     *   for rows inserted before they were re-saved).
     * - JSON parse failure → empty list + warn log (no plaintext in the
     *   log message; fail-open so username/password fill still works).
     *
     * Decryption failure (AES-GCM auth tag mismatch etc.) is intentionally
     * NOT swallowed here — the caller (UnlockVaultUseCase) routes that to
     * its existing UnlockFailure.Decrypt surface.
     */
    fun decrypt(blob: EncryptedBlob): List<CustomField> {
        if (blob.ciphertext.isEmpty()) {
            // Migration-default row: no payload, no IV, no fields.
            return emptyList()
        }
        val plaintext = cipher.decrypt(blob)
        return try {
            val jsonString = String(plaintext, Charsets.UTF_8)
            val parsed = json.decodeFromString(
                ListSerializer(CustomFieldJson.serializer()),
                jsonString,
            )
            parsed.map { CustomField(fieldKey = it.fieldKey, value = it.value) }
        } catch (ex: SerializationException) {
            // Fail-open: parse failure should not break username/password
            // fill. The cause class name is sufficient; we MUST NOT log the
            // raw JSON because it contains plaintext custom field values.
            SafeLogger.warn(
                tag = TAG,
                message = "customFields JSON parse failed; returning empty list",
                throwable = ex,
            )
            emptyList()
        } catch (ex: IllegalArgumentException) {
            // kotlinx.serialization sometimes wraps the parse error in
            // IllegalArgumentException (e.g. unknown UTF-8 sequences). Same
            // fail-open treatment.
            SafeLogger.warn(
                tag = TAG,
                message = "customFields JSON decode rejected; returning empty list",
                throwable = ex,
            )
            emptyList()
        } finally {
            Arrays.fill(plaintext, 0.toByte())
        }
    }

    companion object {
        private const val TAG = "KeyNest.CustomFields"

        /**
         * Strict-by-default JSON configuration: rejects unknown keys so a
         * future schema change does not silently drop values, and avoids
         * pretty-printing to keep ciphertext compact.
         */
        private val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
            prettyPrint = false
        }
    }
}
