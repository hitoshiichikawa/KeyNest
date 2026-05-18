package io.github.hitoshiichikawa.keynest.autofill

import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.widget.inline.InlinePresentationSpec
import io.github.hitoshiichikawa.keynest.autofill.builder.DatasetPresentationFactory
import io.github.hitoshiichikawa.keynest.autofill.builder.FillResponseBuilder
import io.github.hitoshiichikawa.keynest.autofill.parser.AssistStructureParser
import io.github.hitoshiichikawa.keynest.di.ServiceLocator
import io.github.hitoshiichikawa.keynest.util.SafeLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * KeyNest's Autofill provider implementation.
 *
 * Requirements: 3.1, 3.2, 3.3, 3.5, 4.1, 4.2, 4.3, 4.4, 5.1, 7.2,
 * NFR 1.4, NFR 2.1, NFR 2.2, NFR 3.1, NFR 3.2
 *
 * Critical design points:
 * - onFillRequest does ZERO decryption. It returns either null (no
 *   candidates) or a FillResponse where every Dataset is gated behind an
 *   Authentication IntentSender pointing at AutofillUnlockActivity
 *   (Req 5.1, NFR 1.4).
 * - All exceptions are caught and surface as callback.onSuccess(null)
 *   (NFR 3.1 / NFR 3.2).
 * - The cancellation signal is honored - if Android cancels the request
 *   (typically because focus moved off the form), we abort early without
 *   invoking the callback.
 * - onSaveRequest is implemented as a no-op success. The MVP intentionally
 *   does NOT process onSaveRequest (design.md "確定事項"), so we cleanly
 *   tell the framework we handled it - returning failure would surface a
 *   user-visible error.
 */
class KeyNestAutofillService : AutofillService() {

