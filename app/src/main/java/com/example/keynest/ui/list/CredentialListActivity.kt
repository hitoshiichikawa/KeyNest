package com.example.keynest.ui.list

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
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
import com.example.keynest.domain.model.CredentialSortOrder
import com.example.keynest.ui.edit.CredentialEditActivity
import com.example.keynest.ui.enable.AutofillEnableActivity
import com.example.keynest.ui.settings.SettingsActivity
import com.example.keynest.util.AutofillServiceStatus
import com.example.keynest.util.SafeLogger
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Credential list screen and app launcher.
 *
 * Requirements: 1.5, 6.1, 6.3 (MVP); Issue #9 Req 1.x (search), 2.x
 * (filter), 3.x (recent), 4.x (sort), 5.x (overflow / duplicate), 6.x
 * (no regression).
 *
 * Wires the new XML controls (search EditText / two filter chips / sort
 * button / horizontal recent carousel / per-row overflow ImageButton)
 * to [CredentialListViewModel] and reflects [CredentialListUiState]
 * back into the views.
 *
 * Logging policy: SafeLogger calls in this Activity only carry counts
 * and state-kind labels -- never the raw query string, never a
 * username / label / packageName plaintext value (NFR 1.2).
 */
class CredentialListActivity : AppCompatActivity() {

    private lateinit var binding: CredentialListActivityBinding
    private lateinit var adapter: CredentialListAdapter
    private lateinit var recentAdapter: RecentlyUsedCarouselAdapter

    /**
     * Local state mirror: track whether we already published the chip
     * state from a UiState collect so onCheckedStateChange does not loop.
     */
    private var suppressChipCallback: Boolean = false

    private val viewModel: CredentialListViewModel by viewModels {
        CredentialListViewModel.Factory(
            ServiceLocator.listCredentialsUseCase,
            ServiceLocator.observeRecentlyUsedUseCase,
            ServiceLocator.duplicateCredentialUseCase,
            ServiceLocator.deleteCredentialUseCase,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CredentialListActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setUpMainList()
        setUpRecentCarousel()
        setUpSearch()
        setUpFilters()
        setUpSort()
        setUpFab()
        setUpEmptyStateCta()

        observeUiState()
        observeDuplicateResult()
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
            // Issue #10 Req 1.2: "設定" entry opens the new Settings
            // screen. The previous Autofill-only entry is preserved
            // below so the existing nudge flow keeps working.
            R.id.action_open_settings -> {
                startActivity(SettingsActivity.newIntent(this))
                true
            }
            R.id.action_open_autofill_settings -> {
                startActivity(AutofillEnableActivity.newIntent(this))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ---- view setup --------------------------------------------------------

    private fun setUpMainList() {
        adapter = CredentialListAdapter(
            onItemClick = { startEdit(it) },
            onItemLongClick = { promptDelete(it) },
            onOverflowClick = { credential, anchor -> showRowOverflowMenu(credential, anchor) },
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
    }

    private fun setUpRecentCarousel() {
        recentAdapter = RecentlyUsedCarouselAdapter(onItemClick = { startEdit(it) })
        binding.recentRecycler.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL,
            false,
        )
        binding.recentRecycler.adapter = recentAdapter
    }

    private fun setUpSearch() {
        // Req 1.2: incremental search via addTextChangedListener.
        binding.inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.onQueryChanged(s?.toString().orEmpty())
            }
        })
    }

