package com.stellar.demo

import android.os.IBinder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.stellar.demo.ui.navigation.DemoBottomNavigation
import com.stellar.demo.ui.screens.*
import com.stellar.demo.ui.theme.DemoTheme
import com.stellar.demo.ui.components.ExecutionDialog
import roro.stellar.Stellar
import roro.stellar.StellarHelper
import roro.stellar.userservice.ServiceMode
import roro.stellar.userservice.StellarUserService
import roro.stellar.userservice.UserServiceArgs

class MainActivity : ComponentActivity() {

    private var serviceStatus by mutableStateOf(ServiceStatus.CHECKING)
    private var startFollowServerPermission by mutableStateOf(false)
    private var startFollowServerOnBootPermission by mutableStateOf(false)
    private var serviceInfo by mutableStateOf<ServiceInfo?>(null)
    private var logText by mutableStateOf("")

    private var userServiceBinder: IBinder? = null
    private var userServiceConnected by mutableStateOf(false)
    private var currentServiceMode by mutableStateOf(ServiceMode.ONE_TIME)

    private var showExecutionDialog by mutableStateOf(false)
    private var executionDialogTitle by mutableStateOf("")
    private var executionDialogOutput by mutableStateOf("")
    private var isExecutionRunning by mutableStateOf(false)

    private val prefs by lazy {
        getSharedPreferences("stellar_demo_prefs", MODE_PRIVATE)
    }

    companion object {
        private const val PREF_SERVICE_MODE = "service_mode"
    }

    private val binderReceivedListener = Stellar.OnBinderReceivedListener {
        log("[Stellar] 服务已连接")
        checkStatus()
    }

    private val binderDeadListener = Stellar.OnBinderDeadListener {
        log("[Stellar] 服务已断开")
        serviceStatus = ServiceStatus.NOT_RUNNING
        serviceInfo = null
    }

