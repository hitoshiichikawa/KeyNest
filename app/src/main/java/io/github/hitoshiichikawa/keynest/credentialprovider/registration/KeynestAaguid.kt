package io.github.hitoshiichikawa.keynest.credentialprovider.registration

/**
 * KeyNest 固有の AAGUID (Authenticator Attestation GUID) を 1 箇所に集約した定数。
 *
 * Issue #99 (parent #89) — WebAuthn 登録セレモニーで生成する attestedCredentialData
 * の先頭 16 byte に埋め込まれる identifier。UUID `2a56cf86-8332-4829-9f2a-e9a4adbc7abe`
 * を big-endian 16 byte 化したもの (design §3.3 / requirements §決定 1)。後続の
 * `AuthenticatorDataBuilder` / `PasskeyCreator` は本クラス経由でのみ参照することで
 * UUID hex 順 / endian 取り違えを防ぐ。
 *
 * `BYTES_INTERNAL` は `private` に隔離し、外部公開する [bytes] は呼び出しごとに
 * `copyOf()` で防御的コピーを返す (NFR 6.1)。`fill(0)` 等で破壊しても次回呼び出しに
 * 影響しない。
 */
internal object KeynestAaguid {

    /**
     * UUID 表現 (`2a56cf86-8332-4829-9f2a-e9a4adbc7abe`)。テスト / ログ照合用。
     * raw を logcat に出すことは想定していない (NFR 1.4)。
     */
    const val UUID_STRING: String = "2a56cf86-8332-4829-9f2a-e9a4adbc7abe"

    /**
     * `2a 56 cf 86 83 32 48 29 9f 2a e9 a4 ad bc 7a be` の big-endian 16 byte。
     * `bytes()` 経由でのみ取得することで外部からの mutate を遮断する。
     */
    private val BYTES_INTERNAL: ByteArray = byteArrayOf(
        0x2A, 0x56, 0xCF.toByte(), 0x86.toByte(),
        0x83.toByte(), 0x32, 0x48, 0x29,
        0x9F.toByte(), 0x2A, 0xE9.toByte(), 0xA4.toByte(),
        0xAD.toByte(), 0xBC.toByte(), 0x7A, 0xBE.toByte(),
    )

    /** 16 byte の防御的コピーを返す。呼び出しごとに新規 instance。 */
    fun bytes(): ByteArray = BYTES_INTERNAL.copyOf()
}
