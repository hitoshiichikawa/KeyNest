package io.github.hitoshiichikawa.keynest.ui.list

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.R
import io.github.hitoshiichikawa.keynest.domain.model.Credential
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.util.IconLoader
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [CredentialListAdapter] (Issue #101 / Phase 4
 * of umbrella #89). Covers:
 *  - getItemViewType returns the right viewType per variant (R4.4)
 *  - onCreateViewHolder picks the right internal ViewHolder class
 *  - PasswordViewHolder preserves the existing chip_signature visible
 *    + overflow visible behaviour (Issue #29 / #43 baselines)
 *  - PasskeyViewHolder paints ic_passkey_24, hides chip_signature /
 *    strength_bar / btn_overflow (R1.9 / R5.4) and sets the icon
 *    contentDescription to the localized PassKey string (R6.1)
 *  - DiffUtil identity uses the variant-prefixed stableId (R1.5 / D-8)
 *
 * The KeyNest project pattern is Robolectric on `:app:testDebugUnitTest`
 * (see CredentialListEmptyStateTest / PasskeyRepositoryTest) — that is
 * preferred over `connectedDebugAndroidTest` because of the CI
 * constraint described in tasks.md §T-09.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CredentialListAdapterInstrumentationTest {

    private lateinit var themedContext: Context
    private lateinit var parent: FrameLayout
    private lateinit var adapter: CredentialListAdapter
    private val iconLoader = mockk<IconLoader>(relaxed = true)

    @Before
    fun setUp() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        themedContext = ContextThemeWrapper(appContext, R.style.Theme_KeyNest)
        parent = FrameLayout(themedContext)
        adapter = CredentialListAdapter(
            onItemClick = {},
            onItemLongClick = {},
            onOverflowClick = { _, _ -> },
            iconLoader = iconLoader,
        )
    }

    // ---- R4.4: getItemViewType + ViewHolder class --------------------------

    @Test
    fun getItemViewType_returnsPasswordCode_forPasswordVariant() {
        adapter.submitList(listOf(passwordItem(1)))

        assertThat(adapter.getItemViewType(0))
            .isEqualTo(CredentialListAdapter.VIEW_TYPE_PASSWORD)
    }

    @Test
    fun getItemViewType_returnsPasskeyCode_forPasskeyVariant() {
        adapter.submitList(listOf(passkeyItem("pk-1")))

        assertThat(adapter.getItemViewType(0))
            .isEqualTo(CredentialListAdapter.VIEW_TYPE_PASSKEY)
    }

    @Test
    fun onCreateViewHolder_password_returnsPasswordViewHolderClass() {
        val holder = adapter.onCreateViewHolder(
            parent,
            CredentialListAdapter.VIEW_TYPE_PASSWORD,
        )

        assertThat(holder).isInstanceOf(CredentialListAdapter.PasswordViewHolder::class.java)
    }

    @Test
    fun onCreateViewHolder_passkey_returnsPasskeyViewHolderClass() {
        val holder = adapter.onCreateViewHolder(
            parent,
            CredentialListAdapter.VIEW_TYPE_PASSKEY,
        )

        assertThat(holder).isInstanceOf(CredentialListAdapter.PasskeyViewHolder::class.java)
    }

    // ---- R4.5 / R1.2 / R1.8 / R1.9 / R5.4 / R6.1: bind behaviour ---------

    @Test
    fun bindPasskey_paintsIcPasskey24_andHidesChipSignatureStrengthBarAndOverflow() {
        // Drive an actual onBindViewHolder so the internal `bind`
        // wires fire — same path the production code takes.
        adapter.submitList(listOf(passkeyItem("pk-1")))
        val holder = adapter.onCreateViewHolder(
            parent,
            CredentialListAdapter.VIEW_TYPE_PASSKEY,
        )
        adapter.bindViewHolder(holder, 0)

        val passkeyHolder = holder as CredentialListAdapter.PasskeyViewHolder
        val iconView = passkeyHolder.iconAppView
        // R1.8: icon resource is the ic_passkey_24 vector.
        val drawable = iconView.drawable
        assertThat(drawable).isNotNull()
        // Robolectric Shadow exposes the resource id the ImageView was
        // painted with via setImageResource — round-trip check.
        assertThat(shadowOf(drawable).createdFromResId)
            .isEqualTo(R.drawable.ic_passkey_24)

        // R1.9 + R5.4: chip_signature / strength_bar / overflow hidden.
        val itemView = holder.itemView
        assertThat(itemView.findViewById<View>(R.id.chip_signature).visibility)
            .isEqualTo(View.GONE)
        assertThat(itemView.findViewById<View>(R.id.strength_bar).visibility)
            .isEqualTo(View.GONE)
        assertThat(itemView.findViewById<View>(R.id.btn_overflow).visibility)
            .isEqualTo(View.GONE)
    }

    @Test
    fun bindPasskey_setsIconContentDescription_toPassKey() {
        // R6.1: TalkBack reads "PassKey" on the icon for PassKey rows.
        adapter.submitList(listOf(passkeyItem("pk-1")))
        val holder = adapter.onCreateViewHolder(
            parent,
            CredentialListAdapter.VIEW_TYPE_PASSKEY,
        )
        adapter.bindViewHolder(holder, 0)

        val expected = themedContext.getString(R.string.credential_list_passkey_kind_label)
        val passkeyHolder = holder as CredentialListAdapter.PasskeyViewHolder
        assertThat(passkeyHolder.iconAppView.contentDescription.toString())
            .isEqualTo(expected)
    }

    @Test
    fun bindPassword_setsIconContentDescription_toNull() {
        // R6.2: password row icon stays without a contentDescription so
        // TalkBack reads the parent row label instead. The XML default
        // is @null; the bind explicitly re-asserts it (defensive
        // against PasskeyViewHolder reuse).
        adapter.submitList(listOf(passwordItem(1, label = "GitHub")))
        val holder = adapter.onCreateViewHolder(
            parent,
            CredentialListAdapter.VIEW_TYPE_PASSWORD,
        )
        adapter.bindViewHolder(holder, 0)

        val passwordHolder = holder as CredentialListAdapter.PasswordViewHolder
        assertThat(passwordHolder.iconAppView.contentDescription).isNull()
    }

    @Test
    fun bindPassword_keepsExistingSignatureChipAndOverflowVisibility() {
        // Issue #29 baseline: password rows show the signature chip
        // and the overflow button.
        adapter.submitList(listOf(passwordItem(1, hasSignature = true)))
        val holder = adapter.onCreateViewHolder(
            parent,
            CredentialListAdapter.VIEW_TYPE_PASSWORD,
        )
        adapter.bindViewHolder(holder, 0)

        val itemView = holder.itemView
        assertThat(itemView.findViewById<View>(R.id.chip_signature).visibility)
            .isEqualTo(View.VISIBLE)
        assertThat(itemView.findViewById<View>(R.id.btn_overflow).visibility)
            .isEqualTo(View.VISIBLE)
    }

    @Test
    fun bindPasskey_textLabel_fallsBackThroughDisplayName_thenRpDisplayName_thenRpId() {
        // R1.3 fallback chain test for line 1. Use a fresh adapter per
        // case so the AsyncListDiffer cannot return a stale row at
        // position 0 (ListAdapter's submitList is asynchronous, and
        // chaining submits on the same adapter requires explicit looper
        // drain — splitting into three adapters avoids that subtlety).
        assertThat(labelTextForPasskey(passkeyItem(
            credentialId = "pk-a",
            displayName = "Custom label",
            rpDisplayName = "RP shown",
            rpId = "rp.example",
        ))).isEqualTo("Custom label")

        assertThat(labelTextForPasskey(passkeyItem(
            credentialId = "pk-b",
            displayName = null,
            rpDisplayName = "RP shown",
            rpId = "rp.example",
        ))).isEqualTo("RP shown")

        assertThat(labelTextForPasskey(passkeyItem(
            credentialId = "pk-c",
            displayName = null,
            rpDisplayName = null,
            rpId = "rp.example",
        ))).isEqualTo("rp.example")
    }

    @Test
    fun bindPasskey_textSubtitle_fallsBackThroughUserDisplayName_thenUserName_thenUnknownString() {
        // R1.3 fallback chain test for line 2. Fresh-adapter-per-case
        // strategy — see textLabel test above for the rationale.
        assertThat(subtitleTextForPasskey(passkeyItem(
            credentialId = "pk-a",
            userDisplayName = "Alice Cooper",
            userName = "alice",
        ))).isEqualTo("Alice Cooper")

        assertThat(subtitleTextForPasskey(passkeyItem(
            credentialId = "pk-b",
            userDisplayName = null,
            userName = "alice",
        ))).isEqualTo("alice")

        val unknown =
            themedContext.getString(R.string.credential_list_passkey_unknown_user)
        assertThat(subtitleTextForPasskey(passkeyItem(
            credentialId = "pk-c",
            userDisplayName = null,
            userName = null,
        ))).isEqualTo(unknown)
    }

    /**
     * Build a fresh adapter, submit a single PassKey item, drain the
     * looper so AsyncListDiffer has applied the submission, and read
     * back the bound `text_label`.
     */
    private fun labelTextForPasskey(item: CredentialListItem.Passkey): String {
        val freshAdapter = CredentialListAdapter(
            onItemClick = {},
            onItemLongClick = {},
            onOverflowClick = { _, _ -> },
            iconLoader = iconLoader,
        )
        freshAdapter.submitList(listOf(item))
        shadowOf(Looper.getMainLooper()).idle()
        val holder = freshAdapter.onCreateViewHolder(
            parent,
            CredentialListAdapter.VIEW_TYPE_PASSKEY,
        )
        freshAdapter.bindViewHolder(holder, 0)
        return holder.itemView
            .findViewById<android.widget.TextView>(R.id.text_label)
            .text
            .toString()
    }

    /** Same shape as [labelTextForPasskey], reads `text_subtitle`. */
    private fun subtitleTextForPasskey(item: CredentialListItem.Passkey): String {
        val freshAdapter = CredentialListAdapter(
            onItemClick = {},
            onItemLongClick = {},
            onOverflowClick = { _, _ -> },
            iconLoader = iconLoader,
        )
        freshAdapter.submitList(listOf(item))
        shadowOf(Looper.getMainLooper()).idle()
        val holder = freshAdapter.onCreateViewHolder(
            parent,
            CredentialListAdapter.VIEW_TYPE_PASSKEY,
        )
        freshAdapter.bindViewHolder(holder, 0)
        return holder.itemView
            .findViewById<android.widget.TextView>(R.id.text_subtitle)
            .text
            .toString()
    }

    @Test
    fun bindPasskey_textPackage_isAlwaysRpId() {
        // R1.3: line 3 is always rpId — even when displayName / rpDisplayName
        // are present and resolved into line 1.
        adapter.submitList(
            listOf(
                passkeyItem(
                    credentialId = "pk-1",
                    displayName = "Custom",
                    rpDisplayName = "RP",
                    rpId = "rp.example.com",
                ),
            ),
        )
        val holder = adapter.onCreateViewHolder(
            parent,
            CredentialListAdapter.VIEW_TYPE_PASSKEY,
        )
        adapter.bindViewHolder(holder, 0)

        val pkg = holder.itemView.findViewById<android.widget.TextView>(R.id.text_package)
        assertThat(pkg.text.toString()).isEqualTo("rp.example.com")
    }

    @Test
    fun bindPassword_paintsAppIconViaIconLoader_notSetImageResource() {
        // R1.8 inverse: password rows continue to use IconLoader for the
        // app icon resolution path. We verify IconLoader.loadInto was
        // called with the expected packageName.
        every { iconLoader.loadInto(any(), any()) } returns Unit
        adapter.submitList(listOf(passwordItem(1, packageName = "com.example.app")))
        val holder = adapter.onCreateViewHolder(
            parent,
            CredentialListAdapter.VIEW_TYPE_PASSWORD,
        )
        adapter.bindViewHolder(holder, 0)

        io.mockk.verify { iconLoader.loadInto(any(), "com.example.app") }
    }

    // ---- R1.5 / D-8: DiffUtil collision prevention -----------------------

    @Test
    fun diffAreItemsTheSame_passwordIdOne_andPasskeyCredentialIdOne_returnsFalse() {
        // Critical D-8 / R1.5 invariant: a password row with id == 1L
        // and a PassKey row with credentialId == "1" must NEVER collide.
        val pw = CredentialListItem.Password(
            Credential(
                id = CredentialId(1L),
                packageName = "com.example",
                username = "alice",
                label = "L",
                signatureSha256 = null,
                signatureCapturedAt = null,
                createdAt = 0L,
                updatedAt = 0L,
                lastUsedAt = null,
            ),
        )
        val pk = CredentialListItem.Passkey(
            PasskeyDisplayModel(
                credentialId = "1",
                rpId = "example.com",
                rpDisplayName = null,
                userName = null,
                userDisplayName = null,
                displayName = null,
                isDiscoverable = true,
                createdAt = 0L,
                lastUsedAt = null,
            ),
        )

        val sameItems = CredentialListAdapter.DIFF.areItemsTheSame(pw, pk)
        val sameContents = CredentialListAdapter.DIFF.areContentsTheSame(pw, pk)

        // Both must reject the cross-variant pair — areItemsTheSame
        // because the stableId prefix differs (`pw:` vs `pk:`),
        // areContentsTheSame because the `else -> false` defensive
        // branch handles cross-variant pairs.
        assertThat(sameItems).isFalse()
        assertThat(sameContents).isFalse()
    }

    @Test
    fun diffAreItemsTheSame_samePasskeyCredentialId_returnsTrue() {
        val a = CredentialListItem.Passkey(
            PasskeyDisplayModel(
                credentialId = "shared-id",
                rpId = "example.com",
                rpDisplayName = null,
                userName = "alice",
                userDisplayName = null,
                displayName = null,
                isDiscoverable = true,
                createdAt = 0L,
                lastUsedAt = null,
            ),
        )
        val b = CredentialListItem.Passkey(
            PasskeyDisplayModel(
                credentialId = "shared-id",
                rpId = "example.com",
                rpDisplayName = "Example",
                userName = "alice-renamed",
                userDisplayName = null,
                displayName = null,
                isDiscoverable = true,
                createdAt = 0L,
                lastUsedAt = 100L,
            ),
        )

        assertThat(CredentialListAdapter.DIFF.areItemsTheSame(a, b)).isTrue()
        // areContentsTheSame compares all 9 PasskeyDisplayModel fields,
        // so updating userName + rpDisplayName + lastUsedAt should
        // report not-equal (forces a rebind).
        assertThat(CredentialListAdapter.DIFF.areContentsTheSame(a, b)).isFalse()
    }

    @Test
    fun diffAreItemsTheSame_samePasswordId_returnsTrue() {
        val a = CredentialListItem.Password(
            Credential(
                id = CredentialId(7L),
                packageName = "com.example.a",
                username = "alice",
                label = "A",
                signatureSha256 = null,
                signatureCapturedAt = null,
                createdAt = 0L,
                updatedAt = 0L,
                lastUsedAt = null,
            ),
        )
        val b = CredentialListItem.Password(
            Credential(
                id = CredentialId(7L),
                packageName = "com.example.a",
                username = "alice-renamed",
                label = "A",
                signatureSha256 = null,
                signatureCapturedAt = null,
                createdAt = 0L,
                updatedAt = 1L,
                lastUsedAt = null,
            ),
        )

        assertThat(CredentialListAdapter.DIFF.areItemsTheSame(a, b)).isTrue()
        assertThat(CredentialListAdapter.DIFF.areContentsTheSame(a, b)).isFalse()
    }

    // ---- helpers --------------------------------------------------------

    private fun passwordItem(
        id: Long,
        label: String = "L-$id",
        username: String = "u-$id",
        packageName: String = "com.example.$id",
        hasSignature: Boolean = false,
    ): CredentialListItem.Password = CredentialListItem.Password(
        Credential(
            id = CredentialId(id),
            packageName = packageName,
            username = username,
            label = label,
            signatureSha256 = if (hasSignature) {
                io.github.hitoshiichikawa.keynest.domain.model.SigningHash(ByteArray(32) { 0x11.toByte() })
            } else {
                null
            },
            signatureCapturedAt = if (hasSignature) 1L else null,
            createdAt = 0L,
            updatedAt = 0L,
            lastUsedAt = null,
        ),
    )

    private fun passkeyItem(
        credentialId: String,
        rpId: String = "example.com",
        rpDisplayName: String? = "Example",
        userName: String? = "alice",
        userDisplayName: String? = "Alice",
        displayName: String? = null,
        isDiscoverable: Boolean = true,
    ): CredentialListItem.Passkey = CredentialListItem.Passkey(
        PasskeyDisplayModel(
            credentialId = credentialId,
            rpId = rpId,
            rpDisplayName = rpDisplayName,
            userName = userName,
            userDisplayName = userDisplayName,
            displayName = displayName,
            isDiscoverable = isDiscoverable,
            createdAt = 0L,
            lastUsedAt = null,
        ),
    )
}
