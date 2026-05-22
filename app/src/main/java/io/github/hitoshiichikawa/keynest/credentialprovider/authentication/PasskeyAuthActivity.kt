package io.github.hitoshiichikawa.keynest.credentialprovider.authentication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity launched by the [PublicKeyCredentialEntry][androidx.credentials.provider.PublicKeyCredentialEntry]
 * pending intent (see [GetEntryBuilder]) for the authentication ceremony
 * (Issue #100 / parent #89 / design §4.6 / §5.2 / §9.3).
 *
 * **T-03 stub**: this file currently only carries the `companion object`
 * factories needed by [GetEntryBuilder] to compile and to populate the
 * `PendingIntent` target / data Uri. The full `runAuthenticationFlow` body
 * (BiometricPrompt → signWithIncrement → PasskeyAssertion.sign → wipe →
 * setGetCredentialResponse) is filled in by T-04.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyAuthActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // T-04 fills in: ServiceLocator init, retrieveProviderGetCredentialRequest,
        // BiometricPrompt, signWithIncrement, loadPrivateKey, PasskeyAssertion.sign,
        // AuthenticationResponseJSON, PendingIntentHandler.setGetCredentialResponse.
        finish()
    }

    companion object {
        internal const val INTENT_DATA_SCHEME = "keynest"
        internal const val INTENT_DATA_AUTHORITY = "passkey"
        internal const val INTENT_DATA_PATH_PREFIX = "/auth/"

        private const val PENDING_INTENT_REQUEST_CODE = 0xCE9A

        /**
         * Builds the Intent that targets this Activity with the credentialId
         * encoded in the data Uri (`keynest://passkey/auth/<credentialId>`).
         * Mirrors `PasskeyCreateActivity.intent(...)` so concurrent entries
         * stay binder-distinct under `FLAG_UPDATE_CURRENT`.
         */
        @VisibleForTesting
        internal fun intent(context: Context, credentialId: String): Intent =
            Intent(context, PasskeyAuthActivity::class.java).apply {
                data = Uri.Builder()
                    .scheme(INTENT_DATA_SCHEME)
                    .authority(INTENT_DATA_AUTHORITY)
                    .path("$INTENT_DATA_PATH_PREFIX$credentialId")
                    .build()
            }

        /** PendingIntent factory used by [GetEntryBuilder]. */
        @VisibleForTesting
        internal fun pendingIntent(context: Context, credentialId: String): PendingIntent =
            PendingIntent.getActivity(
                context,
                PENDING_INTENT_REQUEST_CODE,
                intent(context, credentialId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** Extracts the credentialId from an Intent built by [intent]. */
        @VisibleForTesting
        internal fun credentialIdFromIntent(intent: Intent): String? {
            val data = intent.data ?: return null
            if (data.scheme != INTENT_DATA_SCHEME) return null
            if (data.authority != INTENT_DATA_AUTHORITY) return null
            val path = data.path ?: return null
            if (!path.startsWith(INTENT_DATA_PATH_PREFIX)) return null
            val suffix = path.removePrefix(INTENT_DATA_PATH_PREFIX)
            return suffix.takeIf { it.isNotEmpty() }
        }
    }
}
