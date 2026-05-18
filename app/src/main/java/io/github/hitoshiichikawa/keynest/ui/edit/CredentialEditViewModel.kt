package io.github.hitoshiichikawa.keynest.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.hitoshiichikawa.keynest.autofill.parser.AutofillFieldHeuristics
import io.github.hitoshiichikawa.keynest.data.entity.DetectedFieldSource
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.CustomField
import io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord
import io.github.hitoshiichikawa.keynest.domain.repository.CredentialRepository
import io.github.hitoshiichikawa.keynest.domain.usecase.DeleteCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.NewCredentialInput
import io.github.hitoshiichikawa.keynest.domain.usecase.ObserveRecentDetectedFieldsUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.SaveCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.SaveFailure
import io.github.hitoshiichikawa.keynest.domain.usecase.UpdateCredentialInput
import io.github.hitoshiichikawa.keynest.domain.usecase.UpdateCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.UpdateFailure
import io.github.hitoshiichikawa.keynest.security.AesGcmCipher
import io.github.hitoshiichikawa.keynest.security.EncryptedBlob
import io.github.hitoshiichikawa.keynest.security.EncryptedCustomFieldsCodec
import io.github.hitoshiichikawa.keynest.util.AdvancedDetailsFormatter
import io.github.hitoshiichikawa.keynest.util.SafeLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs [CredentialEditActivity].
 *
 * Requirements: 1.1, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, plus Issue #14:
 * 1.1, 1.2, 1.5 (advanced section collapse state lifetime),
 * 2.1, 2.2, 2.3, 2.4 (createdAt / updatedAt exposure),
 * 3.1, 3.4 (full 64-char SHA-256 hex exposure, or null placeholder),
 * 4.1, 4.2 (signature captured timestamp / "not captured" branch),
 * 5.1-5.5 (credential ID toggle), 6.x (don't disturb existing edit flow).
 *
 * Distinguishes "new" vs "edit" mode via the [credentialId]:
 * - null  -> new -> SaveCredentialUseCase
 * - non-null -> edit -> UpdateCredentialUseCase (re-resolves signing hash
 *   per Req 2.3)
 *
 * Surfaces field-level [FieldError]s for the UI to render inline.
 *
 * Advanced details (Issue #14): the [advancedDetails] StateFlow exposes
 * read-only metadata of the currently loaded credential plus the two
 * UI toggles (expanded, idVisible). The toggles live on the ViewModel
 * (rather than savedInstanceState) so they survive configuration changes
 * but are scoped to a single Edit Activity lifetime per Req 1.5 -- a
 * fresh navigation to the Edit screen starts with collapsed + id hidden.
 */
class CredentialEditViewModel(
    private val repository: CredentialRepository,
    private val saveUseCase: SaveCredentialUseCase,
    private val updateUseCase: UpdateCredentialUseCase,
    private val deleteUseCase: DeleteCredentialUseCase,
    private val observeRecentDetectedFieldsUseCase: ObserveRecentDetectedFieldsUseCase,
    // Issue #73 Phase 1.5: decrypt customFields / password under the
    // existing unlock session so the Mode.Edit screen can show + edit
    // them. design.md §Architecture: codec / cipher are injected
    // directly into the ViewModel (no new use case layer).
    private val customFieldsCodec: EncryptedCustomFieldsCodec,
    private val aesGcmCipher: AesGcmCipher,
) : ViewModel() {

    sealed class State {
        object Idle : State()
        object Saving : State()
        object Saved : State()
        /**
         * Issue #30 Req 8.12: signals that the delete pipeline failed.
         * The UI surfaces this as a user-visible Snackbar and the screen
         * is intentionally NOT closed (the navigation event is only
         * emitted on success).
         */
        object DeleteFailed : State()
        data class FieldError(val field: Field, val kind: ErrorKind) : State()
        data class Error(val cause: String) : State()
    }

    enum class Field { PackageName, Username, Password, Label }
    enum class ErrorKind { Blank, Invalid }

    /** Whether the user is creating a new credential or editing an existing one. */
    enum class Mode { New, Edit }

    /**
     * Read-only metadata + UI toggle state for the "Advanced details" section.
     *
     * - In [Mode.New], the timestamp / hex / ID fields are all null and the
     *   UI renders "unsaved" placeholders (Req 2.3 / 5.5).
     * - In [Mode.Edit], the fields are filled from the loaded
     *   [EncryptedCredentialRecord]. A null [signatureSha256Hex] / null
     *   [signatureCapturedAt] means the credential was saved while the
     *   target app was not installed -- the UI must show "not captured"
     *   and disable the copy affordance (Req 3.4 / 4.2).
     * - [expanded] toggles via [toggleAdvancedExpanded]; starts collapsed
     *   per Req 1.1.
     * - [credentialIdVisible] toggles via [toggleCredentialIdVisible];
     *   starts hidden per Req 5.2. The numeric ID is only ever materialised
     *   into a String when this flag is true (NFR 1.3).
     */
    data class AdvancedDetails(
        val mode: Mode,
        val credentialId: Long?,
        val createdAt: Long?,
        val updatedAt: Long?,
        val signatureSha256Hex: String?,
        val signatureCapturedAt: Long?,
        val expanded: Boolean,
        val credentialIdVisible: Boolean,
    ) {
        companion object {
            /** Empty / new-mode default. */
            val NewMode: AdvancedDetails = AdvancedDetails(
                mode = Mode.New,
                credentialId = null,
                createdAt = null,
                updatedAt = null,
                signatureSha256Hex = null,
                signatureCapturedAt = null,
                expanded = false,
                credentialIdVisible = false,
            )
        }
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigation: SharedFlow<Unit> = _navigation.asSharedFlow()

    /**
     * Mode.Edit-only metadata derived from the loaded
     * [EncryptedCredentialRecord]. Phase 1.5 (Issue #73).
     *
     * - [initialPassword]: the password decrypted at [load] time. The
     *   Activity uses it twice: (a) to call setText() exactly once so
     *   the user sees the existing value masked, (b) the ViewModel
     *   uses it as the dirty-judge reference in [save] — if the
     *   submitted CharArray content-equals this string, the
     *   UpdateCredentialInput is built with newPassword = null so the
     *   existing ciphertext is preserved.
     *
     * In Mode.New the flow stays at [EditState.Empty] (initialPassword
     * == null) for the entire ViewModel lifetime. The State sealed
     * class above is kept intact (Idle / Saving / Saved / FieldError
     * / Error) so existing observers do not need to know about the
     * new EditState wiring.
     */
    data class EditState(
        val initialPassword: String?,
    ) {
        companion object {
            val Empty: EditState = EditState(initialPassword = null)
        }
    }

    private val _editState = MutableStateFlow(EditState.Empty)
    val editState: StateFlow<EditState> = _editState.asStateFlow()

    private val _advancedDetails = MutableStateFlow(AdvancedDetails.NewMode)
    val advancedDetails: StateFlow<AdvancedDetails> = _advancedDetails.asStateFlow()

    // ---- Issue #66 Phase 1: customFields editor ---------------------------

    /**
     * UI state for the dynamic customField rows. Issue #66 Phase 1.
     *
     * Reducer contract:
     * - [addCustomFieldRow] adds a fresh empty row, capped at
     *   [CustomFieldsState.MAX_CUSTOM_FIELDS] (Req 3.5).
     * - [removeCustomFieldRow] drops the row with the given rowId.
     * - [updateCustomFieldKey] / [updateCustomFieldValue] mutate the
     *   in-place text.
     * - [save] collects the rows, drops any with a blank fieldKey
     *   (Req 3.4 silent drop), and passes the resulting list to
     *   NewCredentialInput.customFields / UpdateCredentialInput.customFields.
     *
     * Edit-mode handling (design.md §9.3 暫定設計):
     * - In [Mode.New], `editable = true` and the UI shows the row editor.
     * - In [Mode.Edit], `editable = false` for Phase 1. The customField
     *   section is rendered read-only (rows == empty list, no add CTA)
     *   because we cannot read the existing customFields without an
     *   additional biometric unlock — outside Phase 1 scope (see
     *   impl-notes.md "確認事項"). Phase 2 will revisit.
     */
    data class CustomFieldsState(
        val rows: List<Row> = emptyList(),
        val editable: Boolean = true,
    ) {
        data class Row(
            /** UI-only identity. Assigned by the ViewModel; never persisted. */
            val rowId: Long,
            val fieldKey: String,
            val value: String,
        )

        val canAddMore: Boolean get() = editable && rows.size < MAX_CUSTOM_FIELDS

        companion object {
            const val MAX_CUSTOM_FIELDS = 10
        }
    }

    private val _customFields = MutableStateFlow(CustomFieldsState())
    val customFields: StateFlow<CustomFieldsState> = _customFields.asStateFlow()

    // ---- Issue #67 Phase 2: detected_fields suggestion chips ------------

    /**
     * UI state for the "recently detected fields" chip group rendered
     * under the customField editor. Issue #67 Phase 2 (design.md §8.1).
     *
     * - [visible]: whether to show the chip strip at all. Flipped on by
     *   [onAddCustomFieldClickedForSuggest] (after the user taps the
     *   add-field button) and back off by [onSuggestionClicked] /
     *   [hideSuggestions].
     * - [items]: the candidate chips, already filtered against the
     *   existing customField keys (Req 4.4) and dedup'd by normalized
     *   form.
     * - [emptyMessage]: when the Flow emitted an empty list — show the
     *   "履歴なし" placeholder instead of the chip group (Req 4.3).
     */
    data class SuggestionState(
        val visible: Boolean,
        val items: List<DetectedFieldSuggestion>,
        val emptyMessage: Boolean,
    ) {
        companion object {
            val Hidden: SuggestionState =
                SuggestionState(visible = false, items = emptyList(), emptyMessage = false)
        }
    }

    /**
     * One suggestion chip's surface data. The original
     * [DetectedField][io.github.hitoshiichikawa.keynest.domain.model.DetectedField]
     * `lastDetectedAt` is irrelevant to the UI so it is intentionally
     * dropped — the order in [SuggestionState.items] already reflects
     * recency.
     */
    data class DetectedFieldSuggestion(
        val fieldKey: String,
        val source: DetectedFieldSource,
    )

    private val _suggestion = MutableStateFlow(SuggestionState.Hidden)
    val suggestion: StateFlow<SuggestionState> = _suggestion.asStateFlow()

    /**
     * The packageName the suggestion query is bound to. Set in
     * [load] for edit mode (which Phase 2 does not actually surface
     * suggestions for) and via [bindPackageForSuggestions] in new mode.
     * Kept nullable so the ViewModel can no-op before the user picks a
     * package.
     */
    private var currentPackageName: String? = null

    /**
     * Job running the active `observeRecent` collection. Cancelled and
     * replaced whenever the bound package changes (or the suggestion
     * source is disabled, e.g. in edit mode).
     */
    private var suggestionJob: Job? = null

    /**
     * Monotonic rowId generator. Survives configuration changes because the
     * ViewModel does; reset on Activity destruction (matching the existing
     * AdvancedDetails toggle lifetime).
     */
    private var nextRowId: Long = 1L

    /**
     * Save (or update) the credential. Ownership of [password] transfers to
     * this method - the underlying use case zero-fills it.
     */
    fun save(
        existingId: Long?,
        packageName: String,
        username: String,
        password: CharArray,
        label: String,
    ) {
        // Issue #66 Phase 1: collect customFields from the ViewModel state,
        // dropping rows with a blank fieldKey (Req 3.4 silent drop). Empty
        // value strings are retained — the user might legitimately want to
        // store an empty value (though this is a soft anti-pattern).
        val effectiveCustomFields: List<CustomField> = _customFields.value.rows
            .filter { it.fieldKey.isNotBlank() }
            .map { CustomField(fieldKey = it.fieldKey, value = it.value) }

        viewModelScope.launch {
            _state.value = State.Saving
            val result = if (existingId != null) {
                updateUseCase(
                    UpdateCredentialInput(
                        id = CredentialId(existingId),
                        packageName = packageName.trim(),
                        username = username.trim(),
                        label = label.trim(),
                        newPassword = if (password.isEmpty()) null else password,
                        // design.md §9.3 暫定: in edit mode the editor is
                        // read-only so we pass null = "leave existing
                        // ciphertext untouched". In new mode this branch is
                        // unreachable.
                        customFields = if (_customFields.value.editable) effectiveCustomFields else null,
                    ),
                ).map { Unit }
            } else {
                saveUseCase(
                    NewCredentialInput(
                        packageName = packageName.trim(),
                        username = username.trim(),
                        password = password,
                        label = label.trim(),
                        customFields = effectiveCustomFields,
                    ),
                ).map { Unit }
            }

            result
                .onSuccess {
                    SafeLogger.info(
                        tag = TAG,
                        message = "credential save ok (mode=${if (existingId != null) "update" else "new"})",
                    )
                    _state.value = State.Saved
                    _navigation.tryEmit(Unit)
                }
                .onFailure { ex ->
                    SafeLogger.error(tag = TAG, message = "credential save failed", throwable = ex)
                    _state.value = ex.toState()
                }
        }
    }

    /**
     * Issue #30 Req 8.10 / 8.12: delete the currently-edited credential.
     *
     * On success the navigation event is emitted (Activity closes via
     * finish()); on failure [State.DeleteFailed] is published so the
     * Activity can surface a Snackbar without closing.
     *
     * The use case is idempotent at the repository layer — deleting a
     * non-existent id is treated as success — so the only failure path
     * is a true storage exception.
     */
    fun delete(credentialId: Long) {
        viewModelScope.launch {
            deleteUseCase(CredentialId(credentialId))
                .onSuccess {
                    SafeLogger.info(tag = TAG, message = "credential delete ok")
                    _navigation.tryEmit(Unit)
                }
                .onFailure { ex ->
                    SafeLogger.error(tag = TAG, message = "credential delete failed", throwable = ex)
                    _state.value = State.DeleteFailed
                }
        }
    }

    suspend fun load(credentialId: Long): EncryptedCredentialRecord? {
        val record = repository.findById(CredentialId(credentialId))
        // Issue #14: refresh the advanced-details snapshot every time a
        // credential is loaded so the UI can populate the read-only rows.
        // Preserve existing toggle state (expanded / idVisible) so an
        // accidental reload does not collapse a section the user already
        // expanded (Req 1.5).
        _advancedDetails.update { current ->
            if (record == null) {
                current.copy(mode = Mode.New, credentialId = null, createdAt = null, updatedAt = null,
                    signatureSha256Hex = null, signatureCapturedAt = null)
            } else {
                current.copy(
                    mode = Mode.Edit,
                    credentialId = record.id.value,
                    createdAt = record.createdAt,
                    updatedAt = record.updatedAt,
                    signatureSha256Hex = AdvancedDetailsFormatter.formatSha256Hex(record.signatureSha256),
                    signatureCapturedAt = record.signatureCapturedAt,
                )
            }
        }
        if (record != null) {
            // Edit mode: design.md §9.3 暫定. The customField section is
            // read-only because we cannot decrypt the existing entries
            // without an extra biometric unlock (deferred to Phase 2).
            _customFields.update { CustomFieldsState(rows = emptyList(), editable = false) }
            // Issue #67 Phase 2 (§8.5): bind the package so a future
            // Phase 1.5 that flips edit-mode editable=true can reuse
            // the suggestion path without further work. Phase 2 will
            // still hide the chip strip because editable=false.
            bindPackageForSuggestions(record.packageName)
        }
        return record
    }

    // ---- Issue #66 Phase 1: customFields reducer methods --------------------

    /**
     * Append a fresh empty row. No-op past [CustomFieldsState.MAX_CUSTOM_FIELDS]
     * (Req 3.5) or when the section is read-only (edit mode, design.md §9.3).
     */
    fun addCustomFieldRow() {
        _customFields.update { current ->
            if (!current.canAddMore) current
            else current.copy(
                rows = current.rows + CustomFieldsState.Row(
                    rowId = nextRowId++,
                    fieldKey = "",
                    value = "",
                ),
            )
        }
    }

    /** Remove the row identified by [rowId]. No-op if not found. */
    fun removeCustomFieldRow(rowId: Long) {
        _customFields.update { current ->
            if (!current.editable) current
            else current.copy(rows = current.rows.filterNot { it.rowId == rowId })
        }
    }

    /** Update the fieldKey on the row identified by [rowId]. */
    fun updateCustomFieldKey(rowId: Long, fieldKey: String) {
        _customFields.update { current ->
            if (!current.editable) current
            else current.copy(rows = current.rows.map {
                if (it.rowId == rowId) it.copy(fieldKey = fieldKey) else it
            })
        }
    }

    /** Update the value on the row identified by [rowId]. */
    fun updateCustomFieldValue(rowId: Long, value: String) {
        _customFields.update { current ->
            if (!current.editable) current
            else current.copy(rows = current.rows.map {
                if (it.rowId == rowId) it.copy(value = value) else it
            })
        }
    }

    // ---- Issue #67 Phase 2: suggestion handlers --------------------------

    /**
     * Bind the credential edit screen to [packageName] so subsequent
     * suggestion refreshes (triggered by [onAddCustomFieldClickedForSuggest])
     * query the right rows.
     *
     * Called from the Activity:
     *   - In new mode: as soon as the user picks a package via the
     *     PackagePicker / types one manually. The Activity wires this
     *     through `inputPackage`'s TextWatcher equivalent.
     *   - In edit mode: from [load] (the package is known up-front).
     *     Suggestions are nevertheless suppressed when
     *     [CustomFieldsState.editable] is false (§8.5).
     *
     * Re-binding with the same package value is a no-op so the chip
     * strip stays steady across configuration changes.
     */
    fun bindPackageForSuggestions(packageName: String?) {
        val normalized = packageName?.trim()?.takeIf { it.isNotEmpty() }
        if (currentPackageName == normalized) return
        currentPackageName = normalized
        // Cancel any in-flight subscription bound to the previous
        // packageName; the chip strip falls back to Hidden until the
        // next add-click triggers a refresh.
        suggestionJob?.cancel()
        suggestionJob = null
        _suggestion.value = SuggestionState.Hidden
    }

    /**
     * Called by the Activity after [addCustomFieldRow] (or in lieu of
     * it when the row cap is already reached). Refreshes the
     * suggestion list and flips visibility to true if there is a bound
     * package and the customField section is editable.
     */
    fun onAddCustomFieldClickedForSuggest() {
        refreshSuggestions(showWhenLoaded = true)
    }

    /**
     * Force the chip strip closed (e.g. the user typed a fieldKey
     * manually after add-click, so the suggestions are no longer
     * relevant). Cancels the underlying subscription so the Flow does
     * not keep emitting in the background.
     */
    fun hideSuggestions() {
        suggestionJob?.cancel()
        suggestionJob = null
        _suggestion.value = SuggestionState.Hidden
    }

    /**
     * Apply [item]'s fieldKey to the most-recently-added customField
     * row (= `rows.last()`) and hide the chip strip.
     *
     * Targeting "the last row" mirrors design.md §8.1 — the suggestion
     * UX is gated on the user having just tapped "Add field", so the
     * row at the tail is the new one. If the user has removed every
     * row between add-click and suggestion-click, the call is a no-op.
     */
    fun onSuggestionClicked(item: DetectedFieldSuggestion) {
        val current = _customFields.value
        val targetRowId = current.rows.lastOrNull()?.rowId ?: run {
            // Defensive: chip strip should already be hidden in this
            // state, but a race could let one click through. Just
            // close the strip and stop.
            hideSuggestions()
            return
        }
        // Phase 1 reducer is reused as-is so the customField path
        // remains unchanged (design.md §8.1).
        updateCustomFieldKey(targetRowId, item.fieldKey)
        // After transferring, drop the chip strip — the user can
        // re-trigger by tapping "Add field" again. This also prevents
        // accidental double-application of the same chip.
        hideSuggestions()
    }

    /**
     * Re-subscribe to [observeRecentDetectedFieldsUseCase] for the
     * currently-bound package. Filters out fieldKeys that already
     * occupy a customField row (Req 4.4) and dedupes by normalised
     * form (Req 4.5).
     *
     * @param showWhenLoaded if true, the resulting [SuggestionState]
     *   has `visible = true` (a chip strip is appropriate). If false,
     *   visibility stays false — used by background refreshes that
     *   should not pop the UI back open.
     */
    private fun refreshSuggestions(showWhenLoaded: Boolean) {
        val pkg = currentPackageName ?: run {
            _suggestion.value = SuggestionState.Hidden
            return
        }
        val state = _customFields.value
        if (!state.editable) {
            // §8.5: edit mode disables the customField editor entirely;
            // hide the chip strip too so we never transfer to a row
            // the user cannot see.
            _suggestion.value = SuggestionState.Hidden
            return
        }
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            observeRecentDetectedFieldsUseCase(pkg, limit = SUGGESTION_LIMIT).collect { list ->
                val existing = _customFields.value.rows
                    .map { AutofillFieldHeuristics.normalizeKey(it.fieldKey) }
                    .filter { it.isNotEmpty() }
                    .toSet()
                val filtered = mutableListOf<DetectedFieldSuggestion>()
                val seenNormalized = mutableSetOf<String>()
                for (entry in list) {
                    val normalized = AutofillFieldHeuristics.normalizeKey(entry.fieldKey)
                    if (normalized.isEmpty()) continue
                    if (normalized in existing) continue
                    if (!seenNormalized.add(normalized)) continue
                    filtered += DetectedFieldSuggestion(
                        fieldKey = entry.fieldKey,
                        source = entry.source,
                    )
                }
                _suggestion.value = SuggestionState(
                    visible = showWhenLoaded,
                    items = filtered,
                    emptyMessage = list.isEmpty(),
                )
            }
        }
    }

    /**
     * Toggles the "Advanced details" section between collapsed and expanded
     * (Req 1.2). Idempotent flip.
     */
    fun toggleAdvancedExpanded() {
        _advancedDetails.update { it.copy(expanded = !it.expanded) }
    }

    /**
     * Toggles whether the credential ID numeric value is visible (Req 5.3 /
     * 5.4). Initial state is hidden (Req 5.2).
     */
    fun toggleCredentialIdVisible() {
        _advancedDetails.update { it.copy(credentialIdVisible = !it.credentialIdVisible) }
    }

    private fun Throwable.toState(): State = when (this) {
        is SaveFailure.PackageNameBlank -> State.FieldError(Field.PackageName, ErrorKind.Blank)
        is SaveFailure.PackageNameInvalid -> State.FieldError(Field.PackageName, ErrorKind.Invalid)
        is SaveFailure.UsernameBlank -> State.FieldError(Field.Username, ErrorKind.Blank)
        is SaveFailure.PasswordBlank -> State.FieldError(Field.Password, ErrorKind.Blank)
        is SaveFailure.LabelBlank -> State.FieldError(Field.Label, ErrorKind.Blank)
        is SaveFailure.Storage -> State.Error(cause = "save")
        is UpdateFailure.PackageNameBlank -> State.FieldError(Field.PackageName, ErrorKind.Blank)
        is UpdateFailure.PackageNameInvalid -> State.FieldError(Field.PackageName, ErrorKind.Invalid)
        is UpdateFailure.UsernameBlank -> State.FieldError(Field.Username, ErrorKind.Blank)
        is UpdateFailure.LabelBlank -> State.FieldError(Field.Label, ErrorKind.Blank)
        is UpdateFailure.NotFound -> State.Error(cause = "not_found")
        is UpdateFailure.Storage -> State.Error(cause = "update")
        else -> State.Error(cause = this.javaClass.simpleName)
    }

    class Factory(
        private val repository: CredentialRepository,
        private val saveUseCase: SaveCredentialUseCase,
        private val updateUseCase: UpdateCredentialUseCase,
        private val deleteUseCase: DeleteCredentialUseCase,
        private val observeRecentDetectedFieldsUseCase: ObserveRecentDetectedFieldsUseCase,
        // Issue #73 Phase 1.5: same singletons as the use cases (see
        // ServiceLocator); the ViewModel decrypts under the existing
        // unlock session, no extra biometric prompt.
        private val customFieldsCodec: EncryptedCustomFieldsCodec,
        private val aesGcmCipher: AesGcmCipher,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == CredentialEditViewModel::class.java)
            return CredentialEditViewModel(
                repository = repository,
                saveUseCase = saveUseCase,
                updateUseCase = updateUseCase,
                deleteUseCase = deleteUseCase,
                observeRecentDetectedFieldsUseCase = observeRecentDetectedFieldsUseCase,
                customFieldsCodec = customFieldsCodec,
                aesGcmCipher = aesGcmCipher,
            ) as T
        }
    }

    private companion object {
        const val TAG = "KeyNest.Edit"
        /** Number of suggestion chips to show (requirements Req 4.1). */
        const val SUGGESTION_LIMIT = 10
    }
}
