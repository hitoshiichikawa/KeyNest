package com.example.keynest.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Boundary-value coverage of [PackageNameValidator]. Req 1.3. */
class PackageNameValidatorTest {

    @Test
    fun valid_acceptsTypicalAndroidPackageNames() {
        listOf(
            "com.example",
            "com.example.target",
            "com.example.target_v2",
            "a.b.c.d.e",
            "io.k_n.x_2",
        ).forEach {
            assertThat(PackageNameValidator.isValid(it)).isTrue()
        }
    }

    @Test
    fun invalid_rejectsBadShapes() {
        listOf(
            "",
            "  ",
            "singletoken",
            ".com.example",
            "com.example.",
            "com..example",
            "9example.foo",
            "com.例.foo",   // non-ASCII
            "com.example-bad",
            "com.example bad",
        ).forEach {
            assertThat(PackageNameValidator.isValid(it)).isFalse()
        }
    }
}
