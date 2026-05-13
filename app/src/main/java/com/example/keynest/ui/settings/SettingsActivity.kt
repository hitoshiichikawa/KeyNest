package com.example.keynest.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.keynest.R
import com.example.keynest.databinding.SettingsActivityBinding
import com.example.keynest.di.ServiceLocator
import com.example.keynest.domain.model.AutofillStatus
import com.example.keynest.domain.model.DeviceLockStatus
import com.example.keynest.ui.danger.DangerZoneActivity
import com.example.keynest.ui.oss.OssLicensesActivity
import com.example.keynest.util.AdvancedDetailsFormatter
import com.example.keynest.util.SystemSettingsIntents
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Settings screen for KeyNest. Issue #10 Req 1.x, 2.x, 3.x, 4.x, 5.x,
 * 6.x.
 *
 * Renders [SettingsViewModel.uiState] into five MaterialCardView
 * sections (Autofill / Security / Vault / About / Danger Zone). Tap
 * handlers:
 *
 * - "Open Android Settings" -> SystemSettingsIntents
 *   .openAutofillServiceChooser (Req 2.4 / 2.6)
 * - "Open Android Security settings" -> SystemSettingsIntents
 *   .openSecuritySettings (Req 3.4 / 3.6)
 * - "Open source licenses" -> OssLicensesActivity (Req 5.2 / 5.3)
 * - "Open Vault clear screen" -> DangerZoneActivity (Req 6.2 / 6.3)
 *
 * onResume calls [SettingsViewModel.refresh] so the autofill / lock /
 * storage values reflect what the user did in the system Settings
 * (Req 2.5 / 3.5).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: SettingsActivityBinding

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(
            appContext = applicationContext,
            observeMetadata = ServiceLocator.observeVaultMetadataUseCase,
            getStorage = ServiceLocator.getVaultStorageUsageUseCase,
            getLockStatus = ServiceLocator.getDeviceLockStatusUseCase,
            appInfoProvider = ServiceLocator.appInfoProvider,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() } // Req 1.3

        wireButtons()
        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        // Req 2.5 / 3.5: refresh non-reactive sources when returning
        // from external Settings activities.
        viewModel.refresh()
    }

    // ---- wiring ---------------------------------------------------------

    private fun wireButtons() {
        binding.btnOpenAutofillSettings.setOnClickListener {
            SystemSettingsIntents.openAutofillServiceChooser(this).onFailure {
                showIntentUnavailableSnackbar()
            }
        }
        binding.btnOpenSecuritySettings.setOnClickListener {
            SystemSettingsIntents.openSecuritySettings(this).onFailure {
                showIntentUnavailableSnackbar()
            }
        }
        binding.btnOssLicenses.setOnClickListener {
            startActivity(OssLicensesActivity.newIntent(this))
        }
        binding.btnOpenDangerZone.setOnClickListener {
            startActivity(DangerZoneActivity.newIntent(this))
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> bind(state) }
            }
        }
    }

    private fun bind(state: SettingsUiState) {
        bindAutofill(state.autofillStatus)
        bindLockStatus(state.lockStatus)
        bindVault(state.metadata, state.storageBytes)
        bindAbout(state)
    }

    private fun bindAutofill(status: AutofillStatus) {
        val resId = when (status) {
            AutofillStatus.Enabled -> R.string.settings_autofill_badge_enabled
            AutofillStatus.NotEnabled -> R.string.settings_autofill_badge_not_enabled
        }
        val text = getString(resId)
        binding.textAutofillStatus.text = text
        binding.textAutofillStatus.contentDescription =
            getString(R.string.settings_autofill_badge_a11y, text)
    }

    private fun bindLockStatus(status: DeviceLockStatus) {
        val resId = when (status) {
            DeviceLockStatus.BiometricAndDeviceCredential ->
                R.string.settings_lock_status_biometric_and_device
            DeviceLockStatus.DeviceCredentialOnly ->
                R.string.settings_lock_status_device_only
            DeviceLockStatus.NoLock ->
                R.string.settings_lock_status_none
            DeviceLockStatus.UpdateRequired ->
                R.string.settings_lock_status_update_required
        }
        binding.textLockStatus.setText(resId)
    }

    private fun bindVault(
        metadata: com.example.keynest.domain.model.VaultMetadata,
        storageBytes: Long,
    ) {
        binding.textVaultCount.text =
            getString(R.string.settings_vault_count_format, metadata.count)
        // Req 4.3: surface a placeholder when there are no rows -- the
        // DAO's MAX(updated_at) returns null which we route to the
        // "—" string instead of a 1970-01-01 epoch formatted timestamp.
        binding.textVaultLatestUpdated.text = metadata.latestUpdatedAt
            ?.let { AdvancedDetailsFormatter.formatTimestamp(it) }
            ?: getString(R.string.settings_vault_latest_updated_empty)
        // Req 4.4: human-readable byte size (e.g. "48 kB") via Android
        // Formatter. The exact output depends on the platform locale.
        binding.textVaultStorage.text = Formatter.formatShortFileSize(this, storageBytes)
    }

    private fun bindAbout(state: SettingsUiState) {
        binding.textAppVersion.text = getString(
            R.string.settings_about_version_format,
            state.appInfo.versionName,
            state.appInfo.versionCode,
        )
    }

    private fun showIntentUnavailableSnackbar() {
        Snackbar.make(
            binding.root,
            R.string.settings_intent_unavailable,
            Snackbar.LENGTH_LONG,
        ).show()
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, SettingsActivity::class.java)
    }
}
