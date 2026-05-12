package com.example.keynest.ui.edit

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.keynest.databinding.PackagePickerBottomSheetBinding
import com.example.keynest.databinding.PackagePickerRowBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.withContext

/**
 * Bottom sheet that lets the user pick an installed app's package name.
 *
 * Requirements: 1.1, 2.1 (entry point that ensures the saved record will
 * have a valid SHA-256 hash at save time).
 *
 * Listing is performed via [PackageManager.getInstalledApplications] on
 * Dispatchers.IO to avoid blocking the main thread on devices with many
 * installed apps.
 */
class PackagePickerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: PackagePickerBottomSheetBinding? = null
    private val binding get() = _binding!!

    /** Result callback. Set via [show]; cleared in onDestroyView. */
    var onPicked: ((String) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = PackagePickerBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = Adapter { picked ->
            onPicked?.invoke(picked)
            dismiss()
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { loadInstalledApps() }
            adapter.submitList(items)
        }
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

    data class AppItem(val packageName: String, val label: String)

    private class Adapter(
        private val onClick: (String) -> Unit,
    ) : RecyclerView.Adapter<Adapter.VH>() {

        private val items = mutableListOf<AppItem>()

        fun submitList(list: List<AppItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            // Issue #5: rows now match `design/screens/screens-2.jsx`
            // <PickerRow/> — IconTile + app name + monospace package.
            // We inflate the dedicated row layout via ViewBinding so the
            // styling lives entirely in XML.
            val inflater = LayoutInflater.from(parent.context)
            val binding = PackagePickerRowBinding.inflate(inflater, parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.bind(item, onClick)
        }

        override fun getItemCount(): Int = items.size

        class VH(private val binding: PackagePickerRowBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: AppItem, onClick: (String) -> Unit) {
                binding.appName.text = item.label
                binding.packageName.text = item.packageName
                binding.iconLetter.text = item.label
                    .firstOrNull { !it.isWhitespace() }
                    ?.uppercaseChar()
                    ?.toString()
                    ?: "?"
                binding.root.setOnClickListener { onClick(item.packageName) }
            }
        }
    }

    companion object {
        private const val TAG = "PackagePickerBottomSheet"

        fun show(manager: FragmentManager, onPicked: (String) -> Unit) {
            val sheet = PackagePickerBottomSheet().apply { this.onPicked = onPicked }
            sheet.show(manager, TAG)
        }
    }
}
