package com.example.keynest.ui.edit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Issue #32: pin the SAMPLE "業務でよく使う" data set and the simple search
 * filter used by [PackagePickerBottomSheet].
 *
 * The visual contract for the SAMPLE list comes from the JSX
 * `ScreenPicker.installed` array (design/screens/screens-2.jsx):
 *   - Salesforce Mobile (com.salesforce.chatter)
 *   - Workday (com.workday.workdroidapp)
 *   - Kintone (com.cybozu.kintone)
 *
 * The Architect-bounded choice in impl-notes.md is to display the SAMPLE
 * as a fixed list (not conditional on installed-state) per the Issue's
 * "SAMPLE データ表示で OK" guidance and to keep the screen useful even on
 * dev / emulator devices that don't have those apps installed (Req 5.5,
 * Out of Scope: 推奨判定ロジック).
 *
 * The filter is part-match (case-insensitive) over `label` and
 * `packageName` per Req 4.7.
 */
class PackagePickerSampleAppsTest {

    // ---- Req 5.5: SAMPLE list size + contents -----------------------------

    @Test
    fun sampleApps_contains3Entries() {
        // Req 5.5: 3-5 SAMPLE rows; we pin 3 as the chosen count.
        assertThat(PackagePickerBottomSheet.SAMPLE_FREQUENTLY_USED).hasSize(3)
    }

    @Test
    fun sampleApps_includesSalesforceWorkdayAndKintone() {
        // Req 5.5: the JSX ScreenPicker `installed[].used=true` set.
        val packages = PackagePickerBottomSheet.SAMPLE_FREQUENTLY_USED
            .map { it.packageName }
        assertThat(packages).containsExactly(
            "com.salesforce.chatter",
            "com.workday.workdroidapp",
            "com.cybozu.kintone",
        )
    }

    // ---- Req 4.7: case-insensitive part-match over label + packageName ----

    @Test
    fun matchesQuery_returnsTrueOnLabelSubstringMatchCaseInsensitive() {
        // Req 4.7: case-insensitive substring filter over the label.
        val app = PackagePickerBottomSheet.AppItem(
            packageName = "com.example.foo",
            label = "Foo App",
        )
        assertThat(PackagePickerBottomSheet.matchesQuery(app, "foo")).isTrue()
        assertThat(PackagePickerBottomSheet.matchesQuery(app, "FOO")).isTrue()
        assertThat(PackagePickerBottomSheet.matchesQuery(app, "App")).isTrue()
    }

    @Test
    fun matchesQuery_returnsTrueOnPackageNameSubstringMatch() {
        // Req 4.7: substring filter over the package name too.
        val app = PackagePickerBottomSheet.AppItem(
            packageName = "com.example.foo",
            label = "Display Name",
        )
        assertThat(PackagePickerBottomSheet.matchesQuery(app, "example")).isTrue()
        assertThat(PackagePickerBottomSheet.matchesQuery(app, "com.")).isTrue()
    }

    @Test
    fun matchesQuery_returnsFalseWhenNeitherFieldContainsQuery() {
        // Req 4.9: rows must be excluded when no field matches.
        val app = PackagePickerBottomSheet.AppItem(
            packageName = "com.example.foo",
            label = "Foo App",
        )
        assertThat(PackagePickerBottomSheet.matchesQuery(app, "bar")).isFalse()
    }

    @Test
    fun matchesQuery_returnsTrueOnEmptyOrBlankQuery() {
        // Req 4.8: when the search bar is empty / blank, no filter is
        // applied (all rows pass).
        val app = PackagePickerBottomSheet.AppItem(
            packageName = "com.example.foo",
            label = "Foo App",
        )
        assertThat(PackagePickerBottomSheet.matchesQuery(app, "")).isTrue()
        assertThat(PackagePickerBottomSheet.matchesQuery(app, "   ")).isTrue()
    }
}
