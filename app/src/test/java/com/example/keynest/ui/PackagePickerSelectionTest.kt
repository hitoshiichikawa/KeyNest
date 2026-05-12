package com.example.keynest.ui

import com.example.keynest.ui.edit.PackagePickerBottomSheet
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #5 / AC 4.5.4 — package picker rows must surface a selected
 * state when tapped (`kn_surface_tint` background + primary check
 * icon). The view-side rendering is driven entirely off
 * `Adapter.selectedPosition`, so this test exercises the position
 * transition without standing up a RecyclerView (which is unnecessary
 * for the contract under test).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PackagePickerSelectionTest {

    @Test
    fun newAdapter_hasNoSelection() {
        val adapter = PackagePickerBottomSheet.Adapter { /* no-op */ }
        assertThat(adapter.selectedPosition).isEqualTo(androidx.recyclerview.widget.RecyclerView.NO_POSITION)
    }

    @Test
    fun rowTap_setsSelectedPosition_andInvokesCallback() {
        val captured = mutableListOf<String>()
        val adapter = PackagePickerBottomSheet.Adapter { captured.add(it) }
        adapter.submitList(
            listOf(
                PackagePickerBottomSheet.AppItem("com.example.a", "App A"),
                PackagePickerBottomSheet.AppItem("com.example.b", "App B"),
            ),
        )

        adapter.handleRowTap(position = 1, packageName = "com.example.b")

        assertThat(adapter.selectedPosition).isEqualTo(1)
        assertThat(captured).containsExactly("com.example.b")
    }

    @Test
    fun rowTap_movesSelection_betweenRows() {
        val captured = mutableListOf<String>()
        val adapter = PackagePickerBottomSheet.Adapter { captured.add(it) }
        adapter.submitList(
            listOf(
                PackagePickerBottomSheet.AppItem("com.example.a", "App A"),
                PackagePickerBottomSheet.AppItem("com.example.b", "App B"),
            ),
        )

        adapter.handleRowTap(0, "com.example.a")
        adapter.handleRowTap(1, "com.example.b")

        assertThat(adapter.selectedPosition).isEqualTo(1)
        assertThat(captured).containsExactly("com.example.a", "com.example.b").inOrder()
    }

    @Test
    fun rowTap_withNoPosition_isIgnored() {
        val captured = mutableListOf<String>()
        val adapter = PackagePickerBottomSheet.Adapter { captured.add(it) }
        adapter.submitList(
            listOf(PackagePickerBottomSheet.AppItem("com.example.a", "App A")),
        )

        adapter.handleRowTap(
            position = androidx.recyclerview.widget.RecyclerView.NO_POSITION,
            packageName = "com.example.a",
        )

        assertThat(adapter.selectedPosition).isEqualTo(androidx.recyclerview.widget.RecyclerView.NO_POSITION)
        assertThat(captured).isEmpty()
    }

    @Test
    fun submitList_resetsSelection() {
        val adapter = PackagePickerBottomSheet.Adapter { /* no-op */ }
        adapter.submitList(
            listOf(PackagePickerBottomSheet.AppItem("com.example.a", "App A")),
        )
        adapter.handleRowTap(0, "com.example.a")
        assertThat(adapter.selectedPosition).isEqualTo(0)

        adapter.submitList(
            listOf(PackagePickerBottomSheet.AppItem("com.example.b", "App B")),
        )

        assertThat(adapter.selectedPosition).isEqualTo(androidx.recyclerview.widget.RecyclerView.NO_POSITION)
    }
}
