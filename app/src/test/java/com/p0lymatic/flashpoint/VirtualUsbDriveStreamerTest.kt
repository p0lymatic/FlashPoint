package com.p0lymatic.flashpoint

import com.p0lymatic.flashpoint.usb.VirtualDriveMode
import com.p0lymatic.flashpoint.usb.VirtualUsbDriveStreamer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class VirtualUsbDriveStreamerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testVirtualDriveCapacityAndParameters() {
        val streamer = VirtualUsbDriveStreamer(
            mode = VirtualDriveMode.SUCCESS,
            cacheDirectory = tempFolder.root
        )

        assertEquals(512, streamer.blockSize)
        assertEquals(2_097_152L, streamer.totalSectors)
        assertEquals(2_097_151L, streamer.lastLba)
        val cap = streamer.getCapacity()
        assertEquals(2_097_151L, cap.first)
        assertEquals(512, cap.second)

        val info = streamer.inquiry()
        assertEquals("FLASHPT", info.vendor)
        assertEquals("VIRTUAL RAMDISK", info.product)
    }

    @Test
    fun testSuccessModeReadWriteAndMbr() {
        val streamer = VirtualUsbDriveStreamer(
            mode = VirtualDriveMode.SUCCESS,
            cacheDirectory = tempFolder.root
        )

        streamer.open()
        try {
            val sectorData = ByteArray(512)
            sectorData[0] = 0xEB.toByte()
            sectorData[1] = 0x3C.toByte()
            sectorData[510] = 0x55.toByte()
            sectorData[511] = 0xAA.toByte()

            streamer.writeSectors(0L, 1, sectorData)

            val readBack = streamer.readSectors(0L, 1)
            assertEquals(512, readBack.size)
            assertEquals(0xEB.toByte(), readBack[0])
            assertEquals(0x3C.toByte(), readBack[1])
            assertEquals(0x55, readBack[510].toInt() and 0xFF)
            assertEquals(0xAA, readBack[511].toInt() and 0xFF)
        } finally {
            streamer.close()
        }
    }

    @Test
    fun testIoErrorModeThrowsAtSixtyPercent() {
        val expectedBytes = 1_000_000L // 1 MB
        val streamer = VirtualUsbDriveStreamer(
            mode = VirtualDriveMode.IO_ERROR,
            cacheDirectory = tempFolder.root,
            expectedTotalBytes = expectedBytes
        )

        streamer.open()
        try {
            // Write 500 KB (50%, below 60% threshold)
            val chunk500k = ByteArray(1000 * 512) // 512,000 bytes
            streamer.writeSectors(0L, 1000, chunk500k)

            // Next write pushes over 600,000 bytes (60%)
            val chunk200k = ByteArray(400 * 512) // 204,800 bytes -> 716,800 bytes total
            try {
                streamer.writeSectors(1000L, 400, chunk200k)
                fail("Expected IOException on reaching ~60% write progress")
            } catch (e: IOException) {
                assertTrue(
                    "Error message should mention SCSI Connection Reset: ${e.message}",
                    e.message?.contains("SCSI Check Condition: USB Connection Reset") == true
                )
            }
        } finally {
            streamer.close()
        }
    }

    @Test
    fun testVerifyFailureModeCorruptsSignature() {
        val streamer = VirtualUsbDriveStreamer(
            mode = VirtualDriveMode.VERIFY_FAILURE,
            cacheDirectory = tempFolder.root
        )

        streamer.open()
        try {
            val sectorData = ByteArray(512)
            sectorData[510] = 0x55.toByte()
            sectorData[511] = 0xAA.toByte()

            streamer.writeSectors(0L, 1, sectorData)

            val readBack = streamer.readSectors(0L, 1)
            assertEquals(512, readBack.size)
            // Offset 510..511 should be corrupted to 0x00, 0x00
            assertEquals(0x00, readBack[510].toInt() and 0xFF)
            assertEquals(0x00, readBack[511].toInt() and 0xFF)
        } finally {
            streamer.close()
        }
    }

    @Test
    fun testCleanupFileDeletesCache() {
        val streamer = VirtualUsbDriveStreamer(
            mode = VirtualDriveMode.SUCCESS,
            cacheDirectory = tempFolder.root
        )

        streamer.open()
        val sectorData = ByteArray(512)
        streamer.writeSectors(0L, 1, sectorData)
        streamer.close()

        val diskFile = File(tempFolder.root, VirtualUsbDriveStreamer.VIRTUAL_DISK_FILENAME)
        assertTrue("Disk file should exist before cleanup", diskFile.exists())

        VirtualUsbDriveStreamer.cleanupFile(tempFolder.root)
        assertFalse("Disk file should be deleted after cleanup", diskFile.exists())
    }
}
