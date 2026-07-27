package me.rerere.rikkahub.plugin.webview

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File
import me.rerere.rikkahub.plugin.data.PluginDataStore

internal class PluginWebViewFileLaunchers(
    val pickImage: (String) -> Unit,
    val pickTextFile: (String) -> Unit,
    val pickBinaryFile: (String) -> Unit,
    val importAudioFile: (String) -> Unit,
    val saveFileAs: (String, String, String) -> Unit,
    val chooseHtmlFile: (ValueCallback<Array<Uri>>?, Array<String>) -> Unit,
)

@Composable
internal fun rememberPluginWebViewFileLaunchers(
    context: Context,
    dataStore: PluginDataStore,
    webViewProvider: () -> WebView?,
): PluginWebViewFileLaunchers {
    var pendingImageCallback by remember { mutableStateOf<String?>(null) }
    var pendingFileCallback by remember { mutableStateOf<String?>(null) }
    var pendingBinaryFileCallback by remember { mutableStateOf<String?>(null) }
    var pendingImportAudioCallback by remember { mutableStateOf<String?>(null) }
    var pendingSaveCallbackId by remember { mutableStateOf<String?>(null) }
    var pendingSaveBase64Data by remember { mutableStateOf<String?>(null) }
    var webViewFileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    fun evaluate(script: String) {
        webViewProvider()?.post { webViewProvider()?.evaluateJavascript(script, null) }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val callbackId = pendingFileCallback ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                val fileName = uri.lastPathSegment ?: "unknown.txt"
                evaluate(
                    "window.__bridgeResult('$callbackId', {success:true,fileName:'${fileName.escapeSingleQuotedJs()}',content:'${content.orEmpty().escapeTextFileJs()}'});",
                )
            } catch (e: Exception) {
                evaluate("window.__bridgeResult('$callbackId', {success:false,error:'${e.message.orEmpty().escapeSingleQuotedJs()}'});")
            }
        } else {
            evaluate("window.__bridgeResult('$callbackId', {success:false,error:'User cancelled'});")
        }
        pendingFileCallback = null
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val callbackId = pendingImageCallback ?: return@rememberLauncherForActivityResult
        val result = if (uri != null) {
            try {
                uriToBase64(context, uri)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process picked image", e)
                "null"
            }
        } else {
            "null"
        }
        webViewProvider()?.evaluateJavascript(
            "if(window.__bridgeCallbacks && window.__bridgeCallbacks['$callbackId'])" +
                "{window.__bridgeCallbacks['$callbackId']($result); delete window.__bridgeCallbacks['$callbackId'];}",
            null,
        )
        pendingImageCallback = null
    }

    val binaryPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val callbackId = pendingBinaryFileCallback ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                val base64 = bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }.orEmpty()
                val fileName = uri.lastPathSegment ?: "unknown"
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                evaluate(
                    "window.__bridgeResult('$callbackId', {success:true,fileName:'${fileName.escapeSingleQuotedJs()}',mimeType:'${mimeType.escapeSingleQuotedJs()}',base64:'$base64'});",
                )
            } catch (e: Exception) {
                evaluate("window.__bridgeResult('$callbackId', {success:false,error:'${e.message.orEmpty().escapeSingleQuotedJs()}'});")
            }
        } else {
            evaluate("window.__bridgeResult('$callbackId', {success:false,error:'User cancelled'});")
        }
        pendingBinaryFileCallback = null
    }

    val audioImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val callbackId = pendingImportAudioCallback ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    evaluate("window.__bridgeResult('$callbackId', {success:false,error:'Failed to read file'});")
                } else {
                    var fileName = uri.lastPathSegment ?: "unknown.mp3"
                    val cutIndex = maxOf(fileName.lastIndexOf('/'), fileName.lastIndexOf(':'))
                    if (cutIndex >= 0) fileName = fileName.substring(cutIndex + 1)
                    val musicDir = File(dataStore.getDataDir(), "music").apply { mkdirs() }
                    val targetFile = File(musicDir, fileName).apply { writeBytes(bytes) }
                    evaluate(
                        "window.__bridgeResult('$callbackId', {success:true,filePath:'${targetFile.absolutePath.escapeSingleQuotedJs()}',fileName:'${fileName.escapeSingleQuotedJs()}'});",
                    )
                }
            } catch (e: Exception) {
                evaluate("window.__bridgeResult('$callbackId', {success:false,error:'${e.message.orEmpty().escapeSingleQuotedJs()}'});")
            }
        } else {
            evaluate("window.__bridgeResult('$callbackId', {success:false,error:'User cancelled'});")
        }
        pendingImportAudioCallback = null
    }

    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val callbackId = pendingSaveCallbackId ?: return@rememberLauncherForActivityResult
        val data = pendingSaveBase64Data
        if (uri != null && data != null) {
            try {
                val bytes = Base64.decode(data, Base64.DEFAULT)
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(bytes)
                    it.flush()
                }
                val fileName = uri.lastPathSegment ?: "saved_file"
                evaluate("window.__bridgeResult('$callbackId', {success:true,fileName:'${fileName.escapeSingleQuotedJs()}'});")
            } catch (e: Exception) {
                evaluate("window.__bridgeResult('$callbackId', {success:false,error:'${e.message.orEmpty().escapeSingleQuotedJs()}'});")
            }
        } else {
            evaluate("window.__bridgeResult('$callbackId', {success:false,error:'User cancelled'});")
        }
        pendingSaveCallbackId = null
        pendingSaveBase64Data = null
    }

    val htmlFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        webViewFileChooserCallback?.onReceiveValue(uri?.let { arrayOf(it) })
        webViewFileChooserCallback = null
    }

    return remember(
        filePickerLauncher,
        imagePickerLauncher,
        binaryPickerLauncher,
        audioImportLauncher,
        saveFileLauncher,
        htmlFileLauncher,
    ) {
        PluginWebViewFileLaunchers(
            pickImage = { callbackId ->
                pendingImageCallback = callbackId
                imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            pickTextFile = { callbackId ->
                pendingFileCallback = callbackId
                filePickerLauncher.launch(arrayOf("text/plain", "text/markdown", "application/octet-stream"))
            },
            pickBinaryFile = { callbackId ->
                pendingBinaryFileCallback = callbackId
                binaryPickerLauncher.launch(arrayOf("*/*"))
            },
            importAudioFile = { callbackId ->
                pendingImportAudioCallback = callbackId
                audioImportLauncher.launch(arrayOf("audio/*"))
            },
            saveFileAs = { callbackId, fileName, base64Data ->
                pendingSaveCallbackId = callbackId
                pendingSaveBase64Data = base64Data
                saveFileLauncher.launch(fileName)
            },
            chooseHtmlFile = { callback, acceptTypes ->
                webViewFileChooserCallback?.onReceiveValue(null)
                webViewFileChooserCallback = callback
                htmlFileLauncher.launch(acceptTypes.takeIf { it.isNotEmpty() } ?: arrayOf("*/*"))
            },
        )
    }
}

private fun uriToBase64(context: Context, uri: Uri): String {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return "null"
    return "\"${Base64.encodeToString(bytes, Base64.NO_WRAP)}\""
}

private fun String.escapeSingleQuotedJs(): String = replace("\\", "\\\\").replace("'", "\\'")
private fun String.escapeTextFileJs(): String = escapeSingleQuotedJs().replace("\n", "\\n").replace("\r", "\\r")
private const val TAG = "PluginWebViewPage"
