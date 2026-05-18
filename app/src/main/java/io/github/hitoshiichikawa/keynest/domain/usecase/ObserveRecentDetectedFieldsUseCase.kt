package io.github.hitoshiichikawa.keynest.domain.usecase

import io.github.hitoshiichikawa.keynest.domain.model.DetectedField
import io.github.hitoshiichikawa.keynest.domain.repository.DetectedFieldRepository
import kotlinx.coroutines.flow.Flow

/**
 * Emits the most recent [limit] detected fields for [packageName], in
 * `lastDetectedAt DESC` order. Issue #67 Phase 2 (design.md §7.4).
 *
 * Backed by [DetectedFieldRepository.observeRecentByPackage]; this class
 * exists so the ViewModel can depend on a focused use case rather than
 * the entire repository surface. Default [limit] is `10`, matching
 * requirements Req 4.1 and the Phase 1 customField cap.
 */
class ObserveRecentDetectedFieldsUseCase(
    private val detectedFieldRepository: DetectedFieldRepository,
) {
    operator fun invoke(packageName: String, limit: Int = 10): Flow<List<DetectedField>> =
        detectedFieldRepository.observeRecentByPackage(packageName, limit)
}
