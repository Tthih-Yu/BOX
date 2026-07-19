package com.example.materialpull.service.session;

import com.example.materialpull.service.AuthTokenService.SessionUser;

import java.time.Duration;
import java.util.Optional;

/**
 * 会话与 WebSocket 票据的存储抽象。
 * 内存实现用于单实例；Redis 实现用于多实例 / 重启不丢登录态。
 */
public interface SessionStore {

    void saveSession(SessionUser session, Duration ttl);

    Optional<SessionUser> getSession(String token);

    void removeSession(String token);

    /** 移除某个用户的全部会话（禁用 / 改密 / 改角色时调用）。 */
    void removeUserSessions(Long userId);

    void saveTicket(SessionUser ticketSession, Duration ttl);

    /** 取出并删除一次性票据。 */
    Optional<SessionUser> consumeTicket(String ticket);

    void removeUserTickets(Long userId);
}
