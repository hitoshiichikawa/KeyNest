package io.github.hitoshiichikawa.keynest.credentialprovider.registration

/**
 * Inputs the registration ceremony (`PasskeyCreateActivity`) hands to
 * [PasskeyCreator.create] (Issue #99 / design §3.2).
 *
 * Internal-only DTO — kept out of the public surface because Issue #99 is
 * the first consumer and the authentication ceremony (#89 分割案 4) will
 * introduce its own input type with different fields (allowCredentials etc).
 */
internal data class PasskeyCreateInput(
    val rpId: String,
    val rpDisplayName: String?,
    val userHandle: ByteArray,
    val userName: String?,
    val userDisplayName: String?,
    /** True for residentKey = required | preferred, false for discouraged (req 3.x). */
    val isDiscoverable: Boolean,
)
