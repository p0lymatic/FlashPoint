package com.p0lymatic.flashpoint.core

import com.p0lymatic.flashpoint.usb.UsbMassStorageStreamer
import com.squareup.moshi.JsonReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.CRC32
import kotlin.coroutines.coroutineContext

data class VentoyRelease(
    val version: String,
    val downloadUrl: String,
    val publishedAt: String,
    val assetSize: Long
)

enum class PartitionScheme { MBR, GPT }

data class VentoyInstallProgress(
    val phase: VentoyPhase,
    val progressPercent: Float,
    val message: String
)

enum class VentoyPhase { FETCHING_RELEASE, DOWNLOADING, PARTITIONING, WRITING_BOOTLOADER, COMPLETED, FAILED }

class VentoyInstaller(
    private val httpClient: OkHttpClient,
    private val cacheDir: File
) {

    suspend fun fetchLatestRelease(): VentoyRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/ventoy/Ventoy/releases/latest")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("GitHub API returned ${response.code}")
        }

        val body = response.body ?: throw IllegalStateException("Empty response body")
        val reader = JsonReader.of(body.source())

        var tagName = ""
        var publishedAt = ""
        var downloadUrl = ""
        var assetSize = 0L

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "tag_name" -> tagName = reader.nextString()
                "published_at" -> publishedAt = reader.nextString()
                "assets" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var name = ""
                        var url = ""
                        var size = 0L
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "name" -> name = reader.nextString()
                                "browser_download_url" -> url = reader.nextString()
                                "size" -> size = reader.nextLong()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if (name.contains("linux", ignoreCase = true) &&
                            name.endsWith(".tar.gz")
                        ) {
                            downloadUrl = url
                            assetSize = size
                        }
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        reader.close()

        val version = tagName.removePrefix("v")

        if (downloadUrl.isEmpty()) {
            throw IllegalStateException("No linux tar.gz asset found in release $tagName")
        }

        VentoyRelease(
            version = version,
            downloadUrl = downloadUrl,
            publishedAt = publishedAt,
            assetSize = assetSize
        )
    }

    fun install(
        release: VentoyRelease,
        streamer: UsbMassStorageStreamer,
        scheme: PartitionScheme
    ): Flow<VentoyInstallProgress> = flow {
        try {
            // Phase 1: Download
            emit(
                VentoyInstallProgress(
                    phase = VentoyPhase.DOWNLOADING,
                    progressPercent = 0f,
                    message = "Downloading Ventoy ${release.version}..."
                )
            )

            withContext(Dispatchers.IO) {
                downloadRelease(release)
            }

            emit(
                VentoyInstallProgress(
                    phase = VentoyPhase.DOWNLOADING,
                    progressPercent = 100f,
                    message = "Download complete"
                )
            )

            coroutineContext.ensureActive()

            // Phase 2: Partition
            emit(
                VentoyInstallProgress(
                    phase = VentoyPhase.PARTITIONING,
                    progressPercent = 0f,
                    message = "Creating ${scheme.name} partition table..."
                )
            )

            val blockSize = streamer.blockSize
            val totalSectors = streamer.totalSectors

            when (scheme) {
                PartitionScheme.MBR -> {
                    val mbr = buildMbrPartitionTable(totalSectors, blockSize)
                    withContext(Dispatchers.IO) {
                        streamer.writeSectors(0L, 1, mbr)
                    }
                }
                PartitionScheme.GPT -> {
                    val gptBuffers = buildGptLayout(totalSectors, blockSize)
                    val entrySectors = gptBuffers[2].size / blockSize
                    withContext(Dispatchers.IO) {
                        // Protective MBR at LBA 0
                        streamer.writeSectors(0L, 1, gptBuffers[0])
                        // GPT header at LBA 1
                        streamer.writeSectors(1L, 1, gptBuffers[1])
                        // Partition entries at LBA 2
                        streamer.writeSectors(2L, entrySectors, gptBuffers[2])
                        // Backup partition entries
                        val backupEntryLba = totalSectors - 1 - entrySectors
                        streamer.writeSectors(backupEntryLba, entrySectors, gptBuffers[3])
                        // Backup GPT header at last LBA
                        streamer.writeSectors(totalSectors - 1, 1, gptBuffers[4])
                    }
                }
            }

            emit(
                VentoyInstallProgress(
                    phase = VentoyPhase.PARTITIONING,
                    progressPercent = 100f,
                    message = "Partition table written"
                )
            )

            coroutineContext.ensureActive()

            // Phase 3: Write bootloader to Partition 2
            emit(
                VentoyInstallProgress(
                    phase = VentoyPhase.WRITING_BOOTLOADER,
                    progressPercent = 0f,
                    message = "Writing FAT16 boot partition..."
                )
            )

            val part2SizeSectors = (BOOT_PARTITION_SIZE_BYTES / blockSize).toLong()
            val part2StartLba: Long = when (scheme) {
                PartitionScheme.MBR -> totalSectors - part2SizeSectors
                PartitionScheme.GPT -> {
                    val entrySectors = (128 * 128) / blockSize
                    val usableEnd = totalSectors - 2 - entrySectors
                    usableEnd - part2SizeSectors + 1
                }
            }

            withContext(Dispatchers.IO) {
                writeFat16BootPartition(streamer, part2StartLba, part2SizeSectors)
            }

            emit(
                VentoyInstallProgress(
                    phase = VentoyPhase.WRITING_BOOTLOADER,
                    progressPercent = 100f,
                    message = "Bootloader partition written"
                )
            )

            emit(
                VentoyInstallProgress(
                    phase = VentoyPhase.COMPLETED,
                    progressPercent = 100f,
                    message = "Ventoy ${release.version} installed successfully"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(
                VentoyInstallProgress(
                    phase = VentoyPhase.FAILED,
                    progressPercent = 0f,
                    message = "Installation failed: ${e.message ?: "Unknown error"}"
                )
            )
        }
    }

    private fun downloadRelease(release: VentoyRelease): File {
        val fileName = release.downloadUrl.substringAfterLast('/')
        val outputFile = File(cacheDir, fileName)

        if (outputFile.exists() && outputFile.length() == release.assetSize) {
            return outputFile
        }

        val request = Request.Builder()
            .url(release.downloadUrl)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Download failed with code ${response.code}")
        }

        val responseBody = response.body ?: throw IllegalStateException("Empty download body")

        outputFile.sink().buffer().use { sink ->
            val source = responseBody.source()
            val bufferSize = 8192L
            while (true) {
                val read = source.read(sink.buffer, bufferSize)
                if (read == -1L) break
                sink.emitCompleteSegments()
            }
        }

        return outputFile
    }

    private fun buildMbrPartitionTable(totalSectors: Long, blockSize: Int): ByteArray {
        val mbr = ByteArray(blockSize)
        val buf = ByteBuffer.wrap(mbr).order(ByteOrder.LITTLE_ENDIAN)

        val part2SizeSectors = (BOOT_PARTITION_SIZE_BYTES / blockSize).toInt()
        val part2StartLba = (totalSectors - part2SizeSectors).toInt()

        val part1StartLba = PARTITION_ALIGNMENT_SECTORS
        val part1SizeSectors = part2StartLba - part1StartLba

        // Partition entry 1 at offset 0x1BE (446): exFAT data
        buf.position(0x1BE)
        buf.put(0x00.toByte())                          // Status: not bootable
        encodeChs(buf, part1StartLba.toLong())           // CHS of first sector
        buf.put(0x07.toByte())                          // Type: exFAT/NTFS/HPFS
        encodeChs(buf, (part1StartLba + part1SizeSectors - 1).toLong())
        buf.putInt(part1StartLba)                       // LBA of first sector
        buf.putInt(part1SizeSectors)                    // Total sectors

        // Partition entry 2 at offset 0x1CE (462): FAT16 VTOYEFI
        buf.position(0x1CE)
        buf.put(0x80.toByte())                          // Status: bootable
        encodeChs(buf, part2StartLba.toLong())           // CHS of first sector
        buf.put(0x0E.toByte())                          // Type: FAT16 LBA
        encodeChs(buf, (part2StartLba + part2SizeSectors - 1).toLong())
        buf.putInt(part2StartLba)                       // LBA of first sector
        buf.putInt(part2SizeSectors)                    // Total sectors

        // Boot signature
        mbr[0x1FE] = 0x55.toByte()
        mbr[0x1FF] = 0xAA.toByte()

        return mbr
    }

    private fun buildGptLayout(totalSectors: Long, blockSize: Int): List<ByteArray> {
        val diskGuid = randomGuidBytes()
        val part1Guid = randomGuidBytes()
        val part2Guid = randomGuidBytes()

        // Microsoft Basic Data: EBD0A0A2-B9E5-4433-87C0-68B6B72699C7
        val basicDataTypeGuid = guidStringToBytes("EBD0A0A2-B9E5-4433-87C0-68B6B72699C7")
        // EFI System Partition: C12A7328-F81F-11D2-BA4B-00A0C93EC93B
        val efiSystemTypeGuid = guidStringToBytes("C12A7328-F81F-11D2-BA4B-00A0C93EC93B")

        val entryCount = 128
        val entrySize = 128
        val entriesBytes = entryCount * entrySize
        val entrySectors = entriesBytes / blockSize

        // Usable LBA range: after primary GPT structures, before backup GPT structures
        val usableFirst = 2L + entrySectors
        val usableLast = totalSectors - 2 - entrySectors

        // Partition 2 (VTOYEFI): 32MB at end of usable area
        val part2SizeSectors = BOOT_PARTITION_SIZE_BYTES / blockSize
        val part2EndLba = usableLast
        val part2StartLba = part2EndLba - part2SizeSectors + 1

        // Partition 1 (exFAT data): rest of usable area
        val part1StartLba = usableFirst
        val part1EndLba = part2StartLba - 1

        // Build partition entries array
        val entries = ByteArray(entriesBytes)
        val eBuf = ByteBuffer.wrap(entries).order(ByteOrder.LITTLE_ENDIAN)

        // Entry 1: exFAT data partition
        eBuf.position(0)
        eBuf.put(basicDataTypeGuid)
        eBuf.put(part1Guid)
        eBuf.putLong(part1StartLba)
        eBuf.putLong(part1EndLba)
        eBuf.putLong(0)                                 // Attributes
        writeUtf16Le(eBuf, "Ventoy")

        // Entry 2: VTOYEFI boot partition
        eBuf.position(entrySize)
        eBuf.put(efiSystemTypeGuid)
        eBuf.put(part2Guid)
        eBuf.putLong(part2StartLba)
        eBuf.putLong(part2EndLba)
        eBuf.putLong(0)                                 // Attributes
        writeUtf16Le(eBuf, "VTOYEFI")

        val entriesCrc = crc32(entries)

        // Primary GPT header at LBA 1
        val primaryHeader = ByteArray(blockSize)
        val hBuf = ByteBuffer.wrap(primaryHeader).order(ByteOrder.LITTLE_ENDIAN)
        hBuf.position(0)
        hBuf.put("EFI PART".toByteArray(Charsets.US_ASCII))  // Signature (8 bytes)
        hBuf.putInt(0x00010000)                         // Revision 1.0
        hBuf.putInt(92)                                 // Header size
        hBuf.putInt(0)                                  // Header CRC32 (placeholder)
        hBuf.putInt(0)                                  // Reserved
        hBuf.putLong(1)                                 // My LBA
        hBuf.putLong(totalSectors - 1)                  // Alternate LBA
        hBuf.putLong(usableFirst)                       // First usable LBA
        hBuf.putLong(usableLast)                        // Last usable LBA
        hBuf.put(diskGuid)                              // Disk GUID (16 bytes)
        hBuf.putLong(2)                                 // Partition entry start LBA
        hBuf.putInt(entryCount)                         // Number of partition entries
        hBuf.putInt(entrySize)                          // Size of partition entry
        hBuf.putInt(entriesCrc)                         // Partition entries CRC32

        // Fill in header CRC32 (over first 92 bytes with CRC field zeroed)
        val headerCrc = crc32(primaryHeader, 0, 92)
        ByteBuffer.wrap(primaryHeader).order(ByteOrder.LITTLE_ENDIAN).putInt(16, headerCrc)

        // Backup GPT header at last LBA
        val backupHeader = primaryHeader.copyOf()
        val bBuf = ByteBuffer.wrap(backupHeader).order(ByteOrder.LITTLE_ENDIAN)
        bBuf.putLong(24, totalSectors - 1)              // My LBA
        bBuf.putLong(32, 1)                             // Alternate LBA
        bBuf.putLong(72, totalSectors - 1 - entrySectors) // Partition entry start LBA (backup)
        bBuf.putInt(16, 0)                              // Clear CRC
        val backupCrc = crc32(backupHeader, 0, 92)
        bBuf.putInt(16, backupCrc)

        // Protective MBR at LBA 0
        val protectiveMbr = ByteArray(blockSize)
        val mBuf = ByteBuffer.wrap(protectiveMbr).order(ByteOrder.LITTLE_ENDIAN)
        mBuf.position(0x1BE)
        mBuf.put(0x00.toByte())                         // Status: not bootable
        mBuf.put(0x00.toByte())                         // CHS start: head 0
        mBuf.put(0x02.toByte())                         // CHS start: sector 2, cylinder 0
        mBuf.put(0x00.toByte())                         // CHS start: cylinder low
        mBuf.put(0xEE.toByte())                         // Type: GPT protective
        mBuf.put(0xFF.toByte())                         // CHS end: head
        mBuf.put(0xFF.toByte())                         // CHS end: sector+cylinder
        mBuf.put(0xFF.toByte())                         // CHS end: cylinder
        mBuf.putInt(1)                                  // LBA start
        val protectiveSize = if (totalSectors - 1 > 0xFFFFFFFFL) {
            0xFFFFFFFF.toInt()
        } else {
            (totalSectors - 1).toInt()
        }
        mBuf.putInt(protectiveSize)                     // Partition size in sectors
        protectiveMbr[0x1FE] = 0x55.toByte()
        protectiveMbr[0x1FF] = 0xAA.toByte()

        val backupEntries = entries.copyOf()

        return listOf(protectiveMbr, primaryHeader, entries, backupEntries, backupHeader)
    }

    private fun writeFat16BootPartition(
        streamer: UsbMassStorageStreamer,
        startLba: Long,
        sizeInSectors: Long
    ) {
        val blockSize = streamer.blockSize
        val reservedSectors = 1
        val numberOfFats = 2
        val rootEntryCount = 512
        val sectorsPerCluster = 4
        val rootDirSectors = ((rootEntryCount * 32) + blockSize - 1) / blockSize

        // Calculate FAT size:
        // dataSectors = sizeInSectors - reservedSectors - rootDirSectors - 2*fatSize
        // totalClusters = dataSectors / sectorsPerCluster
        // fatSize = ceil(totalClusters * 2 / blockSize)
        // Solving: fatSize = ceil((sizeInSectors - reservedSectors - rootDirSectors) * 2
        //                       / (sectorsPerCluster * blockSize + 2 * numberOfFats))
        val numerator = (sizeInSectors - reservedSectors - rootDirSectors) * 2
        val denominator = sectorsPerCluster * blockSize + 2 * numberOfFats
        val fatSize = ((numerator + denominator - 1) / denominator).toInt()

        // FAT16 boot sector (BPB)
        val bootSector = ByteArray(blockSize)
        val bpb = ByteBuffer.wrap(bootSector).order(ByteOrder.LITTLE_ENDIAN)

        bpb.put(0xEB.toByte())                          // Jump
        bpb.put(0x3C.toByte())
        bpb.put(0x90.toByte())
        bpb.put("MSWIN4.1".toByteArray(Charsets.US_ASCII)) // OEM (8 bytes)
        bpb.putShort(blockSize.toShort())               // Bytes per sector
        bpb.put(sectorsPerCluster.toByte())             // Sectors per cluster
        bpb.putShort(reservedSectors.toShort())         // Reserved sectors
        bpb.put(numberOfFats.toByte())                  // Number of FATs
        bpb.putShort(rootEntryCount.toShort())          // Root directory entries
        if (sizeInSectors <= 0xFFFF) {
            bpb.putShort(sizeInSectors.toShort())       // Total sectors (16-bit)
        } else {
            bpb.putShort(0)
        }
        bpb.put(0xF8.toByte())                         // Media descriptor: hard disk
        bpb.putShort(fatSize.toShort())                 // Sectors per FAT
        bpb.putShort(32)                                // Sectors per track
        bpb.putShort(64)                                // Number of heads
        bpb.putInt(0)                                   // Hidden sectors
        if (sizeInSectors > 0xFFFF) {
            bpb.putInt(sizeInSectors.toInt())           // Total sectors (32-bit)
        } else {
            bpb.putInt(0)
        }

        // Extended BPB (FAT16)
        bpb.put(0x80.toByte())                          // Drive number
        bpb.put(0x00.toByte())                          // Reserved
        bpb.put(0x29.toByte())                          // Extended boot signature
        bpb.putInt(0x56544F59)                          // Volume serial ("VTOY")
        bpb.put("VTOYEFI    ".toByteArray(Charsets.US_ASCII)) // Volume label (11 bytes)
        bpb.put("FAT16   ".toByteArray(Charsets.US_ASCII))    // FS type (8 bytes)

        bootSector[0x1FE] = 0x55.toByte()
        bootSector[0x1FF] = 0xAA.toByte()

        streamer.writeSectors(startLba, 1, bootSector)

        // FAT tables
        val fatBytes = ByteArray(fatSize * blockSize)
        val fatBuf = ByteBuffer.wrap(fatBytes).order(ByteOrder.LITTLE_ENDIAN)
        fatBuf.putShort(0, 0xFFF8.toShort())            // Media descriptor
        fatBuf.putShort(2, 0xFFFF.toShort())            // End-of-chain marker

        val fat1Start = startLba + reservedSectors
        streamer.writeSectors(fat1Start, fatSize, fatBytes)

        val fat2Start = fat1Start + fatSize
        streamer.writeSectors(fat2Start, fatSize, fatBytes)

        // Root directory
        val rootDirStart = fat2Start + fatSize
        val rootDir = ByteArray(rootDirSectors * blockSize)
        val rdBuf = ByteBuffer.wrap(rootDir).order(ByteOrder.LITTLE_ENDIAN)
        rdBuf.put("VTOYEFI    ".toByteArray(Charsets.US_ASCII)) // Volume label (11 bytes)
        rdBuf.put(0x08.toByte())                        // Attribute: volume label

        streamer.writeSectors(rootDirStart, rootDirSectors, rootDir)
    }

    private fun encodeChs(buf: ByteBuffer, lba: Long) {
        if (lba > 16_450_559L) {
            // Beyond CHS range — use 0xFE/0xFF/0xFF for LBA-mode partitions > 8GB
            buf.put(0xFE.toByte())
            buf.put(0xFF.toByte())
            buf.put(0xFF.toByte())
        } else {
            val sectorsPerTrack = 63
            val heads = 255
            val sector = (lba % sectorsPerTrack + 1).toInt()
            val temp = lba / sectorsPerTrack
            val head = (temp % heads).toInt()
            val cylinder = (temp / heads).toInt()
            buf.put(head.toByte())
            buf.put(((sector and 0x3F) or ((cylinder shr 2) and 0xC0)).toByte())
            buf.put((cylinder and 0xFF).toByte())
        }
    }

    private fun randomGuidBytes(): ByteArray {
        val uuid = UUID.randomUUID()
        // GPT uses mixed-endian UUID: first 3 fields LE, last 2 fields BE
        val bb = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        val raw = bb.array()

        val result = ByteArray(16)
        // time_low (4 bytes): swap to LE
        result[0] = raw[3]; result[1] = raw[2]; result[2] = raw[1]; result[3] = raw[0]
        // time_mid (2 bytes): swap to LE
        result[4] = raw[5]; result[5] = raw[4]
        // time_hi_and_version (2 bytes): swap to LE
        result[6] = raw[7]; result[7] = raw[6]
        // clock_seq + node (8 bytes): big-endian as-is
        System.arraycopy(raw, 8, result, 8, 8)

        return result
    }

    private fun guidStringToBytes(guid: String): ByteArray {
        val parts = guid.split("-")
        val bytes = ByteArray(16)

        // time_low (4 bytes, LE)
        val timeLow = parts[0].toLong(16)
        bytes[0] = (timeLow and 0xFF).toByte()
        bytes[1] = ((timeLow shr 8) and 0xFF).toByte()
        bytes[2] = ((timeLow shr 16) and 0xFF).toByte()
        bytes[3] = ((timeLow shr 24) and 0xFF).toByte()

        // time_mid (2 bytes, LE)
        val timeMid = parts[1].toInt(16)
        bytes[4] = (timeMid and 0xFF).toByte()
        bytes[5] = ((timeMid shr 8) and 0xFF).toByte()

        // time_hi_and_version (2 bytes, LE)
        val timeHi = parts[2].toInt(16)
        bytes[6] = (timeHi and 0xFF).toByte()
        bytes[7] = ((timeHi shr 8) and 0xFF).toByte()

        // clock_seq (2 bytes, BE)
        val clockSeq = parts[3].toInt(16)
        bytes[8] = ((clockSeq shr 8) and 0xFF).toByte()
        bytes[9] = (clockSeq and 0xFF).toByte()

        // node (6 bytes, BE)
        val node = parts[4].toLong(16)
        bytes[10] = ((node shr 40) and 0xFF).toByte()
        bytes[11] = ((node shr 32) and 0xFF).toByte()
        bytes[12] = ((node shr 24) and 0xFF).toByte()
        bytes[13] = ((node shr 16) and 0xFF).toByte()
        bytes[14] = ((node shr 8) and 0xFF).toByte()
        bytes[15] = (node and 0xFF).toByte()

        return bytes
    }

    private fun writeUtf16Le(buf: ByteBuffer, name: String) {
        for (ch in name) {
            buf.putShort(ch.code.toShort())
        }
    }

    private fun crc32(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        val crc = CRC32()
        crc.update(data, offset, length)
        return crc.value.toInt()
    }

    companion object {
        private const val BOOT_PARTITION_SIZE_BYTES = 32L * 1024 * 1024 // 32MB
        private const val PARTITION_ALIGNMENT_SECTORS = 2048 // 1MB alignment
    }
}
