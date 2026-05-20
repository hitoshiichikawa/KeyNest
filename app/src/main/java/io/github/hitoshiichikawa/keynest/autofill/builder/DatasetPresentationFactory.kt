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
import io.github.hitoshiichikawa.keynest.autofill.icon.AutofillIconRasterizer

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
 * Both surfaces share a single [AutofillIconRasterizer] so the caller
 * application's icon resolves identically across popup and inline (Issue
 * #80 / requirements §4.2).
 *
 * Requirements: 3.3
 */
class DatasetPresentationFactory(
    private val context: Context,
    private val iconRasterizer: AutofillIconRasterizer = AutofillIconRasterizer(context),
) {

    /**
     * @param label Shown bold (the user-friendly label saved with the
     *   credential).
     * @param subtitle Shown smaller (we use the username so the user can
     *   distinguish multiple credentials registered for the same app).
     * @param callerPackage The autofill caller's package name (see
     *   [io.github.hitoshiichikawa.keynest.autofill.KeyNestAutofillService.onFillRequest]).
     *   When non-null / non-blank the dataset row's icon is rebound to the
     *   caller application's icon via `setImageViewBitmap` (Issue #80
     *   requirements §2.1). When null / blank the layout's default
     *   `ic_key_24` rendering is left in place (existing behaviour).
     */
    fun build(label: String, subtitle: String, callerPackage: String?): RemoteViews {
        val packageName = context.packageName
        val views = RemoteViews(packageName, R.layout.dataset_presentation)
        views.setTextViewText(R.id.dataset_label, label)
        views.setTextViewText(R.id.dataset_subtitle, subtitle)
        if (!callerPackage.isNullOrBlank()) {
            // Resolve + composite onto the blue tile. The rasterizer
            // returns a non-null bitmap on every code path (including
            // PackageManager failures), so we always end up with a
            // sensible visual — either the caller icon on the tile, or
            // the fallback key icon on the tile.
            val bitmap = iconRasterizer.loadCallerIconBitmap(callerPackage)
            views.setImageViewBitmap(R.id.dataset_icon, bitmap)
        }
        return views
    }

    /**
     * Build the inline (IME suggestion-strip) presentation for a dataset.
     *
     * Returns null when the spec is null or when running on a device whose
     * platform does not support inline suggestions. The caller falls back
     * to the popup-only presentation in that case.
     *
     * @param callerPackage same contract as [build]; resolves the caller's
     *   icon for `setStartIcon` (Issue #80 requirements §3.1 / §3.2). The
     *   inline surface always calls `setStartIcon` — either with a bitmap
     *   `Icon` from the caller's drawable, or with
     *   `Icon.createWithResource(R.drawable.ic_key_24)` on any failure
     *   path (requirements §3.3).
     */
    fun buildInline(
        label: String,
        subtitle: String,
        spec: InlinePresentationSpec?,
        callerPackage: String?,
    ): InlinePresentation? {
        if (spec == null) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return buildInlineApiR(label, subtitle, spec, callerPackage)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildInlineApiR(
        label: String,
        subtitle: String,
        spec: InlinePresentationSpec,
        callerPackage: String?,
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
        // Issue #80 requirements §3.4: when the IME advertises a max
        // size, clamp the rasterised bitmap to the smaller of the
        // rasterizer's default and the IME budget so we never push a
        // chip-busting bitmap across the Slice boundary. `maxSize.width
        // == 0` is the documented "no constraint" sentinel.
        val sizePx = run {
            val maxW = spec.maxSize.width
            val maxH = spec.maxSize.height
            val cap = when {
                maxW <= 0 || maxH <= 0 -> iconRasterizer.defaultSizePx
                else -> minOf(maxW, maxH)
            }
            minOf(iconRasterizer.defaultSizePx, cap)
        }
        val icon = iconRasterizer.loadCallerIconForInline(callerPackage, sizePx)
        val slice = InlineSuggestionUi.newContentBuilder(pending)
            // Requirement §3.1: must be called BEFORE `build()` and on
            // every code path (happy / fallback alike) so the chip is
            // never rendered without a leading icon.
            .setStartIcon(icon)
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
