package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import io.github.hitoshiichikawa.keynest.data.repository.PasskeyRepositoryImpl
import io.github.hitoshiichikawa.keynest.domain.model.SavePasskeyRequest
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.ProviderException
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyGenerator

/**
 * Core of the PassKey registration ceremony (Issue #99 / parent #89 /
 * design §4.2 / §6).
 *
 * Responsibilities (all pure local — no network IO, NFR 4.x):
 *  - Generate the ES256 (P-256) keypair via the **JCE standard provider**
 *    so the PKCS#8-encoded private key is exportable for AES-GCM wrapping
 *    (design §6.3 selection B). The AndroidKeyStore is therefore used only
 *    for the wrapping key, not the EC key itself.
 *  - Provision the AES-256-GCM **wrapping key** under alias
 *    `passkey_<credentialId>` (#91 決定 3 / design §6.2 / §7.1). StrongBox
 *    is attempted first; `StrongBoxUnavailableException` /
 *    `ProviderException` falls back to non-StrongBox TEE Keystore
 *    (req 1.4 / 1.5).
 *  - Build the COSE_Key public key, `authenticatorData`,
 *    `attestationObject` (`fmt = "none"`), and the WebAuthn
 *    `registrationResponseJson` payload.
 *  - Assemble a [io.github.hitoshiichikawa.keynest.domain.model.SavePasskeyRequest]
 *    carrying the **plaintext** PKCS#8 private key — the caller
 *    (`PasskeyCreateActivity`) must `fill(0)` it after
 *    `PasskeyRepository.save(...)` returns (NFR 1.3).
 *
 * **What this class does NOT do**:
 *  - It does not call `PasskeyRepository.save(...)`. The Activity owns the
 *    repository call so the plaintext-wipe `finally` block stays in one
 *    layer.
 *  - It does not interact with `BiometricPrompt`. The Activity owns the
 *    auth step (design §4.2).
 *
 * Test seams (constructor args):
 *  - `keyPairGeneratorFactory`: substitute the EC `KeyPairGenerator`. Lets
 *    Robolectric tests verify `secp256r1` was requested and inject capture
 *    of the generated KeyPair without an AndroidKeyStore round-trip.
 *  - `wrappingKeyProvisioner`: substitute the StrongBox / Keystore call.
 *    Tests inject a counter to verify the two-step fallback.
 *  - `secureRandom`: deterministic 32-byte vectors for credentialId tests.
 *  - `nowMillisProvider`: deterministic `createdAt`.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class PasskeyCreator(
    private val keyPairGeneratorFactory: () -> KeyPairGenerator = { KeyPairGenerator.getInstance("EC") },
    private val wrappingKeyProvisioner: (Context, String) -> Unit = ::defaultProvisionWrappingKey,
    private val secureRandom: SecureRandom = SecureRandom(),
    private val nowMillisProvider: () -> Long = { System.currentTimeMillis() },
) {

    /**
     * Returns a [PasskeyCreateResult] that the caller may pass — verbatim —
     * to `PasskeyRepository.save(...)`. The returned `savePasskeyRequest`
     * owns a mutable plaintext private key that the caller MUST wipe.
     *
     * @throws PasskeyCreationException.KeyGen on keypair / wrapping-key failure
     * @throws PasskeyCreationException.Encoding on CBOR / COSE encoding failure
     */
    fun create(context: Context, input: PasskeyCreateInput): PasskeyCreateResult {
        val credentialIdBytes = ByteArray(CREDENTIAL_ID_BYTES).also(secureRandom::nextBytes)
        val credentialId = Base64.encodeToString(
            credentialIdBytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val alias = PasskeyRepositoryImpl.aliasFor(credentialId)

        val keyPair: KeyPair = try {
            generateEcKeyPair()
        } catch (e: Throwable) {
            throw PasskeyCreationException.KeyGen(e)
        }
        val publicKey = keyPair.public as? ECPublicKey
            ?: throw PasskeyCreationException.KeyGen(
                IllegalStateException("EC keypair generator returned non-EC public key"),
            )
        val privateKey = keyPair.private as? ECPrivateKey
            ?: throw PasskeyCreationException.KeyGen(
                IllegalStateException("EC keypair generator returned non-EC private key"),
            )

        try {
            wrappingKeyProvisioner(context, alias)
        } catch (e: Throwable) {
            throw PasskeyCreationException.KeyGen(e)
        }

        val publicKeyCose: ByteArray
        val authenticatorData: ByteArray
        val attestationObject: ByteArray
        try {
            publicKeyCose = CoseKeyEncoder.encodeEs256(publicKey)
            val rpIdHash = AuthenticatorDataBuilder.rpIdHash(input.rpId)
            val flags = (AuthenticatorDataBuilder.FLAG_UP.toInt()
                or AuthenticatorDataBuilder.FLAG_UV.toInt()
                or AuthenticatorDataBuilder.FLAG_AT.toInt()).toByte()
            val attestedCredentialData = AuthenticatorDataBuilder.attestedCredentialData(
                aaguid = KeynestAaguid.bytes(),
                credentialId = credentialIdBytes,
                publicKeyCose = publicKeyCose,
            )
            authenticatorData = AuthenticatorDataBuilder.build(
                rpIdHash = rpIdHash,
                flags = flags,
                signCount = INITIAL_SIGN_COUNT,
                attestedCredentialData = attestedCredentialData,
            )
            attestationObject = AttestationObjectBuilder.buildFormatNone(authenticatorData)
        } catch (e: Throwable) {
            throw PasskeyCreationException.Encoding(e)
        }

        val attestationObjectB64 = Base64.encodeToString(
            attestationObject,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val authenticatorDataB64 = Base64.encodeToString(
            authenticatorData,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        // X.509 SubjectPublicKeyInfo DER (Java's getEncoded for EC public key
        // returns SPKI). Chrome's WebAuthn JSON parser requires this in the
        // `publicKey` field as base64url alongside `publicKeyAlgorithm`.
        val publicKeySpkiB64 = Base64.encodeToString(
            publicKey.encoded,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )

        val registrationResponseJson = buildRegistrationResponseJson(
            credentialIdB64 = credentialId,
            attestationObjectB64 = attestationObjectB64,
            authenticatorDataB64 = authenticatorDataB64,
            publicKeySpkiB64 = publicKeySpkiB64,
            publicKeyAlgorithm = COSE_ALG_ES256,
        )

        val pkcs8 = privateKey.encoded ?: throw PasskeyCreationException.KeyGen(
            IllegalStateException(
                "EC private key has no PKCS#8 encoding — provider must not be AndroidKeyStore",
            ),
        )

        val savePasskeyRequest = SavePasskeyRequest(
            credentialId = credentialId,
            rpId = input.rpId,
            rpDisplayName = input.rpDisplayName,
            userHandle = input.userHandle,
            userName = input.userName,
            userDisplayName = input.userDisplayName,
            isDiscoverable = input.isDiscoverable,
            privateKey = pkcs8,
            signCount = INITIAL_SIGN_COUNT.toLong(),
            displayName = input.userDisplayName ?: input.userName,
            createdAt = nowMillisProvider(),
        )

        return PasskeyCreateResult(
            credentialId = credentialId,
            credentialIdBytes = credentialIdBytes.copyOf(),
            publicKeyCose = publicKeyCose,
            authenticatorData = authenticatorData,
            attestationObject = attestationObject,
            registrationResponseJson = registrationResponseJson,
            savePasskeyRequest = savePasskeyRequest,
        )
    }

    private fun generateEcKeyPair(): KeyPair {
        val generator = keyPairGeneratorFactory()
        generator.initialize(ECGenParameterSpec(EC_CURVE_NAME))
        return generator.generateKeyPair()
    }

    /**
     * WebAuthn L3 `RegistrationResponseJSON` serialization
     * (https://www.w3.org/TR/webauthn-3/#dictdef-registrationresponsejson).
     *
     * Modern Chrome on Android validates these fields strictly and rejects
     * the credential with `MojoClassFromJSON failed to convert JSON` if any
     * are missing:
     *  - `response.publicKey` — base64url DER SubjectPublicKeyInfo
     *  - `response.publicKeyAlgorithm` — COSE algorithm identifier (number)
     *  - `response.authenticatorData` — base64url authData (extracted)
     *  - `response.transports` — array of transport hint strings
     *
     * `clientDataJSON` is left as an empty string; the OS framework attaches
     * its own client data from the request side.
     */
    private fun buildRegistrationResponseJson(
        credentialIdB64: String,
        attestationObjectB64: String,
        authenticatorDataB64: String,
        publicKeySpkiB64: String,
        publicKeyAlgorithm: Int,
    ): String {
        val sb = StringBuilder(512)
        sb.append('{')
        sb.append("\"id\":\"").append(escapeJson(credentialIdB64)).append('\"')
        sb.append(",\"rawId\":\"").append(escapeJson(credentialIdB64)).append('\"')
        sb.append(",\"type\":\"public-key\"")
        sb.append(",\"authenticatorAttachment\":\"platform\"")
        sb.append(",\"response\":{")
        sb.append("\"clientDataJSON\":\"\"")
        sb.append(",\"attestationObject\":\"").append(escapeJson(attestationObjectB64)).append('\"')
        sb.append(",\"authenticatorData\":\"").append(escapeJson(authenticatorDataB64)).append('\"')
        sb.append(",\"publicKey\":\"").append(escapeJson(publicKeySpkiB64)).append('\"')
        sb.append(",\"publicKeyAlgorithm\":").append(publicKeyAlgorithm)
        sb.append(",\"transports\":[\"internal\"]")
        sb.append('}')
        sb.append(",\"clientExtensionResults\":{}")
        sb.append('}')
        return sb.toString()
    }

    private fun escapeJson(s: String): String {
        // Base64url alphabet (A-Z a-z 0-9 - _) requires no escaping; keep the
        // helper around in case the credentialId character set ever drifts.
        val needsEscape = s.any { it == '"' || it == '\\' || it < ' ' }
        if (!needsEscape) return s
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    companion object {
        @VisibleForTesting internal const val CREDENTIAL_ID_BYTES: Int = 32
        @VisibleForTesting internal const val EC_CURVE_NAME: String = "secp256r1"
        @VisibleForTesting internal const val INITIAL_SIGN_COUNT: Int = 0
        @VisibleForTesting internal const val WRAPPING_KEY_BITS: Int = 256

        /** COSE Algorithm Identifier for ES256 (RFC 8152). */
        @VisibleForTesting internal const val COSE_ALG_ES256: Int = -7

        /**
         * Provision the AES-256-GCM wrapping key under [alias] in the
         * AndroidKeyStore, attempting StrongBox first and falling back to
         * non-StrongBox TEE (req 1.4 / 1.5 / design §6.4). Idempotent —
         * silently no-ops when the alias already exists (overwrite path).
         */
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @VisibleForTesting
        internal fun defaultProvisionWrappingKey(context: Context, alias: String) {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(alias)) return

            val supportsStrongBox = context.packageManager
                .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

            val baseSpec = {
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(WRAPPING_KEY_BITS)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
            }

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            try {
                generator.init(baseSpec().setIsStrongBoxBacked(supportsStrongBox).build())
                generator.generateKey()
            } catch (e: StrongBoxUnavailableException) {
                generator.init(baseSpec().setIsStrongBoxBacked(false).build())
                generator.generateKey()
            } catch (e: ProviderException) {
                // StrongBox HSM init failures (device-specific) bubble through
                // ProviderException — retry without StrongBox to satisfy req 1.5.
                generator.init(baseSpec().setIsStrongBoxBacked(false).build())
                generator.generateKey()
            }
        }
    }
}

/**
 * Failures raised by [PasskeyCreator.create]. Caller maps both subtypes to
 * `CreateCredentialUnknownException` for the OS (design §9.3).
 */
internal sealed class PasskeyCreationException(message: String, cause: Throwable?) :
    Exception(message, cause) {

    class KeyGen(cause: Throwable) :
        PasskeyCreationException("ES256 keypair generation failed", cause)

    class Encoding(cause: Throwable) :
        PasskeyCreationException("COSE/CBOR encoding failed", cause)
}
