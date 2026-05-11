package com.example.keynest.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.repository.CredentialRepository
import com.example.keynest.domain.usecase.NewCredentialInput
import com.example.keynest.domain.usecase.SaveCredentialUseCase
import com.example.keynest.domain.usecase.SaveFailure
import com.example.keynest.domain.usecase.UpdateCredentialInput
import com.example.keynest.domain.usecase.UpdateCredentialUseCase
import com.example.keynest.domain.usecase.UpdateFailure
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
                    _state.value = State.Saved
                    _navigation.tryEmit(Unit)
                }
                .onFailure { ex ->
                    _state.value = ex.toState()
                }
        }
    }

    suspend fun load(credentialId: Long): com.example.keynest.domain.model.EncryptedCredentialRecord? {
        return repository.findById(CredentialId(credentialId))
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == CredentialEditViewModel::class.java)
            return CredentialEditViewModel(repository, saveUseCase, updateUseCase) as T
        }
    }
}
