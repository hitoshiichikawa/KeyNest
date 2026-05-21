package io.github.hitoshiichikawa.keynest.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migration from v4 to v5. Issue #91 (parent #89).
 *
 * Creates the `passkeys` table that backs the PassKey provider data layer
 * for the Android Credential Manager-based PassKey integration. The
 * migration is purely additive: existing `credentials` / `detected_fields`
 * tables and their data are not touched (requirements Req 2.3).
 *
 * Schema layout (matches [io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity]
 * and the exported `5.json` createSql verbatim):
 *   - 14 columns; `credentialId TEXT NOT NULL` is the primary key.
 *   - `userHandle` / `encryptedPrivateKey` / `privateKeyIv` are BLOB NOT NULL.
 *   - `isDiscoverable INTEGER NOT NULL DEFAULT 1` so newly inserted rows
 *     default to discoverable (matches `@ColumnInfo(defaultValue = "1")`).
 *   - `signCount INTEGER NOT NULL DEFAULT 0` (matches
 *     `@ColumnInfo(defaultValue = "0")`).
 *   - `rpDisplayName` / `userName` / `userDisplayName` / `displayName` /
 *     `lastUsedAt` are nullable.
 *
 * Index layout:
 *   - `index_passkeys_rpId` — non-unique, supports the `listByRpId` family
 *     of DAO queries.
 *   - `index_passkeys_rpId_userHandle` — UNIQUE so the same RP cannot store
 *     two PassKeys for the same userHandle (Issue #91 確定済の設計判断
 *     §決定 2).
 *
 * Column order in the `CREATE TABLE` statement intentionally matches the
 * field order in [io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity]
 * and the `fields[]` array in `app/schemas/.../5.json`. Room's identity-hash
 * compares column metadata (name / type / not-null / default) rather than
 * the literal SQL string, but keeping the order in sync makes the schema
 * file easier to review and reduces the surface area for future drift.
 *
 * Idempotency: `IF NOT EXISTS` is used on both the `CREATE TABLE` and the
 * two `CREATE INDEX` statements so re-running the migrate function (e.g.
 * the second-call test) is safe — same pattern as [Migration_3_4].
 *
 * `fallbackToDestructiveMigration` remains disabled (existing MVP policy).
 */
object Migration_4_5 : Migration(4, 5) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `passkeys` (" +
                "`credentialId` TEXT NOT NULL, " +
                "`rpId` TEXT NOT NULL, " +
                "`rpDisplayName` TEXT, " +
                "`userHandle` BLOB NOT NULL, " +
                "`userName` TEXT, " +
                "`userDisplayName` TEXT, " +
                "`isDiscoverable` INTEGER NOT NULL DEFAULT 1, " +
                "`encryptedPrivateKey` BLOB NOT NULL, " +
                "`privateKeyIv` BLOB NOT NULL, " +
                "`keyAlias` TEXT NOT NULL, " +
                "`signCount` INTEGER NOT NULL DEFAULT 0, " +
                "`displayName` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`lastUsedAt` INTEGER, " +
                "PRIMARY KEY(`credentialId`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_passkeys_rpId` " +
                "ON `passkeys` (`rpId`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_passkeys_rpId_userHandle` " +
                "ON `passkeys` (`rpId`, `userHandle`)",
        )
    }
}
