package com.example.bridge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogDirection {
    SYSTEM,
    WS_TO_TCP,
    TCP_TO_WS_HEADER,
    TCP_TO_WS_PAYLOAD,
    ERROR,
    ROOT
}

data class BridgeLogEntry(
    val id: Long,
    val timestamp: String,
    val direction: LogDirection,
    val tag: String,
    val message: String
)

data class BridgeSessionInfo(
    val id: String,
    val clientAddress: String,
    val targetAdbAddress: String,
    val connectedAt: String,
    val rxHeaders: Long = 0,
    val rxPayloads: Long = 0,
    val txFrames: Long = 0,
    val bytesRx: Long = 0,
    val bytesTx: Long = 0,
    val lastCommand: String = "IDLE"
)

data class BridgeRuntimeState(
    val isServiceRunning: Boolean = false,
    val wsHost: String = "127.0.0.1",
    val wsPort: Int = 8765,
    val adbHost: String = "127.0.0.1",
    val adbPort: Int = 5555,
    val activeClientsCount: Int = 0,
    val activeSessions: List<BridgeSessionInfo> = emptyList(),
    val bytesTxToAdb: Long = 0L,
    val bytesRxFromAdb: Long = 0L,
    val packetsRxHeaderCount: Long = 0L,
    val packetsRxPayloadCount: Long = 0L,
    val packetsTxCount: Long = 0L,
    val lastAdbCommand: String = "NONE",
    val lastError: String? = null,
    val logs: List<BridgeLogEntry> = emptyList()
)

/**
 * Thread-safe singleton state repository synchronizing WebUsbBridgeService (ForegroundService)
 * and the UI / ViewModel layer.
 */
object BridgeStateRepository {
    private const val MAX_LOGS = 250
    private val logIdGenerator = AtomicLong(1L)
    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _state = MutableStateFlow(BridgeRuntimeState())
    val state: StateFlow<BridgeRuntimeState> = _state.asStateFlow()

    @Synchronized
    private fun currentTime(): String = timeFormatter.format(Date())

    fun addLog(direction: LogDirection, tag: String, message: String) {
        val entry = BridgeLogEntry(
            id = logIdGenerator.getAndIncrement(),
            timestamp = currentTime(),
            direction = direction,
            tag = tag,
            message = message
        )
        _state.update { current ->
            val updatedLogs = (listOf(entry) + current.logs).take(MAX_LOGS)
            current.copy(logs = updatedLogs)
        }
    }

    fun clearLogs() {
        _state.update { it.copy(logs = emptyList()) }
    }

    fun updateServiceRunning(running: Boolean, wsPort: Int, adbPort: Int, error: String? = null) {
        _state.update {
            it.copy(
                isServiceRunning = running,
                wsPort = wsPort,
                adbPort = adbPort,
                lastError = error,
                activeClientsCount = if (!running) 0 else it.activeClientsCount,
                activeSessions = if (!running) emptyList() else it.activeSessions
            )
        }
    }

    fun updateConfiguredPorts(wsPort: Int, adbPort: Int) {
        _state.update {
            it.copy(wsPort = wsPort, adbPort = adbPort)
        }
    }

    fun onSessionOpened(session: BridgeSessionInfo) {
        _state.update { current ->
            val updated = current.activeSessions.filterNot { it.id == session.id } + session
            current.copy(
                activeSessions = updated,
                activeClientsCount = updated.size,
                lastError = null
            )
        }
    }

    fun onSessionClosed(sessionId: String) {
        _state.update { current ->
            val updated = current.activeSessions.filterNot { it.id == sessionId }
            current.copy(
                activeSessions = updated,
                activeClientsCount = updated.size
            )
        }
    }

    fun recordHeaderPacket(sessionId: String, commandName: String, dataLen: Int) {
        _state.update { current ->
            val updatedSessions = current.activeSessions.map { s ->
                if (s.id == sessionId) {
                    s.copy(
                        rxHeaders = s.rxHeaders + 1,
                        bytesRx = s.bytesRx + 24,
                        lastCommand = commandName
                    )
                } else s
            }
            current.copy(
                activeSessions = updatedSessions,
                bytesRxFromAdb = current.bytesRxFromAdb + 24,
                packetsRxHeaderCount = current.packetsRxHeaderCount + 1,
                lastAdbCommand = "$commandName (len=$dataLen)"
            )
        }
    }

    fun recordPayloadPacket(sessionId: String, payloadBytes: Int) {
        _state.update { current ->
            val updatedSessions = current.activeSessions.map { s ->
                if (s.id == sessionId) {
                    s.copy(
                        rxPayloads = s.rxPayloads + 1,
                        bytesRx = s.bytesRx + payloadBytes
                    )
                } else s
            }
            current.copy(
                activeSessions = updatedSessions,
                bytesRxFromAdb = current.bytesRxFromAdb + payloadBytes,
                packetsRxPayloadCount = current.packetsRxPayloadCount + 1
            )
        }
    }

    fun recordWsToTcpPacket(sessionId: String, byteCount: Int, commandHint: String?) {
        _state.update { current ->
            val updatedSessions = current.activeSessions.map { s ->
                if (s.id == sessionId) {
                    s.copy(
                        txFrames = s.txFrames + 1,
                        bytesTx = s.bytesTx + byteCount,
                        lastCommand = commandHint ?: s.lastCommand
                    )
                } else s
            }
            current.copy(
                activeSessions = updatedSessions,
                bytesTxToAdb = current.bytesTxToAdb + byteCount,
                packetsTxCount = current.packetsTxCount + 1,
                lastAdbCommand = commandHint ?: current.lastAdbCommand
            )
        }
    }

    fun resetCounters() {
        _state.update {
            it.copy(
                bytesTxToAdb = 0L,
                bytesRxFromAdb = 0L,
                packetsRxHeaderCount = 0L,
                packetsRxPayloadCount = 0L,
                packetsTxCount = 0L,
                lastAdbCommand = "NONE"
            )
        }
    }

    /**
     * Decodes the 4-byte ASCII command signature from an ADB 24-byte header
     * (e.g., CNXN, AUTH, OPEN, OKAY, WRTE, CLSE, SYNC, STLS).
     */
    fun decodeAdbCommandSignature(bytes: ByteArray): String {
        if (bytes.size < 4) return "RAW(${bytes.size}B)"
        val chars = CharArray(4)
        for (i in 0..3) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 65..90 || b in 48..57) {
                chars[i] = b.toChar()
            } else {
                return "BIN(${bytes.size}B)"
            }
        }
        return String(chars)
    }
}