    private val permissionResultListener =
        Stellar.OnRequestPermissionResultListener { requestCode, allowed, onetime ->
            handlePermissionResult(requestCode, allowed, onetime)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val savedMode = prefs.getInt(PREF_SERVICE_MODE, ServiceMode.ONE_TIME.value)
        currentServiceMode = ServiceMode.fromValue(savedMode)

        Stellar.addBinderReceivedListenerSticky(binderReceivedListener)
        Stellar.addBinderDeadListener(binderDeadListener)
        Stellar.addRequestPermissionResultListener(permissionResultListener)

        DemoFunctions.setOnServiceStateChanged { connected ->
            userServiceConnected = connected
            if (!connected) userServiceBinder = null
        }

        log("=== Stellar API Demo ===")
        checkStatus()

        setContent {
            DemoTheme {
                MainApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Stellar.removeBinderReceivedListener(binderReceivedListener)
        Stellar.removeBinderDeadListener(binderDeadListener)
        Stellar.removeRequestPermissionResultListener(permissionResultListener)
        DemoFunctions.setOnServiceStateChanged(null)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainApp() {
        var selectedIndex by remember { mutableIntStateOf(0) }
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = when (selectedIndex) {
                                0 -> "Stellar Demo"
                                1 -> "功能"
                                else -> "设置"
                            }
                        )
                    },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (selectedIndex == 0) {
                            IconButton(onClick = { checkStatus() }) {
                                Icon(Icons.Default.Refresh, "刷新")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                DemoBottomNavigation(
                    selectedIndex = selectedIndex,
                    onItemClick = { selectedIndex = it }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (selectedIndex) {
                    0 -> HomeScreen(
                        serviceStatus = serviceStatus,
                        serviceInfo = serviceInfo,
                        userServiceConnected = userServiceConnected,
                        onRefresh = { checkStatus() },
                        onStatusAction = { handleStatusAction() },
                        onRequestFollowPermission = {
                            Stellar.requestPermission("follow_stellar_startup", 1002)
                        },
                        onRequestBootPermission = {
                            Stellar.requestPermission("follow_stellar_startup_on_boot", 1003)
                        },
                        hasFollowPermission = startFollowServerPermission,
                        hasBootPermission = startFollowServerOnBootPermission
                    )
                    1 -> FunctionsScreen(
                        categories = getDemoCategories(),
                        isReady = serviceStatus == ServiceStatus.READY,
                        onActionClick = { action ->
                            if (serviceStatus == ServiceStatus.READY) {
                                executeAction(action)
                            } else {
                                Toast.makeText(this@MainActivity, "服务未就绪", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    2 -> SettingsScreen(
                        userServiceConnected = userServiceConnected,
                        currentServiceMode = currentServiceMode,
                        onServiceModeChange = { mode ->
                            currentServiceMode = mode
                            prefs.edit().putInt(PREF_SERVICE_MODE, mode.value).apply()
                            log("[设置] 服务模式: ${if (mode == ServiceMode.DAEMON) "守护" else "一次性"}")
                            if (userServiceConnected) rebindUserServiceWithNewMode()
                        },
                        logText = logText,
                        onClearLog = { clearLog() }
                    )
                }
            }
        }

        if (showExecutionDialog) {
            ExecutionDialog(
                title = executionDialogTitle,
                output = executionDialogOutput,
                isRunning = isExecutionRunning,
                onDismiss = {
                    showExecutionDialog = false
                    executionDialogOutput = ""
                }
            )
        }
    }

    private fun checkStatus() {
        serviceStatus = ServiceStatus.CHECKING

        if (!StellarHelper.isManagerInstalled(this)) {
            serviceStatus = ServiceStatus.NOT_INSTALLED
            serviceInfo = null
            log("[状态] 管理器未安装")
            return
        }

        if (!Stellar.pingBinder()) {
            serviceStatus = ServiceStatus.NOT_RUNNING
            serviceInfo = null
            log("[状态] 服务未运行")
            return
        }

        val info = StellarHelper.serviceInfo
        serviceInfo = info?.let {
            ServiceInfo(
                uid = it.uid,
                version = it.version,
                isRoot = it.isRoot,
                isAdb = it.isAdb,
                seLinuxContext = it.seLinuxContext
            )
        }

        if (!Stellar.checkSelfPermission()) {
            serviceStatus = ServiceStatus.NO_PERMISSION
            log("[状态] 权限未授予")
            return
        }

        serviceStatus = ServiceStatus.READY
        log("[状态] 服务已就绪")

        startFollowServerPermission = Stellar.checkSelfPermission("follow_stellar_startup")
        startFollowServerOnBootPermission = Stellar.checkSelfPermission("follow_stellar_startup_on_boot")
    }

    private fun handleStatusAction() {
        when (serviceStatus) {
            ServiceStatus.NOT_INSTALLED -> {
                Toast.makeText(this, "请先安装 Stellar 管理器", Toast.LENGTH_SHORT).show()
            }
            ServiceStatus.NOT_RUNNING -> {
                StellarHelper.openManager(this)
                log("[操作] 打开管理器")
            }
            ServiceStatus.NO_PERMISSION -> {
                log("[操作] 请求权限...")
                Stellar.requestPermission(requestCode = 1001)
            }
            else -> checkStatus()
        }
    }

    private fun handlePermissionResult(requestCode: Int, allowed: Boolean, onetime: Boolean) {
        val permName = when (requestCode) {
            1001 -> "Stellar"
            1002 -> "跟随启动"
            1003 -> "开机跟随启动"
            else -> "未知"
        }

        if (allowed) {
            log("[权限] $permName 已授予${if (onetime) " (仅一次)" else ""}")
            Toast.makeText(this, "$permName 权限已授予", Toast.LENGTH_SHORT).show()
            checkStatus()
        } else {
            log("[权限] $permName 被拒绝")
            Toast.makeText(this, "$permName 权限被拒绝", Toast.LENGTH_SHORT).show()
            when (requestCode) {
                1001 -> serviceStatus = ServiceStatus.NO_PERMISSION
                1002 -> startFollowServerPermission = false
                1003 -> startFollowServerOnBootPermission = false
            }
        }
    }

    private fun log(message: String) {
        runOnUiThread {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            logText += "[$timestamp] $message\n"
        }
    }

    private fun clearLog() {
        logText = ""
        log("日志已清空")
    }

    private fun executeAction(action: FunctionAction) {
        executionDialogTitle = action.title
        executionDialogOutput = ""
        isExecutionRunning = true
        showExecutionDialog = true

        log("\n> ${action.title}")
        action.runner()
    }

    private fun dialogLog(message: String) {
        runOnUiThread {
            executionDialogOutput += "$message\n"
            if (message.startsWith("[OK]") || message.startsWith("[Error]") ||
                message.contains("完成") || message.contains("成功") || message.contains("失败")) {
                isExecutionRunning = false
            }
        }
        log(message)
    }

    private fun rebindUserServiceWithNewMode() {
        log("[UserService] 重新绑定...")
        val oldArgs = UserServiceArgs.Builder(DemoUserService::class.java)
            .processNameSuffix("demo_service")
            .build()
        StellarUserService.unbindUserService(oldArgs)

        userServiceBinder = null
        userServiceConnected = false
        DemoFunctions.setUserServiceBinder(null)

        val newArgs = UserServiceArgs.Builder(DemoUserService::class.java)
            .processNameSuffix("demo_service")
            .versionCode(1)
            .serviceMode(currentServiceMode)
            .build()
        StellarUserService.bindUserService(newArgs, userServiceCallback)
    }

    private val userServiceCallback = object : StellarUserService.ServiceCallback {
        override fun onServiceConnected(service: IBinder) {
            userServiceBinder = service
            userServiceConnected = true
            log("[UserService] 已连接")
            DemoFunctions.setUserServiceBinder(service)
        }

        override fun onServiceDisconnected() {
            userServiceBinder = null
            userServiceConnected = false
            log("[UserService] 已断开")
            DemoFunctions.setUserServiceBinder(null)
        }

        override fun onServiceStartFailed(errorCode: Int, message: String) {
            log("[UserService] 启动失败: [$errorCode] $message")
        }
    }

    private fun getDemoCategories(): List<FunctionCategory> {
        val logger = DemoFunctions.Logger { message -> dialogLog(message) }

        return listOf(
            FunctionCategory(
                name = "基础功能",
                icon = Icons.Default.Info,
                actions = listOf(
                    FunctionAction(
                        "获取服务信息",
                        "版本、UID、SELinux",
                        Icons.Default.Info
                    ) { DemoFunctions.getBasicInfo(this, logger) },
                    FunctionAction(
                        "获取版本信息",
                        "Manager版本",
                        Icons.Default.Info
                    ) { DemoFunctions.getVersionInfo(this, logger) },
                    FunctionAction(
                        "检查权限状态",
                        "验证权限",
                        Icons.Default.Security
                    ) { DemoFunctions.checkPermission(this, logger) }
                )
            ),
            FunctionCategory(
                name = "进程执行",
                icon = Icons.Default.Terminal,
                actions = listOf(
                    FunctionAction(
                        "列出文件",
                        "ls -la /sdcard",
                        Icons.Default.Folder
                    ) { DemoFunctions.runLsCommand(this, logger) },
                    FunctionAction(
                        "查看进程",
                        "ps -A",
                        Icons.Default.Memory
                    ) { DemoFunctions.runPsCommand(this, logger) },
                    FunctionAction(
                        "获取系统属性",
                        "getprop",
                        Icons.Default.Settings
                    ) { DemoFunctions.runGetPropCommand(this, logger) }
                )
            ),
            FunctionCategory(
                name = "系统属性",
                icon = Icons.Default.PhoneAndroid,
                actions = listOf(
                    FunctionAction(
                        "读取设备信息",
                        "品牌、型号",
                        Icons.Default.Smartphone
                    ) { DemoFunctions.readDeviceProperties(this, logger) },
                    FunctionAction(
                        "读取调试属性",
                        "ro.debuggable",
                        Icons.Default.BugReport
                    ) { DemoFunctions.readDebugProperties(this, logger) }
                )
            ),
            FunctionCategory(
                name = "高级功能",
                icon = Icons.Default.Extension,
                actions = listOf(
                    FunctionAction(
                        "检查远程权限",
                        "验证远程服务权限",
                        Icons.Default.VpnKey
                    ) { DemoFunctions.checkRemotePermissions(this, logger) }
                )
            ),
            FunctionCategory(
                name = "UserService",
                icon = Icons.Default.Memory,
                actions = listOf(
                    FunctionAction(
                        "启动 UserService",
                        "在特权进程中启动",
                        Icons.Default.PlayArrow
                    ) { DemoFunctions.startUserService(logger) },
                    FunctionAction(
                        "调用 UserService",
                        "调用服务方法",
                        Icons.Default.Terminal
                    ) { DemoFunctions.callUserService(this, logger) },
                    FunctionAction(
                        "停止 UserService",
                        "停止服务",
                        Icons.Default.Delete
                    ) { DemoFunctions.stopUserService(logger) }
                )
            )
        )
    }
}
