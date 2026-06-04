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
import io.github.hitoshiichikawa.keynest.util.IconLoader

/**
 * RecyclerView adapter for the credential list (Req 1.5 / Issue #9 Req
 * 5.1 / 6.2; Issue #101 R1.x for the multi-viewType extension).
 *
 * Two row variants are rendered against the same layout
 * (`credential_list_item.xml`) — Issue #101 Q-9 settles the design on
 * a shared layout with the ViewHolder toggling visibility and the
 * icon resource per variant. The DRY win + the existing
 * Issue #29 / #43 / #51 tests continuing to pass against the same
 * layout was preferred over a per-variant XML.
 *
 * - `VIEW_TYPE_PASSWORD` (0): existing password row — IconLoader,
 *   label / username / packageName, signature chip + overflow button.
 * - `VIEW_TYPE_PASSKEY` (1): PassKey row — ic_passkey_24 vector via
 *   setImageResource, three-line fallback (displayName → rpDisplayName
 *   → rpId / etc per R1.3), signature chip + strength bar + overflow
 *   button hidden (R1.9 / R5.4), long-click consumed (R5.3).
 *
 * DiffUtil identifies rows by `stableId` (`"pw:<id>"` / `"pk:<credentialId>"`)
 * so a Long credential.id and a String credentialId can never collide
 * (R1.5 / D-8).
 *
 * NFR 1.3: only non-sensitive metadata is bound — we never load
 * decrypted password / private-key bytes into the row.
 */
