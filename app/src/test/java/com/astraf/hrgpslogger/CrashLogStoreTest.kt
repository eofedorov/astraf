package com.astraf.hrgpslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CrashLogStoreTest {

    @Test
    fun fileNameFor_usesExpectedPattern() {
        val name = CrashLogStore.fileNameFor(1_704_067_200_123L)
        assertTrue(name.startsWith("crash_"))
        assertTrue(name.endsWith(".txt"))
    }

    @Test
    fun timestampFromFileName_roundTrip() {
        val millis = 1_704_067_200_456L
        val fileName = CrashLogStore.fileNameFor(millis)
        assertEquals(millis, CrashLogStore.timestampFromFileName(fileName))
    }

    @Test
    fun saveToFile_listFromDir_newestFirst() {
        val dir = createTempDir()
        CrashLogStore.saveToFile(dir, "first", timestampMillis = 1000L)
        CrashLogStore.saveToFile(dir, "second", timestampMillis = 2000L)
        val entries = CrashLogStore.listFromDir(dir)
        assertEquals(2, entries.size)
        assertEquals(2000L, entries[0].timestampMillis)
        assertEquals(1000L, entries[1].timestampMillis)
        assertEquals("second", entries[0].file.readText())
    }

    @Test
    fun listFromDir_latestIsFirst() {
        val dir = createTempDir()
        CrashLogStore.saveToFile(dir, "older", timestampMillis = 100L)
        CrashLogStore.saveToFile(dir, "newer", timestampMillis = 300L)
        val latest = CrashLogStore.listFromDir(dir).first()
        assertEquals("newer", latest.file.readText())
    }

    @Test
    fun clearDir_removesAllLogs() {
        val dir = createTempDir()
        CrashLogStore.saveToFile(dir, "crash", timestampMillis = 100L)
        CrashLogStore.clearDir(dir)
        assertTrue(CrashLogStore.listFromDir(dir).isEmpty())
    }

    @Test
    fun pruneOldFiles_keepsOnlyMaxCount() {
        val dir = createTempDir()
        repeat(25) { index ->
            CrashLogStore.saveToFile(dir, "log-$index", timestampMillis = index.toLong())
        }
        val remaining = dir.listFiles()?.size ?: 0
        assertEquals(CrashLogStore.MAX_LOG_FILES, remaining)
        val entries = CrashLogStore.listFromDir(dir)
        assertEquals(CrashLogStore.MAX_LOG_FILES, entries.size)
        assertTrue(entries.first().timestampMillis >= entries.last().timestampMillis)
    }

    @Test
    fun listFromDir_missingDir_returnsEmpty() {
        val dir = File.createTempFile("missing_crash_logs", "").apply { delete() }
        assertTrue(CrashLogStore.listFromDir(dir).isEmpty())
    }

    @Test
    fun timestampFromFileName_invalid_returnsNull() {
        assertNull(CrashLogStore.timestampFromFileName("not_a_crash.txt"))
    }

    private fun createTempDir(): File =
        File.createTempFile("crash_logs", "").apply {
            delete()
            mkdirs()
        }
}
