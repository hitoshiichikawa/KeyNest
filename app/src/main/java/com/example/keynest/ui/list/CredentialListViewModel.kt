package com.example.keynest.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.keynest.domain.model.Credential
import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.usecase.DeleteCredentialUseCase
import com.example.keynest.domain.usecase.ListCredentialsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs [CredentialListActivity]. Observes the credential list and forwards
 * delete requests to the use case. Req 1.5.
 */
class CredentialListViewModel(
    private val listUseCase: ListCredentialsUseCase,
    private val deleteUseCase: DeleteCredentialUseCase,
) : ViewModel() {

    val credentials: StateFlow<List<Credential>> = listUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    fun delete(id: CredentialId) {
        viewModelScope.launch { deleteUseCase(id) }
    }

    class Factory(
        private val listUseCase: ListCredentialsUseCase,
        private val deleteUseCase: DeleteCredentialUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == CredentialListViewModel::class.java)
            return CredentialListViewModel(listUseCase, deleteUseCase) as T
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
