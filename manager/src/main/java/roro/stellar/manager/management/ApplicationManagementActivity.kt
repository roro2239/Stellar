package roro.stellar.manager.management

import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import roro.stellar.manager.authorization.AuthorizationManager
import roro.stellar.manager.compat.Status
import roro.stellar.manager.ui.theme.StellarTheme
import roro.stellar.manager.ui.theme.AppSpacing
import roro.stellar.manager.utils.UserHandleCompat
import roro.stellar.manager.utils.StellarSystemApis
import roro.stellar.Stellar

class ApplicationManagementActivity : ComponentActivity() {

    private val viewModel by appsViewModel()

    private val binderDeadListener = Stellar.OnBinderDeadListener {
        if (!isFinishing && !isDestroyed) {
            runOnUiThread {
                Toast.makeText(this, "Stellar 服务已停止", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!checkStellarService()) {
            return
        }

        setContent {
            StellarTheme {
                AppManagementScreen(
                    viewModel = viewModel,
                    onBackPressed = { finish() }
                )
            }
        }

        if (viewModel.packages.value == null) {
            viewModel.load()
        }

        Stellar.addBinderDeadListener(binderDeadListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        Stellar.removeBinderDeadListener(binderDeadListener)
    }

    override fun onResume() {
        super.onResume()
        viewModel.load(true)
    }

    private fun checkStellarService(): Boolean {
        return try {
            if (!Stellar.pingBinder()) {
                Toast.makeText(this, "Stellar 服务未运行", Toast.LENGTH_SHORT).show()
                finish()
                false
            } else {
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "无法连接 Stellar 服务", Toast.LENGTH_SHORT).show()
            finish()
            false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagementScreen(
    viewModel: AppsViewModel,
    onBackPressed: () -> Unit
) {
    val packagesResource by viewModel.packages.observeAsState()
    
    var showPermissionError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("授权管理") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        when (packagesResource?.status) {
            Status.LOADING -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            Status.ERROR -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "加载失败: ${packagesResource?.error?.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Status.SUCCESS -> {
                val packages = packagesResource?.data ?: emptyList()
                
                if (packages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("没有应用请求权限")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(
                            top = AppSpacing.topBarContentSpacing,
                            bottom = AppSpacing.screenBottomPadding,
                            start = AppSpacing.screenHorizontalPadding,
                            end = AppSpacing.screenHorizontalPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.itemSpacing)
                    ) {
                        items(
                            count = packages.size,
                            key = { index -> "${packages[index].packageName}_${packages[index].applicationInfo?.uid}" }
                        ) { index ->
                            val packageInfo = packages[index]
                            AppListItem(
                                packageInfo = packageInfo,
                                onToggle = { granted ->
                                    try {
                                        val packageName = packageInfo.packageName
                                        val uid = packageInfo.applicationInfo!!.uid
                                        
                                        if (granted) {
                                            AuthorizationManager.grant(packageName, uid)
                                        } else {
                                            AuthorizationManager.revoke(packageName, uid)
                                        }
                                        
                                        viewModel.load(true)
                                    } catch (e: SecurityException) {
                                        showPermissionError = true
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            )
                        }
                    }
                }
            }
            null -> {}
        }
    }

    if (showPermissionError) {
        AlertDialog(
            onDismissRequest = { showPermissionError = false },
            title = { Text("ADB 权限受限") },
            text = { Text("您的设备制造商很可能限制了 ADB 的权限。\n\n请在开发者选项中调整相关设置，或尝试使用 Root 模式运行。") },
            confirmButton = {
                TextButton(onClick = { showPermissionError = false }) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
fun AppListItem(
    packageInfo: PackageInfo,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val ai = packageInfo.applicationInfo ?: return
    
    val uid = ai.uid
    val userId = UserHandleCompat.getUserId(uid)
    val packageName = packageInfo.packageName
    
    val isGranted = remember(packageName, uid) {
        try {
            AuthorizationManager.granted(packageName, uid)
        } catch (e: Exception) {
            false
        }
    }
    
    var checked by remember(packageName, uid) { mutableStateOf(isGranted) }
    
    LaunchedEffect(packageName, uid) {
        checked = try {
            AuthorizationManager.granted(packageName, uid)
        } catch (e: Exception) {
            false
        }
    }
    
    val appName = remember(ai) {
        if (userId != UserHandleCompat.myUserId()) {
            try {
                val userInfo = StellarSystemApis.getUserInfo(userId)
                "${ai.loadLabel(pm)} - ${userInfo.name} ($userId)"
            } catch (e: Exception) {
                "${ai.loadLabel(pm)} - User $userId"
            }
        } else {
            ai.loadLabel(pm).toString()
        }
    }
    
    val requiresRoot = remember(ai) {
        try {
            ai.metaData?.getBoolean("com.stellar.client.V3_REQUIRES_ROOT") == true
        } catch (e: Exception) {
            false
        }
    }
    
    val iconPainter = remember(ai) {
        try {
            val drawable = ai.loadIcon(pm)
            val bitmap = drawableToBitmap(drawable)
            bitmap?.asImageBitmap()?.let { BitmapPainter(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                checked = !checked
                onToggle(checked)
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconPainter != null) {
                Image(
                    painter = iconPainter,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (requiresRoot) {
                    Text(
                        text = "需要 Root",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Switch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    onToggle(it)
                }
            )
        }
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }
    
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
    
    return try {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    } catch (e: Exception) {
        null
    }
}

