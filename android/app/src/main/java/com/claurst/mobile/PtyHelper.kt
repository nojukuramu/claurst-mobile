package com.claurst.mobile

/**
 * JNI bridge for native PTY operations.
 *
 * The native implementation is in `src/main/jni/pty_helper.c`.
 * All file descriptor integers returned by these methods follow the
 * Unix convention: negative values indicate errors.
 */
class PtyHelper {

    /**
     * Opens `/dev/ptmx` and returns the master file descriptor.
     *
     * @param rows  Terminal height in character rows.
     * @param cols  Terminal width in character columns.
     * @return      Master fd (>= 0) on success, or -1 on failure.
     */
    external fun createPty(rows: Int, cols: Int): Int

    /**
     * Returns the slave device path (e.g. `/dev/pts/N`) for [masterFd].
     *
     * @return Slave device path, or null on failure.
     */
    external fun getSlaveDeviceName(masterFd: Int): String?

    /**
     * Forks a child process with [path] as its executable.
     *
     * The child opens [slaveDevice] as its controlling terminal and sets
     * stdin/stdout/stderr to that slave fd before calling execve.
     *
     * @param path         Absolute path to the executable.
     * @param args         Argument vector (args[0] should be the executable path).
     * @param env          Environment strings in "KEY=VALUE" format.
     * @param slaveDevice  Slave PTY device path returned by [getSlaveDeviceName].
     * @param rows         Terminal height (sent to the slave as TIOCSWINSZ).
     * @param cols         Terminal width.
     * @return             Child PID on success, or -1 on failure.
     */
    external fun forkExec(
        path: String,
        args: Array<String>,
        env: Array<String>,
        slaveDevice: String,
        rows: Int,
        cols: Int
    ): Int

    /**
     * Reads available data from the PTY master into [buf].
     *
     * Blocks until data is available or an error occurs.
     *
     * @return Number of bytes read, 0 on EOF, or negative on error.
     */
    external fun readFromMaster(masterFd: Int, buf: ByteArray): Int

    /**
     * Writes [data] to the PTY master (forwarded to the child's stdin).
     */
    external fun writeToMaster(masterFd: Int, data: ByteArray)

    /** Sends SIGWINCH to resize the terminal window. */
    external fun resize(masterFd: Int, rows: Int, cols: Int)

    /** Sends SIGKILL to the process identified by [pid]. */
    external fun killProcess(pid: Int)

    /** Closes a file descriptor. */
    external fun closeFd(fd: Int)

    companion object {
        init {
            System.loadLibrary("claurst-pty")
        }
    }
}
