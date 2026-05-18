package io.github.hitoshiichikawa.keynest.autofill.matcher

import android.view.autofill.AutofillId
import io.github.hitoshiichikawa.keynest.autofill.parser.AssistStructureParser.CustomFieldCandidate
import io.github.hitoshiichikawa.keynest.autofill.parser.AutofillFieldHeuristics
import io.github.hitoshiichikawa.keynest.domain.model.CustomField

/**
 * Pure utility that pairs each [CustomFieldCandidate] with the value of the
 * first [CustomField] whose normalised [CustomField.fieldKey] is contained
 * in any of the candidate's normalised match keys.
 *
 * Issue #66 Phase 1. Implements the matching algorithm described in
 * design.md §8.1:
 *
 *   for each candidate:
 *     matchKeys = extractMatchKeys(candidate.descriptor)
 *     for each (i, customField) in customFields.withIndex():
 *       normalizedKey = normalizeKey(customField.fieldKey)
 *       if normalizedKey is blank: continue
 *       if any matchKey in matchKeys contains normalizedKey:
 *         output (candidate.autofillId -> customField.value); break
 *
 * Properties:
 * - Req 4.4 (determinism on conflict): the inner loop is broken on the
 *   FIRST matching customField, so the credential's list order picks the
 *   winner.
 * - Req 4.5: the outer loop continues after a match, so the same
 *   customField may apply to multiple fields.
 * - Returns a `Map<AutofillId, String>` keyed by AutofillId — the same
 *   AutofillId is never paired with two different values.
 *
 * Kept as an object (no state) so it can be invoked from
 * AutofillUnlockActivity after unlock without DI ceremony.
 */
object CustomFieldMatcher {

    fun match(
        candidates: List<CustomFieldCandidate>,
        customFields: List<CustomField>,
    ): Map<AutofillId, String> {
        if (candidates.isEmpty() || customFields.isEmpty()) return emptyMap()

        // Pre-compute the normalised fieldKey for each customField so the
        // O(N) inner loop doesn't re-normalise on every candidate.
        val normalisedKeys: List<Pair<String, String>> = customFields.mapNotNull { field ->
            val normalised = AutofillFieldHeuristics.normalizeKey(field.fieldKey)
            if (normalised.isEmpty()) null else normalised to field.value
        }
        if (normalisedKeys.isEmpty()) return emptyMap()

        val out = mutableMapOf<AutofillId, String>()
        for (candidate in candidates) {
            val matchKeys = AutofillFieldHeuristics.extractMatchKeys(candidate.descriptor)
            if (matchKeys.isEmpty()) continue
            for ((normalizedFieldKey, value) in normalisedKeys) {
                if (matchKeys.any { it.contains(normalizedFieldKey) }) {
                    out[candidate.autofillId] = value
                    break // Req 4.4: first match wins
                }
            }
        }
        return out
    }
}
