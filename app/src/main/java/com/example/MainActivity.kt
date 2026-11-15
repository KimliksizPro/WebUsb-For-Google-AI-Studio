package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bridge.AdbListenStatus
import com.example.data.ThemePreference
import com.example.ui.AppTab
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExternalBrowserScreen
import com.example.ui.screens.PacketLogsScreen
import com.example.ui.screens.WebUsbBrowserScreen
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.SignalRed
import com.example.ui.theme.WebUsbBridgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()

            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (uiState.themePreference) {
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
                ThemePreference.SYSTEM -> systemDark
            }

            WebUsbBridgeTheme(darkTheme = useDarkTheme) {
                WebUsbBridgeApp(
                    uiState = uiState,
                    viewModel = mainViewModel
                )
            }
        }
    }
}

private data class NavTabItem(
    val tab: AppTab,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private val NAV_ITEMS = listOf(
    NavTabItem(
        tab = AppTab.DASHBOARD,
        label = "Ana Sayfa",
        icon = Icons.Default.Home,
        testTag = "nav_tab_dashboard"
    ),
    NavTabItem(
        tab = AppTab.BROWSER,
        label = "Tarayıcı",
        icon = Icons.Default.Language,
        testTag = "nav_tab_browser"
    ),
    NavTabItem(
        tab = AppTab.EXTERNAL_MODE,
        label = "Chrome / Brave",
        icon = Icons.Default.BookmarkBorder,
        testTag = "nav_tab_external"
    ),
    NavTabItem(
        tab = AppTab.LOGS,
        label = "Kayıtlar",
        icon = Icons.Default.Terminal,
        testTag = "nav_tab_logs"
    )
)

@Composable
fun WebUsbBridgeApp(
    uiState: MainUiState,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.toggleBridgeService()
    }

    val handleToggleBridgeWithPermission = {
        if (!uiState.bridgeState.isServiceRunning &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.toggleBridgeService()
        }
    }

    val handleStartBridgeWithPermission = {
        if (!uiState.bridgeState.isServiceRunning) {
            handleToggleBridgeWithPermission()
        }
    }

    val handleShutdownAndExit = {
        viewModel.shutdownEverything {
            activity?.finishAndRemoveTask()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 720.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                SimpleTopBar(
                    uiState = uiState,
                    onShutdownEverything = { viewModel.shutdownEverything() },
                    onCycleTheme = viewModel::cycleThemePreference
                )
            },
            bottomBar = {
                if (!isExpandedScreen) {
                    Column {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            NAV_ITEMS.forEach { item ->
                                val selected = uiState.selectedTab == item.tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(item.tab) },
                                    icon = {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        NAV_ITEMS.forEach { item ->
                            val selected = uiState.selectedTab == item.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                    VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (uiState.selectedTab) {
                        AppTab.DASHBOARD -> {
                            DashboardScreen(
                                uiState = uiState,
                                quickTargets = viewModel.quickTargets,
                                onAdbPortChanged = viewModel::onAdbPortInputChanged,
                                onAdbPortPreset = viewModel::setAdbPortPreset,
                                onWsPortChanged = viewModel::onWsPortInputChanged,
                                onProbePortNow = { viewModel.probeAdbPortNow() },
                                onToggleAutoPoll = viewModel::toggleAutoPollAdb,
                                onEnableRootAdb = viewModel::enableAdbWithRoot,
                                onDisableRootAdb = viewModel::disableAdbWithRoot,
                                onCheckRoot = viewModel::checkRootStatus,
                                onToggleBridgeService = handleToggleBridgeWithPermission,
                                onShutdownEverything = { viewModel.shutdownEverything() },
                                onShutdownAndExit = handleShutdownAndExit,
                                onResetCounters = viewModel::resetCounters,
                                onOpenUrlInEmbeddedBrowser = viewModel::loadUrlInBrowser,
                                onNavigateToTab = viewModel::selectTab,
                                onShowBanner = viewModel::showStatusBanner
                            )
                        }

                        AppTab.BROWSER -> {
                            WebUsbBrowserScreen(
                                uiState = uiState,
                                quickTargets = viewModel.quickTargets,
                                onUrlInputChanged = viewModel::onBrowserUrlInputChanged,
                                onLoadUrl = viewModel::loadUrlInBrowser,
                                onPageLoadStateChanged = viewModel::onWebPageLoadStateChanged,
                                onPolyfillInjected = viewModel::recordPolyfillInjected,
                                onStartBridgeService = handleStartBridgeWithPermission,
                                onNavigateBackToDashboard = { viewModel.selectTab(AppTab.DASHBOARD) },
                                onShowBanner = viewModel::showStatusBanner
                            )
                        }

                        AppTab.EXTERNAL_MODE -> {
                            ExternalBrowserScreen(
                                uiState = uiState,
                                onToggleBridgeService = handleToggleBridgeWithPermission,
                                onNavigateBackToDashboard = { viewModel.selectTab(AppTab.DASHBOARD) },
                                onShowBanner = viewModel::showStatusBanner
                            )
                        }

                        AppTab.LOGS -> {
                            PacketLogsScreen(
                                uiState = uiState,
                                onClearLogs = viewModel::clearLogs,
                                onNavigateBackToDashboard = { viewModel.selectTab(AppTab.DASHBOARD) },
                                onShowBanner = viewModel::showStatusBanner
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = uiState.statusBannerMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, EmeraldSignal.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("status_banner")
                        ) {
                            Text(
                                text = uiState.statusBannerMessage ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = EmeraldSignal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleTopBar(
    uiState: MainUiState,
    onShutdownEverything: () -> Unit,
    onCycleTheme: () -> Unit
) {
    val isServiceRunning = uiState.bridgeState.isServiceRunning
    val isAdbOpen = uiState.adbProbeResult.status == AdbListenStatus.LISTENING

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "WebUSB Bridge",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isServiceRunning && isAdbOpen -> "Köprü ve ADB Aktif"
                            isServiceRunning -> "Köprü Servisi Açık"
                            isAdbOpen -> "ADB Portu Açık"
                            else -> "Servis Kapalı"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isServiceRunning || isAdbOpen) EmeraldSignal else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick Complete Shutdown Icon Button in Top Bar
                IconButton(
                    onClick = onShutdownEverything,
                    modifier = Modifier
                        .size(38.dp)
                        .background(SignalRed.copy(alpha = 0.14f), CircleShape)
                        .testTag("topbar_shutdown_all_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Her Şeyi Durdur",
                        tint = SignalRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                val (themeIcon, themeLabel) = when (uiState.themePreference) {
                    ThemePreference.DARK -> Icons.Default.DarkMode to "Koyu Tema"
                    ThemePreference.LIGHT -> Icons.Default.LightMode to "Açık Tema"
                    ThemePreference.SYSTEM -> Icons.Default.Contrast to "Sistem Teması"
                }

                IconButton(
                    onClick = onCycleTheme,
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .testTag("theme_toggle_button")
                ) {
                    Icon(
                        imageVector = themeIcon,
                        contentDescription = themeLabel,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}
