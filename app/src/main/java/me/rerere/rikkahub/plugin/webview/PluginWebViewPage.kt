package me.rerere.rikkahub.plugin.webview

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.plugin.data.PluginDataStore
import me.rerere.rikkahub.plugin.loader.PluginLoader
import me.rerere.rikkahub.plugin.loader.LoadedPlugin
import me.rerere.rikkahub.plugin.manager.PluginManager
import me.rerere.rikkahub.plugin.model.PluginHookConfig
import me.rerere.rikkahub.plugin.model.PluginInfo
import me.rerere.rikkahub.plugin.repository.PluginRepository
import org.json.JSONArray
import org.json.JSONObject
import org.koin.compose.koinInject
import java.io.File

private const val TAG = "PluginWebViewPage"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginWebViewPage(
    pluginId: String,
    htmlEntryPath: String,
    pluginManager: PluginManager,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val pluginLoader = koinInject<PluginLoader>()
    val pluginRepository = koinInject<PluginRepository>()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pendingImageCallback by remember { mutableStateOf<String?>(null) }
    var pendingFileCallback by remember { mutableStateOf<String?>(null) }

    val plugins by pluginManager.plugins.collectAsStateWithLifecycle()
    val pluginInfo = plugins.find { it.manifest.id == pluginId }

    val dataStore = remember(pluginId) {
        PluginDataStore(context, pluginId)
    }

    // 文件选择器 - 用于 Bridge.pickFile()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val bridgeCallback = pendingFileCallback
        if (bridgeCallback != null) {
            if (uri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val content = inputStream?.bufferedReader()?.readText()
                    inputStream?.close()
                    val fileName = uri.lastPathSegment ?: "unknown.txt"
                    val escapedContent = content?.replace("\\", "\\\\")?.replace("'", "\\'")?.replace("\n", "\\n")?.replace("\r", "\\r") ?: ""
                    val escapedName = fileName.replace("\\", "\\\\").replace("'", "\\'")
                    webView?.post {
                        webView?.evaluateJavascript(
                            "window.__bridgeResult('$bridgeCallback', {success:true,fileName:'$escapedName',content:'$escapedContent'});", null
                        )
                    }
                } catch (e: Exception) {
                    val errMsg = e.message?.replace("\\", "\\\\")?.replace("'", "\\'") ?: "Unknown error"
                    webView?.post {
                        webView?.evaluateJavascript(
                            "window.__bridgeResult('$bridgeCallback', {success:false,error:'$errMsg'});", null
                        )
                    }
                }
            } else {
                webView?.post {
                    webView?.evaluateJavascript(
                        "window.__bridgeResult('$bridgeCallback', {success:false,error:'User cancelled'});", null
                    )
                }
            }
            pendingFileCallback = null
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val callbackId = pendingImageCallback
        if (callbackId != null && uri != null) {
            try {
                val base64 = uriToBase64(context, uri)
                webView?.evaluateJavascript(
                    "if(window.__bridgeCallbacks && window.__bridgeCallbacks['$callbackId'])" +
                            "{window.__bridgeCallbacks['$callbackId']($base64); delete window.__bridgeCallbacks['$callbackId'];}",
                    null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process picked image", e)
                webView?.evaluateJavascript(
                    "if(window.__bridgeCallbacks && window.__bridgeCallbacks['$callbackId'])" +
                            "{window.__bridgeCallbacks['$callbackId'](null); delete window.__bridgeCallbacks['$callbackId'];}",
                    null
                )
            }
        } else if (callbackId != null) {
            webView?.evaluateJavascript(
                "if(window.__bridgeCallbacks && window.__bridgeCallbacks['$callbackId'])" +
                        "{window.__bridgeCallbacks['$callbackId'](null); delete window.__bridgeCallbacks['$callbackId'];}",
                null
            )
        }
        pendingImageCallback = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pluginInfo?.manifest?.name ?: "\u63D2\u4EF6\u7BA1\u7406") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(HugeIcons.ArrowLeft01, contentDescription = "\u8FD4\u56DE")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (pluginInfo == null) {
                Text(
                    text = "\u63D2\u4EF6\u4E0D\u5B58\u5728",
                    modifier = Modifier.align(Alignment.Center)
                )
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

                            webViewClient = PluginWebViewClient(
                                pluginInfo = pluginInfo,
                                dataStore = dataStore,
                                pluginLoader = pluginLoader,
                                pluginManager = pluginManager,
                                pluginRepository = pluginRepository,
                                onPickImage = { callbackId ->
                                    pendingImageCallback = callbackId
                                    pickImageLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onPickFile = { callbackId ->
                                    pendingFileCallback = callbackId
                                    filePickerLauncher.launch(arrayOf("text/plain", "text/markdown", "application/octet-stream"))
                                },
                                onClose = onNavigateBack
                            )

                            // 禁用原生长按选择菜单 - 通过CSS/JS控制，不再用原生拦截
                            // （原生setOnLongClickListener会阻止批注模式的文字选择）

                            val htmlFile = File(pluginInfo.directory, htmlEntryPath)
                            if (htmlFile.exists()) {
                                loadUrl("file://${htmlFile.absolutePath}")
                            } else {
                                loadData(
                                    "<html><body><h2>\u9875\u9762\u6587\u4EF6\u4E0D\u5B58\u5728</h2><p>$htmlEntryPath</p></body></html>",
                                    "text/html",
                                    "UTF-8"
                                )
                            }

                            webView = this@apply
                        }
                    },
                    update = { wv ->
                        webView = wv
                    }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
        }
    }
}

