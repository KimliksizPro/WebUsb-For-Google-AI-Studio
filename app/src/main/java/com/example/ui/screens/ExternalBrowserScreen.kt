package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.example.ui.MainUiState
import com.example.ui.components.BadgeTone
import com.example.ui.components.StatusBadge
import com.example.ui.components.TechnicalPanel
import com.example.ui.theme.JetBrainsMonoFontFamily

private data class SetupStepItem(
    val number: String,
    val title: String,
    val detail: String
)

@Composable
fun ExternalBrowserScreen(
    uiState: MainUiState,
    onToggleBridgeService: () -> Unit,
    onNavigateBackToDashboard: () -> Unit,
    onShowBanner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    var showRawCode by remember { mutableStateOf(false) }

    BackHandler {
        onNavigateBackToDashboard()
    }

    val isServiceRunning = uiState.bridgeState.isServiceRunning
    val accentRed = MaterialTheme.colorScheme.primary

    val setupSteps = remember(uiState.activeWsPort, uiState.activeAdbPort) {
        listOf(
            SetupStepItem(
                number = "1",
                title = "Köprü ve ADB'yi Başlat",
                detail = "Kontrol sekmesinden ADB (${uiState.activeAdbPort}) ve Köprü Servisini (${uiState.activeWsPort}) aktif edin."
            ),
            SetupStepItem(
                number = "2",
                title = "Yer İmine Ekle",
                detail = "Yukarıdan Bookmarklet kodunu kopyalayıp Chrome veya Brave'de yeni bir yer iminin URL alanına yapıştırın."
            ),
            SetupStepItem(
                number = "3",
                title = "WebADB'de Çalıştır",
                detail = "WebADB sayfasını açıp adres çubuğundan yer imini çalıştırın; \"WebUSB v2 Aktif!\" uyarısından sonra cihazı bağlayın."
            )
        )
    }

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
            TechnicalPanel(
                title = "Tarayıcı Kodu (WebUSB v2)",
                borderColor = if (isServiceRunning) accentRed.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
                headerTrailing = {
                    StatusBadge(
                        text = if (isServiceRunning) "Köprü Açık" else "Köprü Kapalı",
                        tone = if (isServiceRunning) BadgeTone.SUCCESS else BadgeTone.NEUTRAL
                    )
                }
            ) {
                Text(
                    text = "Bookmarklet kodunu Chrome veya Brave yer imlerine kaydedip WebADB sayfasında çalıştırın.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(uiState.bookmarkletCode))
                        onShowBanner("Bookmarklet kodu kopyalandı!")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("copy_bookmarklet_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bookmarklet Kodunu Kopyala",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(uiState.polyfillJsCode))
                            onShowBanner("Ham JavaScript kodu kopyalandı!")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_raw_js_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ham JS",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    OutlinedButton(
                        onClick = onToggleBridgeService,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("external_toggle_service_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, accentRed.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentRed)
                    ) {
                        Icon(
                            imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = accentRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isServiceRunning) "Durdur" else "Başlat",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = accentRed
                        )
                    }
                }
            }

            TechnicalPanel(
                title = "Kısa Kurulum",
                borderColor = accentRed.copy(alpha = 0.45f),
                headerTrailing = {
                    StatusBadge(
                        text = "3 Adım",
                        tone = BadgeTone.SUCCESS
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    setupSteps.forEach { step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(accentRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = step.number,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = step.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = step.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showRawCode = !showRawCode }
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Kodu Görüntüle",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = accentRed
                    )
                    Icon(
                        imageVector = if (showRawCode) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = accentRed
                    )
                }

                AnimatedVisibility(visible = showRawCode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = uiState.bookmarkletCode,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("polyfill_js_code_text")
                        )
                    }
                }
            }
        }
    }
}
