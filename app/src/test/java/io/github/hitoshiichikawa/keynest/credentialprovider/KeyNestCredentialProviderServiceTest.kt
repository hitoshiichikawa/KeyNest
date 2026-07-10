package io.github.hitoshiichikawa.keynest.credentialprovider

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
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
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.PublicKeyCredentialEntry
import io.github.hitoshiichikawa.keynest.credentialprovider.authentication.GetEntryBuilder
import io.github.hitoshiichikawa.keynest.credentialprovider.registration.CreateEntryBuilder
import io.github.hitoshiichikawa.keynest.credentialprovider.registration.ExcludeCredentialDetector
import io.github.hitoshiichikawa.keynest.di.ServiceLocator
import io.mockk.coVerify
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
 *  - non-PublicKey request → empty response, repository NOT consulted
 *
 * Issue #136: excludeCredentials は **認証前に照合しない** 契約に変更。
 *  - excludeCredentials の有無に関わらず Service は 1 CreateEntry を返し、
 *    ExcludeCredentialDetector を一切呼ばない（存在オラクル防止 /
 *    WebAuthn §6.3.2 step 5）。照合は PasskeyCreateActivity の生体認証
 *    成功後に行われる
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
    private val getEntryBuilder = mockk<GetEntryBuilder>()

    @Before
    fun setUp() {
        // Use Robolectric so applicationContext is attached for the Service's
        // defensive ServiceLocator.initialize(...) call.
        service = Robolectric.setupService(KeyNestCredentialProviderService::class.java)
        mockkObject(ServiceLocator)
        every { ServiceLocator.initialize(any()) } returns Unit
        // #136: Service は detector を呼ばない契約。スタブは「呼ばれない」
        // 検証（coVerify exactly=0）のためだけに繋いでおく。
        every { ServiceLocator.excludeCredentialDetector } returns detector
        every { ServiceLocator.createEntryBuilder } returns createEntryBuilder
        every { ServiceLocator.getEntryBuilder } returns getEntryBuilder
        every { createEntryBuilder.build(any()) } returns fakeCreateEntry()
        every { getEntryBuilder.build(any()) } returns emptyList()
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

    // ---- excludeCredentials (#136: 認証前に照合しない) ---------------------

    @Test
    fun onBeginCreateCredentialRequest_excludeCredentialsPresent_returnsCreateEntryWithoutQueryingVault() {
        // Arrange: excludeCredentials 付きリクエスト。#136 以前はここで
        // vault を照会し NoCreateOption 例外を返していた（存在オラクル）。
        val callback = mockk<OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>>(relaxed = true)
        val resultSlot = slot<BeginCreateCredentialResponse>()

        // Act
        service.onBeginCreateCredentialRequest(
            publicKeyRequest(
                residentKey = "preferred",
                excludeIds = listOf("aaaaaa", "bbbbbb"),
            ),
            CancellationSignal(),
            callback,
        )

        // Assert: 例外ではなく CreateEntry を返し、vault には触れない
        // （認証前の応答が exclude の有無で分岐しない = 観測不能）。
        verify(exactly = 1) { callback.onResult(capture(resultSlot)) }
        verify(exactly = 0) { callback.onError(any()) }
        assertThat(resultSlot.captured.createEntries).hasSize(1)
        coVerify(exactly = 0) { detector.containsAny(any(), any()) }
    }

    @Test
    fun onBeginCreateCredentialRequest_noExcludeCredentials_returnsCreateEntryWithoutQueryingVault() {
        // Arrange
        val callback = mockk<OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>>(relaxed = true)
        val resultSlot = slot<BeginCreateCredentialResponse>()

        // Act
        service.onBeginCreateCredentialRequest(
            publicKeyRequest(residentKey = "preferred"),
            CancellationSignal(),
            callback,
        )

        // Assert: exclude 有り（上のテスト）と応答が区別できないこと。
        verify(exactly = 1) { callback.onResult(capture(resultSlot)) }
        verify(exactly = 0) { callback.onError(any()) }
        assertThat(resultSlot.captured.createEntries).hasSize(1)
        coVerify(exactly = 0) { detector.containsAny(any(), any()) }
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
        coVerify(exactly = 0) { detector.containsAny(any(), any()) }
        verify(exactly = 0) { createEntryBuilder.build(any()) }
    }

    // ---- #100 onBeginGetCredentialRequest --------------------------------

    @Test
    fun onBeginGetCredentialRequest_noPublicKeyOption_returnsEmptyResponse() {
        val request = mockk<BeginGetCredentialRequest>(relaxed = true)
        every { request.beginGetCredentialOptions } returns emptyList()
        val callback = mockk<OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>>(relaxed = true)
        val resultSlot = slot<BeginGetCredentialResponse>()

        service.onBeginGetCredentialRequest(request, CancellationSignal(), callback)

        verify(exactly = 1) { callback.onResult(capture(resultSlot)) }
        verify(exactly = 0) { callback.onError(any()) }
        assertThat(resultSlot.captured.credentialEntries).isEmpty()
        assertThat(resultSlot.captured.actions).isEmpty()
        assertThat(resultSlot.captured.authenticationActions).isEmpty()
        assertThat(resultSlot.captured.remoteEntry).isNull()
        verify(exactly = 0) { getEntryBuilder.build(any()) }
    }

    @Test
    fun onBeginGetCredentialRequest_publicKeyAllowCredentialsEmpty_returnsDiscoverableEntries() {
        val option = publicKeyGetOption(allowCredentials = emptyList())
        val request = mockk<BeginGetCredentialRequest>(relaxed = true)
        every { request.beginGetCredentialOptions } returns listOf(option)
        // Return 3 entries to mimic listDiscoverableByRpId hitting 3 rows.
        every { getEntryBuilder.build(option) } returns listOf(
            fakePublicKeyEntry(option, "id1"),
            fakePublicKeyEntry(option, "id2"),
            fakePublicKeyEntry(option, "id3"),
        )
        val callback = mockk<OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>>(relaxed = true)
        val resultSlot = slot<BeginGetCredentialResponse>()

        service.onBeginGetCredentialRequest(request, CancellationSignal(), callback)

        verify(exactly = 1) { callback.onResult(capture(resultSlot)) }
        verify(exactly = 0) { callback.onError(any()) }
        assertThat(resultSlot.captured.credentialEntries).hasSize(3)
        verify(exactly = 1) { getEntryBuilder.build(option) }
    }

    @Test
    fun onBeginGetCredentialRequest_publicKeyAllowCredentialsSpecified_returnsExistingMatches() {
        val option = publicKeyGetOption(allowCredentials = listOf("id1", "id2", "id3"))
        val request = mockk<BeginGetCredentialRequest>(relaxed = true)
        every { request.beginGetCredentialOptions } returns listOf(option)
        // Mimic GetEntryBuilder returning only the 2 ids that existed.
        every { getEntryBuilder.build(option) } returns listOf(
            fakePublicKeyEntry(option, "id1"),
            fakePublicKeyEntry(option, "id3"),
        )
        val callback = mockk<OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>>(relaxed = true)
        val resultSlot = slot<BeginGetCredentialResponse>()

        service.onBeginGetCredentialRequest(request, CancellationSignal(), callback)

        verify(exactly = 1) { callback.onResult(capture(resultSlot)) }
        verify(exactly = 0) { callback.onError(any()) }
        assertThat(resultSlot.captured.credentialEntries).hasSize(2)
    }

    @Test
    fun onBeginGetCredentialRequest_publicKeyZeroCandidates_returnsEmptyResponse() {
        val option = publicKeyGetOption(allowCredentials = emptyList())
        val request = mockk<BeginGetCredentialRequest>(relaxed = true)
        every { request.beginGetCredentialOptions } returns listOf(option)
        every { getEntryBuilder.build(option) } returns emptyList()
        val callback = mockk<OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>>(relaxed = true)
        val resultSlot = slot<BeginGetCredentialResponse>()

        service.onBeginGetCredentialRequest(request, CancellationSignal(), callback)

        verify(exactly = 1) { callback.onResult(capture(resultSlot)) }
        verify(exactly = 0) { callback.onError(any()) }
        assertThat(resultSlot.captured.credentialEntries).isEmpty()
    }

    @Test
    fun onBeginGetCredentialRequest_passwordOptionOnly_returnsEmptyResponse_andDoesNotCallGetEntryBuilder() {
        // A non-PublicKey option list (simulated by an empty filterIsInstance result)
        // must result in an empty response without invoking GetEntryBuilder.
        val request = mockk<BeginGetCredentialRequest>(relaxed = true)
        every { request.beginGetCredentialOptions } returns emptyList()
        val callback = mockk<OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>>(relaxed = true)
        val resultSlot = slot<BeginGetCredentialResponse>()

        service.onBeginGetCredentialRequest(request, CancellationSignal(), callback)

        verify(exactly = 1) { callback.onResult(capture(resultSlot)) }
        assertThat(resultSlot.captured.credentialEntries).isEmpty()
        verify(exactly = 0) { getEntryBuilder.build(any()) }
    }

    @Test
    fun onBeginGetCredentialRequest_multiplePublicKeyOptions_aggregatesEntries() {
        val optionA = publicKeyGetOption(allowCredentials = listOf("a"))
        val optionB = publicKeyGetOption(allowCredentials = listOf("b1", "b2"))
        val request = mockk<BeginGetCredentialRequest>(relaxed = true)
        every { request.beginGetCredentialOptions } returns listOf(optionA, optionB)
        every { getEntryBuilder.build(optionA) } returns listOf(
            fakePublicKeyEntry(optionA, "a"),
        )
        every { getEntryBuilder.build(optionB) } returns listOf(
            fakePublicKeyEntry(optionB, "b1"),
            fakePublicKeyEntry(optionB, "b2"),
        )
        val callback = mockk<OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>>(relaxed = true)
        val resultSlot = slot<BeginGetCredentialResponse>()

        service.onBeginGetCredentialRequest(request, CancellationSignal(), callback)

        verify(exactly = 1) { callback.onResult(capture(resultSlot)) }
        assertThat(resultSlot.captured.credentialEntries).hasSize(3)
        verify(exactly = 1) { getEntryBuilder.build(optionA) }
        verify(exactly = 1) { getEntryBuilder.build(optionB) }
    }

    // ---- #90 stubs preserved ---------------------------------------------

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

    private fun publicKeyGetOption(
        allowCredentials: List<String>,
        rpId: String = "example.com",
    ): BeginGetPublicKeyCredentialOption {
        val allow = if (allowCredentials.isEmpty()) {
            ""
        } else {
            val items = allowCredentials.joinToString(",") {
                "{\"type\":\"public-key\",\"id\":\"$it\"}"
            }
            ",\"allowCredentials\":[$items]"
        }
        val json = """
            {
              "rpId":"$rpId",
              "challenge":"Y2hhbGxlbmdl"
              $allow
            }
        """.trimIndent()
        return BeginGetPublicKeyCredentialOption(
            candidateQueryData = Bundle(),
            id = "publicKey:test",
            requestJson = json,
            clientDataHash = null,
        )
    }

    private fun fakePublicKeyEntry(
        option: BeginGetPublicKeyCredentialOption,
        credentialId: String,
    ): PublicKeyCredentialEntry {
        val pendingIntent = PendingIntent.getActivity(
            context,
            credentialId.hashCode(),
            Intent(),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return PublicKeyCredentialEntry.Builder(
            context,
            "alice@example.com ($credentialId)",
            pendingIntent,
            option,
        )
            .setDisplayName("Example")
            .build()
    }
}
