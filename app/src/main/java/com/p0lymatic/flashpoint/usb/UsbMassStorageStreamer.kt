package com.p0lymatic.flashpoint.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UsbMassStorageStreamer(
    private val usbManager: UsbManager,
    private val device: UsbDevice
) {
    private lateinit var connection: UsbDeviceConnection
    private lateinit var bulkIn: UsbEndpoint
    private lateinit var bulkOut: UsbEndpoint
    private var tag: Int = 0
    var blockSize: Int = 512
        private set
    var lastLba: Long = 0
        private set
    val totalSectors: Long get() = lastLba + 1

    fun open() {
        val conn = usbManager.openDevice(device)
            ?: throw IllegalStateException("Cannot open USB device connection")
        connection = conn

        var foundInterface = false
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == MASS_STORAGE_CLASS &&
                iface.interfaceSubclass == SCSI_SUBCLASS &&
                iface.interfaceProtocol == BOT_PROTOCOL
            ) {
                if (!connection.claimInterface(iface, true)) {
                    throw IllegalStateException("Cannot claim USB mass storage interface")
                }
                for (e in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(e)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) {
                            bulkIn = ep
                        } else {
                            bulkOut = ep
                        }
                    }
                }
                foundInterface = true
                break
            }
        }

        if (!foundInterface) {
            connection.close()
            throw IllegalStateException("No mass storage interface found on device")
        }
        if (!::bulkIn.isInitialized || !::bulkOut.isInitialized) {
            connection.close()
            throw IllegalStateException("Bulk IN/OUT endpoints not found")
        }
    }

    fun close() {
        if (::connection.isInitialized) {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == MASS_STORAGE_CLASS) {
                    connection.releaseInterface(iface)
                    break
                }
            }
            connection.close()
        }
    }

    private fun sendCbw(cdb: ByteArray, transferLength: Int, directionIn: Boolean): Int {
        val currentTag = ++tag
        val cbw = ByteArray(31)
        val buf = ByteBuffer.wrap(cbw).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0, CBW_SIGNATURE)
        buf.putInt(4, currentTag)
        buf.putInt(8, transferLength)
        cbw[12] = if (directionIn) 0x80.toByte() else 0x00
        cbw[13] = 0 // LUN
        cbw[14] = cdb.size.toByte()
        System.arraycopy(cdb, 0, cbw, 15, cdb.size)

        val sent = connection.bulkTransfer(bulkOut, cbw, cbw.size, USB_TIMEOUT)
        if (sent != cbw.size) {
            throw UsbTransferException("CBW transfer failed: sent $sent of ${cbw.size} bytes")
        }
        return currentTag
    }

    private fun readCsw(expectedTag: Int): CswResult {
        val csw = ByteArray(13)
        val received = connection.bulkTransfer(bulkIn, csw, csw.size, USB_TIMEOUT)
        if (received != 13) {
            throw UsbTransferException("CSW transfer failed: received $received of 13 bytes")
        }

        val buf = ByteBuffer.wrap(csw).order(ByteOrder.LITTLE_ENDIAN)
        val signature = buf.getInt(0)
        if (signature != CSW_SIGNATURE) {
            throw UsbProtocolException(
                "Invalid CSW signature: 0x${Integer.toHexString(signature)}"
            )
        }

        val cswTag = buf.getInt(4)
        if (cswTag != expectedTag) {
            throw UsbProtocolException("CSW tag mismatch: expected $expectedTag, got $cswTag")
        }

        val dataResidue = buf.getInt(8)
        val status = csw[12].toInt() and 0xFF

        if (status == 2) {
            throw UsbProtocolException("Phase error reported in CSW")
        }

        return CswResult(cswTag, dataResidue, status)
    }

    private fun sendCommand(
        cdb: ByteArray,
        dataTransferLength: Int,
        dataIn: Boolean
    ): ByteArray? {
        val sentTag = sendCbw(cdb, dataTransferLength, dataIn)

        var data: ByteArray? = null
        if (dataTransferLength > 0) {
            if (dataIn) {
                data = ByteArray(dataTransferLength)
                var offset = 0
                var remaining = dataTransferLength
                while (remaining > 0) {
                    val chunkSize = remaining.coerceAtMost(16384)
                    val chunk = ByteArray(chunkSize)
                    val read = connection.bulkTransfer(bulkIn, chunk, chunkSize, USB_TIMEOUT)
                    if (read < 0) {
                        throw UsbTransferException("Data IN transfer failed at offset $offset")
                    }
                    System.arraycopy(chunk, 0, data, offset, read)
                    offset += read
                    remaining -= read
                    if (read < chunkSize) break
                }
            } else {
                throw IllegalArgumentException(
                    "Use writeSectors for data OUT transfers"
                )
            }
        }

        val csw = readCsw(sentTag)
        if (csw.status != 0) {
            throw UsbCommandException("SCSI command failed with status ${csw.status}")
        }

        return data
    }

    fun testUnitReady(): Boolean {
        val cdb = ByteArray(6) // opcode 0x00
        val sentTag = sendCbw(cdb, 0, false)
        val csw = readCsw(sentTag)
        return csw.status == 0
    }

    fun inquiry(): DeviceInfo {
        val cdb = ByteArray(6)
        cdb[0] = 0x12 // INQUIRY opcode
        cdb[4] = 36   // allocation length

        val data = sendCommand(cdb, 36, dataIn = true)
            ?: throw UsbCommandException("INQUIRY returned no data")

        val vendor = String(data, 8, 8).trim()
        val product = String(data, 16, 16).trim()
        val revision = String(data, 32, 4).trim()

        return DeviceInfo(vendor, product, revision)
    }

    fun readCapacity10(): Pair<Long, Int> {
        val cdb = ByteArray(10)
        cdb[0] = 0x25.toByte() // READ CAPACITY (10)

        val data = sendCommand(cdb, 8, dataIn = true)
            ?: throw UsbCommandException("READ CAPACITY (10) returned no data")

        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val returnedLba = buf.getInt(0).toLong() and 0xFFFFFFFFL
        val returnedBlockSize = buf.getInt(4)

        lastLba = returnedLba
        blockSize = returnedBlockSize

        return Pair(returnedLba, returnedBlockSize)
    }

    fun readCapacity16(): Pair<Long, Int> {
        val cdb = ByteArray(16)
        cdb[0] = 0x9E.toByte() // SERVICE ACTION IN (16)
        cdb[1] = 0x10          // SAI READ CAPACITY (16)
        // Allocation length at offset 10-13
        val allocLen = 32
        ByteBuffer.wrap(cdb).order(ByteOrder.BIG_ENDIAN).putInt(10, allocLen)

        val data = sendCommand(cdb, allocLen, dataIn = true)
            ?: throw UsbCommandException("READ CAPACITY (16) returned no data")

        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val returnedLba = buf.getLong(0)
        val returnedBlockSize = buf.getInt(8)

        lastLba = returnedLba
        blockSize = returnedBlockSize

        return Pair(returnedLba, returnedBlockSize)
    }

    fun requestSense(): SenseData {
        val cdb = ByteArray(6)
        cdb[0] = 0x03 // REQUEST SENSE
        cdb[4] = 18   // allocation length

        val data = sendCommand(cdb, 18, dataIn = true)
            ?: throw UsbCommandException("REQUEST SENSE returned no data")

        val senseKey = data[2].toInt() and 0x0F
        val asc = data[12].toInt() and 0xFF
        val ascq = data[13].toInt() and 0xFF

        return SenseData(senseKey, asc, ascq)
    }

    fun writeSectors(lba: Long, sectorCount: Int, data: ByteArray) {
        require(data.size == sectorCount * blockSize) {
            "Data size ${data.size} != sectorCount $sectorCount * blockSize $blockSize"
        }

        val cdb: ByteArray
        if (lba < 0x100000000L && sectorCount <= 0xFFFF) {
            // WRITE(10) — opcode 0x2A
            cdb = ByteArray(10)
            cdb[0] = 0x2A.toByte()
            ByteBuffer.wrap(cdb).order(ByteOrder.BIG_ENDIAN).apply {
                putInt(2, lba.toInt())
                putShort(7, sectorCount.toShort())
            }
        } else {
            // WRITE(16) — opcode 0x8A
            cdb = ByteArray(16)
            cdb[0] = 0x8A.toByte()
            ByteBuffer.wrap(cdb).order(ByteOrder.BIG_ENDIAN).apply {
                putLong(2, lba)
                putInt(10, sectorCount)
            }
        }

        val transferLength = data.size
        val sentTag = sendCbw(cdb, transferLength, directionIn = false)

        // Send data in chunks
        var offset = 0
        var remaining = transferLength
        while (remaining > 0) {
            val chunkSize = remaining.coerceAtMost(16384)
            val sent = connection.bulkTransfer(
                bulkOut, data, offset, chunkSize, WRITE_TIMEOUT
            )
            if (sent < 0) {
                throw UsbTransferException("Data OUT transfer failed at offset $offset")
            }
            offset += sent
            remaining -= sent
        }

        val csw = readCsw(sentTag)
        if (csw.status != 0) {
            throw UsbCommandException(
                "WRITE command failed at LBA $lba with status ${csw.status}"
            )
        }
    }

    fun getCapacity(): Pair<Long, Int> {
        val (lba10, bs10) = readCapacity10()
        return if (lba10 == 0xFFFFFFFFL) {
            readCapacity16()
        } else {
            Pair(lba10, bs10)
        }
    }

    data class DeviceInfo(val vendor: String, val product: String, val revision: String)
    data class SenseData(val senseKey: Int, val asc: Int, val ascq: Int)
    private data class CswResult(val tag: Int, val dataResidue: Int, val status: Int)

    companion object {
        const val MASS_STORAGE_CLASS = 8
        const val SCSI_SUBCLASS = 6
        const val BOT_PROTOCOL = 80
        const val CBW_SIGNATURE = 0x43425355
        const val CSW_SIGNATURE = 0x53425355
        const val USB_TIMEOUT = 5000
        const val WRITE_TIMEOUT = 30000
    }
}

class UsbTransferException(message: String) : RuntimeException(message)
class UsbProtocolException(message: String) : RuntimeException(message)
class UsbCommandException(message: String) : RuntimeException(message)
