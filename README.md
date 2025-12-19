# Stellar

[![JitPack](https://img.shields.io/jitpack/version/com.github.RORO2239/Stellar)](https://jitpack.io/#roro2239/Stellar)

一个基于 Shizuku 的魔改分支，让应用通过 ADB 或 Root 权限使用系统级 API。

## 项目简介

Stellar 是 [Shizuku](https://github.com/RikkaApps/Shizuku) 的深度定制版本，专为开发者提供更灵活、更强大的特权 API 框架。通过 ADB 无线调试或 Root 权限启动服务后，应用程序可以调用需要系统级权限的 API，而无需应用本身拥有 Root 权限。

## 核心特性

Stellar 相比原版 Shizuku 进行了以下核心改进：

### 权限系统增强

- **重构权限模型** - 从单一权限改为细分权限管理
- **多权限支持**：
  - `stellar` - 基础 API 访问权限
  - `follow_stellar_startup` - 跟随服务启动
  - `follow_stellar_startup_on_boot` - 开机自启动
- **改进权限回调** - 支持客户端感知是否为一次性授权
- **新增权限管理 API** - 提供完整的权限管理接口

### 启动与服务优化

- **跟随 Stellar 启动** - 应用可跟随 Stellar 服务自动启动

### UI/UX 改进

Stellar 对用户界面进行了全面重构，提供更现代化的使用体验：

- **重构授权管理界面** - 全新的授权管理与 UI 设计，采用 Material Design 风格
- **优化授权页面** - 重新设计授权页 UI，提升交互体验
- **细分权限展示** - 授权页新增细分权限 UI，清晰展示各项权限
- **优化授权应用列表** - 改进应用列表页面，更直观的权限状态显示
- **优化启动页面** - 重新设计启动流程界面，更友好的引导体验

## 与 Shizuku 的主要区别

### 移除的功能
- **UserService** - 完全移除用户服务绑定功能
- **rish** - 移除 Shizuku 内置的 root shell 工具
- **Sui** - 移除了 API 对 Zygisk-Sui 的支持

### 新增的功能
- **跟随启动机制** - 应用可跟随 Stellar 服务自动启动
- **细分权限系统** - 支持多种权限类型的精细化管理
- **权限回调增强** - 支持一次性授权感知

### 重新启用的功能

Stellar 重新启用了 Shizuku 最新版本中已标记为弃用的功能：

- **`newProcess()` API** - 直接创建特权进程的方法，Shizuku 已弃用但 Stellar 保留支持
- **运行时权限授予/撤销** - 通过 `grantRuntimePermission()` 和 `revokeRuntimePermission()` 为其他应用授予或撤销 Android 运行时权限

这些功能在某些场景下仍然非常实用，Stellar 选择继续支持以提供更完整的 API。

### 架构优化
- 100% Kotlin 代码
- 精简模块结构
- 规范化命名

## 快速开始

### 集成 Stellar 到你的应用

查看完整的集成指南和 API 文档：

- **[API 集成指南](INTEGRATION_GUIDE.md)** - 完整的集成步骤、API 参考和代码示例
- **[从 Shizuku 迁移](INTEGRATION_GUIDE.md#从-shizuku-迁移)** - 详细的迁移步骤和 API 对比

### 基本使用流程

1. 添加 JitPack 依赖：`com.github.RORO2239:Stellar:latest.release`
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
