package io.github.hitoshiichikawa.keynest.di

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import io.github.hitoshiichikawa.keynest.credentialprovider.authentication.GetEntryBuilder
import io.github.hitoshiichikawa.keynest.credentialprovider.registration.CreateEntryBuilder
import io.github.hitoshiichikawa.keynest.credentialprovider.registration.ExcludeCredentialDetector
import io.github.hitoshiichikawa.keynest.credentialprovider.registration.PasskeyCreator
import io.github.hitoshiichikawa.keynest.data.KeyNestDatabase
import io.github.hitoshiichikawa.keynest.data.repository.CredentialRepositoryImpl
import io.github.hitoshiichikawa.keynest.data.repository.DetectedFieldRepositoryImpl
import io.github.hitoshiichikawa.keynest.data.repository.PasskeyRepositoryImpl
import io.github.hitoshiichikawa.keynest.domain.repository.CredentialRepository
import io.github.hitoshiichikawa.keynest.domain.repository.DetectedFieldRepository
import io.github.hitoshiichikawa.keynest.domain.repository.PasskeyRepository
import io.github.hitoshiichikawa.keynest.domain.usecase.ClearVaultUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.DeleteCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.DuplicateCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.GetDeviceLockStatusUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.GetVaultStorageUsageUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.ListCredentialsUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.ListPasskeysUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.MarkCredentialUsedUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.ObserveRecentDetectedFieldsUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.ObserveRecentlyUsedUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.ObserveVaultMetadataUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.RecordDetectedFieldsUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.ResolveAutofillCandidatesUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.SaveCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.UnlockVaultUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.UpdateCredentialUseCase
import io.github.hitoshiichikawa.keynest.security.AesGcmCipher
import io.github.hitoshiichikawa.keynest.security.EncryptedCustomFieldsCodec
import io.github.hitoshiichikawa.keynest.security.KeystoreKeyProvider
import io.github.hitoshiichikawa.keynest.ui.settings.passkey.CredentialProviderStatusChecker
import io.github.hitoshiichikawa.keynest.ui.settings.passkey.DefaultCredentialProviderStatusChecker
import io.github.hitoshiichikawa.keynest.util.AppInfoProvider
import io.github.hitoshiichikawa.keynest.util.IconLoader
import io.github.hitoshiichikawa.keynest.util.PackageSignatureResolver
import io.github.hitoshiichikawa.keynest.util.VaultStorageMeasurer
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

    /**
     * Issue #67 Phase 2: backing store for the "recently detected fields"
     * suggestion UI. Held as a singleton so the AutofillService (which
     * writes) and the credential edit screen (which reads via Flow) see
     * the same Room instance.
     */
    val detectedFieldRepository: DetectedFieldRepository by lazy {
        DetectedFieldRepositoryImpl(database.detectedFieldDao())
    }

    /**
     * Issue #99 (parent #89) — registration ceremony backing store.
     * Inlined ahead of the umbrella `PasskeyRepository` Issue (#107) so the
     * PassKey provider Service / `PasskeyCreateActivity` have a real
     * persistence boundary. Shape mirrors #107 design.md §6.x for a
     * conflict-free merge when #107 lands.
     */
    val passkeyRepository: PasskeyRepository by lazy {
        PasskeyRepositoryImpl(database.passkeyDao(), database)
    }

    // ---- Issue #99 PassKey registration ceremony singletons -----------
    //
    // These are only resolved from API 34+ code paths
    // (KeyNestCredentialProviderService / PasskeyCreateActivity, both
    // `@RequiresApi(34)`), so the lazy fields themselves are gated.

    /**
     * Issue #99: core of the WebAuthn registration ceremony — generates the
     * ES256 keypair, COSE_Key, attestationObject and the
     * `SavePasskeyRequest` carrying the plaintext private key for the
     * caller to wipe after `PasskeyRepository.save(...)`.
     */
    // SuppressLint: the lazy initializer body is only ever executed via the
    // @get:RequiresApi(34) accessor, but lint does not propagate the
    // RequiresApi annotation through the lazy lambda. Suppressing here is
    // safe because the only call sites — KeyNestCredentialProviderService
    // and PasskeyCreateActivity — are themselves @RequiresApi(34).
    @get:RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @delegate:SuppressLint("NewApi")
    internal val passkeyCreator: PasskeyCreator by lazy { PasskeyCreator() }

    /**
     * Issue #99: builds the `CreateEntry` shown in the OS sheet and the
     * pending intent that launches `PasskeyCreateActivity`.
     */
    @get:RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @delegate:SuppressLint("NewApi")
    internal val createEntryBuilder: CreateEntryBuilder by lazy {
        CreateEntryBuilder(requireAppContext())
    }

    /**
     * Issue #99: synchronous helper around
     * `PasskeyRepository.findByCredentialId(...)` used by
     * `KeyNestCredentialProviderService.onBeginCreateCredentialRequest`
     * to short-circuit excludeCredentials before the OS sheet renders.
     */
    internal val excludeCredentialDetector: ExcludeCredentialDetector by lazy {
        ExcludeCredentialDetector(passkeyRepository)
    }

    /**
     * Issue #100: builds the [androidx.credentials.provider.PublicKeyCredentialEntry]
     * list shown in the OS sheet from a single
     * `BeginGetPublicKeyCredentialOption`. Resolves only from API 34+ code
     * paths (KeyNestCredentialProviderService.onBeginGetCredentialRequest
     * is `@RequiresApi(34)`).
     */
    @get:RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @delegate:SuppressLint("NewApi")
    internal val getEntryBuilder: GetEntryBuilder by lazy {
        GetEntryBuilder(requireAppContext(), passkeyRepository)
    }

    val keystoreKeyProvider: KeystoreKeyProvider by lazy { KeystoreKeyProvider() }

    val aesGcmCipher: AesGcmCipher by lazy { AesGcmCipher(keystoreKeyProvider) }

    /**
     * Issue #66 Phase 1: shared codec that round-trips `List<CustomField>`
     * through JSON and AES-GCM (reusing [aesGcmCipher] / the same Keystore
     * key as username/password — Req 1.4). Held as a singleton so the use
     * cases that need it (SaveCredentialUseCase / UpdateCredentialUseCase /
     * UnlockVaultUseCase) all share one instance.
     */
    val encryptedCustomFieldsCodec: EncryptedCustomFieldsCodec by lazy {
        EncryptedCustomFieldsCodec(aesGcmCipher)
    }

    val packageSignatureResolver: PackageSignatureResolver by lazy {
        PackageSignatureResolver(requireAppContext().packageManager)
    }

    // ---- use cases -------------------------------------------------------

    val saveCredentialUseCase: SaveCredentialUseCase by lazy {
        SaveCredentialUseCase(
            repo = credentialRepository,
            cipher = aesGcmCipher,
            sigResolver = packageSignatureResolver,
            customFieldsCodec = encryptedCustomFieldsCodec,
        )
    }

    val updateCredentialUseCase: UpdateCredentialUseCase by lazy {
        UpdateCredentialUseCase(
            repo = credentialRepository,
            cipher = aesGcmCipher,
            sigResolver = packageSignatureResolver,
            customFieldsCodec = encryptedCustomFieldsCodec,
        )
    }

    val deleteCredentialUseCase: DeleteCredentialUseCase by lazy {
        DeleteCredentialUseCase(credentialRepository)
    }

    val listCredentialsUseCase: ListCredentialsUseCase by lazy {
        ListCredentialsUseCase(credentialRepository)
    }

    /**
     * Issue #101 (Phase 4 of umbrella #89): Flow source for the merged
     * password+PassKey credential list. Delegates `PasskeyRepository.listAll()`
     * and maps PasskeyEntity to the UI-safe `PasskeyDisplayModel` so the
     * sensitive entity columns never reach the ViewModel layer (NFR 2.2).
     */
    val listPasskeysUseCase: ListPasskeysUseCase by lazy {
        ListPasskeysUseCase(passkeyRepository)
    }

    val resolveAutofillCandidatesUseCase: ResolveAutofillCandidatesUseCase by lazy {
        ResolveAutofillCandidatesUseCase(credentialRepository, packageSignatureResolver)
    }

    /**
     * Issue #67 Phase 2: fire-and-forget detection writer invoked by
     * [io.github.hitoshiichikawa.keynest.autofill.KeyNestAutofillService.onFillRequest].
     */
    val recordDetectedFieldsUseCase: RecordDetectedFieldsUseCase by lazy {
        RecordDetectedFieldsUseCase(detectedFieldRepository, credentialRepository)
    }

    /**
     * Issue #67 Phase 2: Flow source for the credential edit screen
     * suggestion chip group.
     */
    val observeRecentDetectedFieldsUseCase: ObserveRecentDetectedFieldsUseCase by lazy {
        ObserveRecentDetectedFieldsUseCase(detectedFieldRepository)
    }

    val unlockVaultUseCase: UnlockVaultUseCase by lazy {
        UnlockVaultUseCase(
            repo = credentialRepository,
            cipher = aesGcmCipher,
            customFieldsCodec = encryptedCustomFieldsCodec,
        )
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
     *     [io.github.hitoshiichikawa.keynest.KeyNestApp] singleton outlives every UI
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
        ClearVaultUseCase(credentialRepository, keystoreKeyProvider, detectedFieldRepository)
    }

    // ---- Issue #103 (Phase 6) PassKey provider settings ----------------

    /**
     * Issue #103: probes the OS Credential Manager registration state so
     * the Settings screen can render Enabled / Disabled / Unsupported.
     *
     * Not gated by `@RequiresApi` — the checker itself runtime-checks
     * `Build.VERSION.SDK_INT` and returns `Unsupported` on API 33-.
     * This lets the lazy field resolve safely on API 26+ devices.
     */
    val credentialProviderStatusChecker: CredentialProviderStatusChecker by lazy {
        DefaultCredentialProviderStatusChecker(requireAppContext())
    }

    // ---- bootstrap -------------------------------------------------------

    /**
     * Registers the application context. Idempotent and thread-safe.
     * Must be called from [io.github.hitoshiichikawa.keynest.KeyNestApp.onCreate] AND from
     * [io.github.hitoshiichikawa.keynest.autofill.KeyNestAutofillService.onCreate] because
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
