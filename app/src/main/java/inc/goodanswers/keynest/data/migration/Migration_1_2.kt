package inc.goodanswers.keynest.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migration from v1 to v2. Issue #9 Req 3.1, 3.2, 3.4.
 *
 * Adds the `last_used_at INTEGER NULL` column to the existing `credentials`
 * table. Existing rows are left with NULL (= "never used") so the
 * "recently used" carousel starts empty on first launch after upgrade and
 * fills in lazily as autofill is consumed (Req 3.4).
 *
 * `fallbackToDestructiveMigration` is intentionally NOT enabled (existing
 * policy from MVP design) -- losing credentials silently would be a worse
 * UX than a startup crash that prompts re-installation.
 */
object Migration_1_2 : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // SQLite ALTER TABLE ADD COLUMN with no default yields NULL for
        // pre-existing rows, which matches the "never used" semantics we
        // want for Req 3.4.
        db.execSQL("ALTER TABLE credentials ADD COLUMN last_used_at INTEGER")
    }
}
