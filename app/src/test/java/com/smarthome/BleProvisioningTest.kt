package com.smarthome

import com.smarthome.data.ble.BleConnectionState
import com.smarthome.data.ble.BleProvisionStatus
import com.smarthome.data.ble.ProductionBleProvisioningRepository
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BleProvisioningTest {

    @Test
    fun testGattUuidsMatchSpecification() {
        assertEquals(
            "c1060000-0000-1000-8000-00805f9b34fb",
            ProductionBleProvisioningRepository.SERVICE_UUID.toString()
        )
        assertEquals(
            "c1060001-0000-1000-8000-00805f9b34fb",
            ProductionBleProvisioningRepository.CHAR_SSID_UUID.toString()
        )
        assertEquals(
            "c1060002-0000-1000-8000-00805f9b34fb",
            ProductionBleProvisioningRepository.CHAR_PASSWORD_UUID.toString()
        )
        assertEquals(
            "c1060003-0000-1000-8000-00805f9b34fb",
            ProductionBleProvisioningRepository.CHAR_CONNECT_UUID.toString()
        )
        assertEquals(
            "c1060004-0000-1000-8000-00805f9b34fb",
            ProductionBleProvisioningRepository.CHAR_STATUS_UUID.toString()
        )
        assertEquals(
            "00002902-0000-1000-8000-00805f9b34fb",
            ProductionBleProvisioningRepository.CCCD_UUID.toString()
        )
    }

    @Test
    fun testBleProvisionStatusModel() {
        val statusConnected = BleProvisionStatus(
            state = "connected",
            ssid = "MyHomeWiFi",
            ip = "192.168.31.71",
            detail = "Assigned IP 192.168.31.71 on wlan0"
        )
        assertEquals("connected", statusConnected.state)
        assertEquals("MyHomeWiFi", statusConnected.ssid)
        assertEquals("192.168.31.71", statusConnected.ip)
        assertNotNull(statusConnected.detail)

        val statusFailed = BleProvisionStatus(
            state = "failed",
            ssid = "MyHomeWiFi",
            detail = "Wrong WiFi password"
        )
        assertEquals("failed", statusFailed.state)
        assertNull(statusFailed.ip)
        assertEquals("Wrong WiFi password", statusFailed.detail)
    }

    @Test
    fun testBleConnectionStateEnum() {
        assertTrue(BleConnectionState.values().contains(BleConnectionState.DISCONNECTED))
        assertTrue(BleConnectionState.values().contains(BleConnectionState.SCANNING))
        assertTrue(BleConnectionState.values().contains(BleConnectionState.CONNECTING))
        assertTrue(BleConnectionState.values().contains(BleConnectionState.CONNECTED))
        assertTrue(BleConnectionState.values().contains(BleConnectionState.DISCOVERING_SERVICES))
        assertTrue(BleConnectionState.values().contains(BleConnectionState.READY))
        assertTrue(BleConnectionState.values().contains(BleConnectionState.DISCONNECTING))
    }

    @Test
    fun testSsidAndPasswordLengthConstraints() {
        val validSsid = "MyHomeNetwork"
        val tooLongSsid = "A".repeat(33)
        val validPass = "SuperSecretPassword123"
        val tooLongPass = "P".repeat(64)

        assertTrue(validSsid.toByteArray(Charsets.UTF_8).size <= 32)
        assertFalse(tooLongSsid.toByteArray(Charsets.UTF_8).size <= 32)

        assertTrue(validPass.toByteArray(Charsets.UTF_8).size <= 63)
        assertFalse(tooLongPass.toByteArray(Charsets.UTF_8).size <= 63)
    }
}
