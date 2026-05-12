package com.example.keynest.ui.list

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.keynest.R
import com.example.keynest.databinding.CredentialListActivityBinding
import com.example.keynest.di.ServiceLocator
import com.example.keynest.domain.model.Credential
import com.example.keynest.ui.edit.CredentialEditActivity
import com.example.keynest.ui.enable.AutofillEnableActivity
import com.example.keynest.util.AutofillServiceStatus
import com.example.keynest.util.SafeLogger
import kotlinx.coroutines.launch

/**
 * Credential list screen and app launcher.
 *
 * Requirements: 1.5, 6.1, 6.3
 *
 * Responsibilities:
 * - Render the live credential list via [CredentialListViewModel].
 * - Provide a FAB to launch the new-credential editor (Req 1.1 entry point).
 * - Long-press a row to delete (Req 1.5).
 * - On first launch (when Autofill service is not enabled), redirect to
 *   [AutofillEnableActivity] (Req 6.1 / 6.3 / T9.5).
 */
class CredentialListActivity : AppCompatActivity() {

    private lateinit var binding: CredentialListActivityBinding
    private lateinit var adapter: CredentialListAdapter

    private val viewModel: CredentialListViewModel by viewModels {
        CredentialListViewModel.Factory(
            ServiceLocator.listCredentialsUseCase,
            ServiceLocator.deleteCredentialUseCase,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CredentialListActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = CredentialListAdapter(
            onItemClick = { startEdit(it) },
            onItemLongClick = { promptDelete(it) },
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.fabAdd.setOnClickListener {
            startActivity(CredentialEditActivity.newIntent(this))
        }
        // Empty-state CTA shares the same entry point as the FAB. Issue
        // #5 / AC 4.3.3 surfaces a primary button on the brand-mark hero
        // when the vault is empty; we route both controls through the
        // FAB click for behavioural parity.
        binding.btnEmptyCta.setOnClickListener { binding.fabAdd.performClick() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.credentials.collect { list ->
                    SafeLogger.info(tag = TAG, message = "credential list emit size=${list.size}")
                    adapter.submitList(list)
                    val empty = list.isEmpty()
                    binding.emptyView.visibility = if (empty) android.view.View.VISIBLE else android.view.View.GONE
                    // The empty state shows its own primary CTA, so hide
                    // the FAB to avoid two competing entry points (the
                    // FAB stays in the populated state). Both controls
                    // share `binding.fabAdd.performClick()` so the user
                    // ends up in CredentialEditActivity either way.
                    binding.fabAdd.visibility = if (empty) android.view.View.GONE else android.view.View.VISIBLE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Req 6.1 / 6.3 / T9.5: if KeyNest is not the active autofill service,
        // surface the enable screen exactly once (the redirect happens via a
        // savedInstanceState flag so the user can navigate freely afterwards).
        if (!hasEnabledAutofillService() && !redirectShown) {
            redirectShown = true
            startActivity(AutofillEnableActivity.newIntent(this))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.credential_list_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_open_autofill_settings -> {
                startActivity(AutofillEnableActivity.newIntent(this))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startEdit(credential: Credential) {
        startActivity(CredentialEditActivity.newIntent(this, credentialId = credential.id.value))
    }

    private fun promptDelete(credential: Credential) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.action_delete_credential) + ": " + credential.label)
            .setPositiveButton(R.string.action_delete_credential) { _, _ -> viewModel.delete(credential.id) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun hasEnabledAutofillService(): Boolean = AutofillServiceStatus.isCurrentService(this)

    companion object {
        // In-process flag; survives configuration changes but resets on
        // cold process start (which is the appropriate scope for the
        // first-launch nudge).
        private var redirectShown: Boolean = false

        private const val TAG = "KeyNest.List"

        fun newIntent(context: Context): Intent = Intent(context, CredentialListActivity::class.java)
    }
}
