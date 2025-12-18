#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <unistd.h>
#include <dirent.h>
#include <ctime>
#include <cstring>
#include <libgen.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <cerrno>
#include <string>
#include <termios.h>
#include <vector>
#include <string>
#include <regex>
#include <random>
#include "android.h"
#include "misc.h"
#include "selinux.h"
#include "cgroup.h"
#include "logging.h"

#ifdef DEBUG
#define JAVA_DEBUGGABLE
#endif

#define perrorf(...) fprintf(stderr, __VA_ARGS__)

#define EXIT_FATAL_SET_CLASSPATH 3
#define EXIT_FATAL_FORK 4
#define EXIT_FATAL_APP_PROCESS 5
#define EXIT_FATAL_UID 6
#define EXIT_FATAL_PM_PATH 7
#define EXIT_FATAL_KILL 9
#define EXIT_FATAL_BINDER_BLOCKED_BY_SELINUX 10

#define PACKAGE_NAME "roro.stellar.manager"
#define SERVER_NAME "stellar_server"
#define SERVER_CLASS_PATH "roro.stellar.server.StellarService"

#if defined(__arm__)
#define ABI "arm"
#elif defined(__i386__)
#define ABI "x86"
#elif defined(__x86_64__)
#define ABI "x86_64"
#elif defined(__aarch64__)
#define ABI "arm64"
#endif

