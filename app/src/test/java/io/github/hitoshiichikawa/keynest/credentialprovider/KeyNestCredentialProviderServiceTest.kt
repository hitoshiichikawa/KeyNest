package io.github.hitoshiichikawa.keynest.credentialprovider

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePasswordCredentialRequest
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.credentialprovider.registration.CreateEntryBuilder
import io.github.hitoshiichikawa.keynest.credentialprovider.registration.ExcludeCredentialDetector
import io.github.hitoshiichikawa.keynest.di.ServiceLocator
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Unit tests for [KeyNestCredentialProviderService].
 *
 * Issue #90 baseline (empty-response contracts for `onBeginGetCredentialRequest`
 * / `onClearCredentialStateRequest`) is preserved (Req 6.4). Issue #99
 * adds:
 *  - residentKey = required / preferred / discouraged → 1 CreateEntry
 *  - excludeCredentials hit → CreateCredentialNoCreateOptionException
 *  - excludeCredentials miss → 1 CreateEntry
 *  - non-PublicKey request → empty response, repository NOT consulted
 *
 * `ServiceLocator` is stubbed via [mockkObject] so the dependency graph
 * (database / Keystore / repository) stays out of the unit-test classpath.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class KeyNestCredentialProviderServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var service: KeyNestCredentialProviderService

    private val detector = mockk<ExcludeCredentialDetector>()
    private val createEntryBuilder = mockk<CreateEntryBuilder>()

    @Before
    fun setUp() {
        // Use Robolectric so applicationContext is attached for the Service's
        // defensive ServiceLocator.initialize(...) call.
        service = Robolectric.setupService(KeyNestCredentialProviderService::class.java)
        mockkObject(ServiceLocator)
        every { ServiceLocator.initialize(any()) } returns Unit
        every { ServiceLocator.excludeCredentialDetector } returns detector
        every { ServiceLocator.createEntryBuilder } returns createEntryBuilder
        every { createEntryBuilder.build(any()) } returns fakeCreateEntry()
        every { detector.containsAny(any()) } returns false
    }

    @After
    fun tearDown() {
        unmockkObject(ServiceLocator)
    }

    // ---- residentKey three values ----------------------------------------

    @Test
    fun onBeginCreateCredentialRequest_publicKeyResidentKeyRequired_returnsCreateEntry() {
        val callback = mockk<OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>>(relaxed = true)
        val resultSlot = slot<BeginCreateCredentialResponse>()

        service.onBeginCreateCredentialRequest(
            publicKeyRequest(residentKey = "required"),
            CancellationSignal(),
            callback,
        )

        verify(exactly = 1) { callback.onResult(capture(resultSlot)) }
        verify(exactly = 0) { callback.onError(any()) }
        assertThat(resultSlot.captured.createEntries).hasSize(1)
    }

    @Test
    fun onBeginCreateCredentialRequest_publicKeyResidentKeyPreferred_returnsCreateEntry() {
        val callback = mockk<OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>>(relaxed = true)
        val resultSlot = slot<BeginCreateCredentialResponse>()

        service.onBeginCreateCredentialRequest(
            publicKeyRequest(residentKey = "preferred"),
            CancellationSignal(),
            callback,
        )

        verify(exactly = 1) { callback.onResult(capture(resultSlot)) }
        verify(exactly = 0) { callback.onError(any()) }
        assertThat(resultSlot.captured.createEntries).hasSize(1)
    }

    @Test
    fun onBeginCreateCredentialRequest_publicKeyResidentKeyDiscouraged_returnsCreateEntry() {
        val callback = mockk<OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>>(relaxed = true)
        val resultSlot = slot<BeginCreateCredentialResponse>()

        service.onBeginCreateCredentialRequest(
            publicKeyRequest(residentKey = "discouraged"),
            CancellationSignal(),
            callback,
        )

        verify(exactly = 1) { callback.onResult(capture(resultSlot)) }
        verify(exactly = 0) { callback.onError(any()) }
        assertThat(resultSlot.captured.createEntries).hasSize(1)
    }

    // ---- excludeCredentials -----------------------------------------------

    @Test
    fun onBeginCreateCredentialRequest_excludeCredentialsHit_returnsNoCreateOptionException() {
        every { detector.containsAny(any()) } returns true
        val callback = mockk<OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>>(relaxed = true)
        val errSlot = slot<CreateCredentialException>()

        service.onBeginCreateCredentialRequest(
            publicKeyRequest(
                residentKey = "preferred",
                excludeIds = listOf("aaaaaa", "bbbbbb"),
            ),
            CancellationSignal(),
            callback,
        )

        verify(exactly = 1) { callback.onError(capture(errSlot)) }
        verify(exactly = 0) { callback.onResult(any()) }
        assertThat(errSlot.captured).isInstanceOf(CreateCredentialNoCreateOptionException::class.java)
        verify(exactly = 0) { createEntryBuilder.build(any()) }
    }

    @Test
    fun onBeginCreateCredentialRequest_excludeCredentialsNoHit_returnsCreateEntry() {
        every { detector.containsAny(any()) } returns false
        val callback = mockk<OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>>(relaxed = true)
        val resultSlot = slot<BeginCreateCredentialResponse>()

        service.onBeginCreateCredentialRequest(
            publicKeyRequest(
                residentKey = "preferred",
                excludeIds = listOf("xxxxxx", "yyyyyy"),
            ),
            CancellationSignal(),
            callback,
        )

        verify(exactly = 1) { callback.onResult(capture(resultSlot)) }
        verify(exactly = 0) { callback.onError(any()) }
        assertThat(resultSlot.captured.createEntries).hasSize(1)
        verify(exactly = 1) { detector.containsAny(any()) }
    }

    // ---- password request -------------------------------------------------

    @Test
    fun onBeginCreateCredentialRequest_passwordRequest_returnsEmptyResponse_andDoesNotQueryRepository() {
        val callback = mockk<OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>>(relaxed = true)
        val resultSlot = slot<BeginCreateCredentialResponse>()

        service.onBeginCreateCredentialRequest(
            BeginCreatePasswordCredentialRequest(
                callingAppInfo = sampleCallingAppInfo(),
                candidateQueryData = Bundle(),
            ),
            CancellationSignal(),
            callback,
        )

        verify(exactly = 1) { callback.onResult(capture(resultSlot)) }
        verify(exactly = 0) { callback.onError(any()) }
        assertThat(resultSlot.captured.createEntries).isEmpty()
        verify(exactly = 0) { detector.containsAny(any()) }
        verify(exactly = 0) { createEntryBuilder.build(any()) }
    }

    // ---- #90 stubs preserved ---------------------------------------------

    @Test
    fun onBeginGetCredentialRequest_stillReturnsEmptyResponse() {
        val request = mockk<BeginGetCredentialRequest>(relaxed = true)
        val callback = mockk<OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>>(relaxed = true)
        val resultSlot = slot<BeginGetCredentialResponse>()

        service.onBeginGetCredentialRequest(request, CancellationSignal(), callback)

        verify(exactly = 1) { callback.onResult(capture(resultSlot)) }
        verify(exactly = 0) { callback.onError(any()) }
        assertThat(resultSlot.captured.credentialEntries).isEmpty()
        assertThat(resultSlot.captured.actions).isEmpty()
        assertThat(resultSlot.captured.authenticationActions).isEmpty()
        assertThat(resultSlot.captured.remoteEntry).isNull()
    }

    @Test
    fun onClearCredentialStateRequest_stillReturnsNull() {
        val request = mockk<ProviderClearCredentialStateRequest>(relaxed = true)
        val callback = mockk<OutcomeReceiver<Void?, ClearCredentialException>>(relaxed = true)

        service.onClearCredentialStateRequest(request, CancellationSignal(), callback)

        verify(exactly = 1) { callback.onResult(null) }
        verify(exactly = 0) { callback.onError(any()) }
    }

    // ---- helpers ----------------------------------------------------------

    private fun publicKeyRequest(
        residentKey: String,
        excludeIds: List<String> = emptyList(),
    ): BeginCreatePublicKeyCredentialRequest {
        val excludeJson = if (excludeIds.isEmpty()) "" else {
            val items = excludeIds.joinToString(",") {
                "{\"type\":\"public-key\",\"id\":\"$it\"}"
            }
            ",\"excludeCredentials\":[$items]"
        }
        val json = """
            {
              "rp":{"id":"example.com","name":"Example"},
              "user":{"id":"dXNlci0xMjM","name":"alice","displayName":"Alice"},
              "challenge":"Y2hhbGxlbmdl",
              "pubKeyCredParams":[{"type":"public-key","alg":-7}],
              "authenticatorSelection":{"residentKey":"$residentKey"}
              $excludeJson
            }
        """.trimIndent()
        return BeginCreatePublicKeyCredentialRequest(
            requestJson = json,
            callingAppInfo = sampleCallingAppInfo(),
            candidateQueryData = Bundle(),
        )
    }

    private fun sampleCallingAppInfo(): CallingAppInfo =
        CallingAppInfo(
            packageName = context.packageName,
            signingInfo = android.content.pm.SigningInfo(),
        )

    private fun fakeCreateEntry(): CreateEntry {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return CreateEntry.Builder("KeyNest", pendingIntent)
            .setDescription("test entry")
            .build()
    }
}
