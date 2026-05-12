package com.example.keynest.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.repository.CredentialRepository
import com.example.keynest.domain.usecase.DeleteCredentialUseCase
import com.example.keynest.domain.usecase.NewCredentialInput
import com.example.keynest.domain.usecase.SaveCredentialUseCase
import com.example.keynest.domain.usecase.SaveFailure
import com.example.keynest.domain.usecase.UpdateCredentialInput
import com.example.keynest.domain.usecase.UpdateCredentialUseCase
import com.example.keynest.domain.usecase.UpdateFailure
import com.example.keynest.util.SafeLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs [CredentialEditActivity].
 *
 * Requirements: 1.1, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3
 *
 * Distinguishes "new" vs "edit" mode via the [credentialId]:
 * - null  -> new -> SaveCredentialUseCase
 * - non-null -> edit -> UpdateCredentialUseCase (re-resolves signing hash
 *   per Req 2.3)
 *
 * Surfaces field-level [FieldError]s for the UI to render inline.
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
        data class FieldError(val field: Field, val kind: ErrorKind) : State()
        data class Error(val cause: String) : State()
    }

    enum class Field { PackageName, Username, Password, Label }
    enum class ErrorKind { Blank, Invalid }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigation: SharedFlow<Unit> = _navigation.asSharedFlow()

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

    suspend fun load(credentialId: Long): com.example.keynest.domain.model.EncryptedCredentialRecord? {
        return repository.findById(CredentialId(credentialId))
    }

    /**
     * Issue #5 Round 2 / AC 4.4.6 — delete the credential currently being
     * edited. Pure delegation to the existing [DeleteCredentialUseCase]
     * (the same path the list screen's long-press flow already uses);
     * the navigation flow drives the activity to finish on success.
     *
     * Repository / DAO / domain layer are not modified — this is the
     * smallest UI-binding wrapper required to surface the existing
     * delete behaviour from the edit screen.
     */
    fun delete(credentialId: Long) {
        viewModelScope.launch {
            val result = deleteUseCase(CredentialId(credentialId))
            result
                .onSuccess {
                    SafeLogger.info(tag = TAG, message = "credential delete ok")
                    _navigation.tryEmit(Unit)
                }
                .onFailure { ex ->
                    SafeLogger.error(tag = TAG, message = "credential delete failed", throwable = ex)
                    _state.value = State.Error(cause = "delete")
                }
        }
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
