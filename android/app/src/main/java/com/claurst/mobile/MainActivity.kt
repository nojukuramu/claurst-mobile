package com.claurst.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
            val installer = BinaryInstaller(this)
            val binaryPath = installer.installBinary()
            if (binaryPath != null) {
                startActivity(Intent(this, TerminalActivity::class.java))
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.binary_not_found),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        binding.btnOpenWorkspace.setOnClickListener {
            startActivity(Intent(this, WorkspaceActivity::class.java))
        }

        binding.tvVersion.text = getString(R.string.version_label, BuildInfo.VERSION)
    }
}
