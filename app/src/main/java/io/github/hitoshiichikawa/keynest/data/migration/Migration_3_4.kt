package io.github.hitoshiichikawa.keynest.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migration from v3 to v4. Issue #67 Phase 2
 * (requirements §5 Req 2, design.md §4).
 *
 * Creates the `detected_fields` table that backs the "recently detected
 * fields" suggestion UI in the credential edit screen. The migration is
 * pure SQL with no I/O outside SQLite — the Keystore is not touched,
 * mirroring the [Migration_2_3] design.
 *
 * The existing `credentials` table is intentionally not modified
 * (requirements Req 2.2). Phase 2 is purely additive at the schema layer.
 *
 * Schema details:
 *   - Composite PK `(package_name, field_key, source)` collapses repeated
 *     observations of the same fieldKey from the same source for the same
 *     app, which is what `INSERT OR REPLACE` in
 *     [io.github.hitoshiichikawa.keynest.data.dao.DetectedFieldDao.insertOrReplaceInternal]
 *     relies on.
 *   - Index `(package_name, last_detected_at)` (DESC second-column order
 *     is harmless for SQLite but matches Room's
 *     `Index(orders = [ASC, DESC])` declaration so the schema diff is
 *     empty).
 *
 * `fallbackToDestructiveMigration` remains disabled (existing MVP policy).
 */
object Migration_3_4 : Migration(3, 4) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `detected_fields` (" +
                "`package_name` TEXT NOT NULL, " +
                "`field_key` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, " +
                "`last_detected_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`package_name`, `field_key`, `source`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_detected_fields_package_name_last_detected_at` " +
                "ON `detected_fields` (`package_name`, `last_detected_at` DESC)",
        )
    }
}
