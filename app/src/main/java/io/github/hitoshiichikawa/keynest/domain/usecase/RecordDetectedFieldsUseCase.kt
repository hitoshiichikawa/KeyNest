package io.github.hitoshiichikawa.keynest.domain.usecase

import io.github.hitoshiichikawa.keynest.autofill.parser.AutofillFieldHeuristics
import io.github.hitoshiichikawa.keynest.data.entity.DetectedFieldSource
import io.github.hitoshiichikawa.keynest.domain.model.DetectedField
import io.github.hitoshiichikawa.keynest.domain.repository.CredentialRepository
import io.github.hitoshiichikawa.keynest.domain.repository.DetectedFieldRepository

/**
 * Records every observed editable field for [packageName] into
 * `detected_fields`, but only when KeyNest has at least one credential
 * registered for that package. Issue #67 Phase 2 (design.md §7.1).
 *
 * Gating rationale (requirements §4 Q2):
 *   - Recording every package the user opens would balloon the DB and
 *     leak a soft "app history" signal. Restricting writes to
 *     packages the user has explicitly trusted (= saved a credential
 *     for) keeps the table aligned with KeyNest's existing trust
 *     boundary.
 *   - The credential lookup is a single indexed `findByPackage`; it is
 *     cheap relative to the autofill flow it lives inside.
 *
 * Source extraction (requirements §4 Q1):
 *   - `autofillHints` (per-element), `hint`, `idEntry` (=resourceId),
 *     `contentDescription`. `text` is NOT extracted — the
 *     [AutofillFieldHeuristics.FieldDescriptor] type from Phase 1 does
 *     not even surface `text`, so the omission is enforced at the type
 *     level.
 *
 * The persisted `fieldKey` is the **raw** value (e.g. `loginEmail`) so
 * the suggestion UI can show the developer-authored label that users
 * recognise. Normalisation via [AutofillFieldHeuristics.normalizeKey]
 * is applied only to detect blanks — Phase 1's match path normalises
 * separately when comparing (NFR 5).
 */
class RecordDetectedFieldsUseCase(
    private val detectedFieldRepository: DetectedFieldRepository,
    private val credentialRepository: CredentialRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /**
     * Invoke the use case.
     *
     * @param packageName the [android.app.assist.AssistStructure.activityComponent]
     *   package name resolved by the autofill service.
     * @param descriptors descriptors of every editable ViewNode in the
     *   parsed structure (typically `parsed.customFieldCandidates.map { it.descriptor }`).
     */
    suspend operator fun invoke(
        packageName: String,
        descriptors: List<AutofillFieldHeuristics.FieldDescriptor>,
    ) {
        // Req 3.1 + §4 Q2: gate writes on the package being registered.
        // `findByPackage` is fast (`package_name` is indexed) and the
        // call lives on the fire-and-forget detection path, so the
        // latency cost is acceptable.
        if (credentialRepository.findByPackage(packageName).isEmpty()) return

        val now = clock()
        for (descriptor in descriptors) {
            recordDescriptor(packageName, descriptor, now)
        }
    }

    private suspend fun recordDescriptor(
        packageName: String,
        descriptor: AutofillFieldHeuristics.FieldDescriptor,
        now: Long,
    ) {
        // `autofillHints` is a list — emit one row per element so the
        // suggestion UI can distinguish between e.g. `username` and
        // `emailAddress` declared on the same view.
        descriptor.autofillHints?.forEach { hint ->
            upsertIfNotBlank(packageName, hint, DetectedFieldSource.AutofillHints, now)
        }
        upsertIfNotBlank(packageName, descriptor.hint, DetectedFieldSource.Hint, now)
        upsertIfNotBlank(packageName, descriptor.idEntry, DetectedFieldSource.ResourceId, now)
        upsertIfNotBlank(
            packageName,
            descriptor.contentDescription,
            DetectedFieldSource.ContentDescription,
            now,
        )
        // Note: `text` is intentionally NOT read here. The Phase 1
        // FieldDescriptor type does not expose it (design.md §0.1 /
        // §7.2). If a future Phase 1 change adds `text`, this method
        // must skip it explicitly — Req 3.6.
    }

    private suspend fun upsertIfNotBlank(
        packageName: String,
        raw: String?,
        source: DetectedFieldSource,
        now: Long,
    ) {
        // Req 3.5: drop blanks before AND after normalisation. The
        // pre-check fast-paths the common `raw == null` case without
        // touching AutofillFieldHeuristics.
        if (raw.isNullOrBlank()) return
        if (AutofillFieldHeuristics.normalizeKey(raw).isBlank()) return
        detectedFieldRepository.upsert(
            DetectedField(
                packageName = packageName,
                fieldKey = raw,
                source = source,
                lastDetectedAt = now,
            ),
        )
    }
}
