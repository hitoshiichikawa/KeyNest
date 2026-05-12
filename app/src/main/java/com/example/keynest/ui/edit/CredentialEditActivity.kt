package com.example.keynest.ui.edit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.keynest.R
import com.example.keynest.databinding.CredentialEditActivityBinding
import com.example.keynest.di.ServiceLocator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Add / edit credential screen.
 *
 * Requirements: 1.1, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3
 *
 * Password handling:
 * - The EditText holds the value in the framework's Editable (a CharSequence
 *   the framework keeps for IME state). Before submission we copy that
 *   value into a CharArray, hand ownership to the use case, and then clear
 *   the Editable's backing array to minimise the lifetime of the plaintext
 *   in the heap.
 */
class CredentialEditActivity : AppCompatActivity() {

    private lateinit var binding: CredentialEditActivityBinding
    private var editingId: Long? = null
    private var signatureResolveJob: Job? = null

    private val viewModel: CredentialEditViewModel by viewModels {
        CredentialEditViewModel.Factory(
            ServiceLocator.credentialRepository,
            ServiceLocator.saveCredentialUseCase,
            ServiceLocator.updateCredentialUseCase,
            ServiceLocator.deleteCredentialUseCase,
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

        // Issue #5 Round 2 / AC 4.4.6: surface the destructive delete
        // button only in edit mode. The visibility is driven here (not
        // in XML) so the button stays hidden when the activity is
        // launched in "new" mode via newIntent(this).
        binding.btnDelete.visibility = if (editingId != null) View.VISIBLE else View.GONE
        binding.btnDelete.setOnClickListener { onDeleteClicked() }

        // Issue #5 / PR #7: mirror live input into the target app card so the
        // preview elements (AC 4.4.2 — IconTile letter, display name,
        // monospace package, signature chip) actually reflect the record
        // being edited instead of staying as static placeholders.
        binding.inputLabel.addTextChangedListener(afterTextChanged { renderTargetAppCard() })
        binding.inputPackage.addTextChangedListener(afterTextChanged {
            renderTargetAppCard()
            scheduleSignatureChipRefresh()
        })

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
        // Seed an initial empty-state render for the new-credential path
        // (the watchers above only fire on subsequent text changes).
        renderTargetAppCard()

        binding.btnSave.setOnClickListener { onSaveClicked() }
        binding.btnPickInstalledApp.setOnClickListener {
            PackagePickerBottomSheet.show(supportFragmentManager) { picked ->
                binding.inputPackage.setText(picked)
            }
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

    /**
     * Issue #5 / AC 4.4.6 — confirm + delete the credential currently
     * being edited. Routes through the same DeleteCredentialUseCase
     * the list screen's long-press flow uses (no new use case, no
     * repository / DAO changes). Activity finishes via the navigation
     * SharedFlow on success.
     */
    private fun onDeleteClicked() {
        val id = editingId ?: return
        AlertDialog.Builder(this)
            .setMessage(R.string.action_delete_credential_destructive)
            .setPositiveButton(R.string.action_delete_credential) { _, _ -> viewModel.delete(id) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

    private fun renderTargetAppCard() {
        val label = binding.inputLabel.text?.toString()?.trim().orEmpty()
        val pkg = binding.inputPackage.text?.toString()?.trim().orEmpty()
        binding.targetAppName.text = when {
            label.isNotEmpty() -> label
            pkg.isNotEmpty() -> pkg
            else -> getString(R.string.label_target_app)
        }
        binding.targetPackageName.text = pkg
        val seed = if (label.isNotEmpty()) label else pkg
        binding.targetIconLetter.text = seed
            .firstOrNull { !it.isWhitespace() }
            ?.uppercaseChar()
            ?.toString()
            ?: ""
    }

    /**
     * AC 4.4.2 — the "署名取得済み" chip should appear once the entered
     * package resolves to an installed app whose signing certificate can
     * be read. We debounce keystrokes so the resolver doesn't run on
     * every character and bounce visibility.
     */
    private fun scheduleSignatureChipRefresh() {
        signatureResolveJob?.cancel()
        val pkg = binding.inputPackage.text?.toString()?.trim().orEmpty()
        if (pkg.isEmpty()) {
            binding.targetSignatureChip.visibility = View.GONE
            return
        }
        signatureResolveJob = lifecycleScope.launch {
            delay(SIGNATURE_RESOLVE_DEBOUNCE_MS)
            val resolved = withContext(Dispatchers.IO) {
                ServiceLocator.packageSignatureResolver.resolveSha256(pkg)
            }
            binding.targetSignatureChip.visibility =
                if (resolved != null) View.VISIBLE else View.GONE
        }
    }

    private fun afterTextChanged(block: () -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = block()
    }

    companion object {
        private const val EXTRA_CREDENTIAL_ID = "com.example.keynest.extra.CREDENTIAL_ID"
        private const val INVALID_ID = -1L
        private const val SIGNATURE_RESOLVE_DEBOUNCE_MS = 250L

        fun newIntent(context: Context, credentialId: Long? = null): Intent {
            return Intent(context, CredentialEditActivity::class.java).apply {
                if (credentialId != null) putExtra(EXTRA_CREDENTIAL_ID, credentialId)
            }
        }
    }
}
