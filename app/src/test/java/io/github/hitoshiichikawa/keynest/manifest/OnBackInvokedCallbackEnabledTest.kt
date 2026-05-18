package io.github.hitoshiichikawa.keynest.manifest

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Issue #50: backs Requirement 1.1 by mechanically asserting that the source
 * AndroidManifest opts in to the predictive back gesture via
 * `android:enableOnBackInvokedCallback="true"` on the `<application>` element.
 *
 * The check is performed directly against the source manifest XML rather than
 * against a runtime `ApplicationInfo` field because the runtime getter
 * (`isOnBackInvokedCallbackEnabled`) is not exposed on the public Android SDK
 * stubs we compile against and would force this test to rely on hidden API.
 * Parsing the manifest is sufficient to lock the attribute in.
 */
class OnBackInvokedCallbackEnabledTest {

    @Test
    fun applicationManifest_optsIntoOnBackInvokedCallback() {
        val manifestFile = locateManifestFile()
        val applicationElement = parseApplicationElement(manifestFile)

        val attributeValue = applicationElement.getAttributeNS(
            ANDROID_NAMESPACE,
            ATTRIBUTE_NAME
        )

        assertThat(attributeValue).isEqualTo("true")
    }

    private fun locateManifestFile(): File {
        // Tests run with `app/` as working directory under Gradle, so the
        // manifest is reachable through a relative path. Falling back to a
        // parent search keeps the test resilient to alternate invocations.
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("AndroidManifest.xml not found in any expected location: $candidates")
    }

    private fun parseApplicationElement(manifestFile: File): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val document = factory.newDocumentBuilder().parse(manifestFile)
        val applicationNodes = document.getElementsByTagName("application")
        check(applicationNodes.length == 1) {
            "Expected exactly one <application> element, found ${applicationNodes.length}"
        }
        return applicationNodes.item(0) as Element
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val ATTRIBUTE_NAME = "enableOnBackInvokedCallback"
    }
}
