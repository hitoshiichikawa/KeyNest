package io.github.hitoshiichikawa.keynest.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migration from v2 to v3. Issue #66 Phase 1
 * (requirements 2.1 / 2.2).
 *
 * Adds the `custom_fields_ciphertext` and `custom_fields_iv` BLOB columns
 * to the existing `credentials` table. Both are `NOT NULL DEFAULT x''`
 * (zero-length BLOB).
 *
 * Why empty BLOB rather than a real "empty list" ciphertext:
 * - SQLite ALTER TABLE ADD COLUMN only accepts literal defaults; there is
 *   no way to compute an AES-GCM ciphertext at migration time.
 * - The AesGcmCipher relies on the Android Keystore (I/O, exceptions),
 *   which is the wrong place to be called from a synchronous Room
 *   migration callback.
 * - Instead, EncryptedCustomFieldsCodec.decrypt() interprets a
 *   zero-length ciphertext as "no fields" (design.md §4.1 / §6.1). The
 *   first SaveCredentialUseCase / UpdateCredentialUseCase write on a
 *   migrated row replaces the empty BLOB with a real `[]` ciphertext.
 *
 * `fallbackToDestructiveMigration` remains disabled — losing credentials
 * silently is worse UX than a startup crash (existing MVP policy).
 */
object Migration_2_3 : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE credentials ADD COLUMN custom_fields_ciphertext BLOB NOT NULL DEFAULT x''",
        )
        db.execSQL(
            "ALTER TABLE credentials ADD COLUMN custom_fields_iv BLOB NOT NULL DEFAULT x''",
        )
    }
}
