package net.devemperor.tapedeck

import net.devemperor.tapedeck.engine.AppUpdates
import net.devemperor.tapedeck.engine.Versions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatesTest {
    @Test
    fun `compares versions by their numbers`() {
        assertTrue(Versions.isNewer("v0.2.0", "0.1.0"))
        assertTrue(Versions.isNewer("0.1.10", "0.1.9"))
        assertTrue(Versions.isNewer("1.0", "0.9.9"))
        assertFalse(Versions.isNewer("v0.2.0", "0.2.0"))
        assertFalse(Versions.isNewer("v0.2.0", "0.2.0-debug"))
        assertFalse(Versions.isNewer("0.1.9", "0.2.0"))
    }

    @Test
    fun `picks the APK for this processor, else the universal one, with GitHub's checksum`() {
        val body = """{"tag_name":"v0.3.0","html_url":"https://github.com/DevEmperor/Tapedeck/releases/tag/v0.3.0","assets":[
            {"name":"tapedeck-0.3.0-universal.apk","browser_download_url":"https://x/universal.apk","size":200,"digest":"sha256:AAAA"},
            {"name":"tapedeck-0.3.0-arm64-v8a.apk","browser_download_url":"https://x/arm64.apk","size":55,"digest":"sha256:BBBB"},
            {"name":"tapedeck-0.3.0-x86_64.apk","browser_download_url":"https://x/x86_64.apk","size":57}]}"""
        val phone = AppUpdates.parse(body, listOf("arm64-v8a", "armeabi-v7a"))!!
        assertEquals("0.3.0", phone.version)
        assertEquals("https://x/arm64.apk", phone.apkUrl)
        assertEquals("bbbb", phone.apkSha256)
        assertEquals(55L, phone.apkSize)

        val emulator = AppUpdates.parse(body, listOf("x86_64"))!!
        assertEquals("https://x/x86_64.apk", emulator.apkUrl)
        assertNull(emulator.apkSha256)

        assertEquals("https://x/universal.apk", AppUpdates.parse(body, listOf("riscv64"))!!.apkUrl)
    }
}
