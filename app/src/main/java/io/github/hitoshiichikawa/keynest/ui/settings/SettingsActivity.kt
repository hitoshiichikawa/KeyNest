package io.github.hitoshiichikawa.keynest.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.hitoshiichikawa.keynest.R
import io.github.hitoshiichikawa.keynest.databinding.SettingsActivityBinding
import io.github.hitoshiichikawa.keynest.di.ServiceLocator
import io.github.hitoshiichikawa.keynest.domain.model.AutofillStatus
import io.github.hitoshiichikawa.keynest.domain.model.DeviceLockStatus
import io.github.hitoshiichikawa.keynest.ui.danger.DangerZoneActivity
import io.github.hitoshiichikawa.keynest.ui.oss.OssLicensesActivity
import io.github.hitoshiichikawa.keynest.util.AdvancedDetailsFormatter
import io.github.hitoshiichikawa.keynest.util.SystemSettingsIntents
import io.github.hitoshiichikawa.keynest.util.applySystemBarsPadding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Settings screen for KeyNest. Issue #10 Req 1.x..6.x + Issue #33
 * (Phase 2 #5) — JSX `ScreenSettings` (design/screens/screens-2.jsx)
 * 整合.
 *
 * Renders [SettingsViewModel.uiState] into:
 *   - Autofill ステータス hero (Issue #33 Req 2). Background drawable は
 *     [AutofillStatus.Enabled] → `kn_settings_hero_gradient_enabled`
 *     [AutofillStatus.NotEnabled] → `kn_settings_hero_bg_notenabled` を
 *     [bindAutofill] が runtime で差し替える.
 *   - セキュリティ SettingGroup (Req 5) — 「ロック解除方法」行押下で
 *     [SystemSettingsIntents.openSecuritySettings] を発火.
 *   - Vault SettingGroup (Req 6) — 件数 / 最終更新 / DB サイズ の 3 行.
 *   - About SettingGroup (Req 7) — KeyNest version / OSS / Privacy.
 *   - Danger zone SettingGroup (Req 8) — kn_danger_soft 背景 +「すべて削除」
 *     行押下で [DangerZoneActivity] へ.
 *
 * Issue #10 で確立した既存 View ID (`btn_open_autofill_settings` /
 * `btn_open_security_settings` / `btn_oss_licenses` / `btn_open_danger_zone`
 * / `text_autofill_status` / `text_lock_status` / `text_vault_count` /
 * `text_vault_latest_updated` / `text_vault_storage` / `text_app_version` /
 * `toolbar`) は保持しているが、SettingRow 化に伴い `btn_open_security_settings`
 * 等は MaterialButton ではなく LinearLayout に割り当てられている (Req 5.9 /
 * 12.4 の制約はクリック ID 維持に限定されており widget 型は固定していない).
 *
 * onResume calls [SettingsViewModel.refresh] (Issue #10 Req 2.5 / 3.5).
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
        binding.root.applySystemBarsPadding()
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        wireRows()
        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    // ---- wiring ---------------------------------------------------------

    private fun wireRows() {
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

    /**
     * Issue #33 Req 2.4..2.12: hero の背景 / チップ / CTA を [status] に応じて
     * 切り替える. 既存の View ID `text_autofill_status` は Hero の description
     * TextView (Req 2.5 補足文) に割り当てられているため、ステータスチップは
     * `chip_autofill_status` 側で別途バインドする.
     */
    private fun bindAutofill(status: AutofillStatus) {
        val chipLabelResId = when (status) {
            AutofillStatus.Enabled -> R.string.settings_autofill_badge_enabled
            AutofillStatus.NotEnabled -> R.string.settings_autofill_badge_not_enabled
        }
        val chipLabel = getString(chipLabelResId)
        binding.chipAutofillStatus.text = chipLabel
        // NFR 2.5 composite a11y: "Autofill service status: <label>".
        binding.chipAutofillStatus.contentDescription =
            getString(R.string.settings_autofill_badge_a11y, chipLabel)

        when (status) {
            AutofillStatus.Enabled -> applyAutofillHeroEnabled()
            AutofillStatus.NotEnabled -> applyAutofillHeroNotEnabled()
        }
    }

    private fun applyAutofillHeroEnabled() {
        // Req 2.4 / 2.6 / 2.12: gradient + white-on-blue palette.
        binding.groupAutofillHero.background = ContextCompat.getDrawable(
            this, R.drawable.kn_settings_hero_gradient_enabled,
        )
        binding.chipAutofillStatus.background = ContextCompat.getDrawable(
            this, R.drawable.kn_settings_hero_chip_bg_enabled,
        )
        binding.btnOpenAutofillSettings.background = ContextCompat.getDrawable(
            this, R.drawable.kn_settings_hero_cta_bg_enabled,
        )
        val onPrimary = ContextCompat.getColor(this, R.color.kn_on_primary)
        binding.chipAutofillStatus.setTextColor(onPrimary)
        binding.textAutofillHeroTitle.setTextColor(onPrimary)
        binding.textAutofillStatus.setTextColor(onPrimary)
        binding.btnOpenAutofillSettings.setTextColor(onPrimary)
        binding.textAutofillStatus.setText(R.string.settings_autofill_hero_description_enabled)
    }

    private fun applyAutofillHeroNotEnabled() {
        // Req 2.7: kn_warning_soft 背景 / kn_text 本文 / kn_warning accent.
        binding.groupAutofillHero.background = ContextCompat.getDrawable(
            this, R.drawable.kn_settings_hero_bg_notenabled,
        )
        binding.chipAutofillStatus.background = ContextCompat.getDrawable(
            this, R.drawable.kn_settings_hero_chip_bg_notenabled,
        )
        binding.btnOpenAutofillSettings.background = ContextCompat.getDrawable(
            this, R.drawable.kn_settings_hero_cta_bg_notenabled,
        )
        val onSurface = ContextCompat.getColor(this, R.color.kn_text)
        val warning = ContextCompat.getColor(this, R.color.kn_warning)
        binding.chipAutofillStatus.setTextColor(warning)
        binding.textAutofillHeroTitle.setTextColor(onSurface)
        binding.textAutofillStatus.setTextColor(
            ContextCompat.getColor(this, R.color.kn_text_2),
        )
        binding.btnOpenAutofillSettings.setTextColor(warning)
        binding.textAutofillStatus.setText(R.string.settings_autofill_hero_description_not_enabled)
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
        metadata: io.github.hitoshiichikawa.keynest.domain.model.VaultMetadata,
        storageBytes: Long,
    ) {
        binding.textVaultCount.text =
            getString(R.string.settings_vault_count_format, metadata.count)
        binding.textVaultLatestUpdated.text = metadata.latestUpdatedAt
            ?.let { AdvancedDetailsFormatter.formatTimestamp(it) }
            ?: getString(R.string.settings_vault_latest_updated_empty)
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
