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

        SafeLogger.info(
            tag = TAG,
            message = "unlock launched id=$credentialId userIdPresent=${usernameAutofillId != null} " +
                "passIdPresent=${passwordAutofillId != null}",
        )

        if (credentialId == INVALID_ID) {
            SafeLogger.warn(tag = TAG, message = "unlock aborted: missing credentialId")
            finishWithCancel(); return
        }

        runUnlockFlow(credentialId, usernameAutofillId, passwordAutofillId)
    }

    private fun runUnlockFlow(
        credentialId: Long,
        usernameAutofillId: AutofillId?,
        passwordAutofillId: AutofillId?,
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
                        val dataset: Dataset = builder.buildUnlockedDataset(
                            usernameAutofillId = usernameAutofillId,
                            usernameValue = plain.username,
                            passwordAutofillId = passwordAutofillId,
                            passwordValue = String(plain.password),
                            label = plain.label,
                        )
                        val replyIntent = Intent().apply {
                            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset as android.os.Parcelable)
                        }
                        setResult(RESULT_OK, replyIntent)
                        SafeLogger.info(
                            tag = TAG,
                            message = "unlock returning dataset (userLen=${plain.username.length} " +
                                "passLen=${plain.password.size}, userIdPresent=${usernameAutofillId != null}, " +
                                "passIdPresent=${passwordAutofillId != null}, " +
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

    companion object {
        private const val EXTRA_CREDENTIAL_ID = "io.github.hitoshiichikawa.keynest.extra.CREDENTIAL_ID"
        private const val EXTRA_USERNAME_AUTOFILL_ID = "io.github.hitoshiichikawa.keynest.extra.USERNAME_AUTOFILL_ID"
        private const val EXTRA_PASSWORD_AUTOFILL_ID = "io.github.hitoshiichikawa.keynest.extra.PASSWORD_AUTOFILL_ID"
        private const val INVALID_ID = -1L
        private const val TAG = "KeyNest.Unlock"

        fun newIntent(
            context: Context,
            credentialId: Long,
            usernameAutofillId: AutofillId?,
            passwordAutofillId: AutofillId?,
        ): Intent {
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
            }
        }
    }
}
