package inc.goodanswers.keynest.ui.danger

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import inc.goodanswers.keynest.R
import inc.goodanswers.keynest.auth.AuthResult
import inc.goodanswers.keynest.auth.BiometricAuthenticator
import inc.goodanswers.keynest.databinding.DangerZoneActivityBinding
import inc.goodanswers.keynest.di.ServiceLocator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Vault clear screen. Issue #10 Req 7.1, 7.2, 7.4, 7.5, 7.6, 7.7,
 * NFR 1.3, 3.3.
 *
 * Three gates protect the destructive [inc.goodanswers.keynest.domain.
 * usecase.ClearVaultUseCase]:
 *
 *   Gate 1 (this Activity itself): physically separates the action
 *     from Settings.
 *   Gate 2 (BiometricPrompt): launches via the existing
 *     [BiometricAuthenticator]. Cancel / failure resets to Idle.
 *   Gate 3 (MaterialAlertDialog): "Delete everything?" confirmation.
 *     Cancel resets to Idle.
 *
 * After [DangerZoneUiState.Cleared] we Snackbar + finish(). The
 * Activity stack then unwinds to the Settings screen (whose `onResume`
 * refreshes uiState, so the count drops to 0 and the storage shrinks)
 * and then to CredentialListActivity (which re-collects its own
 * uiState and renders the empty state, satisfying Req 7.6).
 */
class DangerZoneActivity : AppCompatActivity() {

    private lateinit var binding: DangerZoneActivityBinding

    private val viewModel: DangerZoneViewModel by viewModels {
        DangerZoneViewModel.Factory(ServiceLocator.clearVaultUseCase)
    }

    private val authenticator: BiometricAuthenticator by lazy {
        BiometricAuthenticator(this)
    }

    private var confirmDialogShown: Boolean = false
    private var biometricJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DangerZoneActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnClear.setOnClickListener { viewModel.onClearRequested() }
        binding.btnRetry.setOnClickListener { viewModel.onClearRequested() }

        observeUiState()
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> bind(state) }
            }
        }
    }

    private fun bind(state: DangerZoneUiState) {
        when (state) {
            DangerZoneUiState.Idle -> renderIdle()
            DangerZoneUiState.Authenticating -> renderAuthenticating()
            DangerZoneUiState.Confirming -> renderConfirming()
            DangerZoneUiState.Clearing -> renderClearing()
            DangerZoneUiState.Cleared -> renderCleared()
            is DangerZoneUiState.Failed -> renderFailed()
        }
    }

    private fun renderIdle() {
        binding.btnClear.isEnabled = true
        binding.btnClear.visibility = View.VISIBLE
        binding.btnRetry.visibility = View.GONE
        binding.groupProgress.visibility = View.GONE
        confirmDialogShown = false
    }

    private fun renderAuthenticating() {
        binding.btnClear.isEnabled = false
        binding.btnRetry.visibility = View.GONE
        binding.groupProgress.visibility = View.GONE
        // Re-entrancy: only one outstanding prompt at a time.
        if (biometricJob?.isActive == true) return
        biometricJob = lifecycleScope.launch {
            val result = authenticator.authenticate(
                title = getString(R.string.danger_zone_biometric_title),
                subtitle = getString(R.string.danger_zone_biometric_subtitle),
            )
            when (result) {
                AuthResult.Succeeded -> viewModel.onAuthSucceeded()
                AuthResult.Cancelled,
                is AuthResult.Failed,
                is AuthResult.Unavailable,
                -> viewModel.onAuthCancelled()
            }
        }
    }

    private fun renderConfirming() {
        binding.btnClear.isEnabled = false
        binding.btnRetry.visibility = View.GONE
        binding.groupProgress.visibility = View.GONE
        // Req 7.4: show the confirmation dialog exactly once per
        // Confirming state. The flag is reset whenever we leave the
        // state.
        if (confirmDialogShown) return
        confirmDialogShown = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.danger_zone_confirm_title)
            .setMessage(R.string.danger_zone_confirm_message)
            .setPositiveButton(R.string.danger_zone_confirm_positive) { _, _ ->
                viewModel.onConfirmed()
            }
            .setNegativeButton(R.string.danger_zone_confirm_negative) { _, _ ->
                viewModel.onConfirmCancelled()
            }
            .setOnCancelListener { viewModel.onConfirmCancelled() }
            .show()
    }

    private fun renderClearing() {
        binding.btnClear.isEnabled = false
        binding.btnRetry.visibility = View.GONE
        binding.groupProgress.visibility = View.VISIBLE
    }

    private fun renderCleared() {
        binding.btnClear.isEnabled = false
        binding.groupProgress.visibility = View.GONE
        Snackbar.make(
            binding.root,
            R.string.danger_zone_cleared_message,
            Snackbar.LENGTH_SHORT,
        ).show()
        // Req 7.6: finish the Activity. The stack unwinds back to
        // CredentialListActivity. Use a short post-delay so the
        // Snackbar has a chance to surface before we leave.
        binding.root.postDelayed({ finish() }, FINISH_DELAY_MS)
    }

    private fun renderFailed() {
        binding.btnClear.isEnabled = false
        binding.btnClear.visibility = View.GONE
        binding.groupProgress.visibility = View.GONE
        binding.btnRetry.visibility = View.VISIBLE
        Snackbar.make(
            binding.root,
            R.string.danger_zone_failed_message,
            Snackbar.LENGTH_LONG,
        ).show()
    }

    companion object {
        private const val FINISH_DELAY_MS: Long = 800L
        fun newIntent(context: Context): Intent = Intent(context, DangerZoneActivity::class.java)
    }
}
