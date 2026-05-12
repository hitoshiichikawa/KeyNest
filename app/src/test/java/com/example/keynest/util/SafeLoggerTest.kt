package com.example.keynest.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Static behaviour of [SafeLogger]. Verifies the redaction primitives that
 * higher layers rely on (NFR 1.3, NFR 5.1).
 */
class SafeLoggerTest {

    @Test
    fun redacted_toString_returnsConstantPlaceholder_regardlessOfValue() {
        // Arrange
        val password = "p@ssw0rd-secret".toCharArray()
        val redacted = SafeLogger.Redacted(password)

        // Act
        val rendered = redacted.toString()

        // Assert: rendered must NOT contain any prefix of the password
        assertThat(rendered).isEqualTo(SafeLogger.REDACTED_PLACEHOLDER)
        assertThat(rendered).doesNotContain("p@")
        assertThat(rendered).doesNotContain("secret")
    }

    @Test
    fun redacted_toString_isStableForNull() {
        assertThat(SafeLogger.Redacted(null).toString()).isEqualTo(SafeLogger.REDACTED_PLACEHOLDER)
    }

    @Test
    fun previewHex_truncatesLongHex_andOmitsBody() {
        // Arrange: a 64-char SHA-256 hex
        val hex = "deadbeefcafebabe".repeat(4)
        require(hex.length == 64)

        // Act
        val preview = SafeLogger.previewHex(hex)

        // Assert: first 8 chars + "..." marker, the remaining 56 chars are gone
        assertThat(preview).isEqualTo("deadbeef...")
        assertThat(preview).doesNotContain("cafebabe")
    }

    @Test
    fun previewHex_redactsShortHex() {
        // 8 chars or fewer must be treated as wholly sensitive (not enough
        // entropy to give a useful preview while remaining safe).
        assertThat(SafeLogger.previewHex("dead")).isEqualTo(SafeLogger.REDACTED_PLACEHOLDER)
        assertThat(SafeLogger.previewHex("deadbeef")).isEqualTo(SafeLogger.REDACTED_PLACEHOLDER)
    }

    @Test
    fun previewHex_returnsRedaction_forEmpty() {
        assertThat(SafeLogger.previewHex("")).isEqualTo(SafeLogger.REDACTED_PLACEHOLDER)
    }
}
