package com.claurst.mobile

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.claurst.mobile.databinding.ActivityTerminalBinding
import org.json.JSONArray

/**
 * Full-screen terminal activity.
 *
 * Hosts a [WebView] running xterm.js (bundled in assets) which is connected
 * to the CLAURST process via a PTY pair.  The [TerminalBridge] exposes a
 * JavaScript interface so that xterm.js can send key input and receive output.
 *
 * Output that arrives before the page finishes loading is buffered and flushed
 * once [WebViewClient.onPageFinished] fires.
 */
class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding
    private lateinit var processManager: ProcessManager
    private lateinit var bridge: TerminalBridge

    /** Guards [pendingOutput] and [webViewReady]. */
    private val outputLock = Any()
    private val pendingOutput = StringBuilder()
    private var webViewReady = false
    private var terminalStartFailed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        val workspaceDir = WorkspaceManager(this).workspaceDir.absolutePath
        processManager = ProcessManager(workspaceDir)
        bridge = TerminalBridge(processManager)

        binding.webViewTerminal.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    flushPendingOutput()
                }
            }
            addJavascriptInterface(bridge, "Android")
            loadUrl("file:///android_asset/terminal.html")
        }
        setStatusUi(getString(R.string.terminal_status_loading), showOverlay = true, showSpinner = true)

        // Wire up terminal output → xterm.js, buffering until the page is ready.
        bridge.onOutput = { data ->
            synchronized(outputLock) {
                if (webViewReady) {
                    writeToTerminal(data)
                } else {
                    pendingOutput.append(data)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!processManager.isRunning()) {
            val binaryPath = BinaryInstaller(this).installBinary()
            if (binaryPath == null) {
                Log.w(TAG, "CLAURST binary not available; falling back to system shell")
                setStatusUi(getString(R.string.terminal_status_deploying_shell), showOverlay = true, showSpinner = true)
            } else {
                setStatusUi(getString(R.string.terminal_status_deploying_claurst), showOverlay = true, showSpinner = true)
            }
            processManager.start(binaryPath)
            if (!processManager.isRunning()) {
                terminalStartFailed = true
                setStatusUi(getString(R.string.terminal_status_failed), showOverlay = true, showSpinner = false)
            } else {
                terminalStartFailed = false
                // If onPageFinished already fired before the process was ready, the
                // overlay was left visible.  Dismiss it now that both sides are up.
                dismissOverlayIfReady()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Keep the process running in the background.
    }

    override fun onDestroy() {
        super.onDestroy()
        processManager.stop()
    }

    /** Forward hardware key events (e.g. volume-down as escape) to the terminal. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // Volume-down is mapped to ESC so users can exit insert mode in
            // vi-like applications.  A brief overlay hint is shown on first use.
            bridge.sendInput("\u001b") // ESC
            showEscHintOnce()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Called once the WebView page is ready; flushes any buffered output. */
    private fun flushPendingOutput() {
        val buffered: String
        synchronized(outputLock) {
            webViewReady = true
            buffered = pendingOutput.toString()
            pendingOutput.clear()
        }
        if (buffered.isNotEmpty()) {
            writeToTerminal(buffered)
        }
        if (terminalStartFailed) {
            setStatusUi(getString(R.string.terminal_status_failed), showOverlay = true, showSpinner = false)
            return
        }
        // Dismiss the overlay only when both the WebView and the process are ready.
        // If the process hasn't started yet (onResume hasn't reached
        // processManager.start()), leave the current overlay — onResume will call
        // dismissOverlayIfReady() once the process is running.
        dismissOverlayIfReady()
    }

    /**
     * Hides the loading overlay when both the WebView and the process are ready.
     *
     * Must be called from the main thread.
     *
     * @return true if the overlay was dismissed (both conditions met), false otherwise.
     */
    private fun dismissOverlayIfReady(): Boolean {
        if (webViewReady && processManager.isRunning()) {
            setStatusUi(getString(R.string.terminal_status_ready), showOverlay = false, showSpinner = false)
            return true
        }
        return false
    }

    /**
     * Sends [data] to xterm.js via [WebView.evaluateJavascript].
     *
     * Uses [JSONArray] to produce a properly JSON-encoded string literal so
     * that any special characters in the process output cannot break the
     * JavaScript call.
     */
    private fun writeToTerminal(data: String) {
        // JSONArray with a single string element gives us ["data"] which we
        // can index at 0 to get a safe JS string literal for any byte content.
        val jsonLiteral = JSONArray().apply { put(data) }.toString()
        // jsonLiteral is ["<escaped-data>"], so [0] evaluates to the string.
        val js = "if(typeof writeOutput==='function') writeOutput($jsonLiteral[0]);"
        runOnUiThread {
            binding.webViewTerminal.evaluateJavascript(js, null)
        }
    }

    private fun showEscHintOnce() {
        val prefs = getSharedPreferences("claurst_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("esc_hint_shown", false)) {
            android.widget.Toast.makeText(
                this,
                "Volume ↓ = ESC",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            prefs.edit().putBoolean("esc_hint_shown", true).apply()
        }
    }

    private fun setStatusUi(message: String, showOverlay: Boolean, showSpinner: Boolean) {
        runOnUiThread {
            binding.tvTerminalStatus.text = message
            binding.progressTerminal.visibility =
                if (showSpinner) android.view.View.VISIBLE else android.view.View.GONE
            binding.terminalStatusOverlay.visibility =
                if (showOverlay) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    companion object {
        private const val TAG = "TerminalActivity"
    }
}
