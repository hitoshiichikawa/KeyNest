package com.example.keynest.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-helper behaviour of [InitialLetterDrawable.computeInitial]
 * (Issue #43 Req 1.4 supporting unit test).
 *
 * The helper is intentionally pure so it can be exercised without
 * spinning up Robolectric; it has no Android dependencies. The visual
 * draw path is covered indirectly via [IconLoaderTest] (the fallback
 * branch returns an [InitialLetterDrawable] instance).
 */
class InitialLetterDrawableTest {

    @Test
    fun computeInitial_returnsUppercaseLastSegmentFirstChar() {
        // Arrange / Act / Assert: representative package names cited in
        // requirements.md > 確認事項 (1) and design.md.
        assertThat(InitialLetterDrawable.computeInitial("com.example.keynest")).isEqualTo("K")
        assertThat(InitialLetterDrawable.computeInitial("com.android.chrome")).isEqualTo("C")
        assertThat(InitialLetterDrawable.computeInitial("org.mozilla.firefox")).isEqualTo("F")
    }

    @Test
    fun computeInitial_singleSegmentPackage_usesItsFirstChar() {
        // Arrange / Act / Assert
        assertThat(InitialLetterDrawable.computeInitial("foo")).isEqualTo("F")
        assertThat(InitialLetterDrawable.computeInitial("a")).isEqualTo("A")
    }

    @Test
    fun computeInitial_alreadyUppercase_returnsAsIs() {
        // Arrange / Act / Assert
        assertThat(InitialLetterDrawable.computeInitial("com.Example.Keynest")).isEqualTo("K")
    }

    @Test
    fun computeInitial_unresolvableLastSegment_returnsQuestionMark() {
        // Arrange / Act / Assert: empty, dot-only, trailing-dot, whitespace.
        assertThat(InitialLetterDrawable.computeInitial("")).isEqualTo("?")
        assertThat(InitialLetterDrawable.computeInitial(".")).isEqualTo("?")
        assertThat(InitialLetterDrawable.computeInitial("com.")).isEqualTo("?")
        assertThat(InitialLetterDrawable.computeInitial("   ")).isEqualTo("?")
    }

    @Test
    fun computeInitial_digitLastSegment_returnsDigit() {
        // Arrange / Act / Assert: package names ending in a numeric segment
        // (e.g. internal QA builds) should not collapse to '?' — the digit
        // is a valid identifying glyph.
        assertThat(InitialLetterDrawable.computeInitial("com.example.7zip")).isEqualTo("7")
    }
}
