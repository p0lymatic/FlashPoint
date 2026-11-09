package com.p0lymatic.flashpoint

import com.p0lymatic.flashpoint.core.PartitionScheme
import com.p0lymatic.flashpoint.core.TargetArchitecture
import com.p0lymatic.flashpoint.core.VentoyCompletionMetrics
import com.p0lymatic.flashpoint.core.VentoyPhase
import com.p0lymatic.flashpoint.ui.DoneMetrics
import com.p0lymatic.flashpoint.ui.formatBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.p0lymatic.flashpoint.core.VentoyInstaller
import com.p0lymatic.flashpoint.core.VentoyRelease
import com.p0lymatic.flashpoint.usb.VirtualDriveMode
import com.p0lymatic.flashpoint.usb.VirtualUsbDriveStreamer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class VentoyInstallerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()


    @Test
    fun testVentoyPhaseEnumValues() {
        val phases = VentoyPhase.values().map { it.name }
        assertTrue(phases.contains("VERIFYING"))
        assertTrue(phases.contains("COMPLETED"))
        assertTrue(phases.contains("DOWNLOADING"))
        assertTrue(phases.contains("PARTITIONING"))
        assertTrue(phases.contains("WRITING_BOOTLOADER"))
    }

    @Test
    fun testVentoyCompletionMetrics() {
        val totalBytes = 33_554_432L // 32 MB
        val durationMillis = 42_000L
        val elapsedSeconds = durationMillis / 1000.0
        val speedMBs = (totalBytes / elapsedSeconds) / (1024.0 * 1024.0)
        val formatted = "%02d:%02d".format(durationMillis / 60000, (durationMillis % 60000) / 1000)

        val metrics = VentoyCompletionMetrics(
            durationMillis = durationMillis,
            formattedDuration = formatted,
            averageSpeedMBs = speedMBs,
            targetArchitecture = "x86_64",
            ventoyVersion = "1.0.99",
            partitionScheme = PartitionScheme.GPT.name,
            driveName = "Samsung Flash Drive",
            driveCapacity = "32.0 GB"
        )

        assertEquals("00:42", metrics.formattedDuration)
        assertEquals("x86_64", metrics.targetArchitecture)
        assertEquals("GPT", metrics.partitionScheme)
        assertEquals("Samsung Flash Drive", metrics.driveName)
        assertEquals("32.0 GB", metrics.driveCapacity)
        assertTrue(metrics.averageSpeedMBs > 0.7 && metrics.averageSpeedMBs < 0.8)
    }

    @Test
    fun testFormatBytes() {
        assertEquals("32.0 GB", formatBytes(32_000_000_000L))
        assertEquals("500.0 MB", formatBytes(500_000_000L))
        assertEquals("100 B", formatBytes(100L))
    }

    @Test
    fun testDoneMetricsMapping() {
        val doneMetrics = DoneMetrics(
            driveNameAndSize = "SanDisk Ultra (16.0 GB)",
            targetOrImageName = "Ventoy 1.0.99",
            schemeAndArchitecture = "GPT (x86_64)",
            averageSpeed = "24.5 MB/s",
            elapsedTime = "00:38"
        )

        assertEquals("SanDisk Ultra (16.0 GB)", doneMetrics.driveNameAndSize)
        assertEquals("Ventoy 1.0.99", doneMetrics.targetOrImageName)
        assertEquals("GPT (x86_64)", doneMetrics.schemeAndArchitecture)
        assertEquals("24.5 MB/s", doneMetrics.averageSpeed)
        assertEquals("00:38", doneMetrics.elapsedTime)
    }

    @Test
    fun testMbrSignatureBytes() {
        val mbr = ByteArray(512)
        mbr[510] = 0x55.toByte()
        mbr[511] = 0xAA.toByte()

        val sig0 = mbr[510].toInt() and 0xFF
        val sig1 = mbr[511].toInt() and 0xFF
        assertEquals(0x55, sig0)
        assertEquals(0xAA, sig1)
    }

    @Test
    fun testVentoyInstallDemoSuccessMode() = runBlocking {
        val streamer = VirtualUsbDriveStreamer(
            mode = VirtualDriveMode.SUCCESS,
            cacheDirectory = tempFolder.root,
            expectedTotalBytes = 34L * 1024L * 1024L
        )
        streamer.open()
        try {
            val installer = VentoyInstaller(OkHttpClient(), tempFolder.root)
            val release = VentoyRelease(
                version = "1.0.99",
                downloadUrl = "ventoy-1.0.99-linux.tar.gz",
                publishedAt = "2024-11-01 (Demo)",
                assetSize = 0L,
                changelog = "Demo"
            )
            val events = installer.install(
                release = release,
                streamer = streamer,
                scheme = PartitionScheme.MBR,
                arch = TargetArchitecture.X86_64,
                driveName = "Virtual RAM Disk (Success)",
                driveCapacity = "1.0 GB"
            ).toList()

            // Verify download phase progress
            val downloadEvents = events.filter { it.phase == VentoyPhase.DOWNLOADING }
            assertTrue(downloadEvents.any { it.progressPercent == 0f })
            assertTrue(downloadEvents.any { it.progressPercent == 50f })
            assertTrue(downloadEvents.any { it.progressPercent == 100f })

            // Verify partitioning phase progress
            val partitionEvents = events.filter { it.phase == VentoyPhase.PARTITIONING }
            assertTrue(partitionEvents.any { it.progressPercent == 30f })
            assertTrue(partitionEvents.any { it.progressPercent == 100f })

            // Verify bootloader phase progress
            val bootloaderEvents = events.filter { it.phase == VentoyPhase.WRITING_BOOTLOADER }
            assertTrue(bootloaderEvents.isNotEmpty())
            assertTrue(bootloaderEvents.any { it.progressPercent == 100f })

            // Verify verifying and completed phases
            assertTrue(events.any { it.phase == VentoyPhase.VERIFYING && it.progressPercent == 98f })
            val completedEvent = events.last()
            assertEquals(VentoyPhase.COMPLETED, completedEvent.phase)
            assertEquals(100f, completedEvent.progressPercent, 0.01f)
            assertEquals("Virtual RAM Disk (Success)", completedEvent.metrics?.driveName)
        } finally {
            streamer.close()
        }
    }

    @Test
    fun testVentoyInstallDemoIoErrorMode() = runBlocking {
        val streamer = VirtualUsbDriveStreamer(
            mode = VirtualDriveMode.IO_ERROR,
            cacheDirectory = tempFolder.root,
            expectedTotalBytes = 34L * 1024L * 1024L
        )
        streamer.open()
        try {
            val installer = VentoyInstaller(OkHttpClient(), tempFolder.root)
            val release = VentoyRelease(
                version = "1.0.99",
                downloadUrl = "ventoy-1.0.99-linux.tar.gz",
                publishedAt = "2024-11-01 (Demo)",
                assetSize = 0L,
                changelog = "Demo"
            )
            val events = installer.install(
                release = release,
                streamer = streamer,
                scheme = PartitionScheme.MBR
            ).toList()

            val failedEvent = events.last()
            assertEquals(VentoyPhase.FAILED, failedEvent.phase)
            assertTrue(
                "Failure message should mention SCSI reset: ${failedEvent.message}",
                failedEvent.message.contains("SCSI Check Condition: USB Connection Reset")
            )

            // Verify that bootloader write started and reached ~60% before failing
            val bootEvents = events.filter { it.phase == VentoyPhase.WRITING_BOOTLOADER }
            assertTrue("Bootloader writes should have started", bootEvents.isNotEmpty())
            val maxBootPct = bootEvents.maxOf { it.progressPercent }
            assertTrue("Max bootloader percent should be around 60%: $maxBootPct", maxBootPct in 55f..70f)
        } finally {
            streamer.close()
        }
    }

    @Test
    fun testVentoyInstallDemoVerifyFailureMode() = runBlocking {
        val streamer = VirtualUsbDriveStreamer(
            mode = VirtualDriveMode.VERIFY_FAILURE,
            cacheDirectory = tempFolder.root,
            expectedTotalBytes = 34L * 1024L * 1024L
        )
        streamer.open()
        try {
            val installer = VentoyInstaller(OkHttpClient(), tempFolder.root)
            val release = VentoyRelease(
                version = "1.0.99",
                downloadUrl = "ventoy-1.0.99-linux.tar.gz",
                publishedAt = "2024-11-01 (Demo)",
                assetSize = 0L,
                changelog = "Demo"
            )
            val events = installer.install(
                release = release,
                streamer = streamer,
                scheme = PartitionScheme.MBR
            ).toList()

            // Bootloader should reach 100% and verification should start
            assertTrue(events.any { it.phase == VentoyPhase.WRITING_BOOTLOADER && it.progressPercent == 100f })
            assertTrue(events.any { it.phase == VentoyPhase.VERIFYING })

            val failedEvent = events.last()
            assertEquals(VentoyPhase.FAILED, failedEvent.phase)
            assertTrue(
                "Failure message should mention invalid MBR signature: ${failedEvent.message}",
                failedEvent.message.contains("Invalid MBR boot signature")
            )
        } finally {
            streamer.close()
        }
    }
}
