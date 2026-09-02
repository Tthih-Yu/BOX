package com.example.materialpull.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防止新增 HTTP 接口仅依赖“已登录”而遗漏角色授权。
 * 认证辅助接口和公开健康检查必须逐项进入明确白名单。
 */
class ControllerRoleCoverageTest {
    private static final Set<String> AUTH_ENDPOINT_EXCEPTIONS = Set.of(
            "AuthController#login",
            "AuthController#logout",
            "AuthController#websocketTicket",
            "DeviceController#login",
            "HealthController#ready"
    );

    @Test
    void everyBusinessEndpointDeclaresRolePolicyOrExplicitException() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<String> missing = new ArrayList<>();
        for (var bean : scanner.findCandidateComponents("com.example.materialpull.controller")) {
            Class<?> type = Class.forName(bean.getBeanClassName());
            boolean classGuarded = AnnotatedElementUtils.hasAnnotation(type, RequireRoles.class);
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                String endpoint = type.getSimpleName() + "#" + method.getName();
                if (!classGuarded
                        && !AnnotatedElementUtils.hasAnnotation(method, RequireRoles.class)
                        && !AUTH_ENDPOINT_EXCEPTIONS.contains(endpoint)) {
                    missing.add(endpoint);
                }
            }
        }

        assertTrue(missing.isEmpty(), "接口缺少 @RequireRoles 或明确白名单: " + missing);
    }
}
