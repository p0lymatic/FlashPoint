package com.p0lymatic.flashpoint.core

import android.content.ContentResolver
import android.net.Uri
import com.p0lymatic.flashpoint.usb.UsbMassStorageStreamer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class BurnProgress(
    val bytesWritten: Long,
    val totalBytes: Long,
    val bytesPerSecond: Double,
    val etaSeconds: Long,
    val sha256Hex: String,
    val phase: BurnPhase,
    val errorMessage: String? = null
)

enum class BurnPhase { PREPARING, WRITING, VERIFYING, COMPLETED, CANCELLED, FAILED }

class RawImageBurner(
    private val contentResolver: ContentResolver,
    private val streamer: UsbMassStorageStreamer
) {

    fun burn(imageUri: Uri, totalBytes: Long): Flow<BurnProgress> = flow {
        emit(
            BurnProgress(
                bytesWritten = 0,
                totalBytes = totalBytes,
                bytesPerSecond = 0.0,
                etaSeconds = 0,
                sha256Hex = "",
                phase = BurnPhase.PREPARING
            )
        )

        var inputStream: InputStream? = null
        var bytesWrittenSoFar = 0L
        var firstSectorSig55: Int? = null
        var firstSectorSigAA: Int? = null
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(CHUNK_SIZE)
            val blockSize = streamer.blockSize
            val startTime = System.nanoTime()

            inputStream = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(imageUri)
                    ?: throw IllegalArgumentException("Cannot open input stream for URI: $imageUri")
            }

            while (true) {
                coroutineContext.ensureActive()

                val bytesRead = withContext(Dispatchers.IO) {
                    inputStream.read(buffer)
                }
                if (bytesRead == -1) break

                if (bytesWrittenSoFar == 0L && bytesRead >= 512) {
                    firstSectorSig55 = buffer[510].toInt() and 0xFF
                    firstSectorSigAA = buffer[511].toInt() and 0xFF
                }

                digest.update(buffer, 0, bytesRead)
                val dataToWrite: ByteArray
                val writeLen: Int
                if (bytesRead % blockSize != 0) {
                    val aligned = ((bytesRead + blockSize - 1) / blockSize) * blockSize
                    val padded = ByteArray(aligned)
                    System.arraycopy(buffer, 0, padded, 0, bytesRead)
                    dataToWrite = padded
                    writeLen = aligned
                } else if (bytesRead < buffer.size) {
                    dataToWrite = buffer.copyOfRange(0, bytesRead)
                    writeLen = bytesRead
                } else {
                    dataToWrite = buffer
                    writeLen = bytesRead
                }

                val startLba = bytesWrittenSoFar / blockSize
                val sectorCount = writeLen / blockSize

                withContext(Dispatchers.IO) {
                    streamer.writeSectors(startLba, sectorCount, dataToWrite)
                }

                bytesWrittenSoFar += bytesRead

                val elapsedNanos = System.nanoTime() - startTime
                val elapsedSeconds = elapsedNanos / 1_000_000_000.0
                val speed = if (elapsedSeconds > 0.0) bytesWrittenSoFar / elapsedSeconds else 0.0
                val remaining = totalBytes - bytesWrittenSoFar
                val eta = if (speed > 0.0) (remaining / speed).toLong() else 0L

                // Clone digest to get intermediate hash without resetting accumulator state
                val intermediateDigest = digest.clone() as MessageDigest
                val intermediateHash = bytesToHex(intermediateDigest.digest())

                emit(
                    BurnProgress(
                        bytesWritten = bytesWrittenSoFar,
                        totalBytes = totalBytes,
                        bytesPerSecond = speed,
                        etaSeconds = eta,
                        sha256Hex = intermediateHash,
                        phase = BurnPhase.WRITING
                    )
                )
            }

            val finalHash = bytesToHex(digest.digest())

            emit(
                BurnProgress(
                    bytesWritten = bytesWrittenSoFar,
                    totalBytes = totalBytes,
                    bytesPerSecond = 0.0,
                    etaSeconds = 0,
                    sha256Hex = finalHash,
                    phase = BurnPhase.VERIFYING
                )
            )

            coroutineContext.ensureActive()

            withContext(Dispatchers.IO) {
                val mbr = streamer.readSectors(0L, 1)
                if (mbr.size < 512) {
                    throw IllegalStateException("Read MBR returned incomplete sector: ${mbr.size} bytes")
                }
                val sig55 = mbr[510].toInt() and 0xFF
                val sigAA = mbr[511].toInt() and 0xFF
                val expected55 = firstSectorSig55 ?: 0x55
                val expectedAA = firstSectorSigAA ?: 0xAA
                if (sig55 != expected55 || sigAA != expectedAA) {
                    throw IllegalStateException(
                        "Invalid MBR boot signature at offset 510..511: expected 0x%02X 0x%02X, found 0x%02X 0x%02X".format(expected55, expectedAA, sig55, sigAA)
                    )
                }
            }

            emit(
                BurnProgress(
                    bytesWritten = bytesWrittenSoFar,
                    totalBytes = totalBytes,
                    bytesPerSecond = 0.0,
                    etaSeconds = 0,
                    sha256Hex = finalHash,
                    phase = BurnPhase.COMPLETED
                )
            )
        } catch (e: CancellationException) {
            emit(
                BurnProgress(
                    bytesWritten = bytesWrittenSoFar,
                    totalBytes = totalBytes,
                    bytesPerSecond = 0.0,
                    etaSeconds = 0,
                    sha256Hex = "",
                    phase = BurnPhase.CANCELLED
                )
            )
            throw e
        } catch (e: Exception) {
            emit(
                BurnProgress(
                    bytesWritten = bytesWrittenSoFar,
                    totalBytes = totalBytes,
                    bytesPerSecond = 0.0,
                    etaSeconds = 0,
                    sha256Hex = "",
                    phase = BurnPhase.FAILED,
                    errorMessage = e.message ?: "Unknown write error"
                )
            )
        } finally {
            try {
                withContext(Dispatchers.IO) {
                    inputStream?.close()
                }
            } catch (_: Exception) {
                // Ignore close errors
            }
        }
    }

    companion object {
        const val CHUNK_SIZE = 128 * 1024

        private fun bytesToHex(bytes: ByteArray): String {
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        }
    }
}
