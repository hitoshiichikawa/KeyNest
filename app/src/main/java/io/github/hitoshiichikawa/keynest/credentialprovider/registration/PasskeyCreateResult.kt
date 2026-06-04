package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import io.github.hitoshiichikawa.keynest.domain.model.SavePasskeyRequest

/**
 * Outputs of [PasskeyCreator.create] (Issue #99 / design §3.2).
 *
 * Holds:
 *  - the WebAuthn-compatible `registrationResponseJson` the Activity hands
 *    back through `PendingIntentHandler.setCreateCredentialResponse(...)`;
 *  - the assembled `authenticatorData` / `attestationObject` byte arrays
 *    for cross-checking in tests (req 2.x);
 *  - the [savePasskeyRequest] the Activity passes to
 *    `PasskeyRepository.save(...)` — its `privateKey` field carries the
 *    plaintext PKCS#8 EC P-256 private key that MUST be `fill(0)`-wiped by
 *    the Activity in a `finally` block (NFR 1.3 / req 1.7).
 */
internal data class PasskeyCreateResult(
    /** base64url-without-padding, 43 chars. */
    val credentialId: String,
    /** raw 32-byte SecureRandom value (defensive copy for tests). */
    val credentialIdBytes: ByteArray,
    /** RFC 8152 §13.1 COSE_Key for the EC P-256 public key (alg = -7). */
    val publicKeyCose: ByteArray,
    /** WebAuthn §6.1 authenticatorData (rpIdHash | flags | signCount | attestedCredentialData). */
    val authenticatorData: ByteArray,
    /** WebAuthn §6.5.4 attestationObject (CBOR `fmt`/`attStmt`/`authData`, fmt=none). */
    val attestationObject: ByteArray,
    /** WebAuthn `PublicKeyCredentialJSON` payload for `CreatePublicKeyCredentialResponse(...)`. */
    val registrationResponseJson: String,
    /** Repository-bound DTO whose `privateKey` MUST be wiped by the caller after save. */
    val savePasskeyRequest: SavePasskeyRequest,
)
