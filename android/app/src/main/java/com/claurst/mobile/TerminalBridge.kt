package com.claurst.mobile

import android.webkit.JavascriptInterface

/**
 * JavaScript interface exposed to xterm.js inside the terminal [WebView].
 *
 * xterm.js calls [sendInput] whenever the user types, and calls [onResize]
 * whenever the terminal viewport changes dimensions.  The host activity
 * subscribes to [onOutput] to receive data from the process and forward it
 * to xterm.js via `evaluateJavascript`.
 */
class TerminalBridge(private val processManager: ProcessManager) {

    /** Called on the main thread when the process produces output. */
    var onOutput: ((String) -> Unit)?
        get() = processManager.onOutput
        set(value) { processManager.onOutput = value }

    /** Called by xterm.js when the user types something. */
    @JavascriptInterface
    fun sendInput(data: String) {
        processManager.sendInput(data)
    }

    /** Called by xterm.js when the terminal viewport changes size. */
    @JavascriptInterface
    fun onResize(cols: Int, rows: Int) {
        processManager.resize(cols, rows)
    }

    /** Programmatically send data to the process (e.g. ESC from hardware key). */
    fun sendInput(data: ByteArray) {
        processManager.sendInput(String(data, Charsets.UTF_8))
    }
}
