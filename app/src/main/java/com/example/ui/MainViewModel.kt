package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bridge.AdbListenStatus
import com.example.bridge.AdbPortManager
import com.example.bridge.AdbProbeResult
import com.example.bridge.BridgeRuntimeState
import com.example.bridge.BridgeStateRepository
import com.example.bridge.LogDirection
import com.example.bridge.WebUsbJsGenerator
import com.example.data.BridgePreferences
import com.example.data.ThemePreference
import com.example.service.WebUsbBridgeService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppTab {
    DASHBOARD,
    BROWSER,
    EXTERNAL_MODE,
    LOGS
}

data class QuickWebUsbTarget(
    val title: String,
    val subtitle: String,
    val url: String
)

data class MainUiState(
    val selectedTab: AppTab = AppTab.DASHBOARD,
    val themePreference: ThemePreference = ThemePreference.DARK,
    val adbPortInput: String = "5555",
    val wsPortInput: String = "8765",
    val activeAdbPort: Int = 5555,
    val activeWsPort: Int = 8765,
    val autoPollAdb: Boolean = true,
    val adbProbeResult: AdbProbeResult = AdbProbeResult(
        host = "127.0.0.1",
        port = 5555,
        status = AdbListenStatus.CHECKING
    ),
    val isRootAvailable: Boolean? = null,
    val isCheckingRoot: Boolean = false,
    val isRunningRootCommand: Boolean = false,
    val isShuttingDownAll: Boolean = false,
    val lastRootCommandOutput: String? = null,
    val lastRootCommandSuccess: Boolean? = null,
    val browserUrlInput: String = "https://app.webadb.com",
    val currentLoadedUrl: String = "https://app.webadb.com",
    val isPageLoading: Boolean = false,
    val pageLoadProgress: Int = 0,
    val pageInjectionCount: Int = 0,
    val lastInjectedTimestamp: String? = null,
    val statusBannerMessage: String? = null,
    val bridgeState: BridgeRuntimeState = BridgeRuntimeState()
) {
    val polyfillJsCode: String
        get() = WebUsbJsGenerator.buildPolyfillScript(activeWsPort)

    val bookmarkletCode: String
        get() = WebUsbJsGenerator.buildBookmarklet(activeWsPort)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = BridgePreferences(application)
    private val _uiState = MutableStateFlow(MainUiState())
    private var pollJob: Job? = null

    val quickTargets = listOf(
        QuickWebUsbTarget(
            title = "WebADB (Tango)",
            subtitle = "app.webadb.com • Tarayıcıdan ADB yönetimi",
            url = "https://app.webadb.com"
        ),
        QuickWebUsbTarget(
            title = "Tango ADB Next",
            subtitle = "tangoapp.dev • Komut satırı ve dosya yöneticisi",
            url = "https://tangoapp.dev"
        ),
        QuickWebUsbTarget(
            title = "Bağlantı Test Konsolu",
            subtitle = "Dahili WebUSB bağlantı testi",
            url = "local://inspector"
        ),
        QuickWebUsbTarget(
            title = "Android Flash Tool",
            subtitle = "flash.android.com • Android kurulum aracı",
            url = "https://flash.android.com"
        )
    )

    val uiState: StateFlow<MainUiState> = combine(
        _uiState,
        BridgeStateRepository.state
    ) { localState, bridgeRuntime ->
        localState.copy(bridgeState = bridgeRuntime)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        viewModelScope.launch {
            prefs.configFlow.collect { config ->
                _uiState.update { current ->
                    current.copy(
                        themePreference = config.themePreference,
                        activeAdbPort = config.adbPort,
                        activeWsPort = config.wsPort,
                        adbPortInput = if (current.adbPortInput == "5555" && config.adbPort != 5555) {
                            config.adbPort.toString()
                        } else {
                            current.adbPortInput
                        },
                        wsPortInput = if (current.wsPortInput == "8765" && config.wsPort != 8765) {
                            config.wsPort.toString()
                        } else {
                            current.wsPortInput
                        },
                        autoPollAdb = config.autoPollAdbPort
                    )
                }
                BridgeStateRepository.updateConfiguredPorts(config.wsPort, config.adbPort)
            }
        }

        checkRootStatus()
        startContinuousPortProbing()
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun cycleThemePreference() {
        val next = when (_uiState.value.themePreference) {
            ThemePreference.DARK -> ThemePreference.LIGHT
            ThemePreference.LIGHT -> ThemePreference.SYSTEM
            ThemePreference.SYSTEM -> ThemePreference.DARK
        }
        _uiState.update { it.copy(themePreference = next) }
        viewModelScope.launch {
            prefs.setThemePreference(next)
        }
    }

    fun setThemePreference(theme: ThemePreference) {
        _uiState.update { it.copy(themePreference = theme) }
        viewModelScope.launch {
            prefs.setThemePreference(theme)
        }
    }

    fun onAdbPortInputChanged(input: String) {
        val filtered = input.filter { it.isDigit() }.take(5)
        _uiState.update { it.copy(adbPortInput = filtered) }
        val parsed = filtered.toIntOrNull()
        if (parsed != null && parsed in 1..65535) {
            _uiState.update { it.copy(activeAdbPort = parsed) }
            viewModelScope.launch {
                prefs.setAdbPort(parsed)
                if (BridgeStateRepository.state.value.isServiceRunning) {
                    WebUsbBridgeService.updateTargetAdbPort(getApplication(), parsed)
                }
                probeAdbPortNow(parsed)
            }
        }
    }

    fun setAdbPortPreset(port: Int) {
        _uiState.update {
            it.copy(
                adbPortInput = port.toString(),
                activeAdbPort = port
            )
        }
        viewModelScope.launch {
            prefs.setAdbPort(port)
            if (BridgeStateRepository.state.value.isServiceRunning) {
                WebUsbBridgeService.updateTargetAdbPort(getApplication(), port)
            }
            probeAdbPortNow(port)
        }
    }

    fun onWsPortInputChanged(input: String) {
        val filtered = input.filter { it.isDigit() }.take(5)
        _uiState.update { it.copy(wsPortInput = filtered) }
        val parsed = filtered.toIntOrNull()
        if (parsed != null && parsed in 1024..65535) {
            _uiState.update { it.copy(activeWsPort = parsed) }
            viewModelScope.launch {
                prefs.setWsPort(parsed)
                BridgeStateRepository.updateConfiguredPorts(parsed, _uiState.value.activeAdbPort)
            }
        }
    }

    fun toggleAutoPollAdb(enabled: Boolean) {
        _uiState.update { it.copy(autoPollAdb = enabled) }
        viewModelScope.launch {
            prefs.setAutoPoll(enabled)
        }
    }

    fun checkRootStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingRoot = true) }
            val hasRoot = AdbPortManager.checkRootAccess()
            _uiState.update {
                it.copy(
                    isRootAvailable = hasRoot,
                    isCheckingRoot = false
                )
            }
            BridgeStateRepository.addLog(
                LogDirection.ROOT,
                "ROOT",
                if (hasRoot) "Root erişimi hazır." else "Root erişimi yok (Manuel mod)."
            )
        }
    }

    fun enableAdbWithRoot() {
        if (_uiState.value.isRunningRootCommand) return
        val targetPort = _uiState.value.activeAdbPort
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRunningRootCommand = true,
                    lastRootCommandOutput = "Root ile ADB ($targetPort) açılıyor..."
                )
            }
            BridgeStateRepository.addLog(
                LogDirection.ROOT,
                "ROOT-AÇ",
                "ADB TCP $targetPort portu açılıyor..."
            )
            val result = AdbPortManager.enableAdbTcpWithRoot(targetPort)
            _uiState.update {
                it.copy(
                    isRunningRootCommand = false,
                    lastRootCommandOutput = result.output,
                    lastRootCommandSuccess = result.success,
                    isRootAvailable = if (result.success) true else it.isRootAvailable
                )
            }
            BridgeStateRepository.addLog(
                if (result.success) LogDirection.ROOT else LogDirection.ERROR,
                "ROOT-SONUÇ",
                result.output
            )
            if (result.success) {
                showStatusBanner("Root ile ADB ($targetPort) açıldı.")
            } else {
                showStatusBanner(result.output)
            }
            delay(500)
            probeAdbPortNow(targetPort)
        }
    }

    fun disableAdbWithRoot() {
        if (_uiState.value.isRunningRootCommand) return
        val targetPort = _uiState.value.activeAdbPort
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRunningRootCommand = true,
                    lastRootCommandOutput = "Root ADB kapatılıyor..."
                )
            }
            BridgeStateRepository.addLog(
                LogDirection.ROOT,
                "ROOT-KAPAT",
                "Root ADB ve TCP portu kapatılıyor..."
            )
            val result = AdbPortManager.disableAdbTcpWithRoot()
            _uiState.update {
                it.copy(
                    isRunningRootCommand = false,
                    lastRootCommandOutput = result.output,
                    lastRootCommandSuccess = result.success
                )
            }
            BridgeStateRepository.addLog(
                if (result.success) LogDirection.ROOT else LogDirection.ERROR,
                "ROOT-KAPAT",
                result.output
            )
            showStatusBanner(result.output)
            delay(400)
            probeAdbPortNow(targetPort)
        }
    }

    /**
     * Stops the WebSocket <-> ADB Foreground Service, closes all active connections,
     * and disables Root ADB TCP (`adbd`) so nothing stays running in the background.
     */
    fun shutdownEverything(onComplete: (() -> Unit)? = null) {
        if (_uiState.value.isShuttingDownAll) return
        val targetPort = _uiState.value.activeAdbPort
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isShuttingDownAll = true,
                    isRunningRootCommand = true,
                    lastRootCommandOutput = "Tüm servisler ve Root ADB durduruluyor..."
                )
            }

            // 1. Stop WebSocket <-> ADB Foreground Service
            WebUsbBridgeService.stopService(getApplication())

            // 2. If Root is available or might be active, disable Root ADB TCP & stop adbd
            val rootResult = if (_uiState.value.isRootAvailable != false) {
                AdbPortManager.disableAdbTcpWithRoot()
            } else {
                null
            }

            delay(350)
            val probe = AdbPortManager.probeAdbPort("127.0.0.1", targetPort)

            val summaryMsg = if (rootResult?.success == true) {
                "Köprü servisi ve Root ADB tamamen kapatıldı."
            } else {
                "Köprü servisi ve tüm bağlantılar kapatıldı."
            }

            _uiState.update {
                it.copy(
                    isShuttingDownAll = false,
                    isRunningRootCommand = false,
                    adbProbeResult = probe,
                    lastRootCommandOutput = summaryMsg,
                    lastRootCommandSuccess = true
                )
            }

            BridgeStateRepository.addLog(
                LogDirection.SYSTEM,
                "KAPAT",
                summaryMsg
            )

            showStatusBanner(summaryMsg)
            onComplete?.invoke()
        }
    }

    fun probeAdbPortNow(port: Int = _uiState.value.activeAdbPort) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    adbProbeResult = it.adbProbeResult.copy(
                        port = port,
                        status = AdbListenStatus.CHECKING
                    )
                )
            }
            val result = AdbPortManager.probeAdbPort("127.0.0.1", port)
            _uiState.update { it.copy(adbProbeResult = result) }
        }
    }

    private fun startContinuousPortProbing() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                if (state.autoPollAdb) {
                    val result = AdbPortManager.probeAdbPort("127.0.0.1", state.activeAdbPort)
                    _uiState.update { it.copy(adbProbeResult = result) }
                }
                delay(2500L)
            }
        }
    }

    fun startBridgeService() {
        val wsPort = _uiState.value.activeWsPort
        val adbPort = _uiState.value.activeAdbPort
        WebUsbBridgeService.startService(getApplication(), wsPort = wsPort, adbPort = adbPort)
        showStatusBanner("Köprü servisi başlatıldı ($wsPort -> $adbPort)")
    }

    fun stopBridgeService() {
        WebUsbBridgeService.stopService(getApplication())
        showStatusBanner("Köprü servisi durduruldu.")
    }

    fun toggleBridgeService() {
        if (BridgeStateRepository.state.value.isServiceRunning) {
            stopBridgeService()
        } else {
            startBridgeService()
        }
    }

    fun onBrowserUrlInputChanged(url: String) {
        _uiState.update { it.copy(browserUrlInput = url) }
    }

    fun loadUrlInBrowser(rawUrl: String = _uiState.value.browserUrlInput) {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return
        val normalized = when {
            trimmed.startsWith("local://") -> trimmed
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
        _uiState.update {
            it.copy(
                browserUrlInput = normalized,
                currentLoadedUrl = normalized,
                selectedTab = AppTab.BROWSER
            )
        }
        if (!normalized.startsWith("local://")) {
            viewModelScope.launch {
                prefs.setLastBrowserUrl(normalized)
            }
        }
    }

    fun onWebPageLoadStateChanged(isLoading: Boolean, progress: Int) {
        _uiState.update {
            it.copy(
                isPageLoading = isLoading,
                pageLoadProgress = progress
            )
        }
    }

    fun recordPolyfillInjected(url: String, phase: String) {
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        _uiState.update {
            it.copy(
                pageInjectionCount = it.pageInjectionCount + 1,
                lastInjectedTimestamp = "$timeStr ($phase)"
            )
        }
        BridgeStateRepository.addLog(
            LogDirection.SYSTEM,
            "TARAYICI",
            "WebUSB bağlantısı sayfaya eklendi -> $url"
        )
    }

    fun clearLogs() {
        BridgeStateRepository.clearLogs()
    }

    fun resetCounters() {
        BridgeStateRepository.resetCounters()
    }

    fun showStatusBanner(message: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusBannerMessage = message) }
            delay(3200L)
            _uiState.update { current ->
                if (current.statusBannerMessage == message) {
                    current.copy(statusBannerMessage = null)
                } else current
            }
        }
    }
}
