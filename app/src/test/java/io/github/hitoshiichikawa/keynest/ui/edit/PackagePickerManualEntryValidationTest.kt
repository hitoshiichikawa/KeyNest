package io.github.hitoshiichikawa.keynest.ui.edit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Issue #32 Req 8.9: pin the manual-input validation gate used by
 * [PackagePickerBottomSheet.isManualEntryValid].
 *
 * The picker reuses the same shape rule as the rest of the codebase
 * (PackageNameValidator pattern: at least one '.', no whitespace, valid
 * identifier segments) — see design/spec.md §1 / Issue #14.
 *
 * Req 8.9 says: blank or "obviously malformed" input MUST NOT invoke
 * onPicked; instead an error message is shown. Tests below pin the
 * "blocked" set explicitly so a future refactor cannot silently weaken
 * the check.
 */
class PackagePickerManualEntryValidationTest {

    // ---- positive cases ---------------------------------------------------

    @Test
    fun manualEntry_acceptsValidPackageNames() {
        listOf(
            "com.example",
            "com.example.foo",
            "io.k_n.x_2",
            "a.b.c.d.e",
        ).forEach {
            assertThat(PackagePickerBottomSheet.isManualEntryValid(it)).isTrue()
        }
    }

    // ---- negative cases (Req 8.9) ----------------------------------------

    @Test
    fun manualEntry_rejectsBlankInput() {
        // Req 8.9: blank → reject.
        assertThat(PackagePickerBottomSheet.isManualEntryValid("")).isFalse()
        assertThat(PackagePickerBottomSheet.isManualEntryValid("   ")).isFalse()
    }

    @Test
    fun manualEntry_rejectsInputWithoutDot() {
        // Req 8.9: "obviously malformed = no dot".
        assertThat(PackagePickerBottomSheet.isManualEntryValid("singletoken")).isFalse()
    }

    @Test
    fun manualEntry_rejectsInputWithWhitespace() {
        // Req 8.9: "obviously malformed = whitespace inside".
        assertThat(PackagePickerBottomSheet.isManualEntryValid("com.example foo")).isFalse()
        assertThat(PackagePickerBottomSheet.isManualEntryValid("com. example")).isFalse()
    }

    @Test
    fun manualEntry_rejectsMalformedShapes() {
        // boundary cases reused from PackageNameValidatorTest.
        listOf(
            ".com.example",
            "com.example.",
            "com..example",
            "9example.foo",   // segment starts with digit
            "com.例.foo",     // non-ASCII segment
            "com.example-bad",
        ).forEach {
            assertThat(PackagePickerBottomSheet.isManualEntryValid(it)).isFalse()
        }
    }
}
