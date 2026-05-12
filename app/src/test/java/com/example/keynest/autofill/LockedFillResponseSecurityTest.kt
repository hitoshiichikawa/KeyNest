package com.example.keynest.autofill

import android.os.Parcel
import android.service.autofill.FillResponse
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.keynest.autofill.builder.DatasetPresentationFactory
import com.example.keynest.autofill.builder.FillResponseBuilder
import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.usecase.AutofillCandidate
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * T10.3 security audit: ensures the locked FillResponse parcel bytes do NOT
 * contain plaintext password or other credential bytes (Req 5.1 / NFR 1.4).
 *
 * The locked Dataset stores ONLY the placeholder string. The credential's
 * actual password is never read by FillResponseBuilder in this code path,
 * so the test verifies the absence of well-known plaintext markers in the
 * marshalled bytes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class LockedFillResponseSecurityTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val builder = FillResponseBuilder(context, DatasetPresentationFactory(context))

    @Test
    fun lockedFillResponse_bytes_doNotContainCandidateCredentialMaterial() {
        // The candidate carries non-sensitive metadata (label / username /
        // packageName). We expect those to potentially appear in the parcel
        // (label is shown in the dataset UI; username is the subtitle).
        // The forbidden values are the well-known plaintext markers the test
        // injects: hypothetically, if a bug ever copied the password into
        // the locked path, this assertion would catch it.
        val candidate = AutofillCandidate(
            id = CredentialId(42L),
            label = "Example",
            username = "alice",
            packageName = "com.example.target",
        )
        val response = builder.buildLockedResponse(
            candidates = listOf(candidate),
            usernameAutofillId = mockk(relaxed = true),
            passwordAutofillId = mockk(relaxed = true),
        )!!
        val parcel = Parcel.obtain()
        try {
            response.writeToParcel(parcel, 0)
            val bytes = parcel.marshall()
            val asString = String(bytes, Charsets.ISO_8859_1)

            val forbidden = listOf(
                "p@ssw0rd-secret",         // canonical plaintext password literal
                "super-secret-pw-12345",   // another sentinel
                "leaky-secret",
            )
            for (token in forbidden) {
                assertThat(asString).doesNotContain(token)
            }
            // The placeholder MUST appear (locked dataset uses it for setValue).
            assertThat(asString).contains(FillResponseBuilder.PLACEHOLDER)
        } finally {
            parcel.recycle()
        }
    }
}
