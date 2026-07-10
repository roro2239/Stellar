package roro.stellar.manager.ui.features.apps

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import roro.stellar.Stellar
import roro.stellar.manager.R
import roro.stellar.manager.authorization.AuthorizationManager
import roro.stellar.manager.common.state.Status
import roro.stellar.manager.compat.ClipboardUtils
import roro.stellar.manager.domain.apps.AppInfo
import roro.stellar.manager.domain.apps.AppType
import roro.stellar.manager.domain.apps.AppsViewModel
import roro.stellar.manager.ui.components.LocalScreenConfig
import roro.stellar.manager.ui.components.StellarInfoDialog
import roro.stellar.manager.ui.components.StellarSegmentedSelector
import roro.stellar.manager.ui.navigation.components.StandardLargeTopAppBar
import roro.stellar.manager.ui.navigation.components.createTopAppBarScrollBehavior
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing
import roro.stellar.manager.util.Logger.Companion.LOGGER
import roro.stellar.manager.util.PinyinUtils
import roro.stellar.manager.util.StellarSystemApis
import roro.stellar.manager.util.UserHandleCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    topAppBarState: TopAppBarState,
    appsViewModel: AppsViewModel
) {
    val scrollBehavior = createTopAppBarScrollBehavior(topAppBarState)
    val stellarAppsResource by appsViewModel.stellarApps.observeAsState()
    val shizukuAppsResource by appsViewModel.shizukuApps.observeAsState()
    var showPermissionError by remember { mutableStateOf(false) }
    val isServiceRunning = Stellar.pingBinder()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val screenConfig = LocalScreenConfig.current
    val gridColumns = screenConfig.gridColumns
    val context = LocalContext.current
    val pm = context.packageManager

    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val searchScope = rememberCoroutineScope()

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (Stellar.pingBinder()) {
                appsViewModel.load(true)
            }
        }
    }

    LaunchedEffect(isSearching) {
        if (isSearching) focusRequester.requestFocus()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (isSearching) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearching = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    title = {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.padding(vertical = 8.dp)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.search_apps),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            isSelectionMode = true
                            selectedApps = emptySet()
                        }) {
                            Icon(
                                Icons.Default.Checklist,
                                contentDescription = null
                            )
                        }
                    }
                )
            } else {
                StandardLargeTopAppBar(
                    title = stringResource(R.string.authorized_apps),
                    scrollBehavior = scrollBehavior,
                    titleContent = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.authorized_apps),
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    searchScope.launch {
                                        val target = topAppBarState.heightOffsetLimit
                                        animate(
                                            initialValue = topAppBarState.heightOffset,
                                            targetValue = target
                                        ) { value, _ ->
                                            topAppBarState.heightOffset = value
                                        }
                                        isSearching = true
                                    }
                                }) {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                }
                                IconButton(onClick = {
                                    isSelectionMode = !isSelectionMode
                                    selectedApps = emptySet()
                                }) {
                                    Icon(Icons.Default.Checklist, contentDescription = null)
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        if (!isServiceRunning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(top = AppSpacing.topBarContentSpacing),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.service_not_running_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = stringResource(R.string.service_not_running_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else when (stellarAppsResource?.status) {
            Status.ERROR -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(top = AppSpacing.topBarContentSpacing),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.load_failed),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )

                        Text(
                            text = stellarAppsResource?.error?.message ?: stringResource(R.string.unknown_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Status.SUCCESS -> {
                val allStellar = stellarAppsResource?.data ?: emptyList()
                val allShizuku = shizukuAppsResource?.data ?: emptyList()

                fun matchesQuery(appInfo: AppInfo): Boolean {
                    if (searchQuery.isEmpty()) return true
                    val appName = appInfo.packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: ""
                    val pkgName = appInfo.packageInfo.packageName
                    return PinyinUtils.matches(appName, searchQuery) || pkgName.contains(searchQuery, ignoreCase = true)
                }

                val stellarApps = remember(allStellar, searchQuery) { allStellar.filter { matchesQuery(it) } }
                val shizukuApps = remember(allShizuku, searchQuery) { allShizuku.filter { matchesQuery(it) } }

                if (allStellar.isEmpty() && allShizuku.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(top = AppSpacing.topBarContentSpacing),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.no_authorized_apps),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = stringResource(R.string.no_authorized_apps_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = paddingValues.calculateTopPadding() + AppSpacing.topBarContentSpacing,
                                bottom = AppSpacing.screenBottomPadding,
                                start = AppSpacing.screenHorizontalPadding,
                                end = AppSpacing.screenHorizontalPadding
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                        // Stellar 原生应用分组
                        if (stellarApps.isNotEmpty()) {
                            item(span = { GridItemSpan(gridColumns) }) {
                                Text(
                                    text = stringResource(R.string.stellar_apps),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(
                                count = stellarApps.size,
                                key = { index -> "stellar_${stellarApps[index].packageInfo.packageName}_${stellarApps[index].packageInfo.applicationInfo?.uid}" }
                            ) { index ->
                                val appInfo = stellarApps[index]
                                AppListItem(
                                    appInfo = appInfo,
                                    refreshTrigger = refreshTrigger,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedApps.contains(appInfo.packageInfo.packageName),
                                    onSelectionChange = { selected ->
                                        selectedApps = if (selected) {
                                            selectedApps + appInfo.packageInfo.packageName
                                        } else {
                                            selectedApps - appInfo.packageInfo.packageName
                                        }
                                    },
                                    onUpdateFlag = { _ ->
                                        refreshTrigger++
                                    }
                                )
                            }
                        }

                        // Shizuku 兼容应用分组
                        if (shizukuApps.isNotEmpty()) {
                            item(span = { GridItemSpan(gridColumns) }) {
                                Text(
                                    text = stringResource(R.string.shizuku_compat_apps),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                            }
                            items(
                                count = shizukuApps.size,
                                key = { index -> "shizuku_${shizukuApps[index].packageInfo.packageName}_${shizukuApps[index].packageInfo.applicationInfo?.uid}" }
                            ) { index ->
                                val appInfo = shizukuApps[index]
                                AppListItem(
                                    appInfo = appInfo,
                                    refreshTrigger = refreshTrigger,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedApps.contains(appInfo.packageInfo.packageName),
                                    onSelectionChange = { selected ->
                                        selectedApps = if (selected) {
                                            selectedApps + appInfo.packageInfo.packageName
                                        } else {
                                            selectedApps - appInfo.packageInfo.packageName
                                        }
                                    },
                                    onUpdateFlag = { _ ->
                                        refreshTrigger++
                                    }
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isSelectionMode,
                        enter = slideInHorizontally(initialOffsetX = { -it }),
                        exit = slideOutHorizontally(targetOffsetX = { -it }),
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    val allApps = (stellarApps + shizukuApps).map { it.packageInfo.packageName }
                                    selectedApps = if (selectedApps.size == allApps.size) emptySet() else allApps.toSet()
                                },
                                shape = AppShape.shapes.fab,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                elevation = FloatingActionButtonDefaults.elevation(2.dp, 2.dp, 2.dp, 2.dp)
                            ) {
                                Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(22.dp))
                            }
                            FloatingActionButton(
                                onClick = {
                                    isSelectionMode = false
                                    selectedApps = emptySet()
                                },
                                shape = AppShape.shapes.fab,
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                elevation = FloatingActionButtonDefaults.elevation(2.dp, 2.dp, 2.dp, 2.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(22.dp))
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isSelectionMode,
                        enter = slideInHorizontally(initialOffsetX = { it }),
                        exit = slideOutHorizontally(targetOffsetX = { it }),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    selectedApps.forEach { pkg ->
                                        val app = (stellarApps + shizukuApps).find { it.packageInfo.packageName == pkg }
                                        app?.let {
                                            try {
                                                val uid = it.packageInfo.applicationInfo?.uid ?: return@forEach
                                                val isShizuku = it.appType == AppType.SHIZUKU
                                                val type = if (isShizuku) "shizuku" else "stellar"
                                                val flag = if (isShizuku) 0 else AuthorizationManager.FLAG_ASK
                                                Stellar.updateFlagForUid(uid, type, flag)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    refreshTrigger++
                                    isSelectionMode = false
                                    selectedApps = emptySet()
                                },
                                shape = AppShape.shapes.fab,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                elevation = FloatingActionButtonDefaults.elevation(2.dp, 2.dp, 2.dp, 2.dp),
                                icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(22.dp)) },
                                text = { Text(stringResource(R.string.permission_ask), style = MaterialTheme.typography.labelMedium) }
                            )
                            ExtendedFloatingActionButton(
                                onClick = {
                                    selectedApps.forEach { pkg ->
                                        val app = (stellarApps + shizukuApps).find { it.packageInfo.packageName == pkg }
                                        app?.let {
                                            try {
                                                val uid = it.packageInfo.applicationInfo?.uid ?: return@forEach
                                                val isShizuku = it.appType == AppType.SHIZUKU
                                                val type = if (isShizuku) "shizuku" else "stellar"
                                                val flag = if (isShizuku) 2 else AuthorizationManager.FLAG_GRANTED
                                                Stellar.updateFlagForUid(uid, type, flag)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    refreshTrigger++
                                    isSelectionMode = false
                                    selectedApps = emptySet()
                                },
                                shape = AppShape.shapes.fab,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                elevation = FloatingActionButtonDefaults.elevation(2.dp, 2.dp, 2.dp, 2.dp),
                                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp)) },
                                text = { Text(stringResource(R.string.permission_allow), style = MaterialTheme.typography.labelMedium) }
                            )
                            ExtendedFloatingActionButton(
                                onClick = {
                                    selectedApps.forEach { pkg ->
                                        val app = (stellarApps + shizukuApps).find { it.packageInfo.packageName == pkg }
                                        app?.let {
                                            try {
                                                val uid = it.packageInfo.applicationInfo?.uid ?: return@forEach
                                                val isShizuku = it.appType == AppType.SHIZUKU
                                                val type = if (isShizuku) "shizuku" else "stellar"
                                                val flag = if (isShizuku) 4 else AuthorizationManager.FLAG_DENIED
                                                Stellar.updateFlagForUid(uid, type, flag)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    refreshTrigger++
                                    isSelectionMode = false
                                    selectedApps = emptySet()
                                },
                                shape = AppShape.shapes.fab,
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                elevation = FloatingActionButtonDefaults.elevation(2.dp, 2.dp, 2.dp, 2.dp),
                                icon = { Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(22.dp)) },
                                text = { Text(stringResource(R.string.permission_deny), style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }
            }
            }

            null -> {}
        }
    }

    if (showPermissionError) {
        StellarInfoDialog(
            onDismissRequest = { showPermissionError = false },
            title = stringResource(R.string.adb_permission_restricted),
            message = stringResource(R.string.adb_permission_restricted_message),
            onConfirm = { showPermissionError = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListItem(
    appInfo: AppInfo,
    refreshTrigger: Int,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionChange: (Boolean) -> Unit = {},
    onUpdateFlag: (Int) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val packageInfo = appInfo.packageInfo
    val ai = packageInfo.applicationInfo ?: return

    val uid = ai.uid
    val userId = UserHandleCompat.getUserId(uid)
    val packageName = packageInfo.packageName
    val isShizukuApp = appInfo.appType == AppType.SHIZUKU

    val appName = remember(ai) {
        if (userId != UserHandleCompat.myUserId()) {
            try {
                val userInfo = StellarSystemApis.getUserInfo(userId)
                "${ai.loadLabel(pm)} - ${userInfo.name} ($userId)"
            } catch (_: Exception) {
                "${ai.loadLabel(pm)} - User $userId"
            }
        } else {
            ai.loadLabel(pm).toString()
        }
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
        return try {
            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    val iconPainter = remember(ai) {
        try {
            val drawable = ai.loadIcon(pm)
            val bitmap = drawableToBitmap(drawable)
            bitmap?.asImageBitmap()?.let { BitmapPainter(it) }
        } catch (_: Exception) {
            null
        }
    }

    val permissionType = if (isShizukuApp) "shizuku" else "stellar"

    fun shizukuToStellarFlag(shizukuFlag: Int): Int {
        return when (shizukuFlag) {
            0 -> AuthorizationManager.FLAG_ASK
            2 -> AuthorizationManager.FLAG_GRANTED
            4 -> AuthorizationManager.FLAG_DENIED
            else -> AuthorizationManager.FLAG_ASK
        }
    }

    fun stellarToShizukuFlag(stellarFlag: Int): Int {
        return when (stellarFlag) {
            AuthorizationManager.FLAG_ASK -> 0
            AuthorizationManager.FLAG_GRANTED -> 2
            AuthorizationManager.FLAG_DENIED -> 4
            else -> 0
        }
    }

    var stellarFlag by remember(refreshTrigger) {
        mutableIntStateOf(
            try {
                val rawFlag = Stellar.getFlagForUid(uid, permissionType)
                if (isShizukuApp) shizukuToStellarFlag(rawFlag) else rawFlag
            } catch (e: Exception) {
                LOGGER.w("获取应用授权状态异常", tr = e)
                AuthorizationManager.FLAG_ASK
            }
        )
    }

    var followStartupFlag by remember(refreshTrigger) {
        mutableIntStateOf(
            try {
                Stellar.getFlagForUid(uid, "follow_stellar_startup")
            } catch (e: Exception) {
                LOGGER.w("获取跟随启动权限状态异常", tr = e)
                AuthorizationManager.FLAG_ASK
            }
        )
    }

    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    @SuppressLint("LocalContextGetResourceValueCall")
    fun onLongPress() {
        scope.launch {
            val logs = withContext(Dispatchers.IO) {
                if (!Stellar.pingBinder()) return@withContext null
                try {
                    Stellar.getLogsForUid(uid)
                } catch (_: Throwable) {
                    null
                }
            }
            when {
                logs == null -> Toast.makeText(
                    context,
                    context.getString(R.string.logs_app_not_running),
                    Toast.LENGTH_LONG
                ).show()

                logs.isEmpty() -> Toast.makeText(
                    context,
                    context.getString(R.string.no_logs_to_copy),
                    Toast.LENGTH_SHORT
                ).show()

                else -> {
                    ClipboardUtils.put(context, logs.joinToString("\n"))
                    Toast.makeText(
                        context,
                        context.getString(R.string.logs_copied),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val cardPaddingStart by animateDpAsState(
        targetValue = if (isSelectionMode) 48.dp else 0.dp,
        label = "cardPadding"
    )
    val statusOffsetX by animateDpAsState(
        targetValue = if (isSelectionMode) 32.dp else 0.dp,
        label = "statusOffset"
    )
    val chevronOffsetX by animateDpAsState(
        targetValue = if (isSelectionMode) 16.dp else 0.dp,
        label = "chevronOffset"
    )
    val chevronAlpha by animateFloatAsState(
        targetValue = if (isSelectionMode) 0f else 1f,
        label = "chevronAlpha"
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = cardPaddingStart),
            shape = AppShape.shapes.cardMedium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            val rotation by animateFloatAsState(targetValue = if (expanded) 90f else 0f, label = "")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (isSelectionMode) {
                                onSelectionChange(!isSelected)
                            } else {
                                expanded = !expanded
                            }
                        },
                        onLongClick = { if (!isSelectionMode) onLongPress() }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconPainter != null) {
                    Image(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(AppShape.shapes.iconSmall)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(AppShape.shapes.iconSmall)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = when (stellarFlag) {
                        AuthorizationManager.FLAG_ASK -> stringResource(R.string.permission_ask)
                        AuthorizationManager.FLAG_GRANTED -> stringResource(R.string.permission_allow)
                        AuthorizationManager.FLAG_DENIED -> stringResource(R.string.permission_deny)
                        else -> stringResource(R.string.unknown)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.offset(x = statusOffsetX)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .offset(x = chevronOffsetX)
                        .alpha(chevronAlpha)
                        .rotate(rotation)
                        .size(24.dp)
                )
            }

            AnimatedVisibility(visible = expanded && !isSelectionMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PermissionItem(
                        title = if (isShizukuApp) stringResource(R.string.shizuku_permission) else stringResource(
                            R.string.basic_permission
                        ),
                        subtitle = if (isShizukuApp) stringResource(R.string.shizuku_permission_subtitle) else stringResource(
                            R.string.basic_permission_subtitle
                        ),
                        currentFlag = stellarFlag,
                        onFlagChange = { newFlag ->
                            try {
                                stellarFlag = newFlag
                                val actualFlag =
                                    if (isShizukuApp) stellarToShizukuFlag(newFlag) else newFlag
                                Stellar.updateFlagForUid(uid, permissionType, actualFlag)
                                onUpdateFlag(newFlag)
                            } catch (e: Exception) {
                                LOGGER.e("更新权限失败", tr = e)
                            }
                        }
                    )

                    if (!isShizukuApp) {
                        PermissionItem(
                            title = stringResource(R.string.follow_startup),
                            subtitle = stringResource(R.string.follow_startup_subtitle),
                            currentFlag = followStartupFlag,
                            onFlagChange = { newFlag ->
                                try {
                                    followStartupFlag = newFlag
                                    Stellar.updateFlagForUid(uid, "follow_stellar_startup", newFlag)
                                } catch (e: Exception) {
                                    LOGGER.e("更新跟随启动权限失败", tr = e)
                                }
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInHorizontally(initialOffsetX = { -it * 2 }),
            exit = slideOutHorizontally(targetOffsetX = { -it * 2 }),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-8).dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChange
            )
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    subtitle: String,
    currentFlag: Int,
    onFlagChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }

        PermissionSegmentSelector(
            currentFlag = currentFlag,
            onFlagChange = onFlagChange
        )
    }
}

@Composable
fun PermissionSegmentSelector(
    currentFlag: Int,
    onFlagChange: (Int) -> Unit
) {
    val entries = listOf(
        AuthorizationManager.FLAG_ASK,
        AuthorizationManager.FLAG_GRANTED,
        AuthorizationManager.FLAG_DENIED
    )
    val labels = mapOf(
        AuthorizationManager.FLAG_ASK to stringResource(R.string.permission_ask),
        AuthorizationManager.FLAG_GRANTED to stringResource(R.string.permission_allow),
        AuthorizationManager.FLAG_DENIED to stringResource(R.string.permission_deny)
    )

    StellarSegmentedSelector(
        items = entries,
        selectedItem = currentFlag,
        onItemSelected = onFlagChange,
        itemLabel = { labels[it] ?: "" },
        itemHeight = AppSpacing.selectorItemHeightSmall
    )
}

