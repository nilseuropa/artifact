package com.nils.artifact.label

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.util.Locale
import java.util.UUID
import kotlin.math.min

data class NiimbotDevice(
    val name: String,
    val address: String,
    val isLikelyNiimbot: Boolean,
) {
    val displayName: String
        get() = name.ifBlank { "Bluetooth printer" }
}

object NiimbotPrinter {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private const val COMMAND_HEARTBEAT = 0xDC
    private const val COMMAND_SET_DENSITY = 0x21
    private const val COMMAND_SET_LABEL_TYPE = 0x23
    private const val COMMAND_PRINT_START = 0x01
    private const val COMMAND_PAGE_START = 0x03
    private const val COMMAND_SET_PAGE_SIZE = 0x13
    private const val COMMAND_PRINT_BITMAP_ROW = 0x85
    private const val COMMAND_PRINT_EMPTY_ROW = 0x84
    private const val COMMAND_PAGE_END = 0xE3
    private const val COMMAND_PRINT_STATUS = 0xA3
    private const val COMMAND_PRINT_END = 0xF3
    private const val RESPONSE_PRINT_STATUS = 0xB3

    private const val DEFAULT_DENSITY = 3
    private const val DEFAULT_LABEL_TYPE = 1
    private const val DEFAULT_COPIES = 1
    private const val PACKET_DELAY_MS = 10L
    private const val STATUS_POLL_MS = 12_000L
    private const val READ_POLL_MS = 20L

    fun needsBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun hasBluetoothConnectPermission(context: Context): Boolean =
        !needsBluetoothConnectPermission() ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<NiimbotDevice> {
        ensureConnectPermission(context)
        val adapter = bluetoothAdapter(context)
            ?: throw IOException("Bluetooth is not available on this phone.")
        if (!adapter.isEnabled) {
            throw IOException("Bluetooth is turned off.")
        }

        return adapter.bondedDevices
            .map { device ->
                val name = device.name.orEmpty()
                NiimbotDevice(
                    name = name,
                    address = device.address,
                    isLikelyNiimbot = isLikelyNiimbot(name),
                )
            }
            .sortedWith(
                compareByDescending<NiimbotDevice> { it.isLikelyNiimbot }
                    .thenBy { it.displayName.lowercase(Locale.getDefault()) }
                    .thenBy { it.address },
            )
    }

