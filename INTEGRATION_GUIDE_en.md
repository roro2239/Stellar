# Stellar API Integration Guide

Stellar is a fork of Shizuku — a privileged API framework that allows privileged operations via ADB or Root. This guide walks you through integrating the Stellar API into your app and migrating from Shizuku to Stellar.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Integration Steps](#integration-steps)
3. [API Reference](#api-reference)
4. [Code Examples](#code-examples)
5. [Migrating from Shizuku](#migrating-from-shizuku)
6. [FAQ](#faq)

---

## Quick Start

### Prerequisites

- Minimum Android version: API 26 (Android 8.0)
- Stellar Manager installed
- Stellar service started (via ADB or Root)

---

## Integration Steps

### 1. Add the Dependency

Add the JitPack repository to `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Add the dependency to `build.gradle`:

```gradle
dependencies {
    implementation 'com.github.roro2239.Stellar-API:<version>'
}
```

> Replace `<version>` with the latest version shown at [![JitPack](https://jitpack.io/v/roro2239/Stellar-API.svg)](https://jitpack.io/#roro2239/Stellar-API)

### 2. Configure AndroidManifest

Add `StellarProvider` to `AndroidManifest.xml`:

```xml
<application>
    <!-- Other components -->

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

**Configuration notes:**
- `android:exported="true"` — must be true so the Stellar service can access the Provider
- `android:multiprocess="false"` — must be false because the Stellar service only obtains the UID at app startup
- `android:permission="android.permission.INTERACT_ACROSS_USERS_FULL"` — restricts access to Shell and the app itself
- `android:authorities` — must follow the `${applicationId}.stellar` format

**Optional permission:**

If you want your app to auto-start when the Stellar service starts, add the following:

```xml
<meta-data
    android:name="roro.stellar.permissions"
    android:value="stellar,follow_stellar_startup" />
```

Permission descriptions:
- `stellar` — basic Stellar API access (required)
- `follow_stellar_startup` — follow Stellar service startup

### 3. Initialize Stellar

Add Stellar listeners to your Activity or Application:

```kotlin
import roro.stellar.Stellar

class MainActivity : ComponentActivity() {

    private val binderReceivedListener = Stellar.OnBinderReceivedListener {
        Log.i("MyApp", "Stellar service connected")
        // Service connected successfully, start using the API
        checkServiceStatus()
    }

    private val binderDeadListener = Stellar.OnBinderDeadListener {
        Log.w("MyApp", "Stellar service disconnected")
        // Service disconnected, update UI state
    }

    private val permissionResultListener =
        Stellar.OnRequestPermissionResultListener { requestCode, allowed, onetime ->
            if (allowed) {
                Log.i("MyApp", "Permission granted")
                // Permission granted, privileged operations can now be performed
            } else {
                Log.w("MyApp", "Permission denied")
                // Permission denied, prompt the user
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Add listeners
        // Sticky version: if the service is already connected, the callback fires immediately;
        // otherwise it waits until the service connects
        Stellar.addBinderReceivedListenerSticky(binderReceivedListener)
        Stellar.addBinderDeadListener(binderDeadListener)
        Stellar.addRequestPermissionResultListener(permissionResultListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove listeners
        Stellar.removeBinderReceivedListener(binderReceivedListener)
        Stellar.removeBinderDeadListener(binderDeadListener)
        Stellar.removeRequestPermissionResultListener(permissionResultListener)
    }

    private fun checkServiceStatus() {
        // Check if the service is running
        if (!Stellar.pingBinder()) {
            Log.e("MyApp", "Service is not running")
            return
        }

        // Check permission
        if (!Stellar.checkSelfPermission()) {
            // Request permission
            Stellar.requestPermission(requestCode = 1)
            return
        }

        // Service is connected and permission is granted — ready to use the API
        Log.i("MyApp", "Service version: ${Stellar.version}")
        Log.i("MyApp", "Service UID: ${Stellar.uid}")
    }
}
```

---

## API Reference

### Core Class: `Stellar`

#### Service Status Checks

```kotlin
// Check if the service is running
Stellar.pingBinder(): Boolean

// Get the service UID (0 = root, 2000 = adb)
Stellar.uid: Int

// Get the service API version
Stellar.version: Int

// Get the latest supported version
Stellar.latestServiceVersion: Int

// Get the SELinux context
Stellar.sELinuxContext: String?

// Get the manager version
Stellar.versionName: String?
Stellar.versionCode: Int
```

#### Permission Management

```kotlin
// Check if permission has been granted
Stellar.checkSelfPermission(permission: String = "stellar"): Boolean

// Request permission
Stellar.requestPermission(permission: String = "stellar", requestCode: Int)

// Check if a permission rationale should be shown
Stellar.shouldShowRequestPermissionRationale(): Boolean

// Check if the Stellar service itself holds a given permission
Stellar.checkRemotePermission(permission: String): Int

// Get the list of supported permissions
Stellar.supportedPermissions: Array<String>
```

#### Process Execution

```kotlin
// Create a privileged process (executes as the Stellar service identity)
Stellar.newProcess(
    cmd: Array<String?>,      // command and arguments
    env: Array<String?>?,     // environment variables (optional)
    dir: String?              // working directory (optional)
): StellarRemoteProcess

// Create a PTY privileged process (supports terminal features: colors, signals, window resizing)
Stellar.newPtyProcess(
    cmd: Array<String?>,      // command and arguments
    env: Array<String?>?,     // environment variables (optional)
    dir: String?              // working directory (optional)
): StellarPtyProcess
```

#### Advanced Features

```kotlin
// Grant/revoke runtime permissions for other apps
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

// Binder transaction wrapper
Stellar.transactRemote(data: Parcel, reply: Parcel?, flags: Int)
```

### Helper Class: `StellarHelper`

```kotlin
// Check if the manager is installed
StellarHelper.isManagerInstalled(context: Context): Boolean

// Open the manager
StellarHelper.openManager(context: Context): Boolean

// Get service info
val serviceInfo = StellarHelper.serviceInfo
serviceInfo?.let {
    val uid = it.uid
    val version = it.version
    val seContext = it.seLinuxContext
    val isRoot = it.isRoot      // uid == 0
    val isAdb = it.isAdb        // uid == 2000
}
```

### System Properties: `StellarSystemProperties`

```kotlin
// Read system properties
StellarSystemProperties.get(key: String): String
StellarSystemProperties.get(key: String, def: String): String
StellarSystemProperties.getInt(key: String, def: Int): Int
StellarSystemProperties.getLong(key: String, def: Long): Long
StellarSystemProperties.getBoolean(key: String, def: Boolean): Boolean

// Write system properties (requires appropriate permission)
StellarSystemProperties.set(key: String, value: String)
```

**Write permission notes:**
- **ADB mode (uid=2000):** can write `debug.*`, `persist.debug.*`, `log.*`, `vendor.debug.*`
- **Root mode (uid=0):** can write most properties (except read-only `ro.*` properties)

### Privileged Process: `StellarRemoteProcess`

```kotlin
val process = Stellar.newProcess(arrayOf("ls", "-la", "/sdcard"), null, null)

// Standard process methods
process.getInputStream(): InputStream
process.getOutputStream(): OutputStream
process.getErrorStream(): InputStream
process.waitFor(): Int
process.exitValue(): Int
process.destroy()

// Additional methods
process.alive(): Boolean
process.waitForTimeout(timeout: Long, unit: TimeUnit): Boolean
```

### PTY Process: `StellarPtyProcess`

```kotlin
val pty = Stellar.newPtyProcess(arrayOf("sh"), null, null)

// Get the PTY file descriptor (read and write share the same fd)
pty.ptyFd: ParcelFileDescriptor

// Resize the terminal window
pty.resize(cols: Int, rows: Int)

// Wait for the process to exit and return the exit code
pty.waitFor(): Int

// Destroy the process
pty.destroy()
```

Differences from `StellarRemoteProcess`:
- PTY uses a single fd for both reading and writing (master/slave pseudo-terminal) instead of separate stdin/stdout/stderr
- Supports terminal features: ANSI colors, Ctrl+C signals, window resizing
- Echo is enabled by default (input appears in the output stream)

### Binder Wrapper: `StellarBinderWrapper`

Used to wrap system service Binders for privileged access:

```kotlin
val binder = StellarBinderWrapper.getSystemService("package")
val pm = IPackageManager.Stub.asInterface(StellarBinderWrapper(binder))
// Now you can use privileged PackageManager APIs
```

### User Service: `StellarUserService`

User services allow you to run a custom Binder service inside the Stellar service process, executing operations with privileged identity.

#### Core Methods

```kotlin
// Bind a user service
StellarUserService.bindUserService(
    args: UserServiceArgs,           // service parameter configuration
    callback: ServiceCallback,       // service callback
    handler: Handler? = mainHandler  // handler for callbacks (optional)
)

// Unbind a user service
StellarUserService.unbindUserService(args: UserServiceArgs)

// Get the bound service Binder (if present and alive)
StellarUserService.peekUserService(args: UserServiceArgs): IBinder?

// Get the current number of active user services
StellarUserService.getUserServiceCount(): Int
```

#### Service Callback Interface

```kotlin
interface ServiceCallback {
    // Service connected successfully
    fun onServiceConnected(service: IBinder)

    // Service disconnected
    fun onServiceDisconnected()

    // Service failed to start (optional implementation)
    fun onServiceStartFailed(errorCode: Int, message: String) {}
}
```

### User Service Args: `UserServiceArgs`

Configure user service parameters with the Builder pattern:

```kotlin
val args = UserServiceArgs.Builder(MyUserService::class.java)
    .processNameSuffix("myservice")  // process name suffix, default "userservice"
    .debug(BuildConfig.DEBUG)        // enable debug mode
    .versionCode(BuildConfig.VERSION_CODE.toLong())  // version number
    .tag("my-tag")                   // optional tag
    .serviceMode(ServiceMode.DAEMON) // service mode
    .build()
```

#### Parameter Reference

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `className` | String | required | Full class name of the service class |
| `processNameSuffix` | String | `"userservice"` | Process name suffix |
| `debug` | Boolean | `false` | Enable debug mode |
| `use32Bit` | Boolean | `false` | Use a 32-bit process |
| `versionCode` | Long | `0` | Service version number |
| `tag` | String? | `null` | Optional tag |
| `serviceMode` | ServiceMode | `ONE_TIME` | Service run mode |
| ~~`useStandaloneDex`~~ | ~~Boolean~~ | ~~`false`~~ | ~~Use standalone DEX~~ (temporarily removed) |

#### Standalone DEX Mode Configuration (temporarily removed)

> **Note:** The standalone DEX mode feature has been temporarily removed. The following documentation is for reference only.

Standalone DEX mode allows compiling user service classes into a separate DEX file, avoiding loading the entire APK.

~~**Step 1: Reference the build script**~~

~~Reference the build script provided by Stellar in your app's `build.gradle`:~~

```gradle
plugins {
    id('com.android.application')
    // ...
}

// Temporarily removed
// apply from: project(':userservice').file('userservice-standalone.gradle')
```

~~**Step 2: Configure stellarUserService**~~

```gradle
// Temporarily removed
// stellarUserService {
//     enabled = true                                    // enable standalone DEX mode
//     serviceClass = 'com.example.MyUserService'        // must specify the full class name of the service class
//     extraClasses = ['com.example.MyHelper']           // optional: additional classes to include
// }
```

| Config | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | Boolean | `false` | Enable standalone DEX mode |
| `serviceClass` | String | `null` | Full class name of the service class (required when enabled) |
| `extraClasses` | List | `[]` | Additional classes to include |

~~**Step 3: Use BuildConfig in code**~~

~~Once enabled, the build script automatically generates the `BuildConfig.STELLAR_USE_STANDALONE_DEX` field:~~

```kotlin
// Temporarily removed
// val args = UserServiceArgs.Builder(MyUserService::class.java)
//     .useStandaloneDex(BuildConfig.STELLAR_USE_STANDALONE_DEX)
//     .build()
```

~~**Without standalone DEX mode**~~

~~If you don't need standalone DEX mode, set `enabled = false` or omit the configuration:~~

```gradle
// Temporarily removed
// stellarUserService {
//     enabled = false  // BuildConfig.STELLAR_USE_STANDALONE_DEX = false
// }
```

**Naming convention:** the corresponding AIDL interface should be named `I<ServiceName>` (e.g., if the service class is `MyUserService`, the interface should be `IMyUserService`).

### Service Mode: `ServiceMode`

```kotlin
enum class ServiceMode {
    ONE_TIME,  // one-time service: stops automatically when the client disconnects
    DAEMON     // daemon: runs continuously until explicitly stopped
}
```

### User Service Helper: `UserServiceHelper`

Helper methods for interacting with user service Binders:

```kotlin
// Destroy a user service
UserServiceHelper.destroy(binder: IBinder)

// Check if the service is alive
UserServiceHelper.isAlive(binder: IBinder): Boolean

// Get the UID of the service process
UserServiceHelper.getUid(binder: IBinder): Int

// Get the PID of the service process
UserServiceHelper.getPid(binder: IBinder): Int
```

---

## Code Examples

### Example 1: Check Service Status

```kotlin
fun checkServiceStatus() {
    if (!Stellar.pingBinder()) {
        println("Service is not running")
        return
    }

    if (!Stellar.checkSelfPermission()) {
        println("Permission not granted")
        return
    }

    val version = Stellar.version
    println("Service version: $version")

    val uid = Stellar.uid
    val mode = when (uid) {
        0 -> "Root"
        2000 -> "ADB"
        else -> "Other (UID=$uid)"
    }
    println("Running mode: $mode")

    val seContext = Stellar.sELinuxContext
    println("SELinux context: $seContext")
}
```

### Example 2: Execute a Shell Command

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
            println("Exit code: $exitCode")

            process.destroy()
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }
}
```

### Example 3: Read System Properties

```kotlin
fun readSystemProperties() {
    try {
        val brand = StellarSystemProperties.get("ro.product.brand")
        println("Brand: $brand")

        val model = StellarSystemProperties.get("ro.product.model")
        println("Model: $model")

        val androidVersion = StellarSystemProperties.get("ro.build.version.release")
        println("Android version: $androidVersion")

        val sdkInt = StellarSystemProperties.getInt("ro.build.version.sdk", 0)
        println("SDK version: $sdkInt")
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }
}
```

### Example 4: Check Stellar Service Permissions

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
        val status = if (result == PackageManager.PERMISSION_GRANTED) "Granted" else "Not granted"
        val name = permission.substringAfterLast(".")
        println("$name: $status")
    }
}
```

### Example 5: Grant Permission to Another App

```kotlin
fun grantPermissionToApp(packageName: String) {
    try {
        Stellar.grantRuntimePermission(
            packageName = packageName,
            permissionName = "android.permission.WRITE_EXTERNAL_STORAGE",
            userId = 0
        )
        println("Permission granted")
    } catch (e: Exception) {
        println("Failed to grant permission: ${e.message}")
    }
}
```

### Example 6: Follow Stellar Startup

If you declared the `follow_stellar_startup` permission in `AndroidManifest.xml`, you can create a BroadcastReceiver to receive startup notifications:

```kotlin
class FollowStellarStartup : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("MyApp", "Stellar started: ${intent?.action}")

        // Perform actions when Stellar starts
        try {
            Stellar.newProcess(
                arrayOf("touch", "/sdcard/stellar_started.log"),
                null,
                null
            )
        } catch (e: Exception) {
            Log.e("MyApp", "Failed to execute command", e)
        }
    }
}
```

Register it in `AndroidManifest.xml`:

```xml
<receiver
    android:name=".FollowStellarStartup"
    android:exported="false">
    <intent-filter>
        <action android:name="roro.stellar.action.STELLAR_STARTED" />
    </intent-filter>
</receiver>
```

### Example 7: Create a User Service

First, define the AIDL interface:

```aidl
// src/main/aidl/com/example/IMyUserService.aidl
package com.example;

interface IMyUserService {
    String executeCommand(String command) = 1;
    String getSystemProperty(String name) = 2;
}
```

Then implement the service class:

```kotlin
// src/main/java/com/example/MyUserService.kt
package com.example

class MyUserService : IMyUserService.Stub() {

    override fun executeCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = java.io.BufferedReader(
                java.io.InputStreamReader(process.inputStream)
            )
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    override fun getSystemProperty(name: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", name))
            val reader = java.io.BufferedReader(
                java.io.InputStreamReader(process.inputStream)
            )
            reader.readLine() ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
```

---

### Example 8: Bind and Use a User Service

```kotlin
import roro.stellar.userservice.StellarUserService
import roro.stellar.userservice.UserServiceArgs
import roro.stellar.userservice.ServiceMode

class MainActivity : ComponentActivity() {

    private var userService: IMyUserService? = null

    private val serviceCallback = object : StellarUserService.ServiceCallback {
        override fun onServiceConnected(service: IBinder) {
            Log.i("MyApp", "User service connected")
            userService = IMyUserService.Stub.asInterface(service)
            // The service is ready to use
            executeCommandViaService()
        }

        override fun onServiceDisconnected() {
            Log.w("MyApp", "User service disconnected")
            userService = null
        }

        override fun onServiceStartFailed(errorCode: Int, message: String) {
            Log.e("MyApp", "User service failed to start: $errorCode - $message")
        }
    }

    private fun bindUserService() {
        val args = UserServiceArgs.Builder(MyUserService::class.java)
            .processNameSuffix("myservice")
            .debug(BuildConfig.DEBUG)
            .versionCode(BuildConfig.VERSION_CODE.toLong())
            .serviceMode(ServiceMode.ONE_TIME)
            .build()

        StellarUserService.bindUserService(args, serviceCallback)
    }

    private fun executeCommandViaService() {
        thread {
            try {
                val result = userService?.executeCommand("ls -la /sdcard")
                Log.i("MyApp", "Command result: $result")
            } catch (e: Exception) {
                Log.e("MyApp", "Failed to execute command", e)
            }
        }
    }
}
```

---

### Example 9: User Service in Daemon Mode

```kotlin
// Use DAEMON mode to create a persistently running service
fun bindDaemonService() {
    val args = UserServiceArgs.Builder(MyUserService::class.java)
        .processNameSuffix("daemon")
        .serviceMode(ServiceMode.DAEMON)  // daemon mode
        .versionCode(BuildConfig.VERSION_CODE.toLong())
        .build()

    StellarUserService.bindUserService(args, object : StellarUserService.ServiceCallback {
        override fun onServiceConnected(service: IBinder) {
            Log.i("MyApp", "Daemon service started")
            // The service will keep running until unbindUserService is explicitly called
        }

        override fun onServiceDisconnected() {
            Log.i("MyApp", "Daemon service stopped")
        }
    })
}

// Stop the daemon service
fun stopDaemonService() {
    val args = UserServiceArgs.Builder(MyUserService::class.java)
        .processNameSuffix("daemon")
        .serviceMode(ServiceMode.DAEMON)
        .build()

    StellarUserService.unbindUserService(args)
}
```

### Example 10: Interactive PTY Shell

```kotlin
import android.os.ParcelFileDescriptor
import roro.stellar.Stellar
import kotlin.concurrent.thread

class PtyShellSession(
    private val pty: StellarPtyProcess,
    private val writer: java.io.OutputStream
) {
    fun send(cmd: String) {
        writer.write("$cmd\n".toByteArray())
    }

    fun resize(cols: Int, rows: Int) = pty.resize(cols, rows)

    fun destroy() = pty.destroy()
}

fun startPtyShell(onOutput: (String) -> Unit): PtyShellSession? {
    if (!Stellar.pingBinder() || !Stellar.checkSelfPermission()) return null

    val pty = try {
        Stellar.newPtyProcess(arrayOf("sh"), null, null)
    } catch (_: Exception) { return null }

    val fd = pty.ptyFd
    val session = PtyShellSession(pty, ParcelFileDescriptor.AutoCloseOutputStream(fd))

    thread {
        try {
            val reader = ParcelFileDescriptor.AutoCloseInputStream(fd)
            val buf = ByteArray(4096)
            while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                onOutput(String(buf, 0, n))
            }
        } catch (_: Exception) {}
    }

    return session
}
```

**Notes:**
- `newPtyProcess` must be called on a background thread (Binder IPC is not allowed on the main thread)
- The PTY fd shares a single `ParcelFileDescriptor` for both reading and writing; wrap the read and write streams separately
- Echo is enabled by default, so sent commands appear in the output stream; if filtering is needed, compare against recently sent commands in the `onOutput` callback
- After calling `destroy()`, the fd closes and the reader thread exits automatically

---

## Migrating from Shizuku

Stellar is a fork of Shizuku, so the API design is highly similar and migration is relatively straightforward. Detailed migration steps follow.

### Stellar vs Shizuku Comparison

| Feature | Stellar | Shizuku |
|---------|---------|---------|
| **Package name** | `roro.stellar.manager` | `moe.shizuku.privileged.api` |
| **API namespace** | `roro.stellar.*` | `rikka.shizuku.*` |
| **Permission system** | Multiple permissions: `stellar`, `follow_stellar_startup` | Single permission model |
| **Startup hook** | Built-in follow-service-startup support | No built-in support |
| **Provider Authority** | `${applicationId}.stellar` | `${applicationId}.shizuku` |

### Migration Steps

#### Step 1: Update Dependencies

```gradle
// Remove Shizuku dependencies
// implementation 'dev.rikka.shizuku:api:13.1.5'
// implementation 'dev.rikka.shizuku:provider:13.1.5'

// Add Stellar dependency
implementation 'com.github.roro2239.Stellar-API:<version>'
```

Also add the JitPack repository to `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

#### Step 2: Update AndroidManifest

```xml
<!-- Remove the Shizuku Provider -->
<!--
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:exported="true"
    android:multiprocess="false"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
-->

<!-- Add the Stellar Provider -->
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

#### Step 3: Update Imports

```kotlin
// Replace
// import rikka.shizuku.Shizuku
// import rikka.shizuku.ShizukuProvider

// with
import roro.stellar.Stellar
import roro.stellar.StellarProvider
import roro.stellar.StellarHelper
```

#### Step 4: Update API Calls

Replace all Shizuku API calls in your code with the corresponding Stellar APIs. For most APIs, you only need to change the class name from `Shizuku` to `Stellar`; a few APIs have minor differences (e.g., `getUid()` becomes the `uid` property).

| Description | Stellar API | Shizuku API |
|-------------|-------------|-------------|
| Check if the service is running | `Stellar.pingBinder()` | `Shizuku.pingBinder()` |
| Get service UID (0=root, 2000=adb) | `Stellar.uid` | `Shizuku.getUid()` |
| Get service API version | `Stellar.version` | `Shizuku.getVersion()` |
| Check if the app has permission | `Stellar.checkSelfPermission()` | `Shizuku.checkSelfPermission()` |
| Request user permission | `Stellar.requestPermission(requestCode = code)` | `Shizuku.requestPermission(code)` |
| Add binder received listener | `Stellar.addBinderReceivedListener()` | `Shizuku.addBinderReceivedListener()` |
| Add binder received listener (sticky) | `Stellar.addBinderReceivedListenerSticky()` | `Shizuku.addBinderReceivedListenerSticky()` |
| Add binder dead listener | `Stellar.addBinderDeadListener()` | `Shizuku.addBinderDeadListener()` |
| Add permission result listener | `Stellar.addRequestPermissionResultListener()` | `Shizuku.addRequestPermissionResultListener()` |
| Remove binder received listener | `Stellar.removeBinderReceivedListener()` | `Shizuku.removeBinderReceivedListener()` |
| Remove binder dead listener | `Stellar.removeBinderDeadListener()` | `Shizuku.removeBinderDeadListener()` |
| Remove permission result listener | `Stellar.removeRequestPermissionResultListener()` | `Shizuku.removeRequestPermissionResultListener()` |
| Create privileged process | `Stellar.newProcess()` | `Shizuku.newProcess()` (deprecated in latest API) |
| Bind user service | `StellarUserService.bindUserService()` | `Shizuku.bindUserService()` |
| Unbind user service | `StellarUserService.unbindUserService()` | `Shizuku.unbindUserService()` |
| Get user service | `StellarUserService.peekUserService()` | `Shizuku.peekUserService()` |

#### Step 5: Update Listener Interfaces

```kotlin
// Replace
// Shizuku.OnBinderReceivedListener { }
// Shizuku.OnBinderDeadListener { }
// Shizuku.OnRequestPermissionResultListener { requestCode, grantResult -> }

// with
Stellar.OnBinderReceivedListener { }
Stellar.OnBinderDeadListener { }
Stellar.OnRequestPermissionResultListener { requestCode, allowed, onetime ->
    // Note: the parameter changed from grantResult to allowed and onetime
}
```

#### Step 6: Update Helper Methods

```kotlin
// Replace
// ShizukuProvider.isShizukuInstalled(context)
// ShizukuProvider.openShizuku(context)

// with
StellarHelper.isManagerInstalled(context)
StellarHelper.openManager(context)
```

#### Step 7: Test the Migration

1. Install Stellar Manager
2. Start the Stellar service (ADB or Root)
3. Test the permission request flow
4. Test privileged process execution
5. Test reconnection after service restart

### Migration Example

**Before migration (Shizuku):**

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

**After migration (Stellar):**

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

## FAQ

### Q1: How do I tell whether the service is running in Root or ADB mode?

```kotlin
val uid = Stellar.uid
when (uid) {
    0 -> println("Root mode")
    2000 -> println("ADB mode")
    else -> println("Unknown mode: $uid")
}

// Or use StellarHelper
val serviceInfo = StellarHelper.serviceInfo
if (serviceInfo?.isRoot == true) {
    println("Root mode")
} else if (serviceInfo?.isAdb == true) {
    println("ADB mode")
}
```

### Q2: How do I handle service disconnection?

```kotlin
private val binderDeadListener = Stellar.OnBinderDeadListener {
    // Service disconnected, update UI
    runOnUiThread {
        Toast.makeText(this, "Stellar service disconnected", Toast.LENGTH_SHORT).show()
        // Disable features that require Stellar
        updateUIForDisconnectedState()
    }
}
```

### Q3: What should I do when permission is denied?

```kotlin
private val permissionResultListener =
    Stellar.OnRequestPermissionResultListener { requestCode, allowed, onetime ->
        if (!allowed) {
            // Permission denied
            if (Stellar.shouldShowRequestPermissionRationale()) {
                // Show a permission rationale dialog
                showPermissionRationaleDialog()
            } else {
                // The user chose "Don't ask again" — guide them to the manager to grant manually
                StellarHelper.openManager(this)
            }
        }
    }
```

### Q4: How do I use Stellar in a multi-process app?

If your app uses multiple processes, enable multi-process support in your Application:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Determine whether the current process is the Provider process
        val isProviderProcess = // your check logic
        StellarProvider.enableMultiProcessSupport(isProviderProcess)

        // If this is not the Provider process, request the Binder
        if (!isProviderProcess) {
            StellarProvider.requestBinderForNonProviderProcess(this)
        }
    }
}
```

### Q5: How do I handle timeouts when executing commands?

```kotlin
fun executeCommandWithTimeout() {
    thread {
        try {
            val process = Stellar.newProcess(
                arrayOf("sleep", "10"),
                null,
                null
            )

            // Wait up to 5 seconds
            val finished = process.waitForTimeout(5, TimeUnit.SECONDS)

            if (!finished) {
                println("Command timed out, force killing")
                process.destroy()
            } else {
                println("Command finished, exit code: ${process.exitValue()}")
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }
}
```

### Q6: How do I check whether the manager is installed?

```kotlin
if (!StellarHelper.isManagerInstalled(context)) {
    // Manager not installed, guide the user to install it
    AlertDialog.Builder(context)
        .setTitle("Stellar Manager required")
        .setMessage("This feature requires Stellar Manager to be installed")
        .show()
}
```

### Q7: How do I read a command's error output?

```kotlin
fun executeCommandWithErrorHandling() {
    thread {
        try {
            val process = Stellar.newProcess(
                arrayOf("ls", "/nonexistent"),
                null,
                null
            )

            // Read standard output
            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            outputReader.lineSequence().forEach { line ->
                println("Output: $line")
            }

            // Read error output
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            errorReader.lineSequence().forEach { line ->
                println("Error: $line")
            }

            val exitCode = process.waitFor()
            println("Exit code: $exitCode")

            process.destroy()
        } catch (e: Exception) {
            println("Exception: ${e.message}")
        }
    }
}
```

### Q8: Can Stellar and Shizuku coexist?

Yes. Stellar and Shizuku use different package names and Provider authorities, so they can be installed and run on the same device simultaneously. However, it's recommended that an app integrate only one of them to avoid confusion.

---

## More Resources

- **Sample app:** see the `demo` module for complete usage examples
- **Feedback:** submit issues on GitHub Issues

---

## License

The modifications in this project are licensed under the [Mozilla Public License 2.0](LICENSE).

The original Shizuku code retains its Apache License 2.0.

| Component | License |
|-----------|---------|
| Stellar modifications | Mozilla Public License 2.0 |
| [Shizuku](https://github.com/RikkaApps/Shizuku) original code | Apache License 2.0 |
