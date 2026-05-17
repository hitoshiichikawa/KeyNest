package io.github.hitoshiichikawa.keynest.ui.edit

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Source-level audit that backs NFR 1.1 / 1.2 for Issue #14: the full
 * 64-char SHA-256 hex must never be forwarded to logcat / SafeLogger /
 * crash analytics. Only [io.github.hitoshiichikawa.keynest.util.SafeLogger.previewHex]
 * (which truncates to the first 8 chars + "...") is permitted.
 *
 * Strategy:
 * - Read the CredentialEditActivity.kt source.
 * - Locate the `copySignatureHexToClipboard` method body and surrounding
 *   lines that handle the `hex` variable.
 * - Assert that every reference to the local `hex` variable inside a
 *   `SafeLogger.info|warn|error|debug` call goes through `previewHex`.
 *
 * This is a static check rather than a runtime test on purpose: a runtime
 * test would only catch regressions that exercise the code path, whereas
 * the audit catches any future commit that adds an unsafe log line.
 */
class CredentialEditActivityLogAuditTest {

    @Test
    fun activitySource_doesNotLogFullSignatureHex() {
        // Arrange: resolve the production source file relative to module root.
        val source = File("src/main/java/inc/goodanswers/keynest/ui/edit/CredentialEditActivity.kt")
        check(source.exists()) { "expected CredentialEditActivity.kt at $source" }
        val text = source.readText()

        // Act: collect every SafeLogger.* call that contains the variable
        // name `hex` literally.
        val lines = text.lineSequence().toList()
        val suspicious = lines.filter { line ->
            line.contains("SafeLogger.") && line.contains("hex")
        }

        // Assert: every such line must wrap `hex` in `previewHex(`.
        suspicious.forEach { line ->
            assertThat(line).contains("previewHex(")
        }
        // Sanity: SafeLogger is used at least once with previewHex so we
        // know the search did not silently match zero lines.
        assertThat(text).contains("SafeLogger.previewHex(")
    }

    @Test
    fun activitySource_doesNotMaterialiseFullHexIntoLoggableInterpolation() {
        // Defensive: catch the easy mistake "${'$'}hex" embedded inside a
        // SafeLogger string literal. We accept the canonical safe form
        // `${'$'}{SafeLogger.previewHex(hex)}` but reject a bare `${'$'}hex` or
        // `${'$'}{hex}` inside any SafeLogger.* call argument.
        val source = File("src/main/java/inc/goodanswers/keynest/ui/edit/CredentialEditActivity.kt")
        val text = source.readText()

        // Naive heuristic: regex over single lines.
        val unsafePattern = Regex("""SafeLogger\.(debug|info|warn|error)\([^)]*\$\{?hex\}?[^)]*\)""")
        val matches = unsafePattern.findAll(text).toList()
        assertThat(matches).isEmpty()
    }
}
