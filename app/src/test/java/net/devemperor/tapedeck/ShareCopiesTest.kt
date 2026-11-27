package net.devemperor.tapedeck

import net.devemperor.tapedeck.data.Storage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ShareCopiesTest {
    private val hour = 60 * 60 * 1000L

    @Test
    fun `copies for sharing go after a day, newer ones stay`() {
        val dir = Files.createTempDirectory("share").toFile()
        try {
            val now = System.currentTimeMillis()
            fun copy(name: String, age: Long) = File(dir, name).apply { writeText("x"); setLastModified(now - age) }
            copy("old.mp4", 25 * hour)
            copy("leftover.mp4.part", 48 * hour)
            copy("fresh.mp4", 23 * hour)
            copy("just-shared.m4a", 0)

            Storage.deleteOlderThanADay(dir, now)

            assertEquals(listOf("fresh.mp4", "just-shared.m4a"), dir.list()!!.sorted())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `no folder yet is fine`() {
        Storage.deleteOlderThanADay(File("/nonexistent/tapedeck-share"), System.currentTimeMillis())
    }
}
