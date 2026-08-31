package com.wanshijie.aptiv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wanshijie.aptiv.data.ApiException
import com.wanshijie.aptiv.data.AppSettings
import com.wanshijie.aptiv.data.AppUpdateInfo
import com.wanshijie.aptiv.data.AppUpdateManager
import com.wanshijie.aptiv.data.InstallLaunchResult
import com.wanshijie.aptiv.data.MaterialPullApi
import com.wanshijie.aptiv.ui.theme.APTIVTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = AppSettings(this)
        setContent {
            APTIVTheme {
                MaterialPullApp(settings)
            }
        }
    }
}

@Composable
fun MaterialPullApp(settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var serverUrl by remember { mutableStateOf(settings.serverBaseUrl) }
    var submitPath by remember { mutableStateOf(settings.scanSubmitPath) }
    var deviceNo by remember { mutableStateOf(settings.deviceNo) }
    var employeeNo by remember { mutableStateOf(settings.employeeNo) }
    val deviceModel = remember { settings.deviceModel }
    var showSettings by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var lastScan by remember { mutableStateOf<ScanRecord?>(null) }
    var pendingPreview by remember { mutableStateOf<ScanPreview?>(null) }
    var requestQtyText by remember { mutableStateOf("") }
    var requestUnit by remember { mutableStateOf(DEFAULT_REQUEST_UNIT) }
    var operation by remember { mutableStateOf(OperationState("待扫码", "按实体扫描键，或点击右上角扫码图标")) }
    var updateState by remember { mutableStateOf(AppUpdateUiState()) }
    val currentVersionName = remember {
        AppUpdateManager(context.applicationContext, settings.serverBaseUrl).currentVersionName()
    }

    // APP 打开后立即唤醒后端并预热 HTTP 连接，减少第一次扫码等待时间。
    LaunchedEffect(Unit) {
        val startedAt = SystemClock.elapsedRealtime()
        runCatching {
            withContext(Dispatchers.IO) {
                MaterialPullApi(AppSettings.normalizeBaseUrl(settings.serverBaseUrl)).healthReady()
            }
        }.onSuccess {
            Log.i("MaterialPullApp", "server warm-up completed in ${SystemClock.elapsedRealtime() - startedAt}ms")
        }.onFailure {
            Log.w("MaterialPullApp", "server warm-up failed in ${SystemClock.elapsedRealtime() - startedAt}ms: ${it.message}")
        }
    }

    val handleScan: (String, String) -> Unit = { rawValue, format ->
        val value = rawValue.trim()
        if (value.isBlank()) {
            scope.launch {
                snackbarHostState.showSnackbar("未读取到条码")
            }
        } else if (isSending) {
            scope.launch {
                snackbarHostState.showSnackbar("正在发送上一次扫码")
            }
        } else {
            lastScan = ScanRecord(value, format)
            scope.launch {
                isSending = true
                pendingPreview = null
                operation = OperationState("正在查询物料", value)
                val normalizedUrl = AppSettings.normalizeBaseUrl(serverUrl)
                val normalizedPath = AppSettings.normalizePath(submitPath)
                settings.serverBaseUrl = normalizedUrl
                settings.scanSubmitPath = normalizedPath
                settings.deviceNo = deviceNo
                settings.employeeNo = employeeNo
                serverUrl = normalizedUrl
                submitPath = normalizedPath
                try {
                    val preview = fetchScanPreview(
                        serverUrl = normalizedUrl,
                        deviceNo = deviceNo,
                        deviceModel = deviceModel,
                        employeeNo = employeeNo,
                        scanCode = value,
                        format = format
                    )
                    pendingPreview = preview
                    requestQtyText = preview.defaultQty
                    requestUnit = preview.defaultUnit.ifBlank { DEFAULT_REQUEST_UNIT }
                    operation = OperationState("请确认申请", "核对物料、数量和单位后点击发送")
                } catch (e: Exception) {
                    Log.e("MaterialPullApp", "load scan preview failed: serverUrl=$normalizedUrl deviceNo=$deviceNo employeeNo=$employeeNo format=$format scanCode=$value", e)
                    operation = OperationState("查询失败", e.message ?: "无法获取物料信息", isError = true)
                    scope.launch { snackbarHostState.showSnackbar("查询失败") }
                } finally {
                    isSending = false
                }
            }
        }
    }

    val submitPreview: () -> Unit = {
        val preview = pendingPreview
        val quantity = requestQtyText.trim().toBigDecimalOrNull()
        if (preview == null) {
            scope.launch { snackbarHostState.showSnackbar("请先扫码获取物料信息") }
        } else if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            scope.launch { snackbarHostState.showSnackbar("申请数量必须大于 0") }
        } else {
            val normalizedUnit = requestUnit.trim().ifBlank { DEFAULT_REQUEST_UNIT }
            scope.launch {
                isSending = true
                operation = OperationState("正在发送申请", "${quantity.stripTrailingZeros().toPlainString()} $normalizedUnit")
                val result = sendScannedCode(
                    serverUrl = AppSettings.normalizeBaseUrl(serverUrl),
                    submitPath = AppSettings.normalizePath(submitPath),
                    deviceNo = deviceNo,
                    deviceModel = deviceModel,
                    employeeNo = employeeNo,
                    scanCode = preview.scanCode,
                    format = preview.format,
                    requestQty = quantity,
                    requestUnit = normalizedUnit
                )
                isSending = false
                operation = result
                if (!result.isError) {
                    pendingPreview = null
                    requestQtyText = ""
                    requestUnit = DEFAULT_REQUEST_UNIT
                }
                snackbarHostState.showSnackbar(result.title)
            }
        }
    }

    val checkForUpdate: () -> Unit = {
        if (!updateState.isBusy) {
            scope.launch {
                updateState = AppUpdateUiState(isChecking = true, message = "正在检查更新…")
                try {
                    val manager = AppUpdateManager(context.applicationContext, AppSettings.normalizeBaseUrl(serverUrl))
                    val info = withContext(Dispatchers.IO) { manager.checkForUpdate() }
                    updateState = if (info.versionCode > manager.currentVersionCode()) {
                        AppUpdateUiState(info = info, message = "发现新版本 ${info.versionName}")
                    } else {
                        AppUpdateUiState(message = "当前已是最新版本")
                    }
                } catch (e: Exception) {
                    updateState = AppUpdateUiState(message = e.message ?: "检查更新失败", isError = true)
                }
            }
        }
    }

    val downloadUpdate: () -> Unit = {
        val info = updateState.info
        if (info != null && !updateState.isBusy) {
            scope.launch {
                updateState = updateState.copy(isDownloading = true, progress = 0, message = "正在下载 ${info.versionName}…", isError = false)
                try {
                    val manager = AppUpdateManager(context.applicationContext, AppSettings.normalizeBaseUrl(serverUrl))
                    val apk = withContext(Dispatchers.IO) {
                        manager.downloadUpdate(info) { progress ->
                            scope.launch {
                                updateState = updateState.copy(progress = progress, message = "正在下载 ${info.versionName}：$progress%")
                            }
                        }
                    }
                    updateState = updateState.copy(
                        isDownloading = false,
                        progress = 100,
                        downloadedApk = apk,
                        message = "下载完成，点击安装更新"
                    )
                } catch (e: Exception) {
                    updateState = updateState.copy(isDownloading = false, message = e.message ?: "下载更新失败", isError = true)
                }
            }
        }
    }

    val installUpdate: () -> Unit = {
        val apk = updateState.downloadedApk
        if (apk != null && !updateState.isBusy) {
            try {
                val manager = AppUpdateManager(context.applicationContext, AppSettings.normalizeBaseUrl(serverUrl))
                updateState = when (manager.launchInstaller(apk)) {
                    InstallLaunchResult.STARTED -> updateState.copy(message = "已打开系统安装界面")
                    InstallLaunchResult.NEEDS_PERMISSION -> updateState.copy(message = "请允许本APP安装未知应用，返回后再次点击安装")
                }
            } catch (e: Exception) {
                updateState = updateState.copy(message = e.message ?: "无法启动安装", isError = true)
            }
        }
    }

    MobyScannerReceiver(onBarcodeDetected = handleScan)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            HomeScreen(
                serverUrl = serverUrl,
                submitPath = submitPath,
                deviceNo = deviceNo,
                deviceModel = deviceModel,
                employeeNo = employeeNo,
                showSettings = showSettings,
                isSending = isSending,
                lastScan = lastScan,
                pendingPreview = pendingPreview,
                requestQtyText = requestQtyText,
                requestUnit = requestUnit,
                operation = operation,
                currentVersionName = currentVersionName,
                updateState = updateState,
                onScanClick = {
                    operation = OperationState("等待扫码", "请按 M71 扫描键，或把条码对准扫码头后再次点击按钮触发扫描")
                    startMobyScan(context)
                    scope.launch {
                        snackbarHostState.showSnackbar("已触发扫码头")
                    }
                },
                onRequestQtyChange = { requestQtyText = it },
                onRequestUnitChange = { requestUnit = it },
                onSubmitPreview = submitPreview,
                onCheckForUpdate = checkForUpdate,
                onDownloadUpdate = downloadUpdate,
                onInstallUpdate = installUpdate,
                onCancelPreview = {
                    pendingPreview = null
                    requestQtyText = ""
                    requestUnit = DEFAULT_REQUEST_UNIT
                    operation = OperationState("已取消", "本次申请未发送，可以重新扫码")
                },
                onToggleSettings = { showSettings = !showSettings },
                onServerUrlChange = { serverUrl = it },
                onSubmitPathChange = { submitPath = it },
                onDeviceNoChange = { deviceNo = it },
                onEmployeeNoChange = { employeeNo = it },
                onSaveSettings = {
                    settings.serverBaseUrl = serverUrl
                    settings.scanSubmitPath = submitPath
                    settings.deviceNo = deviceNo
                    settings.employeeNo = employeeNo
                    serverUrl = settings.serverBaseUrl
                    submitPath = settings.scanSubmitPath
                    operation = OperationState("设置已保存", "${settings.serverBaseUrl}${settings.scanSubmitPath}")
                    scope.launch {
                        snackbarHostState.showSnackbar("设置已保存")
                        runCatching {
                            withContext(Dispatchers.IO) {
                                MaterialPullApi(settings.serverBaseUrl).deviceLogin(
                                    deviceNo = settings.deviceNo,
                                    deviceModel = deviceModel,
                                    employeeNo = settings.employeeNo,
                                    remark = "save_settings"
                                )
                            }
                        }
                    }
                },
                onHealthCheck = {
                    scope.launch {
                        isSending = true
                        val normalizedUrl = AppSettings.normalizeBaseUrl(serverUrl)
                        settings.serverBaseUrl = normalizedUrl
                        serverUrl = normalizedUrl
                        val result = testServer(
                            serverUrl = normalizedUrl,
                            deviceNo = deviceNo,
                            deviceModel = deviceModel,
                            employeeNo = employeeNo
                        )
                        isSending = false
                        operation = result
                        snackbarHostState.showSnackbar(result.title)
                    }
                }
            )
        }
    }
}

