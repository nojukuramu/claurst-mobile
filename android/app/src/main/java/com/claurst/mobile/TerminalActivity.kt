package com.claurst.mobile

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.claurst.mobile.databinding.ActivityTerminalBinding

/**
 * Full-screen terminal activity.
 *
 * Hosts a [WebView] running xterm.js (bundled in assets) which is connected
 * to the CLAURST process via a PTY pair.  The [TerminalBridge] exposes a
 * JavaScript interface so that xterm.js can send key input and receive output.
 */
class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding
    private lateinit var processManager: ProcessManager
    private lateinit var bridge: TerminalBridge

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
            webViewClient = WebViewClient()
            addJavascriptInterface(bridge, "Android")
            loadUrl("file:///android_asset/terminal.html")
        }

        // Wire up terminal output → xterm.js
        bridge.onOutput = { data ->
            val escaped = data
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            runOnUiThread {
                binding.webViewTerminal.evaluateJavascript(
                    "if(typeof writeOutput === 'function') writeOutput(\"$escaped\");",
                    null
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!processManager.isRunning()) {
            val binaryPath = BinaryInstaller(this).installBinary() ?: run {
                Log.e(TAG, "CLAURST binary not available for this architecture")
                finish()
                return
            }
            processManager.start(binaryPath)
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

    companion object {
        private const val TAG = "TerminalActivity"
    }
}