    suspend fun printLabel(
        context: Context,
        device: NiimbotDevice,
        bitmap: Bitmap,
        density: Int = DEFAULT_DENSITY,
        labelType: Int = DEFAULT_LABEL_TYPE,
        copies: Int = DEFAULT_COPIES,
    ) {
        withContext(Dispatchers.IO) {
            ensureConnectPermission(context)
            require(bitmap.width == ArtifactLabelRenderer.WIDTH) {
                "NIIMBOT B1 label width must be ${ArtifactLabelRenderer.WIDTH}px."
            }
            require(bitmap.height == ArtifactLabelRenderer.HEIGHT) {
                "Label height must be ${ArtifactLabelRenderer.HEIGHT}px."
            }
            require(density in 1..5) {
                "Density must be in range 1..5."
            }
            require(copies in 1..255) {
                "Copies must be in range 1..255."
            }

            val bluetoothDevice = findBondedDevice(context, device.address)
            val socket = connect(bluetoothDevice)
            try {
                val output = socket.outputStream
                val input = socket.inputStream

                send(output, COMMAND_HEARTBEAT, byteArrayOf(0x01))
                send(output, COMMAND_SET_DENSITY, byteArrayOf(density.toByte()))
                send(output, COMMAND_SET_LABEL_TYPE, byteArrayOf(labelType.toByte()))
                send(output, COMMAND_PRINT_START, byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00))
                send(output, COMMAND_PAGE_START, byteArrayOf(0x01))
                send(
                    output,
                    COMMAND_SET_PAGE_SIZE,
                    unsignedShortsPayload(bitmap.height, bitmap.width, copies),
                )
                writeRows(output, bitmap)
                send(output, COMMAND_PAGE_END, byteArrayOf(0x01))
                pollStatus(output, input)
                send(output, COMMAND_PRINT_END, byteArrayOf(0x01))
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun findBondedDevice(context: Context, address: String): BluetoothDevice {
        val adapter = bluetoothAdapter(context)
            ?: throw IOException("Bluetooth is not available on this phone.")
        return adapter.bondedDevices.firstOrNull { device -> device.address == address }
            ?: throw IOException("The selected printer is no longer paired.")
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice): BluetoothSocket {
        val factories = listOf<() -> BluetoothSocket>(
            { device.createRfcommSocketToServiceRecord(sppUuid) },
            { device.createInsecureRfcommSocketToServiceRecord(sppUuid) },
            { createChannelOneSocket(device) },
        )

        var lastError: Throwable? = null
        factories.forEach { factory ->
            val socket = runCatching { factory() }
                .onFailure { lastError = it }
                .getOrNull()
                ?: return@forEach
            try {
                socket.connect()
                return socket
            } catch (error: IOException) {
                lastError = error
                runCatching { socket.close() }
            }
        }

        throw IOException("Could not connect to ${device.name ?: device.address}.", lastError)
    }

    private fun writeRows(output: OutputStream, bitmap: Bitmap) {
        val bytesPerRow = (bitmap.width + 7) / 8
        for (row in 0 until bitmap.height) {
            val rowBytes = ByteArray(bytesPerRow)
            var hasBlack = false
            for (col in 0 until bitmap.width) {
                if (isBlack(bitmap.getPixel(col, row))) {
                    hasBlack = true
                    rowBytes[col / 8] = (rowBytes[col / 8].toInt() or (0x80 ushr (col % 8))).toByte()
                }
            }

            if (hasBlack) {
                send(output, COMMAND_PRINT_BITMAP_ROW, bitmapRowPayload(row, rowBytes))
            } else {
                send(output, COMMAND_PRINT_EMPTY_ROW, byteArrayOf((row ushr 8).toByte(), row.toByte(), 0x01))
            }
        }
    }

    private fun bitmapRowPayload(row: Int, rowBytes: ByteArray): ByteArray {
        val countSegment = blackCountSegment(rowBytes)
        return ByteArray(2 + countSegment.size + 1 + rowBytes.size).also { payload ->
            payload[0] = (row ushr 8).toByte()
            payload[1] = row.toByte()
            countSegment.copyInto(payload, destinationOffset = 2)
            payload[5] = 0x01
            rowBytes.copyInto(payload, destinationOffset = 6)
        }
    }

    private fun blackCountSegment(rowBytes: ByteArray): ByteArray {
        val chunkSize = rowBytes.size / 3
        val chunks = listOf(
            rowBytes.copyOfRange(0, chunkSize),
            rowBytes.copyOfRange(chunkSize, chunkSize * 2),
            rowBytes.copyOfRange(chunkSize * 2, rowBytes.size),
        )
        return chunks
            .map { chunk -> chunk.sumOf { byte -> Integer.bitCount(byte.toInt() and 0xff) }.toByte() }
            .toByteArray()
    }

    private fun pollStatus(output: OutputStream, input: InputStream) {
        val deadline = SystemClock.elapsedRealtime() + STATUS_POLL_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            send(output, COMMAND_PRINT_STATUS, byteArrayOf(0x01))
            val pollDeadline = min(deadline, SystemClock.elapsedRealtime() + 1_000L)
            while (SystemClock.elapsedRealtime() < pollDeadline) {
                val packet = readPacket(input, pollDeadline) ?: break
                if (packet.command != RESPONSE_PRINT_STATUS) continue
                val status = packet.payload.lastOrNull()?.toInt()?.and(0xff)
                if (status in setOf(0x01, 0x02, 0x03)) {
                    return
                }
            }
            Thread.sleep(400L)
        }
    }

    private fun send(output: OutputStream, command: Int, payload: ByteArray) {
        output.write(buildPacket(command, payload))
        output.flush()
        Thread.sleep(PACKET_DELAY_MS)
    }

    private fun buildPacket(command: Int, payload: ByteArray): ByteArray {
        require(payload.size <= 255) {
            "Payload too large for NIIMBOT command 0x${command.toString(16)}."
        }
        var checksum = command xor payload.size
        payload.forEach { byte -> checksum = checksum xor (byte.toInt() and 0xff) }
        return ByteArray(2 + 1 + 1 + payload.size + 1 + 2).also { packet ->
            packet[0] = 0x55
            packet[1] = 0x55
            packet[2] = command.toByte()
            packet[3] = payload.size.toByte()
            payload.copyInto(packet, destinationOffset = 4)
            packet[4 + payload.size] = checksum.toByte()
            packet[5 + payload.size] = 0xaa.toByte()
            packet[6 + payload.size] = 0xaa.toByte()
        }
    }

    private fun readPacket(input: InputStream, deadline: Long): PrinterPacket? {
        var foundFirstHeaderByte = false
        while (SystemClock.elapsedRealtime() < deadline) {
            val byte = readByte(input, deadline) ?: return null
            if (!foundFirstHeaderByte) {
                foundFirstHeaderByte = byte == 0x55
            } else if (byte == 0x55) {
                val command = readByte(input, deadline) ?: return null
                val payloadLength = readByte(input, deadline) ?: return null
                val payload = ByteArray(payloadLength)
                for (index in payload.indices) {
                    payload[index] = (readByte(input, deadline) ?: return null).toByte()
                }
                val checksum = readByte(input, deadline) ?: return null
                val tailOne = readByte(input, deadline) ?: return null
                val tailTwo = readByte(input, deadline) ?: return null
                if (tailOne != 0xaa || tailTwo != 0xaa) return null

                var expected = command xor payloadLength
                payload.forEach { payloadByte -> expected = expected xor (payloadByte.toInt() and 0xff) }
                if ((expected and 0xff) != checksum) {
                    throw IOException("Bad checksum in NIIMBOT response.")
                }
                return PrinterPacket(command = command, payload = payload)
            } else {
                foundFirstHeaderByte = byte == 0x55
            }
        }
        return null
    }

    private fun readByte(input: InputStream, deadline: Long): Int? {
        while (SystemClock.elapsedRealtime() < deadline) {
            if (input.available() > 0) {
                val value = input.read()
                if (value < 0) throw IOException("Printer closed the Bluetooth connection.")
                return value and 0xff
            }
            Thread.sleep(READ_POLL_MS)
        }
        return null
    }

    private fun unsignedShortsPayload(vararg values: Int): ByteArray =
        ByteArray(values.size * 2).also { payload ->
            values.forEachIndexed { index, value ->
                payload[index * 2] = (value ushr 8).toByte()
                payload[index * 2 + 1] = value.toByte()
            }
        }

    private fun isBlack(pixel: Int): Boolean {
        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)
        return (red + green + blue) / 3 < 160
    }

    private fun bluetoothAdapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private fun ensureConnectPermission(context: Context) {
        if (!hasBluetoothConnectPermission(context)) {
            throw SecurityException("Bluetooth permission is required to print to the paired NIIMBOT.")
        }
    }

    private fun isLikelyNiimbot(name: String): Boolean {
        val normalized = name.uppercase(Locale.getDefault())
        return "NIIMBOT" in normalized || "B1" in normalized
    }

    @SuppressLint("MissingPermission")
    private fun createChannelOneSocket(device: BluetoothDevice): BluetoothSocket {
        try {
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            return method.invoke(device, 1) as BluetoothSocket
        } catch (error: InvocationTargetException) {
            throw IOException("Could not open RFCOMM channel 1.", error.targetException)
        } catch (error: ReflectiveOperationException) {
            throw IOException("Could not open RFCOMM channel 1.", error)
        }
    }

    private data class PrinterPacket(
        val command: Int,
        val payload: ByteArray,
    )
}
