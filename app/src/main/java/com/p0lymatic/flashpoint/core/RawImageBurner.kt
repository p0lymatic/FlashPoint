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
    val phase: BurnPhase
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
                    phase = BurnPhase.FAILED
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
