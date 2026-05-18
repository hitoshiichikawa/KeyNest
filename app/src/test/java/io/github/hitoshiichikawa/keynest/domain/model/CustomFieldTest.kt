package io.github.hitoshiichikawa.keynest.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Behaviour of the [CustomField] plaintext domain type.
 *
 * Issue #66 Phase 1. Backs Req 1.1 / 1.2 / 5.1 (toString redaction).
 */
class CustomFieldTest {

    @Test
    fun equals_isStructural_overFieldKeyAndValue() {
        val a = CustomField("memberId", "12345")
        val b = CustomField("memberId", "12345")
        val c = CustomField("memberId", "99999")
        val d = CustomField("storeCode", "12345")

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
        assertThat(a).isNotEqualTo(c)
        assertThat(a).isNotEqualTo(d)
    }

    @Test
    fun toString_redactsBothFieldKeyAndValue() {
        // NFR 2.1 + Req 5.1: neither the value nor the (possibly sensitive)
        // fieldKey may leak via toString().
        val rendered = CustomField("会員番号", "leaky-secret-12345").toString()
        assertThat(rendered).doesNotContain("会員番号")
        assertThat(rendered).doesNotContain("leaky-secret")
        // Length surface is preserved so logs can still report shape.
        assertThat(rendered).contains("redacted")
    }

    @Test
    fun construction_acceptsEmptyValueAndEmptyFieldKey() {
        // The domain type does NOT enforce non-empty. The ViewModel reducer
        // silently drops rows with blank fieldKey (Req 3.4); the use case
        // layer never sees them. But the data class itself is unrestricted.
        val emptyValue = CustomField("memberId", "")
        val emptyKey = CustomField("", "12345")
        assertThat(emptyValue.value).isEmpty()
        assertThat(emptyKey.fieldKey).isEmpty()
    }
}
