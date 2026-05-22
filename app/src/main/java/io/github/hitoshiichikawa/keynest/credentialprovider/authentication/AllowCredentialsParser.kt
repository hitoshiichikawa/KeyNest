package io.github.hitoshiichikawa.keynest.credentialprovider.authentication

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses the WebAuthn `PublicKeyCredentialRequestOptionsJSON` payload
 * delivered as `BeginGetPublicKeyCredentialOption.requestJson` into the
 * subset of fields needed by [GetEntryBuilder] (Issue #100 / parent #89 /
 * design §4.3).
 *
 * Symmetry with [io.github.hitoshiichikawa.keynest.credentialprovider.KeyNestCredentialProviderService]'s
 * `extractExcludeCredentialIds` helper (#99): both use the
 * `ignoreUnknownKeys = true; isLenient = true` Json instance and treat
 * malformed input as **empty** rather than throwing so the OS sheet can
 * still render whatever candidates are extractable.
 */
internal object AllowCredentialsParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Returns the `allowCredentials[].id` (base64url) list in the order it
     * appears in [requestJson]. Defensive: missing array, wrong types,
     * malformed JSON, or non-string `id` fields all yield an **empty list**
     * (req 未解決事項 3 / #100 design §4.3 / aligned with #99
     * `extractExcludeCredentialIds`).
     */
    fun parseAllowCredentialIds(requestJson: String): List<String> {
        return try {
            val root = json.parseToJsonElement(requestJson).jsonObject
            val arr = root["allowCredentials"]?.jsonArray ?: return emptyList()
            arr.mapNotNull { element ->
                // Each element MUST be an object with an `id` string field.
                runCatching {
                    element.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Returns `PublicKeyCredentialRequestOptionsJSON.rpId`. Throws
     * [IllegalArgumentException] when the field is missing — the caller
     * ([GetEntryBuilder.build]) treats this as a skip signal for the whole
     * option (design §4.4).
     */
    fun parseRpId(requestJson: String): String {
        val root = try {
            json.parseToJsonElement(requestJson).jsonObject
        } catch (t: Throwable) {
            throw IllegalArgumentException("requestJson is not a JSON object", t)
        }
        val rpId = root["rpId"]?.jsonPrimitive?.contentOrNull
        require(!rpId.isNullOrBlank()) {
            "PublicKeyCredentialRequestOptionsJSON.rpId missing or blank"
        }
        return rpId
    }
}
