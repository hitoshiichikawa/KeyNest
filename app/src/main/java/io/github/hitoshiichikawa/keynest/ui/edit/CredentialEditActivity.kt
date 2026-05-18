package io.github.hitoshiichikawa.keynest.ui.edit

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.hitoshiichikawa.keynest.R
import io.github.hitoshiichikawa.keynest.databinding.CredentialEditActivityBinding
import io.github.hitoshiichikawa.keynest.di.ServiceLocator
import io.github.hitoshiichikawa.keynest.util.AdvancedDetailsFormatter
import io.github.hitoshiichikawa.keynest.util.SafeLogger
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

    /**
     * Issue #73 Phase 1.5 guard: setText(initialPassword) must run
     * exactly once when the value first becomes non-null, otherwise a
     * second emission (re-render, configuration change replay) would
     * overwrite the user's in-progress edits with the load-time value.
     */
    private var passwordInitialized: Boolean = false

    private val viewModel: CredentialEditViewModel by viewModels {
        CredentialEditViewModel.Factory(
            ServiceLocator.credentialRepository,
            ServiceLocator.saveCredentialUseCase,
            ServiceLocator.updateCredentialUseCase,
            ServiceLocator.deleteCredentialUseCase,
            ServiceLocator.observeRecentDetectedFieldsUseCase,
            // Issue #73 Phase 1.5: codec / cipher are needed to decrypt
            // the existing customFields and password under the existing
            // unlock session.
            ServiceLocator.encryptedCustomFieldsCodec,
            ServiceLocator.aesGcmCipher,
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

        // Issue #30 Req 8.1 / 8.2: only render the destructive CTA in edit
        // mode. New mode users see no delete affordance.
        binding.btnDelete.visibility = if (editingId == null) View.GONE else View.VISIBLE

        // Issue #30 Req 3.5 / 3.6: target app card label / package preview.
        // In new mode we render the screen title as the placeholder; the
        // package preview remains empty until the user picks one.
        if (editingId == null) {
            binding.tvTargetAppLabel.text = getString(R.string.credential_edit_title_new)
            binding.tvTargetAppPackage.text = ""
        }

        editingId?.let { id ->
            lifecycleScope.launch {
                val rec = viewModel.load(id) ?: return@launch
                binding.inputPackage.setText(rec.packageName)
                binding.inputUsername.setText(rec.username)
                binding.inputLabel.setText(rec.label)
                // Issue #30 Req 3.5 / 3.6: mirror the loaded credential into
                // the target app card display fields.
                binding.tvTargetAppLabel.text = rec.label
                binding.tvTargetAppPackage.text = rec.packageName
                // Issue #30 Req 3.7 / 3.8: signature chip tint follows the
                // loaded record's signatureSha256 nullability.
                renderSignatureChip(hasSignature = rec.signatureSha256 != null)
                // Issue #73 Phase 1.5: configure the password field for
                // Mode.Edit. The actual setText() for the decrypted value
                // happens when EditState.initialPassword first arrives
                // (collector below). Pre-configure layout-side state here
                // so the field is non-focused & masked & without the
                // password_toggle endIcon when the user first sees the
                // screen (Req 4.3 / 4.4 / 5.4, §9 Q3 採用案 A).
                binding.layoutPassword.endIconMode = TextInputLayout.END_ICON_NONE
                // Move keyboard focus away from inputPassword so the
                // soft IME / shoulder-surfing risk does not trigger the
                // focus-gain plaintext path before the user explicitly
                // taps the field (Req 4.4 / §9 Q5).
                binding.inputLabel.requestFocus()
            }
        }

        // Issue #30 Req 6.6: domain has no strength field today; the bar
        // visibility is GONE per StrengthBar's default. Calling
        // setStrength(null) idempotently re-asserts the hidden state.
        binding.strengthBarEdit.setStrength(null)

        binding.btnSave.setOnClickListener { onSaveClicked() }
        binding.btnPickInstalledApp.setOnClickListener {
            PackagePickerBottomSheet.show(supportFragmentManager) { picked ->
                binding.inputPackage.setText(picked)
                // Issue #30 Req 3.6: keep the target-card preview in sync
                // with the (possibly hidden) editable package field.
                binding.tvTargetAppPackage.text = picked
                // Issue #67 Phase 2: refresh suggestion source when the
                // user picks a new package via the bottom sheet.
                viewModel.bindPackageForSuggestions(picked)
            }
        }
        // Issue #67 Phase 2: keep the suggestion source in sync with the
        // (possibly hidden) editable package field. The TextWatcher
        // covers manual entry through the PackagePicker manual-input
        // dialog, restored state across rotations, and any future code
        // path that mutates `input_package`.
        binding.inputPackage.addTextChangedListener(simpleWatcher { text ->
            viewModel.bindPackageForSuggestions(text)
        })

        // Issue #30 Req 8.9: tapping the delete CTA shows a confirmation
        // dialog; only the positive action actually invokes the use case.
        binding.btnDelete.setOnClickListener { showDeleteConfirmation() }

        binding.advancedHeader.setOnClickListener { viewModel.toggleAdvancedExpanded() }
        binding.toggleCredentialId.setOnClickListener { viewModel.toggleCredentialIdVisible() }
        binding.btnCopySignatureHex.setOnClickListener { copySignatureHexToClipboard() }
        // Issue #66 Phase 1 + Issue #67 Phase 2: customField editor wiring.
        // After appending the row, ask the ViewModel to refresh the
        // detected-fields suggestion chip strip so the user can populate
        // the new row with one tap.
        binding.btnAddCustomField.setOnClickListener {
            viewModel.addCustomFieldRow()
            viewModel.onAddCustomFieldClickedForSuggest()
        }

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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.customFields.collect(::renderCustomFields)
            }
        }
        // Issue #67 Phase 2: detected_fields suggestion chip strip.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.suggestion.collect(::renderDetectedFieldSuggestions)
            }
        }
        // Issue #73 Phase 1.5: Mode.Edit password initial value +
        // masking + focus toggle. Only triggers in Mode.Edit
        // (editingId != null); Mode.New keeps the layout default
        // password_toggle endIcon (NFR 5.2 / §9 Q3 採用案 A).
        if (editingId != null) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.editState.collect(::renderEditStatePassword)
                }
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
            // Issue #30 Req 8.12: delete failure surfaces as a Snackbar and
            // the screen is intentionally NOT closed.
            CredentialEditViewModel.State.DeleteFailed ->
                Snackbar.make(
                    binding.root,
                    R.string.credential_edit_delete_failed,
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

    /**
     * Issue #30 Req 3.7 / 3.8: swap the target-card signature chip
     * background drawable + icon tint + label text between the success
     * (kn_success_soft / kn_success / "署名一致") and the warning
     * (kn_warning_soft / kn_warning / "署名なし") presentations.
     */
    private fun renderSignatureChip(hasSignature: Boolean) {
        if (hasSignature) {
            binding.chipSignatureEdit.setBackgroundResource(R.drawable.kn_signature_chip_bg_success)
            binding.iconSignatureEdit.setColorFilter(
                ContextCompat.getColor(this, R.color.kn_success),
            )
            binding.textSignatureEdit.setText(R.string.signature_match)
            binding.textSignatureEdit.setTextColor(
                ContextCompat.getColor(this, R.color.kn_success),
            )
        } else {
            binding.chipSignatureEdit.setBackgroundResource(R.drawable.kn_signature_chip_bg_warning)
            binding.iconSignatureEdit.setColorFilter(
                ContextCompat.getColor(this, R.color.kn_warning),
            )
            binding.textSignatureEdit.setText(R.string.signature_missing)
            binding.textSignatureEdit.setTextColor(
                ContextCompat.getColor(this, R.color.kn_warning),
            )
        }
    }

    /**
     * Issue #30 Req 8.9 / 8.10 / 8.11 / 8.12: display a Material confirmation
     * dialog before deleting. Positive button calls the ViewModel which
     * invokes DeleteCredentialUseCase and emits a navigation event; the
     * existing navigation collector calls finish() on success. Negative
     * keeps the dialog state and closes only the dialog.
     */
    private fun showDeleteConfirmation() {
        val id = editingId ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.credential_edit_delete_confirm_title)
            .setMessage(R.string.credential_edit_delete_confirm_message)
            .setPositiveButton(R.string.credential_edit_delete_confirm_positive) { _, _ ->
                viewModel.delete(id)
            }
            .setNegativeButton(R.string.credential_edit_delete_confirm_negative, null)
            .show()
    }

    /**
     * Rebuild the customFields row container from [state]. Issue #66 Phase 1.
     *
     * Implementation deliberately replaces the entire container subtree on
     * every state emission (no view recycling) because the row count is
     * capped at 10 — a few extra `addView` calls per keystroke are
     * negligible and keep the row<->ViewModel binding obvious. Each
     * EditText carries a TextWatcher that forwards the new text to the
     * matching reducer method (updateCustomFieldKey / Value); a tag on the
     * EditText guards against the TextWatcher firing re-entrantly when we
     * call setText() during a re-render.
     *
     * design.md §9.3 暫定: in edit mode (`!state.editable`) the add button
     * is hidden, the read-only explanatory note is shown, and the rows
     * list is empty so the editor renders nothing.
     */
    private fun renderCustomFields(state: CredentialEditViewModel.CustomFieldsState) {
        val container = binding.containerCustomFields
        container.removeAllViews()
        if (state.editable) {
            val inflater = LayoutInflater.from(this)
            for (row in state.rows) {
                val rowView = inflater.inflate(R.layout.view_custom_field_row, container, false)
                val keyEdit = rowView.findViewById<TextInputEditText>(R.id.input_field_key)
                val valueEdit = rowView.findViewById<TextInputEditText>(R.id.input_field_value)
                val removeBtn = rowView.findViewById<View>(R.id.btn_remove_row)
                // Pre-fill before attaching the watcher so the watcher does
                // not bounce the same text back into the ViewModel.
                keyEdit.setText(row.fieldKey)
                valueEdit.setText(row.value)
                keyEdit.addTextChangedListener(simpleWatcher { text ->
                    viewModel.updateCustomFieldKey(row.rowId, text)
                })
                valueEdit.addTextChangedListener(simpleWatcher { text ->
                    viewModel.updateCustomFieldValue(row.rowId, text)
                })
                removeBtn.setOnClickListener { viewModel.removeCustomFieldRow(row.rowId) }
                container.addView(rowView)
            }
        }
        // Add button + read-only note visibility.
        binding.btnAddCustomField.visibility = if (state.editable) View.VISIBLE else View.GONE
        binding.btnAddCustomField.isEnabled = state.canAddMore
        binding.tvCustomFieldsReadonlyNote.visibility =
            if (state.editable) View.GONE else View.VISIBLE
    }

    /**
     * Render the Mode.Edit password initial value + masking + focus
     * toggle. Issue #73 Phase 1.5.
     *
     * Wiring fires only the first time `initialPassword` becomes
     * non-null (guarded by [passwordInitialized]) so subsequent
     * StateFlow emissions (re-renders, configuration changes) do not
     * stomp on the user's edits.
     *
     * Steps:
     *  1. setText() the decrypted value into [binding.inputPassword].
     *  2. Apply [PasswordTransformationMethod] so the value is masked
     *     on first paint (Req 4.3, NFR 5.2).
     *  3. Install a focus listener that swaps the transformation
     *     method on focus gain / loss while preserving cursor
     *     selection (Req 5.1 / 5.2 / 5.3).
     *
     * The listener intentionally does NOT call SafeLogger — masking
     * toggle frequency / timing must not be logged (Req 8.4 / NFR 4.3).
     */
    private fun renderEditStatePassword(state: CredentialEditViewModel.EditState) {
        val initial = state.initialPassword ?: return
        if (passwordInitialized) return
        passwordInitialized = true

        binding.inputPassword.setText(initial)
        binding.inputPassword.transformationMethod = PasswordTransformationMethod.getInstance()

        binding.inputPassword.setOnFocusChangeListener { _, hasFocus ->
            val selStart = binding.inputPassword.selectionStart
            val selEnd = binding.inputPassword.selectionEnd
            binding.inputPassword.transformationMethod =
                if (hasFocus) null else PasswordTransformationMethod.getInstance()
            // setSelection requires a valid range; defensive guard
            // against pre-text states (selection == -1).
            if (selStart >= 0 && selEnd >= 0) {
                binding.inputPassword.setSelection(selStart, selEnd)
            }
        }
    }

    /**
     * Render the detected_fields suggestion chip strip (Issue #67
     * Phase 2). Driven by [CredentialEditViewModel.SuggestionState]:
     *
     * - Hidden (visible=false): label / scroll / empty-message all gone.
     * - Visible + empty list + emptyMessage=true: show the empty
     *   placeholder text instead of an empty chip group (Req 4.3).
     * - Visible + items: rebuild the chip group from scratch — chip
     *   count is capped at 10 by the ViewModel so a teardown-and-rebuild
     *   per emission is cheap.
     *
     * The chip itself is a minimal Material chip; the only data shown
     * is the raw `fieldKey`. We deliberately do NOT expose the
     * `source` enum because (a) it would clutter the UI and (b) the
     * source label is a developer-internal classification users do
     * not benefit from seeing.
     */
    private fun renderDetectedFieldSuggestions(
        state: CredentialEditViewModel.SuggestionState,
    ) {
        if (!state.visible) {
            binding.labelDetectedFields.visibility = View.GONE
            binding.scrollDetectedFieldSuggestions.visibility = View.GONE
            binding.tvDetectedFieldsEmpty.visibility = View.GONE
            binding.chipGroupDetectedFields.removeAllViews()
            return
        }
        binding.labelDetectedFields.visibility = View.VISIBLE
        if (state.items.isEmpty()) {
            // Req 4.3: empty placeholder. The empty text also covers
            // emptyMessage=false (Flow not yet emitted) — both surface
            // as "nothing to show", which is fine.
            binding.scrollDetectedFieldSuggestions.visibility = View.GONE
            binding.chipGroupDetectedFields.removeAllViews()
            binding.tvDetectedFieldsEmpty.visibility =
                if (state.emptyMessage) View.VISIBLE else View.GONE
            return
        }
        binding.tvDetectedFieldsEmpty.visibility = View.GONE
        binding.scrollDetectedFieldSuggestions.visibility = View.VISIBLE
        val chipGroup = binding.chipGroupDetectedFields
        chipGroup.removeAllViews()
        for (item in state.items) {
            val chip = Chip(this).apply {
                text = item.fieldKey
                isClickable = true
                isCheckable = false
                isFocusable = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                setOnClickListener { viewModel.onSuggestionClicked(item) }
            }
            chipGroup.addView(chip)
        }
    }

    /**
     * Minimal [TextWatcher] that forwards `onTextChanged` to [onChanged].
     * Kept inline because the editor rows are short-lived (re-created on
     * every state emission); a class-level subclass would obscure the
     * binding-to-rowId capture.
     */
    private fun simpleWatcher(onChanged: (String) -> Unit): TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onChanged(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
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
        private const val EXTRA_CREDENTIAL_ID = "io.github.hitoshiichikawa.keynest.extra.CREDENTIAL_ID"
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
