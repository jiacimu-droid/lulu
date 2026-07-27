package me.rerere.rikkahub.plugin.webview

internal const val PLUGIN_BRIDGE_JAVASCRIPT = """
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
        getPluginConfig: function() { return bridgeCall('getPluginConfig', {}); },
        getData: function(key) { return bridgeCall('getData', {key: key}); },
        setData: function(key, value) { return bridgeCall('setData', {key: key, value: value}); },
        deleteData: function(key) { return bridgeCall('deleteData', {key: key}); },
        listData: function() { return bridgeCall('listData', {}); },
        pickImage: function() { return bridgeCall('pickImage', {}); },
        pickFile: function() { return bridgeCall('pickFile', {}); },
        pickBinaryFile: function() { return bridgeCall('pickBinaryFile', {}); },
        importAudioFile: function() { return bridgeCall('importAudioFile', {}); },
        callTool: function(toolName, params) { return bridgeCall('callTool', {toolName: toolName, params: params || '{}'}); },
        writeFile: function(fileName, base64Data) { return bridgeCall('writeFile', {fileName: fileName, data: base64Data}); },
        saveFileAs: function(fileName, base64Data) { return bridgeCall('saveFileAs', {fileName: fileName, data: base64Data}); },
        readFile: function(fileName) { return bridgeCall('readFile', {fileName: fileName}); },
        listFiles: function(dirPath) { return bridgeCall('listFiles', {dir: dirPath || ''}); },
        deleteFile: function(fileName) { return bridgeCall('deleteFile', {fileName: fileName}); },
        close: function() { bridgeCall('close', {}); },
        callAI: function(prompt, context) { return bridgeCall('callAI', {prompt: prompt, context: context || '{}'}); },
        notifyHook: function(hookName, context) { return bridgeCall('notifyHook', {hookName: hookName, context: context || '{}'}); },
        startTimer: function(seconds) { return bridgeCall('startTimer', {seconds: seconds}); },
        stopTimer: function() { return bridgeCall('stopTimer', {}); },
        getTimerState: function() { return bridgeCall('getTimerState', {}); },
        showOverlay: function(html) { return bridgeCall('showOverlay', {html: html}); },
        hideOverlay: function() { return bridgeCall('hideOverlay', {}); },
        musicPlay: function(filePath, title, artist) { return bridgeCall('musicPlay', {filePath: filePath, title: title || '', artist: artist || ''}); },
        musicPause: function() { return bridgeCall('musicPause', {}); },
        musicResume: function() { return bridgeCall('musicResume', {}); },
        musicStop: function() { return bridgeCall('musicStop', {}); }
    };

    window.onTimerEnd = function() {};
    console.log('Bridge API initialized');
})();
"""

internal const val OVERLAY_BRIDGE_JAVASCRIPT = """
(function() {
    if (window.__overlayBridgeReady) return;
    window.__overlayBridgeReady = true;

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
            setTimeout(function() { document.body.removeChild(iframe); }, 100);
        });
    }

    window.Bridge = {
        hideOverlay: function() { return bridgeCall('hideOverlay', {}); },
        getTimerState: function() { return bridgeCall('getTimerState', {}); },
        getData: function(key) { return bridgeCall('getData', {key: key}); },
        setData: function(key, value) { return bridgeCall('setData', {key: key, value: value}); },
        callAI: function(prompt, context) { return bridgeCall('callAI', {prompt: prompt, context: context || '{}'}); }
    };

    window.onTimerEnd = function() {};
    console.log('Overlay Bridge API initialized');
})();
"""
