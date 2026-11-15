package com.example.bridge

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Core ADB Protocol Packetizer logic and WebSocket <-> TCP Bridge Server.
 *
 * WebUSB ADB libraries (ya-webadb, Tango, WebADB) perform two distinct `transferIn` calls
 * per ADB message:
 *   1) Read exact 24-byte ADB Header
 *   2) Read exact `data_len` bytes of ADB Payload (where `data_len` is stored at bytes [12..16] Little-Endian)
 *
 * Because raw TCP merges stream chunks arbitrarily, this engine reads from the local ADB TCP
 * socket using `readFully` semantics and emits separate Binary WebSocket frames for the 24-byte
 * header and the `data_len` payload.
 */
object AdbProtocolPacketizer {

    const val ADB_HEADER_LENGTH = 24
    const val ADB_MAX_SAFE_PAYLOAD = 16 * 1024 * 1024 // 16 MB guard against corrupt frames

    /**
     * Reads exactly [length] bytes from [input] into a new ByteArray using DataInputStream.readFully semantics.
     * Throws [EOFException] if the TCP stream closes before all bytes are read.
     */
    fun readFully(input: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read == -1) {
                throw EOFException("TCP akışı beklenmedik şekilde kapandı ($offset / $length bayt okundu)")
            }
            offset += read
        }
        return buffer
    }

    /**
     * Extracts `data_len` from indices 12..16 (4 bytes) of the 24-byte ADB header in Little-Endian order.
     */
    fun extractPayloadLengthLittleEndian(header24: ByteArray): Int {
        require(header24.size >= 16) { "ADB başlığı en az 16 bayt olmalıdır (mevcut: ${header24.size})" }
        return ByteBuffer.wrap(header24, 12, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
    }

    /**
     * Reads one complete ADB message from [tcpInput] and dispatches:
     *   1) Exact 24-byte Header frame via [onHeaderFrame]
     *   2) If `data_len > 0`, exact `data_len`-byte Payload frame via [onPayloadFrame]
     */
    fun readAndSplitSingleAdbPacket(
        tcpInput: DataInputStream,
        onHeaderFrame: (headerBytes: ByteArray, dataLen: Int, commandName: String) -> Unit,
        onPayloadFrame: (payloadBytes: ByteArray) -> Unit
    ) {
        val header = ByteArray(ADB_HEADER_LENGTH)
        tcpInput.readFully(header)

        val dataLen = ByteBuffer.wrap(header, 12, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int

        val commandName = BridgeStateRepository.decodeAdbCommandSignature(header)
        onHeaderFrame(header, dataLen, commandName)

        if (dataLen in 1..ADB_MAX_SAFE_PAYLOAD) {
            val payload = ByteArray(dataLen)
            tcpInput.readFully(payload)
            onPayloadFrame(payload)
        } else if (dataLen < 0 || dataLen > ADB_MAX_SAFE_PAYLOAD) {
            throw IllegalStateException("Geçersiz ADB payload uzunluğu (data_len=$dataLen)")
        }
    }
}

class ClientBridgeSession(
    val sessionId: String,
    val webSocket: WebSocket,
    val adbHost: String,
    val adbPort: Int
) {
    @Volatile
    var tcpSocket: Socket? = null

    @Volatile
    var tcpOutput: OutputStream? = null

    private val closed = AtomicBoolean(false)
    private val writeLock = Any()

    fun writeToTcp(bytes: ByteArray) {
        if (closed.get()) return
        synchronized(writeLock) {
            val out = tcpOutput ?: throw IllegalStateException("ADB TCP soketi henüz hazır değil")
            out.write(bytes)
            out.flush()
        }
    }

    fun closeSafely(reason: String) {
        if (closed.compareAndSet(false, true)) {
            try {
                tcpOutput?.close()
            } catch (_: Exception) {
            }
            try {
                tcpSocket?.close()
            } catch (_: Exception) {
            }
            try {
                if (webSocket.isOpen) {
                    webSocket.close(1000, reason)
                }
            } catch (_: Exception) {
            }
            BridgeStateRepository.onSessionClosed(sessionId)
            BridgeStateRepository.addLog(
                LogDirection.SYSTEM,
                "SESSION#$sessionId",
                "Bağlantı güvenli şekilde kapatıldı ($reason) - zombi soket temizlendi."
            )
        }
    }

    fun isClosed(): Boolean = closed.get()
}

class AdbPacketizingWebSocketServer(
    private val wsHost: String = "127.0.0.1",
    private val wsPort: Int = 8765,
    @Volatile var targetAdbPort: Int = 5555,
    private val onActiveClientCountChanged: () -> Unit = {}
) : WebSocketServer(InetSocketAddress(wsHost, wsPort)) {

    private val sessionMap = ConcurrentHashMap<WebSocket, ClientBridgeSession>()
    private val sessionCounter = AtomicInteger(1)
    private val readerExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    init {
        isReuseAddr = true
        isTcpNoDelay = true
        connectionLostTimeout = 0 // Disable ping timeout disconnect during long ADB flashes
    }

    override fun onStart() {
        BridgeStateRepository.updateServiceRunning(
            running = true,
            wsPort = wsPort,
            adbPort = targetAdbPort,
            error = null
        )
        BridgeStateRepository.addLog(
            LogDirection.SYSTEM,
            "WS-SERVER",
            "Dinleniyor: ws://$wsHost:$wsPort -> Hedef ADB: 127.0.0.1:$targetAdbPort"
        )
        onActiveClientCountChanged()
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val sessionId = "S${sessionCounter.getAndIncrement()}"
        val remoteAddr = conn.remoteSocketAddress?.toString() ?: "127.0.0.1"
        val currentAdbPort = targetAdbPort
        val session = ClientBridgeSession(
            sessionId = sessionId,
            webSocket = conn,
            adbHost = "127.0.0.1",
            adbPort = currentAdbPort
        )
        sessionMap[conn] = session

        try {
            // Connect to local adbd TCP port synchronously so immediate transferOut frames succeed
            val socket = Socket()
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.reuseAddress = true
            socket.connect(InetSocketAddress("127.0.0.1", currentAdbPort), 3000)

            session.tcpSocket = socket
            session.tcpOutput = socket.getOutputStream()

            val sessionInfo = BridgeSessionInfo(
                id = sessionId,
                clientAddress = remoteAddr,
                targetAdbAddress = "127.0.0.1:$currentAdbPort",
                connectedAt = synchronized(timeFormatter) { timeFormatter.format(Date()) }
            )
            BridgeStateRepository.onSessionOpened(sessionInfo)
            BridgeStateRepository.addLog(
                LogDirection.SYSTEM,
                "SESSION#$sessionId",
                "WebUSB istemcisi bağlandı ($remoteAddr) -> TCP 127.0.0.1:$currentAdbPort açıldı."
            )
            onActiveClientCountChanged()

            // Start dedicated TCP -> WebSocket 24-byte header + Little-Endian payload reader loop
            readerExecutor.execute {
                runTcpToWebSocketLoop(session, socket)
            }
        } catch (e: Exception) {
            BridgeStateRepository.addLog(
                LogDirection.ERROR,
                "SESSION#$sessionId",
                "ADB TCP 127.0.0.1:$currentAdbPort bağlantısı açılamadı: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
            sessionMap.remove(conn)
            session.closeSafely("ADB TCP port $currentAdbPort unreachable")
            onActiveClientCountChanged()
        }
    }

    private fun runTcpToWebSocketLoop(session: ClientBridgeSession, socket: Socket) {
        val sessionId = session.sessionId
        try {
            val dataInput = DataInputStream(socket.getInputStream())
            while (!session.isClosed() && !socket.isClosed && session.webSocket.isOpen) {
                AdbProtocolPacketizer.readAndSplitSingleAdbPacket(
                    tcpInput = dataInput,
                    onHeaderFrame = { headerBytes, dataLen, commandName ->
                        if (session.webSocket.isOpen) {
                            // 1) Send exact 24-byte ADB header as a single Binary WebSocket message
                            session.webSocket.send(headerBytes)
                            BridgeStateRepository.recordHeaderPacket(sessionId, commandName, dataLen)
                            BridgeStateRepository.addLog(
                                LogDirection.TCP_TO_WS_HEADER,
                                "RX-HDR#$sessionId",
                                "24B Başlık -> WS | Komut: $commandName | data_len(LE): $dataLen bayt"
                            )
                        }
                    },
                    onPayloadFrame = { payloadBytes ->
                        if (session.webSocket.isOpen) {
                            // 2) Send exact data_len payload as a second Binary WebSocket message
                            session.webSocket.send(payloadBytes)
                            BridgeStateRepository.recordPayloadPacket(sessionId, payloadBytes.size)
                            BridgeStateRepository.addLog(
                                LogDirection.TCP_TO_WS_PAYLOAD,
                                "RX-PAY#$sessionId",
                                "${payloadBytes.size}B Gövde (Payload) -> WS gönderildi"
                            )
                        }
                    }
                )
            }
        } catch (eof: EOFException) {
            if (!session.isClosed()) {
                BridgeStateRepository.addLog(
                    LogDirection.SYSTEM,
                    "SESSION#$sessionId",
                    "ADB TCP akışı sonlandı (EOF)."
                )
            }
        } catch (e: Exception) {
            if (!session.isClosed()) {
                BridgeStateRepository.addLog(
                    LogDirection.ERROR,
                    "SESSION#$sessionId",
                    "TCP -> WS okuma hatası: ${e.localizedMessage ?: e.javaClass.simpleName}"
                )
            }
        } finally {
            sessionMap.remove(session.webSocket)
            session.closeSafely("TCP stream ended")
            onActiveClientCountChanged()
        }
    }

    override fun onMessage(conn: WebSocket, message: String) {
        // WebUSB sends Binary ArrayBuffers, but if a text frame is sent, forward its UTF-8 bytes
        val session = sessionMap[conn] ?: return
        try {
            val bytes = message.toByteArray(Charsets.UTF_8)
            session.writeToTcp(bytes)
            BridgeStateRepository.recordWsToTcpPacket(session.sessionId, bytes.size, "TXT(${bytes.size}B)")
        } catch (e: Exception) {
            BridgeStateRepository.addLog(
                LogDirection.ERROR,
                "TX-ERR#${session.sessionId}",
                "WS -> TCP yazma hatası: ${e.localizedMessage}"
            )
            sessionMap.remove(conn)
            session.closeSafely("TCP write failure")
            onActiveClientCountChanged()
        }
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {
        val session = sessionMap[conn] ?: return
        try {
            val bytes = ByteArray(message.remaining())
            message.get(bytes)

            // Write directly to TCP OutputStream and flush()
            session.writeToTcp(bytes)

            val cmdHint = if (bytes.size == 24) {
                val cmd = BridgeStateRepository.decodeAdbCommandSignature(bytes)
                val len = AdbProtocolPacketizer.extractPayloadLengthLittleEndian(bytes)
                "$cmd (tx_len=$len)"
            } else {
                null
            }
            BridgeStateRepository.recordWsToTcpPacket(session.sessionId, bytes.size, cmdHint)
            BridgeStateRepository.addLog(
                LogDirection.WS_TO_TCP,
                "TX-TCP#${session.sessionId}",
                "WS -> TCP ${bytes.size}B yazıldı & flush()" + (if (cmdHint != null) " [$cmdHint]" else "")
            )
        } catch (e: Exception) {
            BridgeStateRepository.addLog(
                LogDirection.ERROR,
                "TX-ERR#${session.sessionId}",
                "WS -> TCP yazma hatası: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
            sessionMap.remove(conn)
            session.closeSafely("TCP write error")
            onActiveClientCountChanged()
        }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        val session = sessionMap.remove(conn)
        if (session != null) {
            val who = if (remote) "İstemci" else "Sunucu"
            session.closeSafely("$who kapattı (kod=$code)")
            onActiveClientCountChanged()
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        if (conn != null) {
            val session = sessionMap.remove(conn)
            session?.closeSafely("WS Hatası: ${ex.localizedMessage ?: ex.javaClass.simpleName}")
            onActiveClientCountChanged()
        } else {
            val errMsg = ex.localizedMessage ?: ex.javaClass.simpleName
            BridgeStateRepository.addLog(
                LogDirection.ERROR,
                "WS-SERVER",
                "Sunucu hatası (Port $wsPort): $errMsg"
            )
            BridgeStateRepository.updateServiceRunning(
                running = false,
                wsPort = wsPort,
                adbPort = targetAdbPort,
                error = errMsg
            )
        }
    }

    fun shutdownAllSessionsAndStop() {
        val active = sessionMap.values.toList()
        sessionMap.clear()
        for (session in active) {
            session.closeSafely("Servis durduruldu")
        }
        try {
            readerExecutor.shutdownNow()
        } catch (_: Exception) {
        }
        try {
            stop(500)
        } catch (_: Exception) {
        }
        BridgeStateRepository.updateServiceRunning(
            running = false,
            wsPort = wsPort,
            adbPort = targetAdbPort,
            error = null
        )
    }
}