    private lateinit var serviceJob: Job
    private lateinit var scope: CoroutineScope
    private lateinit var parser: AssistStructureParser
    private lateinit var responseBuilder: FillResponseBuilder

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(applicationContext)
        serviceJob = SupervisorJob()
        scope = CoroutineScope(Dispatchers.Default + serviceJob)
        parser = AssistStructureParser()
        responseBuilder = FillResponseBuilder(
            context = this,
            presentationFactory = DatasetPresentationFactory(this),
        )
        // Pre-warm Room so the first onFillRequest does not bear the
        // database-open latency. The query intentionally hits a key that
        // never matches, returning quickly while still forcing Room to
        // open the file.
        scope.launch {
            try {
                ServiceLocator.credentialRepository.findByPackage("__prewarm__")
            } catch (t: Throwable) {
                SafeLogger.warn(message = "DB prewarm failed", throwable = t)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        // Cancellation token shared with the coroutine.
        val handlerJob = Job(serviceJob)
        cancellationSignal.setOnCancelListener {
            handlerJob.cancel()
        }

        scope.launch(handlerJob) {
            val t0 = System.currentTimeMillis()
            try {
                // The last context is the latest snapshot for the current
                // focused window; older contexts are ignored to keep the
                // happy path fast (Req 3.5, NFR 2.1).
                val structure = request.fillContexts.lastOrNull()?.structure
                if (structure == null) {
                    if (handlerJob.isActive) callback.onSuccess(null)
                    return@launch
                }

                val parsed = parser.parse(structure)
                val callerPackage = extractCallerPackage(structure)

                // Issue #67 Phase 2 (design.md §7.3): fire-and-forget
                // detection. Launched BEFORE the `hasUsernameAndPassword`
                // short-circuit because the Phase 2 primary use case is
                // exactly the case where Phase 1's heuristics fail to
                // identify a username/password pair (custom-field-only
                // forms). Skipping detection in that branch would leave
                // those apps stuck in the "履歴なし" state forever.
                //
                // Concurrency contract:
                //   - Launched on the service `scope` (NOT bound to
                //     `handlerJob`) so the detection job is decoupled
                //     from the autofill callback / cancellation. The
                //     parent `scope` is `SupervisorJob`-backed so a
                //     detection failure does not bring down siblings.
                //   - Dispatched on `Dispatchers.IO` because the
                //     downstream Room write is blocking-style.
                //   - Registration check lives inside the use case
                //     (`findByPackage`); we deliberately do not
                //     re-check here so the responsibility stays in one
                //     place.
                //   - Any exception is swallowed inside the launched
                //     coroutine — Req 3.8.
                if (callerPackage != null) {
                    val descriptors = parsed.customFieldCandidates.map { it.descriptor }
                    if (descriptors.isNotEmpty()) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                ServiceLocator.recordDetectedFieldsUseCase(
                                    callerPackage,
                                    descriptors,
                                )
                            } catch (t: Throwable) {
                                SafeLogger.warn(
                                    message = "detected_fields upsert failed",
                                    throwable = t,
                                )
                            }
                        }
                    }
                }

                if (!parsed.hasUsernameAndPassword) {
                    // Req 3.2 / NFR 3.1 - we need BOTH fields to safely fill.
                    // (We are conservative here; a fill response with only
                    // username would still trigger the framework UI but the
                    // user experience is poor.)
                    SafeLogger.info(message = "onFillRequest: no username+password fields detected")
                    if (handlerJob.isActive) callback.onSuccess(null)
                    return@launch
                }

                if (callerPackage == null) {
                    SafeLogger.info(message = "onFillRequest: caller package unknown")
                    if (handlerJob.isActive) callback.onSuccess(null)
                    return@launch
                }

                val candidates = ServiceLocator.resolveAutofillCandidatesUseCase(callerPackage)
                val inlineSpecs = extractInlineSpecs(request)
                val response = responseBuilder.buildLockedResponse(
                    candidates = candidates,
                    usernameAutofillId = parsed.usernameId,
                    passwordAutofillId = parsed.passwordId,
                    customFieldCandidates = parsed.customFieldCandidates,
                    inlineSpecs = inlineSpecs,
                )
                val elapsed = System.currentTimeMillis() - t0
                // NFR 2.2 / Req 5.1: log counts only, never the underlying
                // descriptor strings (which could leak third-party app field
                // shapes or user-typed content).
                SafeLogger.info(
                    message = "onFillRequest pkg=$callerPackage candidates=${candidates.size} " +
                        "userId=${parsed.usernameId != null} passId=${parsed.passwordId != null} " +
                        "customFieldCandidates=${parsed.customFieldCandidates.size} " +
                        "inlineSpecs=${inlineSpecs.size} elapsedMs=$elapsed",
                )

                if (handlerJob.isActive) callback.onSuccess(response)
            } catch (t: Throwable) {
                // NFR 3.1 / NFR 3.2: never propagate, always succeed with
                // null. We deliberately do NOT use callback.onFailure(msg)
                // because that surfaces a user-visible error and the cause
                // string might leak diagnostic info.
                SafeLogger.warn(message = "onFillRequest swallowed exception", throwable = t)
                if (handlerJob.isActive) callback.onSuccess(null)
            }
        }
    }

    /**
     * MVP scope explicitly does NOT save credentials via the Autofill
     * framework (design.md "確定事項"). We acknowledge the request so the
     * framework does not surface "save failed" to the user.
     */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    /**
     * Returns the IME-supplied inline presentation specs when available.
     *
     * Inline suggestions render the autofill chip inside the IME's
     * suggestion strip (Gboard etc.), avoiding the popup-vs-keyboard
     * overlap that occurs when the user focuses a password field. The
     * IME is the gating party: it returns specs through
     * `FillRequest.inlineSuggestionsRequest` only when it can host them.
     */
    private fun extractInlineSpecs(request: FillRequest): List<InlinePresentationSpec> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return request.inlineSuggestionsRequest?.inlinePresentationSpecs.orEmpty()
    }

    /**
     * Determines the caller's packageName from the latest [android.app.assist.
     * AssistStructure]. We prefer activityComponent (set on Android 8+)
     * because it can't be spoofed by the calling app.
     */
    private fun extractCallerPackage(structure: android.app.assist.AssistStructure): String? {
        val component = structure.activityComponent
        if (component != null) return component.packageName
        // No safe fallback exists - we refuse to serve fill candidates if we
        // can't determine the package authoritatively.
        return null
    }
}
