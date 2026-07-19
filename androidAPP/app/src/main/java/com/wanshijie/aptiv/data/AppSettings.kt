package com.wanshijie.aptiv.data

import android.content.Context
import android.os.Build

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("material_pull_app", Context.MODE_PRIVATE)

    var serverBaseUrl: String
        get() = prefs.getString(KEY_SERVER_BASE_URL, DEFAULT_SERVER_BASE_URL) ?: DEFAULT_SERVER_BASE_URL
        set(value) = prefs.edit().putString(KEY_SERVER_BASE_URL, normalizeBaseUrl(value)).apply()

    var scanSubmitPath: String
        get() = prefs.getString(KEY_SCAN_SUBMIT_PATH, DEFAULT_SCAN_SUBMIT_PATH) ?: DEFAULT_SCAN_SUBMIT_PATH
        set(value) = prefs.edit().putString(KEY_SCAN_SUBMIT_PATH, normalizePath(value)).apply()

    /**
     * 员工工号。只做展示和日志记录，不参与任何鉴权或业务校验。
     */
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
        const val DEFAULT_SERVER_BASE_URL = "http://10.0.2.2:8080/api"
        const val DEFAULT_SCAN_SUBMIT_PATH = "/scan/empty"

        private const val KEY_SERVER_BASE_URL = "server_base_url"
        private const val KEY_SCAN_SUBMIT_PATH = "scan_submit_path"
        private const val KEY_EMPLOYEE_NO = "employee_no"
        private const val KEY_DEVICE_NO = "device_no"

        fun normalizeBaseUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            if (trimmed.isBlank()) return DEFAULT_SERVER_BASE_URL
            return if (trimmed.endsWith("/api", ignoreCase = true)) trimmed else "$trimmed/api"
        }

        fun normalizePath(value: String): String {
            val trimmed = value.trim()
            if (trimmed.isBlank()) return DEFAULT_SCAN_SUBMIT_PATH
            return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        }
    }
}
