package com.wanshijie.aptiv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.camera.CameraSettings
import com.wanshijie.aptiv.data.ApiException
import com.wanshijie.aptiv.data.AppSettings
import com.wanshijie.aptiv.data.AppUpdateInfo
import com.wanshijie.aptiv.data.AppUpdateManager
import com.wanshijie.aptiv.data.ExternalScanRelayApi
import com.wanshijie.aptiv.data.InstallLaunchResult
import com.wanshijie.aptiv.data.MaterialPullApi
import com.wanshijie.aptiv.data.NetworkMode
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
    var networkMode by remember { mutableStateOf(settings.networkMode) }
    var serverUrl by remember { mutableStateOf(settings.serverBaseUrl) }
    var externalUrl by remember { mutableStateOf(settings.externalBaseUrl) }
    var enrollmentKey by remember { mutableStateOf("") }
    var relayDeviceToken by remember { mutableStateOf(settings.relayDeviceToken) }
    var relayRegisteredDeviceNo by remember { mutableStateOf(settings.relayRegisteredDeviceNo) }
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
    var operation by remember { mutableStateOf(OperationState("待扫码", "点击扫码按钮，将条码或二维码放入摄像头预览框")) }
    var showCameraScanner by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf(AppUpdateUiState()) }
    val currentVersionName = remember {
        AppUpdateManager(context.applicationContext, settings.serverBaseUrl).currentVersionName()
    }

    // APP 打开后立即唤醒后端并预热 HTTP 连接，减少第一次扫码等待时间。
    LaunchedEffect(Unit) {
        val startedAt = SystemClock.elapsedRealtime()
        runCatching {
            withContext(Dispatchers.IO) {
                if (settings.networkMode == NetworkMode.EXTERNAL) {
                    ExternalScanRelayApi(settings.externalBaseUrl).health()
                } else {
                    MaterialPullApi(AppSettings.normalizeInternalBaseUrl(settings.serverBaseUrl)).healthReady()
                }
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
                val normalizedUrl = if (networkMode == NetworkMode.EXTERNAL) {
                    AppSettings.normalizeExternalBaseUrl(externalUrl)
                } else {
                    AppSettings.normalizeInternalBaseUrl(serverUrl)
                }
                val normalizedPath = AppSettings.normalizePath(submitPath)
                if (networkMode == NetworkMode.EXTERNAL) {
                    settings.externalBaseUrl = normalizedUrl
                    externalUrl = normalizedUrl
                } else {
                    settings.serverBaseUrl = normalizedUrl
                    serverUrl = normalizedUrl
                }
                settings.scanSubmitPath = normalizedPath
                settings.deviceNo = deviceNo
                settings.employeeNo = employeeNo
                submitPath = normalizedPath
                try {
                    val preview = fetchScanPreview(
                        serverUrl = normalizedUrl,
                        networkMode = networkMode,
                        relayDeviceToken = relayDeviceToken,
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
                    serverUrl = if (networkMode == NetworkMode.EXTERNAL) {
                        AppSettings.normalizeExternalBaseUrl(externalUrl)
                    } else {
                        AppSettings.normalizeInternalBaseUrl(serverUrl)
                    },
                    networkMode = networkMode,
                    relayDeviceToken = relayDeviceToken,
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
                networkMode = networkMode,
                serverUrl = serverUrl,
                externalUrl = externalUrl,
                enrollmentKey = enrollmentKey,
                isRelayRegistered = relayDeviceToken.isNotBlank() && relayRegisteredDeviceNo == deviceNo.trim(),
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
                    operation = OperationState("等待扫码", "请将条码或二维码放入后置摄像头预览框")
                    showCameraScanner = true
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
                onNetworkModeChange = { networkMode = it },
                onServerUrlChange = { serverUrl = it },
                onExternalUrlChange = { externalUrl = it },
                onEnrollmentKeyChange = { enrollmentKey = it },
                onSubmitPathChange = { submitPath = it },
                onDeviceNoChange = { deviceNo = it },
                onEmployeeNoChange = { employeeNo = it },
                onSaveSettings = {
                    settings.networkMode = networkMode
                    settings.serverBaseUrl = serverUrl
                    settings.externalBaseUrl = externalUrl
                    settings.scanSubmitPath = submitPath
                    settings.deviceNo = deviceNo
                    settings.employeeNo = employeeNo
                    serverUrl = settings.serverBaseUrl
                    externalUrl = settings.externalBaseUrl
                    submitPath = settings.scanSubmitPath
                    scope.launch {
                        isSending = true
                        try {
                            if (networkMode == NetworkMode.EXTERNAL) {
                                if (enrollmentKey.isNotBlank()) {
                                    relayDeviceToken = withContext(Dispatchers.IO) {
                                        ExternalScanRelayApi(settings.externalBaseUrl).registerDevice(
                                            deviceNo = settings.deviceNo,
                                            deviceModel = deviceModel,
                                            employeeNo = settings.employeeNo,
                                            enrollmentKey = enrollmentKey
                                        )
                                    }
                                    settings.relayDeviceToken = relayDeviceToken
                                    relayRegisteredDeviceNo = settings.deviceNo
                                    settings.relayRegisteredDeviceNo = relayRegisteredDeviceNo
                                    enrollmentKey = ""
                                }
                                if (relayDeviceToken.isBlank() || relayRegisteredDeviceNo != settings.deviceNo) {
                                    throw ApiException("当前设备编号尚未登记，请填写设备登记码后保存")
                                }
                                operation = OperationState("外网模式已就绪", settings.externalBaseUrl)
                            } else {
                                withContext(Dispatchers.IO) {
                                    MaterialPullApi(settings.serverBaseUrl).deviceLogin(
                                        deviceNo = settings.deviceNo,
                                        deviceModel = deviceModel,
                                        employeeNo = settings.employeeNo,
                                        remark = "save_settings"
                                    )
                                }
                                operation = OperationState("内网模式已就绪", "${settings.serverBaseUrl}${settings.scanSubmitPath}")
                            }
                            snackbarHostState.showSnackbar("设置已保存")
                        } catch (e: Exception) {
                            operation = OperationState("保存失败", e.message ?: "设备登记失败", isError = true)
                            snackbarHostState.showSnackbar("保存失败")
                        } finally {
                            isSending = false
                        }
                    }
                },
                onHealthCheck = {
                    scope.launch {
                        isSending = true
                        val normalizedUrl = if (networkMode == NetworkMode.EXTERNAL) {
                            AppSettings.normalizeExternalBaseUrl(externalUrl)
                        } else {
                            AppSettings.normalizeInternalBaseUrl(serverUrl)
                        }
                        val result = testServer(
                            serverUrl = normalizedUrl,
                            networkMode = networkMode,
                            deviceNo = deviceNo,
                            deviceModel = deviceModel,
                            employeeNo = employeeNo
                        )
                        if (networkMode == NetworkMode.EXTERNAL) externalUrl = normalizedUrl else serverUrl = normalizedUrl
                        isSending = false
                        operation = result
                        snackbarHostState.showSnackbar(result.title)
                    }
                }
            )
        }
    }

    if (showCameraScanner) {
        CameraScannerDialog(
            onBarcodeDetected = { value, format ->
                showCameraScanner = false
                handleScan(value, format)
            },
            onDismiss = {
                showCameraScanner = false
                operation = OperationState("已取消扫码", "点击扫码按钮可重新打开摄像头")
            }
        )
    }
}

@Composable
private fun CameraScannerDialog(
    onBarcodeDetected: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val latestOnBarcodeDetected by rememberUpdatedState(onBarcodeDetected)
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
    }
    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("手机摄像头扫码", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("使用后置摄像头，请将条码或二维码完整放入预览框内")
                if (cameraGranted) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        factory = { viewContext ->
                            DecoratedBarcodeView(viewContext).apply {
                                barcodeView.cameraSettings = CameraSettings().apply {
                                    // -1 由ZXing选择系统默认后置摄像头，避免不同品牌的后摄ID不一定为0。
                                    requestedCameraId = -1
                                    isAutoFocusEnabled = true
                                }
                                decodeContinuous(object : BarcodeCallback {
                                    private var delivered = false
                                    override fun barcodeResult(result: BarcodeResult?) {
                                        val value = result?.text?.trim().orEmpty()
                                        if (delivered || value.isBlank()) return
                                        delivered = true
                                        pause()
                                        latestOnBarcodeDetected(value, result?.barcodeFormat?.name ?: "UNKNOWN")
                                    }
                                    override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
                                })
                                resume()
                            }
                        },
                        onRelease = { it.pause() }
                    )
                } else {
                    Text("需要摄像头权限才能扫码，请允许权限后重试。", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("授予摄像头权限") }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("取消") }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    networkMode: NetworkMode,
    serverUrl: String,
    externalUrl: String,
    enrollmentKey: String,
    isRelayRegistered: Boolean,
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
    onNetworkModeChange: (NetworkMode) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onExternalUrlChange: (String) -> Unit,
    onEnrollmentKeyChange: (String) -> Unit,
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
                networkMode = networkMode,
                serverUrl = serverUrl,
                externalUrl = externalUrl,
                enrollmentKey = enrollmentKey,
                isRelayRegistered = isRelayRegistered,
                submitPath = submitPath,
                deviceNo = deviceNo,
                deviceModel = deviceModel,
                employeeNo = employeeNo,
                operation = operation,
                lastScan = lastScan,
                isBusy = isSending,
                onNetworkModeChange = onNetworkModeChange,
                onServerUrlChange = onServerUrlChange,
                onExternalUrlChange = onExternalUrlChange,
                onEnrollmentKeyChange = onEnrollmentKeyChange,
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
    networkMode: NetworkMode,
    serverUrl: String,
    externalUrl: String,
    enrollmentKey: String,
    isRelayRegistered: Boolean,
    submitPath: String,
    deviceNo: String,
    deviceModel: String,
    employeeNo: String,
    operation: OperationState,
    lastScan: ScanRecord?,
    isBusy: Boolean,
    onNetworkModeChange: (NetworkMode) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onExternalUrlChange: (String) -> Unit,
    onEnrollmentKeyChange: (String) -> Unit,
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (networkMode == NetworkMode.INTERNAL) {
                    Button(onClick = { }, modifier = Modifier.weight(1f)) { Text("公司内网") }
                    OutlinedButton(onClick = { onNetworkModeChange(NetworkMode.EXTERNAL) }, modifier = Modifier.weight(1f)) { Text("手机外网") }
                } else {
                    OutlinedButton(onClick = { onNetworkModeChange(NetworkMode.INTERNAL) }, modifier = Modifier.weight(1f)) { Text("公司内网") }
                    Button(onClick = { }, modifier = Modifier.weight(1f)) { Text("手机外网") }
                }
            }
            if (networkMode == NetworkMode.INTERNAL) {
                AppTextField(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    label = "内网服务器地址",
                    keyboardType = KeyboardType.Uri
                )
            } else {
                AppTextField(
                    value = externalUrl,
                    onValueChange = onExternalUrlChange,
                    label = "外网中继地址",
                    keyboardType = KeyboardType.Uri
                )
                AppTextField(
                    value = enrollmentKey,
                    onValueChange = onEnrollmentKeyChange,
                    label = if (isRelayRegistered) "设备登记码（已登记，留空即可）" else "设备登记码（首次必填）"
                )
                Text(
                    if (isRelayRegistered) "设备状态：已登记" else "设备状态：未登记",
                    color = if (isRelayRegistered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            AppTextField(
                value = submitPath,
                onValueChange = onSubmitPathChange,
                label = if (networkMode == NetworkMode.EXTERNAL) "业务动作路径（默认 /scan/empty）" else "提交路径"
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

private suspend fun fetchScanPreview(
    serverUrl: String,
    networkMode: NetworkMode,
    relayDeviceToken: String,
    deviceNo: String,
    deviceModel: String,
    employeeNo: String,
    scanCode: String,
    format: String
): ScanPreview {
    val startedAt = SystemClock.elapsedRealtime()
    val response = withContext(Dispatchers.IO) {
        if (networkMode == NetworkMode.EXTERNAL) {
            val result = ExternalScanRelayApi(serverUrl).executeScan(
                action = "preview",
                scanCode = scanCode,
                format = format,
                requestQty = null,
                requestUnit = null,
                deviceNo = deviceNo,
                deviceToken = relayDeviceToken
            )
            result.optJSONObject("data") ?: result
        } else {
            MaterialPullApi(serverUrl).previewBarcodeScan(
                scanCode = scanCode,
                format = format,
                deviceNo = deviceNo,
                deviceModel = deviceModel,
                employeeNo = employeeNo
            )
        }
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
    networkMode: NetworkMode,
    relayDeviceToken: String,
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
            if (networkMode == NetworkMode.EXTERNAL) {
                val action = AppSettings.actionFromPath(submitPath)
                if (action !in setOf("empty", "receive", "exception")) {
                    throw ApiException("外网模式不支持业务动作：$action")
                }
                val result = ExternalScanRelayApi(serverUrl).executeScan(
                    action = action,
                    scanCode = scanCode,
                    format = format,
                    requestQty = requestQty,
                    requestUnit = requestUnit,
                    deviceNo = deviceNo,
                    deviceToken = relayDeviceToken
                )
                result.optJSONObject("data") ?: result
            } else {
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
        }
        OperationState("发送成功", summarizeResponse(response))
    } catch (e: Exception) {
        Log.e("MaterialPullApp", "send scanned code failed: serverUrl=$serverUrl path=$submitPath deviceNo=$deviceNo employeeNo=$employeeNo format=$format scanCode=$scanCode requestQty=$requestQty requestUnit=$requestUnit", e)
        OperationState("发送失败", e.message ?: "网络请求失败", isError = true)
    }
}

private suspend fun testServer(
    serverUrl: String,
    networkMode: NetworkMode,
    deviceNo: String,
    deviceModel: String,
    employeeNo: String
): OperationState {
    return try {
        if (networkMode == NetworkMode.EXTERNAL) {
            val message = withContext(Dispatchers.IO) { ExternalScanRelayApi(serverUrl).health() }
            OperationState("外网连接正常", "$message · $serverUrl")
        } else {
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
            OperationState("内网连接正常", message)
        }
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
