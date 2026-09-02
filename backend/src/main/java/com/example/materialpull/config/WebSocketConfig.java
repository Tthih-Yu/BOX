package com.example.materialpull.config;

import com.example.materialpull.common.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final SecurityProperties securityProperties;
    private final WebSocketScopeInterceptor scopeInterceptor;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(scopeInterceptor)
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override protected java.security.Principal determineUser(
                            org.springframework.http.server.ServerHttpRequest request,
                            org.springframework.web.socket.WebSocketHandler wsHandler,
                            java.util.Map<String, Object> attributes) {
                        return scopeInterceptor.principal(attributes);
                    }
                })
                .setAllowedOriginPatterns(splitOrigins());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(scopeInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(scopeInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
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
