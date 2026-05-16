package inc.goodanswers.keynest.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import inc.goodanswers.keynest.domain.model.Credential
import inc.goodanswers.keynest.domain.model.CredentialId
import inc.goodanswers.keynest.domain.model.CredentialSortOrder
import inc.goodanswers.keynest.domain.usecase.DeleteCredentialUseCase
import inc.goodanswers.keynest.domain.usecase.DuplicateCredentialUseCase
import inc.goodanswers.keynest.domain.usecase.ListCredentialsUseCase
import inc.goodanswers.keynest.domain.usecase.ObserveRecentlyUsedUseCase
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
 * 6.x.
 *
 * Three MVI-style input StateFlows ([query] / [filter] / [sort]) feed
 * into a single composed [uiState] StateFlow that the Activity collects.
 * The composition pipeline is:
 *
 *   1. Re-subscribe to `listUseCase(sort)` whenever the sort order
 *      changes (Req 4.3) using flatMapLatest. The DAO returns rows in
 *      the requested order; we apply filter / search in memory.
 *   2. Combine the sorted list with the current query + filter to
 *      produce the main list (Req 1.x / 2.x / 4.4 -- filter then search
 *      against the sorted list).
 *   3. Combine with `recentUseCase()` to expose the carousel feed; that
 *      flow is intentionally NOT routed through the query / filter
 *      transforms (Req 3.6).
 *
 * Side-channels:
 * - [duplicateResult] emits one event per duplicate attempt so the
 *   Activity can render a Snackbar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CredentialListViewModel(
    private val listUseCase: ListCredentialsUseCase,
    private val recentUseCase: ObserveRecentlyUsedUseCase,
    private val duplicateUseCase: DuplicateCredentialUseCase,
    private val deleteUseCase: DeleteCredentialUseCase,
) : ViewModel() {

    private val query: MutableStateFlow<String> = MutableStateFlow("")
    private val filter: MutableStateFlow<CredentialFilter> =
        MutableStateFlow(CredentialFilter.None)
    private val sort: MutableStateFlow<CredentialSortOrder> =
        MutableStateFlow(CredentialSortOrder.UpdatedAtDesc)

    private val sortedListFlow: Flow<List<Credential>> = sort.flatMapLatest { order ->
        listUseCase(order)
    }

    private val mainListFlow: Flow<List<Credential>> = combine(
        query, filter, sortedListFlow,
    ) { q, f, list ->
        // Req 4.4: filter first then search. Both are pure in-memory
        // transforms so the network surface stays empty (Req 1.5).
        val filtered = applyFilter(list, f)
        if (q.isBlank()) filtered else applySearch(filtered, q)
    }

    val uiState: StateFlow<CredentialListUiState> = combine(
        query, filter, sort, mainListFlow, recentUseCase(),
    ) { q, f, s, mainList, recentList ->
        CredentialListUiState(
            query = q,
            filter = f,
            sort = s,
            mainList = mainList,
            recentList = recentList,
            emptyKind = computeEmptyKind(mainList, q, f),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = CredentialListUiState.EMPTY,
    )

    /**
     * Convenience StateFlow that mirrors `uiState.mainList`. Kept for
     * source compatibility with the pre-#9 Activity wiring; new collectors
     * should prefer [uiState].
     */
    val credentials: StateFlow<List<Credential>> = mainListFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList(),
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

    // ---- pure helpers (exposed at internal visibility for testing) -----

    /** Visible for tests; the live ViewModel always exposes [uiState]. */
    internal val queryState: StateFlow<String> = query.asStateFlow()
    internal val filterState: StateFlow<CredentialFilter> = filter.asStateFlow()
    internal val sortState: StateFlow<CredentialSortOrder> = sort.asStateFlow()

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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == CredentialListViewModel::class.java)
            return CredentialListViewModel(
                listUseCase,
                recentUseCase,
                duplicateUseCase,
                deleteUseCase,
            ) as T
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /**
         * Applies the selected chip filter. Visible at top-level for
         * unit testing the predicate without instantiating the ViewModel.
         */
        internal fun applyFilter(
            list: List<Credential>,
            filter: CredentialFilter,
        ): List<Credential> = when (filter) {
            CredentialFilter.None -> list
            CredentialFilter.SignatureMatched -> list.filter { it.signatureSha256 != null }
            CredentialFilter.SignatureMissing -> list.filter { it.signatureSha256 == null }
        }

        /**
         * Applies the search query: label / username / packageName
         * case-insensitive contains. Req 1.1. The trimmed query length is
         * always >= 1 by the caller's contract (we skip search for blank).
         */
        internal fun applySearch(
            list: List<Credential>,
            query: String,
        ): List<Credential> {
            val needle = query.trim().lowercase()
            if (needle.isEmpty()) return list
            return list.filter { c ->
                c.label.lowercase().contains(needle) ||
                    c.username.lowercase().contains(needle) ||
                    c.packageName.lowercase().contains(needle)
            }
        }

        /**
         * Decides which empty-state message to show. Req 1.4, 2.7.
         */
        internal fun computeEmptyKind(
            mainList: List<Credential>,
            query: String,
            filter: CredentialFilter,
        ): EmptyKind? {
            if (mainList.isNotEmpty()) return null
            val noUserInput = query.isBlank() && filter is CredentialFilter.None
            return if (noUserInput) EmptyKind.Initial else EmptyKind.NoMatch
        }
    }
}
