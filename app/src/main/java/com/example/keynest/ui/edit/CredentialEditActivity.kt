package com.example.keynest.ui.edit

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.keynest.R
import com.example.keynest.databinding.CredentialEditActivityBinding
import com.example.keynest.di.ServiceLocator
import com.example.keynest.util.AdvancedDetailsFormatter
import com.example.keynest.util.SafeLogger
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Add / edit credential screen.
 *
 * Requirements: 1.1, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3 (existing MVP) plus
 * Issue #14: 1.x (collapsible section UI), 2.x (createdAt / updatedAt),
 * 3.x (SHA-256 full hex + clipboard copy), 4.x (signature captured at),
 * 5.x (credential ID toggle), 6.x (existing behaviour preserved),
 * NFR 1.1-1.3 (no full hex / ID leakage in logs).
 *
 * Password handling:
 * - The EditText holds the value in the framework's Editable (a CharSequence
 *   the framework keeps for IME state). Before submission we copy that
 *   value into a CharArray, hand ownership to the use case, and then clear
 *   the Editable's backing array to minimise the lifetime of the plaintext
 *   in the heap.
 *
 * Advanced section behaviour:
 * - Always collapsed on screen entry. The expand / collapse + "Show ID"
 *   toggles live on the ViewModel so configuration changes (rotation)
 *   do not reset them; the Activity-scoped ViewModel is destroyed on
 *   finish(), satisfying Req 1.5 (state cleared on screen exit).
 * - The full 64-char SHA-256 hex is never written to logcat / SafeLogger
 *   even at DEBUG; only [SafeLogger.previewHex] (first 8 chars) is logged.
 */
class CredentialEditActivity : AppCompatActivity() {

    private lateinit var binding: CredentialEditActivityBinding
    private var editingId: Long? = null

    private val viewModel: CredentialEditViewModel by viewModels {
        CredentialEditViewModel.Factory(
            ServiceLocator.credentialRepository,
            ServiceLocator.saveCredentialUseCase,
            ServiceLocator.updateCredentialUseCase,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CredentialEditActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        editingId = intent.getLongExtra(EXTRA_CREDENTIAL_ID, INVALID_ID).takeIf { it != INVALID_ID }
        title = getString(if (editingId == null) R.string.credential_edit_title_new else R.string.credential_edit_title_edit)

        editingId?.let { id ->
            lifecycleScope.launch {
                val rec = viewModel.load(id) ?: return@launch
                binding.inputPackage.setText(rec.packageName)
                binding.inputUsername.setText(rec.username)
                binding.inputLabel.setText(rec.label)
                // password intentionally left blank in edit mode - if the
                // user wants to change it they type a new one.
                binding.layoutPassword.hint = getString(R.string.label_password) + " (optional)"
            }
        }

        binding.btnSave.setOnClickListener { onSaveClicked() }
        binding.btnPickInstalledApp.setOnClickListener {
            PackagePickerBottomSheet.show(supportFragmentManager) { picked ->
                binding.inputPackage.setText(picked)
            }
        }

        binding.advancedHeader.setOnClickListener { viewModel.toggleAdvancedExpanded() }
        binding.toggleCredentialId.setOnClickListener { viewModel.toggleCredentialIdVisible() }
        binding.btnCopySignatureHex.setOnClickListener { copySignatureHexToClipboard() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::renderState)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigation.collect { finish() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.advancedDetails.collect(::renderAdvancedDetails)
            }
        }
    }

    private fun onSaveClicked() {
        clearErrors()
        val pkg = binding.inputPackage.text?.toString().orEmpty()
        val username = binding.inputUsername.text?.toString().orEmpty()
        val label = binding.inputLabel.text?.toString().orEmpty()
        val passwordChars = takePasswordCharArray()
        viewModel.save(
            existingId = editingId,
            packageName = pkg,
            username = username,
            password = passwordChars,
            label = label,
        )
    }

    private fun takePasswordCharArray(): CharArray {
        val editable: Editable = binding.inputPassword.text ?: return charArrayOf()
        val out = CharArray(editable.length)
        editable.getChars(0, editable.length, out, 0)
        // Wipe the EditText buffer immediately. The Editable replacement
        // resets internal state and zeros the previous span buffer (best
        // effort - the heap may still contain copies due to IME caching).
        editable.replace(0, editable.length, "")
        return out
    }

    private fun renderState(state: CredentialEditViewModel.State) {
        when (state) {
            is CredentialEditViewModel.State.FieldError -> showFieldError(state.field, state.kind)
            is CredentialEditViewModel.State.Error -> Snackbar.make(
                binding.root,
                getString(R.string.error_save_failed_with_reason, state.cause),
                Snackbar.LENGTH_LONG,
            ).show()
            // Toast is used (not Snackbar) so that the confirmation survives the
            // activity finish() triggered by the navigation collector.
            CredentialEditViewModel.State.Saved ->
                Toast.makeText(this, R.string.message_credential_saved, Toast.LENGTH_SHORT).show()
            CredentialEditViewModel.State.Saving,
            CredentialEditViewModel.State.Idle,
            -> Unit
        }
    }

