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
        assertTrue(js.contains("alert(\"WebUSB v2 Aktif!\");"))

        val customPortJs = WebUsbJsGenerator.buildPolyfillScript(9001)
        assertTrue(customPortJs.contains("ws://127.0.0.1:9001"))

        val expectedBookmarklet =
            """javascript:(function(){let ws=null;let q=[];let w=[];const epIn={endpointNumber:1,direction:"in",type:"bulk",packetSize:512};const epOut={endpointNumber:2,direction:"out",type:"bulk",packetSize:512};const alt={alternateSetting:0,interfaceClass:255,interfaceSubclass:66,interfaceProtocol:1,interfaceName:"ADB Interface",endpoints:[epIn,epOut]};const iface={interfaceNumber:0,alternate:alt,alternates:[alt],claimed:true};const conf={configurationValue:1,configurationName:"ADB Config",interfaces:[iface]};const fakeDev={usbVersionMajor:2,usbVersionMinor:0,usbVersionSubminor:0,deviceClass:0,deviceSubclass:0,deviceProtocol:0,vendorId:0x18d1,productId:0x4ee7,deviceVersionMajor:1,deviceVersionMinor:0,deviceVersionSubminor:0,manufacturerName:"Android",productName:"Loopback ADB",serialNumber:"LOCALHOST",configuration:conf,configurations:[conf],opened:false,open:async()=>{fakeDev.opened=true;if(ws&&ws.readyState===1)return;ws=new WebSocket("ws://127.0.0.1:8765");ws.binaryType="arraybuffer";ws.onmessage=(e)=>{if(w.length>0){w.shift()(e.data);}else{q.push(e.data);}};return new Promise((res,rej)=>{ws.onopen=res;ws.onerror=rej;});},selectConfiguration:async()=>{},claimInterface:async()=>{iface.claimed=true;},selectAlternateInterface:async()=>{},releaseInterface:async()=>{},clearHalt:async()=>{},reset:async()=>{},close:async()=>{fakeDev.opened=false;if(ws)ws.close();},controlTransferIn:async()=>({data:new DataView(new ArrayBuffer(0)),status:"ok"}),controlTransferOut:async()=>({bytesWritten:0,status:"ok"}),transferOut:async(ep,data)=>{let buf=data instanceof ArrayBuffer?data:new Uint8Array(data.buffer,data.byteOffset,data.byteLength);ws.send(buf);return{bytesWritten:buf.byteLength,status:"ok"};},transferIn:async(ep,len)=>{let buf=q.length>0?q.shift():await new Promise(r=>w.push(r));return{data:new DataView(buf),status:"ok"};}};Object.defineProperty(navigator,"usb",{value:{getDevices:async()=>[fakeDev],requestDevice:async()=>fakeDev,addEventListener:()=>{},removeEventListener:()=>{}},configurable:true});alert("WebUSB v2 Aktif!");})();"""
        assertEquals(expectedBookmarklet, WebUsbJsGenerator.buildBookmarklet(8765))
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
