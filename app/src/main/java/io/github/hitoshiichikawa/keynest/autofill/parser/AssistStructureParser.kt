package io.github.hitoshiichikawa.keynest.autofill.parser

import android.app.assist.AssistStructure
import android.view.autofill.AutofillId

/**
 * Walks an [AssistStructure] and extracts the most likely username /
 * password [AutofillId]s, the caller package name, and the set of
 * candidate fields for custom-field matching (Issue #66 Phase 1).
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
class AssistStructureParser {

    /**
     * One editable view harvested during the walk. Used as input to the
     * custom-field matcher (executed AFTER unlock — see design.md §6.4).
     *
     * Holds the same [AutofillFieldHeuristics.FieldDescriptor] shape used
     * by the existing classifier so the matcher and classifier agree on
     * what each view looks like.
     */
    data class CustomFieldCandidate(
        val autofillId: AutofillId,
        val descriptor: AutofillFieldHeuristics.FieldDescriptor,
    )

    /** Result of a structure scan. Either field may be null. */
    data class ParsedFields(
        val usernameId: AutofillId?,
        val passwordId: AutofillId?,
        val customFieldCandidates: List<CustomFieldCandidate> = emptyList(),
    ) {
        val hasUsernameAndPassword: Boolean get() = usernameId != null && passwordId != null

        /**
         * Pre-Issue #66 contract: empty means neither username nor password
         * was identified. customFieldCandidates intentionally do NOT
         * participate in this flag — callers (KeyNestAutofillService)
         * branch on it to decide whether to short-circuit, and the existing
         * locked-FillResponse path still requires at least one of
         * username/password (design.md §8.3). The new customField match
         * happens later inside AutofillUnlockActivity, not here.
         */
        val isEmpty: Boolean get() = usernameId == null && passwordId == null
    }

    fun parse(structure: AssistStructure): ParsedFields {
        var visitedNodes = 0
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        val customCandidates = mutableListOf<CustomFieldCandidate>()

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

                    val nodeAutofillId = node.autofillId
                    if (nodeAutofillId != null) {
                        val descriptor = toDescriptor(node)
                        val role = AutofillFieldHeuristics.classify(descriptor)
                        when (role) {
                            AutofillFieldHeuristics.Role.Username -> if (usernameId == null) usernameId = nodeAutofillId
                            AutofillFieldHeuristics.Role.Password -> if (passwordId == null) passwordId = nodeAutofillId
                            AutofillFieldHeuristics.Role.Unknown -> {}
                        }
                        // Issue #66 Phase 1: every editable view (regardless
                        // of role) is also a customField candidate. The
                        // matcher decides at unlock time whether any
                        // credential customField applies — design.md §8.2.
                        //
                        // We intentionally do NOT break early here. The
                        // existing username + password short-circuit
                        // (preserved below) would prevent harvesting
                        // customField candidates that appear later in the
                        // structure. With customField support, we must visit
                        // the whole tree (capped by MAX_NODES).
                        customCandidates.add(
                            CustomFieldCandidate(
                                autofillId = nodeAutofillId,
                                descriptor = descriptor,
                            ),
                        )
                    }

                    if (depth + 1 <= MAX_DEPTH) {
                        for (i in 0 until node.childCount) {
                            val child = node.getChildAt(i) ?: continue
                            stack.addLast(child to (depth + 1))
                        }
                    }
                }
            }
            ParsedFields(
                usernameId = usernameId,
                passwordId = passwordId,
                customFieldCandidates = customCandidates.toList(),
            )
        } catch (_: Throwable) {
            // NFR 3.1: never propagate, surface as empty result.
            ParsedFields(usernameId = null, passwordId = null, customFieldCandidates = emptyList())
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