private class PluginWebViewClient(
    private val pluginInfo: PluginInfo,
    private val dataStore: PluginDataStore,
    private val pluginLoader: PluginLoader,
    private val pluginManager: PluginManager,
    private val pluginRepository: PluginRepository,
    private val onPickImage: (callbackId: String) -> Unit,
    private val onPickFile: (callbackId: String) -> Unit,
    private val onClose: () -> Unit
) : WebViewClient() {
    private val json = Json { ignoreUnknownKeys = true }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url?.toString() ?: return false
        if (url.startsWith("bridge://")) {
            handleBridgeCall(view!!, url)
            return true
        }
        return !url.startsWith("file://") && !url.startsWith("about:blank")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.evaluateJavascript(bridgeJavascript, null)
    }

    private fun handleBridgeCall(webView: WebView, url: String) {
        val uri = Uri.parse(url)
        val method = uri.host ?: return
        val params = uri.queryParameterNames.associateWith { uri.getQueryParameter(it) ?: "" }

        when (method) {
            "getPluginConfig" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val savedConfig = pluginRepository.getPluginConfig(pluginInfo.manifest.id)
                        val mergedConfig = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()

                        pluginInfo.manifest.config.forEach { field ->
                            if (savedConfig.containsKey(field.name)) {
                                mergedConfig[field.name] = savedConfig[field.name]!!
                            } else if (field.default != null) {
                                mergedConfig[field.name] = field.default
                            }
                        }

                        savedConfig.forEach { (key, value) ->
                            if (!mergedConfig.containsKey(key)) {
                                mergedConfig[key] = value
                            }
                        }

                        val jsonObj = JsonObject(mergedConfig)
                        val result = json.encodeToString(JsonObject.serializer(), jsonObj)
                        Log.d(TAG, "getPluginConfig for ${pluginInfo.manifest.id}: $result")
                        webView.post {
                            webView.evaluateJavascript(
                                "window.__bridgeResult('${params["callbackId"]}', $result);", null
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get plugin config", e)
                        webView.post {
                            webView.evaluateJavascript(
                                "window.__bridgeResult('${params["callbackId"]}', {});", null
                            )
                        }
                    }
                }
            }

            "getData" -> {
                val key = params["key"] ?: ""
                val value = dataStore.getData(key)
                val result = if (value != null) {
                    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")}\""
                } else "null"
                webView.post {
                    webView.evaluateJavascript(
                        "window.__bridgeResult('${params["callbackId"]}', $result);", null
                    )
                }
            }

            "setData" -> {
                val key = params["key"] ?: ""
                val value = params["value"] ?: ""
                dataStore.setData(key, value)
                webView.post {
                    webView.evaluateJavascript(
                        "window.__bridgeResult('${params["callbackId"]}', true);", null
                    )
                }
            }

            "deleteData" -> {
                val key = params["key"] ?: ""
                dataStore.deleteData(key)
                webView.post {
                    webView.evaluateJavascript(
                        "window.__bridgeResult('${params["callbackId"]}', true);", null
                    )
                }
            }

            "listData" -> {
                val keys = dataStore.listData()
                val jsonArray = JSONArray(keys)
                webView.post {
                    webView.evaluateJavascript(
                        "window.__bridgeResult('${params["callbackId"]}', ${jsonArray.toString()});", null
                    )
                }
            }

            "pickImage" -> {
                val callbackId = params["callbackId"] ?: ""
                onPickImage(callbackId)
            }

            "pickFile" -> {
                val callbackId = params["callbackId"] ?: ""
                onPickFile(callbackId)
            }

            "writeFile" -> {
                val fileName = params["fileName"] ?: ""
                val base64Data = params["data"] ?: ""
                try {
                    val dir = dataStore.getDataDir()
                    val file = File(dir, fileName)
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    file.writeBytes(bytes)
                    webView.post {
                        webView.evaluateJavascript(
                            "window.__bridgeResult('${params["callbackId"]}', '${file.absolutePath}');", null
                        )
                    }
                } catch (e: Exception) {
                    webView.post {
                        webView.evaluateJavascript(
                            "window.__bridgeResult('${params["callbackId"]}', null);", null
                        )
                    }
                }
            }

            "readFile" -> {
                val fileName = params["fileName"] ?: ""
                try {
                    val dir = dataStore.getDataDir()
                    val file = File(dir, fileName)
                    if (file.exists()) {
                        val bytes = file.readBytes()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        webView.post {
                            webView.evaluateJavascript(
                                "window.__bridgeResult('${params["callbackId"]}', '$base64');", null
                            )
                        }
                    } else {
                        webView.post {
                            webView.evaluateJavascript(
                                "window.__bridgeResult('${params["callbackId"]}', null);", null
                            )
                        }
                    }
                } catch (e: Exception) {
                    webView.post {
                        webView.evaluateJavascript(
                            "window.__bridgeResult('${params["callbackId"]}', null);", null
                        )
                    }
                }
            }

            "listFiles" -> {
                val dirPath = params["dir"] ?: ""
                val baseDir = if (dirPath.isEmpty()) dataStore.getDataDir()
                              else File(dataStore.getDataDir(), dirPath)
                val files = if (baseDir.exists() && baseDir.isDirectory) {
                    baseDir.listFiles()?.map { it.name } ?: emptyList()
                } else emptyList()
                val jsonArray = JSONArray(files)
                webView.post {
                    webView.evaluateJavascript(
                        "window.__bridgeResult('${params["callbackId"]}', ${jsonArray.toString()});", null
                    )
                }
            }

            "deleteFile" -> {
                val fileName = params["fileName"] ?: ""
                val dir = dataStore.getDataDir()
                val file = File(dir, fileName)
                val result = file.delete()
                webView.post {
                    webView.evaluateJavascript(
                        "window.__bridgeResult('${params["callbackId"]}', $result);", null
                    )
                }
            }

            "close" -> {
                onClose()
            }

            "callTool" -> {
                val toolName = params["toolName"] ?: ""
                val toolParams = params["params"] ?: "{}"
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val result = callPluginTool(toolName, toolParams)
                        webView.post {
                            webView.evaluateJavascript(
                                "window.__bridgeResult('${params["callbackId"]}', ${result});", null
                            )
                        }
                    } catch (e: Exception) {
                        val errorResult = """{"success":false,"error":"${e.message}"}"""
                        webView.post {
                            webView.evaluateJavascript(
                                "window.__bridgeResult('${params["callbackId"]}', $errorResult);", null
                            )
                        }
                    }
                }
            }

            "callAI" -> {
                val callbackId = params["callbackId"] ?: ""
                val prompt = params["prompt"] ?: ""
                val contextJson = params["context"] ?: "{}"

                if (!pluginInfo.manifest.permissions.contains("ai_chat")) {
                    val errorResult = """{"success":false,"error":"Permission denied: ai_chat permission not declared in manifest"}"""
                    webView.post {
                        webView.evaluateJavascript(
                            "window.__bridgeResult('$callbackId', $errorResult);", null
                        )
                    }
                    return
                }

                if (prompt.isBlank()) {
                    val errorResult = """{"success":false,"error":"prompt is required"}"""
                    webView.post {
                        webView.evaluateJavascript(
                            "window.__bridgeResult('$callbackId', $errorResult);", null
                        )
                    }
                    return
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val aiResult = callAI(prompt, contextJson)
                        webView.post {
                            webView.evaluateJavascript(
                                "window.__bridgeResult('$callbackId', $aiResult);", null
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "callAI failed", e)
                        val errorResult = """{"success":false,"error":"${e.message?.replace("\"", "\\\"")?.replace("\\", "\\\\")}"}"""
                        webView.post {
                            webView.evaluateJavascript(
                                "window.__bridgeResult('$callbackId', $errorResult);", null
                            )
                        }
                    }
                }
            }

            "notifyHook" -> {
                val callbackId = params["callbackId"] ?: ""
                val hookName = params["hookName"] ?: ""
                val hookContextJson = params["context"] ?: "{}"

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val hookResult = handleHookTrigger(webView, hookName, hookContextJson)
                        webView.post {
                            webView.evaluateJavascript(
                                "window.__bridgeResult('$callbackId', $hookResult);", null
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "notifyHook failed", e)
                        val errorResult = """{"success":false,"error":"${e.message?.replace("\"", "\\\"")?.replace("\\", "\\\\")}"}"""
                        webView.post {
                            webView.evaluateJavascript(
                                "window.__bridgeResult('$callbackId', $errorResult);", null
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun callAI(prompt: String, contextJson: String): String {
        val settingsStore: SettingsStore = org.koin.java.KoinJavaComponent.get(SettingsStore::class.java)
        val providerManager: ProviderManager = org.koin.java.KoinJavaComponent.get(ProviderManager::class.java)
        val settings = settingsStore.settingsFlow.first()
        val model = settings.getCurrentChatModel()
            ?: return """{"success":false,"error":"No chat model configured"}"""
        val providerSetting = model.findProvider(settings.providers)
            ?: return """{"success":false,"error":"Provider not found for model"}"""

        val providerImpl = providerManager.getProviderByType(providerSetting)

        val systemPrompt = buildString {
            append("\u4F60\u662F\u4E00\u4E2A\u9605\u8BFB\u52A9\u624B\u3002\u8BF7\u6839\u636E\u7528\u6237\u7684\u95EE\u9898\u7ED9\u51FA\u6709\u6DF1\u5EA6\u7684\u3001\u6E29\u67D4\u7684\u56DE\u7B54\u3002")
            if (contextJson.isNotBlank() && contextJson != "{}") {
                try {
                    val ctx = JSONObject(contextJson)
                    append("\n\n\u5F53\u524D\u9605\u8BFB\u4E0A\u4E0B\u6587\uFF1A")
                    if (ctx.has("book")) append("\n\u4E66\u540D\uFF1A").append(ctx.getString("book"))
                    if (ctx.has("chapter")) append("\n\u7AE0\u8282\uFF1A\u7B2C").append(ctx.getString("chapter")).append("\u7AE0")
                    if (ctx.has("page")) append("\n\u9875\u7801\uFF1A\u7B2C").append(ctx.getString("page")).append("\u9875")
                    if (ctx.has("annotations")) append("\n\u5DF2\u6709\u6279\u6CE8\uFF1A").append(ctx.getString("annotations"))
                    if (ctx.has("content")) append("\n\u9875\u9762\u5185\u5BB9\uFF1A").append(ctx.getString("content"))
                    if (ctx.has("quote")) append("\n\u5F15\u7528\u539F\u6587\uFF1A").append(ctx.getString("quote"))
                    if (ctx.has("note")) append("\n\u7528\u6237\u6279\u6CE8\uFF1A").append(ctx.getString("note"))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse context JSON", e)
                }
            }
        }

        val messages = listOf(
            UIMessage(
                role = MessageRole.SYSTEM,
                parts = listOf(UIMessagePart.Text(systemPrompt))
            ),
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text(prompt))
            )
        )

        val textGenParams = TextGenerationParams(
            model = model,
            tools = emptyList(),
            temperature = 0.7f
        )

        return try {
            val response = providerImpl.generateText(providerSetting, messages, textGenParams)
            val responseText = response.choices.firstOrNull()?.message?.parts
                ?.filterIsInstance<UIMessagePart.Text>()
                ?.firstOrNull()?.text ?: ""

            val escaped = responseText
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            """{"success":true,"text":"$escaped"}"""
        } catch (e: Exception) {
            Log.e(TAG, "AI generation failed", e)
            """{"success":false,"error":"${e.message?.replace("\"", "\\\"")?.replace("\\", "\\\\")}"}"""
        }
    }

    private suspend fun handleHookTrigger(webView: WebView, hookName: String, contextJson: String): String {
        val hookConfig = pluginInfo.manifest.hookConfigs[hookName]
            ?: return """{"success":false,"error":"Hook '$hookName' not configured in manifest"}"""

        when (hookConfig.action) {
            "call_js_function" -> {
                val functionName = hookConfig.function
                    ?: return """{"success":false,"error":"function is required for call_js_function action"}"""

                if (!hookConfig.autoTrigger) {
                    return """{"success":false,"error":"Hook '$hookName' is not auto-triggered"}"""
                }

                val jsCode = "try { if(typeof $functionName === 'function') { $functionName(${escapeJsString(contextJson)}); } } catch(e) { console.error('Hook JS error:', e); }"
                webView.post {
                    webView.evaluateJavascript(jsCode, null)
                }
                return """{"success":true,"action":"call_js_function","function":"$functionName"}"""
            }

            "call_ai" -> {
                val template = hookConfig.promptTemplate
                    ?: return """{"success":false,"error":"promptTemplate is required for call_ai action"}"""

                val contextObj = try {
                    JSONObject(contextJson)
                } catch (e: Exception) {
                    return """{"success":false,"error":"Invalid context JSON"}"""
                }

                var prompt = template
                val variableKeys = listOf("book", "chapter", "page", "quote", "note", "content", "annotations")
                for (key in variableKeys) {
                    if (contextObj.has(key)) {
                        val value = contextObj.getString(key)
                        prompt = prompt.replace("{$key}", value)
                    }
                }

                val aiResult = callAI(prompt, contextJson)
                return aiResult
            }

            else -> {
                return """{"success":false,"error":"Unknown hook action: ${hookConfig.action}"}"""
            }
        }
    }

    private fun escapeJsString(str: String): String {
        val escaped = str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private suspend fun callPluginTool(toolName: String, params: String): String {
        return try {
            val loadedPlugin: LoadedPlugin? = pluginLoader.getAllLoadedPlugins().find { plugin: LoadedPlugin ->
                plugin.info.manifest.tools.any { toolDef -> toolDef.name == toolName }
            }

            if (loadedPlugin == null) {
                return """{"success":false,"error":"Tool not found: $toolName"}"""
            }

            val jsonParams = Json.parseToJsonElement(params)

            val result = pluginLoader.callTool(
                pluginId = loadedPlugin.id,
                toolName = toolName,
                params = jsonParams
            )

            return result.fold(
                onSuccess = { jsonElement: kotlinx.serialization.json.JsonElement ->
                    Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), jsonElement)
                },
                onFailure = { error: Throwable ->
                    """{"success":false,"error":"${error.message}"}"""
                }
            )
        } catch (e: Exception) {
            """{"success":false,"error":"${e.message}"}"""
        }
    }
}

