package com.example.materialpull.config;

import com.example.materialpull.common.SecurityProperties;
import com.example.materialpull.security.RoleInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final RoleInterceptor roleInterceptor;
    private final SecurityProperties securityProperties;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(splitOrigins())
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "X-Auth-Token", "X-Request-Id", "X-Idempotency-Key", "X-Api-Key", "Content-Type")
                .exposedHeaders("X-Request-Id")
                .allowCredentials(false)
                .maxAge(1800);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleInterceptor).addPathPatterns("/**");
    }

    private String[] splitOrigins() {
        String[] origins = allowedOrigins == null || allowedOrigins.isBlank()
                ? new String[]{"http://localhost:5173", "http://127.0.0.1:5173"}
                : allowedOrigins.split("\\s*,\\s*");
        if (!securityProperties.isDevToolsEnabled() && Arrays.stream(origins).anyMatch("*"::equals)) {
            throw new IllegalStateException("生产环境禁止 app.cors.allowed-origins 使用 *");
        }
        return origins;
    }
}
