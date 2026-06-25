package com.wanshijie.aptiv.data

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("material_pull_app", Context.MODE_PRIVATE)

    var serverBaseUrl: String
        get() = prefs.getString(KEY_SERVER_BASE_URL, DEFAULT_SERVER_BASE_URL) ?: DEFAULT_SERVER_BASE_URL
        set(value) = prefs.edit().putString(KEY_SERVER_BASE_URL, normalizeBaseUrl(value)).apply()

    var scanSubmitPath: String
        get() = prefs.getString(KEY_SCAN_SUBMIT_PATH, DEFAULT_SCAN_SUBMIT_PATH) ?: DEFAULT_SCAN_SUBMIT_PATH
        set(value) = prefs.edit().putString(KEY_SCAN_SUBMIT_PATH, normalizePath(value)).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var realName: String
        get() = prefs.getString(KEY_REAL_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_REAL_NAME, value).apply()

    var role: String
        get() = prefs.getString(KEY_ROLE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ROLE, value).apply()

    var deviceNo: String
        get() {
            val saved = prefs.getString(KEY_DEVICE_NO, "") ?: ""
            if (saved.isNotBlank()) return saved
            val generated = "APP-" + System.currentTimeMillis().toString(36).uppercase()
            prefs.edit().putString(KEY_DEVICE_NO, generated).apply()
            return generated
        }
        set(value) = prefs.edit().putString(KEY_DEVICE_NO, value.trim()).apply()

    fun clearSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USERNAME)
            .remove(KEY_REAL_NAME)
            .remove(KEY_ROLE)
            .apply()
    }

    companion object {
        const val DEFAULT_SERVER_BASE_URL = "http://10.0.2.2:8080/api"
        const val DEFAULT_SCAN_SUBMIT_PATH = "/scan/empty"

        private const val KEY_SERVER_BASE_URL = "server_base_url"
        private const val KEY_SCAN_SUBMIT_PATH = "scan_submit_path"
        private const val KEY_TOKEN = "token"
        private const val KEY_USERNAME = "username"
        private const val KEY_REAL_NAME = "real_name"
        private const val KEY_ROLE = "role"
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
