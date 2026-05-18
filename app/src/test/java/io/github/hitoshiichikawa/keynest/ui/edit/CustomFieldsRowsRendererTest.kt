package io.github.hitoshiichikawa.keynest.ui.edit

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputEditText
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.R
import io.github.hitoshiichikawa.keynest.databinding.CredentialEditActivityBinding
import io.github.hitoshiichikawa.keynest.ui.edit.CredentialEditViewModel.CustomFieldsState
import io.github.hitoshiichikawa.keynest.ui.edit.CredentialEditViewModel.CustomFieldsState.Row
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Issue #77 Req 5.1–5.4: focus / view-identity preservation for the
 * customField diff renderer.
 *
 * The renderer's contract is: existing row views must survive across
 * StateFlow emissions, so the user can type continuously without the
 * EditText being re-created (and therefore without losing focus or
 * the IME). These tests drive [CustomFieldsRowsRenderer.render]
 * directly with a small in-test state machine that mirrors what the
 * real ViewModel does on key / value updates, and assert that:
 *
 *  - the EditText instance the user is "typing into" stays the same
 *    object across emissions (Req 2.1 / 2.2)
 *  - focus is not stolen from that EditText (Req 2.1 / 2.3)
 *  - row identity for unrelated rows survives add / remove operations
 *    (Req 4.1 / 4.2 / 5.3 / 5.4)
 *
 * Test layer: Robolectric SDK 33, mirroring
 * [CredentialEditActivityPasswordFocusTest]. We inflate the real
 * `credential_edit_activity` binding to get a real
 * `containerCustomFields` LinearLayout, then drive the renderer
 * against it. The binding's other widgets are unused.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CustomFieldsRowsRendererTest {

    private lateinit var context: Context
    private lateinit var binding: CredentialEditActivityBinding
    private lateinit var inflater: LayoutInflater
    private lateinit var fakeVm: FakeCustomFieldsHolder
    private lateinit var callbacks: CustomFieldsRowsRenderer.Callbacks
    private lateinit var container: ViewGroup

    @Before
    fun setUp() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        context = ContextThemeWrapper(appContext, R.style.Theme_KeyNest)
        inflater = LayoutInflater.from(context)
        binding = CredentialEditActivityBinding.inflate(inflater)
        container = binding.containerCustomFields
        fakeVm = FakeCustomFieldsHolder()
        callbacks = object : CustomFieldsRowsRenderer.Callbacks {
            override fun onFieldKeyChanged(rowId: Long, text: String) {
                fakeVm.updateKey(rowId, text)
                renderCurrent()
            }
            override fun onFieldValueChanged(rowId: Long, text: String) {
                fakeVm.updateValue(rowId, text)
                renderCurrent()
            }
            override fun onRemoveRowClicked(rowId: Long) {
                fakeVm.removeRow(rowId)
                renderCurrent()
            }
        }
    }

    /**
     * Req 5.1: typing "abcdef" into the fieldKey EditText must leave
     * focus on the same EditText instance. The TextWatcher fires
     * after every keystroke, the fake "ViewModel" updates its row,
     * the renderer is re-invoked, and we assert the EditText was not
     * swapped under us.
     */
    @Test
    fun typingIntoFieldKey_preservesEditTextIdentityAndFocus() {
        fakeVm.addRow()
        renderCurrent()
        val rowView = container.getChildAt(0)
        val keyEdit = rowView.findViewById<TextInputEditText>(R.id.input_field_key)
        keyEdit.requestFocus()
        val originalKeyEdit = keyEdit

        // Simulate "abcdef" by appending one character at a time —
        // the TextWatcher fires per setText() so this exercises the
        // emission-per-keystroke pattern that previously rebuilt the
        // whole container.
        for (ch in "abcdef") {
            keyEdit.append(ch.toString())
        }

        val updatedRowView = container.getChildAt(0)
        val updatedKeyEdit = updatedRowView.findViewById<TextInputEditText>(R.id.input_field_key)
        assertThat(updatedKeyEdit).isSameInstanceAs(originalKeyEdit)
        assertThat(updatedKeyEdit.text?.toString()).isEqualTo("abcdef")
        // Focus must not have been stolen. Robolectric leaves the
        // focused view focused unless the view tree is rebuilt; the
        // diff renderer never removes this row across emissions.
        assertThat(updatedKeyEdit.isFocused).isTrue()
    }

    /**
     * Req 5.2: same as above, but for the `value` EditText.
     */
    @Test
    fun typingIntoFieldValue_preservesEditTextIdentityAndFocus() {
        fakeVm.addRow()
        renderCurrent()
        val rowView = container.getChildAt(0)
        val valueEdit = rowView.findViewById<TextInputEditText>(R.id.input_field_value)
        valueEdit.requestFocus()
        val originalValueEdit = valueEdit

        for (ch in "12345") {
            valueEdit.append(ch.toString())
        }

        val updatedRowView = container.getChildAt(0)
        val updatedValueEdit = updatedRowView.findViewById<TextInputEditText>(R.id.input_field_value)
        assertThat(updatedValueEdit).isSameInstanceAs(originalValueEdit)
        assertThat(updatedValueEdit.text?.toString()).isEqualTo("12345")
        assertThat(updatedValueEdit.isFocused).isTrue()
    }

    /**
     * Req 5.3: with 3 rows present (middle row currently being
     * edited / focused), adding a 4th row must NOT recreate the
     * first three rows.
     */
    @Test
    fun addingRowToThreeRowState_preservesExistingRowsFocusAndIdentity() {
        // Seed 3 rows with distinct fieldKeys so we can verify
        // values survive across the add.
        fakeVm.addRow()
        fakeVm.addRow()
        fakeVm.addRow()
        renderCurrent()
        val rows = fakeVm.snapshot().rows
        val middleRowId = rows[1].rowId
        // Capture the middle row's value-EditText, set a value via
        // direct setText to mark identity, and grab focus on it.
        val originalMiddleValueEdit = findValueEditTextFor(middleRowId)
        originalMiddleValueEdit.setText("in-flight")
        originalMiddleValueEdit.requestFocus()
        val originalMiddleRowId = middleRowId

        fakeVm.addRow()
        renderCurrent()

        // After the add: 4 rows exist, the middle row's EditText is
        // the same Kotlin object as before, its text survives, and
        // focus is still on it.
        assertThat(container.childCount).isEqualTo(4)
        val refoundMiddleValueEdit = findValueEditTextFor(originalMiddleRowId)
        assertThat(refoundMiddleValueEdit).isSameInstanceAs(originalMiddleValueEdit)
        assertThat(refoundMiddleValueEdit.text?.toString()).isEqualTo("in-flight")
        assertThat(refoundMiddleValueEdit.isFocused).isTrue()
    }

    /**
     * Req 5.4: removing the middle row of a 3-row state must NOT
     * recreate the surviving (first / third) rows. The renderer
     * only removes the targeted row; rowId tagging ensures the
     * other two views are reused intact.
     */
    @Test
    fun removingMiddleRow_preservesSurroundingRowsFocusAndIdentity() {
        fakeVm.addRow()
        fakeVm.addRow()
        fakeVm.addRow()
        renderCurrent()
        val rows = fakeVm.snapshot().rows
        val firstRowId = rows[0].rowId
        val middleRowId = rows[1].rowId
        val thirdRowId = rows[2].rowId
        val firstKeyEdit = findKeyEditTextFor(firstRowId)
        val thirdValueEdit = findValueEditTextFor(thirdRowId)
        // Mark in-flight values + focus on the third row's value
        // EditText so we can prove focus survives the removal.
        firstKeyEdit.setText("first-key")
        thirdValueEdit.setText("third-value")
        thirdValueEdit.requestFocus()

        fakeVm.removeRow(middleRowId)
        renderCurrent()

        assertThat(container.childCount).isEqualTo(2)
        val refoundFirstKeyEdit = findKeyEditTextFor(firstRowId)
        val refoundThirdValueEdit = findValueEditTextFor(thirdRowId)
        assertThat(refoundFirstKeyEdit).isSameInstanceAs(firstKeyEdit)
        assertThat(refoundThirdValueEdit).isSameInstanceAs(thirdValueEdit)
        assertThat(refoundFirstKeyEdit.text?.toString()).isEqualTo("first-key")
        assertThat(refoundThirdValueEdit.text?.toString()).isEqualTo("third-value")
        assertThat(refoundThirdValueEdit.isFocused).isTrue()
    }

    // ---- helpers ----------------------------------------------------------

    private fun renderCurrent() {
        CustomFieldsRowsRenderer.render(
            container = container,
            inflater = inflater,
            state = fakeVm.snapshot(),
            callbacks = callbacks,
        )
    }

    private fun findRowViewFor(rowId: Long): android.view.View {
        for (i in 0 until container.childCount) {
            val v = container.getChildAt(i)
            if ((v.getTag(R.id.custom_field_row_id) as? Long) == rowId) return v
        }
        error("row view for rowId=$rowId not found")
    }

    private fun findKeyEditTextFor(rowId: Long): TextInputEditText =
        findRowViewFor(rowId).findViewById(R.id.input_field_key)

    private fun findValueEditTextFor(rowId: Long): TextInputEditText =
        findRowViewFor(rowId).findViewById(R.id.input_field_value)

    /**
     * In-test stand-in for the customField slice of
     * [CredentialEditViewModel]. Mirrors the public reducer methods
     * with the same monotonic rowId allocation strategy so the
     * renderer behaves identically to production. Avoids dragging
     * the full ViewModel + use-case graph into a renderer test.
     */
    private class FakeCustomFieldsHolder {
        private var nextRowId: Long = 1L
        private var current = CustomFieldsState()

        fun snapshot(): CustomFieldsState = current

        fun addRow() {
            current = current.copy(
                rows = current.rows + Row(rowId = nextRowId++, fieldKey = "", value = ""),
            )
        }
        fun removeRow(rowId: Long) {
            current = current.copy(rows = current.rows.filterNot { it.rowId == rowId })
        }
        fun updateKey(rowId: Long, text: String) {
            current = current.copy(
                rows = current.rows.map { if (it.rowId == rowId) it.copy(fieldKey = text) else it },
            )
        }
        fun updateValue(rowId: Long, text: String) {
            current = current.copy(
                rows = current.rows.map { if (it.rowId == rowId) it.copy(value = text) else it },
            )
        }
    }
}
