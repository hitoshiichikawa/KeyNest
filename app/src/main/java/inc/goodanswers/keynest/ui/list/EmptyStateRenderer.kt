package inc.goodanswers.keynest.ui.list

import android.view.View
import inc.goodanswers.keynest.R
import inc.goodanswers.keynest.databinding.CredentialListActivityBinding

/**
 * Applies the empty-state visibility / headline-text matrix to a
 * [CredentialListActivityBinding].
 *
 * Issue #29 Req 8.1 / 8.3 / 8.5: the empty state is composed of five
 * elements (hero / headline / body copy / CTA / footer). The matrix is:
 *
 *  - [EmptyKind.Initial]: container VISIBLE, all five elements VISIBLE,
 *    headline = `credential_list_empty`.
 *  - [EmptyKind.NoMatch]:  container VISIBLE, hero / body / CTA / footer
 *    GONE, headline VISIBLE with text = `credential_list_empty_no_match`.
 *  - `null`: container GONE (the main list is non-empty).
 *
 * Extracted as a top-level function so the visibility transitions can be
 * unit-tested with a directly-inflated binding (Robolectric) without
 * having to launch the full [CredentialListActivity] (which would also
 * touch [inc.goodanswers.keynest.di.ServiceLocator] and Room).
 *
 * @param binding the inflated activity binding holding the empty-state
 *                views (`empty_state_container` / `empty_state_hero` /
 *                `empty_view` / `empty_state_body` / `empty_state_cta` /
 *                `empty_state_footer`).
 * @param emptyKind the current empty-state kind, or `null` if the main
 *                  list is non-empty.
 */
internal fun applyEmptyStateVisibility(
    binding: CredentialListActivityBinding,
    emptyKind: EmptyKind?,
) {
    val container = binding.emptyStateContainer
    val hero = binding.emptyStateHero
    val body = binding.emptyStateBody
    val cta = binding.emptyStateCta
    val footer = binding.emptyStateFooter
    val headline = binding.emptyView

    when (emptyKind) {
        EmptyKind.Initial -> {
            container.visibility = View.VISIBLE
            hero.visibility = View.VISIBLE
            headline.visibility = View.VISIBLE
            body.visibility = View.VISIBLE
            cta.visibility = View.VISIBLE
            footer.visibility = View.VISIBLE
            headline.setText(R.string.credential_list_empty)
        }
        EmptyKind.NoMatch -> {
            // Req 8.5: NoMatch shows only the headline message; hide the
            // hero / body copy / CTA / footer.
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