    private fun setUpFilters() {
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            if (suppressChipCallback) return@setOnCheckedStateChangeListener
            // Req 2.6: ChipGroup's singleSelection means checkedIds is
            // always size <= 1.
            val newFilter: CredentialFilter = when (checkedIds.firstOrNull()) {
                R.id.chip_signature_matched -> CredentialFilter.SignatureMatched
                R.id.chip_signature_missing -> CredentialFilter.SignatureMissing
                else -> CredentialFilter.None
            }
            viewModel.onFilterChanged(newFilter)
        }
    }

    private fun setUpSort() {
        binding.btnSort.setOnClickListener { anchor -> showSortPopupMenu(anchor) }
    }

    private fun setUpFab() {
        binding.fabAdd.setOnClickListener {
            startActivity(CredentialEditActivity.newIntent(this))
        }
    }

    /**
     * Issue #29 Req 8.4: the EmptyKind.Initial CTA opens the same edit
     * activity as the FAB. The two affordances cannot be visible at the
     * same logical moment (vault non-empty → CTA hidden), so this is a
     * convenience entry rather than a duplicate flow.
     */
    private fun setUpEmptyStateCta() {
        binding.emptyStateCta.setOnClickListener {
            startActivity(CredentialEditActivity.newIntent(this))
        }
    }

    // ---- ui state collection -----------------------------------------------

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> renderState(state) }
            }
        }
    }

    private fun observeDuplicateResult() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.duplicateResult.collect { outcome ->
                    val msg = when (outcome) {
                        is CredentialListViewModel.DuplicateOutcome.Success ->
                            R.string.message_duplicate_success
                        is CredentialListViewModel.DuplicateOutcome.Failure -> {
                            // NFR 1.2: reason is class-name only and is not
                            // forwarded into the user-visible Snackbar.
                            SafeLogger.warn(
                                tag = TAG,
                                message = "duplicate failed reason=${outcome.reason}",
                            )
                            R.string.message_duplicate_failed
                        }
                    }
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Renders the full [CredentialListUiState] back into the views.
     *
     * NFR 1.2: only `size=..`, `emptyKind=..` etc. counts / kinds are
     * logged here, never the raw query / username / packageName text.
     */
    private fun renderState(state: CredentialListUiState) {
        SafeLogger.info(
            tag = TAG,
            message = "ui emit size=${state.mainList.size} recent=${state.recentList.size} " +
                "filter=${state.filter.javaClass.simpleName} sort=${state.sort} " +
                "emptyKind=${state.emptyKind}",
        )
        adapter.submitList(state.mainList)
        recentAdapter.submitList(state.recentList)
        renderRecentVisibility(state.recentList.isEmpty())
        renderEmptyView(state)
        syncChipsTo(state.filter)
    }

    private fun renderRecentVisibility(isEmpty: Boolean) {
        // Req 3.4: hide both header and recycler so the empty carousel
        // does not occupy vertical space.
        val vis = if (isEmpty) View.GONE else View.VISIBLE
        binding.recentHeader.visibility = vis
        binding.recentRecycler.visibility = vis
    }

    private fun renderEmptyView(state: CredentialListUiState) {
        // Issue #29 Req 8.x: the empty state is now rendered inside a
        // dedicated container that holds the hero illustration, headline
        // (existing empty_view TextView), supplemental body copy, primary
        // CTA and security note. The whole container is GONE when there
        // is no empty state; the existing empty_view TextView is kept
        // inside as the headline.
        //
        // EmptyKind.Initial → show hero + headline + body + CTA + footer
        //                     (Req 8.1's five elements).
        // EmptyKind.NoMatch → show only the headline TextView (Req 8.5).
        // null              → hide the entire container.
        val container = binding.emptyStateContainer
        val hero = binding.emptyStateHero
        val body = binding.emptyStateBody
        val cta = binding.emptyStateCta
        val footer = binding.emptyStateFooter
        val headline = binding.emptyView

        when (state.emptyKind) {
            EmptyKind.Initial -> {
                container.visibility = View.VISIBLE
                hero.visibility = View.VISIBLE
                headline.visibility = View.VISIBLE
                // Req 8.1 / 8.3: body copy is part of the Initial empty
                // state's required elements.
                body.visibility = View.VISIBLE
                cta.visibility = View.VISIBLE
                footer.visibility = View.VISIBLE
                headline.setText(R.string.credential_list_empty)
            }
            EmptyKind.NoMatch -> {
                // Req 8.5: NoMatch shows only the headline message; hide
                // the hero / body copy / CTA / footer.
                container.visibility = View.VISIBLE
                hero.visibility = View.GONE
                body.visibility = View.GONE
                cta.visibility = View.GONE
                footer.visibility = View.GONE
                headline.visibility = View.VISIBLE
                headline.setText(R.string.credential_list_empty_no_match)
            }
            null -> {
                container.visibility = View.GONE
            }
        }
    }

    /**
     * Mirror the ViewModel's filter into the chip group without triggering
     * `onFilterChanged` again. Used when the chip state needs to follow a
     * programmatic state restore (e.g. configuration change).
     */
    private fun syncChipsTo(filter: CredentialFilter) {
        val targetId: Int = when (filter) {
            CredentialFilter.None -> View.NO_ID
            CredentialFilter.SignatureMatched -> R.id.chip_signature_matched
            CredentialFilter.SignatureMissing -> R.id.chip_signature_missing
        }
        if (binding.chipGroupFilters.checkedChipId == targetId) return
        suppressChipCallback = true
        if (targetId == View.NO_ID) {
            binding.chipGroupFilters.clearCheck()
        } else {
            binding.chipGroupFilters.check(targetId)
        }
        suppressChipCallback = false
    }

    // ---- popup menus -------------------------------------------------------

    private fun showSortPopupMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.credential_list_sort, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            val newOrder = when (item.itemId) {
                R.id.sort_updated_at_desc -> CredentialSortOrder.UpdatedAtDesc
                R.id.sort_label_asc -> CredentialSortOrder.LabelAsc
                R.id.sort_package_asc -> CredentialSortOrder.PackageAsc
                else -> return@setOnMenuItemClickListener false
            }
            viewModel.onSortChanged(newOrder)
            true
        }
        popup.show()
    }

    private fun showRowOverflowMenu(credential: Credential, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.credential_list_row_overflow, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_duplicate -> {
                    viewModel.onDuplicate(credential.id)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // ---- existing flows (no behavioural change) ---------------------------

    private fun startEdit(credential: Credential) {
        // Req 3.5 / 6.2: tapping either a main row or a carousel card opens
        // the same editor.
        startActivity(CredentialEditActivity.newIntent(this, credentialId = credential.id.value))
    }

    private fun promptDelete(credential: Credential) {
        // Req 5.6: long-press to delete is preserved.
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
