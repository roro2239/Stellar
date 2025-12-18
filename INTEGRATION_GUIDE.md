# Stellar API 接入指南

Stellar 是基于 Shizuku 的分支项目，是一个特权 API 框架，支持通过 ADB 或 Root 权限执行特权操作。本文档将指导你如何将 Stellar API 集成到你的应用中，以及如何从 Shizuku 迁移到 Stellar。

---

## 目录

1. [快速开始](#快速开始)
2. [集成步骤](#集成步骤)
3. [API 参考](#api-参考)
4. [代码示例](#代码示例)
5. [从 Shizuku 迁移](#从-shizuku-迁移)
6. [常见问题](#常见问题)

---

## 快速开始

### 前置条件

- Android 最低版本：API 26 (Android 8.0)
- 已安装 Stellar 管理器
- 已启动 Stellar 服务（通过 ADB 或 Root）

---

## 集成步骤

### 1. 添加依赖

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

### 2. 配置 AndroidManifest

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

**配置说明：**
- `android:exported="true"` - 必须设置为 true，以便 Stellar 服务可以访问该 Provider
- `android:multiprocess="false"` - 必须设置为 false，因为 Stellar 服务只在应用启动时获取 UID
- `android:permission="android.permission.INTERACT_ACROSS_USERS_FULL"` - 限制只有 Shell 和应用本身可以访问
- `android:authorities` - 必须使用 `${applicationId}.stellar` 格式

**可选权限：**

如果你需要在 Stellar 服务启动时自动启动你的应用，可以添加以下权限：

```xml
<meta-data
    android:name="roro.stellar.permissions"
    android:value="stellar,follow_stellar_startup,follow_stellar_startup_on_boot" />
```

权限说明：
- `stellar` - 基础 Stellar API 访问权限（必需）
- `follow_stellar_startup` - 跟随 Stellar 服务启动
- `follow_stellar_startup_on_boot` - 设备启动时跟随 Stellar 启动

### 3. 初始化 Stellar

在你的 Activity 或 Application 中添加 Stellar 监听器：

```kotlin
import roro.stellar.Stellar

class MainActivity : ComponentActivity() {

    private val binderReceivedListener = Stellar.OnBinderReceivedListener {
        Log.i("MyApp", "Stellar 服务已连接")
        // 服务连接成功，可以开始使用 API
        checkServiceStatus()
    }

    private val binderDeadListener = Stellar.OnBinderDeadListener {
        Log.w("MyApp", "Stellar 服务已断开")
        // 服务断开，更新 UI 状态
    }

    private val permissionResultListener =
        Stellar.OnRequestPermissionResultListener { requestCode, allowed, onetime ->
            if (allowed) {
                Log.i("MyApp", "权限已授予")
                // 权限已授予，可以执行特权操作
            } else {
                Log.w("MyApp", "权限被拒绝")
                // 权限被拒绝，提示用户
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 添加监听器
        // 使用 Sticky 版本：如果服务已连接，会立即触发回调；否则等待服务连接后触发
        Stellar.addBinderReceivedListenerSticky(binderReceivedListener)
        Stellar.addBinderDeadListener(binderDeadListener)
        Stellar.addRequestPermissionResultListener(permissionResultListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 移除监听器
        Stellar.removeBinderReceivedListener(binderReceivedListener)
        Stellar.removeBinderDeadListener(binderDeadListener)
        Stellar.removeRequestPermissionResultListener(permissionResultListener)
    }

    private fun checkServiceStatus() {
        // 检查服务是否运行
        if (!Stellar.pingBinder()) {
            Log.e("MyApp", "服务未运行")
            return
        }

        // 检查权限
        if (!Stellar.checkSelfPermission()) {
            // 请求权限
            Stellar.requestPermission(requestCode = 1)
            return
        }

        // 服务已连接且权限已授予，可以使用 API
        Log.i("MyApp", "服务版本: ${Stellar.version}")
        Log.i("MyApp", "服务 UID: ${Stellar.uid}")
    }
}
```

---

## API 参考

### 核心类：`Stellar`

#### 服务状态检查

```kotlin
// 检查服务是否运行
Stellar.pingBinder(): Boolean

// 获取服务 UID (0 = root, 2000 = adb)
Stellar.uid: Int

// 获取服务 API 版本
Stellar.version: Int

// 获取最新支持的版本
Stellar.latestServiceVersion: Int

// 获取 SELinux 上下文
Stellar.sELinuxContext: String?

// 获取管理器版本
Stellar.versionName: String?
Stellar.versionCode: Int
```

#### 权限管理

```kotlin
// 检查权限是否已授予
Stellar.checkSelfPermission(permission: String = "stellar"): Boolean

// 请求权限
Stellar.requestPermission(permission: String = "stellar", requestCode: Int)

// 检查是否应该显示权限说明
Stellar.shouldShowRequestPermissionRationale(): Boolean

// 检查 Stellar 服务自身是否拥有指定权限
Stellar.checkRemotePermission(permission: String): Int

// 获取支持的权限列表
Stellar.supportedPermissions: Array<String>
```

#### 进程执行

```kotlin
// 创建特权进程（以 Stellar 服务的身份执行）
Stellar.newProcess(
    cmd: Array<String?>,      // 命令和参数
    env: Array<String?>?,     // 环境变量（可选）
    dir: String?              // 工作目录（可选）
): StellarRemoteProcess
```

#### 高级功能

```kotlin
// 为其他应用授予/撤销运行时权限
Stellar.grantRuntimePermission(
    packageName: String,
    permissionName: String,
    userId: Int
)

Stellar.revokeRuntimePermission(
    packageName: String,
    permissionName: String,
    userId: Int
)

// Binder 事务包装器
Stellar.transactRemote(data: Parcel, reply: Parcel?, flags: Int)
```

### 辅助类：`StellarHelper`

```kotlin
// 检查管理器是否已安装
StellarHelper.isManagerInstalled(context: Context): Boolean

// 打开管理器
StellarHelper.openManager(context: Context): Boolean

// 获取服务信息
val serviceInfo = StellarHelper.serviceInfo
serviceInfo?.let {
    val uid = it.uid
    val version = it.version
    val seContext = it.seLinuxContext
    val isRoot = it.isRoot      // uid == 0
    val isAdb = it.isAdb        // uid == 2000
}
```

### 系统属性：`StellarSystemProperties`

```kotlin
// 读取系统属性
StellarSystemProperties.get(key: String): String
StellarSystemProperties.get(key: String, def: String): String
StellarSystemProperties.getInt(key: String, def: Int): Int
StellarSystemProperties.getLong(key: String, def: Long): Long
StellarSystemProperties.getBoolean(key: String, def: Boolean): Boolean

// 写入系统属性（需要相应权限）
StellarSystemProperties.set(key: String, value: String)
```

**写入权限说明：**
- **ADB 模式 (uid=2000)：** 可以写入 `debug.*`、`persist.debug.*`、`log.*`、`vendor.debug.*`
- **Root 模式 (uid=0)：** 可以写入大部分属性（除了 `ro.*` 只读属性）

### 特权进程：`StellarRemoteProcess`

```kotlin
val process = Stellar.newProcess(arrayOf("ls", "-la", "/sdcard"), null, null)

// 标准进程方法
process.getInputStream(): InputStream
process.getOutputStream(): OutputStream
process.getErrorStream(): InputStream
process.waitFor(): Int
process.exitValue(): Int
process.destroy()

// 额外方法
process.alive(): Boolean
process.waitForTimeout(timeout: Long, unit: TimeUnit): Boolean
```

### Binder 包装器：`StellarBinderWrapper`

用于包装系统服务 Binder 以获得特权访问：

```kotlin
val binder = StellarBinderWrapper.getSystemService("package")
val pm = IPackageManager.Stub.asInterface(StellarBinderWrapper(binder))
// 现在可以使用特权 PackageManager API
```

---

## 代码示例

### 示例 1：检查服务状态

```kotlin
fun checkServiceStatus() {
    if (!Stellar.pingBinder()) {
        println("服务未运行")
        return
    }

    if (!Stellar.checkSelfPermission()) {
        println("权限未授予")
        return
    }

    val version = Stellar.version
    println("服务版本: $version")

    val uid = Stellar.uid
    val mode = when (uid) {
        0 -> "Root"
        2000 -> "ADB"
        else -> "其他 (UID=$uid)"
    }
    println("运行模式: $mode")

    val seContext = Stellar.sELinuxContext
    println("SELinux 上下文: $seContext")
}
```

### 示例 2：执行 Shell 命令

```kotlin
fun executeCommand() {
    thread {
        try {
            println("$ ls -la /sdcard")

            val process = Stellar.newProcess(
                arrayOf("ls", "-la", "/sdcard"),
                null,
                null
            )

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.lineSequence().forEach { line ->
                println(line)
            }

            val exitCode = process.waitFor()
            println("退出码: $exitCode")

            process.destroy()
        } catch (e: Exception) {
            println("错误: ${e.message}")
        }
    }
}
```

### 示例 3：读取系统属性

```kotlin
fun readSystemProperties() {
    try {
        val brand = StellarSystemProperties.get("ro.product.brand")
        println("品牌: $brand")

        val model = StellarSystemProperties.get("ro.product.model")
        println("型号: $model")

        val androidVersion = StellarSystemProperties.get("ro.build.version.release")
        println("Android 版本: $androidVersion")

        val sdkInt = StellarSystemProperties.getInt("ro.build.version.sdk", 0)
        println("SDK 版本: $sdkInt")
    } catch (e: Exception) {
        println("错误: ${e.message}")
    }
}
```

### 示例 4：检查 Stellar 服务权限

```kotlin
fun checkStellarServicePermissions() {
    val permissions = arrayOf(
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.READ_LOGS",
        "android.permission.DUMP",
        "android.permission.PACKAGE_USAGE_STATS"
    )

    permissions.forEach { permission ->
        val result = Stellar.checkRemotePermission(permission)
        val status = if (result == PackageManager.PERMISSION_GRANTED) "已授予" else "未授予"
        val name = permission.substringAfterLast(".")
        println("$name: $status")
    }
}
```

### 示例 5：为其他应用授予权限

```kotlin
fun grantPermissionToApp(packageName: String) {
    try {
        Stellar.grantRuntimePermission(
            packageName = packageName,
            permissionName = "android.permission.WRITE_EXTERNAL_STORAGE",
            userId = 0
        )
        println("权限已授予")
    } catch (e: Exception) {
        println("授予权限失败: ${e.message}")
    }
}
```

### 示例 6：跟随 Stellar 启动

如果你在 `AndroidManifest.xml` 中声明了 `follow_stellar_startup` 权限，可以创建一个 BroadcastReceiver 来接收启动通知：

```kotlin
class FollowStellarStartup : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("MyApp", "Stellar 已启动: ${intent?.action}")

        // 在 Stellar 启动时执行操作
        try {
            Stellar.newProcess(
                arrayOf("touch", "/sdcard/stellar_started.log"),
                null,
                null
            )
        } catch (e: Exception) {
            Log.e("MyApp", "执行命令失败", e)
        }
    }
}
```

在 `AndroidManifest.xml` 中注册：

```xml
<receiver
    android:name=".FollowStellarStartup"
    android:exported="false">
    <intent-filter>
        <action android:name="roro.stellar.action.STELLAR_STARTED" />
    </intent-filter>
</receiver>
```

---

## 从 Shizuku 迁移

Stellar 是基于 Shizuku 的分支项目，因此 API 设计高度相似，迁移过程相对简单。以下是详细的迁移步骤。

### Stellar vs Shizuku 对比

| 特性 | Stellar | Shizuku |
|------|---------|---------|
| **包名** | `roro.stellar.manager` | `moe.shizuku.privileged.api` |
| **API 命名空间** | `roro.stellar.*` | `rikka.shizuku.*` |
| **权限系统** | 多权限：`stellar`、`follow_stellar_startup`、`follow_stellar_startup_on_boot` | 单一权限模型 |
| **启动钩子** | 内置支持跟随服务启动和开机启动 | 无内置支持 |
| **Provider Authority** | `${applicationId}.stellar` | `${applicationId}.shizuku` |

### 迁移步骤

#### 步骤 1：更新依赖

```gradle
// 移除 Shizuku 依赖
// implementation 'dev.rikka.shizuku:api:13.1.5'
// implementation 'dev.rikka.shizuku:provider:13.1.5'

// 添加 Stellar 依赖
implementation 'com.github.RORO2239:Stellar:api-v1.0'
```

同时在 `settings.gradle` 中添加 JitPack 仓库：

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

#### 步骤 2：更新 AndroidManifest

```xml
<!-- 移除 Shizuku Provider -->
<!--
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:exported="true"
    android:multiprocess="false"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
-->

<!-- 添加 Stellar Provider -->
<provider
    android:name="roro.stellar.StellarProvider"
    android:authorities="${applicationId}.stellar"
    android:exported="true"
    android:multiprocess="false"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />

<meta-data
    android:name="roro.stellar.permissions"
    android:value="stellar" />
```

#### 步骤 3：更新导入语句

```kotlin
// 替换
// import rikka.shizuku.Shizuku
// import rikka.shizuku.ShizukuProvider

// 为
import roro.stellar.Stellar
import roro.stellar.StellarProvider
import roro.stellar.StellarHelper
```

#### 步骤 4：更新 API 调用

将代码中所有 Shizuku API 调用替换为对应的 Stellar API。大部分 API 只需要将类名从 `Shizuku` 改为 `Stellar`，少数 API 有细微差异（如 `getUid()` 改为属性 `uid`）。

**重要变更：** Stellar 移除了 Shizuku 的 `UserService` 功能。如果你的代码使用了 `Shizuku.bindUserService()` 或 `UserService`，需要改用其他方式实现（如直接使用 `newProcess()` 或 Binder 调用）。

| 功能说明 | Stellar API | Shizuku API |
|----------|-------------|-------------|
| 检查服务是否运行 | `Stellar.pingBinder()` | `Shizuku.pingBinder()` |
| 获取服务 UID（0=root, 2000=adb） | `Stellar.uid` | `Shizuku.getUid()` |
| 获取服务 API 版本 | `Stellar.version` | `Shizuku.getVersion()` |
| 检查应用是否已获得权限 | `Stellar.checkSelfPermission()` | `Shizuku.checkSelfPermission()` |
| 请求用户授予权限 | `Stellar.requestPermission(requestCode = code)` | `Shizuku.requestPermission(code)` |
| 添加服务连接监听器 | `Stellar.addBinderReceivedListener()` | `Shizuku.addBinderReceivedListener()` |
| 添加服务连接监听器（立即触发） | `Stellar.addBinderReceivedListenerSticky()` | `Shizuku.addBinderReceivedListenerSticky()` |
| 添加服务断开监听器 | `Stellar.addBinderDeadListener()` | `Shizuku.addBinderDeadListener()` |
| 添加权限请求结果监听器 | `Stellar.addRequestPermissionResultListener()` | `Shizuku.addRequestPermissionResultListener()` |
| 移除服务连接监听器 | `Stellar.removeBinderReceivedListener()` | `Shizuku.removeBinderReceivedListener()` |
| 移除服务断开监听器 | `Stellar.removeBinderDeadListener()` | `Shizuku.removeBinderDeadListener()` |
| 移除权限请求结果监听器 | `Stellar.removeRequestPermissionResultListener()` | `Shizuku.removeRequestPermissionResultListener()` |
| 创建特权进程执行命令 | `Stellar.newProcess()` | `Shizuku.newProcess()`（已在最新版 API 弃用） |

#### 步骤 5：更新监听器接口

```kotlin
// 替换
// Shizuku.OnBinderReceivedListener { }
// Shizuku.OnBinderDeadListener { }
// Shizuku.OnRequestPermissionResultListener { requestCode, grantResult -> }

// 为
Stellar.OnBinderReceivedListener { }
Stellar.OnBinderDeadListener { }
Stellar.OnRequestPermissionResultListener { requestCode, allowed, onetime ->
    // 注意：参数从 grantResult 改为 allowed 和 onetime
}
```

#### 步骤 6：更新辅助方法

```kotlin
// 替换
// ShizukuProvider.isShizukuInstalled(context)
// ShizukuProvider.openShizuku(context)

// 为
StellarHelper.isManagerInstalled(context)
StellarHelper.openManager(context)
```

#### 步骤 7：测试迁移

1. 安装 Stellar 管理器
2. 启动 Stellar 服务（ADB 或 Root）
3. 测试权限请求流程
4. 测试特权进程执行
5. 测试服务重启后的重连

### 迁移示例

**迁移前（Shizuku）：**

```kotlin
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

class MainActivity : AppCompatActivity() {

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
    }

    private fun checkStatus() {
        if (!Shizuku.pingBinder()) return

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(1)
            return
        }

        val uid = Shizuku.getUid()
        println("UID: $uid")
    }
}
```

**迁移后（Stellar）：**

```kotlin
import roro.stellar.Stellar
import roro.stellar.StellarProvider

class MainActivity : AppCompatActivity() {

    private val binderReceivedListener = Stellar.OnBinderReceivedListener {
        checkStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Stellar.addBinderReceivedListenerSticky(binderReceivedListener)
    }

    private fun checkStatus() {
        if (!Stellar.pingBinder()) return

        if (!Stellar.checkSelfPermission()) {
            Stellar.requestPermission(requestCode = 1)
            return
        }

        val uid = Stellar.uid
        println("UID: $uid")
    }
}
```

---

## 常见问题

### Q1: 如何判断服务是以 Root 还是 ADB 模式运行？

```kotlin
val uid = Stellar.uid
when (uid) {
    0 -> println("Root 模式")
    2000 -> println("ADB 模式")
    else -> println("未知模式: $uid")
}

// 或使用 StellarHelper
val serviceInfo = StellarHelper.serviceInfo
if (serviceInfo?.isRoot == true) {
    println("Root 模式")
} else if (serviceInfo?.isAdb == true) {
    println("ADB 模式")
}
```

### Q2: 如何处理服务断开连接？

```kotlin
private val binderDeadListener = Stellar.OnBinderDeadListener {
    // 服务断开，更新 UI
    runOnUiThread {
        Toast.makeText(this, "Stellar 服务已断开", Toast.LENGTH_SHORT).show()
        // 禁用需要 Stellar 的功能
        updateUIForDisconnectedState()
    }
}
```

### Q3: 权限被拒绝后如何处理？

```kotlin
private val permissionResultListener =
    Stellar.OnRequestPermissionResultListener { requestCode, allowed, onetime ->
        if (!allowed) {
            // 权限被拒绝
            if (Stellar.shouldShowRequestPermissionRationale()) {
                // 显示权限说明对话框
                showPermissionRationaleDialog()
            } else {
                // 用户选择了"不再询问"，引导用户到管理器手动授权
                StellarHelper.openManager(this)
            }
        }
    }
```

### Q4: 如何在多进程应用中使用 Stellar？

如果你的应用使用了多进程，需要在 Application 中启用多进程支持：

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 判断当前进程是否是 Provider 进程
        val isProviderProcess = // 你的判断逻辑
        StellarProvider.enableMultiProcessSupport(isProviderProcess)

        // 如果不是 Provider 进程，请求 Binder
        if (!isProviderProcess) {
            StellarProvider.requestBinderForNonProviderProcess(this)
        }
    }
}
```

### Q5: 执行命令时如何处理超时？

```kotlin
fun executeCommandWithTimeout() {
    thread {
        try {
            val process = Stellar.newProcess(
                arrayOf("sleep", "10"),
                null,
                null
            )

            // 等待最多 5 秒
            val finished = process.waitForTimeout(5, TimeUnit.SECONDS)

            if (!finished) {
                println("命令超时，强制终止")
                process.destroy()
            } else {
                println("命令完成，退出码: ${process.exitValue()}")
            }
        } catch (e: Exception) {
            println("错误: ${e.message}")
        }
    }
}
```

### Q6: 如何检查管理器是否已安装？

```kotlin
if (!StellarHelper.isManagerInstalled(context)) {
    // 管理器未安装，引导用户安装
    AlertDialog.Builder(context)
        .setTitle("需要 Stellar 管理器")
        .setMessage("此功能需要安装 Stellar 管理器")
        .show()
}
```

### Q7: 如何读取命令的错误输出？

```kotlin
fun executeCommandWithErrorHandling() {
    thread {
        try {
            val process = Stellar.newProcess(
                arrayOf("ls", "/nonexistent"),
                null,
                null
            )

            // 读取标准输出
            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            outputReader.lineSequence().forEach { line ->
                println("输出: $line")
            }

            // 读取错误输出
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            errorReader.lineSequence().forEach { line ->
                println("错误: $line")
            }

            val exitCode = process.waitFor()
            println("退出码: $exitCode")

            process.destroy()
        } catch (e: Exception) {
            println("异常: ${e.message}")
        }
    }
}
```

### Q8: Stellar 和 Shizuku 可以共存吗？

可以。Stellar 和 Shizuku 使用不同的包名和 Provider Authority，可以在同一设备上同时安装和运行。但建议应用只集成其中一个，以避免混淆。

---

## 更多资源

- **示例应用：** 查看 `demo` 模块获取完整的使用示例
- **问题反馈：** 在 GitHub Issues 中提交问题

---

## 许可证

Stellar 使用 GNU General Public License v3.0 许可证。详见 LICENSE 文件。
