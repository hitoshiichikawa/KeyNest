package com.example.keynest.ui.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.keynest.databinding.CredentialListRecentItemBinding
import com.example.keynest.domain.model.Credential
import com.example.keynest.util.IconLoader

/**
 * Horizontal carousel adapter for the "Recently used" section. Issue #9
 * Req 3.1, 3.5, NFR 1.3, NFR 3.1.
 *
 * Binds only the credential's label + username (NFR 1.3: no password /
 * ciphertext is ever exposed in a row view). The card's
 * contentDescription is set to a localised string that reads as
 * "<label>, used recently" for TalkBack (NFR 3.1).
 *
 * DiffUtil compares both `id` and `lastUsedAt` so the carousel refreshes
 * when an existing credential is bumped to the top via a fresh autofill
 * use.
 */
class RecentlyUsedCarouselAdapter(
    private val onItemClick: (Credential) -> Unit,
    private val iconLoader: IconLoader,
) : ListAdapter<Credential, RecentlyUsedCarouselAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CredentialListRecentItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick, iconLoader)
    }

    /**
     * Issue #43 Req 2.4: invalidate any in-flight IconLoader request on
     * recycle so a late PackageManager result is not painted onto the
     * rebound card.
     */
    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        iconLoader.cancel(holder.iconAppView)
    }

    class ViewHolder(
        private val binding: CredentialListRecentItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /** Exposed for [onViewRecycled] race-prevention. */
        internal val iconAppView get() = binding.iconApp

        fun bind(item: Credential, onClick: (Credential) -> Unit, iconLoader: IconLoader) {
            binding.textRecentLabel.text = item.label
            // Subtitle: username only -- the package name would push the
            // text outside the 160dp card width.
            binding.textRecentUsername.text = item.username
            // NFR 3.1: localise the screen-reader announcement.
            binding.cardRecent.contentDescription = binding.root.context.getString(
                com.example.keynest.R.string.credential_list_recent_card_a11y,
                item.label,
            )
            binding.cardRecent.setOnClickListener { onClick(item) }

            // Issue #43 Req 1.2: paint the real app icon (or fallback)
            // onto the carousel card's icon tile.
            iconLoader.loadInto(binding.iconApp, item.packageName)
        }
    }

    private companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Credential>() {
            override fun areItemsTheSame(oldItem: Credential, newItem: Credential): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Credential, newItem: Credential): Boolean =
                oldItem.label == newItem.label &&
                    oldItem.username == newItem.username &&
                    oldItem.lastUsedAt == newItem.lastUsedAt
        }
    }
}