static void run_server(const char *dex_path, const char *main_class, const char *process_name) {
    if (setenv("CLASSPATH", dex_path, true)) {
        LOGE("无法设置 CLASSPATH\n");
        exit(EXIT_FATAL_SET_CLASSPATH);
    }

#define ARG(v) char **v = nullptr; \
    char buf_##v[PATH_MAX]; \
    size_t v_size = 0; \
    uintptr_t v_current = 0;
#define ARG_PUSH(v, arg) v_size += sizeof(char *); \
if (v == nullptr) { \
    v = (char **) malloc(v_size); \
} else { \
    v = (char **) realloc(v, v_size);\
} \
v_current = (uintptr_t) v + v_size - sizeof(char *); \
*((char **) v_current) = arg ? strdup(arg) : nullptr;

#define ARG_END(v) ARG_PUSH(v, nullptr)

#define ARG_PUSH_FMT(v, fmt, ...) snprintf(buf_##v, PATH_MAX, fmt, __VA_ARGS__); \
    ARG_PUSH(v, buf_##v)

#ifdef JAVA_DEBUGGABLE
#define ARG_PUSH_DEBUG_ONLY(v, arg) ARG_PUSH(v, arg)
#define ARG_PUSH_DEBUG_VM_PARAMS(v) \
    if (android_get_device_api_level() >= 30) { \
        ARG_PUSH(v, "-Xcompiler-option"); \
        ARG_PUSH(v, "--debuggable"); \
        ARG_PUSH(v, "-XjdwpProvider:adbconnection"); \
        ARG_PUSH(v, "-XjdwpOptions:suspend=n,server=y"); \
    } else if (android_get_device_api_level() >= 28) { \
        ARG_PUSH(v, "-Xcompiler-option"); \
        ARG_PUSH(v, "--debuggable"); \
        ARG_PUSH(v, "-XjdwpProvider:internal"); \
        ARG_PUSH(v, "-XjdwpOptions:transport=dt_android_adb,suspend=n,server=y"); \
    } else { \
        ARG_PUSH(v, "-Xcompiler-option"); \
        ARG_PUSH(v, "--debuggable"); \
        ARG_PUSH(v, "-agentlib:jdwp=transport=dt_android_adb,suspend=n,server=y"); \
    }
#else
    #define ARG_PUSH_DEBUG_VM_PARAMS(v)
#define ARG_PUSH_DEBUG_ONLY(v, arg)
#endif

    char lib_path[PATH_MAX]{0};
    snprintf(lib_path, PATH_MAX, "%s/lib/%s", dirname(dex_path), ABI);

    ARG(argv)
    ARG_PUSH(argv, "/system/bin/app_process")
    ARG_PUSH_FMT(argv, "-Djava.class.path=%s", dex_path)
    ARG_PUSH_FMT(argv, "-Dstellar.library.path=%s", lib_path)
    ARG_PUSH_DEBUG_VM_PARAMS(argv)
    ARG_PUSH(argv, "/system/bin")
    ARG_PUSH_FMT(argv, "--nice-name=%s", process_name)
    ARG_PUSH(argv, main_class)
    ARG_PUSH_DEBUG_ONLY(argv, "--debug")
    ARG_END(argv)

    LOGD("执行 app_process");

    if (execvp((const char *) argv[0], argv)) {
        exit(EXIT_FATAL_APP_PROCESS);
    }
}

static void start_server(const char *path, const char *main_class, const char *process_name) {
    pid_t pid = fork();
    switch (pid) {
        case -1: {
            perrorf("错误：无法创建进程\n");
            exit(EXIT_FATAL_FORK);
        }
        case 0: {
            LOGD("子进程");
            setsid();
            chdir("/");

            int null_fd = open("/dev/null", O_RDWR);
            if (null_fd != -1) {
                dup2(null_fd, STDIN_FILENO);
                if (null_fd > 2) close(null_fd);
            }

            run_server(path, main_class, process_name);
        }
        default: {
            printf("stellar_server 进程号为 %d\n", pid);
            printf("stellar_starter 正常退出（退出码 0）\n");
            exit(EXIT_SUCCESS);
        }
    }
}

static int check_selinux(const char *s, const char *t, const char *c, const char *p) {
    int res = se::selinux_check_access(s, t, c, p, nullptr);
#ifndef DEBUG
    if (res != 0) {
#endif
    printf("selinux_check_access %s %s %s %s: %d\n", s, t, c, p, res);
    fflush(stdout);
#ifndef DEBUG
    }
#endif
    return res;
}

static int switch_cgroup() {
    int pid = getpid();
    if (cgroup::switch_cgroup("/acct", pid)) {
        printf("切换 cgroup 成功，cgroup 位于 /acct\n");
        return 0;
    }
    if (cgroup::switch_cgroup("/dev/cg2_bpf", pid)) {
        printf("切换 cgroup 成功，cgroup 位于 /dev/cg2_bpf\n");
        return 0;
    }
    if (cgroup::switch_cgroup("/sys/fs/cgroup", pid)) {
        printf("切换 cgroup 成功，cgroup 位于 /sys/fs/cgroup\n");
        return 0;
    }
    char buf[PROP_VALUE_MAX + 1];
    if (__system_property_get("ro.config.per_app_memcg", buf) > 0 &&
        strncmp(buf, "false", 5) != 0) {
        if (cgroup::switch_cgroup("/dev/memcg/apps", pid)) {
            printf("切换 cgroup 成功，cgroup 位于 /dev/memcg/apps\n");
            return 0;
        }
    }
    printf("警告：无法切换 cgroup\n");
    fflush(stdout);
    return -1;
}

int main(int argc, char *argv[]) {
    std::string apk_path;
    for (int i = 0; i < argc; ++i) {
        if (strncmp(argv[i], "--apk=", 6) == 0) {
            apk_path = argv[i] + 6;
        }
    }

    uid_t uid = getuid();
    if (uid != 0 && uid != 2000) {
        perrorf("错误：权限不足，需要 root 或 adb 权限（uid=%d）\n", uid);
        exit(EXIT_FATAL_UID);
    }

    se::init();

    if (uid == 0) {
        switch_cgroup();

        if (android_get_device_api_level() >= 29) {
            printf("切换挂载命名空间到 init...\n");
            switch_mnt_ns(1);
        }
    }

    if (uid == 0) {
        char *context = nullptr;
        if (se::getcon(&context) == 0) {
            int res = 0;

            res |= check_selinux("u:r:untrusted_app:s0", context, "binder", "call");
            res |= check_selinux("u:r:untrusted_app:s0", context, "binder", "transfer");

            if (res != 0) {
                perrorf("错误：SELinux 阻止了应用通过 binder 连接（%s）\n", context);
                exit(EXIT_FATAL_BINDER_BLOCKED_BY_SELINUX);
            }
            se::freecon(context);
        }
    }

    printf("启动器开始运行\n");
    fflush(stdout);

    printf("正在终止旧进程...\n");
    fflush(stdout);

    foreach_proc([](pid_t pid) {
        if (pid == getpid()) return;

        char name[1024];
        if (get_proc_name(pid, name, 1024) != 0) return;

        if (strcmp(SERVER_NAME, name) != 0)
            return;

        if (kill(pid, SIGKILL) == 0)
            printf("已终止进程 %d (%s)\n", pid, name);
        else if (errno == EPERM) {
            perrorf("错误：无法终止进程 %d，请先从应用中停止现有服务\n", pid);
            exit(EXIT_FATAL_KILL);
        } else {
            printf("警告：终止进程 %d (%s) 失败\n", pid, name);
        }
    });

    if (access(apk_path.c_str(), R_OK) == 0) {
        printf("使用来自参数的 apk 路径\n");
        fflush(stdout);
    }

    if (apk_path.empty()) {
        auto f = popen("pm path " PACKAGE_NAME, "r");
        if (f) {
            char line[PATH_MAX]{0};
            fgets(line, PATH_MAX, f);
            trim(line);
            if (strstr(line, "package:") == line) {
                apk_path = line + strlen("package:");
            }
            pclose(f);
        }
    }

    if (apk_path.empty() && argc > 0) {
        std::string so_path(argv[0]);
        size_t lib_pos = so_path.find("/lib/");
        if (lib_pos != std::string::npos) {
            apk_path = so_path.substr(0, lib_pos) + "/base.apk";
            printf("从执行路径推导 apk 路径\n");
            fflush(stdout);
        }
    }

    if (apk_path.empty()) {
        perrorf("错误：无法获取应用路径\n");
        exit(EXIT_FATAL_PM_PATH);
    }

    printf("apk 路径为 %s\n", apk_path.c_str());
    if (access(apk_path.c_str(), R_OK) != 0) {
        perrorf("错误：无法访问应用文件 %s\n", apk_path.c_str());
        exit(EXIT_FATAL_PM_PATH);
    }

    printf("正在启动服务器...\n");
    fflush(stdout);
    LOGD("启动服务器");
    start_server(apk_path.c_str(), SERVER_CLASS_PATH, SERVER_NAME);
}

