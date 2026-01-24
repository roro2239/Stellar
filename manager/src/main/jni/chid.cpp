#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <dirent.h>
#include <errno.h>
#include <unistd.h>
#include <time.h>
#include <pwd.h>
#include <grp.h>

#define perrorf(...) fprintf(stderr, __VA_ARGS__)

static void pwtoid(const char *tok, uid_t *uid, gid_t *gid) {
    struct passwd *pw;
    pw = getpwnam(tok);
    if (pw) {
        if (uid) *uid = pw->pw_uid;
        if (gid) *gid = pw->pw_gid;
    } else {
        uid_t tmpid = atoi(tok);
        if (uid) *uid = tmpid;
        if (gid) *gid = tmpid;
    }
}

static void extract_uidgids(const char *uidgids, uid_t *uid, gid_t *gid,
                            gid_t *gids, int *gids_count) {
    char *clobberablegids;
    char *nexttok;
    char *tok;
    int gids_found;

    if (!uidgids || !*uidgids) {
        *gid = *uid = 0;
        *gids_count = 0;
        return;
    }

    clobberablegids = strdup(uidgids);
    nexttok = clobberablegids;
    tok = strsep(&nexttok, ",");
    pwtoid(tok, uid, gid);

    tok = strsep(&nexttok, ",");
    if (!tok) {
        *gids_count = 0;
        free(clobberablegids);
        return;
    }

    pwtoid(tok, NULL, gid);
    gids_found = 0;

    while ((gids_found < *gids_count) && (tok = strsep(&nexttok, ","))) {
        pwtoid(tok, NULL, gids);
        gids_found++;
        gids++;
    }

    if (nexttok && gids_found == *gids_count) {
        perrorf("chid: 组 ID 数量过多\n");
    }

    *gids_count = gids_found;
    free(clobberablegids);
}

int main(int argc, char **argv) {
    uid_t uid, myuid;
    gid_t gid, gids[10];
    myuid = getuid();
    if (myuid != 0 && myuid != 2000) {
        perrorf("chid: uid %d 不允许调用 chid\n", myuid);
        return 1;
    }

    if (argc < 2) {
        uid = gid = 0;
    } else {
        int gids_count = sizeof(gids) / sizeof(gids[0]);
        extract_uidgids(argv[1], &uid, &gid, gids, &gids_count);

        if (gids_count) {
            if (setgroups(gids_count, gids)) {
                perrorf("chid: 设置 groups 失败\n");
                return 1;
            }
        }
    }

    if (setgid(gid) || setuid(uid)) {
        perrorf("chid: 切换 uid 或 gid 时权限被拒绝\n");
        return 1;
    }

    printf("chid: 已切换到 uid=%d, gid=%d\n", getuid(), getgid());

    if (argc == 3) {
        if (execlp(argv[2], argv[2], NULL) < 0) {
            int saved_errno = errno;
            perrorf("chid: 执行 %s 失败，错误: %s\n", argv[2], strerror(errno));
            return -saved_errno;
        }
    } else if (argc > 3) {
        char *exec_args[argc - 1];
        memset(exec_args, 0, sizeof(exec_args));
        memcpy(exec_args, &argv[2], sizeof(exec_args));

        if (execvp(argv[2], exec_args) < 0) {
            int saved_errno = errno;
            perrorf("chid: 执行 %s 失败，错误: %s\n", argv[2], strerror(errno));
            return -saved_errno;
        }
    }

    execlp("/system/bin/sh", "sh", NULL);
    perrorf("chid: 执行 shell 失败\n");
    return 1;
}