private const val bridgeJavascript = """
(function() {
    if (window.Bridge) return;

    window.__bridgeCallbacks = {};
    window.__bridgeResultId = 0;

    window.__bridgeResult = function(callbackId, result) {
        if (callbackId && window.__bridgeCallbacks[callbackId]) {
            try {
                window.__bridgeCallbacks[callbackId](result);
            } catch(e) {
                console.error('Bridge callback error:', e);
            }
            delete window.__bridgeCallbacks[callbackId];
        }
    };

    function bridgeCall(method, params) {
        return new Promise(function(resolve, reject) {
            var callbackId = 'cb_' + (++window.__bridgeResultId);
            window.__bridgeCallbacks[callbackId] = resolve;

            var url = 'bridge://' + method + '?callbackId=' + encodeURIComponent(callbackId);
            for (var key in params) {
                if (params.hasOwnProperty(key)) {
                    url += '&' + encodeURIComponent(key) + '=' + encodeURIComponent(String(params[key]));
                }
            }

            var iframe = document.createElement('iframe');
            iframe.style.display = 'none';
            iframe.src = url;
            document.body.appendChild(iframe);
            setTimeout(function() {
                document.body.removeChild(iframe);
            }, 100);
        });
    }

    window.Bridge = {
        getPluginConfig: function() {
            return bridgeCall('getPluginConfig', {});
        },
        getData: function(key) {
            return bridgeCall('getData', {key: key});
        },
        setData: function(key, value) {
            return bridgeCall('setData', {key: key, value: value});
        },
        deleteData: function(key) {
            return bridgeCall('deleteData', {key: key});
        },
        listData: function() {
            return bridgeCall('listData', {});
        },
        pickImage: function() {
            return bridgeCall('pickImage', {});
        },
        pickFile: function() {
            return bridgeCall('pickFile', {});
        },
        callTool: function(toolName, params) {
            return bridgeCall('callTool', {toolName: toolName, params: params || '{}'});
        },
        writeFile: function(fileName, base64Data) {
            return bridgeCall('writeFile', {fileName: fileName, data: base64Data});
        },
        readFile: function(fileName) {
            return bridgeCall('readFile', {fileName: fileName});
        },
        listFiles: function(dirPath) {
            return bridgeCall('listFiles', {dir: dirPath || ''});
        },
        deleteFile: function(fileName) {
            return bridgeCall('deleteFile', {fileName: fileName});
        },
        close: function() {
            bridgeCall('close', {});
        },
        callAI: function(prompt, context) {
            return bridgeCall('callAI', {prompt: prompt, context: context || '{}'});
        },
        notifyHook: function(hookName, context) {
            return bridgeCall('notifyHook', {hookName: hookName, context: context || '{}'});
        }
    };

    console.log('Bridge API initialized');
})();
"""

private fun uriToBase64(context: android.content.Context, uri: Uri): String {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return "null"
    val bytes = inputStream.readBytes()
    inputStream.close()
    return "\"" + Base64.encodeToString(bytes, Base64.NO_WRAP) + "\""
}