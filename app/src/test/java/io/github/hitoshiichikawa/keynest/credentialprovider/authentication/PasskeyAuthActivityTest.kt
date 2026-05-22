package io.github.hitoshiichikawa.keynest.credentialprovider.authentication

import android.content.Intent
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.provider.ProviderGetCredentialRequest
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.auth.AuthResult
import io.github.hitoshiichikawa.keynest.auth.BiometricAuthenticator
import io.github.hitoshiichikawa.keynest.data.KeyNestDatabase
import io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity
import io.github.hitoshiichikawa.keynest.data.repository.PasskeyRepositoryImpl
import io.github.hitoshiichikawa.keynest.di.ServiceLocator
import io.github.hitoshiichikawa.keynest.security.AesGcmCipher
import io.github.hitoshiichikawa.keynest.security.EncryptedBlob
import io.github.hitoshiichikawa.keynest.security.KeystoreKeyProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Robolectric end-to-end tests for [PasskeyAuthActivity.runAuthenticationFlow]
 * (Issue #100 T-06 / design §5.2 / §9.3).
 *
 * Each test launches the Activity via [Robolectric.buildActivity] (without
 * calling `create()` because the production `onCreate` invokes
 * `PendingIntentHandler.retrieveProviderGetCredentialRequest`, which has no
 * Robolectric shadow and would otherwise crash), installs test seams
 * (`biometricAuthenticatorFactory` / `passkeyRepositoryProvider`), and
 * calls `runAuthenticationFlow(...)` directly. `PendingIntentHandler` is
 * stubbed via `mockkStatic` so we can observe what was written to the
 * result intent. `PasskeyAssertion` is stubbed via `mockkObject` so we
 * can inject sign failures without spending real crypto.
 *
 * Covers:
 *  - Succeeded → assertion completes, response written, signCount + 1 (R3.3 / R4.4)
 *  - Cancelled → CancellationException written, signCount unchanged (R2.3)
 *  - Failed → UnknownException written (R2.3)
 *  - Unavailable → UnknownException written (R2.3)
 *  - PasskeyAssertion.sign throws → rollback, UnknownException (R3.4 / R4.5)
 *  - loadPrivateKey throws AEADBadTagException → rollback, UnknownException (R3.4 / R4.5)
 *  - 2 successful runs → signCount + 2 (R4.4)
 *  - plaintext PKCS#8 wiped in finally even on failure (NFR 1.1)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PasskeyAuthActivityTest {

    private lateinit var roomDb: KeyNestDatabase
    private lateinit var repository: PasskeyRepositoryImpl
    private val cipher = mockk<AesGcmCipher>()
    private val keyProvider = mockk<KeystoreKeyProvider>(relaxed = true)
    private val keyStore = mockk<KeyStore>(relaxed = true)

    private lateinit var testPlaintext: ByteArray

    private lateinit var controller: ActivityController<PasskeyAuthActivity>
    private lateinit var activity: PasskeyAuthActivity

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // Real in-memory Room — required so signWithIncrement's
        // withTransaction has a live database to roll back against.
        roomDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KeyNestDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        // Generate a real P-256 PKCS#8 so PasskeyAssertion.sign (when not
        // mocked) succeeds on the happy path. Cipher returns the same bytes
        // each call so we can observe wipe on the SAME ByteArray instance.
        val gen = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }
        val realPkcs8 = gen.generateKeyPair().private.encoded!!
        // We hand out a fresh copy per call so the wipe assertion in the
        // "even on failure" test can inspect a deterministic buffer.
        every { cipher.decrypt(any()) } answers {
            realPkcs8.copyOf().also { testPlaintext = it }
        }

        repository = PasskeyRepositoryImpl(
            dao = roomDb.passkeyDao(),
            database = roomDb,
            keyProviderFactory = { keyProvider },
            cipherFactory = { cipher },
            keyStoreLoader = { keyStore },
            nowMillisProvider = { 1_700_000_000_000L },
        )

        // Seed one row for credentialId "cred-1" so the Activity can find it.
        runBlocking {
            roomDb.passkeyDao().insert(sampleEntity("cred-1", signCount = 0L))
        }

        // ServiceLocator.passkeyRepository is consulted by the production
        // Activity factory path. Tests use passkeyRepositoryProvider seam
        // but still stub ServiceLocator just in case.
        mockkObject(ServiceLocator)
        every { ServiceLocator.initialize(any()) } returns Unit
        every { ServiceLocator.passkeyRepository } returns repository

        // Stub PendingIntentHandler so we can observe what was written.
        // mockkObject targets the Companion instance directly so the
        // recording phase does not invoke the real static body (which
        // would NPE on the partially-built GetCredentialResponse argument
        // captured by `any()`).
        mockkObject(PendingIntentHandler.Companion)
        every { PendingIntentHandler.setGetCredentialResponse(any(), any()) } just Runs
        every { PendingIntentHandler.setGetCredentialException(any(), any()) } just Runs

        // Build the Activity but pre-stub retrieveProviderGetCredentialRequest
        // and create() it so lifecycleScope is wired and ready to launch
        // coroutines. The production onCreate path finishes early (null
        // request → finishWithException → finish) so authJob is not set —
        // we still drive runAuthenticationFlow directly per test for full
        // coverage of the success/failure branches.
        every {
            PendingIntentHandler.retrieveProviderGetCredentialRequest(any())
        } returns null
        controller = Robolectric.buildActivity(PasskeyAuthActivity::class.java)
            .create()
        activity = controller.get()
        activity.passkeyRepositoryProvider = { repository }

        // The production onCreate path called finishWithException once
        // because retrieveProviderGetCredentialRequest was stubbed to
        // return null (we needed to skip the production flow so the test
        // can drive runAuthenticationFlow directly). Clear the recorded
        // PendingIntentHandler invocations so per-test verify counts start
        // from a clean slate; re-establish the `just Runs` stubs so the
        // subsequent body calls do not invoke the real bodies.
        clearMocks(
            PendingIntentHandler.Companion,
            answers = false,
            recordedCalls = true,
            childMocks = false,
            verificationMarks = true,
            exclusionRules = false,
        )
        every { PendingIntentHandler.setGetCredentialResponse(any(), any()) } just Runs
        every { PendingIntentHandler.setGetCredentialException(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        // Avoid controller.destroy() — the production onCreate already
        // called finish() once it saw the null providerRequest, so the
        // FragmentManager is already torn down and a second
        // dispatchDestroy throws IllegalStateException. The Activity is
        // GC'd at the end of the test method anyway.
        unmockkObject(ServiceLocator)
        unmockkObject(PendingIntentHandler.Companion)
        if (::roomDb.isInitialized) roomDb.close()
        Dispatchers.resetMain()
    }

    @Test
    fun runAuthenticationFlow_biometricSucceeded_completesAssertion_andCommitsSignCount() = runBlocking {
        activity.biometricAuthenticatorFactory = fakeAuthenticator(AuthResult.Succeeded)
        val responseSlot = slot<androidx.credentials.GetCredentialResponse>()

        activity.runAuthenticationFlow(
            credentialId = "cred-1",
            rpId = "example.com",
            clientDataJson = """{"type":"webauthn.get","challenge":"x","origin":"https://example.com"}""",
            providerRequest = mockk(),
        ).join()

        verify(exactly = 1) {
            PendingIntentHandler.setGetCredentialResponse(any(), capture(responseSlot))
        }
        verify(exactly = 0) {
            PendingIntentHandler.setGetCredentialException(any(), any())
        }
        // DB signCount is now 1 (was 0).
        assertThat(roomDb.passkeyDao().findByCredentialId("cred-1")?.signCount).isEqualTo(1L)
    }

    @Test
    fun runAuthenticationFlow_biometricCancelled_returnsCancellationException_andLeavesSignCountUnchanged() =
        runBlocking {
            activity.biometricAuthenticatorFactory = fakeAuthenticator(AuthResult.Cancelled)
            val exSlot = slot<GetCredentialException>()

            activity.runAuthenticationFlow(
                credentialId = "cred-1",
                rpId = "example.com",
                clientDataJson = """{"type":"webauthn.get","challenge":"x"}""",
                providerRequest = mockk(),
            ).join()

            verify(exactly = 1) {
                PendingIntentHandler.setGetCredentialException(any(), capture(exSlot))
            }
            verify(exactly = 0) {
                PendingIntentHandler.setGetCredentialResponse(any(), any())
            }
            assertThat(exSlot.captured).isInstanceOf(GetCredentialCancellationException::class.java)
            // signWithIncrement was never called so the DB stays at 0.
            assertThat(roomDb.passkeyDao().findByCredentialId("cred-1")?.signCount).isEqualTo(0L)
        }

    @Test
    fun runAuthenticationFlow_biometricFailed_returnsUnknownException() = runBlocking {
        activity.biometricAuthenticatorFactory = fakeAuthenticator(AuthResult.Failed(7, "fp lockout"))
        val exSlot = slot<GetCredentialException>()

        activity.runAuthenticationFlow(
            credentialId = "cred-1",
            rpId = "example.com",
            clientDataJson = "{}",
            providerRequest = mockk(),
        ).join()

        verify(exactly = 1) {
            PendingIntentHandler.setGetCredentialException(any(), capture(exSlot))
        }
        assertThat(exSlot.captured).isInstanceOf(GetCredentialUnknownException::class.java)
        assertThat(roomDb.passkeyDao().findByCredentialId("cred-1")?.signCount).isEqualTo(0L)
    }

    @Test
    fun runAuthenticationFlow_biometricUnavailable_returnsUnknownException() = runBlocking {
        activity.biometricAuthenticatorFactory = fakeAuthenticator(
            AuthResult.Unavailable(BiometricAuthenticator.Availability.NotEnrolled),
        )
        val exSlot = slot<GetCredentialException>()

        activity.runAuthenticationFlow(
            credentialId = "cred-1",
            rpId = "example.com",
            clientDataJson = "{}",
            providerRequest = mockk(),
        ).join()

        verify(exactly = 1) {
            PendingIntentHandler.setGetCredentialException(any(), capture(exSlot))
        }
        assertThat(exSlot.captured).isInstanceOf(GetCredentialUnknownException::class.java)
        assertThat(roomDb.passkeyDao().findByCredentialId("cred-1")?.signCount).isEqualTo(0L)
    }

    @Test
    fun runAuthenticationFlow_signFailure_rollsBackSignCount_andReturnsUnknownException() = runBlocking {
        activity.biometricAuthenticatorFactory = fakeAuthenticator(AuthResult.Succeeded)
        mockkObject(PasskeyAssertion)
        every { PasskeyAssertion.sign(any()) } throws
            PasskeyAssertionException.SignFailed(RuntimeException("simulated"))
        val exSlot = slot<GetCredentialException>()

        try {
            activity.runAuthenticationFlow(
                credentialId = "cred-1",
                rpId = "example.com",
                clientDataJson = "{}",
                providerRequest = mockk(),
            ).join()

            verify(exactly = 1) {
                PendingIntentHandler.setGetCredentialException(any(), capture(exSlot))
            }
            verify(exactly = 0) {
                PendingIntentHandler.setGetCredentialResponse(any(), any())
            }
            assertThat(exSlot.captured).isInstanceOf(GetCredentialUnknownException::class.java)
            // Rollback: signCount must NOT have been bumped.
            assertThat(roomDb.passkeyDao().findByCredentialId("cred-1")?.signCount).isEqualTo(0L)
        } finally {
            unmockkObject(PasskeyAssertion)
        }
    }

    @Test
    fun runAuthenticationFlow_decryptFailure_rollsBackSignCount_andReturnsUnknownException() = runBlocking {
        activity.biometricAuthenticatorFactory = fakeAuthenticator(AuthResult.Succeeded)
        every { cipher.decrypt(any()) } throws AEADBadTagException("tampered")
        val exSlot = slot<GetCredentialException>()

        activity.runAuthenticationFlow(
            credentialId = "cred-1",
            rpId = "example.com",
            clientDataJson = "{}",
            providerRequest = mockk(),
        ).join()

        verify(exactly = 1) {
            PendingIntentHandler.setGetCredentialException(any(), capture(exSlot))
        }
        verify(exactly = 0) {
            PendingIntentHandler.setGetCredentialResponse(any(), any())
        }
        assertThat(exSlot.captured).isInstanceOf(GetCredentialUnknownException::class.java)
        // Rollback: signCount must NOT have been bumped.
        assertThat(roomDb.passkeyDao().findByCredentialId("cred-1")?.signCount).isEqualTo(0L)
    }

    @Test
    fun runAuthenticationFlow_calledTwice_incrementsSignCountByTwo() = runBlocking {
        activity.biometricAuthenticatorFactory = fakeAuthenticator(AuthResult.Succeeded)

        activity.runAuthenticationFlow(
            credentialId = "cred-1",
            rpId = "example.com",
            clientDataJson = "{}",
            providerRequest = mockk(),
        ).join()
        activity.runAuthenticationFlow(
            credentialId = "cred-1",
            rpId = "example.com",
            clientDataJson = "{}",
            providerRequest = mockk(),
        ).join()

        assertThat(roomDb.passkeyDao().findByCredentialId("cred-1")?.signCount).isEqualTo(2L)
    }

    @Test
    fun runAuthenticationFlow_wipesPlaintextPrivateKey_evenOnFailure() = runBlocking {
        activity.biometricAuthenticatorFactory = fakeAuthenticator(AuthResult.Succeeded)
        mockkObject(PasskeyAssertion)
        every { PasskeyAssertion.sign(any()) } throws
            PasskeyAssertionException.SignFailed(RuntimeException("simulated"))

        try {
            activity.runAuthenticationFlow(
                credentialId = "cred-1",
                rpId = "example.com",
                clientDataJson = "{}",
                providerRequest = mockk(),
            ).join()

            // The plaintext PKCS#8 that was decrypted must have been
            // zero-filled by the finally block.
            assertThat(testPlaintext).isNotEmpty()
            assertThat(testPlaintext.all { it == 0.toByte() }).isTrue()
        } finally {
            unmockkObject(PasskeyAssertion)
        }
    }

    // ---- helpers --------------------------------------------------------

    private fun fakeAuthenticator(
        result: AuthResult,
    ): (androidx.fragment.app.FragmentActivity) -> BiometricAuthenticator = {
        val fake = mockk<BiometricAuthenticator>()
        coEvery { fake.authenticate(any(), any()) } returns result
        fake
    }

    @Suppress("UNUSED_PARAMETER")
    private fun stubProviderRequest(): ProviderGetCredentialRequest = mockk()

    private fun sampleEntity(credentialId: String, signCount: Long = 0L): PasskeyEntity =
        PasskeyEntity(
            credentialId = credentialId,
            rpId = "example.com",
            rpDisplayName = "Example",
            userHandle = ByteArray(16) { 0x77 },
            userName = "alice@example.com",
            userDisplayName = "Alice",
            isDiscoverable = true,
            encryptedPrivateKey = ByteArray(48) { 0x66 },
            privateKeyIv = ByteArray(12) { 0x55 },
            keyAlias = "keynest_passkey_$credentialId",
            signCount = signCount,
            displayName = null,
            createdAt = 1_700_000_000_000L,
            lastUsedAt = null,
        )

    @Suppress("unused")
    private fun ignoredIntent(): Intent = Intent()

    @Suppress("unused")
    private fun ignoredBlob(): EncryptedBlob = EncryptedBlob(ByteArray(12), ByteArray(48))
}
