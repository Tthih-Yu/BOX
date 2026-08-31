package com.wanshijie.aptiv.data

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

class ExternalScanRelayApi(baseUrl: String) {
    private val baseUrl = AppSettings.normalizeExternalBaseUrl(baseUrl)

    fun health(): String {
        val response = request("GET", "/health")
        return response.optString("status", "UP")
    }

    fun registerDevice(deviceNo: String, deviceModel: String, employeeNo: String, enrollmentKey: String): String {
        if (enrollmentKey.isBlank()) throw ApiException("请输入管理员提供的设备登记码")
        val response = request(
            method = "POST",
            path = "/app/v1/device/register",
            body = JSONObject()
                .put("deviceNo", deviceNo.trim())
                .put("deviceModel", deviceModel.trim())
                .put("employeeNo", employeeNo.trim()),
            headers = mapOf("X-Enrollment-Key" to enrollmentKey.trim())
        )
        return response.optJSONObject("data")?.optString("deviceToken").orEmpty()
            .ifBlank { throw ApiException("云端未返回设备凭证") }
    }

    fun executeScan(
        action: String,
        scanCode: String,
        format: String,
        requestQty: BigDecimal?,
        requestUnit: String?,
        deviceNo: String,
        deviceToken: String
    ): JSONObject {
        if (deviceToken.isBlank()) throw ApiException("设备尚未登记，请在设置中填写登记码并保存")
        val requestId = "APP-SCAN-${UUID.randomUUID()}"
        val body = JSONObject()
            .put("requestId", requestId)
            .put("action", action.lowercase())
            .put("scanCode", scanCode.trim())
            .put("format", format)
        if (requestQty != null) body.put("requestQty", requestQty)
        if (!requestUnit.isNullOrBlank()) body.put("requestUnit", requestUnit.trim())

        val headers = mapOf(
            "Authorization" to "Bearer ${deviceToken.trim()}",
            "X-Device-No" to deviceNo.trim()
        )
        request("POST", "/app/v1/commands", body, headers)

        val deadline = System.currentTimeMillis() + COMMAND_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val command = request("GET", "/app/v1/commands/$requestId", headers = headers)
                .optJSONObject("data") ?: throw ApiException("云端未返回指令状态")
            when (command.optString("status")) {
                "SUCCEEDED" -> return command.optJSONObject("result")
                    ?: throw ApiException("任务成功但没有返回结果")
                "FAILED" -> throw ApiException(command.optString("error", "内网处理失败"))
                "EXPIRED" -> throw ApiException("任务等待超时，请检查公司网络")
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        throw ApiException("等待内网处理超时，请稍后重试")
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        headers: Map<String, String> = emptyMap()
    ): JSONObject {
        val bytes = body?.toString()?.toByteArray(StandardCharsets.UTF_8)
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 6_000
            readTimeout = 12_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Request-Id", "ANDROID-${UUID.randomUUID()}")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (bytes != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setFixedLengthStreamingMode(bytes.size)
            }
        }
        try {
            if (bytes != null) connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299 || !json.optBoolean("success", true)) {
                val message = json.optString("message").ifBlank {
                    json.optString("error", "服务器请求失败 HTTP $status")
                }
                throw ApiException(message)
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val COMMAND_TIMEOUT_MS = 60_000L
        const val POLL_INTERVAL_MS = 800L
    }
}
