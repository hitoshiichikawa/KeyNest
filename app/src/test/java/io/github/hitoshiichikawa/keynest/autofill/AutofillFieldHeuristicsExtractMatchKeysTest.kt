package io.github.hitoshiichikawa.keynest.autofill

import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.autofill.parser.AutofillFieldHeuristics
import io.github.hitoshiichikawa.keynest.autofill.parser.AutofillFieldHeuristics.FieldDescriptor
import org.junit.Test

/**
 * Behaviour of [AutofillFieldHeuristics.extractMatchKeys] /
 * [AutofillFieldHeuristics.normalizeKey]. Issue #66 Phase 1.
 *
 * Backs Req 4.1 (match key sources), design.md §7.1 / §7.2, and the
 * normalisation contract (lowercase + drop every Unicode whitespace
 * character).
 */
class AutofillFieldHeuristicsExtractMatchKeysTest {

    // ---- normalizeKey ----------------------------------------------------

    @Test
    fun normalizeKey_lowercases_andStripsHalfWidthSpaces() {
        assertThat(AutofillFieldHeuristics.normalizeKey("Member ID")).isEqualTo("memberid")
    }

    @Test
    fun normalizeKey_stripsFullWidthSpaceAndTab() {
        // 　 = U+3000 ideographic space, \t = tab
        assertThat(AutofillFieldHeuristics.normalizeKey("会員\t番号　 ")).isEqualTo("会員番号")
    }

    @Test
    fun normalizeKey_stripsNbspAndCr() {
        // NBSP (U+00A0) and CR / LF are also covered by Char.isWhitespace().
        val s = "user name\r\n"
        assertThat(AutofillFieldHeuristics.normalizeKey(s)).isEqualTo("username")
    }

    @Test
    fun normalizeKey_emptyAndAllWhitespace_yieldEmpty() {
        assertThat(AutofillFieldHeuristics.normalizeKey("")).isEmpty()
        assertThat(AutofillFieldHeuristics.normalizeKey("   \t\n　")).isEmpty()
    }

    // ---- extractMatchKeys sources ---------------------------------------

    @Test
    fun extractMatchKeys_drawsFromAutofillHints() {
        val descriptor = blank().copy(
            autofillHints = listOf("memberId", "personalCode"),
        )
        val keys = AutofillFieldHeuristics.extractMatchKeys(descriptor)
        assertThat(keys).containsExactly("memberid", "personalcode")
    }

    @Test
    fun extractMatchKeys_drawsFromHint() {
        val descriptor = blank().copy(hint = "Member ID")
        assertThat(AutofillFieldHeuristics.extractMatchKeys(descriptor))
            .containsExactly("memberid")
    }

    @Test
    fun extractMatchKeys_drawsFromIdEntry() {
        val descriptor = blank().copy(idEntry = "member_id_input")
        assertThat(AutofillFieldHeuristics.extractMatchKeys(descriptor))
            .containsExactly("member_id_input")
    }

    @Test
    fun extractMatchKeys_drawsFromContentDescription() {
        val descriptor = blank().copy(contentDescription = "会員 番号")
        assertThat(AutofillFieldHeuristics.extractMatchKeys(descriptor))
            .containsExactly("会員番号")
    }

    @Test
    fun extractMatchKeys_dedupesAcrossSources() {
        val descriptor = blank().copy(
            autofillHints = listOf("member id"),
            hint = "Member ID",
            idEntry = "MEMBER ID",
            contentDescription = "  member  id ",
        )
        // All four sources normalise to "memberid"; the Set collapses them.
        assertThat(AutofillFieldHeuristics.extractMatchKeys(descriptor))
            .containsExactly("memberid")
    }

    @Test
    fun extractMatchKeys_omitsBlankOrAllWhitespaceSources() {
        val descriptor = blank().copy(
            autofillHints = listOf("", "   "),
            hint = "",
            idEntry = " \t ",
            contentDescription = "real",
        )
        assertThat(AutofillFieldHeuristics.extractMatchKeys(descriptor))
            .containsExactly("real")
    }

    @Test
    fun extractMatchKeys_doesNotIncludeText_design_NotInDescriptor() {
        // The FieldDescriptor type intentionally does not expose `text` to
        // the heuristics module — design.md §7.2 excludes the user-typed
        // content from match keys. This test pins the contract by ensuring
        // a descriptor populated only with sources we DO include yields
        // exactly those, with no extra entries that could leak from text.
        val descriptor = blank().copy(
            autofillHints = listOf("memberId"),
            hint = "Member ID",
            idEntry = "member_id",
            contentDescription = "Member ID field",
        )
        val keys = AutofillFieldHeuristics.extractMatchKeys(descriptor)
        // The four sources collapse to two unique normalised strings.
        // Specifically: "memberid" and "member_id" and "memberidfield".
        // No keys are derived from `text` because the descriptor type
        // does not carry it.
        assertThat(keys).containsExactly("memberid", "member_id", "memberidfield")
    }

    @Test
    fun extractMatchKeys_emptyDescriptor_returnsEmptySet() {
        assertThat(AutofillFieldHeuristics.extractMatchKeys(blank())).isEmpty()
    }

    private fun blank() = FieldDescriptor(
        autofillHints = null,
        inputType = 0,
        idEntry = null,
        hint = null,
        contentDescription = null,
    )
}
