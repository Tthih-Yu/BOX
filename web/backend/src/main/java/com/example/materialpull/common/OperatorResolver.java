package com.example.materialpull.common;

/**
 * 统一解析审计操作人。
 * 页面提交的 operator 字段不再作为可信身份来源，避免前端伪造审计人。
 */
public final class OperatorResolver {
    private OperatorResolver() {}

    public static String currentOperator() {
        return firstNonBlank(RequestContext.getRealName(), RequestContext.getUsername(), systemOperator());
    }

    public static String systemOperator() {
        return "系统用户";
    }

    public static String externalOperator() {
        return "外部系统";
    }

    public static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }
}
