package io.github.hitoshiichikawa.keynest.credentialprovider

import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for [KeyNestCredentialProviderService] (Issue #90 / Phase 1).
 *
 * Verifies that all three callbacks (`onBeginCreateCredentialRequest` /
 * `onBeginGetCredentialRequest` / `onClearCredentialStateRequest`) return
 * empty success responses via `callback.onResult` and never throw nor invoke
 * `callback.onError` (req 3.2 / 3.3 / 3.4 / 3.5 / 6.1 / 6.2 / 6.3).
 *
 * Runs under Robolectric with `sdk = [34]` so that the API 34+ `OutcomeReceiver`
 * and Credential Manager provider classes resolve. The service class itself is
 * annotated `@RequiresApi(34)` (design §6.2). The service holds no state so
 * the test instantiates it directly and invokes each callback synchronously.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class KeyNestCredentialProviderServiceTest {

    private val service = KeyNestCredentialProviderService()

    @Test
    fun onBeginCreateCredentialRequest_invokesOnResult_withEmptyResponse() {
        val request = mockk<BeginCreateCredentialRequest>(relaxed = true)
        val callback = mockk<OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>>(relaxed = true)
        val resultSlot = slot<BeginCreateCredentialResponse>()

        service.onBeginCreateCredentialRequest(request, CancellationSignal(), callback)

        verify(exactly = 1) { callback.onResult(capture(resultSlot)) }
        verify(exactly = 0) { callback.onError(any()) }
        assertThat(resultSlot.captured.createEntries).isEmpty()
        assertThat(resultSlot.captured.remoteEntry).isNull()
    }

    @Test
    fun onBeginGetCredentialRequest_invokesOnResult_withEmptyResponse() {
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
    fun onClearCredentialStateRequest_invokesOnResult_withNull() {
        val request = mockk<ProviderClearCredentialStateRequest>(relaxed = true)
        val callback = mockk<OutcomeReceiver<Void?, ClearCredentialException>>(relaxed = true)

        service.onClearCredentialStateRequest(request, CancellationSignal(), callback)

        verify(exactly = 1) { callback.onResult(null) }
        verify(exactly = 0) { callback.onError(any()) }
    }

    @Test
    fun allCallbacks_doNotThrow_andDoNotInvokeOnError() {
        // Sanity check: re-run each callback once more and assert no exception
        // is raised (req 3.5). mockk verifications above already prove `onError`
        // is not invoked, but exercising the path again under a single test
        // pins the no-throw contract per callback in one assertion.
        val createCallback =
            mockk<OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>>(relaxed = true)
        val getCallback =
            mockk<OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>>(relaxed = true)
        val clearCallback =
            mockk<OutcomeReceiver<Void?, ClearCredentialException>>(relaxed = true)

        service.onBeginCreateCredentialRequest(
            mockk(relaxed = true),
            CancellationSignal(),
            createCallback,
        )
        service.onBeginGetCredentialRequest(
            mockk(relaxed = true),
            CancellationSignal(),
            getCallback,
        )
        service.onClearCredentialStateRequest(
            mockk(relaxed = true),
            CancellationSignal(),
            clearCallback,
        )

        verify(exactly = 0) { createCallback.onError(any()) }
        verify(exactly = 0) { getCallback.onError(any()) }
        verify(exactly = 0) { clearCallback.onError(any()) }
    }
}
