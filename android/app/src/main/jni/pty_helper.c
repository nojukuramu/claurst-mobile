/*
 * pty_helper.c  –  Native PTY allocation and process spawning for ClaURST Mobile.
 *
 * Exposes the following JNI methods to com.claurst.mobile.PtyHelper:
 *
 *   createPty(rows, cols)          → int  (master fd, or -1 on error)
 *   getSlaveDeviceName(masterFd)   → String? (e.g. "/dev/pts/3")
 *   forkExec(path, args, env, slave, rows, cols) → int (child PID, or -1)
 *   readFromMaster(masterFd, buf)  → int (bytes read)
 *   writeToMaster(masterFd, data)  → void
 *   resize(masterFd, rows, cols)   → void
 *   killProcess(pid)               → void
 *   closeFd(fd)                    → void
 */

#include <jni.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include <errno.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <android/log.h>

#define TAG  "ClaURST-PTY"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* -------------------------------------------------------------------------
 * createPty
 * ---------------------------------------------------------------------- */
JNIEXPORT jint JNICALL
Java_com_claurst_mobile_PtyHelper_createPty(
        JNIEnv *env, jobject thiz, jint rows, jint cols)
{
    (void)env; (void)thiz;

    int master = open("/dev/ptmx", O_RDWR | O_CLOEXEC | O_NOCTTY);
    if (master < 0) {
        LOGE("open(/dev/ptmx) failed: %s", strerror(errno));
        return -1;
    }
    if (grantpt(master) < 0 || unlockpt(master) < 0) {
        LOGE("grantpt/unlockpt failed: %s", strerror(errno));
        close(master);
        return -1;
    }

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short)rows;
    ws.ws_col = (unsigned short)cols;
    ioctl(master, TIOCSWINSZ, &ws);

    LOGI("createPty: master=%d rows=%d cols=%d", master, rows, cols);
    return master;
}

/* -------------------------------------------------------------------------
 * getSlaveDeviceName
 * ---------------------------------------------------------------------- */
JNIEXPORT jstring JNICALL
Java_com_claurst_mobile_PtyHelper_getSlaveDeviceName(
        JNIEnv *env, jobject thiz, jint masterFd)
{
    (void)thiz;

    char *name = ptsname(masterFd);
    if (!name) {
        LOGE("ptsname(%d) failed: %s", masterFd, strerror(errno));
        return NULL;
    }
    return (*env)->NewStringUTF(env, name);
}

/* -------------------------------------------------------------------------
 * forkExec
 * ---------------------------------------------------------------------- */
JNIEXPORT jint JNICALL
Java_com_claurst_mobile_PtyHelper_forkExec(
        JNIEnv *env, jobject thiz,
        jstring jpath,
        jobjectArray jargs,
        jobjectArray jenv,
        jstring jslaveDevice,
        jint rows, jint cols)
{
    (void)thiz;

    const char *path        = (*env)->GetStringUTFChars(env, jpath,        NULL);
    const char *slaveDevice = (*env)->GetStringUTFChars(env, jslaveDevice, NULL);

    int argc = (*env)->GetArrayLength(env, jargs);
    char **argv = (char **)malloc((size_t)(argc + 1) * sizeof(char *));
    for (int i = 0; i < argc; i++) {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, jargs, i);
        argv[i] = (char *)(*env)->GetStringUTFChars(env, s, NULL);
    }
    argv[argc] = NULL;

    int envc = (*env)->GetArrayLength(env, jenv);
    char **envp = (char **)malloc((size_t)(envc + 1) * sizeof(char *));
    for (int i = 0; i < envc; i++) {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, jenv, i);
        envp[i] = (char *)(*env)->GetStringUTFChars(env, s, NULL);
    }
    envp[envc] = NULL;

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork() failed: %s", strerror(errno));
        free(argv);
        free(envp);
        (*env)->ReleaseStringUTFChars(env, jpath,        path);
        (*env)->ReleaseStringUTFChars(env, jslaveDevice, slaveDevice);
        return -1;
    }

    if (pid == 0) {
        /* ---- child ---- */
        setsid();

        int slave = open(slaveDevice, O_RDWR);
        if (slave < 0) {
            LOGE("child: open(%s) failed: %s", slaveDevice, strerror(errno));
            _exit(1);
        }

        ioctl(slave, TIOCSCTTY, (char *)NULL);

        struct winsize ws;
        memset(&ws, 0, sizeof(ws));
        ws.ws_row = (unsigned short)rows;
        ws.ws_col = (unsigned short)cols;
        ioctl(slave, TIOCSWINSZ, &ws);

        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) close(slave);

        /* Close all other file descriptors. */
        long max_fd = sysconf(_SC_OPEN_MAX);
        if (max_fd < 0) max_fd = 1024;
        for (int fd = STDERR_FILENO + 1; fd < (int)max_fd; fd++) close(fd);

        execve(path, argv, envp);
        LOGE("execve(%s) failed: %s", path, strerror(errno));
        _exit(1);
    }

    /* ---- parent ---- */
    /* Release all JNI string references and free heap arrays. */
    (*env)->ReleaseStringUTFChars(env, jpath,        path);
    (*env)->ReleaseStringUTFChars(env, jslaveDevice, slaveDevice);

    for (int i = 0; i < argc; i++) {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, jargs, i);
        (*env)->ReleaseStringUTFChars(env, s, argv[i]);
    }
    free(argv);

    for (int i = 0; i < envc; i++) {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, jenv, i);
        (*env)->ReleaseStringUTFChars(env, s, envp[i]);
    }
    free(envp);

    LOGI("forkExec: child pid=%d", pid);
    return (jint)pid;
}

