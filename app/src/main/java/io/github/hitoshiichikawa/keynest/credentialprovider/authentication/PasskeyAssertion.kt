package io.github.hitoshiichikawa.keynest.credentialprovider.authentication

import io.github.hitoshiichikawa.keynest.credentialprovider.registration.AuthenticatorDataBuilder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * WebAuthn Level 2 §6.1 / §6.3.3 に従う認証セレモニーの `authenticatorData`
 * 組み立てと ES256 (P-256) 署名を集約する pure-Kotlin オブジェクト
 * (Issue #100 / parent #89 / design §3.3 / §4.2 / §7).
 *
 * 入力契約:
 *  - [PasskeyAssertionInput.privateKeyPkcs8] は呼び出し側 (`PasskeyAuthActivity`)
 *    が wipe 責務を負う。本関数は内部で `KeyFactory.generatePrivate(...)`
 *    経由で `ECPrivateKey` を復元するが、平文 ByteArray の zero-fill は
 *    Activity 側 `finally` で行う (NFR 1.1)。
 *  - [PasskeyAssertionInput.signCount] は `PasskeyRepository.signWithIncrement`
 *    で得た **新値** (Option A / 決定 3)。`0..0xFFFF_FFFFL` の範囲外なら
 *    [PasskeyAssertionException.Encoding] を投げる。
 *
 * 出力契約:
 *  - [PasskeyAssertionResult.authenticatorData] = `rpIdHash(32) || flags(0x05) ||
 *    signCount(4 big-endian)`. 全長 37 byte 固定 (決定 1 / 決定 2、AT=0 / ED=0).
 *  - [PasskeyAssertionResult.signature] = `Signature.getInstance("SHA256withECDSA").sign()`
 *    の戻り値 (ASN.1 DER, WebAuthn §6.3.3 step 23 要求)。
 *  - 署名対象は `authenticatorData || SHA-256(clientDataJSON)` (`Signature.update`
 *    を 2 段で呼ぶ)。
 *
 * `AuthenticatorDataBuilder.build(..., attestedCredentialData = null)` を再利用し、
 * #99 で既に bytewise 検証済の helper に乗ることで encoding 経路を一本化する
 * (design §4.2 メモ / §11.2 対称性)。
 */
internal object PasskeyAssertion {

    /** UP(0x01) | UV(0x04). AT/ED/BE/BS = 0. 決定 1 / 決定 2. */
    internal const val FLAGS_UP_UV: Byte = 0x05

    /** rpIdHash(32) + flags(1) + signCount(4). 全長 37 byte 固定 (決定 2 / extensions なし). */
    internal const val AUTHENTICATOR_DATA_LENGTH: Int = 37

    /** ES256 = ECDSA P-256 SHA-256 (JCE name, ASN.1 DER 出力 / WebAuthn §6.3.3 step 23). */
    internal const val SIGNATURE_ALGORITHM: String = "SHA256withECDSA"

    /** PKCS#8 → ECPrivateKey 復元時の鍵アルゴ名. */
    internal const val EC_KEY_ALGORITHM: String = "EC"

    /** WebAuthn signCount は unsigned 32-bit. */
    private const val SIGN_COUNT_MAX: Long = 0xFFFF_FFFFL

    fun sign(input: PasskeyAssertionInput): PasskeyAssertionResult {
        val authenticatorData = try {
            require(input.signCount in 0L..SIGN_COUNT_MAX) {
                "signCount must fit in unsigned 32-bit (got ${input.signCount})"
            }
            val rpIdHash = AuthenticatorDataBuilder.rpIdHash(input.rpId)
            AuthenticatorDataBuilder.build(
                rpIdHash = rpIdHash,
                flags = FLAGS_UP_UV,
                signCount = input.signCount.toInt(),
                attestedCredentialData = null,
                extensions = null,
            )
        } catch (t: Throwable) {
            throw PasskeyAssertionException.Encoding(t)
        }

        val signature = try {
            val privateKey = KeyFactory.getInstance(EC_KEY_ALGORITHM)
                .generatePrivate(PKCS8EncodedKeySpec(input.privateKeyPkcs8))
            val clientDataHash = MessageDigest.getInstance("SHA-256")
                .digest(input.clientDataJson.toByteArray(Charsets.UTF_8))
            Signature.getInstance(SIGNATURE_ALGORITHM).run {
                initSign(privateKey)
                update(authenticatorData)
                update(clientDataHash)
                sign()
            }
        } catch (t: Throwable) {
            throw PasskeyAssertionException.SignFailed(t)
        }

        return PasskeyAssertionResult(
            authenticatorData = authenticatorData,
            signature = signature,
        )
    }
}
