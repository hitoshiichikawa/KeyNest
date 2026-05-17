package io.github.hitoshiichikawa.keynest.autofill.builder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.autofill.InlinePresentation
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.v1.InlineSuggestionUi
import io.github.hitoshiichikawa.keynest.R

/**
 * Builds the presentations the Autofill UI displays for each dataset.
 *
 * Two surfaces:
 * - **Popup (RemoteViews)** — the legacy floating panel anchored to the
 *   focused field. Used as the base layer on every API.
 * - **Inline (Slice-backed)** — surface rendered INSIDE the IME's
 *   suggestion strip on API 30+ for IMEs that support it (Gboard etc.).
 *   Eliminates the visual conflict between the autofill popup and the
 *   keyboard reported by users.
 *
 * Requirements: 3.3
 */
class DatasetPresentationFactory(
    private val context: Context,
) {

    /**
     * @param label Shown bold (the user-friendly label saved with the
     *   credential).
     * @param subtitle Shown smaller (we use the username so the user can
     *   distinguish multiple credentials registered for the same app).
     */
    fun build(label: String, subtitle: String): RemoteViews {
        val packageName = context.packageName
        val views = RemoteViews(packageName, R.layout.dataset_presentation)
        views.setTextViewText(R.id.dataset_label, label)
        views.setTextViewText(R.id.dataset_subtitle, subtitle)
        return views
    }

    /**
     * Build the inline (IME suggestion-strip) presentation for a dataset.
     *
     * Returns null when the spec is null or when running on a device whose
     * platform does not support inline suggestions. The caller falls back
     * to the popup-only presentation in that case.
     */
    fun buildInline(
        label: String,
        subtitle: String,
        spec: InlinePresentationSpec?,
    ): InlinePresentation? {
        if (spec == null) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return buildInlineApiR(label, subtitle, spec)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildInlineApiR(
        label: String,
        subtitle: String,
        spec: InlinePresentationSpec,
    ): InlinePresentation {
        // The attribution PendingIntent fires when the user long-presses the
        // chip and asks where the suggestion came from. We route to our own
        // launcher so the user can manage their credentials.
        val attribution = Intent(context, io.github.hitoshiichikawa.keynest.ui.list.CredentialListActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            ATTRIBUTION_REQUEST_CODE,
            attribution,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val slice = InlineSuggestionUi.newContentBuilder(pending)
            .setTitle(label)
            .setSubtitle(subtitle)
            .build()
            .slice
        return InlinePresentation(slice, spec, /* pinned */ false)
    }

    private companion object {
        const val ATTRIBUTION_REQUEST_CODE = 0
    }
}
