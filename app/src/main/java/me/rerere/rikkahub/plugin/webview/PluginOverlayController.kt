package me.rerere.rikkahub.plugin.webview

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.plugin.data.PluginDataStore

internal class PluginOverlayController(
    private val context: Context,
    private val dataStore: PluginDataStore,
) {
    private var overlayWebView: WebView? = null

    fun notifyTimerEnd() {
        overlayWebView?.post {
            overlayWebView?.evaluateJavascript(
                "if(typeof window.onTimerEnd === 'function') { window.onTimerEnd(); }",
                null,
            )
        }
    }

    fun show(sourceWebView: WebView, html: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            sourceWebView.respondError("Overlay permission not granted")
            return
        }
        val activity = context as? Activity
        val windowManager = activity?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (windowManager == null) {
            sourceWebView.respondError("Cannot access WindowManager")
            return
        }
        removeOverlay(windowManager)

        val overlay = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            isFocusable = true
            isFocusableInTouchMode = true
        }
        overlay.requestFocus()
        overlay.webViewClient = overlayClient(windowManager, overlay)
        overlay.setOnKeyListener { _, keyCode, event ->
            keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        overlay.loadDataWithBaseURL("https://rikkahub.local", html, "text/html", "UTF-8", null)
        windowManager.addView(overlay, params)
        overlayWebView = overlay
    }

    fun hide() {
        val windowManager = (context as? Activity)?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        removeOverlay(windowManager)
    }

    fun destroy() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        removeOverlay(windowManager)
    }

    private fun removeOverlay(windowManager: WindowManager?) {
        overlayWebView?.let { overlay ->
            try {
                windowManager?.removeView(overlay)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing overlay", e)
            }
            overlay.destroy()
            overlayWebView = null
        }
    }

    private fun overlayClient(windowManager: WindowManager, overlay: WebView): WebViewClient {
        return object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(OVERLAY_BRIDGE_JAVASCRIPT, null)
                view?.evaluateJavascript(
                    "document.addEventListener('click', function(e){ if(e.target.tagName==='INPUT'||e.target.tagName==='TEXTAREA'){ e.target.focus(); } });",
                    null,
                )
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (!url.startsWith("bridge://")) return false
                val uri = Uri.parse(url)
                val method = uri.host ?: return false
                val params = uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() }
                val callbackId = params["callbackId"].orEmpty()
                when (method) {
                    "hideOverlay" -> removeOverlay(windowManager)
                    "getTimerState" -> respond(
                        overlay,
                        callbackId,
                        "{running:${PomodoroTimerService.isRunning()},remaining:${PomodoroTimerService.getRemainingSeconds()}}",
                    )
                    "getData" -> {
                        val value = dataStore.getData(params["key"].orEmpty())
                        respond(overlay, callbackId, value?.let { "\"${it.jsonEscape()}\"" } ?: "null")
                    }
                    "setData" -> {
                        dataStore.setData(params["key"].orEmpty(), params["value"].orEmpty())
                        respond(overlay, callbackId, "true")
                    }
                    "callAI" -> callAi(overlay, callbackId, params["prompt"].orEmpty(), params["context"] ?: "{}")
                }
                return true
            }
        }
    }

    private fun callAi(webView: WebView, callbackId: String, prompt: String, contextJson: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settingsStore: SettingsStore = org.koin.java.KoinJavaComponent.get(SettingsStore::class.java)
                val providerManager: ProviderManager = org.koin.java.KoinJavaComponent.get(ProviderManager::class.java)
                val settings = settingsStore.settingsFlow.first()
                val model = settings.getCurrentChatModel()
                val result = if (model == null) {
                    """{"success":false,"error":"No chat model configured"}"""
                } else {
                    val providerSetting = model.findProvider(settings.providers)
                    if (providerSetting == null) {
                        """{"success":false,"error":"Provider not found"}"""
                    } else {
                        val provider = providerManager.getProviderByType(providerSetting)
                        val systemPrompt = "你是一个番茄钟陪伴助手。用户正在使用番茄钟专注。请用简短温暖的话回应，鼓励用户保持专注。当前上下文：$contextJson"
                        val messages = listOf(
                            UIMessage(MessageRole.SYSTEM, listOf(UIMessagePart.Text(systemPrompt))),
                            UIMessage(MessageRole.USER, listOf(UIMessagePart.Text(prompt))),
                        )
                        val response = provider.generateText(
                            providerSetting,
                            messages,
                            TextGenerationParams(model = model, tools = emptyList(), temperature = 0.7f),
                        )
                        val text = response.choices.firstOrNull()?.message?.parts
                            ?.filterIsInstance<UIMessagePart.Text>()
                            ?.firstOrNull()?.text.orEmpty()
                        """{"success":true,"text":"${text.jsonEscape()}"}"""
                    }
                }
                respond(webView, callbackId, result)
            } catch (e: Exception) {
                respond(webView, callbackId, """{"success":false,"error":"${e.message.jsonEscape()}"}""")
            }
        }
    }

    private fun respond(webView: WebView, callbackId: String, result: String) {
        webView.post { webView.evaluateJavascript("window.__bridgeResult('$callbackId', $result);", null) }
    }

    private fun WebView.respondError(message: String) {
        post {
            evaluateJavascript(
                "window.__bridgeResult('${null}', {success:false,error:'${message.replace("'", "\\'")}'});",
                null,
            )
        }
    }

    private fun String?.jsonEscape(): String = this.orEmpty()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private companion object {
        const val TAG = "PluginWebViewPage"
    }
}
