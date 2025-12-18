# Stellar
![JitPack](https://img.shields.io/jitpack/version/com.github.RORO2239/Stellar)  
一个无需 Root 使用更高权限的系统 API 的 App。

## 致谢与许可

本项目基于 [Shizuku](https://github.com/RikkaApps/Shizuku)，由 [RikkaApps](https://github.com/RikkaApps) 开发。

根据 Apache License 2.0 许可证开源。

### 主要修改

- 清理对于开发者来说不必要的API
- 为接入的 App 细化权限分类和请求频率
- 通过无线调试或Root权限开机自启动
- 支持指定TCP/IP端口
- 设备运行期间启动服务后再次启动无需无线调试
- 更精美的UI

## 快速开始

### 接入

查看[完整API文档](https://github.com/RORO2239/Stellar/blob/main/INTEGRATION_GUIDE.md)

#### 1. 添加依赖
在 `settings.gradle` 中添加 JitPack 仓库：

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

在 `build.gradle` 中添加依赖：

```gradle
dependencies {
    implementation 'com.github.RORO2239:Stellar:api-v1.0'
}
```

#### 2. 配置 AndroidManifest

在 `AndroidManifest.xml` 中添加 `StellarProvider`：

```xml
<application>
    <!-- 其他组件 -->

    <provider
        android:name="roro.stellar.StellarProvider"
        android:authorities="${applicationId}.stellar"
        android:exported="true"
        android:multiprocess="false"
        android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />

    <meta-data
        android:name="roro.stellar.permissions"
        android:value="stellar" />
</application>
```

#### 3. 初始化Stellar

详见[初始化Stellar](https://github.com/RORO2239/Stellar/blob/main/INTEGRATION_GUIDE.md#3-%E5%88%9D%E5%A7%8B%E5%8C%96-stellar)

### 从 Shizuku 迁移

Stellar 是基于 Shizuku 的分支项目，因此 API 设计高度相似，迁移过程相对简单。以下是详细的迁移步骤。

#### Stellar vs Shizuku 对比

| 特性 | Stellar | Shizuku |
|------|---------|---------|
| **包名** | `roro.stellar.manager` | `moe.shizuku.privileged.api` |
| **API 命名空间** | `roro.stellar.*` | `rikka.shizuku.*` |
| **权限系统** | 多权限：`stellar`、`follow_stellar_startup`、`follow_stellar_startup_on_boot` | 单一权限模型 |
| **启动钩子** | 内置支持跟随服务启动和开机启动 | 无内置支持 |
| **Provider Authority** | `${applicationId}.stellar` | `${applicationId}.shizuku` |

迁移步骤详见[从 Shizuku 迁移](https://github.com/RORO2239/Stellar/blob/main/INTEGRATION_GUIDE.md#%E4%BB%8E-shizuku-%E8%BF%81%E7%A7%BB)

## 许可证

本项目的修改部分采用 [Mozilla Public License 2.0](LICENSE)。

### 第三方许可

| 项目 | 许可证 |
|------|--------|
| [Shizuku](https://github.com/RikkaApps/Shizuku) | Apache License 2.0 |

原始 Shizuku 代码保留其 Apache 2.0 许可证。
