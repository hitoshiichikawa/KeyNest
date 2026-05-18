package io.github.hitoshiichikawa.keynest.autofill.parser

import android.app.assist.AssistStructure
import android.view.autofill.AutofillId

/**
 * Walks an [AssistStructure] and extracts the most likely username /
 * password [AutofillId]s, plus the caller package name.
 *
 * Requirements: 3.1, 3.2, NFR 3.1
 *
 * Guard rails:
 * - Maximum [MAX_NODES] nodes are visited per parse.
 * - Maximum depth [MAX_DEPTH] is enforced via the recursion stack.
 * - All exceptions are swallowed and surface as an empty [ParsedFields]. This
 *   matches NFR 3.1 - the AutofillService MUST gracefully degrade if a
 *   third-party app exposes a malformed structure.
 */
internal class AssistStructureParser {

    /** Result of a structure scan. Either field may be null. */
    data class ParsedFields(
        val usernameId: AutofillId?,
        val passwordId: AutofillId?,
    ) {
        val hasUsernameAndPassword: Boolean get() = usernameId != null && passwordId != null
        val isEmpty: Boolean get() = usernameId == null && passwordId == null
    }

    fun parse(structure: AssistStructure): ParsedFields {
        var visitedNodes = 0
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null

        return try {
            for (windowIndex in 0 until structure.windowNodeCount) {
                val window = structure.getWindowNodeAt(windowIndex)
                val root = window.rootViewNode ?: continue
                val stack = ArrayDeque<Pair<AssistStructure.ViewNode, Int>>()
                stack.addLast(root to 0)

                while (stack.isNotEmpty()) {
                    if (visitedNodes >= MAX_NODES) break
                    val (node, depth) = stack.removeLast()
                    visitedNodes++

                    if (node.autofillId != null) {
                        val role = AutofillFieldHeuristics.classify(toDescriptor(node))
                        when (role) {
                            AutofillFieldHeuristics.Role.Username -> if (usernameId == null) usernameId = node.autofillId
                            AutofillFieldHeuristics.Role.Password -> if (passwordId == null) passwordId = node.autofillId
                            AutofillFieldHeuristics.Role.Unknown -> {}
                        }
                        if (usernameId != null && passwordId != null) break
                    }

                    if (depth + 1 <= MAX_DEPTH) {
                        for (i in 0 until node.childCount) {
                            val child = node.getChildAt(i) ?: continue
                            stack.addLast(child to (depth + 1))
                        }
                    }
                }
                if (usernameId != null && passwordId != null) break
            }
            ParsedFields(usernameId = usernameId, passwordId = passwordId)
        } catch (_: Throwable) {
            // NFR 3.1: never propagate, surface as empty result.
            ParsedFields(usernameId = null, passwordId = null)
        }
    }

    private fun toDescriptor(node: AssistStructure.ViewNode): AutofillFieldHeuristics.FieldDescriptor =
        AutofillFieldHeuristics.FieldDescriptor(
            autofillHints = node.autofillHints?.toList(),
            inputType = node.inputType,
            idEntry = node.idEntry,
            hint = node.hint,
            contentDescription = node.contentDescription?.toString(),
        )

    companion object {
        const val MAX_NODES = 500
        const val MAX_DEPTH = 50
    }
}
