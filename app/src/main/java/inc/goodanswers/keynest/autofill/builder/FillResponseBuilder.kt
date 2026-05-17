package inc.goodanswers.keynest.autofill.builder

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
import inc.goodanswers.keynest.autofill.unlock.AutofillUnlockActivity
import inc.goodanswers.keynest.domain.usecase.AutofillCandidate

/**
 * Constructs the [FillResponse] returned from [inc.goodanswers.keynest.autofill.
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
     * If [candidates] is empty, the caller should send a null FillResponse
     * back to the framework (which yields no Autofill UI).
     */
    fun buildLockedResponse(
        candidates: List<AutofillCandidate>,
        usernameAutofillId: AutofillId?,
        passwordAutofillId: AutofillId?,
        inlineSpecs: List<InlinePresentationSpec> = emptyList(),
    ): FillResponse? {
        if (candidates.isEmpty()) return null
        if (usernameAutofillId == null && passwordAutofillId == null) return null

        val builder = FillResponse.Builder()
        candidates.forEachIndexed { index, candidate ->
            // Per the InlineSuggestionsRequest contract the last spec is reused
            // for any datasets beyond the provided list size.
            val spec = inlineSpecs.getOrNull(index) ?: inlineSpecs.lastOrNull()
            val dataset = buildLockedDataset(candidate, usernameAutofillId, passwordAutofillId, spec)
            builder.addDataset(dataset)
        }
        return builder.build()
    }

    @Suppress("DEPRECATION") // setValue(...) with presentation is the API 26+ legacy form
    private fun buildLockedDataset(
        candidate: AutofillCandidate,
        usernameAutofillId: AutofillId?,
        passwordAutofillId: AutofillId?,
        inlineSpec: InlinePresentationSpec?,
    ): Dataset {
        val presentation = presentationFactory.build(
            label = candidate.label,
            subtitle = candidate.username,
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

        val authIntent = AutofillUnlockActivity.newIntent(
            context = context,
            credentialId = candidate.id.value,
            usernameAutofillId = usernameAutofillId,
            passwordAutofillId = passwordAutofillId,
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
    ): Dataset {
        val builder = Dataset.Builder()
        if (usernameAutofillId != null && usernameValue != null) {
            builder.setValue(usernameAutofillId, AutofillValue.forText(usernameValue))
        }
        if (passwordAutofillId != null && passwordValue != null) {
            builder.setValue(passwordAutofillId, AutofillValue.forText(passwordValue))
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
