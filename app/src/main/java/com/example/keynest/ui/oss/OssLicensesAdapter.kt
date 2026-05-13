package com.example.keynest.ui.oss

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.keynest.databinding.OssLicensesItemBinding

/**
 * RecyclerView adapter for the OSS license list. Issue #10 Req 5.2,
 * 5.3.
 *
 * Each row shows the library name, license identifier, an optional
 * URL button, and (when expanded) the full license text. The full
 * text is GONE by default; tapping the row toggles [OssEntry.isExpanded]
 * which the adapter resubmits via [submitList].
 *
 * The adapter delegates URL click handling to the [onUrlClick]
 * callback because the Activity wraps the Intent dispatch in a
 * try / catch.
 */
internal class OssLicensesAdapter(
    private val onToggle: (OssEntry) -> Unit,
    private val onUrlClick: (String) -> Unit,
) : ListAdapter<OssEntry, OssLicensesAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = OssLicensesItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: OssLicensesItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: OssEntry) {
            binding.textName.text = entry.name
            binding.textLicense.text = entry.license
            if (entry.url.isNullOrBlank()) {
                binding.btnUrl.visibility = View.GONE
            } else {
                binding.btnUrl.visibility = View.VISIBLE
                binding.btnUrl.setOnClickListener { onUrlClick(entry.url) }
            }
            binding.textBody.visibility = if (entry.isExpanded) View.VISIBLE else View.GONE
            binding.textBody.text = entry.text
            binding.root.setOnClickListener { onToggle(entry) }
        }
    }

    companion object {
        private val DIFF: DiffUtil.ItemCallback<OssEntry> = object : DiffUtil.ItemCallback<OssEntry>() {
            override fun areItemsTheSame(oldItem: OssEntry, newItem: OssEntry): Boolean =
                oldItem.name == newItem.name
            override fun areContentsTheSame(oldItem: OssEntry, newItem: OssEntry): Boolean =
                oldItem == newItem
        }
    }
}
