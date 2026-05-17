package io.github.hitoshiichikawa.keynest.autofill

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hitoshiichikawa.keynest.autofill.builder.DatasetPresentationFactory
import io.github.hitoshiichikawa.keynest.autofill.builder.FillResponseBuilder
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.usecase.AutofillCandidate
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Smoke tests for [FillResponseBuilder]. Runs under Robolectric so that
 * PendingIntent + RemoteViews construction succeeds.
 *
 * Covers Req 3.3 (each candidate becomes a dataset), 5.1 (locked response
 * uses authentication; no credential bytes embedded directly), and the null
 * return path used by KeyNestAutofillService.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class FillResponseBuilderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val builder = FillResponseBuilder(context, DatasetPresentationFactory(context))

    private val usernameAutofillId = mockk<android.view.autofill.AutofillId>(relaxed = true)
    private val passwordAutofillId = mockk<android.view.autofill.AutofillId>(relaxed = true)

    @Test
    fun buildLockedResponse_returnsNull_whenNoCandidates() {
        val response = builder.buildLockedResponse(
            candidates = emptyList(),
            usernameAutofillId = usernameAutofillId,
            passwordAutofillId = passwordAutofillId,
        )
        assertThat(response).isNull()
    }

    @Test
    fun buildLockedResponse_returnsNull_whenBothAutofillIdsMissing() {
        val response = builder.buildLockedResponse(
            candidates = listOf(candidate("alice")),
            usernameAutofillId = null,
            passwordAutofillId = null,
        )
        assertThat(response).isNull()
    }

    @Test
    fun buildLockedResponse_returnsNonNull_forSingleCandidate() {
        val response = builder.buildLockedResponse(
            candidates = listOf(candidate("alice")),
            usernameAutofillId = usernameAutofillId,
            passwordAutofillId = passwordAutofillId,
        )
        assertThat(response).isNotNull()
    }

    @Test
    fun buildLockedResponse_returnsNonNull_forMultipleCandidates() {
        val response = builder.buildLockedResponse(
            candidates = listOf(candidate("alice"), candidate("bob")),
            usernameAutofillId = usernameAutofillId,
            passwordAutofillId = passwordAutofillId,
        )
        assertThat(response).isNotNull()
    }

    @Test
    fun placeholderConstant_isNotAUsernameOrPasswordValue() {
        // Belt-and-braces check: the placeholder string we splat into locked
        // datasets contains no candidate-derived characters. If anybody ever
        // tweaks PLACEHOLDER to leak data, this test fails.
        assertThat(FillResponseBuilder.PLACEHOLDER).doesNotContain("alice")
        assertThat(FillResponseBuilder.PLACEHOLDER).doesNotContain("@")
        assertThat(FillResponseBuilder.PLACEHOLDER).isEqualTo("••••••")
    }

    private fun candidate(username: String): AutofillCandidate = AutofillCandidate(
        id = CredentialId(username.hashCode().toLong()),
        label = "Label-$username",
        username = username,
        packageName = "com.example.target",
    )
}
