# Stellar

一个基于 Shizuku 的魔改分支，让应用通过 ADB 或 Root 权限使用系统级 API。

## 项目简介

Stellar 是 [Shizuku](https://github.com/RikkaApps/Shizuku) 的深度定制版本，专为开发者提供更灵活、更强大的特权 API 框架。通过 ADB 无线调试或 Root 权限启动服务后，应用程序可以调用需要系统级权限的 API，而无需应用本身拥有 Root 权限。

## 核心特性

Stellar 相比原版 Shizuku 进行了以下核心改进：

### 权限系统增强

- **全新权限架构** - 摒弃单一权限模式，引入精细化的多维度权限管理体系
- **分级权限控制**：
  - `stellar` - 核心 API 访问权限，授予基础服务调用能力
  - `follow_stellar_startup` - 服务伴随启动权限，实现应用与 Stellar 服务的生命周期绑定
- **智能权限回调** - 增强回调机制，客户端可精准感知授权类型（永久授权/一次性授权）
- **完整权限管理 API** - 提供全套权限查询、申请、撤销接口，满足复杂业务场景需求

### 启动与服务优化

- **服务伴随启动** - 应用可注册为 Stellar 服务的伴随进程，实现服务启动时自动唤醒

### 架构重构

- **服务层重构** - 重新设计核心服务架构，优化模块间通信机制，提升整体性能与响应速度
- **UserService 重写** - 全面重构用户服务层，优化服务架构设计，提升代码可维护性与扩展性
- **Shizuku 兼容性修复** - 修复原 Shizuku 遗留的已知问题，增强框架稳定性

### UI/UX 改进

Stellar 对用户界面进行了全面重构，带来更现代、更直观的使用体验：

- **全新授权管理界面** - 采用 Material Design 3 设计语言，打造简洁优雅的授权管理中心
- **授权页面焕新** - 重新设计授权交互流程，操作更加流畅自然
- **权限可视化展示** - 授权页清晰呈现各项细分权限，用户一目了然
- **应用列表优化** - 改进已授权应用列表，权限状态一览无余
- **引导流程升级** - 重新设计启动引导页面，新用户上手更轻松

## 与 Shizuku 的主要区别

### 移除的功能
- **rish** - 移除 Shizuku 内置的 root shell 工具
- **Sui** - 移除了 API 对 Zygisk-Sui 的支持

### 新增的功能
- **跟随启动机制** - 应用可跟随 Stellar 服务自动启动
- **细分权限系统** - 支持多种权限类型的精细化管理
- **权限回调增强** - 支持一次性授权感知
- **降权激活** - Root 启动后可降权到 Shell 用户运行，提高安全性

### 重新启用的功能

Stellar 重新启用了 Shizuku 最新版本中已标记为弃用的功能：

- **`newProcess()` API** - 直接创建特权进程的方法，Shizuku 已弃用但 Stellar 保留支持
- **运行时权限授予/撤销** - 通过 `grantRuntimePermission()` 和 `revokeRuntimePermission()` 为其他应用授予或撤销 Android 运行时权限

这些功能在某些场景下仍然非常实用，Stellar 选择继续支持以提供更完整的 API。

### 架构优化
- 100% Kotlin 代码
- 精简模块结构
- 规范化命名

## Shizuku 兼容层

Stellar 内置了 Shizuku 兼容层，允许使用 Shizuku API 的应用无需修改代码即可使用 Stellar 服务。

### 工作原理

兼容层通过以下方式实现无缝兼容：

1. **客户端兼容** - `ShizukuProvider` 接收 Stellar 服务发送的 Binder，并通过 `ShizukuCompat` 管理连接状态
2. **服务端拦截** - `ShizukuServiceIntercept` 实现完整的 `IShizukuService` 接口，将 Shizuku API 调用转发到 Stellar 服务
3. **权限映射** - 自动将 Shizuku 权限请求映射到 Stellar 的 `shizuku` 权限

### 支持的 API

兼容层支持 Shizuku 的核心 API：

- `pingBinder()` / `getVersion()` / `getUid()` - 服务状态查询
- `checkSelfPermission()` / `requestPermission()` - 权限管理
- `newProcess()` - 创建特权进程
- `addUserService()` / `removeUserService()` - 用户服务管理
- `transactRemote()` - Binder 事务转发

### 启用/禁用

Shizuku 兼容层默认启用。如需禁用，可在 Stellar 管理器的设置页面中关闭「Shizuku 兼容」开关。

### 注意事项

- 兼容层会自动拒绝来自 Shizuku Manager 的请求，避免冲突
- 使用 Shizuku API 的应用需要在 `AndroidManifest.xml` 中配置 `ShizukuProvider`
- 建议新应用直接使用 Stellar API 以获得完整功能支持

## 降权激活

降权激活功能允许以 Root 权限启动 Stellar 服务后，自动降权到 Shell 用户（uid=2000）运行，提高安全性。

### 启用方式

在 Stellar 管理器的设置页面中，开启「降权激活」开关即可。

### 工作原理

启用降权激活后，启动流程如下：

```
su (root) → libchid.so 2000 → libstellar.so --apk=...
```

1. 使用 Root 权限执行 `libchid.so`
2. `libchid.so` 将进程身份切换到 uid=2000（Shell 用户）
3. 以 Shell 身份执行 `libstellar.so` 启动服务

### 注意事项

- 降权激活仅在 Root 启动模式下生效
- ADB 启动模式本身就是 uid=2000，无需降权
- 降权后服务将失去 Root 特有的能力（如写入系统属性、访问受保护目录等）

## 快速开始

### 集成 Stellar 到你的应用

查看完整的集成指南和 API 文档：

- **[API 集成指南](INTEGRATION_GUIDE.md)** - 完整的集成步骤、API 参考和代码示例
- **[从 Shizuku 迁移](INTEGRATION_GUIDE.md#从-shizuku-迁移)** - 详细的迁移步骤和 API 对比

### 基本使用流程

1. 添加 JitPack 依赖：`com.github.roro2239:Stellar-API:latest.release`
2. 配置 `StellarProvider` 到 AndroidManifest
3. 初始化 Stellar 并请求权限
4. 使用 Stellar API 执行特权操作

> 详细步骤请查看 [API 集成指南](INTEGRATION_GUIDE.md)

## 致谢与许可

### 致谢

本项目基于 [Shizuku](https://github.com/RikkaApps/Shizuku)，由 [RikkaApps](https://github.com/RikkaApps) 开发。感谢原作者的杰出工作。

### 许可证

本项目的修改部分采用 [Mozilla Public License 2.0](LICENSE)。

原始 Shizuku 代码保留其 Apache License 2.0 许可证。

| 组件 | 许可证 |
|------|--------|
| Stellar 修改部分 | Mozilla Public License 2.0 |
| [Shizuku](https://github.com/RikkaApps/Shizuku) 原始代码 | Apache License 2.0 |

## 贡献

欢迎提交 Issue 和 Pull Request。在提交代码前，请确保：

- 代码风格符合项目规范（Kotlin）
- 添加必要的注释和文档
- 测试通过所有功能

## 联系方式

- GitHub Issues: [提交问题](https://github.com/RORO2239/Stellar/issues)
- 项目主页: [RORO2239/Stellar](https://github.com/RORO2239/Stellar)

## 相关链接

- [完整 API 文档](INTEGRATION_GUIDE.md)
- [原版 Shizuku](https://github.com/RikkaApps/Shizuku)
