package com.example.keynest.ui.edit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.keynest.R
import com.example.keynest.databinding.CredentialEditActivityBinding
import com.example.keynest.di.ServiceLocator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

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
            is CredentialEditViewModel.State.Error -> Snackbar.make(binding.root, R.string.error_save_failed, Snackbar.LENGTH_LONG).show()
            CredentialEditViewModel.State.Saving,
            CredentialEditViewModel.State.Saved,
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

    companion object {
        private const val EXTRA_CREDENTIAL_ID = "com.example.keynest.extra.CREDENTIAL_ID"
        private const val INVALID_ID = -1L

        fun newIntent(context: Context, credentialId: Long? = null): Intent {
            return Intent(context, CredentialEditActivity::class.java).apply {
                if (credentialId != null) putExtra(EXTRA_CREDENTIAL_ID, credentialId)
            }
        }
    }
}