class CredentialListAdapter(
    private val onItemClick: (CredentialListItem) -> Unit,
    private val onItemLongClick: (CredentialListItem) -> Unit,
    private val onOverflowClick: (CredentialListItem, View) -> Unit,
    private val iconLoader: IconLoader,
) : ListAdapter<CredentialListItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is CredentialListItem.Password -> VIEW_TYPE_PASSWORD
        is CredentialListItem.Passkey -> VIEW_TYPE_PASSKEY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = CredentialListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return when (viewType) {
            VIEW_TYPE_PASSWORD -> PasswordViewHolder(binding)
            VIEW_TYPE_PASSKEY -> PasskeyViewHolder(binding)
            else -> error("Unknown viewType=$viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is PasswordViewHolder -> {
                require(item is CredentialListItem.Password)
                holder.bind(item, onItemClick, onItemLongClick, onOverflowClick, iconLoader)
            }
            is PasskeyViewHolder -> {
                require(item is CredentialListItem.Passkey)
                holder.bind(item, onItemClick, onItemLongClick)
            }
            else -> error("Unknown ViewHolder type=${holder.javaClass.simpleName}")
        }
    }

    /**
     * Issue #43 Req 2.4: when a password ViewHolder is recycled, cancel
     * the IconLoader request so a late PackageManager result does not
     * paint the wrong icon onto the now-rebound row. PassKey rows paint
     * synchronously via `setImageResource(R.drawable.ic_passkey_24)` so
     * they do not participate in this race and need no cancellation.
     */
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is PasswordViewHolder) {
            iconLoader.cancel(holder.iconAppView)
        }
    }

    /**
     * Base ViewHolder that exposes the `iconApp` view for the
     * IconLoader cancel hook above (PasswordViewHolder needs it;
     * PasskeyViewHolder inherits it but does not use it).
     */
    internal abstract class BaseViewHolder(
        protected val binding: CredentialListItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        /** Exposed for [onViewRecycled] race-prevention. */
        internal val iconAppView get() = binding.iconApp
    }

    internal class PasswordViewHolder(
        binding: CredentialListItemBinding,
    ) : BaseViewHolder(binding) {

        fun bind(
            item: CredentialListItem.Password,
            onClick: (CredentialListItem) -> Unit,
            onLongClick: (CredentialListItem) -> Unit,
            onOverflow: (CredentialListItem, View) -> Unit,
            iconLoader: IconLoader,
        ) {
            val ctx = binding.root.context
            val c = item.credential

            binding.textLabel.text = c.label
            // Issue #29: username and package name are rendered on
            // separate lines per design/screens-1.jsx CredCard
            // (Req 5.6 / 5.7).
            binding.textSubtitle.text = c.username
            binding.textPackage.text = c.packageName

            // Issue #29 Req 7.x: signature chip — visibility restored
            // (PasskeyViewHolder may have hidden it on the previous
            // bind cycle in the recycled view pool).
            binding.chipSignature.visibility = View.VISIBLE
            val hasSignature = c.signatureSha256 != null
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
            // carry a strength value, so the StrengthBar stays hidden.
            // Defensive visibility restore in case a PasskeyViewHolder
            // hid it on a previous bind cycle.
            binding.strengthBar.visibility = View.VISIBLE
            binding.strengthBar.setStrength(null)

            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener {
                onLongClick(item)
                true
            }

            // Issue #101 R5.4 / R6.2: overflow button is visible for
            // password rows (existing Issue #9 / #29 behaviour) and the
            // icon contentDescription is reset to null (the layout XML
            // default — see credential_list_item.xml line 78). The
            // explicit null assignment guards against ViewHolder reuse
            // where a previous PasskeyViewHolder bind left the
            // contentDescription pointing at the PassKey label.
            binding.btnOverflow.visibility = View.VISIBLE
            binding.iconApp.contentDescription = null

            // Localise the overflow button's content description per
            // row so TalkBack reads "More actions for <label>"
            // (NFR 3.1).
            binding.btnOverflow.contentDescription = ctx.getString(
                R.string.credential_list_row_overflow_a11y,
                c.label,
            )
            binding.btnOverflow.setOnClickListener { anchor ->
                onOverflow(item, anchor)
            }

            // Issue #43 Req 1.1: paint the real app icon (or the
            // initial-letter fallback when PackageManager throws).
            iconLoader.loadInto(binding.iconApp, c.packageName)
        }
    }

    internal class PasskeyViewHolder(
        binding: CredentialListItemBinding,
    ) : BaseViewHolder(binding) {

        fun bind(
            item: CredentialListItem.Passkey,
            onClick: (CredentialListItem) -> Unit,
            onLongClick: (CredentialListItem) -> Unit,
        ) {
            val ctx = binding.root.context
            val p = item.passkey

            // Issue #101 R1.3: 3-line fallback chain.
            // Line 1: displayName -> rpDisplayName -> rpId
            binding.textLabel.text = p.displayName
                ?: p.rpDisplayName
                ?: p.rpId
            // Line 2: userDisplayName -> userName -> "(no user)"
            binding.textSubtitle.text = p.userDisplayName
                ?: p.userName
                ?: ctx.getString(R.string.credential_list_passkey_unknown_user)
            // Line 3: rpId (fixed)
            binding.textPackage.text = p.rpId

            // Issue #101 R1.9 / R5.4: signature chip, strength bar and
            // overflow button are PassKey-irrelevant. Hide them on this
            // viewType so the row stays clean.
            binding.chipSignature.visibility = View.GONE
            binding.strengthBar.visibility = View.GONE
            binding.btnOverflow.visibility = View.GONE

            // Issue #101 R1.2 / R1.8: paint the PassKey vector
            // directly. IconLoader is NOT used because PassKey rows
            // have no PackageManager-resolvable app icon.
            binding.iconApp.setImageResource(R.drawable.ic_passkey_24)
            // Issue #101 R6.1: TalkBack reads "PassKey" for the icon.
            binding.iconApp.contentDescription = ctx.getString(
                R.string.credential_list_passkey_kind_label,
            )

            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener {
                // Issue #101 R5.3: long-click is consumed but does
                // nothing — the password promptDelete dialog is not
                // applicable to PassKey rows in v1. Propagating the
                // event would also be wrong; absorbing it suppresses
                // the row ripple.
                onLongClick(item)
                true
            }
        }
    }

    companion object {
        /** Issue #101: viewType for password rows. */
        internal const val VIEW_TYPE_PASSWORD: Int = 0

        /** Issue #101: viewType for PassKey rows. */
        internal const val VIEW_TYPE_PASSKEY: Int = 1

        internal val DIFF = object : DiffUtil.ItemCallback<CredentialListItem>() {
            override fun areItemsTheSame(
                a: CredentialListItem,
                b: CredentialListItem,
            ): Boolean = a.stableId == b.stableId

            override fun areContentsTheSame(
                a: CredentialListItem,
                b: CredentialListItem,
            ): Boolean = when {
                a is CredentialListItem.Password && b is CredentialListItem.Password ->
                    a.credential.packageName == b.credential.packageName &&
                        a.credential.username == b.credential.username &&
                        a.credential.label == b.credential.label &&
                        a.credential.updatedAt == b.credential.updatedAt &&
                        a.credential.signatureSha256 == b.credential.signatureSha256
                a is CredentialListItem.Passkey && b is CredentialListItem.Passkey ->
                    a.passkey == b.passkey
                // areItemsTheSame already returned false for cross-variant
                // pairs (different stableId prefix), so reaching here is
                // unexpected. Defensive: treat as not-equal so DiffUtil
                // forces a rebind.
                else -> false
            }
        }
    }
}
