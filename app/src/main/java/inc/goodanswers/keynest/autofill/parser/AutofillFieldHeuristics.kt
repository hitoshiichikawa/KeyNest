package inc.goodanswers.keynest.autofill.parser

import android.text.InputType
import android.view.View

/**
 * Pure logic that classifies a single editable view as a username field, a
 * password field, both, or neither.
 *
 * Requirements: 3.1, 3.2, NFR 3.1
 *
 * Decision priority (matches design.md OQ-2):
 *   1. Explicit autofillHints (most authoritative - the developer told us).
 *   2. inputType variation indicates a password.
 *   3. inputType variation indicates an email address (treated as username).
 *   4. id-resource entry / hint label / contentDescription text matches one
 *      of the well-known role keywords.
 *
 * Each rule can independently yield UsernameOnly, PasswordOnly, or Both.
 * The caller (AssistStructureParser) reconciles overlapping signals across
 * multiple ViewNodes by preferring whichever rule fired earliest in this
 * priority order.
 */
internal object AutofillFieldHeuristics {

    /** Plain-data view of an AssistStructure.ViewNode for unit-testability. */
    data class FieldDescriptor(
        val autofillHints: List<String>?,
        val inputType: Int,
        val idEntry: String?,
        val hint: String?,
        val contentDescription: String?,
    )

    fun classify(descriptor: FieldDescriptor): Role {
        // 1) Explicit autofill hints.
        descriptor.autofillHints?.forEach { hint ->
            when (hint) {
                View.AUTOFILL_HINT_USERNAME, View.AUTOFILL_HINT_EMAIL_ADDRESS -> return Role.Username
                View.AUTOFILL_HINT_PASSWORD -> return Role.Password
            }
        }

        // 2) inputType variation password.
        if (isPasswordInputType(descriptor.inputType)) return Role.Password

        // 3) inputType variation email.
        if (isEmailInputType(descriptor.inputType)) return Role.Username

        // 4) Textual heuristics on idEntry + hint + contentDescription.
        val descriptorText = buildString {
            descriptor.idEntry?.let { append(it).append(' ') }
            descriptor.hint?.let { append(it).append(' ') }
            descriptor.contentDescription?.let { append(it) }
        }.lowercase()

        if (descriptorText.isBlank()) return Role.Unknown

        val mentionsPassword = PASSWORD_KEYWORDS.any { descriptorText.contains(it) }
        if (mentionsPassword) return Role.Password
        val mentionsUsername = USERNAME_KEYWORDS.any { descriptorText.contains(it) }
        if (mentionsUsername) return Role.Username

        return Role.Unknown
    }

    private fun isPasswordInputType(inputType: Int): Boolean {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (cls == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD) return true
            if (variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) return true
            if (variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) return true
        }
        if (cls == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ) return true
        return false
    }

    private fun isEmailInputType(inputType: Int): Boolean {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (cls == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) return true
            if (variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) return true
        }
        return false
    }

    enum class Role { Username, Password, Unknown }

    // Keyword list mirrors design.md OQ-2 verbatim: user, email, id, account, pass, pwd.
    // We additionally include "login" because real-world apps frequently use
    // "loginId" / "loginInput" instead of "username". "id" alone risks false
    // positives (e.g. resource id "spinner_id"), but design.md explicitly
    // requests it - any over-matching is acceptable because the parser still
    // requires BOTH a username candidate AND a password candidate before
    // returning a non-empty pair, so a stray "id" match cannot single-handedly
    // produce a wrong fill response.
    private val USERNAME_KEYWORDS = listOf("user", "email", "id", "account", "login")
    private val PASSWORD_KEYWORDS = listOf("pass", "pwd", "secret")
}
