package io.github.hitoshiichikawa.keynest.autofill

import android.view.autofill.AutofillId
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.autofill.matcher.CustomFieldMatcher
import io.github.hitoshiichikawa.keynest.autofill.parser.AssistStructureParser.CustomFieldCandidate
import io.github.hitoshiichikawa.keynest.autofill.parser.AutofillFieldHeuristics.FieldDescriptor
import io.github.hitoshiichikawa.keynest.domain.model.CustomField
import io.mockk.mockk
import org.junit.Test

/**
 * Behaviour of [CustomFieldMatcher]. Issue #66 Phase 1.
 *
 * Backs Req 6.2 / 6.3 / 6.4 (matching algorithm) and Req 4.4 / 4.5
 * (deterministic conflict resolution + many-to-many semantics).
 *
 * Runs on plain JUnit. AutofillId is mocked because its constructor is
 * hidden.
 */
class CustomFieldMatcherTest {

    @Test
    fun match_singleField_singleMatch_returnsValue() {
        // Req 6.2: the matcher uses substring containment on normalised
        // strings (lowercase + whitespace stripped). "memberidinput"
        // contains "memberid" so the match fires.
        val fieldId = mockk<AutofillId>()
        val candidate = CustomFieldCandidate(fieldId, descriptor(idEntry = "memberIdInput"))

        val result = CustomFieldMatcher.match(
            candidates = listOf(candidate),
            customFields = listOf(CustomField("memberId", "M-9999")),
        )

        assertThat(result).containsExactly(fieldId, "M-9999")
    }

    @Test
    fun match_firstRegisteredCustomFieldWins_onMultiMatch() {
        // Req 4.4 / 6.3: when the same field could match more than one
        // customField, the FIRST registered customField wins (list order
        // determinism). normalizeKey strips spaces, so "memberIdField"
        // becomes "memberidfield" which contains both "memberid" and
        // "member"; the matcher picks the first.
        val fieldId = mockk<AutofillId>()
        val candidate = CustomFieldCandidate(fieldId, descriptor(idEntry = "memberIdField"))
        val customFields = listOf(
            CustomField("memberId", "FIRST"),
            CustomField("member", "SECOND"),
        )

        val result = CustomFieldMatcher.match(listOf(candidate), customFields)

        assertThat(result).containsExactly(fieldId, "FIRST")
    }

    @Test
    fun match_omitsCandidatesThatDoNotMatchAnyCustomField() {
        // Req 6.4: customFields that do not match any field are absent from
        // the output map (so the Dataset never receives a setValue for
        // them).
        val matchingId = mockk<AutofillId>()
        val unmatchedId = mockk<AutofillId>()
        val candidates = listOf(
            CustomFieldCandidate(matchingId, descriptor(idEntry = "storeCodeInput")),
            CustomFieldCandidate(unmatchedId, descriptor(idEntry = "commentBody")),
        )

        val result = CustomFieldMatcher.match(
            candidates = candidates,
            customFields = listOf(CustomField("storeCode", "TKO-001")),
        )

        assertThat(result).containsExactly(matchingId, "TKO-001")
        assertThat(result).doesNotContainKey(unmatchedId)
    }

    @Test
    fun match_sameCustomFieldAppliedToMultipleFields() {
        // Req 4.5: a single customField may match multiple fields.
        val a = mockk<AutofillId>()
        val b = mockk<AutofillId>()
        val candidates = listOf(
            CustomFieldCandidate(a, descriptor(idEntry = "memberIdPrimary")),
            CustomFieldCandidate(b, descriptor(idEntry = "memberIdConfirm")),
        )

        val result = CustomFieldMatcher.match(
            candidates = candidates,
            customFields = listOf(CustomField("memberId", "M-9999")),
        )

        assertThat(result).containsEntry(a, "M-9999")
        assertThat(result).containsEntry(b, "M-9999")
    }

    @Test
    fun match_blankFieldKey_isIgnored() {
        // A customField whose fieldKey is empty (or only whitespace) MUST
        // NOT match anything — otherwise normalizeKey("") = "" which the
        // contains() check would treat as a hit on every match key.
        val id = mockk<AutofillId>()
        val candidate = CustomFieldCandidate(id, descriptor(idEntry = "anything"))

        val result = CustomFieldMatcher.match(
            candidates = listOf(candidate),
            customFields = listOf(CustomField("   ", "should-not-fill")),
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun match_emptyInputs_returnEmptyMap() {
        assertThat(CustomFieldMatcher.match(emptyList(), listOf(CustomField("k", "v")))).isEmpty()
        val id = mockk<AutofillId>()
        assertThat(
            CustomFieldMatcher.match(
                listOf(CustomFieldCandidate(id, descriptor(idEntry = "x"))),
                emptyList(),
            ),
        ).isEmpty()
    }

    @Test
    fun match_japaneseFieldKey_matchesNormalisedHint() {
        // requirements §3: full-width whitespace is stripped during
        // normalisation so "会員 番号" in the hint matches a customField
        // whose fieldKey is "会員番号".
        val id = mockk<AutofillId>()
        val candidate = CustomFieldCandidate(id, descriptor(hint = "会員 番号"))

        val result = CustomFieldMatcher.match(
            candidates = listOf(candidate),
            customFields = listOf(CustomField("会員番号", "M-9999")),
        )

        assertThat(result).containsExactly(id, "M-9999")
    }

    private fun descriptor(
        idEntry: String? = null,
        hint: String? = null,
    ): FieldDescriptor = FieldDescriptor(
        autofillHints = null,
        inputType = 0,
        idEntry = idEntry,
        hint = hint,
        contentDescription = null,
    )
}
