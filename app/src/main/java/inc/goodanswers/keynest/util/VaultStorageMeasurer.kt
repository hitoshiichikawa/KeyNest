package inc.goodanswers.keynest.util

import android.content.Context
import inc.goodanswers.keynest.data.KeyNestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Measures the on-disk byte size occupied by the credential vault.
 *
 * Issue #10 Req 4.4, 4.5, 4.6. Sums the lengths of the three Room
 * artefacts that make up the vault:
 *
 * - `keynest.db`   - the main SQLite file
 * - `keynest.db-wal` - the WAL journal (Room uses WAL mode by default)
 * - `keynest.db-shm` - the shared-memory file paired with the WAL
 *
 * Other process-local files (SharedPreferences, Keystore metadata) are
 * intentionally excluded because (a) the public API does not expose
 * sizes for them and (b) they hold no credential payload (Req 4.5).
 *
 * The work runs on `Dispatchers.IO` so that the call site (typically a
 * ViewModel state flow build) does not block the main thread (NFR 2.2).
 *
 * `open` so that JVM unit tests can substitute the file probes; the
 * default implementation uses real `File.length()` and is exercised by
 * [inc.goodanswers.keynest.util.VaultStorageMeasurerTest] which writes
 * fixture files into the JVM tmp directory.
 */
open class VaultStorageMeasurer(private val context: Context) {

    /** Returns the total byte size, or 0 when no DB file exists yet. */
    open suspend fun measureBytes(): Long = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(KeyNestDatabase.DB_NAME)
        val parent = dbFile.parentFile
        val files = if (parent == null) {
            listOf(dbFile)
        } else {
            listOf(
                dbFile,
                File(parent, "${KeyNestDatabase.DB_NAME}-wal"),
                File(parent, "${KeyNestDatabase.DB_NAME}-shm"),
            )
        }
        files.sumOf { if (it.exists()) it.length() else 0L }
    }
}
