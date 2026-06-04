package io.github.hitoshiichikawa.keynest.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room schema for stored PassKeys (WebAuthn / FIDO2 public-key credentials).
 *
 * Issue #91 (parent #89) — Phase 1 data layer for the Android Credential
 * Manager-based PassKey provider. This entity holds the metadata required by
 * registration / authentication ceremonies along with an AES-GCM ciphertext
 * of the ES256 private key. The Keystore wrapping key used to encrypt
 * [encryptedPrivateKey] is identified by [keyAlias], which follows the
 * `passkey_<credentialId>` naming convention (one wrapping key per PassKey,
 * Issue #91 確定済の設計判断 §決定 3).
 *
 * Schema notes:
 * - [credentialId] is the WebAuthn credential identifier (base64url) and is
 *   the primary key. `@PrimaryKey(autoGenerate = false)` because the value is
 *   produced by the registration ceremony, not by SQLite.
 * - [userHandle] is BLOB (Issue #91 決定 1 — opaque byte sequence up to 64
 *   bytes per W3C WebAuthn Level 2 §5.4.3).
 * - `(rpId, userHandle)` carries a UNIQUE index so the same RP cannot store
 *   two PassKeys for the same user handle (Issue #91 決定 2). `rpId` also has
 *   a standalone index for the per-RP lookups in
 *   [io.github.hitoshiichikawa.keynest.data.dao.PasskeyDao].
 * - [encryptedPrivateKey] and [privateKeyIv] are kept as separate columns so
 *   the 12-byte GCM IV is never silently fused into the ciphertext blob
 *   (mirrors the `password_ciphertext` / `password_iv` split in
 *   [CredentialEntity]).
 * - [isDiscoverable] is a Room Boolean (INTEGER 0/1) with DEFAULT 1 so
 *   `Migration_4_5`'s `CREATE TABLE` DEFAULT 1 matches the exported schema.
 * - [signCount] is a Long (defensive widening of WebAuthn's unsigned 32-bit
 *   signature counter) with DEFAULT 0.
 *
 * NFR 2.2: [toString] redacts [userHandle], [encryptedPrivateKey] and
 * [privateKeyIv] to size markers only so logcat never leaks user identifier
 * bytes or ciphertext. The [equals] and [hashCode] overrides use
 * `contentEquals` / `contentHashCode` because `data class` defaults compare
 * `ByteArray` by reference.
 */
@Entity(
    tableName = "passkeys",
    indices = [
        Index(value = ["rpId"]),
        Index(value = ["rpId", "userHandle"], unique = true),
    ],
)
data class PasskeyEntity(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "credentialId")
    val credentialId: String,

    @ColumnInfo(name = "rpId")
    val rpId: String,

    @ColumnInfo(name = "rpDisplayName")
    val rpDisplayName: String?,

    @ColumnInfo(name = "userHandle", typeAffinity = ColumnInfo.BLOB)
    val userHandle: ByteArray,

    @ColumnInfo(name = "userName")
    val userName: String?,

    @ColumnInfo(name = "userDisplayName")
    val userDisplayName: String?,

    @ColumnInfo(name = "isDiscoverable", defaultValue = "1")
    val isDiscoverable: Boolean,

    @ColumnInfo(name = "encryptedPrivateKey", typeAffinity = ColumnInfo.BLOB)
    val encryptedPrivateKey: ByteArray,

    @ColumnInfo(name = "privateKeyIv", typeAffinity = ColumnInfo.BLOB)
    val privateKeyIv: ByteArray,

    @ColumnInfo(name = "keyAlias")
    val keyAlias: String,

    @ColumnInfo(name = "signCount", defaultValue = "0")
    val signCount: Long,

    @ColumnInfo(name = "displayName")
    val displayName: String?,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long,

    @ColumnInfo(name = "lastUsedAt")
    val lastUsedAt: Long?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasskeyEntity) return false
        return credentialId == other.credentialId &&
            rpId == other.rpId &&
            rpDisplayName == other.rpDisplayName &&
            userHandle.contentEquals(other.userHandle) &&
            userName == other.userName &&
            userDisplayName == other.userDisplayName &&
            isDiscoverable == other.isDiscoverable &&
            encryptedPrivateKey.contentEquals(other.encryptedPrivateKey) &&
            privateKeyIv.contentEquals(other.privateKeyIv) &&
            keyAlias == other.keyAlias &&
            signCount == other.signCount &&
            displayName == other.displayName &&
            createdAt == other.createdAt &&
            lastUsedAt == other.lastUsedAt
    }

    override fun hashCode(): Int {
        var result = credentialId.hashCode()
        result = 31 * result + rpId.hashCode()
        result = 31 * result + (rpDisplayName?.hashCode() ?: 0)
        result = 31 * result + userHandle.contentHashCode()
        result = 31 * result + (userName?.hashCode() ?: 0)
        result = 31 * result + (userDisplayName?.hashCode() ?: 0)
        result = 31 * result + isDiscoverable.hashCode()
        result = 31 * result + encryptedPrivateKey.contentHashCode()
        result = 31 * result + privateKeyIv.contentHashCode()
        result = 31 * result + keyAlias.hashCode()
        result = 31 * result + signCount.hashCode()
        result = 31 * result + (displayName?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (lastUsedAt?.hashCode() ?: 0)
        return result
    }

    /**
     * Hide opaque user handle bytes and encrypted blobs from accidental
     * logging (NFR 2.2). Only size markers are emitted for [userHandle],
     * [encryptedPrivateKey] and [privateKeyIv].
     */
    override fun toString(): String =
        "PasskeyEntity(credentialId=$credentialId, rpId=$rpId, " +
            "rpDisplayName=$rpDisplayName, " +
            "userHandle=ByteArray(size=${userHandle.size}), " +
            "userName=$userName, userDisplayName=$userDisplayName, " +
            "isDiscoverable=$isDiscoverable, " +
            "encryptedPrivateKey=ByteArray(size=${encryptedPrivateKey.size}), " +
            "privateKeyIv=ByteArray(size=${privateKeyIv.size}), " +
            "keyAlias=$keyAlias, signCount=$signCount, " +
            "displayName=$displayName, createdAt=$createdAt, " +
            "lastUsedAt=$lastUsedAt)"
}
