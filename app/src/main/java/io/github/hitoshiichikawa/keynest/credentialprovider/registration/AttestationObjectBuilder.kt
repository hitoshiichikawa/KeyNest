package io.github.hitoshiichikawa.keynest.credentialprovider.registration

/**
 * `fmt = "none"` の WebAuthn attestation object を組み立てる
 * (Issue #99 / parent #89 / design §4.6 / §7.3).
 *
 * 出力は WebAuthn Level 2 §6.5.4 に従う CBOR map (3 entries):
 *
 * ```
 * {
 *     "fmt":      "none",
 *     "attStmt":  {},          // empty map
 *     "authData": <authenticatorData byte string>,
 * }
 * ```
 *
 * map のキー順は **`"fmt" → "attStmt" → "authData"` の WebAuthn 仕様順** で固定し、
 * alphabetical / canonical ordering ("attStmt" < "authData" < "fmt") は採用しない。
 * これは WebAuthn テストベクタ (design §10.5) との bytewise 一致を取るため。
 */
internal object AttestationObjectBuilder {

    /**
     * `fmt = "none"` の attestation object を CBOR encode する。`authenticatorData` は
     * そのまま CBOR byte string として `"authData"` 値に格納される (長さに応じて
     * `0x58` 1-byte length / `0x59` 2-byte length header が選ばれる)。
     */
    fun buildFormatNone(authenticatorData: ByteArray): ByteArray {
        return CborWriter()
            .writeMapHeader(3)
            .writeTextString("fmt").writeTextString("none")
            .writeTextString("attStmt").writeMapHeader(0)
            .writeTextString("authData").writeByteString(authenticatorData)
            .toByteArray()
    }
}
