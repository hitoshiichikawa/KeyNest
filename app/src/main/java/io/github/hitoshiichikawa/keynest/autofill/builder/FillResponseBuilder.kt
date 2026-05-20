package io.github.hitoshiichikawa.keynest.autofill.builder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.inline.InlinePresentationSpec
import io.github.hitoshiichikawa.keynest.autofill.parser.AssistStructureParser.CustomFieldCandidate
import io.github.hitoshiichikawa.keynest.autofill.unlock.AutofillUnlockActivity
import io.github.hitoshiichikawa.keynest.domain.usecase.AutofillCandidate

/**
 * Constructs the [FillResponse] returned from [io.github.hitoshiichikawa.keynest.autofill.
 * KeyNestAutofillService.onFillRequest].
 *
 * Requirements: 3.3, 3.4, 5.1, NFR 1.4
 *
 * Two modes:
 *
 * - [buildLockedResponse] - vault is locked. For each candidate produces a
 *   Dataset whose values are placeholder strings + an Authentication
 *   IntentSender pointing at AutofillUnlockActivity. NO decrypted bytes are
 *   ever embedded (Req 5.1 / NFR 1.4).
 *
 * - [buildUnlockedDataset] - called after AutofillUnlockActivity has
 *   completed biometric auth and decrypted the credential. Embeds the real
 *   username + password as AutofillValues in a single Dataset (Req 3.4).
 *
 * No persistent state is held by this class - it is safe to reuse the same
 * instance across requests.
 */
