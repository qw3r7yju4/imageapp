package com.example.imageapp

import com.example.imageapp.model.*
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZipAndSharingTest {

    @Test
    fun testFormatFileSize() {
        assertEquals("500 B", StorageScanner.formatFileSize(500L))
        assertEquals("1.0 KB", StorageScanner.formatFileSize(1024L))
        assertEquals("1.5 MB", StorageScanner.formatFileSize((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.00 GB", StorageScanner.formatFileSize((2L * 1024 * 1024 * 1024)))
    }

    @Test
    fun testFormatDuration() {
        assertEquals("00:45", StorageScanner.formatDuration(45000L))
        assertEquals("02:30", StorageScanner.formatDuration(150000L))
        assertEquals("1:05:00", StorageScanner.formatDuration(3900000L))
    }

    @Test
    fun testZipManagerCompression() = runBlocking {
        val tempDir = File.createTempFile("zip_test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val file1 = File(tempDir, "sample1.txt").apply { writeText("Hello World 1") }
            val file2 = File(tempDir, "sample2.txt").apply { writeText("Hello World 2") }
            val destZip = File(tempDir, "test_archive.zip")

            val success = ZipManager.zipFiles(
                sourceFiles = listOf(file1, file2),
                destZipFile = destZip,
                onProgress = { _, _ -> }
            )

            assertTrue(success, "Zip creation should succeed")
            assertTrue(destZip.exists(), "Zip archive file must exist")
            assertTrue(destZip.length() > 0, "Zip archive must not be empty")

            // Verify entries inside zip file
            ZipFile(destZip).use { zip ->
                val entries = zip.entries().toList().map { it.name }
                assertTrue(entries.contains("sample1.txt"), "Zip should contain sample1.txt")
                assertTrue(entries.contains("sample2.txt"), "Zip should contain sample2.txt")
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testP2PTransferManagerSpeedUp() {
        P2PTransferManager.resetState()
        assertEquals(TransferStatus.IDLE, P2PTransferManager.transferState.value.status)

        P2PTransferManager.requestSpeedUpToWifi()
        assertTrue(P2PTransferManager.transferState.value.isWifiActive)

        P2PTransferManager.cancelTransfer()
        assertEquals(TransferStatus.IDLE, P2PTransferManager.transferState.value.status)
    }
}
