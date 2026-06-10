package io.github.hitoshiichikawa.keynest.autofill.unlock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import io.github.hitoshiichikawa.keynest.auth.AuthResult
import io.github.hitoshiichikawa.keynest.auth.BiometricAuthenticator
import io.github.hitoshiichikawa.keynest.autofill.builder.DatasetPresentationFactory
import io.github.hitoshiichikawa.keynest.autofill.builder.FillResponseBuilder
import io.github.hitoshiichikawa.keynest.autofill.matcher.CustomFieldMatcher
import io.github.hitoshiichikawa.keynest.autofill.parser.AssistStructureParser
import io.github.hitoshiichikawa.keynest.autofill.parser.AutofillFieldHeuristics
import io.github.hitoshiichikawa.keynest.di.ServiceLocator
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.util.SafeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

/**
 * Receives the Authentication IntentSender embedded in a locked Dataset
 * (see [FillResponseBuilder.buildLockedResponse]).
 *
 * Requirements: 5.2, 5.3, 5.4, 5.5
 *
 * Flow:
 *   1. Pull credentialId + AutofillId pair out of the launch intent.
 *   2. Launch BiometricPrompt (BIOMETRIC_STRONG | DEVICE_CREDENTIAL).
 *   3. On success: decrypt via UnlockVaultUseCase, build a Dataset with the
 *      real username + password values, set it as EXTRA_AUTHENTICATION_RESULT
 *      on the returned Intent and finish with RESULT_OK.
 *   4. On failure / cancel: finish with RESULT_CANCELED (no extras).
 *   5. Always close the PlaintextCredential before finishing so the
 *      CharArray is zero-filled (Req 5.5).
 */
class AutofillUnlockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialise ServiceLocator defensively - on hostile process boots the
        // Application may not yet have been created when an authentication
        // intent fires.
        ServiceLocator.initialize(applicationContext)

        val credentialId = intent?.getLongExtra(EXTRA_CREDENTIAL_ID, INVALID_ID) ?: INVALID_ID
        val usernameAutofillId: AutofillId? = intent?.getAutofillIdExtra(EXTRA_USERNAME_AUTOFILL_ID)
        val passwordAutofillId: AutofillId? = intent?.getAutofillIdExtra(EXTRA_PASSWORD_AUTOFILL_ID)
        val customFieldCandidates: List<AssistStructureParser.CustomFieldCandidate> =
            readCustomFieldCandidates(intent)

        // Req 5.1 / NFR 2.2: log counts only, never the underlying
        // descriptor strings (third-party app field shapes are private to
        // the user).
        SafeLogger.info(
            tag = TAG,
            message = "unlock launched id=$credentialId userIdPresent=${usernameAutofillId != null} " +
                "passIdPresent=${passwordAutofillId != null} " +
                "customFieldCandidates=${customFieldCandidates.size}",
        )

        if (credentialId == INVALID_ID) {
            SafeLogger.warn(tag = TAG, message = "unlock aborted: missing credentialId")
            finishWithCancel(); return
        }

        runUnlockFlow(credentialId, usernameAutofillId, passwordAutofillId, customFieldCandidates)
    }

    private fun runUnlockFlow(
        credentialId: Long,
        usernameAutofillId: AutofillId?,
        passwordAutofillId: AutofillId?,
        customFieldCandidates: List<AssistStructureParser.CustomFieldCandidate>,
    ) {
        lifecycleScope.launch {
            val authenticator = BiometricAuthenticator(this@AutofillUnlockActivity as FragmentActivity)
            val authResult = authenticator.authenticate(
                title = getString(io.github.hitoshiichikawa.keynest.R.string.biometric_prompt_title),
                subtitle = getString(io.github.hitoshiichikawa.keynest.R.string.biometric_prompt_subtitle),
            )
            when (authResult) {
                AuthResult.Succeeded -> {
                    SafeLogger.info(tag = TAG, message = "auth succeeded; decrypting credentialId=$credentialId")
                    val decryptResult = ServiceLocator.unlockVaultUseCase(CredentialId(credentialId))
                    val plain = decryptResult.getOrNull()
                    if (plain == null) {
                        SafeLogger.warn(
                            tag = TAG,
                            message = "decrypt failed",
                            throwable = decryptResult.exceptionOrNull(),
                        )
                        finishWithCancel(); return@launch
                    }
                    try {
                        val builder = FillResponseBuilder(
                            applicationContext,
                            DatasetPresentationFactory(applicationContext),
                        )
                        // Issue #66 Phase 1: now that the credential is
                        // decrypted, the matcher can pair customField
                        // candidates with the decrypted fieldKeys/values.
                        // Wrapped in try/catch so a matcher bug does not
                        // abort the username/password fill (fail-open).
                        val customFieldValues: Map<AutofillId, String> = try {
                            CustomFieldMatcher.match(
                                candidates = customFieldCandidates,
                                customFields = plain.customFields,
                            )
                        } catch (t: Throwable) {
                            SafeLogger.warn(
                                tag = TAG,
                                message = "customField match failed; proceeding without customField values",
                                throwable = t,
                            )
                            emptyMap()
                        }

                        val dataset: Dataset = builder.buildUnlockedDataset(
                            usernameAutofillId = usernameAutofillId,
                            usernameValue = plain.username,
                            passwordAutofillId = passwordAutofillId,
                            passwordValue = String(plain.password),
                            label = plain.label,
                            customFieldValues = customFieldValues,
                        )
                        val replyIntent = Intent().apply {
                            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset as android.os.Parcelable)
                        }
                        setResult(RESULT_OK, replyIntent)
                        // NFR 2.2 / Req 5.1: presence boolean と件数のみ。
                        // username / password の「長さ」も秘匿情報のメタデータ
                        // （総当たり探索空間を狭める）なので出力しない（#137）。
                        SafeLogger.info(
                            tag = TAG,
                            message = "unlock returning dataset (" +
                                "userPresent=${plain.username.isNotEmpty()}, " +
                                "userIdPresent=${usernameAutofillId != null}, " +
                                "passIdPresent=${passwordAutofillId != null}, " +
                                "customFieldsMatched=${customFieldValues.size}, " +
                                "fwResultPresent=${intent?.hasExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT) == true})",
                        )
                        // Issue #9 Req 3.2 / NFR 2.2: stamp lastUsedAt for
                        // the carousel feed AFTER the dataset is committed
                        // to setResult but BEFORE plain.close(). The launch
                        // is NonCancellable so finish() triggering Activity
                        // teardown does not cancel the update mid-write.
                        // We deliberately do NOT await it so the autofill
                        // response timing (MVP NFR 2.1 = 300ms median for
                        // onFillRequest, equivalent ceiling here) is not
                        // affected.
                        launch(Dispatchers.IO + NonCancellable) {
                            ServiceLocator.markCredentialUsedUseCase(CredentialId(credentialId))
                                .onFailure { ex ->
                                    // NFR 1.2 / NFR 1.3: log the failure
                                    // class only, not the credentialId
                                    // payload or any plaintext.
                                    SafeLogger.warn(
                                        tag = TAG,
                                        message = "markUsed failed",
                                        throwable = ex,
                                    )
                                }
                        }
                    } finally {
                        // Req 5.5: zero-fill the CharArray before finishing.
                        plain.close()
                    }
                    finish()
                }
                AuthResult.Cancelled, is AuthResult.Failed, is AuthResult.Unavailable -> {
                    SafeLogger.info(tag = TAG, message = "auth not succeeded: $authResult")
                    finishWithCancel()
                }
            }
        }
    }

    private fun finishWithCancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun Intent.getAutofillIdExtra(key: String): AutofillId? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, AutofillId::class.java)
        } else {
            getParcelableExtra(key) as? AutofillId
        }
    }

    /**
     * Re-hydrate the [AssistStructureParser.CustomFieldCandidate] list from
     * the extras layout produced by [newIntent] in T10.
     *
     * Wrapped in try/catch so any framework deserialisation error is
     * swallowed into an empty list — the customField path is best-effort
     * and must not abort username/password fill.
     */
    @Suppress("DEPRECATION")
    private fun readCustomFieldCandidates(
        source: Intent?,
    ): List<AssistStructureParser.CustomFieldCandidate> {
        if (source == null) return emptyList()
        return try {
            val ids: List<AutofillId> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                source.getParcelableArrayListExtra(EXTRA_CUSTOM_FIELD_AUTOFILL_IDS, AutofillId::class.java)
                    .orEmpty()
            } else {
                source.getParcelableArrayListExtra<AutofillId>(EXTRA_CUSTOM_FIELD_AUTOFILL_IDS).orEmpty()
            }
            if (ids.isEmpty()) return emptyList()
            val hints = source.getStringArrayExtra(EXTRA_CUSTOM_FIELD_HINTS).orEmpty()
            val idEntries = source.getStringArrayExtra(EXTRA_CUSTOM_FIELD_ID_ENTRIES).orEmpty()
            val contentDescs = source.getStringArrayExtra(EXTRA_CUSTOM_FIELD_CONTENT_DESCRIPTIONS).orEmpty()
            val flatHints = source.getStringArrayExtra(EXTRA_CUSTOM_FIELD_AUTOFILL_HINTS_FLAT) ?: emptyArray()
            val hintLengths: IntArray = source.getIntArrayExtra(EXTRA_CUSTOM_FIELD_AUTOFILL_HINTS_LENGTHS) ?: IntArray(0)
            // Defensive: if any of the parallel arrays is missing / shorter
            // than ids, fall back gracefully.
            if (hints.size != ids.size || idEntries.size != ids.size ||
                contentDescs.size != ids.size || hintLengths.size != ids.size
            ) {
                SafeLogger.warn(
                    tag = TAG,
                    message = "customField extras shape mismatch; skipping customField matching",
                )
                return emptyList()
            }
            val out = ArrayList<AssistStructureParser.CustomFieldCandidate>(ids.size)
            var hintCursor = 0
            for (idx in ids.indices) {
                val autofillId = ids[idx]
                val perFieldLen = hintLengths[idx]
                val autofillHints: List<String>? = if (perFieldLen < 0) {
                    null
                } else {
                    val end = hintCursor + perFieldLen
                    val slice = flatHints.copyOfRange(hintCursor, end).toList()
                    hintCursor = end
                    slice
                }
                out.add(
                    AssistStructureParser.CustomFieldCandidate(
                        autofillId = autofillId,
                        descriptor = AutofillFieldHeuristics.FieldDescriptor(
                            autofillHints = autofillHints,
                            inputType = 0, // not transported; not needed for match key extraction
                            idEntry = idEntries[idx].takeIf { it.isNotEmpty() },
                            hint = hints[idx].takeIf { it.isNotEmpty() },
                            contentDescription = contentDescs[idx].takeIf { it.isNotEmpty() },
                        ),
                    ),
                )
            }
            out
        } catch (t: Throwable) {
            SafeLogger.warn(
                tag = TAG,
                message = "readCustomFieldCandidates failed; skipping",
                throwable = t,
            )
            emptyList()
        }
    }

    companion object {
        private const val EXTRA_CREDENTIAL_ID = "io.github.hitoshiichikawa.keynest.extra.CREDENTIAL_ID"
        private const val EXTRA_USERNAME_AUTOFILL_ID = "io.github.hitoshiichikawa.keynest.extra.USERNAME_AUTOFILL_ID"
        private const val EXTRA_PASSWORD_AUTOFILL_ID = "io.github.hitoshiichikawa.keynest.extra.PASSWORD_AUTOFILL_ID"
        internal const val EXTRA_CUSTOM_FIELD_AUTOFILL_IDS =
            "io.github.hitoshiichikawa.keynest.extra.CUSTOM_FIELD_AUTOFILL_IDS"
        internal const val EXTRA_CUSTOM_FIELD_HINTS =
            "io.github.hitoshiichikawa.keynest.extra.CUSTOM_FIELD_HINTS"
        internal const val EXTRA_CUSTOM_FIELD_ID_ENTRIES =
            "io.github.hitoshiichikawa.keynest.extra.CUSTOM_FIELD_ID_ENTRIES"
        internal const val EXTRA_CUSTOM_FIELD_CONTENT_DESCRIPTIONS =
            "io.github.hitoshiichikawa.keynest.extra.CUSTOM_FIELD_CONTENT_DESCRIPTIONS"
        internal const val EXTRA_CUSTOM_FIELD_AUTOFILL_HINTS_FLAT =
            "io.github.hitoshiichikawa.keynest.extra.CUSTOM_FIELD_AUTOFILL_HINTS_FLAT"
        internal const val EXTRA_CUSTOM_FIELD_AUTOFILL_HINTS_LENGTHS =
            "io.github.hitoshiichikawa.keynest.extra.CUSTOM_FIELD_AUTOFILL_HINTS_LENGTHS"
        private const val INVALID_ID = -1L
        private const val TAG = "KeyNest.Unlock"

        /**
         * Build the auth-PendingIntent target. Issue #66 Phase 1 added the
         * customField parameters; both default to empty so call sites that
         * don't yet care about customFields stay source-compatible.
         *
         * Encoding the descriptor list across Intent extras:
         * - Per-field strings (idEntry / hint / contentDescription) are
         *   carried as parallel String arrays indexed by AutofillId.
         * - autofillHints is itself a `List<String>?` so it is flattened
         *   into a single String[] plus a parallel IntArray of per-field
         *   lengths so the receiver can re-slice it. `null` is represented
         *   by length `-1`.
         */
        fun newIntent(
            context: Context,
            credentialId: Long,
            usernameAutofillId: AutofillId?,
            passwordAutofillId: AutofillId?,
            customFieldAutofillIds: List<AutofillId> = emptyList(),
            customFieldDescriptors: List<AutofillFieldHeuristics.FieldDescriptor> = emptyList(),
        ): Intent {
            require(customFieldAutofillIds.size == customFieldDescriptors.size) {
                "customFieldAutofillIds and customFieldDescriptors must have the same length"
            }
            // Intentionally NOT adding FLAG_ACTIVITY_NEW_TASK: the framework
            // launches this PendingIntent and manages task affinity itself.
            // Forcing a NEW_TASK detaches the unlock activity from the
            // originating app's task and breaks the framework's ability to
            // apply the EXTRA_AUTHENTICATION_RESULT Dataset back to the
            // original form on some Android versions.
            return Intent(context, AutofillUnlockActivity::class.java).apply {
                putExtra(EXTRA_CREDENTIAL_ID, credentialId)
                usernameAutofillId?.let { putExtra(EXTRA_USERNAME_AUTOFILL_ID, it as android.os.Parcelable) }
                passwordAutofillId?.let { putExtra(EXTRA_PASSWORD_AUTOFILL_ID, it as android.os.Parcelable) }
                if (customFieldAutofillIds.isNotEmpty()) {
                    val idsArr = ArrayList<android.os.Parcelable>(customFieldAutofillIds.size).apply {
                        customFieldAutofillIds.forEach { add(it as android.os.Parcelable) }
                    }
                    putParcelableArrayListExtra(EXTRA_CUSTOM_FIELD_AUTOFILL_IDS, idsArr)
                    putExtra(
                        EXTRA_CUSTOM_FIELD_HINTS,
                        customFieldDescriptors.map { it.hint ?: "" }.toTypedArray(),
                    )
                    putExtra(
                        EXTRA_CUSTOM_FIELD_ID_ENTRIES,
                        customFieldDescriptors.map { it.idEntry ?: "" }.toTypedArray(),
                    )
                    putExtra(
                        EXTRA_CUSTOM_FIELD_CONTENT_DESCRIPTIONS,
                        customFieldDescriptors.map { it.contentDescription ?: "" }.toTypedArray(),
                    )
                    val flatHints = mutableListOf<String>()
                    val lengths = IntArray(customFieldDescriptors.size)
                    customFieldDescriptors.forEachIndexed { idx, descriptor ->
                        val hints = descriptor.autofillHints
                        if (hints == null) {
                            lengths[idx] = -1
                        } else {
                            lengths[idx] = hints.size
                            flatHints.addAll(hints)
                        }
                    }
                    putExtra(EXTRA_CUSTOM_FIELD_AUTOFILL_HINTS_FLAT, flatHints.toTypedArray())
                    putExtra(EXTRA_CUSTOM_FIELD_AUTOFILL_HINTS_LENGTHS, lengths)
                }
            }
        }
    }
}
