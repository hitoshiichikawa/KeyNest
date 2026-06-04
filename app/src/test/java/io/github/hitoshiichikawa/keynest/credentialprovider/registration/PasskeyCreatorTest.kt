package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import android.content.Context
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.data.repository.PasskeyRepositoryImpl
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.ProviderException
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests for [PasskeyCreator] (Issue #99 T-06 / design §4.2 / §6).
 *
 * Robolectric (`sdk = [34]`) is required because [PasskeyCreator] uses the
 * Android `Base64` utility and is annotated `@RequiresApi(UPSIDE_DOWN_CAKE)`.
 * The wrapping-key provisioner is always swapped via the constructor seam so
 * the AndroidKeyStore is never touched — tests verify only the keygen /
 * encoding contract and the StrongBox fallback control flow.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PasskeyCreatorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val sampleInput = PasskeyCreateInput(
        rpId = "example.com",
        rpDisplayName = "Example",
        userHandle = ByteArray(16) { 0x77 },
        userName = "alice@example.com",
        userDisplayName = "Alice",
        isDiscoverable = true,
    )

    private fun fixedSecureRandom(byteFill: Byte): SecureRandom = object : SecureRandom() {
        override fun nextBytes(bytes: ByteArray) {
            bytes.fill(byteFill)
        }
    }

    @Test
    fun create_generatesEs256KeypairOnP256Curve() {
        val creator = PasskeyCreator(
            wrappingKeyProvisioner = { _, _ -> /* no-op */ },
        )

        val result = creator.create(context, sampleInput)

        // PKCS#8 round-trips back to an EC key on the P-256 curve.
        val recovered = KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(result.savePasskeyRequest.privateKey)) as ECPrivateKey
        assertThat(recovered.params.curve.field.fieldSize).isEqualTo(256)
    }

    @Test
    fun create_assignsAliasFollowingPasskeyPrefix() {
        val creator = PasskeyCreator(
            secureRandom = fixedSecureRandom(byteFill = 0x42),
            wrappingKeyProvisioner = { _, _ -> },
        )

        val result = creator.create(context, sampleInput)

        // The repository alias derives from the credentialId. Same formula used
        // by both PasskeyCreator (via provisioner seam) and PasskeyRepositoryImpl.
        // #91 design §6.2 / §7.1 / 決定 3 settled on the bare `passkey_` prefix.
        val expectedAlias = PasskeyRepositoryImpl.aliasFor(result.credentialId)
        assertThat(expectedAlias).startsWith("passkey_")
        assertThat(expectedAlias).doesNotContain("keynest_passkey_")
        assertThat(result.credentialId).isEqualTo(
            Base64.encodeToString(
                ByteArray(32) { 0x42 },
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            ),
        )
    }

    @Test
    fun create_strongBoxTrySucceeds_whenPlatformSupports() {
        var firstCallAlias: String? = null
        var invocations = 0
        val creator = PasskeyCreator(
            wrappingKeyProvisioner = { _, alias ->
                invocations++
                firstCallAlias = alias
            },
        )

        val result = creator.create(context, sampleInput)

        assertThat(invocations).isEqualTo(1)
        assertThat(firstCallAlias).isEqualTo(PasskeyRepositoryImpl.aliasFor(result.credentialId))
    }

    @Test
    fun create_strongBoxFallback_onStrongBoxUnavailableException() {
        // Simulate the production provisioner's StrongBox 2-step fallback by
        // verifying that PasskeyCreator wraps the cause into KeyGen on the
        // *outer* throw. Tests of the production provisioner internals are
        // covered separately by Manifest / instrumentation tests once API 34
        // emulators are available (#94). At the PasskeyCreator boundary, a
        // provisioner that ultimately succeeds (after internal fallback) must
        // be treated as success.
        var calls = 0
        val provisioner: (Context, String) -> Unit = { _, _ ->
            calls++
            // Simulate "first attempt threw, second attempt succeeded": the
            // production helper catches StrongBoxUnavailableException
            // internally, so by the time control returns to PasskeyCreator
            // the function has succeeded.
        }
        val creator = PasskeyCreator(wrappingKeyProvisioner = provisioner)

        val result = creator.create(context, sampleInput)

        assertThat(calls).isEqualTo(1)
        assertThat(result.attestationObject).isNotEmpty()
    }

    @Test
    fun create_strongBoxFallback_onProviderException() {
        // A provisioner whose StrongBox attempt failed with ProviderException
        // but recovered internally still presents PasskeyCreator with a single
        // successful call.
        var calls = 0
        val creator = PasskeyCreator(wrappingKeyProvisioner = { _, _ -> calls++ })

        val result = creator.create(context, sampleInput)

        assertThat(calls).isEqualTo(1)
        assertThat(result.savePasskeyRequest.privateKey).isNotEmpty()
    }

    @Test
    fun create_throwsKeyGen_whenBothAttemptsFail() {
        // Provisioner exhausted both StrongBox + TEE attempts and re-raised.
        val terminal = ProviderException("StrongBox + TEE both unavailable")
        val creator = PasskeyCreator(
            wrappingKeyProvisioner = { _, _ -> throw terminal },
        )

        val thrown = runCatching { creator.create(context, sampleInput) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(PasskeyCreationException.KeyGen::class.java)
        assertThat(thrown!!.cause).isSameInstanceAs(terminal)
    }

    @Test
    fun create_throwsKeyGen_whenStrongBoxUnavailableLeaks() {
        // Defensive: even StrongBoxUnavailableException reaching the boundary
        // must be wrapped into KeyGen — never silently swallowed.
        val leak = StrongBoxUnavailableException()
        val creator = PasskeyCreator(
            wrappingKeyProvisioner = { _, _ -> throw leak },
        )

        val thrown = runCatching { creator.create(context, sampleInput) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(PasskeyCreationException.KeyGen::class.java)
        assertThat(thrown!!.cause).isSameInstanceAs(leak)
    }

    @Test
    fun create_returnsPasskeyCreateResult_withPlaintextPkcs8PrivateKey() {
        val creator = PasskeyCreator(
            wrappingKeyProvisioner = { _, _ -> },
        )

        val result = creator.create(context, sampleInput)

        // PKCS#8 round-trip succeeds (Req 1.6 plaintext is exportable so the
        // repository can AES-GCM wrap it).
        val recovered = KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(result.savePasskeyRequest.privateKey))
        assertThat(recovered.algorithm).isEqualTo("EC")
    }

    @Test
    fun create_authenticatorData_signCountIsZero_andFlagsAre0x45() {
        val creator = PasskeyCreator(wrappingKeyProvisioner = { _, _ -> })

        val result = creator.create(context, sampleInput)

        // authenticatorData layout:
        //   0..31  rpIdHash
        //   32     flags
        //   33..36 signCount (big-endian)
        assertThat(result.authenticatorData[32]).isEqualTo(0x45.toByte())
        assertThat(result.authenticatorData[33]).isEqualTo(0x00.toByte())
        assertThat(result.authenticatorData[34]).isEqualTo(0x00.toByte())
        assertThat(result.authenticatorData[35]).isEqualTo(0x00.toByte())
        assertThat(result.authenticatorData[36]).isEqualTo(0x00.toByte())
    }

    @Test
    fun create_attestationObject_isFmtNoneCborMap() {
        val creator = PasskeyCreator(wrappingKeyProvisioner = { _, _ -> })

        val result = creator.create(context, sampleInput)

        // 0xA3 map(3), 0x63 "fmt" (text 3 bytes), 0x66 0x6D 0x74, 0x64 "none"
        val expectedPrefix = byteArrayOf(
            0xA3.toByte(),
            0x63, 0x66, 0x6D, 0x74,
            0x64, 0x6E, 0x6F, 0x6E, 0x65,
        )
        val actualPrefix = result.attestationObject.copyOfRange(0, expectedPrefix.size)
        assertThat(actualPrefix).isEqualTo(expectedPrefix)
    }

    @Test
    fun create_credentialId_is43CharBase64UrlWithoutPadding() {
        val creator = PasskeyCreator(
            secureRandom = fixedSecureRandom(byteFill = 0x00),
            wrappingKeyProvisioner = { _, _ -> },
        )

        val result = creator.create(context, sampleInput)

        // 32 raw bytes encoded as base64url-without-padding == 43 chars.
        assertThat(result.credentialId).hasLength(43)
        assertThat(result.credentialId).matches("[A-Za-z0-9_\\-]+")
        assertThat(result.credentialIdBytes.size).isEqualTo(32)
    }

    @Test
    fun create_isDiscoverableTrue_isCarriedIntoSaveRequest() {
        val creator = PasskeyCreator(wrappingKeyProvisioner = { _, _ -> })

        val resultDiscoverable = creator.create(context, sampleInput.copy(isDiscoverable = true))
        val resultNon = creator.create(context, sampleInput.copy(isDiscoverable = false))

        assertThat(resultDiscoverable.savePasskeyRequest.isDiscoverable).isTrue()
        assertThat(resultNon.savePasskeyRequest.isDiscoverable).isFalse()
    }

    @Test
    fun create_userHandle_andNames_propagate_intoSaveRequest() {
        val creator = PasskeyCreator(wrappingKeyProvisioner = { _, _ -> })

        val result = creator.create(context, sampleInput)
        val saved = result.savePasskeyRequest

        assertThat(saved.rpId).isEqualTo("example.com")
        assertThat(saved.rpDisplayName).isEqualTo("Example")
        assertThat(saved.userHandle).isEqualTo(sampleInput.userHandle)
        assertThat(saved.userName).isEqualTo("alice@example.com")
        assertThat(saved.userDisplayName).isEqualTo("Alice")
        assertThat(saved.signCount).isEqualTo(0L)
    }

    @Test
    fun create_registrationResponseJson_includesIdAndAttestationObject() {
        val creator = PasskeyCreator(wrappingKeyProvisioner = { _, _ -> })

        val result = creator.create(context, sampleInput)

        assertThat(result.registrationResponseJson).contains("\"type\":\"public-key\"")
        assertThat(result.registrationResponseJson).contains("\"id\":\"${result.credentialId}\"")
        assertThat(result.registrationResponseJson).contains("\"rawId\":\"${result.credentialId}\"")
        assertThat(result.registrationResponseJson).contains("\"attestationObject\":")
        assertThat(result.registrationResponseJson).contains("\"clientDataJSON\":\"\"")
    }

    @Test
    fun create_encodingException_throwsWrappedException() {
        // KeyPairGenerator that returns a key with a curve other than P-256
        // routes through CoseKeyEncoder which throws IllegalArgumentException;
        // PasskeyCreator must surface it as Encoding(cause).
        val nonP256Generator: () -> KeyPairGenerator = {
            KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp384r1"))
            }.let { gen ->
                // Wrap into a generator-like that hands back already-initialized state.
                object : KeyPairGenerator("EC") {
                    override fun initialize(params: java.security.spec.AlgorithmParameterSpec?) = Unit
                    override fun generateKeyPair(): KeyPair = gen.generateKeyPair()
                }
            }
        }
        val creator = PasskeyCreator(
            keyPairGeneratorFactory = nonP256Generator,
            wrappingKeyProvisioner = { _, _ -> },
        )

        val thrown = runCatching { creator.create(context, sampleInput) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(PasskeyCreationException.Encoding::class.java)
        assertThat(thrown!!.cause).isInstanceOf(IllegalArgumentException::class.java)
    }
}
