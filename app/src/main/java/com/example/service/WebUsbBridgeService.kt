package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.bridge.AdbPacketizingWebSocketServer
import com.example.bridge.AdbPortManager
import com.example.bridge.BridgeStateRepository
import com.example.bridge.LogDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Persistent ForegroundService running the WebSocket (ws://127.0.0.1:8765) <-> ADB TCP (127.0.0.1:5555)
 * packetizing bridge so the user can either use the built-in WebView browser OR switch to an external
 * browser (Brave / Chrome) without Android killing the localhost bridge.
 */
class WebUsbBridgeService : Service() {

    companion object {
        const val ACTION_START_BRIDGE = "com.example.action.START_BRIDGE"
        const val ACTION_STOP_BRIDGE = "com.example.action.STOP_BRIDGE"
        const val ACTION_SHUTDOWN_ALL = "com.example.action.SHUTDOWN_ALL"
        const val ACTION_UPDATE_ADB_PORT = "com.example.action.UPDATE_ADB_PORT"

        const val EXTRA_WS_PORT = "extra_ws_port"
        const val EXTRA_ADB_PORT = "extra_adb_port"

        private const val NOTIFICATION_CHANNEL_ID = "webusb_local_bridge_channel"
        private const val NOTIFICATION_ID = 8765

        fun startService(context: Context, wsPort: Int = 8765, adbPort: Int = 5555) {
            val intent = Intent(context, WebUsbBridgeService::class.java).apply {
                action = ACTION_START_BRIDGE
                putExtra(EXTRA_WS_PORT, wsPort)
                putExtra(EXTRA_ADB_PORT, adbPort)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WebUsbBridgeService::class.java).apply {
                action = ACTION_STOP_BRIDGE
            }
            context.startService(intent)
        }

        fun updateTargetAdbPort(context: Context, adbPort: Int) {
            val intent = Intent(context, WebUsbBridgeService::class.java).apply {
                action = ACTION_UPDATE_ADB_PORT
                putExtra(EXTRA_ADB_PORT, adbPort)
            }
            context.startService(intent)
        }
    }

    private var wsServer: AdbPacketizingWebSocketServer? = null
    private var currentWsPort: Int = 8765
    private var currentAdbPort: Int = 5555

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_BRIDGE -> {
                stopBridgeInternal()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_SHUTDOWN_ALL -> {
                stopBridgeInternal()
                CoroutineScope(Dispatchers.IO).launch {
                    val result = AdbPortManager.disableAdbTcpWithRoot()
                    BridgeStateRepository.addLog(
                        if (result.success) LogDirection.ROOT else LogDirection.SYSTEM,
                        "SHUTDOWN",
                        result.output
                    )
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_UPDATE_ADB_PORT -> {
                val newAdbPort = intent.getIntExtra(EXTRA_ADB_PORT, currentAdbPort)
                currentAdbPort = newAdbPort
                wsServer?.targetAdbPort = newAdbPort
                BridgeStateRepository.updateConfiguredPorts(currentWsPort, currentAdbPort)
                BridgeStateRepository.addLog(
                    LogDirection.SYSTEM,
                    "SERVICE",
                    "Hedef ADB portu güncellendi: 127.0.0.1:$currentAdbPort"
                )
                updateNotification()
                return START_STICKY
            }

            ACTION_START_BRIDGE, null -> {
                val requestedWsPort = intent?.getIntExtra(EXTRA_WS_PORT, 8765) ?: 8765
                val requestedAdbPort = intent?.getIntExtra(EXTRA_ADB_PORT, 5555) ?: 5555
                startForegroundWithNotification(requestedWsPort, requestedAdbPort)
                startBridgeInternal(requestedWsPort, requestedAdbPort)
                return START_STICKY
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification(wsPort: Int, adbPort: Int) {
        val notification = buildNotification(wsPort, adbPort, BridgeStateRepository.state.value.activeClientsCount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Synchronized
    private fun startBridgeInternal(wsPort: Int, adbPort: Int) {
        if (wsServer != null && currentWsPort == wsPort && BridgeStateRepository.state.value.isServiceRunning) {
            currentAdbPort = adbPort
            wsServer?.targetAdbPort = adbPort
            BridgeStateRepository.updateConfiguredPorts(wsPort, adbPort)
            updateNotification()
            return
        }

        stopBridgeInternal()

        currentWsPort = wsPort
        currentAdbPort = adbPort

        try {
            val server = AdbPacketizingWebSocketServer(
                wsHost = "127.0.0.1",
                wsPort = wsPort,
                targetAdbPort = adbPort,
                onActiveClientCountChanged = { updateNotification() }
            )
            wsServer = server
            server.start()
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "WebSocket sunucusu başlatılamadı"
            BridgeStateRepository.addLog(LogDirection.ERROR, "SERVICE", msg)
            BridgeStateRepository.updateServiceRunning(
                running = false,
                wsPort = wsPort,
                adbPort = adbPort,
                error = msg
            )
        }
    }

    @Synchronized
    private fun stopBridgeInternal() {
        val existing = wsServer
        wsServer = null
        if (existing != null) {
            Thread {
                existing.shutdownAllSessionsAndStop()
            }.start()
            BridgeStateRepository.addLog(
                LogDirection.SYSTEM,
                "SERVICE",
                "Köprü servisi durduruldu."
            )
        } else {
            BridgeStateRepository.updateServiceRunning(
                running = false,
                wsPort = currentWsPort,
                adbPort = currentAdbPort,
                error = null
            )
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val activeClients = BridgeStateRepository.state.value.activeClientsCount
        nm.notify(NOTIFICATION_ID, buildNotification(currentWsPort, currentAdbPort, activeClients))
    }

    private fun buildNotification(wsPort: Int, adbPort: Int, activeClients: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WebUsbBridgeService::class.java).apply {
            action = ACTION_STOP_BRIDGE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val shutdownAllIntent = Intent(this, WebUsbBridgeService::class.java).apply {
            action = ACTION_SHUTDOWN_ALL
        }
        val shutdownAllPendingIntent = PendingIntent.getService(
            this,
            2,
            shutdownAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = "Köprü Aktif ($wsPort -> $adbPort) • Bağlı: $activeClients"

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.notification_stop_action),
                stopPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Komple Kapat",
                shutdownAllPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "WebUSB Local Bridge bildirim kanalı"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopBridgeInternal()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