    private fun showFieldError(field: CredentialEditViewModel.Field, kind: CredentialEditViewModel.ErrorKind) {
        when (field) {
            CredentialEditViewModel.Field.PackageName -> binding.layoutPackage.error = getString(
                if (kind == CredentialEditViewModel.ErrorKind.Blank) R.string.error_package_name_blank
                else R.string.error_package_name_invalid,
            )
            CredentialEditViewModel.Field.Username -> binding.layoutUsername.error = getString(R.string.error_username_blank)
            CredentialEditViewModel.Field.Password -> binding.layoutPassword.error = getString(R.string.error_password_blank)
            CredentialEditViewModel.Field.Label -> binding.layoutLabel.error = getString(R.string.error_label_blank)
        }
    }

    private fun clearErrors() {
        binding.layoutPackage.error = null
        binding.layoutUsername.error = null
        binding.layoutPassword.error = null
        binding.layoutLabel.error = null
    }

    /**
     * Mirror [CredentialEditViewModel.AdvancedDetails] into the read-only
     * advanced-details panel.
     *
     * Responsibilities (kept small to stay under the 40-line guideline):
     * - flip the content visibility + chevron rotation based on .expanded
     * - render each row (timestamps / hex / id) via small helpers
     */
    private fun renderAdvancedDetails(details: CredentialEditViewModel.AdvancedDetails) {
        binding.advancedContent.visibility = if (details.expanded) View.VISIBLE else View.GONE
        binding.advancedChevron.rotation = chevronRotationFor(details.expanded)

        renderTimestampRow(binding.valueCreatedAt, details.createdAt)
        renderTimestampRow(binding.valueUpdatedAt, details.updatedAt)
        renderSignatureRow(details.signatureSha256Hex)
        renderTimestampRow(binding.valueSignatureCapturedAt, details.signatureCapturedAt, useNotCapturedPlaceholder = true)
        renderCredentialIdRow(details)
    }

    private fun renderTimestampRow(
        target: android.widget.TextView,
        epochMillis: Long?,
        useNotCapturedPlaceholder: Boolean = false,
    ) {
        target.text = when {
            epochMillis != null -> AdvancedDetailsFormatter.formatTimestamp(epochMillis)
            useNotCapturedPlaceholder -> getString(R.string.advanced_value_not_captured)
            else -> getString(R.string.advanced_value_unsaved)
        }
    }

    private fun renderSignatureRow(hex: String?) {
        if (hex != null) {
            binding.valueSignatureHex.text = hex
            binding.btnCopySignatureHex.isEnabled = true
            binding.btnCopySignatureHex.visibility = View.VISIBLE
        } else {
            binding.valueSignatureHex.text = getString(R.string.advanced_value_not_captured)
            binding.btnCopySignatureHex.isEnabled = false
            binding.btnCopySignatureHex.visibility = View.GONE
        }
    }

    private fun renderCredentialIdRow(details: CredentialEditViewModel.AdvancedDetails) {
        val id = details.credentialId
        if (id == null) {
            // New mode: hide the entire row + toggle (Req 5.5)
            binding.rowCredentialId.visibility = View.GONE
            binding.valueCredentialId.text = ""
            binding.toggleCredentialId.isChecked = false
            return
        }
        binding.rowCredentialId.visibility = View.VISIBLE
        binding.toggleCredentialId.isChecked = details.credentialIdVisible
        // Only materialise the numeric ID into a String when the toggle is
        // on; the placeholder character has no informational content (NFR 1.3).
        binding.valueCredentialId.text = if (details.credentialIdVisible) {
            id.toString()
        } else {
            getString(R.string.advanced_value_id_hidden)
        }
    }

    private fun copySignatureHexToClipboard() {
        val hex = viewModel.advancedDetails.value.signatureSha256Hex
        if (hex == null) {
            // Defensive: button should be disabled in this state, but a
            // race could let a click through. Bail out silently.
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // The label is user-visible (e.g. in clipboard manager UIs), the
        // payload is the full 64-char hex. We must NEVER pass the full hex
        // to SafeLogger -- only the 8-char preview.
        clipboard.setPrimaryClip(
            SignatureClipboardPayload.build(
                label = getString(R.string.clipboard_label_signature_hex),
                hex = hex,
            ),
        )
        SafeLogger.info(
            tag = TAG,
            message = "signature hex copied (preview=${SafeLogger.previewHex(hex)})",
        )
        Toast.makeText(this, R.string.message_signature_hex_copied, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val EXTRA_CREDENTIAL_ID = "com.example.keynest.extra.CREDENTIAL_ID"
        private const val INVALID_ID = -1L
        private const val TAG = "KeyNest.Edit"
        private const val CHEVRON_COLLAPSED_DEG = 0f
        private const val CHEVRON_EXPANDED_DEG = 180f

        /**
         * Pure helper: returns the chevron rotation (in degrees) for the
         * given expanded state. Extracted so the rotation policy can be
         * unit-tested without spinning up the whole Activity.
         *
         * Backs Req 1.4 (chevron flips to indicate expanded state).
         */
        @JvmStatic
        internal fun chevronRotationFor(expanded: Boolean): Float =
            if (expanded) CHEVRON_EXPANDED_DEG else CHEVRON_COLLAPSED_DEG

        fun newIntent(context: Context, credentialId: Long? = null): Intent {
            return Intent(context, CredentialEditActivity::class.java).apply {
                if (credentialId != null) putExtra(EXTRA_CREDENTIAL_ID, credentialId)
            }
        }
    }
}
