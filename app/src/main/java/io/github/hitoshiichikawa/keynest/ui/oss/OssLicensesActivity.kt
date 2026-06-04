package io.github.hitoshiichikawa.keynest.ui.oss

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.hitoshiichikawa.keynest.R
import io.github.hitoshiichikawa.keynest.databinding.OssLicensesActivityBinding
import io.github.hitoshiichikawa.keynest.util.SafeLogger
import io.github.hitoshiichikawa.keynest.util.applySystemBarsPadding
import io.github.hitoshiichikawa.keynest.util.enableEdgeToEdgeWithKnDefaults
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Open-source license list. Issue #10 Req 5.2, 5.3, 5.4.
 *
 * The activity loads `app/src/main/assets/oss_licenses.json` on
 * Dispatchers.IO and renders the entries via [OssLicensesAdapter].
 * Tapping a row toggles the full text. Tapping the URL button
 * launches `ACTION_VIEW`, falling back to a Snackbar if the device
 * has no browser (rare on consumer phones, common on MDM-locked
 * enterprise builds; Req 5.4 spirit).
 *
 * If the JSON parse fails (corrupted asset, malformed schema) the
 * Activity surfaces a Snackbar and finishes -- the Settings screen
 * underneath stays intact, satisfying Req 5.4 directly.
 *
 * Privacy: assets are bundled at build time, so the read is fully
 * local. The `ACTION_VIEW` dispatch is the only off-device exit
 * point and is deliberately handed off to the system browser
 * (NFR 1.1 -- KeyNest itself does no network IO).
 */
class OssLicensesActivity : AppCompatActivity() {

    private lateinit var binding: OssLicensesActivityBinding
    private lateinit var adapter: OssLicensesAdapter

    private var entries: List<OssEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeWithKnDefaults()
        binding = OssLicensesActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = OssLicensesAdapter(
            onToggle = ::onToggle,
            onUrlClick = ::onUrlClick,
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        loadEntries()
    }

    private fun loadEntries() {
        lifecycleScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val json = assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
                    OssLicensesParser.parse(json)
                }
            }
            outcome.fold(
                onSuccess = { parsed ->
                    entries = parsed
                    adapter.submitList(entries)
                },
                onFailure = { t ->
                    // NFR 1.2: log only the class name, not the full
                    // message (which may include byte offsets etc.).
                    SafeLogger.warn(
                        tag = TAG,
                        message = "OSS license JSON parse failed reason=${t.javaClass.simpleName}",
                    )
                    Snackbar.make(
                        binding.root,
                        R.string.oss_licenses_load_failed,
                        Snackbar.LENGTH_LONG,
                    ).show()
                    // Finish so the user falls back to Settings -- Req 5.4.
                    binding.root.postDelayed({ finish() }, FINISH_DELAY_MS)
                },
            )
        }
    }

    private fun onToggle(entry: OssEntry) {
        entries = entries.map { e ->
            if (e.name == entry.name) e.copy(isExpanded = !e.isExpanded) else e
        }
        adapter.submitList(entries)
    }

    private fun onUrlClick(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(
                binding.root,
                R.string.settings_intent_unavailable,
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    companion object {
        private const val TAG = "KeyNest.OssLic"
        private const val ASSET_NAME = "oss_licenses.json"
        private const val FINISH_DELAY_MS: Long = 1_200L

        fun newIntent(context: Context): Intent =
            Intent(context, OssLicensesActivity::class.java)
    }
}
