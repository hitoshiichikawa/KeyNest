package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.CallingAppInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Tests for [CreateEntryBuilder] (Issue #99 T-07 / design §4.7).
 *
 * Verifies:
 *  - the [androidx.credentials.provider.CreateEntry] is built with the
 *    expected account name + description from the Issue #99 string
 *    resources;
 *  - the PendingIntent targets [PasskeyCreateActivity] (a per-request
 *    Uri token guarantees binder uniqueness);
 *  - the PendingIntent is immutable + uses FLAG_UPDATE_CURRENT.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CreateEntryBuilderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val builder = CreateEntryBuilder(context)

    @Test
    fun build_returnsCreateEntryWithExpectedAccountAndDescription() {
        val entry = builder.build(sampleRequest())

        assertThat(entry.accountName.toString())
            .isEqualTo(context.getString(R.string.passkey_create_entry_account_name))
        assertThat(entry.description?.toString())
            .isEqualTo(context.getString(R.string.passkey_create_entry_description))
    }

    @Test
    fun build_pendingIntentTargetsPasskeyCreateActivity() {
        val entry = builder.build(sampleRequest())

        val shadowPi = Shadows.shadowOf(entry.pendingIntent)
        val intent = shadowPi.savedIntent

        assertThat(intent.component?.className)
            .isEqualTo(PasskeyCreateActivity::class.java.name)
    }

    @Test
    fun build_pendingIntentIsImmutable_andFlagUpdateCurrent() {
        val entry = builder.build(sampleRequest())

        val shadowPi = Shadows.shadowOf(entry.pendingIntent)
        val flags = shadowPi.flags

        // Both flags must be present (ImmutableFlag is mandatory on API 31+).
        assertThat(flags and PendingIntent.FLAG_IMMUTABLE).isEqualTo(PendingIntent.FLAG_IMMUTABLE)
        assertThat(flags and PendingIntent.FLAG_UPDATE_CURRENT).isEqualTo(PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun sampleRequest(): BeginCreatePublicKeyCredentialRequest {
        val callingAppInfo = CallingAppInfo(
            packageName = context.packageName,
            signingInfo = android.content.pm.SigningInfo(),
        )
        return BeginCreatePublicKeyCredentialRequest(
            requestJson = """{"rp":{"id":"example.com","name":"Example"}}""",
            callingAppInfo = callingAppInfo,
            candidateQueryData = Bundle(),
        )
    }
}
