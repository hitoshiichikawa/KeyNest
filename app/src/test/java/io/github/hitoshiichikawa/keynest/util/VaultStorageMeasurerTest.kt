package io.github.hitoshiichikawa.keynest.util

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hitoshiichikawa.keynest.data.KeyNestDatabase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Verifies [VaultStorageMeasurer.measureBytes] against fixture files on
 * Robolectric.
 *
 * Issue #10 Req 4.4, 4.5.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class VaultStorageMeasurerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val measurer = VaultStorageMeasurer(context)

    private lateinit var dbFile: File
    private lateinit var walFile: File
    private lateinit var shmFile: File

    @Before
    fun setUp() {
        dbFile = context.getDatabasePath(KeyNestDatabase.DB_NAME)
        dbFile.parentFile?.mkdirs()
        walFile = File(dbFile.parentFile, "${KeyNestDatabase.DB_NAME}-wal")
        shmFile = File(dbFile.parentFile, "${KeyNestDatabase.DB_NAME}-shm")
        cleanup()
    }

    @After
    fun tearDown() = cleanup()

    private fun cleanup() {
        listOf(dbFile, walFile, shmFile).forEach { if (it.exists()) it.delete() }
    }

    @Test
    fun measureBytes_returnsZero_whenNoFilesExist() = runTest {
        // Req 4.4: cold start with no DB writes yet.
        val size = measurer.measureBytes()
        assertThat(size).isEqualTo(0L)
    }

    @Test
    fun measureBytes_sumsDbAndWalAndShm() = runTest {
        // Arrange
        dbFile.writeBytes(ByteArray(100))
        walFile.writeBytes(ByteArray(50))
        shmFile.writeBytes(ByteArray(20))

        // Act
        val size = measurer.measureBytes()

        // Assert
        assertThat(size).isEqualTo(170L)
    }

    @Test
    fun measureBytes_excludesMissingAuxiliaryFiles() = runTest {
        // Req 4.4: real-world case where the WAL has been checkpointed
        // away. Only `keynest.db` is on disk.
        dbFile.writeBytes(ByteArray(300))
        assertThat(walFile.exists()).isFalse()
        assertThat(shmFile.exists()).isFalse()

        val size = measurer.measureBytes()

        assertThat(size).isEqualTo(300L)
    }
}
