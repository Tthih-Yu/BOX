package com.example.materialpull.service;

import com.example.materialpull.common.*;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class ExternalHttpClient {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(5000))
            .build();

    public String postJson(String url, String json, String authHeader, String apiKey, long timeoutMs, String systemName) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, systemName + "接口地址未配置，禁止按本地假成功继续执行");
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofMillis(timeoutMs <= 0 ? 5000 : timeoutMs))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .header("X-Request-Id", firstNonBlank(RequestContext.getTraceId(), IdGenerator.id("REQ")))
                    .POST(HttpRequest.BodyPublishers.ofString(json == null ? "{}" : json, StandardCharsets.UTF_8));
            if (authHeader != null && !authHeader.isBlank() && apiKey != null && !apiKey.isBlank()) {
                builder.header(authHeader.trim(), apiKey.trim());
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.CIRCUIT_OPEN, systemName + "接口返回异常：HTTP " + response.statusCode() + "，" + abbreviate(response.body(), 300));
            }
            return response.body() == null ? "" : response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.CIRCUIT_OPEN, systemName + "接口调用失败：" + e.getMessage());
        }
    }

    private String abbreviate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }
}
