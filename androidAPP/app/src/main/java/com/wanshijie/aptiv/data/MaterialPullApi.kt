package com.wanshijie.aptiv.data

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

class MaterialPullApi(private val baseUrl: String) {
    fun healthReady(): String {
        val json = request(path = "/health/ready", method = "GET")
        return json.optString("message", "OK")
    }

    fun login(username: String, password: String): LoginSession {
        val body = JSONObject()
            .put("username", username.trim())
            .put("password", password)
        val json = request(path = "/auth/login", method = "POST", body = body)
        val data = json.optJSONObject("data") ?: JSONObject()
        return LoginSession(
            token = data.optString("token"),
            username = data.optString("username"),
            realName = data.optString("realName"),
            role = data.optString("role"),
            roleLabel = data.optString("roleLabel"),
            expiresAt = data.optString("expiresAt")
        )
    }

    fun submitBarcodeScan(
        token: String,
        path: String,
        scanCode: String,
        format: String,
        deviceNo: String
    ): JSONObject {
        val body = JSONObject()
            .put("scanCode", scanCode.trim())
            .put("format", format)
            .put("deviceNo", deviceNo.trim())
            .put("source", "android_app")
            .put("scannedAt", System.currentTimeMillis())
        val response = request(
            path = AppSettings.normalizePath(path),
            method = "POST",
            body = body,
            token = token,
            idempotencyKey = "APP-SCAN-${UUID.randomUUID()}"
        )
        return response.optJSONObject("data") ?: response
    }

    fun submitScan(token: String, request: ScanRequest): JSONObject {
        val path = when (request.mode) {
            ScanMode.EMPTY -> "/scan/empty"
            ScanMode.RECEIVE -> "/scan/receive"
            ScanMode.EXCEPTION -> "/scan/exception"
        }
        val body = JSONObject()
            .put("scanCode", request.scanCode.trim())
            .put("deviceNo", request.deviceNo.trim())
            .put("allowRepeat", request.allowRepeat)
        if (request.mode == ScanMode.RECEIVE) {
            body.put("taskNo", request.taskNo.trim())
            body.put("emptyContainerNo", request.emptyContainerNo.trim())
        }
        if (request.mode == ScanMode.EXCEPTION) {
            body.put("taskNo", request.taskNo.trim())
            body.put("exceptionType", request.exceptionType.trim())
            body.put("reason", request.reason.trim())
        }
        if (request.urgent) body.put("action", "URGENT")
        val response = request(
            path = path,
            method = "POST",
            body = body,
            token = token,
            idempotencyKey = "APP-IDEMP-${UUID.randomUUID()}"
        )
        return response.optJSONObject("data") ?: response
    }

    private fun request(
        path: String,
        method: String,
        body: JSONObject? = null,
        token: String = "",
        idempotencyKey: String = ""
    ): JSONObject {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Request-Id", "APP-${UUID.randomUUID()}")
            if (token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-Api-Key", token)
            }
            if (idempotencyKey.isNotBlank()) setRequestProperty("X-Idempotency-Key", idempotencyKey)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        if (body != null) {
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(body.toString()) }
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { input ->
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
        }.orEmpty()
        val json = if (text.isBlank()) JSONObject() else JSONObject(text)
        if (status !in 200..299 || !json.optBoolean("success", true)) {
            val message = json.optString("message", "服务器请求失败 HTTP $status")
            val requestId = json.optString("requestId")
            throw ApiException(if (requestId.isBlank()) message else "$message\n追踪号：$requestId")
        }
        return json
    }
}

data class LoginSession(
    val token: String,
    val username: String,
    val realName: String,
    val role: String,
    val roleLabel: String,
    val expiresAt: String
)

data class ScanRequest(
    val mode: ScanMode,
    val scanCode: String,
    val taskNo: String,
    val emptyContainerNo: String,
    val exceptionType: String,
    val reason: String,
    val deviceNo: String,
    val urgent: Boolean,
    val allowRepeat: Boolean
)

enum class ScanMode(val title: String) {
    EMPTY("用完拉动"),
    RECEIVE("收货确认"),
    EXCEPTION("异常上报")
}

class ApiException(message: String) : Exception(message)
