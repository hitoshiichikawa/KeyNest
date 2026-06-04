package io.github.hitoshiichikawa.keynest.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.hitoshiichikawa.keynest.domain.model.Credential
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.CredentialSortOrder
import io.github.hitoshiichikawa.keynest.domain.usecase.DeleteCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.DuplicateCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.ListCredentialsUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.ListPasskeysUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.ObserveRecentlyUsedUseCase
import io.github.hitoshiichikawa.keynest.util.SafeLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs [CredentialListActivity]. Issue #9 Req 1.x, 2.x, 3.x, 4.x, 5.x,
 * 6.x; extended for Issue #101 (Phase 4 of umbrella #89) to render
 * passwords and PassKeys in the same RecyclerView.
 *
 * Four MVI-style input StateFlows ([query] / [filter] / [sort] /
 * [kindFilter]) feed into a single composed [uiState] StateFlow that
 * the Activity collects. The composition pipeline is:
 *
 *   1. Re-subscribe to `listUseCase(sort)` whenever the sort order
 *      changes (Req 4.3) using flatMapLatest. The DAO returns rows in
 *      the requested order.
 *   2. Subscribe to `listPasskeysUseCase()` — Room invalidation tracker
 *      re-emits whenever the registration ceremony (#99) or the
 *      authentication ceremony (#100) writes to the `passkeys` table.
 *   3. `combine` the two flows and the four user inputs:
 *      a. [CredentialListSorting.mergeAndSort] interleaves password
 *         and PassKey rows by `lastUsedAt DESC, createdAt DESC,
 *         NULL last` (Issue #101 R1.4).
 *      b. [applyFilter] keeps the existing signature-chip behaviour
 *         for password rows; PassKey rows pass through unchanged
 *         because they have no `signatureSha256` concept (R2.7).
 *      c. [applyKindFilter] is the seam reserved for the future kind
 *         filter chip (Q-2); v1 always pins to `KindFilter.All`.
 *      d. [applySearch] matches the query against both variants'
 *         metadata (case-insensitive contains; R2.1 / R2.2 / R2.5).
 *   4. Combine with `recentUseCase()` to expose the carousel feed; that
 *      flow stays password-only (PassKey carousel integration is out
 *      of scope per Q-6).
 *
 * Side-channels:
 * - [duplicateResult] emits one event per duplicate attempt so the
 *   Activity can render a Snackbar.
 *
 * Logging policy (NFR 2.3): only counts / kinds are written to
 * SafeLogger here. Raw credentialId / rpId / userName / query bytes
 * never reach logcat.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CredentialListViewModel(
    private val listUseCase: ListCredentialsUseCase,
    private val recentUseCase: ObserveRecentlyUsedUseCase,
    private val duplicateUseCase: DuplicateCredentialUseCase,
    private val deleteUseCase: DeleteCredentialUseCase,
    private val listPasskeysUseCase: ListPasskeysUseCase,
) : ViewModel() {

    private val query: MutableStateFlow<String> = MutableStateFlow("")
    private val filter: MutableStateFlow<CredentialFilter> =
        MutableStateFlow(CredentialFilter.None)
    private val sort: MutableStateFlow<CredentialSortOrder> =
        MutableStateFlow(CredentialSortOrder.UpdatedAtDesc)

    /**
     * v1 is always pinned to [KindFilter.All] — there is no UI surface
     * for changing it (Q-2). The State plumbing exists so a follow-up
     * Issue can add a chip without restructuring the combine pipeline.
     */
    private val kindFilter: MutableStateFlow<KindFilter> =
        MutableStateFlow(KindFilter.All)

    private val sortedPasswordsFlow: Flow<List<Credential>> = sort.flatMapLatest { order ->
        listUseCase(order)
    }

    private val passkeysFlow: Flow<List<PasskeyDisplayModel>> = listPasskeysUseCase()

    /**
     * Carries the four small inputs through one Quad-shaped combine
     * so the outer 3-arg `combine` (inputs + mainList + recentList) stays
     * within the StateFlow combine arity limits.
     */
    private data class InputState(
        val query: String,
        val filter: CredentialFilter,
        val sort: CredentialSortOrder,
        val kindFilter: KindFilter,
    )

    private val inputStateFlow: Flow<InputState> = combine(
        query, filter, sort, kindFilter,
    ) { q, f, s, kf -> InputState(q, f, s, kf) }

    private val mainListFlow: Flow<List<CredentialListItem>> = combine(
        inputStateFlow, sortedPasswordsFlow, passkeysFlow,
    ) { input, pwList, pkList ->
        // Issue #101 R1.4 / R2.7 / Q-2: pure in-memory pipeline. All
        // four steps are pure functions so the network surface stays
        // empty (Req 1.5).
        val merged = CredentialListSorting.mergeAndSort(pwList, pkList)
        val afterChip = applyFilter(merged, input.filter)
        val afterKind = applyKindFilter(afterChip, input.kindFilter)
        if (input.query.isBlank()) afterKind else applySearch(afterKind, input.query)
    }

    val uiState: StateFlow<CredentialListUiState> = combine(
        inputStateFlow, mainListFlow, recentUseCase(),
    ) { input, mainList, recentList ->
        CredentialListUiState(
            query = input.query,
            filter = input.filter,
            sort = input.sort,
            kindFilter = input.kindFilter,
            mainList = mainList,
            recentList = recentList,
            emptyKind = computeEmptyKind(mainList, input.query, input.filter, input.kindFilter),
            passkeyCount = mainList.count { it is CredentialListItem.Passkey },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = CredentialListUiState.EMPTY,
    )

    private val _duplicateResult: MutableSharedFlow<DuplicateOutcome> =
        MutableSharedFlow(extraBufferCapacity = 1)
    val duplicateResult: SharedFlow<DuplicateOutcome> = _duplicateResult.asSharedFlow()

    // ---- inputs ---------------------------------------------------------

    fun onQueryChanged(value: String) {
        // Req 1.2: incremental. Always store; the downstream `combine`
        // re-evaluates without any extra submit / debounce.
        query.value = value
    }

    /**
     * Updates the selected filter chip. Pass [CredentialFilter.None] to
     * clear (Req 2.5 / 2.6).
     */
    fun onFilterChanged(value: CredentialFilter) {
        filter.value = value
    }

    fun onSortChanged(value: CredentialSortOrder) {
        sort.value = value
    }

    /**
     * Issue #101 Q-2: kept `internal` because v1 has no UI for it. Tests
     * reach this method directly to exercise PasswordOnly / PasskeyOnly
     * combinations; a future "kind filter chip" Issue will promote the
     * visibility to `public` and add an Activity-side bind.
     */
    internal fun onKindFilterChanged(value: KindFilter) {
        kindFilter.value = value
    }

    fun onDuplicate(id: CredentialId) {
        viewModelScope.launch {
            val outcome = duplicateUseCase(id).fold(
                onSuccess = { DuplicateOutcome.Success(it) },
                onFailure = { DuplicateOutcome.Failure(it.javaClass.simpleName) },
            )
            _duplicateResult.tryEmit(outcome)
        }
    }

    fun delete(id: CredentialId) {
        viewModelScope.launch { deleteUseCase(id) }
    }

    /**
     * Issue #101 R5.1 / R5.2: signal that the user tapped a PassKey
     * row. v1 does not navigate anywhere — the Activity shows a
     * Snackbar. This method exists so the future PassKey edit Activity
     * Issue can promote it to a SharedFlow without the Activity's
     * `setUpMainList` callback shape changing.
     *
     * SafeLogger emits only `rpIdLength=...` so the raw rpId /
     * credentialId / userName never reach logcat (NFR 2.3).
     */
    fun onPasskeyClicked(passkey: PasskeyDisplayModel) {
        SafeLogger.info(
            tag = TAG,
            message = "passkey row tapped (v1: snackbar only) rpIdLength=${passkey.rpId.length}",
        )
    }

    // ---- pure helpers (exposed at internal visibility for testing) -----

    /** Visible for tests; the live ViewModel always exposes [uiState]. */
    internal val queryState: StateFlow<String> = query.asStateFlow()
    internal val filterState: StateFlow<CredentialFilter> = filter.asStateFlow()
    internal val sortState: StateFlow<CredentialSortOrder> = sort.asStateFlow()
    internal val kindFilterState: StateFlow<KindFilter> = kindFilter.asStateFlow()

    sealed class DuplicateOutcome {
        data class Success(val newId: CredentialId) : DuplicateOutcome()
        /** [reason] is the failure class simple name only -- NFR 1.3. */
        data class Failure(val reason: String) : DuplicateOutcome()
    }

    class Factory(
        private val listUseCase: ListCredentialsUseCase,
        private val recentUseCase: ObserveRecentlyUsedUseCase,
        private val duplicateUseCase: DuplicateCredentialUseCase,
        private val deleteUseCase: DeleteCredentialUseCase,
        private val listPasskeysUseCase: ListPasskeysUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == CredentialListViewModel::class.java)
            return CredentialListViewModel(
                listUseCase,
                recentUseCase,
                duplicateUseCase,
                deleteUseCase,
                listPasskeysUseCase,
            ) as T
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val TAG = "KeyNest.ListVM"

        /**
         * Applies the signature-chip filter. Password rows are
         * partitioned by `signatureSha256` presence (Issue #9 Req
         * 2.3 / 2.4); PassKey rows pass through unchanged because they
         * have no `signatureSha256` concept (Issue #101 R2.7).
         */
        internal fun applyFilter(
            list: List<CredentialListItem>,
            filter: CredentialFilter,
        ): List<CredentialListItem> = when (filter) {
            CredentialFilter.None -> list
            CredentialFilter.SignatureMatched -> list.filter { item ->
                when (item) {
                    is CredentialListItem.Password -> item.credential.signatureSha256 != null
                    is CredentialListItem.Passkey -> true
                }
            }
            CredentialFilter.SignatureMissing -> list.filter { item ->
                when (item) {
                    is CredentialListItem.Password -> item.credential.signatureSha256 == null
                    is CredentialListItem.Passkey -> true
                }
            }
        }

        /**
         * Issue #101 Q-2 — kind filter. v1 always pins to
         * [KindFilter.All] so production paths never observe a
         * filtered list, but the logic is implemented now so the
         * future chip Issue only has to bind a UI control.
         */
        internal fun applyKindFilter(
            list: List<CredentialListItem>,
            kindFilter: KindFilter,
        ): List<CredentialListItem> = when (kindFilter) {
            KindFilter.All -> list
            KindFilter.PasswordOnly -> list.filterIsInstance<CredentialListItem.Password>()
            KindFilter.PasskeyOnly -> list.filterIsInstance<CredentialListItem.Passkey>()
        }

        /**
         * Applies the search query. case-insensitive substring contains
         * (R2.5). Variant-aware fields (R2.1 / R2.2):
         *  - Password: label / username / packageName (Issue #9 baseline)
         *  - Passkey:  rpId / rpDisplayName / userName / userDisplayName /
         *              displayName (KeyNest custom name, always null in v1)
         *
         * Blank queries fall through to a no-op return. PassKey rows
         * with all-null nullable fields are dropped on a non-empty
         * query because `(null ?: "").contains(needle)` is always false.
         */
        internal fun applySearch(
            list: List<CredentialListItem>,
            query: String,
        ): List<CredentialListItem> {
            val needle = query.trim().lowercase()
            if (needle.isEmpty()) return list
            return list.filter { item ->
                when (item) {
                    is CredentialListItem.Password -> {
                        val c = item.credential
                        c.label.lowercase().contains(needle) ||
                            c.username.lowercase().contains(needle) ||
                            c.packageName.lowercase().contains(needle)
                    }
                    is CredentialListItem.Passkey -> {
                        val p = item.passkey
                        p.rpId.lowercase().contains(needle) ||
                            (p.rpDisplayName ?: "").lowercase().contains(needle) ||
                            (p.userName ?: "").lowercase().contains(needle) ||
                            (p.userDisplayName ?: "").lowercase().contains(needle) ||
                            (p.displayName ?: "").lowercase().contains(needle)
                    }
                }
            }
        }

        /**
         * Decides which empty-state message to show. Req 1.4, 2.7; the
         * Issue #101 addition is the `kindFilter` parameter so a non-
         * default kind filter also flips the empty-state to NoMatch.
         */
        internal fun computeEmptyKind(
            mainList: List<CredentialListItem>,
            query: String,
            filter: CredentialFilter,
            kindFilter: KindFilter,
        ): EmptyKind? {
            if (mainList.isNotEmpty()) return null
            val noUserInput = query.isBlank() &&
                filter is CredentialFilter.None &&
                kindFilter == KindFilter.All
            return if (noUserInput) EmptyKind.Initial else EmptyKind.NoMatch
        }
    }
}
