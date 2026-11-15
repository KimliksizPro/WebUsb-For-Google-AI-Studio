package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.bridge.BridgeStateRepository
import com.example.bridge.LogDirection
import com.example.bridge.WebUsbJsGenerator
import com.example.ui.MainUiState
import com.example.ui.QuickWebUsbTarget
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalAmber

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebUsbBrowserScreen(
    uiState: MainUiState,
    quickTargets: List<QuickWebUsbTarget>,
    onUrlInputChanged: (String) -> Unit,
    onLoadUrl: (String) -> Unit,
    onPageLoadStateChanged: (Boolean, Int) -> Unit,
    onPolyfillInjected: (String, String) -> Unit,
    onStartBridgeService: () -> Unit,
    onNavigateBackToDashboard: () -> Unit,
    onShowBanner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val currentWsPort by rememberUpdatedState(uiState.activeWsPort)
    val currentAdbPort by rememberUpdatedState(uiState.activeAdbPort)
    val currentPolyfillScript by rememberUpdatedState(uiState.polyfillJsCode)

    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var lastLoadedTarget by remember { mutableStateOf("") }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                allowContentAccess = true
                allowFileAccess = true
                javaScriptCanOpenWindowsAutomatically = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                userAgentString = settings.userAgentString + " WebUSBLocalBridge/1.0 Chrome/128.0.0.0"
            }
        }
    }

    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val targetUrl = url ?: ""
                onPageLoadStateChanged(true, 15)
                canGoBack = view?.canGoBack() == true
                canGoForward = view?.canGoForward() == true
                view?.evaluateJavascript(currentPolyfillScript, null)
                onPolyfillInjected(targetUrl, "onPageStarted")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val targetUrl = url ?: ""
                onPageLoadStateChanged(false, 100)
                canGoBack = view?.canGoBack() == true
                canGoForward = view?.canGoForward() == true
                view?.evaluateJavascript(currentPolyfillScript, null)
                onPolyfillInjected(targetUrl, "onPageFinished")
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                canGoBack = view?.canGoBack() == true
                canGoForward = view?.canGoForward() == true
                view?.evaluateJavascript(currentPolyfillScript, null)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onPageLoadStateChanged(newProgress < 100, newProgress)
                if (newProgress in 30..85) {
                    view?.evaluateJavascript(currentPolyfillScript, null)
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val msg = consoleMessage?.message() ?: return super.onConsoleMessage(consoleMessage)
                if (msg.contains("[WebUSB Bridge]") || msg.contains("ADB", ignoreCase = true)) {
                    BridgeStateRepository.addLog(
                        LogDirection.SYSTEM,
                        "TARAYICI",
                        msg
                    )
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }

        onDispose {
            webView.stopLoading()
        }
    }

    LaunchedEffect(uiState.currentLoadedUrl, uiState.activeWsPort) {
        val target = uiState.currentLoadedUrl
        val key = "$target@${uiState.activeWsPort}"
        if (lastLoadedTarget != key) {
            lastLoadedTarget = key
            if (target.startsWith("local://")) {
                val html = WebUsbJsGenerator.buildLocalTestConsoleHtml(currentWsPort, currentAdbPort)
                webView.loadDataWithBaseURL(
                    "https://localhost.webusb/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            } else {
                webView.loadUrl(target)
            }
        }
    }

    BackHandler {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            onNavigateBackToDashboard()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { if (webView.canGoBack()) webView.goBack() },
                    enabled = canGoBack,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("browser_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { if (webView.canGoForward()) webView.goForward() },
                    enabled = canGoForward,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("browser_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "İleri",
                        modifier = Modifier.size(20.dp)
                    )
                }

                OutlinedTextField(
                    value = uiState.browserUrlInput,
                    onValueChange = onUrlInputChanged,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("browser_url_input"),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "https://app.webadb.com",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            focusManager.clearFocus()
                            lastLoadedTarget = ""
                            onLoadUrl(uiState.browserUrlInput)
                        }
                    ),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        lastLoadedTarget = ""
                        onLoadUrl(uiState.browserUrlInput)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            RoundedCornerShape(10.dp)
                        )
                        .testTag("browser_go_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Yenile",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!uiState.bridgeState.isServiceRunning) {
                    OutlinedButton(
                        onClick = onStartBridgeService,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, SignalAmber),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalAmber),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Köprüyü Başlat",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(EmeraldSignal.copy(alpha = 0.14f))
                        .clickable {
                            webView.evaluateJavascript(currentPolyfillScript, null)
                            onPolyfillInjected(webView.url ?: uiState.currentLoadedUrl, "Manuel")
                            onShowBanner("WebUSB bağlantısı sayfaya tekrar eklendi!")
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("manual_inject_js_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = EmeraldSignal,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Bağlantıyı Yenile",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = EmeraldSignal
                    )
                }

                quickTargets.forEach { target ->
                    val isCurrent = uiState.currentLoadedUrl == target.url
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(
                                1.dp,
                                if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(50)
                            )
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .clickable {
                                focusManager.clearFocus()
                                lastLoadedTarget = ""
                                onLoadUrl(target.url)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = target.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (uiState.isPageLoading) {
            LinearProgressIndicator(
                progress = { (uiState.pageLoadProgress / 100f).coerceIn(0.05f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }

        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .testTag("embedded_webview")
        )
    }
}