@Composable
private fun MobyScannerReceiver(
    onBarcodeDetected: (String, String) -> Unit
) {
    val context = LocalContext.current
    val latestOnBarcodeDetected by rememberUpdatedState(onBarcodeDetected)

    DisposableEffect(context) {
        val appContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action !in MobyScannerContract.RESULT_ACTIONS) return
                val barcode = intent.extractBarcodeValue()
                if (barcode.isNullOrBlank()) {
                    Log.w("MaterialPullApp", "Moby scan broadcast has no barcode. action=${intent.action} extras=${intent.extras?.keySet()}")
                    return
                }
                latestOnBarcodeDetected(barcode, intent.extractBarcodeFormat())
            }
        }
        val filter = IntentFilter().apply {
            MobyScannerContract.RESULT_ACTIONS.forEach(::addAction)
        }

        enableMobyBroadcastMode(appContext)
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        onDispose {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }
}

@Composable
private fun HomeScreen(
    serverUrl: String,
    submitPath: String,
    deviceNo: String,
    deviceModel: String,
    employeeNo: String,
    showSettings: Boolean,
    isSending: Boolean,
    lastScan: ScanRecord?,
    pendingPreview: ScanPreview?,
    requestQtyText: String,
    requestUnit: String,
    operation: OperationState,
    currentVersionName: String,
    updateState: AppUpdateUiState,
    onScanClick: () -> Unit,
    onRequestQtyChange: (String) -> Unit,
    onRequestUnitChange: (String) -> Unit,
    onSubmitPreview: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onCancelPreview: () -> Unit,
    onToggleSettings: () -> Unit,
    onServerUrlChange: (String) -> Unit,
    onSubmitPathChange: (String) -> Unit,
    onDeviceNoChange: (String) -> Unit,
    onEmployeeNoChange: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onHealthCheck: () -> Unit
) {
    if (showSettings) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("设备设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onToggleSettings) {
                    Icon(Icons.Filled.Check, contentDescription = "关闭设置")
                }
            }
            SettingsPanel(
                serverUrl = serverUrl,
                submitPath = submitPath,
                deviceNo = deviceNo,
                deviceModel = deviceModel,
                employeeNo = employeeNo,
                operation = operation,
                lastScan = lastScan,
                isBusy = isSending,
                onServerUrlChange = onServerUrlChange,
                onSubmitPathChange = onSubmitPathChange,
                onDeviceNoChange = onDeviceNoChange,
                onEmployeeNoChange = onEmployeeNoChange,
                onSaveSettings = onSaveSettings,
                onHealthCheck = onHealthCheck
            )
            UpdatePanel(
                currentVersionName = currentVersionName,
                state = updateState,
                onCheckForUpdate = onCheckForUpdate,
                onDownloadUpdate = onDownloadUpdate,
                onInstallUpdate = onInstallUpdate
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onScanClick,
                enabled = !isSending,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isSending) "正在查询" else "物料申请",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = "扫码", modifier = Modifier.size(24.dp))
                    }
                }
            }
            IconButton(onClick = onToggleSettings, enabled = !isSending, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = "设置", modifier = Modifier.size(24.dp))
            }
        }

        if (pendingPreview != null) {
            ScanPreviewCard(
                preview = pendingPreview,
                requestQtyText = requestQtyText,
                requestUnit = requestUnit,
                isBusy = isSending,
                onRequestQtyChange = onRequestQtyChange,
                onRequestUnitChange = onRequestUnitChange,
                onSubmit = onSubmitPreview,
                onCancel = onCancelPreview
            )
        } else {
            ResultCard(operation = operation, lastScan = null)
        }
    }
}

