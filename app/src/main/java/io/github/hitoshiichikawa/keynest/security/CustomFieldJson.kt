package io.github.hitoshiichikawa.keynest.security

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format DTO used by [EncryptedCustomFieldsCodec].
 *
 * Issue #66 Phase 1. Single 1 ciphertext + 1 IV per credential carries the
 * JSON-encoded list of these objects (design.md §3.4 "案 A").
 *
 * Keys are shortened to `k` / `v` to keep the JSON payload (and therefore
 * the AES-GCM ciphertext) compact. The mapping back to the domain
 * [io.github.hitoshiichikawa.keynest.domain.model.CustomField] is performed
 * inside the codec — callers do not see this type.
 */
@Serializable
internal data class CustomFieldJson(
    @SerialName("k") val fieldKey: String,
    @SerialName("v") val value: String,
)
