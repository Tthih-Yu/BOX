package com.wanshijie.aptiv

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.wanshijie.aptiv.data.AppSettings
import com.wanshijie.aptiv.data.MaterialPullApi
import com.wanshijie.aptiv.ui.theme.APTIVTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.Executor
import java.util.concurrent.Executors

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
    var token by remember { mutableStateOf(settings.token) }
    var showSettings by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var lastScan by remember { mutableStateOf<ScanRecord?>(null) }
    var operation by remember { mutableStateOf(OperationState("待扫码", "点击大按钮扫描条形码或二维码")) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showScanner = true
        } else {
            operation = OperationState("无法打开相机", "未授予相机权限", isError = true)
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
            if (showScanner) {
                ScannerScreen(
                    onClose = { showScanner = false },
                    onDetected = { value, format ->
                        showScanner = false
                        lastScan = ScanRecord(value, format)
                        scope.launch {
                            isSending = true
                            operation = OperationState("发送中", value)
                            val normalizedUrl = AppSettings.normalizeBaseUrl(serverUrl)
                            val normalizedPath = AppSettings.normalizePath(submitPath)
                            settings.serverBaseUrl = normalizedUrl
                            settings.scanSubmitPath = normalizedPath
                            settings.deviceNo = deviceNo
                            settings.token = token
                            serverUrl = normalizedUrl
                            submitPath = normalizedPath
                            val result = sendScannedCode(
                                serverUrl = normalizedUrl,
                                submitPath = normalizedPath,
                                token = token,
                                deviceNo = deviceNo,
                                scanCode = value,
                                format = format
                            )
                            isSending = false
                            operation = result
                            snackbarHostState.showSnackbar(result.title)
                        }
                    }
                )
            } else {
                HomeScreen(
                    serverUrl = serverUrl,
                    submitPath = submitPath,
                    deviceNo = deviceNo,
                    token = token,
                    showSettings = showSettings,
                    isSending = isSending,
                    lastScan = lastScan,
                    operation = operation,
                    onScanClick = {
                        if (hasCameraPermission(context)) {
                            showScanner = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onToggleSettings = { showSettings = !showSettings },
                    onServerUrlChange = { serverUrl = it },
                    onSubmitPathChange = { submitPath = it },
                    onDeviceNoChange = { deviceNo = it },
                    onTokenChange = { token = it },
                    onSaveSettings = {
                        settings.serverBaseUrl = serverUrl
                        settings.scanSubmitPath = submitPath
                        settings.deviceNo = deviceNo
                        settings.token = token
                        serverUrl = settings.serverBaseUrl
                        submitPath = settings.scanSubmitPath
                        operation = OperationState("设置已保存", "${settings.serverBaseUrl}${settings.scanSubmitPath}")
                        scope.launch { snackbarHostState.showSnackbar("设置已保存") }
                    },
                    onHealthCheck = {
                        scope.launch {
                            isSending = true
                            val normalizedUrl = AppSettings.normalizeBaseUrl(serverUrl)
                            settings.serverBaseUrl = normalizedUrl
                            serverUrl = normalizedUrl
                            val result = testServer(normalizedUrl)
                            isSending = false
                            operation = result
                            snackbarHostState.showSnackbar(result.title)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    serverUrl: String,
    submitPath: String,
    deviceNo: String,
    token: String,
    showSettings: Boolean,
    isSending: Boolean,
    lastScan: ScanRecord?,
    operation: OperationState,
    onScanClick: () -> Unit,
    onToggleSettings: () -> Unit,
    onServerUrlChange: (String) -> Unit,
    onSubmitPathChange: (String) -> Unit,
    onDeviceNoChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onHealthCheck: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
        }

        Button(
            onClick = onScanClick,
            enabled = !isSending,
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = ButtonDefaults.ContentPadding
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 4.dp
                    )
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(56.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(if (isSending) "发送中" else "扫码", style = MaterialTheme.typography.titleLarge)
            }
        }

        if (showSettings) {
            SettingsPanel(
                serverUrl = serverUrl,
                submitPath = submitPath,
                deviceNo = deviceNo,
                token = token,
                operation = operation,
                lastScan = lastScan,
                isBusy = isSending,
                onServerUrlChange = onServerUrlChange,
                onSubmitPathChange = onSubmitPathChange,
                onDeviceNoChange = onDeviceNoChange,
                onTokenChange = onTokenChange,
                onSaveSettings = onSaveSettings,
                onHealthCheck = onHealthCheck
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    serverUrl: String,
    submitPath: String,
    deviceNo: String,
    token: String,
    operation: OperationState,
    lastScan: ScanRecord?,
    isBusy: Boolean,
    onServerUrlChange: (String) -> Unit,
    onSubmitPathChange: (String) -> Unit,
    onDeviceNoChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
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
                value = token,
                onValueChange = onTokenChange,
                label = "设备密钥 / Token"
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
private fun ScannerScreen(
    onClose: () -> Unit,
    onDetected: (String, String) -> Unit
) {
    var handled by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        BarcodeCameraPreview(
            onBarcodeDetected = { value, format ->
                if (!handled) {
                    handled = true
                    onDetected(value, format)
                }
            }
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(260.dp)
                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            color = Color.Black.copy(alpha = 0.58f),
            contentColor = Color.White,
            shape = CircleShape
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            color = Color.Black.copy(alpha = 0.58f),
            contentColor = Color.White,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "扫码中",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun BarcodeCameraPreview(
    onBarcodeDetected: (String, String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val analyzerExecutor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val scanner = BarcodeScanning.getClient()
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        analyzerExecutor,
                        BarcodeAnalyzer(
                            scanner = scanner,
                            callbackExecutor = mainExecutor,
                            onBarcodeDetected = onBarcodeDetected
                        )
                    )
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analyzer
            )
        }
        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
            scanner.close()
            analyzerExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

private class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val callbackExecutor: Executor,
    private val onBarcodeDetected: (String, String) -> Unit
) : ImageAnalysis.Analyzer {
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val barcode = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                if (barcode != null) {
                    callbackExecutor.execute {
                        onBarcodeDetected(barcode.rawValue.orEmpty(), barcode.formatName())
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
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

private suspend fun sendScannedCode(
    serverUrl: String,
    submitPath: String,
    token: String,
    deviceNo: String,
    scanCode: String,
    format: String
): OperationState {
    return try {
        val response = withContext(Dispatchers.IO) {
            MaterialPullApi(serverUrl).submitBarcodeScan(
                token = token,
                path = submitPath,
                scanCode = scanCode,
                format = format,
                deviceNo = deviceNo
            )
        }
        OperationState("发送成功", summarizeResponse(response))
    } catch (e: Exception) {
        Log.e("MaterialPullApp", "send scanned code failed: serverUrl=$serverUrl path=$submitPath deviceNo=$deviceNo format=$format scanCode=$scanCode", e)
        OperationState("发送失败", e.message ?: "网络请求失败", isError = true)
    }
}

private suspend fun testServer(serverUrl: String): OperationState {
    return try {
        val message = withContext(Dispatchers.IO) {
            MaterialPullApi(serverUrl).healthReady()
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
        "priority" to "优先级",
        "materialCode" to "物料编码",
        "materialName" to "物料名称",
        "warehouseCode" to "仓库代码",
        "warehouseAddress" to "仓库地址",
        "warehouseLocation" to "仓库库位",
        "sendStationAddress" to "发送工位地址",
        "deliveryAddress" to "配送地址",
        "currentBoxCode" to "盒号"
    )
    return labels.mapNotNull { (key, label) ->
        val value = json.optString(key)
        if (value.isBlank() || value == "null") null else "$label：$value"
    }.joinToString("\n").ifBlank { json.toString(2) }
}

private fun hasCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

private fun Barcode.formatName(): String {
    return when (format) {
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_CODE_93 -> "CODE_93"
        Barcode.FORMAT_CODABAR -> "CODABAR"
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        else -> "UNKNOWN"
    }
}

private data class OperationState(
    val title: String,
    val detail: String,
    val isError: Boolean = false
)

private data class ScanRecord(
    val value: String,
    val format: String
)
