package com.example.keynest.ui.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.keynest.R
import com.example.keynest.databinding.CredentialListItemBinding
import com.example.keynest.domain.model.Credential

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
        holder.bind(item, onItemClick, onItemLongClick, onOverflowClick)
    }

    class ViewHolder(private val binding: CredentialListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: Credential,
            onClick: (Credential) -> Unit,
            onLongClick: (Credential) -> Unit,
            onOverflow: (Credential, View) -> Unit,
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
