package com.claurst.mobile

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Installs the CLAURST native binary from app assets into the app's
 * private files directory so it can be executed at runtime.
 *
 * Asset layout (populated by GitHub Actions before APK assembly):
 *
 *   assets/bin/arm64-v8a/claurst
 *   assets/bin/x86_64/claurst
 */
class BinaryInstaller(private val context: Context) {

    private val binDir: File = File(context.filesDir, "bin")
    private val binary: File = File(binDir, "claurst")

    /** Returns true if an installed binary exists for the current ABI. */
    fun isBinaryAvailable(): Boolean = binary.exists() && binary.canExecute()

    /**
     * Copies the architecture-appropriate CLAURST binary from assets to
     * the app's private `files/bin/` directory and marks it executable.
     *
     * @return Absolute path of the installed binary, or null if the ABI is
     *         not supported (i.e. the binary was not bundled for this device).
     */
    fun installBinary(): String? {
        if (isBinaryAvailable()) return binary.absolutePath

        val abi = selectAbi() ?: run {
            Log.e(TAG, "No supported ABI found in ${Build.SUPPORTED_ABIS.toList()}")
            return null
        }

        val assetPath = "bin/$abi/claurst"

        return try {
            binDir.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(binary).use { output ->
                    input.copyTo(output)
                }
            }
            binary.setExecutable(true, false)
            Log.i(TAG, "Installed CLAURST binary from $assetPath to ${binary.absolutePath}")
            binary.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install binary: ${e.message}")
            null
        }
    }

    private fun selectAbi(): String? {
        val supported = Build.SUPPORTED_ABIS
        val bundled = listOf("arm64-v8a", "x86_64")
        return supported.firstOrNull { it in bundled }
    }

    companion object {
        private const val TAG = "BinaryInstaller"
    }
}
