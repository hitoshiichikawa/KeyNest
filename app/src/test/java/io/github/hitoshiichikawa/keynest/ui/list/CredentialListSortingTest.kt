package io.github.hitoshiichikawa.keynest.ui.list

import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.domain.model.Credential
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import org.junit.Test

/**
 * Unit tests for [CredentialListSorting] (Issue #101 / Phase 4 of
 * umbrella #89). Covers R1.4:
 *  - `lastUsedAt DESC` for non-null values
 *  - `null` lastUsedAt sorts to the tail (`nullsLast`)
 *  - `createdAt DESC` is the tiebreaker
 *  - the sort is stable for fully-equal sort keys
 *  - the Comparator is symmetric (passwords and passkeys interleave
 *    correctly without one variant always landing on top)
 */
class CredentialListSortingTest {

    // ---- byLastUsedThenCreatedDesc Comparator --------------------------

    @Test
    fun byLastUsedThenCreatedDesc_nonNullLastUsed_ordersByDesc() {
        val older = passwordItem(id = 1, lastUsedAt = 100L, createdAt = 50L)
        val newer = passwordItem(id = 2, lastUsedAt = 200L, createdAt = 50L)

        val sorted = listOf(older, newer).sortedWith(
            CredentialListSorting.byLastUsedThenCreatedDesc,
        )

        assertThat(sorted).containsExactly(newer, older).inOrder()
    }

    @Test
    fun byLastUsedThenCreatedDesc_nullLastUsed_landsAfterNonNull() {
        val withLastUsed = passwordItem(id = 1, lastUsedAt = 10L, createdAt = 0L)
        val withoutLastUsed = passkeyItem(credentialId = "pk-null", lastUsedAt = null, createdAt = 999L)

        val sorted = listOf(withoutLastUsed, withLastUsed).sortedWith(
            CredentialListSorting.byLastUsedThenCreatedDesc,
        )

        // Non-null lastUsedAt always sorts before null, regardless of
        // createdAt magnitude.
        assertThat(sorted).containsExactly(withLastUsed, withoutLastUsed).inOrder()
    }

    @Test
    fun byLastUsedThenCreatedDesc_bothNullLastUsed_ordersByCreatedDesc() {
        val older = passwordItem(id = 1, lastUsedAt = null, createdAt = 100L)
        val newer = passkeyItem(credentialId = "pk", lastUsedAt = null, createdAt = 200L)

        val sorted = listOf(older, newer).sortedWith(
            CredentialListSorting.byLastUsedThenCreatedDesc,
        )

        // null vs null falls through to createdAt DESC.
        assertThat(sorted).containsExactly(newer, older).inOrder()
    }

    @Test
    fun byLastUsedThenCreatedDesc_sameLastUsed_breaksByCreatedDesc() {
        val older = passwordItem(id = 1, lastUsedAt = 100L, createdAt = 10L)
        val newer = passkeyItem(credentialId = "pk", lastUsedAt = 100L, createdAt = 20L)

        val sorted = listOf(older, newer).sortedWith(
            CredentialListSorting.byLastUsedThenCreatedDesc,
        )

        assertThat(sorted).containsExactly(newer, older).inOrder()
    }

    @Test
    fun byLastUsedThenCreatedDesc_fullyEqualKeys_isStable() {
        val a = passwordItem(id = 1, lastUsedAt = 100L, createdAt = 100L)
        val b = passkeyItem(credentialId = "pk-b", lastUsedAt = 100L, createdAt = 100L)
        val c = passwordItem(id = 2, lastUsedAt = 100L, createdAt = 100L)

        val sorted = listOf(a, b, c).sortedWith(
            CredentialListSorting.byLastUsedThenCreatedDesc,
        )

        // TimSort is stable: insertion order is preserved when the
        // comparator says all rows are equal.
        assertThat(sorted).containsExactly(a, b, c).inOrder()
    }

    @Test
    fun byLastUsedThenCreatedDesc_longBoundary_handlesMaxValue() {
        val almost = passwordItem(id = 1, lastUsedAt = Long.MAX_VALUE - 1L, createdAt = 0L)
        val max = passkeyItem(credentialId = "pk", lastUsedAt = Long.MAX_VALUE, createdAt = 0L)

        val sorted = listOf(almost, max).sortedWith(
            CredentialListSorting.byLastUsedThenCreatedDesc,
        )

        assertThat(sorted).containsExactly(max, almost).inOrder()
    }

    @Test
    fun byLastUsedThenCreatedDesc_variantsInterleave_correctly() {
        // Mixed list with passwords and passkeys at different positions
        // along the sort axis — confirms no variant gets a privileged
        // position.
        val pw1 = passwordItem(id = 1, lastUsedAt = 300L, createdAt = 100L)
        val pw2 = passwordItem(id = 2, lastUsedAt = 100L, createdAt = 50L)
        val pk1 = passkeyItem(credentialId = "pk-1", lastUsedAt = 200L, createdAt = 200L)
        val pk2 = passkeyItem(credentialId = "pk-2", lastUsedAt = null, createdAt = 150L)

        val sorted = listOf(pk2, pw2, pk1, pw1).sortedWith(
            CredentialListSorting.byLastUsedThenCreatedDesc,
        )

        assertThat(sorted).containsExactly(pw1, pk1, pw2, pk2).inOrder()
    }

