package com.example.materialpull.common;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * 简单的 IP / CIDR 白名单匹配工具。支持 IPv4 与 IPv6 的 CIDR 前缀匹配，
 * 也支持单个 IP（等价 /32 或 /128）。用于扫码设备来源 IP 限制。
 */
public final class IpMatcher {

    private IpMatcher() {}

    /** clientIp 是否命中白名单任一条目。白名单为空时返回 true（表示不限制）。 */
    public static boolean matchesAny(String clientIp, List<String> cidrs) {
        if (cidrs == null || cidrs.isEmpty()) return true;
        if (clientIp == null || clientIp.isBlank()) return false;
        for (String cidr : cidrs) {
            if (cidr == null || cidr.isBlank()) continue;
            if (matches(clientIp.trim(), cidr.trim())) return true;
        }
        return false;
    }

    private static boolean matches(String clientIp, String cidr) {
        try {
            String ipPart = cidr;
            int prefix = -1;
            int slash = cidr.indexOf('/');
            if (slash >= 0) {
                ipPart = cidr.substring(0, slash);
                prefix = Integer.parseInt(cidr.substring(slash + 1));
            }
            byte[] target = InetAddress.getByName(clientIp).getAddress();
            byte[] base = InetAddress.getByName(ipPart).getAddress();
            if (target.length != base.length) return false;
            if (prefix < 0) prefix = base.length * 8;

            int fullBytes = prefix / 8;
            int remainingBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (target[i] != base[i]) return false;
            }
            if (remainingBits > 0) {
                int mask = 0xFF << (8 - remainingBits) & 0xFF;
                return (target[fullBytes] & mask) == (base[fullBytes] & mask);
            }
            return true;
        } catch (UnknownHostException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }
}
