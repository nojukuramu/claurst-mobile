package com.claurst.mobile

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages the CLAURST subprocess lifecycle.
 *
 * Allocates a PTY pair via the native [PtyHelper], spawns the CLAURST binary
 * with the PTY slave as its controlling terminal, and asynchronously pumps
 * output from the PTY master to whoever subscribes via [onOutput].
 *
 * @param workspaceDir  Absolute path to the sandboxed workspace directory that
 *                      CLAURST will use as its working directory.
 */
class ProcessManager(
    private val workspaceDir: String
) {
    /** Called on a background thread whenever output is available. */
    var onOutput: ((String) -> Unit)? = null

    private val ptyHelper = PtyHelper()
    private val scope = CoroutineScope(Dispatchers.IO)

    private var masterFd: Int = -1
    private var pid: Int = -1
    private var readerJob: Job? = null

    /** Returns true if the process is currently running. */
    fun isRunning(): Boolean = pid > 0

    /**
     * Starts the CLAURST process.
     *
     * @param binaryPath  Absolute path to the executable binary on device.
     * @param cols        Initial terminal width in columns.
     * @param rows        Initial terminal height in rows.
     */
    fun start(binaryPath: String, cols: Int = 80, rows: Int = 24) {
        if (isRunning()) return

        masterFd = ptyHelper.createPty(rows, cols)
        if (masterFd < 0) {
            Log.e(TAG, "Failed to create PTY")
            return
        }

        val slaveName = ptyHelper.getSlaveDeviceName(masterFd) ?: run {
            Log.e(TAG, "Failed to get slave device name")
            return
        }

        val env = buildEnvironment(binaryPath)
        pid = ptyHelper.forkExec(
            path = binaryPath,
            args = arrayOf(binaryPath),
            env = env,
            slaveDevice = slaveName,
            rows = rows,
            cols = cols
        )

        if (pid < 0) {
            Log.e(TAG, "Failed to fork/exec CLAURST")
            return
        }

        Log.i(TAG, "CLAURST started with pid=$pid on $slaveName")
        startOutputReader()
    }

    /** Sends raw input bytes (keyboard data) to the process via the PTY master. */
    fun sendInput(data: String) {
        if (masterFd < 0) return
        scope.launch {
            try {
                ptyHelper.writeToMaster(masterFd, data.toByteArray(Charsets.UTF_8))
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to PTY: ${e.message}")
            }
        }
    }

    /** Notifies the process of a terminal resize event. */
    fun resize(cols: Int, rows: Int) {
        if (masterFd < 0) return
        ptyHelper.resize(masterFd, rows, cols)
    }

    /** Terminates the CLAURST process and releases the PTY. */
    fun stop() {
        readerJob?.cancel()
        if (pid > 0) {
            ptyHelper.killProcess(pid)
            pid = -1
        }
        if (masterFd >= 0) {
            ptyHelper.closeFd(masterFd)
            masterFd = -1
        }
        scope.cancel()
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun startOutputReader() {
        readerJob = scope.launch {
            val buf = ByteArray(4096)
            while (isActive && masterFd >= 0) {
                val n = try {
                    ptyHelper.readFromMaster(masterFd, buf)
                } catch (e: Exception) {
                    Log.w(TAG, "PTY read error: ${e.message}")
                    break
                }
                if (n <= 0) break
                val text = String(buf, 0, n, Charsets.UTF_8)
                onOutput?.invoke(text)
            }
            Log.i(TAG, "PTY reader finished")
        }
    }

    private fun buildEnvironment(binaryPath: String): Array<String> {
        val binDir = File(binaryPath).parent ?: workspaceDir
        return arrayOf(
            "HOME=$workspaceDir",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            "PATH=$binDir:/system/bin:/system/xbin",
            "CLAURST_WORKSPACE=$workspaceDir",
            "CLAURST_NO_UPDATE_CHECK=1"
        )
    }

    companion object {
        private const val TAG = "ProcessManager"
    }
}
