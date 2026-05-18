package io.github.hitoshiichikawa.keynest.domain.model

/**
 * Whether KeyNest is currently selected as the device's Autofill service.
 *
 * Issue #10 Req 2.1. The Settings screen renders one badge per variant.
 * The actual probe lives in
 * [io.github.hitoshiichikawa.keynest.util.AutofillServiceStatus.isCurrentService] so
 * the Settings screen reuses MVP Req 6 / Issue #10 Req 2.2 logic without
 * duplication.
 */
enum class AutofillStatus {
    /** KeyNest is the active system Autofill provider. */
    Enabled,

    /** KeyNest is not selected as the system Autofill provider. */
    NotEnabled,
}
