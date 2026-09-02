package com.example.materialpull.service.realtime;

import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.concurrent.*;

@Component
public class WebSocketConnectionRegistry {
    private final ConcurrentMap<Long, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
    private final Set<String> invalidSessions = ConcurrentHashMap.newKeySet();

    public void register(Long userId, String sessionId) {
        if (userId == null || sessionId == null) return;
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }
    public void remove(Long userId, String sessionId) {
        if (sessionId == null) return;
        invalidSessions.remove(sessionId);
        if (userId == null) return;
        Set<String> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) sessionsByUser.remove(userId, sessions);
        }
    }
    public void invalidateUser(Long userId) {
        if (userId == null) return;
        Set<String> sessions = sessionsByUser.remove(userId);
        if (sessions != null) invalidSessions.addAll(sessions);
    }
    public boolean invalid(String sessionId) { return sessionId != null && invalidSessions.contains(sessionId); }
}