/* -------------------------------------------------------------------------
 * readFromMaster
 * ---------------------------------------------------------------------- */
JNIEXPORT jint JNICALL
Java_com_claurst_mobile_PtyHelper_readFromMaster(
        JNIEnv *env, jobject thiz, jint masterFd, jbyteArray jbuf)
{
    (void)thiz;

    jsize len = (*env)->GetArrayLength(env, jbuf);
    jbyte *buf = (*env)->GetByteArrayElements(env, jbuf, NULL);

    ssize_t n = read(masterFd, buf, (size_t)len);

    (*env)->ReleaseByteArrayElements(env, jbuf, buf, 0);
    return (jint)n;
}

/* -------------------------------------------------------------------------
 * writeToMaster
 * ---------------------------------------------------------------------- */
JNIEXPORT void JNICALL
Java_com_claurst_mobile_PtyHelper_writeToMaster(
        JNIEnv *env, jobject thiz, jint masterFd, jbyteArray jdata)
{
    (void)thiz;

    jsize len = (*env)->GetArrayLength(env, jdata);
    jbyte *data = (*env)->GetByteArrayElements(env, jdata, NULL);

    ssize_t written = 0;
    while (written < len) {
        ssize_t n = write(masterFd, data + written, (size_t)(len - written));
        if (n < 0) {
            LOGE("write to PTY master failed: %s", strerror(errno));
            break;
        }
        written += n;
    }

    (*env)->ReleaseByteArrayElements(env, jdata, data, JNI_ABORT);
}

/* -------------------------------------------------------------------------
 * resize
 * ---------------------------------------------------------------------- */
JNIEXPORT void JNICALL
Java_com_claurst_mobile_PtyHelper_resize(
        JNIEnv *env, jobject thiz, jint masterFd, jint rows, jint cols)
{
    (void)env; (void)thiz;

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short)rows;
    ws.ws_col = (unsigned short)cols;
    ioctl(masterFd, TIOCSWINSZ, &ws);
}

/* -------------------------------------------------------------------------
 * killProcess
 * ---------------------------------------------------------------------- */
JNIEXPORT void JNICALL
Java_com_claurst_mobile_PtyHelper_killProcess(
        JNIEnv *env, jobject thiz, jint pid)
{
    (void)env; (void)thiz;
    kill((pid_t)pid, SIGKILL);
    waitpid((pid_t)pid, NULL, WNOHANG);
}

/* -------------------------------------------------------------------------
 * closeFd
 * ---------------------------------------------------------------------- */
JNIEXPORT void JNICALL
Java_com_claurst_mobile_PtyHelper_closeFd(
        JNIEnv *env, jobject thiz, jint fd)
{
    (void)env; (void)thiz;
    close(fd);
}
