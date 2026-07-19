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

    /**
     * 安卓端"登记"接口：不做任何鉴权，仅把设备号、设备型号、员工工号写到 device_credential 表里。
     */
    fun deviceLogin(
        deviceNo: String,
        deviceModel: String,
        employeeNo: String,
        remark: String? = null
    ): JSONObject {
        val body = JSONObject()
            .put("deviceNo", deviceNo.trim())
            .put("deviceModel", deviceModel.trim())
            .put("employeeNo", employeeNo.trim())
            .put("source", "android_app")
            .put("remark", remark ?: "")
        val response = request(
            path = "/device/login",
            method = "POST",
            body = body,
            deviceNo = deviceNo,
            deviceModel = deviceModel,
            employeeNo = employeeNo
        )
        return response.optJSONObject("data") ?: response
    }

    fun submitBarcodeScan(
        path: String,
        scanCode: String,
        format: String,
        deviceNo: String,
        deviceModel: String,
        employeeNo: String
    ): JSONObject {
        val body = JSONObject()
            .put("scanCode", scanCode.trim())
            .put("format", format)
            .put("deviceNo", deviceNo.trim())
            .put("source", "android_app")
            .put("scannedAt", System.currentTimeMillis())
            .put("employeeNo", employeeNo.trim())
        val response = request(
            path = AppSettings.normalizePath(path),
            method = "POST",
            body = body,
            deviceNo = deviceNo,
            deviceModel = deviceModel,
            employeeNo = employeeNo,
            idempotencyKey = "APP-SCAN-${UUID.randomUUID()}"
        )
        return response.optJSONObject("data") ?: response
    }

    private fun request(
        path: String,
        method: String,
        body: JSONObject? = null,
        deviceNo: String = "",
        deviceModel: String = "",
        employeeNo: String = "",
        idempotencyKey: String = ""
    ): JSONObject {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Request-Id", "APP-${UUID.randomUUID()}")
            if (deviceNo.isNotBlank()) setRequestProperty("X-Device-No", deviceNo.trim())
            if (deviceModel.isNotBlank()) setRequestProperty("X-Device-Model", deviceModel.trim())
            if (employeeNo.isNotBlank()) setRequestProperty("X-Employee-No", employeeNo.trim())
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

class ApiException(message: String) : Exception(message)
