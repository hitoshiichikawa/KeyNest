package io.github.hitoshiichikawa.keynest.autofill

import android.view.autofill.AutofillId
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.autofill.builder.DatasetPresentationFactory
import io.github.hitoshiichikawa.keynest.autofill.builder.FillResponseBuilder
import io.github.hitoshiichikawa.keynest.autofill.parser.AssistStructureParser.CustomFieldCandidate
import io.github.hitoshiichikawa.keynest.autofill.parser.AutofillFieldHeuristics
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.usecase.AutofillCandidate
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Smoke tests for the customField extensions on [FillResponseBuilder].
 * Issue #66 Phase 1.
 *
 * The match algorithm itself lives in CustomFieldMatcher and is unit
 * tested separately; here we only verify that the builder accepts the new
 * inputs without crashing and that the locked / unlocked Datasets are
 * non-null in the happy paths.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CustomFieldFillResponseTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val builder = FillResponseBuilder(context, DatasetPresentationFactory(context))

    private val usernameAutofillId = mockk<AutofillId>(relaxed = true)
    private val passwordAutofillId = mockk<AutofillId>(relaxed = true)
    private val customFieldAutofillId = mockk<AutofillId>(relaxed = true)

    @Test
    fun buildLockedResponse_acceptsCustomFieldCandidates() {
        val candidate = AutofillCandidate(
            id = CredentialId(1L),
            label = "Label",
            username = "alice",
            packageName = "com.example.target",
        )
        val cfCandidate = CustomFieldCandidate(
            autofillId = customFieldAutofillId,
            descriptor = AutofillFieldHeuristics.FieldDescriptor(
                autofillHints = null,
                inputType = 0,
                idEntry = "member_id",
                hint = "Member ID",
                contentDescription = null,
            ),
        )

        val response = builder.buildLockedResponse(
            candidates = listOf(candidate),
            usernameAutofillId = usernameAutofillId,
            passwordAutofillId = passwordAutofillId,
            customFieldCandidates = listOf(cfCandidate),
        )

        // Smoke: response built without exception even when customField
        // candidates are present. Detailed parcel inspection is done in
        // LockedFillResponseSecurityTest; here we just ensure the new
        // overload path is exercised.
        assertThat(response).isNotNull()
    }

    @Test
    fun buildLockedResponse_dedupesCustomFieldAutofillIds_againstUsernamePassword() {
        // If a customField candidate's AutofillId is the same instance as
        // usernameAutofillId / passwordAutofillId, the builder must not
        // call setValue() twice for it (Android forbids that and throws).
        val candidate = AutofillCandidate(
            id = CredentialId(1L),
            label = "Label",
            username = "alice",
            packageName = "com.example.target",
        )
        val cfCandidateSameAsUsername = CustomFieldCandidate(
            autofillId = usernameAutofillId,
            descriptor = AutofillFieldHeuristics.FieldDescriptor(
                autofillHints = null,
                inputType = 0,
                idEntry = "user",
                hint = null,
                contentDescription = null,
            ),
        )

        // Should not throw.
        val response = builder.buildLockedResponse(
            candidates = listOf(candidate),
            usernameAutofillId = usernameAutofillId,
            passwordAutofillId = passwordAutofillId,
            customFieldCandidates = listOf(cfCandidateSameAsUsername),
        )

        assertThat(response).isNotNull()
    }

    @Test
    fun buildUnlockedDataset_includesCustomFieldValues() {
        // Req 6.2: a matched customField value lands in the unlocked
        // Dataset. We can only smoke-test this here (Dataset is opaque);
        // the secondary contract is asserted in CustomFieldMatcherTest.
        val dataset = builder.buildUnlockedDataset(
            usernameAutofillId = usernameAutofillId,
            usernameValue = "alice",
            passwordAutofillId = passwordAutofillId,
            passwordValue = "p@ssw0rd",
            label = "Label",
            customFieldValues = mapOf(customFieldAutofillId to "M-9999"),
        )
        assertThat(dataset).isNotNull()
    }

    @Test
    fun buildUnlockedDataset_dedupesCustomFieldIds_againstUsernamePassword() {
        // Same defensive dedupe as the locked builder: a customField map
        // entry pointing at the username AutofillId must not trigger a
        // second setValue() call on the same id within one Dataset.
        val dataset = builder.buildUnlockedDataset(
            usernameAutofillId = usernameAutofillId,
            usernameValue = "alice",
            passwordAutofillId = passwordAutofillId,
            passwordValue = "p@ssw0rd",
            label = "Label",
            customFieldValues = mapOf(
                usernameAutofillId to "should-be-ignored",
                customFieldAutofillId to "M-9999",
            ),
        )
        assertThat(dataset).isNotNull()
    }

    @Test
    fun buildUnlockedDataset_emptyCustomFieldMap_preservesExistingBehaviour() {
        // Default value of customFieldValues is empty so existing call
        // sites (and existing tests) keep working unchanged.
        val dataset = builder.buildUnlockedDataset(
            usernameAutofillId = usernameAutofillId,
            usernameValue = "alice",
            passwordAutofillId = passwordAutofillId,
            passwordValue = "p@ssw0rd",
            label = "Label",
        )
        assertThat(dataset).isNotNull()
    }
}
