package io.github.hitoshiichikawa.keynest.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Room entity backing the `detected_fields` table. Issue #67 Phase 2
 * (requirements §5 Req 1, design.md §3.1).
 *
 * Per-row semantics: "for app [packageName], we observed an editable
 * ViewNode whose [source] yielded [fieldKey] (raw, un-normalised) at
 * [lastDetectedAt]". The user's plaintext input is NEVER stored — `text`
 * source is intentionally excluded (requirements §4 Q1).
 *
 * Composite primary key `(package_name, field_key, source)` ensures that
 * the same fieldKey observed via the same source for the same app
 * collapses to one row (`INSERT OR REPLACE` strategy updates
 * `last_detected_at`). The `(package_name, last_detected_at)` index serves
 * the suggestion UI query path (`observeRecentByPackage`, design.md §3.1
 * Req 1.5).
 *
 * Encryption: none. `fieldKey` values are resource ids / autofillHints /
 * a11y labels — developer-authored strings that do not contain PII
 * (requirements §9). Same rationale as the existing `package_name`
 * column on `credentials`.
 */
@Entity(
    tableName = "detected_fields",
    primaryKeys = ["package_name", "field_key", "source"],
    indices = [
        Index(
            value = ["package_name", "last_detected_at"],
            orders = [Index.Order.ASC, Index.Order.DESC],
        ),
    ],
)
data class DetectedFieldEntity(
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "field_key")
    val fieldKey: String,

    /**
     * Storage key of one of the four [DetectedFieldSource] values
     * (`autofillHints` / `hint` / `resourceId` / `contentDescription`).
     * Stored as TEXT rather than INTEGER so DB inspection tools can show
     * the source plainly and a future Phase 3 source addition can land
     * without a column-type migration.
     */
    @ColumnInfo(name = "source")
    val source: String,

    /**
     * Wall-clock epoch millis at which the most recent FillRequest
     * recorded this row. Updated by [io.github.hitoshiichikawa.keynest.data.dao.DetectedFieldDao.upsertWithLruCap]
     * on every observed FillRequest, driving both the LRU cap and the
     * suggestion UI ordering.
     */
    @ColumnInfo(name = "last_detected_at")
    val lastDetectedAt: Long,
)
