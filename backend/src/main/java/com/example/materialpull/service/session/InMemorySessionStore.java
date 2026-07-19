package com.example.materialpull.service.session;

import com.example.materialpull.service.AuthTokenService.SessionUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话存储（默认）。等价于改造前 AuthTokenService 内置的两个 ConcurrentHashMap。
 * 仅在单实例运行时使用；重启后会话丢失属于已知限制。
 */
@Component
@ConditionalOnProperty(prefix = "app.cluster", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemorySessionStore implements SessionStore {

    private final Map<String, SessionUser> sessions = new ConcurrentHashMap<>();
    private final Map<String, SessionUser> tickets = new ConcurrentHashMap<>();

    @Override
    public void saveSession(SessionUser session, Duration ttl) {
        sessions.put(session.token(), session);
        cleanupExpired();
    }

    @Override
    public Optional<SessionUser> getSession(String token) {
        return validateFrom(sessions, token, false);
    }

    @Override
    public void removeSession(String token) {
        if (token != null && !token.isBlank()) sessions.remove(token.trim());
    }

    @Override
    public void removeUserSessions(Long userId) {
        if (userId == null) return;
        sessions.entrySet().removeIf(e -> Objects.equals(e.getValue().userId(), userId));
    }

    @Override
    public void saveTicket(SessionUser ticketSession, Duration ttl) {
        tickets.put(ticketSession.token(), ticketSession);
        cleanupExpired();
    }

    @Override
    public Optional<SessionUser> consumeTicket(String ticket) {
        return validateFrom(tickets, ticket, true);
    }

    @Override
    public void removeUserTickets(Long userId) {
        if (userId == null) return;
        tickets.entrySet().removeIf(e -> Objects.equals(e.getValue().userId(), userId));
    }

    private Optional<SessionUser> validateFrom(Map<String, SessionUser> map, String token, boolean consume) {
        if (token == null || token.isBlank()) return Optional.empty();
        String key = token.trim();
        SessionUser session = consume ? map.remove(key) : map.get(key);
        if (session == null) return Optional.empty();
        if (session.expiresAt().isBefore(LocalDateTime.now())) {
            map.remove(key);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    private void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        sessions.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        tickets.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }
}
