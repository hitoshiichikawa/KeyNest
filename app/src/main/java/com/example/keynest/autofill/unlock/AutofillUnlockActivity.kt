package com.example.keynest.autofill.unlock

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
import com.example.keynest.auth.AuthResult
import com.example.keynest.auth.BiometricAuthenticator
import com.example.keynest.autofill.builder.DatasetPresentationFactory
import com.example.keynest.autofill.builder.FillResponseBuilder
import com.example.keynest.di.ServiceLocator
import com.example.keynest.domain.model.CredentialId
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

        if (credentialId == INVALID_ID) {
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
                title = getString(com.example.keynest.R.string.biometric_prompt_title),
                subtitle = getString(com.example.keynest.R.string.biometric_prompt_subtitle),
            )
            when (authResult) {
                AuthResult.Succeeded -> {
                    val decryptResult = ServiceLocator.unlockVaultUseCase(CredentialId(credentialId))
                    val plain = decryptResult.getOrNull()
                    if (plain == null) {
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
                    } finally {
                        // Req 5.5: zero-fill the CharArray before finishing.
                        plain.close()
                    }
                    finish()
                }
                AuthResult.Cancelled, is AuthResult.Failed, is AuthResult.Unavailable -> {
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
        private const val EXTRA_CREDENTIAL_ID = "com.example.keynest.extra.CREDENTIAL_ID"
        private const val EXTRA_USERNAME_AUTOFILL_ID = "com.example.keynest.extra.USERNAME_AUTOFILL_ID"
        private const val EXTRA_PASSWORD_AUTOFILL_ID = "com.example.keynest.extra.PASSWORD_AUTOFILL_ID"
        private const val INVALID_ID = -1L

        fun newIntent(
            context: Context,
            credentialId: Long,
            usernameAutofillId: AutofillId?,
            passwordAutofillId: AutofillId?,
        ): Intent {
            return Intent(context, AutofillUnlockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_CREDENTIAL_ID, credentialId)
                usernameAutofillId?.let { putExtra(EXTRA_USERNAME_AUTOFILL_ID, it as android.os.Parcelable) }
                passwordAutofillId?.let { putExtra(EXTRA_PASSWORD_AUTOFILL_ID, it as android.os.Parcelable) }
            }
        }
    }
}
