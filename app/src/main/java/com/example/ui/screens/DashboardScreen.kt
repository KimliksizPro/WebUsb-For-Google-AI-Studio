package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.ui.AppTab
import com.example.ui.MainUiState
import com.example.ui.QuickWebUsbTarget
import com.example.ui.components.BadgeTone
import com.example.ui.components.StatusBadge
import com.example.ui.components.TechnicalPanel
import com.example.ui.components.formatByteCount
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalRed

@Composable
fun DashboardScreen(
    uiState: MainUiState,
    quickTargets: List<QuickWebUsbTarget>,
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
    onResetCounters: () -> Unit,
    onOpenUrlInEmbeddedBrowser: (String) -> Unit,
    onNavigateToTab: (AppTab) -> Unit,
    onShowBanner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    var showAdvancedSettings by remember { mutableStateOf(false) }

    val isBridgeRunning = uiState.bridgeState.isServiceRunning
    val isAdbOpen = uiState.adbProbeResult.status == AdbListenStatus.LISTENING

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. ANA DURUM VE TEK TIKLA KONTROL KARTI
        TechnicalPanel(
            title = "Bağlantı Durumu",
            borderColor = if (isBridgeRunning && isAdbOpen) {
                EmeraldSignal.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
            headerTrailing = {
                StatusBadge(
                    text = when {
                        isBridgeRunning && isAdbOpen -> "Hazır & Aktif"
                        isBridgeRunning -> "Köprü Açık"
                        isAdbOpen -> "Sadece ADB Açık"
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
            // Simple 2-column status summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SimpleStatusCard(
                    title = "Köprü Servisi",
                    statusText = if (isBridgeRunning) "Açık (${uiState.activeWsPort})" else "Kapalı",
                    isActive = isBridgeRunning,
                    modifier = Modifier.weight(1f)
                )
                SimpleStatusCard(
                    title = "ADB Portu (${uiState.activeAdbPort})",
                    statusText = when (uiState.adbProbeResult.status) {
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
                    text = "Bağlı Tarayıcı: ${uiState.bridgeState.activeClientsCount}  •  Aktarılan Veri: ${formatByteCount(uiState.bridgeState.bytesRxFromAdb + uiState.bridgeState.bytesTxToAdb)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.bridgeState.lastError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Hata: ${uiState.bridgeState.lastError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SignalRed
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Start / Stop Bridge Button
            Button(
                onClick = onToggleBridgeService,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("toggle_bridge_service_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBridgeRunning) MaterialTheme.colorScheme.surfaceVariant else EmeraldSignal,
                    contentColor = if (isBridgeRunning) MaterialTheme.colorScheme.onSurface else Color.White
                )
            ) {
                Icon(
                    imageVector = if (isBridgeRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isBridgeRunning) "Köprü Servisini Durdur" else "Köprü Servisini Başlat",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // COMPLETE SHUTDOWN BUTTONS (Stops Service + Root ADB + Everything)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onShutdownEverything,
                    enabled = !uiState.isShuttingDownAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("shutdown_all_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalRed,
                        contentColor = Color.White
                    )
                ) {
                    if (uiState.isShuttingDownAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Kapatılıyor...",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Komple Kapat (Servis + ADB)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onShutdownAndExit,
                    enabled = !uiState.isShuttingDownAll,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("shutdown_and_exit_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SignalRed.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalRed)
                ) {
                    Text(
                        text = "Kapat & Çık",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }

        // 2. ROOT İLE ADB KONTROLÜ VE PORT AYARLARI
        TechnicalPanel(
            title = "ADB Kontrolü (Root & Port)",
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
            Text(
                text = "Root izniniz varsa tek dokunuşla ADB bağlantısını açabilir veya işiniz bittiğinde tamamen kapatabilirsiniz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
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
                    border = BorderStroke(1.dp, SignalRed.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalRed)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Root ADB Kapat",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            if (uiState.lastRootCommandOutput != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.lastRootCommandOutput,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.lastRootCommandSuccess == true) EmeraldSignal else SignalAmber
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            // Collapsible Port & Advanced Settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showAdvancedSettings = !showAdvancedSettings }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Port Ayarları (ADB: ${uiState.activeAdbPort} • Köprü: ${uiState.activeWsPort})",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Port Ayarlarını Aç/Kapat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = showAdvancedSettings) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(5555, 5556).forEach { preset ->
                                val selected = uiState.activeAdbPort == preset
                                OutlinedButton(
                                    onClick = { onAdbPortPreset(preset) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.testTag("adb_preset_$preset")
                                ) {
                                    Text(
                                        text = "Port $preset",
                                        style = MaterialTheme.typography.labelSmall
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
                                    text = "Kontrol Et",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Otomatik Kontrol",
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

        // 3. HIZLI BAĞLANTI (TARAYICI & HARİCİ KULLANIM)
        TechnicalPanel(
            title = "WebUSB Siteleri ve Tarayıcı"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onNavigateToTab(AppTab.BROWSER) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_embedded_browser_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dahili Tarayıcı",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(uiState.bookmarkletCode))
                        onShowBanner("Bookmarklet kodu kopyalandı!")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("dashboard_copy_bookmarklet_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Kod Kopyala",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quickTargets.forEach { target ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .background(MaterialTheme.colorScheme.background)
                            .clickable { onOpenUrlInEmbeddedBrowser(target.url) }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("quick_target_${target.url}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = target.title,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = target.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Tarayıcıda Aç",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleStatusCard(
    title: String,
    statusText: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val dotColor = if (isActive) EmeraldSignal else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isActive) EmeraldSignal else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
