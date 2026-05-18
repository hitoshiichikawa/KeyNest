package io.github.hitoshiichikawa.keynest.ui.edit

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.textfield.TextInputEditText
import io.github.hitoshiichikawa.keynest.R

/**
 * Issue #77: idempotent diff renderer for the customField row container.
 *
 * Phase 1 (#66) implemented the renderer as a "full rebuild": every
 * StateFlow emission called `container.removeAllViews()` and re-inflated
 * the whole list. Combined with the per-EditText [TextWatcher], that
 * destroyed the in-progress EditText (and its focus / IME state) on
 * every keystroke, so the user could only enter one character before
 * having to tap the field again. See Issue #77 for the bug write-up.
 *
 * This renderer fixes that by treating the container as a diff target
 * keyed by [CredentialEditViewModel.CustomFieldsState.Row.rowId]:
 *
 *   1. Walk existing children, indexing them by their `rowId` tag
 *      ([R.id.custom_field_row_id]).
 *   2. For each row in [state.rows] (in order):
 *      - If a view already exists for that rowId: reuse it. Do NOT
 *        call `setText()` on the EditTexts unless the ViewModel value
 *        diverged from what the EditText currently holds (rare
 *        normalisation case — see [syncIfDiverged]).
 *      - Otherwise: inflate a fresh row, attach TextWatchers, and add
 *        it to the container.
 *      - Re-bind the remove button click listener so the captured
 *        rowId stays correct (rowId is per-row so this is a no-op
 *        when reusing, but cheap).
 *      - Move the view into the correct child index if it drifted
 *        (e.g. after a middle-row removal).
 *   3. Remove any leftover children whose rowId is no longer in
 *      [state.rows].
 *
 * The renderer also enforces the spec's `state.editable == false`
 * behaviour: in that case the container is emptied (Mode.Edit Phase 1
 * read-only fall-back, preserved for back-compat with the rest of
 * Issue #66 / #73 / #77 spec). With Phase 1.5 (#73) merged this branch
 * is only entered on decrypt failure.
 *
 * TextWatcher re-entrancy: each row's installed watcher is cached on
 * the EditText as a tag ([R.id.custom_field_key_watcher] /
 * [R.id.custom_field_value_watcher]). When the renderer must call
 * setText() to sync a diverged value, it first
 * `removeTextChangedListener` the cached watcher, calls `setText`, then
 * `addTextChangedListener` again. This prevents the watcher from
 * looping the same value back into the ViewModel reducer (which would
 * trigger another emission, etc.).
 *
 * Designed as an `object` with a single render entry point so it can
 * be unit-tested under Robolectric without spinning up the full
 * Activity (the test installs callbacks that record reducer
 * invocations, then asserts focus / view identity is preserved across
 * emissions). See `CustomFieldsRowsRendererTest`.
 */
internal object CustomFieldsRowsRenderer {

    /**
     * Callbacks the renderer invokes when the user interacts with a
     * row. Modelled as a small interface so the Activity can wire
     * them straight into the ViewModel reducers, while tests can
     * substitute a fake recorder.
     *
     * The renderer never calls these synchronously from `render()` —
     * they fire only in response to TextWatcher / click events.
     */
    internal interface Callbacks {
        fun onFieldKeyChanged(rowId: Long, text: String)
        fun onFieldValueChanged(rowId: Long, text: String)
        fun onRemoveRowClicked(rowId: Long)
    }

