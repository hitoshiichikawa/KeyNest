package com.example.keynest.ui.edit

import com.example.keynest.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Issue #48: pin `PackagePickerBottomSheet.buildItems` behaviour after the
 * "業務でよく使う" (frequently-used SAMPLE) section is removed.
 *
 * Coverage:
 *  - Req 3.1 / 3.2: only the "All apps" section is generated; the
 *    "Frequently used" header / rows must not appear.
 *  - Req 3.4: filtering that empties the All-apps section still surfaces
 *    the existing [PackagePickerBottomSheet.ListItem.Empty] placeholder.
 *  - Req 3.5 (matchesQuery): case-insensitive substring filter over label
 *    and packageName is preserved (carried over from the deleted
 *    PackagePickerSampleAppsTest).
 *
 * Note: We intentionally do NOT assert anything about
 * `SAMPLE_FREQUENTLY_USED` — the constant is deleted by this Issue and
 * referencing it from a test would prevent compilation.
 */
class PackagePickerBuildItemsTest {

    private val sheet = PackagePickerBottomSheet()

    // ---- Req 3.1 / 3.2: All-apps-only section --------------------------------

    @Test
    fun buildItems_withInstalledApps_producesOnlyAllAppsHeaderAndRows() {
        // Arrange: 2 installed apps and no filter.
        val installed = listOf(
            PackagePickerBottomSheet.AppItem("com.example.foo", "Foo App"),
            PackagePickerBottomSheet.AppItem("com.example.bar", "Bar App"),
        )

        // Act
        val items = sheet.buildItems(installed, query = "")

        // Assert: header + 2 rows, no other headers.
        assertThat(items).hasSize(3)
        val firstHeader = items[0]
        assertThat(firstHeader).isInstanceOf(PackagePickerBottomSheet.ListItem.Header::class.java)
        assertThat((firstHeader as PackagePickerBottomSheet.ListItem.Header).titleRes)
            .isEqualTo(R.string.package_picker_section_all)
        assertThat(items[1]).isInstanceOf(PackagePickerBottomSheet.ListItem.Row::class.java)
        assertThat(items[2]).isInstanceOf(PackagePickerBottomSheet.ListItem.Row::class.java)

        // Req 3.1 / 3.2 (negative): no second Header row should appear.
        val headerCount = items.count { it is PackagePickerBottomSheet.ListItem.Header }
        assertThat(headerCount).isEqualTo(1)
    }

    @Test
    fun buildItems_withEmptyInstalledAndBlankQuery_producesNoItems() {
        // Arrange: installed list not yet resolved (async still running),
        // no search query. Req 3.3: existing async load behaviour is
        // preserved; until the list resolves, buildItems should emit
        // an empty payload (no SAMPLE rows, no Empty placeholder — the
        // placeholder is for filter misses only, Req 3.4).

        // Act
        val items = sheet.buildItems(installed = emptyList(), query = "")

        // Assert
        assertThat(items).isEmpty()
    }

    // ---- Req 3.4: Empty placeholder on filter miss ---------------------------

    @Test
    fun buildItems_filterMatchesNothing_emitsEmptyPlaceholder() {
        // Arrange: installed apps that do not match the query.
        val installed = listOf(
            PackagePickerBottomSheet.AppItem("com.example.foo", "Foo App"),
        )

        // Act: query that excludes every row.
        val items = sheet.buildItems(installed, query = "zzz-no-match")

        // Assert: single Empty placeholder, no headers.
        assertThat(items).hasSize(1)
        assertThat(items[0]).isEqualTo(PackagePickerBottomSheet.ListItem.Empty)
    }

    @Test
    fun buildItems_filterMatchesSomeRows_keepsAllAppsHeaderAndMatchingRows() {
        // Arrange: installed apps with one matching row.
        val installed = listOf(
            PackagePickerBottomSheet.AppItem("com.example.foo", "Foo App"),
            PackagePickerBottomSheet.AppItem("com.example.bar", "Bar App"),
        )

        // Act
        val items = sheet.buildItems(installed, query = "foo")

        // Assert: header + 1 matching row.
        assertThat(items).hasSize(2)
        assertThat(items[0]).isInstanceOf(PackagePickerBottomSheet.ListItem.Header::class.java)
        val row = items[1] as PackagePickerBottomSheet.ListItem.Row
        assertThat(row.app.packageName).isEqualTo("com.example.foo")
    }

    // ---- Req 3.5: matchesQuery (preserved from PackagePickerSampleAppsTest) --

    @Test
    fun matchesQuery_returnsTrueOnLabelSubstringMatchCaseInsensitive() {
        // Req 3.5: case-insensitive substring filter over the label.
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
        // Req 3.5: substring filter over the package name too.
        val app = PackagePickerBottomSheet.AppItem(
            packageName = "com.example.foo",
            label = "Display Name",
        )
        assertThat(PackagePickerBottomSheet.matchesQuery(app, "example")).isTrue()
        assertThat(PackagePickerBottomSheet.matchesQuery(app, "com.")).isTrue()
    }

    @Test
    fun matchesQuery_returnsFalseWhenNeitherFieldContainsQuery() {
        // Req 3.5 (negative): rows must be excluded when no field matches.
        val app = PackagePickerBottomSheet.AppItem(
            packageName = "com.example.foo",
            label = "Foo App",
        )
        assertThat(PackagePickerBottomSheet.matchesQuery(app, "bar")).isFalse()
    }

    @Test
    fun matchesQuery_returnsTrueOnEmptyOrBlankQuery() {
        // Req 3.5: when the search bar is empty / blank, no filter is
        // applied (all rows pass).
        val app = PackagePickerBottomSheet.AppItem(
            packageName = "com.example.foo",
            label = "Foo App",
        )
        assertThat(PackagePickerBottomSheet.matchesQuery(app, "")).isTrue()
        assertThat(PackagePickerBottomSheet.matchesQuery(app, "   ")).isTrue()
    }
}
