package inc.goodanswers.keynest.manifest

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * T10.6: Manifest audit. Backs NFR 1.5 (no network IO from this app) by
 * mechanically asserting that the merged manifest does NOT declare
 * `android.permission.INTERNET`. Without that permission any networking
 * attempt fails with SecurityException at runtime, so this is the
 * cheapest possible guarantee that credentials cannot accidentally leave
 * the device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class InternetPermissionAbsenceTest {

    @Test
    fun manifest_doesNotDeclare_internetPermission() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val declared = info.requestedPermissions?.toList() ?: emptyList()

        assertThat(declared).doesNotContain(android.Manifest.permission.INTERNET)
        // While we're here, also pin that no accessibility / device owner
        // permissions snuck in.
        assertThat(declared).doesNotContain(android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE)
        assertThat(declared).doesNotContain(android.Manifest.permission.BIND_DEVICE_ADMIN)
    }
}