    /**
     * Apply [state] to [container].
     *
     * - When `state.editable` is false, the container is emptied. The
     *   caller is responsible for the add-button / read-only-note
     *   visibility (the renderer touches only the rows container).
     * - When `state.editable` is true, the container is brought into
     *   alignment with `state.rows` via the diff described in the
     *   class docstring.
     */
    fun render(
        container: ViewGroup,
        inflater: LayoutInflater,
        state: CredentialEditViewModel.CustomFieldsState,
        callbacks: Callbacks,
    ) {
        if (!state.editable) {
            // Mode.Edit read-only fall-back (only reached on decrypt
            // failure now that #73 is merged). Mirror the pre-#77
            // behaviour exactly: empty the container, leave the
            // add-button / note visibility to the caller.
            container.removeAllViews()
            return
        }

        // Step 1: index the current children by rowId, and pre-compute
        // which children should be evicted (rowId no longer in state).
        // Removing the evicted children first means step 2 never has
        // to step over stale views when computing the desired index
        // — and crucially, never has to reorder a focused row past a
        // soon-to-be-deleted neighbour (which would detach + re-attach
        // the focused row and silently lose focus).
        val existing = HashMap<Long, View>(container.childCount)
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val tagged = child.getTag(R.id.custom_field_row_id) as? Long
            if (tagged != null) {
                existing[tagged] = child
            }
        }
        val desiredRowIds = HashSet<Long>(state.rows.size).apply {
            state.rows.forEach { add(it.rowId) }
        }
        // Walk backwards so removeViewAt indices stay stable as we go.
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i)
            val tagged = child.getTag(R.id.custom_field_row_id) as? Long
            if (tagged == null || tagged !in desiredRowIds) {
                container.removeViewAt(i)
                if (tagged != null) existing.remove(tagged)
            }
        }

        // Step 2: walk the desired state in order and place each row
        // at the correct child index. The `desiredIndex` cursor
        // advances exactly once per row in [state.rows]. Because
        // step 1 already evicted views that should disappear, the
        // only structural mutations here are (a) inflate + addView
        // for brand-new rows, and (b) reorder for rows that drifted
        // because of an insert / remove higher up in the list.
        for ((desiredIndex, row) in state.rows.withIndex()) {
            val cached = existing[row.rowId]
            if (cached == null) {
                // No existing view -> inflate fresh + install
                // watchers + tag with rowId.
                val fresh = inflater.inflate(R.layout.view_custom_field_row, container, false)
                fresh.setTag(R.id.custom_field_row_id, row.rowId)
                attachWatchers(fresh, row.rowId, row, callbacks)
                // Insert at the desired position so order matches
                // state.rows.
                container.addView(fresh, desiredIndex)
                // The remove-button listener captures rowId via the
                // lambda parameter, so no per-emission rebind is
                // required for fresh rows.
                fresh.findViewById<View>(R.id.btn_remove_row).setOnClickListener {
                    callbacks.onRemoveRowClicked(row.rowId)
                }
            } else {
                // Existing view: reuse. Re-bind the remove button
                // (cheap; ensures the lambda's captured rowId is
                // fresh even if we ever change Row identity
                // semantics in the future) and sync any diverged
                // value WITHOUT bouncing the TextWatcher.
                cached.findViewById<View>(R.id.btn_remove_row).setOnClickListener {
                    callbacks.onRemoveRowClicked(row.rowId)
                }
                syncIfDiverged(
                    editText = cached.findViewById(R.id.input_field_key),
                    desired = row.fieldKey,
                    watcherTagId = R.id.custom_field_key_watcher,
                )
                syncIfDiverged(
                    editText = cached.findViewById(R.id.input_field_value),
                    desired = row.value,
                    watcherTagId = R.id.custom_field_value_watcher,
                )

                // Reorder if the view drifted from its desired
                // index. addView on an attached child first
                // detaches it, then re-inserts at the requested
                // index — the documented way to reorder. This
                // path is rare because [CredentialEditViewModel]
                // never reorders existing rows in place; it only
                // appends / removes.
                val currentIndex = container.indexOfChild(cached)
                if (currentIndex != desiredIndex) {
                    container.removeViewAt(currentIndex)
                    container.addView(cached, desiredIndex)
                }
            }
        }
    }

    /**
     * Install fresh TextWatchers on the row's two EditTexts and cache
     * them as view tags so we can detach them later for re-entrancy
     * safety. Used only for newly-inflated rows; existing rows keep
     * their already-attached watchers across re-renders (that is the
     * whole point of the diff renderer).
     *
     * The watcher captures `rowId` via the lambda, which is stable
     * for the lifetime of the row view (rowIds are monotonically
     * assigned and never reused — see CredentialEditViewModel.nextRowId).
     */
    private fun attachWatchers(
        rowView: View,
        rowId: Long,
        row: CredentialEditViewModel.CustomFieldsState.Row,
        callbacks: Callbacks,
    ) {
        val keyEdit = rowView.findViewById<TextInputEditText>(R.id.input_field_key)
        val valueEdit = rowView.findViewById<TextInputEditText>(R.id.input_field_value)

        // Pre-fill before attaching the watcher so the watcher does
        // not bounce the inflated initial text back into the
        // ViewModel (would echo "" on add).
        if (row.fieldKey.isNotEmpty()) keyEdit.setText(row.fieldKey)
        if (row.value.isNotEmpty()) valueEdit.setText(row.value)

        val keyWatcher = forwardingWatcher { text -> callbacks.onFieldKeyChanged(rowId, text) }
        val valueWatcher = forwardingWatcher { text -> callbacks.onFieldValueChanged(rowId, text) }
        keyEdit.addTextChangedListener(keyWatcher)
        valueEdit.addTextChangedListener(valueWatcher)
        keyEdit.setTag(R.id.custom_field_key_watcher, keyWatcher)
        valueEdit.setTag(R.id.custom_field_value_watcher, valueWatcher)
    }

    /**
     * Sync [editText] to [desired] iff its current text already
     * differs. Most state emissions originate FROM the EditText (the
     * user typed, the watcher fired, the ViewModel updated, the
     * StateFlow re-emitted), so the values are already in sync and
     * this is the no-op fast path.
     *
     * On the rare divergence (e.g. reducer normalises the input —
     * not currently the case in the codebase but kept defensive
     * against future #73 / #77 follow-ups), we detach the cached
     * watcher, call setText, then re-attach. This prevents the
     * watcher from forwarding the just-synced value back into the
     * ViewModel reducer, which would re-emit the same state, which
     * would re-enter this same sync block — an infinite loop modulo
     * the equality short-circuit.
     */
    private fun syncIfDiverged(
        editText: TextInputEditText,
        desired: String,
        watcherTagId: Int,
    ) {
        val current = editText.text?.toString().orEmpty()
        if (current == desired) return
        val watcher = editText.getTag(watcherTagId) as? TextWatcher
        if (watcher != null) editText.removeTextChangedListener(watcher)
        editText.setText(desired)
        if (watcher != null) editText.addTextChangedListener(watcher)
    }

    private fun forwardingWatcher(onChanged: (String) -> Unit): TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onChanged(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
}
