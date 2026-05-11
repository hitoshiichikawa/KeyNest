package com.example.keynest.autofill

import android.text.InputType
import android.view.View
import com.example.keynest.autofill.parser.AutofillFieldHeuristics
import com.example.keynest.autofill.parser.AutofillFieldHeuristics.FieldDescriptor
import com.example.keynest.autofill.parser.AutofillFieldHeuristics.Role
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-logic tests for the 4-stage classification heuristic. Req 3.1, 3.2,
 * NFR 3.1.
 *
 * Each test exercises one decision path. The class is a plain `object`, so
 * no Robolectric / instrumentation is needed - InputType is just a set of
 * int constants.
 */
class AutofillFieldHeuristicsTest {

    // ---- Stage 1: autofillHints ------------------------------------------

    @Test
    fun classify_returnsUsername_whenAutofillHintIsUsername() {
        val descriptor = blank().copy(autofillHints = listOf(View.AUTOFILL_HINT_USERNAME))
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenAutofillHintIsEmail() {
        val descriptor = blank().copy(autofillHints = listOf(View.AUTOFILL_HINT_EMAIL_ADDRESS))
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsPassword_whenAutofillHintIsPassword() {
        val descriptor = blank().copy(autofillHints = listOf(View.AUTOFILL_HINT_PASSWORD))
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Password)
    }

    @Test
    fun classify_prefersHintOverInputType_whenBothPresent() {
        val descriptor = blank().copy(
            autofillHints = listOf(View.AUTOFILL_HINT_USERNAME),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
        )
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    // ---- Stage 2: inputType password -------------------------------------

    @Test
    fun classify_returnsPassword_forTextPasswordInputType() {
        val descriptor = blank().copy(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
        )
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Password)
    }

    @Test
    fun classify_returnsPassword_forVisiblePasswordInputType() {
        val descriptor = blank().copy(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        )
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Password)
    }

    @Test
    fun classify_returnsPassword_forWebPasswordInputType() {
        val descriptor = blank().copy(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Password)
    }

    @Test
    fun classify_returnsPassword_forNumericPasswordInputType() {
        val descriptor = blank().copy(
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        )
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Password)
    }

    // ---- Stage 3: inputType email ---------------------------------------

    @Test
    fun classify_returnsUsername_forEmailInputType() {
        val descriptor = blank().copy(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        )
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_forWebEmailInputType() {
        val descriptor = blank().copy(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        )
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    // ---- Stage 4: textual heuristics ------------------------------------

    @Test
    fun classify_returnsUsername_whenIdEntryContainsUser() {
        val descriptor = blank().copy(idEntry = "login_user_input")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsPassword_whenHintContainsPwd() {
        val descriptor = blank().copy(hint = "Enter pwd")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Password)
    }

    @Test
    fun classify_returnsPassword_whenContentDescriptionMentionsPassword() {
        val descriptor = blank().copy(contentDescription = "Password field")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Password)
    }

    @Test
    fun classify_treatsKeywordsCaseInsensitively() {
        val descriptor = blank().copy(idEntry = "EMAIL_INPUT")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_prefersPasswordOverUsername_whenBothKeywordsAppear() {
        // e.g. "user_password" -> password win because the keyword scan checks
        // password first.
        val descriptor = blank().copy(idEntry = "user_password")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Password)
    }

    // ---- Unknown / boundary ---------------------------------------------

    @Test
    fun classify_returnsUnknown_whenNoSignalsPresent() {
        assertThat(AutofillFieldHeuristics.classify(blank())).isEqualTo(Role.Unknown)
    }

    @Test
    fun classify_returnsUnknown_whenIdEntryHasUnrelatedKeyword() {
        val descriptor = blank().copy(idEntry = "submit_button", hint = "Click here")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Unknown)
    }

    @Test
    fun classify_ignoresEmptyAutofillHints() {
        val descriptor = blank().copy(autofillHints = emptyList())
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Unknown)
    }

    private fun blank() = FieldDescriptor(
        autofillHints = null,
        inputType = 0,
        idEntry = null,
        hint = null,
        contentDescription = null,
    )
}
