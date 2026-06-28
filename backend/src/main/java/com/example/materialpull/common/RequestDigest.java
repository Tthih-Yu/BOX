package com.example.materialpull.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.StringJoiner;

public final class RequestDigest {
    private RequestDigest() {}

    public static String sha256(String... parts) {
        try {
            StringJoiner joiner = new StringJoiner("\u001f");
            if (parts != null) {
                for (String part : parts) joiner.add(part == null ? "" : part.trim());
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(joiner.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算请求摘要", e);
        }
    }

    public static String valueOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