class FillResponseBuilder(
    private val context: Context,
    private val presentationFactory: DatasetPresentationFactory,
) {

    /**
     * Build the locked response. One Dataset per candidate, each guarded by
     * a fresh [PendingIntent] so that selecting it launches the unlock
     * Activity.
     *
     * [customFieldCandidates] (Issue #66 Phase 1): the full list of
     * editable view candidates harvested by AssistStructureParser. Every
     * candidate's AutofillId is attached to the locked Dataset with the
     * shared placeholder so the framework allows the post-auth Dataset to
     * write to it. The actual customField match is performed AFTER unlock
     * inside [AutofillUnlockActivity] (design.md §6.4 "案 Y"): we cannot
     * match here because the customField keys live encrypted alongside
     * their values and decrypting them at the locked stage would violate
     * Req 5.2.
     *
     * If [candidates] is empty, the caller should send a null FillResponse
     * back to the framework (which yields no Autofill UI).
     */
    fun buildLockedResponse(
        candidates: List<AutofillCandidate>,
        usernameAutofillId: AutofillId?,
        passwordAutofillId: AutofillId?,
        customFieldCandidates: List<CustomFieldCandidate> = emptyList(),
        inlineSpecs: List<InlinePresentationSpec> = emptyList(),
    ): FillResponse? {
        if (candidates.isEmpty()) return null
        if (usernameAutofillId == null && passwordAutofillId == null) return null

        val builder = FillResponse.Builder()
        candidates.forEachIndexed { index, candidate ->
            // Per the InlineSuggestionsRequest contract the last spec is reused
            // for any datasets beyond the provided list size.
            val spec = inlineSpecs.getOrNull(index) ?: inlineSpecs.lastOrNull()
            val dataset = buildLockedDataset(
                candidate = candidate,
                usernameAutofillId = usernameAutofillId,
                passwordAutofillId = passwordAutofillId,
                customFieldCandidates = customFieldCandidates,
                inlineSpec = spec,
            )
            builder.addDataset(dataset)
        }
        return builder.build()
    }

    @Suppress("DEPRECATION") // setValue(...) with presentation is the API 26+ legacy form
    private fun buildLockedDataset(
        candidate: AutofillCandidate,
        usernameAutofillId: AutofillId?,
        passwordAutofillId: AutofillId?,
        customFieldCandidates: List<CustomFieldCandidate>,
        inlineSpec: InlinePresentationSpec?,
    ): Dataset {
        val presentation = presentationFactory.build(
            label = candidate.label,
            subtitle = candidate.username,
            callerPackage = null,
        )
        val inlinePresentation: InlinePresentation? = presentationFactory.buildInline(
            label = candidate.label,
            subtitle = candidate.username,
            spec = inlineSpec,
        )

        val datasetBuilder = Dataset.Builder()

        // Placeholder AutofillValues - they are NEVER shown to the user and
        // NEVER inserted into the form because the dataset has an
        // authentication IntentSender (the framework discards these and
        // waits for the auth result to supply real values).
        if (usernameAutofillId != null) {
            attachLockedValue(datasetBuilder, usernameAutofillId, presentation, inlinePresentation)
        }
        if (passwordAutofillId != null) {
            attachLockedValue(datasetBuilder, passwordAutofillId, presentation, inlinePresentation)
        }
        // Issue #66 Phase 1: also attach a placeholder for every customField
        // candidate AutofillId so the framework permits the post-auth
        // Dataset (built by AutofillUnlockActivity) to write to them. We
        // dedupe against the username/password ids to avoid attaching the
        // same value to the same id twice (the framework treats that as a
        // contract violation).
        val coveredIds = mutableSetOf<AutofillId>().apply {
            usernameAutofillId?.let { add(it) }
            passwordAutofillId?.let { add(it) }
        }
        for (cf in customFieldCandidates) {
            if (coveredIds.add(cf.autofillId)) {
                attachLockedValue(datasetBuilder, cf.autofillId, presentation, inlinePresentation)
            }
        }

        val authIntent = AutofillUnlockActivity.newIntent(
            context = context,
            credentialId = candidate.id.value,
            usernameAutofillId = usernameAutofillId,
            passwordAutofillId = passwordAutofillId,
            customFieldAutofillIds = customFieldCandidates.map { it.autofillId },
            customFieldDescriptors = customFieldCandidates.map { it.descriptor },
        )
        val pending = PendingIntent.getActivity(
            context,
            candidate.id.value.toInt(),
            authIntent,
            pendingIntentFlags(),
        )
        datasetBuilder.setAuthentication(pending.intentSender)

        return datasetBuilder.build()
    }

    /**
     * Build the Dataset that the framework consumes after successful
     * biometric auth. The actual username / password values land here.
     *
     * The 2-arg `setValue(id, value)` is used intentionally: the auth-result
     * Dataset is NOT shown in any picker (the user already selected the
     * locked Dataset), so a RemoteViews presentation is unnecessary and on
     * some Android versions the 3-arg overload causes the framework to
     * treat the result as a new pickable Dataset rather than the resolved
     * value, leaving the target form unfilled. The `label` argument is
     * kept on the signature for symmetry with the locked-side builder but
     * is not surfaced in this path.
     */
    @Suppress("UNUSED_PARAMETER")
    fun buildUnlockedDataset(
        usernameAutofillId: AutofillId?,
        usernameValue: String?,
        passwordAutofillId: AutofillId?,
        passwordValue: CharSequence?,
        label: String,
        customFieldValues: Map<AutofillId, String> = emptyMap(),
    ): Dataset {
        val builder = Dataset.Builder()
        val coveredIds = mutableSetOf<AutofillId>()
        if (usernameAutofillId != null && usernameValue != null) {
            builder.setValue(usernameAutofillId, AutofillValue.forText(usernameValue))
            coveredIds.add(usernameAutofillId)
        }
        if (passwordAutofillId != null && passwordValue != null) {
            builder.setValue(passwordAutofillId, AutofillValue.forText(passwordValue))
            coveredIds.add(passwordAutofillId)
        }
        // Issue #66 Phase 1: write customField values for any AutofillIds
        // not already covered by username/password. The same dedupe rule as
        // the locked builder applies (the framework forbids setValue twice
        // for the same id within a Dataset).
        for ((id, value) in customFieldValues) {
            if (coveredIds.add(id)) {
                builder.setValue(id, AutofillValue.forText(value))
            }
        }
        return builder.build()
    }

    @Suppress("DEPRECATION")
    private fun attachLockedValue(
        builder: Dataset.Builder,
        id: AutofillId,
        presentation: android.widget.RemoteViews,
        inlinePresentation: InlinePresentation?,
    ) {
        val value = AutofillValue.forText(PLACEHOLDER)
        if (inlinePresentation != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setValue(id, value, presentation, inlinePresentation)
        } else {
            builder.setValue(id, value, presentation)
        }
    }

    private fun pendingIntentFlags(): Int {
        // Android 12 (API 31) requires explicit mutability flags. We use
        // MUTABLE because the framework rewrites the intent with the dataset
        // auth extras before delivering it to the activity.
        val base = PendingIntent.FLAG_CANCEL_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) base or PendingIntent.FLAG_MUTABLE else base
    }

    companion object {
        /**
         * Placeholder text injected into locked Datasets. The framework
         * discards this value because [Dataset.Builder.setAuthentication]
         * gates the dataset behind an IntentSender, so this text is never
         * surfaced and never written to the target form.
         *
         * Crucially this string contains no candidate-specific data, so the
         * locked FillResponse Parcel cannot leak any of the credential's
         * actual values (NFR 1.4 / Req 5.1).
         */
        const val PLACEHOLDER = "••••••"
    }
}
