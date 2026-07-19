package com.example.materialpull.service.session;

import com.example.materialpull.config.RedisConfig.ClusterKeyspace;
import com.example.materialpull.service.AuthTokenService.SessionUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Redis 会话存储（集群模式）。会话与一次性票据存入 Redis，并按 TTL 自动过期。
 * 后端重启 / 多实例部署时登录态不丢失、可共享。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.cluster", name = "enabled", havingValue = "true")
public class RedisSessionStore implements SessionStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ClusterKeyspace keys;

    public RedisSessionStore(StringRedisTemplate redis, ObjectMapper redisObjectMapper, ClusterKeyspace keys) {
        this.redis = redis;
        this.mapper = redisObjectMapper;
        this.keys = keys;
    }

    private String sessionKey(String token) { return keys.of("session", token); }
    private String ticketKey(String ticket) { return keys.of("ws-ticket", ticket); }
    private String userSessionsKey(Long userId) { return keys.of("user-sessions", String.valueOf(userId)); }
    private String userTicketsKey(Long userId) { return keys.of("user-tickets", String.valueOf(userId)); }

    @Override
    public void saveSession(SessionUser session, Duration ttl) {
        redis.opsForValue().set(sessionKey(session.token()), serialize(session), ttl);
        if (session.userId() != null) {
            String idx = userSessionsKey(session.userId());
            redis.opsForSet().add(idx, session.token());
            redis.expire(idx, ttl.plusMinutes(5));
        }
    }

    @Override
    public Optional<SessionUser> getSession(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String json = redis.opsForValue().get(sessionKey(token.trim()));
        return deserialize(json);
    }

    @Override
    public void removeSession(String token) {
        if (token == null || token.isBlank()) return;
        redis.delete(sessionKey(token.trim()));
    }

    @Override
    public void removeUserSessions(Long userId) {
        if (userId == null) return;
        String idx = userSessionsKey(userId);
        Set<String> tokens = redis.opsForSet().members(idx);
        if (tokens != null) {
            for (String t : tokens) redis.delete(sessionKey(t));
        }
        redis.delete(idx);
    }

    @Override
    public void saveTicket(SessionUser ticketSession, Duration ttl) {
        redis.opsForValue().set(ticketKey(ticketSession.token()), serialize(ticketSession), ttl);
        if (ticketSession.userId() != null) {
            String idx = userTicketsKey(ticketSession.userId());
            redis.opsForSet().add(idx, ticketSession.token());
            redis.expire(idx, ttl.plusSeconds(30));
        }
    }

    @Override
    public Optional<SessionUser> consumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) return Optional.empty();
        String key = ticketKey(ticket.trim());
        String json = redis.opsForValue().getAndDelete(key);
        return deserialize(json);
    }

    @Override
    public void removeUserTickets(Long userId) {
        if (userId == null) return;
        String idx = userTicketsKey(userId);
        Set<String> ts = redis.opsForSet().members(idx);
        if (ts != null) {
            for (String t : ts) redis.delete(ticketKey(t));
        }
        redis.delete(idx);
    }

    private String serialize(SessionUser session) {
        try {
            return mapper.writeValueAsString(session);
        } catch (Exception e) {
            throw new IllegalStateException("会话序列化失败", e);
        }
    }

    private Optional<SessionUser> deserialize(String json) {
        if (json == null || json.isBlank()) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(json, SessionUser.class));
        } catch (Exception e) {
            log.warn("会话反序列化失败：{}", e.getMessage());
            return Optional.empty();
        }
    }
}
