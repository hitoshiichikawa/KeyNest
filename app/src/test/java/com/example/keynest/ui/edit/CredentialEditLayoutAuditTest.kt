package com.example.keynest.ui.edit

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Source-level audit of `credential_edit_activity.xml` that backs the
 * Issue #14 non-functional requirements.
 *
 * - NFR 2.1: accessibility content descriptions on the section header and
 *   on the credential-ID toggle.
 * - NFR 2.3: the SHA-256 hex copy affordance has a 48dp minimum touch
 *   target.
 * - NFR 3.1: every user-visible string is referenced via @string/...
 *   (no hard-coded English / Japanese leaks into the layout).
 *
 * A static check is preferred over a Robolectric UI test here because the
 * layout file is the canonical source of truth and is read by `aapt` /
 * View binding at compile time -- if the resources move, this test catches
 * the regression at unit-test time.
 */
class CredentialEditLayoutAuditTest {

    private val layoutFile: File = File("src/main/res/layout/credential_edit_activity.xml")
    private val stringsFile: File = File("src/main/res/values/strings.xml")

    @Test
    fun advancedHeader_hasAccessibilityContentDescription() {
        val xml = layoutFile.readText()
        check(layoutFile.exists()) { "missing $layoutFile" }

        // The advanced_header element must carry contentDescription so
        // TalkBack announces the section toggle (NFR 2.1).
        val headerSection = xml.substringAfter("@+id/advanced_header").substringBefore("</LinearLayout>")
        assertThat(headerSection).contains("contentDescription=\"@string/advanced_section_header_a11y\"")
    }

    @Test
    fun credentialIdToggle_hasAccessibilityContentDescription() {
        val xml = layoutFile.readText()

        val toggleSection = xml.substringAfter("@+id/toggle_credential_id").substringBefore("/>")
        assertThat(toggleSection).contains("contentDescription=\"@string/advanced_toggle_show_id_a11y\"")
    }

    @Test
    fun copyButton_meetsAccessibilityTouchTarget_48dp() {
        val xml = layoutFile.readText()

        // The Material copy button must declare at least 48dp minWidth
        // and minHeight (NFR 2.3). Material's default already meets this
        // but we pin it explicitly to defend against a theme change.
        val buttonSection = xml.substringAfter("@+id/btn_copy_signature_hex").substringBefore("/>")
        assertThat(buttonSection).contains("android:minWidth=\"48dp\"")
        assertThat(buttonSection).contains("android:minHeight=\"48dp\"")
    }

    @Test
    fun credentialIdToggle_meetsAccessibilityTouchTarget_48dp() {
        val xml = layoutFile.readText()
        val toggleSection = xml.substringAfter("@+id/toggle_credential_id").substringBefore("/>")
        assertThat(toggleSection).contains("android:minWidth=\"48dp\"")
        assertThat(toggleSection).contains("android:minHeight=\"48dp\"")
    }

    @Test
    fun signatureHexValue_usesMonospaceFont() {
        // Req 3.5: SHA-256 hex must be displayed in monospace so the
        // 64 characters line up readably.
        val xml = layoutFile.readText()
        val valueSection = xml.substringAfter("@+id/value_signature_hex").substringBefore("/>")
        assertThat(valueSection).contains("android:fontFamily=\"monospace\"")
    }

    @Test
    fun advancedRows_areReadOnly_notFocusableNorEditable() {
        // Req 6.3: every value cell in the Advanced section must be
        // non-editable. We approximate "read-only" by pinning
        // focusable=false on the regular value rows so that the framework
        // does not treat them as input. The signature-hex value is allowed
        // to be selectable (to support copy via long-press) -- the copy
        // action is read-only by definition.
        val xml = layoutFile.readText()
        val readOnlyTargets = listOf(
            "@+id/value_created_at",
            "@+id/value_updated_at",
            "@+id/value_signature_captured_at",
            "@+id/value_credential_id",
        )
        readOnlyTargets.forEach { id ->
            val section = xml.substringAfter(id).substringBefore("/>")
            assertThat(section).contains("android:focusable=\"false\"")
            assertThat(section).contains("android:textIsSelectable=\"false\"")
        }
    }

    @Test
    fun allAdvancedSectionStrings_areResources_notLiterals() {
        val xml = layoutFile.readText()

        // Extract the advanced section block.
        val start = xml.indexOf("@+id/advanced_header")
        check(start >= 0) { "advanced_header anchor not found" }
        val advancedXml = xml.substring(start)

        // No raw android:text= with a non-resource value.
        val literalText = Regex("""android:text="(?!@)([^"]+)"""").findAll(advancedXml).toList()
        assertThat(literalText.map { it.groupValues[1] }).isEmpty()

        // Every @string referenced must actually exist in strings.xml.
        val strings = stringsFile.readText()
        Regex("""@string/(\w+)""").findAll(advancedXml).map { it.groupValues[1] }.distinct().forEach { name ->
            assertThat(strings).contains("name=\"$name\"")
        }
    }
}