    // ---- mergeAndSort entry point --------------------------------------

    @Test
    fun mergeAndSort_emptyInputs_returnsEmptyList() {
        val result = CredentialListSorting.mergeAndSort(emptyList(), emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun mergeAndSort_onlyPasswords_returnsAllAsPasswordVariants() {
        val a = passwordCredential(id = 1, lastUsedAt = 100L)
        val b = passwordCredential(id = 2, lastUsedAt = 200L)

        val merged = CredentialListSorting.mergeAndSort(listOf(a, b), emptyList())

        assertThat(merged).hasSize(2)
        assertThat(merged.all { it is CredentialListItem.Password }).isTrue()
        // b has the newer lastUsedAt -> it should sort first.
        assertThat((merged[0] as CredentialListItem.Password).credential.id.value).isEqualTo(2L)
    }

    @Test
    fun mergeAndSort_onlyPasskeys_returnsAllAsPasskeyVariants() {
        val a = passkeyModel("pk-a", lastUsedAt = 100L)
        val b = passkeyModel("pk-b", lastUsedAt = 200L)

        val merged = CredentialListSorting.mergeAndSort(emptyList(), listOf(a, b))

        assertThat(merged).hasSize(2)
        assertThat(merged.all { it is CredentialListItem.Passkey }).isTrue()
        assertThat((merged[0] as CredentialListItem.Passkey).passkey.credentialId).isEqualTo("pk-b")
    }

    @Test
    fun mergeAndSort_mixedInputs_combinesAndReSorts() {
        // design.md §15.1 fixture.
        val pw1 = passwordCredential(id = 1, lastUsedAt = 300L, createdAt = 100L)
        val pw2 = passwordCredential(id = 2, lastUsedAt = 100L, createdAt = 50L)
        val pk1 = passkeyModel("pk-1", lastUsedAt = 200L, createdAt = 200L)
        val pk2 = passkeyModel("pk-2", lastUsedAt = null, createdAt = 150L)

        val merged = CredentialListSorting.mergeAndSort(listOf(pw1, pw2), listOf(pk1, pk2))

        assertThat(merged).hasSize(4)
        // Expected: pw1 (300) -> pk1 (200) -> pw2 (100) -> pk2 (null tail)
        assertThat(merged[0].stableId).isEqualTo("pw:1")
        assertThat(merged[1].stableId).isEqualTo("pk:pk-1")
        assertThat(merged[2].stableId).isEqualTo("pw:2")
        assertThat(merged[3].stableId).isEqualTo("pk:pk-2")
    }

    @Test
    fun mergeAndSort_preservesAllInputs_inOutputCount() {
        // Defensive: the merged list size must equal the union, never
        // drop rows.
        val passwords = (1..5).map { passwordCredential(id = it.toLong(), lastUsedAt = it.toLong()) }
        val passkeys = (1..3).map { passkeyModel("pk-$it", lastUsedAt = it.toLong()) }

        val merged = CredentialListSorting.mergeAndSort(passwords, passkeys)

        assertThat(merged).hasSize(8)
    }

    // ---- helpers -------------------------------------------------------

    private fun passwordItem(
        id: Long,
        lastUsedAt: Long?,
        createdAt: Long,
    ): CredentialListItem.Password = CredentialListItem.Password(
        passwordCredential(id = id, lastUsedAt = lastUsedAt, createdAt = createdAt),
    )

    private fun passwordCredential(
        id: Long,
        lastUsedAt: Long? = null,
        createdAt: Long = 0L,
    ): Credential = Credential(
        id = CredentialId(id),
        packageName = "com.example.$id",
        username = "u-$id",
        label = "L-$id",
        signatureSha256 = null,
        signatureCapturedAt = null,
        createdAt = createdAt,
        updatedAt = 0L,
        lastUsedAt = lastUsedAt,
    )

    private fun passkeyItem(
        credentialId: String,
        lastUsedAt: Long?,
        createdAt: Long,
    ): CredentialListItem.Passkey = CredentialListItem.Passkey(
        passkeyModel(credentialId, lastUsedAt = lastUsedAt, createdAt = createdAt),
    )

    private fun passkeyModel(
        credentialId: String,
        lastUsedAt: Long? = null,
        createdAt: Long = 0L,
    ): PasskeyDisplayModel = PasskeyDisplayModel(
        credentialId = credentialId,
        rpId = "example.com",
        rpDisplayName = null,
        userName = null,
        userDisplayName = null,
        displayName = null,
        isDiscoverable = true,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
    )
}
