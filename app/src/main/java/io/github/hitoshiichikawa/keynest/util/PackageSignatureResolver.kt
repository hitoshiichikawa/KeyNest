package io.github.hitoshiichikawa.keynest.util

import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import io.github.hitoshiichikawa.keynest.domain.model.SigningHash
import java.security.MessageDigest

/**
 * Resolves the SHA-256 signing certificate hash of an installed package.
 *
 * Requirements: 2.1, 2.2, 2.3, 4.1
 *
 * Branching:
 * - API 28+ uses [PackageManager.GET_SIGNING_CERTIFICATES] and reads
 *   `SigningInfo.apkContentsSigners` (or `signingCertificateHistory` if the
 *   signers list rotated). This is preferred because it survives the v3
 *   signature scheme.
 * - API 26-27 falls back to [PackageManager.GET_SIGNATURES] / `signatures[]`.
 *
 * Multi-signer handling: when more than one certificate signs the APK, every
 * signer's SHA-256 is computed individually, sorted (lexicographically by
 * byte value), concatenated, and re-hashed with SHA-256. This canonical form
 * is order-independent and matches across `apkContentsSigners` (which is an
 * unordered set) regardless of the underlying list order.
 *
 * Returns null when the package is not installed (NameNotFoundException) so
 * that [io.github.hitoshiichikawa.keynest.domain.usecase.SaveCredentialUseCase] can record
 * a "signature unavailable" credential per Req 2.2.
 */
class PackageSignatureResolver(
    private val pm: PackageManager,
    private val sdkVersion: Int = Build.VERSION.SDK_INT,
) {

    fun resolveSha256(packageName: String): SigningHash? {
        return try {
            val signatures = readSignatures(packageName) ?: return null
            if (signatures.isEmpty()) return null
            SigningHash(canonicalSha256(signatures))
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    @Suppress("DEPRECATION") // GET_SIGNATURES is the only API 26-27 path
    private fun readSignatures(packageName: String): Array<Signature>? {
        return if (sdkVersion >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo ?: return null
            when {
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory ?: signingInfo.apkContentsSigners
            }
        } else {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            info.signatures
        }
    }

    private fun canonicalSha256(signatures: Array<Signature>): ByteArray {
        // Compute SHA-256 of each signer first, then sort + concatenate +
        // re-hash. This produces a stable identifier irrespective of signer
        // ordering, while still being deterministic for the single-signer
        // common case (one hash sorted by itself, concatenated, re-hashed).
        val perSigner = signatures.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()) }
        val sorted = perSigner.sortedWith(Comparator { a, b -> compareBytes(a, b) })
        val combined = MessageDigest.getInstance("SHA-256")
        for (bytes in sorted) combined.update(bytes)
        return combined.digest()
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        val limit = minOf(a.size, b.size)
        for (i in 0 until limit) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return a.size - b.size
    }
}
