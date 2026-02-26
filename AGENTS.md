# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Stellar 是 Shizuku 的深度定制分支，通过 ADB 或 Root 权限为应用提供系统级 API 框架。100% Kotlin，内置 Shizuku 兼容层。

## 构建命令

```bash
# 构建 debug APK
./gradlew :manager:assembleDebug

# 构建 release APK（需要 signing.properties）
./gradlew :manager:assembleRelease

# 仅构建 server 模块
./gradlew :server:assemble

# 仅构建 API 库
./gradlew :api:assemble

# 清理
./gradlew clean
```

Release 产物输出到 `out/apk/`，mapping 文件输出到 `out/mapping/`。

## 签名配置

Release 签名读取根目录 `signing.properties`（不入库），字段：
- `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEYSTORE_ALIAS` / `KEYSTORE_ALIAS_PASSWORD`

缺失时自动回退到 debug 签名。

## 项目架构

### 模块结构

```
manager/     → Android 应用（管理器 UI），applicationId: roro.stellar.manager
server/      → 特权服务端逻辑（运行在 ADB/Root 进程中）
api/         → 客户端 SDK（供第三方应用集成）
  ├── aidl/      → Stellar AIDL 接口定义（IStellarService, IRemoteProcess 等）
  ├── api/       → 客户端 API 入口（Stellar.kt, StellarHelper.kt）
  ├── provider/  → StellarProvider（ContentProvider，接收服务端 Binder）
  ├── shared/    → 共享常量（StellarApiConstants）
  ├── userservice/ → UserService 框架
  └── demo/      → API 示例应用
shizuku/     → Shizuku 兼容层
  ├── aidl/      → Shizuku AIDL 接口（IShizukuService 等）
  └── api/       → ShizukuCompat, ShizukuProvider
```

### 核心数据流

1. **服务启动**：`manager/startup/` → 通过 Root(`Chid` 降权) 或 ADB 启动 `server`
2. **Binder 分发**：`server/BinderSender` → 通过 ContentProvider 将 Binder 发送到客户端
3. **客户端连接**：`api/provider/StellarProvider` 接收 Binder → `api/api/Stellar.kt` 封装调用
4. **Shizuku 兼容**：`server/shizuku/ShizukuServiceIntercept` 拦截 Shizuku API 调用并转发到 Stellar 服务

### Manager 应用架构

- UI：Jetpack Compose + Material Design 3，导航在 `ui/navigation/`
- 主要页面：Home（服务状态）、Apps（授权管理）、Settings、Terminal、Logs
- ADB 无线配对：`adb/` 包实现完整的 ADB 协议栈
- JNI 层：`src/main/jni/` 包含 starter（服务启动器）、chid（降权工具）、adb_pairing 等 native 组件

### Server 核心组件

- `StellarService` / `StellarServiceCore`：服务主体
- `ClientManager` / `ClientRecord`：客户端连接管理
- `PermissionManager` / `PermissionChecker`：多维度权限系统
- `UserServiceManager` / `UserServiceStarter`：用户服务生命周期
- `ShizukuServiceIntercept`：Shizuku 兼容层服务端实现

## 技术栈

- compileSdk 36, minSdk 24, targetSdk 36, JVM 21
- AGP 8.10.1, Kotlin 2.2.0, Compose Compiler 2.1.21
- NDK 29 + CMake 3.22.1+（JNI 组件）
- 版本目录：各模块独立 `*.versions.toml`，根目录 `gradle/libs.versions.toml` 管理 hidden-api/refine

## 版本号规则

`versionCode`: XYYZZZ（如 100109 = 1.0.109）
`versionName`: X.Y.Z[-suffix]（suffix: dev/alpha/beta/rc）
定义在根 `build.gradle` 的 `ext` 块中。

## 注意事项

- `local.properties` 中设置 `api.useLocal=true` + `api.dir=路径` 可切换到本地 API 源码
- JitPack 环境下（`JITPACK=true`）不包含 manager 和 server 模块
- Shizuku 兼容层自动拒绝来自 Shizuku Manager 的请求
