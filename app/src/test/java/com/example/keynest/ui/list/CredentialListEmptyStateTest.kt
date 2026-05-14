package com.example.keynest.ui.list

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.keynest.R
import com.example.keynest.databinding.CredentialListActivityBinding
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Behaviour of [applyEmptyStateVisibility] (extracted from
 * [CredentialListActivity.renderEmptyView]). Backs Issue #29 Req 8.1 /
 * 8.3 / 8.5 — verifies the visibility matrix for the three possible
 * empty-state values:
 *
 *  - [EmptyKind.Initial]: container + hero + headline + body + CTA +
 *    footer all VISIBLE, headline text = `credential_list_empty`.
 *  - [EmptyKind.NoMatch]: container + headline VISIBLE, hero / body /
 *    CTA / footer GONE, headline text = `credential_list_empty_no_match`.
 *  - `null`: container GONE (the main list is non-empty so no empty-state
 *    is rendered).
 *
 * The test inflates [CredentialListActivityBinding] against a themed
 * [Context] (Theme.KeyNest) so the views can resolve their styles, then
 * calls [applyEmptyStateVisibility] directly. We deliberately avoid
 * launching the full [CredentialListActivity] because that constructor
 * touches [com.example.keynest.di.ServiceLocator] which lazily opens the
 * Room database.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CredentialListEmptyStateTest {

    private lateinit var binding: CredentialListActivityBinding
    private lateinit var context: Context

    @Before
    fun setUp() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        // Theme.KeyNest (Theme.Material3.DayNight.NoActionBar) is required
        // so MaterialCardView / MaterialButton / TextInputLayout resolve
        // their style attributes. Manifest already declares this theme on
        // the application; the test wraps the context explicitly because
        // we are inflating outside the Activity lifecycle.
        context = ContextThemeWrapper(appContext, R.style.Theme_KeyNest)
        val inflater = LayoutInflater.from(context)
        binding = CredentialListActivityBinding.inflate(inflater)
    }

    // ---- Req 8.1 / 8.3: Initial shows all five required elements -----------

    @Test
    fun applyEmptyStateVisibility_initial_showsHeroHeadlineBodyCtaAndFooter() {
        // Arrange / Act
        applyEmptyStateVisibility(binding, EmptyKind.Initial)

        // Assert (Req 8.1 — five elements visible together).
        assertThat(binding.emptyStateContainer.visibility).isEqualTo(View.VISIBLE)
        assertThat(binding.emptyStateHero.visibility).isEqualTo(View.VISIBLE)
        assertThat(binding.emptyView.visibility).isEqualTo(View.VISIBLE)
        assertThat(binding.emptyStateBody.visibility).isEqualTo(View.VISIBLE)
        assertThat(binding.emptyStateCta.visibility).isEqualTo(View.VISIBLE)
        assertThat(binding.emptyStateFooter.visibility).isEqualTo(View.VISIBLE)
        // Headline text reflects the Initial state.
        assertThat(binding.emptyView.text.toString())
            .isEqualTo(context.getString(R.string.credential_list_empty))
    }

    @Test
    fun applyEmptyStateVisibility_initial_bodyTextResolvesEmptyBodyString() {
        // Req 8.1 / 8.3: the body TextView pins to the new
        // credential_list_empty_body string regardless of empty kind
        // (the resource is set in the layout XML; the function only
        // toggles visibility). Asserting on the resolved string catches
        // a regression where the layout silently swaps the text resource.
        applyEmptyStateVisibility(binding, EmptyKind.Initial)

        assertThat(binding.emptyStateBody.text.toString())
            .isEqualTo(context.getString(R.string.credential_list_empty_body))
    }

    // ---- Req 8.5: NoMatch shows only the headline --------------------------

    @Test
    fun applyEmptyStateVisibility_noMatch_hidesHeroBodyCtaAndFooter() {
        // Arrange / Act
        applyEmptyStateVisibility(binding, EmptyKind.NoMatch)

        // Assert (Req 8.5 — hero / body / CTA / footer are GONE).
        assertThat(binding.emptyStateContainer.visibility).isEqualTo(View.VISIBLE)
        assertThat(binding.emptyView.visibility).isEqualTo(View.VISIBLE)
        assertThat(binding.emptyStateHero.visibility).isEqualTo(View.GONE)
        assertThat(binding.emptyStateBody.visibility).isEqualTo(View.GONE)
        assertThat(binding.emptyStateCta.visibility).isEqualTo(View.GONE)
        assertThat(binding.emptyStateFooter.visibility).isEqualTo(View.GONE)
        // Headline text reflects the NoMatch state.
        assertThat(binding.emptyView.text.toString())
            .isEqualTo(context.getString(R.string.credential_list_empty_no_match))
    }

    // ---- Req 8 boundary: emptyKind = null hides the whole container --------

    @Test
    fun applyEmptyStateVisibility_null_hidesTheEntireContainer() {
        // Arrange: prime the container to VISIBLE so we can verify the
        // null branch actively flips it back to GONE (defensive against
        // a "do nothing on null" regression).
        applyEmptyStateVisibility(binding, EmptyKind.Initial)
        assertThat(binding.emptyStateContainer.visibility).isEqualTo(View.VISIBLE)

        // Act
        applyEmptyStateVisibility(binding, emptyKind = null)

        // Assert: container is GONE; nested view visibilities no longer
        // matter because the parent occupies no space.
        assertThat(binding.emptyStateContainer.visibility).isEqualTo(View.GONE)
    }

    // ---- Transition: Initial → NoMatch flips body / CTA / footer to GONE ---

    @Test
    fun applyEmptyStateVisibility_initialThenNoMatch_flipsBodyAndCtaAndFooterToGone() {
        // Defensive: the Activity rebinds the same view tree across state
        // emissions. Initial → NoMatch must actively hide the elements
        // that were previously visible, not just leave the previous
        // state in place.
        applyEmptyStateVisibility(binding, EmptyKind.Initial)
        assertThat(binding.emptyStateBody.visibility).isEqualTo(View.VISIBLE)
        assertThat(binding.emptyStateCta.visibility).isEqualTo(View.VISIBLE)
        assertThat(binding.emptyStateFooter.visibility).isEqualTo(View.VISIBLE)

        applyEmptyStateVisibility(binding, EmptyKind.NoMatch)

        assertThat(binding.emptyStateHero.visibility).isEqualTo(View.GONE)
        assertThat(binding.emptyStateBody.visibility).isEqualTo(View.GONE)
        assertThat(binding.emptyStateCta.visibility).isEqualTo(View.GONE)
        assertThat(binding.emptyStateFooter.visibility).isEqualTo(View.GONE)
    }
}
