package com.stellar.demo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import roro.stellar.Stellar
import roro.stellar.StellarHelper

/**
 * Stellar API Demo - 简洁现代化设计
 * 
 * 功能特性：
 * - 实时服务状态监控
 * - 直接使用 Stellar API
 * - 简洁的 Material Design 3 界面
 * - 分类功能演示
 */
class MainActivity : ComponentActivity() {

    private var serviceStatus by mutableStateOf(ServiceStatus.CHECKING)
    private var serviceInfo by mutableStateOf<StellarHelper.ServiceInfo?>(null)
    private var logText by mutableStateOf("")
    
    // 服务状态枚举
    enum class ServiceStatus(
        val title: String,
        val color: Color,
        val icon: ImageVector
    ) {
        CHECKING("检查中...", Color(0xFF9E9E9E), Icons.Default.Refresh),
        READY("已就绪", Color(0xFF4CAF50), Icons.Default.CheckCircle),
        NOT_INSTALLED("未安装", Color(0xFFF44336), Icons.Default.Warning),
        NOT_RUNNING("未运行", Color(0xFFFF9800), Icons.Default.Error),
        NO_PERMISSION("需要权限", Color(0xFFFF9800), Icons.Default.Lock)
    }

    private val binderReceivedListener = Stellar.OnBinderReceivedListener {
        log("✓ Stellar 服务已连接")
        checkStatus()
    }

    private val binderDeadListener = Stellar.OnBinderDeadListener {
        log("✗ Stellar 服务已断开")
        serviceStatus = ServiceStatus.NOT_RUNNING
        serviceInfo = null
    }