@Composable
private fun UpdatePanel(
    currentVersionName: String,
    state: AppUpdateUiState,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("APP 更新", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "当前版本：$currentVersionName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            state.info?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text("更新说明：$notes", style = MaterialTheme.typography.bodySmall)
            }
            if (state.isDownloading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("下载进度 ${state.progress}%", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCheckForUpdate,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.isChecking) "检查中" else "检查更新")
                }
                when {
                    state.downloadedApk != null -> Button(
                        onClick = onInstallUpdate,
                        enabled = !state.isBusy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("安装更新")
                    }

                    state.info != null -> Button(
                        onClick = onDownloadUpdate,
                        enabled = !state.isBusy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (state.isDownloading) "下载中" else "下载更新")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanPreviewCard(
    preview: ScanPreview,
    requestQtyText: String,
    requestUnit: String,
    isBusy: Boolean,
    onRequestQtyChange: (String) -> Unit,
    onRequestUnitChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text("确认申请", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "物料编号  ${preview.materialCode.ifBlank { preview.scanCode }}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "默认 ${preview.defaultQty} ${preview.defaultUnit}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = requestQtyText,
                    onValueChange = onRequestQtyChange,
                    label = { Text("数量") },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1.35f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = requestUnit,
                    onValueChange = onRequestUnitChange,
                    label = { Text("单位") },
                    enabled = !isBusy,
                    modifier = Modifier.weight(0.65f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("取消")
                }
                Button(
                    onClick = onSubmit,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(if (isBusy) "发送中" else "发送")
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    serverUrl: String,
    submitPath: String,
    deviceNo: String,
    deviceModel: String,
    employeeNo: String,
    operation: OperationState,
    lastScan: ScanRecord?,
    isBusy: Boolean,
    onServerUrlChange: (String) -> Unit,
    onSubmitPathChange: (String) -> Unit,
    onDeviceNoChange: (String) -> Unit,
    onEmployeeNoChange: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onHealthCheck: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            AppTextField(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                label = "服务器地址",
                keyboardType = KeyboardType.Uri
            )
            AppTextField(
                value = submitPath,
                onValueChange = onSubmitPathChange,
                label = "提交路径"
            )
            AppTextField(
                value = deviceNo,
                onValueChange = onDeviceNoChange,
                label = "设备编号"
            )
            AppTextField(
                value = employeeNo,
                onValueChange = onEmployeeNoChange,
                label = "员工工号（仅记录，不参与校验）"
            )
            Text(
                text = "设备型号：$deviceModel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ResultCard(operation = operation, lastScan = lastScan)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onSaveSettings,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存")
                }
                OutlinedButton(
                    onClick = onHealthCheck,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("测试")
                }
            }
        }
    }
}

