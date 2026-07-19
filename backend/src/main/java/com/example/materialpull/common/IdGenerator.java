package com.example.materialpull.common;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class IdGenerator {
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withLocale(Locale.ROOT).withZone(java.time.ZoneId.systemDefault());
    private static final AtomicLong SEQ = new AtomicLong();
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String id(String prefix) {
        long seq = SEQ.updateAndGet(v -> (v + 1) & 0xFFFFF);
        int rand = RANDOM.nextInt(0x100000);
        return sanitize(prefix) + "-" + F.format(Instant.now()) + "-" + base36(seq, 4) + base36(rand, 4);
    }

    private static String sanitize(String prefix) {
        if (prefix == null || prefix.isBlank()) return "ID";
        return prefix.trim().replaceAll("[^A-Za-z0-9_-]", "").toUpperCase(Locale.ROOT);
    }

    private static String base36(long value, int minWidth) {
        String s = Long.toString(value, 36).toUpperCase(Locale.ROOT);
        if (s.length() >= minWidth) return s;
        return "0".repeat(minWidth - s.length()) + s;
    }
}
