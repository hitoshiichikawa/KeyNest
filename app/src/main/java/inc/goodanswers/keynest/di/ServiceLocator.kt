package inc.goodanswers.keynest.di

import android.content.Context
import androidx.biometric.BiometricManager
import inc.goodanswers.keynest.data.KeyNestDatabase
import inc.goodanswers.keynest.data.repository.CredentialRepositoryImpl
import inc.goodanswers.keynest.domain.repository.CredentialRepository
import inc.goodanswers.keynest.domain.usecase.ClearVaultUseCase
import inc.goodanswers.keynest.domain.usecase.DeleteCredentialUseCase
import inc.goodanswers.keynest.domain.usecase.DuplicateCredentialUseCase
import inc.goodanswers.keynest.domain.usecase.GetDeviceLockStatusUseCase
import inc.goodanswers.keynest.domain.usecase.GetVaultStorageUsageUseCase
import inc.goodanswers.keynest.domain.usecase.ListCredentialsUseCase
import inc.goodanswers.keynest.domain.usecase.MarkCredentialUsedUseCase
import inc.goodanswers.keynest.domain.usecase.ObserveRecentlyUsedUseCase
import inc.goodanswers.keynest.domain.usecase.ObserveVaultMetadataUseCase
import inc.goodanswers.keynest.domain.usecase.ResolveAutofillCandidatesUseCase
import inc.goodanswers.keynest.domain.usecase.SaveCredentialUseCase
import inc.goodanswers.keynest.domain.usecase.UnlockVaultUseCase
import inc.goodanswers.keynest.domain.usecase.UpdateCredentialUseCase
import inc.goodanswers.keynest.security.AesGcmCipher
import inc.goodanswers.keynest.security.KeystoreKeyProvider
import inc.goodanswers.keynest.util.AppInfoProvider
import inc.goodanswers.keynest.util.IconLoader
import inc.goodanswers.keynest.util.PackageSignatureResolver
import inc.goodanswers.keynest.util.VaultStorageMeasurer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    /**
     * Issue #46 hotfix: process-wide coroutine scope that backs every
     * async resolve in [IconLoader]. Held as a single value rather than
     * `lazy` because it has no transitive dependency on
     * [requireAppContext] and we want it constructed before [iconLoader]
     * so the lazy initializer can reference it without ordering surprises.
     *
     * Properties:
     *   - [SupervisorJob]: a single failed resolve does NOT cancel
     *     sibling resolves bound to other rows (Req 2.2 isolation).
     *   - [Dispatchers.Main.immediate]: result application happens on the
     *     main thread; `.immediate` lets a same-thread continuation run
     *     synchronously when possible, avoiding a needless reschedule on
     *     cache-hit-like fast paths.
     *   - Never cancelled: matches the JVM process lifetime. The
     *     [inc.goodanswers.keynest.KeyNestApp] singleton outlives every UI
     *     ViewHolder, so we never need a teardown hook here.
     */
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Issue #43: shared icon resolver for credential list / recent
     * carousel / package picker. Held as a process-wide singleton so the
     * LruCache is shared across all three adapters (NFR 1.1 cap=64).
     *
     * Issue #46 hotfix: now receives the process-wide [applicationScope]
     * so resolves no longer depend on `ImageView.findViewTreeLifecycleOwner`
     * (which returned `null` for not-yet-attached ViewHolders, silently
     * dropping the first bind and leaving the row on the kn_blue_500
     * background tile only).
     */
    val iconLoader: IconLoader by lazy {
        IconLoader(
            pm = requireAppContext().packageManager,
            resources = requireAppContext().resources,
            applicationScope = applicationScope,
        )
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
     * Must be called from [inc.goodanswers.keynest.KeyNestApp.onCreate] AND from
     * [inc.goodanswers.keynest.autofill.KeyNestAutofillService.onCreate] because
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
