package io.github.hitoshiichikawa.keynest.autofill.parser

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
object AutofillFieldHeuristics {

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
    //
    // Issue #68 Phase 3 adds Japanese hint literals and common business-app
    // resourceId snake_case patterns via OR-concat. Japanese characters
    // (hiragana / katakana / kanji / full-width Latin) are unchanged by
    // Kotlin's lowercase() because Unicode lowercase mapping is a no-op for
    // those code points, so the existing `descriptorText.contains(kw)` scan
    // works on them as-is. The literals here must match Issue #68 / Req 1
    // bit-exactly (NFR 3): ユーザー has the long-vowel mark U+30FC ("ー"),
    // and ユーザーID uses half-width ASCII "ID" (U+0049 U+0044).
    private val USERNAME_KEYWORDS = listOf(
        // Existing English / ASCII patterns.
        "user", "email", "id", "account", "login",
        // Req 1: Japanese hint literals classified as username candidates.
        "ユーザー名", "ユーザーid", "メールアドレス", "eメール",
        "電話番号", "会員番号", "社員番号",
        // Req 2: common business-app resourceId snake_case patterns.
        // Several of these are already covered by the broad "id" / "email" /
        // "account" entries above, but listing them explicitly improves
        // readability of intent and pins the contract in test (Req 4.2).
        "member_no", "member_id", "customer_id", "customer_no",
        "email_input", "email_field", "mail_address",
        "tel_input", "phone_input", "tel_no",
        "staff_id", "employee_id", "account_id",
    )
    private val PASSWORD_KEYWORDS = listOf(
        // Existing English / ASCII patterns.
        "pass", "pwd", "secret",
        // Req 1.5: Japanese hint literals classified as password candidates.
        "パスワード", "暗証番号",
    )

    // ---- Issue #66 Phase 1: custom field match key extraction --------------

    /**
     * Extract the set of "match keys" for [descriptor]. Used by the custom
     * field matcher to decide whether any of a credential's
     * [io.github.hitoshiichikawa.keynest.domain.model.CustomField] entries
     * applies to this field.
     *
     * Sources (design.md §7.1):
     *   - autofillHints (every element)
     *   - hint
     *   - idEntry
     *   - contentDescription
     *
     * Source NOT included (design.md §7.2):
     *   - text — the editable content the user is currently typing. Including
     *     it would let a credential whose fieldKey is e.g. "社員" hijack any
     *     field whose user-typed value contains "社員", which is unsafe.
     *
     * Each non-null / non-blank source string is normalised via
     * [normalizeKey] (lowercase + whitespace stripped). Blank results after
     * normalisation are dropped. Order is not significant; the return type
     * is a [Set] to deduplicate sources that normalise to the same string
     * (e.g. `idEntry="user_id"` and `hint="User ID"`).
     */
    fun extractMatchKeys(descriptor: FieldDescriptor): Set<String> {
        val out = mutableSetOf<String>()
        descriptor.autofillHints?.forEach { hint -> normalizeKey(hint).takeIf { it.isNotEmpty() }?.let(out::add) }
        normalizeKey(descriptor.hint ?: "").takeIf { it.isNotEmpty() }?.let(out::add)
        normalizeKey(descriptor.idEntry ?: "").takeIf { it.isNotEmpty() }?.let(out::add)
        normalizeKey(descriptor.contentDescription ?: "").takeIf { it.isNotEmpty() }?.let(out::add)
        return out
    }

    /**
     * Single utility used by both the customField matcher and any future
     * suggest-path (Phase 2). NFR 5: matcher and suggester must agree on
     * the normalisation algorithm.
     *
     * Spec (requirements §3 / Q1):
     *   1. lowercase()
     *   2. drop every character classified as whitespace. Kotlin's
     *      [Char.isWhitespace] already covers the full Unicode whitespace
     *      set (half-width space, full-width `　`, tab, CR, LF, NBSP, etc.).
     */
    fun normalizeKey(raw: String): String = buildString(raw.length) {
        for (c in raw) {
            if (!c.isWhitespace()) append(c.lowercaseChar())
        }
    }
}
