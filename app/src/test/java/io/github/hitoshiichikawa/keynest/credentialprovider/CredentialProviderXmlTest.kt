package io.github.hitoshiichikawa.keynest.credentialprovider

import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * Verifies the contents of `res/xml/credential_provider.xml` (Issue #90 / req 2.x / 6.5).
 *
 * The xml is parsed via `Resources.getXml(...)` so AAPT-compiled binary XML is
 * exercised (the same format the OS reads). Three properties are pinned:
 *
 * 1. The root element is `<credential-provider>` (req 2.1).
 * 2. At least one `<capability>` declares `androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL`
 *    (req 2.2 / 6.5).
 * 3. No `<capability>` declares `androidx.credentials.TYPE_PASSWORD_CREDENTIAL`
 *    (req 2.3) — password credentials remain on the autofill path.
 *
 * Discoverable / non-discoverable gating is covered by req 2.4: the xml must
 * not include any attribute that would restrict either case. The current xml
 * declares only `<capabilities>` / `<capability android:name="...">` and
 * therefore implicitly supports both. We assert that no unknown attribute
 * sneaks into the `<credential-provider>` root or `<capability>` element so a
 * later edit cannot quietly add a discoverable-only flag.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CredentialProviderXmlTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun root_is_credentialProvider() {
        context.resources.getXml(R.xml.credential_provider).use { parser ->
            val rootName = advanceToFirstStartTag(parser)
            assertThat(rootName).isEqualTo("credential-provider")
        }
    }

    @Test
    fun declares_publicKeyCredential_capability() {
        val names = readCapabilityNames()
        assertThat(names).contains(ANDROIDX_TYPE_PUBLIC_KEY_CREDENTIAL)
    }

    @Test
    fun doesNotDeclare_passwordCredential_capability() {
        val names = readCapabilityNames()
        assertThat(names).doesNotContain(ANDROIDX_TYPE_PASSWORD_CREDENTIAL)
    }

    @Test
    fun rootAndCapability_haveNoDiscoverabilityRestrictingAttributes() {
        // req 2.4: neither the <credential-provider> root nor any <capability>
        // element may carry an attribute that limits the provider to only
        // discoverable or only non-discoverable PassKeys. We assert the
        // attribute set is constrained to the expected names so a regression
        // (e.g. an accidental android:discoverableOnly="true") fails this test.
        val rootAttrs = mutableListOf<String>()
        val capabilityAttrs = mutableListOf<String>()
        context.resources.getXml(R.xml.credential_provider).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                val sink = when (parser.name) {
                    "credential-provider" -> rootAttrs
                    "capability" -> capabilityAttrs
                    else -> null
                } ?: continue
                for (i in 0 until parser.attributeCount) {
                    sink += parser.getAttributeName(i)
                }
            }
        }
        // The credential-provider root is xmlns-only at the source level; at
        // the binary-xml layer the namespace declaration is consumed by the
        // parser and not reported as an attribute, so the visible set is empty.
        assertThat(rootAttrs).isEmpty()
        // The capability element should only carry android:name.
        assertThat(capabilityAttrs).containsExactly("name")
    }

    private fun readCapabilityNames(): List<String> {
        val names = mutableListOf<String>()
        context.resources.getXml(R.xml.credential_provider).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "capability") {
                    val name = parser.getAttributeValue(ANDROID_NAMESPACE, "name")
                    if (name != null) {
                        names += name
                    }
                }
            }
        }
        return names
    }

    private fun advanceToFirstStartTag(parser: XmlResourceParser): String {
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                return parser.name
            }
            parser.next()
        }
        error("No start tag found in credential_provider.xml")
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val ANDROIDX_TYPE_PUBLIC_KEY_CREDENTIAL = "androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL"
        const val ANDROIDX_TYPE_PASSWORD_CREDENTIAL = "androidx.credentials.TYPE_PASSWORD_CREDENTIAL"
    }
}
