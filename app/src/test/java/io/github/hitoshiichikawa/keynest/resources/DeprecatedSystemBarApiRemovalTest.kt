package io.github.hitoshiichikawa.keynest.resources

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Issue #128: source-level pinning that Theme.KeyNest no longer declares the
 * Android 15 (API 35) deprecated system bar colour attributes, and that no
 * production source file under `app/src/main/java` calls the deprecated
 * Window setter APIs `setStatusBarColor` / `setNavigationBarColor`.
 *
 * Source-level pinning is chosen for the same reason as
 * `Material3ThemeMigrationTest` / `FontTypefaceWiringTest`: the artefacts
 * under test are a pure XML resource (`themes.xml`) and a pure Kotlin source
 * tree, so a textual scan is sufficient and avoids the cost of a Robolectric
 * Activity spin-up.
 *
 * Mapped AC coverage:
 * - Req 1.1: Theme.KeyNest does not declare `android:statusBarColor`.
 * - Req 1.2: Theme.KeyNest does not declare `android:navigationBarColor`.
 * - Req 1.3: no production source under `app/src/main/java` references the
 *   string `setStatusBarColor` (the Window setter that is deprecated /
 *   no-op on Android 15).
 * - Req 1.4: no production source under `app/src/main/java` references the
 *   string `setNavigationBarColor` (same rationale as Req 1.3).
 *
 * Out of unit-test scope:
 * - Req 1.5 (Play Console no longer reports the edge-to-edge deprecation
 *   warning after the release APK is uploaded) is a manual AC that is
 *   verified by a human reviewer on the next release; see impl-notes.md
 *   "Implementation Notes / Task 4" for the recording.
 * - NFR 1.1〜1.4 (the `Theme.KeyNest` invariants and the pre-existing test
 *   suite remaining green without expectation changes) are not re-asserted
 *   here; `Material3ThemeMigrationTest` / `FontTypefaceWiringTest` already
 *   pin the invariants, and this test file does not modify either of them.
 */
class DeprecatedSystemBarApiRemovalTest {

    private val themesFile: File = File("src/main/res/values/themes.xml")
    private val productionSourceRoot: File = File("src/main/java")

    @Test
    fun themeKeyNest_doesNotDeclareAndroidStatusBarColor() {
        // Req 1.1: scoping the check to the Theme.KeyNest declaration
        // (between its `<style ... parent=...>` opening tag and the next
        // `</style>`) ensures we do not false-match an unrelated style.
        // Theme.KeyNest.Translucent intentionally has no statusBarColor
        // either, but isolating Theme.KeyNest keeps the regression guard
        // narrowly aimed at the screen-defining theme.
        //
        // The check anchors on the full `<item name="android:statusBarColor"`
        // token rather than the bare attribute name so an XML comment
        // mentioning the attribute (e.g. "Android 15 で android:statusBarColor
        // が非推奨化したため撤去") inside the Theme.KeyNest block does NOT
        // false-trigger the regression guard. The actual deprecated path
        // would be the `<item name="android:statusBarColor">…</item>` element.
        val keyNestBlock = readThemeKeyNestBlock()
        assertThat(keyNestBlock).doesNotContain("<item name=\"android:statusBarColor\"")
    }

    @Test
    fun themeKeyNest_doesNotDeclareAndroidNavigationBarColor() {
        // Req 1.2: same isolation and item-tag anchoring as the
        // statusBarColor check above.
        val keyNestBlock = readThemeKeyNestBlock()
        assertThat(keyNestBlock).doesNotContain("<item name=\"android:navigationBarColor\"")
    }

    @Test
    fun productionSources_doNotCallSetStatusBarColor() {
        // Req 1.3: no production source under `app/src/main/java` may
        // reference the deprecated Window#setStatusBarColor setter. The
        // Android 15 (API 35) deprecation makes this setter no-op, and
        // Play Console surfaces a warning whenever the symbol is reachable
        // from the APK. Walking only `app/src/main/java` excludes tests
        // (this file itself contains the symbol as a string literal) and
        // generated `build/` artefacts.
        val offenders = findProductionSourcesContaining("setStatusBarColor")
        assertWithMessage("production sources still referencing setStatusBarColor")
            .that(offenders).isEmpty()
    }

    @Test
    fun productionSources_doNotCallSetNavigationBarColor() {
        // Req 1.4: same rationale as the setStatusBarColor check above.
        val offenders = findProductionSourcesContaining("setNavigationBarColor")
        assertWithMessage("production sources still referencing setNavigationBarColor")
            .that(offenders).isEmpty()
    }

    /**
     * Returns the textual body of the `Theme.KeyNest` `<style>` block (from
     * just after the opening `<style name="Theme.KeyNest" parent=...>` tag
     * up to the matching `</style>`). The slicing intentionally anchors on
     * `"Theme.KeyNest"` followed by ` parent` so it does not accidentally
     * match `Theme.KeyNest.Translucent`.
     */
    private fun readThemeKeyNestBlock(): String {
        val themes = themesFile.readText()
        return themes.substringAfter("\"Theme.KeyNest\" parent")
            .substringBefore("</style>")
    }

    /**
     * File-walks `app/src/main/java` and returns the list of `.kt` / `.java`
     * files whose textual contents contain [needle]. Returning the list of
     * relative paths (instead of just a boolean) makes the failure message
     * actionable: the developer can immediately see which file to edit.
     */
    private fun findProductionSourcesContaining(needle: String): List<String> {
        val root = productionSourceRoot
        if (!root.isDirectory) {
            // Defensive: if the working directory is not the `:app` module
            // (e.g. running from the repo root), surface the misconfiguration
            // as a test failure rather than silently passing.
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
