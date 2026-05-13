package com.example.keynest.di

import android.content.Context
import androidx.biometric.BiometricManager
import com.example.keynest.data.KeyNestDatabase
import com.example.keynest.data.repository.CredentialRepositoryImpl
import com.example.keynest.domain.repository.CredentialRepository
import com.example.keynest.domain.usecase.ClearVaultUseCase
import com.example.keynest.domain.usecase.DeleteCredentialUseCase
import com.example.keynest.domain.usecase.DuplicateCredentialUseCase
import com.example.keynest.domain.usecase.GetDeviceLockStatusUseCase
import com.example.keynest.domain.usecase.GetVaultStorageUsageUseCase
import com.example.keynest.domain.usecase.ListCredentialsUseCase
import com.example.keynest.domain.usecase.MarkCredentialUsedUseCase
import com.example.keynest.domain.usecase.ObserveRecentlyUsedUseCase
import com.example.keynest.domain.usecase.ObserveVaultMetadataUseCase
import com.example.keynest.domain.usecase.ResolveAutofillCandidatesUseCase
import com.example.keynest.domain.usecase.SaveCredentialUseCase
import com.example.keynest.domain.usecase.UnlockVaultUseCase
import com.example.keynest.domain.usecase.UpdateCredentialUseCase
import com.example.keynest.security.AesGcmCipher
import com.example.keynest.security.KeystoreKeyProvider
import com.example.keynest.util.AppInfoProvider
import com.example.keynest.util.PackageSignatureResolver
import com.example.keynest.util.VaultStorageMeasurer

/**
 * Lightweight DI container. Holds the singleton graph of database,
 * repository, cipher, signature resolver and use cases shared by the UI
 * Activities and the AutofillService process.
 *
 * Hilt was deliberately not chosen for the MVP (design.md "ServiceLocator
 * (軽量 DI)") to keep the build configuration simple.
 *
 * Thread safety: [initialize] uses double-checked locking; all subsequent
 * accessors return the same singletons. The fields are lazy because the
 * AutofillService process may bind without ever needing the database (e.g.
 * empty FillResponse paths), so we avoid eagerly opening Room.
 */
object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    // ---- core singletons -------------------------------------------------

    private val database: KeyNestDatabase by lazy {
        KeyNestDatabase.create(requireAppContext())
    }

    val credentialRepository: CredentialRepository by lazy {
        CredentialRepositoryImpl(database.credentialDao())
    }

    val keystoreKeyProvider: KeystoreKeyProvider by lazy { KeystoreKeyProvider() }

    val aesGcmCipher: AesGcmCipher by lazy { AesGcmCipher(keystoreKeyProvider) }

    val packageSignatureResolver: PackageSignatureResolver by lazy {
        PackageSignatureResolver(requireAppContext().packageManager)
    }

    // ---- use cases -------------------------------------------------------

    val saveCredentialUseCase: SaveCredentialUseCase by lazy {
        SaveCredentialUseCase(credentialRepository, aesGcmCipher, packageSignatureResolver)
    }

    val updateCredentialUseCase: UpdateCredentialUseCase by lazy {
        UpdateCredentialUseCase(credentialRepository, aesGcmCipher, packageSignatureResolver)
    }

    val deleteCredentialUseCase: DeleteCredentialUseCase by lazy {
        DeleteCredentialUseCase(credentialRepository)
    }

    val listCredentialsUseCase: ListCredentialsUseCase by lazy {
        ListCredentialsUseCase(credentialRepository)
    }

    val resolveAutofillCandidatesUseCase: ResolveAutofillCandidatesUseCase by lazy {
        ResolveAutofillCandidatesUseCase(credentialRepository, packageSignatureResolver)
    }

    val unlockVaultUseCase: UnlockVaultUseCase by lazy {
        UnlockVaultUseCase(credentialRepository, aesGcmCipher)
    }

    // ---- Issue #9 use cases ---------------------------------------------

    val observeRecentlyUsedUseCase: ObserveRecentlyUsedUseCase by lazy {
        ObserveRecentlyUsedUseCase(credentialRepository)
    }

    val markCredentialUsedUseCase: MarkCredentialUsedUseCase by lazy {
        MarkCredentialUsedUseCase(credentialRepository)
    }

    val duplicateCredentialUseCase: DuplicateCredentialUseCase by lazy {
        DuplicateCredentialUseCase(credentialRepository)
    }

    // ---- Issue #10 use cases / utilities --------------------------------

    val vaultStorageMeasurer: VaultStorageMeasurer by lazy {
        VaultStorageMeasurer(requireAppContext())
    }

    val appInfoProvider: AppInfoProvider by lazy {
        AppInfoProvider(requireAppContext())
    }

    val observeVaultMetadataUseCase: ObserveVaultMetadataUseCase by lazy {
        ObserveVaultMetadataUseCase(credentialRepository)
    }

    val getVaultStorageUsageUseCase: GetVaultStorageUsageUseCase by lazy {
        GetVaultStorageUsageUseCase(vaultStorageMeasurer)
    }

    val getDeviceLockStatusUseCase: GetDeviceLockStatusUseCase by lazy {
        GetDeviceLockStatusUseCase(BiometricManager.from(requireAppContext()))
    }

    val clearVaultUseCase: ClearVaultUseCase by lazy {
        ClearVaultUseCase(credentialRepository, keystoreKeyProvider)
    }

    // ---- bootstrap -------------------------------------------------------

    /**
     * Registers the application context. Idempotent and thread-safe.
     * Must be called from [com.example.keynest.KeyNestApp.onCreate] AND from
     * [com.example.keynest.autofill.KeyNestAutofillService.onCreate] because
     * the AutofillService runs in the same process as the application, so
     * KeyNestApp.onCreate has already executed by the time the service binds.
     * This double-call is still safe (subsequent calls are no-ops).
     */
    @Synchronized
    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun requireAppContext(): Context = requireNotNull(appContext) {
        "ServiceLocator.initialize() must be called from KeyNestApp.onCreate() before access"
    }
}