    private val permissionResultListener = Stellar.OnRequestPermissionResultListener { _, allowed, _ ->
        if (allowed) {
            log("✓ 权限已授予")
            Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
            checkStatus()
        } else {
            log("✗ 权限被拒绝")
            Toast.makeText(this, "权限被拒绝", Toast.LENGTH_SHORT).show()
            serviceStatus = ServiceStatus.NO_PERMISSION
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 添加监听器
        Stellar.addBinderReceivedListenerSticky(binderReceivedListener)
        Stellar.addBinderDeadListener(binderDeadListener)
        Stellar.addRequestPermissionResultListener(permissionResultListener)
        
        log("=== Stellar API Demo ===")
        log("欢迎使用 Stellar API 演示应用\n")
        
        // 初始状态检查
        checkStatus()
        
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF90CAF9),
                    secondary = Color(0xFFCE93D8),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                )
            ) {
                MainScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Stellar.removeBinderReceivedListener(binderReceivedListener)
        Stellar.removeBinderDeadListener(binderDeadListener)
        Stellar.removeRequestPermissionResultListener(permissionResultListener)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { 
                        Column {
                            Text("Stellar API Demo")
                            Text(
                                "现代化 API 演示",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = { checkStatus() }) {
                            Icon(Icons.Default.Refresh, "刷新状态")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 状态卡片
                item {
                    StatusCard()
                }
                
                // 功能分类
                val categories = getDemoCategories()
                categories.forEach { category ->
                    item {
                        CategoryHeader(category.name, category.icon)
                    }
                    
                    items(category.actions) { action ->
                        ActionCard(action)
                    }
                }
                
                // 日志卡片
                item {
                    LogCard()
                }
            }
        }
    }

    @Composable
    fun StatusCard() {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = serviceStatus.icon,
                        contentDescription = null,
                        tint = serviceStatus.color,
                        modifier = Modifier.size(32.dp)
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "服务状态",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = serviceStatus.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = serviceStatus.color,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                serviceInfo?.let { info ->
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    InfoRow("UID", info.uid.toString())
                    InfoRow("API 版本", info.version.toString())
                    
                    // 显示Manager版本信息（仅在服务就绪时）
                    if (serviceStatus == ServiceStatus.READY) {
                        val managerVersionInfo = remember(serviceStatus) {
                            try {
                                val versionName = Stellar.versionName
                                val versionCode = Stellar.versionCode
                                // 只有当获取到有效版本信息时才显示
                                if (versionName != null && versionName != "unknown" && versionCode >= 1) {
                                    "$versionName ($versionCode)"
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                // 获取失败时不显示
                                null
                            }
                        }
                        
                        managerVersionInfo?.let { version ->
                            InfoRow("Manager 版本", version)
                        }
                    }
                    
                    InfoRow("运行模式", when {
                        info.isRoot -> "Root"
                        info.isAdb -> "ADB"
                        else -> "其他"
                    })
                    InfoRow("SELinux", info.seLinuxContext ?: "")
                }
                
                if (serviceStatus != ServiceStatus.READY && serviceStatus != ServiceStatus.CHECKING) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { handleStatusAction() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            when (serviceStatus) {
                                ServiceStatus.NOT_INSTALLED -> Icons.Default.Download
                                ServiceStatus.NOT_RUNNING -> Icons.Default.PlayArrow
                                ServiceStatus.NO_PERMISSION -> Icons.Default.VpnKey
                                else -> Icons.Default.Settings
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(when (serviceStatus) {
                            ServiceStatus.NOT_INSTALLED -> "请安装管理器"
                            ServiceStatus.NOT_RUNNING -> "打开 Stellar"
                            ServiceStatus.NO_PERMISSION -> "请求权限"
                            else -> "检查状态"
                        })
                    }
                }
            }
        }
    }

    @Composable
    fun InfoRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }

    @Composable
    fun CategoryHeader(name: String, icon: ImageVector) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }

    @Composable
    fun ActionCard(action: DemoAction) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (serviceStatus == ServiceStatus.READY) {
                    log("\n▶ ${action.title}")
                    action.runner()
                } else {
                    Toast.makeText(this, "Stellar 服务未就绪", Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    action.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = action.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (action.description.isNotEmpty()) {
                        Text(
                            text = action.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    fun LogCard() {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "输出日志",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    IconButton(onClick = { clearLog() }) {
                        Icon(Icons.Default.Delete, "清空日志")
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    val scrollState = rememberScrollState()
                    
                    Text(
                        text = logText.ifEmpty { "日志输出将显示在这里...\n\n运行上面的功能来查看输出" },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (logText.isEmpty()) 
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                    
                    LaunchedEffect(logText) {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                }
            }
        }
    }

    private fun checkStatus() {
        serviceStatus = ServiceStatus.CHECKING
        
        // 检查管理器是否安装
        val managerInstalled = StellarHelper.isManagerInstalled(this)
        if (!managerInstalled) {
            serviceStatus = ServiceStatus.NOT_INSTALLED
            serviceInfo = null
            log("✗ Stellar 管理器应用未安装")
            return
        }
        
        // 检查服务是否运行
        val serviceRunning = Stellar.pingBinder()
        if (!serviceRunning) {
            serviceStatus = ServiceStatus.NOT_RUNNING
            serviceInfo = null
            log("✗ Stellar 服务未运行")
            return
        }
        
        // 获取服务信息
        serviceInfo = StellarHelper.serviceInfo
        
        // 检查权限
        val permissionGranted = Stellar.checkSelfPermission()
        if (!permissionGranted) {
            serviceStatus = ServiceStatus.NO_PERMISSION
            log("✗ 权限未授予")
            return
        }
        
        // 一切就绪
        serviceStatus = ServiceStatus.READY
        log("✓ Stellar 已就绪，可以使用所有功能")
    }

    private fun handleStatusAction() {
        when (serviceStatus) {
            ServiceStatus.NOT_INSTALLED -> {
                Toast.makeText(this, "请先安装 Stellar 管理器", Toast.LENGTH_SHORT).show()
                log("✗ 请先安装 Stellar 管理器")
            }
            ServiceStatus.NOT_RUNNING -> {
                StellarHelper.openManager(this)
                log("已打开 Stellar 管理器")
            }
            ServiceStatus.NO_PERMISSION -> {
                try {
                    log("正在请求权限...")
                    Stellar.requestPermission(1001)
                } catch (e: Exception) {
                    log("✗ 请求权限失败: ${e.message}")
                    Toast.makeText(this, "请求失败", Toast.LENGTH_SHORT).show()
                }
            }
            else -> checkStatus()
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

    private fun getDemoCategories(): List<DemoCategory> {
        val logger = DemoFunctions.Logger { message -> log(message) }
        
        return listOf(
            DemoCategory(
                name = "基础功能",
                icon = Icons.Default.Info,
                actions = listOf(
                    DemoAction(
                        "获取服务信息",
                        "版本、UID、SELinux 上下文",
                        Icons.Default.Info
                    ) { DemoFunctions.getBasicInfo(this@MainActivity, logger) },
                    
                    DemoAction(
                        "获取版本信息",
                        "Manager版本名称、版本代码",
                        Icons.Default.Info
                    ) { DemoFunctions.getVersionInfo(this@MainActivity, logger) },
                    
                    DemoAction(
                        "检查权限状态",
                        "验证当前应用权限",
                        Icons.Default.Security
                    ) { DemoFunctions.checkPermission(this@MainActivity, logger) }
                )
            ),
            
            DemoCategory(
                name = "进程执行",
                icon = Icons.Default.Terminal,
                actions = listOf(
                    DemoAction(
                        "列出文件",
                        "执行 ls -la /sdcard",
                        Icons.Default.Folder
                    ) { DemoFunctions.runLsCommand(this@MainActivity, logger) },
                    
                    DemoAction(
                        "查看进程",
                        "执行 ps -A",
                        Icons.Default.Memory
                    ) { DemoFunctions.runPsCommand(this@MainActivity, logger) },
                    
                    DemoAction(
                        "获取系统属性",
                        "执行 getprop",
                        Icons.Default.Settings
                    ) { DemoFunctions.runGetPropCommand(this@MainActivity, logger) }
                )
            ),
            
            DemoCategory(
                name = "系统属性",
                icon = Icons.Default.PhoneAndroid,
                actions = listOf(
                    DemoAction(
                        "读取设备信息",
                        "品牌、型号、Android 版本",
                        Icons.Default.Smartphone
                    ) { DemoFunctions.readDeviceProperties(this@MainActivity, logger) },
                    
                    DemoAction(
                        "读取调试属性",
                        "ro.debuggable 等",
                        Icons.Default.BugReport
                    ) { DemoFunctions.readDebugProperties(this@MainActivity, logger) }
                )
            ),
            
            DemoCategory(
                name = "高级功能",
                icon = Icons.Default.Extension,
                actions = listOf(
                    DemoAction(
                        "检查远程权限",
                        "验证远程服务权限",
                        Icons.Default.VpnKey
                    ) { DemoFunctions.checkRemotePermissions(this@MainActivity, logger) }
                )
            )
        )
    }
}

// 数据类
data class DemoCategory(
    val name: String,
    val icon: ImageVector,
    val actions: List<DemoAction>
)

data class DemoAction(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val runner: () -> Unit
)

