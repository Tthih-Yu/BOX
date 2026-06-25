package com.example.materialpull.service;

import com.example.materialpull.common.SecurityProperties;
import com.example.materialpull.entity.UserEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthTokenService {
    private final SecurityProperties securityProperties;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, SessionUser> sessions = new ConcurrentHashMap<>();
    private final Map<String, SessionUser> websocketTickets = new ConcurrentHashMap<>();

    public SessionUser issue(UserEntity user) {
        String token = randomToken(48);
        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = issuedAt.plusMinutes(Math.max(5, securityProperties.getSessionTtlMinutes()));
        SessionUser session = new SessionUser(token, user.getId(), user.getUsername(), user.getRealName(), user.getRole(), issuedAt, user.getPasswordUpdatedAt(), expiresAt);
        sessions.put(token, session);
        cleanupExpired();
        return session;
    }

    public Optional<SessionUser> validate(String token) {
        Optional<SessionUser> session = validateFromMap(sessions, token, false);
        if (session.isEmpty()) return Optional.empty();
        SessionUser s = session.get();
        if (s.userId() == null) return Optional.empty();
        Optional<UserEntity> current = userRepository.findById(s.userId());
        if (current.isEmpty() || !Boolean.TRUE.equals(current.get().getEnabled())) {
            revoke(s.token());
            return Optional.empty();
        }
        UserEntity user = current.get();
        if (!Objects.equals(user.getUsername(), s.username()) || !Objects.equals(user.getRole(), s.role()) || passwordChangedAfterIssue(user, s)) {
            revoke(s.token());
            return Optional.empty();
        }
        SessionUser refreshed = new SessionUser(s.token(), user.getId(), user.getUsername(), user.getRealName(), user.getRole(), s.issuedAt(), user.getPasswordUpdatedAt(), s.expiresAt());
        sessions.put(s.token(), refreshed);
        return Optional.of(refreshed);
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) sessions.remove(token.trim());
    }

    public void revokeUserSessions(Long userId) {
        if (userId == null) return;
        sessions.entrySet().removeIf(e -> Objects.equals(e.getValue().userId(), userId));
        websocketTickets.entrySet().removeIf(e -> Objects.equals(e.getValue().userId(), userId));
    }

    public WsTicket issueWebsocketTicket(SessionUser session) {
        String ticket = randomToken(32);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(Math.max(30, securityProperties.getWebsocketTicketTtlSeconds()));
        websocketTickets.put(ticket, new SessionUser(ticket, session.userId(), session.username(), session.realName(), session.role(), session.issuedAt(), session.passwordUpdatedAt(), expiresAt));
        cleanupExpired();
        return new WsTicket(ticket, expiresAt);
    }

    public Optional<SessionUser> consumeWebsocketTicket(String ticket) {
        return validateFromMap(websocketTickets, ticket, true);
    }

    private Optional<SessionUser> validateFromMap(Map<String, SessionUser> map, String token, boolean consume) {
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

    private boolean passwordChangedAfterIssue(UserEntity user, SessionUser session) {
        LocalDateTime currentChangedAt = user.getPasswordUpdatedAt();
        if (currentChangedAt == null) return false;
        LocalDateTime sessionChangedAt = session.passwordUpdatedAt();
        if (sessionChangedAt == null) return currentChangedAt.isAfter(session.issuedAt());
        return currentChangedAt.isAfter(sessionChangedAt);
    }

    private void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        sessions.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        websocketTickets.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    private String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record SessionUser(String token, Long userId, String username, String realName, UserRole role, LocalDateTime issuedAt, LocalDateTime passwordUpdatedAt, LocalDateTime expiresAt) {}
    public record WsTicket(String ticket, LocalDateTime expiresAt) {}
}
