package io.github.hitoshiichikawa.keynest.credentialprovider.authentication

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-JVM unit tests for [AllowCredentialsParser] (Issue #100 T-03 /
 * design §4.3).
 *
 * Verifies:
 *  - `allowCredentials` array → ordered list of `id` strings (R1.2)
 *  - empty / missing / malformed → empty list (defensive, req 未解決事項 3)
 *  - `rpId` extraction with [IllegalArgumentException] on absence (R1.x)
 */
class AllowCredentialsParserTest {

    @Test
    fun parseAllowCredentialIds_returnsIdsInOrder() {
        val json = """
            {
              "rpId":"example.com",
              "challenge":"Y2hhbGxlbmdl",
              "allowCredentials":[
                {"type":"public-key","id":"AAA"},
                {"type":"public-key","id":"BBB"},
                {"type":"public-key","id":"CCC"}
              ]
            }
        """.trimIndent()

        val ids = AllowCredentialsParser.parseAllowCredentialIds(json)

        assertThat(ids).containsExactly("AAA", "BBB", "CCC").inOrder()
    }

    @Test
    fun parseAllowCredentialIds_emptyArray_returnsEmpty() {
        val json = """{"rpId":"example.com","allowCredentials":[]}"""

        val ids = AllowCredentialsParser.parseAllowCredentialIds(json)

        assertThat(ids).isEmpty()
    }

    @Test
    fun parseAllowCredentialIds_missingField_returnsEmpty() {
        val json = """{"rpId":"example.com","challenge":"x"}"""

        val ids = AllowCredentialsParser.parseAllowCredentialIds(json)

        assertThat(ids).isEmpty()
    }

    @Test
    fun parseAllowCredentialIds_malformedJson_returnsEmpty() {
        // Truncated / invalid JSON should never throw — the OS sheet still
        // needs to render whatever candidates it can.
        val ids = AllowCredentialsParser.parseAllowCredentialIds("this-is-not-json")

        assertThat(ids).isEmpty()
    }

    @Test
    fun parseAllowCredentialIds_typeMismatch_returnsEmpty() {
        // allowCredentials is a string instead of an array — defensive.
        val json = """{"rpId":"example.com","allowCredentials":"oops"}"""

        val ids = AllowCredentialsParser.parseAllowCredentialIds(json)

        assertThat(ids).isEmpty()
    }

    @Test
    fun parseAllowCredentialIds_idMissingOnElement_skipsElement() {
        // Element with no `id` field is silently dropped so the rest still
        // surfaces (the OS sheet would otherwise show no candidates).
        val json = """
            {
              "rpId":"example.com",
              "allowCredentials":[
                {"type":"public-key","id":"AAA"},
                {"type":"public-key"},
                {"type":"public-key","id":"CCC"}
              ]
            }
        """.trimIndent()

        val ids = AllowCredentialsParser.parseAllowCredentialIds(json)

        assertThat(ids).containsExactly("AAA", "CCC").inOrder()
    }

    @Test
    fun parseRpId_returnsRpIdString() {
        val json = """{"rpId":"example.com","challenge":"x"}"""

        val rpId = AllowCredentialsParser.parseRpId(json)

        assertThat(rpId).isEqualTo("example.com")
    }

    @Test
    fun parseRpId_throwsIllegalArgumentException_whenMissing() {
        val json = """{"challenge":"x"}"""

        val ex = runCatching { AllowCredentialsParser.parseRpId(json) }.exceptionOrNull()

        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun parseRpId_throwsIllegalArgumentException_whenBlank() {
        val json = """{"rpId":""}"""

        val ex = runCatching { AllowCredentialsParser.parseRpId(json) }.exceptionOrNull()

        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun parseRpId_throwsIllegalArgumentException_onMalformedJson() {
        val ex = runCatching { AllowCredentialsParser.parseRpId("not-json") }.exceptionOrNull()

        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
    }
}
