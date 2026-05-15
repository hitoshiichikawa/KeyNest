package com.example.keynest.ui.enable

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.keynest.R
import com.example.keynest.databinding.AutofillEnableActivityBinding
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Issue #31: visibility / wiring of the redesigned
 * `autofill_enable_activity.xml`.
 *
 * Backs Req 6.x / Req 7.x by inflating [AutofillEnableActivityBinding]
 * against [com.example.keynest.R.style.Theme_KeyNest] and asserting:
 *   * defaults (no Activity attached): the action group is VISIBLE,
 *     the already-enabled group is GONE.
 *   * pre-existing IDs `btn_enable` / `text_already_enabled` resolve
 *     non-null so the existing view binding wiring keeps compiling
 *     (Req 9.1 boundary).
 *   * new IDs `btn_later` / `group_actions` / `group_already_enabled`
 *     resolve non-null.
 *
 * We deliberately avoid launching the full Activity here so the test
 * does not depend on the AutofillManager / Secure-settings backed
 * `AutofillServiceStatus.isCurrentService()` (Robolectric does not
 * stand up the AutofillManagerService binder). The visibility *toggle*
 * itself is a 4-line if/else against a Boolean: we mirror that logic
 * inline in one test to pin the contract.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AutofillEnableActivityVisibilityTest {

    private lateinit var binding: AutofillEnableActivityBinding
    private lateinit var context: Context

    @Before
    fun setUp() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        context = ContextThemeWrapper(appContext, R.style.Theme_KeyNest)
        val inflater = LayoutInflater.from(context)
        binding = AutofillEnableActivityBinding.inflate(inflater)
    }

    // ---- Default state (matches "not enabled" path; Req 6.x) ---------------

    @Test
    fun defaults_actionGroupVisibleAndAlreadyEnabledHidden() {
        // The layout XML pins:
        //   group_actions  : visibility=default (VISIBLE)
        //   group_already_enabled : visibility="gone"
        // This is the cold-start state that gets immediately overwritten
        // by renderState() in onResume; we pin the XML defaults so a
        // future refactor cannot swap them and accidentally flash the
        // already-enabled chip during cold start.
        assertThat(binding.groupActions.visibility).isEqualTo(View.VISIBLE)
        assertThat(binding.groupAlreadyEnabled.visibility).isEqualTo(View.GONE)
    }

    // ---- Existing view binding IDs (Req 9.1 boundary) ----------------------

    @Test
    fun existingBindingIds_resolveNonNull() {
        // Req 9.1: btn_enable / text_already_enabled must keep their
        // IDs so the existing OnClickListener and textAlreadyEnabled
        // setVisibility wiring (preserved across #31) keeps compiling.
        assertThat(binding.btnEnable).isNotNull()
        assertThat(binding.textAlreadyEnabled).isNotNull()
    }

    @Test
    fun newBindingIds_resolveNonNull() {
        // Req 6.1 / 7.1: the redesigned layout adds 3 new visibility /
        // action IDs. We pin them here so a future XML refactor can't
        // silently drop them.
        assertThat(binding.btnLater).isNotNull()
        assertThat(binding.groupActions).isNotNull()
        assertThat(binding.groupAlreadyEnabled).isNotNull()
    }

    // ---- Default labels (Req 6.6 / 6.7 / 7.2) ------------------------------

    @Test
    fun primaryCta_labelsAutofillEnableActionString() {
        // Req 6.6.
        assertThat(binding.btnEnable.text.toString())
            .isEqualTo(context.getString(R.string.autofill_enable_action))
    }

    @Test
    fun laterButton_labelsAutofillEnableActionLaterString() {
        // Req 6.7.
        assertThat(binding.btnLater.text.toString())
            .isEqualTo(context.getString(R.string.autofill_enable_action_later))
    }

    @Test
    fun alreadyEnabledTextView_labelsAutofillEnableAlreadyEnabledString() {
        // Req 7.2.
        assertThat(binding.textAlreadyEnabled.text.toString())
            .isEqualTo(context.getString(R.string.autofill_enable_already_enabled))
    }

    // ---- Visibility toggle contract (Req 7.1 / 7.5) ------------------------

    @Test
    fun applyEnabledState_swapsTheTwoGroups_enabledTrue() {
        // Pin the contract of the if/else in renderState():
        //   enabled = true  -> actions=GONE, already_enabled=VISIBLE
        applyEnabledState(binding, enabled = true)
        assertThat(binding.groupActions.visibility).isEqualTo(View.GONE)
        assertThat(binding.groupAlreadyEnabled.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun applyEnabledState_swapsTheTwoGroups_enabledFalse() {
        // enabled = false -> actions=VISIBLE, already_enabled=GONE
        applyEnabledState(binding, enabled = false)
        assertThat(binding.groupActions.visibility).isEqualTo(View.VISIBLE)
        assertThat(binding.groupAlreadyEnabled.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun applyEnabledState_swapBackForthIsIdempotent() {
        // Req 7.6: onResume re-evaluates after the user returns from
        // Settings; the same toggle is applied repeatedly. We pin that
        // applying the same value twice does not change anything, and
        // that switching back returns to the original state.
        applyEnabledState(binding, enabled = false)
        val initialActions = binding.groupActions.visibility
        val initialEnabled = binding.groupAlreadyEnabled.visibility

        applyEnabledState(binding, enabled = true)
        applyEnabledState(binding, enabled = true)
        assertThat(binding.groupActions.visibility).isEqualTo(View.GONE)
        assertThat(binding.groupAlreadyEnabled.visibility).isEqualTo(View.VISIBLE)

        applyEnabledState(binding, enabled = false)
        assertThat(binding.groupActions.visibility).isEqualTo(initialActions)
        assertThat(binding.groupAlreadyEnabled.visibility).isEqualTo(initialEnabled)
    }

    /**
     * Inline mirror of [AutofillEnableActivity.renderState] minus the
     * `AutofillServiceStatus.isCurrentService` lookup, so the visibility
     * contract can be unit-tested without standing up the system
     * AutofillManagerService. The body must stay in lockstep with the
     * Activity (Req 7.1 / 7.5).
     */
    private fun applyEnabledState(
        binding: AutofillEnableActivityBinding,
        enabled: Boolean,
    ) {
        binding.groupActions.visibility = if (enabled) View.GONE else View.VISIBLE
        binding.groupAlreadyEnabled.visibility = if (enabled) View.VISIBLE else View.GONE
    }
}
