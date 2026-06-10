package io.github.hitoshiichikawa.keynest.util

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Issue #137: production source のログ衛生をソースレベルで pin する監査
 * テスト（`DeprecatedSystemBarApiRemovalTest` と同型の source-pinning）。
 *
 * - `android.util.Log` の直接 import を SafeLogger.kt 単独に限定する。
 *   SafeLogger 以外が raw Log を使うと、redaction 規約（Throwable message
 *   の class 名化）と BuildConfig.DEBUG ゲート（release で DEBUG 行を
 *   落とす）の両方を迂回してしまうため。
 * - パスワード / ユーザー名の「長さ」をログに載せる行（`passLen=` /
 *   `userLen=`）の再発を防ぐ（NFR 5.1: 秘匿情報のメタデータも出さない）。
 */
class RawLogImportAuditTest {

    private val productionSourceRoot: File = File("src/main/java")

    @Test
    fun productionSources_doNotImportRawAndroidUtilLog() {
        // Arrange: `android.util.LruCache` 等を誤検知しないよう行単位の
        // 完全一致で判定する。
        val offendingLine = "import android.util.Log"

        // Act
        val offenders = walkProductionSources()
            .filter { file ->
                file.readLines().any { it.trim() == offendingLine }
            }
            .map { it.relativePathString() }
            .filterNot { it.endsWith("SafeLogger.kt") }
            .toList()

        // Assert
        assertWithMessage(
            "android.util.Log は util/SafeLogger.kt 経由でのみ使用すること。直接 import しているファイル",
        ).that(offenders).isEmpty()
    }

    @Test
    fun productionSources_doNotLogCredentialLengths() {
        // Arrange
        val needles = listOf("passLen=", "userLen=")

        // Act
        val offenders = walkProductionSources()
            .filter { file -> needles.any { file.readText().contains(it) } }
            .map { it.relativePathString() }
            .toList()

        // Assert
        assertWithMessage(
            "パスワード / ユーザー名の長さをログ文字列に含めないこと（NFR 5.1）。該当ファイル",
        ).that(offenders).isEmpty()
    }

    private fun walkProductionSources(): Sequence<File> {
        val root = productionSourceRoot
        if (!root.isDirectory) {
            // Defensive: working directory が :app でない場合はテスト失敗で
            // 気付けるよう、存在しない印を返すのではなく即時に落とす。
            throw AssertionError("missing source root: ${root.absolutePath}")
        }
        return root.walkTopDown()
            .filter { it.isFile }
            .filter { it.extension == "kt" || it.extension == "java" }
    }

    private fun File.relativePathString(): String =
        absolutePath.removePrefix(productionSourceRoot.absolutePath)
            .trimStart(File.separatorChar)
}
