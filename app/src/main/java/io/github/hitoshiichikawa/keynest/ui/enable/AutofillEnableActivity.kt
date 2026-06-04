package io.github.hitoshiichikawa.keynest.ui.enable

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import io.github.hitoshiichikawa.keynest.R
import io.github.hitoshiichikawa.keynest.databinding.AutofillEnableActivityBinding
import io.github.hitoshiichikawa.keynest.util.AutofillServiceStatus
import io.github.hitoshiichikawa.keynest.util.applySystemBarsPadding
import io.github.hitoshiichikawa.keynest.util.enableEdgeToEdgeWithKnDefaults
import com.google.android.material.snackbar.Snackbar

/**
 * Guidance screen that nudges the user to enable KeyNest as the device's
 * Autofill service.
 *
 * Requirements: 6.1, 6.2, 6.3 (Issue #12); aligned to the JSX
 * `ScreenOnboarding` mock by Issue #31.
 *
 * Behaviour (unchanged across Issue #31 redesign):
 * - onResume re-checks [AutofillServiceStatus.isCurrentService] so the
 *   "already enabled" state appears immediately after the user returns
 *   from the Settings activity.
 * - The "Enable" button launches Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE
 *   with the `package:` URI of this app, which is the documented way to
 *   pre-select KeyNest in the Settings picker (and falls back to a Snackbar
 *   on pre-O devices or when the Settings activity is unavailable).
 *
 * New behaviour (Issue #31):
 * - The "later" sub-action button finishes this Activity and returns to
 *   the caller (typically [io.github.hitoshiichikawa.keynest.ui.list.CredentialListActivity]).
 *   Persistence ("don't show again") is intentionally Out of Scope for #31.
 */
class AutofillEnableActivity : AppCompatActivity() {

    private lateinit var binding: AutofillEnableActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeWithKnDefaults()
        binding = AutofillEnableActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()
        binding.btnEnable.setOnClickListener { launchSettings() }
        binding.btnLater.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        renderState()
    }

    /**
     * Visibility groups follow Issue #31 Req 7.x:
     * - When the user has already chosen KeyNest as their Autofill provider
     *   we hide the action area (primary + later) and show the "already
     *   enabled" container instead.
     * - Otherwise we show the action area and hide the "already enabled"
     *   container.
     */
    private fun renderState() {
        val enabled = isAutofillServiceEnabled()
        binding.groupActions.visibility = if (enabled) View.GONE else View.VISIBLE
        binding.groupAlreadyEnabled.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun launchSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Snackbar.make(binding.root, R.string.autofill_enable_settings_unavailable, Snackbar.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.autofill_enable_settings_unavailable, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun isAutofillServiceEnabled(): Boolean = AutofillServiceStatus.isCurrentService(this)

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, AutofillEnableActivity::class.java)
    }
}
