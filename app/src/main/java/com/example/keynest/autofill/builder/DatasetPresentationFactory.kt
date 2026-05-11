package com.example.keynest.autofill.builder

import android.content.Context
import android.widget.RemoteViews
import com.example.keynest.R

/**
 * Builds the RemoteViews that the Autofill UI displays for each dataset
 * (the label + subtitle the user sees in the suggestion popup).
 *
 * Requirements: 3.3
 *
 * MVP uses the legacy RemoteViews presentation API. The newer
 * [android.service.autofill.Presentations] API (API 30+) is intentionally
 * NOT adopted yet because the legacy form is sufficient for the username +
 * label pair we surface, and supporting the new API requires
 * targetSdk-specific RemoteViews wiring.
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
}
