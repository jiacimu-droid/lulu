package me.rerere.rikkahub.plugin.webview

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.rikkahub.plugin.data.PluginDataStore
import me.rerere.rikkahub.plugin.loader.PluginLoader
import me.rerere.rikkahub.plugin.manager.PluginManager
import me.rerere.rikkahub.plugin.repository.PluginRepository
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginWebViewPage(
    pluginId: String,
    htmlEntryPath: String,
    pluginManager: PluginManager,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val pluginLoader = koinInject<PluginLoader>()
    val pluginRepository = koinInject<PluginRepository>()
    val plugins by pluginManager.plugins.collectAsStateWithLifecycle()
    val pluginInfo = plugins.find { it.manifest.id == pluginId }
    val dataStore = remember(pluginId) { PluginDataStore(context, pluginId) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val overlayController = remember(pluginId) { PluginOverlayController(context, dataStore) }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val fileLaunchers = rememberPluginWebViewFileLaunchers(
        context = context,
        dataStore = dataStore,
        webViewProvider = { webView },
    )

    val timerEndReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(receiveContext: Context?, intent: Intent?) {
                if (intent?.action != PomodoroTimerService.ACTION_TIMER_END) return
                webView?.post {
                    webView?.evaluateJavascript(
                        "if(typeof window.onTimerEnd === 'function') { window.onTimerEnd(); }",
                        null,
                    )
                }
                overlayController.notifyTimerEnd()
            }
        }
    }
    DisposableEffect(timerEndReceiver) {
        ContextCompat.registerReceiver(
            context,
            timerEndReceiver,
            IntentFilter(PomodoroTimerService.ACTION_TIMER_END),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            runCatching { context.unregisterReceiver(timerEndReceiver) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pluginInfo?.manifest?.name ?: "插件管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (pluginInfo == null) {
                Text("插件不存在", modifier = Modifier.align(Alignment.Center))
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                @SuppressLint("SetJavaScriptEnabled")
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                databaseEnabled = true
                            }
                            webViewClient = PluginBridgeWebViewClient(
                                pluginInfo = pluginInfo,
                                dataStore = dataStore,
                                pluginLoader = pluginLoader,
                                pluginManager = pluginManager,
                                pluginRepository = pluginRepository,
                                onPickImage = fileLaunchers.pickImage,
                                onPickFile = fileLaunchers.pickTextFile,
                                onPickBinaryFile = fileLaunchers.pickBinaryFile,
                                onImportAudioFile = fileLaunchers.importAudioFile,
                                onSaveFileAs = fileLaunchers.saveFileAs,
                                onClose = onNavigateBack,
                                onStartTimer = startTimer@{ sourceWebView, seconds ->
                                    if (
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                        !Settings.canDrawOverlays(context)
                                    ) {
                                        overlayPermissionLauncher.launch(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}"),
                                            ),
                                        )
                                        sourceWebView.post {
                                            sourceWebView.evaluateJavascript(
                                                "window.__bridgeResult('${null}', {success:false,error:'Overlay permission required. Please grant it in settings and try again.'});",
                                                null,
                                            )
                                        }
                                        return@startTimer
                                    }
                                    if (
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.POST_NOTIFICATIONS,
                                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    PomodoroTimerService.start(context, seconds)
                                },
                                onStopTimer = { PomodoroTimerService.stop(context) },
                                onShowOverlay = overlayController::show,
                                onHideOverlay = overlayController::hide,
                            )
                            webChromeClient = object : WebChromeClient() {
                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: android.webkit.ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?,
                                ): Boolean {
                                    val acceptTypes = fileChooserParams?.acceptTypes
                                        ?.filter { it.isNotBlank() }
                                        ?.toTypedArray()
                                        ?: arrayOf("*/*")
                                    fileLaunchers.chooseHtmlFile(filePathCallback, acceptTypes)
                                    return true
                                }
                            }
                            val htmlFile = File(pluginInfo.directory, htmlEntryPath)
                            if (htmlFile.exists()) {
                                loadUrl("file://${htmlFile.absolutePath}")
                            } else {
                                loadData(
                                    "<html><body><h2>页面文件不存在</h2><p>$htmlEntryPath</p></body></html>",
                                    "text/html",
                                    "UTF-8",
                                )
                            }
                            webView = this
                        }
                    },
                    update = { webView = it },
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            overlayController.destroy()
            PomodoroTimerService.stop(context)
        }
    }
}
