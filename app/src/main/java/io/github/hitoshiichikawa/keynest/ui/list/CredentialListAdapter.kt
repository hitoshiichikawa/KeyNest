package io.github.hitoshiichikawa.keynest.ui.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.hitoshiichikawa.keynest.R
import io.github.hitoshiichikawa.keynest.databinding.CredentialListItemBinding
import io.github.hitoshiichikawa.keynest.domain.model.Credential
import io.github.hitoshiichikawa.keynest.util.IconLoader

/**
 * RecyclerView adapter for the credential list (Req 1.5 / Issue #9 Req
 * 5.1, 6.2). Renders only the non-sensitive metadata of each [Credential]
 * - we never load decrypted data into the row (NFR 1.3).
 *
 * Issue #9 adds the per-row overflow button click callback; the existing
 * row click + long click delegates are preserved (Req 6.2 / 5.6).
 */
class CredentialListAdapter(
    private val onItemClick: (Credential) -> Unit,
    private val onItemLongClick: (Credential) -> Unit,
    private val onOverflowClick: (Credential, View) -> Unit,
    private val iconLoader: IconLoader,
) : ListAdapter<Credential, CredentialListAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CredentialListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onItemClick, onItemLongClick, onOverflowClick, iconLoader)
    }

    /**
     * Issue #43 Req 2.4: when a ViewHolder is recycled (RecyclerView is
     * about to rebind it to a different row), invalidate any in-flight
     * IconLoader request so a delayed PackageManager result does not
     * paint the wrong icon onto the now-rebound row.
     */
    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        iconLoader.cancel(holder.iconAppView)
    }

    class ViewHolder(private val binding: CredentialListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /** Exposed for [onViewRecycled] race-prevention. */
        internal val iconAppView get() = binding.iconApp

        fun bind(
            item: Credential,
            onClick: (Credential) -> Unit,
            onLongClick: (Credential) -> Unit,
            onOverflow: (Credential, View) -> Unit,
            iconLoader: IconLoader,
        ) {
            val ctx = binding.root.context

            binding.textLabel.text = item.label
            // Issue #29: username and package name are now rendered on
            // separate lines per design/screens-1.jsx CredCard (Req 5.6 /
            // 5.7). Username is not sensitive at rest (it is what we
            // already surface in the Autofill dataset chip; NFR 1.3 still
            // applies — we do NOT bind the decrypted password).
            binding.textSubtitle.text = item.username
            binding.textPackage.text = item.packageName

            // Issue #29 Req 7.x: signature chip. We use a single
            // LinearLayout chip view and swap background + tint + label by
            // the signature presence.
            val hasSignature = item.signatureSha256 != null
            if (hasSignature) {
                binding.chipSignature.setBackgroundResource(
                    R.drawable.kn_signature_chip_bg_success,
                )
                val color = ContextCompat.getColor(ctx, R.color.kn_success)
                binding.textSignature.setText(R.string.signature_match)
                binding.textSignature.setTextColor(color)
                binding.iconSignature.setImageResource(R.drawable.ic_shield_fill_16)
                binding.iconSignature.imageTintList =
                    android.content.res.ColorStateList.valueOf(color)
            } else {
                binding.chipSignature.setBackgroundResource(
                    R.drawable.kn_signature_chip_bg_warning,
                )
                val color = ContextCompat.getColor(ctx, R.color.kn_warning)
                binding.textSignature.setText(R.string.signature_missing)
                binding.textSignature.setTextColor(color)
                binding.iconSignature.setImageResource(R.drawable.ic_shield_outline_16)
                binding.iconSignature.imageTintList =
                    android.content.res.ColorStateList.valueOf(color)
            }

            // Issue #29 Req 6.5: the Credential domain model does not
            // carry a strength value. We pass null so the StrengthBar
            // stays GONE; once a strength field is added (separate
            // Issue), the adapter only needs to compute the enum here.
            binding.strengthBar.setStrength(null)

            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener {
                onLongClick(item)
                true
            }
            // Localise the overflow button's content description per row
            // so TalkBack reads "More actions for <label>" (NFR 3.1).
            binding.btnOverflow.contentDescription = ctx.getString(
                R.string.credential_list_row_overflow_a11y,
                item.label,
            )
            binding.btnOverflow.setOnClickListener { anchor ->
                onOverflow(item, anchor)
            }

            // Issue #43 Req 1.1: paint the real app icon (or the
            // initial-letter fallback when PackageManager throws).
            // Issue #51: the parent FrameLayout no longer carries
            // @drawable/kn_icon_tile_bg, so the 12dp rounded blue tile is
            // now drawn by InitialLetterDrawable itself on the fallback
            // path. Real icons are handed to ImageView as-is and the
            // system circular mask of AdaptiveIconDrawable no longer has
            // a parent tile peeking through its four corners.
            iconLoader.loadInto(binding.iconApp, item.packageName)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Credential>() {
            override fun areItemsTheSame(a: Credential, b: Credential): Boolean = a.id == b.id
            override fun areContentsTheSame(a: Credential, b: Credential): Boolean =
                a.packageName == b.packageName &&
                    a.username == b.username &&
                    a.label == b.label &&
                    a.updatedAt == b.updatedAt &&
                    a.signatureSha256 == b.signatureSha256
        }
    }
}
