package me.rerere.rikkahub.plugin.webview

import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
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
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.plugin.data.PluginDataStore
import me.rerere.rikkahub.plugin.loader.LoadedPlugin
import me.rerere.rikkahub.plugin.loader.PluginLoader
import me.rerere.rikkahub.plugin.manager.PluginManager
import me.rerere.rikkahub.plugin.model.PluginInfo
import me.rerere.rikkahub.plugin.repository.PluginRepository
import org.json.JSONArray
import org.json.JSONObject

internal class PluginBridgeWebViewClient(
    private val pluginInfo: PluginInfo,
    private val dataStore: PluginDataStore,
    private val pluginLoader: PluginLoader,
    @Suppress("unused") private val pluginManager: PluginManager,
    private val pluginRepository: PluginRepository,
    private val onPickImage: (callbackId: String) -> Unit,
    private val onPickFile: (callbackId: String) -> Unit,
    private val onPickBinaryFile: (callbackId: String) -> Unit,
    private val onImportAudioFile: (callbackId: String) -> Unit,
    private val onSaveFileAs: (callbackId: String, fileName: String, base64Data: String) -> Unit,
    private val onClose: () -> Unit,
    private val onStartTimer: (webView: WebView, seconds: Int) -> Unit,
    private val onStopTimer: () -> Unit,
    private val onShowOverlay: (webView: WebView, html: String) -> Unit,
    private val onHideOverlay: () -> Unit,
) : WebViewClient() {
    private val json = Json { ignoreUnknownKeys = true }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (url.startsWith("bridge://")) {
            handleBridgeCall(view ?: return true, url)
            return true
        }
        if (!url.startsWith("file://") && !url.startsWith("about:blank")) {
            try {
                view?.context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Log.w(TAG, "Cannot open external URL: $url", e)
            }
            return true
        }
        return false
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.evaluateJavascript(PLUGIN_BRIDGE_JAVASCRIPT, null)
    }

    private fun handleBridgeCall(webView: WebView, url: String) {
        val uri = Uri.parse(url)
        val method = uri.host ?: return
        val params = uri.queryParameterNames.associateWith { uri.getQueryParameter(it) ?: "" }
        val callbackId = params["callbackId"].orEmpty()

        when (method) {
            "getPluginConfig" -> loadPluginConfig(webView, callbackId)
            "getData" -> respond(webView, callbackId, dataStore.getData(params["key"].orEmpty()).toJsonStringOrNull())
            "setData" -> {
                dataStore.setData(params["key"].orEmpty(), params["value"].orEmpty())
                respond(webView, callbackId, "true")
            }
            "deleteData" -> {
                dataStore.deleteData(params["key"].orEmpty())
                respond(webView, callbackId, "true")
            }
            "listData" -> respond(webView, callbackId, JSONArray(dataStore.listData()).toString())
            "pickImage" -> onPickImage(callbackId)
            "pickFile" -> onPickFile(callbackId)
            "pickBinaryFile" -> onPickBinaryFile(callbackId)
            "importAudioFile" -> onImportAudioFile(callbackId)
            "writeFile" -> writePluginFile(webView, callbackId, params["fileName"].orEmpty(), params["data"].orEmpty())
            "readFile" -> readPluginFile(webView, callbackId, params["fileName"].orEmpty())
            "listFiles" -> listPluginFiles(webView, callbackId, params["dir"].orEmpty())
            "deleteFile" -> {
                val result = File(dataStore.getDataDir(), params["fileName"].orEmpty()).delete()
                respond(webView, callbackId, result.toString())
            }
            "musicPlay" -> runMusicAction(webView, callbackId) {
                MusicPlayerService.play(
                    webView.context,
                    params["filePath"].orEmpty(),
                    params["title"].orEmpty(),
                    params["artist"].orEmpty(),
                )
            }
            "musicPause" -> runMusicAction(webView, callbackId) { MusicPlayerService.pause(webView.context) }
            "musicResume" -> runMusicAction(webView, callbackId) { MusicPlayerService.resume(webView.context) }
            "musicStop" -> runMusicAction(webView, callbackId) { MusicPlayerService.stop(webView.context) }
            "close" -> onClose()
            "callTool" -> callTool(webView, callbackId, params["toolName"].orEmpty(), params["params"] ?: "{}")
            "callAI" -> callAiFromBridge(webView, callbackId, params["prompt"].orEmpty(), params["context"] ?: "{}")
            "notifyHook" -> notifyHook(webView, callbackId, params["hookName"].orEmpty(), params["context"] ?: "{}")
            "startTimer" -> {
                onStartTimer(webView, params["seconds"]?.toIntOrNull() ?: 25 * 60)
                respond(webView, callbackId, "{success:true}")
            }
            "stopTimer" -> {
                onStopTimer()
                respond(webView, callbackId, "{success:true}")
            }
            "getTimerState" -> respond(
                webView,
                callbackId,
                "{running:${PomodoroTimerService.isRunning()},remaining:${PomodoroTimerService.getRemainingSeconds()}}",
            )
            "showOverlay" -> {
                onShowOverlay(webView, params["html"].orEmpty())
                respond(webView, callbackId, "{success:true}")
            }
            "saveFileAs" -> onSaveFileAs(
                callbackId,
                params["fileName"] ?: "untitled.json",
                params["data"].orEmpty(),
            )
            "hideOverlay" -> {
                onHideOverlay()
                respond(webView, callbackId, "{success:true}")
            }
        }
    }

    private fun loadPluginConfig(webView: WebView, callbackId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val savedConfig = pluginRepository.getPluginConfig(pluginInfo.manifest.id)
                val mergedConfig = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                pluginInfo.manifest.config.forEach { field ->
                    savedConfig[field.name]?.let { mergedConfig[field.name] = it }
                        ?: field.default?.let { mergedConfig[field.name] = it }
                }
                savedConfig.forEach { (key, value) -> mergedConfig.putIfAbsent(key, value) }
                val result = json.encodeToString(JsonObject.serializer(), JsonObject(mergedConfig))
                Log.d(TAG, "getPluginConfig for ${pluginInfo.manifest.id}: $result")
                respond(webView, callbackId, result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get plugin config", e)
                respond(webView, callbackId, "{}")
            }
        }
    }

    private fun writePluginFile(webView: WebView, callbackId: String, fileName: String, data: String) {
        try {
            val file = File(dataStore.getDataDir(), fileName)
            file.writeBytes(Base64.decode(data, Base64.DEFAULT))
            respond(webView, callbackId, "'${file.absolutePath.escapeSingleQuotedJs()}'")
        } catch (_: Exception) {
            respond(webView, callbackId, "null")
        }
    }

    private fun readPluginFile(webView: WebView, callbackId: String, fileName: String) {
        try {
            val file = File(dataStore.getDataDir(), fileName)
            if (!file.exists()) {
                respond(webView, callbackId, "null")
                return
            }
            respond(webView, callbackId, "'${Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)}'")
        } catch (_: Exception) {
            respond(webView, callbackId, "null")
        }
    }

    private fun listPluginFiles(webView: WebView, callbackId: String, dirPath: String) {
        val baseDir = if (dirPath.isEmpty()) dataStore.getDataDir() else File(dataStore.getDataDir(), dirPath)
        val files = if (baseDir.exists() && baseDir.isDirectory) {
            baseDir.listFiles()?.map { it.name }.orEmpty()
        } else {
            emptyList()
        }
        respond(webView, callbackId, JSONArray(files).toString())
    }

    private fun runMusicAction(webView: WebView, callbackId: String, action: () -> Unit) {
        try {
            action()
            respond(webView, callbackId, "{success:true}")
        } catch (e: Exception) {
            val error = e.message?.escapeSingleQuotedJs() ?: "Unknown error"
            respond(webView, callbackId, "{success:false,error:'$error'}")
        }
    }

    private fun callTool(webView: WebView, callbackId: String, toolName: String, toolParams: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                respond(webView, callbackId, callPluginTool(toolName, toolParams))
            } catch (e: Exception) {
                respond(webView, callbackId, """{"success":false,"error":"${e.message}"}""")
            }
        }
    }

    private fun callAiFromBridge(webView: WebView, callbackId: String, prompt: String, contextJson: String) {
        if (!pluginInfo.manifest.permissions.contains("ai_chat")) {
            respond(webView, callbackId, """{"success":false,"error":"Permission denied: ai_chat permission not declared in manifest"}""")
            return
        }
        if (prompt.isBlank()) {
            respond(webView, callbackId, """{"success":false,"error":"prompt is required"}""")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                respond(webView, callbackId, callAI(prompt, contextJson))
            } catch (e: Exception) {
                Log.e(TAG, "callAI failed", e)
                respond(webView, callbackId, """{"success":false,"error":"${e.message.jsonEscape()}"}""")
            }
        }
    }

    private fun notifyHook(webView: WebView, callbackId: String, hookName: String, contextJson: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                respond(webView, callbackId, handleHookTrigger(webView, hookName, contextJson))
            } catch (e: Exception) {
                Log.e(TAG, "notifyHook failed", e)
                respond(webView, callbackId, """{"success":false,"error":"${e.message.jsonEscape()}"}""")
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
            append("你是一个阅读助手。请根据用户的问题给出有深度的、温柔的回答。")
            if (contextJson.isNotBlank() && contextJson != "{}") {
                try {
                    val context = JSONObject(contextJson)
                    append("\n\n当前阅读上下文：")
                    if (context.has("book")) append("\n书名：").append(context.getString("book"))
                    if (context.has("chapter")) append("\n章节：第").append(context.getString("chapter")).append("章")
                    if (context.has("page")) append("\n页码：第").append(context.getString("page")).append("页")
                    if (context.has("annotations")) append("\n已有批注：").append(context.getString("annotations"))
                    if (context.has("content")) append("\n页面内容：").append(context.getString("content"))
                    if (context.has("quote")) append("\n引用原文：").append(context.getString("quote"))
                    if (context.has("note")) append("\n用户批注：").append(context.getString("note"))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse context JSON", e)
                }
            }
        }
        val messages = listOf(
            UIMessage(role = MessageRole.SYSTEM, parts = listOf(UIMessagePart.Text(systemPrompt))),
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text(prompt))),
        )
        val params = TextGenerationParams(model = model, tools = emptyList(), temperature = 0.7f)
        return try {
            val response = providerImpl.generateText(providerSetting, messages, params)
            val text = response.choices.firstOrNull()?.message?.parts
                ?.filterIsInstance<UIMessagePart.Text>()
                ?.firstOrNull()?.text.orEmpty()
            """{"success":true,"text":"${text.jsonEscape()}"}"""
        } catch (e: Exception) {
            Log.e(TAG, "AI generation failed", e)
            """{"success":false,"error":"${e.message.jsonEscape()}"}"""
        }
    }

    private suspend fun handleHookTrigger(webView: WebView, hookName: String, contextJson: String): String {
        val hookConfig = pluginInfo.manifest.hookConfigs[hookName]
            ?: return """{"success":false,"error":"Hook '$hookName' not configured in manifest"}"""
        return when (hookConfig.action) {
            "call_js_function" -> {
                val functionName = hookConfig.function
                    ?: return """{"success":false,"error":"function is required for call_js_function action"}"""
                if (!hookConfig.autoTrigger) {
                    return """{"success":false,"error":"Hook '$hookName' is not auto-triggered"}"""
                }
                val code = "try { if(typeof $functionName === 'function') { $functionName(${escapeJsString(contextJson)}); } } catch(e) { console.error('Hook JS error:', e); }"
                webView.post { webView.evaluateJavascript(code, null) }
                """{"success":true,"action":"call_js_function","function":"$functionName"}"""
            }
            "call_ai" -> {
                val template = hookConfig.promptTemplate
                    ?: return """{"success":false,"error":"promptTemplate is required for call_ai action"}"""
                val context = try {
                    JSONObject(contextJson)
                } catch (_: Exception) {
                    return """{"success":false,"error":"Invalid context JSON"}"""
                }
                var prompt = template
                listOf("book", "chapter", "page", "quote", "note", "content", "annotations").forEach { key ->
                    if (context.has(key)) prompt = prompt.replace("{$key}", context.getString(key))
                }
                callAI(prompt, contextJson)
            }
            else -> """{"success":false,"error":"Unknown hook action: ${hookConfig.action}"}"""
        }
    }

    private suspend fun callPluginTool(toolName: String, params: String): String {
        return try {
            val loadedPlugin: LoadedPlugin = pluginLoader.getAllLoadedPlugins().find { plugin ->
                plugin.info.manifest.tools.any { it.name == toolName }
            } ?: return """{"success":false,"error":"Tool not found: $toolName"}"""
            val result = pluginLoader.callTool(
                pluginId = loadedPlugin.id,
                toolName = toolName,
                params = Json.parseToJsonElement(params),
            )
            result.fold(
                onSuccess = { Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), it) },
                onFailure = { """{"success":false,"error":"${it.message}"}""" },
            )
        } catch (e: Exception) {
            """{"success":false,"error":"${e.message}"}"""
        }
    }

    private fun respond(webView: WebView, callbackId: String, result: String) {
        webView.post {
            webView.evaluateJavascript("window.__bridgeResult('$callbackId', $result);", null)
        }
    }

    private fun String?.toJsonStringOrNull(): String = this?.let { "\"${it.jsonEscape()}\"" } ?: "null"
    private fun String.escapeSingleQuotedJs(): String = replace("\\", "\\\\").replace("'", "\\'")
    private fun String?.jsonEscape(): String = this.orEmpty()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    private fun escapeJsString(value: String): String = "\"${value.jsonEscape()}\""

    private companion object {
        const val TAG = "PluginWebViewPage"
    }
}
