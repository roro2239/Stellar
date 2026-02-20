#include <jni.h>
#include <unistd.h>
#include <pty.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <termios.h>
#include <stdlib.h>
#include <string.h>

static char **unpackArgs(const jbyte *block, jint count) {
    char **arr = new char *[count + 1];
    const char *p = reinterpret_cast<const char *>(block);
    for (int i = 0; i < count; i++) {
        arr[i] = const_cast<char *>(p);
        p += strlen(p) + 1;
    }
    arr[count] = nullptr;
    return arr;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_rikka_rish_RishHost_start(JNIEnv *env, jclass,
                               jbyteArray argBlock, jint argc,
                               jbyteArray envBlock, jint envc,
                               jbyteArray dirBlock,
                               jbyte tty, jint in, jint out, jint err) {
    jbyte *args = env->GetByteArrayElements(argBlock, nullptr);
    jbyte *envs = envc >= 0 ? env->GetByteArrayElements(envBlock, nullptr) : nullptr;
    jbyte *dir = env->GetByteArrayElements(dirBlock, nullptr);

    char **argv = unpackArgs(args, argc);
    char **envp = envc >= 0 ? unpackArgs(envs, envc) : nullptr;

    int ptmx = -1;
    pid_t pid;

    if (tty) {
        pid = forkpty(&ptmx, nullptr, nullptr, nullptr);
    } else {
        pid = fork();
    }

    if (pid == 0) {
        if (!tty) {
            if (in >= 0) dup2(in, STDIN_FILENO);
            if (out >= 0) dup2(out, STDOUT_FILENO);
            if (err >= 0) dup2(err, STDERR_FILENO);
        }
        if (dir && reinterpret_cast<const char *>(dir)[0]) {
            chdir(reinterpret_cast<const char *>(dir));
        }
        if (envp) {
            execvpe(argv[0], argv, envp);
        } else {
            execvp(argv[0], argv);
        }
        _exit(127);
    }

    env->ReleaseByteArrayElements(argBlock, args, JNI_ABORT);
    if (envs) env->ReleaseByteArrayElements(envBlock, envs, JNI_ABORT);
    env->ReleaseByteArrayElements(dirBlock, dir, JNI_ABORT);
    delete[] argv;
    delete[] envp;

    jintArray result = env->NewIntArray(2);
    jint buf[2] = {pid, ptmx};
    env->SetIntArrayRegion(result, 0, 2, buf);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_rikka_rish_RishHost_setWindowSize(JNIEnv *, jclass, jint fd, jlong size) {
    struct winsize ws;
    ws.ws_col = (unsigned short) (size & 0xffff);
    ws.ws_row = (unsigned short) ((size >> 16) & 0xffff);
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    ioctl(fd, TIOCSWINSZ, &ws);
}

extern "C" JNIEXPORT jint JNICALL
Java_rikka_rish_RishHost_waitFor(JNIEnv *, jclass, jint pid) {
    int status;
    waitpid(pid, &status, 0);
    return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
}
