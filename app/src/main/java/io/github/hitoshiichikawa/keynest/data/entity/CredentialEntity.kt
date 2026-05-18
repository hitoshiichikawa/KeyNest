package io.github.hitoshiichikawa.keynest.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room schema for stored credentials.
 *
 * Requirements: 1.1, 1.2, 1.4, 2.1, 2.2, 2.3
 *
 * Notes:
 * - The plaintext password is NEVER stored. Only [passwordCiphertext] +
 *   [passwordIv] are persisted; together they round-trip through
 *   [io.github.hitoshiichikawa.keynest.security.AesGcmCipher].
 * - There is intentionally no UNIQUE constraint on `package_name` so that
 *   Req 1.4 (multiple credentials for the same package) holds.
 * - `package_name` has an index because the AutofillService primary query
 *   path is `findByPackage`, which we need to keep fast (NFR 2.1).
 * - `signatureSha256` is nullable so we can record credentials whose target
 *   app was not yet installed at save time (Req 2.2).
 */
@Entity(
    tableName = "credentials",
    indices = [Index(value = ["package_name"])],
)
data class CredentialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "password_ciphertext", typeAffinity = ColumnInfo.BLOB)
    val passwordCiphertext: ByteArray,

    @ColumnInfo(name = "password_iv", typeAffinity = ColumnInfo.BLOB)
    val passwordIv: ByteArray,

    @ColumnInfo(name = "signature_sha256", typeAffinity = ColumnInfo.BLOB)
    val signatureSha256: ByteArray?,

    @ColumnInfo(name = "signature_captured_at")
    val signatureCapturedAt: Long?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    /**
     * Wall-clock time (epoch millis) at which this credential was most
     * recently consumed via the autofill unlock flow. Null = never used.
     *
     * Issue #9 requirements 3.1, 3.2, 3.3, 3.4. The column was added in
     * Room schema v2 via [io.github.hitoshiichikawa.keynest.data.migration.Migration_1_2].
     */
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,

    /**
     * AES-GCM ciphertext of the JSON-encoded `List<CustomField>` for this
     * credential. Issue #66 Phase 1.
     *
     * Empty (zero-length) for rows that existed before Room schema v3
     * (added by [io.github.hitoshiichikawa.keynest.data.migration.Migration_2_3]),
     * which the codec interprets as "no custom fields". Once the row is
     * re-saved through SaveCredentialUseCase / UpdateCredentialUseCase the
     * field is populated with a real ciphertext (always non-empty because
     * the JSON `[]` literal is also encrypted).
     */
    @ColumnInfo(name = "custom_fields_ciphertext", typeAffinity = ColumnInfo.BLOB)
    val customFieldsCiphertext: ByteArray = ByteArray(0),

    /**
     * 12-byte AES-GCM IV bundled with [customFieldsCiphertext]. Empty when
     * the ciphertext is empty (migration default).
     */
    @ColumnInfo(name = "custom_fields_iv", typeAffinity = ColumnInfo.BLOB)
    val customFieldsIv: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CredentialEntity) return false
        return id == other.id &&
            packageName == other.packageName &&
            username == other.username &&
            label == other.label &&
            passwordCiphertext.contentEquals(other.passwordCiphertext) &&
            passwordIv.contentEquals(other.passwordIv) &&
            ((signatureSha256 == null && other.signatureSha256 == null) ||
                (signatureSha256 != null && other.signatureSha256 != null &&
                    signatureSha256.contentEquals(other.signatureSha256))) &&
            signatureCapturedAt == other.signatureCapturedAt &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt &&
            lastUsedAt == other.lastUsedAt &&
            customFieldsCiphertext.contentEquals(other.customFieldsCiphertext) &&
            customFieldsIv.contentEquals(other.customFieldsIv)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + packageName.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + passwordCiphertext.contentHashCode()
        result = 31 * result + passwordIv.contentHashCode()
        result = 31 * result + (signatureSha256?.contentHashCode() ?: 0)
        result = 31 * result + (signatureCapturedAt?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + (lastUsedAt?.hashCode() ?: 0)
        result = 31 * result + customFieldsCiphertext.contentHashCode()
        result = 31 * result + customFieldsIv.contentHashCode()
        return result
    }

    /** Hide encrypted / signature bytes from accidental logging (NFR 1.3 / Req 5.1). */
    override fun toString(): String =
        "CredentialEntity(id=$id, packageName=$packageName, username=$username, label=$label, " +
            "ciphertext=<${passwordCiphertext.size}B>, iv=<${passwordIv.size}B>, " +
            "signatureSha256=${signatureSha256?.let { "<${it.size}B>" }}, " +
            "signatureCapturedAt=$signatureCapturedAt, createdAt=$createdAt, updatedAt=$updatedAt, " +
            "lastUsedAt=$lastUsedAt, customFieldsCiphertext=<${customFieldsCiphertext.size}B>, " +
            "customFieldsIv=<${customFieldsIv.size}B>)"
}
