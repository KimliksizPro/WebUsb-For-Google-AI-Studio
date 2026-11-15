package com.example

import com.example.bridge.AdbPortManager
import com.example.bridge.AdbProtocolPacketizer
import com.example.bridge.WebUsbJsGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExampleUnitTest {

    @Test
    fun adbPacketizer_splitsCoalescedTcpStreamInto24ByteHeaderAndLittleEndianPayload() {
        val payloadBytes = "host::features=shell_v2,cmd\u0000".toByteArray(Charsets.UTF_8)
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("CNXN".toByteArray(Charsets.US_ASCII)) // 0..4 command
            putInt(0x01000000)                         // 4..8 arg0
            putInt(1048576)                            // 8..12 arg1
            putInt(payloadBytes.size)                  // 12..16 data_len (Little-Endian)
            putInt(12345)                              // 16..20 crc32
            putInt(-1)                                 // 20..24 magic
        }.array()

        // Simulate TCP merging the 24-byte header and payload into a single stream chunk
        val coalescedTcpBytes = header + payloadBytes
        val dataInput = DataInputStream(ByteArrayInputStream(coalescedTcpBytes))

        var emittedHeader: ByteArray? = null
        var emittedDataLen = -1
        var emittedCommand = ""
        var emittedPayload: ByteArray? = null

        AdbProtocolPacketizer.readAndSplitSingleAdbPacket(
            tcpInput = dataInput,
            onHeaderFrame = { hdr, len, cmd ->
                emittedHeader = hdr
                emittedDataLen = len
                emittedCommand = cmd
            },
            onPayloadFrame = { pay ->
                emittedPayload = pay
            }
        )

        assertEquals(24, emittedHeader?.size)
        assertArrayEquals(header, emittedHeader)
        assertEquals(payloadBytes.size, emittedDataLen)
        assertEquals("CNXN", emittedCommand)
        assertArrayEquals(payloadBytes, emittedPayload)
    }

    @Test
    fun adbPacketizer_handlesZeroLengthPayloadWithoutSecondFrame() {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("OKAY".toByteArray(Charsets.US_ASCII))
            putInt(1)
            putInt(2)
            putInt(0) // data_len = 0
            putInt(0)
            putInt(0)
        }.array()

        val dataInput = DataInputStream(ByteArrayInputStream(header))
        var headerCount = 0
        var payloadCount = 0

        AdbProtocolPacketizer.readAndSplitSingleAdbPacket(
            tcpInput = dataInput,
            onHeaderFrame = { hdr, len, cmd ->
                headerCount++
                assertEquals(24, hdr.size)
                assertEquals(0, len)
                assertEquals("OKAY", cmd)
            },
            onPayloadFrame = {
                payloadCount++
            }
        )

        assertEquals(1, headerCount)
        assertEquals(0, payloadCount)
    }

    @Test
    fun webUsbJsGenerator_injectsDynamicPortAndRequiredUsbSchema() {
        val js = WebUsbJsGenerator.buildPolyfillScript(8765)
        assertTrue(js.contains("ws://127.0.0.1:8765"))
        assertTrue(js.contains("vendorId: 0x18d1, productId: 0x4ee7"))
        assertTrue(js.contains("Object.defineProperty(navigator, \"usb\""))

        val customPortJs = WebUsbJsGenerator.buildPolyfillScript(9001)
        assertTrue(customPortJs.contains("ws://127.0.0.1:9001"))
    }

    @Test
    fun adbPortManager_buildsExactRootCommand() {
        val cmd = AdbPortManager.buildRootAdbCommand(5555)
        assertEquals(
            "setprop service.adb.tcp.port 5555; setprop persist.adb.tcp.port 5555; settings put global adb_enabled 0; sleep 1; settings put global adb_enabled 1; stop adbd; start adbd",
            cmd
        )
    }

    @Test
    fun adbPortManager_buildsDisableRootCommand() {
        val cmd = AdbPortManager.buildDisableRootAdbCommand()
        assertEquals(
            "setprop service.adb.tcp.port -1; setprop persist.adb.tcp.port -1; stop adbd; settings put global adb_enabled 0",
            cmd
        )
    }
}
