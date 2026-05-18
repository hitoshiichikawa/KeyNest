package io.github.hitoshiichikawa.keynest.autofill

import android.view.autofill.AutofillId
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.autofill.parser.AutofillFieldHeuristics
import io.github.hitoshiichikawa.keynest.autofill.unlock.AutofillUnlockActivity
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verifies [AutofillUnlockActivity.newIntent] encodes the customField
 * extras correctly. Issue #66 Phase 1.
 *
 * The matching round-trip (newIntent -> readCustomFieldCandidates ->
 * CustomFieldMatcher) is unit-tested at the matcher layer; here we only
 * pin the on-wire shape:
 *
 * - autofill IDs land in a Parcelable ArrayList extra.
 * - per-field strings land in parallel String[] extras.
 * - autofillHints is flattened (String[]) + a parallel IntArray of
 *   per-field lengths, with -1 meaning the field had null hints.
 *
 * These contract pins guard against an accidental rename of the extra
 * keys, which would silently break the locked->unlocked customField
 * hand-off.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AutofillUnlockActivityIntentTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val usernameId = mockk<AutofillId>(relaxed = true)
    private val cfId1 = mockk<AutofillId>(relaxed = true)
    private val cfId2 = mockk<AutofillId>(relaxed = true)

    @Test
    fun newIntent_withNoCustomFields_doesNotAttachExtras() {
        val intent = AutofillUnlockActivity.newIntent(
            context = context,
            credentialId = 1L,
            usernameAutofillId = usernameId,
            passwordAutofillId = null,
        )
        assertThat(intent.hasExtra(AutofillUnlockActivity.EXTRA_CUSTOM_FIELD_AUTOFILL_IDS)).isFalse()
        assertThat(intent.hasExtra(AutofillUnlockActivity.EXTRA_CUSTOM_FIELD_HINTS)).isFalse()
    }

    @Test
    fun newIntent_withCustomFields_encodesEveryExtra() {
        val intent = AutofillUnlockActivity.newIntent(
            context = context,
            credentialId = 1L,
            usernameAutofillId = usernameId,
            passwordAutofillId = null,
            customFieldAutofillIds = listOf(cfId1, cfId2),
            customFieldDescriptors = listOf(
                AutofillFieldHeuristics.FieldDescriptor(
                    autofillHints = listOf("a", "b"),
                    inputType = 0,
                    idEntry = "member_id",
                    hint = "Member ID",
                    contentDescription = null,
                ),
                AutofillFieldHeuristics.FieldDescriptor(
                    autofillHints = null,
                    inputType = 0,
                    idEntry = "store",
                    hint = null,
                    contentDescription = "Store code",
                ),
            ),
        )

        // AutofillId list survives as a Parcelable ArrayList.
        @Suppress("DEPRECATION")
        val ids = intent.getParcelableArrayListExtra<AutofillId>(
            AutofillUnlockActivity.EXTRA_CUSTOM_FIELD_AUTOFILL_IDS,
        )
        assertThat(ids).hasSize(2)

        // Per-field strings are parallel String arrays.
        val hints = intent.getStringArrayExtra(AutofillUnlockActivity.EXTRA_CUSTOM_FIELD_HINTS)
        val idEntries = intent.getStringArrayExtra(AutofillUnlockActivity.EXTRA_CUSTOM_FIELD_ID_ENTRIES)
        val descs = intent.getStringArrayExtra(AutofillUnlockActivity.EXTRA_CUSTOM_FIELD_CONTENT_DESCRIPTIONS)
        assertThat(hints).asList().containsExactly("Member ID", "").inOrder()
        assertThat(idEntries).asList().containsExactly("member_id", "store").inOrder()
        assertThat(descs).asList().containsExactly("", "Store code").inOrder()

        // autofillHints is flat[] + lengths[]; -1 represents null.
        val flat = intent.getStringArrayExtra(AutofillUnlockActivity.EXTRA_CUSTOM_FIELD_AUTOFILL_HINTS_FLAT)
        val lengths = intent.getIntArrayExtra(AutofillUnlockActivity.EXTRA_CUSTOM_FIELD_AUTOFILL_HINTS_LENGTHS)
        assertThat(flat).asList().containsExactly("a", "b").inOrder()
        assertThat(lengths).asList().containsExactly(2, -1).inOrder()
    }

    @Test
    fun newIntent_rejectsMismatchedIdAndDescriptorLengths() {
        // Defensive contract: the two parallel lists must agree, otherwise
        // the receiving side would read garbage.
        try {
            AutofillUnlockActivity.newIntent(
                context = context,
                credentialId = 1L,
                usernameAutofillId = usernameId,
                passwordAutofillId = null,
                customFieldAutofillIds = listOf(cfId1, cfId2),
                customFieldDescriptors = listOf(
                    AutofillFieldHeuristics.FieldDescriptor(null, 0, null, null, null),
                ),
            )
            error("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected.message).contains("same length")
        }
    }
}
