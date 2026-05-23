package io.github.hitoshiichikawa.keynest.ui.list

import io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity

/**
 * Issue #101 (Phase 4 of umbrella #89) — read-only PassKey projection
 * consumed by the UI layer.
 *
 * `PasskeyEntity` (data layer, #91) carries 14 columns including five
 * sensitive fields (`userHandle` / `encryptedPrivateKey` / `privateKeyIv`
 * / `keyAlias` / `signCount`). The credential list UI never needs any of
 * those, so this projection deliberately omits them. The omission is
 * structural: a misbehaving ViewHolder cannot accidentally `.toString()`
 * a sensitive blob because the field simply does not exist on this type
 * (NFR 2.1).
 *
 * The 1st / 2nd / 3rd line of the PassKey row resolve as (requirements
 * R1.3):
 *  - line 1: [displayName] -> [rpDisplayName] -> [rpId]
 *  - line 2: [userDisplayName] -> [userName] -> R.string.credential_list_passkey_unknown_user
 *  - line 3: [rpId] (fixed)
 *
 * `displayName` is the KeyNest-side user-supplied label; v1 always
 * persists `null` because the PassKey individual-management UI (#89
 * sub-plan 6) has not shipped yet. Reserved here so the adapter
 * fallback logic does not need to change when rename lands.
 *
 * Entity → DisplayModel mapping happens exactly once, inside
 * [io.github.hitoshiichikawa.keynest.domain.usecase.ListPasskeysUseCase],
 * so the [PasskeyEntity] type stays out of `ui/list/` entirely.
 */
data class PasskeyDisplayModel(
    val credentialId: String,
    val rpId: String,
    val rpDisplayName: String?,
    val userName: String?,
    val userDisplayName: String?,
    val displayName: String?,
    val isDiscoverable: Boolean,
    val createdAt: Long,
    val lastUsedAt: Long?,
) {
    companion object {
        /**
         * Project [entity] into a UI-safe [PasskeyDisplayModel]. The
         * five sensitive columns are dropped:
         *  - `userHandle` (BLOB, opaque WebAuthn user identifier)
         *  - `encryptedPrivateKey` (AES-GCM ciphertext)
         *  - `privateKeyIv` (12-byte GCM IV)
         *  - `keyAlias` (AndroidKeyStore alias)
         *  - `signCount` (WebAuthn assertion counter)
         *
         * The mapping is `internal` because no UI-layer caller should be
         * inventing a [PasskeyDisplayModel] from anything other than a
         * [PasskeyEntity] — the use case in `domain/usecase/` is the only
         * legitimate factory site.
         */
        internal fun fromEntity(entity: PasskeyEntity): PasskeyDisplayModel =
            PasskeyDisplayModel(
                credentialId = entity.credentialId,
                rpId = entity.rpId,
                rpDisplayName = entity.rpDisplayName,
                userName = entity.userName,
                userDisplayName = entity.userDisplayName,
                displayName = entity.displayName,
                isDiscoverable = entity.isDiscoverable,
                createdAt = entity.createdAt,
                lastUsedAt = entity.lastUsedAt,
            )
    }
}
