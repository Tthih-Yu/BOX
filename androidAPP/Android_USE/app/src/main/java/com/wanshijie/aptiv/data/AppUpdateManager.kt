package com.wanshijie.aptiv.data

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val notes: String
)

enum class InstallLaunchResult {
    STARTED,
    NEEDS_PERMISSION
}

class AppUpdateManager(
    private val context: Context,
    serverBaseUrl: String
) {
    private val webBaseUrl = serverBaseUrl.trim().trimEnd('/').removeSuffix("/api")

    fun currentVersionCode(): Long {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }

    fun currentVersionName(): String {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.versionName ?: "未知"
    }

    fun checkForUpdate(): AppUpdateInfo {
        val separator = if (UPDATE_MANIFEST_PATH.contains('?')) '&' else '?'
        val url = URL(webBaseUrl + UPDATE_MANIFEST_PATH + separator + "t=" + System.currentTimeMillis())
        val connection = openGet(url)
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
            if (status !in 200..299) throw ApiException("检查更新失败 HTTP $status")
            val json = JSONObject(text)
            val versionCode = json.optLong("versionCode", 0L)
            val versionName = json.optString("versionName").trim()
            val apkUrl = json.optString("apkUrl").trim()
            val sha256 = json.optString("sha256").trim().uppercase()
            if (versionCode <= 0 || versionName.isBlank() || apkUrl.isBlank()) {
                throw ApiException("服务器更新信息不完整")
            }
            if (!sha256.matches(Regex("[0-9A-F]{64}"))) {
                throw ApiException("服务器更新信息缺少有效的 SHA-256")
            }
            return AppUpdateInfo(
                versionCode = versionCode,
                versionName = versionName,
                apkUrl = apkUrl,
                sha256 = sha256,
                size = json.optLong("size", 0L),
                notes = json.optString("notes").trim()
            )
        } finally {
            connection.disconnect()
        }
    }

    fun downloadUpdate(info: AppUpdateInfo, onProgress: (Int) -> Unit): File {
        val url = resolveApkUrl(info.apkUrl)
        val connection = openGet(url).apply { readTimeout = 30_000 }
        val updateDir = File(context.filesDir, UPDATE_DIRECTORY).apply { mkdirs() }
        val partial = File(updateDir, "aptiv-${info.versionCode}.apk.part")
        val completed = File(updateDir, "aptiv-${info.versionCode}.apk")
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw ApiException("下载更新失败 HTTP $status")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.size
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastProgress = -1
            BufferedInputStream(connection.inputStream).use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        if (total > 0) {
                            val progress = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02X".format(it) }
            if (!actualHash.equals(info.sha256, ignoreCase = true)) {
                partial.delete()
                throw ApiException("APK 校验失败，文件可能不完整，请重新下载")
            }
            if (info.size > 0 && downloaded != info.size) {
                partial.delete()
                throw ApiException("APK 大小不一致，请重新下载")
            }
            if (completed.exists() && !completed.delete()) throw ApiException("无法替换旧的更新文件")
            if (!partial.renameTo(completed)) throw ApiException("无法保存下载完成的 APK")
            onProgress(100)
            return completed
        } finally {
            connection.disconnect()
        }
    }

    fun launchInstaller(apkFile: File): InstallLaunchResult {
        if (!apkFile.isFile) throw ApiException("未找到已下载的 APK")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return InstallLaunchResult.NEEDS_PERMISSION
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            clipData = ClipData.newRawUri("APTIV update", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(installIntent)
        } catch (e: ActivityNotFoundException) {
            throw ApiException("设备上没有可用的 APK 安装器")
        }
        return InstallLaunchResult.STARTED
    }

    private fun resolveApkUrl(apkUrl: String): URL {
        if (apkUrl.startsWith("http://", true) || apkUrl.startsWith("https://", true)) return URL(apkUrl)
        return URL(URL(webBaseUrl.trimEnd('/') + "/"), apkUrl.removePrefix("/"))
    }

    private fun openGet(url: URL): HttpURLConnection {
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 10_000
            useCaches = false
            setRequestProperty("Accept", "application/json, application/vnd.android.package-archive, */*")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Connection", "keep-alive")
        }
    }

    private companion object {
        const val UPDATE_MANIFEST_PATH = "/updates/android/latest.json"
        const val UPDATE_DIRECTORY = "updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
