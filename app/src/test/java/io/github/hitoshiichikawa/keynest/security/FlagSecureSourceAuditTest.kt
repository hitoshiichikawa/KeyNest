package io.github.hitoshiichikawa.keynest.security

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Issue #135: 機密情報を表示する Activity が SecureWindow.apply を
 * 呼び出していることをソースレベルで pin する監査テスト。
 *
 * ソーススキャン方式を選ぶ理由は `DeprecatedSystemBarApiRemovalTest` /
 * `Material3ThemeMigrationTest` と同じ — 対象 Activity 群は ServiceLocator
 * 初期化や PendingIntentHandler 等の重い依存を持ち、Robolectric で 5 画面を
 * spin-up するコストに対し「呼び出しが存在する」ことの検査は文字列照合で
 * 十分に決定的なため。FLAG_SECURE が実際にウィンドウへ立つ挙動は
 * `SecureWindowTest` が検証する（役割分担）。
 *
 * 対応 AC:
 * - 5 つの機密 Activity が onCreate で SecureWindow.apply(window) を呼ぶ
 * - FLAG_SECURE の設定は SecureWindow ヘルパー経由のみ（直書き禁止）
 */
class FlagSecureSourceAuditTest {

    private val productionSourceRoot: File = File("src/main/java")

    /**
     * 機密情報（復号済みクレデンシャル・一覧・PassKey セレモニー）を扱う
     * Activity の一覧。画面を追加する場合、機密情報を表示するなら
     * このリストと当該 Activity の onCreate の両方に追記すること。
     */
    private val sensitiveActivitySources = listOf(
        "io/github/hitoshiichikawa/keynest/ui/list/CredentialListActivity.kt",
        "io/github/hitoshiichikawa/keynest/ui/edit/CredentialEditActivity.kt",
        "io/github/hitoshiichikawa/keynest/autofill/unlock/AutofillUnlockActivity.kt",
        "io/github/hitoshiichikawa/keynest/credentialprovider/registration/PasskeyCreateActivity.kt",
        "io/github/hitoshiichikawa/keynest/credentialprovider/authentication/PasskeyAuthActivity.kt",
    )

    @Test
    fun sensitiveActivities_allApplySecureWindow() {
        // Arrange
        val needle = "SecureWindow.apply(window)"

        // Act
        val offenders = sensitiveActivitySources.filter { relPath ->
            val file = File(productionSourceRoot, relPath)
            !file.isFile || !file.readText().contains(needle)
        }

        // Assert
        assertWithMessage(
            "SecureWindow.apply(window) を呼んでいない（またはパスが移動した）機密 Activity",
        ).that(offenders).isEmpty()
    }

    @Test
    fun productionSources_doNotSetFlagSecureOutsideHelper() {
        // Arrange
        val needle = "FLAG_SECURE"

        // Act
        val offenders = findProductionSourcesContaining(needle)
            .filterNot { it.endsWith("SecureWindow.kt") }

        // Assert: ヘルパー以外での直書きを禁止し、適用箇所の機械検査
        // （上のテスト）が常に全量を捕捉できる状態を守る。
        assertWithMessage(
            "FLAG_SECURE は util/SecureWindow.kt 経由でのみ設定すること。直書きしているファイル",
        ).that(offenders).isEmpty()
    }

    /** `DeprecatedSystemBarApiRemovalTest` と同型の production source 走査。 */
    private fun findProductionSourcesContaining(needle: String): List<String> {
        val root = productionSourceRoot
        if (!root.isDirectory) {
            return listOf("<missing source root: ${root.absolutePath}>")
        }
        val rootPath = root.absolutePath
        return root.walkTopDown()
            .filter { it.isFile }
            .filter { it.extension == "kt" || it.extension == "java" }
            .filter { it.readText().contains(needle) }
            .map { it.absolutePath.removePrefix(rootPath).trimStart(File.separatorChar) }
            .toList()
    }
}
