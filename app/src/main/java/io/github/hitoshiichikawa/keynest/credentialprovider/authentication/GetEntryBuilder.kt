package io.github.hitoshiichikawa.keynest.credentialprovider.authentication

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.PublicKeyCredentialEntry
import io.github.hitoshiichikawa.keynest.domain.model.Passkey
import io.github.hitoshiichikawa.keynest.domain.repository.PasskeyRepository
import io.github.hitoshiichikawa.keynest.util.SafeLogger
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Builds the list of [PublicKeyCredentialEntry] that backs the OS
 * Credential Manager sheet for a single [BeginGetPublicKeyCredentialOption]
 * (Issue #100 / parent #89 / design §4.4).
 *
 * Dispatches `allowCredentials` empty/specified branches per req 1.1 / 1.2
 * by consulting [PasskeyRepository]:
 *  - empty / missing → `listDiscoverableByRpId(rpId)` (usernameless login)
 *  - specified       → `findByCredentialId(id)` for each id, kept only
 *                      when non-null AND `entity.rpId == parsedRpId`
 *                      (defensive RP-spoofing guard per design §12.3 Risk).
 *
 * Service callbacks are blocking from the OS framework's perspective, so
 * Repository calls run inside `runBlocking(Dispatchers.IO)`. The DAO point
 * lookups are ms-scale and well within ANR (NFR 5.1 / #99 same pattern).
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class GetEntryBuilder(
    private val context: Context,
    private val repository: PasskeyRepository,
    @Suppress("unused") private val secureRandom: SecureRandom = SecureRandom(),
) {

    fun build(option: BeginGetPublicKeyCredentialOption): List<PublicKeyCredentialEntry> {
        val requestJson = option.requestJson

        val rpId = try {
            AllowCredentialsParser.parseRpId(requestJson)
        } catch (t: IllegalArgumentException) {
            // rpId is mandatory; without it we cannot scope candidates so
            // we skip the entire option (design §4.4 / §9.1).
            SafeLogger.warn(tag = TAG, message = "parseRpId failed; skipping option", throwable = t)
            return emptyList()
        }

        val allowCredentialIds = AllowCredentialsParser.parseAllowCredentialIds(requestJson)

        val candidates: List<Passkey> = runBlocking(Dispatchers.IO) {
            if (allowCredentialIds.isEmpty()) {
                // Usernameless login — discoverable credentials only.
                repository.listDiscoverableByRpId(rpId)
            } else {
                // allowCredentials route — keep only matches that also belong
                // to the requested rpId (defensive RP-spoofing guard).
                allowCredentialIds
                    .mapNotNull { id -> repository.findByCredentialId(id) }
                    .filter { it.rpId == rpId }
            }
        }

        return candidates.map { passkey -> buildEntry(passkey, option) }
    }

    private fun buildEntry(
        passkey: Passkey,
        option: BeginGetPublicKeyCredentialOption,
    ): PublicKeyCredentialEntry {
        // accountName / displayName fallback order per design §4.4 + req 1.6:
        //   accountName  = userDisplayName ?: userName ?: rpId
        //   displayName  = rpDisplayName ?: rpId
        val accountName = passkey.userDisplayName
            ?.takeIf { it.isNotBlank() }
            ?: passkey.userName?.takeIf { it.isNotBlank() }
            ?: passkey.rpId

        val displayName = passkey.rpDisplayName?.takeIf { it.isNotBlank() } ?: passkey.rpId

        val pendingIntent = PasskeyAuthActivity.pendingIntent(context, passkey.credentialId)

        return PublicKeyCredentialEntry.Builder(
            context,
            accountName,
            pendingIntent,
            option,
        )
            .setDisplayName(displayName)
            .build()
    }

    companion object {
        private const val TAG = "KeyNest.GetEntryBuilder"
    }
}
