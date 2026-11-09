package com.p0lymatic.flashpoint.usb

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

enum class VirtualDriveMode {
    SUCCESS,
    IO_ERROR,
    VERIFY_FAILURE
}

class VirtualUsbDriveStreamer(
    val mode: VirtualDriveMode,
    private val cacheDirectory: File,
    var expectedTotalBytes: Long = DEFAULT_EXPECTED_BYTES
) : UsbMassStorageStreamer(null, null) {

    constructor(
        context: Context,
        mode: VirtualDriveMode,
        expectedTotalBytes: Long = DEFAULT_EXPECTED_BYTES
    ) : this(mode, context.cacheDir, expectedTotalBytes)

    override var blockSize: Int = SECTOR_SIZE
    override var lastLba: Long = TOTAL_SECTORS - 1L
    override val totalSectors: Long get() = TOTAL_SECTORS

    private val cacheFile: File = File(cacheDirectory, VIRTUAL_DISK_FILENAME)
    private var randomAccessFile: RandomAccessFile? = null
    private var bytesWrittenSoFar: Long = 0L

    override fun open() {
        val parent = cacheFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val raf = RandomAccessFile(cacheFile, "rw")
        raf.setLength(TOTAL_CAPACITY_BYTES)
        randomAccessFile = raf
        bytesWrittenSoFar = 0L
    }

    override fun close() {
        try {
            randomAccessFile?.close()
        } catch (_: Exception) {}
        randomAccessFile = null
    }

    override fun testUnitReady(): Boolean = true

    override fun inquiry(): DeviceInfo = DeviceInfo("FLASHPT", "VIRTUAL RAMDISK", "1.06")

    override fun getCapacity(): Pair<Long, Int> = Pair(lastLba, blockSize)

    override fun readCapacity10(): Pair<Long, Int> = Pair(lastLba, blockSize)

    override fun readCapacity16(): Pair<Long, Int> = Pair(lastLba, blockSize)

    override fun requestSense(): SenseData = SenseData(0, 0, 0)

    override fun writeSectors(lba: Long, sectorCount: Int, data: ByteArray) {
        val raf = randomAccessFile ?: throw IOException("Virtual USB drive is not open")
        val bytes = sectorCount * blockSize
        require(data.size == bytes) { "Data length ${data.size} != $bytes" }

        // IO_ERROR check: throw when write progress reaches ~60%
        if (mode == VirtualDriveMode.IO_ERROR) {
            val threshold = (expectedTotalBytes * 0.60).toLong().coerceAtLeast(1L)
            if (bytesWrittenSoFar + bytes >= threshold) {
                throw IOException("SCSI Check Condition: USB Connection Reset / Device Unplugged")
            }
        }

        // Realistic delay simulation (target ~28 MB/s)
        val delayMs = (bytes.toLong() * 1000L) / SIMULATED_SPEED_BYTES_PER_SEC
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Write interrupted")
            }
        }

        synchronized(this) {
            raf.seek(lba * blockSize)
            raf.write(data, 0, bytes)
        }
        bytesWrittenSoFar += bytes
    }

    override fun readSectors(lba: Long, sectorCount: Int): ByteArray {
        val raf = randomAccessFile ?: throw IOException("Virtual USB drive is not open")
        val bytes = sectorCount * blockSize
        val buffer = ByteArray(bytes)

        synchronized(this) {
            raf.seek(lba * blockSize)
            var totalRead = 0
            while (totalRead < bytes) {
                val r = raf.read(buffer, totalRead, bytes - totalRead)
                if (r == -1) break
                totalRead += r
            }
        }

        // VERIFY_FAILURE mode: corrupt MBR signature at offset 510..511 for LBA 0
        if (mode == VirtualDriveMode.VERIFY_FAILURE && lba == 0L && bytes >= 512) {
            buffer[510] = 0x00.toByte()
            buffer[511] = 0x00.toByte()
        }

        return buffer
    }

    companion object {
        const val VIRTUAL_DISK_FILENAME = "virtual_ramdisk.bin"
        const val TOTAL_SECTORS = 2_097_152L
        const val SECTOR_SIZE = 512
        const val TOTAL_CAPACITY_BYTES = TOTAL_SECTORS * SECTOR_SIZE // 1,073,741,824 bytes (1.0 GB)
        const val SIMULATED_SPEED_BYTES_PER_SEC = 28L * 1024L * 1024L // ~28 MB/s
        const val DEFAULT_EXPECTED_BYTES = 100L * 1024L * 1024L // 100 MB default

        fun cleanupFile(cacheDir: File) {
            try {
                val file = File(cacheDir, VIRTUAL_DISK_FILENAME)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }

        fun cleanupFile(context: Context) {
            cleanupFile(context.cacheDir)
        }
    }
}
