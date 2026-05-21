package io.github.hitoshiichikawa.keynest.manifest

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Issue #90: backs Requirement 1.x / 6.4 by mechanically asserting that the
 * merged AndroidManifest declares `KeyNestCredentialProviderService` with the
 * exact set of attributes the OS Credential Manager framework expects on
 * Android 14+.
 *
 * Properties verified:
 *  - 1.1: the service entry exists with the expected fully-qualified name
 *  - 1.2: permission == BIND_CREDENTIAL_PROVIDER_SERVICE
 *  - 1.3: exported == true
 *  - 1.4: intent-filter declares
 *         `android.service.credentials.CredentialProviderService`
 *  - 1.5: meta-data `android.credentials.provider` references @xml/credential_provider
 *
 * The manifest is read through `PackageManager` so AAPT-merged Manifest is
 * exercised end-to-end (same view the OS gets).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CredentialProviderServiceManifestTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val packageManager: PackageManager = context.packageManager
    private val packageName: String = context.packageName

    @Test
    fun service_isDeclared_withExpectedName() {
        val service = findCredentialProviderServiceInfo()
        assertThat(service.name).isEqualTo(FQCN_CREDENTIAL_PROVIDER_SERVICE)
    }

    @Test
    fun service_requires_bindCredentialProviderServicePermission() {
        val service = findCredentialProviderServiceInfo()
        assertThat(service.permission).isEqualTo(PERMISSION_BIND_CREDENTIAL_PROVIDER_SERVICE)
    }

    @Test
    fun service_isExported() {
        val service = findCredentialProviderServiceInfo()
        assertThat(service.exported).isTrue()
    }

    @Test
    fun service_advertisesCredentialProviderIntentAction() {
        val matches = packageManager.queryIntentServices(
            Intent(ACTION_CREDENTIAL_PROVIDER_SERVICE).setPackage(packageName),
            0,
        )
        val names = matches.map { it.serviceInfo.name }
        assertThat(names).contains(FQCN_CREDENTIAL_PROVIDER_SERVICE)
    }

    @Test
    fun service_referencesCredentialProviderXmlViaMetaData() {
        val service = findCredentialProviderServiceInfo(includeMetaData = true)
        val metaData = service.metaData
        assertThat(metaData).isNotNull()
        // Robolectric exposes the parsed meta-data as the integer resource id;
        // it must point at R.xml.credential_provider so the OS reads the
        // capability list authored by Issue #90.
        val resourceId = metaData.getInt(METADATA_NAME_CREDENTIAL_PROVIDER, 0)
        assertThat(resourceId).isEqualTo(R.xml.credential_provider)
    }

    private fun findCredentialProviderServiceInfo(includeMetaData: Boolean = false): android.content.pm.ServiceInfo {
        val flags = PackageManager.GET_SERVICES or
            (if (includeMetaData) PackageManager.GET_META_DATA else 0)
        val info = packageManager.getPackageInfo(packageName, flags)
        val services = info.services ?: emptyArray()
        return services.firstOrNull { it.name == FQCN_CREDENTIAL_PROVIDER_SERVICE }
            ?: error(
                "KeyNestCredentialProviderService not declared. Services seen: " +
                    services.joinToString { it.name },
            )
    }

    private companion object {
        const val FQCN_CREDENTIAL_PROVIDER_SERVICE =
            "io.github.hitoshiichikawa.keynest.credentialprovider.KeyNestCredentialProviderService"
        const val PERMISSION_BIND_CREDENTIAL_PROVIDER_SERVICE =
            "android.permission.BIND_CREDENTIAL_PROVIDER_SERVICE"
        const val ACTION_CREDENTIAL_PROVIDER_SERVICE =
            "android.service.credentials.CredentialProviderService"
        const val METADATA_NAME_CREDENTIAL_PROVIDER = "android.credentials.provider"
    }
}
