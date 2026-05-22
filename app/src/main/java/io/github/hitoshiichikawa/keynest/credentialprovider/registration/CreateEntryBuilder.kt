package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.CreateEntry
import io.github.hitoshiichikawa.keynest.R
import java.security.SecureRandom

/**
 * Builds the [CreateEntry] that the OS Credential Manager surface displays
 * when KeyNest is offered as a PassKey storage candidate
 * (Issue #99 / parent #89 / design §4.7).
 *
 * The pending intent points at [PasskeyCreateActivity] and uses
 * `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT` per Android security guidance.
 * A per-request token (`SecureRandom().nextInt()`) is embedded in the
 * Intent's data Uri so that concurrent registration requests do not
 * collide on `PendingIntent` identity (design §4.8).
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class CreateEntryBuilder(
    private val context: Context,
    private val secureRandom: SecureRandom = SecureRandom(),
) {

    @Suppress("UNUSED_PARAMETER")
    fun build(request: BeginCreatePublicKeyCredentialRequest): CreateEntry {
        val accountName = context.getString(R.string.passkey_create_entry_account_name)
        val description = context.getString(R.string.passkey_create_entry_description)

        val token = secureRandom.nextInt()
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            PasskeyCreateActivity.intent(context, token),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // The Activity re-reads the OS request via
        // PendingIntentHandler.retrieveProviderCreateCredentialRequest(...),
        // so the `request` parameter is intentionally not consumed here.
        // We keep it in the signature so future revisions (e.g. RP display
        // name in the entry description) don't break callers.
        return CreateEntry.Builder(accountName, pendingIntent)
            .setDescription(description)
            .build()
    }

    companion object {
        /**
         * The PendingIntent requestCode is reused for all PassKey entries —
         * `FLAG_UPDATE_CURRENT` ensures the most-recent token wins, and the
         * per-request token in the data Uri guarantees binder uniqueness.
         */
        private const val REQUEST_CODE = 0xCE99
    }
}
