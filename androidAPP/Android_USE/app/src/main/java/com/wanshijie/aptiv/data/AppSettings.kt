package com.wanshijie.aptiv.data

import android.content.Context
import android.os.Build

enum class NetworkMode { INTERNAL, EXTERNAL }

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("material_pull_app", Context.MODE_PRIVATE)

    var networkMode: NetworkMode
        get() = runCatching {
            NetworkMode.valueOf(prefs.getString(KEY_NETWORK_MODE, NetworkMode.INTERNAL.name)!!)
        }.getOrDefault(NetworkMode.INTERNAL)
        set(value) = prefs.edit().putString(KEY_NETWORK_MODE, value.name).apply()

    var serverBaseUrl: String
        get() = prefs.getString(KEY_SERVER_BASE_URL, DEFAULT_SERVER_BASE_URL) ?: DEFAULT_SERVER_BASE_URL
        set(value) = prefs.edit().putString(KEY_SERVER_BASE_URL, normalizeInternalBaseUrl(value)).apply()

    var externalBaseUrl: String
        get() = prefs.getString(KEY_EXTERNAL_BASE_URL, DEFAULT_EXTERNAL_BASE_URL) ?: DEFAULT_EXTERNAL_BASE_URL
        set(value) = prefs.edit().putString(KEY_EXTERNAL_BASE_URL, normalizeExternalBaseUrl(value)).apply()

    var relayDeviceToken: String
        get() = prefs.getString(KEY_RELAY_DEVICE_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RELAY_DEVICE_TOKEN, value.trim()).apply()

    var relayRegisteredDeviceNo: String
        get() = prefs.getString(KEY_RELAY_REGISTERED_DEVICE_NO, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RELAY_REGISTERED_DEVICE_NO, value.trim()).apply()

    var scanSubmitPath: String
        get() = prefs.getString(KEY_SCAN_SUBMIT_PATH, DEFAULT_SCAN_SUBMIT_PATH) ?: DEFAULT_SCAN_SUBMIT_PATH
        set(value) = prefs.edit().putString(KEY_SCAN_SUBMIT_PATH, normalizePath(value)).apply()

    var employeeNo: String
        get() = prefs.getString(KEY_EMPLOYEE_NO, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMPLOYEE_NO, value.trim()).apply()

    var deviceNo: String
        get() {
            val saved = prefs.getString(KEY_DEVICE_NO, "") ?: ""
            if (saved.isNotBlank()) return saved
            val generated = "APP-" + System.currentTimeMillis().toString(36).uppercase()
            prefs.edit().putString(KEY_DEVICE_NO, generated).apply()
            return generated
        }
        set(value) = prefs.edit().putString(KEY_DEVICE_NO, value.trim()).apply()

    val deviceModel: String
        get() = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifBlank { "UNKNOWN" }

    companion object {
        const val DEFAULT_SERVER_BASE_URL = "http://10.243.129.131/api"
        const val DEFAULT_EXTERNAL_BASE_URL = "https://tthih.top"
        const val DEFAULT_SCAN_SUBMIT_PATH = "/scan/empty"

        private const val KEY_NETWORK_MODE = "network_mode"
        private const val KEY_SERVER_BASE_URL = "server_base_url"
        private const val KEY_EXTERNAL_BASE_URL = "external_base_url"
        private const val KEY_RELAY_DEVICE_TOKEN = "relay_device_token"
        private const val KEY_RELAY_REGISTERED_DEVICE_NO = "relay_registered_device_no"
        private const val KEY_SCAN_SUBMIT_PATH = "scan_submit_path"
        private const val KEY_EMPLOYEE_NO = "employee_no"
        private const val KEY_DEVICE_NO = "device_no"

        fun normalizeInternalBaseUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            if (trimmed.isBlank()) return DEFAULT_SERVER_BASE_URL
            return if (trimmed.endsWith("/api", ignoreCase = true)) trimmed else "$trimmed/api"
        }

        fun normalizeExternalBaseUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            return if (trimmed.isBlank()) DEFAULT_EXTERNAL_BASE_URL else trimmed.removeSuffix("/api")
        }

        // 保留旧调用名，兼容内网代码。
        fun normalizeBaseUrl(value: String) = normalizeInternalBaseUrl(value)

        fun normalizePath(value: String): String {
            val trimmed = value.trim()
            if (trimmed.isBlank()) return DEFAULT_SCAN_SUBMIT_PATH
            return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        }

        fun actionFromPath(path: String): String = normalizePath(path).substringAfterLast('/').lowercase()
    }
}
