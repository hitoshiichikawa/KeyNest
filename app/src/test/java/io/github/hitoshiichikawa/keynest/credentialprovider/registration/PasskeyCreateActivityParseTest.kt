package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Issue #136: [PasskeyCreateActivity.parseCreationOptions] の
 * excludeCredentials 抽出の検証。
 *
 * Activity は [Robolectric.buildActivity] の `get()` のみで取得し
 * `setup()` を呼ばない（onCreate の PendingIntentHandler 経路を踏まずに
 * 内部 parse 関数だけを直接検証する — PasskeyAuthActivityTest と同じ流儀）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PasskeyCreateActivityParseTest {

    private fun activity(): PasskeyCreateActivity =
        Robolectric.buildActivity(PasskeyCreateActivity::class.java).get()

    @Test
    fun parseCreationOptions_extractsExcludeCredentialIds() {
        // Arrange
        val json = """
            {
              "rp":{"id":"example.com","name":"Example"},
              "user":{"id":"dXNlci0xMjM","name":"alice","displayName":"Alice"},
              "challenge":"Y2hhbGxlbmdl",
              "pubKeyCredParams":[{"type":"public-key","alg":-7}],
              "excludeCredentials":[
                {"type":"public-key","id":"id-one"},
                {"type":"public-key","id":"id-two"}
              ]
            }
        """.trimIndent()

        // Act
        val parsed = activity().parseCreationOptions(json)

        // Assert
        assertThat(parsed.excludeCredentialIds).containsExactly("id-one", "id-two").inOrder()
    }

    @Test
    fun parseCreationOptions_missingExcludeCredentials_yieldsEmptyList() {
        // Arrange
        val json = """
            {
              "rp":{"id":"example.com","name":"Example"},
              "user":{"id":"dXNlci0xMjM","name":"alice","displayName":"Alice"},
              "challenge":"Y2hhbGxlbmdl"
            }
        """.trimIndent()

        // Act
        val parsed = activity().parseCreationOptions(json)

        // Assert
        assertThat(parsed.excludeCredentialIds).isEmpty()
    }

    @Test
    fun parseCreationOptions_excludeEntriesWithoutId_areSkipped() {
        // Arrange: id 欠落・型不正の要素は無視し、有効な id のみ拾う
        val json = """
            {
              "rp":{"id":"example.com","name":"Example"},
              "user":{"id":"dXNlci0xMjM","name":"alice","displayName":"Alice"},
              "challenge":"Y2hhbGxlbmdl",
              "excludeCredentials":[
                {"type":"public-key"},
                {"type":"public-key","id":"valid-id"}
              ]
            }
        """.trimIndent()

        // Act
        val parsed = activity().parseCreationOptions(json)

        // Assert
        assertThat(parsed.excludeCredentialIds).containsExactly("valid-id")
    }
}
