package com.claurst.mobile

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.claurst.mobile.databinding.ActivityMainBinding

/**
 * Home screen of ClaURST Mobile. Provides navigation to the Terminal
 * (sandboxed agent session) and the Workspace file browser.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialise the workspace directory on first launch.
        WorkspaceManager(this).ensureWorkspaceExists()

        binding.btnOpenTerminal.setOnClickListener {
            // Always open the terminal. If the CLAURST binary is not available
            // for this device's ABI, TerminalActivity will fall back to the
            // system shell so users always get a working terminal.
            BinaryInstaller(this).installBinary() // install if not yet done
            startActivity(Intent(this, TerminalActivity::class.java))
        }

        binding.btnOpenWorkspace.setOnClickListener {
            startActivity(Intent(this, WorkspaceActivity::class.java))
        }

        binding.btnOpenSetup.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        binding.tvVersion.text = getString(R.string.version_label, BuildInfo.VERSION)
    }
}
