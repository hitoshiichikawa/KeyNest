package io.github.hitoshiichikawa.keynest.credentialprovider.authentication

/**
 * Input carrier for [PasskeyAssertion.sign] (Issue #100 / parent #89 /
 * design §3.2).
 *
 * Lifetime contract:
 *  - [privateKeyPkcs8] is the **plaintext** PKCS#8 ES256 private key
 *    obtained from `PasskeyRepository.loadPrivateKey(...)`. The wipe
 *    responsibility lives one layer up at `PasskeyAuthActivity`, which
 *    appends the array to a `wipeQueue` and runs `fill(0)` in its
 *    `finally` block (NFR 1.1). [PasskeyAssertion] does NOT wipe the
 *    array itself because the caller may need it again on retry / log.
 *  - [signCount] is the **new** signCount value returned by
 *    `PasskeyRepository.signWithIncrement { newSignCount -> ... }`
 *    (Option A / design §6.1).
 */
internal data class PasskeyAssertionInput(
    val rpId: String,
    /**
     * SHA-256 of the WebAuthn `clientDataJSON`. Per W3C §7.2 step 19, the
     * signature is over `authenticatorData || clientDataHash`. On Android
     * Credential Manager flows from Chrome / Safari the hash is supplied
     * directly by the OS via `GetPublicKeyCredentialOption.clientDataHash`
     * — we MUST sign over that exact bytes (no re-hashing of `requestJson`,
     * which is the OPTIONS payload, not clientDataJSON).
     */
    val clientDataHash: ByteArray,
    val signCount: Long,
    val privateKeyPkcs8: ByteArray,
) {
    // ByteArray field requires content-based equality so unit tests using
    // structural assertions behave predictably; the data class default
    // would compare by reference.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasskeyAssertionInput) return false
        return rpId == other.rpId &&
            clientDataHash.contentEquals(other.clientDataHash) &&
            signCount == other.signCount &&
            privateKeyPkcs8.contentEquals(other.privateKeyPkcs8)
    }

    override fun hashCode(): Int {
        var result = rpId.hashCode()
        result = 31 * result + clientDataHash.contentHashCode()
        result = 31 * result + signCount.hashCode()
        result = 31 * result + privateKeyPkcs8.contentHashCode()
        return result
    }
}

/**
 * Result of [PasskeyAssertion.sign] (Issue #100 / design §3.2).
 *
 * [authenticatorData] is the 37 byte WebAuthn §6.1 layout
 * (`rpIdHash(32) || flags(1) || signCount(4)`, no attestedCredentialData,
 * no extensions — 決定 1 / 決定 2).
 *
 * [signature] is the ASN.1 DER encoded ES256 signature produced by JCE's
 * `SHA256withECDSA` (WebAuthn §6.3.3 step 23).
 */
internal data class PasskeyAssertionResult(
    val authenticatorData: ByteArray,
    val signature: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasskeyAssertionResult) return false
        return authenticatorData.contentEquals(other.authenticatorData) &&
            signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int =
        31 * authenticatorData.contentHashCode() + signature.contentHashCode()
}

/**
 * Failures raised by [PasskeyAssertion.sign]. Callers
 * (`PasskeyAuthActivity`) map both subtypes to
 * `GetCredentialUnknownException` for the OS (design §9.x).
 */
internal sealed class PasskeyAssertionException(message: String, cause: Throwable?) :
    Exception(message, cause) {

    class Encoding(cause: Throwable) :
        PasskeyAssertionException("authenticatorData encoding failed", cause)

    class SignFailed(cause: Throwable) :
        PasskeyAssertionException("ES256 signature failed", cause)
}
