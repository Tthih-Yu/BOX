package com.example.materialpull.service;

import com.example.materialpull.common.SecurityProperties;
import com.example.materialpull.entity.UserEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.UserRepository;
import com.example.materialpull.service.session.SessionStore;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthTokenService {
    private final SecurityProperties securityProperties;
    private final UserRepository userRepository;
    private final SessionStore sessionStore;
    private final com.example.materialpull.repository.UserDeliveryAreaRepository userDeliveryAreaRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    private Duration sessionTtl() {
        return Duration.ofMinutes(Math.max(5, securityProperties.getSessionTtlMinutes()));
    }

    private Duration ticketTtl() {
        return Duration.ofSeconds(Math.max(30, securityProperties.getWebsocketTicketTtlSeconds()));
    }

    public SessionUser issue(UserEntity user) {
        String token = randomToken(48);
        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = issuedAt.plusMinutes(Math.max(5, securityProperties.getSessionTtlMinutes()));
        
        // 加载用户范围（DataScope）
        String factory = user.getFactory();
        java.util.List<String> deliveryAreas = loadUserDeliveryAreas(user.getId());
        
        SessionUser session = new SessionUser(token, user.getId(), user.getUsername(), user.getRealName(), user.getRole(), issuedAt, user.getPasswordUpdatedAt(), expiresAt, factory, deliveryAreas);
        sessionStore.saveSession(session, sessionTtl());
        return session;
    }
    
    private java.util.List<String> loadUserDeliveryAreas(Long userId) {
        return userDeliveryAreaRepository.findByUserId(userId)
                .stream()
                .map(com.example.materialpull.entity.UserDeliveryAreaEntity::getDeliveryArea)
                .collect(java.util.stream.Collectors.toList());
    }

    public Optional<SessionUser> validate(String token) {
        Optional<SessionUser> session = sessionStore.getSession(token);
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
        
        // 检查 factory 是否变化（DataScope）
        if (!Objects.equals(user.getFactory(), s.factory())) {
            revoke(s.token());
            return Optional.empty();
        }
        
        SessionUser refreshed = new SessionUser(s.token(), user.getId(), user.getUsername(), user.getRealName(), user.getRole(), s.issuedAt(), user.getPasswordUpdatedAt(), s.expiresAt(), s.factory(), s.deliveryAreas());
        sessionStore.saveSession(refreshed, sessionTtl());
        return Optional.of(refreshed);
    }

    public void revoke(String token) {
        sessionStore.removeSession(token);
    }

    public void revokeUserSessions(Long userId) {
        sessionStore.removeUserSessions(userId);
        sessionStore.removeUserTickets(userId);
    }

    public WsTicket issueWebsocketTicket(SessionUser session) {
        String ticket = randomToken(32);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(Math.max(30, securityProperties.getWebsocketTicketTtlSeconds()));
        SessionUser ticketSession = new SessionUser(ticket, session.userId(), session.username(), session.realName(), session.role(), session.issuedAt(), session.passwordUpdatedAt(), expiresAt, session.factory(), session.deliveryAreas());
        sessionStore.saveTicket(ticketSession, ticketTtl());
        return new WsTicket(ticket, expiresAt);
    }

    public Optional<SessionUser> consumeWebsocketTicket(String ticket) {
        Optional<SessionUser> session = sessionStore.consumeTicket(ticket);
        if (session.isEmpty()) return Optional.empty();
        if (session.get().expiresAt().isBefore(LocalDateTime.now())) return Optional.empty();
        return session;
    }

    private boolean passwordChangedAfterIssue(UserEntity user, SessionUser session) {
        LocalDateTime currentChangedAt = user.getPasswordUpdatedAt();
        if (currentChangedAt == null) return false;
        LocalDateTime sessionChangedAt = session.passwordUpdatedAt();
        if (sessionChangedAt == null) return currentChangedAt.isAfter(session.issuedAt());
        return currentChangedAt.isAfter(sessionChangedAt);
    }

    private String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record SessionUser(String token, Long userId, String username, String realName, UserRole role, LocalDateTime issuedAt, LocalDateTime passwordUpdatedAt, LocalDateTime expiresAt, String factory, java.util.List<String> deliveryAreas) {
        @JsonCreator
        public SessionUser(
                @JsonProperty("token") String token,
                @JsonProperty("userId") Long userId,
                @JsonProperty("username") String username,
                @JsonProperty("realName") String realName,
                @JsonProperty("role") UserRole role,
                @JsonProperty("issuedAt") LocalDateTime issuedAt,
                @JsonProperty("passwordUpdatedAt") LocalDateTime passwordUpdatedAt,
                @JsonProperty("expiresAt") LocalDateTime expiresAt,
                @JsonProperty("factory") String factory,
                @JsonProperty("deliveryAreas") java.util.List<String> deliveryAreas) {
            this.token = token;
            this.userId = userId;
            this.username = username;
            this.realName = realName;
            this.role = role;
            this.issuedAt = issuedAt;
            this.passwordUpdatedAt = passwordUpdatedAt;
            this.expiresAt = expiresAt;
            this.factory = factory;
            this.deliveryAreas = deliveryAreas;
        }
    }
    public record WsTicket(String ticket, LocalDateTime expiresAt) {}
}
