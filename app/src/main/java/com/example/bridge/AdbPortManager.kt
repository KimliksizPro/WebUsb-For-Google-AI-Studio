package com.example.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

enum class AdbListenStatus {
    CHECKING,
    LISTENING,
    CLOSED
}

data class AdbProbeResult(
    val host: String,
    val port: Int,
    val status: AdbListenStatus,
    val latencyMs: Long = -1L,
    val errorDetail: String? = null
)

data class RootCommandResult(
    val success: Boolean,
    val command: String,
    val exitCode: Int,
    val output: String
)

/**
 * Manages ADB TCP Loopback port detection (Rootless / Manual mode) and
 * Root-assisted one-click ADB over TCP activation and complete deactivation (`su -c ...`).
 */
object AdbPortManager {

    const val EXACT_ROOT_ADB_COMMAND_5555 =
        "su -c \"setprop service.adb.tcp.port 5555; setprop persist.adb.tcp.port 5555; settings put global adb_enabled 0; sleep 1; settings put global adb_enabled 1; stop adbd; start adbd\""

    fun buildRootAdbCommand(port: Int = 5555): String {
        return "setprop service.adb.tcp.port $port; setprop persist.adb.tcp.port $port; settings put global adb_enabled 0; sleep 1; settings put global adb_enabled 1; stop adbd; start adbd"
    }

    fun buildDisableRootAdbCommand(): String {
        return "setprop service.adb.tcp.port -1; setprop persist.adb.tcp.port -1; stop adbd; settings put global adb_enabled 0"
    }

    /**
     * Checks whether the device has active `su` (root) access.
     */
    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(2500, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext false
            }
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            process.exitValue() == 0 && output.contains("uid=0")
        } catch (_: Exception) {
            false
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Executes the Root ADB activation sequence:
     * su -c "setprop service.adb.tcp.port 5555; setprop persist.adb.tcp.port 5555; settings put global adb_enabled 0; sleep 1; settings put global adb_enabled 1; stop adbd; start adbd"
     */
    suspend fun enableAdbTcpWithRoot(port: Int = 5555): RootCommandResult = withContext(Dispatchers.IO) {
        val shellPayload = buildRootAdbCommand(port)
        val fullDisplayCommand = "su -c \"$shellPayload\""
        var process: Process? = null
        try {
            process = ProcessBuilder("su", "-c", shellPayload)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(8000, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext RootCommandResult(
                    success = false,
                    command = fullDisplayCommand,
                    exitCode = -1,
                    output = "Zaman aşımı: su komutu 8 saniye içinde yanıt vermedi."
                )
            }
            val output = BufferedReader(InputStreamReader(process.inputStream))
                .use { it.readText().trim() }
            val code = process.exitValue()
            RootCommandResult(
                success = code == 0,
                command = fullDisplayCommand,
                exitCode = code,
                output = if (output.isEmpty()) "ADB TCP ($port) başarıyla açıldı." else output
            )
        } catch (e: Exception) {
            RootCommandResult(
                success = false,
                command = fullDisplayCommand,
                exitCode = -1,
                output = "Root hatası: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Completely disables ADB over TCP and stops the adbd daemon via Root:
     * su -c "setprop service.adb.tcp.port -1; setprop persist.adb.tcp.port -1; stop adbd; settings put global adb_enabled 0"
     */
    suspend fun disableAdbTcpWithRoot(): RootCommandResult = withContext(Dispatchers.IO) {
        val shellPayload = buildDisableRootAdbCommand()
        val fullDisplayCommand = "su -c \"$shellPayload\""
        var process: Process? = null
        try {
            process = ProcessBuilder("su", "-c", shellPayload)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(6000, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext RootCommandResult(
                    success = false,
                    command = fullDisplayCommand,
                    exitCode = -1,
                    output = "Zaman aşımı: Root ADB kapatma komutu yanıt vermedi."
                )
            }
            val output = BufferedReader(InputStreamReader(process.inputStream))
                .use { it.readText().trim() }
            val code = process.exitValue()
            RootCommandResult(
                success = code == 0,
                command = fullDisplayCommand,
                exitCode = code,
                output = if (output.isEmpty()) "Root ADB ve TCP portu tamamen kapatıldı." else output
            )
        } catch (e: Exception) {
            RootCommandResult(
                success = false,
                command = fullDisplayCommand,
                exitCode = -1,
                output = "Root ADB kapatılamadı: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Probes `Socket("127.0.0.1", port)` to verify whether the target ADB TCP port is in LISTEN state.
     * Immediately closes the probe socket so no zombie sessions remain on `adbd`.
     */
    suspend fun probeAdbPort(host: String = "127.0.0.1", port: Int = 5555, timeoutMs: Int = 600): AdbProbeResult =
        withContext(Dispatchers.IO) {
            if (port !in 1..65535) {
                return@withContext AdbProbeResult(
                    host = host,
                    port = port,
                    status = AdbListenStatus.CLOSED,
                    errorDetail = "Geçersiz port aralığı (1-65535)"
                )
            }
            val startNs = System.nanoTime()
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.reuseAddress = true
                socket.setSoLinger(false, 0)
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                AdbProbeResult(
                    host = host,
                    port = port,
                    status = AdbListenStatus.LISTENING,
                    latencyMs = elapsedMs
                )
            } catch (e: Exception) {
                AdbProbeResult(
                    host = host,
                    port = port,
                    status = AdbListenStatus.CLOSED,
                    errorDetail = e.localizedMessage ?: "Kapalı"
                )
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
}
