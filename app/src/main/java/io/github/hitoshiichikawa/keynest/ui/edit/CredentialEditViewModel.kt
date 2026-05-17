package io.github.hitoshiichikawa.keynest.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord
import io.github.hitoshiichikawa.keynest.domain.repository.CredentialRepository
import io.github.hitoshiichikawa.keynest.domain.usecase.DeleteCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.NewCredentialInput
import io.github.hitoshiichikawa.keynest.domain.usecase.SaveCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.SaveFailure
import io.github.hitoshiichikawa.keynest.domain.usecase.UpdateCredentialInput
import io.github.hitoshiichikawa.keynest.domain.usecase.UpdateCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.UpdateFailure
import io.github.hitoshiichikawa.keynest.util.AdvancedDetailsFormatter
import io.github.hitoshiichikawa.keynest.util.SafeLogger
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

    private val _advancedDetails = MutableStateFlow(AdvancedDetails.NewMode)
    val advancedDetails: StateFlow<AdvancedDetails> = _advancedDetails.asStateFlow()

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
                    ),
                ).map { Unit }
            } else {
                saveUseCase(
                    NewCredentialInput(
                        packageName = packageName.trim(),
                        username = username.trim(),
                        password = password,
                        label = label.trim(),
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
        return record
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == CredentialEditViewModel::class.java)
            return CredentialEditViewModel(repository, saveUseCase, updateUseCase, deleteUseCase) as T
        }
    }

    private companion object {
        const val TAG = "KeyNest.Edit"
    }
}
