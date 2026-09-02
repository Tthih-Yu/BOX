package com.example.materialpull.config;

import com.example.materialpull.security.ScopePrincipal;
import com.example.materialpull.service.realtime.WebSocketConnectionRegistry;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WebSocketScopeInterceptor implements HandshakeInterceptor, ChannelInterceptor {
    private final WebSocketConnectionRegistry connections;
    public WebSocketScopeInterceptor(WebSocketConnectionRegistry connections) { this.connections = connections; }
    public static String encoded(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servlet)) return false;
        var http = servlet.getServletRequest();
        attributes.put("userId", http.getAttribute("loginUserId"));
        attributes.put("username", http.getAttribute("loginUser"));
        attributes.put("role", http.getAttribute("loginRole"));
        attributes.put("factory", http.getAttribute("loginFactory"));
        attributes.put("areas", http.getAttribute("loginDeliveryAreas"));
        return http.getAttribute("loginUser") != null;
    }

    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                         WebSocketHandler handler, Exception exception) {}

    public Principal principal(Map<String, Object> attributes) {
        String username = (String) attributes.get("username");
        String role = (String) attributes.get("role");
        if (username == null || role == null) return null;
        @SuppressWarnings("unchecked") List<String> areas = (List<String>) attributes.getOrDefault("areas", List.of());
        return new ScopePrincipal((Long) attributes.get("userId"), username,
                com.example.materialpull.enums.UserRole.valueOf(role), (String) attributes.get("factory"), areas);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT && !(accessor.getUser() instanceof ScopePrincipal)) {
            throw new MessageDeliveryException("WebSocket connection is not authenticated");
        }
        if (command == StompCommand.SUBSCRIBE) validateDestination(accessor);
        if (command == StompCommand.CONNECT && accessor.getUser() instanceof ScopePrincipal user) {
            connections.register(user.userId(), accessor.getSessionId());
        }
        if (command == StompCommand.DISCONNECT && accessor.getUser() instanceof ScopePrincipal user) {
            connections.remove(user.userId(), accessor.getSessionId());
        }
        if (connections.invalid(accessor.getSessionId())) return null;
        return message;
    }

    private void validateDestination(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof ScopePrincipal user)) throw new MessageDeliveryException("Unauthenticated subscription");
        String destination = accessor.getDestination();
        if (destination == null || !allowed(user).contains(destination)) {
            throw new MessageDeliveryException("Unauthorized subscription destination");
        }
    }

    private Set<String> allowed(ScopePrincipal user) {
        Set<String> result = new HashSet<>();
        for (String topic : List.of("tasks", "taskWarnings", "boxes", "boxPool", "agvJobs", "printJobs", "plans", "purchases")) {
            String base = "/topic/" + topic;
            if (user.globalAdmin()) result.add(base + "/@global");
            else if (user.factory() != null) {
                result.add(base + "/@factory/" + encoded(user.factory()));
                for (String area : user.deliveryAreas()) result.add(base + "/@area/" + encoded(user.factory()) + "/" + encoded(area));
            }
        }
        return result;
    }
}
