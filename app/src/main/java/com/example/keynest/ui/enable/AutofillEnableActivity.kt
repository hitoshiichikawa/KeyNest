package com.example.keynest.ui.enable

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.keynest.R
import com.example.keynest.databinding.AutofillEnableActivityBinding
import com.example.keynest.util.AutofillServiceStatus
import com.google.android.material.snackbar.Snackbar

/**
 * Guidance screen that nudges the user to enable KeyNest as the device's
 * Autofill service.
 *
 * Requirements: 6.1, 6.2, 6.3
 *
 * Behaviour:
 * - onResume re-checks [AutofillManager.hasEnabledAutofillServices] so the
 *   "already enabled" message appears immediately after the user returns
 *   from the Settings activity.
 * - The "Enable" button launches Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE
 *   with the `package:` URI of this app, which is the documented way to
 *   pre-select KeyNest in the Settings picker.
 */
class AutofillEnableActivity : AppCompatActivity() {

    private lateinit var binding: AutofillEnableActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AutofillEnableActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnEnable.setOnClickListener { launchSettings() }
    }

    override fun onResume() {
        super.onResume()
        renderState()
    }

    private fun renderState() {
        val enabled = isAutofillServiceEnabled()
        binding.btnEnable.visibility = if (enabled) View.GONE else View.VISIBLE
        binding.textAlreadyEnabled.visibility = if (enabled) View.VISIBLE else View.GONE
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
