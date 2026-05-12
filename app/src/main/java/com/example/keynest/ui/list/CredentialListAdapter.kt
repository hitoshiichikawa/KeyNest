package com.example.keynest.ui.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.keynest.databinding.CredentialListItemBinding
import com.example.keynest.domain.model.Credential

/**
 * RecyclerView adapter for the credential list (Req 1.5). Renders the
 * non-sensitive metadata of each [Credential] - we never load decrypted
 * data into the row.
 */
class CredentialListAdapter(
    private val onItemClick: (Credential) -> Unit,
    private val onItemLongClick: (Credential) -> Unit,
) : ListAdapter<Credential, CredentialListAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CredentialListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onItemClick, onItemLongClick)
    }

    class ViewHolder(private val binding: CredentialListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: Credential,
            onClick: (Credential) -> Unit,
            onLongClick: (Credential) -> Unit,
        ) {
            binding.textLabel.text = item.label
            // IconTile letter: first non-whitespace character of the label.
            // Issue #5 AC 4.3.1 — surface a single-character monogram in
            // the brand-tinted tile (the design uses the label's leading
            // glyph, falling back to a generic key when blank).
            binding.textIconLetter.text = item.label
                .firstOrNull { !it.isWhitespace() }
                ?.uppercaseChar()
                ?.toString()
                ?: "K"
            // Subtitle: username @ packageName. Username is not sensitive at
            // rest (it's also what we surface in the Autofill dataset chip).
            binding.textSubtitle.text = "${item.username} @ ${item.packageName}"
            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Credential>() {
            override fun areItemsTheSame(a: Credential, b: Credential): Boolean = a.id == b.id
            override fun areContentsTheSame(a: Credential, b: Credential): Boolean =
                a.packageName == b.packageName &&
                    a.username == b.username &&
                    a.label == b.label &&
                    a.updatedAt == b.updatedAt
        }
    }
}
