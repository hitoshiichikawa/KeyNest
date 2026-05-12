package com.example.keynest.util

import com.example.keynest.domain.model.SigningHash
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import org.junit.Test

/**
 * Unit tests for [AdvancedDetailsFormatter].
 *
 * Backs requirements:
 * - 2.2 (local timezone display for createdAt / updatedAt)
 * - 3.1 (64-char lowercase hex full display)
 * - 3.4 (null hash returns null so caller can switch to placeholder)
 * - 4.1 (signature capture local timezone)
 * - NFR 1.1 (output length is a known constant -- the redaction policy
 *   relies on this when wrapping for logs).
 */
class AdvancedDetailsFormatterTest {

    // 2025-01-15T03:45:00Z. Picking a UTC midnight-ish instant so the JST
    // expectation is unambiguous (+09:00).
    private val sampleEpochMillis: Long = 1_736_912_700_000L

    @Test
    fun formatTimestamp_inUtc_rendersZonedWallClockInThatZone() {
        // Arrange + Act
        val rendered = AdvancedDetailsFormatter.formatTimestamp(
            epochMillis = sampleEpochMillis,
            zone = ZoneId.of("UTC"),
        )

        // Assert: pattern yyyy-MM-dd HH:mm in UTC
        assertThat(rendered).isEqualTo("2025-01-15 03:45")
    }

    @Test
    fun formatTimestamp_inJst_shiftsBy9Hours() {
        // Arrange + Act
        val rendered = AdvancedDetailsFormatter.formatTimestamp(
            epochMillis = sampleEpochMillis,
            zone = ZoneId.of("Asia/Tokyo"),
        )

        // Assert: same instant, +09:00 wall clock
        assertThat(rendered).isEqualTo("2025-01-15 12:45")
    }

    @Test
    fun formatTimestamp_epochZero_rendersUnixEpochInUtc() {
        // Boundary: epoch 0 must format cleanly (no negative-year / overflow).
        val rendered = AdvancedDetailsFormatter.formatTimestamp(
            epochMillis = 0L,
            zone = ZoneId.of("UTC"),
        )
        assertThat(rendered).isEqualTo("1970-01-01 00:00")
    }

    @Test
    fun formatSha256Hex_returns64CharLowercaseHex_forValidHash() {
        // Arrange: a deterministic SigningHash so the expected hex is computable.
        val raw = ByteArray(32) { it.toByte() }
        val hash = SigningHash(raw)

        // Act
        val rendered = AdvancedDetailsFormatter.formatSha256Hex(hash)!!

        // Assert: length, lowercase, exact value
        assertThat(rendered).hasLength(AdvancedDetailsFormatter.SHA256_HEX_LENGTH)
        assertThat(rendered).matches("[0-9a-f]{64}")
        assertThat(rendered).isEqualTo(
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
        )
    }

    @Test
    fun formatSha256Hex_returnsNull_whenHashIsNull() {
        // Req 3.4: callers branch on null to render a placeholder.
        assertThat(AdvancedDetailsFormatter.formatSha256Hex(null)).isNull()
    }

    @Test
    fun formatSha256Hex_isAllLowercase_forBytesThatCouldRenderAsUppercase() {
        // Boundary: a byte 0xAB would render as "ab" only if the encoder is
        // lowercase. Pin the contract.
        val raw = ByteArray(32) { 0xAB.toByte() }
        val hash = SigningHash(raw)

        val rendered = AdvancedDetailsFormatter.formatSha256Hex(hash)!!

        assertThat(rendered).isEqualTo("ab".repeat(32))
        assertThat(rendered).doesNotContain("A")
        assertThat(rendered).doesNotContain("B")
    }
}