@Composable
private fun ResultCard(operation: OperationState, lastScan: ScanRecord?) {
    val container = if (operation.isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val content = if (operation.isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val icon = if (operation.isError) Icons.Filled.Warning else Icons.Filled.Send

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, content.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(operation.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(operation.detail, style = MaterialTheme.typography.bodyMedium)
                if (lastScan != null) {
                    Text(
                        text = "${lastScan.format} / ${lastScan.value}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

private fun startMobyScan(context: Context) {
    val appContext = context.applicationContext
    enableMobyBroadcastMode(appContext)
    runCatching {
        appContext.sendBroadcast(Intent(MobyScannerContract.START_DECODE_ACTION))
    }.onFailure { e ->
        Log.e("MaterialPullApp", "start Moby scanner failed", e)
    }
}

private fun enableMobyBroadcastMode(context: Context) {
    runCatching {
        val intent = Intent(MobyScannerContract.SET_PROPERTY_INT_ACTION)
            .putExtra("PropertyID", MobyScannerContract.BROADCAST_PROPERTY_ID)
            .putExtra("PropertyInt", 1)
        context.applicationContext.sendBroadcast(intent)
    }.onFailure { e ->
        Log.w("MaterialPullApp", "enable Moby broadcast mode failed", e)
    }
}

private fun Intent.extractBarcodeValue(): String? {
    val extras = extras ?: return null
    MobyScannerContract.BARCODE_EXTRA_KEYS
        .asSequence()
        .mapNotNull { key -> getExtraText(key, includeNumbers = false) }
        .map { it.cleanBarcodeText() }
        .firstOrNull { it.isNotBlank() }
        ?.let { return it }

    return extras.keySet()
        .asSequence()
        .filterNot { key -> key in MobyScannerContract.FORMAT_EXTRA_KEYS }
        .mapNotNull { key -> getExtraText(key, includeNumbers = false) }
        .map { it.cleanBarcodeText() }
        .firstOrNull { it.isNotBlank() }
}

private fun Intent.extractBarcodeFormat(): String {
    val format = MobyScannerContract.FORMAT_EXTRA_KEYS
        .asSequence()
        .mapNotNull { key -> getExtraText(key, includeNumbers = true) }
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }

    return if (format.isNullOrBlank()) "MOBYDATA" else "MOBYDATA_$format"
}

private fun Intent.getExtraText(key: String, includeNumbers: Boolean): String? {
    val value = extras?.get(key) ?: return null
    return when (value) {
        is String -> value
        is CharSequence -> value.toString()
        is ByteArray -> String(value, Charsets.UTF_8)
        is Int -> if (includeNumbers) value.toString() else null
        is Long -> if (includeNumbers) value.toString() else null
        else -> value.toString()
    }
}

private fun String.cleanBarcodeText(): String {
    return replace("\r", "").replace("\n", "").trim()
}

private object MobyScannerContract {
    const val START_DECODE_ACTION = "com.android.decode.action.START_DECODE"
    const val SET_PROPERTY_INT_ACTION = "com.android.action.setPropertyInt"
    const val BROADCAST_PROPERTY_ID = 0x30D40

    val RESULT_ACTIONS = setOf(
        "com.android.decodewedge.decode_action"
    )

    val BARCODE_EXTRA_KEYS = listOf(
        "com.android.decode.intentwedge.barcode_string",
        "barcode_string",
        "barcode",
        "scannerdata",
        "data",
        "SCAN_BARCODE1"
    )

    val FORMAT_EXTRA_KEYS = listOf(
        "com.android.decode.intentwedge.barcode_type",
        "barcode_type",
        "barcodeType",
        "codeType",
        "type",
        "SCAN_STATE"
    )
}

private suspend fun fetchScanPreview(
    serverUrl: String,
    deviceNo: String,
    deviceModel: String,
    employeeNo: String,
    scanCode: String,
    format: String
): ScanPreview {
    val startedAt = SystemClock.elapsedRealtime()
    val response = withContext(Dispatchers.IO) {
        MaterialPullApi(serverUrl).previewBarcodeScan(
            scanCode = scanCode,
            format = format,
            deviceNo = deviceNo,
            deviceModel = deviceModel,
            employeeNo = employeeNo
        )
    }
    Log.i("MaterialPullApp", "scan preview received in ${SystemClock.elapsedRealtime() - startedAt}ms")
    val quantityText = response.firstText("defaultQty", "standardQty", "requestQty")
    val quantity = quantityText.toBigDecimalOrNull()
    if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
        throw ApiException("后台未返回有效的默认申请数量")
    }
    return ScanPreview(
        // 必须保留扫码枪的原始内容供第二阶段正式提交。
        // 后端预览返回的 scannedCode 是拆分后的纯物料号；若用它覆盖原值，
        // "13609637,物料架-11-E06,备用" 会变成 "13609637"，工位与用途全部丢失。
        scanCode = scanCode,
        format = format,
        materialCode = response.firstText("materialCode"),
        materialName = response.firstText("materialName"),
        warehouseCode = response.firstText("warehouseCode"),
        stationAddress = response.firstText(
            "sendStationAddress",
            "deliveryAddress",
            "stationCode",
            "warehouseAddress",
            "warehouseLocation"
        ),
        defaultQty = quantity.stripTrailingZeros().toPlainString(),
        defaultUnit = response.firstText("defaultUnit", "standardUnit", "requestUnit", "unit")
            .ifBlank { DEFAULT_REQUEST_UNIT }
    )
}

private suspend fun sendScannedCode(
    serverUrl: String,
    submitPath: String,
    deviceNo: String,
    deviceModel: String,
    employeeNo: String,
    scanCode: String,
    format: String,
    requestQty: BigDecimal,
    requestUnit: String
): OperationState {
    return try {
        val response = withContext(Dispatchers.IO) {
            MaterialPullApi(serverUrl).submitBarcodeScan(
                path = submitPath,
                scanCode = scanCode,
                format = format,
                requestQty = requestQty,
                requestUnit = requestUnit,
                deviceNo = deviceNo,
                deviceModel = deviceModel,
                employeeNo = employeeNo
            )
        }
        OperationState("发送成功", summarizeResponse(response))
    } catch (e: Exception) {
        Log.e("MaterialPullApp", "send scanned code failed: serverUrl=$serverUrl path=$submitPath deviceNo=$deviceNo employeeNo=$employeeNo format=$format scanCode=$scanCode requestQty=$requestQty requestUnit=$requestUnit", e)
        OperationState("发送失败", e.message ?: "网络请求失败", isError = true)
    }
}

private suspend fun testServer(
    serverUrl: String,
    deviceNo: String,
    deviceModel: String,
    employeeNo: String
): OperationState {
    return try {
        val api = MaterialPullApi(serverUrl)
        val message = withContext(Dispatchers.IO) { api.healthReady() }
        runCatching {
            withContext(Dispatchers.IO) {
                api.deviceLogin(
                    deviceNo = deviceNo,
                    deviceModel = deviceModel,
                    employeeNo = employeeNo,
                    remark = "test_connection"
                )
            }
        }
        OperationState("连接正常", message)
    } catch (e: Exception) {
        OperationState("连接失败", e.message ?: "服务器无响应", isError = true)
    }
}

private fun summarizeResponse(json: JSONObject): String {
    val labels = mapOf(
        "message" to "结果",
        "taskNo" to "任务号",
        "taskStatus" to "任务状态",
        "materialCode" to "物料编码",
        "requestQty" to "申请数量",
        "requestUnit" to "申请单位"
    )
    return labels.mapNotNull { (key, label) ->
        val value = json.optString(key)
        if (value.isBlank() || value == "null") null else "$label：$value"
    }.joinToString("\n").ifBlank { json.toString(2) }
}

private fun JSONObject.firstText(vararg keys: String): String {
    for (key in keys) {
        val value = opt(key)
        if (value != null && value != JSONObject.NULL) {
            val text = value.toString().trim()
            if (text.isNotBlank() && !text.equals("null", ignoreCase = true)) return text
        }
    }
    return ""
}

private data class OperationState(
    val title: String,
    val detail: String,
    val isError: Boolean = false
)

private data class AppUpdateUiState(
    val info: AppUpdateInfo? = null,
    val message: String = "点击检查更新",
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val progress: Int = 0,
    val downloadedApk: File? = null,
    val isError: Boolean = false
) {
    val isBusy: Boolean
        get() = isChecking || isDownloading
}

private data class ScanRecord(
    val value: String,
    val format: String
)

private data class ScanPreview(
    val scanCode: String,
    val format: String,
    val materialCode: String,
    val materialName: String,
    val warehouseCode: String,
    val stationAddress: String,
    val defaultQty: String,
    val defaultUnit: String
)

private const val DEFAULT_REQUEST_UNIT = "个"
