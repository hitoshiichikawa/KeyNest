package inc.goodanswers.keynest.ui.edit

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Backs Req 3.3: the full 64-char lowercase SHA-256 hex must be written to
 * the system clipboard (no truncation, no transformation).
 *
 * We unit-test the pure payload builder rather than driving the whole
 * Activity, which keeps the test small and free of ServiceLocator wiring.
 * The Activity invokes
 * `clipboard.setPrimaryClip(SignatureClipboardPayload.build(...))` on the
 * Copy click, so verifying the ClipData contents covers the contract.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SignatureClipboardPayloadTest {

    @Test
    fun build_preservesFullHexInClipboardItemText() {
        // Arrange: a canonical 64-char lowercase hex
        val hex = "0".repeat(63) + "f"
        require(hex.length == 64)

        // Act
        val clip = SignatureClipboardPayload.build(label = "KeyNest signature SHA-256", hex = hex)

        // Assert: itemCount + text both intact, label preserved
        assertThat(clip.itemCount).isEqualTo(1)
        assertThat(clip.getItemAt(0).text.toString()).isEqualTo(hex)
        assertThat(clip.description.label.toString()).isEqualTo("KeyNest signature SHA-256")
    }

    @Test
    fun build_doesNotMutateOrTruncate_evenForUnusualInput() {
        // Boundary: empty string (would only happen via a bug in the
        // caller, but the helper itself must not crash or pad).
        val clip = SignatureClipboardPayload.build(label = "L", hex = "")
        assertThat(clip.getItemAt(0).text.toString()).isEmpty()
    }
}
