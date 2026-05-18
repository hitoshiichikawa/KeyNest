package io.github.hitoshiichikawa.keynest.domain.model

import io.github.hitoshiichikawa.keynest.data.entity.DetectedFieldSource

/**
 * Domain-layer view of one observed editable field. Issue #67 Phase 2
 * (design.md §3.3).
 *
 * Mirrors [io.github.hitoshiichikawa.keynest.data.entity.DetectedFieldEntity]
 * but uses the strongly-typed [DetectedFieldSource] enum so callers in the
 * ViewModel / use case layer don't have to interpret raw `storageKey`
 * strings.
 *
 * The mapping between [DetectedField] and the entity is owned by
 * [io.github.hitoshiichikawa.keynest.data.repository.DetectedFieldRepositoryImpl];
 * the domain layer never sees the entity directly (same convention as
 * `Credential` vs `CredentialEntity`).
 */
data class DetectedField(
    val packageName: String,
    val fieldKey: String,
    val source: DetectedFieldSource,
    val lastDetectedAt: Long,
)
