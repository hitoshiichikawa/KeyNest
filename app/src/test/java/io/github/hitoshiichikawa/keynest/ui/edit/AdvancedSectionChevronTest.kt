package io.github.hitoshiichikawa.keynest.ui.edit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit test for [CredentialEditActivity.chevronRotationFor]. Backs Req 1.4:
 * the chevron must visibly switch direction when the section transitions
 * between collapsed and expanded.
 */
class AdvancedSectionChevronTest {

    @Test
    fun chevronRotation_isZero_whenCollapsed() {
        assertThat(CredentialEditActivity.chevronRotationFor(expanded = false)).isEqualTo(0f)
    }

    @Test
    fun chevronRotation_isOneEighty_whenExpanded() {
        assertThat(CredentialEditActivity.chevronRotationFor(expanded = true)).isEqualTo(180f)
    }

    @Test
    fun chevronRotation_differsBetweenStates() {
        // Defensive: even if a future refactor changes the absolute values
        // (e.g. switches to 90deg sides), the rotation MUST differ between
        // collapsed and expanded -- otherwise Req 1.4 is violated.
        val collapsed = CredentialEditActivity.chevronRotationFor(expanded = false)
        val expanded = CredentialEditActivity.chevronRotationFor(expanded = true)
        assertThat(collapsed).isNotEqualTo(expanded)
    }
}
