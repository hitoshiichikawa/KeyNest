package com.example.keynest.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import com.example.keynest.autofill.parser.AssistStructureParser
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/**
 * Behaviour of [AssistStructureParser].
 *
 * AssistStructure / ViewNode have hidden constructors so we mock them via
 * mockk. The parser only reads the surface fields we feed it, which makes
 * this lightweight enough to run on plain JVM JUnit.
 *
 * Covers Req 3.1 (extract username + password), 3.2 (empty when neither can
 * be identified), NFR 3.1 (exceptions swallowed).
 */
class AssistStructureParserTest {

    private val parser = AssistStructureParser()

    @Test
    fun parse_extractsBothFields_whenAutofillHintsAreExplicit() {
        // Arrange: a structure with two leaf nodes - one username, one password.
        val userId = mockk<AutofillId>()
        val passId = mockk<AutofillId>()
        val user = leaf(userId, autofillHints = arrayOf(View.AUTOFILL_HINT_USERNAME))
        val pass = leaf(passId, autofillHints = arrayOf(View.AUTOFILL_HINT_PASSWORD))
        val root = parent(children = arrayOf(user, pass))
        val structure = structureOf(root)

        val parsed = parser.parse(structure)

        assertThat(parsed.usernameId).isSameInstanceAs(userId)
        assertThat(parsed.passwordId).isSameInstanceAs(passId)
        assertThat(parsed.hasUsernameAndPassword).isTrue()
    }

    @Test
    fun parse_returnsEmpty_whenNoCandidatesFound() {
        // Two text fields that look nothing like a login form.
        val a = leaf(mockk(), inputType = InputType.TYPE_CLASS_TEXT, idEntry = "search_query")
        val b = leaf(mockk(), inputType = InputType.TYPE_CLASS_TEXT, idEntry = "comment_body")
        val root = parent(children = arrayOf(a, b))

        val parsed = parser.parse(structureOf(root))

        assertThat(parsed.isEmpty).isTrue()
        assertThat(parsed.hasUsernameAndPassword).isFalse()
    }

    @Test
    fun parse_findsPasswordOnly_whenStructureLacksUsername() {
        // Req 3.2 corollary: a partial signal still yields a deterministic result.
        val passId = mockk<AutofillId>()
        val pass = leaf(passId, inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val root = parent(children = arrayOf(pass))

        val parsed = parser.parse(structureOf(root))

        assertThat(parsed.usernameId).isNull()
        assertThat(parsed.passwordId).isSameInstanceAs(passId)
    }

    @Test
    fun parse_descendsIntoNestedChildren_upToMaxDepth() {
        // Arrange: deeply nested password field
        val passId = mockk<AutofillId>()
        val deep = leaf(passId, autofillHints = arrayOf(View.AUTOFILL_HINT_PASSWORD))
        val nested = (0..5).fold(deep as AssistStructure.ViewNode) { acc, _ ->
            parent(children = arrayOf(acc))
        }
        val root = parent(children = arrayOf(nested))

        val parsed = parser.parse(structureOf(root))

        assertThat(parsed.passwordId).isSameInstanceAs(passId)
    }

    @Test
    fun parse_swallowsExceptions_andReturnsEmpty() {
        // NFR 3.1: anything blowing up inside structure traversal must
        // surface as empty, not propagate.
        val structure = mockk<AssistStructure> {
            every { windowNodeCount } throws RuntimeException("boom")
        }

        val parsed = parser.parse(structure)

        assertThat(parsed.isEmpty).isTrue()
    }

    @Test
    fun parse_handlesEmptyStructure_gracefully() {
        val structure = mockk<AssistStructure> {
            every { windowNodeCount } returns 0
        }
        val parsed = parser.parse(structure)
        assertThat(parsed.isEmpty).isTrue()
    }

    // ---- ViewNode builders ----------------------------------------------

    private fun leaf(
        autofillId: AutofillId,
        autofillHints: Array<String>? = null,
        inputType: Int = 0,
        idEntry: String? = null,
        hint: String? = null,
    ): AssistStructure.ViewNode = mockk {
        every { this@mockk.autofillId } returns autofillId
        every { this@mockk.autofillHints } returns autofillHints
        every { this@mockk.inputType } returns inputType
        every { this@mockk.idEntry } returns idEntry
        every { this@mockk.hint } returns hint
        every { contentDescription } returns null
        every { childCount } returns 0
        every { getChildAt(any()) } returns null
    }

    private fun parent(children: Array<AssistStructure.ViewNode>): AssistStructure.ViewNode = mockk {
        every { autofillId } returns null
        every { autofillHints } returns null
        every { inputType } returns 0
        every { idEntry } returns null
        every { hint } returns null
        every { contentDescription } returns null
        every { childCount } returns children.size
        for ((i, child) in children.withIndex()) {
            every { getChildAt(i) } returns child
        }
    }

    private fun structureOf(root: AssistStructure.ViewNode): AssistStructure {
        val window = mockk<AssistStructure.WindowNode> {
            every { rootViewNode } returns root
        }
        return mockk {
            every { windowNodeCount } returns 1
            every { getWindowNodeAt(0) } returns window
        }
    }
}
