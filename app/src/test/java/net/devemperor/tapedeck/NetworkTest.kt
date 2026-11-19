package net.devemperor.tapedeck

import net.devemperor.tapedeck.data.DownloadState
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.download.NetState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTest {
    private fun entry(mobileData: Boolean = false) = Entry(
        id = "a", url = "https://youtu.be/a", title = "a", createdAt = 0,
        state = DownloadState.WAITING_NETWORK, mobileData = mobileData,
    )

    private val wifi = NetState(connected = true, unmetered = true)
    private val mobile = NetState(connected = true, unmetered = false)
    private val offline = NetState(connected = false, unmetered = false)

    @Test
    fun wifiOnlyHoldsBackMobileDataUnlessAllowed() {
        assertTrue(wifi.allows(entry(), wifiOnly = true))
        assertFalse(mobile.allows(entry(), wifiOnly = true))
        assertTrue(mobile.allows(entry(mobileData = true), wifiOnly = true))
        assertTrue(mobile.allows(entry(), wifiOnly = false))
    }

    @Test
    fun nothingLoadsOffline() {
        assertFalse(offline.allows(entry(mobileData = true), wifiOnly = true))
        assertFalse(offline.allows(entry(), wifiOnly = false))
        assertFalse(offline.onMobileData)
        assertTrue(mobile.onMobileData)
        assertFalse(wifi.onMobileData)
    }

    @Test
    fun waitsForWifiOnlyWithoutTheGoAhead() {
        assertTrue(entry().waitsForWifi)
        assertFalse(entry(mobileData = true).waitsForWifi)
    }
}
