package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bridge.AdbListenStatus
import com.example.ui.MainUiState
import com.example.ui.components.BadgeTone
import com.example.ui.components.StatusBadge
import com.example.ui.components.TechnicalPanel
import com.example.ui.components.formatByteCount
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun DashboardScreen(
    uiState: MainUiState,
    onAdbPortChanged: (String) -> Unit,
    onAdbPortPreset: (Int) -> Unit,
    onWsPortChanged: (String) -> Unit,
    onProbePortNow: () -> Unit,
    onToggleAutoPoll: (Boolean) -> Unit,
    onEnableRootAdb: () -> Unit,
    onDisableRootAdb: () -> Unit,
    onCheckRoot: () -> Unit,
    onToggleBridgeService: () -> Unit,
    onShutdownEverything: () -> Unit,
    onShutdownAndExit: () -> Unit,
    onShowBanner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    var showPortSettings by remember { mutableStateOf(false) }

    val isBridgeRunning = uiState.bridgeState.isServiceRunning
    val isAdbOpen = uiState.adbProbeResult.status == AdbListenStatus.LISTENING
    val accentRed = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. KÖPRÜ DURUMU VE ANA KONTROL
            TechnicalPanel(
                title = "Köprü Durumu",
                borderColor = if (isBridgeRunning && isAdbOpen) {
                    accentRed.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                headerTrailing = {
                    StatusBadge(
                        text = when {
                            isBridgeRunning && isAdbOpen -> "Hazır & Aktif"
                            isBridgeRunning -> "Köprü Açık"
                            isAdbOpen -> "ADB Açık"
                            else -> "Kapalı"
                        },
                        tone = when {
                            isBridgeRunning && isAdbOpen -> BadgeTone.SUCCESS
                            isBridgeRunning || isAdbOpen -> BadgeTone.WARNING
                            else -> BadgeTone.NEUTRAL
                        },
                        modifier = Modifier.testTag("bridge_service_status_badge")
                    )
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MinimalStatusTile(
                        label = "WebSocket",
                        port = uiState.activeWsPort.toString(),
                        status = if (isBridgeRunning) "Açık" else "Kapalı",
                        isActive = isBridgeRunning,
                        modifier = Modifier.weight(1f)
                    )
                    MinimalStatusTile(
                        label = "ADB TCP",
                        port = uiState.activeAdbPort.toString(),
                        status = when (uiState.adbProbeResult.status) {
                            AdbListenStatus.LISTENING -> "Açık"
                            AdbListenStatus.CLOSED -> "Kapalı"
                            AdbListenStatus.CHECKING -> "Kontrol..."
                        },
                        isActive = isAdbOpen,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("adb_port_status_badge")
                    )
                }

                if (isBridgeRunning) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Bağlı istemci: ${uiState.bridgeState.activeClientsCount}  •  Veri: ${
                            formatByteCount(uiState.bridgeState.bytesRxFromAdb + uiState.bridgeState.bytesTxToAdb)
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (uiState.bridgeState.lastError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.bridgeState.lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = accentRed
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onToggleBridgeService,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("toggle_bridge_service_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBridgeRunning) MaterialTheme.colorScheme.surfaceVariant else accentRed,
                        contentColor = if (isBridgeRunning) MaterialTheme.colorScheme.onSurface else Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (isBridgeRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBridgeRunning) "Köprüyü Durdur" else "Köprüyü Başlat",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onShutdownEverything,
                        enabled = !uiState.isShuttingDownAll,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("shutdown_all_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, accentRed.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentRed)
                    ) {
                        if (uiState.isShuttingDownAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = accentRed
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isShuttingDownAll) "Kapatılıyor..." else "Komple Kapat",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    OutlinedButton(
                        onClick = onShutdownAndExit,
                        enabled = !uiState.isShuttingDownAll,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("shutdown_and_exit_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = "Çık",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. ADB & PORT AYARLARI
            TechnicalPanel(
                title = "ADB & Portlar",
                headerTrailing = {
                    val (rootText, rootTone) = when (uiState.isRootAvailable) {
                        true -> "Root Hazır" to BadgeTone.SUCCESS
                        false -> "Root Yok" to BadgeTone.NEUTRAL
                        null -> "Kontrol..." to BadgeTone.WARNING
                    }
                    StatusBadge(
                        text = rootText,
                        tone = rootTone,
                        modifier = Modifier
                            .clickable { onCheckRoot() }
                            .testTag("root_status_badge")
                    )
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onEnableRootAdb,
                        enabled = !uiState.isRunningRootCommand,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("enable_root_adb_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Root ADB Aç",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    OutlinedButton(
                        onClick = onDisableRootAdb,
                        enabled = !uiState.isRunningRootCommand,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("disable_root_adb_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, accentRed.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentRed)
                    ) {
                        Text(
                            text = "Root ADB Kapat",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                if (uiState.lastRootCommandOutput != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.lastRootCommandOutput,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.lastRootCommandSuccess == true) accentRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showPortSettings = !showPortSettings }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Port Ayarları (WS ${uiState.activeWsPort} • ADB ${uiState.activeAdbPort})",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (showPortSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Port Ayarlarını Aç/Kapat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = showPortSettings) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = uiState.wsPortInput,
                                onValueChange = onWsPortChanged,
                                enabled = !isBridgeRunning,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ws_port_input"),
                                singleLine = true,
                                label = { Text("Köprü Portu") },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedTextField(
                                value = uiState.adbPortInput,
                                onValueChange = onAdbPortChanged,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("adb_port_input"),
                                singleLine = true,
                                label = { Text("ADB Portu") },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(5555, 5556).forEach { preset ->
                                    val selected = uiState.activeAdbPort == preset
                                    OutlinedButton(
                                        onClick = { onAdbPortPreset(preset) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) accentRed else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier.testTag("adb_preset_$preset")
                                    ) {
                                        Text(
                                            text = "$preset",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily
                                            ),
                                            color = if (selected) accentRed else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = onProbePortNow,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("probe_adb_port_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Kontrol Et",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Kontrol",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Oto",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Switch(
                                    checked = uiState.autoPollAdb,
                                    onCheckedChange = onToggleAutoPoll,
                                    modifier = Modifier.testTag("auto_poll_switch")
                                )
                            }
                        }
                    }
                }
            }

            // 3. TEK DOKUNUŞLA BOOKMARKLET KOPYALAMA
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Chrome / Brave Kodu",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "WebUSB v2 Bookmarklet (ws://127.0.0.1:${uiState.activeWsPort})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(uiState.bookmarkletCode))
                            onShowBanner("WebUSB v2 bookmarklet kodu kopyalandı!")
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, accentRed.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentRed),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("dashboard_copy_bookmarklet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Kopyala",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalStatusTile(
    label: String,
    port: String,
    status: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val dotColor = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = ":$port",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
