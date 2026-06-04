package io.github.hitoshiichikawa.keynest.credentialprovider.authentication

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CallingAppInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.domain.model.Passkey
import io.github.hitoshiichikawa.keynest.domain.repository.PasskeyRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [GetEntryBuilder] (Issue #100 T-03 / design §4.4).
 *
 * Verifies (R1.1 / R1.2 / R1.3 / R1.6 + design §4.4 fallback order):
 *  - empty allowCredentials → listDiscoverableByRpId, returns one entry per row
 *  - specified allowCredentials → findByCredentialId per id; non-null kept
 *  - mismatched rpId entries are filtered out (defensive RP-spoofing guard)
 *  - zero candidates → empty list
 *  - accountName fallback userDisplayName → userName → rpId
 *  - displayName fallback rpDisplayName → rpId
 *  - pendingIntent targets PasskeyAuthActivity AND carries credentialId in
 *    the data Uri
 *  - parseRpId failure → skip option (empty list)
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GetEntryBuilderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = mockk<PasskeyRepository>()
    private val builder = GetEntryBuilder(context, repository)

    @Test
    fun build_allowCredentialsEmpty_callsListDiscoverableByRpId_andReturnsEntries() {
        val rows = listOf(sampleEntity("A"), sampleEntity("B"), sampleEntity("C"))
        coEvery { repository.listDiscoverableByRpId("example.com") } returns rows

        val entries = builder.build(option(requestJson(allowCredentials = emptyList())))

        assertThat(entries).hasSize(3)
        coVerify(exactly = 1) { repository.listDiscoverableByRpId("example.com") }
        coVerify(exactly = 0) { repository.findByCredentialId(any()) }
    }

    @Test
    fun build_allowCredentialsSpecified_callsFindByCredentialIdForEachId() {
        coEvery { repository.findByCredentialId("id1") } returns sampleEntity("id1")
        coEvery { repository.findByCredentialId("id2") } returns sampleEntity("id2")
        coEvery { repository.findByCredentialId("id3") } returns sampleEntity("id3")

        val entries = builder.build(option(requestJson(allowCredentials = listOf("id1", "id2", "id3"))))

        assertThat(entries).hasSize(3)
        coVerify(exactly = 1) { repository.findByCredentialId("id1") }
        coVerify(exactly = 1) { repository.findByCredentialId("id2") }
        coVerify(exactly = 1) { repository.findByCredentialId("id3") }
        coVerify(exactly = 0) { repository.listDiscoverableByRpId(any()) }
    }

    @Test
    fun build_returnsOnlyExistingCredentials() {
        coEvery { repository.findByCredentialId("id1") } returns sampleEntity("id1")
        coEvery { repository.findByCredentialId("id2") } returns null
        coEvery { repository.findByCredentialId("id3") } returns sampleEntity("id3")

        val entries = builder.build(option(requestJson(allowCredentials = listOf("id1", "id2", "id3"))))

        assertThat(entries).hasSize(2)
    }

    @Test
    fun build_filtersOutEntriesWithMismatchedRpId() {
        // Returned passkey claims rpId = attacker.example so it must be dropped
        // even though the credentialId matched.
        coEvery { repository.findByCredentialId("spoofed") } returns
            sampleEntity("spoofed", rpId = "attacker.example")

        val entries = builder.build(option(requestJson(allowCredentials = listOf("spoofed"))))

        assertThat(entries).isEmpty()
    }

    @Test
    fun build_zeroCandidates_returnsEmptyList() {
        coEvery { repository.listDiscoverableByRpId("example.com") } returns emptyList()

        val entries = builder.build(option(requestJson(allowCredentials = emptyList())))

        assertThat(entries).isEmpty()
    }

    @Test
    fun build_entryAccountName_fallsBackThroughDisplayNameThenUserNameThenRpId() {
        coEvery { repository.listDiscoverableByRpId("example.com") } returns listOf(
            sampleEntity(
                "withDisplay",
                userDisplayName = "Alice",
                userName = "alice@example.com",
            ),
            sampleEntity(
                "noDisplay",
                userDisplayName = null,
                userName = "bob@example.com",
            ),
            sampleEntity(
                "onlyRpId",
                userDisplayName = null,
                userName = null,
            ),
        )

        val entries = builder.build(option(requestJson(allowCredentials = emptyList())))

        assertThat(entries.map { it.username.toString() }).containsExactly(
            "Alice",
            "bob@example.com",
            "example.com",
        ).inOrder()
    }

    @Test
    fun build_pendingIntentTargetsPasskeyAuthActivity_andCarriesCredentialIdInDataUri() {
        coEvery { repository.findByCredentialId("myCred") } returns sampleEntity("myCred")

        val entries = builder.build(option(requestJson(allowCredentials = listOf("myCred"))))

        assertThat(entries).hasSize(1)
        val entry = entries.single()
        val shadowPi = Shadows.shadowOf(entry.pendingIntent)
        val intent = shadowPi.savedIntent

        assertThat(intent.component?.className).isEqualTo(PasskeyAuthActivity::class.java.name)
        assertThat(intent.data?.scheme).isEqualTo(PasskeyAuthActivity.INTENT_DATA_SCHEME)
        assertThat(intent.data?.authority).isEqualTo(PasskeyAuthActivity.INTENT_DATA_AUTHORITY)
        assertThat(intent.data?.path).isEqualTo("${PasskeyAuthActivity.INTENT_DATA_PATH_PREFIX}myCred")
        // PendingIntent must be MUTABLE so the OS Credential Manager can
        // inject the ProviderGetCredentialRequest extras at the moment the
        // user picks this entry. FLAG_IMMUTABLE caused signature verification
        // failures because the request never reached PasskeyAuthActivity.
        assertThat(shadowPi.flags and PendingIntent.FLAG_MUTABLE).isEqualTo(PendingIntent.FLAG_MUTABLE)
        assertThat(shadowPi.flags and PendingIntent.FLAG_IMMUTABLE).isEqualTo(0)
        assertThat(shadowPi.flags and PendingIntent.FLAG_UPDATE_CURRENT)
            .isEqualTo(PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @Test
    fun build_parseRpIdFails_returnsEmptyList() {
        // rpId missing entirely → AllowCredentialsParser.parseRpId throws;
        // GetEntryBuilder must defensively swallow and return [].
        val entries = builder.build(option("""{"challenge":"x"}"""))

        assertThat(entries).isEmpty()
        coVerify(exactly = 0) { repository.listDiscoverableByRpId(any()) }
        coVerify(exactly = 0) { repository.findByCredentialId(any()) }
    }

    // ---- helpers --------------------------------------------------------

    private fun option(requestJson: String): BeginGetPublicKeyCredentialOption =
        BeginGetPublicKeyCredentialOption(
            candidateQueryData = Bundle(),
            id = "publicKey:test",
            requestJson = requestJson,
            clientDataHash = null,
        )

    private fun requestJson(
        rpId: String = "example.com",
        allowCredentials: List<String> = emptyList(),
    ): String {
        val allow = if (allowCredentials.isEmpty()) {
            ""
        } else {
            val items = allowCredentials.joinToString(",") {
                "{\"type\":\"public-key\",\"id\":\"$it\"}"
            }
            ",\"allowCredentials\":[$items]"
        }
        return """
            {
              "rpId":"$rpId",
              "challenge":"Y2hhbGxlbmdl"
              $allow
            }
        """.trimIndent()
    }

    @Suppress("unused")
    private fun callingAppInfo(): CallingAppInfo =
        CallingAppInfo(
            packageName = context.packageName,
            signingInfo = android.content.pm.SigningInfo(),
        )

    private fun sampleEntity(
        credentialId: String,
        rpId: String = "example.com",
        userName: String? = "alice@example.com",
        userDisplayName: String? = "Alice",
    ): Passkey = Passkey(
        credentialId = credentialId,
        rpId = rpId,
        rpDisplayName = "Example",
        userHandle = ByteArray(16) { 0x77 },
        userName = userName,
        userDisplayName = userDisplayName,
        isDiscoverable = true,
        signCount = 0L,
        displayName = null,
        createdAt = 1_700_000_000_000L,
        lastUsedAt = null,
    )
}
