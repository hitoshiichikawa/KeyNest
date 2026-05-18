package io.github.hitoshiichikawa.keynest.autofill

import android.text.InputType
import android.view.View
import io.github.hitoshiichikawa.keynest.autofill.parser.AutofillFieldHeuristics
import io.github.hitoshiichikawa.keynest.autofill.parser.AutofillFieldHeuristics.FieldDescriptor
import io.github.hitoshiichikawa.keynest.autofill.parser.AutofillFieldHeuristics.Role
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

    // ---- Stage 4: Issue #68 Phase 3 — Japanese hint patterns ------------
    // Req 4.1: each Japanese literal listed in requirements §5 Req 1 must be
    // classified as the expected Role when supplied via `hint`. Literals are
    // bit-identical to Issue #68 / requirements §5 (NFR 3) — note ユーザー
    // uses U+30FC (long-vowel mark) and ユーザーID uses half-width ASCII ID.

    @Test
    fun classify_returnsUsername_whenHintIsJapaneseUserName() {
        val descriptor = blank().copy(hint = "ユーザー名")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenHintIsJapaneseUserId() {
        val descriptor = blank().copy(hint = "ユーザーID")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenHintIsJapaneseMailAddress() {
        val descriptor = blank().copy(hint = "メールアドレス")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenHintIsJapaneseEMail() {
        val descriptor = blank().copy(hint = "Eメール")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenHintIsJapanesePhoneNumber() {
        val descriptor = blank().copy(hint = "電話番号")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenHintIsJapaneseMemberNumber() {
        val descriptor = blank().copy(hint = "会員番号")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenHintIsJapaneseEmployeeNumber() {
        val descriptor = blank().copy(hint = "社員番号")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsPassword_whenHintIsJapanesePassword() {
        val descriptor = blank().copy(hint = "パスワード")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Password)
    }

    @Test
    fun classify_returnsPassword_whenHintIsJapanesePinCode() {
        val descriptor = blank().copy(hint = "暗証番号")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Password)
    }

    // ---- Stage 4: Issue #68 Phase 3 — resourceId snake_case patterns ----
    // Req 4.2: each snake_case identifier listed in requirements §5 Req 2
    // must be classified as Role.Username when supplied via `idEntry`.

    @Test
    fun classify_returnsUsername_whenIdEntryIsMemberNo() {
        val descriptor = blank().copy(idEntry = "member_no")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenIdEntryIsMemberId() {
        val descriptor = blank().copy(idEntry = "member_id")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenIdEntryIsCustomerId() {
        val descriptor = blank().copy(idEntry = "customer_id")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenIdEntryIsCustomerNo() {
        val descriptor = blank().copy(idEntry = "customer_no")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenIdEntryIsEmailInput() {
        val descriptor = blank().copy(idEntry = "email_input")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenIdEntryIsEmailField() {
        val descriptor = blank().copy(idEntry = "email_field")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenIdEntryIsMailAddress() {
        val descriptor = blank().copy(idEntry = "mail_address")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenIdEntryIsTelInput() {
        val descriptor = blank().copy(idEntry = "tel_input")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenIdEntryIsPhoneInput() {
        val descriptor = blank().copy(idEntry = "phone_input")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenIdEntryIsTelNo() {
        val descriptor = blank().copy(idEntry = "tel_no")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenIdEntryIsStaffId() {
        val descriptor = blank().copy(idEntry = "staff_id")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenIdEntryIsEmployeeId() {
        val descriptor = blank().copy(idEntry = "employee_id")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    @Test
    fun classify_returnsUsername_whenIdEntryIsAccountId() {
        val descriptor = blank().copy(idEntry = "account_id")
        assertThat(AutofillFieldHeuristics.classify(descriptor)).isEqualTo(Role.Username)
    }

    private fun blank() = FieldDescriptor(
        autofillHints = null,
        inputType = 0,
        idEntry = null,
        hint = null,
        contentDescription = null,
    )
}
