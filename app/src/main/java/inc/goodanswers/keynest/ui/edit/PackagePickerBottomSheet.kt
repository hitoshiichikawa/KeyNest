package inc.goodanswers.keynest.ui.edit

import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import inc.goodanswers.keynest.R
import inc.goodanswers.keynest.databinding.PackagePickerBottomSheetBinding
import inc.goodanswers.keynest.databinding.PackagePickerRowItemBinding
import inc.goodanswers.keynest.databinding.PackagePickerSectionHeaderItemBinding
import inc.goodanswers.keynest.databinding.PackagePickerEmptyItemBinding
import inc.goodanswers.keynest.di.ServiceLocator
import inc.goodanswers.keynest.util.IconLoader
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom sheet that lets the user pick an installed app's package name.
 *
 * Requirements (Issue #32 — Phase 2 #4, refined by Issue #48):
 *   * The visual contract follows the JSX `ScreenPicker` mock in
 *     design/screens/screens-2.jsx (drag handle / title + subtitle /
 *     search bar / "すべてのアプリ" section / icon-tile row / "手動入力"
 *     fallback). Issue #48 removed the JSX-only "業務でよく使う" SAMPLE
 *     section because the recommendation algorithm is not implementable
 *     on Android, and hard-coded SAMPLE rows do not reflect actual usage.
 *   * The existing public entry point [show] keeps its signature
 *     `(FragmentManager, (String) -> Unit) -> Unit` (NFR 2.1 / 2.2).
 *   * Installed-app listing still goes through [loadInstalledApps]
 *     (PackageManager.getInstalledApplications(0)) on Dispatchers.IO
 *     (Req 3.3).
 */
class PackagePickerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: PackagePickerBottomSheetBinding? = null
    private val binding get() = _binding!!

    /** Result callback. Set via [show]; cleared in onDestroyView. */
    var onPicked: ((String) -> Unit)? = null

    /**
     * Full list of installed apps, loaded asynchronously in onViewCreated.
     * The active filter is applied against this snapshot to derive the
     * adapter's item list (sections + rows).
     */
    private var allInstalledApps: List<AppItem> = emptyList()
    private var currentQuery: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = PackagePickerBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = SectionAdapter(::onRowPicked, ServiceLocator.iconLoader)
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnManualEntry.setOnClickListener { showManualEntryDialog() }

        binding.inputSearchPicker.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString().orEmpty()
                adapter.submitItems(buildItems(allInstalledApps, currentQuery))
            }
        })

        // Pre-render with an empty list so the adapter is wired up before
        // the async installed-app load resolves (Req 3.3: existing async
        // load behaviour preserved). With Issue #48 removing the SAMPLE
        // "業務でよく使う" section, this seed renders no items until the
        // installed list arrives — matching the new "All apps only"
        // contract (Req 3.1 / 3.2).
        adapter.submitItems(buildItems(emptyList(), currentQuery))

        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { loadInstalledApps() }
            allInstalledApps = items
            adapter.submitItems(buildItems(items, currentQuery))
        }
    }

    private fun onRowPicked(packageName: String) {
        onPicked?.invoke(packageName)
        dismiss()
    }

    /**
     * Inflate and show the manual-input dialog (Req 8.7 / 8.8). Validates
     * the entered package name with [isManualEntryValid]; on a valid
     * entry, invokes [onPicked] once and dismisses both the dialog and
     * the bottom sheet. On an invalid entry, surfaces a Snackbar with the
     * package_picker_manual_input_invalid message (Req 8.9).
     */
    private fun showManualEntryDialog() {
        val context = requireContext()
        val container = LayoutInflater.from(context)
            .inflate(R.layout.package_picker_manual_dialog_input, null, false)
        val inputView: EditText = container.findViewById(R.id.input_manual_package)

        AlertDialog.Builder(context)
            .setTitle(R.string.package_picker_manual_input_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val typed = inputView.text?.toString().orEmpty().trim()
                if (isManualEntryValid(typed)) {
                    onRowPicked(typed)
                } else {
                    Snackbar.make(
                        binding.root,
                        R.string.package_picker_manual_input_invalid,
                        Snackbar.LENGTH_SHORT,
                    ).show()
                }
            }
            .show()
    }

    private fun loadInstalledApps(): List<AppItem> {
        val pm = requireContext().packageManager
        // No special flags - we deliberately stay with the default visibility
        // (PackageManager respects the queries> manifest restriction so we
        // see only launcher-launchable apps on API 30+).
        val infos = pm.getInstalledApplications(0)
        return infos
            .map { info ->
                AppItem(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    override fun onDestroyView() {
        _binding?.recycler?.adapter = null
        _binding = null
        super.onDestroyView()
    }

    // ---- pure helpers (unit-tested) ---------------------------------------

    /**
     * AppItem rendered by the SectionAdapter and used by [matchesQuery].
     */
    data class AppItem(val packageName: String, val label: String)

    /**
     * Items the SectionAdapter renders. Sealed type lets onCreateViewHolder
     * dispatch on the discriminator without reflection.
     */
    internal sealed class ListItem {
        data class Header(val titleRes: Int) : ListItem()
        data class Row(val app: AppItem) : ListItem()
        object Empty : ListItem()
    }

    /**
     * Compose the list of section headers + rows for the given full list
     * and current filter query.
     *
     * Layout order (Issue #48 Req 3.1 / 3.2 / 3.4):
     *   1. "すべてのアプリ" header   (when at least one installed row passes the filter)
     *   2. installed-app rows (filtered by query; 0 rows while async load is pending)
     *   3. Empty placeholder (Req 3.4) — shown when an active filter excludes
     *      every installed row.
     *
     * Note: the "業務でよく使う" SAMPLE section was removed in Issue #48
     * (hard-coded sample data did not reflect actual installed state).
     */
    internal fun buildItems(installed: List<AppItem>, query: String): List<ListItem> {
        val filteredAll = installed.filter { matchesQuery(it, query) }
        val out = mutableListOf<ListItem>()
        if (filteredAll.isNotEmpty()) {
            out += ListItem.Header(R.string.package_picker_section_all)
            filteredAll.forEach { out += ListItem.Row(it) }
        }
        if (out.isEmpty() && query.isNotBlank()) {
            out += ListItem.Empty
        }
        return out
    }

    /** Adapter that renders the 3 ListItem variants. */
    private class SectionAdapter(
        private val onClick: (String) -> Unit,
        private val iconLoader: IconLoader,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val items = mutableListOf<ListItem>()

        fun submitItems(list: List<ListItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is ListItem.Header -> VIEW_TYPE_HEADER
            is ListItem.Row -> VIEW_TYPE_ROW
            ListItem.Empty -> VIEW_TYPE_EMPTY
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_TYPE_HEADER -> HeaderVH(
                    PackagePickerSectionHeaderItemBinding.inflate(inflater, parent, false),
                )
                VIEW_TYPE_EMPTY -> EmptyVH(
                    PackagePickerEmptyItemBinding.inflate(inflater, parent, false),
                )
                else -> RowVH(
                    PackagePickerRowItemBinding.inflate(inflater, parent, false),
                )
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is ListItem.Header -> (holder as HeaderVH).bind(item)
                is ListItem.Row -> (holder as RowVH).bind(item, onClick, iconLoader)
                ListItem.Empty -> Unit
            }
        }

        /**
         * Issue #43 Req 2.4: only RowVH renders an icon; HeaderVH /
         * EmptyVH have no icon ImageView so we no-op for those types.
         * Cancelling on recycle prevents a late `getApplicationIcon`
         * result from painting onto a row that has since been rebound
         * (or recycled to a Header / Empty slot).
         */
        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            super.onViewRecycled(holder)
            if (holder is RowVH) {
                iconLoader.cancel(holder.iconAppView)
            }
        }

        override fun getItemCount(): Int = items.size

        private class HeaderVH(
            private val binding: PackagePickerSectionHeaderItemBinding,
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: ListItem.Header) {
                binding.textSectionHeader.setText(item.titleRes)
            }
        }

        private class RowVH(
            private val binding: PackagePickerRowItemBinding,
        ) : RecyclerView.ViewHolder(binding.root) {

            /** Exposed for [onViewRecycled] race-prevention. */
            val iconAppView get() = binding.iconApp

            fun bind(item: ListItem.Row, onClick: (String) -> Unit, iconLoader: IconLoader) {
                binding.textAppLabel.text = item.app.label
                binding.textAppPackage.text = item.app.packageName
                binding.root.setOnClickListener { onClick(item.app.packageName) }

                // Issue #43 Req 1.3: paint the real app icon (or fallback).
                iconLoader.loadInto(binding.iconApp, item.app.packageName)
            }
        }

        private class EmptyVH(
            binding: PackagePickerEmptyItemBinding,
        ) : RecyclerView.ViewHolder(binding.root)

        companion object {
            private const val VIEW_TYPE_HEADER = 0
            private const val VIEW_TYPE_ROW = 1
            private const val VIEW_TYPE_EMPTY = 2
        }
    }

    companion object {
        private const val TAG = "PackagePickerBottomSheet"

        /**
         * Case-insensitive substring filter over both the label and the
         * package name (Req 3.5). A blank query matches every row.
         *
         * Issue #48: this helper used to back both the SAMPLE
         * "業務でよく使う" rows and the "All apps" rows; with the SAMPLE
         * section gone, it filters the installed-app list only.
         */
        fun matchesQuery(app: AppItem, query: String): Boolean {
            if (query.isBlank()) return true
            val needle = query.trim().lowercase()
            return app.label.lowercase().contains(needle) ||
                app.packageName.lowercase().contains(needle)
        }

        /**
         * Gate for the manual-input dialog confirm action (Req 8.9).
         * Mirrors [inc.goodanswers.keynest.domain.usecase.PackageNameValidator]
         * but is intentionally redefined here so the picker stays a pure
         * UI module without leaking domain types. Tests pin the rejected
         * set (PackagePickerManualEntryValidationTest).
         */
        private val MANUAL_ENTRY_PATTERN =
            Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

        fun isManualEntryValid(input: String): Boolean {
            val trimmed = input.trim()
            if (trimmed.isBlank()) return false
            return MANUAL_ENTRY_PATTERN.matches(trimmed)
        }

        fun show(manager: FragmentManager, onPicked: (String) -> Unit) {
            val sheet = PackagePickerBottomSheet().apply { this.onPicked = onPicked }
            sheet.show(manager, TAG)
        }
    }
}
